package com.kun.aiinterview.knowledge.admission;

import com.kun.aiinterview.knowledge.retrieval.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.*;
import java.util.function.BiFunction;

/** Explicitly selected composition. Disabled path delegates the existing service unchanged. */
public final class ExperimentalEvidenceStrategy {
    @FunctionalInterface public interface Candidates { RetrievalResult retrieve(String question); }
    @FunctionalInterface public interface Reranker { List<RetrievedChunk> rank(String question, List<RetrievedChunk> candidates) throws Exception; }
    @FunctionalInterface public interface GateModel { String complete(ObjectNode request) throws Exception; }
    public record Result(RetrievalResult retrieval, String status, int rerankCalls, int gateCalls, double rerankMs, double gateMs,
                         com.fasterxml.jackson.databind.JsonNode gateDiagnostics) {}
    private final boolean enabled; private final BiFunction<String, Integer, RetrievalResult> legacy;
    private final Candidates candidates; private final Reranker reranker; private final GateModel model; private final PrimarySemanticGate gate;
    public ExperimentalEvidenceStrategy(boolean enabled, BiFunction<String, Integer, RetrievalResult> legacy, Candidates candidates, Reranker reranker, GateModel model, PrimarySemanticGate gate) {
        this.enabled = enabled; this.legacy = legacy; this.candidates = candidates; this.reranker = reranker; this.model = model; this.gate = gate;
    }
    public Result retrieve(String originalQuestion, String legacyQuery, int legacyTopK, QuestionContract contract) {
        if (!enabled) return new Result(legacy.apply(legacyQuery, legacyTopK), "LEGACY_DEFAULT", 0, 0, 0, 0, null);
        RetrievalResult raw = new RetrievalResult(originalQuestion, 5, null, null, List.of(), List.of());
        if (contract == null || !contract.question().equals(originalQuestion)) return empty(raw, "SAFE_EMPTY_NO_QUESTION_CONTRACT", 0, 0, 0, 0);
        int rerankCalls = 0, gateCalls = 0; double rerankMs = 0, gateMs = 0; long started = 0;
        String stage = "RETRIEVAL";
        try {
            raw = candidates.retrieve(originalQuestion);
            if (raw.items().isEmpty()) return empty(raw, "EMPTY_NO_ELIGIBLE_CANDIDATES", 0, 0, 0, 0);
            stage = "RERANK"; started = System.nanoTime(); rerankCalls = 1;
            var ranked = List.copyOf(reranker.rank(originalQuestion, raw.items())); rerankMs = ms(started);
            Set<String> originals = new HashSet<>(); raw.items().forEach(c -> originals.add(c.vectorId()));
            if (ranked.size() != originals.size() || ranked.stream().map(c -> c.vectorId()).distinct().count() != ranked.size() ||
                    !originals.equals(new HashSet<>(ranked.stream().map(c -> c.vectorId()).toList())) || !new HashSet<>(ranked).equals(new HashSet<>(raw.items())))
                throw new IllegalStateException("RERANK_MAPPING");
            var top2 = ranked.stream().limit(2).toList();
            ObjectNode request = gate.request(contract, top2);
            if (PrimarySemanticGate.JSON.writeValueAsBytes(request).length > 16000) return empty(raw, "SAFE_EMPTY_GATE_INPUT_SIZE", rerankCalls, 0, rerankMs, 0);
            stage = "GATE"; started = System.nanoTime(); gateCalls = 1;
            String completion = model.complete(request); gateMs = ms(started);
            var decision = gate.validate(contract, top2, completion);
            return new Result(new RetrievalResult(raw.query(), 5, raw.embeddingModel(), raw.embeddingVersion(), raw.rawHits(), decision.evidence()),
                    decision.diagnostics().path("status").asText(), rerankCalls, gateCalls, rerankMs, gateMs, decision.diagnostics());
        } catch (Exception failure) {
            if (stage.equals("RERANK") && rerankMs == 0) rerankMs = ms(started);
            if (stage.equals("GATE") && gateMs == 0) gateMs = ms(started);
            String reason = "SAFE_EMPTY_" + stage + (timeout(failure) ? "_TIMEOUT" : failure instanceof IllegalArgumentException || failure instanceof com.fasterxml.jackson.core.JsonProcessingException ? "_INVALID" : "_API_OR_MAPPING_FAILURE");
            return empty(raw, reason, rerankCalls, gateCalls, rerankMs, gateMs);
        }
    }
    private static Result empty(RetrievalResult raw, String reason, int rerankCalls, int gateCalls, double rerankMs, double gateMs) {
        return new Result(new RetrievalResult(raw.query(), raw.requestedTopK(), raw.embeddingModel(), raw.embeddingVersion(), raw.rawHits(), List.of()), reason, rerankCalls, gateCalls, rerankMs, gateMs, null);
    }
    private static double ms(long started) { return (System.nanoTime() - started) / 1_000_000.0; }
    private static boolean timeout(Throwable failure) { for (Throwable t = failure; t != null; t = t.getCause()) if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) return true; return false; }
}
