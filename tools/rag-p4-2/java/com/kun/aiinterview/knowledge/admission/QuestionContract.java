package com.kun.aiinterview.knowledge.admission;

import java.util.*;

/** Authoritative question requirements, authored before candidates; never accepted from model output. */
public record QuestionContract(String questionId, String question, String versionConstraint, List<Requirement> requirements) {
    public enum Role { PRIMARY_MECHANISM, SUPPORTING_CONTEXT }
    public record Requirement(String id, Role role, String questionQuote, String description) {}
    public QuestionContract {
        if (questionId == null || questionId.isBlank() || question == null || question.isBlank() || versionConstraint == null)
            throw new IllegalArgumentException("QUESTION_CONTRACT_REQUIRED");
        requirements = List.copyOf(requirements);
        if (requirements.isEmpty() || requirements.size() > 9) throw new IllegalArgumentException("REQUIREMENT_COUNT");
        Set<String> ids = new HashSet<>();
        for (Requirement r : requirements) {
            if (r == null || r.id() == null || !r.id().matches("r[1-9]") || !ids.add(r.id()) || r.role() == null ||
                    r.questionQuote() == null || r.questionQuote().isBlank() || !question.contains(r.questionQuote()) ||
                    r.description() == null || r.description().isBlank() || r.description().length() > 200)
                throw new IllegalArgumentException("INVALID_QUESTION_REQUIREMENT");
        }
        if (requirements.stream().noneMatch(r -> r.role() == Role.PRIMARY_MECHANISM)) throw new IllegalArgumentException("PRIMARY_REQUIRED");
    }
}
