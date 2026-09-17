package com.kun.aiinterview.interview.evaluation.llm.deepseek;

public record DeepSeekJsonCompletionResult(
        String model,
        String finishReason,
        String rawJson
) {
}
