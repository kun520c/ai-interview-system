package com.kun.aiinterview.interview.report.async;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Explicit lifecycle keeps DB recovery alive when MQ startup fails. Disabled means no MQ client. */
@Component
@ConditionalOnProperty(prefix = "report.async", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ReportMqRuntime {
    private final ReportAsyncProperties properties;
    private final ReportMessageConsumer handler;
    private final ObjectMapper objectMapper;
    private volatile DefaultMQProducer producer;
    private DefaultMQPushConsumer consumer;
    private boolean stopped;

    @Scheduled(fixedDelay = 10000, scheduler = "reportScheduler")
    public synchronized void connect() {
        if (stopped) return;
        if (producer == null) {
            DefaultMQProducer candidate = new DefaultMQProducer(properties.getConsumerGroup() + "-producer");
            candidate.setNamesrvAddr(properties.getNameserver());
            candidate.setInstanceName("report-producer-" + java.util.UUID.randomUUID());
            candidate.setVipChannelEnabled(false);
            candidate.setRetryTimesWhenSendFailed(0);
            candidate.setRetryTimesWhenSendAsyncFailed(0);
            candidate.setRetryAnotherBrokerWhenNotStoreOK(false);
            candidate.setSendMsgTimeout(properties.getSendTimeoutMs());
            try {
                candidate.start();
                producer = candidate;
            } catch (Exception failure) {
                candidate.shutdown();
                log.error("REPORT_MQ_PRODUCER_UNAVAILABLE type={}", failure.getClass().getSimpleName());
            }
        }
        if (consumer != null) return;
        DefaultMQPushConsumer candidate = new DefaultMQPushConsumer(properties.getConsumerGroup());
        candidate.setNamesrvAddr(properties.getNameserver());
        candidate.setInstanceName("report-consumer-" + java.util.UUID.randomUUID());
        candidate.setVipChannelEnabled(false);
        candidate.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        candidate.setConsumeThreadMin(properties.getConsumerThreads());
        candidate.setConsumeThreadMax(properties.getConsumerThreads());
        candidate.setConsumeMessageBatchMaxSize(1);
        candidate.setPullBatchSize(1);
        candidate.setPullThresholdForQueue(8);
        candidate.setMaxReconsumeTimes(properties.getMaxReconsumeTimes());
        candidate.setConsumeTimeout(properties.getConsumerTimeoutMinutes());
        candidate.setAwaitTerminationMillisWhenShutdown(10000);
        candidate.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            if (messages.size() != 1) return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            var message = messages.getFirst();
            try {
                if (message.getBody() == null || message.getBody().length > 1024) {
                    throw new IllegalArgumentException("Invalid message size");
                }
                ReportTaskMessage body = objectMapper.readerFor(ReportTaskMessage.class)
                        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(message.getBody());
                if (body == null) throw new IllegalArgumentException("Missing message");
                handler.onMessage(body);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } catch (Exception failure) {
                // Never log untrusted body/keys or provider/SQL exception messages.
                log.error("REPORT_MQ_CONSUME_RETRY brokerMessageId={} reconsume={} dlqRisk={} type={}",
                        message.getMsgId(), message.getReconsumeTimes(),
                        message.getReconsumeTimes() >= properties.getMaxReconsumeTimes(), failure.getClass().getSimpleName());
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        });
        try {
            candidate.subscribe(properties.getTopic(), "*");
            candidate.start();
            consumer = candidate;
        } catch (Exception failure) {
            candidate.shutdown();
            log.error("REPORT_MQ_CONSUMER_UNAVAILABLE type={}", failure.getClass().getSimpleName());
        }
    }

    public SendResult send(ReportTaskMessage body) throws Exception {
        DefaultMQProducer current = producer;
        if (current == null) throw new IllegalStateException("Report producer unavailable");
        body.validate();
        Message message = new Message(properties.getTopic(), objectMapper.writeValueAsBytes(body));
        message.setKeys(Long.toString(body.taskId()));
        return current.send(message, properties.getSendTimeoutMs());
    }

    @PreDestroy
    public synchronized void close() {
        stopped = true;
        if (consumer != null) consumer.shutdown();
        if (producer != null) producer.shutdown();
        consumer = null;
        producer = null;
    }
}
