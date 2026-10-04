package com.kun.aiinterview.interview.evaluation.judge.jev;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** One HTTP attempt. Provider fallback, rather than HTTP retry, bounds latency. */
public class JevClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public JevClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .configure(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION, true);
    }

    public JevResponse evaluate(JevRequest request) {
        if (request == null || request.state() == null || request.state().isBlank()
                || request.model() == null || request.model().isBlank() || request.questions().isEmpty()) {
            throw new IllegalArgumentException("Jev请求缺少模型、评价内容或问题");
        }
        try {
            String body = restClient.post().uri("/v1/systemone")
                    .contentType(MediaType.APPLICATION_JSON).body(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), (httpRequest, response) -> {
                        // Never retain or log provider error bodies, which may echo user input.
                        throw new ExternalServiceException("Jev HTTP调用失败");
                    })
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new ExternalServiceException("Jev返回空响应");
            }
            JevResponse response = objectMapper.readValue(body, JevResponse.class);
            if (response == null || response.model() == null || response.model().isBlank()
                    || response.model().length() > 100 || response.answers() == null
                    || !response.answers().keySet().equals(request.questions().keySet())
                    || response.answers().values().stream().anyMatch(answer -> answer == null)) {
                throw new ExternalServiceException("Jev响应模型或问题集合非法");
            }
            return response;
        } catch (RestClientException | JsonProcessingException exception) {
            // Parser messages may include response text: do not attach them to a logged exception.
            throw new ExternalServiceException("Jev HTTP或JSON解析失败");
        }
    }
}
