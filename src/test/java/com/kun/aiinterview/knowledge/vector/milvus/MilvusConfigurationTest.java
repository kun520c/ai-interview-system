package com.kun.aiinterview.knowledge.vector.milvus;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

class MilvusConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            MilvusConfiguration.class,
                            MilvusCollectionInitializer.class,
                            MilvusVectorStoreClient.class
                    );

    @Test
    void shouldNotCreateMilvusBeansWhenDisabled() {
        contextRunner
                .withPropertyValues("milvus.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ConnectConfig.class);
                    assertThat(context).doesNotHaveBean(MilvusClientV2.class);
                    assertThat(context)
                            .doesNotHaveBean(MilvusCollectionInitializer.class);
                    assertThat(context)
                            .doesNotHaveBean(MilvusVectorStoreClient.class);
                });
    }

    @Test
    void shouldBindAndCreateEnabledBeansWithoutRealNetwork() {
        AtomicReference<ConnectConfig> constructorArgument =
                new AtomicReference<>();
        try (MockedConstruction<MilvusClientV2> construction =
                     mockConstruction(
                             MilvusClientV2.class,
                             (mock, context) -> constructorArgument.set(
                                     (ConnectConfig) context.arguments().getFirst()
                             )
                     )) {
            contextRunner
                    .withPropertyValues(validEnabledProperties(""))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(MilvusProperties.class);
                        assertThat(context).hasSingleBean(ConnectConfig.class);
                        assertThat(context).hasSingleBean(MilvusClientV2.class);
                        assertThat(context)
                                .hasSingleBean(MilvusCollectionInitializer.class);
                        assertThat(context)
                                .hasSingleBean(MilvusVectorStoreClient.class);

                        MilvusProperties properties = context.getBean(
                                MilvusProperties.class
                        );
                        assertThat(properties.isEnabled()).isTrue();

                        ConnectConfig connectConfig = context.getBean(
                                ConnectConfig.class
                        );
                        assertThat(connectConfig.getUri())
                                .isEqualTo("http://localhost:19530");
                        assertThat(connectConfig.getDbName())
                                .isEqualTo("test_database");
                        assertThat(connectConfig.getToken()).isNull();
                        assertThat(connectConfig.getConnectTimeoutMs())
                                .isEqualTo(1500L);
                        assertThat(connectConfig.getRpcDeadlineMs())
                                .isEqualTo(2500L);

                        assertThat(construction.constructed()).hasSize(1);
                        assertThat(constructorArgument.get())
                                .isSameAs(connectConfig);
                    });

            verify(construction.constructed().getFirst()).close();
        }
    }

    @Test
    void shouldConfigureNonBlankToken() {
        MilvusProperties properties = directProperties();
        properties.setToken("unit-test-token");

        ConnectConfig connectConfig = new MilvusConfiguration()
                .milvusConnectConfig(properties);

        assertThat(connectConfig.getToken()).isEqualTo("unit-test-token");
    }

    @Test
    void shouldIgnoreBlankToken() {
        MilvusProperties properties = directProperties();
        properties.setToken(" \t ");

        ConnectConfig connectConfig = new MilvusConfiguration()
                .milvusConnectConfig(properties);

        assertThat(connectConfig.getToken()).isNull();
    }

    private static String[] validEnabledProperties(String token) {
        return new String[]{
                "milvus.enabled=true",
                "milvus.uri=http://localhost:19530",
                "milvus.token=" + token,
                "milvus.database-name=test_database",
                "milvus.collection-name=test_collection",
                "milvus.dimension=3",
                "milvus.connect-timeout=1500ms",
                "milvus.request-timeout=2500ms"
        };
    }

    private static MilvusProperties directProperties() {
        MilvusProperties properties = new MilvusProperties();
        properties.setUri(URI.create("http://localhost:19530"));
        properties.setDatabaseName("test_database");
        properties.setCollectionName("test_collection");
        properties.setDimension(3);
        properties.setConnectTimeout(Duration.ofMillis(1500));
        properties.setRequestTimeout(Duration.ofMillis(2500));
        return properties;
    }
}
