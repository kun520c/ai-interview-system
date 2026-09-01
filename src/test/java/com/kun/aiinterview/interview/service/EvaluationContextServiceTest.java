package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.entity.InterviewAnswer;
import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.interview.mapper.InterviewAnswerMapper;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationContextServiceTest {

    private static final long MAIN_ANSWER_ID = 1001L;
    private static final long FOLLOW_UP_ANSWER_ID = 1002L;
    private static final long MAIN_QUESTION_ID = 2001L;
    private static final long FOLLOW_UP_QUESTION_ID = 2002L;
    private static final long SESSION_ID = 3001L;

    private static final String SCORING_POINTS_JSON = """
            [
              {
                "scoringPointId": 101,
                "pointType": "CORE",
                "content": "核心评分点",
                "weight": 60
              },
              {
                "scoringPointId": 102,
                "pointType": "KEY",
                "content": "关键评分点",
                "weight": 40
              }
            ]
            """;

    @Mock
    private InterviewAnswerMapper interviewAnswerMapper;

    @Mock
    private InterviewQuestionMapper interviewQuestionMapper;

    private EvaluationContextService evaluationContextService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        evaluationContextService = new EvaluationContextService(
                interviewAnswerMapper,
                interviewQuestionMapper,
                objectMapper
        );
    }

    @Test
    void givenValidMainAnswer_whenBuildingContext_thenUsesPersistedMainSnapshot() {
        InterviewAnswer answer = validAnswer(
                MAIN_ANSWER_ID,
                MAIN_QUESTION_ID,
                "主回答"
        );
        InterviewQuestion question = validMainQuestion();
        stubCurrentAnswerAndQuestion(answer, question);

        EvaluationContext context = evaluationContextService.buildContext(
                MAIN_ANSWER_ID
        );

        assertAll(
                () -> assertThat(context.mode())
                        .isEqualTo(EvaluationMode.MAIN_ANSWER),
                () -> assertThat(context.currentAnswerId())
                        .isEqualTo(MAIN_ANSWER_ID),
                () -> assertThat(context.mainAnswerId())
                        .isEqualTo(MAIN_ANSWER_ID),
                () -> assertThat(context.currentInterviewQuestionId())
                        .isEqualTo(MAIN_QUESTION_ID),
                () -> assertThat(context.mainInterviewQuestionId())
                        .isEqualTo(MAIN_QUESTION_ID),
                () -> assertThat(context.category())
                        .isEqualTo(QuestionCategory.JAVA_COLLECTION),
                () -> assertThat(context.knowledgePoint()).isEqualTo("HashMap"),
                () -> assertThat(context.mainQuestionContent()).isEqualTo("主问题"),
                () -> assertThat(context.referenceAnswerSnapshot())
                        .isEqualTo("历史参考答案快照"),
                () -> assertThat(context.scoringPoints()).containsExactly(
                        new ScoringPointSnapshot(
                                101L,
                                QuestionPointType.CORE,
                                "核心评分点",
                                60
                        ),
                        new ScoringPointSnapshot(
                                102L,
                                QuestionPointType.KEY,
                                "关键评分点",
                                40
                        )
                ),
                () -> assertThat(context.mainAnswerContent()).isEqualTo("主回答"),
                () -> assertThat(context.followUpQuestionContent()).isNull(),
                () -> assertThat(context.followUpAnswerContent()).isNull(),
                () -> assertThat(context.followUpTargetPointIds()).isEmpty()
        );

        verify(interviewAnswerMapper).getInterviewAnswerById(MAIN_ANSWER_ID);
        verify(interviewQuestionMapper).getInterviewQuestionById(MAIN_QUESTION_ID);
        verify(interviewAnswerMapper, never())
                .getInterviewAnswerByInterviewQuestionId(MAIN_QUESTION_ID);
    }

    @Test
    void givenValidFollowUpAnswer_whenBuildingContext_thenCombinesMainAndFollowUpData() {
        InterviewAnswer followUpAnswer = validAnswer(
                FOLLOW_UP_ANSWER_ID,
                FOLLOW_UP_QUESTION_ID,
                "追问回答"
        );
        InterviewQuestion followUpQuestion = validFollowUpQuestion();
        InterviewQuestion mainQuestion = validMainQuestion();
        InterviewAnswer mainAnswer = validAnswer(
                MAIN_ANSWER_ID,
                MAIN_QUESTION_ID,
                "主回答"
        );
        stubValidFollowUpChain(
                followUpAnswer,
                followUpQuestion,
                mainQuestion,
                mainAnswer
        );

        EvaluationContext context = evaluationContextService.buildContext(
                FOLLOW_UP_ANSWER_ID
        );

        assertAll(
                () -> assertThat(context.mode())
                        .isEqualTo(EvaluationMode.FOLLOW_UP_ANSWER),
                () -> assertThat(context.currentAnswerId())
                        .isEqualTo(FOLLOW_UP_ANSWER_ID),
                () -> assertThat(context.mainAnswerId())
                        .isEqualTo(MAIN_ANSWER_ID),
                () -> assertThat(context.currentInterviewQuestionId())
                        .isEqualTo(FOLLOW_UP_QUESTION_ID),
                () -> assertThat(context.mainInterviewQuestionId())
                        .isEqualTo(MAIN_QUESTION_ID),
                () -> assertThat(context.category())
                        .isEqualTo(QuestionCategory.JAVA_COLLECTION),
                () -> assertThat(context.knowledgePoint()).isEqualTo("HashMap"),
                () -> assertThat(context.mainQuestionContent()).isEqualTo("主问题"),
                () -> assertThat(context.referenceAnswerSnapshot())
                        .isEqualTo("历史参考答案快照"),
                () -> assertThat(context.scoringPoints())
                        .extracting(ScoringPointSnapshot::scoringPointId)
                        .containsExactly(101L, 102L),
                () -> assertThat(context.mainAnswerContent()).isEqualTo("主回答"),
                () -> assertThat(context.followUpQuestionContent()).isEqualTo("追问题目"),
                () -> assertThat(context.followUpAnswerContent()).isEqualTo("追问回答"),
                () -> assertThat(context.followUpTargetPointIds())
                        .containsExactly(101L)
        );
        assertThat(followUpQuestion.getReferenceAnswerSnapshot()).isNull();
        assertThat(followUpQuestion.getScoringPointsSnapshot()).isNull();

        verify(interviewAnswerMapper).getInterviewAnswerById(FOLLOW_UP_ANSWER_ID);
        verify(interviewQuestionMapper)
                .getInterviewQuestionById(FOLLOW_UP_QUESTION_ID);
        verify(interviewQuestionMapper).getInterviewQuestionById(MAIN_QUESTION_ID);
        verify(interviewAnswerMapper)
                .getInterviewAnswerByInterviewQuestionId(MAIN_QUESTION_ID);
    }

    @Test
    void givenNullAnswerId_whenBuildingContext_thenRejectsBeforeMapperCalls() {
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluationContextService.buildContext(null)
        );

        verifyNoInteractions(interviewAnswerMapper, interviewQuestionMapper);
    }

    @Test
    void givenMissingAnswer_whenBuildingContext_thenRejects() {
        when(interviewAnswerMapper.getInterviewAnswerById(MAIN_ANSWER_ID))
                .thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> evaluationContextService.buildContext(MAIN_ANSWER_ID)
        );

        verifyNoInteractions(interviewQuestionMapper);
    }

    @Test
    void givenAnswerWithoutInterviewQuestionId_whenBuildingContext_thenRejects() {
        InterviewAnswer answer = validAnswer(MAIN_ANSWER_ID, null, "主回答");
        when(interviewAnswerMapper.getInterviewAnswerById(MAIN_ANSWER_ID))
                .thenReturn(answer);

        assertThrows(
                IllegalStateException.class,
                () -> evaluationContextService.buildContext(MAIN_ANSWER_ID)
        );

        verifyNoInteractions(interviewQuestionMapper);
    }

    @ParameterizedTest
    @MethodSource("blankTexts")
    void givenBlankCurrentAnswerContent_whenBuildingContext_thenRejects(
            String answerContent
    ) {
        InterviewAnswer answer = validAnswer(
                MAIN_ANSWER_ID,
                MAIN_QUESTION_ID,
                answerContent
        );
        when(interviewAnswerMapper.getInterviewAnswerById(MAIN_ANSWER_ID))
                .thenReturn(answer);

        assertThrows(
                IllegalStateException.class,
                () -> evaluationContextService.buildContext(MAIN_ANSWER_ID)
        );

        verifyNoInteractions(interviewQuestionMapper);
    }

    @Test
    void givenMissingCurrentQuestion_whenBuildingContext_thenRejects() {
        InterviewAnswer answer = validAnswer(
                MAIN_ANSWER_ID,
                MAIN_QUESTION_ID,
                "主回答"
        );
        when(interviewAnswerMapper.getInterviewAnswerById(MAIN_ANSWER_ID))
                .thenReturn(answer);
        when(interviewQuestionMapper.getInterviewQuestionById(MAIN_QUESTION_ID))
                .thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> evaluationContextService.buildContext(MAIN_ANSWER_ID)
        );
    }

    @Test
    void givenCurrentQuestionWithoutType_whenBuildingContext_thenRejects() {
        InterviewAnswer answer = validAnswer(
                MAIN_ANSWER_ID,
                MAIN_QUESTION_ID,
                "主回答"
        );
        InterviewQuestion question = validMainQuestion();
        question.setQuestionType(null);
        stubCurrentAnswerAndQuestion(answer, question);

        assertThrows(
                IllegalStateException.class,
                () -> evaluationContextService.buildContext(MAIN_ANSWER_ID)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidMainQuestions")
    void givenInvalidMainQuestion_whenBuildingContext_thenRejects(
            String scenario,
            Consumer<InterviewQuestion> mutation
    ) {
        InterviewAnswer answer = validAnswer(
                MAIN_ANSWER_ID,
                MAIN_QUESTION_ID,
                "主回答"
        );
        InterviewQuestion question = validMainQuestion();
        mutation.accept(question);
        stubCurrentAnswerAndQuestion(answer, question);

        assertThrows(
                IllegalStateException.class,
                () -> evaluationContextService.buildContext(MAIN_ANSWER_ID),
                scenario
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidScoringPointSnapshots")
    void givenInvalidScoringPointSnapshot_whenBuildingMainContext_thenRejects(
            String scenario,
            String scoringPointsSnapshot
    ) {
        InterviewAnswer answer = validAnswer(
                MAIN_ANSWER_ID,
                MAIN_QUESTION_ID,
                "主回答"
        );
        InterviewQuestion question = validMainQuestion();
        question.setScoringPointsSnapshot(scoringPointsSnapshot);
        stubCurrentAnswerAndQuestion(answer, question);

        assertThrows(
                IllegalStateException.class,
                () -> evaluationContextService.buildContext(MAIN_ANSWER_ID),
                scenario
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFollowUpQuestions")
    void givenInvalidFollowUpQuestion_whenBuildingContext_thenRejectsBeforeParentLookup(
            String scenario,
            Consumer<InterviewQuestion> mutation
    ) {
        InterviewAnswer answer = validAnswer(
                FOLLOW_UP_ANSWER_ID,
                FOLLOW_UP_QUESTION_ID,
                "追问回答"
        );
        InterviewQuestion followUpQuestion = validFollowUpQuestion();
        mutation.accept(followUpQuestion);
        stubCurrentAnswerAndQuestion(answer, followUpQuestion);

        assertThrows(
                IllegalStateException.class,
                () -> evaluationContextService.buildContext(FOLLOW_UP_ANSWER_ID),
                scenario
        );

        verify(interviewQuestionMapper, never())
                .getInterviewQuestionById(MAIN_QUESTION_ID);
    }

    @Test
    void givenMissingParentMainQuestion_whenBuildingFollowUpContext_thenRejects() {
        InterviewAnswer followUpAnswer = validAnswer(
                FOLLOW_UP_ANSWER_ID,
                FOLLOW_UP_QUESTION_ID,
                "追问回答"
        );
        InterviewQuestion followUpQuestion = validFollowUpQuestion();
        stubCurrentAnswerAndQuestion(followUpAnswer, followUpQuestion);
        when(interviewQuestionMapper.getInterviewQuestionById(MAIN_QUESTION_ID))
                .thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> evaluationContextService.buildContext(FOLLOW_UP_ANSWER_ID)
        );
    }

    @Test
    void givenParentQuestionIsNotMain_whenBuildingFollowUpContext_thenRejects() {
        InterviewAnswer followUpAnswer = validAnswer(
                FOLLOW_UP_ANSWER_ID,
                FOLLOW_UP_QUESTION_ID,
                "追问回答"
        );
        InterviewQuestion followUpQuestion = validFollowUpQuestion();
        InterviewQuestion invalidParent = validMainQuestion();
        invalidParent.setQuestionType(InterviewQuestionType.FOLLOW_UP);
        stubFollowUpThroughParent(
                followUpAnswer,
                followUpQuestion,
                invalidParent
        );

        assertThrows(
                IllegalStateException.class,
                () -> evaluationContextService.buildContext(FOLLOW_UP_ANSWER_ID)
        );
    }

    @Test
    void givenDifferentParentSession_whenBuildingFollowUpContext_thenRejects() {
        InterviewAnswer followUpAnswer = validAnswer(
                FOLLOW_UP_ANSWER_ID,
                FOLLOW_UP_QUESTION_ID,
                "追问回答"
        );
        InterviewQuestion followUpQuestion = validFollowUpQuestion();
        InterviewQuestion mainQuestion = validMainQuestion();
        mainQuestion.setSessionId(SESSION_ID + 1);
        stubFollowUpThroughParent(
                followUpAnswer,
                followUpQuestion,
                mainQuestion
        );

        assertThrows(
                IllegalStateException.class,
                () -> evaluationContextService.buildContext(FOLLOW_UP_ANSWER_ID)
        );
    }

    @Test
    void givenMissingMainAnswer_whenBuildingFollowUpContext_thenRejects() {
        InterviewAnswer followUpAnswer = validAnswer(
                FOLLOW_UP_ANSWER_ID,
                FOLLOW_UP_QUESTION_ID,
                "追问回答"
        );
        InterviewQuestion followUpQuestion = validFollowUpQuestion();
        InterviewQuestion mainQuestion = validMainQuestion();
        stubFollowUpThroughParent(
                followUpAnswer,
                followUpQuestion,
                mainQuestion
        );
        when(interviewAnswerMapper.getInterviewAnswerByInterviewQuestionId(
                MAIN_QUESTION_ID
        )).thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> evaluationContextService.buildContext(FOLLOW_UP_ANSWER_ID)
        );
    }

    @Test
    void givenMainAnswerBelongsToAnotherQuestion_whenBuildingFollowUpContext_thenRejects() {
        InterviewAnswer followUpAnswer = validAnswer(
                FOLLOW_UP_ANSWER_ID,
                FOLLOW_UP_QUESTION_ID,
                "追问回答"
        );
        InterviewQuestion followUpQuestion = validFollowUpQuestion();
        InterviewQuestion mainQuestion = validMainQuestion();
        InterviewAnswer wrongMainAnswer = validAnswer(
                MAIN_ANSWER_ID,
                MAIN_QUESTION_ID + 10,
                "主回答"
        );
        stubValidFollowUpChain(
                followUpAnswer,
                followUpQuestion,
                mainQuestion,
                wrongMainAnswer
        );

        assertThrows(
                IllegalStateException.class,
                () -> evaluationContextService.buildContext(FOLLOW_UP_ANSWER_ID)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFollowUpTargets")
    void givenInvalidFollowUpTargets_whenBuildingContext_thenRejects(
            String scenario,
            String followUpTargetPoints
    ) {
        InterviewAnswer followUpAnswer = validAnswer(
                FOLLOW_UP_ANSWER_ID,
                FOLLOW_UP_QUESTION_ID,
                "追问回答"
        );
        InterviewQuestion followUpQuestion = validFollowUpQuestion();
        followUpQuestion.setFollowUpTargetPoints(followUpTargetPoints);
        stubValidFollowUpChain(
                followUpAnswer,
                followUpQuestion,
                validMainQuestion(),
                validAnswer(MAIN_ANSWER_ID, MAIN_QUESTION_ID, "主回答")
        );

        assertThrows(
                IllegalStateException.class,
                () -> evaluationContextService.buildContext(FOLLOW_UP_ANSWER_ID),
                scenario
        );
    }

    @Test
    void givenMutableLists_whenCreatingContext_thenDefensivelyCopiesAndExposesImmutableLists() {
        List<ScoringPointSnapshot> scoringPoints = new ArrayList<>(List.of(
                new ScoringPointSnapshot(
                        101L,
                        QuestionPointType.CORE,
                        "核心评分点",
                        100
                )
        ));
        List<Long> targetPointIds = new ArrayList<>(List.of(101L));
        EvaluationContext context = new EvaluationContext(
                FOLLOW_UP_ANSWER_ID,
                FOLLOW_UP_QUESTION_ID,
                MAIN_ANSWER_ID,
                MAIN_QUESTION_ID,
                EvaluationMode.FOLLOW_UP_ANSWER,
                QuestionCategory.JAVA_COLLECTION,
                "HashMap",
                "主问题",
                "历史参考答案快照",
                scoringPoints,
                "主回答",
                "追问题目",
                "追问回答",
                targetPointIds
        );

        scoringPoints.clear();
        targetPointIds.clear();

        assertAll(
                () -> assertThat(context.scoringPoints()).hasSize(1),
                () -> assertThat(context.followUpTargetPointIds())
                        .containsExactly(101L),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> context.scoringPoints().add(
                                new ScoringPointSnapshot(
                                        102L,
                                        QuestionPointType.KEY,
                                        "关键评分点",
                                        1
                                )
                        )
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> context.followUpTargetPointIds().add(102L)
                )
        );
    }

    @Test
    void givenMapperReturnsDifferentAnswerId_whenBuildingContext_thenRejects() {
        InterviewAnswer answer = validAnswer(
                MAIN_ANSWER_ID + 1,
                MAIN_QUESTION_ID,
                "主回答"
        );
        when(interviewAnswerMapper.getInterviewAnswerById(MAIN_ANSWER_ID))
                .thenReturn(answer);

        assertThrows(
                IllegalStateException.class,
                () -> evaluationContextService.buildContext(MAIN_ANSWER_ID)
        );
    }

    @Test
    void givenMapperReturnsDifferentQuestionId_whenBuildingContext_thenRejects() {
        InterviewAnswer answer = validAnswer(
                MAIN_ANSWER_ID,
                MAIN_QUESTION_ID,
                "主回答"
        );
        InterviewQuestion question = validMainQuestion();
        question.setId(MAIN_QUESTION_ID + 1);
        stubCurrentAnswerAndQuestion(answer, question);

        assertThrows(
                IllegalStateException.class,
                () -> evaluationContextService.buildContext(MAIN_ANSWER_ID)
        );
    }

    private void stubCurrentAnswerAndQuestion(
            InterviewAnswer answer,
            InterviewQuestion question
    ) {
        when(interviewAnswerMapper.getInterviewAnswerById(answer.getId()))
                .thenReturn(answer);
        when(interviewQuestionMapper.getInterviewQuestionById(
                answer.getInterviewQuestionId()
        )).thenReturn(question);
    }

    private void stubFollowUpThroughParent(
            InterviewAnswer followUpAnswer,
            InterviewQuestion followUpQuestion,
            InterviewQuestion mainQuestion
    ) {
        stubCurrentAnswerAndQuestion(followUpAnswer, followUpQuestion);
        when(interviewQuestionMapper.getInterviewQuestionById(
                followUpQuestion.getParentQuestionId()
        )).thenReturn(mainQuestion);
    }

    private void stubValidFollowUpChain(
            InterviewAnswer followUpAnswer,
            InterviewQuestion followUpQuestion,
            InterviewQuestion mainQuestion,
            InterviewAnswer mainAnswer
    ) {
        stubFollowUpThroughParent(
                followUpAnswer,
                followUpQuestion,
                mainQuestion
        );
        when(interviewAnswerMapper.getInterviewAnswerByInterviewQuestionId(
                mainQuestion.getId()
        )).thenReturn(mainAnswer);
    }

    private static InterviewAnswer validAnswer(
            Long answerId,
            Long interviewQuestionId,
            String answerContent
    ) {
        return InterviewAnswer.builder()
                .id(answerId)
                .interviewQuestionId(interviewQuestionId)
                .answerContent(answerContent)
                .build();
    }

    private static InterviewQuestion validMainQuestion() {
        return InterviewQuestion.builder()
                .id(MAIN_QUESTION_ID)
                .sessionId(SESSION_ID)
                .questionId(4001L)
                .category(QuestionCategory.JAVA_COLLECTION)
                .knowledgePoint("HashMap")
                .questionContent("主问题")
                .referenceAnswerSnapshot("历史参考答案快照")
                .scoringPointsSnapshot(SCORING_POINTS_JSON)
                .questionType(InterviewQuestionType.MAIN)
                .parentQuestionId(null)
                .followUpTargetPoints(null)
                .planOrder(1)
                .displayOrder(1)
                .build();
    }

    private static InterviewQuestion validFollowUpQuestion() {
        return InterviewQuestion.builder()
                .id(FOLLOW_UP_QUESTION_ID)
                .sessionId(SESSION_ID)
                .questionId(null)
                .category(QuestionCategory.JAVA_COLLECTION)
                .knowledgePoint("HashMap 扩容")
                .questionContent("追问题目")
                .referenceAnswerSnapshot(null)
                .scoringPointsSnapshot(null)
                .questionType(InterviewQuestionType.FOLLOW_UP)
                .parentQuestionId(MAIN_QUESTION_ID)
                .followUpTargetPoints("[101]")
                .planOrder(null)
                .displayOrder(2)
                .build();
    }

    private static Stream<String> blankTexts() {
        return Stream.of(null, "", "   ");
    }

    private static Stream<Arguments> invalidMainQuestions() {
        return Stream.of(
                questionMutation("questionId is null", q -> q.setQuestionId(null)),
                questionMutation(
                        "parentQuestionId is present",
                        q -> q.setParentQuestionId(99L)
                ),
                questionMutation("planOrder is null", q -> q.setPlanOrder(null)),
                questionMutation("sessionId is null", q -> q.setSessionId(null)),
                questionMutation("category is null", q -> q.setCategory(null)),
                questionMutation(
                        "knowledgePoint is blank",
                        q -> q.setKnowledgePoint(" ")
                ),
                questionMutation(
                        "questionContent is blank",
                        q -> q.setQuestionContent(" ")
                ),
                questionMutation(
                        "referenceAnswerSnapshot is blank",
                        q -> q.setReferenceAnswerSnapshot(" ")
                ),
                questionMutation(
                        "scoringPointsSnapshot is blank",
                        q -> q.setScoringPointsSnapshot(" ")
                )
        );
    }

    private static Stream<Arguments> invalidFollowUpQuestions() {
        return Stream.of(
                questionMutation("questionId is present", q -> q.setQuestionId(4001L)),
                questionMutation(
                        "parentQuestionId is null",
                        q -> q.setParentQuestionId(null)
                ),
                questionMutation("planOrder is present", q -> q.setPlanOrder(2)),
                questionMutation("sessionId is null", q -> q.setSessionId(null)),
                questionMutation(
                        "questionContent is blank",
                        q -> q.setQuestionContent(" ")
                ),
                questionMutation(
                        "referenceAnswerSnapshot is present",
                        q -> q.setReferenceAnswerSnapshot("追问错误快照")
                ),
                questionMutation(
                        "scoringPointsSnapshot is present",
                        q -> q.setScoringPointsSnapshot(SCORING_POINTS_JSON)
                ),
                questionMutation(
                        "followUpTargetPoints is blank",
                        q -> q.setFollowUpTargetPoints(" ")
                )
        );
    }

    private static Arguments questionMutation(
            String scenario,
            Consumer<InterviewQuestion> mutation
    ) {
        return Arguments.of(scenario, mutation);
    }

    private static Stream<Arguments> invalidScoringPointSnapshots() {
        return Stream.of(
                Arguments.of("malformed JSON", "[{"),
                Arguments.of("null list", "null"),
                Arguments.of("empty list", "[]"),
                Arguments.of("null element", "[null]"),
                Arguments.of(
                        "duplicate scoringPointId",
                        scoringPointsJson(101, "CORE", "评分点一", 50,
                                101, "KEY", "评分点二", 50)
                ),
                Arguments.of(
                        "non-positive scoringPointId",
                        scoringPointsJson(0, "CORE", "评分点一", 50,
                                102, "KEY", "评分点二", 50)
                ),
                Arguments.of(
                        "null pointType",
                        scoringPointsJson(101, null, "评分点一", 50,
                                102, "KEY", "评分点二", 50)
                ),
                Arguments.of(
                        "blank content",
                        scoringPointsJson(101, "CORE", " ", 50,
                                102, "KEY", "评分点二", 50)
                ),
                Arguments.of(
                        "weight below range",
                        scoringPointsJson(101, "CORE", "评分点一", 0,
                                102, "KEY", "评分点二", 100)
                ),
                Arguments.of(
                        "weight above range",
                        scoringPointsJson(101, "CORE", "评分点一", 101,
                                102, "KEY", "评分点二", 1)
                ),
                Arguments.of(
                        "total weight is not 100",
                        scoringPointsJson(101, "CORE", "评分点一", 40,
                                102, "KEY", "评分点二", 40)
                )
        );
    }

    private static Stream<Arguments> invalidFollowUpTargets() {
        return Stream.of(
                Arguments.of("malformed JSON", "["),
                Arguments.of("null list", "null"),
                Arguments.of("empty list", "[]"),
                Arguments.of("null element", "[null]"),
                Arguments.of("duplicate target id", "[101,101]"),
                Arguments.of("target id outside main snapshot", "[999]")
        );
    }

    private static String scoringPointsJson(
            long firstId,
            String firstType,
            String firstContent,
            int firstWeight,
            long secondId,
            String secondType,
            String secondContent,
            int secondWeight
    ) {
        return """
                [
                  {
                    "scoringPointId": %d,
                    "pointType": %s,
                    "content": "%s",
                    "weight": %d
                  },
                  {
                    "scoringPointId": %d,
                    "pointType": %s,
                    "content": "%s",
                    "weight": %d
                  }
                ]
                """.formatted(
                firstId,
                jsonString(firstType),
                firstContent,
                firstWeight,
                secondId,
                jsonString(secondType),
                secondContent,
                secondWeight
        );
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
