package com.kun.aiinterview.knowledge.vector.milvus;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import com.kun.aiinterview.knowledge.vector.VectorWriteItem;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.exception.MilvusClientException;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import static com.kun.aiinterview.knowledge.vector.milvus
        .MilvusSchemaConstants.*;

import java.util.*;

@Component
@ConditionalOnProperty(
        prefix = "milvus",
        name = "enabled",
        havingValue = "true"
)
public class MilvusVectorStoreClient implements VectorStoreClient {

    private final MilvusClientV2 milvusClient;
    private final MilvusProperties properties;

    public MilvusVectorStoreClient(
            MilvusClientV2 milvusClient,
            MilvusProperties properties
    ){
        this.milvusClient = milvusClient;
        this.properties = properties;
    }

    @Override
    public void insert(List<VectorWriteItem> items){
        List<VectorWriteItem> validatedItems =  validateAndCopyItems(items);

        try {
            InsertResp response = milvusClient.insert(
                    buildInsertRequest(validatedItems)
            );

            validateInsertResponse(response, validatedItems.size());
        }catch(MilvusClientException exception){
            throw new ExternalServiceException(
                    "Milvus批量写入向量失败",
                    exception
            );
        }
    }

    @Override
    public void deleteByVectorIds(List<String> vectorIds){
        List<String> validatedVectorIds = validateAndCopyVectorIds(vectorIds);

        try{
            DeleteResp response = milvusClient.delete(
                    buildDeleteByVectorIdsRequest(validatedVectorIds)
            );

            validateDeleteResponse(response);
        }catch(MilvusClientException exception){
            throw new ExternalServiceException(
                    "Milvus按向量ID删除失败",
                    exception
            );
        }
    }

    @Override
    public void deleteByDocumentId(long documentId) {
        validateDocumentId(documentId);

        try {
            DeleteResp response = milvusClient.delete(
                    buildDeleteByDocumentIdRequest(documentId)
            );
            validateDeleteResponse(response);
        }catch (MilvusClientException exception){
            throw new ExternalServiceException(
                    "Milvus按文档ID删除向量失败",
                    exception
            );
        }
    }

    @Override
    public List<VectorSearchHit> search(
            List<Float> queryVector,
            String embeddingVersion,
            int topK
    ){
        List<Float> validatedQueryVector = validateAndCopyQueryVector(queryVector);

        validateEmbeddingVersion(embeddingVersion);
        validateTopK(topK);

        SearchReq request = buildSearchRequest(
                validatedQueryVector,
                embeddingVersion,
                topK
        );

        try {
            SearchResp response = milvusClient.search(request);

            return mapSearchResponse(
                    response,
                    embeddingVersion,
                    topK
            );
        }catch (MilvusClientException exception){
            throw new ExternalServiceException(
                    "Milvus向量检索失败",
                    exception
            );
        }
    }

    private List<VectorWriteItem> validateAndCopyItems(
            List<VectorWriteItem> items
    ){
        if(items == null){
            throw new IllegalArgumentException("待写入向量集合不能为null");
        }

        if(items.isEmpty()){
            throw new IllegalArgumentException("待写入向量集合不能为空");
        }

        List<VectorWriteItem> copiedItems = new ArrayList<>(items);

        Set<String> vectorIds = new HashSet<>();

        for(int index = 0;index < copiedItems.size();index++){
            VectorWriteItem item = copiedItems.get(index);

            if(item == null){
                throw new IllegalArgumentException("待写入向量不能为null，集合索引："
                                                        + index
                );
            }

            validateItemForMilvus(item,index);

            if(!vectorIds.add(item.vectorId())){
                throw new IllegalArgumentException("同一批次存在重复的vectorId:"
                                                        + item.vectorId()
                );
            }
        }

        return List.copyOf(copiedItems);
    }

    private void validateInsertResponse(
            InsertResp response,
            int expectedInsertCount
    ){
        if(response == null){
            throw new ExternalServiceException("Milvus写入响应不能为空");
        }

        if(response.getInsertCnt() != expectedInsertCount){
            throw new ExternalServiceException(
                    "Milvus写入数量与请求数量不一致"
            );
        }
    }

    private InsertReq buildInsertRequest(List<VectorWriteItem> items){
        List<JsonObject> rows = items.stream()
                .map(this::toJsonRow)
                .toList();

        return InsertReq.builder()
                .collectionName(properties.getCollectionName())
                .data(rows)
                .build();
    }

    private void validateItemForMilvus(
            VectorWriteItem item,
            int itemIndex
    ){
        if(item.vectorId().length() > VECTOR_ID_MAX_LENGTH){
            throw new IllegalArgumentException(
                    "vectorId超过Milvus字段长度限制，"
                            + "集合索引：" + itemIndex
            );
        }

        if(item.embeddingVersion().length() > EMBEDDING_VERSION_MAX_LENGTH){
            throw new IllegalArgumentException(
                    "Embedding 版本超过 Milvus 字段长度限制，"
                            + "集合索引：" + itemIndex
            );
        }

        if(item.values().size() != properties.getDimension()){
            throw new IllegalArgumentException(
                    "向量维度与Milvus Collection不一致，"
                        + "集合索引：" + itemIndex
                        + "，期望维度："
                        + properties.getDimension()
                        + "，实际维度："
                        + item.values().size()
            );
        }
    }

    private JsonObject toJsonRow(VectorWriteItem item){
        JsonObject row = new JsonObject();

        row.addProperty(
                VECTOR_ID_FIELD,
                item.vectorId()
        );

        row.addProperty(
                DOCUMENT_ID_FIELD,
                item.documentId()
        );

        row.addProperty(
                CHUNK_INDEX_FIELD,
                item.chunkIndex()
        );

        row.addProperty(
                EMBEDDING_VERSION_FIELD,
                item.embeddingVersion()
        );

        JsonArray vector = new JsonArray();

        for(Float value : item.values()){
            vector.add(value);
        }

        row.add(
                VECTOR_FIELD,
                vector
        );

        return row;
    }

    private List<String> validateAndCopyVectorIds(List<String> vectorIds){
        if(vectorIds == null){
            throw new IllegalArgumentException("待删除向量ID集合不能为null");
        }

        if(vectorIds.isEmpty()){
            throw new IllegalArgumentException("待删除向量ID集合不能为空");
        }

        List<String> copiedVectorIds = new ArrayList<>(vectorIds);
        Set<String> uniqueVectorIds = new HashSet<>();

        for(int index = 0; index < copiedVectorIds.size(); index++){
            String vectorId = copiedVectorIds.get(index);

            if(vectorId == null){
                throw new IllegalArgumentException(
                        "待删除向量ID不能为null，集合索引：" + index
                );
            }

            if(vectorId.isBlank()){
                throw new IllegalArgumentException(
                        "待删除向量ID不能为空，集合索引：" + index
                );
            }

            if(vectorId.length() > VECTOR_ID_MAX_LENGTH){
                throw new IllegalArgumentException(
                        "待删除向量ID不能超过最长长度限制，集合索引：" + index
                );
            }

            if(!uniqueVectorIds.add(vectorId)){
                throw new IllegalArgumentException("同一批次存在重复的vectorId：" +  vectorId);
            }
        }

        return List.copyOf(copiedVectorIds);
    }

    private void validateDeleteResponse(DeleteResp response){
        if(response == null){
            throw new ExternalServiceException(
                    "Milvus删除响应不能为空"
            );
        }

        if(response.getDeleteCnt() < 0){
            throw new ExternalServiceException(
                    "Milvus删除数量不能为负数"
            );
        }
    }

    private DeleteReq buildDeleteByVectorIdsRequest(
            List<String> vectorIds
    ){
        return DeleteReq.builder()
                .collectionName(properties.getCollectionName())
                .filter(VECTOR_ID_FIELD + " in {vectorIds}")
                .filterTemplateValues(
                        Map.of("vectorIds", vectorIds)
                )
                .build();
    }

    private void validateDocumentId(long documentId){
        if(documentId <= 0){
            throw new IllegalArgumentException("文档ID必须大于0");
        }
    }

    private List<Float> validateAndCopyQueryVector(List<Float> queryVector){
        if(queryVector == null){
            throw new IllegalArgumentException("查询向量集合不能为null");
        }

        if(queryVector.isEmpty()){
            throw new IllegalArgumentException("查询向量集合不能为空");
        }

        List<Float> copiedVector = new ArrayList<>(queryVector);

        for(int index = 0;index < copiedVector.size();index++){
            Float value = copiedVector.get(index);

            if(value == null || !Float.isFinite(value)){
                throw new IllegalArgumentException(
                        "查询向量存在null或非有限值,集合索引：" + index
                );
            }
        }

        if(copiedVector.size() != properties.getDimension()){
            throw new IllegalArgumentException(
                    "查询向量维度与Milvus Collection不一致，"
                        + "期望维度：" + properties.getDimension()
                        + "实际维度：" + copiedVector.size()
            );
        }

        return List.copyOf(copiedVector);
    }

    private void validateEmbeddingVersion(String embeddingVersion){
        if(embeddingVersion == null || embeddingVersion.isBlank()){
            throw new IllegalArgumentException(
                    "Embedding版本不能为空"
            );
        }

        if(embeddingVersion.length() > EMBEDDING_VERSION_MAX_LENGTH){
            throw new IllegalArgumentException(
                    "Embedding版本不能超过Milvus字段长度限制"
            );
        }
    }

    private void validateTopK(int topK){
        if(topK <= 0){
            throw new IllegalArgumentException("topK必须大于0");
        }
    }

    private SearchReq buildSearchRequest(
            List<Float> queryVector,
            String embeddingVersion,
            int topK
    ){
        return SearchReq.builder()
                .collectionName(properties.getCollectionName())
                .annsField(VECTOR_FIELD)
                .metricType(IndexParam.MetricType.COSINE)
                .data(List.of(new FloatVec(queryVector)))
                .filter(
                        EMBEDDING_VERSION_FIELD
                            + " == {embeddingVersion}"
                )
                .filterTemplateValues(
                        Map.of(
                                "embeddingVersion",
                                embeddingVersion
                        )
                )
                .outputFields(
                        List.of(
                                DOCUMENT_ID_FIELD,
                                CHUNK_INDEX_FIELD,
                                EMBEDDING_VERSION_FIELD
                        )
                )
                .limit(topK)
                .build();
    }

    private DeleteReq buildDeleteByDocumentIdRequest(long documentId) {
        return DeleteReq.builder()
                .collectionName(properties.getCollectionName())
                .filter(DOCUMENT_ID_FIELD + " == {documentId}")
                .filterTemplateValues(
                        Map.of("documentId", documentId)
                )
                .build();
    }

    private List<VectorSearchHit> mapSearchResponse(
            SearchResp response,
            String expectedEmbeddingVersion,
            int topK
    ){
        if(response == null){
            throw new ExternalServiceException(
                    "Milvus检索响应不能为空"
            );
        }

        List<List<SearchResp.SearchResult>> resultGroups = response.getSearchResults();

        if(resultGroups == null){
            throw new ExternalServiceException(
                    "Milvus检索结果集合不能为空"
            );
        }

        if(resultGroups.size() != 1){
            throw new ExternalServiceException(
                    "Milvus检索结果组数量与查询向量数量不一致"
            );
        }

        List<SearchResp.SearchResult> results = resultGroups.get(0);

        if(results == null){
            throw new ExternalServiceException("Milvus单组检索结果不能为空");
        }

        if(results.size() > topK){
            throw new ExternalServiceException("Milvus检索结果数量超过请求的topK");
        }

        List<VectorSearchHit> hits = new ArrayList<>(results.size());

        for (int index = 0;index < results.size();index++){
            SearchResp.SearchResult result = results.get(index);

            if(result == null){
                throw new ExternalServiceException(
                        "Milvus检索结果项不能为空，结果索引："
                            + index
                );
            }
            hits.add(
                    toVectorSearchHit(
                            result,
                            expectedEmbeddingVersion,
                            index
                    )
            );
        }

        return List.copyOf(hits);
    }

    private VectorSearchHit toVectorSearchHit(
            SearchResp.SearchResult result,
            String expectedEmbeddingVersion,
            int resultIndex
    ){
        Object rawVectorId = result.getId();

        if(!(rawVectorId instanceof String vectorId)
                    || vectorId.isBlank()){
            throw new ExternalServiceException(
                    "Milvus检索结果vectorId无效,结果索引："
                            + resultIndex
            );
        }

        Map<String,Object> entity = result.getEntity();

        if(entity == null){
            throw new ExternalServiceException(
                        "Milvus检索结果输出字段不能为空，结果索引："
                            + resultIndex
            );
        }

        long documentId = requireLongField(
                entity,
                DOCUMENT_ID_FIELD,
                resultIndex
        );

        long chunkIndexValue = requireLongField(
                entity,
                CHUNK_INDEX_FIELD,
                resultIndex
        );

        String responseEmbeddingVersion =
                requireStringField(
                        entity,
                        EMBEDDING_VERSION_FIELD,
                        resultIndex
                );

        if(documentId <= 0){
            throw new ExternalServiceException(
                    "Milvus检索结果documentId必须大于0，结果索引："
                            + resultIndex
            );
        }

        if (chunkIndexValue < 1
                || chunkIndexValue > Integer.MAX_VALUE) {
            throw new ExternalServiceException(
                    "Milvus检索结果chunkIndex超出有效范围，结果索引："
                            + resultIndex
            );
        }

        if (!expectedEmbeddingVersion.equals(
                responseEmbeddingVersion
        )) {
            throw new ExternalServiceException(
                    "Milvus检索结果Embedding版本与请求不一致，结果索引："
                            + resultIndex
            );
        }

        Float score = result.getScore();

        if (score == null || !Float.isFinite(score)) {
            throw new ExternalServiceException(
                    "Milvus检索结果相似度分数无效，结果索引："
                            + resultIndex
            );
        }

        return new VectorSearchHit(
                vectorId,
                documentId,
                (int) chunkIndexValue,
                responseEmbeddingVersion,
                score.doubleValue()
        );
    }

    private long requireLongField(
            Map<String, Object> entity,
            String fieldName,
            int resultIndex
    ) {
        Object value = entity.get(fieldName);

        if (!(value instanceof Long longValue)) {
            throw new ExternalServiceException(
                    "Milvus检索结果字段类型无效："
                            + fieldName
                            + "，结果索引："
                            + resultIndex
            );
        }

        return longValue;
    }

    private String requireStringField(
            Map<String, Object> entity,
            String fieldName,
            int resultIndex
    ) {
        Object value = entity.get(fieldName);

        if (!(value instanceof String stringValue)
                || stringValue.isBlank()) {
            throw new ExternalServiceException(
                    "Milvus检索结果字符串字段无效："
                            + fieldName
                            + "，结果索引："
                            + resultIndex
            );
        }

        return stringValue;
    }
}
