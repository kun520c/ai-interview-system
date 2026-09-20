package com.kun.aiinterview.knowledge.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.unit.DataSize;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"local", "test"})
class KnowledgeMultipartServletLimitTest {

    private static final String ENDPOINT = "/api/admin/knowledge/documents";
    private static final long FIVE_MIB = 5L * 1024 * 1024;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MultipartProperties multipartProperties;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldAlignServletMultipartLimitWithFiveMibBusinessContract() {
        assertEquals(DataSize.ofBytes(5L * 1024 * 1024), multipartProperties.getMaxFileSize());
        assertEquals(DataSize.ofBytes(6L * 1024 * 1024), multipartProperties.getMaxRequestSize());
    }

    @Test
    void givenFileLargerThanOneMbAndWithinFiveMib_whenUploading_thenServletAcceptsFile()
            throws Exception {
        User admin = createUser(UserRole.ADMIN);
        byte[] content = new byte[1024 * 1024 + 1];
        Arrays.fill(content, (byte) 'a');
        Long documentId = null;

        try {
            ResponseEntity<String> response = upload(admin, "over-one-mb.md", content);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            JsonNode body = objectMapper.readTree(response.getBody());
            assertEquals(200, body.path("code").asInt());
            assertEquals("UPLOADED", body.path("data").path("processingStatus").asText());
            documentId = body.path("data").path("documentId").asLong();
            assertTrue(documentId > 0);
        } finally {
            if (documentId != null) {
                jdbcTemplate.update(
                        "DELETE FROM knowledge_document WHERE id = ?",
                        documentId
                );
            }
            jdbcTemplate.update(
                    "DELETE FROM `user` WHERE id = ?",
                    admin.getId()
            );
        }
    }

    @Test
    void givenFileLargerThanFiveMib_whenUploading_thenReturns413InsteadOf500()
            throws Exception {
        User admin = createUser(UserRole.ADMIN);
        byte[] content = new byte[(int) FIVE_MIB + 1];
        Arrays.fill(content, (byte) 'a');

        try {
            ResponseEntity<String> response = upload(admin, "over-five-mib.md", content);

            assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
            JsonNode body = objectMapper.readTree(response.getBody());
            assertEquals(413, body.path("code").asInt());
            assertEquals("上传文件不能超过5MB", body.path("message").asText());
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM `user` WHERE id = ?",
                    admin.getId()
            );
        }
    }

    private ResponseEntity<String> upload(
            User admin,
            String fileName,
            byte[] content
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken(admin));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        body.add("title", "Multipart 限制测试");
        body.add("category", "JAVA_COLLECTION");

        return restTemplate.postForEntity(
                ENDPOINT,
                new HttpEntity<>(body, headers),
                String.class
        );
    }

    private User createUser(UserRole role) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);
        User user = User.builder()
                .account("multipart_" + role.name().toLowerCase() + "_" + suffix)
                .password(passwordEncoder.encode("Password123!"))
                .username("Multipart测试用户")
                .email("multipart_" + suffix + "@test.com")
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
                user.getRole(),
                user.getPassword()
        );
    }
}
