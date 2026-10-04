package com.kun.aiinterview.interview.evaluation.judge.jev;

import java.util.Map;

/** Official System One request. Criteria are a Score array or a Choice map. */
public record JevRequest(String model, String state, Map<String, Question> questions) {
    public JevRequest {
        questions = Map.copyOf(questions);
    }

    public record Question(String type, Object instructions, Object criteria) {
    }
}
