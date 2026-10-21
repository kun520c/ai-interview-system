package com.kun.aiinterview.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.entity.AnswerEvaluation;
import com.kun.aiinterview.interview.enums.EvaluationPhase;
import com.kun.aiinterview.interview.evaluation.*;
import com.kun.aiinterview.interview.evaluation.decision.FollowUpPolicy;
import com.kun.aiinterview.interview.evaluation.decision.FollowUpTargetResolver;
import com.kun.aiinterview.interview.evaluation.judge.JudgeProvider;
import com.kun.aiinterview.interview.evaluation.llm.LlmEvaluationSuggestion;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPrompt;
import com.kun.aiinterview.interview.evaluation.prompt.EvaluationPromptBuilder;
import com.kun.aiinterview.interview.evaluation.score.EvaluationScoreCalculator;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationStandard;
import com.kun.aiinterview.interview.evaluation.validation.LlmEvaluationValidator;
import com.kun.aiinterview.interview.mapper.AnswerEvaluationMapper;
import com.kun.aiinterview.interview.orchestration.EvaluationOrchestrationService;
import com.kun.aiinterview.interview.service.EvaluationContextService;
import com.kun.aiinterview.knowledge.embedding.*;
import com.kun.aiinterview.knowledge.entity.RagHitLog;
import com.kun.aiinterview.knowledge.entity.RagRetrievalBatch;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.mapper.*;
import com.kun.aiinterview.knowledge.retrieval.*;
import com.kun.aiinterview.knowledge.vector.*;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real application objects with offline external boundaries; not live retrieval quality evidence. */
class RagP1OfflineBusinessReplayTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<Map<String, Object>> OUTPUT = new ArrayList<>();
    private static final JsonNode FIXTURE = fixture();

    @ParameterizedTest
    @ValueSource(floats = {0.60F, 0.70F, 0.75F, 0.80F})
    void replaysAllEightPublicQueriesThroughPromptTraceAndScoring(float threshold) throws Exception {
        for (JsonNode query : FIXTURE.path("queries")) {
            runPipeline(query, threshold, true, null);
        }
    }

    @Test
    void defaultDisabledKeepsFiveCandidates() throws Exception {
        runPipeline(FIXTURE.path("queries").get(0), 0.70F, false, null);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void acceptsEveryDynamicEvidenceCountIncludingZeroWithoutRefill(int count) throws Exception {
        runPipeline(FIXTURE.path("queries").get(0), 0.70F, true, count);
    }

    @Test
    void followUpWithAllCandidatesFilteredUsesNoEvidenceFallback() throws Exception {
        runPipeline(FIXTURE.path("queries").get(7), 0.70F, true, 0);
    }

    @Test
    void readsAdvisorySuggestionsSeparatelyWithoutChangingBaselineLabels() {
        assertThat(FIXTURE.path("advisoryReview").path("proposals")).hasSize(6);
        var query = FIXTURE.path("queries").get(2);
        assertThat(query.path("hits").get(0).path("baselineLabel").asText()).isEqualTo("1");
        assertThat(query.path("hits").get(3).path("baselineLabel").asText()).isEqualTo("2");
        assertThat(FIXTURE.path("provenance").asText()).isEqualTo("AUTHORED_SYNTHETIC_OFFLINE_FIXTURE");
        assertThat(FIXTURE.path("basedOnPaidResponses").asBoolean()).isFalse();
    }

    private void runPipeline(JsonNode query, float threshold, boolean enabled, Integer syntheticCount)
            throws Exception {
        var properties = new RetrievalProperties();
        properties.setCosineThresholdEnabled(enabled);
        properties.setMinCosineScore(threshold);
        var context = context(query);
        String embeddedQuery = query.path("embeddedQuery").asText();
        assertThat(new EvaluationRagQueryBuilder().build(context)).isEqualTo(embeddedQuery);
        String model = FIXTURE.path("embeddingModel").asText();
        String version = query.path("hits").get(0).path("embeddingVersion").asText();

        var embedding = mock(EmbeddingClient.class);
        var vectors = mock(VectorStoreClient.class);
        var chunks = mock(KnowledgeChunkMapper.class);
        var batches = mock(RagRetrievalBatchMapper.class);
        var logs = mock(RagHitLogMapper.class);
        var answers = mock(AnswerEvaluationMapper.class);
        var contexts = mock(EvaluationContextService.class);
        var hits = new ArrayList<VectorSearchHit>();
        var rows = new ArrayList<KnowledgeRetrievalRow>();
        for (JsonNode hit : query.path("hits")) {
            int rank = hit.path("rank").asInt();
            float score = Float.intBitsToFloat(Integer.parseUnsignedInt(
                    hit.path("scoreFloat32BitsHex").asText(), 16));
            assertThat(Float.floatToRawIntBits(Float.parseFloat(hit.path("cosineScore").asText())))
                    .isEqualTo(Float.floatToRawIntBits(score));
            if (syntheticCount != null) score = rank <= syntheticCount ? 0.9F : 0.5F;
            hits.add(new VectorSearchHit(hit.path("vectorId").asText(), hit.path("documentId").asLong(),
                    hit.path("chunkIndex").asInt(), version, score));
            var row = new KnowledgeRetrievalRow();
            row.setVectorId(hit.path("vectorId").asText());
            row.setChunkId(hit.path("chunkId").asLong());
            row.setDocumentId(hit.path("documentId").asLong());
            row.setDocumentVersion(hit.path("documentVersion").asInt());
            row.setChunkIndex(hit.path("chunkIndex").asInt());
            row.setContent(hit.path("content").asText());
            row.setTitle(hit.path("title").asText());
            // Authored offline placeholders, never real SQL rows or retrieval-quality labels.
            row.setCategory(KnowledgeCategory.valueOf(query.path("category").asText()));
            rows.add(row);
        }

        var retained = hits.stream().filter(h -> !enabled || h.similarityScore() >= threshold).toList();
        List<String> retainedIds = retained.stream().map(VectorSearchHit::vectorId).toList();
        when(embedding.embed(List.of(embeddedQuery))).thenReturn(new EmbeddingBatchResult(
                model, version, 1, List.of(new EmbeddingVector(0, List.of(1F))), null));
        when(vectors.search(List.of(1F), version, 5)).thenReturn(hits);
        Collections.reverse(rows); // Real service must restore the vector order after SQL hydration.
        if (!retainedIds.isEmpty()) {
            when(chunks.selectRetrievableByVectorIds(retainedIds, model, version))
                    .thenReturn(rows.stream().filter(r -> retainedIds.contains(r.getVectorId())).toList());
        }

        var retrievalService = new KnowledgeRetrievalService(embedding, vectors, chunks, properties);
        var adapter = new EvaluationRetrievalAdapter(new EvaluationRagQueryBuilder(), retrievalService);
        var trace = new RagTracePersistenceService(batches, logs, properties, org.mockito.Mockito.mock(com.kun.aiinterview.interview.mapper.InterviewAnswerMapper.class));
        var promptBuilder = new EvaluationPromptBuilder();
        var targetResolver = new FollowUpTargetResolver();
        when(contexts.buildContext(1L)).thenReturn(context);
        var traceBatches = new ArrayList<RagRetrievalBatch>();
        var traceHits = new ArrayList<RagHitLog>();
        when(batches.insertBatch(any())).thenAnswer(inv -> {
            traceBatches.add(inv.getArgument(0));
            return 1;
        });
        when(logs.batchInsert(any())).thenAnswer(inv -> {
            List<RagHitLog> inserted = inv.getArgument(0);
            traceHits.addAll(inserted);
            return inserted.size();
        });
        var prompts = new ArrayList<EvaluationPrompt>();
        JudgeProvider offlineJudge = (c, prompt) -> {
            prompts.add(prompt);
            var points = c.scoringPoints().stream().map(p ->
                    new LlmEvaluationSuggestion.ScoringPointResult(p.scoringPointId(), false, null)).toList();
            return new JudgeProvider.Result("offline-stub", prompt.promptVersion(), "{}",
                    new LlmEvaluationSuggestion(16, 15, 14, 13, 12, List.of(), List.of(),
                            null, points, false, null));
        };
        var orchestrator = new EvaluationOrchestrationService(contexts, adapter, promptBuilder, offlineJudge,
                new LlmEvaluationValidator(), new EvaluationScoreCalculator(), new EvaluationStandard(),
                new FollowUpPolicy(targetResolver), targetResolver, answers, JSON, trace);

        var result = orchestrator.evaluate(1L, false, "offline-attempt");

        assertThat(traceBatches).isEmpty();
        trace.persist(result.retrievalBatchId(), result.answerId(), result.pendingTrace());

        assertThat(result.totalScore()).isEqualTo(70);
        assertThat(result.evaluationPhase()).isEqualTo(EvaluationPhase.FINAL);
        assertThat(result.evaluation()).isNotNull();
        verify(answers, never()).insertEvaluation(any());
        assertThat(result.evaluation().getRetrievalBatchId()).isEqualTo(traceBatches.get(0).getRetrievalBatchId());
        assertThat(traceBatches.get(0).getRawHitCount()).isEqualTo(5);
        assertThat(traceBatches.get(0).getEvidenceHitCount()).isEqualTo(retained.size());
        var snapshot = JSON.readTree(traceBatches.get(0).getFilterSnapshot());
        if (enabled) {
            assertThat(snapshot.path("filterVersion").asText()).isEqualTo("evaluation-rag-filter-v2");
            assertThat(snapshot.path("cosineCandidate").path("enabled").asBoolean()).isTrue();
            assertThat((float) snapshot.path("cosineCandidate").path("minScore").asDouble())
                    .isEqualTo(threshold);
        } else {
            assertThat(snapshot.path("filterVersion").asText()).isEqualTo("evaluation-rag-filter-v1");
            assertThat(snapshot.has("cosineCandidate")).isFalse();
        }
        if (!traceHits.isEmpty()) {
            assertThat(JSON.readTree(traceHits.get(0).getFilterSnapshot())).isEqualTo(snapshot);
        }
        List<Integer> ranks = hits.stream().filter(retained::contains).map(h -> hits.indexOf(h) + 1).toList();
        assertThat(traceHits).extracting(RagHitLog::getRankNo).containsExactlyElementsOf(ranks);
        String evidenceSection = prompts.get(0).userPrompt().split("===== RAG EVIDENCE =====\\n", 2)[1];
        assertThat(evidenceSection.lines().filter(l -> l.startsWith("- rank=")).count())
                .isEqualTo(retained.size());
        assertThat(prompts.get(0).systemPrompt()).contains("不得虚构检索内容");
        for (int i = 0; i < hits.size(); i++) {
            String content = query.path("hits").get(i).path("content").asText();
            if (retained.contains(hits.get(i))) assertThat(evidenceSection).contains(content);
            else assertThat(evidenceSection).doesNotContain(content);
        }
        if (retained.isEmpty()) {
            assertThat(evidenceSection).isEqualTo("无可用 RAG Evidence。\n");
            verifyNoInteractions(chunks);
            verify(logs, never()).batchInsert(any());
        } else {
            verify(chunks).selectRetrievableByVectorIds(retainedIds, model, version);
        }
        verify(vectors, times(1)).search(List.of(1F), version, 5);
        OUTPUT.add(Map.of("queryId", query.path("queryId").asText(), "threshold", threshold,
                "enabled", enabled, "synthetic", syntheticCount != null, "retainedRanks", ranks,
                "filteredVectorIds", hits.stream().filter(h -> !retained.contains(h))
                        .map(VectorSearchHit::vectorId).toList(),
                "promptEvidenceCount", retained.size(), "rawHitCount", 5,
                "promptEvidenceSha256", java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(evidenceSection.getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
    }

    private static EvaluationContext context(JsonNode query) {
        var points = StreamSupport.stream(query.path("scoringPoints").spliterator(), false)
                .map(p -> new ScoringPointSnapshot(p.path("scoringPointId").asLong(),
                        QuestionPointType.valueOf(p.path("pointType").asText()),
                        p.path("content").asText(), p.path("weight").asInt())).toList();
        var targets = StreamSupport.stream(query.path("followUpTargetPointIds").spliterator(), false)
                .map(JsonNode::asLong).toList();
        return new EvaluationContext(1L, 2L, 1L, 2L, EvaluationMode.valueOf(query.path("mode").asText()),
                QuestionCategory.valueOf(query.path("category").asText()), query.path("knowledgePoint").asText(),
                query.path("originalQuestion").asText(), query.path("referenceAnswer").asText(), points,
                "离线合成回答，不用于质量评分", query.path("followUpQuestion").asText(null),
                "离线合成追问回答", targets);
    }

    private static JsonNode fixture() {
        try (var input = RagP1OfflineBusinessReplayTest.class.getResourceAsStream("/rag-p1/public-replay.json")) {
            return JSON.readTree(Objects.requireNonNull(input));
        } catch (Exception exception) {
            throw new IllegalStateException("Offline fixture unavailable", exception);
        }
    }

    @AfterAll
    static void writeActualPromptReplayEvidence() throws Exception {
        Path output = Path.of("target/test-replays/public-rag-business-replay.json");
        Files.createDirectories(output.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), OUTPUT);
    }
}
