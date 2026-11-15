package com.kun.aiinterview.interview.report.async;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.SendStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "report.async", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ReportMessageProducer {
    private final ReportMqRuntime runtime;

    public void send(ReportTaskMessage message) throws Exception {
        var result = runtime.send(message);
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("Broker result uncertain");
        }
    }
}
