package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.common.exception.ResourceNotFoundException;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doAnswer;

/** A foreign caller must not wait for an owner's locked session before receiving 404. */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class ReportOwnershipLockIsolationTest extends ReportDatabaseFixture {
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired SqlSessionTemplate sqlSession;
    @MockitoSpyBean InterviewSessionMapper sessionSpy;

    @Test
    void nonOwnerIsRejectedWithoutWaitingForSessionRowLock() throws Exception {
        String account = "m1c-owner-" + UUID.randomUUID();
        long foreignUser = insert("INSERT INTO `user` (account, username, password, email) VALUES (?, ?, ?, ?)",
                account, "M1C foreign", "test-hash", account + "@example.com");
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("SELECT id FROM interview_session WHERE id=? FOR UPDATE", Long.class, sessionId);
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test lock timed out");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
                return null;
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var denial = executor.submit(() -> catchThrowable(() -> service.generateReportForApi(foreignUser, sessionId)));
                assertThat(denial.get(2, TimeUnit.SECONDS)).isInstanceOf(ResourceNotFoundException.class);
            } finally {
                release.countDown();
                holder.get(5, TimeUnit.SECONDS);
            }
        } finally {
            jdbc.update("DELETE FROM `user` WHERE id=?", foreignUser);
        }
    }

    @Test
    void ownershipChangeWhileWaitingForLockIsReadFreshAndRejected() throws Exception {
        String account = "m1c-owner-" + UUID.randomUUID();
        long newOwner = insert("INSERT INTO `user` (account, username, password, email) VALUES (?, ?, ?, ?)",
                account, "M1C new owner", "test-hash", account + "@example.com");
        var locked = new CountDownLatch(1);
        var lockAttempted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            lockAttempted.countDown();
            return sqlSession.getMapper(InterviewSessionMapper.class).getReportVersionForUpdate(sessionId);
        }).when(sessionSpy).getReportVersionForUpdate(sessionId);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("SELECT id FROM interview_session WHERE id=? FOR UPDATE", Long.class, sessionId);
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test lock timed out");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
                assertThat(jdbc.update("UPDATE interview_session SET user_id=? WHERE id=?", newOwner, sessionId)).isEqualTo(1);
                return null;
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var denial = executor.submit(() -> catchThrowable(() -> service.generateReportForApi(userId, sessionId)));
                assertThat(lockAttempted.await(5, TimeUnit.SECONDS)).isTrue();
                release.countDown();
                holder.get(5, TimeUnit.SECONDS);
                assertThat(denial.get(5, TimeUnit.SECONDS)).isInstanceOf(ResourceNotFoundException.class);
                assertThat(tasks.listActiveBySessionId(sessionId)).isEmpty();
            } finally {
                release.countDown();
                holder.get(5, TimeUnit.SECONDS);
            }
        } finally {
            jdbc.update("UPDATE interview_session SET user_id=? WHERE id=?", userId, sessionId);
            jdbc.update("DELETE FROM `user` WHERE id=?", newOwner);
        }
    }
}
