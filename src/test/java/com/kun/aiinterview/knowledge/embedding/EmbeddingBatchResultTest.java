package com.kun.aiinterview.knowledge.embedding;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingBatchResultTest {

    @Test
    void shouldCreateBatchResultWithRequestLevelTokenCount() {
        EmbeddingBatchResult result = new EmbeddingBatchResult(
                "test-model",
                "test-profile-v1",
                3,
                List.of(vector(0)),
                8L
        );

        assertThat(result.model()).isEqualTo("test-model");
        assertThat(result.profileVersion()).isEqualTo("test-profile-v1");
        assertThat(result.dimension()).isEqualTo(3);
        assertThat(result.vectors()).containsExactly(vector(0));
        assertThat(result.totalTokenCount()).isEqualTo(8L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldRejectMissingOrBlankModel(String model) {
        assertThatThrownBy(() -> new EmbeddingBatchResult(
                model,
                "test-profile-v1",
                3,
                List.of(vector(0)),
                8L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void shouldRejectMissingOrBlankProfileVersion(String profileVersion) {
        assertThatThrownBy(() -> new EmbeddingBatchResult(
                "test-model",
                profileVersion,
                3,
                List.of(vector(0)),
                8L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectNonPositiveDimension(int dimension) {
        assertThatThrownBy(() -> new EmbeddingBatchResult(
                "test-model",
                "test-profile-v1",
                dimension,
                List.of(vector(0)),
                8L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("维度");
    }

    @Test
    void shouldRejectNullVectors() {
        assertThatThrownBy(() -> new EmbeddingBatchResult(
                "test-model",
                "test-profile-v1",
                3,
                null,
                8L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("向量集合");
    }

    @Test
    void shouldRejectEmptyVectors() {
        assertThatThrownBy(() -> new EmbeddingBatchResult(
                "test-model",
                "test-profile-v1",
                3,
                List.of(),
                8L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("空");
    }

    @Test
    void shouldRejectVectorWhoseDimensionDoesNotMatchBatchDimension() {
        EmbeddingVector wrongDimension = new EmbeddingVector(
                0,
                List.of(0.1F, 0.2F)
        );

        assertThatThrownBy(() -> new EmbeddingBatchResult(
                "test-model",
                "test-profile-v1",
                3,
                List.of(wrongDimension),
                8L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("维度");
    }

    @Test
    void shouldRejectNegativeTotalTokenCount() {
        assertThatThrownBy(() -> new EmbeddingBatchResult(
                "test-model",
                "test-profile-v1",
                3,
                List.of(vector(0)),
                -1L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");
    }

    @Test
    void shouldAcceptZeroTotalTokenCount() {
        EmbeddingBatchResult result = new EmbeddingBatchResult(
                "test-model",
                "test-profile-v1",
                3,
                List.of(vector(0)),
                0L
        );

        assertThat(result.totalTokenCount()).isZero();
    }

    @Test
    void shouldDefensivelyCopyAndExposeUnmodifiableVectors() {
        List<EmbeddingVector> originalVectors = new ArrayList<>(
                List.of(vector(0))
        );

        EmbeddingBatchResult result = new EmbeddingBatchResult(
                "test-model",
                "test-profile-v1",
                3,
                originalVectors,
                8L
        );
        originalVectors.add(vector(1));

        assertThat(result.vectors()).containsExactly(vector(0));
        assertThatThrownBy(() -> result.vectors().add(vector(1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldAllowBeanValidationOfAValidBatchResult() {
        EmbeddingBatchResult result = new EmbeddingBatchResult(
                "test-model",
                "test-profile-v1",
                3,
                List.of(vector(0)),
                8L
        );

        try (var validatorFactory =
                     Validation.buildDefaultValidatorFactory()) {
            assertThatCode(() ->
                    validatorFactory.getValidator().validate(result)
            ).doesNotThrowAnyException();
        }
    }

    private static EmbeddingVector vector(int inputIndex) {
        return new EmbeddingVector(
                inputIndex,
                List.of(0.1F, 0.2F, 0.3F)
        );
    }
}
