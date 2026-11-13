package com.kun.aiinterview.knowledge.vector.milvus;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.RetryConfig;
import io.milvus.shaded.io.grpc.Context;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    ) {
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
        if (token != null && !token.isBlank()) {
            builder.token(token);
        }

        return builder.build();
    }

    @Bean
    public MilvusClientV2 milvusClient(
            ConnectConfig milvusConnectConfig
    ) {
        // 2.6.20 bounds Connect but not the constructor's subsequent ListDatabases RPC.
        // A gRPC context propagates cancellation to both RPCs; this is not a Future wait timeout.
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var deadline = Context.current().withDeadlineAfter(
                milvusConnectConfig.getConnectTimeoutMs(), TimeUnit.MILLISECONDS, scheduler
        );
        Context previous = deadline.attach();
        try {
            MilvusClientV2 client = new MilvusClientV2(milvusConnectConfig);
            // In 2.6.20 this means one total callable attempt, bypassing the retry loop
            // that swallows timeout/exhaustion/interruption. Business FAILED retry stays explicit.
            client.retryConfig(RetryConfig.builder().maxRetryTimes(1).build());
            return client;
        } finally {
            deadline.detach(previous);
            deadline.cancel(null);
            scheduler.shutdownNow();
        }
    }
}
