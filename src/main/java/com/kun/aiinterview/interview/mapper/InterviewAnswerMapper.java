package com.kun.aiinterview.interview.mapper;

import com.kun.aiinterview.interview.entity.InterviewAnswer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewAnswerMapper {

    InterviewAnswer getInterviewAnswerById(Long id);

    InterviewAnswer getInterviewAnswerByInterviewQuestionId(
            Long interviewQuestionId
    );
}
