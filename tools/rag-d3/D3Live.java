import com.kun.aiinterview.AiInterviewSystemApplication;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalAdapter;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalResult;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPromptBuilder;
import com.kun.aiinterview.knowledge.chunk.KnowledgeTextChunker;
import com.kun.aiinterview.knowledge.dto.UploadKnowledgeDocumentRequest;
import com.kun.aiinterview.knowledge.embedding.EmbeddingProperties;
import com.kun.aiinterview.knowledge.retrieval.RetrievalProperties;
import com.kun.aiinterview.knowledge.service.KnowledgeDocumentAdminService;
import com.kun.aiinterview.knowledge.service.KnowledgeDocumentProcessingTransactionService;
import com.kun.aiinterview.knowledge.service.KnowledgeRetrievalService;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.support.StandardServletEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;


final class D3Live {
    private static final Path CORPUS_MANIFEST = D3Corpus.ROOT.resolve("CORPUS_MANIFEST_20261114.json");
    private static final String EVIDENCE_HEADER = "===== RAG EVIDENCE =====\n";
    private static final Path PUBLIC_PROTOCOL = Path.of("docs/verification/D3A_RETRIEVAL_PROTOCOL_V1_20261114.md");
    private static final String PROTOCOL_SHA = "9fd24d7223f0d73c64dcacea04bdbd6ff99f8593fb281ebc422c3657f01c9cc2";
    private static final Path SUPPLEMENT = Path.of("docs/rag/d3-d/PROTOCOL_SUPPLEMENT_20261114.md");
    private static final String SUPPLEMENT_SHA = "eb3a5a30c07ce8872dfd8a3134ba5d871b71e373426af4f50c97fdac6dabf1e8";
    private static final String INDEPENDENT_SHA = "9f637abce7442fe663c6c0702de907d17dba16ed6e874708ff481c8fbd134632";
    private static final List<String> PROTECTED_TABLES = List.of("user", "question", "question_scoring_point",
            "interview_session", "interview_question", "interview_answer", "answer_evaluation", "rag_retrieval_batch",
            "rag_hit_log", "interview_report", "user_weakness");
    private final Path output;
    private final D3Corpus.Manifest manifest;
    private final Map<String, D3Corpus.Document> documentsByHash = new HashMap<>();
    private JdbcTemplate jdbc;
    private MilvusClientV2 milvus;

    record ChunkRow(String key, long chunkId, int index, String vectorId, String sha256, String float32Sha256) {}
    record DocumentRow(String key, long id, String attempt, int version, String sha256, List<ChunkRow> chunks) {}
    record Snapshot(Map<String, String> protectedTableDigests, Map<String, Long> nonTargetCounts,
                    List<DocumentRow> documents, D3Inventory.Inventory milvus) {}

    private D3Live(Path output) throws Exception {
        this.output = output;
        manifest = D3Corpus.JSON.readValue(CORPUS_MANIFEST.toFile(), D3Corpus.Manifest.class);
        require(manifest.equals(D3Corpus.build(true)), "CORPUS_FREEZE_MISMATCH");
        manifest.documents().forEach(doc -> documentsByHash.put(doc.normalizedSha256(), doc));
    }

    public static void main(String[] args) {
        String operation = args.length > 0 ? args[0] : "";
        try {
            require(args.length >= 2 && Set.of("preflight", "ingest", "retrieve").contains(operation), "ARGUMENTS");
            var work = new D3Live(Path.of(args[1]));
            work.run(operation, Arrays.copyOfRange(args, 2, args.length));
        } catch (Exception error) {
            String code = error.getMessage();
            System.err.println("D3_STOP " + error.getClass().getSimpleName()
                    + (code != null && code.matches("[A-Z0-9_]+") ? " " + code : ""));
            System.exit(1);
        }
    }

    private void run(String operation, String[] args) throws Exception {
        // Require the existing collection before the production initializer can start.
        var sdk = D3Inventory.client();
        try {
            D3Inventory.inventory(sdk);
        } finally {
            sdk.close();
        }
        boolean paid = !"preflight".equals(operation);
        if (paid) {
            checkPaidReceipt();
        }
        try (var app = start(paid)) {
            require(Arrays.equals(app.getEnvironment().getActiveProfiles(), new String[]{"evaluation"}), "PROFILE");
            require(AopUtils.isAopProxy(app.getBean(KnowledgeDocumentProcessingTransactionService.class))
                    && !TransactionSynchronizationManager.isActualTransactionActive(), "TRANSACTION_BOUNDARY");
            var retrieval = app.getBean(RetrievalProperties.class);
            require(!retrieval.isCosineThresholdEnabled() && retrieval.getMinCosineScore() == 0.70F, "RETRIEVAL_SETTINGS");
            jdbc = app.getBean(JdbcTemplate.class);
            milvus = app.getBean(MilvusClientV2.class);
            Snapshot before = snapshot();
            if (operation.equals("preflight")) {
                require(args.length == 1, "PREFLIGHT_OUTPUT");
                D3Corpus.writeNew(output.resolve(args[0]), before);
                System.out.printf("preflight documents=%d vectors=%d%n", before.documents().size(), before.milvus().vectors().size());
                return;
            }
            Snapshot baseline = D3Corpus.JSON.readValue(output.resolve("preflight.json").toFile(), Snapshot.class);
            preserve(baseline, before);
            var embedding = app.getBean(D3PaidEmbedding.class);
            if (operation.equals("ingest")) {
                require(args.length == 0, "INGEST_ARGUMENTS");
                ingest(app, embedding, baseline, before);
            } else {
                require(before.documents().size() == 51, "CORPUS_NOT_READY");
                retrieve(app, embedding, before, args);
                preserve(before, snapshot());
            }
        }
    }

    private void checkPaidReceipt() throws Exception {
        var receipt = D3Corpus.JSON.readTree(output.resolve("admission.json").toFile());
        require(receipt.required("corpusSha256").asText().equals(D3Corpus.sha(Files.readAllBytes(CORPUS_MANIFEST)))
                && receipt.required("publicSha256").asText().equals(D3Queries.PUBLIC_SHA)
                && PROTOCOL_SHA.equals(D3Corpus.sha(Files.readAllBytes(PUBLIC_PROTOCOL)))
                && SUPPLEMENT_SHA.equals(D3Corpus.sha(Files.readAllBytes(SUPPLEMENT)))
                && Set.of("VERIFIED", "CONSOLE_NOT_VISIBLE").contains(receipt.required("balanceVisibility").asText())
                && receipt.required("contentReviewed").asBoolean()
                && receipt.required("labelsReviewed").asBoolean(), "MISSING_ADMISSION_EVIDENCE");
        require(receipt.required("labelsSha256").asText().equals(D3Corpus.sha(Files.readAllBytes(
                Path.of("docs/rag/d3-d/PUBLIC_AI_LABELS_20261114.json")))), "LABEL_FREEZE_MISMATCH");
        require("1".equals(System.getProperty("jdk.httpclient.redirects.retrylimit"))
                && "true".equals(System.getProperty("jdk.httpclient.disableRetryConnect"))
                && "false".equals(System.getProperty("jdk.httpclient.enableAllMethodRetry")), "HTTP_RETRY_CONFIGURATION");
    }

    private ConfigurableApplicationContext start(boolean paid) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("spring.profiles.active", "evaluation");
        values.put("spring.config.location", "classpath:/application.yaml");
        values.put("spring.sql.init.mode", "never");
        values.put("spring.datasource.url", D3Inventory.MYSQL);
        values.put("spring.datasource.username", env("EVALUATION_DB_USERNAME"));
        values.put("spring.datasource.password", env("EVALUATION_DB_PASSWORD"));
        values.put("milvus.enabled", "true");
        values.put("milvus.uri", D3Inventory.MILVUS);
        values.put("milvus.database-name", "default");
        values.put("milvus.collection-name", D3Inventory.COLLECTION);
        values.put("milvus.dimension", "1024");
        values.put("embedding.base-url", paid ? env("EMBEDDING_BASE_URL") : "http://127.0.0.1:1");
        values.put("embedding.api-key", paid ? env("EMBEDDING_API_KEY") : "offline-unused");
        values.put("embedding.model", paid ? env("EMBEDDING_MODEL") : D3Inventory.MODEL);
        values.put("embedding.profile-version", paid ? env("EMBEDDING_PROFILE_VERSION") : D3Inventory.PROFILE);
        values.put("embedding.dimension", paid ? env("EMBEDDING_DIMENSION") : "1024");
        values.put("deepseek.enabled", "false");
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        values.put("jwt.secret", Base64.getEncoder().encodeToString(secret));
        values.put("server.port", "-1");
        values.put("logging.level.root", "OFF");
        values.put("spring.main.banner-mode", "off");
        var environment = new StandardServletEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("d3-evaluation", values));
        var app = new SpringApplication(AiInterviewSystemApplication.class);
        app.setEnvironment(environment);
        app.setAddCommandLineProperties(false);
        app.setWebApplicationType(WebApplicationType.SERVLET);
        if (paid) {
            app.addInitializers(context -> ((org.springframework.beans.factory.support.BeanDefinitionRegistry) context.getBeanFactory())
                    .registerBeanDefinition("d3PaidEmbedding", org.springframework.beans.factory.support.BeanDefinitionBuilder
                            .genericBeanDefinition(D3PaidEmbedding.class)
                            .addConstructorArgReference("embedding-" + EmbeddingProperties.class.getName())
                            .addConstructorArgValue(output.resolve("embedding-usage.jsonl"))
                            .setPrimary(true).getBeanDefinition()));
        }
        return app.run();
    }

    private Snapshot snapshot() throws Exception {
        require(("ai_interview_evaluation " + D3Inventory.MYSQL_UUID).equals(jdbc.queryForObject(
                "SELECT CONCAT(DATABASE(),' ',@@server_uuid)", String.class)), "MYSQL_IDENTITY");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE status='ENABLED'", Long.class) == 63
                && jdbc.queryForObject("SELECT COUNT(*) FROM question_scoring_point WHERE status='ENABLED'", Long.class) == 298,
                "QUESTION_BASELINE");
        var protectedDigests = new TreeMap<String, String>();
        for (String table : PROTECTED_TABLES) {
            String primaryKey = table.equals("rag_retrieval_batch") ? "retrieval_batch_id" : "id";
            var rows = jdbc.queryForList("SELECT * FROM `" + table + "` ORDER BY " + primaryKey);
            protectedDigests.put(table, D3Corpus.sha(D3Corpus.JSON.writeValueAsBytes(rows.stream().map(row -> {
                var canonical = new TreeMap<String, String>();
                row.forEach((key, value) -> canonical.put(key, value == null ? null : value.toString()));
                return canonical;
            }).toList())));
        }
        var nonTargets = new TreeMap<String, Long>();
        for (String schema : List.of("ai_interview", "ai_interview_test")) {
            for (String table : List.of("user", "question", "question_scoring_point", "knowledge_document", "knowledge_chunk")) {
                nonTargets.put(schema + "." + table, jdbc.queryForObject(
                        "SELECT COUNT(*) FROM `" + schema + "`.`" + table + "`", Long.class));
            }
        }
        var inventory = D3Inventory.inventory(milvus);
        var unmatched = new HashMap<>(inventory.vectors());
        var docs = new ArrayList<DocumentRow>();
        for (var row : jdbc.queryForList("SELECT * FROM knowledge_document ORDER BY id")) {
            String hash = (String) row.get("content_hash");
            var doc = documentsByHash.get(hash);
            long id = D3Inventory.number(row, "id").longValue();
            require(doc != null && docs.stream().noneMatch(existing -> existing.sha256().equals(hash)), "UNKNOWN_OR_DUPLICATE_DOCUMENT");
            require(hash.equals(D3Corpus.sha((String) row.get("content"))) && doc.title().equals(row.get("title"))
                    && doc.category().name().equals(row.get("category")) && "MARKDOWN".equals(row.get("file_type"))
                    && Path.of(doc.file()).getFileName().toString().equals(row.get("file_name"))
                    && D3Inventory.number(row, "document_version").intValue() == doc.documentVersion(), "DOCUMENT_CONTENT");
            String status = (String) row.get("processing_status");
            require("READY".equals(status), "DOCUMENT_STATE_" + status);
            String attempt = (String) row.get("processing_attempt_id");
            require(attempt != null && attempt.matches("[0-9a-f]{32}") && row.get("error_message") == null, "DOCUMENT_ATTEMPT");
            var chunks = new ArrayList<ChunkRow>();
            for (var chunk : jdbc.queryForList("SELECT * FROM knowledge_chunk WHERE document_id=? ORDER BY chunk_index", id)) {
                int index = D3Inventory.number(chunk, "chunk_index").intValue();
                String vectorId = (String) chunk.get("vector_id");
                require(index == chunks.size() + 1 && index <= doc.chunks().size()
                        && (attempt + "-" + index).equals(vectorId) && "ACTIVE".equals(chunk.get("status"))
                        && D3Inventory.MODEL.equals(chunk.get("embedding_model"))
                        && D3Inventory.PROFILE.equals(chunk.get("embedding_version"))
                        && D3Inventory.number(chunk, "document_version").intValue() == doc.documentVersion(), "MYSQL_CHUNK_IDENTITY");
                String chunkHash = D3Corpus.sha((String) chunk.get("content"));
                require(doc.chunks().get(index - 1).sha256().equals(chunkHash), "MYSQL_CHUNK_CONTENT");
                var vector = unmatched.remove(vectorId);
                require(vector != null && vector.documentId() == id && vector.chunkIndex() == index
                        && D3Inventory.PROFILE.equals(vector.profile()), "CROSS_STORE_IDENTITY");
                chunks.add(new ChunkRow(doc.id() + "/" + index, D3Inventory.number(chunk, "id").longValue(), index,
                        vectorId, chunkHash, vector.float32Sha256()));
            }
            require(chunks.size() == doc.chunks().size(), "MISSING_CHUNKS");
            docs.add(new DocumentRow(doc.id(), id, attempt, doc.documentVersion(), hash, List.copyOf(chunks)));
        }
        require(unmatched.isEmpty() && docs.stream().anyMatch(doc -> doc.key().equals("J01")), "ORPHAN_OR_E01_MISSING");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_chunk", Long.class) == inventory.vectors().size(), "MYSQL_ORPHAN");
        return new Snapshot(protectedDigests, nonTargets, List.copyOf(docs), inventory);
    }

    private static void preserve(Snapshot previous, Snapshot current) {
        require(previous.protectedTableDigests().equals(current.protectedTableDigests())
                && previous.nonTargetCounts().equals(current.nonTargetCounts())
                && previous.milvus().otherCollectionCounts().equals(current.milvus().otherCollectionCounts())
                && current.documents().containsAll(previous.documents()), "PRESERVATION");
    }

    private void ingest(ConfigurableApplicationContext app, D3PaidEmbedding embedding, Snapshot baseline, Snapshot current) throws Exception {
        var admin = app.getBean(KnowledgeDocumentAdminService.class);
        int inBatch = 0;
        for (var doc : manifest.documents()) {
            if (current.documents().stream().anyMatch(saved -> saved.key().equals(doc.id()))) {
                continue;
            }
            boolean pilot = current.documents().size() == 1;
            var source = Path.of(doc.file());
            byte[] original = Files.readAllBytes(source);
            require(doc.fileSha256().equals(D3Corpus.sha(original)), "INGEST_SOURCE_BYTES");
            String content = D3Corpus.normalize(Files.readString(source));
            var texts = new KnowledgeTextChunker().split(content).stream().map(c -> c.content()).toList();
            require(texts.size() <= 20 && texts.size() == doc.chunks().size(), "INGEST_BATCH_LIMIT");
            var upload = UploadKnowledgeDocumentRequest.builder()
                    .file(new MockMultipartFile("file", source.getFileName().toString(), "text/markdown", original))
                    .title(doc.title()).category(doc.category()).source("D3-B corpus v1 / " + doc.id()).build();
            D3Corpus.writeNew(output.resolve(doc.id() + "-intent.json"), Map.of("id", doc.id(), "sha256", doc.fileSha256(),
                    "startedAt", Instant.now().toString()));
            long id = admin.uploadDocument(upload).getDocumentId();
            D3Corpus.writeNew(output.resolve(doc.id() + "-uploaded.json"), Map.of("id", id, "sha256", doc.normalizedSha256()));
            embedding.authorize("ingest-" + doc.id(), texts);
            admin.processDocument(id);
            var result = embedding.takeResult();
            Snapshot next = snapshot();
            preserve(baseline, next);
            preserve(current, next);
            DocumentRow saved = next.documents().stream().filter(row -> row.id() == id).findFirst().orElseThrow();
            require(next.documents().size() == current.documents().size() + 1 && saved.key().equals(doc.id())
                    && result.vectors().size() == saved.chunks().size(), "INGEST_RESULT_IDENTITY");
            for (var vector : result.vectors()) {
                require(saved.chunks().get(vector.inputIndex()).float32Sha256().equals(D3Inventory.vectorDigest(vector.values())),
                        "PERSISTED_VECTOR_DIFFERS_FROM_RESPONSE");
            }
            D3Corpus.writeNew(output.resolve(doc.id() + "-verified.json"), saved);
            current = next;
            inBatch++;
            if (pilot || inBatch == 5 || current.documents().size() == 51) {
                D3Corpus.writeNew(output.resolve("ingestion-checkpoint-" + current.documents().size() + ".json"), current);
                System.out.printf("ingestion READY=%d vectors=%d%n", current.documents().size(), current.milvus().vectors().size());
                inBatch = 0;
            }
        }
    }

    private void retrieve(ConfigurableApplicationContext app, D3PaidEmbedding embedding, Snapshot snapshot,
                          String[] args) throws Exception {
        require(args.length == 1 || args.length == 3, "RETRIEVAL_ARGUMENTS");
        String set = args[0];
        List<D3Queries.Query> queries = switch (set) {
            case "production" -> D3Queries.production();
            case "public" -> D3Queries.custom(D3Queries.PUBLIC, D3Queries.PUBLIC_SHA, "PUBLIC", 60);
            case "independent" -> {
                require(args.length == 3 && INDEPENDENT_SHA.equals(args[2]), "INDEPENDENT_FREEZE_MISMATCH");
                yield D3Queries.custom(Path.of(args[1]), INDEPENDENT_SHA, "INDEPENDENT", 24);
            }
            default -> throw new IllegalArgumentException("UNKNOWN_QUERY_SET");
        };
        Path predictions = output.resolve(set + "-predictions");
        Files.createDirectory(predictions);
        Map<String, ChunkRow> identity = new HashMap<>();
        snapshot.documents().forEach(doc -> doc.chunks().forEach(chunk -> identity.put(chunk.vectorId(), chunk)));
        var adapter = app.getBean(EvaluationRetrievalAdapter.class);
        var service = app.getBean(KnowledgeRetrievalService.class);
        var promptBuilder = app.getBean(EvaluationPromptBuilder.class);
        for (var query : queries) {
            long start = System.nanoTime();
            embedding.authorize(set + "-" + query.id(), List.of(query.text()));
            EvaluationRetrievalResult result;
            if (set.equals("production")) {
                result = adapter.retrieve(query.context(), 5);
            } else {
                var raw = service.retrieve(query.text(), 5);
                result = new EvaluationRetrievalResult(raw.query(), raw.requestedTopK(), raw.embeddingModel(),
                        raw.embeddingVersion(), raw.rawHits(), raw.items());
            }
            var observed = embedding.takeResult();
            List<Map<String, Object>> rawRows = new ArrayList<>();
            for (var hit : result.rawHits()) {
                ChunkRow known = identity.get(hit.vectorId());
                require(known != null, "UNKNOWN_RAW_CHUNK");
                rawRows.add(Map.of("rank", rawRows.size() + 1, "score", hit.similarityScore(), "key", known.key(),
                        "vectorId", hit.vectorId()));
            }
            List<Map<String, Object>> hydrated = new ArrayList<>();
            for (var hit : result.evidence()) {
                ChunkRow known = identity.get(hit.vectorId());
                require(known != null && known.chunkId() == hit.chunkId() && known.sha256().equals(D3Corpus.sha(hit.content())),
                        "UNKNOWN_HYDRATED_CHUNK");
                hydrated.add(Map.of("rank", hit.vectorRank(), "key", known.key(), "sha256", known.sha256(), "chunkId", known.chunkId()));
            }
            String prompt = promptBuilder.build(query.context(), result).userPrompt();
            String text = prompt.substring(prompt.indexOf(EVIDENCE_HEADER) + EVIDENCE_HEADER.length());
            var parsed = Pattern.compile("(?ms)^- rank=(\\d+), similarity=[^\\n]+\\n  title=[^\\n]*\\n  category=[^\\n]*\\n  source=[^\\n]*\\n  content=(.*?)(?=\\n\\n- rank=|\\n\\n\\z)")
                    .matcher(text);
            List<Map<String, Object>> finalRows = new ArrayList<>();
            while (parsed.find()) {
                int rank = Integer.parseInt(parsed.group(1));
                String hash = D3Corpus.sha(parsed.group(2));
                var known = hydrated.stream().filter(item -> item.get("rank").equals(rank)
                        && item.get("sha256").equals(hash)).findFirst().orElseThrow();
                finalRows.add(Map.of("rank", rank, "key", known.get("key"), "sha256", hash));
            }
            require(finalRows.size() == hydrated.size(), "PROMPT_EVIDENCE_PARSE");
            D3Corpus.writeNew(predictions.resolve(query.id() + ".json"), Map.of("id", query.id(), "category", query.category(),
                    "difficulty", query.difficulty(), "type", query.type(), "raw", rawRows, "hydrated", hydrated, "prompt", finalRows,
                    "promptEvidence", text, "embeddingUsage", observed.totalTokenCount(), "elapsedMs", (System.nanoTime() - start) / 1_000_000));
        }
        System.out.printf("retrieval set=%s queries=%d%n", set, queries.size());
    }

    private static void require(boolean condition, String code) {
        D3Corpus.require(condition, code);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        require(value != null && !value.isBlank(), "MISSING_" + name);
        return value;
    }
}
