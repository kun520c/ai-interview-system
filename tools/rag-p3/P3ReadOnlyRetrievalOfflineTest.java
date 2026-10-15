import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Mock resources/transports only. Does not invoke P1's main or open any service connection. */
public final class P3ReadOnlyRetrievalOfflineTest {
    interface Checked { void run() throws Exception; }
    static int tests, failures, errors;
    static void test(String name, Checked action) {
        tests++;
        try { action.run(); }
        catch (AssertionError failure) { failures++; System.err.println("FAIL " + name); }
        catch (Exception error) { errors++; System.err.println("ERROR " + name + " " + error.getClass().getSimpleName()); }
    }
    static void ok(boolean value) { if (!value) throw new AssertionError(); }
    static void rejects(Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected rejection"); }
        catch (SecurityException expected) {}
    }
    static void stopped(Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected stopped workflow"); }
        catch (IllegalStateException expected) {}
    }
    static byte[] embedding() throws Exception {
        return P3ReadOnlyRetrieval.JSON.writeValueAsBytes(Map.of("model", RagE2ePolicy.MODEL,
                "data", List.of(Map.of("index", 0, "embedding", java.util.Collections.nCopies(1024, .1f))),
                "usage", Map.of("prompt_tokens", 100, "total_tokens", 100)));
    }
    static final class FakeResources implements P3ReadOnlyRetrieval.Resources {
        int searches;
        boolean closed, unchanged = true;
        final Path output;
        FakeResources(Path output) { this.output = output; }
        public Map<String,Object> before() { return Map.of("mock", true); }
        @SuppressWarnings("unchecked")
        public P3ReadOnlyRetrieval.SearchBatch searchAndHydrate(List<Float> vector) throws Exception {
            var report = P3ReadOnlyRetrieval.JSON.readTree(output.resolve("retrieval.json").toFile());
            ok(report.path("calls").path("milvusSearch").asInt() == searches + 1);
            var events = report.path("executionLedger");
            ok(events.get(events.size() - 1).path("outcome").asText().contains("INTENT_PERSISTED"));
            searches++;
            var original = P3ReadOnlyRetrieval.JSON.readTree(Path.of("target/rag-p2/inputs/results.json").toFile())
                    .path("queries").get(0).path("rawTopK5");
            var hits = (List<Map<String,Object>>)(List<?>)P3ReadOnlyRetrieval.JSON.convertValue(original, List.class);
            return new P3ReadOnlyRetrieval.SearchBatch(hits, original);
        }
        public Map<String,Object> after() { return Map.of("unchanged191Rows", unchanged, "unchanged52Vectors", unchanged); }
        public void close() { closed = true; }
    }
    public static void main(String[] args) throws Exception {
        Path folder = Path.of(args[0]); Files.createDirectories(folder);
        Path fixtures = Files.createTempDirectory(folder, "java-mock-");
        var dataset = P3ReadOnlyRetrieval.offlineGate();
        test("frozen queries 6/6", () -> ok(dataset.path("queries").size() == 12));
        test("budget reserves12 rejects13", () -> {
            var budget = new P3ReadOnlyRetrieval.Budget();
            for (int i = 0; i < 12; i++) budget.take();
            rejects(budget::take); ok(budget.attempts == 12);
        });
        test("request only original question", () -> {
            String question = dataset.path("queries").get(0).path("questionText").asText();
            var request = P3ReadOnlyRetrieval.JSON.readTree(P3ReadOnlyRetrieval.request(question));
            ok(request.path("input").size() == 1 && request.path("input").get(0).asText().equals(question));
            ok(request.path("dimensions").asInt() == 1024 && request.path("encoding_format").asText().equals("float"));
            ok(!request.has("expectedCoverage") && !request.has("targetMechanism"));
        });
        for (String bad : List.of("db", "account", "uuid", "read_only")) {
            test("reject mysql identity " + bad, () -> {
                var identity = IndependentRetrievalOfflineTest.identity(); identity.put(bad, "bad");
                rejects(() -> IndependentReadOnlySql.validateIdentity(identity));
            });
        }
        test("server readonly fixed SELECT rollback", () -> {
            var helper = new IndependentRetrieval(); helper.contract = P3ReadOnlyRetrieval.JSON.readTree(IndependentRetrieval.CONTRACT.toFile());
            var db = new IndependentRetrievalOfflineTest.MockDb();
            try (var sql = new IndependentReadOnlySql(db.connection(), helper.corpusSql())) {
                ok(db.calls.contains("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY"));
                sql.query(IndependentReadOnlySql.IDENTITY);
                rejects(() -> sql.query("DELETE FROM knowledge_chunk"));
                rejects(() -> sql.query("UPDATE knowledge_chunk SET status='ACTIVE'"));
                rejects(() -> sql.query("SELECT * FROM knowledge_chunk FOR UPDATE"));
            }
            ok(db.calls.contains("rollback") && db.calls.contains("close") && !db.calls.contains("commit"));
        });
        for (JsonNode usage : List.of(P3ReadOnlyRetrieval.JSON.readTree("{}"),
                P3ReadOnlyRetrieval.JSON.readTree("{\"usage\":{\"total_tokens\":null}}"),
                P3ReadOnlyRetrieval.JSON.readTree("{\"usage\":{\"total_tokens\":-1}}"),
                P3ReadOnlyRetrieval.JSON.readTree("{\"usage\":{\"total_tokens\":1.5}}"))) {
            test("untrustworthy usage rejects", () -> rejects(() -> P3ReadOnlyRetrieval.usage(usage)));
        }
        test("valid usage", () -> ok(P3ReadOnlyRetrieval.usage(P3ReadOnlyRetrieval.JSON.readTree(embedding())) == 100));
        test("combined known usage plus remaining requests budget", () -> {
            P3ReadOnlyRetrieval.budgetGate(dataset, 0, 0);
            rejects(() -> P3ReadOnlyRetrieval.budgetGate(dataset, 1, 800000));
        });
        test("Milvus SDK unavailable executes once", () -> {
            var utils = new io.milvus.v2.utils.RpcUtils();
            utils.retryConfig(io.milvus.v2.client.RetryConfig.builder().maxRetryTimes(1).retryOnRateLimit(false).build());
            var calls = new AtomicInteger();
            try { utils.retry(() -> { calls.incrementAndGet(); throw io.milvus.shaded.io.grpc.Status.UNAVAILABLE.asRuntimeException(); }); }
            catch (Exception expected) {}
            ok(calls.get() == 1);
        });
        test("12 complete calls and intent then search", () -> {
            Path once = fixtures.resolve("success"); var resource = new FakeResources(once);
            var runner = new P3ReadOnlyRetrieval(once); var calls = new AtomicInteger();
            runner.run(dataset, () -> resource, body -> {
                var saved = P3ReadOnlyRetrieval.JSON.readTree(once.resolve("retrieval.json").toFile());
                ok(saved.path("calls").path("embedding").asInt() == calls.incrementAndGet());
                return new P3ReadOnlyRetrieval.HttpReply(200, embedding());
            }, "OFFLINE_MOCK");
            ok(calls.get() == 12 && resource.searches == 12 && resource.closed && runner.tokensKnown == 1200);
            ok(runner.result.get("origin").equals("OFFLINE_MOCK"));
            ok(runner.result.get("P3_RETRIEVAL_RESULT").equals("PASS"));
            ok(Files.exists(once.resolve("EXECUTION_RESERVED.lock")));
            try { runner.run(dataset, () -> resource, body -> new P3ReadOnlyRetrieval.HttpReply(200, embedding()), "OFFLINE_MOCK");
                throw new AssertionError(); } catch (java.nio.file.FileAlreadyExistsException expected) {}
        });
        test("resource gate fail before paid request", () -> {
            var calls = new AtomicInteger(); var runner = new P3ReadOnlyRetrieval(fixtures.resolve("resource-fail"));
            stopped(() -> runner.run(dataset, () -> { throw new SecurityException("RESOURCE_GATE"); },
                    body -> { calls.incrementAndGet(); return new P3ReadOnlyRetrieval.HttpReply(200, embedding()); }, "OFFLINE_MOCK"));
            ok(calls.get() == 0 && runner.embedding.attempts == 0 && runner.search.attempts == 0);
        });
        for (int status : List.of(401, 403, 429, 302, 500)) {
            test("HTTP failure no retry " + status, () -> {
                Path once = fixtures.resolve("http-" + status); var resource = new FakeResources(once);
                var calls = new AtomicInteger(); var runner = new P3ReadOnlyRetrieval(once);
                stopped(() -> runner.run(dataset, () -> resource, body -> {
                    calls.incrementAndGet(); return new P3ReadOnlyRetrieval.HttpReply(status, "{}".getBytes());
                }, "OFFLINE_MOCK"));
                ok(calls.get() == 1 && runner.embedding.attempts == 1 && runner.search.attempts == 0 && resource.closed);
            });
        }
        test("timeout one intent no retry", () -> {
            Path once = fixtures.resolve("timeout"); var resource = new FakeResources(once);
            var calls = new AtomicInteger(); var runner = new P3ReadOnlyRetrieval(once);
            stopped(() -> runner.run(dataset, () -> resource, body -> { calls.incrementAndGet(); throw new java.net.http.HttpTimeoutException("mock"); }, "OFFLINE_MOCK"));
            ok(calls.get() == 1 && runner.embedding.attempts == 1 && resource.closed);
        });
        test("missing usage stops search", () -> {
            Path once = fixtures.resolve("missing-usage"); var resource = new FakeResources(once);
            var response = (com.fasterxml.jackson.databind.node.ObjectNode)P3ReadOnlyRetrieval.JSON.readTree(embedding()); response.remove("usage");
            var runner = new P3ReadOnlyRetrieval(once);
            stopped(() -> runner.run(dataset, () -> resource, body -> new P3ReadOnlyRetrieval.HttpReply(200, P3ReadOnlyRetrieval.JSON.writeValueAsBytes(response)), "OFFLINE_MOCK"));
            ok(runner.embedding.attempts == 1 && runner.search.attempts == 0);
        });
        test("after frozen resource mismatch fail", () -> {
            Path once = fixtures.resolve("after-fail"); var resource = new FakeResources(once); resource.unchanged = false;
            var runner = new P3ReadOnlyRetrieval(once);
            stopped(() -> runner.run(dataset, () -> resource, body -> new P3ReadOnlyRetrieval.HttpReply(200, embedding()), "OFFLINE_MOCK"));
            ok(runner.result.get("P3_RETRIEVAL_RESULT").equals("FAIL"));
        });
        var summary = Map.of("type", "OFFLINE_JAVA_UNIT_MOCK", "tests", tests, "failures", failures,
                "errors", errors, "skipped", 0, "passed", failures == 0 && errors == 0,
                "realExternalRequests", 0, "databaseConnections", 0, "databaseWrites", 0, "vectorWrites", 0);
        Files.write(folder.resolve("java-tests.json"), P3ReadOnlyRetrieval.JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(summary));
        System.out.println(P3ReadOnlyRetrieval.JSON.writeValueAsString(summary));
        if (failures != 0 || errors != 0) throw new AssertionError("P3_JAVA_OFFLINE_TEST_FAILURE");
    }
}
