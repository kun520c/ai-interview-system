package com.kun.aiinterview.user.service;

import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationStandard;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.user.mapper.UserWeaknessMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class UserWeaknessServiceTest {

    private static final long USER_ID = 101L;
    private static final String HASH_MAP = "HashMap";

    @Mock
    private UserWeaknessMapper userWeaknessMapper;

    private UserWeaknessService service;

    @BeforeEach
    void setUp() {
        service = new UserWeaknessService(
                userWeaknessMapper,
                new EvaluationStandard()
        );
    }

    @Test
    void shouldRejectNullUserId() {
        assertThatThrownBy(
                () -> service.applySessionEvaluations(
                        null,
                        List.of(evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                HASH_MAP,
                                60
                        ))
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId不能为空");

        verifyNoInteractions(userWeaknessMapper);
    }

    @Test
    void shouldRejectNullEvaluations() {
        assertThatThrownBy(
                () -> service.applySessionEvaluations(USER_ID, null)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("evaluations不能为空");

        verifyNoInteractions(userWeaknessMapper);
    }

    @Test
    void shouldRejectEmptyEvaluations() {
        assertThatThrownBy(
                () -> service.applySessionEvaluations(
                        USER_ID,
                        List.of()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("evaluations不能为空");

        verifyNoInteractions(userWeaknessMapper);
    }

    @Test
    void shouldRejectNullEvaluation() {
        List<AnswerEvaluation> evaluations = new ArrayList<>();
        evaluations.add(null);

        assertThatThrownBy(
                () -> service.applySessionEvaluations(
                        USER_ID,
                        evaluations
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Evaluation不能为空");

        verifyNoInteractions(userWeaknessMapper);
    }

    @Test
    void shouldRejectNullCategory() {
        assertThatThrownBy(
                () -> service.applySessionEvaluations(
                        USER_ID,
                        List.of(evaluation(null, HASH_MAP, 60))
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Evaluation缺少category");

        verifyNoInteractions(userWeaknessMapper);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void shouldRejectBlankKnowledgePoint(String knowledgePoint) {
        assertThatThrownBy(
                () -> service.applySessionEvaluations(
                        USER_ID,
                        List.of(evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                knowledgePoint,
                                60
                        ))
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Evaluation缺少knowledgePoint");

        verifyNoInteractions(userWeaknessMapper);
    }

    @Test
    void shouldRejectNullTotalScore() {
        assertThatThrownBy(
                () -> service.applySessionEvaluations(
                        USER_ID,
                        List.of(evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                HASH_MAP,
                                null
                        ))
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Evaluation缺少totalScore");

        verifyNoInteractions(userWeaknessMapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void shouldRejectTotalScoreOutsideSupportedRange(int totalScore) {
        assertThatThrownBy(
                () -> service.applySessionEvaluations(
                        USER_ID,
                        List.of(evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                HASH_MAP,
                                totalScore
                        ))
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Evaluation的totalScore必须在0到100之间");

        verifyNoInteractions(userWeaknessMapper);
    }

    @Test
    void shouldUpsertWhenSingleScoreIsBelowGood() {
        service.applySessionEvaluations(
                USER_ID,
                List.of(evaluation(
                        QuestionCategory.JAVA_COLLECTION,
                        HASH_MAP,
                        60
                ))
        );

        ArgumentCaptor<BigDecimal> scoreCaptor =
                ArgumentCaptor.forClass(BigDecimal.class);
        verify(userWeaknessMapper).upsertDiscoveredWeakness(
                eq(USER_ID),
                eq(QuestionCategory.JAVA_COLLECTION),
                eq(HASH_MAP),
                scoreCaptor.capture()
        );
        assertThat(scoreCaptor.getValue())
                .isEqualByComparingTo("40.00");
        verify(userWeaknessMapper, never())
                .resolveActiveWeakness(any(), any(), any(), any());
    }

    @Test
    void shouldResolveWhenSingleScoreReachesGood() {
        service.applySessionEvaluations(
                USER_ID,
                List.of(evaluation(
                        QuestionCategory.JAVA_COLLECTION,
                        HASH_MAP,
                        80
                ))
        );

        ArgumentCaptor<BigDecimal> scoreCaptor =
                ArgumentCaptor.forClass(BigDecimal.class);
        verify(userWeaknessMapper).resolveActiveWeakness(
                eq(USER_ID),
                eq(QuestionCategory.JAVA_COLLECTION),
                eq(HASH_MAP),
                scoreCaptor.capture()
        );
        assertThat(scoreCaptor.getValue())
                .isEqualByComparingTo("20.00");
        verify(userWeaknessMapper, never())
                .upsertDiscoveredWeakness(any(), any(), any(), any());
    }

    @Test
    void shouldAggregateSameKnowledgePointOnce() {
        service.applySessionEvaluations(
                USER_ID,
                List.of(
                        evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                HASH_MAP,
                                60
                        ),
                        evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                HASH_MAP,
                                70
                        )
                )
        );

        ArgumentCaptor<BigDecimal> scoreCaptor =
                ArgumentCaptor.forClass(BigDecimal.class);
        verify(userWeaknessMapper, times(1))
                .upsertDiscoveredWeakness(
                        eq(USER_ID),
                        eq(QuestionCategory.JAVA_COLLECTION),
                        eq(HASH_MAP),
                        scoreCaptor.capture()
                );
        assertThat(scoreCaptor.getValue())
                .isEqualByComparingTo("35.00");
        verify(userWeaknessMapper, never())
                .resolveActiveWeakness(any(), any(), any(), any());
        verifyNoMoreInteractions(userWeaknessMapper);
    }

    @Test
    void shouldTreatSameKnowledgePointInDifferentCategoriesAsSeparateKeys() {
        service.applySessionEvaluations(
                USER_ID,
                List.of(
                        evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                HASH_MAP,
                                60
                        ),
                        evaluation(
                                QuestionCategory.JAVA_CONCURRENCY,
                                HASH_MAP,
                                50
                        )
                )
        );

        ArgumentCaptor<BigDecimal> collectionScoreCaptor =
                ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> concurrencyScoreCaptor =
                ArgumentCaptor.forClass(BigDecimal.class);
        verify(userWeaknessMapper).upsertDiscoveredWeakness(
                eq(USER_ID),
                eq(QuestionCategory.JAVA_COLLECTION),
                eq(HASH_MAP),
                collectionScoreCaptor.capture()
        );
        verify(userWeaknessMapper).upsertDiscoveredWeakness(
                eq(USER_ID),
                eq(QuestionCategory.JAVA_CONCURRENCY),
                eq(HASH_MAP),
                concurrencyScoreCaptor.capture()
        );
        assertThat(collectionScoreCaptor.getValue())
                .isEqualByComparingTo("40.00");
        assertThat(concurrencyScoreCaptor.getValue())
                .isEqualByComparingTo("50.00");
        verify(userWeaknessMapper, times(2))
                .upsertDiscoveredWeakness(any(), any(), any(), any());
        verify(userWeaknessMapper, never())
                .resolveActiveWeakness(any(), any(), any(), any());
    }

    @Test
    void shouldRoundAverageHalfUpAndKeepWeaknessBelowGood() {
        service.applySessionEvaluations(
                USER_ID,
                List.of(
                        evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                HASH_MAP,
                                79
                        ),
                        evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                HASH_MAP,
                                80
                        ),
                        evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                HASH_MAP,
                                80
                        )
                )
        );

        ArgumentCaptor<BigDecimal> scoreCaptor =
                ArgumentCaptor.forClass(BigDecimal.class);
        verify(userWeaknessMapper, times(1))
                .upsertDiscoveredWeakness(
                        eq(USER_ID),
                        eq(QuestionCategory.JAVA_COLLECTION),
                        eq(HASH_MAP),
                        scoreCaptor.capture()
                );
        assertThat(scoreCaptor.getValue())
                .isEqualByComparingTo("20.33");
        verify(userWeaknessMapper, never())
                .resolveActiveWeakness(any(), any(), any(), any());
    }

    @Test
    void shouldResolveWhenRoundedAverageReachesGood() {
        service.applySessionEvaluations(
                USER_ID,
                List.of(
                        evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                HASH_MAP,
                                79
                        ),
                        evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                HASH_MAP,
                                81
                        )
                )
        );

        ArgumentCaptor<BigDecimal> scoreCaptor =
                ArgumentCaptor.forClass(BigDecimal.class);
        verify(userWeaknessMapper, times(1))
                .resolveActiveWeakness(
                        eq(USER_ID),
                        eq(QuestionCategory.JAVA_COLLECTION),
                        eq(HASH_MAP),
                        scoreCaptor.capture()
                );
        assertThat(scoreCaptor.getValue())
                .isEqualByComparingTo("20.00");
        verify(userWeaknessMapper, never())
                .upsertDiscoveredWeakness(any(), any(), any(), any());
    }

    private AnswerEvaluation evaluation(
            QuestionCategory category,
            String knowledgePoint,
            Integer totalScore
    ) {
        return AnswerEvaluation.builder()
                .category(category)
                .knowledgePoint(knowledgePoint)
                .totalScore(totalScore)
                .build();
    }
}
