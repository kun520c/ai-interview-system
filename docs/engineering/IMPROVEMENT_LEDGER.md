# 工程优化台账

建立日期：2026-11-13；最近核验：2026-11-14（Asia/Shanghai）。仓库：`/Users/kun/Developer/projects/ai-interview-system`。

本台账记录可追溯的工程事实，不替代原验收报告。源码学习及掌握证据独立维护于 [SOURCE_LEARNING_BACKLOG.md](SOURCE_LEARNING_BACKLOG.md)。初次建档只修改文档；同日 B4-D 后续开发、回归和当前 Git 状态另见本批记录，不改写历史验收。

## 1. 维护口径与证据基线

- 2026-11-14 E3-E01更新：真实配置已加载，1次百炼请求完成E01 READY、2个Chunk和2条向量的双向对账，真实入库 **PASS**；详见[本次验收](../verification/E3_E01_REAL_INGESTION_20261114.md)。下方旧BLOCKED记录保留原时点事实，没有新增生产实现批次。

- 2026-11-13 E0 补审时点：local MySQL 中 user=1、其余12核心表为空，test 13表为空；Milvus 启动失败，跨库状态未知，整体仍 BLOCKED。见 [E0](../verification/E0_TEST_ENVIRONMENT_READINESS_20261113.md)；下方 D0 是当时历史状态。

- 当前登记 **12 条已开展批次记录**：J1、B1、B2、B3、B4-A、B4-B、B4-C、B4-D、B4-E、B5-0、B5-P2-A、B5-P2-B；另有 **4 条未来编号候选**：B6、B7、B8、B9。B5-0仅审查、测试与计划，不计为业务修复。C0/C3是累计验收证据，不重复计为实现批次；B5-0提出的B5-FIX-A已获本轮明确授权，以B5-P2-A完成密码边界修复；B5-FIX-B亦已获明确授权，以B5-P2-B完成Embedding日志边界修复。
- 2026-11-13 C6 新增累计审查／证据复核记录，不增加生产实现批次；12条批次、4条候选口径不变。C6没有修改生产／测试源码，没有重新执行功能测试或构建；经快照、依赖与原始证据核实复用最新后端1832/0/0/19和前端336/0/0/0，详见[C6收口](../verification/C6_RELIABILITY_PHASE_CLOSEOUT_20261113.md)。
- 2026-11-13 D0 新增离线数据审计及 D1/D3/D4 方案，不增加生产实现批次；12 条批次、4 条候选口径不变。D0 当时 MySQL/Milvus 实时核验 BLOCKED，不能将仓库 21 题/97 点/21 文档或离线 52 Chunk 当作在线数据。详见 [D0 审计](../verification/D0_DATA_READINESS_AUDIT_20261113.md)；本轮未执行功能回归或真实模型测试。
- 此处 B1/B2/B3 是 2026-11 的并发、恢复、报告修复，不是 AGENTS 历史章节中的 Embedding B1、Milvus B2 或 Phase 5 Batch 1～3。
- 区分设计／审计完成、已编码、已编译、已测试、已打包、已暂存、已本地提交、已进入远端分支。测试通过与开发者掌握源码分别记账。
- `T/F/E/S` 表示 Tests / Failures / Errors / Skipped。完整测试数包含跳过；不同轮次存在重叠，禁止相加称为独立用例总数。编译／`-DskipTests package` 的测试计数不适用。
- 历史批次中的“通过”指链接报告对应的历史运行；初次建档汇总现存XML属于复核，不是重新执行。后续B4-D/B4-E/B5-0明确标注各自新执行结果，尤其B5-0完整回归不得混写成历史重算。未复核的工具结果明确引用报告。
- Unit/Mockito 验证 Java 分支和调用契约；Mock HTTP 验证协议映射；MockMvc 不代表浏览器或真实监听端口。真实 MySQL 表示实际执行 MyBatis SQL／事务，但模型仍可为替身。
- `FullInterviewBusinessOfflineTest` 有类级 `@Transactional`：是真实安全链／业务／MySQL + Mock HTTP 的离线业务链，不能独立证明跨请求提交或外部调用不在事务内。独立无外围事务的并发／事务测试另列。
- Loopback HTTP 使用真实 JDK 网络栈与本地合成服务，既不是纯 Mock HTTP，也不是真实 DeepSeek。真实模型可用性、费用、质量和浏览器端到端验证须分别提供证据。
- 不将验收日志耗时写成性能提升，不将预算估算写成 P95/SLA，不将 skipped 写成真实服务通过。

### 1.1 Git 实际状态

2026-11-13 D0 只读更新：本地 `main` HEAD 为 `0c6f92afe331954f7cea37d33db688fe781cbf2c`，即 C7 的独立文档提交；累计源码提交仍为 `71aeb22f7da906660763e9efb1100a20df7705c9`。本轮 GitHub 连接器实时核验远端 main 为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，与缓存相同，0 落后 / 8 领先。暂存区为空；本轮四份 D0/D1/D3/D4 新文档与本台账、学习清单修改均未提交、未推送，无 D0 SHA。入场 5 个用户变动保持，未 fetch 或修改 Git 引用。以下 C7/C6 段落保留当时事实。

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

<a id="d0"></a>

### D0 — 真实数据准备离线审计与扩容/多用户测试方案

- 建立／更新日期、负责人：2026-11-13，Codex。用户仅授权只读审计、数据设计和指定文档；补充明确不启动/创建/重建数据库容器、不修改配置，实时不可读时记录 BLOCKED 并交付离线方案。
- 关联报告：[D0 数据审计](../verification/D0_DATA_READINESS_AUDIT_20261113.md)、[D1 题库/组卷](../verification/D1_QUESTION_BANK_EXPANSION_PLAN_20261113.md)、[D3 RAG](../verification/D3_RAG_CORPUS_EXPANSION_PLAN_20261113.md)、[D4 质量/并发](../verification/D4_MULTI_AGENT_TEST_PLAN_20261113.md)；基线为上节完整 HEAD；临时证据 `/private/tmp/ai-d0-20261113/`，不替代永久数据库审计。
- 状态：离线审计、设计及文档完成；未编码随机逻辑、未扩充完整题目/知识正文、未入库、未运行面试测试。MySQL/Milvus 实时核验 BLOCKED；不能给出真实数据或真实应用可用 PASS。
- 问题／证据：`.env` 配置指向 127.0.0.1:3308/ai_interview，与历史 3306 不同；沙箱外独立 TCP 3308/3306/19530/9091/8080 均 ConnectionRefusedError:61，Docker 指定容器 exited 为补充证据。首轮沙箱 PermissionError 不是停服证据。现有历史入库/检索 JSON 在 Mac 仓库缺失，当前数据迁移完整性未知。
- 内容事实：仓库 21 题、97 点、每题权重和100、CORE齐备、精确重复0；139/139题干/答案/评分点正文在配套Markdown中存在。H01-SP4 的 `instanceof List<String>` 缺静态类型条件，Java21临时正负编译探针验证，列为后续修订；没有修改原题。现有21篇文档经当前生产chunker离线得到52块，52/52长度与历史表一致；不证明历史内容hash或实际MySQL/Milvus身份相同。
- 改动文件与关键入口：仅新增上述四报告、更新两工程清单。读取但未修改 InterviewPlanConfig、QuestionPlanSelector.select/findFirstUnselectedByCategory、InterviewQuestionPlanSnapshotService.prepareSnapshot、InterviewSessionService/TransactionService、QuestionAdminService、KnowledgeTextChunker.split、KnowledgeDocumentProcessingService.processDocument、KnowledgeRetrievalService.retrieve 及 Mapper/Schema。
- 调用链／边界：创建预查活跃Session→RR只读选题/评分点快照→user行锁短事务重查/完整落库；答案按归属+精确载荷/requestId→attempt→外部评价→fenced收口；知识处理按attempt写向量/短事务换Chunk/READY；检索按profile→MySQL资格过滤→Prompt证据。以上均为当前源码审计，不是D0新实现。
- 方案／取舍：新增42个主题使各难度必选类别各3候选，目标15/21/27=63；优先Java分类内独立随机+可控RandomGenerator，历史避重作为后续候选，不改Schema。RAG暂定21+30=51篇，按机制和来源组织，不复制参考答案。A真实模型质量+独立人审与B真实用户/Mock上游负载分开证据、预算和账号。
- 新执行检查：Python结构/对应检查21题97点无结构异常、139/139正文存在；独立javac当前生产chunker退出0，21/21文档离线成功、52块；Java21泛型编译探针2项符合预期（1个exit0、1个预期exit1）；端口及容器只读元数据检查完成；报告数量/覆盖/链接/文件保全检查另记D0最终结果。均不是JUnit测试。
- 最终文档检查：20项通过、0项失败，覆盖题目/文档/评分映射与数量、预算算术、68条学习记录、链接/表格/围栏、敏感模式和保全；两种git diff --check退出0。临时证据为`final-document-checks.json`，997/997非授权变更的入场文件正文/mode不变。
- 测试类型／计数：JUnit/Surefire/Vitest、全项目构建、Unit/Mock回归、MockMvc、真实HTTP、真实MySQL SQL、真实Milvus数据查询和模型评价均未执行，T/F/E/S不适用；SQL/Milvus数据核验为BLOCKED。实际DeepSeek/Embedding调用0、SQL写入0、Milvus写入0。C6/C7历史计数只引用，不充作本轮结果。
- Git／保全：本轮无暂存/commit/push；HEAD与index保持，入场999文件中除两份授权工程文档外正文/mode保全，5个用户变动原样保留。当前远端main及0/8关系见1.1；未核验其他远端分支。
- 学习：[L-D0-01](SOURCE_LEARNING_BACKLOG.md#l-d0-01)～05，新增5条，总计68条，全部待学习（未确认），没有开发者本人讲解/复现证据。
- 残余风险／下一步：服务准备和实时数据补审、H01修订、历史原始证据恢复、逐篇许可与版本核对、新题人工审查、模型预算和真人评审。全部列为后续任务，本轮停止，不自动实施或收费测试。

<a id="e0"></a>

### E0 — 本机环境恢复与只读数据补审

- 日期／负责人：2026-11-13，Codex；证据：[E0 报告](../verification/E0_TEST_ENVIRONMENT_READINESS_20261113.md)、[D0 第7节](../verification/D0_DATA_READINESS_AUDIT_20261113.md)。状态 **BLOCKED**：MySQL 已恢复和只读核验，Milvus 启动失败后停止，跨库检查未执行；独立 evaluation 环境仍为方案。
- 问题与边界：D0 无法连接；原项目 MySQL/Milvus 容器与专用卷存在。检查挂载和空卷初始化风险后只启动原容器；Milvus 日志显示 etcd leader changed，不能推断卷损坏，已终止重启循环。没有创建库、导入、删卷、改配置或调用模型。
- 数据：MySQL 8.0.45 在 3308，local 库 13 表中 user=1、其余 12 表均为0；test 13表全0；36项完整性 SELECT 成功、均为空样本0，评分权重无题可核验。该实例无 evaluation 库；Milvus collection/向量及跨库一致性未知。仓库 21题/97点和21文档为离线来源，不代表数据库当前内容。
- 文件与调用链：新增 E0，追加 D0、本台账和学习清单；未修改生产、测试或配置。环境核验链为容器/卷与端口→确认数据库身份→只读一致性快照→SQL 回滚；向量链因 Milvus 不可用而中断。权衡是保留原卷并隔离副本诊断，不以重建覆盖历史证据。
- 验证：本轮离线静态检查21题/97点及42题、30文档规划编号；MySQL 36/36 SELECT 正常，空样本异常0，首次回滚调用和一条列名失败后修正复跑成功。Docker Compose config --quiet exit 0。Milvus gRPC 连接失败；未运行 JUnit/Mock/真实模型/真实Milvus测试或构建，T/F/E/S 不适用。没有功能或性能通过结论。
- Git：HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`；E0及D0均未跟踪，两个台账已有并保留未提交变更，未暂存、提交或推送；本地缓存 `origin/main` 为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，本轮未核验实时远端。其他用户改动保留。
- 学习：[L-E0-01](SOURCE_LEARNING_BACKLOG.md#l-e0-01)，待学习（未确认），没有开发者掌握证据。后续先备份 Milvus 并在副本诊断，或明确独立环境与单库 schema 写入清单；真实导入、处理及费用预算需后续独立授权。

<a id="e0-r2"></a>

### E0-R2 — Mac 独立 Milvus 备份、部署和基础验收

- 2026-11-13／Codex；证据：[E0-R2 报告](../verification/E0R2_MAC_MILVUS_DEPLOYMENT_20261113.md)。状态：独立 Compose 已配置、真实基础功能已验证；稳定性 **有条件/未完全验收**。旧实例 2.6.24 退出的直接触发链与上游 issue #53144 一致，本机根因未证实；新 2.6.26 不含相应启动竞态修复。
- 旧卷只读归档并 `tar -df` 与原卷核对，再在独立临时目录逐文件解包比对；归档 SHA-256 `1112ab06387ad2c5c6c3e5dc409f578c63678d729abb601b52a22fdddb98f533`。未执行旧服务恢复演练。新建 `compose.milvus.e0r2.yaml`、新容器/卷及 19532/9093 回环端口；旧卷和 MySQL 卷保留，MySQL 未写入。具体文件、资源、恢复边界和调用链见报告；未改生产源码、状态机或环境门禁。
- 验证：定向 Maven T/F/E/S **25/0/0/0**（门禁 23，真实 Milvus schema 2），退出 0；真实 SDK 合成向量 seed/verify/cleanup **3/0/0/0**（阶段数，非 JUnit），首次主动重启后数据保留、按 ID 删除后非目标保留。第二次重启后只读检查退出 0。两次主动重启关闭时均出现 `panic: context canceled`，服务随后 healthy；启动期出现 `leader changed` 警告。未执行全量回归、真实模型、正式 RAG 或长期负载，不能推断性能提升与长期稳定。
- Git：基线 HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`；本轮文件未暂存、未提交、未推送，保留工作区既有更改；本地缓存 `origin/main` 为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，未核验实时远端。本批无提交 SHA。
- 学习：[L-E0R2-01](SOURCE_LEARNING_BACKLOG.md#l-e0r2-01)，待学习、掌握未确认。后续先调查两次关闭 panic 与竞态、资源余量和长期稳定，必要时另行设计外置 etcd；不自动导入题库、文档或调用收费模型。

### E0-R3 — Milvus 稳定性与隔离入库准入

- 2026-11-13／Codex；[验收报告](../verification/E0R3_MILVUS_STABILITY_GATE_20261113.md)，结论 **CONDITIONAL_GO**，仅在备份、专用 evaluation 目标、串行小批导入、失败暂停和逐批对账等条件及后续授权满足时适用。未修改源码或业务状态，未调用模型或导入业务资料。
- 链路：`MilvusConfiguration → MilvusCollectionInitializer → MilvusVectorStoreClient → SDK 2.6.20 → 19532/独立卷`；已 flush 的 B/C 重启前后强一致 ID/数量/检索分数一致，精确清理后 0；同客户端停机时失败三次后恢复。stop 退出134、`CloseEtcdClient` panic 与启动 `leader changed` 复现，未刷盘和长期稳定仍未知。PR #53367 未进入核查的 2.6 分支，2.6.27 不因此解决竞态；具体来源、限制与独立方案见报告。
- 真实验证：Maven Schema Smoke **2/0/0/0**（T/F/E/S）；SDK seed/watch/verify/cleanup **4/0/0/0**（阶段/失败/错误/跳过，非 JUnit）；只读 MySQL 和 Milvus 盘点见报告。未执行完整应用、全量回归、收费模型、长期/压力或断电验证。新增报告与 `backups/E0R3_MILVUS_20261113/` 探针/日志；修改本台账和学习清单。旧卷/容器保留，旧服务恢复演练未完成。
- Git：基线 HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`；远端 main 实时查得 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，本地领先 8；本批无 SHA、未暂存/提交/推送，既有工作区更改保留。学习见 L-E0R3-01/02，均待学习、掌握未确认。

## 3. C0/C3/C6 累计证据与口径修正

- C0：聚焦 **553/0/0/0**，全量 **1589/0/0/19**；工具 gate **37/0/0/0**、公开离线 Java **28/0/0/0**／Python **9/0/0/0**／候选证据 Java **54/0/0/0**。另有真实 MySQL 历史 Jev × B3 的 **2 个手工 Java 断言场景**，失败／错误／跳过 0，不混入 JUnit 总数；编译、package 退出 0。首次 JAR 检查脚本路径错误退出 1，修正路径后退出 0，不是源码失败。
- C3：聚焦 **136/0/0/0**，业务 **336/0/0/0**，全量 **1698/0/0/19**（1679 非跳过），退出均 0；package 退出 0、测试不适用。其 clean 编译和回归针对累计最终树，不是重新测试 B4-B 独立中间提交。
- C3 loopback 全量 **606/602/606/607ms**；与 B4-C 的四个数是不同轮次，不能混写或以差值声称性能改善。
- C0-P2-001 的 DeepSeek 错误 body 日志通道已由 B4-B 定向处理；C3-P2-01 是验收口径澄清：business/full 各 **3 次 `PRIVATE_MODEL_OUTPUT`** 来自既有 `internalExceptionsKeep500WithoutModelRetry` 编程异常 fixture，绕过 DeepSeek 边界；其余 9 个扫描标记为 0。不是专用 DeepSeek 测试失败，也不证明所有日志全局脱敏，更不证明真实秘密已泄漏。
- 上述每次普通全回归的 19 skipped：KnowledgePersistenceIntegrationTest 10，RealEmbeddingSmokeTest 1，RealMilvusVectorStoreSmokeTest 1，RealKnowledgeDocumentProcessingSmokeTest 2，RealKnowledgeDocumentProcessingFailureIntegrationTest 2，RealMilvusSchemaValidationSmokeTest 2，AiSystemBaselineEvaluationTest 1。普通真实 MySQL Mapper／事务测试不属于这些跳过开关。


### E1 — 独立 Evaluation 环境初始化（阻塞）

- 2026-11-13／Codex；[E1 验收报告](../verification/E1_EVALUATION_ENVIRONMENT_SETUP_20261113.md)，**BLOCKED**。两目标只读核验均不存在，未创建对象或导入数据；自动审批拒绝停止新 Milvus 实例，离线一致备份未完成。
- 文件：新增 evaluation 专用 DDL 候选和验收报告；更新本台账和学习清单；无业务代码、配置或测试更改。源 SQL 13 表保留并包含两个 attempt 字段，历史隔离 DDL 不直接执行；候选未执行，实际目标结构和索引待验。配置选 evaluation schema/collection，实际端口需显式覆盖；隔离初始化器不校验端点。
- 证据与类型：真实 MySQL 只读盘点及两次逻辑导出摘要一致；真实 Milvus Java SDK 只读 health/三个集合 Strong count，evaluation 不存在；旧卷归档 SHA-256 和可读性通过。新实例离线备份、恢复、创建后重启、完整应用、真实模型和 JUnit 未执行；无 T/F/E/S 测试计数可报告。详见报告。
- 取舍与边界：原两个 MySQL 库及三份卷保留；受限本地导出含敏感数据且未恢复演练，不能当作恢复已证实。只在保护条件满足后重查目标并执行候选；发生部分创建应停下核查。
- Git：HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`；本轮无提交 SHA，文档与既有改动未暂存，未提交/推送；`origin/main` 跟踪引用 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，本轮未实时核验远端。学习见 L-E1-01/02，均未确认。
- 风险与后续：已知停止 panic、启动竞态和未刷盘数据风险；取得停机批准和审批后完成一致备份、恢复方案和现场复核再继续。E2、模型调用和正式导入不在本次范围。

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

<a id="e1-e3"></a>

### E1–E3 — Evaluation 真实环境与首批题库落地

- 2026-11-13／Codex；证据：[分阶段执行报告](../verification/E1_E3_REAL_DATA_EXECUTION_20261113.md)，本机运行目录 `/Users/kun/Developer/projects/ai-interview-system/backups/E1E3_20261113/`。**A PASS、B PASS、C PASS；D 离线准备 PASS、收费执行 BLOCKED（待预算授权）**。本记录更新旧 E1 阻塞的当前结果，不覆盖历史审批拒绝记录。
- 实际问题：缺少新 Milvus 一致离线恢复点和独立 evaluation 数据环境；冻结资料仅在仓库。已获本轮短停授权并通过执行审批；新实例停止时再次退出134/panic，离线归档、独立卷恢复和原实例重启SDK对账通过，不宣称优雅停机或长期稳定。
- 实际创建：MySQL `127.0.0.1:3308/ai_interview_evaluation`，13表/163列/42索引/15FK/42CHECK；Milvus `127.0.0.1:19532/default/knowledge_chunk_vectors_evaluation`，1024/COSINE/AUTOINDEX，空向量。旧两库全量逻辑dump逐字节不变，SHA `3048c1b5926ecac9ad5aeb9906d23366a4bba556e5acd123c071fe32c76165e8`；三个旧集合元数据和Strong count不变，旧卷/备份保留。
- 文件：新增上述报告；追加本台账与学习清单；临时执行器/日志/备份在Git忽略目录（artifact-manifest.json列全路径）。生产/测试源码、正式及候选DDL、YAML、冻结题库未改。关键调用链：`QuestionImport → Validator → TransactionTemplate → QuestionAdminService.createQuestion → MyBatis → PlanSnapshotService/SnapshotFactory`；所有业务INSERT经正式Service，模型开关关闭。
- 真实结果：回滚演练20题/93点全部撤销；单事务提交冻结21题/97点；独立只读核对5/7/9题及97点完整快照，其他11表0。H01原文未改，泛型instanceof修订建议待审，真实评分准入仍受该缺陷限制。D仅选E01切成2块，拟1次百炼Embedding、预算上限¥0.15，未授权、未上传、未处理、未调用。
- 验证：当前HEAD，Java21，Maven离线定向Unit/Mock **123/0/0/0**（隔离/Service/快照/Milvus Schema/chunker），MockMvc/DTO **27/0/0/0**，两组无重叠总150；真实MySQL手工rollback/import/verify **3阶段/0/0/0**；SDK before/restored/restarted/final四次完整盘点退出0、目标既存Schema复核退出0。归档SHA与tar原卷/恢复卷比较通过。XML、日志和实际命令见报告。
- 失败与限制：首次Python摘要API兼容错误后仅修执行器继续校验；SQL原始文本比较因自增计数/等价ASCII字符集标记失败，结构复核通过；Milvus首次新集合尚未加载，后续只读检查通过，未重建。未执行全量mvn test，因为会写用户明确保护的ai_interview_test；未重定向/削弱原测试。真实模型/完整面试/收费故障恢复/长期压力均未执行。
- Git：本地HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`；GitHub连接器2026-11-13实时main `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，本地领先8。SSH读取publickey认证失败，不影响后续连接器核验。本批文档与证据无SHA、未暂存/提交/推送，既有工作区保留。
- 取舍与下一步：一次性严格目标执行器满足原子导入，不新增通用生产导入框架、不重构环境配置。保留异常停机和H01问题，收费步骤分开审批；D1内容扩充、D2随机组卷、D3知识扩充的准入见报告。新增学习 L-E1E3-01～03，均待学习、掌握未确认；已有L-E1-01/02可关联本次新证据。

<a id="e3-e01"></a>

### E3-E01 — 单篇收费入库授权后的环境门禁（BLOCKED）

- 2026-11-13／Codex；[执行记录](../verification/E3_E01_REAL_INGESTION_20261113.md)，证据目录 `/Users/kun/Developer/projects/ai-interview-system/backups/E3_E01_20261113/`。用户已授权百炼qwen3.7-text-embedding、2块3188字节、1024维、最多1次请求/¥0.15；后来确认“尚未配置，先记录阻塞”。**未执行真实入库**，没有新的业务实现。
- 实际阻塞：项目受保护.env仍是18080本地占位端点和占位Key，local YAML不存在，进程无真实EmbeddingKey；18080由其他应用监听。未替换配置或把真实请求发往该地址。实际地域/适用计费绑定未知，暂停付费步骤，授权本身仍有效且未消费。
- 已核验：E01原始/规范化hash和2块切片输出与阶段D完全一致；归档SHA一致；MySQL及Milvus原服务healthy。真实MySQL结构/计数及完整逻辑导出与上轮终态一致，evaluation21题/97点/知识0/Chunk0；SDK四集合Strong count=0及元数据与原快照一致，没有可复用READY E01。
- 静态调用链和取舍：未来仍走 `AdminService.uploadDocument/processDocument → ProcessingService → SpringAiEmbeddingClient → MilvusVectorStoreClient → persistChunksAndMarkReady`；Spring maxAttempts=1不单独证明底层HTTP零重发，独立执行器的JDK单次exchange与请求门禁仅提出方案，未编码或运行。本轮不创建半成品UPLOADED文档、不发付费探针、不重启。
- 验证数量：离线Java探针1次退出0、真实MySQL只读1阶段通过、真实SDK盘点1次退出0；Maven/JUnit/Mock/全量0次。Embedding/DeepSeek/其他模型0次，无usage、响应向量、实际费用或账单证据；不是收费成功或补偿测试。
- 文件：新增E3报告及忽略目录中的只读证据，追加本台账和学习清单；生产/测试源码、题库/H01、DDL/YAML/.env未改。未暂存/提交/推送，本地HEAD仍 `0c6f92afe331954f7cea37d33db688fe781cbf2c`；远端本轮未重查，上一轮同日main为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`。本批无SHA。
- 后续：用户补齐本机受保护配置并通知路径后，重新前置核验，在既定授权范围内执行；不得把0.15元预算当作额外重试额度。学习 L-E3E01-01，待学习，开发者掌握未确认。

<a id="e3-e01-live"></a>

### E3-E01 — 受控单次真实入库完成（PASS）

- 2026-11-14／Codex；[执行报告](../verification/E3_E01_REAL_INGESTION_20261114.md)，证据目录 `/Users/kun/Developer/projects/ai-interview-system/backups/E3_E01_LIVE_20261113/`（按准备开始日期命名）。本轮解决真实配置缺失，保留上一条BLOCKED历史记录；发送门禁北京时间00:03:22.923948落标记后执行收费请求，该时间不是独立抓包时间。
- 配置：读取权限0600的 `/Users/kun/.config/ai-interview/evaluation-embedding.env`，北京业务空间HTTPS端点、qwen3.7-text-embedding、1024维、profile `qwen3.7-text-embedding-1024-dense-v1`。DeepSeek文件 `/Users/kun/.config/ai-interview/evaluation-deepseek.env` 仅静态检查，未注入本轮应用或调用，不宣称连通性通过。没有输出凭据或修改Codex全局配置。
- 持续授权记录：用户授权后续本项目既定真实功能测试、评分、追问、报告和多AI测试自主使用上述DeepSeek额度，无需重复申请同范围许可；必须保持evaluation隔离，记录真实请求/usage/费用，合理限制并发和重试，禁止无限付费压测。本次仍严格只有1次百炼Embedding、DeepSeek 0次；持续授权不自动启动其他任务，也不扩大Embedding许可。
- 实际目标：MySQL `127.0.0.1:3308/ai_interview_evaluation`，UUID `85dd1e00-cf98-11f1-8677-9eb9380f25e7`；Milvus `127.0.0.1:19532/default/knowledge_chunk_vectors_evaluation`，collectionId `470437151302223600`。保持EnvironmentIsolationInitializer；对象已存在才启动，既存Schema校验通过，无DDL或账号授权修改。
- 文件／调用链：新增20261114报告、旧报告追加完成链接、更新本台账和学习清单；临时执行器及证据在Git忽略目录。沿用 `AdminService.uploadDocument/processDocument → ProcessingService.claimProcessing → KnowledgeTextChunker → SpringAiEmbeddingClient → MilvusVectorStoreClient → persistChunksAndMarkReady`，真实Spring事务代理，没有新增业务算法或修改切片/补偿规则。
- 费用与尝试：两块固定3188 UTF-8字节，1次HTTP 200；请求级prompt_tokens=821、total_tokens=821、completion_tokens=null。北京¥0.50/百万输入tokens，usage计算值 **¥0.0004105**，账单未核验；两条保守上限¥0.128低于授权¥0.15。无实时硬预算，采用固定输入、一次拦截放行、发送前持久标记、进程锁及Spring/JDK禁重试；计数依据应用执行证据，未提供独立抓包。预算不是重试额度。
- 真实落库：documentId=1/version=1/READY；2个ACTIVE Chunk、2条1024维有限向量，attempt `b6b493157f4545b8a5966ae61e845519`。MySQL vectorId集合与Milvus STRONG读取完整集合逐条相等，文档/序号/profile正确，向量逐元素等于本次响应。复用2个向量的1次COSINE搜索Top1均为自身，非独立语义或Hit@K评测。
- 保全：旧两库前后dump逐字节相同（SHA `3048c1b5926ecac9ad5aeb9906d23366a4bba556e5acd123c071fe32c76165e8`）；evaluation其余11表逐块一致，21题/97点和H01未动；其余3集合及目标Schema不变。服务均healthy且无重启。历史离线备份SHA复核通过，本次未追加停服备份，新数据不在旧离线恢复点内。
- 验证／失败：临时Java执行器初次编译因AutoCloseable不兼容失败，修正后编译通过；无费预检2次（首次MySQL SQLSyntaxErrorException，调整为已有管理员凭据后PASS，均0模型/0上传）；正式真实管道1次PASS，MySQL/SDK身份对账和独立前后保全PASS，无模型失败、补偿或故障实验。Maven/JUnit/Unit/Mock/MockMvc/全量回归0次未执行，T/F/E/S不适用，历史150项不计入本轮。
- Git／后续：HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`不变，本批无SHA，未暂存/提交/推送；远端本轮未重查。新增L-E3E01-02，累计79条，全部待学习、掌握未确认。E01保留且本轮结束；账单、独立语义质量、DeepSeek实际调用、故障恢复仍未验证，原Milvus停机风险和H01内容审查不因本次成功解除。

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


2026-11-14 D1 Git复核：本地main为 `0c6f92afe331954f7cea37d33db688fe781cbf2c`，GitHub连接器实时main为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，本地领先8/落后0。D1无新提交，未暂存、未提交、未推送；数据库COMMIT不表示Git提交。

<a id="d1"></a>

### D1 — 63 道正式题库建设与受控扩容

- 建立／更新日期、负责人：2026-11-14（Asia/Shanghai），Codex。目标为 63 个正式候选及其来源、审查和持久化证据。
- 关联报告及版本：[D1 规划](../verification/D1_QUESTION_BANK_EXPANSION_PLAN_20261113.md)、[E1–E3 基线](../verification/E1_E3_REAL_DATA_EXECUTION_20261113.md)、[E01 真实验收](../verification/E3_E01_REAL_INGESTION_20261114.md)、[V2 JSON](../question-bank/D1_QUESTION_BANK_V2_20261114.json)、[V2 可读题库](../question-bank/D1_QUESTION_BANK_V2_20261114.md)、[来源和指纹清单](../question-bank/D1_BANK_MANIFEST_20261114.json)。[最终验收](../verification/D1_QUESTION_BANK_EXPANSION_20261114.md)、[逐题内容审核](../verification/D1_QUESTION_BANK_CONTENT_REVIEW_20261114.md)、[真实导入映射](../question-bank/D1_EVALUATION_IMPORT_MANIFEST_20261114.json)。
- 状态：V2 编写、结构校验和独立逐题内容审查完成，共新增 42 题、201 点；修订 H01 参考答案及 SP4，不新增其评分点。V2 合计 EASY 15、MEDIUM 21、HARD 27 道，298 个评分点。21 个“难度 × 必选分类”各 3 道，每题有效权重合计 100。实际导入状态见下文，不以离线题库数字代替数据库。
- 实际问题：V1 每个必选分类只有一个候选。H01 原题库把 `instanceof List<String>` 一概表述为非法，忽略 Java 21 取决于操作数静态类型和转换条件。新版区分 `Object` 的限制、`List<?>` 检查及允许的参数化类型测试，并说明运行时不逐一检查元素。原 V1 JSON、配套 Markdown 和审查证据保留。 [JLS 21 §15.20.2](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.20.2)
- 改动及入口：`docs/question-bank/` 版本化题库、清单和逐题审查。 `tools/question-bank-d1/build_bank.py` 按矩阵组装并验证完整题库，`revise_h01.py` 保留旧版并生成修订数据，`run_import.py` 组织本机受控运行，`D1QuestionBankImport.java` 的 `readBank/validate/checkReview/rehearse/importBank/verifyFinal` 负责业务导入与核验，`D1MilvusInventory.java` 只读盘点向量，`D1ImportOfflineTest.java` 检查 DTO 和题库负例。生产、测试源树及 Schema 不变。现有 `InterviewPlanConfig`、`QuestionPlanSelector`、RAG 和评分逻辑不改。
- 事务和隔离：导入器按版本及内容摘要绑定审查结果和回滚凭证，确认 evaluation profile、库身份和完整基线后，通过外层 `TransactionTemplate` 调用代理化 `QuestionAdminService.updateQuestion/createQuestion`。H01 保留题目主键，现有 Service 整体替换其评分点，因此评分点数据库 ID 可变。新题生成主键；事务内核对题目、评分点和保护数据，独立读回验收。历史 `interview_question` 快照独立保存，D1 不重算或改写。
- 技术取舍：一次性工具复用正式 DTO、Bean Validation、Service 和 MyBatis，无需新增生产导入接口或表结构。逻辑题号、题库指纹、数据库自增主键用途不同。独立代理内容审核与机器结构检查分别记录。若提交结果未知，先独立只读核验，再决定恢复；重复运行不会绕过基线条件。
- 定向验证：在当前 HEAD、Java 21、模型禁用条件下，Maven/Surefire 最终 **113/0/0/0**，含 Unit/Mock 93、MockMvc 20。覆盖环境隔离、题库 Service、DTO、管理员接口、快照工厂和组卷规则。首次 **113/0/73/0**，错误来自沙箱中 Mockito 附加失败；调整执行环境后同组重跑退出 0。原始结果保存在 `backups/D1_20261114/focused-{initial,retry}-summary.json` 和 XML 目录。
- 离线工具验证：Java 21 实际 Bean Validation 接受 V1 21 与 V2 63 个 DTO；独立工具边界测试 **14 项通过 / 0 失败 / 0 错误 / 0 跳过**，为 1 个有效输入与 13 个拒绝用例（重复标识、未批准映射、分类/难度、权重、枚举、未知字段、空值、UTF-8 上限及版本）。日志见 `backups/D1_20261114/question-validate.log`、`offline-boundary-strict.log`。该工具用例不与 Surefire 相加。
- 真实验证及持久化：真实 MySQL baseline/rollback/import/verify 四阶段最终均退出0。入场21题97点，正式提交并独立读回63题298点；H01题目ID33不变，5点评分点由147–151替换为392–396，新增42题净增201点。失败演练在H01替换及41题创建后触发正式权重拒绝，独立验证三库39表行摘要回到基线。最终原业务库和测试库完整dump逐字节相同，evaluation其他11表及旧20题/92点不变，Milvus四集合（含E01全2048个Float32值）完全相同；重复导入在初始化数据库前按预期拒绝。详见最终验收和本地backups/D1_20261114证据。本轮 DeepSeek 和 Embedding 调用均为 0，未开展真实面试、模型质量、浏览器端到端或全量回归。普通全量 Maven 测试涉及受保护测试库，不能用 113 个定向用例替代。
- Git：本地 HEAD 与实时远端核验见 §1.1。D1 新文档与工具及两份工程台账增量未暂存、未提交、未推送。入场1013文件中除两份授权追加台账外的1011文件保持正文和权限；两台账保留原文，HEAD/index不变。
- 学习关联：L-D1-01～04，累计 83 条，新增项待学习、掌握未确认，开发者证据未提供。
- 残余风险和下一步：**D2 数据准入 GO**，不代表当前已随机或RAG已覆盖63题。D2 另行实现、验证分类内随机，当前选择器仍按现有候选顺序选首项。题目难度和判分效果尚待独立候选人试答与后续评估。


<a id="d2"></a>

### D2 — 分类内随机组卷与真实 MySQL 回滚验收

- 建立／更新：2026-11-14（Asia/Shanghai），Codex；本批已设计、编码、编译、定向测试及受控真实MySQL验证，未打包、未暂存、未提交、未推送。独立报告：[D2验收](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-D2-随机组卷与真实数据库验收.md)。原D1与其他历史报告保留。
- 原因与输入：原 `QuestionPlanSelector` 每分类取首题并补题，现有SQL按分类／ID排序，使入库63题无法得到公平的分类内随机机会。实际evaluation基线63 ENABLED题、298有效评分点，EASY/MEDIUM/HARD为15/21/27、21桶各3；逐字段摘要与D1 manifest匹配，数据准入通过。
- 生产变更：仅 `src/main/java/com/kun/aiinterview/interview/service/QuestionPlanSelector.java` 的 `select`／调用级随机源重载、`groupCandidates`、`validateCandidate`。去重ID、筛选启用且必选分类、按ID稳定索引，每桶均匀抽1，按配置分类顺序返回。拒绝计划总数不等于必选分类数，移除补题；空分类明确失败。
- 随机机制与取舍：正式每次调用使用当前线程的 `ThreadLocalRandom`；测试注入本次调用独有的 `IntUnaryOperator`。没有单例可变随机器、缓存或跨会话状态。相比数据库随机排序，保留已有候选SQL，确定性测试可控制下标；没有新依赖、Schema、Redis或MQ。稳定排序成本在当前63题规模可接受，但未做性能对照，不声称提速。
- 未改调用链：`InterviewSessionService.createSession → prepareSnapshot(只读RR) → QuestionMapper(难度+ENABLED) → selector → enabled scoring points → createAndStartSession(用户行锁、二次幂等检查、全MAIN快照、激活、Session CAS)`。快照工厂、事务注解、Mapper SQL、评分／RAG／追问／报告／认证均未改。
- 测试变更：`src/test/java/com/kun/aiinterview/interview/service/QuestionPlanSelectorTest.java` 最终17项；覆盖5/7/9、分类／顺序、唯一ID、禁用／无关分类排除、重复行公平性、输入顺序变化、可重放、空桶拒绝和并发。固定seed300次为99/99/102，另穷举合法索引证明每题可达；非确定性生产源仅验结构，统计不作为唯一证据。
- 新工具：`tools/question-plan-d2/D2QuestionPlanAcceptance.java`、`run_acceptance.py`、`README.md`。固定3308/evaluation及UUID，保留EnvironmentIsolationInitializer，先备份+完整D1门禁+写入计划，再29个独立回滚探针，逐次独立连接核对13表。运行日志和全部原始证据位于 `/Users/kun/Developer/projects/ai-interview-system/backups/D2_20261114/`。
- 定向JUnit：`./mvnw -B -ntp -o -Dtest=<focused-command.json中14类> test`，**268/0/0/0**、exit0。Unit/Mock/DTO232、MockMvc36（面试16、管理员20），相关创建／事务／工作流及隔离测试通过。原XML在 `focused-surefire/`；未含历史报告。
- 工具负例：首次无凭据门禁 **4/1/0/0**，仅辅助脚本错误预期缺回执异常为NoSuchFileException、实际为FileNotFoundException；拒绝和零数据库操作正确。修正辅助预期后 **4/0/0/0**（各预期Java exit1），保留首次证据；生产未为此修改。工具最终javac与Python AST检查通过。
- 真实MySQL：`python3 tools/question-plan-d2/run_acceptance.py backup/baseline/exercise/verify` 四阶段exit0。exercise为 **29个探针/0失败/0错误/0跳过**，非JUnit：24串行+3并发+1空桶拒绝+1历史快照，成功创建28 Session及194 MAIN。三难度各8场均有8种组合；所有场逐项验证内容／点JSON／顺序／归属；重复创建请求另一难度仍复用原活动Session。
- 历史快照：在回滚事务临时禁用正式EASY/JAVA_BASIC的21、83、84，验证SQL排除禁用候选及缺桶拒绝；另一事务创建临时题125／点598、Session28，再用正式管理员Service更新临时题并替换点为599，重读历史快照完全不变。正式298评分点无修改或删除，临时status及自动更新时间全部回滚。
- 真实并发：3线程各自事务／禁用临时身份，Session25/26/27分别为EASY/MEDIUM/HARD，身份与快照互不污染；外层回滚合并了正常读写事务，只证明实际SQL/CAS/回滚内读取，不冒充独立commit后可见性。
- 最终保全：三库data-only摘要前后相同，另外两库完整结构加数据导出亦相同；evaluation13表逐次29次+最终1次独立核对。E01文档、Chunk与两条完整向量不变，Milvus前后SHA为 `ac27c00aafb21ac1191e73b1b2f4c5f1d6cf7f810de6ccb64b18af500bd14a07`。自增号段被回滚消耗，不重置序列。备份未做恢复演练。
- 未执行：普通全量mvn test及既有真实测试库集成测试（禁止写受保护ai_interview_test，未另启独立环境）；真实DeepSeek/Embedding调用0，未跑浏览器／模型质量／性能测试。选定JUnit skipped=0，不把未运行的guarded tests记作通过或跳过。
- Git：本地HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`；2026-11-14 GitHub连接器实查main `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，ahead8/behind0。index不变，原1040个非忽略文件除本批5个授权修改外1035个正文/mode保留；两台账仅追加。D2无提交SHA，未执行任何Git写操作。
- 用户追加约定：`AGENTS.md`记录2026-11-14持续DeepSeek额度授权（真实功能测试、评分、追问、报告和多AI测试不再逐次询问），并标注旧确定性组卷为历史、记录D2现行规则；不扩展当前D2到模型调用。
- 学习：新增L-D2-01～04，累计87条，全部待学习、掌握未确认；AI实现和测试不作为开发者证据。残余风险：未实现跨场去重；坏评分点仍按既有逻辑抽中后失败；小样本不证明严格均匀性；正常独立事务全回归未执行；RAG仍仅E01。本批停止，不自动进入D3或发布。


<a id="d3-a"></a>

### D3-A — RAG 知识来源、评分点覆盖与独立检索设计

- 2026-11-14（Asia/Shanghai），Codex。来源核查、逐点映射、旧文审查和语料/评测设计完成。本批没有生产实现。详见 [独立审查报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-D3-A-RAG知识覆盖审查.md)、[覆盖工作簿](../rag/d3-a/D3A_KNOWLEDGE_COVERAGE_20261114.xlsx)、[独立评测规程](../verification/D3A_RETRIEVAL_PROTOCOL_V1_20261114.md)、[查询 v1](../verification/D3A_RETRIEVAL_QUERIES_V1_20261114.json)。
- 数据基线：只读实查 MySQL 8.0.45 evaluation 的 63/298，全部逐字段及 ID 匹配 D1。E01 READY v1，2 个 ACTIVE Chunk。Milvus 2.6.26 evaluation Strong count=2，向量身份及 Float32 摘要匹配。两张检索追踪表各 0。仓库 21 篇用生产切片器离线得到 52 块，不计作在线数据。
- 覆盖与取舍：SUPPORTED 107、PARTIALLY_SUPPORTED 47、SOURCE_GAP 143、NEEDS_REVIEW 1（H01.02 同一实现类的条件）。保留现有题库与 D1 修订。51 个计划主题按版本和机制分批，J01 原字节复用，其他 50 篇需后续编写或修订。冻结 60 条独立查询、人工标注规程和指标。语料定稿后需冻结 Chunk 真值，公开仓库可见查询的限制已记入规程。
- 源码入口：`KnowledgeTextChunker.split → KnowledgeDocumentProcessingService.processDocument → KnowledgeDocumentProcessingTransactionService.persistChunksAndMarkReady`。检索入口为 `EvaluationRagQueryBuilder.build → EvaluationRetrievalAdapter.retrieve → KnowledgeRetrievalService.retrieve → KnowledgeChunkMapper.selectRetrievableByVectorIds → EvaluationPromptBuilder.build`。本轮确认 Top5 可能只剩部分合格证据，PromptBuilder 逐条写入实际合格证据。
- 验证：MySQL 最终只读对账 1/0/0/0，Milvus 最终只读盘点 1/0/0/0；21 篇/52 块离线检查与 298 点/21 旧篇/51 篇目录及 60 条查询校验通过。JUnit 0，Embedding/DeepSeek/真实入库/真实相似检索均 0。执行环境的 Docker socket 拒绝及临时脚本下标错误已在独立报告记录。
- Git：本地 HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`，GitHub API 实查远端 main `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，本地领先 8、落后 0。D3-A 文件未暂存、未提交、未推送，无新 SHA。新增 L-D3A-01～04，待学习，掌握未确认。D3-A 到此结束。

<a id="d3-bcd"></a>

### D3-B/C/D — RAG 正文、真实入库与检索验收

- 2026-11-14（Asia/Shanghai），Codex。51 篇冻结语料含原 E01 和 50 篇新正文，52 个生产 Chunk，文档到评分点 316 条关系覆盖 298 个点。正文通过 AI 交叉复核，人工抽查待做。 [最终验收](../verification/D3_BCD_FINAL_ACCEPTANCE_20261114.md)
- 复用 `KnowledgeDocumentAdminService → KnowledgeDocumentProcessingService → persistChunksAndMarkReady`。evaluation 实查 51 READY、52 ACTIVE、52 条真实向量，E01 和题库 63/298 保全。生产 `EvaluationRagQueryBuilder → EvaluationRetrievalAdapter → KnowledgeRetrievalService → EvaluationPromptBuilder` 实跑 72 条，公开 60 条，独立 24 条。Prompt 直接相关率低于门槛，保留首次记录。
- 单场 EASY 真实 HTTP 经过一次同键恢复后完成 5 MAIN、3 FOLLOW_UP 和 READY 报告，独立连接核验 5 FINAL。定向 JUnit 首轮 103/0/21/0（Mockito attach），显式 agent 重跑 103/0/0/0。实际 Embedding 215、DeepSeek 10；按量估约 ¥0.07323，未核账单。未执行全量 JUnit。下一轮仅隔离环境人工及小规模 AI 测试 CONDITIONAL_GO。
- 本轮生产源码没有改动。入场 HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`，实时远端 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`。语料、工具及验收按本地提交记录追溯，未推送。L-D3BCD-01～03 待学习，掌握未确认。

<a id="d3-e"></a>

### D3-E — 评分证据质量、受控对照与人工复核

- 2026-11-14／Codex。[质量与准入报告](../verification/D3E_EVIDENCE_QUALITY_REVIEW_20261114.md)、[回放结果](../rag/d3-e/EVIDENCE_SELECTION_REPLAY_20261114.json)、[人工复核入口](../verification/D4_RELEVANCE_REVIEW_CARDS_20261114.md)、[评分诊断](../rag/d3-e/SCORING_DIAGNOSTIC_20261114.json)及[累计用量](../rag/d3-e/API_USAGE_20261114.json)。入场 HEAD `921f8cdb24bfda50e4e9a4d76d4e1017449fe032`；原 D2 等未提交改动保留。本批没有生产 Java 或 Schema 修改；功能测试 CONDITIONAL_GO，评分质量 NO_GO。
- 源码链路：`EvaluationRagQueryBuilder → KnowledgeRetrievalService.retrieve → EvaluationRetrievalAdapter → EvaluationPromptBuilder`，同一结果用于 `RagTracePersistenceService`。新增 `tools/rag-d3/d3e_replay.py` 核验 156 条原始候选、水合和实际 Prompt；新增评分固定夹具、`D3EScoringProbe`、`d3e_scoring.py` 与摘要脚本；`SmokeUsageProbe` 扩充四类响应流场景，`balance.py` 复用余额校验。未引入运行时选择器。现有阈值、TopK、轨迹和业务状态保持原逻辑。
- 联合结果：公开 A/B/C/D 的 Prompt 直接数/实际条数 67/300、41/60、57/120、1/1；无答案误供 12/12、12/12、12/12、0/12。D 损失 MAIN 7 个 CORE 和 29 个点；C 丢 3 个公开跨主题意图并向所有负例给出材料。原独立集 15/18 <85%，3 个语料缺口保留原分母。标注为 AI 临时、人工待审，不能视为精准真值。评分八对 16 次 HTTP 200、16 个 JSON/Java 校验通过；对照仅是诊断，工程预期存在 E01.03、H25.03 等争议。
- 验证：独立临时 MySQL 8.0.45、13 张测试表和工作区副本上的普通 `mvn test` **1844/0/0/19**（T/F/E/S）、exit 0；`package -DskipTests` exit 0，19 skipped 非实服务通过。回放两次字节一致及合成边界校验通过；用量记录器四类探针 **4/0/0/0**，exit 0，不是 JUnit。新真实 HTTP EASY 因检测到近期活动 Session 安全停止，本轮未验证报告/历史真实读回；已有历史 EASY 不能替代。无前端源码变化，浏览器完整流程未跑。
- API：本轮 Embedding 0、DeepSeek 16，累计 215/26；以峰值及实际 usage 估总 ¥0.25908760，未对账。额度预检一次沙箱连接失败、一次成功；首次评分冻结比较在发请求前停止，证据保留、修正后从零请求恢复。未重试已发起模型请求。未切换服务或修改现有数据。EASY 只读预检复用了旧 `.env` 凭据来源，按本轮附件的私有凭据文件限制存在执行偏差；仅 SELECT/rollback，没有输出或外传凭据，已停止该路径。
- Git：本批工作区文件未暂存、未提交、未推送，无本批提交 SHA；`origin/main` 仅为本地缓存 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，本轮未查询远端。两份台账保留入场未提交内容并追加本段。盲审卡和答案索引保存在本地 Git 忽略目录，不入库。
- 学习：[L-D3E-01～03](SOURCE_LEARNING_BACKLOG.md#l-d3e-01) 均待学习、掌握未确认。下一步先人工复核，再在明确安全窗口进行隔离 HTTP 验证；本批不自动进入 MQ 或部署。

<a id="u1"></a>

### U1 — 人工体验反馈的两题、评分范围与等待状态

- 2026-11-14（Asia/Shanghai），Codex。基线 `921f8cdb24bfda50e4e9a4d76d4e1017449fe032` 加已有 D2 与 D3-E 工作区变动。交付两题候选、评分范围与交互代码及真实合成评分对照。实际用户原答核验、在线更新、真实浏览器和报告延迟待授权数据库入口与归属。报告保留 D3-E 评分质量 **NO_GO**。[U1 复核和命令](../verification/U1_MANUAL_FEEDBACK_REVIEW_20261114.md)
- `EvaluationPromptBuilder` v4 将题干与评分点作为必答边界，认可简短和等价表达，按已答与尚缺描述部分覆盖。`InterviewRequestTiming` 通过 MVC 拦截器关联提交、RAG 内部阶段、DeepSeek、报告聚合及提交。只记录低基数阶段和请求级关联 ID。前端 `useInterviewSession` 保护完整回包和同键恢复，增加即时等待说明、稳定键显示及可展开反馈。
- `U1EvaluationQuestions` 提供先归属、后读取的 owner-only 证据和固定两题正式 Service 事务工具。两题沿用类别、难度、知识点、点型及权重，未来更新由旧快照与当前行身份和哈希锁定。`tools/u1/` 封存真实合成对照，共享预算。8 对固定证据，16 HTTP 200，旧 7/8、新 8/8 通过 Java，仍有遗漏和部分覆盖争议。用量新增 DeepSeek 16、Embedding 0。正式真实业务读写需单独核实。
- 定向后端 **280/0/0/0**，独立日志通道 MockMvc **18/0/0/0**。独立临时 MySQL 普通全量 **1846/0/0/19**，exit 0，package exit 0。最终前端定向 **132/0/0/0**，全量 **339/0/0/0**，typecheck/lint/build exit 0。跑通工具离线检查及 DTO 校验。浏览器、报告完整真实耗时暂未测；模型诊断耗时不是 MQ 前的同步报告基线。
- 本批无提交 SHA，未暂存、提交或推送。远端仅见本地缓存，上轮盲卡与评价结果保持各自版本。新增学习条目 [L-U1-01～03](SOURCE_LEARNING_BACKLOG.md#l-u1-01)，掌握待确认。

<a id="u1-r1"></a>

### U1-R1 — evaluation 凭据入口与人工反馈归因复核

- 2026-11-14／Codex；[受阻验收报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U1-R1-人工反馈与真实浏览器复测.md)。状态：**BLOCKED / NO-GO**；已编码并完成离线定向测试，真实数据库、模型和浏览器端到端尚未验收。
- 问题与方案：旧 D3 入口从项目环境中获取高权限数据库字段；`tools/rag-d3/config.py` 的 `evaluation_database_config` 只接受权限受限的专用文件和固定 evaluation 目标，并拒绝 root、其他字段和数据库目标。`run.py → evaluation_environment`、`u1_timing.py` 及新增 `u1_questions.py → U1EvaluationQuestions` 使用该入口，缺配置即停止；不调用 shell 执行配置、不回退其他 DB 变量。名称检查不证明其他账号确实最小授权，仍需真实验证。历史 D1/D2 脚本保留旧高权限行为，D2 还会访问其他 Schema，本轮不运行。
- 实际环境：`127.0.0.1:3308/ai_interview_evaluation` 的普通应用账号返回 MySQL 1044；未成功读取目标 Schema 或 UUID，专用配置文件尚不存在。自动审批拒绝历史 root 连接，理由是高权限账号不符合其专用账号边界；未执行该连接、创建账户、改权限或在拒绝后间接绕过。拒绝前打开过历史入口启动的既有服务工作台，其当前数据库账号及 Schema 未独立验证。用户已确认账号和当时空闲窗口，浏览器仅显示该用户及 EASY 人工历史会话 链接；数据库归属、user_id、题答评价完整性和历史原答均未核验。
- 证据准备：`U1EvaluationQuestions.history` 的受保护证据增加当前 Chunk 正文、版本与时间；`rag_hit_log` 本身没有内容快照，不能无条件将当前内容当作历史提示词。集合 E08 和 ACID E04 仅完成候选 DTO 校验，未通过 Service 写入；当前服务未切换。现有计时脚本不保证目标题被随机抽中，也不覆盖双击或故障恢复，不得据此宣称真实验收通过。
- 验证：配置 Python unittest **7/0/0/0**；U1 包装器 **2/0/0/0**；前端四文件 Vitest **109/0/0/0**、typecheck 退出 0；隔离后端首轮 **46/0/18/0**（Mockito 自动附加失败），显式已有 Byte Buddy 测试代理重跑 MockMvc **18/0/0/0**，首轮另有 28 项通过。候选 DTO、计时工具编译和语法、Python 内存语法检查及 `git diff --check` 通过；`py_compile` 首次因缓存目录写入受限失败。一次已结束的隔离 MockMvc 启动日志意外显示临时测试口令，测试输出文件已脱敏。无真实模型调用和可计费请求；本轮费用 ¥0，未核账单。真实耗时样本为 0，不计算分位数。
- 变更：`tools/rag-d3/config.py`、`run.py`、`README.md`、新增 `test_evaluation_config.py`；`tools/question-bank-u1/U1EvaluationQuestions.java`、`u1_timing.py`、`README.md`、新增 `u1_questions.py` 与 `test_u1_questions.py`；`docs/verification/D4_MANUAL_AI_TEST_START_HERE.md` 和两份工程台账。原工作区改动及历史报告保留。生产 RAG 选择、数据库结构及历史会话未修改。
- Git：本地 HEAD `921f8cdb24bfda50e4e9a4d76d4e1017449fe032`；本地缓存 `origin/main` `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，ahead 11、behind 0；未实时查询远端。暂存区为空，本轮无提交 SHA、未提交或推送。学习：[L-U1R1-01～02](SOURCE_LEARNING_BACKLOG.md#l-u1r1-01)，均待学习、掌握未确认。
- 后续：取得经授权的非 root evaluation 专用账号，或由管理员在批准后按报告的 Schema 级最小 DML 方案创建；配置文件须 0600、目录 0700。先只读校验实际 Schema 和会话所有权，再重新确认空闲窗口、通过正式 Service 更新候选并做带目标题、双击及失败恢复的新会话真实浏览器验收，记录带样本数的耗时和实际用量。

#### U1-R1 后续 — evaluation 账号、历史归因与两题生效（2026-11-14）

- 按用户明确授权，经新的正式权限审核使用项目已有管理员连接执行账号管理；先查候选账号和授权，未发现重复。新建 `ai_eval_app@172.19.0.1`，仅授予 evaluation 的 `SELECT/INSERT/UPDATE/DELETE`；数据库名中的下划线已转义。私有配置 `~/.config/ai-interview/evaluation-db.env` 权限 0600、目录 0700。随后以该账号独立只读核验 MySQL UUID、固定 Schema 和精确授权，连接原业务库及测试库均被 1044 拒绝，未读取两库业务数据。
- 安全更正：服务器 `partial_revokes=OFF`；先前 U1-R1 报告的未转义 GRANT 示例可能匹配其他数据库，示例未被执行，现已明确加注并改正。依据 [MySQL GRANT 官方文档](https://dev.mysql.com/doc/refman/8.0/en/grant.html)。客户端来源由无默认 Schema 的非 root 只读连接实测，后续 Docker 网络变化时应重新验证。
- 已按授权只读核验人工历史会话归属及相关历史快照；原始答复、标识与证据保存在受保护的本地目录，未改写旧会话。
- 归因初判、历史得分与原始答复仅保存在受保护的本地证据；RAG 命中关联不证明历史提示词正文未变化。
- 两题在无活动会话／评价／报告的窗口，经 `QuestionAdminService.updateQuestion` 外层事务更新并独立读回；集合 ID 85 新评分点 600–603，ACID ID 24 新评分点 604–608，权重不变。受保护记录摘要和 人工历史会话 历史比较通过，证据 `backups/U1_20261114/question-apply-{intent,receipt}.json` 均 0600。数据库提交不等于 Git 提交。
- 真实浏览器验收准备：临时回环测试进程固定仅限合成账号的两道题抽样索引，仍调用原选择器及正式快照流程；计时和首次成功答题响应丢失注入仅作用于本轮合成会话。当前测试进程已准备，完整浏览器交互、评分和报告结果待后续记录；不得将准备状态写成通过。
- 后续状态：[账号与人工反馈归因补充报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U1-R1-账号配置与人工反馈归因补充.md)。未登录真实 HTTP 检查 401／业务码 401；既有 D3 合成账号本地 HTTP 登录成功，但浏览器工具对读取到的凭据做了脱敏，GUI 未能使用实际值登录；不能归咎应用认证逻辑，也未绕过保护。未新建合成应用账号或 Session，账号创建确认待用户；本轮启动的测试进程已停止，准备证据保留。原常驻后端未切换，不能将独立测试进程的启动视为 v4 提示词和计时代码已在常驻进程生效。模型请求 DeepSeek 0、Embedding 0；真实计时样本 0，浏览器答题／失败恢复／报告验收未通过。

#### U1-R1 后续 — 合成账号与真实浏览器验收（2026-11-14）

- [最终报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U1-R1-真实浏览器终验.md)补充上述时点记录。用户授权后通过正式注册接口创建普通 USER 账号（user ID 32），凭据仅保存在 `backups/U1_R1_BROWSER_20261114/synthetic-credentials.json`，文件 0600、目录 0700。先查无活动用户工作，再在 evaluation 中创建合成 Session 31；人工 人工历史会话 始终只读。
- 使用真实 Chrome、HTTP、evaluation MySQL/Milvus 和真实模型，合成会话完成 5 MAIN、1 FOLLOW_UP、6 条答案、5 FINAL、1 READY 报告；两道新题的浏览器展示和数据库快照分别与候选一致。临时测试选择器仅为本合成用户指定两题的抽样下标，仍通过原选择器及正式快照路径；不代表随机分布测试，常驻服务不使用该选择器或故障代理。
- 一次真实双击只发出 1 个提交。对服务端成功答题后的返回人为注入丢失，同页保留并锁定答案，使用原请求 ID 与原正文取得评价；独立 JDBC 对实际浏览器捕获的首轮 1/1、续跑 7/7 个 HTTP 答案正文逐项匹配，最终仅有 6 个唯一答案。前端展示的总分、缺项与修正匹配响应；报告生成和读取通过。首次临时选题器非目标类别空值异常导致创建事务回滚、0 模型调用；第二次故障注入后测试脚本的网络事件假设错误，提前终止，保留已提交会话并同键恢复，未重复该次评分。
- **P1：**响应丢失后，测试服务和 Chrome 重启、同一用户重新登录时，旧答案与请求记录仍在本机，服务端已推进下一题，页面未自动显示上一题反馈或旧题同键恢复入口；正式 HTTP 以原请求可取回。普通单次刷新未独立验证，勿将该组合场景扩大为所有刷新。后续需要覆盖跨重启、身份与多记录边界，并重新浏览器验证；不在本验收批中擅自修改恢复策略。
- 运行边界：13 张 evaluation 表先前已有行逐行摘要保持不变，题库、评分点、知识文档和 Chunk 无新增或变化；合成数据允许新增。Milvus 仅检索，Strong count 前后 52；未逐个比较向量摘要。经新安全窗口启动未经测试代理包装的 U1 evaluation 服务：前端 `127.0.0.1:15174`、后端 `127.0.0.1:18482`；与实测后端 261 个编译资源一致。部署后合成用户登录、Session 31 完成及报告只读取回通过，未登录 API 401。未改生产 RAG 筛选或数据库结构。
- 耗时：正常浏览器评分 n=4，点击到反馈 P50 3.105 s、范围 2.973–3.280 s；两次响应注入丢失与毫秒级重放不计入该分母。6 次服务端评分阶段中位数：Embedding 362.158 ms、Milvus 6.405 ms、MySQL 水合 3.249 ms、DeepSeek 2780.602 ms、校验 0.268 ms、提交 18.809 ms。报告 READY 可见 n=1，为 3.147 s。样本过小，不报告可靠 P95 或总体性能收益。
- 用量：新增 DeepSeek 7 次（评分 6、报告 1），输入 26127 tokens（命中 5120、未命中 21007）、输出 3624；Embedding 6 次共 1284 tokens，全部有 usage 且 HTTP 200。按周六空闲价格估算分别 ¥0.0356054、¥0.0006420，合计 ¥0.0362474，未核实际账单；此前 U1 的 16 次调用另计。[DeepSeek 价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)；[百炼价格](https://help.aliyun.com/zh/model-studio/qwen3-7-text-embedding)。
- 验证：临时五类别选题器离线 3/0/0/0、故障代理自检 1/0/0/0；1 场真实浏览器完成及上述独立读回、保全通过。本轮 Maven、Vitest 和全量回归 0 次，沿用此前独立记录，未将历史通过数称为本轮结果。真实请求证据在 `backups/U1_R1_BROWSER_RETRY_20261114/` 和 `backups/U1_R1_BROWSER_RECOVERY_20261114/`，权限均为 0700/0600。
- 本轮生产源码未改，仅新增私有证据、外部验收报告并追加本台账与学习清单。本地 HEAD `921f8cdb24bfda50e4e9a4d76d4e1017449fe032`；本地缓存 `origin/main` ahead/behind 11/0，未实时查询远端。本批无 Git 提交 SHA、暂存区为空，未自动 add/commit/push/reset/clean/stash/rebase。现有工作区内容保留。**evaluation 有限试用；整体发布与评分质量仍 NO-GO**。学习 [L-U1R1-03～04](SOURCE_LEARNING_BACKLOG.md#l-u1r1-03) 仍为待学习、掌握未确认。
- 日志补记：常驻后端首次启动时，Spring 自动生成的默认安全口令短暂写入权限 0600 的本机启动日志；发现后在运行日志内等长遮盖，未输出到对话、报告或 Git。当前安全链禁用表单登录与 HTTP Basic；后续应消除该启动日志中的口令输出，不能把已遮盖当成从未落盘。

<a id="u2"></a>

### U2 — 已提交评价的跨重启查看与默认用户自动配置（2026-11-14）

- **状态：U1-R1 对应 P1 已解决并通过真实浏览器验收；D3-E 评分质量 NO-GO 不变。** [独立验收报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U2-恢复修复与运行安全验收.md)，私有实测证据 `backups/U2_BROWSER_20261114/`（目录 0700、文件 0600）。本条更新先前时点的 P1 待修状态，保留原记录。
- 根因：服务端评分与推进已提交，响应丢失导致前端评价展示的 sessionStorage 未建立；localStorage 的原提交记录按旧题 ID 存储，而恢复只按 Session 当前题 ID 查找。全新浏览器没有任何原记录。`InterviewService.getDetail` 已先校验 Session 归属并返回持久化题答与评价，因此不新增接口、Schema 或状态机。
- 变更：新增 `PreviousInterviewFeedback.vue`，按需调用现有 GET 详情，从当前题之前按 `displayOrder` 选最近 `EVALUATED` 答案及有效评价，复用 `InterviewEvaluationPane`；在 `InterviewSessionView.vue` 加入口，调整 `sessionCopy.ts` 的旧提示。请求以账号代次、Session、当前题和组件生命周期约束，避免迟到结果串页；不重发答案或改 Session。现有详情响应还包含本场未来题目，组件不渲染；如需避免客户端拿到未来题目，属于待单独设计的接口范围问题。
- 安全：`AiInterviewSystemApplication` 排除 `UserDetailsServiceAutoConfiguration`，避免 Boot 在 JWT 安全链之外生成默认用户；没有关闭安全日志。六个独立 `@WebMvcTest` 切片各自排除同一自动配置，仍运行现有 JWT/401/403 断言。实际两次测试启动及常驻启动无默认口令提示；最终完整回归日志也无此提示。此前两次私有测试日志中的临时默认口令已遮盖，不能称为从未落盘。
- 测试文件：新增 `frontend/tests/PreviousInterviewFeedback.spec.ts`、`DefaultSecurityUserDisabledIntegrationTest`；`FullInterviewBusinessOfflineTest.readsCommittedPreviousEvaluationWithoutResubmissionAndRejectsAnotherUser` 用真实 MySQL/事务和 Mock 外部模型核对原评价、一个追问、重复 GET 不触发模型或 Session 变化、跨用户 404。六个控制器测试切片仅调整自动配置排除，未删除或弱化测试。前端覆盖原答未重发、无本机状态、主答/追问排序、未来题目不渲染、切换用户及迟到响应。
- 最终验证：独立临时 MySQL 8.0.45、13 表、模拟外部模型；定向后端 **165/0/0/0**，完整 Maven **1848/0/0/19**（140 套件），package 退出 0；19 项守护真实服务测试按配置跳过。前端完整 Vitest **346/0/0/0**（37 文件），lint、typecheck、build 退出 0。两次早期后端回归暴露测试切片仍生成临时默认口令，随后补全六个切片并重跑；修复前前端入口缺失的 6 项失败也保留为前置复现。最终聚合及 140 个 XML 摘要在 `backups/U2_BROWSER_20261114/maven-{results,suites}.json`，原始 XML 在 `/private/tmp/ai-u2-regression-rfsdkf_r/target/surefire-reports/`；临时容器和其随机口令已移除。
- 真实浏览器：用户确认停止操作且只读检查无活动工作后，新注册普通合成用户 33、Session 32。代理丢弃服务端成功评价后的首个响应；实际刷新后查看原题、原答及 93 分反馈，DeepSeek/Embedding 调用数始终 1/1。停止本轮前后端和 Chrome，启动新的前后端及全新浏览器目录并重新登录后仍可查看同一反馈，无答案 POST、无新增模型调用。继续完成 5 主答、5 评价、5 FINAL、1 READY 报告；HTTP 正文独立读回 5/5 一致，无重复评价或追问。真实会话未产生追问；有追问分支由上述 Mock 模型集成测试覆盖。已完成会话的最后一题可从工作台→报告→复盘只读查看。
- 实测前后 evaluation 13 表既有行摘要一致，题库、评分点、知识文档及 Chunk 无新增或修改，人工历史会话 不变；Milvus 只检索且 Strong count 前后 52，未做逐向量摘要。两次只读完成会话浏览器检查因工具提前匹配报告页时间线而失败，等待实际复盘路径后通过；一次端口复用失败改空闲端口，均无模型调用。
- 用量：DeepSeek 6 次（5 评分、1 报告），输入 21633 tokens（命中 3968、未命中 17665）、输出 2905；Embedding 5 次、952 tokens，均 HTTP 200。按周六空闲单价估算 DeepSeek ¥0.02936436、Embedding ¥0.00047600，合计 **¥0.02984036**，未核供应商账单。[DeepSeek 价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)；[百炼价格](https://help.aliyun.com/zh/model-studio/qwen3-7-text-embedding)。
- 安全窗口再次核验后，仅替换上一轮本项目所启动的 evaluation 后端：现有前端 `127.0.0.1:15174`、后端 `127.0.0.1:18482`，正常应用不含测试代理。实际登录、只读报告及 401/403 通过；新签名密钥使旧令牌需重新登录。Git 本地 HEAD `921f8cdb24bfda50e4e9a4d76d4e1017449fe032`，本地缓存 `origin/main` ahead/behind 11/0，远端本轮未实时核验；本批无提交 SHA、暂存区为空，未自动 add/commit/push/reset/clean/stash/rebase。学习 [L-U2-01～03](SOURCE_LEARNING_BACKLOG.md#l-u2-01) 仍待学习、掌握未确认。

<a id="s1"></a>

### S1 — 候选人详情题目可见性与 Git 收口（2026-11-14）

- 状态：完成审计、编码、定向和全量验证；[S1 记录](../verification/S1_INTERVIEW_VISIBILITY_SECURITY_20261114.md)。修复前真实 HTTP 新建 EASY 会话后详情返回全部 5 主问题，其中 4 个尚未作答；按本场提前泄题定为 P1。当前题和详情的独立 VO 未包含参考答案快照、评分点、权重或内部原始评价。
- 调用链与取舍：JWT 验签并读取当前数据库用户 → `InterviewController.getDetail` → `InterviewService.loadOwnedSession` → `getDetail` 仅保留当前题和已有答案的 `ANSWERED` 历史 → 独立 VO。未知或其他用户 Session 统一 404；不改变组卷、已保存快照、模型评分或状态机。并发导致当前题变化时 U2 前端现有校验保守拒绝并提示重试。
- 文件：`InterviewService.java`、`InterviewHistoryServiceTest.java`、`InterviewVisibilityHttpIntegrationTest.java`；`.gitignore` 排除系统元数据及独立嵌套检出。未更改数据库结构或 evaluation 业务数据。
- 验证：真实 HTTP、定向服务/控制器/组卷快照 **179/0/0/0**；U2 前端定向 **61/0/0/0**。后端首次普通全量 **1850/8/0/19**，临时 MySQL 的 UTC 与应用时区不一致；校正临时容器会话时区后失败相关 **41/0/0/0**，最终全量 **1850/0/0/19**。前端全量 **346/0/0/0**，lint、typecheck、build 退出 0。9 项工具离线测试通过。真实 HTTP 使用真实 JWT、MyBatis、临时 MySQL，外部 AI 和向量端口为 Mock；本轮真实模型调用 0。
- Git：入场本地 `921f8cdb24bfda50e4e9a4d76d4e1017449fe032`；2026-11-14 实时远端 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，入场 ahead/behind 11/0。本轮本地代码提交 `d090d25929bf21d9a69b4b2d3e8251c6ac6f9535`、`f1fb02b429a92796b2dc0dd4c83c2259b5315d22`、`4bed0e2525b8359d7a21f0b6b7a3a990f905e3d9`；文档提交及最终 ahead/behind 以 Git 日志和交付时核验为准。未推送。
- 学习关联：[L-S1-01～03](SOURCE_LEARNING_BACKLOG.md#l-s1-01)，均待学习、掌握未确认。真实用户会话线索保留在未覆盖的工作区原稿；暂存版本需泛化或排除，不提交原始用户数据、备份、密钥或日志。
