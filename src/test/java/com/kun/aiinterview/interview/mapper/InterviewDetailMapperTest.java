package com.kun.aiinterview.interview.mapper;

import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.entity.InterviewAnswer;
import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.enums.InterviewAnswerStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class InterviewDetailMapperTest {

    private static final String STRENGTHS_JSON = "[\"准确\"]";
    private static final String MISSING_POINTS_JSON = "[\"缺少细节\"]";
    private static final String SCORING_POINT_RESULTS_JSON = """
            [{"scoringPointId":101,"covered":true,"evidence":"结构"}]
            """;
    private static final String RAW_RESULT_JSON = """
            {"correctnessScore":18}
            """;

    @Autowired
    private InterviewQuestionMapper interviewQuestionMapper;

    @Autowired
    private InterviewAnswerMapper interviewAnswerMapper;

    @Autowired
    private AnswerEvaluationMapper answerEvaluationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldLoadSessionQuestionsAnswersAndEvaluationsWithoutCrossingSessions() {
        long userId = insertUser();
        long otherUserId = insertUser();
        long sessionId = insertInterviewSession(userId);
        long otherSessionId = insertInterviewSession(otherUserId);

        long mainQuestionId = insertMainInterviewQuestion(
                sessionId,
                insertQuestion(),
                1,
                3
        );
        long followUpQuestionId = insertFollowUpInterviewQuestion(
                sessionId,
                mainQuestionId,
                4
        );
        long laterMainQuestionId = insertMainInterviewQuestion(
                sessionId,
                insertQuestion(),
                2,
                5
        );
        long unansweredQuestionId = insertMainInterviewQuestion(
                sessionId,
                insertQuestion(),
                3,
                7
        );
        long otherQuestionId = insertMainInterviewQuestion(
                otherSessionId,
                insertQuestion(),
                1,
                1
        );

        long mainAnswerId = insertInterviewAnswer(
                mainQuestionId,
                "MAIN 回答"
        );
        long followUpAnswerId = insertInterviewAnswer(
                followUpQuestionId,
                "FOLLOW_UP 回答"
        );
        long otherAnswerId = insertInterviewAnswer(
                otherQuestionId,
                "其他 Session 回答"
        );

        AnswerEvaluation mainInitial = evaluation(
                mainAnswerId,
                mainQuestionId,
                EvaluationPhase.INITIAL,
                DecisionAction.FOLLOW_UP
        );
        AnswerEvaluation followUpFinal = evaluation(
                followUpAnswerId,
                mainQuestionId,
                EvaluationPhase.FINAL,
                DecisionAction.NEXT_MAIN
        );
        AnswerEvaluation otherEvaluation = evaluation(
                otherAnswerId,
                otherQuestionId,
                EvaluationPhase.FINAL,
                DecisionAction.FINISH
        );
        answerEvaluationMapper.insertEvaluation(mainInitial);
        answerEvaluationMapper.insertEvaluation(followUpFinal);
        answerEvaluationMapper.insertEvaluation(otherEvaluation);

        List<InterviewQuestion> questions =
                interviewQuestionMapper.listBySessionId(sessionId);
        List<InterviewAnswer> answers =
                interviewAnswerMapper.listByInterviewQuestionIds(List.of(
                        mainQuestionId,
                        followUpQuestionId,
                        laterMainQuestionId,
                        unansweredQuestionId
                ));
        List<AnswerEvaluation> evaluations =
                answerEvaluationMapper.listByAnswerIds(List.of(
                        mainAnswerId,
                        followUpAnswerId
                ));
        List<InterviewQuestion> otherQuestions =
                interviewQuestionMapper.listBySessionId(otherSessionId);

        assertEquals(4, questions.size());
        assertEquals(mainQuestionId, questions.get(0).getId());
        assertEquals(3, questions.get(0).getDisplayOrder());
        assertEquals(InterviewQuestionType.MAIN, questions.get(0).getQuestionType());
        assertEquals(followUpQuestionId, questions.get(1).getId());
        assertEquals(4, questions.get(1).getDisplayOrder());
        assertEquals(InterviewQuestionType.FOLLOW_UP, questions.get(1).getQuestionType());
        assertEquals(laterMainQuestionId, questions.get(2).getId());
        assertEquals(5, questions.get(2).getDisplayOrder());
        assertEquals(unansweredQuestionId, questions.get(3).getId());
        assertEquals(7, questions.get(3).getDisplayOrder());
        assertTrue(questions.stream().noneMatch(
                question -> question.getId().equals(otherQuestionId)
        ));

        assertEquals(2, answers.size());
        assertTrue(answers.stream().anyMatch(
                answer -> answer.getId().equals(mainAnswerId)
                        && answer.getStatus() == InterviewAnswerStatus.EVALUATED
        ));
        assertTrue(answers.stream().anyMatch(
                answer -> answer.getId().equals(followUpAnswerId)
        ));
        assertTrue(answers.stream().noneMatch(
                answer -> answer.getId().equals(otherAnswerId)
        ));

        assertEquals(2, evaluations.size());
        assertTrue(evaluations.stream().anyMatch(
                evaluation -> evaluation.getAnswerId().equals(mainAnswerId)
                        && evaluation.getEvaluationPhase() == EvaluationPhase.INITIAL
        ));
        assertTrue(evaluations.stream().anyMatch(
                evaluation -> evaluation.getAnswerId().equals(followUpAnswerId)
                        && evaluation.getEvaluationPhase() == EvaluationPhase.FINAL
        ));
        assertTrue(evaluations.stream().noneMatch(
                evaluation -> evaluation.getAnswerId().equals(otherAnswerId)
        ));

        assertEquals(1, otherQuestions.size());
        assertEquals(otherQuestionId, otherQuestions.get(0).getId());
    }

    private AnswerEvaluation evaluation(
            long answerId,
            long mainQuestionId,
            EvaluationPhase evaluationPhase,
            DecisionAction decisionAction
    ) {
        return AnswerEvaluation.builder()
                .answerId(answerId)
                .mainInterviewQuestionId(mainQuestionId)
                .evaluationPhase(evaluationPhase)
                .correctnessScore(18)
                .completenessScore(17)
                .depthScore(16)
                .clarityScore(15)
                .practiceScore(14)
                .totalScore(80)
                .level("GOOD")
                .strengths(STRENGTHS_JSON)
                .missingPoints(MISSING_POINTS_JSON)
                .correction("补充细节")
                .scoringPointResults(SCORING_POINT_RESULTS_JSON)
                .followUpRecommended(evaluationPhase == EvaluationPhase.INITIAL)
                .suggestedFollowUp(
                        evaluationPhase == EvaluationPhase.INITIAL
                                ? "请说明扩容"
                                : null
                )
                .decisionAction(decisionAction)
                .retrievalBatchId("detail-" + uniqueValue())
                .llmModel("test-deepseek-model")
                .promptVersion("evaluation-prompt-v1")
                .evaluationStandardVersion("evaluation-standard-v1")
                .rawResult(RAW_RESULT_JSON)
                .build();
    }

    private long insertUser() {
        String account = "detail-" + uniqueValue();
        jdbcTemplate.update(
                """
                INSERT INTO `user` (account, username, password, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 'ENABLED')
                """,
                account,
                "Detail Mapper 测试用户",
                "test-password-hash",
                account + "@example.com"
        );
        return requiredId("SELECT id FROM `user` WHERE account = ?", account);
    }

    private long insertQuestion() {
        String knowledgePoint = "Detail-" + uniqueValue();
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
                VALUES (?, 'MEDIUM', 'IN_PROGRESS', 3, 0, 'NOT_STARTED')
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
            int planOrder,
            int displayOrder
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO interview_question
                    (session_id, question_id, category, knowledge_point,
                     question_content, reference_answer_snapshot,
                     scoring_points_snapshot, question_type, parent_question_id,
                     follow_up_target_points, plan_order, display_order, status)
                VALUES (?, ?, 'JAVA_COLLECTION', 'HashMap', ?, ?, ?, 'MAIN',
                        NULL, NULL, ?, ?, 'ANSWERED')
                """,
                sessionId,
                questionId,
                "请说明 HashMap 的核心机制",
                "本场面试参考答案快照",
                "[{\"scoringPointId\":101,\"pointType\":\"CORE\",\"weight\":100}]",
                planOrder,
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

    private long insertFollowUpInterviewQuestion(
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
                VALUES (?, NULL, 'JAVA_COLLECTION', 'HashMap 扩容', ?, NULL,
                        NULL, 'FOLLOW_UP', ?, '[101]', NULL, ?, 'ANSWERED')
                """,
                sessionId,
                "请进一步说明扩容过程",
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

    private long insertInterviewAnswer(
            long interviewQuestionId,
            String answerContent
    ) {
        String requestId = "detail-answer-" + uniqueValue();
        jdbcTemplate.update(
                """
                INSERT INTO interview_answer
                    (interview_question_id, answer_content, status, request_id)
                VALUES (?, ?, 'EVALUATED', ?)
                """,
                interviewQuestionId,
                answerContent,
                requestId
        );
        return requiredId(
                "SELECT id FROM interview_answer WHERE request_id = ?",
                requestId
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
