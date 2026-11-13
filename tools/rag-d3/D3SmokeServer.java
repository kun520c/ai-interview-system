import com.kun.aiinterview.AiInterviewSystemApplication;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/** Bounded one-run verification server. Production service classes remain unchanged. */
public final class D3SmokeServer {
    private static final Path RUN = Path.of("backups/D3_BCD_20261114");
    private static final URI DEEPSEEK = URI.create("https://api.deepseek.com/chat/completions");
    private static final AtomicInteger DEEPSEEK_ATTEMPTS = new AtomicInteger();
    private static final AtomicInteger EMBEDDING_ATTEMPTS = new AtomicInteger();
    private static final AtomicInteger FAILED = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        boolean resume = Boolean.getBoolean("d3.smoke.resume");
        Path marker = RUN.resolve(resume ? "smoke-recovery-started.json" : "smoke-started.json");
        D3Corpus.require(Files.exists(RUN.resolve("ingestion-checkpoint-51.json")) && !Files.exists(marker), "SMOKE_PRECONDITION");
        if (resume) {
            D3Corpus.require(Files.exists(RUN.resolve("smoke-first-readback.tsv"))
                    && Files.exists(RUN.resolve("smoke-result.json")), "SMOKE_RECOVERY_EVIDENCE");
            DEEPSEEK_ATTEMPTS.set(attempts(RUN.resolve("deepseek-usage.jsonl"), ""));
            EMBEDDING_ATTEMPTS.set(attempts(RUN.resolve("embedding-usage.jsonl"), "smoke-"));
            D3Corpus.require(DEEPSEEK_ATTEMPTS.get() == 1 && EMBEDDING_ATTEMPTS.get() == 1, "SMOKE_RECOVERY_BUDGET");
        }
        var client = D3Inventory.client();
        try {
            D3Corpus.require(D3Inventory.inventory(client).vectors().size() == 52, "SMOKE_CORPUS");
        } finally {
            client.close();
        }
        D3Corpus.writeNew(marker, Map.of("mode", resume ? "single-easy-recovery" : "single-easy", "maxEmbedding", 10,
                "maxDeepSeek", 11));
        SpringApplication.run(new Class<?>[]{AiInterviewSystemApplication.class, D3SmokeServer.class}, args);
    }

    private static int attempts(Path journal, String prefix) throws IOException {
        int count = 0;
        for (String line : Files.readAllLines(journal)) {
            var event = D3Corpus.JSON.readTree(line);
            if (event.path("event").asText().equals("attempt") && event.path("operation").asText().startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    @Bean
    @Primary
    EmbeddingClient smokeEmbedding(EmbeddingProperties properties) throws Exception {
        var delegate = new D3PaidEmbedding(properties, RUN.resolve("embedding-usage.jsonl"));
        return texts -> {
            synchronized (delegate) {
                D3Corpus.require(FAILED.get() == 0 && texts.size() == 1 && EMBEDDING_ATTEMPTS.get() < 10, "SMOKE_EMBED_LIMIT");
                int attempt = EMBEDDING_ATTEMPTS.incrementAndGet();
                delegate.authorize("smoke-" + attempt, texts);
                try {
                    delegate.embed(texts);
                    return delegate.takeResult();
                } catch (RuntimeException error) {
                    FAILED.incrementAndGet();
                    throw error;
                }
            }
        };
    }

    @Bean
    org.springframework.boot.web.client.RestClientCustomizer smokeDeepSeekJournal(DeepSeekProperties properties) {
        D3Corpus.require("deepseek-flash".equals(properties.getModel()) && properties.getMaxTokens() <= 4096,
                "SMOKE_DEEPSEEK_CONFIG");
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            D3Corpus.require(FAILED.get() == 0 && DEEPSEEK.equals(request.getURI())
                    && "POST".equals(request.getMethod().name()), "SMOKE_DEEPSEEK_TARGET");
            var payload = D3Corpus.JSON.readTree(body);
            D3Corpus.require("deepseek-flash".equals(payload.required("model").asText())
                    && payload.required("max_tokens").asInt() <= 4096 && body.length <= 48000,
                    "SMOKE_DEEPSEEK_PAYLOAD");
            int attempt = DEEPSEEK_ATTEMPTS.incrementAndGet();
            D3Corpus.require(attempt <= 11, "SMOKE_DEEPSEEK_LIMIT");
            append(Map.of("event", "attempt", "attempt", attempt, "bodyUtf8Bytes", body.length,
                    "maxOutputTokens", payload.required("max_tokens").asInt()));
            try {
                var response = execution.execute(request, body);
                int status = response.getStatusCode().value();
                append(Map.of("event", "headers", "attempt", attempt, "status", status));
                if (!response.getStatusCode().is2xxSuccessful()) {
                    FAILED.incrementAndGet();
                }
                return new UsageResponse(response, attempt);
            } catch (IOException | RuntimeException error) {
                FAILED.incrementAndGet();
                throw error;
            }
        });
    }

    private static void append(Map<String, Object> data) throws IOException {
        Files.writeString(RUN.resolve("deepseek-usage.jsonl"), D3Corpus.JSON.writeValueAsString(data) + "\n", CREATE, APPEND);
    }

    static final class UsageResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final int attempt;
        private final java.util.function.Consumer<Map<String, Object>> usageObserver;
        private byte[] body;

        UsageResponse(ClientHttpResponse delegate, int attempt) {
            this(delegate, attempt, data -> {
                try {
                    append(data);
                } catch (IOException error) {
                    throw new java.io.UncheckedIOException(error);
                }
            });
        }

        UsageResponse(ClientHttpResponse delegate, int attempt,
                      java.util.function.Consumer<Map<String, Object>> usageObserver) {
            this.delegate = delegate;
            this.attempt = attempt;
            this.usageObserver = usageObserver;
        }

        @Override
        public org.springframework.http.HttpStatusCode getStatusCode() throws IOException { return delegate.getStatusCode(); }
        @Override
        public String getStatusText() throws IOException { return delegate.getStatusText(); }
        @Override
        public org.springframework.http.HttpHeaders getHeaders() { return delegate.getHeaders(); }
        @Override
        public void close() { delegate.close(); }

        @Override
        public java.io.InputStream getBody() throws IOException {
            if (body == null) {
                byte[] received = delegate.getBody().readNBytes(512 * 1024 + 1);
                D3Corpus.require(received.length <= 512 * 1024, "SMOKE_DEEPSEEK_RESPONSE_SIZE");
                if (getStatusCode().is2xxSuccessful()) {
                    var usage = D3Corpus.JSON.readTree(received).path("usage");
                    var data = new java.util.LinkedHashMap<String, Object>();
                    data.put("event", "usage");
                    data.put("attempt", attempt);
                    for (String field : List.of("prompt_tokens", "completion_tokens", "total_tokens",
                            "prompt_cache_hit_tokens", "prompt_cache_miss_tokens")) {
                        data.put(field, usage.path(field).isIntegralNumber() ? usage.path(field).asLong() : null);
                    }
                    usageObserver.accept(data);
                }
                body = received;
            }
            return new java.io.ByteArrayInputStream(body);
        }
    }
}
