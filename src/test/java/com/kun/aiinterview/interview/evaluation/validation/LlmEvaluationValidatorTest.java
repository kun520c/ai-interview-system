package com.kun.aiinterview.interview.evaluation.validation;

import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmEvaluationValidatorTest {

    private static final List<String> VALID_STRENGTHS =
            List.of("准确说明了并发写入风险");
    private static final List<String> VALID_MISSING_POINTS =
            List.of("未说明扩容过程");

    private final LlmEvaluationValidator validator =
            new LlmEvaluationValidator();

    @Test
    void givenValidMainSuggestionWithoutFollowUp_whenValidate_thenReturnsTrustedResult() {
        LlmEvaluationSuggestion suggestion = validSuggestion(false, null);

        ValidatedEvaluationSuggestion result = validator.validate(
                mainContext(),
                suggestion
        );

        assertThat(result.correctnessScore()).isEqualTo(18);
        assertThat(result.completenessScore()).isEqualTo(16);
        assertThat(result.depthScore()).isEqualTo(14);
        assertThat(result.clarityScore()).isEqualTo(12);
        assertThat(result.practiceScore()).isEqualTo(10);
        assertThat(result.strengths()).containsExactlyElementsOf(VALID_STRENGTHS);
        assertThat(result.missingPoints())
                .containsExactlyElementsOf(VALID_MISSING_POINTS);
        assertThat(result.scoringPointResults())
                .containsExactlyElementsOf(suggestion.scoringPointResults());
        assertThat(result.followUpRecommended()).isFalse();
        assertThat(result.suggestedFollowUp()).isNull();
    }

    @Test
    void givenValidMainSuggestionWithFollowUp_whenValidate_thenReturnsNormalizedFollowUp() {
        LlmEvaluationSuggestion suggestion = validSuggestion(
                true,
                "  请具体说明扩容风险？  "
        );

        ValidatedEvaluationSuggestion result = validator.validate(
                mainContext(),
                suggestion
        );

        assertThat(result.followUpRecommended()).isTrue();
        assertThat(result.suggestedFollowUp()).isEqualTo("请具体说明扩容风险？");
    }

    @Test
    void givenValidFollowUpSuggestionWithoutAnotherFollowUp_whenValidate_thenSucceeds() {
        ValidatedEvaluationSuggestion result = validator.validate(
                followUpContext(),
                validSuggestion(false, null)
        );

        assertThat(result.followUpRecommended()).isFalse();
        assertThat(result.suggestedFollowUp()).isNull();
    }

    @Test
    void givenNullContext_whenValidate_thenRejects() {
        assertThatThrownBy(() -> validator.validate(
                null,
                validSuggestion(false, null)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EvaluationContext");
    }

    @Test
    void givenNullSuggestion_whenValidate_thenRejects() {
        assertThatThrownBy(() -> validator.validate(mainContext(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("评价结果");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidScoreSuggestions")
    void givenInvalidDimensionScore_whenValidate_thenRejects(
            String scenario,
            LlmEvaluationSuggestion suggestion,
            Class<? extends RuntimeException> expectedException
    ) {
        assertThatThrownBy(() -> validator.validate(mainContext(), suggestion))
                .isInstanceOf(expectedException);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTextLists")
    void givenInvalidStrengthsOrMissingPoints_whenValidate_thenRejects(
            String scenario,
            List<String> strengths,
            List<String> missingPoints
    ) {
        LlmEvaluationSuggestion suggestion = suggestion(
                18,
                16,
                14,
                12,
                10,
                strengths,
                missingPoints,
                validScoringPointResults(),
                false,
                null
        );

        assertThatThrownBy(() -> validator.validate(mainContext(), suggestion))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenNullScoringPointResults_whenValidate_thenRejects() {
        assertInvalidScoringPointResults(null);
    }

    @Test
    void givenScoringPointResultCountMismatch_whenValidate_thenRejects() {
        assertInvalidScoringPointResults(List.of(
                scoringPointResult(101L, true, "回答证据")
        ));
    }

    @Test
    void givenNullScoringPointResultItem_whenValidate_thenRejects() {
        assertInvalidScoringPointResults(Arrays.asList(
                null,
                scoringPointResult(102L, false, null)
        ));
    }

    @Test
    void givenNullScoringPointId_whenValidate_thenRejects() {
        assertInvalidScoringPointResults(List.of(
                scoringPointResult(null, true, "回答证据"),
                scoringPointResult(102L, false, null)
        ));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void givenNonPositiveScoringPointId_whenValidate_thenRejects(
            long scoringPointId
    ) {
        assertInvalidScoringPointResults(List.of(
                scoringPointResult(scoringPointId, true, "回答证据"),
                scoringPointResult(102L, false, null)
        ));
    }

    @Test
    void givenUnknownScoringPointId_whenValidate_thenRejects() {
        assertInvalidScoringPointResults(List.of(
                scoringPointResult(999L, true, "回答证据"),
                scoringPointResult(102L, false, null)
        ));
    }

    @Test
    void givenDuplicateScoringPointId_whenValidate_thenRejects() {
        assertInvalidScoringPointResults(List.of(
                scoringPointResult(101L, true, "回答证据"),
                scoringPointResult(101L, false, null)
        ));
    }

    @Test
    void givenNullCoveredFlag_whenValidate_thenRejects() {
        assertInvalidScoringPointResults(List.of(
                scoringPointResult(101L, null, "回答证据"),
                scoringPointResult(102L, false, null)
        ));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void givenCoveredPointWithoutEvidence_whenValidate_thenRejects(
            String evidence
    ) {
        assertInvalidScoringPointResults(List.of(
                scoringPointResult(101L, true, evidence),
                scoringPointResult(102L, false, null)
        ));
    }

    @Test
    void givenNullFollowUpRecommended_whenValidate_thenRejects() {
        LlmEvaluationSuggestion suggestion = suggestion(
                18,
                16,
                14,
                12,
                10,
                VALID_STRENGTHS,
                VALID_MISSING_POINTS,
                validScoringPointResults(),
                null,
                null
        );

        assertThatThrownBy(() -> validator.validate(mainContext(), suggestion))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("followUpRecommended");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void givenMainFollowUpRecommendedWithoutQuestion_whenValidate_thenRejects(
            String suggestedFollowUp
    ) {
        assertThatThrownBy(() -> validator.validate(
                mainContext(),
                validSuggestion(true, suggestedFollowUp)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("候选追问");
    }

    @Test
    void givenMainFollowUpNotRecommendedButQuestionPresent_whenValidate_thenRejects() {
        assertThatThrownBy(() -> validator.validate(
                mainContext(),
                validSuggestion(false, "请继续说明扩容风险？")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("候选追问");
    }

    @Test
    void givenFollowUpAnswerRecommendsAnotherFollowUp_whenValidate_thenRejects() {
        assertThatThrownBy(() -> validator.validate(
                followUpContext(),
                validSuggestion(true, "请再补充一个细节？")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能再次建议追问");
    }

    @Test
    void givenFollowUpAnswerReturnsQuestionWithoutRecommendation_whenValidate_thenRejects() {
        assertThatThrownBy(() -> validator.validate(
                followUpContext(),
                validSuggestion(false, "请再补充一个细节？")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能返回新的候选追问");
    }

    private void assertInvalidScoringPointResults(
            List<LlmEvaluationSuggestion.ScoringPointResult> results
    ) {
        LlmEvaluationSuggestion suggestion = suggestion(
                18,
                16,
                14,
                12,
                10,
                VALID_STRENGTHS,
                VALID_MISSING_POINTS,
                results,
                false,
                null
        );

        assertThatThrownBy(() -> validator.validate(mainContext(), suggestion))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Stream<Arguments> invalidScoreSuggestions() {
        String[] fieldNames = {
                "correctnessScore",
                "completenessScore",
                "depthScore",
                "clarityScore",
                "practiceScore"
        };

        return IntStream.range(0, fieldNames.length)
                .boxed()
                .flatMap(index -> Stream.of(
                        invalidScoreArgument(
                                fieldNames[index],
                                index,
                                null,
                                IllegalStateException.class
                        ),
                        invalidScoreArgument(
                                fieldNames[index],
                                index,
                                -1,
                                IllegalArgumentException.class
                        ),
                        invalidScoreArgument(
                                fieldNames[index],
                                index,
                                21,
                                IllegalArgumentException.class
                        )
                ));
    }

    private static Arguments invalidScoreArgument(
            String fieldName,
            int index,
            Integer invalidScore,
            Class<? extends RuntimeException> expectedException
    ) {
        Integer[] scores = {18, 16, 14, 12, 10};
        scores[index] = invalidScore;

        return Arguments.of(
                fieldName + "=" + invalidScore,
                suggestion(
                        scores[0],
                        scores[1],
                        scores[2],
                        scores[3],
                        scores[4],
                        VALID_STRENGTHS,
                        VALID_MISSING_POINTS,
                        validScoringPointResults(),
                        false,
                        null
                ),
                expectedException
        );
    }

    private static Stream<Arguments> invalidTextLists() {
        return Stream.of(
                Arguments.of(
                        "strengths为null",
                        (List<String>) null,
                        VALID_MISSING_POINTS
                ),
                Arguments.of(
                        "strengths包含null",
                        Arrays.asList("有效优点", null),
                        VALID_MISSING_POINTS
                ),
                Arguments.of(
                        "strengths包含blank",
                        List.of(" "),
                        VALID_MISSING_POINTS
                ),
                Arguments.of(
                        "missingPoints为null",
                        VALID_STRENGTHS,
                        null
                ),
                Arguments.of(
                        "missingPoints包含null",
                        VALID_STRENGTHS,
                        Arrays.asList("有效遗漏点", null)
                ),
                Arguments.of(
                        "missingPoints包含blank",
                        VALID_STRENGTHS,
                        List.of(" ")
                )
        );
    }

    private static LlmEvaluationSuggestion validSuggestion(
            Boolean followUpRecommended,
            String suggestedFollowUp
    ) {
        return suggestion(
                18,
                16,
                14,
                12,
                10,
                VALID_STRENGTHS,
                VALID_MISSING_POINTS,
                validScoringPointResults(),
                followUpRecommended,
                suggestedFollowUp
        );
    }

    private static LlmEvaluationSuggestion suggestion(
            Integer correctnessScore,
            Integer completenessScore,
            Integer depthScore,
            Integer clarityScore,
            Integer practiceScore,
            List<String> strengths,
            List<String> missingPoints,
            List<LlmEvaluationSuggestion.ScoringPointResult> scoringPointResults,
            Boolean followUpRecommended,
            String suggestedFollowUp
    ) {
        return new LlmEvaluationSuggestion(
                correctnessScore,
                completenessScore,
                depthScore,
                clarityScore,
                practiceScore,
                strengths,
                missingPoints,
                "补充HashMap并发修改可能丢失更新。",
                scoringPointResults,
                followUpRecommended,
                suggestedFollowUp
        );
    }

    private static List<LlmEvaluationSuggestion.ScoringPointResult>
            validScoringPointResults() {
        return List.of(
                scoringPointResult(101L, true, "回答提到并发写入可能覆盖数据"),
                scoringPointResult(102L, false, null)
        );
    }

    private static LlmEvaluationSuggestion.ScoringPointResult scoringPointResult(
            Long scoringPointId,
            Boolean covered,
            String evidence
    ) {
        return new LlmEvaluationSuggestion.ScoringPointResult(
                scoringPointId,
                covered,
                evidence
        );
    }

    private static EvaluationContext mainContext() {
        return context(EvaluationMode.MAIN_ANSWER);
    }

    private static EvaluationContext followUpContext() {
        return context(EvaluationMode.FOLLOW_UP_ANSWER);
    }

    private static EvaluationContext context(EvaluationMode mode) {
        boolean followUp = mode == EvaluationMode.FOLLOW_UP_ANSWER;

        return new EvaluationContext(
                followUp ? 202L : 201L,
                followUp ? 302L : 301L,
                201L,
                301L,
                mode,
                QuestionCategory.JAVA_COLLECTION,
                "HashMap",
                "HashMap为什么线程不安全？",
                "历史参考答案",
                List.of(
                        new ScoringPointSnapshot(
                                101L,
                                QuestionPointType.CORE,
                                "并发写入可能覆盖数据",
                                60
                        ),
                        new ScoringPointSnapshot(
                                102L,
                                QuestionPointType.KEY,
                                "并发扩容可能产生结构异常",
                                40
                        )
                ),
                "用户主回答",
                followUp ? "并发扩容具体有什么风险？" : null,
                followUp ? "用户追问回答" : null,
                followUp ? List.of(102L) : List.of()
        );
    }
}
