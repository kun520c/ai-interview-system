package com.kun.aiinterview.interview.report.async;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class ReportAsyncConfigurationTest {
    final ApplicationContextRunner context=new ApplicationContextRunner().withUserConfiguration(
            ReportAsyncConfiguration.class,ReportMqRuntime.class,ReportOutboxDispatcher.class,ReportMessageProducer.class);
    @Test void disabledByDefaultCreatesNoMqClientOrScheduler() {
        context.run(c -> {
            assertThat(c).hasNotFailed().doesNotHaveBean(ReportMqRuntime.class).doesNotHaveBean(ReportMessageProducer.class)
                    .doesNotHaveBean(ReportOutboxDispatcher.class).doesNotHaveBean("reportScheduler");
            assertThat(c.getBean(ReportAsyncProperties.class).isEnabled()).isFalse();
        });
    }
    @Test void sendMustFitLeaseWithSettlementMargin() {
        context.withPropertyValues("report.async.dispatch-lease-seconds=5").run(c -> assertThat(c).hasFailed());
    }
    @Test void consumerTimeoutMustExceedExecutionDeadline() {
        context.withPropertyValues("report.async.consumer-timeout-minutes=10").run(c -> assertThat(c).hasFailed());
    }
    @Test void scanAndThreadCountsAreBounded() {
        context.withPropertyValues("report.async.scan-batch-size=10000").run(c -> assertThat(c).hasFailed());
        context.withPropertyValues("report.async.consumer-threads=1000").run(c -> assertThat(c).hasFailed());
    }
}
