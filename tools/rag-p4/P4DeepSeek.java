import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

/** One provider request; reuse project-owned JSON completion, retain experimental usage separately. */
public final class P4DeepSeek {
    public static final class Capture {
        public byte[] request, response;
        public int attempts, status;
    }
    public static RestClient.Builder capturing(RestClient.Builder builder, Capture capture) {
        return builder.requestInterceptor((request, body, execution) -> {
            if (++capture.attempts != 1) throw new IOException("SECOND_REQUEST_FORBIDDEN");
            capture.request = body.clone();
            try (ClientHttpResponse response = execution.execute(request, body)) {
                capture.status = response.getStatusCode().value();
                byte[] bytes = response.getBody().readNBytes(1024 * 1024 + 1);
                if (bytes.length > 1024 * 1024) throw new IOException("RESPONSE_TOO_LARGE");
                capture.response = bytes;
                HttpStatusCode status = response.getStatusCode();
                String statusText = response.getStatusText();
                HttpHeaders headers = new HttpHeaders(); headers.putAll(response.getHeaders());
                return new ClientHttpResponse() {
                    private final InputStream input = new ByteArrayInputStream(bytes);
                    public HttpStatusCode getStatusCode() { return status; }
                    public String getStatusText() { return statusText; }
                    public HttpHeaders getHeaders() { return headers; }
                    public InputStream getBody() { return input; }
                    public void close() { try { input.close(); } catch (IOException ignored) {} }
                };
            }
        });
    }
    public static ObjectNode evaluate(DeepSeekEvaluationClient client, Capture capture, String system, JsonNode input) {
        ObjectNode result = SemanticEvidenceGate.JSON.createObjectNode();
        long start = System.nanoTime();
        try {
            var completion = client.completeJson(system, SemanticEvidenceGate.JSON.writeValueAsString(input));
            result.put("returnedModel", completion.model()).put("finishReason", completion.finishReason());
            if (!Set.of("deepseek-v4-flash", "deepseek-flash", "deepseek-v4.1-flash").contains(completion.model()))
                throw new IllegalArgumentException("UNEXPECTED_MODEL_NO_FALLBACK");
            result.set("gate", SemanticEvidenceGate.validate(input, completion.rawJson()));
        } catch (Exception exception) {
            // Exception messages and provider error bodies can contain credentials; report safe codes only.
            String code = exception instanceof IllegalArgumentException ? exception.getMessage() : exception.getClass().getSimpleName();
            result.set("gate", SemanticEvidenceGate.failure(code));
        }
        result.put("elapsedMs", (System.nanoTime() - start) / 1_000_000.0)
                .put("attempts", capture.attempts).put("httpStatus", capture.status);
        if (capture.response != null) {
            try {
                JsonNode outer = SemanticEvidenceGate.JSON.readTree(capture.response);
                if (outer.has("usage")) result.set("usage", outer.get("usage"));
                if (outer.has("id")) result.set("responseId", outer.get("id"));
            } catch (Exception ignored) { /* invalid body remains available in raw artifact */ }
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        // Secret supplied through stdin only; never process arguments, environment, or artifact.
        JsonNode credentials = SemanticEvidenceGate.JSON.readTree(System.in.readAllBytes());
        String key = credentials.path("apiKey").asText().strip();
        if (key.isBlank() || key.contains("\n") || key.contains("\r")) throw new IllegalArgumentException("INVALID_SECRET_INPUT");
        Path inputPath = Path.of(args[0]).toAbsolutePath().normalize(), systemPath = Path.of(args[1]).toAbsolutePath().normalize(), output = Path.of(args[2]).toAbsolutePath().normalize();
        JsonNode input = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(inputPath));
        Path root = Path.of("").toAbsolutePath().normalize();
        String id = input.path("questionId").asText();
        if (!id.matches("g(?:0[1-9]|1[0-2])") ||
                !inputPath.equals(root.resolve("target/rag-p4/prepared/inputs/" + id + ".json")) ||
                !systemPath.equals(root.resolve("tools/rag-p4/system-prompt.txt")) ||
                !output.equals(root.resolve("target/rag-p4/run-once/" + id)))
            throw new IllegalArgumentException("EXPERIMENT_PATH_GUARD");
        Path freezePath = root.resolve("target/rag-p4/prepared/freeze.json");
        String seal = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(freezePath)));
        JsonNode lock = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(output.getParent().resolve("EXECUTION_RESERVED.lock")));
        JsonNode intent = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(output.resolve("REQUEST_INTENT.lock")));
        String payloadSha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(inputPath)));
        if (!credentials.path("authorization").asText().equals("AUTHORIZE P4 12 DEEPSEEK ONCE") ||
                !seal.equals(credentials.path("freezeSha256").asText()) || !seal.equals(lock.path("freezeSha256").asText()) ||
                !payloadSha.equals(intent.path("payloadSha256").asText()))
            throw new IllegalArgumentException("AUTHORIZATION_SEAL_GUARD");
        // A JVM invocation cannot silently replay an already reserved provider request.
        Files.writeString(output.resolve("HTTP_ATTEMPT_RESERVED.lock"), "ONE_ATTEMPT_NO_RETRY", StandardOpenOption.CREATE_NEW);
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setModel("deepseek-v4-flash"); properties.setMaxTokens(3072);
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http); factory.setReadTimeout(Duration.ofSeconds(60));
        Capture capture = new Capture();
        var builder = RestClient.builder().baseUrl("https://api.deepseek.com").requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key);
        var client = new DeepSeekEvaluationClient(capturing(builder, capture).build(), properties, SemanticEvidenceGate.JSON);
        ObjectNode result = evaluate(client, capture, Files.readString(systemPath, StandardCharsets.UTF_8), input);
        // Redact literal secret from raw provider artifacts in the unlikely event of an echo.
        for (String name : new String[]{"request", "response"}) {
            byte[] bytes = name.equals("request") ? capture.request : capture.response;
            if (bytes != null) Files.writeString(output.resolve(name + ".json"),
                    new String(bytes, StandardCharsets.UTF_8).replace(key, "[REDACTED]"), StandardOpenOption.CREATE_NEW);
        }
        Files.writeString(output.resolve("decision.json"),
                SemanticEvidenceGate.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result).replace(key, "[REDACTED]"),
                StandardOpenOption.CREATE_NEW);
    }
}
