import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.Set;

/** Files-only response validation. No HTTP client, credentials, application startup, or run-lock mutation. */
public final class SavedResponseReverification {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path destination = Path.of(args[1]).toAbsolutePath().normalize();
        if (!destination.startsWith(root.resolve("target/rag-p4-repair")))
            throw new IllegalArgumentException("REVERIFICATION_OUTPUT_PATH");
        ObjectNode results = SemanticEvidenceGate.JSON.createObjectNode();
        results.put("origin", "OFFLINE_SAVED_REAL_RESPONSES").put("newHttpRequests", 0);
        var queries = results.putArray("queries");
        for (int n = 1; n <= 5; n++) {
            String id = "g%02d".formatted(n);
            JsonNode input = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(root.resolve("target/rag-p4/prepared/inputs/" + id + ".json")));
            JsonNode response = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(root.resolve("target/rag-p4/run-once/" + id + "/response.json")));
            ObjectNode row = queries.addObject().put("questionId", id);
            long started = System.nanoTime();
            try {
                JsonNode choice = response.path("choices").path(0);
                if (response.path("choices").size() != 1 || choice.path("index").asInt(-1) != 0 ||
                        !choice.path("finish_reason").asText().equals("stop") ||
                        !choice.path("message").path("role").asText().equals("assistant") ||
                        !Set.of("deepseek-flash", "deepseek-v4-flash", "deepseek-v4.1-flash").contains(response.path("model").asText()))
                    throw new IllegalArgumentException("OUTER_RESPONSE_INVALID");
                row.set("gate", SemanticEvidenceGate.validate(input, choice.path("message").path("content").asText()));
            } catch (Exception exception) {
                row.set("gate", SemanticEvidenceGate.failure(exception instanceof IllegalArgumentException ?
                        exception.getMessage() : exception.getClass().getSimpleName()));
            }
            row.put("localValidationMs", (System.nanoTime() - started) / 1_000_000.0);
        }
        Files.writeString(destination, results.toPrettyString(), StandardOpenOption.CREATE_NEW);
        System.out.println("Saved-response verification only: 5 records; new HTTP=0.");
    }
}
