import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.AiInterviewSystemApplication;
import com.kun.aiinterview.common.exception.BusinessException;
import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.config.InterviewPlanConfig;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.interview.service.InterviewQuestionSnapshotFactory;
import com.kun.aiinterview.question.dto.*;
import com.kun.aiinterview.question.entity.*;
import com.kun.aiinterview.question.enums.*;
import com.kun.aiinterview.question.mapper.*;
import com.kun.aiinterview.question.service.QuestionAdminService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.support.StandardServletEnvironment;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;

/** One-off D1 migration. All business writes go through the unmodified QuestionAdminService. */
public final class D1QuestionBankImport {
    private static final Path DIR = Path.of("backups/D1_20261114");
    private static final Path OLD = Path.of("docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json");
    private static final Path BANK = Path.of("docs/question-bank/D1_QUESTION_BANK_V2_20261114.json");
    private static final Path REVIEW = Path.of("docs/question-bank/D1_REVIEW_CHECKLIST_20261114.json");
    private static final String OLD_SHA = "ce6fff9bd42b064009b01c0300e2da5da16e73d04c363e3cf7f2b351cc1b6fd3";
    private static final String IDENTITY = "ai_interview_evaluation 85dd1e00-cf98-11f1-8677-9eb9380f25e7";
    private static final String URL = "jdbc:mysql://127.0.0.1:3308/ai_interview_evaluation?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=20000";
    private static final List<String> TABLES = List.of("user", "question", "question_scoring_point", "interview_session",
            "interview_question", "interview_answer", "answer_evaluation", "knowledge_document", "knowledge_chunk",
            "rag_retrieval_batch", "rag_hit_log", "interview_report", "user_weakness");
    private static final Set<String> DTO_FIELDS = Set.of("category", "knowledgePoint", "difficulty", "questionContent", "referenceAnswer", "scoringPoints");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.FAIL_ON_TRAILING_TOKENS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    public static void main(String[] args) {
        try {
            require(args.length == 1 && Set.of("baseline", "validate", "rollback", "import", "verify").contains(args[0]), "mode");
            require(sha(OLD).equals(OLD_SHA), "frozen V1 digest");
            var old = JSON.readValue(Files.readAllBytes(OLD), new TypeReference<List<CreateQuestionRequest>>() {});
            byte[] bankBytes = args[0].equals("baseline") ? null : Files.readAllBytes(BANK);
            String bankSha = bankBytes == null ? null : sha(bankBytes);
            var revised = bankBytes == null ? old : readBank(bankBytes, old);
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                validate(old, factory.getValidator(), 21);
                validate(revised, factory.getValidator(), args[0].equals("baseline") ? 21 : 63);
            }
            if (args[0].equals("validate")) { System.out.println("PASS DTO: V1=21 / V2=63"); return; }
            if (Set.of("rollback", "import").contains(args[0])) checkReview(bankSha);
            if (args[0].equals("import")) {
                var receipt = JSON.readTree(Files.readAllBytes(DIR.resolve("rollback-pass.json")));
                require(receipt.path("bankSha256").asText().equals(bankSha)
                        && receipt.path("baselineSha256").asText().equals(sha(DIR.resolve("baseline-rows.json")))
                        && receipt.path("status").asText().equals("PASS"), "matching rollback receipt absent");
                require(!Files.exists(DIR.resolve("committed-rows.json")), "commit receipt exists; use verify");
            }
            try (var context = start()) {
                var runner = new D1QuestionBankImport(context, old, revised, bankSha);
                switch (args[0]) {
                    case "baseline" -> runner.baseline();
                    case "rollback" -> runner.rehearse();
                    case "import" -> runner.importBank();
                    case "verify" -> runner.verifyFinal();
                    default -> throw new IllegalStateException();
                }
            }
            System.out.println("PASS mode=" + args[0] + " target=" + IDENTITY);
        } catch (Exception error) {
            // Never log credentials or provider data from exception chains.
            System.err.println("D1 stopped: " + error.getClass().getSimpleName()
                    + (error instanceof GateFailure ? ": " + error.getMessage() : ""));
            System.exit(1);
        }
    }

    private static List<CreateQuestionRequest> readBank(byte[] bytes, List<CreateQuestionRequest> old) throws Exception {
        var root = JSON.readTree(bytes);
        require(root.path("bankVersion").asText().equals("D1-v2-20261114"), "bank version");
        Path plan = Path.of("docs/verification/D1_QUESTION_BANK_EXPANSION_PLAN_20261113.md");
        require(sha(plan).equals("2264b727753dbf3106c8c03a1812eb892e741214b0349b746de252acceb94970"), "approved plan changed");
        Map<String,List<String>> identities = new HashMap<>();
        var pattern = Pattern.compile("(?m)^\\| ([EMH]\\d{2}) \\| [^|]+ \\| (\\w+) \\| (\\w+) \\| `([^`]+)` \\|");
        var matches = pattern.matcher(Files.readString(plan));
        while (matches.find()) identities.put(matches.group(1), List.of(matches.group(2), matches.group(3), matches.group(4)));
        require(identities.size() == 42, "approved new identity count");
        Map<QuestionDifficulty,Integer> oldSequences = new EnumMap<>(QuestionDifficulty.class);
        for (var q : old) {
            int sequence = oldSequences.merge(q.getDifficulty(), 1, Integer::sum);
            String id = q.getDifficulty().name().substring(0, 1) + "%02d".formatted(sequence);
            identities.put(id, List.of(q.getCategory().name(), q.getDifficulty().name(), q.getKnowledgePoint()));
        }
        List<CreateQuestionRequest> requests = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (var q : root.path("questions")) {
            String id = q.path("bankId").asText();
            require(ids.add(id), "duplicate bankId");
            require(List.of(q.path("category").asText(), q.path("difficulty").asText(), q.path("knowledgePoint").asText())
                    .equals(identities.get(id)), "bankId/DTO identity mismatch: " + id);
            ObjectNode dto = ((ObjectNode) q).deepCopy();
            dto.retain(DTO_FIELDS);
            requests.add(JSON.treeToValue(dto, CreateQuestionRequest.class));
        }
        require(ids.size() == 63, "bankId count");
        return requests;
    }

    private static void validate(List<CreateQuestionRequest> items, Validator validator, int count) {
        require(items.size() == count, "question count");
        Set<String> keys = new HashSet<>();
        Set<String> contents = new HashSet<>();
        for (var q : items) {
            require(q != null && validator.validate(q).isEmpty(), "DTO validation");
            require(keys.add(q.getKnowledgePoint()) && contents.add(q.getQuestionContent().strip()), "duplicate logical key/content");
            var points = q.getScoringPoints();
            require(points.size() >= 4 && points.size() <= 6, "point count");
            require(points.stream().mapToInt(ScoringPointRequest::getWeight).sum() == 100, "weight total");
            require(points.stream().anyMatch(p -> p.getPointType() == QuestionPointType.CORE), "missing CORE");
            require(points.stream().map(ScoringPointRequest::getContent).distinct().count() == points.size(), "duplicate points");
        }
        var plan = new InterviewPlanConfig();
        for (var difficulty : QuestionDifficulty.values()) {
            var categories = plan.getRule(difficulty).requiredCategories();
            require(items.stream().filter(q -> q.getDifficulty() == difficulty).allMatch(q -> categories.contains(q.getCategory())), "out-of-plan category");
            for (var category : categories) require(items.stream().filter(q -> q.getDifficulty() == difficulty && q.getCategory() == category).count()
                    == (count == 63 ? 3 : 1), "candidate bucket count");
        }
    }

    private static void checkReview(String bankSha) throws Exception {
        var review = JSON.readTree(Files.readAllBytes(REVIEW));
        require(bankSha.equals(review.path("bankSha256").asText()), "review not bound to parsed bank bytes");
        require(review.path("status").asText().equals("PASS") && review.path("unresolvedP0P1").asInt(-1) == 0, "unresolved content review");
        Set<String> reviewed = new HashSet<>();
        for (var q : review.path("questions")) {
            require(reviewed.add(q.path("bankId").asText()), "duplicate review ID");
            require(q.path("status").asText().equals("PASS"), "question review incomplete");
            require(!q.path("author").asText().isBlank() && !q.path("reviewer").asText().isBlank()
                    && !q.path("author").asText().equals(q.path("reviewer").asText()), "independent reviewer absent");
        }
        Set<String> required = new HashSet<>(); required.add("H01");
        for (int i = 6; i <= 15; i++) required.add("E%02d".formatted(i));
        for (int i = 8; i <= 21; i++) required.add("M%02d".formatted(i));
        for (int i = 10; i <= 27; i++) required.add("H%02d".formatted(i));
        require(reviewed.equals(required), "expected 43 content review records");
    }

    private static ConfigurableApplicationContext start() {
        byte[] secret = new byte[32]; new SecureRandom().nextBytes(secret);
        Map<String,Object> settings = Map.ofEntries(
                Map.entry("spring.profiles.active", "evaluation"), Map.entry("spring.config.location", "classpath:/application.yaml"),
                Map.entry("spring.datasource.url", URL), Map.entry("spring.datasource.username", env("EVALUATION_DB_USERNAME")),
                Map.entry("spring.datasource.password", env("EVALUATION_DB_PASSWORD")), Map.entry("spring.sql.init.mode", "never"),
                Map.entry("milvus.enabled", "false"), Map.entry("milvus.uri", "http://127.0.0.1:19532"),
                Map.entry("milvus.database-name", "default"), Map.entry("milvus.collection-name", "knowledge_chunk_vectors_evaluation"),
                Map.entry("deepseek.enabled", "false"), Map.entry("deepseek.base-url", "http://127.0.0.1:1"), Map.entry("deepseek.api-key", ""),
                Map.entry("embedding.base-url", "http://127.0.0.1:1"), Map.entry("embedding.api-key", "offline-import-placeholder"),
                Map.entry("jwt.secret", Base64.getEncoder().encodeToString(secret)), Map.entry("server.port", "-1"),
                Map.entry("logging.level.root", "OFF"), Map.entry("spring.main.banner-mode", "off"));
        var environment = new StandardServletEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("D1-evaluation-only", settings));
        var application = new SpringApplication(AiInterviewSystemApplication.class);
        application.setEnvironment(environment); application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setAddCommandLineProperties(false);
        return application.run();
    }

    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final QuestionAdminService admin;
    private final QuestionMapper questions;
    private final QuestionScoringPointMapper points;
    private final InterviewQuestionSnapshotFactory snapshots;
    private final List<CreateQuestionRequest> old, revised;
    private final String bankSha;

    private D1QuestionBankImport(ConfigurableApplicationContext context, List<CreateQuestionRequest> old, List<CreateQuestionRequest> revised, String bankSha) {
        require(Arrays.equals(context.getEnvironment().getActiveProfiles(), new String[]{"evaluation"}), "profile");
        jdbc = context.getBean(JdbcTemplate.class); transactions = context.getBean(PlatformTransactionManager.class);
        admin = context.getBean(QuestionAdminService.class); questions = context.getBean(QuestionMapper.class);
        points = context.getBean(QuestionScoringPointMapper.class); snapshots = context.getBean(InterviewQuestionSnapshotFactory.class);
        require(AopUtils.isAopProxy(admin), "transaction proxy");
        this.old = old; this.revised = revised;
        this.bankSha = bankSha;
    }

    private TransactionTemplate tx(boolean readOnly) {
        var template = new TransactionTemplate(transactions);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); template.setReadOnly(readOnly); template.setTimeout(60);
        return template;
    }

    private void guard(boolean lock) {
        require(TransactionSynchronizationManager.isActualTransactionActive(), "no transaction");
        require(IDENTITY.equals(jdbc.queryForObject("SELECT CONCAT(DATABASE(), ' ', @@server_uuid)", String.class)), "database identity");
        var names = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'", String.class);
        require(Set.copyOf(names).equals(Set.copyOf(TABLES)), "table set");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB'", Integer.class) == 13,
                "transactional InnoDB engines required");
        // These scans lock existing rows and insertion gaps under RR. No historical rows are changed.
        if (lock) for (String t : TABLES) jdbc.queryForList("SELECT " + primaryKey(t) + " FROM `" + t + "` ORDER BY " + primaryKey(t) + " FOR UPDATE");
    }

    private JsonNode state() {
        var node = JSON.createObjectNode();
        for (String t : TABLES) node.set(t, JSON.valueToTree(jdbc.queryForList("SELECT * FROM `" + t + "` ORDER BY " + primaryKey(t))));
        // Canonical JSON round trip aligns JDBC Long/Integer node types with the persisted baseline.
        try { return JSON.readTree(JSON.writeValueAsBytes(node)); }
        catch (java.io.IOException failure) { throw new GateFailure("row snapshot serialization"); }
    }

    private static String primaryKey(String table) { return table.equals("rag_retrieval_batch") ? "retrieval_batch_id" : "id"; }

    private JsonNode base() {
        try { return JSON.readTree(Files.readAllBytes(DIR.resolve("baseline-rows.json"))); }
        catch (Exception e) { throw new GateFailure("baseline missing"); }
    }

    private void baseline() throws Exception {
        JsonNode result = tx(true).execute(status -> {
            guard(false); verifyBank(old); JsonNode current = state();
            require(current.path("knowledge_document").size() == 1 && current.path("knowledge_chunk").size() == 2, "E01 cardinality");
            require(current.path("knowledge_document").get(0).path("content_hash").asText().equals("4b82a2724e53946fffa082560fde1c348201114ac4f490e409c2ddbba5071de0")
                    && current.path("knowledge_document").get(0).path("processing_status").asText().equals("READY"), "E01 identity");
            for (String t : TABLES) if (!Set.of("question", "question_scoring_point", "knowledge_document", "knowledge_chunk").contains(t))
                require(current.path(t).isEmpty(), "unexpected historical business rows: " + t);
            return current;
        });
        Files.writeString(DIR.resolve("baseline-rows.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result), StandardOpenOption.CREATE_NEW);
        System.out.println("Baseline exactly matches V1: 21 questions / 97 points; E01 retained; history empty.");
    }

    private void rehearse() throws Exception {
        JsonNode baseline = base();
        try {
            tx(false).executeWithoutResult(status -> {
                guard(true); require(state().equals(baseline), "baseline changed"); verifyBank(old);
                replaceH01();
                var additions = additions();
                for (var q : additions.subList(0, 41)) admin.createQuestion(q);
                var last = additions.getLast();
                var invalid = new CreateQuestionRequest(last.getCategory(), last.getKnowledgePoint(), last.getDifficulty(),
                        last.getQuestionContent(), last.getReferenceAnswer(), List.of(new ScoringPointRequest(QuestionPointType.CORE, "rollback probe", 99)));
                try { admin.createQuestion(invalid); }
                catch (BusinessException expected) {
                    require("评分点权重和必须为100".equals(expected.getMessage()), "unexpected rollback failure reason");
                    throw new DeliberateRollback();
                }
                throw new GateFailure("invalid weights accepted");
            });
            throw new GateFailure("expected rollback not observed");
        } catch (DeliberateRollback expected) { /* Entire update + 41 inserts must disappear. */ }
        tx(true).executeWithoutResult(status -> { guard(false); require(state().equals(baseline), "rollback changed data"); verifyBank(old); });
        Files.writeString(DIR.resolve("rollback-pass.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "status", "PASS", "bankSha256", bankSha, "baselineSha256", sha(DIR.resolve("baseline-rows.json")))), StandardOpenOption.CREATE_NEW);
        System.out.println("Rollback verified: H01 replacement + 41 creates rolled back; all 13 tables identical.");
    }

    private List<CreateQuestionRequest> additions() {
        var oldKeys = old.stream().map(CreateQuestionRequest::getKnowledgePoint).toList();
        var result = revised.stream().filter(q -> !oldKeys.contains(q.getKnowledgePoint())).toList();
        require(result.size() == 42, "additions count"); return result;
    }

    private void replaceH01() {
        var q = revised.stream().filter(r -> r.getKnowledgePoint().equals("generic_type_erasure")).findFirst().orElseThrow();
        Long id = jdbc.queryForObject("SELECT id FROM question WHERE knowledge_point=?", Long.class, q.getKnowledgePoint());
        var request = new UpdateQuestionRequest(q.getCategory(), q.getKnowledgePoint(), q.getDifficulty(), q.getQuestionContent(), q.getReferenceAnswer(), q.getScoringPoints());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            require(factory.getValidator().validate(request).isEmpty(), "update DTO");
        }
        admin.updateQuestion(id, request);
    }

    private void importBank() throws Exception {
        JsonNode baseline = base();
        JsonNode result = tx(false).execute(status -> {
            guard(true); require(state().equals(baseline), "baseline changed; no blind retry"); verifyBank(old);
            replaceH01();
            for (var q : additions()) require(admin.createQuestion(q) > 0, "generated ID");
            verifyBank(revised); verifyPreserved(baseline, state());
            return state();
        });
        // If receipt writing fails after COMMIT, verify mode reconciles; import will reject the now non-baseline bank.
        Files.writeString(DIR.resolve("committed-rows.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result), StandardOpenOption.CREATE_NEW);
        System.out.println("Committed H01 replacement + 42 additions atomically.");
    }

    private void verifyFinal() throws Exception {
        JsonNode result = tx(true).execute(status -> {
            guard(false); verifyBank(revised); verifyPreserved(base(), state()); return state();
        });
        Files.writeString(DIR.resolve("verified-rows.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result), StandardOpenOption.CREATE_NEW);
    }

    private void verifyPreserved(JsonNode before, JsonNode after) {
        for (String table : TABLES) if (!Set.of("question", "question_scoring_point").contains(table))
            require(before.path(table).equals(after.path(table)), "protected table changed: " + table);
        long h01 = -1;
        for (var q : before.path("question")) if (q.path("knowledge_point").asText().equals("generic_type_erasure")) h01 = q.path("id").asLong();
        require(h01 > 0, "H01 identity");
        for (String t : List.of("question", "question_scoring_point")) for (var row : before.path(t)) {
            if ((t.equals("question") ? row.path("id") : row.path("question_id")).asLong() == h01) continue;
            boolean match = false;
            for (var newRow : after.path(t)) if (newRow.equals(row)) { match = true; break; }
            require(match, "old row changed: " + t + "/" + row.path("id"));
        }
        final long id = h01;
        require(revised.stream().anyMatch(q -> q.getKnowledgePoint().equals("generic_type_erasure"))
                && after.path("question").findValues("id").stream().anyMatch(v -> v.asLong() == id), "H01 ID not retained");
    }

    private void verifyBank(List<CreateQuestionRequest> expected) {
        var byKey = new LinkedHashMap<String,CreateQuestionRequest>();
        expected.forEach(q -> require(byKey.putIfAbsent(q.getKnowledgePoint(), q) == null, "duplicate source"));
        List<Question> all = new ArrayList<>();
        for (var d : QuestionDifficulty.values()) all.addAll(questions.selectEnabledQuestionsForInterview(d));
        require(all.size() == expected.size() && jdbc.queryForObject("SELECT COUNT(*) FROM question", Integer.class) == expected.size(), "stored question count/status");
        int count = 0; Set<Long> ids = new HashSet<>(); Set<Long> pointIds = new HashSet<>();
        for (var q : all) {
            var e = byKey.remove(q.getKnowledgePoint());
            require(e != null && ids.add(q.getId()) && q.getId() > 0 && q.getStatus() == QuestionStatus.ENABLED
                    && q.getCategory() == e.getCategory() && q.getDifficulty() == e.getDifficulty()
                    && q.getQuestionContent().equals(e.getQuestionContent()) && q.getReferenceAnswer().equals(e.getReferenceAnswer())
                    && q.getCreatedAt() != null && q.getUpdatedAt() != null, "stored question mismatch");
            var ps = points.selectEnabledByQuestionId(q.getId());
            require(ps.size() == e.getScoringPoints().size(), "point count/status");
            for (int i=0;i<ps.size();i++) {
                var p = ps.get(i); var source = e.getScoringPoints().get(i);
                require(p.getId() > 0 && pointIds.add(p.getId()) && p.getQuestionId().equals(q.getId()) && p.getSortOrder() == i+1
                        && p.getStatus() == QuestionPointStatus.ENABLED && p.getPointType() == source.getPointType()
                        && p.getWeight() == source.getWeight() && p.getContent().equals(source.getContent())
                        && p.getCreatedAt()!=null && p.getUpdatedAt()!=null, "stored point mismatch");
            }
            InterviewQuestion frozen = snapshots.createMainQuestion(1L, q, ps, 1);
            require(frozen.getId() == null && frozen.getQuestionId().equals(q.getId())
                    && frozen.getQuestionContent().equals(e.getQuestionContent()) && frozen.getReferenceAnswerSnapshot().equals(e.getReferenceAnswer()), "in-memory snapshot fields");
            try {
                var parsed = JSON.readValue(frozen.getScoringPointsSnapshot(), new TypeReference<List<ScoringPointSnapshot>>(){});
                require(parsed.equals(ps.stream().map(p -> new ScoringPointSnapshot(p.getId(), p.getPointType(), p.getContent(), p.getWeight())).toList()), "snapshot point round trip");
            } catch (java.io.IOException failure) { throw new GateFailure("snapshot JSON"); }
            count += ps.size();
        }
        require(byKey.isEmpty() && count == jdbc.queryForObject("SELECT COUNT(*) FROM question_scoring_point", Integer.class), "orphan/disabled points");
        System.out.println("Verified all source fields, statuses, generated IDs, ordered points and in-memory snapshots: " + all.size() + "/" + count);
    }

    private static String env(String key) { String s = System.getenv(key); require(s != null && !s.isBlank(), "missing " + key); return s; }
    private static String sha(Path p) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))); }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void require(boolean ok, String message) { if (!ok) throw new GateFailure(message); }
    private static final class GateFailure extends RuntimeException { GateFailure(String message) { super(message); } }
    private static final class DeliberateRollback extends RuntimeException {}
}
