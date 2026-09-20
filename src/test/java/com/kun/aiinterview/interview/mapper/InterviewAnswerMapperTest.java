package com.kun.aiinterview.interview.mapper;

import com.kun.aiinterview.interview.entity.InterviewAnswer;
import com.kun.aiinterview.interview.enums.InterviewAnswerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class InterviewAnswerMapperTest {

    private static final String LONG_ERROR_CODE = "LLM_RESULT_VALIDATION_FAILED";

    @Autowired
    private InterviewAnswerMapper interviewAnswerMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldFindAnswerByIdAndPreserveLongErrorCode() {
        long interviewQuestionId = insertMainInterviewQuestion();
        long answerId = insertInterviewAnswer(
                interviewQuestionId,
                "回答包含 HashMap 的数组、链表和红黑树结构。",
                "FAILED",
                LONG_ERROR_CODE
        );

        InterviewAnswer found = interviewAnswerMapper.getInterviewAnswerById(answerId);
        Integer errorCodeLength = jdbcTemplate.queryForObject(
                """
                SELECT CHARACTER_MAXIMUM_LENGTH
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'interview_answer'
                  AND column_name = 'error_code'
                """,
                Integer.class
        );

        assertNotNull(found);
        assertAll(
                () -> assertEquals(answerId, found.getId()),
                () -> assertEquals(interviewQuestionId, found.getInterviewQuestionId()),
                () -> assertEquals(
                        "回答包含 HashMap 的数组、链表和红黑树结构。",
                        found.getAnswerContent()
                ),
                () -> assertEquals(InterviewAnswerStatus.FAILED, found.getStatus()),
                () -> assertNotNull(found.getRequestId()),
                () -> assertEquals(LONG_ERROR_CODE, found.getErrorCode()),
                () -> assertNotNull(found.getSubmittedAt()),
                () -> assertNotNull(found.getCreatedAt()),
                () -> assertNotNull(found.getUpdatedAt()),
                () -> assertEquals(50, errorCodeLength)
        );
    }

    @Test
    void shouldFindAnswerByInterviewQuestionIdWithoutReturningAnotherQuestionsAnswer() {
        long answeredQuestionId = insertMainInterviewQuestion();
        long unansweredQuestionId = insertMainInterviewQuestion();
        long answerId = insertInterviewAnswer(
                answeredQuestionId,
                "根据问题 ID 查询的回答",
                "SUBMITTED",
                null
        );

        InterviewAnswer found = interviewAnswerMapper
                .getInterviewAnswerByInterviewQuestionId(answeredQuestionId);
        InterviewAnswer unrelated = interviewAnswerMapper
                .getInterviewAnswerByInterviewQuestionId(unansweredQuestionId);

        assertNotNull(found);
        assertAll(
                () -> assertEquals(answerId, found.getId()),
                () -> assertEquals(answeredQuestionId, found.getInterviewQuestionId()),
                () -> assertEquals("根据问题 ID 查询的回答", found.getAnswerContent()),
                () -> assertEquals(InterviewAnswerStatus.SUBMITTED, found.getStatus()),
                () -> assertNull(found.getErrorCode()),
                () -> assertNull(unrelated)
        );
    }

    @Test
    void shouldInsertAnswerPopulateGeneratedKeyAndFindItByRequestId() {
        long interviewQuestionId = insertMainInterviewQuestion();
        String requestId = "workflow-answer-" + uniqueValue();
        LocalDateTime submittedAt = LocalDateTime.now().withNano(0);
        InterviewAnswer answer = InterviewAnswer.builder()
                .interviewQuestionId(interviewQuestionId)
                .answerContent("通过 requestId 验证回答提交幂等查询。")
                .status(InterviewAnswerStatus.SUBMITTED)
                .requestId(requestId)
                .errorCode(null)
                .submittedAt(submittedAt)
                .build();

        int affectedRows = interviewAnswerMapper.insertInterviewAnswer(answer);
        InterviewAnswer found = interviewAnswerMapper
                .getInterviewAnswerByRequestId(requestId);

        assertEquals(1, affectedRows);
        assertNotNull(answer.getId());
        assertNotNull(found);
        assertAll(
                () -> assertEquals(answer.getId(), found.getId()),
                () -> assertEquals(interviewQuestionId, found.getInterviewQuestionId()),
                () -> assertEquals(answer.getAnswerContent(), found.getAnswerContent()),
                () -> assertEquals(InterviewAnswerStatus.SUBMITTED, found.getStatus()),
                () -> assertEquals(requestId, found.getRequestId()),
                () -> assertNull(found.getErrorCode()),
                () -> assertEquals(submittedAt, found.getSubmittedAt()),
                () -> assertNotNull(found.getCreatedAt()),
                () -> assertNotNull(found.getUpdatedAt())
        );
    }

    @Test
    void shouldApplyEvaluationStateChangesOnlyFromExpectedStatuses() {
        long submittedQuestionId = insertMainInterviewQuestion();
        long submittedAnswerId = insertInterviewAnswer(
                submittedQuestionId,
                "首次提交后等待评价。",
                "SUBMITTED",
                "STALE_ERROR"
        );

        assertEquals(0, interviewAnswerMapper.retryEvaluation(submittedAnswerId));
        assertEquals(1, interviewAnswerMapper.claimEvaluation(submittedAnswerId));
        InterviewAnswer evaluatingFromSubmitted = interviewAnswerMapper
                .getInterviewAnswerById(submittedAnswerId);
        assertEquals(InterviewAnswerStatus.EVALUATING, evaluatingFromSubmitted.getStatus());
        assertNull(evaluatingFromSubmitted.getErrorCode());
        assertEquals(0, interviewAnswerMapper.claimEvaluation(submittedAnswerId));
        assertEquals(0, interviewAnswerMapper.retryEvaluation(submittedAnswerId));

        assertEquals(1, interviewAnswerMapper.markEvaluated(submittedAnswerId));
        assertEquals(
                InterviewAnswerStatus.EVALUATED,
                interviewAnswerMapper.getInterviewAnswerById(submittedAnswerId).getStatus()
        );
        assertEquals(0, interviewAnswerMapper.markEvaluated(submittedAnswerId));
        assertEquals(
                0,
                interviewAnswerMapper.markFailed(submittedAnswerId, "LATE_FAILURE")
        );

        long failedQuestionId = insertMainInterviewQuestion();
        long failedAnswerId = insertInterviewAnswer(
                failedQuestionId,
                "上一次评价失败，等待重试。",
                "FAILED",
                "LLM_TIMEOUT"
        );

        assertEquals(0, interviewAnswerMapper.claimEvaluation(failedAnswerId));
        assertEquals(1, interviewAnswerMapper.retryEvaluation(failedAnswerId));
        InterviewAnswer evaluatingFromFailed = interviewAnswerMapper
                .getInterviewAnswerById(failedAnswerId);
        assertEquals(InterviewAnswerStatus.EVALUATING, evaluatingFromFailed.getStatus());
        assertNull(evaluatingFromFailed.getErrorCode());

        assertEquals(
                1,
                interviewAnswerMapper.markFailed(
                        failedAnswerId,
                        "LLM_RESULT_VALIDATION_FAILED"
                )
        );
        InterviewAnswer failed = interviewAnswerMapper
                .getInterviewAnswerById(failedAnswerId);
        assertEquals(InterviewAnswerStatus.FAILED, failed.getStatus());
        assertEquals("LLM_RESULT_VALIDATION_FAILED", failed.getErrorCode());
        assertEquals(
                0,
                interviewAnswerMapper.markFailed(failedAnswerId, "SECOND_FAILURE")
        );
        assertEquals(0, interviewAnswerMapper.markEvaluated(failedAnswerId));
    }

    @Test
    void shouldReclaimStaleEvaluatingOnlyWhenCutoffCoversUpdatedAt() {
        long staleAnswerId = insertInterviewAnswer(
                insertMainInterviewQuestion(),
                "过期 EVALUATING 等待恢复。",
                "EVALUATING",
                "STALE_LEASE"
        );
        long freshAnswerId = insertInterviewAnswer(
                insertMainInterviewQuestion(),
                "仍在窗口内的 EVALUATING。",
                "EVALUATING",
                null
        );
        LocalDateTime staleAt = LocalDateTime.now().minusMinutes(20);
        LocalDateTime freshAt = LocalDateTime.now();
        setAnswerUpdatedAt(staleAnswerId, staleAt);
        setAnswerUpdatedAt(freshAnswerId, freshAt);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);

        assertEquals(
                1,
                interviewAnswerMapper.reclaimStaleEvaluating(
                        staleAnswerId,
                        cutoff
                )
        );
        InterviewAnswer reclaimed = interviewAnswerMapper
                .getInterviewAnswerById(staleAnswerId);
        assertEquals(InterviewAnswerStatus.EVALUATING, reclaimed.getStatus());
        assertNull(reclaimed.getErrorCode());
        assertEquals(
                0,
                interviewAnswerMapper.reclaimStaleEvaluating(
                        staleAnswerId,
                        cutoff
                )
        );
        assertEquals(
                0,
                interviewAnswerMapper.reclaimStaleEvaluating(
                        freshAnswerId,
                        cutoff
                )
        );
        assertEquals(
                InterviewAnswerStatus.EVALUATING,
                interviewAnswerMapper.getInterviewAnswerById(freshAnswerId)
                        .getStatus()
        );
    }

    @Test
    void shouldReturnNullForMissingAnswerId() {
        assertNull(interviewAnswerMapper.getInterviewAnswerById(Long.MAX_VALUE));
    }

    private long insertMainInterviewQuestion() {
        long userId = insertUser();
        long questionId = insertQuestion();
        long sessionId = insertInterviewSession(userId);
        jdbcTemplate.update(
                """
                INSERT INTO interview_question
                    (session_id, question_id, category, knowledge_point,
                     question_content, reference_answer_snapshot,
                     scoring_points_snapshot, question_type, parent_question_id,
                     follow_up_target_points, plan_order, display_order, status)
                VALUES (?, ?, 'JAVA_COLLECTION', 'HashMap', ?, ?, ?, 'MAIN',
                        NULL, NULL, 1, 1, 'ANSWERED')
                """,
                sessionId,
                questionId,
                "请说明 HashMap 的核心机制",
                "本场面试参考答案快照",
                "[{\"scoringPointId\":101,\"weight\":100}]"
        );
        return requiredId(
                "SELECT id FROM interview_question WHERE session_id = ? AND display_order = 1",
                sessionId
        );
    }

    private long insertInterviewAnswer(
            long interviewQuestionId,
            String answerContent,
            String status,
            String errorCode
    ) {
        String requestId = "e1aa-" + uniqueValue();
        jdbcTemplate.update(
                """
                INSERT INTO interview_answer
                    (interview_question_id, answer_content, status, request_id, error_code)
                VALUES (?, ?, ?, ?, ?)
                """,
                interviewQuestionId,
                answerContent,
                status,
                requestId,
                errorCode
        );
        return requiredId(
                "SELECT id FROM interview_answer WHERE request_id = ?",
                requestId
        );
    }

    private void setAnswerUpdatedAt(long answerId, LocalDateTime updatedAt) {
        assertEquals(
                1,
                jdbcTemplate.update(
                        """
                        UPDATE interview_answer
                        SET updated_at = ?
                        WHERE id = ?
                        """,
                        Timestamp.valueOf(updatedAt),
                        answerId
                )
        );
    }

    private long insertUser() {
        String uniqueValue = uniqueValue();
        String account = "e1aa-" + uniqueValue;
        jdbcTemplate.update(
                """
                INSERT INTO `user` (account, username, password, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 'ENABLED')
                """,
                account,
                "E1-A回答测试用户",
                "test-password-hash",
                account + "@example.com"
        );
        return requiredId("SELECT id FROM `user` WHERE account = ?", account);
    }

    private long insertQuestion() {
        String knowledgePoint = "HashMap-" + uniqueValue();
        jdbcTemplate.update(
                """
                INSERT INTO question
                    (category, knowledge_point, difficulty, question_content,
                     reference_answer, status)
                VALUES ('JAVA_COLLECTION', ?, 'MEDIUM', ?, ?, 'ENABLED')
                """,
                knowledgePoint,
                "请说明 HashMap 的核心机制",
                "题库原始参考答案"
        );
        return requiredId(
                "SELECT id FROM question WHERE knowledge_point = ?",
                knowledgePoint
        );
    }

    private long insertInterviewSession(long userId) {
        jdbcTemplate.update(
                """
                INSERT INTO interview_session
                    (user_id, difficulty, status, planned_question_count,
                     completed_question_count, report_status)
                VALUES (?, 'MEDIUM', 'CREATED', 1, 0, 'NOT_STARTED')
                """,
                userId
        );
        return requiredId(
                "SELECT id FROM interview_session WHERE user_id = ?",
                userId
        );
    }

    private long requiredId(String sql, Object... args) {
        Long id = jdbcTemplate.queryForObject(sql, Long.class, args);
        assertNotNull(id);
        return id;
    }

    private String uniqueValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
