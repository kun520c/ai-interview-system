package com.kun.aiinterview.knowledge.vector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.DoubleStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorSearchHitTest {

    @ParameterizedTest
    @ValueSource(doubles = {0.75D, 0.0D, -0.25D})
    void shouldAcceptAnyFiniteCosineSimilarity(double similarityScore) {
        VectorSearchHit hit = validHit(
                "vector-1",
                10L,
                1,
                "embedding-v1",
                similarityScore
        );

        assertThat(hit.similarityScore()).isEqualTo(similarityScore);
    }

    @ParameterizedTest
    @MethodSource("nonFiniteScores")
    void shouldRejectNonFiniteSimilarity(double similarityScore) {
        assertThatThrownBy(() -> validHit(
                "vector-1",
                10L,
                1,
                "embedding-v1",
                similarityScore
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("有限");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldRejectMissingOrBlankVectorId(String vectorId) {
        assertThatThrownBy(() -> validHit(vectorId, 10L, 1, "embedding-v1", 0.5D))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Id");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void shouldRejectNonPositiveOrMissingDocumentId(Long documentId) {
        assertThatThrownBy(() -> validHit("vector-1", documentId, 1, "embedding-v1", 0.5D))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档Id");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectChunkIndexBeforeOne(int chunkIndex) {
        assertThatThrownBy(() -> validHit("vector-1", 10L, chunkIndex, "embedding-v1", 0.5D))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("索引");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldRejectMissingOrBlankEmbeddingVersion(String embeddingVersion) {
        assertThatThrownBy(() -> validHit("vector-1", 10L, 1, embeddingVersion, 0.5D))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本");
    }

    private static VectorSearchHit validHit(
            String vectorId,
            Long documentId,
            int chunkIndex,
            String embeddingVersion,
            double similarityScore
    ) {
        return new VectorSearchHit(
                vectorId,
                documentId,
                chunkIndex,
                embeddingVersion,
                similarityScore
        );
    }

    private static DoubleStream nonFiniteScores() {
        return DoubleStream.of(
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        );
    }
}
