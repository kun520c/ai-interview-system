import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Experiment-only validator. Exact quotations prove presence, not semantic entailment. */
public final class SemanticEvidenceGate {
    public static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final Set<String> VERDICTS = Set.of("CORE_SUPPORT", "BACKGROUND_ONLY", "NO_SUPPORT", "UNCERTAIN");
    private static final Set<String> VERSIONS = Set.of("MATCH", "NOT_APPLICABLE", "MISMATCH", "UNCERTAIN");

    public static ObjectNode validate(JsonNode input, String raw) throws Exception {
        JsonNode root;
        try (var parser = JSON.createParser(raw)) {
            root = JSON.readTree(parser);
            require(parser.nextToken() == null, "TRAILING_JSON");
        }
        fields(root, "questionId", "mainMechanism", "corePoints", "candidates");
        require(text(root, "questionId").equals(text(input, "questionId")), "QUESTION_ID");
        String question = text(input, "question");
        JsonNode main = root.get("mainMechanism");
        fields(main, "questionQuote", "description");
        questionQuote(question, main);
        Map<String, JsonNode> points = new LinkedHashMap<>();
        array(root.get("corePoints"), 1, 6);
        for (JsonNode p : root.get("corePoints")) {
            fields(p, "id", "questionQuote", "description");
            String id = text(p, "id");
            require(id.matches("p[1-6]") && points.put(id, p) == null, "POINT_ID");
            questionQuote(question, p);
        }
        array(input.get("candidates"), 1, 2);
        Map<String, JsonNode> bodies = new LinkedHashMap<>();
        for (JsonNode c : input.get("candidates")) {
            require(bodies.put(text(c, "candidateId"), c) == null, "INPUT_DUPLICATE");
            text(c, "content");
        }
        array(root.get("candidates"), bodies.size(), bodies.size());
        ObjectNode output = root.deepCopy();
        ArrayNode admitted = output.putArray("admittedCandidateIds");
        Set<String> seen = new HashSet<>(), union = new LinkedHashSet<>();
        for (JsonNode c : output.get("candidates")) {
            fields(c, "candidateId", "verdict", "versionAssessment", "supports", "reason");
            String id = text(c, "candidateId"), verdict = text(c, "verdict"), version = text(c, "versionAssessment");
            require(bodies.containsKey(id) && seen.add(id), "CANDIDATE_ID");
            require(VERDICTS.contains(verdict) && VERSIONS.contains(version), "ENUM");
            text(c, "reason");
            array(c.get("supports"), verdict.equals("CORE_SUPPORT") ? 1 : 0, verdict.equals("CORE_SUPPORT") ? 6 : 0);
            if (verdict.equals("CORE_SUPPORT")) require(Set.of("MATCH", "NOT_APPLICABLE").contains(version), "VERSION_NOT_CONFIRMED");
            Set<String> supported = new LinkedHashSet<>();
            String body = text(bodies.get(id), "content");
            for (JsonNode s : c.get("supports")) {
                fields(s, "pointId", "quote", "explanation");
                String pointId = text(s, "pointId"), quote = text(s, "quote");
                require(points.containsKey(pointId), "SUPPORTED_POINT");
                text(s, "explanation");
                require(quote.length() >= 12 && quote.length() <= 400, "QUOTE_LENGTH");
                int offset = body.indexOf(quote);
                require(offset >= 0, "QUOTE_NOT_IN_BODY");
                require(substantiveQuote(body, quote, offset), "QUOTE_METADATA_OR_REFERENCE_ONLY");
                ((ObjectNode) s).put("verifiedStartUtf16", offset).put("verifiedEndUtf16", offset + quote.length());
                // A core point can have multiple grounded quotations. Coverage counts distinct points,
                // while every quotation (including later quotations for the same point) is validated.
                supported.add(pointId);
            }
            ArrayNode missing = ((ObjectNode) c).putArray("missingPointIds");
            points.keySet().stream().filter(p -> !supported.contains(p)).forEach(missing::add);
            if (verdict.equals("CORE_SUPPORT")) { admitted.add(id); union.addAll(supported); }
        }
        require(seen.equals(bodies.keySet()), "MISSING_CANDIDATE");
        ArrayNode missing = output.putArray("missingPointIdsAcrossAdmitted");
        points.keySet().stream().filter(p -> !union.contains(p)).forEach(missing::add);
        output.put("declaredPointCoverage", union.isEmpty() ? "NONE" :
                union.size() == points.size() ? "ALL_MODEL_DECLARED_POINTS" : "PARTIAL_CORE_SUPPORT");
        output.put("wholeAnswerSufficiency", "NOT_INDEPENDENTLY_VERIFIED");
        output.put("status", "VALIDATED_MODEL_JUDGMENT");
        return output;
    }

    private static boolean substantiveQuote(String body, String quote, int offset) {
        if (quote.strip().matches("https?://\\S+")) return false;
        // Entire line is checked even if the model quotes only the middle of a URL/title.
        int start = body.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        int end = body.indexOf('\n', offset + quote.length());
        if (end < 0) end = body.length();
        String[] lines = body.substring(start, end).split("\\R");
        return Arrays.stream(lines).map(String::strip).anyMatch(line ->
                !line.isBlank() && !line.startsWith("#") && !line.startsWith(">") &&
                !line.matches("(?:[-*]\\s+)?\\[[^]]+].*") &&
                !line.matches("(?:[-*]\\s+)?(?:Primary|Secondary):\\s+\\[[^]]+].*") &&
                !line.startsWith("http://") && !line.startsWith("https://") &&
                !line.equals("```") && !line.matches("```[A-Za-z]*"));
    }
    public static ObjectNode failure(String code) {
        ObjectNode n = JSON.createObjectNode();
        n.put("status", "SAFE_EMPTY").put("failureCode", code)
                .put("wholeAnswerSufficiency", "NOT_ASSESSED");
        n.putArray("admittedCandidateIds");
        return n;
    }
    private static void questionQuote(String question, JsonNode point) {
        require(question.contains(text(point, "questionQuote")), "QUESTION_QUOTE");
        text(point, "description");
    }
    private static void array(JsonNode n, int min, int max) {
        require(n != null && n.isArray() && n.size() >= min && n.size() <= max, "ARRAY_COUNT");
    }
    private static void fields(JsonNode n, String... names) {
        require(n != null && n.isObject(), "OBJECT_REQUIRED");
        Set<String> actual = new HashSet<>(); n.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(Set.of(names)), "FIELDS");
    }
    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        require(v != null && v.isTextual() && !v.textValue().isBlank() && v.textValue().length() <= 16000, "TEXT_" + field);
        return v.textValue();
    }
    private static void require(boolean ok, String code) {
        if (!ok) throw new IllegalArgumentException(code);
    }
}
