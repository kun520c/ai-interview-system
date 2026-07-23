package com.kun.aiinterview.question.mapper;

import com.kun.aiinterview.question.entity.Question;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionMapper {
    int insertQuestion(Question question);

    Question getQuestionById(Long id);

    int updateQuestion(Question question);
}
