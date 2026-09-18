package com.kun.aiinterview.interview.vo;

import com.kun.aiinterview.interview.enums.InterviewAnswerStatus;

import java.time.LocalDateTime;

public record InterviewDetailAnswerResponse(

        String answerContent,

        InterviewAnswerStatus answerStatus,

        LocalDateTime submittedAt

) {
}
