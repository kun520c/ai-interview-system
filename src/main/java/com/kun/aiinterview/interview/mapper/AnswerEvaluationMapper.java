package com.kun.aiinterview.interview.mapper;

import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnswerEvaluationMapper {

    int insertEvaluation(
            AnswerEvaluation evaluation
    );

    AnswerEvaluation getByAnswerId(
            Long answerId
    );
}
