import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/** Exercises the smoke-only response wrapper with one-shot delegate streams. */
public final class SmokeUsageProbe {
    public static void main(String[] args) throws Exception {
        var success = verifyBody(HttpStatus.OK, """
                {"model":"deepseek-flash","usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}
                """);
        D3Corpus.require(success.size() == 1 && Long.valueOf(5).equals(success.getFirst().get("total_tokens")),
                "USAGE_CAPTURE");

        var error = verifyBody(HttpStatus.TOO_MANY_REQUESTS, """
                {"error":{"message":"test error"},"usage":{"total_tokens":5}}
                """);
        D3Corpus.require(error.isEmpty(), "ERROR_USAGE");

        verifyUnknownUsage(verifyBody(HttpStatus.OK, ""), "EMPTY_BODY_USAGE");
        verifyUnknownUsage(verifyBody(HttpStatus.OK, "{\"model\":\"deepseek-flash\"}"), "MISSING_USAGE");
        System.out.println("Smoke usage response probe: 4 passed (success, HTTP error, empty body, missing usage).");
    }

    private static ArrayList<Map<String, Object>> verifyBody(HttpStatus status, String content) throws Exception {
        byte[] expected = content.getBytes(StandardCharsets.UTF_8);
        var underlying = new MockClientHttpResponse(expected, status);
        var usage = new ArrayList<Map<String, Object>>();
        try (var response = new D3SmokeServer.UsageResponse(underlying, 1, usage::add)) {
            D3Corpus.require(response.getStatusCode().equals(status), "HTTP_STATUS");
            D3Corpus.require(Arrays.equals(expected, response.getBody().readAllBytes()), "FIRST_READ");
            D3Corpus.require(Arrays.equals(expected, response.getBody().readAllBytes()), "REPEATED_READ");
        }
        return usage;
    }

    private static void verifyUnknownUsage(ArrayList<Map<String, Object>> usage, String check) {
        D3Corpus.require(usage.size() == 1 && usage.getFirst().containsKey("total_tokens")
                && usage.getFirst().get("total_tokens") == null, check);
    }
}
