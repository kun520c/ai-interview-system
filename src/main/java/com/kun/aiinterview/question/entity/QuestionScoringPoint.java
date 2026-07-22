package com.kun.aiinterview.question.entity;

import com.kun.aiinterview.question.enums.QuestionPointStatus;
import com.kun.aiinterview.question.enums.QuestionPointType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionScoringPoint {
    private Long id;
    private Long questionId;
    private QuestionPointType pointType;
    private String content;
    private int weight;
    private int sortOrder;
    private QuestionPointStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
