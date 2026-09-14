package com.kun.aiinterview.interview.model;

import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.entity.QuestionScoringPoint;

import java.util.List;

public record InterviewMainQuestionDraft(
        Question question,
        List<QuestionScoringPoint> scoringPoints
) {

    public InterviewMainQuestionDraft{
        if(question == null){
            throw new IllegalArgumentException(
                    "question must not be null"
            );
        }

        if(scoringPoints == null || scoringPoints.isEmpty()){
            throw new IllegalArgumentException(
                    "scoringPoints must not be empty"
            );
        }

        scoringPoints = List.copyOf(scoringPoints);
    }
}
