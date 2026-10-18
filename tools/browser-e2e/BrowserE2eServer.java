import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.AiInterviewSystemApplication;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import com.kun.aiinterview.knowledge.vector.VectorStoreClient;
import com.kun.aiinterview.knowledge.vector.VectorWriteItem;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;

/** Opt-in browser harness. Never included in the production application classpath. */
public final class BrowserE2eServer {
    static final String URL = "jdbc:mysql://127.0.0.1:13326/ai_interview_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    static final Path OUT = runOutput();

    private static Path runOutput() {
        String runId = System.getenv().getOrDefault("BROWSER_E2E_RUN_ID", "20261018-v3");
        if (!runId.matches("[a-z0-9-]{1,64}")) throw new IllegalArgumentException("Invalid browser run ID");
        return Path.of("target/browser-e2e", runId);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("snapshot")) {
            try (var connection = DriverManager.getConnection(URL, "root", "")) {
                requirePrivateDatabase(connection);
                connection.setReadOnly(true);
                var jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
                var snapshot = new java.util.LinkedHashMap<String, Object>();
                for (String table : List.of("interview_session", "interview_question", "interview_answer",
                        "answer_evaluation", "rag_retrieval_batch", "rag_hit_log", "interview_report", "user_weakness")) {
                    String key = table.equals("rag_retrieval_batch") ? "retrieval_batch_id" : "id";
                    snapshot.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY " + key));
                }
                String file = args.length > 1 && args[1].equals("after-regression")
                        ? "database-snapshot-after-regression.json" : "database-snapshot.json";
                Files.writeString(OUT.resolve(file), new ObjectMapper().findAndRegisterModules()
                        .writerWithDefaultPrettyPrinter().writeValueAsString(snapshot), java.nio.file.StandardOpenOption.CREATE_NEW);
            }
            return;
        }
        // Synthetic administrator, confined to this newly initialized private MySQL instance.
        try (var connection = DriverManager.getConnection(URL, "root", "")) {
            requirePrivateDatabase(connection);
            var jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
            if (jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE account='browser_v3_admin'", Long.class) == 0) {
                jdbc.update("INSERT INTO user(account,username,password,email,role,status) VALUES (?,?,?,?,?,?)",
                        "browser_v3_admin", "V3 隔离管理员", new BCryptPasswordEncoder().encode("BrowserOnly123!"),
                        "browser_v3_admin@example.com", "ADMIN", "ENABLED");
            }
        }
        var app = new SpringApplication(AiInterviewSystemApplication.class);
        app.addInitializers(context -> context.addBeanFactoryPostProcessor(factory -> {
            var registry = (BeanDefinitionRegistry) factory;
            // Replace only the external vector boundary. The real retrieval/hydration/trace pipeline runs.
            for (String name : List.of("milvusConfiguration", "milvusClient", "milvusConnectConfig",
                    "milvusCollectionInitializer", "milvusVectorStoreClient")) {
                if (registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name);
            }
            registry.registerBeanDefinition("browserE2eVectors", new RootBeanDefinition(VectorStoreClient.class,
                    () -> new LocalVectors(factory.getBean(JdbcTemplate.class))));
        }));
        app.run("--spring.profiles.active=test", "--server.address=127.0.0.1", "--server.port=18096",
                "--spring.datasource.url=" + URL, "--spring.datasource.username=root", "--spring.datasource.password=",
                "--jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "--jwt.issuer=browser-e2e-local", "--jwt.access-token-expiration=2h",
                "--embedding.base-url=http://127.0.0.1:18097/v1", "--embedding.api-key=local-mock-only",
                "--embedding.model=browser-e2e-mock-embedding", "--embedding.dimension=1024",
                "--embedding.profile-version=browser-e2e-mock-v1", "--embedding.batch-size=20",
                "--embedding.connect-timeout=3s", "--embedding.read-timeout=10s",
                "--milvus.enabled=true", "--milvus.collection-name=knowledge_chunk_vectors_test",
                "--deepseek.enabled=true", "--deepseek.base-url=http://127.0.0.1:18097",
                "--deepseek.api-key=local-mock-only", "--deepseek.model=browser-e2e-mock-chat",
                "--deepseek.read-timeout=10s", "--ai.judge.provider=deepseek",
                "--rag.retrieval.cosine-threshold-enabled=false");
    }

    private static void requirePrivateDatabase(java.sql.Connection connection) throws Exception {
        if (!"ai_interview_test".equals(connection.getCatalog())) {
            throw new IllegalStateException("Unexpected test catalog");
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT @@datadir")) {
            if (!rows.next() || !Path.of(rows.getString(1)).toAbsolutePath().normalize()
                    .equals(OUT.resolve("mysql-data").toAbsolutePath().normalize())) {
                throw new IllegalStateException("Refusing a MySQL instance outside this run's private data directory");
            }
        }
    }

    static final class LocalVectors implements VectorStoreClient {
        private final JdbcTemplate jdbc;
        LocalVectors(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        public void insert(List<VectorWriteItem> items) { throw new UnsupportedOperationException("E2E retrieval only"); }
        public void deleteByVectorIds(List<String> ids) { throw new UnsupportedOperationException("E2E retrieval only"); }
        public void deleteByDocumentId(long id) { throw new UnsupportedOperationException("E2E retrieval only"); }
        public List<VectorSearchHit> search(List<Float> vector, String version, int topK) {
            if (vector.size() != 1024 || !version.equals("browser-e2e-mock-v1")) throw new IllegalArgumentException("Mock vector contract");
            return jdbc.query("SELECT vector_id,document_id,chunk_index,embedding_version FROM knowledge_chunk ORDER BY id LIMIT ?",
                    (row, index) -> new VectorSearchHit(row.getString(1), row.getLong(2), row.getInt(3), row.getString(4), .9), topK);
        }
    }
}
