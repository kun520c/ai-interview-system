package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.InterviewReport;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.mapper.InterviewReportMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InterviewReportTransactionService {

    private final InterviewReportMapper interviewReportMapper;
    private final InterviewSessionMapper interviewSessionMapper;

    @Transactional
    public InterviewReport completeReportGeneration(
            InterviewReport report,
            InterviewSession session
    ) {
        validateArguments(report, session);

        int reportRows = interviewReportMapper
                .insertReport(report);
        if (reportRows != 1) {
            throw new IllegalStateException(
                    "InterviewReport写入失败"
            );
        }
        if (report.getId() == null) {
            throw new IllegalStateException(
                    "InterviewReport主键未回填"
            );
        }

        int sessionRows = interviewSessionMapper
                .markReportReady(
                        session.getId(),
                        session.getVersion(),
                        report.getOverallScore()
                );
        if (sessionRows != 1) {
            throw new IllegalStateException(
                    "Session报告状态更新为READY失败"
            );
        }

        return report;
    }

    private void validateArguments(
            InterviewReport report,
            InterviewSession session
    ) {
        if (report == null
                || report.getSessionId() == null
                || report.getOverallScore() == null) {
            throw new IllegalArgumentException(
                    "InterviewReport及其核心字段不能为空"
            );
        }
        if (session == null
                || session.getId() == null
                || session.getVersion() == null) {
            throw new IllegalArgumentException(
                    "InterviewSession标识和version不能为空"
            );
        }
        if (!Objects.equals(
                report.getSessionId(),
                session.getId()
        )) {
            throw new IllegalArgumentException(
                    "InterviewReport不属于当前Session"
            );
        }
        if (session.getStatus()
                != InterviewSessionStatus.COMPLETED
                || session.getReportStatus()
                != InterviewReportStatus.GENERATING) {
            throw new IllegalStateException(
                    "Session不是可完成报告生成的状态"
            );
        }
    }
}
