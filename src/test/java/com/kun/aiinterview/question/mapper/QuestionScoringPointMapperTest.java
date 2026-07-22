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

    private long asLong(Object value) {
        return ((Number) value).longValue();
    }

    private int asInt(Object value) {
        return ((Number) value).intValue();
    }
}
