package com.kun.aiinterview.knowledge.vector.milvus;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import static com.kun.aiinterview.knowledge.vector.milvus
        .MilvusSchemaConstants.*;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            validateExistingCollection();
            return;
        }

        createCollection();
    }

    private void validateExistingCollection() {
        DescribeCollectionResp collection = milvusClient.describeCollection(
                DescribeCollectionReq.builder()
                        .collectionName(properties.getCollectionName())
                        .build()
        );

        validateCollectionSchema(collection);

        DescribeIndexResp index = milvusClient.describeIndex(
                DescribeIndexReq.builder()
                        .collectionName(properties.getCollectionName())
                        .fieldName(VECTOR_FIELD)
                        .build()
        );

        validateVectorIndex(index);
    }

    private void validateCollectionSchema(
            DescribeCollectionResp response
    ) {
        if (response == null) {
            throw schemaMismatch(
                    "describeCollection response",
                    "non-null",
                    null
            );
        }

        validateInvariant(
                "primary field",
                VECTOR_ID_FIELD,
                response.getPrimaryFieldName()
        );
        validateInvariant(
                "collection autoID",
                false,
                response.getAutoID()
        );
        validateInvariant(
                "collection enableDynamicField",
                false,
                response.getEnableDynamicField()
        );

        CreateCollectionReq.CollectionSchema schema =
                response.getCollectionSchema();
        if (schema == null) {
            throw schemaMismatch(
                    "collection schema",
                    "non-null",
                    null
            );
        }
        validateInvariant(
                "schema enableDynamicField",
                false,
                schema.isEnableDynamicField()
        );

        Map<String, CreateCollectionReq.FieldSchema> fields =
                mapFields(schema.getFieldSchemaList());
        Set<String> expectedFieldNames = new LinkedHashSet<>(List.of(
                VECTOR_ID_FIELD,
                DOCUMENT_ID_FIELD,
                CHUNK_INDEX_FIELD,
                EMBEDDING_VERSION_FIELD,
                VECTOR_FIELD
        ));
        if (!fields.keySet().equals(expectedFieldNames)) {
            throw schemaMismatch(
                    "field set",
                    expectedFieldNames,
                    fields.keySet()
            );
        }

        CreateCollectionReq.FieldSchema vectorId = fields.get(
                VECTOR_ID_FIELD
        );
        validateFieldType(vectorId, DataType.VarChar);
        validateInvariant(
                "field '" + VECTOR_ID_FIELD + "' primary key",
                true,
                vectorId.getIsPrimaryKey()
        );
        validateInvariant(
                "field '" + VECTOR_ID_FIELD + "' autoID",
                false,
                vectorId.getAutoID()
        );
        validateInvariant(
                "field '" + VECTOR_ID_FIELD + "' maxLength",
                VECTOR_ID_MAX_LENGTH,
                vectorId.getMaxLength()
        );

        validateFieldType(fields.get(DOCUMENT_ID_FIELD), DataType.Int64);
        validateFieldType(fields.get(CHUNK_INDEX_FIELD), DataType.Int64);

        CreateCollectionReq.FieldSchema embeddingVersion = fields.get(
                EMBEDDING_VERSION_FIELD
        );
        validateFieldType(embeddingVersion, DataType.VarChar);
        validateInvariant(
                "field '" + EMBEDDING_VERSION_FIELD + "' maxLength",
                EMBEDDING_VERSION_MAX_LENGTH,
                embeddingVersion.getMaxLength()
        );

        CreateCollectionReq.FieldSchema vector = fields.get(VECTOR_FIELD);
        validateFieldType(vector, DataType.FloatVector);
        validateInvariant(
                "field '" + VECTOR_FIELD + "' dimension",
                properties.getDimension(),
                vector.getDimension()
        );
    }

    private Map<String, CreateCollectionReq.FieldSchema> mapFields(
            List<CreateCollectionReq.FieldSchema> fieldSchemaList
    ) {
        if (fieldSchemaList == null) {
            throw schemaMismatch("field set", "five required fields", null);
        }

        Map<String, CreateCollectionReq.FieldSchema> fields =
                new LinkedHashMap<>();
        for (CreateCollectionReq.FieldSchema field : fieldSchemaList) {
            if (field == null || field.getName() == null) {
                throw schemaMismatch(
                        "field definition",
                        "non-null field with a name",
                        field
                );
            }
            if (fields.putIfAbsent(field.getName(), field) != null) {
                throw schemaMismatch(
                        "unique field names",
                        "no duplicate field",
                        field.getName()
                );
            }
        }
        return fields;
    }

    private void validateFieldType(
            CreateCollectionReq.FieldSchema field,
            DataType expectedType
    ) {
        validateInvariant(
                "field '" + field.getName() + "' type",
                expectedType,
                field.getDataType()
        );
    }

    private void validateVectorIndex(DescribeIndexResp response) {
        if (response == null || response.getIndexDescriptions() == null) {
            throw schemaMismatch(
                    "index on field '" + VECTOR_FIELD + "'",
                    "one index description",
                    null
            );
        }

        List<DescribeIndexResp.IndexDesc> vectorIndexes = response
                .getIndexDescriptions()
                .stream()
                .filter(index -> index != null
                        && VECTOR_FIELD.equals(index.getFieldName()))
                .toList();
        if (vectorIndexes.size() != 1) {
            List<String> actualFields = response.getIndexDescriptions()
                    .stream()
                    .map(index -> index == null
                            ? null
                            : index.getFieldName())
                    .toList();
            throw schemaMismatch(
                    "index field",
                    VECTOR_FIELD,
                    actualFields
            );
        }

        DescribeIndexResp.IndexDesc vectorIndex = vectorIndexes.getFirst();
        validateInvariant(
                "index metric on field '" + VECTOR_FIELD + "'",
                IndexParam.MetricType.COSINE,
                vectorIndex.getMetricType()
        );
        validateInvariant(
                "index type on field '" + VECTOR_FIELD + "'",
                IndexParam.IndexType.AUTOINDEX,
                vectorIndex.getIndexType()
        );
    }

    private void validateInvariant(
            String invariant,
            Object expected,
            Object actual
    ) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw schemaMismatch(invariant, expected, actual);
        }
    }

    private IllegalStateException schemaMismatch(
            String invariant,
            Object expected,
            Object actual
    ) {
        return new IllegalStateException(
                "Milvus Collection '" + properties.getCollectionName()
                        + "' schema mismatch: " + invariant
                        + ", expected=" + expected
                        + ", actual=" + actual
        );
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
