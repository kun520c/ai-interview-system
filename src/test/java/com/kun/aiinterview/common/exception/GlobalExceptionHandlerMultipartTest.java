package com.kun.aiinterview.common.exception;

import com.kun.aiinterview.common.response.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerMultipartTest {

    @Test
    void givenMaxUploadSizeExceededException_whenHandled_thenReturns413Json()
            throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OversizeProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/probe-oversize"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(413))
                .andExpect(jsonPath("$.message").value("上传文件不能超过5MB"));
    }

    @RestController
    static class OversizeProbeController {
        @PostMapping("/probe-oversize")
        Result<Void> probe() {
            throw new MaxUploadSizeExceededException(5L * 1024 * 1024);
        }
    }
}
