# Frontend V3 · GitHub 发布前独立审查

日期：2026-10-18。技术结论：**READY_TO_MERGE**，可以进入最终 PR Review；发布目标为 Draft PR，仍需人工审查和合并决定。

## 审查范围

基线与远程 `main` 均为 `d0b7d59b5a135d78240a6188c2f1f08a4c69a302`。只操作 `codex/frontend-v3-integration-20261018` 的既有集成 Worktree。审查开始时 266 项暂存、无未暂存修改；未重写 main、原 V3 分支或其他 Worktree。

[逐文件清单](V3_PUBLICATION_FILE_REVIEW.md) 记录全部 266 项的用途及发布决定。主要入口：

- 前端：`tokens.css`、`shells.css`、`SessionSplitWorkspace.vue`、`InterviewEvaluationPane.vue`、`InterviewReportView.vue`。
- B1：`LlmEvaluationValidator`、`DeepSeekEvaluationClient`、`JevJudgeProvider`，以及真实 HTTP 合同测试 `FullInterviewBusinessOfflineTest`。
- B2：`JevE06ReliabilityReplayTest`、公开 `jev-reliability-synthetic-scenarios.json`、测试资源 README。
- 联调：`tools/browser-e2e/README.md`、严格 `acceptance.mjs` 和 `verify.mjs`。

## 结论与必要发布整理

B1 仅把模型结果的明确违规分类为已有 `ExternalServiceException`，继续返回脱敏的 502 合同。空内部上下文和其他编程异常不转换为 502。Jev 只在反馈校验局部捕获该类型，重试次数、预算、原生评分复用与回退条件没有扩大。HTTP 测试保留 500、400、409、FAILED 不推进、无评价写入及 requestId 恢复断言。

B2 使用六个明确标记的公开合成协议场景，断言真实适配器调用次数、复用的原生结果、Java 总分以及非法 fallback 拒绝。`MockRestServiceServer` 截获所有模型 HTTP。没有删除测试、添加 Disabled 或排除项，没有把合成结果伪称为历史 33/38 成功率；未发布私有历史原始 JSON。

V3 改动位于展示组件、样式与两个前端测试。API、router、store、composable、storage、后端状态机、DTO、SQL 和数据库结构均保持原实现。原生 radio/button/input、disabled、aria-busy、标题、焦点、skip link、菜单恢复及评价确认逻辑保留；新增分数细条是 aria-hidden 装饰，显示原始分数和服务端总分。

发布审查发现的可移植性问题已最小修正：四个可选 QA/联调脚本不再写死本机 Java/MySQL/Node/Chrome 或用户缓存路径，改用环境变量、PATH 和 Chrome channel。用法见工具 README；未调整任何业务断言或预期状态码。公开历史日志副本仅脱敏机器路径；未改通过/失败结果，原始日志另在本地保留。

图片由 140 张、7,435,709 字节精简至 45 张、2,712,088 字节（约 2.59 MiB），无重复图片哈希：16 张 V2、24 张 V3、5 张真实后端/本地 AI。精选覆盖桌面/手机对照、关键失败和焦点状态；完整矩阵仍在本地原位。第三方参考截图只保留来源说明。两个重复源码补丁也留在本地，以提交 Diff 审查最终源码。共 97 个路径只从发布索引移除，没有删除文件或清理原工作。

## 敏感信息与候选一致性

对所有发布文本、文件路径和二进制清单复核：没有真实密码、访问 JWT、API Key、私有历史原文、业务用户数据、MySQL datadir、node_modules、target 构建产物或机器专属绝对路径。测试代码和隔离工具中存在明确的合成密码、测试签名材料和 `local-mock-only` API 标识；它们仅用于新建的回环测试实例，不是生产凭据。账号、成绩和请求 ID 均为测试生成数据。

与已通过干净检出测试的候选树 `65830dfd49f7fe6650aad6d62a43dbd5521e11a7` 比较 554 个代码、资源和构建路径：550 个 Git blob 完全一致，差异仅为上述四个可选工具的路径解析。生产前后端、所有测试和公开 Fixture 与验收源码一致。详见 [结构化证明](v3/publication-proof.json)。

原工作区 6,299 个文件及原 V3 Worktree 863 个文件的 SHA-256 和 Git porcelain 状态再次复核，无变化。所有既有 Worktree 保留。

## 验证证据

| 检查 | 结果与证据 |
|---|---|
| 前端 Typecheck / Lint / Build | 退出码均为 0；生产前端源码与已验证版本一致 |
| 前端单元 | 293 项通过，0 failures / errors / skipped |
| 集成 Worktree 全量 Maven | 重新汇总原始 135 份 XML：1741 tests / 0 failures / 0 errors / 19 skipped |
| 干净检出默认全量 Maven | 重新汇总独立检出 135 份 XML：1741 / 0 / 0 / 19；无需私有 JSON |
| 严格 full 浏览器 | 原始结果 PASS，49 条检查；0 未捕获页面异常；502 和失败后持久化断言保留 |
| 发布整理影响验证 | PowerShell AST、运行路径解析、5 个 JS 语法检查通过；Playwright 模块解析、Chrome channel、独立空白页交互通过，网络请求 0 |

本阶段通过源码身份复核沿用完整验收，不声称又重跑了一次完整 Maven/业务浏览器。19 个跳过仍为原有 opt-in 服务/AI baseline 用例。完整验收、失败记录、数据状态与证据边界见 [B1/B2 最终报告](V3_BLOCKER_FIX_20261018.md)；最初的 [NOT_READY 集成报告](V3_INTEGRATION_20261018.md) 保留为历史记录。

真实 Spring Boot、独立 MySQL、JWT、角色查询、事务、幂等及恢复经过联调；Embedding/Chat HTTP 和外部向量适配器采用确定性本地替身。没有调用收费模型，没有验证真实模型质量或真实 Milvus。Firefox/Safari、实体手机、屏幕阅读器人工检查和线上部署仍待后续验证。

## GitHub 验证边界

发布前 GitHub API 与本地目录均确认仓库没有配置 GitHub Actions 工作流（0 个）。前端、Maven、浏览器均是本地证据，不能写成 GitHub CI PASS。推送后另核对远程 commit/tree、PR 文件集合、Draft 状态、checks 及 main 未变化；实际提交和 PR 链接以 GitHub PR 正文为准。
