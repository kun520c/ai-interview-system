package com.kun.aiinterview.question.mapper;

import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuestionScoringPointMapper {
    int batchInsert(
            @Param("scoringPoints")
            List<QuestionScoringPoint> scoringPoints
    );

    int deleteByQuestionId(@Param("questionId") Long questionId);
}
