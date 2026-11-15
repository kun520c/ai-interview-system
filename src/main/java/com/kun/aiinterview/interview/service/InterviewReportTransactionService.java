package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.common.exception.ConflictException;
import com.kun.aiinterview.common.exception.ResourceNotFoundException;
import com.kun.aiinterview.interview.entity.*;
import com.kun.aiinterview.interview.entity.InterviewReportTask.State;
import com.kun.aiinterview.interview.entity.InterviewReportTask.Delivery;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.mapper.*;
import com.kun.aiinterview.interview.report.async.*;
import com.kun.aiinterview.user.service.UserWeaknessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/** All business locks are acquired Session -> Task. No external calls in these transactions. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
public class InterviewReportTransactionService {
    private final InterviewReportMapper interviewReportMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewReportTaskMapper taskMapper;
    private final UserWeaknessService userWeaknessService;
    private final InterviewReportFinalReader finalReader;
    private final ReportAsyncProperties properties;
    private final ObjectProvider<InterviewReportGenerator> generatorProvider;
    private final ApplicationEventPublisher events;

    public InterviewReportStatus acceptReportTask(Long userId, Long sessionId) {
        // A separate query prevents MyBatis's session cache from reusing this
        // non-locking authorization precheck for the post-lock session read.
        Long ownerId = interviewSessionMapper.getReportOwnerId(sessionId);
        if (ownerId == null || !Objects.equals(ownerId, userId)) {
            throw new ResourceNotFoundException("面试会话不存在");
        }
        LockedSession locked = lockSession(sessionId);
        InterviewSession session = locked.session();
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new ResourceNotFoundException("面试会话不存在");
        }
        if (session.getStatus() != InterviewSessionStatus.COMPLETED) {
            throw new ConflictException("面试尚未完成，无法生成报告");
        }
        if (session.getReportStatus() == null) throw new IllegalStateException("Session缺少Report状态");
        if (session.getReportStatus() == InterviewReportStatus.READY) {
            if (interviewReportMapper.getBySessionId(sessionId) == null) {
                throw new IllegalStateException("Session为READY但Report不存在");
            }
            return InterviewReportStatus.READY;
        }
        // Reject inconsistent FINAL data before any state mutation, including duplicate POSTs.
        finalReader.aggregateFinalEvaluations(session);
        if (!properties.isEnabled()) {
            // The disabled application also works before the separately approved task-table migration.
            if (session.getReportStatus() == InterviewReportStatus.GENERATING) return InterviewReportStatus.GENERATING;
            throw new BusinessException("报告生成服务当前不可用");
        }
        var active = taskMapper.listActiveBySessionId(sessionId);
        if (session.getReportStatus() == InterviewReportStatus.GENERATING) {
            if (active.size() != 1) {
                log.error("REPORT_HISTORY_REQUIRES_RECONCILIATION sessionId={} activeTasks={}", sessionId, active.size());
            }
            // Historical GENERATING is never reclaimed through POST.
            return InterviewReportStatus.GENERATING;
        }
        if (!active.isEmpty()) throw new IllegalStateException("非GENERATING会话存在活跃报告任务，需人工核对");
        if (!properties.isAccepting() || generatorProvider.getIfAvailable() == null) {
            throw new BusinessException("报告生成服务当前不可用");
        }
        // Reserve acceptance + execution + terminal increments; never overflow Java Integer.
        if (locked.version() > Integer.MAX_VALUE - 3L) {
            failVersion(locked);
            return InterviewReportStatus.FAILED;
        }
        int version = Math.toIntExact(locked.version());
        requireOne(interviewSessionMapper.claimReportGeneration(sessionId, version, session.getReportStatus()), "report acceptance");
        InterviewReportTask task = new InterviewReportTask();
        task.setSessionId(sessionId);
        task.setAttemptVersion(locked.version() + 1);
        requireOne(taskMapper.insertQueued(task, properties.getQueueDeadlineSeconds()), "outbox insert");
        if (task.getId() == null || task.getId() <= 0) throw new IllegalStateException("Report task主键未回填");
        events.publishEvent(new ReportOutboxWakeup.TaskAccepted(task.getId()));
        return InterviewReportStatus.GENERATING;
    }

    /** Null means DB-confirmed duplicate/terminal/expired/stale, never an uncertain result. */
    public ReportExecution claimTaskExecution(ReportTaskMessage message) {
        message.validate();
        LockedSession locked = lockSession(message.sessionId());
        InterviewReportTask task = requireTask(message);
        if (terminal(task)) return null;
        if (!ownsSession(locked, task)) {
            settleStaleOrThrow(locked, task);
            return null;
        }
        LocalDateTime now = taskMapper.databaseNow();
        if (expired(task, now)) {
            failOwned(locked, task, task.getTaskState() == State.QUEUED ? "QUEUE_EXPIRED" : "EXECUTION_EXPIRED");
            return null;
        }
        if (task.getTaskState() == State.RUNNING) return null;
        if (locked.version() > Integer.MAX_VALUE - 2L) {
            failVersion(locked);
            requireOne(taskMapper.finishFailure(task.getId(), task.getTaskState(), "VERSION_EXHAUSTED"), "overflow task");
            return null;
        }
        int executionVersion = Math.toIntExact(locked.version() + 1);
        requireOne(interviewSessionMapper.claimReportExecution(message.sessionId(), Math.toIntExact(locked.version())), "execution CAS");
        requireOne(taskMapper.claimExecution(task.getId(), message.attemptVersion(), executionVersion,
                properties.getExecutionDeadlineSeconds()), "task execution CAS");
        InterviewSession session = locked.session();
        session.setVersion(executionVersion);
        return new ReportExecution(message, executionVersion, session);
    }

    public void completeReportGeneration(ReportExecution execution, InterviewReport report) {
        LockedSession locked = lockSession(execution.message().sessionId());
        InterviewReportTask task = requireTask(execution.message());
        if (!requireExecutionOrSettle(locked, task, execution)) return;
        if (expired(task, taskMapper.databaseNow())) {
            failOwned(locked, task, "EXECUTION_EXPIRED");
            return;
        }
        var aggregate = finalReader.aggregateFinalEvaluations(locked.session());
        if (report == null || !Objects.equals(report.getSessionId(), locked.session().getId())
                || report.getOverallScore() == null || report.getOverallScore().compareTo(aggregate.overallScore()) != 0) {
            throw new IllegalStateException("报告与当前FINAL汇总不一致");
        }
        InterviewReport existing = interviewReportMapper.getBySessionId(report.getSessionId());
        if (existing == null) {
            requireOne(interviewReportMapper.insertReport(report), "report insert");
            if (report.getId() == null) throw new IllegalStateException("InterviewReport主键未回填");
            userWeaknessService.applySessionEvaluations(locked.session().getUserId(), aggregate.evaluations());
        } else {
            // No evidence permits replaying weakness updates on a historical row.
            if (existing.getOverallScore() == null || existing.getOverallScore().compareTo(aggregate.overallScore()) != 0) {
                throw new IllegalStateException("历史报告与FINAL分数不一致，需人工核对");
            }
            log.warn("REPORT_HISTORY_WEAKNESS_AUDIT_REQUIRED sessionId={} taskId={}", report.getSessionId(), task.getId());
        }
        requireOne(interviewSessionMapper.markReportReady(report.getSessionId(), execution.executionVersion(),
                aggregate.overallScore()), "session READY");
        // Deadline is also in the final SQL: time spent doing the writes cannot slip past it.
        requireOne(taskMapper.finishSuccess(task.getId(), execution.executionVersion()), "task SUCCEEDED");
    }

    public void failExecution(ReportExecution execution, String failureCode) {
        LockedSession locked = lockSession(execution.message().sessionId());
        InterviewReportTask task = requireTask(execution.message());
        if (!requireExecutionOrSettle(locked, task, execution)) return;
        failOwned(locked, task, expired(task, taskMapper.databaseNow()) ? "EXECUTION_EXPIRED" : failureCode);
    }

    /** Called by bounded scans, always re-checking deadlines after both locks. */
    public void expireTask(ReportTaskMessage message) {
        LockedSession locked = lockSession(message.sessionId());
        InterviewReportTask task = requireTask(message);
        if (terminal(task)) return;
        if (!ownsSession(locked, task)) {
            settleStaleOrThrow(locked, task);
            return;
        }
        LocalDateTime now = taskMapper.databaseNow();
        if (expired(task, now)) {
            failOwned(locked, task, task.getTaskState() == State.QUEUED ? "QUEUE_EXPIRED" : "EXECUTION_EXPIRED");
        } else if (task.getTaskState() == State.QUEUED && task.getDeliveryState() != Delivery.SENT
                && task.getSendAttempts() >= properties.getMaxSendAttempts()
                && (task.getDeliveryState() == Delivery.PENDING || !task.getDispatchLeaseUntil().isAfter(now))) {
            failOwned(locked, task, "SEND_EXHAUSTED");
        }
    }

    private boolean requireExecutionOrSettle(LockedSession locked, InterviewReportTask task, ReportExecution execution) {
        if (terminal(task)) return false;
        if (!ownsSession(locked, task)) {
            settleStaleOrThrow(locked, task);
            return false;
        }
        if (task.getTaskState() != State.RUNNING || !Objects.equals(task.getExecutionVersion(), (long) execution.executionVersion())) {
            throw new IllegalStateException("报告执行所有权无法确认");
        }
        return true;
    }

    private void failOwned(LockedSession locked, InterviewReportTask task, String code) {
        if (locked.version() >= Integer.MAX_VALUE) failVersion(locked);
        else requireOne(interviewSessionMapper.markReportFailed(locked.session().getId(), Math.toIntExact(locked.version())), "session FAILED");
        requireOne(taskMapper.finishFailure(task.getId(), task.getTaskState(), code), "task FAILED");
        log.warn("REPORT_TASK_FAILED taskId={} sessionId={} code={}", task.getId(), task.getSessionId(), code);
    }

    private void settleStaleOrThrow(LockedSession locked, InterviewReportTask task) {
        Long expected = expectedVersion(task);
        if (expected != null && locked.version() > expected) {
            requireOne(taskMapper.finishFailure(task.getId(), task.getTaskState(), "STALE_ATTEMPT"), "stale task");
            return;
        }
        throw new IllegalStateException("报告任务与Session所有权不一致，需重试或人工核对");
    }

    private boolean ownsSession(LockedSession locked, InterviewReportTask task) {
        return locked.session().getStatus() == InterviewSessionStatus.COMPLETED
                && locked.session().getReportStatus() == InterviewReportStatus.GENERATING
                && Objects.equals(expectedVersion(task), locked.version());
    }

    private Long expectedVersion(InterviewReportTask task) {
        return task.getTaskState() == State.RUNNING ? task.getExecutionVersion() : task.getAttemptVersion();
    }

    private boolean expired(InterviewReportTask task, LocalDateTime now) {
        LocalDateTime deadline = task.getTaskState() == State.QUEUED ? task.getQueueDeadline() : task.getExecutionDeadline();
        if (deadline == null || now == null) throw new IllegalStateException("任务缺少数据库截止时间");
        return !deadline.isAfter(now);
    }

    private void failVersion(LockedSession locked) {
        requireOne(interviewSessionMapper.failReportVersionExhausted(locked.session().getId(), locked.version()), "version exhausted");
        log.error("REPORT_VERSION_EXHAUSTED sessionId={} version={}", locked.session().getId(), locked.version());
    }

    private InterviewReportTask requireTask(ReportTaskMessage message) {
        InterviewReportTask task = taskMapper.getByIdForUpdate(message.taskId());
        if (task == null || !Objects.equals(task.getSessionId(), message.sessionId())
                || !Objects.equals(task.getAttemptVersion(), message.attemptVersion()) || task.getTaskState() == null) {
            // Missing/mismatched rows are not evidence for a safe ACK.
            throw new IllegalStateException("报告消息未匹配持久任务");
        }
        return task;
    }

    private LockedSession lockSession(Long id) {
        Long version = interviewSessionMapper.getReportVersionForUpdate(id);
        if (version == null) throw new ResourceNotFoundException("面试会话不存在");
        InterviewSession session = interviewSessionMapper.getReportSession(id);
        if (session == null || version < 0) throw new IllegalStateException("报告Session无法读取");
        return new LockedSession(session, version);
    }

    private boolean terminal(InterviewReportTask task) {
        return task.getTaskState() == State.SUCCEEDED || task.getTaskState() == State.FAILED;
    }

    private static void requireOne(int rows, String operation) {
        if (rows != 1) throw new IllegalStateException(operation + " affected rows must be 1");
    }

    private record LockedSession(InterviewSession session, Long version) {}
}
