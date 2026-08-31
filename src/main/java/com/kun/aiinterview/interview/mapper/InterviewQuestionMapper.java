package com.kun.aiinterview.interview.mapper;

import com.kun.aiinterview.interview.entity.InterviewQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewQuestionMapper {

    InterviewQuestion getInterviewQuestionById(Long id);
}
