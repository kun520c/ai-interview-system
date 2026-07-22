package com.kun.aiinterview.question.dto;

import com.kun.aiinterview.question.enums.QuestionPointType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateQuestionRequestValidationTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void shouldRejectEmptyScoringPointList() {
        CreateQuestionRequest request = requestWith(List.of());

        Set<ConstraintViolation<CreateQuestionRequest>> violations =
                validator.validate(request);

        assertTrue(hasMessage(
                violations,
                "题目至少需要一个评分点"
        ));
    }

    @Test
    void shouldRejectNullScoringPointElement() {
        CreateQuestionRequest request = requestWith(
                java.util.Collections.singletonList(null)
        );

        Set<ConstraintViolation<CreateQuestionRequest>> violations =
                validator.validate(request);

        assertTrue(hasMessage(violations, "评分点不能为空"));
    }

    @Test
    void shouldCascadeValidationToScoringPoint() {
        ScoringPointRequest scoringPoint = new ScoringPointRequest(
                null,
                " ",
                null
        );
        CreateQuestionRequest request = requestWith(List.of(scoringPoint));

        Set<ConstraintViolation<CreateQuestionRequest>> violations =
                validator.validate(request);

        assertTrue(hasMessage(violations, "评分点类型不能为空"));
        assertTrue(hasMessage(violations, "评分点内容不能为空"));
        assertTrue(hasMessage(violations, "评分点权重不能为空"));
    }

    @Test
    void shouldAcceptValidScoringPoint() {
        ScoringPointRequest scoringPoint = new ScoringPointRequest(
                QuestionPointType.CORE,
                "说明核心结论",
                100
        );

        Set<ConstraintViolation<ScoringPointRequest>> violations =
                validator.validate(scoringPoint);

        assertTrue(violations.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    @NullSource
    void shouldRejectMissingOrOutOfRangeWeight(Integer weight) {
        ScoringPointRequest scoringPoint = new ScoringPointRequest(
                QuestionPointType.CORE,
                "说明核心结论",
                weight
        );

        Set<ConstraintViolation<ScoringPointRequest>> violations =
                validator.validate(scoringPoint);

        assertTrue(violations.stream().anyMatch(
                violation -> "weight".equals(
                        violation.getPropertyPath().toString()
                )
        ));
    }

    private CreateQuestionRequest requestWith(
            List<ScoringPointRequest> scoringPoints
    ) {
        return CreateQuestionRequest.builder()
                .knowledgePoint("Java 集合")
                .questionContent("请说明 HashMap 的核心原理")
                .referenceAnswer("HashMap 基于数组和链表或红黑树实现")
                .scoringPoints(scoringPoints)
                .build();
    }

    private boolean hasMessage(
            Set<ConstraintViolation<CreateQuestionRequest>> violations,
            String message
    ) {
        return violations.stream().anyMatch(
                violation -> message.equals(violation.getMessage())
        );
    }
}
