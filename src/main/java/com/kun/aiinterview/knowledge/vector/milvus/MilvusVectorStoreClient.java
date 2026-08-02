package com.kun.aiinterview.knowledge.vector.milvus;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import com.kun.aiinterview.knowledge.vector.VectorWriteItem;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.exception.MilvusClientException;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.response.InsertResp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import static com.kun.aiinterview.knowledge.vector.milvus
        .MilvusSchemaConstants.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        List<JsonObject> rows = validatedItems.stream()
                .map(this::toJsonRow)
                .toList();

        InsertReq request = InsertReq.builder()
                .collectionName(properties.getCollectionName())
                .data(rows)
                .build();

        try {
            InsertResp response = milvusClient.insert(request);
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
        throw new UnsupportedOperationException("按IDs删除尚未实现");
    }

    @Override
    public void deleteByDocumentId(long documentId) {
        throw new UnsupportedOperationException("按文档ID删除尚未实现");
    }

    @Override
    public List<VectorSearchHit> search(
            List<Float> queryVector,
            String embeddingVersion,
            int topK
    ){
        throw new UnsupportedOperationException("向量搜索尚未实现");
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
}
