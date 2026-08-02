package com.kun.aiinterview.knowledge.vector.milvus;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import static com.kun.aiinterview.knowledge.vector.milvus
        .MilvusSchemaConstants.*;

import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "milvus",
        name = "enabled",
        havingValue = "true"
)
public class MilvusCollectionInitializer implements ApplicationRunner {

    private final MilvusClientV2 milvusClient;
    private final MilvusProperties properties;

    public MilvusCollectionInitializer(MilvusClientV2 milvusClient, MilvusProperties properties) {
        this.milvusClient = milvusClient;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args){
        HasCollectionReq request = HasCollectionReq.builder()
                .collectionName(properties.getCollectionName())
                .build();

        boolean collectionExists =
                Boolean.TRUE.equals(
                        milvusClient.hasCollection(request)
                );

        if(collectionExists){
            return;
        }

        createCollection();
    }

    private void createCollection(){
        CreateCollectionReq.CollectionSchema schema =
                CreateCollectionReq.CollectionSchema.builder()
                        .enableDynamicField(false)
                        .build();

        schema.addField(
                AddFieldReq.builder()
                        .fieldName(VECTOR_ID_FIELD)
                        .dataType(DataType.VarChar)
                        .maxLength(VECTOR_ID_MAX_LENGTH)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build()
        );

        schema.addField(
                AddFieldReq.builder()
                        .fieldName(DOCUMENT_ID_FIELD)
                        .dataType(DataType.Int64)
                        .build()
        );

        schema.addField(
                AddFieldReq.builder()
                        .fieldName(CHUNK_INDEX_FIELD)
                        .dataType(DataType.Int64)
                        .build()
        );

        schema.addField(
                AddFieldReq.builder()
                        .fieldName(EMBEDDING_VERSION_FIELD)
                        .dataType(DataType.VarChar)
                        .maxLength(
                                EMBEDDING_VERSION_MAX_LENGTH
                        )
                        .build()
        );

        schema.addField(
                AddFieldReq.builder()
                        .fieldName(VECTOR_FIELD)
                        .dataType(DataType.FloatVector)
                        .dimension(properties.getDimension())
                        .build()
        );

        IndexParam vectorIndex = IndexParam.builder()
                .fieldName(VECTOR_FIELD)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.COSINE)
                .build();

        CreateCollectionReq request =
                CreateCollectionReq.builder()
                        .collectionName(
                                properties.getCollectionName()
                        )
                        .collectionSchema(schema)
                        .indexParams(List.of(vectorIndex))
                        .build();

        milvusClient.createCollection(request);
    }
}
