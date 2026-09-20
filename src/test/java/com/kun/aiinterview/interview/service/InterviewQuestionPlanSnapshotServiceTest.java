package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.config.InterviewPlanConfig;
import com.kun.aiinterview.interview.model.InterviewMainQuestionDraft;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionPointType;
import com.kun.aiinterview.question.mapper.QuestionMapper;
import com.kun.aiinterview.question.mapper.QuestionScoringPointMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewQuestionPlanSnapshotServiceTest {

    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionScoringPointMapper scoringPointMapper;
    @Mock private QuestionPlanSelector selector;

    private InterviewQuestionPlanSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new InterviewQuestionPlanSnapshotService(
                questionMapper,
                scoringPointMapper,
                selector
        );
    }

    @Test
    void snapshotMethodShouldOwnShortRepeatableReadTransaction()
            throws Exception {
        Method method = InterviewQuestionPlanSnapshotService.class.getMethod(
                "prepareSnapshot",
                QuestionDifficulty.class,
                InterviewPlanConfig.InterviewPlanRule.class
        );
        Transactional transactional = method.getAnnotation(
                Transactional.class
        );

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.isolation())
                .isEqualTo(Isolation.REPEATABLE_READ);
    }

    @Test
    void shouldSelectQuestionsAndBuildImmutableDrafts() {
        var rule = rule(1);
        Question question = question(10L);
        QuestionScoringPoint point = scoringPoint(20L, 10L);
        when(questionMapper.selectEnabledQuestionsForInterview(
                QuestionDifficulty.EASY
        )).thenReturn(List.of(question));
        when(selector.select(any(), eq(rule)))
                .thenReturn(List.of(question));
        when(scoringPointMapper.selectEnabledByQuestionId(10L))
                .thenReturn(List.of(point));

        List<InterviewMainQuestionDraft> drafts =
                service.prepareSnapshot(QuestionDifficulty.EASY, rule);

        assertThat(drafts).singleElement().satisfies(draft -> {
            assertThat(draft.question()).isSameAs(question);
            assertThat(draft.scoringPoints()).containsExactly(point);
        });
        assertThatThrownBy(() -> drafts.add(drafts.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectNullInputsBeforeMapperAccess() {
        assertThatThrownBy(() -> service.prepareSnapshot(null, rule(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.prepareSnapshot(
                QuestionDifficulty.EASY,
                null
        )).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(questionMapper, scoringPointMapper, selector);
    }

    @Test
    void shouldRejectWrongSelectionCountBeforeScoringPointReads() {
        var rule = rule(2);
        Question question = question(10L);
        when(questionMapper.selectEnabledQuestionsForInterview(
                QuestionDifficulty.EASY
        )).thenReturn(List.of(question));
        when(selector.select(any(), eq(rule)))
                .thenReturn(List.of(question));

        assertThatThrownBy(() -> service.prepareSnapshot(
                QuestionDifficulty.EASY,
                rule
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("selected question count");
        verifyNoInteractions(scoringPointMapper);
    }

    @Test
    void shouldRejectMissingScoringPoints() {
        var rule = rule(1);
        Question question = question(10L);
        when(questionMapper.selectEnabledQuestionsForInterview(
                QuestionDifficulty.EASY
        )).thenReturn(List.of(question));
        when(selector.select(any(), eq(rule)))
                .thenReturn(List.of(question));
        when(scoringPointMapper.selectEnabledByQuestionId(10L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.prepareSnapshot(
                QuestionDifficulty.EASY,
                rule
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no enabled scoring points");
    }

    @Test
    void shouldRejectSelectedQuestionWithoutId() {
        var rule = rule(1);
        Question question = question(null);
        when(questionMapper.selectEnabledQuestionsForInterview(
                QuestionDifficulty.EASY
        )).thenReturn(List.of(question));
        when(selector.select(any(), eq(rule)))
                .thenReturn(List.of(question));

        assertThatThrownBy(() -> service.prepareSnapshot(
                QuestionDifficulty.EASY,
                rule
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("question.id");
        verify(scoringPointMapper, never())
                .selectEnabledByQuestionId(any());
    }

    private InterviewPlanConfig.InterviewPlanRule rule(int count) {
        return new InterviewPlanConfig.InterviewPlanRule(
                count,
                List.of(QuestionCategory.JAVA_BASIC)
        );
    }

    private Question question(Long id) {
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
