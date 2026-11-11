# J1～B3 源码学习指南

日期：2026-11-11。对应基线为 `afbd1c5` 加本次已审查工作区，尚未提交。阅读约15分钟；先读入口，再读事务和测试。以下均是本机实际绝对路径，行号对应C0交付版本。

## 1. 删除模型 fallback，为什么业务仍能恢复？

模型fallback发生在**一次评价调用内部**：原来Jev失败后换DeepSeek。现在 [JudgeConfiguration.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/judge/JudgeConfiguration.java:12) 只装配DeepSeek；一次业务调用没有新增应用层HTTP自动重试。

业务恢复发生在**后续请求**：FAILED可再次竞争资格；fresh EVALUATING返回冲突；过期EVALUATING由后续请求以CAS接管；EVALUATED直接返回已有结果。这些机制由MySQL状态、attemptId和Java短事务实现，与是否有第二个模型无关。

先读 [InterviewService.handleExistingAnswer](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewService.java:415)，再读 [Workflow.claimOrRecoverEvaluation](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java:188)。注意：显式重提可能再次产生外部调用，不能承诺模型恰好执行一次。

## 2. CAS正确，应用层为什么还会误报500？

A把答案提交为SUBMITTED后暂停；B处理相同requestId、同题、同答案，成功保存评价并推进Session；A恢复时可能读到旧Answer和新的Session指针。CAS已保证只推进一次，但旧代码先检查“当前题必须相同”，把完成后的正常状态误认为损坏。

B1在 [Workflow.evaluateAnswer](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java:78) 增加首次EVALUATED重放；当前题不匹配时再读Answer，只有实际完成才返回重放信号。`return null`是内部约定，真实响应由 [InterviewService.buildSubmitAnswerResponse](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewService.java:534) 读取持久评价与最新Session构建。缺评价仍报错。

读 [并发重放测试](/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/InterviewConcurrentReplayIntegrationTest.java:117)，找出锁存器、B提交完成后的数据库断言，以及A恢复后整行不变断言。这里验证的是固定交错，不靠sleep碰运气。身份和payload校验始终在外部Service入口，不因重放而放松。

## 3. 为什么只有取得 attemptId 资格的任务才能写FAILED？

生成UUID不等于获得执行权。只有条件UPDATE成功并提交，数据库才承认本任务拥有当前attempt。另一个请求可能已经取得资格或接管；不能因为自己看到异常就把它的状态改成FAILED。

读 [领取事务](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java:94) 和 [InterviewAnswerMapper.xml](/Users/kun/Developer/projects/ai-interview-system/src/main/resources/mapper/InterviewAnswerMapper.xml:118)。失败更新要求 `id + status=EVALUATING + attempt_id`同时匹配；新任务接管后旧attempt更新0行，受到拒绝。

[Workflow.markEvaluationFailed](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java:317) 将次要回写异常放进原异常的suppressed。cause表示原错误的原因链；suppressed表示处理原错误时又发生的次要错误。不要让“记录失败也失败”遮住第一现场。

## 4. 为什么下一题查询也需要try保护？

下一题查询虽然是SELECT，却发生在claim已经提交之后。它失败不会自动撤销之前的短事务。若在try外，Answer停留在fresh EVALUATING，正常重提只能冲突，直到超过stale阈值后再有人请求。

B2将查询移入 [成功领取后的try](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java:149)。try边界不能再向前包住claim或REPLAY：尚未确认拥有资格的请求没有失败回写责任。B1重放位于try之前，因此不被B2误伤。

读 [InterviewPostClaimFailureIntegrationTest](/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/InterviewPostClaimFailureIntegrationTest.java:93)：故障前独立连接看到已提交的EVALUATING，故障后看到FAILED，恢复查询后使用完全相同请求完成。`:150`验证旧任务失败不覆盖新attempt。数据库整体不可用时回写也可能失败，stale仍是兜底；本项目没有后台恢复任务。

## 5. 为什么报告只允许合法FINAL，条数相等还不够？

MAIN需要追问时，INITIAL只代表阶段性评价；FOLLOW_UP FINAL才代表这道MAIN的完整结果。没有追问的MAIN直接FINAL。规则在 [FollowUpPolicy.decide](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpPolicy.java:32)。

两条评价可能都挂在同一道MAIN上，也可能引用其他会话答案。数量等于计划题数不能排除这些错误。新 [FINAL候选SQL](/Users/kun/Developer/projects/ai-interview-system/src/main/resources/mapper/AnswerEvaluationMapper.xml:215) 只筛FINAL并保留重复项；[ReportService.validateEvaluations](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java:248) 再核对MAIN集合/顺序、答案完成态和追问关联。

跟到 [UserWeaknessService.applySessionEvaluations](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/user/service/UserWeaknessService.java:26)：使用同一份FINAL列表，先全部校验再聚合写入。同一场同知识点只有一次统计，INITIAL不进入分母。有追问时使用父MAIN知识点。

历史Jev结果通过业务列读取，raw_result只作为opaque历史数据，不强行套DeepSeek DTO。READY历史报告直接可读；非READY恢复必须校验来源。兼容读取不等于自动纠正过去的错误分数。

## 6. 为什么评价保存与Session推进要在一个短事务？

如果先提交评价、再独立推进Session，中途故障会留下“有评价但会话没前进”的半成品；反过来会留下“会话已完成但评价缺失”。短事务让这些业务事实一起成功或一起回滚。

读 [InterviewWorkflowTransactionService.persistOwnedEvaluation](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java:390)，再读同文件 `moveToFollowUp:149`、`advanceToNextMain:281`、`finishSession:352`：锁答案核验attempt，再写成功RAG轨迹/评价、EVALUATED和Session version/current-question CAS。

模型调用耗时且可能超时，所以先在事务外完成RAG、模型与Java校验，最后才开启短提交事务。读 [EvaluationOrchestrationService.evaluate](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/orchestration/EvaluationOrchestrationService.java:104)：先复用已有结果；新结果携带pending trace返回，由Workflow事务持久化。早期文档“成功trace在模型前独立落库”不是当前实现。

报告采用相同原则：[InterviewReportTransactionService.completeReportGeneration](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java:28) 将report、Weakness和READY放在一起，模型生成在事务外。不要为了原子性把外部HTTP包进长事务。

## 7. 哪些测试真正证明MySQL行为？

- `InterviewWorkflowServiceTest`、`InterviewServiceTest`、`EvaluationOrchestrationServiceTest`、`InterviewReportServiceTest`、`UserWeaknessServiceTest`：Mockito证明Java调用、分支和参数；不能证明SQL或提交。
- `AnswerEvaluationMapperTest`、`InterviewAnswerMapperTest`、`UserWeaknessMapperTest`等：真实MyBatis XML与隔离MySQL；测试事务回滚不会使SQL变成Mock。
- `InterviewConcurrentReplayIntegrationTest`、`InterviewPostClaimFailureIntegrationTest`、`InterviewReportFinalConsistencyIntegrationTest`：无外围测试事务，可观察独立提交、固定交错和回滚；外部模型或上游故障点仍有替身，要分别描述。
- `FullInterviewBusinessOfflineTest`：安全链/MockMvc、真实业务和MySQL加Mock HTTP，但类级Transactional意味着它不能单独证明跨请求提交与外部调用无事务。
- `DeepSeekEvaluationClientTest`等Mock HTTP：证明请求/响应解析及异常合同，不证明真实DeepSeek可达、效果、延迟或费用。
- 19项真实外部测试按门禁跳过，本轮不算它们通过。完整普通回归的1589项中包含这些19项。

推荐打开 [B3真实一致性测试](/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/InterviewReportFinalConsistencyIntegrationTest.java:116)，依次看正常FINAL、错误数据拒绝、历史READY和非READY恢复。再看 [报告事务集成测试](/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/InterviewReportTransactionServiceIntegrationTest.java)，对比“校验拒绝”和“写入后回滚”的证据。

## 8. 如何从diff判断小改动会影响其他路径？

先看 `git status`、`git diff --stat`与未跟踪文件，确认哪些是已有用户修改。再沿每个改动方法的调用者、返回值和事务边界向两端阅读；不要只看新增行数。

本项目的三个练习：

1. B1只加两处返回，但应追到InterviewService：谁验证身份/payload、谁读取评价、缺评价是否仍失败？
2. B2只移动try，逐项标出“未取得资格、OWNED、REPLAY”；确认异常处理不会越过所有权边界，并检查同一attemptId。
3. B3删窗口函数，看所有调用者，再检查非READY恢复入口、构造器依赖与Weakness事务。只改主生成分支可能留下恢复旁路。

本次两个Workflow文件由B1/B2共同修改。累计diff可能把B1替换测试与B2新增never断言合成同一个hunk；应按经SHA验证的阶段快照拆分，不能按屏幕显示的一个hunk全部暂存。完整方案见 [C0收口报告](/Users/kun/Developer/projects/ai-interview-system/docs/verification/J1_B3_PHASE_CLOSEOUT_20261111.md)。

建议阅读顺序：入口与重放（3分钟）→ attempt/try与SQL（4分钟）→ FINAL来源与两种短事务（4分钟）→ 对照真实并发/报告测试及diff归属（4分钟）。读完应能解释：**状态已提交后，异常不会自动撤销它；重放、恢复和报表必须共同依赖可校验的持久事实。**

本轮另发现：HTTP响应脱敏不等于日志脱敏。DeepSeek错误cause目前可能把响应体带入日志，C0已用合成标记复现；留待B4-A审计，本指南不把它描述为已修复。
