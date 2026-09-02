package com.kun.aiinterview.interview.evaluation.llm;

public record DeepSeekEvaluationResult(
        String model,
        String finishReason,
        String rawJson,
        LlmEvaluationSuggestion suggestion
) {
}
