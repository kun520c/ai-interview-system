package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.config.InterviewPlanConfig;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.model.InterviewMainQuestionDraft;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewSessionServiceTest {

    @Mock private InterviewSessionMapper interviewSessionMapper;
    @Mock private InterviewPlanConfig planConfig;
    @Mock private InterviewQuestionPlanSnapshotService snapshotService;
    @Mock private InterviewSessionTransactionService transactionService;

    private InterviewSessionService service;

    @BeforeEach
    void setUp() {
        service = new InterviewSessionService(
                interviewSessionMapper,
                planConfig,
                snapshotService,
                transactionService
        );
    }

    @Test
    void shouldReturnExistingActiveSessionWithoutPlanning() {
        InterviewSession existing = InterviewSession.builder().id(99L).build();
        when(interviewSessionMapper.getActiveSessionByUserId(1L))
                .thenReturn(existing);

        assertThat(service.createSession(1L, QuestionDifficulty.EASY))
                .isSameAs(existing);
        verifyNoInteractions(planConfig, snapshotService, transactionService);
    }

    @Test
    void shouldBuildDraftsAndDelegateTransaction() {
        var rule = new InterviewPlanConfig.InterviewPlanRule(
                1,
                List.of(QuestionCategory.JAVA_BASIC)
        );
        Question question = question(10L);
        QuestionScoringPoint point = scoringPoint(20L, 10L);
        InterviewSession expected = InterviewSession.builder().id(100L).build();
        when(planConfig.getRule(QuestionDifficulty.EASY)).thenReturn(rule);
        when(snapshotService.prepareSnapshot(QuestionDifficulty.EASY, rule))
                .thenReturn(List.of(
                        new InterviewMainQuestionDraft(
                                question,
                                List.of(point)
                        )
                ));
        when(transactionService.createAndStartSession(any(), any()))
                .thenReturn(expected);

        assertThat(service.createSession(1L, QuestionDifficulty.EASY))
                .isSameAs(expected);

        ArgumentCaptor<InterviewSession> sessionCaptor =
                ArgumentCaptor.forClass(InterviewSession.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InterviewMainQuestionDraft>> draftsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(transactionService).createAndStartSession(
                sessionCaptor.capture(),
                draftsCaptor.capture()
        );
        assertThat(sessionCaptor.getValue().getUserId()).isEqualTo(1L);
        assertThat(sessionCaptor.getValue().getDifficulty())
                .isEqualTo(QuestionDifficulty.EASY);
        assertThat(sessionCaptor.getValue().getStatus())
                .isEqualTo(InterviewSessionStatus.CREATED);
        assertThat(sessionCaptor.getValue().getPlannedQuestionCount()).isEqualTo(1);
        assertThat(sessionCaptor.getValue().getCompletedQuestionCount()).isZero();
        assertThat(sessionCaptor.getValue().getVersion()).isZero();
        assertThat(draftsCaptor.getValue()).singleElement()
                .satisfies(draft -> {
                    assertThat(draft.question()).isSameAs(question);
                    assertThat(draft.scoringPoints()).containsExactly(point);
                });
    }

    @Test
    void shouldRejectNullInputsBeforeCallingDependencies() {
        assertThatThrownBy(() -> service.createSession(null, QuestionDifficulty.EASY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createSession(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(interviewSessionMapper, planConfig,
                snapshotService, transactionService);
    }

    @Test
    void shouldRejectSnapshotResultWithWrongSizeWithoutStartingTransaction() {
        var rule = new InterviewPlanConfig.InterviewPlanRule(
                2,
                List.of(QuestionCategory.JAVA_BASIC)
        );
        when(planConfig.getRule(QuestionDifficulty.EASY)).thenReturn(rule);
        when(snapshotService.prepareSnapshot(QuestionDifficulty.EASY, rule))
                .thenReturn(List.of(
                        new InterviewMainQuestionDraft(
                                question(10L),
                                List.of(scoringPoint(20L, 10L))
                        )
                ));

        assertThatThrownBy(() -> service.createSession(1L, QuestionDifficulty.EASY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("question draft count must equal plannedQuestionCount");
        verify(transactionService, never()).createAndStartSession(any(), any());
    }

    private Question question(long id) {
        return Question.builder()
                .id(id)
                .category(QuestionCategory.JAVA_BASIC)
                .build();
    }

    private QuestionScoringPoint scoringPoint(long id, long questionId) {
        return QuestionScoringPoint.builder()
                .id(id)
                .questionId(questionId)
                .pointType(QuestionPointType.CORE)
                .content("point")
                .weight(100)
                .build();
    }
}
