# Repository Organization 执行报告

## 1. 执行摘要

执行日期：2026-10-05。范围为 Repository Organization，不包含业务重构。完成 40 个文件迁移：9 个跨目录归位、18 个同目录中文改名、13 个历史归档（统计互斥）。其中归档包括 10 个 ZIP、2 个重复题库 Markdown 和 1 个初始化帮助。

新增 [中文导航](README.md) 和本执行报告；修复 108 处链接或行内路径，涉及 12 份原有文档。所有指定文件操作均完成，没有覆盖目标，没有永久删除原有文件。源码、工程配置、SQL schema、机器固定路径与原始证据保持执行前内容。

后端 compile PASS；最终显式 test profile 完整回归 PASS（1436 tests、0 failures、0 errors、19 skipped）。前端 36 个测试文件、289 tests 全部 PASS，类型检查与生产 build PASS。前面的环境失败也完整保留在下文。

`GIT_COMMIT_SKIPPED`：当前执行工具实际拒绝 `.git/refs` 和 `.git/index.lock` 写入。没有请求提权，没有修改忽略策略，没有 push。

## 2. 执行前 Git 状态

原分支：`main`；HEAD：`e2cab27`。执行前暂存区为空。Owner 已有 9 份 tracked 修改；全部保留且逐文件 SHA-256 与快照一致。未跟踪文件使用 Git 原有汇总显示，包含前端、agents 和环境隔离相关文件；docs 由于 exclude 不出现在普通 status 中。

```text
 M AGENTS.md
 M src/test/java/com/kun/aiinterview/config/TestProfileExternalServiceIsolationTest.java
 M src/test/java/com/kun/aiinterview/knowledge/embedding/RealEmbeddingSmokeTest.java
 M src/test/java/com/kun/aiinterview/knowledge/service/KnowledgePersistenceIntegrationTest.java
 M src/test/java/com/kun/aiinterview/knowledge/service/RealKnowledgeDocumentProcessingFailureIntegrationTest.java
 M src/test/java/com/kun/aiinterview/knowledge/service/RealKnowledgeDocumentProcessingSmokeTest.java
 M src/test/java/com/kun/aiinterview/knowledge/vector/milvus/RealMilvusSchemaValidationSmokeTest.java
 M src/test/java/com/kun/aiinterview/knowledge/vector/milvus/RealMilvusVectorStoreSmokeTest.java
 M src/test/resources/application-test.yaml
?? "AI 模拟面试系统｜项目设计记录 v1.1.md"
?? DATA_ENVIRONMENT_AUDIT.md
?? DATA_ENVIRONMENT_AUDIT_CLOSURE.md
?? PHASE_0_5_ENVIRONMENT_CLEANUP.md
?? PHASE_1_QUESTION_BANK_FINAL.md
?? PHASE_9B_FINAL_REPAIR_REPORT.md
?? PHASE_9C_FULL_REAL_E2E_REPORT.md
?? agents/
?? frontend/
?? phase-8b3-interview-session-review.zip
?? phase-8b3-repair-review.zip
?? phase-8b4-history-detail-report-review.zip
?? phase-8b5-remaining-surfaces-review.zip
?? phase-8b5r-8c-review.zip
?? phase-9b-final-repair-review.zip
?? sql/PHASE_1_QUESTION_BANK_FINAL.md
?? src/main/java/com/kun/aiinterview/config/EnvironmentIsolationInitializer.java
?? src/main/resources/META-INF/
?? src/main/resources/application-evaluation.yaml
?? src/test/java/com/kun/aiinterview/config/EnvironmentIsolationInitializerTest.java
?? src/test/java/com/kun/aiinterview/evaluation/
?? src/test/resources/META-INF/
?? src/test/resources/application-test-real.yaml
```

最近 5 个提交：

```text
e2cab27 feat: integrate Jev judge provider with DeepSeek fallback
20a933a fix: handle concurrent evaluation claim races
6f426f7 fix: add stale recovery and cross-store reconciliation
1c7b875 fix: harden account security and api error semantics
e699f2b fix: harden interview snapshot and input contracts
```

安全快照同时记录了全部 837 个非依赖/非构建文件的 SHA-256、60 份 Markdown/HTML 的原始文本、完整 `--untracked-files=all` 路径列表与 ignore/exclude。快照只用于本轮比对，未加入 Git；临时检查脚本和快照在收尾时删除。

`.codex/rules` 不存在。指定分支 `chore/project-file-organization-20261005` 创建失败：`cannot lock ref ... unable to create directory for .git/refs/heads/chore/project-file-organization-20261005`。按授权继续在 main 执行。

## 3. 实际移动文件列表

| 旧路径（仓库根相对） | 当前路径（仓库根相对） |
|---|---|
| `AI 模拟面试系统｜项目设计记录 v1.1.md` | `docs/design/AI模拟面试系统-项目设计记录-v1.1.md` |
| `DATA_ENVIRONMENT_AUDIT.md` | `docs/environment/数据环境审计.md` |
| `DATA_ENVIRONMENT_AUDIT_CLOSURE.md` | `docs/environment/数据环境审计-闭环验证.md` |
| `PHASE_0_5_ENVIRONMENT_CLEANUP.md` | `docs/environment/Phase0.5-环境清理与隔离验证.md` |
| `JEV_INTEGRATION_LEARNING_REPORT.md` | `docs/study/Jev-Judge接入学习报告.md` |
| `JEV_JAVA_STRUCTURE_AUDIT_REPORT.md` | `docs/evaluation/Jev-Java结构与无效代码审计.md` |
| `JEV_JUDGE_INCREMENTAL_LEARNING_GUIDE.html` | `docs/study/Jev-Judge增量学习教材.html` |
| `PHASE_9B_FINAL_REPAIR_REPORT.md` | `docs/frontend/review/final/Phase9B-最终修复验证.md` |
| `PHASE_9C_FULL_REAL_E2E_REPORT.md` | `docs/frontend/review/final/Phase9C-前后端真实E2E验证.md` |



## 4. 实际重命名文件列表

| 旧路径（仓库根相对） | 当前路径（仓库根相对） |
|---|---|
| `docs/question-bank/PHASE_1_EXTERNAL_REVIEW_GROK.md` | `docs/question-bank/Phase1-Grok题库审查.md` |
| `docs/question-bank/PHASE_1_DATABASE_IMPORT_PLAN.md` | `docs/question-bank/Phase1D-题库数据库导入计划.md` |
| `docs/question-bank/PHASE_1_DATABASE_IMPORT_REPORT.md` | `docs/question-bank/Phase1D-题库数据库导入验证.md` |
| `docs/rag/PHASE_2A_VERSION_AND_INGESTION_AUDIT.md` | `docs/rag/Phase2A-版本与知识入库审计.md` |
| `docs/rag/PHASE_2B_DOCUMENT_AUTHORING_REPORT.md` | `docs/rag/Phase2B-知识文档编写审查.md` |
| `docs/rag/PHASE_2C_CHUNK_DRY_RUN_REPORT.md` | `docs/rag/Phase2C-离线切片验证.md` |
| `docs/rag/PHASE_2C_CHUNK_PREVIEW.md` | `docs/rag/Phase2C-切片内容预览.md` |
| `docs/rag/PHASE_3A_RUNTIME_READINESS_REPORT.md` | `docs/rag/Phase3A-真实服务就绪验证.md` |
| `docs/rag/PHASE_3B_REAL_INGESTION_REPORT.md` | `docs/rag/Phase3B-真实知识库入库验证.md` |
| `docs/frontend/review/final/FINAL_ENGINEERING_REVIEW.md` | `docs/frontend/review/final/Phase9A-前端工程审查.md` |
| `docs/frontend/review/final/FINAL_UX_ACCESSIBILITY_REVIEW.md` | `docs/frontend/review/final/Phase9A-UX与可访问性审查.md` |
| `docs/frontend/review/final/FINAL_BLIND_REVIEW.md` | `docs/frontend/review/final/Phase9A-独立盲审.md` |
| `docs/frontend/review/final/FINAL_FINDINGS.md` | `docs/frontend/review/final/Phase9B-最终问题台账.md` |
| `docs/learning/ai-interview-system-review.html` | `docs/learning/AI模拟面试系统-学习与源码复盘-2026-07-18.html` |
| `docs/study/AI_INTERVIEW_BACKEND_INTERVIEW_GUIDE.md` | `docs/study/AI模拟面试系统-后端源码与面试学习手册.md` |
| `docs/study/AI_INTERVIEW_BACKEND_INTERVIEW_GUIDE.html` | `docs/study/AI模拟面试系统-后端源码与面试学习手册.html` |
| `docs/study/AI_INTERVIEW_BACKEND_INTERVIEW_GUIDE.pdf` | `docs/study/AI模拟面试系统-后端源码与面试学习手册.pdf` |
| `docs/frontend/prototype/KIMI_VISUAL_REVIEW_PACKAGE.md` | `docs/frontend/prototype/Phase7D-V1-Kimi视觉审查包.md` |



## 5. 实际归档文件列表

| 旧路径（仓库根相对） | 当前路径（仓库根相对） |
|---|---|
| `phase-8b3-interview-session-review.zip` | `docs/99-历史归档/前端审核包/phase-8b3-interview-session-review.zip` |
| `phase-8b3-repair-review.zip` | `docs/99-历史归档/前端审核包/phase-8b3-repair-review.zip` |
| `phase-8b4-history-detail-report-review.zip` | `docs/99-历史归档/前端审核包/phase-8b4-history-detail-report-review.zip` |
| `phase-8b5-remaining-surfaces-review.zip` | `docs/99-历史归档/前端审核包/phase-8b5-remaining-surfaces-review.zip` |
| `phase-8b5r-8c-review.zip` | `docs/99-历史归档/前端审核包/phase-8b5r-8c-review.zip` |
| `phase-9b-final-repair-review.zip` | `docs/99-历史归档/前端审核包/phase-9b-final-repair-review.zip` |
| `docs/frontend/review/phase-8a-repair-review.zip` | `docs/99-历史归档/前端审核包/phase-8a-repair-review.zip` |
| `docs/frontend/review/phase-8b1-auth-product-shell-review.zip` | `docs/99-历史归档/前端审核包/phase-8b1-auth-product-shell-review.zip` |
| `docs/frontend/review/phase-8b2-dashboard-create-review.zip` | `docs/99-历史归档/前端审核包/phase-8b2-dashboard-create-review.zip` |
| `frontend/src.zip` | `docs/99-历史归档/前端审核包/src.zip` |
| `PHASE_1_QUESTION_BANK_FINAL.md` | `docs/99-历史归档/题库副本/Phase1-题库-根目录副本.md` |
| `sql/PHASE_1_QUESTION_BANK_FINAL.md` | `docs/99-历史归档/题库副本/Phase1-题库-SQL目录副本.md` |
| `HELP.md` | `docs/99-历史归档/Spring-Boot初始帮助.md` |

ZIP 采用整文件移动，未解压改写；`frontend/src.zip` 是历史源码快照，保留为归档目录中的 `src.zip`。HELP.md 经检查只有 Spring Boot/Maven/MyBatis 初始化帮助，无项目独有内容。Kimi V1 原文仍声明 `SUPERSEDED BY PHASE 7C/7D V2`，仅改名，历史结论未变。

移动前 3 份题库 Markdown 的共同 SHA-256：

```text
6278fd0ca2bf48c6256fa9bfd1f72ffa1925bb1a70eb2bb11fa67c5f8a06e296
```

canonical Markdown/JSON 和两份归档副本经收尾核对保持执行前内容。

## 6. SKIP 文件和原因

指定文件移动/改名/归档：SKIP = 0。

| 操作或资产 | 处理及原因 |
|---|---|
| 指定分支创建 | SKIP_AND_CONTINUE；执行环境拒绝写入 Git refs，继续 main |
| 精确暂存与本地 commit | GIT_COMMIT_SKIPPED；`git add -f -- docs/README.md` 被拒绝写入 `.git/index.lock`，暂存区仍为空 |
| Push | 未执行；Owner 明确禁止 |
| 18 个 guarded real-service Smoke Tests | SKIPPED；普通回归不启用 RUN_REAL_* |
| 1 个真实 AI Baseline 测试 | SKIPPED；没有授权重跑真实 AI Baseline |
| 自动生成报告、机器数据、截图、source-documents、前端七份核心契约 | 按任务要求保持原名原路径原内容，不属漏执行 |
| Java/Vue/配置/SQL 和 Owner 既有修改 | 保护区，不改动 |

没有因单个操作失败而停止整轮整理。

## 7. 旧路径 → 新路径完整映射

| 旧路径（仓库根相对） | 当前路径（仓库根相对） |
|---|---|
| `AI 模拟面试系统｜项目设计记录 v1.1.md` | `docs/design/AI模拟面试系统-项目设计记录-v1.1.md` |
| `DATA_ENVIRONMENT_AUDIT.md` | `docs/environment/数据环境审计.md` |
| `DATA_ENVIRONMENT_AUDIT_CLOSURE.md` | `docs/environment/数据环境审计-闭环验证.md` |
| `PHASE_0_5_ENVIRONMENT_CLEANUP.md` | `docs/environment/Phase0.5-环境清理与隔离验证.md` |
| `JEV_INTEGRATION_LEARNING_REPORT.md` | `docs/study/Jev-Judge接入学习报告.md` |
| `JEV_JAVA_STRUCTURE_AUDIT_REPORT.md` | `docs/evaluation/Jev-Java结构与无效代码审计.md` |
| `JEV_JUDGE_INCREMENTAL_LEARNING_GUIDE.html` | `docs/study/Jev-Judge增量学习教材.html` |
| `PHASE_9B_FINAL_REPAIR_REPORT.md` | `docs/frontend/review/final/Phase9B-最终修复验证.md` |
| `PHASE_9C_FULL_REAL_E2E_REPORT.md` | `docs/frontend/review/final/Phase9C-前后端真实E2E验证.md` |
| `docs/question-bank/PHASE_1_EXTERNAL_REVIEW_GROK.md` | `docs/question-bank/Phase1-Grok题库审查.md` |
| `docs/question-bank/PHASE_1_DATABASE_IMPORT_PLAN.md` | `docs/question-bank/Phase1D-题库数据库导入计划.md` |
| `docs/question-bank/PHASE_1_DATABASE_IMPORT_REPORT.md` | `docs/question-bank/Phase1D-题库数据库导入验证.md` |
| `docs/rag/PHASE_2A_VERSION_AND_INGESTION_AUDIT.md` | `docs/rag/Phase2A-版本与知识入库审计.md` |
| `docs/rag/PHASE_2B_DOCUMENT_AUTHORING_REPORT.md` | `docs/rag/Phase2B-知识文档编写审查.md` |
| `docs/rag/PHASE_2C_CHUNK_DRY_RUN_REPORT.md` | `docs/rag/Phase2C-离线切片验证.md` |
| `docs/rag/PHASE_2C_CHUNK_PREVIEW.md` | `docs/rag/Phase2C-切片内容预览.md` |
| `docs/rag/PHASE_3A_RUNTIME_READINESS_REPORT.md` | `docs/rag/Phase3A-真实服务就绪验证.md` |
| `docs/rag/PHASE_3B_REAL_INGESTION_REPORT.md` | `docs/rag/Phase3B-真实知识库入库验证.md` |
| `docs/frontend/review/final/FINAL_ENGINEERING_REVIEW.md` | `docs/frontend/review/final/Phase9A-前端工程审查.md` |
| `docs/frontend/review/final/FINAL_UX_ACCESSIBILITY_REVIEW.md` | `docs/frontend/review/final/Phase9A-UX与可访问性审查.md` |
| `docs/frontend/review/final/FINAL_BLIND_REVIEW.md` | `docs/frontend/review/final/Phase9A-独立盲审.md` |
| `docs/frontend/review/final/FINAL_FINDINGS.md` | `docs/frontend/review/final/Phase9B-最终问题台账.md` |
| `docs/learning/ai-interview-system-review.html` | `docs/learning/AI模拟面试系统-学习与源码复盘-2026-07-18.html` |
| `docs/study/AI_INTERVIEW_BACKEND_INTERVIEW_GUIDE.md` | `docs/study/AI模拟面试系统-后端源码与面试学习手册.md` |
| `docs/study/AI_INTERVIEW_BACKEND_INTERVIEW_GUIDE.html` | `docs/study/AI模拟面试系统-后端源码与面试学习手册.html` |
| `docs/study/AI_INTERVIEW_BACKEND_INTERVIEW_GUIDE.pdf` | `docs/study/AI模拟面试系统-后端源码与面试学习手册.pdf` |
| `docs/frontend/prototype/KIMI_VISUAL_REVIEW_PACKAGE.md` | `docs/frontend/prototype/Phase7D-V1-Kimi视觉审查包.md` |
| `phase-8b3-interview-session-review.zip` | `docs/99-历史归档/前端审核包/phase-8b3-interview-session-review.zip` |
| `phase-8b3-repair-review.zip` | `docs/99-历史归档/前端审核包/phase-8b3-repair-review.zip` |
| `phase-8b4-history-detail-report-review.zip` | `docs/99-历史归档/前端审核包/phase-8b4-history-detail-report-review.zip` |
| `phase-8b5-remaining-surfaces-review.zip` | `docs/99-历史归档/前端审核包/phase-8b5-remaining-surfaces-review.zip` |
| `phase-8b5r-8c-review.zip` | `docs/99-历史归档/前端审核包/phase-8b5r-8c-review.zip` |
| `phase-9b-final-repair-review.zip` | `docs/99-历史归档/前端审核包/phase-9b-final-repair-review.zip` |
| `docs/frontend/review/phase-8a-repair-review.zip` | `docs/99-历史归档/前端审核包/phase-8a-repair-review.zip` |
| `docs/frontend/review/phase-8b1-auth-product-shell-review.zip` | `docs/99-历史归档/前端审核包/phase-8b1-auth-product-shell-review.zip` |
| `docs/frontend/review/phase-8b2-dashboard-create-review.zip` | `docs/99-历史归档/前端审核包/phase-8b2-dashboard-create-review.zip` |
| `frontend/src.zip` | `docs/99-历史归档/前端审核包/src.zip` |
| `PHASE_1_QUESTION_BANK_FINAL.md` | `docs/99-历史归档/题库副本/Phase1-题库-根目录副本.md` |
| `sql/PHASE_1_QUESTION_BANK_FINAL.md` | `docs/99-历史归档/题库副本/Phase1-题库-SQL目录副本.md` |
| `HELP.md` | `docs/99-历史归档/Spring-Boot初始帮助.md` |



## 8. 修复的引用

| 文档 | 修复处数 |
|---|---|
| `docs/environment/Phase0.5-环境清理与隔离验证.md` | 5 |
| `docs/environment/数据环境审计-闭环验证.md` | 17 |
| `docs/environment/数据环境审计.md` | 32 |
| `docs/frontend/review/final/Phase9A-UX与可访问性审查.md` | 2 |
| `docs/frontend/review/final/Phase9A-独立盲审.md` | 2 |
| `docs/frontend/review/final/Phase9B-最终修复验证.md` | 1 |
| `docs/learning/AI模拟面试系统-学习与源码复盘-2026-07-18.html` | 1 |
| `docs/question-bank/Phase1D-题库数据库导入计划.md` | 1 |
| `docs/question-bank/Phase1D-题库数据库导入验证.md` | 2 |
| `docs/rag/Phase2C-离线切片验证.md` | 1 |
| `docs/study/Jev-Judge增量学习教材.html` | 43 |
| `docs/study/Jev-Judge接入学习报告.md` | 1 |

修复内容限定于 Markdown 链接目标、HTML href 和明确的行内路径；不改写教材业务正文、原验收结论或代码示例逻辑。环境报告移入 docs 后，对源码、SQL、evidence 与评测报告的现有链接按新父目录重新计算相对路径。

Jev 教材中所有 `file:///` href 已转换为相对路径，包括原桌面学习手册及仓库源码。桌面手册通过仓库内唯一同名原教材确认；chapter-10/11/16/20 等 fragment 在现存 HTML 中存在。未能可靠转换的本机链接：0。

题库计划/报告互引、RAG 离线切片报告到预览、前端 UX/盲审互引、Phase 9B 到问题台账、环境审计到 Phase 9C 等均已指向当前目标。学习复盘中的自身维护路径和 Jev 教材中的 Java 审计报告路径同步更新。新导航通过中文显示名链接到七份未改名的前端契约。

旧名字作为 Markdown 显示文字仍可保留，实际链接目标已更新；这不构成 broken link。

## 9. 保留未修改的历史引用

全仓旧名扫描覆盖 ignored 文本文件，排除 Git internals、依赖、构建产物、IDE 元数据和 local 凭据；二进制 ZIP 通过全文件 SHA-256 验证，未改写内部引用。

保留类型：历史 JSON evidence/manifest 中的报告名与路径；历史报告中的 Created/新增文件记录、原 hash 名称及 Get-FileHash 历史命令输出；Jev 教材叙述的原桌面教材名；canonical 题库真实路径；Gitignore 中的原 HELP.md 规则；已修复链接的旧显示文字。docs 导航和本报告旧路径映射允许出现全部旧名字。

残留位置清单（只记录位置，不复制 evidence 内容）：

| 文件及行号 | 扫描分类 |
|---|---|
| `.gitignore:1` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/study/Jev-Judge增量学习教材.html:43` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/environment/数据环境审计.md:33` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/environment/数据环境审计.md:538` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/environment/数据环境审计-闭环验证.md:7` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/environment/数据环境审计-闭环验证.md:32` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/environment/数据环境审计-闭环验证.md:317` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/environment/数据环境审计-闭环验证.md:318` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/environment/数据环境审计-闭环验证.md:401` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/question-bank/Phase1D-题库数据库导入计划.md:7` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/question-bank/Phase1D-题库数据库导入验证.md:6` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/question-bank/Phase1D-题库数据库导入验证.md:93` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/environment/scripts/phase05_data_cleanup.py:327` | IMMUTABLE_OR_MACHINE_CONTRACT |
| `docs/environment/scripts/phase05_data_cleanup.py:328` | IMMUTABLE_OR_MACHINE_CONTRACT |
| `docs/rag/Phase2C-离线切片验证.md:33` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/rag/Phase3A-真实服务就绪验证.md:162` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/rag/Phase3B-真实知识库入库验证.md:167` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/environment/PHASE_0_5_FINAL_VERIFICATION.json:148` | IMMUTABLE_OR_MACHINE_CONTRACT |
| `docs/environment/PHASE_0_5_FINAL_VERIFICATION.json:149` | IMMUTABLE_OR_MACHINE_CONTRACT |
| `docs/environment/PHASE_0_5_CLEANUP_MANIFEST.md:5` | IMMUTABLE_OR_MACHINE_CONTRACT |
| `docs/environment/PHASE_0_5_CLEANUP_MANIFEST.md:6` | IMMUTABLE_OR_MACHINE_CONTRACT |
| `docs/environment/Phase0.5-环境清理与隔离验证.md:7` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/environment/Phase0.5-环境清理与隔离验证.md:186` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/environment/Phase0.5-环境清理与隔离验证.md:193` | HISTORICAL_MENTION_OR_CURRENT_DOC |
| `docs/environment/Phase0.5-环境清理与隔离验证.md:194` | HISTORICAL_MENTION_OR_CURRENT_DOC |

人工复核未发现仍指向已移动旧目标的有效 href/Markdown 链接。原始 hash 记录保留的是历史时点的内容指纹；人工报告因本次修复路径而改变字节时，不重写原 hash 以冒充历史证据。

## 10. Git ignore / exclude 状态

`.gitignore` 和 `.git/info/exclude` 经 SHA/文本比对保持执行前状态；没有删除 docs ignore，没有扩大 Git 跟踪。

exclude 中仍有两条 `docs/`（沿用原始状态，不合并）：

```text
docs/
docs/
```

实际原 tracked 迁移文件只有根目录的 3 份 Jev 文档。其目标在 ignored docs 中，普通 Git diff 当前只看到旧路径删除；目标文件都存在且经过比对，不是永久删除。

原计划可精确纳入提交的候选为：这 3 份 Jev 目标、3 个 tracked 旧路径删除、`docs/README.md` 和本报告。未计划 force-add 其余本地报告、ZIP、题库历史副本、evidence、screenshots 或 credentials。精确 add 导航即被环境拒绝，故没有执行后续 staging 或 commit。

## 11. Markdown link 检查结果

对所有移动/改名的 Markdown、实际内容修改的 Markdown、新导航和本报告进行相对链接目标存在性检查；HTML href/src 也同时检查。剔除围栏中的历史输出，外部网址不做网络访问，Markdown 锚点不冒充经过全面语义验证。

当前检查覆盖 31 份 Markdown/HTML、177 个相对目标；新增 broken links = 0，现存 broken links = 0。最终数字在报告创建后再次核对并更新。

Jev 到学习手册的章节锚点另行核对；未修复本机 href = 0。导航的目录、PDF、HTML、题库、源码和 SQL 目标均存在。

## 12. 后端验证结果

| 验证 | 命令 | 结果 | 计数与范围 |
|---|---|---|---|
| compile | .\mvnw.cmd -B -ntp -DskipTests compile | PASS | 无需测试计数；JDK 21，BUILD SUCCESS（现有 class 已为最新） |
| 初次默认 test | .\mvnw.cmd -B -ntp test | INTERRUPTED / EXECUTION_ENVIRONMENT_FAILURE | 已完成 suite 合计 401 tests / 1 failures / 63 errors / 1 skipped；不是完整计数 |
| 凭据补齐后默认 test | .\mvnw.cmd -B -ntp test | FAIL / EXECUTION_ENVIRONMENT_FAILURE | 1436 tests / 1 failure / 90 errors / 19 skipped |
| 最终显式 test profile | .\mvnw.cmd -B -ntp -Dspring.profiles.active=test test | PASS | 1436 tests / 0 failures / 0 errors / 19 skipped；BUILD SUCCESS |


初次 Maven 子进程没有 DB_USERNAME/DB_PASSWORD 等变量，出现数据库认证错误；没有修改生产配置适应环境。停止无效尝试后，仅将 Windows 用户环境中已有数据库凭据临时传入该子进程，不输出凭据、不落盘密钥、不持久化环境变量，不加载 formal local DB_URL。

默认回归的 90 个错误来自 6 组 MVC 测试未显式选择 test profile，被已有 EnvironmentIsolationInitializer 拒绝；1 个 failure 是 TestProfileExternalServiceIsolationTest 的 milvus.enabled 非空 false 断言（该轮解析为 null）。最终用 Remove-Item 移除进程级 RUN_REAL_* / MILVUS_ENABLED 等开关，并显式选择 test profile 完成全量验证。没有绕过 initializer，没有关闭 JUnit 条件，没有修改任何测试断言或源码。该最终运行使用 ai_interview_test，真实 MySQL Mapper/事务测试包含在此回归中；不能称为全 Mock。

19 skipped = 18 个双保护真实外部服务测试 + 1 个 AI Baseline。未调用真实 Embedding、Milvus、Jev、DeepSeek；未重跑知识入库或 AI Baseline，未操作正式 MySQL/Milvus 业务数据。此结果不是新的真实 AI / 产品 E2E 验收。

失败 suite 分类（保留前一轮事实，不隐去失败）：

| 默认运行 suite | Tests | Failures | Errors |
|---|---|---|---|
| TestProfileExternalServiceIsolationTest | 3 | 1 | 0 |
| HttpErrorTaxonomyControllerTest | 14 | 0 | 14 |
| InterviewControllerTest | 16 | 0 | 16 |
| AdminKnowledgeDocumentControllerTest | 16 | 0 | 16 |
| AdminQuestionControllerTest | 20 | 0 | 20 |
| AdminQuestionPaginationControllerTest | 15 | 0 | 15 |
| SecurityConfigurationTest | 9 | 0 | 9 |


本轮可审计的 Maven 日志保留在 ignored `target/organization-execution/backend-compile.log`、`backend-test-missing-environment.log`、`backend-test.log`、`backend-test-profile-test.log`。

## 13. 前端验证结果

| 验证 | 命令（frontend 目录） | 结果 | 细节 |
|---|---|---|---|
| 默认单测 | npm.cmd run test:unit | FAIL / EXECUTION_ENVIRONMENT_FAILURE | config bundle 启动发生 spawn EPERM，0 tests 执行 |
| 替代单测 | npm.cmd run test:unit -- --configLoader native --pool threads | PASS | 36 test files / 289 tests passed / 0 failed / 0 skipped |
| 默认 build | npm.cmd run build | FAIL / EXECUTION_ENVIRONMENT_FAILURE | vue-tsc 阶段通过；Vite 配置 bundle 启动发生 spawn EPERM |
| 替代 build | npm.cmd run build -- --configLoader native | PASS | vue-tsc -b + Vite production build 成功 |


使用已有 node_modules，未运行 install/update，未升级包，未修改 package.json、lockfile、vite.config.ts、tsconfig 或测试。CLI native loader 避开默认配置 bundle 阶段触发的 Windows net use 子进程启动错误；threads 是 Vitest 已支持的执行池。没有修改依赖实现或绕过测试。

日志保留于 ignored `target/organization-execution/frontend-test.log`、`frontend-test-native.log`、`frontend-build.log`、`frontend-build-native.log`。前端 build 输出属于原有 ignored dist。

## 14. 最终 Git 状态与源码保护复核

最终分支：`main`；HEAD 保持 `e2cab27`；暂存区为空。Owner 原有 9 个 tracked 修改和原有未跟踪源码/配置/前端保持完整。根目录只剩工程目录、wrapper、pom、AGENTS 与 Git/IDE 配置；Phase 报告、题库副本、帮助和 ZIP 均已归位。

```text
 M AGENTS.md
 D JEV_INTEGRATION_LEARNING_REPORT.md
 D JEV_JAVA_STRUCTURE_AUDIT_REPORT.md
 D JEV_JUDGE_INCREMENTAL_LEARNING_GUIDE.html
 M src/test/java/com/kun/aiinterview/config/TestProfileExternalServiceIsolationTest.java
 M src/test/java/com/kun/aiinterview/knowledge/embedding/RealEmbeddingSmokeTest.java
 M src/test/java/com/kun/aiinterview/knowledge/service/KnowledgePersistenceIntegrationTest.java
 M src/test/java/com/kun/aiinterview/knowledge/service/RealKnowledgeDocumentProcessingFailureIntegrationTest.java
 M src/test/java/com/kun/aiinterview/knowledge/service/RealKnowledgeDocumentProcessingSmokeTest.java
 M src/test/java/com/kun/aiinterview/knowledge/vector/milvus/RealMilvusSchemaValidationSmokeTest.java
 M src/test/java/com/kun/aiinterview/knowledge/vector/milvus/RealMilvusVectorStoreSmokeTest.java
 M src/test/resources/application-test.yaml
?? agents/
?? frontend/
?? src/main/java/com/kun/aiinterview/config/EnvironmentIsolationInitializer.java
?? src/main/resources/META-INF/
?? src/main/resources/application-evaluation.yaml
?? src/test/java/com/kun/aiinterview/config/EnvironmentIsolationInitializerTest.java
?? src/test/java/com/kun/aiinterview/evaluation/
?? src/test/resources/META-INF/
?? src/test/resources/application-test-real.yaml
```

通过初始快照对全部原文件进行比对，只允许清单内 MOVE 和明确的路径修改；意外改动 = 0。Java/Vue 业务、API contract、DB schema、RAG/Jev/DeepSeek、Session/CAS、Controller、SQL、配置、工程文件、Owner 修改均未产生本轮内容变化。检查 `git diff`，保护区 diff 与执行前一致；ignored 本地变化通过 SHA-256 独立检查，补足 Git diff 看不到的部分。

固定/受保护资产检查覆盖 768 个原文件；10 个 ZIP 全文件 SHA-256 与执行前一致。canonical 两种格式、knowledge source documents、data/evidence/scripts/environment JSON、screenshots 和七份前端契约均保持原始字节。

归档/移位文件的 SHA-256 对照（报告路径修复导致的内容变化明确标记）：

| 当前路径 | 执行前 SHA-256 | 内容对照 |
|---|---|---|
| `docs/design/AI模拟面试系统-项目设计记录-v1.1.md` | 6b6446f374131346074a6670b38de34021709940997c9acd7fc477f8c27a1cb8 | SAME |
| `docs/environment/数据环境审计.md` | 5716abc5a1a16bdd39c79c3a425c66651f202cd414916dfe879a20e621641503 | PATH_REFERENCE_ONLY_CHANGED |
| `docs/environment/数据环境审计-闭环验证.md` | cf79c939158bccd8e6f2263ceb76253fcf0b1c234d8fd660817022e2e13395b8 | PATH_REFERENCE_ONLY_CHANGED |
| `docs/environment/Phase0.5-环境清理与隔离验证.md` | 6b7ef86460b501ee378e32ef362574a7a808e01bdbc017bd4c0148821a013ed8 | PATH_REFERENCE_ONLY_CHANGED |
| `docs/study/Jev-Judge接入学习报告.md` | f316c30c518a33689f0f49b2c6b7ef4e437310d86b029c274546e9e5b05317b1 | PATH_REFERENCE_ONLY_CHANGED |
| `docs/evaluation/Jev-Java结构与无效代码审计.md` | 0d62aafd00bd502b7630f6cddf936e46b209251d854b97bc47ec05477393dc50 | SAME |
| `docs/study/Jev-Judge增量学习教材.html` | 0af68f9e27717dddfe271c50dde1b8e8b2904fae6cf0cd8a4b758967672558f5 | PATH_REFERENCE_ONLY_CHANGED |
| `docs/frontend/review/final/Phase9B-最终修复验证.md` | 7f8a856d4210ea6e7c3888c68444e6827418b7c224a65d6eb75ca442b21bdcf1 | PATH_REFERENCE_ONLY_CHANGED |
| `docs/frontend/review/final/Phase9C-前后端真实E2E验证.md` | e960dad1c091701acd379328d1d5c45329c01d1d22cd9c282984f76cec4af3bc | SAME |
| `docs/question-bank/Phase1-Grok题库审查.md` | 072c9647b23d2204f004300f9c65821dd8fa9b19e7fe26ec3a7c1d85fad61a01 | SAME |
| `docs/question-bank/Phase1D-题库数据库导入计划.md` | 786a633e1866437cb38803e7fa13fcb8c6c5e19db18d49ec34c04fa309abf609 | PATH_REFERENCE_ONLY_CHANGED |
| `docs/question-bank/Phase1D-题库数据库导入验证.md` | f521bb8c935e96792018be9580b9728b94c8dac0b6933cc2be5911edd38620ee | PATH_REFERENCE_ONLY_CHANGED |
| `docs/rag/Phase2A-版本与知识入库审计.md` | 865253c22ef3bc351b26f37ee2a33f98b69fd01dadcc3390e0ec13398918cc72 | SAME |
| `docs/rag/Phase2B-知识文档编写审查.md` | 382ee62d9a59966e3b94e8c44b63942d10053c00d114ce83482ea5959849f0e0 | SAME |
| `docs/rag/Phase2C-离线切片验证.md` | f09c702a1e2c670fb35e5e8bd08096815085b305d7513bb56e88503e3fde1f47 | PATH_REFERENCE_ONLY_CHANGED |
| `docs/rag/Phase2C-切片内容预览.md` | 99dba4b8d0c743b7e9413eb30c1a8a066b7efc1f4fd784f54e8dc4779cf2ed44 | SAME |
| `docs/rag/Phase3A-真实服务就绪验证.md` | 91e6b720691f34a2aca71c17925939b7927cee07c82314550ed28620cf9e6754 | SAME |
| `docs/rag/Phase3B-真实知识库入库验证.md` | 7c5a264afdd1f8c4c89e01b7574c7166076a27942b6729839e495f6196b79126 | SAME |
| `docs/frontend/review/final/Phase9A-前端工程审查.md` | 7229fab63289edee6de942d9dbeb2476cbca36c03db54b80e906c58767cf4c28 | SAME |
| `docs/frontend/review/final/Phase9A-UX与可访问性审查.md` | 27b36a056a53b2d8149e2ade08c27135cec7975d55e85b25c0d099f55a7b2ad2 | PATH_REFERENCE_ONLY_CHANGED |
| `docs/frontend/review/final/Phase9A-独立盲审.md` | 2571e71ee7da8b3fdc15409361bd4cf454c066a7bb74533364b15776c3a02b41 | PATH_REFERENCE_ONLY_CHANGED |
| `docs/frontend/review/final/Phase9B-最终问题台账.md` | 6473f9cdcae436fa4f3d6ee5b898bdcbda19895a713d693f651f0f14140d573a | SAME |
| `docs/learning/AI模拟面试系统-学习与源码复盘-2026-07-18.html` | 0b8c63e1f4d501e4de137ff6a14df1fd6e944b26989a5f8a6c16011051e5c84c | PATH_REFERENCE_ONLY_CHANGED |
| `docs/study/AI模拟面试系统-后端源码与面试学习手册.md` | 581f6c893ba8edba30ea78a47e60b58a5bc79e82f969b20a47ec236cde0e72d7 | SAME |
| `docs/study/AI模拟面试系统-后端源码与面试学习手册.html` | 9cfa4151fda70d96f0cd59df2cfe59d21192451d2d99e39a94abb783c51207f7 | SAME |
| `docs/study/AI模拟面试系统-后端源码与面试学习手册.pdf` | 3d24ab4a1200bab52a9309c0fbe8f1f0144eb569a770bd977157c2bdaaed4f12 | SAME |
| `docs/frontend/prototype/Phase7D-V1-Kimi视觉审查包.md` | 78f3cffad9f914e9e96deb80590620a48da8e2fbb56d144c3e1ce58800acebe4 | SAME |
| `docs/99-历史归档/前端审核包/phase-8b3-interview-session-review.zip` | 27ddf9ea731a01fa38dfae66d694bc6a6b85411c0d85e916da77a0928507f545 | SAME |
| `docs/99-历史归档/前端审核包/phase-8b3-repair-review.zip` | 1916e5e45021fffece7703c4604a091d26c546270bccfd09884c1b90448a37dd | SAME |
| `docs/99-历史归档/前端审核包/phase-8b4-history-detail-report-review.zip` | 4d17667d58790086b1c1263ccbb5135cb12627a9d832eee0da800af742235a0e | SAME |
| `docs/99-历史归档/前端审核包/phase-8b5-remaining-surfaces-review.zip` | e873c08b971658e9faf63479716615a5a7fa9148177e98e544c766340a833ad1 | SAME |
| `docs/99-历史归档/前端审核包/phase-8b5r-8c-review.zip` | 93de32e392f50f7afa17483934921ae815c2177dbaa16a7df0ae2c20dbc988ea | SAME |
| `docs/99-历史归档/前端审核包/phase-9b-final-repair-review.zip` | d6bb73b4ac71881be17bf162c60cf1c6add7141c6638b709387c7a19e503d949 | SAME |
| `docs/99-历史归档/前端审核包/phase-8a-repair-review.zip` | ef4b693e799535874a5a711196f0918bddf2f59bf913055c7fc27990dd98b698 | SAME |
| `docs/99-历史归档/前端审核包/phase-8b1-auth-product-shell-review.zip` | 4ec5593efd1fc3eb0d6fb4b7a1f9a3a817d83c765f08e3d807efd0cdf937f34b | SAME |
| `docs/99-历史归档/前端审核包/phase-8b2-dashboard-create-review.zip` | 45e7aeec735aff45760881620e0e5c6f0dcabb3ccbc2899726f494803d35553d | SAME |
| `docs/99-历史归档/前端审核包/src.zip` | 71034e2d180483bbc45d10208a28e8df427677bbeccaa7a907669baf31dcd9b1 | SAME |
| `docs/99-历史归档/题库副本/Phase1-题库-根目录副本.md` | 6278fd0ca2bf48c6256fa9bfd1f72ffa1925bb1a70eb2bb11fa67c5f8a06e296 | SAME |
| `docs/99-历史归档/题库副本/Phase1-题库-SQL目录副本.md` | 6278fd0ca2bf48c6256fa9bfd1f72ffa1925bb1a70eb2bb11fa67c5f8a06e296 | SAME |
| `docs/99-历史归档/Spring-Boot初始帮助.md` | b2e7b241538c4d50ba54cb914ea765341adf7d4f572b7b92352da881088a3747 | SAME |



## 15. 是否生成 commit

`GIT_COMMIT_SKIPPED`。

精确命令 `git add -f -- docs/README.md` 返回：`fatal: Unable to create ... .git/index.lock: Permission denied`。结合此前指定分支失败，当前执行环境没有可用的 Git 写入权限。未继续尝试危险 staging、未更改 ACL、未用其他工具绕开权限、未请求 Owner 或 escalation。

没有 `git add -A`，没有 `git add -f docs/**`，没有批量 force-add；暂存区最终为空。预期 commit message 为 `chore: organize project documentation and archive historical files`，仅供未来按精确范围提交时使用，不表示已经提交。

## 16. commit hash

`N/A`（未生成本轮 commit）。HEAD 仍为执行前的 `e2cab27`，它不是本轮整理的 commit。Push 未执行。

## 17. 未解决风险与最终结论

1. 当前工具的 Git 写入限制仍在；本轮已经完成本地整理、验证与报告，但没有独立 commit。
2. docs exclude 保留，因此文档目标、ZIP 与本地归档不在普通 Git diff 中完整显示；审阅时结合中文导航、完整映射和 SHA 对照。tracked Jev 旧路径显示 D，实际内容在新目标完整保留。
3. 默认 Maven 命令在当前未指定 profile 的 MVC 测试环境下失败；显式 test profile 的全量运行通过。没有把默认失败伪报为 PASS，没有修复超出 scope 的测试/业务。
4. 默认前端 config bundle 命令受 spawn EPERM 影响；本轮用现有 CLI native loader / threads 完成等价单测与生产构建，默认路径的问题未改代码解决。
5. 历史报告、旧名显示文字、JSON evidence 和 ZIP 内部旧名仍保留历史语义；其原结论、基线、状态和 hash 不代表 2026-10-05 重新验收过真实 AI。

本轮临时检查脚本、旧名 pattern 和原始快照在最终核验后删除，仅保留执行日志于已有 ignored target 目录，无新增长期工程脚本。下一步可由 Owner 按本导航和报告审阅本地结果，并在具备 Git 写入权限的环境中精确暂存提交；本轮不 push。


## 附录：执行前完整未跟踪文件清单

以下按执行前 `git status --short --untracked-files=all` 原样记录，共 216 个 Git 可见未跟踪文件；ignored docs、依赖、构建产物和 local 凭据不在此列表内。Owner 原有内容均保留。

```text
"AI 模拟面试系统｜项目设计记录 v1.1.md"
DATA_ENVIRONMENT_AUDIT.md
DATA_ENVIRONMENT_AUDIT_CLOSURE.md
PHASE_0_5_ENVIRONMENT_CLEANUP.md
PHASE_1_QUESTION_BANK_FINAL.md
PHASE_9B_FINAL_REPAIR_REPORT.md
PHASE_9C_FULL_REAL_E2E_REPORT.md
agents/skills/ai-interview-finalization/SKILL.md
agents/skills/ai-interview-frontend/SKILL.md
frontend/.gitignore
frontend/README.md
frontend/eslint.config.js
frontend/index.html
frontend/package-lock.json
frontend/package.json
frontend/public/favicon.svg
frontend/scripts/phase-8b2-mock-api.mjs
frontend/scripts/phase-8b3-mock-api.mjs
frontend/scripts/phase-8b4-mock-api.mjs
frontend/scripts/phase-8b5-mock-api.mjs
frontend/scripts/phase-9b-capture.mjs
frontend/scripts/phase-9b-mock-api.mjs
frontend/scripts/phase-9b-real-smoke.mjs
frontend/src.zip
frontend/src/App.vue
frontend/src/api/apiError.ts
frontend/src/api/endpoints.ts
frontend/src/api/httpClient.ts
frontend/src/api/resultEnvelope.ts
frontend/src/api/unauthorized.ts
frontend/src/app/router/documentTitle.ts
frontend/src/app/router/guards.ts
frontend/src/app/router/index.ts
frontend/src/app/router/routes.ts
frontend/src/app/router/types.ts
frontend/src/app/router/unauthorizedNavigation.ts
frontend/src/app/session/sessionScope.ts
frontend/src/app/stores/auth.ts
frontend/src/app/styles/base.css
frontend/src/app/styles/reset.css
frontend/src/app/styles/shells.css
frontend/src/app/styles/tokens.css
frontend/src/app/styles/utilities.css
frontend/src/app/views/ForbiddenView.vue
frontend/src/app/views/NotFoundView.vue
frontend/src/components/shell/InterviewFocusShell.vue
frontend/src/components/shell/ProductShell.vue
frontend/src/components/shell/PublicShell.vue
frontend/src/components/shell/SessionSplitWorkspace.vue
frontend/src/components/shell/useDismissibleMenu.ts
frontend/src/components/ui/BaseButton.vue
frontend/src/components/ui/BaseIconButton.vue
frontend/src/components/ui/BaseInput.vue
frontend/src/components/ui/BaseTextarea.vue
frontend/src/components/ui/EmptyState.vue
frontend/src/components/ui/LoadingBlock.vue
frontend/src/components/ui/PageHeader.vue
frontend/src/components/ui/ProductMark.vue
frontend/src/components/ui/StatusNotice.vue
frontend/src/env.d.ts
frontend/src/features/admin/knowledge/api/knowledgeAdminApi.ts
frontend/src/features/admin/knowledge/composables/useAdminKnowledge.ts
frontend/src/features/admin/knowledge/copy/knowledgeAdminCopy.ts
frontend/src/features/admin/knowledge/storage/knowledgeRecentStorage.ts
frontend/src/features/admin/knowledge/utils/knowledgeFileRules.ts
frontend/src/features/admin/knowledge/views/AdminKnowledgeView.vue
frontend/src/features/admin/questions/api/questionAdminApi.ts
frontend/src/features/admin/questions/composables/useAdminQuestionDetail.ts
frontend/src/features/admin/questions/composables/useAdminQuestionForm.ts
frontend/src/features/admin/questions/composables/useAdminQuestionList.ts
frontend/src/features/admin/questions/copy/questionAdminCopy.ts
frontend/src/features/admin/questions/copy/questionDisplay.ts
frontend/src/features/admin/questions/utils/questionFormRules.ts
frontend/src/features/admin/questions/utils/questionQuery.ts
frontend/src/features/admin/questions/views/AdminQuestionDetailView.vue
frontend/src/features/admin/questions/views/AdminQuestionFormView.vue
frontend/src/features/admin/questions/views/AdminQuestionListView.vue
frontend/src/features/auth/api/authApi.ts
frontend/src/features/auth/api/userApi.ts
frontend/src/features/auth/components/AuthField.vue
frontend/src/features/auth/components/PasswordField.vue
frontend/src/features/auth/copy/publicProductCopy.ts
frontend/src/features/auth/navigation/passwordChangedNotice.ts
frontend/src/features/auth/storage/authStorage.ts
frontend/src/features/auth/validation/authFormRules.ts
frontend/src/features/auth/views/LoginView.vue
frontend/src/features/auth/views/RegisterView.vue
frontend/src/features/dashboard/components/DashboardInterviewFlow.vue
frontend/src/features/dashboard/components/DashboardRecentList.vue
frontend/src/features/dashboard/composables/useDashboardHistory.ts
frontend/src/features/dashboard/views/DashboardView.vue
frontend/src/features/history/api/historyApi.ts
frontend/src/features/history/components/HistoryPagination.vue
frontend/src/features/history/components/HistoryRow.vue
frontend/src/features/history/composables/useInterviewHistory.ts
frontend/src/features/history/copy/historyCopy.ts
frontend/src/features/history/utils/historyQuery.ts
frontend/src/features/history/utils/historyScoreDisplay.ts
frontend/src/features/history/views/HistoryView.vue
frontend/src/features/interview/api/interviewApi.ts
frontend/src/features/interview/components/DifficultyCard.vue
frontend/src/features/interview/components/InterviewAnswerPane.vue
frontend/src/features/interview/components/InterviewEvaluationPane.vue
frontend/src/features/interview/components/InterviewProgress.vue
frontend/src/features/interview/components/InterviewQuestionPane.vue
frontend/src/features/interview/components/InterviewRecordMissing.vue
frontend/src/features/interview/components/InterviewSessionDock.vue
frontend/src/features/interview/components/InterviewTimeline.vue
frontend/src/features/interview/components/InterviewTimelineItem.vue
frontend/src/features/interview/composables/useCurrentInterview.ts
frontend/src/features/interview/composables/useInterviewDetail.ts
frontend/src/features/interview/composables/useInterviewReport.ts
frontend/src/features/interview/composables/useInterviewSession.ts
frontend/src/features/interview/copy/categoryDisplay.ts
frontend/src/features/interview/copy/detailCopy.ts
frontend/src/features/interview/copy/evaluationLevelDisplay.ts
frontend/src/features/interview/copy/interviewFlow.ts
frontend/src/features/interview/copy/interviewPlan.ts
frontend/src/features/interview/copy/reportCopy.ts
frontend/src/features/interview/copy/reportStatusDisplay.ts
frontend/src/features/interview/copy/sessionCopy.ts
frontend/src/features/interview/navigation/sessionNavigationContext.ts
frontend/src/features/interview/storage/evaluationGateStorage.ts
frontend/src/features/interview/storage/submitRecordStorage.ts
frontend/src/features/interview/stores/currentInterview.ts
frontend/src/features/interview/types/evaluationGate.ts
frontend/src/features/interview/types/reportPageState.ts
frontend/src/features/interview/types/sessionPhase.ts
frontend/src/features/interview/types/submitRecord.ts
frontend/src/features/interview/utils/answerValidation.ts
frontend/src/features/interview/utils/completedSession.ts
frontend/src/features/interview/utils/interviewProgress.ts
frontend/src/features/interview/utils/sessionBootstrap.ts
frontend/src/features/interview/utils/submissionRecovery.ts
frontend/src/features/interview/utils/uncertainMutation.ts
frontend/src/features/interview/views/CreateInterviewView.vue
frontend/src/features/interview/views/InterviewDetailView.vue
frontend/src/features/interview/views/InterviewReportView.vue
frontend/src/features/interview/views/InterviewSessionView.vue
frontend/src/features/profile/composables/useProfile.ts
frontend/src/features/profile/copy/profileCopy.ts
frontend/src/features/profile/views/ProfileView.vue
frontend/src/main.ts
frontend/src/types/auth.ts
frontend/src/types/enums.ts
frontend/src/types/interview.ts
frontend/src/types/knowledge.ts
frontend/src/types/question.ts
frontend/src/types/result.ts
frontend/src/types/user.ts
frontend/src/utils/formatInterviewDate.ts
frontend/src/utils/formatScore.ts
frontend/src/utils/parsePositiveSessionId.ts
frontend/src/utils/requestId.ts
frontend/src/utils/safeRedirect.ts
frontend/src/utils/utf8ByteLength.ts
frontend/tests/AdminKnowledge.spec.ts
frontend/tests/AdminQuestions.spec.ts
frontend/tests/CreateInterviewView.spec.ts
frontend/tests/DashboardView.spec.ts
frontend/tests/DifficultyCard.spec.ts
frontend/tests/HistoryView.spec.ts
frontend/tests/InterviewDetailView.spec.ts
frontend/tests/InterviewEvaluationPane.spec.ts
frontend/tests/InterviewReportView.spec.ts
frontend/tests/InterviewSessionView.spec.ts
frontend/tests/LoginView.spec.ts
frontend/tests/ProductShell.spec.ts
frontend/tests/ProfileView.spec.ts
frontend/tests/RegisterView.spec.ts
frontend/tests/answerValidation.spec.ts
frontend/tests/apiError.spec.ts
frontend/tests/authFormRules.spec.ts
frontend/tests/authInitialize.spec.ts
frontend/tests/authLogin.spec.ts
frontend/tests/authStorage.spec.ts
frontend/tests/currentInterview.spec.ts
frontend/tests/evaluationGateStorage.spec.ts
frontend/tests/helpers/adminFixtures.ts
frontend/tests/helpers/deferred.ts
frontend/tests/helpers/interviewFixtures.ts
frontend/tests/helpers/memoryStorage.ts
frontend/tests/helpers/mountApp.ts
frontend/tests/historyQuery.spec.ts
frontend/tests/knowledgeFileRules.spec.ts
frontend/tests/knowledgeRecentStorage.spec.ts
frontend/tests/parsePositiveSessionId.spec.ts
frontend/tests/questionQuery.spec.ts
frontend/tests/requestId.spec.ts
frontend/tests/resultEnvelope.spec.ts
frontend/tests/routerGuards.spec.ts
frontend/tests/safeRedirect.spec.ts
frontend/tests/sessionBootstrap.spec.ts
frontend/tests/sessionNavigationContext.spec.ts
frontend/tests/sessionScope.spec.ts
frontend/tests/submissionRecovery.spec.ts
frontend/tests/submitRecordStorage.spec.ts
frontend/tsconfig.app.json
frontend/tsconfig.json
frontend/tsconfig.node.json
frontend/vite.config.ts
phase-8b3-interview-session-review.zip
phase-8b3-repair-review.zip
phase-8b4-history-detail-report-review.zip
phase-8b5-remaining-surfaces-review.zip
phase-8b5r-8c-review.zip
phase-9b-final-repair-review.zip
sql/PHASE_1_QUESTION_BANK_FINAL.md
src/main/java/com/kun/aiinterview/config/EnvironmentIsolationInitializer.java
src/main/resources/META-INF/spring.factories
src/main/resources/application-evaluation.yaml
src/test/java/com/kun/aiinterview/config/EnvironmentIsolationInitializerTest.java
src/test/java/com/kun/aiinterview/evaluation/AiSystemBaselineEvaluationTest.java
src/test/java/com/kun/aiinterview/evaluation/BaselineTraceConfiguration.java
src/test/resources/META-INF/ai-interview-test-runtime
src/test/resources/application-test-real.yaml
```

`ORGANIZATION_STATUS = PASS_WITH_WARNINGS`
