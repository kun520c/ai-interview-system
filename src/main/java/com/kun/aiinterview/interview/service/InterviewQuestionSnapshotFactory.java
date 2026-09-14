package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewQuestionSnapshotFactory {

    private final ObjectMapper objectMapper;

    public InterviewQuestion createMainQuestion(
            Long sessionId,
            Question question,
            List<QuestionScoringPoint> scoringPoints,
            int planOrder
    ){
        validate(sessionId,question,scoringPoints,planOrder);

        List<ScoringPointSnapshot> snapshots = scoringPoints.stream()
                .map(point -> new ScoringPointSnapshot(
                        point.getId(),
                        point.getPointType(),
                        point.getContent(),
                        point.getWeight()
                ))
                .toList();

        String scoringPointsSnapshot = toJson(snapshots);

        return InterviewQuestion.builder()
                .sessionId(sessionId)
                .questionId(question.getId())
                .category(question.getCategory())
                .knowledgePoint(question.getKnowledgePoint())
                .questionContent(question.getQuestionContent())
                .referenceAnswerSnapshot(question.getReferenceAnswer())
                .scoringPointsSnapshot(scoringPointsSnapshot)
                .questionType(InterviewQuestionType.MAIN)
                .parentQuestionId(null)
                .followUpTargetPoints(null)
                .planOrder(planOrder)
                .displayOrder(Math.subtractExact(
                        Math.multiplyExact(planOrder, 2),
                        1
                ))
                .status(InterviewQuestionStatus.PENDING)
                .build();
    }

    private void validate(
            Long sessionId,
            Question question,
            List<QuestionScoringPoint> scoringPoints,
            int planOrder
    ){
        if(sessionId == null){
            throw new IllegalArgumentException("sessionId must not be null");
        }

        if(question == null || question.getId() == null){
            throw new IllegalArgumentException("question and question.id must not be null");
        }

        if (planOrder <= 0) {
            throw new IllegalArgumentException("planOrder must be positive");
        }

        if (scoringPoints == null || scoringPoints.isEmpty()) {
            throw new IllegalStateException(
                    "question must have enabled scoring points"
            );
        }

        long totalWeight = 0;

        for (QuestionScoringPoint point : scoringPoints) {
            if (point == null || point.getId() == null) {
                throw new IllegalStateException(
                        "scoring point and scoring point id must not be null"
                );
            }

            if (point.getPointType() == null) {
                throw new IllegalStateException(
                        "scoring point type must not be null"
                );
            }

            if (!question.getId().equals(point.getQuestionId())) {
                throw new IllegalStateException(
                        "scoring point does not belong to question"
                );
            }

            if (point.getContent() == null || point.getContent().isBlank()) {
                throw new IllegalStateException(
                        "scoring point content must not be blank"
                );
            }

            if (point.getWeight() <= 0) {
                throw new IllegalStateException(
                        "scoring point weight must be positive"
                );
            }

            totalWeight += point.getWeight();
        }

        if (totalWeight != 100) {
            throw new IllegalStateException(
                    "enabled scoring point weights must sum to 100"
            );
        }
    }

    private String toJson(List<ScoringPointSnapshot> snapshots){
        try{
            return objectMapper.writeValueAsString(snapshots);
        }catch (JsonProcessingException e){
            throw new IllegalStateException(
                    "failed to serialize scoring point snapshot",
                    e
            );
        }
    }
}
