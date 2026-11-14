import com.fasterxml.jackson.databind.JsonNode;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalResult;
import com.kun.aiinterview.interview.evaluation.decision.FollowUpPolicy;
import com.kun.aiinterview.interview.evaluation.decision.FollowUpTargetResolver;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekConfiguration;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPromptBuilder;
import com.kun.aiinterview.interview.evaluation.score.EvaluationScoreCalculator;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/** Fixed, non-persistent scoring comparison. Launch paid mode only through d3e_scoring.py. */
public final class D3EScoringProbe {
    private static final String HEADER = "===== RAG EVIDENCE =====\n";
    private static final Pattern EVIDENCE = Pattern.compile(
            "(?ms)^- rank=(\\d+), similarity=[^\\n]+\\n  title=([^\\n]*)\\n  category=([^\\n]*)\\n  source=([^\\n]*)\\n  content=(.*?)(?=\\n\\n- rank=|\\n\\n\\z)");
    private static final Path HISTORY = Path.of("backups/D3_BCD_20261114");
    private static final Path FIXTURES = Path.of("tools/rag-d3/d3e-scoring-fixtures.json");
    private static final URI ENDPOINT = URI.create("https://api.deepseek.com/chat/completions");
    private static final int MAX_TOKENS = 4096;
    private static final int MAX_BYTES = 48000;

    record ProbeCase(String queryId, EvaluationContext context, List<Long> expectedCoveredPointIds,
                     JsonNode referenceChecks, Map<String, EvaluationPrompt> prompts, String predictionSha256) {}
    record Plan(String fixtureSha256, String bankSha256, String importSha256, List<ProbeCase> cases) {}

    public static void main(String[] args) throws Exception {
        require(args.length >= 2, "SCORING_ARGUMENTS");
        Path run = Path.of(args[1]);
        Plan plan = prepare();
        Path frozen = run.resolve("frozen-scoring-plan.json");
        if (args.length == 2 && "prepare".equals(args[0])) {
            D3Corpus.writeNew(frozen, plan);
            System.out.println("Prepared 8 paired cases and verified 8 baseline Prompt evidence sections byte for byte.");
            return;
        }
        boolean verify = args.length == 3 && "verify".equals(args[0]);
        require(verify || args.length == 4 && "execute".equals(args[0])
                && "true".equals(System.getenv("D3E_SCORING_ARMED")), "SCORING_NOT_AUTHORIZED");
        JsonNode contents = D3Corpus.JSON.readTree(frozen.toFile());
        // Parse both sides so small long values written as JSON integers get the same node type.
        JsonNode current = D3Corpus.JSON.readTree(D3Corpus.JSON.writeValueAsBytes(plan));
        require(D3Corpus.sha(Files.readAllBytes(frozen)).equals(args[2])
                && contents.equals(current), "SCORING_FROZEN_PLAN_CHANGED");
        if (!verify) {
            execute(run, plan, Integer.parseInt(args[3]));
        }
    }

    private static Plan prepare() throws Exception {
        Map<String, D3Queries.Query> queries = new HashMap<>();
        D3Queries.production().forEach(query -> queries.put(query.id(), query));
        byte[] fixtures = Files.readAllBytes(FIXTURES);
        var cases = new ArrayList<ProbeCase>();
        var seen = new java.util.HashSet<String>();
        var builder = new EvaluationPromptBuilder();
        for (var fixture : D3Corpus.JSON.readTree(fixtures).required("cases")) {
            String queryId = fixture.required("queryId").asText();
            require(seen.add(queryId) && queries.containsKey(queryId), "SCORING_QUERY_ID");
            var query = queries.get(queryId);
            var original = query.context();
            String followUpAnswer = fixture.has("followUpAnswer") ? fixture.required("followUpAnswer").asText() : null;
            require((original.followUpQuestionContent() == null) == (followUpAnswer == null), "SCORING_MODE");
            var context = new EvaluationContext(original.currentAnswerId(), original.currentInterviewQuestionId(),
                    original.mainAnswerId(), original.mainInterviewQuestionId(), original.mode(), original.category(),
                    original.knowledgePoint(), original.mainQuestionContent(), original.referenceAnswerSnapshot(),
                    original.scoringPoints(), fixture.required("mainAnswer").asText(), original.followUpQuestionContent(),
                    followUpAnswer, original.followUpTargetPointIds());
            List<Long> expected = new ArrayList<>();
            fixture.required("coveredPoints").forEach(point -> expected.add(context.scoringPoints().get(point.asInt() - 1).scoringPointId()));
            byte[] predictionBytes = Files.readAllBytes(HISTORY.resolve("production-predictions").resolve(queryId + ".json"));
            JsonNode prediction = D3Corpus.JSON.readTree(predictionBytes);
            require(queryId.equals(prediction.required("id").asText()), "SCORING_PREDICTION_ID");
            List<RetrievedChunk> evidence = evidence(prediction);
            var baseline = builder.build(context, retrieval(query, evidence));
            var diagnostic = builder.build(context, retrieval(query, evidence.stream().limit(2).toList()));
            require(baseline.userPrompt().substring(baseline.userPrompt().indexOf(HEADER) + HEADER.length())
                    .equals(prediction.required("promptEvidence").asText()), "SCORING_BASELINE_PROMPT_BYTES");
            require(baseline.systemPrompt().equals(diagnostic.systemPrompt())
                    && baseline.userPrompt().substring(0, baseline.userPrompt().indexOf(HEADER))
                    .equals(diagnostic.userPrompt().substring(0, diagnostic.userPrompt().indexOf(HEADER))), "SCORING_CONTROLLED_INPUT");
            require(baseline.userPrompt().getBytes(StandardCharsets.UTF_8).length
                    + baseline.systemPrompt().getBytes(StandardCharsets.UTF_8).length < MAX_BYTES - 2000,
                    "SCORING_PROMPT_SIZE");
            cases.add(new ProbeCase(queryId, context, expected, fixture.required("referenceChecks"),
                    Map.of("A", baseline, "C", diagnostic), D3Corpus.sha(predictionBytes)));
        }
        require(cases.size() == 8, "SCORING_CASE_COUNT");
        return new Plan(D3Corpus.sha(fixtures), D3Queries.BANK_SHA, D3Queries.IMPORT_SHA, List.copyOf(cases));
    }

    private static List<RetrievedChunk> evidence(JsonNode prediction) {
        var matched = EVIDENCE.matcher(prediction.required("promptEvidence").asText());
        var chunks = new ArrayList<RetrievedChunk>();
        while (matched.find()) {
            int rank = Integer.parseInt(matched.group(1));
            var raw = prediction.required("raw").get(rank - 1);
            var eligible = prediction.required("hydrated").get(chunks.size());
            var selected = prediction.required("prompt").get(chunks.size());
            String hash = D3Corpus.sha(matched.group(5));
            require(raw.required("rank").asInt() == rank && eligible.required("rank").asInt() == rank
                    && selected.required("rank").asInt() == rank && hash.equals(selected.required("sha256").asText())
                    && hash.equals(eligible.required("sha256").asText()) && raw.required("key").equals(eligible.required("key"))
                    && raw.required("key").equals(selected.required("key")), "SCORING_EVIDENCE_IDENTITY");
            // Only fields serialized by the production PromptBuilder and known historical IDs are needed.
            chunks.add(new RetrievedChunk(rank, raw.required("score").asDouble(), eligible.required("chunkId").asLong(),
                    null, null, null, raw.required("vectorId").asText(), matched.group(2),
                    KnowledgeCategory.valueOf(matched.group(3)), matched.group(4), matched.group(5)));
        }
        require(chunks.size() == prediction.required("hydrated").size()
                && chunks.size() == prediction.required("prompt").size(), "SCORING_EVIDENCE_COUNT");
        return List.copyOf(chunks);
    }

    private static EvaluationRetrievalResult retrieval(D3Queries.Query query, List<RetrievedChunk> evidence) {
        return new EvaluationRetrievalResult(query.text(), 5, D3Inventory.MODEL, D3Inventory.PROFILE, List.of(), evidence);
    }

    private static void execute(Path run, Plan plan, int previousAttempts) throws Exception {
        require(previousAttempts >= 10 && previousAttempts + 16 <= 200, "SCORING_ATTEMPT_BUDGET");
        var properties = new DeepSeekProperties();
        properties.setBaseUrl(URI.create("https://api.deepseek.com"));
        properties.setApiKey(System.getenv("DEEPSEEK_API_KEY"));
        require(properties.getApiKey() != null && !properties.getApiKey().isBlank(), "SCORING_API_KEY");
        properties.setModel("deepseek-flash");
        properties.setMaxTokens(MAX_TOKENS);
        properties.setConnectTimeout(Duration.ofSeconds(3));
        properties.setReadTimeout(Duration.ofSeconds(60));
        var journal = run.resolve("deepseek-usage.jsonl");
        var attempts = new java.util.concurrent.atomic.AtomicInteger(previousAttempts);
        var builder = RestClient.builder().requestInterceptor((request, body, next) -> {
            JsonNode payload = D3Corpus.JSON.readTree(body);
            require(ENDPOINT.equals(request.getURI()) && "POST".equals(request.getMethod().name())
                    && body.length <= MAX_BYTES && payload.required("model").asText().equals(properties.getModel())
                    && payload.required("max_tokens").asInt() == MAX_TOKENS
                    && !payload.required("stream").asBoolean() && attempts.get() < previousAttempts + 16,
                    "SCORING_REQUEST_GUARD");
            int attempt = attempts.incrementAndGet();
            append(journal, Map.of("event", "attempt", "attempt", attempt, "bodyUtf8Bytes", body.length,
                    "maxOutputTokens", MAX_TOKENS, "timestamp", java.time.Instant.now().toString()));
            var response = next.execute(request, body);
            try {
                append(journal, Map.of("event", "headers", "attempt", attempt, "status", response.getStatusCode().value()));
                var usage = new D3SmokeServer.UsageResponse(response, attempt, event -> {
                    try {
                        append(journal, event);
                    } catch (java.io.IOException error) {
                        throw new java.io.UncheckedIOException(error);
                    }
                });
                Files.write(run.resolve("http-" + attempt + ".json"), usage.getBody().readAllBytes(),
                        java.nio.file.StandardOpenOption.CREATE_NEW);
                return usage;
            } catch (java.io.IOException | RuntimeException error) {
                response.close();
                throw error;
            }
        });
        var client = new DeepSeekEvaluationClient(new DeepSeekConfiguration().deepSeekRestClient(builder, properties),
                properties, D3Corpus.JSON);
        var validator = new LlmEvaluationValidator();
        var calculator = new EvaluationScoreCalculator();
        var followUp = new FollowUpPolicy(new FollowUpTargetResolver());
        for (int i = 0; i < plan.cases().size(); i++) {
            var sample = plan.cases().get(i);
            for (String strategy : i % 2 == 0 ? List.of("A", "C") : List.of("C", "A")) {
                var row = new LinkedHashMap<String, Object>();
                row.put("queryId", sample.queryId());
                row.put("strategy", strategy);
                long started = System.nanoTime();
                boolean providerReturned = false;
                try {
                    var result = client.evaluate(sample.prompts().get(strategy));
                    providerReturned = true;
                    row.put("evaluation", result);
                    var validated = validator.validate(sample.context(), result.suggestion());
                    var score = calculator.calculate(validated);
                    row.put("javaScore", score);
                    row.put("javaDecision", followUp.decide(sample.context(), validated, score, true));
                    row.put("status", "VALIDATED");
                } catch (RuntimeException failure) {
                    row.put("status", "ERROR");
                    row.put("errorType", failure.getClass().getSimpleName());
                } finally {
                    row.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
                    row.put("attempt", attempts.get());
                    D3Corpus.writeNew(run.resolve(sample.queryId() + "-" + strategy + ".json"), row);
                    System.out.printf("Case %d/8 (%s) %s%n", i + 1, strategy, row.get("status"));
                }
                // Keep validation failures as observations. Stop if transport or response parsing failed.
                require(providerReturned, "SCORING_PROVIDER_STOPPED");
            }
        }
    }

    private static void append(Path journal, Map<String, Object> event) throws java.io.IOException {
        Files.writeString(journal, D3Corpus.JSON.writeValueAsString(event) + "\n", CREATE, APPEND);
    }

    private static void require(boolean condition, String code) { D3Corpus.require(condition, code); }
}
