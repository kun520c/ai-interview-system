package com.kun.aiinterview.interview.mapper;

import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class InterviewSessionMapperTest {

    @Autowired
    private InterviewSessionMapper interviewSessionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldMapAllSessionColumns() {
        long userId = insertUser();
        long sessionId = insertSession(
                userId,
                "HARD",
                "IN_PROGRESS",
                3,
                1,
                new BigDecimal("42.50"),
                "GENERATING",
                5
        );
        long currentQuestionId = insertMainQuestion(
                sessionId,
                insertQuestion(),
                2,
                3,
                "WAITING_ANSWER"
        );
        setCurrentQuestion(sessionId, currentQuestionId);

        InterviewSession found = interviewSessionMapper
                .getInterviewSessionById(sessionId);

        assertNotNull(found);
        assertAll(
                () -> assertEquals(sessionId, found.getId()),
                () -> assertEquals(userId, found.getUserId()),
                () -> assertEquals(QuestionDifficulty.HARD, found.getDifficulty()),
                () -> assertEquals(InterviewSessionStatus.IN_PROGRESS, found.getStatus()),
                () -> assertEquals(currentQuestionId, found.getCurrentInterviewQuestionId()),
                () -> assertEquals(3, found.getPlannedQuestionCount()),
                () -> assertEquals(1, found.getCompletedQuestionCount()),
                () -> assertEquals(new BigDecimal("42.50"), found.getTotalScore()),
                () -> assertEquals(InterviewReportStatus.GENERATING, found.getReportStatus()),
                () -> assertEquals(5, found.getVersion()),
                () -> assertNotNull(found.getStartedAt()),
                () -> assertNull(found.getEndedAt()),
                () -> assertNotNull(found.getCreatedAt()),
                () -> assertNotNull(found.getUpdatedAt())
        );
    }

    @Test
    void shouldMoveToFollowUpOnlyWhenVersionAndCurrentQuestionBothMatch() {
        long userId = insertUser();
        long sessionId = insertSession(
                userId,
                "MEDIUM",
                "IN_PROGRESS",
                2,
                0,
                null,
                "NOT_STARTED",
                4
        );
        long mainQuestionId = insertMainQuestion(
                sessionId,
                insertQuestion(),
                1,
                1,
                "ANSWERED"
        );
        long followUpQuestionId = insertFollowUpQuestion(
                sessionId,
                mainQuestionId,
                2
        );
        setCurrentQuestion(sessionId, mainQuestionId);

        assertEquals(
                0,
                interviewSessionMapper.moveToFollowUp(
                        sessionId,
                        3,
                        mainQuestionId,
                        followUpQuestionId
                )
        );
        assertEquals(
                0,
                interviewSessionMapper.moveToFollowUp(
                        sessionId,
                        4,
                        followUpQuestionId,
                        followUpQuestionId
                )
        );
        assertEquals(
                1,
                interviewSessionMapper.moveToFollowUp(
                        sessionId,
                        4,
                        mainQuestionId,
                        followUpQuestionId
                )
        );

        InterviewSession moved = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        assertAll(
                () -> assertEquals(followUpQuestionId, moved.getCurrentInterviewQuestionId()),
                () -> assertEquals(0, moved.getCompletedQuestionCount()),
                () -> assertEquals(5, moved.getVersion()),
                () -> assertEquals(InterviewSessionStatus.IN_PROGRESS, moved.getStatus())
        );
        assertEquals(
                0,
                interviewSessionMapper.moveToFollowUp(
                        sessionId,
                        4,
                        mainQuestionId,
                        followUpQuestionId
                )
        );
    }

    @Test
    void shouldAdvanceToNextMainOnceOnlyWhenVersionAndCurrentQuestionBothMatch() {
        long userId = insertUser();
        long sessionId = insertSession(
                userId,
                "MEDIUM",
                "IN_PROGRESS",
                2,
                0,
                null,
                "NOT_STARTED",
                2
        );
        long currentQuestionId = insertMainQuestion(
                sessionId,
                insertQuestion(),
                1,
                1,
                "ANSWERED"
        );
        long nextQuestionId = insertMainQuestion(
                sessionId,
                insertQuestion(),
                2,
                3,
                "PENDING"
        );
        setCurrentQuestion(sessionId, currentQuestionId);

        assertEquals(
                0,
                interviewSessionMapper.advanceToNextMain(
                        sessionId,
                        1,
                        currentQuestionId,
                        nextQuestionId
                )
        );
        assertEquals(
                0,
                interviewSessionMapper.advanceToNextMain(
                        sessionId,
                        2,
                        nextQuestionId,
                        nextQuestionId
                )
        );
        assertEquals(
                1,
                interviewSessionMapper.advanceToNextMain(
                        sessionId,
                        2,
                        currentQuestionId,
                        nextQuestionId
                )
        );

        InterviewSession advanced = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        assertAll(
                () -> assertEquals(nextQuestionId, advanced.getCurrentInterviewQuestionId()),
                () -> assertEquals(1, advanced.getCompletedQuestionCount()),
                () -> assertEquals(3, advanced.getVersion()),
                () -> assertEquals(InterviewSessionStatus.IN_PROGRESS, advanced.getStatus())
        );
        assertEquals(
                0,
                interviewSessionMapper.advanceToNextMain(
                        sessionId,
                        2,
                        currentQuestionId,
                        nextQuestionId
                )
        );
    }

    @Test
    void shouldCompleteSessionOnceOnlyWhenVersionAndCurrentQuestionBothMatch() {
        long userId = insertUser();
        long sessionId = insertSession(
                userId,
                "EASY",
                "IN_PROGRESS",
                1,
                0,
                null,
                "NOT_STARTED",
                6
        );
        long currentQuestionId = insertMainQuestion(
                sessionId,
                insertQuestion(),
                1,
                1,
                "ANSWERED"
        );
        setCurrentQuestion(sessionId, currentQuestionId);

        assertEquals(
                0,
                interviewSessionMapper.completeSession(
                        sessionId,
                        5,
                        currentQuestionId
                )
        );
        assertEquals(
                0,
                interviewSessionMapper.completeSession(
                        sessionId,
                        6,
                        Long.MAX_VALUE
                )
        );
        assertEquals(
                1,
                interviewSessionMapper.completeSession(
                        sessionId,
                        6,
                        currentQuestionId
                )
        );

        InterviewSession completed = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        assertAll(
                () -> assertNull(completed.getCurrentInterviewQuestionId()),
                () -> assertEquals(1, completed.getCompletedQuestionCount()),
                () -> assertEquals(7, completed.getVersion()),
                () -> assertEquals(InterviewSessionStatus.COMPLETED, completed.getStatus()),
                () -> assertNotNull(completed.getEndedAt())
        );
        assertEquals(
                0,
                interviewSessionMapper.completeSession(
                        sessionId,
                        6,
                        currentQuestionId
                )
        );
    }

    private long insertUser() {
        String uniqueValue = uniqueValue();
        String account = "session-" + uniqueValue;
        jdbcTemplate.update(
                """
                INSERT INTO `user` (account, username, password, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 'ENABLED')
                """,
                account,
                "Session Mapper 测试用户",
                "test-password-hash",
                account + "@example.com"
        );
        return requiredId("SELECT id FROM `user` WHERE account = ?", account);
    }

    private long insertQuestion() {
        String knowledgePoint = "Session-CAS-" + uniqueValue();
        jdbcTemplate.update(
                """
                INSERT INTO question
                    (category, knowledge_point, difficulty, question_content,
                     reference_answer, status)
                VALUES ('JAVA_COLLECTION', ?, 'MEDIUM', ?, ?, 'ENABLED')
                """,
                knowledgePoint,
                "请说明当前测试问题",
                "当前测试问题的参考答案"
        );
        return requiredId(
                "SELECT id FROM question WHERE knowledge_point = ?",
                knowledgePoint
        );
    }

    private long insertSession(
            long userId,
            String difficulty,
            String status,
            int plannedQuestionCount,
            int completedQuestionCount,
            BigDecimal totalScore,
            String reportStatus,
            int version
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO interview_session
                    (user_id, difficulty, status, planned_question_count,
                     completed_question_count, total_score, report_status,
                     version, started_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                userId,
                difficulty,
                status,
                plannedQuestionCount,
                completedQuestionCount,
                totalScore,
                reportStatus,
                version
        );
        return requiredId(
                "SELECT id FROM interview_session WHERE user_id = ?",
                userId
        );
    }

    private long insertMainQuestion(
            long sessionId,
            long questionId,
            int planOrder,
            int displayOrder,
            String status
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO interview_question
                    (session_id, question_id, category, knowledge_point,
                     question_content, reference_answer_snapshot,
                     scoring_points_snapshot, question_type, parent_question_id,
                     follow_up_target_points, plan_order, display_order, status)
                VALUES (?, ?, 'JAVA_COLLECTION', 'Session CAS', ?, ?, ?, 'MAIN',
                        NULL, NULL, ?, ?, ?)
                """,
                sessionId,
                questionId,
                "请说明当前测试问题",
                "当前测试问题的参考答案快照",
                "[{\"scoringPointId\":101,\"pointType\":\"CORE\",\"weight\":100}]",
                planOrder,
                displayOrder,
                status
        );
        return requiredId(
                """
                SELECT id FROM interview_question
                WHERE session_id = ? AND display_order = ?
                """,
                sessionId,
                displayOrder
        );
    }

    private long insertFollowUpQuestion(
            long sessionId,
            long parentQuestionId,
            int displayOrder
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO interview_question
                    (session_id, question_id, category, knowledge_point,
                     question_content, reference_answer_snapshot,
                     scoring_points_snapshot, question_type, parent_question_id,
                     follow_up_target_points, plan_order, display_order, status)
                VALUES (?, NULL, 'JAVA_COLLECTION', 'Session CAS 追问', ?, NULL,
                        NULL, 'FOLLOW_UP', ?, '[101]', NULL, ?, 'WAITING_ANSWER')
                """,
                sessionId,
                "请进一步说明当前测试问题",
                parentQuestionId,
                displayOrder
        );
        return requiredId(
                """
                SELECT id FROM interview_question
                WHERE session_id = ? AND display_order = ?
                """,
                sessionId,
                displayOrder
        );
    }

    private void setCurrentQuestion(long sessionId, long questionId) {
        assertEquals(
                1,
                jdbcTemplate.update(
                        """
                        UPDATE interview_session
                        SET current_interview_question_id = ?
                        WHERE id = ?
                        """,
                        questionId,
                        sessionId
                )
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
