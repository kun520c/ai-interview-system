package com.kun.aiinterview.common.recovery;

import com.kun.aiinterview.interview.mapper.InterviewAnswerMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.knowledge.entity.KnowledgeDocument;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.enums.KnowledgeFileType;
import com.kun.aiinterview.knowledge.enums.KnowledgeProcessingStatus;
import com.kun.aiinterview.knowledge.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class StaleRecoveryCasIntegrationTest {

    @Autowired
    private InterviewAnswerMapper interviewAnswerMapper;

    @Autowired
    private InterviewSessionMapper interviewSessionMapper;

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long questionId;
    private Long sessionId;
    private Long interviewQuestionId;
    private Long answerId;
    private Long documentId;

    @AfterEach
    void cleanUp() {
        if (answerId != null) {
            jdbcTemplate.update(
                    "DELETE FROM interview_answer WHERE id = ?",
                    answerId
            );
        }
        if (interviewQuestionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM interview_question WHERE id = ?",
                    interviewQuestionId
            );
        }
        if (sessionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM interview_session WHERE id = ?",
                    sessionId
            );
        }
        if (questionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM question WHERE id = ?",
                    questionId
            );
        }
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        if (documentId != null) {
            jdbcTemplate.update(
                    "DELETE FROM knowledge_document WHERE id = ?",
                    documentId
            );
        }
    }

    @Test
    void concurrentStaleEvaluatingReclaimShouldHaveSingleWinner()
            throws Exception {
        seedEvaluatingAnswer();
        setUpdatedAt("interview_answer", answerId, minutesAgo(20));
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);

        List<Integer> results = runConcurrentCas(() ->
                interviewAnswerMapper.reclaimStaleEvaluating(
                        answerId,
                        cutoff
                )
        );

        assertThat(results).containsExactlyInAnyOrder(1, 0);
        assertThat(interviewAnswerMapper.getInterviewAnswerById(answerId)
                .getStatus().name()).isEqualTo("EVALUATING");
    }

    @Test
    void concurrentStaleGeneratingReclaimShouldHaveSingleWinner()
            throws Exception {
        seedGeneratingSession();
        setUpdatedAt("interview_session", sessionId, minutesAgo(20));
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);

        List<Integer> results = runConcurrentCas(() ->
                interviewSessionMapper.reclaimStaleGenerating(
                        sessionId,
                        cutoff
                )
        );

        assertThat(results).containsExactlyInAnyOrder(1, 0);
        assertThat(interviewSessionMapper.getInterviewSessionById(sessionId)
                .getReportStatus().name()).isEqualTo("GENERATING");
        assertThat(interviewSessionMapper.getInterviewSessionById(sessionId)
                .getVersion()).isEqualTo(5);
    }

    @Test
    void concurrentStaleProcessingReclaimShouldHaveSingleWinner()
            throws Exception {
        seedProcessingDocument();
        setUpdatedAt("knowledge_document", documentId, minutesAgo(20));
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);

        List<Integer> results = runConcurrentCas(() ->
                knowledgeDocumentMapper.reclaimStaleProcessing(
                        documentId,
                        cutoff
                )
        );

        assertThat(results).containsExactlyInAnyOrder(1, 0);
        assertThat(knowledgeDocumentMapper.selectById(documentId)
                .getProcessingStatus())
                .isEqualTo(KnowledgeProcessingStatus.PROCESSING);
    }

    private List<Integer> runConcurrentCas(Callable<Integer> cas)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() ->
                    awaitAndRun(ready, start, cas)
            );
            Future<Integer> second = executor.submit(() ->
                    awaitAndRun(ready, start, cas)
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private Integer awaitAndRun(
            CountDownLatch ready,
            CountDownLatch start,
            Callable<Integer> cas
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("CAS start latch timed out");
        }
        return cas.call();
    }

    private void seedEvaluatingAnswer() {
        userId = insertUser();
        questionId = insertQuestion();
        sessionId = insertSession("IN_PROGRESS", "NOT_STARTED", 1);
        interviewQuestionId = insertMainQuestion();
        String requestId = "recovery-" + uniqueValue();
        jdbcTemplate.update(
                """
                INSERT INTO interview_answer
                    (interview_question_id, answer_content, status, request_id)
                VALUES (?, ?, 'EVALUATING', ?)
                """,
                interviewQuestionId,
                "过期 EVALUATING 并发恢复",
                requestId
        );
        answerId = requiredId(
                "SELECT id FROM interview_answer WHERE request_id = ?",
                requestId
        );
    }

    private void seedGeneratingSession() {
        userId = insertUser();
        sessionId = insertSession("COMPLETED", "GENERATING", 4);
    }

    private void seedProcessingDocument() {
        KnowledgeDocument document = KnowledgeDocument.builder()
                .title("Recovery CAS")
                .category(KnowledgeCategory.JAVA_COLLECTION)
                .fileName("recovery.md")
                .fileType(KnowledgeFileType.MARKDOWN)
                .content("recovery content")
                .contentHash(uniqueValue())
                .documentVersion(1)
                .processingStatus(KnowledgeProcessingStatus.UPLOADED)
                .build();
        assertThat(knowledgeDocumentMapper.insertDocument(document)).isEqualTo(1);
        documentId = document.getId();
        assertThat(knowledgeDocumentMapper.claimProcessing(documentId))
                .isEqualTo(1);
    }

    private long insertUser() {
        String account = "recovery-" + uniqueValue();
        jdbcTemplate.update(
                """
                INSERT INTO `user` (account, username, password, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 'ENABLED')
                """,
                account,
                "Recovery CAS 用户",
                "test-password-hash",
                account + "@example.com"
        );
        return requiredId("SELECT id FROM `user` WHERE account = ?", account);
    }

    private long insertQuestion() {
        String knowledgePoint = "Recovery-" + uniqueValue();
        jdbcTemplate.update(
                """
                INSERT INTO question
                    (category, knowledge_point, difficulty, question_content,
                     reference_answer, status)
                VALUES ('JAVA_COLLECTION', ?, 'MEDIUM', ?, ?, 'ENABLED')
                """,
                knowledgePoint,
                "请说明恢复测试问题",
                "恢复测试参考答案"
        );
        return requiredId(
                "SELECT id FROM question WHERE knowledge_point = ?",
                knowledgePoint
        );
    }

    private long insertSession(
            String status,
            String reportStatus,
            int version
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO interview_session
                    (user_id, difficulty, status, planned_question_count,
                     completed_question_count, report_status, version)
                VALUES (?, 'MEDIUM', ?, 1, 0, ?, ?)
                """,
                userId,
                status,
                reportStatus,
                version
        );
        return requiredId(
                "SELECT id FROM interview_session WHERE user_id = ?",
                userId
        );
    }

    private long insertMainQuestion() {
        jdbcTemplate.update(
                """
                INSERT INTO interview_question
                    (session_id, question_id, category, knowledge_point,
                     question_content, reference_answer_snapshot,
                     scoring_points_snapshot, question_type, parent_question_id,
                     follow_up_target_points, plan_order, display_order, status)
                VALUES (?, ?, 'JAVA_COLLECTION', 'Recovery', ?, ?, ?, 'MAIN',
                        NULL, NULL, 1, 1, 'ANSWERED')
                """,
                sessionId,
                questionId,
                "请说明恢复测试问题",
                "恢复测试参考答案快照",
                "[{\"id\":101,\"pointType\":\"CORE\",\"weight\":100}]"
        );
        return requiredId(
                """
                SELECT id FROM interview_question
                WHERE session_id = ? AND display_order = 1
                """,
                sessionId
        );
    }

    private void setUpdatedAt(
            String table,
            long id,
            LocalDateTime updatedAt
    ) {
        int updated = jdbcTemplate.update(
                "UPDATE " + table + " SET updated_at = ? WHERE id = ?",
                Timestamp.valueOf(updatedAt),
                id
        );
        assertThat(updated).isEqualTo(1);
    }

    private LocalDateTime minutesAgo(int minutes) {
        return LocalDateTime.now().minusMinutes(minutes);
    }

    private long requiredId(String sql, Object... args) {
        Long id = jdbcTemplate.queryForObject(sql, Long.class, args);
        assertThat(id).isNotNull();
        return id;
    }

    private String uniqueValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
