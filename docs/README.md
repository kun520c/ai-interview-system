# AI 模拟面试系统文档导航

## 1. 我第一次看这个项目应该看什么

1. [项目设计记录 v1.1](design/AI模拟面试系统-项目设计记录-v1.1.md)：理解目标与最初设计；当前实现以源码和测试为准。
2. [后端源码与面试学习手册](study/AI模拟面试系统-后端源码与面试学习手册.md)：沿业务流程阅读源码，准备面试讲解。
3. [前端架构](frontend/FRONTEND_ARCHITECTURE.md)：理解正式 Vue 前端的组织与边界。
4. [RAG 版本与知识入库审计](rag/Phase2A-版本与知识入库审计.md)：沿下方 Phase 顺序了解知识源、切片和真实入库。
5. [Jev Judge 接入学习报告](study/Jev-Judge接入学习报告.md)：理解 Jev、DeepSeek fallback 与 Java 校验的职责。
6. [AI Baseline](evaluation/AI_SYSTEM_BASELINE.md)：阅读历史评测结果及其限制；评测通过与离线测试通过是不同结论。
7. [Phase 9C 前后端真实 E2E 验证](frontend/review/final/Phase9C-前后端真实E2E验证.md)：查看最终用户流程的历史验收记录。

各报告保留原验收日期、代码基线与历史结论，文件归位不代表重新完成真实 AI 或真实 E2E 验证。

## 2. 项目设计

- [设计目录](design/)
- [AI 模拟面试系统项目设计记录 v1.1](design/AI模拟面试系统-项目设计记录-v1.1.md)
- [项目规则与当前阶段](../AGENTS.md)

## 3. 题库

- [**Canonical Frozen Question Bank**](question-bank/PHASE_1_QUESTION_BANK_FINAL.md)：唯一规范 Markdown，保持冻结；不要将历史副本当作编辑入口。
- [规范题库 JSON](question-bank/PHASE_1_QUESTION_BANK_FINAL.json)：保留机器稳定路径与内容。
- [Phase 1 Grok 题库审查](question-bank/Phase1-Grok题库审查.md)
- [Phase 1D 题库数据库导入计划](question-bank/Phase1D-题库数据库导入计划.md)
- [Phase 1D 题库数据库导入验证](question-bank/Phase1D-题库数据库导入验证.md)

## 4. RAG

1. [Phase 2A 版本与知识入库审计](rag/Phase2A-版本与知识入库审计.md)
2. [Phase 2B 知识文档编写审查](rag/Phase2B-知识文档编写审查.md)
3. [Phase 2C 离线切片验证](rag/Phase2C-离线切片验证.md)，配合[切片内容预览](rag/Phase2C-切片内容预览.md)
4. [Phase 3A 真实服务就绪验证](rag/Phase3A-真实服务就绪验证.md)
5. [Phase 3B 真实知识库入库验证](rag/Phase3B-真实知识库入库验证.md)

[source-documents](rag/source-documents/) 是实际入库知识源，文件名、内容及历史 hash 属于机器与证据契约，不要随意改名或改写。RAG 用于答案评价证据检索，主问题仍来自冻结题库。

## 5. AI 评判系统

| 主题 | 阅读入口 | 文件性质与边界 |
|---|---|---|
| Jev | [接入学习报告](study/Jev-Judge接入学习报告.md)、[Java 结构与无效代码审计](evaluation/Jev-Java结构与无效代码审计.md) | 人工报告，记录接入结构与离线验证 |
| Jev 教材 | [增量学习教材](study/Jev-Judge增量学习教材.html) | 人工教材，可沿相对链接查看当前源码 |
| DeepSeek | [Jev 接入报告中的 fallback 说明](study/Jev-Judge接入学习报告.md)、[Jev 与 DeepSeek 评测](evaluation/JEV_EVALUATION_REPORT.md) | 前者为人工说明；后者为自动生成的历史评测报告 |
| Baseline | [评测复现与证据说明](evaluation/README.md)、[AI 系统 Baseline](evaluation/AI_SYSTEM_BASELINE.md)、[环境检查](evaluation/ENVIRONMENT_CHECK.md) | 自动生成报告保持英文文件名；遵守隔离环境规则 |
| RAG 评测与 Trace | [RAG 评测](evaluation/RAG_EVALUATION_REPORT.md)、[检索 Trace](evaluation/RAG_RETRIEVAL_TRACES.md) | 自动生成的指标与证据摘要 |
| AI E2E | [AI E2E 评测](evaluation/E2E_EVALUATION_REPORT.md) | 自动生成的历史端到端评测报告 |
| 产品 E2E | [Phase 9C 前后端真实 E2E](frontend/review/final/Phase9C-前后端真实E2E验证.md) | 人工最终验收报告 |

[评测输入](evaluation/data/)、[原始证据](evaluation/evidence/)和[评测脚本](evaluation/scripts/)保持固定路径。历史证据中的旧文件名、ID、hash 和结果不随本次整理改写；复现说明不等于授权重跑入库或付费 AI 调用。

## 6. 前端

V3 当前入口：[设计系统](frontend/V3_DESIGN_SYSTEM.md)、[交付清单](frontend/V3_FILE_MANIFEST.md)、[2026-10-18 集成验证与合并阻塞](frontend/V3_INTEGRATION_20261018.md)。历史 Mock 视觉验收与本次真实后端／本地 AI 替身联调分别记录。

B1/B2 后续修复与技术验收见 [V3 最终合并阻塞修复](frontend/V3_BLOCKER_FIX_20261018.md)。前一份集成报告和原失败证据完整保留，当前结论以这份后续报告为准。

GitHub 交付审查见 [V3 发布前独立审查](frontend/V3_PUBLICATION_REVIEW_20261018.md)及[逐文件发布清单](frontend/V3_PUBLICATION_FILE_REVIEW.md)。

七份核心契约采用“磁盘英文稳定路径 + 中文导航”：

- [前端设计系统](frontend/DESIGN_SYSTEM.md)
- [前端 API 对照表](frontend/FRONTEND_API_MATRIX.md)
- [前端架构](frontend/FRONTEND_ARCHITECTURE.md)
- [前端页面地图](frontend/FRONTEND_PAGE_MAP.md)
- [产品与 UX 规范](frontend/PRODUCT_UX_SPEC.md)
- [前端路由契约](frontend/ROUTE_MAP.md)
- [前端状态模型](frontend/STATE_MODEL.md)

| 区域 | 入口 | 用途 |
|---|---|---|
| prototype | [原型说明](frontend/prototype/README.md)、[原型页面](frontend/prototype/index.html) | 历史原型，与正式 Vue 工程分开 |
| 正式 frontend | [工程说明](../frontend/README.md)、[源码目录](../frontend/src/) | 当前可构建的前端工程 |
| review | [审核目录](frontend/review/)、[最终审核目录](frontend/review/final/) | 人工审核与修复验收报告 |
| screenshots | 各阶段目录内的 `screenshots*` 子目录 | 历史截图证据，保留原路径与内容 |

最终报告：[工程审查](frontend/review/final/Phase9A-前端工程审查.md)、[UX 与可访问性审查](frontend/review/final/Phase9A-UX与可访问性审查.md)、[独立盲审](frontend/review/final/Phase9A-独立盲审.md)、[最终问题台账](frontend/review/final/Phase9B-最终问题台账.md)、[最终修复验证](frontend/review/final/Phase9B-最终修复验证.md)、[真实 E2E 验证](frontend/review/final/Phase9C-前后端真实E2E验证.md)。

[Phase 7D V1 Kimi 视觉审查包](frontend/prototype/Phase7D-V1-Kimi视觉审查包.md)保留原文的 `SUPERSEDED BY PHASE 7C/7D V2` 声明，属于历史资料。

## 7. 环境与数据库

- [环境与隔离目标说明](environment/README.md)
- [数据环境审计](environment/数据环境审计.md)
- [数据环境审计闭环验证](environment/数据环境审计-闭环验证.md)
- [Phase 0.5 环境清理与隔离验证](environment/Phase0.5-环境清理与隔离验证.md)
- [环境清理 manifest](environment/PHASE_0_5_CLEANUP_MANIFEST.md)：自动生成，保留文件名。
- [数据库 SQL](../sql/ai_interview.sql)：原路径与结构不变。
- [环境工具目录](environment/scripts/)：机器固定路径，JSON 证据保留原始内容。

普通测试使用独立 test schema；真实 Smoke Tests 与 AI Baseline 必须遵循 [AGENTS.md](../AGENTS.md) 中的隔离和显式 opt-in 规则。

## 8. 学习资料

- [开发学习记录目录](learning/README.md)、[开发学习记录](learning/AI模拟面试系统-开发学习记录.md)
- [2026-07-18 学习与源码复盘](learning/AI模拟面试系统-学习与源码复盘-2026-07-18.html)
- 后端源码与面试学习手册：[Markdown](study/AI模拟面试系统-后端源码与面试学习手册.md)、[HTML](study/AI模拟面试系统-后端源码与面试学习手册.html)、[PDF](study/AI模拟面试系统-后端源码与面试学习手册.pdf)
- [Jev Judge 接入学习报告](study/Jev-Judge接入学习报告.md)
- [Jev Judge 增量学习教材](study/Jev-Judge增量学习教材.html)

## 9. 历史归档

[99-历史归档](99-历史归档/)中的内容**不代表当前版本**，不要作为当前规范修改或部署。

- [前端审核包](99-历史归档/前端审核包/)：9 份历史审核 ZIP，ZIP 内部未改写。
- [历史源码快照 src.zip](99-历史归档/前端审核包/src.zip)：原 `frontend/src.zip`，保留完整历史快照。
- [重复题库副本](99-历史归档/题库副本/)：根目录和 SQL 目录的旧副本；移动前再次确认与 canonical SHA-256 完全一致。
- [Spring Boot 初始帮助](99-历史归档/Spring-Boot初始帮助.md)：原初始化帮助。

本地文件整理与 Git 跟踪分开处理：既有 `docs/` exclude 规则保留；归档 ZIP、截图、evidence 和本地资料不会因为本次整理被批量加入 Git。

## 10. 文件命名规则

- `Phase编号-中文用途.md`，例如 `Phase2C-离线切片验证.md`。
- `技术名词-中文用途.md`，例如 `Jev-Judge接入学习报告.md`。
- `项目名-中文用途-版本.ext`，例如 `AI模拟面试系统-项目设计记录-v1.1.md`。

RAG、Jev、DeepSeek、Milvus、MySQL、Redis、JWT、API、UX、E2E、Baseline、Trace 等技术词可以保留英文。机器契约、生成器输出与冻结证据优先保持稳定路径。

## 11. 自动生成文件与固定路径

以下文件不应人工改名，也不要为了中文化修改生成器：

- `evaluation/README.md`
- `evaluation/AI_SYSTEM_BASELINE.md`
- `evaluation/ENVIRONMENT_CHECK.md`
- `evaluation/JEV_EVALUATION_REPORT.md`
- `evaluation/RAG_EVALUATION_REPORT.md`
- `evaluation/RAG_RETRIEVAL_TRACES.md`
- `evaluation/E2E_EVALUATION_REPORT.md`
- `environment/PHASE_0_5_CLEANUP_MANIFEST.md`

同时保持 `evaluation/data/`、`evaluation/evidence/`、`evaluation/scripts/`、`environment/scripts/`、`environment/*.json`、`rag/source-documents/`、前端 `screenshots*`、`../frontend/scripts/` 和 `../sql/ai_interview.sql` 的稳定路径。题库 canonical 两种格式与前端七份核心契约也保持原文件名。

## 12. 旧路径映射

以下路径相对于仓库根目录。历史 JSON evidence、ZIP 内部目录、hash 记录和历史输出可能仍使用旧名称，请通过本表定位当前文件；不修改这些原始历史引用。

| 旧路径 | 当前路径 |
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


本轮完整范围、检查与验证结果见[文件整理执行报告](PROJECT_FILE_ORGANIZATION_EXECUTION_REPORT.md)。
