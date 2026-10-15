import com.kun.aiinterview.interview.evaluation.*;
import com.kun.aiinterview.interview.evaluation.prompt.*;
import com.kun.aiinterview.interview.evaluation.judge.JudgeProvider;
import com.kun.aiinterview.knowledge.embedding.*;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.retrieval.*;
import com.kun.aiinterview.knowledge.vector.*;
import io.milvus.v2.client.*;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.SearchReq;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.*;
import java.util.*;

/** Transparent observations only; no replacement of production evaluation or persistence. */
@Configuration(proxyBeanMethods = false)
public class FinalE2eAudit {
    static final class Observation {
        EvaluationContext context;
        EvaluationRetrievalResult retrieval;
        EvaluationPrompt prompt;
        JudgeProvider.Result judge;
        List<KnowledgeRetrievalRow> hydrated = List.of();
        List<String> hydrationIds = List.of();
        List<VectorSearchHit> raw = List.of();
        int embeddingCalls, searches, judgeCalls, dimension;
    }
    static volatile Observation current = new Observation();
    static long collectionId;
    @Bean static BeanPostProcessor fullE2eObservers() {
        return new BeanPostProcessor() {
            @Override public Object postProcessBeforeInitialization(Object bean, String name) {
                if (bean instanceof MilvusClientV2 client) client.retryConfig(RetryConfig.builder().maxRetryTimes(1).build());
                return bean;
            }
            @Override public Object postProcessAfterInitialization(Object bean, String name) {
                if (!(bean instanceof MilvusClientV2 || bean instanceof EmbeddingClient || bean instanceof VectorStoreClient
                        || bean instanceof KnowledgeChunkMapper || bean instanceof EvaluationPromptBuilder || bean instanceof JudgeProvider
                        || bean instanceof com.kun.aiinterview.common.exception.GlobalExceptionHandler)) return bean;
                ProxyFactory proxy = new ProxyFactory(bean);
                proxy.setProxyTargetClass(!(bean instanceof EmbeddingClient || bean instanceof VectorStoreClient
                        || bean instanceof KnowledgeChunkMapper || bean instanceof JudgeProvider));
                proxy.addAdvice((MethodInterceptor) invocation -> {
                    String method = invocation.getMethod().getName(); Object[] args = invocation.getArguments();
                    Observation observation = current;
                    if (bean instanceof MilvusClientV2) {
                        RagE2ePolicy.milvusMethod(method); // Rejects create/load/insert/delete/drop and other writes.
                        if (method.equals("search")) {
                            var search = (SearchReq) args[0];
                            FinalE2eRunner.check(RagE2ePolicy.COLLECTION.equals(search.getCollectionName())
                                    && search.getConsistencyLevel() == null && search.getLimit() == 5, "SEARCH_CONTRACT");
                        }
                    }
                    if (bean instanceof VectorStoreClient) {
                        FinalE2eRunner.check(Set.of("search", "toString", "equals", "hashCode").contains(method), "VECTOR_WRITES_FORBIDDEN");
                        if (method.equals("search")) observation.searches++;
                    }
                    if (bean instanceof EmbeddingClient && method.equals("embed")) observation.embeddingCalls++;
                    if (bean instanceof JudgeProvider && method.equals("evaluate")) observation.judgeCalls++;
                    if (bean instanceof KnowledgeChunkMapper && method.equals("selectRetrievableByVectorIds"))
                        observation.hydrationIds = List.copyOf((List<String>) args[0]);
                    if (bean instanceof com.kun.aiinterview.common.exception.GlobalExceptionHandler
                            && args.length == 1 && args[0] instanceof Throwable error)
                        FinalE2eRunner.failures.add(FinalE2eResume.failure(error));
                    Object result = invocation.proceed();
                    if (bean instanceof MilvusClientV2 && method.equals("describeCollection")) {
                        var desc = (DescribeCollectionResp) result;
                        FinalE2eRunner.check(desc.getCollectionID() == collectionId
                                && desc.getConsistencyLevel() == ConsistencyLevel.BOUNDED, "COLLECTION_ID_CHANGED");
                    }
                    if (bean instanceof EmbeddingClient && method.equals("embed"))
                        observation.dimension = ((EmbeddingBatchResult) result).vectors().getFirst().values().size();
                    if (bean instanceof VectorStoreClient && method.equals("search")) observation.raw = List.copyOf((List<VectorSearchHit>) result);
                    if (bean instanceof KnowledgeChunkMapper && method.equals("selectRetrievableByVectorIds"))
                        observation.hydrated = List.copyOf((List<KnowledgeRetrievalRow>) result);
                    if (bean instanceof EvaluationPromptBuilder && method.equals("build")) {
                        observation.context = (EvaluationContext) args[0]; observation.retrieval = (EvaluationRetrievalResult) args[1];
                        observation.prompt = (EvaluationPrompt) result;
                    }
                    if (bean instanceof JudgeProvider && method.equals("evaluate")) observation.judge = (JudgeProvider.Result) result;
                    return result;
                });
                return proxy.getProxy();
            }
        };
    }
}
