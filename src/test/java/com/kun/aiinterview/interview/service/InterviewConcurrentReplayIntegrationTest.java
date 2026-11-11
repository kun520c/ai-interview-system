package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.dto.SubmitInterviewAnswerRequest;
import com.kun.aiinterview.interview.entity.InterviewAnswer;
import com.kun.aiinterview.interview.evaluation.llm.DeepSeekEvaluationResult;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.mapper.InterviewAnswerMapper;
import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingVector;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import com.kun.aiinterview.knowledge.vector.milvus.MilvusCollectionInitializer;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.user.enums.UserRole;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** No outer test transaction: both requests use committed MySQL state and real transaction proxies. */
@SpringBootTest(properties = {"milvus.enabled=true", "deepseek.enabled=true",
        "deepseek.api-key=offline-test-key", "rag.retrieval.cosine-threshold-enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class InterviewConcurrentReplayIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @Autowired JwtTokenService tokens;
    @Autowired InterviewWorkflowTransactionService transactions;
    @MockitoSpyBean InterviewWorkflowService workflow;
    @MockitoSpyBean InterviewAnswerMapper answers;
    @MockitoBean DeepSeekEvaluationClient deepSeek;
    @MockitoBean EmbeddingClient embedding;
    @MockitoBean VectorStoreClient vectors;
    @MockitoBean MilvusClientV2 milvus;
    @MockitoBean MilvusCollectionInitializer initializer;

    private final ThreadLocal<Boolean> pausedRequest = ThreadLocal.withInitial(() -> false);
    private final CountDownLatch paused = new CountDownLatch(1);
    private final CountDownLatch resume = new CountDownLatch(1);
    private Long userId;
    private Long otherUserId;
    private Long bankQuestionId;
    private Long sessionId;
    private Long mainQuestionId;
    private Long nextMainId;
    private String token;
    private boolean recommendFollowUp;
    private int priorEvaluations;
    private int priorVersion;

    @BeforeEach
    void prepare() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(AopUtils.isAopProxy(transactions)).isTrue();
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("ai_interview_test");
        when(embedding.embed(any())).thenReturn(new EmbeddingBatchResult("qwen3.7-text-embedding",
                "qwen3.7-text-embedding-1024-dense-v1", 1024,
                List.of(new EmbeddingVector(0, Collections.nCopies(1024, .01F))), 0L));
        when(vectors.search(any(), anyString(), anyInt())).thenReturn(List.of());
        when(deepSeek.evaluate(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return modelResult();
        });
        userId = insertUser();
        token = tokenFor(userId);
    }

    static Stream<Arguments> completedInterleavings() {
        return Stream.of("BEFORE_WORKFLOW", "AFTER_ANSWER_READ")
                .flatMap(point -> Stream.of("MAIN_FOLLOW_UP", "MAIN_NEXT_MAIN", "MAIN_FINISH",
                        "FOLLOW_UP_NEXT_MAIN", "FOLLOW_UP_FINISH").map(path -> Arguments.of(point, path)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("completedInterleavings")
    void completedConcurrentRequestReplaysAfterSessionMoves(String pausePoint, String path) throws Exception {
        SubmitInterviewAnswerRequest request = seedPath(path);
        if (pausePoint.equals("BEFORE_WORKFLOW")) {
            doAnswer(invocation -> {
                pauseFirstRequest();
                return invocation.callRealMethod();
            }).when(workflow).evaluateAnswer(anyLong());
        } else {
            doAnswer(invocation -> {
                // MyBatis mappers are JDK proxies; execute the same mapped SELECT through its real session.
                InterviewAnswer snapshot = sqlSession.selectOne(
                        InterviewAnswerMapper.class.getName() + ".getInterviewAnswerById", invocation.getArgument(0));
                pauseFirstRequest();
                return snapshot;
            }).when(answers).getInterviewAnswerById(anyLong());
        }

        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> {
                pausedRequest.set(true);
                try { return submit(request, token); }
                finally { pausedRequest.remove(); }
            });
            assertThat(paused.await(10, TimeUnit.SECONDS)).as("A paused after its answer commit").isTrue();
            assertThat(answerState(request)).isEqualTo("SUBMITTED");
            HttpResult second = submit(request, token);
            assertSuccess(second);
            assertCompletedState(request, path);
            Map<String, Object> committedSession = sessionRow();
            Map<String, Object> committedEvaluation = evaluationRow(request);
            resume.countDown();
            HttpResult firstResult = first.get(10, TimeUnit.SECONDS);

            // The database assertions above also run on the red reproduction before this HTTP assertion.
            assertSuccess(firstResult);
            assertThat(firstResult.body()).isEqualTo(second.body());
            assertThat(sessionRow()).isEqualTo(committedSession);
            assertThat(evaluationRow(request)).isEqualTo(committedEvaluation);
            assertSuccess(submit(request, token));
            assertCompletedState(request, path);
        } finally {
            resume.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void freshEvaluationReturns409AndDoesNotStartSecondModelCall() throws Exception {
        var request = seedPath("MAIN_FINISH");
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            paused.countDown();
            assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
            return modelResult();
        }).when(deepSeek).evaluate(any());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> submit(request, token));
            assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(answerState(request)).isEqualTo("EVALUATING");
            assertThat(submit(request, token).status()).isEqualTo(409);
            verify(deepSeek, times(1)).evaluate(any());
            assertThat(evaluationCount()).isZero();
            assertThat(((Number) sessionRow().get("version")).intValue()).isZero();
            resume.countDown();
            assertSuccess(first.get(10, TimeUnit.SECONDS));
            assertCompletedState(request, "MAIN_FINISH");
        } finally {
            resume.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONTENT", "QUESTION", "REQUEST_ID", "OTHER_USER", "NO_TOKEN"})
    void completedResultCannotBypassRequestIdentityOrOwnership(String change) throws Exception {
        var original = seedPath("MAIN_NEXT_MAIN");
        assertSuccess(submit(original, token));
        Map<String, Object> committed = sessionRow();
        var changed = new SubmitInterviewAnswerRequest(change.equals("QUESTION") ? nextMainId : original.interviewQuestionId(),
                change.equals("CONTENT") ? "changed answer" : original.answerContent(),
                change.equals("REQUEST_ID") ? UUID.randomUUID().toString() : original.requestId());
        String caller = token;
        if (change.equals("OTHER_USER")) {
            otherUserId = insertUser();
            caller = tokenFor(otherUserId);
        } else if (change.equals("NO_TOKEN")) {
            caller = null;
        }
        assertThat(submit(changed, caller).status()).isEqualTo(
                change.equals("OTHER_USER") ? 404 : change.equals("NO_TOKEN") ? 401 : 409);
        assertThat(sessionRow()).isEqualTo(committed);
        assertCompletedState(original, "MAIN_NEXT_MAIN");
    }

    @Test
    void earlierAnswerReplaysItsEvaluationWithLatestCompletedSession() throws Exception {
        var original = seedPath("MAIN_NEXT_MAIN");
        HttpResult first = submit(original, token);
        assertSuccess(first);
        assertSuccess(submit(answerFor(nextMainId), token));
        Map<String, Object> completed = sessionRow();

        HttpResult replay = submit(original, token);

        assertSuccess(replay);
        assertThat(replay.body().at("/data/evaluation")).isEqualTo(first.body().at("/data/evaluation"));
        assertThat(replay.body().at("/data/nextAction").asText()).isEqualTo("NEXT_MAIN");
        assertThat(replay.body().at("/data/sessionStatus").asText()).isEqualTo("COMPLETED");
        assertThat(replay.body().at("/data/nextQuestion").isNull()).isTrue();
        assertThat(sessionRow()).isEqualTo(completed);
        assertThat(((Number) completed.get("version")).intValue()).isEqualTo(2);
        assertThat(((Number) completed.get("completed_question_count")).intValue()).isEqualTo(2);
        assertThat(evaluationCount()).isEqualTo(2);
        verify(deepSeek, times(2)).evaluate(any());
    }

    @Test
    void completedFlagWithoutPersistedEvaluationStillFailsInsteadOfFabricatingSuccess() throws Exception {
        var request = seedPath("MAIN_FINISH");
        assertSuccess(submit(request, token));
        jdbc.update("DELETE e FROM answer_evaluation e JOIN interview_answer a ON a.id=e.answer_id WHERE a.request_id=?",
                request.requestId());
        Map<String, Object> completed = sessionRow();

        assertThat(submit(request, token).status()).isEqualTo(500);

        assertThat(sessionRow()).isEqualTo(completed);
        assertThat(evaluationCount()).isZero();
        verify(deepSeek, times(1)).evaluate(any());
    }

    private void pauseFirstRequest() throws InterruptedException {
        if (pausedRequest.get() && paused.getCount() != 0) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            paused.countDown();
            assertThat(resume.await(10, TimeUnit.SECONDS)).as("B must finish before A resumes").isTrue();
        }
    }

    private SubmitInterviewAnswerRequest seedPath(String path) throws Exception {
        boolean hasNext = path.endsWith("NEXT_MAIN");
        String marker = "b1-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO question(category, knowledge_point, difficulty, question_content, reference_answer, status)
                VALUES ('JAVA_COLLECTION', ?, 'MEDIUM', 'Explain HashMap', 'HashMap reference', 'ENABLED')
                """, marker);
        bankQuestionId = jdbc.queryForObject("SELECT id FROM question WHERE knowledge_point=?", Long.class, marker);
        jdbc.update("""
                INSERT INTO interview_session(user_id, difficulty, status, planned_question_count,
                    completed_question_count, report_status, version)
                VALUES (?, 'MEDIUM', 'IN_PROGRESS', ?, 0, 'NOT_STARTED', 0)
                """, userId, hasNext ? 2 : 1);
        sessionId = jdbc.queryForObject("SELECT id FROM interview_session WHERE user_id=?", Long.class, userId);
        mainQuestionId = insertMain(1, "WAITING_ANSWER");
        if (hasNext) nextMainId = insertMain(2, "PENDING");
        jdbc.update("UPDATE interview_session SET current_interview_question_id=? WHERE id=?", mainQuestionId, sessionId);
        long target = mainQuestionId;
        recommendFollowUp = path.equals("MAIN_FOLLOW_UP") || path.startsWith("FOLLOW_UP");
        if (path.startsWith("FOLLOW_UP")) {
            assertSuccess(submit(answerFor(mainQuestionId), token));
            target = ((Number) sessionRow().get("current_interview_question_id")).longValue();
            recommendFollowUp = false;
            priorEvaluations = 1;
            priorVersion = 1;
        }
        return answerFor(target);
    }

    private long insertMain(int order, String state) {
        jdbc.update("""
                INSERT INTO interview_question(session_id, question_id, category, knowledge_point,
                    question_content, reference_answer_snapshot, scoring_points_snapshot, question_type,
                    plan_order, display_order, status)
                VALUES (?, ?, 'JAVA_COLLECTION', 'HashMap', 'Explain HashMap', 'HashMap reference',
                    '[{"id":101,"pointType":"CORE","content":"Core mechanism","weight":100}]',
                    'MAIN', ?, ?, ?)
                """, sessionId, bankQuestionId, order, order * 2 - 1, state);
        return jdbc.queryForObject("SELECT id FROM interview_question WHERE session_id=? AND plan_order=?",
                Long.class, sessionId, order);
    }

    private SubmitInterviewAnswerRequest answerFor(long questionId) {
        return new SubmitInterviewAnswerRequest(questionId, "HashMap uses buckets and a hash function.",
                UUID.randomUUID().toString());
    }

    private DeepSeekEvaluationResult modelResult() throws Exception {
        var suggestion = new LlmEvaluationSuggestion(16, 16, 16, 16, 16,
                List.of("Clear explanation"), List.of("Boundary cases"), "Explain collision handling",
                List.of(new LlmEvaluationSuggestion.ScoringPointResult(101L, !recommendFollowUp, "Fixture")),
                recommendFollowUp, recommendFollowUp ? "Explain collision handling in more detail" : null);
        return new DeepSeekEvaluationResult("deepseek-offline", "stop", json.writeValueAsString(suggestion), suggestion);
    }

    private void assertCompletedState(SubmitInterviewAnswerRequest request, String path) {
        var session = sessionRow();
        boolean followUp = path.equals("MAIN_FOLLOW_UP");
        boolean finished = path.endsWith("FINISH");
        assertThat(answerState(request)).isEqualTo("EVALUATED");
        assertThat(evaluationCount()).isEqualTo(priorEvaluations + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_answer a JOIN interview_question q "
                + "ON q.id=a.interview_question_id WHERE q.session_id=?", Integer.class, sessionId))
                .isEqualTo(priorEvaluations + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rag_retrieval_batch b JOIN interview_answer a "
                + "ON a.id=b.answer_id JOIN interview_question q ON q.id=a.interview_question_id WHERE q.session_id=?",
                Integer.class, sessionId)).isEqualTo(priorEvaluations + 1);
        assertThat(((Number) session.get("version")).intValue()).isEqualTo(priorVersion + 1);
        assertThat(((Number) session.get("completed_question_count")).intValue()).isEqualTo(followUp ? 0 : 1);
        assertThat(session.get("status")).isEqualTo(finished ? "COMPLETED" : "IN_PROGRESS");
        if (finished) assertThat(session.get("current_interview_question_id")).isNull();
        else if (!followUp) assertThat(((Number) session.get("current_interview_question_id")).longValue()).isEqualTo(nextMainId);
        else assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_question WHERE parent_question_id=? "
                + "AND id=? AND status='WAITING_ANSWER'", Integer.class, mainQuestionId,
                session.get("current_interview_question_id"))).isEqualTo(1);
        verify(deepSeek, times(priorEvaluations + 1)).evaluate(any());
        verify(embedding, times(priorEvaluations + 1)).embed(any());
        verify(vectors, times(priorEvaluations + 1)).search(any(), anyString(), anyInt());
    }

    private HttpResult submit(SubmitInterviewAnswerRequest request, String callerToken) throws Exception {
        var builder = post("/api/interviews/" + sessionId + "/answers")
                .contentType("application/json").content(json.writeValueAsString(request));
        if (callerToken != null) builder.header("Authorization", "Bearer " + callerToken);
        var response = mvc.perform(builder).andReturn().getResponse();
        return new HttpResult(response.getStatus(), json.readTree(response.getContentAsString()));
    }

    private void assertSuccess(HttpResult result) {
        assertThat(result.status()).as(result.body().toString()).isEqualTo(200);
        assertThat(result.body().path("code").asInt()).isEqualTo(200);
    }

    private Map<String, Object> sessionRow() {
        return jdbc.queryForMap("SELECT * FROM interview_session WHERE id=?", sessionId);
    }

    private String answerState(SubmitInterviewAnswerRequest request) {
        return jdbc.queryForObject("SELECT status FROM interview_answer WHERE request_id=?", String.class, request.requestId());
    }

    private Map<String, Object> evaluationRow(SubmitInterviewAnswerRequest request) {
        return jdbc.queryForMap("SELECT e.* FROM answer_evaluation e JOIN interview_answer a ON a.id=e.answer_id "
                + "WHERE a.request_id=?", request.requestId());
    }

    private int evaluationCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM answer_evaluation e JOIN interview_question q "
                + "ON q.id=e.main_interview_question_id WHERE q.session_id=?", Integer.class, sessionId);
    }

    private long insertUser() {
        String account = "b1-" + UUID.randomUUID();
        jdbc.update("INSERT INTO `user`(account, username, password, email, role, status) "
                + "VALUES (?, 'B1 test', 'test-password-hash', ?, 'USER', 'ENABLED')", account, account + "@example.com");
        return jdbc.queryForObject("SELECT id FROM `user` WHERE account=?", Long.class, account);
    }

    private String tokenFor(long id) {
        return tokens.generateAccessToken(id, jdbc.queryForObject("SELECT account FROM `user` WHERE id=?", String.class, id),
                UserRole.USER, "test-password-hash");
    }

    @AfterEach
    void cleanUp() {
        resume.countDown();
        if (sessionId != null) {
            for (String table : List.of("rag_hit_log", "rag_retrieval_batch", "answer_evaluation")) {
                jdbc.update("DELETE t FROM " + table + " t JOIN interview_answer a ON a.id=t.answer_id "
                        + "JOIN interview_question q ON q.id=a.interview_question_id WHERE q.session_id=?", sessionId);
            }
            jdbc.update("DELETE a FROM interview_answer a JOIN interview_question q ON q.id=a.interview_question_id "
                    + "WHERE q.session_id=?", sessionId);
            jdbc.update("DELETE FROM interview_question WHERE session_id=? AND question_type='FOLLOW_UP'", sessionId);
            jdbc.update("DELETE FROM interview_question WHERE session_id=?", sessionId);
            jdbc.update("DELETE FROM interview_session WHERE id=?", sessionId);
        }
        if (bankQuestionId != null) jdbc.update("DELETE FROM question WHERE id=?", bankQuestionId);
        if (userId != null) jdbc.update("DELETE FROM `user` WHERE id=?", userId);
        if (otherUserId != null) jdbc.update("DELETE FROM `user` WHERE id=?", otherUserId);
    }

    private record HttpResult(int status, JsonNode body) { }
}
