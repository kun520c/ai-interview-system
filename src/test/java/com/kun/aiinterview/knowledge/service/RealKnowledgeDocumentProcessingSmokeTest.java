package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.knowledge.chunk.KnowledgeChunkDraft;
import com.kun.aiinterview.knowledge.chunk.KnowledgeTextChunker;
import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingConfiguration;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import com.kun.aiinterview.knowledge.embedding.dashscope.DashScopeEmbeddingClient;
import com.kun.aiinterview.knowledge.entity.KnowledgeDocument;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.enums.KnowledgeFileType;
import com.kun.aiinterview.knowledge.enums.KnowledgeProcessingStatus;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.mapper.KnowledgeDocumentMapper;
import com.kun.aiinterview.knowledge.retrieval.RetrievalResult;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import com.kun.aiinterview.knowledge.vector.VectorWriteItem;
import com.kun.aiinterview.knowledge.vector.milvus.MilvusCollectionInitializer;
import com.kun.aiinterview.knowledge.vector.milvus.MilvusConfiguration;
import com.kun.aiinterview.knowledge.vector.milvus.MilvusProperties;
import com.kun.aiinterview.knowledge.vector.milvus.MilvusVectorStoreClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
        classes = RealKnowledgeDocumentProcessingSmokeTest.PipelineTestConfiguration.class,
        initializers = ConfigDataApplicationContextInitializer.class
)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "milvus.enabled=true",
        "spring.main.web-application-type=none"
})
@Tag("real-external")
@EnabledIfEnvironmentVariable(
        named = "RUN_REAL_KNOWLEDGE_PIPELINE_TEST",
        matches = "(?i)true"
)
class RealKnowledgeDocumentProcessingSmokeTest {

    private static final String OPT_IN_ENVIRONMENT_VARIABLE =
            "RUN_REAL_KNOWLEDGE_PIPELINE_TEST";
    private static final String EXPECTED_MODEL = "qwen3.7-text-embedding";
    private static final int EXPECTED_DIMENSION = 1024;
    private static final String EXPECTED_PROFILE_VERSION =
            "qwen3.7-text-embedding-1024-dense-v1";
    private static final int DOCUMENT_VERSION = 1;
    private static final int TOP_K = 10;
    private static final int RETRIEVAL_TOP_K = 5;
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(400);

    @Autowired
    private KnowledgeDocumentProcessingService processingService;

    @Autowired
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Autowired
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Autowired
    private KnowledgeTextChunker knowledgeTextChunker;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private EmbeddingProperties embeddingProperties;

    @Autowired
    private VectorStoreClient vectorStoreClient;

    @Autowired
    private MilvusProperties milvusProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void requireExplicitRealPipelineOptIn() {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(
                        System.getenv(OPT_IN_ENVIRONMENT_VARIABLE)
                ),
                "未显式启用真实知识文档处理Pipeline测试"
        );
    }

    @Test
    void shouldCompleteRealKnowledgeDocumentProcessingPipeline() throws Throwable {
        assertRealInfrastructureAndFixedProfiles();

        String testId = UUID.randomUUID().toString();
        String content = stableMultiChunkContent(testId);
        List<KnowledgeChunkDraft> expectedDrafts = knowledgeTextChunker.split(
                content
        );
        assertThat(expectedDrafts).hasSizeGreaterThanOrEqualTo(2);

        KnowledgeDocument document = uploadedDocument(testId, content);
        Long documentId = null;
        List<String> vectorIds = List.of();
        List<Float> cleanupQueryVector = null;
        String cleanupEmbeddingVersion = EXPECTED_PROFILE_VERSION;
        Throwable primaryFailure = null;

        try {
            assertThat(knowledgeDocumentMapper.insertDocument(document))
                    .isEqualTo(1);
            assertThat(document.getId()).isPositive();
            documentId = document.getId();
            assertThat(knowledgeDocumentMapper.selectById(documentId)
                    .getProcessingStatus())
                    .isEqualTo(KnowledgeProcessingStatus.UPLOADED);

            processingService.processDocument(documentId);

            KnowledgeDocument readyDocument = knowledgeDocumentMapper.selectById(
                    documentId
            );
            assertThat(readyDocument.getProcessingStatus())
                    .isEqualTo(KnowledgeProcessingStatus.READY);
            assertThat(readyDocument.getErrorMessage()).isNull();

            List<Map<String, Object>> chunkRows = queryChunkRows(documentId);
            assertThat(chunkRows).hasSize(expectedDrafts.size());
            assertChunkRows(chunkRows, expectedDrafts);
            vectorIds = chunkRows.stream()
                    .map(row -> (String) row.get("vector_id"))
                    .toList();

            Map<String, Object> firstRow = chunkRows.getFirst();
            String firstContent = (String) firstRow.get("content");
            String firstVectorId = (String) firstRow.get("vector_id");
            int firstChunkIndex = asInt(firstRow.get("chunk_index"));
            String embeddingVersion = (String) firstRow.get("embedding_version");
            cleanupEmbeddingVersion = embeddingVersion;

            EmbeddingBatchResult queryEmbedding = embeddingClient.embed(
                    List.of(firstContent)
            );
            assertThat(queryEmbedding.model()).isEqualTo(EXPECTED_MODEL);
            assertThat(queryEmbedding.dimension()).isEqualTo(EXPECTED_DIMENSION);
            assertThat(queryEmbedding.profileVersion())
                    .isEqualTo(EXPECTED_PROFILE_VERSION);
            List<Float> queryVector = queryEmbedding.vectors()
                    .getFirst()
                    .values();
            cleanupQueryVector = queryVector;

            Long expectedDocumentId = documentId;
            await().atMost(AWAIT_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> {
                        List<VectorSearchHit> hits = vectorStoreClient.search(
                                queryVector,
                                embeddingVersion,
                                TOP_K
                        );
                        assertThat(hits).isNotEmpty();
                        VectorSearchHit topHit = hits.getFirst();
                        assertThat(topHit.vectorId()).isEqualTo(firstVectorId);
                        assertThat(topHit.documentId())
                                .isEqualTo(expectedDocumentId);
                        assertThat(topHit.chunkIndex())
                                .isEqualTo(firstChunkIndex);
                        assertThat(topHit.embeddingVersion())
                                .isEqualTo(embeddingVersion);
                        assertThat(Double.isFinite(topHit.similarityScore()))
                                .isTrue();
                        assertThat(topHit.similarityScore())
                                .isCloseTo(1.0D, within(0.0001D));
                    });
            System.out.printf(
                    "Real knowledge pipeline summary: model=%s, dimension=%d, "
                            + "profileVersion=%s, chunkCount=%d, top1=true%n",
                    queryEmbedding.model(),
                    queryEmbedding.dimension(),
                    queryEmbedding.profileVersion(),
                    chunkRows.size()
            );
        } catch (Throwable failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                cleanUp(
                        documentId,
                        vectorIds,
                        cleanupQueryVector,
                        cleanupEmbeddingVersion
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

    @Test
    void shouldRetrieveProcessedDocumentThroughRealEmbeddingMilvusAndMysql() throws Throwable {
        assertRealInfrastructureAndFixedProfiles();

        String testId = UUID.randomUUID().toString();
        String content = retrievalContent(testId);
        String title = "R1 real retrieval " + testId;
        String source = "R1 real retrieval smoke " + testId;
        KnowledgeDocument document = uploadedRetrievalDocument(
                testId,
                title,
                source,
                content
        );
        Long documentId = null;
        List<String> vectorIds = List.of();
        List<Float> cleanupQueryVector = null;
        String sentinelVectorId = "r1-sentinel-"
                + testId.replace("-", "");
        boolean sentinelInserted = false;
        Throwable primaryFailure = null;

        try {
            assertThat(knowledgeDocumentMapper.insertDocument(document))
                    .isEqualTo(1);
            assertThat(document.getId()).isPositive();
            documentId = document.getId();

            processingService.processDocument(documentId);

            KnowledgeDocument readyDocument = knowledgeDocumentMapper.selectById(
                    documentId
            );
            assertThat(readyDocument.getProcessingStatus())
                    .isEqualTo(KnowledgeProcessingStatus.READY);

            List<Map<String, Object>> chunkRows = queryChunkRows(documentId);
            assertThat(chunkRows).hasSize(1);
            Map<String, Object> chunkRow = chunkRows.getFirst();
            vectorIds = List.of((String) chunkRow.get("vector_id"));
            String vectorId = vectorIds.getFirst();
            String embeddingVersion = (String) chunkRow.get("embedding_version");

            EmbeddingBatchResult visibilityEmbedding = embeddingClient.embed(
                    List.of(content)
            );
            List<Float> visibilityQueryVector = visibilityEmbedding.vectors()
                    .getFirst()
                    .values();
            cleanupQueryVector = visibilityQueryVector;

            vectorStoreClient.insert(List.of(
                    new VectorWriteItem(
                            sentinelVectorId,
                            documentId,
                            999,
                            embeddingVersion,
                            visibilityQueryVector
                    )
            ));
            sentinelInserted = true;

            await().atMost(AWAIT_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(
                            vectorStoreClient.search(
                                    visibilityQueryVector,
                                    embeddingVersion,
                                    TOP_K
                            )
                    ).extracting(VectorSearchHit::vectorId)
                            .contains(vectorId, sentinelVectorId));

            String query = "  Java 中什么 Map 适合多线程并发访问？  ";
            RetrievalResult result = knowledgeRetrievalService.retrieve(
                    query,
                    RETRIEVAL_TOP_K
            );

            assertThat(result).isNotNull();
            assertThat(result.query()).isEqualTo(query.strip());
            assertThat(result.requestedTopK()).isEqualTo(RETRIEVAL_TOP_K);
            assertThat(result.embeddingModel())
                    .isEqualTo(embeddingProperties.getModel())
                    .isNotBlank();
            assertThat(result.embeddingVersion())
                    .isEqualTo(embeddingProperties.getProfileVersion())
                    .isNotBlank();
            assertThat(result.items())
                    .isNotEmpty()
                    .hasSizeLessThanOrEqualTo(RETRIEVAL_TOP_K);
            assertStrictlyIncreasingVectorRanks(result.items());
            assertThat(result.items())
                    .allMatch(item -> Double.isFinite(item.similarityScore()));

            Long expectedDocumentId = documentId;
            RetrievedChunk retrievedChunk = result.items().stream()
                    .filter(item -> item.documentId().equals(expectedDocumentId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Retrieval未命中本轮测试文档，documentId="
                                    + expectedDocumentId
                    ));

            assertThat(retrievedChunk.chunkId()).isPositive();
            assertThat(retrievedChunk.documentId()).isEqualTo(documentId);
            assertThat(retrievedChunk.documentVersion())
                    .isEqualTo(DOCUMENT_VERSION);
            assertThat(retrievedChunk.chunkIndex()).isGreaterThanOrEqualTo(1);
            assertThat(retrievedChunk.vectorId())
                    .isEqualTo(vectorId)
                    .isNotBlank();
            assertThat(retrievedChunk.title()).isEqualTo(title);
            assertThat(retrievedChunk.category())
                    .isEqualTo(KnowledgeCategory.JAVA_COLLECTION);
            assertThat(retrievedChunk.source()).isEqualTo(source);
            assertThat(retrievedChunk.content())
                    .isEqualTo(content)
                    .isNotBlank();
            assertThat(Double.isFinite(retrievedChunk.similarityScore()))
                    .isTrue();
            assertThat(retrievedChunk.vectorRank()).isGreaterThanOrEqualTo(1);

            System.out.printf(
                    "Real retrieval summary: documentId=%d, chunkId=%d, "
                            + "vectorId=%s, documentVersion=%d, rank=%d, "
                            + "similarityScore=%f%n",
                    retrievedChunk.documentId(),
                    retrievedChunk.chunkId(),
                    retrievedChunk.vectorId(),
                    retrievedChunk.documentVersion(),
                    retrievedChunk.vectorRank(),
                    retrievedChunk.similarityScore()
            );
        } catch (Throwable failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                cleanUpRetrieval(
                        documentId,
                        vectorIds,
                        cleanupQueryVector,
                        EXPECTED_PROFILE_VERSION,
                        sentinelVectorId,
                        sentinelInserted
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

    private void assertRealInfrastructureAndFixedProfiles() {
        assertThat(embeddingClient)
                .isInstanceOf(DashScopeEmbeddingClient.class);
        assertThat(vectorStoreClient)
                .isInstanceOf(MilvusVectorStoreClient.class);
        assertThat(embeddingProperties.getModel()).isEqualTo(EXPECTED_MODEL);
        assertThat(embeddingProperties.getDimension())
                .isEqualTo(EXPECTED_DIMENSION);
        assertThat(embeddingProperties.getProfileVersion())
                .isEqualTo(EXPECTED_PROFILE_VERSION);
        assertThat(milvusProperties.isEnabled()).isTrue();
        assertThat(milvusProperties.getDimension())
                .isEqualTo(EXPECTED_DIMENSION);
    }

    private void assertChunkRows(
            List<Map<String, Object>> rows,
            List<KnowledgeChunkDraft> expectedDrafts
    ) {
        assertThat(rows)
                .extracting(row -> asInt(row.get("chunk_index")))
                .containsExactlyElementsOf(
                        expectedDrafts.stream()
                                .map(KnowledgeChunkDraft::chunkIndex)
                                .toList()
                );
        assertThat(new HashSet<>(rows.stream()
                .map(row -> (String) row.get("vector_id"))
                .toList())).hasSize(rows.size());

        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> row = rows.get(index);
            KnowledgeChunkDraft expectedDraft = expectedDrafts.get(index);
            assertThat(row.get("content"))
                    .isEqualTo(expectedDraft.content());
            assertThat(row.get("content")).asString().isNotBlank();
            assertThat(row.get("token_count")).isNull();
            assertThat(row.get("vector_id")).asString().isNotBlank();
            assertThat(row.get("embedding_model"))
                    .isEqualTo(EXPECTED_MODEL);
            assertThat(row.get("embedding_version"))
                    .isEqualTo(EXPECTED_PROFILE_VERSION);
            assertThat(row.get("status")).isEqualTo("ACTIVE");
        }
    }

    private List<Map<String, Object>> queryChunkRows(Long documentId) {
        return jdbcTemplate.queryForList(
                """
                SELECT chunk_index, content, token_count, vector_id,
                       embedding_model, embedding_version, status
                FROM knowledge_chunk
                WHERE document_id = ? AND document_version = ?
                ORDER BY chunk_index
                """,
                documentId,
                DOCUMENT_VERSION
        );
    }

    private void cleanUp(
            Long documentId,
            List<String> vectorIds,
            List<Float> queryVector,
            String embeddingVersion
    ) throws Throwable {
        AtomicReference<Throwable> cleanupFailure = new AtomicReference<>();
        List<String> cleanupVectorIds = vectorIds;

        if (cleanupVectorIds.isEmpty() && documentId != null) {
            try {
                cleanupVectorIds = jdbcTemplate.queryForList(
                        """
                        SELECT vector_id
                        FROM knowledge_chunk
                        WHERE document_id = ? AND document_version = ?
                        """,
                        String.class,
                        documentId,
                        DOCUMENT_VERSION
                );
            } catch (Throwable failure) {
                cleanupFailure.set(failure);
            }
        }

        if (!cleanupVectorIds.isEmpty()) {
            List<String> idsToDelete = List.copyOf(cleanupVectorIds);
            attemptCleanup(
                    cleanupFailure,
                    () -> vectorStoreClient.deleteByVectorIds(idsToDelete)
            );
        }
        if (documentId != null) {
            attemptCleanup(cleanupFailure, () ->
                    knowledgeChunkMapper.deleteByDocumentIdAndVersion(
                            documentId,
                            DOCUMENT_VERSION
                    )
            );
            attemptCleanup(cleanupFailure, () ->
                    jdbcTemplate.update(
                            "DELETE FROM knowledge_document WHERE id = ?",
                            documentId
                    )
            );
            attemptCleanup(cleanupFailure, () -> {
                assertThat(countRows(
                        "SELECT COUNT(*) FROM knowledge_chunk WHERE document_id = ?",
                        documentId
                )).isZero();
                assertThat(countRows(
                        "SELECT COUNT(*) FROM knowledge_document WHERE id = ?",
                        documentId
                )).isZero();
            });
        }
        if (!cleanupVectorIds.isEmpty() && queryVector != null) {
            List<String> idsToVerifyAbsent = List.copyOf(cleanupVectorIds);
            attemptCleanup(cleanupFailure, () ->
                    await().atMost(AWAIT_TIMEOUT)
                            .pollInterval(POLL_INTERVAL)
                            .untilAsserted(() -> assertThat(
                                    vectorStoreClient.search(
                                            queryVector,
                                            embeddingVersion,
                                            TOP_K
                                    )
                            ).extracting(VectorSearchHit::vectorId)
                                    .doesNotContainAnyElementsOf(idsToVerifyAbsent))
            );
        }

        if (cleanupFailure.get() != null) {
            throw cleanupFailure.get();
        }
        System.out.println(
                "Real knowledge pipeline targeted cleanup completed"
        );
    }

    private void cleanUpRetrieval(
            Long documentId,
            List<String> vectorIds,
            List<Float> queryVector,
            String embeddingVersion,
            String sentinelVectorId,
            boolean sentinelInserted
    ) throws Throwable {
        AtomicReference<Throwable> cleanupFailure = new AtomicReference<>();

        attemptCleanup(cleanupFailure, () -> cleanUp(
                documentId,
                vectorIds,
                queryVector,
                embeddingVersion
        ));

        if (sentinelInserted && queryVector != null) {
            attemptCleanup(cleanupFailure, () ->
                    await().atMost(AWAIT_TIMEOUT)
                            .pollInterval(POLL_INTERVAL)
                            .untilAsserted(() -> assertThat(
                                    vectorStoreClient.search(
                                            queryVector,
                                            embeddingVersion,
                                            TOP_K
                                    )
                            ).extracting(VectorSearchHit::vectorId)
                                    .contains(sentinelVectorId))
            );
        }

        attemptCleanup(cleanupFailure, () ->
                vectorStoreClient.deleteByVectorIds(
                        List.of(sentinelVectorId)
                )
        );

        if (queryVector != null) {
            attemptCleanup(cleanupFailure, () ->
                    await().atMost(AWAIT_TIMEOUT)
                            .pollInterval(POLL_INTERVAL)
                            .untilAsserted(() -> assertThat(
                                    vectorStoreClient.search(
                                            queryVector,
                                            embeddingVersion,
                                            TOP_K
                                    )
                            ).extracting(VectorSearchHit::vectorId)
                                    .doesNotContain(sentinelVectorId))
            );
        }

        if (cleanupFailure.get() != null) {
            throw cleanupFailure.get();
        }
        System.out.println(
                "Real retrieval sentinel preservation and cleanup completed"
        );
    }

    private static void attemptCleanup(
            AtomicReference<Throwable> previousFailure,
            CleanupAction action
    ) {
        try {
            action.run();
        } catch (Throwable failure) {
            Throwable existing = previousFailure.get();
            if (existing == null) {
                previousFailure.set(failure);
            } else {
                existing.addSuppressed(failure);
            }
        }
    }

    private int countRows(String sql, Long documentId) {
        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                documentId
        );
        return count == null ? 0 : count;
    }

    private static KnowledgeDocument uploadedDocument(
            String testId,
            String content
    ) {
        return KnowledgeDocument.builder()
                .title("C1 real pipeline " + testId)
                .category(KnowledgeCategory.JAVA_BASIC)
                .fileName("c1-real-pipeline-" + testId + ".md")
                .fileType(KnowledgeFileType.MARKDOWN)
                .content(content)
                .contentHash(testId.replace("-", "")
                        + UUID.randomUUID().toString().replace("-", ""))
                .source("C1 real pipeline smoke test")
                .documentVersion(DOCUMENT_VERSION)
                .processingStatus(KnowledgeProcessingStatus.UPLOADED)
                .errorMessage(null)
                .build();
    }

    private static KnowledgeDocument uploadedRetrievalDocument(
            String testId,
            String title,
            String source,
            String content
    ) {
        return KnowledgeDocument.builder()
                .title(title)
                .category(KnowledgeCategory.JAVA_COLLECTION)
                .fileName("r1-real-retrieval-" + testId + ".md")
                .fileType(KnowledgeFileType.MARKDOWN)
                .content(content)
                .contentHash(testId.replace("-", "")
                        + UUID.randomUUID().toString().replace("-", ""))
                .source(source)
                .documentVersion(DOCUMENT_VERSION)
                .processingStatus(KnowledgeProcessingStatus.UPLOADED)
                .errorMessage(null)
                .build();
    }

    private static String retrievalContent(String testId) {
        return ("""
                R1 真实检索唯一标记 %s。ConcurrentHashMap 是 Java 中适合多线程并发访问的线程安全 Map。它通过 CAS 和更细粒度的同步机制提高并发读写能力。普通 HashMap 不保证并发写入安全，并发修改时不应把它直接作为共享可变 Map 使用。
                """).formatted(testId).strip();
    }

    private static void assertStrictlyIncreasingVectorRanks(
            List<RetrievedChunk> items
    ) {
        for (int index = 0; index < items.size() - 1; index++) {
            assertThat(items.get(index).vectorRank())
                    .isLessThan(items.get(index + 1).vectorRank());
        }
    }

    private static String stableMultiChunkContent(String testId) {
        String paragraph = """
                Java 后端面试知识验证 %s。HashMap 在 Java 8 中通常使用数组、链表和红黑树组织数据，定位桶时会结合哈希扰动与数组长度。发生哈希冲突后，元素进入同一个桶；当链表达到阈值并且数组容量满足要求时，可以树化以改善极端查询性能。Spring 事务通过代理拦截方法调用，并借助事务管理器协调数据库连接的提交与回滚。MySQL InnoDB 使用事务日志和锁机制维护一致性，实际工程中还需要关注索引、隔离级别、死锁处理与慢查询分析。
                """.formatted(testId).strip();
        return (paragraph + "\n\n").repeat(7) + paragraph;
    }

    private static int asInt(Object value) {
        return ((Number) value).intValue();
    }

    @FunctionalInterface
    private interface CleanupAction {
        void run() throws Throwable;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan("com.kun.aiinterview.knowledge.mapper")
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            MybatisAutoConfiguration.class,
            RestClientAutoConfiguration.class
    })
    @Import({
            EmbeddingConfiguration.class,
            DashScopeEmbeddingClient.class,
            MilvusConfiguration.class,
            MilvusCollectionInitializer.class,
            MilvusVectorStoreClient.class,
            KnowledgeTextChunker.class,
            KnowledgeDocumentProcessingTransactionService.class,
            KnowledgeDocumentProcessingService.class,
            KnowledgeRetrievalService.class
    })
    static class PipelineTestConfiguration {
    }
}
