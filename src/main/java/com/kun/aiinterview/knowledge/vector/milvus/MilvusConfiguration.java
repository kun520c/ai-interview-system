package com.kun.aiinterview.knowledge.vector.milvus;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MilvusProperties.class)
@ConditionalOnProperty(
        prefix = "milvus",
        name = "enabled",
        havingValue = "true"
)
public class MilvusConfiguration {

    @Bean
    public ConnectConfig milvusConnectConfig(
            MilvusProperties properties
    ){
        var builder = ConnectConfig.builder()
                .uri(properties.getUri().toString())
                .dbName(properties.getDatabaseName())
                .connectTimeoutMs(
                        properties.getConnectTimeout().toMillis()
                )
                .rpcDeadlineMs(
                        properties.getRequestTimeout().toMillis()
                );

        String token = properties.getToken();
        if(token != null && !token.isBlank()){
            builder.token(token);
        }

        return builder.build();
    }

    @Bean
    public MilvusClientV2 milvusClient(
            ConnectConfig milvusConnectConfig
    ){
        return new MilvusClientV2(milvusConnectConfig);
    }
}
