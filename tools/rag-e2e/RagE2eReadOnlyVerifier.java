import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.kun.aiinterview.common.response.Result;
import com.kun.aiinterview.interview.entity.*;
import com.kun.aiinterview.interview.enums.*;
import com.kun.aiinterview.interview.evaluation.*;
import com.kun.aiinterview.interview.evaluation.decision.*;
import com.kun.aiinterview.interview.evaluation.llm.*;
import com.kun.aiinterview.interview.evaluation.prompt.*;
import com.kun.aiinterview.interview.evaluation.score.*;
import com.kun.aiinterview.interview.evaluation.standard.*;
import com.kun.aiinterview.interview.evaluation.validation.*;
import com.kun.aiinterview.interview.mapper.*;
import com.kun.aiinterview.interview.service.*;
import com.kun.aiinterview.interview.vo.*;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;

/** Replay only preserved data through pure Java rules. Never boots Spring or constructs an external client. */
public final class RagE2eReadOnlyVerifier {
    static final ObjectMapper JSON=Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    static final ObjectMapper DB=JSON.copy().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    static final List<String> ROOT_ORDER=List.of("correctnessScore","completenessScore","depthScore",
            "clarityScore","practiceScore","strengths","missingPoints","correction","scoringPointResults",
            "followUpRecommended","suggestedFollowUp");
    static void require(boolean value,String code) { RagE2ePolicy.require(value,code); }
    static JsonNode modelEvidence(JsonNode report,String provider) {
        for(var e:report.path("modelHttp"))if(provider.equals(e.path("provider").asText()))return e;
        throw new SecurityException("MODEL_EVIDENCE_MISSING");
    }
    static JsonNode reorder(JsonNode input,List<String> order) {
        var result=JSON.createObjectNode();
        for(String key:order)if(input.has(key))result.set(key,input.get(key));
        input.fields().forEachRemaining(e->{if(!result.has(e.getKey()))result.set(e.getKey(),e.getValue());});
        return result;
    }
    static String spaced(JsonNode n) throws Exception {
        if(n.isObject()) {
            var fields=new ArrayList<String>();
            for(var it=n.fields();it.hasNext();) {
                var e=it.next();fields.add(JSON.writeValueAsString(e.getKey())+": "+spaced(e.getValue()));
            }
            return "{"+String.join(", ",fields)+"}";
        }
        if(n.isArray()) {
            var values=new ArrayList<String>();for(var v:n)values.add(spaced(v));
            return "["+String.join(", ",values)+"]";
        }
        return JSON.writeValueAsString(n);
    }
    static String recoverProviderContent(String stored,String recordedSha) throws Exception {
        // One deterministic presentation, with ALL fields retained. It is accepted only by the original SHA.
        // This is a reconstruction witness, never relabelled as a retained HTTP response.
        var reordered=reorder(RagE2eConsistency.object(stored),ROOT_ORDER);
        var points=JSON.createArrayNode();
        for(var p:reordered.path("scoringPointResults"))points.add(reorder(p,List.of("scoringPointId","covered","evidence")));
        ((com.fasterxml.jackson.databind.node.ObjectNode)reordered).set("scoringPointResults",points);
        String candidate=spaced(reordered);
        require(RagE2ePolicy.hash(candidate).equals(recordedSha),"ORIGINAL_CONTENT_CANNOT_BE_RECOVERED_NO_NEW_CALL");
        return candidate;
    }
    static JsonNode one(JsonNode snapshot,String key) {
        var rows=snapshot.path(key);require(rows.isArray()&&rows.size()==1,"EXACTLY_ONE_"+key.toUpperCase()+"_REQUIRED");
        return rows.get(0);
    }
    static <T> T entity(JsonNode row,Class<T> type) {
        var copy=row.deepCopy();
        row.fields().forEachRemaining(e->{
            if(e.getKey().endsWith("_at")&&!e.getValue().isNull())
                ((com.fasterxml.jackson.databind.node.ObjectNode)copy).put(e.getKey(),e.getValue().asText().replace(' ','T'));
        });
        if(copy.has("follow_up_recommended"))((com.fasterxml.jackson.databind.node.ObjectNode)copy)
                .put("follow_up_recommended","1".equals(copy.path("follow_up_recommended").asText()));
        return DB.convertValue(copy,type);
    }
    static <T> T port(Class<T> type,Map<String,java.util.function.Function<Object[],Object>> methods) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
            var fn=methods.get(m.getName());require(fn!=null,"REPLAY_PORT_CALL_FORBIDDEN_"+m.getName());return fn.apply(a);
        }));
    }
    static void matchesHttp(JsonNode original,String method,String path,Object data) throws Exception {
        String sha=RagE2ePolicy.hash(JSON.writeValueAsBytes(Result.success(data)));
        for(var h:original.path("businessHttp"))if(method.equals(h.path("method").asText())&&path.equals(h.path("path").asText())) {
            require(h.path("httpStatus").asInt()==200&&sha.equals(h.path("responseSha256").asText()),"ORIGINAL_"+method+"_RESPONSE_HASH_MISMATCH");return;
        }
        throw new SecurityException("ORIGINAL_HTTP_EVIDENCE_MISSING");
    }
    public static void main(String[] args) throws Exception {
        Path snapshotPath=Path.of(args[0]),output=Path.of(args[1]);
        Path reportPath=Path.of("target/rag-e2e/attempts/"+RagE2eRunner.RECOVERY_RUN_ID+"/report.json");
        var original=JSON.readTree(reportPath.toFile());var snapshot=JSON.readTree(snapshotPath.toFile());
        require(RagE2eRunner.RECOVERY_RUN_ID.equals(original.path("runId").asText())
                && "RAW_MODEL_TO_DB_MISMATCH".equals(original.path("failureCode").asText()),"EXACT_PRIOR_FAILURE_REQUIRED");
        var seal=JSON.readTree(Path.of("target/rag-e2e/review-seal.json").toFile());int sourceFiles=0;
        for(var it=seal.fields();it.hasNext();) {
            var e=it.next();if(e.getKey().startsWith("src/main/")) {
                require(RagE2ePolicy.hash(Files.readAllBytes(Path.of(e.getKey()))).equals(e.getValue().asText()),"PRODUCTION_SOURCE_CHANGED");sourceFiles++;
            }
        }
        require(sourceFiles>0,"PRODUCTION_SEAL_REQUIRED");
        require("ai_interview_test".equals(snapshot.path("identity").path("db").asText())
                && "1".equals(snapshot.path("identity").path("read_only").asText()),"READ_ONLY_TEST_SNAPSHOT_REQUIRED");
        var session=entity(one(snapshot,"sessions"),InterviewSession.class);
        var answer=entity(one(snapshot,"answers"),InterviewAnswer.class);
        var evaluation=entity(one(snapshot,"evaluations"),AnswerEvaluation.class);
        var batch=one(snapshot,"batches");var identifiers=original.path("businessIdentifiers");
        require(session.getId()==139L&&session.getUserId()==361L&&answer.getInterviewQuestionId()==133L
                && evaluation.getAnswerId().equals(answer.getId())&&evaluation.getMainInterviewQuestionId()==133L
                && answer.getRequestId().equals(identifiers.path("requestId").asText())
                && RagE2ePolicy.hash(answer.getAnswerContent()).equals(identifiers.path("answerSha256").asText()),"RUN_IDENTITIES_MISMATCH");
        require(answer.getStatus()==InterviewAnswerStatus.EVALUATED&&answer.getErrorCode()==null,"EXISTING_ANSWER_NOT_EVALUATED");
        var questions=new ArrayList<InterviewQuestion>();for(var row:snapshot.path("questions"))questions.add(entity(row,InterviewQuestion.class));
        var questionMap=new HashMap<Long,InterviewQuestion>();questions.forEach(q->questionMap.put(q.getId(),q));
        require(questions.size()==5&&questions.stream().allMatch(q->q.getQuestionType()==InterviewQuestionType.MAIN),"ORIGINAL_MAIN_PLAN_MISMATCH");
        var questionPort=port(InterviewQuestionMapper.class,Map.of(
                "getInterviewQuestionById",a->questionMap.get(a[0]),"listBySessionId",a->questions));
        var answerPort=port(InterviewAnswerMapper.class,Map.of(
                "getInterviewAnswerById",a->answer.getId().equals(a[0])?answer:null,"listByInterviewQuestionIds",a->List.of(answer)));
        var context=new EvaluationContextService(answerPort,questionPort,JSON).buildContext(answer.getId());
        require(batch.path("answer_id").asLong()==answer.getId()&&evaluation.getRetrievalBatchId().equals(batch.path("retrieval_batch_id").asText()),"TRACE_EVALUATION_LINK_MISMATCH");
        String query=new EvaluationRagQueryBuilder().build(context);
        require(query.equals(batch.path("query_text").asText())
                && RagE2ePolicy.hash(query).equals(modelEvidence(original,"bailian").path("querySha256").asText()),"ORIGINAL_EMBEDDING_QUERY_MISMATCH");
        var evidence=new ArrayList<RetrievedChunk>();var evidenceReport=new ArrayList<Map<String,Object>>();
        for(var hit:snapshot.path("hits")) {
            require(hit.path("answer_id").asLong()==answer.getId()&&hit.path("retrieval_batch_id").asText().equals(evaluation.getRetrievalBatchId())
                    && hit.path("query_text").asText().equals(query)&&"ACTIVE".equals(hit.path("chunk_status").asText())
                    &&"READY".equals(hit.path("processing_status").asText())
                    &&hit.path("document_version").asText().equals(hit.path("current_document_version").asText())
                    &&RagE2ePolicy.MODEL.equals(hit.path("chunk_embedding_model").asText())
                    &&RagE2ePolicy.VERSION.equals(hit.path("chunk_embedding_version").asText()),"TRACE_HYDRATION_INVALID");
            double cosine=Double.parseDouble(hit.path("similarity_score").asText());require(cosine>=0.70,"TRACE_BELOW_THRESHOLD");
            var chunk=new RetrievedChunk(hit.path("rank_no").asInt(),cosine,hit.path("chunk_id").asLong(),hit.path("document_id").asLong(),
                    hit.path("document_version").asInt(),hit.path("chunk_index").asInt(),hit.path("vector_id").asText(),
                    hit.path("title").asText(),KnowledgeCategory.valueOf(hit.path("category").asText()),
                    hit.path("source").isNull()?null:hit.path("source").asText(),hit.path("content").asText());
            evidence.add(chunk);evidenceReport.add(Map.of("chunkId",chunk.chunkId(),"documentId",chunk.documentId(),
                    "vectorId",chunk.vectorId(),"rank",chunk.vectorRank(),"persistedCosine",cosine,
                    "contentSha256",RagE2ePolicy.hash(chunk.content())));
        }
        require(evidence.size()==batch.path("evidence_hit_count").asInt()&&!evidence.isEmpty()&&batch.path("top_k").asInt()==5,"TRACE_COUNTS_MISMATCH");
        var filter=JSON.readTree(batch.path("filter_snapshot").asText()).path("cosineCandidate");
        require(filter.path("enabled").asBoolean()&&filter.path("minScore").asDouble()==0.70
                &&"COSINE".equals(filter.path("metric").asText()),"TRACE_FILTER_MISMATCH");
        // Raw hits were not persisted. An empty rawHits list is deliberate; no synthetic search records.
        var retrieval=new EvaluationRetrievalResult(query,5,batch.path("embedding_model").asText(),batch.path("embedding_version").asText(),List.of(),evidence);
        var prompt=new EvaluationPromptBuilder().build(context,retrieval);var wire=modelEvidence(original,"deepseek");
        require(RagE2ePolicy.hash(prompt.userPrompt()).equals(wire.path("userPromptSha256").asText())
                &&RagE2ePolicy.hash(prompt.systemPrompt()).equals(wire.path("systemPromptSha256").asText()),"ORIGINAL_WIRE_PROMPT_HASH_MISMATCH");
        String recovered=recoverProviderContent(evaluation.getRawResult(),wire.path("rawEvaluationSha256").asText());
        List<Map<String,Object>> modelHttp=JSON.convertValue(original.path("modelHttp"),new TypeReference<>(){});
        RagE2eConsistency.verifyModel(evaluation.getRawResult(),recovered,evaluation.getLlmModel(),wire.path("responseModel").asText(),modelHttp);
        var parsed=JSON.readValue(recovered,LlmEvaluationSuggestion.class);
        var validated=new LlmEvaluationValidator().validate(context,parsed);
        var score=new EvaluationScoreCalculator().calculate(validated);
        var decision=new FollowUpPolicy(new FollowUpTargetResolver()).decide(context,validated,score,true);
        require(RagE2eConsistency.equivalent(JSON.readTree(evaluation.getScoringPointResults()),JSON.valueToTree(validated.scoringPointResults()))
                &&RagE2eConsistency.equivalent(JSON.readTree(evaluation.getStrengths()),JSON.valueToTree(validated.strengths()))
                &&RagE2eConsistency.equivalent(JSON.readTree(evaluation.getMissingPoints()),JSON.valueToTree(validated.missingPoints()))
                &&Objects.equals(evaluation.getCorrection(),validated.correction())
                &&Objects.equals(evaluation.getSuggestedFollowUp(),validated.suggestedFollowUp())
                &&evaluation.getFollowUpRecommended()==validated.followUpRecommended(),"VALIDATED_FIELDS_TO_DB_MISMATCH");
        require(evaluation.getCorrectnessScore()==score.correctnessScore()&&evaluation.getCompletenessScore()==score.completenessScore()
                &&evaluation.getDepthScore()==score.depthScore()&&evaluation.getClarityScore()==score.clarityScore()
                &&evaluation.getPracticeScore()==score.practiceScore()&&evaluation.getTotalScore()==score.totalScore()
                &&evaluation.getLevel().equals(new EvaluationStandard().resolveLevel(score.totalScore()).name())
                &&evaluation.getEvaluationPhase()==decision.evaluationPhase()&&evaluation.getDecisionAction()==decision.decisionAction(),"JAVA_SCORE_OR_DECISION_MISMATCH");
        require(evaluation.getPromptVersion().equals(prompt.promptVersion())
                &&evaluation.getEvaluationStandardVersion().equals(EvaluationStandard.VERSION),"EVALUATION_VERSION_MISMATCH");
        var sessionPort=port(InterviewSessionMapper.class,Map.of("getInterviewSessionById",a->session));
        var evaluationPort=port(AnswerEvaluationMapper.class,Map.of("listByAnswerIds",a->List.of(evaluation)));
        var service=new InterviewService(null,sessionPort,questionPort,answerPort,null,evaluationPort,null,JSON);
        var detail=service.getDetail(361L,139L);
        var detailEvaluation=detail.questions().stream().filter(q->q.interviewQuestionId()==133L).findFirst().orElseThrow().evaluation();
        var next=questionMap.get(session.getCurrentInterviewQuestionId());
        var submit=new SubmitInterviewAnswerResponse(detailEvaluation,evaluation.getDecisionAction(),session.getStatus(),
                new InterviewQuestionResponse(next.getId(),next.getCategory(),next.getKnowledgePoint(),next.getQuestionType(),next.getQuestionContent()));
        matchesHttp(original,"POST","/api/interviews/139/answers",submit);
        matchesHttp(original,"GET","/api/interviews/139",detail);
        require(session.getStatus()==InterviewSessionStatus.IN_PROGRESS&&session.getReportStatus()==InterviewReportStatus.NOT_STARTED
                &&session.getCompletedQuestionCount()==1&&session.getCurrentInterviewQuestionId()==134L
                &&next.getStatus()==InterviewQuestionStatus.WAITING_ANSWER&&questionMap.get(133L).getStatus()==InterviewQuestionStatus.ANSWERED
                &&snapshot.path("reports").isEmpty()&&snapshot.path("weaknesses").isEmpty(),"WORKFLOW_OR_REPORT_STATE_MISMATCH");
        require(snapshot.path("all52ChunksEligible").asBoolean()
                &&original.path("testVectors").path("mappingVerified").asBoolean()
                &&original.path("testVectors").path("entities").asInt()==52
                &&"REUSED_FROZEN_MATCH".equals(original.path("testCorpus").path("status").asText()),"COMPLETE_ELIGIBLE_FROZEN_CORPUS_REQUIRED");
        var digests=snapshot.path("hydrationDigestSummary");
        require(digests.size()==1&&digests.get(0).path("COUNT_STAR").asInt()==1
                &&digests.get(0).path("SUM_ROWS_SENT").asInt()==evidence.size(),"ACTUAL_ONE_HYDRATION_DIGEST_REQUIRED");
        var runStart=java.time.Instant.parse(original.path("startedUtc").asText()).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        var runEnd=java.time.Instant.parse(original.path("endedUtc").asText()).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        var hydrationTime=java.time.LocalDateTime.parse(digests.get(0).path("FIRST_SEEN").asText().replace(' ','T'));
        require(!hydrationTime.isBefore(runStart)&&!hydrationTime.isAfter(runEnd)
                &&digests.get(0).path("FIRST_SEEN").asText().equals(digests.get(0).path("LAST_SEEN").asText()),"HYDRATION_DIGEST_NOT_THIS_RUN");
        var result=new LinkedHashMap<String,Object>();
        result.put("runId",RagE2eRunner.RECOVERY_RUN_ID);result.put("verification","PASS_EXISTING_RUN_BUSINESS_CONTRACT");
        result.put("originalReportUnchanged",true);result.put("originalReportSha256",RagE2ePolicy.hash(Files.readAllBytes(reportPath)));
        result.put("snapshotSha256",RagE2ePolicy.hash(Files.readAllBytes(snapshotPath)));result.put("productionSourceSealFilesMatched",sourceFiles);
        result.put("productionBugOrVerifierBug","VERIFIER_BUG_MYSQL_JSON_REPRESENTATION");
        result.put("model",Map.of("responseEnvelopeSha256",wire.path("responseSha256").asText(),"responseEnvelopeBodyRetained",false,
                "providerContentSha256",RagE2ePolicy.hash(recovered),"providerContentRecovery","DETERMINISTIC_PRESENTATION_MATCHED_ORIGINAL_SHA256",
                "databaseJsonTextSha256",RagE2ePolicy.hash(evaluation.getRawResult()),"fullJsonEquivalent",true));
        result.put("evaluation",Map.of("answerId",answer.getId(),"evaluationId",evaluation.getId(),"answerStatus",answer.getStatus(),
                "phase",decision.evaluationPhase(),"score",score,"level",evaluation.getLevel(),"decision",decision.decisionAction(),
                "llmFollowUpRecommended",validated.followUpRecommended(),"followUpTargets",decision.followUpTargetPointIds()));
        result.put("retrieval",Map.of("rawHitCount",batch.path("raw_hit_count").asInt(),"rawHitDetailsRetained",false,
                "sqlHydratedCount",evidence.size(),"promptEvidenceCount",evidence.size(),"knowledge",evidenceReport,
                "batchId",evaluation.getRetrievalBatchId(),"userPromptSha256",RagE2ePolicy.hash(prompt.userPrompt()),
                "systemPromptSha256",RagE2ePolicy.hash(prompt.systemPrompt()),"querySha256",RagE2ePolicy.hash(query)));
        result.put("cosineCandidates",Map.of("count",evidence.size(),"countKind","DERIVED_FROM_THIS_RUN_EVIDENCE",
                "derivation","Original run proved all 52 vectors map to frozen, eligible chunks; every distinct threshold candidate must hydrate. The only production hydration query returned 2 rows during this run.",
                "originalRawCandidateListRetained",false,"actualHydrationDigest",digests.get(0)));
        result.put("mysqlJsonNormalization",Map.of("selectCastReproducedExactStoredText",snapshot.path("mysqlCastExactlyEqualsStoredRawResult").asBoolean()));
        require(snapshot.path("mysqlCastExactlyEqualsStoredRawResult").asBoolean(),"MYSQL_JSON_NORMALIZATION_NOT_REPRODUCED");
        result.put("httpDbConsistency",Map.of("originalPostResponseHashMatches",true,"originalGetResponseHashMatches",true,
                "actualProductionGetDetailProjectionUsed",true,"postProjectionSource","SubmitInterviewAnswerResponse contract, confirmed by original byte SHA"));
        result.put("modelCalls",0);result.put("businessHttp",0);result.put("databaseWrites",0);result.put("milvusCalls",0);
        Files.writeString(output,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result),StandardOpenOption.CREATE_NEW);
        System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }
}
