package com.kun.aiinterview.question.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.question.dto.CreateQuestionRequest;
import com.kun.aiinterview.question.dto.ScoringPointRequest;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionPointType;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Transactional
class AdminQuestionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateQuestionAndScoringPointsForDatabaseAdmin()
            throws Exception {
        User admin = createAdmin();
        String accessToken = jwtTokenService.generateAccessToken(
                admin.getId(),
                admin.getAccount(),
                admin.getRole()
        );
        CreateQuestionRequest request = validRequest();

        String responseBody = mockMvc.perform(
                        post("/api/admin/questions")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.questionId").isNumber())
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode responseJson = objectMapper.readTree(responseBody);
        long questionId = responseJson.path("data")
                .path("questionId")
                .asLong();
        assertTrue(questionId > 0);
        Map<String, Object> question = jdbcTemplate.queryForMap(
                """
                SELECT category, knowledge_point, difficulty, status
                FROM question
                WHERE id = ?
                """,
                questionId
        );
        List<Map<String, Object>> scoringPoints =
                jdbcTemplate.queryForList(
                        """
                        SELECT question_id, point_type, content,
                               weight, sort_order, status
                        FROM question_scoring_point
                        WHERE question_id = ?
                        ORDER BY sort_order
                        """,
                        questionId
                );
        assertAll(
                () -> assertEquals("JAVA_COLLECTION", question.get("category")),
                () -> assertEquals("HashMap", question.get("knowledge_point")),
                () -> assertEquals("MEDIUM", question.get("difficulty")),
                () -> assertEquals("ENABLED", question.get("status")),
                () -> assertEquals(2, scoringPoints.size()),
                () -> assertEquals("CORE", scoringPoints.get(0).get("point_type")),
                () -> assertEquals(60, asInt(scoringPoints.get(0).get("weight"))),
                () -> assertEquals(1, asInt(scoringPoints.get(0).get("sort_order"))),
                () -> assertEquals("ENABLED", scoringPoints.get(0).get("status")),
                () -> assertEquals("KEY", scoringPoints.get(1).get("point_type")),
                () -> assertEquals(40, asInt(scoringPoints.get(1).get("weight"))),
                () -> assertEquals(2, asInt(scoringPoints.get(1).get("sort_order")))
        );
    }

    private User createAdmin() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);
        User admin = User.builder()
                .account("admin_" + suffix)
                .password(passwordEncoder.encode("Password123!"))
                .username("题库管理员")
                .email("admin_" + suffix + "@test.com")
                .role(UserRole.ADMIN)
                .status(UserStatus.ENABLED)
                .build();

        assertEquals(1, userMapper.insertUser(admin));
        assertNotNull(admin.getId());
        return admin;
    }

    private CreateQuestionRequest validRequest() {
        return CreateQuestionRequest.builder()
                .category(QuestionCategory.JAVA_COLLECTION)
                .knowledgePoint("HashMap")
                .difficulty(QuestionDifficulty.MEDIUM)
                .questionContent("请说明 HashMap 的核心原理")
                .referenceAnswer("HashMap 基于数组和链表或红黑树实现")
                .scoringPoints(List.of(
                        new ScoringPointRequest(
                                QuestionPointType.CORE,
                                "说明数组和桶结构",
                                60
                        ),
                        new ScoringPointRequest(
                                QuestionPointType.KEY,
                                "说明哈希冲突处理",
                                40
                        )
                ))
                .build();
    }

    private int asInt(Object value) {
        return ((Number) value).intValue();
    }
}
