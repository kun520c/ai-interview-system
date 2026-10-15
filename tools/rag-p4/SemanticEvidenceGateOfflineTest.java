import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Mock outcomes are authored test data, never real semantic quality evidence. */
public final class SemanticEvidenceGateOfflineTest {
    private static int tests;
    private interface Checked { void run() throws Exception; }
    private static void test(Checked body) throws Exception { body.run(); tests++; }
    private static void check(boolean ok) { if (!ok) throw new AssertionError("Test failed"); }
    private static void reject(JsonNode input, ObjectNode result) throws Exception {
        try { SemanticEvidenceGate.validate(input, result.toString()); throw new AssertionError("Unsafe result accepted"); }
        catch (IllegalArgumentException expected) { /* validator rejected */ }
    }
    private static String quote(JsonNode candidate) {
        String body = candidate.get("content").asText();
        // Select a real substantive paragraph solely for wire/substring validation, not semantic labeling.
        for (String line : body.split("\\R")) {
            String s = line.strip();
            if (s.length() >= 25 && !s.startsWith("#") && !s.startsWith(">") && !s.startsWith("[") && !s.startsWith("- [") && !s.startsWith("http"))
                return s.substring(0, Math.min(s.length(), 180));
        }
        throw new AssertionError("No fixture quote");
    }
    private static ObjectNode fixture(JsonNode input, String verdict) {
        ObjectNode root = SemanticEvidenceGate.JSON.createObjectNode();
        root.put("questionId", input.get("questionId").asText());
        String anchor = input.get("question").asText().substring(0, 8);
        root.putObject("mainMechanism").put("questionQuote", anchor).put("description", "Fixture mechanism");
        var points = root.putArray("corePoints");
        for (int i = 1; i <= 2; i++) points.addObject().put("id", "p" + i).put("questionQuote", anchor).put("description", "Fixture core point " + i);
        var candidates = root.putArray("candidates");
        for (JsonNode source : input.get("candidates")) {
            ObjectNode c = candidates.addObject().put("candidateId", source.get("candidateId").asText())
                    .put("verdict", verdict).put("versionAssessment", "NOT_APPLICABLE").put("reason", "DEVELOPER_AUTHORED_MOCK_ONLY");
            var supports = c.putArray("supports");
            if (verdict.equals("CORE_SUPPORT")) supports.addObject().put("pointId", "p1").put("quote", quote(source)).put("explanation", "Fixture partial core support, not whole-answer sufficiency");
        }
        return root;
    }
    private static ObjectNode completion(ObjectNode decision, String finish) {
        ObjectNode outer = SemanticEvidenceGate.JSON.createObjectNode().put("id", "mock-completion").put("model", "deepseek-flash");
        ObjectNode choice = outer.putArray("choices").addObject().put("index", 0).put("finish_reason", finish);
        choice.putObject("message").put("role", "assistant").put("content", decision.toString());
        outer.putObject("usage").put("prompt_tokens", 100).put("completion_tokens", 200).put("total_tokens", 300)
                .put("prompt_cache_hit_tokens", 20).put("prompt_cache_miss_tokens", 80);
        return outer;
    }
    private static ObjectNode request(JsonNode input, String system, ObjectNode body, HttpStatus status) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.deepseek.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.deepseek.com/chat/completions")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body.toString()));
        P4DeepSeek.Capture capture = new P4DeepSeek.Capture();
        DeepSeekProperties props = new DeepSeekProperties(); props.setModel("deepseek-v4-flash"); props.setMaxTokens(3072);
        var client = new DeepSeekEvaluationClient(P4DeepSeek.capturing(builder, capture).build(), props, SemanticEvidenceGate.JSON);
        ObjectNode result = P4DeepSeek.evaluate(client, capture, system, input);
        server.verify(); check(capture.attempts == 1);
        try {
            JsonNode sent = SemanticEvidenceGate.JSON.readTree(capture.request);
            check(sent.get("model").asText().equals("deepseek-v4-flash"));
            check(sent.get("max_tokens").asInt() == 3072 && !sent.get("stream").asBoolean());
            check(sent.at("/thinking/type").asText().equals("disabled") && sent.at("/response_format/type").asText().equals("json_object"));
            check(SemanticEvidenceGate.JSON.readTree(sent.at("/messages/1/content").asText()).equals(input));
        } catch (Exception e) { throw new AssertionError(e); }
        return result;
    }
    public static void main(String[] args) throws Exception {
        Path prepared = Path.of(args[0]), offline = Path.of(args[1]);
        String system = Files.readString(Path.of(args[2]), StandardCharsets.UTF_8);
        JsonNode plan = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(prepared.resolve("plan.json")));
        for (JsonNode q : plan.get("queries")) {
            test(() -> {
                JsonNode input = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(Path.of(q.get("payloadPath").asText())));
                boolean positiveFixture = q.get("queryId").asText().contains("-C");
                var decision = fixture(input, positiveFixture ? "CORE_SUPPORT" : "BACKGROUND_ONLY");
                var result = request(input, system, completion(decision, "stop"), HttpStatus.OK);
                check(result.at("/gate/admittedCandidateIds").size() == (positiveFixture ? 2 : 0));
                check(result.at("/usage/prompt_tokens").asInt() == 100);
                if (q.get("queryId").asText().equals("P3-C03")) {
                    check(result.at("/gate/declaredPointCoverage").asText().equals("PARTIAL_CORE_SUPPORT"));
                    check(result.at("/gate/wholeAnswerSufficiency").asText().equals("NOT_INDEPENDENTLY_VERIFIED"));
                }
            });
        }
        JsonNode input = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(prepared.resolve("inputs/g03.json")));
        ObjectNode valid = fixture(input, "CORE_SUPPORT");
        test(() -> { var r = valid.deepCopy(); ((ObjectNode) r.at("/candidates/0/supports/0")).put("quote", "这是原文中完全不存在的编造证据引用。"); reject(input, r); });
        test(() -> { var r = valid.deepCopy(); ((ObjectNode) r.at("/candidates/0")).put("candidateId", "other"); reject(input, r); });
        test(() -> { var r = valid.deepCopy(); ((ObjectNode) r.at("/candidates/1")).put("candidateId", "c1"); reject(input, r); });
        test(() -> { var r = valid.deepCopy(); ((ObjectNode) r.at("/candidates/0")).put("verdict", "BACKGROUND_ONLY"); reject(input, r); });
        test(() -> { var r = valid.deepCopy(); ((ObjectNode) r.at("/candidates/0")).put("versionAssessment", "UNCERTAIN"); reject(input, r); });
        test(() -> { var r = valid.deepCopy(); ((ObjectNode) r.at("/candidates/0/supports/0")).put("pointId", "p9"); reject(input, r); });
        test(() -> { var r = valid.deepCopy(); r.put("extra", "ignored?"); reject(input, r); });
        test(() -> { var r = valid.deepCopy(); ((ObjectNode) r.get("mainMechanism")).put("questionQuote", "问题没有这个词"); reject(input, r); });
        test(() -> { var r = valid.deepCopy(); ((ObjectNode) r.at("/candidates/0/supports/0")).put("quote", "volatile"); reject(input, r); });
        test(() -> { var r = valid.deepCopy(); ((ObjectNode) r.at("/candidates/0/supports/0")).put("quote", input.at("/candidates/0/content").asText().split("\n")[0]); reject(input, r); });
        test(() -> { try { SemanticEvidenceGate.validate(input, valid + " {}"); throw new AssertionError(); } catch (IllegalArgumentException expected) {} });
        test(() -> { try { SemanticEvidenceGate.validate(input, valid.toString().replaceFirst("\\{", "{\"questionId\":\"duplicate\",")); throw new AssertionError(); } catch (com.fasterxml.jackson.core.JsonProcessingException expected) {} });
        test(() -> { var r = request(input, system, completion(valid, "length"), HttpStatus.OK); check(r.at("/gate/status").asText().equals("SAFE_EMPTY")); check(r.at("/gate/admittedCandidateIds").isEmpty()); });
        test(() -> { var r = request(input, system, completion(valid, "stop"), HttpStatus.TOO_MANY_REQUESTS); check(r.at("/gate/status").asText().equals("SAFE_EMPTY")); });
        test(() -> { var outer = completion(valid, "stop"); ((ObjectNode) outer.at("/choices/0/message")).put("content", "not JSON"); var r = request(input, system, outer, HttpStatus.OK); check(r.at("/gate/status").asText().equals("SAFE_EMPTY")); });
        test(() -> { var r = request(input, system, completion(fixture(input, "UNCERTAIN"), "stop"), HttpStatus.OK); check(r.at("/gate/admittedCandidateIds").isEmpty()); });
        ObjectNode summary = SemanticEvidenceGate.JSON.createObjectNode().put("type", "OFFLINE_JAVA_MOCK")
                .put("tests", tests).put("failures", 0).put("errors", 0).put("skipped", 0).put("realRequests", 0)
                .put("semanticQualityVerified", false).put("note", "12 real P3 payloads; developer-authored responses test contracts only");
        Files.writeString(offline.resolve("java-tests.json"), summary.toPrettyString());
        System.out.println("P4 Java Mock tests=" + tests + ", failures=0, errors=0, skipped=0; paid requests=0.");
    }
}
