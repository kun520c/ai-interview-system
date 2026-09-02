package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingVector;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.retrieval.KnowledgeRetrievalRow;
import com.kun.aiinterview.knowledge.retrieval.RetrievalResult;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeRetrievalRawHitTest {

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private VectorStoreClient vectorStoreClient;

    @Mock
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Test
    void givenRawMilvusHitsFilteredByMysql_whenRetrieve_thenKeepsRawHitsAndEligibleEvidenceSeparately() {
        String query = "HashMap并发";
        String model = "test-model";
        String profile = "test-profile";
        List<Float> vector = List.of(0.1F, 0.2F);

        when(embeddingClient.embed(List.of(query)))
                .thenReturn(
                        new EmbeddingBatchResult(
                                model,
                                profile,
                                2,
                                List.of(
                                        new EmbeddingVector(
                                                0,
                                                vector
                                        )
                                ),
                                null
                        )
                );

        VectorSearchHit hitA =
                new VectorSearchHit(
                        "A",
                        101L,
                        1,
                        profile,
                        0.91D
                );

        VectorSearchHit hitB =
                new VectorSearchHit(
                        "B",
                        102L,
                        2,
                        profile,
                        0.82D
                );

        when(vectorStoreClient.search(vector, profile, 5))
                .thenReturn(List.of(hitA, hitB));

        KnowledgeRetrievalRow rowA = new KnowledgeRetrievalRow();
        rowA.setChunkId(1001L);
        rowA.setDocumentId(101L);
        rowA.setDocumentVersion(1);
        rowA.setChunkIndex(1);
        rowA.setVectorId("A");
        rowA.setTitle("HashMap");
        rowA.setCategory(KnowledgeCategory.JAVA_COLLECTION);
        rowA.setContent("eligible content");

        when(
                knowledgeChunkMapper.selectRetrievableByVectorIds(
                        List.of("A", "B"),
                        model,
                        profile
                )
        ).thenReturn(List.of(rowA));

        KnowledgeRetrievalService service =
                new KnowledgeRetrievalService(
                        embeddingClient,
                        vectorStoreClient,
                        knowledgeChunkMapper
                );

        RetrievalResult result =
                service.retrieve(query, 5);

        assertThat(result.rawHits())
                .containsExactly(hitA, hitB);

        assertThat(result.items())
                .hasSize(1);

        assertThat(result.items().get(0).vectorId())
                .isEqualTo("A");

        assertThat(result.items().get(0).vectorRank())
                .isEqualTo(1);
    }
}
