# Phase 5 protocol precision patch test results

Execution: 2026-10-08 (Asia/Shanghai), Oracle JDK21.0.11, offline only.

## 实际运行记录

| run | tests | failures | errors | skipped | result |
|---|---:|---:|---:|---:|---|
| first targeted run | 201 | 1 | 0 | 0 | BUILD FAILURE：新增JSON表示形式断言失败 |
| final targeted run | 201 | 0 | 0 | 0 | BUILD SUCCESS：完整typed response一致、数值和映射断言保持 |

两个运行共402个JUnit invocation；最终验收为201/0/0/0，首轮失败记录保留。所有已有测试未改。

子进程切JDK21，移除RUN_REAL_*、MILVUS_ENABLED、DEEPSEEK_ENABLED、AI_JUDGE_PROVIDER和Provider凭证变量；不导入Windows User凭证。仅执行6套JUnit，无SpringBootTest/数据库测试，HTTP用MockRestServiceServer截获，生成及fallback用Mockito。

```text
.\mvnw.cmd -o -B -ntp -Dtest=JevScorePrecisionCompatibilityTest,JevJudgeProviderTest,JevClientTest,JevScoreMappingTest,JudgeConfigurationTest,LlmEvaluationValidatorTest test
.\mvnw.cmd -o -B -ntp -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/phase5-protocol-patch/classpath.txt
```

Maven compile/classpath BUILD SUCCESS；最终测试已编译生产修改和测试。CapturedProtocolReplay临时类javac成功；原首调用完整response数值校验PASS、原值保留，不计算业务总分、不使用gold调参。没有完整回归或真实集成声明。

## 最终每套结果

| suite | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| JudgeConfigurationTest | 12 | 0 | 0 | 0 |
| JevClientTest | 17 | 0 | 0 | 0 |
| JevJudgeProviderTest | 46 | 0 | 0 | 0 |
| JevScoreMappingTest | 16 | 0 | 0 | 0 |
| JevScorePrecisionCompatibilityTest | 63 | 0 | 0 | 0 |
| LlmEvaluationValidatorTest | 47 | 0 | 0 | 0 |

## 新增63项覆盖说明

| 场景 | 结果 |
|---|---|
| 五维numeric replay、原typed响应/score/probabilities/映射保留 | PASS |
| 显式潜在分布：总和1、E3.558与两位舍入 | PASS |
| score3.80/4.00/3.50/3.57与captured区间不相交 | 4/4拒绝，PASS |
| 其他response.model不借用兼容预算 | 5/5拒绝，PASS |
| score/概率高精度不一致不借用预算 | 2/2拒绝，PASS |
| 其他模型高精度但原紧容差内一致 | PASS |
| 和偏差±0.00011/±0.01严格拒绝 | 4/4，PASS |
| 负/>1/null/缺多key/null map/type/legend/score/confidence非法 | 15/15，PASS |
| typed概率NaN/+Inf/−Inf先拒绝 | 3/3，PASS |
| 低confidence一次fallback、无feedback | PASS |
| choice不是argmax | PASS |
| 0/1/2/3/4全单点确定分布 | 5/5，PASS |
| 单点截断、总和约束、闭区间端点 | 12/12，PASS |
| near0/1截断、十进制端点及0.33/0.67浮点输入 | 7/7，PASS |
| p4=1但score3.97恶意矛盾，独立预算会误受而本算法拒绝 | PASS |

旧JevScoreMappingTest继续验证score NaN/Inf/null/越界和HALF_UP；旧Client/Provider/Validator/Configuration测试验证原JSON、keys、legend、异常与fallback规则。

## 每个JUnit invocation最终结果

参数化序号对应源码ValueSource/CsvSource的声明顺序。以下从本次Surefire XML提取，不使用历史数量。

### JudgeConfigurationTest

| test invocation | result |
|---|---|
| deepSeekDisabledDoesNotCreateJudgeOrRequireJevKey | PASS |
| rejectsInvalidJevConfiguration(String)[1] | PASS |
| rejectsInvalidJevConfiguration(String)[2] | PASS |
| rejectsInvalidJevConfiguration(String)[3] | PASS |
| rejectsInvalidJevConfiguration(String)[4] | PASS |
| rejectsInvalidJevConfiguration(String)[5] | PASS |
| rejectsInvalidJevConfiguration(String)[6] | PASS |
| rejectsInvalidJevConfiguration(String)[7] | PASS |
| rejectsInvalidJevConfiguration(String)[8] | PASS |
| explicitlySelectsJevAndBindsProperties | PASS |
| defaultsToOriginalDeepSeekWithoutJevConfiguration | PASS |
| rejectsUnknownProviderRatherThanSilentlySelectingDeepSeek | PASS |

### JevClientTest

| test invocation | result |
|---|---|
| parsesOfficialScoreResponseAndSendsOfficialHttpContract | PASS |
| rejectsInvalidResponseEnvelopeAndJson(String)[1] | PASS |
| rejectsInvalidResponseEnvelopeAndJson(String)[2] | PASS |
| rejectsInvalidResponseEnvelopeAndJson(String)[3] | PASS |
| rejectsInvalidResponseEnvelopeAndJson(String)[4] | PASS |
| rejectsInvalidResponseEnvelopeAndJson(String)[5] | PASS |
| rejectsInvalidResponseEnvelopeAndJson(String)[6] | PASS |
| rejectsInvalidResponseEnvelopeAndJson(String)[7] | PASS |
| rejectsInvalidResponseEnvelopeAndJson(String)[8] | PASS |
| rejectsInvalidResponseEnvelopeAndJson(String)[9] | PASS |
| rejectsInvalidResponseEnvelopeAndJson(String)[10] | PASS |
| sanitizesHttpErrorsWithoutExposingProviderBody(int)[1] | PASS |
| sanitizesHttpErrorsWithoutExposingProviderBody(int)[2] | PASS |
| sanitizesHttpErrorsWithoutExposingProviderBody(int)[3] | PASS |
| sanitizesHttpErrorsWithoutExposingProviderBody(int)[4] | PASS |
| sanitizesHttpErrorsWithoutExposingProviderBody(int)[5] | PASS |
| convertsTimeoutToSanitizedExternalFailure | PASS |

### JevJudgeProviderTest

| test invocation | result |
|---|---|
| nullSelectedChoiceProbabilityFallsBackBeforeGeneration(String)[1] | PASS |
| nullSelectedChoiceProbabilityFallsBackBeforeGeneration(String)[2] | PASS |
| mainAnswerWithoutJevRecommendationHasNoGeneratedFollowUp | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[1] | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[2] | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[3] | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[4] | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[5] | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[6] | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[7] | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[8] | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[9] | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[10] | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[11] | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[12] | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[13] | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[14] | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[15] | PASS |
| invalidJevDecisionFallsBackBeforeGeneration(String)[16] | PASS |
| programmingFailurePropagatesWithoutFallback(String)[1] | PASS |
| programmingFailurePropagatesWithoutFallback(String)[2] | PASS |
| configuredLowConfidenceFallsBackBeforeGeneration | PASS |
| mapsJevDecisionsAndUsesDeepSeekOnlyForTextThenRetainsJavaFollowUpVeto | PASS |
| defaultZeroConfidenceThresholdDoesNotIntroduceAnUncalibratedPolicy | PASS |
| followUpAnswerUsesCombinedAnswersAndCannotAskAnotherFollowUp | PASS |
| timeoutFallsBackOnceToOriginalDeepSeekJudge | PASS |
| mergesAllScoringPointsByIdAndPreservesEveryJevScore(int)[1] | PASS |
| mergesAllScoringPointsByIdAndPreservesEveryJevScore(int)[2] | PASS |
| mergesAllScoringPointsByIdAndPreservesEveryJevScore(int)[3] | PASS |
| illegalGeneratedFeedbackFallsBackInsteadOfChangingJevDecisions(String)[1] | PASS |
| illegalGeneratedFeedbackFallsBackInsteadOfChangingJevDecisions(String)[2] | PASS |
| illegalGeneratedFeedbackFallsBackInsteadOfChangingJevDecisions(String)[3] | PASS |
| illegalGeneratedFeedbackFallsBackInsteadOfChangingJevDecisions(String)[4] | PASS |
| illegalGeneratedFeedbackFallsBackInsteadOfChangingJevDecisions(String)[5] | PASS |
| illegalGeneratedFeedbackFallsBackInsteadOfChangingJevDecisions(String)[6] | PASS |
| illegalGeneratedFeedbackFallsBackInsteadOfChangingJevDecisions(String)[7] | PASS |
| illegalGeneratedFeedbackFallsBackInsteadOfChangingJevDecisions(String)[8] | PASS |
| illegalGeneratedFeedbackFallsBackInsteadOfChangingJevDecisions(String)[9] | PASS |
| illegalGeneratedFeedbackFallsBackInsteadOfChangingJevDecisions(String)[10] | PASS |
| illegalGeneratedFeedbackFallsBackInsteadOfChangingJevDecisions(String)[11] | PASS |
| generationServiceFailureFallsBackToCompleteOriginalJudge | PASS |
| httpFailureFallsBackOnceToOriginalDeepSeekJudge(int)[1] | PASS |
| httpFailureFallsBackOnceToOriginalDeepSeekJudge(int)[2] | PASS |
| httpFailureFallsBackOnceToOriginalDeepSeekJudge(int)[3] | PASS |
| httpFailureFallsBackOnceToOriginalDeepSeekJudge(int)[4] | PASS |
| fallbackFailurePropagatesWithoutRecursingOrInventingScores | PASS |

### JevScoreMappingTest

| test invocation | result |
|---|---|
| mapsEndpointsAndHalfUpRounding(double, int)[1] | PASS |
| mapsEndpointsAndHalfUpRounding(double, int)[2] | PASS |
| mapsEndpointsAndHalfUpRounding(double, int)[3] | PASS |
| mapsEndpointsAndHalfUpRounding(double, int)[4] | PASS |
| mapsEndpointsAndHalfUpRounding(double, int)[5] | PASS |
| mapsEndpointsAndHalfUpRounding(double, int)[6] | PASS |
| mapsEndpointsAndHalfUpRounding(double, int)[7] | PASS |
| mapsEndpointsAndHalfUpRounding(double, int)[8] | PASS |
| mapsEndpointsAndHalfUpRounding(double, int)[9] | PASS |
| mapsEndpointsAndHalfUpRounding(double, int)[10] | PASS |
| rejectsInvalidScoresInsteadOfClamping(Double)[1] | PASS |
| rejectsInvalidScoresInsteadOfClamping(Double)[2] | PASS |
| rejectsInvalidScoresInsteadOfClamping(Double)[3] | PASS |
| rejectsInvalidScoresInsteadOfClamping(Double)[4] | PASS |
| rejectsInvalidScoresInsteadOfClamping(Double)[5] | PASS |
| rejectsInvalidScoresInsteadOfClamping(Double)[6] | PASS |

### JevScorePrecisionCompatibilityTest

| test invocation | result |
|---|---|
| respectsClippedFeasibleEndpointsAndUnitSumRatherThanAnAbsoluteBudget(int, double, boolean)[1] | PASS |
| respectsClippedFeasibleEndpointsAndUnitSumRatherThanAnAbsoluteBudget(int, double, boolean)[2] | PASS |
| respectsClippedFeasibleEndpointsAndUnitSumRatherThanAnAbsoluteBudget(int, double, boolean)[3] | PASS |
| respectsClippedFeasibleEndpointsAndUnitSumRatherThanAnAbsoluteBudget(int, double, boolean)[4] | PASS |
| respectsClippedFeasibleEndpointsAndUnitSumRatherThanAnAbsoluteBudget(int, double, boolean)[5] | PASS |
| respectsClippedFeasibleEndpointsAndUnitSumRatherThanAnAbsoluteBudget(int, double, boolean)[6] | PASS |
| respectsClippedFeasibleEndpointsAndUnitSumRatherThanAnAbsoluteBudget(int, double, boolean)[7] | PASS |
| respectsClippedFeasibleEndpointsAndUnitSumRatherThanAnAbsoluteBudget(int, double, boolean)[8] | PASS |
| respectsClippedFeasibleEndpointsAndUnitSumRatherThanAnAbsoluteBudget(int, double, boolean)[9] | PASS |
| respectsClippedFeasibleEndpointsAndUnitSumRatherThanAnAbsoluteBudget(int, double, boolean)[10] | PASS |
| respectsClippedFeasibleEndpointsAndUnitSumRatherThanAnAbsoluteBudget(int, double, boolean)[11] | PASS |
| respectsClippedFeasibleEndpointsAndUnitSumRatherThanAnAbsoluteBudget(int, double, boolean)[12] | PASS |
| rejectsScoresOutsideCapturedQuantizationIntervals(double)[1] | PASS |
| rejectsScoresOutsideCapturedQuantizationIntervals(double)[2] | PASS |
| rejectsScoresOutsideCapturedQuantizationIntervals(double)[3] | PASS |
| rejectsScoresOutsideCapturedQuantizationIntervals(double)[4] | PASS |
| doesNotExtendObservedDisplayAssumptionToAnotherReturnedModel(String)[1] | PASS |
| doesNotExtendObservedDisplayAssumptionToAnotherReturnedModel(String)[2] | PASS |
| doesNotExtendObservedDisplayAssumptionToAnotherReturnedModel(String)[3] | PASS |
| doesNotExtendObservedDisplayAssumptionToAnotherReturnedModel(String)[4] | PASS |
| doesNotExtendObservedDisplayAssumptionToAnotherReturnedModel(String)[5] | PASS |
| replaysAllFiveCapturedNumericAnswersWithoutChangingScoresOrProbabilities | PASS |
| rejectsAdversarialScoreThatIndependentErrorBudgetWouldAdmit | PASS |
| lowConfidenceStillTriggersExactlyOneFallbackBeforeFeedback | PASS |
| capturedScoreHasAnExplicitUnroundedProbabilityWitness | PASS |
| checksNearZeroOneClippingAndDecimalEndpointsWithoutExtraEpsilon(double, double, double, boolean)[1] | PASS |
| checksNearZeroOneClippingAndDecimalEndpointsWithoutExtraEpsilon(double, double, double, boolean)[2] | PASS |
| checksNearZeroOneClippingAndDecimalEndpointsWithoutExtraEpsilon(double, double, double, boolean)[3] | PASS |
| checksNearZeroOneClippingAndDecimalEndpointsWithoutExtraEpsilon(double, double, double, boolean)[4] | PASS |
| checksNearZeroOneClippingAndDecimalEndpointsWithoutExtraEpsilon(double, double, double, boolean)[5] | PASS |
| checksNearZeroOneClippingAndDecimalEndpointsWithoutExtraEpsilon(double, double, double, boolean)[6] | PASS |
| checksNearZeroOneClippingAndDecimalEndpointsWithoutExtraEpsilon(double, double, double, boolean)[7] | PASS |
| choiceArgmaxMustRemainConsistentEvenWhenScoresQualifyForCompatibility | PASS |
| preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String)[1] | PASS |
| preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String)[2] | PASS |
| preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String)[3] | PASS |
| preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String)[4] | PASS |
| preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String)[5] | PASS |
| preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String)[6] | PASS |
| preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String)[7] | PASS |
| preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String)[8] | PASS |
| preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String)[9] | PASS |
| preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String)[10] | PASS |
| preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String)[11] | PASS |
| preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String)[12] | PASS |
| preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String)[13] | PASS |
| preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String)[14] | PASS |
| preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String)[15] | PASS |
| acceptsEveryExactSinglePointDistributionIncludingZeroAndFour(int)[1] | PASS |
| acceptsEveryExactSinglePointDistributionIncludingZeroAndFour(int)[2] | PASS |
| acceptsEveryExactSinglePointDistributionIncludingZeroAndFour(int)[3] | PASS |
| acceptsEveryExactSinglePointDistributionIncludingZeroAndFour(int)[4] | PASS |
| acceptsEveryExactSinglePointDistributionIncludingZeroAndFour(int)[5] | PASS |
| preservesOriginalProbabilitySumConstraintEvenIfIntervalsHaveAUnitSumWitness(double)[1] | PASS |
| preservesOriginalProbabilitySumConstraintEvenIfIntervalsHaveAUnitSumWitness(double)[2] | PASS |
| preservesOriginalProbabilitySumConstraintEvenIfIntervalsHaveAUnitSumWitness(double)[3] | PASS |
| preservesOriginalProbabilitySumConstraintEvenIfIntervalsHaveAUnitSumWitness(double)[4] | PASS |
| rejectsNonFiniteTypedProbabilityBeforeCompatibility(double)[1] | PASS |
| rejectsNonFiniteTypedProbabilityBeforeCompatibility(double)[2] | PASS |
| rejectsNonFiniteTypedProbabilityBeforeCompatibility(double)[3] | PASS |
| higherPrecisionMismatchCannotBorrowTwoDecimalBudget(String)[1] | PASS |
| higherPrecisionMismatchCannotBorrowTwoDecimalBudget(String)[2] | PASS |
| preservesOriginalTightCheckForConsistentHigherPrecisionAndUnknownModels | PASS |

### LlmEvaluationValidatorTest

| test invocation | result |
|---|---|
| givenNullScoringPointId_whenValidate_thenRejects | PASS |
| givenMainFollowUpNotRecommendedButQuestionPresent_whenValidate_thenRejects | PASS |
| givenNullScoringPointResults_whenValidate_thenRejects | PASS |
| givenFollowUpAnswerRecommendsAnotherFollowUp_whenValidate_thenRejects | PASS |
| givenNullScoringPointResultItem_whenValidate_thenRejects | PASS |
| givenNullSuggestion_whenValidate_thenRejects | PASS |
| givenNullFollowUpRecommended_whenValidate_thenRejects | PASS |
| givenNullCoveredFlag_whenValidate_thenRejects | PASS |
| givenValidMainSuggestionWithFollowUp_whenValidate_thenReturnsNormalizedFollowUp | PASS |
| givenUnknownScoringPointId_whenValidate_thenRejects | PASS |
| givenNonPositiveScoringPointId_whenValidate_thenRejects(long)[1] | PASS |
| givenNonPositiveScoringPointId_whenValidate_thenRejects(long)[2] | PASS |
| givenMainFollowUpRecommendedWithoutQuestion_whenValidate_thenRejects(String)[1] | PASS |
| givenMainFollowUpRecommendedWithoutQuestion_whenValidate_thenRejects(String)[2] | PASS |
| givenMainFollowUpRecommendedWithoutQuestion_whenValidate_thenRejects(String)[3] | PASS |
| givenMainFollowUpRecommendedWithoutQuestion_whenValidate_thenRejects(String)[4] | PASS |
| givenNullContext_whenValidate_thenRejects | PASS |
| givenInvalidStrengthsOrMissingPoints_whenValidate_thenRejects(String, List, List)[1] | PASS |
| givenInvalidStrengthsOrMissingPoints_whenValidate_thenRejects(String, List, List)[2] | PASS |
| givenInvalidStrengthsOrMissingPoints_whenValidate_thenRejects(String, List, List)[3] | PASS |
| givenInvalidStrengthsOrMissingPoints_whenValidate_thenRejects(String, List, List)[4] | PASS |
| givenInvalidStrengthsOrMissingPoints_whenValidate_thenRejects(String, List, List)[5] | PASS |
| givenInvalidStrengthsOrMissingPoints_whenValidate_thenRejects(String, List, List)[6] | PASS |
| givenDuplicateScoringPointId_whenValidate_thenRejects | PASS |
| givenScoringPointResultCountMismatch_whenValidate_thenRejects | PASS |
| givenValidFollowUpSuggestionWithoutAnotherFollowUp_whenValidate_thenSucceeds | PASS |
| givenCoveredPointWithoutEvidence_whenValidate_thenRejects(String)[1] | PASS |
| givenCoveredPointWithoutEvidence_whenValidate_thenRejects(String)[2] | PASS |
| givenCoveredPointWithoutEvidence_whenValidate_thenRejects(String)[3] | PASS |
| givenCoveredPointWithoutEvidence_whenValidate_thenRejects(String)[4] | PASS |
| givenFollowUpAnswerReturnsQuestionWithoutRecommendation_whenValidate_thenRejects | PASS |
| givenInvalidDimensionScore_whenValidate_thenRejects(String, LlmEvaluationSuggestion, Class)[1] | PASS |
| givenInvalidDimensionScore_whenValidate_thenRejects(String, LlmEvaluationSuggestion, Class)[2] | PASS |
| givenInvalidDimensionScore_whenValidate_thenRejects(String, LlmEvaluationSuggestion, Class)[3] | PASS |
| givenInvalidDimensionScore_whenValidate_thenRejects(String, LlmEvaluationSuggestion, Class)[4] | PASS |
| givenInvalidDimensionScore_whenValidate_thenRejects(String, LlmEvaluationSuggestion, Class)[5] | PASS |
| givenInvalidDimensionScore_whenValidate_thenRejects(String, LlmEvaluationSuggestion, Class)[6] | PASS |
| givenInvalidDimensionScore_whenValidate_thenRejects(String, LlmEvaluationSuggestion, Class)[7] | PASS |
| givenInvalidDimensionScore_whenValidate_thenRejects(String, LlmEvaluationSuggestion, Class)[8] | PASS |
| givenInvalidDimensionScore_whenValidate_thenRejects(String, LlmEvaluationSuggestion, Class)[9] | PASS |
| givenInvalidDimensionScore_whenValidate_thenRejects(String, LlmEvaluationSuggestion, Class)[10] | PASS |
| givenInvalidDimensionScore_whenValidate_thenRejects(String, LlmEvaluationSuggestion, Class)[11] | PASS |
| givenInvalidDimensionScore_whenValidate_thenRejects(String, LlmEvaluationSuggestion, Class)[12] | PASS |
| givenInvalidDimensionScore_whenValidate_thenRejects(String, LlmEvaluationSuggestion, Class)[13] | PASS |
| givenInvalidDimensionScore_whenValidate_thenRejects(String, LlmEvaluationSuggestion, Class)[14] | PASS |
| givenInvalidDimensionScore_whenValidate_thenRejects(String, LlmEvaluationSuggestion, Class)[15] | PASS |
| givenValidMainSuggestionWithoutFollowUp_whenValidate_thenReturnsTrustedResult | PASS |

## 独立full response重放

```json
{
  "allFiveScoreDimensionsAccepted": true,
  "mysqlWrites": 0,
  "milvusWrites": 0,
  "businessScoreOrQualityEvaluationPerformed": false,
  "capturedResponseReplayAccepted": true,
  "originalScoresAndProbabilitiesUnchanged": true,
  "deepseekCalls": 0,
  "jevHttpCalls": 0
}
```

## 本次调用及写入

```text
JEV_HTTP_CALLS: 0
DEEPSEEK_CALLS: 0
EMBEDDING_CALLS: 0
MYSQL_WRITES: 0
MILVUS_WRITES: 0
RAG_TRACE_WRITES: 0
PHASE5B_ONLINE_EPOCH_STARTED: NO
PHASE5C_EXECUTED: NO
```

0来自所执行纯离线代码路径；没有新datasource/Milvus客户端，没有发起DB快照查询，不能将其误解为真实服务测试。

## 原文件before/after哈希

408个原文件受检，只有授权Provider修改，其余407个不变。新增测试和两报告为新增文件。

| file | before SHA-256 | after SHA-256 | result |
|---|---|---|---|
| src/main/resources/application-evaluation.yaml | 086d7960c88ba0e12250f2a4adac8b36f9d8bd76371598d1b459301a198495f2 | 086d7960c88ba0e12250f2a4adac8b36f9d8bd76371598d1b459301a198495f2 | UNCHANGED |
| src/main/resources/application-local.yaml | 2b00f9d59ee9a250047596d3df18d54e8b9f16c57bd5852408d5999859737713 | 2b00f9d59ee9a250047596d3df18d54e8b9f16c57bd5852408d5999859737713 | UNCHANGED |
| src/main/resources/application.yaml | 52dfccdaf61fc3b8f27388c6c16956508a67dca4a98b4ab97c6ef9b4d7a7a5cc | 52dfccdaf61fc3b8f27388c6c16956508a67dca4a98b4ab97c6ef9b4d7a7a5cc | UNCHANGED |
| src/main/resources/mapper/AnswerEvaluationMapper.xml | 7717f1f96e5895e1be435a16e214702c26a93348d88bc126b97b16fbfe8a1dea | 7717f1f96e5895e1be435a16e214702c26a93348d88bc126b97b16fbfe8a1dea | UNCHANGED |
| src/main/resources/mapper/InterviewAnswerMapper.xml | 5d41dddbcde8a2795fd3e9949cdae87a7f5babbbdbdee9f0430c6b1ed34f82b9 | 5d41dddbcde8a2795fd3e9949cdae87a7f5babbbdbdee9f0430c6b1ed34f82b9 | UNCHANGED |
| src/main/resources/mapper/InterviewQuestionMapper.xml | cd08b2c6d7883a2a3233c55cb87d2f06bc7a477d6330c7241a2760ffe0a2a7ec | cd08b2c6d7883a2a3233c55cb87d2f06bc7a477d6330c7241a2760ffe0a2a7ec | UNCHANGED |
| src/main/resources/mapper/InterviewReportMapper.xml | f116900a441a1bfe959fc981f1750ad0ab0f52a435a8b3723591f3315ddca986 | f116900a441a1bfe959fc981f1750ad0ab0f52a435a8b3723591f3315ddca986 | UNCHANGED |
| src/main/resources/mapper/InterviewSessionMapper.xml | 1fa41f4ba31aeba68b34c8de888ff8d4ca19172516d3adc68cfc7a43bee9477f | 1fa41f4ba31aeba68b34c8de888ff8d4ca19172516d3adc68cfc7a43bee9477f | UNCHANGED |
| src/main/resources/mapper/KnowledgeChunkMapper.xml | 63889346dce0bb981b7df9132f78aa976e07f7e942f06b7778cda4a6a000e2dc | 63889346dce0bb981b7df9132f78aa976e07f7e942f06b7778cda4a6a000e2dc | UNCHANGED |
| src/main/resources/mapper/KnowledgeDocumentMapper.xml | 94dbd97927261d779d0e2b12d2e3b8229018e1d13ff2cff958ffafbdcf86691a | 94dbd97927261d779d0e2b12d2e3b8229018e1d13ff2cff958ffafbdcf86691a | UNCHANGED |
| src/main/resources/mapper/QuestionMapper.xml | f0c2cddb90793d5054bb7ac24cb2f83fe9ce2a6999bb3c9a522bc44451bdcd19 | f0c2cddb90793d5054bb7ac24cb2f83fe9ce2a6999bb3c9a522bc44451bdcd19 | UNCHANGED |
| src/main/resources/mapper/QuestionScoringPointMapper.xml | 2a8498bc9e588a6afed0bcf700280d067e587f255147047659ebd6f658d2cb41 | 2a8498bc9e588a6afed0bcf700280d067e587f255147047659ebd6f658d2cb41 | UNCHANGED |
| src/main/resources/mapper/RagHitLogMapper.xml | c8c0d5dac7be020947feabc9b12142aca81badabd3ea96c46877de8e9f65db4d | c8c0d5dac7be020947feabc9b12142aca81badabd3ea96c46877de8e9f65db4d | UNCHANGED |
| src/main/resources/mapper/RagRetrievalBatchMapper.xml | ab2caa86cf1b4bf672d524ecc6e1e349fd1cebcf37c71b9b4219ff07b4fdcedc | ab2caa86cf1b4bf672d524ecc6e1e349fd1cebcf37c71b9b4219ff07b4fdcedc | UNCHANGED |
| src/main/resources/mapper/UserMapper.xml | 3e9d0af51db5dedf10cc2835da65a9daad9159f4b4f6f773ba530e342e4a7dbe | 3e9d0af51db5dedf10cc2835da65a9daad9159f4b4f6f773ba530e342e4a7dbe | UNCHANGED |
| src/main/resources/mapper/UserWeaknessMapper.xml | 6647405c4bf852469d5a832c547a40d3198d0ba5f49123025701b9fb7f7a28de | 6647405c4bf852469d5a832c547a40d3198d0ba5f49123025701b9fb7f7a28de | UNCHANGED |
| src/main/resources/META-INF/spring.factories | aa6d69b0b333762ffb93995da9db734c024d7986d35eddd8c3c00923ce8a2bcc | aa6d69b0b333762ffb93995da9db734c024d7986d35eddd8c3c00923ce8a2bcc | UNCHANGED |
| src/main/java/com/kun/aiinterview/AiInterviewSystemApplication.java | ab7342a63c8a746f801ce2bd6861c062f988fcb43e529a2e38ed7a7e16632e42 | ab7342a63c8a746f801ce2bd6861c062f988fcb43e529a2e38ed7a7e16632e42 | UNCHANGED |
| src/main/java/com/kun/aiinterview/config/EnvironmentIsolationInitializer.java | a4de695816d6a111dae040219017da340653b7bc3f8735bad76f03825e8e9bce | a4de695816d6a111dae040219017da340653b7bc3f8735bad76f03825e8e9bce | UNCHANGED |
| src/main/java/com/kun/aiinterview/config/PasswordConfig.java | f8489500d9cd71915c8016a38407608e3802252055e8e5a1e7a47cd2ea95a510 | f8489500d9cd71915c8016a38407608e3802252055e8e5a1e7a47cd2ea95a510 | UNCHANGED |
| src/main/java/com/kun/aiinterview/user/controller/UserController.java | 3a0dbd37c5980bf66a6f81de3566c637a559b1e52abbab2b5a9cf1e36a0dbf68 | 3a0dbd37c5980bf66a6f81de3566c637a559b1e52abbab2b5a9cf1e36a0dbf68 | UNCHANGED |
| src/main/java/com/kun/aiinterview/user/dto/ChangePasswordRequest.java | 417fecb22f08d9ea49867daf46cc1e994629dde6187a15c23bd5dd19cb9ce161 | 417fecb22f08d9ea49867daf46cc1e994629dde6187a15c23bd5dd19cb9ce161 | UNCHANGED |
| src/main/java/com/kun/aiinterview/user/entity/User.java | a1cd95883365d2eed14eb0f3641fe9e484f49c798b16faee758cccf85c5e36ea | a1cd95883365d2eed14eb0f3641fe9e484f49c798b16faee758cccf85c5e36ea | UNCHANGED |
| src/main/java/com/kun/aiinterview/user/entity/UserWeakness.java | 74cddce2eec451b33d9793b9532e3de4a6a2dd91415eb0af86662577984234d8 | 74cddce2eec451b33d9793b9532e3de4a6a2dd91415eb0af86662577984234d8 | UNCHANGED |
| src/main/java/com/kun/aiinterview/user/enums/UserRole.java | 40d038f8da5234070bc2e87617d66eeaa66cb6dd7c0e88e4b52c8a642a9a0723 | 40d038f8da5234070bc2e87617d66eeaa66cb6dd7c0e88e4b52c8a642a9a0723 | UNCHANGED |
| src/main/java/com/kun/aiinterview/user/enums/UserStatus.java | 338143078aed977d27b7de38c4416de0f8d8db6fa364f25c13c20f2b521c8578 | 338143078aed977d27b7de38c4416de0f8d8db6fa364f25c13c20f2b521c8578 | UNCHANGED |
| src/main/java/com/kun/aiinterview/user/enums/UserWeaknessStatus.java | 318c6a454614e24e4e59d92c0fbcb5d5aea3db235eaf4959e5b10e16f09595ae | 318c6a454614e24e4e59d92c0fbcb5d5aea3db235eaf4959e5b10e16f09595ae | UNCHANGED |
| src/main/java/com/kun/aiinterview/user/mapper/UserMapper.java | fb42447ac472fc6ab51af148a74fb41aea64a7eaaa2b1574970dfb4effc290bf | fb42447ac472fc6ab51af148a74fb41aea64a7eaaa2b1574970dfb4effc290bf | UNCHANGED |
| src/main/java/com/kun/aiinterview/user/mapper/UserWeaknessMapper.java | cfe8604c3737b6813fafc069ec2d4b92fda9184eaca7afc6f2b6a99c537f8290 | cfe8604c3737b6813fafc069ec2d4b92fda9184eaca7afc6f2b6a99c537f8290 | UNCHANGED |
| src/main/java/com/kun/aiinterview/user/service/UserService.java | 9e9c7fbc5e38eed5d39a4e00c2e66c1d953b2dbf0cb26b61c8e67a62e13d2c5e | 9e9c7fbc5e38eed5d39a4e00c2e66c1d953b2dbf0cb26b61c8e67a62e13d2c5e | UNCHANGED |
| src/main/java/com/kun/aiinterview/user/service/UserWeaknessService.java | 483ed97bf3fc9ca7e2d45fb8c78d178cdc0a6a6f669b47e785bfb289910cb1d0 | 483ed97bf3fc9ca7e2d45fb8c78d178cdc0a6a6f669b47e785bfb289910cb1d0 | UNCHANGED |
| src/main/java/com/kun/aiinterview/user/vo/CurrentUserResponse.java | e0cd51085dce318c36641f9a4a8579c152720d2b5b0cf23e0c68e9086afb81ed | e0cd51085dce318c36641f9a4a8579c152720d2b5b0cf23e0c68e9086afb81ed | UNCHANGED |
| src/main/java/com/kun/aiinterview/security/config/SecurityConfiguration.java | 8aa9c0da2075fb3711f92c81db145dfc0f2fe6b2377174c4a547a67e47b1caa5 | 8aa9c0da2075fb3711f92c81db145dfc0f2fe6b2377174c4a547a67e47b1caa5 | UNCHANGED |
| src/main/java/com/kun/aiinterview/security/filter/JwtAuthenticationFilter.java | 67eed7da8ec109f0e05c7fa1949b706098fb1f8a026c75ec8260b9d6a4932efa | 67eed7da8ec109f0e05c7fa1949b706098fb1f8a026c75ec8260b9d6a4932efa | UNCHANGED |
| src/main/java/com/kun/aiinterview/security/handler/RestAccessDeniedHandler.java | bea54d72b93db347b1a9f6c24b894dd2b855d03be933f18def315b08ab5f785e | bea54d72b93db347b1a9f6c24b894dd2b855d03be933f18def315b08ab5f785e | UNCHANGED |
| src/main/java/com/kun/aiinterview/security/handler/RestAuthenticationEntryPoint.java | b99c11eb309d04efa6647ae03f05cce62910d2592d32c3a9b25b44932760fe36 | b99c11eb309d04efa6647ae03f05cce62910d2592d32c3a9b25b44932760fe36 | UNCHANGED |
| src/main/java/com/kun/aiinterview/security/jwt/JwtConfiguration.java | f91e71c22e0ce642011903e8892af516fd13ea710511b99e78b042a4ccde194e | f91e71c22e0ce642011903e8892af516fd13ea710511b99e78b042a4ccde194e | UNCHANGED |
| src/main/java/com/kun/aiinterview/security/jwt/JwtProperties.java | 4d00e32e29d7c370877e59dcec6c7c83e44e4aa9fd4bf66d6dfea9c829208601 | 4d00e32e29d7c370877e59dcec6c7c83e44e4aa9fd4bf66d6dfea9c829208601 | UNCHANGED |
| src/main/java/com/kun/aiinterview/security/jwt/JwtTokenService.java | df6f1d62dc27564170b7180d5b2f3dab09a2b5da4415ed5c20b35559c25cae6b | df6f1d62dc27564170b7180d5b2f3dab09a2b5da4415ed5c20b35559c25cae6b | UNCHANGED |
| src/main/java/com/kun/aiinterview/security/model/AuthenticatedUser.java | 5e74646514e76596c6f4293227b35ce700e5fd6f1a45a7416b26b7cee85062a8 | 5e74646514e76596c6f4293227b35ce700e5fd6f1a45a7416b26b7cee85062a8 | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/controller/AdminQuestionController.java | 3fe42833edcaefdd81cf8602038034f4af4fdf37bf01cd3e4e76ebc0863e48e9 | 3fe42833edcaefdd81cf8602038034f4af4fdf37bf01cd3e4e76ebc0863e48e9 | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/dto/CreateQuestionRequest.java | 8e658eb598c3c0c774519a585654d34a7f632d0e575bd1df05936618f2b5056a | 8e658eb598c3c0c774519a585654d34a7f632d0e575bd1df05936618f2b5056a | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/dto/QuestionPageQuery.java | cc89555667ce900a793a2e991094ac2d9d095ccd6808c621f4337f2cb1fb7583 | cc89555667ce900a793a2e991094ac2d9d095ccd6808c621f4337f2cb1fb7583 | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/dto/ScoringPointRequest.java | b275c93953281d7c092eb124835e1d30939c496580a420c0b9cb50b84477d34c | b275c93953281d7c092eb124835e1d30939c496580a420c0b9cb50b84477d34c | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/dto/UpdateQuestionRequest.java | 7a99c8f0ce7f8b1f657b1dacdbfe0b7530e7f2f3aae1107b0e57b79780f369d1 | 7a99c8f0ce7f8b1f657b1dacdbfe0b7530e7f2f3aae1107b0e57b79780f369d1 | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/dto/UpdateQuestionStatusRequest.java | 80dafd0ee37be93993bd577550d92a8da51245b27de06dd38b34174d4aec6e78 | 80dafd0ee37be93993bd577550d92a8da51245b27de06dd38b34174d4aec6e78 | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/entity/Question.java | 5ab3954af6b717cd8ff0c103ed5be3da3906c67e5b7abd100bcc36a7a0970c06 | 5ab3954af6b717cd8ff0c103ed5be3da3906c67e5b7abd100bcc36a7a0970c06 | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/entity/QuestionScoringPoint.java | 1e4ce99e5c3e974536ac4444c52da93bd8208d9eb28e9b667807114fddf88e4e | 1e4ce99e5c3e974536ac4444c52da93bd8208d9eb28e9b667807114fddf88e4e | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/enums/QuestionCategory.java | 3bc76e4781afa7a3b14d1cfe92187e010c9d9a7575efec8f1b550d794b7fe733 | 3bc76e4781afa7a3b14d1cfe92187e010c9d9a7575efec8f1b550d794b7fe733 | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/enums/QuestionDifficulty.java | b22cc86e58c974d2fb6c152057b0e9bb28b98838744134980718ed9fdd9e3041 | b22cc86e58c974d2fb6c152057b0e9bb28b98838744134980718ed9fdd9e3041 | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/enums/QuestionPointStatus.java | f19e90760eeee4c82192ff6fd3a71ba6412b33edd02b4d2190b6ce947051aec2 | f19e90760eeee4c82192ff6fd3a71ba6412b33edd02b4d2190b6ce947051aec2 | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/enums/QuestionPointType.java | d2a0a25452e419a89ecdce3c69a7e2bbb7e858a9630b252323bd7a0ac2cf3190 | d2a0a25452e419a89ecdce3c69a7e2bbb7e858a9630b252323bd7a0ac2cf3190 | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/enums/QuestionStatus.java | 3a02c118c4377a3e62ccf2ea2535343b1b6fee82ad7c52f96f31d03db303cc51 | 3a02c118c4377a3e62ccf2ea2535343b1b6fee82ad7c52f96f31d03db303cc51 | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/mapper/QuestionMapper.java | dc1561cc3f49608e1f6a11d3f69ef23588069688b2b36010c61a1983e70cb737 | dc1561cc3f49608e1f6a11d3f69ef23588069688b2b36010c61a1983e70cb737 | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/mapper/QuestionScoringPointMapper.java | 17886bb505b5834184d48142695768295b6a20a1835c4af98a2a7a6892ea935a | 17886bb505b5834184d48142695768295b6a20a1835c4af98a2a7a6892ea935a | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/service/QuestionAdminService.java | 537ba2aebce511d3f1be6f34468edd37ccd2ca4704704286bd2f2e0fcb0d31e8 | 537ba2aebce511d3f1be6f34468edd37ccd2ca4704704286bd2f2e0fcb0d31e8 | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/vo/AdminQuestionDetailResponse.java | d51c61ddaa05508e412bac49c374bf68bc6bb29cdebfb4b96a967d2c76888c6f | d51c61ddaa05508e412bac49c374bf68bc6bb29cdebfb4b96a967d2c76888c6f | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/vo/AdminQuestionListItem.java | 2dc5320c160354eba2f8784741fd72f0d99d4f5e04ca68918bf76a59e36ce000 | 2dc5320c160354eba2f8784741fd72f0d99d4f5e04ca68918bf76a59e36ce000 | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/vo/AdminQuestionPageResponse.java | 23aee8bc2d64f330098a151d61f6e637ec4c7cac695251adebc1f1baf923b42a | 23aee8bc2d64f330098a151d61f6e637ec4c7cac695251adebc1f1baf923b42a | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/vo/AdminScoringPointDetail.java | ed281745d7aff9a9dfd58ce22d5ffcb46dbf8139b9379a125551d7418ab540c6 | ed281745d7aff9a9dfd58ce22d5ffcb46dbf8139b9379a125551d7418ab540c6 | UNCHANGED |
| src/main/java/com/kun/aiinterview/question/vo/CreateQuestionResponse.java | 83f649ce8596e888c39e4290239edcc0f652b6bff4288eb246ed4d4073370f39 | 83f649ce8596e888c39e4290239edcc0f652b6bff4288eb246ed4d4073370f39 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/chunk/KnowledgeChunkDraft.java | 4b8a82d9c8a377aa86027bf1dc37080c6be3a93322a95b283bd8fc6d7a2af69a | 4b8a82d9c8a377aa86027bf1dc37080c6be3a93322a95b283bd8fc6d7a2af69a | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/chunk/KnowledgeTextChunker.java | c98f2bcc46c14f9ef7216f1e6b42512338ff088d587925e7bef569565d4aa406 | c98f2bcc46c14f9ef7216f1e6b42512338ff088d587925e7bef569565d4aa406 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/controller/AdminKnowledgeDocumentController.java | ea32c36d5da75025245d86a0d43f83ad5ea2bd532c1cefbad332cf64f60c019b | ea32c36d5da75025245d86a0d43f83ad5ea2bd532c1cefbad332cf64f60c019b | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/dto/UploadKnowledgeDocumentRequest.java | 8e5dfd3eedaa36b2b43588b82419bdc9200e8ff3ee121dd92b7cfc33e434e57e | 8e5dfd3eedaa36b2b43588b82419bdc9200e8ff3ee121dd92b7cfc33e434e57e | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/embedding/EmbeddingBatchResult.java | 753bb273c16c9e20b6fcd2a62b7089d19a0d5628ba9052576487be391f3567de | 753bb273c16c9e20b6fcd2a62b7089d19a0d5628ba9052576487be391f3567de | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/embedding/EmbeddingClient.java | 41f10f6f6bc25261d4a762e14b19b4bdd591c0fd672121c97476addba1493c71 | 41f10f6f6bc25261d4a762e14b19b4bdd591c0fd672121c97476addba1493c71 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/embedding/EmbeddingConfiguration.java | a3a17632bb4b0ca72bf29eeea478946367f198b03b7be8ef26c3d7ff8126aa52 | a3a17632bb4b0ca72bf29eeea478946367f198b03b7be8ef26c3d7ff8126aa52 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/embedding/EmbeddingProperties.java | 704899d5982aa05d8e97986ac2ebd960bea77a6932ab8447c0b45d3408856d35 | 704899d5982aa05d8e97986ac2ebd960bea77a6932ab8447c0b45d3408856d35 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/embedding/EmbeddingVector.java | b3778c27266e21a8919a3bff2211861ca54df053a38bb2d55c1ac8eab1d0fd1a | b3778c27266e21a8919a3bff2211861ca54df053a38bb2d55c1ac8eab1d0fd1a | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/entity/KnowledgeChunk.java | 53b7d342c9c633321fd2ad026557a0632790df95ce012f4fe7baadec3e0699d8 | 53b7d342c9c633321fd2ad026557a0632790df95ce012f4fe7baadec3e0699d8 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/entity/KnowledgeDocument.java | 59cd64e67c6df3521dfb559ea23f5a6955c41c7bc6804be9f13070ab287de257 | 59cd64e67c6df3521dfb559ea23f5a6955c41c7bc6804be9f13070ab287de257 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/entity/RagHitLog.java | 5d198a9f538e9637c8da7fccc4da6817e7ef9b2aeb55c94bb8edcf5631acef86 | 5d198a9f538e9637c8da7fccc4da6817e7ef9b2aeb55c94bb8edcf5631acef86 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/entity/RagRetrievalBatch.java | 09a09048e1f8aadfae6b2d70e564ec5450b7e4703b3a4b0ec77f60f221088194 | 09a09048e1f8aadfae6b2d70e564ec5450b7e4703b3a4b0ec77f60f221088194 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/enums/KnowledgeCategory.java | 613aee7b81004d9e80fcfbe2465b36efd1d129ebb18d831b9f35f5b724f13ac3 | 613aee7b81004d9e80fcfbe2465b36efd1d129ebb18d831b9f35f5b724f13ac3 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/enums/KnowledgeChunkStatus.java | a3b890b8e7506c4ceff041c8c4f02ee151cd91fe4df2b97d8feaf8e7a53e5481 | a3b890b8e7506c4ceff041c8c4f02ee151cd91fe4df2b97d8feaf8e7a53e5481 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/enums/KnowledgeFileType.java | 0c60e8b7d9146ced33d95d987128ea8e96ef3fc60141fa511a76218c0b13700b | 0c60e8b7d9146ced33d95d987128ea8e96ef3fc60141fa511a76218c0b13700b | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/enums/KnowledgeProcessingStatus.java | b9958aec40848c39d3085fad2260ed19e1d6d3f5bf96a1a4b67200e617d3b788 | b9958aec40848c39d3085fad2260ed19e1d6d3f5bf96a1a4b67200e617d3b788 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/mapper/KnowledgeChunkMapper.java | dc1d633689fe21b1891acea6ad02f375f1a394ce9068d90aed9048430350bc44 | dc1d633689fe21b1891acea6ad02f375f1a394ce9068d90aed9048430350bc44 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/mapper/KnowledgeDocumentMapper.java | 3776a2a0b6f8161bc697dc6bfee2f2d41ae641696dd8005a0f0d9895c261caf1 | 3776a2a0b6f8161bc697dc6bfee2f2d41ae641696dd8005a0f0d9895c261caf1 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/mapper/RagHitLogMapper.java | 56ac548aa4ec041c755b97dd923d92aa441300b640e20bb830e0bc9543b7f66a | 56ac548aa4ec041c755b97dd923d92aa441300b640e20bb830e0bc9543b7f66a | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/mapper/RagRetrievalBatchMapper.java | 1e2d0659c6feb7060dc8aeee78d9cc272ad401774af143d6bedfccedbf2e477e | 1e2d0659c6feb7060dc8aeee78d9cc272ad401774af143d6bedfccedbf2e477e | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/retrieval/KnowledgeRetrievalRow.java | f629a6bc4b1191accb26c6932a37a6bdbd7d4d501433f9548d5ed3494d91543d | f629a6bc4b1191accb26c6932a37a6bdbd7d4d501433f9548d5ed3494d91543d | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/retrieval/RetrievalResult.java | c4f1f2c69a359578e661fd11ae0ea30aa80a37407158319313e97ac170c05cfc | c4f1f2c69a359578e661fd11ae0ea30aa80a37407158319313e97ac170c05cfc | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/retrieval/RetrievedChunk.java | ac57e046b91eb5da756b732d049bfaf1c3a94d7b44211976aebeeda13f735e22 | ac57e046b91eb5da756b732d049bfaf1c3a94d7b44211976aebeeda13f735e22 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeDocumentAdminService.java | b96fb9ade6479cd6c9074b3a3f9b81848dc045dad8e59eebfb8b59dfa312b3ba | b96fb9ade6479cd6c9074b3a3f9b81848dc045dad8e59eebfb8b59dfa312b3ba | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeDocumentProcessingService.java | ca9abc76312e02374a655bb6fe8b9ae2f2d32f02c4b3c06a5bd6e6014096d937 | ca9abc76312e02374a655bb6fe8b9ae2f2d32f02c4b3c06a5bd6e6014096d937 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeDocumentProcessingTransactionService.java | bdbb9464ae49b55f626b68643f4db087aa41d47ae4833457ea42644e223313ed | bdbb9464ae49b55f626b68643f4db087aa41d47ae4833457ea42644e223313ed | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalService.java | 45b1707f9b1b9c1462e8cd3cdf575258edc97d3c0051d74164d9c44c711aa70c | 45b1707f9b1b9c1462e8cd3cdf575258edc97d3c0051d74164d9c44c711aa70c | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/service/RagTracePersistenceService.java | 83d6301aaf7ac258a502abf68c4e84224f11946a55713be59e534240d5af15f8 | 83d6301aaf7ac258a502abf68c4e84224f11946a55713be59e534240d5af15f8 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/vector/VectorSearchHit.java | c9d070eddd0a7cfcd2248895652a6b162270430721a222033103147c7698ff7c | c9d070eddd0a7cfcd2248895652a6b162270430721a222033103147c7698ff7c | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/vector/VectorStoreClient.java | 6a054da9a3277c1dc8df7547cdda12c4b2ac2f90eaf14d8c9eda9aa487c0a0c8 | 6a054da9a3277c1dc8df7547cdda12c4b2ac2f90eaf14d8c9eda9aa487c0a0c8 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/vector/VectorWriteItem.java | e83384fdf5042d9bfa5abc238fa483a4560a3cc5c8ff5c087eacf777843258c2 | e83384fdf5042d9bfa5abc238fa483a4560a3cc5c8ff5c087eacf777843258c2 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/vo/UploadKnowledgeDocumentResponse.java | 22512e430a210bd3967051f815018fde140fc607e711bdb7ff333f0a0f1a2a9d | 22512e430a210bd3967051f815018fde140fc607e711bdb7ff333f0a0f1a2a9d | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusCollectionInitializer.java | 57d675fce5cdd9652ca709fe7378b4ff5e2447b15a12a4a202bdb63ff8a2b9dd | 57d675fce5cdd9652ca709fe7378b4ff5e2447b15a12a4a202bdb63ff8a2b9dd | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusConfiguration.java | 15dcca7ac419f5568e028498e2d9ef0fd3a4d72b1bcdb3fe35f3cf4c4838c53b | 15dcca7ac419f5568e028498e2d9ef0fd3a4d72b1bcdb3fe35f3cf4c4838c53b | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusProperties.java | 2c606ee9c958c8b2b595143d2a92cbeac7e55c25ebf871256c2c875a1b58c57f | 2c606ee9c958c8b2b595143d2a92cbeac7e55c25ebf871256c2c875a1b58c57f | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusSchemaConstants.java | b3987ab84654f32029f6c261a00378b58220400ade1019908481829d59f086e6 | b3987ab84654f32029f6c261a00378b58220400ade1019908481829d59f086e6 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusVectorStoreClient.java | 5c585e380420a4b7d90850e42c59eae0ab7c4888072645803bc59039058d91d1 | 5c585e380420a4b7d90850e42c59eae0ab7c4888072645803bc59039058d91d1 | UNCHANGED |
| src/main/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingClient.java | ebd342809aea17c48721d8f57777aaea939e10ca66e8a83c3e0b35836b1d4642 | ebd342809aea17c48721d8f57777aaea939e10ca66e8a83c3e0b35836b1d4642 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/config/InterviewPlanConfig.java | f74090eb0b37a15c34a02de4d1629be9d9171defbfaf8327d274379f45873635 | f74090eb0b37a15c34a02de4d1629be9d9171defbfaf8327d274379f45873635 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/controller/InterviewController.java | d4c688d3d9644e27f96dacc3a5572ff0eb6cc4dda3ecb7544f4d81b240a90e2e | d4c688d3d9644e27f96dacc3a5572ff0eb6cc4dda3ecb7544f4d81b240a90e2e | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/dto/CreateInterviewRequest.java | 84cac12f60e35dba5e05e86f9452272a4c4d49f738eb9372baeee4e8494af8e4 | 84cac12f60e35dba5e05e86f9452272a4c4d49f738eb9372baeee4e8494af8e4 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/dto/SubmitInterviewAnswerRequest.java | a77854dcb9e265336663bf9a71f7dbf1f1da1ebd28ea006104d5d2d1cc5edd72 | a77854dcb9e265336663bf9a71f7dbf1f1da1ebd28ea006104d5d2d1cc5edd72 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/entity/AnswerEvaluation.java | b3792ba87dc2091da1fd481fb9e1515e882238e8422c2220ceffec092eb38d5b | b3792ba87dc2091da1fd481fb9e1515e882238e8422c2220ceffec092eb38d5b | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/entity/InterviewAnswer.java | 9fe66c1811976f4420ff6f05d9b0ead9ccd6f3d2918aee3962aee846dda8ae05 | 9fe66c1811976f4420ff6f05d9b0ead9ccd6f3d2918aee3962aee846dda8ae05 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/entity/InterviewQuestion.java | 9fefddeb46422678b58b4bc75d26bda23e7a9e2ba535a550b398d69f69f5c170 | 9fefddeb46422678b58b4bc75d26bda23e7a9e2ba535a550b398d69f69f5c170 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/entity/InterviewReport.java | ea68ff4752f59876c716307414f20adc526996e0e58e49c8d3d24340e3bd7982 | ea68ff4752f59876c716307414f20adc526996e0e58e49c8d3d24340e3bd7982 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/entity/InterviewSession.java | 96db948791133db25808a1e993a9634b801849d33291b063d7ce701bee25978f | 96db948791133db25808a1e993a9634b801849d33291b063d7ce701bee25978f | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/enums/DecisionAction.java | afc9a035b49552dad04608c59184176612c6777b9a6082bb521053c3834cefb4 | afc9a035b49552dad04608c59184176612c6777b9a6082bb521053c3834cefb4 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/enums/EvaluationPhase.java | a583be4ba033ae667c3dd24196f53e522ccb748df36f5d3b19f2f434c1123fdc | a583be4ba033ae667c3dd24196f53e522ccb748df36f5d3b19f2f434c1123fdc | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/enums/InterviewAnswerStatus.java | e6d2817b22228ecec2334bfab7df24eec99cf21d688b4c7879045bc99b90dac5 | e6d2817b22228ecec2334bfab7df24eec99cf21d688b4c7879045bc99b90dac5 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/enums/InterviewQuestionStatus.java | 7703a3e58c660ad63adf0dd92a2a02f1ac6512c68254b5fa5d338688600411fa | 7703a3e58c660ad63adf0dd92a2a02f1ac6512c68254b5fa5d338688600411fa | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/enums/InterviewQuestionType.java | e7d805969fad5575f899ea4f817af8773e6f94db9a8a1d02e137b88f8eca8acd | e7d805969fad5575f899ea4f817af8773e6f94db9a8a1d02e137b88f8eca8acd | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/enums/InterviewReportStatus.java | ce110e71d9c408ba85ac54fde59d56f6cb93a984376cdbc00abe8f9f6f328044 | ce110e71d9c408ba85ac54fde59d56f6cb93a984376cdbc00abe8f9f6f328044 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/enums/InterviewSessionStatus.java | d1663087b92ed7ae3bbdeaa57b7396ab7547b3de2ea7229252fcf0702cff0d12 | d1663087b92ed7ae3bbdeaa57b7396ab7547b3de2ea7229252fcf0702cff0d12 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationContext.java | 699c9f4eb02ff10619c5500686fc2cfad000d8e8b528bc3a929ac144e410f1ed | 699c9f4eb02ff10619c5500686fc2cfad000d8e8b528bc3a929ac144e410f1ed | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationMode.java | 94aacddd3ae665c6feeb69709a71bfcc07e93340cfbddc6b106981f9b774edf2 | 94aacddd3ae665c6feeb69709a71bfcc07e93340cfbddc6b106981f9b774edf2 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRagQueryBuilder.java | 2866ca2aeccd9c7da473a31107e4b9a87d298bf4b0495482715c262a115bd97c | 2866ca2aeccd9c7da473a31107e4b9a87d298bf4b0495482715c262a115bd97c | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRetrievalAdapter.java | 9aa5a8abc76d4f74169283c388ca2e587684d0e2f5f088ecfb790ef1ad5b2cda | 9aa5a8abc76d4f74169283c388ca2e587684d0e2f5f088ecfb790ef1ad5b2cda | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRetrievalResult.java | 50a2ece07eb178f5c623326ca668c25c6c192fe342e02a9261fdda1aa953e8c6 | 50a2ece07eb178f5c623326ca668c25c6c192fe342e02a9261fdda1aa953e8c6 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/ScoringPointSnapshot.java | 0c6e898218b223ddc0e115d12f01fdc83dc57d25f777a7c4a6884fdb35fdf104 | 0c6e898218b223ddc0e115d12f01fdc83dc57d25f777a7c4a6884fdb35fdf104 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/mapper/AnswerEvaluationMapper.java | a1959add38faf3fc37d7e5e833cde7146c367b9fddbce6330e4cffff0bc5871c | a1959add38faf3fc37d7e5e833cde7146c367b9fddbce6330e4cffff0bc5871c | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/mapper/InterviewAnswerMapper.java | 19ba1c9b9e07eba9cca5b5d096262b8a90f89e1655934cb3729d9dbf5a309a31 | 19ba1c9b9e07eba9cca5b5d096262b8a90f89e1655934cb3729d9dbf5a309a31 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/mapper/InterviewQuestionMapper.java | fd56ec5f547875a8698a6a290df1c1b6044c49adf038c957ae80ab68a2e1a1c6 | fd56ec5f547875a8698a6a290df1c1b6044c49adf038c957ae80ab68a2e1a1c6 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/mapper/InterviewReportMapper.java | 960eb67bdfeb974713ef04cdd0a08af770bb97ccd2c25946098d8277eee1930a | 960eb67bdfeb974713ef04cdd0a08af770bb97ccd2c25946098d8277eee1930a | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/mapper/InterviewSessionMapper.java | 25101959424c9a0f7467f6bf31fd619e1d3a6c9c7d544bffac908be208721737 | 25101959424c9a0f7467f6bf31fd619e1d3a6c9c7d544bffac908be208721737 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/model/InterviewMainQuestionDraft.java | cfd8d59b37c9e9f1ec88b6746ae22fe80a09e76431d6ef4df8cc29a64fe585e4 | cfd8d59b37c9e9f1ec88b6746ae22fe80a09e76431d6ef4df8cc29a64fe585e4 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/model/InterviewReportGenerationResult.java | 80f5df7ec311aa471f541f7226e1b39e4f66ca6f171bebd12f4504a2369f75d3 | 80f5df7ec311aa471f541f7226e1b39e4f66ca6f171bebd12f4504a2369f75d3 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/orchestration/EvaluationOrchestrationResult.java | 88f9cf624c8a7bb1a4d0cb6603648edd66083ed4d7c858856db681057e67d5ee | 88f9cf624c8a7bb1a4d0cb6603648edd66083ed4d7c858856db681057e67d5ee | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/orchestration/EvaluationOrchestrationService.java | 52858c7ed80fcc8015e81a45eb3b33cb2d9c5691304e435a2974c6525b0de12d | 52858c7ed80fcc8015e81a45eb3b33cb2d9c5691304e435a2974c6525b0de12d | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/service/EvaluationContextService.java | 162c57b37c860815194526db91a9255b5820baa2543741a3671a0caee0f85f13 | 162c57b37c860815194526db91a9255b5820baa2543741a3671a0caee0f85f13 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/service/InterviewQuestionPlanSnapshotService.java | 359ac496abd3142fc2b379b41f0e7096ea5d669514dc0baf16fd32bafa51dac8 | 359ac496abd3142fc2b379b41f0e7096ea5d669514dc0baf16fd32bafa51dac8 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/service/InterviewQuestionSnapshotFactory.java | bea2f84776481abb0fb8841eb49c6fb058500c39f9b5c22f634d9a1c3262e60b | bea2f84776481abb0fb8841eb49c6fb058500c39f9b5c22f634d9a1c3262e60b | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/service/InterviewReportGenerator.java | ef84b139d034f2bb68c18d79a9f6be42bdfc1275199f51ef06c115f81bc59a42 | ef84b139d034f2bb68c18d79a9f6be42bdfc1275199f51ef06c115f81bc59a42 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java | c5fe76c5449957ae4fbca1a4a6c62893da3c387e02f0ebe2c49c19832ac17d5d | c5fe76c5449957ae4fbca1a4a6c62893da3c387e02f0ebe2c49c19832ac17d5d | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java | b86e4f621655e3b47adc46b66a03b3403ed610bd9e5ea3703327677eb7e007d6 | b86e4f621655e3b47adc46b66a03b3403ed610bd9e5ea3703327677eb7e007d6 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/service/InterviewService.java | 2a387120ce5b659278e2467e6667038a07a8381c73fbc767805be2f213b80cc8 | 2a387120ce5b659278e2467e6667038a07a8381c73fbc767805be2f213b80cc8 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/service/InterviewSessionService.java | c8fba96ffb0a9dd5115e7b7268b6db8a3489ec3cf3f3a610dcb59e0728ad2900 | c8fba96ffb0a9dd5115e7b7268b6db8a3489ec3cf3f3a610dcb59e0728ad2900 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/service/InterviewSessionTransactionService.java | 730f00f71ca3b9a5e1d9bd614aba6f314dcda19fc1f8dc1265728078dcc78cdf | 730f00f71ca3b9a5e1d9bd614aba6f314dcda19fc1f8dc1265728078dcc78cdf | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java | ec442da403b7de1cd5736c2a729b0f434af03947caccc872d952acd1b28bf762 | ec442da403b7de1cd5736c2a729b0f434af03947caccc872d952acd1b28bf762 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java | c63e9df43c5587420e646284842659ebb807b6ec18f5d98294149052d504aecf | c63e9df43c5587420e646284842659ebb807b6ec18f5d98294149052d504aecf | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/service/QuestionPlanSelector.java | 37506f89b61580f1a87e7e711a647d1c62e1bf45c51a0ebe8ac206209a949ec4 | 37506f89b61580f1a87e7e711a647d1c62e1bf45c51a0ebe8ac206209a949ec4 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/vo/InterviewDetailAnswerResponse.java | 94dc68b57fa10a0b41d9095e16c82ec6bb5108486b76b0bdd0b1124b0dedb677 | 94dc68b57fa10a0b41d9095e16c82ec6bb5108486b76b0bdd0b1124b0dedb677 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/vo/InterviewDetailQuestionResponse.java | 13372d555bd046bbf38e5ad04ded990094d69ce668e93fadbe059009cb3ba48d | 13372d555bd046bbf38e5ad04ded990094d69ce668e93fadbe059009cb3ba48d | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/vo/InterviewDetailResponse.java | 32e4faf2bad121b8f272e2a879d9b656b735730a7267f518555e0c4bf201a0a0 | 32e4faf2bad121b8f272e2a879d9b656b735730a7267f518555e0c4bf201a0a0 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/vo/InterviewEvaluationResponse.java | af82cad5b152df45b42c0856504619b325123c62d01f7fa4b78c9edb1aa388c4 | af82cad5b152df45b42c0856504619b325123c62d01f7fa4b78c9edb1aa388c4 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/vo/InterviewHistoryItemResponse.java | d3348e4970f40c7fcc4009b9bdd7098854827fd2a699a9e2fd4cef66ca4c7764 | d3348e4970f40c7fcc4009b9bdd7098854827fd2a699a9e2fd4cef66ca4c7764 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/vo/InterviewHistoryPageResponse.java | 1d1f9bd5dca9bf7eb386096cc4e8dd275b850adbbb210699e211efd66fe440e5 | 1d1f9bd5dca9bf7eb386096cc4e8dd275b850adbbb210699e211efd66fe440e5 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/vo/InterviewQuestionResponse.java | d9d953d3154277512779aaba487148b89f8eff7dd7122b85c71bbe88eb503e8c | d9d953d3154277512779aaba487148b89f8eff7dd7122b85c71bbe88eb503e8c | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/vo/InterviewReportResponse.java | abc844952c368754ce3d5a31196af6e07492a346be8f4b002edee9219852cf5a | abc844952c368754ce3d5a31196af6e07492a346be8f4b002edee9219852cf5a | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/vo/InterviewSessionResponse.java | 2533dbf1196e4becb6e9dbb2f83003f2115b2a29623e4d1ebbcb6a69da311c55 | 2533dbf1196e4becb6e9dbb2f83003f2115b2a29623e4d1ebbcb6a69da311c55 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/vo/SubmitInterviewAnswerResponse.java | 672e399e18b6739be57e505d10a4c1b5345749a94940a795cbb74e1fb6038981 | 672e399e18b6739be57e505d10a4c1b5345749a94940a795cbb74e1fb6038981 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGenerator.java | 0b7b822e651c2a5fd0faf8c32af3dce61d84c7d8826b8338c3a2eab08fd92279 | 0b7b822e651c2a5fd0faf8c32af3dce61d84c7d8826b8338c3a2eab08fd92279 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportResponse.java | 72caaf379a2c0c8a09cb5c55a06e1f7ed5c6c82c0398dd5e0c24ecd060d4eee3 | 72caaf379a2c0c8a09cb5c55a06e1f7ed5c6c82c0398dd5e0c24ecd060d4eee3 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/report/prompt/InterviewReportPrompt.java | 2dfc240b0588f4c51d9b5687db5d3d61f564cd4b939d44b98d2f4adc45c9c3e0 | 2dfc240b0588f4c51d9b5687db5d3d61f564cd4b939d44b98d2f4adc45c9c3e0 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/report/prompt/InterviewReportPromptBuilder.java | b62e2e5699517a9cced50cd7552ae7fed8a1e047642c291c1f03041f7befe9fd | b62e2e5699517a9cced50cd7552ae7fed8a1e047642c291c1f03041f7befe9fd | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/decision/EvaluationDecision.java | 8f13e02c670afa09ab972185b4f1d55dbb161a3e941ad084c3920e0d57e1d10c | 8f13e02c670afa09ab972185b4f1d55dbb161a3e941ad084c3920e0d57e1d10c | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpPolicy.java | fed9b824cc8c42ecd53ed26de024767cd29b18b414e45d0782d1889b82270dfa | fed9b824cc8c42ecd53ed26de024767cd29b18b414e45d0782d1889b82270dfa | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpTargetResolver.java | 92108c964ece7266238576826dac4f5e2697b3b226d4c03b46e83a603c3304c9 | 92108c964ece7266238576826dac4f5e2697b3b226d4c03b46e83a603c3304c9 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/judge/DeepSeekJudgeProvider.java | 99a509f733b5ba00a6f8ee9c3a163bd694e9519223d0ea81ed608fcc05ea2c56 | 99a509f733b5ba00a6f8ee9c3a163bd694e9519223d0ea81ed608fcc05ea2c56 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/judge/JudgeConfiguration.java | 8c926cd86b12ac948dffdc1627bdb04419555ab06e4d2dce59de81331a44c64d | 8c926cd86b12ac948dffdc1627bdb04419555ab06e4d2dce59de81331a44c64d | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/judge/JudgeProperties.java | c94e61426d66ebd8647e9332e7f525e13979a2acf21a5059dc5fdf0b181df8d2 | c94e61426d66ebd8647e9332e7f525e13979a2acf21a5059dc5fdf0b181df8d2 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/judge/JudgeProvider.java | 3e2a96b586d950f9c0d22c3552f8ea30fd779209320d1ccbf444f338380360d7 | 3e2a96b586d950f9c0d22c3552f8ea30fd779209320d1ccbf444f338380360d7 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/llm/DeepSeekEvaluationResult.java | fbf9c3257a1899d44215e8809a8657bfc4c192df9463934f4e00340401240ea2 | fbf9c3257a1899d44215e8809a8657bfc4c192df9463934f4e00340401240ea2 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/llm/LlmEvaluationSuggestion.java | 7106151eff41101d4d704070f226bdfeaf073e3fd37a8b16f54cc87a28468e35 | 7106151eff41101d4d704070f226bdfeaf073e3fd37a8b16f54cc87a28468e35 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPrompt.java | d2b3a6353023a0576aad8066f42345610bedda752a71e7dfe7118a525898e852 | d2b3a6353023a0576aad8066f42345610bedda752a71e7dfe7118a525898e852 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilder.java | d85385c5057a03626e9ae0fd52d0e2940296fbe9e69c807acc5f19eb33108e89 | d85385c5057a03626e9ae0fd52d0e2940296fbe9e69c807acc5f19eb33108e89 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/score/EvaluationScore.java | 0ecfc7133292fae70dbaf8690eab4b0f4d57821aa9d2f8ff8cd8dbe9b595e377 | 0ecfc7133292fae70dbaf8690eab4b0f4d57821aa9d2f8ff8cd8dbe9b595e377 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/score/EvaluationScoreCalculator.java | a5a9c2316d7fdbed93cc91786257698fc0dc04b077e909a22a81cc4e8d53d79c | a5a9c2316d7fdbed93cc91786257698fc0dc04b077e909a22a81cc4e8d53d79c | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/standard/EvaluationLevel.java | e5df70a7ab5e27ba0bfe795fda6ca07071c1c0da80d65d48242f9e6ac5d46d46 | e5df70a7ab5e27ba0bfe795fda6ca07071c1c0da80d65d48242f9e6ac5d46d46 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/standard/EvaluationStandard.java | 546be6ad8cc1115ed9af9035e0eba0cf18e48fffaf559f7d510de09de4454616 | 546be6ad8cc1115ed9af9035e0eba0cf18e48fffaf559f7d510de09de4454616 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidator.java | d9043d8858efe9cfd7a6526511b02cbd0b9dbd2136c56272d052713e9e1105eb | d9043d8858efe9cfd7a6526511b02cbd0b9dbd2136c56272d052713e9e1105eb | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/validation/ValidatedEvaluationSuggestion.java | 602108a5a95de7c04283332ef19bcde1bcc0ef0171075391ca3a07c1bbc5709c | 602108a5a95de7c04283332ef19bcde1bcc0ef0171075391ca3a07c1bbc5709c | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekConfiguration.java | 47c316906de6c6e563e63b8e8cbdc54d34e719df2065f9ab629f262790f4b4cd | 47c316906de6c6e563e63b8e8cbdc54d34e719df2065f9ab629f262790f4b4cd | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java | 6ecaeca2907e971e591f76867a60dacf57ef5f681ea1a3f736e4ed5de5bc9e0b | 6ecaeca2907e971e591f76867a60dacf57ef5f681ea1a3f736e4ed5de5bc9e0b | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekJsonCompletionResult.java | c46d99e959065125ea5bb175eac462d5e41ac04a9fb7b8d853f075a47829859a | c46d99e959065125ea5bb175eac462d5e41ac04a9fb7b8d853f075a47829859a | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekProperties.java | 7da602c78508d1cb8631c96357ad541f416bd407e97e4b9315d611065e9df2a7 | 7da602c78508d1cb8631c96357ad541f416bd407e97e4b9315d611065e9df2a7 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevClient.java | 46e297eff79e0573f00b85b817973ac670ac94d7870d7737e52af7cf44b96639 | 46e297eff79e0573f00b85b817973ac670ac94d7870d7737e52af7cf44b96639 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java | 55fe550394468288077f70a03b57e9a9127e1a2284823b1942595e9ba08c8873 | d79eaa50533ea34be605c453de90dfb2010946440223a874ce2e98835dd28fa4 | AUTHORIZED_PATCH |
| src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevProperties.java | ced6eedc550ca920b3463a5b38b1a0b7730c08d68985b6376c27a8a5364df013 | ced6eedc550ca920b3463a5b38b1a0b7730c08d68985b6376c27a8a5364df013 | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevRequest.java | a7c389af9d35b30942d7d2499320de75d15b9dc6ba9e7b7e17c1ca79ea9b28ef | a7c389af9d35b30942d7d2499320de75d15b9dc6ba9e7b7e17c1ca79ea9b28ef | UNCHANGED |
| src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevResponse.java | 045ab7675ac2a9bd0752e52b6e4f82a4e62628d115fb3b24f3bedf52796abe90 | 045ab7675ac2a9bd0752e52b6e4f82a4e62628d115fb3b24f3bedf52796abe90 | UNCHANGED |
| src/main/java/com/kun/aiinterview/common/exception/BusinessException.java | bba2deb795dc0629b7b51927b0d96e9b10607c4a88b8b4212517adbbc4919192 | bba2deb795dc0629b7b51927b0d96e9b10607c4a88b8b4212517adbbc4919192 | UNCHANGED |
| src/main/java/com/kun/aiinterview/common/exception/ConflictException.java | e6bc2a61c3677932638f8e237aa4d3e27a6d43ae0772782e06b811e8bf3fdb41 | e6bc2a61c3677932638f8e237aa4d3e27a6d43ae0772782e06b811e8bf3fdb41 | UNCHANGED |
| src/main/java/com/kun/aiinterview/common/exception/ExternalServiceException.java | 3b0e58f2b51d27f29588c6b72950c895a4d153425eb5aa1ec657b95b0ab4611c | 3b0e58f2b51d27f29588c6b72950c895a4d153425eb5aa1ec657b95b0ab4611c | UNCHANGED |
| src/main/java/com/kun/aiinterview/common/exception/GlobalExceptionHandler.java | a4ecda43f33e8ebd9f82931b7feeb16752a52785822c1ab43721a3c62097668c | a4ecda43f33e8ebd9f82931b7feeb16752a52785822c1ab43721a3c62097668c | UNCHANGED |
| src/main/java/com/kun/aiinterview/common/exception/ResourceNotFoundException.java | 8888c4b2660252c80aa42aa8faf3e7860aeff0c2851e99d2e7798b0a10dd409c | 8888c4b2660252c80aa42aa8faf3e7860aeff0c2851e99d2e7798b0a10dd409c | UNCHANGED |
| src/main/java/com/kun/aiinterview/common/recovery/RecoveryConfiguration.java | 692d20568703741dfe80ab04499b2ad3da31567b03e4bfad8e446408aca1ed9d | 692d20568703741dfe80ab04499b2ad3da31567b03e4bfad8e446408aca1ed9d | UNCHANGED |
| src/main/java/com/kun/aiinterview/common/recovery/StaleRecoveryProperties.java | 1366377f4a2058685dd47491b3d6dad8d2ef6eebcfd94b61405298613b9bbfb6 | 1366377f4a2058685dd47491b3d6dad8d2ef6eebcfd94b61405298613b9bbfb6 | UNCHANGED |
| src/main/java/com/kun/aiinterview/common/response/Result.java | e9b07d9aed49f708f5e6e50f5fa1712d5f041d63ddf7c3bea7bc24ab48ee9019 | e9b07d9aed49f708f5e6e50f5fa1712d5f041d63ddf7c3bea7bc24ab48ee9019 | UNCHANGED |
| src/main/java/com/kun/aiinterview/common/validation/Utf8ByteSize.java | be17ea299f8ab4aa32b5aa0c6a80e240b427e1b742672db24917c17fbe9e6124 | be17ea299f8ab4aa32b5aa0c6a80e240b427e1b742672db24917c17fbe9e6124 | UNCHANGED |
| src/main/java/com/kun/aiinterview/common/validation/Utf8ByteSizeValidator.java | c2e2358b43f78dc41642987ad2ba15fdd2c11a107453f026ea70a9fd65ceae97 | c2e2358b43f78dc41642987ad2ba15fdd2c11a107453f026ea70a9fd65ceae97 | UNCHANGED |
| src/main/java/com/kun/aiinterview/auth/controller/AuthController.java | cda6c759a35f9e55bf55eb9b41b6d3cd0ce7773d05972d3526b5ba5ca0053acf | cda6c759a35f9e55bf55eb9b41b6d3cd0ce7773d05972d3526b5ba5ca0053acf | UNCHANGED |
| src/main/java/com/kun/aiinterview/auth/dto/LoginRequest.java | 07513ccf526ecb2734371556d8f7aeff2da57751d95d5e00796d1a7e4219f35b | 07513ccf526ecb2734371556d8f7aeff2da57751d95d5e00796d1a7e4219f35b | UNCHANGED |
| src/main/java/com/kun/aiinterview/auth/dto/RegisterRequest.java | 664a92a1800addfe58beb4e645a9de599a27fcb2082f63a396f83facaeb7ccaa | 664a92a1800addfe58beb4e645a9de599a27fcb2082f63a396f83facaeb7ccaa | UNCHANGED |
| src/main/java/com/kun/aiinterview/auth/service/AuthService.java | 83c029ffe875340586ea7ab37cf3281b895c9a09b149a87442e0e5850c5c74b2 | 83c029ffe875340586ea7ab37cf3281b895c9a09b149a87442e0e5850c5c74b2 | UNCHANGED |
| src/main/java/com/kun/aiinterview/auth/vo/LoginResponse.java | 171f068d7128c0b09ce7bffdb0b311ae2eabf3f199749c5faf369d4fb6d224ac | 171f068d7128c0b09ce7bffdb0b311ae2eabf3f199749c5faf369d4fb6d224ac | UNCHANGED |
| src/test/resources/application-test-real.yaml | 4138f1123c703fde05a064fa225e36f953cacd0aaf1555991d40d85e718f195f | 4138f1123c703fde05a064fa225e36f953cacd0aaf1555991d40d85e718f195f | UNCHANGED |
| src/test/resources/application-test.yaml | 96e59104dff63e80ad47bcb1add58cb69ac123f034195f625f3a442387cf0de9 | 96e59104dff63e80ad47bcb1add58cb69ac123f034195f625f3a442387cf0de9 | UNCHANGED |
| src/test/resources/META-INF/ai-interview-test-runtime | 86efcc549e02e62ccce713724b2d786b0ba013c32a3d933665219a561ac17f35 | 86efcc549e02e62ccce713724b2d786b0ba013c32a3d933665219a561ac17f35 | UNCHANGED |
| src/test/java/com/kun/aiinterview/AiInterviewSystemApplicationTests.java | 40286bf777fda9660701f932189abba8f1bae873a5dd5d3521d41a7b0799e072 | 40286bf777fda9660701f932189abba8f1bae873a5dd5d3521d41a7b0799e072 | UNCHANGED |
| src/test/java/com/kun/aiinterview/DatabaseConnectionTest.java | 5ababa566fce8f69eeb3885080ddbcd72b220fcd30ffce43607e15f38898609b | 5ababa566fce8f69eeb3885080ddbcd72b220fcd30ffce43607e15f38898609b | UNCHANGED |
| src/test/java/com/kun/aiinterview/config/EnvironmentIsolationInitializerTest.java | fba6fd5dfa77b39ecc2084120e65d491e355476cc9323690c82fd39350174a21 | fba6fd5dfa77b39ecc2084120e65d491e355476cc9323690c82fd39350174a21 | UNCHANGED |
| src/test/java/com/kun/aiinterview/config/TestProfileExternalServiceIsolationTest.java | bd33e51bb999efecc4d30694524407ff6b4789a53809ca8a1cc590ad29be1b08 | bd33e51bb999efecc4d30694524407ff6b4789a53809ca8a1cc590ad29be1b08 | UNCHANGED |
| src/test/java/com/kun/aiinterview/evaluation/AiSystemBaselineEvaluationTest.java | 5305ee80846c8d8a5dc4c2c06489dfbebf7f6879eec26f5d0f5b941c7625329f | 5305ee80846c8d8a5dc4c2c06489dfbebf7f6879eec26f5d0f5b941c7625329f | UNCHANGED |
| src/test/java/com/kun/aiinterview/evaluation/BaselineTraceConfiguration.java | fd18dab16fca6bd8222298fa384ffe083cdce43eed99304e5ecf90cc97790076 | fd18dab16fca6bd8222298fa384ffe083cdce43eed99304e5ecf90cc97790076 | UNCHANGED |
| src/test/java/com/kun/aiinterview/user/controller/UserControllerIntegrationTest.java | 92ee8383208f2dcd03ed5de3ebd6bfb01b8bc5854a1f9f3469f4c389f92b0325 | 92ee8383208f2dcd03ed5de3ebd6bfb01b8bc5854a1f9f3469f4c389f92b0325 | UNCHANGED |
| src/test/java/com/kun/aiinterview/user/dto/ChangePasswordRequestTest.java | 9bc2511734b82ed99f1a2001cc007a5b24e8648930f3d3bccad2a28ccf3babf4 | 9bc2511734b82ed99f1a2001cc007a5b24e8648930f3d3bccad2a28ccf3babf4 | UNCHANGED |
| src/test/java/com/kun/aiinterview/user/mapper/UserMapperTest.java | d35403441530385279d76f7fc4f08145ce24060fe25139d82f86d4cfd8bd1b4a | d35403441530385279d76f7fc4f08145ce24060fe25139d82f86d4cfd8bd1b4a | UNCHANGED |
| src/test/java/com/kun/aiinterview/user/mapper/UserWeaknessMapperTest.java | e479d24d52db3ec8e27edef6fd85830aa70a3de46552d86f02422d9657ecf4c6 | e479d24d52db3ec8e27edef6fd85830aa70a3de46552d86f02422d9657ecf4c6 | UNCHANGED |
| src/test/java/com/kun/aiinterview/user/service/ConcurrentPasswordChangeIntegrationTest.java | 0510ac3a14536e7c755bea8f01b63eaea56ffe9444d5745538e250dcec61504f | 0510ac3a14536e7c755bea8f01b63eaea56ffe9444d5745538e250dcec61504f | UNCHANGED |
| src/test/java/com/kun/aiinterview/user/service/UserServiceTest.java | e182948fb27bee2fd5ecca00259b185470b472665c2b29b7b7543a3f92c9d6c2 | e182948fb27bee2fd5ecca00259b185470b472665c2b29b7b7543a3f92c9d6c2 | UNCHANGED |
| src/test/java/com/kun/aiinterview/user/service/UserWeaknessServiceTest.java | 9a6541d53b0e303d2be7a9df6fc8dfb6cf9991154634fe38fccfb9131ae96ee4 | 9a6541d53b0e303d2be7a9df6fc8dfb6cf9991154634fe38fccfb9131ae96ee4 | UNCHANGED |
| src/test/java/com/kun/aiinterview/security/config/SecurityConfigurationTest.java | 78e0ff2862402b4edbddc67f7140c7f20ae4c6fd82ed373f61b573663e459f79 | 78e0ff2862402b4edbddc67f7140c7f20ae4c6fd82ed373f61b573663e459f79 | UNCHANGED |
| src/test/java/com/kun/aiinterview/security/filter/JwtAuthenticationFilterTest.java | f250750f2e07883f6e69effcedcdc0207a224afd18395c3577579377286388aa | f250750f2e07883f6e69effcedcdc0207a224afd18395c3577579377286388aa | UNCHANGED |
| src/test/java/com/kun/aiinterview/security/handler/RestAccessDeniedHandlerTest.java | 7f54553d03404aeee013dd9557bbfbdf5426913c367845437159519ca6d524dc | 7f54553d03404aeee013dd9557bbfbdf5426913c367845437159519ca6d524dc | UNCHANGED |
| src/test/java/com/kun/aiinterview/security/handler/RestAuthenticationEntryPointTest.java | e786b793cc4adb8d5bbb78161f803324c0dcb4af2faca7238cf157a821029c9d | e786b793cc4adb8d5bbb78161f803324c0dcb4af2faca7238cf157a821029c9d | UNCHANGED |
| src/test/java/com/kun/aiinterview/security/jwt/JwtPropertiesTest.java | 421e42ec09f2f19a32f311f1b597b657e64c1fa896a79986465d6da3d8b1320a | 421e42ec09f2f19a32f311f1b597b657e64c1fa896a79986465d6da3d8b1320a | UNCHANGED |
| src/test/java/com/kun/aiinterview/security/jwt/JwtTokenServiceTest.java | 947bd8a6943b325e02f7e2ababbadc739c5303225b4b5f228dcdbdbcd506542e | 947bd8a6943b325e02f7e2ababbadc739c5303225b4b5f228dcdbdbcd506542e | UNCHANGED |
| src/test/java/com/kun/aiinterview/security/model/AuthenticatedUserTest.java | c7beb8425ae4d619b56adb0970c881ecf9ed3d85b5f2954f89fd4247333f2330 | c7beb8425ae4d619b56adb0970c881ecf9ed3d85b5f2954f89fd4247333f2330 | UNCHANGED |
| src/test/java/com/kun/aiinterview/question/controller/AdminQuestionControllerIntegrationTest.java | 897b20c6bcfed88815413eaab181c58169c7ac4951232e33380a7a5fb2e8c41c | 897b20c6bcfed88815413eaab181c58169c7ac4951232e33380a7a5fb2e8c41c | UNCHANGED |
| src/test/java/com/kun/aiinterview/question/controller/AdminQuestionControllerTest.java | 5ef1a2a8eb293c2dd41ba939bf43f15a3899ffc945636dc1cfa1e0d485884153 | 5ef1a2a8eb293c2dd41ba939bf43f15a3899ffc945636dc1cfa1e0d485884153 | UNCHANGED |
| src/test/java/com/kun/aiinterview/question/controller/AdminQuestionPaginationControllerTest.java | 8655010991f3280a9d7a76b40e1121b18a19f582b8cdeacb92999bbbcf065a6e | 8655010991f3280a9d7a76b40e1121b18a19f582b8cdeacb92999bbbcf065a6e | UNCHANGED |
| src/test/java/com/kun/aiinterview/question/controller/AdminQuestionPaginationIntegrationTest.java | fb88b181145b2baac4061c0b7267306dfc4bad58099e99fe18c67777d55ff5c4 | fb88b181145b2baac4061c0b7267306dfc4bad58099e99fe18c67777d55ff5c4 | UNCHANGED |
| src/test/java/com/kun/aiinterview/question/dto/CreateQuestionRequestValidationTest.java | ff9b8a1175eaa54d40d39e7347c299fb2a14783463f81e38ae4f3b7532aa0496 | ff9b8a1175eaa54d40d39e7347c299fb2a14783463f81e38ae4f3b7532aa0496 | UNCHANGED |
| src/test/java/com/kun/aiinterview/question/mapper/QuestionMapperTest.java | e68e4f2d34ef3b1a9d4e11295d604f1ab2a34ebf54c4c7a7b36b8c99ad863fc0 | e68e4f2d34ef3b1a9d4e11295d604f1ab2a34ebf54c4c7a7b36b8c99ad863fc0 | UNCHANGED |
| src/test/java/com/kun/aiinterview/question/mapper/QuestionPaginationMapperTest.java | 2278e7d0dd0a67b7aa94918609b281580990d84c5448655d6df9b636905cf641 | 2278e7d0dd0a67b7aa94918609b281580990d84c5448655d6df9b636905cf641 | UNCHANGED |
| src/test/java/com/kun/aiinterview/question/mapper/QuestionScoringPointMapperTest.java | 8d37c8922bc45a3a2182da36d7cb323d8b9b5094f4f7dbc0e333b18185e329d6 | 8d37c8922bc45a3a2182da36d7cb323d8b9b5094f4f7dbc0e333b18185e329d6 | UNCHANGED |
| src/test/java/com/kun/aiinterview/question/service/QuestionAdminPaginationServiceTest.java | df582a2c4bac69740b7d6c1781f6f92778670c71748b7cbd9831c2494fa5c733 | df582a2c4bac69740b7d6c1781f6f92778670c71748b7cbd9831c2494fa5c733 | UNCHANGED |
| src/test/java/com/kun/aiinterview/question/service/QuestionAdminServiceTest.java | f961e3eaed8da6dc532f414fb00f1e30889b69df671c8bcdbd23c17eb0f963b8 | f961e3eaed8da6dc532f414fb00f1e30889b69df671c8bcdbd23c17eb0f963b8 | UNCHANGED |
| src/test/java/com/kun/aiinterview/question/service/QuestionAdminServiceTransactionTest.java | d0e737e31cb3d68bfa248dfc2960cb2852b9edc5794f699e5d46cc9c33b88770 | d0e737e31cb3d68bfa248dfc2960cb2852b9edc5794f699e5d46cc9c33b88770 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/chunk/KnowledgeTextChunkerTest.java | 051f5452da81ec7e43e48eab645a97c08dd56771ebd499d2bc16fd60f0cc2ffb | 051f5452da81ec7e43e48eab645a97c08dd56771ebd499d2bc16fd60f0cc2ffb | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/controller/AdminKnowledgeDocumentControllerIntegrationTest.java | 937d3cb3487474afaa1da6f54a5847106c56b7b7d084653b573e1e26f0887bfc | 937d3cb3487474afaa1da6f54a5847106c56b7b7d084653b573e1e26f0887bfc | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/controller/AdminKnowledgeDocumentControllerTest.java | 7c4f22a99ac95759b4cd7111ee3e76ea386a3b3b9ca9365e28aa9081ed2f8883 | 7c4f22a99ac95759b4cd7111ee3e76ea386a3b3b9ca9365e28aa9081ed2f8883 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/controller/AdminKnowledgeDocumentProcessApiTest.java | 9df162e6783478d6e25ed2c7920064b4bd029add9b7b1a6bee51f9f1f30e4329 | 9df162e6783478d6e25ed2c7920064b4bd029add9b7b1a6bee51f9f1f30e4329 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/controller/KnowledgeMultipartServletLimitTest.java | 589e9961966fa54cd8f3582126deb18b69c030ebdaa0366ff86137326948869e | 589e9961966fa54cd8f3582126deb18b69c030ebdaa0366ff86137326948869e | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/embedding/EmbeddingBatchResultTest.java | 0f8c379010414ad1c9b608cc02e82ed98f76d9aa6567b1dd583b7c7772ab54d0 | 0f8c379010414ad1c9b608cc02e82ed98f76d9aa6567b1dd583b7c7772ab54d0 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/embedding/EmbeddingConfigurationTest.java | 51e771303266c0eb5188b50aea119bde632c2c126976a85caacb52c6cb695f01 | 51e771303266c0eb5188b50aea119bde632c2c126976a85caacb52c6cb695f01 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/embedding/EmbeddingPropertiesTest.java | 4fa3336d409d043b2b984b34ddeb2b0a54c07ae68beeefd26f6bd87bd01b8e74 | 4fa3336d409d043b2b984b34ddeb2b0a54c07ae68beeefd26f6bd87bd01b8e74 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/embedding/EmbeddingVectorTest.java | 106d5d5dbde2a61a5d0135c4fba897e7e98657a96c3c9db1937db1bc066b2bd6 | 106d5d5dbde2a61a5d0135c4fba897e7e98657a96c3c9db1937db1bc066b2bd6 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/embedding/RealEmbeddingSmokeTest.java | e498b86f53b8d168c47aa86ce71a8dd6029a032617a3f8b2d2737ff6b49f171a | e498b86f53b8d168c47aa86ce71a8dd6029a032617a3f8b2d2737ff6b49f171a | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/mapper/KnowledgeDocumentMapperTest.java | 67f453726b9df294137d3d991eade3ea710c4c35dafda86d9dc14edb25597ac4 | 67f453726b9df294137d3d991eade3ea710c4c35dafda86d9dc14edb25597ac4 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/mapper/RagTraceMapperTest.java | 34fae89ab9b5198245f5a982e2b8ce64bfee1c9eaedf5e17acb0f317de5c627b | 34fae89ab9b5198245f5a982e2b8ce64bfee1c9eaedf5e17acb0f317de5c627b | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeDocumentAdminServiceTest.java | 16c5ad9d7fb41120dcadd39dc6a413db680c8e6cec570536d2296da96f8661b4 | 16c5ad9d7fb41120dcadd39dc6a413db680c8e6cec570536d2296da96f8661b4 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeDocumentProcessingServiceConfigurationTest.java | e8f84d59895e31b5507bdcbe26d7090e32c59038f103518d7c9c243b7cafb64d | e8f84d59895e31b5507bdcbe26d7090e32c59038f103518d7c9c243b7cafb64d | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeDocumentProcessingServiceTest.java | d61b62abaad1f46753565e0951913b1d7ae3472b8d71bc31d8e088ab1f075014 | d61b62abaad1f46753565e0951913b1d7ae3472b8d71bc31d8e088ab1f075014 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeDocumentProcessingTransactionServiceTest.java | 68455ac00e94cf2db0bacf2da4f8d8f51bea1c9bea407c114d3c60ae60f959c8 | 68455ac00e94cf2db0bacf2da4f8d8f51bea1c9bea407c114d3c60ae60f959c8 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/service/KnowledgePersistenceIntegrationTest.java | 0d19e4da5d7396b2ced37c27cda97ff671889d6e362e62f4aaf231b62e807e8a | 0d19e4da5d7396b2ced37c27cda97ff671889d6e362e62f4aaf231b62e807e8a | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalRawHitTest.java | 68418ea076c3e40bf0fc7842ea8d3012dcc5e20fbd7dcde9f51f7df1e92fb29c | 68418ea076c3e40bf0fc7842ea8d3012dcc5e20fbd7dcde9f51f7df1e92fb29c | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalServiceTest.java | c671793000704f73dc7ad3d8c8adaf4f4b6c3bdad269b84ef49128eeed00d391 | c671793000704f73dc7ad3d8c8adaf4f4b6c3bdad269b84ef49128eeed00d391 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/service/RagTracePersistenceServiceIntegrationTest.java | c7c9c1df1d7ca40cfd0f0a351b79a05f64ad8d8af5eb3109f206710d9f056e03 | c7c9c1df1d7ca40cfd0f0a351b79a05f64ad8d8af5eb3109f206710d9f056e03 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/service/RagTracePersistenceServiceTest.java | 60e178df1238406a92a690c62166459baa10021c1336b2dd55e29c8e6c611c1f | 60e178df1238406a92a690c62166459baa10021c1336b2dd55e29c8e6c611c1f | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/service/RealKnowledgeDocumentProcessingFailureIntegrationTest.java | d1c86ba648811c1e0cebd890cd49164b2f18b6c834779594bc9db6dfeaeec514 | d1c86ba648811c1e0cebd890cd49164b2f18b6c834779594bc9db6dfeaeec514 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/service/RealKnowledgeDocumentProcessingSmokeTest.java | 94aa35cb8bd3eaafb18ab015638c07a6fe4b51b5e52600a1bf8cfa23f6592c85 | 94aa35cb8bd3eaafb18ab015638c07a6fe4b51b5e52600a1bf8cfa23f6592c85 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/vector/VectorSearchHitTest.java | 6a2f6da10e2ae919c962465c94170111068f3c54aac118625ced96bc93e44ea3 | 6a2f6da10e2ae919c962465c94170111068f3c54aac118625ced96bc93e44ea3 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/vector/VectorWriteItemTest.java | 665da2b1326e63c2a6a4b4a033109aaa0991211c80124206e238c9c20fac1071 | 665da2b1326e63c2a6a4b4a033109aaa0991211c80124206e238c9c20fac1071 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusCollectionInitializerTest.java | 912130cb6b463de51ae067e7b57f13446ccbb5b2ced8e7837a60cea2f9fc364d | 912130cb6b463de51ae067e7b57f13446ccbb5b2ced8e7837a60cea2f9fc364d | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusConfigurationTest.java | dc63cdc4734f9aa125678eca4df8a142a898013cdc68d1783f2d590e80afc9b5 | dc63cdc4734f9aa125678eca4df8a142a898013cdc68d1783f2d590e80afc9b5 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusPropertiesTest.java | 1a9d01206ab741727b2e630fe5c9d1701592be27717883b7e18d3dd9f5a2a6aa | 1a9d01206ab741727b2e630fe5c9d1701592be27717883b7e18d3dd9f5a2a6aa | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusVectorStoreClientTest.java | 25f322ca1d5486f0ab05273befe5436bb4108a52cd85e39cddd9dc69d2a5f847 | 25f322ca1d5486f0ab05273befe5436bb4108a52cd85e39cddd9dc69d2a5f847 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/vector/milvus/RealMilvusSchemaValidationSmokeTest.java | cc96dc090b9977767d47630b9c38ee00d536f466cd1d6eab450409f5fdd363d8 | cc96dc090b9977767d47630b9c38ee00d536f466cd1d6eab450409f5fdd363d8 | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/vector/milvus/RealMilvusVectorStoreSmokeTest.java | 91585460e3ed9d3b4566637dd4b399a471cdab6b6466212558980f0422ce6c3c | 91585460e3ed9d3b4566637dd4b399a471cdab6b6466212558980f0422ce6c3c | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingClientTest.java | cd17ec9ba2d730b08892ac29661ab82328d5821439f858f849369440b57ab28c | cd17ec9ba2d730b08892ac29661ab82328d5821439f858f849369440b57ab28c | UNCHANGED |
| src/test/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingHttpContractTest.java | d7c18904757e5432606409cc578969fd6f0bdb626f65fea9da438432408478d1 | d7c18904757e5432606409cc578969fd6f0bdb626f65fea9da438432408478d1 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/config/InterviewPlanConfigTest.java | f9872fc631ee376afbcf76741d393902bda843cc81f00dd4ce087646420cd165 | f9872fc631ee376afbcf76741d393902bda843cc81f00dd4ce087646420cd165 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/controller/HttpErrorTaxonomyControllerTest.java | 8a8a3772d76d532948545b6fd3709175443a11e03c9128084df574efcf32dccc | 8a8a3772d76d532948545b6fd3709175443a11e03c9128084df574efcf32dccc | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/controller/InterviewControllerTest.java | ec6386b975adf778eab5f1eb920144dc181668c19b5c5dee02e87ff6aa9b8749 | ec6386b975adf778eab5f1eb920144dc181668c19b5c5dee02e87ff6aa9b8749 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/EvaluationRagQueryBuilderTest.java | 32eb04649c7c9674dbfac464ff6a9f4c4c99c0c71853dbe60ca61224f4016ee5 | 32eb04649c7c9674dbfac464ff6a9f4c4c99c0c71853dbe60ca61224f4016ee5 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/EvaluationRetrievalAdapterTest.java | 01fdc18c3d2658d0833ceb35bbe41675df3266bac32cc8175a0e5f1400395324 | 01fdc18c3d2658d0833ceb35bbe41675df3266bac32cc8175a0e5f1400395324 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/mapper/AnswerEvaluationMapperTest.java | 6c9f77405589f8e496ed88f4e96e453f43950b0e9c60d7b00cd37f01f999e932 | 6c9f77405589f8e496ed88f4e96e453f43950b0e9c60d7b00cd37f01f999e932 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/mapper/InterviewAnswerMapperTest.java | ed828a8ca00fbb06815e168e7d479c20534687f3260eeb2f8b1a0331df952c1b | ed828a8ca00fbb06815e168e7d479c20534687f3260eeb2f8b1a0331df952c1b | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/mapper/InterviewDetailMapperTest.java | 68b23c4228a10015729c8be7f3559f1642aac396372c9e68b1b36c378ad3ea63 | 68b23c4228a10015729c8be7f3559f1642aac396372c9e68b1b36c378ad3ea63 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/mapper/InterviewHistoryMapperTest.java | 7524489a6246a6656ef8cb0ad49a58e01ad741610a897b4ced3ddce5255ede44 | 7524489a6246a6656ef8cb0ad49a58e01ad741610a897b4ced3ddce5255ede44 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/mapper/InterviewQuestionMapperTest.java | 397c5e4ecd86a3bb3b8afdcae414490f628fd7e0dc2f4ea0c9b3bd8b2e0e7b86 | 397c5e4ecd86a3bb3b8afdcae414490f628fd7e0dc2f4ea0c9b3bd8b2e0e7b86 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/mapper/InterviewSessionCreationMapperTest.java | 0a63009688ffb81e5ffe95e41ffb45d0cb89f6ddd10a4e84a5f44b666b1e8ce6 | 0a63009688ffb81e5ffe95e41ffb45d0cb89f6ddd10a4e84a5f44b666b1e8ce6 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/mapper/InterviewSessionMapperTest.java | cf1d399b5622e916bdb0afe8c4fcbdaa53dd042b2f0bc3e0f048bd616d0c7336 | cf1d399b5622e916bdb0afe8c4fcbdaa53dd042b2f0bc3e0f048bd616d0c7336 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/EvaluationContextServiceTest.java | 0a0c577ac6de3643926247b787a4fff1a00eabc08bc2a9416d0d10d55517f52e | 0a0c577ac6de3643926247b787a4fff1a00eabc08bc2a9416d0d10d55517f52e | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewEvaluationClaimConcurrencyIntegrationTest.java | 206bd1d6489d7148c2c462c45ee7e7b2a05f40e88e5f8f8535ea742168aa2de6 | 206bd1d6489d7148c2c462c45ee7e7b2a05f40e88e5f8f8535ea742168aa2de6 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewHistoryServiceTest.java | fb200c43a9850068ec2df6c508a25199662d3ade9f775e7382437e963344e22f | fb200c43a9850068ec2df6c508a25199662d3ade9f775e7382437e963344e22f | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewQuestionPlanSnapshotIntegrationTest.java | e17b7ebfe3f6829ef5d47e88c6434ddde797c98c82fb0243a6e555f8a695b7fe | e17b7ebfe3f6829ef5d47e88c6434ddde797c98c82fb0243a6e555f8a695b7fe | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewQuestionPlanSnapshotServiceTest.java | 19fd5b972910a4cf96a9be7fde6371e3a1fd375296e0df8b39c2a68160fe1163 | 19fd5b972910a4cf96a9be7fde6371e3a1fd375296e0df8b39c2a68160fe1163 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewQuestionSnapshotFactoryTest.java | 1072f26335b5be7245396db62ac28b4b218d2fa7c7e0d6840c9ca2f4a3ad7c75 | 1072f26335b5be7245396db62ac28b4b218d2fa7c7e0d6840c9ca2f4a3ad7c75 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewReportApiServiceTest.java | 6047b7df9212f20921aafa3832f7ee383614c36501a5e13ddb53b7a7e71607d4 | 6047b7df9212f20921aafa3832f7ee383614c36501a5e13ddb53b7a7e71607d4 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewReportServiceTest.java | 8fe4cfb4f0ada489081959e59b8060cb06d66be8c4c2c769a766471149e566fb | 8fe4cfb4f0ada489081959e59b8060cb06d66be8c4c2c769a766471149e566fb | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewReportTransactionServiceIntegrationTest.java | b168d0d2386cca858f07cf6d5b87bddc7b95620b6ffe7ad77fe6d8b7c045db1f | b168d0d2386cca858f07cf6d5b87bddc7b95620b6ffe7ad77fe6d8b7c045db1f | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewReportTransactionServiceTest.java | 6b7efe3d9c8cd01a17b2da74fc77d98b898e9ca80dc308a3fd02215e7536809e | 6b7efe3d9c8cd01a17b2da74fc77d98b898e9ca80dc308a3fd02215e7536809e | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewServiceTest.java | 6fa628eba0a410910f7f433acdb693cdb6caa74646b135e44370fd3587d7c025 | 6fa628eba0a410910f7f433acdb693cdb6caa74646b135e44370fd3587d7c025 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewSessionServiceTest.java | e6905a4dbbfae93dfe0101948d0957fc7bb0fdd0745aeec5340d3e4a0ba4167f | e6905a4dbbfae93dfe0101948d0957fc7bb0fdd0745aeec5340d3e4a0ba4167f | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewSessionTransactionIntegrationTest.java | 5c96b301aff08e89f86f4c260e40584d88bea5398e4d3bc7f0c1870521e9e677 | 5c96b301aff08e89f86f4c260e40584d88bea5398e4d3bc7f0c1870521e9e677 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewSessionTransactionServiceTest.java | 400b30530fb9b66b7386c9d65eb09a2e4b2cb88ca3493a1638138e8e445fa53d | 400b30530fb9b66b7386c9d65eb09a2e4b2cb88ca3493a1638138e8e445fa53d | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowServiceTest.java | e8f900f846d47815a6bad0cf2294bea8940c0d4403fe52170691612a4c59f88c | e8f900f846d47815a6bad0cf2294bea8940c0d4403fe52170691612a4c59f88c | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionServiceIntegrationTest.java | 6ef0955e39cc67fa60b21663226c958b4f5810800033fc6849f4887e2e68cc09 | 6ef0955e39cc67fa60b21663226c958b4f5810800033fc6849f4887e2e68cc09 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionServiceTest.java | 69c8d8b7ea75d67b270c3ce6929d3b44da0ebbca4d28bf1a675f489fe1e736e7 | 69c8d8b7ea75d67b270c3ce6929d3b44da0ebbca4d28bf1a675f489fe1e736e7 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/service/QuestionPlanSelectorTest.java | 39210d194dc339ce3dc53b797d02a32ba27853ab7a694a3838bc6a19bd49acb0 | 39210d194dc339ce3dc53b797d02a32ba27853ab7a694a3838bc6a19bd49acb0 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGeneratorConfigurationTest.java | 0d0698690a6ad0a422da1eb7ae9a34cb345bf907fe276deabf5239e01702a712 | 0d0698690a6ad0a422da1eb7ae9a34cb345bf907fe276deabf5239e01702a712 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGeneratorTest.java | ad45403cea8b2f11dde839c9f1b1c8006e7fe68f7b4fac6773c9fbe3adb82af2 | ad45403cea8b2f11dde839c9f1b1c8006e7fe68f7b4fac6773c9fbe3adb82af2 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/report/prompt/InterviewReportPromptBuilderTest.java | ffff9dbe671ffbe5c5aa788e49476d36479fb68fd26fc8a89a9595072040b8da | ffff9dbe671ffbe5c5aa788e49476d36479fb68fd26fc8a89a9595072040b8da | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/decision/EvaluationDecisionTest.java | 0a81940e815aaa00e0ab0daea24edcb8ecc9eccd54b7ad1efbb6d3ef8da80e91 | 0a81940e815aaa00e0ab0daea24edcb8ecc9eccd54b7ad1efbb6d3ef8da80e91 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpPolicyTest.java | ae710984d2f5fff376444e7c0ef822f74b79e3c22c9827c0eaecc48857d1e917 | ae710984d2f5fff376444e7c0ef822f74b79e3c22c9827c0eaecc48857d1e917 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpTargetResolverTest.java | a47ef7d9fb91d62ace5ddc1e1e0de9087752b4fd7b9b1b379c4dff93c96079ac | a47ef7d9fb91d62ace5ddc1e1e0de9087752b4fd7b9b1b379c4dff93c96079ac | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/judge/DeepSeekJudgeProviderTest.java | 2487bc844de6330026660c8f435ac8beece6b1e1b756348ee63675cc84356329 | 2487bc844de6330026660c8f435ac8beece6b1e1b756348ee63675cc84356329 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/judge/JudgeConfigurationTest.java | 31d9449ad3898652c93d5bb0f11701328bc8b4f7500bcec811bd7551f99b5827 | 31d9449ad3898652c93d5bb0f11701328bc8b4f7500bcec811bd7551f99b5827 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/orchestration/EvaluationOrchestrationServiceTest.java | 92b0c4acc7b3ec9dec11c49141b8005dec9b1d2263f81646c87e6a7853b526ad | 92b0c4acc7b3ec9dec11c49141b8005dec9b1d2263f81646c87e6a7853b526ad | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilderTest.java | cd2f69620e5f04eb4f8ffd17ca9ee2c6fafcb60b45b23a6e9fd219181da3904a | cd2f69620e5f04eb4f8ffd17ca9ee2c6fafcb60b45b23a6e9fd219181da3904a | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptTest.java | 1b191315661fc9e1019379b421e2b21d4fa430c43dde9e0306c035c8c2922069 | 1b191315661fc9e1019379b421e2b21d4fa430c43dde9e0306c035c8c2922069 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/score/EvaluationScoreCalculatorTest.java | 2e43933a415d1eeb1c09f716f931e4ca1200b8e5c54f2e89f735754b57fa77d7 | 2e43933a415d1eeb1c09f716f931e4ca1200b8e5c54f2e89f735754b57fa77d7 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/standard/EvaluationStandardTest.java | 8ce92d98472e1a83de18275eb5af48a74d89eeb7c7421cabb2326c2c7a0a2ce0 | 8ce92d98472e1a83de18275eb5af48a74d89eeb7c7421cabb2326c2c7a0a2ce0 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidatorTest.java | 3a7208cead00768da9c43bf079bad2e605acbeb348c5603e37368ffa82045780 | 3a7208cead00768da9c43bf079bad2e605acbeb348c5603e37368ffa82045780 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekConfigurationTest.java | 993c03b1d280d130afb5715a611e7b4078c55ccc055ca906c59a3e46c5a3e331 | 993c03b1d280d130afb5715a611e7b4078c55ccc055ca906c59a3e46c5a3e331 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClientTest.java | dd38ae77ae425c9f284f97320169a8b561744c5ba342d553aab633ee9922c1c7 | dd38ae77ae425c9f284f97320169a8b561744c5ba342d553aab633ee9922c1c7 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekPropertiesTest.java | 8dc09f3a8c413223ebc3972d52eff7e2b1a6674b1f53971c539aa782d7a5e090 | 8dc09f3a8c413223ebc3972d52eff7e2b1a6674b1f53971c539aa782d7a5e090 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevClientTest.java | 76e4293f03624ac213045286f17c537524cc27837ea2982191a0fa555d36ba16 | 76e4293f03624ac213045286f17c537524cc27837ea2982191a0fa555d36ba16 | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProviderTest.java | da5b6b37fd8fc995e953adff2c3bafbb787515cab68ef3618cf350cd52ca2caa | da5b6b37fd8fc995e953adff2c3bafbb787515cab68ef3618cf350cd52ca2caa | UNCHANGED |
| src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevScoreMappingTest.java | 45449fb7ed0246c39fb83dac430fb20ddb3807bbdc9acf6cae318dae07441ea2 | 45449fb7ed0246c39fb83dac430fb20ddb3807bbdc9acf6cae318dae07441ea2 | UNCHANGED |
| src/test/java/com/kun/aiinterview/common/exception/GlobalExceptionHandlerMultipartTest.java | 347a41f46100909af367a7c17ecace4d07a6a219325620d16efd0675ba255fea | 347a41f46100909af367a7c17ecace4d07a6a219325620d16efd0675ba255fea | UNCHANGED |
| src/test/java/com/kun/aiinterview/common/exception/GlobalExceptionHandlerMvcTest.java | 8ab06a21226cd5a6568dac179c0e3e629fd475facbd7944469dfbadcf64ce689 | 8ab06a21226cd5a6568dac179c0e3e629fd475facbd7944469dfbadcf64ce689 | UNCHANGED |
| src/test/java/com/kun/aiinterview/common/recovery/StaleRecoveryCasIntegrationTest.java | cafafc75012d18f735a5272a07463e1fe5292a069c07057d5ecc4eff0f3f9ce5 | cafafc75012d18f735a5272a07463e1fe5292a069c07057d5ecc4eff0f3f9ce5 | UNCHANGED |
| src/test/java/com/kun/aiinterview/common/recovery/StaleRecoveryPropertiesTest.java | f9fc44dc58c3ad73c7d80b731f80cf411d8967be1b4162f3566af7b512701219 | f9fc44dc58c3ad73c7d80b731f80cf411d8967be1b4162f3566af7b512701219 | UNCHANGED |
| src/test/java/com/kun/aiinterview/common/validation/Utf8ByteSizeValidatorTest.java | 19360c0eeef3ec233df348fc1b9318d63a5997d88930333ba4dbd6e88884607c | 19360c0eeef3ec233df348fc1b9318d63a5997d88930333ba4dbd6e88884607c | UNCHANGED |
| src/test/java/com/kun/aiinterview/common/validation/Utf8TextRequestValidationTest.java | 15bc772f86ccc729abf12f5e7ee842c3094b49b3b37c2dc245980fd78dc14bcb | 15bc772f86ccc729abf12f5e7ee842c3094b49b3b37c2dc245980fd78dc14bcb | UNCHANGED |
| src/test/java/com/kun/aiinterview/auth/controller/AuthControllerTest.java | fd75a106bb938aedb8f952220c201ca3b0a069105d6ef8aaa35ba49aa573cc5e | fd75a106bb938aedb8f952220c201ca3b0a069105d6ef8aaa35ba49aa573cc5e | UNCHANGED |
| src/test/java/com/kun/aiinterview/auth/service/AuthServiceTest.java | 4f2b95c4e9dc96f1fe8592038523c33464e98ff425673bb144a1418fa78f6b3d | 4f2b95c4e9dc96f1fe8592038523c33464e98ff425673bb144a1418fa78f6b3d | UNCHANGED |
| src/test/java/com/kun/aiinterview/auth/service/AuthServiceUniquenessTest.java | 09509436548c8464236685704697da11db7fa1dad61f1332e47d229dcc603bd5 | 09509436548c8464236685704697da11db7fa1dad61f1332e47d229dcc603bd5 | UNCHANGED |
| docs/rag/Phase2A-版本与知识入库审计.md | 865253c22ef3bc351b26f37ee2a33f98b69fd01dadcc3390e0ec13398918cc72 | 865253c22ef3bc351b26f37ee2a33f98b69fd01dadcc3390e0ec13398918cc72 | UNCHANGED |
| docs/rag/Phase2B-知识文档编写审查.md | 382ee62d9a59966e3b94e8c44b63942d10053c00d114ce83482ea5959849f0e0 | 382ee62d9a59966e3b94e8c44b63942d10053c00d114ce83482ea5959849f0e0 | UNCHANGED |
| docs/rag/Phase2C-切片内容预览.md | 99dba4b8d0c743b7e9413eb30c1a8a066b7efc1f4fd784f54e8dc4779cf2ed44 | 99dba4b8d0c743b7e9413eb30c1a8a066b7efc1f4fd784f54e8dc4779cf2ed44 | UNCHANGED |
| docs/rag/Phase2C-离线切片验证.md | cf77aabaa2b58ab8be68269c36e0ebb0186b909716d063ee0fefd7fa259ba5a2 | cf77aabaa2b58ab8be68269c36e0ebb0186b909716d063ee0fefd7fa259ba5a2 | UNCHANGED |
| docs/rag/Phase3A-真实服务就绪验证.md | 91e6b720691f34a2aca71c17925939b7927cee07c82314550ed28620cf9e6754 | 91e6b720691f34a2aca71c17925939b7927cee07c82314550ed28620cf9e6754 | UNCHANGED |
| docs/rag/Phase3B-真实知识库入库验证.md | 7c5a264afdd1f8c4c89e01b7574c7166076a27942b6729839e495f6196b79126 | 7c5a264afdd1f8c4c89e01b7574c7166076a27942b6729839e495f6196b79126 | UNCHANGED |
| docs/rag/PHASE_4A_GOLDEN_SET_REPORT.md | 63429f9ea73cdbcad00f1a065250830b27333d9803a442aadc31faf7e777c862 | 63429f9ea73cdbcad00f1a065250830b27333d9803a442aadc31faf7e777c862 | UNCHANGED |
| docs/rag/PHASE_4B_BASELINE_RETRIEVAL_REPORT.md | 4cbc52c5d3f0326e709a40719ed559e0c7c5345883496016e2e43b9ebff86010 | 4cbc52c5d3f0326e709a40719ed559e0c7c5345883496016e2e43b9ebff86010 | UNCHANGED |
| docs/rag/PHASE_4C1_ADMISSION_POLICY_REPORT.md | 47e4844fc6d5de13daf3d1a53fb4bb459553ff9e9e60364e2ea89b2fc8ed534b | 47e4844fc6d5de13daf3d1a53fb4bb459553ff9e9e60364e2ea89b2fc8ed534b | UNCHANGED |
| docs/rag/PHASE_4C2_HOLDOUT_FREEZE_REPORT.md | 16e6b38180bacc75a1d2496648e8132584d646edb78936cc68552616f4b97930 | 16e6b38180bacc75a1d2496648e8132584d646edb78936cc68552616f4b97930 | UNCHANGED |
| docs/rag/PHASE_4C3_HOLDOUT_VALIDATION_REPORT.md | 6b89a14b7cb8f2b53c4ffefaf531ab4ca791619d61cdd4b73dd3748609c244f3 | 6b89a14b7cb8f2b53c4ffefaf531ab4ca791619d61cdd4b73dd3748609c244f3 | UNCHANGED |
| docs/rag/PHASE_4D1_FULL_KB_PLANNING_REPORT.md | 3adf3a6818ba78cb018e790183cb5b4c155b69f8f71b190312412a14876f1c17 | 3adf3a6818ba78cb018e790183cb5b4c155b69f8f71b190312412a14876f1c17 | UNCHANGED |
| docs/rag/PHASE_4D2_FULL_KB_AUTHORING_REPORT.md | 0978be89a57ad6ff8315e7a506840d0a56cb2fc676729f3d3084fdc883d09b50 | 0978be89a57ad6ff8315e7a506840d0a56cb2fc676729f3d3084fdc883d09b50 | UNCHANGED |
| docs/rag/PHASE_4D3_PRODUCTION_CHUNK_DRYRUN_REPORT.md | 87065bdad1e97194294328a7d4dc891b014c64bc5e99f59a8deb1e8895014137 | 87065bdad1e97194294328a7d4dc891b014c64bc5e99f59a8deb1e8895014137 | UNCHANGED |
| docs/rag/PHASE_4D4_FULL_BANK_INGESTION_REPORT.md | 11c27454b10b605b380ec088aab7716941463a5e9309c9e207336627d92ba6f4 | 11c27454b10b605b380ec088aab7716941463a5e9309c9e207336627d92ba6f4 | UNCHANGED |
| docs/rag/PHASE_4D5_FULL_BANK_GOLDEN_FREEZE_REPORT.md | baf309ca8be31478208114ddcc6bdf6d644c2bcb751958404fcdff8c5fdca97f | baf309ca8be31478208114ddcc6bdf6d644c2bcb751958404fcdff8c5fdca97f | UNCHANGED |
| docs/rag/PHASE_4D5_FULL_BANK_RETRIEVAL_REPORT.md | dffb7cf874f6ef9e5ba9ee4a4d1ead955f7db27c8678e64f9ca5191caec7f57c | dffb7cf874f6ef9e5ba9ee4a4d1ead955f7db27c8678e64f9ca5191caec7f57c | UNCHANGED |
| docs/rag/evaluation/PHASE_4A_GOLDEN_RELEVANCE.json | f3870b3432856dd44efbe384656367b798bb730d4c9313bdc6cee6259ac1cd22 | f3870b3432856dd44efbe384656367b798bb730d4c9313bdc6cee6259ac1cd22 | UNCHANGED |
| docs/rag/evaluation/PHASE_4A_GOLDEN_RELEVANCE_REVIEW.md | b12685faf1a79f221ab970b0ff34d01772526ad56972b37ed38fcfe1b5e91c54 | b12685faf1a79f221ab970b0ff34d01772526ad56972b37ed38fcfe1b5e91c54 | UNCHANGED |
| docs/rag/evaluation/PHASE_4A_PRODUCTION_QUERY_SET.json | 8251e2ee7307b9a03ec258328e7f29d91475d51b640f11a37c4f04d29e029d35 | 8251e2ee7307b9a03ec258328e7f29d91475d51b640f11a37c4f04d29e029d35 | UNCHANGED |
| docs/rag/evaluation/PHASE_4B_BASELINE_RETRIEVAL_RESULTS.json | cb5ba082f079a9a61e01fbf087e738084d550ce4f53a95f2549efbfe7f80bec0 | cb5ba082f079a9a61e01fbf087e738084d550ce4f53a95f2549efbfe7f80bec0 | UNCHANGED |
| docs/rag/evaluation/PHASE_4B_BASELINE_RETRIEVAL_REVIEW.md | b3f508a0c401db70b8a9a2360ea662fb951ef5c6c9d6e8aa5c2c354863e94dfa | b3f508a0c401db70b8a9a2360ea662fb951ef5c6c9d6e8aa5c2c354863e94dfa | UNCHANGED |
| docs/rag/evaluation/PHASE_4C1_ADMISSION_POLICY_REVIEW.md | 914576b83edefb62baad654996aedf3764fe03a8135f06977f57ae4582e96b67 | 914576b83edefb62baad654996aedf3764fe03a8135f06977f57ae4582e96b67 | UNCHANGED |
| docs/rag/evaluation/PHASE_4C1_THRESHOLD_SWEEP.json | 99cba5cbf1a3baf42f7d237ee2ae456c55ec37d7eec82ff8baf6be422f32da68 | 99cba5cbf1a3baf42f7d237ee2ae456c55ec37d7eec82ff8baf6be422f32da68 | UNCHANGED |
| docs/rag/evaluation/PHASE_4C2_HOLDOUT_GOLDEN_RELEVANCE.json | c481a3e541799bfb525ec8528a1224069181fa20c0d679f0b4aeae2914f2f4b5 | c481a3e541799bfb525ec8528a1224069181fa20c0d679f0b4aeae2914f2f4b5 | UNCHANGED |
| docs/rag/evaluation/PHASE_4C2_HOLDOUT_QUERY_SET.json | 7cc73a8c8eaf438243b1109ae2bd8a4e62d2a7982551b5dbc6c0309cb6e68e95 | 7cc73a8c8eaf438243b1109ae2bd8a4e62d2a7982551b5dbc6c0309cb6e68e95 | UNCHANGED |
| docs/rag/evaluation/PHASE_4C2_HOLDOUT_REVIEW.md | d92d032315186f9fe586326912d14b8a1358bc111f90d6e73138615697c98959 | d92d032315186f9fe586326912d14b8a1358bc111f90d6e73138615697c98959 | UNCHANGED |
| docs/rag/evaluation/PHASE_4C3_HOLDOUT_VALIDATION_RESULTS.json | d9fb07b8394f08fbbd216fccbe3cb86ebabdf2840c48f5b617cfaea768d067ae | d9fb07b8394f08fbbd216fccbe3cb86ebabdf2840c48f5b617cfaea768d067ae | UNCHANGED |
| docs/rag/evaluation/PHASE_4C3_HOLDOUT_VALIDATION_REVIEW.md | 625870738d2ca61a9b201bfc2fc478dfb1174f34437bcd8be705a89868f170ba | 625870738d2ca61a9b201bfc2fc478dfb1174f34437bcd8be705a89868f170ba | UNCHANGED |
| docs/rag/evaluation/PHASE_4D5_FULL_BANK_BASELINE_RESULTS.json | 4d0a9109afa4555223f09bd30f30b4f7902d77179fbe2462df6eab175f3fa8c5 | 4d0a9109afa4555223f09bd30f30b4f7902d77179fbe2462df6eab175f3fa8c5 | UNCHANGED |
| docs/rag/evaluation/PHASE_4D5_FULL_BANK_BASELINE_REVIEW.md | 64f87d1f444d161bfefceed4f48ac78c81c3c5f49db58cc11ad80908483be9eb | 64f87d1f444d161bfefceed4f48ac78c81c3c5f49db58cc11ad80908483be9eb | UNCHANGED |
| docs/rag/evaluation/PHASE_4D5_FULL_BANK_GOLDEN_RELEVANCE.json | d4e0fe47c7f103d4fcfb4798345aeeeb8cebdbf09817f015ea6bb30be6a06959 | d4e0fe47c7f103d4fcfb4798345aeeeb8cebdbf09817f015ea6bb30be6a06959 | UNCHANGED |
| docs/rag/evaluation/PHASE_4D5_FULL_BANK_GOLDEN_REVIEW.md | d0b9ca36c6b7f70a05d14a8c4d7c5106d00551a4ca353080a9e01b23a4b9eb40 | d0b9ca36c6b7f70a05d14a8c4d7c5106d00551a4ca353080a9e01b23a4b9eb40 | UNCHANGED |
| docs/rag/full-bank/PHASE_4D1_DOCUMENT_DESIGN_PLAN.md | d2efe9b7ff00b98c732f7adf9665ae3465d8c5c76e8584de3783ff1585474cb7 | d2efe9b7ff00b98c732f7adf9665ae3465d8c5c76e8584de3783ff1585474cb7 | UNCHANGED |
| docs/rag/full-bank/PHASE_4D1_FULL_KB_SOURCE_MANIFEST.md | cff0ce18d45c998dc1332b6f89b19932c9c0881b7ed61d23dc592b85d31f40fb | cff0ce18d45c998dc1332b6f89b19932c9c0881b7ed61d23dc592b85d31f40fb | UNCHANGED |
| docs/rag/full-bank/PHASE_4D1_SCORING_POINT_SOURCE_MATRIX.json | 44b81d35e1e8c6bf40caf068974cc0b3a0ca76b400e49e3161b91ded849684a5 | 44b81d35e1e8c6bf40caf068974cc0b3a0ca76b400e49e3161b91ded849684a5 | UNCHANGED |
| docs/rag/full-bank/PHASE_4D2_AUTHORING_EVIDENCE_MATRIX.json | 022423b8ee1d118a00427611d6fe07a7f2c050d62e55b6582e34b3ff47889c34 | 022423b8ee1d118a00427611d6fe07a7f2c050d62e55b6582e34b3ff47889c34 | UNCHANGED |
| docs/rag/full-bank/PHASE_4D2_COMMANDER_RAW_REVIEW_BUNDLE.txt | a43322a9f2d15178e313588d46553a530b55d8286381870d0c3bf755ec97be9e | a43322a9f2d15178e313588d46553a530b55d8286381870d0c3bf755ec97be9e | UNCHANGED |
| docs/rag/full-bank/PHASE_4D2_DOCUMENT_CONTENT_REVIEW.md | a873ebe1fce2527312bd05dc917fe2228cb82513f59a07e18b4416131405be64 | a873ebe1fce2527312bd05dc917fe2228cb82513f59a07e18b4416131405be64 | UNCHANGED |
| docs/rag/full-bank/PHASE_4D3_CHUNK_REVIEW.md | c2d0e292a21d53708e1ac710c82ff1f32abc83157bcbbd101b4c2262943f05f4 | c2d0e292a21d53708e1ac710c82ff1f32abc83157bcbbd101b4c2262943f05f4 | UNCHANGED |
| docs/rag/full-bank/PHASE_4D3_PRODUCTION_CHUNK_MANIFEST.json | ce60ac53d7075924d7c24ed7ffc3fab2c33bd363a7521c59f20c37e353dfb34c | ce60ac53d7075924d7c24ed7ffc3fab2c33bd363a7521c59f20c37e353dfb34c | UNCHANGED |
| docs/rag/full-bank/PHASE_4D3_SCORING_POINT_CHUNK_COVERAGE.json | 16c67abc7fa02d324ca81cf4f7dd7409be4e3161a1bcd273e7d7059d33e63a04 | 16c67abc7fa02d324ca81cf4f7dd7409be4e3161a1bcd273e7d7059d33e63a04 | UNCHANGED |
| docs/rag/full-bank/PHASE_4D4_CROSS_STORE_INTEGRITY.json | 5cec02ca57298409296ef0e3a8325fdece232619b9e007fbb6d5e31b7289bdc2 | 5cec02ca57298409296ef0e3a8325fdece232619b9e007fbb6d5e31b7289bdc2 | UNCHANGED |
| docs/rag/full-bank/PHASE_4D4_INGESTION_REVIEW.md | ddcf66dcf3dfc3dd77a68e009ba5eef852f2a4458a8279bef541a03a10b80311 | ddcf66dcf3dfc3dd77a68e009ba5eef852f2a4458a8279bef541a03a10b80311 | UNCHANGED |
| docs/rag/full-bank/PHASE_4D4_REAL_INGESTION_MANIFEST.json | 1edd45249080bf4d9f5def8af069afefbd14c088c811f49a1b989b5d3d371c0d | 1edd45249080bf4d9f5def8af069afefbd14c088c811f49a1b989b5d3d371c0d | UNCHANGED |
| docs/rag/source-documents/e01_java21_interface_vs_abstract_class.md | 4b82a2724e53946fffa082560fde1c348201114ac4f490e409c2ddbba5071de0 | 4b82a2724e53946fffa082560fde1c348201114ac4f490e409c2ddbba5071de0 | UNCHANGED |
| docs/rag/source-documents/e02_java21_arraylist_vs_linkedlist.md | 8863861e7379cbb3120926a649eb0243945861732aec9975ce97cdcb7c640dec | 8863861e7379cbb3120926a649eb0243945861732aec9975ce97cdcb7c640dec | UNCHANGED |
| docs/rag/source-documents/e03_spring_6_2_ioc_di.md | d71d9af393307ba3455dab8ffa945e5700f5bec0d3849bae7d37676bbbd352c6 | d71d9af393307ba3455dab8ffa945e5700f5bec0d3849bae7d37676bbbd352c6 | UNCHANGED |
| docs/rag/source-documents/e04_mysql_8_0_transaction_acid.md | fe159ec2e2c0de9c41b78b496e86cfcea89f846efc69940421306d8ba3613ef2 | fe159ec2e2c0de9c41b78b496e86cfcea89f846efc69940421306d8ba3613ef2 | UNCHANGED |
| docs/rag/source-documents/e05_rfc9293_rfc768_tcp_vs_udp.md | caa8af92609855354e83f0037af99e42bb9b3fc65125196103730bb2bf258d26 | caa8af92609855354e83f0037af99e42bb9b3fc65125196103730bb2bf258d26 | UNCHANGED |
| docs/rag/source-documents/h01_java21_generic_type_erasure.md | c11ca06913e220726cb279a36ee65124e5e52c4557cb272c1c37b78975fb565b | c11ca06913e220726cb279a36ee65124e5e52c4557cb272c1c37b78975fb565b | UNCHANGED |
| docs/rag/source-documents/h02_openjdk8u_concurrent_hashmap.md | 7058c59a4c4c11cda492cb6cc565b85c79a09cbec5b7e3cbf1d9587f30114617 | 7058c59a4c4c11cda492cb6cc565b85c79a09cbec5b7e3cbf1d9587f30114617 | UNCHANGED |
| docs/rag/source-documents/h03_hotspot21_g1_gc.md | 3879a27607bfd98ae4e28c329a63c47474fbe75e25607cc660b7a85c89851603 | 3879a27607bfd98ae4e28c329a63c47474fbe75e25607cc660b7a85c89851603 | UNCHANGED |
| docs/rag/source-documents/h04_java21_thread_pool_design.md | d3c05bc195af157dade05d3e99d312668cb7150206601f3542be0e5b2116c8dc | d3c05bc195af157dade05d3e99d312668cb7150206601f3542be0e5b2116c8dc | UNCHANGED |
| docs/rag/source-documents/h05_spring_6_2_transaction_boundary.md | 6933b91b5ed6eda94a223c7026dde68d86c6bd3c35ab1729707b1c2ae5aa2c66 | 6933b91b5ed6eda94a223c7026dde68d86c6bd3c35ab1729707b1c2ae5aa2c66 | UNCHANGED |
| docs/rag/source-documents/h06_mysql_8_0_innodb_mvcc_consistent_read.md | 17f181b4460fb6deca4af8e23a7188df3de3a7640f41c254d39aa0af00a06bea | 17f181b4460fb6deca4af8e23a7188df3de3a7640f41c254d39aa0af00a06bea | UNCHANGED |
| docs/rag/source-documents/h07_redis_distributed_lock_baseline.md | e79aff2f6d42ce77989566e6d7d611de77ac5720f962f325ffbae307a1ae88c7 | e79aff2f6d42ce77989566e6d7d611de77ac5720f962f325ffbae307a1ae88c7 | UNCHANGED |
| docs/rag/source-documents/h08_rfc9293_tcp_connection_lifecycle.md | 82dec99995bddc1c7cd17705743598b1d57ae265da80ececb5220b46e8b5df44 | 82dec99995bddc1c7cd17705743598b1d57ae265da80ececb5220b46e8b5df44 | UNCHANGED |
| docs/rag/source-documents/h09_mysql_8_0_inventory_idempotency_scenario.md | 2ace6486655a2242f4a616b85c57d0cc9dae3f7130381d49326615d6cd72f65b | 2ace6486655a2242f4a616b85c57d0cc9dae3f7130381d49326615d6cd72f65b | UNCHANGED |
| docs/rag/source-documents/m01_java21_equals_hashcode_contract.md | 27a74088733409277eeb02d7e67b02722af8a2a1ec1d40ff1efac5c5596d0387 | 27a74088733409277eeb02d7e67b02722af8a2a1ec1d40ff1efac5c5596d0387 | UNCHANGED |
| docs/rag/source-documents/m02_hashmap_jdk8u_put_resize_treeify.md | c5e93fa3c3d3af8fb6e78b60db2d309ccb4e37a286e6ac47d4bb6f76fb053b22 | c5e93fa3c3d3af8fb6e78b60db2d309ccb4e37a286e6ac47d4bb6f76fb053b22 | UNCHANGED |
| docs/rag/source-documents/m03_jvm21_runtime_data_areas.md | 3daf071761e4dad10738c672acc6781b57a3092edb3e9ee43c943d0899a54d7e | 3daf071761e4dad10738c672acc6781b57a3092edb3e9ee43c943d0899a54d7e | UNCHANGED |
| docs/rag/source-documents/m04_volatile_jmm_java21.md | fd1f73f1e1ce307567bcf6b4cf737315b9c36591f1de3620689a979dea2b760c | fd1f73f1e1ce307567bcf6b4cf737315b9c36591f1de3620689a979dea2b760c | UNCHANGED |
| docs/rag/source-documents/m05_spring_6_2_aop_proxy_self_invocation.md | b622cf633bb2ccf7320c33feede070f3875f7670e7f92c89488e2a5ee5457788 | b622cf633bb2ccf7320c33feede070f3875f7670e7f92c89488e2a5ee5457788 | UNCHANGED |
| docs/rag/source-documents/m06_mysql_8_0_innodb_index_structure.md | 0a11be7250f287eacff2ab77234cb04b1d245ac453127fb1c61a868e305b814c | 0a11be7250f287eacff2ab77234cb04b1d245ac453127fb1c61a868e305b814c | UNCHANGED |
| docs/rag/source-documents/m07_redis_classic_data_structures.md | 893c0e95adc06f18fb2877feb54c7d14f9a69b2329926046f72f129427354caa | 893c0e95adc06f18fb2877feb54c7d14f9a69b2329926046f72f129427354caa | UNCHANGED |
| docs/question-bank/Phase1-Grok题库审查.md | 072c9647b23d2204f004300f9c65821dd8fa9b19e7fe26ec3a7c1d85fad61a01 | 072c9647b23d2204f004300f9c65821dd8fa9b19e7fe26ec3a7c1d85fad61a01 | UNCHANGED |
| docs/question-bank/Phase1D-题库数据库导入计划.md | f4102a42ee5c351f52324db102db01424e9562174de77e5269a2a0127906850e | f4102a42ee5c351f52324db102db01424e9562174de77e5269a2a0127906850e | UNCHANGED |
| docs/question-bank/Phase1D-题库数据库导入验证.md | 0b1a3a24e89326078bc467f5353a6de242797e308c2044345202b3b9d24b4b9a | 0b1a3a24e89326078bc467f5353a6de242797e308c2044345202b3b9d24b4b9a | UNCHANGED |
| docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json | ce6fff9bd42b064009b01c0300e2da5da16e73d04c363e3cf7f2b351cc1b6fd3 | ce6fff9bd42b064009b01c0300e2da5da16e73d04c363e3cf7f2b351cc1b6fd3 | UNCHANGED |
| docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.md | 6278fd0ca2bf48c6256fa9bfd1f72ffa1925bb1a70eb2bb11fa67c5f8a06e296 | 6278fd0ca2bf48c6256fa9bfd1f72ffa1925bb1a70eb2bb11fa67c5f8a06e296 | UNCHANGED |
| docs/evaluation/judge/PHASE_5A_JUDGE_BENCHMARK.json | fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a | fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a | UNCHANGED |
| docs/evaluation/judge/PHASE_5A_JUDGE_BENCHMARK_FREEZE_REPORT.md | 90a6815d2a31a1f6dfc31bebbbaba2fe72f3f5a2115940957c44738c926844be | 90a6815d2a31a1f6dfc31bebbbaba2fe72f3f5a2115940957c44738c926844be | UNCHANGED |
| docs/evaluation/judge/PHASE_5A_JUDGE_BENCHMARK_REVIEW.md | 3c3bac5ccf750e1881dbf32b1c77e4e285174e767f71016b90ed031458e92b69 | 3c3bac5ccf750e1881dbf32b1c77e4e285174e767f71016b90ed031458e92b69 | UNCHANGED |
| docs/evaluation/judge/PHASE_5B_DEEPSEEK_BASELINE_RESULTS.json | af7d26e487a84270777d72934177e5b657655288184421a26214a2a9df250913 | af7d26e487a84270777d72934177e5b657655288184421a26214a2a9df250913 | UNCHANGED |
| docs/evaluation/judge/PHASE_5B_FIRST_CALL_PROTOCOL_DIAGNOSTIC.json | a25f72db95fdb95b0f39f771f7ff45f402aeb88091b8852623e5087f861e329c | a25f72db95fdb95b0f39f771f7ff45f402aeb88091b8852623e5087f861e329c | UNCHANGED |
| docs/evaluation/judge/PHASE_5B_JEV_RESULTS.json | 1cfe251ce0eb35e1d33f70b6a3dc2de6c53b0883064af7b2d28030464a8fa70c | 1cfe251ce0eb35e1d33f70b6a3dc2de6c53b0883064af7b2d28030464a8fa70c | UNCHANGED |
| docs/evaluation/judge/PHASE_5B_JUDGE_COMPARISON_REVIEW.md | 56582267c7d2e15b9893380d34d8a51788b3ea17cfedd6a7643bf4fe0ff79e19 | 56582267c7d2e15b9893380d34d8a51788b3ea17cfedd6a7643bf4fe0ff79e19 | UNCHANGED |
| docs/evaluation/judge/PHASE_5B_PRE_PROVIDER_TOOLING_FAILURE.json | fcdf55560861bdb6c96183f96c42db92c76b214fbe28ae3f01ffb6feda132070 | fcdf55560861bdb6c96183f96c42db92c76b214fbe28ae3f01ffb6feda132070 | UNCHANGED |
| docs/evaluation/judge/PHASE_5B_RUN_EVIDENCE.json | ff375e8af2853c782be2e8e8c415b7513f15ed113aa300930c0a6e3b48938403 | ff375e8af2853c782be2e8e8c415b7513f15ed113aa300930c0a6e3b48938403 | UNCHANGED |
| docs/evaluation/judge/PHASE_5C_FALLBACK_PROOF.md | 169dd2d31a3a9de60b16a1349376e4de085ccb88d1552d4a4adbe62adcab81fb | 169dd2d31a3a9de60b16a1349376e4de085ccb88d1552d4a4adbe62adcab81fb | UNCHANGED |
| docs/evaluation/PHASE_5_JUDGE_VALIDATION_REPORT.md | 151d9f4b4790ead5777ffdd8996ba0bacd7535a0729463ea74fc834a04a7cb5e | 151d9f4b4790ead5777ffdd8996ba0bacd7535a0729463ea74fc834a04a7cb5e | UNCHANGED |
| AGENTS.md | d45fd6f2d5af7e0bb0f1ca3eea84f0a8dd6132a388e102daea5cb5d4a6c8b16f | d45fd6f2d5af7e0bb0f1ca3eea84f0a8dd6132a388e102daea5cb5d4a6c8b16f | UNCHANGED |
| pom.xml | c31fa95239c1c5f2ee12afd1d03fd41fca3f2f04dbc7399b5b2abf7baf4a6773 | c31fa95239c1c5f2ee12afd1d03fd41fca3f2f04dbc7399b5b2abf7baf4a6773 | UNCHANGED |

新增测试SHA-256: ebab2b2ccb5bff0a1e7e3ba921dd3cf63471aa87618660df02e11691db2f1a5d

## 停止门禁

```text
PHASE_5_PROTOCOL_PATCH_OFFLINE_PASS: PASS
CAPTURED_RESPONSE_REPLAY_ACCEPTED: PASS
CLEARLY_INCONSISTENT_RESPONSE_REJECTED: PASS
STRICT_NON_SCORE_CHECKS_PRESERVED: PASS
ROUNDING_ASSUMPTION_DOCUMENTED: PASS
TARGETED_UNIT_TESTS_PASS: PASS
FROZEN_BENCHMARK_SHA_UNCHANGED: PASS
FROZEN_PHASE4_RAG_ARTIFACTS_UNCHANGED: PASS
DB_MILVUS_WRITES_0: PASS
JEV_HTTP_CALLS_0: PASS
DEEPSEEK_CALLS_0: PASS
PRODUCTION_MUTATION_SCOPE_LIMITED: PASS
PHASE_5B_RESTART: BLOCKED_PENDING_COMMANDER_PATCH_REVIEW
PHASE_6: BLOCKED
```
