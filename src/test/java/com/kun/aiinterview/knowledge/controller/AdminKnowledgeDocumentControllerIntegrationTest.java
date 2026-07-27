package com.kun.aiinterview.knowledge.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Transactional
class AdminKnowledgeDocumentControllerIntegrationTest {

    private static final String ENDPOINT = "/api/admin/knowledge/documents";

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
    void givenDatabaseAdminAndMarkdown_whenUploading_thenPersistsNormalizedDocument()
            throws Exception {
        User admin = createUser(UserRole.ADMIN);
        String expectedContent = "# HashMap\n第一段\n第二段  \n";
        MockMultipartFile file = file(
                "C:\\fakepath\\HashMap.md",
                "\uFEFF# HashMap\r\n第一段\r第二段  \r\n"
                        .getBytes(StandardCharsets.UTF_8)
        );
        int beforeCount = documentCount();

        String responseBody = mockMvc.perform(multipart(ENDPOINT)
                        .file(file)
                        .param("title", "  HashMap 原理  ")
                        .param("category", "JAVA_COLLECTION")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken(admin)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.documentId").isNumber())
                .andExpect(jsonPath("$.data.fileName").value("HashMap.md"))
                .andExpect(jsonPath("$.data.fileType").value("MARKDOWN"))
                .andExpect(jsonPath("$.data.documentVersion").value(1))
                .andExpect(jsonPath("$.data.processingStatus").value("UPLOADED"))
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andExpect(jsonPath("$.data.contentHash").doesNotExist())
                .andExpect(jsonPath("$.data.errorMessage").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        long documentId = response.path("data").path("documentId").asLong();
        assertTrue(documentId > 0);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT title, category, file_name, file_type, content,
                       content_hash, source, document_version,
                       processing_status, error_message, created_at, updated_at
                FROM knowledge_document
                WHERE id = ?
                """,
                documentId
        );
        assertAll(
                () -> assertEquals(beforeCount + 1, documentCount()),
                () -> assertEquals("HashMap 原理", row.get("title")),
                () -> assertEquals("JAVA_COLLECTION", row.get("category")),
                () -> assertEquals("HashMap.md", row.get("file_name")),
                () -> assertEquals("MARKDOWN", row.get("file_type")),
                () -> assertEquals(expectedContent, row.get("content")),
                () -> assertEquals(sha256(expectedContent), row.get("content_hash")),
                () -> assertNull(row.get("source")),
                () -> assertEquals(1, ((Number) row.get("document_version")).intValue()),
                () -> assertEquals("UPLOADED", row.get("processing_status")),
                () -> assertNull(row.get("error_message")),
                () -> assertNotNull(row.get("created_at")),
                () -> assertNotNull(row.get("updated_at"))
        );
    }

    @Test
    void givenDatabaseUser_whenUploading_thenReturnsForbiddenWithoutInsert()
            throws Exception {
        User user = createUser(UserRole.USER);
        int beforeCount = documentCount();

        mockMvc.perform(validRequest(accessToken(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        assertEquals(beforeCount, documentCount());
    }

    @Test
    void givenNoToken_whenUploading_thenReturnsUnauthorizedWithoutInsert()
            throws Exception {
        int beforeCount = documentCount();

        mockMvc.perform(multipart(ENDPOINT)
                        .file(file(
                                "document.md",
                                "正文".getBytes(StandardCharsets.UTF_8)
                        ))
                        .param("title", "标题")
                        .param("category", "JAVA_COLLECTION"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        assertEquals(beforeCount, documentCount());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDocuments")
    void givenInvalidDocument_whenUploading_thenReturnsBadRequestWithoutInsert(
            String scenario,
            String fileName,
            byte[] content
    ) throws Exception {
        User admin = createUser(UserRole.ADMIN);
        int beforeCount = documentCount();

        mockMvc.perform(multipart(ENDPOINT)
                        .file(file(fileName, content))
                        .param("title", "标题")
                        .param("category", "JAVA_COLLECTION")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken(admin)
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        assertEquals(beforeCount, documentCount(), scenario);
    }

    private static Stream<Arguments> invalidDocuments() {
        return Stream.of(
                Arguments.of(
                        "illegal extension",
                        "document.pdf",
                        "正文".getBytes(StandardCharsets.UTF_8)
                ),
                Arguments.of(
                        "malformed UTF-8",
                        "document.txt",
                        new byte[]{(byte) 0xC3, (byte) 0x28}
                ),
                Arguments.of(
                        "blank content",
                        "document.md",
                        " \t\r\n ".getBytes(StandardCharsets.UTF_8)
                )
        );
    }

    private org.springframework.test.web.servlet.RequestBuilder validRequest(String token) {
        return multipart(ENDPOINT)
                .file(file(
                        "document.md",
                        "正文".getBytes(StandardCharsets.UTF_8)
                ))
                .param("title", "标题")
                .param("category", "JAVA_COLLECTION")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockMultipartFile file(String originalFileName, byte[] content) {
        return new MockMultipartFile(
                "file",
                originalFileName,
                "application/octet-stream",
                content
        );
    }

    private User createUser(UserRole role) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);
        User user = User.builder()
                .account("knowledge_" + role.name().toLowerCase() + "_" + suffix)
                .password(passwordEncoder.encode("Password123!"))
                .username("知识库测试用户")
                .email("knowledge_" + suffix + "@test.com")
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

    private int documentCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_document",
                Integer.class
        );
        assertNotNull(count);
        return count;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(content.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
