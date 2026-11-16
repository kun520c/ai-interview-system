package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.common.exception.ResourceNotFoundException;
import com.kun.aiinterview.interview.entity.InterviewReport;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.mapper.InterviewReportMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.model.InterviewReportGenerationResult;
import com.kun.aiinterview.interview.vo.InterviewReportResponse;
import com.kun.aiinterview.interview.report.async.ReportExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InterviewReportService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE =
            new TypeReference<>() {
            };

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewReportMapper interviewReportMapper;
    private final InterviewReportTransactionService transactionService;
    private final ObjectProvider<InterviewReportGenerator> generatorProvider;
    private final ObjectMapper objectMapper;
    private final InterviewReportFinalReader finalReader;

    public InterviewReportResponse getReport(
            Long userId,
            Long sessionId
    ) {
        if (userId == null || sessionId == null) {
            throw new IllegalArgumentException(
                    "userId和sessionId不能为空"
            );
        }

        InterviewSession session = loadOwnedSession(sessionId, userId);

        InterviewReportStatus reportStatus =
                session.getReportStatus();
        if (reportStatus == null) {
            throw new IllegalStateException(
                    "Session缺少Report状态"
            );
        }

        if (reportStatus != InterviewReportStatus.READY) {
            return buildStatusResponse(sessionId, reportStatus);
        }

        InterviewReport report = interviewReportMapper
                .getBySessionId(sessionId);
        if (report == null) {
            throw new IllegalStateException(
                    "Session为READY但Report不存在"
            );
        }

        return buildReadyResponse(sessionId, report);
    }

    /** POST accepts durable work; it never calls the model. */
    public InterviewReportResponse generateReportForApi(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) {
            throw new IllegalArgumentException("userId和sessionId不能为空");
        }
        InterviewReportStatus status = transactionService.acceptReportTask(userId, sessionId);
        return status == InterviewReportStatus.READY
                ? buildReadyResponse(sessionId, interviewReportMapper.getBySessionId(sessionId))
                : buildStatusResponse(sessionId, status);
    }

    /** Only a successful consumer claim supplies this immutable execution token. */
    public void generateClaimedReport(ReportExecution execution) {
        InterviewReport report;
        try {
            var session = execution.session();
            var aggregate = finalReader.aggregateFinalEvaluations(session);
            // A historical report is reconciled by the final transaction without another model call.
            InterviewReport existing = interviewReportMapper.getBySessionId(session.getId());
            report = existing != null ? existing : buildReport(session.getId(), aggregate.overallScore(),
                    requireGenerator().generate(session, List.copyOf(aggregate.evaluations()), aggregate.overallScore()));
        } catch (org.springframework.dao.DataAccessException uncertain) {
            throw uncertain;
        } catch (RuntimeException deterministic) {
            transactionService.failExecution(execution, "GENERATION_FAILED");
            return;
        }
        // Commit uncertainty must escape to MQ. Never turn an uncertain commit into an ACK.
        try {
            transactionService.completeReportGeneration(execution, report);
        } catch (IllegalStateException | IllegalArgumentException deterministic) {
            transactionService.failExecution(execution, "COMPLETION_REJECTED");
        }
    }

    private InterviewSession loadOwnedSession(Long sessionId, Long userId) {
        InterviewSession session = interviewSessionMapper
                .getReportSession(sessionId);
        if (session == null || !Objects.equals(session.getUserId(), userId)) {
            throw new ResourceNotFoundException("面试会话不存在");
        }
        return session;
    }

    private InterviewReportGenerator requireGenerator() {
        InterviewReportGenerator generator =
                generatorProvider.getIfAvailable();
        if (generator == null) {
            throw new BusinessException(
                    "报告生成服务当前不可用"
            );
        }
        return generator;
    }

    private InterviewReport buildReport(
            Long sessionId,
            BigDecimal overallScore,
            InterviewReportGenerationResult generationResult
    ) {
        if (generationResult == null) {
            throw new IllegalStateException(
                    "Report Generator返回空结果"
            );
        }

        return InterviewReport.builder()
                .sessionId(sessionId)
                .overallScore(overallScore)
                .result(generationResult.result())
                .summary(generationResult.summary())
                .strengths(toJson(
                        generationResult.strengths(),
                        "strengths"
                ))
                .weaknesses(toJson(
                        generationResult.weaknesses(),
                        "weaknesses"
                ))
                .suggestions(toJson(
                        generationResult.suggestions(),
                        "suggestions"
                ))
                .llmModel(generationResult.llmModel())
                .promptVersion(generationResult.promptVersion())
                .build();
    }

    private String toJson(Object value, String fieldName) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Report " + fieldName + " JSON序列化失败",
                    exception
            );
        }
    }

    private InterviewReportResponse buildStatusResponse(
            Long sessionId,
            InterviewReportStatus reportStatus
    ) {
        return new InterviewReportResponse(
                sessionId,
                reportStatus,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private InterviewReportResponse buildReadyResponse(
            Long sessionId,
            InterviewReport report
    ) {
        if (report == null) {
            throw new IllegalStateException(
                    "InterviewReport不能为空"
            );
        }
        if (!Objects.equals(report.getSessionId(), sessionId)) {
            throw new IllegalStateException(
                    "InterviewReport不属于当前Session"
            );
        }

        return new InterviewReportResponse(
                sessionId,
                InterviewReportStatus.READY,
                report.getOverallScore(),
                report.getResult(),
                report.getSummary(),
                parseStringList(
                        report.getStrengths(),
                        "strengths"
                ),
                parseStringList(
                        report.getWeaknesses(),
                        "weaknesses"
                ),
                parseStringList(
                        report.getSuggestions(),
                        "suggestions"
                )
        );
    }

    private List<String> parseStringList(
            String json,
            String fieldName
    ) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException(
                    "InterviewReport中的" + fieldName + " JSON为空"
            );
        }

        try {
            List<String> values = objectMapper.readValue(
                    json,
                    STRING_LIST_TYPE
            );

            if (values == null) {
                throw new IllegalStateException(
                        "InterviewReport中的" + fieldName + "为空"
                );
            }
            return List.copyOf(values);
        } catch (JsonProcessingException | NullPointerException exception) {
            throw new IllegalStateException(
                    "InterviewReport中的" + fieldName + " JSON非法",
                    exception
            );
        }
    }

}
