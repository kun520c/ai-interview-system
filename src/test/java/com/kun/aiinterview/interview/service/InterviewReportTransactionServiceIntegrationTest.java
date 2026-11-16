package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.InterviewReportTask;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.model.InterviewReportGenerationResult;
import com.kun.aiinterview.interview.report.async.*;
import com.kun.aiinterview.user.service.UserWeaknessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real MySQL/MyBatis commits and row-lock races; no real MQ or model in this class. */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class InterviewReportTransactionServiceIntegrationTest extends ReportDatabaseFixture {
    @MockitoSpyBean UserWeaknessService weaknessService;
    @MockitoSpyBean com.kun.aiinterview.interview.mapper.InterviewReportTaskMapper taskSpy;
    @org.springframework.beans.factory.annotation.Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;

    @Test void taskInsertFailureRollsBackAcceptanceAndInsertedOutboxRow() {
        doAnswer(invocation -> {
            var task = (InterviewReportTask) invocation.getArgument(0);
            // MyBatis interface proxies have no concrete callRealMethod; use the same transaction-bound template.
            sqlSession.getMapper(com.kun.aiinterview.interview.mapper.InterviewReportTaskMapper.class)
                    .insertQueued(task,invocation.getArgument(1));
            // The task insert succeeds first; a real constraint failure aborts that same transaction.
            jdbc.update("UPDATE interview_report_task SET task_state=NULL WHERE id=?",task.getId());
            return 1;
        }).when(taskSpy).insertQueued(any(),anyInt());
        assertThatThrownBy(() -> service.generateReportForApi(userId,sessionId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(tasks.listActiveBySessionId(sessionId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_report_task WHERE session_id=?",Integer.class,sessionId)).isZero();
        var unchanged=sessions.getInterviewSessionById(sessionId);
        assertThat(unchanged.getReportStatus()).isEqualTo(InterviewReportStatus.NOT_STARTED);
        assertThat(unchanged.getVersion()).isZero();
        assertThat(generatorCalls.get()).isZero();
    }

    @Test void shouldInsertReportWeaknessAndMarkSessionReadyAtomically() {
        var message = accept();
        assertThat(reports.getBySessionId(sessionId)).isNull();
        var execution = transactions.claimTaskExecution(message);
        transactions.completeReportGeneration(execution,reportDraft());
        assertThat(reports.getBySessionId(sessionId).getOverallScore()).isEqualByComparingTo("70.00");
        assertThat(((Number)weakness().get("discovered_count")).intValue()).isEqualTo(1);
        assertThat((BigDecimal)weakness().get("weakness_score")).isEqualByComparingTo("30.00");
        assertThat(sessions.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.READY);
        assertThat(tasks.getById(message.taskId()).getTaskState()).isEqualTo(InterviewReportTask.State.SUCCEEDED);
        assertThat(sessions.getInterviewSessionById(sessionId).getVersion()).isEqualTo(3);
    }

    @Test void shouldRejectOldVersionBeforeReportAndWeaknessWrites() {
        var execution = transactions.claimTaskExecution(accept());
        var old = new ReportExecution(execution.message(),execution.executionVersion()-1,execution.session());
        assertThatThrownBy(() -> transactions.completeReportGeneration(old,reportDraft())).isInstanceOf(IllegalStateException.class);
        assertNoReportOrWeakness();
        assertThat(tasks.getById(execution.message().taskId()).getTaskState()).isEqualTo(InterviewReportTask.State.RUNNING);
    }

    @Test void shouldRollbackReportAndWeaknessWhenSessionReadyUpdateFails() {
        var execution = transactions.claimTaskExecution(accept());
        doAnswer(invocation -> {
            invocation.callRealMethod();
            // Fault injection after report and weakness writes, within the same transaction.
            assertThat(jdbc.update("UPDATE interview_session SET version=version+1 WHERE id=?",sessionId)).isEqualTo(1);
            return null;
        }).when(weaknessService).applySessionEvaluations(eq(userId),any());
        assertThatThrownBy(() -> transactions.completeReportGeneration(execution,reportDraft()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("session READY affected rows must be 1");
        assertNoReportOrWeakness();
        assertThat(tasks.getById(execution.message().taskId()).getTaskState()).isEqualTo(InterviewReportTask.State.RUNNING);
        assertThat(sessions.getInterviewSessionById(sessionId).getVersion()).isEqualTo(execution.executionVersion());
        assertThat(sessions.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
    }

    @Test void shouldRollbackReportAndWeaknessWhenLastTaskWriteFails() {
        var execution = transactions.claimTaskExecution(accept());
        doAnswer(invocation -> {
            invocation.callRealMethod();
            jdbc.update("UPDATE interview_report_task SET execution_deadline=UTC_TIMESTAMP(6) WHERE id=?",execution.message().taskId());
            return null;
        }).when(weaknessService).applySessionEvaluations(eq(userId),any());
        assertThatThrownBy(() -> transactions.completeReportGeneration(execution,reportDraft())).isInstanceOf(IllegalStateException.class);
        assertNoReportOrWeakness();
        assertThat(tasks.getById(execution.message().taskId()).getTaskState()).isEqualTo(InterviewReportTask.State.RUNNING);
        assertThat(sessions.getInterviewSessionById(sessionId).getVersion()).isEqualTo(execution.executionVersion());
        assertThat(sessions.getInterviewSessionById(sessionId).getReportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
    }

    @Test void shouldRollbackReportWhenWeaknessSqlFails() {
        var execution = transactions.claimTaskExecution(accept());
        doAnswer(invocation -> {
            invocation.callRealMethod();
            // Actual MySQL NOT NULL violation after a successful weakness write, all rolled back.
            jdbc.update("UPDATE user_weakness SET knowledge_point=NULL WHERE user_id=?",userId);
            return null;
        }).when(weaknessService).applySessionEvaluations(eq(userId),any());
        assertThatThrownBy(() -> transactions.completeReportGeneration(execution,reportDraft()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertNoReportOrWeakness();
    }

    @ParameterizedTest @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void oldReportVersionCannotCommitOrFailReplacement(boolean oldThrows, boolean replacementFinishesFirst) throws Exception {
        var oldEntered = new CountDownLatch(1); var newEntered = new CountDownLatch(1);
        var releaseOld = new CountDownLatch(1); var releaseNew = new CountDownLatch(1);
        var calls = new AtomicInteger();
        when(configuredGenerator.generate(any(),any(),any())).thenAnswer(invocation -> {
            boolean old = calls.incrementAndGet()==1;
            (old?oldEntered:newEntered).countDown();
            assertThat((old?releaseOld:releaseNew).await(10,TimeUnit.SECONDS)).isTrue();
            if(old && oldThrows) throw new IllegalArgumentException("old provider failed");
            return new InterviewReportGenerationResult("FAIR",old?"worker-A":"worker-B",List.of(),List.of(),List.of(),"fake","fake");
        });
        var consumer = new ReportMessageConsumer(transactions,service);
        var oldMessage = accept();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var old = executor.submit(() -> consumer.onMessage(oldMessage));
            assertThat(oldEntered.await(10,TimeUnit.SECONDS)).isTrue();
            jdbc.update("UPDATE interview_report_task SET execution_deadline=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE id=?",oldMessage.taskId());
            transactions.expireTask(oldMessage);
            var newMessage = accept();
            assertThat(newMessage.taskId()).isNotEqualTo(oldMessage.taskId());
            assertThat(newMessage.attemptVersion()).isGreaterThan(oldMessage.attemptVersion());
            var replacement = executor.submit(() -> consumer.onMessage(newMessage));
            assertThat(newEntered.await(10,TimeUnit.SECONDS)).isTrue();
            int version = sessions.getInterviewSessionById(sessionId).getVersion();
            if(replacementFinishesFirst) {releaseNew.countDown(); replacement.get(10,TimeUnit.SECONDS);}
            releaseOld.countDown(); old.get(10,TimeUnit.SECONDS);
            if(!replacementFinishesFirst) {
                assertThat(sessions.getInterviewSessionById(sessionId).getVersion()).isEqualTo(version);
                assertNoReportOrWeakness(); releaseNew.countDown(); replacement.get(10,TimeUnit.SECONDS);
            }
            assertThat(reports.getBySessionId(sessionId).getSummary()).isEqualTo("worker-B");
            assertThat(((Number)weakness().get("discovered_count")).intValue()).isEqualTo(1);
            consumer.onMessage(oldMessage); consumer.onMessage(newMessage);
            assertThat(calls.get()).isEqualTo(2);
        } finally {releaseOld.countDown();releaseNew.countDown();executor.shutdownNow();executor.awaitTermination(10,TimeUnit.SECONDS);}
    }

    @Test void simultaneousReportRequestsHaveOneClaimOneReportAndOneWeaknessUpdate() throws Exception {
        var start = new CountDownLatch(1);
        try(var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {start.await();return service.generateReportForApi(userId,sessionId);});
            var second = executor.submit(() -> {start.await();return service.generateReportForApi(userId,sessionId);});
            start.countDown();
            assertThat(first.get(10,TimeUnit.SECONDS).reportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
            assertThat(second.get(10,TimeUnit.SECONDS).reportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
            assertThat(tasks.listActiveBySessionId(sessionId)).hasSize(1);
            var message=ReportOutboxDispatcher.message(tasks.listActiveBySessionId(sessionId).getFirst());
            var consumer=new ReportMessageConsumer(transactions,service);
            var c1=executor.submit(() -> consumer.onMessage(message));
            var c2=executor.submit(() -> consumer.onMessage(message));
            c1.get(10,TimeUnit.SECONDS);c2.get(10,TimeUnit.SECONDS);
            assertThat(generatorCalls.get()).isEqualTo(1);
            assertThat(((Number)weakness().get("discovered_count")).intValue()).isEqualTo(1);
            assertReadyReplayUnchanged(reports.getBySessionId(sessionId));
        }
    }

    @Test void newPostCannotReclaimHistoricalGeneratingWithoutTask() {
        jdbc.update("UPDATE interview_session SET report_status='GENERATING',updated_at=TIMESTAMPADD(MINUTE,-20,NOW()) WHERE id=?",sessionId);
        assertThat(service.generateReportForApi(userId,sessionId).reportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
        assertThat(tasks.listActiveBySessionId(sessionId)).isEmpty();
        assertThat(generatorCalls.get()).isZero(); assertNoReportOrWeakness();
    }

    @ParameterizedTest @ValueSource(strings = {"NOT_STARTED", "FAILED", "READY", "GENERATING"})
    void disabledReportApiDoesNotMutatePersistedState(String status) {
        if (status.equals("READY")) acceptAndConsume();
        else jdbc.update("UPDATE interview_session SET report_status=? WHERE id=?",status,sessionId);
        when(asyncProperties.isEnabled()).thenReturn(false);
        var before = jdbc.queryForMap("SELECT * FROM interview_session WHERE id=?",sessionId);
        var taskRows = jdbc.queryForList("SELECT * FROM interview_report_task WHERE session_id=?",sessionId);
        int calls = generatorCalls.get();
        assertThat(service.getReport(userId,sessionId).reportStatus()).isEqualTo(InterviewReportStatus.valueOf(status));
        if (status.equals("NOT_STARTED") || status.equals("FAILED")) {
            assertThatThrownBy(() -> service.generateReportForApi(userId,sessionId))
                    .isInstanceOf(com.kun.aiinterview.common.exception.ServiceUnavailableException.class)
                    .hasMessage("报告生成服务未启用，请联系管理员");
            assertNoReportOrWeakness();
        } else if (status.equals("READY")) {
            assertReadyReplayUnchanged(reports.getBySessionId(sessionId));
        } else {
            assertThat(service.generateReportForApi(userId,sessionId).reportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
            assertNoReportOrWeakness();
        }
        assertThat(jdbc.queryForMap("SELECT * FROM interview_session WHERE id=?",sessionId)).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM interview_report_task WHERE session_id=?",sessionId)).isEqualTo(taskRows);
        assertThat(generatorCalls.get()).isEqualTo(calls);
    }

    @Test void pausedAcceptanceRefusesNewAttemptButKeepsExistingWorkReadable() {
        when(asyncProperties.isAccepting()).thenReturn(false);
        assertThatThrownBy(() -> service.generateReportForApi(userId,sessionId))
                .isInstanceOf(com.kun.aiinterview.common.exception.ServiceUnavailableException.class)
                .hasMessage("报告生成服务暂不受理新请求，请稍后重试");
        assertThat(sessions.getInterviewSessionById(sessionId).getVersion()).isZero();
        assertThat(tasks.listActiveBySessionId(sessionId)).isEmpty();
        assertNoReportOrWeakness();
        when(asyncProperties.isAccepting()).thenReturn(true);
        var accepted = accept();
        when(asyncProperties.isAccepting()).thenReturn(false);
        assertThat(service.generateReportForApi(userId,sessionId).reportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
        assertThat(tasks.listActiveBySessionId(sessionId)).extracting(InterviewReportTask::getId).containsExactly(accepted.taskId());
        assertThat(service.getReport(userId,sessionId).reportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
        assertThat(generatorCalls.get()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"QUEUED", "RUNNING", "FAILED", "SUCCEEDED", "VERSION_MISMATCH", "MULTIPLE_ACTIVE", "REPORT_EXISTS"})
    void generatingReadsAndPostsNeverRepairOrDuplicateHistoricalState(String scenario) {
        var message = accept();
        switch (scenario) {
            case "RUNNING" -> transactions.claimTaskExecution(message);
            case "FAILED", "SUCCEEDED" -> jdbc.update("UPDATE interview_report_task SET task_state=? WHERE id=?",scenario,message.taskId());
            case "VERSION_MISMATCH" -> jdbc.update("UPDATE interview_session SET version=version+1 WHERE id=?",sessionId);
            case "MULTIPLE_ACTIVE" -> {
                var duplicate = new InterviewReportTask();
                duplicate.setSessionId(sessionId); duplicate.setAttemptVersion(message.attemptVersion()+1);
                tasks.insertQueued(duplicate,600);
            }
            case "REPORT_EXISTS" -> {
                reports.insertReport(reportDraft());
                jdbc.update("UPDATE interview_report_task SET task_state='SUCCEEDED' WHERE id=?",message.taskId());
            }
        }
        var before = jdbc.queryForMap("SELECT * FROM interview_session WHERE id=?",sessionId);
        var taskRows = jdbc.queryForList("SELECT * FROM interview_report_task WHERE session_id=? ORDER BY id",sessionId);
        var reportRows = jdbc.queryForList("SELECT * FROM interview_report WHERE session_id=?",sessionId);
        for (int i=0; i<2; i++) {
            assertThat(service.getReport(userId,sessionId).reportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
            var response=service.generateReportForApi(userId,sessionId);
            assertThat(response.reportStatus()).isEqualTo(InterviewReportStatus.GENERATING);
            assertThat(response.summary()).isNull();
        }
        assertThat(jdbc.queryForMap("SELECT * FROM interview_session WHERE id=?",sessionId)).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM interview_report_task WHERE session_id=? ORDER BY id",sessionId)).isEqualTo(taskRows);
        assertThat(jdbc.queryForList("SELECT * FROM interview_report WHERE session_id=?",sessionId)).isEqualTo(reportRows);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_weakness WHERE user_id=?",Integer.class,userId)).isZero();
        assertThat(generatorCalls.get()).isZero();
    }

    @Test void existingValidReportRecoveredWithoutApplyingWeaknessAgain() {
        var historical=reportDraft(); reports.insertReport(historical);
        assertThat(acceptAndConsume().getId()).isEqualTo(historical.getId());
        assertThat(generatorCalls.get()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_weakness WHERE user_id=?",Integer.class,userId)).isZero();
        assertReadyReplayUnchanged(historical);
    }

    private void assertNoReportOrWeakness() {
        assertThat(reports.getBySessionId(sessionId)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_weakness WHERE user_id=?",Integer.class,userId)).isZero();
    }
}
