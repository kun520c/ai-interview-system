package com.kun.aiinterview.knowledge.admission.experimental;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.knowledge.admission.PrimarySemanticGate;
import com.kun.aiinterview.knowledge.admission.QuestionContract;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Offline experiment only. No Spring registration, provider transport or production fallback. */
public final class CandidateLevelSemanticGate {
    public record Decision(List<RetrievedChunk> evidence, JsonNode diagnostics) {
        public Decision {
            evidence = List.copyOf(evidence);
            diagnostics = diagnostics.deepCopy();
        }

        @Override
        public JsonNode diagnostics() {
            return diagnostics.deepCopy();
        }
    }

    public Decision validate(QuestionContract contract, List<RetrievedChunk> candidates, String raw) {
        ObjectNode diagnostics;
        List<RetrievedChunk> eligible;
        try {
            require(contract != null, "QUESTION_CONTRACT_REQUIRED");
            require(candidates != null && !candidates.isEmpty() && candidates.size() <= 2, "GATE_TOP2_ONLY");
            Set<String> ids = new LinkedHashSet<>();
            for (RetrievedChunk candidate : candidates) {
                require(candidate != null && candidate.vectorId() != null && !candidate.vectorId().isBlank()
                        && ids.add(candidate.vectorId()) && candidate.content() != null && !candidate.content().isBlank(),
                        "CANDIDATE_INPUT");
            }
            require(raw != null && !raw.isBlank(), "MODEL_RESPONSE_REQUIRED");
            // Validate the ORIGINAL raw response in full before isolating anything. No labels are rewritten.
            var strict = new PrimarySemanticGate().validate(contract, candidates, raw);
            diagnostics = (ObjectNode) strict.diagnostics().deepCopy();
            eligible = strict.evidence();
        } catch (JsonProcessingException failure) {
            return invalid(contract, candidates, "INVALID_JSON", failure);
        } catch (IllegalArgumentException failure) {
            return invalid(contract, candidates, failure.getMessage(), failure);
        } catch (Exception failure) {
            return invalid(contract, candidates, "UNSAFE_VALIDATION_FAILURE", failure);
        }

        diagnostics.put("strictValidation", "PASSED");
        ArrayNode sources = diagnostics.putArray("uncertaintySources");
        Set<String> globalReasons = new LinkedHashSet<>();
        Map<String, Set<String>> directByRequirement = new LinkedHashMap<>();
        Map<String, Set<String>> conflictByRequirement = new LinkedHashMap<>();
        for (JsonNode candidate : diagnostics.path("candidates")) {
            String id = candidate.path("candidateId").asText();
            String verdict = candidate.path("verdict").asText();
            if (verdict.equals("UNCERTAIN")) {
                source(sources, "GLOBAL", id, "VERDICT_UNCERTAIN", "");
                globalReasons.add("VERDICT_UNCERTAIN");
            }
            if (candidate.path("versionAssessment").asText().equals("UNCERTAIN")) {
                boolean isolated = verdict.equals("NO_SUPPORT") && candidate.path("supports").isEmpty();
                source(sources, isolated ? "CANDIDATE" : "GLOBAL", id, "VERSION_UNCERTAIN", "");
                if (!isolated) globalReasons.add("VERSION_UNCERTAIN_NOT_ISOLATABLE");
            }
            for (JsonNode support : candidate.path("supports")) {
                String requirement = support.path("requirementId").asText();
                switch (support.path("relation").asText()) {
                    case "UNCERTAIN" -> {
                        source(sources, "GLOBAL", id, "RELATION_UNCERTAIN", requirement);
                        globalReasons.add("RELATION_UNCERTAIN");
                    }
                    case "DIRECT_MECHANISM" -> directByRequirement.computeIfAbsent(requirement, key -> new LinkedHashSet<>()).add(id);
                    case "CONDITION_CONFLICT" -> conflictByRequirement.computeIfAbsent(requirement, key -> new LinkedHashSet<>()).add(id);
                    default -> { /* SUPPORTING_CONTEXT never upgrades a requirement's authoritative role. */ }
                }
            }
        }
        ArrayNode contradictions = diagnostics.putArray("contradictions");
        for (var entry : conflictByRequirement.entrySet()) {
            Set<String> direct = directByRequirement.get(entry.getKey());
            if (direct != null) {
                globalReasons.add("CONTRADICTORY_SUPPORT_RELATIONS");
                ObjectNode conflict = contradictions.addObject().put("requirementId", entry.getKey());
                conflict.set("directCandidateIds", PrimarySemanticGate.JSON.valueToTree(direct));
                conflict.set("conflictCandidateIds", PrimarySemanticGate.JSON.valueToTree(entry.getValue()));
                source(sources, "GLOBAL", "", "CONTRADICTORY_SUPPORT_RELATIONS", entry.getKey());
            }
        }

        List<RetrievedChunk> admitted = globalReasons.isEmpty() ? eligible : List.of();
        diagnostics.set("strictlyEligibleCandidateIds", PrimarySemanticGate.JSON.valueToTree(eligible.stream().map(RetrievedChunk::vectorId).toList()));
        diagnostics.set("globalRejectionReasons", PrimarySemanticGate.JSON.valueToTree(globalReasons));
        finish(contract, candidates, admitted, diagnostics, globalReasons.isEmpty() ?
                (admitted.isEmpty() ? "EMPTY_NO_PRIMARY_SUPPORT" : "ADMITTED") : "SAFE_EMPTY_GLOBAL_REJECTION");
        return new Decision(admitted, diagnostics);
    }

    private static Decision invalid(QuestionContract contract, List<RetrievedChunk> candidates, String code, Exception failure) {
        ObjectNode diagnostics = PrimarySemanticGate.JSON.createObjectNode().put("strictValidation", "FAILED");
        diagnostics.putObject("validationFailure").put("code", code).put("exceptionType", failure.getClass().getSimpleName());
        diagnostics.putArray("globalRejectionReasons").add(code);
        source(diagnostics.putArray("uncertaintySources"), "GLOBAL", "", code, "");
        diagnostics.putArray("strictlyEligibleCandidateIds");
        // Invalid/unconfirmed CORE versions remain global failures, with their exact validation code visible.
        finish(contract, candidates, List.of(), diagnostics, "SAFE_EMPTY_GLOBAL_REJECTION");
        return new Decision(List.of(), diagnostics);
    }

    private static void finish(QuestionContract contract, List<RetrievedChunk> candidates, List<RetrievedChunk> admitted,
                               ObjectNode diagnostics, String status) {
        diagnostics.put("policy", "CANDIDATE_LEVEL_EXPERIMENT_V1").put("status", status)
                .put("globalRejected", status.equals("SAFE_EMPTY_GLOBAL_REJECTION"))
                .put("wholeAnswerSufficiency", "NOT_INDEPENDENTLY_VERIFIED");
        if (contract != null) diagnostics.put("questionId", contract.questionId());
        Set<String> accepted = new LinkedHashSet<>(admitted.stream().map(RetrievedChunk::vectorId).toList());
        diagnostics.set("admittedCandidateIds", PrimarySemanticGate.JSON.valueToTree(accepted));
        ArrayNode rejected = diagnostics.putArray("rejectedCandidateIds");
        if (candidates != null) for (RetrievedChunk candidate : candidates) {
            if (candidate != null && candidate.vectorId() != null && !accepted.contains(candidate.vectorId())) rejected.add(candidate.vectorId());
        }
        Set<String> covered = new LinkedHashSet<>();
        for (JsonNode candidate : diagnostics.path("candidates")) {
            boolean acceptedCandidate = accepted.contains(candidate.path("candidateId").asText());
            ((ObjectNode) candidate).put("admission", acceptedCandidate ? "ADMITTED" :
                    diagnostics.path("globalRejected").asBoolean() ? "REJECTED_GLOBAL" : "REJECTED_VERDICT");
            if (acceptedCandidate) for (JsonNode support : candidate.path("supports")) {
                if (support.path("relation").asText().equals("DIRECT_MECHANISM")
                        && support.path("authoritativeRole").asText().equals("PRIMARY_MECHANISM"))
                    covered.add(support.path("requirementId").asText());
            }
        }
        ArrayNode missing = diagnostics.putArray("missingPrimaryRequirementIdsAcrossAdmitted");
        if (contract != null) contract.requirements().stream().filter(r -> r.role() == QuestionContract.Role.PRIMARY_MECHANISM
                && !covered.contains(r.id())).forEach(r -> missing.add(r.id()));
    }

    private static void source(ArrayNode sources, String scope, String id, String code, String requirement) {
        ObjectNode source = sources.addObject().put("scope", scope).put("code", code);
        if (!id.isEmpty()) source.put("candidateId", id);
        if (!requirement.isEmpty()) source.put("requirementId", requirement);
    }

    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalArgumentException(code);
    }
}
