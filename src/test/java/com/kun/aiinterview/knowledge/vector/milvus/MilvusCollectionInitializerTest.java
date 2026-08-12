package com.kun.aiinterview.knowledge.vector.milvus;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.exception.ErrorCode;
import io.milvus.v2.exception.MilvusClientException;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.CHUNK_INDEX_FIELD;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.DOCUMENT_ID_FIELD;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.EMBEDDING_VERSION_FIELD;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.EMBEDDING_VERSION_MAX_LENGTH;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.VECTOR_FIELD;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.VECTOR_ID_FIELD;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.VECTOR_ID_MAX_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MilvusCollectionInitializerTest {

    private static final int EXPECTED_DIMENSION = 1024;

    private MilvusClientV2 milvusClient;
    private MilvusCollectionInitializer initializer;

    @BeforeEach
    void setUp() {
        milvusClient = mock(MilvusClientV2.class);
        initializer = new MilvusCollectionInitializer(
                milvusClient,
                testProperties()
        );
    }

    @Test
    void shouldValidateCompatibleExistingCollectionWithoutCreatingIt() {
        stubExistingCollection(
                compatibleCollectionResponse(),
                compatibleIndexResponse()
        );

        initializer.run(mock(ApplicationArguments.class));

        ArgumentCaptor<DescribeCollectionReq> collectionCaptor =
                ArgumentCaptor.forClass(DescribeCollectionReq.class);
        verify(milvusClient).describeCollection(collectionCaptor.capture());
        assertThat(collectionCaptor.getValue().getCollectionName())
                .isEqualTo("test_collection");

        ArgumentCaptor<DescribeIndexReq> indexCaptor =
                ArgumentCaptor.forClass(DescribeIndexReq.class);
        verify(milvusClient).describeIndex(indexCaptor.capture());
        assertThat(indexCaptor.getValue().getCollectionName())
                .isEqualTo("test_collection");
        assertThat(indexCaptor.getValue().getFieldName())
                .isEqualTo(VECTOR_FIELD);
        verify(milvusClient, never())
                .createCollection(any(CreateCollectionReq.class));
    }

    @Test
    void shouldCreateExpectedSchemaAndCosineAutoIndexWhenMissing() {
        when(milvusClient.hasCollection(any(HasCollectionReq.class)))
                .thenReturn(false);

        initializer.run(mock(ApplicationArguments.class));

        ArgumentCaptor<CreateCollectionReq> requestCaptor =
                ArgumentCaptor.forClass(CreateCollectionReq.class);
        verify(milvusClient).createCollection(requestCaptor.capture());

        CreateCollectionReq request = requestCaptor.getValue();
        assertThat(request.getCollectionName()).isEqualTo("test_collection");

        CreateCollectionReq.CollectionSchema schema =
                request.getCollectionSchema();
        assertThat(schema.isEnableDynamicField()).isFalse();
        assertThat(schema.getFieldSchemaList()).hasSize(5);

        CreateCollectionReq.FieldSchema vectorId =
                schema.getField(VECTOR_ID_FIELD);
        assertThat(vectorId.getDataType()).isEqualTo(DataType.VarChar);
        assertThat(vectorId.getIsPrimaryKey()).isTrue();
        assertThat(vectorId.getAutoID()).isFalse();
        assertThat(vectorId.getMaxLength())
                .isEqualTo(VECTOR_ID_MAX_LENGTH);

        assertThat(schema.getField(DOCUMENT_ID_FIELD).getDataType())
                .isEqualTo(DataType.Int64);
        assertThat(schema.getField(CHUNK_INDEX_FIELD).getDataType())
                .isEqualTo(DataType.Int64);

        CreateCollectionReq.FieldSchema embeddingVersion =
                schema.getField(EMBEDDING_VERSION_FIELD);
        assertThat(embeddingVersion.getDataType())
                .isEqualTo(DataType.VarChar);
        assertThat(embeddingVersion.getMaxLength())
                .isEqualTo(EMBEDDING_VERSION_MAX_LENGTH);

        CreateCollectionReq.FieldSchema vector =
                schema.getField(VECTOR_FIELD);
        assertThat(vector.getDataType()).isEqualTo(DataType.FloatVector);
        assertThat(vector.getDimension()).isEqualTo(EXPECTED_DIMENSION);

        assertThat(request.getIndexParams()).singleElement()
                .satisfies(index -> {
                    assertThat(index.getFieldName())
                            .isEqualTo(VECTOR_FIELD);
                    assertThat(index.getIndexType())
                            .isEqualTo(IndexParam.IndexType.AUTOINDEX);
                    assertThat(index.getMetricType())
                            .isEqualTo(IndexParam.MetricType.COSINE);
                });
        verify(milvusClient, never())
                .describeCollection(any(DescribeCollectionReq.class));
        verify(milvusClient, never())
                .describeIndex(any(DescribeIndexReq.class));
    }

    @Test
    void shouldFailFastWhenVectorDimensionIsWrong() {
        List<CreateCollectionReq.FieldSchema> fields = expectedFields();
        fields.set(4, vectorField(768));
        stubExistingCollection(
                collectionResponse(fields, false, VECTOR_ID_FIELD, false),
                compatibleIndexResponse()
        );

        assertSchemaMismatch("field 'vector' dimension", 1024, 768);

        verify(milvusClient, never())
                .describeIndex(any(DescribeIndexReq.class));
    }

    @Test
    void shouldFailFastWhenRequiredFieldIsMissing() {
        List<CreateCollectionReq.FieldSchema> fields = expectedFields();
        fields.remove(3);
        stubExistingCollection(
                collectionResponse(fields, false, VECTOR_ID_FIELD, false),
                compatibleIndexResponse()
        );

        assertThatThrownBy(() -> initializer.run(
                mock(ApplicationArguments.class)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test_collection")
                .hasMessageContaining("field set")
                .hasMessageContaining(EMBEDDING_VERSION_FIELD);
    }

    @Test
    void shouldFailFastWhenDocumentIdTypeIsWrong() {
        List<CreateCollectionReq.FieldSchema> fields = expectedFields();
        fields.set(1, field(DOCUMENT_ID_FIELD, DataType.VarChar));
        stubExistingCollection(
                collectionResponse(fields, false, VECTOR_ID_FIELD, false),
                compatibleIndexResponse()
        );

        assertSchemaMismatch(
                "field 'document_id' type",
                DataType.Int64,
                DataType.VarChar
        );
    }

    @Test
    void shouldFailFastWhenVectorIdIsNotPrimaryKey() {
        List<CreateCollectionReq.FieldSchema> fields = expectedFields();
        fields.set(0, vectorIdField(false, false, VECTOR_ID_MAX_LENGTH));
        stubExistingCollection(
                collectionResponse(fields, false, VECTOR_ID_FIELD, false),
                compatibleIndexResponse()
        );

        assertSchemaMismatch("field 'vector_id' primary key", true, false);
    }

    @Test
    void shouldFailFastWhenVectorIdUsesAutoId() {
        List<CreateCollectionReq.FieldSchema> fields = expectedFields();
        fields.set(0, vectorIdField(true, true, VECTOR_ID_MAX_LENGTH));
        stubExistingCollection(
                collectionResponse(fields, false, VECTOR_ID_FIELD, false),
                compatibleIndexResponse()
        );

        assertSchemaMismatch("field 'vector_id' autoID", false, true);
    }

    @Test
    void shouldFailFastWhenVectorIdMaxLengthIsWrong() {
        List<CreateCollectionReq.FieldSchema> fields = expectedFields();
        fields.set(0, vectorIdField(true, false, 32));
        stubExistingCollection(
                collectionResponse(fields, false, VECTOR_ID_FIELD, false),
                compatibleIndexResponse()
        );

        assertSchemaMismatch("field 'vector_id' maxLength", 64, 32);
    }

    @Test
    void shouldFailFastWhenDynamicFieldsAreEnabled() {
        stubExistingCollection(
                collectionResponse(
                        expectedFields(),
                        true,
                        VECTOR_ID_FIELD,
                        false
                ),
                compatibleIndexResponse()
        );

        assertSchemaMismatch(
                "collection enableDynamicField",
                false,
                true
        );
    }

    @Test
    void shouldFailFastWhenCollectionPrimaryFieldIsWrong() {
        stubExistingCollection(
                collectionResponse(
                        expectedFields(),
                        false,
                        DOCUMENT_ID_FIELD,
                        false
                ),
                compatibleIndexResponse()
        );

        assertSchemaMismatch(
                "primary field",
                VECTOR_ID_FIELD,
                DOCUMENT_ID_FIELD
        );
    }

    @Test
    void shouldFailFastWhenCollectionAutoIdIsEnabled() {
        stubExistingCollection(
                collectionResponse(
                        expectedFields(),
                        false,
                        VECTOR_ID_FIELD,
                        true
                ),
                compatibleIndexResponse()
        );

        assertSchemaMismatch("collection autoID", false, true);
    }

    @Test
    void shouldFailFastWhenVectorIndexTargetsWrongField() {
        stubExistingCollection(
                compatibleCollectionResponse(),
                indexResponse(DOCUMENT_ID_FIELD, IndexParam.MetricType.COSINE)
        );

        assertThatThrownBy(() -> initializer.run(
                mock(ApplicationArguments.class)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test_collection")
                .hasMessageContaining("index field")
                .hasMessageContaining(VECTOR_FIELD)
                .hasMessageContaining(DOCUMENT_ID_FIELD);
    }

    @Test
    void shouldFailFastWhenVectorIndexMetricIsWrong() {
        stubExistingCollection(
                compatibleCollectionResponse(),
                indexResponse(VECTOR_FIELD, IndexParam.MetricType.L2)
        );

        assertSchemaMismatch(
                "index metric on field 'vector'",
                IndexParam.MetricType.COSINE,
                IndexParam.MetricType.L2
        );
    }

    @Test
    void shouldFailFastWhenVectorIndexTypeIsWrong() {
        stubExistingCollection(
                compatibleCollectionResponse(),
                indexResponse(
                        VECTOR_FIELD,
                        IndexParam.IndexType.HNSW,
                        IndexParam.MetricType.COSINE
                )
        );

        assertSchemaMismatch(
                "index type on field 'vector'",
                IndexParam.IndexType.AUTOINDEX,
                IndexParam.IndexType.HNSW
        );
    }

    @Test
    void shouldPropagateSdkExceptionFromExistenceCheck() {
        MilvusClientException failure = sdkFailure();
        when(milvusClient.hasCollection(any(HasCollectionReq.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> initializer.run(
                mock(ApplicationArguments.class)
        )).isSameAs(failure);
        verify(milvusClient, never())
                .createCollection(any(CreateCollectionReq.class));
    }

    @Test
    void shouldPropagateSdkExceptionFromDescribeCollection() {
        MilvusClientException failure = sdkFailure();
        when(milvusClient.hasCollection(any(HasCollectionReq.class)))
                .thenReturn(true);
        when(milvusClient.describeCollection(
                any(DescribeCollectionReq.class)
        )).thenThrow(failure);

        assertThatThrownBy(() -> initializer.run(
                mock(ApplicationArguments.class)
        )).isSameAs(failure);
        verify(milvusClient, never())
                .describeIndex(any(DescribeIndexReq.class));
    }

    @Test
    void shouldPropagateSdkExceptionFromDescribeIndex() {
        MilvusClientException failure = sdkFailure();
        when(milvusClient.hasCollection(any(HasCollectionReq.class)))
                .thenReturn(true);
        when(milvusClient.describeCollection(
                any(DescribeCollectionReq.class)
        )).thenReturn(compatibleCollectionResponse());
        when(milvusClient.describeIndex(any(DescribeIndexReq.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> initializer.run(
                mock(ApplicationArguments.class)
        )).isSameAs(failure);
    }

    private void stubExistingCollection(
            DescribeCollectionResp collection,
            DescribeIndexResp index
    ) {
        when(milvusClient.hasCollection(any(HasCollectionReq.class)))
                .thenReturn(true);
        when(milvusClient.describeCollection(
                any(DescribeCollectionReq.class)
        )).thenReturn(collection);
        when(milvusClient.describeIndex(any(DescribeIndexReq.class)))
                .thenReturn(index);
    }

    private void assertSchemaMismatch(
            String invariant,
            Object expected,
            Object actual
    ) {
        assertThatThrownBy(() -> initializer.run(
                mock(ApplicationArguments.class)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test_collection")
                .hasMessageContaining(invariant)
                .hasMessageContaining("expected=" + expected)
                .hasMessageContaining("actual=" + actual);
    }

    private static DescribeCollectionResp compatibleCollectionResponse() {
        return collectionResponse(
                expectedFields(),
                false,
                VECTOR_ID_FIELD,
                false
        );
    }

    private static DescribeCollectionResp collectionResponse(
            List<CreateCollectionReq.FieldSchema> fields,
            boolean dynamicFieldsEnabled,
            String primaryField,
            boolean autoId
    ) {
        CreateCollectionReq.CollectionSchema schema =
                CreateCollectionReq.CollectionSchema.builder()
                        .fieldSchemaList(fields)
                        .enableDynamicField(dynamicFieldsEnabled)
                        .build();
        return DescribeCollectionResp.builder()
                .collectionName("test_collection")
                .fieldNames(fields.stream()
                        .map(CreateCollectionReq.FieldSchema::getName)
                        .toList())
                .vectorFieldNames(List.of(VECTOR_FIELD))
                .primaryFieldName(primaryField)
                .enableDynamicField(dynamicFieldsEnabled)
                .autoID(autoId)
                .collectionSchema(schema)
                .build();
    }

    private static List<CreateCollectionReq.FieldSchema> expectedFields() {
        return new ArrayList<>(List.of(
                vectorIdField(true, false, VECTOR_ID_MAX_LENGTH),
                field(DOCUMENT_ID_FIELD, DataType.Int64),
                field(CHUNK_INDEX_FIELD, DataType.Int64),
                CreateCollectionReq.FieldSchema.builder()
                        .name(EMBEDDING_VERSION_FIELD)
                        .dataType(DataType.VarChar)
                        .maxLength(EMBEDDING_VERSION_MAX_LENGTH)
                        .build(),
                vectorField(EXPECTED_DIMENSION)
        ));
    }

    private static CreateCollectionReq.FieldSchema vectorIdField(
            boolean primaryKey,
            boolean autoId,
            int maxLength
    ) {
        return CreateCollectionReq.FieldSchema.builder()
                .name(VECTOR_ID_FIELD)
                .dataType(DataType.VarChar)
                .isPrimaryKey(primaryKey)
                .autoID(autoId)
                .maxLength(maxLength)
                .build();
    }

    private static CreateCollectionReq.FieldSchema field(
            String name,
            DataType dataType
    ) {
        return CreateCollectionReq.FieldSchema.builder()
                .name(name)
                .dataType(dataType)
                .build();
    }

    private static CreateCollectionReq.FieldSchema vectorField(
            int dimension
    ) {
        return CreateCollectionReq.FieldSchema.builder()
                .name(VECTOR_FIELD)
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build();
    }

    private static DescribeIndexResp compatibleIndexResponse() {
        return indexResponse(VECTOR_FIELD, IndexParam.MetricType.COSINE);
    }

    private static DescribeIndexResp indexResponse(
            String fieldName,
            IndexParam.MetricType metricType
    ) {
        return indexResponse(
                fieldName,
                IndexParam.IndexType.AUTOINDEX,
                metricType
        );
    }

    private static DescribeIndexResp indexResponse(
            String fieldName,
            IndexParam.IndexType indexType,
            IndexParam.MetricType metricType
    ) {
        return DescribeIndexResp.builder()
                .indexDescriptions(List.of(
                        DescribeIndexResp.IndexDesc.builder()
                                .fieldName(fieldName)
                                .indexName("vector_index")
                                .indexType(indexType)
                                .metricType(metricType)
                                .build()
                ))
                .build();
    }

    private static MilvusClientException sdkFailure() {
        return new MilvusClientException(
                ErrorCode.CLIENT_ERROR,
                "unit-test failure"
        );
    }

    private static MilvusProperties testProperties() {
        MilvusProperties properties = new MilvusProperties();
        properties.setEnabled(true);
        properties.setUri(URI.create("http://localhost:19530"));
        properties.setDatabaseName("test_database");
        properties.setCollectionName("test_collection");
        properties.setDimension(EXPECTED_DIMENSION);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setRequestTimeout(Duration.ofSeconds(2));
        return properties;
    }
}
