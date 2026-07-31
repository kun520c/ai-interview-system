package com.kun.aiinterview.knowledge.embedding.dashscope;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class DashScopeEmbeddingResponseTest {

    private final ObjectMapper objectMapper =
            Jackson2ObjectMapperBuilder.json().build();

    @Test
    void shouldDeserializeKnownFieldsAndIgnoreSupplierMetadata()
            throws Exception {
        String json = """
                {
                  "id": "response-id",
                  "object": "list",
                  "data": [
                    {
                      "object": "embedding",
                      "index": 0,
                      "embedding": [0.1, 0.2, 0.3]
                    }
                  ],
                  "model": "test-model",
                  "usage": {
                    "prompt_tokens": 4,
                    "total_tokens": 5
                  }
                }
                """;

        DashScopeEmbeddingResponse response = objectMapper.readValue(
                json,
                DashScopeEmbeddingResponse.class
        );

        assertThat(response.model()).isEqualTo("test-model");
        assertThat(response.data()).singleElement().satisfies(data -> {
            assertThat(data.index()).isZero();
            assertThat(data.embedding())
                    .containsExactly(0.1F, 0.2F, 0.3F);
        });
        assertThat(response.usage().promptTokens()).isEqualTo(4L);
        assertThat(response.usage().totalTokens()).isEqualTo(5L);
    }

    @Test
    void shouldKeepMissingIndexAsNull() throws Exception {
        String json = """
                {
                  "data": [{"embedding": [0.1, 0.2, 0.3]}],
                  "model": "test-model"
                }
                """;

        DashScopeEmbeddingResponse response = objectMapper.readValue(
                json,
                DashScopeEmbeddingResponse.class
        );

        assertThat(response.data().getFirst().index()).isNull();
    }

    @Test
    void shouldAllowMissingUsageForClientLevelDecision() throws Exception {
        String json = """
                {
                  "data": [
                    {"index": 0, "embedding": [0.1, 0.2, 0.3]}
                  ],
                  "model": "test-model"
                }
                """;

        DashScopeEmbeddingResponse response = objectMapper.readValue(
                json,
                DashScopeEmbeddingResponse.class
        );

        assertThat(response.usage()).isNull();
    }
}
