package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.common.exception.ConflictException;
import com.kun.aiinterview.common.exception.ResourceNotFoundException;
import com.kun.aiinterview.common.recovery.StaleRecoveryProperties;
import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.entity.InterviewReport;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.mapper.AnswerEvaluationMapper;
import com.kun.aiinterview.interview.mapper.InterviewReportMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.model.InterviewReportGenerationResult;
import com.kun.aiinterview.interview.vo.InterviewReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final AnswerEvaluationMapper answerEvaluationMapper;
    private final InterviewReportTransactionService transactionService;
    private final ObjectProvider<InterviewReportGenerator> generatorProvider;
    private final ObjectMapper objectMapper;
    private final StaleRecoveryProperties staleRecoveryProperties;

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

    public InterviewReportResponse generateReportForApi(
            Long userId,
            Long sessionId
    ) {
        return buildReadyResponse(
                sessionId,
                generateReport(userId, sessionId)
        );
    }

    public InterviewReport generateReport(
            Long userId,
            Long sessionId
    ) {
        if (userId == null || sessionId == null) {
            throw new IllegalArgumentException(
                    "userId和sessionId不能为空"
            );
        }

        InterviewSession session = loadOwnedSession(sessionId, userId);
        validateCompleted(session);

        InterviewReport existingReport =
                handleExistingReportStatus(session);
        if (existingReport != null) {
            return existingReport;
        }

        InterviewSession latest = loadOwnedSession(sessionId, userId);
        validateCompleted(latest);
        InterviewReportGenerator generator =
                requireGeneratorFor(latest);

        if (latest.getReportStatus()
                != InterviewReportStatus.GENERATING) {
            claimReportGeneration(latest);
        }

        try {
            return generateClaimedReport(
                    userId,
                    sessionId,
                    generator
            );
        } catch (RuntimeException original) {
            recoverReportFailure(sessionId, original);
            throw original;
        }
    }

    private InterviewReport generateClaimedReport(
            Long userId,
            Long sessionId,
            InterviewReportGenerator generator
    ) {
        InterviewSession generatingSession =
                reloadGeneratingSession(sessionId, userId);
        List<AnswerEvaluation> evaluations =
                answerEvaluationMapper
                        .listFinalEffectiveEvaluationsBySessionId(
                                sessionId
                        );
        validateEvaluations(
                generatingSession,
                evaluations
        );

        BigDecimal overallScore =
                calculateOverallScore(evaluations);
        InterviewReportGenerationResult generationResult =
                generator.generate(
                        generatingSession,
                        List.copyOf(evaluations),
                        overallScore
                );
        InterviewReport report = buildReport(
                sessionId,
                overallScore,
                generationResult
        );

        return transactionService.completeReportGeneration(
                report,
                generatingSession,
                evaluations
        );
    }

    private InterviewSession loadOwnedSession(Long sessionId, Long userId) {
        InterviewSession session = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        if (session == null || !Objects.equals(session.getUserId(), userId)) {
            throw new ResourceNotFoundException("面试会话不存在");
        }
        return session;
    }

    private void validateCompleted(InterviewSession session) {
        if (session.getStatus()
                != InterviewSessionStatus.COMPLETED) {
            throw new ConflictException(
                    "面试尚未完成，无法生成报告"
            );
        }
    }

    private InterviewReport handleExistingReportStatus(
            InterviewSession session
    ) {
        if (session.getReportStatus() == null) {
            throw new IllegalStateException(
                    "Session缺少Report状态"
            );
        }

        return switch (session.getReportStatus()) {
            case READY -> {
                InterviewReport report = interviewReportMapper
                        .getBySessionId(session.getId());
                if (report == null) {
                    throw new IllegalStateException(
                            "Session为READY但Report不存在"
                    );
                }
                yield report;
            }
            case GENERATING -> recoverGenerating(session);
            case NOT_STARTED, FAILED -> null;
        };
    }

    private InterviewReport recoverGenerating(InterviewSession session) {
        if (!staleRecoveryProperties
                .isGeneratingStale(session.getUpdatedAt())) {
            throw new ConflictException(
                    "报告正在生成中，请稍后重试"
            );
        }

        int reclaimed = interviewSessionMapper.reclaimStaleGenerating(
                session.getId(),
                staleRecoveryProperties.generatingCutoff()
        );
        if (reclaimed == 1) {
            return existingReportOrContinue(session);
        }

        InterviewSession latest = interviewSessionMapper
                .getInterviewSessionById(session.getId());
        if (latest == null) {
            throw new IllegalStateException(
                    "Report恢复时Session不存在"
            );
        }
        if (!Objects.equals(latest.getUserId(), session.getUserId())) {
            throw new IllegalStateException(
                    "Report恢复后Session不属于当前用户"
            );
        }

        return switch (latest.getReportStatus()) {
            case READY -> {
                InterviewReport report = interviewReportMapper
                        .getBySessionId(latest.getId());
                if (report == null) {
                    throw new IllegalStateException(
                            "Session为READY但Report不存在"
                    );
                }
                yield report;
            }
            case FAILED, NOT_STARTED -> null;
            case GENERATING -> throw new ConflictException(
                    "报告正在生成中，请稍后重试"
            );
        };
    }

    private InterviewReport existingReportOrContinue(
            InterviewSession session
    ) {
        InterviewReport report = interviewReportMapper
                .getBySessionId(session.getId());
        if (report == null) {
            return null;
        }

        InterviewSession generating = reloadGeneratingSession(
                session.getId(),
                session.getUserId()
        );
        if (report.getOverallScore() == null) {
            throw new IllegalStateException(
                    "已有Report缺少overallScore"
            );
        }
        int affectedRows = interviewSessionMapper.markReportReady(
                generating.getId(),
                generating.getVersion(),
                report.getOverallScore()
        );
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "已有Report时READY状态恢复失败"
            );
        }
        return report;
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

    private InterviewReportGenerator requireGeneratorFor(
            InterviewSession session
    ) {
        try {
            return requireGenerator();
        } catch (BusinessException exception) {
            if (session.getReportStatus()
                    == InterviewReportStatus.GENERATING) {
                recoverReportFailure(session.getId(), exception);
            }
            throw exception;
        }
    }

    private void claimReportGeneration(InterviewSession session) {
        int affectedRows = interviewSessionMapper
                .claimReportGeneration(
                        session.getId(),
                        session.getVersion(),
                        session.getReportStatus()
                );
        if (affectedRows != 1) {
            throw new ConflictException(
                    "报告生成状态已发生变化，请重试"
            );
        }
    }

    private InterviewSession reloadGeneratingSession(
            Long sessionId,
            Long userId
    ) {
        InterviewSession session = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        if (session == null) {
            throw new IllegalStateException(
                    "Report claim后Session不存在"
            );
        }

        if (!Objects.equals(session.getUserId(), userId)) {
            throw new IllegalStateException(
                    "Report claim后Session不属于当前用户"
            );
        }
        if (session.getStatus() != InterviewSessionStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Report claim后Session状态不是COMPLETED"
            );
        }
        if (session.getReportStatus()
                != InterviewReportStatus.GENERATING) {
            throw new IllegalStateException(
                    "Report claim后Session状态不是GENERATING"
            );
        }
        if (session.getVersion() == null) {
            throw new IllegalStateException(
                    "Report claim后Session缺少version"
            );
        }
        return session;
    }

    private void validateEvaluations(
            InterviewSession session,
            List<AnswerEvaluation> evaluations
    ) {
        if (evaluations == null || evaluations.isEmpty()) {
            throw new IllegalStateException(
                    "面试缺少有效Evaluation"
            );
        }
        if (session.getPlannedQuestionCount() == null
                || evaluations.size()
                != session.getPlannedQuestionCount()) {
            throw new IllegalStateException(
                    "有效Evaluation数量与计划的MAIN题数不一致"
            );
        }
    }

    private BigDecimal calculateOverallScore(
            List<AnswerEvaluation> evaluations
    ) {
        BigDecimal total = BigDecimal.ZERO;
        for (AnswerEvaluation evaluation : evaluations) {
            if (evaluation == null
                    || evaluation.getTotalScore() == null) {
                throw new IllegalStateException(
                        "有效Evaluation缺少totalScore"
                );
            }
            total = total.add(
                    BigDecimal.valueOf(
                            evaluation.getTotalScore()
                    )
            );
        }

        return total.divide(
                BigDecimal.valueOf(evaluations.size()),
                2,
                RoundingMode.HALF_UP
        );
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

    private void recoverReportFailure(
            Long sessionId,
            RuntimeException original
    ) {
        try {
            InterviewSession latest = interviewSessionMapper
                    .getInterviewSessionById(sessionId);
            if (latest == null) {
                throw new IllegalStateException(
                        "Report失败恢复时Session不存在"
                );
            }

            if (latest.getReportStatus()
                    == InterviewReportStatus.GENERATING) {
                int affectedRows = interviewSessionMapper
                        .markReportFailed(
                                latest.getId(),
                                latest.getVersion()
                        );
                if (affectedRows != 1) {
                    throw new IllegalStateException(
                            "Report状态更新为FAILED失败"
                    );
                }
            }
        } catch (RuntimeException recovery) {
            original.addSuppressed(recovery);
        }
    }
}
