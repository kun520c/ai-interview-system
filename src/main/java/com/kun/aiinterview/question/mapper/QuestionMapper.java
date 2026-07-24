package com.kun.aiinterview.question.mapper;

import com.kun.aiinterview.question.dto.QuestionPageQuery;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.vo.AdminQuestionListItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuestionMapper {
    int insertQuestion(Question question);

    Question getQuestionById(Long id);

    int updateQuestion(Question question);

    long countQuestion(@Param("query")QuestionPageQuery query);

    List<AdminQuestionListItem> selectQuestionPage(
            @Param("query")QuestionPageQuery query,
            @Param("offset") Long offset,
            @Param("pageSize") int pageSize
            );
}
