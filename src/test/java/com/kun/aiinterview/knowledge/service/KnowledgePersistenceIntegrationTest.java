package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.knowledge.entity.KnowledgeChunk;
import com.kun.aiinterview.knowledge.entity.KnowledgeDocument;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.enums.KnowledgeChunkStatus;
import com.kun.aiinterview.knowledge.enums.KnowledgeFileType;
import com.kun.aiinterview.knowledge.enums.KnowledgeProcessingStatus;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MybatisTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(KnowledgeDocumentProcessingTransactionService.class)
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("real-database")
@EnabledIfEnvironmentVariable(
        named = "RUN_REAL_KNOWLEDGE_DB_TEST",
        matches = "(?i)true"
)
class KnowledgePersistenceIntegrationTest {

    private static final String OPT_IN_ENVIRONMENT_VARIABLE =
            "RUN_REAL_KNOWLEDGE_DB_TEST";
    private static final String EMBEDDING_MODEL = "c1-db-test-model";
    private static final String EMBEDDING_VERSION = "c1-db-test-profile-v1";

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Autowired
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Autowired
    private KnowledgeDocumentProcessingTransactionService transactionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Set<Long> createdDocumentIds = new LinkedHashSet<>();

    @BeforeAll
    static void requireExplicitRealDatabaseOptIn() {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(
                        System.getenv(OPT_IN_ENVIRONMENT_VARIABLE)
                ),
                "未显式启用真实知识库数据库测试"
        );
    }

    @AfterEach
    void cleanUpCreatedData() {
        for (Long documentId : createdDocumentIds) {
            jdbcTemplate.update(
                    "DELETE FROM knowledge_chunk WHERE document_id = ?",
                    documentId
            );
        }
        for (Long documentId : createdDocumentIds) {
            jdbcTemplate.update(
                    "DELETE FROM knowledge_document WHERE id = ?",
                    documentId
            );
        }
        for (Long documentId : createdDocumentIds) {
            assertEquals(0, countChunks(documentId));
            assertEquals(0, countDocuments(documentId));
        }
        createdDocumentIds.clear();
    }

    @Test
    void insertSelectAndClaimCasExecuteAgainstRealMysql() {
        KnowledgeDocument inserted = insertUploadedDocument();

        KnowledgeDocument selected = knowledgeDocumentMapper.selectById(
                inserted.getId()
        );
        assertAll(
                () -> assertEquals(inserted.getId(), selected.getId()),
                () -> assertEquals(inserted.getTitle(), selected.getTitle()),
                () -> assertEquals(inserted.getContent(), selected.getContent()),
                () -> assertEquals(
                        inserted.getContentHash(),
                        selected.getContentHash()
                ),
                () -> assertEquals(1, selected.getDocumentVersion()),
                () -> assertEquals(
                        KnowledgeProcessingStatus.UPLOADED,
                        selected.getProcessingStatus()
                )
        );

        assertEquals(
                1,
                knowledgeDocumentMapper.claimProcessing(inserted.getId())
        );
        KnowledgeDocument claimed = knowledgeDocumentMapper.selectById(
                inserted.getId()
        );
        assertAll(
                () -> assertEquals(
                        KnowledgeProcessingStatus.PROCESSING,
                        claimed.getProcessingStatus()
                ),
                () -> assertNull(claimed.getErrorMessage())
        );

        assertEquals(
                0,
                knowledgeDocumentMapper.claimProcessing(inserted.getId())
        );
        assertEquals(
                KnowledgeProcessingStatus.PROCESSING,
                knowledgeDocumentMapper.selectById(inserted.getId())
                        .getProcessingStatus()
        );
    }

    @Test
    void markReadyRequiresProcessingStateAgainstRealMysql() {
        KnowledgeDocument processingDocument = insertUploadedDocument();
        KnowledgeDocument uploadedDocument = insertUploadedDocument();
        assertEquals(
                1,
                knowledgeDocumentMapper.claimProcessing(
                        processingDocument.getId()
                )
        );

        assertEquals(
                1,
                knowledgeDocumentMapper.markReady(processingDocument.getId())
        );
        assertEquals(
                KnowledgeProcessingStatus.READY,
                knowledgeDocumentMapper.selectById(processingDocument.getId())
                        .getProcessingStatus()
        );
        assertEquals(
                0,
                knowledgeDocumentMapper.markReady(processingDocument.getId())
        );

        assertEquals(
                0,
                knowledgeDocumentMapper.markReady(uploadedDocument.getId())
        );
        assertEquals(
                KnowledgeProcessingStatus.UPLOADED,
                knowledgeDocumentMapper.selectById(uploadedDocument.getId())
                        .getProcessingStatus()
        );
    }

    @Test
    void markFailedRequiresProcessingStateAgainstRealMysql() {
        KnowledgeDocument processingDocument = insertUploadedDocument();
        KnowledgeDocument uploadedDocument = insertUploadedDocument();
        KnowledgeDocument readyDocument = insertUploadedDocument();
        assertEquals(
                1,
                knowledgeDocumentMapper.claimProcessing(
                        processingDocument.getId()
                )
        );
        assertEquals(
                1,
                knowledgeDocumentMapper.claimProcessing(readyDocument.getId())
        );
        assertEquals(
                1,
                knowledgeDocumentMapper.markReady(readyDocument.getId())
        );

        assertEquals(
                1,
                knowledgeDocumentMapper.markFailed(
                        processingDocument.getId(),
                        "c1 integration test failure"
                )
        );
        KnowledgeDocument failed = knowledgeDocumentMapper.selectById(
                processingDocument.getId()
        );
        assertAll(
                () -> assertEquals(
                        KnowledgeProcessingStatus.FAILED,
                        failed.getProcessingStatus()
                ),
                () -> assertEquals(
                        "c1 integration test failure",
                        failed.getErrorMessage()
                )
        );

        assertEquals(
                0,
                knowledgeDocumentMapper.markFailed(
                        uploadedDocument.getId(),
                        "must not be stored"
                )
        );
        assertEquals(
                0,
                knowledgeDocumentMapper.markFailed(
                        readyDocument.getId(),
                        "must not be stored"
                )
        );
        assertNull(
                knowledgeDocumentMapper.selectById(uploadedDocument.getId())
                        .getErrorMessage()
        );
        assertNull(
                knowledgeDocumentMapper.selectById(readyDocument.getId())
                        .getErrorMessage()
        );
    }

    @Test
    void batchInsertMapsNullableTokensAndDeleteUsesDocumentAndVersion() {
        KnowledgeDocument documentA = insertUploadedDocument();
        KnowledgeDocument documentB = insertUploadedDocument();
        List<KnowledgeChunk> chunks = List.of(
                chunk(documentA.getId(), 1, 1),
                chunk(documentA.getId(), 1, 2),
                chunk(documentA.getId(), 2, 1),
                chunk(documentB.getId(), 1, 1)
        );

        assertEquals(chunks.size(), knowledgeChunkMapper.batchInsert(chunks));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT document_id, document_version, chunk_index, content,
                       token_count, vector_id, embedding_model,
                       embedding_version, status
                FROM knowledge_chunk
                WHERE document_id IN (?, ?)
                ORDER BY document_id, document_version, chunk_index
                """,
                documentA.getId(),
                documentB.getId()
        );
        assertThat(rows).hasSize(chunks.size());
        for (KnowledgeChunk chunk : chunks) {
            Map<String, Object> row = rows.stream()
                    .filter(candidate -> asLong(candidate.get("document_id"))
                            == chunk.getDocumentId())
                    .filter(candidate -> asInt(candidate.get("document_version"))
                            == chunk.getDocumentVersion())
                    .filter(candidate -> asInt(candidate.get("chunk_index"))
                            == chunk.getChunkIndex())
                    .findFirst()
                    .orElseThrow();
            assertAll(
                    () -> assertEquals(chunk.getContent(), row.get("content")),
                    () -> assertNull(row.get("token_count")),
                    () -> assertEquals(chunk.getVectorId(), row.get("vector_id")),
                    () -> assertEquals(
                            EMBEDDING_MODEL,
                            row.get("embedding_model")
                    ),
                    () -> assertEquals(
                            EMBEDDING_VERSION,
                            row.get("embedding_version")
                    ),
                    () -> assertEquals("ACTIVE", row.get("status"))
            );
        }

        assertEquals(
                2,
                knowledgeChunkMapper.deleteByDocumentIdAndVersion(
                        documentA.getId(),
                        1
                )
        );
        assertAll(
                () -> assertEquals(0, countChunks(documentA.getId(), 1)),
                () -> assertEquals(1, countChunks(documentA.getId(), 2)),
                () -> assertEquals(1, countChunks(documentB.getId(), 1))
        );
    }

    @Test
    void transactionServiceCommitsChunksAndReadyTogetherAgainstRealMysql() {
        KnowledgeDocument document = insertUploadedDocument();
        assertEquals(
                1,
                knowledgeDocumentMapper.claimProcessing(document.getId())
        );
        List<KnowledgeChunk> chunks = List.of(
                chunk(document.getId(), 1, 1),
                chunk(document.getId(), 1, 2),
                chunk(document.getId(), 1, 3)
        );

        transactionService.persistChunksAndMarkReady(document.getId(), chunks);

        assertAll(
                () -> assertEquals(3, countChunks(document.getId())),
                () -> assertEquals(
                        KnowledgeProcessingStatus.READY,
                        knowledgeDocumentMapper.selectById(document.getId())
                                .getProcessingStatus()
                )
        );
    }

    @Test
    void transactionServiceRollsBackBatchWhenMarkReadyRejectsRealState() {
        KnowledgeDocument uploadedDocument = insertUploadedDocument();
        List<KnowledgeChunk> chunks = List.of(
                chunk(uploadedDocument.getId(), 1, 1),
                chunk(uploadedDocument.getId(), 1, 2)
        );

        assertThrows(
                BusinessException.class,
                () -> transactionService.persistChunksAndMarkReady(
                        uploadedDocument.getId(),
                        chunks
                )
        );

        assertAll(
                () -> assertEquals(0, countChunks(uploadedDocument.getId())),
                () -> assertEquals(
                        KnowledgeProcessingStatus.UPLOADED,
                        knowledgeDocumentMapper.selectById(
                                uploadedDocument.getId()
                        ).getProcessingStatus()
                )
        );
    }

    private KnowledgeDocument insertUploadedDocument() {
        String uniqueValue = UUID.randomUUID().toString();
        KnowledgeDocument document = KnowledgeDocument.builder()
                .title("C1 database integration " + uniqueValue)
                .category(KnowledgeCategory.JAVA_BASIC)
                .fileName("c1-" + uniqueValue + ".md")
                .fileType(KnowledgeFileType.MARKDOWN)
                .content("# C1 integration content\n" + uniqueValue)
                .contentHash(uniqueHash())
                .source("C1 integration test")
                .documentVersion(1)
                .processingStatus(KnowledgeProcessingStatus.UPLOADED)
                .errorMessage(null)
                .build();

        assertEquals(1, knowledgeDocumentMapper.insertDocument(document));
        assertNotNull(document.getId());
        createdDocumentIds.add(document.getId());
        return document;
    }

    private KnowledgeChunk chunk(
            Long documentId,
            int documentVersion,
            int chunkIndex
    ) {
        String uniqueValue = UUID.randomUUID().toString();
        return KnowledgeChunk.builder()
                .documentId(documentId)
                .documentVersion(documentVersion)
                .chunkIndex(chunkIndex)
                .content("C1 chunk " + uniqueValue)
                .tokenCount(null)
                .vectorId(uniqueValue)
                .embeddingModel(EMBEDDING_MODEL)
                .embeddingVersion(EMBEDDING_VERSION)
                .status(KnowledgeChunkStatus.ACTIVE)
                .build();
    }

    private int countDocuments(Long documentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_document WHERE id = ?",
                Integer.class,
                documentId
        );
        return count == null ? 0 : count;
    }

    private int countChunks(Long documentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_chunk WHERE document_id = ?",
                Integer.class,
                documentId
        );
        return count == null ? 0 : count;
    }

    private int countChunks(Long documentId, int documentVersion) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM knowledge_chunk
                WHERE document_id = ? AND document_version = ?
                """,
                Integer.class,
                documentId,
                documentVersion
        );
        return count == null ? 0 : count;
    }

    private static int asInt(Object value) {
        return ((Number) value).intValue();
    }

    private static long asLong(Object value) {
        return ((Number) value).longValue();
    }

    private static String uniqueHash() {
        List<String> parts = new ArrayList<>(2);
        parts.add(UUID.randomUUID().toString().replace("-", ""));
        parts.add(UUID.randomUUID().toString().replace("-", ""));
        return String.join("", parts);
    }
}
