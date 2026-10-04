package com.kun.aiinterview.interview.evaluation.judge;

import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;

/** Supplies untrusted evaluation suggestions; Java still validates and decides. */
public interface JudgeProvider {
    Result evaluate(EvaluationContext context, EvaluationPrompt prompt);

    record Result(String model, String promptVersion, String rawJson,
                  LlmEvaluationSuggestion suggestion) {
    }
}
