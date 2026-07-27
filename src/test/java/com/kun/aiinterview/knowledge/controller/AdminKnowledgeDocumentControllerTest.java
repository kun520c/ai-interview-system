package com.kun.aiinterview.knowledge.controller;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.knowledge.dto.UploadKnowledgeDocumentRequest;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.enums.KnowledgeFileType;
import com.kun.aiinterview.knowledge.enums.KnowledgeProcessingStatus;
import com.kun.aiinterview.knowledge.service.KnowledgeDocumentAdminService;
import com.kun.aiinterview.knowledge.vo.UploadKnowledgeDocumentResponse;
import com.kun.aiinterview.security.config.SecurityConfiguration;
import com.kun.aiinterview.security.filter.JwtAuthenticationFilter;
import com.kun.aiinterview.security.handler.RestAccessDeniedHandler;
import com.kun.aiinterview.security.handler.RestAuthenticationEntryPoint;
import com.kun.aiinterview.security.jwt.JwtTokenService;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminKnowledgeDocumentController.class)
@Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class AdminKnowledgeDocumentControllerTest {

    private static final String ENDPOINT = "/api/admin/knowledge/documents";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeDocumentAdminService knowledgeDocumentAdminService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserMapper userMapper;

    @BeforeEach
    void stubSuccessfulUpload() {
        when(knowledgeDocumentAdminService.uploadDocument(
                any(UploadKnowledgeDocumentRequest.class)
        )).thenReturn(
                UploadKnowledgeDocumentResponse.builder()
                        .documentId(88L)
                        .fileName("HashMap.md")
                        .fileType(KnowledgeFileType.MARKDOWN)
                        .documentVersion(1)
                        .processingStatus(KnowledgeProcessingStatus.UPLOADED)
                        .build()
        );
    }

    @Test
    void givenNoToken_whenUploading_thenReturnsUnifiedUnauthorizedJson()
            throws Exception {
        mockMvc.perform(validMultipartRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(knowledgeDocumentAdminService);
    }

    @Test
    void givenDatabaseUserRole_whenUploading_thenReturnsForbidden()
            throws Exception {
        stubTokenUser("knowledge-user-token", UserRole.USER);

        mockMvc.perform(validMultipartRequest()
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer knowledge-user-token"
                        ))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(knowledgeDocumentAdminService);
    }

    @Test
    void givenAdminAndValidMultipart_whenUploading_thenPassesDtoAndReturnsSafeResult()
            throws Exception {
        stubTokenUser("knowledge-admin-token", UserRole.ADMIN);

        mockMvc.perform(validMultipartRequest()
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer knowledge-admin-token"
                        ))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.documentId").value(88))
                .andExpect(jsonPath("$.data.fileName").value("HashMap.md"))
                .andExpect(jsonPath("$.data.fileType").value("MARKDOWN"))
                .andExpect(jsonPath("$.data.documentVersion").value(1))
                .andExpect(jsonPath("$.data.processingStatus").value("UPLOADED"))
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andExpect(jsonPath("$.data.contentHash").doesNotExist())
                .andExpect(jsonPath("$.data.errorMessage").doesNotExist());

        ArgumentCaptor<UploadKnowledgeDocumentRequest> captor =
                ArgumentCaptor.forClass(UploadKnowledgeDocumentRequest.class);
        verify(knowledgeDocumentAdminService).uploadDocument(captor.capture());
        UploadKnowledgeDocumentRequest request = captor.getValue();
        assertAll(
                () -> assertEquals("HashMap.md", request.getFile().getOriginalFilename()),
                () -> assertEquals("  HashMap 原理  ", request.getTitle()),
                () -> assertEquals(KnowledgeCategory.JAVA_COLLECTION, request.getCategory()),
                () -> assertEquals("  官方文档  ", request.getSource())
        );
    }

    @Test
    void givenAdminAndMissingSource_whenUploading_thenStillCallsService()
            throws Exception {
        stubTokenUser("source-optional-admin-token", UserRole.ADMIN);

        mockMvc.perform(multipart(ENDPOINT)
                        .file(markdownFile())
                        .param("title", "HashMap 原理")
                        .param("category", "JAVA_COLLECTION")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer source-optional-admin-token"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<UploadKnowledgeDocumentRequest> captor =
                ArgumentCaptor.forClass(UploadKnowledgeDocumentRequest.class);
        verify(knowledgeDocumentAdminService).uploadDocument(captor.capture());
        assertNull(captor.getValue().getSource());
    }

    @Test
    void givenAdminAndMissingFile_whenUploading_thenReturnsBadRequest()
            throws Exception {
        stubTokenUser("missing-file-admin-token", UserRole.ADMIN);

        mockMvc.perform(multipart(ENDPOINT)
                        .param("title", "HashMap 原理")
                        .param("category", "JAVA_COLLECTION")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer missing-file-admin-token"
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(knowledgeDocumentAdminService);
    }

    @Test
    void givenAdminAndEmptyFile_whenUploading_thenReturnsBadRequest()
            throws Exception {
        stubTokenUser("empty-file-admin-token", UserRole.ADMIN);
        when(knowledgeDocumentAdminService.uploadDocument(argThat(
                request -> request.getFile() != null && request.getFile().isEmpty()
        ))).thenThrow(new BusinessException("上传文件不能为空"));
        MockMultipartFile empty = new MockMultipartFile(
                "file",
                "empty.md",
                "text/markdown",
                new byte[0]
        );

        mockMvc.perform(multipart(ENDPOINT)
                        .file(empty)
                        .param("title", "HashMap 原理")
                        .param("category", "JAVA_COLLECTION")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer empty-file-admin-token"
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("上传文件不能为空"));

        verify(knowledgeDocumentAdminService).uploadDocument(argThat(
                request -> request.getFile() != null && request.getFile().isEmpty()
        ));
    }

    @Test
    void givenAdminAndMissingTitle_whenUploading_thenReturnsBadRequest()
            throws Exception {
        stubTokenUser("missing-title-admin-token", UserRole.ADMIN);

        mockMvc.perform(multipart(ENDPOINT)
                        .file(markdownFile())
                        .param("category", "JAVA_COLLECTION")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer missing-title-admin-token"
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(knowledgeDocumentAdminService);
    }

    @Test
    void givenAdminAndBlankTitle_whenUploading_thenReturnsBadRequest()
            throws Exception {
        stubTokenUser("blank-title-admin-token", UserRole.ADMIN);

        mockMvc.perform(multipart(ENDPOINT)
                        .file(markdownFile())
                        .param("title", " \t ")
                        .param("category", "JAVA_COLLECTION")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer blank-title-admin-token"
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(knowledgeDocumentAdminService);
    }

    @Test
    void givenAdminAndMissingCategory_whenUploading_thenReturnsBadRequest()
            throws Exception {
        stubTokenUser("missing-category-admin-token", UserRole.ADMIN);

        mockMvc.perform(multipart(ENDPOINT)
                        .file(markdownFile())
                        .param("title", "HashMap 原理")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer missing-category-admin-token"
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(knowledgeDocumentAdminService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "java_collection"})
    void givenAdminAndInvalidCategory_whenUploading_thenReturnsBadRequest(
            String category
    ) throws Exception {
        stubTokenUser("invalid-category-admin-token", UserRole.ADMIN);

        mockMvc.perform(multipart(ENDPOINT)
                        .file(markdownFile())
                        .param("title", "HashMap 原理")
                        .param("category", category)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer invalid-category-admin-token"
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(knowledgeDocumentAdminService);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            validMultipartRequest() {
        return multipart(ENDPOINT)
                .file(markdownFile())
                .param("title", "  HashMap 原理  ")
                .param("category", "JAVA_COLLECTION")
                .param("source", "  官方文档  ");
    }

    private MockMultipartFile markdownFile() {
        return new MockMultipartFile(
                "file",
                "HashMap.md",
                "text/markdown",
                "# HashMap\n正文".getBytes(StandardCharsets.UTF_8)
        );
    }

    private void stubTokenUser(String token, UserRole role) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1001");
        when(jwtTokenService.parseAndValidate(token)).thenReturn(claims);
        when(userMapper.getUserById(1001L)).thenReturn(
                User.builder()
                        .id(1001L)
                        .account("database-account")
                        .username("数据库用户")
                        .role(role)
                        .status(UserStatus.ENABLED)
                        .build()
        );
    }
}
