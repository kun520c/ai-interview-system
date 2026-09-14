package com.kun.aiinterview.interview.mapper;

import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionPointStatus;
import com.kun.aiinterview.question.mapper.QuestionMapper;
import com.kun.aiinterview.question.mapper.QuestionScoringPointMapper;
import com.kun.aiinterview.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class InterviewSessionCreationMapperTest {

    @Autowired private QuestionMapper questionMapper;
    @Autowired private QuestionScoringPointMapper scoringPointMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void shouldUseRealSelectionAndLockQueries() {
        long userId = insertUser();
        long questionId = insertQuestion("HARD", "ENABLED");
        insertScoringPoint(questionId, "KEY", 40, 2, "ENABLED");
        insertScoringPoint(questionId, "CORE", 60, 1, "ENABLED");
        insertScoringPoint(questionId, "ADVANCED", 20, 0, "DISABLED");

        assertThat(userMapper.getUserByIdForUpdate(userId).getId())
                .isEqualTo(userId);

        List<Question> candidates = questionMapper
                .selectEnabledQuestionsForInterview(QuestionDifficulty.HARD);
        assertThat(candidates).filteredOn(question -> question.getId() == questionId)
                .singleElement()
                .satisfies(question -> {
                    assertThat(question.getDifficulty()).isEqualTo(QuestionDifficulty.HARD);
                    assertThat(question.getQuestionContent()).isEqualTo("mapper-question");
                    assertThat(question.getReferenceAnswer()).isEqualTo("mapper-answer");
                });

        List<QuestionScoringPoint> points = scoringPointMapper
                .selectEnabledByQuestionId(questionId);
        assertThat(points).extracting(QuestionScoringPoint::getSortOrder)
                .containsExactly(1, 2);
        assertThat(points).allSatisfy(point -> {
            assertThat(point.getQuestionId()).isEqualTo(questionId);
            assertThat(point.getStatus()).isEqualTo(QuestionPointStatus.ENABLED);
            assertThat(point.getId()).isNotNull();
            assertThat(point.getPointType()).isNotNull();
            assertThat(point.getContent()).isNotBlank();
            assertThat(point.getWeight()).isPositive();
        });
    }

    private long insertUser() {
        String value = unique();
        jdbcTemplate.update("""
                INSERT INTO `user` (account, username, password, email, role, status)
                VALUES (?, 'mapper-lock-user', 'hash', ?, 'USER', 'ENABLED')
                """, "creation-" + value, "creation-" + value + "@example.com");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE account = ?",
                Long.class,
                "creation-" + value
        );
    }

    private long insertQuestion(String difficulty, String status) {
        String point = "creation-mapper-" + unique();
        jdbcTemplate.update("""
                INSERT INTO question
                    (category, knowledge_point, difficulty, question_content,
                     reference_answer, status)
                VALUES ('JAVA_BASIC', ?, ?, 'mapper-question', 'mapper-answer', ?)
                """, point, difficulty, status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM question WHERE knowledge_point = ?",
                Long.class,
                point
        );
    }

    private void insertScoringPoint(
            long questionId,
            String type,
            int weight,
            int order,
            String status
    ) {
        jdbcTemplate.update("""
                INSERT INTO question_scoring_point
                    (question_id, point_type, content, weight, sort_order, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, questionId, type, "point-" + order, weight, order, status);
    }

    private String unique() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
