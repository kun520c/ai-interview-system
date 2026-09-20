package com.kun.aiinterview.common.validation;

import com.kun.aiinterview.interview.dto.SubmitInterviewAnswerRequest;
import com.kun.aiinterview.question.dto.CreateQuestionRequest;
import com.kun.aiinterview.question.dto.ScoringPointRequest;
import com.kun.aiinterview.question.dto.UpdateQuestionRequest;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionPointType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class Utf8TextRequestValidationTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void shouldAcceptAnswerAtExactAsciiByteLimit() {
        SubmitInterviewAnswerRequest request =
                new SubmitInterviewAnswerRequest(
                        1L,
                        "a".repeat(65_535),
                        "request-1"
                );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldRejectAnswerAboveAsciiByteLimit() {
        SubmitInterviewAnswerRequest request =
                new SubmitInterviewAnswerRequest(
                        1L,
                        "a".repeat(65_536),
                        "request-1"
                );

        assertThat(hasPropertyViolation(
                validator.validate(request),
                "answerContent"
        )).isTrue();
    }

    @Test
    void shouldAcceptQuestionFieldsAtMultibyteByteLimit() {
        String withinLimit = "😀".repeat(16_383);
        CreateQuestionRequest request = createRequest(
                withinLimit,
                withinLimit,
                withinLimit
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldRejectEveryQuestionTextFieldAboveMultibyteByteLimit() {
        String oversized = "😀".repeat(16_384);
        CreateQuestionRequest createRequest = createRequest(
                oversized,
                oversized,
                oversized
        );
        UpdateQuestionRequest updateRequest = updateRequest(
                oversized,
                oversized,
                oversized
        );

        Set<ConstraintViolation<CreateQuestionRequest>> createViolations =
                validator.validate(createRequest);
        Set<ConstraintViolation<UpdateQuestionRequest>> updateViolations =
                validator.validate(updateRequest);

        assertThat(hasPropertyViolation(
                createViolations,
                "questionContent"
        )).isTrue();
        assertThat(hasPropertyViolation(
                createViolations,
                "referenceAnswer"
        )).isTrue();
        assertThat(hasPropertyViolation(
                createViolations,
                "scoringPoints[0].content"
        )).isTrue();
        assertThat(hasPropertyViolation(
                updateViolations,
                "questionContent"
        )).isTrue();
        assertThat(hasPropertyViolation(
                updateViolations,
                "referenceAnswer"
        )).isTrue();
        assertThat(hasPropertyViolation(
                updateViolations,
                "scoringPoints[0].content"
        )).isTrue();
    }

    private CreateQuestionRequest createRequest(
            String questionContent,
            String referenceAnswer,
            String scoringPointContent
    ) {
        return CreateQuestionRequest.builder()
                .category(QuestionCategory.JAVA_COLLECTION)
                .knowledgePoint("HashMap")
                .difficulty(QuestionDifficulty.MEDIUM)
                .questionContent(questionContent)
                .referenceAnswer(referenceAnswer)
                .scoringPoints(List.of(scoringPoint(scoringPointContent)))
                .build();
    }

    private UpdateQuestionRequest updateRequest(
            String questionContent,
            String referenceAnswer,
            String scoringPointContent
    ) {
        return UpdateQuestionRequest.builder()
                .category(QuestionCategory.JAVA_COLLECTION)
                .knowledgePoint("HashMap")
                .difficulty(QuestionDifficulty.MEDIUM)
                .questionContent(questionContent)
                .referenceAnswer(referenceAnswer)
                .scoringPoints(List.of(scoringPoint(scoringPointContent)))
                .build();
    }

    private ScoringPointRequest scoringPoint(String content) {
        return new ScoringPointRequest(
                QuestionPointType.CORE,
                content,
                100
        );
    }

    private boolean hasPropertyViolation(
            Set<? extends ConstraintViolation<?>> violations,
            String property
    ) {
        return violations.stream().anyMatch(
                violation -> property.equals(
                        violation.getPropertyPath().toString()
                )
        );
    }
}
