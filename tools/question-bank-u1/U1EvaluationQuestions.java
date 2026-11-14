import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.AiInterviewSystemApplication;
import com.kun.aiinterview.question.dto.UpdateQuestionRequest;
import com.kun.aiinterview.question.service.QuestionAdminService;
import jakarta.validation.Validation;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.support.StandardServletEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Evaluation-only inspection and two-question update. Credentials are injected by an authorized caller. */
public final class U1EvaluationQuestions {
    private static final String URL = "jdbc:mysql://127.0.0.1:3308/ai_interview_evaluation"
            + "?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=20000";
    private static final String DATABASE = "ai_interview_evaluation 85dd1e00-cf98-11f1-8677-9eb9380f25e7";
    private static final List<String> KEYS = List.of("transaction_acid", "collection_semantics");
    private static final String CANDIDATE_SHA = "10a73a3a6f206a98a50bc5f9f69a67c88f33be39a862479b4b4f34d208881847";
    private static final Path EVIDENCE_DIRECTORY = Path.of("backups/U1_20261114").toAbsolutePath().normalize();
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]*");
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final QuestionAdminService admin;

    private U1EvaluationQuestions(ConfigurableApplicationContext context) {
        jdbc = context.getBean(JdbcTemplate.class);
        transactions = context.getBean(PlatformTransactionManager.class);
        admin = context.getBean(QuestionAdminService.class);
        require(AopUtils.isAopProxy(admin), "QuestionAdminService transaction proxy missing");
    }

    public static void main(String[] arguments) {
        try {
            if (arguments.length == 2 && arguments[0].equals("validate")) {
                readRevisions(Path.of(arguments[1]));
                System.out.println("U1 two-question DTO validation passed");
                return;
            }
            require(arguments.length == 3 && arguments[0].equals("inspect")
                    || arguments.length == 4 && arguments[0].equals("apply") && "true".equals(env("U1_WINDOW_CONFIRMED")),
                    "Invalid mode or write window not confirmed");
            Path directory = Path.of(arguments[1]).toAbsolutePath().normalize();
            require(directory.equals(EVIDENCE_DIRECTORY) && Files.isDirectory(directory) && !Files.isSymbolicLink(directory)
                    && directory.toRealPath().equals(directory)
                    && Files.getPosixFilePermissions(directory).equals(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")),
                    "Evidence directory must be owner-only");
            long sessionId = Long.parseLong(arguments[2]);
            require(sessionId > 0, "Invalid session ID");
            String account = env("U1_EVALUATION_ACCOUNT");
            try (var context = start()) {
                var runner = new U1EvaluationQuestions(context);
                switch (arguments[0]) {
                    case "inspect" -> runner.inspect(directory, sessionId, account);
                    case "apply" -> runner.apply(directory, sessionId, account, Path.of(arguments[3]));
                    default -> throw new IllegalArgumentException("Unknown mode");
                }
            }
            System.out.println("U1 evaluation " + arguments[0] + " passed");
        } catch (Exception error) {
            System.err.println("U1 operation stopped: " + error.getClass().getSimpleName()
                    + (error instanceof GateFailure ? " (" + error.getMessage() + ")" : ""));
            System.exit(1);
        }
    }

    private static ConfigurableApplicationContext start() {
        byte[] secret = new byte[48];
        new SecureRandom().nextBytes(secret);
        var settings = Map.<String, Object>ofEntries(
                Map.entry("spring.profiles.active", "evaluation"),
                Map.entry("spring.config.location", "classpath:/application.yaml"),
                Map.entry("spring.datasource.url", URL),
                Map.entry("spring.datasource.username", env("EVALUATION_DB_USERNAME")),
                Map.entry("spring.datasource.password", env("EVALUATION_DB_PASSWORD")),
                Map.entry("spring.sql.init.mode", "never"),
                Map.entry("server.port", "-1"),
                Map.entry("milvus.enabled", "false"),
                Map.entry("milvus.uri", "http://127.0.0.1:19532"),
                Map.entry("milvus.database-name", "default"),
                Map.entry("milvus.collection-name", "knowledge_chunk_vectors_evaluation"),
                Map.entry("embedding.base-url", "http://127.0.0.1:1"),
                Map.entry("embedding.api-key", "offline-u1-question-admin"),
                Map.entry("deepseek.enabled", "false"),
                Map.entry("jwt.secret", Base64.getEncoder().encodeToString(secret)),
                Map.entry("logging.level.root", "OFF"),
                Map.entry("spring.main.banner-mode", "off"));
        var environment = new StandardServletEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("u1-evaluation-only", settings));
        var application = new SpringApplication(AiInterviewSystemApplication.class);
        application.setEnvironment(environment);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setAddCommandLineProperties(false);
        return application.run();
    }

    private void inspect(Path directory, long session, String account) throws Exception {
        var evidence = transaction(true).execute(status -> {
            var data = history(session, account);
            data.put("currentBank", currentBank(false));
            return data;
        });
        write(directory.resolve("historical-sample.json"), evidence);
    }

    private Map<String, Object> history(long session, String account) {
        require(DATABASE.equals(jdbc.queryForObject("SELECT CONCAT(DATABASE(), ' ', @@server_uuid)", String.class)),
                "Evaluation database identity mismatch");
        require(jdbc.queryForObject("""
                SELECT COUNT(*) FROM interview_session s JOIN user u ON u.id = s.user_id
                WHERE s.id = ? AND BINARY u.account = BINARY ? AND s.difficulty = 'EASY'
                """, Integer.class, session, account) == 1, "Session does not match the confirmed evaluation account");
        var questions = jdbc.queryForList("""
                SELECT q.* FROM interview_question q
                LEFT JOIN interview_question parent ON parent.id = q.parent_question_id AND parent.session_id = q.session_id
                WHERE q.session_id = ? AND (
                    (q.question_type = 'MAIN' AND q.knowledge_point IN ('transaction_acid', 'collection_semantics')) OR
                    (q.question_type = 'FOLLOW_UP' AND parent.question_type = 'MAIN'
                      AND parent.knowledge_point IN ('transaction_acid', 'collection_semantics')))
                ORDER BY q.display_order
                """, session);
        require(questions.stream().filter(q -> "MAIN".equals(q.get("question_type")))
                .map(q -> q.get("knowledge_point")).sorted().toList().equals(KEYS.stream().sorted().toList()),
                "Both intended main snapshots were not found in this session");
        var rows = new java.util.ArrayList<Map<String, Object>>();
        for (var question : questions) {
            long id = ((Number) question.get("id")).longValue();
            var answer = jdbc.queryForList("SELECT * FROM interview_answer WHERE interview_question_id = ?", id);
            var evaluation = jdbc.queryForList("""
                    SELECT e.* FROM answer_evaluation e JOIN interview_answer a ON a.id = e.answer_id
                    WHERE a.interview_question_id = ?
                    """, id);
            var retrieval = jdbc.queryForList("""
                    SELECT r.* FROM rag_retrieval_batch r JOIN interview_answer a ON a.id = r.answer_id
                    WHERE a.interview_question_id = ? ORDER BY r.created_at, r.retrieval_batch_id
                    """, id);
            var hits = jdbc.queryForList("""
                    SELECT h.retrieval_batch_id, h.chunk_id, h.rank_no, h.similarity_score,
                           h.created_at AS hit_created_at,
                           d.title, d.category, d.source, d.document_version AS current_document_version,
                           c.document_version AS chunk_document_version, c.status AS current_chunk_status,
                           c.created_at AS chunk_created_at, c.updated_at AS chunk_updated_at,
                           c.content AS current_chunk_content, SHA2(c.content, 256) AS content_sha256
                    FROM rag_hit_log h JOIN interview_answer a ON a.id = h.answer_id
                    JOIN knowledge_chunk c ON c.id = h.chunk_id JOIN knowledge_document d ON d.id = c.document_id
                    WHERE a.interview_question_id = ? ORDER BY h.retrieval_batch_id, h.rank_no
                    """, id);
            rows.add(Map.of("question", question, "answer", answer, "evaluation", evaluation,
                    "retrieval", retrieval, "evidence", hits));
        }
        return new LinkedHashMap<>(Map.of("session", jdbc.queryForMap("""
                SELECT id, status, difficulty, report_status, version, started_at, ended_at FROM interview_session WHERE id = ?
                """, session), "report", jdbc.queryForList("""
                SELECT overall_score, result, llm_model, prompt_version, created_at, updated_at
                FROM interview_report WHERE session_id = ?
                """, session), "questions", rows));
    }

    private Map<String, Object> currentBank(boolean lock) {
        var current = new LinkedHashMap<String, Object>();
        for (String key : KEYS) {
            var ids = jdbc.queryForList("SELECT id FROM question WHERE knowledge_point = ?", Long.class, key);
            require(ids.size() == 1, "Expected one current question per stable key");
            var question = jdbc.queryForMap("SELECT * FROM question WHERE id = ?" + (lock ? " FOR UPDATE" : ""), ids.getFirst());
            var points = jdbc.queryForList("SELECT * FROM question_scoring_point WHERE question_id = ? ORDER BY sort_order, id"
                    + (lock ? " FOR UPDATE" : ""), question.get("id"));
            current.put(key, Map.of("question", question, "points", points));
        }
        return current;
    }

    private void apply(Path directory, long session, String account, Path candidatePath) throws Exception {
        Path sample = directory.resolve("historical-sample.json");
        require(Files.isRegularFile(sample) && !Files.isSymbolicLink(sample)
                && Files.getPosixFilePermissions(sample).equals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")),
                "Inspect the owned session first");
        Path intent = directory.resolve("question-apply-intent.json");
        Path receipt = directory.resolve("question-apply-receipt.json");
        require(!Files.exists(receipt) && !Files.exists(intent), "Prior apply attempt exists, inspect its outcome before continuing");
        var revisions = readRevisions(candidatePath);
        var saved = JSON.readTree(sample.toFile());
        var result = transaction(false).execute(status -> {
            var history = history(session, account);
            var current = currentBank(true);
            require(tree(current).equals(saved.required("currentBank")), "Current questions changed after inspection");
            for (String field : List.of("session", "questions", "report")) {
                require(tree(history.get(field)).equals(saved.required(field)), "Owned history changed after inspection");
            }
            require(jdbc.queryForObject("SELECT COUNT(*) FROM interview_answer WHERE status = 'EVALUATING'", Integer.class) == 0
                    && jdbc.queryForObject("SELECT COUNT(*) FROM interview_session WHERE report_status = 'GENERATING'", Integer.class) == 0,
                    "An evaluation or report is currently in progress");
            var before = databaseHashes();
            for (var revision : revisions.entrySet()) {
                var prior = tree(current).required(revision.getKey());
                require(matches(prior, revision.getValue().old()), "Candidate old content does not match evaluation");
                long currentId = prior.required("question").required("id").asLong();
                boolean historyContainsId = java.util.stream.StreamSupport.stream(saved.required("questions").spliterator(), false)
                        .map(row -> row.required("question"))
                        .anyMatch(q -> "MAIN".equals(q.required("question_type").asText())
                                && q.required("knowledge_point").asText().equals(revision.getKey())
                                && q.required("question_id").asLong() == currentId);
                require(historyContainsId && currentId == revision.getValue().historicalQuestionId(),
                        "Current question identity does not match the owned MAIN snapshot and candidate");
            }
            try {
                write(intent, Map.of("state", "PREPARED", "priorBank", tree(current), "revisions", revisions,
                        "candidateSha256", sha256(Files.readAllBytes(candidatePath)), "preservationHashes", before));
            } catch (java.io.IOException error) {
                throw new GateFailure("Could not save owner-only change intent");
            }
            for (var revision : revisions.entrySet()) {
                var prior = tree(current).required(revision.getKey());
                admin.updateQuestion(prior.required("question").required("id").asLong(), revision.getValue().revised());
            }
            var updated = tree(currentBank(false));
            for (var revision : revisions.entrySet()) {
                require(matches(updated.required(revision.getKey()), revision.getValue().revised()), "Revised content readback mismatch");
            }
            require(before.equals(databaseHashes()) && tree(history).equals(tree(history(session, account))),
                    "Unrelated rows or history changed during transaction");
            return Map.of("updatedKeys", KEYS, "preservationHashes", before,
                    "priorBank", tree(current), "updatedBank", updated, "ownedHistoryUnchanged", true);
        });
        System.out.println("U1 question update committed, verifying readback");
        transaction(true).executeWithoutResult(status -> {
            require(result.get("updatedBank").equals(tree(currentBank(false))), "Committed data readback mismatch");
            require(result.get("preservationHashes").equals(databaseHashes()), "Committed unrelated rows changed");
        });
        write(receipt, result);
    }

    private record Revision(long historicalQuestionId, UpdateQuestionRequest old, UpdateQuestionRequest revised) {}

    private static Map<String, Revision> readRevisions(Path path) throws Exception {
        var revisions = new LinkedHashMap<String, Revision>();
        byte[] content = Files.readAllBytes(path);
        require(CANDIDATE_SHA.equals(sha256(content)), "Candidate file differs from audited version");
        try (var validator = Validation.buildDefaultValidatorFactory()) {
            for (var row : JSON.readTree(content).required("questions")) {
                var old = JSON.treeToValue(row.required("baselineRequest"), UpdateQuestionRequest.class);
                var revised = JSON.treeToValue(row.required("candidateRequest"), UpdateQuestionRequest.class);
                require(KEYS.contains(old.getKnowledgePoint()) && old.getKnowledgePoint().equals(revised.getKnowledgePoint())
                        && old.getCategory() == revised.getCategory() && old.getDifficulty() == revised.getDifficulty()
                        && validator.getValidator().validate(revised).isEmpty()
                        && old.getScoringPoints().size() == revised.getScoringPoints().size(), "Invalid candidate question shape");
                for (int i = 0; i < old.getScoringPoints().size(); i++) {
                    var before = old.getScoringPoints().get(i);
                    var after = revised.getScoringPoints().get(i);
                    require(before.getPointType() == after.getPointType() && before.getWeight().equals(after.getWeight()),
                            "Candidate changed point type or weight");
                }
                long questionId = row.required("historicalEvaluationLookupHint").required("questionId").asLong();
                require(revisions.putIfAbsent(old.getKnowledgePoint(), new Revision(questionId, old, revised)) == null, "Duplicate candidate");
            }
        }
        require(revisions.keySet().equals(Set.copyOf(KEYS)), "Both candidates are required");
        return revisions;
    }

    private static boolean matches(JsonNode state, UpdateQuestionRequest dto) {
        var q = state.required("question");
        if (!q.required("category").asText().equals(dto.getCategory().name())
                || !q.required("knowledge_point").asText().equals(dto.getKnowledgePoint())
                || !q.required("difficulty").asText().equals(dto.getDifficulty().name())
                || !q.required("status").asText().equals("ENABLED")
                || !q.required("question_content").asText().equals(dto.getQuestionContent())
                || !q.required("reference_answer").asText().equals(dto.getReferenceAnswer())) return false;
        var points = state.required("points");
        if (points.size() != dto.getScoringPoints().size()) return false;
        for (int i = 0; i < points.size(); i++) {
            var row = points.get(i);
            var point = dto.getScoringPoints().get(i);
            if (row.required("sort_order").asInt() != i + 1 || !row.required("status").asText().equals("ENABLED")
                    || !row.required("content").asText().equals(point.getContent())
                    || !row.required("point_type").asText().equals(point.getPointType().name())
                    || row.required("weight").asInt() != point.getWeight()) return false;
        }
        return true;
    }

    private Map<String, String> databaseHashes() {
        var hashes = new LinkedHashMap<String, String>();
        for (String table : List.of("user", "interview_session", "interview_question", "interview_answer", "answer_evaluation",
                "interview_report", "rag_retrieval_batch", "rag_hit_log", "user_weakness", "knowledge_document", "knowledge_chunk")) {
            String key = table.equals("rag_retrieval_batch") ? "retrieval_batch_id" : "id";
            hashes.put(table, rowDigests(table, key, ""));
        }
        String otherKeys = "knowledge_point NOT IN ('transaction_acid', 'collection_semantics')";
        hashes.put("otherQuestions", rowDigests("question", "id", " WHERE " + otherKeys));
        hashes.put("otherScoringPoints", rowDigests("question_scoring_point", "id",
                " WHERE question_id IN (SELECT id FROM question WHERE " + otherKeys + ")"));
        return hashes;
    }

    private String rowDigests(String table, String key, String condition) {
        var columns = jdbc.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION
                """, String.class, table);
        require(columns.contains(key) && columns.stream().allMatch(column -> SQL_IDENTIFIER.matcher(column).matches()),
                "Unexpected protected table schema");
        String projection = columns.stream().map(column -> "`" + column + "`").collect(Collectors.joining(", "));
        return hash(jdbc.queryForList("SELECT SHA2(CAST(JSON_ARRAY(" + projection + ") AS CHAR), 256) FROM `"
                + table + "`" + condition + " ORDER BY `" + key + "`", String.class));
    }

    private TransactionTemplate transaction(boolean readOnly) {
        var template = new TransactionTemplate(transactions);
        template.setReadOnly(readOnly);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        template.setTimeout(45);
        return template;
    }

    private static JsonNode tree(Object value) {
        try { return JSON.readTree(JSON.writeValueAsBytes(value)); }
        catch (Exception error) { throw new IllegalStateException("Unable to serialize evidence"); }
    }

    private static String hash(Object value) {
        try { return sha256(JSON.writeValueAsBytes(value)); }
        catch (Exception error) { throw new IllegalStateException("Unable to hash evaluation snapshot"); }
    }

    private static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static void write(Path path, Object value) throws java.io.IOException {
        Files.createFile(path, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
        Files.write(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value), StandardOpenOption.WRITE);
    }

    private static String env(String key) {
        var value = System.getenv(key);
        require(value != null && !value.isBlank(), "Missing authorized environment input " + key);
        return value;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new GateFailure(reason);
    }

    private static final class GateFailure extends IllegalStateException {
        GateFailure(String message) { super(message); }
    }
}
