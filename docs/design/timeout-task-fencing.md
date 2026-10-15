# 超时接管任务的最小隔离修复

基于执行时远端 `main`：`9f0f40656aa8fd86157f3b462aa1e4bc1fb55b96`。

## 核查结论与机制

| 流程 | 源码确认的竞争窗口 | 本次修复 |
| --- | --- | --- |
| EVALUATING | 重抢仅刷新时间；旧任务仍能先写 Evaluation，占用唯一键，或将接管中的 Answer 标记 FAILED | 每次成功领取、重试、重抢写入新的 `attempt_id`；模型返回后，在短事务内锁 Answer、验证身份，再保存评价、更新 EVALUATED 并推进 Session |
| GENERATING | 两次读取之间出现他人的 GENERATING 可绕过 claim；claim 后重新读取可能取得他人的 Version；失败恢复采用最新 Version | 只允许 CAS 胜者执行；claim/reclaim 使用期望 Version，固定本次递增后的 Version；完成、失败及已有报告复用均使用该值 |
| PROCESSING | 重抢后旧任务仍可能写 Chunk/READY/FAILED；事务外按文档删除 Chunk 和 Milvus 向量可能清掉接管任务的数据 | 新增 `processing_attempt_id`；短事务内锁文档并验证身份，再替换 Chunk、更新 READY；FAILED 带身份 CAS |

原有的单赢家 claim、唯一键、Session 乐观锁、报告/Weakness/READY 的事务回滚已经覆盖部分交错，不能将它们描述为完全无保护。上述旧版窗口由源码确认；本次验证使用修复后的确定性交错测试，不声称执行过旧版本的红灯复现。

Evaluation Orchestrator 现在只准备评价数据或读取历史评价，写入责任归现有 Workflow TransactionService。历史已落库评价的失败重试仍可复用。原 Orchestrator 的三个持久化失败用例移到事务服务测试，继续验证影响行数及生成主键检查。

## Milvus 与跨系统边界

- 保留 Collection Schema 和原生 SDK 适配器。`vector_id = 32位Attempt ID + '-' + chunkIndex`，最长 43 字符，符合现有 64 字符上限。
- 处理流程不再调用 `deleteByDocumentId`。失败补偿仅删除本批次的明确 ID；事务成功后，仅清理事务内读取到的旧 Chunk 向量 ID。旧数据删除和 Chunk/READY 写入在同一 MySQL 事务内。
- 旧向量清理失败只记录待清理 ID，不回退 READY，不补偿当前有效批次；补偿失败保留原异常并附加失败原因。
- MySQL 返回提交异常时，重新确认同一 Attempt 的 READY；已确认提交则保留向量并返回成功。无法确认时保留可能的向量、记录 ID，FAILED 更新仍受本 Attempt 约束。
- MySQL/Milvus 不是原子事务。进程在向量写入后崩溃、删除失败、提交确认不可用，仍可能留下孤立向量。它们没有对应的可检索 MySQL Chunk，会被既有 READY/ACTIVE/版本/vector_id 资格过滤排除，但会占用容量，甚至挤占候选命中位置。
- 本次不增加后台清理设施。运维清理应在排空处理任务后，对照 MySQL 有效 vector_id，使用明确 ID 删除孤立向量；不能按文档全量删除。外部模型调用也可能重复，任务隔离只保证业务写入资格。

## 数据库迁移与部署

仅新增两个可空 `varchar(32)` 字段，历史行无需回填或清空。历史进行中任务在下一次合法领取/重抢时获得新身份。

建表源为 `sql/ai_interview.sql`，测试建表快照为 `tools/rag-e2e/schema-test-only.sql`；增量脚本为 `sql/migrations/20261015_timeout_attempt_fencing.sql`。脚本需在目标库执行一次。本次仅在 `ai_interview_test` 应用迁移。

部署前先停止或排空旧实例，再执行迁移并启动新代码；旧代码不校验身份，不能与新实例混跑。

## 验证

使用 CountDownLatch、受控 Fake/Mock、真实 MySQL 和真实 Spring 事务验证精确交错：A 超时后 B 接管；A 在 B 提交前后成功返回或抛错；旧评价无法占用唯一键；重复领取及并发抢占；评价写入后推进失败回滚及重试；历史评价复用；报告旧 Version 的成功/失败路径；文档旧任务补偿不能删 B 向量；MySQL 回滚恢复旧 Chunk；补偿失败；提交确认异常与提交后清理失败。正常流程和原有幂等性也纳入回归。

真实模型和真实 Milvus 不作为本次验证证据；普通回归保留所有外部测试开关及双重保护。

最终验证记录（2026-10-15）：

| 执行 | Tests | Failures | Errors | Skipped | 结果 |
| --- | ---: | ---: | ---: | ---: | --- |
| `mvnw.cmd -B -ntp test`（最终全量） | 1638 | 0 | 0 | 19 | BUILD SUCCESS，1619 通过 |
| 单独启用知识持久化真实 MySQL 测试的聚焦运行 | 59 | 0 | 0 | 0 | 含 `KnowledgePersistenceIntegrationTest` 的 10 项真实 MySQL 测试 |
| 提交确认保护的聚焦运行 | 35 | 0 | 0 | 0 | BUILD SUCCESS；后续增加的交错参数由最终全量覆盖 |
| `mvnw.cmd -B -ntp -DskipTests package` | — | — | — | — | BUILD SUCCESS，未重复执行测试 |

全量的 19 项跳过为：AI baseline 1、Embedding 1、知识持久化 10、知识失败补偿 2、知识处理 2、Milvus Schema 2、Milvus VectorStore 1。知识持久化的 10 项另行通过 Maven 临时启用后已验证；所有临时开关随后清除，其余真实模型/Milvus 测试未执行。不合计相互重叠的运行数量。
