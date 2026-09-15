package com.kun.aiinterview.interview.vo;

import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;

public record SubmitInterviewAnswerResponse(

        InterviewEvaluationResponse evaluation,

        DecisionAction nextAction,

        InterviewSessionStatus sessionStatus,

        InterviewQuestionResponse nextQuestion

) {
}
