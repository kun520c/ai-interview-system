package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.judge.DeepSeekJudgeProvider;
import com.kun.aiinterview.interview.evaluation.judge.JudgeProvider;
import com.kun.aiinterview.interview.evaluation.judge.jev.JevClient;
import com.kun.aiinterview.interview.evaluation.judge.jev.JevJudgeProvider;
import com.kun.aiinterview.interview.evaluation.judge.jev.JevProperties;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationStandard;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import com.kun.aiinterview.interview.model.InterviewReportGenerationResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingVector;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import com.kun.aiinterview.knowledge.vector.milvus.MilvusCollectionInitializer;

import io.milvus.v2.client.MilvusClientV2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Real HTTP/security/business/MyBatis/transactions, with explicitly mocked external ports only. */
@SpringBootTest(properties = {"milvus.enabled=true", "deepseek.enabled=true", "ai.judge.provider=deepseek",
        "deepseek.api-key=offline-test-key", "rag.retrieval.cosine-threshold-enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class FullInterviewBusinessOfflineTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired InterviewService interviewService;
    @MockitoBean EmbeddingClient embedding;
    @MockitoBean VectorStoreClient vectors;
    @MockitoBean JudgeProvider judge;
    @MockitoBean InterviewReportGenerator reportGenerator;
    @MockitoBean MilvusClientV2 milvus;
    @MockitoBean MilvusCollectionInitializer initializer;
    private String token;
    private long userId;
    private final AtomicInteger providerCalls = new AtomicInteger();

    @BeforeEach void prepare() throws Exception {
        String vectorId = createDatabaseFixtures();
        clearInvocations(milvus,initializer);
        providerCalls.set(0);
        when(embedding.embed(any())).thenReturn(new EmbeddingBatchResult("qwen3.7-text-embedding",
                "qwen3.7-text-embedding-1024-dense-v1",1024,List.of(new EmbeddingVector(0,Collections.nCopies(1024,.01F))),0L));
        var chunk=jdbc.queryForMap("SELECT c.vector_id,c.document_id,c.chunk_index,c.embedding_version,c.content "
                +"FROM knowledge_chunk c JOIN knowledge_document d ON d.id=c.document_id "
                +"WHERE c.status='ACTIVE' AND d.processing_status='READY' AND c.document_version=d.document_version "
                +"AND c.embedding_version='qwen3.7-text-embedding-1024-dense-v1' AND c.vector_id=?", vectorId);
        when(vectors.search(any(),anyString(),anyInt())).thenReturn(List.of(new VectorSearchHit(
                chunk.get("vector_id").toString(),((Number)chunk.get("document_id")).longValue(),
                ((Number)chunk.get("chunk_index")).intValue(),chunk.get("embedding_version").toString(),.9)));
        when(judge.evaluate(any(),any())).thenAnswer(invocation -> {
            EvaluationContext context=invocation.getArgument(0);
            EvaluationPrompt prompt=invocation.getArgument(1);
            assertThat(prompt.userPrompt()).contains(chunk.get("content").toString());
            int call=providerCalls.incrementAndGet();
            boolean follow=call==1 && context.mode()==EvaluationMode.MAIN_ANSWER;
            // Keep both the INITIAL and the comprehensive FINAL weak to verify weakness persistence.
            int dimension=call<=2 ? 8 : 18;
            var points=context.scoringPoints().stream().map(point -> new LlmEvaluationSuggestion.ScoringPointResult(
                    point.scoringPointId(),!follow,"Offline fixture evidence")).toList();
            var suggestion=new LlmEvaluationSuggestion(dimension,dimension,dimension,dimension,dimension,
                    List.of("Offline strength"),List.of("Offline gap"),"Offline correction",points,follow,
                    follow ? "请进一步解释核心机制和边界情况。" : null);
            return new JudgeProvider.Result("offline-judge","offline-v1",json.writeValueAsString(suggestion),suggestion);
        });
        when(reportGenerator.generate(any(),any(),any())).thenAnswer(invocation -> {
            BigDecimal score=invocation.getArgument(2);
            return new InterviewReportGenerationResult(new EvaluationStandard().resolveLevel(score).name(),
                    "Offline report summary",List.of("Offline strength"),List.of("Offline weakness"),List.of("Offline suggestion"),
                    "offline-report","offline-report-v1");
        });
        String account="offline_"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        String password="OfflineOnly123!";
        request("POST","/api/auth/register",Map.of("account",account,"username","Offline candidate",
                "email",account+"@example.com","password",password),200);
        var login=request("POST","/api/auth/login",Map.of("account",account,"password",password),200);
        token=login.path("accessToken").asText(); userId=login.path("userId").asLong();
        assertThat(token).isNotBlank(); assertThat(userId).isPositive();
    }

    /** Each test owns its data; the existing test transaction rolls these inserts back. */
    private String createDatabaseFixtures() throws Exception {
        String marker = "public-offline-" + UUID.randomUUID();
        for (String category : List.of("JAVA_BASIC", "JAVA_COLLECTION", "SPRING", "MYSQL", "NETWORK")) {
            String knowledgePoint = marker + "-" + category;
            assertThat(jdbc.update("""
                    INSERT INTO question(category,knowledge_point,difficulty,question_content,reference_answer,status)
                    VALUES (?,?,'EASY',?,?,'ENABLED')
                    """, category, knowledgePoint, "Explain the offline " + category + " mechanism.",
                    "Authored offline reference for " + category)).isEqualTo(1);
            Long questionId = jdbc.queryForObject("SELECT id FROM question WHERE knowledge_point=?",
                    Long.class, knowledgePoint);
            assertThat(jdbc.update("""
                    INSERT INTO question_scoring_point(question_id,content,point_type,weight,status,sort_order)
                    VALUES (?,?,'CORE',100,'ENABLED',1)
                    """, questionId, "Explain the core mechanism and boundary conditions.")).isEqualTo(1);
        }
        String content = "Authored offline knowledge evidence; no live corpus or provider response.";
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.update("""
                INSERT INTO knowledge_document(title,category,file_name,file_type,content,content_hash,
                    document_version,processing_status)
                VALUES (?,'JAVA_BASIC','public-offline.md','MARKDOWN',?,?,1,'READY')
                """, marker, content, hash)).isEqualTo(1);
        Long documentId = jdbc.queryForObject("SELECT id FROM knowledge_document WHERE title=?", Long.class, marker);
        String vectorId = "public-offline-vector-" + UUID.randomUUID();
        assertThat(jdbc.update("""
                INSERT INTO knowledge_chunk(document_id,document_version,chunk_index,content,vector_id,
                    embedding_model,embedding_version,status)
                VALUES (?,1,1,?,?,'qwen3.7-text-embedding','qwen3.7-text-embedding-1024-dense-v1','ACTIVE')
                """, documentId, content, vectorId)).isEqualTo(1);
        return vectorId;
    }

    @Test void completesFiveMainsFollowUpReportAndWeaknessThroughPublicContract() throws Exception {
        var created=request("POST","/api/interviews",Map.of("difficulty","EASY"),200);
        long sessionId=created.path("sessionId").asLong();
        assertThat(created.path("plannedQuestionCount").asInt()).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_question WHERE session_id=? AND question_type='MAIN'",Integer.class,sessionId)).isEqualTo(5);
        request("POST","/api/interviews/"+sessionId+"/report",null,409);
        assertThat(request("POST","/api/interviews",Map.of("difficulty","EASY"),200).path("sessionId").asLong()).isEqualTo(sessionId);
        JsonNode current=created.path("currentQuestion");

        for(int index=1;index<=6;index++) {
            long questionId=current.path("interviewQuestionId").asLong();
            var body=Map.of("interviewQuestionId",questionId,"answerContent","Offline candidate answer "+index,"requestId",UUID.randomUUID().toString());
            var response=request("POST","/api/interviews/"+sessionId+"/answers",body,200);
            var detail=request("GET","/api/interviews/"+sessionId,null,200);
            assertThat(json.readTree(json.writeValueAsBytes(interviewService.getDetail(userId,sessionId)))).isEqualTo(detail);
            assertThat(detail.path("questions")).anySatisfy(q -> {
                assertThat(q.path("interviewQuestionId").asLong()).isEqualTo(questionId);
                assertThat(q.path("evaluation")).isEqualTo(response.path("evaluation"));
            });
            var replay=request("POST","/api/interviews/"+sessionId+"/answers",body,200);
            assertThat(replay.path("evaluation")).isEqualTo(response.path("evaluation"));
            assertThat(providerCalls.get()).isEqualTo(index);
            request("POST","/api/interviews/"+sessionId+"/answers",Map.of("interviewQuestionId",questionId,
                    "answerContent","changed","requestId",body.get("requestId")),409);
            request("POST","/api/interviews/"+sessionId+"/answers",Map.of("interviewQuestionId",questionId,
                    "answerContent","Offline duplicate","requestId",UUID.randomUUID().toString()),409);
            assertThat(providerCalls.get()).isEqualTo(index);
            if(index==1) {
                assertThat(response.path("nextAction").asText()).isEqualTo("FOLLOW_UP");
                assertThat(response.path("evaluation").path("evaluationPhase").asText()).isEqualTo("INITIAL");
            } else assertThat(response.path("evaluation").path("evaluationPhase").asText()).isEqualTo("FINAL");
            if(index==6) {
                assertThat(response.path("nextAction").asText()).isEqualTo("FINISH");
                assertThat(response.path("sessionStatus").asText()).isEqualTo("COMPLETED");
                assertThat(response.path("nextQuestion").isNull()).isTrue();
            } else current=response.path("nextQuestion");
        }

        assertThat(request("GET","/api/interviews/current",null,200).isNull()).isTrue();
        var report=request("POST","/api/interviews/"+sessionId+"/report",null,200);
        assertThat(report.path("overallScore").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(request("GET","/api/interviews/"+sessionId+"/report",null,200)).isEqualTo(report);
        assertThat(request("POST","/api/interviews/"+sessionId+"/report",null,200)).isEqualTo(report);
        assertThat(jdbc.queryForObject("SELECT completed_question_count FROM interview_session WHERE id=?",Integer.class,sessionId)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer_evaluation e JOIN interview_question q ON q.id=e.main_interview_question_id WHERE q.session_id=? AND e.evaluation_phase='FINAL'",Integer.class,sessionId)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rag_retrieval_batch b JOIN interview_answer a ON a.id=b.answer_id JOIN interview_question q ON q.id=a.interview_question_id WHERE q.session_id=?",Integer.class,sessionId)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rag_hit_log h JOIN interview_answer a ON a.id=h.answer_id JOIN interview_question q ON q.id=a.interview_question_id WHERE q.session_id=?",Integer.class,sessionId)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_weakness WHERE user_id=?",Integer.class,userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT weakness_score FROM user_weakness WHERE user_id=?",BigDecimal.class,userId)).isEqualByComparingTo("60.00");
        assertThat(jdbc.queryForObject("SELECT discovered_count FROM user_weakness WHERE user_id=?",Integer.class,userId)).isEqualTo(1);
        verify(reportGenerator,times(1)).generate(any(),any(),any());
        verify(embedding,times(6)).embed(any()); verify(vectors,times(6)).search(any(),anyString(),anyInt());
        verifyNoInteractions(milvus,initializer);
    }

    @Test void externalEvaluationFailurePersistsFailedWithoutAdvancingSession() throws Exception {
        var created=request("POST","/api/interviews",Map.of("difficulty","EASY"),200);
        long sessionId=created.path("sessionId").asLong(); long questionId=created.path("currentQuestion").path("interviewQuestionId").asLong();
        var body=Map.of("interviewQuestionId",questionId,"answerContent","Offline failure probe","requestId",UUID.randomUUID().toString());
        doThrow(new ExternalServiceException("Offline provider outage")).when(judge).evaluate(any(),any());
        request("POST","/api/interviews/"+sessionId+"/answers",body,502);
        assertThat(jdbc.queryForObject("SELECT status FROM interview_answer WHERE request_id=?",String.class,body.get("requestId"))).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT completed_question_count FROM interview_session WHERE id=?",Integer.class,sessionId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer_evaluation e JOIN interview_question q ON q.id=e.main_interview_question_id WHERE q.session_id=?",Integer.class,sessionId)).isZero();
        verifyNoInteractions(reportGenerator,milvus,initializer);
    }

    static Stream<Arguments> invalidModelScores() {
        var cases = new ArrayList<Arguments>();
        for (String provider : List.of("deepseek", "jev-fallback")) {
            for (String field : scoreFields()) {
                for (String value : List.of("-1", "21", "null", "missing")) {
                    cases.add(Arguments.of(provider, field, value));
                }
            }
            for (String value : List.of("1.5", "\"20\"", "\"not-a-score\"", "true", "[]")) {
                cases.add(Arguments.of(provider, "correctnessScore", value));
            }
        }
        return cases.stream();
    }

    static Stream<Arguments> legalModelScores() {
        return Stream.of("deepseek", "jev-fallback").flatMap(provider -> scoreFields().stream()
                .flatMap(field -> Stream.of(0, 20)
                        .map(score -> Arguments.of(provider, field, score))));
    }

    private static List<String> scoreFields() {
        return List.of("correctnessScore", "completenessScore", "depthScore", "clarityScore", "practiceScore");
    }

    @ParameterizedTest
    @MethodSource("invalidModelScores")
    void invalidModelScoresReturnSanitized502AndPreserveFailedState(String provider, String field, String value) throws Exception {
        useHttpModel(provider, node -> {
            if (value.equals("missing")) node.remove(field);
            else try { node.set(field, json.readTree(value)); } catch (Exception error) { throw new AssertionError(error); }
        });
        var created = request("POST", "/api/interviews", Map.of("difficulty", "EASY"), 200);
        long sessionId = created.path("sessionId").asLong();
        long questionId = created.at("/currentQuestion/interviewQuestionId").asLong();
        var body = modelAnswer(questionId);
        modelAnswerResponse(sessionId, body, 502);
        assertFailedModelState(sessionId, questionId, body.get("requestId").toString());
        assertThat(providerCalls.get()).as("no automatic model retry").isEqualTo(1);
        verifyNoInteractions(reportGenerator, milvus, initializer);
    }

    @ParameterizedTest
    @MethodSource("legalModelScores")
    void acceptsZeroAndTwentyInEveryDimension(String provider, String field, int score) throws Exception {
        useHttpModel(provider, node -> node.put(field, score));
        var created = request("POST", "/api/interviews", Map.of("difficulty", "EASY"), 200);
        var response = modelAnswerResponse(created.path("sessionId").asLong(),
                modelAnswer(created.at("/currentQuestion/interviewQuestionId").asLong()), 200);
        assertThat(response.at("/data/evaluation/" + field).asInt()).isEqualTo(score);
        assertThat(response.at("/data/evaluation/totalScore").asInt()).isEqualTo(40 + score);
        assertThat(providerCalls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"argument", "state", "null-pointer"})
    void internalExceptionsKeep500WithoutModelRetry(String kind) throws Exception {
        RuntimeException failure = switch (kind) {
            case "argument" -> new IllegalArgumentException("PRIVATE_MODEL_OUTPUT");
            case "state" -> new IllegalStateException("PRIVATE_MODEL_OUTPUT");
            default -> new NullPointerException("PRIVATE_MODEL_OUTPUT");
        };
        doAnswer(invocation -> { providerCalls.incrementAndGet(); throw failure; }).when(judge).evaluate(any(), any());
        var created = request("POST", "/api/interviews", Map.of("difficulty", "EASY"), 200);
        long sessionId = created.path("sessionId").asLong();
        long questionId = created.at("/currentQuestion/interviewQuestionId").asLong();
        var body = modelAnswer(questionId);
        modelAnswerResponse(sessionId, body, 500);
        assertFailedModelState(sessionId, questionId, body.get("requestId").toString());
        assertThat(providerCalls.get()).isEqualTo(1);
    }

    @Test void failedModelRetryReusesIdentityAndCannotChangeOriginalPayload() throws Exception {
        useHttpModel("deepseek", node -> { if (providerCalls.get() == 1) node.put("correctnessScore", 21); });
        var created = request("POST", "/api/interviews", Map.of("difficulty", "EASY"), 200);
        long sessionId = created.path("sessionId").asLong();
        long questionId = created.at("/currentQuestion/interviewQuestionId").asLong();
        var body = modelAnswer(questionId);
        modelAnswerResponse(sessionId, body, 502);
        assertFailedModelState(sessionId, questionId, body.get("requestId").toString());
        request("POST", "/api/interviews/" + sessionId + "/answers", Map.of("interviewQuestionId", questionId,
                "requestId", body.get("requestId"), "answerContent", "changed"), 409);
        request("POST", "/api/interviews/" + sessionId + "/answers", Map.of("interviewQuestionId", questionId,
                "requestId", UUID.randomUUID().toString(), "answerContent", " "), 400);
        assertThat(providerCalls.get()).isEqualTo(1);
        var success = modelAnswerResponse(sessionId, body, 200);
        var replay = modelAnswerResponse(sessionId, body, 200);
        assertThat(replay.path("data")).isEqualTo(success.path("data"));
        assertThat(providerCalls.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_answer WHERE request_id=?",
                Integer.class, body.get("requestId"))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer_evaluation e JOIN interview_answer a ON a.id=e.answer_id WHERE a.request_id=?",
                Integer.class, body.get("requestId"))).isEqualTo(1);
    }

    private Map<String, Object> modelAnswer(long questionId) {
        return Map.of("interviewQuestionId", questionId, "requestId", UUID.randomUUID().toString(),
                "answerContent", "PRIVATE_CANDIDATE_INPUT");
    }

    private JsonNode modelAnswerResponse(long sessionId, Map<String, Object> body, int expected) throws Exception {
        var result = mvc.perform(post("/api/interviews/" + sessionId + "/answers")
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content(json.writeValueAsString(body)))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(expected)).andReturn();
        String raw = result.getResponse().getContentAsString();
        var response = json.readTree(raw);
        if (expected >= 500) {
            assertThat(response.path("message").asText()).isEqualTo(expected == 502 ? "外部服务暂时不可用" : "系统异常，请稍后重试");
            assertThat(raw).doesNotContain("PRIVATE_MODEL_OUTPUT", "PRIVATE_CANDIDATE_INPUT", "private-model-id",
                    "Authorization", token, "rawJson", "systemPrompt", "userPrompt");
        }
        return response;
    }

    private void assertFailedModelState(long sessionId, long questionId, String requestId) {
        assertThat(jdbc.queryForObject("SELECT status FROM interview_answer WHERE request_id=?", String.class, requestId)).isEqualTo("FAILED");
        var session = jdbc.queryForMap("SELECT status,completed_question_count,current_interview_question_id,report_status FROM interview_session WHERE id=?", sessionId);
        assertThat(session.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(((Number) session.get("completed_question_count")).intValue()).isZero();
        assertThat(((Number) session.get("current_interview_question_id")).longValue()).isEqualTo(questionId);
        assertThat(session.get("report_status")).isEqualTo("NOT_STARTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer_evaluation e JOIN interview_question q ON q.id=e.main_interview_question_id WHERE q.session_id=?", Integer.class, sessionId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_report WHERE session_id=?", Integer.class, sessionId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_weakness WHERE user_id=?", Integer.class, userId)).isZero();
    }

    private void useHttpModel(String provider, Consumer<ObjectNode> change) {
        doAnswer(invocation -> {
            providerCalls.incrementAndGet();
            EvaluationContext context = invocation.getArgument(0);
            EvaluationPrompt prompt = invocation.getArgument(1);
            var points = context.scoringPoints().stream().map(point -> new LlmEvaluationSuggestion.ScoringPointResult(
                    point.scoringPointId(), true, context.mainAnswerContent())).toList();
            var suggestion = new LlmEvaluationSuggestion(10, 10, 10, 10, 10, List.of("PRIVATE_MODEL_OUTPUT"), List.of(), null, points, false, null);
            ObjectNode content = json.valueToTree(suggestion);
            change.accept(content);
            var builder = RestClient.builder().baseUrl("http://deepseek.offline");
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://deepseek.offline/chat/completions"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                            json.writeValueAsString(Map.of("model", "private-model-id", "choices", List.of(Map.of(
                                    "index", 0, "finish_reason", "stop", "message", Map.of("role", "assistant", "content", content.toString()))))),
                            MediaType.APPLICATION_JSON));
            var client = new DeepSeekEvaluationClient(builder.build(),
                    new DeepSeekProperties(), json);
            var direct = new DeepSeekJudgeProvider(client);
            try {
                if (provider.equals("deepseek")) return direct.evaluate(context, prompt);
                var nativeClient = mock(JevClient.class);
                when(nativeClient.evaluate(any())).thenThrow(new ExternalServiceException("synthetic native outage"));
                try {
                    return new JevJudgeProvider(nativeClient,
                            new JevProperties(), client, direct, json,
                            new LlmEvaluationValidator()).evaluate(context, prompt);
                } finally { verify(nativeClient, times(1)).evaluate(any()); }
            } finally { server.verify(); }
        }).when(judge).evaluate(any(), any());
    }

    private JsonNode request(String method,String path,Object body,int expected) throws Exception {
        var builder=switch(method) { case "GET" -> get(path); default -> post(path); };
        if(token!=null) builder.header("Authorization","Bearer "+token);
        if(body!=null) builder.contentType("application/json").content(json.writeValueAsString(body));
        var result=mvc.perform(builder).andExpect(status().is(expected)).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
