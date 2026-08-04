package com.kun.aiinterview.knowledge.vector.milvus;

import com.google.gson.JsonArray;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import com.kun.aiinterview.knowledge.vector.VectorWriteItem;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.exception.ErrorCode;
import io.milvus.v2.exception.MilvusClientException;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.CHUNK_INDEX_FIELD;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.DOCUMENT_ID_FIELD;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.EMBEDDING_VERSION_FIELD;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.EMBEDDING_VERSION_MAX_LENGTH;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.VECTOR_FIELD;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.VECTOR_ID_FIELD;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.VECTOR_ID_MAX_LENGTH;
import static org.assertj.core.api.Assertions.within;
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
    void shouldRejectNullVectorIdListWithoutCallingMilvus() {
        assertThatThrownBy(() -> client.deleteByVectorIds(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldRejectEmptyVectorIdListWithoutCallingMilvus() {
        assertThatThrownBy(() -> client.deleteByVectorIds(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("空");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldRejectNullVectorIdWithCollectionIndexWithoutCallingMilvus() {
        List<String> vectorIds = new ArrayList<>();
        vectorIds.add("vector-1");
        vectorIds.add(null);

        assertThatThrownBy(() -> client.deleteByVectorIds(vectorIds))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("集合索引：1");

        verifyNoInteractions(milvusClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \t "})
    void shouldRejectBlankVectorIdWithCollectionIndexWithoutCallingMilvus(
            String vectorId
    ) {
        assertThatThrownBy(() -> client.deleteByVectorIds(
                List.of("vector-1", vectorId)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("集合索引：1");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldRejectOverlongDeleteVectorIdWithoutCallingMilvus() {
        String vectorId = "v".repeat(VECTOR_ID_MAX_LENGTH + 1);

        assertThatThrownBy(() -> client.deleteByVectorIds(
                List.of(vectorId)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("长度")
                .hasMessageContaining("集合索引：0");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldRejectDuplicateDeleteVectorIdWithoutCallingMilvus() {
        assertThatThrownBy(() -> client.deleteByVectorIds(
                List.of("duplicate", "vector-2", "duplicate")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复")
                .hasMessageContaining("duplicate");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldCreateExpectedDeleteByVectorIdsRequestFromImmutableCopy() {
        List<String> vectorIds = new ArrayList<>(List.of(
                " vector-0 ",
                "vector'1",
                "vector\"2",
                "vector\\3"
        ));
        when(milvusClient.delete(any(DeleteReq.class)))
                .thenReturn(successfulDeleteResponse(4));

        client.deleteByVectorIds(vectorIds);
        vectorIds.set(0, "changed-after-call");

        DeleteReq request = capturedDeleteRequest();
        assertThat(request.getCollectionName()).isEqualTo("test_collection");
        assertThat(request.getFilter())
                .isEqualTo(VECTOR_ID_FIELD + " in {vectorIds}")
                .doesNotContain("vector'1", "vector\"2", "vector\\3");
        assertThat(request.getFilterTemplateValues())
                .containsOnlyKeys("vectorIds");
        assertThat(deleteVectorIdsFrom(request)).containsExactly(
                " vector-0 ",
                "vector'1",
                "vector\"2",
                "vector\\3"
        );
        assertThat(request.getIds()).isNull();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 2L})
    void shouldAllowNonNegativeDeleteCountForVectorIds(long deleteCount) {
        when(milvusClient.delete(any(DeleteReq.class)))
                .thenReturn(successfulDeleteResponse(deleteCount));

        client.deleteByVectorIds(List.of("vector-1"));

        verify(milvusClient).delete(any(DeleteReq.class));
    }

    @Test
    void shouldRejectNullDeleteByVectorIdsResponse() {
        when(milvusClient.delete(any(DeleteReq.class))).thenReturn(null);

        assertThatThrownBy(() -> client.deleteByVectorIds(
                List.of("vector-1")
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("响应");
    }

    @Test
    void shouldRejectNegativeDeleteByVectorIdsCount() {
        when(milvusClient.delete(any(DeleteReq.class)))
                .thenReturn(successfulDeleteResponse(-1));

        assertThatThrownBy(() -> client.deleteByVectorIds(
                List.of("vector-1")
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("负数");
    }

    @Test
    void shouldConvertDeleteByVectorIdsSdkExceptionAndPreserveCause() {
        MilvusClientException failure = sdkFailure();
        when(milvusClient.delete(any(DeleteReq.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> client.deleteByVectorIds(
                List.of("vector-1")
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("向量ID")
                .hasCause(failure);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void shouldRejectNonPositiveDocumentIdWithoutCallingMilvus(
            long documentId
    ) {
        assertThatThrownBy(() -> client.deleteByDocumentId(documentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("大于0");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldCreateExpectedDeleteByDocumentIdRequest() {
        when(milvusClient.delete(any(DeleteReq.class)))
                .thenReturn(successfulDeleteResponse(1));

        client.deleteByDocumentId(101L);

        DeleteReq request = capturedDeleteRequest();
        assertThat(request.getCollectionName()).isEqualTo("test_collection");
        assertThat(request.getFilter())
                .isEqualTo(DOCUMENT_ID_FIELD + " == {documentId}");
        assertThat(request.getFilterTemplateValues())
                .containsOnlyKeys("documentId")
                .containsEntry("documentId", 101L);
        assertThat(request.getIds()).isNull();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 3L})
    void shouldAllowNonNegativeDeleteCountForDocumentId(long deleteCount) {
        when(milvusClient.delete(any(DeleteReq.class)))
                .thenReturn(successfulDeleteResponse(deleteCount));

        client.deleteByDocumentId(101L);

        verify(milvusClient).delete(any(DeleteReq.class));
    }

    @Test
    void shouldRejectNullDeleteByDocumentIdResponse() {
        when(milvusClient.delete(any(DeleteReq.class))).thenReturn(null);

        assertThatThrownBy(() -> client.deleteByDocumentId(101L))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("响应");
    }

    @Test
    void shouldRejectNegativeDeleteByDocumentIdCount() {
        when(milvusClient.delete(any(DeleteReq.class)))
                .thenReturn(successfulDeleteResponse(-1));

        assertThatThrownBy(() -> client.deleteByDocumentId(101L))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("负数");
    }

    @Test
    void shouldConvertDeleteByDocumentIdSdkExceptionAndPreserveCause() {
        MilvusClientException failure = sdkFailure();
        when(milvusClient.delete(any(DeleteReq.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> client.deleteByDocumentId(101L))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("文档ID")
                .hasCause(failure);
    }

    @Test
    void shouldRejectNullQueryVectorWithoutCallingMilvus() {
        assertThatThrownBy(() -> client.search(
                null,
                "embedding-v1",
                5
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldRejectEmptyQueryVectorWithoutCallingMilvus() {
        assertThatThrownBy(() -> client.search(
                List.of(),
                "embedding-v1",
                5
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("空");

        verifyNoInteractions(milvusClient);
    }

    @ParameterizedTest
    @MethodSource("invalidQueryVectorValues")
    void shouldRejectNullOrNonFiniteQueryValueWithoutCallingMilvus(
            Float invalidValue
    ) {
        List<Float> queryVector = new ArrayList<>();
        queryVector.add(0.1F);
        queryVector.add(invalidValue);
        queryVector.add(0.3F);

        assertThatThrownBy(() -> client.search(
                queryVector,
                "embedding-v1",
                5
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("集合索引：1");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldRejectWrongQueryDimensionWithoutCallingMilvus() {
        assertThatThrownBy(() -> client.search(
                List.of(0.1F, 0.2F),
                "embedding-v1",
                5
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("期望维度：3")
                .hasMessageContaining("实际维度：2");

        verifyNoInteractions(milvusClient);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t "})
    void shouldRejectMissingOrBlankSearchEmbeddingVersionWithoutCallingMilvus(
            String embeddingVersion
    ) {
        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                embeddingVersion,
                5
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本");

        verifyNoInteractions(milvusClient);
    }

    @Test
    void shouldRejectOverlongSearchEmbeddingVersionWithoutCallingMilvus() {
        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "e".repeat(EMBEDDING_VERSION_MAX_LENGTH + 1),
                5
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("长度");

        verifyNoInteractions(milvusClient);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectNonPositiveTopKWithoutCallingMilvus(int topK) {
        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "embedding-v1",
                topK
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("大于0");

        verifyNoInteractions(milvusClient);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void shouldAcceptPositiveTopK(int topK) {
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(emptySearchResponse());

        assertThat(client.search(
                validQueryVector(),
                "embedding-v1",
                topK
        )).isEmpty();

        assertThat(capturedSearchRequest().getLimit()).isEqualTo(topK);
    }

    @Test
    void shouldCreateExpectedSearchRequestWithoutTrimmingVersion() {
        List<Float> queryVector = new ArrayList<>(validQueryVector());
        String embeddingVersion = " embedding-v1 ";
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(emptySearchResponse());

        client.search(queryVector, embeddingVersion, 5);
        queryVector.set(0, 9.9F);

        SearchReq request = capturedSearchRequest();
        assertThat(request.getCollectionName()).isEqualTo("test_collection");
        assertThat(request.getAnnsField()).isEqualTo(VECTOR_FIELD);
        assertThat(request.getMetricType())
                .isEqualTo(IndexParam.MetricType.COSINE);
        assertThat(request.getData()).singleElement()
                .isInstanceOf(FloatVec.class);
        assertThat(queryVectorFrom(request))
                .containsExactly(0.1F, -0.2F, 0.3F);
        assertThat(request.getLimit()).isEqualTo(5L);
        assertThat(request.getFilter())
                .isEqualTo(EMBEDDING_VERSION_FIELD
                        + " == {embeddingVersion}");
        assertThat(request.getFilterTemplateValues())
                .containsOnlyKeys("embeddingVersion")
                .containsEntry("embeddingVersion", embeddingVersion);
        assertThat(request.getOutputFields()).containsExactly(
                DOCUMENT_ID_FIELD,
                CHUNK_INDEX_FIELD,
                EMBEDDING_VERSION_FIELD
        );
        assertThat(request.getOutputFields())
                .doesNotContain(VECTOR_FIELD, VECTOR_ID_FIELD);
        assertThat(request.getIds()).isEmpty();
    }

    @Test
    void shouldRejectNullSearchResponse() {
        when(milvusClient.search(any(SearchReq.class))).thenReturn(null);

        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "embedding-v1",
                5
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("响应");
    }

    @Test
    void shouldRejectNullSearchResults() {
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(SearchResp.builder()
                        .searchResults(null)
                        .build());

        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "embedding-v1",
                5
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("结果集合");
    }

    @Test
    void shouldRejectEmptySearchResultGroups() {
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(SearchResp.builder()
                        .searchResults(List.of())
                        .build());

        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "embedding-v1",
                5
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("组数量");
    }

    @Test
    void shouldRejectMultipleSearchResultGroups() {
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(SearchResp.builder()
                        .searchResults(List.of(List.of(), List.of()))
                        .build());

        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "embedding-v1",
                5
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("组数量");
    }

    @Test
    void shouldRejectNullSingleSearchResultGroup() {
        List<List<SearchResp.SearchResult>> resultGroups = new ArrayList<>();
        resultGroups.add(null);
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(SearchResp.builder()
                        .searchResults(resultGroups)
                        .build());

        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "embedding-v1",
                5
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("单组");
    }

    @Test
    void shouldReturnUnmodifiableEmptyListForNoSearchHits() {
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(emptySearchResponse());

        List<VectorSearchHit> hits = client.search(
                validQueryVector(),
                "embedding-v1",
                5
        );

        assertThat(hits).isEmpty();
        assertThatThrownBy(() -> hits.add(validExpectedHit()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldMapResultsAtTopKInMilvusOrderToUnmodifiableList() {
        SearchResp.SearchResult first = validSearchResult(
                "vector-2", 102L, 2L, "embedding-v1", 0.9F
        );
        SearchResp.SearchResult second = validSearchResult(
                "vector-1", 101L, 1L, "embedding-v1", 0.8F
        );
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(searchResponse(List.of(first, second)));

        List<VectorSearchHit> hits = client.search(
                validQueryVector(),
                "embedding-v1",
                2
        );

        assertThat(hits)
                .extracting(VectorSearchHit::vectorId)
                .containsExactly("vector-2", "vector-1");
        assertThatThrownBy(hits::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldAllowFewerSearchResultsThanTopK() {
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(searchResponse(List.of(validSearchResult())));

        assertThat(client.search(
                validQueryVector(),
                "embedding-v1",
                5
        )).hasSize(1);
    }

    @Test
    void shouldRejectMoreSearchResultsThanTopK() {
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(searchResponse(List.of(
                        validSearchResult(),
                        validSearchResult(
                                "vector-2", 102L, 2L,
                                "embedding-v1", 0.8F
                        )
                )));

        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "embedding-v1",
                1
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("topK");
    }

    @Test
    void shouldRejectNullSearchResultWithIndex() {
        List<SearchResp.SearchResult> results = new ArrayList<>();
        results.add(validSearchResult());
        results.add(null);
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(searchResponse(results));

        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "embedding-v1",
                2
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("结果索引：1");
    }

    @Test
    void shouldMapValidSdkSearchResult() {
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(searchResponse(List.of(validSearchResult())));

        VectorSearchHit hit = client.search(
                validQueryVector(),
                "embedding-v1",
                5
        ).getFirst();

        assertThat(hit.vectorId()).isEqualTo("vector-1");
        assertThat(hit.documentId()).isEqualTo(101L);
        assertThat(hit.chunkIndex()).isEqualTo(2);
        assertThat(hit.embeddingVersion()).isEqualTo("embedding-v1");
        assertThat(hit.similarityScore()).isCloseTo(0.85D, within(0.000001D));
    }

    @ParameterizedTest
    @MethodSource("invalidSearchResultIds")
    void shouldRejectInvalidSearchResultId(Object id) {
        SearchResp.SearchResult result = SearchResp.SearchResult.builder()
                .id(id)
                .entity(validEntity())
                .score(0.85F)
                .build();
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(searchResponse(List.of(result)));

        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "embedding-v1",
                5
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("vectorId")
                .hasMessageContaining("结果索引：0");
    }

    @Test
    void shouldRejectNullSearchResultEntity() {
        SearchResp.SearchResult result = SearchResp.SearchResult.builder()
                .id("vector-1")
                .entity(null)
                .score(0.85F)
                .build();
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(searchResponse(List.of(result)));

        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "embedding-v1",
                5
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("输出字段")
                .hasMessageContaining("结果索引：0");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            DOCUMENT_ID_FIELD,
            CHUNK_INDEX_FIELD,
            EMBEDDING_VERSION_FIELD
    })
    void shouldRejectMissingSearchResultEntityField(String fieldName) {
        Map<String, Object> entity = validEntity();
        entity.remove(fieldName);
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(searchResponse(List.of(
                        searchResult("vector-1", entity, 0.85F)
                )));

        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "embedding-v1",
                5
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining(fieldName)
                .hasMessageContaining("结果索引：0");
    }

    @Test
    void shouldRejectUnexpectedDocumentIdType() {
        assertInvalidEntityField(DOCUMENT_ID_FIELD, "101", "document_id");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void shouldRejectNonPositiveSearchResultDocumentId(long documentId) {
        assertInvalidEntityField(DOCUMENT_ID_FIELD, documentId, "documentId");
    }

    @Test
    void shouldRejectUnexpectedChunkIndexType() {
        assertInvalidEntityField(CHUNK_INDEX_FIELD, 2, "chunk_index");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, 2147483648L})
    void shouldRejectOutOfRangeSearchResultChunkIndex(long chunkIndex) {
        assertInvalidEntityField(CHUNK_INDEX_FIELD, chunkIndex, "chunkIndex");
    }

    @ParameterizedTest
    @MethodSource("invalidResponseEmbeddingVersions")
    void shouldRejectInvalidSearchResultEmbeddingVersion(Object version) {
        assertInvalidEntityField(
                EMBEDDING_VERSION_FIELD,
                version,
                EMBEDDING_VERSION_FIELD
        );
    }

    @Test
    void shouldRejectSearchResultEmbeddingVersionMismatch() {
        assertInvalidEntityField(
                EMBEDDING_VERSION_FIELD,
                "embedding-v2",
                "不一致"
        );
    }

    @ParameterizedTest
    @MethodSource("invalidSearchScores")
    void shouldRejectNullOrNonFiniteSearchResultScore(Float score) {
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(searchResponse(List.of(
                        searchResult("vector-1", validEntity(), score)
                )));

        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "embedding-v1",
                5
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("分数")
                .hasMessageContaining("结果索引：0");
    }

    @ParameterizedTest
    @ValueSource(floats = {0.0F, -0.25F, 0.75F})
    void shouldAcceptAnyFiniteSearchResultScore(float score) {
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(searchResponse(List.of(
                        searchResult("vector-1", validEntity(), score)
                )));

        VectorSearchHit hit = client.search(
                validQueryVector(),
                "embedding-v1",
                5
        ).getFirst();

        assertThat(hit.similarityScore()).isEqualTo((double) score);
    }

    @Test
    void shouldConvertSearchSdkExceptionAndPreserveCause() {
        MilvusClientException failure = sdkFailure();
        when(milvusClient.search(any(SearchReq.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "embedding-v1",
                5
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("检索")
                .hasCause(failure);
    }

    @Test
    void shouldPropagateNonSdkSearchRuntimeException() {
        IllegalStateException failure = new IllegalStateException(
                "unit-test non-sdk failure"
        );
        when(milvusClient.search(any(SearchReq.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "embedding-v1",
                5
        )).isSameAs(failure);
    }

    private InsertReq capturedInsertRequest() {
        ArgumentCaptor<InsertReq> requestCaptor =
                ArgumentCaptor.forClass(InsertReq.class);
        verify(milvusClient).insert(requestCaptor.capture());
        return requestCaptor.getValue();
    }

    private DeleteReq capturedDeleteRequest() {
        ArgumentCaptor<DeleteReq> requestCaptor =
                ArgumentCaptor.forClass(DeleteReq.class);
        verify(milvusClient).delete(requestCaptor.capture());
        return requestCaptor.getValue();
    }

    private SearchReq capturedSearchRequest() {
        ArgumentCaptor<SearchReq> requestCaptor =
                ArgumentCaptor.forClass(SearchReq.class);
        verify(milvusClient).search(requestCaptor.capture());
        return requestCaptor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static List<String> deleteVectorIdsFrom(DeleteReq request) {
        return (List<String>) request.getFilterTemplateValues()
                .get("vectorIds");
    }

    @SuppressWarnings("unchecked")
    private static List<Float> queryVectorFrom(SearchReq request) {
        FloatVec vector = (FloatVec) request.getData().getFirst();
        return (List<Float>) vector.getData();
    }

    private void assertInvalidEntityField(
            String fieldName,
            Object fieldValue,
            String expectedMessage
    ) {
        Map<String, Object> entity = validEntity();
        entity.put(fieldName, fieldValue);
        when(milvusClient.search(any(SearchReq.class)))
                .thenReturn(searchResponse(List.of(
                        searchResult("vector-1", entity, 0.85F)
                )));

        assertThatThrownBy(() -> client.search(
                validQueryVector(),
                "embedding-v1",
                5
        ))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining(expectedMessage)
                .hasMessageContaining("结果索引：0");
    }

    private static List<Float> validQueryVector() {
        return List.of(0.1F, -0.2F, 0.3F);
    }

    private static SearchResp emptySearchResponse() {
        return searchResponse(List.of());
    }

    private static SearchResp searchResponse(
            List<SearchResp.SearchResult> results
    ) {
        return SearchResp.builder()
                .searchResults(List.of(results))
                .build();
    }

    private static SearchResp.SearchResult validSearchResult() {
        return validSearchResult(
                "vector-1",
                101L,
                2L,
                "embedding-v1",
                0.85F
        );
    }

    private static SearchResp.SearchResult validSearchResult(
            String vectorId,
            long documentId,
            long chunkIndex,
            String embeddingVersion,
            float score
    ) {
        return searchResult(
                vectorId,
                new HashMap<>(Map.of(
                        DOCUMENT_ID_FIELD, documentId,
                        CHUNK_INDEX_FIELD, chunkIndex,
                        EMBEDDING_VERSION_FIELD, embeddingVersion
                )),
                score
        );
    }

    private static SearchResp.SearchResult searchResult(
            Object vectorId,
            Map<String, Object> entity,
            Float score
    ) {
        return SearchResp.SearchResult.builder()
                .id(vectorId)
                .entity(entity)
                .score(score)
                .build();
    }

    private static Map<String, Object> validEntity() {
        return new HashMap<>(Map.of(
                DOCUMENT_ID_FIELD, 101L,
                CHUNK_INDEX_FIELD, 2L,
                EMBEDDING_VERSION_FIELD, "embedding-v1"
        ));
    }

    private static VectorSearchHit validExpectedHit() {
        return new VectorSearchHit(
                "vector-1",
                101L,
                2,
                "embedding-v1",
                0.85F
        );
    }

    private static Stream<Arguments> invalidQueryVectorValues() {
        return Stream.of(
                Arguments.of((Float) null),
                Arguments.of(Float.NaN),
                Arguments.of(Float.POSITIVE_INFINITY),
                Arguments.of(Float.NEGATIVE_INFINITY)
        );
    }

    private static Stream<Arguments> invalidSearchResultIds() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(101L),
                Arguments.of(""),
                Arguments.of(" \t ")
        );
    }

    private static Stream<Arguments> invalidResponseEmbeddingVersions() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(1L),
                Arguments.of(""),
                Arguments.of(" \t ")
        );
    }

    private static Stream<Arguments> invalidSearchScores() {
        return Stream.of(
                Arguments.of((Float) null),
                Arguments.of(Float.NaN),
                Arguments.of(Float.POSITIVE_INFINITY),
                Arguments.of(Float.NEGATIVE_INFINITY)
        );
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

    private static DeleteResp successfulDeleteResponse(long deleteCount) {
        return DeleteResp.builder()
                .deleteCnt(deleteCount)
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
        properties.setDimension(3);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setRequestTimeout(Duration.ofSeconds(2));
        return properties;
    }
}
