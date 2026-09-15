package com.kun.aiinterview.interview.vo;

import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationLevel;

import java.util.List;

public record InterviewEvaluationResponse(

        EvaluationPhase evaluationPhase,

        Integer correctnessScore,

        Integer completenessScore,

        Integer depthScore,

        Integer clarityScore,

        Integer practiceScore,

        Integer totalScore,

        EvaluationLevel level,

        List<String> strengths,

        List<String> missingPoints,

        String correction

) {
}
