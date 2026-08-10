package com.kun.aiinterview.knowledge.vector.milvus;

import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import com.kun.aiinterview.knowledge.embedding.EmbeddingVector;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import com.kun.aiinterview.knowledge.vector.VectorWriteItem;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "milvus.enabled=true",
        "spring.main.web-application-type=none"
})
@ActiveProfiles("local")
@Tag("real-external")
@EnabledIfEnvironmentVariable(
        named = "RUN_REAL_MILVUS_TEST",
        matches = "(?i)true"
)
class RealMilvusVectorStoreSmokeTest {

    private static final String EXPECTED_MODEL =
            "qwen3.7-text-embedding";
    private static final int EXPECTED_DIMENSION = 1024;
    private static final String EXPECTED_PROFILE_VERSION =
            "qwen3.7-text-embedding-1024-dense-v1";
    private static final int TOP_K = 10;
    private static final double COSINE_ORDER_TOLERANCE = 0.000001D;
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(400);

    @BeforeAll
    static void requireExplicitRealMilvusOptIn() {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(
                        System.getenv("RUN_REAL_MILVUS_TEST")
                ),
                "未显式启用真实Milvus测试"
        );
    }

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private EmbeddingProperties embeddingProperties;

    @Autowired
    private VectorStoreClient vectorStoreClient;

    @Autowired
    private MilvusProperties milvusProperties;

    @Test
    void shouldCompleteRealEmbeddingAndMilvusLifecycle() throws Throwable {
        assertExpectedEmbeddingAndMilvusProfile();

        SmokeData data = newSmokeData();
        List<List<Float>> generatedVectors = null;
        Throwable primaryFailure = null;

        System.out.printf(
                "Real Milvus smoke start: testId=%s, vectorIds=%s, "
                        + "documentIds=%s%n",
                data.testId(),
                List.of(data.vectorA(), data.vectorB(), data.vectorC()),
                List.of(data.documentA(), data.documentB())
        );

        try {
            EmbeddingBatchResult embeddingResult = embeddingClient.embed(
                    data.texts()
            );
            assertEmbeddingResult(embeddingResult);

            List<List<Float>> vectors = embeddingResult.vectors().stream()
                    .map(EmbeddingVector::values)
                    .toList();
            generatedVectors = vectors;

            System.out.printf(
                    "Real Embedding completed: testId=%s, model=%s, "
                            + "profileVersion=%s, dimension=%d, "
                            + "vectorCount=%d, totalTokenCount=%s%n",
                    data.testId(),
                    embeddingResult.model(),
                    embeddingResult.profileVersion(),
                    embeddingResult.dimension(),
                    embeddingResult.vectors().size(),
                    embeddingResult.totalTokenCount()
            );

            List<VectorWriteItem> items = List.of(
                    new VectorWriteItem(
                            data.vectorA(),
                            data.documentA(),
                            1,
                            embeddingResult.profileVersion(),
                            vectors.get(0)
                    ),
                    new VectorWriteItem(
                            data.vectorB(),
                            data.documentA(),
                            2,
                            embeddingResult.profileVersion(),
                            vectors.get(1)
                    ),
                    new VectorWriteItem(
                            data.vectorC(),
                            data.documentB(),
                            1,
                            embeddingResult.profileVersion(),
                            vectors.get(2)
                    )
            );

            vectorStoreClient.insert(items);
            System.out.printf(
                    "Real Milvus insert completed: testId=%s%n",
                    data.testId()
            );

            await().atMost(AWAIT_TIMEOUT)
                     .pollInterval(POLL_INTERVAL)
                     .untilAsserted(() -> {
                        List<VectorSearchHit> hitsA = search(vectors.get(0));
                        assertThat(hitsA).isNotEmpty();

                        VectorSearchHit hitA = hitsA.getFirst();
                        assertThat(hitA.vectorId())
                                .isEqualTo(data.vectorA());
                        assertThat(hitA.documentId())
                                .isEqualTo(data.documentA());
                        assertThat(hitA.chunkIndex()).isEqualTo(1);
                        assertThat(hitA.embeddingVersion())
                                .isEqualTo(embeddingProperties.getProfileVersion());
                        assertThat(Double.isFinite(hitA.similarityScore()))
                                .isTrue();
                        assertThat(hitA.similarityScore())
                                .isCloseTo(1.0D, within(0.0001D));
                        assertCosineScoresNonIncreasing(hitsA);

                        assertPresent(
                                search(vectors.get(1)),
                                data.vectorB()
                        );
                        assertPresent(
                                search(vectors.get(2)),
                                data.vectorC()
                        );
                    });
            System.out.printf(
                    "Real Milvus search completed: testId=%s%n",
                    data.testId()
            );

            vectorStoreClient.deleteByVectorIds(List.of(data.vectorA()));
            await().atMost(AWAIT_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> {
                        assertAbsent(
                                search(vectors.get(0)),
                                data.vectorA()
                        );
                        assertPresent(
                                search(vectors.get(1)),
                                data.vectorB()
                        );
                        assertPresent(
                                search(vectors.get(2)),
                                data.vectorC()
                        );
                    });
            System.out.printf(
                    "Real Milvus deleteByVectorIds completed: testId=%s%n",
                    data.testId()
            );

            vectorStoreClient.deleteByDocumentId(data.documentA());
            await().atMost(AWAIT_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> {
                        assertAbsent(
                                search(vectors.get(0)),
                                data.vectorA()
                        );
                        assertAbsent(
                                search(vectors.get(1)),
                                data.vectorB()
                        );
                        assertPresent(
                                search(vectors.get(2)),
                                data.vectorC()
                        );
                    });
            System.out.printf(
                    "Real Milvus deleteByDocumentId completed: testId=%s%n",
                    data.testId()
            );
        } catch (Throwable failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                cleanUp(data, generatedVectors);
                System.out.printf(
                        "Real Milvus cleanup completed: testId=%s%n",
                        data.testId()
                );
            } catch (Throwable cleanupFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private void assertExpectedEmbeddingAndMilvusProfile() {
        assertThat(embeddingProperties.getModel())
                .isEqualTo(EXPECTED_MODEL);
        assertThat(embeddingProperties.getDimension())
                .isEqualTo(EXPECTED_DIMENSION);
        assertThat(embeddingProperties.getProfileVersion())
                .isEqualTo(EXPECTED_PROFILE_VERSION);
        assertThat(milvusProperties.getDimension())
                .isEqualTo(EXPECTED_DIMENSION);
    }

    private void assertEmbeddingResult(EmbeddingBatchResult result) {
        assertThat(result).isNotNull();
        assertThat(result.model()).isEqualTo(embeddingProperties.getModel());
        assertThat(result.profileVersion())
                .isEqualTo(embeddingProperties.getProfileVersion());
        assertThat(result.dimension()).isEqualTo(EXPECTED_DIMENSION);
        assertThat(result.dimension())
                .isEqualTo(embeddingProperties.getDimension())
                .isEqualTo(milvusProperties.getDimension());
        assertThat(result.vectors()).hasSize(3);
        assertThat(result.vectors())
                .extracting(EmbeddingVector::inputIndex)
                .containsExactly(0, 1, 2);

        for (EmbeddingVector vector : result.vectors()) {
            assertThat(vector).isNotNull();
            assertThat(vector.values())
                    .hasSize(EXPECTED_DIMENSION)
                    .doesNotContainNull()
                    .allMatch(Float::isFinite)
                    .anyMatch(value -> Float.compare(value, 0.0F) != 0);
        }

        if (result.totalTokenCount() != null) {
            assertThat(result.totalTokenCount()).isGreaterThanOrEqualTo(0L);
        }
    }

    private List<VectorSearchHit> search(List<Float> queryVector) {
        return vectorStoreClient.search(
                queryVector,
                embeddingProperties.getProfileVersion(),
                TOP_K
        );
    }

    private static void assertCosineScoresNonIncreasing(
            List<VectorSearchHit> hits
    ) {
        for (int index = 0; index < hits.size() - 1; index++) {
            double currentScore = hits.get(index).similarityScore();
            double nextScore = hits.get(index + 1).similarityScore();

            assertThat(currentScore + COSINE_ORDER_TOLERANCE)
                    .isGreaterThanOrEqualTo(nextScore);
        }
    }

    private static void assertPresent(
            List<VectorSearchHit> hits,
            String vectorId
    ) {
        assertThat(hits)
                .extracting(VectorSearchHit::vectorId)
                .contains(vectorId);
    }

    private static void assertAbsent(
            List<VectorSearchHit> hits,
            String vectorId
    ) {
        assertThat(hits)
                .extracting(VectorSearchHit::vectorId)
                .doesNotContain(vectorId);
    }

    private void cleanUp(
            SmokeData data,
            List<List<Float>> generatedVectors
    ) throws Throwable {
        Throwable cleanupFailure = null;

        cleanupFailure = attemptCleanup(
                cleanupFailure,
                () -> vectorStoreClient.deleteByVectorIds(
                        List.of(
                                data.vectorA(),
                                data.vectorB(),
                                data.vectorC()
                        )
                )
        );
        cleanupFailure = attemptCleanup(
                cleanupFailure,
                () -> vectorStoreClient.deleteByDocumentId(data.documentA())
        );
        cleanupFailure = attemptCleanup(
                cleanupFailure,
                () -> vectorStoreClient.deleteByDocumentId(data.documentB())
        );

        if (generatedVectors != null) {
            cleanupFailure = attemptCleanup(cleanupFailure, () ->
                    await().atMost(AWAIT_TIMEOUT)
                            .pollInterval(POLL_INTERVAL)
                            .untilAsserted(() -> {
                                assertAbsent(
                                        search(generatedVectors.get(0)),
                                        data.vectorA()
                                );
                                assertAbsent(
                                        search(generatedVectors.get(1)),
                                        data.vectorB()
                                );
                                assertAbsent(
                                        search(generatedVectors.get(2)),
                                        data.vectorC()
                                );
                            })
            );
        }

        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    private static Throwable attemptCleanup(
            Throwable previousFailure,
            CleanupAction action
    ) {
        try {
            action.run();
            return previousFailure;
        } catch (Throwable failure) {
            if (previousFailure == null) {
                return failure;
            }
            previousFailure.addSuppressed(failure);
            return previousFailure;
        }
    }

    private static SmokeData newSmokeData() {
        UUID uuid = UUID.randomUUID();
        String testId = uuid.toString();
        String compactId = testId.replace("-", "");
        long documentA = Long.parseLong(compactId.substring(0, 15), 16) + 1;
        long documentB = Long.parseLong(compactId.substring(15, 30), 16) + 1;

        if (documentB == documentA) {
            documentB++;
        }

        return new SmokeData(
                testId,
                "rmvs-a-" + compactId,
                "rmvs-b-" + compactId,
                "rmvs-c-" + compactId,
                documentA,
                documentB,
                List.of(
                        "Java HashMap 底层结构真实验证 " + testId,
                        "Spring Bean 生命周期真实验证 " + testId,
                        "MySQL 事务隔离级别真实验证 " + testId
                )
        );
    }

    @FunctionalInterface
    private interface CleanupAction {
        void run() throws Throwable;
    }

    private record SmokeData(
            String testId,
            String vectorA,
            String vectorB,
            String vectorC,
            long documentA,
            long documentB,
            List<String> texts
    ) {
    }
}
