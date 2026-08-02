package com.kun.aiinterview.knowledge.vector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorWriteItemTest {

    @Test
    void shouldCreateValidItem() {
        VectorWriteItem item = new VectorWriteItem(
                "vector-1",
                10L,
                1,
                "embedding-v1",
                List.of(0.1F, -0.2F, 0.3F)
        );

        assertThat(item.vectorId()).isEqualTo("vector-1");
        assertThat(item.documentId()).isEqualTo(10L);
        assertThat(item.chunkIndex()).isEqualTo(1);
        assertThat(item.embeddingVersion()).isEqualTo("embedding-v1");
        assertThat(item.values()).containsExactly(0.1F, -0.2F, 0.3F);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldRejectMissingOrBlankVectorId(String vectorId) {
        assertThatThrownBy(() -> validItem(vectorId, 10L, 1, "embedding-v1", List.of(0.1F)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Id");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void shouldRejectNonPositiveOrMissingDocumentId(Long documentId) {
        assertThatThrownBy(() -> validItem("vector-1", documentId, 1, "embedding-v1", List.of(0.1F)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档Id");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectChunkIndexBeforeOne(int chunkIndex) {
        assertThatThrownBy(() -> validItem("vector-1", 10L, chunkIndex, "embedding-v1", List.of(0.1F)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("索引");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldRejectMissingOrBlankEmbeddingVersion(String embeddingVersion) {
        assertThatThrownBy(() -> validItem("vector-1", 10L, 1, embeddingVersion, List.of(0.1F)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本");
    }

    @Test
    void shouldRejectNullValues() {
        assertThatThrownBy(() -> validItem("vector-1", 10L, 1, "embedding-v1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("向量");
    }

    @Test
    void shouldRejectEmptyValues() {
        assertThatThrownBy(() -> validItem("vector-1", 10L, 1, "embedding-v1", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("空");
    }

    @ParameterizedTest
    @MethodSource("invalidVectorValues")
    void shouldRejectNullOrNonFiniteValue(Float invalidValue) {
        List<Float> values = new ArrayList<>();
        values.add(0.1F);
        values.add(invalidValue);

        assertThatThrownBy(() -> validItem("vector-1", 10L, 1, "embedding-v1", values))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("维度索引：1");
    }

    @Test
    void shouldDefensivelyCopyOriginalValues() {
        List<Float> originalValues = new ArrayList<>(List.of(0.1F, 0.2F));

        VectorWriteItem item = validItem(
                "vector-1",
                10L,
                1,
                "embedding-v1",
                originalValues
        );
        originalValues.set(0, 9.9F);

        assertThat(item.values()).containsExactly(0.1F, 0.2F);
    }

    @Test
    void shouldExposeUnmodifiableValues() {
        VectorWriteItem item = validItem(
                "vector-1",
                10L,
                1,
                "embedding-v1",
                List.of(0.1F, 0.2F)
        );

        assertThatThrownBy(() -> item.values().add(0.3F))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static VectorWriteItem validItem(
            String vectorId,
            Long documentId,
            int chunkIndex,
            String embeddingVersion,
            List<Float> values
    ) {
        return new VectorWriteItem(
                vectorId,
                documentId,
                chunkIndex,
                embeddingVersion,
                values
        );
    }

    private static Stream<Float> invalidVectorValues() {
        return Stream.of(
                null,
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY
        );
    }
}
