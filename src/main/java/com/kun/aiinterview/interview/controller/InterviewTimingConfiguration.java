package com.kun.aiinterview.interview.controller;

import com.kun.aiinterview.common.observability.InterviewRequestTiming;
import com.kun.aiinterview.common.observability.InterviewRequestTiming.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class InterviewTimingConfiguration implements WebMvcConfigurer {

    static final String TIMING_HEADER = "X-Interview-Timing-Id";
    private static final String ANSWER_PATH = "/api/interviews/{sessionId}/answers";
    private static final String REPORT_PATH = "/api/interviews/{sessionId}/report";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TimingInterceptor()).addPathPatterns(ANSWER_PATH, REPORT_PATH);
    }

    private static final class TimingInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (!"POST".equals(request.getMethod())) {
                return true;
            }
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (ANSWER_PATH.equals(pattern)) {
                response.setHeader(TIMING_HEADER, InterviewRequestTiming.begin(request, Operation.ANSWER));
            } else if (REPORT_PATH.equals(pattern)) {
                response.setHeader(TIMING_HEADER, InterviewRequestTiming.begin(request, Operation.REPORT));
            }
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                    Object handler, Exception exception) {
            InterviewRequestTiming.finish(request, response.getStatus(), exception != null);
        }
    }
}
