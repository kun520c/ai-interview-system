import com.fasterxml.jackson.databind.JsonNode;
import com.kun.aiinterview.interview.evaluation.judge.jev.*;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.web.client.RestClient;

import java.io.*;
import java.lang.reflect.*;
import java.math.*;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Replays original HTTP bytes against original questions. In-memory transport; no external calls. */
public class JevHistoricalReplay {
    static final List<String> DIMENSIONS = List.of("correctnessScore", "completenessScore",
            "depthScore", "clarityScore", "practiceScore");
    static void require(boolean condition) { if (!condition) throw new IllegalStateException("EVIDENCE_MISMATCH"); }

    static JevClient client(byte[] responseBody) {
        ClientHttpRequestFactory factory = (uri, method) -> new ClientHttpRequest() {
            final HttpHeaders headers = new HttpHeaders();
            final Map<String, Object> attributes = new HashMap<>();
            final ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
            public HttpMethod getMethod() { return method; }
            public URI getURI() { return uri; }
            public HttpHeaders getHeaders() { return headers; }
            public Map<String, Object> getAttributes() { return attributes; }
            public OutputStream getBody() { return requestBody; }
            public ClientHttpResponse execute() {
                require(method == HttpMethod.POST && uri.toString().equals("http://offline.invalid/v1/systemone"));
                return new ClientHttpResponse() {
                    final HttpHeaders responseHeaders = new HttpHeaders();
                    public HttpStatusCode getStatusCode() { return HttpStatus.OK; }
                    public String getStatusText() { return "OK"; }
                    public HttpHeaders getHeaders() {
                        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
                        return responseHeaders;
                    }
                    public InputStream getBody() { return new ByteArrayInputStream(responseBody); }
                    public void close() { }
                };
            }
        };
        return new JevClient(RestClient.builder().baseUrl("http://offline.invalid")
                .requestFactory(factory).build(), JevScoreOnce.JSON);
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        var validate = JevJudgeProvider.class.getDeclaredMethod("validateAnswers", JevRequest.class, JevResponse.class);
        validate.setAccessible(true);
        var mapScore = JevJudgeProvider.class.getDeclaredMethod("mapScore", Double.class);
        mapScore.setAccessible(true);
        var provider = new JevJudgeProvider(null, new JevProperties(), null, null, JevScoreOnce.JSON,
                new LlmEvaluationValidator());
        var records = new ArrayList<Map<String, Object>>();
        var failures = new ArrayList<Map<String, Object>>();
        var levelCounts = new TreeMap<String, Integer>();
        int wireVerified = 0, jsonParsed = 0, nativeValidated = 0, dimensionsValidated = 0, usagePresent = 0;
        var perEpoch = new LinkedHashMap<String, Integer>();
        for (String epoch : List.of("05", "06")) {
            Path evidence = root.resolve("docs/evaluation/judge/PHASE_5B_EPOCH_" + epoch + "_RUN_EVIDENCE.json");
            JsonNode run = JevScoreOnce.CAPTURE_JSON.readTree(Files.readAllBytes(evidence));
            int count = 0;
            for (JsonNode event : run.path("events")) {
                if (!"JEV".equals(event.path("provider").asText()) || event.path("httpStatus").asInt() != 200) continue;
                count++;
                var record = new LinkedHashMap<String, Object>();
                record.put("epoch", epoch);
                record.put("caseId", event.path("caseId").asText());
                try {
                    require("api.typesafe.ai".equals(event.path("host").asText())
                            && "/v1/systemone".equals(event.path("path").asText()));
                    Path file = Path.of(event.path("responseFile").asText());
                    if (!file.isAbsolute()) file = root.resolve(file);
                    file = file.normalize();
                    require(file.startsWith(root));
                    byte[] bytes = Files.readAllBytes(file);
                    String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                    require(sha.equals(event.path("responseSha256").asText()));
                    wireVerified++;
                    record.put("responseFile", root.relativize(file).toString());
                    record.put("responseSha256", sha);
                    var request = JevScoreOnce.CAPTURE_JSON.treeToValue(event.path("requestBody"), JevRequest.class);
                    var response = client(bytes).evaluate(request);
                    jsonParsed++;
                    JsonNode raw = JevScoreOnce.CAPTURE_JSON.readTree(bytes);
                    require("jev-1.13.0".equals(response.model()));
                    require(JevScoreOnce.JSON.valueToTree(response.answers()).equals(
                            JevScoreOnce.JSON.valueToTree(JevScoreOnce.JSON.treeToValue(raw, JevResponse.class).answers())));
                    validate.invoke(provider, request, response);
                    nativeValidated++;
                    var scoreNames = new TreeSet<String>();
                    var mapped = new LinkedHashMap<String, Integer>();
                    for (var entry : request.questions().entrySet()) {
                        if (!"score".equals(entry.getValue().type())) continue;
                        scoreNames.add(entry.getKey());
                        int levels = ((List<?>) entry.getValue().criteria()).size();
                        levelCounts.merge(Integer.toString(levels), 1, Integer::sum);
                        // Exactly the original criteria; never adapt a five-level response to three levels.
                        JevScoreOnce.validateScore(entry.getValue(), response.answers().get(entry.getKey()));
                        int actual = (Integer) mapScore.invoke(null, response.answers().get(entry.getKey()).score());
                        int expected = BigDecimal.valueOf(response.answers().get(entry.getKey()).score())
                                .multiply(BigDecimal.valueOf(5)).setScale(0, RoundingMode.HALF_UP).intValueExact();
                        require(actual == expected && actual >= 0 && actual <= 20);
                        mapped.put(entry.getKey(), actual);
                        dimensionsValidated++;
                    }
                    require(scoreNames.equals(new TreeSet<>(DIMENSIONS)));
                    var usage = raw.path("usage");
                    boolean validUsage = usage.path("input_tokens").isIntegralNumber()
                            && usage.path("input_tokens").canConvertToLong() && usage.path("input_tokens").longValue() >= 0
                            && usage.path("output_tokens").isIntegralNumber()
                            && usage.path("output_tokens").canConvertToLong() && usage.path("output_tokens").longValue() >= 0;
                    if (validUsage) usagePresent++;
                    record.put("mappedFiveDimensionScores", mapped);
                    record.put("usagePresentAndIntegral", validUsage);
                    record.put("status", "PASS_ORIGINAL_REQUEST_PROTOCOL");
                    records.add(record);
                } catch (Exception failure) {
                    record.put("errorType", failure instanceof InvocationTargetException reflected
                            ? reflected.getCause().getClass().getSimpleName() : failure.getClass().getSimpleName());
                    failures.add(record);
                }
            }
            perEpoch.put(epoch, count);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("status", failures.isEmpty() && records.size() == 76 ? "PASS" : "FAIL");
        result.put("scope", "Original E05/E06 five-dimensional Score, Choice, JSON and Java mapping; no feedback quality replay");
        result.put("casesByEpoch", perEpoch);
        result.put("wireHashVerified", wireVerified);
        result.put("productionClientJsonParsed", jsonParsed);
        result.put("productionJudgeNativeValidated", nativeValidated);
        result.put("scoreDimensionsValidated", dimensionsValidated);
        result.put("originalScoreCriteriaSizeHistogram", levelCounts);
        result.put("matchingThreeLevelRealResponses", levelCounts.getOrDefault("3", 0));
        result.put("historicalUsagePresent", usagePresent);
        result.put("failures", failures);
        result.put("records", records);
        result.put("newRealModelCalls", 0);
        Files.writeString(Path.of(args[1]), JevScoreOnce.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        result.remove("records");
        System.out.println(JevScoreOnce.JSON.writeValueAsString(result));
        if (!"PASS".equals(result.get("status"))) System.exit(1);
    }
}
