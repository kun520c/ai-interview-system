package com.kun.aiinterview.question.mapper;

import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class QuestionMapperTest {

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldInsertQuestionAndPopulateGeneratedId() {
        Question question = Question.builder()
                .category(QuestionCategory.JAVA_COLLECTION)
                .knowledgePoint("HashMap")
                .difficulty(QuestionDifficulty.MEDIUM)
                .questionContent("请说明 HashMap 的核心原理")
                .referenceAnswer("HashMap 基于数组和链表或红黑树实现")
                .status(QuestionStatus.ENABLED)
                .build();

        int affectedRows = questionMapper.insertQuestion(question);

        assertNotNull(question.getId());
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT category, knowledge_point, difficulty,
                       question_content, reference_answer, status,
                       created_at, updated_at
                FROM question
                WHERE id = ?
                """,
                question.getId()
        );
        assertAll(
                () -> assertEquals(1, affectedRows),
                () -> assertEquals("JAVA_COLLECTION", row.get("category")),
                () -> assertEquals("HashMap", row.get("knowledge_point")),
                () -> assertEquals("MEDIUM", row.get("difficulty")),
                () -> assertEquals(
                        "请说明 HashMap 的核心原理",
                        row.get("question_content")
                ),
                () -> assertEquals(
                        "HashMap 基于数组和链表或红黑树实现",
                        row.get("reference_answer")
                ),
                () -> assertEquals("ENABLED", row.get("status")),
                () -> assertNotNull(row.get("created_at")),
                () -> assertNotNull(row.get("updated_at"))
        );
    }
}
