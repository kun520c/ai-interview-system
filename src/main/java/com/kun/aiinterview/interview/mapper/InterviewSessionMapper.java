package com.kun.aiinterview.interview.mapper;

import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    InterviewSession getActiveSessionByUserId(
            @Param("userId") Long userId
    );

    int insertInterviewSession(
            InterviewSession interviewSession
    );

    int startSession(
            @Param("id") Long id,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("firstQuestionId") Long firstQuestionId
    );

    int claimReportGeneration(
            @Param("id") Long id,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("expectedReportStatus")
            InterviewReportStatus expectedReportStatus
    );

    int reclaimStaleGenerating(
            @Param("id") Long id,
            @Param("cutoff") LocalDateTime cutoff
    );

    int markReportReady(
            @Param("id") Long id,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("totalScore") BigDecimal totalScore
    );

    int markReportFailed(
            @Param("id") Long id,
            @Param("expectedVersion") Integer expectedVersion
    );

    List<InterviewSession> listCompletedSessionsByUserId(
            @Param("userId") Long userId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    long countCompletedSessionsByUserId(@Param("userId") Long userId);
}
