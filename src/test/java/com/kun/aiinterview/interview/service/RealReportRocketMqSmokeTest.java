package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.InterviewReportTask;
import com.kun.aiinterview.interview.report.async.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real independent RocketMQ + approved MySQL + fake model. No production brokers/schemas. */
@SpringBootTest
@ActiveProfiles({"local", "test", "report-mq-test"})
@EnabledIfEnvironmentVariable(named="RUN_REAL_REPORT_MQ_TEST", matches="true")
class RealReportRocketMqSmokeTest extends ReportDatabaseFixture {
    private ReportMqRuntime runtime;
    private ReportMessageProducer producer;
    private ReportOutboxDispatcher dispatcher;
    private ReportMessageConsumer handler;

    @Override @BeforeEach void setUp() {
        // Second guard precedes even the fixture's first database write.
        Assumptions.assumeTrue("true".equals(System.getenv("RUN_REAL_REPORT_MQ_TEST")));
        super.setUp();
        var p=new ReportAsyncProperties();
        p.setNameserver("127.0.0.1:19876");
        p.setTopic("ai-interview-m1b-report-test");
        p.setConsumerGroup("ai-interview-m1b-report-test-consumer");
        // Test-only retry budget; production remains five retries.
        p.setMaxReconsumeTimes(1);
        handler=org.mockito.Mockito.spy(new ReportMessageConsumer(transactions,service));
        runtime=new ReportMqRuntime(p,handler,objectMapper);
        runtime.connect();
        producer=new ReportMessageProducer(runtime);
        dispatcher=new ReportOutboxDispatcher(tasks,transactions,producer,p);
    }

    @AfterEach void closeMqBeforeFixtureCleanup() {
        if(runtime!=null) runtime.close();
    }

    @Test void committedOutboxSurvivesNoSendAndDuplicateDeliveryDoesNotRepeatWeakness() throws Exception {
        Assumptions.assumeFalse("true".equals(System.getenv("M1B_MQ_EXPECT_UNAVAILABLE")));
        var message=accept();
        // Commit has completed; no dispatch until this explicit invocation (no process kill claimed).
        assertThat(reports.getBySessionId(sessionId)).isNull();
        dispatcher.dispatchDue();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(tasks.getById(message.taskId()).getTaskState()).isEqualTo(InterviewReportTask.State.SUCCEEDED));
        producer.send(message); producer.send(message);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(generatorCalls.get()).isEqualTo(1);
            assertThat(((Number)weakness().get("discovered_count")).intValue()).isEqualTo(1);
        });
        assertThat(reports.getBySessionId(sessionId)).isNotNull();
        System.out.println("M1B_REAL_MQ committed-outbox duplicate-delivery modelCalls="+generatorCalls.get());
    }

    @Test void brokerAcceptedBeforeSentRecordCannotRevertCompletedTask() throws Exception {
        Assumptions.assumeFalse("true".equals(System.getenv("M1B_MQ_EXPECT_UNAVAILABLE")));
        var message=accept(); var token=java.util.UUID.randomUUID().toString();
        assertThat(tasks.claimDispatch(message.taskId(),token,30,20)).isEqualTo(1);
        producer.send(message);
        // Deterministically withhold markSent until actual consumer commits. Not a wire-level lost ACK.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(tasks.getById(message.taskId()).getTaskState()).isEqualTo(InterviewReportTask.State.SUCCEEDED));
        assertThat(tasks.markSent(message.taskId(),token)).isEqualTo(1);
        assertThat(tasks.getById(message.taskId()).getTaskState()).isEqualTo(InterviewReportTask.State.SUCCEEDED);
        assertThat(generatorCalls.get()).isEqualTo(1);
        System.out.println("M1B_REAL_MQ consumer-before-sent preserved SUCCEEDED");
    }
    @Test void unavailableBrokerStillAllowsDeadlineRecovery() {
        Assumptions.assumeTrue("true".equals(System.getenv("M1B_MQ_EXPECT_UNAVAILABLE")));
        var message=accept();
        jdbc.update("UPDATE interview_report_task SET send_attempts=19 WHERE id=?",message.taskId());
        dispatcher.dispatchDue();
        assertThat(tasks.getById(message.taskId()).getFailureCode()).isEqualTo("SEND_EXHAUSTED");
        assertThat(generatorCalls.get()).isZero();
        assertThat(reports.getBySessionId(sessionId)).isNull();
        var retry=accept();
        jdbc.update("UPDATE interview_report_task SET queue_deadline=UTC_TIMESTAMP(6) WHERE id=?",retry.taskId());
        dispatcher.expireDue();
        assertThat(tasks.getById(retry.taskId()).getFailureCode()).isEqualTo("QUEUE_EXPIRED");
        System.out.println("M1B_REAL_MQ broker unavailable: SEND_EXHAUSTED and QUEUE_EXPIRED persisted");
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void retryExhaustionDlqAndControlledReplay(boolean illegalPayload) throws Exception {
        Assumptions.assumeFalse("true".equals(System.getenv("M1B_MQ_EXPECT_UNAVAILABLE")));
        var message=accept();
        if (illegalPayload) {
            var raw=new org.apache.rocketmq.client.producer.DefaultMQProducer("ai-interview-m1b-probe");
            raw.setNamesrvAddr("127.0.0.1:19876");raw.setVipChannelEnabled(false);raw.setRetryTimesWhenSendFailed(0);
            try {
                raw.start();
                var body=objectMapper.writeValueAsString(message);
                body=body.substring(0,body.length()-1)+",\"unexpected\":\"synthetic\"}";
                var invalid=new org.apache.rocketmq.common.message.Message("ai-interview-m1b-report-test",body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                invalid.setKeys(message.taskId().toString());
                assertThat(raw.send(invalid,5000).getSendStatus()).isEqualTo(org.apache.rocketmq.client.producer.SendStatus.SEND_OK);
            } finally {raw.shutdown();}
        } else {
            // Injected application/DB-unreadable exception; the Broker retry and DLQ are real.
            org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("injected"))
                    .when(handler).onMessage(message);
            producer.send(message);
        }
        await().pollInterval(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(60)).until(() -> dlqContains(message.taskId()));
        // DLQ is not a business FAILED transition.
        assertThat(tasks.getById(message.taskId()).getTaskState()).isEqualTo(InterviewReportTask.State.QUEUED);
        assertThat(generatorCalls.get()).isZero();
        org.mockito.Mockito.doCallRealMethod().when(handler).onMessage(message);
        var persisted=tasks.getById(message.taskId());
        assertThat(persisted.getQueueDeadline()).isAfter(tasks.databaseNow());
        assertThat(persisted.getAttemptVersion()).isEqualTo(message.attemptVersion());
        // Operator-controlled replay reconstructs only the three stable IDs from the verified DB row.
        producer.send(ReportOutboxDispatcher.message(persisted));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(tasks.getById(message.taskId()).getTaskState()).isEqualTo(InterviewReportTask.State.SUCCEEDED));
        assertThat(generatorCalls.get()).isEqualTo(1);
        assertThat(((Number)weakness().get("discovered_count")).intValue()).isEqualTo(1);
        System.out.println("M1B_REAL_MQ DLQ and controlled replay illegalPayload="+illegalPayload);
    }

    private boolean dlqContains(Long taskId) throws Exception {
        Process process=new ProcessBuilder("docker","compose","-f","tools/m1b/compose.yaml","exec","-T","broker",
                "sh","mqadmin","queryMsgByKey","-n","namesrv:9876","-t",
                "%DLQ%ai-interview-m1b-report-test-consumer","-k",taskId.toString()).redirectErrorStream(true).start();
        if (!process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)) {process.destroyForcibly();return false;}
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        // queryMsgByKey filters by exact topic/key and prints ID/QID/Offset rows, not MessageExt.
        boolean found=process.exitValue()==0 && output.lines().anyMatch(line ->
                line.strip().matches("[0-9A-Fa-f]{32}\\s+\\d+\\s+\\d+"));
        if (found) System.out.println("M1B_DLQ_CONFIRMED taskId="+taskId+" "+output.strip());
        return found;
    }
}
