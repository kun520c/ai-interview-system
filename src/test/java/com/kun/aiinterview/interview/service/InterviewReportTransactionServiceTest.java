package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.entity.InterviewReport;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.mapper.InterviewReportMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.user.service.UserWeaknessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewReportTransactionServiceTest {

    private static final long USER_ID = 101L;
    private static final long SESSION_ID = 201L;
    private static final int SESSION_VERSION = 4;

    @Mock
    private InterviewReportMapper interviewReportMapper;

    @Mock
    private InterviewSessionMapper interviewSessionMapper;

    @Mock
    private UserWeaknessService userWeaknessService;

    private InterviewReportTransactionService service;

    @BeforeEach
    void setUp() {
        service = new InterviewReportTransactionService(
                interviewReportMapper,
                interviewSessionMapper,
                userWeaknessService
        );
    }

    @Test
    void shouldInsertReportThenApplyWeaknessThenMarkSessionReady() {
        InterviewReport report = report();
        InterviewSession session = session();
        List<AnswerEvaluation> evaluations = evaluations();
        when(interviewReportMapper.insertReport(same(report)))
                .thenAnswer(invocation -> {
                    report.setId(301L);
                    return 1;
                });
        when(interviewSessionMapper.markReportReady(
                SESSION_ID,
                SESSION_VERSION,
                report.getOverallScore()
        )).thenReturn(1);

        InterviewReport result = service.completeReportGeneration(
                report,
                session,
                evaluations
        );

        assertThat(result).isSameAs(report);
        assertThat(result.getId()).isEqualTo(301L);
        InOrder order = inOrder(
                interviewReportMapper,
                userWeaknessService,
                interviewSessionMapper
        );
        order.verify(interviewReportMapper).insertReport(report);
        order.verify(userWeaknessService).applySessionEvaluations(
                USER_ID,
                evaluations
        );
        order.verify(interviewSessionMapper).markReportReady(
                SESSION_ID,
                SESSION_VERSION,
                report.getOverallScore()
        );
    }

    @Test
    void shouldStopBeforeWeaknessAndReadyWhenReportInsertFails() {
        InterviewReport report = report();
        when(interviewReportMapper.insertReport(report))
                .thenReturn(0);

        assertThatThrownBy(
                () -> service.completeReportGeneration(
                        report,
                        session(),
                        evaluations()
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("InterviewReport写入失败");

        verifyNoInteractions(userWeaknessService);
        verify(interviewSessionMapper, never())
                .markReportReady(
                        SESSION_ID,
                        SESSION_VERSION,
                        report.getOverallScore()
                );
    }

    @Test
    void shouldStopBeforeWeaknessAndReadyWhenGeneratedKeyIsMissing() {
        InterviewReport report = report();
        when(interviewReportMapper.insertReport(report))
                .thenReturn(1);

        assertThatThrownBy(
                () -> service.completeReportGeneration(
                        report,
                        session(),
                        evaluations()
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("InterviewReport主键未回填");

        verifyNoInteractions(userWeaknessService);
        verify(interviewSessionMapper, never())
                .markReportReady(
                        SESSION_ID,
                        SESSION_VERSION,
                        report.getOverallScore()
                );
    }

    @Test
    void shouldStopBeforeReadyWhenWeaknessUpdateFails() {
        InterviewReport report = report();
        InterviewSession session = session();
        List<AnswerEvaluation> evaluations = evaluations();
        when(interviewReportMapper.insertReport(report))
                .thenAnswer(invocation -> {
                    report.setId(301L);
                    return 1;
                });
        doThrow(new IllegalStateException("Evaluation缺少category"))
                .when(userWeaknessService)
                .applySessionEvaluations(USER_ID, evaluations);

        assertThatThrownBy(
                () -> service.completeReportGeneration(
                        report,
                        session,
                        evaluations
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Evaluation缺少category");

        verify(userWeaknessService).applySessionEvaluations(
                USER_ID,
                evaluations
        );
        verify(interviewSessionMapper, never())
                .markReportReady(
                        SESSION_ID,
                        SESSION_VERSION,
                        report.getOverallScore()
                );
    }

    @Test
    void shouldThrowWhenMarkReadyAffectsNoRow() {
        InterviewReport report = report();
        List<AnswerEvaluation> evaluations = evaluations();
        when(interviewReportMapper.insertReport(report))
                .thenAnswer(invocation -> {
                    report.setId(301L);
                    return 1;
                });
        when(interviewSessionMapper.markReportReady(
                SESSION_ID,
                SESSION_VERSION,
                report.getOverallScore()
        )).thenReturn(0);

        assertThatThrownBy(
                () -> service.completeReportGeneration(
                        report,
                        session(),
                        evaluations
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session报告状态更新为READY失败");

        verify(interviewReportMapper).insertReport(report);
        verify(userWeaknessService).applySessionEvaluations(
                USER_ID,
                evaluations
        );
        verify(interviewSessionMapper).markReportReady(
                SESSION_ID,
                SESSION_VERSION,
                report.getOverallScore()
        );
    }

    @Test
    void completeMethodDeclaresTransactionalBoundary()
            throws NoSuchMethodException {
        Method method = InterviewReportTransactionService.class
                .getMethod(
                        "completeReportGeneration",
                        InterviewReport.class,
                        InterviewSession.class,
                        List.class
                );

        assertThat(method.getAnnotation(Transactional.class))
                .isNotNull();
    }

    private InterviewReport report() {
        return InterviewReport.builder()
                .sessionId(SESSION_ID)
                .overallScore(new BigDecimal("80.33"))
                .result("PASS")
                .summary("总体表现良好")
                .strengths("[]")
                .weaknesses("[]")
                .suggestions("[]")
                .llmModel("report-test-model")
                .promptVersion("report-v1")
                .build();
    }

    private InterviewSession session() {
        return InterviewSession.builder()
                .id(SESSION_ID)
                .userId(USER_ID)
                .status(InterviewSessionStatus.COMPLETED)
                .reportStatus(InterviewReportStatus.GENERATING)
                .version(SESSION_VERSION)
                .build();
    }

    private List<AnswerEvaluation> evaluations() {
        return List.of(
                AnswerEvaluation.builder()
                        .category(QuestionCategory.JAVA_COLLECTION)
                        .knowledgePoint("HashMap")
                        .totalScore(60)
                        .build()
        );
    }
}
