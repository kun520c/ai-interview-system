package com.kun.aiinterview.interview.evaluation.judge.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.judge.DeepSeekJudgeProvider;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import com.kun.aiinterview.interview.evaluation.score.EvaluationScoreCalculator;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Reads the frozen archive, intercepts EVERY HTTP call in memory, never changes historical evidence. */
class JevE06ReliabilityReplayTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> INVALID = Set.of("E02-EXCELLENT", "E02-OFF_TOPIC", "H05-BOUNDARY",
            "E02-REPAIR", "H09-FAILED_REPAIR");
    private static JsonNode archive;

    @BeforeAll
    static void readFrozenArchive() throws Exception {
        archive = MAPPER.readTree(Path.of("docs/evaluation/judge/PHASE_5B_EPOCH_06_RUN_EVIDENCE.json").toFile());
        assertThat(archive.path("frozenInputs")).hasSize(38);
        assertThat(archive.path("events")).hasSize(114);
    }

    @ParameterizedTest
    @CsvSource({"0,false,33,38,5", "1,false,33,43,5", "1,true,38,43,0"})
    void comparesAll38CasesWithoutPretendingSyntheticRecoveryIsHistoricalQuality(
            int retries, boolean syntheticRecovery, int expectedPrimary, int expectedFeedback, int expectedFallback) throws Exception {
        int primary = 0;
        int valid = 0;
        var visited = new HashSet<String>();
        for (var frozen : archive.path("frozenInputs")) {
            String id = frozen.path("caseId").asText();
            assertThat(visited.add(id)).isTrue();
            var context = MAPPER.treeToValue(frozen.path("context"), EvaluationContext.class);
            var prompt = MAPPER.treeToValue(frozen.path("prompt"), EvaluationPrompt.class);
            var nativeBody = event(id, "JEV", "FEEDBACK").path("responseBody");
            var feedbackBody = event(id, "DEEPSEEK", "FEEDBACK").path("responseBody");
            var baselineBody = event(id, "DEEPSEEK", "BASELINE").path("responseBody");
            var nativeBuilder = RestClient.builder().baseUrl("http://jev.offline");
            var nativeServer = MockRestServiceServer.bindTo(nativeBuilder).build();
            nativeServer.expect(requestTo("http://jev.offline/v1/systemone"))
                    .andRespond(withSuccess(nativeBody.toString(), MediaType.APPLICATION_JSON));
            var feedbackBuilder = RestClient.builder().baseUrl("http://deepseek.offline");
            var feedbackServer = MockRestServiceServer.bindTo(feedbackBuilder).build();
            expectFeedback(feedbackServer, feedbackBody);
            boolean invalid = INVALID.contains(id);
            if (invalid && retries > 0) {
                var retryBody = feedbackBody.deepCopy();
                if (syntheticRecovery) {
                    ((ObjectNode) retryBody.at("/choices/0/message")).put("content",
                            JevFeedbackReliabilityTest.structuralFeedback(MAPPER, nativeBody, context).toString());
                }
                expectFeedback(feedbackServer, retryBody);
            }
            if (invalid && !(retries > 0 && syntheticRecovery)) expectFeedback(feedbackServer, baselineBody);
            var deepSeekProperties = new DeepSeekProperties();
            deepSeekProperties.setBaseUrl(URI.create("http://deepseek.offline"));
            var generation = new DeepSeekEvaluationClient(feedbackBuilder.build(), deepSeekProperties, MAPPER);
            var properties = new JevProperties();
            properties.setFeedbackMaxRetries(retries);
            var validator = new LlmEvaluationValidator();
            var provider = new JevJudgeProvider(new JevClient(nativeBuilder.build(), MAPPER), properties,
                    generation, new DeepSeekJudgeProvider(generation), MAPPER, validator);

            var result = provider.evaluate(context, prompt);
            var validated = validator.validate(context, result.suggestion());
            new EvaluationScoreCalculator().calculate(validated);
            valid++;
            if (result.model().startsWith("jev")) primary++;
            nativeServer.verify();
            feedbackServer.verify();
        }
        assertThat(primary).isEqualTo(expectedPrimary);
        assertThat(valid).isEqualTo(38);
        int feedbackCalls = 38 + (retries > 0 ? INVALID.size() : 0);
        assertThat(feedbackCalls).isEqualTo(expectedFeedback);
        assertThat(38 - primary).isEqualTo(expectedFallback);
        var summary = MAPPER.createObjectNode().put("offlineOnly", true).put("syntheticRecovery", syntheticRecovery)
                .put("feedbackMaxRetries", retries).put("cases", valid).put("primaryValidated", primary)
                .put("mixedValidated", valid).put("nativeCalls", 38).put("feedbackCalls", feedbackCalls)
                .put("fallbackCalls", 38 - primary).put("totalClientCalls", 38 + feedbackCalls + 38 - primary)
                .put("realPaidCalls", 0).put("realModelRecoveryRate", "UNVERIFIED")
                .put("realLatencyAndChargeAfterChange", "UNVERIFIED");
        Path output = Path.of("target/value-repair/replay-" + retries + "-" + syntheticRecovery + ".json");
        Files.createDirectories(output.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), summary);
    }

    private static JsonNode event(String id, String provider, String purpose) {
        for (var event : archive.path("events")) {
            if (id.equals(event.path("caseId").asText()) && provider.equals(event.path("provider").asText())
                    && purpose.equals(event.path("purpose").asText())) return event;
        }
        throw new AssertionError("Missing frozen event: " + id + "/" + provider + "/" + purpose);
    }

    private static void expectFeedback(MockRestServiceServer server, JsonNode body) {
        server.expect(requestTo("http://deepseek.offline/chat/completions"))
                .andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));
    }
}
