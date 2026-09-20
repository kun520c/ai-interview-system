package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.common.exception.ConflictException;
import com.kun.aiinterview.common.exception.ResourceNotFoundException;
import com.kun.aiinterview.common.recovery.StaleRecoveryProperties;
import com.kun.aiinterview.knowledge.chunk.KnowledgeChunkDraft;
import com.kun.aiinterview.knowledge.chunk.KnowledgeTextChunker;
import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingVector;
import com.kun.aiinterview.knowledge.entity.KnowledgeChunk;
import com.kun.aiinterview.knowledge.entity.KnowledgeDocument;
import com.kun.aiinterview.knowledge.enums.KnowledgeChunkStatus;
import com.kun.aiinterview.knowledge.enums.KnowledgeProcessingStatus;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.mapper.KnowledgeDocumentMapper;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import com.kun.aiinterview.knowledge.vector.VectorWriteItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentProcessingServiceTest {

    private static final long DOCUMENT_ID = 101L;
    private static final int DOCUMENT_VERSION = 3;
    private static final String DOCUMENT_CONTENT = "normalized knowledge content";
    private static final String EMBEDDING_MODEL = "test-embedding-model";
    private static final String EMBEDDING_VERSION = "test-profile-v1";

    private static final List<KnowledgeChunkDraft> DRAFTS = List.of(
            new KnowledgeChunkDraft(1, "chunk one", 9),
            new KnowledgeChunkDraft(2, "chunk two", 9),
            new KnowledgeChunkDraft(3, "chunk three", 11)
    );

    private static final List<Float> VECTOR_ZERO = List.of(0.10F, 0.11F);
    private static final List<Float> VECTOR_ONE = List.of(0.20F, 0.21F);
    private static final List<Float> VECTOR_TWO = List.of(0.30F, 0.31F);

    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Mock
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Mock
    private KnowledgeTextChunker knowledgeTextChunker;

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private VectorStoreClient vectorStoreClient;

    @Mock
    private KnowledgeDocumentProcessingTransactionService transactionService;

    private KnowledgeDocumentProcessingService processingService;

    @BeforeEach
    void setUp() {
        processingService = new KnowledgeDocumentProcessingService(
                knowledgeDocumentMapper,
                knowledgeChunkMapper,
                knowledgeTextChunker,
                embeddingClient,
                vectorStoreClient,
                transactionService,
                new StaleRecoveryProperties()
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void givenInvalidDocumentId_whenProcessing_thenRejectsBeforeAnyDependencyCall(
            Long documentId
    ) {
        assertThrows(
                BusinessException.class,
                () -> processingService.processDocument(documentId)
        );

        verifyNoInteractions(
                knowledgeDocumentMapper,
                knowledgeChunkMapper,
                knowledgeTextChunker,
                embeddingClient,
                vectorStoreClient,
                transactionService
        );
    }

    @Test
    void givenMissingDocument_whenProcessing_thenStopsWithoutMarkingFailed() {
        when(knowledgeDocumentMapper.selectById(DOCUMENT_ID)).thenReturn(null);

        assertThrows(
                ResourceNotFoundException.class,
                () -> processingService.processDocument(DOCUMENT_ID)
        );

        verify(knowledgeDocumentMapper).selectById(DOCUMENT_ID);
        verify(knowledgeDocumentMapper, never()).claimProcessing(DOCUMENT_ID);
        verify(knowledgeDocumentMapper, never())
                .markFailed(eq(DOCUMENT_ID), anyString());
        verifyNoInteractions(
                knowledgeChunkMapper,
                knowledgeTextChunker,
                embeddingClient,
                vectorStoreClient,
                transactionService
        );
    }

    @Test
    void givenClaimRejected_whenProcessing_thenStopsWithoutMarkingFailed() {
        when(knowledgeDocumentMapper.selectById(DOCUMENT_ID))
                .thenReturn(uploadedDocument(), uploadedDocument());
        when(knowledgeDocumentMapper.claimProcessing(DOCUMENT_ID)).thenReturn(0);

        assertThrows(
                ConflictException.class,
                () -> processingService.processDocument(DOCUMENT_ID)
        );

        verify(knowledgeDocumentMapper).claimProcessing(DOCUMENT_ID);
        verify(knowledgeDocumentMapper, never())
                .markFailed(eq(DOCUMENT_ID), anyString());
        verify(vectorStoreClient, never()).deleteByDocumentId(DOCUMENT_ID);
        verifyNoInteractions(
                knowledgeChunkMapper,
                knowledgeTextChunker,
                embeddingClient,
                transactionService
        );
    }

    @Test
    void givenValidDocumentAndOutOfOrderVectors_whenProcessing_thenPersistsMatchedItems() {
        EmbeddingBatchResult embeddingResult = outOfOrderEmbeddingResult();
        stubThroughEmbedding(embeddingResult);

        processingService.processDocument(DOCUMENT_ID);

        ArgumentCaptor<List<VectorWriteItem>> vectorItemsCaptor = listCaptor();
        ArgumentCaptor<List<KnowledgeChunk>> chunksCaptor = listCaptor();
        InOrder calls = inOrder(
                knowledgeDocumentMapper,
                vectorStoreClient,
                knowledgeChunkMapper,
                knowledgeTextChunker,
                embeddingClient,
                transactionService
        );
        calls.verify(knowledgeDocumentMapper).selectById(DOCUMENT_ID);
        calls.verify(knowledgeDocumentMapper).claimProcessing(DOCUMENT_ID);
        calls.verify(knowledgeDocumentMapper).selectById(DOCUMENT_ID);
        calls.verify(vectorStoreClient).deleteByDocumentId(DOCUMENT_ID);
        calls.verify(knowledgeChunkMapper).deleteByDocumentIdAndVersion(
                DOCUMENT_ID,
                DOCUMENT_VERSION
        );
        calls.verify(knowledgeTextChunker).split(DOCUMENT_CONTENT);
        calls.verify(embeddingClient).embed(List.of(
                "chunk one",
                "chunk two",
                "chunk three"
        ));
        calls.verify(vectorStoreClient).insert(vectorItemsCaptor.capture());
        calls.verify(transactionService)
                .persistChunksAndMarkReady(
                        eq(DOCUMENT_ID),
                        chunksCaptor.capture()
                );

        List<KnowledgeChunk> chunks = chunksCaptor.getValue();
        List<VectorWriteItem> vectorItems = vectorItemsCaptor.getValue();
        List<List<Float>> expectedVectors = List.of(
                VECTOR_ZERO,
                VECTOR_ONE,
                VECTOR_TWO
        );
        Set<String> uniqueVectorIds = new HashSet<>();

        assertThat(chunks).hasSize(DRAFTS.size());
        assertThat(vectorItems).hasSize(DRAFTS.size());
        for (int index = 0; index < DRAFTS.size(); index++) {
            KnowledgeChunkDraft draft = DRAFTS.get(index);
            KnowledgeChunk chunk = chunks.get(index);
            VectorWriteItem vectorItem = vectorItems.get(index);

            assertThat(chunk.getDocumentId()).isEqualTo(DOCUMENT_ID);
            assertThat(chunk.getDocumentVersion()).isEqualTo(DOCUMENT_VERSION);
            assertThat(chunk.getChunkIndex()).isEqualTo(draft.chunkIndex());
            assertThat(chunk.getContent()).isEqualTo(draft.content());
            assertThat(chunk.getTokenCount()).isNull();
            assertThat(chunk.getEmbeddingModel()).isEqualTo(EMBEDDING_MODEL);
            assertThat(chunk.getEmbeddingVersion()).isEqualTo(EMBEDDING_VERSION);
            assertThat(chunk.getStatus()).isEqualTo(KnowledgeChunkStatus.ACTIVE);
            assertThat(chunk.getVectorId()).isNotBlank();
            assertDoesNotThrow(() -> UUID.fromString(chunk.getVectorId()));
            assertThat(uniqueVectorIds.add(chunk.getVectorId())).isTrue();

            assertThat(vectorItem.vectorId()).isEqualTo(chunk.getVectorId());
            assertThat(vectorItem.documentId()).isEqualTo(DOCUMENT_ID);
            assertThat(vectorItem.chunkIndex()).isEqualTo(draft.chunkIndex());
            assertThat(vectorItem.embeddingVersion()).isEqualTo(EMBEDDING_VERSION);
            assertThat(vectorItem.values()).containsExactlyElementsOf(
                    expectedVectors.get(index)
            );
        }

        verify(vectorStoreClient, never()).deleteByVectorIds(anyList());
        verify(knowledgeDocumentMapper, never())
                .markFailed(eq(DOCUMENT_ID), anyString());
    }

    @Test
    void givenChunkerFailure_whenProcessing_thenMarksFailedAndRethrowsOriginal() {
        RuntimeException originalException = new RuntimeException("chunk failure");
        stubClaimedDocument();
        when(knowledgeTextChunker.split(DOCUMENT_CONTENT))
                .thenThrow(originalException);
        stubSuccessfulMarkFailed();

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> processingService.processDocument(DOCUMENT_ID)
        );

        assertSame(originalException, actual);
        verify(vectorStoreClient).deleteByDocumentId(DOCUMENT_ID);
        verify(vectorStoreClient, never()).insert(anyList());
        verify(vectorStoreClient, never()).deleteByVectorIds(anyList());
        verifyNoInteractions(embeddingClient, transactionService);
        verify(knowledgeDocumentMapper)
                .markFailed(DOCUMENT_ID, "知识文档切片失败");
    }

    @Test
    void givenEmbeddingFailure_whenProcessing_thenDoesNotCompensateMilvus() {
        RuntimeException originalException = new RuntimeException("embedding failure");
        stubThroughChunking();
        when(embeddingClient.embed(chunkTexts())).thenThrow(originalException);
        stubSuccessfulMarkFailed();

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> processingService.processDocument(DOCUMENT_ID)
        );

        assertSame(originalException, actual);
        verifyNoInsertOrCompensation();
        verify(knowledgeDocumentMapper)
                .markFailed(DOCUMENT_ID, "知识文档Embedding生成失败");
    }

    @Test
    void givenWrongEmbeddingCount_whenProcessing_thenFailsBeforeMilvus() {
        EmbeddingBatchResult incompleteResult = embeddingResult(List.of(
                vector(0, VECTOR_ZERO),
                vector(1, VECTOR_ONE)
        ));
        stubThroughEmbedding(incompleteResult);
        stubSuccessfulMarkFailed();

        assertThrows(
                BusinessException.class,
                () -> processingService.processDocument(DOCUMENT_ID)
        );

        verifyNoInsertOrCompensation();
        verify(knowledgeDocumentMapper)
                .markFailed(DOCUMENT_ID, "知识文档Embedding生成失败");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidIndexResults")
    void givenInvalidEmbeddingIndex_whenProcessing_thenFailsBeforeMilvus(
            String scenario,
            EmbeddingBatchResult invalidResult
    ) {
        stubThroughEmbedding(invalidResult);
        stubSuccessfulMarkFailed();

        assertThrows(
                BusinessException.class,
                () -> processingService.processDocument(DOCUMENT_ID),
                scenario
        );

        verifyNoInsertOrCompensation();
        verify(knowledgeDocumentMapper)
                .markFailed(DOCUMENT_ID, "知识文档Embedding生成失败");
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenMissingEmbeddingIndex_whenProcessing_thenFailsBeforeMilvus() {
        List<EmbeddingVector> inconsistentVectors = mock(List.class);
        Iterator<EmbeddingVector> incompleteIterator = List.of(
                vector(0, VECTOR_ZERO),
                vector(1, VECTOR_ONE)
        ).iterator();
        when(inconsistentVectors.size()).thenReturn(DRAFTS.size());
        when(inconsistentVectors.iterator()).thenReturn(incompleteIterator);
        EmbeddingBatchResult inconsistentResult = mock(EmbeddingBatchResult.class);
        when(inconsistentResult.vectors()).thenReturn(inconsistentVectors);
        stubThroughEmbedding(inconsistentResult);
        stubSuccessfulMarkFailed();

        assertThrows(
                BusinessException.class,
                () -> processingService.processDocument(DOCUMENT_ID)
        );

        verifyNoInsertOrCompensation();
        verify(knowledgeDocumentMapper)
                .markFailed(DOCUMENT_ID, "知识文档Embedding生成失败");
    }

    @Test
    void givenMilvusInsertFailure_whenProcessing_thenCompensatesAllAttemptedIds() {
        RuntimeException originalException = new RuntimeException("insert timeout");
        stubThroughEmbedding(outOfOrderEmbeddingResult());
        doThrow(originalException).when(vectorStoreClient).insert(anyList());
        stubSuccessfulMarkFailed();

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> processingService.processDocument(DOCUMENT_ID)
        );

        assertSame(originalException, actual);
        assertCompensatedIdsMatchAttemptedInsert();
        verifyNoInteractions(transactionService);
        verify(knowledgeDocumentMapper)
                .markFailed(DOCUMENT_ID, "Milvus向量写入失败");
    }

    @Test
    void givenMysqlTransactionFailure_whenProcessing_thenCompensatesMilvus() {
        RuntimeException originalException = new RuntimeException("mysql failure");
        stubThroughEmbedding(outOfOrderEmbeddingResult());
        doThrow(originalException)
                .when(transactionService)
                .persistChunksAndMarkReady(eq(DOCUMENT_ID), anyList());
        stubSuccessfulMarkFailed();

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> processingService.processDocument(DOCUMENT_ID)
        );

        assertSame(originalException, actual);
        assertCompensatedIdsMatchAttemptedInsert();
        verify(knowledgeDocumentMapper).markFailed(
                DOCUMENT_ID,
                "知识切片持久化或文档状态更新失败"
        );
    }

    @Test
    void givenCompensationFailure_whenProcessing_thenKeepsOriginalAsPrimaryException() {
        RuntimeException originalException = new RuntimeException("mysql failure");
        RuntimeException compensationException =
                new RuntimeException("compensation failure");
        stubThroughEmbedding(outOfOrderEmbeddingResult());
        doThrow(originalException)
                .when(transactionService)
                .persistChunksAndMarkReady(eq(DOCUMENT_ID), anyList());
        doThrow(compensationException)
                .when(vectorStoreClient)
                .deleteByVectorIds(anyList());
        stubSuccessfulMarkFailed();

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> processingService.processDocument(DOCUMENT_ID)
        );

        assertSame(originalException, actual);
        assertThat(actual.getSuppressed()).containsExactly(compensationException);
        verify(vectorStoreClient).deleteByVectorIds(anyList());
        verify(knowledgeDocumentMapper).markFailed(
                DOCUMENT_ID,
                "知识切片持久化或文档状态更新失败"
        );
        verify(knowledgeDocumentMapper).claimProcessing(DOCUMENT_ID);
        verify(knowledgeDocumentMapper, never()).claimFailedProcessing(DOCUMENT_ID);
    }

    @Test
    void givenReadyDocument_whenProcessing_thenRejectsWithoutCleanupOrInsert() {
        when(knowledgeDocumentMapper.selectById(DOCUMENT_ID))
                .thenReturn(document(KnowledgeProcessingStatus.READY, null));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> processingService.processDocument(DOCUMENT_ID)
        );

        assertThat(exception.getMessage()).isEqualTo("文档已处理完成，不能重复处理");
        verify(knowledgeDocumentMapper, never()).claimProcessing(DOCUMENT_ID);
        verify(knowledgeDocumentMapper, never()).claimFailedProcessing(DOCUMENT_ID);
        verify(knowledgeDocumentMapper, never())
                .reclaimStaleProcessing(eq(DOCUMENT_ID), any());
        verify(vectorStoreClient, never()).deleteByDocumentId(DOCUMENT_ID);
        verify(vectorStoreClient, never()).insert(anyList());
        verifyNoInteractions(
                knowledgeChunkMapper,
                knowledgeTextChunker,
                embeddingClient,
                transactionService
        );
    }

    @Test
    void givenFreshProcessingDocument_whenProcessing_thenConflictsWithoutCleanup() {
        when(knowledgeDocumentMapper.selectById(DOCUMENT_ID))
                .thenReturn(processingDocument(LocalDateTime.now()));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> processingService.processDocument(DOCUMENT_ID)
        );

        assertThat(exception.getMessage()).isEqualTo("文档正在处理中，请稍后重试");
        verify(knowledgeDocumentMapper, never())
                .reclaimStaleProcessing(eq(DOCUMENT_ID), any());
        verify(knowledgeDocumentMapper, never()).claimProcessing(DOCUMENT_ID);
        verify(knowledgeDocumentMapper, never()).claimFailedProcessing(DOCUMENT_ID);
        verify(vectorStoreClient, never()).deleteByDocumentId(DOCUMENT_ID);
        verify(vectorStoreClient, never()).insert(anyList());
        verifyNoInteractions(
                knowledgeChunkMapper,
                knowledgeTextChunker,
                embeddingClient,
                transactionService
        );
    }

    @Test
    void givenFailedDocument_whenRetrying_thenDeletesByDocumentIdBeforeInsert() {
        stubFailedRetry(outOfOrderEmbeddingResult());

        processingService.processDocument(DOCUMENT_ID);

        InOrder order = inOrder(
                knowledgeDocumentMapper,
                vectorStoreClient,
                knowledgeChunkMapper,
                knowledgeTextChunker,
                embeddingClient,
                transactionService
        );
        order.verify(knowledgeDocumentMapper).selectById(DOCUMENT_ID);
        order.verify(knowledgeDocumentMapper).claimFailedProcessing(DOCUMENT_ID);
        order.verify(knowledgeDocumentMapper).selectById(DOCUMENT_ID);
        order.verify(vectorStoreClient).deleteByDocumentId(DOCUMENT_ID);
        order.verify(knowledgeChunkMapper).deleteByDocumentIdAndVersion(
                DOCUMENT_ID,
                DOCUMENT_VERSION
        );
        order.verify(knowledgeTextChunker).split(DOCUMENT_CONTENT);
        order.verify(embeddingClient).embed(chunkTexts());
        order.verify(vectorStoreClient).insert(anyList());
        order.verify(transactionService).persistChunksAndMarkReady(
                eq(DOCUMENT_ID),
                anyList()
        );
        verify(knowledgeDocumentMapper, never()).claimProcessing(DOCUMENT_ID);
        verify(vectorStoreClient, never()).deleteByVectorIds(anyList());
    }

    @Test
    void givenStaleProcessingDocument_whenRecovering_thenReclaimsAndDeletesByDocumentIdBeforeInsert() {
        stubStaleProcessingRetry(outOfOrderEmbeddingResult());

        processingService.processDocument(DOCUMENT_ID);

        InOrder order = inOrder(
                knowledgeDocumentMapper,
                vectorStoreClient
        );
        order.verify(knowledgeDocumentMapper).selectById(DOCUMENT_ID);
        order.verify(knowledgeDocumentMapper).reclaimStaleProcessing(
                eq(DOCUMENT_ID),
                any()
        );
        order.verify(knowledgeDocumentMapper).selectById(DOCUMENT_ID);
        order.verify(vectorStoreClient).deleteByDocumentId(DOCUMENT_ID);
        order.verify(vectorStoreClient).insert(anyList());
        verify(knowledgeDocumentMapper, never()).claimProcessing(DOCUMENT_ID);
        verify(knowledgeDocumentMapper, never()).claimFailedProcessing(DOCUMENT_ID);
    }

    @Test
    void givenCompensationDeleteFailure_whenRetryingFailedDocument_thenCleansByDocumentIdBeforeNewInsert() {
        RuntimeException originalException = new RuntimeException("mysql failure");
        RuntimeException compensationException =
                new RuntimeException("compensation failure");
        stubUploadedThenFailedRetry(outOfOrderEmbeddingResult());
        doThrow(originalException)
                .doNothing()
                .when(transactionService)
                .persistChunksAndMarkReady(eq(DOCUMENT_ID), anyList());
        doThrow(compensationException)
                .when(vectorStoreClient)
                .deleteByVectorIds(anyList());
        stubSuccessfulMarkFailed();

        RuntimeException first = assertThrows(
                RuntimeException.class,
                () -> processingService.processDocument(DOCUMENT_ID)
        );
        assertSame(originalException, first);
        assertThat(first.getSuppressed()).containsExactly(compensationException);

        processingService.processDocument(DOCUMENT_ID);

        InOrder order = inOrder(vectorStoreClient, transactionService);
        order.verify(vectorStoreClient).deleteByDocumentId(DOCUMENT_ID);
        order.verify(vectorStoreClient).insert(anyList());
        order.verify(transactionService).persistChunksAndMarkReady(
                eq(DOCUMENT_ID),
                anyList()
        );
        order.verify(vectorStoreClient).deleteByVectorIds(anyList());
        order.verify(vectorStoreClient).deleteByDocumentId(DOCUMENT_ID);
        order.verify(vectorStoreClient).insert(anyList());
        order.verify(transactionService).persistChunksAndMarkReady(
                eq(DOCUMENT_ID),
                anyList()
        );
        verify(knowledgeDocumentMapper).claimFailedProcessing(DOCUMENT_ID);
        verify(vectorStoreClient, times(2)).deleteByDocumentId(DOCUMENT_ID);
        verify(vectorStoreClient, times(2)).insert(anyList());
    }

    @Test
    void givenLostStaleProcessingReclaim_whenProcessing_thenConflictsWithoutCleanup() {
        when(knowledgeDocumentMapper.selectById(DOCUMENT_ID))
                .thenReturn(staleProcessingDocument());
        when(knowledgeDocumentMapper.reclaimStaleProcessing(
                eq(DOCUMENT_ID),
                any()
        )).thenReturn(0);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> processingService.processDocument(DOCUMENT_ID)
        );

        assertThat(exception.getMessage()).isEqualTo("文档正在处理中，请稍后重试");
        verify(knowledgeDocumentMapper).reclaimStaleProcessing(
                eq(DOCUMENT_ID),
                any()
        );
        verify(vectorStoreClient, never()).deleteByDocumentId(DOCUMENT_ID);
        verify(vectorStoreClient, never()).insert(anyList());
        verifyNoInteractions(
                knowledgeChunkMapper,
                knowledgeTextChunker,
                embeddingClient,
                transactionService
        );
    }

    @Test
    void givenMarkFailedAffectsNoRow_whenProcessing_thenKeepsOriginalException() {
        RuntimeException originalException = new RuntimeException("embedding failure");
        stubThroughChunking();
        when(embeddingClient.embed(chunkTexts())).thenThrow(originalException);
        when(knowledgeDocumentMapper.markFailed(eq(DOCUMENT_ID), anyString()))
                .thenReturn(0);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> processingService.processDocument(DOCUMENT_ID)
        );

        assertSame(originalException, actual);
        assertThat(actual.getSuppressed())
                .singleElement()
                .satisfies(suppressed -> {
                    assertThat(suppressed)
                            .isInstanceOf(IllegalArgumentException.class);
                    assertThat(suppressed.getMessage())
                            .isEqualTo("知识文档FAILED状态更新失败");
                });
    }

    @Test
    void givenMarkFailedThrows_whenProcessing_thenKeepsOriginalAsPrimaryException() {
        RuntimeException originalException = new RuntimeException("embedding failure");
        RuntimeException statusException = new RuntimeException("database unavailable");
        stubThroughChunking();
        when(embeddingClient.embed(chunkTexts())).thenThrow(originalException);
        when(knowledgeDocumentMapper.markFailed(eq(DOCUMENT_ID), anyString()))
                .thenThrow(statusException);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> processingService.processDocument(DOCUMENT_ID)
        );

        assertSame(originalException, actual);
        assertThat(actual.getSuppressed()).containsExactly(statusException);
    }

    private void stubClaimedDocument() {
        when(knowledgeDocumentMapper.selectById(DOCUMENT_ID))
                .thenReturn(uploadedDocument(), processingDocument(null));
        when(knowledgeDocumentMapper.claimProcessing(DOCUMENT_ID)).thenReturn(1);
    }

    private void stubFailedRetry(EmbeddingBatchResult embeddingResult) {
        when(knowledgeDocumentMapper.selectById(DOCUMENT_ID))
                .thenReturn(failedDocument(), processingDocument(null));
        when(knowledgeDocumentMapper.claimFailedProcessing(DOCUMENT_ID))
                .thenReturn(1);
        when(knowledgeTextChunker.split(DOCUMENT_CONTENT)).thenReturn(DRAFTS);
        when(embeddingClient.embed(chunkTexts())).thenReturn(embeddingResult);
    }

    private void stubStaleProcessingRetry(EmbeddingBatchResult embeddingResult) {
        when(knowledgeDocumentMapper.selectById(DOCUMENT_ID))
                .thenReturn(
                        staleProcessingDocument(),
                        processingDocument(null)
                );
        when(knowledgeDocumentMapper.reclaimStaleProcessing(
                eq(DOCUMENT_ID),
                any()
        )).thenReturn(1);
        when(knowledgeTextChunker.split(DOCUMENT_CONTENT)).thenReturn(DRAFTS);
        when(embeddingClient.embed(chunkTexts())).thenReturn(embeddingResult);
    }

    private void stubUploadedThenFailedRetry(EmbeddingBatchResult embeddingResult) {
        when(knowledgeDocumentMapper.selectById(DOCUMENT_ID))
                .thenReturn(
                        uploadedDocument(),
                        processingDocument(null),
                        failedDocument(),
                        processingDocument(null)
                );
        when(knowledgeDocumentMapper.claimProcessing(DOCUMENT_ID)).thenReturn(1);
        when(knowledgeDocumentMapper.claimFailedProcessing(DOCUMENT_ID))
                .thenReturn(1);
        when(knowledgeTextChunker.split(DOCUMENT_CONTENT)).thenReturn(DRAFTS);
        when(embeddingClient.embed(chunkTexts())).thenReturn(embeddingResult);
    }

    private void stubThroughChunking() {
        stubClaimedDocument();
        when(knowledgeTextChunker.split(DOCUMENT_CONTENT)).thenReturn(DRAFTS);
    }

    private void stubThroughEmbedding(EmbeddingBatchResult embeddingResult) {
        stubThroughChunking();
        when(embeddingClient.embed(chunkTexts())).thenReturn(embeddingResult);
    }

    private void stubSuccessfulMarkFailed() {
        when(knowledgeDocumentMapper.markFailed(eq(DOCUMENT_ID), anyString()))
                .thenReturn(1);
    }

    private void verifyNoInsertOrCompensation() {
        verify(vectorStoreClient).deleteByDocumentId(DOCUMENT_ID);
        verify(vectorStoreClient, never()).insert(anyList());
        verify(vectorStoreClient, never()).deleteByVectorIds(anyList());
        verifyNoInteractions(transactionService);
    }

    private void assertCompensatedIdsMatchAttemptedInsert() {
        ArgumentCaptor<List<VectorWriteItem>> insertedCaptor = listCaptor();
        ArgumentCaptor<List<String>> deletedIdsCaptor = listCaptor();
        verify(vectorStoreClient).insert(insertedCaptor.capture());
        verify(vectorStoreClient).deleteByVectorIds(deletedIdsCaptor.capture());

        List<String> insertedIds = insertedCaptor.getValue().stream()
                .map(VectorWriteItem::vectorId)
                .toList();
        assertThat(deletedIdsCaptor.getValue())
                .containsExactlyElementsOf(insertedIds);
        assertThat(new HashSet<>(insertedIds)).hasSize(insertedIds.size());
    }

    private static KnowledgeDocument validDocument() {
        return processingDocument(null);
    }

    private static KnowledgeDocument uploadedDocument() {
        return document(KnowledgeProcessingStatus.UPLOADED, null);
    }

    private static KnowledgeDocument failedDocument() {
        return document(KnowledgeProcessingStatus.FAILED, null);
    }

    private static KnowledgeDocument staleProcessingDocument() {
        return processingDocument(LocalDateTime.now().minusMinutes(16));
    }

    private static KnowledgeDocument processingDocument(LocalDateTime updatedAt) {
        return document(KnowledgeProcessingStatus.PROCESSING, updatedAt);
    }

    private static KnowledgeDocument document(
            KnowledgeProcessingStatus status,
            LocalDateTime updatedAt
    ) {
        return KnowledgeDocument.builder()
                .id(DOCUMENT_ID)
                .documentVersion(DOCUMENT_VERSION)
                .content(DOCUMENT_CONTENT)
                .processingStatus(status)
                .updatedAt(updatedAt)
                .build();
    }

    private static List<String> chunkTexts() {
        return DRAFTS.stream()
                .map(KnowledgeChunkDraft::content)
                .toList();
    }

    private static EmbeddingBatchResult outOfOrderEmbeddingResult() {
        return embeddingResult(List.of(
                vector(2, VECTOR_TWO),
                vector(0, VECTOR_ZERO),
                vector(1, VECTOR_ONE)
        ));
    }

    private static EmbeddingBatchResult embeddingResult(
            List<EmbeddingVector> vectors
    ) {
        return new EmbeddingBatchResult(
                EMBEDDING_MODEL,
                EMBEDDING_VERSION,
                2,
                vectors,
                42L
        );
    }

    private static EmbeddingVector vector(int inputIndex, List<Float> values) {
        return new EmbeddingVector(inputIndex, values);
    }

    private static Stream<Arguments> invalidIndexResults() {
        return Stream.of(
                Arguments.of(
                        "out-of-range input index",
                        embeddingResult(List.of(
                                vector(0, VECTOR_ZERO),
                                vector(1, VECTOR_ONE),
                                vector(3, VECTOR_TWO)
                        ))
                ),
                Arguments.of(
                        "duplicate input index",
                        embeddingResult(List.of(
                                vector(0, VECTOR_ZERO),
                                vector(0, VECTOR_ONE),
                                vector(2, VECTOR_TWO)
                        ))
                )
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> ArgumentCaptor<List<T>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
