# Frontend V3：B1 / B2 最终修复验收

日期：2026-10-18（Asia/Shanghai）。结论：**READY_TO_MERGE**，仅表示本次技术验收通过。未 commit、push 或 merge。

本报告接续 [原集成报告](V3_INTEGRATION_20261018.md)。原报告及 `v3/integration/` 的 NOT_READY 结果、失败现场均作为历史记录保留；当前结论以本报告和 [本轮结果](v3/blocker-fix/RESULT.json) 为准。

## 1. 范围与基线

- 分支：`codex/frontend-v3-integration-20261018`；Worktree：`.worktrees/frontend-v3-integration/`。
- 原基线：`d0b7d59b5a135d78240a6188c2f1f08a4c69a302`，HEAD 未改写。
- 只修复模型响应分类、离线测试资源依赖及必要的验收目录配置。没有调整 V3 视觉或前端业务源码。
- REST 路径、DTO、SQL、数据库结构、面试状态机、幂等、恢复和报告聚合实现均未修改。
- 仅连接本轮私有 MySQL `127.0.0.1:13326/ai_interview_test`；未连接业务数据库或共享 Milvus，未调用收费模型。

## 2. B1 根因与修复

原 `EvaluationOrchestrationService` 调用 validator 后，模型评分越界抛出普通 `IllegalArgumentException`，缺失字段等抛出普通 `IllegalStateException`，最终落入通用 500 handler。

修复位置：

| 文件 | 改动与责任 |
|---|---|
| `LlmEvaluationValidator.java` | 仅明确的模型建议违规改用已有 `ExternalServiceException`；空 `EvaluationContext` 仍是内部调用错误，未改为 502 |
| `DeepSeekEvaluationClient.java` | 复制客户端自己的 ObjectMapper，拒绝字符串 / 布尔等标量强转以及小数截断为整数；不改变应用全局 ObjectMapper |
| `JevJudgeProvider.java` | 在反馈校验的局部边界捕获该外部响应异常，沿用原 `FEEDBACK_VALIDATION_FAILED` 重试 / 回退路径；内部异常直接传播 |

没有新增全局 `IllegalArgumentException` 捕获。`GlobalExceptionHandler`、Controller 和编排 / Workflow 不需要修改，继续复用已有 HTTP 502：`code=502`、`message=外部服务暂时不可用`。内部错误仍为 500，业务输入和状态冲突仍为 400 / 409。

`FullInterviewBusinessOfflineTest` 通过真实 Spring MVC / Security / MyBatis / MySQL / 事务验证状态码、响应体及落库行为，只将模型 HTTP 在内存中截获：

- 50 项非法输出：五维分别为 -1、21、null、缺失；另含小数、数字字符串、非数字字符串、布尔和数组。分别经过 DeepSeek 直接路径及真实 Jev Provider 的 DeepSeek fallback 路径，均为 502。
- 20 项合法边界：五维分别为 0 / 20，两条模型路径均接受，总分仍由 Java 求和。
- 3 项内部异常：普通 `IllegalArgumentException` / `IllegalStateException` / `NullPointerException` 均为 500；不新增自动模型请求。
- 1 项失败后恢复：原 requestId 改内容仍为 409，空答案仍为 400；相同身份显式重试成功，成功重放不再调用模型，只保留一份答案和评价。
- 失败时答案为 FAILED，无评价写入、无会话推进、无报告或薄弱点写入。已有真实 RAG trace 不伪装成“完全无数据库写入”。
- HTTP 错误体逐项排除合成的模型原文、回答、模型标识、Prompt 字段和真实测试 JWT。

该类最终共 76 项（含原有 2 项完整业务测试），全部通过。另保留并扩展 validator、客户端、Jev 重试及错误分类测试；内部 validator 缺陷不触发反馈重试或 fallback。请求预算、重试上限、回退条件、原生判分复用机制均未扩大。

## 3. B2 公开离线 Fixture

`JevE06ReliabilityReplayTest` 保留测试类，改为明确的合成协议回归，不读取私有历史 JSON，也不再把 33/38 当作公开测试必须维持的结果。

新增公开 classpath 资源：

- `src/test/resources/evaluation/jev-reliability-synthetic-scenarios.json`：6 个针对机制设计的场景。
- `src/test/resources/evaluation/README.md`：合成数据、既有 capture 和私有历史研究的证据边界。

合成上下文与原生响应按项目现有测试中的 request / rubric 构造方式生成；合法反馈复用已有 `JevFeedbackReliabilityTest.structuralFeedback`。原有 E05 / E06 captures 不修改、不复制进新矩阵、不重新标注来源。

| 合成场景 | Native HTTP | Feedback HTTP | Fallback HTTP |
|---|---:|---:|---:|
| 首次反馈合法，重试配置 0 | 1 | 1 | 0 |
| 非法反馈，重试配置 0 | 1 | 1 | 1 |
| 配置 1，第二次反馈恢复 | 1 | 2 | 0 |
| 配置 1，反馈耗尽 | 1 | 2 | 1 |
| 原生结果非法 | 1 | 0 | 1 |
| 耗尽后 fallback 评分非法 | 1 | 2 | 1 |

所有请求由 `MockRestServiceServer` 截获，额外或错误 URI 请求直接使测试失败。测试验证原生判分保持、Java 评分合法性以及精确调用次数，不能证明真实模型质量、延迟、费用或历史成功率。

私有历史文件仅在本集成 Worktree 中被保留到 `target/v3-blocker-fix/private-archive-preserved-NOT-USED.json`，原测试查找路径已不存在；没有用于本轮任何成功测试，也未进入 Git。原工作区历史文件、历史报告及原 Git 版本保持原样，研究记录仍可追溯。

审计了测试源码对磁盘 / classpath 资源的引用。剩余 `docs/evaluation/evidence` 引用只用于现有 opt-in `AiSystemBaselineEvaluationTest` 及其专用 `BaselineTraceConfiguration`，未被默认测试加载。全新检出的完整测试成功进一步证明默认路径无需手动补资源。

## 4. 最终验证结果

| 验证 | 实际结果 |
|---|---|
| 前端 Typecheck / Lint / Build | 全部退出码 0，未改变源码或 lockfile |
| 前端单元测试 | 36 个文件、293 项通过；失败 0、错误 0、跳过 0 |
| 定向修复复验 | 107 项，失败 0、错误 0、跳过 0 |
| 集成 Worktree 完整 Maven | 1741 项，失败 0、错误 0、跳过 19，BUILD SUCCESS |
| 无私有资源的新检出，默认 Maven | 1741 项，失败 0、错误 0、跳过 19，BUILD SUCCESS |
| 严格浏览器 `full` | PASS；49 条汇总检查；0 个未捕获页面异常 |
| 浏览器数据只读对照（完整回归前 / 后） | PASS；包含非法模型答案 FAILED、无评价 / 推进，以及重放和双击防重 |
| Git whitespace / 索引 / 来源保护 | 通过，见本轮证据 |

两轮完整 Maven 均实际执行通过 1722 项，19 个跳过均为原有 opt-in 真实服务测试（含 AI baseline），没有新增跳过或 Maven 排除。所有 `RUN_REAL_*` 与 `MILVUS_ENABLED` 在测试子进程中清除；测试 profile 和环境隔离初始化器保持启用。

### 严格 full 浏览器

命令：设置 `BROWSER_E2E_RUN_ID=20261018-v3-b1b2` 后运行 `node tools/browser-e2e/acceptance.mjs attempt-01 full`。只增加了运行目录配置；`assert.equal(invalid.status, 502)` 和原业务断言均未调整。

真实运行 Spring Boot、JWT、数据库当前角色、MyBatis、事务和业务流程。Embedding / Chat HTTP 使用本机确定性替身；Milvus 外部适配器继续使用明确的本地只读替身，不宣称真实 Milvus 或真实模型已通过。

桌面和手机各完成五道 MAIN、两道 FOLLOW_UP、评价确认、FINISH 刷新确认、报告、历史和详情；匿名 401、USER 管理 API 403、ADMIN 正常登录 / API 200 / 管理页面、跨用户 404 均通过。网络故障是在真实提交完成后丢弃浏览器响应，再按原身份恢复；不冒充物理长超时的所有可能情况。

恢复场景验证：两次原身份请求只有一份答案 / 评价 / Retrieval Batch，双击只有一次请求；非法模型评分实际 HTTP 502，刷新后保留原提交内容，FAILED 且不推进。

浏览器共 115 次 API 请求、18 次答案 POST、2 次报告生成。36 次模型 HTTP 全部为本地替身（17 Embedding、19 Chat）；收费模型调用 **0**。

## 5. 全新检出复现

最终候选源码树：`65830dfd49f7fe6650aad6d62a43dbd5521e11a7`。

通过复制索引到 `target/v3-blocker-fix/candidate-final.index`，仅在这个临时索引中加入修复及公开资源，再生成候选树和 `git archive`。真实索引在导出和测试期间保持完全一致。候选归档在新的 `clean-checkout-verified/` 中展开，并初始化独立 Git 环境；没有 commit，没有关联或替换任何既有 Worktree。

启动前确认：无私有历史 JSON、无 `application-local.yaml`、无旧 `target/`。执行的命令为 **`.\mvnw.cmd -B -ntp test`**，没有 `-Dtest`、测试排除或额外 skip。仅给子进程提供 JDK 21、本轮私有 test DB、空密码属性和 SerialGC / 内存设置；未改 POM 或生产配置来绕过隔离检查。

成功 XML 汇总 1741 / 0 / 0 / 19；候选树与最终暂存的全部 Java、测试资源、前端、构建配置和运行脚本按索引内容核对。后续仅增加报告、导航说明和证据，不改变已验证代码。

## 6. 修复过程中的失败记录

- 最初测试编译有 2 个 checked-exception 声明错误，尚未执行测试；修正测试响应 helper 的声明。
- 定向首轮 258 项、2 failures、74 errors、0 skipped：JSON 缺省 / null 对照及重设 Mockito 桩时旧桩执行导致的测试代码问题；分别改为 typed 原生响应精确比较和 `doAnswer`，复验 107 项全通过。
- 全量首轮 1741 / 1 / 0 / 19：一项原测试仍期待模型缺失评分点抛内部异常。仅同步为新的外部响应类型，完整拒绝条件和具体消息断言保留；全量重跑通过。
- 第一次新检出在继续执行时未先确认隔离服务存活，私有 MySQL 已退出，连接被拒绝；中断时 XML 为 610 / 0 / 48 / 1。该记录标为执行环境中断，未当作代码回归。只停止该检出的 JVM、恢复原私有 datadir，重新展开同一候选树到另一全新目录后，默认全量通过；没有改配置或测试规避失败。

上述现场和日志均保留，不以最终成功覆盖早期失败。

## 7. Git 与原工作保护

原 229 个暂存路径全部保留。221 个原 blob 内容未改；8 个已有路径仅追加运行目录支持或导航 / 操作说明（6 个运行工具、工具 README、docs 导航）。33 个已移植的 V3 前端文件逐字节未改。原索引及原暂存二进制 diff 已单独保存，最终修复完成后才暂存必要增量。

原工作区 6299 个文件与原 V3 Worktree 863 个文件的 SHA-256 均无变化，Git 状态与之前保护快照一致。没有 reset、清理或删除任何 Worktree；没有操作远程 main。候选树只是 Git tree 对象，不是提交。

新公开资源、修复代码、本报告和证据进入 Git 索引；私有历史 JSON、MySQL 数据、构建目录和环境凭据不进入索引。共享 exclude 规则保持不动，文档仅按明确路径 `git add -f`。

私有服务已按 PID、创建时间、可执行文件核对后停止，端口释放；datadir、两轮新检出、历史失败和本轮证据全部保留。

## 8. 交付索引与剩余边界

- 核心修复涉及 3 个生产类、相关测试、公开 Fixture 及运行目录支持；发布时以 Git 提交 Diff 为审查入口。原 `B1_B2_FINAL_SOURCE.patch` 在本地归档保留，避免在仓库重复存储源码。
- [结构化结果](v3/blocker-fix/RESULT.json)、[完整变更清单](v3/blocker-fix/change-list.json)。
- [集成 Maven](v3/blocker-fix/integration-maven.json)、[新检出 Maven](v3/blocker-fix/clean-maven.json)、[候选树来源](v3/blocker-fix/candidate-final-export.json)。
- [严格 full 浏览器](v3/blocker-fix/browser-full.json)、[回归后数据库一致性](v3/blocker-fix/consistency-after-regression.json)、[实际 502 HTTP](v3/blocker-fix/invalid-model-http.json)。
- [原工作区保护](v3/blocker-fix/original-workspaces-preserved.json)、[原暂存区保护](v3/blocker-fix/staging-preservation.json)。
- [最终源码与索引一致性](v3/blocker-fix/final-source-index-proof.json)：554 个代码、资源和构建文件匹配已通过默认测试的新检出。

完整日志、所有截图、两份 SQL 快照和原始 Surefire XML 在本 Worktree 的 `target/v3-blocker-fix/` 与 `target/browser-e2e/20261018-v3-b1b2/`。

B1、B2 均已关闭，未发现需要扩大业务逻辑范围的新缺陷。真实模型质量、真实 Milvus、Firefox / Safari、实体手机及线上部署不属于本次通过结论。下一步由用户审查并决定是否提交、推送和合并；本轮仅标记 READY_TO_MERGE。
