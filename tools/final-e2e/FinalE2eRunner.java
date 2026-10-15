import com.fasterxml.jackson.databind.*;
import com.kun.aiinterview.AiInterviewSystemApplication;
import com.kun.aiinterview.interview.evaluation.*;
import com.kun.aiinterview.interview.evaluation.score.*;
import com.kun.aiinterview.interview.evaluation.validation.*;
import com.kun.aiinterview.interview.evaluation.standard.*;
import com.kun.aiinterview.interview.evaluation.decision.*;
import com.kun.aiinterview.interview.mapper.AnswerEvaluationMapper;
import com.kun.aiinterview.interview.service.InterviewService;
import io.milvus.v2.client.*;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.collection.request.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.math.*;
import java.util.*;
import java.util.stream.Collectors;

/** One complete interview through real public HTTP APIs; all direct SQL is SELECT only. */
public class FinalE2eRunner {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final String RUN_ID = "final-e2e-20261013-001";
    static final String AUTHORIZATION = "AUTHORIZE_FINAL_E2E_ONCE_MAX_5_CNY";
    static Path output;
    static final Map<String,Object> report = new LinkedHashMap<>();
    static final List<Map<String,Object>> failures = Collections.synchronizedList(new ArrayList<>());
    static final List<Map<String,Object>> httpRecords = new ArrayList<>();
    static final List<Map<String,Object>> evaluations = new ArrayList<>();
    static final Map<String,Map<String,String>> historicalRows = new TreeMap<>();
    static String base, token = "";
    static long userId, sessionId;
    static long continuationSessionId;
    static FinalE2eGate gate;
    static ConfigurableApplicationContext app;
    static String judgeProvider = "jev";
    static void check(boolean value, String code) { RagE2ePolicy.require(value, code); }
    static String env(String name) { return Objects.toString(System.getenv(name), ""); }
    static void save(Path file, Object value) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value), StandardCharsets.UTF_8);
    }
    static List<Map<String,String>> sql(Connection c, String query, Object... args) throws Exception {
        check(query.stripLeading().toUpperCase(Locale.ROOT).startsWith("SELECT "), "DIRECT_SQL_SELECT_ONLY");
        return RagE2eRunner.select(c, query, args);
    }
    static Connection connection() throws Exception {
        check(!env("FINAL_DB_USERNAME").isBlank() && !env("FINAL_DB_PASSWORD").isBlank(), "DATABASE_CREDENTIAL_MISSING");
        Connection c = DriverManager.getConnection(RagE2ePolicy.jdbc(RagE2ePolicy.TARGET), env("FINAL_DB_USERNAME"), env("FINAL_DB_PASSWORD"));
        c.setReadOnly(true);
        try (Statement s = c.createStatement()) { s.execute("SET SESSION TRANSACTION READ ONLY"); }
        c.setAutoCommit(false);
        check("1".equals(sql(c, "SELECT @@session.transaction_read_only AS ro").getFirst().get("ro")), "SQL_READ_ONLY_REQUIRED");
        return c;
    }
    static Map<String,Object> budget() {
        // USD converted at a conservative planning factor of 8 CNY/USD, not a live exchange quote.
        double upper = 10 * 8320 * .5 / 1_000_000
                + (10 * FinalE2eGate.BODY_CAP * .042
                + 21 * (FinalE2eGate.BODY_CAP * .3 + 4096 * 1.2)) / 1_000_000 * 8;
        check(upper < 5, "PLANNED_BUDGET_EXCEEDED");
        Map<String,Object> budget = new LinkedHashMap<>();
        budget.put("plannedMainQuestions", 5); budget.put("maximumFollowUps", 5);
        budget.put("normalRequestsWithoutFollowUp", Map.of("embedding",5,"jev",5,"deepseek",6));
        budget.put("maximumProviderRequests", Map.of("embedding",10,"jev",10,"deepseek",21));
        budget.put("deepseekMaximumExplanation", "Each answer: feedback then at most one production fallback; report: one request.");
        budget.put("expectedCny", "0.10-0.60 (estimate, not account billing evidence)");
        budget.put("conservativeBodyBasedUpperCny", upper); budget.put("proposedBudgetCny", 5);
        budget.put("priceSources", List.of("https://docs.typesafe.ai/models", "https://api-docs.deepseek.com/quick_start/pricing/",
                "https://help.aliyun.com/en/model-studio/qwen3-7-text-embedding"));
        budget.put("rates", Map.of("embeddingCnyPerMillionInput",.5,"jevUsdPerMillionInput",.042,
                "deepseekUsdPerMillionUncachedInputPeak",.3,"deepseekUsdPerMillionOutputPeak",1.2,"planningCnyPerUsd",8));
        budget.put("limitMeaning", "Forwarded HTTP counts and body/output caps are enforced; token estimate is not a provider account spending cap.");
        return budget;
    }
    static Map<String,Object> preflight(Connection c) throws Exception {
        RagE2eRunner.loadContract();
        var identity = sql(c, "SELECT DATABASE() AS db, @@server_uuid AS uuid").getFirst();
        check(RagE2ePolicy.TARGET.equals(identity.get("db")) && RagE2ePolicy.SERVER_UUID.equals(identity.get("uuid")), "MYSQL_IDENTITY");
        var columns = sql(c, "SELECT TABLE_NAME AS `table`, COLUMN_NAME AS `column`, COLUMN_TYPE AS `type`, "
                + "IS_NULLABLE AS nullable, COLUMN_KEY AS `key`, EXTRA AS extra FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION");
        check(JSON.valueToTree(columns.stream().filter(r -> RagE2ePolicy.TABLES.contains(r.get("table"))).toList())
                .equals(RagE2eRunner.contract.path("sqlColumns")), "DATABASE_SCHEMA_DIFFERS_FROM_EXISTING_CONTRACT");
        check(sql(c,"SELECT COUNT(*) AS n FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() "
                + "AND REFERENCED_TABLE_SCHEMA<>DATABASE()").getFirst().get("n").equals("0"), "CROSS_SCHEMA_FK");
        Map<String,Object> corpus = verifyCorpus(c);
        Map<String,Object> vectors;
        MilvusClientV2 client = new MilvusClientV2(ConnectConfig.builder().uri("http://127.0.0.1:19530")
                .dbName("default").connectTimeoutMs(5000L).rpcDeadlineMs(20000L).build());
        try {
            client.retryConfig(RetryConfig.builder().maxRetryTimes(1).build());
            check(Boolean.TRUE.equals(client.hasCollection(HasCollectionReq.builder().collectionName(RagE2ePolicy.COLLECTION).build())), "TEST_COLLECTION_MISSING");
            var description = client.describeCollection(DescribeCollectionReq.builder().collectionName(RagE2ePolicy.COLLECTION).build());
            check(description.getCollectionID() == 469534467320352840L
                    && description.getConsistencyLevel() == ConsistencyLevel.BOUNDED, "TEST_COLLECTION_ID_OR_CONSISTENCY");
            check(Boolean.TRUE.equals(client.getLoadState(GetLoadStateReq.builder().collectionName(RagE2ePolicy.COLLECTION).build())), "TEST_COLLECTION_NOT_LOADED");
            var rows = RagE2eRunner.vectors(client, RagE2ePolicy.COLLECTION, ConsistencyLevel.BOUNDED);
            check(rows.size() == 52 && RagE2eRunner.vectorHash(rows).equals(RagE2eRunner.contract.path("milvusFrozen").path("fullVectorRowsSha256").asText()), "FROZEN_VECTOR_CHANGED");
            var chunks = RagE2eRunner.sourceRows.get("knowledge_chunk").stream().collect(Collectors.toMap(r -> r.get("vector_id"), r -> r));
            for (var row : rows) {
                var chunk = chunks.get(row.get("vector_id").toString());
                check(chunk != null && chunk.get("document_id").equals(row.get("document_id").toString())
                        && chunk.get("chunk_index").equals(row.get("chunk_index").toString())
                        && chunk.get("embedding_version").equals(row.get("embedding_version").toString()), "MYSQL_VECTOR_MAPPING");
            }
            FinalE2eAudit.collectionId = description.getCollectionID();
            vectors = Map.of("collection", RagE2ePolicy.COLLECTION, "collectionId", description.getCollectionID(),
                    "entities", 52, "consistency", "BOUNDED", "frozenSha256", RagE2eRunner.vectorHash(rows));
        } finally { client.close(); }
        Map<String,Integer> categories = new LinkedHashMap<>();
        for (String category : List.of("JAVA_BASIC","JAVA_COLLECTION","SPRING","MYSQL","NETWORK")) {
            int count = (int) RagE2eRunner.sourceRows.get("question").stream().filter(r -> category.equals(r.get("category"))
                    && "EASY".equals(r.get("difficulty")) && "ENABLED".equals(r.get("status"))).count();
            check(count > 0, "EASY_QUESTION_PLAN_INCOMPLETE"); categories.put(category, count);
        }
        captureHistory(c);
        c.rollback();
        return Map.of("status","PASS","mysql",identity,"corpus",corpus,"vectors",vectors,"easyPlanCandidateCounts",categories,
                "directDatabaseWrites",0,"vectorWrites",0,"modelCalls",0,"historicalRowsProtected",historicalRows.values().stream().mapToInt(Map::size).sum());
    }
    static Map<String,Object> verifyCorpus(Connection c) throws Exception {
        Map<String,Object> summary = new LinkedHashMap<>();
        for (String table : RagE2ePolicy.CORPUS) {
            var rows = RagE2eRunner.corpus(c, table);
            var expected = RagE2eRunner.contract.path("mysqlFrozen").path(table);
            check(rows.size() == expected.path("count").asInt() && RagE2ePolicy.rowHash(rows)
                    .equals(expected.path("rowSnapshotSha256").asText()), "FROZEN_CORPUS_CHANGED_" + table);
            RagE2eRunner.sourceRows.put(table, rows);
            summary.put(table, Map.of("rows", rows.size(), "sha256", RagE2ePolicy.rowHash(rows)));
        }
        RagE2ePolicy.corpusProfile(RagE2eRunner.sourceRows.get("knowledge_document"), RagE2eRunner.sourceRows.get("knowledge_chunk"));
        return summary;
    }
    static String historyQuery(String table) {
        return table.equals("user") ? "SELECT id,account,username,email,role,status,created_at,updated_at,password_changed_at FROM `user` ORDER BY id"
                : "SELECT * FROM `" + table + "` ORDER BY " + (table.equals("rag_retrieval_batch") ? "retrieval_batch_id" : "id");
    }
    static void captureHistory(Connection c) throws Exception {
        for (String table : RagE2ePolicy.TABLES) if (!RagE2ePolicy.CORPUS.contains(table)) {
            Map<String,String> hashes = new LinkedHashMap<>();
            for (var row : sql(c, historyQuery(table))) {
                String id = row.get(table.equals("rag_retrieval_batch") ? "retrieval_batch_id" : "id");
                hashes.put(id, RagE2ePolicy.rowHash(List.of(historyRow(table, row))));
            }
            historicalRows.put(table, hashes);
        }
    }
    static void verifyHistory(Connection c) throws Exception {
        c.rollback();
        for (var entry : historicalRows.entrySet()) {
            Map<String,String> after = new HashMap<>();
            for (var row : sql(c, historyQuery(entry.getKey()))) after.put(row.get(entry.getKey().equals("rag_retrieval_batch") ? "retrieval_batch_id" : "id"),
                    RagE2ePolicy.rowHash(List.of(historyRow(entry.getKey(), row))));
            assertHistory(entry.getKey(), entry.getValue(), after);
        }
        verifyCorpus(c);
    }
    static Map<String,String> historyRow(String table, Map<String,String> row) {
        var protectedRow = new LinkedHashMap<>(row);
        if (continuationSessionId > 0) {
            String resumed = Long.toString(continuationSessionId);
            if (table.equals("interview_session") && resumed.equals(row.get("id"))) {
                // Only workflow fields of the explicitly resumed session may advance.
                for (String field : List.of("status", "current_interview_question_id", "completed_question_count",
                        "total_score", "report_status", "version", "ended_at", "updated_at")) protectedRow.remove(field);
            } else if (table.equals("interview_question") && resumed.equals(row.get("session_id"))) {
                protectedRow.remove("status"); protectedRow.remove("updated_at");
            }
        }
        return protectedRow;
    }
    static void assertHistory(String table, Map<String,String> before, Map<String,String> after) {
        before.forEach((id, hash) -> {
            if (!hash.equals(after.get(id))) {
                report.put("historyMismatch", Map.of("table", table, "rowId", id));
                check(false, "HISTORICAL_BUSINESS_ROW_CHANGED");
            }
        });
    }
    static ConfigurableApplicationContext boot() {
        Map<String,Object> settings = new LinkedHashMap<>();
        settings.put("spring.profiles.active", "test-real");
        settings.put("spring.config.location", "classpath:/,file:tools/rag-e2e/config/");
        settings.put("spring.datasource.url", RagE2ePolicy.jdbc(RagE2ePolicy.TARGET));
        settings.put("spring.datasource.hikari.jdbc-url", RagE2ePolicy.jdbc(RagE2ePolicy.TARGET));
        settings.put("spring.datasource.username", env("FINAL_DB_USERNAME")); settings.put("spring.datasource.password", env("FINAL_DB_PASSWORD"));
        settings.put("spring.datasource.hikari.connection-init-sql", ""); settings.put("spring.datasource.hikari.maximum-pool-size", 3);
        settings.put("spring.sql.init.mode", "never"); settings.put("server.address", "127.0.0.1"); settings.put("server.port", 0);
        settings.put("embedding.base-url", gate.base("embedding")); settings.put("embedding.api-key", modelKey("EMBEDDING_API_KEY"));
        settings.put("embedding.model", RagE2ePolicy.MODEL); settings.put("embedding.dimension", 1024);
        settings.put("embedding.profile-version", RagE2ePolicy.VERSION); settings.put("embedding.batch-size", 1);
        settings.put("deepseek.enabled", true); settings.put("deepseek.base-url", gate.base("deepseek"));
        settings.put("deepseek.api-key", modelKey("DEEPSEEK_API_KEY")); settings.put("deepseek.model", "deepseek-flash");
        settings.put("deepseek.max-tokens",4096); settings.put("deepseek.read-timeout","150s");
        settings.put("ai.judge.provider",judgeProvider); settings.put("ai.judge.jev.base-url", gate.base("jev"));
        settings.put("ai.judge.jev.api-key",modelKey("JEV_API_KEY")); settings.put("ai.judge.jev.model","jev-latest");
        settings.put("ai.judge.jev.min-confidence",0.0); settings.put("ai.judge.jev.read-timeout","150s");
        settings.put("embedding.read-timeout","150s");
        settings.put("milvus.enabled",true); settings.put("milvus.uri","http://127.0.0.1:19530");
        settings.put("milvus.database-name","default"); settings.put("milvus.collection-name",RagE2ePolicy.COLLECTION);
        settings.put("milvus.dimension",1024); settings.put("milvus.token",""); settings.put("milvus.request-timeout","20s");
        settings.put("rag.retrieval.cosine-threshold-enabled",false);
        settings.put("jwt.secret",Base64.getEncoder().encodeToString(new java.security.SecureRandom().generateSeed(64)));
        settings.put("jwt.issuer","ai-interview-system"); settings.put("jwt.access-token-expiration","2h");
        settings.put("logging.level.root","OFF"); settings.put("spring.main.banner-mode","off");
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("final-e2e-fixed", settings));
        var application = new SpringApplication(AiInterviewSystemApplication.class, FinalE2eAudit.class);
        application.setEnvironment(environment); application.setAddCommandLineProperties(false);
        application.addInitializers(context -> {
            var resolved = context.getEnvironment();
            check(Set.copyOf(Arrays.asList(resolved.getActiveProfiles())).equals(Set.of("test-real")), "EXACT_PROFILES");
            RagE2ePolicy.auditJdbc(resolved.getProperty("spring.datasource.url"));
            check(judgeProvider.equals(resolved.getProperty("ai.judge.provider"))
                    && "false".equals(resolved.getProperty("rag.retrieval.cosine-threshold-enabled")), "PRODUCTION_PATH_MISMATCH");
        }); // The registered production EnvironmentIsolationInitializer still validates before infrastructure starts.
        var context = application.run();
        verifyHttpMapper(context.getBean(ObjectMapper.class));
        base = "http://127.0.0.1:" + ((ServletWebServerApplicationContext)context).getWebServer().getPort();
        return context;
    }
    static final class BusinessClient {
        static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    static String modelKey(String name) { return env(name).isBlank() ? "final-e2e-not-authorized" : env(name); }
    record HttpDateProbe(LocalDateTime startedAt) { }
    static void verifyHttpMapper(ObjectMapper mapper) {
        check(mapper.valueToTree(new HttpDateProbe(LocalDateTime.of(2026,10,13,19,34,11)))
                .path("startedAt").asText().equals("2026-10-13T19:34:11"),"HTTP_DATE_SERIALIZATION_CONTRACT");
    }
    static JsonNode httpProjection(ObjectMapper mapper,Object value) { return mapper.valueToTree(value); }
    static JsonNode http(String method, String path, Object body, int expected) throws Exception {
        report.put("currentOperation", method + " " + path);
        Map<String,Object> record = new LinkedHashMap<>();
        record.put("method",method); record.put("path",path); record.put("expectedStatus",expected); record.put("startedAt",Instant.now().toString());
        httpRecords.add(record); checkpoint();
        byte[] bytes = body == null ? new byte[0] : JSON.writeValueAsBytes(body);
        boolean auth = path.startsWith("/api/auth/");
        if (!auth && body != null) record.put("request",JSON.readTree(bytes));
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(450))
                .header("Content-Type","application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(bytes));
        if (!token.isBlank()) builder.header("Authorization", "Bearer " + token);
        try {
            var response = BusinessClient.HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            record.put("actualStatus",response.statusCode()); record.put("completedAt",Instant.now().toString());
            JsonNode parsed = JSON.readTree(response.body());
            if (auth) record.put("authBodyOmitted",true); else record.put("response",parsed);
            check(response.statusCode() == expected, "UNEXPECTED_HTTP_STATUS_" + response.statusCode());
            if (expected == 200) check(parsed.path("code").asInt() == 200, "BUSINESS_RESULT_FAILED");
            return parsed.path("data");
        } catch (Exception error) {
            record.put("exceptionClass",error.getClass().getName()); throw error;
        } finally { checkpoint(); }
    }
    static void checkpoint() throws Exception {
        report.put("http",httpRecords); report.put("evaluations",evaluations); report.put("exceptions",failures);
        if (gate != null) {
            report.put("providerCounts",gate.countSnapshot()); report.put("providerRecords",gate.records);
            report.put("costFromReportedUsage",usageCost());
        }
        save(output.resolve("report.json"),report);
    }
    static Map<String,Object> usageCost() {
        double cny=0,usd=0; int missing=0;
        for(var record:gate.records) {
            JsonNode usage=JSON.valueToTree(record.get("usage"));
            String provider=record.get("provider").toString();
            String inputKey=provider.equals("jev") ? "input_tokens" : "prompt_tokens";
            if(!usage.path(inputKey).isIntegralNumber()) { missing++; continue; }
            long input=usage.path(inputKey).asLong();
            if(provider.equals("embedding")) cny+=input*.5/1_000_000;
            else if(provider.equals("jev")) usd+=input*.042/1_000_000;
            else {
                if(!usage.path("completion_tokens").isIntegralNumber()) { missing++; continue; }
                usd+=(input*.3+usage.path("completion_tokens").asLong()*1.2)/1_000_000;
            }
        }
        return Map.of("embeddingCny",cny,"jevAndDeepSeekUsdAtPeakUncachedRates",usd,"requestsWithoutUsableUsage",missing,
                "planningCnyAt8PerUsd",cny+usd*8,"accountBillingVerified",false);
    }
    static Map<String,Object> snapshot(Connection c, String name) throws Exception {
        c.rollback();
        Map<String,Object> snapshot = new LinkedHashMap<>();
        snapshot.put("session",sql(c,"SELECT * FROM interview_session WHERE id=?",sessionId));
        snapshot.put("questions",sql(c,"SELECT * FROM interview_question WHERE session_id=? ORDER BY display_order,id",sessionId));
        snapshot.put("answers",sql(c,"SELECT a.* FROM interview_answer a JOIN interview_question q ON q.id=a.interview_question_id WHERE q.session_id=? ORDER BY a.id",sessionId));
        snapshot.put("evaluations",sql(c,"SELECT e.* FROM answer_evaluation e JOIN interview_question q ON q.id=e.main_interview_question_id WHERE q.session_id=? ORDER BY e.id",sessionId));
        snapshot.put("ragBatches",sql(c,"SELECT b.* FROM rag_retrieval_batch b JOIN interview_answer a ON a.id=b.answer_id JOIN interview_question q ON q.id=a.interview_question_id WHERE q.session_id=? ORDER BY b.created_at,b.retrieval_batch_id",sessionId));
        snapshot.put("ragHits",sql(c,"SELECT h.* FROM rag_hit_log h JOIN interview_answer a ON a.id=h.answer_id JOIN interview_question q ON q.id=a.interview_question_id WHERE q.session_id=? ORDER BY h.id",sessionId));
        snapshot.put("report",sql(c,"SELECT * FROM interview_report WHERE session_id=?",sessionId));
        snapshot.put("weaknesses",sql(c,"SELECT * FROM user_weakness WHERE user_id=? ORDER BY id",userId));
        save(output.resolve(name + ".database.json"),snapshot);
        return snapshot;
    }
    static void verifyEvaluation(Connection c, long questionId, String requestId, JsonNode response) throws Exception {
        c.rollback(); var observation = FinalE2eAudit.current;
        check(observation.embeddingCalls == 1 && observation.searches == 1 && observation.judgeCalls == 1
                && observation.dimension == 1024 && observation.judge != null && observation.prompt != null, "ACTUAL_PROVIDER_CHAIN_INCOMPLETE");
        var answer = sql(c,"SELECT * FROM interview_answer WHERE request_id=?",requestId);
        check(answer.size() == 1 && "EVALUATED".equals(answer.getFirst().get("status")), "ANSWER_TERMINAL_STATE");
        var saved = sql(c,"SELECT * FROM answer_evaluation WHERE answer_id=?",answer.getFirst().get("id"));
        check(saved.size() == 1,"ONE_EVALUATION_PER_ANSWER"); var evaluation = saved.getFirst();
        check(RagE2eConsistency.equivalent(JSON.readTree(evaluation.get("raw_result")),JSON.readTree(observation.judge.rawJson()))
                && evaluation.get("llm_model").equals(observation.judge.model()), "PROVIDER_RESULT_DB_MISMATCH");
        var validated = app.getBean(LlmEvaluationValidator.class).validate(observation.context,observation.judge.suggestion());
        var score = app.getBean(EvaluationScoreCalculator.class).calculate(validated);
        check(score.totalScore() == Integer.parseInt(evaluation.get("total_score")), "JAVA_SCORE_DB_MISMATCH");
        check(new EvaluationStandard().resolveLevel(score.totalScore()).name().equals(evaluation.get("level")),"EVALUATION_LEVEL_DB_MISMATCH");
        var scoreFields = List.of("correctness_score","completeness_score","depth_score","clarity_score","practice_score");
        var scores = List.of(score.correctnessScore(),score.completenessScore(),score.depthScore(),score.clarityScore(),score.practiceScore());
        for (int i=0;i<5;i++) check(scores.get(i).equals(Integer.valueOf(evaluation.get(scoreFields.get(i)))),"DIMENSION_DB_MISMATCH");
        boolean nextMain = Integer.parseInt(sql(c,"SELECT COUNT(*) AS n FROM interview_question WHERE session_id=? AND question_type='MAIN' AND plan_order>?",
                sessionId,sql(c,"SELECT plan_order FROM interview_question WHERE id=?",observation.context.mainInterviewQuestionId()).getFirst().get("plan_order")).getFirst().get("n")) > 0;
        var decision = app.getBean(FollowUpPolicy.class).decide(observation.context,validated,score,nextMain);
        check(evaluation.get("decision_action").equals(decision.decisionAction().name())
                && evaluation.get("evaluation_phase").equals(decision.evaluationPhase().name()), "JAVA_DECISION_DB_MISMATCH");
        check(RagE2eConsistency.equivalent(JSON.readTree(evaluation.get("scoring_point_results")),JSON.valueToTree(validated.scoringPointResults())), "SCORING_POINTS_DB_MISMATCH");
        var retrieval = observation.retrieval;
        check(retrieval.query().equals(new EvaluationRagQueryBuilder().build(observation.context)), "RETRIEVAL_QUERY_CONTRACT");
        check(observation.hydrationIds.equals(observation.raw.stream().map(h->h.vectorId()).distinct().toList()), "DEFAULT_RAG_FILTER_MISMATCH");
        check(!retrieval.evidence().isEmpty(), "REAL_HYDRATED_EVIDENCE_MISSING");
        var batches = sql(c,"SELECT * FROM rag_retrieval_batch WHERE answer_id=?",answer.getFirst().get("id"));
        check(batches.size()==1,"ONE_RETRIEVAL_BATCH"); var batch=batches.getFirst();
        check(evaluation.get("retrieval_batch_id").equals(batch.get("retrieval_batch_id"))
                && batch.get("query_text").equals(retrieval.query())
                && Integer.parseInt(batch.get("raw_hit_count"))==retrieval.rawHits().size()
                && Integer.parseInt(batch.get("evidence_hit_count"))==retrieval.evidence().size()
                && batch.get("top_k").equals("5")
                && batch.get("embedding_model").equals(RagE2ePolicy.MODEL)
                && batch.get("embedding_version").equals(RagE2ePolicy.VERSION), "RAG_BATCH_DB_MISMATCH");
        check(!JSON.readTree(batch.get("filter_snapshot")).path("cosineCandidate").path("enabled").asBoolean(), "EXPERIMENTAL_THRESHOLD_ENABLED");
        var hits=sql(c,"SELECT * FROM rag_hit_log WHERE retrieval_batch_id=? ORDER BY rank_no",batch.get("retrieval_batch_id"));
        check(hits.size()==retrieval.evidence().size(),"RAG_HIT_DB_COUNT");
        for(int i=0;i<hits.size();i++) {
            var evidence=retrieval.evidence().get(i); var hit=hits.get(i);
            check(hit.get("chunk_id").equals(evidence.chunkId().toString()) && Integer.parseInt(hit.get("rank_no"))==evidence.vectorRank()
                    && Math.abs(Double.parseDouble(hit.get("similarity_score"))-evidence.similarityScore()) < .000001,"RAG_CITATION_DB_MISMATCH");
            check(observation.hydrated.stream().anyMatch(r->r.getChunkId().equals(evidence.chunkId()) && r.getContent().equals(evidence.content()))
                    && observation.prompt.userPrompt().contains("  content="+evidence.content()+"\n\n"), "MYSQL_HYDRATION_PROMPT_MISMATCH");
        }
        var detail=http("GET","/api/interviews/"+sessionId,null,200);
        JsonNode question=null;
        for(var candidate:detail.path("questions")) if(candidate.path("interviewQuestionId").asLong()==questionId) question=candidate;
        check(question!=null && RagE2eConsistency.equivalent(question.path("evaluation"),response.path("evaluation")),"POST_DETAIL_EVALUATION_MISMATCH");
        var detailFromJava=httpProjection(app.getBean(ObjectMapper.class),app.getBean(InterviewService.class).getDetail(userId,sessionId));
        check(RagE2eConsistency.equivalent(detail,detailFromJava),"HTTP_DETAIL_DB_PROJECTION_MISMATCH");
        verifyWire(observation);
        Map<String,Object> item=new LinkedHashMap<>();
        item.put("questionId",questionId); item.put("answerId",answer.getFirst().get("id")); item.put("evaluation",evaluation);
        item.put("rawMilvusHits",retrieval.rawHits()); item.put("hydrationIds",observation.hydrationIds); item.put("hydratedRows",observation.hydrated);
        item.put("query",retrieval.query()); item.put("prompt",observation.prompt); item.put("evidence",retrieval.evidence());
        item.put("provider",observation.judge.model()); item.put("wireAndPersistenceVerified",true); evaluations.add(item);
        item.put("providerOutcome",observation.judge.model().startsWith("jev") ? "JEV_SCORE_AND_DEEPSEEK_FEEDBACK"
                : judgeProvider.equals("jev") ? "PRODUCTION_DEEPSEEK_FALLBACK" : "DEEPSEEK_JUDGE");
        save(output.resolve("answer-"+evaluations.size()+".evidence.json"),item);
    }
    static void verifyWire(FinalE2eAudit.Observation observation) throws Exception {
        List<Map<String,Object>> records=gate.records.stream().filter(r->gate.operation.equals(r.get("operation"))).toList();
        check(records.stream().anyMatch(r->"embedding".equals(r.get("provider"))),"QUERY_EMBEDDING_WIRE_MISSING");
        for(var record:records) {
            var request=JSON.readTree(Files.readAllBytes(output.resolve("providers").resolve(record.get("requestFile").toString())));
            if(record.get("provider").equals("embedding")) check(request.path("input").get(0).asText().equals(observation.retrieval.query()),"QUERY_WIRE_MISMATCH");
            if(record.get("provider").equals("jev")) check(request.path("state").asText().equals(observation.prompt.userPrompt()),"JEV_STATE_PROMPT_MISMATCH");
            if(record.get("provider").equals("deepseek")) check(request.path("messages").path(1).path("content").asText().startsWith(observation.prompt.userPrompt()),"DEEPSEEK_PROMPT_WIRE_MISMATCH");
        }
        if(observation.judge.model().startsWith("jev")) {
            var raw=JSON.readTree(observation.judge.rawJson());
            check(records.stream().anyMatch(r->"jev".equals(r.get("provider")) && Integer.valueOf(200).equals(r.get("httpStatus"))),"JEV_HTTP_SUCCESS_MISSING");
            for(var record:records) if(record.get("provider").equals("jev")) {
                var body=JSON.readTree(Files.readAllBytes(output.resolve("providers").resolve(record.get("responseFile").toString())));
                // Production deliberately persists the typed JevResponse projection. Full envelope remains in provider files.
                var projection=JSON.valueToTree(JSON.treeToValue(body,com.kun.aiinterview.interview.evaluation.judge.jev.JevResponse.class));
                check(RagE2eConsistency.equivalent(raw.path("judge"),projection),"JEV_TYPED_RESPONSE_RAW_RESULT_MISMATCH");
            }
            String generation=raw.path("generation").path("rawJson").asText();
            check(!generation.isBlank() && matchesDeepSeekContent(records,generation),"FEEDBACK_RESPONSE_RAW_RESULT_MISMATCH");
        } else check(matchesDeepSeekContent(records,observation.judge.rawJson()),"FALLBACK_RESPONSE_RAW_RESULT_MISMATCH");
    }
    static boolean matchesDeepSeekContent(List<Map<String,Object>> records,String raw) throws Exception {
        for(var record:records) if(record.get("provider").equals("deepseek") && Integer.valueOf(200).equals(record.get("httpStatus"))) {
            var body=JSON.readTree(Files.readAllBytes(output.resolve("providers").resolve(record.get("responseFile").toString())));
            if(body.path("choices").path(0).path("message").path("content").asText().equals(raw)) return true;
        }
        return false;
    }
    static void execute(Connection c) throws Exception {
        gate=new FinalE2eGate(output.resolve("providers"),(uri,auth,body)->RagE2eModelGate.realTransport().post(uri,auth,body));
        gate.start(); app=boot();
        http("GET","/api/interviews/current",null,401);
        String account="final_"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        String password=RagE2eRunner.randomCandidatePassword();
        http("POST","/api/auth/register",Map.of("account",account,"username","Final E2E synthetic candidate","email",account+"@example.com","password",password),200);
        var login=http("POST","/api/auth/login",Map.of("account",account,"password",password),200);
        token=login.path("accessToken").asText(); check(!token.isBlank() && login.path("role").asText().equals("USER"),"LOGIN_JWT_ROLE");
        var me=http("GET","/api/users/me",null,200); userId=me.path("userId").asLong();
        check(me.path("account").asText().equals(account) && userId==login.path("userId").asLong(),"LOGIN_CURRENT_USER_MISMATCH");
        c.rollback(); var user=sql(c,"SELECT id,role,status FROM `user` WHERE account=?",account).getFirst();
        check(userId>0 && Long.toString(userId).equals(user.get("id")) && "USER".equals(user.get("role")) && "ENABLED".equals(user.get("status")),"JWT_DATABASE_USER");
        http("GET","/api/admin/questions?page=1&pageSize=10",null,403);
        http("GET","/api/interviews/history?page=0",null,400);
        var created=http("POST","/api/interviews",Map.of("difficulty","EASY"),200); sessionId=created.path("sessionId").asLong();
        check(sessionId>0 && created.path("plannedQuestionCount").asInt()==5 && created.path("completedQuestionCount").asInt()==0,"FIVE_MAIN_QUESTION_PLAN");
        report.put("userId",userId); report.put("sessionId",sessionId);
        var initial=snapshot(c,"00-created");
        List<Map<String,String>> mainRows=sql(c,"SELECT * FROM interview_question WHERE session_id=? AND question_type='MAIN' ORDER BY plan_order",sessionId);
        check(mainRows.size()==5,"PERSISTED_MAIN_PLAN_COUNT");
        for(int i=0;i<5;i++) {
            var row=mainRows.get(i); final String bankId=row.get("question_id");
            var bank=RagE2eRunner.sourceRows.get("question").stream().filter(r->r.get("id").equals(bankId)).findFirst().orElseThrow();
            check(Integer.parseInt(row.get("plan_order"))==i+1 && row.get("question_content").equals(bank.get("question_content"))
                    && row.get("reference_answer_snapshot").equals(bank.get("reference_answer")),"FIXED_BANK_SNAPSHOT");
        }
        var resumed=http("POST","/api/interviews",Map.of("difficulty","EASY"),200);
        check(resumed.path("sessionId").asLong()==sessionId,"CREATE_RESUME_IDEMPOTENCY");
        http("POST","/api/interviews/"+sessionId+"/report",null,409);
        String ownerToken=token; token="";
        String stranger="foreign_"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        http("POST","/api/auth/register",Map.of("account",stranger,"username","Ownership probe","email",stranger+"@example.com","password",password),200);
        token=http("POST","/api/auth/login",Map.of("account",stranger,"password",password),200).path("accessToken").asText();
        http("GET","/api/interviews/"+sessionId,null,404); http("POST","/api/interviews/"+sessionId+"/report",null,404);
        token=ownerToken;
        completeInterview(c,mainRows,created.path("currentQuestion"),0,0,1);
    }
    static void completeInterview(Connection c,List<Map<String,String>> mainRows,JsonNode current,int mainNumber,int followUps,int firstOrdinal) throws Exception {
        boolean finished=false;
        for(int n=firstOrdinal;n<=10;n++) {
            long id=current.path("interviewQuestionId").asLong(); check(id>0,"CURRENT_QUESTION_MISSING");
            c.rollback(); var row=sql(c,"SELECT * FROM interview_question WHERE id=? AND session_id=?",id,sessionId).getFirst();
            boolean main="MAIN".equals(row.get("question_type"));
            String answer;
            if(main) {
                mainNumber++;
                // Deliberately incomplete genuine candidate input for question one; no changes to frozen bank/expected scores.
                answer=(mainNumber==1 || mainNumber==5) ? "这个知识点我目前只知道基本概念，具体原理、边界情况和代码实践还不熟悉。" : row.get("reference_answer_snapshot");
            } else {
                followUps++;
                answer=mainNumber==5 ? "这个追问涉及的具体机制我仍然不清楚，目前无法给出可靠的解释和实践例子。"
                        : sql(c,"SELECT reference_answer_snapshot FROM interview_question WHERE id=?",row.get("parent_question_id")).getFirst().get("reference_answer_snapshot");
            }
            check(mainNumber<=5 && followUps<=5,"QUESTION_COUNT_BOUNDARY");
            var request=Map.of("interviewQuestionId",id,"answerContent",answer,"requestId",UUID.randomUUID().toString());
            gate.begin("answer-"+n); FinalE2eAudit.current=new FinalE2eAudit.Observation();
            var response=http("POST","/api/interviews/"+sessionId+"/answers",request,200);
            verifyEvaluation(c,id,request.get("requestId").toString(),response);
            snapshot(c,String.format("%02d-after-answer",n));
            int calls=gate.total();
            var replay=http("POST","/api/interviews/"+sessionId+"/answers",request,200);
            check(RagE2eConsistency.equivalent(replay.path("evaluation"),response.path("evaluation")) && gate.total()==calls,"ANSWER_REPLAY_NO_PROVIDER_CALL");
            http("POST","/api/interviews/"+sessionId+"/answers",Map.of("interviewQuestionId",id,"answerContent",answer+" changed","requestId",request.get("requestId")),409);
            http("POST","/api/interviews/"+sessionId+"/answers",Map.of("interviewQuestionId",id,"answerContent",answer,"requestId",UUID.randomUUID().toString()),409);
            check(gate.total()==calls,"REJECTED_DUPLICATE_TRIGGERED_PROVIDER");
            c.rollback(); var session=sql(c,"SELECT * FROM interview_session WHERE id=?",sessionId).getFirst();
            String action=response.path("nextAction").asText();
            if(action.equals("FINISH")) {
                check(mainNumber==5 && "COMPLETED".equals(session.get("status")) && "5".equals(session.get("completed_question_count"))
                        && session.get("current_interview_question_id")==null && response.path("nextQuestion").isNull(),"COMPLETE_PLAN_END_STATE");
                finished=true; break;
            }
            check(Set.of("FOLLOW_UP","NEXT_MAIN").contains(action) && "IN_PROGRESS".equals(session.get("status")),"INTERMEDIATE_WORKFLOW_STATE");
            current=response.path("nextQuestion");
            check(Long.toString(current.path("interviewQuestionId").asLong()).equals(session.get("current_interview_question_id")),"CURRENT_QUESTION_STATE");
            check(Integer.parseInt(session.get("completed_question_count"))==(action.equals("FOLLOW_UP")?mainNumber-1:mainNumber),"COMPLETED_COUNT_STATE");
        }
        check(finished,"ENTIRE_PLANNED_INTERVIEW_NOT_FINISHED");
        report.put("answeredMainQuestions",mainNumber); report.put("answeredFollowUps",followUps);
        check(http("GET","/api/interviews/current",null,200).isNull(),"COMPLETED_SESSION_STILL_CURRENT");
        gate.begin("report"); var generated=http("POST","/api/interviews/"+sessionId+"/report",null,200);
        var read=http("GET","/api/interviews/"+sessionId+"/report",null,200);
        check(RagE2eConsistency.equivalent(generated,read),"REPORT_POST_GET_MISMATCH");
        int calls=gate.total(); var before=snapshot(c,"20-report-ready");
        var replay=http("POST","/api/interviews/"+sessionId+"/report",null,200);
        check(RagE2eConsistency.equivalent(read,replay) && calls==gate.total(),"REPORT_IDEMPOTENCY_PROVIDER_COUNT");
        var after=snapshot(c,"21-report-replayed");
        check(JSON.valueToTree(before).equals(JSON.valueToTree(after)),"REPORT_REPLAY_MUTATED_PERSISTENCE");
        verifyFinal(c,generated,mainRows);
        var history=http("GET","/api/interviews/history?page=1&pageSize=10",null,200);
        check(history.path("total").asInt()==1 && history.path("items").get(0).path("sessionId").asLong()==sessionId,"HISTORY_PERSISTENCE");
        check(failures.stream().noneMatch(f->f.containsKey("gateException")),"GATE_FAILED_DURING_EXECUTION");
        report.put("FINAL_E2E_STATUS","REAL_E2E_PASS");
    }
    static void verifyFinal(Connection c,JsonNode generated,List<Map<String,String>> originalMains) throws Exception {
        c.rollback();
        var session=sql(c,"SELECT * FROM interview_session WHERE id=?",sessionId).getFirst();
        check("COMPLETED".equals(session.get("status")) && "READY".equals(session.get("report_status")),"FINAL_SESSION_STATE");
        var mains=sql(c,"SELECT * FROM interview_question WHERE session_id=? AND question_type='MAIN' ORDER BY plan_order",sessionId);
        check(mains.size()==5,"FINAL_MAIN_COUNT");
        for(int i=0;i<5;i++) {
            var row=mains.get(i); var original=originalMains.get(i);
            check("ANSWERED".equals(row.get("status")),"MAIN_NOT_ANSWERED");
            for(String key:List.of("id","question_id","plan_order","question_content","reference_answer_snapshot","scoring_points_snapshot"))
                check(Objects.equals(row.get(key),original.get(key)),"MAIN_PLAN_RESELECTED_OR_MUTATED");
        }
        var effective=app.getBean(AnswerEvaluationMapper.class).listFinalEffectiveEvaluationsBySessionId(sessionId);
        check(effective.size()==5 && effective.stream().allMatch(e->e.getEvaluationPhase().name().equals("FINAL")),"FIVE_FINAL_EFFECTIVE_EVALUATIONS");
        check("5".equals(sql(c,"SELECT COUNT(*) AS n FROM answer_evaluation e JOIN interview_question q ON q.id=e.main_interview_question_id WHERE q.session_id=? AND e.evaluation_phase='FINAL'",sessionId).getFirst().get("n")),"EXACTLY_FIVE_FINAL_EVALUATIONS");
        check(sql(c,"SELECT a.id FROM interview_answer a JOIN interview_question q ON q.id=a.interview_question_id WHERE q.session_id=? AND a.status<>'EVALUATED'",sessionId).isEmpty(),"NONTERMINAL_ANSWER_REMAINS");
        BigDecimal average=BigDecimal.valueOf(effective.stream().mapToInt(e->e.getTotalScore()).sum()).divide(BigDecimal.valueOf(5),2,RoundingMode.HALF_UP);
        var reports=sql(c,"SELECT * FROM interview_report WHERE session_id=?",sessionId); check(reports.size()==1,"ONE_FINAL_REPORT");
        var saved=reports.getFirst();
        check(new BigDecimal(session.get("total_score")).compareTo(average)==0
                && new BigDecimal(saved.get("overall_score")).compareTo(average)==0
                && generated.path("overallScore").decimalValue().compareTo(average)==0,"FINAL_SCORE_AGGREGATION");
        check(saved.get("result").equals(new EvaluationStandard().resolveLevel(average).name()),"REPORT_JAVA_LEVEL");
        check(saved.get("summary").equals(generated.path("summary").asText()),"REPORT_SUMMARY_DB_HTTP");
        for(String key:List.of("strengths","weaknesses","suggestions")) check(RagE2eConsistency.equivalent(JSON.readTree(saved.get(key)),generated.path(key)),"REPORT_FEEDBACK_DB_HTTP");
        var reportRecords=gate.records.stream().filter(r->"report".equals(r.get("operation"))).toList();
        check(reportRecords.size()==1 && Integer.valueOf(200).equals(reportRecords.getFirst().get("httpStatus")),"ONE_REAL_REPORT_MODEL_SUCCESS");
        var body=JSON.readTree(Files.readAllBytes(output.resolve("providers").resolve(reportRecords.getFirst().get("responseFile").toString())));
        var raw=JSON.readTree(body.path("choices").path(0).path("message").path("content").asText());
        check(raw.path("summary").asText().strip().equals(saved.get("summary")),"MODEL_REPORT_DB_SUMMARY");
        for(String key:List.of("strengths","weaknesses","suggestions")) {
            List<String> normalized=new ArrayList<>(); raw.path(key).forEach(v->normalized.add(v.asText().strip()));
            check(RagE2eConsistency.equivalent(JSON.valueToTree(normalized),JSON.readTree(saved.get(key))),"MODEL_REPORT_DB_LISTS");
        }
        var weak=sql(c,"SELECT * FROM user_weakness WHERE user_id=?",userId);
        Map<String,List<Integer>> groups=new LinkedHashMap<>();
        for(var e:effective) groups.computeIfAbsent(e.getCategory().name()+"|"+e.getKnowledgePoint(), k->new ArrayList<>()).add(e.getTotalScore());
        int expectedCount=0;
        for(var entry:groups.entrySet()) {
            BigDecimal score=BigDecimal.valueOf(entry.getValue().stream().mapToInt(Integer::intValue).sum()).divide(BigDecimal.valueOf(entry.getValue().size()),2,RoundingMode.HALF_UP);
            var matches=weak.stream().filter(r->(r.get("category")+"|"+r.get("knowledge_point")).equals(entry.getKey())).toList();
            if(score.compareTo(BigDecimal.valueOf(80))<0) {
                expectedCount++; check(matches.size()==1,"EXPECTED_WEAKNESS_MISSING"); var row=matches.getFirst();
                check("ACTIVE".equals(row.get("status")) && "1".equals(row.get("discovered_count"))
                        && new BigDecimal(row.get("weakness_score")).compareTo(BigDecimal.valueOf(100).subtract(score))==0,"WEAKNESS_FINAL_SCORE_AGGREGATION");
            } else check(matches.isEmpty(),"UNEXPECTED_WEAKNESS_FOR_STRONG_SCORE");
        }
        check(weak.size()==expectedCount,"WEAKNESS_COUNT");
        check(sql(c,"SELECT parent_question_id,COUNT(*) AS n FROM interview_question WHERE session_id=? AND question_type='FOLLOW_UP' GROUP BY parent_question_id HAVING COUNT(*)>1",sessionId).isEmpty(),"MULTIPLE_FOLLOW_UPS_PER_MAIN");
        report.put("finalVerification",Map.of("finalEffectiveEvaluations",effective.size(),"overallScore",average,"weaknessRows",weak.size(),
                "reportId",saved.get("id"),"reportAndWeaknessIdempotency",true,"mainSnapshotsPreserved",true));
        report.put("coverageLimits",Map.of("realFollowUpObserved",report.get("answeredFollowUps"),"realWeaknessRows",weak.size(),
                "difficulty","EASY only","subjectiveScoringAccuracyMeasured",false,"frontendUiJourneyVerified",false));
    }
    public static void main(String[] args) throws Exception {
        check(args.length>0,"MODE_REQUIRED");
        boolean execute=args[0].equals("execute");
        check(execute || args[0].equals("prepare"),"MODE_REJECTED");
        if(execute) {
            check(args.length==2 && AUTHORIZATION.equals(args[1]),"SINGLE_USE_HUMAN_FEE_AUTHORIZATION_REQUIRED");
            output=Path.of("target/final-e2e/attempts",RUN_ID);
            Files.createDirectories(output.getParent()); Files.createDirectory(output);
            Files.writeString(output.resolve("ATTEMPT_RESERVED.lock"),Instant.now().toString(),StandardOpenOption.CREATE_NEW);
        } else output=Path.of("target/final-e2e/preparation");
        report.put("FINAL_E2E_STATUS","BLOCKED"); report.put("runId",RUN_ID); report.put("budget",budget());
        report.put("feeAuthorizationConsumed",execute);
        report.put("configuration",Map.of("profile","test-real","database",RagE2ePolicy.TARGET,"collection",RagE2ePolicy.COLLECTION,
                "provider","jev","deepseekModel","deepseek-flash","rag","production dense TopK=5, no cosine threshold","p42Enabled",false));
        try(Connection c=connection()) {
            report.put("preflight",preflight(c));
            if(execute) {
                for(String name:List.of("EMBEDDING_API_KEY","DEEPSEEK_API_KEY","JEV_API_KEY")) check(!env(name).isBlank(),"PROVIDER_CREDENTIAL_MISSING");
                Files.writeString(output.resolve("EXECUTION_CONSUMED.lock"),Instant.now().toString(),StandardOpenOption.CREATE_NEW);
                try { execute(c); }
                finally {
                    if(app!=null) app.close(); if(gate!=null) gate.close();
                    if(sessionId>0) snapshot(c,"99-final-or-failure");
                    verifyHistory(c); report.put("frozenCorpusAndHistoricalBusinessRowsUnchanged",true);
                }
            } else {
                gate=new FinalE2eGate(output.resolve("forbidden-provider-traffic"),(uri,auth,body)-> { throw new SecurityException("PREPARE_MODEL_CALL_FORBIDDEN"); });
                try {
                    gate.start(); app=boot();
                    check(gate.total()==0,"STARTUP_ATTEMPTED_MODEL_CALL");
                    report.put("productionStartup",Map.of("status","PASS","providerClass",app.getBean(com.kun.aiinterview.interview.evaluation.judge.JudgeProvider.class).getClass().getName(),
                            "workflowPresent",app.containsBean("interviewWorkflowService"),"reportGeneratorPresent",app.containsBean("deepSeekInterviewReportGenerator"),
                            "modelCalls",0,"collectionWrites",0));
                } finally { if(app!=null) app.close(); gate.close(); }
                report.put("remainingBlocker","One explicit fee authorization; no business writes or model calls made.");
            }
        } catch(Exception error) {
            report.put("FINAL_E2E_STATUS","BLOCKED"); report.put("failure",FinalE2eResume.failure(error));
            checkpoint(); System.out.println("FINAL_E2E_STATUS=BLOCKED; inspect " + output.resolve("report.json"));
            throw new IllegalStateException("Final E2E stopped; original evidence and locks retained. Exception: " + error.getClass().getSimpleName());
        }
        checkpoint(); System.out.println("FINAL_E2E_STATUS="+report.get("FINAL_E2E_STATUS")+"; evidence="+output.resolve("report.json"));
    }
}
