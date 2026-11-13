# 源码学习待办清单

建立／最近更新：2026-11-13（Asia/Shanghai）。C7 已将 C6 验收的同一源码依次提交，累计源码提交为 `71aeb22f7da906660763e9efb1100a20df7705c9`；B4-D／B4-E／B5-P2-A／B5-P2-B 的完整 SHA 和父子关系见 [工程优化台账](IMPROVEMENT_LEDGER.md#11-git-实际状态)。本清单与台账随其后的独立文档提交归档，未推送。

建档及 C6 时点的基线是 `01ac774caff491b978dd2268e8b427355a64b97a` 加四批未提交修改。下文各批“无 SHA／未提交”保留为当时事实，当前提交定位以以上 C7 更新为准。源码内容与历史回归证据不变；新执行的独立编译／类型检查见 [C7 记录](IMPROVEMENT_LEDGER.md#c7)，不等于重跑功能测试或开发者掌握。

## 1. 学习状态与使用方法

本清单共 **63 条**：历史批次技术点25条，C0/C3共通方法3条，B4-D源码学习7条，B4-E源码学习7条，B5-0审查/验证学习6条，B5-P2-A密码边界学习3条，B5-P2-B日志边界学习3条，C6累计收口学习3条，旧并发/观测未编号候选2条，B6/B7/B8/B9范围占位4条。最后4条不是已经定义的技术点，范围明确后再拆分。B5原1条占位已按本轮明确范围拆为6条；B5-P2-A/B均已完成工程修复，C6完成证据复核，仍没有开发者掌握证据。

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
