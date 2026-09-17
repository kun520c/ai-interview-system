package com.kun.aiinterview.interview.model;

import java.util.List;
import java.util.Objects;

public record InterviewReportGenerationResult(
        String result,
        String summary,
        List<String> strengths,
        List<String> weaknesses,
        List<String> suggestions,
        String llmModel,
        String promptVersion
) {

    public InterviewReportGenerationResult {
        requireText(result, "result");
        requireText(summary, "summary");
        requireText(llmModel, "llmModel");
        requireText(promptVersion, "promptVersion");
        strengths = List.copyOf(
                Objects.requireNonNull(strengths, "strengths不能为空")
        );
        weaknesses = List.copyOf(
                Objects.requireNonNull(weaknesses, "weaknesses不能为空")
        );
        suggestions = List.copyOf(
                Objects.requireNonNull(suggestions, "suggestions不能为空")
        );
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "不能为空"
            );
        }
    }
}
