import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.kun.aiinterview.interview.entity.*;
import com.kun.aiinterview.interview.enums.*;
import com.kun.aiinterview.interview.evaluation.*;
import com.kun.aiinterview.interview.evaluation.decision.*;
import com.kun.aiinterview.interview.evaluation.llm.*;
import com.kun.aiinterview.interview.evaluation.prompt.*;
import com.kun.aiinterview.interview.evaluation.score.*;
import com.kun.aiinterview.interview.evaluation.standard.*;
import com.kun.aiinterview.interview.evaluation.validation.*;
import com.kun.aiinterview.interview.mapper.*;
import com.kun.aiinterview.interview.service.*;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import com.kun.aiinterview.knowledge.vector.VectorSearchHit;
import java.math.*;
import java.nio.file.*;
import java.sql.Connection;
import java.util.*;

/** Preserved wire replay + SELECT-only reconciliation. No Spring boot, login, writer or model transport. */
public final class FinalE2eSecondRepair {
    static final Path SECOND = Path.of("target/final-e2e/attempts/final-e2e-20261013-002");
    static final ObjectMapper JSON = RagE2eReadOnlyVerifier.JSON;
    static final Map<String, String> TABLES = new LinkedHashMap<>(Map.of(
            "session", "interview_session", "questions", "interview_question", "answers", "interview_answer",
            "evaluations", "answer_evaluation", "ragBatches", "rag_retrieval_batch", "ragHits", "rag_hit_log",
            "report", "interview_report", "weaknesses", "user_weakness"));
    static void check(boolean ok, String code) {
        FinalE2eRunner.check(ok, code);
    }

    static JsonNode read(String file) throws Exception {
        return JSON.readTree(SECOND.resolve(file).toFile());
    }

    static List<Map<String, String>> rows(JsonNode snapshot, String key) {
        return JSON.convertValue(snapshot.path(key), new TypeReference<>() {});
    }

    static Map<String, String> hashes(String table, List<Map<String, String>> rows) {
        var hashes = new LinkedHashMap<String, String>();
        for (var row : rows)
            hashes.put(row.get(table.equals("rag_retrieval_batch") ? "retrieval_batch_id" : "id"),
                RagE2ePolicy.rowHash(List.of(FinalE2eRunner.historyRow(table, row))));
        return hashes;
    }

    static void verifySnapshotHistory(JsonNode before, JsonNode after) {
        // Match the real runner's TreeMap table order and SELECT ... ORDER BY id order.
        TABLES.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry -> {
            var b = new ArrayList<>(rows(before, entry.getKey()));
            if (!entry.getValue().equals("rag_retrieval_batch"))
                b.sort(Comparator.comparingLong(row -> Long.parseLong(row.get("id"))));
            FinalE2eRunner.assertHistory(entry.getValue(), hashes(entry.getValue(), b), hashes(entry.getValue(), rows(after, entry.getKey())));
        });
    }

    static Map<String, Object> verifyHistoryContract() throws Exception {
        var before = read("00-resumed-existing-session.database.json");
        var after = read("99-final-or-failure.database.json");
        long prior = FinalE2eRunner.continuationSessionId;
        Object mismatch = FinalE2eRunner.report.get("historyMismatch");
        try {
            FinalE2eRunner.continuationSessionId = 0;
            try {
                verifySnapshotHistory(before, after);
                throw new AssertionError("Original guard did not reproduce");
            } catch (SecurityException expected) {
                check(expected.getMessage().equals("HISTORICAL_BUSINESS_ROW_CHANGED"), "SECOND_FAILURE_REPLAY");
            }
            Object firstMismatch = FinalE2eRunner.report.get("historyMismatch");
            check(Map.of("table", "interview_question", "rowId", "174").equals(firstMismatch), "SECOND_FIRST_CHANGED_HISTORY_ROW");
            FinalE2eRunner.continuationSessionId = 150;
            verifySnapshotHistory(before, after);
            return Map.of("strictWholeRowGuardReproduced", true, "scopedContinuationGuard", "PASS",
                    "allowedSessionId", 150, "existingAnswersEvaluationsRagStillImmutable", true, "originalFirstMismatch", firstMismatch);
        } finally {
            FinalE2eRunner.continuationSessionId = prior;
            if (mismatch == null)
                FinalE2eRunner.report.remove("historyMismatch");
            else
                FinalE2eRunner.report.put("historyMismatch", mismatch);
        }
    }

    static Map<String, Object> replay() throws Exception {
        var report = read("report.json");
        var before = read("00-resumed-existing-session.database.json");
        var after = read("99-final-or-failure.database.json");
        var failure = report.path("cleanupVerificationFailure");
        check(failure.path("guardCode").asText().equals("HISTORICAL_BUSINESS_ROW_CHANGED")
                && failure.path("causeChain").get(0).path("stackTrace").toString().contains("FinalE2eRunner.java:161")
                && failure.path("causeChain").get(0).path("stackTrace").toString().contains("FinalE2eResume.java:85"), "SECOND_ORIGINAL_THROW_SITE");
        check(!report.has("failure") && report.path("FINAL_E2E_STATUS").asText().equals("BLOCKED"), "SECOND_FAILURE_CLASSIFICATION");
        var contract = verifyHistoryContract();
        // Read and hash every retained file, including all JSON, both locks and the wrapper-only console.
        var artifacts = new TreeMap<String, String>();
        try (var files = Files.walk(SECOND)) {
            for (var file : files.filter(Files::isRegularFile).toList()) {
                byte[] bytes = Files.readAllBytes(file);
                artifacts.put(SECOND.relativize(file).toString().replace('\\', '/'), RagE2ePolicy.hash(bytes));
                if (file.toString().endsWith(".json"))
                    JSON.readTree(bytes);
            }
        }
        check(artifacts.containsKey("ATTEMPT_RESERVED.lock") && artifacts.containsKey("EXECUTION_CONSUMED.lock"), "SECOND_LOCKS_MISSING");
        var index = read("providers/provider-index.json");
        int embedding = 0, deepseek = 0;
        for (var item : index) {
            for (String type : List.of("request", "response"))
                check(RagE2ePolicy.hash(Files.readAllBytes(SECOND.resolve("providers")
                    .resolve(item.path(type+"File").asText()))).equals(item.path(type+"Sha256").asText()), "SECOND_PROVIDER_SHA_MISMATCH");
            check(item.path("httpStatus").asInt() == 200, "SECOND_PROVIDER_NOT_SUCCESSFUL");
            switch (item.path("provider").asText()) {
                case "embedding" -> embedding++;
                case "deepseek" -> deepseek++;
                default -> throw new SecurityException("SECOND_UNEXPECTED_PROVIDER");
            }
        }
        check(embedding == 6 && deepseek == 7 && index.size() == 13, "SECOND_PROVIDER_COUNTS");
        var login = report.path("http").get(0);
        check(login.path("path").asText().equals("/api/auth/login") && login.path("actualStatus").asInt() == 200
                && login.path("authBodyOmitted").asBoolean() && report.path("authorizedCredentialChange").path("passwordRotated").asBoolean(), "SECOND_CREDENTIAL_LOGIN_EVIDENCE");
        check(RagE2eConsistency.equivalent(after, read("20-report-ready.database.json"))
                && RagE2eConsistency.equivalent(after, read("21-report-replayed.database.json")), "SECOND_REPORT_REPLAY_CHANGED_DB");
        verifyCompleted(after, report);
        var deltas = new LinkedHashMap<String, Integer>();
        TABLES.forEach((key, table) -> deltas.put(table, after.path(key).size() - before.path(key).size()));
        var changed = new ArrayList<Map<String, Object>>();
        for (String key : List.of("session", "questions"))
            for (var row : before.path(key)) {
                JsonNode match = find(after.path(key), "id", row.path("id").asText());
                var fields = FinalE2eRepair.differences(row, match, "");
                if (!fields.isEmpty())
                    changed.add(Map.of("table", TABLES.get(key), "id", row.path("id").asText(), "changedFields", fields));
            }
        var result = new LinkedHashMap<String, Object>();
        result.put("SECOND_FAILURE_ROOT_CAUSE", "RUNNER_CONTINUATION_HISTORY_SCOPE_FALSE_POSITIVE");
        result.put("classification", "EXECUTOR_CLEANUP_GUARD; business completed before failure");
        result.put("firstThrowSite", "FinalE2eRunner.java:161 lambda$verifyHistory$4 -> check:43 -> RagE2ePolicy.require:38 (attempt 002 source)");
        result.put("caller", "FinalE2eResume.java:85; line 92 is final wrapper");
        result.put("historyContract", contract);
        result.put("artifactSha256", artifacts);
        result.put("ATTEMPT_002_MODEL_CALLS", Map.of("embedding", embedding, "deepseekAnswerEvaluation", 6, "deepseekReport", 1, "jev", 0,
                "totalForwarded", 13, "http200", 13, "accountBillingVerified", false, "savedUsageCostEstimate", report.path("costFromReportedUsage")));
        result.put("ATTEMPT_002_DB_CHANGES", Map.of("passwordRotatedUserId", 427, "normalLoginSucceeded", true, "newRows", deltas,
                "newRowTotal", deltas.values().stream().mapToInt(Integer::intValue).sum(), "preexistingRowsChanged", changed,
                "newAnswers", 6, "newEvaluations", 6, "existingAnswer100Reevaluated", false, "reportId", 14, "sqlWriteStatementCountRecorded", false));
        result.put("SESSION_150_CURRENT_PROGRESS", progress(after));
        result.put("historicalAuditLimit", "Full all-table baseline hashes were in attempt 002 process memory only. Retained session snapshots prove original business rows; unrelated historical rows cannot be retroactively certified.");
        result.put("credentialReuse", "Attempt 002 random password/JWT were only in process memory; no persisted candidate credential. No further login or rotation is needed.");
        result.put("originalAttemptStatus", "BLOCKED_UNCHANGED");
        result.put("newModelCalls", 0);
        result.put("databaseWrites", 0);
        return result;
    }

    static JsonNode find(JsonNode rows, String key, String id) {
        for (var row : rows)
            if (row.path(key).asText().equals(id))
            return row;
        throw new SecurityException("PRESERVED_ROW_MISSING_"+key.toUpperCase());
    }

    static JsonNode lastHttp(JsonNode report, String method, String path) {
        JsonNode found = null;
        for (var item : report.path("http"))
            if (item.path("method").asText().equals(method) && item.path("path").asText().equals(path)
                && item.path("actualStatus").asInt() == 200)
            found = item.path("response").path("data");
        check(found != null, "SECOND_HTTP_RESPONSE_MISSING");
        return found;
    }

    static void verifyCompleted(JsonNode snapshot, JsonNode report) throws Exception {
        var session = snapshot.path("session").get(0);
        check(session.path("id").asLong() == 150 && session.path("user_id").asLong() == 427 && session.path("status").asText().equals("COMPLETED")
                && session.path("report_status").asText().equals("READY") && session.path("completed_question_count").asInt() == 5
                && session.path("current_interview_question_id").isNull(), "SECOND_COMPLETED_SESSION");
        check(snapshot.path("questions").size() == 7 && snapshot.path("answers").size() == 7 && snapshot.path("evaluations").size() == 7
                && snapshot.path("ragBatches").size() == 7 && snapshot.path("ragHits").size() == 35 && snapshot.path("report").size() == 1, "SECOND_FINAL_ROW_COUNTS");
        var questions = new HashMap<Long, InterviewQuestion>();
        var answers = new HashMap<Long, InterviewAnswer>();
        snapshot.path("questions").forEach(row -> {
            var q = RagE2eReadOnlyVerifier.entity(row, InterviewQuestion.class);
            questions.put(q.getId(), q);
        });
        snapshot.path("answers").forEach(row -> {
            var a = RagE2eReadOnlyVerifier.entity(row, InterviewAnswer.class);
            answers.put(a.getId(), a);
        });
        check(questions.values().stream().allMatch(q -> q.getStatus() == InterviewQuestionStatus.ANSWERED)
                && answers.values().stream().allMatch(a -> a.getStatus() == InterviewAnswerStatus.EVALUATED), "SECOND_UNFINISHED_QUESTION_OR_ANSWER");
        var qPort = RagE2eReadOnlyVerifier.port(InterviewQuestionMapper.class, Map.of("getInterviewQuestionById", a -> questions.get(a[0])));
        var aPort = RagE2eReadOnlyVerifier.port(InterviewAnswerMapper.class, Map.of("getInterviewAnswerById", a -> answers.get(a[0]),
                "getInterviewAnswerByInterviewQuestionId", a -> answers.values().stream().filter(v -> v.getInterviewQuestionId().equals(a[0])).findFirst().orElseThrow()));
        var contextService = new EvaluationContextService(aPort, qPort, JSON);
        var finalByMain = new LinkedHashMap<Long, Integer>();
        for (var row : snapshot.path("evaluations")) {
            var evaluation = RagE2eReadOnlyVerifier.entity(row, AnswerEvaluation.class);
            var context = contextService.buildContext(evaluation.getAnswerId());
            var validated = new LlmEvaluationValidator().validate(context, JSON.readValue(evaluation.getRawResult(), LlmEvaluationSuggestion.class));
            var score = new EvaluationScoreCalculator().calculate(validated);
            boolean next = questions.values().stream().anyMatch(q -> q.getQuestionType() == InterviewQuestionType.MAIN
                    && q.getPlanOrder()>questions.get(context.mainInterviewQuestionId()).getPlanOrder());
            var decision = new FollowUpPolicy(new FollowUpTargetResolver()).decide(context, validated, score, next);
            check(score.totalScore() == evaluation.getTotalScore() && decision.evaluationPhase() == evaluation.getEvaluationPhase()
                    && decision.decisionAction() == evaluation.getDecisionAction() && new EvaluationStandard().resolveLevel(score.totalScore()).name().equals(evaluation.getLevel()), "SECOND_JAVA_SCORE_DECISION");
            check(List.of(score.correctnessScore(), score.completenessScore(), score.depthScore(), score.clarityScore(), score.practiceScore())
                    .equals(List.of(evaluation.getCorrectnessScore(), evaluation.getCompletenessScore(), evaluation.getDepthScore(), evaluation.getClarityScore(), evaluation.getPracticeScore()))
                    && RagE2eConsistency.equivalent(JSON.valueToTree(validated.scoringPointResults()), JSON.readTree(evaluation.getScoringPointResults())), "SECOND_SCORE_DIMENSIONS_COVERAGE");
            var batch = find(snapshot.path("ragBatches"), "answer_id", evaluation.getAnswerId().toString());
            String query = new EvaluationRagQueryBuilder().build(context);
            check(batch.path("retrieval_batch_id").asText().equals(evaluation.getRetrievalBatchId()) && batch.path("query_text").asText().equals(query)
                    && batch.path("top_k").asInt() == 5 && batch.path("raw_hit_count").asInt() == 5 && batch.path("evidence_hit_count").asInt() == 5
                    && !JSON.readTree(batch.path("filter_snapshot").asText()).path("cosineCandidate").path("enabled").asBoolean(), "SECOND_RAG_CONTRACT");
            if (evaluation.getAnswerId() != 100) {
                JsonNode evidence = null;
                for (int ordinal = 2; ordinal <= 7; ordinal++) {
                    var candidate = read("answer-"+ordinal+".evidence.json");
                    if (candidate.path("answerId").asLong() == evaluation.getAnswerId()) {
                        evidence = candidate;
                        break;
                    }
                }
                check(evidence != null, "SECOND_ANSWER_EVIDENCE_MISSING");
                List<RetrievedChunk> chunks = JSON.convertValue(evidence.path("evidence"), new TypeReference<>() {});
                List<VectorSearchHit> rawHits = JSON.convertValue(evidence.path("rawMilvusHits"), new TypeReference<>() {});
                var prompt = new EvaluationPromptBuilder().build(context, new EvaluationRetrievalResult(query, 5, RagE2ePolicy.MODEL, RagE2ePolicy.VERSION, rawHits, chunks));
                check(chunks.size() == 5 && evidence.path("query").asText().equals(query)
                        && RagE2eConsistency.equivalent(JSON.valueToTree(prompt), evidence.path("prompt")), "SECOND_PROMPT_REPLAY");
                int hits = 0;
                for (var hit : snapshot.path("ragHits"))
                    if (hit.path("answer_id").asLong() == evaluation.getAnswerId()) {
                        hits++;
                        var chunk = chunks.get(hit.path("rank_no").asInt() - 1);
                        check(hit.path("chunk_id").asLong() == chunk.chunkId() && hit.path("retrieval_batch_id").asText().equals(evaluation.getRetrievalBatchId())
                            && Math.abs(hit.path("similarity_score").asDouble() - chunk.similarityScore())<.000001, "SECOND_RAG_CITATION");
                    }
                check(hits == 5, "SECOND_RAG_HITS");
                boolean model = false, embed = false;
                for (var wire : read("providers/provider-index.json"))
                    if (wire.path("operation").asText().equals("answer-"+evidenceOrdinal(evidence))) {
                        var request = read("providers/"+wire.path("requestFile").asText());
                        var response = read("providers/"+wire.path("responseFile").asText());
                        if (wire.path("provider").asText().equals("embedding")) {
                            embed = true;
                            check(request.path("input").get(0).asText().equals(query)
                            && response.path("data").get(0).path("embedding").size() == 1024, "SECOND_EMBEDDING_WIRE");
                        } else {
                            model = true;
                            check(request.path("messages").get(0).path("content").asText().equals(prompt.systemPrompt())
                            && request.path("messages").get(1).path("content").asText().equals(prompt.userPrompt())
                            && RagE2eConsistency.equivalent(JSON.readTree(response.path("choices").get(0).path("message").path("content").asText()), JSON.readTree(evaluation.getRawResult())), "SECOND_JUDGE_WIRE");
                        }
                    }
                check(model && embed, "SECOND_WIRE_PAIR");
            }
            if (evaluation.getEvaluationPhase() == EvaluationPhase.FINAL)
                check(finalByMain.put(evaluation.getMainInterviewQuestionId(), evaluation.getTotalScore()) == null, "SECOND_DUPLICATE_FINAL");
        }
        check(finalByMain.size() == 5, "SECOND_FIVE_FINALS");
        var average = BigDecimal.valueOf(finalByMain.values().stream().mapToInt(Integer::intValue).sum()).divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);
        var saved = snapshot.path("report").get(0);
        var http = lastHttp(report, "GET", "/api/interviews/150/report");
        check(average.compareTo(new BigDecimal(session.path("total_score").asText())) == 0
                && average.compareTo(new BigDecimal(saved.path("overall_score").asText())) == 0 && average.compareTo(http.path("overallScore").decimalValue()) == 0, "SECOND_REPORT_AVERAGE");
        check(saved.path("result").asText().equals(new EvaluationStandard().resolveLevel(average).name()), "SECOND_REPORT_LEVEL");
        var raw = JSON.readTree(read("providers/13-report-deepseek.response.json").path("choices").get(0).path("message").path("content").asText());
        check(raw.path("summary").asText().strip().equals(saved.path("summary").asText()) && http.path("summary").asText().equals(saved.path("summary").asText()), "SECOND_REPORT_SUMMARY");
        for (String field : List.of("strengths", "weaknesses", "suggestions")) {
            List<String> normalized = new ArrayList<>();
            raw.path(field).forEach(v -> normalized.add(v.asText().strip()));
            check(RagE2eConsistency.equivalent(JSON.valueToTree(normalized), JSON.readTree(saved.path(field).asText()))
                    && RagE2eConsistency.equivalent(http.path(field), JSON.readTree(saved.path(field).asText())), "SECOND_REPORT_LISTS");
        }
        check(snapshot.path("weaknesses").size() == 1, "SECOND_WEAKNESS_COUNT");
        var weak = snapshot.path("weaknesses").get(0);
        check(weak.path("category").asText().equals("NETWORK") && weak.path("knowledge_point").asText().equals("tcp_vs_udp")
                && new BigDecimal(weak.path("weakness_score").asText()).compareTo(BigDecimal.valueOf(90)) == 0
                && weak.path("discovered_count").asInt() == 1 && weak.path("status").asText().equals("ACTIVE"), "SECOND_WEAKNESS_FINAL_ONLY");
        check(lastHttp(report, "GET", "/api/interviews/history?page=1&pageSize=10").path("items").get(0).path("sessionId").asLong() == 150, "SECOND_HISTORY_HTTP");
    }

    static int evidenceOrdinal(JsonNode evidence) throws Exception {
        for (int i = 2; i <= 7; i++)
            if (read("answer-"+i+".evidence.json").path("answerId").asText().equals(evidence.path("answerId").asText()))
            return i;
        throw new SecurityException("SECOND_EVIDENCE_ORDINAL");
    }

    static Map<String, Object> progress(JsonNode snapshot) {
        var finals = new ArrayList<Map<String, Object>>();
        for (var q : snapshot.path("questions"))
            if (q.path("question_type").asText().equals("MAIN")) {
                var eval = new ArrayList<JsonNode>();
                for (var e : snapshot.path("evaluations"))
                    if (e.path("main_interview_question_id").asText().equals(q.path("id").asText()))
                    eval.add(e);
                var end = eval.stream().filter(e -> e.path("evaluation_phase").asText().equals("FINAL")).findFirst().orElseThrow();
                finals.add(Map.of("mainQuestionId", q.path("id").asLong(), "knowledgePoint", q.path("knowledge_point").asText(),
                    "finalAnswerId", end.path("answer_id").asLong(), "finalEvaluationId", end.path("id").asLong(), "score", end.path("total_score").asInt(), "followUp", eval.size() == 2));
            }
        return Map.of("sessionId", 150, "status", "COMPLETED", "reportStatus", "READY", "completedMains", 5, "followUps", 2,
                "answerRows", 7, "ragBatches", 7, "ragHitRows", 35, "mains", finals, "report", Map.of("id", 14, "overallScore", "74.20", "weaknessRows", 1));
    }

    static Map<String, Object> inspect(Connection c, Path out) throws Exception {
        FinalE2eRunner.output = out;
        FinalE2eRunner.userId = 427;
        FinalE2eRunner.sessionId = 150;
        FinalE2eRunner.continuationSessionId = 150;
        var preflight = FinalE2eRunner.preflight(c); // Frozen MySQL/Milvus SELECT/query only; never creates/loads vectors.
        var current = FinalE2eRunner.JSON.valueToTree(FinalE2eRunner.snapshot(c, "current"));
        check(RagE2eConsistency.equivalent(current, read("99-final-or-failure.database.json")), "SECOND_CURRENT_STATE_CHANGED");
        verifyCompleted(current, read("report.json"));
        FinalE2eRepair.verifyFirstEvaluation(FinalE2eRepair.read("99-final-or-failure.database.json"), c);
        var hits = FinalE2eRunner.sql(c, "SELECT h.answer_id,h.rank_no,h.chunk_id,c.vector_id,c.document_id,c.chunk_index,c.document_version,c.embedding_model,c.embedding_version,c.status,c.content,d.title,d.source,d.category,d.processing_status,d.document_version AS current_version "
                +"FROM rag_hit_log h JOIN interview_answer a ON a.id=h.answer_id JOIN interview_question q ON q.id=a.interview_question_id "
                +"JOIN knowledge_chunk c ON c.id=h.chunk_id JOIN knowledge_document d ON d.id=c.document_id WHERE q.session_id=150 ORDER BY h.answer_id,h.rank_no");
        check(hits.size() == 35, "SECOND_CURRENT_HYDRATION_COUNT");
        for (var row : hits)
            check(row.get("status").equals("ACTIVE") && row.get("processing_status").equals("READY")
                && row.get("document_version").equals(row.get("current_version")) && row.get("embedding_version").equals(RagE2ePolicy.VERSION), "SECOND_CURRENT_EVIDENCE_ELIGIBILITY");
        for (int i = 2; i <= 7; i++) {
            var saved = read("answer-"+i+".evidence.json");
            for (var chunk : saved.path("evidence")) {
                var row = hits.stream().filter(h -> h.get("answer_id").equals(saved.path("answerId").asText())
                        && h.get("rank_no").equals(chunk.path("vectorRank").asText())).findFirst().orElseThrow();
                check(row.get("chunk_id").equals(chunk.path("chunkId").asText()) && row.get("content").equals(chunk.path("content").asText())
                        && row.get("vector_id").equals(chunk.path("vectorId").asText()) && row.get("document_id").equals(chunk.path("documentId").asText())
                        && row.get("title").equals(chunk.path("title").asText()) && row.get("category").equals(chunk.path("category").asText())
                        && Objects.equals(row.get("source"), chunk.path("source").isNull() ? null : chunk.path("source").asText()), "SECOND_CURRENT_PROMPT_EVIDENCE_MISMATCH");
            }
        }
        var users = FinalE2eRunner.sql(c, "SELECT id,account,role,status,password_changed_at FROM `user` WHERE id=427");
        check(users.size() == 1 && users.getFirst().get("account").equals(FinalE2eResume.ACCOUNT) && users.getFirst().get("role").equals("USER")
                && users.getFirst().get("status").equals("ENABLED") && users.getFirst().get("password_changed_at") != null, "SECOND_CURRENT_USER");
        FinalE2eRunner.verifyHistory(c);
        return Map.of("status", "PASS", "sameAsPreservedTerminalSnapshot", true, "preflight", preflight, "user", users.getFirst(),
                "currentProgress", progress(current), "newModelCalls", 0, "databaseWrites", 0);
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 2 && Set.of("offline", "inspect").contains(args[0]), "SECOND_REPAIR_MODE");
        Path out = Path.of(args[1]);
        var result = replay();
        if (args[0].equals("inspect"))
            try (var c = FinalE2eRunner.connection()) {
                result.put("liveReadOnlyVerification", inspect(c, out));
            }
        result.put("OFFLINE_REPAIR_STATUS", "PASS");
        result.put("NEXT_REAL_RUN_READY", "NO");
        result.put("nextRunReason", "NOT_NEEDED_ALL_BUSINESS_COMPLETE; no attempt 003, fee authorization or credential rotation required");
        result.put("FINAL_E2E_RECONCILED_STATUS", args[0].equals("inspect") ? "REAL_E2E_PASS_RECONCILED" : "OFFLINE_EVIDENCE_PASS");
        FinalE2eRunner.save(out.resolve("second-failure-diagnosis.json"), result);
        System.out.println("SECOND_FAILURE_REPLAY=PASS; mode="+args[0]+"; new model calls=0; database writes=0; evidence="+out);
    }
}
