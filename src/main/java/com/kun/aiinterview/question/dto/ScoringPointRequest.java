package com.kun.aiinterview.question.dto;

import com.kun.aiinterview.common.validation.Utf8ByteSize;
import com.kun.aiinterview.question.enums.QuestionPointType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ScoringPointRequest {
    @NotNull(message = "评分点类型不能为空")
    private QuestionPointType pointType;
    @NotBlank(message = "评分点内容不能为空")
    @Size(max = Utf8ByteSize.MYSQL_TEXT_MAX_BYTES,
            message = "评分点内容长度不能超过65535位")
    @Utf8ByteSize(message = "评分点内容不能超过65535个UTF-8字节")
    private String content;
    @NotNull(message = "评分点权重不能为空")
    @Min(value = 1, message = "评分点权重不能小于1")
    @Max(value = 100, message = "评分点权重不能大于100")
    private Integer weight;
}
