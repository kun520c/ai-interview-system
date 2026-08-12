package com.kun.aiinterview.knowledge.vector.milvus;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.CHUNK_INDEX_FIELD;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.DOCUMENT_ID_FIELD;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.EMBEDDING_VERSION_FIELD;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.EMBEDDING_VERSION_MAX_LENGTH;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.VECTOR_FIELD;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.VECTOR_ID_FIELD;
import static com.kun.aiinterview.knowledge.vector.milvus.MilvusSchemaConstants.VECTOR_ID_MAX_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
        classes = RealMilvusSchemaValidationSmokeTest
                .SchemaTestConfiguration.class,
        initializers = ConfigDataApplicationContextInitializer.class
)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "milvus.enabled=true",
        "spring.main.web-application-type=none"
})
@Tag("real-external")
@EnabledIfEnvironmentVariable(
        named = "RUN_REAL_MILVUS_SCHEMA_TEST",
        matches = "(?i)true"
)
class RealMilvusSchemaValidationSmokeTest {

    private static final String OPT_IN_ENVIRONMENT_VARIABLE =
            "RUN_REAL_MILVUS_SCHEMA_TEST";
    private static final int WRONG_TEST_DIMENSION = 3;
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(300);

    @Autowired
    private MilvusClientV2 milvusClient;

    @Autowired
    private MilvusProperties properties;

    @Autowired
    private MilvusCollectionInitializer initializer;

    @BeforeAll
    static void requireExplicitRealSchemaOptIn() {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(
                        System.getenv(OPT_IN_ENVIRONMENT_VARIABLE)
                ),
                "未显式启用真实 Milvus Schema 一致性测试"
        );
    }

    @Test
    void shouldValidateCurrentRealCollectionSchemaAndIndex() {
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getDimension()).isPositive();

        initializer.run(null);

        DescribeIndexResp indexResponse = milvusClient.describeIndex(
                DescribeIndexReq.builder()
                        .collectionName(properties.getCollectionName())
                        .fieldName(VECTOR_FIELD)
                        .build()
        );
        DescribeIndexResp.IndexDesc vectorIndex = indexResponse
                .getIndexDescByFieldName(VECTOR_FIELD);
        assertThat(vectorIndex).isNotNull();
        assertThat(vectorIndex.getFieldName()).isEqualTo(VECTOR_FIELD);
        assertThat(vectorIndex.getMetricType())
                .isEqualTo(IndexParam.MetricType.COSINE);
        assertThat(vectorIndex.getIndexType())
                .isEqualTo(IndexParam.IndexType.AUTOINDEX);

        System.out.printf(
                "Real Milvus schema validation summary: collection=%s, "
                        + "fieldName=%s, metricType=%s, indexType=%s%n",
                properties.getCollectionName(),
                vectorIndex.getFieldName(),
                vectorIndex.getMetricType(),
                vectorIndex.getIndexType()
        );
    }

    @Test
    void shouldFailFastForRealTemporaryCollectionWithWrongDimension() {
        String temporaryCollectionName = "c1_schema_mismatch_"
                + UUID.randomUUID().toString().replace("-", "");
        Throwable primaryFailure = null;

        try {
            milvusClient.createCollection(
                    wrongDimensionCollectionRequest(temporaryCollectionName)
            );

            MilvusProperties expectedProperties = new MilvusProperties();
            expectedProperties.setCollectionName(temporaryCollectionName);
            expectedProperties.setDimension(properties.getDimension());
            MilvusCollectionInitializer temporaryInitializer =
                    new MilvusCollectionInitializer(
                            milvusClient,
                            expectedProperties
                    );

            Throwable mismatch = catchThrowable(
                    () -> temporaryInitializer.run(null)
            );
            assertThat(mismatch)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(temporaryCollectionName)
                    .hasMessageContaining("field 'vector' dimension")
                    .hasMessageContaining(
                            "expected=" + properties.getDimension()
                    )
                    .hasMessageContaining(
                            "actual=" + WRONG_TEST_DIMENSION
                    );
        } catch (Throwable failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                dropTemporaryCollection(temporaryCollectionName);
            } catch (Throwable cleanupFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private CreateCollectionReq wrongDimensionCollectionRequest(
            String collectionName
    ) {
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
                        .maxLength(EMBEDDING_VERSION_MAX_LENGTH)
                        .build()
        );
        schema.addField(
                AddFieldReq.builder()
                        .fieldName(VECTOR_FIELD)
                        .dataType(DataType.FloatVector)
                        .dimension(WRONG_TEST_DIMENSION)
                        .build()
        );

        IndexParam vectorIndex = IndexParam.builder()
                .fieldName(VECTOR_FIELD)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.COSINE)
                .build();
        return CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(List.of(vectorIndex))
                .build();
    }

    private void dropTemporaryCollection(String collectionName) {
        HasCollectionReq hasCollectionRequest = HasCollectionReq.builder()
                .collectionName(collectionName)
                .build();
        if (Boolean.TRUE.equals(
                milvusClient.hasCollection(hasCollectionRequest)
        )) {
            milvusClient.dropCollection(
                    DropCollectionReq.builder()
                            .collectionName(collectionName)
                            .build()
            );
        }
        await().atMost(AWAIT_TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> assertThat(
                        milvusClient.hasCollection(hasCollectionRequest)
                ).isFalse());
        System.out.println(
                "Real Milvus schema mismatch temporary Collection cleanup "
                        + "completed"
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({
            MilvusConfiguration.class,
            MilvusCollectionInitializer.class
    })
    static class SchemaTestConfiguration {
    }
}
