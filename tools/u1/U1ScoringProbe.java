import com.fasterxml.jackson.databind.JsonNode;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalResult;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.interview.evaluation.decision.FollowUpPolicy;
import com.kun.aiinterview.interview.evaluation.decision.FollowUpTargetResolver;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekConfiguration;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPromptBuilder;
import com.kun.aiinterview.interview.evaluation.prompt.U1HistoricalPromptBuilder;
import com.kun.aiinterview.interview.evaluation.score.EvaluationScoreCalculator;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.regex.Pattern;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;

/** Paired assessment of the eight frozen synthetic answers. No database or embedding client. */
public final class U1ScoringProbe {
    private static final Path FIXTURE = Path.of("docs/verification/U1_SYNTHETIC_SCORING_FIXTURES_20261114.json");
    private static final Path HISTORICAL = Path.of("backups/D3_BCD_20261114/production-predictions");
    private static final String HEADER = "===== RAG EVIDENCE =====\n";
    private static final String MODEL = "deepseek-flash";
    private static final int MAX_TOKENS = 4096;
    private static final int MAX_BYTES = 48000;
    private static final int MODEL_COUNT = 16;
    private static final URI ENDPOINT = URI.create("https://api.deepseek.com/chat/completions");
    private static final Pattern ENTRY = Pattern.compile(
            "(?ms)^- rank=(\\d+), similarity=[^\\n]+\\n  title=([^\\n]*)\\n  category=([^\\n]*)\\n  source=([^\\n]*)\\n  content=(.*?)(?=\\n\\n- rank=|\\n\\n\\z)");
    private static final List<String> MODES = List.of("OLD", "NEW");

    record Arm(String name, EvaluationContext context, EvaluationPrompt prompt) {}
    record Sample(String caseId, String bankId, JsonNode expectations, List<Arm> arms) {}
    record Plan(String fixtureSha256, String contentSha256, String legacyPromptSourceSha256,
                String candidatePromptSourceSha256, List<Sample> samples) {}

    public static void main(String[] args) throws Exception {
        require(args.length >= 4 && args.length <= 6, "U1_ARGUMENTS");
        String mode = args[0];
        Path work = Path.of(args[1]);
        Plan plan = prepare(args[2], args[3]);
        Path frozen = work.resolve("frozen-plan.json");
        if ("prepare".equals(mode)) {
            D3Corpus.writeNew(frozen, plan);
            System.out.println("Prepared 8 pairs. All 16 Prompt evidence sections match their frozen source byte for byte.");
            return;
        }
        byte[] frozenBytes = Files.readAllBytes(frozen);
        require(args.length >= 5 && D3Corpus.sha(frozenBytes).equals(args[4])
                && D3Corpus.JSON.readTree(frozenBytes).equals(D3Corpus.JSON.readTree(D3Corpus.JSON.writeValueAsBytes(plan))),
                "U1_FROZEN_PLAN_CHANGED");
        if ("verify".equals(mode)) {
            return;
        }
        require(args.length == 6 && "execute".equals(mode) && "true".equals(System.getenv("U1_SCORING_ARMED")),
                "U1_SCORING_NOT_ARMED");
        execute(work, plan, Integer.parseInt(args[5]));
    }

    private static Plan prepare(String historicalSourceSha, String candidateSourceSha) throws Exception {
        var oldBuilder = new U1HistoricalPromptBuilder();
        var newBuilder = new EvaluationPromptBuilder();
        byte[] fixtureBytes = Files.readAllBytes(FIXTURE);
        var fixtures = D3Corpus.JSON.readTree(fixtureBytes);
        var contentFile = Path.of(fixtures.required("questionContentFile").asText());
        require(contentFile.equals(Path.of("docs/question-bank/U1_QUESTION_CONTENT_CANDIDATES_20261114.json")),
                "U1_CONTENT_PATH");
        byte[] contentBytes = Files.readAllBytes(contentFile);
        String contentSha = D3Corpus.sha(contentBytes);
        require(fixtures.required("questionContentSha256").asText().equals(contentSha), "U1_CONTENT_SHA");
        Map<String, JsonNode> byBank = new LinkedHashMap<>();
        for (var item : D3Corpus.JSON.readTree(contentBytes).required("questions")) {
            byBank.put(item.required("bankId").asText(), item);
        }
        require(byBank.keySet().equals(java.util.Set.of("E08", "E04")), "U1_CONTENT_IDS");
        Map<String, EvaluationRetrievalResult> evidence = new LinkedHashMap<>();
        for (String bankId : byBank.keySet()) {
            evidence.put(bankId, evidence(bankId, fixtures.required("evidence").required(bankId)));
        }
        var samples = new ArrayList<Sample>();
        var seen = new java.util.HashSet<String>();
        for (var test : fixtures.required("cases")) {
            String caseId = test.required("caseId").asText();
            String bankId = test.required("bankId").asText();
            String answer = test.required("answer").asText();
            require(byBank.containsKey(bankId) && seen.add(caseId)
                    && caseId.matches("E0[48]-(NAMES_ONLY|BRIEF_CORRECT|PARTIAL|FACTUAL_ERROR)")
                    && caseId.startsWith(bankId + "-") && !answer.isBlank(), "U1_CASE_ID");
            var question = byBank.get(bankId);
            EvaluationContext oldContext = context(question.required("baselineRequest"), answer);
            EvaluationContext newContext = context(question.required("candidateRequest"), answer);
            require(oldContext.category() == newContext.category()
                    && oldContext.knowledgePoint().equals(newContext.knowledgePoint())
                    && oldContext.scoringPoints().size() == newContext.scoringPoints().size(), "U1_COMPARISON_CONTEXT");
            var oldPrompt = oldBuilder.build(oldContext, evidence.get(bankId));
            var newPrompt = newBuilder.build(newContext, evidence.get(bankId));
            String referenceEvidence = D3Corpus.JSON.readTree(HISTORICAL.resolve(bankId + ".json").toFile())
                    .required("promptEvidence").asText();
            require("evaluation-prompt-v3".equals(oldPrompt.promptVersion())
                    && "evaluation-prompt-v4".equals(newPrompt.promptVersion())
                    && promptEvidence(oldPrompt).equals(referenceEvidence)
                    && promptEvidence(newPrompt).equals(referenceEvidence), "U1_PROMPT_EVIDENCE");
            var a = new Arm("OLD", oldContext, oldPrompt);
            var b = new Arm("NEW", newContext, newPrompt);
            samples.add(new Sample(caseId, bankId, test.required("expectations"),
                    samples.size() % 2 == 0 ? List.of(a, b) : List.of(b, a)));
        }
        require(samples.size() * 2 == MODEL_COUNT, "U1_CASE_COUNT");
        return new Plan(D3Corpus.sha(fixtureBytes), contentSha, historicalSourceSha, candidateSourceSha,
                List.copyOf(samples));
    }

    private static EvaluationContext context(JsonNode request, String answer) {
        var points = new ArrayList<ScoringPointSnapshot>();
        for (var point : request.required("scoringPoints")) {
            points.add(new ScoringPointSnapshot((long) points.size() + 1,
                    QuestionPointType.valueOf(point.required("pointType").asText()),
                    point.required("content").asText(), point.required("weight").asInt()));
        }
        return new EvaluationContext(1L, 1L, 1L, 1L, EvaluationMode.MAIN_ANSWER,
                QuestionCategory.valueOf(request.required("category").asText()), request.required("knowledgePoint").asText(),
                request.required("questionContent").asText(), request.required("referenceAnswer").asText(), points,
                answer, null, null, List.of());
    }

    private static EvaluationRetrievalResult evidence(String bankId, JsonNode expected) throws IOException {
        var path = HISTORICAL.resolve(bankId + ".json");
        byte[] bytes = Files.readAllBytes(path);
        JsonNode historical = D3Corpus.JSON.readTree(bytes);
        String text = historical.required("promptEvidence").asText();
        require(expected.required("predictionFile").asText().equals(path.toString())
                && expected.required("predictionSha256").asText().equals(D3Corpus.sha(bytes))
                && expected.required("promptEvidenceSha256").asText().equals(D3Corpus.sha(text))
                && expected.required("promptOrder").equals(historical.required("prompt")), "U1_EVIDENCE_SHA");
        var matcher = ENTRY.matcher(text);
        var chunks = new ArrayList<RetrievedChunk>();
        while (matcher.find()) {
            int rank = Integer.parseInt(matcher.group(1));
            var raw = historical.required("raw").get(rank - 1);
            var hydrated = historical.required("hydrated").get(chunks.size());
            var selected = historical.required("prompt").get(chunks.size());
            require(rank == raw.required("rank").asInt() && rank == hydrated.required("rank").asInt()
                    && rank == selected.required("rank").asInt() && raw.required("key").equals(hydrated.required("key"))
                    && hydrated.required("key").equals(selected.required("key"))
                    && D3Corpus.sha(matcher.group(5)).equals(selected.required("sha256").asText())
                    && hydrated.required("sha256").equals(selected.required("sha256")), "U1_EVIDENCE_ENTRY");
            chunks.add(new RetrievedChunk(rank, raw.required("score").asDouble(), hydrated.required("chunkId").asLong(),
                    null, null, null, raw.required("vectorId").asText(), matcher.group(2),
                    KnowledgeCategory.valueOf(matcher.group(3)), matcher.group(4), matcher.group(5)));
        }
        require(chunks.size() == 5 && historical.required("raw").size() == chunks.size()
                && historical.required("hydrated").size() == chunks.size()
                && historical.required("prompt").size() == chunks.size(), "U1_EVIDENCE_COUNT");
        return new EvaluationRetrievalResult("U1_FIXED_SYNTHETIC_COMPARISON", 5, D3Inventory.MODEL,
                D3Inventory.PROFILE, List.of(), chunks);
    }

    private static String promptEvidence(EvaluationPrompt prompt) {
        int offset = prompt.userPrompt().indexOf(HEADER);
        require(offset >= 0, "U1_PROMPT_EVIDENCE_HEADER");
        return prompt.userPrompt().substring(offset + HEADER.length());
    }

    private static void execute(Path work, Plan plan, int previousU1Attempts) throws Exception {
        require(previousU1Attempts >= 0 && previousU1Attempts + MODEL_COUNT <= 24, "U1_ATTEMPT_CAP");
        var properties = new DeepSeekProperties();
        properties.setBaseUrl(URI.create("https://api.deepseek.com"));
        properties.setModel(MODEL);
        properties.setApiKey(System.getenv("DEEPSEEK_API_KEY"));
        properties.setMaxTokens(MAX_TOKENS);
        properties.setConnectTimeout(Duration.ofSeconds(3));
        properties.setReadTimeout(Duration.ofSeconds(60));
        require(properties.getApiKey() != null && !properties.getApiKey().isBlank(), "U1_SCORING_KEY");
        var journal = work.getParent().resolve("deepseek-usage.jsonl");
        var sequence = plan.samples().stream().flatMap(sample -> sample.arms().stream()).toList();
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var builder = RestClient.builder().requestInterceptor((request, body, next) -> {
            JsonNode payload = D3Corpus.JSON.readTree(body);
            require(attempts.get() < MODEL_COUNT && ENDPOINT.equals(request.getURI())
                    && "POST".equals(request.getMethod().name()) && body.length <= MAX_BYTES, "U1_REQUEST_RESTRICTION");
            var armed = sequence.get(attempts.get());
            var messages = payload.required("messages");
            require(payload.required("model").asText().equals(MODEL) && payload.required("max_tokens").asInt() == MAX_TOKENS
                    && !payload.required("stream").asBoolean()
                    && payload.required("thinking").required("type").asText().equals("disabled")
                    && payload.required("response_format").required("type").asText().equals("json_object")
                    && messages.size() == 2
                    && messages.get(0).required("role").asText().equals("system")
                    && messages.get(0).required("content").asText().equals(armed.prompt().systemPrompt())
                    && messages.get(1).required("role").asText().equals("user")
                    && messages.get(1).required("content").asText().equals(armed.prompt().userPrompt()), "U1_REQUEST_PAYLOAD");
            int attempt = previousU1Attempts + attempts.incrementAndGet();
            append(journal, Map.of("event", "attempt", "attempt", attempt, "operation", "scoring-comparison",
                    "startedAt", Instant.now().toString(), "bodyUtf8Bytes", body.length, "maxOutputTokens", MAX_TOKENS));
            var response = next.execute(request, body);
            try {
                append(journal, Map.of("event", "headers", "attempt", attempt, "status", response.getStatusCode().value()));
                var usage = new D3SmokeServer.UsageResponse(response, attempt, event -> appendUnchecked(journal, event));
                Files.write(work.resolve("http-" + attempt + ".json"), usage.getBody().readAllBytes(), CREATE_NEW);
                return usage;
            } catch (IOException | RuntimeException failure) {
                response.close();
                throw failure;
            }
        });
        var client = new DeepSeekEvaluationClient(new DeepSeekConfiguration().deepSeekRestClient(builder, properties),
                properties, D3Corpus.JSON);
        var validator = new LlmEvaluationValidator();
        var score = new EvaluationScoreCalculator();
        var followUp = new FollowUpPolicy(new FollowUpTargetResolver());
        for (var sample : plan.samples()) {
            for (var arm : sample.arms()) {
                SequencedMap<String, Object> output = new LinkedHashMap<>();
                output.put("caseId", sample.caseId());
                output.put("arm", arm.name());
                long started = System.nanoTime();
                int previous = attempts.get();
                boolean completionParsed = false;
                try {
                    var evaluation = client.evaluate(arm.prompt());
                    completionParsed = true;
                    var checked = validator.validate(arm.context(), evaluation.suggestion());
                    var total = score.calculate(checked);
                    output.put("status", "VALIDATED");
                    output.put("evaluation", evaluation);
                    output.put("javaScore", total);
                    output.put("javaDecision", followUp.decide(arm.context(), checked, total, true));
                } catch (RuntimeException failure) {
                    output.put("status", "ERROR");
                    output.put("errorType", failure.getClass().getSimpleName());
                } finally {
                    output.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
                    output.put("attempt", attempts.get() == previous + 1 ? previousU1Attempts + attempts.get() : null);
                    D3Corpus.writeNew(work.resolve(sample.caseId() + "-" + arm.name() + ".json"), output);
                }
                // Preserve validation failures as observations. A transport/provider parse failure stops the run.
                require(completionParsed && attempts.get() == previous + 1, "U1_PROVIDER_STOPPED");
            }
        }
        require(attempts.get() == MODEL_COUNT, "U1_ATTEMPT_COUNT");
        System.out.printf("Completed the frozen %d-attempt scoring sequence. Raw results are private.%n", MODEL_COUNT);
    }

    private static void append(Path journal, Map<String, Object> event) throws IOException {
        Files.writeString(journal, D3Corpus.JSON.writeValueAsString(event) + "\n", CREATE, APPEND);
    }

    private static void appendUnchecked(Path journal, Map<String, Object> event) {
        try {
            append(journal, event);
        } catch (IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }

    private static void require(boolean condition, String code) {
        D3Corpus.require(condition, code);
    }
}
