package com.kun.aiinterview.knowledge.embedding.dashscope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashScopeEmbeddingRequestTest {

    private final ObjectMapper objectMapper =
            Jackson2ObjectMapperBuilder.json().build();

    @Test
    void shouldSerializeOpenAiCompatibleEmbeddingRequest() throws Exception {
        DashScopeEmbeddingRequest request = new DashScopeEmbeddingRequest(
                "test-model",
                List.of("first text", "second text"),
                3,
                "float"
        );

        JsonNode json = objectMapper.readTree(
                objectMapper.writeValueAsBytes(request)
        );

        assertThat(json.get("model").asText()).isEqualTo("test-model");
        assertThat(json.get("input").isArray()).isTrue();
        assertThat(json.get("input")).hasSize(2);
        assertThat(json.get("input").get(0).asText())
                .isEqualTo("first text");
        assertThat(json.get("input").get(1).asText())
                .isEqualTo("second text");
        assertThat(json.get("dimensions").asInt()).isEqualTo(3);
        assertThat(json.get("encoding_format").asText()).isEqualTo("float");
        assertThat(json.has("encodingFormat")).isFalse();
        assertThat(json.has("apiKey")).isFalse();
        assertThat(json.has("profileVersion")).isFalse();
    }
}
