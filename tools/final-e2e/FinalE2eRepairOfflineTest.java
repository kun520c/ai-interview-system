import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;

/** Replays the exact saved failure and ensures the full equality check still rejects corruption. */
public class FinalE2eRepairOfflineTest {
    static int tests;
    static void test(boolean condition) { tests++; if(!condition) throw new AssertionError("Repair check failed: "+tests); }
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]);
        var result=FinalE2eRepair.replay(); test(result.get("fixedFullDetailComparison").equals("PASS"));
        test(((List<?>)result.get("differentFields")).size()==3);
        try { FinalE2eRunner.verifyHttpMapper(new ObjectMapper().findAndRegisterModules());throw new AssertionError("Bad mapper accepted"); }
        catch(SecurityException expected) { test(expected.getMessage().equals("HTTP_DATE_SERIALIZATION_CONTRACT")); }
        FinalE2eRunner.verifyHttpMapper(FinalE2eRepair.JSON);test(true);
        var original=FinalE2eRepair.http(FinalE2eRepair.read("report.json"),"GET","/api/interviews/150");
        JsonNode repaired=FinalE2eRepair.detail(FinalE2eRepair.read("99-final-or-failure.database.json"));
        var changed=(ObjectNode)repaired.deepCopy(); changed.put("startedAt","2026-10-13T19:34:12");test(!RagE2eConsistency.equivalent(original,changed));
        changed=(ObjectNode)repaired.deepCopy();changed.remove("createdAt");test(!RagE2eConsistency.equivalent(original,changed));
        changed=(ObjectNode)repaired.deepCopy();((ObjectNode)changed.path("questions").get(0).path("evaluation")).put("totalScore",8);test(!RagE2eConsistency.equivalent(original,changed));
        changed=(ObjectNode)repaired.deepCopy();((ObjectNode)changed.path("questions").get(0).path("answer")).put("answerContent","tampered");test(!RagE2eConsistency.equivalent(original,changed));
        changed=(ObjectNode)repaired.deepCopy();var array=(com.fasterxml.jackson.databind.node.ArrayNode)changed.path("questions");
        var first=array.get(0);array.set(0,array.get(1));array.set(1,first);test(!RagE2eConsistency.equivalent(original,changed));
        try { Files.createDirectory(FinalE2eRepair.ORIGINAL);throw new AssertionError("Old attempt reservation accepted"); }
        catch(FileAlreadyExistsException expected) {test(true);}
        var gate=new FinalE2eGate(out,(uri,auth,body)-> {throw new AssertionError("Offline request forwarded");},Map.of("embedding",9,"jev",0,"deepseek",10),1);
        gate.begin("answer-2");try {gate.admit("jev","Bearer unused","{}".getBytes());throw new AssertionError("Known region-blocked provider admitted");}
        catch(SecurityException expected) {test(expected.getMessage().equals("MODEL_REQUEST_BUDGET_EXHAUSTED"));}
        test(gate.total()==0);
        test(FinalE2eResume.budgetUpper()<3);
        FinalE2eRunner.report.put("currentOperation","GET /api/interviews/150");
        var diagnostic=FinalE2eResume.failure(new SecurityException("HTTP_DETAIL_DB_PROJECTION_MISMATCH"));
        test(diagnostic.get("operation").equals("GET /api/interviews/150")&&diagnostic.get("guardCode").equals("HTTP_DETAIL_DB_PROJECTION_MISMATCH"));
        FinalE2eRunner.save(out.resolve("repair-offline-tests.json"),Map.of("tests",tests,"failures",0,"errors",0,"skipped",0,"modelCalls",0,"databaseWrites",0));
        FinalE2eRunner.save(out.resolve("pure-replay.json"),result);
        System.out.println("REPAIR_OFFLINE_PASS: "+tests+" tests; zero model calls and zero database writes");
    }
}
