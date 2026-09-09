package com.kun.aiinterview.interview.entity;

import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InterviewSession {

    private Long id;

    private Long userId;

    private QuestionDifficulty difficulty;

    private InterviewSessionStatus status;

    private Long currentInterviewQuestionId;

    private Integer plannedQuestionCount;

    private Integer completedQuestionCount;

    private BigDecimal totalScore;

    private InterviewReportStatus reportStatus;

    private Integer version;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
