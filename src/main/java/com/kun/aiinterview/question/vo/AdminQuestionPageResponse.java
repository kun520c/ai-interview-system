package com.kun.aiinterview.question.vo;

import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@Builder
@Data
@NoArgsConstructor
public class AdminQuestionPageResponse {
    private List<AdminQuestionListItem> records;
    private Long total;
    private Integer pageNum;
    private Integer pageSize;
    private Long totalPages;
}
