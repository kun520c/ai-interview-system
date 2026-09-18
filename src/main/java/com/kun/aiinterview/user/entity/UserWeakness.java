package com.kun.aiinterview.user.entity;

import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.user.enums.UserWeaknessStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserWeakness {

    private Long id;

    private Long userId;

    private QuestionCategory category;

    private String knowledgePoint;

    private BigDecimal weaknessScore;

    private Integer discoveredCount;

    private LocalDateTime lastDiscoveredAt;

    private UserWeaknessStatus status;

    private LocalDateTime resolvedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
