package com.kun.aiinterview.user.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.user.dto.ChangePasswordRequest;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Import(ConcurrentPasswordChangeIntegrationTest.PausingUserMapperConfiguration.class)
class ConcurrentPasswordChangeIntegrationTest {

    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD_A = "NewPasswordAAA!";
    private static final String NEW_PASSWORD_B = "NewPasswordBBB!";
    private static final long LOCK_WAIT_POLL_NANOS =
            TimeUnit.MILLISECONDS.toNanos(25);
    private static final long LOCK_WAIT_DEADLINE_NANOS =
            TimeUnit.SECONDS.toNanos(10);

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordChangeLockBarrier lockBarrier;

    private Long userId;
    private String account;

    @BeforeEach
    void setUp() {
        account = "pwdlock_" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        jdbcTemplate.update("""
                INSERT INTO `user`
                    (account, username, password, email, role, status)
                VALUES (?, 'password-lock-user', ?, ?, 'USER', 'ENABLED')
                """, account, passwordEncoder.encode(OLD_PASSWORD),
                account + "@example.com");
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE account = ?",
                Long.class,
                account
        );
    }

    @AfterEach
    void tearDown() {
        lockBarrier.disarm();
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
    }

    @Test
    void concurrentOldPasswordChangesShouldAllowExactlyOneWinner()
            throws Exception {
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch resumeAfterLock = new CountDownLatch(1);
        lockBarrier.arm(userId, lockAcquired, resumeAfterLock);
        String originalHash = jdbcTemplate.queryForObject(
                "SELECT password FROM `user` WHERE id = ?",
                String.class,
                userId
        );

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(
                    () -> changePassword(NEW_PASSWORD_A)
            );

            assertThat(lockAcquired.await(15, TimeUnit.SECONDS))
                    .as("first request must obtain MySQL FOR UPDATE before verify")
                    .isTrue();

            Future<Boolean> second = executor.submit(
                    () -> changePassword(NEW_PASSWORD_B)
            );

            awaitMysqlRowLockWait(userId);
            resumeAfterLock.countDown();

            boolean firstSucceeded = awaitPasswordChange(first);
            User afterWinner = userMapper.getUserById(userId);
            LocalDateTime winnerChangedAt = afterWinner.getPasswordChangedAt();
            String winnerHash = afterWinner.getPassword();

            boolean secondSucceeded = awaitPasswordChange(second);
            User afterLoser = userMapper.getUserById(userId);

            assertThat(firstSucceeded).isTrue();
            assertThat(secondSucceeded).isFalse();
            assertThat(winnerHash).isNotEqualTo(originalHash);
            assertThat(afterLoser.getPassword()).isEqualTo(winnerHash);
            assertThat(afterLoser.getPasswordChangedAt())
                    .isEqualTo(winnerChangedAt);
        }

        User stored = userMapper.getUserById(userId);
        assertThat(passwordEncoder.matches(NEW_PASSWORD_A, stored.getPassword()))
                .isTrue();
        assertThat(passwordEncoder.matches(NEW_PASSWORD_B, stored.getPassword()))
                .isFalse();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, stored.getPassword()))
                .isFalse();
        assertThat(stored.getPasswordChangedAt()).isNotNull();
    }

    private boolean changePassword(String newPassword) {
        userService.changePassword(
                userId,
                new ChangePasswordRequest(OLD_PASSWORD, newPassword)
        );
        return true;
    }

    private boolean awaitPasswordChange(Future<Boolean> future)
            throws InterruptedException {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException(
                    "concurrent password change timed out",
                    exception
            );
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            assertThat(cause)
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("请输入正确的现存密码");
            return false;
        }
    }

    private void awaitMysqlRowLockWait(Long lockedUserId)
            throws InterruptedException {
        long deadline = System.nanoTime() + LOCK_WAIT_DEADLINE_NANOS;
        String lastObservation = "no lock-wait metadata yet";
        while (System.nanoTime() < deadline) {
            lastObservation = observeUserRowLockWait(lockedUserId);
            if (lastObservation.startsWith("WAITING")) {
                return;
            }
            Thread.sleep(TimeUnit.NANOSECONDS.toMillis(LOCK_WAIT_POLL_NANOS));
        }
        throw new IllegalStateException(
                "MySQL did not report a row-lock wait on user.id="
                        + lockedUserId
                        + " before releasing the holder. Last observation: "
                        + lastObservation
        );
    }

    private String observeUserRowLockWait(Long lockedUserId) {
        try {
            List<Map<String, Object>> waits = jdbcTemplate.queryForList(
                    """
                    SELECT
                        requesting.THREAD_ID AS requesting_thread,
                        requesting.LOCK_STATUS AS requesting_status,
                        requesting.LOCK_TYPE AS requesting_type,
                        requesting.LOCK_MODE AS requesting_mode,
                        requesting.LOCK_DATA AS requesting_lock_data,
                        blocking.THREAD_ID AS blocking_thread,
                        blocking.LOCK_STATUS AS blocking_status
                    FROM performance_schema.data_lock_waits waits
                    INNER JOIN performance_schema.data_locks requesting
                        ON requesting.ENGINE_LOCK_ID
                            = waits.REQUESTING_ENGINE_LOCK_ID
                        AND requesting.ENGINE = waits.REQUESTING_ENGINE
                    INNER JOIN performance_schema.data_locks blocking
                        ON blocking.ENGINE_LOCK_ID
                            = waits.BLOCKING_ENGINE_LOCK_ID
                        AND blocking.ENGINE = waits.BLOCKING_ENGINE
                    WHERE requesting.OBJECT_SCHEMA = DATABASE()
                      AND requesting.OBJECT_NAME = 'user'
                      AND requesting.LOCK_STATUS = 'WAITING'
                      AND blocking.LOCK_STATUS = 'GRANTED'
                      AND (
                            requesting.LOCK_DATA = CAST(? AS CHAR)
                         OR requesting.LOCK_DATA = CONCAT('''', ?, '''')
                         OR INSTR(
                                CAST(requesting.LOCK_DATA AS CHAR),
                                CAST(? AS CHAR)
                            ) > 0
                      )
                    """,
                    lockedUserId,
                    lockedUserId,
                    lockedUserId
            );
            if (!waits.isEmpty()) {
                Map<String, Object> wait = waits.getFirst();
                return "WAITING requestingThread="
                        + wait.get("requesting_thread")
                        + " blockingThread="
                        + wait.get("blocking_thread")
                        + " lockData="
                        + wait.get("requesting_lock_data")
                        + " lockType="
                        + wait.get("requesting_type")
                        + " lockMode="
                        + wait.get("requesting_mode");
            }
            return "performance_schema.data_lock_waits had no matching WAITING row";
        } catch (DataAccessException exception) {
            return observeInnoDbLockWaitFallback(exception);
        }
    }

    private String observeInnoDbLockWaitFallback(
            DataAccessException primaryException
    ) {
        try {
            Integer waitingTransactions = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.innodb_trx
                    WHERE trx_state = 'LOCK WAIT'
                      AND (
                            trx_query LIKE '%FOR UPDATE%'
                         OR trx_query LIKE '%`user`%'
                      )
                    """,
                    Integer.class
            );
            if (waitingTransactions != null && waitingTransactions > 0) {
                return "WAITING innodb_trx lock-wait count="
                        + waitingTransactions;
            }
            return "information_schema.innodb_trx had no FOR UPDATE lock wait; "
                    + "performance_schema error="
                    + primaryException.getMostSpecificCause().getMessage();
        } catch (DataAccessException fallbackException) {
            throw new IllegalStateException(
                    "Cannot observe MySQL row-lock waits. "
                            + "performance_schema.data_lock_waits and "
                            + "information_schema.innodb_trx are both unreadable.",
                    fallbackException
            );
        }
    }

    @TestConfiguration
    static class PausingUserMapperConfiguration {

        @Bean
        PasswordChangeLockBarrier passwordChangeLockBarrier() {
            return new PasswordChangeLockBarrier();
        }

        @Bean
        @Primary
        UserMapper pausingUserMapper(
                @Qualifier("userMapper") UserMapper delegate,
                PasswordChangeLockBarrier barrier
        ) {
            return new UserMapper() {
                @Override
                public User getUserById(Long id) {
                    return delegate.getUserById(id);
                }

                @Override
                public User getUserByIdForUpdate(Long id) {
                    User user = delegate.getUserByIdForUpdate(id);
                    barrier.afterForUpdate(id);
                    return user;
                }

                @Override
                public User getUserByAccount(String account) {
                    return delegate.getUserByAccount(account);
                }

                @Override
                public User getUserByEmail(String email) {
                    return delegate.getUserByEmail(email);
                }

                @Override
                public int insertUser(User user) {
                    return delegate.insertUser(user);
                }

                @Override
                public int updatePassword(
                        Long id,
                        String password,
                        LocalDateTime passwordChangedAt
                ) {
                    return delegate.updatePassword(
                            id,
                            password,
                            passwordChangedAt
                    );
                }
            };
        }
    }

    static class PasswordChangeLockBarrier {

        private volatile Long userId;
        private volatile CountDownLatch lockAcquired;
        private volatile CountDownLatch resumeAfterLock;
        private final AtomicInteger forUpdateCalls = new AtomicInteger();

        void arm(
                Long targetUserId,
                CountDownLatch lockAcquiredLatch,
                CountDownLatch resumeAfterLockLatch
        ) {
            userId = targetUserId;
            lockAcquired = lockAcquiredLatch;
            resumeAfterLock = resumeAfterLockLatch;
            forUpdateCalls.set(0);
        }

        void disarm() {
            userId = null;
            CountDownLatch resume = resumeAfterLock;
            if (resume != null) {
                resume.countDown();
            }
        }

        void afterForUpdate(Long requestedUserId) {
            if (!armedFor(requestedUserId)) {
                return;
            }

            if (forUpdateCalls.incrementAndGet() != 1) {
                return;
            }

            lockAcquired.countDown();
            try {
                if (!resumeAfterLock.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "password-change lock resume timed out"
                    );
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "password-change lock wait interrupted",
                        exception
                );
            }
        }

        private boolean armedFor(Long requestedUserId) {
            return userId != null && userId.equals(requestedUserId);
        }
    }
}
