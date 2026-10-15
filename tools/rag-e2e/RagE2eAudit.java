import com.kun.aiinterview.interview.evaluation.*;
import com.kun.aiinterview.interview.evaluation.prompt.*;
import com.kun.aiinterview.interview.evaluation.judge.JudgeProvider;
import com.kun.aiinterview.interview.evaluation.validation.*;
import com.kun.aiinterview.interview.evaluation.score.*;
import com.kun.aiinterview.interview.evaluation.decision.*;
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

/** E2E-only observation around production beans. Delegates business logic unchanged. */
@Configuration(proxyBeanMethods=false)
public class RagE2eAudit {
    static volatile EvaluationContext context;
    static volatile EvaluationRetrievalResult retrieval;
    static volatile EvaluationPrompt prompt;
    static volatile JudgeProvider.Result judgeResult;
    static volatile ValidatedEvaluationSuggestion validated;
    static volatile EvaluationScore score;
    static volatile EvaluationDecision decision;
    static volatile List<KnowledgeRetrievalRow> sqlRows=List.of();
    static volatile List<String> sqlVectorIds=List.of();
    static volatile boolean collectionChecked, searchUsesCollectionDefault;
    static volatile long targetCollectionId;
    static volatile int embeddingDimension;
    static final RagE2ePolicy.Budget embeddingCalls=new RagE2ePolicy.Budget(1), searches=new RagE2ePolicy.Budget(1);
    static final RagE2ePolicy.Budget judgeCalls=new RagE2ePolicy.Budget(1);
    static final List<String> milestones=Collections.synchronizedList(new ArrayList<>());
    static final List<Map<String,Object>> serviceFailures=Collections.synchronizedList(new ArrayList<>());
    static final Map<String,Integer> sdkCalls=Collections.synchronizedMap(new LinkedHashMap<>());
    static List<VectorSearchHit> rawHits=List.of();
    @Bean static BeanPostProcessor e2eObservers() {
        return new BeanPostProcessor() {
            @Override public Object postProcessBeforeInitialization(Object bean,String name) {
                if (bean instanceof MilvusClientV2 client) client.retryConfig(RetryConfig.builder().maxRetryTimes(1).build());
                return bean;
            }
            @Override public Object postProcessAfterInitialization(Object bean,String name) {
                boolean watched=bean instanceof MilvusClientV2 || bean instanceof EmbeddingClient
                        || bean instanceof VectorStoreClient || bean instanceof KnowledgeChunkMapper
                        || bean instanceof EvaluationPromptBuilder || bean instanceof JudgeProvider
                        || bean instanceof LlmEvaluationValidator || bean instanceof EvaluationScoreCalculator
                        || bean instanceof FollowUpPolicy
                        || bean instanceof com.kun.aiinterview.common.exception.GlobalExceptionHandler;
                if(!watched)return bean;
                ProxyFactory proxy=new ProxyFactory(bean);
                // Interfaces for MyBatis/project ports; class proxy for concrete production services.
                proxy.setProxyTargetClass(!(bean instanceof EmbeddingClient || bean instanceof VectorStoreClient
                        || bean instanceof KnowledgeChunkMapper || bean instanceof JudgeProvider));
                proxy.addAdvice((MethodInterceptor)invocation->{
                    String method=invocation.getMethod().getName();
                    Object[] args=invocation.getArguments();
                    if(bean instanceof com.kun.aiinterview.common.exception.GlobalExceptionHandler
                            && args.length==1 && args[0] instanceof Throwable error) {
                        // Capture the exception the real MVC handler received, not the Runner's status guard.
                        // Class/stack/vendor code only; arbitrary SQL text and exception messages stay private.
                        serviceFailures.add(RagE2eRunner.failureDetails(error));
                    }
                    if(bean instanceof MilvusClientV2) {
                        RagE2ePolicy.milvusMethod(method);
                        sdkCalls.merge(method,1,Integer::sum);
                        if(method.equals("search")) {
                            var request=(SearchReq)args[0];
                            RagE2ePolicy.require(collectionChecked && request.getConsistencyLevel()==null
                                    && RagE2ePolicy.COLLECTION.equals(request.getCollectionName())
                                    && request.getLimit()==5,"PRODUCTION_SEARCH_BOUNDARY");
                            searchUsesCollectionDefault=true;
                        }
                    }
                    if(bean instanceof EmbeddingClient && method.equals("embed"))embeddingCalls.take();
                    if(bean instanceof VectorStoreClient) {
                        RagE2ePolicy.require(Set.of("search","toString","hashCode","equals").contains(method),"VECTOR_WRITE_FORBIDDEN");
                        if(method.equals("search")) searches.take();
                    }
                    if(bean instanceof JudgeProvider && method.equals("evaluate"))judgeCalls.take();
                    if(bean instanceof KnowledgeChunkMapper && method.equals("selectRetrievableByVectorIds")) {
                        sqlVectorIds=List.copyOf((List<String>)args[0]); milestones.add("MYSQL_HYDRATION_STARTED");
                    }
                    Object result=invocation.proceed();
                    if(bean instanceof MilvusClientV2 && method.equals("describeCollection")) {
                        var desc=(DescribeCollectionResp)result;
                        RagE2ePolicy.require(desc.getCollectionID()==targetCollectionId
                                && "default".equals(desc.getDatabaseName())
                                && desc.getConsistencyLevel()==ConsistencyLevel.BOUNDED,"MILVUS_ID_OR_BOUNDED_MISMATCH");
                        collectionChecked=true;
                    }
                    if(bean instanceof EmbeddingClient && method.equals("embed")) {
                        var embedded=(EmbeddingBatchResult)result;
                        embeddingDimension=embedded.vectors().getFirst().values().size(); milestones.add("EMBEDDING_OK");
                    }
                    if(bean instanceof VectorStoreClient && method.equals("search")) {
                        rawHits=List.copyOf((List<VectorSearchHit>)result); milestones.add("MILVUS_SEARCH_OK");
                    }
                    if(bean instanceof KnowledgeChunkMapper && method.equals("selectRetrievableByVectorIds")) {
                        sqlRows=List.copyOf((List<KnowledgeRetrievalRow>)result); milestones.add("MYSQL_HYDRATION_OK");
                    }
                    if(bean instanceof EvaluationPromptBuilder && method.equals("build")) {
                        context=(EvaluationContext)args[0]; retrieval=(EvaluationRetrievalResult)args[1];
                        prompt=(EvaluationPrompt)result; milestones.add("PROMPT_BUILT");
                        RagE2ePolicy.require(!retrieval.evidence().isEmpty(),"E2E_REQUIRES_REAL_HYDRATED_EVIDENCE");
                    }
                    if(bean instanceof JudgeProvider && method.equals("evaluate")) {
                        judgeResult=(JudgeProvider.Result)result; milestones.add("JUDGE_JSON_PARSED");
                    }
                    if(bean instanceof LlmEvaluationValidator && method.equals("validate")) {
                        validated=(ValidatedEvaluationSuggestion)result; milestones.add("JAVA_VALIDATED");
                    }
                    if(bean instanceof EvaluationScoreCalculator && method.equals("calculate")) {
                        score=(EvaluationScore)result; milestones.add("JAVA_SCORED");
                    }
                    if(bean instanceof FollowUpPolicy && method.equals("decide")) {
                        decision=(EvaluationDecision)result; milestones.add("JAVA_DECIDED");
                    }
                    return result;
                });
                return proxy.getProxy();
            }
        };
    }
}
