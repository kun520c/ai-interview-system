package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.common.exception.ResourceNotFoundException;
import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.entity.InterviewAnswer;
import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.enums.InterviewAnswerStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationLevel;
import com.kun.aiinterview.interview.mapper.AnswerEvaluationMapper;
import com.kun.aiinterview.interview.mapper.InterviewAnswerMapper;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.vo.InterviewDetailQuestionResponse;
import com.kun.aiinterview.interview.vo.InterviewDetailResponse;
import com.kun.aiinterview.interview.vo.InterviewHistoryPageResponse;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewHistoryServiceTest {

    private static final long USER_ID = 11L;
    private static final long OTHER_USER_ID = 12L;
    private static final long SESSION_ID = 21L;
    private static final long MAIN_QUESTION_ID = 31L;
    private static final long FOLLOW_UP_QUESTION_ID = 32L;
    private static final long UNANSWERED_QUESTION_ID = 33L;
    private static final long MAIN_ANSWER_ID = 41L;
    private static final long FOLLOW_UP_ANSWER_ID = 42L;

    @Mock
    private InterviewSessionService interviewSessionService;

    @Mock
    private InterviewSessionMapper interviewSessionMapper;

    @Mock
    private InterviewQuestionMapper interviewQuestionMapper;

    @Mock
    private InterviewAnswerMapper interviewAnswerMapper;

    @Mock
    private InterviewWorkflowTransactionService transactionService;

    @Mock
    private AnswerEvaluationMapper answerEvaluationMapper;

    @Mock
    private ObjectProvider<InterviewWorkflowService> workflowServiceProvider;

    private InterviewService service;

    @BeforeEach
    void setUp() {
        service = new InterviewService(
                interviewSessionService,
                interviewSessionMapper,
                interviewQuestionMapper,
                interviewAnswerMapper,
                transactionService,
                answerEvaluationMapper,
                workflowServiceProvider,
                new ObjectMapper()
        );
    }

    @Test
    void shouldUseDefaultPaginationAndReturnEmptyHistory() {
        when(interviewSessionMapper.countCompletedSessionsByUserId(USER_ID))
                .thenReturn(0L);

        InterviewHistoryPageResponse response =
                service.listHistory(USER_ID, null, null);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(10);
        assertThat(response.total()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.items()).isEmpty();
        verify(interviewSessionMapper)
                .countCompletedSessionsByUserId(USER_ID);
        verify(interviewSessionMapper, never())
                .listCompletedSessionsByUserId(anyLong(), anyInt(), anyLong());
        verifyNoWriteInteractions();
    }

    @Test
    void shouldApplyCustomPaginationAndMapSafeHistoryFields() {
        LocalDateTime endedAt = LocalDateTime.of(2026, 9, 18, 12, 0);
        InterviewSession session = completedSession(SESSION_ID, USER_ID, endedAt);
        when(interviewSessionMapper.countCompletedSessionsByUserId(USER_ID))
                .thenReturn(12L);
        when(interviewSessionMapper.listCompletedSessionsByUserId(
                USER_ID,
                5,
                5
        )).thenReturn(List.of(session));

        InterviewHistoryPageResponse response =
                service.listHistory(USER_ID, 2, 5);

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.pageSize()).isEqualTo(5);
        assertThat(response.total()).isEqualTo(12L);
        assertThat(response.totalPages()).isEqualTo(3L);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sessionId()).isEqualTo(SESSION_ID);
        assertThat(response.items().get(0).difficulty())
                .isEqualTo(QuestionDifficulty.MEDIUM);
        assertThat(response.items().get(0).status())
                .isEqualTo(InterviewSessionStatus.COMPLETED);
        assertThat(response.items().get(0).reportStatus())
                .isEqualTo(InterviewReportStatus.READY);
        assertThat(response.items().get(0).totalScore())
                .isEqualByComparingTo("82.50");
        assertThat(response.items().get(0).plannedQuestionCount()).isEqualTo(5);
        assertThat(response.items().get(0).completedQuestionCount()).isEqualTo(5);
        assertThat(response.items().get(0).endedAt()).isEqualTo(endedAt);
        assertThat(response.items().get(0).startedAt()).isNotNull();
        assertThat(response.items().get(0).createdAt()).isNotNull();
        verify(interviewSessionMapper).listCompletedSessionsByUserId(
                USER_ID,
                5,
                5
        );
        verifyNoWriteInteractions();
    }

    @ParameterizedTest
    @CsvSource({
            "0, 10, 页码必须大于等于1",
            "1, 0, 每页数量必须在1到50之间",
            "1, 51, 每页数量必须在1到50之间"
    })
    void shouldRejectInvalidPagination(
            int page,
            int pageSize,
            String message
    ) {
        assertThatThrownBy(() -> service.listHistory(USER_ID, page, pageSize))
                .isInstanceOf(BusinessException.class)
                .hasMessage(message);

        verifyNoInteractions(interviewSessionMapper);
        verifyNoWriteInteractions();
    }

    @Test
    void shouldRejectHistoryOwnedByAnotherUserLeakingFromMapper() {
        when(interviewSessionMapper.countCompletedSessionsByUserId(USER_ID))
                .thenReturn(1L);
        when(interviewSessionMapper.listCompletedSessionsByUserId(
                USER_ID,
                10,
                0
        )).thenReturn(List.of(completedSession(
                SESSION_ID,
                OTHER_USER_ID,
                LocalDateTime.of(2026, 9, 18, 12, 0)
        )));

        assertThatThrownBy(() -> service.listHistory(USER_ID, 1, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("历史InterviewSession不属于当前用户");
    }

    @Test
    void shouldRejectNonCompletedSessionLeakingIntoHistory() {
        InterviewSession inProgress = completedSession(
                SESSION_ID,
                USER_ID,
                LocalDateTime.of(2026, 9, 18, 12, 0)
        );
        inProgress.setStatus(InterviewSessionStatus.IN_PROGRESS);
        when(interviewSessionMapper.countCompletedSessionsByUserId(USER_ID))
                .thenReturn(1L);
        when(interviewSessionMapper.listCompletedSessionsByUserId(
                USER_ID,
                10,
                0
        )).thenReturn(List.of(inProgress));

        assertThatThrownBy(() -> service.listHistory(USER_ID, 1, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("历史InterviewSession状态不是COMPLETED");
    }

    @Test
    void shouldRejectMissingSessionDetail() {
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(null);

        assertThatThrownBy(() -> service.getDetail(USER_ID, SESSION_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("面试会话不存在");

        verifyNoInteractions(interviewQuestionMapper, interviewAnswerMapper, answerEvaluationMapper);
        verifyNoWriteInteractions();
    }

    @Test
    void shouldRejectDetailOwnedByAnotherUserBeforeLoadingQuestions() {
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(inProgressSession(OTHER_USER_ID));

        assertThatThrownBy(() -> service.getDetail(USER_ID, SESSION_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("面试会话不存在");

        verifyNoInteractions(interviewQuestionMapper, interviewAnswerMapper, answerEvaluationMapper);
        verifyNoWriteInteractions();
    }

    @Test
    void shouldReturnCompletedDetailTimelineWithInitialAndFinalEvaluations() {
        InterviewSession session = completedSession(
                SESSION_ID,
                USER_ID,
                LocalDateTime.of(2026, 9, 18, 18, 0)
        );
        InterviewQuestion mainQuestion = question(
                MAIN_QUESTION_ID,
                InterviewQuestionType.MAIN,
                1,
                1,
                InterviewQuestionStatus.ANSWERED
        );
        InterviewQuestion followUpQuestion = question(
                FOLLOW_UP_QUESTION_ID,
                InterviewQuestionType.FOLLOW_UP,
                null,
                2,
                InterviewQuestionStatus.ANSWERED
        );
        stubDetailReads(
                session,
                List.of(mainQuestion, followUpQuestion),
                List.of(
                        answer(MAIN_ANSWER_ID, MAIN_QUESTION_ID, "MAIN 答案"),
                        answer(FOLLOW_UP_ANSWER_ID, FOLLOW_UP_QUESTION_ID, "追问答案")
                ),
                List.of(
                        evaluation(
                                MAIN_ANSWER_ID,
                                MAIN_QUESTION_ID,
                                EvaluationPhase.INITIAL,
                                DecisionAction.FOLLOW_UP,
                                "[\"概念准确\"]",
                                "[\"缺少扩容\"]"
                        ),
                        evaluation(
                                FOLLOW_UP_ANSWER_ID,
                                MAIN_QUESTION_ID,
                                EvaluationPhase.FINAL,
                                DecisionAction.NEXT_MAIN,
                                "[\"补充了扩容\"]",
                                "[]"
                        )
                )
        );

        InterviewDetailResponse response = service.getDetail(USER_ID, SESSION_ID);

        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        assertThat(response.status()).isEqualTo(InterviewSessionStatus.COMPLETED);
        assertThat(response.questions()).hasSize(2);

        InterviewDetailQuestionResponse main = response.questions().get(0);
        assertThat(main.interviewQuestionId()).isEqualTo(MAIN_QUESTION_ID);
        assertThat(main.questionType()).isEqualTo(InterviewQuestionType.MAIN);
        assertThat(main.planOrder()).isEqualTo(1);
        assertThat(main.displayOrder()).isEqualTo(1);
        assertThat(main.answer().answerContent()).isEqualTo("MAIN 答案");
        assertThat(main.evaluation().evaluationPhase())
                .isEqualTo(EvaluationPhase.INITIAL);
        assertThat(main.evaluation().strengths()).containsExactly("概念准确");
        assertThat(main.evaluation().missingPoints()).containsExactly("缺少扩容");
        assertThat(main.evaluation().level()).isEqualTo(EvaluationLevel.GOOD);

        InterviewDetailQuestionResponse followUp = response.questions().get(1);
        assertThat(followUp.interviewQuestionId()).isEqualTo(FOLLOW_UP_QUESTION_ID);
        assertThat(followUp.questionType()).isEqualTo(InterviewQuestionType.FOLLOW_UP);
        assertThat(followUp.planOrder()).isNull();
        assertThat(followUp.displayOrder()).isEqualTo(2);
        assertThat(followUp.evaluation().evaluationPhase())
                .isEqualTo(EvaluationPhase.FINAL);
        assertThat(followUp.evaluation().strengths()).containsExactly("补充了扩容");
        assertThat(followUp.evaluation().missingPoints()).isEmpty();

        verify(interviewSessionMapper).getInterviewSessionById(SESSION_ID);
        verify(interviewQuestionMapper).listBySessionId(SESSION_ID);
        verify(interviewAnswerMapper).listByInterviewQuestionIds(List.of(
                MAIN_QUESTION_ID,
                FOLLOW_UP_QUESTION_ID
        ));
        verify(answerEvaluationMapper).listByAnswerIds(argThat(ids ->
                ids.size() == 2
                        && ids.contains(MAIN_ANSWER_ID)
                        && ids.contains(FOLLOW_UP_ANSWER_ID)
        ));
        verifyNoMoreInteractions(
                interviewSessionMapper,
                interviewQuestionMapper,
                interviewAnswerMapper,
                answerEvaluationMapper
        );
        verifyNoWriteInteractions();
    }

    @Test
    void shouldReturnInProgressDetailWithNullAnswerAndEvaluation() {
        InterviewQuestion unanswered = question(
                UNANSWERED_QUESTION_ID,
                InterviewQuestionType.MAIN,
                1,
                1,
                InterviewQuestionStatus.WAITING_ANSWER
        );
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(inProgressSession(USER_ID));
        when(interviewQuestionMapper.listBySessionId(SESSION_ID))
                .thenReturn(List.of(unanswered));
        when(interviewAnswerMapper.listByInterviewQuestionIds(
                List.of(UNANSWERED_QUESTION_ID)
        )).thenReturn(List.of());

        InterviewDetailResponse response = service.getDetail(USER_ID, SESSION_ID);

        assertThat(response.status()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).answer()).isNull();
        assertThat(response.questions().get(0).evaluation()).isNull();
        verify(answerEvaluationMapper, never()).listByAnswerIds(anyList());
        verifyNoWriteInteractions();
    }

    @Test
    void shouldReturnEmptyQuestionListWhenSessionHasNoQuestions() {
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(inProgressSession(USER_ID));
        when(interviewQuestionMapper.listBySessionId(SESSION_ID))
                .thenReturn(List.of());

        InterviewDetailResponse response = service.getDetail(USER_ID, SESSION_ID);

        assertThat(response.questions()).isEmpty();
        verifyNoInteractions(interviewAnswerMapper, answerEvaluationMapper);
        verifyNoWriteInteractions();
    }

    @Test
    void shouldKeepAnswerWithoutEvaluationAsNullEvaluation() {
        InterviewQuestion mainQuestion = question(
                MAIN_QUESTION_ID,
                InterviewQuestionType.MAIN,
                1,
                1,
                InterviewQuestionStatus.ANSWERED
        );
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(inProgressSession(USER_ID));
        when(interviewQuestionMapper.listBySessionId(SESSION_ID))
                .thenReturn(List.of(mainQuestion));
        when(interviewAnswerMapper.listByInterviewQuestionIds(
                List.of(MAIN_QUESTION_ID)
        )).thenReturn(List.of(
                answer(MAIN_ANSWER_ID, MAIN_QUESTION_ID, "尚未评价")
        ));
        when(answerEvaluationMapper.listByAnswerIds(List.of(MAIN_ANSWER_ID)))
                .thenReturn(List.of());

        InterviewDetailResponse response = service.getDetail(USER_ID, SESSION_ID);

        assertThat(response.questions().get(0).answer().answerContent())
                .isEqualTo("尚未评价");
        assertThat(response.questions().get(0).evaluation()).isNull();
        verifyNoWriteInteractions();
    }

    @Test
    void shouldFailWhenPersistedEvaluationJsonIsInvalid() {
        InterviewQuestion mainQuestion = question(
                MAIN_QUESTION_ID,
                InterviewQuestionType.MAIN,
                1,
                1,
                InterviewQuestionStatus.ANSWERED
        );
        stubDetailReads(
                inProgressSession(USER_ID),
                List.of(mainQuestion),
                List.of(answer(MAIN_ANSWER_ID, MAIN_QUESTION_ID, "MAIN 答案")),
                List.of(evaluation(
                        MAIN_ANSWER_ID,
                        MAIN_QUESTION_ID,
                        EvaluationPhase.FINAL,
                        DecisionAction.NEXT_MAIN,
                        "not-json",
                        "[\"缺少扩容\"]"
                ))
        );

        assertThatThrownBy(() -> service.getDetail(USER_ID, SESSION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AnswerEvaluation中的strengths JSON非法");
    }

    @Test
    void shouldFailWhenPersistedMissingPointsJsonIsInvalid() {
        InterviewQuestion mainQuestion = question(
                MAIN_QUESTION_ID,
                InterviewQuestionType.MAIN,
                1,
                1,
                InterviewQuestionStatus.ANSWERED
        );
        stubDetailReads(
                inProgressSession(USER_ID),
                List.of(mainQuestion),
                List.of(answer(MAIN_ANSWER_ID, MAIN_QUESTION_ID, "MAIN 答案")),
                List.of(evaluation(
                        MAIN_ANSWER_ID,
                        MAIN_QUESTION_ID,
                        EvaluationPhase.FINAL,
                        DecisionAction.NEXT_MAIN,
                        "[\"概念准确\"]",
                        "missing-points"
                ))
        );

        assertThatThrownBy(() -> service.getDetail(USER_ID, SESSION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AnswerEvaluation中的missingPoints JSON非法");
    }

    @Test
    void shouldFailWhenUserIdIsMissing() {
        assertThatThrownBy(() -> service.listHistory(null, 1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId必须大于0");
        assertThatThrownBy(() -> service.getDetail(0L, SESSION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId必须大于0");
        verifyNoInteractions(interviewSessionMapper);
    }

    private void stubDetailReads(
            InterviewSession session,
            List<InterviewQuestion> questions,
            List<InterviewAnswer> answers,
            List<AnswerEvaluation> evaluations
    ) {
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(session);
        when(interviewQuestionMapper.listBySessionId(SESSION_ID))
                .thenReturn(questions);
        List<Long> questionIds = questions.stream()
                .map(InterviewQuestion::getId)
                .toList();
        when(interviewAnswerMapper.listByInterviewQuestionIds(questionIds))
                .thenReturn(answers);
        when(answerEvaluationMapper.listByAnswerIds(argThat(ids ->
                ids != null
                        && ids.size() == answers.size()
                        && ids.containsAll(answers.stream()
                        .map(InterviewAnswer::getId)
                        .toList())
        ))).thenReturn(evaluations);
    }

    private InterviewSession completedSession(
            long sessionId,
            long userId,
            LocalDateTime endedAt
    ) {
        LocalDateTime startedAt = endedAt.minusHours(1);
        return InterviewSession.builder()
                .id(sessionId)
                .userId(userId)
                .difficulty(QuestionDifficulty.MEDIUM)
                .status(InterviewSessionStatus.COMPLETED)
                .plannedQuestionCount(5)
                .completedQuestionCount(5)
                .totalScore(new BigDecimal("82.50"))
                .reportStatus(InterviewReportStatus.READY)
                .version(4)
                .currentInterviewQuestionId(99L)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .createdAt(startedAt.minusMinutes(5))
                .build();
    }

    private InterviewSession inProgressSession(long userId) {
        return InterviewSession.builder()
                .id(SESSION_ID)
                .userId(userId)
                .difficulty(QuestionDifficulty.EASY)
                .status(InterviewSessionStatus.IN_PROGRESS)
                .plannedQuestionCount(5)
                .completedQuestionCount(0)
                .reportStatus(InterviewReportStatus.NOT_STARTED)
                .version(1)
                .currentInterviewQuestionId(MAIN_QUESTION_ID)
                .startedAt(LocalDateTime.of(2026, 9, 18, 10, 0))
                .createdAt(LocalDateTime.of(2026, 9, 18, 9, 50))
                .build();
    }

    private InterviewQuestion question(
            long id,
            InterviewQuestionType type,
            Integer planOrder,
            int displayOrder,
            InterviewQuestionStatus status
    ) {
        return InterviewQuestion.builder()
                .id(id)
                .sessionId(SESSION_ID)
                .category(QuestionCategory.JAVA_COLLECTION)
                .knowledgePoint("HashMap")
                .questionContent("问题-" + id)
                .referenceAnswerSnapshot("参考答案-" + id)
                .scoringPointsSnapshot("[{\"id\":101}]")
                .followUpTargetPoints("[101]")
                .questionType(type)
                .parentQuestionId(
                        type == InterviewQuestionType.FOLLOW_UP
                                ? MAIN_QUESTION_ID
                                : null
                )
                .planOrder(planOrder)
                .displayOrder(displayOrder)
                .status(status)
                .build();
    }

    private InterviewAnswer answer(
            long id,
            long questionId,
            String content
    ) {
        return InterviewAnswer.builder()
                .id(id)
                .interviewQuestionId(questionId)
                .answerContent(content)
                .status(InterviewAnswerStatus.EVALUATED)
                .requestId("request-" + id)
                .errorCode(null)
                .submittedAt(LocalDateTime.of(2026, 9, 18, 11, 0))
                .build();
    }

    private AnswerEvaluation evaluation(
            long answerId,
            long mainQuestionId,
            EvaluationPhase phase,
            DecisionAction action,
            String strengths,
            String missingPoints
    ) {
        return AnswerEvaluation.builder()
                .id(answerId + 100)
                .answerId(answerId)
                .mainInterviewQuestionId(mainQuestionId)
                .evaluationPhase(phase)
                .correctnessScore(18)
                .completenessScore(17)
                .depthScore(16)
                .clarityScore(15)
                .practiceScore(14)
                .totalScore(80)
                .level(EvaluationLevel.GOOD.name())
                .strengths(strengths)
                .missingPoints(missingPoints)
                .correction("补充说明")
                .scoringPointResults("[{\"scoringPointId\":101}]")
                .followUpRecommended(phase == EvaluationPhase.INITIAL)
                .suggestedFollowUp("请继续说明")
                .decisionAction(action)
                .retrievalBatchId("batch-" + answerId)
                .llmModel("deepseek-v4-flash")
                .promptVersion("evaluation-prompt-v1")
                .evaluationStandardVersion("evaluation-standard-v1")
                .rawResult("{\"raw\":true}")
                .build();
    }

    private void verifyNoWriteInteractions() {
        verifyNoInteractions(
                interviewSessionService,
                transactionService,
                workflowServiceProvider
        );
        verify(interviewSessionMapper, never()).insertInterviewSession(any());
        verify(interviewSessionMapper, never())
                .completeSession(any(), any(), any());
        verify(interviewQuestionMapper, never()).insertFollowUp(any());
        verify(interviewQuestionMapper, never()).markAnswered(any(), any());
        verify(interviewAnswerMapper, never()).insertInterviewAnswer(any());
        verify(answerEvaluationMapper, never()).insertEvaluation(any());
    }
}
