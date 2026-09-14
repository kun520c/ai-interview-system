package com.kun.aiinterview.interview.mapper;

import com.kun.aiinterview.interview.entity.InterviewQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InterviewQuestionMapper {

    InterviewQuestion getInterviewQuestionById(@Param("id") Long id);

    InterviewQuestion findNextPendingMainQuestion(
            @Param("sessionId") Long sessionId,
            @Param("currentPlanOrder") Integer currentPlanOrder
    );

    int markWaitingAnswer(
            @Param("id") Long id,
            @Param("sessionId") Long sessionId
    );

    int markAnswered(
            @Param("id") Long id,
            @Param("sessionId") Long sessionId
    );

    int insertFollowUp(InterviewQuestion interviewQuestion);

    InterviewQuestion getFollowUpByParentQuestionId(
            @Param("parentQuestionId") Long parentQuestionId
    );

    int batchInsertMainQuestions(
            @Param("questions")
            List<InterviewQuestion> questions
    );

    InterviewQuestion getMainQuestionByPlanOrder(
            @Param("sessionId") Long sessionId,
            @Param("planOrder") Integer planOrder
    );
}
