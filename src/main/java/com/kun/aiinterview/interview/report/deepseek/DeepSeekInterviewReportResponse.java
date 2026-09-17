package com.kun.aiinterview.interview.report.deepseek;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record DeepSeekInterviewReportResponse(
        String summary,
        List<String> strengths,
        List<String> weaknesses,
        List<String> suggestions
) {
}
