# B2：评价任务领取后的异常恢复边界修复

执行日期：2026-11-11（Asia/Shanghai）。B2 已开发、编译，并通过定向测试、真实隔离 MySQL 验证和完整普通回归。未暂存、未 commit、未 push；完成后停止，不开展 B3。

报告绝对路径：`/Users/kun/Developer/projects/ai-interview-system/docs/verification/B2_POST_CLAIM_FAILURE_RECOVERY_20261111.md`。

## 1. 前置核对与本轮边界

- 已阅读根目录 `AGENTS.md`、J1 验收报告 `docs/verification/JEV_REMOVAL_DEEPSEEK_ONLY_20261111.md`、B1 报告 `docs/verification/B1_CONCURRENT_IDEMPOTENT_REPLAY_20261111.md`，检查 Git 状态、diff、相关源码、Mapper XML、事务及并发测试。
- 实际分支为 `main`，HEAD 为 `afbd1c5ab010f3a0a296e15e25c016f81eb2b74f`。开始时有 62 个未提交路径，暂存区为空。J1/B1 仍是工作区修改，没有新增提交；本轮没有访问远端，不据此声称远端最新状态。
- B1 两处只读重放判断仍在：首次读到 EVALUATED 直接重放；Session 当前题不匹配时重读 Answer，仅实际 EVALUATED 才重放。J1 的 DeepSeek-only 清理仍在。
- 重新汇总 B1 保存的 Surefire XML：定向 **286 / 0 / 0 / 0**，完整 **1549 / 0 / 0 / 19**。这些是已核实的历史证据，不算作 B2 新执行结果。J1 的历史回归与清理范围已核对报告，本轮完整回归再次覆盖当前代码。
- 未发现阻断 B2 的问题。没有修改 Schema、HTTP API、Jev 清理、评分/报告规则、DeepSeek 重试策略、基础设施或异步架构。
- 开始时保存初始 diff、文件 SHA-256 与两个重叠文件的内容。结束时其余 **60 个初始路径完全不变**；Workflow 相对 B1 仅移动查询到 try 内；原 Workflow 测试的每一行均按原顺序保留，只增加测试、断言和 import。B1 新并发测试及两份旧验收报告未被修改。

## 2. 修复前调用链与异常边界

当前调用顺序为：

```text
InterviewService.submitAnswer
  → 校验会话/题目归属、requestId、完整答案内容
  → 新答案：submitAnswer 短事务提交 SUBMITTED + 问题 ANSWERED
    已有同请求答案：按当前状态决定重放或再次执行
  → InterviewWorkflowService.evaluateAnswer（无外层事务）
      → 读取并校验 Answer / Question / Session / MAIN
      → B1 完成重放判断
      → 生成本次 attemptId
      → claimOrRecoverEvaluation
          → SUBMITTED claim / FAILED retry / stale EVALUATING reclaim
          → OWNED 或 REPLAY；无资格时拒绝或抛错
      → REPLAY 直接返回
      → findNextPendingMainQuestion                  ← 原来在 try 外
      → hasNextMainQuestion = nextMain != null        ← 原来在 try 外
      → try
          → Orchestration.evaluate
          → applyDecision：FOLLOW_UP / NEXT_MAIN / FINISH
          → withPersistedId
        catch RuntimeException
          → 按本次 attemptId 尝试 markEvaluationFailed
          → 原样抛出原异常
```

### 六个关键问题

1. **何时算成功领取？**

   `tryClaimEvaluation`、`tryRetryEvaluation` 或 `tryReclaimStaleEvaluating` 执行真实条件 UPDATE，受影响行数为 1，事务代理成功返回，随后 `claimOrRecoverEvaluation` 返回 `OWNED`。CAS 失败后的状态重读可能再进入有界 retry/reclaim，只有最终成功才是 OWNED；EVALUATED 是 REPLAY。生成 UUID 或看到一个旧状态快照都不等于取得资格。

2. **attemptId 何时写库？**

   32 位去连字符 UUID 先在 Workflow 内生成；随后在领取 SQL 中与状态一起更新。SUBMITTED/FAILED 分别变为 EVALUATING，stale reclaim 保持 EVALUATING 并刷新 `updated_at`。本调用链没有外层事务，事务服务的 `@Transactional` 默认 REQUIRED 因而各自开启短事务，成功提交后调用方才继续。不能将其误称为 REQUIRES_NEW。

3. **成功领取后原有哪些步骤不受 catch 保护？**

   `findNextPendingMainQuestion(session.getId(), mainQuestion.getPlanOrder())` 的查询和 `nextMain != null` 判断位于 try 外；前者会因数据库访问失败抛 RuntimeException。领取前的问题/会话读取、父 MAIN 解析与 claim 本身也在 try 外，但它们发生在本请求尚未确认取得资格时，责任不同。

4. **下一题查询抛异常后，原数据库是什么状态？**

   claim 已提交，所以 Answer 保持 `EVALUATING + 本次 attempt_id`，`error_code` 为空；正常 claim 更新还会刷新行的 `updated_at`。本次 Orchestration 尚未执行，没有新增评价或 RAG batch，Session 不推进。原调用栈直接向外抛查询异常，完全没有进入 FAILED 回写 helper。

5. **为什么不能立即走 FAILED 重试？**

   `InterviewService.handleExistingAnswer` 会让同 requestId、同题、同内容重新进入 Workflow，但实际状态仍是 fresh EVALUATING，`recoverStaleEvaluating` 返回冲突。`retryEvaluation` 的 SQL 只接受 FAILED。默认 stale 阈值为 10 分钟，至少 1 分钟；只有过期后再次请求才可竞争接管，没有后台定时恢复。没有后续请求时可一直停留，因此单靠 stale 不能代替已捕获异常后的及时失败回写。

6. **旧 attempt 为什么不能影响新任务？**

   `markFailed` 同时要求 `id`、`status='EVALUATING'` 和 `attempt_id` 一致。新任务接管后 attempt 已改变，旧任务 UPDATE 为 0 行；事务服务据此抛出 `Answer失败状态更新失败`。若新任务已完成，status 条件也会阻止修改。Workflow 把这个次要异常放入原异常的 suppressed，而不是覆盖原始查询错误。

## 3. 确定性复现及改前证据

先添加测试，保持 B1 后的生产文件不变，再运行 Maven。复现不依赖 sleep、随机线程调度或真实模型故障。

### Unit / Mockito

在既有 `InterviewWorkflowServiceTest` 上新增：

- SUBMITTED 首次领取、FAILED 重试领取、stale EVALUATING 接管，各自成功后注入下一题查询异常，验证原异常对象、SQLException cause 和回写使用的同一 attemptId。
- 查询异常之后，FAILED 回写又抛异常，要求仍抛原查询异常，并将回写异常置于 suppressed。
- claim/retry/reclaim 本身抛异常的三个用例，要求没有失败回写。

另给已有 CAS 失败、fresh/stale 拒绝与 REPLAY 用例增加 `never().markEvaluationFailed(...)` 断言。Mock 结果只用于证明调用顺序、参数和异常语义，不作为数据库状态证据。

### 真实隔离 MySQL

新增 `InterviewPostClaimFailureIntegrationTest`，无外围 `@Transactional`：

1. 为 MAIN/FOLLOW_UP 分别准备最小持久化 fixture，经真实 `transactions.submitAnswer` 提交答案。
2. 用独立 DataSource 连接确认初始 `SUBMITTED / attempt_id=null / error_code=null`。
3. 通过真实事务代理、真实 MyBatis XML 执行 claim。
4. 只在下一题查询的测试包装入口注入异常。入口先断言无活动事务，再用另一个连接读到已提交的 `EVALUATING + 32 位 attemptId`，随后抛出含 SQLException cause 的 `DataAccessResourceFailureException`。
5. 调用结束后再次通过独立连接读取 Answer，要求立即 FAILED，并保持同一 attemptId。对 Session 和问题整行、评价和 RAG batch 数量做断言。

正式改前运行 **44 tests / 7 failures / 0 errors / 0 skipped，退出 1**：

- 3 个单测发现应发生的 FAILED 调用未发生。
- 1 个单测发现 suppressed 为空，因为回写根本没有执行。
- MAIN/FOLLOW_UP 两个数据库用例实际得到 `AnswerState[status=EVALUATING, attemptId=本次值, errorCode=null]`，与预期 FAILED 明确不符。
- 另一个数据库用例固定“旧查询期间发生接管”的交错，发现旧路径没有执行回写，因而缺少预期的 fenced 回写异常；这不代表原代码曾覆盖新任务。

这 7 项均指向保护边界缺口，没有连接、Schema、SQL 执行或测试上下文错误。修复前测试首次编译曾因 AssertJ `singleElement()` 返回 ObjectAssert 而不能直接调用 `hasMessage()` 失败；改为在 `satisfies` 内断言 Throwable 后才得到上述正式复现，该编译失败单独记账，不算业务故障证据。

## 4. 最小修复与文件清单

生产逻辑只移动一个已有代码块：在 `claimOrRecoverEvaluation` 成功返回且排除 REPLAY 后立即进入现有 try，把下一题查询及 `hasNextMainQuestion` 计算放在其内部。评价、决策、返回、catch 和 helper 均保持原语义。

为什么从这里开始保护：数据库已经持久化本请求的执行资格，后续可捕获的运行时失败应尝试结束这一 attempt，使后续请求可以及时重试。不能把 try 向前扩到 claim 之前，否则读取失败、CAS 失败、正常重放或提交结果尚不确定的请求都可能误入失败处理。即便 SQL fencing 能挡住一部分错误请求，也不应主动发出没有所有权依据的状态更新。

本轮修改文件：

1. `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java`：将下一题查询移入成功领取后的异常保护范围；B1 的两处判断原样保留。
2. `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowServiceTest.java`：新增 7 项参数化展开后的测试，并加强 6 个既有无资格/重放用例的“不回写 FAILED”断言；当前该类实际执行 41 项。

本轮新增文件：

3. `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/InterviewPostClaimFailureIntegrationTest.java`：3 项真实 MySQL 验证，覆盖 MAIN/FOLLOW_UP 失败提交与同请求重试、旧查询失败与新 attempt 的隔离。
4. `/Users/kun/Developer/projects/ai-interview-system/docs/verification/B2_POST_CLAIM_FAILURE_RECOVERY_20261111.md`：本验收与学习报告。

未改事务服务、Mapper 接口/XML、配置、依赖、Controller、异常处理器或数据库结构。相对 B1 的生产差异已另存临时证据；逆向还原该唯一移动后与 B1 文件内容完全一致。

## 5. FAILED 回写的事务与异常保护

现有两层职责继续保留：

- Workflow 的私有 `markEvaluationFailed(answerId, attemptId, primaryException)` 负责保留原异常。它调用事务服务；若回写发生 RuntimeException，执行 `primaryException.addSuppressed(markFailedException)`；外层随后 `throw exception`，既不包装也不改 cause。
- 事务服务的公共 `markEvaluationFailed(answerId, errorCode, attemptId)` 使用 `@Transactional` 调用 Mapper，并要求恰好更新 1 行。真实 SQL 是：

```sql
UPDATE interview_answer
SET status = 'FAILED', error_code = #{errorCode}
WHERE id = #{id}
  AND status = 'EVALUATING'
  AND attempt_id = #{attemptId}
```

当前生产链无外层事务，claim 的提交不会被稍后的查询异常回滚；FAILED 是后续另一个短事务。回写也不会改变已提交的 attemptId，下一次 FAILED 重试会设置新的 attemptId。

旧任务晚到时，SQL 0 行是安全拒绝，不会将新任务改成 FAILED。数据库连接故障也可能令 FAILED 回写失败；此时仍保留最先发生的查询异常，附加次要异常，并依靠现有 stale 接管兜底。没有无限重试、循环补偿或统一转 HTTP 409 的逻辑。

## 6. 修复后覆盖及真实数据库证据

1. **下一题查询成功**：原 Workflow 的五条 MAIN/FOLLOW_UP 转移单测，以及 B1 真实并发业务链均通过。参数 `hasNextMainQuestion`、追问、推进和 FINISH 未变。
2. **领取后查询失败**：单测覆盖 claim/retry/reclaim 三种 OWNED 来源；真实 MySQL MAIN/FOLLOW_UP 均立即提交 FAILED，attempt 与 query 入口观察值相同，error_code 为 EVALUATION_ERROR。
3. **原异常优先**：单测及真实 MySQL 用例均验证异常对象 identity 和原 SQLException cause；未包装成 ConflictException。公共 HTTP 错误处理无修改，原数据库错误继续按既有分类处理。
4. **二次回写失败**：Mock 回写异常被精确保留于 suppressed。另一个真实 MySQL 用例使旧查询期间的本 fixture 过期，真实 reclaim 为新 attempt，然后旧查询抛错；真实 `markFailed` 更新 0 行，次要异常进入 suppressed，新 attempt 仍 EVALUATING。
5. **REPLAY 与未取得资格**：保留 B1 首次完成、旧快照完成的无事务服务调用断言；CAS 失败后完成重放、fresh/stale 冲突均明确不回写。claim/retry/reclaim 抛错不回写，查询和评价也不执行。
6. **同请求重试**：真实 MySQL 用例恢复真实下一题 SELECT，直接以同一个 request 对象调用 `InterviewService.submitAnswer`，没有改 requestId、问题或答案内容。新 attempt 已提交为 EVALUATING，旧错误码清空，随后真实完成事务写入评价和 RAG batch，Answer 为 EVALUATED，Session version=1、completedQuestionCount=1。再次重放返回相同响应；该题答案始终 1 行，评价及 batch 各 1 行。
7. **B1 不退化**：`InterviewConcurrentReplayIntegrationTest` 的 18 项全部通过，包括两类暂停点下的五条转移、已完成响应重建、fresh 冲突与身份/载荷边界。
8. **旧任务和 stale**：既有 `InterviewEvaluationClaimConcurrencyIntegrationTest` 的 11 项、`StaleRecoveryCasIntegrationTest` 的 3 项通过，包括真实双线程 claim/retry、stale 接管及迟到结果隔离。

新增集成测试真实执行 MySQL、MyBatis XML、Spring 事务代理、Workflow、InterviewService 与持久响应构建；只有查询故障入口及 Orchestration 是替身。FOLLOW_UP 使用预置的最小状态 fixture。旧查询接管用例固定顺序注入，不自称新的双线程竞态测试；真实多线程覆盖来自原有并发测试。没有切断真实网络来模拟 MySQL 故障，也没有调用真实 DeepSeek/Embedding/Milvus。

## 7. 执行环境、命令与精确结果

环境实测：macOS 26.7.1 / ARM64，Temurin Java 21.0.13，项目 Maven Wrapper 3.9.16。采用 `sh mvnw`，不是 Windows `.cmd`。

MySQL 为本轮独立容器 `ai-interview-b2-20261111-mysql`，已有镜像 8.0.45，回环端口 `127.0.0.1:13320`，内存数据目录，全局时区 `+08:00`。JDBC 仅为：

```text
jdbc:mysql://127.0.0.1:13320/ai_interview_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
```

DDL 只在临时副本替换建库/USE 目标。数据库清单仅有 `ai_interview_test` 与 MySQL 系统库。没有访问正式 `ai_interview` 或 evaluation 数据库，没有读取正式凭据或修改 `.env`/local 配置。初次普通沙箱 Docker 只读检查被权限边界阻止，授权执行后可用；这不是代码或测试失败。

每个 Maven 子进程清除所有继承的 RUN_REAL_*、SPRING_*、MILVUS_*、DEEPSEEK_*、JEV_*、AI_JUDGE_*、旧 DB_*/TEST_DB_* 及 JVM/Maven 覆盖，仅注入临时数据库连接。`MILVUS_ENABLED` 和各 RUN_REAL_* 均未设置；保留 test profile、EnvironmentIsolationInitializer 和 Smoke 双门禁。B1 的业务测试使用自身显式 Bean 装配和外部替身，不能据此认为真实服务被开启。

所有证据独立保存于 `/tmp/ai-b2-20261111/`。临时 `run_maven.py` 在每次运行前归档上一轮 Surefire 文件，运行后复制当次 XML 并汇总，避免历史文件混入计数。该执行器不是项目新增工具。以下 T/F/E/S 分别为 Tests / Failures / Errors / Skipped。

1. **新增测试首轮编译**：

   `sh mvnw -B -ntp -Dtest=InterviewWorkflowServiceTest,InterviewPostClaimFailureIntegrationTest test`

   退出 **1**，testCompile 失败，**执行测试 0 项**，JUnit F/E/S 不适用。原因及修正见第 3 节。证据：`reproduction-before.log`、`reproduction-before-summary.json`。

2. **正式改前复现**：同上命令，生产文件仍为 B1 后版本。

   **44 / 7 / 0 / 0，退出 1**。证据：`reproduction-confirmed.log`、`reproduction-confirmed-reports/`、`reproduction-confirmed-summary.json`。

3. **最小修复后定向验证**：同上命令。

   **44 / 0 / 0 / 0，退出 0，BUILD SUCCESS**。其中 Unit/Mock 41，新增真实 MySQL 3。证据：`focused-recovery.log`、`focused-recovery-reports/`、`focused-recovery-summary.json`。

4. **相关业务与并发回归**：

   ```text
   sh mvnw -B -ntp -Dtest=InterviewPostClaimFailureIntegrationTest,InterviewConcurrentReplayIntegrationTest,InterviewServiceTest,InterviewWorkflowServiceTest,InterviewWorkflowTransactionServiceTest,InterviewWorkflowTransactionServiceIntegrationTest,InterviewEvaluationClaimConcurrencyIntegrationTest,InterviewControllerTest,EvaluationOrchestrationServiceTest,StaleRecoveryCasIntegrationTest,InterviewAnswerMapperTest,InterviewSessionMapperTest,FullInterviewBusinessOfflineTest,JudgeConfigurationTest,DeepSeekJudgeProviderTest test
   ```

   **296 / 0 / 0 / 0，退出 0，BUILD SUCCESS**。按类实际计数：B2 集成 3；B1 集成 18；InterviewService 31；Workflow 41；WorkflowTransaction 单测 34、集成 6；评价并发 11；Controller 16；Orchestration 20；StaleRecoveryCas 3；AnswerMapper 6；SessionMapper 14；FullInterviewBusinessOffline 85；JudgeConfiguration 7；DeepSeekJudgeProvider 1。证据：`focused-business.log`、`focused-business-reports/`、`focused-class-counts.json`。

5. **完整普通回归**：

   `sh mvnw -B -ntp test`

   **1559 / 0 / 0 / 19，退出 0，BUILD SUCCESS**。相对 B1 本轮实际增加 Unit 7 + 集成 3，共 10 项；这只是结果核对，不是预设测试数量。证据：`full-regression.log`、`full-regression-reports/`、`full-regression-summary.json`。

6. **差异检查**：`git diff --check` **退出 0**；测试计数不适用。工作区保全无异常，证据 `preservation-check.json`。没有单独 package 或浏览器 E2E 执行，不把编译当成功能验收。

19 skipped 实际分布：KnowledgePersistenceIntegrationTest 10；RealEmbeddingSmokeTest 1；RealMilvusVectorStoreSmokeTest 1；RealKnowledgeDocumentProcessingSmokeTest 2；RealKnowledgeDocumentProcessingFailureIntegrationTest 2；RealMilvusSchemaValidationSmokeTest 2；AiSystemBaselineEvaluationTest 1。

验证结束后，本轮 fixture 的用户、题库问题、答案残留计数均为 0；只停止 B2 临时容器并删除其临时凭据文件。未停止其他容器、删除既有数据卷或改变正式数据。证据 `database-final-check.txt`、`cleanup.json`。临时日志不是长期交付材料，测试代码与本报告可长期复核；重跑需要重新准备隔离数据库和临时连接信息。

## 8. 尚未验证与残余风险

- 未执行真实 DeepSeek、Bailian Embedding、Milvus Smoke、AI baseline 或模型质量/费用实验；跳过不代表真实服务通过。真实隔离 MySQL 验证已执行，须与外部模型 Mock 区分。
- 故障点是可捕获的 RuntimeException。进程退出、JVM 严重错误、claim 提交结果不确定，以及 FAILED 回写因数据库不可用而失败，都仍可能留下 EVALUATING，继续依靠已有请求驱动 stale 机制。没有增加后台恢复或承诺外部调用 exactly-once。
- 新测试对 MySQL 提交、条件更新和 retry 有真实证据；对网络断连、连接池整体故障、长期宕机后的最终恢复没有新实测。没有为测试放松 fencing、安全检查或隔离规则。
- B3 的报告 FINAL-only 独立问题仍按 J1/B1 记录保留，本轮没有修复或验证其解决。未实现退避、限流、队列等后续改造，也未发现需要扩大 B2 的新阻断问题。

## 9. 约 14 分钟的源码学习路径

以下以 `/Users/kun/Developer/projects/ai-interview-system/` 为仓库根目录，行号对应本轮文件。

1. **0～2 分钟：提交与重试入口。** 打开 `src/main/java/com/kun/aiinterview/interview/service/InterviewService.java:223`，再读 `handleExistingAnswer:415`。找出身份校验、完整 payload 校验，以及新答案事务提交后才进入 Workflow 的位置。回答“为什么重试不重新插答案”。
2. **2～5 分钟：异常保护边界。** 打开 `src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java:48`，重点看 `:142` 的 attempt、`:145` 的 REPLAY、`:149` 的 try、`:178` 的 catch。区分领取前的合法性检查、拥有资格后的工作，以及正常重放。把下一题查询临时放回 try 外，在脑中推演异常后持久状态。
3. **5～7 分钟：领取和失败事务。** 读 `src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java:93` 至 `markEvaluationFailed:129`，再对照 `src/main/resources/mapper/InterviewAnswerMapper.xml` 的 claimEvaluation、retryEvaluation、reclaimStaleEvaluating、markFailed。说明“生成 attemptId”和“数据库授予执行资格”为何不同，以及 affectedRows=0 的含义。
4. **7～9 分钟：异常与迟到任务。** 回到 Workflow 的 `markEvaluationFailed:317`，理解 `throw exception` 与 `addSuppressed` 保留的信息。读 `claimOrRecoverEvaluation:188` 和 `recoverStaleEvaluating:259`，对照 `src/main/java/com/kun/aiinterview/common/recovery/StaleRecoveryProperties.java` 的 10 分钟阈值，解释为什么 FAILED 可立即重试而 fresh EVALUATING 不行。
5. **9～11 分钟：单测如何锁定边界。** 读 `src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowServiceTest.java:354`、`:389`、`:408`。分别找同一个 attemptId、同一个异常实例、SQLException cause、suppressed 和未领取不回写的断言。注意这些只证明 Java 调用约定，不证明 MySQL 提交。
6. **11～14 分钟：真实持久化证据。** 读 `src/test/java/com/kun/aiinterview/interview/service/InterviewPostClaimFailureIntegrationTest.java:93`、`:150`、`:177`。确认没有外围测试事务，查询故障之前独立连接已读到 EVALUATING，失败之后独立连接读到 FAILED，然后同请求完成；再看旧查询失败时新 attempt 不受影响。最后对照 B1 的 `InterviewConcurrentReplayIntegrationTest`，理解“重放正确”和“失败恢复正确”是两个独立边界。

读完应能解释：短事务领取完成后，后续查询失败不会自动撤销已提交的状态；需要拥有者在独立短事务里按 attemptId 条件回写。扩大异常保护范围不能跨越所有权确认点，原始错误也不能被恢复操作的第二个错误掩盖。

## 10. 交付状态

- **已开发**：成功领取后的下一题查询已纳入既有异常保护；生产只改一个方法内的边界。
- **已测试**：确定性改前失败、修复后 Unit/Mock、真实隔离 MySQL、B1 并发重放、相关事务及完整普通回归均有本轮证据；`git diff --check` 通过。
- **尚未验证**：真实外部模型/向量服务与真实网络故障恢复，详见第 8 节。
- **待后续处理**：用户审阅 diff 与本报告；B3 等独立任务留待另行授权。本轮无暂存、commit、push、stash、reset 或覆盖既有修改的操作。
