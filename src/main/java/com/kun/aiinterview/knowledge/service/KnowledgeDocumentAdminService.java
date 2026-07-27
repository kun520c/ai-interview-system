package com.kun.aiinterview.knowledge.service;

import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.knowledge.dto.UploadKnowledgeDocumentRequest;
import com.kun.aiinterview.knowledge.entity.KnowledgeDocument;
import com.kun.aiinterview.knowledge.enums.KnowledgeFileType;
import com.kun.aiinterview.knowledge.enums.KnowledgeProcessingStatus;
import com.kun.aiinterview.knowledge.mapper.KnowledgeDocumentMapper;
import com.kun.aiinterview.knowledge.vo.UploadKnowledgeDocumentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class KnowledgeDocumentAdminService {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int MAX_SOURCE_LENGTH = 255;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    public UploadKnowledgeDocumentResponse uploadDocument(
            UploadKnowledgeDocumentRequest request
    ) {
        if (request == null) {
            throw new BusinessException("上传文件不能为空");
        }

        MultipartFile file = request.getFile();

        if (file == null) {
            throw new BusinessException("文件不能为空");
        }

        if (file.isEmpty()) {
            throw new BusinessException("不能为空文件");
        }

        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BusinessException("文档标题不能为空");
        }

        if (request.getCategory() == null) {
            throw new BusinessException("知识领域不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("上传文件不能超过5MB");
        }

        String fileName = cleanFileName(file.getOriginalFilename());

        KnowledgeFileType fileType = resolveFileType(fileName);

        byte[] fileBytes = readFileBytes(file);

        String content = decodeUtf8(fileBytes);

        content = normalizeContent(content);

        String contentHash = calculateSha256(content);

        String title = request.getTitle().trim();
        validateLength(title, MAX_TITLE_LENGTH, "文档标题长度不能超过200个字符");

        String source = normalizeSource(request.getSource());

        KnowledgeDocument document = KnowledgeDocument.builder()
                .title(title)
                .category(request.getCategory())
                .fileName(fileName)
                .fileType(fileType)
                .content(content)
                .contentHash(contentHash)
                .source(source)
                .documentVersion(1)
                .processingStatus(KnowledgeProcessingStatus.UPLOADED)
                .errorMessage(null)
                .build();

        int affectedRows = knowledgeDocumentMapper.insertDocument(document);

        if (affectedRows != 1) {
            throw new BusinessException("知识文档创建失败");
        }

        if (document.getId() == null) {
            throw new BusinessException("知识文档主键回填失败");
        }

        return UploadKnowledgeDocumentResponse.builder()
                .documentId(document.getId())
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .documentVersion(document.getDocumentVersion())
                .processingStatus(document.getProcessingStatus())
                .build();
    }

    private String cleanFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException("文件名不能为空");
        }

        try {
            String normalizedPath = originalFileName.replace("\\", "/");

            if (normalizedPath.endsWith("/")) {
                throw new BusinessException("文件名不能为空");
            }

            Path fileNamePath = Path.of(normalizedPath).getFileName();
            if (fileNamePath == null) {
                throw new BusinessException("文件名不能为空");
            }

            String fileName = fileNamePath.toString().trim();
            if (fileName.isBlank()) {
                throw new BusinessException("文件名不能为空");
            }

            validateLength(
                    fileName,
                    MAX_FILE_NAME_LENGTH,
                    "文件名长度不能超过255个字符"
            );
            return fileName;
        } catch (InvalidPathException exception) {
            throw new BusinessException("文件名不合法");
        }
    }

    private KnowledgeFileType resolveFileType(String fileName) {
        String lowerCaseFileName = fileName.toLowerCase(Locale.ROOT);

        if (lowerCaseFileName.endsWith(".md")
                || lowerCaseFileName.endsWith(".markdown")) {
            return KnowledgeFileType.MARKDOWN;
        }

        if (lowerCaseFileName.endsWith(".txt")) {
            return KnowledgeFileType.TEXT;
        }

        throw new BusinessException("当前只支持Markdown和TXT文件");
    }

    private byte[] readFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException("读取文件失败");
        }
    }

    private String decodeUtf8(byte[] fileBytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(fileBytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new BusinessException("文件内容必须使用UTF-8编码");
        }
    }

    private String normalizeContent(String originalContent) {
        String normalizedContent = originalContent;

        if (normalizedContent.startsWith("\uFEFF")) {
            normalizedContent = normalizedContent.substring(1);
        }

        normalizedContent = normalizedContent.replace("\r\n", "\n")
                .replace("\r", "\n");

        if (normalizedContent.isBlank()) {
            throw new BusinessException("文档正文不能为空");
        }

        return normalizedContent;
    }

    private String calculateSha256(String content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = messageDigest.digest(
                    content.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前运行环境不支持SHA-256",
                    exception
            );
        }
    }

    private String normalizeSource(String originalSource) {
        if (originalSource == null) {
            return null;
        }

        String source = originalSource.trim();
        if (source.isEmpty()) {
            return null;
        }

        validateLength(source, MAX_SOURCE_LENGTH, "文档来源长度不能超过255个字符");
        return source;
    }

    private void validateLength(String value, int maxLength, String message) {
        if (value.length() > maxLength) {
            throw new BusinessException(message);
        }
    }
}
