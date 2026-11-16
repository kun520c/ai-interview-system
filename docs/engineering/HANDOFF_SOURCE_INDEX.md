# 一、二期工程交接：文件与证据索引

> **2026-11-16 收口更新：**下文是 11-15 的原始时点索引，不表示候选仍未提交或各路径可随 Git 克隆；当前 Git、单库恢复及私有证据边界见[后续部署与冻结记录](2026-11-16-本机部署收口与工程档案.md#最终交付冻结与恢复演练)。

核查日期：2026-11-15（Asia/Shanghai）。本索引用于定位证据，**不构成将文件上传到外部服务的授权**。本次仅生成三份交接 Markdown；未复制用户答题、模型原文、密钥、数据库备份或私人日志。文件存在不代表其内容已部署、已通过人工验收或可公开。

证据标记沿用 [完整交接](HANDOFF_TO_CHATGPT_PHASE2.md)：`VERIFIED_SOURCE` 当前文件可核；`VERIFIED_TEST` 有相应历史执行证据；`REPORTED` 为报告陈述，原始过程未全部复核；`DESIGN_ONLY` 为设计或实验；`UNVERIFIED` 为缺证据；`NO_GO` 为该项未达门槛。同一文件可以同时含源码事实、历史测试和未来建议，应逐条判断。

“分享建议”中的**可优先**仅表示本次编写时有意识避免原答和密钥，不代表完成全面敏感信息审计；任何外发仍应复核。**先脱敏**表示可能有本机路径、会话/账号线索、合成或真实答题、模型输出或私有设施信息。**不直接分享**表示不应整份上传或打包；若确需举证，先提取最小匿名摘要。

## 1. 推荐阅读顺序

1. [HANDOFF_QUICK_START.md](HANDOFF_QUICK_START.md)：新对话首先读取，用来避免将远端 main 误当本机最新实现。
2. [HANDOFF_TO_CHATGPT_PHASE2.md](HANDOFF_TO_CHATGPT_PHASE2.md)：完整状态、链路、分阶段测试和当前风险。
3. 本索引：按具体问题选择源码、最终报告或历史证据；需要独立文件时由用户选择性提供。
4. [AGENTS.md](../../AGENTS.md)：业务不可变规则、数据库批准要求和安全/测试约束。其阶段性事实有历史滞后，按本次交接和当前源码复核。
5. [M1-E 主报告第 10 节](m1e-20261115/DECISION_AND_VERIFICATION.md#10-最终收口复核2026-11-15仅-p1p3) 与 [M1-E 收口验证](m1e-20261115/closeout/VERIFICATION.json)：最新代码边界和较早测量的关系。先读摘要，再决定是否需要原始数据。
6. [Q2 诊断](../evaluation/q2-20261114/DIAGNOSTIC.md)、[Q2-R2 对照](../evaluation/q2-20261114/q2-r2/COMPARISON_SUMMARY.md)：明确评分 NO-GO，且 v5 和评分规则候选未应用。
7. 按任务查阅具体源码及其测试；需要时间线时再读 [工程历史](PROJECT_ENGINEERING_HISTORY_20261111_20261115.md)。不要让历史设计或简历候选覆盖后续实现/失败。

## 2. 规则、状态与长期工程记录

| 文件路径 | 用途 / 所属阶段 | 对应源码或测试 | 证据等级 | 敏感信息与分享建议 |
|---|---|---|---|---|
| [AGENTS.md](../../AGENTS.md) | 全阶段项目规则、数据库和测试约束；部分阶段性事实已滞后 | 全项目 | VERIFIED_SOURCE；历史结果需逐项核对 | 有本机路径；先脱敏。可说明规则，避免将旧时点视为现状。 |
| [README.md](../../README.md) | 已提交基线的项目、架构、运行说明 | 主应用、前端及历史测试 | VERIFIED_SOURCE；MQ 描述对本地工作区已过时 | 通常可分享；应同时附本交接的更新说明。 |
| [pom.xml](../../pom.xml)、[package-lock.json](../../frontend/package-lock.json) | 后端依赖与前端锁定版本；含未提交 MQ client | 应用及前端构建 | VERIFIED_SOURCE | 主要为依赖信息；检查后可分享。 |
| [application.yaml](../../src/main/resources/application.yaml)、[application-test.yaml](../../src/test/resources/application-test.yaml)、[application-test-real.yaml](../../src/test/resources/application-test-real.yaml) | 公共/测试配置、开关和隔离目标 | `EnvironmentIsolationInitializer`、`ReportAsyncProperties` | VERIFIED_SOURCE | 文件使用变量占位；先复核。**不要提供被忽略的本地配置或环境变量值。** |
| [IMPROVEMENT_LEDGER.md](IMPROVEMENT_LEDGER.md) | J1～M1 工程批次、时点状态、测试与取舍 | 各阶段报告和对应源码 | REPORTED + 部分 VERIFIED_TEST | 可能有本机环境、历史身份或细节；先脱敏。工作区修改未提交。 |
| [ENGINEERING_CASEBOOK.md](ENGINEERING_CASEBOOK.md) | Q2、M1-A～E 问题、方案、取舍摘要 | 事务/MQ/前端及质量证据 | REPORTED + 部分 VERIFIED_TEST | 有本机报告路径；先脱敏；文件未跟踪。 |
| [RESUME_EVIDENCE_BANK.md](RESUME_EVIDENCE_BANK.md) | 可核实的候选表达及禁止夸大范围 | 对应历史报告 | REPORTED | 可能涉及个人贡献表达和路径；先由用户审核，不证明本人独立实现或掌握。 |
| [SOURCE_LEARNING_BACKLOG.md](SOURCE_LEARNING_BACKLOG.md) | 源码调用链、学习练习和未确认的掌握状态 | 对应 Service/Mapper/测试 | VERIFIED_SOURCE + REPORTED | 可能含历史场景与本机路径；先脱敏。不要将学习项标为已掌握。 |
| [PROJECT_ENGINEERING_HISTORY_20261111_20261115.md](PROJECT_ENGINEERING_HISTORY_20261111_20261115.md) | J1/B、D、U/S、Q、M1 的完整时间线和证据 | H01～H21 各自给出源码与测试 | REPORTED + 部分 VERIFIED_SOURCE/TEST | 含具体样例、路径和历史标识；先脱敏。未提交。 |
| [PROJECT_SOURCE_LEARNING_GUIDE.md](PROJECT_SOURCE_LEARNING_GUIDE.md)、[PROJECT_INTERVIEW_CASES.md](PROJECT_INTERVIEW_CASES.md) | 学习顺序、工程案例和表达边界 | 各阶段对应实现/测试 | REPORTED | 可能有本机路径及候选个人贡献；先审核再分享。 |
| [CHAT_DISCUSSION_DECISIONS_20261111_20261115.md](CHAT_DISCUSSION_DECISIONS_20261111_20261115.md) | 既有对话决策的核证草稿 | 工程历史、Q1/Q2 和 M1 资料 | REPORTED | 含技术案例与本地线索；并非完整旧对话原文，先脱敏。 |

## 3. 按阶段定位报告和数据

本节中的报告均为本机现存文件；独立报告不是 Git 仓库内容，若新对话无法读取本机，应由用户按需选择性提供。下表只指向证据，不建议整目录打包。

| 文件路径 | 用途 / 所属阶段 | 对应源码或测试 | 证据等级 | 敏感信息与分享建议 |
|---|---|---|---|---|
| [JEV_REMOVAL_DEEPSEEK_ONLY_20261111.md](../verification/JEV_REMOVAL_DEEPSEEK_ONLY_20261111.md)、[J1_B3_PHASE_CLOSEOUT_20261111.md](../verification/J1_B3_PHASE_CLOSEOUT_20261111.md) | J1 单模型、B3 收口及回归 | `DeepSeekJudgeProvider`、`InterviewReportFinalReader` 等 | VERIFIED_TEST + REPORTED | 本机路径和历史配置；先脱敏。 |
| [B1](../verification/B1_CONCURRENT_IDEMPOTENT_REPLAY_20261111.md)、[B2](../verification/B2_POST_CLAIM_FAILURE_RECOVERY_20261111.md)、[B3](../verification/B3_REPORT_FINAL_CONSISTENCY_20261111.md) | 并发重放、领取后故障、FINAL 完整性 | Workflow、事务 Service、Answer/Report Mapper 与对应集成测试 | VERIFIED_TEST + REPORTED | 测试身份和日志路径可能存在；先脱敏。 |
| [B4-A](../verification/B4A_DEEPSEEK_RELIABILITY_AUDIT_20261112.md)、[B4-B](../verification/B4B_DEEPSEEK_LOG_SAFETY_20261112.md)、[B4-C](../verification/B4C_DEEPSEEK_TIMEOUT_RETRY_20261112.md)、[B4-D](../verification/B4D_MILVUS_RETRY_DEADLINE_20261113.md)、[B4-E](../verification/B4E_REPORT_STALE_RECOVERY_20261113.md) | 外部依赖可靠性、日志、超时及旧报告恢复 | DeepSeek/Embedding/Milvus client、旧报告前端 | VERIFIED_TEST + REPORTED | 测试故障样例/路径；先脱敏。B4-E 的同步恢复不是当前 MQ 状态机。 |
| [B5 密码](../verification/B5_P2A_PASSWORD_UTF8_BOUNDARY_20261113.md)、[B5 Embedding](../verification/B5_P2B_EMBEDDING_LOG_SAFETY_20261113.md)、[C6 收口](../verification/C6_RELIABILITY_PHASE_CLOSEOUT_20261113.md) | 输入边界、日志安全及历史完整回归 | `PasswordPolicy`、Embedding adapter 与测试 | VERIFIED_TEST + REPORTED | 可能有脱敏哨兵和本机路径；先审核。 |
| [D1 题库验收](../verification/D1_QUESTION_BANK_EXPANSION_20261114.md)、[D1 题库说明](../question-bank/D1_QUESTION_BANK_V2_20261114.md) | 63 题、298 点的历史构建和 evaluation 导入 | `QuestionAdminService`、Question Mapper | VERIFIED_TEST + REPORTED | 含题目/评分依据；分享前确认接收范围，不含用户答题不等于无需审核。 |
| [D2 随机组卷报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-D2-随机组卷与真实数据库验收.md) | 随机选择、29 个回滚探针、快照 | `QuestionPlanSelector`、`InterviewQuestionPlanSnapshotServiceTest` | VERIFIED_TEST | 本机实例、测试身份/题目 ID 和路径；先脱敏。 |
| [D3-A 知识覆盖报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-D3-A-RAG知识覆盖审查.md)、[D3-A 评测规程](../verification/D3A_RETRIEVAL_PROTOCOL_V1_20261114.md) | 来源、覆盖、切片与冻结评测设计 | `KnowledgeTextChunker`、检索 QueryBuilder | DESIGN_ONLY（A 时点）+ REPORTED | 可能含题库与来源细节；先审查。后续 D3-B/C/D 已实施入库验证。 |
| [D3-B/C/D 最终验收](../verification/D3_BCD_FINAL_ACCEPTANCE_20261114.md)、[D3-E 证据质量](../verification/D3E_EVIDENCE_QUALITY_REVIEW_20261114.md) | 真实 Embedding/Milvus 入库、检索及质量分母 | `KnowledgeRetrievalService`、`KnowledgeChunkMapper`、RAG tests | VERIFIED_TEST + NO_GO（质量门槛） | 含合成测试 ID、性能与路径；先脱敏；独立原始查询和用户数据不要打包。 |
| [U1 反馈](../verification/U1_MANUAL_FEEDBACK_REVIEW_20261114.md) | 题目表达和评分反馈分析 | Prompt、题库、前端等待提示 | VERIFIED_TEST + REPORTED | 含反馈案例或用户线索；先脱敏。 |
| [U1-R1 初次受阻](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U1-R1-人工反馈与真实浏览器复测.md)、[账号与归因补充](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U1-R1-账号配置与人工反馈归因补充.md)、[真实浏览器终验](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U1-R1-真实浏览器终验.md) | 同阶段三个不同时点：阻塞、补充、最终观察 | `useInterviewSession`、服务详情与浏览器测试 | VERIFIED_TEST + REPORTED | 含实例、历史身份及原始私有证据位置；先脱敏；最终缺口后来由 U2 修复。 |
| [U2 恢复与安全验收](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U2-恢复修复与运行安全验收.md) | 已保存反馈跨重启恢复、默认用户安全 | `PreviousInterviewFeedback.vue`、`InterviewService` | VERIFIED_TEST | 含测试流程/路径；先脱敏；别把无追问真实样本说成真实追问验收。 |
| [S1 可见性](../verification/S1_INTERVIEW_VISIBILITY_SECURITY_20261114.md)、[S1-R1 隔离实例终验](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-S1-R1-题目接口安全修复部署与终验.md) | 不返回未来题及受限实例切换 | `InterviewService.getDetail`、`InterviewVisibilityHttpIntegrationTest` | VERIFIED_TEST | 本机 PID/端口/测试细节；先脱敏。受限 GO 不代表当前 M1 已部署。 |
| [G2 安全同步审查](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-G2-Git安全同步审查报告.md)、[G2 同步结果](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-G2-Git安全同步结果.md) | 先阻塞、后获用户授权公开题库并正常推送的历史记录 | 历史 Git 提交和远端 | REPORTED | 涉及公开范围与 Git 安全，先审核；后续结果取代早期阻塞的时点结论。 |
| [Q1 真实评分诊断](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-Q1-真实评分质量与多候选人测试诊断.md)、[Q1-R1 归因](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-Q1-R1-真实评分错误归因与盲审准备.md)、[Q1-R2 解盲](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-Q1-R2-人工盲审解盲与评分差异.md) | 真实模型评分、风险卡、人工意见与争议 | `LlmEvaluationValidator`、评分/追问链 | VERIFIED_TEST + NO_GO | Q1-R2 等含详细答案/模型输出和解盲内容；**不直接分享**，只提炼匿名技术事实。 |
| [Q2 诊断](../evaluation/q2-20261114/DIAGNOSTIC.md)、[用户技术复核](../evaluation/q2-20261114/USER_TECHNICAL_REVIEW.md)、[Q2-R1 契约报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-Q2-R1-评分契约审计与离线验证.md)、[Q2-R2 对照](../evaluation/q2-20261114/q2-r2/COMPARISON_SUMMARY.md) | v5 撤回、Boolean 契约、552 OR 用户裁决与受阻对照 | `EvaluationPromptBuilder`、`LlmEvaluationValidator`、`FollowUpPolicy` | VERIFIED_TEST + DESIGN_ONLY + NO_GO | 可能含题答/规则原文、路径；先脱敏。候选补丁不应被当作线上状态。 |
| [M1-A 设计](2026-11-14-M1A-ROCKETMQ-ASYNC-REPORT-DESIGN.md) | 单表 Outbox、状态和故障窗口的原始方案 | 当前 async Service/Mapper、任务表 DDL | DESIGN_ONLY（当时）+ 后续 VERIFIED_SOURCE | 有本机环境/任务细节；先审核。设计与 M1-B 之后实现分开。 |
| [M1-B 数据库准入](2026-11-14-M1B-DATABASE-ADMISSION.md)、[M1-B 验收](2026-11-14-M1B-ROCKETMQ-ASYNC-REPORT-VERIFICATION.md) | 精确授权实例、迁移、实现、分层测试 | `InterviewReportTransactionService`、Task Mapper、MQ 测试 | VERIFIED_SOURCE + VERIFIED_TEST + REPORTED | 含本机实例、容器和备份路径；先脱敏；不复制备份或凭据。 |
| [M1-C 主报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-M1C-RocketMQ异步报告验收.md)、[故障记录](2026-11-15-M1C-FAILURE-INJECTION.md)、[测试环境](2026-11-15-M1C-TEST-PLAN-ENVIRONMENT.md) | 真实进程故障、A/B、验收边界 | `RealReportCrashWindowProbeTest`、`ReportOwnershipLockIsolationTest` | VERIFIED_TEST + NO_GO（整体） | 含本机环境、测试 ID/路径及部分内容示例；先脱敏。 |
| [M1-C A/B](m1c-20261115-after-fix/AB_RESULTS.md)、[M1-C 浏览器](m1c-20261115-browser/BROWSER_RESULTS.md)、[M1-C-R1 复核](M1C_R1_BOTTLENECK_REVIEW.md) | 保留原性能退化与线程/扫描归因 | `tools/m1c/`、原始 CSV/时间线 | VERIFIED_TEST + REPORTED | 含测试身份与资源细节；先脱敏。不要将不同实验分位数相减。 |
| [M1-C-R2 报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-M1-C-R2-RocketMQ多瓶颈归因与最小调优.md)、[R2 证据入口](m1c-r2-20261115/README.md) | 消费线程、扫描/轮询及局部配置收益 | `ReportMqRuntime`、`ReportOutboxDispatcher`、R2 tests | VERIFIED_TEST | 本机部署标识及路径；先脱敏。性能仅固定桩/小样本。 |
| [M1-D 正式报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-M1-D-报告生成优化与真实效果对照.md)、[D 证据入口](m1d-20261115/README.md)、[内容审核](m1d-20261115/QUALITY_AUDIT.md) | 完整/精简/Java 三种实验、质量否决、1 秒轮询 | `InterviewReportPromptBuilder`、`useInterviewReport.ts` | VERIFIED_TEST + DESIGN_ONLY + NO_GO | 有合成样本和模型输出定位；先脱敏；原型不应归入正式实现。 |
| [M1-E 主报告](m1e-20261115/DECISION_AND_VERIFICATION.md)、[收口验证](m1e-20261115/closeout/VERIFICATION.json)、[结构失败归因](m1e-20261115/closeout/structure-failure.json) | 最新 P1/P3、实测、原响应离线重放 | `ReportOutboxWakeup`、`InterviewReportView`、生成器测试 | VERIFIED_SOURCE + VERIFIED_TEST + NO_GO（内容） | 含本机演示入口、合成 Session/任务 ID 和私有文件路径；先脱敏；不要附体验账号或凭据。 |
| [M1-E 实验 CSV](m1e-20261115/EXPERIMENTS.csv)、[测试 suites](m1e-20261115/full-test-suites.json)、[计数摘要](m1e-20261115/cost-and-counts.json) | 分开复算模型桩、真实调用、前端和后端数量 | `tools/m1e/analyze.py`、原始日志 | VERIFIED_TEST；本次已复算关键计数/中位数 | CSV 有合成 session ID、用量和时序；先去标识化。费用是估算而非账单。 |

## 4. 核心源码与针对性测试入口

| 路径 | 用途 / 阶段 | 对应测试或配置 | 证据等级 | 敏感信息与分享建议 |
|---|---|---|---|---|
| [InterviewController.java](../../src/main/java/com/kun/aiinterview/interview/controller/InterviewController.java)、[InterviewService.java](../../src/main/java/com/kun/aiinterview/interview/service/InterviewService.java) | 面试、答题、历史、详情和报告入口；S1/U2 | `InterviewControllerTest`、`InterviewVisibilityHttpIntegrationTest` | VERIFIED_SOURCE | 源码未见需要复制凭据；分享前仍审查。 |
| [QuestionPlanSelector.java](../../src/main/java/com/kun/aiinterview/interview/service/QuestionPlanSelector.java)、[InterviewQuestionPlanSnapshotService.java](../../src/main/java/com/kun/aiinterview/interview/service/InterviewQuestionPlanSnapshotService.java) | D2 分类内随机和一致性快照 | `QuestionPlanSelectorTest`、snapshot tests、`InterviewPlanConfig` | VERIFIED_SOURCE | 代码通常可分享，题库/评分点内容另行审查。 |
| [InterviewWorkflowService.java](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java)、[InterviewWorkflowTransactionService.java](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java) | 答案幂等、领取、attempt fencing、FINAL 和状态转换 | `InterviewConcurrentReplayIntegrationTest`、workflow tests | VERIFIED_SOURCE | 业务代码可按需提供；避免附带真实答题 fixture。 |
| [EvaluationOrchestrationService.java](../../src/main/java/com/kun/aiinterview/interview/orchestration/EvaluationOrchestrationService.java)、[EvaluationRagQueryBuilder.java](../../src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRagQueryBuilder.java)、[KnowledgeRetrievalService.java](../../src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalService.java) | 评价/RAG 调用链及资格过滤 | QueryBuilder/KnowledgeRetrieval/Orchestration tests | VERIFIED_SOURCE | 源码先审查；不附原用户答案、真实 Prompt 和供应商密钥。 |
| [AdminKnowledgeDocumentController.java](../../src/main/java/com/kun/aiinterview/knowledge/controller/AdminKnowledgeDocumentController.java)、[KnowledgeDocumentProcessingService.java](../../src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeDocumentProcessingService.java) | 管理员上传与显式处理、MySQL/Milvus 补偿边界 | `KnowledgeDocumentProcessingServiceTest`、受保护真实 Smoke Tests | VERIFIED_SOURCE | 可按需提供源码；不附知识库私有数据、向量备份或凭据。 |
| [LlmEvaluationValidator.java](../../src/main/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidator.java)、[FollowUpPolicy.java](../../src/main/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpPolicy.java) | Q1/Q2 结构校验及 Java 追问决策 | `LlmEvaluationValidatorTest`、`FollowUpPolicyTest` | VERIFIED_SOURCE | 可按需提供；结构校验不构成内容质量证据。 |
| [InterviewReportService.java](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java)、[InterviewReportTransactionService.java](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java)、[InterviewReportFinalReader.java](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportFinalReader.java) | M1 受理、执行版本、FINAL 和最终事务 | `InterviewReportServiceTest`、`ReportTaskTransactionTest`、FINAL 集成测试 | VERIFIED_SOURCE；工作区含未提交改动 | 代码可按需提供；不要只发送 HEAD 版本。 |
| [ReportOutboxDispatcher.java](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportOutboxDispatcher.java)、[ReportOutboxWakeup.java](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportOutboxWakeup.java) | 派发租约、扫描和提交后可丢失提示 | `ReportOutboxDispatcherTest`、`ReportOutboxWakeupIntegrationTest` | VERIFIED_SOURCE；未跟踪 | 可按需提供；含内部运行细节。 |
| [ReportMqRuntime.java](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportMqRuntime.java)、[ReportMessageConsumer.java](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportMessageConsumer.java)、[ReportTaskMessage.java](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportTaskMessage.java) | MQ 生命周期、线程、消息与消费 ACK | `ReportMessageConsumerTest`、`RealReportRocketMqSmokeTest` | VERIFIED_SOURCE；未跟踪 | 可按需提供；不要附真实 Broker 凭据或私有环境配置。 |
| [InterviewReportTaskMapper.xml](../../src/main/resources/mapper/InterviewReportTaskMapper.xml)、[任务表迁移](../../sql/migrations/20261114_interview_report_task.sql) | Outbox SQL、状态/期限及 DDL | `ReportTaskMapperIntegrationTest`、数据库准入报告 | VERIFIED_SOURCE | 表结构可供审查；**分享不授权在任何目标执行**。 |
| [InterviewReportView.vue](../../frontend/src/features/interview/views/InterviewReportView.vue)、[useInterviewReport.ts](../../frontend/src/features/interview/composables/useInterviewReport.ts)、[InterviewReportView.spec.ts](../../frontend/tests/InterviewReportView.spec.ts) | M1-D/E 轮询、渐进展示、请求合并 | 收口前端 59 项及全量 349 项 | VERIFIED_SOURCE + VERIFIED_TEST | 可按需提供；代码/测试未提交，避免附私人浏览器状态。 |
| [DeepSeekInterviewReportGenerator.java](../../src/main/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGenerator.java)、[生成器测试](../../src/test/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGeneratorTest.java) | 完整报告与原结构失败离线回放 | 收口 `14/0/0/0` | VERIFIED_SOURCE + VERIFIED_TEST | 测试依赖原响应 fixture；分享前确认其合成内容和任何敏感信息。 |

## 5. 原始证据、缺失文件和隐私边界

以下是测试总览可追溯到的原始文件位置；路径只是证据定位，**这些文件可能包含内部环境、样本、完整请求或回答，不直接交给新对话**。`/tmp` 是临时位置，未来可能失效；本次检查所列路径存在，不代表逐项审核了正文。

| 阶段 | 原始执行文件或数据 | 对应验证及版本 | 证据等级 | 敏感信息与分享建议 |
|---|---|---|---|---|
| J1 / B3 | `/tmp/ai-j1-20261111/final-regression.log`；`/tmp/ai-b3-20261111/full-regression.log` | 各自历史工作区的 1526 与 1589 项普通 Maven | VERIFIED_TEST：本次核对两个汇总 | 可能有环境和测试细节；不直接分享。 |
| D1 / D2 / D3 | `backups/D1_20261114/focused-retry.log`；`backups/D2_20261114/focused.log`、`exercise.log`；`backups/D3_BCD_20261114/junit-focused.log` | 113、268、103 项定向及 D2 真实回滚探针；各自历史版本 | VERIFIED_TEST：本次核对路径存在；报告记录结果 | 有题库、数据库或测试标识线索；不直接分享。 |
| U2 / S1 | `backups/U2_BROWSER_20261114/maven-results.json`、`maven-suites.json`；`/tmp/ai-interview-s1-full-maven-final.log`、`/tmp/ai-interview-s1-frontend-tests.log` | U2 后端 1848/19；S1 后端 1850/19、前端 346；另有对应浏览器/权限场景 | VERIFIED_TEST：本次核对 U2 结果汇总及 S1 日志汇总 | U2 目录还包含测试凭据和浏览器记录；禁止整目录打包。 |
| Q1 / Q2 | `backups/Q1_20261114_scoring/usage.jsonl`；`backups/Q2_20261114_scoring/usage.jsonl`；相应报告引用的输出 | 真实模型尝试与用量；Q2 新 v5 与保存的旧 v4 结果对照 | VERIFIED_TEST：原目录和账本存在；本次未重放 | 目录含完整 Prompt、回答或模型响应；不直接分享。 |
| M1-B / M1-C | `backups/M1B_20261114/m1b-full-maven-final-2.log`、`m1b-real-mq-1.log`；`backups/M1C_20261114_1521UTC/full-backend-after-cache-fix.log`；[最终故障数据](m1c-20261114/faults-after-cache-fix.jsonl) | 工作区当时版本的 1923、1938 全量和真实 MQ/进程故障 | VERIFIED_TEST：本次核对文件存在；具体记录见原报告 | 日志/JSON 可能有标识和环境数据；不直接分享。 |
| M1-C-R2 / M1-D | `backups/M1C_R2_20261115/full-tests.log`、`real-mq-tests.log`；`backups/M1D_20261115/backend-full.log`、`backend-full-rerun.log`、`real-mq-tests.log`、`frontend-unit.log` | R2 1938 和 MQ 2；D 首轮 125 失败、重跑通过以及前端/MQ | VERIFIED_TEST：本次核对 R2 汇总与 D 文件存在；[D 计数](m1d-20261115/test-results.json) | 有合成数据、实例和路径；不直接分享。不要误把 D 的失败首轮当最终通过日志。 |
| M1-E / 收口 | `backups/M1E_20261115/full-backend-final.log`、`real-mq.log`、`full-frontend.log`；`backups/M1E_20261115/closeout/frontend-full.log`、`backend-replay.log`；[实验 CSV](m1e-20261115/EXPERIMENTS.csv) | 原后端 1947、MQ 2、测量前端 346；收口前端 349 和 Mock 回放 14；模型桩及真实调用 | VERIFIED_TEST：本次复核主要日志、计数和部分 CSV 中位数 | 私有原日志及完整调用不直接分享；先仅提供脱敏摘要。 |

| 文件路径 | 用途 / 阶段 | 对应源码或测试 | 证据等级 | 敏感信息与分享建议 |
|---|---|---|---|---|
| [M1-E 原始证据索引](m1e-20261115/EVIDENCE_INDEX.json) | 770 个历史原始文件路径、SHA-256 和大小 | 对应 M1-E 报告/计量/测试 | VERIFIED_TEST：本次 770/770 文件存在且散列匹配 | 有绝对路径；索引先脱敏，原文件不要整批上传。 |
| [M1-E 后端日志](/Users/kun/Developer/projects/ai-interview-system/backups/M1E_20261115/full-backend-final.log)、[MQ 专项日志](/Users/kun/Developer/projects/ai-interview-system/backups/M1E_20261115/real-mq.log)、[最新前端日志](/Users/kun/Developer/projects/ai-interview-system/backups/M1E_20261115/closeout/frontend-full.log)、[重放日志](/Users/kun/Developer/projects/ai-interview-system/backups/M1E_20261115/closeout/backend-replay.log) | 对应 1947/34、真实 MQ 2、前端 349、Mock 回放 14 的历史原始执行 | Maven Surefire / Vitest | VERIFIED_TEST：本次只读核对汇总 | 原日志可能含环境、标识、样例或其他敏感数据；**不直接分享**。 |
| [M1-E 完整合成报告输入输出](m1e-20261115/full-report-calls.json)、`docs/engineering/m1d-20261115/model-calls.json` | 历史模型调用的详细复核 | 报告生成器/质量审核 | VERIFIED_TEST | 含完整题目、评价和模型输出；即使报告称合成也应 **不直接分享**，先提取匿名结论。 |
| `backups/`、`/private/tmp/` 的历史 XML/日志/备份 | 各阶段原始材料；具体路径见对应报告和索引 | J1～M1 的各类测试 | REPORTED；部分本次存在性核对 | 可能含数据库导出、凭据、用户回答、原 Prompt、令牌或私人日志；**不打包、不直接分享**。临时目录可能已被系统清理。 |
| 历史 E2E 首次失败的原始模型 JSON | 较早 E2E 失败归因 | [E2E 报告](../evaluation/E2E_EVALUATION_REPORT.md)和 [工程历史 H06](PROJECT_ENGINEERING_HISTORY_20261111_20261115.md#h06) | **UNVERIFIED / 据历史记录未保存** | 缺原文；不从 500/502 推测具体数组或模型原因。 |
| 上一长期 ChatGPT 对话完整原文 | 决策和贡献来源 | 现有 [对话决策草稿](CHAT_DISCUSSION_DECISIONS_20261111_20261115.md)只是部分归档 | **UNVERIFIED / 本次未取得** | 不推断未归档决定或用户个人贡献；如用户愿意，可提供经过审查的摘要。 |
| Q2-R2 首次未知结果的供应商记录与账单 | 判断请求是否到达和是否计费 | [Q2-R2](../evaluation/q2-20261114/q2-r2/COMPARISON_SUMMARY.md) | **UNVERIFIED / 当时无响应 ID 或账单证据** | 不推断零费用，也不重发原请求。 |
| 独立人工 gold、生产流量与当前部署身份 | 评分/报告质量和上线可靠性准入 | Q1/Q2、M1-E、运行环境 | **UNVERIFIED / 缺失** | 不把 AI 自审、小样本或历史进程身份当成人工金标或当前生产证据。 |

本次检查上述关键工程索引的 Markdown 本地链接未发现缺失；不表示仓库全部历史链接和全部外部 URL 均已验证。M1-E 主报告在仓库 `docs/engineering/m1e-20261115/`；本次未发现另存于独立 Reports 目录的 M1-E 正式报告，不从缺失的文件名推测内容。
