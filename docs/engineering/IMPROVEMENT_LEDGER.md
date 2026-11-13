# 工程优化台账

建立／最近核验日期：2026-11-13（Asia/Shanghai）。仓库：`/Users/kun/Developer/projects/ai-interview-system`。

本台账记录可追溯的工程事实，不替代原验收报告。源码学习及掌握证据独立维护于 [SOURCE_LEARNING_BACKLOG.md](SOURCE_LEARNING_BACKLOG.md)。初次建档只修改文档；同日 B4-D 后续开发、回归和当前 Git 状态另见本批记录，不改写历史验收。

## 1. 维护口径与证据基线

- 当前登记 **12 条已开展批次记录**：J1、B1、B2、B3、B4-A、B4-B、B4-C、B4-D、B4-E、B5-0、B5-P2-A、B5-P2-B；另有 **4 条未来编号候选**：B6、B7、B8、B9。B5-0仅审查、测试与计划，不计为业务修复。C0/C3是累计验收证据，不重复计为实现批次；B5-0提出的B5-FIX-A已获本轮明确授权，以B5-P2-A完成密码边界修复；B5-FIX-B亦已获明确授权，以B5-P2-B完成Embedding日志边界修复。
- 2026-11-13 C6 新增累计审查／证据复核记录，不增加生产实现批次；12条批次、4条候选口径不变。C6没有修改生产／测试源码，没有重新执行功能测试或构建；经快照、依赖与原始证据核实复用最新后端1832/0/0/19和前端336/0/0/0，详见[C6收口](../verification/C6_RELIABILITY_PHASE_CLOSEOUT_20261113.md)。
- 此处 B1/B2/B3 是 2026-11 的并发、恢复、报告修复，不是 AGENTS 历史章节中的 Embedding B1、Milvus B2 或 Phase 5 Batch 1～3。
- 区分设计／审计完成、已编码、已编译、已测试、已打包、已暂存、已本地提交、已进入远端分支。测试通过与开发者掌握源码分别记账。
- `T/F/E/S` 表示 Tests / Failures / Errors / Skipped。完整测试数包含跳过；不同轮次存在重叠，禁止相加称为独立用例总数。编译／`-DskipTests package` 的测试计数不适用。
- 历史批次中的“通过”指链接报告对应的历史运行；初次建档汇总现存XML属于复核，不是重新执行。后续B4-D/B4-E/B5-0明确标注各自新执行结果，尤其B5-0完整回归不得混写成历史重算。未复核的工具结果明确引用报告。
- Unit/Mockito 验证 Java 分支和调用契约；Mock HTTP 验证协议映射；MockMvc 不代表浏览器或真实监听端口。真实 MySQL 表示实际执行 MyBatis SQL／事务，但模型仍可为替身。
- `FullInterviewBusinessOfflineTest` 有类级 `@Transactional`：是真实安全链／业务／MySQL + Mock HTTP 的离线业务链，不能独立证明跨请求提交或外部调用不在事务内。独立无外围事务的并发／事务测试另列。
- Loopback HTTP 使用真实 JDK 网络栈与本地合成服务，既不是纯 Mock HTTP，也不是真实 DeepSeek。真实模型可用性、费用、质量和浏览器端到端验证须分别提供证据。
- 不将验收日志耗时写成性能提升，不将预算估算写成 P95/SLA，不将 skipped 写成真实服务通过。

### 1.1 Git 实际状态

2026-11-13 C7 本地提交更新：用户明确授权按 C6 五批计划执行 `git add` / `git commit`，未授权推送。四批代码已依次形成以下本地提交（每项父提交为上一项，首项父提交为原 HEAD `01ac774caff491b978dd2268e8b427355a64b97a`）：

- B4-D：`f71da96acb2fb8d8eec378341f7d9358e31eca19`，11 个文件。
- B4-E：`f51f294ac83cf73b3489883a9cea04f031993d53`，5 个文件。
- B5-P2-A：`6c4993c0f3948a84067b3d3372371db02d0cf5bc`，21 个文件。
- B5-P2-B：`71aeb22f7da906660763e9efb1100a20df7705c9`，7 个文件；也是本节对应的累计源码提交。

四批逐提交文件、完整 patch、hunk 与 C6 计划一致；代码提交后独立导出版本的后端编译／前端类型检查通过，详见 [C7 记录](#c7)。本节与学习清单的 Git 状态补记随 C6 第 7.5 节规定的 6 个文档路径单独归档，不改旧验收报告。该文档提交的最终完整 SHA、暂存区及领先计数由 C7 提交后核验记录给出，不在提交自身内伪造自引用 SHA。

本轮 GitHub 连接器只读核实远端 `main` 仍为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，与缓存 `origin/main` 一致；四个代码提交后为 0 落后／7 领先。没有 fetch、push 或改写历史。以下 C6 及更早段落均保留其验收时点事实，其中“未提交”不代表 C7 之后的状态。

2026-11-13 C6最新核验：分支main，HEAD仍为`01ac774caff491b978dd2268e8b427355a64b97a`；GitHub连接器只读核实远端main仍为`89b77dc082272e38cfbbecb4b57c37edae9ee74d`，祖先计数0/3。入场54个变动路径，新增C6报告后55项；暂存区为空，HEAD/index未变。C6仅更新本台账、学习清单并新增报告，其他996个入场文件正文/mode保全，包括mvnw 0755、Compose、MQ文档及两处.DS_Store。B4-D/E、P2-A/B及工程文档仍无提交SHA，未推送；五批提交清单及全部hunk归属见[C6第7节](../verification/C6_RELIABILITY_PHASE_CLOSEOUT_20261113.md#7-git-提交规划仅计划未暂存)。

2026-11-13 B5-P2-B再次核验：本地HEAD `01ac774caff491b978dd2268e8b427355a64b97a`，GitHub连接器实时远端main `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，祖先0/3。Embedding边界、本轮测试和三份文档均为未暂存工作区变动，无本批SHA，未提交/推送。入场47个已有变动文件仅两份授权工程清单更新，其余45个正文及mode保全。见[本批记录](#b5-p2-b)。

2026-11-13 B5-P2-A再次核验：HEAD与实时远端main仍分别为`01ac774caff491b978dd2268e8b427355a64b97a`和`89b77dc082272e38cfbbecb4b57c37edae9ee74d`，0/3；本批叠加密码修复及测试/文档，暂存区为空。入场已有文件除本批指定两份台账追加外均保全，未提交/推送，详见[本批记录](#b5-p2-a)。

本节下方为初次建档时的历史快照。2026-11-13 B5-0再次实时核验：本地HEAD与远端main仍为下述两个SHA，领先3；B4-D/E及工程文档已形成未提交树，B5-0只修改4份指定文档。当前入场24路径及保护结论见[本轮记录](#b5-0)，不能把初次建档“仅mvnw/Compose”当作当前入场状态。

2026-11-13 只读核验：`git status --short --branch`、`git log`、逐提交 `git show --name-status`、祖先关系检查；另经 GitHub 连接器实时读取 `kun520c/ai-interview-system` 的 `main` 提交。

- 本地分支 `main`，HEAD：`01ac774caff491b978dd2268e8b427355a64b97a`。
- 本地缓存 `origin/main` 与实时远端 main 均为 [`89b77dc082272e38cfbbecb4b57c37edae9ee74d`](https://github.com/kun520c/ai-interview-system/commit/89b77dc082272e38cfbbecb4b57c37edae9ee74d)。未执行 fetch，也没有把缓存引用单独当作实时证据。
- J1、B1、B2、B3、C0 提交均为该远端 SHA 的祖先：已在远端 main。B4-A、B4-B、B4-C/C3 三个提交已在本地 main，尚未进入本次核验的远端 main；未穷举其他远端分支，不声称它们从未发布到任何位置。
- 原验收报告中的“未提交／未推送”保留为当时事实；当前状态由本节 Git 证据更新，不能照抄历史结束语。
- 入场暂存区为空，既有未提交变动仅 `mvnw` 的 `100644 → 100755` 和未跟踪的 `compose.mac.local.yaml`，不归入优化批次。本次保留两者内容与权限。
- 本次文档变动：新增本台账、源码学习清单，修改根 `AGENTS.md` 维护说明；未暂存、未 commit、未 push，无本次提交 SHA。

前置测试修正另有已推送提交 `b1f8c6b0d555fe624bfb027a741ac52563ee6d50`（`ConcurrentPasswordChangeIntegrationTest` 的 MySQL 锁等待诊断 SQL）。C0 的累计通过依赖此基线，不把它归功于 J1～B3 生产修复。C0 文档提交为上述 `89b77dc...`；C3 文档实际随 `01ac774...` 提交。

### 1.2 长期证据入口

- [J1 验收](../verification/JEV_REMOVAL_DEEPSEEK_ONLY_20261111.md)
- [B1 验收](../verification/B1_CONCURRENT_IDEMPOTENT_REPLAY_20261111.md)
- [B2 验收](../verification/B2_POST_CLAIM_FAILURE_RECOVERY_20261111.md)
- [B3 验收](../verification/B3_REPORT_FINAL_CONSISTENCY_20261111.md)
- [C0：J1～B3 收口](../verification/J1_B3_PHASE_CLOSEOUT_20261111.md)及[源码学习指南](../verification/J1_B3_SOURCE_LEARNING_GUIDE_20261111.md)
- [B4-A 审计](../verification/B4A_DEEPSEEK_RELIABILITY_AUDIT_20261112.md)
- [B4-B 验收](../verification/B4B_DEEPSEEK_LOG_SAFETY_20261112.md)
- [B4-C 验收](../verification/B4C_DEEPSEEK_TIMEOUT_RETRY_20261112.md)
- [C3：B4-A～B4-C 收口](../verification/C3_B4A_B4C_CLOSEOUT_20261112.md)
- [B4-D 验收](../verification/B4D_MILVUS_RETRY_DEADLINE_20261113.md)
- [B4-E 验收](../verification/B4E_REPORT_STALE_RECOVERY_20261113.md)
- [B5-0 准入审查](../verification/B5_0_SYSTEM_READINESS_REVIEW_20261113.md)及[真实人工/AI测试计划](../verification/B5_REAL_WORLD_TEST_PLAN_20261113.md)

2026-11-13 B4-D 更新：再次通过 GitHub 连接器核验远端 main 仍为 `89b77dc...`，本地 HEAD 仍为 `01ac774...`，领先 3 个提交。原 AGENTS/mvnw/Compose/MQ 文档保留，台账仍未跟踪；本批新增源码/测试/验收文档均未暂存、未提交、未推送。工作期间新增观察到两处 `.DS_Store`，未修改。详见 [B4-D 保护证据](../verification/B4D_MILVUS_RETRY_DEADLINE_20261113.md#1-基线与原有修改保护)。

- [B5-P2-A 密码 UTF-8 边界修复验收](../verification/B5_P2A_PASSWORD_UTF8_BOUNDARY_20261113.md)

- [B5-P2-B Embedding日志脱敏验收](../verification/B5_P2B_EMBEDDING_LOG_SAFETY_20261113.md)
- [C6：可靠性修复累计收口、证据复核与提交计划](../verification/C6_RELIABILITY_PHASE_CLOSEOUT_20261113.md)

## 2. 历史批次

<a id="j1"></a>

### J1 — 移除 Jev 运行时，保留 DeepSeek 单模型与业务恢复

- 日期：2026-11-11。状态：已实现、编译、离线／真实 MySQL 回归、打包；已本地提交并进入远端 main。
- 提交：[`b525683ce72def1f59432a33959f672c2c87b7a1`](https://github.com/kun520c/ai-interview-system/commit/b525683ce72def1f59432a33959f672c2c87b7a1)，实际 55 个文件。完整改动以 `git show --name-status b525683` 和 C0 的 J1 文件清单为准。
- 问题／根因：旧 selector 将 Jev 判分、DeepSeek 反馈、预算与跨模型 fallback 组合在同一路径；生产装配、有效配置及实验工具都有入口。仅删一个 Bean 不能完成退役，也可能误删独立的 FAILED/stale 业务恢复。
- 实际改动：[JudgeConfiguration](../../src/main/java/com/kun/aiinterview/interview/evaluation/judge/JudgeConfiguration.java) 的 `judgeProvider` 只装配 DeepSeek；删除 `JudgeProperties` 与 `judge/jev/` 下 8 个实现、10 个专属测试；修改生产／测试 YAML 移除 selector；迁移 `JudgeConfigurationTest`、`EvaluationOrchestrationServiceTest`、`FullInterviewBusinessOfflineTest`、baseline 测试及工具。新增 [check_deepseek_only.py](../../tools/check_deepseek_only.py)，旧网络入口立即拒绝执行；README／迁移文档标注退役。被删除文件通过该提交父版本查阅，不提供伪造的当前文件链接。
- 主要调用链：`InterviewService.submitAnswer → InterviewWorkflowService.evaluateAnswer → EvaluationOrchestrationService.evaluate → JudgeProvider/DeepSeekJudgeProvider → DeepSeekEvaluationClient → LlmEvaluationValidator → EvaluationScoreCalculator → EvaluationStandard → FollowUpPolicy → Workflow 收口短事务`。报告另走 `DeepSeekInterviewReportGenerator → completeJson → completeReportGeneration`。
- 关键保留方法：`evaluate/getByAnswerId` 先复用历史业务列；`handleExistingAnswer` 处理显式恢复；claim/retry/reclaim 与 `persistOwnedEvaluation` 保留执行身份和提交权。成功 RAG trace 随 fenced 收口事务提交，早期 AGENTS 的“模型前独立提交成功 trace”不是当前行为。
- 取舍：保留 provider-neutral 接口与业务状态机，删除供应商组合；历史 `jev-*` 元数据和 opaque `raw_result` 可读，不强制套 DeepSeek DTO，不迁移历史数据。保留一次应用级调用，不增加自动 retry、新模型或新依赖。
- 证据：[J1 实际验证、文件清单及兼容性](../verification/JEV_REMOVAL_DEEPSEEK_ONLY_20261111.md)，[C0 交叉复核](../verification/J1_B3_PHASE_CLOSEOUT_20261111.md)。学习：[L-J1-01](SOURCE_LEARNING_BACKLOG.md#l-j1-01)～04、[L-X-01](SOURCE_LEARNING_BACKLOG.md#l-x-01)～03。

测试类型与结果：

- Unit／Mock HTTP + 真实 MySQL 混合定向：最终 **367/0/0/0**；完整普通回归 **1526/0/0/19**，退出 0。其中离线业务类 **85/0/0/0**，数据库真实、模型替身；独立事务／并发类另行运行。
- 历史失败保留：改前全量 **1790/7/0/19** 是临时 MySQL UTC 与项目时区不一致，校正测试实例后 **1790/0/0/19**；首轮定向 **367/14/0/0** 为新 fixture 配置和 MyBatis 一级缓存问题，修正夹具后通过。
- 报告记载的工具验证：DeepSeek-only gate 最终 **37/0/0/0**，另编译 21 源文件和 9 项静态检查；公开离线 gate 最终 Java **28/0/0/0**、Python **9/0/0/0**、候选证据 Java **54/0/0/0**。公开离线首轮 Java **28/0/13/0** 为沙箱 attach／临时目录权限问题。工具名称含 E2E 不等于浏览器／真实模型 E2E。
- `-DskipTests package` 退出 0，测试不适用；JAR 检查确认保留 DeepSeek、移除 Jev。真实 DeepSeek／Embedding／Milvus、浏览器 UI E2E、Windows PowerShell：未执行。没有模型质量或性能改善结论。

<a id="b1"></a>

### B1 — 并发完成后的幂等重放

- 日期：2026-11-11。状态：已实现、编译、定向及真实 MySQL／全回归；已本地提交并进入远端 main。
- 提交：[`1df25ea6dfe6c818cf0c75a58c47f58c09ed524a`](https://github.com/kun520c/ai-interview-system/commit/1df25ea6dfe6c818cf0c75a58c47f58c09ed524a)，4 个文件。
- 问题／根因：A 已提交答案后暂停，B 完成同请求并推进 Session；A 再进入 Workflow 或持有旧 Answer 快照时，当前题检查先于完成重放，正常完成误报 HTTP 500。数据库单胜者 CAS 不能修正应用层读取顺序。
- 改动文件／方法：[InterviewWorkflowService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java) 的 `evaluateAnswer` 增加首次 EVALUATED 重放和当前题不匹配时 `requireReloadedAnswer` 重读；修改 [InterviewWorkflowServiceTest](../../src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowServiceTest.java)；新增 [InterviewConcurrentReplayIntegrationTest](../../src/test/java/com/kun/aiinterview/interview/service/InterviewConcurrentReplayIntegrationTest.java) 和 B1 验收报告。
- 调用链：Controller → `InterviewService.submitAnswer/handleExistingAnswer` 校验归属及完整 payload → 提交答案短事务 → Workflow 首读／重读已完成答案 → 内部重放信号 `null` → `buildSubmitAnswerResponse` 从持久评价和最新 Session 重建响应。
- 取舍：仅明确 EVALUATED 才重放；缺评价、错身份、未完成错题仍报错。沿用内部返回约定，不增加全局锁、长事务、循环重试或先 claim 再校验。`requestId` 唯一约束、attempt fencing 与 Session version 各自保留。
- 测试：正式改前真实 MySQL 复现 **16/10/0/0**，10 个受控交错为期望 200 实际 500；修复首轮定向 **50/0/0/0**，最终业务定向 **286/0/0/0**，完整普通回归 **1549/0/0/19**，成功轮退出 0。初版夹具同为 **16/10/0/0**，其中 5 项受 MyBatis 代理 `callRealMethod` 误用影响，不是完整业务复现证据。
- 真实 MySQL／MockMvc 专项 **18/0/0/0**：无外围测试事务，两暂停点 × 五条状态转移；CountDownLatch 固定顺序，联合断言数据库整行／数量／version、HTTP、外部替身调用次数。Unit/Mock 单独验证分支与参数。不是浏览器 E2E；真实模型及外部 Smoke 未执行。
- 限制：只证明指定交错，不穷尽所有竞态；stale 接管可再次调用外部模型，不承诺供应商 exactly-once。未单独 package。
- 证据：[B1 验收](../verification/B1_CONCURRENT_IDEMPOTENT_REPLAY_20261111.md)、[C0](../verification/J1_B3_PHASE_CLOSEOUT_20261111.md)。学习：[L-B1-01](SOURCE_LEARNING_BACKLOG.md#l-b1-01)～04。

<a id="b2"></a>

### B2 — 成功领取后的查询失败及时收尾

- 日期：2026-11-11。状态：已实现、编译、定向及真实 MySQL／全回归；已本地提交并进入远端 main。
- 提交：[`7d259818c7650b4c57e3d642d3821cb17291e4a0`](https://github.com/kun520c/ai-interview-system/commit/7d259818c7650b4c57e3d642d3821cb17291e4a0)，4 个文件。
- 问题／根因：claim 短事务已提交，但 `findNextPendingMainQuestion` 在 try 外；查询异常不会回滚既有领取，答案留在 fresh EVALUATING，显式请求也只能等待 stale。
- 改动文件／方法：[InterviewWorkflowService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java) 仅将下一题查询及布尔计算移入 `evaluateAnswer` 的 OWNED try；扩展 Workflow 单测；新增 [InterviewPostClaimFailureIntegrationTest](../../src/test/java/com/kun/aiinterview/interview/service/InterviewPostClaimFailureIntegrationTest.java) 与 B2 报告。事务服务、Mapper／SQL 未修改；B1 重放分支保留。
- 调用链：`claimOrRecoverEvaluation → OWNED`（REPLAY 提前返回）→ try 内查询／评价／推进 → catch → Workflow `markEvaluationFailed` → 事务服务同名方法 → Mapper `markFailed(id, EVALUATING, attemptId)` → 原异常重抛。
- 取舍：保护边界始于确认拥有资格后，不能包住领取失败、未授权请求或重放。失败更新 0 行是旧 attempt 被拒绝；次要回写异常以 suppressed 保留，原查询异常／cause 不被覆盖。无新增事务传播策略或后台恢复。
- 测试：首轮新测试 testCompile 失败（AssertJ API 误用），执行测试 0、JUnit F/E/S 不适用；正式改前 **44/7/0/0**；修复后 **44/0/0/0**（Unit/Mock 41 + 真实 MySQL 3）；业务 **296/0/0/0**；普通全量 **1559/0/0/19**，后三轮退出 0。
- 真实 MySQL 专项 **3/0/0/0**：MAIN／FOLLOW_UP 的领取及 FAILED 由独立连接观察；同 payload 再请求成功；旧查询失败不覆盖新 attempt。故障查询入口和 Orchestration 为替身，旧查询接管按固定顺序注入，不冒充新双线程网络故障实验。浏览器 E2E／真实模型未执行，未单独 package。
- 限制：数据库长期不可用、回写失败、进程崩溃和 claim 提交结果不确定仍可能留下 EVALUATING，依赖原请求驱动 stale；未验证真实断网后的最终恢复。
- 证据：[B2 验收](../verification/B2_POST_CLAIM_FAILURE_RECOVERY_20261111.md)、[C0](../verification/J1_B3_PHASE_CLOSEOUT_20261111.md)。学习：[L-B2-01](SOURCE_LEARNING_BACKLOG.md#l-b2-01)～03。

<a id="b3"></a>

### B3 — 报告与 Weakness 只使用合法 FINAL

- 日期：2026-11-11。状态：已实现、编译、真实 SQL／事务及全回归；已本地提交并进入远端 main。
- 提交：[`6de9e7aecb69a5269d1c8fa466f3d2dc57865ee4`](https://github.com/kun520c/ai-interview-system/commit/6de9e7aecb69a5269d1c8fa466f3d2dc57865ee4)，11 个文件。
- 问题／根因：旧 SQL 以 FINAL 优先、INITIAL 兜底，`ROW_NUMBER` 隐藏重复 MAIN；Java 只校验数量，无法证明完整计划及答案归属。已有非 READY 报告恢复还绕过来源检查。
- 生产改动：[AnswerEvaluationMapper.xml](../../src/main/resources/mapper/AnswerEvaluationMapper.xml) 的 `listFinalEffectiveEvaluationsBySessionId` 仅取 FINAL、保留重复并按 MAIN 计划排序；[Mapper 接口](../../src/main/java/com/kun/aiinterview/interview/mapper/AnswerEvaluationMapper.java) 更新契约；[InterviewReportService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java) 增加 `validateEvaluations/validateEvaluationAssociations/requireEvaluatedAnswer`，`existingReportOrContinue` 复用校验；[UserWeaknessService](../../src/main/java/com/kun/aiinterview/user/service/UserWeaknessService.java) 的 `validateEvaluation` 在写入前拒绝非 FINAL。
- 测试／文档：修改 AnswerEvaluationMapperTest、InterviewReportServiceTest、InterviewReportApiServiceTest、InterviewReportTransactionServiceIntegrationTest、UserWeaknessServiceTest；新增 [InterviewReportFinalConsistencyIntegrationTest](../../src/test/java/com/kun/aiinterview/interview/service/InterviewReportFinalConsistencyIntegrationTest.java) 和 B3 报告。完整路径见提交及报告第 5 节。
- 调用链：ReportService 校验所有者／COMPLETED → READY 历史直接返回或 CAS 领取 → FINAL 候选 → MAIN 唯一集合、计划、答案与追问关联校验 → Java `calculateOverallScore` → 报告生成器 → `completeReportGeneration` 短事务内 Report + `applySessionEvaluations` + READY。
- 取舍：用现有查询和 Java 业务校验解决，不改冻结 Schema；INITIAL 只用于证明追问来源，不入评分。BigDecimal／HALF_UP、父 MAIN 知识点聚合、同场一次弱项计数与原子提交保留。READY 历史可读，不静默纠正历史分数；非 READY 恢复必须校验。增加问题／答案批量查询及每个追问一次 INITIAL 查询，未测性能收益。
- 测试：正式改前 **55/14/0/0**；修复定向 **93/0/0/0**；后发现恢复旁路复现 **2/2/0/0**；补齐后扩展 **134/0/0/0**、业务 **320/0/0/0**；全量 **1589/0/0/19**，成功轮退出 0。早期三轮各 **55/2/16/0** 为必填字段／CHECK 夹具错误，随后 **55/16/0/0** 中 2 项是 JDBC 数值类型断言问题，均不算生产缺陷。
- 真实 MySQL 专项最终 **24/0/0/0**，无外围事务，仅报告文本生成器为替身：合法 MAIN／追问、17 类异常、FAILED 重试、READY 历史、非 READY 恢复和实际 INSERT 失败；另有真实 Mapper、READY CAS／Weakness SQL 回滚测试。Mock 仅证明服务分支，真实模型／浏览器 E2E 未执行。J1 工具 gate **37/0/0/0**（报告记录）；未单独 package。
- 限制：未扫描真实历史业务库，未修复已有 READY 的错误分数；未验证绕过应用的并发直接改库。失败回写仍可能受数据库可用性影响。
- 证据：[B3 验收](../verification/B3_REPORT_FINAL_CONSISTENCY_20261111.md)、[C0 历史 Jev 交叉补验](../verification/J1_B3_PHASE_CLOSEOUT_20261111.md)。学习：[L-B3-01](SOURCE_LEARNING_BACKLOG.md#l-b3-01)～04。

<a id="b4-a"></a>

### B4-A — DeepSeek 可靠性审计与后续设计

- 日期：2026-11-12。状态：审计／设计与既有离线测试复核完成；本批没有生产修复。文档已本地提交，未进入本次核验的远端 main。
- 提交：`004e434d3c1352c15d022bc1bf08abdfb02f4a02`；唯一新增文件为 [B4-A 审计报告](../verification/B4A_DEEPSEEK_RELIABILITY_AUDIT_20261112.md)。`git show --stat 004e434` 可核对。
- 实际解决的工程问题：将“调用失败／慢／难恢复”拆成有源码证据的独立风险和可验证方案，没有把审计本身写成修复。发现日志边界、诊断分类、Milvus 隐式重试与整链预算、GENERATING 页面恢复缺口、并发容量和调用观测缺口；usage 成本属于可选增强。
- 根因／源码入口：Client `requestCompletion` 保留危险上游 cause，GlobalExceptionHandler 打印完整栈；DeepSeekConfiguration 仅约束单次 HTTP；Milvus `search → RpcUtils.retry` 有 SDK 默认策略；`useInterviewReport.ts` 的 GENERATING 只 GET，不发恢复 POST。报告第 2 节 E01～E24 和依赖 D01/D02 给出对应文件／方法。
- 主要调用链：评分为 claim → RAG（Embedding → Milvus → MySQL）→ 单次评分／反馈／追问建议 → Java 校验与短事务；报告为独立 POST → FINAL 汇总 → 单次 completeJson → Report／Weakness／READY。用户提交追问答案才再评分，创建追问本身不再调用模型。
- 取舍：先安全边界和分类，再验证 search 预算／取消；不直接叠加自动 retry。候选为 JVM 非阻塞有界许可和安全日志事件，暂不引 Redis、MQ、线程池／熔断框架。B4-A 旧路线 B4-C1/C2、D1/D2 不能直接视为全部已实施。
- 测试：既有 12 类离线 Unit／Mock HTTP／MockMvc 首轮 **201/0/85/0**（Mockito 无法自挂载，退出 1）；受影响 3 类以临时 javaagent 重跑 **85/0/0/0**，退出 0。201 个不同用例分两轮最终通过，不存在一轮“201/0/0/0”的证据。
- 未执行：真实 MySQL、完整 mvn test、socket 故障、SDK 运行时注入、负载、真实模型、前端／浏览器 E2E。SDK 75 次是该版本代码／字节码确认的最多 callable 次数；不是所有超时都会重试，也不是本批实测延迟。
- 后续结果：日志通道由 B4-B 修复，分类由 B4-C 补强；整链预算、容量、前端恢复、事件观测仍待独立任务。学习：[L-B4A-01](SOURCE_LEARNING_BACKLOG.md#l-b4a-01)～04。

<a id="b4-b"></a>

### B4-B — DeepSeek 异常链和日志安全边界

- 日期：2026-11-12。状态：已实现、编译、Mock／真实 MySQL 全回归与打包；已本地提交，未进入本次核验的远端 main。
- 提交：`f764c087f75d6b515b09bf2a0aef3f1d9a20c906`，6 个文件；共享 Client／业务测试在此提交只含 B4-B 增量。
- 问题／根因：Spring 默认错误 handler 将 body 写入异常 message/cause，Jackson 错误也可带片段；任意 finish_reason 可被拼入日志。API 固定 502 不影响日志遍历完整 Throwable，原 HTTP 响应脱敏不能证明日志安全。
- 改动：[DeepSeekEvaluationClient](../../src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java) 的 `requestCompletion` 请求级 `onStatus` 提前拒绝非 2xx，网络／解析异常断开危险 cause，`safeFinishReason` 白名单；[DeepSeekInterviewReportGenerator](../../src/main/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGenerator.java) 的 `parseResponse` 不挂 Jackson 原异常。修改 DeepSeekEvaluationClientTest、FullInterviewBusinessOfflineTest；新增 [DeepSeekLogSafetyTest](../../src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekLogSafetyTest.java) 与 B4-B 报告。
- 调用链：评分 evaluate／报告 completeJson → requestCompletion → onStatus 或解析边界产生安全 ExternalServiceException → 原 Workflow／Report FAILED 收尾 → GlobalExceptionHandler 单条 ERROR＋安全本地栈＋固定 502。
- 取舍：不读取错误 body/statusText/诊断 headers，不用正则删几个 key，不关闭全局栈；只保留固定 stage/category/数值状态，保留本地定位能力。未修改 GlobalExceptionHandler、ExternalServiceException、业务状态机或配置；编程异常仍原样传播。
- 测试：改前 **6/0/0/0** 是“应能观察到泄漏”的复现断言，通过不是安全验收；最终专项 Unit／Mock HTTP／日志渲染 **36/0/0/0**；聚焦 **281/0/0/0**；全量 **1635/0/0/19**，成功轮退出 0；package 退出 0、无测试。
- 失败过程：**79/0/36/0**（夹具漏超时属性）、**79/1/0/0**（将 close 获取流误当 read）、**281/32/0/0**（误匹配安全类名 AuthorizationFilter）；调整夹具／精确断言后通过，没有放松正文和完整栈检查。
- 真实 MySQL 离线业务类 **95/0/0/0**，其中答案 15 类／报告 17 类故障；Mock HTTP，认证／业务／MyBatis 真实。独立事务并发回归另验证提交权。异常对象、stack trace、Logback proxy、PatternLayout 与 HTTP 响应均检查；不仅检查顶层 message。
- 限制：没有真实模型、socket 延迟、浏览器 E2E／APM／wire-level 审计；数据库收尾 suppressed 和其他模块日志不在全局安全保证内。C3 另澄清通用 sentinel 三次命中，见第 3 节。
- 证据：[B4-B](../verification/B4B_DEEPSEEK_LOG_SAFETY_20261112.md)、[C3](../verification/C3_B4A_B4C_CLOSEOUT_20261112.md)。学习：[L-B4B-01](SOURCE_LEARNING_BACKLOG.md#l-b4b-01)～03。

<a id="b4-c"></a>

### B4-C — 固定错误分类、嵌套超时识别与单次尝试验证

- 日期：2026-11-12。状态：已实现分类修复、编译、Mock／Loopback HTTP／真实 MySQL 回归及打包；已本地提交，未进入本次核验的远端 main。未实施统一 deadline、SDK 重试治理或自动重试。
- 提交：`01ac774caff491b978dd2268e8b427355a64b97a`，6 个文件（含 C3 文档）。
- 问题／根因：B4-B 的 HTTP_STATUS 缺固定 reason；逐节点立即分类使外层 JSON 遮蔽 SocketTimeoutException、普通 timeout 遮蔽更具体连接超时。未证明自动 retry 是必要修复。
- 改动：[DeepSeekEvaluationClient](../../src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java) 的 `safeHttpFailureReason` 增加固定 reason，`safeFailureCategory` 有界遍历最多 10 节点，传输异常优先；扩展 FullInterviewBusinessOfflineTest；新增 [DeepSeekFailurePolicyTest](../../src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekFailurePolicyTest.java)、[DeepSeekHttpTimeoutTest](../../src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekHttpTimeoutTest.java)、B4-C 与 C3 报告。Properties／Configuration／Milvus／业务事务未改。
- 调用链：原评分／报告共享 Client → HTTP reason 或 cause 类型分类 → 安全异常 → 原 FAILED 收尾 → 同 payload 显式新请求 → 成功后持久结果重放；应用模型调用计数为 1 → 2 → 2。
- 取舍：分类不授予重试权限，保留一次应用级请求；Retry-After 不解析、不等待、不转发；没有具体超时类型的 IOException 保守记 IO_ERROR，不根据 message 或耗时猜测。3s/60s 参数、120s 前端和 stale 阈值均未修改。JDK 连接恢复与 Milvus SDK 原有隐式重试不等于应用 retry，也不能承诺网络 exactly-once。
- 测试：改前分类 **42/25/0/0**，25 个断言失败不等于 25 个独立 bug；首轮 loopback **4/0/4/0** 为沙箱禁止 bind，未执行到 HTTP。允许的执行环境下客户端聚焦 **136/0/0/0**，业务 **336/0/0/0**，完整 **1698/0/0/19**，退出均 0；package 退出 0、无测试。
- Unit／Mock：FailurePolicy **42/0/0/0**，保留 LogSafety **36/0/0/0**；8 类 Retry-After 验证无等待／无自动重发。真实 MySQL＋Mock HTTP 离线业务类 **112/0/0/0**（答案 24 类、报告 25 类故障），独立事务并发类继续通过。
- 本地真实 HTTP：TimeoutTest **4/0/0/0**；生产 factory、600ms 测试 readTimeout 下，B4-C 全量 headers/body/partial-json/trickle 分别 **604/607/604/605ms**、每例请求 1 次。仅为本机合成服务读等待验证；headers 分类 TIMEOUT，其他 IO_ERROR。不是公网性能或真实供应商取消／计费证据。
- 限制：未验证 WAN/TLS/代理/HTTP2、真实连接超时秒数、Milvus 75 次运行时故障、整链硬上限、容量和真实模型／浏览器 E2E；第 10/11 层及环形 cause 未有专项运行覆盖。前端恢复缺口仍在。
- 证据：[B4-C](../verification/B4C_DEEPSEEK_TIMEOUT_RETRY_20261112.md)、[C3](../verification/C3_B4A_B4C_CLOSEOUT_20261112.md)。学习：[L-B4C-01](SOURCE_LEARNING_BACKLOG.md#l-b4c-01)～03。

<a id="b4-d"></a>

### B4-D — Milvus SDK 2.6.20 重试与超时治理

- 建立/更新日期与执行者：2026-11-13，Codex；范围由本轮用户明确指定，替代旧候选编号含义。
- 状态：**已编码、已编译、已聚焦/完整回归验证、已打包**；真实 MySQL 已验证；真实 Milvus/Embedding/DeepSeek 未执行。无本批提交 SHA，所有修改仍在工作区，暂存区为空。
- 原问题与级别：4 项 P2，均有源码/测试证据。2.6.20 RpcUtils 吞掉预算/次数耗尽/中断异常；hasCollection null 被当不存在，void create 可能静默成功；构造器 ListDatabases 没有 deadline；正数亚毫秒 Duration 转成 0 关闭 deadline。没有生产事故或数据损坏已发生的证据。
- SDK 真实行为：默认 75 是总 callable 尝试数，退避 10ms×3、封顶3s，默认总预算0。UNAVAILABLE/RateLimit 可重试；DEADLINE_EXCEEDED 等7类直接失败，但 CANCELLED 在2.6.20可重试。重试耗尽返回null；1ms预算在实测31/32ms后仍成功；退避吞中断。实际 gRPC 3次成功628ms超过每次500ms，绝对deadline逐次刷新。不可混用V1配置。
- 依据：[独立验收报告](../verification/B4D_MILVUS_RETRY_DEADLINE_20261113.md)，准确 Maven 2.6.20/2.6.21 源码与当前 JAR/字节码、`/private/tmp/ai-b4d-20261113/` 的逐轮 XML/日志和摘要。2.6.21 diff 确实修复本次循环缺陷；未升级依赖。
- 生产修改：[MilvusConfiguration](../../src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusConfiguration.java).milvusClient 在发布单例前配置 maxRetryTimes=1，并用gRPC Context给构造期Connect/ListDatabases共享connectTimeout；finally detach/cancel/shutdown。[MilvusProperties](../../src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusProperties.java) 拒绝小于1ms和毫秒溢出；[MilvusCollectionInitializer](../../src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusCollectionInitializer.java).run 拒绝null，不创建未知Collection。
- 修改前链：Spring Bean → Connect(5s) → 无deadline的ListDatabases → 默认75次外层重试 → 每次10s stub → 成功/异常/null。修改后：初始化Context(connectTimeout) → 固定一次callable → 同次内部RPC共用requestTimeout stub → adapter受控失败/合法结果；无外层SDK自动重试退避。初始化后detach，不把初始化deadline留给后续操作。
- 业务与事务保持：KnowledgeRetrievalService→search→MySQL资格检查；KnowledgeDocumentProcessingService在insert前记录可能已写入→只补偿当前attempt-index IDs→FAILED CAS。已有MySQL提交确认、旧attempt隔离、新attempt显式恢复、Session CAS、RAG trace与J1～B4-C保持。相关Service/Mapper生产代码未改。
- 测试文件：新增 [MilvusSdkRetryBehaviorTest](../../src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusSdkRetryBehaviorTest.java) 15项及 [MilvusGrpcDeadlineTest](../../src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusGrpcDeadlineTest.java) 12项；扩展MilvusConfigurationTest、MilvusPropertiesTest、MilvusCollectionInitializerTest、[KnowledgeAttemptFencingIntegrationTest](../../src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeAttemptFencingIntegrationTest.java) 3项、[KnowledgeRetrievalServiceTest](../../src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalServiceTest.java) 2项。完整路径/命令见报告。
- 方案取舍：官方单次分支绕过真实缺陷，优于只把次数降为3或设置被吞的总预算；代价是暂态故障不再自动恢复。未复制retry实现、未用Future假取消、未加业务线程池或新依赖。SDK升级单独待审批；本批无需升级即可修复应用路径。
- 故障证据：原JAR 15/0/0/0；改前契约45/3/0/0；改前gRPC 10/8/0/0（7项前置策略断言，1项无deadline）。两次夹具testCompile失败、测试0；一次扩展180/3/0/0为合成向量维度不匹配，修正夹具后通过，未降低校验。
- 最终测试：Milvus+真实MySQL补偿聚焦 **180/0/0/0**；RAG/知识 **450/0/0/10**；完整普通回归 **1732/0/0/19**（1713非跳过）；各退出0。package与git diff --check退出0，package不执行测试。full关键B4-B/C安全/分类与B1/B2/B3真实一致性测试均通过。各轮不可相加；普通真实MySQL不是opt-in跳过项。
- 类型边界：Unit/Mock SDK、真实JAR callable、Loopback gRPC transport、真实隔离MySQL分别计证据；MySQL回归的Milvus/Embedding为替身。构造800ms实测803ms、insert300ms实测307ms；取消可达服务端Context，但不能撤销迟到写入。完整回归总时长28:17含无Milvus的既有测试异常长wall time，未定位原因、不作性能归因。
- Git：基线完整HEAD `01ac774caff491b978dd2268e8b427355a64b97a`；2026-11-13实时远端main `89b77dc082272e38cfbbecb4b57c37edae9ee74d`。B4-A～C三次提交未进入该远端main，本批未提交/推送。原有AGENTS与mvnw mode、Compose及Git index摘要一致；未跟踪MQ文档被外部操作更新，本轮未编辑或回退其当前内容；本轮只按授权更新两份原未提交台账。
- 遗留风险：没有整链严格时间上界/服务器exactly-once保证；内部SchemaMismatch可重发insert、透明传输行为未承诺禁用；迟到孤儿需受控维护，MySQL只保证不成为有效证据。独立OrphanVectorMaintenance CLI直接new客户端，未继承Spring策略，保留为后续P2；其iterator需要单独核验。未做真实Milvus、真实模型、WAN/代理/负载验证；不声称所有SDK入口已修复。
- 关联学习：[L-B4D-01](SOURCE_LEARNING_BACKLOG.md#l-b4d-01)～07，全部待学习；没有开发者本人掌握证据。下一步仅审阅diff；CLI治理/SDK升级/真实服务复核独立确定范围，不自动进入B4-E等后续。

<a id="b4-e"></a>

### B4-E — 面试报告 GENERATING 人工恢复入口

- 日期：2026-11-13。用户本轮明确范围，原候选占位转为已实施批次；状态：前端已编码、typecheck/ESLint/build通过、专项与全量单元验证通过、后端定向回归通过；未暂存、提交、推送，无 B4-E SHA。
- 问题/根因：GENERATING 只有 GET 轮询，而 `generate()` 只允许 NOT_STARTED/FAILED。后端 stale 接管只能从 POST 进入，页面无法主动到达该路径。前端停留时间既不触发后端接管，也不能判定数据库任务已过期。
- 修复前证据：新增 long-running 测试在原生产代码上推进 fake timers 11 分钟，221次 GET、0次 POST、仍为 GENERATING，失败于缺少恢复按钮。33/1/0/0，原32项通过；这是 jsdom/API Mock，不是真实崩溃实验。完整记录：[B4-E验收](../verification/B4E_REPORT_STALE_RECOVERY_20261113.md)，临时原始证据 `/private/tmp/ai-b4e-20261113/`。
- 生产修改：[useInterviewReport](../../frontend/src/features/interview/composables/useInterviewReport.ts) 的 `recoverGenerating/submitReport/handleGenerateFailure/realignFromDetail/refreshReport`：受控GENERATING入口、共享posting保护、明确包含GENERATING的提交来源类型、409/不确定结果确认GET；恢复服务不可用也查真实状态。[InterviewReportView](../../frontend/src/features/interview/views/InterviewReportView.vue) 新增按钮与disabled/loading；[reportCopy](../../frontend/src/features/interview/copy/reportCopy.ts) 明确服务器判断过期及不保证立即恢复。
- 测试/文档修改：[InterviewReportView.spec.ts](../../frontend/tests/InterviewReportView.spec.ts) 新增24项，保留原32项；本台账、源码学习清单与本批验收报告。API封装、页面状态枚举及全部后端生产/测试/SQL/配置均未修改。
- 原调用链：页面bootstrap→Detail→fetchInterviewReport→GET Controller→Service.getReport→只读状态/报告；GENERATING→定时refreshReport→同一GET。新人工链：按钮→recoverGenerating→submitReport→generateInterviewReport→POST Controller→generateReportForApi/generateReport→数据库updated_at判stale→reclaimStaleGenerating version CAS→已有报告恢复或FINAL校验/Java均分/生成器→Report+Weakness+READY短事务。
- 状态与异步边界：GENERATING人工提交→GENERATING_POST；409→GET真实GENERATING+提示/READY；超时或5xx→先GET，无法确认→RECOVERY_UNKNOWN。提交递增epoch使旧GET失效、暂停轮询；确认GENERATING后继续3秒GET。posting覆盖POST及确认GET，route/sessionId/unmount守卫仍有效。无自动POST、页面计时判stale或伪造终态。
- 取舍：复用既有函数、页面状态与HTTP合同；提交期间暂停轮询，避免旧GET覆盖READY。后端仍以数据库updated_at和version决定资格，无新任务系统、后台调度或依赖。服务不可用时可能已经取得claim并回写FAILED，因此GENERATING路径不能简单套用原NOT_STARTED/FAILED回退规则。
- 实际测试（2026-11-13，HEAD加B4-D/B4-E工作区）：报告专项 **56/0/0/0**，前端全量 **317/0/0/0**、36文件；typecheck/ESLint/build退出0。后端既有报告/API/事务/FINAL/CAS定向 **123/0/0/0**、10类，退出0；其中Unit/配置/Mockito43、MockMvc30、真实MySQL50。命令与逐类计数见验收第5节。不同轮次不能累加成独立测试量。
- 中间运行：初次修复专项54/0/0/0；追加401后56/2/0/0，原因为测试未等待懒加载登录路由，增加有界waitFor后56/0/0/0。未放松生产认证或断言。前端编译/构建不计作测试；CLI中一个不存在的ReportMapperTest选择器未计为通过，真实Report写入/回滚由事务测试覆盖。
- 环境/类型：Vue Test Utils+fake timers+可控Promise；401使用真实Axios拦截器、合成adapter与内存路由。后端使用本轮mysql:8.0.45 ARM64 tmpfs独立ai_interview_test，模型为替身；RUN_REAL_*与MILVUS_ENABLED保持unset，test profile禁用外部服务。测试后已核验并清理本轮容器/口令，未操作既有库/卷。
- 未执行：真实浏览器E2E、浏览器到真实Spring端口完整链、进程崩溃/真实丢响应、真实DeepSeek/Embedding/Milvus、生产负载。因没有后端变更且定向合同覆盖充分，本轮未重跑全部Maven或后端package；不引用B4-D历史全量作为本轮通过证据。git diff --check和暂存区检查通过。
- Git：本地HEAD `01ac774caff491b978dd2268e8b427355a64b97a`；GitHub连接器本日再次核实远端main `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，本地领先3，B4-A～C仍未进入该远端main。B4-D所有源码/测试/验收及其他已有用户文件摘要不变，只有两份台账按授权更新；index不变，暂存区为空。B4-E四个前端文件、两份已有未跟踪台账和新验收报告均未提交/推送。
- 兼容性/遗留：CAS约束数据库提交权，不能取消旧供应商调用或保证只计费一次；单页面防并发不替代多标签页的后端CAS。按钮不承诺恢复成功；真实E2E仍待独立验证。未发现需扩展为后端生产修改的缺陷，也没有性能改善结论。
- 关联学习：[L-B4E-01](SOURCE_LEARNING_BACKLOG.md#l-b4e-01)～07，全部待学习；本人讲解/复现/修改证据未提供。源码入口、核心问题、练习见清单，约13分钟路径见验收第8节。本轮完成后停止，不自动开发后续候选或执行Git写操作。

<a id="b5"></a>
<a id="b5-0"></a>

### B5-0 — 全系统缺陷收口与真实测试准入审查

- 建立/更新日期、执行者：2026-11-13，Codex。用户明确只读审查/累计回归/建立真实测试方案；本批替代原B5范围占位，未授权业务修复或真实模型消耗。
- 关联证据：[准入审查](../verification/B5_0_SYSTEM_READINESS_REVIEW_20261113.md)、[执行计划](../verification/B5_REAL_WORLD_TEST_PLAN_20261113.md)；临时原始证据 `/private/var/folders/xx/vxww8vxd1hz_f8szvp9th6j40000gn/T/b5-0-20261113-_puq9a_q/`。历史J1～B4-E原报告保持不变。
- 状态：审查/计划完成，当前累计树已完整回归、前后端构建/打包；真实MySQL已验证。真实DeepSeek/Embedding/Milvus、完整浏览器面试、人工评分质量和费用验收均未执行。**CONDITIONAL_GO**，仅准入隔离环境、单人串行、合成数据的小规模测试；不得写成真实第三方已验收或可以公开部署。
- 实际发现：未确认P0/P1。B5-P2-01以真实HTTP+MySQL复现25个“界”（75 UTF-8字节）注册/改密返回500，根因为前后端按字符计数、BCrypt按字节限额；失败注册无行、失败改密哈希不变。B5-P2-02以Mock HTTP+实际Spring AI/Logback复现Embedding错误正文进入cause和服务端日志，公开Result仍脱敏502。仅用合成标记，没有真实秘密泄漏证据。
- 保留限制：P2-03原浏览器requestId记录丢失后的失败答案恢复入口缺失；P2-04整链无严格时间上界、stale不取消旧调用、迟到孤儿/独立维护CLI；P2-05当前版本真实依赖/浏览器证据缺口且本机application-local.yaml缺失；P2-06usage/费用、语义随机性和并发容量未验证。分级、影响和证据层次详见报告第5节。
- 文件改动：只新增上述两份verification文档、更新本台账与SOURCE_LEARNING_BACKLOG；未修改生产/测试源码、API、SQL、配置、依赖和AGENTS。临时ReadinessProbe.java/run_checks.py等位于证据目录，不加入工程测试数。
- 已审查调用链：authFormRules→AuthService/BCrypt→JWT数据库身份；create/resume→固定快照→用户行锁；SubmitRecord→原requestId→Workflow claim/retry/reclaim→RAG→DeepSeek→Java validate/sum/policy→attempt fencing短事务；Report GET只读/POST version CAS→FINAL关联校验→Report+Weakness+READY事务。B1/B2/B3/B4-D/E机制保留，没有重写CAS、幂等或状态机。
- 取舍：不为审查越界修复。独立建议B5-FIX-A统一密码UTF-8边界和前后端文案，B5-FIX-B治理Embedding不可信异常/日志；均未实施、无SHA。用合成数据、小规模串行和保留原浏览器记录约束后续测试，真实依赖预检及预算未满足则场景BLOCKED。
- 本轮完整Maven：`sh mvnw -B -ntp test`，**1732/0/0/19**、136套件、退出0；真实隔离mysql:8.0.45 ARM64、tmpfs、ai_interview_test。包括真实MySQL/MyBatis/事务/CAS、Unit/Mockito/Mock HTTP/MockMvc及SDK/loopback；模型与真实Milvus未调用。19跳过逐类见报告，所有双门禁与EnvironmentIsolationInitializer保持。40.514s为本机观测，无性能提升结论。
- 本轮前端：`npm run test:unit` **317/0/0/0**、36文件、退出0；typecheck/lint/build各退出0。`sh mvnw -B -ntp -DskipTests package`退出0，测试数不适用；JAR无local/test配置和Jev类。前端jsdom不算浏览器E2E。
- 工具：`python3 -B tools/check_deepseek_only.py` **37/0/0/0**、21源编译/9静态检查；3个退役入口预期退出1，其余成功；`python3 -B tools/run_public_offline.py` Java **28/0/0/0**、Python **9/0/0/0**、候选gate Java **54/0/0/0**，两个总入口退出0。不同轮次重叠，不合计为独立测试总量；Windows执行/历史付费重放未做。
- 临时探针：首次javac因HttpRequest通配符导入歧义退出1（2编译错误、执行测试0），只修临时脚本后编译/运行退出0；7项观察、0探针断言失败/错误/跳过，其中6项真实端口+MySQL、1项Embedding Mock HTTP+日志。Node3组密码检查退出0。成功复现缺陷不是产品修复通过；不计入JUnit/Vitest数量。
- 安全/保护：978个跟踪文件的私钥/Provider/GitHub-token模式扫描0疑似，敏感文件名0命中；有漏检边界。git diff --check/cached及文档链接检查退出0。入场24文件中除两份授权台账外22个字节/mode保留，暂存区空、HEAD不变；专用容器核验后移除，临时随机凭据删除，不触碰既有卷/库。
- Git分别核实：本地HEAD `01ac774caff491b978dd2268e8b427355a64b97a`；2026-11-13 GitHub连接器实时远端main `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，祖先计数0/3。J1～B3/C0已在远端，B4-A～C仅本地，B4-D/E/B5-0未暂存/提交/推送、无本批SHA；没有把历史报告结束语当当前发布状态。
- 计划产出：H01～H14逐项包含前置/操作/预期/DB/记录位置；AI分7类，独立人工评分点基准、稳定性重复、实际输出/配置/耗时/费用/异常模板。新质量重复必须新业务请求，同requestId重放只证明幂等；未知usage/费用填UNKNOWN。真实执行和付费额度尚待后续明确任务。
- 学习关联：[L-B5-01](SOURCE_LEARNING_BACKLOG.md#l-b5-01)～06，原1条占位拆为6条，清单共54条。所有状态待学习/掌握未确认；无开发者本人讲解、复现或独立修改证据。下一步仅按准入条件审阅计划/选择独立修复或真实测试任务，本轮到此停止。

<a id="b5-p2-a"></a>

### B5-P2-A — 密码 UTF-8 字节边界修复

- 建立／更新：2026-11-13，Codex；来源为B5-0的B5-P2-01，用户本轮明确授权。原候选名B5-FIX-A归入本批，原报告保留。关联[验收报告](../verification/B5_P2A_PASSWORD_UTF8_BOUNDARY_20261113.md)。
- 状态：已核实源码、改前复现、编码、编译、Unit/Mock与真实隔离MySQL验证、全量回归和打包；未暂存／提交／推送。学习未验收。
- 根因：@Size限制UTF-16单位，不是UTF-8字节；实际crypto6.5.11的encode在超过72字节时抛IllegalArgumentException，通用异常映射500。matches走历史兼容路径，错误超限输入为false，匹配前72字节可为true，不能声称所有登录也500。
- 生产文件／方法：RegisterRequest、LoginRequest、ChangePasswordRequest复用@Utf8ByteSize；新增[PasswordPolicy](../../src/main/java/com/kun/aiinterview/common/validation/PasswordPolicy.java).validate/validateCurrent；AuthService.register/login和UserService.changePassword在数据库/编码器前执行防护。原Utf8ByteSize/Validator、PasswordConfig、Controller、异常处理器、Mapper和JWT未改。
- 前端：authFormRules用TextEncoder计字节并明确提示；LoginView/RegisterView/ProfileView去掉密码maxlength，useProfile移除字符上限导出。拒绝不裁剪，传参保留原字符串。新增PasswordSample、PasswordUtf8BoundaryTest、PasswordUtf8BoundaryIntegrationTest；修改两项原Controller文案断言和四个前端测试文件。全部绝对路径见报告第4节。
- 调用链：HTTP @Valid → DTO字节约束 → 服务密码专用防护 → 原BCrypt/Mapper。合法改密仍@Transactional → SELECT FOR UPDATE → 验当前密码 → 新旧不同 → encode → 单条密码/时间UPDATE → affectedRows；后续请求仍核对credential_version及iat。没有Schema、依赖、算法、事务/锁或Token规则变更。
- 取舍：保留注册/登录/新密码至少8个原String.length单位；当前密码不新加8位下限。服务显式防护可覆盖手动new绕过方法校验代理；没有截断fallback、预散列或泛化IllegalArgumentException→400。
- 环境／证据：新建本批唯一MySQL8.0.45 tmpfs容器，仅ai_interview_test，首轮127.0.0.1:63375、最终补验重建63999；真实外部门禁均关闭。原始目录`/private/var/folders/xx/vxww8vxd1hz_f8szvp9th6j40000gn/T/b5-p2a-20261113-p3rjz9s7/`，含执行器、按轮XML/日志、summary、源码保全与cleanup记录。完整命令见报告第5节。
- 运行：改前首次16/5/0/0、独立断言覆盖四入口后16/5/0/0，均退出1；四组编码超限各注册/改密500，第五项为匹配前缀登录200，均是预期红测。修后聚焦127/0/0/0，退出0；初轮全量1775/0/0/19；随后核对Hibernate Validator8.0.3源码为Java trim判空，统一服务/前端并补充Unicode/控制空白用例，最终聚焦49/0/0/0、全量1780/0/0/19（138类），均退出0。新增Unit/Mock29项、真实MySQL+MockMvc19项均通过，无外围集成测试事务；真实并发改密1项、原JWT/鉴权回归通过。
- 前端聚焦42/0/0/0；全量初轮332/0/0/0、中间335/0/0/0、最终336/0/0/0（各36文件），均退出0。lint、build（含vue-tsc）、后端skipTests package、JAR与diff检查均退出0，构建非测试不填用例数。未单独重复typecheck，未跑真实浏览器E2E。
- 跳过／安全证据：19项为原7个外部门禁类，名单见报告，不是真实模型/向量通过。新测试逐响应及Logback消息/异常栈检查，六轮后端日志合成密码/BCrypt模式扫描0命中。失败注册无记录、失败改密散列及两个时间列不变、成功改密旧Token401/新Token200；非代理无效输入Mapper/encoder/JWT零交互。
- Git：本地HEAD `01ac774caff491b978dd2268e8b427355a64b97a`；GitHub连接器本日实时main `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，祖先0/3。B4-A～C本地历史、B4-D/E未提交树及旧报告保留；本批无SHA、未暂存/提交/推送。没有把本地验证写成远端发布。
- 兼容／剩余风险：旧版本或迁移可能存在超72字节输入对应的BCrypt散列，无法由散列恢复原长度；本批登录/当前密码也拒绝超限，可能影响这种历史输入。未查正式库、未批量改密码，若证实有用户受影响需独立授权恢复方案。短当前密码兼容有真实测试。B5的其他风险不在范围；未证明开发者掌握或性能提升。
- 清理与停止：本批测试用户剩余0；本轮唯一临时容器及凭据已删除，共享资源不动。代码、回归和档案完成后停止。
- 学习关联：[L-B5P2A-01](SOURCE_LEARNING_BACKLOG.md#l-b5p2a-01)～03；新增3条，清单57条，全为待学习／未确认。下一步由开发者独立讲解、复现和练习，不自动进入其他业务开发。

<a id="b5-p2-b"></a>

### B5-P2-B — Embedding 上游错误日志脱敏

- 日期：2026-11-13。状态：已审查、复现、编码、编译、Mock HTTP日志测试、真实隔离MySQL回归、完整普通回归及打包；未暂存/提交/推送，真实模型及Milvus未执行。依据：[验收报告](../verification/B5_P2B_EMBEDDING_LOG_SAFETY_20261113.md)，关闭B5-0的B5-P2-02/B5-FIX-B候选，仅限本批边界。
- 问题／根因：Spring AI 1.1.8 RetryUtils默认错误处理器把HTTP正文放入NonTransientAiException/TransientAiException；旧SpringAiEmbeddingClient.requestBatch捕获全部RuntimeException并保留cause，GlobalExceptionHandler完整日志会泄漏；validateResponseModel直接拼接供应商模型名。HTTP客户端本来已是固定安全502。
- 改前证据：两轮25/0/0/0、退出0的观察测试，先14项、增强JSON夹具后16项日志/栈含合成sentinel。6类HTTP、7类网络、2类Jackson诊断、1类恶意模型名均有实证；通过表示完成观察，不表示改前安全。空响应WARN实测仅对象标识，未泄漏输入；null data/item实际产生Spring AI NPE。原始结果与解释均保留。
- 改动文件／方法：新增[EmbeddingOpenAiApi](../../src/main/java/com/kun/aiinterview/knowledge/embedding/springai/EmbeddingOpenAiApi.java)的ResponseErrorHandler/embeddings；[EmbeddingConfiguration](../../src/main/java/com/kun/aiinterview/knowledge/embedding/EmbeddingConfiguration.java).createOpenAiApi接入；[SpringAiEmbeddingClient](../../src/main/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingClient.java).requestBatch/safeFailureCategory/validateResponseModel安全转换。新增SpringAiEmbeddingLogSafetyTest，定向更新SpringAiEmbeddingClientTest和FullInterviewBusinessOfflineTest；所有绝对路径见报告第5节。
- 调用链：EmbeddingClient → OpenAiEmbeddingModel.call（项目maxAttempts=1）→ 专属API HTTP状态/响应形状检查 → 项目结果校验 → RAG/MySQL资格校验或知识处理。失败经Orchestration/Workflow或KnowledgeDocumentProcessingService收尾后进入原GlobalExceptionHandler；事务、attempt提交权、补偿规则均未改。
- 技术取舍：不读HTTP错误正文/状态文本/诊断header，仅复制数字状态；网络/解析按类型最多遍历10层cause，新异常不保留原cause/suppressed；null body/data/item在框架转换前拒绝，其他结果校验原样保留。业务/程序错误原样抛出；不是catch-all，不关闭全局栈，不增加重试/通用错误框架，不复制整个Spring AI模型。
- 证据与环境：`/private/tmp/ai-b5p2b-20261113/`，含两轮baseline、各轮独立XML/summary、javap、日志扫描、保全与清理记录。macOS26.7.1 ARM64/Java21.0.13/Maven3.9.16；本批唯一MySQL8.0.45 tmpfs、127.0.0.1:65293、ai_interview_test的13表、+08:00。门禁和隔离初始化器保留，未改受管配置或Schema。
- 测试与命令：报告第6节保留完整选择器和`run.py`入口。baseline与baseline-json各25/0/0/0；client-focused为195/0/0/1（RealEmbedding门禁）；service-focused为302/0/0/0；full-regression和最终final-regression均1832/0/0/19、139类、1813非跳过，全部退出0。最终审查恢复未使用chat路径为框架默认值后重跑完整回归，没有功能测试失败被隐去。package跳过测试，计数不适用；diff检查退出0。
- 覆盖：新增40项根Logback/完整stack/cause/suppressed/MockMvc安全合同；业务新增11项Embedding失败→FAILED→同payload恢复和1项文档FAILED→READY；原B4-B日志36、知识处理补偿/事务、RAG/MySQL、fencing/并发和失败恢复回归通过。业务类124项有外围测试事务，不能独立证明跨请求提交；独立事务测试另行验证。实际向量网络为Mock。
- 日志证据：两轮聚焦及两轮全量Maven日志中本批sentinel、假API Key、Embedding输入和知识内容标记均0；完整异常本地栈和受控分类保留。19项跳过为原七类真实服务门禁，不计为集成成功。
- Git与保全：HEAD/远端main SHA见本节最新核验；本批无SHA，保留B4-D/E/P2-A及其他已有内容。没有自动暂存、commit、push或清理工作区；只有本批临时数据库资源收尾，不涉及共享容器/数据卷。
- 未验证／剩余：真实模型/Milvus、代理/TLS、部署APM/wire DEBUG未做；其他模块或数据库/补偿新增suppressed不构成全局脱敏保证；深链类别可能降级但不复制原文。无性能改善声明。依赖升级需重跑框架边界合同。
- 学习关联：[L-B5P2B-01](SOURCE_LEARNING_BACKLOG.md#l-b5p2b-01)～03，新增3条，共60条，全为待学习；没有开发者本人掌握证据。完成本批后停止，不扩展MQ/Redis、其他SDK治理、前端、部署或真实模型测试。

<a id="c6"></a>

### C6 — 可靠性修复累计审查、回归证据与提交规划

- 建立／更新日期、负责人：2026-11-13，Codex。用户明确只做累计审查、证据核验、Git规划及工程档案，不自动真实测试或提交。
- 关联报告／版本：[C6收口](../verification/C6_RELIABILITY_PHASE_CLOSEOUT_20261113.md)，HEAD加B4-D/E、B5-P2-A/B工作区；临时复核证据`/private/tmp/ai-c6-20261113/`。原B4/B5验收报告均保留。
- 状态：审查／计划／文档完成；复用已编译、已测试、已打包的同一源码快照；本轮没有新编码／编译／JUnit或Vitest执行。准入 **CONDITIONAL_GO**，未发现新P0/P1；不等于真实模型、真实Milvus或浏览器整链通过。
- 实际问题与根因：多批源码、共享测试及未跟踪台账混在工作区，历史总数与HEAD不能单独标识交付树；需核实代码、依赖、运行目标和原始证据，再设计可编译依赖组。未以报告结论直接替代核验。
- 改动文件／关键入口：仅新增C6报告、更新两份工程清单。复核DeepSeekEvaluationClient的requestCompletion/safeFailureCategory、MilvusConfiguration.milvusClient、useInterviewReport.submitReport/recoverAfterMutationFailure、PasswordPolicy与changePassword、EmbeddingOpenAiApi.embeddings及SpringAiEmbeddingClient.requestBatch；这些生产方法本轮未修改。
- 主要调用链／边界：requestId精确载荷→attempt claim→RAG/单次模型调用→Java校验／计分→当前attempt锁行及Session CAS短事务；报告GET只读、POST按数据库stale/version领取→FINAL汇总→Report/Weakness/READY原子提交；改密仍锁用户行并通过credential_version使旧JWT失效。
- 方案与取舍：B4-D/E/P2-A/P2-B分别11/5/21/7文件均与对应历史快照一致；P2-B最终9项交付哈希一致，JAR同哈希，故复用最新全量，不无依据重复付费或本地回归。代码、配置或依赖变化后必须重新评估；没有声称重建／编译了新的中间Git提交。
- 测试证据：重新解析37个独立历史XML归档；最新后端139类 **1832/0/0/19**，1813非跳过，历史`sh mvnw -B -ntp test`退出0；前端36文件 **336/0/0/0**，历史`npm run test:unit`退出0。最终lint/build退出0，build包含vue-tsc；后端skipTests package退出0，均不是C6新执行测试。19个真实服务门禁跳过保持，普通真实MySQL实际运行。
- 关键分层：DeepSeek日志36、Embedding日志40、DeepSeek分类42／loopback HTTP4、真实SDK15／合成gRPC12、真实MySQL fencing19、并发重放18／领取后恢复3／报告FINAL24／工作流事务6／报告事务9／stale CAS3、密码29+19／并发改密1、JWT过滤32、隔离初始化23均F/E/S=0。124项离线业务有外围事务，不能单独代表跨请求提交或真实模型。
- 新执行检查：git diff两种check退出0；npm ls退出0，278个已安装版本匹配lock；99个打包运行依赖匹配当前测试classpath JAR，另1个为Boot打包工具。JAR哈希`1a12fe23c58bb9d966eaba618987cde97721654676448294d308c1fadfa9b11b`与P2-B一致；无本地/测试配置及私钥类文件名。入场950文本文件高特异秘密模式扫描0候选，范围不包括未提交的本地Compose；扫描不证明绝对安全。
- 日志口径：专用DeepSeek/Embedding标记0；P2-B业务/最终全量各3次PRIVATE_MODEL_OUTPUT仍来自既有内部程序异常fixture，与C3一致；不泛化为所有日志零标记。无新真实秘密泄漏证据，也不宣称全局日志治理完成。
- 历史失败／跳过：37归档保留红测、夹具错误和环境错误，B4-A仅日志201/0/85/0及85/0/0/0；C6没有新的功能测试失败。真实服务及浏览器未运行，环境、专用凭据、预算和数据准备未确认，不能记PASS。
- Git计划：保留已有B4-A/B/C提交，之后按B4-D→B4-E→P2-A→P2-B→工程文档五批，共50路径；明确排除mvnw、Compose、两处.DS_Store和MQ文档5项。共享FullInterviewBusinessOfflineTest当前6个hunk全部归P2-B，两个台账完整归末批，不机械拆旧历史。当前本地/远端SHA及未提交状态见1.1，未add/commit/push。
- 残余风险／准入：历史超长密码兼容、旧任务与费用不确定、真实Milvus/迟到孤儿/维护CLI、原浏览器恢复键、APM及独立异常日志范围、容量/质量/费用缺证据均保留。真实测试须evaluation隔离目标、专用Key、明确请求/金额预算、合成账号题库、精确ID清理与去敏HTTP/SQL/日志采集；B5旧H14预期更新为合法72字节成功、超限400，不再预期500。
- 关联学习：[L-C6-01](SOURCE_LEARNING_BACKLOG.md#l-c6-01)～03，新增3条后共63条，全部掌握未确认。没有开发者本人讲解、复现或独立修改证据。
- 下一步／停止：只在新的明确任务中完成真实准入准备或执行精确Git提交；本轮不启动模型、Milvus、浏览器测试，不改数据库，不部署，不自动开启后续候选。

<a id="c7"></a>

### C7 — 可靠性修复阶段本地 Git 收口

- 日期／负责人：2026-11-13，Codex；仅 Git 收口及提交状态补记，不开发新功能，不修改已验收的生产逻辑、测试源码、配置、依赖或 SQL。
- 问题与方案：落实 C6 已验收的五批 50 路径计划，防止跨批缺类、缺导出及无关文件混入。保留既有三个本地提交，按 B4-D → B4-E → B5-P2-A → B5-P2-B → docs 顺序精确暂存；共享 FullInterviewBusinessOfflineTest 的 6 个 hunk 全部归 P2-B，两份累计台账完整归 docs。
- 版本与归属证据：四个代码 SHA 见 1.1；入场 999 个文件的 SHA-256／mode 与 C6 结束快照一致，55 个变动路径正好为 50 个计划路径及 5 个排除项，index 与 C6 相同且为空。逐次检查 cached diff、路径、hunk、暂存 blob、敏感模式及权限；四批代码完整 patch 与提交前留存 patch 相同。
- 原源码／调用链保持：MilvusConfiguration 的单次 SDK 策略、useInterviewReport 的显式恢复、PasswordPolicy 与 DTO／Service、EmbeddingOpenAiApi 与适配器均仅入库；具体方法、调用链和取舍沿用各批记录。源码／配置／构建／工具／SQL 的 688 文件摘要仍为 `6309802a92b18cb4eaebab2fde0ef771acb8c4e10a3ec33f1988f72419350449`。`mvnw.cmd` 既有工作区 CRLF 与 Git LF 差异由 `.gitattributes` 解释，原／新提交 blob 一致，并非本轮修改。
- 本轮新执行的编译检查：使用 `git archive <完整SHA>` 导出到独立临时目录。B4-D、P2-A、P2-B 各运行 `sh mvnw -o -B -ntp -DskipTests test-compile`，均退出 0；B4-E、P2-A 各对 tsconfig.app.json 和 tsconfig.node.json 运行 `vue-tsc --noEmit`，4 次均退出 0。构建信息写入临时目录，没有借用后续批次源码掩盖依赖；B4-E 后端与 B4-D 相同，P2-B 前端与 P2-A 相同，docs 无源码变化。
- 功能测试：本轮未新执行 JUnit／Vitest；复用经源码、原日志、summary、139 份 XML 和打包 JAR 核对的历史后端 **1832/0/0/19**、前端 **336/0/0/0**，原运行退出 0；前端 lint／build 历史退出 0。19 项真实服务门禁仍为跳过，不是集成成功。XML 摘要仍为 `cf2fc547c08b074f33767cb8e9d1d859813e4fe3cf44429327c4e17670e5d01c`，JAR 摘要仍为 `1a12fe23c58bb9d966eaba618987cde97721654676448294d308c1fadfa9b11b`。
- 检查器异常记录：临时导出脚本首轮因系统 Python 的 tarfile 不支持 `filter` 参数退出，尚未开始 Maven；改为校验归档成员后导出，编译通过。首次 Git blob 对比将既有 mvnw.cmd 换行转换误判为不一致，核对属性及原提交后校正检查器。两者均未修改项目文件，也不是功能测试失败。
- 文档更新：本台账追加实际 SHA／验证记录，SOURCE_LEARNING_BACKLOG 补充同一源码的提交定位；C6 和各批验收报告正文不变。未新增第二份收口报告，未改变 12 个实施／审查批次、4 个候选和 63 条学习条目的统计口径。
- 保全与安全：`mvnw` mode、compose.mac.local.yaml、两处 .DS_Store、MQ 文档仍排除；内容、mode、属主及 ACL 检查保持。未纳入 target／dist／node_modules、本机配置、真实凭据、数据库备份或临时证据。合成测试 fixture 随原测试提交，扫描未发现高特异秘密候选；扫描结论不替代全局安全保证。
- 临时证据：`/private/tmp/ai-c7-20261113-zz1q6mlv/`，包含入场清单、各批暂存 patch／审计、独立编译日志、历史测试复核与提交后 final-check.json；临时文件未进入 Git，仍有被系统清理的留存限制。
- 学习与边界：关联 L-X-02、L-C6-01／03，仅增加实际 Git 定位依据，63 条仍待学习／掌握未确认。没有真实 DeepSeek、Embedding、Milvus 调用，没有数据库操作、部署或推送；远端同步等待下一次明确指令。原 C6 风险及真实测试准入条件不变。

## 3. C0/C3/C6 累计证据与口径修正

- C0：聚焦 **553/0/0/0**，全量 **1589/0/0/19**；工具 gate **37/0/0/0**、公开离线 Java **28/0/0/0**／Python **9/0/0/0**／候选证据 Java **54/0/0/0**。另有真实 MySQL 历史 Jev × B3 的 **2 个手工 Java 断言场景**，失败／错误／跳过 0，不混入 JUnit 总数；编译、package 退出 0。首次 JAR 检查脚本路径错误退出 1，修正路径后退出 0，不是源码失败。
- C3：聚焦 **136/0/0/0**，业务 **336/0/0/0**，全量 **1698/0/0/19**（1679 非跳过），退出均 0；package 退出 0、测试不适用。其 clean 编译和回归针对累计最终树，不是重新测试 B4-B 独立中间提交。
- C3 loopback 全量 **606/602/606/607ms**；与 B4-C 的四个数是不同轮次，不能混写或以差值声称性能改善。
- C0-P2-001 的 DeepSeek 错误 body 日志通道已由 B4-B 定向处理；C3-P2-01 是验收口径澄清：business/full 各 **3 次 `PRIVATE_MODEL_OUTPUT`** 来自既有 `internalExceptionsKeep500WithoutModelRetry` 编程异常 fixture，绕过 DeepSeek 边界；其余 9 个扫描标记为 0。不是专用 DeepSeek 测试失败，也不证明所有日志全局脱敏，更不证明真实秘密已泄漏。
- 上述每次普通全回归的 19 skipped：KnowledgePersistenceIntegrationTest 10，RealEmbeddingSmokeTest 1，RealMilvusVectorStoreSmokeTest 1，RealKnowledgeDocumentProcessingSmokeTest 2，RealKnowledgeDocumentProcessingFailureIntegrationTest 2，RealMilvusSchemaValidationSmokeTest 2，AiSystemBaselineEvaluationTest 1。普通真实 MySQL Mapper／事务测试不属于这些跳过开关。

## 4. 未来候选任务（未授权实施）

2026-11-13 用户确认：未有正式定义的编号登记为候选占位、范围待确认。以下均无本批实现、测试结果或提交 SHA；不因建档而启动开发。每项启动时复制第 6 节模板，再以证据明确范围。

原 B4-D 候选“并发许可/安全调用观测”来自 B4-A/C3；2026-11-13 用户明确将本轮 B4-D 定义为 Milvus SDK 重试与超时治理。旧方向保留为**未编号、未实施候选**，不自动分配给 B4-E～B9。其学习记录迁移至 L-CAND-01/02，不能把本批向量修复写成许可或指标已实现。

2026-11-13 后续更新：用户已明确 B4-E 为报告 GENERATING 人工恢复入口，实施与验收记录移至第2节。其余编号继续保持候选，不因本轮完成而启动。

2026-11-13再次更新：B5已明确为本轮B5-0准入审查/测试计划，记录移至第2节；后续B5-FIX-A/B仅为候选建议，不因本批审查完成而获得实施授权。B6～B9仍未定义。

2026-11-13 C6状态校正：上段是B5-0时点记录；B5-FIX-A/B后来分别获授权并以B5-P2-A/B完成工程修复，当前不再是未实施候选。其他候选不因C6准入而获得开发授权。

<a id="b6"></a>

### B6 — 候选占位，范围待确认

- 尚未找到正式名称、问题定义或验收范围。源码入口／依赖／技术取舍／测试计划待补；证据、实现与 SHA：无。学习占位：[L-B6-01](SOURCE_LEARNING_BACKLOG.md#l-b6-01)。

<a id="b7"></a>

### B7 — 候选占位，范围待确认

- 尚未找到正式名称、问题定义或验收范围。源码入口／依赖／技术取舍／测试计划待补；证据、实现与 SHA：无。学习占位：[L-B7-01](SOURCE_LEARNING_BACKLOG.md#l-b7-01)。

<a id="b8"></a>

### B8 — 候选占位，范围待确认

- 尚未找到正式名称、问题定义或验收范围。源码入口／依赖／技术取舍／测试计划待补；证据、实现与 SHA：无。学习占位：[L-B8-01](SOURCE_LEARNING_BACKLOG.md#l-b8-01)。

<a id="b9"></a>

### B9 — 候选占位，范围待确认

- 尚未找到正式名称、问题定义或验收范围。源码入口／依赖／技术取舍／测试计划待补；证据、实现与 SHA：无。学习占位：[L-B9-01](SOURCE_LEARNING_BACKLOG.md#l-b9-01)。

未映射到编号的证据池：跨层 deadline 与 stale 配置关系、独立维护 CLI 的 Milvus 策略、其他模块日志边界、可选 usage／费用。来源为 B4-A/B4-C/C3，不擅自把它们分配给 B5～B9，也不承诺实施顺序或完成时间。前端 GENERATING 显式恢复已按本轮明确授权完成 B4-E，真实端到端证据仍待补。

## 5. 证据留存与待补事项

2026-11-13 本机原始归档仍存在；以下完整 XML 均重新解析并核对 T/F/E/S：

- J1：[final-regression-reports](/private/tmp/ai-j1-20261111/final-regression-reports)，128 个 XML，**1526/0/0/19**。
- B1：[full-regression-reports](/private/tmp/ai-b1-20261111/full-regression-reports)，129 个 XML，**1549/0/0/19**。
- B2：[full-regression-reports](/private/tmp/ai-b2-20261111/full-regression-reports)，130 个 XML，**1559/0/0/19**。
- B3：[full-regression-reports](/private/tmp/ai-b3-20261111/full-regression-reports)，131 个 XML，**1589/0/0/19**。
- C0：[full-regression-reports](/private/tmp/ai-c0-20261111/full-regression-reports)，131 个 XML，**1589/0/0/19**。
- B4-B：[full-regression-reports](/private/tmp/ai-b4b-20261112/full-regression-reports)，132 个 XML，**1635/0/0/19**。
- B4-C：[full-regression-reports](/private/tmp/ai-b4c-20261112/full-regression-reports)，134 个 XML，**1698/0/0/19**。
- C3：[full-reports](/private/tmp/ai-c3-20261112/full-reports)，134 个 XML，**1698/0/0/19**。
- B4-A：复核原 [focused-tests.log](/private/var/folders/xx/vxww8vxd1hz_f8szvp9th6j40000gn/T/b4a-audit-dnu77b5b/focused-tests.log) 与 [mockito-rerun.log](/private/var/folders/xx/vxww8vxd1hz_f8szvp9th6j40000gn/T/b4a-audit-dnu77b5b/mockito-rerun.log)，分别 **201/0/85/0**、**85/0/0/0**；未拿当前 target 覆盖文件冒充历史 XML。

各批复现／聚焦 XML 亦按独立运行目录复核。工具 gate、package、手工探针及环境解释沿用原验收报告，不声称本轮重跑。具体命令、完整失败过程、隔离容器及清理记录在各原报告，无需复制为第二份验收报告。

待补／仍有限制：

1. 原 XML／日志在临时目录，当前存在不代表长期持久归档；长期保留的位置、脱敏清单及校验摘要尚待确定。不得整目录上传临时凭据或原始请求；本轮没有移动、复制或公开这些文件。仓库中的报告和测试源码已提供可长期追溯的摘要与复现入口。
2. 没有本组批次的真实模型、收费故障恢复、真实 Milvus、浏览器＋真实模型端到端验收（B4-D 已补充实际 SDK / 合成 gRPC 重试注入），以及生产负载／P95／成本改善证据。历史其他阶段的真实服务报告不能自动覆盖本次代码版本。
3. DeepSeek的cause深度边界／环形链专项、真实公网网络边界、全局日志治理和历史READY数据质量仍未全面证实；Embedding环形／深链已有B5-P2-B专项，不能把旧C3缺口泛化到所有客户端，亦不能反向外推为DeepSeek已有同样专项。
4. B6、B7～B9的正式验收定义待补；B4-D/E及B5-P2-A/B工程修复均已完成。实际模型测试尚未执行，C6只作有条件准入和证据核验；候选登记不是需求审批。
5. B4-A～B4-C 的远端 main 发布证据尚无；本次两份台账和 AGENTS 说明尚无提交 SHA。后续获授权提交／推送后再更新观测日期、实际 SHA 和远端包含关系。
6. 开发者逐技术点的讲解、复现、独立修改／反例分析证据尚未提供；学习清单全部保持掌握未确认。

## 6. 每批任务更新模板

复制以下模板作为新批次；候选任务先填问题和待证据项，其余用“未执行／待确认”，不要用空白或默认通过。维护时保留旧事实，新增有日期的状态更新，不改写历史验收结论。

```markdown
### <批次 ID> — <具体问题／目标>

- 建立／更新日期、负责人：
- 关联报告／问题证据（路径、版本、运行 ID）：
- 状态：候选／审计／设计／已编码／编译／测试／打包，分别说明。
- 实际问题、触发条件、根因；哪些仍是推测：
- 改动文件与关键方法（生产／测试／配置／文档；区分未改调用入口）：
- 主要调用链及事务／所有权边界：
- 选择方案、替代方案和技术取舍：
- 实际结果与不在范围内的事项；性能结论须给对照实验：
- 测试运行逐项：日期、命令、代码基线、环境、Mock 边界、T/F/E/S、退出码、原始证据链接。
- 测试类型分别列：Unit/Mock、Mock HTTP/MockMvc、真实 MySQL、Loopback、真实模型/向量、浏览器/端到端。
- 失败／错误／跳过解释、修正及重跑；未执行和阻塞明确列出：
- Git：本地完整 SHA／未提交路径／暂存状态；远端分支、核验日期、远端 SHA 与祖先关系；未核验写未知。
- 关联学习 ID：前置知识、源码入口、核心问题、练习、掌握状态与证据（同步学习清单）。
- 残余风险、待补证据、下一步与授权边界：
- 后续状态更新：<日期；发生的事实；证据；不改写旧记录>
```

本次维护变更记录：2026-11-13 建立 7 条历史记录及 7 条候选；核验 Git／历史测试归档；新增 36 条学习记录；仅文档变更，未提交／推送。

后续维护记录：2026-11-13 B4-D 已修复应用 Milvus 路径并完成1732/0/0/19普通回归；保留历史7批与旧未编号候选。新增7条源码学习任务，当前43条，全部掌握未确认。仍未暂存、提交、推送。

后续维护记录：2026-11-13 B4-E 完成前端报告恢复入口、56项专项/317项前端全量/123项后端定向回归及构建；将原1条学习占位拆成7条，清单共49条。B4-D及其他既有文件保留，工程实施与开发者掌握分别记录；未暂存、提交、推送。

后续维护记录：2026-11-13 B5-0完成累计审查、1732/0/0/19后端和317/0/0/0前端回归、工具/构建、两项P2临时复现及真实测试计划。CONDITIONAL_GO；不改业务代码，不启用真实模型。原B5占位拆为6条，学习清单54条，全部待学习。只更新指定4份文档，未暂存、提交、推送。

后续维护记录：2026-11-13 B5-P2-A完成密码字节边界修复，1780/0/0/19后端和336/0/0/0前端最终全量回归及打包通过；新增3条学习任务，共57条，全部待学习。保留B4历史与既有工作区，未暂存、提交或推送。

后续维护记录：2026-11-13 B5-P2-B完成Embedding日志边界修复、40项日志专项及1832/0/0/19最终普通回归；新增3条待学习任务，共60条。B5-FIX-B候选已按授权收口，其他候选不启动；未暂存、提交或推送。

后续维护记录：2026-11-13 C6完成累计审查、37归档XML及快照／依赖核验，复用1832/0/0/19后端和336/0/0/0前端最终证据，新增五批精确提交计划与真实测试准入条件。未改生产／测试源码，未重跑测试或构建，未执行真实调用或Git写操作；CONDITIONAL_GO。新增3条学习任务，共63条，全部掌握未确认。

<a id="d3-bcd"></a>

### D3-B/C/D — RAG 正文、真实入库与检索验收

- 2026-11-14（Asia/Shanghai），Codex。51 篇冻结语料含原 E01 和 50 篇新正文，52 个生产 Chunk，文档到评分点 316 条关系覆盖 298 个点。正文通过 AI 交叉复核，人工抽查待做。 [最终验收](../verification/D3_BCD_FINAL_ACCEPTANCE_20261114.md)
- 复用 `KnowledgeDocumentAdminService → KnowledgeDocumentProcessingService → persistChunksAndMarkReady`。evaluation 实查 51 READY、52 ACTIVE、52 条真实向量，E01 和题库 63/298 保全。生产 `EvaluationRagQueryBuilder → EvaluationRetrievalAdapter → KnowledgeRetrievalService → EvaluationPromptBuilder` 实跑 72 条，公开 60 条，独立 24 条。Prompt 直接相关率低于门槛，保留首次记录。
- 单场 EASY 真实 HTTP 经过一次同键恢复后完成 5 MAIN、3 FOLLOW_UP 和 READY 报告，独立连接核验 5 FINAL。定向 JUnit 首轮 103/0/21/0（Mockito attach），显式 agent 重跑 103/0/0/0。实际 Embedding 215、DeepSeek 10；按量估约 ¥0.07323，未核账单。未执行全量 JUnit。下一轮仅隔离环境人工及小规模 AI 测试 CONDITIONAL_GO。
- 本轮生产源码没有改动。入场 HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`，实时远端 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`。语料、工具及验收按本地提交记录追溯，未推送。L-D3BCD-01～03 待学习，掌握未确认。
