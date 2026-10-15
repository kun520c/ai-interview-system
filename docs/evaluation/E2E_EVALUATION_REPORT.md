# 真实端到端面试评测

结论：**FAIL（首次成功可靠性未通过）**。真实DeepSeek链路能完成三档质量的面试、持久化与报告，但出现一次答案HTTP500和两次报告HTTP502。事后重试可恢复，不改变baseline失败事实。Jev+RAG+DeepSeek链路因缺少Jev凭据仍BLOCKED，未实际验证。

## 实际运行链路

```text
真实register/login → JWT + DB当前用户认证
→ Create EASY Session（5 MAIN题库快照一次落库）
→ POST answer（短事务写Answer并CAS）
→ claim EVALUATING → EvaluationContext（FOLLOW_UP合并MAIN与追答）
→ question/scoring points query → 真Embedding → 真Milvus → MySQL eligibility
→ rag_retrieval_batch + rag_hit_log
→ EvaluationPrompt → JudgeProvider(deepseek，本轮实际选择)
→ 真DeepSeek → Java validator → Java五维相加 → Java FollowUpPolicy
→ AnswerEvaluation INITIAL/FINAL → Session CAS → 追问/下一MAIN/结束
→ POST report → FINAL聚合与均分 → 真DeepSeek文本 → Java校验
→ InterviewReport + UserWeakness事务 → READY
```

Jev是可选策略，不是默认链路中的必经步骤。若选jev，才是Jev分数/coverage判断→DeepSeek文本→Java合并校验；错误时一次回退到完整DeepSeek Judge。所有实际评价llm_model为deepseek-flash、prompt_version为evaluation-prompt-v1，没有把fallback当Jev成功。

启动方式：Maven/Surefire + local profile +真实Tomcat随机端口；JDK HttpClient通过真实HTTP访问。核心AI/RAG完全使用生产bean，无Mock。第二轮测试观察器只调用原bean并原样返回结果，不改query、prompt、score或状态。日志只涉及合成回答，不包含key/JWT/password。

## 会话结果

| 批次 | Session | 回答质量 | 提交尝试数 | FINAL均分 | 首次报告HTTP | 原始结论 |
| --- | --- | --- | --- | --- | --- | --- |
| 第一轮 | 2463 | excellent | 5 | 92.8 | 200 | COMPLETED_REPORT_CONSISTENT |
| 第一轮 | 2464 | medium | 10 | 32.0 | 200 | COMPLETED_REPORT_CONSISTENT |
| 第一轮 | 2465 | poor | 2 | 未完成 | 未生成 | 首次追问HTTP500后中断 |
| 第二轮 | 2466 | excellent | 5 | 95.0 | 502 | REPORT_FAILED |
| 第二轮 | 2467 | medium | 10 | 33.4 | 502 | REPORT_FAILED |
| 第二轮 | 2468 | poor | 10 | 11.0 | 200 | COMPLETED_REPORT_CONSISTENT |

数据：[第一轮](evidence/e2e-first.json)、[第二轮](evidence/e2e.json)、[诊断恢复](evidence/recovery.json)。答案输入由评测者准备，不是本人实际回答；medium表示“核心概念部分正确、缺乏原理”，不是预设必为60分档。所有会话难度EASY，5个题目相同，未声称覆盖MEDIUM/HARD题库。

第一轮优秀5 MAIN得88/96/91/97/92，均分92.8；部分正确5 MAIN+5追问，FINAL31/31/31/35/32，均分32.0。第二轮对应均分95.0/33.4/11.0。排序符合直觉，但覆盖权重60%的部分正确答案最终约33分，因总分按五维求和而非评分点权重；需要本人标注完整性/实践量表，不能擅改业务公式。

## 真实失败与恢复

1. Session2465、Answer1392、FOLLOW_UP首次HTTP500：`LlmEvaluationValidator`拒绝缺失scoringPointResults。RAG batch已保存、未写AnswerEvaluation；答案进入FAILED，会话不向下一题推进。失败raw provider JSON未持久化，因此只能凭异常定位字段，无法事后还原整个JSON。该缺陷属于模型输出与错误边界/可观测性问题，不是MySQL或Milvus不可用。
2. Session2466/2467首次报告HTTP502：DeepSeek Report strengths数组数量不满足1..5；Java拒绝保存报告并标记失败。没有截断列表或放宽schema来让测试通过。原始两次失败report JSON未保存，不能断言到底是0还是>5项。
3. 对FAILED答案保持相同requestId/content做一次真实诊断重试，1392变EVALUATED、FINAL=2并推进下一MAIN；该会话未继续完成。对两份失败报告做真实诊断重试后都READY、均分仍95.0/33.4。恢复阶段只为本轮合成用户签发测试JWT，随后仍经过真实DB校验；它不是又一轮register/login完整流程。
4. 首次Recovery采集脚本误查interview_session.overall_score，在报告已成功后报SQL错误；修正为实际total_score后采集通过。记录为测试脚本错误，不当作生产回归，也没有抹掉原始执行Error。

跨两轮共42次首次答案HTTP提交，1次500；5次首次报告生成，2次502。这是小样本观察，不能作为长期SLA。补充恢复后目前有5个COMPLETED/READY会话、1个IN_PROGRESS会话；保留原始失败证据。

## Coverage、追问与一致性

优秀答案覆盖全部核心评分点，直接FINAL；部分正确基本只覆盖CORE，追问围绕缺失KEY，如桶定位、复杂度、构造注入、回表、旧SYN。FOLLOW_UP没有再次追问；5主问最多各1追问。较差错误结论两个点均不覆盖，纠正方向基本正确。

每个完整session有5个FINAL；MAIN的INITIAL不会计入报告与weakness。所有成功报告overallScore与FINAL均分相同；第一轮、第二轮和恢复均用SQL/HTTP核对。首轮每场首答的相同请求重放未新增RAG batch，避免模型再次评分。空answer和65536字节边界是真实HTTP400，未落Answer记录。

## 不能只看分数：已发现的点评问题

Session2463 ArrayList评价missingPoints写“未特别强调…的边界已在回答中说明，整体无实质遗漏”，自相矛盾。优秀报告又把哈希碰撞、构造注入的不可变性和序列号同步列为不足；输入实际已说hashCode相同不保证相等、final依赖、x/y序列号。部分要求超出冻结评分点。应把“确实缺失”与“可选拓展”分开标注，减少误导复习建议。

较差报告仍有“能够理解题意”等推断式表扬，没有技术正确性证据支撑；不属于评分反转，但需要人工审查反馈文风。TCP题库reference_answer本身有“客户端确认自己的ACK是否收到”的不准确表述，生成反馈有沿用风险；RFC9293讲的是确认SYN序列号与处理旧重复连接请求，不能以ACK确认ACK解释第三次握手。[TCP标准](https://www.rfc-editor.org/rfc/rfc9293.html)。本轮记录，未改题库或参考答案。

## RAG 是否真正参与

生产trace与prompt证明相同检索证据被传入真实DeepSeek。第二轮完整保存25次原始检索/过滤结果和25次Java校验前Judge结果：[runtime-retrieval-e2e.json](evidence/runtime-retrieval-e2e.json)、[runtime-judge-e2e.json](evidence/runtime-judge-e2e.json)。检索query不含答案；MAIN各质量档的query相同；FOLLOW_UP只添加追问与目标点评标准。

这是“输入参与”证据，未证明因果提升。有/无RAG配对对照区间重叠，参考答案本来就含关键事实；当前实验无法把RAG收益从模型知识/参考答案中分离。Top5无关片段已进入prompt，模型本轮大多没被带偏，但不能假定未来永远安全。

## 可观测性

rag_retrieval_batch能定位query、TopK、过滤快照、model/version、raw/evidence count；rag_hit_log记录通过eligibility的chunk IDs/rank/similarity。失败前RAG仍可查。成功answer_evaluation包含provider/model/promptVersion/raw_result与Java分数。原始Milvus被过滤命中只在内存，不逐项持久化；失败Judge/Report raw输出不保存，失败原因detail不足。

本轮测试观察器解决本地证据采集而未修改生产日志；后续建议增加开发/评测专用、脱敏且有保留期限的阶段事件，特别是schema失败字段、实际provider/fallback原因和失败raw摘要。不得记录API key或真实用户隐私。本轮尚未完成真实Jev在线兼容、timeout或fallback频率检验。
