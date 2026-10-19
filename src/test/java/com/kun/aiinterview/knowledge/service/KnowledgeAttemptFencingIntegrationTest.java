package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.common.exception.ConflictException;
import com.kun.aiinterview.common.recovery.StaleRecoveryProperties;
import com.kun.aiinterview.knowledge.chunk.KnowledgeTextChunker;
import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingVector;
import com.kun.aiinterview.knowledge.entity.KnowledgeChunk;
import com.kun.aiinterview.knowledge.entity.KnowledgeDocument;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.enums.KnowledgeFileType;
import com.kun.aiinterview.knowledge.enums.KnowledgeProcessingStatus;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.mapper.KnowledgeDocumentMapper;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import com.kun.aiinterview.knowledge.vector.VectorWriteItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class KnowledgeAttemptFencingIntegrationTest {
    @Autowired private KnowledgeDocumentMapper documents;
    @Autowired private KnowledgeChunkMapper chunks;
    @Autowired private KnowledgeDocumentProcessingTransactionService transactions;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    private Long documentId;
    private VectorStoreClient vectors;
    private Set<String> stored;

    @BeforeEach
    void seed() {
        var document = KnowledgeDocument.builder().title("attempt fencing")
                .category(KnowledgeCategory.JAVA_COLLECTION).fileName("attempt.md")
                .fileType(KnowledgeFileType.MARKDOWN).content("HashMap evidence")
                .contentHash(UUID.randomUUID().toString().replace("-", "").repeat(2))
                .documentVersion(1).processingStatus(KnowledgeProcessingStatus.UPLOADED).build();
        assertThat(documents.insertDocument(document)).isEqualTo(1);
        documentId = document.getId();
        stored = ConcurrentHashMap.newKeySet();
        vectors = mock(VectorStoreClient.class);
        doAnswer(i -> {
            List<VectorWriteItem> items = i.getArgument(0);
            items.forEach(item -> stored.add(item.vectorId()));
            return null;
        }).when(vectors).insert(anyList());
        doAnswer(i -> { stored.removeAll(i.getArgument(0)); return null; })
                .when(vectors).deleteByVectorIds(anyList());
    }

    @AfterEach
    void clean() {
        if (documentId != null) {
            jdbc.update("DELETE FROM knowledge_chunk WHERE document_id = ?", documentId);
            jdbc.update("DELETE FROM knowledge_document WHERE id = ?", documentId);
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "true,false,false", "false,false,true", "true,false,true", "false,true,true", "false,true,false"})
    void staleCompletionAndCompensationCannotTouchReplacement(boolean oldThrows, boolean deleteFails, boolean replacementFinishesFirst)
            throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        CountDownLatch newEntered = new CountDownLatch(1);
        CountDownLatch resumeNew = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        EmbeddingClient embedding = texts -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                await(resume);
                if (oldThrows) { throw new IllegalArgumentException("old embedding failed"); }
            } else {
                newEntered.countDown();
                await(resumeNew);
            }
            return embedding();
        };
        var service = service(embedding, transactions);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var old = executor.submit(() -> catchThrowable(() -> service.processDocument(documentId)));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            String oldAttempt = documents.selectById(documentId).getProcessingAttemptId();
            jdbc.update("UPDATE knowledge_document SET updated_at = ? WHERE id = ?",
                    LocalDateTime.now().minusMinutes(20), documentId);
            var replacement = executor.submit(() -> service.processDocument(documentId));
            assertThat(newEntered.await(10, TimeUnit.SECONDS)).isTrue();
            String newAttempt = documents.selectById(documentId).getProcessingAttemptId();
            assertThat(newAttempt).isNotEqualTo(oldAttempt);
            if (replacementFinishesFirst) {
                resumeNew.countDown();
                replacement.get(10, TimeUnit.SECONDS);
            }
            if (deleteFails) {
                doThrow(new IllegalStateException("delete unavailable")).when(vectors).deleteByVectorIds(anyList());
            }
            resume.countDown();
            Throwable failure = old.get(10, TimeUnit.SECONDS);
            assertThat(failure).isInstanceOf(oldThrows ? IllegalArgumentException.class : ConflictException.class);
            if (deleteFails) {
                assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).contains("delete unavailable");
            }
            if (!replacementFinishesFirst) {
                assertThat(documents.selectById(documentId).getProcessingStatus()).isEqualTo(KnowledgeProcessingStatus.PROCESSING);
                assertThat(chunkIds()).isEmpty();
                resumeNew.countDown();
                replacement.get(10, TimeUnit.SECONDS);
            }
            assertThat(documents.selectById(documentId).getProcessingStatus()).isEqualTo(KnowledgeProcessingStatus.READY);
            assertThat(chunkIds()).containsExactly(newAttempt + "-1");
            assertThat(stored).contains(newAttempt + "-1");
            if (!deleteFails) { assertThat(stored).containsExactly(newAttempt + "-1"); }
            assertThat(chunks.selectRetrievableByVectorIds(List.copyOf(stored), "fake", "fake-v1"))
                    .extracting(row -> row.getVectorId()).containsExactly(newAttempt + "-1");
            verify(vectors, never()).deleteByDocumentId(documentId);
        } finally {
            resume.countDown();
            resumeNew.countDown();
        }
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void mysqlRollbackCompensatesOnlyCurrentBatchAndRetryUsesNewIdentity(boolean deleteFails) {
        var failure = new IllegalStateException("injected MySQL transaction failure");
        var failingTransactions = mock(KnowledgeDocumentProcessingTransactionService.class);
        when(failingTransactions.persistChunksAndMarkReady(eq(documentId), anyList(), anyString()))
                .thenAnswer(i -> new TransactionTemplate(transactionManager).execute(status -> {
                    transactions.persistChunksAndMarkReady(documentId, i.getArgument(1), i.getArgument(2));
                    throw failure;
                }));
        stored.add("unrelated-vector");
        if (deleteFails) {
            doThrow(new IllegalStateException("compensation unavailable")).when(vectors).deleteByVectorIds(anyList());
        }
        Throwable actual = catchThrowable(() -> service(texts -> embedding(), failingTransactions).processDocument(documentId));
        assertThat(actual).isSameAs(failure);
        String failedAttempt = documents.selectById(documentId).getProcessingAttemptId();
        assertThat(documents.selectById(documentId).getProcessingStatus()).isEqualTo(KnowledgeProcessingStatus.FAILED);
        assertThat(chunkIds()).isEmpty();
        assertThat(stored).contains("unrelated-vector");
        if (!deleteFails) { assertThat(stored).containsExactly("unrelated-vector"); }
        else { assertThat(actual.getSuppressed()).extracting(Throwable::getMessage).contains("compensation unavailable"); }
        service(texts -> embedding(), transactions).processDocument(documentId);
        String retryAttempt = documents.selectById(documentId).getProcessingAttemptId();
        assertThat(retryAttempt).isNotEqualTo(failedAttempt);
        assertThat(chunkIds()).containsExactly(retryAttempt + "-1");
        assertThat(stored).contains("unrelated-vector", retryAttempt + "-1");
        verify(vectors, never()).deleteByDocumentId(documentId);
    }

    @Test
    void stalePersistCannotDeleteReplacementChunks() {
        assertThat(documents.claimProcessing(documentId, "A")).isEqualTo(1);
        jdbc.update("UPDATE knowledge_document SET updated_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(20), documentId);
        assertThat(documents.reclaimStaleProcessing(documentId, LocalDateTime.now().minusMinutes(15), "B")).isEqualTo(1);
        servicePersist("B");
        assertThat(catchThrowable(() -> servicePersist("A"))).isInstanceOf(ConflictException.class);
        assertThat(documents.markFailed(documentId, "old failure", "A")).isZero();
        assertThat(chunkIds()).containsExactly("B-1");
    }

    @Test
    void compensationBeforeLateInsertLeavesOnlyAnIneligibleOldAttemptOrphan() throws Exception {
        var entered = new CountDownLatch(1);
        var releaseTimeout = new CountDownLatch(1);
        var releaseLateWrite = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        var lateWrite = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
        doAnswer(invocation -> {
            List<VectorWriteItem> items = List.copyOf(invocation.getArgument(0));
            if (calls.incrementAndGet() == 1) {
                lateWrite.set(executor.submit(() -> {
                    await(releaseLateWrite);
                    items.forEach(item -> stored.add(item.vectorId()));
                }));
                entered.countDown();
                await(releaseTimeout);
                throw new com.kun.aiinterview.common.exception.ExternalServiceException("insert response timed out");
            }
            items.forEach(item -> stored.add(item.vectorId()));
            return null;
        }).when(vectors).insert(anyList());
        var processing = service(texts -> embedding(), transactions);
        try {
            var old = executor.submit(() -> catchThrowable(() -> processing.processDocument(documentId)));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            String a = documents.selectById(documentId).getProcessingAttemptId();
            jdbc.update("UPDATE knowledge_document SET updated_at = ? WHERE id = ?",
                    LocalDateTime.now().minusMinutes(20), documentId);
            processing.processDocument(documentId);
            String b = documents.selectById(documentId).getProcessingAttemptId();
            assertThat(b).isNotEqualTo(a);
            assertThat(chunkIds()).containsExactly(b + "-1");

            releaseTimeout.countDown();
            assertThat(old.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(com.kun.aiinterview.common.exception.ExternalServiceException.class);
            verify(vectors).deleteByVectorIds(List.of(a + "-1"));
            assertThat(stored).containsExactly(b + "-1");

            releaseLateWrite.countDown();
            lateWrite.get().get(10, TimeUnit.SECONDS);
            assertThat(stored).containsExactlyInAnyOrder(a + "-1", b + "-1");
            assertThat(documents.selectById(documentId).getProcessingAttemptId()).isEqualTo(b);
            assertThat(documents.selectById(documentId).getProcessingStatus()).isEqualTo(KnowledgeProcessingStatus.READY);
            assertThat(chunkIds()).containsExactly(b + "-1");
            assertThat(chunks.selectRetrievableByVectorIds(List.copyOf(stored), "fake", "fake-v1"))
                    .extracting(row -> row.getVectorId()).containsExactly(b + "-1");
            verify(vectors, never()).deleteByVectorIds(List.of(b + "-1"));
            verify(vectors, never()).deleteByDocumentId(documentId);
        } finally {
            releaseTimeout.countDown();
            releaseLateWrite.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void mysqlRollbackRestoresPreviousChunks() {
        assertThat(documents.claimProcessing(documentId, "old")).isEqualTo(1);
        servicePersist("old");
        jdbc.update("UPDATE knowledge_document SET processing_status = 'FAILED' WHERE id = ?", documentId);
        assertThat(documents.claimFailedProcessing(documentId, "current")).isEqualTo(1);
        var failure = new IllegalStateException("rollback after READY");
        assertThat(catchThrowable(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            servicePersist("current");
            throw failure;
        }))).isSameAs(failure);
        assertThat(chunkIds()).containsExactly("old-1");
        assertThat(documents.selectById(documentId).getProcessingStatus()).isEqualTo(KnowledgeProcessingStatus.PROCESSING);
    }

    @Test
    void retiredCleanupFailureCannotCompensateCommittedBatch() {
        assertThat(documents.claimProcessing(documentId, "old")).isEqualTo(1);
        servicePersist("old");
        stored.add("old-1");
        jdbc.update("UPDATE knowledge_document SET processing_status = 'FAILED' WHERE id = ?", documentId);
        doThrow(new IllegalStateException("retired cleanup unavailable")).when(vectors).deleteByVectorIds(List.of("old-1"));
        service(texts -> embedding(), transactions).processDocument(documentId);
        String current = documents.selectById(documentId).getProcessingAttemptId() + "-1";
        assertThat(documents.selectById(documentId).getProcessingStatus()).isEqualTo(KnowledgeProcessingStatus.READY);
        assertThat(chunkIds()).containsExactly(current);
        assertThat(stored).containsExactlyInAnyOrder("old-1", current);
        verify(vectors).deleteByVectorIds(List.of("old-1"));
        verify(vectors, never()).deleteByVectorIds(List.of(current));
    }

    @Test
    void lostMysqlCommitAcknowledgementPreservesCommittedVectors() {
        var ambiguousTransactions = mock(KnowledgeDocumentProcessingTransactionService.class);
        when(ambiguousTransactions.persistChunksAndMarkReady(eq(documentId), anyList(), anyString()))
                .thenAnswer(i -> {
                    transactions.persistChunksAndMarkReady(documentId, i.getArgument(1), i.getArgument(2));
                    throw new IllegalStateException("commit acknowledgement lost");
                });
        service(texts -> embedding(), ambiguousTransactions).processDocument(documentId);
        String current = documents.selectById(documentId).getProcessingAttemptId() + "-1";
        assertThat(documents.selectById(documentId).getProcessingStatus()).isEqualTo(KnowledgeProcessingStatus.READY);
        assertThat(stored).containsExactly(current);
        assertThat(chunkIds()).containsExactly(current);
        verify(vectors, never()).deleteByVectorIds(anyList());
    }

    @Test
    void commitConfirmationWaitsForUnresolvedTransactionBeforeCompensation() throws Exception {
        var written = new CountDownLatch(1);
        var releaseCommit = new CountDownLatch(1);
        var confirming = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        var commit = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
        var ambiguousTransactions = mock(KnowledgeDocumentProcessingTransactionService.class);
        when(ambiguousTransactions.persistChunksAndMarkReady(eq(documentId), anyList(), anyString()))
                .thenAnswer(i -> {
                    List<KnowledgeChunk> pendingChunks = i.getArgument(1);
                    String attempt = i.getArgument(2);
                    commit.set(executor.submit(() -> new TransactionTemplate(transactionManager)
                            .executeWithoutResult(status -> {
                                transactions.persistChunksAndMarkReady(documentId, pendingChunks, attempt);
                                written.countDown();
                                await(releaseCommit);
                            })));
                    assertThat(written.await(10, TimeUnit.SECONDS)).isTrue();
                    throw new IllegalStateException("commit acknowledgement unavailable while commit is pending");
                });
        var access = mock(KnowledgeDocumentMapper.class, org.mockito.AdditionalAnswers.delegatesTo(documents));
        org.mockito.Mockito.doAnswer(i -> {
            confirming.countDown();
            return documents.lockById(documentId);
        }).when(access).lockById(documentId);
        // Signal both possible read paths: reverting to a snapshot read must fail this test.
        org.mockito.Mockito.doAnswer(i -> {
            if (written.getCount() == 0) { confirming.countDown(); }
            return documents.selectById(documentId);
        }).when(access).selectById(documentId);
        var service = new KnowledgeDocumentProcessingService(access, new KnowledgeTextChunker(),
                texts -> embedding(), vectors, ambiguousTransactions, new StaleRecoveryProperties());
        try {
            var processing = executor.submit(() -> service.processDocument(documentId));
            assertThat(confirming.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> processing.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            verify(vectors, never()).deleteByVectorIds(anyList());
            releaseCommit.countDown();
            commit.get().get(10, TimeUnit.SECONDS);
            processing.get(10, TimeUnit.SECONDS);
            String current = documents.selectById(documentId).getProcessingAttemptId() + "-1";
            assertThat(documents.selectById(documentId).getProcessingStatus()).isEqualTo(KnowledgeProcessingStatus.READY);
            assertThat(chunkIds()).containsExactly(current);
            assertThat(stored).containsExactly(current);
            verify(vectors, never()).deleteByVectorIds(anyList());
        } finally {
            releaseCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void newRequestRecoversCrashedProcessingWithoutDeletingItsUnrecordedVectors() {
        assertThat(documents.claimProcessing(documentId, "crashed-worker")).isEqualTo(1);
        stored.add("crashed-worker-1"); // Simulated JVM exit after Milvus write, before MySQL persistence.
        jdbc.update("UPDATE knowledge_document SET updated_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(20), documentId);
        service(texts -> embedding(), transactions).processDocument(documentId);
        String current = documents.selectById(documentId).getProcessingAttemptId() + "-1";
        assertThat(current).isNotEqualTo("crashed-worker-1");
        assertThat(chunkIds()).containsExactly(current);
        assertThat(stored).containsExactlyInAnyOrder("crashed-worker-1", current);
        assertThat(chunks.selectRetrievableByVectorIds(List.copyOf(stored), "fake", "fake-v1"))
                .extracting(row -> row.getVectorId()).containsExactly(current);
        verify(vectors, never()).deleteByDocumentId(documentId);
        verify(vectors, never()).deleteByVectorIds(anyList());
    }

    @Test
    void unavailableCommitConfirmationRetainsOnlyOwnPossibleOrphans() {
        var failure = new IllegalStateException("transaction unavailable");
        var confirmationFailure = new IllegalStateException("confirmation unavailable");
        var unavailableTransactions = mock(KnowledgeDocumentProcessingTransactionService.class);
        when(unavailableTransactions.persistChunksAndMarkReady(eq(documentId), anyList(), anyString())).thenThrow(failure);
        var documentAccess = mock(KnowledgeDocumentMapper.class);
        when(documentAccess.selectById(documentId)).thenAnswer(i -> documents.selectById(documentId));
        when(documentAccess.lockById(documentId)).thenThrow(confirmationFailure);
        when(documentAccess.claimProcessing(eq(documentId), anyString()))
                .thenAnswer(i -> documents.claimProcessing(documentId, i.getArgument(1)));
        when(documentAccess.markFailed(eq(documentId), anyString(), anyString()))
                .thenAnswer(i -> documents.markFailed(documentId, i.getArgument(1), i.getArgument(2)));
        var service = new KnowledgeDocumentProcessingService(documentAccess, new KnowledgeTextChunker(),
                texts -> embedding(), vectors, unavailableTransactions, new StaleRecoveryProperties());
        assertThat(catchThrowable(() -> service.processDocument(documentId))).isSameAs(failure);
        assertThat(failure.getSuppressed()).contains(confirmationFailure);
        assertThat(stored).containsExactly(documents.selectById(documentId).getProcessingAttemptId() + "-1");
        assertThat(chunkIds()).isEmpty();
        assertThat(documents.selectById(documentId).getProcessingStatus()).isEqualTo(KnowledgeProcessingStatus.FAILED);
        verify(vectors, never()).deleteByVectorIds(anyList());
    }

    private void servicePersist(String attempt) {
        var chunk = KnowledgeChunk.builder().documentId(documentId).documentVersion(1).chunkIndex(1)
                .content("evidence").vectorId(attempt + "-1").embeddingModel("fake").embeddingVersion("fake-v1")
                .status(com.kun.aiinterview.knowledge.enums.KnowledgeChunkStatus.ACTIVE).build();
        transactions.persistChunksAndMarkReady(documentId, List.of(chunk), attempt);
    }

    private List<String> chunkIds() {
        return chunks.listVectorIdsByDocumentIdAndVersion(documentId, 1);
    }

    private KnowledgeDocumentProcessingService service(EmbeddingClient embedding,
            KnowledgeDocumentProcessingTransactionService tx) {
        return new KnowledgeDocumentProcessingService(documents, new KnowledgeTextChunker(), embedding,
                vectors, tx, new StaleRecoveryProperties());
    }

    private EmbeddingBatchResult embedding() {
        return new EmbeddingBatchResult("fake", "fake-v1", 1, List.of(new EmbeddingVector(0, List.of(1F))), null);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) { throw new IllegalStateException("embedding latch timed out"); }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
