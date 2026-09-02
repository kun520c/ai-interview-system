package com.kun.aiinterview.interview.evaluation.llm.deepseek;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@ConditionalOnProperty(
        prefix = "deepseek",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(DeepSeekProperties.class)
public class DeepSeekConfiguration {

    @Bean
    @Qualifier("deepseekRestClient")
    public RestClient deepSeekRestClient(
            RestClient.Builder builder,
            DeepSeekProperties properties
    ){

        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                properties.getConnectTimeout()
                        )
                        .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(
                        httpClient
                );

        requestFactory.setReadTimeout(
                properties.getReadTimeout()
        );

        return builder
                .baseUrl(
                        properties
                                .getBaseUrl()
                                .toString()
                )
                .requestFactory(requestFactory)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer "
                                + properties.getApiKey()
                )
                .build();
    }
}
