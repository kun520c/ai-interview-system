package com.kun.aiinterview.question.vo;

import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminQuestionDetailResponse {
    private Long id;
    private QuestionCategory category;
    private String knowledgePoint;
    private QuestionDifficulty difficulty;
    private String content;
    private String referenceAnswer;
    private QuestionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AdminScoringPointDetail> scoringPoints;
}
