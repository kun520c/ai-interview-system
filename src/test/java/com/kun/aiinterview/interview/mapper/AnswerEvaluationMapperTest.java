package com.kun.aiinterview.interview.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.question.enums.QuestionCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class AnswerEvaluationMapperTest {

    private static final String STRENGTHS_JSON = """
            ["准确", "表达清晰"]
            """;
    private static final String MISSING_POINTS_JSON = """
            ["缺少扩容细节"]
            """;
    private static final String SCORING_POINT_RESULTS_JSON = """
            [
              {
                "scoringPointId": 101,
                "covered": true,
                "evidence": "说明了数组和链表结构"
              }
            ]
            """;
    private static final String RAW_RESULT_JSON = """
            {
              "correctnessScore": 18,
              "completenessScore": 17,
              "followUpRecommended": false
            }
            """;

    @Autowired
    private AnswerEvaluationMapper answerEvaluationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldInsertAndSelectCompleteFinalEvaluation()
            throws JsonProcessingException {
        MainAnswerFixture fixture = insertMainAnswerFixture();
        AnswerEvaluation evaluation = evaluation(
                fixture.mainAnswerId(),
                fixture.mainQuestionId(),
                EvaluationPhase.FINAL,
                DecisionAction.NEXT_MAIN,
                false,
                null,
                "建议补充 HashMap 扩容机制。"
        );

        assertNull(evaluation.getId());

        int affectedRows = answerEvaluationMapper.insertEvaluation(evaluation);

        assertNotNull(evaluation.getId());

        AnswerEvaluation found = answerEvaluationMapper.getByAnswerId(
                fixture.mainAnswerId()
        );

        assertNotNull(found);
        assertAll(
                () -> assertEquals(1, affectedRows),
                () -> assertEquals(evaluation.getId(), found.getId()),
                () -> assertEquals(fixture.mainAnswerId(), found.getAnswerId()),
                () -> assertEquals(
                        fixture.mainQuestionId(),
                        found.getMainInterviewQuestionId()
                ),
                () -> assertEquals(EvaluationPhase.FINAL, found.getEvaluationPhase()),
                () -> assertEquals(18, found.getCorrectnessScore()),
                () -> assertEquals(17, found.getCompletenessScore()),
                () -> assertEquals(16, found.getDepthScore()),
                () -> assertEquals(15, found.getClarityScore()),
                () -> assertEquals(14, found.getPracticeScore()),
                () -> assertEquals(80, found.getTotalScore()),
                () -> assertEquals("TEST_LEVEL", found.getLevel()),
                () -> assertEquals(
                        objectMapper.readTree(STRENGTHS_JSON),
                        objectMapper.readTree(found.getStrengths())
                ),
                () -> assertEquals(
                        objectMapper.readTree(MISSING_POINTS_JSON),
                        objectMapper.readTree(found.getMissingPoints())
                ),
                () -> assertEquals(
                        "建议补充 HashMap 扩容机制。",
                        found.getCorrection()
                ),
                () -> assertEquals(
                        objectMapper.readTree(SCORING_POINT_RESULTS_JSON),
                        objectMapper.readTree(found.getScoringPointResults())
                ),
                () -> assertFalse(found.getFollowUpRecommended()),
                () -> assertNull(found.getSuggestedFollowUp()),
                () -> assertEquals(DecisionAction.NEXT_MAIN, found.getDecisionAction()),
                () -> assertEquals(
                        evaluation.getRetrievalBatchId(),
                        found.getRetrievalBatchId()
                ),
                () -> assertEquals("test-deepseek-model", found.getLlmModel()),
                () -> assertEquals("evaluation-prompt-v1", found.getPromptVersion()),
                () -> assertEquals(
                        "test-standard-v1",
                        found.getEvaluationStandardVersion()
                ),
                () -> assertEquals(
                        objectMapper.readTree(RAW_RESULT_JSON),
                        objectMapper.readTree(found.getRawResult())
                ),
                () -> assertNotNull(found.getCreatedAt())
        );
    }

    @Test
    void shouldPersistNullableCorrectionAndSuggestedFollowUp() {
        MainAnswerFixture fixture = insertMainAnswerFixture();
        AnswerEvaluation evaluation = evaluation(
                fixture.mainAnswerId(),
                fixture.mainQuestionId(),
                EvaluationPhase.FINAL,
                DecisionAction.FINISH,
                false,
                null,
                null
        );

        int affectedRows = answerEvaluationMapper.insertEvaluation(evaluation);
        AnswerEvaluation found = answerEvaluationMapper.getByAnswerId(
                fixture.mainAnswerId()
        );

        assertNotNull(found);
        assertAll(
                () -> assertEquals(1, affectedRows),
                () -> assertNotNull(evaluation.getId()),
                () -> assertNull(found.getCorrection()),
                () -> assertNull(found.getSuggestedFollowUp())
        );
    }

    @Test
    void shouldRejectSecondEvaluationForSameAnswer() {
        MainAnswerFixture fixture = insertMainAnswerFixture();
        AnswerEvaluation first = evaluation(
                fixture.mainAnswerId(),
                fixture.mainQuestionId(),
                EvaluationPhase.FINAL,
                DecisionAction.NEXT_MAIN,
                false,
                null,
                null
        );
        AnswerEvaluation duplicate = evaluation(
                fixture.mainAnswerId(),
                fixture.mainQuestionId(),
                EvaluationPhase.FINAL,
                DecisionAction.NEXT_MAIN,
                false,
                null,
                null
        );

        assertEquals(1, answerEvaluationMapper.insertEvaluation(first));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> answerEvaluationMapper.insertEvaluation(duplicate)
        );

        Integer evaluationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM answer_evaluation WHERE answer_id = ?",
                Integer.class,
                fixture.mainAnswerId()
        );
        assertEquals(1, evaluationCount);
    }

    @Test
    void shouldAllowMainInitialAndFollowUpFinalForSameMainQuestion() {
        FollowUpAnswerFixture fixture = insertFollowUpAnswerFixture();
        AnswerEvaluation initialEvaluation = evaluation(
                fixture.mainAnswerId(),
                fixture.mainQuestionId(),
                EvaluationPhase.INITIAL,
                DecisionAction.FOLLOW_UP,
                true,
                "请进一步说明 HashMap 的扩容过程。",
                null
        );
        AnswerEvaluation finalEvaluation = evaluation(
                fixture.followUpAnswerId(),
                fixture.mainQuestionId(),
                EvaluationPhase.FINAL,
                DecisionAction.NEXT_MAIN,
                false,
                null,
                "综合 MAIN 和 FOLLOW_UP 回答后的修正建议。"
        );

        int initialRows = answerEvaluationMapper.insertEvaluation(initialEvaluation);
        int finalRows = answerEvaluationMapper.insertEvaluation(finalEvaluation);

        AnswerEvaluation foundInitial = answerEvaluationMapper.getByAnswerId(
                fixture.mainAnswerId()
        );
        AnswerEvaluation foundFinal = answerEvaluationMapper.getByAnswerId(
                fixture.followUpAnswerId()
        );
        Integer evaluationCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM answer_evaluation
                WHERE main_interview_question_id = ?
                """,
                Integer.class,
                fixture.mainQuestionId()
        );

        assertNotNull(foundInitial);
        assertNotNull(foundFinal);
        assertAll(
                () -> assertEquals(1, initialRows),
                () -> assertEquals(1, finalRows),
                () -> assertNotNull(initialEvaluation.getId()),
                () -> assertNotNull(finalEvaluation.getId()),
                () -> assertEquals(
                        fixture.mainQuestionId(),
                        foundInitial.getMainInterviewQuestionId()
                ),
                () -> assertEquals(
                        fixture.mainQuestionId(),
                        foundFinal.getMainInterviewQuestionId()
                ),
                () -> assertEquals(
                        EvaluationPhase.INITIAL,
                        foundInitial.getEvaluationPhase()
                ),
                () -> assertEquals(
                        DecisionAction.FOLLOW_UP,
                        foundInitial.getDecisionAction()
                ),
                () -> assertTrue(foundInitial.getFollowUpRecommended()),
                () -> assertEquals(
                        EvaluationPhase.FINAL,
                        foundFinal.getEvaluationPhase()
                ),
                () -> assertEquals(
                        DecisionAction.NEXT_MAIN,
                        foundFinal.getDecisionAction()
                ),
                () -> assertFalse(foundFinal.getFollowUpRecommended()),
                () -> assertEquals(2, evaluationCount)
        );
    }

    @Test
    void shouldReturnInitialEvaluationWhenMainHasNoFinalEvaluation() {
        MainAnswerFixture fixture = insertMainAnswerFixture();
        AnswerEvaluation initialEvaluation = evaluation(
                fixture.mainAnswerId(),
                fixture.mainQuestionId(),
                EvaluationPhase.INITIAL,
                DecisionAction.NEXT_MAIN,
                false,
                null,
                null
        );
        assertEquals(
                1,
                answerEvaluationMapper.insertEvaluation(initialEvaluation)
        );

        List<AnswerEvaluation> evaluations = answerEvaluationMapper
                .listFinalEffectiveEvaluationsBySessionId(fixture.sessionId());

        assertEquals(1, evaluations.size());
        AnswerEvaluation found = evaluations.getFirst();
        assertAll(
                () -> assertEquals(initialEvaluation.getId(), found.getId()),
                () -> assertEquals(
                        fixture.mainQuestionId(),
                        found.getMainInterviewQuestionId()
                ),
                () -> assertEquals(
                        EvaluationPhase.INITIAL,
                        found.getEvaluationPhase()
                ),
                () -> assertEquals(
                        QuestionCategory.JAVA_COLLECTION,
                        found.getCategory()
                ),
                () -> assertEquals("HashMap", found.getKnowledgePoint()),
                () -> assertEquals(
                        "请说明 HashMap 的核心机制",
                        found.getQuestionContent()
                )
        );
    }

    @Test
    void shouldReturnOneFinalEffectiveEvaluationPerMainInPlanOrderForSession() {
        long userId = insertUser();
        long sessionId = insertInterviewSession(userId, 3);

        long mainQuestion3Id = insertMainInterviewQuestion(
                sessionId,
                insertQuestion(),
                3,
                4
        );
        long mainQuestion1Id = insertMainInterviewQuestion(
                sessionId,
                insertQuestion(),
                1,
                1
        );
        long mainQuestion2Id = insertMainInterviewQuestion(
                sessionId,
                insertQuestion(),
                2,
                2
        );

        long mainAnswer1Id = insertInterviewAnswer(
                mainQuestion1Id,
                "MAIN 1 回答"
        );
        long mainAnswer2Id = insertInterviewAnswer(
                mainQuestion2Id,
                "MAIN 2 回答"
        );
        long followUpQuestion2Id = insertFollowUpInterviewQuestion(
                sessionId,
                mainQuestion2Id,
                3
        );
        long followUpAnswer2Id = insertInterviewAnswer(
                followUpQuestion2Id,
                "MAIN 2 的 FOLLOW_UP 回答"
        );
        long mainAnswer3Id = insertInterviewAnswer(
                mainQuestion3Id,
                "MAIN 3 回答"
        );

        AnswerEvaluation main1Initial = evaluation(
                mainAnswer1Id,
                mainQuestion1Id,
                EvaluationPhase.INITIAL,
                DecisionAction.NEXT_MAIN,
                false,
                null,
                null
        );
        AnswerEvaluation main2Initial = evaluation(
                mainAnswer2Id,
                mainQuestion2Id,
                EvaluationPhase.INITIAL,
                DecisionAction.FOLLOW_UP,
                true,
                "请继续说明。",
                null
        );
        AnswerEvaluation main2Final = evaluation(
                followUpAnswer2Id,
                mainQuestion2Id,
                EvaluationPhase.FINAL,
                DecisionAction.NEXT_MAIN,
                false,
                null,
                null
        );
        AnswerEvaluation main3Initial = evaluation(
                mainAnswer3Id,
                mainQuestion3Id,
                EvaluationPhase.INITIAL,
                DecisionAction.FINISH,
                false,
                null,
                null
        );
        answerEvaluationMapper.insertEvaluation(main3Initial);
        answerEvaluationMapper.insertEvaluation(main2Initial);
        answerEvaluationMapper.insertEvaluation(main1Initial);
        answerEvaluationMapper.insertEvaluation(main2Final);

        MainAnswerFixture otherSessionFixture = insertMainAnswerFixture();
        AnswerEvaluation otherSessionEvaluation = evaluation(
                otherSessionFixture.mainAnswerId(),
                otherSessionFixture.mainQuestionId(),
                EvaluationPhase.FINAL,
                DecisionAction.FINISH,
                false,
                null,
                null
        );
        answerEvaluationMapper.insertEvaluation(otherSessionEvaluation);

        List<AnswerEvaluation> evaluations = answerEvaluationMapper
                .listFinalEffectiveEvaluationsBySessionId(sessionId);

        assertEquals(3, evaluations.size());
        assertAll(
                () -> assertEquals(
                        mainQuestion1Id,
                        evaluations.get(0).getMainInterviewQuestionId()
                ),
                () -> assertEquals(
                        EvaluationPhase.INITIAL,
                        evaluations.get(0).getEvaluationPhase()
                ),
                () -> assertEquals(
                        mainQuestion2Id,
                        evaluations.get(1).getMainInterviewQuestionId()
                ),
                () -> assertEquals(
                        main2Final.getId(),
                        evaluations.get(1).getId()
                ),
                () -> assertEquals(
                        EvaluationPhase.FINAL,
                        evaluations.get(1).getEvaluationPhase()
                ),
                () -> assertEquals(
                        mainQuestion3Id,
                        evaluations.get(2).getMainInterviewQuestionId()
                ),
                () -> assertEquals(
                        EvaluationPhase.INITIAL,
                        evaluations.get(2).getEvaluationPhase()
                ),
                () -> assertTrue(evaluations.stream().noneMatch(
                        evaluation -> evaluation.getMainInterviewQuestionId()
                                .equals(otherSessionFixture.mainQuestionId())
                ))
        );
    }

    private MainAnswerFixture insertMainAnswerFixture() {
        long userId = insertUser();
        long questionId = insertQuestion();
        long sessionId = insertInterviewSession(userId);
        long mainQuestionId = insertMainInterviewQuestion(
                sessionId,
                questionId
        );
        long mainAnswerId = insertInterviewAnswer(
                mainQuestionId,
                "MAIN 回答：HashMap 使用数组、链表和红黑树。"
        );
        return new MainAnswerFixture(sessionId, mainQuestionId, mainAnswerId);
    }

    private FollowUpAnswerFixture insertFollowUpAnswerFixture() {
        long userId = insertUser();
        long questionId = insertQuestion();
        long sessionId = insertInterviewSession(userId);
        long mainQuestionId = insertMainInterviewQuestion(
                sessionId,
                questionId
        );
        long mainAnswerId = insertInterviewAnswer(
                mainQuestionId,
                "MAIN 回答：HashMap 使用数组、链表和红黑树。"
        );
        long followUpQuestionId = insertFollowUpInterviewQuestion(
                sessionId,
                mainQuestionId
        );
        long followUpAnswerId = insertInterviewAnswer(
                followUpQuestionId,
                "FOLLOW_UP 回答：容量超过阈值后会进行扩容。"
        );
        return new FollowUpAnswerFixture(
                mainQuestionId,
                mainAnswerId,
                followUpAnswerId
        );
    }

    private AnswerEvaluation evaluation(
            long answerId,
            long mainQuestionId,
            EvaluationPhase evaluationPhase,
            DecisionAction decisionAction,
            boolean followUpRecommended,
            String suggestedFollowUp,
            String correction
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
                .level("TEST_LEVEL")
                .strengths(STRENGTHS_JSON)
                .missingPoints(MISSING_POINTS_JSON)
                .correction(correction)
                .scoringPointResults(SCORING_POINT_RESULTS_JSON)
                .followUpRecommended(followUpRecommended)
                .suggestedFollowUp(suggestedFollowUp)
                .decisionAction(decisionAction)
                .retrievalBatchId("f1-retrieval-" + uniqueValue())
                .llmModel("test-deepseek-model")
                .promptVersion("evaluation-prompt-v1")
                .evaluationStandardVersion("test-standard-v1")
                .rawResult(RAW_RESULT_JSON)
                .build();
    }

    private long insertUser() {
        String uniqueValue = uniqueValue();
        String account = "f1ae-" + uniqueValue;
        jdbcTemplate.update(
                """
                INSERT INTO `user` (account, username, password, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 'ENABLED')
                """,
                account,
                "F1评价测试用户",
                "test-password-hash",
                account + "@example.com"
        );
        return requiredId("SELECT id FROM `user` WHERE account = ?", account);
    }

    private long insertQuestion() {
        String knowledgePoint = "F1-AnswerEvaluation-" + uniqueValue();
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

    private long insertInterviewSession(long userId) {
        return insertInterviewSession(userId, 1);
    }

    private long insertInterviewSession(long userId, int plannedQuestionCount) {
        jdbcTemplate.update(
                """
                INSERT INTO interview_session
                    (user_id, difficulty, status, planned_question_count,
                     completed_question_count, report_status)
                VALUES (?, 'MEDIUM', 'CREATED', ?, 0, 'NOT_STARTED')
                """,
                userId,
                plannedQuestionCount
        );
        return requiredId(
                "SELECT id FROM interview_session WHERE user_id = ?",
                userId
        );
    }

    private long insertMainInterviewQuestion(long sessionId, long questionId) {
        return insertMainInterviewQuestion(sessionId, questionId, 1, 1);
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
                "[{\"scoringPointId\":101,\"weight\":100}]",
                planOrder,
                displayOrder
        );
        return requiredId(
                """
                SELECT id FROM interview_question
                WHERE session_id = ? AND plan_order = ?
                """,
                sessionId,
                planOrder
        );
    }

    private long insertFollowUpInterviewQuestion(
            long sessionId,
            long mainQuestionId
    ) {
        return insertFollowUpInterviewQuestion(sessionId, mainQuestionId, 2);
    }

    private long insertFollowUpInterviewQuestion(
            long sessionId,
            long mainQuestionId,
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
                        NULL, 'FOLLOW_UP', ?, ?, NULL, ?, 'ANSWERED')
                """,
                sessionId,
                "请进一步说明 HashMap 的扩容过程",
                mainQuestionId,
                "[101]",
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
        String requestId = "f1-answer-" + uniqueValue();
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

    private record MainAnswerFixture(
            long sessionId,
            long mainQuestionId,
            long mainAnswerId
    ) {
    }

    private record FollowUpAnswerFixture(
            long mainQuestionId,
            long mainAnswerId,
            long followUpAnswerId
    ) {
    }
}
