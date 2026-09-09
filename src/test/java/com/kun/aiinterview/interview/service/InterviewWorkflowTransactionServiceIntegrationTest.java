package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewAnswerStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class InterviewWorkflowTransactionServiceIntegrationTest {

    @Autowired
    private InterviewWorkflowTransactionService transactionService;

    @Autowired
    private InterviewQuestionMapper interviewQuestionMapper;

    @Autowired
    private InterviewSessionMapper interviewSessionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long sessionId;
    private Long currentQuestionId;
    private Long nextQuestionId;
    private Long currentQuestionBankId;
    private Long nextQuestionBankId;
    private Long answerId;

    @AfterEach
    void cleanUp() {
        if (answerId != null) {
            jdbcTemplate.update(
                    "DELETE FROM interview_answer WHERE id = ?",
                    answerId
            );
        }

        if (currentQuestionId != null || nextQuestionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM interview_question WHERE session_id = ?",
                    sessionId
            );
        }

        if (sessionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM interview_session WHERE id = ?",
                    sessionId
            );
        }

        if (currentQuestionBankId != null) {
            jdbcTemplate.update(
                    "DELETE FROM question WHERE id = ?",
                    currentQuestionBankId
            );
        }

        if (nextQuestionBankId != null) {
            jdbcTemplate.update(
                    "DELETE FROM question WHERE id = ?",
                    nextQuestionBankId
            );
        }

        if (userId != null) {
            jdbcTemplate.update(
                    "DELETE FROM `user` WHERE id = ?",
                    userId
            );
        }
    }

    @Test
    void shouldRollbackEarlierUpdatesWhenSessionCasFails() {
        prepareWorkflowState();

        InterviewQuestion currentQuestion =
                interviewQuestionMapper.getInterviewQuestionById(
                        currentQuestionId
                );
        InterviewQuestion nextQuestion =
                interviewQuestionMapper.getInterviewQuestionById(
                        nextQuestionId
                );
        InterviewSession staleSession =
                interviewSessionMapper.getInterviewSessionById(sessionId);
        staleSession.setVersion(staleSession.getVersion() + 1);

        assertThatThrownBy(
                () -> transactionService.advanceToNextMain(
                        answerId,
                        currentQuestion,
                        staleSession,
                        nextQuestion
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session推进到下一道MAIN失败");

        assertThat(answerStatus())
                .isEqualTo(InterviewAnswerStatus.EVALUATING.name());
        assertThat(questionStatus(nextQuestionId))
                .isEqualTo(InterviewQuestionStatus.PENDING.name());

        InterviewSession unchangedSession =
                interviewSessionMapper.getInterviewSessionById(sessionId);
        assertThat(unchangedSession.getCurrentInterviewQuestionId())
                .isEqualTo(currentQuestionId);
        assertThat(unchangedSession.getCompletedQuestionCount())
                .isZero();
        assertThat(unchangedSession.getVersion()).isZero();
    }

    private void prepareWorkflowState() {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");
        String account = "workflow-tx-" + unique;

        jdbcTemplate.update(
                """
                INSERT INTO `user`
                    (account, username, password, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 'ENABLED')
                """,
                account,
                "Workflow事务测试用户",
                "test-password-hash",
                account + "@example.com"
        );
        userId = requiredId(
                "SELECT id FROM `user` WHERE account = ?",
                account
        );

        currentQuestionBankId = insertQuestion(
                "Current-" + unique
        );
        nextQuestionBankId = insertQuestion(
                "Next-" + unique
        );

        jdbcTemplate.update(
                """
                INSERT INTO interview_session
                    (user_id, difficulty, status, planned_question_count,
                     completed_question_count, report_status, version)
                VALUES (?, 'MEDIUM', 'IN_PROGRESS', 2, 0, 'NOT_STARTED', 0)
                """,
                userId
        );
        sessionId = requiredId(
                "SELECT id FROM interview_session WHERE user_id = ?",
                userId
        );

        currentQuestionId = insertInterviewQuestion(
                currentQuestionBankId,
                1,
                1,
                InterviewQuestionStatus.ANSWERED.name()
        );
        nextQuestionId = insertInterviewQuestion(
                nextQuestionBankId,
                2,
                3,
                InterviewQuestionStatus.PENDING.name()
        );

        jdbcTemplate.update(
                """
                UPDATE interview_session
                SET current_interview_question_id = ?
                WHERE id = ?
                """,
                currentQuestionId,
                sessionId
        );

        String requestId = "workflow-tx-request-" + unique;
        jdbcTemplate.update(
                """
                INSERT INTO interview_answer
                    (interview_question_id, answer_content, status, request_id)
                VALUES (?, ?, 'EVALUATING', ?)
                """,
                currentQuestionId,
                "HashMap 使用数组、链表和红黑树存储数据。",
                requestId
        );
        answerId = requiredId(
                "SELECT id FROM interview_answer WHERE request_id = ?",
                requestId
        );
    }

    private long insertQuestion(String knowledgePoint) {
        jdbcTemplate.update(
                """
                INSERT INTO question
                    (category, knowledge_point, difficulty, question_content,
                     reference_answer, status)
                VALUES ('JAVA_COLLECTION', ?, 'MEDIUM', ?, ?, 'ENABLED')
                """,
                knowledgePoint,
                "请说明 HashMap 的核心机制",
                "HashMap 参考答案"
        );

        return requiredId(
                "SELECT id FROM question WHERE knowledge_point = ?",
                knowledgePoint
        );
    }

    private long insertInterviewQuestion(
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
                     scoring_points_snapshot, question_type, plan_order,
                     display_order, status)
                VALUES (?, ?, 'JAVA_COLLECTION', 'HashMap', ?, ?, ?, 'MAIN',
                        ?, ?, ?)
                """,
                sessionId,
                questionId,
                "请说明 HashMap 的核心机制",
                "HashMap 参考答案",
                "[{\"scoringPointId\":101,\"pointType\":\"CORE\","
                        + "\"content\":\"说明核心机制\",\"weight\":100}]",
                planOrder,
                displayOrder,
                status
        );

        return requiredId(
                """
                SELECT id
                FROM interview_question
                WHERE session_id = ? AND plan_order = ?
                """,
                sessionId,
                planOrder
        );
    }

    private String answerStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM interview_answer WHERE id = ?",
                String.class,
                answerId
        );
    }

    private String questionStatus(long interviewQuestionId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM interview_question WHERE id = ?",
                String.class,
                interviewQuestionId
        );
    }

    private long requiredId(String sql, Object... arguments) {
        Long id = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                arguments
        );
        assertThat(id).isNotNull();
        return id;
    }
}
