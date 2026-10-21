package com.kun.aiinterview.knowledge.maintenance;

import com.kun.aiinterview.knowledge.vector.milvus.MilvusProperties;
import io.milvus.orm.iterator.QueryIterator;
import io.milvus.response.QueryResultsWrapper.RowRecord;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.QueryIteratorReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.QueryReq;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/** Explicit CLI only: no Spring bean, application bootstrap, scheduler or HTTP entry point. */
public class OrphanVectorMaintenance {
    private static final String VECTOR_ID_FIELD = "vector_id";
    private final JdbcTemplate mysql;
    private final MilvusClientV2 milvus;
    private final MilvusProperties properties;
    private final PrintStream output;

    public OrphanVectorMaintenance(JdbcTemplate mysql, MilvusClientV2 milvus,
            MilvusProperties properties, PrintStream output) {
        this.mysql = mysql;
        this.milvus = milvus;
        this.properties = properties;
        this.output = output;
    }

    public record Options(boolean delete, boolean writersDrained, boolean oldInstancesStopped,
            String expectedTarget, int batchSize, Path journal) {
        public Options {
            if (!writersDrained || !oldInstancesStopped) {
                throw new IllegalArgumentException(
                        "Confirm writers drained AND old instances stopped; PROCESSING=0 alone is unsafe");
            }
            if (expectedTarget == null || expectedTarget.isBlank() || journal == null
                    || batchSize < 1 || batchSize > 1000) {
                throw new IllegalArgumentException("Require target, new journal path and batch size 1..1000");
            }
        }
    }

    public record Summary(long scanned, long candidates, long deleted) { }

    public Summary run(Options options) throws IOException {
        String schema = mysql.queryForObject("SELECT DATABASE()", String.class);
        String target = schema + "/" + properties.getDatabaseName() + "/" + properties.getCollectionName();
        if (!target.equals(options.expectedTarget())) {
            throw new IllegalArgumentException("Maintenance target does not match explicit confirmation");
        }
        requireNoProcessing();

        long scanned = 0;
        long candidates = 0;
        long deleted = 0;
        // CREATE_NEW prevents overwriting the evidence of an interrupted run.
        try (Journal journal = new Journal(options.journal())) {
            journal.write((options.delete() ? "DELETE" : "DRY_RUN") + "\t" + target);
            QueryIterator iterator = milvus.queryIterator(QueryIteratorReq.builder()
                    .databaseName(properties.getDatabaseName()).collectionName(properties.getCollectionName())
                    .outputFields(List.of(VECTOR_ID_FIELD)).expr("").limit(-1)
                    .batchSize(options.batchSize()).ignoreGrowing(false)
                    .consistencyLevel(ConsistencyLevel.STRONG).build());

            try {
                while (true) {
                    List<RowRecord> page = iterator.next();
                    if (page == null) {
                        throw new IllegalStateException("Null iterator page; scan is incomplete");
                    }
                    if (page.isEmpty()) {
                        break;
                    }

                    requireNoProcessing();
                    List<String> ids = page.stream().map(row -> checkedId(row.get(VECTOR_ID_FIELD)))
                            .distinct().toList();
                    scanned += page.size();

                    Set<String> existing = existingIds(ids);
                    for (String id : ids) {
                        if (!existing.contains(id)) {
                            journal.write("CANDIDATE\t" + id);
                            output.println("CANDIDATE\t" + id);
                            candidates++;
                        }
                    }
                }
            } finally {
                iterator.close();
            }

            journal.write("SCAN_COMPLETE\t" + scanned + "\t" + candidates);
            // Finish scanning before deleting, so mutation cannot disturb the iterator.
            if (options.delete()) {
                try (var reader = Files.newBufferedReader(options.journal(), StandardCharsets.UTF_8)) {
                    reader.readLine(); // target header
                    for (long read = 0; read < candidates;) {
                        List<String> batch = new ArrayList<>();
                        for (int i = 0; i < options.batchSize() && read < candidates; i++, read++) {
                            String line = reader.readLine();
                            if (line == null || !line.startsWith("CANDIDATE\t")) {
                                throw new IOException("Incomplete candidate journal");
                            }
                            batch.add(checkedId(line.substring("CANDIDATE\t".length())));
                        }

                        requireNoProcessing();
                        Set<String> nowExisting = existingIds(batch);
                        List<String> orphanIds = batch.stream().filter(id -> !nowExisting.contains(id))
                                .distinct().toList();
                        for (String id : nowExisting) {
                            journal.write("PRESERVED\t" + id);
                        }
                        if (orphanIds.isEmpty()) {
                            continue;
                        }

                        try {
                            var deletion = milvus.delete(DeleteReq.builder()
                                    .databaseName(properties.getDatabaseName())
                                    .collectionName(properties.getCollectionName())
                                    .filter("vector_id in {vectorIds}")
                                    .filterTemplateValues(Map.of("vectorIds", orphanIds)).build());
                            if (deletion == null || deletion.getDeleteCnt() < 0) {
                                throw new IllegalStateException("Invalid deletion response");
                            }

                            var remaining = milvus.query(QueryReq.builder()
                                    .databaseName(properties.getDatabaseName())
                                    .collectionName(properties.getCollectionName())
                                    .ids(new ArrayList<Object>(orphanIds))
                                    .outputFields(List.of(VECTOR_ID_FIELD))
                                    .consistencyLevel(ConsistencyLevel.STRONG).build());
                            if (remaining == null || remaining.getQueryResults() == null
                                    || !remaining.getQueryResults().isEmpty()) {
                                throw new IllegalStateException("Deletion not confirmed by strong read");
                            }

                            for (String id : orphanIds) {
                                journal.write("DELETED_VERIFIED\t" + id);
                            }
                            deleted += orphanIds.size();
                        } catch (RuntimeException failure) {
                            for (String id : orphanIds) {
                                journal.write("UNCONFIRMED\t" + id);
                            }
                            throw new IllegalStateException(
                                    "Cleanup incomplete; retain journal and rescan into a NEW journal", failure);
                        }
                    }
                }
            }

            journal.write("COMPLETE\t" + scanned + "\t" + candidates + "\t" + deleted);
        }

        Summary summary = new Summary(scanned, candidates, deleted);
        output.println(summary);
        return summary;
    }

    private Set<String> existingIds(List<String> ids) {
        // Return candidate spellings from SQL, preserving MySQL's collation semantics.
        String inputs = String.join(" UNION ALL ", java.util.Collections.nCopies(ids.size(),
                "SELECT ? AS vector_id"));
        return new HashSet<>(mysql.queryForList(
                "SELECT candidate.vector_id FROM (" + inputs + ") candidate "
                        + "WHERE EXISTS (SELECT 1 FROM knowledge_chunk c WHERE c.vector_id = candidate.vector_id)",
                String.class, ids.toArray()));
    }

    private void requireNoProcessing() {
        Long count = mysql.queryForObject(
                "SELECT COUNT(*) FROM knowledge_document WHERE processing_status = 'PROCESSING'", Long.class);
        if (count == null || count != 0) {
            throw new IllegalStateException("PROCESSING documents remain; maintenance stopped");
        }
    }

    private static String checkedId(Object value) {
        if (!(value instanceof String id) || id.isBlank() || id.length() > 64
                || id.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("Invalid vector ID; maintenance stopped");
        }
        return id;
    }

    private static final class Journal implements AutoCloseable {
        private final FileChannel channel;
        private final BufferedWriter writer;

        Journal(Path path) throws IOException {
            channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            writer = new BufferedWriter(Channels.newWriter(channel, StandardCharsets.UTF_8));
        }

        void write(String line) throws IOException {
            writer.write(line);
            writer.newLine();
            writer.flush();
            channel.force(true);
        }

        @Override
        public void close() throws IOException {
            writer.close();
        }
    }

    public static void main(String[] args) throws Exception {
        Set<String> flags = new HashSet<>(List.of(args));
        Set<String> accepted = Set.of("--delete", "--writers-drained", "--old-instances-stopped");
        if (!accepted.containsAll(flags)) {
            throw new IllegalArgumentException("Unknown CLI option");
        }

        Options options = new Options(flags.contains("--delete"), flags.contains("--writers-drained"),
                flags.contains("--old-instances-stopped"), requiredEnv("ORPHAN_EXPECTED_TARGET"),
                Integer.parseInt(System.getenv().getOrDefault("ORPHAN_BATCH_SIZE", "500")),
                Path.of(requiredEnv("ORPHAN_JOURNAL")));
        DriverManagerDataSource datasource = new DriverManagerDataSource(requiredEnv("ORPHAN_MYSQL_URL"),
                requiredEnv("ORPHAN_MYSQL_USERNAME"), requiredEnv("ORPHAN_MYSQL_PASSWORD"));

        MilvusProperties properties = new MilvusProperties();
        properties.setDatabaseName(requiredEnv("ORPHAN_MILVUS_DATABASE"));
        properties.setCollectionName(requiredEnv("ORPHAN_MILVUS_COLLECTION"));
        var config = ConnectConfig.builder().uri(requiredEnv("ORPHAN_MILVUS_URI"))
                .dbName(properties.getDatabaseName()).connectTimeoutMs(3000).rpcDeadlineMs(20000);
        String token = System.getenv("ORPHAN_MILVUS_TOKEN");
        if (token != null && !token.isBlank()) {
            config.token(token);
        }

        MilvusClientV2 client = new MilvusClientV2(config.build());
        try {
            new OrphanVectorMaintenance(new JdbcTemplate(datasource), client, properties, System.out).run(options);
        } finally {
            client.close();
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing environment variable: " + name);
        }
        return value;
    }
}
