package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.entity.InterviewAnswer;
import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.interview.mapper.InterviewAnswerMapper;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class EvaluationContextService {
    private static final TypeReference<List<ScoringPointSnapshot>>
            SCORING_POINTS_TYPE = new TypeReference<>() {
    };

    private static final TypeReference<List<Long>>
            TARGET_POINT_IDS_TYPE = new TypeReference<>() {
    };

    private final InterviewAnswerMapper interviewAnswerMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final ObjectMapper objectMapper;

    public EvaluationContextService(
            InterviewAnswerMapper interviewAnswerMapper,
            InterviewQuestionMapper interviewQuestionMapper,
            ObjectMapper objectMapper
    ) {
        this.interviewAnswerMapper = interviewAnswerMapper;
        this.interviewQuestionMapper = interviewQuestionMapper;
        this.objectMapper = objectMapper;
    }

    public EvaluationContext buildContext(Long answerId) {
        if (answerId == null) {
            throw new IllegalArgumentException("answerId must not be null");
        }

        InterviewAnswer currentAnswer =
                interviewAnswerMapper.getInterviewAnswerById(answerId);

        if (currentAnswer == null) {
            throw new IllegalArgumentException(
                    "Interview answer not found:" + answerId);
        }

        validateAnswerContent(currentAnswer);

        if (!Objects.equals(answerId, currentAnswer.getId())) {
            throw new IllegalStateException(
                    "Loaded interview answer id does not match requested answerId"
            );
        }

        InterviewQuestion currentQuestion =
                interviewQuestionMapper.getInterviewQuestionById(
                        currentAnswer.getInterviewQuestionId()
                );

        if (currentQuestion == null) {
            throw new IllegalStateException(
                    "Interview question not found for answer:" + answerId
            );
        }

        validateQuestionIdentity(
                currentQuestion,
                currentAnswer.getInterviewQuestionId()
        );

        if (currentQuestion.getQuestionType() == null) {
            throw new IllegalStateException(
                    "Interview question type must not be null:"
                            + currentQuestion.getId()
            );
        }

        return switch (currentQuestion.getQuestionType()) {
            case MAIN ->
                buildMainContext(currentAnswer,currentQuestion);

            case FOLLOW_UP ->
                buildFollowUpContext(currentAnswer,currentQuestion);
        };
    }

    private EvaluationContext buildMainContext(
            InterviewAnswer mainAnswer,
            InterviewQuestion mainQuestion
    ) {
        validateMainQuestion(mainQuestion);

        List<ScoringPointSnapshot> scoringPoints =
                parseScoringPoints(mainQuestion.getScoringPointsSnapshot());

        validateScoringPoints(scoringPoints);

        return new EvaluationContext(
                mainAnswer.getId(),
                mainQuestion.getId(),

                mainAnswer.getId(),
                mainQuestion.getId(),

                EvaluationMode.MAIN_ANSWER,

                mainQuestion.getCategory(),
                mainQuestion.getKnowledgePoint(),

                mainQuestion.getQuestionContent(),
                mainQuestion.getReferenceAnswerSnapshot(),

                scoringPoints,

                mainAnswer.getAnswerContent(),

                null,
                null,

                List.of()
        );
    }

    private EvaluationContext buildFollowUpContext(
            InterviewAnswer followUpAnswer,
            InterviewQuestion followUpQuestion
    ) {
        validateFollowUpQuestion(followUpQuestion);

        InterviewQuestion mainQuestion =
                interviewQuestionMapper.getInterviewQuestionById(
                        followUpQuestion.getParentQuestionId()
                );

        if (mainQuestion == null) {
            throw new IllegalStateException(
                    "Parent main interview question not found:"
                            + followUpQuestion.getParentQuestionId()
            );
        }

        validateQuestionIdentity(
                mainQuestion,
                followUpQuestion.getParentQuestionId()
        );

        validateMainQuestion(mainQuestion);

        if (!Objects.equals(
                mainQuestion.getSessionId(),
                followUpQuestion.getSessionId()
        )) {
            throw new IllegalStateException(
                    "Follow-up question and main question"
                            + "must belong to the same interview session"
            );
        }

        InterviewAnswer mainAnswer =
                interviewAnswerMapper
                        .getInterviewAnswerByInterviewQuestionId(
                                mainQuestion.getId()
                        );

        if (mainAnswer == null) {
            throw new IllegalStateException(
                    "Main answer not found for follow-up question:"
                                + followUpQuestion.getId()
            );
        }

        validateAnswerContent(mainAnswer);

        if (!Objects.equals(
                mainQuestion.getId(),
                mainAnswer.getInterviewQuestionId()
        )) {
            throw new IllegalStateException(
                    "Main answer does not belong to the expected main question"
            );
        }

        List<ScoringPointSnapshot> scoringPoints =
                parseScoringPoints(mainQuestion.getScoringPointsSnapshot());

        validateScoringPoints(scoringPoints);

        List<Long> targetPointIds =
                parseFollowUpTargetPointIds(
                        followUpQuestion.getFollowUpTargetPoints()
                );

        validateFollowUpTargetPointIds(
                targetPointIds,
                scoringPoints
        );

        return new EvaluationContext(
                followUpAnswer.getId(),
                followUpQuestion.getId(),

                mainAnswer.getId(),
                mainQuestion.getId(),

                EvaluationMode.FOLLOW_UP_ANSWER,

                mainQuestion.getCategory(),
                mainQuestion.getKnowledgePoint(),

                mainQuestion.getQuestionContent(),
                mainQuestion.getReferenceAnswerSnapshot(),

                scoringPoints,

                mainAnswer.getAnswerContent(),

                followUpQuestion.getQuestionContent(),
                followUpAnswer.getAnswerContent(),

                targetPointIds
        );
    }

    private void validateMainQuestion(
            InterviewQuestion question
    ) {
        if (question.getQuestionType()
                != InterviewQuestionType.MAIN) {
            throw new IllegalStateException(
                    "Expected MAIN interview question: "
                            + question.getId()
            );
        }

        if (question.getQuestionId() == null) {
            throw new IllegalStateException(
                    "MAIN interview question must reference question bank"
            );
        }

        if (question.getParentQuestionId() != null) {
            throw new IllegalStateException(
                    "MAIN interview question must not have parentQuestionId"
            );
        }

        if (question.getPlanOrder() == null) {
            throw new IllegalStateException(
                    "MAIN interview question must have planOrder"
            );
        }

        if (question.getSessionId() == null) {
            throw new IllegalStateException(
                    "MAIN interview question must have sessionId"
            );
        }

        if (question.getCategory() == null) {
            throw new IllegalStateException(
                    "MAIN interview question must have category"
            );
        }

        if (isBlank(question.getKnowledgePoint())) {
            throw new IllegalStateException(
                    "MAIN interview question must have knowledgePoint"
            );
        }

        if (isBlank(question.getQuestionContent())) {
            throw new IllegalStateException(
                    "MAIN interview question must have questionContent"
            );
        }

        if (isBlank(question.getReferenceAnswerSnapshot())) {
            throw new IllegalStateException(
                    "MAIN interview question must have "
                            + "referenceAnswerSnapshot"
            );
        }

        if (isBlank(question.getScoringPointsSnapshot())) {
            throw new IllegalStateException(
                    "MAIN interview question must have "
                            + "scoringPointsSnapshot"
            );
        }
    }

    private void validateFollowUpQuestion(
            InterviewQuestion question
    ) {
        if (question.getQuestionType()
                != InterviewQuestionType.FOLLOW_UP) {
            throw new IllegalStateException(
                    "Expected FOLLOW_UP interview question: "
                            + question.getId()
            );
        }

        if (question.getQuestionId() != null) {
            throw new IllegalStateException(
                    "FOLLOW_UP interview question "
                            + "must not reference question bank"
            );
        }

        if (question.getParentQuestionId() == null) {
            throw new IllegalStateException(
                    "FOLLOW_UP interview question must have parentQuestionId"
            );
        }

        if (question.getPlanOrder() != null) {
            throw new IllegalStateException(
                    "FOLLOW_UP interview question must not have planOrder"
            );
        }

        if (question.getSessionId() == null) {
            throw new IllegalStateException(
                    "FOLLOW_UP interview question must have sessionId"
            );
        }

        if (isBlank(question.getQuestionContent())) {
            throw new IllegalStateException(
                    "FOLLOW_UP interview question must have questionContent"
            );
        }

        if (question.getReferenceAnswerSnapshot() != null) {
            throw new IllegalStateException(
                    "FOLLOW_UP interview question must not have "
                            + "referenceAnswerSnapshot"
            );
        }

        if (question.getScoringPointsSnapshot() != null) {
            throw new IllegalStateException(
                    "FOLLOW_UP interview question must not have "
                            + "scoringPointsSnapshot"
            );
        }

        if (isBlank(question.getFollowUpTargetPoints())) {
            throw new IllegalStateException(
                    "FOLLOW_UP interview question must have "
                            + "followUpTargetPoints"
            );
        }
    }

    private void validateAnswerContent(
            InterviewAnswer answer
    ) {
        if (answer.getId() == null) {
            throw new IllegalStateException(
                    "Interview answer must have id"
            );
        }

        if (answer.getInterviewQuestionId() == null) {
            throw new IllegalStateException(
                    "Interview answer must have interviewQuestionId"
            );
        }

        if (isBlank(answer.getAnswerContent())) {
            throw new IllegalStateException(
                    "Interview answer content must not be blank: "
                            + answer.getId()
            );
        }
    }

    private void validateQuestionIdentity(
            InterviewQuestion question,
            Long expectedQuestionId
    ) {
        if (question.getId() == null) {
            throw new IllegalStateException(
                    "Interview question must have id"
            );
        }

        if (!Objects.equals(expectedQuestionId, question.getId())) {
            throw new IllegalStateException(
                    "Loaded interview question id does not match expected id"
            );
        }
    }

    private List<ScoringPointSnapshot> parseScoringPoints(
            String json
    ) {
        try {
            return objectMapper.readValue(
                    json,
                    SCORING_POINTS_TYPE
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Invalid scoringPointsSnapshot JSON",
                    ex
            );
        }
    }

    private List<Long> parseFollowUpTargetPointIds(
            String json
    ) {
        try {
            return objectMapper.readValue(
                    json,
                    TARGET_POINT_IDS_TYPE
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Invalid followUpTargetPoints JSON",
                    ex
            );
        }
    }

    private void validateScoringPoints(
            List<ScoringPointSnapshot> scoringPoints
    ) {
        if (scoringPoints == null || scoringPoints.isEmpty()) {
            throw new IllegalStateException(
                    "Scoring point snapshot must not be empty"
            );
        }

        Set<Long> scoringPointIds = new HashSet<>();
        int totalWeight = 0;

        for (ScoringPointSnapshot point : scoringPoints) {
            if (point == null) {
                throw new IllegalStateException(
                        "Scoring point snapshot must not contain null"
                );
            }

            if (point.scoringPointId() == null
                    || point.scoringPointId() <= 0) {
                throw new IllegalStateException(
                        "Scoring point id must be positive"
                );
            }

            if (!scoringPointIds.add(point.scoringPointId())) {
                throw new IllegalStateException(
                        "Duplicate scoring point id: "
                                + point.scoringPointId()
                );
            }

            if (point.pointType() == null) {
                throw new IllegalStateException(
                        "Scoring point type must not be null"
                );
            }

            if (isBlank(point.content())) {
                throw new IllegalStateException(
                        "Scoring point content must not be blank"
                );
            }

            if (point.weight() < 1 || point.weight() > 100) {
                throw new IllegalStateException(
                        "Scoring point weight must be between 1 and 100"
                );
            }

            totalWeight += point.weight();
        }

        if (totalWeight != 100) {
            throw new IllegalStateException(
                    "Scoring point weights must sum to 100"
            );
        }
    }

    private void validateFollowUpTargetPointIds(
            List<Long> targetPointIds,
            List<ScoringPointSnapshot> scoringPoints
    ) {
        if (targetPointIds == null || targetPointIds.isEmpty()) {
            throw new IllegalStateException(
                    "Follow-up target point ids must not be empty"
            );
        }

        Set<Long> validPointIds = new HashSet<>();

        for (ScoringPointSnapshot scoringPoint : scoringPoints) {
            validPointIds.add(scoringPoint.scoringPointId());
        }

        Set<Long> seenTargetIds = new HashSet<>();

        for (Long targetPointId : targetPointIds) {
            if (targetPointId == null) {
                throw new IllegalStateException(
                        "Follow-up target point id must not be null"
                );
            }

            if (!seenTargetIds.add(targetPointId)) {
                throw new IllegalStateException(
                        "Duplicate follow-up target point id: "
                                + targetPointId
                );
            }

            if (!validPointIds.contains(targetPointId)) {
                throw new IllegalStateException(
                        "Follow-up target point does not belong "
                                + "to main scoring point snapshot: "
                                + targetPointId
                );
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
