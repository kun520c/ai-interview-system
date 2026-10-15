import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.RetryConfig;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** P3-only retrieval. Reuses P1 read-only guards, never invokes its runner or output paths. */
public final class P3ReadOnlyRetrieval {
    static final ObjectMapper JSON = IndependentRetrieval.JSON;
    static final Path QUERIES = Path.of("tools/rag-p3/queries-v1.json");
    static final Path FREEZE = Path.of("target/rag-p3/prepared/freeze.json");
    static final Path OUTPUT = Path.of("target/rag-p3/run-once/retrieval");
    static final String AUTH = "AUTHORIZE P3 12 EACH ONCE";

    static final class Budget {
        int attempts;
        void take() { require(attempts < 12, "CALL_LIMIT_12"); attempts++; }
    }
    record HttpReply(int status, byte[] body) {}
    record SearchBatch(List<Map<String,Object>> hits, JsonNode rawResponse) {}
    interface EmbeddingTransport { HttpReply post(byte[] request) throws Exception; }
    interface Resources extends AutoCloseable {
        Map<String,Object> before() throws Exception;
        SearchBatch searchAndHydrate(List<Float> vector) throws Exception;
        Map<String,Object> after() throws Exception;
        void close() throws Exception;
    }
    interface ResourceFactory { Resources open() throws Exception; }
    static void require(boolean value, String code) { RagE2ePolicy.require(value, code); }
    static String sha(Path path) throws Exception { return IndependentRetrieval.sha(path); }

    static JsonNode offlineGate() throws Exception {
        var freeze = JSON.readTree(FREEZE.toFile());
        require(sha(FREEZE).equals(Files.readString(FREEZE.resolveSibling("freeze.sha256")).strip()), "P3_FREEZE_SHA");
        for (var iterator = freeze.path("coreSha256").fields(); iterator.hasNext();) {
            var entry = iterator.next();
            require(sha(Path.of("tools/rag-p3", entry.getKey())).equals(entry.getValue().asText()), "P3_CORE_CHANGED");
        }
        require(sha(IndependentRetrieval.CONTRACT).equals(IndependentRetrieval.CONTRACT_SHA), "FROZEN_CORPUS_CONTRACT_CHANGED");
        IndependentRetrieval.checkMapper();
        var queries = JSON.readTree(QUERIES.toFile());
        require(queries.path("queries").size() == 12, "P3_QUERY_COUNT");
        int covered = 0, negative = 0;
        var ids = new java.util.HashSet<String>();
        for (var q : queries.path("queries")) {
            require(ids.add(q.path("queryId").asText()), "DUPLICATE_QUERY");
            String text = q.path("questionText").asText();
            require(!text.isBlank() && text.getBytes(StandardCharsets.UTF_8).length <= 2048, "QUERY_BYTE_GATE");
            require(RagE2ePolicy.hash(text).equals(q.path("questionSha256").asText()) &&
                    text.equals(q.path("embeddingQueryText").asText()) && text.equals(q.path("rerankQueryText").asText()), "QUESTION_QUERY_CHANGED");
            if (q.path("expectedCoverage").asText().equals("COVERED")) covered++;
            else if (q.path("expectedCoverage").asText().equals("NOT_COVERED")) negative++;
        }
        require(covered == 6 && negative == 6, "P3_SPLIT");
        for (var iterator = queries.path("corpusDocumentSha256").fields(); iterator.hasNext();) {
            var entry = iterator.next();
            require(sha(Path.of(entry.getKey())).equals(entry.getValue().asText()), "FROZEN_SOURCE_CHANGED");
        }
        return queries;
    }

    static final class NativeResources implements Resources {
        final IndependentRetrieval helper = new IndependentRetrieval();
        IndependentReadOnlySql sql;
        java.sql.Connection connection;
        MilvusClientV2 milvus;
        Map<String,List<Map<String,String>>> originalCorpus;
        final String password;
        final Map<String,Object> audit = new LinkedHashMap<>();
        NativeResources(String password, String token) throws Exception {
            this.password = password;
            helper.contract = JSON.readTree(IndependentRetrieval.CONTRACT.toFile());
            try {
                connection = DriverManager.getConnection(RagE2ePolicy.jdbc("ai_interview_test"), "rag_e2e_writer", password);
                sql = new IndependentReadOnlySql(connection, helper.corpusSql());
                audit.put("mysqlIdentity", sql.identity);
                originalCorpus = helper.corpus(sql);
                var config = ConnectConfig.builder().uri("http://127.0.0.1:19530").dbName("default")
                        .connectTimeoutMs(5000).rpcDeadlineMs(20000);
                if (!token.isBlank()) config.token(token);
                milvus = new MilvusClientV2(config.build());
                milvus.retryConfig(RetryConfig.builder().maxRetryTimes(1).retryOnRateLimit(false).build());
                var description = milvus.describeCollection(DescribeCollectionReq.builder().collectionName(IndependentRetrieval.COLLECTION).build());
                var index = milvus.describeIndex(DescribeIndexReq.builder().collectionName(IndependentRetrieval.COLLECTION).fieldName("vector").build());
                IndependentRetrieval.validateSchema(description, index);
                require(Boolean.TRUE.equals(milvus.getLoadState(GetLoadStateReq.builder().collectionName(IndependentRetrieval.COLLECTION).build())), "MILVUS_NOT_LOADED");
                String hash = helper.checkVectors(helper.vectors(milvus), originalCorpus.get("knowledge_chunk"));
                audit.put("mysqlFrozenBefore", helper.contract.get("mysqlFrozen"));
                audit.put("milvusVectorHashBefore", hash);
                audit.put("collectionId", Long.toString(IndependentRetrieval.COLLECTION_ID));
                audit.put("sqlAudit", sql.audit);
                audit.put("protection", "Server READ ONLY + fixed SELECTs; rollback only. Milvus describe/query/search only; no initialization/load/write.");
            } catch (Exception error) {
                try { close(); } catch (Exception ignored) {}
                throw error;
            }
        }
        public Map<String,Object> before() { return audit; }
        public SearchBatch searchAndHydrate(List<Float> vector) throws Exception {
            var response = milvus.search(IndependentRetrieval.searchRequest(vector));
            var hits = IndependentRetrieval.rawHits(response);
            require(hits.size() == 5, "P3_NEEDS_FIVE_CANDIDATES");
            JsonNode raw = JSON.valueToTree(response);
            helper.hydrate(sql, hits);
            for (var hit : hits) require(Boolean.TRUE.equals(hit.get("sqlEligible")), "INELIGIBLE_HYDRATED_HIT");
            return new SearchBatch(hits, raw);
        }
        public Map<String,Object> after() throws Exception {
            sql.close();
            connection = null;
            var oldAudit = sql.audit;
            sql = null;
            try (var after = new IndependentReadOnlySql(DriverManager.getConnection(RagE2ePolicy.jdbc("ai_interview_test"),
                    "rag_e2e_writer", password), helper.corpusSql())) {
                helper.corpus(after);
                oldAudit.addAll(after.audit);
            }
            String hash = helper.checkVectors(helper.vectors(milvus), originalCorpus.get("knowledge_chunk"));
            var description = milvus.describeCollection(DescribeCollectionReq.builder().collectionName(IndependentRetrieval.COLLECTION).build());
            require(description.getCollectionID() == IndependentRetrieval.COLLECTION_ID, "COLLECTION_CHANGED_AFTER");
            return Map.of("unchanged191Rows", true, "unchanged52Vectors", true,
                    "milvusVectorHashAfter", hash, "mysqlFrozenAfter", helper.contract.get("mysqlFrozen"));
        }
        public void close() throws Exception {
            try { if (sql != null) sql.close(); else if (connection != null) connection.close(); }
            finally { if (milvus != null) milvus.close(); }
        }
    }

    final Path output;
    final Budget embedding = new Budget(), search = new Budget();
    final Map<String,Object> result = new LinkedHashMap<>();
    final List<Map<String,Object>> records = new ArrayList<>(), events = new ArrayList<>();
    long tokensKnown;
    P3ReadOnlyRetrieval(Path output) { this.output = output; }

    static void durable(Path path, byte[] bytes) throws Exception {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (var file = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) file.write(buffer);
            file.force(true);
        }
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    void save() throws Exception {
        result.put("calls", Map.of("embedding", embedding.attempts, "milvusSearch", search.attempts,
                "deepseek", 0, "jev", 0, "mysqlWrites", 0, "milvusWrites", 0));
        result.put("embeddingTokensKnown", tokensKnown);
        result.put("queries", records);
        result.put("executionLedger", events);
        durable(output.resolve("retrieval.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(result));
    }
    Map<String,Object> intent(String id, String operation, Budget budget) throws Exception {
        budget.take();
        var event = new LinkedHashMap<String,Object>();
        event.put("queryId", id); event.put("operation", operation); event.put("attempt", 1);
        event.put("startedUtc", Instant.now().toString()); event.put("outcome", "INTENT_PERSISTED_UNKNOWN_IF_INTERRUPTED");
        events.add(event); save(); return event;
    }
    static long usage(JsonNode response) {
        var total = response.path("usage").path("total_tokens");
        require(total.isIntegralNumber() && total.canConvertToLong() && total.asLong() > 0, "TRUSTWORTHY_EMBEDDING_USAGE_REQUIRED");
        return total.asLong();
    }
    static byte[] request(String text) throws Exception {
        return JSON.writeValueAsBytes(Map.of("model", RagE2ePolicy.MODEL, "input", List.of(text), "dimensions", 1024, "encoding_format", "float"));
    }
    static void budgetGate(JsonNode dataset, int next, long knownTokens) throws Exception {
        var protocol = JSON.readTree(Path.of("tools/rag-p3/protocol-v1.json").toFile());
        long futureEmbedding = 0, futureRerank = 0;
        int instructionBytes = "Given a web search query, retrieve relevant passages that answer the query."
                .getBytes(StandardCharsets.UTF_8).length;
        for (int i = 0; i < dataset.path("queries").size(); i++) {
            int bytes = dataset.path("queries").get(i).path("questionText").asText().getBytes(StandardCharsets.UTF_8).length;
            if (i >= next) futureEmbedding += bytes + 256L;
            futureRerank += 5L * (bytes + instructionBytes + protocol.path("maxDocumentUtf8Bytes").asInt() + 256L);
        }
        require((knownTokens + futureEmbedding + futureRerank) * .50 / 1_000_000 <=
                Double.parseDouble(protocol.path("budgetReferenceCny").asText()), "COMBINED_BUDGET_REFERENCE_GATE");
    }
    void run(JsonNode dataset, ResourceFactory resources, EmbeddingTransport transport, String origin) throws Exception {
        Files.createDirectory(output);
        Files.writeString(output.resolve("EXECUTION_RESERVED.lock"), Instant.now().toString(), StandardOpenOption.CREATE_NEW);
        result.put("P3_RETRIEVAL_RESULT", "IN_PROGRESS"); result.put("origin", origin);
        result.put("startedUtc", Instant.now().toString()); result.put("retryCount", 0);
        result.put("querySetSha256", sha(QUERIES)); result.put("freezeSha256", sha(FREEZE));
        for (var q : dataset.path("queries")) {
            var record = new LinkedHashMap<String,Object>(); record.put("queryId", q.path("queryId").asText());
            record.put("questionText", q.path("questionText").asText()); record.put("status", "NOT_STARTED"); records.add(record);
        }
        save();
        try (var service = resources.open()) {
            result.put("resourcesBefore", service.before()); save();
            for (int queryIndex = 0; queryIndex < records.size(); queryIndex++) {
                var record = records.get(queryIndex);
                String id = record.get("queryId").toString(), text = record.get("questionText").toString();
                record.put("status", "RUNNING"); long start = System.nanoTime();
                // Keep the total known charge below the reference; no provider tokenizer call.
                budgetGate(dataset, queryIndex, tokensKnown);
                byte[] body = request(text);
                Files.write(output.resolve(id + ".embedding-request.json"), body, StandardOpenOption.CREATE_NEW);
                var event = intent(id, "EMBEDDING_HTTP", embedding);
                event.put("requestSha256", RagE2ePolicy.hash(body)); save();
                long sent = System.nanoTime();
                var reply = transport.post(body);
                record.put("embeddingElapsedMs", (System.nanoTime() - sent) / 1_000_000d);
                Files.write(output.resolve(id + ".embedding-response.json"), reply.body(), StandardOpenOption.CREATE_NEW);
                event.put("httpStatus", reply.status()); event.put("responseSha256", RagE2ePolicy.hash(reply.body())); save();
                require(reply.status() == 200, "EMBEDDING_HTTP_FAILURE_NO_RETRY");
                var response = JSON.readTree(reply.body());
                var vector = IndependentRetrieval.embeddingValues(response);
                long tokens = usage(response); tokensKnown = Math.addExact(tokensKnown, tokens);
                record.put("embedding", Map.of("model", RagE2ePolicy.MODEL, "dimension", 1024,
                        "usage", response.get("usage"), "responseSha256", RagE2ePolicy.hash(reply.body())));
                event.put("outcome", "SUCCESS"); event.put("endedUtc", Instant.now().toString()); save();
                event = intent(id, "MILVUS_SEARCH", search);
                var batch = service.searchAndHydrate(vector);
                require(batch.hits().size() == 5, "P3_NEEDS_FIVE_CANDIDATES");
                Files.write(output.resolve(id + ".milvus-response.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(batch.rawResponse()), StandardOpenOption.CREATE_NEW);
                record.put("rawTopK5", batch.hits()); record.put("status", "COMPLETE");
                record.put("retrievalElapsedMs", (System.nanoTime() - start) / 1_000_000d);
                event.put("outcome", "SUCCESS"); event.put("endedUtc", Instant.now().toString()); save();
            }
            result.putAll(service.after());
            require(Boolean.TRUE.equals(result.get("unchanged191Rows")) && Boolean.TRUE.equals(result.get("unchanged52Vectors")), "P3_AFTER_RESOURCE_GATE");
            require(sha(QUERIES).equals(result.get("querySetSha256")) && sha(FREEZE).equals(result.get("freezeSha256")), "P3_INPUT_CHANGED_DURING_RUN");
            result.put("P3_RETRIEVAL_RESULT", "PASS");
        } catch (Exception error) {
            result.put("P3_RETRIEVAL_RESULT", "FAIL");
            result.put("failureClass", error.getClass().getName());
            result.put("failureCode", error instanceof SecurityException ? error.getMessage() : "EXTERNAL_OR_EXECUTION_FAILURE_NO_RETRY");
            for (var record : records) if ("RUNNING".equals(record.get("status"))) record.put("status", "FAILED_NO_RETRY");
            throw new IllegalStateException("P3_RETRIEVAL_STOPPED_NO_RETRY");
        } finally {
            result.put("endedUtc", Instant.now().toString()); save();
            Files.writeString(output.resolve("retrieval.sha256"), sha(output.resolve("retrieval.json")), StandardOpenOption.CREATE_NEW);
        }
    }
    public static void main(String[] args) throws Exception {
        var dataset = offlineGate();
        if (args.length == 1 && args[0].equals("offline")) {
            System.out.println("P3_JAVA_OFFLINE_GATE=PASS; liveCalls=0"); return;
        }
        require(args.length == 1 && args[0].equals("execute-once"), "EXPLICIT_P3_EXECUTE_REQUIRED");
        var envelope = JSON.readTree(System.in.readNBytes(65536));
        require(AUTH.equals(envelope.path("authorization").asText()) && sha(FREEZE).equals(envelope.path("freezeSha256").asText()), "P3_AUTH_FREEZE_BINDING");
        require("true".equals(System.getProperty("jdk.httpclient.disableRetryConnect")) &&
                "false".equals(System.getProperty("jdk.httpclient.enableAllMethodRetry")), "JDK_HTTP_RETRIES_NOT_DISABLED");
        String key = envelope.path("embeddingKey").asText(), password = envelope.path("dbPassword").asText();
        require(!key.isBlank() && !password.isBlank(), "P3_LOCAL_CREDENTIALS_MISSING");
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        var runner = new P3ReadOnlyRetrieval(OUTPUT);
        runner.run(dataset, () -> new NativeResources(password, envelope.path("milvusToken").asText()), body -> {
            var request = HttpRequest.newBuilder(URI.create(RagE2ePolicy.EMBEDDING_ENDPOINT + "/embeddings"))
                    .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String text = new String(response.body(), StandardCharsets.UTF_8);
            require(!text.contains(key) && !text.contains(password), "PROVIDER_ECHOED_SECRET_NOT_PERSISTED");
            return new HttpReply(response.statusCode(), response.body());
        }, "REAL");
        System.out.println("P3_RETRIEVAL_RESULT=PASS; embedding=12, search=12, writes=0");
    }
}
