import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public final class P4GateRepairOfflineTest {
    private static final List<String> passed = new ArrayList<>();
    private interface Checked { void run() throws Exception; }
    private static void test(String name, Checked check) throws Exception { check.run(); passed.add(name); }
    private static void require(boolean ok) { if (!ok) throw new AssertionError("Repair check failed"); }
    private static void rejected(JsonNode input, JsonNode model, String code) throws Exception {
        try { SemanticEvidenceGate.validate(input, model.toString()); throw new AssertionError("Invalid evidence admitted"); }
        catch (IllegalArgumentException error) { require(error.getMessage().equals(code)); }
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]), destination = Path.of(args[1]);
        JsonNode input = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(root.resolve("target/rag-p4/prepared/inputs/g05.json")));
        JsonNode outer = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(root.resolve("target/rag-p4/run-once/g05/response.json")));
        ObjectNode model = (ObjectNode) SemanticEvidenceGate.JSON.readTree(outer.at("/choices/0/message/content").asText());
        test("real g05: repeated p1 with different real quotes accepted", () -> {
            var result = SemanticEvidenceGate.validate(input, model.toString());
            require(result.at("/admittedCandidateIds").size() == 1 && result.at("/admittedCandidateIds/0").asText().equals("c1"));
            require(result.at("/candidates/0/supports").size() == 3);
            require(result.at("/candidates/0/supports/0/pointId").equals(result.at("/candidates/0/supports/1/pointId")));
            require(!result.at("/candidates/0/supports/0/quote").equals(result.at("/candidates/0/supports/1/quote")));
        });
        test("distinct-point coverage and missing points are not inflated by duplicate p1", () -> {
            var value = model.deepCopy(); ((com.fasterxml.jackson.databind.node.ArrayNode) value.at("/candidates/0/supports")).remove(2);
            var result = SemanticEvidenceGate.validate(input, value.toString());
            require(result.at("/declaredPointCoverage").asText().equals("PARTIAL_CORE_SUPPORT"));
            require(result.at("/candidates/0/missingPointIds/0").asText().equals("p2"));
        });
        test("later same-point quotation must still exist in its own body", () -> {
            var value = model.deepCopy(); ((ObjectNode) value.at("/candidates/0/supports/1")).put("quote", "这是后一次引用中的伪造机制说明，不存在于原文。");
            rejected(input, value, "QUOTE_NOT_IN_BODY");
        });
        test("unknown point ID remains rejected", () -> {
            var value = model.deepCopy(); ((ObjectNode) value.at("/candidates/0/supports/1")).put("pointId", "p6");
            rejected(input, value, "SUPPORTED_POINT");
        });
        test("later same-point heading is still rejected", () -> {
            String heading = input.at("/candidates/0/content").asText().lines().filter(s -> s.startsWith("#") && s.length() >= 12).findFirst().orElseThrow();
            var value = model.deepCopy(); ((ObjectNode) value.at("/candidates/0/supports/1")).put("quote", heading);
            rejected(input, value, "QUOTE_METADATA_OR_REFERENCE_ONLY");
        });
        test("later same-point URL alone is still rejected", () -> {
            String body = input.at("/candidates/1/content").asText();
            String line = body.lines().filter(s -> s.contains("https://")).findFirst().orElseThrow();
            String url = line.substring(line.indexOf("https://")).strip();
            var value = model.deepCopy(); ObjectNode c2 = (ObjectNode) value.at("/candidates/1"); c2.put("verdict", "CORE_SUPPORT");
            var cites = c2.putArray("supports");
            cites.addObject().put("pointId", "p1").put("quote", body.lines().findFirst().orElseThrow()).put("explanation", "Negative-test fixture only");
            cites.addObject().put("pointId", "p1").put("quote", url).put("explanation", "Negative-test fixture only");
            rejected(input, value, "QUOTE_METADATA_OR_REFERENCE_ONLY");
        });
        test("later same-point reference list is still rejected", () -> {
            String body = input.at("/candidates/1/content").asText();
            String line = body.lines().filter(s -> s.startsWith("- Primary:") && s.length() >= 12).findFirst().orElseThrow();
            var value = model.deepCopy(); ObjectNode c2 = (ObjectNode) value.at("/candidates/1"); c2.put("verdict", "CORE_SUPPORT");
            var cites = c2.putArray("supports");
            cites.addObject().put("pointId", "p1").put("quote", body.lines().findFirst().orElseThrow()).put("explanation", "Negative-test fixture only");
            cites.addObject().put("pointId", "p1").put("quote", line).put("explanation", "Negative-test fixture only");
            rejected(input, value, "QUOTE_METADATA_OR_REFERENCE_ONLY");
        });
        test("exact duplicate quote cannot inflate declared point coverage", () -> {
            var value = model.deepCopy(); var array = (com.fasterxml.jackson.databind.node.ArrayNode) value.at("/candidates/0/supports");
            array.remove(2); array.add(array.get(0).deepCopy());
            var result = SemanticEvidenceGate.validate(input, value.toString());
            require(result.at("/missingPointIdsAcrossAdmitted/0").asText().equals("p2"));
        });
        test("duplicate core point declaration is still invalid", () -> {
            var value = model.deepCopy(); ((ObjectNode) value.at("/corePoints/1")).put("id", "p1");
            rejected(input, value, "POINT_ID");
        });
        test("CORE_SUPPORT without quotations is still rejected", () -> {
            var value = model.deepCopy(); ((ObjectNode) value.at("/candidates/0")).putArray("supports");
            rejected(input, value, "ARRAY_COUNT");
        });
        test("version mismatch cannot be admitted as CORE_SUPPORT", () -> {
            var value = model.deepCopy(); ((ObjectNode) value.at("/candidates/0")).put("versionAssessment", "MISMATCH");
            rejected(input, value, "VERSION_NOT_CONFIRMED");
        });
        test("cross-candidate quotation is not accepted", () -> {
            var value = model.deepCopy(); ObjectNode c2 = (ObjectNode) value.at("/candidates/1");
            c2.put("verdict", "CORE_SUPPORT");
            c2.set("supports", value.at("/candidates/0/supports").deepCopy());
            rejected(input, value, "QUOTE_NOT_IN_BODY");
        });
        test("g01 c2 original BACKGROUND_ONLY is preserved by code repair", () -> {
            JsonNode i = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(root.resolve("target/rag-p4/prepared/inputs/g01.json")));
            JsonNode o = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(root.resolve("target/rag-p4/run-once/g01/response.json")));
            var result = SemanticEvidenceGate.validate(i, o.at("/choices/0/message/content").asText());
            require(result.at("/candidates/1/verdict").asText().equals("BACKGROUND_ONLY"));
            require(result.at("/admittedCandidateIds").size() == 1);
        });
        ObjectNode summary = SemanticEvidenceGate.JSON.createObjectNode().put("type", "OFFLINE_GROUNDED_QUOTATION_REPAIR")
                .put("tests", passed.size()).put("failures", 0).put("errors", 0).put("skipped", 0).put("newHttpRequests", 0);
        var checks = summary.putArray("checks"); passed.forEach(checks::add);
        Files.writeString(destination, summary.toPrettyString(), StandardOpenOption.CREATE_NEW);
        System.out.println("P4 repair tests=" + passed.size() + ", failures=0, errors=0, skipped=0; new HTTP=0.");
    }
}
