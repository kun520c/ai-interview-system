package com.kun.aiinterview.question.vo;

import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminQuestionListItem {
    private Long id;
    private QuestionCategory category;
    private String knowledgePoint;
    private QuestionDifficulty difficulty;
    private String questionContent;
    private QuestionStatus status;
    private LocalDateTime updatedAt;
}
