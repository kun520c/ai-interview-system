package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.report.async.ReportMessageConsumer;
import com.kun.aiinterview.interview.report.async.ReportMessageProducer;
import com.kun.aiinterview.interview.report.async.ReportMqRuntime;
import com.kun.aiinterview.interview.report.async.ReportOutboxDispatcher;
import com.kun.aiinterview.interview.report.async.ReportAsyncProperties;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Parent test runner kills only this Surefire process at an explicitly signalled window. */
@SpringBootTest
@ActiveProfiles({"local", "test", "report-mq-test"})
@EnabledIfEnvironmentVariable(named = "RUN_REAL_REPORT_MQ_TEST", matches = "true")
class RealReportCrashWindowProbeTest extends ReportDatabaseFixture {
    private ReportMqRuntime runtime;
    private ReportMqRuntime secondRuntime;
    private final CountDownLatch neverReleased = new CountDownLatch(1);

    @Override
    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue("true".equals(System.getenv("RUN_REAL_REPORT_MQ_TEST")));
        Assumptions.assumeTrue("true".equals(System.getenv("M1C_CRASH_PROBE")));
        super.setUp();
    }

    @AfterEach
    void closeRuntime() {
        if (runtime != null) runtime.close();
        if (secondRuntime != null) secondRuntime.close();
    }

    @Test
    void committedBeforeAnySend() throws Exception {
        var message = accept();
        assertThat(tasks.getById(message.taskId()).getDeliveryState().name()).isEqualTo("PENDING");
        signal("COMMITTED_BEFORE_SEND", message.taskId(), message.sessionId());
        assertThat(neverReleased.await(90, TimeUnit.SECONDS)).as("parent must terminate this process").isTrue();
    }

    @Test
    void brokerAcceptedBeforeSentRecord() throws Exception {
        var message = accept();
        var token = UUID.randomUUID().toString();
        assertThat(tasks.claimDispatch(message.taskId(), token, 6, 20)).isEqualTo(1);
        var producer = new DefaultMQProducer("ai-interview-m1c-crash-producer-" + UUID.randomUUID());
        producer.setNamesrvAddr("127.0.0.1:19876");
        producer.setVipChannelEnabled(false);
        producer.setRetryTimesWhenSendFailed(0);
        try {
            producer.start();
            Message raw = new Message("ai-interview-m1b-report-test", objectMapper.writeValueAsBytes(message));
            raw.setKeys(message.taskId().toString());
            assertThat(producer.send(raw, 5000).getSendStatus()).isEqualTo(SendStatus.SEND_OK);
            signal("SEND_OK_BEFORE_MARK", message.taskId(), message.sessionId());
            assertThat(neverReleased.await(90, TimeUnit.SECONDS)).as("parent must terminate this process").isTrue();
        } finally {
            producer.shutdown();
        }
    }

    @Test
    void businessCommittedBeforeConsumerAck() throws Exception {
        var message = accept();
        var handler = spy(new ReportMessageConsumer(transactions, service));
        doAnswer(invocation -> {
            invocation.callRealMethod();
            assertThat(tasks.getById(message.taskId()).getTaskState().name()).isEqualTo("SUCCEEDED");
            assertThat(generatorCalls.get()).isEqualTo(1);
            signal("COMMITTED_BEFORE_ACK", message.taskId(), message.sessionId());
            neverReleased.await(90, TimeUnit.SECONDS);
            return null;
        }).when(handler).onMessage(message);
        connectAndSend(handler, message);
        assertThat(neverReleased.await(90, TimeUnit.SECONDS)).as("parent must terminate this process").isTrue();
    }

    @Test
    void modelReturnedBeforeReportCommit() throws Exception {
        when(asyncProperties.getExecutionDeadlineSeconds()).thenReturn(5);
        var message = accept();
        // The real consumer claims with the real transaction bean. Block only the
        // subsequent completion call, before it enters the report transaction.
        var blockedTransactions = mock(InterviewReportTransactionService.class);
        doAnswer(invocation -> {
            assertThat(generatorCalls.get()).isEqualTo(1);
            assertThat(reports.getBySessionId(sessionId)).isNull();
            signal("MODEL_RETURNED_BEFORE_COMMIT", message.taskId(), message.sessionId());
            neverReleased.await(90, TimeUnit.SECONDS);
            return null;
        }).when(blockedTransactions).completeReportGeneration(any(), any());
        @SuppressWarnings("unchecked") ObjectProvider<InterviewReportGenerator> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(configuredGenerator);
        var blockedService = new InterviewReportService(sessions, reports, blockedTransactions, provider, objectMapper, finalReader);
        connectAndSend(new ReportMessageConsumer(transactions, blockedService), message);
        assertThat(neverReleased.await(90, TimeUnit.SECONDS)).as("parent must terminate this process").isTrue();
    }

    @Test
    void observeRedeliveryOfCommittedTask() throws Exception {
        long taskId = Long.parseLong(System.getenv("M1C_CRASH_TASK_ID"));
        var prior = tasks.getById(taskId);
        assertThat(prior).isNotNull();
        var message = ReportOutboxDispatcher.message(prior);
        var observed = new CountDownLatch(1);
        var handler = spy(new ReportMessageConsumer(transactions, service));
        doAnswer(invocation -> {
            invocation.callRealMethod();
            signal("REDELIVERED_AFTER_COMMIT", taskId, message.sessionId());
            observed.countDown();
            return null;
        }).when(handler).onMessage(message);
        runtime = new ReportMqRuntime(properties(), handler, objectMapper);
        runtime.connect();
        assertThat(observed.await(50, TimeUnit.SECONDS)).as("broker redelivery").isTrue();
        assertThat(generatorCalls.get()).isZero();
        assertThat(tasks.getById(taskId).getTaskState().name()).isEqualTo("SUCCEEDED");
    }

    @Test
    void pendingTaskWhileBrokerUnavailable() {
        long taskId = Long.parseLong(System.getenv("M1C_CRASH_TASK_ID"));
        var task = tasks.getById(taskId);
        var properties = properties();
        runtime = new ReportMqRuntime(properties, new ReportMessageConsumer(transactions, service), objectMapper);
        runtime.connect();
        var dispatcher = new ReportOutboxDispatcher(tasks, transactions, new ReportMessageProducer(runtime), properties);
        dispatcher.dispatchDue();
        var after = tasks.getById(taskId);
        assertThat(after.getTaskState().name()).isEqualTo("QUEUED");
        assertThat(after.getDeliveryState().name()).isEqualTo("PENDING");
        assertThat(after.getSendAttempts()).isGreaterThan(task.getSendAttempts());
        assertThat(generatorCalls.get()).isZero();
        signal("BROKER_DOWN_PENDING", taskId, task.getSessionId());
    }

    @Test
    void resumePendingAfterBrokerRecovery() {
        long taskId = Long.parseLong(System.getenv("M1C_CRASH_TASK_ID"));
        var task = tasks.getById(taskId);
        var properties = properties();
        runtime = new ReportMqRuntime(properties, new ReportMessageConsumer(transactions, service), objectMapper);
        runtime.connect();
        var dispatcher = new ReportOutboxDispatcher(tasks, transactions, new ReportMessageProducer(runtime), properties);
        await().atMost(java.time.Duration.ofSeconds(75)).pollInterval(java.time.Duration.ofSeconds(2)).untilAsserted(() -> {
            dispatcher.dispatchDue();
            assertThat(tasks.getById(taskId).getTaskState().name()).isEqualTo("SUCCEEDED");
        });
        assertThat(generatorCalls.get()).isEqualTo(1);
        assertThat(reports.getBySessionId(task.getSessionId())).isNotNull();
        signal("RECOVERED_AFTER_RESTART", taskId, task.getSessionId());
    }

    @Test
    void resendExpiredLeaseWithoutConsumer() throws Exception {
        long taskId = Long.parseLong(System.getenv("M1C_CRASH_TASK_ID"));
        var task = tasks.getById(taskId);
        var producer = new DefaultMQProducer("ai-interview-m1c-resend-" + UUID.randomUUID());
        producer.setNamesrvAddr("127.0.0.1:19876");
        producer.setVipChannelEnabled(false);
        producer.setRetryTimesWhenSendFailed(0);
        try {
            producer.start();
            var adapter = mock(ReportMessageProducer.class);
            doAnswer(invocation -> {
                var message = (com.kun.aiinterview.interview.report.async.ReportTaskMessage) invocation.getArgument(0);
                var raw = new Message("ai-interview-m1b-report-test", objectMapper.writeValueAsBytes(message));
                raw.setKeys(message.taskId().toString());
                assertThat(producer.send(raw, 5000).getSendStatus()).isEqualTo(SendStatus.SEND_OK);
                return null;
            }).when(adapter).send(any());
            new ReportOutboxDispatcher(tasks, transactions, adapter, properties()).dispatchDue();
            var after = tasks.getById(taskId);
            assertThat(after.getTaskState().name()).isEqualTo("QUEUED");
            assertThat(after.getDeliveryState().name()).isEqualTo("SENT");
            assertThat(after.getSendAttempts()).isEqualTo(2);
            signal("LEASE_RESENT", taskId, task.getSessionId());
        } finally {
            producer.shutdown();
        }
    }

    @Test
    void observeTwoCopiesAfterResend() throws Exception {
        long taskId = Long.parseLong(System.getenv("M1C_CRASH_TASK_ID"));
        var task = tasks.getById(taskId);
        var message = ReportOutboxDispatcher.message(task);
        var observed = new CountDownLatch(2);
        var handler = spy(new ReportMessageConsumer(transactions, service));
        doAnswer(invocation -> {
            invocation.callRealMethod();
            observed.countDown();
            return null;
        }).when(handler).onMessage(message);
        runtime = new ReportMqRuntime(properties(), handler, objectMapper);
        runtime.connect();
        assertThat(observed.await(50, TimeUnit.SECONDS)).as("both broker deliveries").isTrue();
        assertThat(generatorCalls.get()).isEqualTo(1);
        assertThat(tasks.getById(taskId).getTaskState().name()).isEqualTo("SUCCEEDED");
        assertThat(reports.getBySessionId(task.getSessionId())).isNotNull();
        signal("TWO_COPIES_ONE_REPORT", taskId, task.getSessionId());
    }

    @Test
    void twoRealConsumerInstancesCompeteForSameTask() throws Exception {
        var message = accept();
        var entered = new CountDownLatch(2);
        var completed = new CountDownLatch(2);
        var handler = spy(new ReportMessageConsumer(transactions, service));
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(entered.await(20, TimeUnit.SECONDS)).isTrue();
            try {
                invocation.callRealMethod();
            } finally {
                completed.countDown();
            }
            return null;
        }).when(handler).onMessage(message);
        var first = properties();
        var second = properties();
        String group = "ai-interview-m1c-competition-" + UUID.randomUUID();
        String topic = System.getenv("M1C_COMPETITION_TOPIC");
        assertThat(topic).matches("ai-interview-m1c-competition-[a-f0-9]{8}");
        first.setTopic(topic);
        second.setTopic(topic);
        first.setConsumerGroup(group + "-a");
        second.setConsumerGroup(group + "-b");
        runtime = new ReportMqRuntime(first, handler, objectMapper);
        secondRuntime = new ReportMqRuntime(second, handler, objectMapper);
        runtime.connect();
        secondRuntime.connect();
        new ReportMessageProducer(runtime).send(message);
        assertThat(completed.await(45, TimeUnit.SECONDS)).as("both real consumer instances").isTrue();
        assertThat(generatorCalls.get()).isEqualTo(1);
        assertThat(tasks.getById(message.taskId()).getTaskState().name()).isEqualTo("SUCCEEDED");
        assertThat(reports.getBySessionId(sessionId)).isNotNull();
        assertThat(((Number) weakness().get("discovered_count")).intValue()).isEqualTo(1);
        signal("REAL_CONSUMERS_COMPETED", message.taskId(), message.sessionId());
    }

    @Test
    void expiredExecutionNeedsExplicitRetry() throws Exception {
        long taskId = Long.parseLong(System.getenv("M1C_CRASH_TASK_ID"));
        var old = tasks.getById(taskId);
        var oldMessage = ReportOutboxDispatcher.message(old);
        await().atMost(java.time.Duration.ofSeconds(12)).untilAsserted(() -> {
            transactions.expireTask(oldMessage);
            assertThat(tasks.getById(taskId).getTaskState().name()).isEqualTo("FAILED");
        });
        assertThat(reports.getBySessionId(old.getSessionId())).isNull();
        Long oldUser = jdbc.queryForObject("SELECT user_id FROM interview_session WHERE id=?", Long.class, old.getSessionId());
        assertThat(service.generateReportForApi(oldUser, old.getSessionId()).reportStatus().name()).isEqualTo("GENERATING");
        var active = tasks.listActiveBySessionId(old.getSessionId());
        assertThat(active).hasSize(1);
        var retry = ReportOutboxDispatcher.message(active.getFirst());
        connectAndSend(new ReportMessageConsumer(transactions, service), retry);
        await().atMost(java.time.Duration.ofSeconds(35)).untilAsserted(() ->
                assertThat(tasks.getById(retry.taskId()).getTaskState().name()).isEqualTo("SUCCEEDED"));
        assertThat(generatorCalls.get()).isEqualTo(1);
        assertThat(reports.getBySessionId(old.getSessionId())).isNotNull();
        signal("EXPLICIT_RETRY_SUCCEEDED", taskId, old.getSessionId());
    }

    private void connectAndSend(ReportMessageConsumer handler,
                                com.kun.aiinterview.interview.report.async.ReportTaskMessage message) throws Exception {
        runtime = new ReportMqRuntime(properties(), handler, objectMapper);
        runtime.connect();
        new ReportMessageProducer(runtime).send(message);
    }

    private ReportAsyncProperties properties() {
        var properties = new ReportAsyncProperties();
        properties.setNameserver("127.0.0.1:19876");
        properties.setTopic("ai-interview-m1b-report-test");
        properties.setConsumerGroup("ai-interview-m1b-report-test-consumer");
        return properties;
    }

    private void signal(String window, long taskId, long sessionId) {
        System.out.printf("M1C_CRASH_WINDOW %s %d %d %d %d%n", window,
                ProcessHandle.current().pid(), taskId, sessionId, userId);
        System.out.flush();
    }
}
