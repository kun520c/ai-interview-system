package com.kun.aiinterview.question.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.question.dto.CreateQuestionRequest;
import com.kun.aiinterview.question.dto.ScoringPointRequest;
import com.kun.aiinterview.question.dto.UpdateQuestionRequest;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionPointStatus;
import com.kun.aiinterview.question.enums.QuestionPointType;
import com.kun.aiinterview.question.enums.QuestionStatus;
import com.kun.aiinterview.question.mapper.QuestionScoringPointMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class QuestionAdminServiceTransactionTest {

    @Autowired
    private QuestionAdminService questionAdminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private QuestionScoringPointMapper questionScoringPointMapper;

    private String knowledgePoint;
    private String originalKnowledgePoint;
    private final Set<Long> questionIdsToClean = new HashSet<>();

    @AfterEach
    void cleanUpUnexpectedQuestion() {
        if (knowledgePoint != null) {
            questionIdsToClean.addAll(jdbcTemplate.queryForList(
                    "SELECT id FROM question WHERE knowledge_point = ?",
                    Long.class,
                    knowledgePoint
            ));
        }

        for (Long questionId : questionIdsToClean) {
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
    void shouldRollbackQuestionWhenScoringPointInsertCountIsWrong() {
        knowledgePoint = "rollback-" + UUID.randomUUID();
        CreateQuestionRequest request = CreateQuestionRequest.builder()
                .category(QuestionCategory.JAVA_COLLECTION)
                .knowledgePoint(knowledgePoint)
                .difficulty(QuestionDifficulty.MEDIUM)
                .questionContent("事务回滚测试题目")
                .referenceAnswer("事务回滚测试答案")
                .scoringPoints(List.of(
                        new ScoringPointRequest(
                                QuestionPointType.CORE,
                                "事务回滚测试评分点",
                                100
                        )
                ))
                .build();
        doReturn(0)
                .when(questionScoringPointMapper)
                .batchInsert(anyList());

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.createQuestion(request)
        );

        Integer questionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question WHERE knowledge_point = ?",
                Integer.class,
                knowledgePoint
        );
        assertEquals(0, questionCount);
    }

    @Test
    void shouldRestoreQuestionAndOldScoringPointsWhenReplacementInsertFails() {
        Long questionId = insertOriginalQuestion();
        insertOldScoringPoints(questionId);
        UpdateQuestionRequest request = UpdateQuestionRequest.builder()
                .category(QuestionCategory.JVM)
                .knowledgePoint("修改后的知识点")
                .difficulty(QuestionDifficulty.HARD)
                .questionContent("修改后的题目内容")
                .referenceAnswer("修改后的参考答案")
                .scoringPoints(List.of(
                        new ScoringPointRequest(
                                QuestionPointType.CORE,
                                "新评分点一",
                                60
                        ),
                        new ScoringPointRequest(
                                QuestionPointType.KEY,
                                "新评分点二",
                                40
                        )
                ))
                .build();
        doReturn(0)
                .when(questionScoringPointMapper)
                .batchInsert(anyList());

        assertThrows(
                BusinessException.class,
                () -> questionAdminService.updateQuestion(questionId, request)
        );

        Map<String, Object> question = jdbcTemplate.queryForMap(
                """
                SELECT category, knowledge_point, difficulty,
                       question_content, reference_answer, status
                FROM question
                WHERE id = ?
                """,
                questionId
        );
        List<Map<String, Object>> scoringPoints = jdbcTemplate.queryForList(
                """
                SELECT point_type, content, weight, sort_order, status
                FROM question_scoring_point
                WHERE question_id = ?
                ORDER BY sort_order
                """,
                questionId
        );
        assertAll(
                () -> assertEquals("JAVA_COLLECTION", question.get("category")),
                () -> assertEquals(
                        originalKnowledgePoint,
                        question.get("knowledge_point")
                ),
                () -> assertEquals("MEDIUM", question.get("difficulty")),
                () -> assertEquals("事务原题目内容", question.get("question_content")),
                () -> assertEquals("事务原参考答案", question.get("reference_answer")),
                () -> assertEquals("DISABLED", question.get("status")),
                () -> assertEquals(2, scoringPoints.size()),
                () -> assertOldScoringPoint(
                        scoringPoints.get(0),
                        "CORE",
                        "旧评分点一",
                        70,
                        1,
                        "ENABLED"
                ),
                () -> assertOldScoringPoint(
                        scoringPoints.get(1),
                        "PRINCIPLE",
                        "旧评分点二",
                        30,
                        2,
                        "DISABLED"
                )
        );
    }

    private Long insertOriginalQuestion() {
        originalKnowledgePoint = "事务原知识点-" + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO question
                (category, knowledge_point, difficulty,
                 question_content, reference_answer, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                QuestionCategory.JAVA_COLLECTION.name(),
                originalKnowledgePoint,
                QuestionDifficulty.MEDIUM.name(),
                "事务原题目内容",
                "事务原参考答案",
                QuestionStatus.DISABLED.name()
        );
        Long questionId = jdbcTemplate.queryForObject(
                "SELECT id FROM question WHERE knowledge_point = ?",
                Long.class,
                originalKnowledgePoint
        );
        questionIdsToClean.add(questionId);
        return questionId;
    }

    private void insertOldScoringPoints(Long questionId) {
        jdbcTemplate.update(
                """
                INSERT INTO question_scoring_point
                (question_id, point_type, content, weight, sort_order, status)
                VALUES
                (?, ?, ?, ?, ?, ?),
                (?, ?, ?, ?, ?, ?)
                """,
                questionId,
                QuestionPointType.CORE.name(),
                "旧评分点一",
                70,
                1,
                QuestionPointStatus.ENABLED.name(),
                questionId,
                QuestionPointType.PRINCIPLE.name(),
                "旧评分点二",
                30,
                2,
                QuestionPointStatus.DISABLED.name()
        );
    }

    private void assertOldScoringPoint(
            Map<String, Object> row,
            String pointType,
            String content,
            int weight,
            int sortOrder,
            String status
    ) {
        assertAll(
                () -> assertEquals(pointType, row.get("point_type")),
                () -> assertEquals(content, row.get("content")),
                () -> assertEquals(
                        weight,
                        ((Number) row.get("weight")).intValue()
                ),
                () -> assertEquals(
                        sortOrder,
                        ((Number) row.get("sort_order")).intValue()
                ),
                () -> assertEquals(status, row.get("status"))
        );
    }
}
