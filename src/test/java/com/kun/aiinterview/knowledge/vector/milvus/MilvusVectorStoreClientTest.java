package com.kun.aiinterview.knowledge.vector.milvus;

import com.google.gson.JsonArray;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.knowledge.vector.VectorWriteItem;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.exception.ErrorCode;
import io.milvus.v2.exception.MilvusClientException;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.response.InsertResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MilvusVectorStoreClientTest {

    private MilvusClientV2 milvusClient;
    private MilvusVectorStoreClient client;

    @BeforeEach
    void setUp() {
        milvusClient = mock(MilvusClientV2.class);
        client = new MilvusVectorStoreClient(
                milvusClient,
                testProperties()
        );
    }

    @Test
    void shouldRejectNullItemsWithoutCallingMilvus() {
        assertThatThrownBy(() -> client.insert(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldRejectEmptyItemsWithoutCallingMilvus() {
        assertThatThrownBy(() -> client.insert(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("空");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldRejectNullItemWithCollectionIndexWithoutCallingMilvus() {
        List<VectorWriteItem> items = new ArrayList<>();
        items.add(validItem("vector-1", 1L, 1));
        items.add(null);

        assertThatThrownBy(() -> client.insert(items))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("集合索引：1");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldRejectDuplicateVectorIdWithoutCallingMilvus() {
        List<VectorWriteItem> items = List.of(
                validItem("duplicate", 1L, 1),
                validItem("duplicate", 1L, 2)
        );

        assertThatThrownBy(() -> client.insert(items))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复")
                .hasMessageContaining("duplicate");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldRejectOverlongVectorIdWithoutCallingMilvus() {
        VectorWriteItem item = validItem(
                "v".repeat(VECTOR_ID_MAX_LENGTH + 1),
                1L,
                1
        );

        assertThatThrownBy(() -> client.insert(List.of(item)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vectorId")
                .hasMessageContaining("集合索引：0");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldRejectOverlongEmbeddingVersionWithoutCallingMilvus() {
        VectorWriteItem item = new VectorWriteItem(
                "vector-1",
                1L,
                1,
                "e".repeat(EMBEDDING_VERSION_MAX_LENGTH + 1),
                List.of(0.1F, 0.2F, 0.3F)
        );

        assertThatThrownBy(() -> client.insert(List.of(item)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本")
                .hasMessageContaining("集合索引：0");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldRejectWrongDimensionWithoutCallingMilvus() {
        VectorWriteItem item = new VectorWriteItem(
                "vector-1",
                1L,
                1,
                "embedding-v1",
                List.of(0.1F, 0.2F)
        );

        assertThatThrownBy(() -> client.insert(List.of(item)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("期望维度：3")
                .hasMessageContaining("实际维度：2");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldCreateExpectedSingleRowInsertRequest() {
        when(milvusClient.insert(any(InsertReq.class)))
                .thenReturn(successfulResponse(1));

        client.insert(List.of(validItem("vector-1", 101L, 2)));

        InsertReq request = capturedInsertRequest();
        assertThat(request.getCollectionName()).isEqualTo("test_collection");
        assertThat(request.getData()).singleElement()
                .satisfies(row -> {
                    assertThat(row.get(VECTOR_ID_FIELD).getAsString())
                            .isEqualTo("vector-1");
                    assertThat(row.get(DOCUMENT_ID_FIELD).getAsLong())
                            .isEqualTo(101L);
                    assertThat(row.get(CHUNK_INDEX_FIELD).getAsInt())
                            .isEqualTo(2);
                    assertThat(row.get(EMBEDDING_VERSION_FIELD).getAsString())
                            .isEqualTo("embedding-v1");

                    assertThat(row.get(VECTOR_FIELD).isJsonArray()).isTrue();
                    JsonArray vector = row.getAsJsonArray(VECTOR_FIELD);
                    assertThat(vector.asList())
                            .extracting(element -> element.getAsFloat())
                            .containsExactly(0.1F, -0.2F, 0.3F);
                });
    }

    @Test
    void shouldCreateOneJsonRowPerItemInOriginalOrder() {
        when(milvusClient.insert(any(InsertReq.class)))
                .thenReturn(successfulResponse(2));

        client.insert(List.of(
                validItem("vector-1", 101L, 1),
                validItem("vector-2", 101L, 2)
        ));

        InsertReq request = capturedInsertRequest();
        assertThat(request.getData())
                .extracting(row -> row.get(VECTOR_ID_FIELD).getAsString())
                .containsExactly("vector-1", "vector-2");
        assertThat(request.getData())
                .extracting(row -> row.get(CHUNK_INDEX_FIELD).getAsInt())
                .containsExactly(1, 2);
    }

    @Test
    void shouldConvertSdkExceptionAndPreserveCause() {
        MilvusClientException failure = new MilvusClientException(
                ErrorCode.CLIENT_ERROR,
                "unit-test failure"
        );
        when(milvusClient.insert(any(InsertReq.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> client.insert(
                List.of(validItem("vector-1", 1L, 1))
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("Milvus")
                .hasCause(failure);
    }

    @Test
    void shouldRejectNullInsertResponse() {
        when(milvusClient.insert(any(InsertReq.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> client.insert(
                List.of(validItem("vector-1", 1L, 1))
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("响应");
    }

    @Test
    void shouldRejectUnexpectedInsertCount() {
        when(milvusClient.insert(any(InsertReq.class)))
                .thenReturn(successfulResponse(0));

        assertThatThrownBy(() -> client.insert(
                List.of(validItem("vector-1", 1L, 1))
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("数量");
    }

    @Test
    void shouldKeepDeleteAndSearchOperationsUnsupported() {
        assertThatThrownBy(() -> client.deleteByVectorIds(
                List.of("vector-1")
        )).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> client.deleteByDocumentId(1L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> client.search(
                List.of(0.1F, 0.2F, 0.3F),
                "embedding-v1",
                5
        )).isInstanceOf(UnsupportedOperationException.class);

        verifyNoInteractions(milvusClient);
    }

    private InsertReq capturedInsertRequest() {
        ArgumentCaptor<InsertReq> requestCaptor =
                ArgumentCaptor.forClass(InsertReq.class);
        verify(milvusClient).insert(requestCaptor.capture());
        return requestCaptor.getValue();
    }

    private static VectorWriteItem validItem(
            String vectorId,
            long documentId,
            int chunkIndex
    ) {
        return new VectorWriteItem(
                vectorId,
                documentId,
                chunkIndex,
                "embedding-v1",
                List.of(0.1F, -0.2F, 0.3F)
        );
    }

    private static InsertResp successfulResponse(long insertCount) {
        return InsertResp.builder()
                .InsertCnt(insertCount)
                .build();
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
