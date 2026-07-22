package com.kun.aiinterview.question.dto;

import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateQuestionRequest {
    @NotNull(message = "题目分类不能为空")
    private QuestionCategory category;
    @NotBlank(message = "知识点不能为空")
    @Size(max = 100,message = "知识点长度不能超过100位")
    private String knowledgePoint;
    @NotNull(message = "题目难度不能为空")
    private QuestionDifficulty difficulty;
    @NotBlank(message = "题目内容不能为空")
    private String questionContent;
    @NotBlank(message = "参考答案不能为空")
    private String referenceAnswer;
    @NotEmpty(message = "题目至少需要一个评分点")
    @Valid
    private List<@NotNull(message = "评分点不能为空") ScoringPointRequest> scoringPoints;
}
