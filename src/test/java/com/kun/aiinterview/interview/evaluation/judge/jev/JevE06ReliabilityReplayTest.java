package com.kun.aiinterview.interview.evaluation.judge.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.interview.evaluation.*;
import com.kun.aiinterview.interview.evaluation.judge.DeepSeekJudgeProvider;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import com.kun.aiinterview.interview.evaluation.score.EvaluationScoreCalculator;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Synthetic protocol regression. Historical 33/38 quality statistics are NOT reproduced here. */
class JevE06ReliabilityReplayTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Stream<JsonNode> scenarios() throws Exception {
        try (var input = JevE06ReliabilityReplayTest.class.getResourceAsStream(
                "/evaluation/jev-reliability-synthetic-scenarios.json")) {
            assertThat(input).as("public classpath fixture").isNotNull();
            var fixture = MAPPER.readTree(input);
            assertThat(fixture.path("provenance").asText()).contains("synthetic");
            return java.util.stream.StreamSupport.stream(fixture.path("scenarios").spliterator(), false).toList().stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void verifiesSyntheticCompositionThroughRealHttpAdapters(JsonNode scenario) throws Exception {
        int retries = scenario.path("retries").asInt();
        int invalidAttempts = scenario.path("invalidFeedbackAttempts").asInt();
        boolean nativeInvalid = scenario.path("nativeInvalid").asBoolean();
        boolean fallbackInvalid = scenario.path("fallbackInvalid").asBoolean();
        int feedbackCount = nativeInvalid ? 0 : Math.min(invalidAttempts + 1, retries + 1);
        boolean fallsBack = nativeInvalid || invalidAttempts > retries;
        var context = new EvaluationContext(1L, 2L, 1L, 2L, EvaluationMode.MAIN_ANSWER,
                QuestionCategory.JAVA_BASIC, "synthetic-cas", "Explain CAS", "Compare and swap",
                List.of(new ScoringPointSnapshot(101L, QuestionPointType.CORE, "CAS", 100)),
                "CAS compares and swaps atomically", null, null, List.of());
        var prompt = new EvaluationPrompt("synthetic-v1", "synthetic system", context.mainAnswerContent());
        var nativeBuilder = RestClient.builder().baseUrl("http://jev.offline");
        var nativeServer = MockRestServiceServer.bindTo(nativeBuilder).build();
        var feedbackBuilder = RestClient.builder().baseUrl("http://deepseek.offline");
        var feedbackServer = MockRestServiceServer.bindTo(feedbackBuilder).build();
        var properties = new JevProperties();
        properties.setFeedbackMaxRetries(retries);
        var deepSeekProperties = new DeepSeekProperties();
        deepSeekProperties.setBaseUrl(URI.create("http://deepseek.offline"));
        var generation = new DeepSeekEvaluationClient(feedbackBuilder.build(), deepSeekProperties, MAPPER);
        var validator = new LlmEvaluationValidator();
        var provider = new JevJudgeProvider(new JevClient(nativeBuilder.build(), MAPPER), properties,
                generation, new DeepSeekJudgeProvider(generation), MAPPER, validator);
        var nativeBody = nativeResponse(provider.buildRequest(context, prompt));
        var nativeCalls = new AtomicInteger();
        nativeServer.expect(requestTo("http://jev.offline/v1/systemone"))
                .andRespond(request -> {
                    nativeCalls.incrementAndGet();
                    return withSuccess(nativeInvalid ? "{}" : nativeBody.toString(), MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });
        String validFeedback = JevFeedbackReliabilityTest.structuralFeedback(MAPPER, nativeBody, context).toString();
        var feedbackCalls = new AtomicInteger();
        var fallbackCalls = new AtomicInteger();
        for (int index = 0; index < feedbackCount; index++) {
            String content = index < invalidAttempts ? "{invalid-synthetic-feedback" : validFeedback;
            feedbackServer.expect(requestTo("http://deepseek.offline/chat/completions"))
                    .andExpect(request -> assertThat(((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString()).doesNotContain("{invalid-synthetic-feedback"))
                    .andRespond(request -> {
                        feedbackCalls.incrementAndGet();
                        return withSuccess(envelope(content), MediaType.APPLICATION_JSON).createResponse(request);
                    });
        }
        if (fallsBack) {
            var baseline = new LlmEvaluationSuggestion(fallbackInvalid ? 21 : 10, 11, 12, 13, 14,
                    List.of("synthetic strength"), List.of(), null,
                    List.of(new LlmEvaluationSuggestion.ScoringPointResult(101L, true, context.mainAnswerContent())), false, null);
            feedbackServer.expect(requestTo("http://deepseek.offline/chat/completions"))
                    .andRespond(request -> {
                        fallbackCalls.incrementAndGet();
                        return withSuccess(envelope(MAPPER.writeValueAsString(baseline)), MediaType.APPLICATION_JSON)
                                .createResponse(request);
                    });
        }
        var result = provider.evaluate(context, prompt);
        if (fallbackInvalid) {
            assertThatThrownBy(() -> validator.validate(context, result.suggestion()))
                    .isInstanceOf(ExternalServiceException.class);
        } else {
            var score = new EvaluationScoreCalculator().calculate(validator.validate(context, result.suggestion()));
            assertThat(score.totalScore()).isEqualTo(fallsBack ? 60 : 100);
        }
        assertThat(nativeCalls.get()).isEqualTo(1);
        assertThat(feedbackCalls.get()).isEqualTo(feedbackCount);
        assertThat(fallbackCalls.get()).isEqualTo(fallsBack ? 1 : 0);
        if (!fallsBack) {
            var raw = MAPPER.readTree(result.rawJson());
            assertThat(MAPPER.treeToValue(raw.path("judge"), JevResponse.class))
                    .isEqualTo(MAPPER.treeToValue(nativeBody, JevResponse.class));
            assertThat(raw.at("/execution/nativeCalls").asInt()).isEqualTo(1);
            assertThat(raw.at("/execution/feedbackCalls").asInt()).isEqualTo(feedbackCount);
            assertThat(result.suggestion().scoringPointResults().getFirst().covered()).isTrue();
        }
        nativeServer.verify();
        feedbackServer.verify(); // Unexpected HTTP requests fail; neither builder has a network transport.
    }

    private static ObjectNode nativeResponse(JevRequest request) {
        // Same request-driven rubric construction as JevJudgeProviderTest; no captured model output.
        var response = MAPPER.createObjectNode().put("model", "jev-synthetic");
        var answers = response.putObject("answers");
        request.questions().forEach((id, question) -> {
            var answer = answers.putObject(id).put("type", question.type()).put("confidence", 1.0);
            var probabilities = answer.putObject("probabilities");
            if ("score".equals(question.type())) {
                answer.put("score", 4.0);
                var legend = answer.putObject("legend");
                var levels = (List<?>) question.criteria();
                for (int index = 0; index < levels.size(); index++) {
                    legend.set(Integer.toString(index), MAPPER.valueToTree(levels.get(index)));
                    probabilities.put(Integer.toString(index), index == 4 ? 1.0 : 0.0);
                }
            } else {
                boolean follow = "followUp".equals(id);
                answer.put("choice", follow ? "no" : "covered");
                probabilities.put(follow ? "yes" : "covered", follow ? 0.0 : 1.0);
                probabilities.put(follow ? "no" : "uncovered", follow ? 1.0 : 0.0);
            }
        });
        return response;
    }

    private static String envelope(String content) throws com.fasterxml.jackson.core.JsonProcessingException {
        return MAPPER.writeValueAsString(java.util.Map.of("model", "deepseek-synthetic", "choices", List.of(
                java.util.Map.of("index", 0, "finish_reason", "stop", "message",
                        java.util.Map.of("role", "assistant", "content", content)))));
    }
}
