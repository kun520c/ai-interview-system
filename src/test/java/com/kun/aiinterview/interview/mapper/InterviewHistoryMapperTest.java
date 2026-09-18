package com.kun.aiinterview.interview.mapper;

import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class InterviewHistoryMapperTest {

    @Autowired
    private InterviewSessionMapper interviewSessionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldIsolateCompletedSessionsByUserAndSortByEndedAtThenId() {
        long userId = insertUser();
        long otherUserId = insertUser();

        LocalDateTime earlier = LocalDateTime.of(2026, 9, 1, 10, 0, 0);
        LocalDateTime later = LocalDateTime.of(2026, 9, 2, 10, 0, 0);

        long olderCompletedId = insertSession(
                userId,
                "COMPLETED",
                earlier,
                new BigDecimal("70.00"),
                "READY"
        );
        long firstSameEndedAtId = insertSession(
                userId,
                "COMPLETED",
                later,
                new BigDecimal("80.00"),
                "READY"
        );
        long secondSameEndedAtId = insertSession(
                userId,
                "COMPLETED",
                later,
                new BigDecimal("90.00"),
                "READY"
        );
        insertSession(userId, "IN_PROGRESS", null, null, "NOT_STARTED");
        insertSession(
                otherUserId,
                "COMPLETED",
                later.plusDays(1),
                new BigDecimal("99.00"),
                "READY"
        );

        long total = interviewSessionMapper
                .countCompletedSessionsByUserId(userId);
        List<InterviewSession> firstPage =
                interviewSessionMapper.listCompletedSessionsByUserId(
                        userId,
                        2,
                        0
                );
        List<InterviewSession> secondPage =
                interviewSessionMapper.listCompletedSessionsByUserId(
                        userId,
                        2,
                        2
                );
        long otherUserTotal = interviewSessionMapper
                .countCompletedSessionsByUserId(otherUserId);

        assertEquals(3, total);
        assertEquals(1, otherUserTotal);
        assertEquals(2, firstPage.size());
        assertEquals(secondSameEndedAtId, firstPage.get(0).getId());
        assertEquals(firstSameEndedAtId, firstPage.get(1).getId());
        assertEquals(1, secondPage.size());
        assertEquals(olderCompletedId, secondPage.get(0).getId());
        assertEquals(userId, firstPage.get(0).getUserId());
        assertEquals(InterviewSessionStatus.COMPLETED, firstPage.get(0).getStatus());
        assertEquals(QuestionDifficulty.MEDIUM, firstPage.get(0).getDifficulty());
        assertEquals(InterviewReportStatus.READY, firstPage.get(0).getReportStatus());
        assertEquals(new BigDecimal("90.00"), firstPage.get(0).getTotalScore());
        assertTrue(firstPage.stream().noneMatch(
                session -> session.getStatus() != InterviewSessionStatus.COMPLETED
        ));
        assertTrue(firstPage.stream().noneMatch(
                session -> !session.getUserId().equals(userId)
        ));
        assertTrue(secondPage.stream().noneMatch(
                session -> !session.getUserId().equals(userId)
        ));
    }

    @Test
    void shouldReturnEmptyHistoryWhenUserHasNoCompletedSession() {
        long userId = insertUser();
        insertSession(userId, "IN_PROGRESS", null, null, "NOT_STARTED");

        long total = interviewSessionMapper
                .countCompletedSessionsByUserId(userId);
        List<InterviewSession> sessions =
                interviewSessionMapper.listCompletedSessionsByUserId(
                        userId,
                        10,
                        0
                );

        assertEquals(0, total);
        assertNotNull(sessions);
        assertTrue(sessions.isEmpty());
    }

    private long insertUser() {
        String account = "hist-" + uniqueValue();
        jdbcTemplate.update(
                """
                INSERT INTO `user` (account, username, password, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 'ENABLED')
                """,
                account,
                "History Mapper 测试用户",
                "test-password-hash",
                account + "@example.com"
        );
        return requiredId("SELECT id FROM `user` WHERE account = ?", account);
    }

    private long insertSession(
            long userId,
            String status,
            LocalDateTime endedAt,
            BigDecimal totalScore,
            String reportStatus
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO interview_session
                    (user_id, difficulty, status, planned_question_count,
                     completed_question_count, total_score, report_status,
                     started_at, ended_at)
                VALUES (?, 'MEDIUM', ?, 5, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                """,
                userId,
                status,
                "COMPLETED".equals(status) ? 5 : 0,
                totalScore,
                reportStatus,
                endedAt
        );
        return requiredId(
                """
                SELECT id FROM interview_session
                WHERE user_id = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                userId
        );
    }

    private long requiredId(String sql, Object... args) {
        Long id = jdbcTemplate.queryForObject(sql, Long.class, args);
        assertNotNull(id);
        return id;
    }

    private String uniqueValue() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
