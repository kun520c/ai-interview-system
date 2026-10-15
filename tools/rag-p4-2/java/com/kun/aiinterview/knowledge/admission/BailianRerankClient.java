package com.kun.aiinterview.knowledge.admission;

import com.fasterxml.jackson.databind.JsonNode;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import org.springframework.web.client.RestClient;
import java.net.URI;
import java.util.*;

/** Native Beijing Rerank endpoint, never the Embedding compatibility endpoint. One application request. */
public final class BailianRerankClient implements ExperimentalEvidenceStrategy.Reranker {
    public static final String PATH = "/api/v1/services/rerank/text-rerank/text-rerank";
    private final RestClient client;
    public BailianRerankClient(RestClient client, URI endpoint) {
        if (!"https".equals(endpoint.getScheme()) || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null || endpoint.getPort() != -1 ||
                endpoint.getHost() == null || !endpoint.getHost().matches("[A-Za-z0-9-]+\\.cn-beijing\\.maas\\.aliyuncs\\.com") || !endpoint.getPath().equals(PATH))
            throw new IllegalArgumentException("BEIJING_RERANK_ENDPOINT_REQUIRED");
        this.client = client;
    }
    public List<RetrievedChunk> rank(String question, List<RetrievedChunk> candidates) throws Exception {
        if (candidates.isEmpty() || candidates.size() > 5) throw new IllegalArgumentException("RERANK_TOP5_ONLY");
        var request = PrimarySemanticGate.JSON.createObjectNode().put("model", "qwen3.7-text-rerank");
        var input = request.putObject("input").put("query", question);
        var documents = input.putArray("documents"); candidates.forEach(c -> documents.add(c.title() + "\n\n" + c.content()));
        request.putObject("parameters").put("top_n", candidates.size());
        JsonNode response = client.post().body(request).retrieve().body(JsonNode.class);
        if (response == null || response.has("code") || !response.path("output").path("results").isArray() ||
                response.path("output").path("results").size() != candidates.size()) throw new IllegalArgumentException("RERANK_RESPONSE_COUNT");
        List<RetrievedChunk> sorted = new ArrayList<>(); Set<Integer> seen = new HashSet<>(); double previous = 1;
        for (var r : response.path("output").path("results")) {
            int index = r.path("index").asInt(-1); double score = r.path("relevance_score").asDouble(Double.NaN);
            if (!r.path("index").isIntegralNumber() || !r.path("relevance_score").isNumber() || index < 0 || index >= candidates.size() || !seen.add(index) ||
                    !Double.isFinite(score) || score < 0 || score > previous) throw new IllegalArgumentException("RERANK_INDEX_SCORE");
            previous = score; sorted.add(candidates.get(index));
        }
        return List.copyOf(sorted);
    }
}
