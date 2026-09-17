package com.kun.aiinterview.interview.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InterviewReport {

    private Long id;

    private Long sessionId;

    private BigDecimal overallScore;

    private String result;

    private String strengths;

    private String summary;

    private String weaknesses;

    private String suggestions;

    private String llmModel;

    private String promptVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
