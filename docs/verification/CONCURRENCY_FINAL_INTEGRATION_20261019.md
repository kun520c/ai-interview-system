# 并发安全最终集成验收（2026-10-19）

已完成代码移植、必要修复、独立真实 MySQL 验收、Frontend V3 回归和打包。真实 Milvus 未验证。

- 基线和交付前再次 fetch 的 `origin/main` 均为 `bc2f1295c94af55fd08164c23c33dbf9900e91c5`。
- 原 PR #2 分支 HEAD 为 `2361591ec689076e973522dfff72b34a8bf4056a`。
- 独立分支：`codex/concurrency-final-integration-20261019`。
- 独立 Worktree：项目目录下 `.worktrees/concurrency-final-integration`。原工作区及其他 Worktree 未被修改；fetch 更新了远端引用。
- 用 `git cherry-pick --no-commit f28cd24 2361591` 完整复用两个修复提交，无冲突。没有复制原脏工作区，没有覆盖 Frontend V3、README 或 Jev 修复。
- 本次未执行 commit、push、PR merge、正式数据库迁移或真实向量删除。代码处于本地待提交状态。

## 最终行为与额外修复

原 PR 的 Attempt Fencing、固定报告 Version、短事务和 `OrphanVectorMaintenance` 全部保留。每次领取/重试/超时接管生成新身份；外部调用结束后仍需在 Java/MySQL 端重新验证该身份。

1. **评价**：RAG 轨迹、AnswerEvaluation、EVALUATED、追问/下一题和 Session 推进在同一短事务提交。旧任务无法写入成功结果或失败审计，也不能把接管任务改为 FAILED。历史已保存评价可以复用。
2. **报告**：只有成功 CAS 的任务执行生成，固定使用它获得的 Version。Report、Weakness 和 READY 保持一个事务；旧 Version 的成功/失败均不能改变接管结果。
3. **知识处理**：Chunk 替换和 READY 在校验 `processing_attempt_id` 的锁定事务内执行。向量仅按本批次 ID 补偿；旧 Chunk 的明确 ID 只在新事务成功后清理。不再按 documentId 批量删除向量。
4. **本次新增修复——提交确认**：MySQL 提交异常后用 `lockById` 当前锁定读等待未决事务结束。普通 SELECT 可能看到旧 PROCESSING，错误地补偿已经成功或正在提交的向量。确认失败时继续保留向量。
5. **本次新增修复——答案提交死锁**：确定性双线程测试在真实 InnoDB 上复现了 Answer 外键检查与 Question 更新之间的锁竞争。短事务回滚后，`InterviewService` 将数据库死锁/锁等待异常转为 ConflictException（HTTP 409），提示复用原 requestId 重试，不增加自动模型重试。
6. **本次新增验收**：同时提交相同内容、相同 requestId 不同内容、不同 requestId 竞争同题；同时请求报告；三种崩溃遗留状态的新请求恢复；提交尚未结束时的确认等待；迁移历史兼容性。

超时仍由请求触发：EVALUATING 10 分钟、GENERATING 10 分钟、PROCESSING 15 分钟。未引入 MQ、Redis 锁、分布式事务或后台调度器。主问题来源、模型策略、RAG 算法和前端设计均未更改。

## 验证环境与准确统计

Java 21.0.11、MySQL 8.0 本机独立实例；MySQL 仅绑定 `127.0.0.1:13326`，数据目录为本 Worktree 的 `target/browser-e2e/20261019-concurrency/mysql-data`，业务测试使用 `ai_interview_test`。未连接正式 MySQL。所有外部模型调用均为 Mock/Fake 或本机 Mock HTTP。

普通 Maven 运行前清除了全部 `RUN_REAL_*` 和 `MILVUS_ENABLED`。使用 Serial GC / 768 MiB 上限作为测试 JVM 参数；未改变测试选择、断言或生产 JVM 配置。

| 运行 | Tests/检查点 | Failures | Errors | Skipped | 结果 |
| --- | ---: | ---: | ---: | ---: | --- |
| 初次并发专项 | 73 | 3 | 0 | 0 | 真实 MySQL 提交死锁暴露为数据库异常，随后修复 |
| 增补迁移后的专项 | 105 | 0 | 1 | 0 | 新迁移测试的同名 CREATE LIKE 夹具失败，随后修正为连接级结构副本 |
| 最终并发专项 | 105 | 0 | 0 | 0 | PASS |
| 完整 Maven 回归 | 1789 | 0 | 0 | 19 | BUILD SUCCESS；1770 通过 |
| 单独临时启用 KnowledgePersistenceIntegrationTest | 10 | 0 | 0 | 0 | 真实独立 MySQL PASS；开关在 finally 中清除 |
| 前端 Vitest | 293（36 文件） | 0 | 0 | 0 | PASS |
| 完整浏览器验收 | 49 检查点 | 0 | 0 个未捕获页面异常 | — | PASS；2 次完整面试及恢复/权限场景 |
| 类型检查 / lint / 前端生产构建 | — | — | — | — | 全部退出码 0 |
| 后端 compile / package | — | — | — | — | BUILD SUCCESS；package 使用 skipTests，不计为再次测试通过 |

不同运行相互重叠，不累加为独立用例总数。全量的 19 项跳过为：AI baseline 1、Embedding 1、知识持久化 10、知识失败补偿 2、知识处理 2、Milvus Schema 2、Milvus VectorStore 1。知识持久化的 10 项已另外启用并通过，其余真实外部服务未验证。

浏览器使用生产 Vue、真实 Spring Boot HTTP、安全认证、MyBatis、事务和独立 MySQL，外部 Embedding/Chat HTTP 使用本机 Mock，向量适配器为本地 Fake。49 项检查覆盖重复点击只发一次、丢失响应后复用同一 requestId/内容、刷新恢复、主答/追问、5 道主问题与最终评价、报告、弱项、历史/详情和管理员权限。数据库快照与 UI/请求一致性复核通过。保留了 33 条预期的负向 HTTP/被阻断外部字体资源 console 记录；未捕获页面异常为 0，未将负向记录隐藏为零。

## 验收场景索引

| 要求 | 可执行证据 |
| --- | --- |
| 两线程同时提交；requestId 内容冲突；同题唯一 Answer | `InterviewEvaluationClaimConcurrencyIntegrationTest.simultaneousSubmissionsKeepOneAnswerAndAllowExactReplay` 三组参数；真实唯一键/事务；完成后精确重放并拒绝改内容 |
| 两线程同时领取评价 | 同类 `concurrentSubmittedClaimShouldHaveOneEvaluatorAndNeverReturn500`、`concurrentFailedRetryShouldHaveOneEvaluatorAndNeverReturn500` |
| A 超时后 B 接管，A 随后成功或失败 | 同类 `staleWorkerCannotSaveEvaluationOrFailReplacement` 四组交错 |
| 评价已写入后推进失败全部回滚 | `InterviewWorkflowTransactionServiceIntegrationTest.shouldRollbackEarlierUpdatesWhenSessionCasFails`；轨迹/评价/Answer/Question/Session 一并核对 |
| 两线程请求报告；旧 Version 不能成功/失败 | `InterviewReportTransactionServiceIntegrationTest.simultaneousReportRequestsHaveOneClaimOneReportAndOneWeaknessUpdate`、`oldReportVersionCannotCommitOrFailReplacement` |
| Milvus 写成功而 MySQL 回滚 | `KnowledgeAttemptFencingIntegrationTest.mysqlRollbackCompensatesOnlyCurrentBatchAndRetryUsesNewIdentity`；真实 MySQL + Fake 向量集合 |
| 旧补偿与新任务提交交错 | 同类 `staleCompletionAndCompensationCannotTouchReplacement` 六组交错 |
| 提交确认丢失、尚未结束或不可用 | 同类 `lostMysqlCommitAcknowledgementPreservesCommittedVectors`、`commitConfirmationWaitsForUnresolvedTransactionBeforeCompensation`、`unavailableCommitConfirmationRetainsOnlyOwnPossibleOrphans` |
| 孤立向量 dry-run 保留有效/INACTIVE Chunk | `OrphanVectorMaintenanceTest`；`InterviewWorkflowTransactionServiceIntegrationTest.maintenanceSqlPreservesExistingInactiveChunkAndReportsOnlyOrphan`（真实 MySQL、Mock SDK） |
| 模拟 JVM 崩溃后的新请求恢复 | `newRequestRecoversCrashedEvaluatingWithNoLiveOldWorker`、`newRequestRecoversCrashedGeneratingAndAppliesWeaknessOnce`、`newRequestRecoversCrashedProcessingWithoutDeletingItsUnrecordedVectors` |
| V3 正常流程、重复点击、丢失响应恢复 | `tools/browser-e2e/acceptance.mjs attempt-01 full`，49 项 PASS；`verify.mjs` 数据复核 PASS |

崩溃用例直接构造崩溃会留下的持久状态并令旧任务不再运行，没有声称执行了操作系统级 JVM kill 或真实网络故障注入。

## 执行命令

从独立 Worktree 执行，运行目录统一设置 `BROWSER_E2E_RUN_ID=20261019-concurrency`。完整命令与输出保存在该目录 `target/browser-e2e/20261019-concurrency/`；运行参数中没有正式凭据。

```powershell
$env:JAVA_HOME='<本机 Java 21 目录>'
$env:BROWSER_E2E_RUN_ID='20261019-concurrency'
./tools/browser-e2e/environment.ps1 -Mode Prepare
./tools/browser-e2e/environment.ps1 -Mode Start

# 本次私有 MySQL 的 root 仅在本机测试实例使用空密码。
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:13326/ai_interview_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:TEST_DB_USERNAME='root'
$env:DB_USERNAME='root'
Get-ChildItem Env: | Where-Object { $_.Name -like 'RUN_REAL_*' -or $_.Name -eq 'MILVUS_ENABLED' } | Remove-Item
$env:JAVA_TOOL_OPTIONS='-XX:+UseSerialGC -Xmx768m'

./mvnw.cmd -B -ntp '-Dspring.datasource.password=' '-Dtest=TimeoutAttemptFencingMigrationIntegrationTest,InterviewServiceTest,InterviewEvaluationClaimConcurrencyIntegrationTest,InterviewWorkflowTransactionServiceIntegrationTest,InterviewReportTransactionServiceIntegrationTest,KnowledgeAttemptFencingIntegrationTest,KnowledgeDocumentProcessingServiceTest,OrphanVectorMaintenanceTest' test
./mvnw.cmd -B -ntp '-Dspring.datasource.password=' test

$env:RUN_REAL_KNOWLEDGE_DB_TEST='true'
try { ./mvnw.cmd -B -ntp '-Dspring.datasource.password=' '-Dtest=KnowledgePersistenceIntegrationTest' test }
finally { Remove-Item Env:RUN_REAL_KNOWLEDGE_DB_TEST }
./mvnw.cmd -B -ntp -DskipTests package

# 在 frontend 目录：npm ci --ignore-scripts --no-audit --no-fund
# 然后分别执行 npm run typecheck、npm run lint、npm run test:unit、npm run build
# Playwright/Chrome 使用 tools/browser-e2e/README.md 的本机运行时配置。
node tools/browser-e2e/acceptance.mjs attempt-01 full
./tools/browser-e2e/environment.ps1 -Mode SnapshotAfterRegression
node tools/browser-e2e/verify.mjs attempt-01 --after-regression
./tools/browser-e2e/environment.ps1 -Mode Stop
```

Prepare 拒绝覆盖既有运行目录，重跑必须选择新的 RUN_ID。实际已安装依赖后重启了本次前端，并通过 RestartBackend 加载最新 Java 修复。所有服务只使用本次创建并记录身份的进程，结束时停止。

## MySQL 迁移

迁移文件：`sql/migrations/20261015_timeout_attempt_fencing.sql`；仍仅增加 `interview_answer.attempt_id` 和 `knowledge_document.processing_attempt_id` 两个可空 varchar(32)，没有新表、唯一键改动、清空或历史数据回填。

`TimeoutAttemptFencingMigrationIntegrationTest` 在独立 `ai_interview_test` 中创建连接级临时表，取实际表的列和索引定义并移除两个新字段以恢复旧结构，省略副本的约束。它在这些临时历史表上执行原始迁移文件，核对 4 种 Answer 状态、4 种 Document 状态、原始内容和 NULL 身份保留，再核对历史进行中行可以取得新身份；最后仅删除 TEMPORARY 表。永久测试表来自包含新字段的建表快照，Mapper/事务/唯一约束则使用这些真实永久表验证。没有把临时表验证描述为已在正式环境应用迁移。

脚本是一次性 DDL。MySQL 的两条 ALTER 不构成可回滚的整体事务；上线前检查两列当前状态。若部分执行成功，先核对结构再补齐，不能盲目重跑或删除历史数据。正式迁移需由用户单独执行。

## Milvus、维护工具与故障边界

本机 Docker daemon 未运行，`127.0.0.1:19530` 连接未成功。没有执行真实 Milvus 写入、删除或 Collection 创建；全量运行的真实 Milvus 用例按双重保护跳过。因此 SDK Mock 和 Fake 交错验证不构成真实 Milvus 集成成功证据。正式合并前建议在独立测试 Collection 补跑受保护 Smoke Tests，并验证非目标数据保留。

保留维护工具的默认 dry-run、显式目标核对、writers-drained 和 old-instances-stopped 确认、PROCESSING 检查、所有 Chunk 的 MySQL 存在性检查、删除前复查和新建审计文件。dry-run 对 Mock 向量扫描结果识别孤立 ID，保留有效和 INACTIVE Chunk，未删除有效数据。显式删除分支仅用 SDK Mock 验证，没有执行真实删除。

| 故障 | 行为与剩余风险 |
| --- | --- |
| 线程正常抛 RuntimeException | 已有短事务回滚；失败状态按本次身份 CAS；向量仅补偿本次明确 ID，补偿失败留下可审计孤立向量 |
| 请求中途断开 | 服务线程可能继续并提交；客户端保留原 requestId/内容查询或重放，不据此假定服务端失败 |
| JVM 崩溃/强制终止 | 无法保证 catch/finally 补偿；留下的进行中状态由超时后的新请求接管；向量可能孤立，需排空后的维护对账 |
| A 超时后 B 接管 | A 的成功、失败、轨迹写入、Chunk 替换和状态更新均受到本次身份限制；A 的明确向量 ID 与 B 不同 |
| 外部响应超时但实际执行成功 | 模型调用可能重复；Milvus 可能稍后完成写入；MySQL 提交确认不可用时保留向量。外部调用不承诺恰好一次 |
| 孤立向量 | MySQL 资格过滤避免当作有效证据，但仍占容量并可能挤占候选位置。工具不能自动证明跨进程任务已排空，需要运维确认 |

## 推荐合并与上线步骤

1. 复核本分支相对最新 main 的差异和验收记录；需要发布时由用户提交、push 并创建最终集成 PR。旧 PR #2 的功能已完整复用，可在新 PR 合并后由用户标注已被替代。
2. 在有 Milvus 的隔离环境补跑独立测试 Collection 的真实 Smoke Tests。保持全部开关临时、保留双重保护；不得切换为正式 Collection。
3. 合并前再次 fetch/rebase 或重新移植到最新 main；若有新增代码冲突，解决后运行相应回归。当前验收基线为上述 bc2f129。
4. 部署时先阻断入口并排空/停止旧实例，确认没有可能继续写入的旧任务。不要混跑不支持 fencing 的旧应用。
5. 备份目标库并核对两列状态，由用户执行一次迁移，再部署新代码，观察评价、报告和知识处理的正常/恢复路径。
6. 孤立向量维护单独安排窗口：核对目标地址、确认排空、先 dry-run；真实删除需要用户另行授权并重新核对 MySQL，不自动执行。

## 原始证据

本 Worktree 的 `target/browser-e2e/20261019-concurrency/` 保留：`compile.log`、`focused.log`、`focused-fixed.log`、`focused-final.log`、`focused-final-reports/`、`maven-full.log`、`maven-full-reports/`、`maven-full-summary.json`、`knowledge-db-optin.log`、`maven-package.log`、前端四项日志、`frontend-exit-codes.json`、`browser-full.log`、`attempt-01/`、`database-snapshot-after-regression.json`、`consistency-after-regression.log`、私有 MySQL 数据目录和服务进程记录。测试输出位于 ignored target，不随代码自动发布。

## 最终文件清单与用途

下表由最终 Git 差异生成，A 为本分支新增，M 为修改；原 PR 复用文件与本次必要增补一起交付。

| 状态 | 文件 | 用途 |
| --- | --- | --- |
| A | `docs/verification/CONCURRENCY_FINAL_INTEGRATION_20261019.md` | 最终文件、测试、迁移、风险与合并步骤记录 |
| A | `docs/design/timeout-task-fencing.md` | 原 PR 的隔离机制说明及最终验收索引 |
| M | `sql/ai_interview.sql` | 新建库同步两个可空任务身份字段 |
| A | `sql/migrations/20261015_timeout_attempt_fencing.sql` | 旧库一次性新增两个可空身份字段 |
| M | `src/main/java/com/kun/aiinterview/interview/entity/InterviewAnswer.java` | 映射评价 attemptId |
| M | `src/main/java/com/kun/aiinterview/interview/mapper/InterviewAnswerMapper.java` | 评价领取、接管、完成与失败携带身份；锁定读取 |
| M | `src/main/java/com/kun/aiinterview/interview/mapper/InterviewSessionMapper.java` | 报告接管要求期望 Version |
| M | `src/main/java/com/kun/aiinterview/interview/orchestration/EvaluationOrchestrationResult.java` | 携带待提交评价和 RAG 轨迹，提交后返回持久 ID |
| M | `src/main/java/com/kun/aiinterview/interview/orchestration/EvaluationOrchestrationService.java` | 准备评价；失败审计按任务身份保护；不提前提交成功评价 |
| M | `src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java` | 单次成功 CAS 后固定 Version，成功/失败不采用新任务版本 |
| M | `src/main/java/com/kun/aiinterview/interview/service/InterviewService.java` | 提交事务锁竞争回滚后返回可重试 409，保留 requestId |
| M | `src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java` | 生成并传递评价身份，统一提交结果和流程 |
| M | `src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java` | 锁 Answer 验证身份，轨迹/评价/Answer/Question/Session 原子提交 |
| M | `src/main/java/com/kun/aiinterview/knowledge/entity/KnowledgeDocument.java` | 映射 processingAttemptId |
| A | `src/main/java/com/kun/aiinterview/knowledge/maintenance/OrphanVectorMaintenance.java` | 显式 CLI 对账；默认 dry-run、目标/排空保护与审计 |
| M | `src/main/java/com/kun/aiinterview/knowledge/mapper/KnowledgeChunkMapper.java` | 读取替换前的明确 Vector ID |
| M | `src/main/java/com/kun/aiinterview/knowledge/mapper/KnowledgeDocumentMapper.java` | 处理领取、接管、READY/FAILED 按任务身份保护 |
| M | `src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeDocumentProcessingService.java` | 固定身份、精确 ID 补偿、锁读提交确认、成功后清理旧 ID |
| M | `src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeDocumentProcessingTransactionService.java` | 锁定身份，Chunk 替换和 READY 同一事务 |
| M | `src/main/java/com/kun/aiinterview/knowledge/service/RagTracePersistenceService.java` | 失败轨迹仅允许当前评价任务写入 |
| M | `src/main/resources/mapper/InterviewAnswerMapper.xml` | 评价 CAS 身份与锁 SQL |
| M | `src/main/resources/mapper/InterviewSessionMapper.xml` | 报告超时接管增加 Version 条件 |
| M | `src/main/resources/mapper/KnowledgeChunkMapper.xml` | 读取文档版本对应明确 Vector ID 的 SQL |
| M | `src/main/resources/mapper/KnowledgeDocumentMapper.xml` | 处理身份条件与锁 SQL |
| M | `src/test/java/com/kun/aiinterview/common/recovery/StaleRecoveryCasIntegrationTest.java` | 真实 MySQL 超时接管 CAS 参数与单赢家验证 |
| A | `src/test/java/com/kun/aiinterview/common/recovery/TimeoutAttemptFencingMigrationIntegrationTest.java` | 原迁移脚本对临时历史表的兼容与数据保留验收 |
| M | `src/test/java/com/kun/aiinterview/interview/evaluation/orchestration/EvaluationOrchestrationServiceTest.java` | 准备结果、历史评价复用和身份化失败审计验收 |
| M | `src/test/java/com/kun/aiinterview/interview/mapper/InterviewAnswerMapperTest.java` | 真实评价身份 CAS、锁读取及状态验证 |
| M | `src/test/java/com/kun/aiinterview/interview/mapper/InterviewSessionMapperTest.java` | 真实报告 Version CAS 验证 |
| M | `src/test/java/com/kun/aiinterview/interview/service/InterviewEvaluationClaimConcurrencyIntegrationTest.java` | 双线程提交/领取、旧任务晚返回、内容冲突和崩溃遗留恢复 |
| M | `src/test/java/com/kun/aiinterview/interview/service/InterviewReportServiceTest.java` | 固定抢占 Version、重试、已有报告与失败路径验证 |
| M | `src/test/java/com/kun/aiinterview/interview/service/InterviewReportTransactionServiceIntegrationTest.java` | 真实事务原子性、报告并发、旧 Version 与崩溃遗留恢复 |
| M | `src/test/java/com/kun/aiinterview/interview/service/InterviewServiceTest.java` | 提交死锁/锁等待映射为 409，保留原异常及无模型调用 |
| M | `src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowServiceTest.java` | 评价身份透传、历史复用、流程决策与失败处理 |
| M | `src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionServiceIntegrationTest.java` | 真实原子提交/回滚、旧身份拒绝与维护 dry-run SQL |
| M | `src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionServiceTest.java` | 评价/轨迹持久化校验与流程事务职责的聚焦测试 |
| A | `src/test/java/com/kun/aiinterview/knowledge/maintenance/OrphanVectorMaintenanceTest.java` | 默认 dry-run、目标/排空保护、分页、复查、审计及 Mock 删除验证 |
| M | `src/test/java/com/kun/aiinterview/knowledge/mapper/KnowledgeDocumentMapperTest.java` | 真实处理身份 CAS 与锁 SQL 验证 |
| A | `src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeAttemptFencingIntegrationTest.java` | 真实 MySQL + Fake 向量的旧补偿交错、回滚、提交确认与恢复 |
| M | `src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeDocumentProcessingServiceTest.java` | 新身份及精确补偿边界的 Mock 验证 |
| M | `src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeDocumentProcessingTransactionServiceTest.java` | 处理身份校验、旧 ID 返回及 Chunk/READY 事务职责 |
| M | `src/test/java/com/kun/aiinterview/knowledge/service/KnowledgePersistenceIntegrationTest.java` | 保留双重开关的真实 MySQL 持久化/回滚验收 |
| M | `src/test/java/com/kun/aiinterview/knowledge/service/RagP1OfflineBusinessReplayTest.java` | 离线回放适配准备评价与新的提交职责 |
| M | `src/test/java/com/kun/aiinterview/knowledge/service/RagTracePersistenceServiceTest.java` | 轨迹服务依赖适配及原有写入校验 |
| M | `src/test/java/com/kun/aiinterview/knowledge/service/RealKnowledgeDocumentProcessingFailureIntegrationTest.java` | 保留双重保护并适配精确补偿/身份参数 |
| A | `tools/orphan-vectors/README.md` | 维护窗口、默认 dry-run、目标核对和明确 ID 清理操作说明 |
| M | `tools/rag-e2e/schema-test-only.sql` | 独立测试建表快照同步两个可空身份字段 |
