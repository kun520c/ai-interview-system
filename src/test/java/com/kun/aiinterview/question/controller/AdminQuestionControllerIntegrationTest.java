package com.kun.aiinterview.question.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.question.dto.CreateQuestionRequest;
import com.kun.aiinterview.question.dto.ScoringPointRequest;
import com.kun.aiinterview.question.dto.UpdateQuestionRequest;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionPointStatus;
import com.kun.aiinterview.question.enums.QuestionPointType;
import com.kun.aiinterview.question.enums.QuestionStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    void shouldReplaceAllScoringPointsAndPreserveQuestionStatus()
            throws Exception {
        User admin = createAdmin();
        String accessToken = accessToken(admin);
        Long targetQuestionId = insertQuestion(
                "target",
                QuestionStatus.DISABLED
        );
        Long otherQuestionId = insertQuestion(
                "other",
                QuestionStatus.ENABLED
        );
        insertTargetOldScoringPoints(targetQuestionId);
        insertOtherScoringPoint(otherQuestionId);
        UpdateQuestionRequest request = replacementRequest();

        mockMvc.perform(put("/api/admin/questions/{questionId}", targetQuestionId)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isEmpty());

        Map<String, Object> updatedQuestion = questionRow(targetQuestionId);
        List<Map<String, Object>> replacementRows =
                scoringPointRows(targetQuestionId);
        Map<String, Object> otherQuestion = questionRow(otherQuestionId);
        List<Map<String, Object>> otherRows =
                scoringPointRows(otherQuestionId);
        assertAll(
                () -> assertEquals("JVM", updatedQuestion.get("category")),
                () -> assertEquals(
                        "类加载机制",
                        updatedQuestion.get("knowledge_point")
                ),
                () -> assertEquals("HARD", updatedQuestion.get("difficulty")),
                () -> assertEquals(
                        "请说明类加载的完整过程",
                        updatedQuestion.get("question_content")
                ),
                () -> assertEquals(
                        "加载、验证、准备、解析和初始化",
                        updatedQuestion.get("reference_answer")
                ),
                () -> assertEquals("DISABLED", updatedQuestion.get("status")),
                () -> assertEquals(3, replacementRows.size()),
                () -> assertScoringPointRow(
                        replacementRows.get(0),
                        targetQuestionId,
                        "CORE",
                        "说明类加载阶段",
                        50,
                        1,
                        "ENABLED"
                ),
                () -> assertScoringPointRow(
                        replacementRows.get(1),
                        targetQuestionId,
                        "INTERNAL",
                        "说明双亲委派",
                        30,
                        2,
                        "ENABLED"
                ),
                () -> assertScoringPointRow(
                        replacementRows.get(2),
                        targetQuestionId,
                        "ADVANCED",
                        "说明类加载器隔离",
                        20,
                        3,
                        "ENABLED"
                ),
                () -> assertEquals(
                        "JAVA_COLLECTION",
                        otherQuestion.get("category")
                ),
                () -> assertEquals(
                        "other-原知识点",
                        otherQuestion.get("knowledge_point")
                ),
                () -> assertEquals("ENABLED", otherQuestion.get("status")),
                () -> assertEquals(1, otherRows.size()),
                () -> assertScoringPointRow(
                        otherRows.get(0),
                        otherQuestionId,
                        "PRINCIPLE",
                        "其他题目评分点",
                        100,
                        1,
                        "DISABLED"
                )
        );
    }

    @Test
    void shouldReturnBadRequestWhenAdminUpdatesMissingQuestion()
            throws Exception {
        User admin = createAdmin();

        mockMvc.perform(put("/api/admin/questions/{questionId}", Long.MAX_VALUE)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken(admin)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                replacementRequest()
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("题目不存在"));
    }

    @Test
    void shouldReturnBadRequestForInvalidQuestionId() throws Exception {
        User admin = createAdmin();

        mockMvc.perform(put("/api/admin/questions/0")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken(admin)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                replacementRequest()
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("题目ID不合法"));
    }

    @Test
    void shouldReturnBadRequestWhenReplacementWeightsDoNotTotalOneHundred()
            throws Exception {
        User admin = createAdmin();
        Long questionId = insertQuestion(
                "invalid-weight",
                QuestionStatus.DISABLED
        );
        insertTargetOldScoringPoints(questionId);
        UpdateQuestionRequest request = replacementRequest();
        request.setScoringPoints(List.of(
                new ScoringPointRequest(
                        QuestionPointType.CORE,
                        "权重不足一",
                        60
                ),
                new ScoringPointRequest(
                        QuestionPointType.KEY,
                        "权重不足二",
                        30
                )
        ));

        mockMvc.perform(put("/api/admin/questions/{questionId}", questionId)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken(admin)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("评分点权重和必须为100"));

        assertAll(
                () -> assertEquals(
                        "invalid-weight-原题目内容",
                        questionRow(questionId).get("question_content")
                ),
                () -> assertEquals(2, scoringPointRows(questionId).size())
        );
    }

    @Test
    void shouldReturnForbiddenWhenDatabaseUserUpdatesQuestion()
            throws Exception {
        User user = createUser(UserRole.USER);

        mockMvc.perform(put("/api/admin/questions/1")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken(user)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                replacementRequest()
                        )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private User createAdmin() {
        return createUser(UserRole.ADMIN);
    }

    private User createUser(UserRole role) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);
        User admin = User.builder()
                .account(role.name().toLowerCase() + "_" + suffix)
                .password(passwordEncoder.encode("Password123!"))
                .username("题库管理员")
                .email(role.name().toLowerCase() + "_" + suffix + "@test.com")
                .role(role)
                .status(UserStatus.ENABLED)
                .build();

        assertEquals(1, userMapper.insertUser(admin));
        assertNotNull(admin.getId());
        return admin;
    }

    private String accessToken(User user) {
        return jwtTokenService.generateAccessToken(
                user.getId(),
                user.getAccount(),
                user.getRole()
        );
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

    private Long insertQuestion(String marker, QuestionStatus status) {
        String knowledgePoint = marker + "-原知识点";
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
                marker + "-原题目内容",
                marker + "-原参考答案",
                status.name()
        );
        Long questionId = jdbcTemplate.queryForObject(
                "SELECT id FROM question WHERE knowledge_point = ?",
                Long.class,
                knowledgePoint
        );
        assertNotNull(questionId);
        return questionId;
    }

    private void insertTargetOldScoringPoints(Long questionId) {
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
                "目标旧评分点一",
                60,
                1,
                QuestionPointStatus.ENABLED.name(),
                questionId,
                QuestionPointType.KEY.name(),
                "目标旧评分点二",
                40,
                2,
                QuestionPointStatus.DISABLED.name()
        );
    }

    private void insertOtherScoringPoint(Long questionId) {
        jdbcTemplate.update(
                """
                INSERT INTO question_scoring_point
                (question_id, point_type, content, weight, sort_order, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                questionId,
                QuestionPointType.PRINCIPLE.name(),
                "其他题目评分点",
                100,
                1,
                QuestionPointStatus.DISABLED.name()
        );
    }

    private UpdateQuestionRequest replacementRequest() {
        return UpdateQuestionRequest.builder()
                .category(QuestionCategory.JVM)
                .knowledgePoint("类加载机制")
                .difficulty(QuestionDifficulty.HARD)
                .questionContent("请说明类加载的完整过程")
                .referenceAnswer("加载、验证、准备、解析和初始化")
                .scoringPoints(List.of(
                        new ScoringPointRequest(
                                QuestionPointType.CORE,
                                "说明类加载阶段",
                                50
                        ),
                        new ScoringPointRequest(
                                QuestionPointType.INTERNAL,
                                "说明双亲委派",
                                30
                        ),
                        new ScoringPointRequest(
                                QuestionPointType.ADVANCED,
                                "说明类加载器隔离",
                                20
                        )
                ))
                .build();
    }

    private Map<String, Object> questionRow(Long questionId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT category, knowledge_point, difficulty,
                       question_content, reference_answer, status
                FROM question
                WHERE id = ?
                """,
                questionId
        );
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
                () -> assertEquals(
                        questionId,
                        ((Number) row.get("question_id")).longValue()
                ),
                () -> assertEquals(pointType, row.get("point_type")),
                () -> assertEquals(content, row.get("content")),
                () -> assertEquals(weight, asInt(row.get("weight"))),
                () -> assertEquals(sortOrder, asInt(row.get("sort_order"))),
                () -> assertEquals(status, row.get("status"))
        );
    }

    private int asInt(Object value) {
        return ((Number) value).intValue();
    }
}
