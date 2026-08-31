package com.kun.aiinterview.interview.entity;

import com.kun.aiinterview.interview.enums.InterviewAnswerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InterviewAnswer {

    private Long id;

    private Long interviewQuestionId;

    private String answerContent;

    private InterviewAnswerStatus status;

    private String requestId;

    private String errorCode;

    private LocalDateTime submittedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
