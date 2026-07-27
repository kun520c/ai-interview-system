package com.kun.aiinterview.question.vo;

import com.kun.aiinterview.question.enums.QuestionPointType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminScoringPointDetail {
    private QuestionPointType pointType;
    private String content;
    private int weight;
}
