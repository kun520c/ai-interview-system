import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Records actual provider traffic and consumes finite budgets before forwarding, without retries. */
final class FinalE2eGate implements AutoCloseable {
    static final int BODY_CAP = 65536;
    final Map<String,Integer> counts = new LinkedHashMap<>();
    final List<Map<String,Object>> records = new ArrayList<>();
    final Path output;
    final Transport transport;
    final Map<String,Integer> limits;
    final int deepSeekPerAnswer;
    String operation = "UNASSIGNED";
    final Map<String,Integer> operationCounts = new HashMap<>();
    HttpServer server;
    interface Transport { RagE2eModelGate.Reply post(URI uri, String authorization, byte[] body) throws Exception; }
    FinalE2eGate(Path output, Transport transport) {
        this(output,transport,Map.of("embedding",10,"jev",10,"deepseek",21),2);
    }
    FinalE2eGate(Path output, Transport transport,Map<String,Integer> limits,int deepSeekPerAnswer) {
        this.output = output; this.transport = transport;
        this.limits=Map.copyOf(limits); this.deepSeekPerAnswer=deepSeekPerAnswer;
        for (String provider : List.of("embedding", "jev", "deepseek")) counts.put(provider, 0);
    }
    synchronized void begin(String value) { operation = value; operationCounts.clear(); }
    synchronized int total() { return counts.values().stream().mapToInt(Integer::intValue).sum(); }
    synchronized Map<String,Integer> countSnapshot() { return Map.copyOf(counts); }
    static String provider(String path) {
        return switch (path) {
            case "/embedding/embeddings" -> "embedding";
            case "/jev/v1/systemone" -> "jev";
            case "/deepseek/chat/completions" -> "deepseek";
            default -> throw new SecurityException("MODEL_PATH_REJECTED");
        };
    }
    synchronized void admit(String provider, String authorization, byte[] body) throws Exception {
        FinalE2eRunner.check(body.length <= BODY_CAP, "MODEL_REQUEST_BYTE_CAP");
        int maximum = limits.get(provider);
        int perOperation = provider.equals("deepseek") && operation.startsWith("answer-") ? deepSeekPerAnswer : 1;
        FinalE2eRunner.check(operation.startsWith("answer-") || operation.equals("report"), "NO_ACTIVE_BUSINESS_OPERATION");
        FinalE2eRunner.check(!operation.equals("report") || provider.equals("deepseek"), "REPORT_PROVIDER_REJECTED");
        FinalE2eRunner.check(counts.get(provider) < maximum
                && operationCounts.getOrDefault(provider, 0) < perOperation, "MODEL_REQUEST_BUDGET_EXHAUSTED");
        String key = FinalE2eRunner.env(switch(provider) {
            case "embedding" -> "EMBEDDING_API_KEY"; case "jev" -> "JEV_API_KEY"; default -> "DEEPSEEK_API_KEY";
        });
        FinalE2eRunner.check(!key.isBlank() && ("Bearer " + key).equals(authorization), "MODEL_AUTH_MISMATCH");
        JsonNode request = FinalE2eRunner.JSON.readTree(body);
        if (provider.equals("embedding")) {
            RagE2ePolicy.modelRequest(request, true);
            FinalE2eRunner.check(request.path("input").get(0).asText().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 8320,
                    "QUERY_BYTE_CAP");
        } else if (provider.equals("deepseek")) {
            RagE2ePolicy.modelRequest(request, false);
        } else {
            FinalE2eRunner.check("jev-latest".equals(request.path("model").asText())
                    && request.path("state").isTextual() && request.path("questions").isObject(), "JEV_REQUEST_CONTRACT");
        }
        counts.merge(provider, 1, Integer::sum);
        operationCounts.merge(provider, 1, Integer::sum);
    }
    RagE2eModelGate.Reply forward(String path, String authorization, byte[] body) throws Exception {
        String provider = provider(path);
        admit(provider, authorization, body);
        int index = total();
        String prefix = String.format("%02d-%s-%s", index, operation, provider);
        Files.createDirectories(output);
        Files.write(output.resolve(prefix + ".request.json"), body, StandardOpenOption.CREATE_NEW);
        Map<String,Object> item = new LinkedHashMap<>();
        item.put("provider", provider); item.put("operation", operation); item.put("sentAt", Instant.now().toString());
        item.put("requestFile", prefix + ".request.json"); item.put("requestSha256", RagE2ePolicy.hash(body));
        item.put("requestBytes", body.length); records.add(item);
        FinalE2eRunner.save(output.resolve("provider-index.json"), records);
        String endpoint = switch(provider) {
            case "embedding" -> RagE2ePolicy.EMBEDDING_ENDPOINT + "/embeddings";
            case "jev" -> "https://api.typesafe.ai/v1/systemone";
            default -> "https://api.deepseek.com/chat/completions";
        };
        item.put("endpoint", endpoint);
        try {
            try (var connection=FinalE2eRunner.connection()) {
                FinalE2eRunner.snapshot(connection,prefix+".before-provider");
            }
            var reply = transport.post(URI.create(endpoint), authorization, body);
            // Only the body is saved. Authorization and candidate login responses are never saved.
            Files.write(output.resolve(prefix + ".response.json"), reply.body(), StandardOpenOption.CREATE_NEW);
            item.put("httpStatus", reply.status()); item.put("responseFile", prefix + ".response.json");
            item.put("responseSha256", RagE2ePolicy.hash(reply.body()));
            item.put("receivedAt", Instant.now().toString());
            try {
                JsonNode response = FinalE2eRunner.JSON.readTree(reply.body());
                item.put("responseModel", response.path("model").asText());
                item.put("usage", response.path("usage"));
            } catch (Exception ignored) { item.put("parseableJson", false); }
            return reply;
        } catch (Exception error) {
            item.put("outcome", "UNKNOWN_OR_FAILED_NO_RETRY"); item.put("exceptionClass", error.getClass().getName());
            throw error;
        } finally { FinalE2eRunner.save(output.resolve("provider-index.json"), records); }
    }
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                FinalE2eRunner.check(exchange.getRequestMethod().equals("POST"), "MODEL_POST_ONLY");
                var reply = forward(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestBody().readNBytes(BODY_CAP + 1));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(reply.status(), reply.body().length);
                exchange.getResponseBody().write(reply.body());
            } catch (Exception error) {
                FinalE2eRunner.failures.add(Map.of("gateException", error.getClass().getName(), "operation", operation));
                byte[] body = "{\"error\":\"FINAL_E2E_GATE_REJECTED_NO_RETRY\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(502, body.length); exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        server.start();
    }
    String base(String provider) { return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + provider; }
    @Override public void close() { if (server != null) server.stop(0); }
}
