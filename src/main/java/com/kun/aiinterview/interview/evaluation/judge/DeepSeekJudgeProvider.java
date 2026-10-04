package com.kun.aiinterview.interview.evaluation.judge;

import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;

/** Keeps the original DeepSeek request, parsing and metadata unchanged. */
public class DeepSeekJudgeProvider implements JudgeProvider {
    private final DeepSeekEvaluationClient client;

    public DeepSeekJudgeProvider(DeepSeekEvaluationClient client) {
        this.client = client;
    }

    @Override
    public Result evaluate(EvaluationContext context, EvaluationPrompt prompt) {
        var result = client.evaluate(prompt);
        return new Result(result.model(), prompt.promptVersion(),
                result.rawJson(), result.suggestion());
    }
}
