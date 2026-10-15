package com.kun.aiinterview.interview.evaluation.judge.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationRetrievalResult;
import com.kun.aiinterview.interview.evaluation.judge.DeepSeekJudgeProvider;
import com.kun.aiinterview.interview.evaluation.llm.DeepSeekEvaluationResult;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekJsonCompletionResult;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPromptBuilder;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Captured E06 outputs stay invalid. Synthetic compliant outputs are only Mock evidence. */
class JevFeedbackPromptHardeningTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode capture(String caseId) throws Exception {
        try (var input = getClass().getResourceAsStream("/evaluation/jev-e06-feedback-captures.json")) {
            for (var row : mapper.readTree(input)) {
                if (row.path("caseId").asText().equals(caseId)) return row;
            }
        }
        throw new AssertionError(caseId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"E02-EXCELLENT", "E02-OFF_TOPIC", "H05-BOUNDARY", "E02-REPAIR", "H09-FAILED_REPAIR"})
    void unchangedCapturedInvalidOutputsStillUseExistingFallbackExactlyOnce(String caseId) throws Exception {
        var row = capture(caseId);
        replay(row, mapper.treeToValue(row.path("context"), EvaluationContext.class),
                row.path("feedbackContent").asText(), false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"E02-EXCELLENT", "E02-OFF_TOPIC", "H05-BOUNDARY", "E02-REPAIR", "H09-FAILED_REPAIR"})
    void separatelyConstructedCompliantMockIncludesAllMainIdsAndQuotesBothSources(String caseId) throws Exception {
        var row = capture(caseId);
        var context = mapper.treeToValue(row.path("context"), EvaluationContext.class);
        replay(row, context, compliantFeedback(row, context).toString(), true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"exact-main", "exact-followup", "emoji", "combining-unicode", "normalized-unicode",
            "exact-spaces", "collapsed-spaces", "changed-punctuation", "joined-answers", "ellipsis",
            "empty", "blank", "null", "escaped-json", "unescaped-json"})
    void exactCandidateSubstringAndStrictJsonRemainRequired(String scenario) throws Exception {
        var row = capture("E02-REPAIR");
        var original = mapper.treeToValue(row.path("context"), EvaluationContext.class);
        var context = new EvaluationContext(original.currentAnswerId(), original.currentInterviewQuestionId(),
                original.mainAnswerId(), original.mainInterviewQuestionId(), original.mode(), original.category(),
                original.knowledgePoint(), original.mainQuestionContent(), original.referenceAnswerSnapshot(),
                original.scoringPoints(), "第一段  原文，中文。😀 e\u0301 \"引号\" C:\\tmp\n下一行。",
                original.followUpQuestionContent(), "追问原文：修复完成。", original.followUpTargetPointIds());
        var feedback = compliantFeedback(row, original);
        for (var point : feedback.withArray("pointEvidence")) point.withObject("").put("evidence", "追问原文：修复完成。");
        String quote = switch (scenario) {
            case "exact-main" -> "原文，中文。";
            case "exact-followup" -> "追问原文：修复完成。";
            case "emoji" -> "😀";
            case "combining-unicode" -> "e\u0301";
            case "normalized-unicode" -> "é";
            case "exact-spaces" -> "第一段  原文";
            case "collapsed-spaces" -> "第一段 原文";
            case "changed-punctuation" -> "原文,中文。";
            case "joined-answers" -> "原文，中文。追问原文：修复完成。";
            case "ellipsis" -> "第一段……下一行。";
            case "empty" -> "";
            case "blank" -> " \t\n";
            case "null" -> null;
            case "escaped-json", "unescaped-json" -> "\"引号\" C:\\tmp\n下一行。";
            default -> throw new AssertionError(scenario);
        };
        var first = feedback.withArray("pointEvidence").get(0).withObject("");
        if (quote == null) first.putNull("evidence"); else first.put("evidence", quote);
        String content = feedback.toString();
        if (scenario.equals("unescaped-json")) content = content.replace("\\\"引号\\\"", "\"引号\"");
        boolean valid = List.of("exact-main", "exact-followup", "emoji", "combining-unicode", "exact-spaces", "escaped-json").contains(scenario);
        replay(row, context, content, valid);
    }

    @ParameterizedTest
    @ValueSource(strings = {"only-targets", "missing-main-covered", "duplicate", "foreign-id"})
    void followUpMustIncludeAllMainIdsWithoutFillingOrChangingNativeCoverage(String scenario) throws Exception {
        var row = capture("E02-REPAIR");
        var context = mapper.treeToValue(row.path("context"), EvaluationContext.class);
        var feedback = compliantFeedback(row, context);
        var points = feedback.withArray("pointEvidence");
        switch (scenario) {
            case "only-targets" -> { points.remove(0); points.remove(0); }
            case "missing-main-covered" -> points.remove(0);
            case "duplicate" -> points.add(points.get(0).deepCopy());
            case "foreign-id" -> points.get(0).withObject("").put("scoringPointId", 999999L);
            default -> throw new AssertionError(scenario);
        }
        replay(row, context, feedback.toString(), false);
    }

    private ObjectNode compliantFeedback(JsonNode row, EvaluationContext context) {
        var feedback = mapper.createObjectNode();
        feedback.putArray("strengths"); feedback.putArray("missingPoints"); feedback.putNull("correction");
        var points = feedback.putArray("pointEvidence");
        for (var point : context.scoringPoints()) {
            long id = point.scoringPointId();
            var value = points.addObject().put("scoringPointId", id);
            boolean covered = row.at("/nativeResponse/answers/point_" + id + "/choice").asText().equals("covered");
            if (!covered) { value.putNull("evidence"); continue; }
            String quote = switch ((int) id) {
                case 2480 -> context.mainAnswerContent().contains("它用能扩容")
                        ? "它用能扩容的数组装引用" : "ArrayList 是一个容量不够会扩容的数组，LinkedList 是节点前后串起来的双向链表。";
                case 2481 -> context.mainAnswerContent().contains("它用能扩容")
                        ? "下标 get 可以直接算位置，是 O(1)。" : "我知道 get(index) 在 ArrayList 上是 O(1)，链表要沿节点找过去，按下标最坏 O(n)。";
                case 2482 -> context.followUpAnswerContent() == null
                        ? "在中间删一个还要挪后面的引用。" : "ArrayList 在中间插入或删除要移动后面的引用";
                case 2483 -> context.followUpAnswerContent() == null
                        ? "还会看节点额外内存和实测耗时，不能只看增删次数。" : "还要看额外节点内存和实测表现，不能光看增删次数。";
                case 2549 -> "核心仍是实际调用要经过代理，this 自调用不会经过。";
                case 2550 -> "默认 RuntimeException 和 Error 回滚，checked exception 要看 rollbackFor。";
                case 2551 -> "应按业务重抛或显式设置 rollback-only。";
                case 2552 -> "一次用例中订单和明细必须同成同败，我会放在同一个事务，把无关查询移出以缩短边界。";
                case 2569 -> "防超卖我会让数据库执行 UPDATE product SET stock=stock-1 WHERE id=? AND stock>0，然后检查影响行数，返回 1 才算抢到，0 就失败。";
                case 2570 -> "同一次业务操作一直携带相同 requestId，数据库处理记录对这个键做唯一约束，重复进来就读已有结果，不再扣库存。";
                default -> throw new AssertionError(id);
            };
            value.put("evidence", quote);
        }
        if (row.at("/nativeResponse/answers/followUp/choice").asText().equals("yes")) {
            feedback.put("suggestedFollowUp", "请具体说明本题所要求机制的适用条件？");
        } else feedback.putNull("suggestedFollowUp");
        return feedback;
    }

    private void replay(JsonNode row, EvaluationContext context, String content, boolean valid) throws Exception {
        var http = RestClient.builder().baseUrl("http://localhost");
        var server = MockRestServiceServer.bindTo(http).build();
        var generation = mock(DeepSeekEvaluationClient.class);
        var prompt = new EvaluationPromptBuilder().build(context,
                mapper.treeToValue(row.path("retrieval"), EvaluationRetrievalResult.class));
        var sentinel = new LlmEvaluationSuggestion(1, 1, 1, 1, 1, List.of(), List.of(), null, List.of(), false, null);
        when(generation.evaluate(prompt)).thenReturn(new DeepSeekEvaluationResult("offline-fallback-sentinel", "stop", "{}", sentinel));
        when(generation.completeJson(anyString(), anyString())).thenReturn(new DeepSeekJsonCompletionResult("deepseek-flash", "stop", content));
        var provider = new JevJudgeProvider(new JevClient(http.build(), mapper), new JevProperties(), generation,
                new DeepSeekJudgeProvider(generation), mapper, new LlmEvaluationValidator());
        server.expect(requestTo("http://localhost/v1/systemone"))
                .andRespond(withSuccess(row.path("nativeResponse").toString(), MediaType.APPLICATION_JSON));
        var result = provider.evaluate(context, prompt);
        verify(generation, times(1)).completeJson(argThat(system -> system.contains("一个非空连续原文片段")
                && system.contains("不改字词、空白或标点") && system.contains("已由MAIN覆盖的点仍从MAIN摘录")
                && system.contains("按JSON规则转义")), anyString());
        assertThat(prompt.promptVersion()).isEqualTo("evaluation-prompt-v3");
        assertThat(prompt.systemPrompt()).contains("missingPoints只写尚缺的具体子项", "correction中的补充建议也不能把已明确表述的内容称为尚需补充");
        if (valid) {
            assertThat(result.model()).isEqualTo("jev-1.13.0");
            assertThat(result.promptVersion()).isEqualTo("jev-judge-v3");
            var validated = new LlmEvaluationValidator().validate(context, result.suggestion());
            assertThat(validated.scoringPointResults()).extracting(LlmEvaluationSuggestion.ScoringPointResult::scoringPointId)
                    .containsExactlyElementsOf(context.scoringPoints().stream().map(p -> p.scoringPointId()).toList());
            for (var point : validated.scoringPointResults()) {
                assertThat(point.covered()).isEqualTo(row.at("/nativeResponse/answers/point_" + point.scoringPointId() + "/choice").asText().equals("covered"));
                if (!point.covered()) assertThat(point.evidence()).isNull();
            }
            verify(generation, never()).evaluate(any());
        } else {
            assertThat(result.model()).isEqualTo("offline-fallback-sentinel");
            assertThat(result.suggestion()).isSameAs(sentinel);
            verify(generation, times(1)).evaluate(prompt);
        }
        server.verify();
    }
}
