package com.kun.aiinterview.interview.mapper;

import com.kun.aiinterview.interview.entity.InterviewAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InterviewAnswerMapper {

    InterviewAnswer getInterviewAnswerById(@Param("id") Long id);

    InterviewAnswer getInterviewAnswerByInterviewQuestionId(
            @Param("interviewQuestionId") Long interviewQuestionId
    );

    InterviewAnswer getInterviewAnswerByRequestId(@Param("requestId") String requestId);

    int insertInterviewAnswer(InterviewAnswer interviewAnswer);

    int claimEvaluation(@Param("id") Long id);

    int retryEvaluation(@Param("id") Long id);

    int markEvaluated(@Param("id") Long id);

    int markFailed(
            @Param("id") Long id,
            @Param("errorCode") String errorCode
    );
}
