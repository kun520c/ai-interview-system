package com.kun.aiinterview.interview.evaluation;

import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationRagQueryBuilderTest {

    private final EvaluationRagQueryBuilder queryBuilder =
            new EvaluationRagQueryBuilder();

    @Test
    void givenMainAnswerContext_whenBuild_thenUsesMainQuestionAndAllScoringPointsOnly() {
        EvaluationContext context = mainContext();

        String query = queryBuilder.build(context);

        assertThat(query)
                .contains("HashMap为什么线程不安全？")
                .contains("并发put可能覆盖数据")
                .contains("并发扩容可能产生结构异常")
                .doesNotContain("用户主回答")
                .doesNotContain("参考答案")
                .doesNotContain("追问：");
    }

    @Test
    void givenFollowUpContext_whenBuild_thenUsesMainQuestionFollowUpQuestionAndTargetPointsOnly() {
        EvaluationContext context = followUpContext();

        String query = queryBuilder.build(context);

        assertThat(query)
                .contains("HashMap为什么线程不安全？")
                .contains("并发扩容具体有什么风险？")
                .contains("并发扩容可能产生结构异常")
                .doesNotContain("并发put可能覆盖数据")
                .doesNotContain("用户主回答")
                .doesNotContain("用户追问回答")
                .doesNotContain("参考答案");
    }

    @Test
    void givenNullContext_whenBuild_thenRejects() {
        assertThatThrownBy(() -> queryBuilder.build(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EvaluationContext");
    }

    @Test
    void givenFollowUpTargetMissingFromMainScoringPoints_whenBuild_thenRejects() {
        EvaluationContext context = new EvaluationContext(
                202L,
                302L,
                201L,
                301L,
                EvaluationMode.FOLLOW_UP_ANSWER,
                QuestionCategory.JAVA_COLLECTION,
                "HashMap",
                "HashMap为什么线程不安全？",
                "参考答案",
                scoringPoints(),
                "用户主回答",
                "并发扩容具体有什么风险？",
                "用户追问回答",
                List.of(999L)
        );

        assertThatThrownBy(() -> queryBuilder.build(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("评分点");
    }

    private EvaluationContext mainContext() {
        return new EvaluationContext(
                201L,
                301L,
                201L,
                301L,
                EvaluationMode.MAIN_ANSWER,
                QuestionCategory.JAVA_COLLECTION,
                "HashMap",
                "HashMap为什么线程不安全？",
                "参考答案",
                scoringPoints(),
                "用户主回答",
                null,
                null,
                List.of()
        );
    }

    private EvaluationContext followUpContext() {
        return new EvaluationContext(
                202L,
                302L,
                201L,
                301L,
                EvaluationMode.FOLLOW_UP_ANSWER,
                QuestionCategory.JAVA_COLLECTION,
                "HashMap",
                "HashMap为什么线程不安全？",
                "参考答案",
                scoringPoints(),
                "用户主回答",
                "并发扩容具体有什么风险？",
                "用户追问回答",
                List.of(102L)
        );
    }

    private List<ScoringPointSnapshot> scoringPoints() {
        return List.of(
                new ScoringPointSnapshot(
                        101L,
                        QuestionPointType.CORE,
                        "并发put可能覆盖数据",
                        50
                ),
                new ScoringPointSnapshot(
                        102L,
                        QuestionPointType.INTERNAL,
                        "并发扩容可能产生结构异常",
                        50
                )
        );
    }
}
