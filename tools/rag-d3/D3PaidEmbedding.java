import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingConfiguration;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import com.kun.aiinterview.knowledge.embedding.springai.SpringAiEmbeddingClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

final class D3PaidEmbedding implements EmbeddingClient {
    private static final long CNY20_IN_EMBEDDING_TOKENS = 40_000_000L;
    private static final int INPUT_LIMIT = 128_000;
    private final Path journal;
    private final EmbeddingClient delegate;
    private String operation;
    private List<String> authorizedInputs;
    private int attempts;
    private long tokenCeiling;
    private EmbeddingBatchResult result;
    private boolean pending;

    D3PaidEmbedding(EmbeddingProperties properties, Path journal) throws Exception {
        this.journal = journal;
        if (Files.exists(journal)) {
            try (var lines = Files.lines(journal)) {
                for (String line : (Iterable<String>) lines::iterator) {
                    var row = D3Corpus.JSON.readTree(line);
                    if (row.path("event").asText().equals("attempt")) {
                        D3Corpus.require(!pending, "UNRESOLVED_HTTP_ATTEMPT");
                        attempts++;
                        tokenCeiling += row.required("reservedTokens").asLong();
                        pending = true;
                    } else if (row.path("event").asText().equals("success")) {
                        D3Corpus.require(pending, "UNEXPECTED_HTTP_SUCCESS");
                        tokenCeiling -= row.required("reservedTokens").asLong() - row.required("usageTokens").asLong();
                        pending = false;
                    }
                }
            }
        }
        D3Corpus.require(!pending, "UNRESOLVED_HTTP_ATTEMPT");
        URI base = properties.getBaseUrl();
        D3Corpus.require("https".equals(base.getScheme()) && base.getHost() != null
                && base.getHost().matches("[a-z0-9-]+\\.cn-beijing\\.maas\\.aliyuncs\\.com")
                && "/compatible-mode/v1".equals(base.getPath()) && base.getPort() == -1
                && base.getUserInfo() == null && base.getQuery() == null && base.getFragment() == null
                && D3Inventory.MODEL.equals(properties.getModel()) && properties.getDimension() == 1024
                && D3Inventory.PROFILE.equals(properties.getProfileVersion()) && properties.getBatchSize() == 20,
                "EMBEDDING_TARGET_OR_MODEL");
        var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER).proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(properties.getConnectTimeout()).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.getReadTimeout());
        URI endpoint = URI.create(base + "/embeddings");
        var builder = EmbeddingConfiguration.createEmbeddingRestClientBuilder(properties)
                .requestFactory(factory).requestInterceptor((request, body, execution) -> {
                    try {
                        D3Corpus.require(!pending && attempts < 300 && authorizedInputs != null
                                && request.getMethod() == HttpMethod.POST && endpoint.equals(request.getURI()),
                                "EMBEDDING_HTTP_GUARD");
                        var payload = D3Corpus.JSON.readTree(body);
                        List<String> inputs = D3Corpus.JSON.readerForListOf(String.class).readValue(payload.required("input"));
                        D3Corpus.require(authorizedInputs.equals(inputs) && D3Inventory.MODEL.equals(payload.required("model").asText())
                                && payload.required("dimensions").asInt() == 1024
                                && "float".equals(payload.required("encoding_format").asText()), "EMBEDDING_PAYLOAD");
                        long reserved = (long) INPUT_LIMIT * inputs.size();
                        D3Corpus.require(tokenCeiling + reserved <= CNY20_IN_EMBEDDING_TOKENS, "EMBEDDING_BUDGET");
                        append(Map.of("event", "attempt", "attempt", attempts + 1, "operation", operation,
                                "startedAt", Instant.now().toString(), "reservedTokens", reserved, "inputs", inputs.size(),
                                "inputUtf8Bytes", inputs.stream().mapToInt(s -> s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum()));
                        tokenCeiling += reserved;
                        attempts++;
                        pending = true;
                        long start = System.nanoTime();
                        var response = execution.execute(request, body);
                        append(Map.of("event", "headers", "attempt", attempts, "status", response.getStatusCode().value(),
                                "elapsedMs", (System.nanoTime() - start) / 1_000_000));
                        return response;
                    } catch (RuntimeException error) {
                        throw new java.io.IOException("D3_EMBEDDING_GUARD", error);
                    }
                });
        var api = EmbeddingConfiguration.createOpenAiApi(properties, builder);
        delegate = new SpringAiEmbeddingClient(EmbeddingConfiguration.createEmbeddingModel(api, properties), properties);
    }

    void authorize(String operation, List<String> inputs) {
        D3Corpus.require(authorizedInputs == null && !pending && !inputs.isEmpty() && inputs.size() <= 20,
                "EMBEDDING_OPERATION");
        this.operation = operation;
        authorizedInputs = List.copyOf(inputs);
        result = null;
    }

    EmbeddingBatchResult takeResult() {
        D3Corpus.require(result != null && !pending && authorizedInputs == null, "MISSING_EMBEDDING_RESULT");
        var value = result;
        result = null;
        return value;
    }

    @Override
    public EmbeddingBatchResult embed(List<String> texts) {
        D3Corpus.require(texts.equals(authorizedInputs) && !pending, "EMBEDDING_NOT_AUTHORIZED");
        try {
            var embedded = delegate.embed(texts);
            long reserved = (long) INPUT_LIMIT * texts.size();
            Long usage = embedded.totalTokenCount();
            D3Corpus.require(pending && usage != null && usage > 0 && usage <= reserved, "EMBEDDING_USAGE");
            append(Map.of("event", "success", "attempt", attempts, "usageTokens", usage, "reservedTokens", reserved));
            tokenCeiling -= reserved - usage;
            pending = false;
            result = embedded;
            return embedded;
        } catch (java.io.IOException error) {
            throw new IllegalStateException("EMBEDDING_JOURNAL", error);
        } finally {
            authorizedInputs = null;
        }
    }

    private void append(Map<String, Object> event) throws java.io.IOException {
        Files.writeString(journal, D3Corpus.JSON.writeValueAsString(event) + "\n", CREATE, APPEND);
    }
}
