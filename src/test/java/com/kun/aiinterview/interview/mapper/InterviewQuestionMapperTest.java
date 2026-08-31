package com.kun.aiinterview.interview.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.question.enums.QuestionCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class InterviewQuestionMapperTest {

    private static final String QUESTION_BANK_REFERENCE_ANSWER = "题库原始参考答案";
    private static final String INTERVIEW_REFERENCE_ANSWER = "本场面试参考答案快照";
    private static final String SCORING_POINTS_JSON = """
            [
              {
                "scoringPointId": 101,
                "pointType": "CORE",
                "content": "说明核心机制",
                "weight": 100
              }
            ]
            """;
    private static final String FOLLOW_UP_TARGET_POINTS_JSON = "[101,102]";

    @Autowired
    private InterviewQuestionMapper interviewQuestionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldFindMainQuestionByIdAndMapCompleteSnapshot() throws JsonProcessingException {
        long userId = insertUser();
        long questionId = insertQuestion(QUESTION_BANK_REFERENCE_ANSWER);
        long sessionId = insertInterviewSession(userId);
        long interviewQuestionId = insertMainInterviewQuestion(
                sessionId,
                questionId,
                INTERVIEW_REFERENCE_ANSWER,
                SCORING_POINTS_JSON,
                1,
                1,
                "WAITING_ANSWER"
        );

        InterviewQuestion found = interviewQuestionMapper.getInterviewQuestionById(
                interviewQuestionId
        );

        assertNotNull(found);
        assertAll(
                () -> assertEquals(interviewQuestionId, found.getId()),
                () -> assertEquals(sessionId, found.getSessionId()),
                () -> assertEquals(questionId, found.getQuestionId()),
                () -> assertEquals(QuestionCategory.JAVA_COLLECTION, found.getCategory()),
                () -> assertEquals("HashMap", found.getKnowledgePoint()),
                () -> assertEquals("请说明 HashMap 的核心机制", found.getQuestionContent()),
                () -> assertEquals(INTERVIEW_REFERENCE_ANSWER, found.getReferenceAnswerSnapshot()),
                () -> assertEquals(
                        objectMapper.readTree(SCORING_POINTS_JSON),
                        objectMapper.readTree(found.getScoringPointsSnapshot())
                ),
                () -> assertEquals(InterviewQuestionType.MAIN, found.getQuestionType()),
                () -> assertNull(found.getParentQuestionId()),
                () -> assertNull(found.getFollowUpTargetPoints()),
                () -> assertEquals(1, found.getPlanOrder()),
                () -> assertEquals(1, found.getDisplayOrder()),
                () -> assertEquals(
                        InterviewQuestionStatus.WAITING_ANSWER,
                        found.getStatus()
                ),
                () -> assertNotNull(found.getCreatedAt()),
                () -> assertNotNull(found.getUpdatedAt())
        );
    }

    @Test
    void shouldMapNullableFieldsForFollowUpQuestion() throws JsonProcessingException {
        long userId = insertUser();
        long questionId = insertQuestion(QUESTION_BANK_REFERENCE_ANSWER);
        long sessionId = insertInterviewSession(userId);
        long mainQuestionId = insertMainInterviewQuestion(
                sessionId,
                questionId,
                INTERVIEW_REFERENCE_ANSWER,
                SCORING_POINTS_JSON,
                1,
                1,
                "ANSWERED"
        );
        long followUpQuestionId = insertFollowUpInterviewQuestion(
                sessionId,
                mainQuestionId
        );

        InterviewQuestion found = interviewQuestionMapper.getInterviewQuestionById(
                followUpQuestionId
        );

        assertNotNull(found);
        assertAll(
                () -> assertEquals(followUpQuestionId, found.getId()),
                () -> assertEquals(sessionId, found.getSessionId()),
                () -> assertNull(found.getQuestionId()),
                () -> assertEquals(QuestionCategory.JAVA_COLLECTION, found.getCategory()),
                () -> assertEquals("HashMap 扩容", found.getKnowledgePoint()),
                () -> assertEquals("请进一步说明扩容过程", found.getQuestionContent()),
                () -> assertNull(found.getReferenceAnswerSnapshot()),
                () -> assertNull(found.getScoringPointsSnapshot()),
                () -> assertEquals(InterviewQuestionType.FOLLOW_UP, found.getQuestionType()),
                () -> assertEquals(mainQuestionId, found.getParentQuestionId()),
                () -> assertEquals(
                        objectMapper.readTree(FOLLOW_UP_TARGET_POINTS_JSON),
                        objectMapper.readTree(found.getFollowUpTargetPoints())
                ),
                () -> assertNull(found.getPlanOrder()),
                () -> assertEquals(2, found.getDisplayOrder()),
                () -> assertEquals(
                        InterviewQuestionStatus.WAITING_ANSWER,
                        found.getStatus()
                ),
                () -> assertNotNull(found.getCreatedAt()),
                () -> assertNotNull(found.getUpdatedAt())
        );
    }

    @Test
    void shouldReturnNullForMissingInterviewQuestionId() {
        assertNull(interviewQuestionMapper.getInterviewQuestionById(Long.MAX_VALUE));
    }

    private long insertUser() {
        String uniqueValue = uniqueValue();
        jdbcTemplate.update(
                """
                INSERT INTO `user` (account, username, password, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 'ENABLED')
                """,
                "e1aq-" + uniqueValue,
                "E1-A问题测试用户",
                "test-password-hash",
                "e1aq-" + uniqueValue + "@example.com"
        );
        return requiredId(
                "SELECT id FROM `user` WHERE account = ?",
                "e1aq-" + uniqueValue
        );
    }

    private long insertQuestion(String referenceAnswer) {
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
                referenceAnswer
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

    private long insertMainInterviewQuestion(
            long sessionId,
            long questionId,
            String referenceAnswerSnapshot,
            String scoringPointsSnapshot,
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
                VALUES (?, ?, 'JAVA_COLLECTION', 'HashMap', ?, ?, ?, 'MAIN',
                        NULL, NULL, ?, ?, ?)
                """,
                sessionId,
                questionId,
                "请说明 HashMap 的核心机制",
                referenceAnswerSnapshot,
                scoringPointsSnapshot,
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

    private long insertFollowUpInterviewQuestion(long sessionId, long parentQuestionId) {
        jdbcTemplate.update(
                """
                INSERT INTO interview_question
                    (session_id, question_id, category, knowledge_point,
                     question_content, reference_answer_snapshot,
                     scoring_points_snapshot, question_type, parent_question_id,
                     follow_up_target_points, plan_order, display_order, status)
                VALUES (?, NULL, 'JAVA_COLLECTION', 'HashMap 扩容', ?, NULL,
                        NULL, 'FOLLOW_UP', ?, ?, NULL, 2, 'WAITING_ANSWER')
                """,
                sessionId,
                "请进一步说明扩容过程",
                parentQuestionId,
                FOLLOW_UP_TARGET_POINTS_JSON
        );
        return requiredId(
                """
                SELECT id FROM interview_question
                WHERE session_id = ? AND display_order = 2
                """,
                sessionId
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
