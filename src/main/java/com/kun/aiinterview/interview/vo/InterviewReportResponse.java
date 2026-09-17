package com.kun.aiinterview.interview.vo;

import com.kun.aiinterview.interview.enums.InterviewReportStatus;

import java.math.BigDecimal;
import java.util.List;

public record InterviewReportResponse(

        Long sessionId,

        InterviewReportStatus reportStatus,

        BigDecimal overallScore,

        String result,

        String summary,

        List<String> strengths,

        List<String> weaknesses,

        List<String> suggestions

) {
}
