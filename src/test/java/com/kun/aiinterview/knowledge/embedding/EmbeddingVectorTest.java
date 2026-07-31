package com.kun.aiinterview.knowledge.embedding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingVectorTest {

    @Test
    void shouldCreateVectorWithInputIndexAndValues() {
        EmbeddingVector vector = new EmbeddingVector(
                1,
                List.of(0.1F, 0.2F, 0.3F)
        );

        assertThat(vector.inputIndex()).isEqualTo(1);
        assertThat(vector.values()).containsExactly(0.1F, 0.2F, 0.3F);
    }

    @Test
    void shouldRejectNegativeInputIndex() {
        assertThatThrownBy(() -> new EmbeddingVector(
                -1,
                List.of(0.1F)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("索引");
    }

    @Test
    void shouldRejectNullValues() {
        assertThatThrownBy(() -> new EmbeddingVector(0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("向量");
    }

    @Test
    void shouldRejectEmptyValues() {
        assertThatThrownBy(() -> new EmbeddingVector(0, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("空");
    }

    @Test
    void shouldDefensivelyCopyOriginalValues() {
        List<Float> originalValues = new ArrayList<>(
                List.of(0.1F, 0.2F)
        );

        EmbeddingVector vector = new EmbeddingVector(0, originalValues);
        originalValues.set(0, 9.9F);

        assertThat(vector.values()).containsExactly(0.1F, 0.2F);
    }

    @Test
    void shouldExposeUnmodifiableValues() {
        EmbeddingVector vector = new EmbeddingVector(
                0,
                List.of(0.1F, 0.2F)
        );

        assertThatThrownBy(() -> vector.values().add(0.3F))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
