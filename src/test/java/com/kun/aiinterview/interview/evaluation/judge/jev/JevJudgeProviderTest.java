package com.kun.aiinterview.interview.evaluation.judge.jev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.evaluation.*;
import com.kun.aiinterview.interview.evaluation.decision.FollowUpPolicy;
import com.kun.aiinterview.interview.evaluation.decision.FollowUpTargetResolver;
import com.kun.aiinterview.interview.evaluation.judge.DeepSeekJudgeProvider;
import com.kun.aiinterview.interview.evaluation.llm.DeepSeekEvaluationResult;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekJsonCompletionResult;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPromptBuilder;
import com.kun.aiinterview.interview.evaluation.score.EvaluationScoreCalculator;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class JevJudgeProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmEvaluationValidator validator = new LlmEvaluationValidator();
    private MockRestServiceServer server;
    private DeepSeekEvaluationClient deepSeek;
    private JevProperties properties;
    private JevJudgeProvider provider;
    private EvaluationContext context;
    private EvaluationPrompt prompt;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://localhost");
        server = MockRestServiceServer.bindTo(builder).build();
        deepSeek = mock(DeepSeekEvaluationClient.class);
        properties = new JevProperties();
        provider = new JevJudgeProvider(new JevClient(builder.build(), mapper), properties,
                deepSeek, new DeepSeekJudgeProvider(deepSeek), mapper, validator);
        useMode(EvaluationMode.MAIN_ANSWER);
    }

    @AfterEach
    void verifyHttp() {
        server.verify();
    }

    @Test
    void mapsJevDecisionsAndUsesDeepSeekOnlyForTextThenRetainsJavaFollowUpVeto() throws Exception {
        server.expect(requestTo("http://localhost/v1/systemone"))
                .andExpect(jsonPath("$.questions.correctnessScore.type").value("score"))
                .andExpect(jsonPath("$.questions.point_101.instructions.scoringPoint.id").value(101))
                .andExpect(jsonPath("$.questions.point_101.instructions.scoringPoint.weight").value(60))
                .andExpect(jsonPath("$.state").value(prompt.userPrompt()))
                .andRespond(withSuccess(validResponse().toString(), MediaType.APPLICATION_JSON));
        stubFeedback(validFeedback("CAS保护状态推进", "请解释并发冲突"));
        var result = provider.evaluate(context, prompt);
        assertThat(result.model()).isEqualTo("jev-1.13.0");
        assertThat(result.promptVersion()).isEqualTo("jev-judge-v1");
        var validated = validator.validate(context, result.suggestion());
        var score = new EvaluationScoreCalculator().calculate(validated);
        assertThat(score.totalScore()).isEqualTo(85);
        assertThat(validated.scoringPointResults()).containsExactly(
                new LlmEvaluationSuggestion.ScoringPointResult(101L, true, "CAS保护状态推进"),
                new LlmEvaluationSuggestion.ScoringPointResult(102L, false, null));
        assertThat(validated.followUpRecommended()).isTrue();
        assertThat(new FollowUpPolicy(new FollowUpTargetResolver())
                .decide(context, validated, score, true).decisionAction()).isEqualTo(DecisionAction.NEXT_MAIN);
        var raw = mapper.readTree(result.rawJson());
        assertThat(raw.at("/judge/answers/point_101/confidence").doubleValue()).isEqualTo(0.8);
        assertThat(raw.at("/generation/model").textValue()).isEqualTo("test-deepseek");
        verify(deepSeek, never()).evaluate(any());
        verify(deepSeek).completeJson(argThat(system -> system.contains("不能重评或覆盖")),
                argThat(user -> user.contains("\"correctnessScore\":17")));
    }

    @Test
    void followUpAnswerUsesCombinedAnswersAndCannotAskAnotherFollowUp() {
        useMode(EvaluationMode.FOLLOW_UP_ANSWER);
        assertThat(provider.buildRequest(context, prompt).questions()).doesNotContainKey("followUp");
        server.expect(requestTo("http://localhost/v1/systemone"))
                .andExpect(jsonPath("$.state").value(prompt.userPrompt()))
                .andRespond(withSuccess(validResponse().toString(), MediaType.APPLICATION_JSON));
        stubFeedback(validFeedback("版本号比较避免重复推进", null));
        var result = provider.evaluate(context, prompt);
        assertThat(result.suggestion().followUpRecommended()).isFalse();
        assertThat(result.suggestion().suggestedFollowUp()).isNull();
        assertThat(result.suggestion().scoringPointResults().getFirst().evidence())
                .isEqualTo("版本号比较避免重复推进");
        verify(deepSeek, never()).evaluate(any());
    }

    @Test
    void mainAnswerWithoutJevRecommendationHasNoGeneratedFollowUp() {
        ObjectNode response = validResponse();
        var followUp = answer(response, "followUp");
        followUp.put("choice", "no");
        followUp.withObject("/probabilities").put("yes", 0.1).put("no", 0.9);
        expectResponse(response.toString());
        stubFeedback(validFeedback("CAS保护状态推进", null));
        var result = provider.evaluate(context, prompt);
        assertThat(result.suggestion().followUpRecommended()).isFalse();
        assertThat(result.suggestion().suggestedFollowUp()).isNull();
        verify(deepSeek, never()).evaluate(any());
    }

    @Test
    void generationServiceFailureFallsBackToCompleteOriginalJudge() {
        expectResponse(validResponse().toString());
        when(deepSeek.completeJson(anyString(), anyString()))
                .thenThrow(new ExternalServiceException("generation unavailable"));
        assertFallback();
    }

    @Test
    void defaultZeroConfidenceThresholdDoesNotIntroduceAnUncalibratedPolicy() {
        ObjectNode response = validResponse();
        answer(response, "correctnessScore").put("confidence", 0);
        expectResponse(response.toString());
        stubFeedback(validFeedback("CAS保护状态推进", "请解释并发冲突"));
        assertThat(provider.evaluate(context, prompt).model()).startsWith("jev");
        verify(deepSeek, never()).evaluate(any());
    }

    @Test
    void configuredLowConfidenceFallsBackBeforeGeneration() {
        properties.setMinConfidence(0.9);
        expectResponse(validResponse().toString());
        assertFallback();
        verify(deepSeek, never()).completeJson(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"covered", "uncovered"})
    void nullSelectedChoiceProbabilityFallsBackBeforeGeneration(String choice) {
        var response = validResponse();
        var point = answer(response, "point_101");
        point.put("choice", choice);
        // Put the selected null value last, after a valid alternative probability.
        var probabilities = point.putObject("probabilities");
        probabilities.put("covered".equals(choice) ? "uncovered" : "covered", 0.1);
        probabilities.putNull(choice);
        expectResponse(response.toString());
        assertFallback();
        verify(deepSeek, never()).completeJson(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null-pointer", "index-out-of-bounds"})
    void programmingFailurePropagatesWithoutFallback(String scenario) {
        expectResponse(validResponse().toString());
        RuntimeException failure = "null-pointer".equals(scenario)
                ? new NullPointerException("internal defect") : new IndexOutOfBoundsException("internal defect");
        when(deepSeek.completeJson(anyString(), anyString())).thenThrow(failure);
        assertThatThrownBy(() -> provider.evaluate(context, prompt)).isSameAs(failure);
        verify(deepSeek, never()).evaluate(any());
        verify(deepSeek, times(1)).completeJson(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void mergesAllScoringPointsByIdAndPreservesEveryJevScore(int count) {
        var points = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new ScoringPointSnapshot(101L + index, QuestionPointType.CORE,
                        "评分点" + index, 100 / count + (index == 0 ? 100 % count : 0))).toList();
        context = new EvaluationContext(context.currentAnswerId(), context.currentInterviewQuestionId(),
                context.mainAnswerId(), context.mainInterviewQuestionId(), context.mode(), context.category(),
                context.knowledgePoint(), context.mainQuestionContent(), context.referenceAnswerSnapshot(),
                points, context.mainAnswerContent(), null, null, List.of());
        prompt = new EvaluationPromptBuilder().build(context,
                new EvaluationRetrievalResult("query without answer", 5, "embedding", "v1", List.of(), List.of()));
        expectResponse(validResponse().toString());
        var feedback = mapper.createObjectNode();
        feedback.putArray("strengths");
        feedback.putArray("missingPoints");
        feedback.put("suggestedFollowUp", "请解释并发冲突");
        var evidence = feedback.putArray("pointEvidence");
        // Generation order may differ from the frozen scoring-point order.
        for (var point : points.reversed()) {
            evidence.addObject().put("scoringPointId", point.scoringPointId())
                    .put("evidence", point.scoringPointId() == 101L ? "CAS保护状态推进" : null);
        }
        stubFeedback(feedback.toString());
        var result = provider.evaluate(context, prompt);
        var suggestion = result.suggestion();
        assertThat(List.of(suggestion.correctnessScore(), suggestion.completenessScore(), suggestion.depthScore(),
                suggestion.clarityScore(), suggestion.practiceScore())).containsExactly(17, 17, 17, 17, 17);
        assertThat(suggestion.scoringPointResults()).extracting(LlmEvaluationSuggestion.ScoringPointResult::scoringPointId)
                .containsExactlyElementsOf(points.stream().map(ScoringPointSnapshot::scoringPointId).toList());
        assertThat(suggestion.scoringPointResults()).allSatisfy(point -> {
            assertThat(point.covered()).isEqualTo(point.scoringPointId() == 101L);
            assertThat(point.evidence()).isEqualTo(point.scoringPointId() == 101L ? "CAS保护状态推进" : null);
        });
        verify(deepSeek, never()).evaluate(any());
        verify(deepSeek, times(1)).completeJson(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500, 529})
    void httpFailureFallsBackOnceToOriginalDeepSeekJudge(int status) {
        server.expect(requestTo("http://localhost/v1/systemone"))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)).body("private-data"));
        assertFallback();
        verify(deepSeek, never()).completeJson(anyString(), anyString());
    }

    @Test
    void timeoutFallsBackOnceToOriginalDeepSeekJudge() {
        server.expect(requestTo("http://localhost/v1/systemone"))
                .andRespond(request -> { throw new SocketTimeoutException(); });
        assertFallback();
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "missing-point", "extra-point", "type", "score-null", "score-range",
            "score-expectation", "confidence-null", "confidence-range", "distribution-null", "probability-null",
            "probability-range", "probability-sum", "legend", "choice", "choice-probability"})
    void invalidJevDecisionFallsBackBeforeGeneration(String scenario) {
        var response = validResponse();
        var score = answer(response, "correctnessScore");
        String body = response.toString();
        switch (scenario) {
            case "malformed" -> body = "{broken-json";
            case "missing-point" -> response.withObject("/answers").remove("point_101");
            case "extra-point" -> response.withObject("/answers").putObject("unexpected");
            case "type" -> score.put("type", "noul");
            case "score-null" -> score.putNull("score");
            case "score-range" -> score.put("score", 5);
            case "score-expectation" -> score.put("score", 2);
            case "confidence-null" -> score.putNull("confidence");
            case "confidence-range" -> score.put("confidence", 1.1);
            case "distribution-null" -> score.putNull("probabilities");
            case "probability-null" -> score.withObject("/probabilities").putNull("0");
            case "probability-range" -> score.withObject("/probabilities").put("0", -0.1);
            case "probability-sum" -> score.withObject("/probabilities").put("0", 0.5);
            case "legend" -> score.withObject("/legend").put("0", "wrong rubric");
            case "choice" -> answer(response, "point_101").put("choice", "maybe");
            case "choice-probability" -> answer(response, "point_101").put("choice", "uncovered");
            default -> throw new AssertionError(scenario);
        }
        expectResponse("malformed".equals(scenario) ? body : response.toString());
        assertFallback();
        verify(deepSeek, never()).completeJson(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "null", "invented-evidence", "duplicate", "foreign-id", "missing-id",
            "missing-strengths", "blank-strength", "missing-followup", "score-override", "covered-override"})
    void illegalGeneratedFeedbackFallsBackInsteadOfChangingJevDecisions(String scenario) throws Exception {
        expectResponse(validResponse().toString());
        String body = validFeedback("CAS保护状态推进", "请解释并发冲突");
        var feedback = (ObjectNode) mapper.readTree(body);
        switch (scenario) {
            case "malformed" -> body = "{invalid";
            case "null" -> body = "null";
            case "invented-evidence" -> feedback.withArray("pointEvidence").get(0)
                    .withObject("").put("evidence", "from reference answer only");
            case "duplicate" -> feedback.withArray("pointEvidence").add(feedback.withArray("pointEvidence").get(0));
            case "foreign-id" -> feedback.withArray("pointEvidence").get(0).withObject("").put("scoringPointId", 999);
            case "missing-id" -> feedback.withArray("pointEvidence").remove(0);
            case "missing-strengths" -> feedback.remove("strengths");
            case "blank-strength" -> feedback.withArray("strengths").add(" ");
            case "missing-followup" -> feedback.putNull("suggestedFollowUp");
            case "score-override" -> feedback.put("correctnessScore", 20);
            case "covered-override" -> feedback.put("covered", false);
            default -> throw new AssertionError(scenario);
        }
        stubFeedback(List.of("malformed", "null").contains(scenario) ? body : feedback.toString());
        assertFallback();
    }

    @Test
    void fallbackFailurePropagatesWithoutRecursingOrInventingScores() {
        expectResponse("{}");
        var failure = new ExternalServiceException("DeepSeek unavailable");
        when(deepSeek.evaluate(prompt)).thenThrow(failure);
        assertThatThrownBy(() -> provider.evaluate(context, prompt)).isSameAs(failure);
        verify(deepSeek, times(1)).evaluate(prompt);
    }

    private void useMode(EvaluationMode mode) {
        context = new EvaluationContext(1L, 2L, 1L, 2L, mode, QuestionCategory.JAVA_BASIC, "CAS",
                "如何防止重复推进？", "使用版本号", List.of(
                new ScoringPointSnapshot(101L, QuestionPointType.CORE, "CAS机制", 60),
                new ScoringPointSnapshot(102L, QuestionPointType.KEY, "事务回滚", 40)),
                "CAS保护状态推进", mode == EvaluationMode.FOLLOW_UP_ANSWER ? "如何判断冲突？" : null,
                mode == EvaluationMode.FOLLOW_UP_ANSWER ? "版本号比较避免重复推进" : null,
                mode == EvaluationMode.FOLLOW_UP_ANSWER ? List.of(101L) : List.of());
        prompt = new EvaluationPromptBuilder().build(context,
                new EvaluationRetrievalResult("query without answer", 5, "embedding", "v1", List.of(), List.of()));
    }

    private ObjectNode validResponse() {
        var response = mapper.createObjectNode().put("model", "jev-1.13.0");
        var answers = response.putObject("answers");
        provider.buildRequest(context, prompt).questions().forEach((id, question) -> {
            var answer = answers.putObject(id).put("type", question.type());
            var probabilities = answer.putObject("probabilities");
            if ("score".equals(question.type())) {
                answer.put("score", 3.4).put("confidence", 0.6666667);
                var legend = answer.putObject("legend");
                var levels = (List<?>) question.criteria();
                for (int index = 0; index < levels.size(); index++) {
                    legend.set(Integer.toString(index), mapper.valueToTree(levels.get(index)));
                    probabilities.put(Integer.toString(index), index == 3 ? 0.6 : index == 4 ? 0.4 : 0);
                }
            } else {
                boolean followUp = "followUp".equals(id);
                String choice = followUp ? "yes" : "point_101".equals(id) ? "covered" : "uncovered";
                answer.put("choice", choice).put("confidence", 0.8);
                probabilities.put(followUp ? "yes" : "covered", followUp || "covered".equals(choice) ? 0.9 : 0.1);
                probabilities.put(followUp ? "no" : "uncovered", followUp || "covered".equals(choice) ? 0.1 : 0.9);
            }
        });
        return response;
    }

    private ObjectNode answer(ObjectNode response, String id) {
        return (ObjectNode) response.path("answers").path(id);
    }

    private String validFeedback(String quote, String followUp) {
        var feedback = mapper.createObjectNode();
        feedback.putArray("strengths").add("理解CAS");
        feedback.putArray("missingPoints").add("缺少事务回滚说明");
        feedback.put("correction", "补充事务边界");
        var points = feedback.putArray("pointEvidence");
        points.addObject().put("scoringPointId", 101).put("evidence", quote);
        points.addObject().put("scoringPointId", 102).putNull("evidence");
        feedback.put("suggestedFollowUp", followUp);
        return feedback.toString();
    }

    private void expectResponse(String body) {
        server.expect(requestTo("http://localhost/v1/systemone"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void stubFeedback(String json) {
        when(deepSeek.completeJson(anyString(), anyString()))
                .thenReturn(new DeepSeekJsonCompletionResult("test-deepseek", "stop", json));
    }

    private void assertFallback() {
        var original = new LlmEvaluationSuggestion(10, 11, 12, 13, 14, List.of(), List.of(), null,
                List.of(new LlmEvaluationSuggestion.ScoringPointResult(101L, true, "original evidence"),
                        new LlmEvaluationSuggestion.ScoringPointResult(102L, false, null)), false, null);
        when(deepSeek.evaluate(prompt)).thenReturn(new DeepSeekEvaluationResult("original-deepseek", "stop", "{}", original));
        var result = provider.evaluate(context, prompt);
        assertThat(result.model()).isEqualTo("original-deepseek");
        assertThat(result.promptVersion()).isEqualTo(prompt.promptVersion());
        assertThat(result.rawJson()).isEqualTo("{}");
        assertThat(result.suggestion()).isSameAs(original);
        verify(deepSeek, times(1)).evaluate(prompt);
    }
}
