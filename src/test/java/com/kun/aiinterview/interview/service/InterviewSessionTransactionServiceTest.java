package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.model.InterviewMainQuestionDraft;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewSessionTransactionServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private InterviewSessionMapper sessionMapper;
    @Mock private InterviewQuestionMapper questionMapper;
    @Mock private InterviewQuestionSnapshotFactory snapshotFactory;

    private InterviewSessionTransactionService service;

    @BeforeEach
    void setUp() {
        service = new InterviewSessionTransactionService(
                userMapper, sessionMapper, questionMapper, snapshotFactory
        );
    }

    @Test
    void creationMethodShouldOwnTransactionalBoundary() throws Exception {
        Method method = InterviewSessionTransactionService.class.getMethod(
                "createAndStartSession", InterviewSession.class, List.class
        );
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void shouldCreatePersistReadActivateAndStartInOrder() {
        InterviewSession draftSession = sessionDraft(1);
        InterviewMainQuestionDraft draft = questionDraft(10L);
        InterviewQuestion built = mainQuestion(null, 100L, 1,
                InterviewQuestionStatus.PENDING);
        InterviewQuestion persisted = mainQuestion(200L, 100L, 1,
                InterviewQuestionStatus.PENDING);
        InterviewSession started = startedSession(300L, 200L, 1);
        when(userMapper.getUserByIdForUpdate(1L)).thenReturn(new User());
        when(sessionMapper.insertInterviewSession(same(draftSession)))
                .thenAnswer(invocation -> {
                    draftSession.setId(300L);
                    return 1;
                });
        when(snapshotFactory.createMainQuestion(300L, draft.question(),
                draft.scoringPoints(), 1)).thenReturn(built);
        when(questionMapper.batchInsertMainQuestions(anyList())).thenReturn(1);
        when(questionMapper.getMainQuestionByPlanOrder(300L, 1))
                .thenReturn(persisted);
        when(questionMapper.markWaitingAnswer(200L, 300L)).thenReturn(1);
        when(sessionMapper.startSession(300L, 0, 200L)).thenReturn(1);
        when(sessionMapper.getInterviewSessionById(300L)).thenReturn(started);

        assertThat(service.createAndStartSession(draftSession, List.of(draft)))
                .isSameAs(started);

        InOrder order = inOrder(userMapper, sessionMapper, questionMapper,
                snapshotFactory);
        order.verify(userMapper).getUserByIdForUpdate(1L);
        order.verify(sessionMapper).getActiveSessionByUserId(1L);
        order.verify(sessionMapper).insertInterviewSession(draftSession);
        order.verify(snapshotFactory).createMainQuestion(300L,
                draft.question(), draft.scoringPoints(), 1);
        order.verify(questionMapper).batchInsertMainQuestions(anyList());
        order.verify(questionMapper).getMainQuestionByPlanOrder(300L, 1);
        order.verify(questionMapper).markWaitingAnswer(200L, 300L);
        order.verify(sessionMapper).startSession(300L, 0, 200L);
        order.verify(sessionMapper).getInterviewSessionById(300L);
    }

    @Test
    void shouldRejectMissingUser() {
        InterviewSession session = sessionDraft(1);
        when(userMapper.getUserByIdForUpdate(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.createAndStartSession(
                session, List.of(questionDraft(10L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user does not exist");
        verifyNoInteractions(sessionMapper, questionMapper, snapshotFactory);
    }

    @Test
    void shouldReturnActiveSessionFoundAfterLockWithoutInsert() {
        InterviewSession session = sessionDraft(1);
        InterviewSession existing = startedSession(9L, 8L, 1);
        when(userMapper.getUserByIdForUpdate(1L)).thenReturn(new User());
        when(sessionMapper.getActiveSessionByUserId(1L)).thenReturn(existing);

        assertThat(service.createAndStartSession(
                session, List.of(questionDraft(10L))))
                .isSameAs(existing);
        verify(sessionMapper, never()).insertInterviewSession(any());
        verifyNoInteractions(questionMapper, snapshotFactory);
    }

    @Test
    void shouldRejectSessionInsertAffectedRows() {
        stubLockedWithoutActive();
        when(sessionMapper.insertInterviewSession(any())).thenReturn(0);

        assertFails(sessionDraft(1), "failed to insert interview session");
        verifyNoInteractions(questionMapper, snapshotFactory);
    }

    @Test
    void shouldRejectMissingGeneratedSessionId() {
        stubLockedWithoutActive();
        when(sessionMapper.insertInterviewSession(any())).thenReturn(1);

        assertFails(sessionDraft(1), "failed to insert interview session");
    }

    @Test
    void shouldRejectIncompleteMainBatch() {
        InterviewSession session = sessionDraft(1);
        InterviewMainQuestionDraft draft = questionDraft(10L);
        stubInserted(session, 300L);
        when(snapshotFactory.createMainQuestion(any(), any(), any(), eq(1)))
                .thenReturn(mainQuestion(null, 100L, 1,
                        InterviewQuestionStatus.PENDING));
        when(questionMapper.batchInsertMainQuestions(anyList())).thenReturn(0);

        assertThatThrownBy(() -> service.createAndStartSession(
                session, List.of(draft)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("complete interview question plan");
        verify(questionMapper, never()).getMainQuestionByPlanOrder(any(), any());
    }

    @Test
    void shouldRejectMissingFirstMainQuestion() {
        var context = stubThroughBatch();
        when(questionMapper.getMainQuestionByPlanOrder(300L, 1)).thenReturn(null);

        assertThatThrownBy(() -> service.createAndStartSession(
                context.session(), List.of(context.draft())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid first MAIN");
    }

    @Test
    void shouldRejectFirstQuestionActivationFailure() {
        var context = stubThroughFirstQuestion();
        when(questionMapper.markWaitingAnswer(200L, 300L)).thenReturn(0);

        assertThatThrownBy(() -> service.createAndStartSession(
                context.session(), List.of(context.draft())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("activate first");
        verify(sessionMapper, never()).startSession(any(), any(), any());
    }

    @Test
    void shouldRejectSessionStartCasFailure() {
        var context = stubThroughFirstQuestion();
        when(questionMapper.markWaitingAnswer(200L, 300L)).thenReturn(1);
        when(sessionMapper.startSession(300L, 0, 200L)).thenReturn(0);

        assertThatThrownBy(() -> service.createAndStartSession(
                context.session(), List.of(context.draft())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("start interview session");
    }

    @Test
    void shouldRejectDraftCountDifferentFromPlanBeforeLock() {
        assertThatThrownBy(() -> service.createAndStartSession(
                sessionDraft(2), List.of(questionDraft(10L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("draft count");
        verifyNoInteractions(userMapper, sessionMapper, questionMapper,
                snapshotFactory);
    }

    private void assertFails(InterviewSession session, String message) {
        assertThatThrownBy(() -> service.createAndStartSession(
                session, List.of(questionDraft(10L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message);
    }

    private void stubLockedWithoutActive() {
        when(userMapper.getUserByIdForUpdate(1L)).thenReturn(new User());
        when(sessionMapper.getActiveSessionByUserId(1L)).thenReturn(null);
    }

    private void stubInserted(InterviewSession session, long id) {
        stubLockedWithoutActive();
        when(sessionMapper.insertInterviewSession(same(session)))
                .thenAnswer(invocation -> {
                    session.setId(id);
                    return 1;
                });
    }

    private TestContext stubThroughBatch() {
        InterviewSession session = sessionDraft(1);
        InterviewMainQuestionDraft draft = questionDraft(10L);
        stubInserted(session, 300L);
        when(snapshotFactory.createMainQuestion(300L, draft.question(),
                draft.scoringPoints(), 1))
                .thenReturn(mainQuestion(null, 100L, 1,
                        InterviewQuestionStatus.PENDING));
        when(questionMapper.batchInsertMainQuestions(anyList())).thenReturn(1);
        return new TestContext(session, draft);
    }

    private TestContext stubThroughFirstQuestion() {
        TestContext context = stubThroughBatch();
        when(questionMapper.getMainQuestionByPlanOrder(300L, 1))
                .thenReturn(mainQuestion(200L, 100L, 1,
                        InterviewQuestionStatus.PENDING));
        return context;
    }

    private InterviewSession sessionDraft(int count) {
        return InterviewSession.builder()
                .userId(1L)
                .difficulty(QuestionDifficulty.EASY)
                .status(InterviewSessionStatus.CREATED)
                .plannedQuestionCount(count)
                .completedQuestionCount(0)
                .reportStatus(InterviewReportStatus.NOT_STARTED)
                .version(0)
                .build();
    }

    private InterviewSession startedSession(long id, long questionId, int count) {
        return InterviewSession.builder()
                .id(id)
                .userId(1L)
                .difficulty(QuestionDifficulty.EASY)
                .status(InterviewSessionStatus.IN_PROGRESS)
                .currentInterviewQuestionId(questionId)
                .plannedQuestionCount(count)
                .completedQuestionCount(0)
                .reportStatus(InterviewReportStatus.NOT_STARTED)
                .version(1)
                .startedAt(LocalDateTime.now())
                .build();
    }

    private InterviewMainQuestionDraft questionDraft(long questionId) {
        Question question = Question.builder()
                .id(questionId)
                .category(QuestionCategory.JAVA_BASIC)
                .build();
        QuestionScoringPoint point = QuestionScoringPoint.builder()
                .id(questionId + 1)
                .questionId(questionId)
                .weight(100)
                .build();
        return new InterviewMainQuestionDraft(question, List.of(point));
    }

    private InterviewQuestion mainQuestion(
            Long id,
            long questionId,
            int planOrder,
            InterviewQuestionStatus status
    ) {
        return InterviewQuestion.builder()
                .id(id)
                .sessionId(300L)
                .questionId(questionId)
                .questionType(InterviewQuestionType.MAIN)
                .planOrder(planOrder)
                .status(status)
                .build();
    }

    private record TestContext(
            InterviewSession session,
            InterviewMainQuestionDraft draft
    ) {
    }
}
