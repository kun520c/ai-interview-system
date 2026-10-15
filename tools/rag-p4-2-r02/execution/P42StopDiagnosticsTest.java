import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;

/** Synthetic gRPC errors and Mock resources only; no real connection or model. */
public class P42StopDiagnosticsTest {
    static int tests;
    static void check(boolean value) { if(!value) throw new AssertionError(); }
    static Exception grpc(String code) throws Exception {
        Class<?> type=Class.forName("io.milvus.shaded.io.grpc.Status");
        Object status=type.getField(code).get(null);
        status=type.getMethod("withDescription",String.class).invoke(status,"API_KEY=SECRET_PASSWORD Bearer SECRET_TOKEN");
        return (Exception)type.getMethod("asRuntimeException").invoke(status);
    }
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]);
        for(String code:List.of("DEADLINE_EXCEEDED","UNAVAILABLE","UNAUTHENTICATED")) {
            JsonNode diag=P42R02ExecuteOnce.failureDiagnostics(grpc(code),"RESOURCE_PREFLIGHT");
            check(diag.at("/causeChain/0/grpcCode").asText().equals(code));
            check(!diag.toString().contains("SECRET") && !diag.toString().contains("API_KEY") && !diag.toString().contains("Bearer")); tests++;
        }
        var outer=new P42R02ExecuteOnce.Stop("P42_STOPPED_PRESERVE_LOCKS_ANALYZE_OFFLINE"); outer.initCause(grpc("DEADLINE_EXCEEDED"));
        var chain=P42R02ExecuteOnce.failureDiagnostics(outer,"TERMINAL");
        check(chain.at("/causeChain/1/grpcCode").asText().equals("DEADLINE_EXCEEDED") && !chain.toString().contains("SECRET")); tests++;
        int[] http={0}; var runner=new P42R02ExecuteOnce(out.resolve("mock-preflight"),(s,b)-> {http[0]++; throw new AssertionError();},()->{},
                Files.readString(Path.of("tools/rag-p4-2/system-prompt.txt")),"MOCK","MOCK",List.of());
        var dataset=P42R02ExecuteOnce.read(P42R02ExecuteOnce.PREP.resolve("holdout-questions-v1.json"));
        try { runner.run(dataset,()-> {throw grpc("DEADLINE_EXCEEDED");}); throw new AssertionError(); }
        catch(P42R02ExecuteOnce.Stop expected) {}
        JsonNode summary=P42R02ExecuteOnce.read(runner.output.resolve("summary.json"));
        check(http[0]==0 && summary.path("executionStatus").asText().equals("STOPPED") &&
                summary.at("/failureDiagnostics/phase").asText().equals("RESOURCE_PREFLIGHT") &&
                summary.at("/failureDiagnostics/causeChain/0/grpcCode").asText().equals("DEADLINE_EXCEEDED"));
        check(Files.exists(runner.output.resolve("EXECUTION_RESERVED.lock")) && Files.exists(runner.output.resolve("artifacts.sha256")));
        for(var q:summary.path("questions")) check(q.path("status").asText().equals("NOT_STARTED")); tests++;
        try {runner.run(dataset,()-> {throw new AssertionError();});throw new AssertionError();} catch(FileAlreadyExistsException expected) {}
        check(http[0]==0); tests++;
        System.out.println("Future diagnostic fix: tests="+tests+", failures=0, errors=0, skipped=0; real connections=0; model calls=0");
    }
}
