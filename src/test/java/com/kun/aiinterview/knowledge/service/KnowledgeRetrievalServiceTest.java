package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingVector;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.retrieval.KnowledgeRetrievalRow;
import com.kun.aiinterview.knowledge.retrieval.RetrievalResult;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeRetrievalServiceTest {

    private static final String QUERY = "Explain Java collections";
    private static final String EMBEDDING_MODEL = "test-embedding-model";
    private static final String EMBEDDING_VERSION = "test-profile-v1";
    private static final List<Float> QUERY_VECTOR = List.of(0.10F, 0.20F);
    private static final int TOP_K = 5;

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private VectorStoreClient vectorStoreClient;

    @Mock
    private KnowledgeChunkMapper knowledgeChunkMapper;

    private KnowledgeRetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        retrievalService = new KnowledgeRetrievalService(
                embeddingClient,
                vectorStoreClient,
                knowledgeChunkMapper
        );
    }

    @Test
    void givenValidQueryAndThreeHits_whenRetrieving_thenReturnsCompleteRankedEvidence() {
        List<VectorSearchHit> hits = List.of(
                hit("A", 101L, 1, 0.91D),
                hit("B", 102L, 2, 0.82D),
                hit("C", 103L, 3, 0.73D)
        );
        List<KnowledgeRetrievalRow> rows = List.of(
                row("A", 1001L, 101L, 1, "content A", "title A",
                        KnowledgeCategory.JAVA_COLLECTION, "source A"),
                row("B", 1002L, 102L, 2, "content B", "title B",
                        KnowledgeCategory.SPRING, "source B"),
                row("C", 1003L, 103L, 3, "content C", "title C",
                        KnowledgeCategory.MYSQL, "source C")
        );
        stubRetrieval(TOP_K, hits, List.of("A", "B", "C"), rows);

        RetrievalResult result = retrievalService.retrieve(
                "  " + QUERY + "  ",
                TOP_K
        );

        assertThat(result.query()).isEqualTo(QUERY);
        assertThat(result.requestedTopK()).isEqualTo(TOP_K);
        assertThat(result.embeddingModel()).isEqualTo(EMBEDDING_MODEL);
        assertThat(result.embeddingVersion()).isEqualTo(EMBEDDING_VERSION);
        assertThat(result.items()).hasSize(3);
        assertThat(result.items())
                .extracting(RetrievedChunk::vectorId)
                .containsExactly("A", "B", "C");
        assertThat(result.items())
                .extracting(RetrievedChunk::vectorRank)
                .containsExactly(1, 2, 3);
        assertThat(result.items())
                .extracting(RetrievedChunk::similarityScore)
                .containsExactly(0.91D, 0.82D, 0.73D);
        assertThat(result.items())
                .extracting(RetrievedChunk::content)
                .containsExactly("content A", "content B", "content C");
        assertThat(result.items())
                .extracting(RetrievedChunk::title)
                .containsExactly("title A", "title B", "title C");
        assertThat(result.items())
                .extracting(RetrievedChunk::category)
                .containsExactly(
                        KnowledgeCategory.JAVA_COLLECTION,
                        KnowledgeCategory.SPRING,
                        KnowledgeCategory.MYSQL
                );
        assertThat(result.items())
                .extracting(RetrievedChunk::source)
                .containsExactly("source A", "source B", "source C");

        verify(embeddingClient).embed(List.of(QUERY));
        verify(vectorStoreClient).search(
                QUERY_VECTOR,
                EMBEDDING_VERSION,
                TOP_K
        );
        verify(knowledgeChunkMapper, times(1))
                .selectRetrievableByVectorIds(
                        List.of("A", "B", "C"),
                        EMBEDDING_MODEL,
                        EMBEDDING_VERSION
                );
    }

    @Test
    void givenMapperRowsInDifferentOrder_whenRetrieving_thenRestoresMilvusOrder() {
        VectorSearchHit hitA = hit("A", 101L, 1, 0.91D);
        VectorSearchHit hitB = hit("B", 102L, 2, 0.82D);
        VectorSearchHit hitC = hit("C", 103L, 3, 0.73D);
        KnowledgeRetrievalRow rowA = row("A", 1001L, 101L, 1,
                "content A", "title A", KnowledgeCategory.JAVA_BASIC, "source A");
        KnowledgeRetrievalRow rowB = row("B", 1002L, 102L, 2,
                "content B", "title B", KnowledgeCategory.SPRING, "source B");
        KnowledgeRetrievalRow rowC = row("C", 1003L, 103L, 3,
                "content C", "title C", KnowledgeCategory.MYSQL, "source C");
        stubRetrieval(
                TOP_K,
                List.of(hitA, hitB, hitC),
                List.of("A", "B", "C"),
                List.of(rowC, rowA, rowB)
        );

        RetrievalResult result = retrievalService.retrieve(QUERY, TOP_K);

        assertThat(result.items())
                .extracting(RetrievedChunk::vectorId)
                .containsExactly("A", "B", "C");
        assertThat(result.items())
                .extracting(RetrievedChunk::vectorRank)
                .containsExactly(1, 2, 3);
    }

    @Test
    void givenOnlySomeHitsRemainRetrievable_whenRetrieving_thenKeepsOriginalRanksWithoutRefill() {
        List<VectorSearchHit> hits = List.of(
                hit("A", 101L, 1, 0.95D),
                hit("B", 102L, 2, 0.85D),
                hit("C", 103L, 3, 0.75D),
                hit("D", 104L, 4, 0.65D),
                hit("E", 105L, 5, 0.55D)
        );
        List<KnowledgeRetrievalRow> rows = List.of(
                row("A", 1001L, 101L, 1, "content A", "title A",
                        KnowledgeCategory.JAVA_BASIC, "source A"),
                row("C", 1003L, 103L, 3, "content C", "title C",
                        KnowledgeCategory.JAVA_CONCURRENCY, "source C"),
                row("E", 1005L, 105L, 5, "content E", "title E",
                        KnowledgeCategory.REDIS, "source E")
        );
        stubRetrieval(
                TOP_K,
                hits,
                List.of("A", "B", "C", "D", "E"),
                rows
        );

        RetrievalResult result = retrievalService.retrieve(QUERY, TOP_K);

        assertThat(result.items()).hasSize(3);
        assertThat(result.items())
                .extracting(RetrievedChunk::vectorId)
                .containsExactly("A", "C", "E");
        assertThat(result.items())
                .extracting(RetrievedChunk::vectorRank)
                .containsExactly(1, 3, 5);
        verify(vectorStoreClient, times(1)).search(
                QUERY_VECTOR,
                EMBEDDING_VERSION,
                TOP_K
        );
    }

    @Test
    void givenMilvusReturnsNoHits_whenRetrieving_thenReturnsEmptyWithoutMapperCall() {
        when(embeddingClient.embed(List.of(QUERY)))
                .thenReturn(validEmbeddingResult());
        when(vectorStoreClient.search(
                QUERY_VECTOR,
                EMBEDDING_VERSION,
                TOP_K
        )).thenReturn(List.of());

        RetrievalResult result = retrievalService.retrieve(QUERY, TOP_K);

        assertThat(result.items()).isEmpty();
        assertThat(result.query()).isEqualTo(QUERY);
        assertThat(result.requestedTopK()).isEqualTo(TOP_K);
        assertThat(result.embeddingModel()).isEqualTo(EMBEDDING_MODEL);
        assertThat(result.embeddingVersion()).isEqualTo(EMBEDDING_VERSION);
        verifyNoInteractions(knowledgeChunkMapper);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t\n"})
    void givenMissingOrBlankQuery_whenRetrieving_thenRejectsBeforeEmbedding(
            String query
    ) {
        assertThatThrownBy(() -> retrievalService.retrieve(query, TOP_K))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Query");

        verifyNoInteractions(
                embeddingClient,
                vectorStoreClient,
                knowledgeChunkMapper
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void givenNonPositiveTopK_whenRetrieving_thenRejectsBeforeEmbedding(
            int topK
    ) {
        assertThatThrownBy(() -> retrievalService.retrieve(QUERY, topK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK");

        verifyNoInteractions(
                embeddingClient,
                vectorStoreClient,
                knowledgeChunkMapper
        );
    }

    @Test
    void givenEmbeddingReturnsNull_whenRetrieving_thenRejectsBeforeMilvusSearch() {
        when(embeddingClient.embed(List.of(QUERY))).thenReturn(null);

        assertThatThrownBy(() -> retrievalService.retrieve(QUERY, TOP_K))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("结果不能为空");

        verifyNoInteractions(vectorStoreClient, knowledgeChunkMapper);
    }

    @Test
    void givenEmbeddingReturnsMultipleVectors_whenRetrieving_thenRejectsBeforeMilvusSearch() {
        EmbeddingBatchResult result = new EmbeddingBatchResult(
                EMBEDDING_MODEL,
                EMBEDDING_VERSION,
                QUERY_VECTOR.size(),
                List.of(
                        new EmbeddingVector(0, QUERY_VECTOR),
                        new EmbeddingVector(1, QUERY_VECTOR)
                ),
                null
        );
        when(embeddingClient.embed(List.of(QUERY))).thenReturn(result);

        assertThatThrownBy(() -> retrievalService.retrieve(QUERY, TOP_K))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("数量异常");

        verifyNoInteractions(vectorStoreClient, knowledgeChunkMapper);
    }

    @Test
    void givenSingleEmbeddingHasNonZeroInputIndex_whenRetrieving_thenRejectsBeforeMilvusSearch() {
        EmbeddingBatchResult result = new EmbeddingBatchResult(
                EMBEDDING_MODEL,
                EMBEDDING_VERSION,
                QUERY_VECTOR.size(),
                List.of(new EmbeddingVector(1, QUERY_VECTOR)),
                null
        );
        when(embeddingClient.embed(List.of(QUERY))).thenReturn(result);

        assertThatThrownBy(() -> retrievalService.retrieve(QUERY, TOP_K))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("输入索引异常");

        verifyNoInteractions(vectorStoreClient, knowledgeChunkMapper);
    }

    @Test
    void givenDocumentIdDiffersAcrossStores_whenRetrieving_thenFailsConsistencyCheck() {
        VectorSearchHit hit = hit("X", 10L, 3, 0.80D);
        KnowledgeRetrievalRow row = row(
                "X", 1001L, 11L, 3, "content X", "title X",
                KnowledgeCategory.JVM, "source X"
        );
        stubRetrieval(TOP_K, List.of(hit), List.of("X"), List.of(row));

        assertThatThrownBy(() -> retrievalService.retrieve(QUERY, TOP_K))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("documentId");
    }

    @Test
    void givenChunkIndexDiffersAcrossStores_whenRetrieving_thenFailsConsistencyCheck() {
        VectorSearchHit hit = hit("X", 10L, 3, 0.80D);
        KnowledgeRetrievalRow row = row(
                "X", 1001L, 10L, 4, "content X", "title X",
                KnowledgeCategory.JVM, "source X"
        );
        stubRetrieval(TOP_K, List.of(hit), List.of("X"), List.of(row));

        assertThatThrownBy(() -> retrievalService.retrieve(QUERY, TOP_K))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chunkIndex");
    }

    @Test
    void givenMapperReturnsDuplicateVectorIds_whenRetrieving_thenFailsInsteadOfOverwriting() {
        VectorSearchHit hit = hit("X", 10L, 3, 0.80D);
        KnowledgeRetrievalRow first = row(
                "X", 1001L, 10L, 3, "first content", "first title",
                KnowledgeCategory.JVM, "first source"
        );
        KnowledgeRetrievalRow duplicate = row(
                "X", 1002L, 10L, 3, "duplicate content", "duplicate title",
                KnowledgeCategory.JVM, "duplicate source"
        );
        stubRetrieval(
                TOP_K,
                List.of(hit),
                List.of("X"),
                List.of(first, duplicate)
        );

        assertThatThrownBy(() -> retrievalService.retrieve(QUERY, TOP_K))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate key");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0D, -0.25D})
    void givenFiniteLowSimilarityScore_whenRetrieving_thenPreservesScore(
            double similarityScore
    ) {
        VectorSearchHit hit = hit("X", 10L, 3, similarityScore);
        KnowledgeRetrievalRow row = row(
                "X", 1001L, 10L, 3, "content X", "title X",
                KnowledgeCategory.JVM, "source X"
        );
        stubRetrieval(TOP_K, List.of(hit), List.of("X"), List.of(row));

        RetrievalResult result = retrievalService.retrieve(QUERY, TOP_K);

        assertThat(result.items()).singleElement()
                .extracting(RetrievedChunk::similarityScore)
                .isEqualTo(similarityScore);
    }

    private void stubRetrieval(
            int topK,
            List<VectorSearchHit> hits,
            List<String> vectorIds,
            List<KnowledgeRetrievalRow> rows
    ) {
        when(embeddingClient.embed(List.of(QUERY)))
                .thenReturn(validEmbeddingResult());
        when(vectorStoreClient.search(
                QUERY_VECTOR,
                EMBEDDING_VERSION,
                topK
        )).thenReturn(hits);
        when(knowledgeChunkMapper.selectRetrievableByVectorIds(
                vectorIds,
                EMBEDDING_MODEL,
                EMBEDDING_VERSION
        )).thenReturn(rows);
    }

    private static EmbeddingBatchResult validEmbeddingResult() {
        return new EmbeddingBatchResult(
                EMBEDDING_MODEL,
                EMBEDDING_VERSION,
                QUERY_VECTOR.size(),
                List.of(new EmbeddingVector(0, QUERY_VECTOR)),
                null
        );
    }

    private static VectorSearchHit hit(
            String vectorId,
            Long documentId,
            int chunkIndex,
            double similarityScore
    ) {
        return new VectorSearchHit(
                vectorId,
                documentId,
                chunkIndex,
                EMBEDDING_VERSION,
                similarityScore
        );
    }

    private static KnowledgeRetrievalRow row(
            String vectorId,
            Long chunkId,
            Long documentId,
            int chunkIndex,
            String content,
            String title,
            KnowledgeCategory category,
            String source
    ) {
        KnowledgeRetrievalRow row = new KnowledgeRetrievalRow();
        row.setVectorId(vectorId);
        row.setChunkId(chunkId);
        row.setDocumentId(documentId);
        row.setDocumentVersion(1);
        row.setChunkIndex(chunkIndex);
        row.setContent(content);
        row.setTitle(title);
        row.setCategory(category);
        row.setSource(source);
        return row;
    }
}
