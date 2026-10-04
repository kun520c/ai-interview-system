package com.kun.aiinterview.interview.evaluation.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekJudgeProviderTest {
    @Test
    void preservesOriginalPromptJsonScoresAndMetadataThroughRealClient() throws Exception {
        var mapper = new ObjectMapper();
        var builder = RestClient.builder().baseUrl("http://localhost");
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = new DeepSeekProperties();
        properties.setModel("test-deepseek");
        properties.setMaxTokens(4096);
        var provider = new DeepSeekJudgeProvider(new DeepSeekEvaluationClient(builder.build(), properties, mapper));
        var prompt = new EvaluationPrompt("evaluation-prompt-v1", "old system prompt", "old user prompt");
        String raw = """
                {"correctnessScore":20,"completenessScore":19,"depthScore":18,"clarityScore":17,
                "practiceScore":16,"strengths":[],"missingPoints":[],"correction":null,
                "scoringPointResults":[],"followUpRecommended":false,"suggestedFollowUp":null}
                """;
        String response = """
                {"model":"test-deepseek","choices":[{"index":0,"finish_reason":"stop",
                "message":{"role":"assistant","content":%s}}]}
                """.formatted(mapper.writeValueAsString(raw));
        server.expect(requestTo("http://localhost/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content").value(prompt.systemPrompt()))
                .andExpect(jsonPath("$.messages[1].content").value(prompt.userPrompt()))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        var result = provider.evaluate(null, prompt);
        assertThat(result.model()).isEqualTo("test-deepseek");
        assertThat(result.promptVersion()).isEqualTo(prompt.promptVersion());
        assertThat(result.rawJson()).isEqualTo(raw);
        assertThat(result.suggestion().correctnessScore()).isEqualTo(20);
        assertThat(result.suggestion().practiceScore()).isEqualTo(16);
        server.verify();
    }
}
