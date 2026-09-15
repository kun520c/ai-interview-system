package com.kun.aiinterview.interview.controller;

import com.kun.aiinterview.interview.dto.SubmitInterviewAnswerRequest;
import com.kun.aiinterview.interview.enums.DecisionAction;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationLevel;
import com.kun.aiinterview.interview.service.InterviewService;
import com.kun.aiinterview.interview.vo.InterviewEvaluationResponse;
import com.kun.aiinterview.interview.vo.InterviewQuestionResponse;
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
