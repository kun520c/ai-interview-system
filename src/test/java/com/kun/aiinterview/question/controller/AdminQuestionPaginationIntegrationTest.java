package com.kun.aiinterview.question.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Transactional
class AdminQuestionPaginationIntegrationTest {

    private static final LocalDateTime FUTURE_TIME =
            LocalDateTime.of(2099, 1, 1, 0, 0);

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
    void shouldUseDefaultPageNumberAndSize() throws Exception {
        User admin = createUser(UserRole.ADMIN);
        long originalTotal = countQuestions();
        String marker = marker();
        for (int index = 0; index < 12; index++) {
            insertQuestion(
                    marker + "-" + index,
                    marker + "-content-" + index,
                    QuestionCategory.JAVA_BASIC,
                    QuestionDifficulty.EASY,
                    QuestionStatus.ENABLED,
                    FUTURE_TIME.plusMinutes(index)
            );
        }

        JsonNode data = performAdminGet(admin, "")
                .path("data");

        assertEquals(1, data.path("pageNum").asInt());
        assertEquals(10, data.path("pageSize").asInt());
        assertEquals(originalTotal + 12, data.path("total").asLong());
        assertEquals(10, data.path("records").size());
    }

    @Test
    void shouldReturnTwoNonOverlappingPagesWithCorrectTotals()
            throws Exception {
        User admin = createUser(UserRole.ADMIN);
        String marker = marker();
        for (int index = 0; index < 7; index++) {
            insertQuestion(
                    marker + "-" + index,
                    marker + "-content-" + index,
                    QuestionCategory.JVM,
                    QuestionDifficulty.MEDIUM,
                    QuestionStatus.ENABLED,
                    FUTURE_TIME.plusMinutes(index)
            );
        }

        JsonNode first = performAdminGet(
                admin,
                "?keyword=" + marker + "&pageNum=1&pageSize=5"
        ).path("data");
        JsonNode second = performAdminGet(
                admin,
                "?keyword=" + marker + "&pageNum=2&pageSize=5"
        ).path("data");

        Set<Long> firstIds = ids(first.path("records"));
        Set<Long> secondIds = ids(second.path("records"));
        assertEquals(5, first.path("records").size());
        assertEquals(2, second.path("records").size());
        assertEquals(7, first.path("total").asLong());
        assertEquals(2, first.path("totalPages").asLong());
        assertTrue(firstIds.stream().noneMatch(secondIds::contains));
    }

    @Test
    void shouldFilterByCategoryDifficultyAndEachStatus() throws Exception {
        User admin = createUser(UserRole.ADMIN);
        String marker = marker();
        insertQuestion(
                marker + "-target",
                marker + "-target-content",
                QuestionCategory.MYSQL,
                QuestionDifficulty.HARD,
                QuestionStatus.DISABLED,
                FUTURE_TIME
        );
        insertQuestion(
                marker + "-wrong-category",
                marker + "-wrong-category-content",
                QuestionCategory.REDIS,
                QuestionDifficulty.HARD,
                QuestionStatus.DISABLED,
                FUTURE_TIME
        );
        insertQuestion(
                marker + "-wrong-difficulty",
                marker + "-wrong-difficulty-content",
                QuestionCategory.MYSQL,
                QuestionDifficulty.EASY,
                QuestionStatus.DISABLED,
                FUTURE_TIME
        );
        insertQuestion(
                marker + "-enabled",
                marker + "-enabled-content",
                QuestionCategory.MYSQL,
                QuestionDifficulty.HARD,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );

        JsonNode category = performAdminGet(
                admin,
                "?keyword=" + marker + "&category=MYSQL"
        ).path("data");
        JsonNode difficulty = performAdminGet(
                admin,
                "?keyword=" + marker + "&difficulty=EASY"
        ).path("data");
        JsonNode disabled = performAdminGet(
                admin,
                "?keyword=" + marker + "&status=DISABLED"
        ).path("data");
        JsonNode enabled = performAdminGet(
                admin,
                "?keyword=" + marker + "&status=ENABLED"
        ).path("data");

        assertEquals(3, category.path("total").asLong());
        assertEquals(1, difficulty.path("total").asLong());
        assertEquals(3, disabled.path("total").asLong());
        assertEquals(1, enabled.path("total").asLong());
        assertEveryField(category.path("records"), "category", "MYSQL");
        assertEveryField(difficulty.path("records"), "difficulty", "EASY");
        assertEveryField(disabled.path("records"), "status", "DISABLED");
        assertEveryField(enabled.path("records"), "status", "ENABLED");
    }

    @Test
    void shouldMatchKeywordInKnowledgePointAndQuestionContent()
            throws Exception {
        User admin = createUser(UserRole.ADMIN);
        String knowledgeMarker = marker();
        String contentMarker = marker();
        insertQuestion(
                knowledgeMarker,
                "ordinary content",
                QuestionCategory.SPRING,
                QuestionDifficulty.MEDIUM,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );
        insertQuestion(
                "ordinary knowledge",
                contentMarker,
                QuestionCategory.SPRING,
                QuestionDifficulty.MEDIUM,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );

        JsonNode knowledgeResult = performAdminGet(
                admin,
                "?keyword=" + knowledgeMarker
        ).path("data");
        JsonNode contentResult = performAdminGet(
                admin,
                "?keyword=" + contentMarker
        ).path("data");

        assertEquals(1, knowledgeResult.path("total").asLong());
        assertEquals(
                knowledgeMarker,
                knowledgeResult.path("records").get(0)
                        .path("knowledgePoint").asText()
        );
        assertEquals(1, contentResult.path("total").asLong());
        assertEquals(
                contentMarker,
                contentResult.path("records").get(0)
                        .path("questionContent").asText()
        );
    }

    @Test
    void shouldRequireAllCombinedFilters() throws Exception {
        User admin = createUser(UserRole.ADMIN);
        String marker = marker();
        Long expectedId = insertQuestion(
                marker + "-target",
                marker + "-target-content",
                QuestionCategory.JAVA_CONCURRENCY,
                QuestionDifficulty.HARD,
                QuestionStatus.DISABLED,
                FUTURE_TIME
        );
        insertQuestion(
                marker + "-other",
                marker + "-other-content",
                QuestionCategory.JAVA_CONCURRENCY,
                QuestionDifficulty.HARD,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );

        JsonNode data = performAdminGet(
                admin,
                "?keyword=" + marker
                        + "&category=JAVA_CONCURRENCY"
                        + "&difficulty=HARD"
                        + "&status=DISABLED"
        ).path("data");

        assertEquals(1, data.path("total").asLong());
        assertEquals(expectedId, data.path("records").get(0).path("id").asLong());
    }

    @Test
    void shouldSortByUpdatedAtThenIdAndExposeOnlyListFields()
            throws Exception {
        User admin = createUser(UserRole.ADMIN);
        String marker = marker();
        Long olderId = insertQuestion(
                marker + "-older",
                marker + "-older-content",
                QuestionCategory.PROJECT_SCENARIO,
                QuestionDifficulty.MEDIUM,
                QuestionStatus.ENABLED,
                FUTURE_TIME.minusDays(1)
        );
        Long lowerId = insertQuestion(
                marker + "-lower",
                marker + "-lower-content",
                QuestionCategory.PROJECT_SCENARIO,
                QuestionDifficulty.MEDIUM,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );
        Long higherId = insertQuestion(
                marker + "-higher",
                marker + "-higher-content",
                QuestionCategory.PROJECT_SCENARIO,
                QuestionDifficulty.MEDIUM,
                QuestionStatus.ENABLED,
                FUTURE_TIME
        );

        JsonNode records = performAdminGet(
                admin,
                "?keyword=" + marker
        ).path("data").path("records");

        assertEquals(List.of(higherId, lowerId, olderId), orderedIds(records));
        JsonNode item = records.get(0);
        assertTrue(item.hasNonNull("updatedAt"));
        assertFalse(item.has("updateAt"));
        assertFalse(item.has("referenceAnswer"));
        assertFalse(item.has("scoringPoints"));
        assertFalse(item.has("createdAt"));
    }

    @Test
    void shouldReturnEmptyPageForNoMatchAndForPageBeyondLastPage()
            throws Exception {
        User admin = createUser(UserRole.ADMIN);
        String marker = marker();
        for (int index = 0; index < 3; index++) {
            insertQuestion(
                    marker + "-" + index,
                    marker + "-content-" + index,
                    QuestionCategory.NETWORK,
                    QuestionDifficulty.MEDIUM,
                    QuestionStatus.ENABLED,
                    FUTURE_TIME.plusMinutes(index)
            );
        }

        JsonNode missing = performAdminGet(
                admin,
                "?keyword=missing-" + UUID.randomUUID()
        ).path("data");
        JsonNode beyond = performAdminGet(
                admin,
                "?keyword=" + marker + "&pageNum=100&pageSize=2"
        ).path("data");

        assertTrue(missing.path("records").isArray());
        assertEquals(0, missing.path("records").size());
        assertEquals(0, missing.path("total").asLong());
        assertEquals(0, missing.path("totalPages").asLong());
        assertEquals(0, beyond.path("records").size());
        assertEquals(3, beyond.path("total").asLong());
        assertEquals(2, beyond.path("totalPages").asLong());
    }

    @Test
    void shouldEnforceAdminSecurityForRealJwtRequests() throws Exception {
        User user = createUser(UserRole.USER);

        mockMvc.perform(get("/api/admin/questions")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken(user)
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/admin/questions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    private JsonNode performAdminGet(User admin, String queryString)
            throws Exception {
        String response = mockMvc.perform(
                        get("/api/admin/questions" + queryString)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken(admin)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private User createUser(UserRole role) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);
        User user = User.builder()
                .account(role.name().toLowerCase() + "_page_" + suffix)
                .password(passwordEncoder.encode("Password123!"))
                .username("分页测试用户")
                .email(role.name().toLowerCase() + "_page_" + suffix + "@test.com")
                .role(role)
                .status(UserStatus.ENABLED)
                .build();
        assertEquals(1, userMapper.insertUser(user));
        assertNotNull(user.getId());
        return user;
    }

    private String accessToken(User user) {
        return jwtTokenService.generateAccessToken(
                user.getId(),
                user.getAccount(),
                user.getRole()
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
                "pagination integration reference answer",
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

    private long countQuestions() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question",
                Long.class
        );
    }

    private Set<Long> ids(JsonNode records) {
        Set<Long> result = new HashSet<>();
        records.forEach(item -> result.add(item.path("id").asLong()));
        return result;
    }

    private List<Long> orderedIds(JsonNode records) {
        return java.util.stream.StreamSupport.stream(
                        records.spliterator(),
                        false
                )
                .map(item -> item.path("id").asLong())
                .toList();
    }

    private void assertEveryField(
            JsonNode records,
            String field,
            String expected
    ) {
        assertTrue(records.isArray());
        records.forEach(item -> assertEquals(expected, item.path(field).asText()));
    }

    private String marker() {
        return "page-" + UUID.randomUUID().toString().replace("-", "");
    }
}
