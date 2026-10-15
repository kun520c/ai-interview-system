package com.kun.aiinterview.knowledge.admission;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import java.util.*;

/** Quote presence and immutable requirement roles are enforced locally; semantic entailment still needs review. */
public final class PrimarySemanticGate {
    public static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    public record Decision(List<RetrievedChunk> evidence, JsonNode diagnostics) {
        public Decision { evidence = List.copyOf(evidence); diagnostics = diagnostics.deepCopy(); }
    }
    public ObjectNode request(QuestionContract contract, List<RetrievedChunk> candidates) {
        if (candidates.isEmpty() || candidates.size() > 2) throw new IllegalArgumentException("GATE_TOP2_ONLY");
        ObjectNode request = JSON.createObjectNode().put("questionId", contract.questionId()).put("question", contract.question())
                .put("versionConstraint", contract.versionConstraint());
        request.set("requirements", JSON.valueToTree(contract.requirements()));
        var array = request.putArray("candidates");
        for (RetrievedChunk c : candidates) array.addObject().put("candidateId", c.vectorId()).put("title", c.title()).put("content", c.content());
        return request;
    }
    public Decision validate(QuestionContract contract, List<RetrievedChunk> candidates, String raw) throws Exception {
        JsonNode root;
        try (var parser = JSON.createParser(raw)) { root = JSON.readTree(parser); require(parser.nextToken() == null, "TRAILING_JSON"); }
        fields(root, "questionId", "candidates");
        require(text(root, "questionId").equals(contract.questionId()), "QUESTION_ID");
        Map<String, QuestionContract.Requirement> requirements = new LinkedHashMap<>();
        contract.requirements().forEach(r -> requirements.put(r.id(), r));
        Map<String, RetrievedChunk> byId = new LinkedHashMap<>();
        require(!candidates.isEmpty() && candidates.size() <= 2, "GATE_TOP2_ONLY");
        for (var c : candidates) require(byId.put(c.vectorId(), c) == null && c.content() != null && !c.content().isBlank(), "CANDIDATE_INPUT");
        array(root.path("candidates"), byId.size(), byId.size());
        ObjectNode diagnostics = root.deepCopy();
        var admittedIds = diagnostics.putArray("admittedCandidateIds");
        List<RetrievedChunk> admitted = new ArrayList<>(); Set<String> seen = new HashSet<>(), covered = new LinkedHashSet<>(); boolean uncertain = false;
        for (JsonNode candidate : diagnostics.path("candidates")) {
            fields(candidate, "candidateId", "verdict", "versionAssessment", "supports", "reason");
            String id = text(candidate, "candidateId"), verdict = text(candidate, "verdict"), version = text(candidate, "versionAssessment");
            require(byId.containsKey(id) && seen.add(id), "CANDIDATE_ID"); text(candidate, "reason");
            require(Set.of("CORE_SUPPORT", "BACKGROUND_ONLY", "NO_SUPPORT", "UNCERTAIN").contains(verdict), "VERDICT");
            uncertain |= verdict.equals("UNCERTAIN");
            require(Set.of("MATCH", "NOT_APPLICABLE", "MISMATCH", "UNCERTAIN").contains(version), "VERSION");
            uncertain |= version.equals("UNCERTAIN");
            array(candidate.path("supports"), 0, 6);
            Set<String> primary = new LinkedHashSet<>();
            for (JsonNode support : candidate.path("supports")) {
                fields(support, "requirementId", "relation", "quote", "explanation");
                String point = text(support, "requirementId"), relation = text(support, "relation"), quote = text(support, "quote");
                require(requirements.containsKey(point), "UNKNOWN_REQUIREMENT"); text(support, "explanation");
                require(Set.of("DIRECT_MECHANISM", "SUPPORTING_CONTEXT", "CONDITION_CONFLICT", "UNCERTAIN").contains(relation), "RELATION");
                require(quote.length() >= 12 && quote.length() <= 400, "QUOTE_LENGTH");
                String body = byId.get(id).content();
                int offset = groundedOffset(body, quote);
                require(offset >= 0, "QUOTE_NOT_GROUNDED_BODY");
                ((ObjectNode) support).put("verifiedStartUtf16", offset).put("verifiedEndUtf16", offset + quote.length())
                        .put("authoritativeRole", requirements.get(point).role().name());
                // A model's DIRECT claim on a contextual ID never upgrades its role.
                if (requirements.get(point).role() == QuestionContract.Role.PRIMARY_MECHANISM && relation.equals("DIRECT_MECHANISM")) primary.add(point);
            }
            if (verdict.equals("CORE_SUPPORT")) {
                require(!primary.isEmpty(), "CORE_REQUIRES_PRIMARY_MECHANISM");
                require(version.equals("MATCH") || (contract.versionConstraint().isBlank() && version.equals("NOT_APPLICABLE")), "CORE_VERSION_UNCONFIRMED");
                admitted.add(byId.get(id)); admittedIds.add(id); covered.addAll(primary);
            } else {
                require(primary.isEmpty(), "VERDICT_PRIMARY_CONTRADICTION");
                if (verdict.equals("NO_SUPPORT")) require(candidate.path("supports").isEmpty(), "NO_SUPPORT_WITH_CLAIMS");
            }
            var missing = ((ObjectNode) candidate).putArray("missingPrimaryRequirementIds");
            requirements.values().stream().filter(r -> r.role() == QuestionContract.Role.PRIMARY_MECHANISM && !primary.contains(r.id()))
                    .forEach(r -> missing.add(r.id()));
        }
        var missing = diagnostics.putArray("missingPrimaryRequirementIdsAcrossAdmitted");
        requirements.values().stream().filter(r -> r.role() == QuestionContract.Role.PRIMARY_MECHANISM && !covered.contains(r.id())).forEach(r -> missing.add(r.id()));
        Set<String> accepted = new HashSet<>(admitted.stream().map(RetrievedChunk::vectorId).toList());
        admitted = candidates.stream().filter(c -> accepted.contains(c.vectorId())).toList();
        admittedIds.removeAll(); admitted.forEach(c -> admittedIds.add(c.vectorId()));
        diagnostics.put("status", admitted.isEmpty() ? (uncertain ? "SAFE_EMPTY_UNCERTAIN" : "EMPTY_NO_PRIMARY_SUPPORT") : (uncertain ? "ADMITTED_WITH_UNCERTAIN_CANDIDATE" : "ADMITTED"))
                .put("wholeAnswerSufficiency", "NOT_INDEPENDENTLY_VERIFIED");
        return new Decision(admitted, diagnostics);
    }
    private static int groundedOffset(String body, String quote) {
        if (quote.strip().matches("https?://\\S+")) return -1;
        for (int offset = body.indexOf(quote); offset >= 0; offset = body.indexOf(quote, offset + 1)) {
            int start = body.lastIndexOf('\n', Math.max(0, offset - 1)) + 1, end = body.indexOf('\n', offset + quote.length());
            if (end < 0) end = body.length();
            boolean substantive = Arrays.stream(body.substring(start, end).split("\\R")).map(String::strip).anyMatch(line ->
                    !line.isBlank() && !line.startsWith("#") && !line.startsWith(">") && !line.startsWith("http") &&
                    !line.matches("(?:[-*]\\s+)?\\[[^]]+].*") && !line.matches("(?:[-*]\\s+)?(?:Primary|Secondary):\\s+\\[[^]]+].*") && !line.matches("```[A-Za-z]*"));
            if (substantive) return offset;
        }
        return -1;
    }
    private static String text(JsonNode n, String key) { JsonNode v = n.get(key); require(v != null && v.isTextual() && !v.asText().isBlank(), "TEXT_" + key); return v.asText(); }
    private static void fields(JsonNode n, String... names) { require(n != null && n.isObject(), "OBJECT"); Set<String> found = new HashSet<>(); n.fieldNames().forEachRemaining(found::add); require(found.equals(Set.of(names)), "FIELDS"); }
    private static void array(JsonNode n, int min, int max) { require(n.isArray() && n.size() >= min && n.size() <= max, "ARRAY_COUNT"); }
    private static void require(boolean ok, String code) { if (!ok) throw new IllegalArgumentException(code); }
}
