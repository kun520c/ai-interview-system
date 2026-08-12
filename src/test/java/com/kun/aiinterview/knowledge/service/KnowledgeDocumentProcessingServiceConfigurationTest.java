package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.knowledge.chunk.KnowledgeTextChunker;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.mapper.KnowledgeDocumentMapper;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KnowledgeDocumentProcessingServiceConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            KnowledgeDocumentProcessingService.class
                    );

    @Test
    void shouldNotCreateProcessingServiceWhenMilvusIsDisabled() {
        contextRunner
                .withPropertyValues("milvus.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(
                            KnowledgeDocumentProcessingService.class
                    );
                });
    }

    @Test
    void shouldCreateProcessingServiceWhenMilvusIsEnabledAndDependenciesExist() {
        contextRunner
                .withPropertyValues("milvus.enabled=true")
                .withBean(
                        KnowledgeDocumentMapper.class,
                        () -> mock(KnowledgeDocumentMapper.class)
                )
                .withBean(
                        KnowledgeTextChunker.class,
                        () -> mock(KnowledgeTextChunker.class)
                )
                .withBean(
                        EmbeddingClient.class,
                        () -> mock(EmbeddingClient.class)
                )
                .withBean(
                        VectorStoreClient.class,
                        () -> mock(VectorStoreClient.class)
                )
                .withBean(
                        KnowledgeDocumentProcessingTransactionService.class,
                        () -> mock(
                                KnowledgeDocumentProcessingTransactionService.class
                        )
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(
                            KnowledgeDocumentProcessingService.class
                    );
                });
    }
}
