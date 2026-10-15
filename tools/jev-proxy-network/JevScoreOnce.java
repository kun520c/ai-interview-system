import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.kun.aiinterview.interview.evaluation.judge.JudgeConfiguration;
import com.kun.aiinterview.interview.evaluation.judge.jev.*;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** A standalone production JevClient call. No Spring application, persistence or fallback provider. */
public class JevScoreOnce {
    static final String AUTHORIZATION = "AUTHORIZE_JEV_PROXY_ONCE_MAX_USD_0_01";
    static final URI OFFICIAL_BASE = URI.create("https://api.typesafe.ai");
    static final ObjectMapper JSON = new ObjectMapper();
    static final ObjectMapper CAPTURE_JSON = JSON.copy()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION, true);
    static final int RESPONSE_BYTE_CAP = 65536;
    static final BigDecimal BUDGET_USD = new BigDecimal("0.01");
    static final JevRequest REQUEST = new JevRequest("jev-latest",
            "Question: What does Java volatile provide? Answer: Visibility and ordering, but not atomicity for increment.",
            Map.of("accuracy", new JevRequest.Question("score", "Rate only the factual correctness of this answer.",
                    List.of("incorrect", "partly correct", "correct"))));

    static BigDecimal validateAuthorization(String authorization, boolean providerAccessConfirmed,
                                            boolean pricingConfirmed, String country, BigDecimal price) {
        if (!AUTHORIZATION.equals(authorization) || !providerAccessConfirmed || !pricingConfirmed
                || country == null || !country.matches("[A-Z]{2}")) {
            throw new SecurityException("NEW_HUMAN_AUTHORIZATION_AND_PROVIDER_ACCESS_AND_PRICING_REQUIRED");
        }
        // Published Jev 1.13 maximum input context. This is a planning bound, not a provider invoice cap.
        var bound = price == null ? BigDecimal.ZERO : price.multiply(new BigDecimal("0.065536"));
        if (price == null || price.signum() <= 0 || bound.compareTo(BUDGET_USD) > 0) {
            throw new SecurityException("CONFIRMED_PRICE_EXCEEDS_USD_0_01_PLANNING_BUDGET");
        }
        return bound;
    }

    static final class OnceGuard {
        final Path directory;
        final URI base;
        final AtomicInteger attempts = new AtomicInteger();

        OnceGuard(Path directory, URI base) {
            this.directory = directory;
            this.base = base;
        }

        void reserve() throws Exception {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("EXECUTION_RESERVED.lock"), Instant.now().toString(),
                    StandardOpenOption.CREATE_NEW);
        }

        void admit(String method, URI uri, byte[] body) throws java.io.IOException {
            if (!"POST".equals(method) || !base.resolve("/v1/systemone").equals(uri) || body.length > 1024
                    || !attempts.compareAndSet(0, 1)) {
                throw new SecurityException("ONLY_ONE_FIXED_JEV_POST_ALLOWED");
            }
            Files.writeString(directory.resolve("POST_CONSUMED.lock"), Instant.now().toString(),
                    StandardOpenOption.CREATE_NEW);
        }
    }

    static JevProperties properties(String key) {
        var properties = new JevProperties();
        properties.setApiKey(key);
        properties.setBaseUrl(OFFICIAL_BASE);
        properties.setConnectTimeout(Duration.ofSeconds(5));
        properties.setReadTimeout(Duration.ofSeconds(20));
        properties.getProxy().setEnabled(true);
        properties.getProxy().setHost("127.0.0.1");
        properties.getProxy().setPort(7897);
        return properties;
    }

    static JevClient client(JevProperties properties, OnceGuard guard, Map<String, Object> result) {
        var builder = RestClient.builder().requestInterceptor((request, body, execution) -> {
            guard.admit(request.getMethod().name(), request.getURI(), body);
            result.put("jevPostAttempts", 1);
            var response = execution.execute(request, body);
            result.put("httpStatus", response.getStatusCode().value());
            Files.writeString(guard.directory.resolve("http-status.json"), JSON.writeValueAsString(
                    Map.of("httpStatus", response.getStatusCode().value(), "receivedUtc", Instant.now().toString())),
                    StandardOpenOption.CREATE_NEW);
            if (response.getStatusCode().value() != 200) {
                result.put("responseCaptureStatus", "HTTP_ERROR_BODY_NOT_SAVED");
                return response;
            }
            byte[] responseBody;
            try {
                responseBody = response.getBody().readNBytes(RESPONSE_BYTE_CAP + 1);
                if (responseBody.length > RESPONSE_BYTE_CAP) throw new IOException("RESPONSE_BYTE_CAP");
                captureResponse(responseBody, properties.getApiKey(), guard.directory, result);
            } catch (IOException failure) {
                response.close();
                result.put("responseCaptureStatus", "CAPTURE_OR_BODY_READ_FAILED");
                throw failure;
            }
            var bufferedBody = new ByteArrayInputStream(responseBody);
            return new ClientHttpResponse() {
                public HttpStatusCode getStatusCode() throws IOException { return response.getStatusCode(); }
                public String getStatusText() throws IOException { return response.getStatusText(); }
                public HttpHeaders getHeaders() { return response.getHeaders(); }
                public java.io.InputStream getBody() { return bufferedBody; }
                public void close() { response.close(); }
            };
        });
        return new JudgeConfiguration().jevClient(builder, properties, JSON);
    }

    static void validateResponse(JevResponse response) {
        if (response == null || response.answers() == null
                || !response.answers().keySet().equals(REQUEST.questions().keySet())) {
            throw new IllegalStateException("SCORE_RESPONSE_INVALID");
        }
        validateScore(REQUEST.questions().get("accuracy"), response.answers().get("accuracy"));
    }

    static void validateScore(JevRequest.Question question, JevResponse.Answer answer) {
        var levels = (List<?>) question.criteria();
        int topLevel = levels.size() - 1;
        if (!"score".equals(question.type()) || topLevel < 1 || answer == null
                || !"score".equals(answer.type()) || answer.score() == null || !Double.isFinite(answer.score())
                || answer.score() < 0 || answer.score() > topLevel || answer.confidence() == null
                || !Double.isFinite(answer.confidence()) || answer.confidence() < 0 || answer.confidence() > 1
                || answer.legend() == null || answer.probabilities() == null
                || answer.legend().size() != levels.size() || answer.probabilities().size() != levels.size()) {
            throw new IllegalStateException("SCORE_RESPONSE_INVALID");
        }
        for (int index = 0; index < levels.size(); index++) {
            String key = Integer.toString(index);
            var probability = answer.probabilities().get(key);
            if (!JSON.valueToTree(levels.get(index)).equals(answer.legend().get(key))
                    || probability == null || !Double.isFinite(probability) || probability < 0 || probability > 1) {
                throw new IllegalStateException("SCORE_RESPONSE_INVALID");
            }
        }
        // Display rounding/expectation validation remains the production judge's responsibility.
    }

    static void captureResponse(byte[] body, String key, Path directory, Map<String, Object> result) throws IOException {
        var capture = new LinkedHashMap<String, Object>();
        capture.put("httpStatus", 200);
        capture.put("responseBytes", body.length);
        try {
            capture.put("responseSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)));
            JsonNode root = CAPTURE_JSON.readTree(body);
            var usage = root == null ? null : root.get("usage");
            boolean usageValid = usage != null && usage.isObject();
            var safeUsage = new LinkedHashMap<String, Long>();
            for (String field : List.of("input_tokens", "output_tokens")) {
                var value = usage == null ? null : usage.get(field);
                if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
                    usageValid = false;
                } else safeUsage.put(field, value.longValue());
            }
            var total = usage == null ? null : usage.get("total_tokens");
            if (total != null) {
                if (!total.isIntegralNumber() || !total.canConvertToLong() || total.longValue() < 0) usageValid = false;
                else safeUsage.put("total_tokens", total.longValue());
            }
            capture.put("usageStatus", usage == null ? "MISSING" : usageValid ? "VALID" : "INVALID");
            if (usageValid) capture.put("usage", safeUsage);
            var parsed = CAPTURE_JSON.treeToValue(root, JevResponse.class);
            validateResponse(parsed);
            if (parsed.model() == null || !parsed.model().matches("jev-[A-Za-z0-9.-]{1,90}")) {
                throw new IllegalStateException("MODEL_INVALID");
            }
            var safeResponse = new LinkedHashMap<String, Object>();
            safeResponse.put("model", parsed.model());
            // Validated Score fields only: no provider strings, unknown fields, state or headers.
            var answer = parsed.answers().get("accuracy");
            safeResponse.put("answers", Map.of("accuracy", Map.of("type", "score", "score", answer.score(),
                    "confidence", answer.confidence(), "legend", answer.legend(), "probabilities", answer.probabilities())));
            if (usageValid) safeResponse.put("usage", safeUsage);
            String safeJson = JSON.writeValueAsString(safeResponse);
            if (key != null && !key.isBlank() && safeJson.contains(key)) {
                throw new IllegalStateException("SECRET_ECHO_REJECTED");
            }
            capture.put("validatedResponse", safeResponse);
            capture.put("scoringProtocolStatus", "RANGE_AND_FIELDS_VALIDATED");
        } catch (Exception failure) {
            capture.put("scoringProtocolStatus", "INVALID_OR_UNPARSEABLE");
            // No messages, response strings, error bodies or headers are saved.
        }
        Files.writeString(directory.resolve("response-capture.json"), JSON.writerWithDefaultPrettyPrinter()
                .writeValueAsString(capture), StandardOpenOption.CREATE_NEW);
        result.put("responseCaptureFile", "response-capture.json");
        result.put("responseCaptureStatus", capture.get("scoringProtocolStatus"));
        result.put("usageStatus", capture.getOrDefault("usageStatus", "UNPARSEABLE"));
        if (capture.containsKey("usage")) result.put("usage", capture.get("usage"));
    }

    public static void main(String[] args) {
        Map<String, Object> result = new LinkedHashMap<>();
        Path output = Path.of(args[0]);
        result.put("jevPostAttempts", 0);
        result.put("deepSeekCalls", 0);
        result.put("embeddingCalls", 0);
        result.put("milvusCalls", 0);
        result.put("mysqlWrites", 0);
        result.put("retryCount", 0);
        result.put("budgetUsd", BUDGET_USD);
        result.put("providerInvoiceHardCap", false);
        result.put("startedUtc", Instant.now().toString());
        boolean reserved = false;
        boolean succeeded = false;
        try {
            var bound = validateAuthorization(args[1], Boolean.parseBoolean(args[2]), Boolean.parseBoolean(args[3]),
                    args[4], new BigDecimal(args[5]));
            if (!"true".equals(System.getProperty("jdk.httpclient.disableRetryConnect"))
                    || !"false".equals(System.getProperty("jdk.httpclient.enableAllMethodRetry"))
                    || System.getProperty("jdk.httpclient.HttpClient.log") != null
                    || System.getProperty("javax.net.debug") != null) {
                throw new SecurityException("NO_RETRY_AND_NO_SENSITIVE_HTTP_LOGGING_REQUIRED");
            }
            String key = System.getenv("JEV_API_KEY");
            if (key == null || key.isBlank()) throw new SecurityException("JEV_API_KEY_REQUIRED_IN_CHILD_ENVIRONMENT");
            var properties = properties(key);
            var guard = new OnceGuard(output, OFFICIAL_BASE);
            guard.reserve();
            reserved = true;
            result.put("documentedMaximumInputCostBoundUsd", bound);
            result.put("providerAccessConfirmedByHuman", true);
            result.put("confirmedCountry", args[4]);
            try (var transport = JevClient.createHttpClient(properties)) {
                var selected = transport.proxy().orElseThrow().select(OFFICIAL_BASE);
                if (selected.size() != 1 || selected.getFirst().type() != Proxy.Type.HTTP) {
                    throw new SecurityException("EXPLICIT_HTTP_PROXY_REQUIRED");
                }
                var trace = JevNetworkDiagnostic.get(transport, JevNetworkDiagnostic.TRACE, new HashMap<>());
                result.put("freePreflight", trace);
                if (!Integer.valueOf(200).equals(trace.get("httpStatus")) || !args[4].equals(trace.get("country"))) {
                    throw new SecurityException("PROXY_COUNTRY_NOT_EQUAL_TO_HUMAN_CONFIRMED_ALLOWED_COUNTRY");
                }
            }
            var response = client(properties, guard, result).evaluate(REQUEST);
            if (!"RANGE_AND_FIELDS_VALIDATED".equals(result.get("responseCaptureStatus"))) {
                throw new IllegalStateException("SAFE_RESPONSE_CAPTURE_REQUIRED");
            }
            validateResponse(response);
            result.put("model", response.model());
            result.put("accuracyScore", response.answers().get("accuracy").score());
            result.put("confidence", response.answers().get("accuracy").confidence());
            result.put("scoreScaleMaximum", ((List<?>) REQUEST.questions().get("accuracy").criteria()).size() - 1);
            result.put("status", "SCORE_RESPONSE_VERIFIED");
            succeeded = true;
        } catch (Exception failure) {
            result.put("status", "STOPPED_NO_RETRY");
            // Never output exception messages/causes, headers, API keys or provider bodies.
            result.put("errorType", failure.getClass().getSimpleName());
            if (Integer.valueOf(451).equals(result.get("httpStatus"))) result.put("providerRegionAccess", "BLOCKED");
        } finally {
            if (reserved) {
                try {
                    Files.writeString(output.resolve("result.json"), JSON.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(result), StandardOpenOption.CREATE_NEW);
                } catch (Exception failure) {
                    result.put("resultPersistence", "FAILED_PRESERVE_LOCKS");
                    succeeded = false;
                }
            }
            try { System.out.println(JSON.writeValueAsString(result)); } catch (Exception ignored) { }
        }
        if (!succeeded) System.exit(1);
    }
}
