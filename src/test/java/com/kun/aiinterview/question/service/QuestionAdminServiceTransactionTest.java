package com.kun.aiinterview.question.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.question.dto.CreateQuestionRequest;
import com.kun.aiinterview.question.dto.ScoringPointRequest;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionPointType;
import com.kun.aiinterview.question.mapper.QuestionScoringPointMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class QuestionAdminServiceTransactionTest {

    @Autowired
    private QuestionAdminService questionAdminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private QuestionScoringPointMapper questionScoringPointMapper;

    private String knowledgePoint;

    @AfterEach
    void cleanUpUnexpectedQuestion() {
        if (knowledgePoint != null) {
            jdbcTemplate.update(
                    "DELETE FROM question WHERE knowledge_point = ?",
                    knowledgePoint
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
        when(questionScoringPointMapper.batchInsert(anyList()))
                .thenReturn(0);

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
}
