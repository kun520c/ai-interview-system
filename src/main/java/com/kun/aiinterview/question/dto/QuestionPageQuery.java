package com.kun.aiinterview.question.dto;

import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QuestionPageQuery {
    @Min(value = 1,message = "页码必须大于等于1")
    @Builder.Default
    private Integer pageNum = 1;
    @Min(value = 1,message = "每页数量必须大于等于1")
    @Max(value = 50,message = "每页数量不能超过50")
    @Builder.Default
    private Integer pageSize = 10;
    private QuestionCategory category;
    private QuestionDifficulty difficulty;
    private QuestionStatus status;
    @Size(max = 100,message = "搜索关键词长度不能超过100位")
    private String keyword;

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum == null ? 1 : pageNum;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize == null ? 10 : pageSize;
    }
}
