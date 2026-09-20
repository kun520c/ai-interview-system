package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewReportServiceTest {

    private static final long USER_ID = 101L;
    private static final long SESSION_ID = 201L;
    private static final int INITIAL_VERSION = 3;
    private static final int GENERATING_VERSION = 4;

    @Mock
    private InterviewSessionMapper interviewSessionMapper;

    @Mock
    private InterviewReportMapper interviewReportMapper;

    @Mock
    private AnswerEvaluationMapper answerEvaluationMapper;

    @Mock
    private InterviewReportTransactionService transactionService;

    @Mock
    private ObjectProvider<InterviewReportGenerator> generatorProvider;

    @Mock
    private InterviewReportGenerator generator;

    private InterviewReportService service;

    @BeforeEach
    void setUp() {
        service = serviceWith(new ObjectMapper());
    }

    @Test
    void shouldRejectNullIdentifiersBeforeMapperCalls() {
        assertThatThrownBy(
                () -> service.generateReport(null, SESSION_ID)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId和sessionId不能为空");
        assertThatThrownBy(
                () -> service.generateReport(USER_ID, null)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId和sessionId不能为空");

        verifyNoInteractions(
                interviewSessionMapper,
                interviewReportMapper,
                answerEvaluationMapper,
                transactionService,
                generatorProvider,
                generator
        );
    }

    @Test
    void shouldRejectMissingSession() {
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(null);

        assertThatThrownBy(
                () -> service.generateReport(USER_ID, SESSION_ID)
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("面试会话不存在");

        verifyNoInteractions(
                interviewReportMapper,
                answerEvaluationMapper,
                transactionService,
                generatorProvider,
                generator
        );
    }

    @Test
    void shouldRejectSessionOwnedByAnotherUser() {
        InterviewSession session = initialSession(
                InterviewReportStatus.NOT_STARTED
        );
        session.setUserId(USER_ID + 1);
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(session);

        assertThatThrownBy(
                () -> service.generateReport(USER_ID, SESSION_ID)
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("面试会话不存在");

        verifyNoInteractions(
                generatorProvider,
                generator,
                answerEvaluationMapper,
                transactionService
        );
    }

    @Test
    void shouldRejectSessionThatIsNotCompleted() {
        InterviewSession session = initialSession(
                InterviewReportStatus.NOT_STARTED
        );
        session.setStatus(InterviewSessionStatus.IN_PROGRESS);
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(session);

        assertThatThrownBy(
                () -> service.generateReport(USER_ID, SESSION_ID)
        )
                .isInstanceOf(ConflictException.class)
                .hasMessage("面试尚未完成，无法生成报告");

        verifyNoInteractions(
                generatorProvider,
                generator,
                answerEvaluationMapper,
                transactionService
        );
    }

    @Test
    void shouldReturnExistingReportWhenSessionIsReady() {
        InterviewSession session = initialSession(
                InterviewReportStatus.READY
        );
        InterviewReport report = report();
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(session);
        when(interviewReportMapper.getBySessionId(SESSION_ID))
                .thenReturn(report);

        InterviewReport result = service.generateReport(
                USER_ID,
                SESSION_ID
        );

        assertThat(result).isSameAs(report);
        verifyNoInteractions(
                generatorProvider,
                generator,
                answerEvaluationMapper,
                transactionService
        );
        verify(interviewSessionMapper, never())
                .claimReportGeneration(any(), any(), any());
    }

    @Test
    void shouldRejectReadySessionWhenReportIsMissing() {
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(initialSession(InterviewReportStatus.READY));
        when(interviewReportMapper.getBySessionId(SESSION_ID))
                .thenReturn(null);

        assertThatThrownBy(
                () -> service.generateReport(USER_ID, SESSION_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session为READY但Report不存在");

        verifyNoInteractions(generatorProvider, generator);
    }

    @Test
    void shouldRejectDuplicateGenerationWhenAlreadyGenerating() {
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(initialSession(
                        InterviewReportStatus.GENERATING
                ));

        assertThatThrownBy(
                () -> service.generateReport(USER_ID, SESSION_ID)
        )
                .isInstanceOf(ConflictException.class)
                .hasMessage("报告正在生成中，请稍后重试");

        verifyNoInteractions(generatorProvider, generator);
        verify(interviewSessionMapper, never())
                .claimReportGeneration(any(), any(), any());
        verify(interviewSessionMapper, never())
                .reclaimStaleGenerating(any(), any());
    }

    @Test
    void shouldRetryStaleGeneratingWhenReportDoesNotExist() {
        InterviewSession stale = generatingSession();
        stale.setUpdatedAt(LocalDateTime.now().minusMinutes(11));
        stale.setVersion(INITIAL_VERSION);
        InterviewSession reclaimed = generatingSession();
        List<AnswerEvaluation> evaluations = evaluations(70, 80, 90);
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(stale, reclaimed, reclaimed);
        when(interviewSessionMapper.reclaimStaleGenerating(eq(SESSION_ID), any()))
                .thenReturn(1);
        when(interviewReportMapper.getBySessionId(SESSION_ID)).thenReturn(null);
        when(generatorProvider.getIfAvailable()).thenReturn(generator);
        when(answerEvaluationMapper.listFinalEffectiveEvaluationsBySessionId(
                SESSION_ID
        )).thenReturn(evaluations);
        when(generator.generate(any(), any(), any()))
                .thenReturn(generationResult());
        when(transactionService.completeReportGeneration(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InterviewReport result = service.generateReport(USER_ID, SESSION_ID);

        assertThat(result.getOverallScore()).isEqualByComparingTo("80.00");
        verify(interviewSessionMapper).reclaimStaleGenerating(
                eq(SESSION_ID),
                any()
        );
        verify(interviewSessionMapper, never())
                .claimReportGeneration(any(), any(), any());
        verify(generator).generate(any(), any(), any());
        verify(transactionService).completeReportGeneration(any(), any(), any());
    }

    @Test
    void shouldReconcileStaleGeneratingWhenReportAlreadyExistsWithoutCallingGenerator() {
        InterviewSession stale = generatingSession();
        stale.setUpdatedAt(LocalDateTime.now().minusMinutes(11));
        InterviewSession reclaimed = generatingSession();
        InterviewReport existing = report();
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(stale, reclaimed);
        when(interviewSessionMapper.reclaimStaleGenerating(eq(SESSION_ID), any()))
                .thenReturn(1);
        when(interviewReportMapper.getBySessionId(SESSION_ID)).thenReturn(existing);
        when(interviewSessionMapper.markReportReady(
                SESSION_ID,
                GENERATING_VERSION,
                existing.getOverallScore()
        )).thenReturn(1);

        InterviewReport result = service.generateReport(USER_ID, SESSION_ID);

        assertThat(result).isSameAs(existing);
        verify(interviewSessionMapper).markReportReady(
                SESSION_ID,
                GENERATING_VERSION,
                existing.getOverallScore()
        );
        verifyNoInteractions(generatorProvider, generator, transactionService);
        verify(interviewSessionMapper, never())
                .claimReportGeneration(any(), any(), any());
        verify(answerEvaluationMapper, never())
                .listFinalEffectiveEvaluationsBySessionId(any());
    }

    @Test
    void shouldGenerateReportFromNotStartedAndRoundAverageHalfUp() {
        List<AnswerEvaluation> evaluations = evaluations(
                80,
                80,
                81
        );
        stubClaimedFlow(
                InterviewReportStatus.NOT_STARTED,
                evaluations
        );
        when(generator.generate(
                any(),
                any(),
                any()
        )).thenReturn(generationResult());
        when(transactionService.completeReportGeneration(
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> invocation.getArgument(0));

        InterviewReport result = service.generateReport(
                USER_ID,
                SESSION_ID
        );

        assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(result.getOverallScore())
                .isEqualByComparingTo("80.33");
        assertThat(result.getStrengths())
                .isEqualTo("[\"Java基础扎实\"]");
        assertThat(result.getWeaknesses())
                .isEqualTo("[\"并发知识不足\"]");
        assertThat(result.getSuggestions())
                .isEqualTo("[\"复习线程池\"]");
        assertThat(result.getPromptVersion())
                .isEqualTo("report-v1");

        InOrder order = inOrder(
                interviewSessionMapper,
                answerEvaluationMapper,
                generator,
                transactionService
        );
        order.verify(interviewSessionMapper)
                .claimReportGeneration(
                        SESSION_ID,
                        INITIAL_VERSION,
                        InterviewReportStatus.NOT_STARTED
                );
        order.verify(interviewSessionMapper)
                .getInterviewSessionById(SESSION_ID);
        order.verify(answerEvaluationMapper)
                .listFinalEffectiveEvaluationsBySessionId(SESSION_ID);
        order.verify(generator).generate(
                generatingSession(),
                evaluations,
                new BigDecimal("80.33")
        );
        order.verify(transactionService)
                .completeReportGeneration(
                        result,
                        generatingSession(),
                        evaluations
                );
        verify(interviewSessionMapper, never())
                .markReportFailed(any(), any());
    }

    @Test
    void shouldRetryGenerationFromFailed() {
        List<AnswerEvaluation> evaluations = evaluations(70, 80, 90);
        stubClaimedFlow(
                InterviewReportStatus.FAILED,
                evaluations
        );
        when(generator.generate(any(), any(), any()))
                .thenReturn(generationResult());
        when(transactionService.completeReportGeneration(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InterviewReport result = service.generateReport(
                USER_ID,
                SESSION_ID
        );

        assertThat(result.getOverallScore())
                .isEqualByComparingTo("80.00");
        verify(interviewSessionMapper)
                .claimReportGeneration(
                        SESSION_ID,
                        INITIAL_VERSION,
                        InterviewReportStatus.FAILED
                );
        verify(generator).generate(
                generatingSession(),
                evaluations,
                new BigDecimal("80.00")
        );
    }

    @Test
    void shouldStopWhenClaimAffectsNoRow() {
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(initialSession(
                        InterviewReportStatus.NOT_STARTED
                ));
        when(generatorProvider.getIfAvailable()).thenReturn(generator);
        when(interviewSessionMapper.claimReportGeneration(
                SESSION_ID,
                INITIAL_VERSION,
                InterviewReportStatus.NOT_STARTED
        )).thenReturn(0);

        assertThatThrownBy(
                () -> service.generateReport(USER_ID, SESSION_ID)
        )
                .isInstanceOf(ConflictException.class)
                .hasMessage("报告生成状态已发生变化，请重试");

        verifyNoInteractions(generator);
        verifyNoInteractions(answerEvaluationMapper, transactionService);
    }

    @Test
    void shouldMarkFailedWhenEvaluationCountIsIncomplete() {
        List<AnswerEvaluation> incomplete = evaluations(80, 90);
        stubClaimedFlow(
                InterviewReportStatus.NOT_STARTED,
                incomplete
        );
        when(interviewSessionMapper.markReportFailed(
                SESSION_ID,
                GENERATING_VERSION
        )).thenReturn(1);

        assertThatThrownBy(
                () -> service.generateReport(USER_ID, SESSION_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("有效Evaluation数量与计划的MAIN题数不一致");

        verify(interviewSessionMapper).markReportFailed(
                SESSION_ID,
                GENERATING_VERSION
        );
        verifyNoInteractions(generator, transactionService);
    }

    @Test
    void shouldMarkFailedAndRethrowOriginalGeneratorException() {
        RuntimeException original = new RuntimeException(
                "generator failed"
        );
        stubClaimedFlow(
                InterviewReportStatus.NOT_STARTED,
                evaluations(70, 80, 90)
        );
        when(generator.generate(any(), any(), any()))
                .thenThrow(original);
        when(interviewSessionMapper.markReportFailed(
                SESSION_ID,
                GENERATING_VERSION
        )).thenReturn(1);

        assertThatThrownBy(
                () -> service.generateReport(USER_ID, SESSION_ID)
        ).isSameAs(original);

        verify(interviewSessionMapper).markReportFailed(
                SESSION_ID,
                GENERATING_VERSION
        );
        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldMarkFailedWhenReportJsonSerializationFails()
            throws JsonProcessingException {
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
        when(failingObjectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException(
                        "test serialization failure"
                ) {
                });
        service = serviceWith(failingObjectMapper);
        stubClaimedFlow(
                InterviewReportStatus.NOT_STARTED,
                evaluations(70, 80, 90)
        );
        when(generator.generate(any(), any(), any()))
                .thenReturn(generationResult());
        when(interviewSessionMapper.markReportFailed(
                SESSION_ID,
                GENERATING_VERSION
        )).thenReturn(1);

        assertThatThrownBy(
                () -> service.generateReport(USER_ID, SESSION_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Report strengths JSON序列化失败")
                .hasCauseInstanceOf(JsonProcessingException.class);

        verify(interviewSessionMapper).markReportFailed(
                SESSION_ID,
                GENERATING_VERSION
        );
        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldMarkFailedWhenCompletionTransactionFails() {
        RuntimeException original = new IllegalStateException(
                "InterviewReport写入失败"
        );
        stubClaimedFlow(
                InterviewReportStatus.NOT_STARTED,
                evaluations(70, 80, 90)
        );
        when(generator.generate(any(), any(), any()))
                .thenReturn(generationResult());
        when(transactionService.completeReportGeneration(any(), any(), any()))
                .thenThrow(original);
        when(interviewSessionMapper.markReportFailed(
                SESSION_ID,
                GENERATING_VERSION
        )).thenReturn(1);

        assertThatThrownBy(
                () -> service.generateReport(USER_ID, SESSION_ID)
        ).isSameAs(original);

        verify(interviewSessionMapper).markReportFailed(
                SESSION_ID,
                GENERATING_VERSION
        );
    }

    @Test
    void shouldNotReplaceOriginalWhenFailedRecoveryAlsoThrows() {
        RuntimeException original = new RuntimeException(
                "generator failed"
        );
        RuntimeException recovery = new RuntimeException(
                "failed recovery"
        );
        stubClaimedFlow(
                InterviewReportStatus.NOT_STARTED,
                evaluations(70, 80, 90)
        );
        when(generator.generate(any(), any(), any()))
                .thenThrow(original);
        when(interviewSessionMapper.markReportFailed(
                SESSION_ID,
                GENERATING_VERSION
        )).thenThrow(recovery);

        assertThatThrownBy(
                () -> service.generateReport(USER_ID, SESSION_ID)
        )
                .isSameAs(original)
                .satisfies(exception -> assertThat(
                        exception.getSuppressed()
                ).containsExactly(recovery));
    }

    @Test
    void shouldNotMarkFailedWhenLatestSessionIsAlreadyReady() {
        RuntimeException commitSignal = new RuntimeException(
                "completion returned an error after commit"
        );
        InterviewSession ready = initialSession(
                InterviewReportStatus.READY
        );
        ready.setVersion(GENERATING_VERSION + 1);
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(
                        initialSession(
                                InterviewReportStatus.NOT_STARTED
                        ),
                        initialSession(
                                InterviewReportStatus.NOT_STARTED
                        ),
                        generatingSession(),
                        ready
                );
        when(generatorProvider.getIfAvailable()).thenReturn(generator);
        when(interviewSessionMapper.claimReportGeneration(
                SESSION_ID,
                INITIAL_VERSION,
                InterviewReportStatus.NOT_STARTED
        )).thenReturn(1);
        List<AnswerEvaluation> evaluations = evaluations(70, 80, 90);
        when(answerEvaluationMapper
                .listFinalEffectiveEvaluationsBySessionId(SESSION_ID))
                .thenReturn(evaluations);
        when(generator.generate(any(), any(), any()))
                .thenReturn(generationResult());
        when(transactionService.completeReportGeneration(any(), any(), any()))
                .thenThrow(commitSignal);

        assertThatThrownBy(
                () -> service.generateReport(USER_ID, SESSION_ID)
        ).isSameAs(commitSignal);

        verify(interviewSessionMapper, never())
                .markReportFailed(any(), any());
    }

    @Test
    void shouldRejectBeforeClaimWhenGeneratorBeanIsMissing() {
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(initialSession(
                        InterviewReportStatus.NOT_STARTED
                ));
        when(generatorProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(
                () -> service.generateReport(USER_ID, SESSION_ID)
        )
                .isInstanceOf(BusinessException.class)
                .hasMessage("报告生成服务当前不可用");

        verify(interviewSessionMapper, never())
                .claimReportGeneration(any(), any(), any());
        verifyNoInteractions(
                generator,
                answerEvaluationMapper,
                transactionService
        );
    }

    private void stubClaimedFlow(
            InterviewReportStatus initialStatus,
            List<AnswerEvaluation> evaluations
    ) {
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(
                        initialSession(initialStatus),
                        initialSession(initialStatus),
                        generatingSession()
                );
        when(generatorProvider.getIfAvailable()).thenReturn(generator);
        when(interviewSessionMapper.claimReportGeneration(
                SESSION_ID,
                INITIAL_VERSION,
                initialStatus
        )).thenReturn(1);
        when(answerEvaluationMapper
                .listFinalEffectiveEvaluationsBySessionId(SESSION_ID))
                .thenReturn(evaluations);
    }

    private InterviewReportService serviceWith(
            ObjectMapper objectMapper
    ) {
        return new InterviewReportService(
                interviewSessionMapper,
                interviewReportMapper,
                answerEvaluationMapper,
                transactionService,
                generatorProvider,
                objectMapper,
                new StaleRecoveryProperties()
        );
    }

    private InterviewSession initialSession(
            InterviewReportStatus reportStatus
    ) {
        return InterviewSession.builder()
                .id(SESSION_ID)
                .userId(USER_ID)
                .status(InterviewSessionStatus.COMPLETED)
                .plannedQuestionCount(3)
                .completedQuestionCount(3)
                .reportStatus(reportStatus)
                .version(INITIAL_VERSION)
                .build();
    }

    private InterviewSession generatingSession() {
        InterviewSession session = initialSession(
                InterviewReportStatus.GENERATING
        );
        session.setVersion(GENERATING_VERSION);
        return session;
    }

    private List<AnswerEvaluation> evaluations(Integer... scores) {
        return java.util.Arrays.stream(scores)
                .map(score -> AnswerEvaluation.builder()
                        .totalScore(score)
                        .build())
                .toList();
    }

    private InterviewReportGenerationResult generationResult() {
        return new InterviewReportGenerationResult(
                "PASS",
                "总体表现良好",
                List.of("Java基础扎实"),
                List.of("并发知识不足"),
                List.of("复习线程池"),
                "report-test-model",
                "report-v1"
        );
    }

    private InterviewReport report() {
        return InterviewReport.builder()
                .id(301L)
                .sessionId(SESSION_ID)
                .overallScore(new BigDecimal("80.00"))
                .result("PASS")
                .summary("总体表现良好")
                .strengths("[]")
                .weaknesses("[]")
                .suggestions("[]")
                .llmModel("report-test-model")
                .promptVersion("report-v1")
                .build();
    }
}
