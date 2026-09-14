package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewQuestionSnapshotFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InterviewQuestionSnapshotFactory factory =
            new InterviewQuestionSnapshotFactory(objectMapper);

    @Test
    void shouldFreezeMainQuestionAndScoringPoints() throws Exception {
        var result = factory.createMainQuestion(
                10L,
                question(20L),
                List.of(point(30L, 20L, QuestionPointType.CORE, 60),
                        point(31L, 20L, QuestionPointType.KEY, 40)),
                2
        );

        assertThat(result.getSessionId()).isEqualTo(10L);
        assertThat(result.getQuestionId()).isEqualTo(20L);
        assertThat(result.getCategory()).isEqualTo(QuestionCategory.JAVA_BASIC);
        assertThat(result.getKnowledgePoint()).isEqualTo("JMM");
        assertThat(result.getQuestionContent()).isEqualTo("question-content");
        assertThat(result.getReferenceAnswerSnapshot()).isEqualTo("reference-answer");
        assertThat(result.getPlanOrder()).isEqualTo(2);
        assertThat(result.getDisplayOrder()).isEqualTo(3);
        assertThat(result.getQuestionType()).isEqualTo(InterviewQuestionType.MAIN);
        assertThat(result.getStatus()).isEqualTo(InterviewQuestionStatus.PENDING);
        assertThat(result.getParentQuestionId()).isNull();
        assertThat(result.getFollowUpTargetPoints()).isNull();
        JsonNode json = objectMapper.readTree(result.getScoringPointsSnapshot());
        assertThat(json.get(0)).hasSize(4);
        assertThat(json.get(0).fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("id", "pointType", "content", "weight");
        assertThat(json.get(0).get("id").asLong()).isEqualTo(30L);
        assertThat(json.get(0).get("pointType").asText()).isEqualTo("CORE");
        assertThat(json.get(0).get("content").asText()).isEqualTo("point-30");
        assertThat(json.get(0).get("weight").asInt()).isEqualTo(60);
    }

    @Test
    void shouldRejectInvalidScoringPoints() {
        assertThatThrownBy(() -> factory.createMainQuestion(
                10L,
                question(20L),
                List.of(point(30L, 20L, QuestionPointType.CORE, 90)),
                1
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sum to 100");

        assertThatThrownBy(() -> factory.createMainQuestion(
                10L,
                question(20L),
                List.of(point(30L, 99L, QuestionPointType.CORE, 100)),
                1
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong");

        assertThatThrownBy(() -> factory.createMainQuestion(
                10L,
                question(20L),
                List.of(),
                1
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must have enabled");
    }

    private Question question(long id) {
        return Question.builder()
                .id(id)
                .category(QuestionCategory.JAVA_BASIC)
                .knowledgePoint("JMM")
                .questionContent("question-content")
                .referenceAnswer("reference-answer")
                .build();
    }

    private QuestionScoringPoint point(
            long id,
            long questionId,
            QuestionPointType type,
            int weight
    ) {
        return QuestionScoringPoint.builder()
                .id(id)
                .questionId(questionId)
                .pointType(type)
                .content("point-" + id)
                .weight(weight)
                .build();
    }
}
