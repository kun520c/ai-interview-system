package com.kun.aiinterview.knowledge.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.knowledge.service.KnowledgeDocumentProcessingService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Transactional
class AdminKnowledgeDocumentProcessApiTest {

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

    @MockitoBean
    private KnowledgeDocumentProcessingService knowledgeDocumentProcessingService;

    @Test
    void givenUploadedDocument_whenAdminProcesses_thenInvokesExistingProcessDocument()
            throws Exception {
        User admin = createUser(UserRole.ADMIN);
        String token = accessToken(admin);

        String responseBody = mockMvc.perform(multipart(ENDPOINT)
                        .file(file(
                                "HashMap.md",
                                "# HashMap\n正文".getBytes(StandardCharsets.UTF_8)
                        ))
                        .param("title", "HashMap 原理")
                        .param("category", "JAVA_COLLECTION")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.processingStatus").value("UPLOADED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        long documentId = response.path("data").path("documentId").asLong();
        assertTrue(documentId > 0);

        mockMvc.perform(post(ENDPOINT + "/" + documentId + "/process")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(knowledgeDocumentProcessingService).processDocument(documentId);
    }

    @Test
    void givenAdmin_whenProcessingExistingId_thenInvokesProcessDocument()
            throws Exception {
        User admin = createUser(UserRole.ADMIN);

        mockMvc.perform(post(ENDPOINT + "/88/process")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken(admin)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(knowledgeDocumentProcessingService).processDocument(88L);
    }

    @Test
    void givenDatabaseUser_whenProcessing_thenDoesNotInvokeProcessDocument()
            throws Exception {
        User user = createUser(UserRole.USER);

        mockMvc.perform(post(ENDPOINT + "/88/process")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken(user)
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(knowledgeDocumentProcessingService);
    }

    @Test
    void givenNoToken_whenProcessing_thenDoesNotInvokeProcessDocument()
            throws Exception {
        mockMvc.perform(post(ENDPOINT + "/88/process"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(knowledgeDocumentProcessingService);
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
                .account("process_" + role.name().toLowerCase() + "_" + suffix)
                .password(passwordEncoder.encode("Password123!"))
                .username("知识处理测试用户")
                .email("process_" + suffix + "@test.com")
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
}
