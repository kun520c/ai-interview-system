package com.kun.aiinterview.interview.evaluation.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.common.exception.ExternalServiceException;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalResult;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FollowUpOutputCompletenessHardeningTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sendsAllMainConstraintButNeverFillsMissingModelResults(boolean complete) throws Exception {
        var mapper = new ObjectMapper();
        var context = new EvaluationContext(4L, 3L, 2L, 1L, EvaluationMode.FOLLOW_UP_ANSWER,
                QuestionCategory.JAVA_BASIC, "offline-followup", "本地题目", "本地参考", List.of(
                new ScoringPointSnapshot(101L, QuestionPointType.CORE, "点一", 25),
                new ScoringPointSnapshot(102L, QuestionPointType.KEY, "点二", 25),
                new ScoringPointSnapshot(103L, QuestionPointType.KEY, "点三", 25),
                new ScoringPointSnapshot(104L, QuestionPointType.PRINCIPLE, "点四", 25)),
                "主回答", "追问", "追问回答", List.of(103L, 104L));
        var prompt = new EvaluationPromptBuilder().build(context, new EvaluationRetrievalResult(
                "本地查询", 5, "offline-embedding", "offline-version", List.of(), List.of()));
        var builder = RestClient.builder().baseUrl("http://localhost");
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = new DeepSeekProperties();
        properties.setModel("deepseek-flash");
        var client = new DeepSeekEvaluationClient(builder.build(), properties, mapper);
        var inner = mapper.createObjectNode();
        for (String field : List.of("correctnessScore", "completenessScore", "depthScore", "clarityScore", "practiceScore")) {
            inner.put(field, 10);
        }
        inner.putArray("strengths");inner.putArray("missingPoints");inner.putNull("correction");
        inner.put("followUpRecommended", false);inner.putNull("suggestedFollowUp");
        var results = inner.putArray("scoringPointResults");
        for (long id : complete ? List.of(101L, 102L, 103L, 104L) : List.of(103L, 104L)) {
            results.addObject().put("scoringPointId", id).put("covered", true).put("evidence", "本地回答依据");
        }
        var outer = mapper.createObjectNode().put("model", "deepseek-flash");
        outer.putArray("choices").addObject().put("index", 0).put("finish_reason", "stop")
                .putObject("message").put("role", "assistant").put("content", inner.toString());
        server.expect(requestTo("http://localhost/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content").value(containsString("完整MAIN评分点ID列表：[101, 102, 103, 104]")))
                .andExpect(jsonPath("$.messages[0].content").value(containsString("禁止只输出targets")))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.thinking.type").value("disabled"))
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andRespond(withSuccess(outer.toString(), MediaType.APPLICATION_JSON));
        var result = client.evaluate(prompt);
        assertThat(result.rawJson()).isEqualTo(inner.toString());
        assertThat(result.suggestion().scoringPointResults()).hasSize(complete ? 4 : 2);
        if (complete) {
            var valid = new LlmEvaluationValidator().validate(context, result.suggestion());
            assertThat(valid.followUpRecommended()).isFalse();
            assertThat(valid.scoringPointResults()).extracting(x -> x.scoringPointId())
                    .containsExactly(101L, 102L, 103L, 104L);
        } else {
            assertThatThrownBy(() -> new LlmEvaluationValidator().validate(context, result.suggestion()))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessage("scoringPointResults数量与MAIN评分点数量不一致");
        }
        server.verify();
    }
}
