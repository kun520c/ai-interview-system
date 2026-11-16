# 工程优化台账

> **历史证据范围：**本文含不同时点的历史记录。部分原始数据、本地报告和实验工具仅保留在私有或未提交工作区，公开检出中的相关链接可能不可用；Git 和部署状态以最新记录及实际仓库为准。

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
- 实际环境：`127.0.0.1:3308/ai_interview_evaluation` 的普通应用账号返回 MySQL 1044；未成功读取目标 Schema 或 UUID，专用配置文件尚不存在。自动审批拒绝历史 root 连接，理由是高权限账号不符合其专用账号边界；未执行该连接、创建账户、改权限或在拒绝后间接绕过。拒绝前打开过历史入口启动的既有服务工作台，其当前数据库账号及 Schema 未独立验证。用户已确认账号和当时空闲窗口，浏览器仅显示该用户及 EASY 人工测试会话 链接；数据库归属、user_id、题答评价完整性和历史原答均未核验。
- 证据准备：`U1EvaluationQuestions.history` 的受保护证据增加当前 Chunk 正文、版本与时间；`rag_hit_log` 本身没有内容快照，不能无条件将当前内容当作历史提示词。集合 E08 和 ACID E04 仅完成候选 DTO 校验，未通过 Service 写入；当前服务未切换。现有计时脚本不保证目标题被随机抽中，也不覆盖双击或故障恢复，不得据此宣称真实验收通过。
- 验证：配置 Python unittest **7/0/0/0**；U1 包装器 **2/0/0/0**；前端四文件 Vitest **109/0/0/0**、typecheck 退出 0；隔离后端首轮 **46/0/18/0**（Mockito 自动附加失败），显式已有 Byte Buddy 测试代理重跑 MockMvc **18/0/0/0**，首轮另有 28 项通过。候选 DTO、计时工具编译和语法、Python 内存语法检查及 `git diff --check` 通过；`py_compile` 首次因缓存目录写入受限失败。一次已结束的隔离 MockMvc 启动日志意外显示临时测试口令，测试输出文件已脱敏。无真实模型调用和可计费请求；本轮费用 ¥0，未核账单。真实耗时样本为 0，不计算分位数。
- 变更：`tools/rag-d3/config.py`、`run.py`、`README.md`、新增 `test_evaluation_config.py`；`tools/question-bank-u1/U1EvaluationQuestions.java`、`u1_timing.py`、`README.md`、新增 `u1_questions.py` 与 `test_u1_questions.py`；`docs/verification/D4_MANUAL_AI_TEST_START_HERE.md` 和两份工程台账。原工作区改动及历史报告保留。生产 RAG 选择、数据库结构及历史会话未修改。
- Git：本地 HEAD `921f8cdb24bfda50e4e9a4d76d4e1017449fe032`；本地缓存 `origin/main` `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，ahead 11、behind 0；未实时查询远端。暂存区为空，本轮无提交 SHA、未提交或推送。学习：[L-U1R1-01～02](SOURCE_LEARNING_BACKLOG.md#l-u1r1-01)，均待学习、掌握未确认。
- 后续：取得经授权的非 root evaluation 专用账号，或由管理员在批准后按报告的 Schema 级最小 DML 方案创建；配置文件须 0600、目录 0700。先只读校验实际 Schema 和会话所有权，再重新确认空闲窗口、通过正式 Service 更新候选并做带目标题、双击及失败恢复的新会话真实浏览器验收，记录带样本数的耗时和实际用量。

#### U1-R1 后续 — evaluation 账号、历史归因与两题生效（2026-11-14）

- 按用户明确授权，经新的正式权限审核使用项目已有管理员连接执行账号管理；先查候选账号和授权，未发现重复。新建 `ai_eval_app@172.19.0.1`，仅授予 evaluation 的 `SELECT/INSERT/UPDATE/DELETE`；数据库名中的下划线已转义。私有配置 `~/.config/ai-interview/evaluation-db.env` 权限 0600、目录 0700。随后以该账号独立只读核验 MySQL UUID、固定 Schema 和精确授权，连接原业务库及测试库均被 1044 拒绝，未读取两库业务数据。
- 安全更正：服务器 `partial_revokes=OFF`；先前 U1-R1 报告的未转义 GRANT 示例可能匹配其他数据库，示例未被执行，现已明确加注并改正。依据 [MySQL GRANT 官方文档](https://dev.mysql.com/doc/refman/8.0/en/grant.html)。客户端来源由无默认 Schema 的非 root 只读连接实测，后续 Docker 网络变化时应重新验证。
- 只读核验一场经授权人工测试会话的归属、难度、报告状态及题答评价完整性；原始作答、快照、JSON 与关联 Chunk 当前正文保存在仅本人可读的 `backups/U1_20261114/historical-sample.json`。未改写旧会话，原答是否与用户实际输入完全一致仍待用户确认。
- 归因初判：集合题的记录未包含集合知识，不能据此认定已答内容被模型漏判；ACID 主答的一致性表达有部分方向，但未解释业务约束等机制，不能简单说完全未答或完全覆盖。旧题未明确要求隔离级别差异，旧反馈将其作为缺项；集合反馈把示例实现写得偏窄。ACID INITIAL 43、FINAL 15，Java 分别按五维相加；分差值得进一步评估，但不能仅凭一次记录断定具体因果。RAG 命中 ID 与当前 Chunk 内容可关联，历史正文无独立快照，不把时间戳当作严格逐字证明。
- 两题在无活动会话／评价／报告的窗口，经 `QuestionAdminService.updateQuestion` 外层事务更新并独立读回；集合 ID 85 新评分点 600–603，ACID ID 24 新评分点 604–608，权重不变。受保护记录摘要和 人工测试会话 历史比较通过，证据 `backups/U1_20261114/question-apply-{intent,receipt}.json` 均 0600。数据库提交不等于 Git 提交。
- 真实浏览器验收准备：临时回环测试进程固定仅限合成账号的两道题抽样索引，仍调用原选择器及正式快照流程；计时和首次成功答题响应丢失注入仅作用于本轮合成会话。当前测试进程已准备，完整浏览器交互、评分和报告结果待后续记录；不得将准备状态写成通过。
- 后续状态：[账号与人工反馈归因补充报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U1-R1-账号配置与人工反馈归因补充.md)。未登录真实 HTTP 检查 401／业务码 401；既有 D3 合成账号本地 HTTP 登录成功，但浏览器工具对读取到的凭据做了脱敏，GUI 未能使用实际值登录；不能归咎应用认证逻辑，也未绕过保护。未新建合成应用账号或 Session，账号创建确认待用户；本轮启动的测试进程已停止，准备证据保留。原常驻后端未切换，不能将独立测试进程的启动视为 v4 提示词和计时代码已在常驻进程生效。模型请求 DeepSeek 0、Embedding 0；真实计时样本 0，浏览器答题／失败恢复／报告验收未通过。

#### U1-R1 后续 — 合成账号与真实浏览器验收（2026-11-14）

- [最终报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U1-R1-真实浏览器终验.md)补充上述时点记录。用户授权后通过正式注册接口创建普通 USER 账号（user ID 32），凭据仅保存在 `backups/U1_R1_BROWSER_20261114/synthetic-credentials.json`，文件 0600、目录 0700。先查无活动用户工作，再在 evaluation 中创建合成 Session 31；人工 人工测试会话 始终只读。
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
- 实测前后 evaluation 13 表既有行摘要一致，题库、评分点、知识文档及 Chunk 无新增或修改，人工测试会话 不变；Milvus 只检索且 Strong count 前后 52，未做逐向量摘要。两次只读完成会话浏览器检查因工具提前匹配报告页时间线而失败，等待实际复盘路径后通过；一次端口复用失败改空闲端口，均无模型调用。
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
- 收口复核：2026-11-14 本地 HEAD `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，实时远端 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`；ahead 20、behind 0，暂存区为空。本轮九个本地提交依次为 `d090d25`、`f1fb02b`、`4bed0e2`、`04e86b1`、`b04976f`、`59667d9`、`447f676`、`3844ca4`、`84e3489`。五个工作区文件保留原稿或空行差异，未提交的原稿须先审查和脱敏；未推送。该收口快照留在工作区，已提交台账的文档提交 SHA 以 Git 日志为准。
- 运行状态：`127.0.0.1:18482` 的既有 Java 进程启动时间早于本次 `InterviewService` 修复；本轮未重启或替换该进程，也未对其真实用户数据发起验证。不能把 Git 提交和隔离测试通过当成该运行实例已修复；在安全窗口使用已审查构建更新并验证前，应按风险仍可能存在处理。

<a id="s1-r1"></a>

### S1-R1 — Evaluation 安全修复生效与真实验收（2026-11-14）

- 结论：**S1-R1 题目可见性安全门禁 GO**；[独立终验报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-S1-R1-题目接口安全修复部署与终验.md)。评分质量 NO-GO 不因本轮改变。用户确认所有真人已停止使用；切换前两次只读检查均无活动 Session、EVALUATING Answer 或 GENERATING Report。
- 部署：独立导出 HEAD `84e3489a85d0fb6c8b54ad2a130c52d7476fb897` 并离线 `-DskipTests package`；JAR SHA-256 `48db442bff8d22f0b1c50186cace3627e5183923c0b167b799b6d87202a7b63f`，源码 blob 与 HEAD 一致、JAR 字节码确认 S1 过滤。预验端口 18483 通过后，仅停止旧后端 PID 1165，新后端 PID 8362 使用该 JAR 监听 `127.0.0.1:18482`；前端 15174 保持运行并代理新后端。evaluation 专用非 root MySQL 与 Milvus 目标、SQL 自动初始化禁用、原 JWT 签名密钥均核验；未重启数据库或向量库。实际 JAR 和受限证据位于 `backups/S1_R1_DEPLOY_20261114/`。
- 真实 HTTP：新建 EASY 会话后仅当前 1 题可见，4 道 PENDING 不返回；匿名/非法令牌 401、普通用户管理接口 403、跨用户详情/报告 404。真实作答后已答题、答案、FINAL 反馈及当前题可见，剩余 3 道 PENDING 不返回。前端代理与后端详情一致，候选人响应不含内部快照字段；对未作答题的 16 个实际评分点正文作只读比对未发现泄露。
- 浏览器：通过现有 15174 页面注册合成用户、创建会话并提交一次合成回答；刷新和退出重新登录后继续面试，上一题答案及评价均可恢复。未保存浏览器 HAR，泄题结论依据实际代理/后端 HTTP 与数据库只读比对。5 道 MAIN 的题干、参考答案、评分点快照与题库一致，作答前后摘要一致；一次样本不证明随机分布。
- 外部调用：1 次答案 POST，应用日志各记录 1 次 Embedding 与 DeepSeek 阶段、HTTP 200；未独立采集供应商 HTTP 重试次数、token usage 或账单，不估算价格。本轮不做评分质量调参。
- 保护：evaluation 13 表原有 **753 行**逐行摘要全部不变；新增仅 2 个合成用户、1 个合成 Session、5 道面试题、1 答案、1 评价、1 RAG Batch、5 命中。合成 Session 有意留在第 2 题 `IN_PROGRESS` 供复核。Milvus Collection 身份/维度核验及 Strong count 52，与 U2 历史数字一致；本轮未做切换前逐向量摘要。未修改受保护历史行。
- Git：最终只读远端 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，本地 ahead/behind **20/0**；20 个本地提交 connectivity 检查通过，暂存区为空。本轮无新提交或推送，原 5 个未暂存文件及嵌套仓库保留，本条及学习项仅追加到其中两份原台账。既有远端已跟踪的 8 份前端日志未更改；常见敏感模式未命中，不把忽略规则说成已跟踪日志不存在。
- 学习关联：[L-S1R1-01～03](SOURCE_LEARNING_BACKLOG.md#l-s1r1-01)，均待学习、掌握未确认。未进行本轮全量测试、整场报告、真实追问或性能回归。


<a id="q1"></a>

### Q1 — 真实评分与多候选人诊断（2026-11-14）

- 状态：**三场真实合成 EASY 面试和报告完成；评分质量 NO-GO，人工盲审未完成**。[测试计划、样本与盲卡](../evaluation/q1-20261114/PLAN_AND_SCOPE.md)和用户指定 Reports 目录的独立诊断保留证据。14 条固定合成样本在模型调用前冻结；18 次真实评分包含 4 组有/无历史 RAG 成对对照，18/18 HTTP 200、Java 结构校验及总分求和通过。作者接触过评分标准，预期非人工 gold；E04 历史证据对应旧查询，配对没有实时检索，不能推断 RAG 因果收益。
- 重要发现：真实业务 MySQL LEFT JOIN 样本中，模型对会丢失仅有未支付订单用户的 WHERE OR 变体错误认可，字面量只读 SQL 反例得到 ON `1,2`、WHERE `2`；真实业务接口题追问 correction 把 Java 9 才支持的接口 private 方法归为 Java 8。M05 将已写出的 `removeIf` 漏判为未覆盖，A02 列题外例子要求，H01 有证据臂把已答风险写成遗漏；准确率和争议仍待独立盲审。调用链为 `EvaluationPromptBuilder → DeepSeekEvaluationClient → LlmEvaluationValidator → EvaluationScoreCalculator → FollowUpPolicy`；本轮未改生产策略。
- 业务验证：独立 evaluation 服务经正式 HTTP 注册、登录、随机组卷、作答、追问、完成、生成/读取报告；A/B/C 分别 8/7/5 答案、5/5/5 MAIN、3/2/0 追问、各一份 READY 报告。只读核对 20/20 答案、requestId、五维、总分、阶段及动作，20/20 各有 1 RAG Batch 与 5 Hit；各 Session 5 条 FINAL、无重复追问。匿名 401、普通用户管理接口 403，三账号双向详情和报告 12/12 为 404。候选与评分均用 DeepSeek，同源偏差存在。
- 用量与失败：DeepSeek 62 次尝试，61 次 HTTP 200 且有 usage、1 次候选生成 `URLError` 无响应与 usage；Embedding 20 次尝试、20 次 HTTP 200 且有 usage。已知空闲价估算合计 ¥0.21424402，未知请求未计入；为其额外预留 ¥0.0216 仅作执行器保护。停止后对该候选题进行了有界的单次恢复，旧未知记录保留、未重发业务答案。首轮快照预检因主键假设错误在任何业务写入和模型调用前停止；修正后成功。未把网络异常当作已证实的代码故障。
- 数据与验证：13 表 769 条既有行摘要不变；新增 3 用户、3 Session、20 题目、20 答案/评价/批次、100 Hit、3 报告、6 弱项。Milvus count 前后 52，未做逐向量比较。固定 18 次和业务 20 次五维总分一致；另完成 12 次跨用户请求检查与 SQL 反例；未执行本轮全量 Maven。新文件为 `docs/evaluation/q1-20261114/` 及 `tools/q1/`，私有过程证据保存在 `backups/Q1_20261114_scoring/`、`backups/Q1_20261114_http_2/`、`backups/Q1_20261114_resume/`。全工作区 `git diff --check` 有入场既存 HARD_AUTHOR_NOTES 末尾空行告警，未修改该文件。
- Git：本地 HEAD `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，本轮无提交、无推送，远端未实时核验，原五个未暂存文件保留；本批文件和台账变动未暂存。学习项 [L-Q1-01～03](SOURCE_LEARNING_BACKLOG.md#l-q1-01) 均待学习、掌握未确认。

<a id="q1-r1"></a>

### Q1-R1 — 真实评分错误归因与首轮盲审准备（2026-11-14）

- 状态：**诊断与盲审准备完成；人工审核未开始，评分质量 NO-GO**。原 34 张卡与原索引保留，风险分层挑选 12 张并重新编号，盲审材料不含系统评分、角色和解盲答案；选择索引与分析分别保存在 `docs/evaluation/q1-r1-20261114/blind/` 和 `analysis/`。12 张为选择性样本，不用于推断总体准确率。
- 真实归因：只读回查 5 条指定 Q1 合成答案/评价快照及持久化 RAG 命中。LEFT JOIN 回答的四个评分点确有正确内容，模型错误在未纠正额外 WHERE OR 变体并反向归因；参考答案与 rank 1 知识段落已说明同类丢行，不可将模型错误直接归咎于它们。接口 private 方法版本纠正错误；Java 21 正文正确但未提供引入年份，本题不要求年份，是否扩写是候选而非已确认的知识缺陷。接口复合评分点 96 的 `covered=true` 与证据/遗漏项冲突；粒度调整待出题人复核。M05 已答 `removeIf` 仍被点 449 判未覆盖，模型建议追问但 Java 离线结果为 NEXT_MAIN；另记录 H01、A02、M02、真实 ACID 与无关 Redis 命中，均区分已观测问题和不可确定的因果。[矩阵](../evaluation/q1-r1-20261114/analysis/CAUSE_MATRIX.md)。
- 真实验证：LEFT JOIN 既有字面量 SQL 只读执行 2/2，ON 得 `1,2`、WHERE OR 得 `2`；Temurin 21 `javac --release 8` 对接口 private 方法按预期失败（1 个错误、3 个选项过时警告），`--release 9` 成功。盲卡 12 张题答最终 12/12 与原卡核验、原卡及索引 SHA 不变；检查脚本曾因换行处理和摘要抄录问题两次断言失败，修正检查逻辑后通过，原卡未改。模型及 Embedding 调用 0，Q1 遗留 1 次未知 usage 未被改写。没有执行模型回归或全量 Maven。
- 实际版本：本地 HEAD `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`；现场运行后端仍为 evaluation 指定 MySQL/Milvus、`deepseek-flash`。当前 9 个相关 class 与 Q1 JAR 字节一致；仅其中 4 个有 Q1 当时启动前检查，不能夸大其他历史运行时证明。生产代码、题库、RAG 和数据库结构未改。
- 建议：先人工独立审查 12 卡，再解盲并明确争议；错误原因与评分点设计分别裁决。Q2 的 LEFT JOIN、版本、等价答案和遗漏回归及未见验收门槛为[设计草案](../evaluation/q1-r1-20261114/analysis/REGRESSION_AND_Q2.md)，未实施、未自动重跑，不用单次模型成功声称永久修复。
- Git：本批无提交 SHA、未暂存、未提交或推送；远端未实时核验，入场未提交内容保留。新增[报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-Q1-R1-真实评分错误归因与盲审准备.md)与项目评审目录，私有只读快照在 `backups/Q1_R1_20261114/`。学习项 [L-Q1R1-01～03](SOURCE_LEARNING_BACKLOG.md#l-q1r1-01) 均待学习，掌握未确认。

### Q1-R2 — 人工总体评价解盲与 Q2 准入（2026-11-14）

- 入场盲卡 SHA-256 `67195695fce56e5176473d400a8df27652fef9191f5c0ddc80d6db7cb063952e`，备份 `docs/evaluation/q1-r2-20261114/ROUND1_USER_ORIGINAL.md` 与原件一致，未补填人工栏目。过程偏差：备份前并行读取了解盲索引；保留此记录，未修改原稿。用户仅提供总体意见，未作为逐点金标准。
- 12 卡逐一关联 Q1 原始模型逐点评价、五维、Java 总分、建议与决策，见 `docs/evaluation/q1-r2-20261114/UNBLIND_REPORT.md` 和 `UNBLIND_DATA.json`。按独立工程口径 7/12 卡有可确认输出问题；其中错误认可 1 卡、正确内容误列遗漏/未覆盖 3 卡（含一条文字误报）、反馈事实错误 1 卡，类别重叠，人工尚未逐点裁决，不能推断总体准确率。
- 卡 01 未写名称但表达四项含义；卡 07 一致性已答，但隔离性绝对化仍有风险；卡 09 的事务用途是否完整覆盖待用户确认。Q2 优先级、独立留出与三个争议见 `docs/evaluation/q1-r2-20261114/Q2_PLAN.md`。评分质量 NO-GO。
- 真实模型及 Embedding 调用 0；仅只读回查一条合成会话快照，未修改数据库、题库、RAG 或生产代码；未重跑 Maven。HEAD `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，本批无提交或推送、远端未实时核验。学习项待学习。

### Q2 — 评分质量修复候选未通过（2026-11-14）

- 用户对卡 01、09、03 的技术复核已单独记录于 `docs/evaluation/q2-20261114/USER_TECHNICAL_REVIEW.md`；原始盲卡与初评未改。评分质量 **NO-GO，修复未完成**。
- 候选 v5 仅细化提示中的名称/含义、复合点子项、等价方案和额外事实错误；真实旧样本 18 次 HTTP 200，17 次 Java 校验通过、1 次 JSON 解析错误。A04 工作单元单次改善，但 A02 一致性出现退化，M05 `removeIf` 仍被漏认；不将 200 或单次分数视作质量通过。候选已从生产源码撤回并以 `CANDIDATE_V5.patch` 保留，完整比较见 `docs/evaluation/q2-20261114/DIAGNOSTIC.md`。
- 验证：修改前定向 **43/0/0/0**、候选 **44/0/0/0**、恢复后 **43/0/0/0**。扩展测试首次 **87/0/20/0**，Mockito 自附加失败；显式 Mockito agent 后 **87/0/0/0**。未执行全量 Maven或独立留出验证。
- 实际 DeepSeek 18 次尝试、18 HTTP 200、18 usage，输入 63577、输出 10523 tokens，空闲价估算 ¥0.08208628；Embedding 0。证据保存在 `backups/Q2_20261114_scoring/`，未重试解析失败，未写业务数据库。
- HEAD `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`；本轮未提交、未推送，远端未实时核验。生产代码恢复入场状态；新增 Q2 文档和证据、台账未暂存。尚未建立独立盲审留出集，不能称验收完成。

### Q2-R1 — 评分契约审计与离线原型（2026-11-14）

- 状态：审计和设计完成，评分质量仍 **NO-GO**；未实现评分契约、题库或生产代码变更。独立报告：[评分契约审计与离线验证](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-Q2-R1-评分契约审计与离线验证.md)。新增 `tools/q2/q2_r1_offline_contract.py`，仅以手工输入的子项布尔值演示 ALL/ANY、二值映射、文字证据来源和 SQLite 内存反例，不判断模型准确率。
- 主要证据链：`EvaluationContextService.buildContext → EvaluationPromptBuilder → DeepSeekEvaluationClient → LlmEvaluationValidator.validate → EvaluationScoreCalculator.calculate → FollowUpPolicy.decide → FollowUpTargetResolver.resolve → 工作流持久化`。现有校验未核对 evidence 与原答或 missingPoints 的语义；五维由模型独立给分、Java 求和，不能从结构成功断言事实正确。历史回放依赖二值 JSON；旧 false 无法区分部分与完全未答。
- 取舍：建议先做少量标准人工裁决及 B 的纯离线子项原型；A 可缓解标准含混但不保证模型判断，C 可表达部分状态但会增加兼容与恢复成本。此为候选，不授权生产实施，不假定需改表或无需改表。LEFT JOIN 额外错误须独立事实核查，不能只改覆盖点。
- JSON 失败复核：Q2 第 18 次 H02-RAG 使用 v5，HTTP 200、`stop`、输出 702/4096 token；内容在第 1 行第 1319 列语法错误，不能据此认定截断或字段缺失。客户端已启用 `json_object` 并拒绝非法 JSON；未修补、重试或输出响应正文。
- 本轮验证：离线 Python **9/0/0/0**；现有 DeepSeek 客户端、失败策略及日志安全 Mock HTTP/MockMvc 定向测试 **98/0/0/0**。无模型或 Embedding 调用、费用为零，未执行独立盲测、真实外部服务或完整 Maven 回归。新造样本为待人工审核的开发样本，不是盲测 gold；原盲卡备份 SHA 仍为 `67195695fce56e5176473d400a8df27652fef9191f5c0ddc80d6db7cb063952e`。
- 版本与 Git：源码 v4 与 HEAD 一致；历史部署记录的 JAR class 为 v4，PID 8362 正监听 18482，但 `jcmd` 被拒，不能独立验证该进程加载 JAR。HEAD `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，缓存 origin/main 同 SHA，远端未实时核验；本批无提交 SHA、未暂存、未提交、未推送；保留入场改动。新增学习项均待学习，掌握未确认。

### Q2-R2 — 评分口径候选及有界对照受阻（2026-11-14）

- 状态：**未完成模型对照，候选不可应用，保留 v4 及现行题库**。对照与精确 diff 见 [COMPARISON_SUMMARY.md](../evaluation/q2-20261114/q2-r2/COMPARISON_SUMMARY.md)，候选见 [CANDIDATE_RULES.json](../evaluation/q2-20261114/q2-r2/CANDIDATE_RULES.json)。生产 Prompt、Schema、评分契约、追问政策、服务和 RAG 均未修改；不升级三态，因为本批仅检验低成本文字澄清，其收益尚未证明。
- 范围：先冻结 4 条未经人工盲审的留出样本，再只生成一个候选，针对 ACID 五点、ArrayList 一点与 Spring 事务传播的复合风险一点，只追加解释，不调整点数、权重及类型。552 的 ALL/OR 口径未决，候选含“待出题人确认”工作文字，不能原样写入生产。LEFT JOIN 和接口版本仅保留既有事实反向控制，本轮无模型对照。`QuestionAdminService.updateQuestion` 全量删除重建点，未来应用不可假设 ID 不变；评分点亦参与 `EvaluationRagQueryBuilder`，固定证据对照不能证明检索无变化。
- 调用前预算：固定 v4、deepseek-flash、原有历史证据，40 次计划按高峰未命中和最大输出估约 ¥3，故 ¥2 护栏内先执行每样本第一对后重复。实际第一条 A02 旧规则即发生 `ExternalServiceException`；共 **1 次尝试、0 个已记录 HTTP 响应、0 个 usage、0 个有效结果**，未知费用按 ¥0.071730 保守预留后依计划停止，未重试。后续独立 DNS 查询报 `gaierror` errno 8；不足以断言该请求的精确根因。Embedding 0、业务写入 0。[私有计划及账本](../../backups/Q2_R2_20261114/)。
- 验证：两个工具 `javac -proc:none` 编译通过；7 条旧新规则的身份、权重、类型、哈希和文本上限及 4 条留出唯一性、40 项计划/证据数量通过定向一致性检查；离线 `verify` 重建 40 个计划 Prompt，校验除声明的评分点文本外均相同，没有发出请求；已有 Python 离线原型 **9/0/0/0**。未执行本轮完整 Maven、真实模型比较或独立人工盲审，不能推断准确率变化。
- Git：本地 HEAD 与缓存 origin/main 均 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，远端未实时核验；本批无 commit SHA、未暂存、未提交、未推送。保留入场所有改动及盲卡；源码 v4 与 HEAD 一致，运行进程版本未核实且未操作。学习项待学习，掌握未确认。

### Q2-R2 收口 — 用户确认 552 的 OR 口径，网络预检 BLOCKED（2026-11-14）

- 用户明确评分点 552：准确解释至少一种与所选事务传播方案相关的风险及其合理机制即可，不强制穷举风险；“有风险”不足以覆盖。读取的题目 115 原文是“指出独立事务额外连接和依赖外层未提交数据或外层持锁的风险，给出相应限制。”保留独立事务及相应限制要求；OR 是用户确认的产品评分决策，不把历史原文伪说成无歧义。新 [OR 候选](../evaluation/q2-20261114/q2-r2/CANDIDATE_552_OR.json) 清除工作性待确认文字、保留其余六点，未上线；[对照总结追加记录](../evaluation/q2-20261114/q2-r2/COMPARISON_SUMMARY.md)。
- 不付费预检：当前执行环境对 DeepSeek 域名及另一域名的 DNS 均 `gaierror` errno 8；无凭据 HTTPS HEAD 的 curl 退出 6，HTTP 000；受保护配置可读，模型与目标符合预期且凭据非空。不能推断整机网络或供应商不可用；依明确停止条件，本轮 DeepSeek、Embedding 调用及数据库写入均 0。旧一次尝试无响应头、供应商请求 ID 或 usage，未获得独立账单证据，实际费用仍未知，保留 ¥0.071730 预算预留且不重发。
- 验证：离线逐字段核对用户 OR 口径、原始 552 正文、其他六点及权重类型，核对候选和四条原留出样本哈希；不代表模型效果。留出样本未独立人工审核，错误认可、漏判、追问、JSON 和五维结果均尚无新对照；v4、JSON、评分算法、追问、Schema、生产题库和服务未改。未来必须单独冻结新候选的实验计划，并核验 Service 更新及 RAG 查询变化。
- 工程案例：[ENGINEERING_CASEBOOK.md](ENGINEERING_CASEBOOK.md)；学习条目待学习，掌握未确认。HEAD `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，缓存 origin/main 同 SHA，远端未实时核验；本批无提交 SHA、未暂存、未提交、未推送。

<a id="m1-a"></a>
### M1-A — RocketMQ 异步报告最小架构设计（2026-11-14）

- **状态：设计/源码审查；未编码、编译、测试、迁移、提交或推送。** [详细方案及故障矩阵](2026-11-14-M1A-ROCKETMQ-ASYNC-REPORT-DESIGN.md)。本批源码/Mapper/Schema/前端均未修改；DeepSeek、Embedding 调用均为 0。未改变 v4、Q2 未通过候选或评分质量 NO-GO。
- **链路与问题：**`InterviewController` → `InterviewReportService` 完成归属、COMPLETED、CAS、FINAL 聚合 → `InterviewReportGenerator` 事务外生成 → `InterviewReportTransactionService` 事务写 Report/Weakness/READY。旧的 Session `updated_at` stale 含排队时间；直接监听原同步方法既不能防止重复消费，也可能误接管排队任务。
- **决定与代价：**按本任务引入 MQ 的目标，推荐一张任务表兼 Outbox、申请与入队原子提交、消费者二次 CAS、独立排队/执行截止及恢复扫描；明确普通双写和事务消息的失败窗口。单体数据库任务线程更轻，未有负载证据证明 MQ 对吞吐的收益。新增 Schema 尚未授权实施；实际 DDL、回滚和遗留 GENERATING 需另审。
- **证据/验证：**本轮源码和 Schema 快照只读审查、U1/U2/B3/B4-E 历史记录、Apache 官方文档和 Docker 只读资源检查；功能测试/构建/真实 MQ/在线 Schema 验证均未运行，T/F/E/S 不适用。历史浏览器答题 n=4 P50 3.105 s；报告 READY n=1 3.147 s，不能当性能分布。候选 RocketMQ 5.3.2 + Spring starter 2.3.6；Boot 3.5/Java 21 兼容性未验证。
- **Git：**本地 HEAD `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`；本地缓存 `origin/main` 同 SHA，远端未实时核验。入场本台账、学习清单已修改，案例库未跟踪；本批仅追加它们，新增设计和简历证据文档，均未暂存、无本批提交 SHA、未推送。学习项 L-M1A-01～03 均待学习，掌握未确认。

#### M1-A 最小实施清单和隔离 MQ 探针补充（2026-11-14）

- [原 M1-A 文档第 8～9 节](2026-11-14-M1A-ROCKETMQ-ASYNC-REPORT-DESIGN.md)追加文件/方法、双进度及 task/version fencing、ACK 边界、时限候选、待批准 DDL 与索引、迁移/应用回退及四类故障注入。单体 + 单 Topic/group/table，保留 Outbox、执行 CAS、事务和有界扫描；不引入事务消息、CDC、Redis、分布式锁或推送。
- **实测范围：**独立临时 Maven 应用使用 Java 21.0.13、Boot 3.5.16、RocketMQ Spring Starter 2.3.6 和隔离 ARM64 Broker 5.3.2，依赖树中的 RocketMQ client 5.3.2；编译 package 退出 0，测试跳过、T/F/E/S 不适用；实际 1 次普通消息发送 SEND_OK、1 次监听器收到、探针退出 0。Maven 提示 javassist 3.21.0-GA effective model 告警，完整项目待排查。未验证业务事务/持久 ACK/死信/故障注入，不宣称生产兼容。
- **隔离与状态：**专属临时目录 `/private/tmp/ai-interview-m1a-swJBsI` 保留源码及收发/依赖树日志，专属两个容器和网络已移除；镜像缓存保留，没有停其他服务、连 evaluation、执行 DDL、改变账号权限或调用 DeepSeek/Embedding。生产实现、数据库迁移、Git 暂存/提交/推送均未执行；本批无提交 SHA。DDL 和目标仍须独立批准。学习 L-M1A-04 待学习、掌握未确认。

<a id="m1-b"></a>

### M1-B — RocketMQ 异步报告实施与分层验收（2026-11-14）

- **状态：已编码、编译、打包、Unit/Mock、真实 MySQL 和独立 RocketMQ 验证；未部署正式服务、未暂存/提交/推送。** [完整验收及逐文件清单](2026-11-14-M1B-ROCKETMQ-ASYNC-REPORT-VERIFICATION.md)、[数据库准入](2026-11-14-M1B-DATABASE-ADMISSION.md)、[运行/回退手册](../../tools/m1b/README.md)。保留 M1-A 设计及所有原验收，不重写历史状态。
- **问题/链路：**报告 POST 原同步等待模型；改为 `generateReportForApi → acceptReportTask` 同事务 Session CAS + QUEUED/PENDING，`ReportOutboxDispatcher → Producer → ReportMessageConsumer → claimTaskExecution → generateClaimedReport → completeReportGeneration`。复用 FINAL Reader、Report Mapper、Weakness Service，最终 Report/Weakness/READY/SUCCEEDED 一起提交。删除 `reclaimStaleGenerating` 和 Service 同步旁路，前端 GENERATING 操作只 GET。
- **改动/取舍：**新增 Task 实体、Mapper/XML、单表手工迁移及 async 包九个小类；短事务固定 Session→Task 锁序、两阶段 version、Long 读取无符号版本并防 Integer 溢出、token 租约、独立排队/执行截止、有限批量与退避。使用 native RocketMQ client 5.3.2 显式生命周期，使 MQ 故障不阻止 DB 扫描；默认关闭 MQ，无同步 fallback。只接受持久结果或 DB 证明的重复后 ACK，数据库/所有权不确定交 MQ 重试。增加 Broker/任务/监控及上线排空成本，不证明吞吐收益。
- **精确授权：**用户仅批准 `127.0.0.1:3308/ai_interview_test` 的 `20261114_interview_report_task.sql` 和本轮测试数据写入清理；只执行该 CREATE TABLE，未扩权、新建 Schema、访问 evaluation 或操作其他项目容器。迁移前备份和权限见准入记录。真实测试仅清理本轮资源，最终 14 表精确计数均 0；表保留。独立 MQ 项目/网络 `ai-interview-m1b`，loopback 19876/20911，唯一业务 Topic/group；验证后仅 stop，保留消息/容器/网络。
- **实际测试 T/F/E/S：**首批 Unit 77/0/0/0，真实 MySQL 45/0/0/0；真实 MQ 初轮 2/0/0/0、Broker 停机 1/0/0/0、DLQ/重放 2/0/0/0（模型替身）。全量首轮 1920/2/1/22，修正测试 stub 并限制 test Hikari 连接后 1920/0/0/22；新增 SQL 回滚注入的代理误用导致 1923/1/0/23，定向修正 12/0/0/0，**最终 1923/0/0/23**。前端旧 POST 契约 56/23/0/0→新 GET 契约 51/0/0/0，完整 **341/0/0/0**、typecheck/lint/build 均退出 0。DLQ 首探针解析错导致 2/0/2/0 后修正；所有失败日志保留，不将 Mock 当真实故障或跳过当通过。打包在完整测试通过后不重复执行测试，退出 0。
- **未验证/风险：**没有实际进程 kill、线缆层回执/消费 ACK 丢失、共享 DB 停机或提交不确定实测；暂停旧执行者/期限注入/真实重复 MQ 的证据强度已分别说明。真实 DeepSeek/Embedding 调用 0，无性能结论。30 秒租约/5 秒发送/600 秒期限/20 次上限尚无长期压测；DB/扫描器不可用不能保证按时收口。DLQ 不等于业务 FAILED；历史报告恢复仍需人工审计弱项。完整依赖树已核查，javassist 3.21.0-GA 的旧 tools.jar effective-model 告警未消失，未屏蔽或降低安全检查。
- **Git/保护：**本地 HEAD `84e3489a85d0fb6c8b54ad2a130c52d7476fb897` 不变，GitHub 连接器本轮实时核对远端 main 同 SHA。本批无提交 SHA、未暂存/提交/推送。入场 50 个未提交文件在追加工程记录前逐一 SHA 不变，三份工程记录仅追加；原 HARD_AUTHOR_NOTES EOF 空行告警保留。学习 [L-M1B-01～05](SOURCE_LEARNING_BACKLOG.md#l-m1b-01) 待学习，掌握未确认。

<a id="m1-c"></a>
### M1-C — 异步报告验收与 A/B 首轮（2026-11-15）

- **首轮状态与结论：**M1-B 未提交实现完成首轮功能、部分真实进程故障和 A/B；整体 **NO-GO**：真实模型出现无证据的具体判断，RUNNING 任务崩溃不会自动重新生成，默认配置下完整 READY 和完成吞吐显著退化。[方案/环境](2026-11-15-M1C-TEST-PLAN-ENVIRONMENT.md)、[故障与修复](2026-11-15-M1C-FAILURE-INJECTION.md)、[A/B 原始数据](m1c-20261114/AB_RESULTS.md)。截至首轮未修改生产 Java；后续所有权锁修复另记。未修改评分 v4、题库、RAG 或 Schema 设计。
- **链路与取舍：**`generateReportForApi → acceptReportTask → insertQueued → ReportOutboxDispatcher → ReportMqRuntime → claimTaskExecution → generateClaimedReport → completeReportGeneration → UserWeaknessService`；`useInterviewReport` 每 3 秒 GET。真实终止、Broker 停复、重发、两实例竞争、ACK 前终止及模型结果后终止有分层证据；两组/同组、测试替身/真实 API、直接触发扫描/自动调度明确区分。执行超时的新 attempt 需显式申请，不假称自动重试或模型恰好一次。
- **性能与资源：**1 秒/并发 10、每组 30 条：POST P95 A 1086.4 ms（READY）/B 22.1 ms（受理），READY P95 1086.4/7089.4 ms，闭环完成吞吐 7.206/1.476 次每秒。正式共 540 条测量加 54 条预热，错误/超时均 0；95 次资源样本，NameServer/Broker 内存中位数分别 173.5/523.7 MiB。B 默认两消费线程和额外事务，结果不可归因于 MQ 单一因素，也非生产容量。
- **实际验证 T/F/E/S：**报告定向 98/0/0/0，独立真实 MQ 2/0/0/0，旧执行者四交错 4/0/0/0；新增故障类在普通条件下 11/0/0/11；最终普通全量 **1934/0/0/34**（1900 通过、34 条跳过），前端报告 51/0/0/0、全量 341/0/0/0，typecheck/lint/build 退出 0；预算网关离线 4/0/0/0。实际 SIGKILL 的 Maven 非零是注入，不计 JUnit 通过。场景 5 首轮探针 1/0/1/0，修正后重测通过；首次桩、时区和编码错误均保留。
- **真实模型：**2 次 DeepSeek HTTP 200、2 份 usage，输入 901/输出 529 token，按高峰未缓存费率估算 $0.0009051，非账单；浏览器真实轮询与刷新、报告/弱项持久化完成，但自然语言存在证据外判断。Embedding 0；用户批准上限 2 次、$0.05，未进行真实模型压力测试。
- **Git/范围：**本地 HEAD `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`；远端 main 由 GitHub 连接器只读核实同 SHA（SSH 443 `publickey` 验证失败），未提交、未推送，M1-B 与 M1-C 均为工作区未提交内容。B 版本由 JAR 和源码散列确定而非 Git SHA；新隔离数据库及容器保留，未触及其他项目或历史库。首轮停测前无 QUEUED/RUNNING，专用 MQ 两容器 stop 后 exit 137 且非 OOM，后续重启并重测；不宣称优雅排空。学习项见 [L-M1C-01](SOURCE_LEARNING_BACKLOG.md#l-m1c-01)，掌握未确认。

#### M1-C 补充：先鉴权后锁行（2026-11-15）

- **复现与根因：**真实 MySQL 中持有目标 Session 行锁时，非所有者 POST 对应 Service 在 2 秒内未能拒绝；修复前 **1/0/1/0**，为 `TimeoutException`。`acceptReportTask` 原先先 `getReportVersionForUpdate`，之后才核对归属；可使无权请求等待其他用户的行锁，未证明数据泄露或压力攻击规模。
- **首次修复（历史，随后发现缺陷）：**`InterviewReportTransactionService.acceptReportTask` 先通过现有 `getReportSession` 普通读校验所有权，获得锁后再检查；不改表或消息协议。定向 **60/0/0/0**、普通全量 **1937/0/0/34**，五组真实进程故障重跑满足各自断言。之后发现同一 Mapper 语句在事务内可能命中 MyBatis 一级缓存，不能将此次修复当成最终正确方案。
- **最终修复及版本边界：**真实 MySQL 测试在锁等待期间改变合成会话归属，首次修复未拒绝旧所有者（**1/1/0/0**）；最终将锁前预读改为独立 `getReportOwnerId`，锁后保留另一 Mapper 语句查询完整 Session 并重检。真实 MySQL/单元/API 定向 **75/0/0/0**，最终普通后端全量 **1938/0/0/34**（1904 通过）；最终修复源码的五组进程级故障重跑均符合各自断言，场景 1 的恢复阶段启动 B JAR `f3b6095d5ed9cc465dfd8f000a1ea658a08c771576e19e8817e134de427e5334`，其他由 Surefire 测试进程及替身触发。首次修复版 `8b59ecbf...` 的性能试验中止，已完成的 183 条记录仅作历史保留。首轮 A/B 和 2 次真实 DeepSeek 均是更早 JAR `cf1a199e...` 的证据；真实模型预算次数已用完，未对最终 JAR 再调用付费模型。无本批提交 SHA、未推送；个人掌握仍未确认。

#### M1-C 补充：最终 JAR 的 HTTP A/B（2026-11-15）

- **性能：**[最终完整数据](m1c-20261115-after-fix/AB_RESULTS.md)，本地模型桩 1/3/5 秒、并发 1/5/10，ABBA/BAAB，每组配置 30 测量 + 3 预热；540 测量均成功、错误/超时均 0，594 次桩调用及 297 对用户消息 Prompt 哈希一致，未逐条哈希模型完整请求。1 秒/并发 10 时，POST P95 A/B **1046.5/24.9 ms**（不同完成语义），READY P95 **1046.5/6967.8 ms**，闭环完成 **7.252/1.489 次每秒**。九组 B 完整 READY P95 和完成吞吐均劣于 A，不将受理改善称作生成或吞吐加速。
- **资源及限制：**96 次采样无采样错误，应用 A/B RSS 中位数 **241.0/337.4 MiB**、采样 CPU 最大 **10.3%/67.1%**、HTTP 非空闲线程观测最大 **10/3**、MySQL 连接数观测最大 **11/12**（含采样）；NameServer/Broker 内存中位数 **158.9/462.2 MiB**。双 JVM 同机、轮次跨采样且背景 Session 分别 619/633，不能把差值当纯 MQ 成本；同机其他服务未停。纯 MQ 投递时间无独立时点，待建立观测。
- **浏览器另测：**本地 Chrome 157 无头模式、当前 Vue 前端分别连接 A/B，3 秒页面轮询，独立 594 条（54 预热、540 测量）全部可见；每配置每组 30 条，用户消息 Prompt 成对相同。1 秒桩/最大并发 10 的可见 P95 **1356.7/9470.0 ms**，差 **+8113.4 ms（+598.0%）**；含自动化开销、不代表物理绘制，与 HTTP 样本不能合并。[逐条证据](m1c-20261115-browser/BROWSER_RESULTS.md)。

#### M1-C-R1 — 完整 READY 退化与恢复契约归因（2026-11-15）

- **状态：**[独立复核](M1C_R1_BOTTLENECK_REVIEW.md)及逐任务合成证据；未修改业务代码、DDL、评分 v4、题库或 RAG，未执行新模型桩/收费模型请求、完整 Maven/前端回归。旧实验中的“两个消费者”应理解为一个实例内两条监听/业务执行线程，不是两个实例或全局模型并发许可；历史表述和负面数据保留。
- **证据与调用链：**`benchmark.py` 闭环每个用户待 READY 后下一次申请；`ReportOutboxDispatcher` fixedDelay、单次有界扫描 → `ReportMqRuntime` 并发回调 → `claimTaskExecution` CAS → 模型调用 → `completeReportGeneration`。原始区间约束可在假设时钟稳定及原 JAR/源码一致的前提下反推 1 秒桩/并发 10 模型实际峰值 A=10、B=2；它不是直接计数器。并发 1 实际均为 1，B 逐任务 queued→started 中位数/P95 874.115/984.621 ms。原始数据不足以把该区间全部归因于扫描或纯 MQ；新机制 C 不能通过现有 B 最大 8 配置等同 A 实测 10，未通过单独限制 A 制造对照。
- **恢复与锁：**M1-A 草案选择 RUNNING 超时 FAILED 后显式重试，当前一致；独立产品批准记录未知。历史最终故障 5 以 5 秒和显式 `expireTask` 注入，旧任务失败、新任务 READY，两次模型替身调用、报告/弱项各 1；不代表自动 READY 或自然 600 秒扫描已测。锁路径先 owner 普通读、再 `FOR UPDATE`、不同 Mapper 锁后读且 READ_COMMITTED，再校验/CAS；`useCache=false` 不关闭一级缓存。历史真实 MySQL 相关定向 75/0/0/0、旧执行者四交错 4/0/0/0，均非本轮新执行。
- **范围/资源/版本：**仅重启 M1-C 专属 B MySQL 执行只读事务，导出 HTTP 与浏览器各 297 条合成任务时间，读取索引/EXPLAIN，随后停止且退出 0；其他容器、历史数据及卷未更改。开发头 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897` 加未提交工作区，最终 B JAR SHA `f3b6095d5ed9cc465dfd8f000a1ea658a08c771576e19e8817e134de427e5334`；无本批提交 SHA，未提交/推送，远端未实时复核。结果依原始实验和环境限制，不推断新性能收益。
- **遗留/取舍：**扫描占比、消费/数据库等待、最终 JAR 的真实模型质量、137 信号链和生产部署未验证；自动恢复与性能改动仅为备选方案。**总体 NO-GO**，不同维度详见报告。学习项 [L-M1C-R1-01～03](SOURCE_LEARNING_BACKLOG.md#l-m1c-r1-01) 均待学习、掌握未确认。


### M1-C-R2 — 异步报告多环节性能归因与两项配置调优

- **建立/更新日期、负责人：**2026-11-15，Codex；以入场166个已变动/未跟踪文件及历史1188条请求为证据基线。旧验收不改写。
- **关联报告/运行ID：**[正式归因与最小修复报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-M1-C-R2-RocketMQ多瓶颈归因与最小调优.md)，`backups/M1C_R2_20261115/{pilot-01,pilot-02,main,supplement,browser,browser-poll1000-retest,final-defaults}`；[可复算数据](m1c-r2-20261115/README.md)。A JAR 3fb09529…，入场B f3b6095d…，最终B 551d78688ecd01fafc16ef15d0e6fa1e745325e4220eb77156ebba789766eae8；完整散列在报告/manifest。
- **状态：**审查、测试探针、两项配置改动、编译、分层测试、打包和有限隔离验证已完成；未部署/暂存/提交/推送。最终JAR不含测试探针。
- **实际问题/归因：**原B仅2个实际模型执行线程，高并发PULLED→回调平均2313.3ms；提交→派发平均1069.7ms，模型HTTP约1005.5ms。2/4/8配置实际计数吻合；发送确认约毫秒。1队列及pullThreshold=8导致消费缓存可达9并触发背压，不能将长拉取全部归于网络。池/锁/GC没有本负载下的主要瓶颈证据，长负载与峰值栈仍待验证。
- **改动文件/关键点：**生产仅`ReportAsyncProperties.consumerThreads=4/scanIntervalMs=500`、`ReportOutboxDispatcher.dispatchDue/expireDue`注解fallback=500、`application.yaml`两个带环境覆盖的默认值。新增`tools/m1c-r2/`隔离计时/资源/浏览器/统计工具及数据目录；四份工程记录只追加。业务Service/事务/Mapper/安全和生产前端均保留入场内容。
- **调用链/边界：**POST→owner普通预查→锁Session并独立复查→FINAL→CAS+Outbox同事务→周期派发/发送租约→并发回调→执行CAS→事务外模型→Report/Weakness/READY/SUCCEEDED同事务。T1/T6/T9来自afterCommit，T0～T9用同JVM单调时钟；GET/DOM另行校时，不拿updated_at当提交。
- **方案/替代/代价：**默认4线程而非8，兼顾实测收益和未经验证的收费供应商配额；500ms提高空扫描理论SQL频率2→8/秒。保留8/500及1秒前端轮询为候选；未增加提交后唤醒、扩大池、改ACK、重试或Schema。回退两个环境变量为2/2000并按既有停写/排空流程重启，不删除任务和消息。
- **结果：**新协议每组20条、1秒桩/并发10，新旧默认DB速率1.800→3.641（+102.3%），闭环完成1.775→3.531（+98.9%），客户端READY P95 6067.4→3563.6ms（−41.3%）；原30条两波指标保留，不能跨协议混用。固定4条/秒18任务原/新最大QUEUED10/3、最后到达后排空5.967/1.666秒。相同实测并发8时A/B仍为1032.5/1419.9ms均值，不能宣称MQ比同步快。
- **运行/测试计数：**2026-11-15，`python3 tools/m1c/run-tests.py --tests <报告专项>`为131/0/0/0（58 Unit/Mock、73真实MySQL），退出0、9.420秒；`--tests '*'`为1938/0/0/34，退出0、55.396秒；`--mq --tests 'RealReportRocketMqSmokeTest#committedOutboxSurvivesNoSendAndDuplicateDeliveryDoesNotRepeatWeakness+brokerAcceptedBeforeSentRecordCannotRevertCompletedTask'`为2/0/0/0，退出0、8.474秒。普通全量清除RUN_REAL/MILVUS开关，34跳过不是外部服务通过。既有迁移回归只在连接内临时表运行历史DDL，不改持久Schema。package退出0，1.286秒，计数不适用。
- **类型/数量：**240条性能测量（含12浏览器）业务成功240，预热34尝试/33成功；固定loopback桩273调用、付费DeepSeek/Embedding0；真实MySQL/MQ不等于真实模型。最终默认值18条突发16+2批次、实际并发4、报告/弱项一次，活动任务0。前端全量、真实模型质量、生产故障/部署未执行。
- **失败/错误/跳过：**pilot-01探针ParamMap错误引起1次预热500、模型0；修正后4测量通过。浏览器1秒覆盖未匹配3e3，六条未POST样本复用重测；六条完成后清理超时脚本退出1，后续仅零请求关闭检查退出0。失败原样保留，未弱化断言。没有费用授权阻塞。
- **资源/收尾：**Hikari等待采样0、主组InnoDB锁等待时间增量0；CPU/RSS/GC/容器值保留，不把RSS中位数相加成整机增量。结束offset1261/1261、积压0；四专属容器恢复停止，MySQL退出0、官方mqshutdown后MQ退出143且无OOM，未验证随后重启，不推断旧137原因已解决。
- **Git/保全：**本地HEAD和缓存origin/main为84e3489a85d0fb6c8b54ad2a130c52d7476fb897；远端未实时核验，祖先关系未新增验证。暂存区为空，无本批提交SHA，未提交/推送。除3个明确生产修改和4份追加文档外入场文件保留；原历史记录hash不变。
- **学习关联：**L-M1C-R2-01～04：afterCommit/时钟、真实并发/背压、负载口径/资源、浏览器观察/取舍。全部待学习/掌握未确认，没有开发者本人复现证据。
- **残余/下一步边界：**本批业务正确性GO；生产MQ可靠性和报告内容质量NO-GO，人工恢复契约仍不自动READY。保留为可靠异步工程实践；若优先简化MVP，再单独评估数据库Worker及前端轮询。停止追加性能实验，不启动下一轮。


### M1-D — 报告生成策略真实对照与最小轮询优化

- 建立/更新：2026-11-15；执行Codex，用户内容验收尚未完成。[正式报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-M1-D-报告生成优化与真实效果对照.md)、[证据索引](m1d-20261115/README.md)、[技术事实审核](m1d-20261115/QUALITY_AUDIT.md)。
- 状态：语义与架构设计完成；3种生成策略隔离原型已编码/编译/实际运行；正式生产仅前端轮询常量修改并通过回归/构建；精简/规则/唤醒未发布，无MQ直写仅离线设计/原型。未部署。
- 问题/根因：额外模型调用实际约2.9～5.4秒，Outbox与轮询再叠加等待；已有FINAL可用于零调用报告，但精选与综合分析能力有损。历史FINAL错误可传到报告，不能把文本引用当事实真值。
- 生产文件/方法：frontend/src/features/interview/composables/useInterviewReport.ts的REPORT_POLL_INTERVAL_MS 3000→1000；其余现有业务代码/SQL/YAML未改。tools/m1d新增CompactReportPromptBuilder、DeterministicReportGenerator、M1dExperiment及Python/浏览器有界工具，复用M1-C-R2 ReportProbe；不进入生产JAR。4份工程记录只追加。
- 调用链/边界：归属→FINAL校验→Session CAS+Outbox事务→MQ执行CAS→事务外Generator→Report+Weakness+READY+SUCCEEDED事务。保留唯一约束、期限与围栏；FINAL、Java总分、弱项只写一次不变。
- 对照与取舍：3场同FINAL下完整/精简v1供应商中位数4943.272/3535.680ms，C场精简反而慢；输入-6.2%、输出-24.8%但质量不等价。v2实际2次，1成功/1数组超限FAILED。规则经MQ生成器中位数0.658ms、READY247.378ms，仍丢部分重要条目与综合建议，不替换模型。8次费用估算$0.004268064，非账单；缓存使精简v1本批实际估算费用更高。
- 最小收益：规则报告3秒/1秒轮询各6条，浏览器中位数3108.600/1105.050ms；3.416秒模型耗时桩各2条6103.650/4097.800ms，GET2/4。最终源码无覆盖2条4093.450ms。理论长GENERATING的GET频率3倍，尚未做长期容量测试。4/8线程及即时唤醒仅有界候选，保留4/500默认。
- 测试命令/结果：普通./mvnw -B -ntp test首次1938/125/0/34退出1，修正本轮隔离题状态后同命令1938/0/0/34退出0；真实MQ两指定方法2/0/0/0退出0；npm run test:unit 341/0/0/0，build/lint退出0；最终OfflineChecks 96项断言通过（非JUnit），Java8/9编译反例符合预期。基线HEAD+原M1B/C工作区与本轮1行变更，MySQL3318/test，真实开关普通全量全部清除。[精确结果](m1d-20261115/test-results.json)。
- 类型区分：JUnit含Unit/Mock、Mock HTTP/MockMvc和真实MySQL；34个guarded真实服务跳过；专项2个真实MQ+MySQL但生成器替身；端到端8个真实DeepSeek、24个loopback模型调用、28个Java报告；61新Session共59READY/1FAILED/1未提交。55条完整计时、3预检、v2部分结果另列，不混为JUnit数。
- 失败/未执行：最初FOLLOW_UP夹具结构CHECK失败并回滚；首次全量125失败因295个新EASY报告夹具ENABLED却无评分点，修正为DISABLED且不改生产/测试校验后通过。v2列表超限保留FAILED与费用；worker异常未关闭stdin及超时参数位置已修正，缺失DOM不伪造；剩余付费计划停止。未重做完整进程kill/提交未知/长期Broker故障或真人盲审。
- Git：本地HEAD 84e3489a85d0fb6c8b54ad2a130c52d7476fb897；本批无commit SHA；生产增量1行、4记录追加、tools/m1d与docs/engineering/m1d-20261115新文件均未暂存；未commit/push。远端main本轮未实时核验，远端SHA/祖先关系未知；不推断发布。
- 学习：L-M1D-01～06，全部待学习/掌握未确认。保留既有M1-C/R2验收原件。
- 残余风险/准入：报告内容仍需处理上游事实错误和证据范围，并获得真人可用性验收；不发布精简或规则替代。保持完整DeepSeek+MQ为当前单路径；纯Java若日后足够再单独设计互斥切换，基础预览/增强状态只设计。无DDL、无正式部署，历史7表已有行散列保持，活动任务与MQ积压0，专属容器已停并保留数据。


### M1-E — 提交后有界派发提示与逐题结果提前可见

- **建立/更新：**2026-11-15 / Codex；[唯一主报告](m1e-20261115/DECISION_AND_VERIFICATION.md)、[测试索引](m1e-20261115/test-results.json)、[逐任务](m1e-20261115/EXPERIMENTS.csv)、[质量核对](m1e-20261115/quality-audit.json)。历史M1-A/B/C/R2/D证据保留。
- **实施状态：**P1/P3已设计、编码、编译、打包、单元/Mock与真实MySQL/MQ验证；12真实DeepSeek尝试完成11READY/1FAILED；实际浏览器12路径；独立验收运行完成。P2仅工具原型，P4/P5/P6未新增生产调整。不等于正式部署或内容发布。
- **问题/证据：**周期扫描带来额外Outbox等待；已有保存详情在READY前不可见。预先冻结基线完整报告+4线程+500ms+1秒轮询，测量实际提交、调用、浏览器绘制；排除“思考未关闭/客户端未复用”的错误假设。
- **正式文件/方法：**新ReportOutboxWakeup.onTaskAccepted，修改ReportAsyncConfiguration.Scheduling.reportWakeupExecutor、ReportAsyncProperties.wakeupEnabled、InterviewReportTransactionService.acceptReportTask发布事件、application.yaml开关；前端InterviewReportView.vue复用timeline/savedFinals、reportCopy来源文案。新增2测试类、改ReportUnitFixture与前端spec。无新Schema/API主版本/报告策略。
- **调用链/取舍：**Session+Outbox提交→可丢失提示→独立有界执行器→原Dispatcher租约CAS→MQ执行CAS→事务外Generator→原Report/Weakness/READY/SUCCEEDED事务。AtomicBoolean合并不忙循环；AbortPolicy拒绝只诊断，500ms扫描可靠兜底。相比在完成事务自动建报告，避免耦合答题成功及全用户付费消费。
- **实际测量：**1秒桩并发1各8，Outbox416.69→0.68ms、流水线1462.57→1040.46ms；并发8各8流水线2817.34→2820.29ms无改善，模型峰值4、队列4、Hikari等待采样0、锁等待增量0。真实6对Outbox243.77→0.86ms；5对双方成功的流水线配对中位收益45.25ms，不能称稳定提速。真实浏览器首次有用4.223→0.085秒（保存FINAL），完整页A更慢/C略快/B候选无READY。
- **测试命令/精确计数：**environment.py test调用./mvnw -B -ntp test；最终1947/0/0/34（1913执行通过），定向57/0/0/0，真实MQ两个指定方法2/0/0/0；前端npm run test:unit 346/0/0/0，定向56/0/0/0，typecheck/lint/build/package通过。普通全量清除RUN_REAL/MILVUS开关，34跳过不是外部服务通过。详见命令索引。
- **失败与修正：**首次定向57/0/4/0来自新增测试Qualifier缺失；首次全量1947/0/1/34来自新DB账号缺现有锁诊断表SELECT，修环境而不弱化测试。首轮48压测实际客户端2/9而标签1/8，保留探索样本、修工具补32准确样本。B候选真实返回7条优势/弱项被5项上限拒绝，FAILED保留且零报告/弱项；请求/生成器与基线相同，未改Prompt或重试。
- **数量/费用：**86正式Stub报告测量+2预检、22额外预热、4交互传输探针=114 loopback调用；一个预检409提前退出、0任务/模型。12真实报告/浏览器12总路径；12次usage已知、峰值1、0重跑、Embedding0。估算$0.004890084非高峰，预算折算约¥0.0489非账单。新增123会话：121READY/1FAILED/1未完成预检；122任务活动0。
- **环境/资源：**专属MySQL3321/test、MQ19876/30911、应用28082、前端28084，不连接正式/evaluation数据。原有8运行容器/15174与18482服务保持。收尾位点126/126、积压0，专属容器/应用关闭并保留数据，精确退出码见resource-closeout.json；MySQL容器0/OOM=false，docker exec关闭客户端137不等同容器OOM。未重做保护库行hash，写入边界已审计。
- **Git：**HEAD84e3489a85d0fb6c8b54ad2a130c52d7476fb897；无本批SHA、未暂存/commit/push、无正式部署；远端本轮未实时核验。相对入场patch及文件保全见证据，不用git对HEAD的大差异冒充本轮改动。
- **学习关联/准入：**L-M1E-01～04全部待学习、掌握未确认。局部调度/事务回归与P3体验通过；上游错误传播、范围扩大/空证据具体推断仍使内容发布NO-GO；完整MQ生产故障边界未新验收。完成本批交付，不追加大规模性能矩阵。


### M1-E 收口 — 同域状态查询合并与原响应归因（2026-11-15）

- **范围/状态：**仅 P1＋P3 最终核对；P1 后端不改。新增前端最小修复已编码、定向/全量前端验证、构建通过；结构失败用原响应做离线验证，未增加真实模型调用；只读隔离入口启动/重新登录读取后已关闭。并非新性能实验、生产部署或内容质量放行。
- **问题、文件与调用链：**`frontend/src/features/interview/composables/useInterviewReport.ts` 的 `recoverGenerating` 不再增加 epoch，`refreshReport → queryReportForScope` 复用同域 Promise，解决人工检查与定时 GET 重叠；路由/POST 仍保留 epoch 隔离。`frontend/tests/InterviewReportView.spec.ts` 补终态共享查询、FAILED 刷新/重新登录与跨用户拒绝。生成器测试类＋`src/test/resources/report/m1e-structure-failure-response.json` 保留 call003 原始字节；`tools/m1e/demo.py` 支持新前端独立哈希清单，不覆盖旧测量构建身份。
- **证据/取舍：**213 后端生产/配置文件匹配，770 历史证据哈希匹配；原 P1 5 单元＋4 真实 MySQL、真实 MQ 2、后端全量 1947/0/0/34 复用不重跑。离页停止新轮询并忽略旧响应，不增加传输 abort 或扩大为新调度策略。原真实失败 JSON 7/7/5、stop/876 tokens；离线原样重放第一异常为 strengths 超界，原当时日志只记录 GENERATION_FAILED，模型内部违约原因未证实。
- **本次命令/计数 T/F/E/S：**`npm run test:unit -- tests/InterviewReportView.spec.ts -t "shares an in-flight poll"` 59/3/0/56（修复前必要复现）；同文件完整运行 59/0/0/0；`npm run test:unit` 349/0/0/0（37 文件）；`npm run typecheck`、`npm run lint`、`npm run build` 成功。`./mvnw -o -B -ntp -Dtest=DeepSeekInterviewReportGeneratorTest ... test` 14/0/0/0（Mock HTTP，非外部服务）。完整命令、日志与 Surefire 实际归档位置见 [VERIFICATION.json](m1e-20261115/closeout/VERIFICATION.json)。
- **指标与风险：**原 1 秒桩 c1 流水线 **1.463→1.040 秒**，原 3 对浏览器首有用 **4.223→0.085 秒**是已保存逐题结果；完整报告时间未证实稳定下降。本次 0 新付费，旧 12 次及 $0.004890084 估算不变；内容发布 NO-GO、结构越界风险与完整生产 MQ 故障边界仍保留。
- **Git/运行/学习：**HEAD84e3489a85d0fb6c8b54ad2a130c52d7476fb897；无本次 commit SHA、暂存、提交、推送，远端未刷新。可进入 Git 整理；目标常驻切换/回滚未验收，本次只读入口已关闭，原服务 PID 不变，专属容器数据保留。L-M1E-05 新增，01～05 个人掌握均未确认；[唯一主报告](m1e-20261115/DECISION_AND_VERIFICATION.md) 与 [收口补丁](m1e-20261115/closeout/changes.patch)。


### ARCHIVE-01 — 旧对话摘要与工程证据归档（2026-11-15）

- **状态和范围：**文档归档；新建历史主档案、源码学习路线和面试案例库，四份长期记录仅追加索引。未修改业务源码、配置、测试或数据库，未删除原验收和历史失败记录。
- **问题和方法：**完整旧对话未提供，以用户摘要作为待核对陈述；只读核查本地 Git、44 个源码/配置/SQL 文件、26 个测试文件及阶段原报告，离线检查 D1、Q1、Q2 三份结构化数据。文件盘点不是逐行审计或测试运行。
- **调用链与取舍：**历史案例 [H01–H21](PROJECT_ENGINEERING_HISTORY_20261111_20261115.md) 覆盖 Workflow 领取与重放、FINAL、检索评分、Session/Outbox/MQ、最终事务及前端展示；历史失败、性能退化和不同构建的结果分列，不重复改写既有验收。
- **验证：**本轮未运行构建、单元/Mock/真实服务测试、浏览器、性能实验或付费模型；本轮 T/F/E/S 不适用，新增 DeepSeek/Embedding 调用 0。文内测试数字均明确为历史记录；链接、结构和入场未提交文件保全作离线检查。
- **Git：**入场本地 HEAD 及缓存 origin/main 为 84e3489a85d0fb6c8b54ad2a130c52d7476fb897；本轮无提交 SHA、未暂存/提交/推送，远端未实时核验。M1-B～E 工作区属于未提交状态，不把同一 HEAD 当其完整构建身份。
- **风险和学习：**评分及报告内容发布 NO-GO；模型调用非恰好一次、长期生产故障/性能和目标服务切换未完整验证。学习路线 15 个模块均待用户本人确认掌握；不授权后续开发或部署。


<a id="p2-mq-audit"></a>
### P2-MQ-AUDIT — RocketMQ 性能收益与工程价值审计（2026-11-15）

- **建立日期/范围/状态：**2026-11-15，Codex；二期第一项为源码与历史证据审计、最小公平实验方案，不是生产优化批次。完整[独立审计报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-RocketMQ性能收益与工程价值审计.md)。未修改生产代码、测试、配置或数据库，未运行迁移、部署或收费模型；本批无提交 SHA。
- **问题/定位：**异步 POST 更快并不代表报告 READY 更快。原 M1-C 的 1 秒桩/客户端并发 10、每组 30 条：POST P95 1046.5/24.9 ms（READY/受理），客户端 READY P95 1046.5/6967.8 ms，闭环完成 7.252/1.489 次/秒；实际模型峰值 10/2，不能将差异全部归因于 MQ。R2 等峰值 8/8、各 8 条的 T0→完成事务均值 1032.5/1419.9 ms，包含持久任务、验证和派发等差异，不是纯 MQ 网络成本。历史请求数据、失败和实验条件原样保留。
- **调用链/已确认瓶颈：**acceptReportTask 的 Session/Outbox 同事务 → AFTER_COMMIT 可丢失提示或 500 ms 扫描 → dispatchDue 租约与发送 → ReportMqRuntime 同步消费 → claimTaskExecution → generateClaimedReport（事务外模型）→ completeReportGeneration（Report、Weakness、READY、SUCCEEDED 同事务）→ 前端 1 秒状态轮询。R2 旧 2 线程下拉取到回调平均等 2313.3 ms；M1-E 最新 4 线程在客户端 c8 的模型峰值 4/队列峰值 4。提交后提示的 c1 桩中位等待 416.69→0.68 ms，但 c8 流水线中位数 2817.34→2820.29 ms 无改善，不能再把已实施 P1 当本轮新增优化。
- **数据库与资源边界：**R2 采样 Hikari 等待、InnoDB 行锁增量和 data_lock_waits 为 0，仅表示该短样本未观察到等待；历史每任务独立用户，不能排除同用户 Weakness 热点。发送/重试、长积压、多实例重平衡和供应商限流缺少生产证据。历史资源样本非严格独立增量。
- **价值/代价/备选：**持久受理、后台处理、CAS 及部分重复投递/进程故障恢复有历史隔离证据，但部分价值来自 MySQL 事务而非 MQ 独占；Broker、双进度、线程、监控和运维有成本。条件保留现有实现并评价数据库持久任务＋有界本地执行（仅方案），不承诺零丢失、分布式原子或模型恰好一次；RUNNING 模型后崩溃仍可能需显式重试。
- **候选及取舍：**仅提出隔离环境评估线程 4→8，及在新版本复现超过 16 条的派发尾部后评估有限补唤醒；真实供应商并发限制、数据库热点与额外扫描成本均待证。公平方案为同步 A / 当前异步 B，共同固定模型桩许可 K=4、客户端并发 1/4/8、三轮各 20 条及每轮两条预热；先冻结身份和隔离目标，保留失败及各阶段时间。共 360 次测量＋36 预热均未执行，扩展范围须另行批准；K=8 和大于16条突发另列。
- **本轮离线验证：**原始 M1-C c10 的 A/B 各 30 条成功、0 失败，按原公式复算分位数和完成速率匹配；R2 原始 14/14 波、224 条明细重算的峰值/错误/吞吐匹配；M1-E c1/c8 中位数、真实报告 6/6 与 5/6 和三对首有用内容复算匹配。五份历史 SHA 清单 102/102 条、M1-E 原始索引 770/770、收口索引 132/132 匹配；工作区后端 212/212 文件与收口入场清单相符。它们是离线检查，不是本轮 JUnit、浏览器或真实服务通过。
- **失败与纠正：**本轮第一次离线核验先输出数值后，Python 3.9 因无 hashlib.file_digest 报 AttributeError、命令退出 1；改为分块读取后完成两份索引核验。一次用 wave_end 得 7.251 次/秒，核对原 summarize.py 后按最大 finish_monotonic 复算为原口径 7.252；未改历史数据。历史 M1-D 首次 1938/125/0/34 的夹具污染、M1-E 首次 1947/0/1/34 的隔离账号诊断权限、48 条探索并发标签错误以及真实 12 次中 1 FAILED 均保留。
- **测试与未执行：**本轮 Maven/Vitest、DB/MQ、浏览器、性能与故障测试均未执行，T/F/E/S 不适用；新增 DeepSeek/Embedding/Milvus 调用 0。历史 M1-E 普通后端 1947/0/0/34（1913 通过）、真实 MySQL＋RocketMQ 专项 2/0/0/0（模型桩）、收口前端 349/0/0/0、Mock HTTP 回放 14/0/0/0 均为历史运行，不相加为新测试。
- **Git/运行/未完成：**入场本地 HEAD 与缓存 origin/main 为 84e3489a85d0fb6c8b54ad2a130c52d7476fb897，本轮 GitHub 连接器只读核验远端 main 同 SHA；入场 29 个已跟踪文件修改、281 个未跟踪文件、暂存 0，四份工程记录已有内容，仅追加本节及关联记录。本批未暂存/提交/推送、未改嵌套仓库。Docker socket 访问被拒，历史隔离端口未见监听，不推断服务已停或运行版本；不启动/停止服务。真实性能对照、同用户热点、长期故障、内容质量和目标库准入均未完成；整体发布与“MQ 完整报告提速”均 NO-GO。学习入口 [L-P2MQ-01](SOURCE_LEARNING_BACKLOG.md#l-p2mq-01)、[L-P2MQ-02](SOURCE_LEARNING_BACKLOG.md#l-p2mq-02) 待学习、掌握未确认。
- **本轮原始核验输出：**[离线复算与散列检查日志](p2-mq-audit-20261115/offline-verification.txt)，保留首次 AttributeError、错误吞吐口径与修正后的独立执行输出；日志不是新的性能测试。


<a id="p2-mq-fair-ab"></a>
### P2-MQ-FAIR-AB — 公平 A/B 环境核验与离线复算（2026-11-15）

- **问题与状态：**当前同步 A、异步 B 在共同 K=4 下的完整 READY 和闭环吞吐尚无公平对照；本轮为安全准入与证据复算，**性能基线 NO-GO、生产发布 NO-GO**，不是代码优化批次。[独立报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-RocketMQ公平AB基线验证.md)。
- **环境与阻塞：**默认沙箱 Docker socket 为 permission denied，loopback 连接/绑定返回 EPERM。经批准的只读提权检查确认 Docker client/server 29.9.2、M1-C 两个 MySQL 和专属 NameServer/Broker 均停止；标签/端口/配额已核验，库内容、Topic、实际运行配置和端到端隔离未验证。旧模型桩仅计数、不限制 K=4，原 M1-C 夹具是一 MAIN，完整规范化请求体未逐对校验；因此预实验及正式矩阵均为 0 次。未启动容器或操作共享服务。证据：[默认沙箱](p2-mq-fair-ab-20261115/sandbox-preflight.json)、[只读提权](p2-mq-fair-ab-20261115/host-readonly-preflight.json)。
- **构建和工作区：**同步 A JAR `3fb095291adf7b21e8b69695f70aaa6085e6631b29522583f21af11a2e0d7c52` 与旧工作树 JAR 一致；当前 target JAR 与 M1-E 异步候选同为 `b82e87e22701d89837884b1ba54fcf1f3faf2da450a7f7a05631dfb188b3ca7b`；当前 `pom.xml`+`src/main` 的 212/212 文件与 M1-E 收口入场快照相符。JAR 未重建、现存监听进程身份未证明；源码默认 `report.async.enabled=false`，本轮没有实验实例可证明已启用。HEAD 与本地缓存 origin/main 均为 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，未访问远端；本批无提交 SHA、无暂存/提交/推送/部署。
- **历史真实性能与根因线索：**M1-C 旧 1 秒桩、c10、各 30/30 成功，A/B 客户端 READY P50/P95 1034.772/1046.482 与 5020.092/6967.793 ms，闭环 7.252/1.489 次/秒；A/B 实际模型峰值 10/2，且 POST 分别返回 READY/GENERATING，不是公平 MQ 开销。M1-E 当前异步内部 c8、各 8/8 成功，受理 afterCommit→最终 afterCommit P50 2817.341→2820.289 ms，未见整体改善；此比较不是同步/异步。R2 的 2/4/8 线程排队变化和 M1-E 峰值 4 支持复测消费限制，不证明它是唯一瓶颈。[离线结果](p2-mq-fair-ab-20261115/historical-replay.json)。
- **调用链与候选：**acceptReportTask → 受理事务 afterCommit → ReportOutboxWakeup/dispatchDue → ReportMqRuntime/ReportMessageConsumer → claimTaskExecution → generateClaimedReport → 最终事务 afterCommit。至多考虑共同 K=4 下隔离配置 4→8 消费线程；或在当前版 >16 条突发确证派发残余后考虑有界续扫。两项均未实施，须记录许可等待、期限、重复及 SQL/资源成本；K=4 的 1 秒模型服务理论上限约 4 次/秒，并不保证达到。历史 5/5 注入场景的可靠性边界及模型后崩溃需显式重试保持不变。
- **检查与遗留：**新增离线脚本实际复算历史 M1-C 60/60、M1-E 32/32；R2 点数 224 条/14 波，其中 5 组 76 条逐指标复核；重复结果逐字节一致，统计边界 3/3 通过。一次只读档案路径误写为 backups 引发 FileNotFoundError，已纠正，不是业务故障。无新增 Maven/Vitest/DB/MQ/浏览器/故障运行，T/F/E/S 不适用，真实 DeepSeek/Embedding/Milvus 新调用 0。先在授权环境核实独立库及唯一 Topic、补齐 K=4 桩和请求体配对；条件满足后方可执行原 360 测量+36 预热矩阵。学习入口 [L-P2MQ-03](SOURCE_LEARNING_BACKLOG.md#l-p2mq-03) 未确认。

- **收尾完整性检查：**入场快照中的其他 307/307 文件散列未变化，四份指定档案追加前确认原散列并验证只追加；新增节涉及的本地链接 19/19 存在。全工作区 `git diff --check` 退出 2，原因是入场即有未提交文件 `docs/question-bank/d1-drafts/HARD_AUTHOR_NOTES_20261114.md:21` 的 EOF 空行，原文件散列未变化，未擅自修正；本轮两份已跟踪档案的定向 `git diff --check` 通过，另两份未跟踪档案新增节的尾随空白检查通过。


### GIT-CLOSEOUT-AUDIT — 二期工作区安全清点与提交规划（2026-11-15）

- **状态与范围：**只读审计及提交规划完成；本批仅追加四份工程档案并形成[独立报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-Git工作区安全收口与提交规划.md)，未改生产/测试代码、DDL、配置或原始证据。M1-B/C/R2/D/E 是当前未提交工作区实现，不是新的已提交阶段；Q2 v5 是已撤回候选。
- **问题与边界：**入场外层 `main`、HEAD、本地 `origin/main` 与 GitHub 只读远端 `main` 均为 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`；暂存 0，已跟踪修改 29，未跟踪文件 286。SSH 443 DNS 查询失败，远端结论来自 GitHub 连接器；无 fetch。嵌套仓库独立且被忽略，仅只读核对，不改其内容。
- **关联调用链及提交取舍：**`acceptReportTask → Session/Task 同事务 → AFTER_COMMIT 提示/定时扫描 → Dispatcher/Producer → Consumer/claimTaskExecution → generateClaimedReport → completeReportGeneration` 与 Mapper/XML、隔离配置、前端 GET/POST 契约及对应测试交织。建议先提交仅含 SQL 脚本/引导说明的一批，再将当前后端、前端和测试作为完整逻辑单元；不凭阶段名称强拆。只有逐步重建、验证每一中间树后才能进一步拆分。
- **安全发现：**两处既有未提交文档将此前脱敏的人工作答会话/账号写为具体标识，暂不进入候选提交；原始模型响应、Q1 用户原文和原始实验日志/证据需逐项隐私审批，原件本地保留。合成实验脚本使用固定夹具密码，未与已忽略 `.env` 的候选凭据匹配，不据此认定真实凭据泄露；用途与目标需核实后才考虑入库。被忽略的 `.env`、`backups/`、嵌套仓库和日志不暂存。
- **版本及历史证据核验：**按 M1-E 收口入口清单及收口覆盖逐项重算，当前后端生产/配置 212/212、后端测试/资源 167/167、前端源码/测试 176/176 散列一致；原证据索引 770/770 与收口索引 132/132 一致。普通后端全量运行早于收口时的生成器测试及新响应夹具，历史 `1947/0/0/34` 不能写成对当前全部测试的重新执行；收口前端 `349/0/0/0`、Mock HTTP 复放 `14/0/0/0` 与真实 MQ/数据库专项 `2/0/0/0` 均为历史结果，适用边界见报告。
- **本轮检查：**无新 Maven/Vitest、数据库、MQ、浏览器、性能或模型测试，T/F/E/S 不适用；DeepSeek/Embedding 新调用 0。`git diff --check` 退出 2，唯一既有问题为 `docs/question-bank/d1-drafts/HARD_AUTHOR_NOTES_20261114.md:21` 末尾空行；暂存区检查退出 0。保留该文件原样，不将格式问题当作业务测试失败。
- **Git/下一步：**本批无提交 SHA，未暂存、提交、推送、迁移或部署。推荐待明确授权后从现有基线建立 `codex/phase2-git-closeout`，逐文件挑选并核查每批最终树，绝不全量暂存；测试库迁移和真实 MQ 验证仍需各自满足目标隔离与授权。评分/报告内容及生产发布仍 NO-GO，学习入口 [L-GIT-01](SOURCE_LEARNING_BACKLOG.md#l-git-01) 待本人确认。


### GIT-CLOSEOUT-READY — 工作区保护、隐私整理与提交前验证（2026-11-15）

- **状态与目标：**已建立本机恢复快照、创建候选分支并完成隐私与提交前验证；未暂存、提交、推送、合并或部署。[独立报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-Git安全整理与提交前验证.md)。核心 SQL 与代码可以按所列范围进入后续 Git 提交审查；整个工作区尚不适合一次性提交，真实 MQ 及生产切换不在本轮验收范围。
- **Git 与保护：**外层 `codex/phase2-git-closeout` 的 HEAD、`main`、本地 `origin/main` 与本轮 GitHub 只读 `main` 均为 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`；暂存区空、无新提交 SHA。入场 29 个已跟踪修改和 286 个未跟踪文件已逐项复制到仅本机的 `/Users/kun/Developer/projects/ai-interview-system/backups/GIT_CLOSEOUT_20261115_145412_c53370`，315/315 内容回读哈希一致，目录 0700、文件 0600；既有 `.env`、其他备份及独立嵌套仓库不在本快照内，也没有被修改。分支创建后 315/315 初始文件保持不变；后续仅按本批隐私及测试范围修改，原件仍在快照。
- **隐私与变更：**`.gitignore` 对原始实验/评审、交接 ZIP、原始供应商响应、固定合成密码的脚本及 Q1/Q2 工具设本地排除；不删除历史文件。两份原含人工标识的 U1 文档已还原为占位描述；进一步发现台账和学习清单中同一人工会话的标识及台账中原答细节，也已匿名化，原文保存在快照。原始响应未改，`DeepSeekInterviewReportGeneratorTest` 新增不依赖私有数据的合成超限用例，保留原始字节/SHA 回放，在本机夹具存在时运行；干净公开检出时该私有回放有 1 条条件跳过。
- **必要文档：**`README.md`、`docs/GETTING_STARTED.md`、`AGENTS.md` 补充异步报告默认关闭、没有同步回退、任务表增量迁移、FINAL 聚合和历史阶段边界。`.gitignore` 中的历史证据目录在公开检出中不可用；工程档案中的本地链接仅供持有原件者追溯，不能当成随仓库提供。
- **SQL 与调用链：**基础 `sql/ai_interview.sql` 仍是 13 表且指定 `ai_interview`；`sql/migrations/20261114_interview_report_task.sql` 单独增加任务/Outbox 表。只读核验隔离 `ai_interview_test`：17/17 列类型及空值约束、6/6 索引定义、外键 RESTRICT、2 个启用 CHECK 与迁移声明相符；真实 MyBatis 测试覆盖任务领取/租约/事务。当前 `acceptReportTask → Task/Outbox → Dispatcher/Consumer → claimTaskExecution → generateClaimedReport → completeReportGeneration` 与前端状态契约须作为同一功能单元，不按 M1-B/C/R2/D/E 标签机械拆分。未对任何正式表执行迁移；普通全量的历史迁移合同仅运行在连接级临时表。
- **测试维护：**`InterviewReportTransactionServiceIntegrationTest.shouldRollbackReportAndWeaknessWhenSessionReadyUpdateFails` 在本次测试事务中于 Report/Weakness 写入后注入版本变化，验证 READY 条件更新失败时报告、弱项和版本一起回滚；不改生产逻辑。定向 MySQL `1/0/0/0`。以用户限定的安全环境为前提，记录测试前后 14 张隔离表均空；未清理历史业务数据或迁移正式库。
- **本轮实际测试 T/F/E/S：**默认沙箱首次后端定向 `134/0/90/0`，错误根因是 Mockito/Byte Buddy 不能外部自附加；在本机授权环境原样复跑 `134/0/0/0`，含私有原始响应回放。隔离 MySQL 定向 `194/0/0/0`，新增回滚 `1/0/0/0`；加测试前普通全量 `1949/0/0/34`。最终不带私有响应的公开候选全量 `1950/0/0/35`（1915 通过；34 个真实服务门禁、1 个私有原始响应回放），公开生成器定向 `15/0/0/1`。前端报告页 `59/0/0/0`、全量 `349/0/0/0`；lint、typecheck、build 均退出 0。不同运行重叠，不相加。日志、151 份最终 Surefire XML、预检/后检及 556 项源码清单在本机 `/Users/kun/Developer/projects/ai-interview-system/backups/GIT_CLOSEOUT_VERIFICATION_u0a30otz`。
- **未验证与残余：**本轮真实 MQ、DeepSeek、Embedding、Milvus 运行及收费调用均为 0；不把历史真实服务成功写成本轮结果。全工作区 `git diff --check` 仍因入场已有的 `HARD_AUTHOR_NOTES_20261114.md:21` EOF 空行退出 2；候选范围定向检查通过，原文件未改。真实目标迁移、旧写入者排空、长期 MQ 故障/运维和内容质量仍待单独验收；不可宣称生产发布或性能提升。学习项 [L-GIT-02](SOURCE_LEARNING_BACKLOG.md#l-git-02) 待本人确认。


### GIT-CLOSEOUT-COMMITS — 二期本地分批提交与证据边界（2026-11-15）

- **问题与处置：**在保留 315 项恢复快照、199 项已忽略文件和独立嵌套仓库的前提下，按明确文件路径分批暂存、检查并提交；未使用 `git add .`。原始响应、私人账号/作答、凭据、日志和备份不进入本批。M1-A/B/C 的必要设计、验收及三份工程索引作为文档依赖单独纳入本批；被忽略的原始证据仍只在本机。
- **实际本地提交：**隐私规则与匿名化 `ed7a7ba8b1e0c3fc4276154f242e67178acf1b1a`（2 文件）；报告任务表 SQL `18ef21b4c3e3cd30e4d30be645f41bb2ecabb445`（2 文件）；M1-B～E 业务/测试及运行说明 `28cb2db25519e9d26c95dc00d716af4993d60f2d`（51 文件）。这些 SHA 位于本地 `codex/phase2-git-closeout`，不表示已经推送、合并或部署；各提交按当时暂存文件逐项核验、`git diff --cached --check` 通过。
- **本批文档提交身份：**本条和案例库、简历证据库、学习清单及必要的历史摘要属于后续文档提交；自引用 SHA 不能预先写入同一个提交，可用 `git log -1 -- docs/engineering/IMPROVEMENT_LEDGER.md` 查看实际完整 SHA。四批最终 SHA 与工作区状态另载于本机正式收口报告，不以预测值替代。
- **复用的验证：**逐文件核验 556/556 测试源码/SQL 与 `backups/GIT_CLOSEOUT_VERIFICATION_u0a30otz/final-source-manifest.json` 一致；没有改生产代码、测试或 SQL，未为提交机械重跑。上轮最终公开候选后端 `1950/0/0/35`（34 条受控外部服务、1 条本机私有回放跳过），隔离 MySQL 定向 `194/0/0/0`、READY 失败回滚 `1/0/0/0`，前端 `349/0/0/0`，lint/typecheck/build 通过。本轮新增真实 MQ、DeepSeek、Embedding、Milvus 和付费调用均为 0。
- **边界与待办：**初始 HEAD 及本轮只读远端 `main` 为 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，未经 push 的本地分支提交不在远端；上次全工作区已有 EOF 空行和其他未提交工具/交接材料仍须逐项处理。异步默认关闭、无同步回退；真实 MQ 故障、生产库迁移/切换、评分与报告内容质量仍需单独验收，不因 Git 提交而放行。用户本人掌握状态未确认。

### GIT-PUSH-PREFLIGHT — 公开分支推送前隐私阻塞（2026-11-15）

- **状态：未推送。** 按用户本轮“存在隐私冲突时停止推送、不得擅自重写历史”的要求，保留四个本地提交及分支原样；本条是本地未提交的工程记录，不包含备份路径明文。
- **版本：**候选分支 HEAD `0509cd6729c8dc6e45f35c9aef2b49985c59bf75`；本地 main、origin/main 与 GitHub 只读远端 main 均为 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，远端未发现同名候选分支。四个提交链与上次交付一致，暂存区为空。
- **阻塞事实：**目标仓库为公开仓库；第四个提交新增 4 个此前基线未包含的本机私有备份绝对路径，另有本地报告和工作站绝对路径。推送会公开这些路径及提交历史，尽管备份内容本身未暂存；是否允许公开需由用户明确决定。四个提交作者/提交者邮箱均与既有公开基线一致，但并非 GitHub noreply。
- **其他隐私检查：**四个提交共 68 项文件修订，未发现已知真实人工账号、原始回答、私有模型响应标识或已配置非占位凭据；扫描有范围限制。历史基线已有 8 份日志和 5 张截图被跟踪且命中忽略规则，四个提交均未改动；日志已做已知值扫描，截图上次视觉核验为隔离演示材料。
- **完整性与验证：**恢复快照 315/315 校验、199/199 忽略原件一致、根目录 0700；提交树与已测源码/SQL 556/556 一致。旧独立嵌套仓库未修改。没有重跑测试、执行迁移、调用收费模型或推送；上轮测试证据及 35 条跳过仍按原范围解释。
- **下一步：**先决定是否允许公开现有路径元数据，或另行授权准备保留现有分支的脱敏提交链；单独追加一个脱敏提交不能抹去前序提交中的路径。除本条外，原有 1 个文档修改及 50 个未跟踪文件继续保留。

### GIT-PUSH-CLOSEOUT — 候选分支推送完成（2026-11-15）

- **授权与隐私：**用户明确允许普通本机备份绝对路径及既有 Git 作者邮箱随公开仓库提交，不重写历史。再次核对 4 条备份路径均为项目备份目录路径字符串，未匹配凭据标记或已配置的非占位密钥值；备份文件未加入提交。
- **推送结果：**`codex/phase2-git-closeout` 已推送并设置上游 `origin/codex/phase2-git-closeout`；GitHub 只读查询确认远端 HEAD 为 `0509cd6729c8dc6e45f35c9aef2b49985c59bf75`，与本地 HEAD 一致。远端 `main` 仍为 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，未推送或合并 main，未创建 PR。
- **连接过程：**SSH 443 的公钥认证被拒，首次推送失败；临时覆盖 `remote.origin.url` 的一次尝试仍选用了原 SSH 地址，也未推送。随后仅对本次命令指定 HTTPS `pushurl`，使用本机已有钥匙串凭据成功推送；持久 `origin` 地址未修改，未使用强制推送。
- **保全与验证：**推送前本地分支和四个 SHA 与记录一致，暂存区为空；恢复快照仍在，原有未提交文件未纳入推送。本轮未重跑测试、修改生产代码、执行迁移、部署或调用收费模型。真实 MQ 验收、目标库迁移和生产切换仍需单独处理。本条台账记录保持本地未提交。


### P2-MQ-CANDIDATE-ACCEPTANCE — 当前候选 RocketMQ 核心链路复核（2026-11-15）

- **结论与范围：**真实 MySQL + RocketMQ、候选 JAR 和本机固定响应模型替身完成有限核心链路验证；可以进入后续部署准备和逐目标准入核对。此结论不批准目标迁移、正式部署或内容发布，完整生产 MQ 可靠性和报告语义质量仍 **NO-GO**。本轮未修改生产/测试源码、SQL、配置或状态机，也未提交、推送或合并。
- **构建与身份：**从 Git `0509cd6729c8dc6e45f35c9aef2b49985c59bf75` 的 `git archive` 在独立临时目录离线 `./mvnw -o -B -ntp -DskipTests package` 构建；成功编译 193 个生产源文件及 155 个测试源文件，但该命令跳过测试。实际运行 JAR SHA-256 `3346146f49fca7c7c604b2d70c3ebddc134d7d49a97467a07d80fe35c2fa07e0`，原件及构建日志保存在本机忽略目录 `backups/MQ_CANDIDATE_20261115_soe3tev4/`。本地 HEAD 与缓存的 `origin/codex/phase2-git-closeout` 均为上述 SHA；此前记录已确认候选分支推送，本轮未实时查询远端。
- **隔离与配置：**复用历史批准且迁移已执行的 `127.0.0.1:3308/ai_interview_test`，MySQL 8.0.45、容器 ID 前缀 `02ccbfd0e120`、14 张表；没有创建 Schema、执行 DDL、扩权或访问其他业务 Schema。应用和 Maven 使用原隔离测试账号；有界合成夹具的 SQL 通过已核验的同一容器 root 执行。仅重启 `ai-interview-m1b` 专用 RocketMQ 5.3.2，NameServer `127.0.0.1:19876`、Broker `127.0.0.1:20911`、Topic `ai-interview-m1b-report-test`、消费组 `ai-interview-m1b-report-test-consumer`，既有 Topic 路由经只读核验；Broker 使用独立容器，不与其他项目共用。实际启动的两个 JAR PID 分别为 90998（异步开启）和 91459（关闭），绑定 `127.0.0.1:28082`；`test,report-mq-test`、Milvus 关闭，真实 MQ 开关仅在测试进程临时设置，模型地址强制指向 `127.0.0.1:28889` 替身。
- **调用链与正常幂等：**真实 HTTP `generateReportForApi → acceptReportTask` 在会话锁下提交状态与 Outbox，`ReportOutboxWakeup/Dispatcher → ReportMqRuntime → ReportMessageConsumer → claimTaskExecution → generateClaimedReport → completeReportGeneration` 进入模型替身并原子落库。1 个合成 Session 的 6 次并发 POST 均返回 `GENERATING`；模型替身受控阻塞期间从独立连接观察到 1 个 `RUNNING/SENT` 任务、0 报告、0 弱项。放行后 `READY/SUCCEEDED/SENT`，Report 1、Task 1、Weakness 1 且 `ACTIVE/discovered_count=1`；再 POST 两次仍 `READY`，上述状态/数量和版本不变，替身只调用 1 次。
- **有界停复与关闭开关：**仅停专用 Broker 后对另一 Session 连续 POST 两次，均受理且只有 1 个 `QUEUED/PENDING` 任务；真实发送失败后 `send_attempts=1`、报告/弱项为 0，日志为 `REPORT_SEND_UNCERTAIN ... MQClientException`，这是预期注入故障而非证明生产代码缺陷。启动同一 Broker 后，原任务以 `send_attempts=2` 自动达到 `READY/SUCCEEDED/SENT`，Report/Task/ACTIVE Weakness 各 1，发现次数 1，替身调用 1。由 Broker 启动命令起至观察 READY 为 **4.377 秒**，仅单次本机样本、含轮询，不能当 RTO、SLA 或性能收益。以同一 JAR 重启并关闭异步后，新 Session 的 POST 返回 HTTP 400、`报告生成服务当前不可用`，无任务/报告/弱项且状态版本未变；已有 READY 报告 GET 200，核对的 Session 版本、任务及各项计数不变。
- **测试计数：**上述 HTTP 场景覆盖 3 个合成 Session，其中 2 个生成报告、1 个验证禁用；包括 6 次并发首次申请、2 次 READY 后重放、故障期间 2 次申请和 1 次禁用 POST。另从相同 Git 导出源码执行受两层保护、显式临时开启的真实 MySQL + MQ JUnit 两个指定方法，**T/F/E/S = 2/0/0/0**：真实重复消息不重复模型/弱项、消费者完成后迟到 `markSent` 不回退成功状态；生成器为 Mockito 替身，不是网络层丢 ACK 或进程崩溃。构建和 HTTP 场景不是 JUnit 数量，不与 2 项相加为测试用例总数。普通全量本轮未重跑；历史公开候选后端 `1950/0/0/35` 属先前运行，35 条跳过不构成外部服务通过。
- **历史故障版本适用性：**对照 M1-C 修复后 B 版源码清单，211 项中 **206 项相同、5 项变化、0 缺失**，当前另增加 `ReportOutboxWakeup.java`。`ReportMessageConsumer`、`ReportMqRuntime`、`InterviewReportService`、FINAL Reader 和两份 Task/Session Mapper XML 的散列未变；`ReportAsyncConfiguration`、`ReportAsyncProperties`、`ReportOutboxDispatcher`、`InterviewReportTransactionService`、`application.yaml` 已变化。旧证据可用于解释相同实现部分的已观察行为，不得把五组历史进程崩溃认作当前 SHA 的五次重测。[历史故障记录](2026-11-15-M1C-FAILURE-INJECTION.md)保留：事务后退出/停复、发送成功后退出、不同组消费者竞争、提交后 ACK 前退出、模型返回后退出共 5 个最终版场景达到各自断言；测试缩短部分租约/期限，第五种需显式重试。当前复核的是短暂停复、实际重复消息和迟到 `markSent`，并未复现真实进程 kill 或网络 ACK 丢失。
- **风险和代价：**本轮未验证 MySQL 真停机或 JDBC 提交结果未知、Broker 永久磁盘损坏、同组多进程重平衡、长时间积压与耗尽重试/自然 600 秒期限、生产多节点 HA、真实 DeepSeek 成功后崩溃、告警/人工重放及目标切换回退。历史 `M1C-RECOVERY-01`：运行中消费者在模型返回后死亡，期限收口后仍需用户显式重试；历史 `M1C-REPORT-QUALITY-01` 的无依据具体判断仍未解决。模型替身不证明报告质量或外部调用恰好一次，不从本轮样本推断性能或可靠性提升。
- **资源与失败记录：**真实 DeepSeek、Embedding、Milvus API 新调用均 **0**，因此本轮外部模型 Token 消耗为 0；没有查询供应商账单，不虚构费用数据。正常与故障后的 Broker/消费组 offset 均已追平；保留原消息和偏移。测试后仅按当轮 ID/账号删除 3 组合成数据，14 张测试表逐表精确计数均为 0。随后只停止本轮启动的 NameServer/Broker，停止超时设 3 秒后两容器 exit 137、OOMKilled=false，不能称作优雅关闭；原 MySQL 和其他项目服务继续运行。最初 `py_compile` 因默认 Python 缓存目录被沙箱拒绝而失败，随后用内存 AST 校验临时工具语法，未涉及生产代码。原始事件、日志、清理前 SQL 结果、清理对账、来源哈希和 JAR 均在上述本机目录；未删除历史失败或恢复快照。
- **工程记录：**本条及 [案例库](ENGINEERING_CASEBOOK.md)、[简历证据库](RESUME_EVIDENCE_BANK.md)、[学习清单](SOURCE_LEARNING_BACKLOG.md#l-p2mq-candidate-01)仅追加，原先未提交台账内容保留；本轮无文档提交 SHA。源码学习仅登记入口，个人掌握与贡献均未确认。

- **当前版本提交后唤醒补充回归：**针对历史 M1-C 后新增的 `ReportOutboxWakeup` 差异，在同一 Git 导出树执行 `ReportOutboxWakeupTest,ReportOutboxWakeupIntegrationTest`，**9/0/0/0**：5 项 Unit/Mock，4 项真实 MySQL 事务与派发 CAS、MQ 替身。覆盖提交后可见、回滚不持久化/不唤醒、拒绝提示后周期扫描、唤醒与扫描共享租约；没有重启 Broker，不是新的真实 MQ 故障或进程崩溃实验。原始日志 `backups/MQ_CANDIDATE_20261115_soe3tev4/wakeup-targeted-junit.log`。


<a id="p2-deploy-admission"></a>
### P2-DEPLOY-ADMISSION — 二期部署准入与最小方案（2026-11-15）

- **目标和状态：**完成只读环境、版本、数据库、MQ 与故障证据核对，形成[目标环境准入和最小部署方案](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-二期部署环境准入与最小部署方案.md)；本机完整受控演示当前 **NO-GO**、长期运行 **NO-GO**、公网 **NO-GO**。目标 Schema 尚未由用户选择/授权迁移，报告中的启动和回滚命令仅为后续模板，未部署或修改业务数据。
- **问题、原因及取舍：**本机 `127.0.0.1:3308/ai_interview` 为 13 表、任务表缺失，题目/评分点/知识文档/会话均为 0；`ai_interview_test` 已有任务表且任务/会话/报告均为 0，原批准不涵盖 local。当前 healthy 的 19532 Milvus 没有 local Collection，旧 19530 实例停止且原卷需保全；现有专用 Broker 停止，其消息与 offset 位于容器可写层，不可直接作为长期持久存储。各状态源于测试隔离、逐目标准入与实验容器设计，不能未经验证定性为源码故障。
- **现场与配置边界：**PID 8362 的旧同步 JAR `48db442b...` 仍监听 18482、连接 evaluation 数据库；PID 97909 的 Vite 监听 15174、代理旧后端，版本可能随工作区变化；均未停止。当前候选禁止在 evaluation profile 打开报告 MQ；local `.env` 的 DeepSeek/Milvus 关闭且缺 DeepSeek key，旧 evaluation 进程存在非空模型配置不代表新目标可用。`accepting=false` 不停止处理已有任务；旧手册 2 s/2 线程与候选默认 500 ms/4 线程不同。
- **最小方案与调用链：**建议待审批后单体 Java + Vue、本地 MySQL/Milvus、隔离的 RocketMQ Topic/group：`generateReportForApi → acceptReportTask → ReportOutboxWakeup/ReportOutboxDispatcher → ReportMqRuntime → ReportMessageConsumer → generateClaimedReport`。现有 test Broker 仅可考虑短时演示，同一容器 stop/start 保留可写层；长期需要经单独验证的 Broker 持久卷、备份恢复与静态站点路由。停止先断入口并排空；回退先确认旧/新执行者退出和各状态一致，保留任务表、MQ 数据及原卷。
- **故障差异：**复算 M1-C 历史 B 清单 211 项中 206 相同、5 变化、0 缺失，另新增唤醒；旧进程 kill 证据不等于当前 SHA 重测。当前候选此前真实 MQ 2/0/0/0、唤醒定向 9/0/0/0（其中 4 为真实 MySQL+MQ 替身），本轮未重跑。若长期使用需优先针对提交后发送前及提交后 ACK 前的当前版本真实进程 kill 补证，RUNNING 超时显式重试限制仍在。
- **验证和影响：**本轮只读 GitHub 分支、Docker、进程/端口、MySQL 只读聚合及元数据、Milvus 健康/Collection、Broker 离线配置和 JAR 哈希；未执行任何 JUnit/Vitest/真实故障测试（T/F/E/S：0/0/0/0，未执行）、迁移、服务启动/停止或真实模型调用。先前普通后端 1950/0/0/35 为历史独立运行，跳过不是真实服务通过。无新性能、可靠性提升或费用结论。
- **版本与文件：**本地 HEAD 和实时远端候选为 `0509cd6729c8dc6e45f35c9aef2b49985c59bf75`，实时远端 main 为 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，候选领先 4、落后 0；候选 JAR SHA-256 `3346146f49fca7c7c604b2d70c3ebddc134d7d49a97467a07d80fe35c2fa07e0`。本轮仅追加四份工程文档并新增外部报告，未更改代码/测试/SQL/配置；本批文档未暂存、未提交、未推送，无本批提交 SHA。用户既有未提交内容和原始失败证据保留。
- **未决：**精确部署目标与每目标迁移/数据授权、同步一致备份及恢复演练、local 知识数据和 Collection、MQ 持久化、真实模型配置与有界链路、长期运行/公网安全、已知报告文本质量。只登记[学习入口](SOURCE_LEARNING_BACKLOG.md#l-p2-deploy-admission-01)，不代表开发者已掌握。

- **历史构建细化复核：**M1-C 原始 `entry-B.jar` 实测 SHA-256 `f3b6095d5ed9cc465dfd8f000a1ea658a08c771576e19e8817e134de427e5334` 与历史记录相符。其打包 YAML 与当时源码清单散列一致；与候选 YAML 及两版 `javap -p -c/-v` 对照，确认消费线程 2→4、扫描 2000→500 ms、受理后新增事件及 `AFTER_COMMIT` 唤醒与独立有界执行器。Dispatcher 指令输出一致但定时注解变化；不能将其推成完整端到端等价。该检查是静态证据，不是新增测试，详见报告第 10 节。


<a id="p2-local-demo-e2e"></a>
### P2-LOCAL-DEMO-E2E — 本机隔离演示与真实端到端验收（2026-11-15）

- **结果和范围：**按用户对精确目标的明确授权完成一场真实 MySQL/Milvus/Embedding/DeepSeek/RocketMQ 的本机 EASY 合成面试，单用户有界演示 **GO**；长期运行、公网及 Q1/Q2 内容质量均 **NO-GO**。API 执行注册/组卷/答题/报告，独立浏览器验证登录、历史与报告刷新，不将这些分别执行的检查夸大为每一步均由 UI 完成。完整[验收报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-二期本机完整演示与真实端到端验收.md)，私有证据目录 `/Users/kun/Developer/projects/ai-interview-system/backups/DEMO_ENV_20261115_9lpoxk7u/`。
- **目标及迁移：**唯一目标 `127.0.0.1:3308/ai_interview`、容器 `02ccbfd0e120...`。迁移前 13 个 InnoDB 表、1 个既有 USER、无 Session/Question/KnowledgeDocument/任务表；一致性逻辑备份 SHA-256 `1cad40c30191c08916fad2fb80c518b1b88d19be8022eb1a27ac53b531548b11`，13 表结构已核，恢复未演练。经明确批准仅执行一次 `20261114_interview_report_task.sql`（SHA-256 `8143a05aa12d663ca3fa4f22fb301ed5524360ba33781c912969b6af27aaab7d`），后验 14 表，17 列、6 索引、5 约束与已测 test 表一致。没有 DROP/清库/覆盖恢复或读写 evaluation 数据。
- **最小数据与链路：**新建管理员 ID 2（只对该新账号条件升权）、普通候选 ID 3；通过管理 API 写入已审 E01–E05 共 5 题/21 评分点及 5 篇 READY 文档，切片 10；local Collection 10 条向量的标识与 MySQL 元数据逐条一致。实际 `InterviewController → InterviewService → EvaluationOrchestrationService → Embedding/Milvus/MySQL RAG → DeepSeek → FINAL`、`generateReportForApi → acceptReportTask → Wakeup/Dispatcher → ReportMqRuntime → ReportMessageConsumer → generateClaimedReport → completeReportGeneration` 均贯通。Session 1 的 5 个 Answer EVALUATED、5 个 FINAL、5 个 RAG batch/25 hit、任务 `SUCCEEDED/SENT`、Report 1；0 条弱项，未覆盖弱项新增。无 FOLLOW_UP 自然触发、无伪造 READY。
- **模型、时间与质量：**5 次知识处理 + 5 次查询 Embedding 共 **10 次/5245 tokens**（4441+804）；DeepSeek 5 次评价+1 次报告共 **6 次**，输入 **21131**、输出 **3151**、合计 **24282 tokens**，HTTP 均 200。报告 POST 受理 **21.597 ms**，1 秒轮询首次观察 READY **5085.179 ms**，报告 DeepSeek 转发耗时 **4906.690 ms**；单次样本不推断稳定性能。未获取供应商账单，费用 UNKNOWN。五个 FINAL 92/90/91/92/91，报告 91.2，单样本复核无明显事实矛盾；LinkedList 对已说明 O(n) 定位的反馈存在措辞/粒度风险。参考答案作为合成作答有高分偏差，不代表模型质量通过。
- **运行配置与保护：**后端 `8081`、Vue `5173`、有限真实模型验收转发器当前 `28891`；local Collection 在 19532 独立命名空间，专用 MQ 在 19877/20912，独立 Topic/group 与持久 store/log 卷，历史测试 Broker 未启动、evaluation 的 `18482/15174` 进程未停止。`.env` 中 Embedding URL 实为本地替身，因此真实演示使用受保护的 provider 配置，经临时回环转发器原样转发并记录 usage，不改生产代码。完成一场后排空并重启本轮后端/转发器，新账本为空、后续限 DeepSeek 11/Embedding 15；重启后完成浏览器/消费者复核，但未再次付费重跑整场。
- **验证与失败：**普通 Maven/JUnit、Vitest 未运行（T/F/E/S=0/0/0/0，未执行）；前端依赖检查/build exit 0；2 轮真实浏览器历史/报告恢复及 1 轮重复注册 HTTP 400 后登录 200，未额外调用模型。MQ 创建初期就绪命令有异常但 exit 0，按实际路由而非码判断；首次对账误把 ACTIVE 写作 ENABLED、另一次只读 SQL 列名笔误 1054，已更正并保留原始错误。演示 Broker 空载直接 stop 45 秒后 exit 137，后用镜像 `mqshutdown broker`：exit 143、日志有 Shutdown hook over/commitlog 关闭成功，重启记录正常关闭，偏移前后 1/1、积压 0；不代表在途消息或生产 RTO 已验收。首轮 Docker 模板预检失败发生在停机前，记录保留。
- **状态、影响和未决：**代码候选 HEAD `0509cd6729c8dc6e45f35c9aef2b49985c59bf75`；本轮未改生产/测试源码、项目 SQL 或配置，未暂存、提交、推送或合并；新增的隔离脚本/私有日志均在 Git 忽略的证据目录，现有四份工程文档仅追加。数据库/Collection/MQ 和两名合成账号是经授权的持久本机状态，未自动清理。未覆盖模型失败、运行中 crash、同组多节点和长期堆积；mqshutdown 只验证空队列；备份未恢复演练，费用和 Q1/Q2 仍未知/NO-GO。[学习入口](SOURCE_LEARNING_BACKLOG.md#l-p2-local-demo-01)仅登记。

<a id="p2-ai-quality"></a>
### P2-AI-QUALITY｜真实 AI 评分、报告事实和结构验收（2026-11-15）

- **状态与范围。** Codex 完成证据审计、冻结案例和有界报告对照。报告 v2 候选在隔离目录编码、编译并定向测试，真实样本出现退化后拒绝采用。评分准确性、报告事实和输出可靠性均为 NO-GO。 [专项验收](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-真实AI评分与报告质量专项验收.md) · [复核案例](../evaluation/2026-11-15-AI质量复核案例集.md)
- **实际问题与根因。** 演示 LinkedList 已答按下标定位 O(n) 和定位后改链接，第 7 个 KEY/25 仍为 false，模型额外要求显式串起 add/remove。Java 五维 19/18/16/19/18 求和为 90，缺乏可核对的数值扣分反事实。报告两处把 Setter 注入、ACID 刷盘等可选拓展升级为弱项，LinkedList 条目继承上游粒度问题并压缩条件。M1-E 原响应的 7/7/5 项在发送端正常结束，现有 Java 重放拒绝；同一请求曾有合规结果，模型为何违约尚无法确认。
- **调用链与改动。** 只读快照与存档 API 对账。EvaluationPromptBuilder → DeepSeekEvaluationClient → LlmEvaluationValidator → EvaluationScoreCalculator → FollowUpPolicy 的离线回放重建了原五题。InterviewReportFinalReader 提供每 MAIN 的有效 FINAL 与 Java 均分，InterviewReportPromptBuilder 仅送 FINAL 的叙述与分数。候选只动 InterviewReportPromptBuilder 的系统提示和版本，尝试明确字段证据、主答/追问范围和缺证据文案。没有进入正式源码。校验、业务事务和 READY 写入仍由既有实现控制。
- **真实对照与成本。** 五个固定风险输入各跑一次 v1 和候选，仅系统提示变化。DeepSeek 10/10 HTTP 200，Java 合格 5/5 → 3/5（100% → 60%），拒绝 0/5 → 2/5（0% → 40%），分别为重复字段和优势 6 项。候选空证据时少了具体技术弱项推断，却把 3 道 EXCELLENT 写成 4 道，仍传播 Java 8/private 上游错误，仍把 Setter 列入演示弱项。17,920 输入、6,044 输出、共 23,964 tokens，0 重试。按非高峰价估算 $0.005016096，实际账单未知；Embedding、评分重调及新业务端到端均为 0。
- **执行证据。** 基线 Maven Wrapper 离线 Unit/Mock 与 Mock HTTP 共 130/0/0/0，隔离候选报告定向 16/0/0/0，均包含原响应回放且测试重叠。5 份评价和 1 份报告请求散列 6/6 与原转发器一致。真实 MySQL 前后 33 行证据字节相同；两条 LEFT JOIN 字面量查询返回 1,2 与 2。javac 私有接口方法在 release 8 预期退出 1，在 release 9 退出 0。启动器首次在发请求前因主机 Python 版本退出，修复启动器后按冻结计划调用。未执行普通全量、浏览器或真实 MQ。 [测试与用量原件](/Users/kun/Developer/projects/ai-interview-system/backups/AI_QUALITY_20261115_kEsfnFQK/)
- **本地与远端。** 本地 HEAD、同名 GitHub 候选分支均为 0509cd6729c8dc6e45f35c9aef2b49985c59bf75，GitHub main 为 84e3489a85d0fb6c8b54ad2a130c52d7476fb897。本批跟踪范围为四份工程记录的追加与新增案例集，均未暂存、提交或推送。补丁、原始输入输出、失败与验证证据保存在 Git 忽略目录；报告与 Canvas 单独交付。
- **后续门槛。** 冻结等价表达、真实缺项、事实错误与拓展的逐点人工口径，加独立留出后再比较新方案。已知历史问题继续作为回归样本。没有评分准确率提升或结构失败率的总体估计。 [学习项 L-P2-AIQ-01](SOURCE_LEARNING_BACKLOG.md#l-p2-aiq-01)


<a id="p2-report-trust-20261116"></a>
### P2-REPORT-TRUST — 报告内容证据边界最小候选及否决（2026-11-16）

- **状态与问题：**内容质量 **NO-GO**，撤回整个隔离候选；正式工作区生产、测试及前端代码不变。v1 Prompt 和 Java 均要求 weaknesses 1～5 项，无法表达无明确负面证据；上游 FINAL 可能误判，且报告无法访问原答或独立核实技术事实。不能将 missingPoints 空数组等同无错误，也不能把可选拓展直接当缺陷。[完整验收报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-16-报告内容可信度最小修复与回归验收.md)。
- **调用链及最小实验：**`InterviewReportFinalReader.aggregateFinalEvaluations → InterviewReportPromptBuilder.build/toPromptEvaluation → DeepSeekEvaluationClient.completeJson → DeepSeekInterviewReportGenerator.parseResponse/validateTextList → InterviewReportService` 的 JSON 序列化及 READY 读取 → 前端报告页。隔离候选仅调整报告 Prompt、允许 weaknesses 0～5、报告专用重复字段/尾随 JSON 拒绝和空弱项提示；评分 v4、RAG、持久模型、Schema、MQ/Outbox 未改。候选源码与测试的 [完整补丁](../../backups/REPORT_CONTENT_20261116_ua0rf2j0/candidate.patch) 只作失败证据，**未应用**。
- **根因与取舍：**JSON object 模式不保证列表语义上限，历史已知字段重复和 7/7/5 原始响应仍按原契约拒绝；另测到重复未知字段可被旧解析器忽略。语义上，Prompt 无法验证 FINAL 真伪，也无法确保遵循数量和版本禁令；候选虽然允许空弱项，却在 R3 把明确的 StringBuilder 并发边界与 prototype 首次引用缺项全部降为 `weaknesses=[]`，建议中还丢掉前者。保留原实现符合“不证明有效则不交补丁”；不应据此认为现状无风险。
- **离线测试（总数/失败/错误/跳过）：**基线首次 **75/0/37/0**，根因是 Mockito 自附加在沙箱失败；使用已有 Mockito Java 代理原组重跑 **75/0/0/0**。基线历史 Mock 回放 **12/0/0/0**；隔离候选后端 **90/0/0/0**（包含 13 项回放），前端基线 **59/0/0/0**、候选 **60/0/0/0**。不同运行和测试有重叠，不相加。冻结请求另有 14 次 Mock HTTP 干跑；不是实际模型或数据库测试。两次启动脚本兼容问题均发生在付费请求之前，完整记录保留。
- **真实配对：**5 组历史风险案例＋2 组未参与调参的新合成留出案例，各臂一次，共 **14 次 HTTP 200/stop**，Java 结构基线 **6/7**、候选 **7/7**；本轮 R3 基线因 strengths 8 条拒绝，历史合法 v1 响应本轮重放可见两个缺项，不能把历史响应称为本轮同对成功基线。候选 R1 避免把 Setter 列弱项；R2 空弱项但仍由高分推断能力；R3 丢有评价依据的缺项；R4 未完整保留追问范围；R5 版本措辞仍有歧义。H1 无负面返回空，H2 仅 correction 明确错误时保留弱项；均非独立人工金标，不能宣布质量 GO。
- **用量、开销及时间：**DeepSeek 14 次、0 自动重试，输入 **21,023**（缓存命中 10,368、未命中 10,655）、输出 **6,398**、合计 **27,421 tokens**；按调用当时 [DeepSeek 非高峰费率](https://api-docs.deepseek.com/quick_start/pricing/)估算 **$0.005468154**，供应商实际账单未知。两臂 HTTP 中位数 4182.205/3300.865 ms，各 7 次且输入/输出不同，不说明加速。Embedding、Milvus、DB 和 MQ 新访问为 0。
- **兼容性与限制：**MySQL `JSON NOT NULL` 可表示 `[]`，服务读写及非空历史 READY 的代码路径/Mock 通过；没有执行真实 MySQL 空数组写入、真实历史库读取或普通后端全量。原前端空数组仍为空白区块。历史 33 行只核对既存前后快照散列，本轮没有实时数据库复核；不推断其他进程的实时修改。
- **证据、保护和版本：**[冻结计划、14 份响应与 Java 回放、费用、失败日志及散列核验](../../backups/REPORT_CONTENT_20261116_ua0rf2j0/) 保存在被 Git 忽略的私有目录；候选有完整补丁但无提交 SHA。工作区 HEAD `0509cd6729c8dc6e45f35c9aef2b49985c59bf75`，本地缓存同名远端分支相同；未实时核验远端，未暂存、提交、推送、合并或部署。旧评分 v4 散列、已拒绝旧 Prompt 补丁及原快照散列均未变。后续需要独立人工裁定和未调参真人确认留出；学习入口 [L-P2-REPORT-TRUST-01](SOURCE_LEARNING_BACKLOG.md#l-p2-report-trust-01) 仅登记，掌握未确认。

<a id="p2-scoring-quality-20261116"></a>
### P2-SCORING-QUALITY — 评分质量评测可行性及有限基线（2026-11-16）

- **状态与目标：**现有评分链的离线方法工程试点可行；规模化正式评测、评分内容质量及生产发布为 **NO-GO / 证据不足**。本轮不修复生产 Prompt、代码、题库、RAG、数据库或历史评价。[专项报告](../evaluation/2026-11-16-scoring-quality/REPORT.md) · [协议](../evaluation/2026-11-16-scoring-quality/PROTOCOL.md)。
- **范围与根因：**evaluation 只读快照为 63 题、298 启用点；当前 local 服务目标为 `ai_interview`，读取其进程凭据后访问正式库的操作被自动审批拒绝，当前运行题库数量未知。复合评分点的 Boolean 不能表达部分态，名称要求与点映射、任选方案和事实纠正需独立人工裁定；校验器不保证自由文本事实正确。
- **数据与调用链：**冻结 48 条候选回答（开发 32、留出 16，按题隔离），保留 12 张历史卡与两次 ACID 用户判断。通过 `EvaluationPromptBuilder → DeepSeekEvaluationClient → LlmEvaluationValidator → EvaluationScoreCalculator → FollowUpPolicy` 重用 5 条历史固定证据，实际 12 次 HTTP 200、JSON 12/12、Java Validator 12/12；这不是实时 RAG 或业务持久化测试。四项经用户确认的点级断言在重复输出中匹配 11/11，不能外推整体准确率；无人工总分区间，留出 0/16 已执行。
- **已见问题：**A02 的已答一致性一次被文字列为缺失，三次未提示缺少名称并将非必答例子写入缺项；M05 的 449 两次未认可独立 removeIf；CARD-06 的额外 JOIN 错误两次未被纠正，分数 93/98。H01 的 552 完整布尔真值待裁决。24 个可重复比较的答案—点评价中 452 翻转 1 次；最大同答总分极差 15。部分文本判断仍为 AI 技术复核候选，不冒充人工金标。
- **验证与费用：**本轮 Maven Unit/Mock/Mock HTTP **97/0/0/0**；新增离线 Python 最终 **10/0/0/0**，含 SQLite 内存反例与计划、标签、追答目标未知、求和、ID 及未知响应/usage 的失败记录检查。真实 DeepSeek 输入 49,513、输出 6,898、合计 56,411 tokens；按当时非高峰费率估算约 ¥0.0306922，实际账单未知；0 Embedding、0 Milvus 调用。首次冻结计划比较在 HTTP 前失败并修复；追答盲卡缺失目标 ID 的 v1 表示经保留后在 v2 修正为未知，未影响实际调用；历史 Q2 失败及未知账单仍保留。[失败记录](../evaluation/2026-11-16-scoring-quality/FAILURES.md)。
- **变更与版本：**新增 `tools/scoring-quality-20261116/` 只读盘点、有限探针、候选集及汇总/测试；新增专项报告、协议、逐题 CSV 和脱敏指标，`.gitignore` 保护快照及候选答案，原始模型证据在私有 `backups/SCORING_QUALITY_20261116/`。四份工程档案只追加。本地 HEAD `0509cd6729c8dc6e45f35c9aef2b49985c59bf75`，本地缓存上游相同；远端未实时核验，本批未暂存、提交、推送或部署。学习入口 [L-P2-SCORING-QUALITY-01](SOURCE_LEARNING_BACKLOG.md#l-p2-scoring-quality-01) 待本人确认。


<a id="p2-reliability-options-20261116"></a>
### P2-RELIABILITY-OPTIONS — 低成本评分/报告方案甄别（2026-11-16）

- **结论：**本轮无可采用候选；不应用任何补丁。A低档思考2例保留M05正确覆盖但仍漏JOIN附加错，耗时8.245/14.579s；B单次先逐点与附加事实后五维有局部文字改善，但M04真实响应非法JSON触发预冻结淘汰。报告来源投影仅机械可行，不能据引用保证语义。[完整报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-16-AI评分低成本可靠性方案甄别与验证.md) · [证据索引](../evaluation/2026-11-16-reliability/README.md)。
- **问题和调用链：**现行客户端固定thinking.disabled，官方Flash/4096/3s/60s；评分`Builder→Client→Validator→ScoreCalculator→FollowUpPolicy/TargetResolver`。报告FINAL查询已有逐点结果而`toPromptEvaluation`不传；上游correction混合错误与拓展且可能有错。保持冻结RAG、题库、五维求和与业务决策。
- **隔离实现/取舍：**HEAD导出副本的A改Properties/Client/ClientTest，默认false且评分独立开关，报告不变；B仅Builder/Test，既有JSON字段重排和核对说明，56增39删包含示例移动。实际仅2个真实候选，低温/RAG/二次调用未实测；不重用历史清空弱项的失败报告候选。两补丁只作淘汰实验归档。
- **实测证据：**18应用层attempt含1条环境UNKNOWN，17份HTTP200/stop，16 Java有效。BASE7/7响应有效（另1未知）、A2/2、B7/8。相同输入且共同有效6对，窄判据BASE2/6、B5/6；另M04结构反向失败不得剔除。M05无同轮BASE，449认可只作历史对照。A02仍漏正式名称；B错误追问前提被Java拦住；JOIN纠错不改变同轮correctness20/总分99。不外推准确率，重复0，holdout0/16调用且仅hash。
- **成本：**已知输入72345、输出11730（含3018推理）、总84075，非高峰缓存估算¥0.09028836；未知预留21512token/¥0.0676。保守总预算占用105587token/高峰¥0.24817672，非账单。BASE/B同7对输入+257/次，实际缓存估算+46.24%，全未命中+2.80%，配对HTTP差中位0ms，不称提速。无Embedding/DB/Milvus/MQ/服务访问。
- **测试T/F/E/S：**基线9类139/0/0/1，A同9类144/0/0/1（同一私有M1e样本条件跳过）；B8类138/0/0/0（运行组不同且重叠不可相加）；Java21操作断言7/0/0/0，报告Java离线5/0/0/0。报告21/21逐点结果、R3四缺项和H2纠错保留，但R5错误同样传递；R1+5363UTF8字节，Token/质量未测。全量/真实MySQL/报告模型/业务E2E均未执行。
- **失败/权限：**首条DNS环境失败保留未知且禁止重发；初始执行器漏JDK传输禁重试开关，后续补回，首条内部次数未证。具体载荷导出曾被自动审批拒绝；核验8条合成来源/技术语料、无用户评论秘密后正常复审通过。原请求/响应/失败不改写，非法JSON不修补。完成已冻结续跑批后复核门槛、取消追加重复与新增付费（M04后同批还有9条）；不为交补丁降低验收。
- **状态及保全：**主树src/测试保持HEAD，四档案仅追加，原未提交内容保留。SHA0509cd6729c8dc6e45f35c9aef2b49985c59bf75与本地缓存上游一致，未实时核验远端。候选已编码/编译/定向测试/有限真实评分，但未暂存/提交/推送/合并/部署，无本批提交SHA。学习[L-P2-RELIABILITY-01](SOURCE_LEARNING_BACKLOG.md#l-p2-reliability-01)待确认，未开展教学。


<a id="p2-mq-net-value-20261116"></a>
### P2-MQ-NET-VALUE — RocketMQ 工程净收益与后端收口（2026-11-16）

- **结论：CONDITIONAL GO。** 保留现有MQ/Outbox实现并停止本轮MQ功能开发；持久受理与恢复有价值，未证明MQ相对MySQL持久任务+本地有界执行的独立净收益，不宣称总体吞吐提升。AI评分v4、报告v1不变，语义质量尚未证明达标；不将完美AI作为后端收口门槛。[正式报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-16-RocketMQ工程净收益审查与后端收口.md) · [完整证据索引](mq-net-value-20261116/README.md)。
- **问题与归因：**旧同步/异步实验实际模型并发10/2，POST完成与受理混用；历史READY退化和失败保留。本轮区分POST、服务端最终afterCommit、客户端观察、首份内容、完整可见与闭环完成。事务/Outbox/状态机/CAS/executionVersion负责一致性、恢复与幂等，MQ负责独立传递/重投/消费分发。首次内容改善来自前端复用保存FINAL，不归功MQ。
- **本轮实现范围：**新增 `tools/mq-net-value-20261116/{run.py,analyze.py,PROTOCOL.md}`；run复用旧探针、强制loopback桩、冻结资源/输入/K/样本上限，analyze离线核对事件和配对并计算描述统计。新增证据目录与正式报告，四档案仅追加。生产/测试/前端/SQL未改，没有修复或新依赖；未实现方案C。
- **调用链与取舍：**`generateReportForApi → acceptReportTask → ReportOutboxWakeup/Dispatcher → ReportMqRuntime → ReportMessageConsumer → claimTaskExecution → generateClaimedReport → completeReportGeneration`；受理和最终提交分开，模型在事务外。保留500ms兜底和有界唤醒、4消费线程；不改模型/失败契约以获取更好数字。单机新系统可优先评估C，现有B切换需新验证，当前不擅自重构。
- **最小实测：**固定1秒桩、实际K=4、c1/4/8、各侧每层8测量+2预热，48/48测量与12/12预热成功、失败0；30/30完整请求配对，60桩调用。真实MySQL/MQ；非JUnit/真实模型/浏览器。POST中位A/B依次1031.828/10.815、1040.143/18.113、1544.565/21.505ms；DB提交1028.676/1045.212、1036.157/1074.971、1540.773/1612.036ms；闭环0.968/0.949、3.844/2.889、3.900/3.729份/s。小样本、背景行数不等、共享宿主和轮询相位，不给稳定P95或总体性能外推。
- **可靠性与成本：**当前候选已有真实重复消息/迟到markSent2/0/0/0、6次POST只1任务、Broker停复1样本4.377s；历史5组进程故障未在当前SHA重跑，模型返回后崩溃仍需显式重试。演示MQ两服务5点内存中位合计653.5MiB；应用RSS增约59～61MiB、线程中位增约53～59，短采样不当峰值。4次扫描/500ms理论8SELECT/s/实例；实测扫描Executor约9.50～10.32/s；不等同MySQL服务端执行数。运维/连接/任务保留有成本，HA/同组多实例尚未验证。
- **历史验证与完整性：**源码清单556/556、候选MQ原件22/22、M1-E770/770一致；E2E71/75一致，4可变日志/账号/令牌文件变化，未读取凭据正文。复用后端1950/0/0/35（1915执行通过）、真实MySQL194/0/0/0及单独回滚1/0/0/0、前端349/0/0/0、MQ2/0/0/0、唤醒9/0/0/0（5Mock/4真实DB）；有重叠不可相加。本轮Maven/Vitest/浏览器/全量均未执行，不把跳过计为集成通过。
- **失败、隔离与用量：**沙箱Docker访问失败及mqadmin容器内地址错误发生于测量前，更正后一次业务运行成功；原失败说明保留。只启停原专用3317/3318 test库及19876/20911 MQ，零DDL/清表，A/B各保留30个新合成会话；B30任务SUCCEEDED/SENT且offset30/30积压0。四容器恢复停止，原演示不动，不访问evaluation、不提取进程凭据。新增真实DeepSeek/Embedding/Milvus0，外部Token/费用0。
- **Git及剩余门槛：**HEAD及GitHub同名远端分支均0509cd6729c8dc6e45f35c9aef2b49985c59bf75，远端main为84e3489a85d0fb6c8b54ad2a130c52d7476fb897；候选已推送未合并。本批工具/数据/文档未暂存、提交、推送，无本批SHA。长期部署仍需精确目标迁移/排空回退、持久运行配置、备份恢复、资源预算、监控及人工处置；不是继续无限MQ开发。本人掌握待确认；[学习入口](SOURCE_LEARNING_BACKLOG.md#l-p2-mq-net-value-01)仅登记。

<a id="p2-local-deployment-closeout-20261116"></a>
### P2-LOCAL-DEPLOYMENT-CLOSEOUT — 单机部署、重启与研究档案（2026-11-16）

- **决策与边界：**受控本机演示 GO；本机长期持久运行和公网发布各自 NO-GO。保留 MQ/Outbox 的 CONDITIONAL GO、评分 v4 和报告 v1；不再无明确收益地调优 MQ 或扩大评分研究。本轮没有业务代码、评分、报告、数据库结构或 SQL 变更。
- **问题、原因和方案：**既有 Broker 已有独立 store/log 卷但重启策略为 no；前端运行在临时目录，后端依赖一次性模型转发器，历史 13 表备份未覆盖当前数据。复用原容器/卷，将 MQ 策略改为 unless-stopped，前端切换至候选工作区；新增固定本机目标及 JAR 哈希、活跃任务和转发器身份检查的 `tools/local-deployment/backend.py`，保留原有数据库、Collection、消息和偏移。拒绝 compose up 重建、旧卷迁移及引入新基础设施；本轮只追加部署文档和测试。
- **实际对象与停启：**`127.0.0.1:3308/ai_interview` 当前 14 表、任务表 17 列；运行 JAR `3346146f49fca7c7c604b2d70c3ebddc134d7d49a97467a07d80fe35c2fa07e0`，源码 HEAD `0509cd6729c8dc6e45f35c9aef2b49985c59bf75`。Broker/NameServer 空载 `mqshutdown` 后分别 exit 143、非 OOM，原容器启动并恢复策略，Broker offset 与 consumer offset 前后均 1/1、积压/在途 0；后端和 Vue 受控停启，同一既有 READY 报告哈希不变。MySQL/Milvus 共享其他隔离目标，本轮未重启。
- **实际验证 T/F/E/S：**后端普通全量 `1950/0/0/34`、真实隔离 MySQL+MQ 定向 `2/0/0/0`（模型替身）、启动器 Python `8/0/0/0`、前端 `349/0/0/0`；typecheck、lint、前端 build 和后端 package 通过。不同运行有重叠，不相加。另有 1 次提交后发送前受控 kill：QUEUED/PENDING、0 报告经候选 JAR 和真实 MQ 恢复 READY/SUCCEEDED、1 报告、1 次弱项累计；再重启及 READY 重放无重复，模型桩 1 次。受理阶段直接调服务方法，GET/重放经 HTTP；不冒充本轮完整新会话 HTTP 或真实模型。
- **备份与不足：**当前 14 表 InnoDB 逻辑备份 `backups/LOCAL_RUNTIME_20261116/mysql-backups/ai_interview-20261116T132122.sql`、0600、SHA-256 `fea91a5df65ac3e164ed513aea96bad4793d6bb449ccdb29fdaee9f6668f796d`；未在独立目标恢复，也无跨 MySQL/Milvus/MQ 一致快照。现有一次性模型转发器无法自动跨机器重启，独立本机供应商凭据启动未真实验证；无自动告警、日志轮转、断电及离机恢复证据。
- **失败与保全：**首次只读 SQL 错列名触发 MySQL 1054，改为实际 `status`；`py_compile` 首次写系统缓存被沙箱拒绝，后改 AST/隔离执行；仓库级 diff-check 指向原有题库笔记末尾空行，未改该文件。原始测试、停启、数据库及私有报告读取证据在 `backups/DEPLOY_CLOSEOUT_20261116/`。本轮 DeepSeek/Embedding 调用、Token 均 0，未查供应商账单。
- **档案、版本及下一步：**[工程证据入口](2026-11-16-本机部署收口与工程档案.md) · [正式报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-16-二期单机部署收口与工程研究档案.md) · [运行手册](../../tools/local-deployment/README.md)。本地缓存上游同 SHA、未实时 fetch；新文件和档案未暂存、提交、推送或合并，无本批提交 SHA。历史记录和既有修改保留，个人掌握未确认。仅当要长期运行或独立发布时，另补本机专用供应商配置、精确授权的隔离恢复与发布准入；不将其扩展为当前业务重构。

<a id="p2-final-freeze-git-closeout-20261116"></a>
### P2-FINAL-FREEZE — 隔离恢复、证据核对与 Git 收口（2026-11-16）

- **目标和范围：**收口本机作品集，不继续 MQ 性能或 AI 质量研究。业务 Java、前端、评分 v4、报告 v1、MQ 和既有业务 Schema 均未修改；已有用户改动与原始实验保留。本次独立空容器内恢复备份所执行的建库及建表仅属于临时演练，不触碰现有业务库。
- **问题与决策：**此前 14 表逻辑备份只有导出和散列，没有独立恢复证据；使用同版本 MySQL 8.0.45、全新无网络实例和仅内存的 `/var/lib/mysql` 导入，不复用原库、容器或卷。原文件 0600、SHA-256 `fea91a5df65ac3e164ed513aea96bad4793d6bb449ccdb29fdaee9f6668f796d` 前后不变。14 张 InnoDB 表的行数、14 项 `CHECK TABLE` 与重导出的逐表数据元组散列 14/14 一致，任务表 17 列；结束后容器停止并保留，临时数据随停止消失。[私有执行记录](../../backups/GIT_FREEZE_20261116_a7d4c2/restore-result.json) · [范围及手册](2026-11-16-本机部署收口与工程档案.md#最终交付冻结与恢复演练)。未验证恢复库上的应用读取、Milvus/MQ 或跨存储一致性，也不是断电或离机恢复。
- **源码及测试关系：**HEAD `0509cd6729c8dc6e45f35c9aef2b49985c59bf75`，当前生产／测试源码、前端、SQL 和 `pom.xml` 相对 HEAD 无差异；GitHub 远端同名分支实时核对相同，远端 main `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，候选已推送分支、未合 main。运行 JAR 散列 `3346146f49fca7c7c604b2d70c3ebddc134d7d49a97467a07d80fe35c2fa07e0`，不是此前独立重建但未部署的不同散列 JAR。本轮未重复后端全量、前端全量、真实 MQ 或真实模型；复用前次后端 `1950/0/0/34`、前端 `349/0/0/0`、真实 MySQL/MQ 定向 `2/0/0/0`（模型桩），不同运行有重叠，跳过项不视为真实服务通过。
- **本次实际验证 T/F/E/S：**部署启动器离线 `8/0/0/0`；评分证据工具离线 `10/0/0/0`，其中部分读取被忽略的本机冻结样本；34 个未跟踪 Python 工具文件通过 AST 语法检查，4 个 CJS 文件通过 `node --check`，部署 Compose 配置解析通过，均不等于真实业务执行。前端 `/` 200、未认证后端接口 401、Milvus health 200 只是存活边界检查。无真实 DeepSeek 或 Embedding 请求，新增 Token 与供应商费用 0，未查询账单。
- **完整性和隐私：**MQ pilot 私有原件 75/75 散列一致；本批文档编辑前，其补充索引 67/71 和评分索引 69/73 的 4 项不一致均是此前持续追加的四份工程档案，旧索引为历史快照，不改旧值。最新部署清单在本次文档编辑前 20/20 一致；本批追加后历史清单中的可变文档自然不再匹配。部分原始答题、模型响应、数据库备份、绝对路径及报告仅本机可见，不能把索引当成原件或公开许可；历史两个临时测试日志链接缺失，不伪造补回。历史交接标明时点；其 zip 保持私有且未重打包。
- **后续完整性复核：**本批文档编辑后 MQ 补充索引为 63/71；8 项变化分别为四份持续追加的工程档案、三份时点提示及 MQ 索引 README。评分索引仍为 69/73；5 份历史清单共 102/102 项、M1-E 主索引 770/770 项及收口索引 132/132 项均一致。[本机复核记录](../../backups/GIT_FREEZE_20261116_a7d4c2/archive-integrity.json)。候选批次不含部分本机私有索引和旧材料，公开检出不能据此独立复现完整证据。
- **Git 收口：**入场 9 个已跟踪文件修改、91 个未跟踪文件，暂存区空；`HARD_AUTHOR_NOTES_20261114.md` 只有用户原有末尾空行，整树 `git diff --check` 因此提示，未擅改。建议部署工具与档案／脱敏证据分两批精确选文件；旧实验目录存在本地测试口令及依赖忽略脚本的情况，不整目录暂存。具体路径与候选边界见[最终验收报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-16-二期最终交付冻结与Git收口验收.md)。本批无提交 SHA，未暂存、提交、推送或合并。
- **最终裁决：**受控本机作品集演示 GO；长期运行、公网发布各 NO-GO。MQ CONDITIONAL GO：受理有优势，完整报告／闭环吞吐未证明改善，数据库与 Java 承担主要一致性，MQ 另有资源和运维成本；数据库持久任务＋有界执行器仅是未实现替代。真实评分误判、人工裁决修订、失败 Prompt 和内容未准入均保留；开发者本人掌握仍待确认。

<a id="p2-final-git-commits-20261116"></a>
### P2-FINAL-GIT-COMMITS — 两批本地提交收口（2026-11-16）

- **范围与实现：**业务源码、前端、SQL、评分 v4、报告 v1 及 MQ 实现保持冻结；部署批包含 `tools/local-deployment/` 的运行手册、启动器、安全测试及两个 RocketMQ 配置快照。档案批仅收录本机部署与研究索引、MQ 脱敏指标和故障矩阵、AI 质量及人工裁决摘要，并新增[未采用 Prompt 改动](../evaluation/2026-11-16-scoring-quality/FAILED_PROMPTS.md)；原始答案、模型响应、数据库备份和含口令实验工具不入批。四份既有档案原内容保留，本次仅追加状态。
- **版本与提交：**业务及测试源码基线 `0509cd6729c8dc6e45f35c9aef2b49985c59bf75`；部署批本地提交 `38f8ac9eac4d92581bb082fa93a5a8dded16e930`。本记录所在档案批的实际 SHA 与文件清单以 `git log -1`、`git show --name-status` 为准，避免在提交内容中自引用 SHA。提交前实时核对远端候选仍为 `0509cd6729c8dc6e45f35c9aef2b49985c59bf75`，远端 main 为 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`；两笔收口提交仅在本地，不代表已推送或合并。
- **本轮验证：**启动器离线 `8/0/0/0`（总数/失败/错误/跳过）；Compose 配置解析通过。后端监听进程打开的候选 JAR SHA-256 为 `3346146f49fca7c7c604b2d70c3ebddc134d7d49a97467a07d80fe35c2fa07e0`；前端 200、未认证后端 401、Milvus health 200，仅证所测入口可达。MySQL、Milvus、演示 MQ 容器运行状态为只读快照，不等于新端到端测试。
- **历史证据与局限：**复核原日志，基线后端全量 `1950/0/0/34`、真实 MySQL/MQ 定向 `2/0/0/0`（模型替身）、前端 `349/0/0/0`；均非本轮重跑，测试集有重叠，跳过不计作集成通过。MQ pilot 散列 `75/75` 匹配；补充索引 `63/71`、评分索引 `69/73` 在本批最终文档追加前核对，其余是可变档案的历史快照变化，不改旧值。后续追加将使更多引用当前文档的旧散列过期。48 条测量的 POST 与 DB READY 中位数按 CSV 复算一致；非新性能实验。
- **保留结论和下一步：**MQ 为 CONDITIONAL GO：异步受理中位约 11–22 ms，但完整 READY 和闭环吞吐未优于同步；MySQL/Java 承担事务、Outbox/CAS/幂等，MQ 提供投递与重投，另有约 653.5 MiB 消息服务内存中位成本。DB 持久任务加有界执行器仅为未实现替代。私有材料及部分绝对路径无法由公开检出独立复原，外发前须复查隐私和链接；本机受控演示 GO、长期运行和公网发布 NO-GO，内容质量未准入。本轮没有模型 API 调用，也未开展源码教学或确认个人掌握。
- **提交前链接及敏感内容检查：**对档案批暂存的 Markdown 全文做静态链接检查，发现 124 个“文件—本机绝对目标”组合、其中 40 个不同的仓库外绝对目标；另有 16 个私有备份目标及 68 个在本机存在但不在索引内的其他目标。这包含旧文档内容和重复引用，不是新增文件或原件丢失数。候选文件未检出常见私钥、供应商密钥、JWT 或硬编码口令模式，但模式扫描不是完整脱敏证明；公开推送前须重新逐项审查，不能为了修复链接上传原始材料。

<a id="p2-public-push-20261116"></a>
### P2-PUBLIC-PUSH — 公开范围与远端核验（2026-11-16）

- **用户公开决策：**普通本机路径、Git 作者邮箱、工程研究和失败实验、脱敏日志及合成数据可公开；API Key、密码、JWT、私钥、私人原始回答、真实用户隐私、数据库私有备份及敏感日志仍不得公开。此前关于本机路径本身须作为推送前阻塞的判断，由本次明确授权调整；私有证据链接不等于授权上传原件。
- **推送前检查：**仅复核从 `0509cd6729c8dc6e45f35c9aef2b49985c59bf75` 到 `5f1240f27a16cd5c2359a401b13a066a80188525` 的 37 个文件；私钥、常见供应商/云凭据、JWT、带凭据 URL、Bearer 和口令赋值模式无命中；本机配置中 6 个敏感字段的值逐项比对亦无匹配，未输出值。人工裁决摘录对应合成实验及用户技术复核摘要，私人原答、响应、备份和受保护配置未纳入。扫描不能证明所有未知形式的敏感信息均不存在。
- **部署依赖与验证：**`tools/rag-d3/config.py` 已在仓库。启动器仍为既有本机演示工具：指定 JAR、`.env`、数据库及容器由本机提供；默认模式还需私有转发器材料，`--model-config` 可替代转发器但不能替代 JAR 和本机资源。干净检出不能直接复现，私有文件不随仓库公开；[手册](../../tools/local-deployment/README.md)明确此边界。实际本机 `--check` 通过、活跃任务 0；启动器离线测试 `8/0/0/0`，另以模拟缺少配置和 JAR 的两项定向检查 `2/0/0/0` 验证先于模型及数据库访问而拒绝；Compose 配置解析通过。未启动或更改服务，未调用模型。
- **实际远端结果：**SSH 普通推送首次因代理未加载身份而以 `Permission denied (publickey)` 失败；随后使用已登录 GitHub CLI 的临时 HTTPS 凭据正常推送，GitHub 实时核对远端候选分支与 `5f1240f27a16cd5c2359a401b13a066a80188525` 一致，包含 `38f8ac9eac4d92581bb082fa93a5a8dded16e930` 和 `5f1240f27a16cd5c2359a401b13a066a80188525` 两批。仓库 `origin` 地址未改，未强推、合并或部署。本条及手册的后续文档提交身份和远端状态，以 `git log` 与远端实时核验为准，不能在自身提交正文中预填自身 SHA。
- **证据与发布限制：**仓库公开汇总数据、技术决策、脱敏案例及失败 Prompt；原始回答、模型响应、私有清单、日志、备份和实验工具仍保留本机，完整研究链不能仅凭公开检出复算。业务源码、前端、SQL、评分 v4 和报告 v1 未变，本轮不重跑历史全量；历史后端 `1950/0/0/34`、真实 MySQL/MQ `2/0/0/0`（模型替身）、前端 `349/0/0/0` 仍按原版本和范围引用。本机受控演示 GO，MQ CONDITIONAL GO，长期运行、公网部署与 AI 内容质量未获准入；没有开展源码学习或确认个人掌握。

<a id="p2-final-pr-review-20261116"></a>
### P2-FINAL-PR-REVIEW — Draft PR 与合并准入（2026-11-16）

- **范围与版本：**[Draft PR #12](https://github.com/kun520c/ai-interview-system/pull/12)，基线 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，候选 `1a425fa5f7939907c5d3468449936d44daa413fd`，`main...HEAD` 为 7 提交、97 文件。仅审查代码、历史证据和配置，不修改业务、评分 v4、报告 v1、MQ 或 SQL，不执行迁移。此前 64 个未提交文件及私有原件保留。
- **P1 — 默认行为与接口不兼容：**`report.async.enabled=false` 是默认值；`InterviewReportService.generateReportForApi → InterviewReportTransactionService.acceptReportTask` 对未生成或失败报告返回受控 HTTP 400，不再调用 main 上的同步生成路径，已有 READY 可读。启用异步后，新请求的 HTTP 200 表示 GENERATING、报告字段为空，旧同步接口成功则返回 READY；现有前端已轮询，其他假设 POST 直接包含完整报告的客户端须适配。该行为已在 README 标明，仍改变原默认业务能力和成功响应语义。合并前须决定保留兼容的同步回退，或明确接受异步为必需条件并在实际切换前完成目标迁移、MQ 及启用配置；不能直接把开关改为默认开启而假定依赖已就绪。本轮未实施任何修复。
- **P1 — 历史 GENERATING 的切换：**旧同步版本崩溃遗留的 GENERATING 若无新任务，启用异步时当前代码记录 `REPORT_HISTORY_REQUIRES_RECONCILIATION` 并返回 GENERATING；关闭异步时则直接返回 GENERATING。扫描器只扫描任务表，前端检查仅 GET，无法自行创建新 attempt。旧版提供 stale reclaim。已知切换方案要求停写排空并逐项对账；存在遗留行时须先明确经授权的安全处置，不能批量改状态、无条件重发或假设已恢复。本轮未访问目标数据，遗留行是否存在未知。
- **一致性审查：**受理同事务更新 Session 并插入任务；派发以租约 CAS 保护，发送在事务外，重复和不确定回执可重投；消费以 Session→Task 锁顺序及 attempt/executionVersion 隔离，报告、弱项和终态在同一事务提交。RUNNING 崩溃可能超时 FAILED、需显式重试和再次调用模型。接口公开状态为 NOT_STARTED/GENERATING/READY/FAILED，Outbox 的 PENDING 是内部投递状态，并非前端 API 状态。
- **本轮验证 T/F/E/S：**报告受理、状态、消费者、派发、唤醒及配置的 8 个 Maven Unit/Mock 类 `100/0/0/0`，无真实外部服务；GitHub Actions 的 Vue typecheck、lint、构建通过，单测 `349/0/0/0`。GitHub commit status 无条目，仓库目前没有后端 Actions 工作流；不将本地测试称为后端 CI。源码版本一致，复用历史后端全量 `1950/0/0/34`、真实 MySQL/MQ `2/0/0/0`（模型替身）及 MySQL 单库恢复 14/14；均非本轮重跑，跳过不表示真实外部集成通过。
- **安全与档案：**扫描候选分支完整祖先的 1,983 个可达 blob，并核对本地已知敏感值；历史 Embedding 命中是禁用真实模型的示例占位值，Bearer 命中为测试替身或说明文本，未证实真实凭据泄露，因此未提出无依据的轮换结论。另查本地已知其他 origin 分支独有 blob；模式扫描不能排除未知格式。已核对公开 MQ 正反指标、资源成本、失败 Prompt、人工修订和单机恢复限制；私有原件不上传，公开检出不可完整复算。
- **P2 — 已公开的历史测试口令：**main 历史中的 `frontend/scripts/phase-9b-real-smoke.mjs` 会向本机 8080 注册使用固定测试口令的生成账号，脚本没有断言目标库隔离或清理账号；这是既有文件，不是本 PR 引入。已找到的原验收记录显示当次 `ECONNREFUSED`、未创建账号，其他运行及当前各库是否存在这些账号未知。另一个浏览器验收工具的固定测试管理员限定于独立测试数据库与私有 datadir，历史记录显示服务已停止。不要把这些测试值当真实供应商凭据；若旧 smoke 曾成功作用于非隔离目标，应在获准的确切目标核查并禁用或重置对应临时账号，不擅自查询或改动用户库。
- **决策与状态：**当前 **合并 NO-GO**，先解决或明确接受以上兼容性及切换条件；不把 GitHub 的可自动合并等同业务准入。MQ 仍 CONDITIONAL GO，本机受控演示 GO，长期运行、公网发布和 AI 内容质量未放行。此条及相关案例、简历证据、学习入口为本地文档追加，尚未提交或推送；未进行源码教学，个人掌握待确认。无本轮新增模型调用或数据迁移。


<a id="pr12-p1-compat-20261116"></a>
### PR12-P1-COMPAT — 接受异步接口变更并核对历史状态（2026-11-16）

- **日期／执行者／状态：**2026-11-16（Asia/Shanghai），Codex。已审查、编码、编译、定向验证；未打包、部署、暂存、提交或推送。基线 `1a425fa5f7939907c5d3468449936d44daa413fd`，对照 main `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`。前一条 PR 审查和原始实验完整保留，本条是后续状态更新。
- **发现／证据：**[PR #12](https://github.com/kun520c/ai-interview-system/pull/12) 描述列出两个 P1，本轮评论/review threads 均为空；源码对照确认旧同步路径和 stale POST CAS 存在于 main、新版已移除。[本批处置与切换说明](pr12-p1-20261116/README.md)、[只读 SQL](pr12-p1-20261116/report-cutover-readonly.sql)、[逐类验证及散列](pr12-p1-20261116/verification.json)为当前证据。原始日志与入场保护位于 `/Users/kun/Developer/projects/ai-interview-system/backups/PR12_P1_20261116/`，没有上传私有 XML/凭据。
- **P1-1／最终 ACCEPTED：**原同步 POST 成功返回 READY，新版 HTTP 200 只表示 GENERATING；默认 false 时首次/FAILED 生成不再可用，READY 仍可读。根因是 `acceptReportTask` 取代同步生成且将未启用、暂停受理/无生成器混为 BusinessException 400。用户明确允许接受异步必需条件；不恢复两套同步状态机、不做 MQ 故障同步降级、不默认开启 MQ。新增 `ServiceUnavailableException` 和 503 映射，两种原因分别为“未启用”与“暂不受理”；前端只精确识别已知 503，保留旧 400 消息及未知 5xx GET-first。解决的是错误分类和提示，**接受的是旧默认能力及成功响应语义的不兼容**。其他同步客户端/只认 400 的客户端尚未穷举，须配套升级。
- **P1-2／最终 RESOLVED（限获准目标核对项）：**旧 GENERATING 没有新任务时，新消费者/扫描器看不到，GET 可能长期等待。用户本轮明确允许现有容器 `ai-interview-mac-mysql-1`、`127.0.0.1:3308/ai_interview` 只读，并允许同实例 `ai_interview_test` 有界测试。16:26:16 本机演示库一致快照：READY 1、GENERATING 0、SUCCEEDED/SENT 1、活跃任务 0；无报告无任务、已有报告非 READY、仅失败/成功终态未同步、多活跃任务、Session/任务版本异常等均 0，情况 A。备选自动重建、批量修状态和复杂恢复均拒绝；保留不接管，补充 POST 版本不匹配日志及前端长期等待人工核对提示。**没有修复历史数据或恢复旧同步 stale reclaim**；其他数据库和未来时点未核查，须再执行切换门禁，存在异常则停止并逐项另取数据操作授权。
- **文件／调用链：**生产为 `common/exception/ServiceUnavailableException`（新增）、`GlobalExceptionHandler`、`InterviewReportTransactionService.acceptReportTask`、前端 `useInterviewReport.handleGenerateFailure` 与 `reportCopy`；4 个相关测试文件补契约/事务/前端反例。调用链 `Controller → InterviewReportService.generateReportForApi → acceptReportTask` 保留 Session→Task 锁顺序、同事务受理，最终报告/弱项/终态事务不变；`getReport` 与扫描器只读审查未改。配置/Mapper/Schema/Prompt 未改。`GETTING_STARTED` 加门禁，本目录新增说明/SQL/验证 JSON，四份工程档案仅追加；完整文件列表见本批说明。
- **实际验证 T/F/E/S：**Java 21.0.13、Maven 3.9.16、MySQL 8.0.45，实测 macOS 26.7.1 ARM64；后端 13 类共 **200/0/0/0**，200 通过，拆为 Unit/Mock/配置 106、MockMvc 35、真实 MySQL/MyBatis/事务 59，退出 0。Node 24.22.1/npm 11.19.1/Vitest 5.0.1，报告页 **65/0/0/0**、65 通过，typecheck/定向 ESLint 退出 0。含禁用四状态、启用受理、重复 POST、READY 重读、正常 QUEUED/RUNNING、无任务、仅终态、版本不符、多任务、已有报告不重放弱项；前端已知拒绝不显示成功、不自动 POST，未知 503 保持 GET-first。测试库涉及 9 表前后均 0，只清理本轮生成主键。
- **失败／未执行：**初次普通沙箱读取 Docker socket 被拒绝，随后按获准范围提升权限完成，非代码失败。测试首轮全通过，无降级检查、删测试或跳过失败。全量 Maven/前端、真实 MQ、供应商模型、Embedding/Milvus、evaluation、真实浏览器端到端未运行；本轮模型调用 0。0 跳过仅是选定集合，不等于 guarded Smoke 通过；不把历史全量当本轮结果。性能未测，MQ CONDITIONAL GO 和原失败证据保持。
- **Git／准入／后续：**本地与实时远端候选 HEAD 仍为 `1a425fa5f7939907c5d3468449936d44daa413fd`；所有本批改动仅工作区，暂存区为空，无本批 commit SHA。入场 68 个 dirty 文件保留，四份工程档案保留原文后追加，其余 64 项正文/mode 不变。当前本地修订满足本轮定向技术门禁；远端 Draft PR 仍是旧代码和 NO-GO 描述，须另获提交/推送授权，更新审查信息、核对新 HEAD CI 后才确认实际合并准入。未向 GitHub发消息。切换仍需旧执行者停止、备份、逐目标核对及迁移/部署授权；本轮没有批准或实施这些操作。
- **学习关联：**L-PR12-P1-01／02，以下清单仅登记入口；没有教学或本人独立解释、复现、修改证据。工程完成不等于个人掌握。本机受控演示 GO，长期运行/公网发布 NO-GO 不变。


<a id="pr12-final-regression-20261116"></a>
### PR12-FINAL-REGRESSION — 全量回归、普通推送与合并建议（2026-11-16）

- **任务／状态：**用户明确授权本轮审查、最终回归、逐文件暂存、普通提交/推送、更新 Draft PR #12，不授权合并或部署。17 个 P1 文件逐项及散列核对一致，未发现需要继续修复的新生产问题；旧 68 个 dirty 文件保护仍有效，四份共享档案原文保留，其余 64 个正文/mode 不变。学习只登记入口。
- **根因／选择／风险：**P1-1 的旧同步 POST→READY 被异步 POST→GENERATING 取代，默认关闭导致新报告不可生成；用户选择 **ACCEPTED Breaking Change**，不恢复双路径、不默认开启 MQ、不隐式同步降级。错误分类和前端明确拒绝处理已修正，但没有恢复同步兼容。P1-2 是旧 GENERATING 无任务无法被扫描；16:26:16 对获准 `127.0.0.1:3308/ai_interview` 只读快照异常 0，因此 **RESOLVED 仅限该核对项**，保留安全不接管。此次不重连业务库，不把旧快照写成新数据；其他目标切换前须重复核对。
- **代码／事务／范围：**调用链及文件同 PR12-P1-COMPAT；新增 503 的 ServiceUnavailableException、受理原因区分、前端精确拒绝和历史状态诊断，无新的数据写入路径。最终事务、弱项、MQ、Schema、评分 v4/报告 v1 均未再改动。本轮生产/测试源码与已验证候选相同，只补工程记录和发布证据。
- **实际全量 T/F/E/S：**2026-11-16 16:48:44–16:49:44，当前源码执行 `./mvnw -B -ntp package`，151 类 **1971/0/0/34**，1937 通过，退出 0、重打包成功；原 P1 定向 200 项包含其中且全部通过。真实 MySQL 仅 `127.0.0.1:3308/ai_interview_test`，14 表前后行数均 0，永久结构未变；现有迁移回归仅操作连接内临时表。Node24 本地前端全量 37 文件 **355/0/0/0**，355 通过，报告页65项包含其中；typecheck、全量 lint、build 全通过。环境版本及逐项证据见 [最终回归记录](pr12-p1-20261116/final-regression.json)。
- **跳过／失败／复用：**34 项为显式 opt-in 的外部服务、报告 MQ/崩溃探针和 AI baseline，未启用；不是集成通过。当前全量首轮通过，无删测试、放宽检查、失败重跑或新增真实模型/MQ。历史真实 MQ/模型替身、故障恢复证据继续保留，当前差异未触及其核心链路，故不重复性能/付费测试。后端日志中的合成故障注入并非测试失败。
- **Git 事实：**代码/测试9文件普通提交 `9a95e53b13fc77be27962696202dc14169f18248`，已普通推送到 `codex/phase2-git-closeout`；GitHub live PR head 逐字核对相同。远端 main 核对仍为 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，未合并。接续文档批9文件包括原8项及新 final-regression.json；其自身 SHA 和最终远端/Checks 由 PR 与本机发布证据核验，不伪造自引用 SHA。无 force push/rebase/业务库迁移或部署。
- **GitHub 源码检查：**[43834763837/job133188536470](https://github.com/kun520c/ai-interview-system/actions/runs/43834763837/job/133188536470) 全成功，Node22.23.4，Vitest355通过，typecheck/lint/build通过；Commit Status无条目。最终文档推送后继续检查该最终HEAD；后端全量是本地验证，不称为后端CI。
- **审查结论／限制：**新增 P0、未接受 P1 未发现；最终 HEAD Checks 通过后给出 **MERGE GO** 建议，可转 Ready for Review，按本任务范围仍保留 Draft，不直接合并。P2仍有后端CI缺口、main既有固定口令smoke的其他环境使用未知；仓库外客户端需适配已接受的异步/503契约。MQ CONDITIONAL GO、内容质量局限、受控演示GO、长期/公网NO-GO保持。
- **证据／学习／收口：**[说明第8节](pr12-p1-20261116/README.md#8-最终回归与发布记录2026-11-16后续任务)，本机 `/Users/kun/Developer/projects/ai-interview-system/backups/PR12_FINAL_20261116/` 保存完整日志、151 XML、DB前后、入场文件和推送/CI证据；首轮 evidence不覆写。关联 L-PR12-FINAL-01，掌握未确认。之后仅完成文档推送、PR说明和Checks核对，不扩展新业务任务。

- 发布前安全检查补记：精确本机配置值扫描首轮命中 GETTING_STARTED 中的既有禁用示例占位值；核对原 HEAD 与当前均仅出现 1 次，未新增真实凭据。原始命中与脱敏核查存于本轮 credential-scan-triage.json。保持其他精确值与凭据模式检查，不将占位值误报为泄露，也不将这次扫描中断计作测试失败。
