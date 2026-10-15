import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.nio.file.*;
import java.util.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Developer-authored responses test contracts, never improvements in real model judgments. */
public final class P41OfflineTest {
    private static final List<String> checks = new ArrayList<>();
    private interface Checked { void run() throws Exception; }
    private static void test(String name, Checked fn) throws Exception { fn.run(); checks.add(name); }
    private static void require(boolean ok) { if (!ok) throw new AssertionError("P4.1 offline check failed"); }
    private static ObjectNode outer(JsonNode content) {
        ObjectNode n = SemanticEvidenceGate.JSON.createObjectNode().put("id", "MOCK_ONLY").put("model", "deepseek-flash");
        n.putArray("choices").addObject().put("index", 0).put("finish_reason", "stop")
                .putObject("message").put("role", "assistant").put("content", content.toString());
        n.putObject("usage").put("prompt_tokens", 100).put("completion_tokens", 200).put("total_tokens", 300);
        return n;
    }
    private static ObjectNode call(JsonNode input, JsonNode model, String prompt) throws Exception {
        var builder = RestClient.builder().baseUrl("https://api.deepseek.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.deepseek.com/chat/completions")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(outer(model).toString(), MediaType.APPLICATION_JSON));
        var capture = new P4DeepSeek.Capture();
        var props = new DeepSeekProperties(); props.setModel("deepseek-v4-flash"); props.setMaxTokens(3072);
        var client = new DeepSeekEvaluationClient(P4DeepSeek.capturing(builder, capture).build(), props, SemanticEvidenceGate.JSON);
        ObjectNode result = P4DeepSeek.evaluate(client, capture, prompt, input);
        server.verify(); require(capture.attempts == 1);
        var sent = SemanticEvidenceGate.JSON.readTree(capture.request);
        require(sent.at("/messages/0/content").asText().equals(prompt));
        require(SemanticEvidenceGate.JSON.readTree(sent.at("/messages/1/content").asText()).equals(input));
        require(sent.at("/response_format/type").asText().equals("json_object") && sent.at("/thinking/type").asText().equals("disabled"));
        require(sent.path("max_tokens").asInt() == 3072);
        return result;
    }
    private static JsonNode load(Path path) throws Exception { return SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(path)); }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]), prepared = root.resolve("target/rag-p4-1/prepared"), output = root.resolve("target/rag-p4-1/offline");
        String prompt = Files.readString(root.resolve("tools/rag-p4-1/system-prompt.txt"));
        JsonNode c01 = load(prepared.resolve("inputs/t1.json"));
        ObjectNode fixture = (ObjectNode) load(prepared.resolve("mock-c01-response.json"));
        test("partial core support retained without all-question sufficiency", () -> {
            var r = call(c01, fixture, prompt);
            require(r.at("/gate/admittedCandidateIds").size() == 2);
            require(r.at("/gate/candidates/1/missingPointIds").size() > 0);
            require(r.at("/gate/wholeAnswerSufficiency").asText().equals("NOT_INDEPENDENTLY_VERIFIED"));
        });
        test("same core point accepts multiple separately grounded quotations", () -> {
            JsonNode input = load(root.resolve("target/rag-p4/prepared/inputs/g05.json"));
            JsonNode response = load(root.resolve("target/rag-p4/run-once/g05/response.json"));
            var r = call(input, SemanticEvidenceGate.JSON.readTree(response.at("/choices/0/message/content").asText()), prompt);
            require(r.at("/gate/admittedCandidateIds").size() == 1 && r.at("/gate/candidates/0/supports").size() == 3);
        });
        test("topic-only N02 and N06 return zero in authored fixtures", () -> {
            for (String id : List.of("t2", "t3")) {
                var r = call(load(prepared.resolve("inputs/" + id + ".json")), load(prepared.resolve("mock-" + id + "-response.json")), prompt);
                require(r.at("/gate/status").asText().equals("VALIDATED_MODEL_JUDGMENT") && r.at("/gate/admittedCandidateIds").isEmpty());
            }
        });
        test("invented quotation fails closed", () -> {
            var v = fixture.deepCopy(); ((ObjectNode) v.at("/candidates/1/supports/0")).put("quote", "不存在于正文的所谓证据，不能当成有效引用。");
            var r = call(c01, v, prompt); require(r.at("/gate/status").asText().equals("SAFE_EMPTY"));
        });
        test("explicit incompatible version cannot be CORE_SUPPORT", () -> {
            var v = fixture.deepCopy(); ((ObjectNode) v.at("/candidates/1")).put("versionAssessment", "MISMATCH");
            var r = call(c01, v, prompt); require(r.at("/gate/failureCode").asText().equals("VERSION_NOT_CONFIRMED"));
        });
        test("UNCERTAIN is valid safe-empty judgment", () -> {
            var v = fixture.deepCopy();
            for (JsonNode c : v.path("candidates")) { ((ObjectNode) c).put("verdict", "UNCERTAIN").put("versionAssessment", "UNCERTAIN").putArray("supports"); }
            var r = call(c01, v, prompt);
            require(r.at("/gate/status").asText().equals("VALIDATED_MODEL_JUDGMENT") && r.at("/gate/admittedCandidateIds").isEmpty());
        });
        test("unknown core point remains rejected", () -> {
            var v = fixture.deepCopy(); ((ObjectNode) v.at("/candidates/1/supports/0")).put("pointId", "p6");
            var r = call(c01, v, prompt); require(r.at("/gate/failureCode").asText().equals("SUPPORTED_POINT"));
        });
        test("format error never admits evidence", () -> {
            var v = fixture.deepCopy(); v.put("unexpected", true);
            var r = call(c01, v, prompt); require(r.at("/gate/status").asText().equals("SAFE_EMPTY") && r.at("/gate/admittedCandidateIds").isEmpty());
        });
        // Actual old responses are replayed unchanged. New prompt cannot retroactively change them.
        ObjectNode historical = SemanticEvidenceGate.JSON.createObjectNode().put("origin", "SAVED_REAL_RESPONSE_REPLAY_NOT_NEW_PROMPT_RESULT");
        var replay = historical.putArray("queries");
        JsonNode expected = load(root.resolve("target/rag-p4-repair/support-point-v1/final-report-v2/repaired-revalidation.json"));
        for (int i = 1; i <= 5; i++) {
            String id = "g%02d".formatted(i);
            JsonNode input = load(root.resolve("target/rag-p4/prepared/inputs/" + id + ".json"));
            JsonNode response = load(root.resolve("target/rag-p4/run-once/" + id + "/response.json"));
            var gate = SemanticEvidenceGate.validate(input, response.at("/choices/0/message/content").asText());
            require(gate.equals(expected.at("/queries/" + (i - 1) + "/gate")));
            replay.addObject().put("questionId", id).set("gate", gate);
        }
        Files.writeString(output.resolve("historical-replay.json"), historical.toPrettyString());
        ObjectNode summary = SemanticEvidenceGate.JSON.createObjectNode().put("tests", checks.size()).put("failures", 0).put("errors", 0)
                .put("skipped", 0).put("realModelCalls", 0).put("historicalRecordsReplayed", 5).put("realPromptImprovementVerified", false);
        var names = summary.putArray("checks"); checks.forEach(names::add);
        Files.writeString(output.resolve("java-tests.json"), summary.toPrettyString());
        System.out.println("P4.1 Java Mock tests=" + checks.size() + ", failures=0, errors=0, skipped=0; historical replay=5 identical; paid=0.");
    }
}
