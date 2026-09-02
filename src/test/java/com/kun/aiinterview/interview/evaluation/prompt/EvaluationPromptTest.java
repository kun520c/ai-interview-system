package com.kun.aiinterview.interview.evaluation.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationPromptTest {

    private static final String PROMPT_VERSION = "evaluation-prompt-v1";
    private static final String SYSTEM_PROMPT = "system prompt";
    private static final String USER_PROMPT = "user prompt";

    @Test
    void givenValidFields_whenCreatingPrompt_thenPreservesValues() {
        EvaluationPrompt prompt = new EvaluationPrompt(
                PROMPT_VERSION,
                SYSTEM_PROMPT,
                USER_PROMPT
        );

        assertThat(prompt).isEqualTo(new EvaluationPrompt(
                PROMPT_VERSION,
                SYSTEM_PROMPT,
                USER_PROMPT
        ));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void givenMissingOrBlankPromptVersion_whenCreatingPrompt_thenRejects(
            String promptVersion
    ) {
        assertThatThrownBy(() -> new EvaluationPrompt(
                promptVersion,
                SYSTEM_PROMPT,
                USER_PROMPT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void givenMissingOrBlankSystemPrompt_whenCreatingPrompt_thenRejects(
            String systemPrompt
    ) {
        assertThatThrownBy(() -> new EvaluationPrompt(
                PROMPT_VERSION,
                systemPrompt,
                USER_PROMPT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("System Prompt");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void givenMissingOrBlankUserPrompt_whenCreatingPrompt_thenRejects(
            String userPrompt
    ) {
        assertThatThrownBy(() -> new EvaluationPrompt(
                PROMPT_VERSION,
                SYSTEM_PROMPT,
                userPrompt
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User Prompt");
    }
}
