package com.kun.aiinterview.knowledge.vector.milvus;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.exception.ErrorCode;
import io.milvus.v2.exception.MilvusClientException;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;

import java.net.URI;
import java.time.Duration;

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
    void shouldSkipCreationWhenCollectionAlreadyExists() {
        when(milvusClient.hasCollection(any(HasCollectionReq.class)))
                .thenReturn(true);

        initializer.run(mock(ApplicationArguments.class));

        ArgumentCaptor<HasCollectionReq> requestCaptor =
                ArgumentCaptor.forClass(HasCollectionReq.class);
        verify(milvusClient).hasCollection(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getCollectionName())
                .isEqualTo("test_collection");
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
        assertThat(vector.getDimension()).isEqualTo(3);

        assertThat(request.getIndexParams()).singleElement()
                .satisfies(index -> {
                    assertThat(index.getFieldName())
                            .isEqualTo(VECTOR_FIELD);
                    assertThat(index.getIndexType())
                            .isEqualTo(IndexParam.IndexType.AUTOINDEX);
                    assertThat(index.getMetricType())
                            .isEqualTo(IndexParam.MetricType.COSINE);
                });
    }

    @Test
    void shouldPropagateSdkExceptionFromExistenceCheck() {
        MilvusClientException failure = new MilvusClientException(
                ErrorCode.CLIENT_ERROR,
                "unit-test failure"
        );
        when(milvusClient.hasCollection(any(HasCollectionReq.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> initializer.run(
                mock(ApplicationArguments.class)
        ))
                .isSameAs(failure);
        verify(milvusClient, never())
                .createCollection(any(CreateCollectionReq.class));
    }

    private static MilvusProperties testProperties() {
        MilvusProperties properties = new MilvusProperties();
        properties.setEnabled(true);
        properties.setUri(URI.create("http://localhost:19530"));
        properties.setDatabaseName("test_database");
        properties.setCollectionName("test_collection");
        properties.setDimension(3);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setRequestTimeout(Duration.ofSeconds(2));
        return properties;
    }
}
