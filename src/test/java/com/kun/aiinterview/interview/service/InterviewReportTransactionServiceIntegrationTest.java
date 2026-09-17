package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.InterviewReport;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.mapper.InterviewReportMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class InterviewReportTransactionServiceIntegrationTest {

    private static final int SESSION_VERSION = 4;

    @Autowired
    private InterviewReportTransactionService transactionService;

    @Autowired
    private InterviewReportMapper interviewReportMapper;

    @Autowired
    private InterviewSessionMapper interviewSessionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");
        String account = "report-tx-" + unique;
        jdbcTemplate.update(
                """
                INSERT INTO `user`
                    (account, username, password, email, role, status)
                VALUES (?, ?, ?, ?, 'USER', 'ENABLED')
                """,
                account,
                "Report事务测试用户",
                "test-password-hash",
                account + "@example.com"
        );
        userId = requiredId(
                "SELECT id FROM `user` WHERE account = ?",
                account
        );

        jdbcTemplate.update(
                """
                INSERT INTO interview_session
                    (user_id, difficulty, status, planned_question_count,
                     completed_question_count, total_score, report_status,
                     version, started_at, ended_at)
                VALUES (?, 'MEDIUM', 'COMPLETED', 1, 1, NULL,
                        'GENERATING', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                SESSION_VERSION
        );
        sessionId = requiredId(
                "SELECT id FROM interview_session WHERE user_id = ?",
                userId
        );
    }

    @AfterEach
    void tearDown() {
        if (sessionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM interview_report WHERE session_id = ?",
                    sessionId
            );
            jdbcTemplate.update(
                    "DELETE FROM interview_session WHERE id = ?",
                    sessionId
            );
        }
        if (userId != null) {
            jdbcTemplate.update(
                    "DELETE FROM `user` WHERE id = ?",
                    userId
            );
        }
    }

    @Test
    void shouldInsertReportAndMarkSessionReadyAtomically() {
        InterviewSession session = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        InterviewReport report = report();

        InterviewReport completed =
                transactionService.completeReportGeneration(
                        report,
                        session
                );

        assertThat(completed.getId()).isNotNull();
        InterviewReport persisted = interviewReportMapper
                .getBySessionId(sessionId);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getSessionId()).isEqualTo(sessionId);
        assertThat(persisted.getOverallScore())
                .isEqualByComparingTo("80.33");
        assertThat(persisted.getWeaknesses())
                .isEqualTo("[\"并发知识不足\"]");
        assertThat(persisted.getPromptVersion())
                .isEqualTo("report-v1");
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();

        InterviewSession ready = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        assertThat(ready.getReportStatus())
                .isEqualTo(InterviewReportStatus.READY);
        assertThat(ready.getTotalScore())
                .isEqualByComparingTo("80.33");
        assertThat(ready.getVersion())
                .isEqualTo(SESSION_VERSION + 1);
    }

    @Test
    void shouldRollbackInsertedReportWhenMarkReadyFails() {
        InterviewSession staleSession = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        staleSession.setVersion(SESSION_VERSION - 1);

        assertThatThrownBy(
                () -> transactionService.completeReportGeneration(
                        report(),
                        staleSession
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Session报告状态更新为READY失败");

        Integer reportCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_report WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        assertThat(reportCount).isZero();

        InterviewSession unchanged = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        assertThat(unchanged.getReportStatus())
                .isEqualTo(InterviewReportStatus.GENERATING);
        assertThat(unchanged.getTotalScore()).isNull();
        assertThat(unchanged.getVersion())
                .isEqualTo(SESSION_VERSION);
    }

    private InterviewReport report() {
        return InterviewReport.builder()
                .sessionId(sessionId)
                .overallScore(new BigDecimal("80.33"))
                .result("PASS")
                .summary("总体表现良好")
                .strengths("[\"Java基础扎实\"]")
                .weaknesses("[\"并发知识不足\"]")
                .suggestions("[\"复习线程池\"]")
                .llmModel("report-test-model")
                .promptVersion("report-v1")
                .build();
    }

    private long requiredId(String sql, Object... arguments) {
        Long id = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                arguments
        );
        assertThat(id).isNotNull();
        return id;
    }
}
