package com.kun.aiinterview.common.recovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the exact migration on connection-local historical tables in the isolated test catalog. */
@SpringBootTest
@ActiveProfiles("test")
class TimeoutAttemptFencingMigrationIntegrationTest {
    @Autowired private DataSource dataSource;

    @Test
    void migrationPreservesHistoricalPayloadsAndAllLifecycleStates() throws Exception {
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            assertThat(connection.getCatalog()).isEqualTo("ai_interview_test");
            // Temporary tables shadow only this connection. Keep the real column/index definitions;
            // omit constraints because connection-local copies have no parent tables.
            createTemporaryCopy(connection, "interview_answer");
            createTemporaryCopy(connection, "knowledge_document");
            try {
                sql.execute("ALTER TABLE interview_answer DROP COLUMN attempt_id");
                sql.execute("ALTER TABLE knowledge_document DROP COLUMN processing_attempt_id");
                var answerStates = List.of("SUBMITTED", "EVALUATING", "EVALUATED", "FAILED");
                var documentStates = List.of("UPLOADED", "PROCESSING", "READY", "FAILED");
                for (int index = 0; index < 4; index++) {
                    sql.executeUpdate("INSERT INTO interview_answer (id, interview_question_id, answer_content, status, request_id) "
                            + "VALUES (" + (index + 1) + "," + (index + 1) + ",'historical answer','"
                            + answerStates.get(index) + "','historical-" + index + "')");
                    sql.executeUpdate("INSERT INTO knowledge_document (id,title,category,file_name,file_type,content,content_hash,"
                            + "document_version,processing_status) VALUES (" + (index + 1)
                            + ",'historical title','JAVA_COLLECTION','historical.md','MARKDOWN','historical evidence','"
                            + "a".repeat(64) + "',1,'" + documentStates.get(index) + "')");
                }
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                        Path.of("sql/migrations/20261015_timeout_attempt_fencing.sql")));
                try (var rows = sql.executeQuery("SELECT status,answer_content,attempt_id FROM interview_answer ORDER BY id")) {
                    for (String state : answerStates) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isEqualTo(state);
                        assertThat(rows.getString(2)).isEqualTo("historical answer");
                        assertThat(rows.getString(3)).isNull();
                    }
                    assertThat(rows.next()).isFalse();
                }
                try (var rows = sql.executeQuery("SELECT processing_status,content,processing_attempt_id "
                        + "FROM knowledge_document ORDER BY id")) {
                    for (String state : documentStates) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isEqualTo(state);
                        assertThat(rows.getString(2)).isEqualTo("historical evidence");
                        assertThat(rows.getString(3)).isNull();
                    }
                    assertThat(rows.next()).isFalse();
                }
                assertThat(sql.executeUpdate("UPDATE interview_answer SET attempt_id='new-attempt',status='EVALUATING' "
                        + "WHERE status='EVALUATING' AND attempt_id IS NULL")).isEqualTo(1);
                assertThat(sql.executeUpdate("UPDATE knowledge_document SET processing_attempt_id='new-attempt' "
                        + "WHERE processing_status='PROCESSING' AND processing_attempt_id IS NULL")).isEqualTo(1);
            } finally {
                // TEMPORARY is deliberate: cleanup can never drop the permanent application tables.
                sql.execute("DROP TEMPORARY TABLE IF EXISTS knowledge_document");
                sql.execute("DROP TEMPORARY TABLE IF EXISTS interview_answer");
            }
        }
    }

    private void createTemporaryCopy(java.sql.Connection connection, String table) throws Exception {
        String definition;
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SHOW CREATE TABLE " + table)) {
            assertThat(rows.next()).isTrue();
            definition = rows.getString(2).replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE")
                    .replaceAll("(?m)^\\s*CONSTRAINT .*\\R", "")
                    .replaceAll(",\\R\\) ENGINE", "\n) ENGINE");
        }
        try (var statement = connection.createStatement()) { statement.execute(definition); }
    }
}
