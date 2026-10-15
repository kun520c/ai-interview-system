package com.kun.aiinterview.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.EvaluationRagQueryBuilder;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.vector.request.SearchReq;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** Frozen questions and real QueryBuilder verification only; no retrieval clients or database context. */
class RagP1IndependentQuerySetTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FROZEN_SHA256 =
            "4f42970bbdb3682c475056e512ff725832329d213b8b49e3a27ac45b5afbd456";
    private static final String SENTINEL = "用户答案不得进入查询_SENTINEL";

    @Test
    void frozenDatasetHasExactBytesAndEightFourFourSplitWithoutLabelsOrHits() throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/rag-p1-independent/queries-v1.json")) {
            bytes = Objects.requireNonNull(input).readAllBytes();
        }
        assertThat(sha(bytes)).isEqualTo(FROZEN_SHA256);
        JsonNode dataset = JSON.readTree(bytes);
        assertThat(dataset.path("liveRetrievalStatus").asText()).isEqualTo("NOT_RUN");
        assertThat(dataset.path("annotationStatus").asText()).isEqualTo("NOT_STARTED");
        var queries = StreamSupport.stream(dataset.path("queries").spliterator(), false).toList();
        assertThat(queries).hasSize(16);
        assertThat(queries.stream().map(q -> q.path("queryId").asText()).distinct().count()).isEqualTo(16);
        assertThat(queries.stream().collect(Collectors.groupingBy(q -> q.path("testType").asText(),
                Collectors.counting()))).isEqualTo(Map.of(
                "INDEPENDENT_PARAPHRASE", 8L, "BOUNDARY_FOLLOW_UP", 4L, "OUT_OF_COVERAGE", 4L));
        for (JsonNode query : queries) {
            assertThat(query.has("humanLabel")).isFalse();
            assertThat(query.has("hits")).isFalse();
            assertThat(query.has("cosineScore")).isFalse();
            if (query.path("testType").asText().equals("OUT_OF_COVERAGE")) {
                assertThat(query.path("uncoveredReason").asText()).isNotBlank();
                assertThat(query.path("coverageStatus").asText()).isEqualTo("NOT_COVERED");
            }
        }
    }

    @Test
    void realProductionQueryBuilderMatchesAllSixteenFrozenTextsAndHashes() throws Exception {
        var builder = new EvaluationRagQueryBuilder();
        for (JsonNode query : dataset().path("queries")) {
            JsonNode input = query.path("builderInput");
            var points = new ArrayList<ScoringPointSnapshot>();
            for (JsonNode point : input.path("scoringPoints")) {
                points.add(new ScoringPointSnapshot(point.path("scoringPointId").asLong(),
                        QuestionPointType.valueOf(point.path("pointType").asText()),
                        point.path("content").asText(), point.path("weight").asInt()));
            }
            List<Long> targets = StreamSupport.stream(input.path("followUpTargetPointIds").spliterator(), false)
                    .map(JsonNode::asLong).toList();
            var context = new EvaluationContext(1L, 2L, 1L, 2L,
                    EvaluationMode.valueOf(input.path("mode").asText()),
                    QuestionCategory.valueOf(query.path("category").asText()),
                    query.path("knowledgePoint").asText(), input.path("mainQuestionContent").asText(),
                    "离线参考占位，不进入Query", points, SENTINEL,
                    input.path("followUpQuestionContent").asText(null), SENTINEL, targets);
            String actual = builder.build(context);
            assertThat(actual).as(query.path("queryId").asText())
                    .isEqualTo(query.path("productionStyleQueryText").asText())
                    .doesNotContain(SENTINEL, "coverageBasis", "uncoveredReason", "NOT_COVERED");
            assertThat(sha(actual.getBytes(StandardCharsets.UTF_8)))
                    .isEqualTo(query.path("productionStyleQuerySha256").asText());
        }
    }

    @Test
    void coverageReferencesMatchTheFrozenCorpusManifest() throws Exception {
        JsonNode dataset = dataset();
        var corpus = dataset.path("corpusDocumentSha256").fields();
        int count = 0;
        while (corpus.hasNext()) {
            var source = corpus.next();
            assertThat(source.getValue().asText()).matches("[0-9a-f]{64}");
            count++;
        }
        assertThat(count).isEqualTo(21);
        for (JsonNode query : dataset.path("queries")) {
            for (JsonNode source : query.path("coverageBasis")) {
                assertThat(source.path("sha256").asText()).isEqualTo(dataset.path("corpusDocumentSha256")
                        .path(source.path("path").asText()).asText());
                if (source.has("quote")) {
                    assertThat(source.path("line").asInt()).isPositive();
                    assertThat(source.path("quote").asText()).isNotBlank();
                }
            }
        }
    }

    @Test
    void nativeSdkDefaultsInheritCollectionConsistencyWhichDefaultsToBounded() {
        // Building DTOs does not create a collection or issue any RPC.
        assertThat(SearchReq.builder().collectionName("offline-only").build().getConsistencyLevel()).isNull();
        assertThat(CreateCollectionReq.builder().collectionName("offline-only").build().getConsistencyLevel())
                .isEqualTo(ConsistencyLevel.BOUNDED);
    }

    private JsonNode dataset() throws Exception {
        try (var input = getClass().getResourceAsStream("/rag-p1-independent/queries-v1.json")) {
            return JSON.readTree(Objects.requireNonNull(input));
        }
    }

    private static String sha(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
