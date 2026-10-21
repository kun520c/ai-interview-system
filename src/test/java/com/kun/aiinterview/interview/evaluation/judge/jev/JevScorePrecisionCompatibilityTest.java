package com.kun.aiinterview.interview.evaluation.judge.jev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.interview.evaluation.judge.JudgeProvider;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekJsonCompletionResult;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Offline protocol checks: synthetic context, intercepted HTTP, and mocked text generation only. */
class JevScorePrecisionCompatibilityTest {
    private static final List<String> DIMENSIONS = List.of("correctnessScore", "completenessScore",
            "depthScore", "clarityScore", "practiceScore");
    private final ObjectMapper mapper = new ObjectMapper();
    private final EvaluationContext context = new EvaluationContext(1L, 2L, 1L, 2L,
            EvaluationMode.MAIN_ANSWER, QuestionCategory.JAVA_BASIC, "offline-protocol",
            "仅用于本地协议测试的题目", "本地参考", List.of(
            new ScoringPointSnapshot(101L, QuestionPointType.CORE, "本地评分点", 100)),
            "仅用于本地协议测试的回答", null, null, List.of());
    private final EvaluationPrompt prompt = new EvaluationPrompt("test-prompt", "local system", "local data");
    private final JudgeProvider.Result fallbackResult = new JudgeProvider.Result("offline-fallback",
            "fallback-prompt", "{}", null);
    private MockRestServiceServer server;
    private DeepSeekEvaluationClient generation;
    private JudgeProvider fallback;
    private JevProperties properties;
    private JevJudgeProvider provider;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://localhost");
        server = MockRestServiceServer.bindTo(builder).build();
        generation = mock(DeepSeekEvaluationClient.class);
        fallback = mock(JudgeProvider.class);
        when(fallback.evaluate(context, prompt)).thenReturn(fallbackResult);
        properties = new JevProperties();
        provider = new JevJudgeProvider(new JevClient(builder.build(), mapper), properties,
                generation, fallback, mapper, new LlmEvaluationValidator());
    }

    @AfterEach
    void verifyInterceptedHttp() {
        server.verify();
    }

    @Test
    void replaysAllFiveCapturedNumericAnswersWithoutChangingScoresOrProbabilities() throws Exception {
        // Only numeric protocol data from the incident; no candidate answer or semantic gold is used.
        var response = validResponse();
        putScore(response, "correctnessScore", 3.87, 0, 0, 0, 0.13, 0.87);
        putScore(response, "completenessScore", 3.96, 0, 0, 0, 0.04, 0.96);
        putScore(response, "depthScore", 3.82, 0, 0, 0, 0.18, 0.82);
        putScore(response, "clarityScore", 3.56, 0, 0, 0.02, 0.41, 0.57);
        putScore(response, "practiceScore", 3.82, 0, 0, 0.01, 0.16, 0.83);
        double[] confidence = {0.89, 0.97, 0.85, 0.62, 0.85};
        for (int index = 0; index < DIMENSIONS.size(); index++) {
            answer(response, DIMENSIONS.get(index)).put("confidence", confidence[index]);
        }
        expectResponse(response);
        stubFeedback();

        var result = provider.evaluate(context, prompt);

        assertThat(result.model()).isEqualTo("jev-1.13.0");
        assertThat(result.promptVersion()).isEqualTo(JevJudgeProvider.PROMPT_VERSION);
        var rawJudge = mapper.readTree(result.rawJson()).path("judge");
        // Record serialization adds explicit nulls to optional fields absent in the HTTP JSON.
        assertThat(mapper.treeToValue(rawJudge, JevResponse.class))
                .isEqualTo(mapper.treeToValue(response, JevResponse.class));
        assertThat(rawJudge.at("/answers/clarityScore/score").doubleValue()).isEqualTo(3.56);
        assertThat(List.of(result.suggestion().correctnessScore(), result.suggestion().completenessScore(),
                result.suggestion().depthScore(), result.suggestion().clarityScore(),
                result.suggestion().practiceScore())).containsExactly(19, 20, 19, 18, 19);
        verifyNoInteractions(fallback);
        verify(generation, times(1)).completeJson(anyString(), anyString());
    }

    @Test
    void capturedScoreHasAnExplicitUnroundedProbabilityWitness() {
        List<BigDecimal> witness = List.of(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.016"),
                new BigDecimal("0.410"), new BigDecimal("0.574"));
        assertThat(witness.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1");
        assertThat(witness.stream().map(p -> p.setScale(2, RoundingMode.HALF_UP))).containsExactly(
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.02"),
                new BigDecimal("0.41"), new BigDecimal("0.57"));
        BigDecimal expected = BigDecimal.ZERO;
        for (int index = 0; index < witness.size(); index++) {
            expected = expected.add(witness.get(index).multiply(BigDecimal.valueOf(index)));
        }
        assertThat(expected).isEqualByComparingTo("3.558");
        assertThat(expected.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo("3.56");
    }

    @ParameterizedTest
    @ValueSource(doubles = {3.80, 4.00, 3.50, 3.57})
    void rejectsScoresOutsideCapturedQuantizationIntervals(double score) {
        var response = validResponse();
        putScore(response, "clarityScore", score, 0, 0, 0.02, 0.41, 0.57);
        expectResponse(response);
        assertFallbackBeforeFeedback();
    }

    @ParameterizedTest
    @ValueSource(strings = {"jev-latest", "jev-1.13", "jev-1.13.1", "jev-1.14.0", "unknown"})
    void doesNotExtendObservedDisplayAssumptionToAnotherReturnedModel(String model) {
        var response = validResponse().put("model", model);
        putScore(response, "clarityScore", 3.56, 0, 0, 0.02, 0.41, 0.57);
        expectResponse(response);
        assertFallbackBeforeFeedback();
    }

    @ParameterizedTest
    @ValueSource(strings = {"score", "probability"})
    void higherPrecisionMismatchCannotBorrowTwoDecimalBudget(String field) {
        var response = validResponse();
        putScore(response, "clarityScore", "score".equals(field) ? 3.5601 : 3.56,
                0, 0, 0.02, "probability".equals(field) ? 0.41001 : 0.41,
                "probability".equals(field) ? 0.56999 : 0.57);
        expectResponse(response);
        assertFallbackBeforeFeedback();
    }

    @Test
    void preservesOriginalTightCheckForConsistentHigherPrecisionAndUnknownModels() {
        var response = validResponse().put("model", "jev-other");
        putScore(response, "clarityScore", 3.56789, 0, 0, 0, 0.43211, 0.56789);
        expectResponse(response);
        stubFeedback();

        assertThat(provider.evaluate(context, prompt).suggestion().clarityScore()).isEqualTo(18);
        verifyNoInteractions(fallback);
    }

    @ParameterizedTest
    @CsvSource({"0.00011,false", "-0.00011,false", "0.01,true", "-0.01,false"})
    void rejectsNonGridOrInfeasibleSumsButAcceptsAnExplicitConservingWitness(double change, boolean accepted) {
        var response = validResponse();
        putScore(response, "clarityScore", 3.56, 0, 0, 0.02, 0.41, 0.57 + change);
        expectResponse(response);
        if (!accepted) {
            assertFallbackBeforeFeedback();
            return;
        }
        // The authorized display contract replaces the old unconditional sum rejection.
        // This p* sums to one, rounds to (0,0,.02,.41,.58), and has E=3.56.
        var witness = List.of(new BigDecimal(".00125"), BigDecimal.ZERO,
                new BigDecimal(".015"), new BigDecimal(".405"), new BigDecimal(".57875"));
        assertThat(witness.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1");
        assertThat(witness.stream().map(p -> p.setScale(2, RoundingMode.HALF_UP))).containsExactly(
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.02"),
                new BigDecimal("0.41"), new BigDecimal("0.58"));
        BigDecimal expectation = BigDecimal.ZERO;
        for (int i = 0; i < witness.size(); i++) {
            expectation = expectation.add(witness.get(i).multiply(BigDecimal.valueOf(i)));
        }
        assertThat(expectation).isEqualByComparingTo("3.56");
        stubFeedback();

        assertThat(provider.evaluate(context, prompt).model()).isEqualTo("jev-1.13.0");
        verifyNoInteractions(fallback);
    }

    @ParameterizedTest
    @ValueSource(strings = {"negative", "greater-than-one", "null", "missing-key", "extra-key",
            "null-map", "bad-type", "bad-legend", "missing-legend-key", "extra-legend-key",
            "null-score", "negative-score", "score-above-four", "null-confidence", "confidence-range"})
    void preservesStrictChecksOnResponsesOtherwiseEligibleForCompatibility(String invalid) {
        var response = validResponse();
        putScore(response, "clarityScore", 3.56, 0, 0, 0.02, 0.41, 0.57);
        var score = answer(response, "clarityScore");
        var probabilities = score.withObject("/probabilities");
        switch (invalid) {
            case "negative" -> probabilities.put("0", -0.01);
            case "greater-than-one" -> probabilities.put("4", 1.01);
            case "null" -> probabilities.putNull("0");
            case "missing-key" -> probabilities.remove("0");
            case "extra-key" -> probabilities.put("5", 0);
            case "null-map" -> score.putNull("probabilities");
            case "bad-type" -> score.put("type", "choice");
            case "bad-legend" -> score.withObject("/legend").put("0", "wrong");
            case "missing-legend-key" -> score.withObject("/legend").remove("0");
            case "extra-legend-key" -> score.withObject("/legend").put("5", "extra");
            case "null-score" -> score.putNull("score");
            case "negative-score" -> score.put("score", -0.01);
            case "score-above-four" -> score.put("score", 4.01);
            case "null-confidence" -> score.putNull("confidence");
            case "confidence-range" -> score.put("confidence", 1.01);
            default -> throw new AssertionError(invalid);
        }
        expectResponse(response);
        assertFallbackBeforeFeedback();
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteTypedProbabilityBeforeCompatibility(double invalid) throws Exception {
        var response = mapper.treeToValue(validResponse(), JevResponse.class);
        var answers = new LinkedHashMap<>(response.answers());
        var score = answers.get("clarityScore");
        var probabilities = new LinkedHashMap<>(score.probabilities());
        probabilities.put("0", invalid);
        answers.put("clarityScore", new JevResponse.Answer(score.type(), score.score(), null,
                probabilities, score.confidence(), score.legend()));
        var client = mock(JevClient.class);
        when(client.evaluate(any())).thenReturn(new JevResponse(response.model(), answers));
        provider = new JevJudgeProvider(client, properties, generation, fallback, mapper,
                new LlmEvaluationValidator());
        assertFallbackBeforeFeedback();
        verify(client, times(1)).evaluate(any());
    }

    @Test
    void lowConfidenceStillTriggersExactlyOneFallbackBeforeFeedback() {
        properties.setMinConfidence(0.9);
        var response = validResponse();
        putScore(response, "clarityScore", 3.56, 0, 0, 0.02, 0.41, 0.57);
        answer(response, "clarityScore").put("confidence", 0.89);
        expectResponse(response);
        assertFallbackBeforeFeedback();
    }

    @Test
    void choiceArgmaxMustRemainConsistentEvenWhenScoresQualifyForCompatibility() {
        var response = validResponse();
        putScore(response, "clarityScore", 3.56, 0, 0, 0.02, 0.41, 0.57);
        answer(response, "point_101").put("choice", "uncovered");
        expectResponse(response);
        assertFallbackBeforeFeedback();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void acceptsEveryExactSinglePointDistributionIncludingZeroAndFour(int level) {
        var response = validResponse();
        double[] probabilities = new double[5];
        probabilities[level] = 1;
        putScore(response, "clarityScore", level, probabilities);
        expectResponse(response);
        stubFeedback();

        assertThat(provider.evaluate(context, prompt).suggestion().clarityScore()).isEqualTo(level * 5);
        verifyNoInteractions(fallback);
    }

    @ParameterizedTest
    @CsvSource({"0,0.02,true", "0,0.03,false", "1,1.02,true", "1,1.03,false",
            "2,1.99,true", "2,2.01,true", "2,2.02,false", "3,2.99,true",
            "3,2.98,true", "3,2.97,false", "4,3.98,true", "4,3.97,false"})
    void respectsClippedFeasibleEndpointsAndUnitSumRatherThanAnAbsoluteBudget(
            int level, double score, boolean accepted) {
        var response = validResponse();
        double[] probabilities = new double[5];
        probabilities[level] = 1;
        putScore(response, "clarityScore", score, probabilities);
        expectResponse(response);
        if (accepted) {
            stubFeedback();

            assertThat(provider.evaluate(context, prompt).suggestion().clarityScore())
                    .isEqualTo(JevJudgeProvider.mapScore(score));
            verifyNoInteractions(fallback);
        } else {
            assertFallbackBeforeFeedback();
        }
    }

    @ParameterizedTest
    @CsvSource({"0.01,0.99,3.94,true", "0.01,0.99,3.98,true", "0.01,0.99,3.99,false",
            "0.99,0.01,0.02,true", "0.99,0.01,0.06,true", "0.99,0.01,0.07,false",
            "0.33,0.67,2.68,true"})
    void checksNearZeroOneClippingAndDecimalEndpointsWithoutExtraEpsilon(
            double zeroProbability, double fourProbability, double score, boolean accepted) {
        var response = validResponse();
        putScore(response, "clarityScore", score, zeroProbability, 0, 0, 0, fourProbability);
        expectResponse(response);
        if (accepted) {
            stubFeedback();

            assertThat(provider.evaluate(context, prompt).model()).isEqualTo("jev-1.13.0");
            verifyNoInteractions(fallback);
        } else {
            assertFallbackBeforeFeedback();
        }
    }

    @Test
    void rejectsAdversarialScoreThatIndependentErrorBudgetWouldAdmit() {
        // Independent budget: sum(i * 0.005) + 0.005 = 0.055, so it would admit 3.97.
        // Unit-sum constrained probabilities with reported p4=1 have E >= 3.98;
        // reported score 3.97 only allows E <= 3.975, hence no feasible intersection.
        var response = validResponse();
        putScore(response, "clarityScore", 3.97, 0, 0, 0, 0, 1);
        expectResponse(response);
        assertFallbackBeforeFeedback();
    }

    private ObjectNode validResponse() {
        var response = mapper.createObjectNode().put("model", "jev-1.13.0");
        var answers = response.putObject("answers");
        provider.buildRequest(context, prompt).questions().forEach((key, question) -> {
            var answer = answers.putObject(key).put("type", question.type()).put("confidence", 1);
            if ("score".equals(question.type())) {
                var legend = answer.putObject("legend");
                var levels = (List<?>) question.criteria();
                for (int index = 0; index < levels.size(); index++) {
                    legend.set(Integer.toString(index), mapper.valueToTree(levels.get(index)));
                }
                answer.put("score", 4);
                var probabilities = answer.putObject("probabilities");
                for (int index = 0; index < 5; index++) {
                    probabilities.put(Integer.toString(index), index == 4 ? 1 : 0);
                }
            } else {
                boolean followUp = "followUp".equals(key);
                answer.put("choice", followUp ? "no" : "covered");
                answer.putObject("probabilities").put(followUp ? "yes" : "uncovered", 0)
                        .put(followUp ? "no" : "covered", 1);
            }
        });
        return response;
    }

    private ObjectNode answer(ObjectNode response, String key) {
        return (ObjectNode) response.path("answers").path(key);
    }

    private void putScore(ObjectNode response, String dimension, double score, double... probabilities) {
        var answer = answer(response, dimension).put("score", score);
        var values = answer.putObject("probabilities");
        for (int index = 0; index < probabilities.length; index++) {
            values.put(Integer.toString(index), probabilities[index]);
        }
    }

    private void expectResponse(ObjectNode response) {
        server.expect(requestTo("http://localhost/v1/systemone"))
                .andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
    }

    private void stubFeedback() {
        when(generation.completeJson(anyString(), anyString())).thenReturn(new DeepSeekJsonCompletionResult(
                "offline-generation", "stop", """
                {"strengths":[],"missingPoints":[],"correction":null,
                "pointEvidence":[{"scoringPointId":101,"evidence":"仅用于本地协议测试的回答"}],
                "suggestedFollowUp":null}
                """));
    }

    private void assertFallbackBeforeFeedback() {
        assertThat(provider.evaluate(context, prompt)).isSameAs(fallbackResult);
        verify(fallback, times(1)).evaluate(context, prompt);
        verify(generation, never()).completeJson(anyString(), anyString());
        verifyNoInteractions(generation);
    }
}
