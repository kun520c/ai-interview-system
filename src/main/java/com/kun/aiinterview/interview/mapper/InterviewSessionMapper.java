package com.kun.aiinterview.interview.mapper;

import com.kun.aiinterview.interview.entity.InterviewSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InterviewSessionMapper {

    InterviewSession getInterviewSessionById(@Param("id") Long id);

    int moveToFollowUp(
            @Param("id") Long id,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("expectedCurrentQuestionId") Long expectedCurrentQuestionId,
            @Param("followUpQuestionId") Long followUpQuestionId
    );

    int advanceToNextMain(
            @Param("id") Long id,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("expectedCurrentQuestionId") Long expectedCurrentQuestionId,
            @Param("nextMainQuestionId") Long nextMainQuestionId
    );

    int completeSession(
            @Param("id") Long id,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("expectedCurrentQuestionId") Long expectedCurrentQuestionId
    );
}
