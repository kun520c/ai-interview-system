package com.kun.aiinterview.knowledge.embedding;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.knowledge.embedding.springai.SpringAiEmbeddingClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.List;

@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingConfiguration {

    static final String EMBEDDINGS_PATH = "/embeddings";
    static final int NO_RETRY_ATTEMPTS = 1;
    static final String ENCODING_FORMAT = "float";

    @Bean
    public OpenAiApi embeddingOpenAiApi(EmbeddingProperties properties) {
        return createOpenAiApi(
                properties,
                createEmbeddingRestClientBuilder(properties)
        );
    }

    @Bean
    public EmbeddingModel embeddingModel(
            OpenAiApi embeddingOpenAiApi,
            EmbeddingProperties properties
    ) {
        return createEmbeddingModel(embeddingOpenAiApi, properties);
    }

    @Bean
    public EmbeddingClient springAiEmbeddingClient(
            EmbeddingModel embeddingModel,
            EmbeddingProperties properties
    ) {
        return new SpringAiEmbeddingClient(embeddingModel, properties);
    }

    public static RestClient.Builder createEmbeddingRestClientBuilder(
            EmbeddingProperties properties
    ) {
        RestClient.Builder restClientBuilder = RestClient.builder();
        applyEmbeddingTimeouts(restClientBuilder, properties);
        applyEmbeddingJackson(restClientBuilder);
        return restClientBuilder;
    }

    static RestClient.Builder applyEmbeddingTimeouts(
            RestClient.Builder restClientBuilder,
            EmbeddingProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return restClientBuilder.requestFactory(requestFactory);
    }

    static RestClient.Builder applyEmbeddingJackson(
            RestClient.Builder restClientBuilder
    ) {
        return restClientBuilder.messageConverters(
                EmbeddingConfiguration::replaceJacksonConverter
        );
    }

    public static OpenAiApi createOpenAiApi(
            EmbeddingProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        return OpenAiApi.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .apiKey(properties.getApiKey())
                .embeddingsPath(EMBEDDINGS_PATH)
                .restClientBuilder(restClientBuilder)
                .build();
    }

    public static EmbeddingModel createEmbeddingModel(
            OpenAiApi embeddingOpenAiApi,
            EmbeddingProperties properties
    ) {
        return new OpenAiEmbeddingModel(
                embeddingOpenAiApi,
                MetadataMode.EMBED,
                createEmbeddingOptions(properties),
                createNoRetryTemplate()
        );
    }

    static OpenAiEmbeddingOptions createEmbeddingOptions(
            EmbeddingProperties properties
    ) {
        return OpenAiEmbeddingOptions.builder()
                .model(properties.getModel())
                .dimensions(properties.getDimension())
                .encodingFormat(ENCODING_FORMAT)
                .build();
    }

    static RetryTemplate createNoRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(NO_RETRY_ATTEMPTS)
                .noBackoff()
                .build();
    }

    public static ObjectMapper createEmbeddingObjectMapper() {
        return enableFailOnNullForPrimitives(
                Jackson2ObjectMapperBuilder.json().build()
        );
    }

    private static void replaceJacksonConverter(
            List<HttpMessageConverter<?>> converters
    ) {
        boolean replaced = false;
        for (int index = 0; index < converters.size(); index++) {
            HttpMessageConverter<?> converter = converters.get(index);
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                converters.set(
                        index,
                        new MappingJackson2HttpMessageConverter(
                                enableFailOnNullForPrimitives(
                                        jacksonConverter.getObjectMapper()
                                )
                        )
                );
                replaced = true;
            }
        }
        if (!replaced) {
            converters.add(new MappingJackson2HttpMessageConverter(
                    createEmbeddingObjectMapper()
            ));
        }
    }

    private static ObjectMapper enableFailOnNullForPrimitives(
            ObjectMapper source
    ) {
        return source.copy().enable(
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
        );
    }
}
