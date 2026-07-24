package com.kun.aiinterview.question.mapper;

import com.kun.aiinterview.question.dto.QuestionPageQuery;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionStatus;
import com.kun.aiinterview.question.vo.AdminQuestionListItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class QuestionPaginationMapperTest {

    private static final LocalDateTime FUTURE_TIME =
            LocalDateTime.of(2099, 1, 1, 0, 0);

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCountAllQuestionsAndApplyLimitAndOffsetWithoutDuplicates() {
        long originalTotal = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question",
                Long.class
        );
        String marker = marker();
        insertQuestion(
                marker + "-1",
                QuestionCategory.JAVA_BASIC,
                QuestionDifficulty.EASY,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );
        insertQuestion(
                marker + "-2",
                QuestionCategory.JVM,
                QuestionDifficulty.MEDIUM,
                QuestionStatus.DISABLED,
                FUTURE_TIME
        );
        insertQuestion(
                marker + "-3",
                QuestionCategory.MYSQL,
                QuestionDifficulty.HARD,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );
        insertQuestion(
                marker + "-4",
                QuestionCategory.SPRING,
                QuestionDifficulty.MEDIUM,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );
        QuestionPageQuery query = new QuestionPageQuery();

        long total = questionMapper.countQuestion(query);
        List<AdminQuestionListItem> firstPage =
                questionMapper.selectQuestionPage(query, 0L, 2);
        List<AdminQuestionListItem> secondPage =
                questionMapper.selectQuestionPage(query, 2L, 2);

        Set<Long> firstPageIds = ids(firstPage);
        Set<Long> secondPageIds = ids(secondPage);
        assertAll(
                () -> assertEquals(originalTotal + 4, total),
                () -> assertEquals(2, firstPage.size()),
                () -> assertEquals(2, secondPage.size()),
                () -> assertTrue(
                        firstPageIds.stream().noneMatch(secondPageIds::contains)
                )
        );
    }

    @Test
    void shouldUseSameCategoryFilterForCountAndPageSelection() {
        long originalCount = countByColumn(
                "category",
                QuestionCategory.JVM.name()
        );
        String marker = marker();
        Long expectedId = insertQuestion(
                marker,
                QuestionCategory.JVM,
                QuestionDifficulty.MEDIUM,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );
        insertQuestion(
                marker + "-other",
                QuestionCategory.REDIS,
                QuestionDifficulty.MEDIUM,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );
        QuestionPageQuery query = QuestionPageQuery.builder()
                .category(QuestionCategory.JVM)
                .build();

        long total = questionMapper.countQuestion(query);
        List<AdminQuestionListItem> records =
                questionMapper.selectQuestionPage(query, 0L, 50);

        assertAll(
                () -> assertEquals(originalCount + 1, total),
                () -> assertTrue(records.stream().allMatch(
                        item -> item.getCategory() == QuestionCategory.JVM
                )),
                () -> assertTrue(ids(records).contains(expectedId))
        );
    }

    @Test
    void shouldFilterByDifficulty() {
        long originalCount = countByColumn(
                "difficulty",
                QuestionDifficulty.HARD.name()
        );
        String marker = marker();
        Long expectedId = insertQuestion(
                marker,
                QuestionCategory.SPRING,
                QuestionDifficulty.HARD,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );
        insertQuestion(
                marker + "-other",
                QuestionCategory.SPRING,
                QuestionDifficulty.EASY,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );
        QuestionPageQuery query = QuestionPageQuery.builder()
                .difficulty(QuestionDifficulty.HARD)
                .build();

        long total = questionMapper.countQuestion(query);
        List<AdminQuestionListItem> records =
                questionMapper.selectQuestionPage(query, 0L, 50);

        assertAll(
                () -> assertEquals(originalCount + 1, total),
                () -> assertTrue(records.stream().allMatch(
                        item -> item.getDifficulty() == QuestionDifficulty.HARD
                )),
                () -> assertTrue(ids(records).contains(expectedId))
        );
    }

    @Test
    void shouldFilterByStatus() {
        long originalCount = countByColumn(
                "status",
                QuestionStatus.DISABLED.name()
        );
        String marker = marker();
        Long expectedId = insertQuestion(
                marker,
                QuestionCategory.NETWORK,
                QuestionDifficulty.MEDIUM,
                QuestionStatus.DISABLED,
                FUTURE_TIME
        );
        insertQuestion(
                marker + "-other",
                QuestionCategory.NETWORK,
                QuestionDifficulty.MEDIUM,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );
        QuestionPageQuery query = QuestionPageQuery.builder()
                .status(QuestionStatus.DISABLED)
                .build();

        long total = questionMapper.countQuestion(query);
        List<AdminQuestionListItem> records =
                questionMapper.selectQuestionPage(query, 0L, 50);

        assertAll(
                () -> assertEquals(originalCount + 1, total),
                () -> assertTrue(records.stream().allMatch(
                        item -> item.getStatus() == QuestionStatus.DISABLED
                )),
                () -> assertTrue(ids(records).contains(expectedId))
        );
    }

    @Test
    void shouldMatchKeywordAgainstKnowledgePoint() {
        String marker = marker();
        Long expectedId = insertQuestion(
                marker + "-knowledge",
                "ordinary question content",
                QuestionCategory.JAVA_COLLECTION,
                QuestionDifficulty.MEDIUM,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );
        QuestionPageQuery query = QuestionPageQuery.builder()
                .keyword(marker + "-knowledge")
                .build();

        long total = questionMapper.countQuestion(query);
        List<AdminQuestionListItem> records =
                questionMapper.selectQuestionPage(query, 0L, 10);

        assertAll(
                () -> assertEquals(1, total),
                () -> assertEquals(1, records.size()),
                () -> assertEquals(expectedId, records.getFirst().getId())
        );
    }

    @Test
    void shouldMatchKeywordAgainstQuestionContent() {
        String marker = marker();
        Long expectedId = insertQuestion(
                "ordinary knowledge point",
                marker + "-content",
                QuestionCategory.JAVA_CONCURRENCY,
                QuestionDifficulty.HARD,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );
        QuestionPageQuery query = QuestionPageQuery.builder()
                .keyword(marker + "-content")
                .build();

        long total = questionMapper.countQuestion(query);
        List<AdminQuestionListItem> records =
                questionMapper.selectQuestionPage(query, 0L, 10);

        assertAll(
                () -> assertEquals(1, total),
                () -> assertEquals(1, records.size()),
                () -> assertEquals(expectedId, records.getFirst().getId())
        );
    }

    @Test
    void shouldRequireEveryCombinedFilterToMatch() {
        String marker = marker();
        Long expectedId = insertQuestion(
                marker + "-target",
                "combined " + marker,
                QuestionCategory.MYSQL,
                QuestionDifficulty.HARD,
                QuestionStatus.DISABLED,
                FUTURE_TIME
        );
        insertQuestion(
                marker + "-wrong-status",
                "combined " + marker,
                QuestionCategory.MYSQL,
                QuestionDifficulty.HARD,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );
        insertQuestion(
                marker + "-wrong-category",
                "combined " + marker,
                QuestionCategory.REDIS,
                QuestionDifficulty.HARD,
                QuestionStatus.DISABLED,
                FUTURE_TIME
        );
        QuestionPageQuery query = QuestionPageQuery.builder()
                .category(QuestionCategory.MYSQL)
                .difficulty(QuestionDifficulty.HARD)
                .status(QuestionStatus.DISABLED)
                .keyword(marker)
                .build();

        long total = questionMapper.countQuestion(query);
        List<AdminQuestionListItem> records =
                questionMapper.selectQuestionPage(query, 0L, 10);

        assertAll(
                () -> assertEquals(1, total),
                () -> assertEquals(1, records.size()),
                () -> assertEquals(expectedId, records.getFirst().getId())
        );
    }

    @Test
    void shouldReturnEmptyNonNullListWhenNoQuestionMatches() {
        QuestionPageQuery query = QuestionPageQuery.builder()
                .keyword("missing-" + UUID.randomUUID())
                .build();

        long total = questionMapper.countQuestion(query);
        List<AdminQuestionListItem> records =
                questionMapper.selectQuestionPage(query, 0L, 10);

        assertAll(
                () -> assertEquals(0, total),
                () -> assertNotNull(records),
                () -> assertTrue(records.isEmpty())
        );
    }

    @Test
    void shouldOrderByUpdatedAtDescendingThenIdDescending() {
        String marker = marker();
        Long olderId = insertQuestion(
                marker + "-older",
                QuestionCategory.JAVA_BASIC,
                QuestionDifficulty.EASY,
                QuestionStatus.ENABLED,
                FUTURE_TIME.minusDays(1)
        );
        Long sameTimeLowerId = insertQuestion(
                marker + "-same-lower",
                QuestionCategory.JAVA_BASIC,
                QuestionDifficulty.EASY,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );
        Long sameTimeHigherId = insertQuestion(
                marker + "-same-higher",
                QuestionCategory.JAVA_BASIC,
                QuestionDifficulty.EASY,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );
        QuestionPageQuery query = QuestionPageQuery.builder()
                .keyword(marker)
                .build();

        List<AdminQuestionListItem> records =
                questionMapper.selectQuestionPage(query, 0L, 10);

        assertEquals(
                List.of(sameTimeHigherId, sameTimeLowerId, olderId),
                records.stream().map(AdminQuestionListItem::getId).toList()
        );
    }

    @Test
    void shouldMapOnlyQuestionListFields() {
        String marker = marker();
        Long expectedId = insertQuestion(
                marker + "-knowledge",
                marker + "-content",
                QuestionCategory.PROJECT_SCENARIO,
                QuestionDifficulty.MEDIUM,
                QuestionStatus.DISABLED,
                FUTURE_TIME
        );
        QuestionPageQuery query = QuestionPageQuery.builder()
                .keyword(marker)
                .build();

        List<AdminQuestionListItem> records =
                questionMapper.selectQuestionPage(query, 0L, 10);

        assertFalse(records.isEmpty());
        AdminQuestionListItem item = records.getFirst();
        assertAll(
                () -> assertEquals(expectedId, item.getId()),
                () -> assertEquals(
                        QuestionCategory.PROJECT_SCENARIO,
                        item.getCategory()
                ),
                () -> assertEquals(marker + "-knowledge", item.getKnowledgePoint()),
                () -> assertEquals(
                        QuestionDifficulty.MEDIUM,
                        item.getDifficulty()
                ),
                () -> assertEquals(marker + "-content", item.getQuestionContent()),
                () -> assertEquals(QuestionStatus.DISABLED, item.getStatus()),
                () -> assertNotNull(item.getUpdatedAt())
        );
    }

    private Long insertQuestion(
            String marker,
            QuestionCategory category,
            QuestionDifficulty difficulty,
            QuestionStatus status,
            LocalDateTime updatedAt
    ) {
        return insertQuestion(
                marker,
                marker + "-question-content",
                category,
                difficulty,
                status,
                updatedAt
        );
    }

    private Long insertQuestion(
            String knowledgePoint,
            String questionContent,
            QuestionCategory category,
            QuestionDifficulty difficulty,
            QuestionStatus status,
            LocalDateTime updatedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO question
                (category, knowledge_point, difficulty, question_content,
                 reference_answer, status, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                category.name(),
                knowledgePoint,
                difficulty.name(),
                questionContent,
                "pagination test reference answer",
                status.name(),
                updatedAt
        );
        Long id = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM question
                WHERE knowledge_point = ? AND question_content = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                Long.class,
                knowledgePoint,
                questionContent
        );
        assertNotNull(id);
        return id;
    }

    private long countByColumn(String column, String value) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question WHERE " + column + " = ?",
                Long.class,
                value
        );
    }

    private Set<Long> ids(List<AdminQuestionListItem> records) {
        return new HashSet<>(
                records.stream().map(AdminQuestionListItem::getId).toList()
        );
    }

    private String marker() {
        return "page-" + UUID.randomUUID().toString().replace("-", "");
    }
}
