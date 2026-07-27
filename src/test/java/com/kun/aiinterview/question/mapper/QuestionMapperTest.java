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
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void shouldFindQuestionByIdAndReturnNullForMissingQuestion() {
        Question question = insertQuestion(
                "查询测试知识点",
                QuestionStatus.ENABLED
        );

        Question found = questionMapper.getQuestionById(question.getId());
        Question missing = questionMapper.getQuestionById(Long.MAX_VALUE);

        assertAll(
                () -> assertNotNull(found),
                () -> assertEquals(question.getId(), found.getId()),
                () -> assertEquals(QuestionCategory.JAVA_COLLECTION, found.getCategory()),
                () -> assertEquals("查询测试知识点", found.getKnowledgePoint()),
                () -> assertEquals(QuestionDifficulty.MEDIUM, found.getDifficulty()),
                () -> assertEquals("原题目内容", found.getQuestionContent()),
                () -> assertEquals("原参考答案", found.getReferenceAnswer()),
                () -> assertEquals(QuestionStatus.ENABLED, found.getStatus()),
                () -> assertNotNull(found.getCreatedAt()),
                () -> assertNotNull(found.getUpdatedAt()),
                () -> assertNull(missing)
        );
    }

    @Test
    void shouldUpdateOnlySpecifiedQuestionAndPreserveStatus() {
        Question target = insertQuestion(
                "待更新知识点",
                QuestionStatus.DISABLED
        );
        Question other = insertQuestion(
                "不应更新知识点",
                QuestionStatus.ENABLED
        );
        Question update = Question.builder()
                .id(target.getId())
                .category(QuestionCategory.JVM)
                .knowledgePoint("类加载机制")
                .difficulty(QuestionDifficulty.HARD)
                .questionContent("请说明类加载过程")
                .referenceAnswer("加载、验证、准备、解析和初始化")
                .build();

        int affectedRows = questionMapper.updateQuestion(update);

        Question updated = questionMapper.getQuestionById(target.getId());
        Question unchanged = questionMapper.getQuestionById(other.getId());
        assertAll(
                () -> assertEquals(1, affectedRows),
                () -> assertEquals(QuestionCategory.JVM, updated.getCategory()),
                () -> assertEquals("类加载机制", updated.getKnowledgePoint()),
                () -> assertEquals(QuestionDifficulty.HARD, updated.getDifficulty()),
                () -> assertEquals("请说明类加载过程", updated.getQuestionContent()),
                () -> assertEquals(
                        "加载、验证、准备、解析和初始化",
                        updated.getReferenceAnswer()
                ),
                () -> assertEquals(QuestionStatus.DISABLED, updated.getStatus()),
                () -> assertEquals(
                        QuestionCategory.JAVA_COLLECTION,
                        unchanged.getCategory()
                ),
                () -> assertEquals("不应更新知识点", unchanged.getKnowledgePoint()),
                () -> assertEquals(QuestionDifficulty.MEDIUM, unchanged.getDifficulty()),
                () -> assertEquals("原题目内容", unchanged.getQuestionContent()),
                () -> assertEquals("原参考答案", unchanged.getReferenceAnswer()),
                () -> assertEquals(QuestionStatus.ENABLED, unchanged.getStatus())
        );
    }

    @Test
    void givenTwoQuestionsAndScoringPoints_whenUpdatingStatus_thenOnlyTargetQuestionStatusChanges() {
        Question target = insertQuestion(
                "状态修改目标知识点",
                QuestionStatus.ENABLED
        );
        Question other = insertQuestion(
                "状态修改隔离知识点",
                QuestionStatus.ENABLED
        );
        jdbcTemplate.update(
                """
                INSERT INTO question_scoring_point
                (question_id, point_type, content, weight, sort_order, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                target.getId(),
                "CORE",
                "状态修改不应影响的评分点",
                100,
                1,
                "ENABLED"
        );
        Map<String, Object> before = jdbcTemplate.queryForMap(
                """
                SELECT category, knowledge_point, difficulty,
                       question_content, reference_answer
                FROM question
                WHERE id = ?
                """,
                target.getId()
        );

        int affectedRows = questionMapper.updateQuestionStatus(
                target.getId(),
                QuestionStatus.DISABLED
        );

        Map<String, Object> after = jdbcTemplate.queryForMap(
                """
                SELECT category, knowledge_point, difficulty,
                       question_content, reference_answer, status
                FROM question
                WHERE id = ?
                """,
                target.getId()
        );
        Map<String, Object> scoringPoint = jdbcTemplate.queryForMap(
                """
                SELECT content, weight, sort_order, status
                FROM question_scoring_point
                WHERE question_id = ?
                """,
                target.getId()
        );
        assertAll(
                () -> assertEquals(1, affectedRows),
                () -> assertEquals(before.get("category"), after.get("category")),
                () -> assertEquals(
                        before.get("knowledge_point"),
                        after.get("knowledge_point")
                ),
                () -> assertEquals(before.get("difficulty"), after.get("difficulty")),
                () -> assertEquals(
                        before.get("question_content"),
                        after.get("question_content")
                ),
                () -> assertEquals(
                        before.get("reference_answer"),
                        after.get("reference_answer")
                ),
                () -> assertEquals("DISABLED", after.get("status")),
                () -> assertEquals(
                        QuestionStatus.ENABLED,
                        questionMapper.getQuestionById(other.getId()).getStatus()
                ),
                () -> assertEquals(
                        "状态修改不应影响的评分点",
                        scoringPoint.get("content")
                ),
                () -> assertEquals(100, ((Number) scoringPoint.get("weight")).intValue()),
                () -> assertEquals(1, ((Number) scoringPoint.get("sort_order")).intValue()),
                () -> assertEquals("ENABLED", scoringPoint.get("status"))
        );
    }

    @Test
    void givenMissingQuestion_whenUpdatingStatus_thenAffectedRowsIsZero() {
        int affectedRows = questionMapper.updateQuestionStatus(
                Long.MAX_VALUE,
                QuestionStatus.DISABLED
        );

        assertEquals(0, affectedRows);
    }

    @Test
    void givenQuestionAlreadyInTargetStatus_whenUpdatingStatus_thenMatchedRowCountIsOne() {
        Question question = insertQuestion(
                "相同状态影响行数知识点",
                QuestionStatus.ENABLED
        );

        int affectedRows = questionMapper.updateQuestionStatus(
                question.getId(),
                QuestionStatus.ENABLED
        );

        assertEquals(1, affectedRows);
    }

    private Question insertQuestion(
            String knowledgePoint,
            QuestionStatus status
    ) {
        Question question = Question.builder()
                .category(QuestionCategory.JAVA_COLLECTION)
                .knowledgePoint(knowledgePoint)
                .difficulty(QuestionDifficulty.MEDIUM)
                .questionContent("原题目内容")
                .referenceAnswer("原参考答案")
                .status(status)
                .build();

        assertEquals(1, questionMapper.insertQuestion(question));
        assertNotNull(question.getId());
        return question;
    }
}
