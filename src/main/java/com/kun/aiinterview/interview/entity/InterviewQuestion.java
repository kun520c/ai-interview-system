package com.kun.aiinterview.interview.entity;

import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.question.enums.QuestionCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InterviewQuestion {

    private Long id;

    private Long sessionId;

    private Long questionId;

    private QuestionCategory category;

    private String knowledgePoint;

    private String  questionContent;

    private String referenceAnswerSnapshot;

    private String scoringPointsSnapshot;

    private InterviewQuestionType questionType;

    private Long parentQuestionId;

    private String followUpTargetPoints;

    private Integer planOrder;

    private Integer displayOrder;

    private InterviewQuestionStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
