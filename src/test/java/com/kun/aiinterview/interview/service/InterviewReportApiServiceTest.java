package com.kun.aiinterview.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.interview.entity.InterviewReport;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.mapper.AnswerEvaluationMapper;
import com.kun.aiinterview.interview.mapper.InterviewReportMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.interview.vo.InterviewReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewReportApiServiceTest {

    private static final long USER_ID = 101L;
    private static final long SESSION_ID = 201L;

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

    private InterviewReportService service;

    @BeforeEach
    void setUp() {
        service = new InterviewReportService(
                interviewSessionMapper,
                interviewReportMapper,
                answerEvaluationMapper,
                transactionService,
                generatorProvider,
                new ObjectMapper()
        );
    }

    @Test
    void shouldRejectMissingSessionForGet() {
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(null);

        assertThatThrownBy(() -> service.getReport(USER_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InterviewSession不存在");

        verifyNoInteractions(interviewReportMapper, generatorProvider);
    }

    @Test
    void shouldRejectAnotherUsersSessionForGet() {
        InterviewSession session = session(
                InterviewReportStatus.NOT_STARTED
        );
        session.setUserId(USER_ID + 1);
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(session);

        assertThatThrownBy(() -> service.getReport(USER_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("InterviewSession不属于当前用户");

        verifyNoInteractions(interviewReportMapper, generatorProvider);
    }

    @ParameterizedTest
    @EnumSource(
            value = InterviewReportStatus.class,
            names = {"NOT_STARTED", "GENERATING", "FAILED"}
    )
    void shouldReturnOnlyCurrentStatusWithoutSideEffects(
            InterviewReportStatus reportStatus
    ) {
        InterviewSession session = session(reportStatus);
        session.setStatus(InterviewSessionStatus.IN_PROGRESS);
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(session);

        InterviewReportResponse response = service.getReport(
                USER_ID,
                SESSION_ID
        );

        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        assertThat(response.reportStatus()).isEqualTo(reportStatus);
        assertThat(response.overallScore()).isNull();
        assertThat(response.result()).isNull();
        assertThat(response.summary()).isNull();
        assertThat(response.strengths()).isNull();
        assertThat(response.weaknesses()).isNull();
        assertThat(response.suggestions()).isNull();
        assertThat(session.getReportStatus()).isEqualTo(reportStatus);

        verifyNoInteractions(
                interviewReportMapper,
                answerEvaluationMapper,
                transactionService,
                generatorProvider
        );
        verify(interviewSessionMapper, never())
                .claimReportGeneration(any(), any(), any());
        verify(interviewSessionMapper, never())
                .markReportReady(any(), any(), any());
        verify(interviewSessionMapper, never())
                .markReportFailed(any(), any());
    }

    @Test
    void shouldReturnReadyReportWithJsonLists() {
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(session(InterviewReportStatus.READY));
        when(interviewReportMapper.getBySessionId(SESSION_ID))
                .thenReturn(report());

        InterviewReportResponse response = service.getReport(
                USER_ID,
                SESSION_ID
        );

        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        assertThat(response.reportStatus())
                .isEqualTo(InterviewReportStatus.READY);
        assertThat(response.overallScore())
                .isEqualByComparingTo("82.50");
        assertThat(response.result()).isEqualTo("GOOD");
        assertThat(response.summary()).isEqualTo("总体表现良好");
        assertThat(response.strengths())
                .containsExactly("Java基础扎实", "表达清晰");
        assertThat(response.weaknesses())
                .containsExactly("并发知识不足");
        assertThat(response.suggestions())
                .containsExactly("复习线程池");

        verifyNoInteractions(
                answerEvaluationMapper,
                transactionService,
                generatorProvider
        );
        verify(interviewSessionMapper, never())
                .claimReportGeneration(any(), any(), any());
    }

    @Test
    void shouldRejectReadyStatusWhenReportIsMissing() {
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(session(InterviewReportStatus.READY));
        when(interviewReportMapper.getBySessionId(SESSION_ID))
                .thenReturn(null);

        assertThatThrownBy(() -> service.getReport(USER_ID, SESSION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session为READY但Report不存在");
    }

    @Test
    void shouldRejectInvalidPersistedReportJson() {
        InterviewReport report = report();
        report.setWeaknesses("not-json");
        when(interviewSessionMapper.getInterviewSessionById(SESSION_ID))
                .thenReturn(session(InterviewReportStatus.READY));
        when(interviewReportMapper.getBySessionId(SESSION_ID))
                .thenReturn(report);

        assertThatThrownBy(() -> service.getReport(USER_ID, SESSION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("InterviewReport中的weaknesses JSON非法");
    }

    @Test
    void shouldBuildReadyApiResponseFromExistingGenerateReportMethod() {
        InterviewReportService spyService = spy(service);
        InterviewReport report = report();
        doReturn(report).when(spyService)
                .generateReport(USER_ID, SESSION_ID);

        InterviewReportResponse response = spyService
                .generateReportForApi(USER_ID, SESSION_ID);

        assertThat(response.reportStatus())
                .isEqualTo(InterviewReportStatus.READY);
        assertThat(response.strengths())
                .containsExactly("Java基础扎实", "表达清晰");
        verify(spyService).generateReport(USER_ID, SESSION_ID);
    }

    private InterviewSession session(
            InterviewReportStatus reportStatus
    ) {
        return InterviewSession.builder()
                .id(SESSION_ID)
                .userId(USER_ID)
                .status(InterviewSessionStatus.COMPLETED)
                .reportStatus(reportStatus)
                .version(3)
                .build();
    }

    private InterviewReport report() {
        return InterviewReport.builder()
                .id(301L)
                .sessionId(SESSION_ID)
                .overallScore(new BigDecimal("82.50"))
                .result("GOOD")
                .summary("总体表现良好")
                .strengths("[\"Java基础扎实\",\"表达清晰\"]")
                .weaknesses("[\"并发知识不足\"]")
                .suggestions("[\"复习线程池\"]")
                .llmModel("internal-model")
                .promptVersion("internal-prompt-version")
                .build();
    }
}
