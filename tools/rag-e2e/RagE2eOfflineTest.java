import com.fasterxml.jackson.databind.*;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import com.kun.aiinterview.config.EnvironmentIsolationInitializer;
import com.kun.aiinterview.knowledge.embedding.*;
import com.kun.aiinterview.interview.evaluation.score.*;
import com.kun.aiinterview.interview.evaluation.validation.*;
import com.kun.aiinterview.interview.evaluation.decision.*;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.URI;
import java.net.http.*;
import javax.net.ssl.SSLSession;

/** Focused executable tests. Fake upstream transport, no socket/server/JDBC/Milvus/model access. */
public final class RagE2eOfflineTest {
    interface Checked { void run() throws Exception; }
    static int tests;
    static void ok(boolean condition) { if(!condition)throw new AssertionError("Offline assertion failed"); }
    static void test(Checked action) throws Exception { action.run(); tests++; }
    static void rejects(Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected guard rejection"); }
        catch(SecurityException expected) { }
    }
    static List<String> writerGrants() {
        List<String> grants=new ArrayList<>(List.of("GRANT USAGE ON *.* TO `rag_e2e_writer`@`127.0.0.1`"));
        for(String t:RagE2ePolicy.TABLES) {
            List<String> privileges=new ArrayList<>(List.of("SELECT"));
            if(RagE2ePolicy.INSERT.contains(t))privileges.add("INSERT");
            if(RagE2ePolicy.UPDATE.contains(t))privileges.add("UPDATE");
            grants.add("GRANT "+String.join(", ",privileges)+" ON `ai_interview_test`.`"+t+"` TO `rag_e2e_writer`@`127.0.0.1`");
        }
        grants.add("GRANT UPDATE (`id`) ON `ai_interview_test`.`user` TO `rag_e2e_writer`@`127.0.0.1`");
        return grants;
    }
    static void grants(List<String> values) { RagE2ePolicy.grantSet("rag_e2e_writer@127.0.0.1","NONE","",values,true); }
    static Map<String,String> row(String... pairs) {
        var row=new LinkedHashMap<String,String>();
        for(int i=0;i<pairs.length;i+=2) row.put(pairs[i],pairs[i+1]);
        return row;
    }
    static void initializer(String schema,String collection,String... profiles) {
        try(var context=new GenericApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of(
                    "spring.datasource.url","jdbc:mysql://127.0.0.1:3306/"+schema,"milvus.collection-name",collection)));
            new EnvironmentIsolationInitializer().initialize(context);
        }
    }
    static void illegalTarget(String schema,String collection,String... profiles) {
        try { initializer(schema,collection,profiles); throw new AssertionError("Initializer accepted unsafe target"); }
        catch(IllegalStateException expected) { }
    }
    record FakeResponse(int statusCode,String body,HttpRequest request) implements HttpResponse<String> {
        public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        public HttpHeaders headers() { return HttpHeaders.of(Map.of(),(a,b)->true); }
        public Optional<SSLSession> sslSession() { return Optional.empty(); }
        public URI uri() { return request.uri(); }
        public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
    static FakeResponse response(int status,String body) throws Exception {
        RagE2eRunner.baseUrl="http://127.0.0.1:12345";
        return new FakeResponse(status,body,RagE2eRunner.businessRequest("POST","/api/interviews",Map.of("difficulty","EASY")));
    }
    public static void main(String[] args) throws Exception {
        test(()->{
            var error=io.milvus.shaded.io.grpc.Status.DEADLINE_EXCEEDED
                    .withDescription("waiting_for_connection").asRuntimeException();
            error.setStackTrace(new StackTraceElement[]{new StackTraceElement(
                    "io.milvus.v2.client.MilvusClientV2","connect","MilvusClientV2.java",243)});
            RagE2eRunner.report.put("currentOperation","MilvusClientV2.<init>");
            var details=RagE2eRunner.failureDetails(error);
            ok("DEADLINE_EXCEEDED".equals(details.get("grpcStatus"))
                    && "waiting_for_connection".equals(details.get("grpcDescription"))
                    && "MilvusClientV2.<init>".equals(details.get("operation"))
                    && details.get("causeChain").toString().contains("MilvusClientV2.java:243"));
        });
        test(()->{
            RagE2eRunner.token="synthetic-secret";
            try {
                var error=new IllegalStateException("do not export this message",io.milvus.shaded.io.grpc.Status.UNAUTHENTICATED
                        .withDescription("Authorization: Bearer synthetic-secret; token=unknown-secret").asException());
                var details=RagE2eRunner.failureDetails(error);
                String exported=new ObjectMapper().writeValueAsString(details);
                ok("UNAUTHENTICATED".equals(details.get("grpcStatus"))
                        && !exported.contains("synthetic-secret") && !exported.contains("unknown-secret")
                        && !exported.contains("do not export this message"));
            } finally { RagE2eRunner.token=null; }
        });
        test(()->{
            var details=RagE2eRunner.failureDetails(new IllegalArgumentException("private payload"));
            ok(!details.containsKey("grpcStatus") && !details.toString().contains("private payload")
                    && RagE2eRunner.safeGrpcDescription(null,List.of())==null);
            RagE2eRunner.report.remove("currentOperation");
        });
        test(()->{
            RagE2eRunner.report.put("currentOperation","MilvusClientV2.query:knowledge_chunk_vectors_test");
            var reply=response(500,"{\"code\":500,\"message\":\"系统异常，请稍后重试\",\"data\":null}");
            var evidence=RagE2eRunner.businessHttpEvidence(reply);
            ok(evidence.get("method").equals("POST") && evidence.get("path").equals("/api/interviews")
                    && evidence.get("httpStatus").equals(500));
            rejects(()->RagE2eRunner.successful(reply));
            var details=RagE2eRunner.failureDetails(new SecurityException("BUSINESS_HTTP_STATUS_MISMATCH"));
            ok(details.get("operation").equals("BUSINESS_HTTP POST /api/interviews"));
        });
        test(()->ok(RagE2eRunner.successful(response(200,"{\"code\":200,\"message\":\"success\",\"data\":{\"sessionId\":42}}"))
                .path("sessionId").asInt()==42));
        for(int status:List.of(201,202,204,400,401,403,404,409,500,502,503))
            test(()->rejects(()->RagE2eRunner.successful(response(status,"{\"code\":200,\"data\":{}}"))));
        for(String body:List.of("null","{}","{\"code\":\"200\"}","{\"code\":400}","{\"code\":200.5}","{\"code\":4294967496}"))
            test(()->rejects(()->RagE2eRunner.successful(response(200,body))));
        test(()->{
            String body="{\"code\":500,\"message\":\"Authorization: Bearer private-secret\","
                    +"\"data\":{\"accessToken\":\"private-jwt\",\"answerContent\":\"private-answer\"}}";
            String exported=RagE2eRunner.JSON.writeValueAsString(RagE2eRunner.businessHttpEvidence(response(500,body)));
            ok(!exported.contains("private-secret") && !exported.contains("private-jwt") && !exported.contains("private-answer"));
        });
        test(()->ok(RagE2eRunner.businessHttpEvidence(response(500,"<html>private-jwt</html>"))
                .get("sanitizedResponse").toString().contains("NON_JSON_RESPONSE")));
        test(()->{
            var error=new IllegalStateException("private SQL",new java.sql.SQLSyntaxErrorException("private SQL and account","42000",1142));
            var observer=RagE2eAudit.e2eObservers();
            var handler=(com.kun.aiinterview.common.exception.GlobalExceptionHandler)observer.postProcessAfterInitialization(
                    new com.kun.aiinterview.common.exception.GlobalExceptionHandler(),"handler");
            ok(handler.handleException(error).getCode()==500);
            var details=RagE2eAudit.serviceFailures.getLast();
            ok(details.get("sqlErrorCode").equals(1142) && details.get("sqlState").equals("42000")
                    && !details.toString().contains("private SQL"));
        });
        test(()->{
            var method=com.kun.aiinterview.interview.controller.InterviewController.class.getMethod("createInterview",
                    com.kun.aiinterview.security.model.AuthenticatedUser.class,
                    com.kun.aiinterview.interview.dto.CreateInterviewRequest.class);
            ok(method.getAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class)==null
                    && method.getReturnType()==com.kun.aiinterview.common.response.Result.class);
            ok(RagE2eRunner.expectedHttpStatus("POST","/api/auth/register")==200
                    && RagE2eRunner.expectedHttpStatus("POST","/api/auth/login")==200);
            rejects(()->RagE2eRunner.expectedHttpStatus("POST","/api/admin/knowledge/documents"));
            rejects(()->RagE2eRunner.expectedHttpStatus("DELETE","/api/interviews/42"));
        });
        test(RagE2eRunner::loadContract);
        test(()->{
            RagE2eRunner.loadRecoveryPlan(RagE2eRunner.RECOVERY_RUN_ID);
            ok(RagE2eRunner.recoveryPlan.path("authMode").asText().equals("REGISTER_NEW_USER")
                    && !RagE2eRunner.recoveryPlan.path("corpusWritesAllowed").asBoolean()
                    && !RagE2eRunner.recoveryPlan.path("milvusWritesAllowed").asBoolean());
        });
        for(String runId:List.of("../run-once","rag-e2e-attempt-004-20261012","","rag-e2e-attempt-003-20261012/../run-once"))
            test(()->rejects(()->RagE2eRunner.loadRecoveryPlan(runId)));
        test(()->{
            Path root=Files.createTempDirectory(Path.of("target/rag-e2e"),"offline-recovery-");
            Path attempt=RagE2eRunner.reserveRecoveryAttempt(root,RagE2eRunner.RECOVERY_RUN_ID);
            Path lock=attempt.resolve("ATTEMPT_RESERVED.lock"); String original=Files.readString(lock);
            Files.writeString(attempt.resolve("report.json"),"original synthetic report",StandardOpenOption.CREATE_NEW);
            try { RagE2eRunner.reserveRecoveryAttempt(root,RagE2eRunner.RECOVERY_RUN_ID); throw new AssertionError(); }
            catch(FileAlreadyExistsException expected) { }
            ok(Files.readString(lock).equals(original)
                    && Files.readString(attempt.resolve("report.json")).equals("original synthetic report")
                    && attempt.getParent().equals(root.toAbsolutePath().normalize()));
        });
        test(()->{
            Path root=Path.of("target/rag-e2e/never-created-by-invalid-id");
            rejects(()->RagE2eRunner.reserveRecoveryAttempt(root,"../run-once"));
            ok(!Files.exists(root));
        });
        test(()->{
            String oldLock=Files.readString(Path.of("target/rag-e2e/EXECUTION_CONSUMED.lock"));
            String reportHash=RagE2ePolicy.hash(Files.readAllBytes(RagE2eRunner.OUT.resolve("report.json")));
            rejects(RagE2eRunner::guardLegacyAttempt);
            ok(oldLock.equals(Files.readString(Path.of("target/rag-e2e/EXECUTION_CONSUMED.lock")))
                    && reportHash.equals(RagE2ePolicy.hash(Files.readAllBytes(RagE2eRunner.OUT.resolve("report.json")))));
        });
        test(()->{
            Path previous=RagE2eRunner.outputDirectory;
            try {
                RagE2eRunner.outputDirectory=Files.createTempDirectory(Path.of("target/rag-e2e"),"offline-report-");
                String original=RagE2ePolicy.hash(Files.readAllBytes(RagE2eRunner.OUT.resolve("report.json")));
                RagE2eRunner.writeReport();
                ok(Files.exists(RagE2eRunner.outputDirectory.resolve("report.json"))
                        && original.equals(RagE2ePolicy.hash(Files.readAllBytes(RagE2eRunner.OUT.resolve("report.json")))));
            } finally { RagE2eRunner.outputDirectory=previous; }
        });
        test(()->{
            RagE2eRunner.recoveryAttempt=true;
            try {
                rejects(()->RagE2eRunner.copyMissingCorpus(null));
                rejects(()->RagE2eRunner.prepareVectors(null,List.of()));
                ok(RagE2eRunner.businessRequest("POST","/api/auth/register",Map.of()).method().equals("POST"));
                ok(RagE2eRunner.businessRequest("POST","/api/auth/login",Map.of()).method().equals("POST")
                        && RagE2eRunner.missingEnvironment().stream().noneMatch(n->n.startsWith("E2E_SOURCE_"))
                        && !RagE2eRunner.missingEnvironment().contains("E2E_CANDIDATE_PASSWORD"));
            } finally { RagE2eRunner.recoveryAttempt=false; }
        });
        test(()->ok(RagE2eRunner.contract.path("mysqlFrozen").path("knowledge_chunk").path("count").asInt()==52));
        test(()->ok(RagE2ePolicy.rowHash(List.of(new LinkedHashMap<>(Map.of("one","中")))).equals(RagE2ePolicy.hash("1:中;\n"))));
        test(()->RagE2ePolicy.auditJdbc(RagE2ePolicy.jdbc("ai_interview_test")));
        test(()->rejects(()->RagE2ePolicy.auditJdbc(RagE2ePolicy.jdbc("ai_interview"))));
        test(()->rejects(()->RagE2ePolicy.auditJdbc(RagE2ePolicy.jdbc("ai_interview_test")+"&allowMultiQueries=true")));
        test(()->grants(writerGrants()));
        test(()->rejects(()->{var grants=writerGrants(); grants.replaceAll(g->g.contains(".`user`")
                ?g.replace("SELECT, INSERT","SELECT"):g); grants(grants);}));
        test(()->rejects(()->{var grants=writerGrants(); grants.removeIf(g->g.contains("UPDATE (`id`)")); grants(grants);}));
        test(()->{var grants=writerGrants(); grants.removeIf(g->g.contains("UPDATE (`id`)"));
            grants.replaceAll(g->g.contains(".`user`")?g.replace("SELECT, INSERT","SELECT, INSERT, UPDATE (`id`)"):g);
            grants(grants);});
        for(String privilege:List.of("UPDATE","UPDATE (`password`)","UPDATE (`role`)","UPDATE (`status`)","UPDATE (`id`, `role`)"))
            test(()->rejects(()->{var grants=writerGrants();grants.add("GRANT "+privilege
                    +" ON `ai_interview_test`.`user` TO `rag_e2e_writer`@`127.0.0.1`"); grants(grants);}));
        test(()->rejects(()->RagE2ePolicy.grantSet("root@localhost","NONE","",writerGrants(),true)));
        test(()->rejects(()->RagE2ePolicy.grantSet("rag_e2e_writer@127.0.0.1","`admin`@`%`","",writerGrants(),true)));
        test(()->rejects(()->RagE2ePolicy.grantSet("rag_e2e_writer@127.0.0.1","NONE","admin",writerGrants(),true)));
        for(String extra:List.of("GRANT ALL PRIVILEGES ON *.* TO `rag_e2e_writer`@`127.0.0.1`",
                "GRANT SELECT ON `ai_interview_test`.* TO `rag_e2e_writer`@`127.0.0.1`",
                "GRANT INSERT ON `ai_interview`.`user` TO `rag_e2e_writer`@`127.0.0.1`",
                "GRANT DELETE ON `ai_interview_test`.`interview_answer` TO `rag_e2e_writer`@`127.0.0.1`",
                "GRANT INSERT ON `ai_interview_test`.`interview_report` TO `rag_e2e_writer`@`127.0.0.1`",
                "GRANT UPDATE ON `ai_interview_test`.`question` TO `rag_e2e_writer`@`127.0.0.1`",
                "GRANT SELECT ON `ai_interview_test`.`user` TO `rag_e2e_writer`@`127.0.0.1` WITH GRANT OPTION"))
            test(()->rejects(()->{var g=writerGrants();g.add(extra);grants(g);}));
        test(()->rejects(()->{var g=writerGrants();g.removeIf(s->s.contains("`rag_hit_log`"));grants(g);}));
        test(()->initializer("ai_interview_test","knowledge_chunk_vectors_test","test-real","rag-p1"));
        test(()->illegalTarget("ai_interview","knowledge_chunk_vectors_test","test-real","rag-p1"));
        test(()->illegalTarget("ai_interview_test","knowledge_chunk_vectors_local","test-real","rag-p1"));
        test(()->illegalTarget("ai_interview_test","knowledge_chunk_vectors_test","evaluation","test-real"));
        test(()->rejects(()->RagE2ePolicy.endpoint("https://proxy.example/v1",false)));
        test(()->rejects(()->RagE2ePolicy.endpoint("https://api.deepseek.com?key=secret",false)));
        test(()->rejects(()->RagE2ePolicy.endpoint("http://dashscope.aliyuncs.com/compatible-mode/v1",true)));
        test(()->ok("deepseek-flash".equals(RagE2ePolicy.JUDGE_MODEL)
                && RagE2ePolicy.embeddingRate(RagE2ePolicy.EMBEDDING_ENDPOINT)==0.50
                && RagE2ePolicy.embeddingRate("https://dashscope-intl.aliyuncs.com/compatible-mode/v1")==0.525));
        test(()->{RagE2eRunner.priceGate(); var cost=(Map<?,?>)RagE2eRunner.report.get("cost");
            ok(Boolean.FALSE.equals(cost.get("estimateIsAccountDebitCap"))
                    && ((Number)cost.get("conservativeEstimateCny")).doubleValue()<1.0
                    && ((Number)cost.get("embeddingRateCnyPerM")).doubleValue()==0.50);});
        test(()->{
            var documents=List.of(row("id","9","document_version","1","processing_status","READY"));
            var chunks=List.of(row("document_id","9","document_version","1","embedding_model",RagE2ePolicy.MODEL,
                    "embedding_version",RagE2ePolicy.VERSION,"status","ACTIVE","vector_id","11111111-1111-1111-1111-111111111111"));
            RagE2ePolicy.corpusProfile(documents,chunks);
            rejects(()->RagE2ePolicy.corpusProfile(documents,List.of(row("document_id","9","document_version","2",
                    "embedding_model",RagE2ePolicy.MODEL,"embedding_version",RagE2ePolicy.VERSION,"status","ACTIVE",
                    "vector_id","22222222-2222-2222-2222-222222222222"))));
        });
        test(()->{
            List<String> grants=new ArrayList<>(List.of("GRANT USAGE ON *.* TO `rag_e2e_source_ro`@`127.0.0.1`"));
            for(String table:RagE2ePolicy.CORPUS)
                grants.add("GRANT SELECT ON `ai_interview`.`"+table+"` TO `rag_e2e_source_ro`@`127.0.0.1`");
            ok(RagE2ePolicy.grantSet("rag_e2e_source_ro@127.0.0.1","NONE","",grants,false).contains("SELECT@knowledge_chunk"));
            rejects(()->{var extra=new ArrayList<>(grants); extra.add("GRANT INSERT ON `ai_interview`.`question` TO `rag_e2e_source_ro`@`127.0.0.1`");
                RagE2ePolicy.grantSet("rag_e2e_source_ro@127.0.0.1","NONE","",extra,false);});
        });
        for(String mutation:List.of("insert","upsert","delete","createCollection","dropCollection","loadCollection"))
            test(()->rejects(()->RagE2ePolicy.milvusMethod(mutation)));
        test(()->RagE2ePolicy.milvusMethod("search"));
        AtomicInteger upstream=new AtomicInteger();
        var mapper=new ObjectMapper();
        var gate=new RagE2eModelGate("https://dashscope.aliyuncs.com/compatible-mode/v1","https://api.deepseek.com",
                "fake-embedding","fake-judge",(u,a,b)->{upstream.incrementAndGet();return new RagE2eModelGate.Reply(500,"{}".getBytes());});
        byte[] embedding=mapper.writeValueAsBytes(Map.of("model",RagE2ePolicy.MODEL,"dimensions",1024,"encoding_format","float","input",List.of("synthetic query")));
        test(()->ok(gate.forward("/embedding/embeddings","Bearer fake-embedding",embedding).status()==500));
        test(()->rejects(()->gate.forward("/embedding/embeddings","Bearer fake-embedding",embedding)));
        test(()->ok(upstream.get()==1)); // upstream failure consumed budget, never refunded
        RagE2eAudit.prompt=new EvaluationPrompt("e2e-offline-v1","system","user");
        byte[] judge=mapper.writeValueAsBytes(Map.of("model",RagE2ePolicy.JUDGE_MODEL,"max_tokens",4096,"stream",false,
                "thinking",Map.of("type","disabled"),"response_format",Map.of("type","json_object"),
                "messages",List.of(Map.of("role","system","content","system"),Map.of("role","user","content","user"))));
        test(()->ok(gate.forward("/judge/chat/completions","Bearer fake-judge",judge).status()==500));
        test(()->rejects(()->gate.forward("/judge/chat/completions","Bearer fake-judge",judge)));
        test(()->ok(upstream.get()==2));
        test(()->rejects(()->gate.forward("/judge/retry","Bearer fake-judge",judge)));
        test(()->rejects(()->RagE2ePolicy.modelRequest(((com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(judge)).put("max_tokens",4097),false)));
        test(()->rejects(()->RagE2ePolicy.modelRequest(((com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(embedding)).put("dimensions",768),true)));
        test(()->{var budget=new RagE2ePolicy.Budget(1);budget.take();rejects(budget::take);});
        test(()->{
            EmbeddingClient fake=inputs->new EmbeddingBatchResult(RagE2ePolicy.MODEL,RagE2ePolicy.VERSION,1024,
                    List.of(new EmbeddingVector(0,Collections.nCopies(1024,0.0F))),1L);
            var observed=(EmbeddingClient)RagE2eAudit.e2eObservers().postProcessAfterInitialization(fake,"fakeEmbedding");
            ok(observed.embed(List.of("offline probe")).dimension()==1024);
            rejects(()->observed.embed(List.of("forbidden second call")));
        });
        test(()->{
            var observer=RagE2eAudit.e2eObservers();
            var calculator=(EvaluationScoreCalculator)observer.postProcessAfterInitialization(new EvaluationScoreCalculator(),"score");
            var suggestion=new ValidatedEvaluationSuggestion(25,20,15,10,10,List.of("fixture"),List.of(),"fixture",List.of(),false,"");
            ok(calculator.calculate(suggestion).totalScore()==80 && RagE2eAudit.score.totalScore()==80);
        });
        test(()->{
            var observer=RagE2eAudit.e2eObservers();
            ok(observer.postProcessAfterInitialization(new LlmEvaluationValidator(),"validator") instanceof LlmEvaluationValidator);
            ok(observer.postProcessAfterInitialization(new FollowUpPolicy(new FollowUpTargetResolver()),"policy") instanceof FollowUpPolicy);
            ok(observer.postProcessAfterInitialization(new com.kun.aiinterview.interview.evaluation.prompt.EvaluationPromptBuilder(),"prompt")
                    instanceof com.kun.aiinterview.interview.evaluation.prompt.EvaluationPromptBuilder);
        });
        test(()->{Path dir=Files.createTempDirectory(Path.of("target/rag-e2e"),"offline-lock-");
            Path lock=dir.resolve("consumed");Files.writeString(lock,"intent",StandardOpenOption.CREATE_NEW);
            try { Files.writeString(lock,"retry",StandardOpenOption.CREATE_NEW);throw new AssertionError(); }
            catch(FileAlreadyExistsException expected) { } });
        RagE2eRegistrationFlowOfflineTest.run();
        var summary=Map.of("tests",tests,"failures",0,"errors",0,"skipped",0,"liveCalls",0,"liveWrites",0);
        Path summaryPath=args.length==0?Path.of("target/rag-e2e/offline-tests.json"):Path.of(args[0]);
        mapper.writerWithDefaultPrettyPrinter().writeValue(summaryPath.toFile(),summary);
        System.out.println("E2E focused offline tests: "+mapper.writeValueAsString(summary));
    }
}
