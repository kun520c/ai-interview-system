import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.interview.evaluation.judge.jev.JevClient;
import com.kun.aiinterview.interview.evaluation.judge.jev.JevResponse;
import com.sun.net.httpserver.HttpServer;

import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic local fixtures only; the real runner main and public preflight are never invoked. */
public class JevScoreOnceOfflineTest {
    interface Checked { void run() throws Exception; }
    static int passed;
    static final List<String> failures = new ArrayList<>();
    static Path fixtures;

    static void check(boolean value) { if (!value) throw new AssertionError(); }
    static void rejects(Checked action) throws Exception {
        try { action.run(); } catch (SecurityException | FileAlreadyExistsException expected) { return; }
        throw new AssertionError();
    }
    static void test(String name, Checked action) {
        try { action.run(); passed++; } catch (Throwable failure) { failures.add(name + ":" + failure.getClass().getSimpleName()); }
    }
    static JevScoreOnce.OnceGuard guard(String name, URI base) throws Exception {
        var guard = new JevScoreOnce.OnceGuard(fixtures.resolve(name), base);
        guard.reserve();
        return guard;
    }
    static JevResponse scoreResponse(double score) {
        return new JevResponse("jev-offline", Map.of("accuracy", new JevResponse.Answer("score", score, null,
                Map.of("0", 0.0, "1", 0.2, "2", 0.8), 0.8,
                Map.of("0", JevScoreOnce.JSON.valueToTree("incorrect"),
                        "1", JevScoreOnce.JSON.valueToTree("partly correct"),
                        "2", JevScoreOnce.JSON.valueToTree("correct")))));
    }
    static String successBody() {
        var root = JevScoreOnce.JSON.valueToTree(scoreResponse(1.8));
        ((com.fasterxml.jackson.databind.node.ObjectNode) root).putObject("usage")
                .put("input_tokens", 100).put("output_tokens", 18);
        return root.toString();
    }
    static void httpScenario(int status) throws Exception {
        var proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var counts = new AtomicInteger();
        proxy.createContext("/", exchange -> {
            counts.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            String json = status == 200
                    ? successBody()
                    : "{\"title\":\"private-region-body-or-key\",\"status\":451}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (status == 302) exchange.getResponseHeaders().set("Location", "http://127.0.0.1:1/never");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        proxy.start();
        try {
            var properties = JevScoreOnce.properties("offline-placeholder-key");
            properties.getProxy().setPort(proxy.getAddress().getPort());
            // Fixed real endpoint is never contacted: the local HTTP proxy responds without forwarding.
            properties.setBaseUrl(URI.create("http://jev-offline.invalid"));
            var guard = guard("http-" + status, properties.getBaseUrl());
            var result = new LinkedHashMap<String, Object>();
            var client = JevScoreOnce.client(properties, guard, result);
            if (status == 200) {
                JevScoreOnce.validateResponse(client.evaluate(JevScoreOnce.REQUEST));
            } else {
                try { client.evaluate(JevScoreOnce.REQUEST); throw new AssertionError(); }
                catch (ExternalServiceException expected) { check(expected.getCause() == null); }
            }
            check(Integer.valueOf(status).equals(result.get("httpStatus")));
            check(Integer.valueOf(1).equals(result.get("jevPostAttempts")));
            check(Files.exists(guard.directory.resolve("http-status.json")));
            if (status == 200) {
                var captured = JevScoreOnce.JSON.readTree(Files.readString(guard.directory.resolve("response-capture.json")));
                check(captured.at("/validatedResponse/answers/accuracy/score").doubleValue() == 1.8);
                check(captured.at("/usage/input_tokens").longValue() == 100);
                check(captured.at("/usage/output_tokens").longValue() == 18);
                check("VALID".equals(result.get("usageStatus")));
                check(!captured.toString().contains("offline-placeholder-key"));
            } else check(!Files.exists(guard.directory.resolve("response-capture.json")));
            rejects(() -> client.evaluate(JevScoreOnce.REQUEST));
            check(counts.get() == 1);
            check(Files.exists(guard.directory.resolve("POST_CONSUMED.lock")));
            check(!JevScoreOnce.JSON.writeValueAsString(result).contains("private-region-body-or-key"));
        } finally { proxy.stop(0); }
    }

    public static void main(String[] args) throws Exception {
        var output = Path.of(args[0]);
        fixtures = Files.createTempDirectory(output.getParent(), "runner-offline-");
        for (String scenario : List.of("authorization", "provider", "pricing", "country")) {
            test("missing " + scenario + " rejects before resources", () -> rejects(() ->
                    JevScoreOnce.validateAuthorization(scenario.equals("authorization") ? "" : JevScoreOnce.AUTHORIZATION,
                            !scenario.equals("provider"), !scenario.equals("pricing"), scenario.equals("country") ? "" : "SG",
                            new BigDecimal("0.042"))));
        }
        test("confirmed current price fits budget", () -> check(
                JevScoreOnce.validateAuthorization(JevScoreOnce.AUTHORIZATION, true, true, "SG", new BigDecimal("0.042"))
                        .compareTo(new BigDecimal("0.01")) < 0));
        test("over budget rejects", () -> rejects(() -> JevScoreOnce.validateAuthorization(
                JevScoreOnce.AUTHORIZATION, true, true, "SG", new BigDecimal("1"))));
        test("nonpositive price rejects", () -> rejects(() -> JevScoreOnce.validateAuthorization(
                JevScoreOnce.AUTHORIZATION, true, true, "SG", BigDecimal.ZERO)));
        test("independent reservation cannot be reused", () -> {
            var guard = guard("reservation", JevScoreOnce.OFFICIAL_BASE);
            rejects(guard::reserve);
            check(guard.attempts.get() == 0);
        });
        test("only one POST can consume the lock", () -> {
            var guard = guard("admission", JevScoreOnce.OFFICIAL_BASE);
            var uri = JevScoreOnce.OFFICIAL_BASE.resolve("/v1/systemone");
            guard.admit("POST", uri, new byte[100]);
            rejects(() -> guard.admit("POST", uri, new byte[100]));
            check(guard.attempts.get() == 1);
        });
        test("wrong endpoint, method and oversized body cannot consume a POST", () -> {
            var guard = guard("contract", JevScoreOnce.OFFICIAL_BASE);
            rejects(() -> guard.admit("GET", JevScoreOnce.OFFICIAL_BASE.resolve("/v1/systemone"), new byte[1]));
            rejects(() -> guard.admit("POST", URI.create("https://api.deepseek.com/v1/systemone"), new byte[1]));
            rejects(() -> guard.admit("POST", JevScoreOnce.OFFICIAL_BASE.resolve("/v1/systemone"), new byte[1025]));
            check(guard.attempts.get() == 0);
        });
        test("production client success; duplicate blocked before transport", () -> httpScenario(200));
        test("HTTP451 sanitized; no retry or fallback", () -> httpScenario(451));
        test("HTTP302 never follows redirect", () -> httpScenario(302));
        test("failed proxy retains consumed lock without reaching origin", () -> {
            var originCalls = new AtomicInteger();
            var origin = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            origin.createContext("/", exchange -> { originCalls.incrementAndGet(); exchange.close(); });
            origin.start();
            try (var unavailableProxy = new Socket()) {
                unavailableProxy.bind(new InetSocketAddress("127.0.0.1", 0));
                var properties = JevScoreOnce.properties("offline-placeholder-key");
                properties.getProxy().setPort(unavailableProxy.getLocalPort());
                properties.setBaseUrl(URI.create("http://127.0.0.1:" + origin.getAddress().getPort()));
                var guard = guard("failed-proxy", properties.getBaseUrl());
                var result = new LinkedHashMap<String, Object>();
                var client = JevScoreOnce.client(properties, guard, result);
                try { client.evaluate(JevScoreOnce.REQUEST); throw new AssertionError(); }
                catch (ExternalServiceException expected) { check(expected.getCause() == null); }
                rejects(() -> client.evaluate(JevScoreOnce.REQUEST));
                check(originCalls.get() == 0 && guard.attempts.get() == 1);
                check(Files.exists(guard.directory.resolve("POST_CONSUMED.lock")));
            } finally { origin.stop(0); }
        });
        test("official HTTPS selector is fixed even after connect failure", () -> {
            try (var client = JevClient.createHttpClient(JevScoreOnce.properties("offline-placeholder-key"))) {
                var selector = client.proxy().orElseThrow();
                check(selector.select(JevScoreOnce.OFFICIAL_BASE).getFirst().type() == Proxy.Type.HTTP);
                selector.connectFailed(JevScoreOnce.OFFICIAL_BASE, new InetSocketAddress("127.0.0.1", 7897), new java.io.IOException());
                check(selector.select(JevScoreOnce.OFFICIAL_BASE).getFirst().type() == Proxy.Type.HTTP);
            }
        });
        test("three-level Score accepts the documented 0 to 2 range", () -> {
            for (double score : new double[]{0, 1.43, 2}) {
                JevScoreOnce.validateResponse(scoreResponse(score));
            }
        });
        test("three-level Score rejects outside the range and nonfinite values", () -> {
            for (double score : new double[]{-0.01, 2.01, Double.NaN, Double.POSITIVE_INFINITY}) {
                boolean rejected = false;
                try {
                    JevScoreOnce.validateResponse(scoreResponse(score));
                } catch (IllegalStateException expected) { rejected = true; }
                check(rejected);
            }
        });
        test("usage survives scoring failure; provider strings and extra fields are omitted", () -> {
            var root = JevScoreOnce.CAPTURE_JSON.readTree(successBody());
            ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("private", "secret-echo");
            ((com.fasterxml.jackson.databind.node.ObjectNode) root.at("/answers/accuracy")).put("score", 2.1);
            var directory = fixtures.resolve("invalid-score-capture");
            Files.createDirectories(directory);
            JevScoreOnce.captureResponse(root.toString().getBytes(StandardCharsets.UTF_8), "secret-echo", directory,
                    new LinkedHashMap<>());
            var captured = JevScoreOnce.JSON.readTree(Files.readString(directory.resolve("response-capture.json")));
            check(!captured.has("validatedResponse"));
            check(captured.at("/usage/input_tokens").longValue() == 100);
            check(!captured.toString().contains("secret-echo"));
        });
        test("numeric strings and duplicate JSON keys cannot be captured as validated scores", () -> {
            int index = 0;
            for (String body : List.of(successBody().replace("\"score\":1.8", "\"score\":\"1.8\""),
                    successBody().replace("\"score\":1.8", "\"score\":1.8,\"score\":1.9"))) {
                var directory = fixtures.resolve("invalid-json-capture-" + index++);
                Files.createDirectories(directory);
                JevScoreOnce.captureResponse(body.getBytes(StandardCharsets.UTF_8), "secret-echo", directory,
                        new LinkedHashMap<>());
                var captured = JevScoreOnce.JSON.readTree(Files.readString(directory.resolve("response-capture.json")));
                check(!captured.has("validatedResponse"));
            }
        });
        test("missing or invalid usage is explicit and never fabricated", () -> {
            int index = 0;
            for (String tokenValue : List.of("missing", "-1", "\"100\"", "null")) {
                var root = (com.fasterxml.jackson.databind.node.ObjectNode) JevScoreOnce.CAPTURE_JSON.readTree(successBody());
                if (tokenValue.equals("missing")) root.remove("usage");
                else ((com.fasterxml.jackson.databind.node.ObjectNode) root.get("usage")).set("input_tokens",
                        JevScoreOnce.CAPTURE_JSON.readTree(tokenValue));
                var directory = fixtures.resolve("usage-" + index++);
                Files.createDirectories(directory);
                JevScoreOnce.captureResponse(root.toString().getBytes(StandardCharsets.UTF_8), "secret-echo", directory,
                        new LinkedHashMap<>());
                var captured = JevScoreOnce.JSON.readTree(Files.readString(directory.resolve("response-capture.json")));
                check(!captured.has("usage"));
                check(captured.path("usageStatus").asText().equals(tokenValue.equals("missing") ? "MISSING" : "INVALID"));
            }
        });
        var result = Map.of("status", failures.isEmpty() ? "PASS" : "FAIL", "tests", passed + failures.size(),
                "passed", passed, "failed", failures.size(), "errors", 0, "skipped", 0,
                "realJevCalls", 0, "failures", failures);
        String json = JevScoreOnce.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        Files.writeString(output, json);
        System.out.println(json);
        if (!failures.isEmpty()) System.exit(1);
    }
}
