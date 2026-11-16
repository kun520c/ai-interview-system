# 源码学习待办清单

> **历史证据范围：**本文含不同时点的历史记录。部分原始数据、本地报告和实验工具仅保留在私有或未提交工作区，公开检出中的相关链接可能不可用；Git 和部署状态以最新记录及实际仓库为准。

建立：2026-11-13；最近更新：2026-11-14（Asia/Shanghai）。C7 已将 C6 验收的同一源码依次提交，累计源码提交为 `71aeb22f7da906660763e9efb1100a20df7705c9`；B4-D／B4-E／B5-P2-A／B5-P2-B 的完整 SHA 和父子关系见 [工程优化台账](IMPROVEMENT_LEDGER.md#11-git-实际状态)。本清单与台账的C7版本随其后的独立文档提交归档，未推送；后续更新尚未提交。

建档及 C6 时点的基线是 `01ac774caff491b978dd2268e8b427355a64b97a` 加四批未提交修改。下文各批“无 SHA／未提交”保留为当时事实，当前提交定位以以上 C7 更新为准。源码内容与历史回归证据不变；新执行的独立编译／类型检查见 [C7 记录](IMPROVEMENT_LEDGER.md#c7)，不等于重跑功能测试或开发者掌握。

2026-11-13 D0 更新：当前 HEAD 为 `0c6f92afe331954f7cea37d33db688fe781cbf2c`（C7 文档提交）；远端 main 实时核验为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，本地领先8。D0仅新增离线审计/方案及本轮学习条目，当前未暂存/提交/推送。MySQL/Milvus 实时核验 BLOCKED；随机选题、42题新增正文、30篇新增知识正文及多用户测试均未实施。旧段落继续保留其原时点事实。

## 1. 学习状态与使用方法

2026-11-14 E3-E01更新：累计 **79条**（不含模板占位行），全部保持待学习、掌握未确认。真实服务入库证据不代替本人讲解、复现或独立修改证据。

D0 时点本清单共 **68 条**：历史批次技术点25条，C0/C3共通方法3条，B4-D源码学习7条，B4-E源码学习7条，B5-0审查/验证学习6条，B5-P2-A密码边界学习3条，B5-P2-B日志边界学习3条，C6累计收口学习3条，D0数据/测试设计学习5条，旧并发/观测未编号候选2条，B6/B7/B8/B9范围占位4条。最后4条不是已经定义的技术点，范围明确后再拆分。B5原1条占位已按本轮明确范围拆为6条；B5-P2-A/B均已完成工程修复，C6完成证据复核，D0仅审计/设计，仍没有开发者掌握证据。

**工程已实现、AI 已生成代码、测试通过、已有源码指南，均不代表开发者掌握。** 本次没有收到开发者逐项学习反馈，因此所有条目初始化为“待学习，掌握情况未确认”；这也不表示已经证明开发者不会。

建议状态：待学习（未确认）→ 正在阅读 → 能独立讲解（附证据）→ 能复现／定位（附证据）→ 能独立修改并验证（附证据）。代码变动后可回到“待复核”。不要由代理根据测试成功自动升级状态；应记录开发者本人讲解、练习、反例分析或独立修改的日期与材料。一次口头确认只能支持其实际覆盖范围。

使用方式：选择一个条目 → 补齐前置知识 → 从具体方法追调用方／事务／SQL／测试 → 回答核心问题 → 保存本人产出的证据 → 更新状态和仍不确定的点。所有练习默认仅阅读／本地推演；付费调用、数据修改、提交／发布仍需遵循项目授权边界。

链接指向当前文件，方法名是主要定位依据；行号会漂移。回看某批原始实现时使用台账 SHA 的 `git show`；被删除的 Jev 代码只能读 J1 父版本。依赖源码以报告核实版本为准，不把当前网上最新源码套到历史依赖。

## 2. 技术点清单

### J1

<a id="l-j1-01"></a>

#### L-J1-01 — 单一 Provider 装配与退役边界

- 前置知识：Spring Bean、@Configuration、条件装配、接口与实现。
- 源码入口：[JudgeConfiguration](../../src/main/java/com/kun/aiinterview/interview/evaluation/judge/JudgeConfiguration.java) 的 `judgeProvider`；[DeepSeekJudgeProvider](../../src/main/java/com/kun/aiinterview/interview/evaluation/judge/DeepSeekJudgeProvider.java) 的 `evaluate`。
- 核心问题：为什么保留 JudgeProvider 接口却不再保留 selector？禁用 DeepSeek 或注入旧 selector 时会怎样？
- 验证理解的练习：沿 JudgeConfigurationTest 解释唯一 Bean、禁用路径和旧配置无效；对照 J1 父提交说明删除范围。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-j1-02"></a>

#### L-J1-02 — 历史结果兼容与业务恢复

- 前置知识：数据库业务列、JSON、幂等、状态机。
- 源码入口：[Orchestration](../../src/main/java/com/kun/aiinterview/interview/orchestration/EvaluationOrchestrationService.java) 的 `evaluate/prepareEvaluation`；[InterviewService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewService.java) 的 `submitAnswer/handleExistingAnswer/buildSubmitAnswerResponse`。
- 核心问题：删除供应商 fallback 为什么不删除 FAILED/stale 恢复？为什么历史 raw_result 不必符合当前 DeepSeek DTO？
- 验证理解的练习：跟踪历史 FINAL/FOLLOW_UP 重放，用持久业务列说明何处零模型调用、何处新请求可能再次收费。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-j1-03"></a>

#### L-J1-03 — 不可信模型建议到 Java 决策

- 前置知识：Bean Validation、JSON 数值类型、枚举、不变量。
- 源码入口：[LlmEvaluationValidator](../../src/main/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidator.java) 的 `validate`；[EvaluationScoreCalculator](../../src/main/java/com/kun/aiinterview/interview/evaluation/score/EvaluationScoreCalculator.java)；[FollowUpPolicy](../../src/main/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpPolicy.java) 的 `decide`。
- 核心问题：模型能提供什么，不能决定什么？MAIN INITIAL 与追问 FINAL 如何对应？总分为何不能信任模型自报？
- 验证理解的练习：自行构造分数字符串、缺维度、越界和二次追问反例，指出应由哪层拒绝。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-j1-04"></a>

#### L-J1-04 — 删除实现时迁移测试与工具

- 前置知识：Git diff、测试分类、Maven 编译/打包、条件测试。
- 源码入口：[DeepSeek-only gate](../../tools/check_deepseek_only.py)；[JudgeConfigurationTest](../../src/test/java/com/kun/aiinterview/interview/evaluation/judge/JudgeConfigurationTest.java)；[FullInterviewBusinessOfflineTest](../../src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java)。
- 核心问题：为何可以删除 Jev 专属测试但要保留公共校验？为什么工具 E2E 名称、编译成功和 RETIRED 退出码不能直接说明 E2E 通过？
- 验证理解的练习：核对 1790−246−27+9=1526 的历史数量来源，并列出仍跳过、只编译和真实执行的证据。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

### B1

<a id="l-b1-01"></a>

#### L-B1-01 — requestId、完整载荷与持久响应

- 前置知识：JWT 当前用户、所有权校验、唯一约束、DTO。
- 源码入口：[InterviewService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewService.java) 的 `submitAnswer/handleExistingAnswer/buildSubmitAnswerResponse`。
- 核心问题：为什么 requestId 相同还要校验题目和答案精确相等？内部 return null 为什么不等于 HTTP 空成功？
- 验证理解的练习：沿 Controller 到响应重建解释 401/404/409、缺评价 500 和最新 Session 的来源。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b1-02"></a>

#### L-B1-02 — 旧快照与完成重放竞态

- 前置知识：线程交错、数据库提交可见性、读写分离步骤。
- 源码入口：[Workflow](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java) 的 `evaluateAnswer`。
- 核心问题：首次 EVALUATED 判断为何不够？旧 SUBMITTED 与新 Session 指针怎样组合出错误？为何不能错题一律返回成功？
- 验证理解的练习：画出或口述两暂停点×五条转移；标出 requireReloadedAnswer 的唯一新增读取和保留错误分支。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b1-03"></a>

#### L-B1-03 — 唯一约束、attempt fencing 与 Session CAS

- 前置知识：条件 UPDATE、行锁、事务原子性、版本号。
- 源码入口：[WorkflowTransactionService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java) 的 `persistOwnedEvaluation`；[Answer Mapper SQL](../../src/main/resources/mapper/InterviewAnswerMapper.xml)；[Session Mapper SQL](../../src/main/resources/mapper/InterviewSessionMapper.xml)。
- 核心问题：唯一记录、执行资格和会话推进各由什么约束？为什么最终结果唯一不代表供应商只执行一次？
- 验证理解的练习：说明 moveToFollowUp/advanceToNextMain/finishSession 的锁、attempt、trace/evaluation、状态与 CAS 提交顺序。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b1-04"></a>

#### L-B1-04 — 确定性真实并发测试

- 前置知识：CountDownLatch、Future、线程隔离、Spring 事务代理。
- 源码入口：[InterviewConcurrentReplayIntegrationTest](../../src/test/java/com/kun/aiinterview/interview/service/InterviewConcurrentReplayIntegrationTest.java) 的 `pauseFirstRequest/assertCompletedState`。
- 核心问题：怎样证明 A 的提交可见后 B 才完成，A 最后才继续？外围 @Transactional 或 sleep 会损失哪种证据？
- 验证理解的练习：依据测试描述数据库整行、模型调用计数、HTTP 和版本断言，区分真实 MySQL 与外部替身。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

### B2

<a id="l-b2-01"></a>

#### L-B2-01 — 领取后 try/catch 的责任边界

- 前置知识：异常传播、CAS、OWNED/REPLAY 状态。
- 源码入口：[Workflow](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java) 的 `evaluateAnswer`。
- 核心问题：SELECT 为何也需要领取后的失败保护？try 为什么不能包到 claim 之前？
- 验证理解的练习：逐行标出无资格、REPLAY、OWNED，推演下一题查询抛异常时修复前后的持久状态。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b2-02"></a>

#### L-B2-02 — 独立短事务与 REQUIRED

- 前置知识：Spring AOP 代理、事务传播、自动提交与回滚。
- 源码入口：[WorkflowTransactionService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java) 的 `tryClaimEvaluation/tryRetryEvaluation/tryReclaimStaleEvaluating/markEvaluationFailed`；[PostClaim 集成测试](../../src/test/java/com/kun/aiinterview/interview/service/InterviewPostClaimFailureIntegrationTest.java)。
- 核心问题：无外层事务时 REQUIRED 为什么已构成独立短事务？后面的查询异常为什么不能回滚之前的 claim？
- 验证理解的练习：用独立连接观察点解释 EVALUATING→FAILED；查源码核实实际方法名与 Mapper 的对应关系，不将其称作 REQUIRES_NEW。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b2-03"></a>

#### L-B2-03 — 原始异常、cause、suppressed 与旧任务隔离

- 前置知识：Throwable、SQLException、affected rows、stale 接管。
- 源码入口：[Workflow](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java) 的 `markEvaluationFailed`；[markFailed SQL](../../src/main/resources/mapper/InterviewAnswerMapper.xml)。
- 核心问题：失败回写又失败时保留哪一个异常？旧 attempt 更新 0 行为何是安全拒绝？数据库宕机是否仍能即时 FAILED？
- 验证理解的练习：解释原异常对象 identity、SQLException cause 和 suppressed 断言；列出崩溃/提交不确定仍需 stale 的边界。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

### B3

<a id="l-b3-01"></a>

#### L-B3-01 — FINAL 候选 SQL 与重复保真

- 前置知识：SQL JOIN、窗口函数、FINAL/INITIAL 业务含义。
- 源码入口：[AnswerEvaluationMapper.xml](../../src/main/resources/mapper/AnswerEvaluationMapper.xml)；[Mapper 接口](../../src/main/java/com/kun/aiinterview/interview/mapper/AnswerEvaluationMapper.java) 的 `listFinalEffectiveEvaluationsBySessionId`。
- 核心问题：为何 FINAL 优先不是 FINAL-only？为什么去掉 ROW_NUMBER，而不是在 SQL 静默去重？
- 验证理解的练习：构造 INITIAL-only、重复 FINAL+缺失 MAIN 的反例，说明数量相等仍能错误。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b3-02"></a>

#### L-B3-02 — 跨表归属与完整计划校验

- 前置知识：集合相等、唯一性、MAIN/FOLLOW_UP、外键能力边界。
- 源码入口：[ReportService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java) 的 `generateReport/validateEvaluations/existingReportOrContinue`。
- 核心问题：外键和 UNIQUE(answer_id) 为何不能证明评价属于当前 MAIN 的最终答案？如何核对计划连续性和父 MAIN INITIAL？
- 验证理解的练习：跟踪 validateEvaluationAssociations，逐项解释跨 Session、错误父题、未完成答案、错误 FINAL decision 的拒绝。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b3-03"></a>

#### L-B3-03 — Java 聚合与 Report/Weakness 原子提交

- 前置知识：BigDecimal、HALF_UP、分组、MySQL upsert、事务回滚。
- 源码入口：[UserWeaknessService](../../src/main/java/com/kun/aiinterview/user/service/UserWeaknessService.java) 的 `applySessionEvaluations/validateEvaluation/calculateAverageScore`；[ReportTransactionService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java) 的 `completeReportGeneration`。
- 核心问题：为什么 INITIAL 不进分母，追问按父 MAIN 知识点？为什么同场同知识点只加一次 discovered_count？
- 验证理解的练习：手算 70/80 的 75.00 与弱项 25.00，再说明 Weakness SQL 或 READY CAS 失败时哪些写入回滚。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b3-04"></a>

#### L-B3-04 — READY 历史兼容与恢复旁路

- 前置知识：幂等、状态机、校验和数据迁移的区别。
- 源码入口：[ReportService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java) 的 `generateReport/validateEvaluations/existingReportOrContinue`；[FINAL 一致性集成测试](../../src/test/java/com/kun/aiinterview/interview/service/InterviewReportFinalConsistencyIntegrationTest.java)。
- 核心问题：为什么 READY 可以直接读，而非 READY 已有报告恢复仍须校验？为什么不自动修正历史错误分数？
- 验证理解的练习：说明已有报告行不能证明来源合法；对照历史 READY、FAILED/stale 恢复和真实 INSERT 失败三个测试边界。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

### B4-A

<a id="l-b4a-01"></a>

#### L-B4A-01 — 评分、反馈、追问和报告的实际调用链

- 前置知识：同步 Servlet、RAG 职责、事务与网络调用。
- 源码入口：[Orchestration](../../src/main/java/com/kun/aiinterview/interview/orchestration/EvaluationOrchestrationService.java) 的 `evaluate/prepareEvaluation`；[Report Generator](../../src/main/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGenerator.java) 的 `generate`；[ReportService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java) 的 `generateReport/validateEvaluations/existingReportOrContinue`。
- 核心问题：哪些动作共用一次模型响应？为什么 FINISH 与生成报告是两个请求？sendAsync 为什么仍可能占用请求线程？
- 验证理解的练习：从 Controller 追到 Client，解释无失败时 N+F+1 的应用调用数假设、成功/失败 trace 的提交边界。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b4a-02"></a>

#### L-B4A-02 — 单次超时、SDK 隐式重试与整链预算

- 前置知识：Duration、HTTP/RPC deadline、退避、单调时钟。
- 源码入口：[DeepSeekConfiguration](../../src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekConfiguration.java) 的 `deepSeekRestClient`；[EmbeddingConfiguration](../../src/main/java/com/kun/aiinterview/knowledge/embedding/EmbeddingConfiguration.java)；[MilvusConfiguration](../../src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusConfiguration.java)；[MilvusVectorStoreClient](../../src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusVectorStoreClient.java) 的 `search`。
- 核心问题：为什么 60s 不是评分上限？为什么 10s RPC deadline 不限制整个 search？75 次是哪一个版本、哪些错误的行为？
- 验证理解的练习：按 B4-A D02/B4-C 的实际 SDK 2.6.20 依赖证据追 RpcUtils.retry；区分 207.64s/957.64s 预算估算与实测，勿把 DEADLINE_EXCEEDED 说成必重试。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b4a-03"></a>

#### L-B4A-03 — GET 轮询与 POST 恢复的可达性

- 前置知识：HTTP 无副作用查询、前端状态、fresh/stale、后端 CAS。
- 源码入口：[useInterviewReport.ts](../../frontend/src/features/interview/composables/useInterviewReport.ts)；[ReportService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java) 的 `generateReport/validateEvaluations/existingReportOrContinue`。
- 核心问题：后端有 stale 接管为何页面仍可能卡在 GENERATING？为什么不每 3 秒自动 POST？
- 验证理解的练习：追踪页面生成条件、GET 轮询和 POST，描述 fresh 409、stale 单赢家、READY 复用的待测交互；不把设计当实现。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b4a-04"></a>

#### L-B4A-04 — 容量、可观测性与成本的不同问题

- 前置知识：线程/连接、信号量、日志与指标、token usage。
- 源码入口：[Workflow](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java) 的 `evaluateAnswer`；[ReportService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java) 的 `generateReport/validateEvaluations/existingReportOrContinue`；[DeepSeek Client](../../src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java) 的 `requestCompletion`；[B4-A 第 7/8 节](../verification/B4A_DEEPSEEK_RELIABILITY_AUDIT_20261112.md)。
- 核心问题：为什么 Tomcat 线程数、幂等和前端禁按钮都不等于任务并发上限？为什么数据库结果数不是模型调用数？
- 验证理解的练习：比较 tryAcquire、线程池、Redis 限流的适用条件；指出 elapsed、attempt、usage 的证据缺口，unknown 用量不能填 0。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

### B4-B

<a id="l-b4b-01"></a>

#### L-B4B-01 — RestClient 错误 handler 的拦截顺序

- 前置知识：RestClient retrieve/body、HTTP 状态、响应流生命周期。
- 源码入口：[DeepSeek Client](../../src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java) 的 `requestCompletion`；[DeepSeekConfiguration](../../src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekConfiguration.java) 的 `deepSeekRestClient`。
- 核心问题：默认 handler 如何把 body 带进 Throwable？请求级 onStatus 为什么先于默认处理？为何覆盖非 2xx 而不仅 4xx/5xx？
- 验证理解的练习：结合本地 Spring 6.2.19 sources.jar 的 StatusHandler/DefaultRestClient，解释“不读取 body/statusText”夹具；框架 close 与 read 断言不同。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b4b-02"></a>

#### L-B4B-02 — 异常对象的安全边界与白名单

- 前置知识：Throwable message/cause/suppressed/stack、Jackson、信任边界。
- 源码入口：[DeepSeek Client](../../src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java) 的 `requestCompletion`；[Report Generator](../../src/main/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGenerator.java) 的 `parseResponse`；[GlobalExceptionHandler](../../src/main/java/com/kun/aiinterview/common/exception/GlobalExceptionHandler.java) 的 `handleExternalServiceException`。
- 核心问题：固定外层 message 为何仍会泄漏？不挂危险 cause 如何保留本地栈？未知 finish_reason 和解析错误应保留哪些信息？
- 验证理解的练习：解释新安全异常与 B2 原异常保全的不同边界；不得把 DeepSeek 局部断链扩展为无条件删除所有模块 cause。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b4b-03"></a>

#### L-B4B-03 — 完整日志渲染的可验证脱敏

- 前置知识：Logback appender/proxy/layout、MockMvc、测试假输入。
- 源码入口：[DeepSeekLogSafetyTest](../../src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekLogSafetyTest.java)；[业务离线测试](../../src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java)。
- 核心问题：为什么只查 getMessage/formattedMessage 不够？为什么 AuthorizationFilter 类名不等于授权头泄漏？
- 验证理解的练习：逐项找到异常对象、完整栈、ThrowableProxy、PatternLayout、HTTP 502 的断言，并解释改前 6 项通过其实在证明泄漏存在。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

### B4-C

<a id="l-b4c-01"></a>

#### L-B4C-01 — 固定 reason 与有界嵌套分类

- 前置知识：异常继承、cause 遍历、switch、诊断优先级。
- 源码入口：[DeepSeek Client](../../src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java) 的 `safeHttpFailureReason/safeFailureCategory`；[FailurePolicyTest](../../src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekFailurePolicyTest.java)。
- 核心问题：外层 JsonParseException 为什么不能遮住内层 SocketTimeoutException？为什么普通 IOException 不按文字猜超时？
- 验证理解的练习：解释 CONNECT_TIMEOUT→TIMEOUT→CONNECTION_ERROR→RESPONSE_JSON→IO_ERROR 的顺序，并标注 10/11 层及环形链目前仅源码有界、缺专项实测。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b4c-02"></a>

#### L-B4C-02 — 单次应用尝试、显式重试与 Retry-After

- 前置知识：HTTP 错误语义、幂等、副作用、预算与退避。
- 源码入口：[FailurePolicyTest](../../src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekFailurePolicyTest.java)；[InterviewService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewService.java) 的 `submitAnswer/handleExistingAnswer/buildSubmitAnswerResponse`；[DeepSeek Client](../../src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java) 的 `requestCompletion`。
- 核心问题：可恢复分类为什么不是重试授权？为何不能整个 Workflow 加 Retry？为什么 1→2→2 不能证明供应商只收费一次？
- 验证理解的练习：解释 8 类 Retry-After 当前无等待/无重发；若未来重试，列出剩余预算、去重边界、所有权和取消必须验证的条件。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b4c-03"></a>

#### L-B4C-03 — 真实本机 HTTP 超时与取消边界

- 前置知识：Future、latch、InputStream、计时与线程等待。
- 源码入口：[DeepSeekConfiguration](../../src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekConfiguration.java) 的 `deepSeekRestClient`；[DeepSeekHttpTimeoutTest](../../src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekHttpTimeoutTest.java)。
- 核心问题：延迟 headers、停 body、部分 JSON、持续慢正文分别验证什么？为什么返回 IO_ERROR 也可能是 timer 关闭流造成？
- 验证理解的练习：对照 Spring TimeoutHandler 和 600ms 夹具，说明 loopback/Mock/WAN 的证据差异；调用方返回不等于供应商停止生成或计费。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

### C0/C3 共通

<a id="l-x-01"></a>

#### L-X-01 — 证据分层与精确计数

- 前置知识：Surefire XML、测试事务、退出码、测试替身。
- 源码入口：[C0 收口](../verification/J1_B3_PHASE_CLOSEOUT_20261111.md)；[C3 收口](../verification/C3_B4A_B4C_CLOSEOUT_20261112.md)；[完整离线业务类](../../src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java)。
- 核心问题：为什么 Mock HTTP+真实 MySQL 不能叫真实模型 E2E？为何 package、跳过和回归重叠不能计成新增通过？
- 验证理解的练习：从单次归档 XML 重算 T/F/E/S；解释环境错误、夹具错误、业务失败和复现成功四者的区别。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-x-02"></a>

#### L-X-02 — 共享文件拆分、提交与推送证据

- 前置知识：Git 工作区/索引/提交/远端引用、diff/hunk、祖先关系。
- 源码入口：[C0 提交规划](../verification/J1_B3_PHASE_CLOSEOUT_20261111.md)；[C3 第 6 节](../verification/C3_B4A_B4C_CLOSEOUT_20261112.md)；[当前台账](IMPROVEMENT_LEDGER.md)。
- 核心问题：为何 Workflow 的 B1/B2、Client 的 B4-B/B4-C 不能按最终整文件归给一个批次？本地 origin/main 为什么可能过期？
- 验证理解的练习：只读 git show 各 SHA，说明历史报告“未提交”与当前提交不矛盾；核对远端祖先关系，不执行 commit/push。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-x-03"></a>

#### L-X-03 — 安全结论的范围与反例

- 前置知识：日志源头、调用栈、合成 sentinel、审计范围。
- 源码入口：[业务内部异常测试](../../src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java) 的 `internalExceptionsKeep500WithoutModelRetry`；[C3 第 5 节](../verification/C3_B4A_B4C_CLOSEOUT_20261112.md)。
- 核心问题：PRIVATE_MODEL_OUTPUT 三次命中为何不等于 DeepSeek 安全修复失效？也为什么不能因此说所有日志都安全？
- 验证理解的练习：追溯三种异常绕过 Client 的路径，写出“已测边界、例外、未验证范围”各一句；不将合成标记误报为真实泄密。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

### B4-D — Milvus SDK 重试与超时治理

关联 [工程记录](IMPROVEMENT_LEDGER.md#b4-d) 与 [B4-D验收](../verification/B4D_MILVUS_RETRY_DEADLINE_20261113.md)。源码为HEAD加本轮未提交修改；工程验证已完成，开发者学习证据未提供。准确依赖入口及下载地址见验收第2节，勿用最新SDK源码替换2.6.20事实。

<a id="l-b4d-01"></a>

#### L-B4D-01 — SDK重试策略与异常传播

- 前置知识：Callable、异常传播、gRPC status、退避。
- 源码入口、调用者/测试：准确2.6.20源码 `io/milvus/v2/client/RetryConfig`、`utils/RpcUtils.retry`；[MilvusSdkRetryBehaviorTest](../../src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusSdkRetryBehaviorTest.java)。
- 核心问题/面试追问：75是总尝试还是附加次数？哪些gRPC状态/服务端RateLimit会重试？为什么CANCELLED也可能继续？catch ignored如何把异常变成null？
- 独立练习/讲解验收目标：手动追踪UNAVAILABLE两次失败和第三次成功；解释为何只配置总预算/3次不能解决2.6.20缺陷。
- 掌握状态：**待学习**。开发者掌握证据：未提供；不能由Codex实现/测试成功推定掌握。
- 尚待本人验证：上述调用链、反例与适用边界；真实Milvus/模型行为本轮未验证。
- 最近复核：2026-11-13，Codex仅核对源码和工程证据；没有开展本人学习验收。

<a id="l-b4d-02"></a>

#### L-B4D-02 — gRPC deadline与构造器数据库检查

- 前置知识：绝对截止时间、Context、try/finally、资源释放。
- 源码入口、调用者/测试：SDK MilvusClientV2.connect/getRpcStub、ClientUtils.checkDatabaseExist；[MilvusConfiguration.milvusClient](../../src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusConfiguration.java)；MilvusGrpcDeadlineTest的rawSdkDatabaseProbe/factoryBoundsStalledConstructorDatabaseProbe。
- 核心问题/面试追问：为什么Connect有超时而ListDatabases仍可能无界等待？Context如何传入底层RPC，为什么初始化完成必须detach/cancel？
- 独立练习/讲解验收目标：画出两个初始化RPC及共享预算；对照未修SDK服务端无deadline与修复后cancellation证据，解释1ns转0ms的风险。
- 掌握状态：**待学习**。开发者掌握证据：未提供；不能由Codex实现/测试成功推定掌握。
- 尚待本人验证：上述调用链、反例与适用边界；真实Milvus/模型行为本轮未验证。
- 最近复核：2026-11-13，Codex仅核对源码和工程证据；没有开展本人学习验收。

<a id="l-b4d-03"></a>

#### L-B4D-03 — 单次RPC、一次SDK尝试与整条业务链超时

- 前置知识：Duration、单调时钟、绝对deadline、串行调用。
- 源码入口、调用者/测试：SDK VectorService.insert、CollectionService.createCollectionWithSchema；[MilvusGrpcDeadlineTest.actualGrpcRetrySucceedsButExceedsOneAttemptDeadline](../../src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusGrpcDeadlineTest.java)；[MilvusProperties](../../src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusProperties.java)。
- 核心问题/面试追问：同次内部RPC为什么共用剩余deadline，而外层retry重置deadline？maxRetryTimes=1为何不等于只有一次网络请求？整链还包含哪些等待？
- 独立练习/讲解验收目标：根据三次200ms响应和500ms单次预算解释628ms观测；指出CPU、DB、调度与SchemaMismatch使哪些严格上界说法不成立。
- 掌握状态：**待学习**。开发者掌握证据：未提供；不能由Codex实现/测试成功推定掌握。
- 尚待本人验证：上述调用链、反例与适用边界；真实Milvus/模型行为本轮未验证。
- 最近复核：2026-11-13，Codex仅核对源码和工程证据；没有开展本人学习验收。

<a id="l-b4d-04"></a>

#### L-B4D-04 — SDK重试与业务FAILED重试的身份边界

- 前置知识：状态机、requestId、attemptId、CAS、短事务。
- 源码入口、调用者/测试：[KnowledgeDocumentProcessingService.occupyProcessing/processDocument](../../src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeDocumentProcessingService.java)；[KnowledgeDocumentMapper.xml](../../src/main/resources/mapper/KnowledgeDocumentMapper.xml) 的claimFailedProcessing/reclaimStaleProcessing/markFailed；KnowledgeAttemptFencingIntegrationTest。
- 核心问题/面试追问：一次SDK调用内部重复与新的FAILED请求有何区别？谁创建新attempt？关闭SDK重试为什么不删除业务恢复？旧attempt能否改新任务状态？
- 独立练习/讲解验收目标：按首次失败→精确补偿→FAILED→同document新请求→新attempt READY口述SQL及外部调用次数；对照面试requestId/Session CAS说明共性和差异。
- 掌握状态：**待学习**。开发者掌握证据：未提供；不能由Codex实现/测试成功推定掌握。
- 尚待本人验证：上述调用链、反例与适用边界；真实Milvus/模型行为本轮未验证。
- 最近复核：2026-11-13，Codex仅核对源码和工程证据；没有开展本人学习验收。

<a id="l-b4d-05"></a>

#### L-B4D-05 — 线程中断与底层请求取消

- 前置知识：InterruptedException、中断位、gRPC Context、线程生命周期。
- 源码入口、调用者/测试：SDK RpcUtils.retry的sleep catch；MilvusGrpcDeadlineTest.interruptionCancelsActiveRpcWithoutSdkRetry与configuredDeadlineCancelsTransportButCannotUndoLateServerWrite；MilvusConfiguration finally。
- 核心问题/面试追问：Future.get(timeout)只结束什么等待？旧SDK为何吞中断？服务端观察Context取消能否证明停止持久化？HTTP断开和stale接管是否会自动中断？
- 独立练习/讲解验收目标：复述新旧两条中断路径，解释测试中的future/join只是夹具保护；列出服务器不配合取消的反例。
- 掌握状态：**待学习**。开发者掌握证据：未提供；不能由Codex实现/测试成功推定掌握。
- 尚待本人验证：上述调用链、反例与适用边界；真实Milvus/模型行为本轮未验证。
- 最近复核：2026-11-13，Codex仅核对源码和工程证据；没有开展本人学习验收。

<a id="l-b4d-06"></a>

#### L-B4D-06 — 外部向量写入结果不确定性

- 前置知识：网络确认、幂等ID、失败与未知结果、最终一致性。
- 源码入口、调用者/测试：[MilvusVectorStoreClient.insert/deleteByVectorIds](../../src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusVectorStoreClient.java)；MilvusGrpcDeadlineTest.lostInsertAcknowledgementStillRequiresTargetedIdempotentCompensation；SDK VectorService.insert SchemaMismatch路径。
- 核心问题/面试追问：为什么timeout或UNAVAILABLE不能推出未写入？为什么deleteCnt=0可接受而insertCnt不符必须拒绝？一次尝试为什么仍不能承诺exactly-once？
- 独立练习/讲解验收目标：对照“写入后丢确认”和“补偿后迟到写入”分别列出最终向量状态，找出不能靠一次删除证明的结论。
- 掌握状态：**待学习**。开发者掌握证据：未提供；不能由Codex实现/测试成功推定掌握。
- 尚待本人验证：上述调用链、反例与适用边界；真实Milvus/模型行为本轮未验证。
- 最近复核：2026-11-13，Codex仅核对源码和工程证据；没有开展本人学习验收。

<a id="l-b4d-07"></a>

#### L-B4D-07 — MySQL/Milvus定向补偿与资格校验

- 前置知识：事务提交结果不确定、行锁、CAS、source of truth。
- 源码入口、调用者/测试：KnowledgeDocumentProcessingService.processDocument/compensateMilvus；[KnowledgeDocumentProcessingTransactionService.persistChunksAndMarkReady](../../src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeDocumentProcessingTransactionService.java)；[KnowledgeChunkMapper.xml](../../src/main/resources/mapper/KnowledgeChunkMapper.xml)；[KnowledgeAttemptFencingIntegrationTest](../../src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeAttemptFencingIntegrationTest.java)。
- 核心问题/面试追问：为什么milvusWriteMayExist必须在insert前置true？MySQL提交确认丢失时为何先锁行确认？物理孤儿为何不能成为有效证据？为什么不能按documentId删整批新任务？
- 独立练习/讲解验收目标：独立解释late-write、lost-commit-ack和旧任务晚失败三例；说明既有维护CLI为何必须停写且未自动继承本次Spring策略。
- 掌握状态：**待学习**。开发者掌握证据：未提供；不能由Codex实现/测试成功推定掌握。
- 尚待本人验证：上述调用链、反例与适用边界；真实Milvus/模型行为本轮未验证。
- 最近复核：2026-11-13，Codex仅核对源码和工程证据；没有开展本人学习验收。

### B4-E — 报告 GENERATING 人工恢复入口

关联 [工程台账](IMPROVEMENT_LEDGER.md#b4-e) 与 [B4-E验收及13分钟路径](../verification/B4E_REPORT_STALE_RECOVERY_20261113.md)。工程验证已完成，以下7条全部 **待学习**；没有开发者本人讲解、复现或独立修改证据，不能由实现或测试成功自动认定掌握。

<a id="l-b4e-01"></a>

#### L-B4E-01 — Vue Composition API 的异步状态管理

- 前置知识：ref、computed、async/await、Promise、Vue渲染更新。
- 源码入口/调用者：[useInterviewReport](../../frontend/src/features/interview/composables/useInterviewReport.ts) 的 `generate/recoverGenerating/submitReport`、`posting/generateBusy/canRecoverGenerating`；[View](../../frontend/src/features/interview/views/InterviewReportView.vue) 的恢复按钮。
- 核心问题：按钮disabled与函数内posting守卫为何都需要？同一渲染周期两次点击为何不能只靠界面禁用？为什么提交来源类型必须包含GENERATING？
- 验证理解的练习：独立写出点击、await前后、确认GET及finally的状态顺序；用deferred说明请求pending时第二次调用被谁拒绝。不要把GENERATING_POST当作数据库状态。
- 掌握状态：**待学习**。开发者掌握证据：未提供；待本人验证：能讲解并修改一个分支且通过专项测试。
- 最近复核：2026-11-13，仅源码/工程证据核对，未开展本人学习验收。

<a id="l-b4e-02"></a>

#### L-B4E-02 — 轮询与服务端状态同步

- 前置知识：setInterval/clearInterval、生命周期、响应式视图、单次刷新去重。
- 源码入口/调用者：`startPolling/stopPolling/refreshReport/adoptReport`、`onUnmounted`；[专项测试](../../frontend/tests/InterviewReportView.spec.ts) 的 long-running、stops polling、manual recovery。
- 核心问题：为何GENERATING继续GET，而READY/FAILED停止？为何恢复POST期间暂停轮询？页面等待11分钟为何不能推断任务已过期？
- 验证理解的练习：手推首次GET+220个周期为221次GET、0次POST；列出卸载、切会话、完成、查询失败时的计时器状态，解释轮询本身不等于后台恢复。
- 掌握状态：**待学习**。开发者掌握证据：未提供；待本人验证：能复现轮询次数与终止边界。
- 最近复核：2026-11-13，仅源码/工程证据核对，未开展本人学习验收。

<a id="l-b4e-03"></a>

#### L-B4E-03 — GET 查询与 POST 状态变更的区别

- 前置知识：HTTP方法、接口副作用、鉴权、幂等重放、409。
- 源码入口/调用者：[interviewApi](../../frontend/src/features/interview/api/interviewApi.ts) 的fetch/generate；[InterviewController](../../src/main/java/com/kun/aiinterview/interview/controller/InterviewController.java) 的getReport/generateReport；[ReportService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java) 的getReport/generateReport。
- 核心问题：同一路径两个HTTP方法为何职责不同？后端已有stale能力为何前端仍需新按钮？READY的POST为何复用结果？为什么不在轮询中自动POST？
- 验证理解的练习：从按钮/计时器分别追到Mapper，标出首次可能写库的调用；解释fresh409确认后为何仍显示GENERATING而不是ERROR。
- 掌握状态：**待学习**。开发者掌握证据：未提供；待本人验证：独立说明副作用与恢复入口的可达性。
- 最近复核：2026-11-13，仅源码/工程证据核对，未开展本人学习验收。

<a id="l-b4e-04"></a>

#### L-B4E-04 — 请求超时后的结果不确定性

- 前置知识：请求与确认、网络超时、业务持久状态、重试与查询。
- 源码入口/调用者：`handleGenerateFailure/recoverAfterMutationFailure`、[isUncertainMutationFailure](../../frontend/src/features/interview/utils/uncertainMutation.ts)、RECOVERY_UNKNOWN卡片及timeout/502/服务不可用测试。
- 核心问题：POST超时为何不等于生成失败？为何先GET而不补发POST？服务不可用为什么在GENERATING路径不能直接回退FAILED/NOT_STARTED？
- 验证理解的练习：分别画出“服务器完成但响应丢失”“仍在生成”“已FAILED”“确认GET也失败”四种顺序；解释何时允许用户再次提交以及依据何来。
- 掌握状态：**待学习**。开发者掌握证据：未提供；待本人验证：能区分失败与未知结果，不以客户端异常伪造终态。
- 最近复核：2026-11-13，仅源码/工程证据核对，未开展本人学习验收。

<a id="l-b4e-05"></a>

#### L-B4E-05 — 前后端 CAS 恢复协作

- 前置知识：条件UPDATE、affected rows、乐观并发、短事务、任务所有权。
- 源码入口/调用者：[StaleRecoveryProperties](../../src/main/java/com/kun/aiinterview/common/recovery/StaleRecoveryProperties.java) 的isGeneratingStale/generatingCutoff；ReportService.generateReport；[SessionMapper XML](../../src/main/resources/mapper/InterviewSessionMapper.xml) 的reclaimStaleGenerating/markReportReady/markReportFailed；[真实CAS测试](../../src/test/java/com/kun/aiinterview/common/recovery/StaleRecoveryCasIntegrationTest.java)。
- 核心问题：数据库updated_at与后端cutoff如何协作？两次POST谁取得资格？为什么version必须使用本次CAS所得token？CAS为何不能保证旧供应商调用已停止或只计费一次？
- 验证理解的练习：手推两个相同expectedVersion并发UPDATE的1/0结果，以及旧version完成/失败的0行结果；说明READY重放和已有报告恢复如何避免重复Weakness更新。
- 掌握状态：**待学习**。开发者掌握证据：未提供；待本人验证：能区分页面防重、数据库提交权和外部副作用。
- 最近复核：2026-11-13，仅源码/工程证据核对，未开展本人学习验收。

<a id="l-b4e-06"></a>

#### L-B4E-06 — epoch / sessionId 防止过期响应污染

- 前置知识：闭包、异步交错、路由参数、组件卸载。
- 源码入口/调用者：`isCurrentScope/bootstrap/submitReport/refreshReport`与onUnmounted；专项测试的pre-recovery poll、old recovery、unmounted recovery。
- 核心问题：为什么检查sessionId还不够？同一会话提交前已发出的GET如何覆盖READY？为何旧finally不能把新请求posting清零？
- 验证理解的练习：控制旧GET在新POST READY之后返回，指出epoch在哪递增、在哪校验；再模拟41→42→41，解释为何相同sessionId也不能接受最早的响应。
- 掌握状态：**待学习**。开发者掌握证据：未提供；待本人验证：能独立设计一个迟到成功/失败的交错测试。
- 最近复核：2026-11-13，仅源码/工程证据核对，未开展本人学习验收。

<a id="l-b4e-07"></a>

#### L-B4E-07 — 用可控 Promise 与 fake timers 验证异步顺序

- 前置知识：Vitest、Vue Test Utils、微任务/计时器、测试替身边界。
- 源码入口/调用者：[deferred](../../frontend/tests/helpers/deferred.ts)、[专项测试](../../frontend/tests/InterviewReportView.spec.ts) 中advanceTimersByTimeAsync/flushPromises/waitFor、enableAutoUnmount以及401的Axios adapter；后端StaleRecoveryCasIntegrationTest的latch/并发SQL。
- 核心问题：可控Promise如何稳定制造迟到响应？fake timers为何不能证明真实后端过期？一次flushPromises为何不能保证懒加载路由已完成？如何证明恰好一次POST而不只是按钮变灰？
- 验证理解的练习：独立复现改前缺少恢复入口的红测，再解释GET次数、POST次数、disabled与最终页面四组断言。对照jsdom、MockMvc、真实MySQL和真实浏览器E2E各能证明什么。
- 掌握状态：**待学习**。开发者掌握证据：未提供；待本人验证：能独立复现并解释失败，不能仅运行后抄写“通过”。
- 最近复核：2026-11-13，仅源码/工程证据核对，未开展本人学习验收。

### 旧 B4-D 方向：未编号候选，尚未实施

2026-11-13 本轮用户将 B4-D 明确定义为 Milvus 治理；原 L-B4D-01/02 迁移为 L-CAND-01/02，保留原学习内容，不把它们记作本批实现。

<a id="l-cand-01"></a>

#### L-CAND-01 — 有界许可生命周期预学习

- 前置知识：Semaphore/tryAcquire、finally、状态机、请求线程。
- 源码入口：[Workflow](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java) 的 `evaluateAnswer`；[ReportService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java) 的 `generateReport/validateEvaluations/existingReportOrContinue`；[C3 第 7 节 D1](../verification/C3_B4A_B4C_CLOSEOUT_20261112.md)。
- 核心问题：许可应在 claim 前还是后？如何避免拒绝留下进行中状态或把重放也拒绝？
- 验证理解的练习：仅做方案练习：列正常、异常、中断、CAS 失败、stale、重放与超额路径，设计 latch 验收；没有现成许可组件可称已实现。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-cand-02"></a>

#### L-CAND-02 — 安全调用事件预学习

- 前置知识：SLF4J、单调时钟、MDC 生命周期、指标基数。
- 源码入口：[DeepSeek Client](../../src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java) 的 `requestCompletion`；[Orchestration](../../src/main/java/com/kun/aiinterview/interview/orchestration/EvaluationOrchestrationService.java) 的 `evaluate/prepareEvaluation`；[B4-A 第 8 节／D2](../verification/B4A_DEEPSEEK_RELIABILITY_AUDIT_20261112.md)。
- 核心问题：一次供应商调用和一次业务 attempt 如何分别统计？为什么 userId/正文不能做指标标签？
- 验证理解的练习：拟定固定字段与完成事件断言；说明失败、重试、旧任务和未知 usage 的统计边界。事件组件尚未实现。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；复核日期：待补。

### B5-0：全系统准入审查与真实测试设计

关联 [B5-0台账](IMPROVEMENT_LEDGER.md#b5-0)、[准入审查](../verification/B5_0_SYSTEM_READINESS_REVIEW_20261113.md)、[测试计划](../verification/B5_REAL_WORLD_TEST_PLAN_20261113.md)。源码基线为HEAD `01ac774caff491b978dd2268e8b427355a64b97a` 加B4-D/E工作区；本批仅文档修改。最近核验2026-11-13，Codex；没有开发者本人学习验收。

<a id="l-b5-01"></a>

#### L-B5-01 — 累计树、证据层次与准入结论

- 前置知识：Git HEAD/index/working tree/remote、JUnit/Vitest、集成测试与Mock、已有L-X-01～03。
- 源码入口：[EnvironmentIsolationInitializer](../../src/main/java/com/kun/aiinterview/config/EnvironmentIsolationInitializer.java)、[test配置](../../src/test/resources/application-test.yaml)、[FullInterviewBusinessOfflineTest](../../src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java)；报告第2/6节及run_checks.py证据。
- 核心问题：为什么1732/0/0/19不等于1732项真实外部通过？为何B4-D/E已通过但不能说已提交？为什么有外围事务的业务链不独立证明跨请求提交？
- 独立练习：从本轮XML重算T/F/E/S，列7个跳过类；用Git祖先关系解释远端落后3个提交和未提交树差别；写出GO/CONDITIONAL_GO/NO_GO的证据边界。
- 尚待验证：本人能独立复算与解释，尚无材料。掌握状态：**待学习（未确认）**；开发者证据未提供，学习复核日期待补。

<a id="l-b5-02"></a>

#### L-B5-02 — 字符数、UTF-8字节与BCrypt输入合同

- 前置知识：Java/JavaScript String.length、UTF-8、Bean Validation、BCrypt、HTTP错误映射。
- 源码入口：[RegisterRequest](../../src/main/java/com/kun/aiinterview/auth/dto/RegisterRequest.java)、[ChangePasswordRequest](../../src/main/java/com/kun/aiinterview/user/dto/ChangePasswordRequest.java)、[PasswordConfig](../../src/main/java/com/kun/aiinterview/config/PasswordConfig.java)、[authFormRules](../../frontend/src/features/auth/validation/authFormRules.ts)；AuthService.register/UserService.changePassword。
- 核心问题：25个“界”为何通过@Size但触发500？为何不能截断密码或把所有IllegalArgumentException都映射400？注册/改密/登录的实际现状为何不相同？
- 独立练习：复述ASCII72、中文72/75与emoji边界；在合成隔离环境独立复现并证明无新用户/无密码覆盖；只提出统一合同方案，未授权前不实施B5-FIX-A。
- 尚待验证：修复方案及本人复现均未完成。掌握状态：**待学习（未确认）**；开发者证据未提供，学习复核日期待补。
- 2026-11-13后续工程状态：B5-FIX-A已获本轮授权，以B5-P2-A完成实现与回归，详见L-B5P2A-01～03。上条保留为B5-0当时快照；本人学习、复现和掌握仍未确认。

<a id="l-b5-03"></a>

#### L-B5-03 — 从Embedding异常到最终日志的泄露通道

- 前置知识：L-B4B-01～03、Throwable cause/suppressed、Spring AI错误处理、Logback渲染。
- 源码入口：[EmbeddingConfiguration](../../src/main/java/com/kun/aiinterview/knowledge/embedding/EmbeddingConfiguration.java).createOpenAiApi/createEmbeddingModel、[SpringAiEmbeddingClient](../../src/main/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingClient.java).requestBatch、[GlobalExceptionHandler](../../src/main/java/com/kun/aiinterview/common/exception/GlobalExceptionHandler.java).handleExternalServiceException。
- 核心问题：固定“调用Embedding服务失败”为何仍携带正文？客户端502脱敏为什么不证明日志安全？只给最外层换message能否堵住框架自身日志？
- 独立练习：用合成sentinel画完整传播链，分别断言公开Result、Throwable树和最终Logback输出；说明本次仅证明合成回显，不证明真实密钥已泄露。B5-FIX-B仍候选。
- 尚待验证：完整Embedding旁路修复与真实凭据安全测试未做。掌握状态：**待学习（未确认）**；开发者证据未提供，学习复核日期待补。
- 2026-11-13后续工程状态：B5-FIX-B已获授权，以B5-P2-B完成边界修复、合成sentinel日志验收和完整回归，详见L-B5P2B-01～03。上条为B5-0时点快照；未使用真实凭据，本人学习和掌握仍未确认。

<a id="l-b5-04"></a>

#### L-B5-04 — 同请求恢复与跨设备能力边界

- 前置知识：localStorage、身份作用域、幂等键、数据库CAS/attempt/version，L-B1及L-B4E系列。
- 源码入口：[submitRecordStorage](../../frontend/src/features/interview/storage/submitRecordStorage.ts)、[useInterviewSession](../../frontend/src/features/interview/composables/useInterviewSession.ts).retrySamePair/handleConflict、[InterviewService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewService.java).handleExistingAnswer/buildDetailQuestion；报告恢复另看useInterviewReport。
- 核心问题：重新登录为何能保留原记录，而换空白浏览器不能自动找回requestId？为什么GET等待10分钟不接管？数据库单胜者为何不等于模型单次计费？
- 独立练习：手推“答案已完成但响应丢失”“FAILED且原键保留”“FAILED且键丢失”三条路径；依H07/H09/H10/H13拟定真实故障证据，区分请求未送达与已送达丢响应。
- 尚待验证：本人讲解及真实双浏览器/崩溃操作。掌握状态：**待学习（未确认）**；开发者证据未提供，学习复核日期待补。

<a id="l-b5-05"></a>

#### L-B5-05 — 独立人工基准、随机性与模型成本记录

- 前置知识：评分点覆盖、实验控制、重复样本、请求与模型调用差别、usage与价格。
- 源码入口：[EvaluationPromptBuilder](../../src/main/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilder.java)、[LlmEvaluationValidator](../../src/main/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidator.java)、[DeepSeekEvaluationClient](../../src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java).buildRequest/DeepSeekChatResponse；计划第4节。
- 核心问题：AI自评为什么不是绝对真值？同requestId输出稳定为什么不证明模型稳定？输入/上下文不同和随机波动怎么区分？没有usage为什么不能用字符数算成本或填0？
- 独立练习：给同一真实题库MAIN设计弱/一般/强/错/特殊答案并人工逐点评审；提出3轮重复的复核触发器，列供应商账单归因和未知字段；不进行未授权付费调用。
- 尚待验证：语义质量、真实usage/费用和开发者独立标注。掌握状态：**待学习（未确认）**；开发者证据未提供，学习复核日期待补。

<a id="l-b5-06"></a>

#### L-B5-06 — FINAL、追问规则与报告数据库验收

- 前置知识：SQL JOIN/GROUP BY、事务、BigDecimal/HALF_UP、已有L-B3系列。
- 源码入口：[FollowUpPolicy](../../src/main/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpPolicy.java).decide、[FollowUpTargetResolver](../../src/main/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpTargetResolver.java)、[InterviewReportService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java).validateEvaluations/calculateOverallScore、[ReportTransaction](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java)。
- 核心问题：低分为何不必追问？MAIN INITIAL为何不能加入平均分？“5条评价”与“5个MAIN各一个合法FINAL”有何差别？报告和弱项为何必须一起验证回滚？
- 独立练习：使用计划Q1～Q6在合成只读快照中核对归属、计数、均分和重复读取无增量；用一个INITIAL误入报告的反例证明仅计数不足。
- 尚待验证：本人独立SQL结果和解释。掌握状态：**待学习（未确认）**；开发者证据未提供，学习复核日期待补。

### B5-P2-A：密码 UTF-8 字节边界

关联[本批台账](IMPROVEMENT_LEDGER.md#b5-p2-a)与[验收报告](../verification/B5_P2A_PASSWORD_UTF8_BOUNDARY_20261113.md)。源码为HEAD `01ac774caff491b978dd2268e8b427355a64b97a`加本批未提交树，无本批SHA。最近复核2026-11-13，Codex；工程测试通过，开发者学习未验收。

<a id="l-b5p2a-01"></a>

#### L-B5P2A-01 — UTF-16、UTF-8 与 BCrypt 历史校验语义

- 前置知识：L-B5-02、Java/JavaScript String.length、字符编码、单向散列。
- 源码入口：[Utf8ByteSizeValidator.isWithinLimit](../../src/main/java/com/kun/aiinterview/common/validation/Utf8ByteSizeValidator.java)、[PasswordConfig](../../src/main/java/com/kun/aiinterview/config/PasswordConfig.java)、实际crypto6.5.11 BCrypt.hashpw/checkpw；[authFormRules](../../frontend/src/features/auth/validation/authFormRules.ts)、PasswordUtf8BoundaryTest.shouldCharacterizeActualEncoderAndByteFixtures。
- 核心问题：为什么72个字符不等于72字节？emoji为何占两个String.length单位？为什么encode抛异常但matches可能返回false或true？为什么不能从已有散列推断原密码字节数？TextEncoder检测与修改密码内容有什么区别？实际Hibernate Validator8的@NotBlank为什么不能等同String.isBlank或JavaScript trim？
- 独立练习：只用合成样本计算ASCII、中文、emoji和组合字符的长度/字节数；复现72与73字节，并解释历史匹配前缀的风险与新API拒绝决定；禁止输出真实密码或散列。
- 掌握状态：**待学习（未确认）**。开发者证据：未提供；本人复现和讲解尚待验证。最近复核：2026-11-13／Codex，仅工程证据。

<a id="l-b5p2a-02"></a>

#### L-B5P2A-02 — DTO 校验、服务直接调用与前端输入完整性

- 前置知识：Bean Validation、@Valid/@Validated、Spring代理、BusinessException、HTTP错误映射。
- 源码入口：RegisterRequest/LoginRequest/ChangePasswordRequest；[PasswordPolicy.validate/validateCurrent](../../src/main/java/com/kun/aiinterview/common/validation/PasswordPolicy.java)；AuthService.register/login、UserService.changePassword；[非代理测试](../../src/test/java/com/kun/aiinterview/auth/PasswordUtf8BoundaryTest.java)；LoginView/RegisterView/ProfileView的PasswordField调用。
- 核心问题：UserService有@Validated为何不能自动校验DTO内部字段？手动new为何绕过方法代理？为何要在编码器/数据库前检查，不能捕获所有IllegalArgumentException？为什么当前密码不统一加8位下限？maxlength为什么可能静默裁掉粘贴尾部？
- 独立练习：沿四个HTTP密码字段标注DTO与服务边界；用Mockito零交互证明直接调用防护；独立修改一条合成边界测试，确认前端保持输入并阻止请求，后端仍独立返回400。
- 掌握状态：**待学习（未确认）**。开发者证据：未提供；本人对代理边界和不裁剪原则的解释尚待验证。最近复核：2026-11-13／Codex。

<a id="l-b5p2a-03"></a>

#### L-B5P2A-03 — 密码修复的持久化、并发、令牌与日志验收

- 前置知识：事务提交、SELECT FOR UPDATE、负向断言、JWT credential_version、Logback、L-B5-01测试证据层次。
- 源码入口：[PasswordUtf8BoundaryIntegrationTest](../../src/test/java/com/kun/aiinterview/auth/PasswordUtf8BoundaryIntegrationTest.java)的shouldRejectAtEveryHttpBoundaryWithoutWriting/shouldStillVerifyALegacyShortCurrentPassword/cleanUpAndCheckLogs；ConcurrentPasswordChangeIntegrationTest、UserControllerIntegrationTest、JwtAuthenticationFilterTest；前端四个相关spec。
- 核心问题：400为何还需证明没有INSERT/UPDATE？为什么检查散列和两个时间列？无外围测试事务与MockMvc分别能证明什么？旧Token同秒失效靠什么？为何前端成功提示或Mock成功不证明真实MySQL锁？日志只看HTTP脱敏为何不够？
- 独立练习：从本批最终XML独立复算1780/0/0/19，并指出19项不是外部通过；解释单胜者测试中的锁等待、失败方与赢家散列；用样本标签而非密码做测试名，检查响应及日志异常链；复述历史超限密码需独立恢复方案的原因。
- 掌握状态：**待学习（未确认）**。开发者证据：未提供；未完成本人SQL、并发时序或日志检查产出。最近复核：2026-11-13／Codex。

### B5-P2-B：Embedding 上游错误日志脱敏

关联 [工程台账](IMPROVEMENT_LEDGER.md#b5-p2-b) 与 [B5-P2-B验收](../verification/B5_P2B_EMBEDDING_LOG_SAFETY_20261113.md)。代码为HEAD `01ac774caff491b978dd2268e8b427355a64b97a`加本批未提交修改；以下3条均**待学习**，工程通过不能代替开发者验收。

<a id="l-b5p2b-01"></a>

#### L-B5P2B-01 — HTTP响应、Throwable与最终日志的三个安全边界

- 关联工程／SHA：B5-P2-B，当前工作区无本批提交SHA；报告第2、3、6节。
- 前置知识：Java异常cause/suppressed、RestClient、Logback、L-B5-03及B4-B日志边界。
- 源码入口：[SpringAiEmbeddingClient.requestBatch/validateResponseModel](../../src/main/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingClient.java) → [GlobalExceptionHandler.handleExternalServiceException](../../src/main/java/com/kun/aiinterview/common/exception/GlobalExceptionHandler.java)；[SpringAiEmbeddingLogSafetyTest](../../src/test/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingLogSafetyTest.java) 的assertSafeFailure。
- 核心问题：为什么固定502和外层message不能证明安全？为什么原cause不能继续保留？为什么上游模型名与换行也要视为不可信？不打印异常与仅保留本地安全栈有什么区别？
- 独立练习／讲解验收：使用报告留存的改前探针，在隔离临时副本复现HTTP400和恶意model；分别检查HTTP、Throwable树、最终日志，解释首轮JSON未命中与第二轮实证的差别；不要改正式测试为允许泄漏。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供。
- 尚待验证：本人能独立定位正文进入日志的第一处边界，并解释为何不能宣称空响应WARN已泄漏输入。
- 最近复核：2026-11-13／Codex，仅工程证据；无掌握状态升级。

<a id="l-b5p2b-02"></a>

#### L-B5P2B-02 — Spring AI转换前校验与精确异常分类

- 关联工程／SHA：B5-P2-B；当前工作区无提交SHA，报告第2、4节。
- 前置知识：继承/覆盖、HTTP错误handler、JSON解码、类型判断、有界循环。
- 源码入口：[EmbeddingOpenAiApi](../../src/main/java/com/kun/aiinterview/knowledge/embedding/springai/EmbeddingOpenAiApi.java).embeddings与专属ResponseErrorHandler；[EmbeddingConfiguration](../../src/main/java/com/kun/aiinterview/knowledge/embedding/EmbeddingConfiguration.java).createOpenAiApi/createNoRetryTemplate；SpringAiEmbeddingClient.safeFailureCategory。实际Spring AI1.1.8源码链接见验收报告。
- 核心问题：为什么删除catch RuntimeException前必须拦截null data/item？为什么框架默认10次retry不是项目实际策略？为什么只复制数字状态／固定类别，且优先识别嵌套超时？为什么程序缺陷不能统一502？
- 独立练习／讲解验收：沿HTTP503、坏JSON、null data和独立NPE各画一次调用路径；解释no-read测试；独立补一个JSON包装SocketTimeout用例，断言无cause/suppressed、一次请求及正确分类。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供。
- 尚待验证：本人能解释10层上限、环形链和类别降级，以及小型API子类与复制整个模型实现的维护取舍。
- 最近复核：2026-11-13／Codex，仅实现/测试复核；无掌握状态升级。

<a id="l-b5p2b-03"></a>

#### L-B5P2B-03 — 日志故障矩阵与业务FAILED恢复的分层验证

- 关联工程／SHA：B5-P2-B；当前工作区无提交SHA，报告第6、7节。
- 前置知识：Mock HTTP、MockMvc、真实MyBatis/MySQL、CAS/attempt、事务、L-B5-01证据层次。
- 源码入口：[FullInterviewBusinessOfflineTest](../../src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java).embeddingFailureKeepsLogsSafeAndAllowsExplicitAnswerRecovery/embeddingFailureDoesNotWriteVectorsAndDocumentRetryCanReachReady/useHttpEmbedding；KnowledgeDocumentProcessingService的processDocument；InterviewWorkflowService的evaluateAnswer。
- 核心问题：为什么失败后无评价／不推进还要验证原payload重试与成功重放？为什么Embedding失败前没有向量写入就不应补偿？为何真实MySQL加外围@Transactional不能独立证明跨请求提交？为什么日志sentinel为0不等于所有生产日志安全？
- 独立练习／讲解验收：在隔离ai_interview_test运行两条恢复路径，解释SQL状态、外部调用1→2→2和日志断言；从Surefire复算T/F/E/S，指出19项门禁及真实Milvus替身边界。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供。
- 尚待验证：本人独立复现、SQL/日志证据与对补偿／无外围事务测试的比较；不要求付费调用真实模型。
- 最近复核：2026-11-13／Codex，仅工程事实；无掌握状态升级。

### C6：累计可靠性基线与真实测试准入

关联[工程台账](IMPROVEMENT_LEDGER.md#c6)与[C6收口报告](../verification/C6_RELIABILITY_PHASE_CLOSEOUT_20261113.md)。HEAD仍为`01ac774caff491b978dd2268e8b427355a64b97a`加B4-D/E、P2-A/B未提交树；C6只修改文档，复用经快照／依赖／XML核验的后端1832/0/0/19、前端336/0/0/0，并非本轮新执行测试。以下学习状态不能由工程准入自动升级。

<a id="l-c6-01"></a>

#### L-C6-01 — 同一代码快照的回归证据复用

- 关联工程记录／验收／SHA：C6第1、3、4节；当前工作区无C6提交SHA，远端状态见台账。
- 前置知识：L-X-01、Git HEAD与dirty tree、SHA-256、Surefire XML、测试替身与退出码。
- 源码／证据入口：[pom.xml](../../pom.xml)、[frontend/package-lock.json](../../frontend/package-lock.json)、[EnvironmentIsolationInitializer](../../src/main/java/com/kun/aiinterview/config/EnvironmentIsolationInitializer.java)、各原验收报告的运行器及XML路径；C6的historical-xml-audit.json、batch-history-hash-audit.json、package-dependency-audit.json。
- 核心问题：HEAD相同为何不代表源码相同？总数1832相同为何不足以复用？final-regression与previous-reports如何避免混计？为何19 skipped和skipTests package不能记为外部集成通过？JAR同哈希、99个依赖一致分别证明什么，又不能证明什么？
- 独立练习／讲解验收：不运行真实模型，从139份XML独立复算1832/0/0/19和1813非跳过，列出七类门禁；比较两个批次快照，解释文档变化可复用、代码／依赖变化必须重新评估；给出一条“相同总数但不同测试”的反例。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供。
- 尚未理解／待验证点：本人对证据时点、环境错误、原始日志范围和复用条件的独立说明；临时目录不等于持久归档。
- 最近复核日期／人：2026-11-13／Codex，仅工程审查；无掌握状态升级。

<a id="l-c6-02"></a>

#### L-C6-02 — 异常安全、显式恢复与提交权的跨批关系

- 关联工程记录／验收／SHA：C6第2、4、5节；B4-B/C已在本地提交，其余为当前未提交树。
- 前置知识：L-B4B/C、L-B4D、L-B4E、L-B5P2A/B，事务、Throwable、CAS与JWT。
- 源码入口：[DeepSeekEvaluationClient](../../src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java).requestCompletion；[EmbeddingOpenAiApi](../../src/main/java/com/kun/aiinterview/knowledge/embedding/springai/EmbeddingOpenAiApi.java).embeddings；[MilvusConfiguration](../../src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusConfiguration.java).milvusClient；[InterviewWorkflowTransactionService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java).persistOwnedEvaluation；[InterviewReportService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java).generateReport；[useInterviewReport](../../frontend/src/features/interview/composables/useInterviewReport.ts).submitReport；[UserService](../../src/main/java/com/kun/aiinterview/user/service/UserService.java).changePassword。
- 核心问题：断开上游cause为何仍要抛失败？程序错误为何不能一律502？HTTP一次、SDK一次callable和业务显式恢复为何不同？attemptId/version/epoch各保护哪一层？JWT签名有效为何还要查当前散列和角色？为什么日志中3次通用合成标记不推翻客户端安全专项，也不证明全局安全？
- 独立练习／讲解验收：手推Embedding503→FAILED→原payload恢复→成功重放的1→2→2调用；再推报告POST超时→GET READY、旧attempt迟到和改密失败／成功的数据库及Token变化。指出每条路径的SQL／测试依据，以及仅Mock不能证明的部分。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供。
- 尚未理解／待验证点：本人能说明“唯一有效提交”不等于“唯一供应商执行／计费”，并区分源异常保真与不可信外部文本隔离。
- 最近复核日期／人：2026-11-13／Codex；不要求在学习练习中付费调用或改正式数据。

<a id="l-c6-03"></a>

#### L-C6-03 — 按历史hunk组织提交与有条件真实准入

- 关联工程记录／验收／SHA：C6第6、7节；现有B4-A/B/C三提交保留，后续五批仅计划，没有提交SHA。
- 前置知识：L-X-02、diff hunk、文件依赖、profile／数据库隔离、测试预算与数据归属。
- 源码入口：[FullInterviewBusinessOfflineTest](../../src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java)新增Embedding测试及useHttpEmbedding；[authFormRules](../../frontend/src/features/auth/validation/authFormRules.ts)与Profile调用方；[EmbeddingConfiguration](../../src/main/java/com/kun/aiinterview/knowledge/embedding/EmbeddingConfiguration.java)与新增API子类；[真实测试计划](../verification/B5_REAL_WORLD_TEST_PLAN_20261113.md)及C6更新条款。
- 核心问题：共享文件为什么不能仅凭文件名拆批？当前FullInterviewBusinessOfflineTest的6个未提交hunk为何全部属于P2-B？两个从未跟踪的台账为何适合最后整文件纳入？mvnw mode、Compose、MQ文档为何排除？CONDITIONAL_GO为什么不等于可立即消耗额度或公开发布？
- 独立练习／讲解验收：只读复核五组50路径和五项排除清单，指出缺PasswordPolicy／EmbeddingOpenAiApi／旧passwordMax调用时的编译风险；自行填写一份不含秘密的evaluation隔离、请求／金额预算、合成账号、精确ID清理、HTTP/SQL/日志采集准入单，明确哪些字段仍UNKNOWN。不得执行git add/commit/push或真实付费请求作为默认练习。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供。
- 尚未理解／待验证点：本人hunk归属说明、历史超长密码兼容判断、真实Milvus验证与停止条件；真实使用前仍需单独任务授权。
- 最近复核日期／人：2026-11-13／Codex，仅计划复核。

- 后续状态更新（C7，2026-11-13）：五批文件／hunk 计划已按用户明确授权进入本地 Git 收口，四批代码 SHA 已记录于台账；独立导出的中间代码版本编译／类型检查通过。可用这些真实提交复核缺类／缺导出风险和共享测试归属；未收到开发者本人讲解、复现或独立修改证据，本条仍为待学习（未确认），也未授权推送或真实服务调用。

### D0：数据事实、随机快照与多用户试验设计

关联 [D0 工程记录](IMPROVEMENT_LEDGER.md#d0) 与 D0/D1/D3/D4 四份方案。下列五项新增条目均为待学习；审计和设计产出不能替代开发者学习验收。阅读练习不授权写库、调用模型或启动服务。

<a id="l-d0-01"></a>

#### L-D0-01 — 区分配置、历史证据与实时数据

- 关联工程记录／报告／SHA：[D0](../verification/D0_DATA_READINESS_AUDIT_20261113.md) 第2/5/6节；HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`，D0无新提交。
- 前置知识：TCP连接、Docker端口映射、进程环境变量、MySQL只读事务、哈希。
- 源码入口：`application.yaml` 的 datasource/Milvus 配置、`EnvironmentIsolationInitializer`、`QuestionMapper.selectEnabledQuestionsForInterview`、`KnowledgeChunkMapper.selectRetrievableByVectorIds`；本地配置只读且不输出秘密。
- 核心问题：PermissionError为什么不能证明停服？ConnectionRefused为什么不能证明空库？历史3306导入与当前3308卷如何对应？仓库JSON为什么不能给出ENABLED状态？
- 独立练习／讲解验收目标：不用历史数字，列出只读连接、实例身份、同快照统计、逻辑题号匹配和MySQL/Milvus双向身份检查的证据链；将未知项明确标BLOCKED。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；当前只进行了代理离线审计。
- 尚未理解／待验证点：真实库/集合数据和迁移完整性，环境恢复后再补，不默认操作已有卷。
- 最近复核日期／人：2026-11-13／Codex。状态变更：新增；无开发者升级证据。

<a id="l-d0-02"></a>

#### L-D0-02 — 可复现随机组卷与固定快照

- 关联工程记录／报告／SHA：[D1](../verification/D1_QUESTION_BANK_EXPANSION_PLAN_20261113.md) 第4节；同上HEAD。随机方案未实现。
- 前置知识：集合去重、伪随机数、REPEATABLE_READ、短事务、用户行锁和并发幂等；可复习L-B1条目。
- 源码入口：`QuestionPlanSelector.select/findFirstUnselectedByCategory`、`InterviewQuestionPlanSnapshotService.prepareSnapshot`、`InterviewSessionService.createSession`、`InterviewSessionTransactionService.createAndStartSession`、`InterviewQuestionSnapshotFactory.createMainQuestion`；对应selector/snapshot/session测试。
- 核心问题：为什么扩到63题仍不自动随机？seed之外还要固定什么？两个请求抽出不同草稿为何最终只能返回同一活跃Session？历史避重为什么可先不加Schema？
- 独立练习／讲解验收目标：纸面推演每类3候选抽1、同场无重复、缺分类失败、A/B竞争和管理员更新；解释3^5组合不保证相邻场不重复；提出受控随机索引测试而非“不同seed一定不同”。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供。
- 尚未理解／待验证点：历史窗口/回退语义；实际随机实现和真MySQL并发回归均待后续授权。
- 最近复核日期／人：2026-11-13／Codex。状态变更：新增设计学习，不标已编码或已掌握。

<a id="l-d0-03"></a>

#### L-D0-03 — 评分点、来源版本与切片覆盖

- 关联工程记录／报告／SHA：[D3](../verification/D3_RAG_CORPUS_EXPANSION_PLAN_20261113.md) 第2～7节、D0的H01发现；当前HEAD。
- 前置知识：Java泛型/JLS静态类型、UTF-8与UTF-16、RAG检索、数据库版本和内容hash。
- 源码入口：`KnowledgeTextChunker.split/findNaturalBoundary/calculateNextStart`、`KnowledgeDocumentProcessingService.processDocument`、`KnowledgeRetrievalService.retrieve`；H01源文档与题库JSON；4D3切片表。
- 核心问题：题目参考答案为什么不能充当独立知识来源？97个映射为什么不等于97点无歧义？离线52Chunk为什么不能代表Milvus52实体？READY/ACTIVE/profile过滤后如何保留原排名？
- 独立练习／讲解验收目标：解释H01两个静态类型的instanceof编译差异；选一评分点，追踪官方节→原创段落→实际Chunk→检索资格→Prompt证据，区分版本、许可待核项及每一步未执行证据。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；Codex运行两个临时编译探针不是开发者掌握证据。
- 尚未理解／待验证点：历史JSON缺失、逐篇许可、留出金标准、新SP及真实召回；30篇新语料只有主题设计。
- 最近复核日期／人：2026-11-13／Codex。状态变更：新增；保持未确认。

<a id="l-d0-04"></a>

#### L-D0-04 — 独立人审、模型质量与调用预算

- 关联工程记录／报告／SHA：[D4-A](../verification/D4_MULTI_AGENT_TEST_PLAN_20261113.md#2-a多-ai-模拟面试质量测试)；当前HEAD，试验未运行。
- 前置知识：实验变量、盲评、样本量、MAE、模型token/usage、INITIAL/FINAL和Java计分。
- 源码入口：`EvaluationPromptBuilder.build`、`LlmEvaluationValidator.validate`、`EvaluationScoreCalculator.calculate`、`FollowUpPolicy.decide`、`InterviewReportService`；候选人运行器未实现。
- 核心问题：角色标签为什么不是正确性真值？为什么不能让同一模型的判断替代独立人工？requestId重放为何不算新质量样本？评分点权重与五维总分有什么区别？
- 独立练习／讲解验收目标：独立复算12场84个MAIN的96～180次DeepSeek、84～168次Embedding/考生生成上限；设计先盲评后揭盲的误差分析，列出费用UNKNOWN及停止阈值。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供。
- 尚未理解／待验证点：实际模型价格/配额、真实usage、人评一致性和质量样本；没有授权本条自动触发收费请求。
- 最近复核日期／人：2026-11-13／Codex。状态变更：新增，工程方案与掌握分开。

<a id="l-d0-05"></a>

#### L-D0-05 — 多账号并发、替身边界与最终状态核验

- 关联工程记录／报告／SHA：[D4-B](../verification/D4_MULTI_AGENT_TEST_PLAN_20261113.md#3-b多用户并发与可靠性测试)；当前HEAD；可关联L-B1/B2/B3及L-C6-02。
- 前置知识：真实HTTP与MockMvc差异、JWT/归属、唯一约束、CAS/attempt fencing、故障注入和数据库事务。
- 源码入口：`InterviewService.submitAnswer/handleExistingAnswer`、`InterviewWorkflowService.evaluateAnswer`、`InterviewWorkflowTransactionService`、`InterviewReportService/TransactionService`、`InterviewAnswerMapper.xml`与`AnswerEvaluationMapper.xml`；负载替身装配/运行器未实现。
- 核心问题：跨账号相同requestId为何会冲突？HTTP超时为何不能换新键？stale重试为何可能再次收费却不能重复落业务结果？关闭Milvus开关为什么不等于完整Mock负载链？
- 独立练习／讲解验收目标：推演响应丢失、旧attempt迟到、报告重启恢复三条时序；逐条说明Q1～Q6核验的归属/快照/FINAL/报告/弱项不变量，给出真上游与替身分开的证据标签。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供。
- 尚未理解／待验证点：1/3/5/10真实用户与20/35/50VU实际结果、测试装配和观测；不能从历史单测推出容量。
- 最近复核日期／人：2026-11-13／Codex。状态变更：新增；未实施、未执行、未掌握均分别记录。

### E0：环境身份、只读快照与隔离恢复

<a id="l-e0-01"></a>

#### L-E0-01 — 从容器与卷追踪实际数据，并区分空样本和跨库一致性

- 关联工程记录／报告／SHA：[E0](IMPROVEMENT_LEDGER.md#e0)、[环境补审](../verification/E0_TEST_ENVIRONMENT_READINESS_20261113.md)；基线 `0c6f92afe331954f7cea37d33db688fe781cbf2c`，本批无提交。
- 前置知识：L-D0-01、Docker 命名卷/容器生命周期、TCP 端口映射、MySQL 一致性快照、Milvus embedded etcd 与标量查询。
- 源码／配置入口：`compose.mac.local.yaml`（未跟踪）、`application-evaluation.yaml`、`EnvironmentIsolationInitializer`、`KnowledgeChunkMapper.selectRetrievableByVectorIds`；E0 报告的 SQL 与跨库方案。
- 核心问题：为什么停止容器不代表数据丢失、空卷启动可能初始化？为什么容器3306与宿主3308是同一服务？空表36项异常为0能证明什么？为什么 MySQL 与 Milvus 各自计数相等仍不足以证明身份一致？为何隔离初始化器不能证明服务端实例身份？
- 独立练习／讲解验收目标：在不写库、不读取凭据的前提下画出容器→卷→服务器→数据库→chunk→vector ID 的证据链；说明一致性快照和跨库时间差、先完整备份再于副本诊断的原因，并标出当前缺失的 Milvus 证据。
- 掌握状态：**待学习（未确认）**；开发者掌握证据：未提供。尚待验证：副本恢复、schema/collection 身份和向量双向对照；最近复核 2026-11-13／Codex。状态变更：新增，未从代理检查推断掌握。

E0 仅新增本条学习任务；其余已有条目及历史计数记录保留，未进行开发者学习验收。

<a id="l-e0r2-01"></a>

#### L-E0R2-01 — embedded etcd 启停竞态、独立卷备份与向量持久化

- 关联工程记录／证据：[E0-R2](IMPROVEMENT_LEDGER.md#e0-r2)、[部署验收](../verification/E0R2_MAC_MILVUS_DEPLOYMENT_20261113.md)；代码基线 `0c6f92afe331954f7cea37d33db688fe781cbf2c`，本批未提交。
- 前置知识：L-E0-01、Docker 只读卷挂载、etcd 单节点选主、COSINE、Milvus 持久化与一致性查询。
- 入口：`compose.milvus.e0r2.yaml`、`MilvusCollectionInitializer`、`MilvusVectorStoreClient`，以及报告所列上游 issue/修复 PR、隔离 SDK 验收源码与日志。
- 核心问题：健康检查为何不能证明后续每次启动稳定？为何单节点选主也可能出现 leader changed？归档文件比对与服务恢复分别证明什么？为何 flush 和重启后读取要分别验证？精确删除后如何证明非目标数据保留？为何关闭阶段 panic 不能用下一次 healthy 掩盖？
- 独立练习：绘制两套容器→卷→etcd/向量与 SDK 测试的边界，解释本次两次重启时序、启动修复未回移及恢复前的身份核验；不在原卷进行破坏性演练。
- 掌握状态：**待学习（未确认）**；开发者掌握证据未提供，最近复核 2026-11-13／Codex。新增这一条不改变旧批次的历史计数。

2026-11-13 E0-R3 更新：在 D0 的 68 条之后新增 L-E0-01、L-E0R2-01 和以下 2 条，当前共 72 条；历史批次计数保留。工程验证不等于本人掌握。

<a id="l-e0r3-01"></a>

#### L-E0R3-01 — SDK 停机超时、异常边界与恢复

- 证据：[E0-R3](../verification/E0R3_MILVUS_STABILITY_GATE_20261113.md)；入口：`MilvusConfiguration`、`MilvusVectorStoreClient.search`、`MilvusGrpcDeadlineTest`、`MilvusSdkRetryBehaviorTest`。
- 核心问题：为何 health 恢复后 shard 尚可能不可用？deadline、单次尝试、异常封装与同一客户端后续读成功分别证明什么？为什么不能推断在途写入成功或安全重试？
- 独立练习：仅阅读记录和源码，画出停止、超时、启动、不可用和恢复的时间线，说明每处证据及未知范围。
- 掌握状态：**待学习（未确认）**；开发者本人证据未提供。

<a id="l-e0r3-02"></a>

#### L-E0R3-02 — 合成向量持久化与隔离入库对账

- 证据：[E0-R3](../verification/E0R3_MILVUS_STABILITY_GATE_20261113.md)；入口：`EnvironmentIsolationInitializer`、`KnowledgeDocumentProcessingService.processDocument`、`KnowledgeDocumentProcessingTransactionService.persistChunksAndMarkReady`。
- 核心问题：为何 flush 后重启通过不证明未刷盘写入安全？为什么需要 ID/版本双向对账而非只比数量？隔离 schema、collection、实例身份和旧卷备份各保护什么？
- 独立练习：以报告中 B/C 的 ID、数量和分数画出重启前后证据链，并设计失败暂停、恢复核验与精确补偿清单；不操作原卷或调用模型。
- 掌握状态：**待学习（未确认）**；开发者本人证据未提供。

### 未定义范围的候选占位

2026-11-13用户已明确B5-0为准入审查/测试计划，原B5占位拆为上方6条；B6～B9仍未定义。以下不构成开发授权，没有伪造的源码入口。

<a id="l-b6-01"></a>

#### L-B6-01 — B6 技术点待定义

- 前置知识：待任务范围明确后识别，不预设技术栈。
- 源码入口：待确认；本批尚无已定义实现或关键方法。
- 核心问题：本批要解决哪一个有证据的问题？应阅读什么调用链、边界和测试？
- 下一步：先补问题证据、正式范围与源码入口，再拆成可验证的学习条目；当前无学习完成结论。
- 掌握状态：**待学习（范围待确认，掌握未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b7-01"></a>

#### L-B7-01 — B7 技术点待定义

- 前置知识：待任务范围明确后识别，不预设技术栈。
- 源码入口：待确认；本批尚无已定义实现或关键方法。
- 核心问题：本批要解决哪一个有证据的问题？应阅读什么调用链、边界和测试？
- 下一步：先补问题证据、正式范围与源码入口，再拆成可验证的学习条目；当前无学习完成结论。
- 掌握状态：**待学习（范围待确认，掌握未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b8-01"></a>

#### L-B8-01 — B8 技术点待定义

- 前置知识：待任务范围明确后识别，不预设技术栈。
- 源码入口：待确认；本批尚无已定义实现或关键方法。
- 核心问题：本批要解决哪一个有证据的问题？应阅读什么调用链、边界和测试？
- 下一步：先补问题证据、正式范围与源码入口，再拆成可验证的学习条目；当前无学习完成结论。
- 掌握状态：**待学习（范围待确认，掌握未确认）**。开发者掌握证据：未提供；复核日期：待补。

<a id="l-b9-01"></a>

#### L-B9-01 — B9 技术点待定义

- 前置知识：待任务范围明确后识别，不预设技术栈。
- 源码入口：待确认；本批尚无已定义实现或关键方法。
- 核心问题：本批要解决哪一个有证据的问题？应阅读什么调用链、边界和测试？
- 下一步：先补问题证据、正式范围与源码入口，再拆成可验证的学习条目；当前无学习完成结论。
- 掌握状态：**待学习（范围待确认，掌握未确认）**。开发者掌握证据：未提供；复核日期：待补。


2026-11-13 E1 更新：新增以下 2 条，现共 74 条；工程结果 BLOCKED，开发者掌握均未确认。

<a id="l-e1-01"></a>

#### L-E1-01 — 隔离 DDL 与并发恢复字段核验

- 证据：[E1 报告](../verification/E1_EVALUATION_ENVIRONMENT_SETUP_20261113.md)；入口：`sql/ai_interview.sql`、`sql/migrations/20261015_timeout_attempt_fencing.sql`、`EnvironmentIsolationInitializer`。
- 前置知识：MySQL DDL、外键、索引、并发 attempt fencing。
- 核心问题：为何历史隔离快照不能直接执行，如何确认目标名称及逐列、约束和索引的一致性？
- 独立练习：指出两个 attempt 字段及调用链；说明静态审阅与实际建库验证的差异。
- 状态：待学习，掌握未确认；开发者证据未提供。

<a id="l-e1-02"></a>

#### L-E1-02 — Milvus 一致备份与隔离恢复

- 证据：[E1 报告](../verification/E1_EVALUATION_ENVIRONMENT_SETUP_20261113.md)、E0-R2/R3；入口：`MilvusCollectionInitializer` 和 SDK 只读盘点。
- 前置知识：Docker 容器与数据卷、Milvus Schema、强一致查询、备份恢复。
- 核心问题：为什么在线打包卷不能直接当一致快照，如何区分备份可读、恢复演练和跨库对账？
- 独立练习：画出旧卷与新实例的映射，说明停机风险、独立卷恢复和故障中止条件。
- 状态：待学习，掌握未确认；开发者证据未提供。

2026-11-13 E1–E3 更新：新增3条，累计77条。A/B/C真实环境与导入验收通过，D收费执行仍待授权；没有开发者本人学习验收，全部新增项保持待学习。已有L-E1-01/02的恢复与隔离证据补充见本次报告，不改其掌握状态。

<a id="l-e1e3-01"></a>

#### L-E1E3-01 — 批量业务导入的外层事务和失败回滚

- 工程记录：[E1–E3](IMPROVEMENT_LEDGER.md#e1-e3)；[执行报告](../verification/E1_E3_REAL_DATA_EXECUTION_20261113.md)。基线HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`，本批无新提交SHA。
- 前置：Spring代理、REQUIRED传播、MyBatis生成键、InnoDB事务及间隙锁。
- 入口：`QuestionAdminService.createQuestion`、Question/ScoringPoint Mapper；临时 `backups/E1E3_20261113/QuestionImport.java` 的 `rehearseRollback/importQuestions/guardEmptyForWrite`。
- 核心问题：为何HTTP的@Valid不能覆盖直接Service调用？外层TransactionTemplate如何让21次Service调用原子提交？为什么回滚会留下自增间隙，独立连接验证比同事务SELECT更有说服力？
- 独立练习：解释20/93后权重拒绝的异常传播，证明无REQUIRES_NEW或吞异常提交；仅在另外明确授权的隔离环境复现，勿重跑当前非空导入。
- 状态：**待学习（掌握未确认）**；开发者证据未提供；待验证本人能独立定位事务边界与未知提交结果处置。复核2026-11-13／Codex。

<a id="l-e1e3-02"></a>

#### L-E1E3-02 — 冻结内容、运行时题库和MAIN快照的逐项一致性

- 关联记录/代码基线同L-E1E3-01；前置：JSON、Java泛型与type erasure、题库状态、计划分类约束。
- 入口：冻结21题JSON/MD、`InterviewQuestionPlanSnapshotService.prepareSnapshot`、`InterviewQuestionSnapshotFactory.createMainQuestion`、临时执行器 `verifyQuestionSnapshots/verifyPoints/verifySnapshot`。
- 核心问题：为何21/97计数和权重100仍不能证明答案或快照完整？如何在不创建用户/会话时验证生产快照工厂？H01为何要区分静态可允许的instanceof与运行时元素类型验证？
- 独立练习：对照一题的源内容、真实读回题库和序列化快照；按JLS21给出Object→List<?>及List<Integer>→ArrayList<Integer>的边界，形成独立修订建议。
- 状态：**待学习（掌握未确认）**；开发者证据未提供；H01修订尚未应用，不能将AI导入通过视为内容缺陷或学习验收完成。复核2026-11-13／Codex。

<a id="l-e1e3-03"></a>

#### L-E1E3-03 — RAG试入库的费用门槛与双向身份核验

- 关联记录同上；前置：L-E1-01/02、字符/UTF-8字节/token、Embedding批处理、attempt fencing与补偿。
- 入口：`KnowledgeTextChunker.split`、`KnowledgeDocumentAdminService.uploadDocument`、`KnowledgeDocumentProcessingService.processDocument`、事务服务 `persistChunksAndMarkReady`、`MilvusVectorStoreClient`；本地 `rag-pilot-manifest.json`。
- 核心问题：为何2块、3188字节不等于实际tokens？为什么READY与向量计数相等还需对照document/chunk/vector/model/profile？MySQL提交不明时为何不能盲目删除向量或重复process？
- 独立练习：复算1次2输入的预算，列出授权前检查和1文档/2块/2向量对账字段，解释何时暂停而非重试；不自行触发收费调用。
- 状态：**待学习（掌握未确认）**；开发者证据未提供；实际tokens、费用、收费恢复验收未执行。复核2026-11-13／Codex。

2026-11-13 E3-E01更新：新增1条，累计78条（不含模板占位行）。单次收费调用已授权，但因真实配置未迁移而BLOCKED，0请求；不将授权、静态审查或AI说明当作工程执行及本人掌握证据。

<a id="l-e3e01-01"></a>

#### L-E3E01-01 — 本地占位配置与真实单请求计费门禁

- 记录：[E3-E01](IMPROVEMENT_LEDGER.md#e3-e01)、[执行报告](../verification/E3_E01_REAL_INGESTION_20261113.md)；HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`，本批无新提交。
- 前置：L-E1E3-03、Spring属性来源、HTTP请求重发、秘密管理、服务地域计价。
- 入口：`application-evaluation.yaml`、`EnvironmentIsolationInitializer`、`EmbeddingConfiguration.createNoRetryTemplate/applyEmbeddingTimeouts`、`SpringAiEmbeddingClient`，JDK21 HttpClient/MultiExchange；本轮无新业务实现。
- 核心问题：为什么已有.env及非空Key不代表真实模型可用？为什么不能向占位loopback发送凭据？Spring的一次尝试和底层HTTP一次exchange有什么区别？为什么未收到usage时不能填写实际tokens、分摊到Chunk或声称账单已扣费？
- 独立练习：只输出非秘密的配置来源/目标/model/profile，辨别占位值；解释地域绑定及单请求上限，区分预算、usage计算价和供应商账单。真实请求仍遵循既定授权和前置核验，不以练习名义追加。
- 状态：**待学习（掌握未确认）**；开发者讲解、复现或独立修改证据未提供；重发保护运行时验证及真实服务入库尚未执行。复核2026-11-13／Codex。

L-E3E01-01后续证据（2026-11-14）：真实配置已补齐，禁重试与一次请求门禁已用于E01成功入库，见[本次报告](../verification/E3_E01_REAL_INGESTION_20261114.md)。有应用层1次执行及HTTP 200证据，无独立抓包；没有故障重发实验。本人掌握状态仍待学习。L-E1E3-03现可引用实际请求级821 tokens及按usage计算的¥0.0004105，但账单与收费故障恢复仍未验证。

2026-11-14 E3-E01真实入库更新：新增以下1条，累计79条；无开发者学习验收，不升级旧条目状态。

<a id="l-e3e01-02"></a>

#### L-E3E01-02 — 真实向量逐条对账与请求级费用证据

- 关联：[工程记录](IMPROVEMENT_LEDGER.md#e3-e01-live)、[E01验收](../verification/E3_E01_REAL_INGESTION_20261114.md)；HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`，本批无新提交。
- 前置：L-E1E3-01/03、L-E3E01-01、Spring事务代理、CAS领取、Milvus一致性级别、Float32和摘要。
- 入口／调用链：`KnowledgeDocumentAdminService.uploadDocument/processDocument → KnowledgeDocumentProcessingService → KnowledgeDocumentProcessingTransactionService.persistChunksAndMarkReady`；`SpringAiEmbeddingClient`、`MilvusVectorStoreClient`。本地临时 `backups/E3_E01_LIVE_20261113/E01Verifier.java` 读取真实MySQL及STRONG Milvus，比较vectorId集合、documentId/chunkIndex/profile、版本关系、完整内容及向量逐值。
- 核心问题：为何总数相等不足以证明一致？为什么需要对全体ID双向核对及非目标数据前后比较？为什么复用向量的COSINE自检不等于独立语义检索？请求级usage、Chunk token_count、公开价格计算和实际账单有何区别？成功入库为何不能证明补偿或旧备份包含新向量？
- 独立练习：用脱敏证据复核doc1、chunk1/2及两vectorId映射，独立复算 `821×0.50/1,000,000`，解释completion_tokens=null和两个Chunk token_count=NULL；只读材料练习不追加模型调用。讲解短事务提交权与跨库失败时定向补偿边界，列出仍未验证场景。
- 工程证据：真实1请求/2Chunk/2向量，逐条对账PASS；原两库、evaluation其余11表和非目标集合保全PASS；未运行JUnit/全量回归，未做故障实验或账单核验。
- 掌握状态：**待学习（掌握未确认）**。开发者掌握证据：未提供；尚待本人独立讲解、复算和失败边界分析。最近复核：2026-11-14／Codex；新增状态，不因AI执行通过升级。

## 3. 推荐阅读顺序与现有材料

1. L-J1-01～03 建立模型／Java／持久状态边界，再读 L-B1-01～04 的幂等与并发。
2. L-B2-01～03 追领取后异常与短事务，再读 L-B3-01～04 的 FINAL 来源、汇总和恢复。
3. L-B4A-01～04 明确完整调用与风险范围；按 L-B4B-01～03、L-B4C-01～03 阅读安全边界、分类和实际 HTTP 等待。
4. L-X-01～03 用原报告／Git／XML 校准证据口径。随后按L-B4D-01～07及验收报告10～15分钟导航读实际Milvus修复。旧并发/观测候选仅预学习，其余候选等验收范围明确后再排顺序。
5. B4-E之后阅读L-B5-01～06：先校准准入证据，再对照B5-P2-A密码修复及B5-P2-B已完成的Embedding日志边界，最后按真实测试计划练习恢复、质量与FINAL核对。真实测试通过和开发者掌握仍分别记账。
6. 用L-C6-01～03完成累计验收练习：独立复算证据、串联失败／恢复／提交权、说明精确提交边界与真实准入条件。先阅读和推演，不默认启动付费测试、操作数据库或提交代码。

原 [J1～B3 学习指南](../verification/J1_B3_SOURCE_LEARNING_GUIDE_20261111.md)、各批验收报告末尾学习路径、[C3 学习指南](../verification/C3_B4A_B4C_CLOSEOUT_20261112.md)是教材入口，不是学习完成证明。“10～15 分钟”是历史建议阅读时长，不是掌握时长承诺。

## 4. 每批新增学习条目模板

每次更新工程台账时，将涉及的新机制、关键取舍与测试方法拆成条目；关联批次 ID，避免一个“已完成”勾选覆盖整套源码。跨批共通知识可复用已有条目，但新增行为应补核心问题和证据。

```markdown
#### L-<批次>-<序号> — <具体技术点>

- 关联工程记录／验收报告／代码 SHA：
- 前置知识（可引用其他学习 ID）：
- 源码入口（实际路径、类/方法、调用者、SQL/测试；未实现写待确认）：
- 核心问题（为什么需要、怎样工作、失败边界、替代方案）：
- 独立练习／讲解验收目标：
- 掌握状态：待学习（未确认）。
- 开发者掌握证据：未提供；以后填写本人产出、日期、材料链接、覆盖范围。
- 尚未理解／待验证点：
- 最近复核日期／人：
- 状态变更：<日期；旧→新；证据；剩余问题>
```

更新自检：未从 AI 实现／测试结果推断掌握；前置知识和源码入口可定位；核心问题能独立回答；候选范围和实际能力分开；状态升级有开发者证据；与工程台账的批次和数量一致。

本次维护记录：2026-11-13 初始化 36 条；未开展开发者学习验收，未把任何条目标记为已掌握。

后续维护记录：2026-11-13新增B4-D七条；旧两条候选保留并迁移编号；当前43条。新增学习状态全部为待学习，未收到本人掌握证据。

后续维护记录：2026-11-13 B4-E将1条占位拆为7条，49条；B5-0再将1条占位拆为6条，当前54条。B5-0仅审查、测试与文档；所有条目仍待学习，未收到本人讲解、复现或独立修改证据。

后续维护记录：2026-11-13 B5-P2-A新增3条，共57条；密码修复已有工程证据，全部学习条目仍待学习，没有以测试通过替代开发者本人掌握验收。

后续维护记录：2026-11-13 B5-P2-B新增3条，共60条，全部待学习。工程日志脱敏与恢复回归已有证据；未收到开发者本人讲解、复现或独立修改材料。

后续维护记录：2026-11-13 C6新增L-C6-01～03，共63条；累计基线经证据复核给出CONDITIONAL_GO，但本轮未运行真实服务测试，亦未收到开发者本人学习验收材料，所有条目继续掌握未确认。


2026-11-14 D1 更新：新增 L-D1-01～04，累计 **83 条**（不含模板占位行），全部保持待学习、掌握未确认。题库审核和真实验收不替代开发者本人证据。

### D1 — 版本化题库与受控导入

<a id="l-d1-01"></a>

#### L-D1-01 — Java 版本条件与评分口径

- 关联工程记录／版本：[D1 工程记录](IMPROVEMENT_LEDGER.md#d1)、[V2 题库](../question-bank/D1_QUESTION_BANK_V2_20261114.json)。源码基线 `0c6f92afe331954f7cea37d33db688fe781cbf2c`，D1 无新提交。
- 前置：泛型擦除、可具体化类型、Java 21 `instanceof` 转换，L-D0-03。
- 源码与材料：原 V1 的 H01 题、V2 的 H01 及 SP4，`tools/question-bank-d1/revise_h01.py`，评分点模型与 `EvaluationScoreCalculator`。 [JLS 21 §15.20.2](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.20.2)
- 核心问题：为什么 `Object instanceof List<String>` 的限制不能扩展到所有操作数？为什么已知泛型上下文可接受某些窄化测试，但运行时并不逐元素确认？内容覆盖权重与后端五维评分总分各表达什么？
- 独立练习：自行写两个合法和一个非法片段，用 Java 21 `javac` 说明结果；用另一种口述方式解释例外，并独立给出两个可检验评分点。
- 掌握状态：**待学习（掌握未确认）**。开发者证据未提供；待本人独立讲解及复现。最近复核 2026-11-14／Codex，首次登记。

<a id="l-d1-02"></a>

#### L-D1-02 — 逻辑题号、内容指纹与来源版本

- 关联工程记录／版本：同 L-D1-01。[版本与来源清单](../question-bank/D1_BANK_MANIFEST_20261114.json)
- 前置：JSON、SHA-256、主键、来源版本与许可，L-D0-01/03。
- 源码入口：`tools/question-bank-d1/build_bank.py` 的 `canonical/request/build`，`D1QuestionBankImport.readBank/checkReview`，冻结 V1、V2 JSON 和逐题审核记录。
- 核心问题：题号、knowledgePoint、题目内容指纹和数据库自增 ID 为什么要分别维护？H01 改答案后哪个标识变化？为什么通过 DTO 和权重校验仍需逐题审查，导入器如何确保写入的是已审字节？
- 独立练习：任选一个新增题和 H01，复算指纹并从 V1/V2 对照追到资料版本、审核记录、入库映射，解释调整权重及仅重排 JSON 键的影响。
- 掌握状态：**待学习（掌握未确认）**。开发者证据未提供；待本人复算和解释身份边界。最近复核 2026-11-14／Codex，首次登记。

<a id="l-d1-03"></a>

#### L-D1-03 — Service 代理、整批事务与失败恢复

- 关联工程记录／版本：同 L-D1-01。前置：L-E1E3-01、Spring 代理、`REQUIRED`、InnoDB RR 与 MyBatis 生成键。
- 源码入口：`D1QuestionBankImport.guard/tx/rehearse/importBank/verifyFinal` → `QuestionAdminService.updateQuestion/createQuestion` → Question/ScoringPoint Mapper。定向测试 `QuestionAdminServiceTest` 与 `D1ImportOfflineTest` 分别验证 Service 逻辑及输入边界。
- 核心问题：直接调用 Service 如何补上 Controller 的 DTO 校验？外层事务如何绑定多次写入和 H01 替换？为什么回滚后业务行相同仍可能出现自增间隙？提交成功但回执写失败时怎样确认结果？
- 独立练习：画出正常提交、最后一题权重失败和提交回执丢失三个时序，列出锁定、回滚、独立读回的证据。实际演练仅在另行授权的隔离环境执行。
- 掌握状态：**待学习（掌握未确认）**。开发者证据未提供；待本人能识别事务代理和回执丢失的恢复路径。最近复核 2026-11-14／Codex，首次登记。

<a id="l-d1-04"></a>

#### L-D1-04 — 历史快照与有效评分点隔离

- 关联工程记录／版本：同 L-D1-01。前置：L-E1E3-02、L-D0-02、REPEATABLE READ、持久化快照、启停状态。
- 源码入口：`QuestionAdminService.updateQuestion`、`QuestionScoringPointMapper.selectEnabledByQuestionId`、`InterviewQuestionPlanSnapshotService.prepareSnapshot`、`InterviewQuestionSnapshotFactory.createMainQuestion`、`D1QuestionBankImport.verifyBank/verifyPreserved`。
- 核心问题：当前题库更新后，为什么历史题干、答案及评分点不能随之改变？当前题目总行数和可参与组卷的启用题数如何分别验收？H01 整体替换评分点后，为什么快照的旧点 ID 仍可用于历史重放？
- 独立练习：用脱敏读回或本地推演比较 H01 更新前后的当前题库数据与已生成快照，列出需要相等与允许变化的字段。分析某个点禁用后权重和变成 75 的入库／组卷门禁。
- 掌握状态：**待学习（掌握未确认）**。开发者证据未提供；待本人能独立指出快照与实时状态的读取边界。最近复核 2026-11-14／Codex，首次登记。


2026-11-14 D2更新：新增L-D2-01～04，累计 **87条**（不含模板），全部保持待学习、掌握未确认。真实工程验收不代表开发者本人掌握。

### D2 — 分类随机、可控测试与快照保护

<a id="l-d2-01"></a>

#### L-D2-01 — 分类分桶、均匀抽样与稳定索引

- 工程／版本：[D2台账](IMPROVEMENT_LEDGER.md#d2)、[独立验收](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-D2-随机组卷与真实数据库验收.md)。基线 `0c6f92afe331954f7cea37d33db688fe781cbf2c` 加D2未提交修改。
- 前置：List/Map、枚举、排序、主键唯一性、均匀分布，L-D0-02。
- 源码入口：`QuestionPlanSelector.select/groupCandidates/validateCandidate`，`InterviewPlanConfig.getRule`，`QuestionMapper.xml#selectEnabledQuestionsForInterview`；测试 `shouldGiveEachUniqueCandidateOneReachableIndex`、`shouldReplayTheSamePlanWithFixedSeedEvenIfCandidateOrderChanges`。
- 核心问题：按ID排序为何不会恢复首题偏差？重复行如何改变概率？为什么每类一个随机下标能保证覆盖且无需补题？难度由哪一层约束？同一固定随机序列遇到反转输入为何仍能重放？
- 独立练习：自行列出两分类各三题，包含相同重复行与禁用题，手算每个合法下标对应的唯一候选，再实现输入重排测试。解释计划总数与必选分类数不等时为何拒绝。
- 掌握状态：**待学习（掌握未确认）**。开发者证据未提供，待本人讲解与独立修改；尚待验证公平性和错误边界的独立解释。最近复核2026-11-14／Codex，首次登记。

<a id="l-d2-02"></a>

#### L-D2-02 — 线程本地随机源与确定性测试

- 工程／版本：同L-D2-01。前置：函数式接口、方法引用、Spring单例、线程安全、固定种子、CyclicBarrier。
- 源码入口：`QuestionPlanSelector.select`公开入口与包级随机源重载；`QuestionPlanSelectorTest.shouldKeepConcurrentDrawsAndTheirResultsIndependent`、`shouldBuildValidPlansWhenProductionRandomSourceIsUsedConcurrently`、`fixedSeedSampleShouldShowNoObviousFirstCandidateBias`。
- 核心问题：为什么不能把测试的可变随机序列放进共享生产Bean？ThreadLocalRandom在何时取得、何线程使用？固定seed、逐下标穷举、真实小样本各证明什么？HARD/MYSQL观察到1/0/7为何不能直接判定算法不公平？
- 独立练习：用两个独立固定源并发抽取首/末候选，解释局部桶为何不相互污染；改一个候选顺序重放；设计不依赖严格随机频率的失败断言。
- 掌握状态：**待学习（掌握未确认）**。开发者证据未提供，待本人复现并解释测试边界；最近复核2026-11-14／Codex，首次登记。

<a id="l-d2-03"></a>

#### L-D2-03 — 随机计划、活动会话幂等与历史快照

- 工程／版本：同L-D2-01。前置：L-D1-04、REPEATABLE_READ、Spring REQUIRED、用户行锁、乐观CAS与JSON序列化。
- 源码入口：未改 `InterviewSessionService.createSession → InterviewQuestionPlanSnapshotService.prepareSnapshot → InterviewSessionTransactionService.createAndStartSession → InterviewQuestionSnapshotFactory.createMainQuestion`；工具 `create/testHistoricalSnapshot`，Mapper `startSession/batchInsertMainQuestions/listBySessionId`。
- 核心问题：新建场次需要随机，活动Session重复请求为何必须返回原场次？用户行锁后的第二次检查解决什么竞态？管理员更新评分点主键后，历史快照为什么保留旧id和顺序？planOrder与displayOrder为什么不同？
- 独立练习：绘制正常创建、同用户竞争、管理员更新三种时序；追踪题干/答案/点JSON的读写位置。仅在后续明确授权的隔离环境自行复现非空历史快照保护。
- 掌握状态：**待学习（掌握未确认）**。开发者证据未提供；尚待本人独立区分内存草稿、事务内落库及提交后持久事实。最近复核2026-11-14／Codex，首次登记。

<a id="l-d2-04"></a>

#### L-D2-04 — 真实验收的目标门禁与可回滚证据

- 工程／版本：同L-D2-01。前置：L-D1-03、逻辑备份、摘要、MySQL自增、事务可见性与独立连接。
- 源码入口：`tools/question-plan-d2/run_acceptance.py` 的 `check_identity/backup/verify`；`D2QuestionPlanAcceptance.verifyBackup/verifyBank/readRows/rollback/concurrent`。证据：D2 `write-plan.json`、`writes.jsonl`、`result.json`、`protection-result.json`。
- 核心问题：为什么库名正确还要核对host/端口/UUID/触发器？为什么写前登记可能产生的行，生成后再记录具体ID？回滚后业务行相等为何允许自增号不同？29个fixture检查与最后总体检查如何计数？外围事务为何使本验收不能证明正常独立commit可见性？
- 独立练习：不连数据库复现四种失败门禁，区分预期exit1与测试失败；从已留证据追出一个Session的计划、生成行和最终零残留结论，并列出本轮未验证的事项。
- 掌握状态：**待学习（掌握未确认）**。开发者证据未提供；待本人能制定精确恢复方案并如实区分Mock、真实SQL、回滚与commit证据。最近复核2026-11-14／Codex，首次登记。


### D3-A — 评分证据、切片与独立检索评测

关联 [D3-A 工程记录](IMPROVEMENT_LEDGER.md#d3-a)、[覆盖工作簿](../rag/d3-a/D3A_KNOWLEDGE_COVERAGE_20261114.xlsx) 和 [评测规程](../verification/D3A_RETRIEVAL_PROTOCOL_V1_20261114.md)。工作区基于 HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`，本批无提交。以下练习在本地阅读和离线推演范围内。

#### L-D3A-01 — 技术事实、版本与评分覆盖

- 前置及入口：D1 V2 评分要求、旧 H01/M02、工作簿的「评分点矩阵」和「来源索引」，以及 `InterviewQuestionSnapshotFactory.createMainQuestion` 的评分点冻结。
- 核心问题：为什么已定位到权威来源仍可能有正文缺口？何时要把规范、实现、工程建议或项目行为分开？同一类型擦除的例子需要哪些静态与运行时前提？
- 独立练习：为 H01.02 和 M02.04 分别写一条可核查事实、一个反例和一个可接受作答，定位 JLS 条款或固定 8u 源码分支。用原题核对边界，保留来源和理由。
- 掌握状态：**待学习（掌握未确认）**。待本人给出核对与反例证据。最近复核 2026-11-14／Codex。

#### L-D3A-02 — 生产切片与文档版本保全

- 前置及入口：`KnowledgeDocumentAdminService.uploadDocument → KnowledgeTextChunker.split/findNaturalBoundary/calculateNextStart → KnowledgeDocumentProcessingService.processDocument → KnowledgeDocumentProcessingTransactionService.persistChunksAndMarkReady`。
- 核心问题：UTF-16 单元、UTF-8 字节和 token 有何不同？为什么引用尾块、前提丢失或 READY 文档改正文都会影响证据？遇到提交确认不明时怎样避免误删有效向量？
- 独立练习：本地用 E01 原文计算规范化 hash 和两个 Chunk 摘要，另用一段含 CRLF 与非 BMP 字符的合成文档检查边界。画出上传、领取、外部写入、MySQL 提交及补偿顺序。
- 掌握状态：**待学习（掌握未确认）**。待本人复现摘要并解释故障窗口。最近复核 2026-11-14／Codex。

#### L-D3A-03 — 原始排名、资格与 Prompt

- 前置及入口：`EvaluationRagQueryBuilder.build`、`KnowledgeRetrievalService.retrieve`、`KnowledgeChunkMapper.selectRetrievableByVectorIds`、`EvaluationPromptBuilder.build` 和 `RagTracePersistenceService.persist`。
- 核心问题：原始 Top5 只剩 rank 2、4 时，为什么 rank 2 不应在主口径改记 rank 1？MySQL 的 ACTIVE/READY、版本、模型和 profile 如何决定资格？哪些证据会进入 Prompt 与持久化追踪？
- 独立练习：用手工合成的 5 条命中，标记两条资格丢失、两条直接证据和一条相邻材料。计算三阶段 Hit@1/3/5、Precision@5、MRR@5 与最终相关率，说明空证据的分母。
- 掌握状态：**待学习（掌握未确认）**。待本人独立算出指标并定位资格过滤与序列化源码。最近复核 2026-11-14／Codex。

#### L-D3A-04 — 冻结独立查询与人工真值

- 前置及入口：[评测规程](../verification/D3A_RETRIEVAL_PROTOCOL_V1_20261114.md)、`KnowledgeTextChunker.split`、`tools/rag-p1/IndependentRetrieval.java` 与 [历史留出集记录](../rag/PHASE_4C2_HOLDOUT_FREEZE_REPORT.md)。
- 核心问题：题库回放能验证什么？共享概念与题干泄漏如何区别？为什么正例没 DIRECT 仍计 miss，负例有背景却无法提供所请求的实例事实？覆盖同一个子意图两次如何计数？
- 独立练习：自行写两条全新查询和一个负例，为六段自备证据独立标注并记录争议。生成查询与标注摘要，模拟收到 Top5 后分析失败原因，保留首次结果并设计另一组验证样本。
- 掌握状态：**待学习（掌握未确认）**。待本人提供可重算的小样本和裁决理由。最近复核 2026-11-14／Codex。

### D3-B/C/D 源码学习（2026-11-14）

#### L-D3BCD-01 — 生产切片与向量入库身份

- 入口：`KnowledgeTextChunker.split`、`KnowledgeDocumentProcessingService.processDocument`、`persistChunksAndMarkReady` 与 `D3Inventory.inventory`。
- 独立练习：解释原文件摘要与规范化 Chunk 摘要各防哪种错误。用两条合成向量记录说明 READY 和 ACTIVE 还需怎样的跨库身份核对，以及结果未知时应读哪些字段。
- 掌握状态：**待学习（掌握未确认）**。

#### L-D3BCD-02 — 首位命中、Prompt 相关率与独立集

- 入口：`EvaluationRetrievalAdapter.retrieve`、`EvaluationPromptBuilder.build` 与 [补充规程](../rag/d3-d/PROTOCOL_SUPPLEMENT_20261114.md)。
- 独立练习：重算一次命中靠前、其余内容无直接支持的 A/B/C 结果，解释负例为什么单列。用一条语料缺口和一条原始召回失败说明后续修复的不同证据。
- 掌握状态：**待学习（掌握未确认）**。

#### L-D3BCD-03 — 付费尝试与业务请求恢复

- 入口：`DeepSeekEvaluationClient`、`InterviewService.submitAnswer`、`D3SmokeServer.UsageResponse` 与 EASY 提交后读回。
- 独立练习：复现响应流只能读一次造成的包装器故障，证明修复后能保留响应内容并只记一次 usage。解释相同业务请求键仍可能产生新的上游计费，以及如何用 FINAL 核对报告。
- 掌握状态：**待学习（掌握未确认）**。

### D3-E 源码与评测学习（2026-11-14）

<a id="l-d3e-01"></a>

#### L-D3E-01 — 候选、真实证据和精度分母

- 入口：`EvaluationRagQueryBuilder`、`KnowledgeRetrievalService.retrieve`、`EvaluationRetrievalAdapter`、`EvaluationPromptBuilder` 和 `tools/rag-d3/d3e_replay.py`。
- 独立练习：对原始 rank 1、2、4 合成一次资格过滤与去重，分别计算原始 Hit@1/3/5、固定 P@5、最终 micro/macro 精度，解释空证据及负例的分母；指出追踪表如何对应实际 Prompt。
- 掌握状态：**待学习（掌握未确认）**，等待本人演算与源码定位证据。

#### L-D3E-02 — 机制覆盖、误拒绝与有界选择

- 入口：[D3-E 报告](../verification/D3E_EVIDENCE_QUALITY_REVIEW_20261114.md)、版本化回放和公开 Q26/Q29/Q33。
- 独立练习：解释为什么首位命中和高精度不能保证所有子意图覆盖；对比 0.70 阈值的 CORE 丢失与无答案误供，设计不读取标签或题号的下一轮测试，并区分语料缺口与召回 miss。
- 掌握状态：**待学习（掌握未确认）**，等待本人解释和独立复核证据。

#### L-D3E-03 — 评分盲审与外部调用记账

- 入口：`D3EScoringProbe`、`D3SmokeServer.UsageResponse`、`LlmEvaluationValidator` 和人工盲审卡。
- 独立练习：解释固定题答的 A/C 对照为什么不能证明因果显著；分别核对 E01.03、H25.03 的预期歧义。描述一次 HTTP 失败、业务重放与供应商计费计数的不同边界，并验证错误体和重复读取不会丢失响应。
- 掌握状态：**待学习（掌握未确认）**，等待本人独立判定与复现证据。

### U1 题目表达、评价边界与可见等待（2026-11-14）

<a id="l-u1-01"></a>

#### L-U1-01 — 稳定知识点键和历史评分点快照

- 入口：`QuestionAdminService.updateQuestion → InterviewQuestionSnapshotFactory → EvaluationContextService`。
- 独立练习：从两题的新旧 DTO 识别哪些值可改，解释评分点替换为何分配新 ID，如何确认旧会话仍用旧评分点和报告。区分当前题库内容与客户端知识点显示文字。
- 掌握状态：**待学习（掌握未确认）**。

#### L-U1-02 — 评价必答边界与固定证据对照

- 入口：`EvaluationPromptBuilder`、`LlmEvaluationValidator`、[U1 合成样本](../verification/U1_SYNTHETIC_SCORING_FIXTURES_20261114.json)。
- 独立练习：为 ACID 的一种口语解释和一种错误解释分别写出 Boolean 覆盖及部分反馈，说明为何不应拿参考答案中的隔离级别举例当作必答。比较同一答案在两组方案中的覆盖分歧，锁定判断后再解盲。
- 掌握状态：**待学习（掌握未确认）**。

#### L-U1-03 — 同步答题、报告和浏览器等待计时

- 入口：`InterviewTimingConfiguration`、`InterviewRequestTiming`、`InterviewService`、`InterviewReportService`、`useInterviewSession`。
- 独立练习：通过安全关联头把一条浏览器时间线匹配到后端阶段，解释为何 MVC 总时间和用户点击到结果的时间不同，为什么超时不等价于服务端失败，记录同键恢复作用。
- 掌握状态：**待学习（掌握未确认）**。

### U1-R1 evaluation 账号边界与证据追溯（2026-11-14）

<a id="l-u1r1-01"></a>

#### L-U1R1-01 — 专用数据库配置与权限验证

- 入口：`tools/rag-d3/config.py → evaluation_database_config`、`run.py → evaluation_environment`、`EnvironmentIsolationInitializer`。
- 前置知识：文件属主与 0700/0600、JDBC 目标、MySQL 账号的主机和 Schema 授权。
- 独立练习：解释为什么字面配置不能当 shell 执行；使用只读连接核对 Schema 与服务器身份，区分配置路径正确、连接成功和账号仅具备预期权限；设计只覆盖 evaluation 的 DML 权限。
- 证据和状态：本轮仅有离线门禁测试，evaluation 真实连接因账号权限阻塞；开发者本人讲解、复现或修改证据未提供。**待学习（掌握未确认）**。

#### L-U1R1-02 — RAG 历史证据与浏览器验收范围

- 入口：`RagTracePersistenceService`、`rag_hit_log`、`U1EvaluationQuestions.history`、`InterviewRequestTiming` 和 U1 浏览器验收入口。
- 前置知识：历史快照与当前引用、随机抽题、请求关联及样本统计。
- 独立练习：说明为何仅凭当前 Chunk 不一定能证明历史提示词正文；为双目标随机组卷列出可检查的会话条件，分别设计重复点击、失败恢复和阶段耗时证据，并解释样本不足时为何不能给 P95。
- 证据和状态：本轮仅完成代码和测试边界检查，未执行真实会话或记录耗时；开发者本人证据未提供。**待学习（掌握未确认）**。

2026-11-14 U1-R1 后续证据：专用账号已创建并通过精确授权及两个其他 Schema 的拒绝访问检查；人工测试会话 的数据库归属、题答及两道题的历史快照已只读核验，题库更新经正式 Service 提交并回读。复核了 MySQL Schema 级授权下划线通配符的转义要求。上述为工程代理证据，L-U1R1-01～02 仍为**待学习（掌握未确认）**；开发者本人讲解或复现尚未提供，真实浏览器耗时与完整流程仍待验收。

同日补充：真实 HTTP 未登录边界及合成账号 API 登录已核验；浏览器工具未能使用脱敏后的凭据完成 GUI 登录，完整浏览器与计时仍未验收。学习状态不变。

同日最终证据：普通合成用户 Session 31 的真实 Chrome/HTTP 评分、追问、报告及与数据库的答案逐项比对已完成；同页响应丢失后原请求恢复通过。跨服务及浏览器重启并重新登录的组合场景发现上一题反馈未自动恢复，详见 [U1-R1 最终报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U1-R1-真实浏览器终验.md)。这些为工程代理验证证据，L-U1R1-01～02 的学习状态仍为**待学习（掌握未确认）**。

<a id="l-u1r1-03"></a>

#### L-U1R1-03 — 结果未知、同键恢复与跨重启状态

- 入口：`useInterviewSession.handleSubmitFailure/postAnswer/applyBootstrapResult`、`submitRecordStorage`、`sessionBootstrap.resolveSessionBootstrap`、`InterviewService.submitAnswer`。
- 独立练习：分别画出服务端未提交、已提交但响应丢失、已推进后浏览器与服务重启三条时序；说明同一请求 ID 和答案为什么能取得已保存评价、何时仍可能触发模型收费。解释本轮 8 次 HTTP 正文、6 个唯一答案与 6 次模型评分之间的关系，提出身份切换和最终题目的反例。
- 掌握状态：**待学习（掌握未确认）**；尚无开发者本人讲解、复现或独立修改证据。

#### L-U1R1-04 — 样本配对、真实耗时与用量

- 入口：`InterviewRequestTiming`、`EvaluationRetrievalAdapter`、`KnowledgeRetrievalService.retrieve`、`InterviewReportService`，以及本轮私有浏览器网络与后端计时记录。
- 独立练习：将一条实际请求关联到浏览器可见时间和各后端阶段；解释为什么 4 条正常端到端、6 条服务端评分和 1 条报告不能混用分母，也不能提供可靠 P95。用缓存命中、未命中和输出 token 独立复算使用量估价，并区分估算与供应商账单。
- 掌握状态：**待学习（掌握未确认）**；尚无开发者本人复算或解释证据。

### U2 反馈恢复与启动安全（2026-11-14）

<a id="l-u2-01"></a>

#### L-U2-01 — 提交结果未知与只读反馈恢复

- 入口：`InterviewWorkflowTransactionService`、`InterviewService.getDetail`、`useInterviewSession.recordForBootstrap`、`PreviousInterviewFeedback.vue`。
- 独立练习：画出答案已提交、响应丢失、当前题已推进和全新浏览器登录的状态；解释 localStorage 的旧题键为什么不能靠当前题 ID 命中。使用已有详情结果推导 INITIAL 主答与 FINAL 追问各应显示哪个评价，说明为何本次查看不应调用模型或回退 Session。
- 掌握状态：**待学习（掌握未确认）**；工程代理已复现并修复，尚无开发者本人讲解或独立复现证据。

#### L-U2-02 — Boot 默认用户与 JWT 安全链

- 入口：`AiInterviewSystemApplication`、`SecurityConfiguration.securityFilterChain`、`DefaultSecurityUserDisabledIntegrationTest` 及六个 `@WebMvcTest` 安全切片。
- 独立练习：解释自定义 SecurityFilterChain 为何不自动等于禁止默认 UserDetailsService，定位自动配置排除条件；说明 401、普通用户 403、注册登录与当前用户角色查询各自验证什么，为什么隐藏日志不能证明默认用户不存在。
- 掌握状态：**待学习（掌握未确认）**；尚无开发者本人解释或独立修改证据。

#### L-U2-03 — 故障注入、跨进程读回与计费边界

- 入口：`FullInterviewBusinessOfflineTest.readsCommittedPreviousEvaluationWithoutResubmissionAndRejectsAnotherUser`、U2 私有浏览器与数据库读回证据。
- 独立练习：区分 Mock 事务测试、服务端已提交后截断响应、浏览器刷新、前后端重启及全新用户目录分别证明的内容；核对 5 次答案 POST、5 条持久化答案/评价、6 次 DeepSeek（含报告）与 5 次 Embedding，解释为何两次只读恢复不计为模型调用。
- 掌握状态：**待学习（掌握未确认）**；尚无开发者本人复核或演算证据。

### S1 候选人数据可见性（2026-11-14）

<a id="l-s1-01"></a>

#### L-S1-01 — 持久化计划与接口可见边界

- 入口：`InterviewQuestionPlanSnapshotService`、`InterviewService.getDetail`、`InterviewDetailQuestionResponse`。
- 独立练习：画出创建时完整快照、当前题、已提交历史和未来题四者关系；解释为何界面隐藏不足以阻止直接 HTTP 请求，以及为什么已作答但评价尚未完成时仍可查看本人答案。
- 掌握状态：**待学习（掌握未确认）**；未收到开发者本人讲解或复现证据。

#### L-S1-02 — JWT、归属校验与反馈恢复

- 入口：`JwtAuthenticationFilter`、`InterviewService.loadOwnedSession`、`PreviousInterviewFeedback.vue`、`InterviewVisibilityHttpIntegrationTest`。
- 独立练习：区分无令牌 401、普通用户管理接口 403、跨用户详情 404；解释按当前题和展示顺序恢复主答或追问评价时，身份或 Session 变化如何避免串读。
- 掌握状态：**待学习（掌握未确认）**；未收到开发者本人讲解或复现证据。

#### L-S1-03 — 隔离回归与时间语义

- 入口：`EnvironmentIsolationInitializer`、超时回收 CAS 测试、`InterviewVisibilityHttpIntegrationTest`。
- 独立练习：对比 MySQL 服务器时区、JDBC 配置和 JVM 本地时间对过期判断的影响；设计只连接临时测试库的真实 HTTP 与 Mock 模型验证，分别报告失败、重测、跳过和真实外部服务范围。
- 掌握状态：**待学习（掌握未确认）**；未收到开发者本人讲解或复现证据。

### S1-R1 Evaluation 部署与终验（2026-11-14）

<a id="l-s1r1-01"></a>

#### L-S1R1-01 — 构建身份与安全切换

- 入口：`git archive`、Maven package、`InterviewService.getDetail`、`EnvironmentIsolationInitializer`、evaluation 配置。
- 独立练习：说明源码 SHA、JAR SHA、运行 PID、端口和字节码检查分别证明什么；画出预验、确认空闲、只停止已归属旧进程、启动失败不恢复旧漏洞服务的顺序。
- 掌握状态：**待学习（掌握未确认）**；尚无开发者本人解释或独立复现证据。

#### L-S1R1-02 — 真实 HTTP、浏览器与快照边界

- 入口：`InterviewController`、`InterviewService.getDetail`、`InterviewQuestionSnapshotFactory`、`PreviousInterviewFeedback.vue`。
- 独立练习：区分页面显示与前端代理实际 JSON；逐步解释 401、403、404、当前题、未作答题、历史反馈和刷新重新登录测试各自证明的边界。解释为什么一次组卷与 5 道快照对账不能证明随机分布。
- 掌握状态：**待学习（掌握未确认）**；尚无开发者本人解释或独立复现证据。

#### L-S1R1-03 — 行摘要、真实模型阶段和用量证据

- 入口：evaluation 只读快照对账、`InterviewRequestTiming`、`EvaluationRetrievalAdapter`、`JudgeProvider`。
- 独立练习：说明既有行逐行摘要和新增合成数据如何分别核验；区分一次应用阶段、供应商 HTTP 尝试次数、usage 与实际账单，并解释未采集 usage 时为何不能计算费用。
- 掌握状态：**待学习（掌握未确认）**；尚无开发者本人解释或独立复算证据。

### Q1 真实评分、RAG 对照与成本边界（2026-11-14）

<a id="l-q1-01"></a>

#### L-Q1-01 — 评分点证据与反馈一致性

- 入口：`EvaluationPromptBuilder`、`LlmEvaluationValidator`、`EvaluationScoreCalculator`、`FollowUpPolicy`，以及 Q1 的 M05、A02、H01 合成诊断。
- 独立练习：区分完整覆盖、部分覆盖、事实错误及未作答；逐一指出 `strengths`、`missingPoints`、`correction` 与原答之间的证据关系。说明 Java 结构校验及五维求和为何不能保证技术判断正确。
- 掌握状态：**待学习（掌握未确认）**；未收到开发者本人独立审核或讲解证据。

#### L-Q1-02 — 配对消融与盲审

- 入口：Q1 固定样本、`SCORING_RESULTS.json`、两份盲卡及历史检索 Prompt。
- 独立练习：先独立盲审再解盲，分析四组配对的覆盖与事实差异；解释一次输出、历史证据、同源模型和未固定人工 gold 如何限制因果与泛化结论。
- 掌握状态：**待学习（掌握未确认）**；未收到开发者本人解释或复核证据。

#### L-Q1-03 — 未知请求与三类验证

- 入口：`tools/q1/` 执行门禁和私有逐次 usage 账本、只读行摘要与 HTTP 读回记录。
- 独立练习：核对 62 次 DeepSeek 尝试中 61 份 usage 与 1 次结果未知，解释为何不能把未知当零费用；区分结构合法、业务完成、评分准确以及历史数据未改变各自需要的证据。
- 掌握状态：**待学习（掌握未确认）**；未收到开发者本人复算或复现证据。

### Q1-R1 错误归因与盲审（2026-11-14）

<a id="l-q1r1-01"></a>

#### L-Q1R1-01 — 外连接反例与回答归因

- 入口：Q1-R1 只读 SQL 反例、历史题目快照、`EvaluationPromptBuilder`、Q1 业务评价。
- 独立练习：手推无订单、只有未支付、仅已支付和混合订单时 ON 与 WHERE OR 的行集；分开列候选人真正说过、模型认可和模型错误归因的内容。说明为什么四个正确覆盖的评分点不能证明整条回答没有额外错误。
- 掌握状态：**待学习（掌握未确认）**；尚无开发者本人盲审、讲解或复现证据。

#### L-Q1R1-02 — Java 版本事实与证据来源

- 入口：接口题原主答与追问、`javac --release 8/9` 反例、JLS 8/9/21 及 Q1 的 rank 1–2 知识正文。
- 独立练习：解释接口 default/static 与 private 的版本差异，并核对 LLM 的原句、参考答案和知识正文；说明“证据没写某个年份”何时只是范围缺口，为什么不必推断知识错误或模型因果。
- 掌握状态：**待学习（掌握未确认）**；尚无开发者本人复核或讲解证据。

#### L-Q1R1-03 — 二值覆盖、盲审和回归门槛

- 入口：`LlmEvaluationValidator`、`FollowUpPolicy`、评分点 96 和 449、Q1-R1 首轮 12 张盲卡及 Q2 草案。
- 独立练习：比较部分覆盖、未答、事实错误与二值 `covered`；说明 M05 的模型建议与 Java 实际决策不同，提出不泄露系统结论的盲审与未见样本方案，并说明一次模型通过为何不足以证明永久修复。
- 掌握状态：**待学习（掌握未确认）**；尚无开发者本人讲解、盲审或独立修改证据。

### Q1-R2 总体盲评解盲（2026-11-14）

#### L-Q1R2-01 — 名称、机制与人工金标准

- 入口：Q1-R2 卡 01、07、09 及 `UNBLIND_REPORT.md`。
- 独立练习：区分术语没有命名与机制未答、部分覆盖与错误观点；解释为什么总体评语和空白评分点不能用作逐点准确率分母。
- 状态：**待学习（掌握未确认）**；首轮已有总体评语，但尚无逐点裁决或独立讲解证据。

#### L-Q1R2-02 — 反馈误判与 Java 工作流

- 入口：Q1-R2 卡 03、06、08、11 和 `FollowUpPolicy`。
- 独立练习：区分错误认可、正确内容被写成遗漏和追问建议未被执行；对同一卡可同时存在正确评分点与错误额外观点的情况画出证据链。
- 状态：**待学习（掌握未确认）**；尚无开发者本人逐点讲解证据。

### Q2 评分候选验证（2026-11-14）

#### L-Q2-01 — 结构校验与真实评分退化

- 入口：`EvaluationPromptBuilder`、Q2 `CANDIDATE_V5.patch` 和 `COMPARISON.json`。
- 独立练习：对比 A02、A04、M05 的 v4/v5 结果，解释为何提示词合同测试通过、HTTP 200 和总分变化均不能证明评价正确；说明为何未通过的候选撤回但证据保留。
- 状态：**待学习（掌握未确认）**；未收到开发者本人独立讲解或复现实证。

#### L-Q2-02 — 独立留出与模型输出失败

- 入口：Q2 `DIAGNOSTIC.md`、H02 原始响应与 usage、Mockito agent 测试记录。
- 独立练习：区分旧开发样本与未见独立留出集；说明 HTTP 200、JSON 合法、Java 校验及人工技术评价分别证明什么；区分 Mockito 环境错误与生产业务故障。
- 状态：**待学习（掌握未确认）**；尚无开发者本人复核或解释证据。

### Q2-R1 评分契约与离线证据（2026-11-14）

#### L-Q2R1-01 — 复合评分点、子项映射与追问

- 入口：`LlmEvaluationValidator.validate`、`FollowUpPolicy.decide`、`FollowUpTargetResolver.resolve`，及 `tools/q2/q2_r1_offline_contract.py`。
- 独立练习：自行标注一个包含必答子项、可选替代方案及拓展内容的评分点，解释 ALL/ANY、PARTIAL 映射成 false 后丢失的信息，以及模型建议、CORE、总分和最多两个目标如何共同影响追问。区分历史已保存结果与未来会话快照。
- 状态：**待学习（掌握未确认）**；未收到开发者本人讲解或复现证据。

#### L-Q2R1-02 — 原答证据、技术事实与结构化输出

- 入口：`DeepSeekEvaluationClient`、`LlmEvaluationSuggestion`、Q2-R1 审计报告及离线 SQL 反例。
- 独立练习：说明原文引用存在为何不证明观点正确；分别追踪 HTTP 200、finish reason、JSON 解析、字段校验及技术事实核验。分析 Q2 第 18 次 v5 非法 JSON 为什么不是已证实的截断，并解释历史二值评价为什么不能自动回填为部分覆盖。
- 状态：**待学习（掌握未确认）**；未收到开发者本人解释或独立验证证据。

### Q2-R2 评分口径与受控对照（2026-11-14）

#### L-Q2R2-01 — 评分点澄清与产品要求

- 入口：`docs/evaluation/q2-20261114/q2-r2/CANDIDATE_RULES.json`、`QuestionAdminService.updateQuestion`、`EvaluationRagQueryBuilder`。
- 独立练习：区分名称和含义、等价替代与在增强 for 中直接结构修改；解释 552 的风险清单为什么需要出题人裁决。追踪全量替换时评分点 ID、历史快照和检索查询如何变化。
- 状态：**待学习（掌握未确认）**；无开发者本人讲解或独立验证证据。

#### L-Q2R2-02 — 样本冻结与未知费用

- 入口：`tools/q2/Q2R2Probe.java`、Q2-R2 对照总结及私有计划、usage 账本。
- 独立练习：解释留出先冻结但未独立盲审为何不能算金标准；区分预请求计数、无 HTTP 状态、未知 usage、预算预留和供应商实际账单，推导为何首次失败后停止，不能报告任何模型收益。
- 状态：**待学习（掌握未确认）**；无开发者本人复算或讲解证据。

#### L-Q2R2-03 — OR 评分口径与网络预检

- 入口：`docs/evaluation/q2-20261114/q2-r2/CANDIDATE_552_OR.json`、Q2-R2 对照总结及 `ENGINEERING_CASEBOOK.md`。
- 独立练习：解释为什么用户确认至少一种风险不等于取消解释机制及相应限制；区分相关风险、笼统声称有风险、以及只说出术语。画出 DNS 预检、受保护配置检查、上轮未知账单预留、停止付费调用和后续重新冻结对照的边界。
- 状态：**待学习（掌握未确认）**；已收到用户对产品规则的决定，尚无开发者本人关于实现、验证或账单边界的讲解或复现证据。

### M1-A 异步报告方案（2026-11-14）

<a id="l-m1a-01"></a>
#### L-M1A-01 — 普通双写、Outbox 与事务消息

- 入口：[M1-A 方案](2026-11-14-M1A-ROCKETMQ-ASYNC-REPORT-DESIGN.md)第 2～3 节、Apache RocketMQ 事务消息文档。
- 独立练习：画出 DB 提交后、MQ 发送前崩溃及 MQ 成功但回执丢失的两种时间线，解释 Outbox 同事务范围、重复发送与半消息回查的区别；说明为何不是分布式原子提交。
- 状态：**待学习（掌握未确认）**；无开发者独立讲解或复现证据。

#### L-M1A-02 — 任务领取、排队期限与 fencing

- 入口：`InterviewReportService`、`InterviewSessionMapper.xml`、`InterviewReportTransactionService` 与 M1-A 第 3 节。
- 独立练习：推演重复消息、两个消费者、长排队、超时后旧模型响应及 ACK 丢失，说明 Session version、task 状态、事务回滚与独立截止各保证什么；解释为何不能承诺 DeepSeek 恰好一次。
- 状态：**待学习（掌握未确认）**；无开发者独立讲解或复现证据。

#### L-M1A-03 — API 恢复与证据边界

- 入口：`useInterviewReport.ts`、B4-E、U1-R1 浏览器验收与 M1-A 第 4～7 节。
- 独立练习：说明 200 GENERATING、GET 轮询、刷新登录、FAILED 显式重试与旧 POST 兼容；设计真实 MQ 故障注入并区分 POST 受理时间与申请到 READY 时间。
- 状态：**待学习（掌握未确认）**；无开发者独立讲解或复现证据。

#### L-M1A-04 — 双进度、ACK 与迁移回退

- 入口：[M1-A 第 8～9 节](2026-11-14-M1A-ROCKETMQ-ASYNC-REPORT-DESIGN.md)、`InterviewReportTransactionService.completeReportGeneration`、临时探针源码。
- 独立练习：画出消息被消费后派发器才记录 SENT 的顺序，说明两个状态列及 token 为什么不倒退；推演 DB 成功但 ACK 丢失、执行超时后旧线程提交与显式新轮，说明事务、fencing 与 ACK 分别保护什么；解释升级或回退时为什么不能混跑旧同步和新异步实例。
- 状态：**待学习（掌握未确认）**；没有开发者独立讲解、复现或修改证据。一次真实 MQ 收发不代表故障恢复已验证。

### M1-B 实现与故障证据（2026-11-14）

本批新增 5 条；工程测试通过不替代本人掌握证据。关联 [M1-B 台账](IMPROVEMENT_LEDGER.md#m1-b) 和 [验收](2026-11-14-M1B-ROCKETMQ-ASYNC-REPORT-VERIFICATION.md)，源码未提交，本轮基线 SHA `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`。

<a id="l-m1b-01"></a>
#### L-M1B-01 — 同事务受理与投递租约

- 关联工程记录／验收／代码 SHA：M1-B，链接及未提交状态见本节引言。
- 前置知识：L-M1A-01、Spring 事务代理、SQL 唯一约束、条件 UPDATE。
- 源码入口：`InterviewReportTransactionService.acceptReportTask` → `InterviewReportTaskMapper.insertQueued`；`ReportOutboxDispatcher.dispatchDue` → claimDispatch/markSent/rescheduleDispatch；`ReportTaskMapperIntegrationTest`。
- 核心问题：Session 和 Outbox 为什么同事务？为什么 broker SEND_OK 不等于业务成功？晚到 markSent 为什么不能同时改 task_state？一个单体数据库线程替代 MQ 有何收益/代价？
- 独立练习／讲解验收目标：复述受理 SQL 失败整体回滚与消费先于 markSent 的实际测试，手算 token A/B 交错和 2/4/8/16/32/60 秒退避；指出测试没有覆盖的进程 kill。
- 掌握状态：**待学习（未确认）**；开发者掌握证据：未提供。
- 尚未理解／待验证点：大批积压时索引、扫描间隔和租约参数；不能把小 fixture EXPLAIN 当性能证据。
- 最近复核日期／人：2026-11-14 / Codex（源码与工程证据）；状态变更：新增待确认，无本人验收。

<a id="l-m1b-02"></a>
#### L-M1B-02 — 固定执行资格、锁序与版本溢出

- 关联工程记录／验收／代码 SHA：M1-B，未提交。
- 前置知识：L-B1-03、Session version、Java Integer/Long、MySQL UNSIGNED、行锁。
- 源码入口：`claimTaskExecution/completeReportGeneration/settleStaleOrThrow`、`ReportExecution`、`InterviewSessionMapper.xml`；真实事务测试 `oldReportVersionCannotCommitOrFailReplacement` 和 Mapper 版本边界案例。
- 核心问题：为什么固定 Session→Task 锁序？attempt 和 executionVersion 各是什么？为何读到新 version 不能让旧执行者获得新资格？为什么受理预留三次增量？
- 独立练习／讲解验收目标：推演旧模型成功/抛错×新执行者先后完成四种顺序，指出唯一有效提交；解释 INT UNSIGNED 能存但 Java Integer 不能读的边界和受控 FAILED。
- 掌握状态：**待学习（未确认）**；开发者掌握证据：未提供。
- 尚未理解／待验证点：外部模型无法靠 fencing 撤销已发请求；尚无真实 kill 后费用/恢复证据。
- 最近复核日期／人：2026-11-14 / Codex；状态变更：新增待确认。

<a id="l-m1b-03"></a>
#### L-M1B-03 — ACK、提交未知与 DLQ

- 关联工程记录／验收／代码 SHA：M1-B，未提交。
- 前置知识：消息至少一次、异常传播、事务提交、幂等。
- 源码入口：`ReportMqRuntime` listener、`ReportMessageConsumer.onMessage`、`InterviewReportService.generateClaimedReport`；`RealReportRocketMqSmokeTest.retryExhaustionDlqAndControlledReplay`。
- 核心问题：哪些数据库证据允许 ACK？为什么失败回写失败不能吞掉？RUNNING 重复与未知所有权的区别？为何 DLQ 不是业务 FAILED？
- 独立练习／讲解验收目标：从日志定位重试次数和 DLQ offset，解释 CLI 表格输出曾导致的探针超时；按手册从 DB 重建合法消息，明确终态/旧任务不重放与 FAILED 新 POST 的区别。
- 掌握状态：**待学习（未确认）**；开发者掌握证据：未提供。
- 尚未理解／待验证点：真实 ACK 丢失、JDBC 提交结果未知仍未注入；Mock DB 异常与真实 Broker 故障证据不同。
- 最近复核日期／人：2026-11-14 / Codex；状态变更：新增待确认。

<a id="l-m1b-04"></a>
#### L-M1B-04 — 三类期限与前端状态查询

- 关联工程记录／验收／代码 SHA：M1-B，未提交。
- 前置知识：数据库时钟、UTC/DATETIME、定时扫描、异步 HTTP、请求世代。
- 源码入口：`expireTask`、Task Mapper 四条扫描 SQL、`ReportAsyncProperties`；`useInterviewReport.ts`、`InterviewReportView.spec.ts`。
- 核心问题：派发租约/排队/执行期限各何时开始？为什么 DB 停机会打破准时收口？POST 200 GENERATING 与旧同步完成语义有什么区别？GET 如何保持无副作用并拒绝旧响应？
- 独立练习／讲解验收目标：推演 600 秒排队可能先于 20 次发送上限，手动 GET 与旧轮询交错、刷新/401/FAILED 重试；解释为什么 HTTP Timing 不再代表生成 READY 的全程耗时。
- 掌握状态：**待学习（未确认）**；开发者掌握证据：未提供。
- 尚未理解／待验证点：自然等待期限、长时间停机补扫、实际浏览器和真实模型端到端尚未验证。
- 最近复核日期／人：2026-11-14 / Codex；状态变更：新增待确认。

<a id="l-m1b-05"></a>
#### L-M1B-05 — 逐目标迁移、资源故障及切换回退

- 关联工程记录／验收／代码 SHA：M1-B、数据库准入和 `tools/m1b/README.md`，未提交。
- 前置知识：MySQL DDL 隐式提交、环境隔离、Maven 依赖树、Hikari 与 Spring 测试上下文缓存。
- 源码入口：单表增量、`EnvironmentIsolationInitializer`、`tools/m1b/run-tests.py`、test Hikari 配置、dependency-tree/effective-model 日志。
- 核心问题：test 名称为何不证明授权？为什么 snapshot 与增量不能重复执行？为什么旧同步和异步不能混跑？连接池总量为何可使业务并发测试失败？javassist tools.jar 告警是否等于已知功能失败或安全通过？
- 独立练习／讲解验收目标：列出某一精确目标的审批材料，按历史 GENERATING 分类制定停写/排空方案；用原始测试日志区分 2 业务等待失败、1 stub 错误和实际 max_connections 证据，不靠删断言通过。
- 掌握状态：**待学习（未确认）**；开发者掌握证据：未提供。
- 尚未理解／待验证点：正式目标迁移/回退演练和依赖升级仍未完成；备份不能自动覆盖现有数据。
- 最近复核日期／人：2026-11-14 / Codex；状态变更：新增待确认。

### M1-C 真实故障与性能证据（2026-11-15）

<a id="l-m1c-01"></a>
#### L-M1C-01 — 区分受理、READY 与吞吐

- 关联记录：[M1-C 台账](IMPROVEMENT_LEDGER.md#m1-c)、[最终构建 HTTP 数据](m1c-20261115-after-fix/AB_RESULTS.md)、[浏览器数据](m1c-20261115-browser/BROWSER_RESULTS.md)与[修复前历史数据](m1c-20261114/AB_RESULTS.md)，B 仍未提交。
- 源码入口：`ReportOutboxDispatcher.dispatchDue`、`ReportMqRuntime` 消费线程设置、`tools/m1c/benchmark.py` 与 `summarize.py`。
- 独立练习：复算最终构建 1 秒/并发 10 的 POST P95、READY P95 和完成吞吐变化；解释 A POST READY 与 B POST GENERATING 不能混称生成耗时、2 个消费者/2 秒扫描与 200 ms HTTP 客户端轮询的影响，并说明 30 样本、闭环吞吐和浏览器实际 3 秒轮询的限制。
- 掌握状态：**待学习（未确认）**；开发者独立复算或讲解证据未提供。

<a id="l-m1c-02"></a>
#### L-M1C-02 — 进程退出、ACK 和有界重试

- 关联记录：[故障注入与问题](2026-11-15-M1C-FAILURE-INJECTION.md)、`tools/m1c/faults.py`、`RealReportCrashWindowProbeTest`。
- 独立练习：画出提交后未发送、SEND_OK 后未记账、DB 成功 ACK 前 kill 及模型返回后提交前 kill 的持久状态；解释哪些实际自动恢复、为什么旧执行者不能提交、为什么显式新 attempt 可能再调一次模型。
- 掌握状态：**待学习（未确认）**；开发者独立复现或讲解证据未提供。

#### L-M1C-03 — 真实模型证据与费用边界

- 关联记录：[两份合成报告](m1c-20261114/real-report-evidence.json)、[usage](m1c-20261114/real-model-usage.jsonl)、`InterviewReportPromptBuilder`、`DeepSeekInterviewReportGenerator`。
- 独立练习：指出输入没有哪些事实而报告却作了具体判断；区分结构有效与事实有据，复算 901 输入/529 输出 token 的高峰费率估算，说明 usage、预算预留与供应商账单的区别。
- 掌握状态：**待学习（未确认）**；开发者独立讲解或复算证据未提供。

#### L-M1C-04 — 所有权检查与数据库锁

- 关联记录：[所有权锁复现](2026-11-15-M1C-FAILURE-INJECTION.md)、`ReportOwnershipLockIsolationTest`、`ReportTaskTransactionTest`。
- 前置知识：普通一致性读、`SELECT ... FOR UPDATE`、事务锁顺序、TOCTOU 和 MyBatis 会话级一级缓存的查询键。
- 独立练习：说明为什么无权请求在目标行已被持有时会先等待，以及无锁预查为什么仍必须在拿锁后再次验证；复现修复前 2 秒超时。用真实 MySQL 模拟等待锁期间归属变化，解释相同 Mapper 语句可能命中一级缓存并导致错误接受、使用不同 Mapper 语句的原因，并讨论额外 SELECT 对性能对照的影响。
- 掌握状态：**待学习（未确认）**；开发者独立复现、讲解或修改证据未提供。

### M1-C-R1 实际执行并发与恢复契约（2026-11-15）

此前 L-M1C-01 的“2 个消费者”应理解为一个实例的两条监听执行线程，不是两个实例或全局许可。以下条目均为新增学习任务，不代表开发者已经掌握。

<a id="l-m1c-r1-01"></a>
#### L-M1C-R1-01 — 闭环、实际在途与阶段时间

- 关联记录：[M1-C-R1 复核](M1C_R1_BOTTLENECK_REVIEW.md)第 2～3 节及其逐任务数据；最终 B 未提交。
- 前置知识：并发与吞吐、闭环负载、概率分位数、单调时钟和数据库审计时间。
- 源码入口：`tools/m1c/benchmark.py`、`ReportMqRuntime`、`ReportOutboxDispatcher`。
- 独立练习：从一个 1 秒必含模型调用区间推导必经子区间 `[end−1s,start+1s]`；解释为何两个实例和两条线程不同、A=10/B=2 是带条件的区间推导，复算单任务排队和同任务观察残差，说明为何不能相减不同样本 P95。
- 掌握状态：**待学习（未确认）**；开发者独立复算或讲解证据未提供。
- 尚待验证：扫描/传输/锁等待各自精确时间及相同实际并发 8 的隔离对照。

#### L-M1C-R1-02 — 消费者崩溃与预算化恢复

- 关联记录：[故障原始记录](m1c-20261114/faults-after-cache-fix.jsonl)、[M1-A 设计](2026-11-14-M1A-ROCKETMQ-ASYNC-REPORT-DESIGN.md)及本轮复核。
- 源码入口：`claimTaskExecution`、`expireTask`、`ReportMessageConsumer.onMessage`、`completeReportGeneration`。
- 独立练习：画出 task 821 模型返回后崩溃的状态、被 ACK 的重复消息、显式过期和新请求；说明自动 FAILED 与自动 READY 的差别，分别讨论第二次模型调用及报告/弱项不重复，设计重启不重置的有限预算而不绕过版本 CAS。
- 掌握状态：**待学习（未确认）**；开发者独立解释或复现证据未提供。
- 尚待验证：产品是否接受人工契约、自然到期扫描与有界自动恢复所需持久字段。

#### L-M1C-R1-03 — 事务隔离、MyBatis 缓存与锁后复查

- 关联记录：[所有权锁故障](2026-11-15-M1C-FAILURE-INJECTION.md)及本轮复核。
- 源码入口：`InterviewReportTransactionService.acceptReportTask`、`InterviewSessionMapper.xml`、MyBatis `BaseExecutor.createCacheKey`。
- 独立练习：说明为什么无锁预查不能替代加锁后检查；解释不同 MappedStatement ID 如何避免本路径一级缓存命中、READ_COMMITTED 如何影响锁后普通读，以及 `useCache=false` 为什么不是关闭一级缓存。用历史 MySQL 回归阐明旧执行者 fencing 和事务外模型调用的边界。
- 掌握状态：**待学习（未确认）**；开发者独立解释或修改证据未提供。
- 尚待验证：更高并发下的锁/池等待和异常传播；不改变隔离级别或删除检查。


### M1-C-R2 多瓶颈归因与最小调优（2026-11-15）

以下四项均待学习，工程验证不代表开发者已掌握。关联[本轮证据](m1c-r2-20261115/README.md)与[正式报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-M1-C-R2-RocketMQ多瓶颈归因与最小调优.md)；当前HEAD84e3489a85d0fb6c8b54ad2a130c52d7476fb897加未提交工作区，无本批提交SHA。

<a id="l-m1c-r2-01"></a>
#### L-M1C-R2-01 — 真实提交边界与跨进程时钟

- 前置知识：Spring事务同步、JDBC提交、单调/墙钟、往返时延和统计分位数。
- 源码入口：`tools/m1c-r2/ReportProbe.java`的SqlProbe/committed、`run.py`的calibrate、`analyze.py`；对应insertQueued、claimExecution、finishSuccess事务。
- 核心问题：SQL返回、updated_at、afterCommit为什么不同？为什么T4可能晚于T5？怎样保留缺失字段及校时误差，而不是相减两个P95？
- 独立练习：任选一个task在原始归档中还原T0～T10；复算一个阶段P50/P95和配对均值总和，解释HTTP校时误差及Broker时钟不能精确拆纯网络时延。
- 掌握状态：**待学习（未确认）**。开发者本人讲解、复算或独立修改证据：未提供。
- 尚待验证：逐条Broker ACK持久确认、JDBC提交结果未知与更低扰动观测。
- 最近复核：2026-11-15 / Codex；状态变更：新增待确认。

#### L-M1C-R2-02 — 执行并发、拉取缓存和背压

- 前置知识：ThreadPoolExecutor、线程池排队、push客户端的后台拉取、CAS执行权。
- 源码入口：`ReportMqRuntime.connect`、`ReportMessageConsumer.onMessage`；5.3.2 DefaultMQPushConsumerImpl.pullMessage与ConsumeMessageConcurrentlyService；探针PULLED/T5/T7计数。
- 核心问题：1个实例、1个Topic队列、2/4/8条执行线程为何不同？缓存阈值8为何可观测9？为什么提高消费并发后Broker拉取等待也可能改变？
- 独立练习：用原2/2000样本找到“已PULLED但等回调”的任务，解释2.31秒平均执行排队；复核固定到达率下flow-control20与0，不把所有延迟称为Broker网络开销。
- 掌握状态：**待学习（未确认）**。开发者本人复现证据：未提供。
- 尚待验证：收费模型实际配额、同组多进程重平衡及同用户弱项热点。
- 最近复核：2026-11-15 / Codex；状态变更：新增待确认。

#### L-M1C-R2-03 — 闭环、固定到达率与有限批次吞吐

- 前置知识：到达率/并发/吞吐、队列积压、起步排空、nearest-rank、小样本限制。
- 源码入口：`run.py`的wave、`analyze.py`的DB/客户端速率、`collect.py`的fixed-arrival-summary；原`tools/m1c/benchmark.py`。
- 核心问题：为什么原两波15条与新单波20条不能混算收益？为什么4条/秒A的峰值可以为5？POST受理快为什么仍可积压？
- 独立练习：从T1/T6计数重建最大QUEUED10与3，复算排空5.967与1.666秒；区分原始历史7.252/1.489、新配置收益及生产容量。
- 掌握状态：**待学习（未确认）**。开发者本人复算/讲解证据：未提供。
- 尚待验证：长期稳态容量、混合用户负载、采样对JIT/CPU的扰动及正式SLA。
- 最近复核：2026-11-15 / Codex；状态变更：新增待确认。

#### L-M1C-R2-04 — 后端完成、页面可见与工程取舍

- 前置知识：XHR、轮询计时、DOM布局/动画帧、外部调用与版本围栏、配置回退。
- 源码入口：`useInterviewReport.ts`的3000ms轮询；`browser-worker.cjs`的XHR/DOM时点；`ReportAsyncProperties`和Dispatcher的默认4/500。
- 核心问题：为什么GET后十几毫秒可见，但提交后仍等1.71秒？1秒轮询如何增加认证/查询成本？为什么只落地两项服务端配置，保留前端候选？
- 独立练习：复算一条T9→T10→T11，解释1→2次GET和小样本限制；给出两个环境变量恢复2/2000的排空方案，并比较MQ与仅MySQL任务Worker的收益/成本，保留人工重试和内容质量边界。
- 掌握状态：**待学习（未确认）**。开发者本人独立操作/解释证据：未提供。
- 尚待验证：前端高并发GET负担、生产运维、报告证据约束与真实模型质量。
- 最近复核：2026-11-15 / Codex；状态变更：新增待确认。


### M1-D — 报告证据、生成策略与可见延迟（2026-11-15）

以下6项均为待学习，掌握未确认；工程实现和本轮技术审核不代表开发者已能独立讲解。[报告与证据](m1d-20261115/README.md)。

<a id="l-m1d-01"></a>
#### L-M1D-01 — FINAL 与最终报告分工

- 前置知识：MAIN/INITIAL/FINAL、BigDecimal、事务边界。
- 源码入口：InterviewReportFinalReader.aggregateFinalEvaluations/validateEvaluationAssociations；InterviewReportService.generateClaimedReport；EvaluationStandard.resolveLevel。
- 核心问题：追问为什么不是额外一题？报告为什么不能根据文字再评分？旧说明中的INITIAL fallback为什么不能套到当前源码？
- 练习：沿A场5个FINAL手算73.40并追踪原INITIAL的关联校验，指出LLM被允许改变的四个文本字段。
- 掌握状态：待学习（未确认）；开发者证据未提供，复核日期待补。

<a id="l-m1d-02"></a>
#### L-M1D-02 — 有来源不等于正确，压缩不等于等价

- 前置知识：Prompt、结构化输出、证据索引与语义范围。
- 源码入口：InterviewReportPromptBuilder.build；DeepSeekInterviewReportGenerator.validateTextList；tools/m1d/CompactReportPromptBuilder.java与QUALITY_AUDIT.md。
- 核心问题：correction里的拓展为什么不能变成weakness？Q引用存在为什么仍可能丢MAIN/追问限定？输入已有错误时第二次模型能保证修复吗？
- 练习：从call6指出漏掉的Q4；复现private接口方法release8/9反例；解释call8为何finish_reason=stop仍必须FAILED，不得放宽5项校验。
- 掌握状态：待学习（未确认）；开发者证据未提供，复核日期待补。

<a id="l-m1d-03"></a>
#### L-M1D-03 — 受理、持久READY和可见时间

- 前置知识：单调时钟、afterCommit、浏览器轮询、异步请求防重。
- 源码入口：tools/m1c-r2/ReportProbe.java；tools/m1d/analyze.py/browser-worker.cjs；useInterviewReport.ts的startPolling/refreshReport/adoptReport。
- 核心问题：为什么生成器0.658ms仍需约3.1秒看到？缩短轮询改变哪段延迟、不改变哪段？为什么不能把不同组P95相减当分段？
- 练习：从task-timeline取一条T9到GET/DOM时点复算，解释3.416秒回放的GET2→4；推导长GENERATING下1秒轮询负载约3倍以及in-flight锁作用。
- 掌握状态：待学习（未确认）；开发者证据未提供，复核日期待补。

<a id="l-m1d-04"></a>
#### L-M1D-04 — Token、缓存与失败调用成本

- 前置知识：usage、缓存命中/未命中、预算预留与未知结果。
- 源码入口：tools/m1d/guard.py；m1d-20261115/paid-usage.jsonl/cost-summary.json。
- 核心问题：为什么精简v1 Token更少但本批估算更贵？校验失败为何仍计费？费用估算与实际账单有什么差别？
- 练习：手算8次15592/4658 Token的非高峰$0.004268064；解释固定12次/$0.50预算为何在第8次停止，未知usage时为什么不能盲目重试。
- 掌握状态：待学习（未确认）；开发者证据未提供，复核日期待补。

<a id="l-m1d-05"></a>
#### L-M1D-05 — 纯Java、MQ和两阶段报告取舍

- 前置知识：Outbox、CAS、唯一约束、不可变最终结果、幂等。
- 源码入口：DeterministicReportGenerator.generate/select；InterviewReportTransactionService.completeReportGeneration；M1dExperiment的提交后提示原型。
- 核心问题：无模型时哪些恢复成本仍存在？为什么不能把规则报告先READY再直接覆盖AI结果？只读basicReport与持久两阶段状态有什么区别？
- 练习：用模型返回前/后、事务提交前/后四个崩溃窗口解释当前契约，设计互斥的未来执行方式，不能同时启用两条提交路径。
- 掌握状态：待学习（未确认）；开发者证据未提供，复核日期待补。

<a id="l-m1d-06"></a>
#### L-M1D-06 — 实验夹具也需要业务隔离

- 前置知识：随机组卷、题目状态、评分点约束、事务回滚。
- 源码入口：tools/m1d/fixtures.py；InterviewQuestionPlanSnapshotService.prepareSnapshot；FullInterviewBusinessOfflineTest.createDatabaseFixtures。
- 核心问题：为什么报告实验复制的EASY题会影响另一组离线组卷测试？仅用独立数据库为什么还不够？
- 练习：追踪首次125失败的question has no enabled scoring points证据，说明只把本轮新建且有标记的复制题设DISABLED为什么不影响已保存MAIN快照，也不应删除约束/跳过测试。
- 掌握状态：待学习（未确认）；开发者证据未提供，复核日期待补。


### M1-E — 可靠提交、可丢失提示与用户可见时间（2026-11-15）

关联[主报告和逐条证据](m1e-20261115/DECISION_AND_VERIFICATION.md)。工程测试通过与开发者本人掌握分别登记；当前HEAD84e3489a85d0fb6c8b54ad2a130c52d7476fb897加未提交工作区，本批无提交SHA。

<a id="l-m1e-01"></a>
#### L-M1E-01 — 持久Outbox与提交后唤醒的不同责任

- 前置知识：Spring事务同步、ThreadLocal事务资源、线程池拒绝策略、CAS租约。
- 源码入口：ReportOutboxWakeup.onTaskAccepted、ReportAsyncConfiguration.reportWakeupExecutor、InterviewReportTransactionService.acceptReportTask、ReportOutboxDispatcher.dispatchDue；ReportOutboxWakeupIntegrationTest。
- 核心问题：为什么事件可丢失而任务不能丢失？AFTER_COMMIT为什么不直接发网络？为何不用CallerRuns或在残留事务资源上继续写？AtomicBoolean吞掉执行中的提示为何仍正确？
- 独立练习：复现提交回滚/执行器拒绝/并行扫描/消费者先完成4个窗口，解释500ms兜底、leaseToken与executionVersion保护的对象，说明提示只能优化等待而不保证恰好一次。
- 掌握状态：**待学习（未确认）**；开发者独立解释、复现或修改证据未提供。最近复核：2026-11-15 / Codex。

<a id="l-m1e-02"></a>
#### L-M1E-02 — 生成、提前开始、观察与基础内容可见

- 前置知识：MAIN/INITIAL/FINAL、完成事务、幂等POST、Vue computed/epoch、XHR与动画帧。
- 源码入口：InterviewReportView.vue的showEvidence/savedFinals，useInterviewReport.ts的timeline/refreshReport，tools/m1e/browser-worker.cjs与analyze.py。
- 核心问题：首份逐题分数0.085秒为什么不等于完整报告？P2的5秒阅读为什么减少页面等待而不是模型耗时？FINAL数据库旧记录重放与本次完成事务提交的计时边界有何差别？
- 独立练习：复算A的完整页反而慢986.6ms和首份结果早4146ms；核对B FAILED仍展示保存分数、不写报告/弱项；解释详情每页1次以及401/404停止与旧响应隔离。
- 掌握状态：**待学习（未确认）**；开发者证据未提供。尚待验证：后台标签页退避、真人可用性，不把未实现候选记为掌握。

<a id="l-m1e-03"></a>
#### L-M1E-03 — 完整输入、模型失败和可核验费用

- 前置知识：不可信模型输出、数组校验、证据范围、响应流、Token/cache用量和预算预留。
- 源码入口：DeepSeekInterviewReportGenerator.validateTextList、InterviewReportPromptBuilder.build；tools/m1e/guard.py；quality-audit.json、paid-usage.jsonl。
- 核心问题：同样请求为什么一份READY一份FAILED？200/stop为何不等于业务成功？为什么“有题干+得分”不能推出replace知识欠缺？保存和转发响应如何保证只读一次？
- 独立练习：定位call3的7项违例；对齐F输入空列表与输出具体弱项，查Java8/9规范；复算12次费用、区分预算换算与真实汇率/账单，说明未知usage不能按0处理。
- 掌握状态：**待学习（未确认）**；无开发者本人审核证据。内容发布仍NO-GO，不以AI抽查代替人工盲审。

<a id="l-m1e-04"></a>
#### L-M1E-04 — 实验协议纠错与实际并发

- 前置知识：客户端线程数、模型在途区间、闭环相位、热身与冷启动、单调时钟校准。
- 源码入口：tools/m1e/run.py的ThreadPoolExecutor、ReportProbe.java、analyze.py；protocol-correction.json和原始事件。
- 核心问题：为什么concurrency+1把标称1/8变成2/9？客户端8为什么模型峰值4？采样DB等待0为何不证明没有短等待？为何成功样本中位数不能掩盖1个失败？
- 独立练习：从T7/T8重建峰值，复算正确c1和c8对照、解释c8无收益；按6对请求哈希配对并保留失败/冷连接/缓存条件，不宣传稳定P95。
- 掌握状态：**待学习（未确认）**；开发者独立复现证据未提供。后续学习不授权新增付费实验或提交推送。


#### L-M1E-05 — epoch 隔离不等于请求合并，离线重放不等于新模型成功（2026-11-15）

- 前置知识：Promise 在途复用、组件生命周期、路由 epoch、防止过期响应与防止重复请求的区别；完整原响应、结构校验短路顺序、Mock HTTP 与外部调用证据类型。
- 源码入口：`useInterviewReport.refreshReport/queryReportForScope/recoverGenerating`、`InterviewReportView.spec.ts`；`DeepSeekInterviewReportGeneratorTest.givenSavedM1eResponse_whenReplayed_thenRejectsSevenStrengthsWithoutRepair` 与原响应资源。调用链：定时器/手动按钮→同域 Promise→GET→终态停轮询；原响应→客户端解析→报告列表校验→越界异常。
- 核心问题：手动递增 epoch 为什么能防止旧响应覆盖，却同时绕过在途保护？离页后停止轮询为什么不等于已发 HTTP 被取消？FAILED 重新登录应怎样从服务端归属校验后的已保存结果恢复？stop＋可解析 JSON 为什么仍可被 1–5 项业务约束拒绝？为什么本次重放不能冒充事故时保存的完整异常堆栈？
- 独立练习：复现 3 个重叠失败，再使同域峰值 GET=1；解释 READY/FAILED/404、换路由、离页及登录切换断言。核验原响应 SHA 和 7/7/5 条目，指出实际第一异常和未执行到的后续约束；区分本次 14 项 Mock 与历史真实 11 READY/1 FAILED，不修改 JSON 或校验规则。
- 证据与取舍：本次前端定向 59、全量 349、生成器 14 全通过；旧后端 1947/0/0/34 复用未重跑。原 1.463→1.040 秒为 1 秒桩 c1；4.223→0.085 秒为已有逐题结果可见，不能说完整报告稳定提速；内容发布 NO-GO 仍成立。[本次验证](m1e-20261115/closeout/VERIFICATION.json)。
- 掌握状态：**待学习（未确认）**。尚无开发者本人解释、复现或独立修改证据；L-M1E-01～04 状态不变。学习条目不授权后续真实调用、提交或部署。


### ARCHIVE-01｜依赖顺序学习索引（2026-11-15）

- 原有学习条目和已有证据原样保留；[15 个源码学习模块](PROJECT_SOURCE_LEARNING_GUIDE.md) 按 Session/FINAL → 幂等/CAS → 事务与 MyBatis 缓存 → 同步/异步报告 → MQ/Outbox → 执行围栏/恢复 → 前端与性能 → 评分/RAG 顺序串联。
- 与已有条目建立入口：[H01–H04](PROJECT_ENGINEERING_HISTORY_20261111_20261115.md#h01) 对应 B1–B4/B5；[H05–H12](PROJECT_ENGINEERING_HISTORY_20261111_20261115.md#h05) 对应题库、U1/U2、S1 和 Q1/Q2；[H13–H21](PROJECT_ENGINEERING_HISTORY_20261111_20261115.md#h13) 对应 M1-A～M1-E。
- [10 个面试演练](PROJECT_INTERVIEW_CASES.md) 含追问、取舍、历史证据和禁止夸大之处。AI 辅助产出不等于本人独立实现或掌握。
- 新路线全部为 **待学习 / 待确认**；仅在用户提供本人解释、复现或独立修改证据后确认。归档不授权启动真实模型、迁移、部署或新增大规模测试。


### P2-MQ-AUDIT｜公平性能比较与单体异步取舍（2026-11-15）

本次只登记后续学习入口，未开展源码学习或个人能力验收；以下两项均待学习，旧条目状态不变。

<a id="l-p2mq-01"></a>
#### L-P2MQ-01 — 相同模型上限下的端到端性能口径

- 关联记录：[P2-MQ-AUDIT](IMPROVEMENT_LEDGER.md#p2-mq-audit)、[审计报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-RocketMQ性能收益与工程价值审计.md)第 3、7 节；当前为 HEAD 84e3489 加未提交工作区，无本批代码 SHA。
- 前置知识：L-M1C-01、L-M1C-R2-01～04、L-M1E-04；闭环并发、样本分位数和事务提交。
- 源码入口：tools/m1c/benchmark.py、tools/m1c/summarize.py、tools/m1c-r2/ReportProbe.java、tools/m1c-r2/analyze.py；ReportMqRuntime.connect 和 ReportMessageConsumer.onMessage。
- 核心问题：为何 POST READY 与 GENERATING 延迟不能直接当生成收益？K=4 的桩许可为何必须计入排队？为什么不能用不同任务或组的 P95 相减？模型峰值与客户端并发分别代表什么？
- 将来独立练习：复算 M1-C 原波次吞吐、R2 8/8 同峰值及 M1-E c1/c8 的中位数；区分失败分母和缺少稳定 P95 的证据。不在本轮执行学习。
- 掌握状态：待学习（未确认）；开发者掌握证据未提供；尚待公平 A/B 和本人复现/解释；最近复核 2026-11-15 / Codex。

<a id="l-p2mq-02"></a>
#### L-P2MQ-02 — 事务 Outbox 与数据库持久任务的工程边界

- 关联记录：[P2-MQ-AUDIT](IMPROVEMENT_LEDGER.md#p2-mq-audit)、[审计报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-RocketMQ性能收益与工程价值审计.md)第 4～6 节；数据库任务替代方案仅设计。
- 前置知识：L-M1B-01～04、L-M1E-01；事务、租约、版本围栏、终态幂等。
- 源码入口：InterviewReportTransactionService.acceptReportTask/claimTaskExecution/completeReportGeneration、ReportOutboxWakeup.onTaskAccepted、ReportOutboxDispatcher.dispatchDue、ReportMqRuntime.connect、InterviewReportTaskMapper.xml 与 UserWeaknessMapper.xml。
- 核心问题：哪些可靠性来自同库事务/CAS，哪些依赖 Broker？本地 Future 为何不是持久任务？同用户弱项热点为何尚无实验结论？超过单批量与丢失提示怎样由补扫兜底？
- 将来独立练习：按提交前后、发送回执未知、模型返回后及最终提交后四个窗口画状态表，对照历史失败证据；对比方案 C 所需领取、回退与运维，不先修改数据库。不在本轮执行学习。
- 掌握状态：待学习（未确认）；开发者掌握证据未提供；方案 C 等价性、无迁移复用和本人理解均待验证；最近复核 2026-11-15 / Codex。


<a id="l-p2mq-03"></a>
### L-P2MQ-03 — 公平模型许可与实验准入（2026-11-15）

- **本轮仅登记后续学习入口：**关联 [P2-MQ-FAIR-AB](IMPROVEMENT_LEDGER.md#p2-mq-fair-ab) 和[核验报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-RocketMQ公平AB基线验证.md)；没有开展源码教学或个人能力验收。
- **入口：**`tools/m1c/benchmark.py` 的模型桩与单 MAIN 夹具、`tools/m1c-r2/run.py`/`ReportProbe.java` 的模型峰值及 afterCommit 计时、`tools/m1d/fixtures.py` 的五 MAIN 夹具、`docs/engineering/p2-mq-fair-ab-20261115/offline_replay.py` 的历史口径。
- **以后待核验问题：**客户端并发、工作线程、模型入口排队与实际 K=4 许可各自限制什么；为什么请求体完整哈希和成对数据是准入条件；为什么 POST READY 与 GENERATING 不同，n=8 的 nearest-rank P95 仅为最大值；旧线程数和扫描周期同时改变时如何避免错误归因。
- **掌握状态：待学习（未确认）。**没有用户独立解释、复现或修改证据；本轮仅完成离线复算，公平 A/B、生产可靠性和 4→8 收益均未验证。


<a id="l-git-01"></a>
### L-GIT-01 — 工作区版本身份与可构建提交边界（2026-11-15）

- **本轮仅登记后续入口：**[工作区审计台账](IMPROVEMENT_LEDGER.md#git-closeout-audit--二期工作区安全清点与提交规划2026-11-15) 与[逐文件清点报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-Git工作区安全收口与提交规划.md)；本轮没有开展源码教学。
- **源码/证据入口：**`InterviewReportTransactionService.acceptReportTask/claimTaskExecution/completeReportGeneration`、`ReportOutboxDispatcher`、`ReportOutboxWakeup`、`InterviewReportTaskMapper.xml`、`frontend/src/features/interview/composables/useInterviewReport.ts`、M1-E 收口 `source-manifest.json` / `VERIFICATION.json` 与历史原始索引。
- **后续学习问题：**为什么相同 HEAD 不能标识未提交源码；为什么前后端报告状态协议和迁移/测试必须按依赖审查；历史测试的文件散列与运行环境各能证明什么；如何在不覆盖证据的前提下只选择安全文件。
- **掌握状态：待学习（未确认）。**用户尚未提供本人解释、复现或独立修改证据；未把自动审计等同于个人掌握。


<a id="l-git-02"></a>
### L-GIT-02 — 隐私夹具、事务回滚与可复验的提交快照（2026-11-15）

- **仅登记后续学习入口：**[工程台账](IMPROVEMENT_LEDGER.md)与[提交前验证报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-Git安全整理与提交前验证.md)；本轮不开展源码教学。
- **入口：**`InterviewReportTransactionService.completeReportGeneration`、`InterviewReportTransactionServiceIntegrationTest.shouldRollbackReportAndWeaknessWhenSessionReadyUpdateFails`、`DeepSeekInterviewReportGeneratorTest` 的合成响应与私有回放、`sql/migrations/20261114_interview_report_task.sql`、本机恢复快照和最终源码清单。
- **掌握状态：待学习（未确认）。**尚无用户本人独立解释、复现或修改证据；测试通过不代表开发者已掌握。


<a id="l-git-03"></a>
### L-GIT-03 — 分批提交和证据版本的追溯（2026-11-15）

- **仅登记入口：**隐私规则 `ed7a7ba8b1e0c3fc4276154f242e67178acf1b1a`、SQL `18ef21b4c3e3cd30e4d30be645f41bb2ecabb445`、异步报告 `28cb2db25519e9d26c95dc00d716af4993d60f2d`；对应本地工作区代码、测试源码清单和工程台账。各提交边界以 `git show --stat <SHA>` 和本机收口报告为准。
- **掌握状态：待学习（未确认）。**本轮没有开展源码教学，也没有用户独立解释、复现或修改证据。


<a id="l-p2mq-candidate-01"></a>
### L-P2MQ-CANDIDATE-01 — 当前候选的 Outbox、消费与故障边界（2026-11-15）

- **本轮只登记后续入口：**[当前候选验收台账](IMPROVEMENT_LEDGER.md#p2-mq-candidate-acceptance--当前候选-rocketmq-核心链路复核2026-11-15)、本机 `backups/MQ_CANDIDATE_20261115_soe3tev4/` 的事件、清理前对账和源码比较。未开展源码教学。
- **入口：**`InterviewReportTransactionService.acceptReportTask/claimTaskExecution/completeReportGeneration`、`ReportOutboxWakeup.onTaskAccepted`、`ReportOutboxDispatcher.dispatchDue`、`ReportMqRuntime`、`ReportMessageConsumer.onMessage`、`InterviewReportTaskMapper.xml` 和 `RealReportRocketMqSmokeTest` 的重复消息/迟到确认方法。
- **待学习问题：**为何受理后任务可见但报告尚未存在；为什么发送结果不确定后可重发、`markSent` 不得回退业务终态；任务去重与外部模型恰好一次有何区别；为什么 `RUNNING` 时进程崩溃的恢复契约不同于发送前 Broker 短暂停机；历史版本只有部分源码相同意味着什么。
- **掌握状态：待学习（未确认）。**没有用户本人独立解释、复现或修改证据；一次 4.377 秒恢复仅为受控样本，不是 RTO。

- **补充证据入口：**`wakeup-targeted-junit.log` 的 5 项 Unit/Mock 与 4 项真实 MySQL 事务测试；仍只登记入口，不代表本人已学习或掌握。


<a id="l-p2-deploy-admission-01"></a>
### L-P2-DEPLOY-ADMISSION-01 — 部署目标隔离、迁移与回退（2026-11-15）

- **本轮仅登记入口：**[准入与最小部署报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-二期部署环境准入与最小部署方案.md)、[工程台账](IMPROVEMENT_LEDGER.md#p2-deploy-admission)；未开展源码学习或能力验收。
- **源码入口：**`EnvironmentIsolationInitializer.validateReportMq`、`InterviewReportTransactionService.acceptReportTask`、`ReportMqRuntime.connect/close`、`ReportOutboxDispatcher.dispatchDue/expireDue`、`MilvusCollectionInitializer`、`SecurityConfiguration.securityFilterChain` 与前端 `createWebHistory`；对照 `sql/migrations/20261114_interview_report_task.sql`、本机 `tools/m1b/broker.conf`。测试/历史文件不代表当前部署目标。
- **后续问题：**为什么 MQ 开关默认关闭且无同步回退；为何 accepting=false 时已有任务仍可被消费；旧同步执行者与历史 GENERATING 为什么须在切换前排空；为什么 MySQL、Milvus、MQ 的备份和 offset 要按一致时点核对；为何 M1-C 局部源码哈希一致不足以证明当前端到端崩溃测试；为何静态文件还需要安全放行和 SPA 路由处理。
- **掌握状态：待学习（未确认）。**没有开发者本人独立解释、复现或修改证据；登记不授权部署、迁移或真实模型调用。

- **差异复核入口：**后续可对照原始 M1-C `backups/M1C_R2_20261115/entry-B.jar` 与候选 JAR 的 `ReportAsyncProperties`、`ReportAsyncConfiguration$Scheduling`、`InterviewReportTransactionService.acceptReportTask` 和 Dispatcher 注解；本轮只核对，不开展学习。


<a id="l-p2-local-demo-01"></a>
### L-P2-LOCAL-DEMO-01 — 本机真实模型、RAG 与异步报告追踪（2026-11-15）

- **本轮仅登记入口：**[真实演示验收](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-二期本机完整演示与真实端到端验收.md)、[工程台账](IMPROVEMENT_LEDGER.md#p2-local-demo-e2e)和本机私有 `/Users/kun/Developer/projects/ai-interview-system/backups/DEMO_ENV_20261115_9lpoxk7u/` 的事件、usage、对账及失败日志；没有开展源码学习。
- **源码入口：**`InterviewService.submitAnswer`、`EvaluationOrchestrationService`、`SpringAiEmbeddingClient`、`MilvusVectorStoreClient`、`InterviewReportTransactionService.acceptReportTask/completeReportGeneration`、`ReportOutboxWakeup`、`ReportMqRuntime`、前端 `useInterviewReport` 与历史路由；对照本机演示 Broker 的 `mqshutdown` 与持久卷证据。
- **后续学习问题：**为什么 HTTP 异步受理时间不能当完整报告时间；为什么 10 条跨库 ID 一致不证明向量语义质量；为什么参考答案作答会高估真实表现；为什么 `docker stop` 与 JVM `mqshutdown` 的信号路径不同；为什么空载偏移恢复不证明在途消息可靠；为什么用户已启用的后端重启后仍需重新核对限额和真实调用。
- **掌握状态：待学习（未确认）。**没有开发者本人解释、复现或独立修改证据；登记不改变 Q1/Q2 NO-GO，不额外授权生产部署或清理数据。

<a id="l-p2-aiq-01"></a>
### L-P2-AIQ-01 — 评分语义、报告证据和模型结构校验（2026-11-15）

- **范围与前置。** 仅登记，当前暂停教学。前置为会话评分点快照、五维求分、单 MAIN 对应有效 FINAL、Jackson record 绑定。 [工程台账](IMPROVEMENT_LEDGER.md#p2-ai-quality) · [验证案例](../evaluation/2026-11-15-AI质量复核案例集.md)
- **源码入口。** EvaluationPromptBuilder.build、LlmEvaluationValidator.validateScoringPointResults、EvaluationScoreCalculator.calculate、FollowUpPolicy.decide、InterviewReportFinalReader.aggregateFinalEvaluations、InterviewReportPromptBuilder.toPromptEvaluation、DeepSeekInterviewReportGenerator.parseResponse/validateTextList，以及 DeepSeekInterviewReportGeneratorTest 的历史响应回放。
- **以后待复现。** 用 LinkedList 第 7 点说明 weight、covered 与实际 90 分各来自哪里。分别追溯原报告的已答事实、上游错误和报告新增推断。复验 JSON 模式下重复字段、7/7/5 超限为何在 Java 被拒绝，以及怎么证明重放的字节与真实响应相同。
- **掌握状态。** 待学习（未确认）；无开发者独立解释、复现或修改证据。


<a id="l-p2-report-trust-01"></a>
### L-P2-REPORT-TRUST-01 — 空弱项、JSON 契约和上游证据边界（2026-11-16）

- **仅登记入口，当前不学习：**[工程台账](IMPROVEMENT_LEDGER.md#p2-report-trust-20261116)、[报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-16-报告内容可信度最小修复与回归验收.md)和被 Git 忽略的 `backups/REPORT_CONTENT_20261116_ua0rf2j0/`；候选已否决。
- **源码入口：**`InterviewReportPromptBuilder.build/toPromptEvaluation`、`DeepSeekInterviewReportGenerator.parseResponse/validateTextList`、`DeepSeekEvaluationClient.completeJson`、`InterviewReportService` 的报告 JSON 写入与 READY 读回、前端 `InterviewReportView.vue`。对照本机保存的历史真实重复字段、6/7 项响应与 R3 真实对照；先区分结构合法和上游评价真实。
- **掌握状态：待学习（未确认）。**没有用户本人讲解、复现或独立修改证据；本轮未开展源码学习，也未采用候选。

<a id="l-p2-scoring-quality-01"></a>
### L-P2-SCORING-QUALITY-01 — 评分金标、原答证据与稳定性（2026-11-16）

- **仅登记入口，当前不开展学习：**[评测协议](../evaluation/2026-11-16-scoring-quality/PROTOCOL.md)、[人工判断历史](../evaluation/2026-11-16-scoring-quality/HUMAN_REVIEW.md)及 [工程台账](IMPROVEMENT_LEDGER.md#p2-scoring-quality-20261116)。
- **源码入口：**`EvaluationPromptBuilder.build`、`DeepSeekEvaluationClient.evaluate/parseSuggestion`、`LlmEvaluationValidator.validateScoringPointResults`、`EvaluationScoreCalculator.calculate`、`FollowUpPolicy.decide` 与 `tools/scoring-quality-20261116/summarize.py`；对照被忽略的冻结输入及原始响应，注意隐私边界。
- **掌握状态：待学习（未确认）。**尚无用户独立解释、复现或修改证据；仅登记不表示模型评分质量通过，也不授权修改生产评分规则。


<a id="l-p2-reliability-01"></a>
### L-P2-RELIABILITY-01 — 请求模式、单次核对顺序与反向门禁（2026-11-16）

- **状态：待学习/待确认；本轮源码教学暂停。** 无开发者独立讲解、复现或修改证据，不能因代理完成实验标掌握。
- **工程入口：**[完整报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-16-AI评分低成本可靠性方案甄别与验证.md) · [证据索引](../evaluation/2026-11-16-reliability/README.md)；`DeepSeekEvaluationClient.evaluate/completeJson/buildRequest`、`DeepSeekProperties`、`EvaluationPromptBuilder`、`LlmEvaluationValidator`、`EvaluationScoreCalculator`、`FollowUpPolicy/FollowUpTargetResolver`、`InterviewReportPromptBuilder.toPromptEvaluation`。
- **记录的取舍：**候选A仅评分低思考、报告仍disabled；B在同JSON契约先点/纠错后评分，局部改善但真实非法JSON与语义残留使其淘汰。真实HTTP、Java结构、语义依据、分数校准、发布准入需分开；reasoning计入completion而非重复计费，未知请求保留预留不重发。
- **证据：**基线139/0/0/1、A144/0/0/1、B138/0/0/0，JDK7/0/0/0，报告离线5/0/0/0；17真实响应16 Java有效、1额外环境UNKNOWN，84075已知tokens约¥0.09028836。6个共同合法诊断对2/6→5/6之外存在结构反向失败，不能择优统计。报告21/21原样依据仍可能传播上游错误。
- **未来掌握确认（不在本轮执行）：**本人能复现删除/JOIN反例、解释真实缺项与拓展区别、说明生产请求与报告隔离和未知usage预算后再更新。没有本批提交SHA；主树代码未改、文档未提交/推送。


<a id="l-p2-mq-net-value-01"></a>
### L-P2-MQ-NET-VALUE-01 — 异步收益口径、恢复责任及成本（2026-11-16）

- **状态：待学习／掌握未确认；本轮不开展源码教学。** 没有用户本人独立解释、复现或修改证据。
- **入口：**[净收益报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-16-RocketMQ工程净收益审查与后端收口.md)、[指标/可靠性/成本证据](mq-net-value-20261116/README.md)；`InterviewReportService.generateReportForApi`、`InterviewReportTransactionService.acceptReportTask/claimTaskExecution/completeReportGeneration`、`ReportOutboxDispatcher`、`ReportOutboxWakeup`、`ReportMessageConsumer`、`ReportMqRuntime`及两份Task/Session Mapper XML；观测入口 `tools/m1c-r2/ReportProbe.java`、`tools/mq-net-value-20261116/analyze.py`。
- **后续辨析项：**POST受理与事务afterCommit、轮询可见及闭环吞吐的区别；MQ重投与DB幂等的责任；executionVersion隔离旧执行者；模型返回后崩溃为何显式重试且可能再次付费；500ms扫描的数据库负担；经典PushConsumer队列分配与多实例前提。
- **结论边界：**共同K=4预实验48+12成功不证明总体吞吐优势，方案C未实现；CONDITIONAL GO保留现有MQ并停止本轮开发。工程文档追加和候选已推送状态不等于本人掌握，也不授权后续重构或教学。

<a id="l-p2-local-deployment-closeout-01"></a>
### L-P2-LOCAL-DEPLOYMENT-CLOSEOUT-01 — 单机持久化、进程恢复与证据边界（2026-11-16）

- **状态：待学习／掌握未确认；本轮不开展教学。** 用户本人独立解释、复现或修改的证据尚未提供。
- **入口：**[运行手册](../../tools/local-deployment/README.md)、[启动器](../../tools/local-deployment/backend.py)、[验收索引](2026-11-16-本机部署收口与工程档案.md)、[当前候选故障记录](../../backups/DEPLOY_CLOSEOUT_20261116/crash-recovery/result.json)；对照 `InterviewReportTransactionService.acceptReportTask/claimTaskExecution/completeReportGeneration`、`ReportOutboxDispatcher`、`ReportMqRuntime`、`InterviewReportTaskMapper.xml` 和 `RealReportCrashWindowProbeTest.committedBeforeAnySend`。
- **以后可辨析：**容器可写层、命名卷和备份的区别；为何消息发送前崩溃可由扫描恢复、RUNNING 崩溃可能失败并需要显式重试；Broker offset 与业务幂等分别证明什么；为何 MySQL InnoDB 一致备份不等于 Milvus/MQ 跨存储一致恢复；为什么 401 不是完整健康检查；NameServer 与 Broker 启动顺序为何仍需核验。
- **证据边界：**本轮真实测试 MQ 2 项、一次当前候选 kill 样本均使用模型替身；本机空载停启不证明断电恢复；独立备份恢复、长期运行和公网均未放行。不凭档案追加推断个人已掌握。

<a id="l-p2-final-freeze-git-closeout-01"></a>
### L-P2-FINAL-FREEZE-01 — 交付冻结及独立恢复入口（2026-11-16）

- **状态：待学习／掌握未确认。** 本轮只登记入口，不开展教学；没有开发者本人独立解释、复现或修改证据。
- **工程入口：**[部署与恢复记录](2026-11-16-本机部署收口与工程档案.md#最终交付冻结与恢复演练)、[单机运行手册](../../tools/local-deployment/README.md)、本机私有[恢复脚本](../../backups/GIT_FREEZE_20261116_a7d4c2/restore_rehearsal.py)和[结果](../../backups/GIT_FREEZE_20261116_a7d4c2/restore-result.json)；业务源码入口 `InterviewReportTransactionService.acceptReportTask/claimTaskExecution/completeReportGeneration`、`ReportOutboxDispatcher`、`ReportMessageConsumer`、`EvaluationPromptBuilder` 和 `InterviewReportPromptBuilder`。
- **关联历史：**[MQ 指标与故障矩阵](mq-net-value-20261116/README.md)、[评分人工裁决](../evaluation/2026-11-16-scoring-quality/HUMAN_REVIEW.md)、[失败候选](../evaluation/2026-11-16-scoring-quality/FAILURES.md)、[低成本方案](../evaluation/2026-11-16-reliability/README.md)。只记录定位与证据边界，不改变任何业务或模型规则。

<a id="l-p2-final-git-commits-01"></a>
### L-P2-FINAL-GIT-COMMITS-01 — 版本、证据和隐私边界（2026-11-16）

- **状态：待学习／掌握未确认。** 本次只维护索引，没有源码教学，也没有用户独立解释、复现或修改证据。
- **以后可查：**[Git 与测试台账](IMPROVEMENT_LEDGER.md#p2-final-git-commits-20261116)、[失败 Prompt](../evaluation/2026-11-16-scoring-quality/FAILED_PROMPTS.md)、[MQ 指标](mq-net-value-20261116/README.md)和[单机手册](../../tools/local-deployment/README.md)。区分源码 SHA、运行 JAR、文档提交和远端状态；区分历史真实服务、离线 Mock 和本轮测试。

<a id="l-p2-final-pr-review-01"></a>
### L-P2-FINAL-PR-REVIEW-01 — 默认兼容与历史状态切换（2026-11-16）

- **状态：待学习／掌握未确认。** 本轮仅记录审查入口，没有源码教学或开发者独立解释、复现、修改证据。
- **以后可查：**[PR 审查台账](IMPROVEMENT_LEDGER.md#p2-final-pr-review-20261116)，对照 main 上旧 `InterviewReportService.generateReport/claimReportGeneration` 与候选 `InterviewReportTransactionService.acceptReportTask`、`ReportOutboxDispatcher.expireDue`、前端 `recoverGenerating`。区分无任务的历史 GENERATING、持久 QUEUED 和已领取 RUNNING；区分合并代码与目标迁移、部署授权。


<a id="l-pr12-p1-01"></a>
#### L-PR12-P1-01 — 异步成功语义与确定拒绝

- 关联工程记录／版本：[PR12-P1-COMPAT](IMPROVEMENT_LEDGER.md#pr12-p1-compat-20261116)，`1a425fa5f7939907c5d3468449936d44daa413fd` + 未提交改动；P1-1 ACCEPTED Breaking Change。
- 前置知识：HTTP 状态码、Result envelope、前端状态机、提交结果不确定。
- 源码入口：`src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java#acceptReportTask`、`common/exception/ServiceUnavailableException.java` 与 `GlobalExceptionHandler`；`frontend/src/features/interview/composables/useInterviewReport.ts#handleGenerateFailure`；`InterviewControllerTest`、`ReportTaskTransactionTest` 和 `frontend/tests/InterviewReportView.spec.ts`。
- 核心问题：为什么 POST 200/GENERATING 不代表完成？为什么已知 503 可保持原状态而未知 503 必须先 GET？默认关闭和暂停受理为何不同？为什么本轮不恢复同步路径？
- 独立练习／讲解验收目标：未来对照 NOT_STARTED/FAILED/READY/GENERATING，解释已知拒绝、网络丢响应、异步受理与最终事务；本轮不开展。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；测试通过不代替本人解释或独立修改。
- 尚未验证点：仓库外客户端行为、本人独立复现。最近复核：2026-11-16／Codex，仅登记入口。

<a id="l-pr12-p1-02"></a>
#### L-PR12-P1-02 — 历史会话与任务所有权的只读切换核对

- 关联工程记录／版本：[本批切换条件](pr12-p1-20261116/README.md)，同上基线 + 未提交改动；P1-2 RESOLVED 仅指已授权目标的核对项。
- 前置知识：MySQL 一致快照、任务终态、Session CAS、attemptVersion 与 executionVersion、弱项幂等。
- 源码入口：[只读 SQL](pr12-p1-20261116/report-cutover-readonly.sql)、`InterviewReportTransactionService.acceptReportTask/ownsSession/completeReportGeneration`、`ReportOutboxDispatcher.expireDue`、`InterviewReportTaskMapper.xml#listActiveBySessionId`、`InterviewReportTransactionServiceIntegrationTest`；前端 `recoverGenerating` 仅 GET。
- 核心问题：为什么 RUNNING 不能只比 attemptVersion？无活跃任务是否等于无历史任务或无报告？为什么异常为 0 只支持当前目标时点，不能证明自动恢复？有报告时怎样避免重放弱项？
- 独立练习／讲解验收目标：未来用已有隔离用例区分无任务、终态未同步、多活跃和版本不符，解释逐目标授权及停旧执行者条件；本轮仅登记，不能擅自对业务库练习。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；真实 MySQL 测试不是本人掌握证据。
- 尚未验证点：其他数据库、未来切换时点、历史异常的数据修复。最近复核：2026-11-16／Codex，无源码教学或状态升级。


<a id="l-pr12-final-01"></a>
#### L-PR12-FINAL-01 — 全量回归、提交源码与远端检查的对应关系

- 关联记录／版本：[最终回归台账](IMPROVEMENT_LEDGER.md#pr12-final-regression-20261116)；源码 `9a95e53b13fc77be27962696202dc14169f18248`，后续文档提交只改记录。
- 前置知识：Maven生命周期、Surefire结果、Git提交树、CI head SHA、环境隔离；可先看 L-PR12-P1-01/02。
- 源码入口：`pom.xml`、`src/test/resources/application-test.yaml`、`ReportTaskTransactionTest`、`InterviewReportTransactionServiceIntegrationTest`、`frontend/tests/InterviewReportView.spec.ts`、`.github/workflows/frontend-checks.yml`；[脱敏回归清单](pr12-p1-20261116/final-regression.json)。
- 核心问题：为什么package包含test而无需重复全量？为什么1937通过和34跳过要分开？为何提交前测试可由源码散列关联，但远端Checks仍须核对最终HEAD？为什么Node24本地通过不能替代Node22的CI？
- 未来独立练习／讲解目标：从原始XML、源码SHA和PR检查分别复核一次结果，指出历史真实MQ证据与当前Mock/DB回归边界；本轮仅登记，不开展教学或练习。
- 掌握状态：**待学习（未确认）**。开发者掌握证据：未提供；实际贡献与独立理解待本人产出。
- 尚待验证点：本人独立复核能力、其他目标部署准入；没有因此追加业务开发。最近复核：2026-11-16／Codex。

<a id="l-p2-final-casebook-review-20261116"></a>
### 二期最终复盘后的学习入口（2026-11-16）

- **状态：待学习／掌握未确认。** 本次只整理索引，没有源码教学，也没有开发者本人独立解释、复现或修改证据；PR 合并不代表本人已掌握。
- **先后入口：**[案例库阅读路径](ENGINEERING_CASEBOOK.md#二期最终复盘阅读路径2026-11-16) → [L-M1A-01](#l-m1a-01)、[L-M1B-01～05](#l-m1b-01) 的事务 Outbox／投递租约／ACK → [L-M1C-02](#l-m1c-02) 的期限、崩溃与执行围栏 → [L-M1C-R2-01～04](#l-m1c-r2-01) 和 [L-P2-MQ-NET-VALUE-01](#l-p2-mq-net-value-01) 的公平性能口径／MQ 成本 → [L-P2-SCORING-QUALITY-01](#l-p2-scoring-quality-01) 的人工标签与模型证据 → [L-PR12-P1-01～02](#l-pr12-p1-01) 的接口契约与历史状态核对。
- **后续可自查的问题：**为什么异步受理不等于生成提速？为什么数据库任务、CAS 和最终事务不能由 MQ 自动替代？模型返回后进程崩溃为何可能需要显式重试？为什么默认关闭异步不恢复同步？这些问题的项目事实和证据位于案例库原章节；本次不创建练习或触发真实服务。
- **版本与范围：**PR #12 merge commit `3d85d9495b151da06260f2d0b57dc1031abab02c`；本次文档补充尚未提交，历史性能和测试不因本次整理而重新验证。真实参与、源码理解及独立复现均待本人提供证据。

- **最终归档补记（2026-11-16）：**工程开发阶段结束，后续从事务与任务领取、Outbox 派发与消费、执行围栏、报告最终事务和性能证据阅读开始；上述条目均保持待学习／掌握未确认。文档归档 PR 和合并 SHA 以 GitHub 记录为准，本补记不启动源码教学或新开发任务。
