package com.kun.aiinterview.interview.mapper;

import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AnswerEvaluationMapper {

    int insertEvaluation(AnswerEvaluation evaluation);

    AnswerEvaluation getByAnswerId(@Param("answerId") Long answerId);

    // Returns FINAL candidates in MAIN plan order; duplicates and invalid associations
    // must remain visible so report validation can reject persisted inconsistencies.
    List<AnswerEvaluation> listFinalEffectiveEvaluationsBySessionId(
            @Param("sessionId") Long sessionId
    );

    List<AnswerEvaluation> listByAnswerIds(
            @Param("answerIds") List<Long> answerIds
    );
}
