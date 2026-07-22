package com.kun.aiinterview.question.entity;

import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {
    private Long id;
    private QuestionCategory category;
    private String knowledgePoint;
    private QuestionDifficulty difficulty;
    private String questionContent;
    private String referenceAnswer;
    private QuestionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
