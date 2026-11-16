# AI 模拟面试系统：一期现状与二期工程交接

> **2026-11-16 收口更新：**下文为 11-15 原始交接快照；其中“当前”“未提交”“未部署”等词只适用于当时。候选提交、受控演示和后续 MySQL 隔离恢复以[部署与冻结记录](2026-11-16-本机部署收口与工程档案.md#最终交付冻结与恢复演练)为准，原文保留用于追溯。

核查日期：2026-11-15（Asia/Shanghai）。项目根目录：`/Users/kun/Developer/projects/ai-interview-system`。本文以**本机工作区**为对象，结合实际源码、Git、历史报告和可读取的测试证据整理；不是一次新的功能验收、部署许可或个人能力认证。下一次对话开始时，应重新核查 Git、配置、运行实例和目标数据库，不能把本文的时间点当作实时状态。

配套文件：[精简首读](HANDOFF_QUICK_START.md) · [文件与证据索引](HANDOFF_SOURCE_INDEX.md)。架构与业务规则首先遵守根目录 [AGENTS.md](../../AGENTS.md)，但其中部分“当前阶段”、README 的运行说明和历史验收段落已落后于本地工作区；应结合本文件、源码和最新证据理解，不能把旧时点说明直接当现状。

## 证据标记和阅读规则

| 标记 | 含义 |
|---|---|
| **VERIFIED_SOURCE** | 本次直接核对了当前本地源码、配置、文件或 Git；只能证明文件内容，不能证明运行中的进程加载了它。 |
| **VERIFIED_TEST** | 已找到相应历史执行记录；若注明“本次复算”，表示本次核对了原日志、测试 XML 汇总、实验 CSV 或散列，**不表示本次重新执行测试**。测试结论只适用于当时版本、环境和范围。 |
| **REPORTED** | 用户摘要或历史报告的陈述，本次尚未独立核实全部原始过程；原报告可继续追溯。 |
| **DESIGN_ONLY** | 仅为设计、实验原型或候选，未作为当前正式业务路径实施。 |
| **UNVERIFIED** | 现有证据不足、对象未实测或当前状态未知。 |
| **NO_GO** | 明确没有达到该项准入，不能由其他维度的通过抵消。 |

本次没有重跑 Maven、前端、数据库、MQ、浏览器或模型测试；没有执行迁移、修改业务代码、操作服务、调用收费模型、暂存、提交或推送。历史各轮结果有重复测试，不能相加为独立测试数。

## 1. 当前项目状态

### 1.1 项目和功能

**VERIFIED_SOURCE。** 面向 Java 后端实习、校招及初级岗位的模拟面试学习项目。单体 Spring Boot 加 Vue 前端，包含注册登录、题库与知识文档管理、会话创建与随机组卷、答题及最多一次追问、RAG 辅助 DeepSeek 评价、报告、历史和弱项。主问题来自固定题库并在创建时保存快照；LLM 提供建议，Java 验证并控制业务状态、算分、追问和持久化。

**VERIFIED_SOURCE。** `pom.xml` 指定 Java 21、Spring Boot **3.5.16**、Spring AI **1.1.8**（仅 Embedding）、MyBatis Spring Boot **3.0.5**、Milvus Java SDK **2.6.20**、JJWT **0.13.0**、RocketMQ Java client **5.3.2**；MySQL 驱动、Spring Security 等依赖由项目构建管理。默认 Embedding 模型 `qwen3.7-text-embedding`、维度 1024，默认 DeepSeek 模型 `deepseek-flash`。前端锁文件解析为 Vue **3.5.43**、Vue Router **5.3.1**、Pinia **4.0.3**、Vite **8.3.0**、TypeScript **6.0.3**、Vitest **5.0.1**。详见 [pom.xml](../../pom.xml)、[前端 package-lock](../../frontend/package-lock.json) 和 [application.yaml](../../src/main/resources/application.yaml)。

**VERIFIED_SOURCE。** 本次只读命令实际返回 Temurin Java **21.0.13**、Node **24.22.1**、npm **11.19.1**。普通配置仍需外部数据库、JWT 和 Embedding 配置；真实评分另需 DeepSeek、Milvus 和有效凭据。约 2026-11-15 13:28，本次 `lsof` 列出回环地址 **15174、18482、3308、19532** 的监听者；指定检查的 19876 没出现在结果中。监听仅说明该时点可见端口被进程持有，不证明服务健康、认证可用或进程运行本工作区代码。Docker socket 本次访问被拒绝，未因此判断 Docker 或数据库停机；当前运行 JAR、真实目标数据和凭据可用性均 **UNVERIFIED**。历史 M1-E 曾在隔离 MySQL **8.0.45**、RocketMQ **5.3.2** 和本机浏览器完成有限验收，不证明该专属实验环境现在仍在运行。

### 1.2 Git、工作区和已部署边界

**VERIFIED_SOURCE。** 外层仓库当前分支 `main`，HEAD 为 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`；本地 `origin/main` 指向同一 SHA，按本地引用计算领先/落后 **0/0**。本次通过 GitHub 连接器读取远端 `main` 的 commit，也得到同一 SHA；这是本次核验时点的远端状态，没有 fetch。本地 SSH 443 查询因 `ssh.github.com` 域名解析失败而未成功，并非据此判定远端不存在。该 SHA 不能标识尚未提交的工作区内容。

**VERIFIED_SOURCE。** 交接前暂存区为空；外层仓库有 **29 个已跟踪文件修改、277 个未跟踪文件**（`git status --untracked-files=all`，不含本次新增的三份交接文件）。修改覆盖报告 Service/Mapper、隔离配置、`pom.xml`、前端和测试，以及已有工程记录；未跟踪内容包括 RocketMQ 异步实现和测试、任务表迁移、M1-B～E/Q1/Q2 证据及工具。不要用只查看 HEAD 的结论覆盖这些实现，也不要执行 `reset`、`clean`、覆盖或按目录整体暂存。实际变化应以之后的 `git status --short --untracked-files=all` 和逐文件 diff 为准。

**VERIFIED_SOURCE。** `ai-interview-system/` 内还有一个独立嵌套 Git 仓库，自己的 `main` HEAD 为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，本次其 `git status --short --branch` 未显示工作区改动；它被外层 `.gitignore` 排除，不是外层工作树或 submodule 的替身。不要对其做整理或清理。

**VERIFIED_SOURCE + VERIFIED_TEST。** 当前工作区已有 M1-B～E 的业务实现及测试，但未见属于这些工作区改动的本地提交；它们尚未因 HEAD 和远端相同而进入 GitHub。M1-E 的历史最终收口记录核对过 213 个后端生产/配置文件与当时被测快照一致；本次单独核对最终收口列出的 5 个代码/测试/资源路径哈希全部匹配，原始证据索引 770/770 文件哈希匹配。不能将这些局部/历史核对扩大为本次重新验证全部运行环境。此前 S1-R1 有过 **evaluation 隔离实例**的受控切换；M1-B～E 未经证据证明已切换到常驻实例或正式部署。

**VERIFIED_SOURCE。** README 中“尚未接入消息队列”“报告只由后续请求触发恢复”等描述反映已提交旧基线，和本地未提交异步报告实现不一致；`AGENTS.md` 中较旧的“真实 DeepSeek 未验证”、待开发前端、13 表及旧下一阶段也是历史描述。当前实现中报告异步功能默认 `report.async.enabled=false`；关闭时没有旧同步生成回退，已存在的 GENERATING 状态仍可读取，新的生成受理会返回受控不可用。不要为了让演示可用而在未知数据库上直接打开开关。

### 1.3 数据库、MQ 和准入范围

**VERIFIED_SOURCE + REPORTED。** `sql/ai_interview.sql` 是迁移前的基础快照；`sql/migrations/20261015_timeout_attempt_fencing.sql` 是旧增量；`sql/migrations/20261114_interview_report_task.sql` 是人工增量、只新增 `interview_report_task`，未并入基础快照也不自动执行。旧快照是 13 张核心表；应用任务表的目标实际为 14 张，不能推定所有库已升级。DDL 有隐式提交，不能当作与应用切换原子回滚。

**VERIFIED_TEST / REPORTED。** [M1-B 准入记录](2026-11-14-M1B-DATABASE-ADMISSION.md)记录用户曾明确批准对 `127.0.0.1:3308/ai_interview_test` 一次执行该迁移，以及本专项有界测试数据写入/清理；该目标当时已执行，不能重复建表。此批准不是对 `local`、`evaluation`、其他实例或任意后续数据操作的通用批准。M1-C/R2/D、M1-E 使用各自经当轮范围许可的专属隔离库和独立 Broker；例如 M1-E 历史隔离目标为 `127.0.0.1:3321/ai_interview_test`、专属 RocketMQ，**不由此推定可操作其他目标**。本次不授权任何迁移、导入、清理、启动或停服。

**VERIFIED_SOURCE。** 常规配置目标由 profile 和环境变量决定；项目隔离约定为 local 的 `ai_interview` / `knowledge_chunk_vectors_local`、普通测试的 `ai_interview_test` / `knowledge_chunk_vectors_test`、evaluation 的 `ai_interview_evaluation` / `knowledge_chunk_vectors_evaluation`。`EnvironmentIsolationInitializer` 在初始化前校验目标；不要绕开，也不要把测试目标覆盖为正式 local。真实 Smoke Tests 保留双层 opt-in；普通全量执行前清除 `RUN_REAL_*`、`MILVUS_ENABLED` 等覆盖。启动任何环境前应重新确认实际端点、schema、权限、数据和审批；历史目标配置不是当前可写许可。

**NO_GO。** AI 评分准确性、综合报告内容发布、生产级 MQ 全故障可靠性以及实际目标数据库/服务切换尚未通过完整准入。**限定 GO**：M1-E P1/P3 在历史测试范围内的事务、安全与体验收口；S1 在当时隔离实例的题目可见性门禁；均不构成整体发布 GO。

## 2. 系统架构与实际入口

以下路径均相对项目根目录。`Mapper.xml` 在 `src/main/resources/mapper/`，同名 Mapper 接口在相应模块的 `mapper/`。

1. **应用与认证（VERIFIED_SOURCE）。** `AiInterviewSystemApplication` → `SecurityConfiguration` → `JwtAuthenticationFilter` / `JwtTokenService`；入口 `POST /api/auth/register`、`POST /api/auth/login` 由 `auth/controller/AuthController` 和 `AuthService` 处理，`UserService`、`UserMapper.xml` 负责用户。密码 BCrypt，JWT 签名但不加密；保护请求回查数据库当前用户状态、角色及凭据版本，而非仅信任 token 声明。管理接口和资源所有权分别校验。
2. **会话创建与题库（VERIFIED_SOURCE）。** `POST /api/interviews` → `InterviewController.createInterview` → `InterviewSessionService` → `InterviewQuestionPlanSnapshotService.prepareSnapshot` → `QuestionPlanSelector`；`InterviewPlanConfig` 规定 EASY/MEDIUM/HARD 各 5/7/9 个分类内主问题。`QuestionMapper` / `QuestionScoringPointMapper` 查启用题和评分点；`InterviewSessionTransactionService`、`InterviewQuestionSnapshotFactory`、`InterviewSessionMapper`、`InterviewQuestionMapper` 负责短事务与快照。每类候选去重排序后按随机索引选一题，题目顺序仍是配置的分类顺序；跨场不保证不重复。
3. **作答、主问题及追问（VERIFIED_SOURCE）。** `POST /api/interviews/{sessionId}/answers` → `InterviewService.submitAnswer` → `InterviewWorkflowService` / `InterviewWorkflowTransactionService` → `InterviewAnswerMapper`、`InterviewQuestionMapper`、`InterviewSessionMapper`、`AnswerEvaluationMapper`。同一 `requestId` 重放还需同问题和完全相同答案；领取/重试使用 attempt 身份和 CAS，外部调用不放在长事务里。每道 MAIN 最多一个 FOLLOW_UP；无追问时主答 FINAL，有追问时主答 INITIAL、追答综合结果 FINAL。`FollowUpPolicy` 和 Java 校验决定是否追问及状态推进，模型只能建议。
4. **RAG 与评价（VERIFIED_SOURCE）。** `EvaluationContextService` → `EvaluationOrchestrationService.evaluate` → `EvaluationRagQueryBuilder` / `EvaluationRetrievalAdapter` → `KnowledgeRetrievalService` → `SpringAiEmbeddingClient`、`MilvusVectorStoreClient` → `KnowledgeChunkMapper` 的 MySQL 资格过滤 → `EvaluationPromptBuilder` → `DeepSeekJudgeProvider` / `DeepSeekEvaluationClient` → `LlmEvaluationValidator`、`EvaluationScoreCalculator`、`EvaluationStandard`、`FollowUpPolicy`。查询由题目及评分点构建，不含用户答案；评价 Prompt 可以含答案。只有符合资格且选入 Prompt 的证据由 `RagTracePersistenceService`、`RagRetrievalBatchMapper` 和 `RagHitLogMapper` 持久化。五维分数由模型建议，Java 检查范围并求和；评分点 `covered` 为布尔值，非可靠的自然语言语义证明。
5. **管理员数据入口（VERIFIED_SOURCE）。** `/api/admin/questions` → `AdminQuestionController` → `QuestionAdminService` → `QuestionMapper`、`QuestionScoringPointMapper`；知识文档 `POST /api/admin/knowledge/documents` 和 `POST /api/admin/knowledge/documents/{documentId}/process` → `AdminKnowledgeDocumentController` → `KnowledgeDocumentAdminService` → `KnowledgeDocumentProcessingService`、`KnowledgeTextChunker`、Embedding/Milvus adapter → `KnowledgeDocumentProcessingTransactionService`、`KnowledgeDocumentMapper`、`KnowledgeChunkMapper`。上传和显式处理是两个动作；MySQL 是业务真相源，跨 MySQL/Milvus 的写入不是分布式原子事务。配置见 `application.yaml` 中的 multipart、`embedding`、`milvus` 与 `recovery`，目标隔离由 `EnvironmentIsolationInitializer` 检查。
6. **FINAL、报告和弱项（VERIFIED_SOURCE）。** `InterviewReportFinalReader.aggregateFinalEvaluations` 验证每个 MAIN 的 FINAL 集合、答案及追问关联并聚合；`InterviewReportPromptBuilder` 将题目快照和 FINAL 评价送入 `DeepSeekInterviewReportGenerator`，不是将全部原答重新送入最终报告。`InterviewReportTransactionService.completeReportGeneration` 复核执行权、期限、FINAL 和分数，调用 `InterviewReportMapper`、`UserWeaknessService` / `UserWeaknessMapper`，把 Report、Weakness、Session READY 和 Task SUCCEEDED 放在同一短事务。弱项按分类与知识点汇总 FINAL，依据 Java 等级规则处理发现或恢复，不能由模型直接写库。
7. **异步消息（VERIFIED_SOURCE，工作区未提交）。** `POST /api/interviews/{sessionId}/report` → `InterviewReportService.generateReportForApi` → `InterviewReportTransactionService.acceptReportTask` → `InterviewReportTaskMapper.xml`；提交后的 `ReportOutboxWakeup` 或 `ReportOutboxDispatcher` 定时扫描 → `ReportMessageProducer` → `ReportMqRuntime` → `ReportMessageConsumer` → `claimTaskExecution` → `generateClaimedReport` → 最终事务。配置在 `application.yaml` 的 `report.async`、`ReportAsyncProperties`、`ReportAsyncConfiguration`；库表定义在任务表迁移文件。
8. **前端与只读详情（VERIFIED_SOURCE）。** `frontend/src/features/interview/views/InterviewReportView.vue`、`composables/useInterviewReport.ts`、`copy/reportCopy.ts`：读取 `GET /api/interviews/{sessionId}` 和 `GET /api/interviews/{sessionId}/report`，生成时以 1 秒周期查询报告状态；受权限保护的已保存逐题结果可先展示，不能视为综合报告 READY。详情由 `InterviewService.getDetail` 先核对归属，仅返回允许查看的问题及已保存结果，不暴露未来题、参考答案和评分点快照或原始模型输出。

## 3. 已开展阶段及最终取舍

下述“完成”均限定在所列历史范围；详细源文件、原始证据与隐私级别见 [索引](HANDOFF_SOURCE_INDEX.md)。

### J1、B 系列：单模型与工程可靠性

- **J1，2026-11-11。** 目标是移除 Jev 运行分支，保留 DeepSeek 单模型、历史记录兼容及公共校验。修改 Judge/客户端装配和对应测试、工具；历史 Jev 数据未被重写。**VERIFIED_TEST**：普通后端 `1526/0/0/19`（T/F/E/S），真实 MySQL + 外部模型替身；[J1 报告](../verification/JEV_REMOVAL_DEEPSEEK_ONLY_20261111.md)。已提交 `b525683`。后来的评分质量问题并未因此解决。
- **B1/B2/B3，2026-11-11。** 分别解决并发完成后同键重放误报、成功领取后前置查询异常未收口、报告用 INITIAL 兜底及 FINAL 集合不一致。主要为 `InterviewWorkflowService`、事务 Service、`AnswerEvaluationMapper.xml` 和报告聚合；B3 最终逻辑现由 `InterviewReportFinalReader` 承载。**VERIFIED_TEST**：B1 定向 `286/0/0/0`、B2 `296/0/0/0`、B3 定向 `134/0/0/0`，B3 普通全量 `1589/0/0/19`；使用隔离真实 MySQL 和模型替身，见 [B1](../verification/B1_CONCURRENT_IDEMPOTENT_REPLAY_20261111.md)、[B2](../verification/B2_POST_CLAIM_FAILURE_RECOVERY_20261111.md)、[B3](../verification/B3_REPORT_FINAL_CONSISTENCY_20261111.md)。指定历史错误已修复，不能说穷尽全部并发或证明模型语义正确。
- **B4/B5，2026-11-12～13。** 处理 DeepSeek 日志与超时分类、Milvus SDK 有界尝试、报告旧恢复、密码 UTF-8 字节边界、Embedding 日志边界。**VERIFIED_TEST**：例如 DeepSeek 日志定向 `281/0/0/0`、Milvus 定向 `180/0/0/0`、Embedding 定向 `195/0/0/1`；见 [工程台账](IMPROVEMENT_LEDGER.md)和 [阶段汇总](PROJECT_ENGINEERING_HISTORY_20261111_20261115.md#h04)。超时仍不证明上游未执行或未计费；旧的同步报告恢复已由本地异步路径替代，不能照搬其状态机。

### D1～D3：题库、组卷和 RAG

- **D1，2026-11-14。** 扩充题库并经服务导入与读回；历史 evaluation 为 63 题、298 评分点，EASY/MEDIUM/HARD 为 15/21/27。**VERIFIED_TEST**：定向 `113/0/0/0`，另有 4 个真实 MySQL 导入/回读阶段；未在该批运行普通全量或真实评分。[验收](../verification/D1_QUESTION_BANK_EXPANSION_20261114.md)。这是历史数据快照，不是当前库盘点或人工难度效度证明。
- **D2，2026-11-14。** 针对固定首题的问题，在 `QuestionPlanSelector` 中做分类内随机抽样，沿用创建时全量快照；已提交 `d090d25`。**VERIFIED_TEST**：定向 `268/0/0/0`；29 个真实 MySQL 回滚探针、28 场合成会话和 194 行 MAIN 快照；见 [独立 D2 报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-D2-随机组卷与真实数据库验收.md)。探针合并外层回滚事务，未证明独立提交后的跨连接可见性，也未证明长期分布。
- **D3-A～E，2026-11-14。** 从来源审查和语料设计，到隔离环境真实入库、检索及证据质量审查；沿用现有检索，不把评测工具误称为检索算法修复。**VERIFIED_TEST**：当时 51 READY 文档、52 ACTIVE chunk、52 个向量对账；真实检索 MAIN Hit@1 63/63，但选入 Prompt 的直接证据为 75/315=23.81%，公开集 67/300=22.33%，独立集 21/120=17.50%；独立正例 Hit@5 为 15/18。定向 `103/0/0/0`。标签主要是 AI 临时判断而非独立人工 gold，命中率不是评分正确率；历史仅允许 evaluation 小范围试用，质量未达预设门槛。见 [D3 最终验收](../verification/D3_BCD_FINAL_ACCEPTANCE_20261114.md)及 [D3-E](../verification/D3E_EVIDENCE_QUALITY_REVIEW_20261114.md)。

### U1、U2、S1：体验恢复与可见性

- **U1/U1-R1，2026-11-14。** 梳理题目表达、反馈和等待；两道题按历史授权修订，旧会话快照保持。**VERIFIED_TEST**：8 对合成答案 16 次真实模型 HTTP 200，旧/新分别 7/8、8/8 通过 Java 结构校验，但仍有评分争议；真实浏览器丢响应与同键恢复测试暴露跨重启上一题反馈缺口。早期受阻报告是历史，不能覆盖后续结果。见 [U1](../verification/U1_MANUAL_FEEDBACK_REVIEW_20261114.md)、[U1-R1 最终报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U1-R1-真实浏览器终验.md)。
- **U2，2026-11-14。** 复用受归属保护的详情恢复最近已保存反馈，并排除 Spring Boot 默认用户自动配置；源码在 `useInterviewSession`、`PreviousInterviewFeedback.vue`、`AiInterviewSystemApplication` 等，提交 `f1fb02b`。**VERIFIED_TEST**：后端定向 `165/0/0/0`、普通全量 `1848/0/0/19`、前端 `346/0/0/0`；真实浏览器复测已保存反馈的读取无新答案 POST/模型调用，当次真实场景无追问，追问覆盖来自替身测试。[U2 报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U2-恢复修复与运行安全验收.md)。该恢复缺口已修复，评分质量仍独立 NO-GO。
- **S1/S1-R1，2026-11-14。** 修复本人详情提前返回未答未来题；`InterviewService.getDetail` 限制可见集合，提交 `4bed0e2`。**VERIFIED_TEST**：后端定向 `179/0/0/0`；首次全量 `1850/8/0/19` 因隔离测试库/JVM 时区不一致，调整测试实例后 `1850/0/0/19`；后续隔离 evaluation 的 HTTP/浏览器及 401/403/404 门禁 GO。[S1](../verification/S1_INTERVIEW_VISIBILITY_SECURITY_20261114.md)、[S1-R1](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-S1-R1-题目接口安全修复部署与终验.md)。不推断已发生跨用户泄露，也不把该 GO 推广为全部系统安全通过。

### Q1/Q2：评分质量 NO-GO

- **Q1，2026-11-14。** 三名合成候选人完整业务报告 3/3 READY，但工程风险复核的 12 张卡有 7 张至少一处可确认输出问题；样本按风险选取，不是总体错误率，也不是独立人工 gold。存在已答误判遗漏、复合评分点、可选方案及 Java 版本事实等问题。**VERIFIED_TEST + NO_GO**：结构和业务成功不能证明评分准确；[Q1 诊断](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-Q1-真实评分质量与多候选人测试诊断.md)、[争议与解盲](../evaluation/q1-r2-20261114/CONFIRMED_ISSUES_AND_DISAGREEMENTS.md)。原卡/原答应先脱敏审查再分享。
- **Q2，2026-11-14。** v5 Prompt 在已揭盲旧样本的 18 次 DeepSeek HTTP 调用中，17 次通过解析和 Java 校验、1 次 JSON 解析失败，并有内容退化；候选撤回，当前源码保持 `evaluation-prompt-v4`。Q2-R1 发现 Boolean covered 不能表达部分覆盖，Java 并不验证文字证据是否真正来自原答。Q2-R2 七点评分口径候选的网络请求未得到可用模型输出；后续用户已明确其中 552 的 OR 口径，但新候选仍未上线、未完成新旧真实对照。**VERIFIED_TEST + DESIGN_ONLY + NO_GO**：[Q2 诊断](../evaluation/q2-20261114/DIAGNOSTIC.md)、[Q2-R2](../evaluation/q2-20261114/q2-r2/COMPARISON_SUMMARY.md)。不要把候选 patch 当成当前生产规则，也不要拿没有独立人工 gold 的旧样本反复调参充作验收。

### M1-A～E：异步报告

- **M1-A，2026-11-14。** 原同步报告 POST 阻塞外部模型；对比直接双写、事务消息和 MySQL 单表 Outbox 后，选择后者配合 RocketMQ、独立排队/执行期限与版本隔离。**DESIGN_ONLY（在 A 时点）+ VERIFIED_TEST**：独立兼容性收发探针不等于业务实现或生产可靠性；[设计](2026-11-14-M1A-ROCKETMQ-ASYNC-REPORT-DESIGN.md)。M1-B 后已落地工作区，不应继续称当前仅设计。
- **M1-B，2026-11-14。** 实施异步受理、单表 Outbox、MQ 同步回调消费、CAS 和短事务，新增任务表迁移及专项测试；详见第 4 节。**VERIFIED_TEST**：普通全量 `1923/0/0/23`、前端 `341/0/0/0`、真实 MySQL+MQ 指定 2 项通过，模型替身；专用 Broker 停机、DLQ 和重放均有指定场景证据。**REPORTED**：独立目标的任务表已批准并执行。没有正式切换/完整生产故障验收。[M1-B 验收](2026-11-14-M1B-ROCKETMQ-ASYNC-REPORT-VERIFICATION.md)。
- **M1-C / R1，2026-11-15。** 验收真实进程崩溃窗口和同步/异步对照；修复非所有者先等待会话锁，以及首次修复后 MyBatis 一级缓存导致锁后读到旧 owner 的问题：先独立 SQL 预查，再锁行，用不同语句复查。**VERIFIED_TEST**：权限/缓存相关 `75/0/0/0`，最终普通全量 `1938/0/0/34`，最终版五组进程故障各达断言；原配置在 1 秒桩高并发下完整 READY 和闭环完成率明显退化。R1 区分一个消费者实例的两条线程和实际模型在途，不将全部差距归因于 MQ。**NO_GO**：模型后崩溃需显式重试、真实报告可产生无依据判断；[故障](2026-11-15-M1C-FAILURE-INJECTION.md)、[R1](M1C_R1_BOTTLENECK_REVIEW.md)。
- **M1-C-R2，2026-11-15。** 真实测量消费执行池、扫描和页面观察，把默认消费线程从 2 调至 4、扫描 2000 ms 调至 500 ms。**VERIFIED_TEST**：定向 `131/0/0/0`、普通全量 `1938/0/0/34`、真实 MySQL+MQ 两项 `2/0/0/0`，模型替身。固定 1 秒桩、并发 10、单波 20 条下，异步旧/新闭环完成 `1.775→3.531 次/秒`；仍非相对同步更快的证明。8 线程只作候选。完整生产 MQ 和内容质量仍 NO-GO。[R2 正式报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-M1-C-R2-RocketMQ多瓶颈归因与最小调优.md)。
- **M1-D，2026-11-15。** 研究完整 DeepSeek、精简 Prompt、Java 规则三种报告；仅将正式前端轮询从 3 秒改为 1 秒。精简与 Java 原型未获内容质量接受，未成为正式生成路径。**VERIFIED_TEST**：最终后端 `1938/0/0/34`、前端 `341/0/0/0`、真实 MQ 两项通过；8 次真实 DeepSeek 中 7 READY、1 结构 FAILED。首次全量 `1938/125/0/34` 来自隔离夹具把无评分点复制题设为 ENABLED；修正夹具后才通过，不能掩盖首轮失败。报告仍有历史错误传播和无依据内容。见 [M1-D 报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-M1-D-报告生成优化与真实效果对照.md)。
- **M1-E 及最终收口，2026-11-15。** 保留 P1 提交后有界提示唤醒、P3 先展示既有逐题结果，继续 4 消费线程/500 ms 扫描/1 秒轮询；P2 提前 POST 仅隔离原型，未设为真人默认；未使用精简 Prompt、Java 替代、SSE 或扩大连接池。**VERIFIED_TEST**：后端原全量 `1947/0/0/34`；后续前端修复后全量 **349/0/0/0**、报告页 **59/0/0/0**、离线原响应重放 **14/0/0/0**，后端全量复用前一快照而非重跑。真实模型 12 次 11 READY、1 FAILED；内容发布和生产 MQ 完整可靠性仍 NO-GO。[主报告及第 10 节](m1e-20261115/DECISION_AND_VERIFICATION.md)。

## 4. RocketMQ 与单表 Outbox 的最新实现

### 4.1 状态、受理与投递

**VERIFIED_SOURCE。** `interview_report_task` 同时是任务和 Outbox，不另设一张 Outbox 表。业务 `task_state` 为 `QUEUED → RUNNING → SUCCEEDED/FAILED`；投递 `delivery_state` 为 `PENDING → LEASED → SENT`，可在发送不确定后退避为 PENDING 或等租约过期重领。两组状态并行，不能将 SENT 当成报告成功；消息只含 `taskId`、`sessionId`、`attemptVersion`，不放用户答案、Prompt 或凭据。`UNIQUE(session_id, attempt_version)` 防相同 attempt 重复，活跃任务唯一性还依赖 Session 行锁和业务入口检查。

**VERIFIED_SOURCE。** 受理先用独立 `getReportOwnerId` 非锁定查询拒绝非所有者，再按 Session → Task 顺序锁定并用另一个查询复核 owner、状态及完整 FINAL；同一 READ_COMMITTED 短事务以 Session version CAS 置 GENERATING、插入 QUEUED/PENDING 和期限。重复 POST 在 GENERATING 时读取当前状态；READY 复用已有报告；FAILED 的新申请创建新 task/attempt。历史 GENERATING 但任务缺失会告警，不通过 POST 猜测接管。版本空间预留与溢出受控处理见事务 Service。

**VERIFIED_SOURCE。** `ReportOutboxDispatcher` 查询到期待发任务/过期租约，以随机 token CAS 领取后在数据库事务外发 MQ；发送结果不确定可能已到 Broker，故有限退避重发。只有 `SEND_OK` 才记 SENT。`markSent` 用原租约 token，仅更新投递字段；即使消费者先完成也不会把 SUCCEEDED 或 FAILED 改回去。任务时间比较使用 MySQL `UTC_TIMESTAMP(6)`，而非用 `updated_at` 假充实际提交时间。

### 4.2 唤醒、消费、完成与故障

**VERIFIED_SOURCE。** 任务插入后发布事件，`ReportOutboxWakeup` 仅在事务 **AFTER_COMMIT** 尝试向独立的 1 线程/容量 1 执行器提交提示，`AtomicBoolean` 合并提示，默认拒绝策略不会在 POST 线程执行 Broker 网络 I/O。提示可能丢失、被拒绝或进程在提交后退出；持久任务仍在，健康实例的 500 ms 定时扫描是兜底。二者调用同一 `dispatchDue → claimDispatch`，没有第二套无围栏发送路径。

**VERIFIED_SOURCE。** `ReportMqRuntime` 使用 `DefaultMQPushConsumer` / `MessageListenerConcurrently`；默认 min=max **4**、消费 batch/pull batch 各 1、单队列 pull threshold 8、最多重消费 5 次、消费超时 12 分钟。producer 应用层发送重试关闭；每 10 秒尝试连接，Broker 启动失败不会阻止数据库过期扫描启动。默认扫描批量 16、发送超时 5 秒、租约 30 秒、发送上限 20、队列和执行期限各 600 秒；两种预算通过配置校验。以上是源码默认值，不是当前运行进程实参或生产容量建议。

**VERIFIED_SOURCE。** 消费回调同步完成 DB 领取与业务处理；Session/Task 锁内校验消息三标识、状态和期限，CAS 胜者获得固定 `executionVersion`。重复、终态、过期或旧任务在数据库可确认后不重复生成；数据库读取/提交结果不确定、非法消息等异常返回重试，不先转本地线程 ACK。外部报告模型调用在事务外；最终事务再复核固定版本和期限，只有成功提交报告、弱项、Session READY、Task SUCCEEDED 后才正常确认。历史已有报告分支不重复更新弱项并告警待审计。

**VERIFIED_TEST。** M1-C 的隔离 MySQL、真实 Broker、模型替身和真实进程故障记录：① 受理提交后退出、Broker 停复，健康实例扫描补发；② 发送成功后退出，可能重发但指定案例报告/弱项只提交一次；③ 重复消息和两个消费者竞争只允许一个有效执行；④ DB 完成后 ACK 前杀进程，重投未再次调用模型替身；⑤ 模型返回后、最终事务前进程死亡，原任务期限到后 FAILED，用户显式重试可再次调用模型。最终版五组达到各自断言；部分期限由测试注入，不能当自然等待 600 秒或全部真实网络 ACK 丢包。旧/新执行者四种交错另由真实 MySQL `4/0/0/0` 验证。详见 [故障记录](2026-11-15-M1C-FAILURE-INJECTION.md)。

**UNVERIFIED / NO_GO。** RUNNING 消费者死亡不会自动重新生成直至 READY；即使重复消息被确认，也要等期限/扫描器收口 FAILED，之后由用户显式重试，可能再次付费。数据库或扫描器不可用时无法保证准时收口；没有活跃实例时也不能自动扫描。尚缺 JDBC commit 结果未知、真实 MySQL 停机、Broker 磁盘永久故障、长期积压、多进程重平衡、真实模型成功后 kill、可靠监控和目标环境恢复演练的完整验收。不得宣称 MQ/DB 分布式原子事务、消息零丢失、模型恰好一次或生产高可用。

### 4.3 页面与内容质量

**VERIFIED_SOURCE + VERIFIED_TEST。** P3 在完整报告未开始、生成中或失败时复用已认证用户、已完成 Session 的详情，显示已经保存的逐题评价及 FINAL 分数；这不是综合报告，也不将状态置 READY、重算总分或自动触发模型。最终收口修复同会话/epoch 下手动检查与定时 GET 重叠，复用在途 Promise；终态或离页停止定时器，切换路由后的旧响应不能覆盖新页。已发出的旧 HTTP 请求并不保证传输层取消。

**VERIFIED_TEST + NO_GO。** M1-E 的 12 次真实 DeepSeek 完整报告尝试均 HTTP 200，11 READY、1 FAILED；失败响应可解析但 strengths/weaknesses/suggestions 分别为 7/7/5，超过各 1～5 项的既有约束。原日志只记 `GENERATION_FAILED`；相同原始响应字节的离线 Mock HTTP 重放证实首先被 strengths 数量校验拒绝；失败任务没有报告或弱项写入，逐题评价仍在。没有依据归咎于 P1、网络截断或推断模型违约原因；没有通过截断、放宽校验或付费重试掩盖失败。另有空文字证据下推断具体弱项、继承历史 Java 版本错误等内容问题；没有独立人工留出评审，内容准确性仍 NO-GO。

### 4.4 必须分开的五种时延/吞吐

| 指标 | 定义与历史例子 | 不能推断 |
|---|---|---|
| POST 受理延迟 | 请求进入到持久受理响应；M1-E 真实 6 对中位数基线/候选约 **13.08/13.02 ms**。 | 并非模型完成或用户得到报告。 |
| 数据库 READY 时间 | 明确计时点的最终事务 `afterCommit`；M1-E 固定 1 秒桩、并发 1、各 8 条，使用同一候选 JAR 切换唤醒开关，受理事务 afterCommit→完成事务 afterCommit 中位数 **1.463→1.040 s**。 | 不是页面时间，不是模型提速，也不是持久承诺；旧 M1-C 的 `updated_at` 只能近似，可能被迟到 SENT 改写。 |
| 页面观察 READY 时间 | 浏览器或客户端读取到 READY、实际可见的时间，需说明 GET/DOM 和起点。M1-E A 完整报告页等待 **4.233→5.219 s**，C **4.212→4.189 s**，B 候选 FAILED 无完整报告时间。 | 不能删去失败样本或宣称完整报告稳定变快；DOM 与物理屏幕绘制也不完全相同。 |
| 首次有用内容可见 | M1-E A/B/C **3 对**真实浏览器，页面导航到视口中首次出现既有 FINAL 分数或完整摘要的中位数 **4.223→0.085 s**；候选 B 失败仍显示已有逐题结果。 | 两侧内容不同；不是完整 AI 报告提速、准确率或稳定分布。 |
| 闭环完成吞吐 | M1-C-R2 1 秒桩、并发 10、各 20 条，旧配置/4 线程 500 ms 为 **1.775→3.531 次/秒**；包含客户端等待与排空。 | 不是 POST 独立受理能力、长期生产 QPS 或生产 SLA。 |

**VERIFIED_TEST，本次复算。** M1-E `EXPERIMENTS.csv` 的 c1 每组 8 条中位数为 **1462.567→1040.465 ms**；浏览器 A/B/C 的候选首次有用中位数 **84.7 ms**，基线 **4223 ms**。相同实验 c8 各 8 条流水线中位数 **2817.341→2820.289 ms**，没有收益；真实报告 5 对双方成功的配对流水线中位差候选快约 45.25 ms，但存在变快和变慢，不能排除失败的第 6 对。先前 48 条 c1/c8 标签因驱动实际并发 2/9 被降为探索数据，不可当正式样本。

**VERIFIED_TEST。** M1-C 初始完整 A/B：1 秒桩、客户端并发 10、各 30 条，原同步/异步 POST P95 **1046.5/24.9 ms**，客户端 READY P95 **1046.5/6967.8 ms**，闭环完成 **7.252/1.489 次/秒**；原 A 模型在途最多 10、B 两条消费线程，200 ms GET，模型桩和客户端闭环不是等效并发。R2 同协议旧/新异步配置客户端 READY P95 **6067.4→3563.6 ms**、DB 完成速率 **1.800→3.641 次/秒**；单波 20 条与旧实验两波各 15 条不能混算。原始对照见 [M1-C A/B](m1c-20261115-after-fix/AB_RESULTS.md)、[R2](m1c-r2-20261115/README.md)和 [M1-E](m1e-20261115/DECISION_AND_VERIFICATION.md)。

## 5. 重要测试总览

表中 `T/P/F/E/S` 为总数/实际通过/失败/错误/跳过；`—` 表示不适用或未记录，非零失败的历史首轮不抹去。各行执行数量均为 **VERIFIED_TEST**：有历史执行证据，其中已独立复算的范围另行注明；报告中的内容判断仍须区分 **REPORTED / NO_GO**，不能把测试通过扩展成语义正确。各版本的“工作区”还包括当时未提交 diff；不能仅凭 HEAD SHA 复原。各阶段原始日志或数据的精确路径见 [索引第 5 节](HANDOFF_SOURCE_INDEX.md#5-原始证据缺失文件和隐私边界)。**所有执行均为历史记录，本轮未重跑。**

| 阶段及日期 | 类型 / 当时版本 | T/P/F/E/S 或实际数量 | 模型 | 真实 MySQL / MQ | 适用范围与原始入口 |
|---|---|---:|---|---|---|
| J1，11-11 | 普通 Maven；J1 工作区，后提交 `b525683` | 1526/1507/0/0/19 | 替身 | 是 / 否 | 单模型迁移，非评分准确性；[J1](../verification/JEV_REMOVAL_DEEPSEEK_ONLY_20261111.md)。 |
| B3，11-11 | 定向及普通全量；B1～B3 工作区，后提交 `6de9e7a` | 1589/1570/0/0/19（全量） | 替身 | 是 / 否 | FINAL/弱项和历史兼容；[B3](../verification/B3_REPORT_FINAL_CONSISTENCY_20261111.md)。 |
| D1，11-14 | 定向 JUnit，另有受控导入 | 113/113/0/0/0；导入读回 4 阶段 | 无 | 是 / 否 | 题库结构和当时 evaluation 数据；[D1](../verification/D1_QUESTION_BANK_EXPANSION_20261114.md)。 |
| D2，11-14 | 定向 + 回滚探针；`0c6f92a` + D2 diff，后提交 `d090d25` | 268/268/0/0/0；29 个回滚探针 | 无 | 是 / 否 | 随机选择和快照，非提交后可见性；[D2 报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-D2-随机组卷与真实数据库验收.md)。 |
| D3，11-14 | 定向 + 真实检索/一次有限业务；冻结语料与工作区 | 103/103/0/0/0；63 MAIN 检索，1 场 EASY 含恢复 | 真实 Embedding/DeepSeek | 是 / 否 | 条件试用，AI 标签非人工 gold；[D3](../verification/D3_BCD_FINAL_ACCEPTANCE_20261114.md)。 |
| U2，11-14 | 普通全量 + 前端 + 浏览器；U2 工作区，后提交 `f1fb02b` | 后端 1848/1829/0/0/19；前端 346/346/0/0/0 | 浏览器场次真实；其他测试有替身 | 是 / 否 | 指定已保存反馈恢复；[U2](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U2-恢复修复与运行安全验收.md)。 |
| S1，11-14 | 定向、全量及独立实例安全；后提交 `4bed0e2` | 后端 1850/1831/0/0/19；前端 346/346/0/0/0 | 安全测试替身 | 是 / 否 | 首轮 8 个时区相关失败，修正隔离环境后通过；[S1](../verification/S1_INTERVIEW_VISIBILITY_SECURITY_20261114.md)。 |
| Q1，11-14 | 真实评分/技术风险卡；当时工作区 | 3 场合成报告 READY；风险卡 7/12 存在至少一项问题 | 真实付费 | 是 / 否 | 不能把风险选样外推为错误率；[Q1](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-Q1-真实评分质量与多候选人测试诊断.md)。 |
| Q2，11-14 | 已揭盲旧样本，v5 单臂对照保存的历史 v4 输出；v5 已撤回 | 18 次 HTTP 200；17 合法、1 JSON 失败 | 真实付费 | 复用 Q1 已保存证据，实时读库未证实 / 否 | 不等于 17 次评分正确；[Q2](../evaluation/q2-20261114/DIAGNOSTIC.md)。Q2-R2 后续无有效模型结果。 |
| M1-B，11-14 | 普通 Maven；`84e3489` + B diff | 1923/1900/0/0/23 | 替身 | 是 / 默认禁用 | 当时后端工作区；[M1-B](2026-11-14-M1B-ROCKETMQ-ASYNC-REPORT-VERIFICATION.md)。 |
| M1-B，11-14 | 真实 MQ/MySQL 专项；同上 | 2/2/0/0/0 | 替身 | 是 / 是 | 重发与迟到 SENT；另有故障/DLQ 探针，详见同上，不等于全部故障。 |
| M1-C，11-15 | 最终版普通 Maven + 真实进程故障 | 1938/1904/0/0/34；故障 5/5 场景达到各自断言 | 替身；另两次真实报告为较早 JAR | 是 / 是（故障专项） | 故障不是 5 项 JUnit；[故障记录](2026-11-15-M1C-FAILURE-INJECTION.md)。 |
| M1-C-R2，11-15 | 定向 + 全量 + 专项；`84e3489` + B/C/R2 diff | 定向 131/131/0/0/0；全量 1938/1904/0/0/34；MQ 2/2/0/0/0 | 替身 | 是 / 专项是 | 配置调优和有限批次；[R2 报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-M1-C-R2-RocketMQ多瓶颈归因与最小调优.md)。 |
| M1-D，11-15 | 全量 + 真实报告策略对照；`84e3489` + B/C/R2/D diff | 后端 1938/1904/0/0/34；前端 341/341/0/0/0；真实 8 次中 7 READY、1 FAILED | 全量替身；对照真实付费 | 是 / 专项是 | 首次全量曾有 125 个夹具污染失败；[M1-D](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-M1-D-报告生成优化与真实效果对照.md)。 |
| M1-E，11-15 | 普通 Maven；`84e3489` + B～E 工作区 | **1947/1913/0/0/34** | 替身 | 是 / 默认禁用 | 本次复算 151 个 suite 和原日志；[suite 汇总](m1e-20261115/full-test-suites.json)、[原日志](/Users/kun/Developer/projects/ai-interview-system/backups/M1E_20261115/full-backend-final.log)。首轮曾 1 个诊断权限错误，修复隔离账号权限后通过。 |
| M1-E，11-15 | 真实 MQ 指定两项；同测量工作区 | **2/2/0/0/0** | 生成器替身 | 是 / 是 | 补发/重复与迟到 SENT；[原日志](/Users/kun/Developer/projects/ai-interview-system/backups/M1E_20261115/real-mq.log)。 |
| M1-E，11-15 | 测量构建前端 Vitest | **346/346/0/0/0** | Mock/替身 | 否 / 否 | 这是较早构建；不是最终收口最新数。[M1-E](m1e-20261115/DECISION_AND_VERIFICATION.md)。 |
| M1-E，11-15 | 六对真实报告 + 三对浏览器；测量构建 | 12 次 HTTP 200：11 READY、1 FAILED；浏览器 3 对 | 真实付费 | 是 / 是 | 不等于 12 次内容质量通过；[原计时 CSV](m1e-20261115/EXPERIMENTS.csv)、[调用计数](m1e-20261115/cost-and-counts.json)。 |
| M1-E 收口，11-15 | 最新前端 Vitest；在测量构建后修改轮询合并 | 报告页 59/59/0/0/0；全量 **349/349/0/0/0** | Mock | 否 / 否 | 原日志本次复核；[验证清单](m1e-20261115/closeout/VERIFICATION.json)、[前端日志](/Users/kun/Developer/projects/ai-interview-system/backups/M1E_20261115/closeout/frontend-full.log)。原有性能样本未在此构建重测。 |
| M1-E 收口，11-15 | 后端生成器 Mock HTTP 回放；最新测试资源 | **14/14/0/0/0** | 原响应离线重放，非新付费调用 | 否 / 否 | 复现超限拒绝；[原日志](/Users/kun/Developer/projects/ai-interview-system/backups/M1E_20261115/closeout/backend-replay.log)。 |

**VERIFIED_TEST。** M1-E 前端 typecheck、lint、build 的历史日志记录通过；收口前后有独立构建身份。收口前 5 项唤醒单测及 4 项真实 MySQL 测试覆盖提示合并、拒绝、提交/回滚、竞争租约；收口复用原测试，没有再次执行完整 kill 矩阵。`-DskipTests package`、静态检查和页面展示不得计成 JUnit 或真实服务测试。历史 M1-E 计费估算不是供应商账单；本次新增调用为 0。

## 6. 当前遗留问题，按优先级

1. **P0 / NO_GO：评分质量。** Q1 真实评分存在事实和覆盖错误；v5 已撤回，Q2-R2 新候选没有有效对照，独立人工审定的留出集不足。优先建立答案事实、名称/含义、复合点与追问的评判合同及人工复核，不以 HTTP 200、分数变化或结构合法替代正确性。
2. **P0 / NO_GO：报告内容质量。** 综合报告可继承错误、在没有文字依据时推测具体缺点、遗漏或扩大事实范围。需要区分上游 FINAL 错误与报告汇总新引入的问题，使用独立样本和真人逐项审阅；不得改写历史评价伪装修复。
3. **P1 / VERIFIED_TEST：M1-E 结构违例仍可再发生。** 1/12 真实输出超数量上限；当前验证器正确拒绝，原因和总体发生率未知。后续应单独评估稳定性、用户失败提示及有界费用；不放宽约束或静默裁剪来制造成功。
4. **P1 / UNVERIFIED：完整报告真实生成时间未证明稳定下降。** P1 减少局部派发等待，P3 提前展示已有内容；小样本真实模型、冷/热连接及输出长度波动都限制推断。准确采样前不能宣布模型或完整页面普遍提速。
5. **P1 / VERIFIED_SOURCE + UNVERIFIED：消费者崩溃与恢复契约。** 已领取后死亡，原任务超时失败、需显式新申请；上游可能被再次调用。应由产品明确是否接受人工重试，不能把“等待扫描器”误写为自动 READY；自动恢复若被要求，应先明确预算、次数、围栏和数据库变化的必要性。
6. **P1 / NO_GO：生产级 MQ 故障与可观测性。** 已有真实进程/Broker 的有限场景；长期积压、JDBC 提交未知、多节点重平衡、磁盘永久丢失、MySQL 实停、监控告警及恢复演练未完整验证。依赖旧 POM/tools.jar 告警和传递依赖也需要单独审查，不能仅凭编译通过认定供应链安全。
7. **P1 / UNVERIFIED：发布、迁移和版本身份。** 当前 M1 改动未提交；正式目标库没有本次迁移许可和已应用证据，常驻实例的目标版本、停写排空、历史 GENERATING 对账和回退未验收。旧同步与新异步写入者不能无计划并行。DDL 按精确目标审查并获授权后执行，保留数据和消息。
8. **P2 / VERIFIED_TEST + UNVERIFIED：RAG 和用户体验边界。** 历史直接证据相关率未达标、标注主要由 AI 完成；当前实时数据与长期 Milvus 稳定性未知。1 秒轮询的后台标签页降频未实现；旧请求离页未做传输层 abort。不要将这与已修复的跨重启反馈恢复、未来题可见性、owner 锁/一级缓存或轮询重复 GET 混同。
9. **P2 / UNVERIFIED：开发者掌握。** 实现、测试或工程代理写出的案例不等于用户已独立掌握；`SOURCE_LEARNING_BACKLOG.md` 中 M1-E 学习项仍待本人讲解、复现或独立修改。

## 7. 后续建议与范围控制

### 必须完成，作为发布/切换前条件

1. 首先以当前 `git status` 和源码确认本文件是否已过期；保留所有已有变动，对 M1-B/C/R2/D/E、Q1/Q2、文档、测试、工具逐文件检查差异与敏感信息，制定可审查的拆分和依赖顺序。**本交接不授权 `git add`、commit 或 push**；正式提交/推送按用户后续明确要求执行。
2. 对 M1-E 最终 P1/P3 做安全和体验复核，重点确认授权详情、失败后可见性、同域请求合并、关闭/拒绝提示和扫描兜底；复用有效历史证据，只在有新改动或缺口时补充针对性测试。
3. 在明确目标和批准后准备隔离环境真实验收：检查任务表是否存在及版本、完整 FINAL、旧 GENERATING、旧同步写入者排空、任务/消息留存和回退；分别验证事务一致性、指定 MQ 故障和真实模型结构，不触碰其他项目或历史数据。
4. 将评分与报告内容作为两个独立 NO-GO 门禁：制定人工裁决、独立留出、错误分类、样本和可重复比较；真实模型仅按明确目标、现有授权及费用护栏调用，记录实际结果和未知计费。

### 推荐完成

- 根据未来需要补有限真实并发和运行准备：任务排队/期限、同用户弱项热行、连接池、重平衡、Broker 长停、监控和恢复；先定义负载与观测点，避免重做无决策价值的矩阵。
- 让 README/运行手册在实际提交整理时描述新旧报告路径、默认关闭、迁移/回滚和已验收边界；不要在尚未批准的目标直接开启异步或把旧文档整段改写为上线声明。
- 按 [源码学习清单](SOURCE_LEARNING_BACKLOG.md) 从身份校验与快照、答案幂等与 FINAL、Outbox 状态和 CAS、事务提交、前端轮询逐步学习；每项以用户自己的解释或复现记录掌握情况。

### 可选增强，先证明需求

自动有界恢复、预先触发报告、SSE、自适应轮询、替换 MQ 或两阶段基础报告均未成为当前默认方案；只有在产品需要、明确成本和状态语义后另行设计。数据库结构变更必须按 AGENTS 要求说明具体问题、Java 无法合理解决的原因和迁移影响，并取得明确批准。

## 8. 缺失证据与交接边界

- **UNVERIFIED：**本次无上一段长期 ChatGPT 对话的完整逐轮原文，只能使用用户指令和本地归档，不能推断用户个人贡献或未归档决定。
- **UNVERIFIED：**较早 E2E 首次失败的原始模型 JSON 据现存 [工程历史](PROJECT_ENGINEERING_HISTORY_20261111_20261115.md#h06) 未保存；不能从失败代码反推具体输出。Q2-R2 有一次请求结果/usage 未知，也不能推断供应商收到、计费或未计费。
- **UNVERIFIED：**真实人工 gold、真实生产负载/商业用户、当前服务二进制身份、当前所有数据库/MQ 状态、目标部署/回滚及供应商实际账单均无本次证据。早期临时 `/tmp` 日志可能随系统清理；分享之前按索引逐项复查。
- **VERIFIED_TEST：**本次核对的 M1-E 原始证据索引 770 个文件均存在且 SHA-256 匹配，关键五项收口源码/测试资源哈希匹配，后端 1947/34、前端 349、MQ 2 的原日志可读；这些校验不是新测试或对全部历史报告的完整审计。
- **隐私：**新对话优先携带本文件、精简摘要及证据索引。原始答案、完整 Prompt、模型输出、数据库备份、认证信息、私有日志、`.env`、`application-local.yaml` 与 `backups/` 不应直接打包或上传。历史报告可能含合成或真实用户线索、路径、标识和技术细节，分享前逐项脱敏；见索引。
