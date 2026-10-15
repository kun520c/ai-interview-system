import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** New identity and diagnostic behavior with injected mocks; no network clients. */
public final class P42R02IdentityDiagnosticsTest {
    static int tests;
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        var envelope = P42R02ExecuteOnce.JSON.createObjectNode().put("authorization", P42ExecuteOnce.AUTH)
                .put("embeddingKey", "MOCK").put("rerankKey", "MOCK").put("deepseekKey", "MOCK")
                .put("dbPassword", "MOCK").put("workspaceId", "mock-beijing").put("budgetReferenceCny", 1);
        try { P42R02ExecuteOnce.credentials(envelope); throw new AssertionError(); }
        catch (P42R02ExecuteOnce.Stop expected) { check(expected.getMessage().equals("EXPLICIT_AUTH_REQUIRED")); }
        envelope.put("authorization", P42R02ExecuteOnce.AUTH); P42R02ExecuteOnce.credentials(envelope); tests++;
        check(!P42R02ExecuteOnce.RUN.equals(P42ExecuteOnce.RUN) && !P42R02ExecuteOnce.SEAL.equals(P42ExecuteOnce.SEAL)
                && !P42R02ExecuteOnce.AUTH.equals(P42ExecuteOnce.AUTH)); tests++;

        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        var mock = new P42R02RunnerTest.MockServices(); mock.failStage = "RERANK";
        var runner = new P42R02ExecuteOnce(out.resolve("stage-failure"), mock, () -> {},
                Files.readString(Path.of("tools/rag-p4-2/system-prompt.txt")), "MOCK", "MOCK", List.of("MOCK_SECRET"));
        var dataset = P42R02ExecuteOnce.read(P42R02ExecuteOnce.PREP.resolve("holdout-questions-v1.json"));
        try { runner.run(dataset, () -> mock); throw new AssertionError(); } catch (P42R02ExecuteOnce.Stop expected) {}
        var summary = P42R02ExecuteOnce.read(runner.output.resolve("summary.json"));
        check(summary.at("/failureDiagnostics/phase").asText().equals("RERANK")
                && summary.at("/failureDiagnostics/externalCallsAtFailure/EMBEDDING").asInt() == 1
                && summary.at("/failureDiagnostics/externalCallsAtFailure/MILVUS_SEARCH").asInt() == 1
                && summary.at("/failureDiagnostics/externalCallsAtFailure/RERANK").asInt() == 1
                && summary.at("/failureDiagnostics/externalCallsAtFailure/SEMANTIC_GATE").asInt() == 0);
        check(summary.path("executionId").asText().equals(P42R02ExecuteOnce.EXECUTION_ID)); tests++;
        var diagnostics = P42R02ExecuteOnce.failureDiagnostics(P42StopDiagnosticsTest.grpc("UNAVAILABLE"), "MILVUS_CLIENT_CONSTRUCT");
        check(diagnostics.path("grpcStatusCode").asText().equals("UNAVAILABLE")
                && diagnostics.path("rootCauseSummary").asText().contains("MILVUS_CLIENT_CONSTRUCT")
                && !diagnostics.toString().contains("SECRET") && !diagnostics.toString().contains("Bearer")); tests++;
        var lock = P42R02ExecuteOnce.read(runner.output.resolve("EXECUTION_RESERVED.lock"));
        String lockSha = P42R02ExecuteOnce.sha(runner.output.resolve("EXECUTION_RESERVED.lock"));
        int calls = mock.httpCalls;
        try { runner.run(dataset, () -> mock); throw new AssertionError(); }
        catch (Exception expected) { check(expected instanceof FileAlreadyExistsException || expected instanceof P42R02ExecuteOnce.Stop); }
        check(mock.httpCalls == calls && P42R02ExecuteOnce.sha(runner.output.resolve("EXECUTION_RESERVED.lock")).equals(lockSha)
                && lock.path("executionId").asText().equals(P42R02ExecuteOnce.EXECUTION_ID)
                && lock.path("authorization").asText().equals(P42R02ExecuteOnce.AUTH)); tests++;
        P42R02ExecuteOnce.writeJson(out.resolve("identity-diagnostic-tests.json"), Map.of("tests", tests, "failures", 0, "errors", 0, "skipped", 0, "newRealCalls", 0));
        System.out.println("R02 identity/diagnostic tests=" + tests + ", failures=0, errors=0, skipped=0; real calls=0");
    }
}
