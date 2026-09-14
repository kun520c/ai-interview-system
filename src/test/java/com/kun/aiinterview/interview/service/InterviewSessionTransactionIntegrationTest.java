package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.entity.InterviewSession;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.model.InterviewMainQuestionDraft;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionPointStatus;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class InterviewSessionTransactionIntegrationTest {

    @Autowired private InterviewSessionTransactionService transactionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String marker;
    private long userId;
    private List<InterviewMainQuestionDraft> drafts;

    @BeforeEach
    void setUp() {
        marker = "session-create-" + UUID.randomUUID().toString().replace("-", "");
        userId = insertUser();
        drafts = List.of(
                insertQuestionDraft(QuestionCategory.JAVA_BASIC, 1),
                insertQuestionDraft(QuestionCategory.SPRING, 2),
                insertQuestionDraft(QuestionCategory.MYSQL, 3)
        );
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("""
                DELETE iq FROM interview_question iq
                JOIN interview_session s ON s.id = iq.session_id
                WHERE s.user_id = ?
                """, userId);
        jdbcTemplate.update("DELETE FROM interview_session WHERE user_id = ?", userId);
        jdbcTemplate.update("""
                DELETE sp FROM question_scoring_point sp
                JOIN question q ON q.id = sp.question_id
                WHERE q.knowledge_point LIKE ?
                """, marker + "%");
        jdbcTemplate.update("DELETE FROM question WHERE knowledge_point LIKE ?", marker + "%");
        jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
    }

    @Test
    void shouldPersistCompletePlanAndStartFirstMain() {
        InterviewSession started = transactionService.createAndStartSession(
                newSession(3),
                drafts
        );

        assertThat(started.getStatus()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);
        assertThat(started.getVersion()).isEqualTo(1);
        assertThat(started.getCompletedQuestionCount()).isZero();
        assertThat(started.getPlannedQuestionCount()).isEqualTo(3);
        assertThat(started.getCurrentInterviewQuestionId()).isNotNull();
        assertThat(started.getStartedAt()).isNotNull();

        List<InterviewQuestion> rows = jdbcTemplate.query("""
                SELECT id, session_id, question_id, plan_order, display_order,
                       question_type, parent_question_id, follow_up_target_points,
                       status, question_content, reference_answer_snapshot,
                       scoring_points_snapshot
                FROM interview_question
                WHERE session_id = ?
                ORDER BY plan_order
                """, (rs, rowNum) -> InterviewQuestion.builder()
                .id(rs.getLong("id"))
                .sessionId(rs.getLong("session_id"))
                .questionId(rs.getLong("question_id"))
                .planOrder(rs.getInt("plan_order"))
                .displayOrder(rs.getInt("display_order"))
                .questionType(InterviewQuestionType.valueOf(rs.getString("question_type")))
                .parentQuestionId((Long) rs.getObject("parent_question_id"))
                .followUpTargetPoints(rs.getString("follow_up_target_points"))
                .status(InterviewQuestionStatus.valueOf(rs.getString("status")))
                .questionContent(rs.getString("question_content"))
                .referenceAnswerSnapshot(rs.getString("reference_answer_snapshot"))
                .scoringPointsSnapshot(rs.getString("scoring_points_snapshot"))
                .build(), started.getId());

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(InterviewQuestion::getPlanOrder)
                .containsExactly(1, 2, 3);
        assertThat(rows).extracting(InterviewQuestion::getDisplayOrder)
                .containsExactly(1, 3, 5);
        assertThat(rows).extracting(InterviewQuestion::getStatus)
                .containsExactly(
                        InterviewQuestionStatus.WAITING_ANSWER,
                        InterviewQuestionStatus.PENDING,
                        InterviewQuestionStatus.PENDING
                );
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getQuestionType()).isEqualTo(InterviewQuestionType.MAIN);
            assertThat(row.getParentQuestionId()).isNull();
            assertThat(row.getFollowUpTargetPoints()).isNull();
        });
        assertThat(rows.getFirst().getId())
                .isEqualTo(started.getCurrentInterviewQuestionId());
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getQuestionContent()).startsWith("question-");
            assertThat(row.getReferenceAnswerSnapshot()).startsWith("answer-");
            assertThat(row.getScoringPointsSnapshot())
                    .contains("\"id\"", "pointType", "content", "weight")
                    .doesNotContain(
                            "questionId",
                            "status",
                            "sortOrder",
                            "createdAt",
                            "updatedAt"
                    );
        });
    }

    @Test
    void shouldRollbackSessionAndMainQuestionsWhenSnapshotValidationFails() {
        Question question = drafts.getFirst().question();
        QuestionScoringPoint wrongPoint = QuestionScoringPoint.builder()
                .id(drafts.getFirst().scoringPoints().getFirst().getId())
                .questionId(Long.MAX_VALUE)
                .pointType(QuestionPointType.CORE)
                .content("wrong owner")
                .weight(100)
                .status(QuestionPointStatus.ENABLED)
                .build();

        assertThatThrownBy(() -> transactionService.createAndStartSession(
                newSession(1),
                List.of(new InterviewMainQuestionDraft(question, List.of(wrongPoint)))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong");

        Integer sessionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_session WHERE user_id = ?",
                Integer.class,
                userId
        );
        assertThat(sessionCount).isZero();

        Integer mainQuestionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM interview_question
                WHERE question_id IN (?, ?, ?)
                """,
                Integer.class,
                drafts.get(0).question().getId(),
                drafts.get(1).question().getId(),
                drafts.get(2).question().getId()
        );
        assertThat(mainQuestionCount).isZero();
    }

    @Test
    void concurrentRequestsShouldReturnOneActiveSession() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> createAfterBarrier(ready, start));
            var second = executor.submit(() -> createAfterBarrier(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            InterviewSession firstResult = first.get(10, TimeUnit.SECONDS);
            InterviewSession secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResult.getId()).isEqualTo(secondResult.getId());
        }

        Integer activeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM interview_session
                WHERE user_id = ? AND status IN ('CREATED', 'IN_PROGRESS')
                """, Integer.class, userId);
        assertThat(activeCount).isEqualTo(1);
    }

    private InterviewSession createAfterBarrier(
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("test barrier timed out");
        }
        return transactionService.createAndStartSession(newSession(3), drafts);
    }

    private InterviewSession newSession(int count) {
        return InterviewSession.builder()
                .userId(userId)
                .difficulty(QuestionDifficulty.EASY)
                .status(InterviewSessionStatus.CREATED)
                .plannedQuestionCount(count)
                .completedQuestionCount(0)
                .reportStatus(InterviewReportStatus.NOT_STARTED)
                .version(0)
                .build();
    }

    private long insertUser() {
        jdbcTemplate.update("""
                INSERT INTO `user` (account, username, password, email, role, status)
                VALUES (?, 'session-create-user', 'hash', ?, 'USER', 'ENABLED')
                """, marker, marker + "@example.com");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE account = ?",
                Long.class,
                marker
        );
    }

    private InterviewMainQuestionDraft insertQuestionDraft(
            QuestionCategory category,
            int number
    ) {
        String knowledgePoint = marker + "-" + number;
        jdbcTemplate.update("""
                INSERT INTO question
                    (category, knowledge_point, difficulty, question_content,
                     reference_answer, status)
                VALUES (?, ?, 'EASY', ?, ?, 'ENABLED')
                """, category.name(), knowledgePoint,
                "question-" + number, "answer-" + number);
        Long questionId = jdbcTemplate.queryForObject(
                "SELECT id FROM question WHERE knowledge_point = ?",
                Long.class,
                knowledgePoint
        );
        jdbcTemplate.update("""
                INSERT INTO question_scoring_point
                    (question_id, point_type, content, weight, sort_order, status)
                VALUES (?, 'CORE', ?, 100, 1, 'ENABLED')
                """, questionId, "point-" + number);
        Long pointId = jdbcTemplate.queryForObject("""
                SELECT id FROM question_scoring_point
                WHERE question_id = ? AND sort_order = 1
                """, Long.class, questionId);

        Question question = Question.builder()
                .id(questionId)
                .category(category)
                .knowledgePoint(knowledgePoint)
                .difficulty(QuestionDifficulty.EASY)
                .questionContent("question-" + number)
                .referenceAnswer("answer-" + number)
                .build();
        QuestionScoringPoint point = QuestionScoringPoint.builder()
                .id(pointId)
                .questionId(questionId)
                .pointType(QuestionPointType.CORE)
                .content("point-" + number)
                .weight(100)
                .sortOrder(1)
                .status(QuestionPointStatus.ENABLED)
                .build();
        return new InterviewMainQuestionDraft(question, List.of(point));
    }
}
