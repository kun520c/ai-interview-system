package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.model.InterviewReportGenerationResult;

import java.math.BigDecimal;
import java.util.List;

public interface InterviewReportGenerator {

    InterviewReportGenerationResult generate(
            InterviewSession session,
            List<AnswerEvaluation> evaluations,
            BigDecimal overallScore
    );
}
