package com.kun.aiinterview.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.kun.aiinterview.interview.evaluation.*;
import com.kun.aiinterview.interview.evaluation.judge.*;
import com.kun.aiinterview.interview.evaluation.judge.jev.*;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPromptBuilder;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import com.kun.aiinterview.interview.evaluation.score.EvaluationScoreCalculator;
import com.kun.aiinterview.interview.evaluation.decision.FollowUpPolicy;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import com.kun.aiinterview.knowledge.chunk.KnowledgeTextChunker;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import com.kun.aiinterview.knowledge.service.KnowledgeRetrievalService;
import com.kun.aiinterview.knowledge.vector.milvus.MilvusProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.user.enums.UserRole;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Real services only. Explicit opt-in, isolated evaluation profile, and a real embedded HTTP server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("evaluation")
@Import(BaselineTraceConfiguration.class)
@EnabledIfEnvironmentVariable(named = "RUN_REAL_AI_BASELINE", matches = "true")
class AiSystemBaselineEvaluationTest {
    @Autowired ObjectMapper mapper;
    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired EmbeddingClient embedding;
    @Autowired EmbeddingProperties embeddingProperties;
    @Autowired MilvusProperties milvusProperties;
    @Autowired MilvusClientV2 milvus;
    @Autowired KnowledgeRetrievalService retrieval;
    @Autowired EvaluationRetrievalAdapter evaluationRetrieval;
    @Autowired EvaluationPromptBuilder prompts;
    @Autowired EvaluationRagQueryBuilder queries;
    @Autowired LlmEvaluationValidator validator;
    @Autowired EvaluationScoreCalculator scores;
    @Autowired FollowUpPolicy followUpPolicy;
    @Autowired JudgeProvider judge;
    @Autowired DeepSeekEvaluationClient deepseek;
    @Autowired KnowledgeTextChunker chunker;
    @Autowired JwtTokenService jwtTokens;
    @LocalServerPort int port;

    @Test
    void evaluateBaseline() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("RUN_REAL_AI_BASELINE")));
        assertThat(environment.getActiveProfiles()).contains("evaluation");
        assertThat(db().queryForObject("SELECT DATABASE()", String.class))
                .isEqualTo("ai_interview_evaluation");
        assertThat(milvusProperties.getCollectionName())
                .isEqualTo("knowledge_chunk_vectors_evaluation");
        String phase = System.getProperty("evaluation.phase", "inventory");
        switch (phase) {
            case "inventory" -> inventory();
            case "rag" -> rag();
            case "judge" -> judgeCases();
            case "pipeline" -> pipeline();
            case "e2e" -> e2e();
            case "recovery" -> recovery();
            default -> throw new IllegalArgumentException("Unsupported baseline phase");
        }
        System.out.println("BASELINE_PHASE=" + phase + " OUTPUT=docs/evaluation/evidence");
    }

    void inventory() throws Exception {
        JdbcTemplate db = new JdbcTemplate(dataSource);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());
        result.put("mysqlVersion", db.queryForObject("SELECT VERSION()", String.class));
        result.put("judgeProvider", environment.getProperty("ai.judge.provider"));
        result.put("jevCredentialPresent", hasText(environment.getProperty("ai.judge.jev.api-key")));
        result.put("deepseekCredentialPresent", hasText(environment.getProperty("deepseek.api-key")));
        result.put("deepseekModel", environment.getProperty("deepseek.model"));
        result.put("embeddingModel", embeddingProperties.getModel());
        result.put("embeddingDimension", embeddingProperties.getDimension());
        result.put("embeddingVersion", embeddingProperties.getProfileVersion());
        result.put("collection", milvusProperties.getCollectionName());
        result.put("schema", milvus.describeCollection(DescribeCollectionReq.builder()
                .collectionName(milvusProperties.getCollectionName()).build()));
        result.put("index", milvus.describeIndex(DescribeIndexReq.builder()
                .collectionName(milvusProperties.getCollectionName()).fieldName("vector").build()));
        result.put("questions", db.queryForList("""
                SELECT q.id,q.category,q.difficulty,q.knowledge_point,q.question_content,
                       q.reference_answer,q.status FROM question q ORDER BY q.id
                """));
        result.put("scoringPoints", db.queryForList("""
                SELECT id,question_id,point_type,content,weight FROM question_scoring_point ORDER BY question_id,id
                """));
        result.put("documents", db.queryForList("""
                SELECT id,title,category,source,file_type,content,document_version,processing_status
                FROM knowledge_document ORDER BY id
                """));
        result.put("chunks", db.queryForList("""
                SELECT id,document_id,document_version,chunk_index,content,token_count,vector_id,
                       embedding_model,embedding_version,status FROM knowledge_chunk ORDER BY document_id,chunk_index
                """));
        var probe = embedding.embed(List.of("Java HashMap 根据哈希值定位桶，哈希冲突通过链表或红黑树处理。"));
        result.put("embeddingProbe", Map.of("model",probe.model(),"dimension",probe.vectors().getFirst().values().size(),
                "vectorCount",probe.vectors().size(),"finite",probe.vectors().getFirst().values().stream().allMatch(Float::isFinite)));
        write("inventory.json", result);
    }

    boolean hasText(String value) { return value != null && !value.isBlank(); }

    JdbcTemplate db() { return new JdbcTemplate(dataSource); }
    JsonNode read(String name) throws Exception {
        return mapper.readTree(Path.of("docs/evaluation/data", name).toFile());
    }

    EvaluationContext context(JsonNode c, String answer) {
        List<ScoringPointSnapshot> points = new ArrayList<>();
        for (JsonNode p : c.path("scoringPoints")) {
            points.add(new ScoringPointSnapshot(p.path("id").asLong(),
                    QuestionPointType.valueOf(p.path("pointType").asText()),
                    p.path("content").asText(), p.path("weight").asInt()));
        }
        return new EvaluationContext(1L,1L,1L,1L,EvaluationMode.MAIN_ANSWER,
                QuestionCategory.valueOf(c.path("category").asText()), c.path("knowledgePoint").asText(),
                c.path("question").asText(), c.path("referenceAnswer").asText(), points,
                answer, null, null, List.of());
    }

    void rag() throws Exception {
        String dataset = System.getProperty("evaluation.dataset", "existing");
        List<Object> traces = new ArrayList<>();
        for (JsonNode c : read(dataset + "-golden.json")) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("case",c);
            EvaluationContext context = context(c,"ANSWER_NOT_IN_RETRIEVAL_测试标识");
            String query = queries.build(context);
            assertThat(query).doesNotContain(context.mainAnswerContent());
            row.put("query",query);
            long start = System.nanoTime();
            try {
                var result = evaluationRetrieval.retrieve(context,5);
                row.put("result",result);
                row.put("status","EXECUTED");
            } catch (RuntimeException failure) {
                row.put("status","ERROR");
                row.put("errorClass",failure.getClass().getSimpleName());
            }
            row.put("elapsedMs",(System.nanoTime()-start)/1_000_000);
            traces.add(row);
            write("rag-" + dataset + ".json",traces);
            System.out.println("BASELINE_RAG_CASE=" + c.path("id").asText() + " STATUS=" + row.get("status"));
        }
    }

    void judgeCases() throws Exception {
        JsonNode data = read("judge-cases.json");
        var base = data.path("context");
        var constantRetrieval = evaluationRetrieval.retrieve(context(base,"retrieval does not use this"),5);
        List<Object> rows = new ArrayList<>();
        boolean jevAvailable = hasText(environment.getProperty("ai.judge.jev.api-key"));
        JudgeProvider provider = judge;
        for (JsonNode c : data.path("cases")) {
            for (int repeat=1;repeat<=c.path("repeats").asInt(1);repeat++) {
                EvaluationContext ctx = context(base,c.path("input").asText());
                Map<String,Object> row = evaluateJudge(provider,ctx,constantRetrieval);
                row.put("case",c);
                row.put("repeat",repeat);
                row.put("jevStatus",jevAvailable ? row.get("actualProvider") : "BLOCKED_MISSING_CREDENTIAL");
                rows.add(row);
                write("judge-cases.json",rows);
                System.out.println("BASELINE_JUDGE_CASE=" + c.path("id").asText() + " REPEAT=" + repeat + " STATUS=" + row.get("status"));
            }
        }
        // Network failure injection: no fabricated AI responses; fallback still calls real DeepSeek.
        // Port 1 is deliberately unavailable. This is resilience evidence, not Jev quality evidence.
        JevProperties failureProperties = new JevProperties();
        failureProperties.setBaseUrl(URI.create("http://127.0.0.1:1"));
        failureProperties.setApiKey("evaluation-local-network-fault");
        failureProperties.setConnectTimeout(Duration.ofMillis(250));
        failureProperties.setReadTimeout(Duration.ofMillis(250));
        var faultClient = new JevClient(RestClient.builder().baseUrl(failureProperties.getBaseUrl().toString())
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(250)).build())).build(),mapper);
        var faultProvider = new JevJudgeProvider(faultClient,failureProperties,deepseek,
                new DeepSeekJudgeProvider(deepseek),mapper,validator);
        var networkFault = evaluateJudge(faultProvider,context(base,data.path("cases").get(0).path("input").asText()),constantRetrieval);
        networkFault.put("injection","real connection refusal; real DeepSeek fallback; no mock responses");
        write("judge-network-fault.json",networkFault);

        List<Object> ablation = new ArrayList<>();
        JsonNode partial = data.path("cases").get(1);
        var ctx = context(base,partial.path("input").asText());
        var empty = new EvaluationRetrievalResult(constantRetrieval.query(),5,
                constantRetrieval.embeddingModel(),constantRetrieval.embeddingVersion(),List.of(),List.of());
        for(int i=0;i<3;i++) {
            var row = evaluateJudge(provider,ctx,empty);
            row.put("ablation","same context; no RAG evidence");
            ablation.add(row);
        }
        write("judge-rag-ablation.json",ablation);
    }

    Map<String,Object> evaluateJudge(JudgeProvider provider, EvaluationContext context,
                                     EvaluationRetrievalResult retrieved) {
        Map<String,Object> row = new LinkedHashMap<>();
        long start = System.nanoTime();
        try {
            var prompt = prompts.build(context,retrieved);
            row.put("prompt",prompt);
            row.put("retrieval",retrieved);
            var result = provider.evaluate(context,prompt);
            row.put("providerResult",result);
            JsonNode raw = mapper.readTree(result.rawJson());
            row.put("actualProvider",raw.path("provider").asText("deepseek"));
            var validated = validator.validate(context,result.suggestion());
            var score = scores.calculate(validated);
            row.put("javaScore",score);
            row.put("javaDecision",followUpPolicy.decide(context,validated,score,true));
            row.put("status","EXECUTED_VALIDATED");
        } catch (Exception failure) {
            row.put("status","ERROR");
            row.put("errorClass",failure.getClass().getSimpleName());
        }
        row.put("elapsedMs",(System.nanoTime()-start)/1_000_000);
        return row;
    }

    record ApiResult(int httpStatus, JsonNode body, long elapsedMs) {}
    ApiResult api(String method,String path,String token,Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                .timeout(Duration.ofSeconds(150));
        if(token!=null) builder.header("Authorization","Bearer "+token);
        if(body==null) builder.method(method,HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type","application/json").method(method,
                HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        long start = System.nanoTime();
        var response = HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofString());
        return new ApiResult(response.statusCode(),mapper.readTree(response.body()),(System.nanoTime()-start)/1_000_000);
    }
    String newUser(String prefix) throws Exception {
        String account = "eval"+prefix+UUID.randomUUID().toString().replace("-","").substring(0,12);
        String password = "Ev!"+UUID.randomUUID();
        assertThat(api("POST","/api/auth/register",null,Map.of("account",account,"password",password,
                "username","Baseline "+prefix,"email",account+"@example.invalid")).httpStatus()).isEqualTo(200);
        var login = api("POST","/api/auth/login",null,Map.of("account",account,"password",password));
        assertThat(login.httpStatus()).isEqualTo(200);
        return login.body().path("data").path("accessToken").asText();
    }

    void pipeline() throws Exception {
        String token = newUser("corpus");
        var me = api("GET","/api/users/me",token,null).body().path("data");
        long userId = me.path("id").asLong(me.path("userId").asLong());
        assertThat(userId).isPositive();
        assertThat(db().update("UPDATE user SET role='ADMIN' WHERE id=?",userId)).isEqualTo(1);
        List<Object> created = new ArrayList<>();
        for(JsonNode document:read("pipeline-documents.json")) {
            String boundary = "eval"+UUID.randomUUID();
            String text = "--"+boundary+"\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\n"
                    +document.path("title").asText()+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"category\"\r\n\r\n"
                    +document.path("category").asText()+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"source\"\r\n\r\n"
                    +"baseline-evaluation-20261004\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"baseline.md\"\r\nContent-Type: text/markdown\r\n\r\n"
                    +document.path("content").asText()+"\r\n--"+boundary+"--\r\n";
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/admin/knowledge/documents"))
                    .header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary)
                    .POST(HttpRequest.BodyPublishers.ofString(text,StandardCharsets.UTF_8)).build();
            var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var upload=mapper.readTree(response.body());
            long id=upload.path("data").path("documentId").asLong(upload.path("data").path("id").asLong());
            assertThat(id).isPositive();
            var processed=api("POST","/api/admin/knowledge/documents/"+id+"/process",token,null);
            assertThat(processed.httpStatus()).isEqualTo(200);
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("fixtureKey",document.path("key").asText());
            row.put("documentId",id);
            row.put("upload",upload);
            row.put("process",processed);
            row.put("document",db().queryForMap("SELECT id,title,category,content,document_version,processing_status FROM knowledge_document WHERE id=?",id));
            var chunks=db().queryForList("SELECT id,document_id,document_version,chunk_index,content,token_count,vector_id,embedding_model,embedding_version,status FROM knowledge_chunk WHERE document_id=? ORDER BY chunk_index",id);
            row.put("chunks",chunks);
            row.put("chunkerDrafts",chunker.split(document.path("content").asText()));
            assertThat(chunks).hasSize(chunker.split(document.path("content").asText()).size());
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(()-> {
                assertThat(retrieval.retrieve(document.path("verificationQuery").asText(),5).items())
                        .anyMatch(item->item.documentId()==id);
            });
            row.put("verificationRetrieval",retrieval.retrieve(document.path("verificationQuery").asText(),5));
            created.add(row);
            write("pipeline.json",created);
            System.out.println("BASELINE_PIPELINE_DOCUMENT="+id+" CHUNKS="+chunks.size());
        }
        // Toggle only this run's first document, always restore in finally. Never mutate shared corpus.
        @SuppressWarnings("unchecked") long id=((Number)((Map<String,Object>)created.getFirst()).get("documentId")).longValue();
        String query=read("pipeline-documents.json").get(0).path("verificationQuery").asText();
        List<Object> filters=new ArrayList<>();
        List<String[]> mutations=List.of(
                new String[]{"chunk_inactive","UPDATE knowledge_chunk SET status='INACTIVE' WHERE document_id=?","UPDATE knowledge_chunk SET status='ACTIVE' WHERE document_id=?"},
                new String[]{"document_not_ready","UPDATE knowledge_document SET processing_status='FAILED' WHERE id=?","UPDATE knowledge_document SET processing_status='READY' WHERE id=?"},
                new String[]{"document_version_mismatch","UPDATE knowledge_document SET document_version=2 WHERE id=?","UPDATE knowledge_document SET document_version=1 WHERE id=?"},
                new String[]{"embedding_model_mismatch","UPDATE knowledge_chunk SET embedding_model='evaluation-mismatch' WHERE document_id=?","UPDATE knowledge_chunk SET embedding_model='"+embeddingProperties.getModel()+"' WHERE document_id=?"},
                new String[]{"embedding_version_mismatch","UPDATE knowledge_chunk SET embedding_version='evaluation-mismatch' WHERE document_id=?","UPDATE knowledge_chunk SET embedding_version='"+embeddingProperties.getProfileVersion()+"' WHERE document_id=?"}
        );
        for(String[] mutation:mutations) {
            try {
                db().update(mutation[1],id);
                var result=retrieval.retrieve(query,5);
                assertThat(result.rawHits()).anyMatch(hit->hit.documentId()==id);
                assertThat(result.items()).noneMatch(item->item.documentId()==id);
                filters.add(Map.of("case",mutation[0],"result",result));
            } finally { db().update(mutation[2],id); }
        }
        write("metadata-filters.json",filters);
    }

    void e2e() throws Exception {
        JsonNode answerSets=read("e2e-answers.json");
        List<Object> sessions=new ArrayList<>();
        for(String quality:List.of("excellent","medium","poor")) {
            String token=newUser(quality);
            var creation=api("POST","/api/interviews",token,Map.of("difficulty","EASY"));
            assertThat(creation.httpStatus()).isEqualTo(200);
            long sessionId=creation.body().path("data").path("sessionId").asLong();
            JsonNode current=creation.body().path("data").path("currentQuestion");
            Map<String,Object> session=new LinkedHashMap<>();
            session.put("quality",quality);session.put("sessionId",sessionId);session.put("creation",creation);
            session.put("configuredProvider",environment.getProperty("ai.judge.provider"));
            List<Object> turns=new ArrayList<>();session.put("turns",turns);sessions.add(session);
            if(quality.equals("excellent")) {
                long questionId=current.path("interviewQuestionId").asLong();
                session.put("emptyAnswer",api("POST","/api/interviews/"+sessionId+"/answers",token,
                        Map.of("interviewQuestionId",questionId,"answerContent","","requestId",UUID.randomUUID().toString())));
                session.put("oversizedAnswer",api("POST","/api/interviews/"+sessionId+"/answers",token,
                        Map.of("interviewQuestionId",questionId,"answerContent","a".repeat(65536),"requestId",UUID.randomUUID().toString())));
                assertThat(db().queryForObject("SELECT COUNT(*) FROM interview_answer a JOIN interview_question q ON q.id=a.interview_question_id WHERE q.session_id=?",Integer.class,sessionId)).isZero();
            }
            for(int turn=0;turn<11 && !current.isNull() && !current.isMissingNode();turn++) {
                long questionId=current.path("interviewQuestionId").asLong();
                var snapshot=db().queryForMap("SELECT id,question_type,parent_question_id,question_id,category,question_content,reference_answer_snapshot,scoring_points_snapshot,follow_up_target_points FROM interview_question WHERE id=?",questionId);
                long mainBankId;
                if("FOLLOW_UP".equals(snapshot.get("question_type"))) {
                    mainBankId=db().queryForObject("SELECT question_id FROM interview_question WHERE id=?",Long.class,snapshot.get("parent_question_id"));
                } else mainBankId=((Number)snapshot.get("question_id")).longValue();
                String answer=answerSets.path(Long.toString(mainBankId)).path(quality).asText();
                assertThat(answer).isNotBlank();
                String requestId=UUID.randomUUID().toString();
                Map<String,Object> body=Map.of("interviewQuestionId",questionId,"answerContent",answer,"requestId",requestId);
                var response=api("POST","/api/interviews/"+sessionId+"/answers",token,body);
                Map<String,Object> record=new LinkedHashMap<>();
                record.put("question",snapshot);record.put("answer",answer);record.put("response",response);
                var evaluationRows=db().queryForList("SELECT ae.* FROM answer_evaluation ae JOIN interview_answer a ON a.id=ae.answer_id WHERE a.interview_question_id=?",questionId);
                record.put("evaluationRows",evaluationRows);
                record.put("ragBatches",db().queryForList("SELECT rb.* FROM rag_retrieval_batch rb JOIN interview_answer a ON a.id=rb.answer_id WHERE a.interview_question_id=?",questionId));
                record.put("ragHits",db().queryForList("SELECT rh.* FROM rag_hit_log rh JOIN interview_answer a ON a.id=rh.answer_id WHERE a.interview_question_id=? ORDER BY rh.rank_no",questionId));
                turns.add(record);write("e2e.json",sessions);
                System.out.println("BASELINE_E2E_SESSION="+sessionId+" TURN="+turn+" HTTP="+response.httpStatus());
                if(response.httpStatus()!=200) {
                    // Exactly one diagnostic replay, preserving the failed attempt and original request.
                    // This exercises the production FAILED retry path; it is not counted as first-attempt success.
                    var retry=api("POST","/api/interviews/"+sessionId+"/answers",token,body);
                    record.put("diagnosticRetry",retry);
                    record.put("retryEvaluationRows",db().queryForList("SELECT ae.* FROM answer_evaluation ae JOIN interview_answer a ON a.id=ae.answer_id WHERE a.interview_question_id=?",questionId));
                    session.put("hadFailure",true);
                    if(retry.httpStatus()!=200) {
                        session.put("status","FAILED_HTTP_AFTER_DIAGNOSTIC_RETRY");write("e2e.json",sessions);break;
                    }
                    response=retry;
                    evaluationRows=db().queryForList("SELECT ae.* FROM answer_evaluation ae JOIN interview_answer a ON a.id=ae.answer_id WHERE a.interview_question_id=?",questionId);
                }
                assertThat(evaluationRows).hasSize(1);
                if(turn==0) {
                    int before=db().queryForObject("SELECT COUNT(*) FROM rag_retrieval_batch",Integer.class);
                    record.put("idempotentReplay",api("POST","/api/interviews/"+sessionId+"/answers",token,body));
                    assertThat(db().queryForObject("SELECT COUNT(*) FROM rag_retrieval_batch",Integer.class)).isEqualTo(before);
                }
                current=response.body().path("data").path("nextQuestion");
                if("COMPLETED".equals(response.body().path("data").path("sessionStatus").asText())) {
                    var report=api("POST","/api/interviews/"+sessionId+"/report",token,null);
                    session.put("report",report);
                    session.put("detail",api("GET","/api/interviews/"+sessionId,token,null));
                    var finals=db().queryForList("SELECT ae.* FROM answer_evaluation ae JOIN interview_question q ON q.id=ae.main_interview_question_id WHERE q.session_id=? AND ae.evaluation_phase='FINAL' ORDER BY q.plan_order",sessionId);
                    session.put("finals",finals);
                    assertThat(finals).hasSize(5);
                    BigDecimal average=BigDecimal.valueOf(finals.stream().mapToInt(x->((Number)x.get("total_score")).intValue()).sum())
                            .divide(BigDecimal.valueOf(5),2,RoundingMode.HALF_UP);
                    session.put("expectedReportScore",average);
                    if(report.httpStatus()==200) {
                        assertThat(report.body().path("data").path("overallScore").decimalValue()).isEqualByComparingTo(average);
                        session.put("status","COMPLETED_REPORT_CONSISTENT");
                    } else session.put("status","REPORT_FAILED");
                    write("e2e.json",sessions);break;
                }
            }
            write("e2e.json",sessions);
        }
    }

    void recovery() throws Exception {
        // Only recover IDs recorded by this harness. No shared user/session mutation.
        JsonNode previous=mapper.readTree(Path.of("docs/evaluation/evidence/e2e.json").toFile());
        List<Object> recovered=new ArrayList<>();
        for(JsonNode session:previous) {
            if(!session.path("status").asText().equals("REPORT_FAILED")) continue;
            long id=session.path("sessionId").asLong();
            var user=db().queryForMap("SELECT u.id,u.account,u.role,u.password FROM user u JOIN interview_session s ON s.user_id=u.id WHERE s.id=?",id);
            assertThat((String)user.get("account")).startsWith("eval");
            // Real signer + real DB-backed JWT filter; no stored tokens/passwords in evidence.
            String token=jwtTokens.generateAccessToken(((Number)user.get("id")).longValue(),
                    (String)user.get("account"),UserRole.valueOf((String)user.get("role")),(String)user.get("password"));
            var response=api("POST","/api/interviews/"+id+"/report",token,null);
            var states=db().queryForMap("SELECT status,report_status,total_score FROM interview_session WHERE id=?",id);
            recovered.add(Map.of("sessionId",id,"kind","one diagnostic real report retry; test-issued JWT", "response",response,"state",states));
            write("recovery.json",recovered);
            System.out.println("BASELINE_RECOVERY_SESSION="+id+" HTTP="+response.httpStatus());
        }
        // Preserve first failed answer evidence; one unchanged-input retry is diagnostic only.
        JsonNode first=mapper.readTree(Path.of("docs/evaluation/evidence/e2e-first.json").toFile());
        JsonNode failed=first.get(first.size()-1);
        long sessionId=failed.path("sessionId").asLong();
        var user=db().queryForMap("SELECT u.id,u.account,u.role,u.password FROM user u JOIN interview_session s ON s.user_id=u.id WHERE s.id=?",sessionId);
        assertThat((String)user.get("account")).startsWith("eval");
        var answers=db().queryForList("SELECT a.id,a.interview_question_id,a.request_id,a.answer_content,a.status FROM interview_answer a JOIN interview_question q ON q.id=a.interview_question_id WHERE q.session_id=? AND a.status='FAILED'",sessionId);
        for(var answer:answers) {
            String token=jwtTokens.generateAccessToken(((Number)user.get("id")).longValue(),
                    (String)user.get("account"),UserRole.valueOf((String)user.get("role")),(String)user.get("password"));
            var response=api("POST","/api/interviews/"+sessionId+"/answers",token,
                    Map.of("interviewQuestionId",answer.get("interview_question_id"),"requestId",answer.get("request_id"),"answerContent",answer.get("answer_content")));
            recovered.add(Map.of("sessionId",sessionId,"kind","one diagnostic FAILED-answer retry; test-issued JWT",
                    "response",response,"answerBefore",answer,"answerAfter",db().queryForMap("SELECT id,status,error_code FROM interview_answer WHERE id=?",answer.get("id"))));
            write("recovery.json",recovered);
            System.out.println("BASELINE_RECOVERY_ANSWER="+answer.get("id")+" HTTP="+response.httpStatus());
        }
        write("post-run-corpus.json",db().queryForList("SELECT id,title,document_version,processing_status FROM knowledge_document ORDER BY id"));
    }

    void write(String name, Object value) throws Exception {
        Path directory = Path.of("docs/evaluation/evidence");
        Files.createDirectories(directory);
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve(name).toFile(), value);
    }
}
