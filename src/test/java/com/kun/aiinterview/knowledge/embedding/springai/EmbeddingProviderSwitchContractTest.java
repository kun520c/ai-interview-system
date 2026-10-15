package com.kun.aiinterview.knowledge.embedding.springai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingConfiguration;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.retrieval.RetrievalProperties;
import com.kun.aiinterview.knowledge.service.KnowledgeRetrievalService;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Real Spring AI OpenAI adapter, two intercepted compatible endpoints, unchanged business services. */
class EmbeddingProviderSwitchContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final VectorStoreClient vectors = mock(VectorStoreClient.class);
    private final KnowledgeChunkMapper chunks = mock(KnowledgeChunkMapper.class);

    @ParameterizedTest
    @ValueSource(strings = {"bailian-compatible", "alternate-compatible"})
    void switchesEndpointModelAndProfileWithoutChangingBusinessCode(String provider) throws Exception {
        var properties = properties(provider);
        var builder = EmbeddingConfiguration.createEmbeddingRestClientBuilder(properties);
        var server = MockRestServiceServer.bindTo(builder).build();
        EmbeddingClient port = client(properties, builder);
        server.expect(requestTo(properties.getBaseUrl() + "/embeddings"))
                .andExpect(header("Authorization", "Bearer offline-key"))
                .andExpect(jsonPath("$.model").value(properties.getModel()))
                .andExpect(jsonPath("$.dimensions").value(1024))
                .andExpect(jsonPath("$.encoding_format").value("float"))
                .andExpect(jsonPath("$.input[0]").value("  first  "))
                .andRespond(withSuccess(response(properties.getModel(), 1024, 1, 0), MediaType.APPLICATION_JSON));
        server.expect(requestTo(properties.getBaseUrl() + "/embeddings"))
                .andExpect(jsonPath("$.input.length()").value(1))
                .andRespond(withSuccess(response(properties.getModel(), 1024, 0), MediaType.APPLICATION_JSON));
        server.expect(requestTo(properties.getBaseUrl() + "/embeddings"))
                .andExpect(jsonPath("$.input[0]").value("query"))
                .andRespond(withSuccess(response(properties.getModel(), 1024, 0), MediaType.APPLICATION_JSON));

        var embedded = port.embed(List.of("  first  ", "second", "third"));
        assertThat(embedded.vectors()).extracting(vector -> vector.inputIndex()).containsExactly(0, 1, 2);
        assertThat(embedded.vectors().get(0).values()).containsOnly(0.25F);
        assertThat(embedded.vectors().get(1).values()).containsOnly(0.5F);
        assertThat(embedded.profileVersion()).isEqualTo(properties.getProfileVersion());
        assertThat(embedded.totalTokenCount()).isEqualTo(18L); // Request totals, never per-vector allocation.
        var queryVector = Collections.nCopies(1024, 0.25F);
        when(vectors.search(queryVector, properties.getProfileVersion(), 5)).thenReturn(List.of());
        var service = new KnowledgeRetrievalService(port, vectors, chunks, new RetrievalProperties());
        var retrieval = service.retrieve("query", 5);
        assertThat(retrieval.embeddingModel()).isEqualTo(properties.getModel());
        assertThat(retrieval.embeddingVersion()).isEqualTo(properties.getProfileVersion());
        assertThat(retrieval.items()).isEmpty(); // A new profile cannot assume the old collection has compatible data.
        verify(vectors).search(queryVector, properties.getProfileVersion(), 5);
        verifyNoInteractions(chunks);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"model-drift", "dimension-drift", "duplicate-index", "rate-limit"})
    void incompatibleProviderOutputFailsBeforeVectorSearchAndIsNotRetried(String scenario) throws Exception {
        var properties = properties("alternate-compatible");
        var builder = EmbeddingConfiguration.createEmbeddingRestClientBuilder(properties);
        var server = MockRestServiceServer.bindTo(builder).build();
        var expectation = server.expect(requestTo(properties.getBaseUrl() + "/embeddings"));
        if (scenario.equals("rate-limit")) expectation.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        else expectation.andRespond(withSuccess(response(
                scenario.equals("model-drift") ? "unexpected-model" : properties.getModel(),
                scenario.equals("dimension-drift") ? 768 : 1024,
                scenario.equals("duplicate-index") ? new int[]{0, 0} : new int[]{0}), MediaType.APPLICATION_JSON));
        var service = new KnowledgeRetrievalService(client(properties, builder), vectors, chunks, new RetrievalProperties());
        assertThatThrownBy(() -> service.retrieve("query", 5)).isInstanceOf(ExternalServiceException.class);
        verifyNoInteractions(vectors, chunks);
        server.verify();
    }

    private EmbeddingClient client(EmbeddingProperties properties, RestClient.Builder builder) {
        var api = EmbeddingConfiguration.createOpenAiApi(properties, builder);
        return new SpringAiEmbeddingClient(EmbeddingConfiguration.createEmbeddingModel(api, properties), properties);
    }

    private EmbeddingProperties properties(String provider) {
        var result = new EmbeddingProperties();
        result.setBaseUrl(URI.create("http://" + provider + ".offline/v1"));
        result.setApiKey("offline-key");
        result.setModel(provider.equals("bailian-compatible") ? "qwen3.7-text-embedding" : "alternate-embedding-v2");
        result.setProfileVersion(provider.equals("bailian-compatible") ? "qwen3.7-text-embedding-1024-dense-v1" : "alternate-1024-v2");
        result.setDimension(1024);
        result.setBatchSize(2);
        result.setConnectTimeout(Duration.ofSeconds(1));
        result.setReadTimeout(Duration.ofSeconds(2));
        return result;
    }

    private String response(String model, int dimension, int... indexes) throws Exception {
        var root = mapper.createObjectNode().put("object", "list").put("model", model);
        root.putObject("usage").put("prompt_tokens", 9).put("total_tokens", 9);
        var data = root.putArray("data");
        for (int index : indexes) {
            var entry = data.addObject().put("object", "embedding").put("index", index);
            var values = entry.putArray("embedding");
            for (int d = 0; d < dimension; d++) values.add(index == 0 ? 0.25F : 0.5F);
        }
        return mapper.writeValueAsString(root);
    }
}
