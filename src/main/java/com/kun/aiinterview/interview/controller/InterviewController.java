package com.kun.aiinterview.interview.controller;

import com.kun.aiinterview.common.response.Result;
import com.kun.aiinterview.interview.dto.CreateInterviewRequest;
import com.kun.aiinterview.interview.dto.SubmitInterviewAnswerRequest;
import com.kun.aiinterview.interview.service.InterviewReportService;
import com.kun.aiinterview.interview.service.InterviewService;
import com.kun.aiinterview.interview.vo.InterviewDetailResponse;
import com.kun.aiinterview.interview.vo.InterviewHistoryPageResponse;
import com.kun.aiinterview.interview.vo.InterviewReportResponse;
import com.kun.aiinterview.interview.vo.InterviewSessionResponse;
import com.kun.aiinterview.interview.vo.SubmitInterviewAnswerResponse;
import com.kun.aiinterview.security.model.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final InterviewReportService interviewReportService;

    @PostMapping
    public Result<InterviewSessionResponse> createInterview(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateInterviewRequest request
    ) {
        InterviewSessionResponse response =
                interviewService.createOrResume(
                        authenticatedUser.userId(),
                        request.difficulty()
                );

        return Result.success(response);
    }

    @GetMapping("/current")
    public Result<InterviewSessionResponse> getCurrentInterview(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        InterviewSessionResponse response =
                interviewService.getCurrent(
                        authenticatedUser.userId()
                );

        return Result.success(response);
    }

    @GetMapping("/history")
    public Result<InterviewHistoryPageResponse> listHistory(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        InterviewHistoryPageResponse response =
                interviewService.listHistory(
                        authenticatedUser.userId(),
                        page,
                        pageSize
                );

        return Result.success(response);
    }

    @GetMapping("/{sessionId}")
    public Result<InterviewDetailResponse> getDetail(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long sessionId
    ) {
        InterviewDetailResponse response =
                interviewService.getDetail(
                        authenticatedUser.userId(),
                        sessionId
                );

        return Result.success(response);
    }

    @PostMapping("/{sessionId}/answers")
    public Result<SubmitInterviewAnswerResponse> submitAnswer(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long sessionId,
            @Valid @RequestBody SubmitInterviewAnswerRequest request
    ) {
        SubmitInterviewAnswerResponse response =
                interviewService.submitAnswer(
                        authenticatedUser.userId(),
                        sessionId,
                        request
                );

        return Result.success(response);
    }

    @GetMapping("/{sessionId}/report")
    public Result<InterviewReportResponse> getReport(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long sessionId
    ) {
        InterviewReportResponse response = interviewReportService
                .getReport(
                        authenticatedUser.userId(),
                        sessionId
                );

        return Result.success(response);
    }

    @PostMapping("/{sessionId}/report")
    public Result<InterviewReportResponse> generateReport(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long sessionId
    ) {
        InterviewReportResponse response = interviewReportService
                .generateReportForApi(
                        authenticatedUser.userId(),
                        sessionId
                );

        return Result.success(response);
    }
}
