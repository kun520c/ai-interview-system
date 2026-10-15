import java.nio.file.*;
import java.util.*;

/** No sockets, application context or database; reject real content changes. */
public final class RagE2eConsistencyOfflineTest {
    interface Checked { void run() throws Exception; }
    static int tests;
    static final String RAW="{\"score\":19,\"extra\":null,\"items\":[{\"id\":1,\"text\":\"中文\"},{\"id\":2}]}";
    static final List<Map<String,Object>> WIRE=List.of(Map.of("provider","deepseek",
            "responseModel","deepseek-flash","rawEvaluationSha256",RagE2ePolicy.hash(RAW)));
    static void check(Checked action) throws Exception { action.run();tests++; }
    static void rejects(Checked action) throws Exception {
        try { action.run();throw new AssertionError("Expected rejection"); }
        catch(SecurityException | com.fasterxml.jackson.core.JsonProcessingException expected) { tests++; }
    }
    static void verify(String stored) throws Exception {
        RagE2eConsistency.verifyModel(stored,RAW,"deepseek-flash","deepseek-flash",WIRE);
    }
    public static void main(String[] args) throws Exception {
        check(()->verify(RAW));
        check(()->verify(" { \"items\" : [ {\"text\":\"中文\", \"id\":1}, {\"id\":2}], \"extra\":null, \"score\":19 } "));
        check(()->verify(RAW.replace("中文","\\u4e2d\\u6587")));
        check(()->{
            if(!RagE2eConsistency.equivalent(RagE2eRunner.JSON.readTree("{\"id\":2476}"),
                    RagE2eRunner.JSON.valueToTree(Map.of("id",2476L))))throw new AssertionError("IntNode/LongNode must agree");
        });
        check(()->verify(RAW.replace("19","19.0")));
        rejects(()->verify(RAW.replace("19","18")));
        rejects(()->verify(RAW.replace("\"中文\"","\"英文\"")));
        rejects(()->verify(RAW.replace("\"extra\":null,","")));
        rejects(()->verify(RAW.replace("\"extra\":null","\"extra\":false")));
        rejects(()->verify(RAW.replace("\"extra\":null","\"extra\":null,\"newField\":1")));
        rejects(()->verify(RAW.replace("19","\"19\"")));
        rejects(()->verify(RAW.replace("[{\"id\":1,\"text\":\"中文\"},{\"id\":2}]","[{\"id\":2},{\"id\":1,\"text\":\"中文\"}]")));
        rejects(()->verify(RAW.replace("\"score\":19","\"score\":18,\"score\":19")));
        rejects(()->verify(RAW+" {}"));
        rejects(()->verify("null"));
        rejects(()->verify("[]"));
        rejects(()->RagE2eConsistency.verifyModel(RAW,RAW,"other","deepseek-flash",WIRE));
        rejects(()->RagE2eConsistency.verifyModel(RAW,RAW+" ","deepseek-flash","deepseek-flash",WIRE));
        rejects(()->RagE2eConsistency.verifyModel(RAW,RAW,"deepseek-flash","deepseek-flash",List.of()));
        rejects(()->RagE2eConsistency.verifyModel(RAW,RAW,"deepseek-flash","deepseek-flash",
                List.of(Map.of("provider","deepseek","responseModel","other","rawEvaluationSha256",RagE2ePolicy.hash(RAW)))));
        var summary=Map.of("tests",tests,"failures",0,"errors",0,"skipped",0,"modelCalls",0,"databaseWrites",0);
        Files.writeString(Path.of(args[0]),RagE2eRunner.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(summary),StandardOpenOption.CREATE_NEW);
        System.out.println(RagE2eRunner.JSON.writeValueAsString(summary));
    }
}
