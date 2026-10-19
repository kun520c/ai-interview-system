# 并发专项最后验收（2026-10-19）

结论：**READY_TO_COMMIT**。

当前代码已完成最终审查、独立真实 MySQL/Milvus 验收和普通全量回归。生产并发实现未修改；本轮只修复受保护 Smoke Test 的依赖配置遗漏，增加一项用户要求的迟到插入回归测试，并新增本报告。未执行 commit、push、PR merge 或正式数据库迁移。

## 基线、范围与原始证据

- 分支：`codex/concurrency-final-integration-20261019`。
- 本轮重新 fetch 后，`HEAD` 与 `origin/main` 均为 `bc2f1295c94af55fd08164c23c33dbf9900e91c5`，提交层面 ahead/behind 为 `0/0`；所有修复仍是本地待提交变更。
- 原 PR #2 HEAD 仍为 `2361591ec689076e973522dfff72b34a8bf4056a`。
- 初始暂存 47 文件的完整补丁、索引快照保存在 `target/concurrency-final-acceptance/20261019/initial-staged.patch` 和 `initial-index.txt`。初始补丁 SHA-256：`7EB729701B7AE4CB0C62DE22698155022B1A336516EF90ED5029BF6D427DF9D6`。
- 上轮 `target/browser-e2e/20261019-concurrency/`、原 Worktree 和其他工作区修改均保留。本轮使用新的独立证据目录，没有覆盖上轮报告、日志、数据库数据目录或补丁。
- 最终变更为 49 文件：初始 47 文件，加上此前未变更的处理流程 Smoke Test 和本报告。初始差异没有被丢弃；本轮追加的测试变更可由 `review-additions.patch` 单独核对。

## 代码审查结论

未发现需要修改生产并发机制的明确问题。逐项结论如下。

| 审查点 | 结果与依据 |
| --- | --- |
| Attempt Fencing 与 SQL CAS | 领取/重试/超时接管生成并固定 UUID 身份；Answer/Document 完成和失败 SQL 同时限定主键、进行中状态和本次身份。完成事务内锁行并重新验证身份。领取成功后不重新读取并采用他人的身份。报告固定使用成功 CAS 所获得的递增 Version。 |
| 评价事务一致性 | 新评价的 RAG 轨迹、评价、Answer、Question 和 Session 推进在一个短事务内；Session CAS/外键/写入失败共同回滚。失败准备阶段的诊断轨迹由独立短事务锁 Answer、验证原身份后写入；旧任务不能追加诊断。持久化职责迁移后，生成主键和影响行数测试在事务服务中继续保留。 |
| MySQL 提交异常确认 | `KnowledgeDocumentProcessingService` 第 111 行使用 `lockById` 当前锁定读，等待持有文档行锁的未决事务结束；只在同一 Attempt 的 READY 得到确认时视作已提交。确认读取失败则保留可能已提交的向量。未恢复为普通快照读，没有把外部 Milvus RPC 放在数据库锁内。真实 MySQL 的未决提交等待测试通过。 |
| 死锁映射 HTTP 409 | `InterviewService` 第 289 行仅在短提交事务调用之外捕获 `PessimisticLockingFailureException`，覆盖死锁/获取锁失败等可重试并发异常。该事务代理已完成回滚；随后返回 ConflictException。没有把所有 DataAccessException、SQL 错误、外部错误或提交结果不明错误统一转为 409。唯一键冲突仍单独按 requestId、问题和精确内容核对。 |
| Milvus 补偿 | Vector ID 使用 `attemptId + '-' + chunkIndex`，不同任务具有不同 ID。失败仅删除本批明确 ID；新事务提交后仅清理锁内读取的旧 Chunk ID。不按 documentId 清理任务数据；提交后旧数据清理失败不会删除当前有效批次或回退 READY。 |
| 孤立向量维护 | 默认 dry-run，显式目标核对及两个排空确认，分批检查 PROCESSING；存在任意 MySQL Chunk 的 ID 都保留，包括 INACTIVE。删除前重新核对 MySQL，使用参数化明确 ID 删除，并用 STRONG 查询确认结果。 |
| 迁移与测试 | 两个可空 varchar(32) 字段与 Entity/Mapper/建表快照一致，保留历史 NULL 行。一次性 DDL 的部分执行风险在上轮报告中已说明。迁移历史兼容测试本轮再次通过。发现的测试配置遗漏已修复，未删除测试、减弱断言或绕过启用条件。 |

重点实现位置：`InterviewWorkflowTransactionService.persistOwnedEvaluation`、`InterviewReportService.generateReport`、`KnowledgeDocumentProcessingTransactionService.persistChunksAndMarkReady`、`KnowledgeDocumentProcessingService.processDocument`、`OrphanVectorMaintenance.run`。

## 本轮必要修改

| 文件 | 修改与原因 |
| --- | --- |
| `src/test/java/com/kun/aiinterview/knowledge/service/RealKnowledgeDocumentProcessingFailureIntegrationTest.java` | 在隔离测试配置中显式导入 `RecoveryConfiguration`；真实启用后发现缺少 StaleRecoveryProperties Bean，导致两项测试不能启动。 |
| `src/test/java/com/kun/aiinterview/knowledge/service/RealKnowledgeDocumentProcessingSmokeTest.java` | 显式导入 `RecoveryConfiguration` 和 `RetrievalProperties`，补齐该隔离配置实际使用的服务依赖。 |
| `src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeAttemptFencingIntegrationTest.java` | 新增 `compensationBeforeLateInsertLeavesOnlyAnIneligibleOldAttemptOrphan`：使用 CountDownLatch、Fake 向量集合和真实 MySQL，持续覆盖“补偿已完成后，迟到插入才生效”。 |
| 本报告 | 保存最终审查、真实服务、失败修复、清理和提交准备结论。 |

原有真实服务测试的 `@EnabledIfEnvironmentVariable`、`Assumptions.assumeTrue(...)`、`local,test-real` profile 均保留。没有改动生产 Java、Mapper SQL、数据库迁移、前端、模型策略或 RAG 算法。

## 真实服务隔离与收费调用限制

Docker/Milvus 本轮已由本机环境启动。只读检查确认 Docker Desktop 4.94.0 / Engine 29.8.2 可达，既有 `milvusdb/milvus:v2.6.20` 容器健康，19530 端口连接成功；没有启动、停止、重建或修改这些容器，也没有改动其他项目的 MySQL/Redis 环境。

- 新建独占 MySQL 8.0 实例：仅绑定 `127.0.0.1:13328`，数据库 `ai_interview_test`，数据目录位于本轮证据目录 `mysql-data`。测试在启动时核对实际 `@@datadir`。
- 新建唯一 Milvus 数据库：`concurrency_acceptance_20261019_839037bd`，Collection：`knowledge_chunk_vectors_test`。数据库创建前确认不存在，并写所有权记录；没有复用既有测试或正式 Collection。
- 保留 `EnvironmentIsolationInitializer`；通过独立 Milvus 数据库实现隔离，Collection 名仍使用规范测试名，没有绕过目标校验。
- 现有 Smoke Tests 的 Embedding HTTP 指向 `127.0.0.1:18098/v1` 的 Fake；新增交错验收使用内存 Fake Embedding。DeepSeek 关闭，未启用真实 Embedding 模型测试。**收费模型调用为 0**。
- 日志中既有测试打印的 “Real Embedding completed” 表示生产适配器完成了对本机 Fake 的调用，不能据此认定真实模型已验证；本轮真实外部系统为 MySQL 和 Milvus。
- 真实测试通过 Maven/Surefire 临时开关运行，完成后在 finally 清除；没有写入 Git 配置、IDEA 配置或用户/系统环境变量。

## A/B 超时任务同时写入 Milvus：单独结论

| 用户关心的问题 | 最终结论 |
| --- | --- |
| A 是否仍可能写入 Milvus？ | **会。** 已在真实 Milvus 上验证：A 在 insert 内暂停，B 接管并完成后，释放 A，A 仍可能完成自己的向量写入；响应超时后，实际插入也可能迟到生效。任务身份限制的是业务提交资格，不能撤销已发出的 Milvus RPC。 |
| A 是否可能提交 MySQL？ | **B 已成功接管后，A 不能提交 Chunk 或 READY。** 最终 `processing_attempt_id` 始终为 B；对该文档只存在 B 的一个 Chunk 批次。锁内身份校验及带身份 CAS 拒绝 A，A 的失败更新也不能覆盖 B。 |
| A 是否可能破坏 B 的 Milvus 数据？ | **当前补偿路径不会。** 8 组真实交错均记录到 A 的补偿只携带 A 的 Vector ID；B 和非目标哨兵向量在补偿前后均存在。没有调用按 documentId 删除的路径。 |
| A 的迟到写入如何识别和清理？ | ID 保留 A 的身份前缀；实际有效性以 MySQL 精确 vector_id/版本/状态校验为准。A 向量可能仍出现在原始 Milvus 结果中，但没有对应有效 MySQL Chunk，不能进入有效证据。待本次写入任务和迟到 RPC 全部结束后，维护 dry-run 将其识别为孤立 ID，删除前再次确认不存在任意 Chunk，再按明确 ID 清理。 |

新增真实验收类保存在 `target/concurrency-final-acceptance/20261019/RealMilvusAttemptAcceptanceTest.java`，不进入生产构建；它也保留显式环境变量和 Assumption 两层保护。

1. **8 组参数交错**：在 `VectorStoreClient.insert()` 内，分别于真正 Milvus 写入前/后阻塞 A；令 B 超时接管并完成 MySQL/向量写入，再释放 A；组合 A 正常返回/抛超时异常及 A 补偿成功/失败。逐组核对 B 身份、READY、唯一 Chunk、明确补偿 ID、B/哨兵保留与 MySQL 资格过滤。
2. **补偿先于迟到插入**：A 的 insert 已开始但实际写入受独立 latch 阻塞；B 完成后，先让 A 收到注入的响应超时。等待 A 的真实精确删除返回、旧线程结束并确认 A ID 不存在，再释放迟到写入。真实 Milvus 随后出现 A 向量；MySQL 仍只有 B。dry-run 返回 `scanned=3, candidates=1, deleted=0`；排空后的明确删除返回 `deleted=1`，B 和哨兵保留。
3. **维护保护**：真实 ACTIVE Chunk、INACTIVE Chunk 和一个无 Chunk 的向量同时存在；dry-run 保留全部数据，仅报告一个候选；明确删除后只剩 ACTIVE/INACTIVE 对应向量，重复 dry-run 没有候选。

迟到插入的实际证据示例：A=`dc16cabd91d24ccf8e3b606bb8c62c19`，B=`b10a1f8646bd4c89a7b2a88aec90e1af`；详见 `takeover-evidence.txt` 和 `real-attempt-reports/`。所有交错通过 latch 和直接设置旧 updated_at 实现，没有等待真实 15 分钟，也没有持有数据库长事务来阻止旧 RPC。

## 测试运行与准确统计

| 本轮运行 | Tests | Failures | Errors | Skipped | 说明 |
| --- | ---: | ---: | ---: | ---: | --- |
| `real-existing` | 5 | 0 | 2 | 0 | 首次真实启用；补偿测试缺少 RecoveryConfiguration，随后修复 |
| `real-existing-fixed` | 7 | 0 | 0 | 2 | 补偿等 5 项通过；处理流程开关当时未打开，两项跳过，不视为通过 |
| `real-final` | 7 | 0 | 2 | 0 | 打开处理流程开关后暴露 RetrievalProperties 缺失，随后修复 |
| `real-final-verified` | 7 | 0 | 0 | 0 | 现有向量生命周期 1、Schema 2、失败补偿 2、知识处理/检索 2 全部通过 |
| `real-attempt` | 10 | 0 | 0 | 0 | 新增真实 MySQL/Milvus 交错和维护验收全部通过 |
| `focused` | 83 | 0 | 0 | 0 | 答案/评价/报告并发、迁移、知识 Attempt、维护保护；其中 KnowledgeAttemptFencingIntegrationTest 为 16 项 |
| `ordinary-full` | 1790 | 0 | 0 | 19 | 普通全量 Maven 回归 BUILD SUCCESS；1771 通过，真实开关全部关闭 |

不同运行包含重复用例，不累加为独立测试总数。普通全量的 19 项仍是 baseline 1、Embedding 1、知识 MySQL 持久化 10、知识失败补偿 2、知识处理 2、Milvus Schema 2、Milvus VectorStore 1；其中 Milvus 相关用例已在单独的真实启用运行中通过，Embedding/真实模型仍未验证。

前端和生产打包本轮未修改，也未重复执行；上轮的前端 293 项、浏览器 49 检查点和 JAR 打包记录见 `CONCURRENCY_FINAL_INTEGRATION_20261019.md`，不列作本轮新证据。

执行入口均保存在本轮证据目录，代表性命令如下：

```powershell
./target/concurrency-final-acceptance/20261019/environment.ps1 -Mode Prepare

./target/concurrency-final-acceptance/20261019/run-tests.ps1 -Tests 'RealMilvusVectorStoreSmokeTest,RealMilvusSchemaValidationSmokeTest,RealKnowledgeDocumentProcessingFailureIntegrationTest,RealKnowledgeDocumentProcessingSmokeTest' -Label real-final-verified -Real

# 临时真实交错类先使用 Java 21 javac 和 dependency classpath 编译到本轮 test-classes；
# 再直接运行 Surefire，避免改动原 src/test 或原测试报告输出。
./target/concurrency-final-acceptance/20261019/run-tests.ps1 -Tests 'RealMilvusAttemptAcceptanceTest' -Label real-attempt -Real -CompiledOnly

./target/concurrency-final-acceptance/20261019/run-tests.ps1 -Tests 'KnowledgeAttemptFencingIntegrationTest,TimeoutAttemptFencingMigrationIntegrationTest,InterviewServiceTest,InterviewEvaluationClaimConcurrencyIntegrationTest,InterviewWorkflowTransactionServiceIntegrationTest,InterviewReportTransactionServiceIntegrationTest,OrphanVectorMaintenanceTest' -Label focused

./target/concurrency-final-acceptance/20261019/run-tests.ps1 -Tests '*' -Label ordinary-full
./target/concurrency-final-acceptance/20261019/environment.ps1 -Mode Stop
```

`run-tests.ps1` 只生成临时根 POM，用于本轮单独的 testOutputDirectory、Surefire reportsDirectory 和 Serial GC/768 MiB 参数；不修改原 pom.xml，不排除既有测试。临时 POM 在 finally 删除。环境创建、命名空间创建/清理及 javac 命令的实际结果保存在相应日志中。复验必须使用新的证据目录和新的唯一命名空间，不覆盖当前已完成记录；当前命名空间已删除。

## 资源清理与交付检查

- 每个真实测试先按本次记录的明确 Vector ID 清理，并验证非目标哨兵仍存在；测试结束核对自有向量已不存在、MySQL 自有文档/Chunk 已清理。
- 全部真实测试和迟到 RPC 结束后，命名空间清理程序用 STRONG 查询确认测试 Collection 中向量数为 0，才删除本次创建的 Collection/数据库；`milvus-cleanup.json` 确认数据库已移除，原 default Collection 清单不变。Schema mismatch 临时 Collection 由原测试确认删除。
- 本次私有 MySQL 和本机 Fake 进程按 PID、启动时间和可执行路径核对后停止；MySQL 数据目录作为 ignored 本地验收材料保留。既有 Docker/Milvus、其他项目 MySQL/Redis 容器保持运行。
- `git diff --cached --check`、`git diff --check`、文件范围和可识别敏感凭据扫描通过。没有加入 local 配置、密钥、数据库密码、target 输出或无关前端/Jev 文件。自动扫描之外人工检查了配置、测试常量和文档中的凭据引用，均为占位符或本机测试值。
- 原始暂存快照保留；仅追加上述必要测试修复、用户要求的回归和本报告。最终索引差异核对保存在 `final-git-audit.json`。

## 故障边界与后续动作

本轮证明的是真实 MySQL/Milvus 上受控交错的不变量。响应超时由测试在适配器边界注入，未声称覆盖所有真实网络故障；没有执行真实模型调用或操作系统级 JVM kill。进程崩溃、不可确认提交、迟到 RPC 或补偿失败仍可能留下孤立向量。外部模型/向量调用不承诺恰好一次，也不承诺跨 MySQL/Milvus 原子提交。

可以审查并提交当前集成分支。发布前再次核对 main；部署时排空/停止旧实例，再由用户执行已批准的一次性迁移并部署。正式孤立向量维护仍需独立维护窗口、目标核对、dry-run 和用户授权；本次隔离删除验收不能作为正式删除的授权。

本报告对应的新证据目录为 `target/concurrency-final-acceptance/20261019/`：包含初始/最终差异、审查清单、临时验收源码和脚本、所有运行日志及 XML、维护 TSV、收费为 0 的 Fake 请求记录、Milvus 所有权/清理记录和进程停止记录。旧证据目录未覆盖。
