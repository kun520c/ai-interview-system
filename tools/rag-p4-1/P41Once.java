import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/** Separate P4.1 single-call launcher; shared transport/validator, no old P4 main or run locks. */
public final class P41Once {
    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath().normalize();
        Path input = Path.of(args[0]).toAbsolutePath().normalize(), output = Path.of(args[1]).toAbsolutePath().normalize();
        JsonNode credentials = SemanticEvidenceGate.JSON.readTree(System.in.readAllBytes());
        String key = credentials.path("apiKey").asText().strip();
        String id = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(input)).path("questionId").asText();
        Path prepared = root.resolve("target/rag-p4-1/prepared"), run = root.resolve("target/rag-p4-1/run-once");
        if (!id.matches("t[1-3]") || !input.equals(prepared.resolve("inputs/" + id + ".json")) || !output.equals(run.resolve(id)) ||
                !credentials.path("authorization").asText().equals("AUTHORIZE P4.1 C01 N02 N06 3 ONCE") ||
                key.isBlank() || key.contains("\n") || key.contains("\r")) throw new IllegalArgumentException("P41_AUTHORIZATION_PATH_GUARD");
        String seal = sha(prepared.resolve("freeze.json"));
        JsonNode lock = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(run.resolve("EXECUTION_RESERVED.lock")));
        JsonNode intent = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(output.resolve("REQUEST_INTENT.lock")));
        JsonNode frozen = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(prepared.resolve("freeze.json")));
        Path prompt = root.resolve("tools/rag-p4-1/system-prompt.txt");
        if (!seal.equals(credentials.path("freezeSha256").asText()) || !seal.equals(lock.path("freezeSha256").asText()) ||
                !sha(input).equals(intent.path("payloadSha256").asText()) ||
                !sha(prompt).equals(frozen.path("fileSha256").path(prompt.toString()).asText()))
            throw new IllegalArgumentException("P41_FROZEN_RECEIPT_GUARD");
        Files.writeString(output.resolve("HTTP_ATTEMPT_RESERVED.lock"), "ONE_ATTEMPT_NO_RETRY", StandardOpenOption.CREATE_NEW);
        JsonNode payload = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(input));
        var properties = new DeepSeekProperties(); properties.setModel("deepseek-v4-flash"); properties.setMaxTokens(3072);
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()) {
            var factory = new JdkClientHttpRequestFactory(http); factory.setReadTimeout(Duration.ofSeconds(60));
            var capture = new P4DeepSeek.Capture();
            var builder = RestClient.builder().baseUrl("https://api.deepseek.com").requestFactory(factory)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key);
            var client = new DeepSeekEvaluationClient(P4DeepSeek.capturing(builder, capture).build(), properties, SemanticEvidenceGate.JSON);
            ObjectNode result = P4DeepSeek.evaluate(client, capture, Files.readString(prompt, StandardCharsets.UTF_8), payload);
            for (String name : new String[]{"request", "response"}) {
                byte[] bytes = name.equals("request") ? capture.request : capture.response;
                if (bytes != null) Files.writeString(output.resolve(name + ".json"),
                        new String(bytes, StandardCharsets.UTF_8).replace(key, "[REDACTED]"), StandardOpenOption.CREATE_NEW);
            }
            Files.writeString(output.resolve("decision.json"), result.toPrettyString().replace(key, "[REDACTED]"), StandardOpenOption.CREATE_NEW);
        }
    }
}
