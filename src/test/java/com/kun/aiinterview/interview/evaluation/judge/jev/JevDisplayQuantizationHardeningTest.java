package com.kun.aiinterview.interview.evaluation.judge.jev;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.List;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Numeric-only capture replay and synthetic adversarial boundaries; all HTTP is intercepted in memory. */
class JevDisplayQuantizationHardeningTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final EvaluationContext context = new EvaluationContext(1L, 2L, 1L, 2L,
            EvaluationMode.MAIN_ANSWER, QuestionCategory.JAVA_BASIC, "offline-hardening",
            "本地题目", "本地参考", List.of(new ScoringPointSnapshot(101L, QuestionPointType.CORE, "本地点", 100)),
            "本地回答", null, null, List.of());
    private final EvaluationPrompt prompt = new EvaluationPrompt("offline", "local system", "local data");
    private final JudgeProvider.Result fallbackResult = new JudgeProvider.Result("offline-fallback", "offline", "{}", null);
    private DeepSeekEvaluationClient feedback;
    private JudgeProvider fallback;
    private JevJudgeProvider provider;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://localhost");
        server = MockRestServiceServer.bindTo(builder).build();
        feedback = mock(DeepSeekEvaluationClient.class);
        fallback = mock(JudgeProvider.class);
        when(fallback.evaluate(context, prompt)).thenReturn(fallbackResult);
        provider = new JevJudgeProvider(new JevClient(builder.build(), mapper), new JevProperties(),
                feedback, fallback, mapper, new LlmEvaluationValidator());
    }

    @AfterEach
    void verifyMemoryHttp() { server.verify(); }

    @ParameterizedTest
    @ValueSource(strings = {"E02-UNKNOWN", "M04-PARTIAL", "M04-BOUNDARY", "H05-EXCELLENT", "H05-UNKNOWN"})
    void replaysFiveNumericCapturesWithoutChangingAnyReturnedNumber(String caseId) throws Exception {
        JsonNode fixture = null;
        try (var stream = getClass().getResourceAsStream("/evaluation/jev-e05-numeric-captures.json")) {
            for (var item : mapper.readTree(stream)) {
                if (caseId.equals(item.path("caseId").asText())) { fixture = item; break; }
            }
        }
        assertThat(fixture).isNotNull();
        var response = validResponse();
        fixture.path("numericAnswers").fields().forEachRemaining(entry -> response.withObject("/answers")
                .set(entry.getKey(), entry.getValue()));
        expect(response);
        stubFeedback();
        var result = provider.evaluate(context, prompt);
        assertThat(result.model()).isEqualTo("jev-1.13.0");
        var retained = mapper.readTree(result.rawJson()).path("judge").path("answers");
        fixture.path("numericAnswers").fields().forEachRemaining(entry -> {
            var actual = retained.path(entry.getKey());
            assertThat(actual.path("score")).isEqualTo(entry.getValue().path("score"));
            assertThat(actual.path("probabilities")).isEqualTo(entry.getValue().path("probabilities"));
            assertThat(actual.path("confidence")).isEqualTo(entry.getValue().path("confidence"));
            assertThat(actual.path("legend")).isEqualTo(entry.getValue().path("legend"));
        });
        verifyNoInteractions(fallback);
        verify(feedback, times(1)).completeJson(anyString(), anyString());
    }

    @ParameterizedTest
    @CsvSource({"score,1,true", "score,-1,true", "score,2,false", "score,-2,false",
            "probability,1,true", "probability,-1,true", "probability,2,false", "probability,-2,false"})
    void onlyImmediateBinaryNeighborsQualifyForGridCompatibility(String field, int steps, boolean accepted) {
        var response = validResponse();
        double changed = "score".equals(field) ? 2.42 : .47;
        for (int i = 0; i < Math.abs(steps); i++) { changed = steps > 0 ? Math.nextUp(changed) : Math.nextDown(changed); }
        score(response, "score".equals(field) ? changed : 2.42, .01, .12, .35,
                "probability".equals(field) ? changed : .47, .05);
        expect(response);
        assertOutcome(accepted);
    }

    @ParameterizedTest
    @CsvSource({"0.99,1.98,true", "1.01,2.02,true", "0.99,2.03,false", "1.01,1.97,false",
            "0.96,2.0,false", "1.04,2.0,false"})
    void nonUnitDisplayedSumNeedsJointUnitMassAndScoreFeasibility(double sum, double score, boolean accepted) {
        var response = validResponse();
        double last = BigDecimal.valueOf(sum).subtract(new BigDecimal(".80")).doubleValue();
        score(response, score, .20, .20, .20, .20, last);
        expect(response);
        assertOutcome(accepted);
    }

    @ParameterizedTest
    @ValueSource(doubles = {.471, .4700001})
    void genuineHigherPrecisionCannotBorrowTheDisplayInterval(double probability) {
        var response = validResponse();
        score(response, 2.42, .01, .12, .35, probability, .05);
        expect(response);
        assertOutcome(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jev-1.13.1", "jev-1.14.0", "jev-latest", "unknown"})
    void otherModelsKeepStrictSumAndExpectationRules(String model) {
        var response = validResponse().put("model", model);
        score(response, 1.98, .20, .20, .20, .20, .19);
        expect(response);
        assertOutcome(false);
    }

    @ParameterizedTest
    @ValueSource(doubles = {.99, 1.01})
    void choiceProbabilityMassStillUsesOriginalStrictRule(double sum) {
        var response = validResponse();
        var p = response.withObject("/answers/point_101/probabilities");
        p.put("covered", sum < 1 ? .99 : 1).put("uncovered", sum < 1 ? 0 : .01);
        expect(response);
        assertOutcome(false);
    }

    @ParameterizedTest
    @CsvSource({"zero,1,true", "zero,2,false", "one,-1,true", "one,-2,false"})
    void adjacentBinaryNeighborsAtClippedProbabilityEndpoints(String endpoint, int steps, boolean accepted) {
        var response = validResponse();
        double p = "zero".equals(endpoint) ? 0 : 1;
        for (int i = 0; i < Math.abs(steps); i++) { p = steps > 0 ? Math.nextUp(p) : Math.nextDown(p); }
        score(response, 3.98, "zero".equals(endpoint) ? p : 0, 0, 0, 0, "one".equals(endpoint) ? p : 1);
        expect(response);
        assertOutcome(accepted);
    }

    @Test
    void zeroMassCannotPassEvenWithZeroScore() {
        var response = validResponse();
        score(response, 0, 0, 0, 0, 0, 0);
        expect(response);
        assertOutcome(false);
    }

    private ObjectNode validResponse() {
        var response = mapper.createObjectNode().put("model", "jev-1.13.0");
        var answers = response.putObject("answers");
        provider.buildRequest(context, prompt).questions().forEach((key, question) -> {
            var a = answers.putObject(key).put("type", question.type()).put("confidence", 1);
            if ("score".equals(question.type())) {
                a.put("score", 4);
                var legend = a.putObject("legend");
                var levels = (List<?>) question.criteria();
                for (int i = 0; i < 5; i++) { legend.set(Integer.toString(i), mapper.valueToTree(levels.get(i))); }
                var p = a.putObject("probabilities");
                for (int i = 0; i < 5; i++) { p.put(Integer.toString(i), i == 4 ? 1 : 0); }
            } else {
                boolean follow = key.equals("followUp");
                a.put("choice", follow ? "no" : "covered");
                a.putObject("probabilities").put(follow ? "no" : "covered", 1).put(follow ? "yes" : "uncovered", 0);
            }
        });
        return response;
    }

    private void score(ObjectNode response, double score, double... probabilities) {
        var answer = response.withObject("/answers/clarityScore").put("score", score);
        var p = answer.putObject("probabilities");
        for (int i = 0; i < 5; i++) { p.put(Integer.toString(i), probabilities[i]); }
    }

    private void expect(ObjectNode response) {
        server.expect(requestTo("http://localhost/v1/systemone"))
                .andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
    }

    private void stubFeedback() {
        when(feedback.completeJson(anyString(), anyString())).thenReturn(new DeepSeekJsonCompletionResult(
                "offline-feedback", "stop", """
                {"strengths":[],"missingPoints":[],"correction":null,
                "pointEvidence":[{"scoringPointId":101,"evidence":"本地回答"}],"suggestedFollowUp":null}
                """));
    }

    private void assertOutcome(boolean accepted) {
        if (accepted) {
            stubFeedback();

            assertThat(provider.evaluate(context, prompt).model()).isEqualTo("jev-1.13.0");
            verifyNoInteractions(fallback);
            verify(feedback, times(1)).completeJson(anyString(), anyString());
        } else {
            assertThat(provider.evaluate(context, prompt)).isSameAs(fallbackResult);
            verify(fallback, times(1)).evaluate(context, prompt);
            verifyNoInteractions(feedback);
        }
    }
}
