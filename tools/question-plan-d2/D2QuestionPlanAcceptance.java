import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.AiInterviewSystemApplication;
import com.kun.aiinterview.interview.config.InterviewPlanConfig;
import com.kun.aiinterview.interview.entity.InterviewQuestion;
import com.kun.aiinterview.interview.enums.InterviewQuestionStatus;
import com.kun.aiinterview.interview.enums.InterviewQuestionType;
import com.kun.aiinterview.interview.enums.InterviewReportStatus;
import com.kun.aiinterview.interview.enums.InterviewSessionStatus;
import com.kun.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.kun.aiinterview.interview.service.InterviewQuestionPlanSnapshotService;
import com.kun.aiinterview.interview.service.InterviewSessionService;
import com.kun.aiinterview.interview.service.InterviewSessionTransactionService;
import com.kun.aiinterview.question.dto.CreateQuestionRequest;
import com.kun.aiinterview.question.dto.ScoringPointRequest;
import com.kun.aiinterview.question.dto.UpdateQuestionRequest;
import com.kun.aiinterview.question.dto.UpdateQuestionStatusRequest;
import com.kun.aiinterview.question.entity.Question;
import com.kun.aiinterview.question.entity.QuestionScoringPoint;
import com.kun.aiinterview.question.enums.QuestionDifficulty;
import com.kun.aiinterview.question.enums.QuestionPointStatus;
import com.kun.aiinterview.question.enums.QuestionPointType;
import com.kun.aiinterview.question.enums.QuestionStatus;
import com.kun.aiinterview.question.mapper.QuestionMapper;
import com.kun.aiinterview.question.mapper.QuestionScoringPointMapper;
import com.kun.aiinterview.question.service.QuestionAdminService;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.UserRole;
import com.kun.aiinterview.user.enums.UserStatus;
import com.kun.aiinterview.user.mapper.UserMapper;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.support.StandardServletEnvironment;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Bounded D2 acceptance. All fixture writes take place in transactions marked for rollback. */
public final class D2QuestionPlanAcceptance {
    private static final String TARGET = "127.0.0.1:3308/ai_interview_evaluation";
    private static final String SERVER_UUID = "85dd1e00-cf98-11f1-8677-9eb9380f25e7";
    private static final String URL = "jdbc:mysql://" + TARGET
            + "?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&sslMode=DISABLED"
            + "&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=12000";
    private static final Path BANK = Path.of("docs/question-bank/D1_QUESTION_BANK_V2_20261114.json");
    private static final String BANK_SHA = "d4c1d9f20664308231ab383f5dce67154f3c5d874fc041b44f7499ce8647177e";
    private static final Path MANIFEST = Path.of("docs/question-bank/D1_EVALUATION_IMPORT_MANIFEST_20261114.json");
    private static final String MANIFEST_SHA = "dfdd58d2a9d4f108178ae068e45afa910a676046264d67ddb841c72816644f74";
    private static final List<String> TABLES = List.of("user", "question", "question_scoring_point", "interview_session",
            "interview_question", "interview_answer", "answer_evaluation", "knowledge_document", "knowledge_chunk",
            "rag_retrieval_batch", "rag_hit_log", "interview_report", "user_weakness");
    private static final Map<QuestionDifficulty, Integer> PLANNED_COUNTS = Map.of(
            QuestionDifficulty.EASY, 5, QuestionDifficulty.MEDIUM, 7, QuestionDifficulty.HARD, 9);
    private static final int SAMPLES_PER_DIFFICULTY = 8;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path work;
    private final Credentials credentials;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final InterviewPlanConfig plans;
    private final InterviewSessionService sessions;
    private final InterviewQuestionMapper snapshots;
    private final UserMapper users;
    private final QuestionMapper questions;
    private final QuestionScoringPointMapper points;
    private final QuestionAdminService admin;

    public static void main(String[] args) {
        try {
            require(args.length == 2 && Set.of("baseline", "exercise").contains(args[0]), "usage: baseline|exercise evidence-dir");
            Path work = Path.of(args[1]).toRealPath();
            require(Files.isDirectory(work), "evidence directory required");
            boolean exercise = args[0].equals("exercise");
            if (exercise) verifyBackup(work);
            var credentials = new Credentials(env("EVALUATION_DB_USERNAME"), env("EVALUATION_DB_PASSWORD"));
            try (var context = start(credentials)) {
                var runner = new D2QuestionPlanAcceptance(work, credentials, context);
                if (exercise) runner.exercise();
                else runner.baseline();
            }
            System.out.println("PASS " + args[0] + " target=" + TARGET + " serverUuid=" + SERVER_UUID);
        } catch (Exception failure) {
            // Report only tool-authored gate messages and safe exception metadata.
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                String detail = cause instanceof GateFailure ? ": " + cause.getMessage() : "";
                System.err.println(cause.getClass().getSimpleName() + detail);
                for (StackTraceElement frame : cause.getStackTrace()) {
                    if (frame.getClassName().equals(D2QuestionPlanAcceptance.class.getName())) {
                        System.err.println("  " + frame.getMethodName() + ":" + frame.getLineNumber());
                        break;
                    }
                }
            }
            System.exit(1);
        }
    }

    private static ConfigurableApplicationContext start(Credentials credentials) {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        Map<String, Object> settings = Map.ofEntries(
                Map.entry("spring.profiles.active", "evaluation"),
                Map.entry("spring.config.location", "classpath:/application.yaml"),
                Map.entry("spring.datasource.url", URL),
                Map.entry("spring.datasource.username", credentials.username()),
                Map.entry("spring.datasource.password", credentials.password()),
                Map.entry("spring.datasource.hikari.maximum-pool-size", "4"),
                Map.entry("spring.datasource.hikari.connection-timeout", "5000"),
                Map.entry("spring.sql.init.mode", "never"),
                Map.entry("milvus.enabled", "false"),
                Map.entry("milvus.uri", "http://127.0.0.1:19532"),
                Map.entry("milvus.database-name", "default"),
                Map.entry("milvus.collection-name", "knowledge_chunk_vectors_evaluation"),
                Map.entry("deepseek.enabled", "false"),
                Map.entry("deepseek.base-url", "http://127.0.0.1:1"),
                Map.entry("embedding.base-url", "http://127.0.0.1:1"),
                Map.entry("embedding.api-key", "offline-d2-placeholder"),
                Map.entry("jwt.secret", Base64.getEncoder().encodeToString(secret)),
                Map.entry("server.port", "-1"),
                Map.entry("logging.level.root", "OFF"),
                Map.entry("spring.main.banner-mode", "off"));
        var environment = new StandardServletEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("d2-evaluation-only", settings));
        var application = new SpringApplication(AiInterviewSystemApplication.class);
        application.setEnvironment(environment);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setAddCommandLineProperties(false);
        return application.run();
    }

    private D2QuestionPlanAcceptance(Path work, Credentials credentials, ConfigurableApplicationContext context) {
        require(Arrays.equals(context.getEnvironment().getActiveProfiles(), new String[]{"evaluation"}), "evaluation profile only");
        this.work = work;
        this.credentials = credentials;
        jdbc = context.getBean(JdbcTemplate.class);
        transactions = context.getBean(PlatformTransactionManager.class);
        plans = context.getBean(InterviewPlanConfig.class);
        sessions = context.getBean(InterviewSessionService.class);
        snapshots = context.getBean(InterviewQuestionMapper.class);
        users = context.getBean(UserMapper.class);
        questions = context.getBean(QuestionMapper.class);
        points = context.getBean(QuestionScoringPointMapper.class);
        admin = context.getBean(QuestionAdminService.class);
        require(AopUtils.isAopProxy(admin)
                && AopUtils.isAopProxy(context.getBean(InterviewQuestionPlanSnapshotService.class))
                && AopUtils.isAopProxy(context.getBean(InterviewSessionTransactionService.class)), "transaction proxies required");
    }

    private void baseline() throws Exception {
        var bank = loadD1Bank();
        var baseline = transaction(true).execute(status -> {
            var tables = currentRows();
            verifyBank(bank, tables);
            return tables;
        });
        require(independentRows().equals(baseline), "row baseline moved during read-only audit");
        writeJson("baseline.json", baseline);
        System.out.println("PASS 63 enabled questions, 298 effective points, 21 buckets × 3; 13-table baseline recorded");
    }

    private void exercise() throws Exception {
        var bank = loadD1Bank();
        var baseline = JSON.readTree(work.resolve("baseline.json").toFile());
        transaction(true).executeWithoutResult(status -> {
            var tables = currentRows();
            require(tables.equals(baseline), "row baseline changed before exercise");
            verifyBank(bank, tables);
        });

        String prefix = "d2_" + UUID.randomUUID().toString().replace("-", "");
        var ordinary = new ArrayList<Probe>();
        var parallel = new ArrayList<Probe>();
        for (var difficulty : QuestionDifficulty.values()) {
            for (int i = 0; i < SAMPLES_PER_DIFFICULTY; i++) {
                ordinary.add(new Probe(prefix + "_" + difficulty.name().charAt(0) + i, difficulty));
            }
            parallel.add(new Probe(prefix + "_p" + difficulty.name().charAt(0), difficulty));
        }
        var missing = new Probe(prefix + "_missing", QuestionDifficulty.EASY);
        var history = new Probe(prefix + "_history", QuestionDifficulty.EASY);
        var firstEasyCategory = plans.getRule(QuestionDifficulty.EASY).requiredCategories().getFirst();
        var bucket = questions.selectEnabledQuestionsForInterview(QuestionDifficulty.EASY).stream()
                .filter(q -> q.getCategory() == firstEasyCategory).map(Question::getId).toList();
        require(bucket.size() == 3, "temporary disabled bucket drift");
        Files.createFile(work.resolve("writes.jsonl"));
        writeJson("write-plan.json", Map.of(
                "target", TARGET, "serverUuid", SERVER_UUID, "serial", ordinary, "concurrent", parallel,
                "missingCategory", missing, "historicalSnapshot", history,
                "temporarilyDisabledQuestionIds", bucket, "temporaryQuestionKnowledgePoint", history.account(),
                "rowLimits", Map.of("user", 29, "interview_session", 28, "interview_question", 194,
                        "question", 1, "question_scoring_point", 2)));

        var samples = new ArrayList<Draw>();
        var result = new LinkedHashMap<String, Object>();
        try {
            for (var probe : ordinary) {
                samples.add(rollback(baseline, () -> create(probe, insertUser(probe), null)));
            }
            var concurrent = concurrent(baseline, parallel);
            rollback(baseline, () -> {
                testMissingCategory(missing, firstEasyCategory.name(), bucket);
                return null;
            });
            var historical = rollback(baseline, () -> testHistoricalSnapshot(history, bucket));
            result.put("target", TARGET);
            result.put("serverUuid", SERVER_UUID);
            result.put("serial", samples);
            result.put("concurrent", concurrent);
            result.put("historicalSnapshot", historical);
            result.put("missingCategoryRejected", firstEasyCategory);
            result.put("diagnosticDistribution", distribution(samples));
            result.put("rollbackChecks", 29);
        } finally {
            require(independentRows().equals(baseline), "final independent row reconciliation differs; preserve evidence");
        }
        result.put("tableRowsMatchBaseline", true);
        writeJson("result.json", result);
        System.out.println("PASS 28 rolled-back Sessions, 194 MAIN snapshots, 29 independent rollback reconciliations");
    }

    private List<Draw> concurrent(JsonNode baseline, List<Probe> probes) throws Exception {
        var barrier = new CyclicBarrier(probes.size());
        var executor = Executors.newFixedThreadPool(probes.size());
        try {
            List<Callable<Draw>> work = probes.stream().<Callable<Draw>>map(probe -> () -> rollback(baseline, () -> {
                long userId = insertUser(probe);
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new GateFailure("concurrent fixtures did not reach start barrier", failure);
                }
                return create(probe, userId, null);
            })).toList();
            var draws = new ArrayList<Draw>();
            for (var future : executor.invokeAll(work, 30, TimeUnit.SECONDS)) draws.add(future.get());
            require(draws.stream().map(Draw::userId).distinct().count() == probes.size()
                    && draws.stream().map(Draw::sessionId).distinct().count() == probes.size(), "concurrent Session identity isolation");
            return List.copyOf(draws);
        } finally {
            executor.shutdownNow();
            require(executor.awaitTermination(15, TimeUnit.SECONDS), "concurrent fixture rollback did not finish");
        }
    }

    private <T> T rollback(JsonNode baseline, Supplier<T> body) {
        try {
            return transaction(false).execute(status -> {
                try {
                    require(currentRows().equals(baseline), "row baseline moved before fixture write");
                    return body.get();
                } finally {
                    status.setRollbackOnly();
                }
            });
        } finally {
            require(independentRows().equals(baseline), "independent rollback reconciliation differs; preserve evidence");
        }
    }

    private long insertUser(Probe probe) {
        String email = probe.account() + "@example.invalid";
        require(users.getUserByAccount(probe.account()) == null && users.getUserByEmail(email) == null, "fixture identity collision");
        var user = User.builder().account(probe.account()).username("D2 rollback probe")
                .password("d2-fixture-cannot-authenticate").email(email).role(UserRole.USER).status(UserStatus.DISABLED).build();
        require(users.insertUser(user) == 1 && user.getId() != null && user.getId() > 0, "temporary user insertion");
        record("user", Map.of("account", probe.account(), "userId", user.getId()));
        return user.getId();
    }

    private Draw create(Probe probe, long userId, Long forcedQuestionId) {
        var difficulty = probe.difficulty();
        var rule = plans.getRule(difficulty);
        var session = sessions.createSession(userId, difficulty);
        require(session != null && session.getId() != null && session.getId() > 0, "Session persistence");
        record("session", Map.of("userId", userId, "sessionId", session.getId(), "difficulty", difficulty));
        var rows = snapshots.listBySessionId(session.getId());
        record("snapshots", Map.of("sessionId", session.getId(), "snapshotIds", rows.stream().map(InterviewQuestion::getId).toList()));
        require(Objects.equals(session.getUserId(), userId) && session.getDifficulty() == difficulty
                && session.getStatus() == InterviewSessionStatus.IN_PROGRESS && session.getVersion() == 1
                && PLANNED_COUNTS.get(difficulty).equals(session.getPlannedQuestionCount())
                && session.getCompletedQuestionCount() == 0 && session.getStartedAt() != null
                && session.getReportStatus() == InterviewReportStatus.NOT_STARTED, "Session ownership, start CAS and counters");
        require(rows.size() == rule.plannedQuestionCount() && rows.size() == PLANNED_COUNTS.get(difficulty), "exact 5/7/9 MAIN count");
        require(Objects.equals(session.getCurrentInterviewQuestionId(), rows.getFirst().getId()), "Session points at first MAIN");

        var seen = new HashSet<Long>();
        var draws = new ArrayList<Selected>();
        for (int index = 0; index < rows.size(); index++) {
            int planOrder = index + 1;
            var row = rows.get(index);
            require(Objects.equals(row.getSessionId(), session.getId()) && row.getQuestionType() == InterviewQuestionType.MAIN
                    && row.getPlanOrder() == planOrder && row.getDisplayOrder() == planOrder * 2 - 1
                    && row.getParentQuestionId() == null && row.getFollowUpTargetPoints() == null
                    && row.getCategory() == rule.requiredCategories().get(index) && seen.add(row.getQuestionId())
                    && row.getStatus() == (index == 0 ? InterviewQuestionStatus.WAITING_ANSWER : InterviewQuestionStatus.PENDING),
                    "MAIN ownership, category coverage, ordering and state");
            var source = questions.getQuestionById(row.getQuestionId());
            var sourcePoints = points.selectEnabledByQuestionId(row.getQuestionId());
            require(source != null && source.getDifficulty() == difficulty && source.getStatus() == QuestionStatus.ENABLED
                    && row.getCategory() == source.getCategory() && row.getKnowledgePoint().equals(source.getKnowledgePoint())
                    && row.getQuestionContent().equals(source.getQuestionContent())
                    && row.getReferenceAnswerSnapshot().equals(source.getReferenceAnswer())
                    && parse(row.getScoringPointsSnapshot()).equals(frozenPoints(sourcePoints)), "frozen question, answer and ordered scoring fields");
            draws.add(new Selected(planOrder, row.getDisplayOrder(), row.getCategory().name(), row.getQuestionId(), row.getId(),
                    sourcePoints.stream().map(QuestionScoringPoint::getId).toList()));
        }
        if (forcedQuestionId != null) require(seen.contains(forcedQuestionId), "isolated historical question was not selected");
        var repeated = sessions.createSession(userId, difficulty == QuestionDifficulty.EASY ? QuestionDifficulty.HARD : QuestionDifficulty.EASY);
        require(session.equals(repeated) && rows.equals(snapshots.listBySessionId(session.getId()))
                && jdbc.queryForObject("SELECT COUNT(*) FROM interview_session WHERE user_id=?", Integer.class, userId) == 1,
                "replay preserves the active Session and its exact snapshots");
        return new Draw(probe.account(), userId, session.getId(), difficulty, List.copyOf(draws));
    }

    private void testMissingCategory(Probe probe, String category, List<Long> bucket) {
        long userId = insertUser(probe);
        disableBucket(probe, bucket);
        try {
            sessions.createSession(userId, probe.difficulty());
            throw new GateFailure("missing required category accepted");
        } catch (IllegalStateException rejected) {
            require(rejected.getMessage() != null && rejected.getMessage().contains(category), "missing category must be identified");
        }
        require(jdbc.queryForObject("SELECT COUNT(*) FROM interview_session WHERE user_id=?", Integer.class, userId) == 0,
                "missing category wrote a Session");
    }

    private Draw testHistoricalSnapshot(Probe probe, List<Long> bucket) {
        long userId = insertUser(probe);
        disableBucket(probe, bucket);
        var category = plans.getRule(probe.difficulty()).requiredCategories().getFirst();
        long questionId = admin.createQuestion(new CreateQuestionRequest(category, probe.account(), probe.difficulty(),
                "D2 temporary original question", "D2 temporary original answer",
                List.of(new ScoringPointRequest(QuestionPointType.CORE, "D2 temporary original point", 100))));
        var originalPointIds = points.selectEnabledByQuestionId(questionId).stream().map(QuestionScoringPoint::getId).toList();
        record("temporary-question", Map.of("account", probe.account(), "questionId", questionId, "pointIds", originalPointIds));
        var draw = create(probe, userId, questionId);
        var before = snapshots.listBySessionId(draw.sessionId());
        admin.updateQuestion(questionId, new UpdateQuestionRequest(category, probe.account(), probe.difficulty(),
                "D2 temporary edited question", "D2 temporary edited answer",
                List.of(new ScoringPointRequest(QuestionPointType.KEY, "D2 temporary edited point", 100))));
        var changed = questions.getQuestionById(questionId);
        var changedPoints = points.selectEnabledByQuestionId(questionId);
        record("temporary-question-replacement", Map.of("questionId", questionId,
                "pointIds", changedPoints.stream().map(QuestionScoringPoint::getId).toList()));
        require(changed.getQuestionContent().equals("D2 temporary edited question")
                && changed.getReferenceAnswer().equals("D2 temporary edited answer")
                && changedPoints.size() == 1 && !originalPointIds.contains(changedPoints.getFirst().getId())
                && changedPoints.getFirst().getContent().equals("D2 temporary edited point"), "administrator replacement persisted inside probe");
        require(before.equals(snapshots.listBySessionId(draw.sessionId())), "administrator edit changed frozen history");
        return draw;
    }

    private void disableBucket(Probe probe, List<Long> bucket) {
        for (long id : bucket) admin.updateQuestionStatus(id, new UpdateQuestionStatusRequest(QuestionStatus.DISABLED));
        record("temporary-question-status", Map.of("account", probe.account(), "questionIds", bucket));
        var returned = questions.selectEnabledQuestionsForInterview(probe.difficulty()).stream().map(Question::getId).toList();
        require(bucket.stream().noneMatch(returned::contains), "disabled questions leaked into SQL candidates");
    }

    private ObjectNode distribution(List<Draw> samples) {
        var distribution = JSON.createObjectNode();
        for (var difficulty : QuestionDifficulty.values()) {
            var draws = samples.stream().filter(d -> d.difficulty() == difficulty).toList();
            var byCategory = new TreeMap<String, Map<Long, Integer>>();
            for (var candidate : questions.selectEnabledQuestionsForInterview(difficulty)) {
                byCategory.computeIfAbsent(candidate.getCategory().name(), key -> new TreeMap<>()).put(candidate.getId(), 0);
            }
            for (var draw : draws) for (var selected : draw.questions()) {
                byCategory.get(selected.category()).merge(selected.questionId(), 1, Integer::sum);
            }
            long unique = draws.stream().map(d -> d.questions().stream().map(Selected::questionId).toList()).distinct().count();
            distribution.set(difficulty.name(), JSON.valueToTree(Map.of("drawCount", draws.size(), "distinctCombinations", unique,
                    "frequencyByCategoryAndQuestionId", byCategory)));
        }
        return distribution;
    }

    private static Map<String, JsonNode> loadD1Bank() throws IOException {
        byte[] bankBytes = Files.readAllBytes(BANK);
        byte[] manifestBytes = Files.readAllBytes(MANIFEST);
        require(sha(bankBytes).equals(BANK_SHA) && sha(manifestBytes).equals(MANIFEST_SHA), "D1 source or manifest bytes changed");
        var mapping = new HashMap<String, JsonNode>();
        for (var row : JSON.readTree(manifestBytes).path("databaseIdMapping")) mapping.put(row.path("bankId").asText(), row);
        var items = new HashMap<String, JsonNode>();
        for (var question : JSON.readTree(bankBytes).path("questions")) {
            String bankId = question.path("bankId").asText();
            String knowledgePoint = question.path("knowledgePoint").asText();
            var ids = mapping.remove(bankId);
            require(ids != null && ids.path("knowledgePoint").asText().equals(knowledgePoint)
                    && ids.path("status").asText().equals("ENABLED") && ids.path("revision").equals(question.path("revision")), "D1 identity mapping");
            require(items.putIfAbsent(knowledgePoint, ids) == null, "duplicate D1 knowledgePoint");
        }
        require(items.size() == 63 && mapping.isEmpty(), "D1 catalog cardinality");
        return Map.copyOf(items);
    }

    private void verifyBank(Map<String, JsonNode> bank, JsonNode tables) {
        require(tables.path("question").path("rows").asInt() == 63
                && tables.path("question_scoring_point").path("rows").asInt() == 298, "expected 63 questions and 298 scoring point rows");
        int totalPoints = 0;
        var questionIds = new HashSet<Long>();
        var pointIds = new HashSet<Long>();
        for (var difficulty : QuestionDifficulty.values()) {
            var rule = plans.getRule(difficulty);
            require(rule.plannedQuestionCount() == PLANNED_COUNTS.get(difficulty)
                    && rule.requiredCategories().size() == rule.plannedQuestionCount(), "D2 plan shape " + difficulty);
            var actual = questions.selectEnabledQuestionsForInterview(difficulty);
            require(actual.size() == 3 * PLANNED_COUNTS.get(difficulty), "D1 difficulty count " + difficulty);
            for (var category : rule.requiredCategories()) {
                require(actual.stream().filter(q -> q.getCategory() == category).count() == 3, "D1 candidate bucket " + difficulty + "/" + category);
            }
            for (var question : actual) {
                var expected = bank.get(question.getKnowledgePoint());
                require(expected != null && questionIds.add(question.getId()) && question.getDifficulty() == difficulty
                        && question.getStatus() == QuestionStatus.ENABLED
                        && question.getId() == expected.path("questionId").asLong(), "D1 stored identity/status");
                var scoringPoints = points.selectEnabledByQuestionId(question.getId());
                require(scoringPoints.size() >= 4 && scoringPoints.size() <= 5
                        && scoringPoints.size() == expected.path("pointCount").asInt(), "D1 point count");
                int weights = 0;
                for (int i = 0; i < scoringPoints.size(); i++) {
                    var point = scoringPoints.get(i);
                    require(pointIds.add(point.getId()) && point.getId() == expected.path("pointIds").path(i).asLong()
                            && point.getStatus() == QuestionPointStatus.ENABLED && point.getSortOrder() == i + 1
                            && point.getQuestionId().equals(question.getId()) && !point.getContent().isBlank()
                            && point.getWeight() >= 1 && point.getWeight() <= 100, "D1 point identity/order/status");
                    weights += point.getWeight();
                }
                require(weights == 100 && scoringPoints.stream().anyMatch(p -> p.getPointType() == QuestionPointType.CORE), "D1 effective scoring weights");
                require(contentFingerprint(question, scoringPoints).equals(expected.path("requestSha256").asText()), "D1 content fingerprint changed");
                totalPoints += scoringPoints.size();
            }
        }
        require(questionIds.size() == 63 && totalPoints == 298 && pointIds.size() == 298, "D1 stored cardinality");
    }

    private static String contentFingerprint(Question question, List<QuestionScoringPoint> points) {
        // The D1 compact fingerprint sorts keys and preserves the original UTF-8 text.
        var dto = JSON.createObjectNode().put("category", question.getCategory().name())
                .put("difficulty", question.getDifficulty().name()).put("knowledgePoint", question.getKnowledgePoint())
                .put("questionContent", question.getQuestionContent()).put("referenceAnswer", question.getReferenceAnswer());
        var scoring = dto.putArray("scoringPoints");
        for (var point : points) scoring.addObject().put("content", point.getContent())
                .put("pointType", point.getPointType().name()).put("weight", point.getWeight());
        return sha(dto.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JsonNode frozenPoints(List<QuestionScoringPoint> points) {
        var result = JSON.createArrayNode();
        for (var point : points) result.addObject().put("id", point.getId())
                .put("pointType", point.getPointType().name()).put("content", point.getContent()).put("weight", point.getWeight());
        // Match the normal JSON parser's numeric node types for small and large identifiers.
        return parse(result.toString());
    }

    private TransactionTemplate transaction(boolean readOnly) {
        var transaction = new TransactionTemplate(transactions);
        transaction.setReadOnly(readOnly);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(20);
        return transaction;
    }

    private JsonNode currentRows() {
        return jdbc.execute((ConnectionCallback<JsonNode>) D2QuestionPlanAcceptance::readRows);
    }

    private JsonNode independentRows() {
        try (var connection = DriverManager.getConnection(URL, credentials.username(), credentials.password())) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                return readRows(connection);
            } finally {
                connection.rollback();
            }
        } catch (SQLException failure) {
            throw new GateFailure("independent MySQL readback failed", failure);
        }
    }

    private static JsonNode readRows(Connection connection) throws SQLException {
        require(connection.getMetaData().getURL().equals(URL) && !connection.getAutoCommit(), "fixed JDBC URL and transaction required");
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (var result = statement.executeQuery("SELECT DATABASE(), @@server_uuid, VERSION()")) {
                require(result.next() && "ai_interview_evaluation".equals(result.getString(1))
                        && SERVER_UUID.equals(result.getString(2)) && result.getString(3).startsWith("8.0.45"), "database identity/version mismatch");
            }
            try (var result = statement.executeQuery("SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) {
                var names = new HashSet<String>();
                while (result.next()) {
                    require("InnoDB".equals(result.getString(2)), "expected transactional InnoDB tables");
                    names.add(result.getString(1));
                }
                require(names.equals(Set.copyOf(TABLES)), "unexpected evaluation table set");
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()")) {
                require(result.next() && result.getInt(1) == 0, "unexpected evaluation triggers");
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE"
                    + " WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_SCHEMA IS NOT NULL AND REFERENCED_TABLE_SCHEMA<>DATABASE()")) {
                require(result.next() && result.getInt(1) == 0, "cross-schema foreign keys are outside fixture scope");
            }
            var tables = JSON.createObjectNode();
            for (var table : TABLES) {
                var digest = digest();
                String key = table.equals("rag_retrieval_batch") ? "retrieval_batch_id" : "id";
                try (var rows = statement.executeQuery("SELECT * FROM `" + table + "` ORDER BY " + key)) {
                    int count = 0;
                    int columns = rows.getMetaData().getColumnCount();
                    while (rows.next()) {
                        for (int col = 1; col <= columns; col++) {
                            byte[] bytes = rows.getBytes(col);
                            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes == null ? -1 : bytes.length).array());
                            if (bytes != null) digest.update(bytes);
                        }
                        count++;
                    }
                    tables.putObject(table).put("rows", count).put("sha256", HexFormat.of().formatHex(digest.digest()));
                }
            }
            return tables;
        }
    }

    private static void verifyBackup(Path work) throws IOException {
        var receipt = JSON.readTree(work.resolve("backup-receipt.json").toFile());
        String file = receipt.path("backupFile").asText();
        require(TARGET.equals(receipt.path("target").asText()) && SERVER_UUID.equals(receipt.path("serverUuid").asText())
                && file.matches("[A-Za-z0-9_-]+\\.sql"), "backup receipt target or path mismatch");
        var backup = work.resolve(file);
        require(Files.isRegularFile(backup) && !Files.isSymbolicLink(backup) && Files.size(backup) > 0
                && sha(Files.readAllBytes(backup)).equals(receipt.path("backupSha256").asText()), "verified evaluation backup required");
    }

    private void writeJson(String file, Object value) throws IOException {
        Files.writeString(work.resolve(file), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n", StandardOpenOption.CREATE_NEW);
    }

    private synchronized void record(String kind, Map<String, ?> value) {
        var row = JSON.createObjectNode().put("kind", kind);
        row.set("data", JSON.valueToTree(value));
        try {
            Files.writeString(work.resolve("writes.jsonl"), row + "\n", StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new GateFailure("fixture identity journal write failed", failure);
        }
    }

    private static JsonNode parse(String value) {
        try {
            return JSON.readTree(value);
        } catch (IOException failure) {
            throw new GateFailure("scoring snapshot JSON could not be read", failure);
        }
    }

    private static String env(String key) {
        String value = System.getenv(key);
        require(value != null && !value.isBlank(), "missing " + key);
        return value;
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String sha(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GateFailure(message);
    }

    private record Credentials(String username, String password) {}
    private record Probe(String account, QuestionDifficulty difficulty) {}
    private record Selected(int planOrder, int displayOrder, String category, long questionId, long snapshotId, List<Long> pointIds) {}
    private record Draw(String account, long userId, long sessionId, QuestionDifficulty difficulty, List<Selected> questions) {}

    private static final class GateFailure extends RuntimeException {
        GateFailure(String message) { super(message); }
        GateFailure(String message, Throwable cause) { super(message, cause); }
    }
}
