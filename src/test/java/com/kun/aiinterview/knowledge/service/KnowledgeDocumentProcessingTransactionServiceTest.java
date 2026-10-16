package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.knowledge.entity.KnowledgeChunk;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentProcessingTransactionServiceTest {

    private static final long DOCUMENT_ID = 101L;

    @Mock
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    private KnowledgeDocumentProcessingTransactionService transactionService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(knowledgeDocumentMapper.lockById(DOCUMENT_ID)).thenReturn(
                com.kun.aiinterview.knowledge.entity.KnowledgeDocument.builder()
                        .id(DOCUMENT_ID).documentVersion(1).processingAttemptId("attempt-test")
                        .processingStatus(com.kun.aiinterview.knowledge.enums.KnowledgeProcessingStatus.PROCESSING).build());
        transactionService = new KnowledgeDocumentProcessingTransactionService(
                knowledgeChunkMapper,
                knowledgeDocumentMapper
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void givenInvalidDocumentId_whenPersisting_thenRejectsBeforeMapperCalls(
            Long documentId
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.persistChunksAndMarkReady(
                        documentId,
                        chunks(3),
                        "attempt-test"
                )
        );

        verifyNoInteractions(knowledgeChunkMapper, knowledgeDocumentMapper);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingChunks")
    void givenMissingChunks_whenPersisting_thenRejectsBeforeMapperCalls(
            String scenario,
            List<KnowledgeChunk> chunks
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.persistChunksAndMarkReady(
                        DOCUMENT_ID,
                        chunks,
                        "attempt-test"
                ),
                scenario
        );

        verifyNoInteractions(knowledgeChunkMapper, knowledgeDocumentMapper);
    }

    @Test
    void givenExpectedAffectedRows_whenPersisting_thenInsertsBeforeMarkingReady() {
        List<KnowledgeChunk> chunks = chunks(3);
        when(knowledgeChunkMapper.batchInsert(chunks)).thenReturn(chunks.size());
        when(knowledgeDocumentMapper.markReady(DOCUMENT_ID, "attempt-test")).thenReturn(1);

        assertDoesNotThrow(
                () -> transactionService.persistChunksAndMarkReady(
                        DOCUMENT_ID,
                        chunks,
                        "attempt-test"
                )
        );

        InOrder calls = inOrder(knowledgeChunkMapper, knowledgeDocumentMapper);
        calls.verify(knowledgeChunkMapper).batchInsert(chunks);
        calls.verify(knowledgeDocumentMapper).markReady(DOCUMENT_ID, "attempt-test");
    }

    @Test
    void givenPartialBatchInsert_whenPersisting_thenDoesNotMarkReady() {
        List<KnowledgeChunk> chunks = chunks(3);
        when(knowledgeChunkMapper.batchInsert(chunks)).thenReturn(2);

        assertThrows(
                BusinessException.class,
                () -> transactionService.persistChunksAndMarkReady(
                        DOCUMENT_ID,
                        chunks,
                        "attempt-test"
                )
        );

        verify(knowledgeDocumentMapper, never()).markReady(DOCUMENT_ID, "attempt-test");
    }

    @Test
    void givenMarkReadyAffectsNoRow_whenPersisting_thenRejects() {
        List<KnowledgeChunk> chunks = chunks(3);
        when(knowledgeChunkMapper.batchInsert(chunks)).thenReturn(chunks.size());
        when(knowledgeDocumentMapper.markReady(DOCUMENT_ID, "attempt-test")).thenReturn(0);

        assertThrows(
                BusinessException.class,
                () -> transactionService.persistChunksAndMarkReady(
                        DOCUMENT_ID,
                        chunks,
                        "attempt-test"
                )
        );

        verify(knowledgeChunkMapper).batchInsert(chunks);
        verify(knowledgeDocumentMapper).markReady(DOCUMENT_ID, "attempt-test");
    }

    @Test
    void persistMethodDeclaresTransactionalBoundary() throws NoSuchMethodException {
        Method method = KnowledgeDocumentProcessingTransactionService.class
                .getMethod(
                        "persistChunksAndMarkReady",
                        Long.class,
                        List.class, String.class
                );

        assertNotNull(method.getAnnotation(Transactional.class));
    }

    private static Stream<Arguments> missingChunks() {
        return Stream.of(
                Arguments.of("null chunks", null),
                Arguments.of("empty chunks", List.of())
        );
    }

    private static List<KnowledgeChunk> chunks(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> KnowledgeChunk.builder()
                        .documentId(DOCUMENT_ID)
                        .documentVersion(1)
                        .chunkIndex(index)
                        .content("chunk " + index).vectorId("attempt-test-" + index)
                        .build())
                .toList();
    }
}
