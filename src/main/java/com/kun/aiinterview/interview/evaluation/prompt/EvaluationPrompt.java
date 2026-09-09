package com.kun.aiinterview.interview.evaluation.prompt;

public record EvaluationPrompt(
        String promptVersion,
        String systemPrompt,
        String userPrompt
) {

    public EvaluationPrompt{

        if (promptVersion == null || promptVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "Prompt版本不能为空"
            );
        }

        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException(
                    "System Prompt不能为空"
            );
        }

        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException(
                    "User Prompt不能为空"
            );
        }
    }
}
