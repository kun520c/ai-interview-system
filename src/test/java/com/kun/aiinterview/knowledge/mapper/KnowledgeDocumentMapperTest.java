package com.kun.aiinterview.knowledge.mapper;

import com.kun.aiinterview.knowledge.entity.KnowledgeDocument;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.enums.KnowledgeFileType;
import com.kun.aiinterview.knowledge.enums.KnowledgeProcessingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Transactional
class KnowledgeDocumentMapperTest {

    private static final String CONTENT_HASH =
            "89f4afecbf9a9e8b33d7f51d87d2e79c93d747aa76f3d84a5658e345b71680e8";

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void givenCompleteDocument_whenInserting_thenPersistsEveryColumnAndPopulatesId() {
        KnowledgeDocument document = document("官方文档");

        int affectedRows = knowledgeDocumentMapper.insertDocument(document);

        assertNotNull(document.getId());
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT title, category, file_name, file_type, content,
                       content_hash, source, document_version,
                       processing_status, error_message, created_at, updated_at
                FROM knowledge_document
                WHERE id = ?
                """,
                document.getId()
        );
        assertAll(
                () -> assertEquals(1, affectedRows),
                () -> assertEquals("HashMap 原理", row.get("title")),
                () -> assertEquals("JAVA_COLLECTION", row.get("category")),
                () -> assertEquals("HashMap.md", row.get("file_name")),
                () -> assertEquals("MARKDOWN", row.get("file_type")),
                () -> assertEquals("# HashMap\n完整原文\n", row.get("content")),
                () -> assertEquals(CONTENT_HASH, row.get("content_hash")),
                () -> assertEquals("官方文档", row.get("source")),
                () -> assertEquals(1, asInt(row.get("document_version"))),
                () -> assertEquals("UPLOADED", row.get("processing_status")),
                () -> assertNull(row.get("error_message")),
                () -> assertNotNull(row.get("created_at")),
                () -> assertNotNull(row.get("updated_at"))
        );
    }

    @Test
    void givenNullSource_whenInserting_thenDatabaseStoresNull() {
        KnowledgeDocument document = document(null);

        int affectedRows = knowledgeDocumentMapper.insertDocument(document);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT source, document_version, processing_status, error_message
                FROM knowledge_document
                WHERE id = ?
                """,
                document.getId()
        );
        assertAll(
                () -> assertEquals(1, affectedRows),
                () -> assertNotNull(document.getId()),
                () -> assertNull(row.get("source")),
                () -> assertEquals(1, asInt(row.get("document_version"))),
                () -> assertEquals("UPLOADED", row.get("processing_status")),
                () -> assertNull(row.get("error_message"))
        );
    }

    private KnowledgeDocument document(String source) {
        return KnowledgeDocument.builder()
                .title("HashMap 原理")
                .category(KnowledgeCategory.JAVA_COLLECTION)
                .fileName("HashMap.md")
                .fileType(KnowledgeFileType.MARKDOWN)
                .content("# HashMap\n完整原文\n")
                .contentHash(CONTENT_HASH)
                .source(source)
                .documentVersion(1)
                .processingStatus(KnowledgeProcessingStatus.UPLOADED)
                .errorMessage(null)
                .build();
    }

    private int asInt(Object value) {
        return ((Number) value).intValue();
    }
}
