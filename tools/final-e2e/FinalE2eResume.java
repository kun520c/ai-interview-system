import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import com.kun.aiinterview.interview.service.InterviewService;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Future explicitly authorized continuation. This class is never run during diagnosis/preparation. */
public final class FinalE2eResume {
    static final String RUN_ID="final-e2e-20261013-002";
    static final String AUTHORIZATION="AUTHORIZE_RESUME_150_ROTATE_TEST_PASSWORD_ONCE_MAX_3_CNY";
    static final String ACCOUNT="final_6a0a6fcb4ad04935852b";
    static double budgetUpper() {return 9*8320*.5/1_000_000+10*(FinalE2eGate.BODY_CAP*.3+4096*1.2)/1_000_000*8;}
    static void check(boolean condition,String code) { FinalE2eRunner.check(condition,code); }
    static void rotateAuthorizedFixturePassword(Connection writer,PasswordEncoder encoder,String password) throws Exception {
        // Explicit new human authorization covers only this synthetic test user's credentials.
        // No session, question, answer, evaluation, trace, report or weakness value is written here.
        try(var statement=writer.prepareStatement("UPDATE `user` SET password=?,password_changed_at=CURRENT_TIMESTAMP "
                +"WHERE id=427 AND account=? AND role='USER' AND status='ENABLED'")) {
            statement.setString(1,encoder.encode(password));statement.setString(2,ACCOUNT);
            check(statement.executeUpdate()==1,"EXACT_SYNTHETIC_USER_PASSWORD_ROTATION");
        }
    }
    static Map<String,Object> failure(Throwable error) {
        Map<String,Object> result=new LinkedHashMap<>(RagE2eRunner.failureDetails(error));
        result.put("operation",FinalE2eRunner.report.get("currentOperation"));
        if(error instanceof SecurityException && error.getMessage()!=null && error.getMessage().matches("[A-Z0-9_]+")) result.put("guardCode",error.getMessage());
        return result;
    }
    public static void main(String[] args) throws Exception {
        check(args.length==1&&AUTHORIZATION.equals(args[0]),"NEW_FEE_AND_SYNTHETIC_CREDENTIAL_AUTHORIZATION_REQUIRED");
        for(String key:List.of("FINAL_DB_USERNAME","FINAL_DB_PASSWORD","EMBEDDING_API_KEY","DEEPSEEK_API_KEY")) check(!FinalE2eRunner.env(key).isBlank(),"RECOVERY_CREDENTIAL_MISSING");
        check(budgetUpper()<3,"RESUME_BUDGET_EXCEEDED");
        Path output=Path.of("target/final-e2e/attempts",RUN_ID);
        Files.createDirectories(output.getParent()); Files.createDirectory(output);
        Files.writeString(output.resolve("ATTEMPT_RESERVED.lock"),Instant.now().toString(),StandardOpenOption.CREATE_NEW);
        FinalE2eRunner.output=output;FinalE2eRunner.userId=427;FinalE2eRunner.sessionId=150;FinalE2eRunner.judgeProvider="deepseek";
        var report=FinalE2eRunner.report;
        report.put("FINAL_E2E_STATUS","BLOCKED");report.put("runId",RUN_ID);report.put("continuationOf","final-e2e-20261013-001");
        report.put("feeAuthorizationConsumed",true);
        report.put("budget",Map.of("maximumNewProviderRequests",Map.of("embedding",9,"jev",0,"deepseek",10),
                "conservativeUpperCny",budgetUpper(),"proposedBudgetCny",3,"firstAnswerReevaluation",false));
        report.put("configuration",Map.of("profile","test-real","database","ai_interview_test","collection",RagE2ePolicy.COLLECTION,
                "provider","deepseek","reason","Prior Jev HTTP451 region rejection; use existing production DeepSeek provider",
                "rag","production dense TopK=5, no cosine threshold","p42Enabled",false));
        try(var c=FinalE2eRunner.connection()) {
            // Validate the actual runner's continuation guard before any credential write or fee.
            report.put("continuationHistoryContract",FinalE2eSecondRepair.verifyHistoryContract());
            FinalE2eRunner.continuationSessionId=150;
            var owner=FinalE2eRunner.sql(c,"SELECT user_id FROM interview_session WHERE id=150");
            check(owner.size()==1 && "427".equals(owner.getFirst().get("user_id")),"RESUME_HISTORY_SCOPE_OWNERSHIP");
            report.put("firstAnswerVerification",FinalE2eRepair.replay());
            report.put("preflight",FinalE2eRepair.inspect(c,output.resolve("admission")));
            FinalE2eRunner.output=output;
            FinalE2eRunner.gate=new FinalE2eGate(output.resolve("providers"),(uri,auth,body)->RagE2eModelGate.realTransport().post(uri,auth,body),Map.of("embedding",9,"jev",0,"deepseek",10),1);
            FinalE2eRunner.gate.start();FinalE2eRunner.app=FinalE2eRunner.boot();
            Files.writeString(output.resolve("EXECUTION_CONSUMED.lock"),Instant.now().toString(),StandardOpenOption.CREATE_NEW);
            String password=RagE2eRunner.randomCandidatePassword();
            try(var writer=DriverManager.getConnection(RagE2ePolicy.jdbc(RagE2ePolicy.TARGET),FinalE2eRunner.env("FINAL_DB_USERNAME"),FinalE2eRunner.env("FINAL_DB_PASSWORD"))) {
                check("ai_interview_test".equals(writer.getCatalog()),"CREDENTIAL_ROTATION_TEST_SCHEMA_ONLY");
                writer.setAutoCommit(false);
                rotateAuthorizedFixturePassword(writer,FinalE2eRunner.app.getBean(PasswordEncoder.class),password);
                writer.commit();
            }
            report.put("authorizedCredentialChange",Map.of("userId",427,"passwordRotated",true,"roleOrStatusChanged",false,"businessResultsChanged",false));
            c.rollback();FinalE2eRunner.captureHistory(c); // Preserve every row after the separately authorized credential rotation.
            var login=FinalE2eRunner.http("POST","/api/auth/login",Map.of("account",ACCOUNT,"password",password),200);
            FinalE2eRunner.token=login.path("accessToken").asText();
            check(login.path("userId").asLong()==427&&!FinalE2eRunner.token.isBlank(),"RESUME_NORMAL_LOGIN");
            var current=FinalE2eRunner.http("GET","/api/interviews/current",null,200);
            check(current.path("sessionId").asLong()==150&&current.path("currentQuestion").path("interviewQuestionId").asLong()==178,"ORIGINAL_SESSION_RESUME");
            var original=FinalE2eRepair.read("report.json");
            com.fasterxml.jackson.databind.JsonNode firstRequest=null;
            for(var h:original.path("http")) if(h.path("method").asText().equals("POST")&&h.path("path").asText().equals("/api/interviews/150/answers")) firstRequest=h.path("request");
            check(firstRequest!=null,"ORIGINAL_REQUEST_MISSING");
            var replay=FinalE2eRunner.http("POST","/api/interviews/150/answers",firstRequest,200);
            check(FinalE2eRunner.gate.total()==0&&RagE2eConsistency.equivalent(replay.path("evaluation"),
                    FinalE2eRepair.http(original,"POST","/api/interviews/150/answers").path("evaluation")),"FIRST_ANSWER_REPLAY_TRIGGERED_MODEL");
            var before=FinalE2eRunner.snapshot(c,"00-resumed-existing-session");
            FinalE2eRunner.evaluations.add(Map.of("questionId",173,"answerId",100,"sourceAttempt","final-e2e-20261013-001",
                    "providerOutcome","PRODUCTION_DEEPSEEK_FALLBACK","alreadyPaidAndReused",true,"reevaluated",false));
            report.put("originalProviderCounts",Map.of("embedding",1,"jev",1,"deepseek",1));
            var mains=FinalE2eRunner.sql(c,"SELECT * FROM interview_question WHERE session_id=150 AND question_type='MAIN' ORDER BY plan_order");
            FinalE2eRunner.completeInterview(c,mains,current.path("currentQuestion"),1,0,2);
        } catch(Exception error) {
            report.put("FINAL_E2E_STATUS","BLOCKED");report.put("failure",failure(error));
        } finally {
            if(FinalE2eRunner.app!=null)FinalE2eRunner.app.close();if(FinalE2eRunner.gate!=null)FinalE2eRunner.gate.close();
            try(var c=FinalE2eRunner.connection()) {
                FinalE2eRunner.snapshot(c,"99-final-or-failure");FinalE2eRunner.verifyHistory(c);
                report.put("frozenCorpusAndProtectedHistoricalFieldsUnchangedAfterAuthorizedContinuation",true);
            }
            catch(Exception error) {report.put("FINAL_E2E_STATUS","BLOCKED");report.put("cleanupVerificationFailure",failure(error));}
            FinalE2eRunner.checkpoint();
        }
        System.out.println("FINAL_E2E_STATUS="+report.get("FINAL_E2E_STATUS")+"; evidence="+output);
        if(!report.get("FINAL_E2E_STATUS").equals("REAL_E2E_PASS"))throw new IllegalStateException("Resume stopped. Keep this attempt and all locks; no automatic retry.");
    }
}
