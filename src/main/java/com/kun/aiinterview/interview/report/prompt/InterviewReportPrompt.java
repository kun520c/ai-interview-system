package com.kun.aiinterview.interview.report.prompt;

public record InterviewReportPrompt(
        String promptVersion,
        String systemPrompt,
        String userPrompt
) {

    public InterviewReportPrompt {
        requireText(promptVersion, "Report Prompt版本");
        requireText(systemPrompt, "Report System Prompt");
        requireText(userPrompt, "Report User Prompt");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "不能为空"
            );
        }
    }
}
