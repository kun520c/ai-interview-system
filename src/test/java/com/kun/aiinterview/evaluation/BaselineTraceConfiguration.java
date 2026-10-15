package com.kun.aiinterview.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalAdapter;
import com.kun.aiinterview.interview.evaluation.judge.JudgeProvider;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Observe actual production calls, returning the original result without retries or edits. */
@TestConfiguration(proxyBeanMethods = false)
class BaselineTraceConfiguration {
    @Bean
    static BeanPostProcessor baselineTraceRecorder() {
        return new BeanPostProcessor() {
            final ObjectMapper writer = new ObjectMapper().findAndRegisterModules();
            final Map<String,List<Object>> traces = new LinkedHashMap<>();

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                String kind = bean instanceof JudgeProvider ? "judge"
                        : bean instanceof EvaluationRetrievalAdapter ? "retrieval"
                        : bean instanceof DeepSeekEvaluationClient ? "deepseek" : null;
                if (kind == null) return bean;
                var proxy = new ProxyFactory(bean);
                proxy.addAdvice((MethodInterceptor) invocation -> {
                    if (!List.of("evaluate","retrieve","completeJson").contains(invocation.getMethod().getName())) {
                        return invocation.proceed();
                    }
                    Map<String,Object> row = new LinkedHashMap<>();
                    row.put("method",invocation.getMethod().getName());
                    if(kind.equals("deepseek")) row.put("arguments",invocation.getArguments());
                    else row.put("context",invocation.getArguments()[0]);
                    if (kind.equals("judge")) row.put("prompt",invocation.getArguments()[1]);
                    long start = System.nanoTime();
                    try {
                        Object result = invocation.proceed();
                        row.put("result",result);
                        return result;
                    } catch (Throwable failure) {
                        row.put("errorClass",failure.getClass().getSimpleName());
                        throw failure;
                    } finally {
                        row.put("elapsedMs",(System.nanoTime()-start)/1_000_000);
                        synchronized(traces) {
                            traces.computeIfAbsent(kind,ignored->new ArrayList<>()).add(row);
                            Path file=Path.of("docs/evaluation/evidence/runtime-"+kind+"-"
                                    +System.getProperty("evaluation.phase","inventory")+".json");
                            Files.createDirectories(file.getParent());
                            writer.writerWithDefaultPrettyPrinter().writeValue(file.toFile(),traces.get(kind));
                        }
                    }
                });
                return proxy.getProxy();
            }
        };
    }
}
