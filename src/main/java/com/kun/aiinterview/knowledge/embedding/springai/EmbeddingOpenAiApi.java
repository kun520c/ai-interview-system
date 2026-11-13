package com.kun.aiinterview.knowledge.embedding.springai;

import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.URI;

/** Checks the wire response before Spring AI's model conversion and logging. */
public final class EmbeddingOpenAiApi extends OpenAiApi {

    public EmbeddingOpenAiApi(
            EmbeddingProperties properties,
            String embeddingsPath,
            RestClient.Builder restClientBuilder
    ) {
        super(properties.getBaseUrl().toString(), new SimpleApiKey(properties.getApiKey()),
                new LinkedMultiValueMap<>(), "/v1/chat/completions", embeddingsPath,
                restClientBuilder, WebClient.builder(), new ResponseErrorHandler() {
                    @Override
                    public boolean hasError(ClientHttpResponse response) throws IOException {
                        return !response.getStatusCode().is2xxSuccessful();
                    }

                    @Override
                    public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
                        // Never read the error body, status text or diagnostic headers.
                        throw new ExternalServiceException(
                                "调用Embedding服务失败 [stage=embedding_http, category=HTTP_STATUS, httpStatus="
                                        + response.getStatusCode().value() + "]"
                        );
                    }
                });
    }

    @Override
    public <T> ResponseEntity<EmbeddingList<Embedding>> embeddings(EmbeddingRequest<T> request) {
        ResponseEntity<EmbeddingList<Embedding>> response = super.embeddings(request);
        EmbeddingList<Embedding> body = response.getBody();
        // Spring AI 1.1.8 logs the request on a null body and dereferences data/items unchecked.
        // Reject those wire shapes here; do not disguise arbitrary model bugs as provider failures.
        if (body == null) {
            throw new ExternalServiceException("Embedding服务器返回空响应 [stage=embedding_response, category=RESPONSE_SHAPE]");
        }
        if (body.data() == null) {
            throw new ExternalServiceException("Embedding响应缺少data [stage=embedding_response, category=RESPONSE_SHAPE]");
        }
        if (body.data().stream().anyMatch(item -> item == null)) {
            throw new ExternalServiceException("Embedding响应包含空数据项 [stage=embedding_response, category=RESPONSE_SHAPE]");
        }
        return response;
    }
}
