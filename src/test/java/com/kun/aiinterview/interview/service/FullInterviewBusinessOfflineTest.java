package com.kun.aiinterview.interview.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.judge.DeepSeekJudgeProvider;
import com.kun.aiinterview.interview.evaluation.judge.JudgeProvider;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationStandard;
import com.kun.aiinterview.interview.model.InterviewReportGenerationResult;
import com.kun.aiinterview.interview.orchestration.EvaluationOrchestrationService;
import com.kun.aiinterview.interview.report.deepseek.DeepSeekInterviewReportGenerator;
import com.kun.aiinterview.interview.report.prompt.InterviewReportPromptBuilder;
import org.springframework.test.web.client.ResponseCreator;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.function.Function;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingConfiguration;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import com.kun.aiinterview.knowledge.embedding.EmbeddingVector;
import com.kun.aiinterview.knowledge.embedding.springai.SpringAiEmbeddingClient;
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
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
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
@SpringBootTest(properties = {"milvus.enabled=true", "deepseek.enabled=true",
        "deepseek.api-key=offline-test-key", "rag.retrieval.cosine-threshold-enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class FullInterviewBusinessOfflineTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired InterviewService interviewService;
    @Autowired EvaluationOrchestrationService orchestration;
    @Autowired DeepSeekProperties deepSeekProperties;
    @Autowired EmbeddingProperties embeddingProperties;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @MockitoBean EmbeddingClient embedding;
    @MockitoBean VectorStoreClient vectors;
    @MockitoBean JudgeProvider judge;
    @MockitoBean InterviewReportGenerator reportGenerator;
    @MockitoBean MilvusClientV2 milvus;
    @MockitoBean MilvusCollectionInitializer initializer;
    private static final String LOG_SENTINEL = "B4B_TEST_SENSITIVE_BODY_20261112";
    private static final String EMBEDDING_LOG_SENTINEL = "B5_P2B_SENSITIVE_RESPONSE_SENTINEL";
    private static final String MALICIOUS_RESPONSE = LOG_SENTINEL
            + "\r\nERROR FORGED_LOG_RECORD\u001b[31m\u0000 Authorization: Bearer offline-test-key PRIVATE_CANDIDATE_INPUT";
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

    @ParameterizedTest
    @ValueSource(strings = {"400", "401", "403", "429", "500", "503", "connect", "timeout", "json", "model", "empty"})
    void embeddingFailureKeepsLogsSafeAndAllowsExplicitAnswerRecovery(String fault) throws Exception {
        var calls = new AtomicInteger();
        var server = useHttpEmbedding(fault, calls);
        var created = request("POST", "/api/interviews", Map.of("difficulty", "EASY"), 200);
        long sessionId = created.path("sessionId").asLong();
        long questionId = created.at("/currentQuestion/interviewQuestionId").asLong();
        var body = modelAnswer(questionId);
        withSafeProviderLog(() -> modelAnswerResponse(sessionId, body, 502), "InterviewWorkflowService");
        assertFailedModelState(sessionId, questionId, body.get("requestId").toString());
        assertThat(calls.get()).isEqualTo(1);
        verifyNoInteractions(vectors, judge, reportGenerator);
        request("POST", "/api/interviews/" + sessionId + "/answers", Map.of("interviewQuestionId", questionId,
                "requestId", body.get("requestId"), "answerContent", "changed"), 409);
        var success = modelAnswerResponse(sessionId, body, 200);
        var replay = modelAnswerResponse(sessionId, body, 200);
        assertThat(replay.path("data")).isEqualTo(success.path("data"));
        assertThat(calls.get()).isEqualTo(2);
        verify(vectors, times(1)).search(any(), anyString(), anyInt());
        verify(judge, times(1)).evaluate(any(), any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_answer WHERE request_id=?",
                Integer.class, body.get("requestId"))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer_evaluation e JOIN interview_answer a ON a.id=e.answer_id WHERE a.request_id=?",
                Integer.class, body.get("requestId"))).isEqualTo(1);
        server.verify();
    }

    @Test
    void embeddingFailureDoesNotWriteVectorsAndDocumentRetryCanReachReady() throws Exception {
        var calls = new AtomicInteger();
        var server = useHttpEmbedding("503", calls);
        jdbc.update("UPDATE user SET role='ADMIN' WHERE id=?", userId);
        String title = "b5p2b-" + UUID.randomUUID();
        String content = "B5P2B_PRIVATE_KNOWLEDGE_CONTENT";
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8)));
        jdbc.update("""
                INSERT INTO knowledge_document(title,category,file_name,file_type,content,content_hash,
                    document_version,processing_status)
                VALUES (?,'JAVA_BASIC','b5p2b.md','MARKDOWN',?,?,1,'UPLOADED')
                """, title, content, hash);
        Long documentId = jdbc.queryForObject("SELECT id FROM knowledge_document WHERE title=?", Long.class, title);
        String endpoint = "/api/admin/knowledge/documents/" + documentId + "/process";
        withSafeProviderLog(() -> request("POST", endpoint, null, 502), "KnowledgeDocumentProcessingService");
        assertThat(jdbc.queryForObject("SELECT processing_status FROM knowledge_document WHERE id=?",
                String.class, documentId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT error_message FROM knowledge_document WHERE id=?",
                String.class, documentId)).doesNotContain(EMBEDDING_LOG_SENTINEL, content);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_chunk WHERE document_id=?",
                Integer.class, documentId)).isZero();
        verifyNoInteractions(vectors);
        assertThat(calls.get()).isEqualTo(1);
        request("POST", endpoint, null, 200);
        assertThat(jdbc.queryForObject("SELECT processing_status FROM knowledge_document WHERE id=?",
                String.class, documentId)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_chunk WHERE document_id=? AND status='ACTIVE'",
                Integer.class, documentId)).isEqualTo(1);
        verify(vectors, times(1)).insert(any());
        verify(vectors, never()).deleteByVectorIds(any());
        assertThat(calls.get()).isEqualTo(2);
        server.verify();
    }

    private MockRestServiceServer useHttpEmbedding(String fault, AtomicInteger calls) throws Exception {
        var builder = EmbeddingConfiguration.createEmbeddingRestClientBuilder(embeddingProperties);
        var server = MockRestServiceServer.bindTo(builder).build();
        String upstreamBody = EMBEDDING_LOG_SENTINEL + MALICIOUS_RESPONSE;
        String valid = json.writeValueAsString(Map.of("model", embeddingProperties.getModel(), "data",
                List.of(Map.of("index", 0, "embedding", Collections.nCopies(embeddingProperties.getDimension(), .01F))),
                "usage", Map.of("prompt_tokens", 1, "total_tokens", 1)));
        ResponseCreator failure = switch (fault) {
            case "connect" -> withException(new ConnectException(upstreamBody));
            case "timeout" -> withException(new SocketTimeoutException(upstreamBody));
            case "json" -> withSuccess("{\"model\":" + EMBEDDING_LOG_SENTINEL + "}", MediaType.APPLICATION_JSON);
            case "model" -> withSuccess(valid.replace(json.writeValueAsString(embeddingProperties.getModel()),
                    json.writeValueAsString(upstreamBody)), MediaType.APPLICATION_JSON);
            case "empty" -> withSuccess("", MediaType.APPLICATION_JSON);
            default -> withRawStatus(Integer.parseInt(fault)).body(upstreamBody);
        };
        String url = embeddingProperties.getBaseUrl() + "/embeddings";
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(url)).andRespond(failure);
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(url))
                .andRespond(withSuccess(valid, MediaType.APPLICATION_JSON));
        var api = EmbeddingConfiguration.createOpenAiApi(embeddingProperties, builder);
        var client = new SpringAiEmbeddingClient(EmbeddingConfiguration.createEmbeddingModel(api, embeddingProperties),
                embeddingProperties);
        doAnswer(invocation -> {
            calls.incrementAndGet();
            List<String> texts = invocation.getArgument(0);
            assertThat(String.join("\n", texts)).doesNotContain("PRIVATE_CANDIDATE_INPUT");
            return client.embed(texts);
        }).when(embedding).embed(any());
        return server;
    }

    static Stream<Arguments> invalidModelScores() {
        var cases = new ArrayList<Arguments>();
        for (String field : scoreFields()) {
            for (String value : List.of("-1", "21", "null", "missing", "1.5", "\"20\"", "\"not-a-score\"", "true", "[]")) {
                cases.add(Arguments.of(field, value));
            }
        }
        return cases.stream();
    }

    static Stream<Arguments> legalModelScores() {
        return scoreFields().stream()
                .flatMap(field -> Stream.of(0, 20)
                        .map(score -> Arguments.of(field, score)));
    }

    private static List<String> scoreFields() {
        return List.of("correctnessScore", "completenessScore", "depthScore", "clarityScore", "practiceScore");
    }

    @ParameterizedTest
    @MethodSource("invalidModelScores")
    void invalidModelScoresReturnSanitized502AndPreserveFailedState(String field, String value) throws Exception {
        useHttpModel(node -> {
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
    void acceptsZeroAndTwentyInEveryDimension(String field, int score) throws Exception {
        useHttpModel(node -> node.put(field, score));
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
        useHttpModel(node -> { if (providerCalls.get() == 1) node.put("correctnessScore", 21); });
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

    @ParameterizedTest
    @ValueSource(strings = {"400", "401", "402", "403", "408", "429", "500", "502", "503", "504", "connect", "connect-timeout", "http-timeout", "timeout", "empty", "blank", "length", "content_filter", "insufficient_system_resource", "tool_calls", "illegal-json", "missing-fields", "outer-json", "malicious-finish"})
    void singleHttpFailureAllowsOnlyExplicitSamePayloadRecovery(String fault) throws Exception {
        doAnswer(invocation -> {
            providerCalls.incrementAndGet();
            return withHttpResponse(faultResponse(fault), client -> new DeepSeekJudgeProvider(client)
                    .evaluate(invocation.getArgument(0), invocation.getArgument(1)));
        }).when(judge).evaluate(any(), any());
        var created = request("POST", "/api/interviews", Map.of("difficulty", "EASY"), 200);
        long sessionId = created.path("sessionId").asLong();
        long questionId = created.at("/currentQuestion/interviewQuestionId").asLong();
        var body = modelAnswer(questionId);
        withSafeProviderLog(() -> modelAnswerResponse(sessionId, body, 502), "InterviewWorkflowService");
        assertFailedModelState(sessionId, questionId, body.get("requestId").toString());
        assertThat(providerCalls.get()).isEqualTo(1);
        request("POST", "/api/interviews/" + sessionId + "/answers", Map.of("interviewQuestionId", questionId,
                "requestId", body.get("requestId"), "answerContent", "changed"), 409);
        assertThat(providerCalls.get()).isEqualTo(1);
        useHttpModel(node -> { });
        var recovered = modelAnswerResponse(sessionId, body, 200);
        assertThat(modelAnswerResponse(sessionId, body, 200)).isEqualTo(recovered);
        assertThat(providerCalls.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer_evaluation e JOIN interview_answer a ON a.id=e.answer_id WHERE a.request_id=?",
                Integer.class, body.get("requestId"))).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"400", "401", "402", "403", "408", "429", "500", "502", "503", "504", "connect", "connect-timeout", "http-timeout", "timeout", "empty", "blank", "length", "content_filter", "insufficient_system_resource", "tool_calls", "illegal-json", "missing-fields", "wrong-type", "outer-json", "malicious-finish"})
    void reportFailureDoesNotPublishOrUpdateWeaknessAndSameSessionCanRecover(String fault) throws Exception {
        useHttpModel(node -> { });
        var created = request("POST", "/api/interviews", Map.of("difficulty", "EASY"), 200);
        long sessionId = created.path("sessionId").asLong();
        var current = created.path("currentQuestion");
        for (int i = 0; i < 5; i++) {
            current = modelAnswerResponse(sessionId, modelAnswer(current.path("interviewQuestionId").asLong()), 200)
                    .at("/data/nextQuestion");
        }
        var calls = new AtomicInteger();
        useHttpReport(fault, calls);
        var failure = withSafeProviderLog(() -> mvc.perform(post("/api/interviews/" + sessionId + "/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.message").value("外部服务暂时不可用")).andReturn(), "InterviewReportService");
        assertThat(failure.getResponse().getContentAsString()).doesNotContain(LOG_SENTINEL, "PRIVATE_MODEL_OUTPUT", "private-model-id", token);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT report_status FROM interview_session WHERE id=?", String.class, sessionId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT status FROM interview_session WHERE id=?", String.class, sessionId)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_report WHERE session_id=?", Integer.class, sessionId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_weakness WHERE user_id=?", Integer.class, userId)).isZero();
        useHttpReport("valid-report", calls);
        var ready = request("POST", "/api/interviews/" + sessionId + "/report", null, 200);
        assertThat(request("POST", "/api/interviews/" + sessionId + "/report", null, 200)).isEqualTo(ready);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_report WHERE session_id=?", Integer.class, sessionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT report_status FROM interview_session WHERE id=?", String.class, sessionId)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT MAX(discovered_count) FROM user_weakness WHERE user_id=?", Integer.class, userId)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void historicalJevFinalAndFollowUpReadAndReplayWithoutModelOrDuplicateTrace(boolean followUp) throws Exception {
        if (!followUp) useHttpModel(node -> { });
        var created = request("POST", "/api/interviews", Map.of("difficulty", "EASY"), 200);
        long sessionId = created.path("sessionId").asLong();
        var body = modelAnswer(created.at("/currentQuestion/interviewQuestionId").asLong());
        var original = modelAnswerResponse(sessionId, body, 200);
        long answerId = jdbc.queryForObject("SELECT id FROM interview_answer WHERE request_id=?", Long.class, body.get("requestId"));
        // Only this test's newly created row becomes a historical fixture. The opaque envelope is not a DeepSeek DTO.
        String historicalRaw = "{\"provider\":\"jev\",\"judge\":{\"answers\":{}},\"generation\":{\"rawJson\":\"legacy opaque payload\"}}";
        assertThat(jdbc.update("UPDATE answer_evaluation SET llm_model='jev-1.13.0',prompt_version='jev-judge-v1',raw_result=? WHERE answer_id=?",
                historicalRaw, answerId)).isEqualTo(1);
        // JdbcTemplate fixture updates bypass MyBatis' transaction-local first-level cache.
        sqlSession.clearCache();
        var before = jdbc.queryForMap("SELECT * FROM answer_evaluation WHERE answer_id=?", answerId);
        var tracesBefore = jdbc.queryForList("SELECT * FROM rag_retrieval_batch WHERE answer_id=?", answerId);
        var hitsBefore = jdbc.queryForList("SELECT * FROM rag_hit_log WHERE answer_id=?", answerId);
        var questionsBefore = jdbc.queryForList("SELECT * FROM interview_question WHERE session_id=? ORDER BY id", sessionId);
        clearInvocations(judge, embedding, vectors, reportGenerator);
        var detail = request("GET", "/api/interviews/" + sessionId, null, 200);
        assertThat(detail.path("questions").get(0).path("evaluation").path("evaluationPhase").asText())
                .isEqualTo(followUp ? "INITIAL" : "FINAL");
        assertThat(modelAnswerResponse(sessionId, body, 200)).isEqualTo(original);
        var replay = orchestration.evaluate(answerId, true, "history-read-only");
        assertThat(replay.decisionAction().name()).isEqualTo(followUp ? "FOLLOW_UP" : "NEXT_MAIN");
        assertThat(replay.evaluation().getLlmModel()).isEqualTo("jev-1.13.0");
        assertThat(json.readTree(replay.evaluation().getRawResult())).isEqualTo(json.readTree(historicalRaw));
        if (followUp) assertThat(replay.followUpTargetPointIds()).isNotEmpty();
        assertThat(jdbc.queryForMap("SELECT * FROM answer_evaluation WHERE answer_id=?", answerId)).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM rag_retrieval_batch WHERE answer_id=?", answerId)).isEqualTo(tracesBefore);
        assertThat(jdbc.queryForList("SELECT * FROM rag_hit_log WHERE answer_id=?", answerId)).isEqualTo(hitsBefore);
        assertThat(jdbc.queryForList("SELECT * FROM interview_question WHERE session_id=? ORDER BY id", sessionId)).isEqualTo(questionsBefore);
        verifyNoInteractions(judge, embedding, vectors, reportGenerator);
    }

    private <T> T withSafeProviderLog(Callable<T> request, String serviceClass) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("com.kun.aiinterview");
        var appender = new ListAppender<ILoggingEvent>();
        var layout = new PatternLayout();
        appender.setContext(logger.getLoggerContext());
        layout.setContext(logger.getLoggerContext());
        layout.setPattern("%level %logger - %msg%n%ex{full}");
        appender.start();
        layout.start();
        logger.addAppender(appender);
        try {
            T result = request.call();
            assertThat(appender.list).hasSize(1);
            var event = appender.list.getFirst();
            assertThat(event.getFormattedMessage()).isEqualTo("外部服务异常");
            var throwable = ((ThrowableProxy) event.getThrowableProxy()).getThrowable();
            assertThat(throwable).isInstanceOf(ExternalServiceException.class).hasNoCause();
            assertThat(throwable.getSuppressed()).isEmpty();
            String rendered = layout.doLayout(event);
            assertThat(rendered).contains("ERROR", "ExternalServiceException", serviceClass);
            for (String output : List.of(rendered, ThrowableProxyUtil.asString(event.getThrowableProxy()))) {
                assertThat(output).doesNotContain(LOG_SENTINEL, EMBEDDING_LOG_SENTINEL, "B5P2B_PRIVATE_KNOWLEDGE_CONTENT", "PRIVATE_MODEL_OUTPUT", "PRIVATE_CANDIDATE_INPUT",
                        "private-model-id", "offline-test-key", "Authorization:", "Bearer ", token,
                        "FORGED_LOG_RECORD", "\r", "\u001b", "\u0000");
            }
            return result;
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            layout.stop();
        }
    }

    private void useHttpReport(String fault, AtomicInteger calls) {
        doAnswer(invocation -> {
            calls.incrementAndGet();
            return withHttpResponse(faultResponse(fault), client -> new DeepSeekInterviewReportGenerator(
                    client, deepSeekProperties, new InterviewReportPromptBuilder(json), new EvaluationStandard(), json)
                    .generate(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        }).when(reportGenerator).generate(any(), any(), any());
    }

    private <T> T withHttpResponse(ResponseCreator response, Function<DeepSeekEvaluationClient, T> call) {
        var builder = RestClient.builder().baseUrl("http://deepseek.offline")
                .defaultHeader("Authorization", "Bearer offline-test-key");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.ExpectedCount.once(),
                        org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://deepseek.offline/chat/completions"))
                .andRespond(response);
        try { return call.apply(new DeepSeekEvaluationClient(builder.build(), deepSeekProperties, json)); }
        finally { server.verify(); }
    }

    private ResponseCreator faultResponse(String fault) throws Exception {
        if (fault.matches("[45][0-9]{2}")) return withRawStatus(Integer.parseInt(fault)).body(MALICIOUS_RESPONSE);
        if (fault.equals("connect")) return withException(new ConnectException(MALICIOUS_RESPONSE));
        if (fault.equals("connect-timeout")) return withException(new java.net.http.HttpConnectTimeoutException(MALICIOUS_RESPONSE));
        if (fault.equals("http-timeout")) return withException(new java.net.http.HttpTimeoutException(MALICIOUS_RESPONSE));
        if (fault.equals("timeout")) return withException(new SocketTimeoutException(MALICIOUS_RESPONSE));
        if (fault.equals("outer-json")) return withSuccess("{" + LOG_SENTINEL, MediaType.APPLICATION_JSON);
        if (fault.equals("empty")) return withSuccess("", MediaType.APPLICATION_JSON);
        String content = switch (fault) {
            case "blank" -> "   ";
            case "illegal-json" -> "{" + LOG_SENTINEL;
            case "wrong-type" -> "{\"summary\":[],\"strengths\":\"" + LOG_SENTINEL + "\"}";
            case "valid-report" -> json.writeValueAsString(Map.of("summary", "Recovered report", "strengths", List.of("Strength"),
                    "weaknesses", List.of("Weakness"), "suggestions", List.of("Suggestion")));
            default -> "{}";
        };
        String finish = fault.equals("malicious-finish") ? MALICIOUS_RESPONSE
                : List.of("length", "content_filter", "insufficient_system_resource", "tool_calls").contains(fault) ? fault : "stop";
        return withSuccess(json.writeValueAsString(Map.of("model", "private-model-id", "choices", List.of(Map.of(
                "index", 0, "finish_reason", finish, "message", Map.of("role", "assistant", "content", content))))), MediaType.APPLICATION_JSON);
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
            assertThat(raw).doesNotContain(LOG_SENTINEL, "PRIVATE_MODEL_OUTPUT", "PRIVATE_CANDIDATE_INPUT", "private-model-id",
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

    private void useHttpModel(Consumer<ObjectNode> change) {
        doAnswer(invocation -> {
            providerCalls.incrementAndGet();
            EvaluationContext context = invocation.getArgument(0);
            EvaluationPrompt prompt = invocation.getArgument(1);
            var points = context.scoringPoints().stream().map(point -> new LlmEvaluationSuggestion.ScoringPointResult(
                    point.scoringPointId(), true, context.mainAnswerContent())).toList();
            var suggestion = new LlmEvaluationSuggestion(10, 10, 10, 10, 10, List.of("PRIVATE_MODEL_OUTPUT"), List.of(), null, points, false, null);
            ObjectNode content = json.valueToTree(suggestion);
            change.accept(content);
            var builder = RestClient.builder().baseUrl("http://deepseek.offline")
                .defaultHeader("Authorization", "Bearer offline-test-key");
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://deepseek.offline/chat/completions"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                            json.writeValueAsString(Map.of("model", "private-model-id", "choices", List.of(Map.of(
                                    "index", 0, "finish_reason", "stop", "message", Map.of("role", "assistant", "content", content.toString()))))),
                            MediaType.APPLICATION_JSON));
            var client = new DeepSeekEvaluationClient(builder.build(),
                    deepSeekProperties, json);
            var direct = new DeepSeekJudgeProvider(client);
            try {
                return direct.evaluate(context, prompt);
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
