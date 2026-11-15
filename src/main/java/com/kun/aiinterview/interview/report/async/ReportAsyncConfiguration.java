package com.kun.aiinterview.interview.report.async;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(ReportAsyncProperties.class)
public class ReportAsyncConfiguration {
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "report.async", name = "enabled", havingValue = "true")
    static class Scheduling {
        @Bean
        ThreadPoolTaskExecutor reportWakeupExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(1);
            executor.setThreadNamePrefix("report-wakeup-");
            // AbortPolicy (the default): never perform Broker I/O on the committing request thread.
            executor.setWaitForTasksToCompleteOnShutdown(false);
            executor.setAwaitTerminationSeconds(10);
            return executor;
        }

        @Bean
        ThreadPoolTaskScheduler reportScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            // Dispatch, expiration and MQ connection attempts must not starve one another.
            scheduler.setPoolSize(3);
            scheduler.setThreadNamePrefix("report-outbox-");
            scheduler.setWaitForTasksToCompleteOnShutdown(false);
            scheduler.setAwaitTerminationSeconds(10);
            return scheduler;
        }
    }
}
