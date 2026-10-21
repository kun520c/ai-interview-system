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

        if (session.getReportStatus() == InterviewReportStatus.READY) {
            return requireExistingReport(sessionId);
        }

        if (session.getVersion() == null || session.getReportStatus() == null) {
            throw new IllegalStateException("Session缺少Report状态或version");
        }

        int claimed;
        if (session.getReportStatus() == InterviewReportStatus.GENERATING) {
            if (!staleRecoveryProperties.isGeneratingStale(session.getUpdatedAt())) {
                throw new ConflictException("报告正在生成中，请稍后重试");
            }
            claimed = interviewSessionMapper.reclaimStaleGenerating(
                    sessionId, staleRecoveryProperties.generatingCutoff(), session.getVersion());
        } else {
            requireGenerator();
            claimed = interviewSessionMapper.claimReportGeneration(
                    sessionId, session.getVersion(), session.getReportStatus());
        }

        if (claimed != 1) {
            InterviewSession latest = loadOwnedSession(sessionId, userId);
            if (latest.getReportStatus() == InterviewReportStatus.READY) {
                return requireExistingReport(sessionId);
            }
            throw new ConflictException("报告生成状态已发生变化，请重试");
        }

        // The successful CAS increments exactly this version. Never reload a newer token.
        session.setVersion(Math.addExact(session.getVersion(), 1));
        session.setReportStatus(InterviewReportStatus.GENERATING);
        int attemptVersion = session.getVersion();

        try {
            InterviewReport existing = existingReportOrContinue(session);
            if (existing != null) {
                return existing;
            }
            return generateClaimedReport(session, requireGenerator());
        } catch (RuntimeException original) {
            recoverReportFailure(sessionId, attemptVersion, original);
            throw original;
        }
    }

    private InterviewReport requireExistingReport(Long sessionId) {
        InterviewReport report = interviewReportMapper.getBySessionId(sessionId);
        if (report == null) {
            throw new IllegalStateException("Session为READY但Report不存在");
        }
        return report;
    }

    private InterviewReport generateClaimedReport(
            InterviewSession generatingSession,
            InterviewReportGenerator generator
    ) {
        Long sessionId = generatingSession.getId();
        List<AnswerEvaluation> evaluations = answerEvaluationMapper
                .listFinalEffectiveEvaluationsBySessionId(sessionId);
        validateEvaluations(generatingSession, evaluations);

        BigDecimal overallScore = calculateOverallScore(evaluations);
        InterviewReportGenerationResult generationResult = generator.generate(
                generatingSession, List.copyOf(evaluations), overallScore);
        InterviewReport report = buildReport(sessionId, overallScore, generationResult);

        return transactionService.completeReportGeneration(report, generatingSession, evaluations);
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

    private InterviewReport existingReportOrContinue(
            InterviewSession session
    ) {
        InterviewReport report = interviewReportMapper
                .getBySessionId(session.getId());
        if (report == null) {
            return null;
        }

        if (report.getOverallScore() == null) {
            throw new IllegalStateException(
                    "已有Report缺少overallScore"
            );
        }
        int affectedRows = interviewSessionMapper.markReportReady(
                session.getId(),
                session.getVersion(),
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

    private void recoverReportFailure(Long sessionId, int attemptVersion, RuntimeException original) {
        try {
            // Zero rows means this task has lost ownership; no latest-version retry is allowed.
            interviewSessionMapper.markReportFailed(sessionId, attemptVersion);
        } catch (RuntimeException recovery) {
            original.addSuppressed(recovery);
        }
    }
}
