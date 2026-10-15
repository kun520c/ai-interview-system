import java.nio.file.*;
import java.util.*;

/** Tests forwarding authorization and finite budgets without constructing a network client. */
public class FinalE2eOfflineTest {
    static int tests;
    static void expectBlocked(Checked action) throws Exception {
        tests++;
        try { action.run(); throw new AssertionError("Expected rejection"); }
        catch(SecurityException expected) { }
    }
    interface Checked { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        FinalE2eRunner.output=Path.of("target/final-e2e/preparation");
        var gate=new FinalE2eGate(FinalE2eRunner.output,(uri,auth,body)-> { throw new AssertionError("Offline transport invoked"); });
        expectBlocked(()->FinalE2eGate.provider("/unknown"));
        expectBlocked(()->gate.admit("deepseek","Bearer missing",new byte[FinalE2eGate.BODY_CAP+1]));
        expectBlocked(()->gate.admit("embedding","Bearer missing","{}".getBytes()));
        gate.begin("report");
        expectBlocked(()->gate.admit("jev","Bearer missing","{}".getBytes()));
        gate.begin("answer-1");
        gate.counts.put("embedding",10);
        expectBlocked(()->gate.admit("embedding","Bearer missing","{}".getBytes()));
        gate.counts.put("deepseek",21);
        expectBlocked(()->gate.admit("deepseek","Bearer missing","{}".getBytes()));
        gate.counts.put("deepseek",0); gate.operationCounts.put("deepseek",2);
        expectBlocked(()->gate.admit("deepseek","Bearer missing","{}".getBytes()));
        gate.operationCounts.clear();
        expectBlocked(()->gate.admit("deepseek","Bearer missing","{}".getBytes()));
        tests++; FinalE2eRunner.check(((Number)FinalE2eRunner.budget().get("conservativeBodyBasedUpperCny")).doubleValue()<5,"BUDGET");
        tests++;
        var old=Path.of("target/final-e2e/preparation/offline-reservation-probe");
        if(!Files.exists(old)) Files.createDirectory(old);
        try { Files.createDirectory(old); throw new AssertionError("Repeated reservation accepted"); }
        catch(FileAlreadyExistsException expected) { }
        FinalE2eRunner.save(FinalE2eRunner.output.resolve("gate-offline-tests.json"),Map.of("tests",tests,"failures",0,"errors",0,"skipped",0,"modelCalls",0));
        System.out.println("Final E2E gate offline tests: "+tests+" passed, zero model calls");
    }
}
