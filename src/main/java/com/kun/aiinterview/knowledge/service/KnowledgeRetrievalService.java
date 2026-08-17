package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.knowledge.embedding.EmbeddingBatchResult;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.embedding.EmbeddingVector;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.retrieval.KnowledgeRetrievalRow;
import com.kun.aiinterview.knowledge.retrieval.RetrievalResult;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(
        prefix = "milvus",
        name = "enabled",
        havingValue = "true"
)
public class KnowledgeRetrievalService {

    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;
    private final KnowledgeChunkMapper knowledgeChunkMapper;

    public KnowledgeRetrievalService(
            EmbeddingClient embeddingClient,
            VectorStoreClient vectorStoreClient,
            KnowledgeChunkMapper knowledgeChunkMapper
    ){
        this.embeddingClient = embeddingClient;
        this.vectorStoreClient = vectorStoreClient;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
    }

    public RetrievalResult retrieve(
            String query,
            int topK
    ){
        if(query == null || query.isBlank()){
            throw new IllegalArgumentException(
                    "检索Query不能为空"
            );
        }

        if(topK <= 0){
            throw new IllegalArgumentException(
                    "topK必须大于0"
            );
        }

        String normalizedQuery = query.strip();

        EmbeddingBatchResult embeddingResult =
                embeddingClient.embed(
                        List.of(normalizedQuery)
                );

        if(embeddingResult == null){
            throw new IllegalStateException(
                    "Query Embedding返回结果不能为空"
            );
        }

        if(embeddingResult.vectors().size() != 1){
            throw new IllegalStateException(
                    "Query Embedding返回的向量数量异常"
            );
        }

        EmbeddingVector queryEmbedding =
                embeddingResult.vectors().get(0);

        if(queryEmbedding == null){
            throw new IllegalStateException(
                    "Query Embedding返回的向量不能为空"
            );
        }

        if(queryEmbedding.inputIndex() != 0){
            throw new IllegalStateException(
                    "Query Embedding返回的输入索引异常"
            );
        }

        List<VectorSearchHit> hits =
                vectorStoreClient.search(
                        queryEmbedding.values(),
                        embeddingResult.profileVersion(),
                        topK
                );

        if(hits.isEmpty()){
            return new RetrievalResult(
                    normalizedQuery,
                    topK,
                    embeddingResult.model(),
                    embeddingResult.profileVersion(),
                    List.of()
            );
        }

        List<String> vectorIds =
                hits.stream()
                        .map(VectorSearchHit::vectorId)
                        .distinct()
                        .toList();

        List<KnowledgeRetrievalRow> rows =
                knowledgeChunkMapper
                        .selectRetrievableByVectorIds(
                                vectorIds,
                                embeddingResult.model(),
                                embeddingResult.profileVersion()
                        );

        Map<String,KnowledgeRetrievalRow> rowByVectorId =
                rows.stream()
                        .collect(
                                Collectors.toMap(
                                        KnowledgeRetrievalRow::getVectorId,
                                        Function.identity()
                                )
                        );

        List<RetrievedChunk> retrievedChunks =
                new ArrayList<>();

        for(int i = 0;i < hits.size();i++){

            VectorSearchHit hit = hits.get(i);

            KnowledgeRetrievalRow row =
                    rowByVectorId.get(
                            hit.vectorId()
                    );

            if(row == null){
                continue;
            }

            if(!Objects.equals(
                    row.getDocumentId(),
                    hit.documentId()
            )){
                throw new IllegalStateException(
                        "Milvus与Mysql的documentId不一致"
                );
            }

            if(!Objects.equals(
                    row.getChunkIndex(),
                    hit.chunkIndex()
            )){
                throw new IllegalStateException(
                        "Milvus与Mysql的chunkIndex不一致"
                );
            }

            retrievedChunks.add(
                    new RetrievedChunk(
                            i + 1,
                            hit.similarityScore(),

                            row.getChunkId(),
                            row.getDocumentId(),
                            row.getDocumentVersion(),
                            row.getChunkIndex(),

                            row.getVectorId(),

                            row.getTitle(),
                            row.getCategory(),
                            row.getSource(),

                            row.getContent()
                    )
            );
        }

        return new RetrievalResult(
                normalizedQuery,
                topK,
                embeddingResult.model(),
                embeddingResult.profileVersion(),
                retrievedChunks
        );
    }
}
