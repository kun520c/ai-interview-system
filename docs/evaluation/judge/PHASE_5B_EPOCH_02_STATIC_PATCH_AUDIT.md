# Phase 5B Epoch E02 static patch audit

Audit UTC: 2026-10-08T03:37:28.639976+00:00

**STATIC_PATCH_AUDIT: PASS。仅授权新epoch；在线、语义质量与fallback此时尚未验证。**

## 独立核验的范围

读取离线补丁两报告、冻结benchmark及review/freeze报告、E01 comparison/原首调用diagnostic/运行及两provider结果。实际git diff只在Provider中增加两常量、两个数学私有方法及原score条件的附加兼容分支，54插入/1删除；未改HTTP Client、rubric、业务分数、prompt、fallback或合并逻辑。新增未跟踪专项测试源码已完整阅读，其63项包括captured数值原样保留、显式单位概率见证、未知模型及高精度不一致拒绝、边界/非法/低confidence与一次fallback。其他脏文件独立记录于target/phase5-epoch02/git-before.txt，保留用户修改。

HEAD: 6076a70f18f3062b8faff4fc179cf21f00a95036

Provider SHA256: d79eaa50533ea34be605c453de90dfb2010946440223a874ce2e98835dd28fa4

Benchmark SHA256: fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a

原补丁前408文件中除授权Provider外407/407字节相同；E01前置文件清单除Provider外全部相同。Phase4 question-bank/corpus/golden/完整检索报告与E01 incident/results/总报告均未覆盖。当前411文件完整hash清单保存于target/phase5-epoch02/integrity-before.json，后续E02 evidence将嵌入。38case=30MAIN+8FOLLOW_UP；24排序、4repair、原gold/bands/hardGates全部保留。新报告文件按E02命名。

## 源码逐项审核

| 结论 | 源码位置 | 状态 |
|---|---|---|
| 原严格分差0.001及概率和0.0001仍执行，兼容仅在分差超标时求值 | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:184 | PASS |
| 实际返回模型必须恰好jev-1.13.0；请求别名不提供资格 | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:198 | PASS |
| 原score在0.01十进制网格 | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:202 | PASS |
| 所有概率在0.01网格 | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:211 | PASS |
| 每个概率的±0.005区间限制在0..1 | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:214 | PASS |
| 总概率=1必须可行 | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:219 | PASS |
| 从概率下界分配剩余总质量；求最小及最大期望 | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:222 | PASS |
| score显示区间限制0..4，闭区间相交才接受 | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:225 | PASS |
| 升序或降序贪心填充容量，精确BigDecimal，不添加epsilon | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:234 | PASS |
| 原始score直接乘5 HALF_UP映射，未修改 | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:247 | PASS |
| 严格type、confidence、legend检查在数学兼容之前 | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:141 | PASS |
| 严格概率keys、finite、range检查在兼容之前 | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:169 | PASS |
| choice必须为argmax，原0.000001约束保留 | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:180 | PASS |
| 生产fallback仅在捕获ExternalServiceException后调用一次 | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:112 | PASS |
| 反馈证据必须是候选人连续原文 | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:283 | PASS |
| 反馈保留judge分数与coverage、追问建议 | src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:291 | PASS |

贪心正确性：剩余概率为1−Σl，所有变量容量u−l；若低索引尚有容量，较高索引上已分配质量移向低索引会减小Σi*q_i，故升序达到最小值；降序对称达到最大值。可行域凸，连续线性目标的可行值形成区间。与score显示区间相交是单位总概率约束下存在可解释隐藏期望的必要充分条件。该假设仅适用已观测版本/网格，不是供应商普遍精度保证。源码不会改写任何answer/概率或业务映射。

JevClient严格重复字段、尾随JSON、标量类型、非空model与question key集合检查保持；Provider type/legend/keys/finite/range/sum/confidence/choice argmax、反馈白名单字段与exact quote、validator均保持。unknown模型可以通过原紧检查，不能借用新增假设。安全边界及一次fallback生产代码保持。

## 本轮独立离线测试

JDK21.0.11；Maven -o，仅6套Mock/单元测试；子进程移除RUN_REAL_*、MILVUS_ENABLED、DEEPSEEK_ENABLED、AI_JUDGE_PROVIDER、JEV/DeepSeek/Embedding keys，不导入User凭证。无数据库、Milvus或推理HTTP。日志target/phase5-epoch02/offline-tests.log，201项invocation明细offline-test-results.json。

| suite | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| JevClientTest | 17 | 0 | 0 | 0 |
| JevJudgeProviderTest | 46 | 0 | 0 | 0 |
| JevScoreMappingTest | 16 | 0 | 0 | 0 |
| JevScorePrecisionCompatibilityTest | 63 | 0 | 0 | 0 |
| JudgeConfigurationTest | 12 | 0 | 0 | 0 |
| LlmEvaluationValidatorTest | 47 | 0 | 0 | 0 |
| TOTAL | 201 | 0 | 0 | 0 |

BUILD SUCCESS。未跳过、删改或放宽测试，无额外生产修改。尚未执行在线调用；runtime gates通过后才允许canary。禁止add/commit/push及Phase6。

## 原有工作树状态（完整保留）

    M AGENTS.md
     M src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java
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
    ?? src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevScorePrecisionCompatibilityTest.java
    ?? src/test/resources/META-INF/
    ?? src/test/resources/application-test-real.yaml
