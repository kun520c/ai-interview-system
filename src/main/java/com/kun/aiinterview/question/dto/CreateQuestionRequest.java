package com.kun.aiinterview.question.dto;

import com.kun.aiinterview.common.validation.Utf8ByteSize;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
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
    @Size(max = Utf8ByteSize.MYSQL_TEXT_MAX_BYTES,
            message = "题目内容长度不能超过65535位")
    @Utf8ByteSize(message = "题目内容不能超过65535个UTF-8字节")
    private String questionContent;
    @NotBlank(message = "参考答案不能为空")
    @Size(max = Utf8ByteSize.MYSQL_TEXT_MAX_BYTES,
            message = "参考答案长度不能超过65535位")
    @Utf8ByteSize(message = "参考答案不能超过65535个UTF-8字节")
    private String referenceAnswer;
    @NotEmpty(message = "题目至少需要一个评分点")
    @Valid
    private List<@NotNull(message = "评分点不能为空") ScoringPointRequest> scoringPoints;
}
