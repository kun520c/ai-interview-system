package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.knowledge.dto.UploadKnowledgeDocumentRequest;
import com.kun.aiinterview.knowledge.entity.KnowledgeDocument;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.enums.KnowledgeFileType;
import com.kun.aiinterview.knowledge.enums.KnowledgeProcessingStatus;
import com.kun.aiinterview.knowledge.mapper.KnowledgeDocumentMapper;
import com.kun.aiinterview.knowledge.vo.UploadKnowledgeDocumentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentAdminServiceTest {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @InjectMocks
    private KnowledgeDocumentAdminService knowledgeDocumentAdminService;

    @ParameterizedTest(name = "{0} should map to {1}")
    @MethodSource("supportedFiles")
    void givenSupportedExtension_whenUploading_thenMapsBackendFileType(
            String fileName,
            KnowledgeFileType expectedType
    ) {
        stubSuccessfulInsert();

        UploadKnowledgeDocumentResponse response = knowledgeDocumentAdminService
                .uploadDocument(request(fileName, "正文", "标题", "来源"));

        ArgumentCaptor<KnowledgeDocument> captor =
                ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(knowledgeDocumentMapper).insertDocument(captor.capture());
        assertAll(
                () -> assertEquals(expectedType, captor.getValue().getFileType()),
                () -> assertEquals(expectedType, response.getFileType()),
                () -> assertEquals(fileName, response.getFileName())
        );
    }

    @Test
    void givenWhitespaceAroundMetadata_whenUploading_thenTrimsTitleAndSource() {
        stubSuccessfulInsert();

        knowledgeDocumentAdminService.uploadDocument(
                request("document.md", "正文", "  集合框架  ", "  Oracle 文档  ")
        );

        KnowledgeDocument document = capturedDocument();
        assertAll(
                () -> assertEquals("集合框架", document.getTitle()),
                () -> assertEquals("Oracle 文档", document.getSource())
        );
    }

    @Test
    void givenBlankSource_whenUploading_thenStoresNull() {
        stubSuccessfulInsert();

        knowledgeDocumentAdminService.uploadDocument(
                request("document.txt", "正文", "标题", " \t\r\n ")
        );

        assertNull(capturedDocument().getSource());
    }

    @ParameterizedTest(name = "{0} should be cleaned to {1}")
    @MethodSource("clientPaths")
    void givenClientPath_whenUploading_thenKeepsOnlyFinalFileName(
            String originalFileName,
            String expectedFileName
    ) {
        stubSuccessfulInsert();

        UploadKnowledgeDocumentResponse response = knowledgeDocumentAdminService
                .uploadDocument(request(originalFileName, "正文", "标题", null));

        assertAll(
                () -> assertEquals(expectedFileName, capturedDocument().getFileName()),
                () -> assertEquals(expectedFileName, response.getFileName())
        );
    }

    @Test
    void givenUtf8BomAndMixedNewlines_whenUploading_thenNormalizesWithoutTrimmingMarkdown() {
        stubSuccessfulInsert();
        String original = "\uFEFF# 标题\r\n\r段落  \r\n";

        knowledgeDocumentAdminService.uploadDocument(
                request("document.md", original, "标题", null)
        );

        KnowledgeDocument document = capturedDocument();
        assertEquals("# 标题\n\n段落  \n", document.getContent());
    }

    @Test
    void givenValidContent_whenUploading_thenBuildsControlledEntityAndResponse() {
        stubSuccessfulInsert();

        UploadKnowledgeDocumentResponse response = knowledgeDocumentAdminService
                .uploadDocument(
                        request(
                                "document.md",
                                "# 标题\n正文",
                                "标题",
                                "来源"
                        )
                );

        KnowledgeDocument document = capturedDocument();
        assertAll(
                () -> assertEquals(101L, response.getDocumentId()),
                () -> assertEquals("document.md", response.getFileName()),
                () -> assertEquals(KnowledgeFileType.MARKDOWN, response.getFileType()),
                () -> assertEquals(1, response.getDocumentVersion()),
                () -> assertEquals(
                        KnowledgeProcessingStatus.UPLOADED,
                        response.getProcessingStatus()
                ),
                () -> assertEquals(KnowledgeCategory.JAVA_COLLECTION, document.getCategory()),
                () -> assertEquals(1, document.getDocumentVersion()),
                () -> assertEquals(
                        KnowledgeProcessingStatus.UPLOADED,
                        document.getProcessingStatus()
                ),
                () -> assertNull(document.getErrorMessage()),
                () -> assertTrue(document.getContentHash().matches("[0-9a-f]{64}")),
                () -> assertFalse(document.getContentHash().matches(".*[A-F].*")),
                () -> assertEquals(
                        sha256("# 标题\n正文"),
                        document.getContentHash()
                )
        );
    }

    @Test
    void givenEquivalentLineEndings_whenUploading_thenProducesSameNormalizedContentAndHash() {
        AtomicLong ids = new AtomicLong(200);
        when(knowledgeDocumentMapper.insertDocument(any(KnowledgeDocument.class)))
                .thenAnswer(invocation -> {
                    KnowledgeDocument document = invocation.getArgument(0);
                    document.setId(ids.incrementAndGet());
                    return 1;
                });

        knowledgeDocumentAdminService.uploadDocument(
                request("windows.md", "第一行\r\n第二行\r\n", "标题", null)
        );
        knowledgeDocumentAdminService.uploadDocument(
                request("linux.md", "第一行\n第二行\n", "标题", null)
        );

        ArgumentCaptor<KnowledgeDocument> captor =
                ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(knowledgeDocumentMapper, org.mockito.Mockito.times(2))
                .insertDocument(captor.capture());
        KnowledgeDocument windows = captor.getAllValues().get(0);
        KnowledgeDocument linux = captor.getAllValues().get(1);
        assertAll(
                () -> assertEquals(linux.getContent(), windows.getContent()),
                () -> assertEquals(linux.getContentHash(), windows.getContentHash())
        );
    }

    @Test
    void givenEquivalentContentWithAndWithoutBom_whenUploading_thenProducesSameHash() {
        AtomicLong ids = new AtomicLong(300);
        when(knowledgeDocumentMapper.insertDocument(any(KnowledgeDocument.class)))
                .thenAnswer(invocation -> {
                    KnowledgeDocument document = invocation.getArgument(0);
                    document.setId(ids.incrementAndGet());
                    return 1;
                });

        knowledgeDocumentAdminService.uploadDocument(
                request("with-bom.txt", "\uFEFF相同正文", "标题", null)
        );
        knowledgeDocumentAdminService.uploadDocument(
                request("without-bom.txt", "相同正文", "标题", null)
        );

        ArgumentCaptor<KnowledgeDocument> captor =
                ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(knowledgeDocumentMapper, org.mockito.Mockito.times(2))
                .insertDocument(captor.capture());
        assertEquals(
                captor.getAllValues().get(0).getContentHash(),
                captor.getAllValues().get(1).getContentHash()
        );
    }

    @Test
    void givenNullRequest_whenUploading_thenRejectsBeforeMapperCall() {
        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(null)
        );

        verifyNoInteractions(knowledgeDocumentMapper);
    }

    @Test
    void givenNullFile_whenUploading_thenRejectsBeforeMapperCall() {
        UploadKnowledgeDocumentRequest request = baseRequest();
        request.setFile(null);

        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(request)
        );

        verifyNoInteractions(knowledgeDocumentMapper);
    }

    @Test
    void givenEmptyFile_whenUploading_thenRejectsBeforeReadingOrMapperCall()
            throws IOException {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);
        UploadKnowledgeDocumentRequest request = baseRequest();
        request.setFile(file);

        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(request)
        );

        verify(file, never()).getBytes();
        verifyNoInteractions(knowledgeDocumentMapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " \t\r\n "})
    void givenMissingOrBlankTitle_whenUploading_thenRejectsBeforeMapperCall(String title) {
        UploadKnowledgeDocumentRequest request =
                request("document.md", "正文", title, null);

        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(request)
        );

        verifyNoInteractions(knowledgeDocumentMapper);
    }

    @Test
    void givenNullCategory_whenUploading_thenRejectsBeforeMapperCall() {
        UploadKnowledgeDocumentRequest request = baseRequest();
        request.setCategory(null);

        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(request)
        );

        verifyNoInteractions(knowledgeDocumentMapper);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oversizedMetadata")
    void givenOversizedMetadata_whenUploading_thenRejectsBeforeMapperCall(
            String scenario,
            String fileName,
            String title,
            String source
    ) {
        UploadKnowledgeDocumentRequest request =
                request(fileName, "正文", title, source);

        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(request),
                scenario
        );

        verifyNoInteractions(knowledgeDocumentMapper);
    }

    @Test
    void givenFileLargerThanFiveMegabytes_whenUploading_thenRejectsBeforeReadingBytes()
            throws IOException {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.getSize()).thenReturn(MAX_FILE_SIZE + 1);
        UploadKnowledgeDocumentRequest request = baseRequest();
        request.setFile(file);

        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(request)
        );

        verify(file, never()).getBytes();
        verifyNoInteractions(knowledgeDocumentMapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " \t "})
    void givenMissingOriginalFileName_whenUploading_thenRejectsBeforeMapperCall(
            String originalFileName
    ) {
        UploadKnowledgeDocumentRequest request = request(
                originalFileName,
                "text/plain",
                "正文".getBytes(StandardCharsets.UTF_8),
                "标题",
                null
        );

        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(request)
        );

        verifyNoInteractions(knowledgeDocumentMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "\0document.md"})
    void givenUnusableOriginalFileName_whenUploading_thenRejectsAsBusinessError(
            String originalFileName
    ) {
        UploadKnowledgeDocumentRequest request = request(
                originalFileName,
                "正文",
                "标题",
                null
        );

        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(request)
        );

        verifyNoInteractions(knowledgeDocumentMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"document.pdf", "document.docx"})
    void givenUnsupportedExtension_whenUploading_thenRejectsBeforeMapperCall(
            String fileName
    ) {
        UploadKnowledgeDocumentRequest request = request(
                fileName,
                "text/plain",
                "正文".getBytes(StandardCharsets.UTF_8),
                "标题",
                null
        );

        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(request)
        );

        verifyNoInteractions(knowledgeDocumentMapper);
    }

    @Test
    void givenSpoofedMarkdownContentTypeAndIllegalExtension_whenUploading_thenRejects() {
        UploadKnowledgeDocumentRequest request = request(
                "document.exe",
                "text/markdown",
                "正文".getBytes(StandardCharsets.UTF_8),
                "标题",
                null
        );

        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(request)
        );

        verifyNoInteractions(knowledgeDocumentMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \t\r\n "})
    void givenEmptyOrBlankContent_whenUploading_thenRejectsBeforeMapperCall(
            String content
    ) {
        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(
                        request("document.txt", content, "标题", null)
                )
        );

        verifyNoInteractions(knowledgeDocumentMapper);
    }

    @Test
    void givenMalformedUtf8_whenUploading_thenRejectsBeforeMapperCall() {
        byte[] malformedUtf8 = {(byte) 0xC3, (byte) 0x28};

        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(
                        request("document.txt", malformedUtf8, "标题", null)
                )
        );

        verifyNoInteractions(knowledgeDocumentMapper);
    }

    @Test
    void givenFileReadFailure_whenUploading_thenRejectsBeforeMapperCall()
            throws IOException {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.getSize()).thenReturn(10L);
        when(file.getOriginalFilename()).thenReturn("document.md");
        when(file.getBytes()).thenThrow(new IOException("disk failure"));
        UploadKnowledgeDocumentRequest request = baseRequest();
        request.setFile(file);

        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(request)
        );

        verifyNoInteractions(knowledgeDocumentMapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void givenUnexpectedAffectedRows_whenUploading_thenRejects(int affectedRows) {
        when(knowledgeDocumentMapper.insertDocument(any(KnowledgeDocument.class)))
                .thenReturn(affectedRows);

        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(baseRequest())
        );
    }

    @Test
    void givenMissingGeneratedId_whenUploading_thenRejects() {
        when(knowledgeDocumentMapper.insertDocument(any(KnowledgeDocument.class)))
                .thenReturn(1);

        assertThrows(
                BusinessException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(baseRequest())
        );
    }

    @Test
    void givenMapperFailure_whenUploading_thenPropagatesOriginalException() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("database unavailable");
        when(knowledgeDocumentMapper.insertDocument(any(KnowledgeDocument.class)))
                .thenThrow(failure);

        DataAccessResourceFailureException actual = assertThrows(
                DataAccessResourceFailureException.class,
                () -> knowledgeDocumentAdminService.uploadDocument(baseRequest())
        );

        assertSame(failure, actual);
    }

    private static Stream<Arguments> supportedFiles() {
        return Stream.of(
                Arguments.of("document.md", KnowledgeFileType.MARKDOWN),
                Arguments.of("document.markdown", KnowledgeFileType.MARKDOWN),
                Arguments.of("document.txt", KnowledgeFileType.TEXT),
                Arguments.of("DOCUMENT.MD", KnowledgeFileType.MARKDOWN),
                Arguments.of("DOCUMENT.MARKDOWN", KnowledgeFileType.MARKDOWN),
                Arguments.of("DOCUMENT.TXT", KnowledgeFileType.TEXT)
        );
    }

    private static Stream<Arguments> clientPaths() {
        return Stream.of(
                Arguments.of("HashMap.md", "HashMap.md"),
                Arguments.of("C:\\fakepath\\HashMap.md", "HashMap.md"),
                Arguments.of("C:\\Users\\developer\\HashMap.md", "HashMap.md"),
                Arguments.of("folder/HashMap.md", "HashMap.md")
        );
    }

    private static Stream<Arguments> oversizedMetadata() {
        return Stream.of(
                Arguments.of(
                        "title longer than 200 characters",
                        "document.md",
                        "题".repeat(201),
                        null
                ),
                Arguments.of(
                        "source longer than 255 characters",
                        "document.md",
                        "标题",
                        "来".repeat(256)
                ),
                Arguments.of(
                        "file name longer than 255 characters",
                        "a".repeat(253) + ".md",
                        "标题",
                        null
                )
        );
    }

    private void stubSuccessfulInsert() {
        when(knowledgeDocumentMapper.insertDocument(any(KnowledgeDocument.class)))
                .thenAnswer(invocation -> {
                    KnowledgeDocument document = invocation.getArgument(0);
                    document.setId(101L);
                    return 1;
                });
    }

    private KnowledgeDocument capturedDocument() {
        ArgumentCaptor<KnowledgeDocument> captor =
                ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(knowledgeDocumentMapper).insertDocument(captor.capture());
        return captor.getValue();
    }

    private UploadKnowledgeDocumentRequest baseRequest() {
        return request("document.md", "正文", "标题", null);
    }

    private UploadKnowledgeDocumentRequest request(
            String originalFileName,
            String content,
            String title,
            String source
    ) {
        return request(
                originalFileName,
                "application/octet-stream",
                content.getBytes(StandardCharsets.UTF_8),
                title,
                source
        );
    }

    private UploadKnowledgeDocumentRequest request(
            String originalFileName,
            byte[] content,
            String title,
            String source
    ) {
        return request(
                originalFileName,
                "application/octet-stream",
                content,
                title,
                source
        );
    }

    private UploadKnowledgeDocumentRequest request(
            String originalFileName,
            String contentType,
            byte[] content,
            String title,
            String source
    ) {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                originalFileName,
                contentType,
                content
        );
        return UploadKnowledgeDocumentRequest.builder()
                .file(file)
                .title(title)
                .category(KnowledgeCategory.JAVA_COLLECTION)
                .source(source)
                .build();
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
