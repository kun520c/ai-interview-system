import com.fasterxml.jackson.databind.*;
import com.kun.aiinterview.interview.entity.*;
import com.kun.aiinterview.interview.enums.*;
import com.kun.aiinterview.interview.evaluation.*;
import com.kun.aiinterview.interview.evaluation.decision.*;
import com.kun.aiinterview.interview.evaluation.llm.*;
import com.kun.aiinterview.interview.evaluation.prompt.*;
import com.kun.aiinterview.interview.evaluation.score.*;
import com.kun.aiinterview.interview.evaluation.validation.*;
import com.kun.aiinterview.interview.mapper.*;
import com.kun.aiinterview.interview.service.*;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import java.nio.file.*;
import java.sql.Connection;
import java.util.*;

/** SELECT-only inspection and pure replay of saved real output; never creates a provider client. */
final class FinalE2eRepair {
    static final Path ORIGINAL=Path.of("target/final-e2e/attempts/final-e2e-20261013-001");
    static final ObjectMapper JSON=RagE2eReadOnlyVerifier.JSON;
    static void check(boolean condition,String code) { FinalE2eRunner.check(condition,code); }
    static JsonNode read(String file) throws Exception { return JSON.readTree(ORIGINAL.resolve(file).toFile()); }
    static JsonNode http(JsonNode original,String method,String path) {
        for(var item:original.path("http")) if(method.equals(item.path("method").asText())&&path.equals(item.path("path").asText())
                && item.path("actualStatus").asInt()==200) return item.path("response").path("data");
        throw new SecurityException("PRESERVED_HTTP_RESPONSE_MISSING");
    }
    static JsonNode detail(JsonNode snapshot) {
        var session=RagE2eReadOnlyVerifier.entity(snapshot.path("session").get(0),InterviewSession.class);
        List<InterviewQuestion> questions=new ArrayList<>(); snapshot.path("questions").forEach(row->questions.add(RagE2eReadOnlyVerifier.entity(row,InterviewQuestion.class)));
        List<InterviewAnswer> answers=new ArrayList<>(); snapshot.path("answers").forEach(row->answers.add(RagE2eReadOnlyVerifier.entity(row,InterviewAnswer.class)));
        List<AnswerEvaluation> evaluations=new ArrayList<>(); snapshot.path("evaluations").forEach(row->evaluations.add(RagE2eReadOnlyVerifier.entity(row,AnswerEvaluation.class)));
        var sessions=RagE2eReadOnlyVerifier.port(InterviewSessionMapper.class,Map.of("getInterviewSessionById",a->session));
        var q=RagE2eReadOnlyVerifier.port(InterviewQuestionMapper.class,Map.of("listBySessionId",a->questions));
        var a=RagE2eReadOnlyVerifier.port(InterviewAnswerMapper.class,Map.of("listByInterviewQuestionIds",ids->answers));
        var e=RagE2eReadOnlyVerifier.port(AnswerEvaluationMapper.class,Map.of("listByAnswerIds",ids->evaluations));
        var service=new InterviewService(null,sessions,q,a,null,e,null,JSON);
        return FinalE2eRunner.httpProjection(JSON,service.getDetail(427L,150L));
    }
    static List<String> differences(JsonNode left,JsonNode right,String path) {
        List<String> differences=new ArrayList<>();
        if(RagE2eConsistency.equivalent(left,right)) return differences;
        if(left!=null&&right!=null&&left.isObject()&&right.isObject()) {
            Set<String> fields=new LinkedHashSet<>(); left.fieldNames().forEachRemaining(fields::add);right.fieldNames().forEachRemaining(fields::add);
            for(String field:fields) differences.addAll(differences(left.get(field),right.get(field),path+"/"+field));
        } else if(left!=null&&right!=null&&left.isArray()&&right.isArray()&&left.size()==right.size()) {
            for(int i=0;i<left.size();i++) differences.addAll(differences(left.get(i),right.get(i),path+"/"+i));
        } else differences.add(path);
        return differences;
    }
    static Map<String,Object> replay() throws Exception {
        var original=read("report.json");var snapshot=read("99-final-or-failure.database.json");
        var expected=http(original,"GET","/api/interviews/150");var projection=detail(snapshot);
        // Convert actual production DTO JSON back to a DTO to reproduce the old runner's mapper behavior.
        var dto=JSON.treeToValue(projection,com.kun.aiinterview.interview.vo.InterviewDetailResponse.class);
        var wrong=new ObjectMapper().findAndRegisterModules().valueToTree(dto);
        var changed=differences(expected,wrong,"");
        check(new HashSet<>(changed).equals(Set.of("/startedAt","/createdAt","/questions/0/answer/submittedAt")),"OLD_FAILURE_REPRODUCTION_MISMATCH");
        check(RagE2eConsistency.equivalent(expected,projection),"FIXED_FULL_DETAIL_PROJECTION_MISMATCH");
        var failure=original.path("failure").path("causeChain").get(0).path("stackTrace");
        check(failure.toString().contains("verifyEvaluation(FinalE2eRunner.java:324)"),"FIRST_FAILURE_STACK_CHANGED");
        for(var provider:read("providers/provider-index.json")) {
            check(RagE2ePolicy.hash(Files.readAllBytes(ORIGINAL.resolve("providers").resolve(provider.path("requestFile").asText())))
                    .equals(provider.path("requestSha256").asText()),"ORIGINAL_REQUEST_HASH_MISMATCH");
            check(RagE2ePolicy.hash(Files.readAllBytes(ORIGINAL.resolve("providers").resolve(provider.path("responseFile").asText())))
                    .equals(provider.path("responseSha256").asText()),"ORIGINAL_RESPONSE_HASH_MISMATCH");
        }
        var response=http(original,"POST","/api/interviews/150/answers");
        check(RagE2eConsistency.equivalent(expected.path("questions").get(0).path("evaluation"),response.path("evaluation")),"PRESERVED_POST_GET_EVALUATION_MISMATCH");
        var evaluation=snapshot.path("evaluations").get(0);
        var wire=read("providers/03-answer-1-deepseek.response.json");
        String raw=wire.path("choices").get(0).path("message").path("content").asText();
        check(RagE2eConsistency.equivalent(JSON.readTree(raw),JSON.readTree(evaluation.path("raw_result").asText())),"SAVED_PROVIDER_RESULT_DB_MISMATCH");
        var jev=read("providers/02-answer-1-jev.response.json");
        check(jev.path("status").asInt()==451,"PRESERVED_JEV_REGION_FAILURE");
        return Map.of("rootCause","RUNNER_HTTP_DATE_SERIALIZATION_MISMATCH","failureCode","HTTP_DETAIL_DB_PROJECTION_MISMATCH",
                "actualThrowSite","FinalE2eRunner.java:324 (first execution)","differentFields",changed,
                "firstModelRequests",3,"firstSuccessfulModelResponses",2,"jevOutcome","HTTP_451_REGION_UNAVAILABLE",
                "fixedFullDetailComparison","PASS","originalAttemptStatus","BLOCKED","newModelRequests",0);
    }
    static Map<String,Object> inspect(Connection c,Path out) throws Exception {
        FinalE2eRunner.output=out; FinalE2eRunner.userId=427;FinalE2eRunner.sessionId=150;
        var preflight=FinalE2eRunner.preflight(c);
        var current=FinalE2eRunner.JSON.valueToTree(FinalE2eRunner.snapshot(c,"current"));
        check(RagE2eConsistency.equivalent(current,read("99-final-or-failure.database.json")),"FIRST_ATTEMPT_BUSINESS_STATE_CHANGED");
        var users=FinalE2eRunner.sql(c,"SELECT id,account,role,status,created_at FROM `user` WHERE id IN (427,428) ORDER BY id");
        check(users.size()==2 && "final_6a0a6fcb4ad04935852b".equals(users.getFirst().get("account"))
                && users.stream().allMatch(u->"USER".equals(u.get("role"))&&"ENABLED".equals(u.get("status"))),"FIRST_TEST_USER_STATE");
        String grantee="CONCAT(QUOTE(SUBSTRING_INDEX(CURRENT_USER(),'@',1)),'@',QUOTE(SUBSTRING_INDEX(CURRENT_USER(),'@',-1)))";
        var permissions=FinalE2eRunner.sql(c,"SELECT 'GLOBAL' AS scope,NULL AS column_name FROM information_schema.USER_PRIVILEGES WHERE PRIVILEGE_TYPE='UPDATE' AND GRANTEE="+grantee
                +" UNION ALL SELECT 'SCHEMA',NULL FROM information_schema.SCHEMA_PRIVILEGES WHERE PRIVILEGE_TYPE='UPDATE' AND TABLE_SCHEMA='ai_interview_test' AND GRANTEE="+grantee
                +" UNION ALL SELECT 'TABLE',NULL FROM information_schema.TABLE_PRIVILEGES WHERE PRIVILEGE_TYPE='UPDATE' AND TABLE_SCHEMA='ai_interview_test' AND TABLE_NAME='user' AND GRANTEE="+grantee
                +" UNION ALL SELECT 'COLUMN',COLUMN_NAME FROM information_schema.COLUMN_PRIVILEGES WHERE PRIVILEGE_TYPE='UPDATE' AND TABLE_SCHEMA='ai_interview_test' AND TABLE_NAME='user' AND GRANTEE="+grantee);
        check(permissions.stream().anyMatch(p->!p.get("scope").equals("COLUMN"))
                ||permissions.stream().map(p->p.get("column_name")).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet())
                .containsAll(Set.of("password","password_changed_at")),"TEST_CREDENTIAL_ROTATION_CAPABILITY");
        verifyFirstEvaluation(current,c);
        return Map.of("status","PASS","preflight",preflight,"users",users,"newRowsInFirstAttempt",17,
                "sessionId",150,"currentQuestionId",178,"answeredMainQuestionId",173,"currentStateMatchesOriginalSnapshot",true,
                "directDatabaseWritesThisTask",0,"newModelRequests",0);
    }
    static void verifyFirstEvaluation(JsonNode snapshot,Connection c) throws Exception {
        var questionMap=new HashMap<Long,InterviewQuestion>();snapshot.path("questions").forEach(row->{var q=RagE2eReadOnlyVerifier.entity(row,InterviewQuestion.class);questionMap.put(q.getId(),q);});
        var answer=RagE2eReadOnlyVerifier.entity(snapshot.path("answers").get(0),InterviewAnswer.class);
        var evaluation=RagE2eReadOnlyVerifier.entity(snapshot.path("evaluations").get(0),AnswerEvaluation.class);
        var answers=RagE2eReadOnlyVerifier.port(InterviewAnswerMapper.class,Map.of("getInterviewAnswerById",a->answer));
        var questions=RagE2eReadOnlyVerifier.port(InterviewQuestionMapper.class,Map.of("getInterviewQuestionById",a->questionMap.get(a[0])));
        var context=new EvaluationContextService(answers,questions,JSON).buildContext(100L);
        String query=new EvaluationRagQueryBuilder().build(context);
        var embedRequest=read("providers/01-answer-1-embedding.request.json");
        check(embedRequest.path("input").get(0).asText().equals(query),"FIRST_REAL_QUERY_MISMATCH");
        var embedded=read("providers/01-answer-1-embedding.response.json");
        check(embedded.path("data").get(0).path("embedding").size()==1024,"FIRST_REAL_EMBEDDING_DIMENSION");
        var hydration=FinalE2eRunner.sql(c,"SELECT h.rank_no,h.similarity_score,c.*,d.title,d.category,d.source,d.processing_status,d.document_version AS current_version "
                +"FROM rag_hit_log h JOIN knowledge_chunk c ON c.id=h.chunk_id JOIN knowledge_document d ON d.id=c.document_id "
                +"WHERE h.answer_id=100 ORDER BY h.rank_no");
        check(hydration.size()==5,"FIRST_REAL_HYDRATION_COUNT");
        List<RetrievedChunk> evidence=new ArrayList<>();List<VectorSearchHit> rawHits=new ArrayList<>();
        for(var row:hydration) {
            check("ACTIVE".equals(row.get("status"))&&"READY".equals(row.get("processing_status"))
                    &&row.get("document_version").equals(row.get("current_version")),"FIRST_EVIDENCE_ELIGIBILITY");
            double cosine=Double.parseDouble(row.get("similarity_score"));
            evidence.add(new RetrievedChunk(Integer.parseInt(row.get("rank_no")),cosine,Long.valueOf(row.get("id")),Long.valueOf(row.get("document_id")),
                    Integer.valueOf(row.get("document_version")),Integer.valueOf(row.get("chunk_index")),row.get("vector_id"),row.get("title"),
                    KnowledgeCategory.valueOf(row.get("category")),row.get("source"),row.get("content")));
            rawHits.add(new VectorSearchHit(row.get("vector_id"),Long.valueOf(row.get("document_id")),Integer.parseInt(row.get("chunk_index")),row.get("embedding_version"),cosine));
        }
        var retrieval=new EvaluationRetrievalResult(query,5,RagE2ePolicy.MODEL,RagE2ePolicy.VERSION,rawHits,evidence);
        var prompt=new EvaluationPromptBuilder().build(context,retrieval);
        var request=read("providers/03-answer-1-deepseek.request.json");
        check(request.path("messages").get(0).path("content").asText().equals(prompt.systemPrompt())
                &&request.path("messages").get(1).path("content").asText().equals(prompt.userPrompt()),"FIRST_REAL_PROMPT_REPLAY_MISMATCH");
        var suggestion=JSON.readValue(evaluation.getRawResult(),LlmEvaluationSuggestion.class);
        var validated=new LlmEvaluationValidator().validate(context,suggestion);
        var score=new EvaluationScoreCalculator().calculate(validated);
        var decision=new FollowUpPolicy(new FollowUpTargetResolver()).decide(context,validated,score,true);
        check(score.totalScore()==7&&score.totalScore()==evaluation.getTotalScore()
                &&decision.decisionAction()==DecisionAction.FOLLOW_UP&&evaluation.getEvaluationPhase()==EvaluationPhase.INITIAL,"FIRST_REAL_JAVA_DECISION_REPLAY");
        check(RagE2eConsistency.equivalent(JSON.readTree(evaluation.getScoringPointResults()),JSON.valueToTree(validated.scoringPointResults())),"FIRST_SCORING_POINT_PERSISTENCE");
        check(snapshot.path("ragBatches").size()==1&&snapshot.path("ragBatches").get(0).path("raw_hit_count").asInt()==5
                &&snapshot.path("ragBatches").get(0).path("evidence_hit_count").asInt()==5,"FIRST_TRACE_COUNTS");
        check(!JSON.readTree(snapshot.path("ragBatches").get(0).path("filter_snapshot").asText()).path("cosineCandidate").path("enabled").asBoolean(),"DEFAULT_RAG_CHANGED");
    }
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[1]);Map<String,Object> result=new LinkedHashMap<>(replay());
        if(args[0].equals("inspect")) try(var c=FinalE2eRunner.connection()) {result.put("databaseInspection",inspect(c,out));}
        else check(args[0].equals("offline"),"REPAIR_MODE_INVALID");
        FinalE2eRunner.save(out.resolve("diagnosis.json"),result);
        System.out.println("FIRST_ATTEMPT_READ_ONLY_REPLAY=PASS; model requests=0; evidence="+out);
    }
}
