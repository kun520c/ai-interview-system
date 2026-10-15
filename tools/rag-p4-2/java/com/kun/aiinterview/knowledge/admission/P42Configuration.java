package com.kun.aiinterview.knowledge.admission;

import com.kun.aiinterview.interview.evaluation.llm.deepseek.*;
import com.kun.aiinterview.knowledge.embedding.EmbeddingClient;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.service.KnowledgeRetrievalService;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.time.Duration;
import java.util.Set;

/** Explicit import in the standalone experiment only. This directory is outside production sources. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(P42Properties.class)
public class P42Configuration {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "rag.experimental-evidence", name = "enabled", havingValue = "true", matchIfMissing = false)
    static class Enabled {
        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean(name = "p42HttpClient")
        HttpClient p42HttpClient() {
            return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        }
        @Bean BailianRerankClient p42Reranker(P42Properties properties, HttpClient p42HttpClient) {
            String workspace = properties.getRerankWorkspaceId();
            if (workspace == null || !workspace.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?") || properties.getRerankApiKey() == null || properties.getRerankApiKey().isBlank())
                throw new IllegalArgumentException("EXPLICIT_RERANK_WORKSPACE_AND_KEY_REQUIRED");
            URI endpoint = URI.create("https://" + workspace + ".cn-beijing.maas.aliyuncs.com" + BailianRerankClient.PATH);
            var factory = new JdkClientHttpRequestFactory(p42HttpClient); factory.setReadTimeout(Duration.ofSeconds(10));
            RestClient client = RestClient.builder().baseUrl(endpoint.toString()).requestFactory(factory)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getRerankApiKey()).build();
            return new BailianRerankClient(client, endpoint);
        }
        @Bean ExperimentalEvidenceStrategy.GateModel p42GateModel(P42Properties properties, DeepSeekProperties source, HttpClient p42HttpClient) throws Exception {
            if (source.getBaseUrl() == null || !"api.deepseek.com".equals(source.getBaseUrl().getHost()) || !"https".equals(source.getBaseUrl().getScheme()) ||
                    !Set.of("deepseek-v4-flash", "deepseek-flash").contains(source.getModel()) || source.getApiKey() == null || source.getApiKey().isBlank())
                throw new IllegalArgumentException("EXPLICIT_OFFICIAL_FLASH_CONFIGURATION_REQUIRED");
            String prompt = Files.readString(Path.of(properties.getGatePromptPath()));
            // Separate experimental properties; production client and token settings remain untouched.
            var limited = new DeepSeekProperties(); limited.setModel(source.getModel()); limited.setMaxTokens(3072);
            var factory = new JdkClientHttpRequestFactory(p42HttpClient); factory.setReadTimeout(Duration.ofSeconds(30));
            var rest = RestClient.builder().baseUrl(source.getBaseUrl().toString()).requestFactory(factory)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + source.getApiKey()).build();
            var jsonClient = new DeepSeekEvaluationClient(rest, limited, PrimarySemanticGate.JSON);
            return request -> {
                var response = jsonClient.completeJson(prompt, PrimarySemanticGate.JSON.writeValueAsString(request));
                if (!Set.of("deepseek-v4-flash", "deepseek-flash", "deepseek-v4.1-flash").contains(response.model())) throw new IllegalArgumentException("UNEXPECTED_MODEL");
                return response.rawJson();
            };
        }
        @Bean MilvusTop5Candidates p42Candidates(EmbeddingClient embedding, VectorStoreClient vector, KnowledgeChunkMapper mapper) {
            return new MilvusTop5Candidates(embedding, vector, mapper);
        }
        @Bean ExperimentalEvidenceStrategy p42Strategy(KnowledgeRetrievalService existing, MilvusTop5Candidates candidates,
                BailianRerankClient reranker, ExperimentalEvidenceStrategy.GateModel model) {
            return new ExperimentalEvidenceStrategy(true, existing::retrieve, candidates::retrieve, reranker, model, new PrimarySemanticGate());
        }
    }
}
