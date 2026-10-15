package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingVector;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.retrieval.KnowledgeRetrievalRow;
import com.kun.aiinterview.knowledge.retrieval.RetrievalProperties;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KnowledgeRetrievalThresholdTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(RetrievalProperties.class);

    @Test
    void defaultsKeepExperimentDisabledAndBindingCanEnableIt() {
        contextRunner.run(c -> {
            assertThat(c).hasNotFailed();
            assertThat(c.getBean(RetrievalProperties.class).isCosineThresholdEnabled()).isFalse();
        });
        contextRunner.withPropertyValues("rag.retrieval.cosine-threshold-enabled=true",
                "rag.retrieval.min-cosine-score=0.75").run(c -> {
            assertThat(c).hasNotFailed();
            assertThat(c.getBean(RetrievalProperties.class).isCosineThresholdEnabled()).isTrue();
            assertThat(c.getBean(RetrievalProperties.class).getMinCosineScore()).isEqualTo(0.75F);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "-1.01", "1.01"})
    void invalidThresholdFailsBinding(String value) {
        contextRunner.withPropertyValues("rag.retrieval.min-cosine-score=" + value)
                .run(c -> assertThat(c).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rawScoresMustBeFinite(double score) {
        assertThatThrownBy(() -> new VectorSearchHit("v", 1L, 1, "profile", score))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void experimentYamlOnlyEnablesCandidatePolicy() throws Exception {
        var sources = new YamlPropertySourceLoader().load("rag-p1",
                new ClassPathResource("application-rag-p1.yaml"));
        var source = (MapPropertySource) sources.get(0);
        assertThat(source.getSource()).containsOnlyKeys(
                "rag.retrieval.cosine-threshold-enabled", "rag.retrieval.min-cosine-score");
        contextRunner.withInitializer(c -> c.getEnvironment().getPropertySources().addFirst(source))
                .run(c -> {
                    assertThat(c).hasNotFailed();
                    var settings = c.getBean(RetrievalProperties.class);
                    assertThat(settings.isCosineThresholdEnabled()).isTrue();
                    assertThat(settings.getMinCosineScore()).isEqualTo(0.70F);
                });
    }

    @Test
    void inclusiveFloat32CutoffPreservesRankGapsAndDoesNotDeduplicateDocument() {
        var embedding = mock(EmbeddingClient.class);
        var vectors = mock(VectorStoreClient.class);
        var mapper = mock(KnowledgeChunkMapper.class);
        var settings = new RetrievalProperties();
        settings.setCosineThresholdEnabled(true);
        List<VectorSearchHit> hits = List.of(
                hit(1, 0.9F), hit(2, Math.nextDown(0.70F)), hit(3, 0.70F),
                hit(4, -0.2F), hit(5, Math.nextUp(0.70F)));
        when(embedding.embed(List.of("query"))).thenReturn(new EmbeddingBatchResult(
                "model", "profile", 1, List.of(new EmbeddingVector(0, List.of(1F))), null));
        when(vectors.search(List.of(1F), "profile", 5)).thenReturn(hits);
        // Even an unexpectedly broad hydration response cannot reintroduce a filtered hit.
        when(mapper.selectRetrievableByVectorIds(any(), eq("model"), eq("profile")))
                .thenReturn(List.of(row(5), row(2), row(3), row(1)));
        var service = new KnowledgeRetrievalService(embedding, vectors, mapper, settings);
        var result = service.retrieve("query", 5);
        assertThat(result.rawHits()).containsExactlyElementsOf(hits);
        assertThat(result.items()).extracting(RetrievedChunk::vectorRank).containsExactly(1, 3, 5);
        assertThat(result.items()).extracting(RetrievedChunk::documentId).containsExactly(1L, 1L, 1L);
        assertThat(result.items()).extracting(RetrievedChunk::similarityScore)
                .containsExactly((double) 0.9F, (double) 0.70F, (double) Math.nextUp(0.70F));
        verify(mapper).selectRetrievableByVectorIds(List.of("v1", "v3", "v5"), "model", "profile");
        verify(vectors, times(1)).search(List.of(1F), "profile", 5);

        settings.setCosineThresholdEnabled(false);
        assertThat(service.retrieve("query", 5).items()).extracting(RetrievedChunk::vectorRank)
                .containsExactly(1, 2, 3, 5);
    }

    private static VectorSearchHit hit(int rank, float score) {
        return new VectorSearchHit("v" + rank, 1L, rank, "profile", score);
    }

    private static KnowledgeRetrievalRow row(int rank) {
        var row = new KnowledgeRetrievalRow();
        row.setVectorId("v" + rank);
        row.setDocumentId(1L);
        row.setChunkIndex(rank);
        return row;
    }
}
