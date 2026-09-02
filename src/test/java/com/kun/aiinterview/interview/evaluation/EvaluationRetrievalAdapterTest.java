package com.kun.aiinterview.interview.evaluation;

import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.retrieval.RetrievalResult;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import com.kun.aiinterview.knowledge.service.KnowledgeRetrievalService;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import com.kun.aiinterview.question.enums.QuestionCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationRetrievalAdapterTest {

    @Mock
    private EvaluationRagQueryBuilder queryBuilder;

    @Mock
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @Test
    void givenEvaluationContext_whenRetrieve_thenBuildsQueryAndPreservesRawHitsAndEvidence() {
        EvaluationContext context = context();
        String query = "stable-query";

        VectorSearchHit rawHit =
                new VectorSearchHit(
                        "vector-1",
                        10L,
                        1,
                        "profile-v1",
                        0.91D
                );

        RetrievedChunk evidence =
                new RetrievedChunk(
                        1,
                        0.91D,
                        100L,
                        10L,
                        1,
                        1,
                        "vector-1",
                        "HashMap",
                        KnowledgeCategory.JAVA_COLLECTION,
                        null,
                        "HashMap并发知识"
                );

        RetrievalResult retrievalResult =
                new RetrievalResult(
                        query,
                        5,
                        "embedding-model",
                        "profile-v1",
                        List.of(rawHit),
                        List.of(evidence)
                );

        when(queryBuilder.build(context)).thenReturn(query);
        when(knowledgeRetrievalService.retrieve(query, 5))
                .thenReturn(retrievalResult);

        EvaluationRetrievalAdapter adapter =
                new EvaluationRetrievalAdapter(
                        queryBuilder,
                        knowledgeRetrievalService
                );

        EvaluationRetrievalResult result =
                adapter.retrieve(context, 5);

        assertThat(result.query()).isEqualTo(query);
        assertThat(result.requestedTopK()).isEqualTo(5);
        assertThat(result.embeddingModel()).isEqualTo("embedding-model");
        assertThat(result.embeddingVersion()).isEqualTo("profile-v1");
        assertThat(result.rawHits()).containsExactly(rawHit);
        assertThat(result.evidence()).containsExactly(evidence);

        verify(queryBuilder).build(context);
        verify(knowledgeRetrievalService).retrieve(query, 5);
    }

    @Test
    void givenRetrievalServiceReturnsNull_whenRetrieve_thenRejects() {
        EvaluationContext context = context();
        when(queryBuilder.build(context)).thenReturn("stable-query");
        when(knowledgeRetrievalService.retrieve("stable-query", 5))
                .thenReturn(null);

        EvaluationRetrievalAdapter adapter =
                new EvaluationRetrievalAdapter(
                        queryBuilder,
                        knowledgeRetrievalService
                );

        assertThatThrownBy(() -> adapter.retrieve(context, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("检索结果");
    }

    private EvaluationContext context() {
        return new EvaluationContext(
                1L,
                2L,
                1L,
                2L,
                EvaluationMode.MAIN_ANSWER,
                QuestionCategory.JAVA_COLLECTION,
                "HashMap",
                "HashMap为什么线程不安全？",
                "参考答案",
                List.of(),
                "用户回答",
                null,
                null,
                List.of()
        );
    }
}
