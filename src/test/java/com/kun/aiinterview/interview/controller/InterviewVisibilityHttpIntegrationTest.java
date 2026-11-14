package com.kun.aiinterview.interview.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.judge.JudgeProvider;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.service.InterviewReportGenerator;
import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingVector;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import com.kun.aiinterview.knowledge.vector.milvus.MilvusCollectionInitializer;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Real socket, JWT filter, MyBatis and MySQL; only external AI/vector ports are mocked. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"milvus.enabled=true", "deepseek.enabled=true",
                "deepseek.api-key=offline-test-key"})
@ActiveProfiles("test")
class InterviewVisibilityHttpIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoBean EmbeddingClient embedding;
    @MockitoBean VectorStoreClient vectors;
    @MockitoBean JudgeProvider judge;
    @MockitoBean InterviewReportGenerator reportGenerator;
    @MockitoBean MilvusClientV2 milvus;
    @MockitoBean MilvusCollectionInitializer initializer;

    private final List<Long> questionIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> sessionIds = new ArrayList<>();
    private String marker;
    private String token;
    private String otherToken;
    private long sessionId;

    @BeforeEach
    void setUp() throws Exception {
        marker = "s1-" + UUID.randomUUID();
        for (String category : List.of("JAVA_BASIC", "JAVA_COLLECTION", "SPRING", "MYSQL", "NETWORK")) {
            String point = marker + "-" + category;
            jdbc.update("""
                    INSERT INTO question(category,knowledge_point,difficulty,question_content,reference_answer,status)
                    VALUES (?,?,'EASY',?,?,'ENABLED')
                    """, category, point, "QUESTION " + point, "PRIVATE REFERENCE " + point);
            Long id = jdbc.queryForObject("SELECT id FROM question WHERE knowledge_point=?", Long.class, point);
            questionIds.add(id);
            jdbc.update("""
                    INSERT INTO question_scoring_point(question_id,point_type,content,weight,sort_order,status)
                    VALUES (?,'CORE',?,100,1,'ENABLED')
                    """, id, "PRIVATE SCORING POINT " + point);
        }
        when(embedding.embed(any())).thenReturn(new EmbeddingBatchResult("qwen3.7-text-embedding",
                "qwen3.7-text-embedding-1024-dense-v1", 1024,
                List.of(new EmbeddingVector(0, Collections.nCopies(1024, .01F))), 0L));
        when(vectors.search(any(), anyString(), anyInt())).thenReturn(List.of());
        when(judge.evaluate(any(), any())).thenAnswer(call -> {
            EvaluationContext context = call.getArgument(0);
            var results = context.scoringPoints().stream()
                    .map(point -> new LlmEvaluationSuggestion.ScoringPointResult(point.scoringPointId(), true,
                            "synthetic test evidence"))
                    .toList();
            var suggestion = new LlmEvaluationSuggestion(18, 18, 18, 18, 18,
                    List.of("synthetic strength"), List.of("synthetic gap"), "synthetic correction",
                    results, false, null);
            return new JudgeProvider.Result("offline-judge", "offline-v1",
                    json.writeValueAsString(suggestion), suggestion);
        });
        token = registerAndLogin("owner");
        otherToken = registerAndLogin("other");
        JsonNode created = post("/api/interviews", token, Map.of("difficulty", "EASY"), 200).path("data");
        sessionId = created.path("sessionId").asLong();
        assertThat(sessionId).isPositive();
        sessionIds.add(sessionId);
    }

    @AfterEach
    void tearDown() {
        for (Long id : sessionIds) {
            jdbc.update("""
                    DELETE FROM rag_hit_log WHERE retrieval_batch_id IN
                    (SELECT retrieval_batch_id FROM rag_retrieval_batch WHERE answer_id IN
                    (SELECT a.id FROM interview_answer a JOIN interview_question q ON q.id=a.interview_question_id
                    WHERE q.session_id=?))
                    """, id);
            jdbc.update("""
                    DELETE FROM answer_evaluation WHERE answer_id IN
                    (SELECT a.id FROM interview_answer a JOIN interview_question q ON q.id=a.interview_question_id
                    WHERE q.session_id=?)
                    """, id);
            jdbc.update("""
                    DELETE FROM rag_retrieval_batch WHERE answer_id IN
                    (SELECT a.id FROM interview_answer a JOIN interview_question q ON q.id=a.interview_question_id
                    WHERE q.session_id=?)
                    """, id);
            jdbc.update("""
                    DELETE a FROM interview_answer a JOIN interview_question q ON q.id=a.interview_question_id
                    WHERE q.session_id=?
                    """, id);
            jdbc.update("DELETE FROM interview_question WHERE session_id=? AND question_type='FOLLOW_UP'", id);
            jdbc.update("DELETE FROM interview_question WHERE session_id=?", id);
            jdbc.update("DELETE FROM interview_session WHERE id=?", id);
        }
        for (Long id : questionIds) {
            jdbc.update("DELETE FROM question_scoring_point WHERE question_id=?", id);
            jdbc.update("DELETE FROM question WHERE id=?", id);
        }
        for (Long id : userIds) {
            jdbc.update("DELETE FROM user_weakness WHERE user_id=?", id);
            jdbc.update("DELETE FROM user WHERE id=?", id);
        }
        sessionIds.clear();
        questionIds.clear();
        userIds.clear();
    }

    @Test
    void realHttpEnforcesOwnershipAndOnlyExposesCurrentAndAnsweredQuestions() throws Exception {
        assertThat(get("/api/interviews/" + sessionId, null).getStatusCode().value()).isEqualTo(401);
        assertThat(get("/api/interviews/current", token + "invalid").getStatusCode().value()).isEqualTo(401);
        assertThat(get("/api/interviews/" + sessionId, otherToken).getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/admin/questions", token).getStatusCode().value()).isEqualTo(403);

        JsonNode current = getData("/api/interviews/current", token);
        long firstQuestionId = current.path("currentQuestion").path("interviewQuestionId").asLong();
        assertThat(firstQuestionId).isPositive();
        JsonNode detail = getData("/api/interviews/" + sessionId, token);
        assertNoInternalScoringData(current);
        assertNoInternalScoringData(detail);
        assertNoFutureQuestions(detail, sessionId, firstQuestionId);
        assertThat(detail.path("questions").size()).isEqualTo(1);
        assertThat(detail.path("questions").get(0).path("interviewQuestionId").asLong()).isEqualTo(firstQuestionId);

        JsonNode submitted = post("/api/interviews/" + sessionId + "/answers", token,
                Map.of("interviewQuestionId", firstQuestionId, "answerContent", "Synthetic answer",
                        "requestId", UUID.randomUUID().toString()), 200).path("data");
        assertThat(submitted.path("evaluation").path("correction").asText()).isEqualTo("synthetic correction");
        long secondQuestionId = submitted.path("nextQuestion").path("interviewQuestionId").asLong();
        assertThat(secondQuestionId).isPositive().isNotEqualTo(firstQuestionId);
        assertNoInternalScoringData(submitted);

        JsonNode resumed = getData("/api/interviews/current", token);
        assertThat(resumed.path("currentQuestion").path("interviewQuestionId").asLong()).isEqualTo(secondQuestionId);
        JsonNode after = getData("/api/interviews/" + sessionId, token);
        assertThat(after.path("questions").size()).isEqualTo(2);
        JsonNode previous = after.path("questions").get(0);
        assertThat(previous.path("interviewQuestionId").asLong()).isEqualTo(firstQuestionId);
        assertThat(previous.path("answer").path("answerContent").asText()).isEqualTo("Synthetic answer");
        assertThat(previous.path("evaluation").path("correction").asText()).isEqualTo("synthetic correction");
        assertThat(after.path("questions").get(1).path("interviewQuestionId").asLong()).isEqualTo(secondQuestionId);
        assertNoFutureQuestions(after, sessionId, secondQuestionId);
        assertNoInternalScoringData(after);
    }

    private void assertNoFutureQuestions(JsonNode response, long id, long currentId) {
        List<Map<String, Object>> future = jdbc.queryForList("""
                SELECT id, question_content FROM interview_question
                WHERE session_id=? AND status='PENDING' AND id<>?
                """, id, currentId);
        assertThat(future).isNotEmpty();
        String body = response.toString();
        for (Map<String, Object> question : future) {
            assertThat(body).doesNotContain(question.get("question_content").toString());
            for (JsonNode visible : response.path("questions")) {
                assertThat(visible.path("interviewQuestionId").asLong())
                        .isNotEqualTo(((Number) question.get("id")).longValue());
            }
        }
    }

    private void assertNoInternalScoringData(JsonNode body) {
        String value = body.toString();
        assertThat(value).doesNotContain("PRIVATE REFERENCE", "PRIVATE SCORING POINT");
        Set<String> fields = new HashSet<>();
        collectFields(body, fields);
        assertThat(fields).doesNotContain("referenceAnswer", "referenceAnswerSnapshot", "scoringPoints",
                "scoringPointsSnapshot", "scoringPointResults", "weight", "followUpTargetPoints", "rawResult");
    }

    private void collectFields(JsonNode node, Set<String> fields) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                fields.add(entry.getKey());
                collectFields(entry.getValue(), fields);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectFields(child, fields));
        }
    }

    private String registerAndLogin(String suffix) {
        String account = marker.replace("-", "").substring(0, 20) + suffix;
        String password = "OfflineOnly123!";
        post("/api/auth/register", null, Map.of("account", account, "username", "synthetic user",
                "email", account + "@example.com", "password", password), 200);
        JsonNode login = post("/api/auth/login", null, Map.of("account", account, "password", password), 200).path("data");
        userIds.add(login.path("userId").asLong());
        return login.path("accessToken").asText();
    }

    private JsonNode getData(String path, String bearer) {
        ResponseEntity<JsonNode> response = get(path, bearer);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody().path("data");
    }

    private ResponseEntity<JsonNode> get(String path, String bearer) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(bearer)), JsonNode.class);
    }

    private JsonNode post(String path, String bearer, Object body, int expectedStatus) {
        ResponseEntity<JsonNode> response = http.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers(bearer)), JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        return response.getBody();
    }

    private HttpHeaders headers(String bearer) {
        HttpHeaders headers = new HttpHeaders();
        if (bearer != null) headers.setBearerAuth(bearer);
        return headers;
    }
}
