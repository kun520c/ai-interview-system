package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.entity.InterviewReport;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.mapper.InterviewReportMapper;
import com.kun.aiinterview.interview.mapper.InterviewSessionMapper;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.user.entity.UserWeakness;
import com.kun.aiinterview.user.enums.UserWeaknessStatus;
import com.kun.aiinterview.user.mapper.UserWeaknessMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class InterviewReportTransactionServiceIntegrationTest {

    private static final int SESSION_VERSION = 4;
    private static final String KNOWLEDGE_POINT = "HashMap";

    @Autowired
    private InterviewReportTransactionService transactionService;

    @Autowired
    private InterviewReportMapper interviewReportMapper;

    @Autowired
    private InterviewSessionMapper interviewSessionMapper;

    @Autowired
    private UserWeaknessMapper userWeaknessMapper;

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
        if (userId != null) {
            jdbcTemplate.update(
                    "DELETE FROM user_weakness WHERE user_id = ?",
                    userId
            );
        }
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
    void shouldInsertReportWeaknessAndMarkSessionReadyAtomically() {
        InterviewSession session = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        InterviewReport report = report();

        InterviewReport completed =
                transactionService.completeReportGeneration(
                        report,
                        session,
                        List.of(evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                KNOWLEDGE_POINT,
                                60
                        ))
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

        UserWeakness weakness = userWeaknessMapper
                .getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                );
        assertThat(weakness).isNotNull();
        assertThat(weakness.getStatus())
                .isEqualTo(UserWeaknessStatus.ACTIVE);
        assertThat(weakness.getDiscoveredCount()).isEqualTo(1);
        assertThat(weakness.getWeaknessScore())
                .isEqualByComparingTo("40.00");
        assertThat(weakness.getResolvedAt()).isNull();

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
    void shouldRollbackReportAndWeaknessWhenMarkReadyFails() {
        userWeaknessMapper.upsertDiscoveredWeakness(
                userId,
                QuestionCategory.JAVA_COLLECTION,
                KNOWLEDGE_POINT,
                new BigDecimal("40.00")
        );
        UserWeakness original = userWeaknessMapper
                .getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                );
        assertThat(original.getDiscoveredCount()).isEqualTo(1);

        InterviewSession staleSession = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        staleSession.setVersion(SESSION_VERSION - 1);

        assertThatThrownBy(
                () -> transactionService.completeReportGeneration(
                        report(),
                        staleSession,
                        List.of(evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                KNOWLEDGE_POINT,
                                50
                        ))
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

        UserWeakness unchanged = userWeaknessMapper
                .getByUserCategoryAndKnowledgePoint(
                        userId,
                        QuestionCategory.JAVA_COLLECTION,
                        KNOWLEDGE_POINT
                );
        assertThat(unchanged.getId()).isEqualTo(original.getId());
        assertThat(unchanged.getDiscoveredCount()).isEqualTo(1);
        assertThat(unchanged.getWeaknessScore())
                .isEqualByComparingTo("40.00");
        assertThat(unchanged.getStatus())
                .isEqualTo(UserWeaknessStatus.ACTIVE);
        assertThat(unchanged.getLastDiscoveredAt())
                .isEqualTo(original.getLastDiscoveredAt());

        InterviewSession session = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        assertThat(session.getReportStatus())
                .isEqualTo(InterviewReportStatus.GENERATING);
        assertThat(session.getTotalScore()).isNull();
        assertThat(session.getVersion())
                .isEqualTo(SESSION_VERSION);
    }

    @Test
    void shouldRollbackReportWhenWeaknessSqlFails() {
        InterviewSession session = interviewSessionMapper
                .getInterviewSessionById(sessionId);
        String overlongKnowledgePoint = "x".repeat(101);

        assertThatThrownBy(
                () -> transactionService.completeReportGeneration(
                        report(),
                        session,
                        List.of(evaluation(
                                QuestionCategory.JAVA_COLLECTION,
                                overlongKnowledgePoint,
                                60
                        ))
                )
        ).isInstanceOf(RuntimeException.class);

        Integer reportCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_report WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        assertThat(reportCount).isZero();

        Integer weaknessCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_weakness WHERE user_id = ?",
                Integer.class,
                userId
        );
        assertThat(weaknessCount).isZero();

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

    private AnswerEvaluation evaluation(
            QuestionCategory category,
            String knowledgePoint,
            int totalScore
    ) {
        return AnswerEvaluation.builder()
                .category(category)
                .knowledgePoint(knowledgePoint)
                .totalScore(totalScore)
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
