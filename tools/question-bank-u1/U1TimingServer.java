import com.kun.aiinterview.AiInterviewSystemApplication;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/** Starts the real application with additional, run-only upstream request guards. */
public final class U1TimingServer {
    private static final Path RUN = Path.of("backups/U1_20261114");
    private static final URI DEEPSEEK = URI.create("https://api.deepseek.com/chat/completions");
    private static final String ANSWER = "/api/interviews/{sessionId}/answers";
    private static final String REPORT = "/api/interviews/{sessionId}/report";
    private static final String PATTERN = org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE;
    private static final String RUN_HEADER = "X-U1-Synthetic-Run";
    private static final Object GUARD = new Object();
    private static int previousDeepSeekAttempts;
    private static int answerAttempts;
    private static int reportAttempts;
    private static int embeddingAttempts;
    private static boolean stopped;

    public static void main(String[] args) throws Exception {
        require("true".equals(System.getenv("U1_WINDOW_CONFIRMED")), "U1_WINDOW");
        require(Files.isRegularFile(RUN.resolve("timing-started.json")), "U1_TIMING_MARKER");
        if (args.length == 1 && "readback".equals(args[0])) {
            readback();
            return;
        }
        previousDeepSeekAttempts = Integer.parseInt(System.getenv("U1_PREVIOUS_LOCAL_DEEPSEEK_ATTEMPTS"));
        require(previousDeepSeekAttempts >= 0 && previousDeepSeekAttempts <= 16, "U1_PRIOR_DEEPSEEK_ATTEMPTS");
        require(D3Inventory.MYSQL.equals(System.getenv("EVALUATION_DB_URL")), "U1_DB_TARGET");
        try (var connection = java.sql.DriverManager.getConnection(D3Inventory.MYSQL,
                System.getenv("EVALUATION_DB_USERNAME"), System.getenv("EVALUATION_DB_PASSWORD"));
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT CONCAT(DATABASE(), ' ', @@server_uuid)")) {
            require(result.next() && ("ai_interview_evaluation " + D3Inventory.MYSQL_UUID).equals(result.getString(1)),
                    "U1_DB_IDENTITY");
        }
        var milvus = D3Inventory.client();
        try {
            var description = milvus.describeCollection(io.milvus.v2.service.collection.request.DescribeCollectionReq.builder()
                    .databaseName("default").collectionName(D3Inventory.COLLECTION).build());
            require(description.getCollectionID() == D3Inventory.COLLECTION_ID, "U1_MILVUS_IDENTITY");
            var count = milvus.query(io.milvus.v2.service.vector.request.QueryReq.builder()
                    .databaseName("default").collectionName(D3Inventory.COLLECTION).filter("")
                    .consistencyLevel(io.milvus.v2.common.ConsistencyLevel.STRONG)
                    .outputFields(List.of("count(*)")).build());
            require(D3Inventory.number(count.getQueryResults().getFirst().getEntity(), "count(*)").intValue() == 52,
                    "U1_MILVUS_COUNT");
        } finally {
            milvus.close();
        }
        SpringApplication.run(new Class<?>[]{AiInterviewSystemApplication.class, U1TimingServer.class}, args);
    }

    private static void readback() throws Exception {
        var result = D3Corpus.JSON.readTree(RUN.resolve("timing-browser.json").toFile());
        require("COMPLETED".equals(result.required("status").asText()), "U1_READBACK_STATUS");
        long sessionId = result.required("sessionId").asLong();
        long userId = result.required("userId").asLong();
        require(sessionId > 0 && userId > 0, "U1_READBACK_IDENTITIES");
        try (var connection = java.sql.DriverManager.getConnection(D3Inventory.MYSQL,
                System.getenv("EVALUATION_DB_USERNAME"), System.getenv("EVALUATION_DB_PASSWORD"))) {
            connection.setReadOnly(true);
            try (var statement = connection.prepareStatement("""
                    SELECT s.status, s.report_status, s.planned_question_count,
                      (SELECT COUNT(*) FROM interview_report r WHERE r.session_id=s.id) AS reports,
                      (SELECT COUNT(*) FROM interview_question q JOIN interview_answer a ON a.interview_question_id=q.id
                         JOIN answer_evaluation e ON e.answer_id=a.id WHERE q.session_id=s.id AND e.evaluation_phase='FINAL') AS finals,
                      (SELECT COUNT(*) FROM interview_question q JOIN interview_answer a ON a.interview_question_id=q.id
                         JOIN rag_retrieval_batch b ON b.answer_id=a.id WHERE q.session_id=s.id) AS batches
                    FROM interview_session s JOIN user u ON u.id=s.user_id
                    WHERE s.id=? AND s.user_id=? AND u.account REGEXP '^u1timing_[a-f0-9]{10}$' AND s.difficulty='EASY'
                    """)) {
                statement.setLong(1, sessionId);
                statement.setLong(2, userId);
                try (var row = statement.executeQuery()) {
                    require(row.next() && "COMPLETED".equals(row.getString("status"))
                            && "READY".equals(row.getString("report_status")) && row.getInt("planned_question_count") == 5
                            && row.getInt("reports") == 1 && row.getInt("finals") == 5
                            && row.getInt("batches") == result.required("answers").size(), "U1_READBACK_RESULT");
                    D3Corpus.writeNew(RUN.resolve("timing-post-readback.json"), Map.of("status", "COMPLETED",
                            "reportStatus", "READY", "reports", row.getInt("reports"), "finalEvaluations", row.getInt("finals"),
                            "retrievalBatches", row.getInt("batches")));
                }
            }
        }
    }

    @Bean
    @Primary
    EmbeddingClient timingEmbedding(EmbeddingProperties properties) throws Exception {
        var embedding = new D3PaidEmbedding(properties, RUN.resolve("embedding-usage.jsonl"));
        return texts -> {
            synchronized (GUARD) {
                require(!stopped && permitted(ANSWER) && texts.size() == 1 && embeddingAttempts < 7, "U1_EMBEDDING_GUARD");
                embedding.authorize("timing-answer-" + (++embeddingAttempts), texts);
                try {
                    embedding.embed(texts);
                    return embedding.takeResult();
                } catch (RuntimeException failure) {
                    stopped = true;
                    throw failure;
                }
            }
        };
    }

    @Bean
    RestClientCustomizer timingDeepSeek(DeepSeekProperties properties) {
        require("deepseek-flash".equals(properties.getModel()) && properties.getMaxTokens() == 4096, "U1_DEEPSEEK_CONFIGURATION");
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            synchronized (GUARD) {
                boolean answer = permitted(ANSWER);
                boolean report = permitted(REPORT);
                require(!stopped && (answer && answerAttempts < 7 || report && reportAttempts < 1)
                        && DEEPSEEK.equals(request.getURI()) && "POST".equals(request.getMethod().name()), "U1_DEEPSEEK_GUARD");
                var payload = D3Corpus.JSON.readTree(body);
                require("deepseek-flash".equals(payload.required("model").asText())
                        && payload.required("max_tokens").asInt() == 4096 && body.length <= 48000, "U1_DEEPSEEK_PAYLOAD");
                String operation = answer ? "timing-answer" : "timing-report";
                if (answer) {
                    answerAttempts++;
                } else {
                    reportAttempts++;
                }
                int attempt = previousDeepSeekAttempts + answerAttempts + reportAttempts;
                append(Map.of("event", "attempt", "attempt", attempt, "operation", operation,
                        "bodyUtf8Bytes", body.length, "maxOutputTokens", 4096));
                try {
                    var response = execution.execute(request, body);
                    int status = response.getStatusCode().value();
                    append(Map.of("event", "headers", "attempt", attempt, "operation", operation, "status", status));
                    stopped = !response.getStatusCode().is2xxSuccessful();
                    return new D3SmokeServer.UsageResponse(response, attempt, event -> {
                        try {
                            var entry = new java.util.LinkedHashMap<>(event);
                            entry.put("operation", operation);
                            append(entry);
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                    });
                } catch (IOException | RuntimeException failure) {
                    stopped = true;
                    throw failure;
                }
            }
        });
    }

    private static boolean permitted(String path) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        String key = System.getenv("U1_TIMING_BROWSER_KEY");
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean synthetic = authentication != null
                && authentication.getPrincipal() instanceof com.kun.aiinterview.security.model.AuthenticatedUser user
                && user.account().matches("u1timing_[a-f0-9]{10}");
        return key != null && !key.isBlank() && key.equals(request.getHeader(RUN_HEADER))
                && synthetic && "POST".equals(request.getMethod()) && path.equals(request.getAttribute(PATTERN));
    }

    private static void append(Map<String, Object> event) throws IOException {
        Files.writeString(RUN.resolve("deepseek-usage.jsonl"), D3Corpus.JSON.writeValueAsString(event) + "\n", CREATE, APPEND);
    }

    private static void require(boolean condition, String code) { D3Corpus.require(condition, code); }
}
