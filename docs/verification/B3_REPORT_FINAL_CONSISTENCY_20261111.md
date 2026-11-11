# B3：报告 FINAL-only 一致性审查与最小修复

执行日期：2026-11-11（Asia/Shanghai）。本轮已完成源码审查、改前复现、修复、真实隔离 MySQL 验证及普通完整回归。未暂存、未 commit、未 push；B3 完成后停止，不执行 B4。

## 1. 结论与实际基线

**问题真实存在。** 原查询不是严格 FINAL-only，而是“每道 MAIN 优先 FINAL，没有则用 INITIAL”。报告服务只检查候选数量，不能证明每条评价的答案归属正确。真实 MySQL 已证明 INITIAL 兜底、MAIN 错绑、跨 Session 答案、重复追问等异常可生成 READY 报告；Weakness 接收同一列表，也会受到影响。

本轮改为：查询只提供 FINAL 候选且不隐藏重复项；Java 核实 MAIN 计划覆盖、唯一性、顺序、答案完成状态与 MAIN/FOLLOW_UP 关联后，才允许计算和生成报告。已有非 READY 报告行的恢复分支也执行同一校验。已经 READY 的历史报告继续直接读取。

基线事实：

- 仓库：`/Users/kun/Developer/projects/ai-interview-system`，分支 `main`。
- HEAD：`afbd1c5ab010f3a0a296e15e25c016f81eb2b74f`；本机 `origin/main` 跟踪引用相同。本轮未 fetch，不将本机跟踪引用描述成新核验的远端状态。
- 开始时有 **64 个已有改动路径**，包含 J1/B1/B2、用户环境及文档修改；暂存区为空。各路径 SHA-256／删除状态已记录，结束时 **0 项意外变化**。本轮新增的修改没有覆盖这些路径。
- 已读取 `AGENTS.md` 及 J1、B1、B2 验收记录。J1 报告为 `JEV_REMOVAL_DEEPSEEK_ONLY_20261111.md`。这些批次的改动仍在工作区，未据历史文档推断它们已经发布。
- 历史普通回归记录分别为 J1 **1526/0/0/19**、B1 **1549/0/0/19**、B2 **1559/0/0/19**；它们不是本轮测试结果。本轮实测见第 7 节。
- 未发现需要中止 B3 的既有阻断缺陷。没有执行 reset、clean、stash、commit、push、Schema 迁移或生产数据修复。
- 实测环境：macOS **26.7.1 / ARM64**，Temurin **21.0.13**，Maven Wrapper **3.9.16**。以实际环境为准，使用 `sh mvnw`。

## 2. 业务规则与七个重点问题

1. **原 SQL 如何选择？** `answer_evaluation` 按 `main_interview_question_id` 分组，以 `FINAL → INITIAL` 为优先级，再按评价 ID 倒序，取 `ROW_NUMBER() = 1`。只通过评价声明的 MAIN ID 关联问题；没有核对 `answer_id` 的实际问题归属。
2. **何时会选 INITIAL？** 某道 MAIN 没有任何被该查询视为 FINAL 的评价，但至少有 INITIAL 时。即使 INITIAL 的追问尚未完成，也会返回它。
3. **正常完成的 Session 会缺 FINAL 吗？** 在正常业务链、合法输入及事务成功的前提下不应发生。`FollowUpPolicy` 对无追问 MAIN 和 FOLLOW_UP 生成 FINAL，对需追问 MAIN 生成 INITIAL；`EvaluationDecision` 限制 INITIAL 只能配 FOLLOW_UP。`InterviewWorkflowTransactionService` 在推进／完成事务内持久化评价、标记答案 EVALUATED，并按 version/current-question CAS 推进 Session，失败整体回滚。Session 的 COMPLETED 标记本身不等于对历史或异常数据的完整性证明。
4. **缺 FINAL 会采用 INITIAL 吗？** 原来会。改前真实 fixture 的 `initial-only` 实际结果为 `failure=NONE, status=READY, report=true, generatorCalls=1`。完全没有评价时，原数量检查已经会拒绝。
5. **条数正好等于计划题数仍会错误生成吗？** 会。两道 MAIN 对调评价归属、某条评价指向其他 Session 的答案，以及 INITIAL + 合法 FINAL，都可满足旧数量检查。修复后还核对 MAIN ID 集合、唯一性、计划序号、实际最终答案，不能用总条数替代这些约束。
6. **报告与 Weakness 是否共用选择逻辑？** 是。ReportService 只查询一次用于新报告的 FINAL 列表，将同一列表交给生成器及完成事务；完成事务再交给 UserWeaknessService。Weakness 不另选评价，本轮额外在其写入前拒绝非 FINAL。
7. **已有 READY 报告是否受影响？** GET 继续读取持久化报告；重复 POST 在 READY 分支直接返回既有报告，不重新查询评价、不调用生成器、不更新薄弱点。即使历史源数据只有 INITIAL，已经 READY 的报告仍可读。本轮不会纠正其历史分数或内容。

`AGENTS.md` 的顶层规则要求 FINAL-only，但其 2026-09-17 历史能力记录明确写了 INITIAL 兜底；原 Mapper 测试也期待兜底。本轮按用户明确的 B3 规则调整现行行为，并在此记录差异，不改写历史验收事实。

## 3. 修复前 SQL 与调用链

原查询核心：

```sql
ROW_NUMBER() OVER (
    PARTITION BY ae.main_interview_question_id
    ORDER BY CASE ae.evaluation_phase
                 WHEN 'FINAL' THEN 1
                 WHEN 'INITIAL' THEN 2
             END,
             ae.id DESC
) AS evaluation_priority
-- WHERE iq.session_id = sessionId AND iq.question_type = 'MAIN'
--   AND ae.evaluation_phase IN ('INITIAL', 'FINAL')
-- 外层 WHERE evaluation_priority = 1，ORDER BY plan_order
```

原调用顺序：ReportService 校验所有者和 COMPLETED → READY 直接返回，或 CAS 领取 GENERATING → 查询有效评价 → 只校验非空和数量 → Java 平均分 → ReportGenerator → 完成事务 INSERT report → Weakness 按知识点聚合写入 → Session READY。

另有恢复分支：领取 GENERATING 后若发现已有报告行，只检查 overallScore 非空，便恢复 READY；它原来完全绕过评价查询。修复主要路径后，补充的两个真实 MySQL 用例证明 FAILED／stale GENERATING 都存在这一旁路，因此一并纳入相同校验。

数据库外键保证引用的行存在，`UNIQUE(answer_id)` 保证每个答案最多一条评价，但并不保证“评价声明的 MAIN”就是“答案所属 MAIN”，也不能以此证明 Session 覆盖完整。无需修改 Schema，由报告聚合边界拒绝不一致数据即可。

## 4. 复现证据与最小修复

### 4.1 先复现后修改

先增加 ReportService、Weakness 单测和 `InterviewReportFinalConsistencyIntegrationTest`，保持生产 Mapper 和服务不变。正式改前运行 `reproduction-verified` 为 **55 tests / 14 failures / 0 errors / 0 skipped，退出 1**。

- 两个单测分别证明报告与 Weakness 未拒绝 INITIAL。
- 集成测试当时有 16 项：11 类异常数据都错误进入 READY，另一个“缺 FINAL 失败后重试”用例因首次没有失败而失败，共 12 个业务断言失败。
- 正常无追问、正常 INITIAL + FOLLOW_UP FINAL、完全缺评价的既有拒绝、历史 READY 重复读取这 4 项通过。
- 11 类错误放行分别是 INITIAL-only、MAIN 归属对调、跨 Session 答案、错误追问父 MAIN、错误追问 Session、已有追问却使用 MAIN FINAL、重复追问 FINAL、答案未完成、实际 MAIN 多于计划题数、FINAL 配 FOLLOW_UP 决策、追问缺 MAIN INITIAL。
- 这些异常通过合法 SQL 写入测试 fixture，未禁用外键或 CHECK 约束，也未调用 DeepSeek 制造异常。

测试准备阶段曾发生必填字段遗漏、总分 CHECK 约束和 JDBC 数值类型断言错误，均单独记账，详见第 7 节；这些错误不作为生产缺陷证据。

### 4.2 查询和 Java 边界

新 SQL 保留原字段投影和 MAIN 快照关联，核心为：

```sql
FROM answer_evaluation ae
INNER JOIN interview_question iq ON iq.id = ae.main_interview_question_id
WHERE iq.session_id = #{sessionId}
  AND iq.question_type = 'MAIN'
  AND ae.evaluation_phase = 'FINAL'
ORDER BY iq.plan_order ASC, ae.id ASC
```

删除窗口排名，不再用 INITIAL 兜底，也不再用“最新 ID”掩盖同一 MAIN 的多个 FINAL。Mapper 返回的是候选，合法性由 Java 明确检查：

1. 候选数量等于计划 MAIN 数量，phase 必须是 FINAL，MAIN ID 非空且不能重复，决策只能是 NEXT_MAIN 或 FINISH。
2. 复用现有 `InterviewQuestionMapper.listBySessionId`，核对实际 MAIN 集合与候选集合、数量完全一致，MAIN `planOrder` 从 1 连续递增且与评价顺序一致。
3. 问题属于当前 Session 且已 ANSWERED；每个 FOLLOW_UP 必须指向本场 MAIN，每道 MAIN 最多一个追问。
4. 复用现有批量答案查询；MAIN 答案必须 EVALUATED。有追问时，最终评价只能对应该追问的 EVALUATED 答案；无追问时只能对应 MAIN 答案。
5. 有追问时用既有 `getByAnswerId` 核对父 MAIN 的 INITIAL、FOLLOW_UP 决策和 MAIN 归属。INITIAL 仅用于证明关联链完整，不进入分数或薄弱点统计。

校验失败统一抛出带明确内部原因的 `IllegalStateException`。外层保留原异常，按本次领取的 version 尝试 GENERATING → FAILED；没有改成 BusinessException 或普通用户输入错误。既有异常处理器对外返回 HTTP 500 和通用系统错误信息，本轮没有变更 API 契约。

### 4.3 事务、评分及恢复

- `InterviewReportTransactionService` 生产代码未修改：INSERT report → Weakness → READY 仍在同一个 Spring 事务。READY CAS 失败或 Weakness SQL 失败仍整体回滚。
- Java 平均分仍使用 BigDecimal、两位小数、HALF_UP；等级仍由既有 EvaluationStandard 计算。没有让 LLM 改总分、等级或业务状态。
- Weakness 仍按 `category + knowledgePoint` 聚合，同场同知识点最多一次更新；`weaknessScore = 100 - 平均 FINAL 分数`，原 discovered_count 与 ACTIVE/RESOLVED 规则不变。
- 已有非 READY 报告行在恢复 READY 前也执行 `validateEvaluations`。发现不一致则保留报告行，Session 按原失败恢复流程处理；不删除、重写或重算历史报告。
- READY 查询与重复生成不进入新校验。FAILED 可再次领取；如果 FINAL 仍缺失，继续受控失败。测试仅在自己的 fixture 中补齐合法追问 FINAL 后证明重试成功，生产没有新增自动补数据逻辑。
- 没有引入新基础设施、事务抽象、后台任务、模型重试或限流。

## 5. 文件清单与代码差异

本轮修改的生产文件：

- `/Users/kun/Developer/projects/ai-interview-system/src/main/resources/mapper/AnswerEvaluationMapper.xml`：FINAL-only；删除排名兜底，保留重复候选与 MAIN 顺序。
- `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/mapper/AnswerEvaluationMapper.java`：补充候选查询契约注释，方法签名不变。
- `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java`：增加两个现有 Mapper 依赖、MAIN/答案/追问校验；非 READY 恢复复用同一入口。原分数和完成事务不变。
- `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/user/service/UserWeaknessService.java`：在任何 Weakness 写入之前拒绝非 FINAL。

本轮修改的既有测试：

- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/mapper/AnswerEvaluationMapperTest.java`：保留 INITIAL-only 场景，改为断言空列表；顺序测试中的无追问题改用合法 FINAL，继续验证 Session 隔离和追问合并。
- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/InterviewReportServiceTest.java`：新增 INITIAL、null phase、重复 MAIN、外部 MAIN、错误答案拒绝测试；原正常夹具补齐 phase、决策及来源，恢复已有报告的测试补齐合法 FINAL。
- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/InterviewReportApiServiceTest.java`：仅适配新构造器依赖。
- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/InterviewReportTransactionServiceIntegrationTest.java`：为原部分 Mock 的并发测试补齐来源夹具；实际事务、SQL、回滚和 fencing 断言保留。
- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/user/service/UserWeaknessServiceTest.java`：新增 INITIAL 拒绝且零 Mapper 写入断言；原合法夹具标记 FINAL。

本轮新增文件：

- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/InterviewReportFinalConsistencyIntegrationTest.java`：24 项真实 MySQL 验证，无外围测试事务；只有报告文本生成器为确定性替身。
- `/Users/kun/Developer/projects/ai-interview-system/docs/verification/B3_REPORT_FINAL_CONSISTENCY_20261111.md`：本验收报告。

没有删除任何既有测试。旧 Mapper 的“INITIAL 是最终结果”断言与本轮规则直接冲突，调整为拒绝；不是减少覆盖以通过测试。其他夹具调整是补足合法业务前提，原回滚、幂等及并发断言没有放松。

## 6. 真实 MySQL 验证范围与证据

本轮独立容器：`ai-interview-b3-20261111-mysql`，MySQL **8.0.45**，回环端口 **13321**，临时内存数据目录，全局时区 `+08:00`。连接仅为：

```text
jdbc:mysql://127.0.0.1:13321/ai_interview_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
```

DDL 只在临时副本改建库／USE 目标，正式 Schema 文件未改。数据库清单只有 `ai_interview_test` 和 MySQL 系统库。新测试每次首先断言 `SELECT DATABASE() = ai_interview_test`。没有访问正式 `ai_interview` 或 evaluation 库，没有复制正式凭据或改 `.env`／local 配置。

最终新增集成类 **24/0/0/0**：

- 2 项正常路径：无追问时 MAIN 60 + MAIN 80，报告 70.00、Weakness 30.00；有追问时 MAIN INITIAL 20 被排除，FOLLOW_UP FINAL 70 + 另一 MAIN FINAL 80，报告 75.00、Weakness 25.00。追问的知识点标签故意不同，断言仍聚合在父 MAIN 的 HashMap；每场 discovered_count 为 1。
- 17 项异常：上文 11 类，以及明确存在追问但缺 FINAL、完全缺评价、重复 FINAL 与缺失评价数量相抵、已有合法 FINAL 又多一条外部答案 FINAL、问题未 ANSWERED、MAIN 计划序号断裂。全部在生成器之前失败，无新报告、无 Weakness 写入，评价整行快照不变。
- 1 项 FAILED 重试：缺 FINAL 连续两次失败；fixture 补齐合法追问 FINAL 后成功，重复查询／生成不重复写入。
- 1 项历史 READY：源数据 INITIAL-only，预置历史报告 42.00；重复 GET／POST 保持报告、Session、Weakness 整行不变，生成器调用 0。
- 2 项既有非 READY 报告恢复：FAILED、过期 GENERATING 都不能把缺 FINAL 的数据重新标成 READY；已有报告行保持原样。
- 1 项真实 report INSERT 失败：向完成事务提供 summary=null，触发 MySQL NOT NULL，报告与 Weakness 均无新增，Session 保持 GENERATING；再调用同一失败 CAS 并正常重试成功。此用例直接验证事务边界，外层失败回写由既有 ReportService 单测另行覆盖。

既有真实 MySQL 事务测试继续证明：READY 版本失败时 report 和已更新的 Weakness 一并回滚；Weakness SQL 失败时 report 回滚；报告并发领取、stale 接管及旧 version 隔离仍通过。新类使用真实 MyBatis、真实 Report/Weakness 事务代理，没有用 Mock 结果冒充数据库提交证据。

测试完成后，B3 用户、题库问题、答案和报告残留计数均为 0。仅停止 B3 自建容器并移除随机测试凭据；未停止其他容器、删除原有数据卷或操作历史业务数据。

## 7. 测试命令与精确结果

执行器：`/tmp/ai-b3-20261111/run_maven.py`。每次运行前归档上一轮 Surefire，结束后复制本轮 XML，计数不混入历史文件。Maven 子进程清除继承的 RUN_REAL_*、SPRING_*、MILVUS_*、DEEPSEEK_*、JEV_*、AI_JUDGE_*、DB_*/TEST_DB_* 及 JVM/Maven 覆盖，只注入临时隔离数据库连接。`MILVUS_ENABLED` 与所有 `RUN_REAL_*` 均 unset，test 配置、EnvironmentIsolationInitializer 和 Smoke 双门禁保留。

以下 T/F/E/S 为 Tests / Failures / Errors / Skipped；每项均为实际执行。

**复现命令 R：**

```text
sh mvnw -B -ntp -Dtest=InterviewReportServiceTest,UserWeaknessServiceTest,InterviewReportFinalConsistencyIntegrationTest test
```

1. `reproduction-before`，R：**55/2/16/0，退出 1**。集成夹具漏填 retrieval_batch_id；2 个单测已暴露缺陷，16 个夹具错误不算业务证据。
2. `reproduction-confirmed`，R：**55/2/16/0，退出 1**。补齐前一字段后暴露 raw_result 漏填；同样不能算集成复现。
3. `reproduction-valid-fixture`，R：**55/2/16/0，退出 1**。维度固定 12 与 total_score 不一致触发 CHECK；随后使五维与总分一致，没有关闭或降低约束。
4. `reproduction-final`，R：**55/16/0/0，退出 1**。14 个目标缺陷断言之外，2 个正常路径的 discovered_count 断言将 JDBC Long 与 Integer 比较，已改为 Number.longValue；这 2 个不是生产失败。
5. **正式改前 `reproduction-verified`**，R：**55/14/0/0，退出 1**。生产仍未修改，正常路径和历史 READY 通过，14 项均为目标拒绝断言失败。

**首轮修复后定向命令 F：**

```text
sh mvnw -B -ntp -Dtest=InterviewReportServiceTest,InterviewReportApiServiceTest,InterviewReportTransactionServiceTest,InterviewReportTransactionServiceIntegrationTest,InterviewReportFinalConsistencyIntegrationTest,AnswerEvaluationMapperTest,UserWeaknessServiceTest,UserWeaknessMapperTest test
```

6. `focused-final`，F：**93/0/0/0，退出 0**。
7. `recovery-gap-reproduction`：`sh mvnw -B -ntp -Dtest=InterviewReportFinalConsistencyIntegrationTest#existingUnreadyReportCannotPromoteInvalidSources test`，**2/2/0/0，退出 1**。证明当时仍未修改的非 READY 恢复分支绕过校验；确认后才修复该分支。

**扩展报告回归命令 E：**

```text
sh mvnw -B -ntp -Dtest=InterviewReportServiceTest,InterviewReportApiServiceTest,InterviewReportTransactionServiceTest,InterviewReportTransactionServiceIntegrationTest,InterviewReportFinalConsistencyIntegrationTest,AnswerEvaluationMapperTest,UserWeaknessServiceTest,UserWeaknessMapperTest,InterviewReportPromptBuilderTest,DeepSeekInterviewReportGeneratorTest,DeepSeekInterviewReportGeneratorConfigurationTest,HttpErrorTaxonomyControllerTest test
```

8. `focused-complete`，E：**134/0/0/0，退出 0**。随后补充一项明确存在追问但缺 FINAL 的 fixture，由下面两轮验证。

**J1/B1/B2 与最终 B3 集成回归命令 B：**

```text
sh mvnw -B -ntp -Dtest=InterviewPostClaimFailureIntegrationTest,InterviewConcurrentReplayIntegrationTest,InterviewServiceTest,InterviewWorkflowServiceTest,InterviewWorkflowTransactionServiceTest,InterviewWorkflowTransactionServiceIntegrationTest,InterviewEvaluationClaimConcurrencyIntegrationTest,InterviewControllerTest,EvaluationOrchestrationServiceTest,StaleRecoveryCasIntegrationTest,InterviewAnswerMapperTest,InterviewSessionMapperTest,FullInterviewBusinessOfflineTest,JudgeConfigurationTest,DeepSeekJudgeProviderTest,InterviewReportFinalConsistencyIntegrationTest test
```

9. `focused-business`，B：**320/0/0/0，退出 0**。其中 B1 集成 18、B2 集成 3、B3 集成 24；J1 的完整离线业务链 85 项继续通过。
10. **完整普通回归**：`sh mvnw -B -ntp test`，**1589/0/0/19，退出 0，BUILD SUCCESS**。证据为 `full-regression.log`、`full-regression-reports/`、`full-regression-summary.json`。最终 ReportService 单测 24 项，Weakness 单测 19 项，新真实集成 24 项。
11. **J1 工具 gate**：`python3 -B tools/check_deepseek_only.py`，**37/0/0/0，退出 0**；21 个 Java 工具源文件编译、9 项静态检查通过。输出：`/Users/kun/Developer/projects/ai-interview-system/target/deepseek-only-b5db801c97b54826ba1d6407df1a383d/`。三个退役入口子命令退出 1 是预期 RETIRED 拒绝，不是 gate 失败；模型调用 0，数据库访问 false。
12. `git diff --check`：**退出 0**，测试计数不适用。未单独执行 package，不把 test 阶段编译描述成功能验收。

完整回归的 19 个跳过项：KnowledgePersistenceIntegrationTest 10；RealEmbeddingSmokeTest 1；RealMilvusVectorStoreSmokeTest 1；RealKnowledgeDocumentProcessingSmokeTest 2；RealKnowledgeDocumentProcessingFailureIntegrationTest 2；RealMilvusSchemaValidationSmokeTest 2；AiSystemBaselineEvaluationTest 1。跳过不是这些真实服务验证成功。

临时证据目录：`/tmp/ai-b3-20261111/`。各运行均有对应 `*-summary.json`、日志和独立 XML；另有 `initial-status.txt`、`initial-hashes.json`、`preservation-check.json`、`database-final-check.txt`、`cleanup.json`、`diff-check.txt`。临时凭据已经移除，重跑需重新准备隔离数据库。最初普通沙箱访问 Docker 被权限边界阻止，获准后创建临时容器成功；这不是代码失败。

## 8. 历史数据影响、未验证场景与限制

- 没有迁移、删除或修改现有评价、报告、薄弱点数据。测试中的异常注入与清理只针对每项测试新建的 fixture。
- 已 READY 的历史结果保持可读，过去已计入的 INITIAL 分数或错误 Weakness 不会自动被纠正。本轮也未扫描真实历史库，因此不能声称历史数据没有此类问题。
- 非 READY 的异常会话现在受控失败，直到其数据满足业务约束；这不是新增自动修复、自动扫描或重试任务。正常 FAILED 重试、READY 幂等和 stale/version fencing 保留。
- 未执行真实 DeepSeek、Bailian Embedding、Milvus Smoke、AI baseline、模型质量／费用实验、浏览器 E2E或 Windows PowerShell 执行。报告文本和外部服务行为通过确定性替身／Mock HTTP 验证。
- 报告输入校验使用既有只读查询；问题与评价在正常 COMPLETED 业务路径上不再被修改。本轮没有针对管理员绕过业务直接并发改库的情况增加跨外部调用长事务或锁，也没有进行这类并发篡改实验。
- 每场额外批量读取问题及答案；每个有追问的 MAIN 再查一次 INITIAL 来核实关联。未进行大规模性能基准，也未增加缓存或通用数据访问抽象。
- 进程崩溃、数据库长期不可用、FAILED 回写失败等仍依赖原有版本保护及请求驱动 stale 恢复；本轮不宣称解决所有报告故障。

## 9. 约 14 分钟源码学习路径

1. **0～2 分钟：先确定什么是 FINAL。** 阅读 `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpPolicy.java` 与同目录 `EvaluationDecision.java`。说明“有追问的 MAIN 为什么是 INITIAL，追问 FINAL 为什么代表父 MAIN”。
2. **2～4 分钟：确认完成态来自事务。** 阅读 `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java:285` 和 `:352`。找出评价持久化、答案 EVALUATED、Session CAS 在同一事务的位置，解释异常数据与正常工作流的区别。
3. **4～6 分钟：读 SQL 与候选契约。** 打开 `/Users/kun/Developer/projects/ai-interview-system/src/main/resources/mapper/AnswerEvaluationMapper.xml:215`，对照本报告的旧 SQL。解释为什么删除 INITIAL 兜底后，还需要删除 ROW_NUMBER 隐藏重复的行为。
4. **6～9 分钟：读报告完整性校验。** 打开 `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java:248`。逐项找 phase、MAIN 唯一集合、计划顺序、最终答案与父 MAIN INITIAL 的检查，再看 `:208` 的非 READY 恢复复用。用“两条评价都挂在同一道 MAIN”反证数量检查不够。
5. **9～11 分钟：读评分与原子提交。** 继续看 ReportService `:344` 的 BigDecimal；再读 `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java` 和 `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/user/service/UserWeaknessService.java:26`。解释先按知识点聚合再写库、失败整体回滚的原因。
6. **11～14 分钟：用测试复核推理。** 阅读 `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/InterviewReportFinalConsistencyIntegrationTest.java:116`、`:140`、`:214`、`:228`，对照旧库复现和新库拒绝。最后看原事务集成测试的 READY CAS 回滚及并发版本用例，区分 Mock 调用验证、真实 SQL 和真实事务证据。

## 10. 四道 Java 后端面试追问及参考答案

**1. 数据库有外键和唯一索引，为什么还要在 Java 校验评价归属？**

外键只证明引用存在，`UNIQUE(answer_id)` 只证明一个答案最多一条评价。它们不自动证明该答案属于评价声明的 MAIN，也不证明每道计划 MAIN 都有一个 FINAL。这里需要跨问题类型、父子关系、Session、答案状态的业务不变量，报告边界用实际计划和答案进行校验，比修改冻结 Schema 更直接。

**2. 为什么不能用 INITIAL 兜底？为什么条数相等仍不够？**

INITIAL 表示追问前的阶段性结果，不是最终成绩；接受它会掩盖尚未完整完成的评价链。数量相等只说明列表长度符合要求，无法排除重复 MAIN、遗漏 MAIN 与错绑答案。应同时检查 FINAL、MAIN 唯一集合与计划一致，以及评价对应 MAIN 或唯一追问的最终答案。

**3. 为什么报告生成不包在一个长事务里，但报告与 Weakness 又必须同事务？**

外部模型调用耗时且可能失败，放在数据库事务中会长期占用连接和锁。先用 CAS 领取生成资格，在事务外校验、算分并调用生成器，最后用短事务写 report、Weakness、READY；任一步失败全部回滚。version 条件阻止旧任务覆盖接管任务，减少长事务并不等于放弃原子性。

**4. 修复后如何兼顾幂等、失败重试和历史兼容？**

READY 请求返回已存报告，不重新算分或增加 discovered_count，因此历史结果保持可读且重复请求无写入。FAILED 请求可重新 CAS 领取，但必须重新通过 FINAL 完整性校验。已有报告行但 Session 非 READY 的恢复也必须校验，避免旁路；发现异常应保留历史行并受控失败，不能静默删除历史数据或用 INITIAL 冒充成功。

## 11. 交付状态

代码已开发、编译并通过单测、Mock HTTP、真实隔离 MySQL 和完整普通回归；没有把这些结果描述为真实模型验证。生成文件的实际绝对路径已列于第 5 节。未暂存、未 commit、未 push，无新增基础设施、Schema 或 API 变更。

下一步仅为用户审阅本轮 diff 与本报告。本轮停止在 B3，不自动开展 B4。
