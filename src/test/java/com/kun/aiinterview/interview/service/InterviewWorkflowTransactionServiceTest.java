package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.InterviewAnswer;
import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewAnswerStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.mapper.InterviewAnswerMapper;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.question.enums.QuestionCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewWorkflowTransactionServiceTest {

    private static final long ANSWER_ID = 101L;
    private static final long SESSION_ID = 201L;
    private static final long MAIN_QUESTION_ID = 301L;
    private static final long FOLLOW_UP_QUESTION_ID = 302L;
    private static final long NEXT_MAIN_QUESTION_ID = 303L;
    private static final int SESSION_VERSION = 7;

    @Mock
    private InterviewAnswerMapper interviewAnswerMapper;

    @Mock
    private InterviewQuestionMapper interviewQuestionMapper;

    @Mock
    private InterviewSessionMapper interviewSessionMapper;

    private InterviewWorkflowTransactionService service;

    @BeforeEach
    void setUp() {
        service = new InterviewWorkflowTransactionService(
                interviewAnswerMapper,
                interviewQuestionMapper,
                interviewSessionMapper
        );
    }

    @Test
    void shouldSubmitAnswerAndMarkQuestionAnsweredInOrder() {
        InterviewAnswer answer = submittedAnswerDraft();
        InterviewQuestion currentQuestion = mainQuestion();
        when(interviewAnswerMapper.insertInterviewAnswer(same(answer)))
                .thenAnswer(invocation -> {
                    answer.setId(ANSWER_ID);
                    return 1;
                });
        when(interviewQuestionMapper.markAnswered(
                MAIN_QUESTION_ID,
                SESSION_ID
        )).thenReturn(1);

        InterviewAnswer result = service.submitAnswer(
                answer,
                currentQuestion
        );

        assertThat(result).isSameAs(answer);
        assertThat(answer.getId()).isEqualTo(ANSWER_ID);
        assertThat(answer.getInterviewQuestionId())
                .isEqualTo(MAIN_QUESTION_ID);
        assertThat(answer.getStatus())
                .isEqualTo(InterviewAnswerStatus.SUBMITTED);
        assertThat(answer.getErrorCode()).isNull();
        assertThat(answer.getSubmittedAt()).isNotNull();

        InOrder order = inOrder(
                interviewAnswerMapper,
                interviewQuestionMapper
        );
        order.verify(interviewAnswerMapper)
                .insertInterviewAnswer(answer);
        order.verify(interviewQuestionMapper)
                .markAnswered(MAIN_QUESTION_ID, SESSION_ID);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void shouldStopSubmissionWhenAnswerInsertCountIsUnexpected(
            int affectedRows
    ) {
        InterviewAnswer answer = submittedAnswerDraft();
        when(interviewAnswerMapper.insertInterviewAnswer(answer))
                .thenReturn(affectedRows);

        assertThatThrownBy(
                () -> service.submitAnswer(answer, mainQuestion())
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Answer写入失败");

        verifyNoInteractions(interviewQuestionMapper);
    }

    @Test
    void shouldStopSubmissionWhenGeneratedKeyIsMissing() {
        InterviewAnswer answer = submittedAnswerDraft();
        when(interviewAnswerMapper.insertInterviewAnswer(answer))
                .thenReturn(1);

        assertThatThrownBy(
                () -> service.submitAnswer(answer, mainQuestion())
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Answer主键未回填");

        verifyNoInteractions(interviewQuestionMapper);
    }

    @Test
    void shouldFailSubmissionWhenQuestionCasMisses() {
        InterviewAnswer answer = submittedAnswerDraft();
        when(interviewAnswerMapper.insertInterviewAnswer(answer))
                .thenAnswer(invocation -> {
                    answer.setId(ANSWER_ID);
                    return 1;
                });
        when(interviewQuestionMapper.markAnswered(
                MAIN_QUESTION_ID,
                SESSION_ID
        )).thenReturn(0);

        assertThatThrownBy(
                () -> service.submitAnswer(answer, mainQuestion())
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Question状态更新为ANSWERED失败");
    }

    @Test
    void shouldClaimAndRetryEvaluationThroughSeparateCasMethods() {
        when(interviewAnswerMapper.claimEvaluation(ANSWER_ID))
                .thenReturn(1);
        when(interviewAnswerMapper.retryEvaluation(ANSWER_ID))
                .thenReturn(1);

        service.claimEvaluation(ANSWER_ID);
        service.retryEvaluation(ANSWER_ID);

        verify(interviewAnswerMapper).claimEvaluation(ANSWER_ID);
        verify(interviewAnswerMapper).retryEvaluation(ANSWER_ID);
        verifyNoInteractions(
                interviewQuestionMapper,
                interviewSessionMapper
        );
    }

    @Test
    void shouldRejectFailedEvaluationRetryCas() {
        when(interviewAnswerMapper.retryEvaluation(ANSWER_ID))
                .thenReturn(0);

        assertThatThrownBy(
                () -> service.retryEvaluation(ANSWER_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Answer评价重试资格抢占失败");

        verifyNoInteractions(
                interviewQuestionMapper,
                interviewSessionMapper
        );
    }

    @Test
    void shouldReturnWhetherStaleEvaluatingReclaimWon() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        when(interviewAnswerMapper.reclaimStaleEvaluating(ANSWER_ID, cutoff))
                .thenReturn(1, 0);

        assertThat(service.tryReclaimStaleEvaluating(ANSWER_ID, cutoff)).isTrue();
        assertThat(service.tryReclaimStaleEvaluating(ANSWER_ID, cutoff)).isFalse();

        verify(interviewAnswerMapper, times(2))
                .reclaimStaleEvaluating(ANSWER_ID, cutoff);
        verifyNoInteractions(
                interviewQuestionMapper,
                interviewSessionMapper
        );
    }

    @Test
    void shouldRejectBlankIdentifiersForStaleEvaluatingReclaim() {
        assertThatThrownBy(
                () -> service.tryReclaimStaleEvaluating(null, LocalDateTime.now())
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("answerId和cutoff不能为空");
        assertThatThrownBy(
                () -> service.tryReclaimStaleEvaluating(ANSWER_ID, null)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("answerId和cutoff不能为空");

        verifyNoInteractions(
                interviewAnswerMapper,
                interviewQuestionMapper,
                interviewSessionMapper
        );
    }

    @Test
    void shouldCreateAndActivateFollowUpWithBackendControlledOrder() {
        InterviewQuestion parentMain = mainQuestion();
        InterviewSession session = session(MAIN_QUESTION_ID);
        InterviewQuestion draft = followUpDraft();
        when(interviewQuestionMapper.getFollowUpByParentQuestionId(
                MAIN_QUESTION_ID
        )).thenReturn(null);
        when(interviewQuestionMapper.insertFollowUp(same(draft)))
                .thenAnswer(invocation -> {
                    draft.setId(FOLLOW_UP_QUESTION_ID);
                    return 1;
                });
        when(interviewAnswerMapper.markEvaluated(ANSWER_ID))
                .thenReturn(1);
        when(interviewQuestionMapper.markWaitingAnswer(
                FOLLOW_UP_QUESTION_ID,
                SESSION_ID
        )).thenReturn(1);
        when(interviewSessionMapper.moveToFollowUp(
                SESSION_ID,
                SESSION_VERSION,
                MAIN_QUESTION_ID,
                FOLLOW_UP_QUESTION_ID
        )).thenReturn(1);

        InterviewQuestion result = service.moveToFollowUp(
                ANSWER_ID,
                parentMain,
                session,
                draft
        );

        assertThat(result).isSameAs(draft);
        assertThat(draft.getId()).isEqualTo(FOLLOW_UP_QUESTION_ID);
        assertThat(draft.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(draft.getQuestionId()).isNull();
        assertThat(draft.getCategory())
                .isEqualTo(parentMain.getCategory());
        assertThat(draft.getKnowledgePoint())
                .isEqualTo(parentMain.getKnowledgePoint());
        assertThat(draft.getReferenceAnswerSnapshot()).isNull();
        assertThat(draft.getScoringPointsSnapshot()).isNull();
        assertThat(draft.getQuestionType())
                .isEqualTo(InterviewQuestionType.FOLLOW_UP);
        assertThat(draft.getParentQuestionId())
                .isEqualTo(MAIN_QUESTION_ID);
        assertThat(draft.getPlanOrder()).isNull();
        assertThat(draft.getDisplayOrder())
                .isEqualTo(parentMain.getDisplayOrder() + 1);
        assertThat(draft.getStatus())
                .isEqualTo(InterviewQuestionStatus.PENDING);

        InOrder order = inOrder(
                interviewQuestionMapper,
                interviewAnswerMapper,
                interviewSessionMapper
        );
        order.verify(interviewQuestionMapper)
                .getFollowUpByParentQuestionId(MAIN_QUESTION_ID);
        order.verify(interviewQuestionMapper)
                .insertFollowUp(draft);
        order.verify(interviewAnswerMapper)
                .markEvaluated(ANSWER_ID);
        order.verify(interviewQuestionMapper)
                .markWaitingAnswer(
                        FOLLOW_UP_QUESTION_ID,
                        SESSION_ID
                );
        order.verify(interviewSessionMapper)
                .moveToFollowUp(
                        SESSION_ID,
                        SESSION_VERSION,
                        MAIN_QUESTION_ID,
                        FOLLOW_UP_QUESTION_ID
                );
    }

    @Test
    void shouldReuseExistingPendingFollowUpDuringRecovery() {
        InterviewQuestion existingFollowUp = pendingFollowUp();
        when(interviewQuestionMapper.getFollowUpByParentQuestionId(
                MAIN_QUESTION_ID
        )).thenReturn(existingFollowUp);
        when(interviewAnswerMapper.markEvaluated(ANSWER_ID))
                .thenReturn(1);
        when(interviewQuestionMapper.markWaitingAnswer(
                FOLLOW_UP_QUESTION_ID,
                SESSION_ID
        )).thenReturn(1);
        when(interviewSessionMapper.moveToFollowUp(
                SESSION_ID,
                SESSION_VERSION,
                MAIN_QUESTION_ID,
                FOLLOW_UP_QUESTION_ID
        )).thenReturn(1);

        InterviewQuestion result = service.moveToFollowUp(
                ANSWER_ID,
                mainQuestion(),
                session(MAIN_QUESTION_ID),
                followUpDraft()
        );

        assertThat(result).isSameAs(existingFollowUp);
        verify(interviewQuestionMapper, never())
                .insertFollowUp(any());
        verify(interviewAnswerMapper)
                .markEvaluated(ANSWER_ID);
        verify(interviewQuestionMapper)
                .markWaitingAnswer(
                        FOLLOW_UP_QUESTION_ID,
                        SESSION_ID
                );
        verify(interviewSessionMapper)
                .moveToFollowUp(
                        SESSION_ID,
                        SESSION_VERSION,
                        MAIN_QUESTION_ID,
                        FOLLOW_UP_QUESTION_ID
                );
    }

    @Test
    void shouldReturnAlreadyActivatedFollowUpIdempotently() {
        InterviewQuestion existingFollowUp = pendingFollowUp();
        existingFollowUp.setStatus(
                InterviewQuestionStatus.WAITING_ANSWER
        );
        when(interviewQuestionMapper.getFollowUpByParentQuestionId(
                MAIN_QUESTION_ID
        )).thenReturn(existingFollowUp);

        InterviewQuestion result = service.moveToFollowUp(
                ANSWER_ID,
                mainQuestion(),
                session(FOLLOW_UP_QUESTION_ID),
                followUpDraft()
        );

        assertThat(result).isSameAs(existingFollowUp);
        verify(interviewQuestionMapper, never())
                .insertFollowUp(any());
        verify(interviewAnswerMapper, never())
                .markEvaluated(any());
        verify(interviewQuestionMapper, never())
                .markWaitingAnswer(any(), any());
        verifyNoInteractions(interviewSessionMapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void shouldStopFollowUpWhenInsertCountIsUnexpected(
            int affectedRows
    ) {
        InterviewQuestion draft = followUpDraft();
        when(interviewQuestionMapper.getFollowUpByParentQuestionId(
                MAIN_QUESTION_ID
        )).thenReturn(null);
        when(interviewQuestionMapper.insertFollowUp(draft))
                .thenReturn(affectedRows);

        assertThatThrownBy(
                () -> service.moveToFollowUp(
                        ANSWER_ID,
                        mainQuestion(),
                        session(MAIN_QUESTION_ID),
                        draft
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FOLLOW_UP问题写入失败");

        verifyNoInteractions(
                interviewAnswerMapper,
                interviewSessionMapper
        );
    }

    @Test
    void shouldStopFollowUpWhenGeneratedKeyIsMissing() {
        InterviewQuestion draft = followUpDraft();
        when(interviewQuestionMapper.getFollowUpByParentQuestionId(
                MAIN_QUESTION_ID
        )).thenReturn(null);
        when(interviewQuestionMapper.insertFollowUp(draft))
                .thenReturn(1);

        assertThatThrownBy(
                () -> service.moveToFollowUp(
                        ANSWER_ID,
                        mainQuestion(),
                        session(MAIN_QUESTION_ID),
                        draft
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FOLLOW_UP问题主键未回填");

        verifyNoInteractions(
                interviewAnswerMapper,
                interviewSessionMapper
        );
    }

    @Test
    void shouldStopFollowUpWhenAnswerCasMisses() {
        InterviewQuestion draft = stubInsertedFollowUp();
        when(interviewAnswerMapper.markEvaluated(ANSWER_ID))
                .thenReturn(0);

        assertThatThrownBy(
                () -> service.moveToFollowUp(
                        ANSWER_ID,
                        mainQuestion(),
                        session(MAIN_QUESTION_ID),
                        draft
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Answer状态更新为EVALUATED失败");

        verify(interviewQuestionMapper, never())
                .markWaitingAnswer(any(), any());
        verifyNoInteractions(interviewSessionMapper);
    }

    @Test
    void shouldStopFollowUpWhenQuestionActivationCasMisses() {
        InterviewQuestion draft = stubInsertedFollowUp();
        when(interviewAnswerMapper.markEvaluated(ANSWER_ID))
                .thenReturn(1);
        when(interviewQuestionMapper.markWaitingAnswer(
                FOLLOW_UP_QUESTION_ID,
                SESSION_ID
        )).thenReturn(0);

        assertThatThrownBy(
                () -> service.moveToFollowUp(
                        ANSWER_ID,
                        mainQuestion(),
                        session(MAIN_QUESTION_ID),
                        draft
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FOLLOW_UP问题激活失败");

        verifyNoInteractions(interviewSessionMapper);
    }

    @Test
    void shouldRejectFollowUpWhenSessionCasMisses() {
        InterviewQuestion draft = stubInsertedFollowUp();
        when(interviewAnswerMapper.markEvaluated(ANSWER_ID))
                .thenReturn(1);
        when(interviewQuestionMapper.markWaitingAnswer(
                FOLLOW_UP_QUESTION_ID,
                SESSION_ID
        )).thenReturn(1);
        when(interviewSessionMapper.moveToFollowUp(
                SESSION_ID,
                SESSION_VERSION,
                MAIN_QUESTION_ID,
                FOLLOW_UP_QUESTION_ID
        )).thenReturn(0);

        assertThatThrownBy(
                () -> service.moveToFollowUp(
                        ANSWER_ID,
                        mainQuestion(),
                        session(MAIN_QUESTION_ID),
                        draft
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session推进到FOLLOW_UP失败");
    }

    @Test
    void shouldAdvanceToNextMainInOrder() {
        InterviewQuestion currentQuestion = mainQuestion();
        InterviewSession session = session(MAIN_QUESTION_ID);
        InterviewQuestion nextMain = nextMainQuestion();
        when(interviewAnswerMapper.markEvaluated(ANSWER_ID))
                .thenReturn(1);
        when(interviewQuestionMapper.markWaitingAnswer(
                NEXT_MAIN_QUESTION_ID,
                SESSION_ID
        )).thenReturn(1);
        when(interviewSessionMapper.advanceToNextMain(
                SESSION_ID,
                SESSION_VERSION,
                MAIN_QUESTION_ID,
                NEXT_MAIN_QUESTION_ID
        )).thenReturn(1);

        service.advanceToNextMain(
                ANSWER_ID,
                currentQuestion,
                session,
                nextMain
        );

        InOrder order = inOrder(
                interviewAnswerMapper,
                interviewQuestionMapper,
                interviewSessionMapper
        );
        order.verify(interviewAnswerMapper)
                .markEvaluated(ANSWER_ID);
        order.verify(interviewQuestionMapper)
                .markWaitingAnswer(
                        NEXT_MAIN_QUESTION_ID,
                        SESSION_ID
                );
        order.verify(interviewSessionMapper)
                .advanceToNextMain(
                        SESSION_ID,
                        SESSION_VERSION,
                        MAIN_QUESTION_ID,
                        NEXT_MAIN_QUESTION_ID
                );
    }

    @Test
    void shouldStopNextMainWhenAnswerCasMisses() {
        when(interviewAnswerMapper.markEvaluated(ANSWER_ID))
                .thenReturn(0);

        assertThatThrownBy(
                () -> service.advanceToNextMain(
                        ANSWER_ID,
                        mainQuestion(),
                        session(MAIN_QUESTION_ID),
                        nextMainQuestion()
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Answer状态更新为EVALUATED失败");

        verifyNoInteractions(
                interviewQuestionMapper,
                interviewSessionMapper
        );
    }

    @Test
    void shouldStopNextMainWhenQuestionActivationCasMisses() {
        when(interviewAnswerMapper.markEvaluated(ANSWER_ID))
                .thenReturn(1);
        when(interviewQuestionMapper.markWaitingAnswer(
                NEXT_MAIN_QUESTION_ID,
                SESSION_ID
        )).thenReturn(0);

        assertThatThrownBy(
                () -> service.advanceToNextMain(
                        ANSWER_ID,
                        mainQuestion(),
                        session(MAIN_QUESTION_ID),
                        nextMainQuestion()
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("下一道MAIN激活失败");

        verifyNoInteractions(interviewSessionMapper);
    }

    @Test
    void shouldRejectNextMainWhenSessionCasMisses() {
        when(interviewAnswerMapper.markEvaluated(ANSWER_ID))
                .thenReturn(1);
        when(interviewQuestionMapper.markWaitingAnswer(
                NEXT_MAIN_QUESTION_ID,
                SESSION_ID
        )).thenReturn(1);
        when(interviewSessionMapper.advanceToNextMain(
                SESSION_ID,
                SESSION_VERSION,
                MAIN_QUESTION_ID,
                NEXT_MAIN_QUESTION_ID
        )).thenReturn(0);

        assertThatThrownBy(
                () -> service.advanceToNextMain(
                        ANSWER_ID,
                        mainQuestion(),
                        session(MAIN_QUESTION_ID),
                        nextMainQuestion()
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session推进到下一道MAIN失败");
    }

    @Test
    void shouldFinishSessionAfterAnswerBecomesEvaluated() {
        when(interviewAnswerMapper.markEvaluated(ANSWER_ID))
                .thenReturn(1);
        when(interviewSessionMapper.completeSession(
                SESSION_ID,
                SESSION_VERSION,
                MAIN_QUESTION_ID
        )).thenReturn(1);

        service.finishSession(
                ANSWER_ID,
                mainQuestion(),
                session(MAIN_QUESTION_ID)
        );

        InOrder order = inOrder(
                interviewAnswerMapper,
                interviewSessionMapper
        );
        order.verify(interviewAnswerMapper)
                .markEvaluated(ANSWER_ID);
        order.verify(interviewSessionMapper)
                .completeSession(
                        SESSION_ID,
                        SESSION_VERSION,
                        MAIN_QUESTION_ID
                );
    }

    @Test
    void shouldStopFinishWhenAnswerCasMisses() {
        when(interviewAnswerMapper.markEvaluated(ANSWER_ID))
                .thenReturn(0);

        assertThatThrownBy(
                () -> service.finishSession(
                        ANSWER_ID,
                        mainQuestion(),
                        session(MAIN_QUESTION_ID)
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Answer状态更新为EVALUATED失败");

        verifyNoInteractions(interviewSessionMapper);
    }

    @Test
    void shouldRejectFinishWhenSessionCasMisses() {
        when(interviewAnswerMapper.markEvaluated(ANSWER_ID))
                .thenReturn(1);
        when(interviewSessionMapper.completeSession(
                SESSION_ID,
                SESSION_VERSION,
                MAIN_QUESTION_ID
        )).thenReturn(0);

        assertThatThrownBy(
                () -> service.finishSession(
                        ANSWER_ID,
                        mainQuestion(),
                        session(MAIN_QUESTION_ID)
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session完成失败");
    }

    @Test
    void shouldRejectNextQuestionThatIsNotMain() {
        InterviewQuestion invalidNext = pendingFollowUp();

        assertThatThrownBy(
                () -> service.advanceToNextMain(
                        ANSWER_ID,
                        mainQuestion(),
                        session(MAIN_QUESTION_ID),
                        invalidNext
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("下一道问题必须是带planOrder的MAIN");

        verifyNoInteractions(
                interviewAnswerMapper,
                interviewQuestionMapper,
                interviewSessionMapper
        );
    }

    @Test
    void shouldRejectNextMainFromAnotherSession() {
        InterviewQuestion invalidNext = nextMainQuestion();
        invalidNext.setSessionId(999L);

        assertThatThrownBy(
                () -> service.advanceToNextMain(
                        ANSWER_ID,
                        mainQuestion(),
                        session(MAIN_QUESTION_ID),
                        invalidNext
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("下一道MAIN不属于当前Session");

        verifyNoInteractions(
                interviewAnswerMapper,
                interviewQuestionMapper,
                interviewSessionMapper
        );
    }

    @Test
    void shouldRejectFinishWhenSessionSnapshotPointsElsewhere() {
        assertThatThrownBy(
                () -> service.finishSession(
                        ANSWER_ID,
                        mainQuestion(),
                        session(NEXT_MAIN_QUESTION_ID)
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session当前问题与待推进问题不一致");

        verifyNoInteractions(
                interviewAnswerMapper,
                interviewQuestionMapper,
                interviewSessionMapper
        );
    }

    @Test
    void shouldRejectFollowUpBeforeInsertWhenSessionNoLongerPointsToMain() {
        when(interviewQuestionMapper.getFollowUpByParentQuestionId(
                MAIN_QUESTION_ID
        )).thenReturn(null);

        assertThatThrownBy(
                () -> service.moveToFollowUp(
                        ANSWER_ID,
                        mainQuestion(),
                        session(NEXT_MAIN_QUESTION_ID),
                        followUpDraft()
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session当前问题不是目标MAIN");

        verify(interviewQuestionMapper, never())
                .insertFollowUp(any());
        verifyNoInteractions(
                interviewAnswerMapper,
                interviewSessionMapper
        );
    }

    @Test
    void shouldRequestTransactionRollbackWhenLaterSubmissionStepFails() {
        PlatformTransactionManager transactionManager =
                mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus =
                mock(TransactionStatus.class);
        when(transactionManager.getTransaction(
                any(TransactionDefinition.class)
        )).thenReturn(transactionStatus);

        InterviewAnswer answer = submittedAnswerDraft();
        when(interviewAnswerMapper.insertInterviewAnswer(answer))
                .thenAnswer(invocation -> {
                    answer.setId(ANSWER_ID);
                    return 1;
                });
        when(interviewQuestionMapper.markAnswered(
                MAIN_QUESTION_ID,
                SESSION_ID
        )).thenReturn(0);

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.register(TransactionTestConfiguration.class);
            context.registerBean(
                    "transactionManager",
                    PlatformTransactionManager.class,
                    () -> transactionManager
            );
            context.registerBean(
                    InterviewAnswerMapper.class,
                    () -> interviewAnswerMapper
            );
            context.registerBean(
                    InterviewQuestionMapper.class,
                    () -> interviewQuestionMapper
            );
            context.registerBean(
                    InterviewSessionMapper.class,
                    () -> interviewSessionMapper
            );
            context.registerBean(
                    InterviewWorkflowTransactionService.class
            );
            context.refresh();

            InterviewWorkflowTransactionService transactionalService =
                    context.getBean(
                            InterviewWorkflowTransactionService.class
                    );

            assertThatThrownBy(
                    () -> transactionalService.submitAnswer(
                            answer,
                            mainQuestion()
                    )
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Question状态更新为ANSWERED失败");
        }

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
    }

    private InterviewQuestion stubInsertedFollowUp() {
        InterviewQuestion draft = followUpDraft();
        when(interviewQuestionMapper.getFollowUpByParentQuestionId(
                MAIN_QUESTION_ID
        )).thenReturn(null);
        when(interviewQuestionMapper.insertFollowUp(draft))
                .thenAnswer(invocation -> {
                    draft.setId(FOLLOW_UP_QUESTION_ID);
                    return 1;
                });
        return draft;
    }

    private InterviewAnswer submittedAnswerDraft() {
        return InterviewAnswer.builder()
                .answerContent("HashMap 通过数组和链表存储数据")
                .status(InterviewAnswerStatus.FAILED)
                .requestId("workflow-request-1")
                .errorCode("OLD_ERROR")
                .submittedAt((LocalDateTime) null)
                .build();
    }

    private InterviewQuestion mainQuestion() {
        return InterviewQuestion.builder()
                .id(MAIN_QUESTION_ID)
                .sessionId(SESSION_ID)
                .category(QuestionCategory.JAVA_COLLECTION)
                .knowledgePoint("HashMap")
                .questionType(InterviewQuestionType.MAIN)
                .planOrder(2)
                .displayOrder(3)
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

    private InterviewSession session(
            long currentInterviewQuestionId
    ) {
        return InterviewSession.builder()
                .id(SESSION_ID)
                .currentInterviewQuestionId(
                        currentInterviewQuestionId
                )
                .version(SESSION_VERSION)
                .build();
    }

    private InterviewQuestion followUpDraft() {
        return InterviewQuestion.builder()
                .id(999L)
                .sessionId(999L)
                .questionId(999L)
                .questionContent("请进一步说明扩容机制")
                .referenceAnswerSnapshot("不应保存")
                .scoringPointsSnapshot("[]")
                .questionType(InterviewQuestionType.MAIN)
                .parentQuestionId(999L)
                .followUpTargetPoints("[101]")
                .planOrder(99)
                .displayOrder(99)
                .status(InterviewQuestionStatus.ANSWERED)
                .build();
    }

    private InterviewQuestion pendingFollowUp() {
        return InterviewQuestion.builder()
                .id(FOLLOW_UP_QUESTION_ID)
                .sessionId(SESSION_ID)
                .questionContent("请进一步说明扩容机制")
                .questionType(InterviewQuestionType.FOLLOW_UP)
                .parentQuestionId(MAIN_QUESTION_ID)
                .followUpTargetPoints("[101]")
                .planOrder(null)
                .displayOrder(4)
                .status(InterviewQuestionStatus.PENDING)
                .build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionTestConfiguration {
    }
}
