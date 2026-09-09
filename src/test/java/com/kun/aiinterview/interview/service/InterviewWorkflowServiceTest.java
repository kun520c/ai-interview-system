package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.entity.InterviewAnswer;
import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.enums.InterviewAnswerStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationLevel;
import com.kun.aiinterview.interview.mapper.InterviewAnswerMapper;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.orchestration.EvaluationOrchestrationResult;
import com.kun.aiinterview.interview.orchestration.EvaluationOrchestrationService;
import com.kun.aiinterview.question.enums.QuestionCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewWorkflowServiceTest {

    private static final long ANSWER_ID = 101L;
    private static final long SESSION_ID = 201L;
    private static final long MAIN_QUESTION_ID = 301L;
    private static final long FOLLOW_UP_QUESTION_ID = 302L;
    private static final long NEXT_MAIN_QUESTION_ID = 303L;
    private static final int MAIN_PLAN_ORDER = 2;

    @Mock
    private InterviewAnswerMapper interviewAnswerMapper;

    @Mock
    private InterviewQuestionMapper interviewQuestionMapper;

    @Mock
    private InterviewSessionMapper interviewSessionMapper;

    @Mock
    private InterviewWorkflowTransactionService transactionService;

    @Mock
    private EvaluationOrchestrationService evaluationOrchestrationService;

    private ObjectMapper objectMapper;
    private InterviewWorkflowService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new InterviewWorkflowService(
                interviewAnswerMapper,
                interviewQuestionMapper,
                interviewSessionMapper,
                transactionService,
                evaluationOrchestrationService,
                objectMapper
        );
    }

    @Test
    void shouldMoveMainAnswerToFollowUpWithTargetJson() throws Exception {
        InterviewQuestion main = mainQuestion();
        InterviewQuestion nextMain = nextMainQuestion();
        InterviewSession session = session(MAIN_QUESTION_ID);
        stubMainWorkflow(
                InterviewAnswerStatus.SUBMITTED,
                main,
                session,
                nextMain
        );
        EvaluationOrchestrationResult evaluation = result(
                EvaluationPhase.INITIAL,
                DecisionAction.FOLLOW_UP,
                "请进一步说明扩容过程",
                List.of(101L, 103L)
        );
        when(evaluationOrchestrationService.evaluate(
                ANSWER_ID,
                true
        )).thenReturn(evaluation);

        EvaluationOrchestrationResult actual =
                service.evaluateAnswer(ANSWER_ID);

        assertThat(actual).isSameAs(evaluation);
        ArgumentCaptor<InterviewQuestion> draftCaptor =
                ArgumentCaptor.forClass(InterviewQuestion.class);
        InOrder order = inOrder(
                transactionService,
                evaluationOrchestrationService
        );
        order.verify(transactionService)
                .claimEvaluation(ANSWER_ID);
        order.verify(evaluationOrchestrationService)
                .evaluate(ANSWER_ID, true);
        order.verify(transactionService)
                .moveToFollowUp(
                        eq(ANSWER_ID),
                        same(main),
                        same(session),
                        draftCaptor.capture()
                );

        InterviewQuestion draft = draftCaptor.getValue();
        assertThat(draft.getQuestionContent())
                .isEqualTo("请进一步说明扩容过程");
        List<Long> targetIds = objectMapper.readValue(
                draft.getFollowUpTargetPoints(),
                new TypeReference<>() {
                }
        );
        assertThat(targetIds).containsExactly(101L, 103L);
        verify(transactionService, never())
                .advanceToNextMain(any(), any(), any(), any());
        verify(transactionService, never())
                .finishSession(any(), any(), any());
    }

    @Test
    void shouldAdvanceMainAnswerToNextMain() {
        InterviewQuestion main = mainQuestion();
        InterviewQuestion nextMain = nextMainQuestion();
        InterviewSession session = session(MAIN_QUESTION_ID);
        stubMainWorkflow(
                InterviewAnswerStatus.SUBMITTED,
                main,
                session,
                nextMain
        );
        EvaluationOrchestrationResult evaluation = finalResult(
                DecisionAction.NEXT_MAIN
        );
        when(evaluationOrchestrationService.evaluate(
                ANSWER_ID,
                true
        )).thenReturn(evaluation);

        service.evaluateAnswer(ANSWER_ID);

        verify(interviewQuestionMapper)
                .findNextPendingMainQuestion(
                        SESSION_ID,
                        MAIN_PLAN_ORDER
                );
        InOrder order = inOrder(
                transactionService,
                evaluationOrchestrationService
        );
        order.verify(transactionService)
                .claimEvaluation(ANSWER_ID);
        order.verify(evaluationOrchestrationService)
                .evaluate(ANSWER_ID, true);
        order.verify(transactionService)
                .advanceToNextMain(
                        ANSWER_ID,
                        main,
                        session,
                        nextMain
                );
        verify(transactionService, never())
                .moveToFollowUp(any(), any(), any(), any());
        verify(transactionService, never())
                .finishSession(any(), any(), any());
    }

    @Test
    void shouldFinishAfterLastMainAnswer() {
        InterviewQuestion main = mainQuestion();
        InterviewSession session = session(MAIN_QUESTION_ID);
        stubMainWorkflow(
                InterviewAnswerStatus.SUBMITTED,
                main,
                session,
                null
        );
        when(evaluationOrchestrationService.evaluate(
                ANSWER_ID,
                false
        )).thenReturn(finalResult(DecisionAction.FINISH));

        service.evaluateAnswer(ANSWER_ID);

        verify(transactionService).finishSession(
                ANSWER_ID,
                main,
                session
        );
        verify(transactionService, never())
                .moveToFollowUp(any(), any(), any(), any());
        verify(transactionService, never())
                .advanceToNextMain(any(), any(), any(), any());
    }

    @Test
    void shouldUseParentMainPlanOrderAfterFollowUpAnswer() {
        InterviewQuestion followUp = followUpQuestion(MAIN_QUESTION_ID);
        InterviewQuestion parentMain = mainQuestion();
        InterviewQuestion nextMain = nextMainQuestion();
        InterviewSession session = session(FOLLOW_UP_QUESTION_ID);
        stubFollowUpWorkflow(
                InterviewAnswerStatus.SUBMITTED,
                followUp,
                parentMain,
                session,
                nextMain
        );
        when(evaluationOrchestrationService.evaluate(
                ANSWER_ID,
                true
        )).thenReturn(finalResult(DecisionAction.NEXT_MAIN));

        service.evaluateAnswer(ANSWER_ID);

        verify(interviewQuestionMapper)
                .getInterviewQuestionById(MAIN_QUESTION_ID);
        verify(interviewQuestionMapper)
                .findNextPendingMainQuestion(
                        SESSION_ID,
                        MAIN_PLAN_ORDER
                );
        verify(transactionService).advanceToNextMain(
                ANSWER_ID,
                followUp,
                session,
                nextMain
        );
        verify(transactionService, never())
                .moveToFollowUp(any(), any(), any(), any());
    }

    @Test
    void shouldFinishAfterLastFollowUpAnswer() {
        InterviewQuestion followUp = followUpQuestion(MAIN_QUESTION_ID);
        InterviewQuestion parentMain = mainQuestion();
        InterviewSession session = session(FOLLOW_UP_QUESTION_ID);
        stubFollowUpWorkflow(
                InterviewAnswerStatus.SUBMITTED,
                followUp,
                parentMain,
                session,
                null
        );
        when(evaluationOrchestrationService.evaluate(
                ANSWER_ID,
                false
        )).thenReturn(finalResult(DecisionAction.FINISH));

        service.evaluateAnswer(ANSWER_ID);

        verify(transactionService).finishSession(
                ANSWER_ID,
                followUp,
                session
        );
        verify(transactionService, never())
                .moveToFollowUp(any(), any(), any(), any());
        verify(transactionService, never())
                .advanceToNextMain(any(), any(), any(), any());
    }

    @Test
    void shouldMarkAnswerFailedAndRethrowEvaluationException() {
        stubMainWorkflow(
                InterviewAnswerStatus.SUBMITTED,
                mainQuestion(),
                session(MAIN_QUESTION_ID),
                null
        );
        RuntimeException failure =
                new RuntimeException("DeepSeek调用失败");
        when(evaluationOrchestrationService.evaluate(
                ANSWER_ID,
                false
        )).thenThrow(failure);

        assertThatThrownBy(
                () -> service.evaluateAnswer(ANSWER_ID)
        ).isSameAs(failure);

        InOrder order = inOrder(
                transactionService,
                evaluationOrchestrationService
        );
        order.verify(transactionService)
                .claimEvaluation(ANSWER_ID);
        order.verify(evaluationOrchestrationService)
                .evaluate(ANSWER_ID, false);
        order.verify(transactionService)
                .markEvaluationFailed(
                        ANSWER_ID,
                        "EVALUATION_ERROR"
                );
        verifyNoDecisionTransitions();
    }

    @Test
    void shouldRetryFailedAnswerBeforeEvaluation() {
        InterviewQuestion main = mainQuestion();
        InterviewQuestion nextMain = nextMainQuestion();
        InterviewSession session = session(MAIN_QUESTION_ID);
        stubMainWorkflow(
                InterviewAnswerStatus.FAILED,
                main,
                session,
                nextMain
        );
        when(evaluationOrchestrationService.evaluate(
                ANSWER_ID,
                true
        )).thenReturn(finalResult(DecisionAction.NEXT_MAIN));

        service.evaluateAnswer(ANSWER_ID);

        verify(transactionService).retryEvaluation(ANSWER_ID);
        verify(transactionService, never())
                .claimEvaluation(ANSWER_ID);
        verify(transactionService).advanceToNextMain(
                ANSWER_ID,
                main,
                session,
                nextMain
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = InterviewAnswerStatus.class,
            names = {"EVALUATING", "EVALUATED"}
    )
    void shouldRejectAnswerStatusThatCannotEnterEvaluation(
            InterviewAnswerStatus status
    ) {
        InterviewAnswer answer = answer(status, MAIN_QUESTION_ID);
        InterviewQuestion main = mainQuestion();
        when(interviewAnswerMapper.getInterviewAnswerById(ANSWER_ID))
                .thenReturn(answer);
        when(interviewQuestionMapper.getInterviewQuestionById(
                MAIN_QUESTION_ID
        )).thenReturn(main);
        when(interviewSessionMapper.getInterviewSessionById(
                SESSION_ID
        )).thenReturn(session(MAIN_QUESTION_ID));

        assertThatThrownBy(
                () -> service.evaluateAnswer(ANSWER_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("当前Answer状态不允许进入评价");

        verifyNoInteractions(
                evaluationOrchestrationService,
                transactionService
        );
        verify(interviewQuestionMapper, never())
                .findNextPendingMainQuestion(any(), any());
    }

    @Test
    void shouldRejectAnswerForNonCurrentQuestion() {
        InterviewQuestion main = mainQuestion();
        when(interviewAnswerMapper.getInterviewAnswerById(ANSWER_ID))
                .thenReturn(answer(
                        InterviewAnswerStatus.SUBMITTED,
                        MAIN_QUESTION_ID
                ));
        when(interviewQuestionMapper.getInterviewQuestionById(
                MAIN_QUESTION_ID
        )).thenReturn(main);
        when(interviewSessionMapper.getInterviewSessionById(
                SESSION_ID
        )).thenReturn(session(NEXT_MAIN_QUESTION_ID));

        assertThatThrownBy(
                () -> service.evaluateAnswer(ANSWER_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("当前Answer不属于Session正在处理的问题");

        verifyNoInteractions(
                evaluationOrchestrationService,
                transactionService
        );
    }

    @Test
    void shouldRejectFollowUpWithoutParentQuestionId() {
        InterviewQuestion followUp = followUpQuestion(null);
        stubCurrentQuestionBeforeParentResolution(followUp);

        assertThatThrownBy(
                () -> service.evaluateAnswer(ANSWER_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FOLLOW_UP缺少parentQuestionId");

        verifyNoInteractions(
                evaluationOrchestrationService,
                transactionService
        );
    }

    @Test
    void shouldRejectFollowUpWhenParentDoesNotExist() {
        InterviewQuestion followUp = followUpQuestion(MAIN_QUESTION_ID);
        stubCurrentQuestionBeforeParentResolution(followUp);
        when(interviewQuestionMapper.getInterviewQuestionById(
                MAIN_QUESTION_ID
        )).thenReturn(null);

        assertThatThrownBy(
                () -> service.evaluateAnswer(ANSWER_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FOLLOW_UP对应的MAIN不存在");

        verifyNoInteractions(
                evaluationOrchestrationService,
                transactionService
        );
    }

    @Test
    void shouldRejectFollowUpWhenParentIsNotMain() {
        InterviewQuestion followUp = followUpQuestion(MAIN_QUESTION_ID);
        InterviewQuestion invalidParent =
                followUpQuestion(999L);
        invalidParent.setId(MAIN_QUESTION_ID);
        stubCurrentQuestionBeforeParentResolution(followUp);
        when(interviewQuestionMapper.getInterviewQuestionById(
                MAIN_QUESTION_ID
        )).thenReturn(invalidParent);

        assertThatThrownBy(
                () -> service.evaluateAnswer(ANSWER_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FOLLOW_UP的parent不是MAIN");

        verifyNoInteractions(
                evaluationOrchestrationService,
                transactionService
        );
    }

    @Test
    void shouldRejectFollowUpWhenParentBelongsToAnotherSession() {
        InterviewQuestion followUp = followUpQuestion(MAIN_QUESTION_ID);
        InterviewQuestion invalidParent = mainQuestion();
        invalidParent.setSessionId(999L);
        stubCurrentQuestionBeforeParentResolution(followUp);
        when(interviewQuestionMapper.getInterviewQuestionById(
                MAIN_QUESTION_ID
        )).thenReturn(invalidParent);

        assertThatThrownBy(
                () -> service.evaluateAnswer(ANSWER_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FOLLOW_UP与MAIN不属于同一个Session");

        verifyNoInteractions(
                evaluationOrchestrationService,
                transactionService
        );
    }

    @Test
    void shouldRejectNextMainDecisionWhenNoNextMainExists() {
        stubMainWorkflow(
                InterviewAnswerStatus.SUBMITTED,
                mainQuestion(),
                session(MAIN_QUESTION_ID),
                null
        );
        when(evaluationOrchestrationService.evaluate(
                ANSWER_ID,
                false
        )).thenReturn(finalResult(DecisionAction.NEXT_MAIN));

        assertThatThrownBy(
                () -> service.evaluateAnswer(ANSWER_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "DecisionAction为NEXT_MAIN但不存在下一道MAIN"
                );

        verify(transactionService, never())
                .finishSession(any(), any(), any());
        verify(transactionService, never())
                .advanceToNextMain(any(), any(), any(), any());
        verify(transactionService)
                .markEvaluationFailed(
                        ANSWER_ID,
                        "EVALUATION_ERROR"
                );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectFollowUpWithoutSuggestedQuestion(
            String suggestedFollowUp
    ) {
        stubMainWorkflow(
                InterviewAnswerStatus.SUBMITTED,
                mainQuestion(),
                session(MAIN_QUESTION_ID),
                null
        );
        when(evaluationOrchestrationService.evaluate(
                ANSWER_ID,
                false
        )).thenReturn(result(
                EvaluationPhase.INITIAL,
                DecisionAction.FOLLOW_UP,
                suggestedFollowUp,
                List.of(101L)
        ));

        assertThatThrownBy(
                () -> service.evaluateAnswer(ANSWER_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FOLLOW_UP缺少追问内容");

        verify(transactionService, never())
                .moveToFollowUp(any(), any(), any(), any());
    }

    @Test
    void shouldRejectFollowUpWithoutTargetPointIds() {
        stubMainWorkflow(
                InterviewAnswerStatus.SUBMITTED,
                mainQuestion(),
                session(MAIN_QUESTION_ID),
                null
        );
        when(evaluationOrchestrationService.evaluate(
                ANSWER_ID,
                false
        )).thenReturn(result(
                EvaluationPhase.INITIAL,
                DecisionAction.FOLLOW_UP,
                "请补充说明扩容过程",
                List.of()
        ));

        assertThatThrownBy(
                () -> service.evaluateAnswer(ANSWER_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("评价结果决策非法");

        verify(transactionService, never())
                .moveToFollowUp(any(), any(), any(), any());
    }

    @Test
    void shouldNeverCreateSecondFollowUpForFollowUpAnswer() {
        InterviewQuestion followUp = followUpQuestion(MAIN_QUESTION_ID);
        stubFollowUpWorkflow(
                InterviewAnswerStatus.SUBMITTED,
                followUp,
                mainQuestion(),
                session(FOLLOW_UP_QUESTION_ID),
                null
        );
        when(evaluationOrchestrationService.evaluate(
                ANSWER_ID,
                false
        )).thenReturn(result(
                EvaluationPhase.INITIAL,
                DecisionAction.FOLLOW_UP,
                "继续追问",
                List.of(101L)
        ));

        assertThatThrownBy(
                () -> service.evaluateAnswer(ANSWER_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("只有MAIN评价可以进入FOLLOW_UP");

        verify(transactionService, never())
                .moveToFollowUp(any(), any(), any(), any());
    }

    @Test
    void shouldRejectFinishDecisionWhenNextMainStillExists() {
        stubMainWorkflow(
                InterviewAnswerStatus.SUBMITTED,
                mainQuestion(),
                session(MAIN_QUESTION_ID),
                nextMainQuestion()
        );
        when(evaluationOrchestrationService.evaluate(
                ANSWER_ID,
                true
        )).thenReturn(finalResult(DecisionAction.FINISH));

        assertThatThrownBy(
                () -> service.evaluateAnswer(ANSWER_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "DecisionAction为FINISH但仍存在下一道MAIN"
                );

        verifyNoDecisionTransitions();
    }

    @Test
    void shouldPreservePrimaryFailureWhenMarkFailedAlsoFails() {
        stubMainWorkflow(
                InterviewAnswerStatus.SUBMITTED,
                mainQuestion(),
                session(MAIN_QUESTION_ID),
                null
        );
        RuntimeException primary =
                new RuntimeException("Evaluation失败");
        RuntimeException markFailed =
                new RuntimeException("FAILED CAS失败");
        when(evaluationOrchestrationService.evaluate(
                ANSWER_ID,
                false
        )).thenThrow(primary);
        doThrow(markFailed)
                .when(transactionService)
                .markEvaluationFailed(
                        ANSWER_ID,
                        "EVALUATION_ERROR"
                );

        assertThatThrownBy(
                () -> service.evaluateAnswer(ANSWER_ID)
        )
                .isSameAs(primary)
                .satisfies(exception ->
                        assertThat(exception.getSuppressed())
                                .containsExactly(markFailed)
                );
    }

    private void stubMainWorkflow(
            InterviewAnswerStatus answerStatus,
            InterviewQuestion main,
            InterviewSession session,
            InterviewQuestion nextMain
    ) {
        when(interviewAnswerMapper.getInterviewAnswerById(ANSWER_ID))
                .thenReturn(answer(
                        answerStatus,
                        MAIN_QUESTION_ID
                ));
        when(interviewQuestionMapper.getInterviewQuestionById(
                MAIN_QUESTION_ID
        )).thenReturn(main);
        when(interviewSessionMapper.getInterviewSessionById(
                SESSION_ID
        )).thenReturn(session);
        when(interviewQuestionMapper.findNextPendingMainQuestion(
                SESSION_ID,
                MAIN_PLAN_ORDER
        )).thenReturn(nextMain);
    }

    private void stubFollowUpWorkflow(
            InterviewAnswerStatus answerStatus,
            InterviewQuestion followUp,
            InterviewQuestion parentMain,
            InterviewSession session,
            InterviewQuestion nextMain
    ) {
        stubCurrentQuestionBeforeParentResolution(
                followUp,
                answerStatus,
                session
        );
        when(interviewQuestionMapper.getInterviewQuestionById(
                MAIN_QUESTION_ID
        )).thenReturn(parentMain);
        when(interviewQuestionMapper.findNextPendingMainQuestion(
                SESSION_ID,
                MAIN_PLAN_ORDER
        )).thenReturn(nextMain);
    }

    private void stubCurrentQuestionBeforeParentResolution(
            InterviewQuestion currentQuestion
    ) {
        stubCurrentQuestionBeforeParentResolution(
                currentQuestion,
                InterviewAnswerStatus.SUBMITTED,
                session(FOLLOW_UP_QUESTION_ID)
        );
    }

    private void stubCurrentQuestionBeforeParentResolution(
            InterviewQuestion currentQuestion,
            InterviewAnswerStatus answerStatus,
            InterviewSession session
    ) {
        when(interviewAnswerMapper.getInterviewAnswerById(ANSWER_ID))
                .thenReturn(answer(
                        answerStatus,
                        FOLLOW_UP_QUESTION_ID
                ));
        when(interviewQuestionMapper.getInterviewQuestionById(
                FOLLOW_UP_QUESTION_ID
        )).thenReturn(currentQuestion);
        when(interviewSessionMapper.getInterviewSessionById(
                SESSION_ID
        )).thenReturn(session);
    }

    private InterviewAnswer answer(
            InterviewAnswerStatus status,
            long interviewQuestionId
    ) {
        return InterviewAnswer.builder()
                .id(ANSWER_ID)
                .interviewQuestionId(interviewQuestionId)
                .status(status)
                .build();
    }

    private InterviewQuestion mainQuestion() {
        return InterviewQuestion.builder()
                .id(MAIN_QUESTION_ID)
                .sessionId(SESSION_ID)
                .category(QuestionCategory.JAVA_COLLECTION)
                .knowledgePoint("HashMap")
                .questionType(InterviewQuestionType.MAIN)
                .planOrder(MAIN_PLAN_ORDER)
                .displayOrder(3)
                .status(InterviewQuestionStatus.ANSWERED)
                .build();
    }

    private InterviewQuestion followUpQuestion(Long parentQuestionId) {
        return InterviewQuestion.builder()
                .id(FOLLOW_UP_QUESTION_ID)
                .sessionId(SESSION_ID)
                .questionType(InterviewQuestionType.FOLLOW_UP)
                .parentQuestionId(parentQuestionId)
                .planOrder(null)
                .displayOrder(4)
                .status(InterviewQuestionStatus.ANSWERED)
                .build();
    }

    private InterviewQuestion nextMainQuestion() {
        return InterviewQuestion.builder()
                .id(NEXT_MAIN_QUESTION_ID)
                .sessionId(SESSION_ID)
                .questionType(InterviewQuestionType.MAIN)
                .planOrder(3)
                .displayOrder(5)
                .status(InterviewQuestionStatus.PENDING)
                .build();
    }

    private InterviewSession session(long currentQuestionId) {
        return InterviewSession.builder()
                .id(SESSION_ID)
                .currentInterviewQuestionId(currentQuestionId)
                .version(7)
                .build();
    }

    private EvaluationOrchestrationResult finalResult(
            DecisionAction action
    ) {
        return result(
                EvaluationPhase.FINAL,
                action,
                null,
                List.of()
        );
    }

    private EvaluationOrchestrationResult result(
            EvaluationPhase phase,
            DecisionAction action,
            String suggestedFollowUp,
            List<Long> targetPointIds
    ) {
        return new EvaluationOrchestrationResult(
                401L,
                ANSWER_ID,
                MAIN_QUESTION_ID,
                phase,
                action,
                80,
                EvaluationLevel.GOOD,
                action == DecisionAction.FOLLOW_UP,
                suggestedFollowUp,
                targetPointIds,
                "retrieval-batch-1"
        );
    }

    private void verifyNoDecisionTransitions() {
        verify(transactionService, never())
                .moveToFollowUp(any(), any(), any(), any());
        verify(transactionService, never())
                .advanceToNextMain(any(), any(), any(), any());
        verify(transactionService, never())
                .finishSession(any(), any(), any());
    }
}
