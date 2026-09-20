package com.kun.aiinterview.interview.service;

import com.kun.aiinterview.interview.config.InterviewPlanConfig;
import com.kun.aiinterview.interview.model.InterviewMainQuestionDraft;
import com.kun.aiinterview.question.dto.ScoringPointRequest;
import com.kun.aiinterview.question.dto.UpdateQuestionRequest;
import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionPointType;
import com.kun.aiinterview.question.mapper.QuestionScoringPointMapper;
import com.kun.aiinterview.question.service.QuestionAdminService;
import com.kun.aiinterview.question.vo.AdminScoringPointDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest
@ActiveProfiles({"local", "test"})
@Import(InterviewQuestionPlanSnapshotIntegrationTest.PausingMapperConfiguration.class)
class InterviewQuestionPlanSnapshotIntegrationTest {

    private static final String OLD_QUESTION = "OLD Question";
    private static final String OLD_REFERENCE = "OLD Reference";
    private static final String NEW_QUESTION = "NEW Question";
    private static final String NEW_REFERENCE = "NEW Reference";

    @Autowired
    private InterviewQuestionPlanSnapshotService snapshotService;

    @Autowired
    private QuestionAdminService questionAdminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScoringPointReadBarrier scoringPointReadBarrier;

    private Long questionId;
    private QuestionCategory category;
    private QuestionDifficulty difficulty;
    private String knowledgePoint;

    @BeforeEach
    void setUp() {
        Map.Entry<QuestionCategory, QuestionDifficulty> combination =
                findUnusedEnabledCombination();
        category = combination.getKey();
        difficulty = combination.getValue();
        knowledgePoint = "snapshot-" + UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO question
                    (category, knowledge_point, difficulty, question_content,
                     reference_answer, status)
                VALUES (?, ?, ?, ?, ?, 'ENABLED')
                """, category.name(), knowledgePoint, difficulty.name(),
                OLD_QUESTION, OLD_REFERENCE);
        questionId = jdbcTemplate.queryForObject(
                "SELECT id FROM question WHERE knowledge_point = ?",
                Long.class,
                knowledgePoint
        );
        jdbcTemplate.update("""
                INSERT INTO question_scoring_point
                    (question_id, point_type, content, weight, sort_order, status)
                VALUES
                    (?, 'CORE', 'OLD Core', 60, 1, 'ENABLED'),
                    (?, 'KEY', 'OLD Key', 40, 2, 'ENABLED')
                """, questionId, questionId);
    }

    @AfterEach
    void tearDown() {
        if (questionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM question_scoring_point WHERE question_id = ?",
                    questionId
            );
            jdbcTemplate.update(
                    "DELETE FROM question WHERE id = ?",
                    questionId
            );
        }
    }

    @Test
    void shouldReadOldQuestionAndOldScoringPointsFromOneRepeatableReadView()
            throws Exception {
        CountDownLatch scoringPointReadEntered = new CountDownLatch(1);
        CountDownLatch resumeScoringPointRead = new CountDownLatch(1);
        scoringPointReadBarrier.arm(
                questionId,
                scoringPointReadEntered,
                resumeScoringPointRead
        );

        var rule = new InterviewPlanConfig.InterviewPlanRule(
                1,
                List.of(category)
        );

        try (var executor = Executors.newSingleThreadExecutor()) {
            var snapshotFuture = executor.submit(
                    () -> snapshotService.prepareSnapshot(difficulty, rule)
            );

            assertThat(scoringPointReadEntered.await(10, TimeUnit.SECONDS))
                    .as("candidate SELECT should finish before scoring-point SELECT")
                    .isTrue();

            questionAdminService.updateQuestion(
                    questionId,
                    updatedQuestionRequest()
            );

            assertThat(loadCurrentQuestionValues()).containsEntry(
                    "question_content",
                    NEW_QUESTION
            ).containsEntry(
                    "reference_answer",
                    NEW_REFERENCE
            );

            resumeScoringPointRead.countDown();
            List<InterviewMainQuestionDraft> drafts =
                    snapshotFuture.get(10, TimeUnit.SECONDS);

            assertThat(drafts).singleElement().satisfies(draft -> {
                assertThat(draft.question().getId()).isEqualTo(questionId);
                assertThat(draft.question().getCategory()).isEqualTo(category);
                assertThat(draft.question().getKnowledgePoint())
                        .isEqualTo(knowledgePoint);
                assertThat(draft.question().getDifficulty())
                        .isEqualTo(difficulty);
                assertThat(draft.question().getQuestionContent())
                        .isEqualTo(OLD_QUESTION);
                assertThat(draft.question().getReferenceAnswer())
                        .isEqualTo(OLD_REFERENCE);
                assertThat(draft.scoringPoints())
                        .extracting(
                                point -> point.getContent(),
                                point -> point.getPointType(),
                                point -> point.getWeight(),
                                point -> point.getSortOrder()
                        )
                        .containsExactly(
                                org.assertj.core.groups.Tuple.tuple(
                                        "OLD Core",
                                        QuestionPointType.CORE,
                                        60,
                                        1
                                ),
                                org.assertj.core.groups.Tuple.tuple(
                                        "OLD Key",
                                        QuestionPointType.KEY,
                                        40,
                                        2
                                )
                        );
            });
        } finally {
            resumeScoringPointRead.countDown();
        }
    }

    private UpdateQuestionRequest updatedQuestionRequest() {
        return UpdateQuestionRequest.builder()
                .category(category)
                .knowledgePoint(knowledgePoint)
                .difficulty(difficulty)
                .questionContent(NEW_QUESTION)
                .referenceAnswer(NEW_REFERENCE)
                .scoringPoints(List.of(
                        new ScoringPointRequest(
                                QuestionPointType.CORE,
                                "NEW Core",
                                70
                        ),
                        new ScoringPointRequest(
                                QuestionPointType.ADVANCED,
                                "NEW Advanced",
                                30
                        )
                ))
                .build();
    }

    private Map<String, Object> loadCurrentQuestionValues() {
        return jdbcTemplate.queryForMap("""
                SELECT question_content, reference_answer
                FROM question
                WHERE id = ?
                """, questionId);
    }

    private Map.Entry<QuestionCategory, QuestionDifficulty>
    findUnusedEnabledCombination() {
        for (QuestionDifficulty candidateDifficulty
                : QuestionDifficulty.values()) {
            for (QuestionCategory candidateCategory
                    : QuestionCategory.values()) {
                Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM question
                        WHERE category = ?
                          AND difficulty = ?
                          AND status = 'ENABLED'
                        """, Integer.class,
                        candidateCategory.name(),
                        candidateDifficulty.name());
                if (count != null && count == 0) {
                    return Map.entry(
                            candidateCategory,
                            candidateDifficulty
                    );
                }
            }
        }

        throw new IllegalStateException(
                "no unused enabled category/difficulty combination"
        );
    }

    @TestConfiguration
    static class PausingMapperConfiguration {

        @Bean
        ScoringPointReadBarrier scoringPointReadBarrier() {
            return new ScoringPointReadBarrier();
        }

        @Bean
        @Primary
        QuestionScoringPointMapper pausingQuestionScoringPointMapper(
                @Qualifier("questionScoringPointMapper")
                QuestionScoringPointMapper delegate,
                ScoringPointReadBarrier barrier
        ) {
            return new QuestionScoringPointMapper() {
                @Override
                public int batchInsert(
                        List<QuestionScoringPoint> scoringPoints
                ) {
                    return delegate.batchInsert(scoringPoints);
                }

                @Override
                public int deleteByQuestionId(Long questionId) {
                    return delegate.deleteByQuestionId(questionId);
                }

                @Override
                public List<AdminScoringPointDetail>
                selectDetailByQuestionId(Long questionId) {
                    return delegate.selectDetailByQuestionId(questionId);
                }

                @Override
                public List<QuestionScoringPoint>
                selectEnabledByQuestionId(Long questionId) {
                    barrier.awaitIfArmed(questionId);
                    return delegate.selectEnabledByQuestionId(questionId);
                }
            };
        }
    }

    static class ScoringPointReadBarrier {

        private volatile Long questionId;
        private volatile CountDownLatch entered;
        private volatile CountDownLatch resume;

        void arm(
                Long targetQuestionId,
                CountDownLatch enteredLatch,
                CountDownLatch resumeLatch
        ) {
            questionId = targetQuestionId;
            entered = enteredLatch;
            resume = resumeLatch;
        }

        void awaitIfArmed(Long requestedQuestionId) {
            if (!requestedQuestionId.equals(questionId)) {
                return;
            }

            entered.countDown();
            try {
                if (!resume.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "scoring-point read resume timed out"
                    );
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "scoring-point read interrupted",
                        exception
                );
            }
        }
    }
}
