package com.kun.aiinterview.interview.evaluation.llm.deepseek;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.interview.evaluation.llm.DeepSeekEvaluationResult;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "deepseek",
        name = "enabled",
        havingValue = "true"
)
public class DeepSeekEvaluationClient {

    private static final String CHAT_COMPLETIONS_PATH =
            "/chat/completions";

    private final RestClient restClient;
    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;

    public DeepSeekEvaluationClient(
            @Qualifier("deepSeekRestClient")
            RestClient restClient,
            DeepSeekProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public DeepSeekEvaluationResult evaluate(
            EvaluationPrompt prompt
    ) {

        if (prompt == null) {
            throw new IllegalArgumentException(
                    "EvaluationPrompt不能为空"
            );
        }

        DeepSeekChatRequest request =
                buildRequest(prompt);

        DeepSeekChatResponse response =
                requestCompletion(request);

        DeepSeekChoice choice =
                validateResponse(response);

        String rawJson =
                choice.message().content();

        LlmEvaluationSuggestion suggestion =
                parseSuggestion(rawJson);

        return new DeepSeekEvaluationResult(
                response.model(),
                choice.finishReason(),
                rawJson,
                suggestion
        );
    }

    private DeepSeekChatRequest buildRequest(
            EvaluationPrompt prompt
    ) {

        return new DeepSeekChatRequest(
                properties.getModel(),

                List.of(
                        new DeepSeekMessage(
                                "system",
                                prompt.systemPrompt()
                        ),
                        new DeepSeekMessage(
                                "user",
                                prompt.userPrompt()
                        )
                ),

                new DeepSeekResponseFormat(
                        "json_object"
                ),

                new DeepSeekThinking(
                        "disabled"
                ),

                properties.getMaxTokens(),

                false
        );
    }

    private DeepSeekChatResponse requestCompletion(
            DeepSeekChatRequest request
    ) {

        try {

            DeepSeekChatResponse response =
                    restClient
                            .post()
                            .uri(CHAT_COMPLETIONS_PATH)
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(request)
                            .retrieve()
                            .body(
                                    DeepSeekChatResponse.class
                            );

            if (response == null) {
                throw new ExternalServiceException(
                        "DeepSeek服务器返回空响应"
                );
            }

            return response;

        } catch (RestClientException exception) {

            throw new ExternalServiceException(
                    "调用DeepSeek评价服务失败",
                    exception
            );
        }
    }

    private DeepSeekChoice validateResponse(
            DeepSeekChatResponse response
    ) {

        if (response.model() == null
                || response.model().isBlank()) {

            throw new ExternalServiceException(
                    "DeepSeek响应缺少模型名称"
            );
        }

        if (response.choices() == null
                || response.choices().size() != 1) {

            throw new ExternalServiceException(
                    "DeepSeek响应choices数量异常"
            );
        }

        DeepSeekChoice choice =
                response.choices().get(0);

        if (choice == null) {
            throw new ExternalServiceException(
                    "DeepSeek响应包含空choice"
            );
        }

        if (choice.index() == null
                || choice.index() != 0) {

            throw new ExternalServiceException(
                    "DeepSeek响应choice索引异常"
            );
        }

        /*
         * 只有 stop 才表示正常完成。
         *
         * length:
         * JSON 可能被 max_tokens 截断。
         *
         * content_filter /
         * insufficient_system_resource /
         * tool_calls:
         * 也都不能作为正常评价结果继续处理。
         */
        if (!"stop".equals(
                choice.finishReason()
        )) {

            throw new ExternalServiceException(
                    "DeepSeek响应未正常结束，finishReason="
                            + choice.finishReason()
            );
        }

        if (choice.message() == null) {
            throw new ExternalServiceException(
                    "DeepSeek响应缺少message"
            );
        }

        if (!"assistant".equals(
                choice.message().role()
        )) {
            throw new ExternalServiceException(
                    "DeepSeek响应message role异常"
            );
        }

        if (choice.message().content() == null
                || choice.message()
                .content()
                .isBlank()) {

            throw new ExternalServiceException(
                    "DeepSeek响应缺少评价内容"
            );
        }

        return choice;
    }

    private LlmEvaluationSuggestion parseSuggestion(
            String rawJson
    ) {

        try {

            LlmEvaluationSuggestion suggestion =
                    objectMapper.readValue(
                            rawJson,
                            LlmEvaluationSuggestion.class
                    );

            if (suggestion == null) {
                throw new ExternalServiceException(
                        "DeepSeek评价JSON解析结果不能为空"
                );
            }

            return suggestion;

        } catch (JsonProcessingException exception) {

            throw new ExternalServiceException(
                    "DeepSeek评价结果不是可解析的JSON对象",
                    exception
            );
        }
    }

    private record DeepSeekChatRequest(
            String model,

            List<DeepSeekMessage> messages,

            @JsonProperty("response_format")
            DeepSeekResponseFormat responseFormat,

            DeepSeekThinking thinking,

            @JsonProperty("max_tokens")
            int maxTokens,

            boolean stream
    ) {
    }

    private record DeepSeekMessage(
            String role,
            String content
    ) {
    }

    private record DeepSeekResponseFormat(
            String type
    ) {
    }

    private record DeepSeekThinking(
            String type
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekChatResponse(
            String model,
            List<DeepSeekChoice> choices
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekChoice(
            Integer index,

            @JsonProperty("finish_reason")
            String finishReason,

            DeepSeekResponseMessage message
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekResponseMessage(
            String role,
            String content
    ) {
    }
}