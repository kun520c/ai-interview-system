package com.kun.aiinterview.interview.mapper;

import com.kun.aiinterview.interview.entity.InterviewReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InterviewReportMapper {

    InterviewReport getBySessionId(
            @Param("sessionId") Long sessionId
    );

    int insertReport(
            InterviewReport report
    );
}
