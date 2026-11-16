package com.kun.aiinterview.interview.mapper;

import com.kun.aiinterview.interview.entity.InterviewReportTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InterviewReportTaskMapper {
    LocalDateTime databaseNow();
    int insertQueued(@Param("task") InterviewReportTask task, @Param("queueSeconds") int queueSeconds);
    InterviewReportTask getById(@Param("id") Long id);
    InterviewReportTask getByIdForUpdate(@Param("id") Long id);
    List<InterviewReportTask> listActiveBySessionId(@Param("sessionId") Long sessionId);
    List<InterviewReportTask> listPending(@Param("limit") int limit);
    List<InterviewReportTask> listExpiredLeases(@Param("limit") int limit);
    List<InterviewReportTask> listExpiredQueued(@Param("limit") int limit);
    List<InterviewReportTask> listExpiredRunning(@Param("limit") int limit);
    int claimDispatch(@Param("id") Long id, @Param("token") String token,
                      @Param("leaseSeconds") int leaseSeconds, @Param("maxAttempts") int maxAttempts);
    int markSent(@Param("id") Long id, @Param("token") String token);
    int rescheduleDispatch(@Param("id") Long id, @Param("token") String token,
                           @Param("backoffSeconds") int backoffSeconds);
    int claimExecution(@Param("id") Long id, @Param("attemptVersion") long attemptVersion,
                       @Param("executionVersion") int executionVersion, @Param("executionSeconds") int executionSeconds);
    int finishSuccess(@Param("id") Long id, @Param("executionVersion") int executionVersion);
    int finishFailure(@Param("id") Long id, @Param("state") InterviewReportTask.State state,
                      @Param("failureCode") String failureCode);
}
