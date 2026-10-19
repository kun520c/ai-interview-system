package com.kun.aiinterview.knowledge.maintenance;

import com.kun.aiinterview.knowledge.vector.milvus.MilvusProperties;
import io.milvus.orm.iterator.QueryIterator;
import io.milvus.response.QueryResultsWrapper.RowRecord;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.QueryIteratorReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.QueryResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrphanVectorMaintenanceTest {
    @TempDir Path temp;
    private final JdbcTemplate mysql = mock(JdbcTemplate.class);
    private final MilvusClientV2 milvus = mock(MilvusClientV2.class);
    private final QueryIterator iterator = mock(QueryIterator.class);
    private OrphanVectorMaintenance tool;

    @BeforeEach
    void setup() {
        MilvusProperties properties = new MilvusProperties();
        properties.setDatabaseName("default");
        properties.setCollectionName("isolated");
        tool = new OrphanVectorMaintenance(mysql, milvus, properties, new PrintStream(OutputStream.nullOutputStream()));
        when(mysql.queryForObject("SELECT DATABASE()", String.class)).thenReturn("ai_interview_test");
        when(mysql.queryForObject(contains("COUNT(*)"), eq(Long.class))).thenReturn(0L);
        when(mysql.queryForList(anyString(), eq(String.class), any(Object[].class))).thenReturn(List.of());
        when(milvus.queryIterator(any())).thenReturn(iterator);
        when(milvus.delete(any())).thenReturn(DeleteResp.builder().deleteCnt(1L).build());
        when(milvus.query(any())).thenReturn(QueryResp.builder().queryResults(List.of()).build());
    }

    @Test
    void defaultDryRunScansMultiplePagesAndNeverDeletesExistingIds() throws Exception {
        when(iterator.next()).thenReturn(List.of(row("owned"), row("orphan-1")),
                List.of(row("orphan-2")), List.of());
        when(mysql.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of("owned"), List.of());
        var result = tool.run(options(false, "dry.tsv"));
        assertThat(result).isEqualTo(new OrphanVectorMaintenance.Summary(3, 2, 0));
        verify(milvus, never()).delete(any());
        verify(milvus, never()).query(any());
        assertThat(Files.readString(temp.resolve("dry.tsv")))
                .contains("CANDIDATE\torphan-1", "CANDIDATE\torphan-2").doesNotContain("CANDIDATE\towned");
        var request = ArgumentCaptor.forClass(QueryIteratorReq.class);
        verify(milvus).queryIterator(request.capture());
        assertThat(request.getValue().getConsistencyLevel()).isEqualTo(ConsistencyLevel.STRONG);
        assertThat(request.getValue().getLimit()).isEqualTo(-1);
        assertThat(request.getValue().getBatchSize()).isEqualTo(2);
        assertThat(request.getValue().isIgnoreGrowing()).isFalse();
        assertThat(request.getValue().getOutputFields()).containsExactly("vector_id");
        verify(iterator).close();
    }

    @Test
    void explicitCleanupRechecksMysqlAndOnlyDeletesExactOrphans() throws Exception {
        when(iterator.next()).thenReturn(List.of(row("late-owned"), row("orphan")), List.of());
        when(mysql.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of(), List.of("late-owned"));
        var summary = tool.run(options(true, "delete.tsv"));
        assertThat(summary.deleted()).isEqualTo(1);
        var request = ArgumentCaptor.forClass(DeleteReq.class);
        verify(milvus).delete(request.capture());
        assertThat(request.getValue().getDatabaseName()).isEqualTo("default");
        assertThat(request.getValue().getCollectionName()).isEqualTo("isolated");
        assertThat(request.getValue().getFilter()).isEqualTo("vector_id in {vectorIds}");
        assertThat(request.getValue().getFilterTemplateValues().get("vectorIds")).isEqualTo(List.of("orphan"));
        var query = ArgumentCaptor.forClass(QueryReq.class);
        verify(milvus).query(query.capture());
        assertThat(query.getValue().getConsistencyLevel()).isEqualTo(ConsistencyLevel.STRONG);
        assertThat(Files.readString(temp.resolve("delete.tsv")))
                .contains("PRESERVED\tlate-owned", "DELETED_VERIFIED\torphan");
    }

    @Test
    void partialDeletionFailureKeepsCandidatesAndRerunRescansSafely() throws Exception {
        when(iterator.next()).thenReturn(List.of(row("a"), row("b")), List.of(row("c")), List.of());
        when(milvus.delete(any())).thenReturn(DeleteResp.builder().deleteCnt(2L).build())
                .thenThrow(new IllegalStateException("timeout after partial write"));
        assertThatThrownBy(() -> tool.run(options(true, "failed.tsv")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cleanup incomplete");
        String failed = Files.readString(temp.resolve("failed.tsv"));
        assertThat(failed).contains("CANDIDATE\tc", "DELETED_VERIFIED\ta",
                "DELETED_VERIFIED\tb", "UNCONFIRMED\tc").doesNotContain("COMPLETE\t3\t3\t3");
        when(iterator.next()).thenReturn(List.of(row("c")), List.of());
        doReturn(DeleteResp.builder().deleteCnt(1L).build()).when(milvus).delete(any());
        assertThat(tool.run(options(true, "retry.tsv")).deleted()).isEqualTo(1);
        assertThat(Files.readString(temp.resolve("failed.tsv"))).isEqualTo(failed);
    }

    @Test
    void unconfirmedStrongReadMustNotRecordSuccess() throws Exception {
        when(iterator.next()).thenReturn(List.of(row("a")), List.of());
        when(milvus.query(any())).thenReturn(null);
        assertThatThrownBy(() -> tool.run(options(true, "unknown.tsv"))).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(temp.resolve("unknown.tsv")))
                .contains("UNCONFIRMED\ta").doesNotContain("DELETED_VERIFIED");
    }

    @Test
    void incompleteScanNeverStartsDeletionAndClosesIterator() {
        when(iterator.next()).thenReturn(List.of(row("a"))).thenThrow(new IllegalStateException("read failed"));
        assertThatThrownBy(() -> tool.run(options(true, "scan-failed.tsv"))).isInstanceOf(IllegalStateException.class);
        verify(milvus, never()).delete(any());
        verify(iterator).close();
    }

    @Test
    void processingOrWrongTargetPreventsAnyMilvusAccess() {
        when(mysql.queryForObject(contains("COUNT(*)"), eq(Long.class))).thenReturn(1L);
        assertThatThrownBy(() -> tool.run(options(true, "busy.tsv"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tool.run(new OrphanVectorMaintenance.Options(true, true, true,
                "wrong/default/isolated", 2, temp.resolve("wrong.tsv")))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(milvus);
    }

    @Test
    void zeroProcessingIsInsufficientWithoutBothOperationalConfirmations() {
        assertThatThrownBy(() -> new OrphanVectorMaintenance.Options(true, false, true,
                "db/default/c", 500, temp.resolve("bad"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrphanVectorMaintenance.Options(true, true, false,
                "db/default/c", 500, temp.resolve("bad"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesToOverwriteAnEarlierJournal() throws Exception {
        Files.writeString(temp.resolve("previous.tsv"), "original evidence");
        assertThatThrownBy(() -> tool.run(options(true, "previous.tsv")))
                .isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
        assertThat(Files.readString(temp.resolve("previous.tsv"))).isEqualTo("original evidence");
        verifyNoInteractions(milvus);
    }

    @Test
    void scansBeyondOffsetLimitWithBoundedBatches() throws Exception {
        var pageNumber = new java.util.concurrent.atomic.AtomicInteger();
        when(iterator.next()).thenAnswer(call -> {
            int page = pageNumber.getAndIncrement();
            if (page == 20) {
                return List.of();
            }
            return java.util.stream.IntStream.range(0, 1000)
                    .mapToObj(i -> row("owned-" + page + "-" + i)).toList();
        });
        when(mysql.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenAnswer(call -> java.util.Arrays.stream(call.getArguments()).skip(2)
                        .map(String.class::cast).toList());
        var summary = tool.run(new OrphanVectorMaintenance.Options(false, true, true,
                "ai_interview_test/default/isolated", 1000, temp.resolve("large.tsv")));
        assertThat(summary.scanned()).isEqualTo(20000);
        assertThat(summary.candidates()).isZero();
        verify(milvus, never()).delete(any());
        verify(iterator, times(21)).next();
    }

    private OrphanVectorMaintenance.Options options(boolean delete, String file) {
        return new OrphanVectorMaintenance.Options(delete, true, true,
                "ai_interview_test/default/isolated", 2, temp.resolve(file));
    }

    private RowRecord row(String id) {
        RowRecord row = new RowRecord();
        row.put("vector_id", id);
        return row;
    }
}
