# Frontend V3 最终集成准备（2026-10-18）

## 结论：NOT_READY_TO_MERGE

V3 已安全移植到最新 GitHub main，前端工程检查及真实 Spring Boot / 独立 MySQL 的正常业务联调通过。**严格全量浏览器验收仍失败**：main 在拒绝越界模型分数后返回 HTTP 500，未按已有外部依赖错误合同返回 502。用户明确选择“保持 main 后端不动，列为合并阻塞”，因此没有移植原工作区中的后端修复。

另有干净检出的测试资源缺口：main 的 `JevE06ReliabilityReplayTest` 依赖未发布的历史证据 JSON。补齐本地资源后的 Maven 成功不能证明干净检出可完整复现。

## 集成身份与工作区保护

- 集成分支：`codex/frontend-v3-integration-20261018`。
- 独立 Worktree：`.worktrees/frontend-v3-integration/`。
- 基线：`d0b7d59b5a135d78240a6188c2f1f08a4c69a302`；开始时 fetch 和结束时 `ls-remote` 均确认该提交是远程 main。
- 原 V3 分支仍为 `codex/frontend-v3`，基准 `6076a70`；未合并、rebase 或 cherry-pick 旧分支。
- 初始登记的 4 个 Worktree 全部保留，另增加本次集成 Worktree。未 reset、覆盖、清理或删除既有工作。
- 原工作区 6299 个文件、原 V3 Worktree 863 个文件的 SHA-256 复核均无变化，两处 Git porcelain 状态与开始时一致。两处原有未提交修改完整保留。
- 集成内容暂存供审查；**未 commit、未 push、未合并 main**。原分支与远程 main 均未改写。

## 补丁与哈希验证

交付包 SHA-256：`eec51c5eaa2e56f965d4b37a3ef9fe455938dde164690ad49bd1af0368ccf1cf`，与附带校验文件一致。ZIP 在独立审计目录解包前检查了路径边界。

交付清单包含 32 个修改文件和 1 个新增 QA 脚本。新 main 的 32 个检出文件均为 CRLF，而前置清单采用 LF；逐文件去除 CRLF 差异后，**32/32 前置哈希匹配**，新增脚本在 main 中不存在。交付包内其余前端文件与 main 经相同换行核验后也全部相同，未发现需要冲突合并的新业务逻辑。

先执行 `git apply --check`，再应用交付包内 `V3_SOURCE.patch`，未使用强制覆盖、忽略上下文或失败片段。应用后先证明每个文件与包内源码只可能有换行差异，再恢复包内对应文件的换行形式；**33/33 原始 after 哈希匹配**。

之后仅额外移除 `InterviewReportView.vue` 一处空白行上的两个空格，以通过 `git diff --check`。该文件的最终原始哈希因此不同于原交付清单；其余展示逻辑没有修改。最终源码重新执行了全部四项前端工程检查。Git 可能在检出时转换源码换行，跨平台内容核验应比较 LF 规范化后的内容；归档目录新增 `.gitattributes`，保证原始补丁、清单和旧证据字节不被 Git 换行转换。

## 变更范围与文件用途

| 范围 | 文件 / 入口 | 用途 |
|---|---|---|
| V3 前端 | [原交付文件清单](V3_FILE_MANIFEST.md)中的 30 个生产展示文件、2 个测试文件和 1 个 QA 脚本 | 原样移植视觉改版，不重新设计 |
| V3 资料 | `V3_OPEN_SOURCE_RESEARCH.md`、`V3_DESIGN_SYSTEM.md`、`V3_VERIFICATION.md`、`V3_FILE_MANIFEST.md`、`v3/` | 保留设计依据、补丁、哈希、Mock 截图及历史验证记录 |
| 联调启动 | `tools/browser-e2e/environment.ps1`、`BrowserE2eServer.java` | 新 MySQL 实例、固定回环端口、真实 Spring Boot；校验端口、进程身份及私有 datadir |
| 合成依赖与数据 | `tools/browser-e2e/mock-provider.mjs`、`fixtures.sql` | 明确标记的本地 AI 响应和独立测试题库，不读取业务库 |
| 浏览器验收 | `tools/browser-e2e/acceptance.mjs` | 真实表单输入与点击；桌面、手机、恢复、鉴权；失败即退出，保留所有 attempt |
| 一致性与阻塞诊断 | `tools/browser-e2e/verify.mjs`、`verify-blocker.mjs` | 持久化与 UI 对照；单独证明 500/502 阻塞及拒绝后的数据安全性 |
| 回归与说明 | `tools/browser-e2e/regression.ps1`、`README.md` | 不排除用例的 Maven 全量回归、独立日志与复现说明 |
| 本次报告与证据 | 本文件、`v3/integration/`、`docs/README.md` | 结论、失败和通过证据、导航与变更索引 |

这些联调工具基于原工作区已有的 `tools/browser-e2e/` 复制后调整，只存在于本次集成 Worktree。它们位于生产 `src/` 之外，不打包进应用。没有修改 `src/`、`pom.xml`、SQL、REST Controller / DTO、面试状态机、幂等或恢复实现；没有复制原工作区的后端未提交业务修改。

## 最终前端工程检查

| 检查 | 结果 |
|---|---|
| `npm ci --no-audit --no-fund` | 按已有 lockfile 安装，无新增依赖 |
| `npm run typecheck` | 退出码 0 |
| `npm run lint` | 退出码 0，0 errors / 0 warnings |
| `npm run test:unit` | 36 个文件，293 项通过；失败 0、错误 0、跳过 0 |
| `npm run build` | 退出码 0，vue-tsc 与 Vite 成功 |
| `git diff --check` | 通过 |

首轮沙箱 Vitest 为 285 项通过、0 项断言失败、3 个 worker 错误（依赖读取 / spawn EPERM），未形成完整成功结果。允许正常测试子进程后 293 项全部通过；最终源码再次完整通过四项检查。没有跳过或弱化原有测试。

## 真实后端联调的边界

真实运行：最新 main 的 Spring Boot HTTP、Spring Security、JWT、数据库当前用户状态 / 角色、MyBatis、事务、题目计划与快照、RAG MySQL 资格过滤与 trace 持久化、Java 校验和计分、FollowUpPolicy、状态推进、报告聚合及薄弱点持久化。

明确使用替身：Embedding / DeepSeek HTTP 指向 `127.0.0.1:18097` 的确定性本地服务器；Milvus 外部适配器由只读本地 `VectorStoreClient` 替身替换。没有验证真实 Milvus 或真实模型质量，不将本地模型返回的分数称为真实 AI 评分。

- Spring Boot：`127.0.0.1:18096`，profile 仅 `test`；保留 `EnvironmentIsolationInitializer`，没有绕过。
- 全新 MySQL 8 实例：`127.0.0.1:13326/ai_interview_test`；datadir 位于本 Worktree 的 `target/browser-e2e/20261018-v3/mysql-data/`。
- 前端：`127.0.0.1:15183`；独立 Chrome / Playwright 上下文，阻止非本机浏览器请求。
- 未读取 `application-local.yaml`，未连接业务 MySQL，未加载真实模型凭据，未修改共享 Milvus。
- 全部浏览器 attempt 合计 53 次本地模型 HTTP（25 Embedding、28 Chat）；**收费模型调用 0**。历史证据回放另为内存 Mock，不产生 HTTP 网络调用。
- 本次创建的四个服务进程已按 PID、启动时间和 executable 核对后停止；端口均释放。新测试数据目录与证据保留。

## 浏览器及数据库证据

| 执行 | 实际结果 |
|---|---|
| attempt-01 | 8 条检查通过后，新增工具错误地要求普通评价页刷新仍停在确认页，导致 1 项工具断言失败；已修正工具，未改生产恢复逻辑 |
| attempt-02（严格 full） | 28 条检查通过后，越界模型响应实际 HTTP 500，期望 502，失败 1；严格全量结果仍为 FAIL |
| attempt-03（明确 happy 范围） | 39 条汇总检查通过、失败 0、未捕获页面异常 0；不冒充 full 通过 |
| 数据库只读对照（Maven 前 / 后） | 两场面试均通过；回归未破坏已完成的浏览器结果 |

attempt-03 在 1440×1000 和 390×844 两个视口各完成一场：正常注册 / 登录、错误密码提示、登录刷新恢复、创建 EASY 五题计划、空答案禁用、提交、读取评价、显式确认后继续、2 道追问、FINISH 确认刷新保留、生成报告、报告刷新、历史及保存答案详情。页面分数与实际响应 / 数据库一致。

每场真实持久化：5 MAIN、2 FOLLOW_UP、7 EVALUATED 答案、7 评价（其中 5 FINAL）、7 Retrieval Batch、35 条 Prompt 证据、1 报告、2 薄弱点，最终均分 70。分数是本地合成 AI 的输入经 Java 计算所得。

权限覆盖：匿名受保护 API / 管理 API 返回 401；普通数据库 USER 的管理 API 返回 403，管理路由进入 forbidden；合成 ADMIN 正常登录后管理 API 返回 200，题库和知识文档页面显示成功；其他用户的报告关联详情返回 404。

attempt-02 还证明：响应在真实提交完成后被工具丢弃，按原 requestId + 原答案重放；双击仅发出一次提交；各身份只有一份答案 / 评价 / Retrieval Batch。越界分数对应答案 FAILED，没有评价写入、问题推进、报告或薄弱点写入。这些安全性检查通过不抵消 HTTP 分类失败。

## Maven 全量回归

使用 JDK 21、现有 Maven Wrapper 和本次新 MySQL 实例。普通回归前清除所有 `RUN_REAL_*` 和 `MILVUS_ENABLED`；19 个 opt-in 测试保持跳过（18 个既有真实服务用例和 1 个 AI baseline）。通过临时 POM 仅设置独立报告目录与 `-XX:+UseSerialGC -Xmx768m`，不改变测试代码、不删减用例。

| 运行 | Tests run | Failures | Errors | Skipped | 结论 |
|---|---:|---:|---:|---:|---|
| 新 Worktree 首轮 | 1659 | 0 | 1 | 19 | BUILD FAILURE：缺少历史 JSON，`@BeforeAll` 失败 |
| 本地补齐历史 JSON 后全量重跑 | 1661 | 0 | 0 | 19 | BUILD SUCCESS；1642 项执行通过 |

资源为 `docs/evaluation/judge/PHASE_5B_EPOCH_06_RUN_EVIDENCE.json`，SHA-256 为 `960805d571c6a6dcf8787a74286d5c83aec4368dc2b1fb2180bec5f76993caab`。仅从原工作区复制到新 Worktree，没有修改原件，没有提交该私有历史文件。3 个参数化离线回放用例随后通过；数量变化来自首轮 BeforeAll 错误条目与实际三条参数用例的差异。

## 阻塞项与剩余风险

1. **B1：模型校验错误的 HTTP 分类。** `EvaluationOrchestrationService` 将模型建议直接交给 validator；越界分数抛出 `IllegalArgumentException`，最终落入通用 500 handler。现场源码与 `d0b7d59` 完全一致，是既有 main 缺口。用户要求本次保持后端不动，未修复。`v3/integration/proposed-model-error-classification.patch` 仅为未应用的审查材料。
2. **B2：干净检出的 Maven 复现缺口。** 上述离线回放依赖未发布历史文件；本次本地补资源不修复 GitHub 干净检出的缺失。后续应以可公开、确定性的测试 fixture 单独修复，不发布私有原始证据，不删除或跳过测试。
3. 本次未验证真实模型、真实 Milvus、Firefox / Safari、实体手机软键盘与屏幕阅读器。不宣称部署、性能、评分准确率或外部服务集成已通过。

推荐下一步：单独处理 B1 和 B2，再在本集成分支重跑严格 `full` 浏览器验收及干净检出的 Maven。通过后再由用户审查、提交并决定合并；当前不要合并 main。

## Git 管理与复现入口

共享 `.git/info/exclude` 中的 `docs/` 规则保持原样；只对本次明确列出的 V3 文档 / 证据使用 `git add -f`，并核对 Git 索引内的完整性。没有强制加入其他历史目录、凭据、运行数据、node_modules 或构建产物。完整暂存变更清单见 `v3/integration/change-list.json`。

前端工程四项命令在 `frontend/` 中执行。联调入口与机器路径见 `tools/browser-e2e/README.md`。现有 run 目录已保存证据，`Prepare` 会拒绝覆盖；未来运行需先选择新的目录及空闲端口。当前可直接对已保存快照运行：

```powershell
node tools/browser-e2e/verify.mjs attempt-03 --after-regression
node tools/browser-e2e/verify-blocker.mjs
```

第一条只确认 happy 范围数据一致性；第二条确认已知阻塞仍有证据，均不能把整体状态改为 READY。

证据索引：`v3/integration/` 保存换行分析、补丁后哈希、工作区保护、前端日志、两轮 Maven 摘要、三次浏览器结果、失败 HTTP 证据、只读一致性、模型本地调用计数和代表截图。完整运行日志、脱敏 HTTP、全部截图、两份数据库快照、Surefire XML 及私有 MySQL 数据仍保存在本 Worktree 的 `target/`。
