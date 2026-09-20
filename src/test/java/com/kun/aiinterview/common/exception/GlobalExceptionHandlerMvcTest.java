package com.kun.aiinterview.common.exception;

import com.kun.aiinterview.common.response.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerMvcTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldMapTypeMismatchTo400() throws Exception {
        mockMvc.perform(get("/probe/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数不合法"));
    }

    @Test
    void shouldMapMissingRequestParameterTo400() throws Exception {
        mockMvc.perform(get("/probe/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("缺少必要的请求参数"));
    }

    @Test
    void shouldMapUnsupportedMethodTo405() throws Exception {
        mockMvc.perform(put("/probe/required"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405))
                .andExpect(jsonPath("$.message").value("不支持的请求方法"));
    }

    @Test
    void shouldMapResourceNotFoundTo404() throws Exception {
        mockMvc.perform(get("/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("面试会话不存在"));
    }

    @Test
    void shouldMapConflictTo409() throws Exception {
        mockMvc.perform(post("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("答案正在评估中，请稍后重试"));
    }

    @Test
    void shouldSanitizeExternalServiceFailureAs502() throws Exception {
        mockMvc.perform(get("/probe/external"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.message").value("外部服务暂时不可用"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("sk-secret")
                )))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("raw provider")
                )));
    }

    @Test
    void shouldMapUnexpectedExceptionTo500WithoutLeakingDetail()
            throws Exception {
        mockMvc.perform(get("/probe/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("系统异常，请稍后重试"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal boom")
                )));
    }

    @Test
    void shouldKeepMultipartOversizeAs413() throws Exception {
        mockMvc.perform(post("/probe/oversize"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(413))
                .andExpect(jsonPath("$.message").value("上传文件不能超过5MB"));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/items/{id}")
        Result<Void> item(@PathVariable Long id) {
            return Result.success();
        }

        @GetMapping("/probe/required")
        Result<Void> required(@RequestParam String q) {
            return Result.success();
        }

        @GetMapping("/probe/missing")
        Result<Void> missing() {
            throw new ResourceNotFoundException("面试会话不存在");
        }

        @PostMapping("/probe/conflict")
        Result<Void> conflict() {
            throw new ConflictException("答案正在评估中，请稍后重试");
        }

        @GetMapping("/probe/external")
        Result<Void> external() {
            throw new ExternalServiceException(
                    "DashScope 401 api-key=sk-secret raw provider body"
            );
        }

        @GetMapping("/probe/unexpected")
        Result<Void> unexpected() {
            throw new IllegalStateException("internal boom");
        }

        @PostMapping("/probe/oversize")
        Result<Void> oversize() {
            throw new MaxUploadSizeExceededException(5L * 1024 * 1024);
        }
    }
}
