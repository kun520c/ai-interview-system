package com.kun.aiinterview.interview.evaluation;

import com.kun.aiinterview.knowledge.retrieval.RetrievalResult;
import com.kun.aiinterview.knowledge.service.KnowledgeRetrievalService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import static com.kun.aiinterview.common.observability.InterviewRequestTiming.Phase.QUERY_BUILD;
import static com.kun.aiinterview.common.observability.InterviewRequestTiming.measure;

@Service
@ConditionalOnProperty(
        prefix = "milvus",
        name = "enabled",
        havingValue = "true"
)
public class EvaluationRetrievalAdapter {

    private final EvaluationRagQueryBuilder queryBuilder;
    private final KnowledgeRetrievalService knowledgeRetrievalService;

    public EvaluationRetrievalAdapter(
            EvaluationRagQueryBuilder queryBuilder,
            KnowledgeRetrievalService knowledgeRetrievalService
    ) {
        this.queryBuilder = queryBuilder;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
    }

    public EvaluationRetrievalResult retrieve(
            EvaluationContext context,
            int topK
    ) {
        String query = measure(QUERY_BUILD, () -> queryBuilder.build(context));

        RetrievalResult retrievalResult =
                knowledgeRetrievalService.retrieve(
                        query,
                        topK
                );

        if (retrievalResult == null) {
            throw new IllegalStateException("知识检索结果不能为空");
        }

        return new EvaluationRetrievalResult(
                retrievalResult.query(),
                retrievalResult.requestedTopK(),
                retrievalResult.embeddingModel(),
                retrievalResult.embeddingVersion(),
                retrievalResult.rawHits(),
                retrievalResult.items()
        );
    }
}
