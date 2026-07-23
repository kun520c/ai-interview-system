package com.kun.aiinterview.question.mapper;

import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionPointStatus;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class QuestionScoringPointMapperTest {

    @Autowired
    private QuestionScoringPointMapper questionScoringPointMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldBatchInsertScoringPointsInRequestOrder() {
        Long questionId = insertParentQuestion();
        List<QuestionScoringPoint> scoringPoints = List.of(
                scoringPoint(
                        questionId,
                        QuestionPointType.CORE,
                        "说明数组和桶结构",
                        60,
                        1
                ),
                scoringPoint(
                        questionId,
                        QuestionPointType.KEY,
                        "说明哈希冲突处理",
                        40,
                        2
                )
        );

        int affectedRows = questionScoringPointMapper.batchInsert(
                scoringPoints
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT question_id, point_type, content, weight,
                       sort_order, status
                FROM question_scoring_point
                WHERE question_id = ?
                ORDER BY sort_order
                """,
                questionId
        );
        assertAll(
                () -> assertEquals(2, affectedRows),
                () -> assertEquals(2, rows.size()),
                () -> assertEquals(questionId, asLong(rows.get(0).get("question_id"))),
                () -> assertEquals("CORE", rows.get(0).get("point_type")),
                () -> assertEquals("说明数组和桶结构", rows.get(0).get("content")),
                () -> assertEquals(60, asInt(rows.get(0).get("weight"))),
                () -> assertEquals(1, asInt(rows.get(0).get("sort_order"))),
                () -> assertEquals("ENABLED", rows.get(0).get("status")),
                () -> assertEquals(questionId, asLong(rows.get(1).get("question_id"))),
                () -> assertEquals("KEY", rows.get(1).get("point_type")),
                () -> assertEquals(2, asInt(rows.get(1).get("sort_order")))
        );
    }

    @Test
    void shouldDeleteOnlyTargetQuestionPointsAndInsertReplacementPoints() {
        Long targetQuestionId = insertParentQuestion();
        Long otherQuestionId = insertParentQuestion();
        questionScoringPointMapper.batchInsert(List.of(
                scoringPoint(
                        targetQuestionId,
                        QuestionPointType.CORE,
                        "目标旧评分点一",
                        60,
                        1
                ),
                scoringPoint(
                        targetQuestionId,
                        QuestionPointType.KEY,
                        "目标旧评分点二",
                        40,
                        2
                ),
                scoringPoint(
                        otherQuestionId,
                        QuestionPointType.PRINCIPLE,
                        "其他题目评分点",
                        100,
                        1
                )
        ));

        int deletedRows =
                questionScoringPointMapper.deleteByQuestionId(targetQuestionId);
        List<QuestionScoringPoint> replacements = List.of(
                scoringPoint(
                        targetQuestionId,
                        QuestionPointType.INTERNAL,
                        "新评分点一",
                        70,
                        1
                ),
                scoringPoint(
                        targetQuestionId,
                        QuestionPointType.ADVANCED,
                        "新评分点二",
                        30,
                        2
                )
        );
        int insertedRows = questionScoringPointMapper.batchInsert(replacements);

        List<Map<String, Object>> targetRows = scoringPointRows(targetQuestionId);
        List<Map<String, Object>> otherRows = scoringPointRows(otherQuestionId);
        assertAll(
                () -> assertEquals(2, deletedRows),
                () -> assertEquals(2, insertedRows),
                () -> assertEquals(2, targetRows.size()),
                () -> assertScoringPointRow(
                        targetRows.get(0),
                        targetQuestionId,
                        "INTERNAL",
                        "新评分点一",
                        70,
                        1,
                        "ENABLED"
                ),
                () -> assertScoringPointRow(
                        targetRows.get(1),
                        targetQuestionId,
                        "ADVANCED",
                        "新评分点二",
                        30,
                        2,
                        "ENABLED"
                ),
                () -> assertEquals(1, otherRows.size()),
                () -> assertScoringPointRow(
                        otherRows.get(0),
                        otherQuestionId,
                        "PRINCIPLE",
                        "其他题目评分点",
                        100,
                        1,
                        "ENABLED"
                )
        );
    }

    @Test
    void shouldReturnZeroWhenQuestionHasNoScoringPoints() {
        Long questionId = insertParentQuestion();

        int deletedRows =
                questionScoringPointMapper.deleteByQuestionId(questionId);

        assertEquals(0, deletedRows);
    }

    private Long insertParentQuestion() {
        String knowledgePoint = "mapper-" + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO question
                (category, knowledge_point, difficulty,
                 question_content, reference_answer, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                QuestionCategory.JAVA_COLLECTION.name(),
                knowledgePoint,
                QuestionDifficulty.MEDIUM.name(),
                "Mapper 测试题目",
                "Mapper 测试答案",
                "ENABLED"
        );

        Long questionId = jdbcTemplate.queryForObject(
                "SELECT id FROM question WHERE knowledge_point = ?",
                Long.class,
                knowledgePoint
        );
        assertNotNull(questionId);
        return questionId;
    }

    private QuestionScoringPoint scoringPoint(
            Long questionId,
            QuestionPointType pointType,
            String content,
            int weight,
            int sortOrder
    ) {
        return QuestionScoringPoint.builder()
                .questionId(questionId)
                .pointType(pointType)
                .content(content)
                .weight(weight)
                .sortOrder(sortOrder)
                .status(QuestionPointStatus.ENABLED)
                .build();
    }

    private List<Map<String, Object>> scoringPointRows(Long questionId) {
        return jdbcTemplate.queryForList(
                """
                SELECT question_id, point_type, content, weight,
                       sort_order, status
                FROM question_scoring_point
                WHERE question_id = ?
                ORDER BY sort_order
                """,
                questionId
        );
    }

    private void assertScoringPointRow(
            Map<String, Object> row,
            Long questionId,
            String pointType,
            String content,
            int weight,
            int sortOrder,
            String status
    ) {
        assertAll(
                () -> assertEquals(questionId, asLong(row.get("question_id"))),
                () -> assertEquals(pointType, row.get("point_type")),
                () -> assertEquals(content, row.get("content")),
                () -> assertEquals(weight, asInt(row.get("weight"))),
                () -> assertEquals(sortOrder, asInt(row.get("sort_order"))),
                () -> assertEquals(status, row.get("status"))
        );
    }

    private long asLong(Object value) {
        return ((Number) value).longValue();
    }

    private int asInt(Object value) {
        return ((Number) value).intValue();
    }
}
