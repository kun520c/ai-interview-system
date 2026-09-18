package com.kun.aiinterview.interview.vo;

import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.question.enums.QuestionDifficulty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InterviewHistoryItemResponse(

        Long sessionId,

        QuestionDifficulty difficulty,

        InterviewSessionStatus status,

        InterviewReportStatus reportStatus,

        BigDecimal totalScore,

        Integer plannedQuestionCount,

        Integer completedQuestionCount,

        LocalDateTime startedAt,

        LocalDateTime endedAt,

        LocalDateTime createdAt

) {
}
