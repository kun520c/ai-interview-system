package com.kun.aiinterview.knowledge.embedding;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "milvus.enabled=false",
        "spring.main.web-application-type=none"
})
@ActiveProfiles("local")
@Tag("real-external")
@EnabledIfEnvironmentVariable(
        named = "RUN_REAL_EMBEDDING_TEST",
        matches = "(?i)true"
)
class RealEmbeddingSmokeTest {

    private static final String EXPECTED_MODEL =
            "qwen3.7-text-embedding";
    private static final int EXPECTED_DIMENSION = 1024;
    private static final String EXPECTED_PROFILE_VERSION =
            "qwen3.7-text-embedding-1024-dense-v1";

    @BeforeAll
    static void requireExplicitRealEmbeddingOptIn() {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(
                        System.getenv("RUN_REAL_EMBEDDING_TEST")
                ),
                "未显式启用真实Embedding测试"
        );
    }

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private EmbeddingProperties embeddingProperties;

    @Test
    void shouldRequestRealEmbeddingsAndReturnValidVectors() {
        assertExpectedEmbeddingProfile();

        EmbeddingBatchResult result = embeddingClient.embed(List.of(
                "Java 中 HashMap 的底层数据结构是什么？",
                "Spring Bean 的生命周期包含哪些主要阶段？"
        ));

        assertThat(result).isNotNull();
        assertThat(result.model()).isEqualTo(embeddingProperties.getModel());
        assertThat(result.profileVersion())
                .isEqualTo(embeddingProperties.getProfileVersion());
        assertThat(result.dimension())
                .isEqualTo(embeddingProperties.getDimension());
        assertThat(result.vectors()).hasSize(2);
        assertThat(result.vectors())
                .extracting(EmbeddingVector::inputIndex)
                .containsExactly(0, 1);

        for (EmbeddingVector vector : result.vectors()) {
            assertThat(vector).isNotNull();
            assertThat(vector.values())
                    .hasSize(embeddingProperties.getDimension())
                    .doesNotContainNull()
                    .allMatch(Float::isFinite)
                    .anyMatch(value -> Float.compare(value, 0.0F) != 0);
        }

        if (result.totalTokenCount() != null) {
            assertThat(result.totalTokenCount()).isGreaterThanOrEqualTo(0L);
        }

        System.out.printf(
                "Real Embedding summary: model=%s, profileVersion=%s, "
                        + "dimension=%d, vectorCount=%d, totalTokenCount=%s%n",
                result.model(),
                result.profileVersion(),
                result.dimension(),
                result.vectors().size(),
                result.totalTokenCount()
        );
    }

    private void assertExpectedEmbeddingProfile() {
        assertThat(embeddingProperties.getModel())
                .isEqualTo(EXPECTED_MODEL);
        assertThat(embeddingProperties.getDimension())
                .isEqualTo(EXPECTED_DIMENSION);
        assertThat(embeddingProperties.getProfileVersion())
                .isEqualTo(EXPECTED_PROFILE_VERSION);
    }
}
