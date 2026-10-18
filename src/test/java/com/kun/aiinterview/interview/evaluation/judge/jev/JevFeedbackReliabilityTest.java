package com.kun.aiinterview.interview.evaluation.judge.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.judge.JudgeProvider;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekJsonCompletionResult;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class JevFeedbackReliabilityTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JevProperties properties = new JevProperties();
    private final JevClient nativeClient = mock(JevClient.class);
    private final DeepSeekEvaluationClient feedback = mock(DeepSeekEvaluationClient.class);
    private final JudgeProvider fallback = mock(JudgeProvider.class);
    private final EvaluationPrompt prompt = new EvaluationPrompt("offline", "system", "private-answer-prompt");
    private final JudgeProvider.Result fallbackResult = new JudgeProvider.Result("fallback-model", "offline", "{}", null);

    private JsonNode capture(String id) throws Exception {
        try (var input = getClass().getResourceAsStream("/evaluation/jev-e06-feedback-captures.json")) {
            for (var row : mapper.readTree(input)) if (row.path("caseId").asText().equals(id)) return row;
        }
        throw new AssertionError(id);
    }

    private EvaluationContext arrange(JsonNode row) throws Exception {
        when(nativeClient.evaluate(any())).thenReturn(mapper.treeToValue(row.path("nativeResponse"), JevResponse.class));
        when(fallback.evaluate(any(), any())).thenReturn(fallbackResult);
        return mapper.treeToValue(row.path("context"), EvaluationContext.class);
    }

    private JevJudgeProvider provider() {
        return new JevJudgeProvider(nativeClient, properties, feedback, fallback, mapper, new LlmEvaluationValidator());
    }

    private DeepSeekJsonCompletionResult completion(String body) {
        return new DeepSeekJsonCompletionResult("offline-feedback", "stop", body);
    }

    /** Synthetic structural fixture only. Its exact substrings do not establish semantic quality. */
    static ObjectNode structuralFeedback(ObjectMapper mapper, JsonNode nativeResponse, EvaluationContext context) {
        var result = mapper.createObjectNode();
        result.putArray("strengths");
        result.putArray("missingPoints");
        result.putNull("correction");
        var evidence = result.putArray("pointEvidence");
        for (var point : context.scoringPoints()) {
            var entry = evidence.addObject().put("scoringPointId", point.scoringPointId());
            if ("covered".equals(nativeResponse.at("/answers/point_" + point.scoringPointId() + "/choice").asText())) {
                entry.put("evidence", context.mainAnswerContent());
            } else entry.putNull("evidence");
        }
        if ("yes".equals(nativeResponse.at("/answers/followUp/choice").asText())) {
            result.put("suggestedFollowUp", "请进一步说明该机制的边界条件？");
        } else result.putNull("suggestedFollowUp");
        return result;
    }

    @ParameterizedTest
    @ValueSource(strings = {"argument", "state"})
    void internalValidatorErrorsNeverTriggerFeedbackRetryOrFallback(String kind) throws Exception {
        var row = capture("E02-EXCELLENT");
        var context = arrange(row);
        when(feedback.completeJson(anyString(), anyString())).thenReturn(
                completion(structuralFeedback(mapper, row.path("nativeResponse"), context).toString()));
        var internal = mock(LlmEvaluationValidator.class);
        RuntimeException failure = kind.equals("argument")
                ? new IllegalArgumentException("internal defect") : new IllegalStateException("internal defect");
        when(internal.validate(any(), any())).thenThrow(failure);
        var provider = new JevJudgeProvider(nativeClient, properties, feedback, fallback, mapper, internal);
        assertThatThrownBy(() -> provider.evaluate(context, prompt)).isSameAs(failure);
        verify(nativeClient, times(1)).evaluate(any());
        verify(feedback, times(1)).completeJson(anyString(), anyString());
        verifyNoInteractions(fallback);
    }

    @ParameterizedTest
    @ValueSource(strings = {"E02-EXCELLENT", "E02-OFF_TOPIC", "H05-BOUNDARY", "E02-REPAIR", "H09-FAILED_REPAIR"})
    void retriesOnlyFeedbackAndKeepsFrozenNativeDecision(String id, CapturedOutput output) throws Exception {
        var row = capture(id);
        var context = arrange(row);
        when(feedback.completeJson(anyString(), anyString())).thenReturn(
                completion(row.path("feedbackContent").asText()),
                completion(structuralFeedback(mapper, row.path("nativeResponse"), context).toString()));

        var result = provider().evaluate(context, prompt);
        new LlmEvaluationValidator().validate(context, result.suggestion());
        var execution = mapper.readTree(result.rawJson()).path("execution");
        assertThat(execution.path("nativeCalls").asInt()).isEqualTo(1);
        assertThat(execution.path("feedbackCalls").asInt()).isEqualTo(2);
        assertThat(execution.path("fallbackCalls").asInt()).isZero();
        assertThat(execution.path("retryStopReason").asText()).isEqualTo("RECOVERED");
        assertThat(mapper.treeToValue(mapper.readTree(result.rawJson()).path("judge"), JevResponse.class))
                .isEqualTo(mapper.treeToValue(row.path("nativeResponse"), JevResponse.class));
        assertThat(result.suggestion().scoringPointResults()).allSatisfy(point -> assertThat(point.covered())
                .isEqualTo("covered".equals(row.at("/nativeResponse/answers/point_" + point.scoringPointId() + "/choice").asText())));
        assertThat(java.util.List.of(result.suggestion().correctnessScore(), result.suggestion().completenessScore(),
                result.suggestion().depthScore(), result.suggestion().clarityScore(), result.suggestion().practiceScore()))
                .containsExactlyElementsOf(java.util.List.of("correctnessScore", "completenessScore", "depthScore",
                        "clarityScore", "practiceScore").stream().map(dimension -> JevJudgeProvider.mapScore(
                        row.at("/nativeResponse/answers/" + dimension + "/score").doubleValue())).toList());
        assertThat(result.suggestion().followUpRecommended())
                .isEqualTo("yes".equals(row.at("/nativeResponse/answers/followUp/choice").asText()));
        verify(nativeClient, times(1)).evaluate(any());
        verifyNoInteractions(fallback);
        var systems = ArgumentCaptor.forClass(String.class);
        var users = ArgumentCaptor.forClass(String.class);
        verify(feedback, times(2)).completeJson(systems.capture(), users.capture());
        assertThat(users.getAllValues().get(0)).isEqualTo(users.getAllValues().get(1));
        assertThat(systems.getAllValues().get(1)).contains("上次反馈被Java拒绝", "不能改动judgeDecision");
        assertThat(output.getOut()).contains("judge_execution", "\"feedbackCalls\":2")
                .doesNotContain("private-answer-prompt", context.mainAnswerContent(), row.path("feedbackContent").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"E02-EXCELLENT", "E02-OFF_TOPIC", "H05-BOUNDARY", "E02-REPAIR", "H09-FAILED_REPAIR"})
    void repeatedHistoricalFailureRemainsInvalidAndFallsBackOnce(String id, CapturedOutput output) throws Exception {
        var row = capture(id);
        var context = arrange(row);
        when(feedback.completeJson(anyString(), anyString())).thenReturn(completion(row.path("feedbackContent").asText()));
        assertThat(provider().evaluate(context, prompt)).isSameAs(fallbackResult);
        verify(nativeClient, times(1)).evaluate(any());
        verify(feedback, times(2)).completeJson(anyString(), anyString());
        verify(fallback, times(1)).evaluate(context, prompt);
        assertThat(output.getOut()).contains("\"fallbackStage\":\"DEEPSEEK_FEEDBACK\"", "\"retryStopReason\":\"MAX_RETRIES\"");
    }

    @Test
    void configurationCanDisableRetry(CapturedOutput output) throws Exception {
        properties.setFeedbackMaxRetries(0);
        var context = arrange(capture("E02-OFF_TOPIC"));
        when(feedback.completeJson(anyString(), anyString())).thenReturn(completion("{invalid"));
        assertThat(provider().evaluate(context, prompt)).isSameAs(fallbackResult);
        verify(feedback).completeJson(anyString(), anyString());
        assertThat(output.getOut()).contains("FEEDBACK_JSON_INVALID", "\"feedbackCalls\":1");
    }

    @Test
    void nativeCallFailureHasSeparateClassificationAndNeverCallsFeedback(CapturedOutput output) throws Exception {
        var context = arrange(capture("E02-OFF_TOPIC"));
        when(nativeClient.evaluate(any())).thenThrow(new ExternalServiceException("private-api-key-body"));
        assertThat(provider().evaluate(context, prompt)).isSameAs(fallbackResult);
        verify(nativeClient).evaluate(any());
        verifyNoInteractions(feedback);
        assertThat(output.getOut()).contains("NATIVE_CALL_FAILED", "\"fallbackStage\":\"JEV_NATIVE\"")
                .doesNotContain("private-api-key-body");
    }

    @Test
    void nativeInvalidDecisionIsNotRetried(CapturedOutput output) throws Exception {
        var row = capture("E02-OFF_TOPIC");
        var context = arrange(row);
        var invalid = row.path("nativeResponse").deepCopy();
        ((ObjectNode) invalid.at("/answers/correctnessScore")).put("confidence", -1);
        when(nativeClient.evaluate(any())).thenReturn(mapper.treeToValue(invalid, JevResponse.class));
        assertThat(provider().evaluate(context, prompt)).isSameAs(fallbackResult);
        verify(nativeClient).evaluate(any());
        verifyNoInteractions(feedback);
        assertThat(output.getOut()).contains("NATIVE_DECISION_INVALID");
    }

    @Test
    void feedbackTransportFailureIsNotRetried(CapturedOutput output) throws Exception {
        var context = arrange(capture("E02-OFF_TOPIC"));
        when(feedback.completeJson(anyString(), anyString())).thenThrow(new ExternalServiceException("private-response-body"));
        assertThat(provider().evaluate(context, prompt)).isSameAs(fallbackResult);
        verify(feedback).completeJson(anyString(), anyString());
        assertThat(output.getOut()).contains("FEEDBACK_CALL_FAILED", "NON_RETRYABLE").doesNotContain("private-response-body");
    }

    @Test
    void reservePreventsRetryWhenLessThanTwoSecondsRemain(CapturedOutput output) throws Exception {
        var context = arrange(capture("E02-OFF_TOPIC"));
        properties.setRequestTimeout(Duration.ofSeconds(2));
        properties.setFallbackTimeout(Duration.ofSeconds(1));
        when(feedback.completeJson(anyString(), anyString())).thenReturn(completion("{invalid"));
        assertThat(provider().evaluate(context, prompt)).isSameAs(fallbackResult);
        verify(feedback).completeJson(anyString(), anyString());
        verify(fallback).evaluate(context, prompt);
        assertThat(output.getOut()).contains("INSUFFICIENT_BUDGET");
    }

    @Test
    void timedOutFeedbackIsInterruptedBeforeSingleBoundedFallback(CapturedOutput output) throws Exception {
        var context = arrange(capture("E02-OFF_TOPIC"));
        properties.setFeedbackTimeout(Duration.ofMillis(100));
        var cancelled = new CountDownLatch(1);
        when(feedback.completeJson(anyString(), anyString())).thenAnswer(invocation -> {
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException expected) { cancelled.countDown(); Thread.currentThread().interrupt(); }
            throw new ExternalServiceException("cancelled");
        });
        assertThat(provider().evaluate(context, prompt)).isSameAs(fallbackResult);
        assertThat(cancelled.await(2, TimeUnit.SECONDS)).isTrue();
        verify(feedback).completeJson(anyString(), anyString());
        verify(fallback).evaluate(context, prompt);
        assertThat(output.getOut()).contains("CALL_TIMEOUT", "NON_RETRYABLE");
    }

    @Test
    void fallbackTimeoutIsBoundedAndRetainsBothFailureStages(CapturedOutput output) throws Exception {
        var context = arrange(capture("E02-OFF_TOPIC"));
        properties.setRequestTimeout(Duration.ofSeconds(1));
        properties.setFallbackTimeout(Duration.ofMillis(100));
        when(nativeClient.evaluate(any())).thenThrow(new ExternalServiceException("native failed"));
        var cancelled = new CountDownLatch(1);
        when(fallback.evaluate(any(), any())).thenAnswer(invocation -> {
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException expected) { cancelled.countDown(); Thread.currentThread().interrupt(); }
            throw new ExternalServiceException("fallback failed");
        });
        assertThatThrownBy(() -> provider().evaluate(context, prompt)).isInstanceOf(JevStageException.class)
                .hasMessageContaining("CALL_TIMEOUT");
        await().atMost(Duration.ofSeconds(2)).until(() -> cancelled.getCount() == 0);
        verify(fallback).evaluate(context, prompt);
        assertThat(output.getOut()).contains("\"failureClassification\":\"NATIVE_CALL_FAILED\"",
                "\"fallbackFailure\":\"CALL_TIMEOUT\"");
    }

    @Test
    void interruptedCallerPreservesInterruptAndDoesNotDispatchAnyCall() throws Exception {
        var context = arrange(capture("E02-OFF_TOPIC"));
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> provider().evaluate(context, prompt)).isInstanceOf(JevStageException.class)
                    .hasMessageContaining("INTERRUPTED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(nativeClient, feedback, fallback);
        } finally { Thread.interrupted(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate-field", "trailing-object", "numeric-evidence", "extra-score", "normalized-quote"})
    void retryCannotBypassStrictJsonOrEvidenceValidation(String scenario) throws Exception {
        var row = capture("E02-EXCELLENT");
        var context = arrange(row);
        var body = structuralFeedback(mapper, row.path("nativeResponse"), context);
        switch (scenario) {
            case "numeric-evidence" -> ((ObjectNode) body.withArray("pointEvidence").get(0)).put("evidence", 123);
            case "extra-score" -> body.put("correctnessScore", 20);
            case "normalized-quote" -> ((ObjectNode) body.withArray("pointEvidence").get(0)).put("evidence", "fabricated evidence");
        }
        String json = switch (scenario) {
            case "duplicate-field" -> body.toString().replace("\"strengths\":[]", "\"strengths\":[],\"strengths\":[]");
            case "trailing-object" -> body + "{}";
            default -> body.toString();
        };
        when(feedback.completeJson(anyString(), anyString())).thenReturn(completion("{invalid"), completion(json));
        assertThat(provider().evaluate(context, prompt)).isSameAs(fallbackResult);
        verify(nativeClient).evaluate(any());
        verify(feedback, times(2)).completeJson(anyString(), anyString());
        verify(fallback).evaluate(context, prompt);
    }
}
