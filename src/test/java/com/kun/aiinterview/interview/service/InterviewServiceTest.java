package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.interview.dto.SubmitInterviewAnswerRequest;
import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.entity.InterviewAnswer;
import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.enums.InterviewAnswerStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationLevel;
import com.kun.aiinterview.interview.mapper.AnswerEvaluationMapper;
import com.kun.aiinterview.interview.mapper.InterviewAnswerMapper;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.vo.InterviewSessionResponse;
import com.kun.aiinterview.interview.vo.SubmitInterviewAnswerResponse;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    private static final long USER_ID = 11L;
    private static final long OTHER_USER_ID = 12L;
    private static final long SESSION_ID = 21L;
    private static final long QUESTION_ID = 31L;
    private static final long NEXT_QUESTION_ID = 32L;
    private static final long ANSWER_ID = 41L;
    private static final String REQUEST_ID = "request-41";

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

    @Mock
    private InterviewWorkflowService workflowService;

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
    void shouldCreateSessionAndExposeOnlyCurrentQuestionFields() {
        InterviewSession session = session(
                InterviewSessionStatus.IN_PROGRESS,
                QUESTION_ID,
                USER_ID
        );
        InterviewQuestion question = question(
                QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.WAITING_ANSWER,
                InterviewQuestionType.MAIN
        );
        when(interviewSessionService.createSession(
                USER_ID,
                QuestionDifficulty.MEDIUM
        )).thenReturn(session);
        when(interviewQuestionMapper.getInterviewQuestionById(
                QUESTION_ID
        )).thenReturn(question);

        InterviewSessionResponse response = service.createOrResume(
                USER_ID,
                QuestionDifficulty.MEDIUM
        );

        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        assertThat(response.currentQuestion().interviewQuestionId())
                .isEqualTo(QUESTION_ID);
        assertThat(response.currentQuestion().content())
                .isEqualTo("问题-31");
    }

    @Test
    void shouldReturnActiveSessionWhenCreateDelegatesResume() {
        InterviewSession active = session(
                InterviewSessionStatus.IN_PROGRESS,
                QUESTION_ID,
                USER_ID
        );
        when(interviewSessionService.createSession(
                USER_ID,
                QuestionDifficulty.HARD
        )).thenReturn(active);
        when(interviewQuestionMapper.getInterviewQuestionById(
                QUESTION_ID
        )).thenReturn(question(
                QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.WAITING_ANSWER,
                InterviewQuestionType.MAIN
        ));

        InterviewSessionResponse response = service.createOrResume(
                USER_ID,
                QuestionDifficulty.HARD
        );

        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        assertThat(response.status())
                .isEqualTo(InterviewSessionStatus.IN_PROGRESS);
    }

    @Test
    void shouldReturnNullWhenThereIsNoActiveSession() {
        when(interviewSessionMapper.getActiveSessionByUserId(USER_ID))
                .thenReturn(null);

        assertThat(service.getCurrent(USER_ID)).isNull();

        verifyNoInteractions(interviewQuestionMapper);
    }

    @Test
    void shouldSubmitNewAnswerThenReturnPersistedEvaluationAndNextMain() {
        InterviewSession initial = session(
                InterviewSessionStatus.IN_PROGRESS,
                QUESTION_ID,
                USER_ID
        );
        InterviewSession advanced = session(
                InterviewSessionStatus.IN_PROGRESS,
                NEXT_QUESTION_ID,
                USER_ID
        );
        InterviewQuestion current = question(
                QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.WAITING_ANSWER,
                InterviewQuestionType.MAIN
        );
        InterviewQuestion next = question(
                NEXT_QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.WAITING_ANSWER,
                InterviewQuestionType.MAIN
        );
        stubInitialSessionAndQuestion(initial, current);
        when(interviewAnswerMapper.getInterviewAnswerByRequestId(
                REQUEST_ID
        )).thenReturn(null);
        when(interviewAnswerMapper.getInterviewAnswerByInterviewQuestionId(
                QUESTION_ID
        )).thenReturn(null);
        when(workflowServiceProvider.getIfAvailable())
                .thenReturn(workflowService);
        when(transactionService.submitAnswer(any(), any()))
                .thenAnswer(invocation -> {
                    InterviewAnswer answer = invocation.getArgument(0);
                    answer.setId(ANSWER_ID);
                    answer.setStatus(InterviewAnswerStatus.SUBMITTED);
                    return answer;
                });
        when(answerEvaluationMapper.getByAnswerId(ANSWER_ID))
                .thenReturn(evaluation(
                        DecisionAction.NEXT_MAIN,
                        EvaluationPhase.FINAL
                ));
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(initial, advanced);
        when(interviewQuestionMapper.getInterviewQuestionById(
                NEXT_QUESTION_ID
        )).thenReturn(next);

        SubmitInterviewAnswerResponse response = service.submitAnswer(
                USER_ID,
                SESSION_ID,
                request()
        );

        assertThat(response.nextAction())
                .isEqualTo(DecisionAction.NEXT_MAIN);
        assertThat(response.sessionStatus())
                .isEqualTo(InterviewSessionStatus.IN_PROGRESS);
        assertThat(response.nextQuestion().interviewQuestionId())
                .isEqualTo(NEXT_QUESTION_ID);
        assertCompleteEvaluation(response);

        InOrder order = inOrder(transactionService, workflowService);
        order.verify(transactionService).submitAnswer(any(), any());
        order.verify(workflowService).evaluateAnswer(ANSWER_ID);
    }

    @Test
    void shouldRejectSessionOwnedByAnotherUser() {
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(session(
                        InterviewSessionStatus.IN_PROGRESS,
                        QUESTION_ID,
                        OTHER_USER_ID
                ));

        assertThatThrownBy(() -> service.submitAnswer(
                USER_ID,
                SESSION_ID,
                request()
        )).isInstanceOf(BusinessException.class)
                .hasMessage("InterviewSession不属于当前用户");

        verifyNoInteractions(
                interviewQuestionMapper,
                interviewAnswerMapper,
                transactionService,
                workflowService
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = InterviewSessionStatus.class,
            names = {"CREATED", "COMPLETED", "CANCELLED"}
    )
    void shouldRejectNormalSubmissionOutsideInProgress(
            InterviewSessionStatus status
    ) {
        InterviewSession session = session(status, QUESTION_ID, USER_ID);
        InterviewQuestion question = question(
                QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.WAITING_ANSWER,
                InterviewQuestionType.MAIN
        );
        stubInitialSessionAndQuestion(session, question);
        when(interviewAnswerMapper.getInterviewAnswerByRequestId(
                REQUEST_ID
        )).thenReturn(null);

        assertThatThrownBy(() -> service.submitAnswer(
                USER_ID,
                SESSION_ID,
                request()
        )).isInstanceOf(BusinessException.class)
                .hasMessage("当前InterviewSession不可提交答案");

        verifyNoInteractions(transactionService, workflowService);
    }

    @Test
    void shouldRejectQuestionThatIsNotCurrent() {
        InterviewSession session = session(
                InterviewSessionStatus.IN_PROGRESS,
                NEXT_QUESTION_ID,
                USER_ID
        );
        InterviewQuestion requested = question(
                QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.PENDING,
                InterviewQuestionType.MAIN
        );
        stubInitialSessionAndQuestion(session, requested);
        when(interviewAnswerMapper.getInterviewAnswerByRequestId(
                REQUEST_ID
        )).thenReturn(null);

        assertThatThrownBy(() -> service.submitAnswer(
                USER_ID,
                SESSION_ID,
                request()
        )).isInstanceOf(BusinessException.class)
                .hasMessage("提交的问题不是当前问题");
    }

    @Test
    void shouldRejectQuestionFromAnotherSession() {
        InterviewSession session = session(
                InterviewSessionStatus.IN_PROGRESS,
                QUESTION_ID,
                USER_ID
        );
        stubInitialSessionAndQuestion(
                session,
                question(
                        QUESTION_ID,
                        999L,
                        InterviewQuestionStatus.WAITING_ANSWER,
                        InterviewQuestionType.MAIN
                )
        );

        assertThatThrownBy(() -> service.submitAnswer(
                USER_ID,
                SESSION_ID,
                request()
        )).isInstanceOf(BusinessException.class)
                .hasMessage("InterviewQuestion不属于当前Session");

        verifyNoInteractions(interviewAnswerMapper, transactionService);
    }

    @Test
    void shouldReplayEvaluatedAnswerFromDatabaseWithoutWorkflow() {
        InterviewSession completed = session(
                InterviewSessionStatus.COMPLETED,
                null,
                USER_ID
        );
        InterviewQuestion answered = question(
                QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.ANSWERED,
                InterviewQuestionType.MAIN
        );
        InterviewAnswer answer = answer(InterviewAnswerStatus.EVALUATED);
        stubInitialSessionAndQuestion(completed, answered);
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(completed);
        when(interviewAnswerMapper.getInterviewAnswerByRequestId(
                REQUEST_ID
        )).thenReturn(answer);
        when(answerEvaluationMapper.getByAnswerId(ANSWER_ID))
                .thenReturn(evaluation(
                        DecisionAction.FINISH,
                        EvaluationPhase.FINAL
                ));

        SubmitInterviewAnswerResponse response = service.submitAnswer(
                USER_ID,
                SESSION_ID,
                request()
        );

        assertThat(response.sessionStatus())
                .isEqualTo(InterviewSessionStatus.COMPLETED);
        assertThat(response.nextQuestion()).isNull();
        assertCompleteEvaluation(response);
        verifyNoInteractions(workflowServiceProvider, workflowService);
    }

    @ParameterizedTest
    @EnumSource(
            value = InterviewAnswerStatus.class,
            names = {"SUBMITTED", "FAILED"}
    )
    void shouldContinueWorkflowForRetryableExistingAnswer(
            InterviewAnswerStatus answerStatus
    ) {
        InterviewSession currentSession = session(
                InterviewSessionStatus.IN_PROGRESS,
                QUESTION_ID,
                USER_ID
        );
        InterviewSession completed = session(
                InterviewSessionStatus.COMPLETED,
                null,
                USER_ID
        );
        InterviewQuestion answered = question(
                QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.ANSWERED,
                InterviewQuestionType.MAIN
        );
        stubInitialSessionAndQuestion(currentSession, answered);
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(currentSession, completed);
        when(interviewAnswerMapper.getInterviewAnswerByRequestId(
                REQUEST_ID
        )).thenReturn(answer(answerStatus));
        when(workflowServiceProvider.getIfAvailable())
                .thenReturn(workflowService);
        when(answerEvaluationMapper.getByAnswerId(ANSWER_ID))
                .thenReturn(evaluation(
                        DecisionAction.FINISH,
                        EvaluationPhase.FINAL
                ));

        SubmitInterviewAnswerResponse response = service.submitAnswer(
                USER_ID,
                SESSION_ID,
                request()
        );

        assertThat(response.sessionStatus())
                .isEqualTo(InterviewSessionStatus.COMPLETED);
        verify(workflowService).evaluateAnswer(ANSWER_ID);
        verify(transactionService, never()).submitAnswer(any(), any());
    }

    @Test
    void shouldRejectEvaluatingAnswerWithoutStartingSecondWorkflow() {
        InterviewSession session = session(
                InterviewSessionStatus.IN_PROGRESS,
                QUESTION_ID,
                USER_ID
        );
        InterviewQuestion answered = question(
                QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.ANSWERED,
                InterviewQuestionType.MAIN
        );
        stubInitialSessionAndQuestion(session, answered);
        when(interviewAnswerMapper.getInterviewAnswerByRequestId(
                REQUEST_ID
        )).thenReturn(answer(InterviewAnswerStatus.EVALUATING));

        assertThatThrownBy(() -> service.submitAnswer(
                USER_ID,
                SESSION_ID,
                request()
        )).isInstanceOf(BusinessException.class)
                .hasMessage("答案正在评估中，请稍后重试");

        verifyNoInteractions(workflowServiceProvider, workflowService);
    }

    @Test
    void shouldRejectRequestIdUsedForAnotherQuestion() {
        InterviewSession session = session(
                InterviewSessionStatus.IN_PROGRESS,
                QUESTION_ID,
                USER_ID
        );
        InterviewQuestion question = question(
                QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.WAITING_ANSWER,
                InterviewQuestionType.MAIN
        );
        stubInitialSessionAndQuestion(session, question);
        InterviewAnswer conflicting = answer(
                InterviewAnswerStatus.EVALUATED
        );
        conflicting.setInterviewQuestionId(NEXT_QUESTION_ID);
        when(interviewAnswerMapper.getInterviewAnswerByRequestId(
                REQUEST_ID
        )).thenReturn(conflicting);

        assertThatThrownBy(() -> service.submitAnswer(
                USER_ID,
                SESSION_ID,
                request()
        )).isInstanceOf(BusinessException.class)
                .hasMessage("requestId已用于其他问题");

        verifyNoInteractions(workflowServiceProvider, workflowService);
    }

    @Test
    void shouldRejectDifferentRequestForQuestionThatAlreadyHasAnswer() {
        InterviewSession session = session(
                InterviewSessionStatus.IN_PROGRESS,
                QUESTION_ID,
                USER_ID
        );
        InterviewQuestion question = question(
                QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.WAITING_ANSWER,
                InterviewQuestionType.MAIN
        );
        stubInitialSessionAndQuestion(session, question);
        when(interviewAnswerMapper.getInterviewAnswerByRequestId(
                REQUEST_ID
        )).thenReturn(null);
        when(interviewAnswerMapper.getInterviewAnswerByInterviewQuestionId(
                QUESTION_ID
        )).thenReturn(answer(InterviewAnswerStatus.SUBMITTED));

        assertThatThrownBy(() -> service.submitAnswer(
                USER_ID,
                SESSION_ID,
                request()
        )).isInstanceOf(BusinessException.class)
                .hasMessage("当前问题已经使用其他requestId提交过答案");

        verifyNoInteractions(workflowServiceProvider, transactionService);
    }

    @Test
    void shouldNotWriteNewAnswerWhenWorkflowBeanIsUnavailable() {
        InterviewSession session = session(
                InterviewSessionStatus.IN_PROGRESS,
                QUESTION_ID,
                USER_ID
        );
        InterviewQuestion question = question(
                QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.WAITING_ANSWER,
                InterviewQuestionType.MAIN
        );
        stubInitialSessionAndQuestion(session, question);
        when(interviewAnswerMapper.getInterviewAnswerByRequestId(
                REQUEST_ID
        )).thenReturn(null);
        when(interviewAnswerMapper.getInterviewAnswerByInterviewQuestionId(
                QUESTION_ID
        )).thenReturn(null);
        when(workflowServiceProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.submitAnswer(
                USER_ID,
                SESSION_ID,
                request()
        )).isInstanceOf(BusinessException.class)
                .hasMessage("面试评价服务当前不可用");

        verifyNoInteractions(transactionService, workflowService);
    }

    @Test
    void shouldReturnPersistedFollowUpQuestion() {
        assertDatabaseQuestionAfterWorkflow(
                DecisionAction.FOLLOW_UP,
                InterviewQuestionType.FOLLOW_UP
        );
    }

    @Test
    void shouldReturnPersistedNextMainQuestion() {
        assertDatabaseQuestionAfterWorkflow(
                DecisionAction.NEXT_MAIN,
                InterviewQuestionType.MAIN
        );
    }

    @Test
    void shouldReturnCompletedSessionWithoutNextQuestion() {
        InterviewSession initial = session(
                InterviewSessionStatus.IN_PROGRESS,
                QUESTION_ID,
                USER_ID
        );
        InterviewSession completed = session(
                InterviewSessionStatus.COMPLETED,
                null,
                USER_ID
        );
        InterviewQuestion current = question(
                QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.WAITING_ANSWER,
                InterviewQuestionType.MAIN
        );
        stubNewAnswerWorkflow(initial, current, completed);
        when(answerEvaluationMapper.getByAnswerId(ANSWER_ID))
                .thenReturn(evaluation(
                        DecisionAction.FINISH,
                        EvaluationPhase.FINAL
                ));

        SubmitInterviewAnswerResponse response = service.submitAnswer(
                USER_ID,
                SESSION_ID,
                request()
        );

        assertThat(response.sessionStatus())
                .isEqualTo(InterviewSessionStatus.COMPLETED);
        assertThat(response.nextQuestion()).isNull();
    }

    @Test
    void shouldRejectInvalidPersistedStrengthsJson() {
        InterviewSession completed = session(
                InterviewSessionStatus.COMPLETED,
                null,
                USER_ID
        );
        InterviewQuestion answered = question(
                QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.ANSWERED,
                InterviewQuestionType.MAIN
        );
        stubInitialSessionAndQuestion(completed, answered);
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(completed);
        when(interviewAnswerMapper.getInterviewAnswerByRequestId(
                REQUEST_ID
        )).thenReturn(answer(InterviewAnswerStatus.EVALUATED));
        AnswerEvaluation evaluation = evaluation(
                DecisionAction.FINISH,
                EvaluationPhase.FINAL
        );
        evaluation.setStrengths("not-json");
        when(answerEvaluationMapper.getByAnswerId(ANSWER_ID))
                .thenReturn(evaluation);

        assertThatThrownBy(() -> service.submitAnswer(
                USER_ID,
                SESSION_ID,
                request()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("AnswerEvaluation中的strengths JSON非法");
    }

    @Test
    void shouldRecoverSameIdempotentRequestAfterDuplicateKey() {
        InterviewSession initial = session(
                InterviewSessionStatus.IN_PROGRESS,
                QUESTION_ID,
                USER_ID
        );
        InterviewSession completed = session(
                InterviewSessionStatus.COMPLETED,
                null,
                USER_ID
        );
        InterviewQuestion current = question(
                QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.WAITING_ANSWER,
                InterviewQuestionType.MAIN
        );
        stubInitialSessionAndQuestion(initial, current);
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(initial, completed, completed);
        when(interviewAnswerMapper.getInterviewAnswerByRequestId(
                REQUEST_ID
        )).thenReturn(
                null,
                answer(InterviewAnswerStatus.EVALUATED)
        );
        when(interviewAnswerMapper.getInterviewAnswerByInterviewQuestionId(
                QUESTION_ID
        )).thenReturn(null);
        when(workflowServiceProvider.getIfAvailable())
                .thenReturn(workflowService);
        when(transactionService.submitAnswer(any(), any()))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(answerEvaluationMapper.getByAnswerId(ANSWER_ID))
                .thenReturn(evaluation(
                        DecisionAction.FINISH,
                        EvaluationPhase.FINAL
                ));

        SubmitInterviewAnswerResponse response = service.submitAnswer(
                USER_ID,
                SESSION_ID,
                request()
        );

        assertThat(response.nextAction()).isEqualTo(DecisionAction.FINISH);
        verifyNoInteractions(workflowService);
    }

    private void assertDatabaseQuestionAfterWorkflow(
            DecisionAction action,
            InterviewQuestionType nextType
    ) {
        InterviewSession initial = session(
                InterviewSessionStatus.IN_PROGRESS,
                QUESTION_ID,
                USER_ID
        );
        InterviewSession advanced = session(
                InterviewSessionStatus.IN_PROGRESS,
                NEXT_QUESTION_ID,
                USER_ID
        );
        InterviewQuestion current = question(
                QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.WAITING_ANSWER,
                InterviewQuestionType.MAIN
        );
        InterviewQuestion next = question(
                NEXT_QUESTION_ID,
                SESSION_ID,
                InterviewQuestionStatus.WAITING_ANSWER,
                nextType
        );
        stubNewAnswerWorkflow(initial, current, advanced);
        when(answerEvaluationMapper.getByAnswerId(ANSWER_ID))
                .thenReturn(evaluation(action, EvaluationPhase.INITIAL));
        when(interviewQuestionMapper.getInterviewQuestionById(
                NEXT_QUESTION_ID
        )).thenReturn(next);

        SubmitInterviewAnswerResponse response = service.submitAnswer(
                USER_ID,
                SESSION_ID,
                request()
        );

        assertThat(response.nextAction()).isEqualTo(action);
        assertThat(response.nextQuestion().interviewQuestionId())
                .isEqualTo(NEXT_QUESTION_ID);
        assertThat(response.nextQuestion().questionType())
                .isEqualTo(nextType);
    }

    private void stubNewAnswerWorkflow(
            InterviewSession initial,
            InterviewQuestion current,
            InterviewSession latest
    ) {
        stubInitialSessionAndQuestion(initial, current);
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(initial, latest);
        when(interviewAnswerMapper.getInterviewAnswerByRequestId(
                REQUEST_ID
        )).thenReturn(null);
        when(interviewAnswerMapper.getInterviewAnswerByInterviewQuestionId(
                QUESTION_ID
        )).thenReturn(null);
        when(workflowServiceProvider.getIfAvailable())
                .thenReturn(workflowService);
        when(transactionService.submitAnswer(any(), any()))
                .thenAnswer(invocation -> {
                    InterviewAnswer answer = invocation.getArgument(0);
                    answer.setId(ANSWER_ID);
                    answer.setStatus(InterviewAnswerStatus.SUBMITTED);
                    return answer;
                });
    }

    private void stubInitialSessionAndQuestion(
            InterviewSession session,
            InterviewQuestion question
    ) {
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(session);
        when(interviewQuestionMapper.getInterviewQuestionById(QUESTION_ID))
                .thenReturn(question);
    }

    private SubmitInterviewAnswerRequest request() {
        return new SubmitInterviewAnswerRequest(
                QUESTION_ID,
                "HashMap使用数组、链表和红黑树。",
                REQUEST_ID
        );
    }

    private InterviewSession session(
            InterviewSessionStatus status,
            Long currentQuestionId,
            Long userId
    ) {
        return InterviewSession.builder()
                .id(SESSION_ID)
                .userId(userId)
                .difficulty(QuestionDifficulty.MEDIUM)
                .status(status)
                .currentInterviewQuestionId(currentQuestionId)
                .plannedQuestionCount(3)
                .completedQuestionCount(
                        status == InterviewSessionStatus.COMPLETED ? 3 : 0
                )
                .version(1)
                .build();
    }

    private InterviewQuestion question(
            long id,
            long sessionId,
            InterviewQuestionStatus status,
            InterviewQuestionType type
    ) {
        return InterviewQuestion.builder()
                .id(id)
                .sessionId(sessionId)
                .category(QuestionCategory.JAVA_COLLECTION)
                .knowledgePoint("集合")
                .questionContent("问题-" + id)
                .questionType(type)
                .planOrder(1)
                .displayOrder(1)
                .status(status)
                .build();
    }

    private InterviewAnswer answer(InterviewAnswerStatus status) {
        return InterviewAnswer.builder()
                .id(ANSWER_ID)
                .interviewQuestionId(QUESTION_ID)
                .answerContent("已保存答案")
                .requestId(REQUEST_ID)
                .status(status)
                .build();
    }

    private AnswerEvaluation evaluation(
            DecisionAction action,
            EvaluationPhase phase
    ) {
        return AnswerEvaluation.builder()
                .id(51L)
                .answerId(ANSWER_ID)
                .mainInterviewQuestionId(QUESTION_ID)
                .evaluationPhase(phase)
                .correctnessScore(18)
                .completenessScore(17)
                .depthScore(16)
                .clarityScore(15)
                .practiceScore(14)
                .totalScore(80)
                .level(EvaluationLevel.GOOD.name())
                .strengths("[\"概念准确\",\"结构清晰\"]")
                .missingPoints("[\"缺少扩容细节\"]")
                .correction("补充阈值与扩容过程")
                .decisionAction(action)
                .build();
    }

    private void assertCompleteEvaluation(
            SubmitInterviewAnswerResponse response
    ) {
        assertThat(response.evaluation().correctnessScore()).isEqualTo(18);
        assertThat(response.evaluation().completenessScore()).isEqualTo(17);
        assertThat(response.evaluation().depthScore()).isEqualTo(16);
        assertThat(response.evaluation().clarityScore()).isEqualTo(15);
        assertThat(response.evaluation().practiceScore()).isEqualTo(14);
        assertThat(response.evaluation().totalScore()).isEqualTo(80);
        assertThat(response.evaluation().level()).isEqualTo(EvaluationLevel.GOOD);
        assertThat(response.evaluation().strengths())
                .containsExactly("概念准确", "结构清晰");
        assertThat(response.evaluation().missingPoints())
                .containsExactly("缺少扩容细节");
        assertThat(response.evaluation().correction())
                .isEqualTo("补充阈值与扩容过程");
    }
}
