package com.kun.aiinterview.interview.entity;

import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.question.enums.QuestionCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerEvaluation {

    private Long id;

    private Long answerId;

    private Long mainInterviewQuestionId;

    private QuestionCategory category;

    private String knowledgePoint;

    private String questionContent;

    private EvaluationPhase evaluationPhase;

    private Integer correctnessScore;

    private Integer completenessScore;

    private Integer depthScore;

    private Integer clarityScore;

    private Integer practiceScore;

    private Integer totalScore;

    private String level;

    private String strengths;

    private String missingPoints;

    private String correction;

    private String scoringPointResults;

    private Boolean followUpRecommended;

    private String suggestedFollowUp;

    private DecisionAction decisionAction;

    private String retrievalBatchId;

    private String llmModel;

    private String promptVersion;

    private String evaluationStandardVersion;

    private String rawResult;

    private LocalDateTime createdAt;
}
