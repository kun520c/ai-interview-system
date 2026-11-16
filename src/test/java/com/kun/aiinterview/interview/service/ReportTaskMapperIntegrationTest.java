package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.InterviewReportTask;
import com.kun.aiinterview.interview.report.async.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class ReportTaskMapperIntegrationTest extends ReportDatabaseFixture {
    @Test void generatedKeyUniqueAttemptAndCheckConstraints() {
        var message=accept();
        assertThat(message.taskId()).isPositive();
        var task=tasks.getById(message.taskId());
        assertThat(task.getTaskState()).isEqualTo(InterviewReportTask.State.QUEUED);
        assertThat(task.getDeliveryState()).isEqualTo(InterviewReportTask.Delivery.PENDING);
        assertThat(ChronoUnit.SECONDS.between(task.getQueuedAt(),task.getQueueDeadline())).isEqualTo(600);
        assertThatThrownBy(() -> tasks.insertQueued(task,600)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE interview_report_task SET task_state='INVALID' WHERE id=?",message.taskId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(tasks.getById(message.taskId()).getTaskState()).isEqualTo(InterviewReportTask.State.QUEUED);
    }

    @Test void lateSendAndFailureReceiptCannotUndoSuccessOrNewLease() {
        var message=accept(); var token=UUID.randomUUID().toString();
        assertThat(tasks.claimDispatch(message.taskId(),token,30,20)).isEqualTo(1);
        assertThat(tasks.claimDispatch(message.taskId(),UUID.randomUUID().toString(),30,20)).isZero();
        var execution=transactions.claimTaskExecution(message);
        transactions.completeReportGeneration(execution,reportDraft());
        assertThat(tasks.markSent(message.taskId(),"old-token")).isZero();
        assertThat(tasks.rescheduleDispatch(message.taskId(),token,2)).isZero();
        assertThat(tasks.markSent(message.taskId(),token)).isEqualTo(1);
        var result=tasks.getById(message.taskId());
        assertThat(result.getTaskState()).isEqualTo(InterviewReportTask.State.SUCCEEDED);
        assertThat(result.getExecutionVersion()).isEqualTo((long)execution.executionVersion());
    }

    @Test void expiredLeaseCanBeReclaimedButLateTokenCannotSettle() {
        var message=accept(); var old=UUID.randomUUID().toString(); var current=UUID.randomUUID().toString();
        assertThat(tasks.claimDispatch(message.taskId(),old,30,20)).isEqualTo(1);
        jdbc.update("UPDATE interview_report_task SET dispatch_lease_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE id=?",message.taskId());
        assertThat(tasks.listExpiredLeases(1)).extracting(InterviewReportTask::getId).contains(message.taskId());
        assertThat(tasks.claimDispatch(message.taskId(),current,30,20)).isEqualTo(1);
        assertThat(tasks.markSent(message.taskId(),old)).isZero();
        assertThat(tasks.rescheduleDispatch(message.taskId(),old,2)).isZero();
        assertThat(tasks.getById(message.taskId()).getSendAttempts()).isEqualTo(2);
        assertThat(tasks.markSent(message.taskId(),current)).isEqualTo(1);
    }

    @Test void twoDispatchersHaveOneLeaseWinner() throws Exception {
        var message=accept(); var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(() -> {start.await();return tasks.claimDispatch(message.taskId(),UUID.randomUUID().toString(),30,20);});
            var b=executor.submit(() -> {start.await();return tasks.claimDispatch(message.taskId(),UUID.randomUUID().toString(),30,20);});
            start.countDown(); assertThat(a.get(10,TimeUnit.SECONDS)+b.get(10,TimeUnit.SECONDS)).isEqualTo(1);
        }
    }

    @Test void queueDeadlineAndClaimRaceHaveNoLateOwner() throws Exception {
        var message=accept();
        jdbc.update("UPDATE interview_report_task SET queue_deadline=UTC_TIMESTAMP(6) WHERE id=?",message.taskId());
        try(var executor=Executors.newFixedThreadPool(2)) {
            var claim=executor.submit(() -> transactions.claimTaskExecution(message));
            var expiry=executor.submit(() -> transactions.expireTask(message));
            assertThat(claim.get(10,TimeUnit.SECONDS)).isNull(); expiry.get(10,TimeUnit.SECONDS);
        }
        assertThat(tasks.getById(message.taskId()).getFailureCode()).isEqualTo("QUEUE_EXPIRED");
        assertThat(generatorCalls.get()).isZero();
    }

    @Test void databaseUtcClockAndBoundedIndexScans() {
        var message=accept();
        assertThat(Math.abs(ChronoUnit.SECONDS.between(tasks.databaseNow(),LocalDateTime.now(ZoneOffset.UTC)))).isLessThan(5);
        assertThat(tasks.listPending(1)).hasSize(1);
        for(String query : java.util.List.of(
                "task_state='QUEUED' AND delivery_state='PENDING' AND next_send_at<=UTC_TIMESTAMP(6) ORDER BY next_send_at,id",
                "task_state='QUEUED' AND delivery_state='LEASED' AND dispatch_lease_until<=UTC_TIMESTAMP(6) ORDER BY dispatch_lease_until,id",
                "task_state='QUEUED' AND queue_deadline<=UTC_TIMESTAMP(6) ORDER BY queue_deadline,id",
                "task_state='RUNNING' AND execution_deadline<=UTC_TIMESTAMP(6) ORDER BY execution_deadline,id")) {
            var plan=jdbc.queryForMap("EXPLAIN SELECT * FROM interview_report_task WHERE "+query+" LIMIT 16");
            assertThat(plan.get("possible_keys")).isNotNull();
            System.out.println("M1B_EXPLAIN key="+plan.get("key")+" rows="+plan.get("rows")+" extra="+plan.get("Extra"));
        }
        assertThat(tasks.getById(message.taskId()).getNextSendAt()).isNotNull();
    }

    @ParameterizedTest @ValueSource(longs={2147483645L,2147483647L,4294967295L})
    void unsignedVersionCannotOverflowJava(long version) {
        jdbc.update("UPDATE interview_session SET version=? WHERE id=?",version,sessionId);
        assertThat(service.generateReportForApi(userId,sessionId).reportStatus()).isEqualTo(com.kun.aiinterview.interview.enums.InterviewReportStatus.FAILED);
        assertThat(jdbc.queryForObject("SELECT version FROM interview_session WHERE id=?",Long.class,sessionId)).isEqualTo(version);
        assertThat(tasks.listActiveBySessionId(sessionId)).isEmpty();
        assertThat(service.getReport(userId,sessionId).reportStatus()).isEqualTo(com.kun.aiinterview.interview.enums.InterviewReportStatus.FAILED);
    }

    @Test void sendExhaustionAndQueueExecutionDeadlinesAreIndependent() {
        var message=accept();
        jdbc.update("UPDATE interview_report_task SET send_attempts=20 WHERE id=?",message.taskId());
        transactions.expireTask(message);
        assertThat(tasks.getById(message.taskId()).getFailureCode()).isEqualTo("SEND_EXHAUSTED");
        var retry=accept();
        assertThat(retry.taskId()).isNotEqualTo(message.taskId());
        var execution=transactions.claimTaskExecution(retry);
        jdbc.update("UPDATE interview_report_task SET queue_deadline=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE id=?",retry.taskId());
        transactions.expireTask(retry);
        assertThat(tasks.getById(retry.taskId()).getTaskState()).isEqualTo(InterviewReportTask.State.RUNNING);
        jdbc.update("UPDATE interview_report_task SET execution_deadline=UTC_TIMESTAMP(6) WHERE id=?",retry.taskId());
        transactions.completeReportGeneration(execution,reportDraft());
        assertThat(tasks.getById(retry.taskId()).getFailureCode()).isEqualTo("EXECUTION_EXPIRED");
        assertThat(reports.getBySessionId(sessionId)).isNull();
    }
}
