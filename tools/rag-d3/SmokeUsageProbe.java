import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/** Verifies that Spring can read response bytes repeatedly while usage is captured once. */
public final class SmokeUsageProbe {
    public static void main(String[] args) throws Exception {
        byte[] json = """
                {"model":"deepseek-flash","usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}
                """.getBytes(StandardCharsets.UTF_8);
        var underlying = new MockClientHttpResponse(json, HttpStatus.OK);
        var usage = new ArrayList<Map<String, Object>>();
        var response = new D3SmokeServer.UsageResponse(underlying, 1, usage::add);
        D3Corpus.require(Arrays.equals(json, response.getBody().readAllBytes()), "FIRST_READ");
        D3Corpus.require(Arrays.equals(json, response.getBody().readAllBytes()), "REPEATED_READ");
        D3Corpus.require(usage.size() == 1 && usage.getFirst().get("total_tokens").equals(5L), "USAGE_CAPTURE");
        response.close();
        System.out.println("Repeated response reads preserve identical bytes and emit one usage record.");
    }
}
