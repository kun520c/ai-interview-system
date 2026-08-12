package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.knowledge.chunk.KnowledgeTextChunker;
import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingConfiguration;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import com.kun.aiinterview.knowledge.embedding.dashscope.DashScopeEmbeddingClient;
import com.kun.aiinterview.knowledge.entity.KnowledgeChunk;
import com.kun.aiinterview.knowledge.entity.KnowledgeDocument;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.enums.KnowledgeChunkStatus;
import com.kun.aiinterview.knowledge.enums.KnowledgeFileType;
import com.kun.aiinterview.knowledge.enums.KnowledgeProcessingStatus;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.mapper.KnowledgeDocumentMapper;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import com.kun.aiinterview.knowledge.vector.VectorWriteItem;
import com.kun.aiinterview.knowledge.vector.milvus.MilvusCollectionInitializer;
import com.kun.aiinterview.knowledge.vector.milvus.MilvusConfiguration;
import com.kun.aiinterview.knowledge.vector.milvus.MilvusProperties;
import com.kun.aiinterview.knowledge.vector.milvus.MilvusVectorStoreClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
        classes = RealKnowledgeDocumentProcessingFailureIntegrationTest
                .FailurePipelineTestConfiguration.class,
        initializers = ConfigDataApplicationContextInitializer.class
)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "milvus.enabled=true",
        "spring.main.web-application-type=none"
})
@Tag("real-external")
@EnabledIfEnvironmentVariable(
        named = "RUN_REAL_KNOWLEDGE_FAILURE_TEST",
        matches = "(?i)true"
)
class RealKnowledgeDocumentProcessingFailureIntegrationTest {

    private static final String OPT_IN_ENVIRONMENT_VARIABLE =
            "RUN_REAL_KNOWLEDGE_FAILURE_TEST";
    private static final String EXPECTED_MODEL = "qwen3.7-text-embedding";
    private static final int EXPECTED_DIMENSION = 1024;
    private static final String EXPECTED_PROFILE_VERSION =
            "qwen3.7-text-embedding-1024-dense-v1";
    private static final int DOCUMENT_VERSION = 1;
    private static final int TOP_K = 10;
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(400);

    @Autowired
    private KnowledgeDocumentProcessingService processingService;

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
    private MilvusProperties milvusProperties;

    @Autowired
    private MilvusVectorStoreClient realVectorStoreClient;

    @Autowired
    private FaultInjectingVectorStoreClient faultInjectingVectorStoreClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void requireExplicitRealFailureOptIn() {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(
                        System.getenv(OPT_IN_ENVIRONMENT_VARIABLE)
                ),
                "未显式启用真实知识文档失败补偿测试"
        );
    }

    @BeforeEach
    void resetFaultInjection() {
        faultInjectingVectorStoreClient.reset();
    }

    @Test
    void postWriteFaultTriggersRealTargetedMilvusCompensation() throws Throwable {
        assertRealInfrastructureAndFixedProfile();

        RuntimeException injectedException =
                new RuntimeException("test-only post-write fault");
        faultInjectingVectorStoreClient.throwAfterVisibleInsert(
                injectedException
        );
        KnowledgeDocument document = uploadedDocument(
                "post-write-" + UUID.randomUUID(),
                stableMultiChunkContent("post-write")
        );
        Long documentId = null;
        Throwable primaryFailure = null;

        try {
            insertDocument(document);
            documentId = document.getId();

            RuntimeException actual = org.junit.jupiter.api.Assertions.assertThrows(
                    RuntimeException.class,
                    () -> processingService.processDocument(document.getId())
            );

            assertThat(actual).isSameAs(injectedException);
            assertThat(faultInjectingVectorStoreClient
                    .wasRealInsertObservedBeforeFault()).isTrue();
            assertCompensationExactlyMatchesInsertedIds();
            assertDocumentFailedWithoutPipelineChunks(
                    documentId,
                    "Milvus向量写入失败",
                    0
            );
            awaitInsertedVectorsAbsent();

            System.out.printf(
                    "Real post-write compensation summary: vectorCount=%d, "
                            + "documentFailed=true, mysqlChunkCount=0%n",
                    faultInjectingVectorStoreClient.insertedItems().size()
            );
        } catch (Throwable failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                cleanUp(documentId, List.of());
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
    void mysqlUniqueConstraintFailureTriggersCrossStoreCompensation()
            throws Throwable {
        assertRealInfrastructureAndFixedProfile();

        faultInjectingVectorStoreClient.passThrough();
        String testId = UUID.randomUUID().toString();
        KnowledgeDocument document = uploadedDocument(
                "mysql-conflict-" + testId,
                stableMultiChunkContent("mysql-conflict")
        );
        Long documentId = null;
        VectorWriteItem sentinel = null;
        String fixtureVectorId = "fixture-" + testId;
        Throwable primaryFailure = null;

        try {
            insertDocument(document);
            documentId = document.getId();
            KnowledgeChunk fixture = fixtureChunk(
                    documentId,
                    fixtureVectorId
            );
            assertThat(knowledgeChunkMapper.batchInsert(List.of(fixture)))
                    .isEqualTo(1);

            sentinel = insertRealSentinel(testId);
            awaitVectorPresent(sentinel);

            RuntimeException actual = org.junit.jupiter.api.Assertions.assertThrows(
                    RuntimeException.class,
                    () -> processingService.processDocument(document.getId())
            );

            assertThat(actual)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasRootCauseInstanceOf(
                            SQLIntegrityConstraintViolationException.class
                    );
            assertThat(faultInjectingVectorStoreClient.realInsertCompleted())
                    .isTrue();
            assertThat(faultInjectingVectorStoreClient.insertedItems())
                    .hasSizeGreaterThanOrEqualTo(2);
            assertCompensationExactlyMatchesInsertedIds();
            assertThat(faultInjectingVectorStoreClient.compensatedIds())
                    .doesNotContain(fixtureVectorId, sentinel.vectorId());

            KnowledgeDocument failedDocument =
                    knowledgeDocumentMapper.selectById(documentId);
            assertThat(failedDocument.getProcessingStatus())
                    .isEqualTo(KnowledgeProcessingStatus.FAILED);
            assertThat(failedDocument.getErrorMessage())
                    .isEqualTo("知识切片持久化或文档状态更新失败");

            List<Map<String, Object>> remainingChunks = queryChunkIdentityRows(
                    documentId
            );
            assertThat(remainingChunks).singleElement().satisfies(row -> {
                assertThat(asInt(row.get("chunk_index"))).isEqualTo(1);
                assertThat(row.get("vector_id")).isEqualTo(fixtureVectorId);
                assertThat(row.get("token_count")).isNull();
            });
            assertThat(remainingChunks)
                    .extracting(row -> (String) row.get("vector_id"))
                    .doesNotContainAnyElementsOf(
                            faultInjectingVectorStoreClient.insertedIds()
                    );

            awaitInsertedVectorsAbsent();
            awaitVectorPresent(sentinel);

            System.out.printf(
                    "Real MySQL failure compensation summary: pipelineVectorCount=%d, "
                            + "fixtureRows=1, sentinelPreserved=true%n",
                    faultInjectingVectorStoreClient.insertedItems().size()
            );
        } catch (Throwable failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            List<VectorWriteItem> extraVectors = sentinel == null
                    ? List.of()
                    : List.of(sentinel);
            try {
                cleanUp(documentId, extraVectors);
            } catch (Throwable cleanupFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private void assertRealInfrastructureAndFixedProfile() {
        assertThat(embeddingClient)
                .isInstanceOf(DashScopeEmbeddingClient.class);
        assertThat(realVectorStoreClient)
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

    private void assertCompensationExactlyMatchesInsertedIds() {
        List<String> insertedIds = faultInjectingVectorStoreClient.insertedIds();
        assertThat(insertedIds).isNotEmpty();
        assertThat(new HashSet<>(insertedIds)).hasSize(insertedIds.size());
        assertThat(faultInjectingVectorStoreClient.deleteRequests())
                .containsExactly(insertedIds);
    }

    private void assertDocumentFailedWithoutPipelineChunks(
            Long documentId,
            String expectedErrorMessage,
            int expectedChunkCount
    ) {
        KnowledgeDocument failed = knowledgeDocumentMapper.selectById(documentId);
        assertThat(failed.getProcessingStatus())
                .isEqualTo(KnowledgeProcessingStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo(expectedErrorMessage);
        assertThat(countChunks(documentId)).isEqualTo(expectedChunkCount);
    }

    private void awaitInsertedVectorsAbsent() {
        List<VectorWriteItem> insertedItems =
                faultInjectingVectorStoreClient.insertedItems();
        List<String> insertedIds = faultInjectingVectorStoreClient.insertedIds();
        await().atMost(AWAIT_TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> {
                    for (VectorWriteItem item : insertedItems) {
                        assertThat(realVectorStoreClient.search(
                                item.values(),
                                item.embeddingVersion(),
                                TOP_K
                        )).extracting(VectorSearchHit::vectorId)
                                .doesNotContainAnyElementsOf(insertedIds);
                    }
                });
    }

    private VectorWriteItem insertRealSentinel(String testId) {
        EmbeddingBatchResult result = embeddingClient.embed(List.of(
                "C1 compensation sentinel " + testId
        ));
        VectorWriteItem sentinel = new VectorWriteItem(
                "sentinel-" + testId.replace("-", ""),
                positiveRandomDocumentId(),
                1,
                result.profileVersion(),
                result.vectors().getFirst().values()
        );
        realVectorStoreClient.insert(List.of(sentinel));
        return sentinel;
    }

    private void awaitVectorPresent(VectorWriteItem item) {
        await().atMost(AWAIT_TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> assertThat(
                        realVectorStoreClient.search(
                                item.values(),
                                item.embeddingVersion(),
                                TOP_K
                        )
                ).extracting(VectorSearchHit::vectorId)
                        .contains(item.vectorId()));
    }

    private void cleanUp(
            Long documentId,
            List<VectorWriteItem> extraVectors
    ) throws Throwable {
        AtomicReference<Throwable> cleanupFailure = new AtomicReference<>();
        List<VectorWriteItem> pipelineItems =
                faultInjectingVectorStoreClient.insertedItems();
        List<String> pipelineIds = faultInjectingVectorStoreClient.insertedIds();

        if (!pipelineIds.isEmpty()) {
            attemptCleanup(
                    cleanupFailure,
                    () -> realVectorStoreClient.deleteByVectorIds(pipelineIds)
            );
        }
        if (!extraVectors.isEmpty()) {
            List<String> extraIds = extraVectors.stream()
                    .map(VectorWriteItem::vectorId)
                    .toList();
            attemptCleanup(
                    cleanupFailure,
                    () -> realVectorStoreClient.deleteByVectorIds(extraIds)
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
                assertThat(countChunks(documentId)).isZero();
                assertThat(countDocuments(documentId)).isZero();
            });
        }
        if (!pipelineItems.isEmpty()) {
            attemptCleanup(cleanupFailure, () -> awaitVectorsAbsent(
                    pipelineItems,
                    pipelineIds
            ));
        }
        if (!extraVectors.isEmpty()) {
            List<String> extraIds = extraVectors.stream()
                    .map(VectorWriteItem::vectorId)
                    .toList();
            attemptCleanup(cleanupFailure, () -> awaitVectorsAbsent(
                    extraVectors,
                    extraIds
            ));
        }

        if (cleanupFailure.get() != null) {
            throw cleanupFailure.get();
        }
        System.out.println("Real failure test targeted cleanup completed");
    }

    private void awaitVectorsAbsent(
            List<VectorWriteItem> items,
            List<String> vectorIds
    ) {
        await().atMost(AWAIT_TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> {
                    for (VectorWriteItem item : items) {
                        assertThat(realVectorStoreClient.search(
                                item.values(),
                                item.embeddingVersion(),
                                TOP_K
                        )).extracting(VectorSearchHit::vectorId)
                                .doesNotContainAnyElementsOf(vectorIds);
                    }
                });
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

    private void insertDocument(KnowledgeDocument document) {
        assertThat(knowledgeDocumentMapper.insertDocument(document)).isEqualTo(1);
        assertThat(document.getId()).isPositive();
        assertThat(knowledgeDocumentMapper.selectById(document.getId())
                .getProcessingStatus()).isEqualTo(KnowledgeProcessingStatus.UPLOADED);
    }

    private KnowledgeChunk fixtureChunk(Long documentId, String vectorId) {
        return KnowledgeChunk.builder()
                .documentId(documentId)
                .documentVersion(DOCUMENT_VERSION)
                .chunkIndex(1)
                .content("C1 unique constraint fixture")
                .tokenCount(null)
                .vectorId(vectorId)
                .embeddingModel(EXPECTED_MODEL)
                .embeddingVersion(EXPECTED_PROFILE_VERSION)
                .status(KnowledgeChunkStatus.ACTIVE)
                .build();
    }

    private List<Map<String, Object>> queryChunkIdentityRows(Long documentId) {
        return jdbcTemplate.queryForList(
                """
                SELECT chunk_index, vector_id, token_count
                FROM knowledge_chunk
                WHERE document_id = ? AND document_version = ?
                ORDER BY chunk_index
                """,
                documentId,
                DOCUMENT_VERSION
        );
    }

    private int countChunks(Long documentId) {
        return countRows(
                "SELECT COUNT(*) FROM knowledge_chunk WHERE document_id = ?",
                documentId
        );
    }

    private int countDocuments(Long documentId) {
        return countRows(
                "SELECT COUNT(*) FROM knowledge_document WHERE id = ?",
                documentId
        );
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
                .title("C1 real failure " + testId)
                .category(KnowledgeCategory.JAVA_BASIC)
                .fileName("c1-real-failure-" + testId + ".md")
                .fileType(KnowledgeFileType.MARKDOWN)
                .content(content)
                .contentHash(uniqueHash())
                .source("C1 real failure integration test")
                .documentVersion(DOCUMENT_VERSION)
                .processingStatus(KnowledgeProcessingStatus.UPLOADED)
                .errorMessage(null)
                .build();
    }

    private static String stableMultiChunkContent(String marker) {
        String paragraph = """
                Java 后端补偿测试 %s。HashMap 使用数组、链表和红黑树组织数据，哈希冲突发生时元素进入同一个桶。Spring 声明式事务依赖代理和事务管理器协调连接提交或回滚。MySQL InnoDB 通过事务日志、锁和隔离级别维护一致性。知识文档处理还需要保证 MySQL 与向量数据库之间的业务补偿边界清晰，任何外部写入后的失败都只能清理由本次流程生成的数据。
                """.formatted(marker).strip();
        return (paragraph + "\n\n").repeat(8) + paragraph;
    }

    private static String uniqueHash() {
        List<String> parts = new ArrayList<>(2);
        parts.add(UUID.randomUUID().toString().replace("-", ""));
        parts.add(UUID.randomUUID().toString().replace("-", ""));
        return String.join("", parts);
    }

    private static long positiveRandomDocumentId() {
        long value = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        return value == 0 ? 1 : value;
    }

    private static int asInt(Object value) {
        return ((Number) value).intValue();
    }

    @FunctionalInterface
    private interface CleanupAction {
        void run() throws Throwable;
    }

    static final class FaultInjectingVectorStoreClient
            implements VectorStoreClient {

        private final MilvusVectorStoreClient delegate;
        private final List<List<String>> deleteRequests =
                new CopyOnWriteArrayList<>();

        private volatile List<VectorWriteItem> insertedItems = List.of();
        private volatile RuntimeException postWriteException;
        private volatile boolean realInsertCompleted;
        private volatile boolean realInsertObservedBeforeFault;

        FaultInjectingVectorStoreClient(MilvusVectorStoreClient delegate) {
            this.delegate = delegate;
        }

        void reset() {
            insertedItems = List.of();
            deleteRequests.clear();
            postWriteException = null;
            realInsertCompleted = false;
            realInsertObservedBeforeFault = false;
        }

        void throwAfterVisibleInsert(RuntimeException exception) {
            reset();
            postWriteException = exception;
        }

        void passThrough() {
            reset();
        }

        @Override
        public void insert(List<VectorWriteItem> items) {
            insertedItems = List.copyOf(items);
            delegate.insert(insertedItems);
            realInsertCompleted = true;

            RuntimeException injected = postWriteException;
            if (injected != null) {
                await().atMost(AWAIT_TIMEOUT)
                        .pollInterval(POLL_INTERVAL)
                        .untilAsserted(() -> {
                            for (VectorWriteItem item : insertedItems) {
                                assertThat(delegate.search(
                                        item.values(),
                                        item.embeddingVersion(),
                                        TOP_K
                                )).extracting(VectorSearchHit::vectorId)
                                        .contains(item.vectorId());
                            }
                        });
                realInsertObservedBeforeFault = true;
                throw injected;
            }
        }

        @Override
        public void deleteByVectorIds(List<String> vectorIds) {
            deleteRequests.add(List.copyOf(vectorIds));
            delegate.deleteByVectorIds(vectorIds);
        }

        @Override
        public void deleteByDocumentId(long documentId) {
            delegate.deleteByDocumentId(documentId);
        }

        @Override
        public List<VectorSearchHit> search(
                List<Float> queryVector,
                String embeddingVersion,
                int topK
        ) {
            return delegate.search(queryVector, embeddingVersion, topK);
        }

        List<VectorWriteItem> insertedItems() {
            return insertedItems;
        }

        List<String> insertedIds() {
            return insertedItems.stream()
                    .map(VectorWriteItem::vectorId)
                    .toList();
        }

        List<List<String>> deleteRequests() {
            return List.copyOf(deleteRequests);
        }

        List<String> compensatedIds() {
            return deleteRequests.stream()
                    .flatMap(List::stream)
                    .toList();
        }

        boolean realInsertCompleted() {
            return realInsertCompleted;
        }

        boolean wasRealInsertObservedBeforeFault() {
            return realInsertObservedBeforeFault;
        }
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
            KnowledgeDocumentProcessingService.class
    })
    static class FailurePipelineTestConfiguration {

        @Bean
        @Primary
        FaultInjectingVectorStoreClient faultInjectingVectorStoreClient(
                MilvusVectorStoreClient realVectorStoreClient
        ) {
            return new FaultInjectingVectorStoreClient(realVectorStoreClient);
        }
    }
}
