package com.kun.aiinterview.interview.controller;

import com.kun.aiinterview.interview.dto.SubmitInterviewAnswerRequest;
import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.enums.InterviewAnswerStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationLevel;
import com.kun.aiinterview.interview.service.InterviewReportService;
import com.kun.aiinterview.interview.service.InterviewService;
import com.kun.aiinterview.interview.vo.InterviewDetailAnswerResponse;
import com.kun.aiinterview.interview.vo.InterviewDetailQuestionResponse;
import com.kun.aiinterview.interview.vo.InterviewDetailResponse;
import com.kun.aiinterview.interview.vo.InterviewEvaluationResponse;
import com.kun.aiinterview.interview.vo.InterviewHistoryItemResponse;
import com.kun.aiinterview.interview.vo.InterviewHistoryPageResponse;
import com.kun.aiinterview.interview.vo.InterviewQuestionResponse;
import com.kun.aiinterview.interview.vo.InterviewReportResponse;
import com.kun.aiinterview.interview.vo.InterviewSessionResponse;
import com.kun.aiinterview.interview.vo.SubmitInterviewAnswerResponse;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.security.config.SecurityConfiguration;
import com.kun.aiinterview.security.filter.JwtAuthenticationFilter;
import com.kun.aiinterview.security.handler.RestAccessDeniedHandler;
import com.kun.aiinterview.security.handler.RestAuthenticationEntryPoint;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InterviewController.class)
@Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class InterviewControllerTest {

    private static final long USER_ID = 1001L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterviewService interviewService;

    @MockitoBean
    private InterviewReportService interviewReportService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserMapper userMapper;

    @Test
    void shouldRejectUnauthenticatedCreate() throws Exception {
        mockMvc.perform(post("/api/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":\"MEDIUM\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(interviewService);
    }

    @Test
    void shouldRejectUnauthenticatedCurrentQuery() throws Exception {
        mockMvc.perform(get("/api/interviews/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(interviewService);
    }

    @Test
    void shouldRejectUnauthenticatedAnswerSubmission() throws Exception {
        mockMvc.perform(post("/api/interviews/21/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAnswerJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(interviewService);
    }

    @Test
    void shouldRejectUnauthenticatedReportQueries() throws Exception {
        mockMvc.perform(get("/api/interviews/21/report"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/interviews/21/report"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(interviewReportService);
    }

    @Test
    void shouldRejectUnauthenticatedHistoryAndDetailQueries() throws Exception {
        mockMvc.perform(get("/api/interviews/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/interviews/21"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(interviewService);
    }

    @Test
    void shouldCreateInterviewFromAuthenticatedUserWithoutSnapshotLeak()
            throws Exception {
        stubTokenUser("create-token");
        when(interviewService.createOrResume(
                USER_ID,
                QuestionDifficulty.MEDIUM
        )).thenReturn(sessionResponse());

        mockMvc.perform(post("/api/interviews")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer create-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":\"MEDIUM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.sessionId").value(21))
                .andExpect(jsonPath(
                        "$.data.currentQuestion.interviewQuestionId"
                ).value(31))
                .andExpect(jsonPath(
                        "$.data.currentQuestion.content"
                ).value("HashMap的底层结构是什么？"))
                .andExpect(jsonPath(
                        "$.data.currentQuestion.referenceAnswerSnapshot"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.data.currentQuestion.scoringPointsSnapshot"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.data.currentQuestion.followUpTargetPoints"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.data.currentQuestion.status"
                ).doesNotExist());
    }

    @Test
    void shouldReturnNullDataWhenThereIsNoCurrentInterview()
            throws Exception {
        stubTokenUser("current-token");
        when(interviewService.getCurrent(USER_ID)).thenReturn(null);

        mockMvc.perform(get("/api/interviews/current")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer current-token"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    @Test
    void shouldPassAuthenticatedUserAndPathSessionToSubmitService()
            throws Exception {
        stubTokenUser("answer-token");
        when(interviewService.submitAnswer(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(21L),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(submitResponse());
        ArgumentCaptor<SubmitInterviewAnswerRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        SubmitInterviewAnswerRequest.class
                );

        mockMvc.perform(post("/api/interviews/21/answers")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer answer-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAnswerJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath(
                        "$.data.evaluation.clarityScore"
                ).value(15))
                .andExpect(jsonPath(
                        "$.data.evaluation.claritySocre"
                ).doesNotExist())
                .andExpect(jsonPath("$.data.nextAction")
                        .value("NEXT_MAIN"))
                .andExpect(jsonPath(
                        "$.data.nextQuestion.interviewQuestionId"
                ).value(32));

        verify(interviewService).submitAnswer(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(21L),
                requestCaptor.capture()
        );
        assertThat(requestCaptor.getValue().interviewQuestionId())
                .isEqualTo(31L);
        assertThat(requestCaptor.getValue().answerContent())
                .isEqualTo("数组、链表和红黑树");
        assertThat(requestCaptor.getValue().requestId())
                .isEqualTo("request-31");
    }

    @Test
    void shouldValidateBlankAnswerBeforeCallingService() throws Exception {
        stubTokenUser("invalid-answer-token");

        mockMvc.perform(post("/api/interviews/21/answers")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer invalid-answer-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "interviewQuestionId": 31,
                                  "answerContent": "   ",
                                  "requestId": "request-31"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message")
                        .value("answerContent不能为空"));

        verifyNoInteractions(interviewService);
    }

    @Test
    void shouldGetReportUsingAuthenticatedUserWithoutInternalFieldLeak()
            throws Exception {
        stubTokenUser("get-report-token");
        when(interviewReportService.getReport(USER_ID, 21L))
                .thenReturn(reportResponse());

        mockMvc.perform(get("/api/interviews/21/report")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer get-report-token"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value(21))
                .andExpect(jsonPath("$.data.reportStatus")
                        .value("READY"))
                .andExpect(jsonPath("$.data.overallScore")
                        .value(82.50))
                .andExpect(jsonPath("$.data.strengths[0]")
                        .value("Java基础扎实"))
                .andExpect(jsonPath("$.data.weaknesses[0]")
                        .value("并发知识不足"))
                .andExpect(jsonPath("$.data.suggestions[0]")
                        .value("复习线程池"))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.llmModel").doesNotExist())
                .andExpect(jsonPath("$.data.promptVersion")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.data.version").doesNotExist())
                .andExpect(jsonPath("$.data.rawResult").doesNotExist())
                .andExpect(jsonPath("$.data.retrievalBatchId")
                        .doesNotExist());

        verify(interviewReportService).getReport(USER_ID, 21L);
    }

    @Test
    void shouldGenerateReportUsingJwtUserAndIgnoreForgedUserId()
            throws Exception {
        stubTokenUser("post-report-token");
        when(interviewReportService.generateReportForApi(USER_ID, 21L))
                .thenReturn(reportResponse());

        mockMvc.perform(post(
                        "/api/interviews/21/report?userId=999999"
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer post-report-token"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(21))
                .andExpect(jsonPath("$.data.reportStatus")
                        .value("READY"));

        verify(interviewReportService)
                .generateReportForApi(USER_ID, 21L);
    }

    @Test
    void shouldListHistoryFromAuthenticatedUserAndIgnoreForgedUserId()
            throws Exception {
        stubTokenUser("history-token");
        when(interviewService.listHistory(USER_ID, 2, 5))
                .thenReturn(historyPageResponse());

        mockMvc.perform(get("/api/interviews/history?userId=999999&page=2&pageSize=5")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer history-token"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(5))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items[0].sessionId").value(21))
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.items[0].reportStatus")
                        .value("READY"))
                .andExpect(jsonPath("$.data.items[0].totalScore").value(82.50))
                .andExpect(jsonPath("$.data.items[0].version").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].currentInterviewQuestionId")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.items[0].userId").doesNotExist());

        verify(interviewService).listHistory(USER_ID, 2, 5);
    }

    @Test
    void shouldPassDefaultHistoryPaginationWhenQueryIsOmitted()
            throws Exception {
        stubTokenUser("history-default-token");
        when(interviewService.listHistory(USER_ID, null, null))
                .thenReturn(emptyHistoryPageResponse());

        mockMvc.perform(get("/api/interviews/history")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer history-default-token"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0));

        verify(interviewService).listHistory(USER_ID, null, null);
    }

    @Test
    void shouldGetDetailFromAuthenticatedUserWithoutInternalFieldLeak()
            throws Exception {
        stubTokenUser("detail-token");
        when(interviewService.getDetail(USER_ID, 21L))
                .thenReturn(detailResponse());

        mockMvc.perform(get("/api/interviews/21?userId=999999")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer detail-token"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value(21))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.questions[0].interviewQuestionId")
                        .value(31))
                .andExpect(jsonPath("$.data.questions[0].questionContent")
                        .value("HashMap的底层结构是什么？"))
                .andExpect(jsonPath("$.data.questions[0].answer.answerContent")
                        .value("数组、链表和红黑树"))
                .andExpect(jsonPath("$.data.questions[0].evaluation.evaluationPhase")
                        .value("INITIAL"))
                .andExpect(jsonPath("$.data.questions[0].evaluation.strengths[0]")
                        .value("概念准确"))
                .andExpect(jsonPath("$.data.questions[0].evaluation.missingPoints[0]")
                        .value("缺少扩容细节"))
                .andExpect(jsonPath("$.data.questions[1].questionType")
                        .value("FOLLOW_UP"))
                .andExpect(jsonPath("$.data.questions[1].evaluation.evaluationPhase")
                        .value("FINAL"))
                .andExpect(jsonPath("$.data.questions[0].referenceAnswerSnapshot")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].scoringPointsSnapshot")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].followUpTargetPoints")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].parentQuestionId")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].answer.requestId")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].answer.errorCode")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].evaluation.rawResult")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].evaluation.retrievalBatchId")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].evaluation.llmModel")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].evaluation.promptVersion")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].evaluation.evaluationStandardVersion")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].evaluation.scoringPointResults")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].evaluation.followUpRecommended")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].evaluation.suggestedFollowUp")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].evaluation.decisionAction")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.version").doesNotExist())
                .andExpect(jsonPath("$.data.currentInterviewQuestionId")
                        .doesNotExist());

        verify(interviewService).getDetail(USER_ID, 21L);
    }

    private void stubTokenUser(String token) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(Long.toString(USER_ID));
        when(jwtTokenService.parseAndValidate(token)).thenReturn(claims);
        when(userMapper.getUserById(USER_ID)).thenReturn(
                User.builder()
                        .id(USER_ID)
                        .account("interview-user")
                        .username("面试用户")
                        .role(UserRole.USER)
                        .status(UserStatus.ENABLED)
                        .build()
        );
    }

    private InterviewSessionResponse sessionResponse() {
        return new InterviewSessionResponse(
                21L,
                QuestionDifficulty.MEDIUM,
                InterviewSessionStatus.IN_PROGRESS,
                3,
                0,
                new InterviewQuestionResponse(
                        31L,
                        QuestionCategory.JAVA_COLLECTION,
                        "HashMap",
                        InterviewQuestionType.MAIN,
                        "HashMap的底层结构是什么？"
                )
        );
    }

    private SubmitInterviewAnswerResponse submitResponse() {
        return new SubmitInterviewAnswerResponse(
                new InterviewEvaluationResponse(
                        EvaluationPhase.FINAL,
                        18,
                        17,
                        16,
                        15,
                        14,
                        80,
                        EvaluationLevel.GOOD,
                        List.of("概念准确"),
                        List.of("缺少扩容细节"),
                        "补充扩容过程"
                ),
                DecisionAction.NEXT_MAIN,
                InterviewSessionStatus.IN_PROGRESS,
                new InterviewQuestionResponse(
                        32L,
                        QuestionCategory.JVM,
                        "类加载",
                        InterviewQuestionType.MAIN,
                        "类加载过程是什么？"
                )
        );
    }

    private InterviewReportResponse reportResponse() {
        return new InterviewReportResponse(
                21L,
                InterviewReportStatus.READY,
                new BigDecimal("82.50"),
                "GOOD",
                "总体表现良好",
                List.of("Java基础扎实"),
                List.of("并发知识不足"),
                List.of("复习线程池")
        );
    }

    private InterviewHistoryPageResponse historyPageResponse() {
        return new InterviewHistoryPageResponse(
                2,
                5,
                1,
                1,
                List.of(new InterviewHistoryItemResponse(
                        21L,
                        QuestionDifficulty.MEDIUM,
                        InterviewSessionStatus.COMPLETED,
                        InterviewReportStatus.READY,
                        new BigDecimal("82.50"),
                        5,
                        5,
                        LocalDateTime.of(2026, 9, 18, 10, 0),
                        LocalDateTime.of(2026, 9, 18, 11, 0),
                        LocalDateTime.of(2026, 9, 18, 9, 50)
                ))
        );
    }

    private InterviewHistoryPageResponse emptyHistoryPageResponse() {
        return new InterviewHistoryPageResponse(
                1,
                10,
                0,
                0,
                List.of()
        );
    }

    private InterviewDetailResponse detailResponse() {
        return new InterviewDetailResponse(
                21L,
                QuestionDifficulty.MEDIUM,
                InterviewSessionStatus.COMPLETED,
                InterviewReportStatus.READY,
                new BigDecimal("82.50"),
                5,
                5,
                LocalDateTime.of(2026, 9, 18, 10, 0),
                LocalDateTime.of(2026, 9, 18, 11, 0),
                LocalDateTime.of(2026, 9, 18, 9, 50),
                List.of(
                        new InterviewDetailQuestionResponse(
                                31L,
                                InterviewQuestionType.MAIN,
                                QuestionCategory.JAVA_COLLECTION,
                                "HashMap",
                                "HashMap的底层结构是什么？",
                                1,
                                1,
                                InterviewQuestionStatus.ANSWERED,
                                new InterviewDetailAnswerResponse(
                                        "数组、链表和红黑树",
                                        InterviewAnswerStatus.EVALUATED,
                                        LocalDateTime.of(2026, 9, 18, 10, 20)
                                ),
                                new InterviewEvaluationResponse(
                                        EvaluationPhase.INITIAL,
                                        18,
                                        17,
                                        16,
                                        15,
                                        14,
                                        80,
                                        EvaluationLevel.GOOD,
                                        List.of("概念准确"),
                                        List.of("缺少扩容细节"),
                                        "补充扩容过程"
                                )
                        ),
                        new InterviewDetailQuestionResponse(
                                32L,
                                InterviewQuestionType.FOLLOW_UP,
                                QuestionCategory.JAVA_COLLECTION,
                                "HashMap 扩容",
                                "请说明扩容过程",
                                null,
                                2,
                                InterviewQuestionStatus.ANSWERED,
                                new InterviewDetailAnswerResponse(
                                        "超过阈值后扩容",
                                        InterviewAnswerStatus.EVALUATED,
                                        LocalDateTime.of(2026, 9, 18, 10, 40)
                                ),
                                new InterviewEvaluationResponse(
                                        EvaluationPhase.FINAL,
                                        18,
                                        17,
                                        16,
                                        15,
                                        14,
                                        80,
                                        EvaluationLevel.GOOD,
                                        List.of("补充完整"),
                                        List.of(),
                                        "表现更好"
                                )
                        )
                )
        );
    }

    private String validAnswerJson() {
        return """
                {
                  "interviewQuestionId": 31,
                  "answerContent": "数组、链表和红黑树",
                  "requestId": "request-31"
                }
                """;
    }
}
