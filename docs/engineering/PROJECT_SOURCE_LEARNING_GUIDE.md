# AI 模拟面试系统源码学习路线

日期：2026-11-15。配套：[工程历史主档案](PROJECT_ENGINEERING_HISTORY_20261111_20261115.md)、[面试案例](PROJECT_INTERVIEW_CASES.md)、[已有学习台账](SOURCE_LEARNING_BACKLOG.md)。建议按本路线顺序学习：先业务事实，再并发与事务，再异步和质量验证；不要求一次读完整个类。

**所有模块的掌握状态均为「待学习 / 待确认」**。AI 编写、测试或讲解不等于本人掌握；只有本人提供解释、复现或独立修改证据后再由本人确认。代码中的 M1-B～E 处于未提交工作区，历史同步版本请用只读 git show 84e3489:<仓库相对路径> 查看，切勿覆盖现有工作区。练习默认画图、阅读或在独立隔离环境完成；不要连接正式数据、运行付费模型、清理数据库或启动真实服务，除非另有明确授权和隔离检查。

<a id="learn-01"></a>

## 01｜Session 状态机与题目快照

- **原理：**状态机定义允许的状态和迁移；持久化快照让历史业务不受题库后续修改影响。状态名称、当前题与版本必须共同满足不变量。
- **项目场景：**创建时按类别一次选出全部 MAIN，之后按当前题推进；候选人 API 只看到允许的题目。
- **源码与方法：**[InterviewService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewService.java) 的 createOrResume/getDetail；[QuestionPlanSelector](../../src/main/java/com/kun/aiinterview/interview/service/QuestionPlanSelector.java) 的 select；[快照服务](../../src/main/java/com/kun/aiinterview/interview/service/InterviewQuestionPlanSnapshotService.java) 的 prepareSnapshot。
- **测试：**[QuestionPlanSelectorTest](../../src/test/java/com/kun/aiinterview/interview/service/QuestionPlanSelectorTest.java)、[快照集成测试](../../src/test/java/com/kun/aiinterview/interview/service/InterviewQuestionPlanSnapshotIntegrationTest.java)。
- **面试问题：**为什么预先持久化所有题目，却不能通过详情返回所有题目？题库更改后旧 Session 为什么不能变？
- **练习：**手画 CREATED→IN_PROGRESS→COMPLETED 及每次 currentQuestion/version 变化，标注用户可见和数据库可见范围。
- **掌握状态：待学习 / 待确认。** 对应 H05、H08。

<a id="learn-02"></a>

## 02｜MAIN、FOLLOW_UP、INITIAL 与 FINAL

- **原理：**同一 MAIN 的主答可能先得到 INITIAL；最多一个追问后的综合评价才是 FINAL；没有追问时主答直接 FINAL。
- **项目场景：**报告和弱项只消费每个 MAIN 的有效 FINAL，避免重复评分或缺项被静默替换。
- **源码与方法：**[Workflow](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java) 的 evaluateAnswer/applyDecision；[事务 Service](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java) 的 moveToFollowUp/advanceToNextMain/finishSession；[FinalReader](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportFinalReader.java) 的 aggregateFinalEvaluations。
- **测试：**[FINAL 集成测试](../../src/test/java/com/kun/aiinterview/interview/service/InterviewReportFinalConsistencyIntegrationTest.java) 的 selectsOneFinalPerMainAndAppliesWeaknessOnce 和拒绝损坏数据用例。
- **面试问题：**有五条 FINAL 为什么仍可能不合法？追问答案绑定错 MAIN 时应该怎么办？
- **练习：**各画一条有追问、无追问的数据链，并设计一个“条数相同但集合错误”的反例。
- **掌握状态：待学习 / 待确认。** 对应 H03。

<a id="learn-03"></a>

## 03｜请求身份、幂等与唯一约束

- **原理：**幂等表示重复同一业务请求不会产生不应重复的业务结果；身份还要包含合法归属与完整载荷。唯一键只约束写入，不自动修正读到的旧状态。
- **项目场景：**一个请求已成功但响应丢失，另一个相同请求重放；不同用户或不同 payload 不能冒充合法重放。
- **源码与方法：**[InterviewService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewService.java) 的 submitAnswer/buildSubmitAnswerResponse；[Workflow](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java) 的 evaluateAnswer/requireReloadedAnswer。
- **测试：**[InterviewConcurrentReplayIntegrationTest](../../src/test/java/com/kun/aiinterview/interview/service/InterviewConcurrentReplayIntegrationTest.java) 的 completedConcurrentRequestReplaysAfterSessionMoves 和身份边界测试。
- **面试问题：**数据库已成功后为什么还可能返回 500？唯一索引和“读取已完成结果”分别解决什么？
- **练习：**画请求 A 暂停、B 完成、A 恢复的两次读取；补一条 payload 不同必须拒绝的路径。
- **掌握状态：待学习 / 待确认。** 对应 H01。

<a id="learn-04"></a>

## 04｜CAS、version 与执行身份

- **原理：**CAS 用“当前状态和版本仍符合预期”作为 UPDATE 条件，并检查受影响行数；attempt 标识当前工作者，fencing 阻止旧工作者写新状态。
- **项目场景：**答案评价有 attemptId；报告有 attemptVersion 与 executionVersion；发送租约另有 dispatchToken，三者职责不同。
- **源码与方法：**[Answer SQL](../../src/main/resources/mapper/InterviewAnswerMapper.xml) 的 claimEvaluation；[Session SQL](../../src/main/resources/mapper/InterviewSessionMapper.xml) 的 claimReportGeneration/claimReportExecution；[任务 SQL](../../src/main/resources/mapper/InterviewReportTaskMapper.xml) 的 claimDispatch/claimExecution/finishSuccess。
- **测试：**[ReportTaskTransactionTest](../../src/test/java/com/kun/aiinterview/interview/service/ReportTaskTransactionTest.java)、[Workflow 事务测试](../../src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionServiceIntegrationTest.java)。
- **面试问题：**version 为什么不能由旧消费者在快完成时重新读一个新值？重复消息与旧执行者有何区别？
- **练习：**写出两消费者竞争时一条成功、一条更新 0 行的时序；再写旧执行者超时后晚到的条件。
- **掌握状态：待学习 / 待确认。** 对应 H02、H14、H16。

<a id="learn-05"></a>

## 05｜Spring 声明式事务与异常收口

- **原理：**事务是原子提交的数据库边界，不应把慢外部调用和网络等待一起放入长事务；声明式事务需理解代理调用、传播与回滚。
- **项目场景：**先短事务领取，事务外评价/生成，最后短事务提交结果；失败时只有当前 attempt 可以写 FAILED。
- **源码与方法：**[Workflow 事务 Service](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java) 的 tryClaimEvaluation/markEvaluationFailed；[报告事务 Service](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java) 的 acceptReportTask/completeReportGeneration；[报告 Service](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java) 的 generateClaimedReport。
- **测试：**[Workflow 事务集成测试](../../src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionServiceIntegrationTest.java) 的 shouldRollbackEarlierUpdatesWhenSessionCasFails；[报告事务集成测试](../../src/test/java/com/kun/aiinterview/interview/service/InterviewReportTransactionServiceIntegrationTest.java)。
- **面试问题：**为什么领取成功后查询异常也要收口？REQUIRED 和 REQUIRES_NEW 在这里如何判断？
- **练习：**标出受理、模型、最终提交三段的事务开始/结束；给每段加一个异常并推断回滚范围。
- **掌握状态：待学习 / 待确认。** 对应 H02、H03、H14。

<a id="learn-06"></a>

## 06｜MySQL 行锁、MyBatis 一级缓存与 TOCTOU

- **原理：**按主键 SELECT ... FOR UPDATE 会等待目标行的排他锁；锁前预查与加锁之间状态可能改变。同一 SqlSession 的一级缓存可能复用相同查询结果。
- **项目场景：**非所有者先被普通查询拒绝；所有者加锁后再用不同查询读取并校验，报告事务为 READ_COMMITTED。
- **源码与方法：**[acceptReportTask/lockSession](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java)；[Session Mapper](../../src/main/resources/mapper/InterviewSessionMapper.xml) 的 getReportOwnerId、getReportVersionForUpdate、getReportSession。
- **测试：**[ReportOwnershipLockIsolationTest](../../src/test/java/com/kun/aiinterview/interview/service/ReportOwnershipLockIsolationTest.java) 两个方法分别测试无权等待与锁前后 owner 变化。
- **面试问题：**为何先鉴权后加锁仍要复查？useCache=false 是否必然关闭一级缓存？隔离级别如何影响锁后读取？
- **练习：**不改代码，画三个版本：先锁后验、同查询预查复查、不同查询加锁后复查；各写一个失败或成立条件。
- **掌握状态：待学习 / 待确认。** 对应 H15。

<a id="learn-07"></a>

## 07｜历史同步报告与当前异步报告的对照

- **原理：**同步请求返回通常代表业务完成；异步受理只代表任务已接受，客户端必须查询终态。
- **项目场景：**旧 POST 等待完整报告；新工作区 POST 返回 GENERATING，GET 查询，FINAL 汇总和提交不变量保持。
- **源码与方法：**只读比较 Git 提交 84e3489 中 InterviewReportService.generateReport 与[当前 InterviewReportService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java) 的 generateReportForApi/generateClaimedReport，以及 [FinalReader](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportFinalReader.java)。
- **测试：**[InterviewReportApiServiceTest](../../src/test/java/com/kun/aiinterview/interview/service/InterviewReportApiServiceTest.java)、[ReportFinalConsistencyIntegrationTest](../../src/test/java/com/kun/aiinterview/interview/service/InterviewReportFinalConsistencyIntegrationTest.java)。
- **面试问题：**同步模型调用是否原来就在长事务里？如何验证？为什么两种 POST P95 不能直接当同一指标？
- **练习：**画两个顺序图，分别标 HTTP 返回时点、模型调用、事务提交、客户端看到 READY 的时间。
- **掌握状态：待学习 / 待确认。** 对应 H13、H17。

<a id="learn-08"></a>

## 08｜RocketMQ 生产、消费与 ACK

- **原理：**普通消息可重复；生产成功、消费者开始和业务提交是不同事件。只有数据库确认可安全终结或持久失败后才可 ACK；状态不确定应保留重试。
- **项目场景：**消息只携带稳定 ID/version；消费者以数据库为权威，领取失败的终态重复消息不重新生成。
- **源码与方法：**[ReportMqRuntime](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportMqRuntime.java) 的 connect/send；[ReportMessageProducer](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportMessageProducer.java) 的 send；[ReportMessageConsumer](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportMessageConsumer.java) 的 onMessage。
- **测试：**[消费者单测](../../src/test/java/com/kun/aiinterview/interview/report/async/ReportMessageConsumerTest.java)、[真实 MQ Smoke](../../src/test/java/com/kun/aiinterview/interview/service/RealReportRocketMqSmokeTest.java)；后者属于显式开关的真实服务测试。
- **面试问题：**Broker 收到消息能否证明业务已经提交？ACK 前进程退出会怎样？
- **练习：**画发送成功但未标 SENT、业务提交但 ACK 未返回两条时序，分别标重投后的 DB 决策。
- **掌握状态：待学习 / 待确认。** 对应 H14、H16。

<a id="learn-09"></a>

## 09｜Transactional Outbox 与提交后唤醒

- **原理：**业务状态与待发事件先在同一数据库事务中落盘；派发器可重扫、租约领取、发送并有条件记账。提交后的内存通知只是提示，不能替代持久记录。
- **项目场景：**一张 interview_report_task 同时保存业务任务与投递进度，P1 在 AFTER_COMMIT 后用有界执行器提示派发，500 ms 扫描仍兜底。
- **源码与方法：**[迁移 DDL](../../sql/migrations/20261114_interview_report_task.sql)、[任务 SQL](../../src/main/resources/mapper/InterviewReportTaskMapper.xml)、[Dispatcher](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportOutboxDispatcher.java) 的 dispatchDue、[Wakeup](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportOutboxWakeup.java) 的 onTaskAccepted。
- **测试：**[ReportOutboxDispatcherTest](../../src/test/java/com/kun/aiinterview/interview/report/async/ReportOutboxDispatcherTest.java)、[Wakeup 事务测试](../../src/test/java/com/kun/aiinterview/interview/service/ReportOutboxWakeupIntegrationTest.java)。
- **面试问题：**为什么 DB 提交和 MQ 发送无法直接视为原子？提示丢了怎么办？消费者先完成时迟到 markSent 能改哪些列？
- **练习：**列举提交前回滚、提交后进程退出、发送成功后回执未知三个窗口，各标持久事实与恢复者。
- **掌握状态：待学习 / 待确认。** 对应 H13、H14、H20。

<a id="learn-10"></a>

## 10｜消费者租约、截止时间与执行身份

- **原理：**投递租约控制谁可以派发；排队 deadline 与执行 deadline 描述不同阶段；执行版本决定谁可提交。超时不证明外部调用已停止。
- **项目场景：**任务 QUEUED→RUNNING，竞争者只有一人可获得 ReportExecution；执行超期时旧执行者不能覆盖新 attempt。
- **源码与方法：**[ReportExecution](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportExecution.java)、[任务 SQL](../../src/main/resources/mapper/InterviewReportTaskMapper.xml) 的 claimDispatch/claimExecution/finishSuccess、[事务 Service](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java) 的 claimTaskExecution。
- **测试：**[ReportTaskMapperIntegrationTest](../../src/test/java/com/kun/aiinterview/interview/service/ReportTaskMapperIntegrationTest.java)、[ReportTaskTransactionTest](../../src/test/java/com/kun/aiinterview/interview/service/ReportTaskTransactionTest.java)。
- **面试问题：**排队和执行为什么不能共享一个 updated_at 超时？派发租约 token 与执行 version 为什么不可合并？
- **练习：**以 2 个派发器和 2 个消费者为角色，画租约过期后旧回执迟到；逐一判断 SQL WHERE 是否允许更新。
- **掌握状态：待学习 / 待确认。** 对应 H14、H16。

<a id="learn-11"></a>

## 11｜失败恢复与显式重试

- **原理：**失败结果分为确定失败、未知提交和旧执行者；持久化状态可防重复提交，但无法撤销已经发出的外部调用。
- **项目场景：**RUNNING 消费者在模型返回后崩溃，超期后任务 FAILED，由用户显式新申请；数据库已成功提交而 ACK 未返回时，重投直接识别终态。
- **源码与方法：**[报告事务 Service](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java) 的 failExecution/expireTask/requireExecutionOrSettle；[Dispatcher](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportOutboxDispatcher.java) 的 expireDue。
- **测试：**[RealReportCrashWindowProbeTest](../../src/test/java/com/kun/aiinterview/interview/service/RealReportCrashWindowProbeTest.java) 的 modelReturnedBeforeReportCommit、businessCommittedBeforeConsumerAck 和 expiredExecutionNeedsExplicitRetry；仅按历史报告理解，不擅自启用真实故障测试。
- **面试问题：**为什么不能保证模型恰好调用一次？失败回写也失败时是否应 ACK？
- **练习：**整理“崩溃前 DB 状态—Broker 状态—外部调用次数—可否重试”的四行手写清单。
- **掌握状态：待学习 / 待确认。** 对应 H16。

<a id="learn-12"></a>

## 12｜报告与弱项的最终原子提交

- **原理：**多个相关业务写入要么一起提交，要么一起回滚；幂等还必须处理历史报告已存在时的副作用重复。
- **项目场景：**最终事务再次验证 FINAL、分数、session/task 身份和执行期限；写 Report、Weakness、READY、SUCCEEDED。
- **源码与方法：**[InterviewReportFinalReader](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportFinalReader.java) 的 aggregateFinalEvaluations；[事务 Service](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java) 的 completeReportGeneration；[UserWeaknessService](../../src/main/java/com/kun/aiinterview/user/service/UserWeaknessService.java) 的 applySessionEvaluations。
- **测试：**[FINAL 一致性测试](../../src/test/java/com/kun/aiinterview/interview/service/InterviewReportFinalConsistencyIntegrationTest.java)、[报告事务集成测试](../../src/test/java/com/kun/aiinterview/interview/service/InterviewReportTransactionServiceIntegrationTest.java)。
- **面试问题：**若报告已存在但状态不是 READY，为何不能再次更新 Weakness？最后 SQL 为什么再次判断 deadline？
- **练习：**在纸上为四个写入点分别假设异常，写出事务回滚后应看到的状态。
- **掌握状态：待学习 / 待确认。** 对应 H03、H14。

<a id="learn-13"></a>

## 13｜前端轮询、恢复与渐进展示

- **原理：**轮询中的 READY/FAILED 是终态；路由、用户或会话改变时旧响应不能覆盖新状态；相同作用域可共享在途请求。
- **项目场景：**1 秒 GET 查询完整报告，生成中先显示已有授权 FINAL；失败也可看已保存结果，且不伪造 READY。
- **源码与方法：**[useInterviewReport](../../frontend/src/features/interview/composables/useInterviewReport.ts) 的 queryReportForScope/recoverGenerating；[报告页](../../frontend/src/features/interview/views/InterviewReportView.vue)；[上一题反馈](../../frontend/src/features/interview/components/PreviousInterviewFeedback.vue)。
- **测试：**[InterviewReportView.spec.ts](../../frontend/tests/InterviewReportView.spec.ts)、[InterviewSessionView.spec.ts](../../frontend/tests/InterviewSessionView.spec.ts)。
- **面试问题：**页面提前显示旧数据是否会触发模型？切换账号时如何防止展示上一用户数据？忽略旧响应与 abort 有何区别？
- **练习：**画 GENERATING→READY、GENERATING→FAILED、切换用户三条异步时序，标记何时停止计时器。
- **掌握状态：待学习 / 待确认。** 对应 H07、H21。

<a id="learn-14"></a>

## 14｜P95、吞吐、样本与 A/B 对照

- **原理：**先定义起止点与统计总体，才比较 P50/P95；闭环负载等待前次完成，开放式负载按独立节奏到达。不同样本和不同阶段的 P95 不能直接相减。
- **项目场景：**同步 POST 完成与异步 POST 受理语义不同；R1 使用区间反推在途，R2 使用新计时器直接计数并调优。
- **源码与方法：**[M1-C A/B](m1c-20261115-after-fix/AB_RESULTS.md)、[R1 归因](M1C_R1_BOTTLENECK_REVIEW.md)、[R2 数据索引](m1c-r2-20261115/README.md)、[benchmark.py](../../tools/m1c/benchmark.py)。
- **测试：**这类是历史受控实验，不是单元测试；可核对 requests.csv、statistics.csv 和 task-timeline，而非本轮重新压测。
- **面试问题：**24.9 ms 与 1046.5 ms 可以说异步生成快了吗？为何线程 2→4 可能改善 I/O 吞吐，也可能增加压力？
- **练习：**用 H17 数字写一段含样本、桩、并发、POST 语义和退化的 100 字结论，再给出一项无法得出的结论。
- **掌握状态：待学习 / 待确认。** 对应 H17、H18、H20。

<a id="learn-15"></a>

## 15｜评分、RAG 与真实质量边界

- **原理：**结构合法、证据字段非空和 Java 求和正确是必要的程序检查，不足以证明证据相关、技术事实正确或模型总体准确。
- **项目场景：**RAG query 不含答案，筛选后证据进入 Prompt；covered 仅布尔，五维由模型建议，Java 校验并相加，追问由确定性规则约束。
- **源码与方法：**[EvaluationRagQueryBuilder](../../src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRagQueryBuilder.java) 的 build；[KnowledgeRetrievalService](../../src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalService.java) 的 retrieve；[Validator](../../src/main/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidator.java) 的 validate；[ScoreCalculator](../../src/main/java/com/kun/aiinterview/interview/evaluation/score/EvaluationScoreCalculator.java) 的 calculate；[FollowUpPolicy](../../src/main/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpPolicy.java) 的 decide。
- **测试：**[ValidatorTest](../../src/test/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidatorTest.java)、[ScoreCalculatorTest](../../src/test/java/com/kun/aiinterview/interview/evaluation/score/EvaluationScoreCalculatorTest.java)、[FollowUpPolicyTest](../../src/test/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpPolicyTest.java)；质量反例另看 [Q1 解盲](../evaluation/q1-r2-20261114/UNBLIND_REPORT.md)。
- **面试问题：**为什么 7/12 不能当总体错误率？部分覆盖如何影响 CORE 追问？为什么历史 false 不能直接转为 PARTIAL？
- **练习：**不调用模型，针对 LEFT JOIN、removeIf、ACID 各写“已答/遗漏/技术错误/证据不足”四栏；比较已有模型结论并保留分歧。
- **掌握状态：待学习 / 待确认。** 对应 H06、H09–H12、H19。
