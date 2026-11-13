import com.fasterxml.jackson.databind.JsonNode;
import com.kun.aiinterview.interview.evaluation.EvaluationContext;
import com.kun.aiinterview.interview.evaluation.EvaluationMode;
import com.kun.aiinterview.interview.evaluation.EvaluationRagQueryBuilder;
import com.kun.aiinterview.interview.evaluation.ScoringPointSnapshot;
import com.kun.aiinterview.question.enums.QuestionCategory;
import com.kun.aiinterview.question.enums.QuestionPointType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class D3Queries {
    static final Path BANK = Path.of("docs/question-bank/D1_QUESTION_BANK_V2_20261114.json");
    static final Path IMPORT = Path.of("docs/question-bank/D1_EVALUATION_IMPORT_MANIFEST_20261114.json");
    static final Path PUBLIC = Path.of("docs/verification/D3A_RETRIEVAL_QUERIES_V1_20261114.json");
    static final String BANK_SHA = "d4c1d9f20664308231ab383f5dce67154f3c5d874fc041b44f7499ce8647177e";
    static final String IMPORT_SHA = "dfdd58d2a9d4f108178ae068e45afa910a676046264d67ddb841c72816644f74";
    static final String PUBLIC_SHA = "b5515261f5b2a9f4aa0c93e02d62948d2c28b2c0e41367a51273099cdcbc05fd";

    record Query(String id, String type, String category, String difficulty, String text, EvaluationContext context) {}

    private D3Queries() {}

    public static void main(String[] args) throws Exception {
        D3Corpus.require(args.length == 1, "QUERY_OUTPUT");
        var production = production();
        var publicSet = custom(PUBLIC, PUBLIC_SHA, "PUBLIC", 60);
        D3Corpus.writeNew(Path.of(args[0]), Map.of("production", production.stream().map(q -> Map.of(
                "id", q.id(), "type", q.type(), "category", q.category(), "difficulty", q.difficulty(), "query", q.text())).toList(),
                "publicQueryCount", publicSet.size(), "bankSha256", BANK_SHA, "importSha256", IMPORT_SHA,
                "publicSha256", PUBLIC_SHA));
        System.out.printf("production=%d public=%d%n", production.size(), publicSet.size());
    }

    static List<Query> production() throws Exception {
        JsonNode bank = readVerified(BANK, BANK_SHA).required("questions");
        JsonNode mapping = readVerified(IMPORT, IMPORT_SHA).required("databaseIdMapping");
        D3Corpus.require(bank.size() == 63 && mapping.size() == 63, "D1_INPUT_SIZE");
        Map<String, JsonNode> ids = new HashMap<>();
        mapping.forEach(row -> ids.put(row.required("bankId").asText(), row));
        List<Query> result = new ArrayList<>();
        List<Query> followups = new ArrayList<>();
        var categories = EnumSet.noneOf(QuestionCategory.class);
        var builder = new EvaluationRagQueryBuilder();
        for (JsonNode row : bank) {
            String id = row.required("bankId").asText();
            String difficulty = row.required("difficulty").asText();
            var category = QuestionCategory.valueOf(row.required("category").asText());
            var scoring = row.required("scoringPoints");
            var pointIds = ids.get(id).required("pointIds");
            D3Corpus.require(scoring.size() == pointIds.size(), "D1_POINT_MAPPING");
            List<ScoringPointSnapshot> points = new ArrayList<>();
            for (int i = 0; i < scoring.size(); i++) {
                JsonNode point = scoring.get(i);
                points.add(new ScoringPointSnapshot(pointIds.get(i).asLong(),
                        QuestionPointType.valueOf(point.required("pointType").asText()),
                        point.required("content").asText(), point.required("weight").asInt()));
            }
            var main = new EvaluationContext(1L, 1L, 1L, 1L, EvaluationMode.MAIN_ANSWER, category,
                    row.required("knowledgePoint").asText(), row.required("questionContent").asText(),
                    row.required("referenceAnswer").asText(), points, "D3_SYNTHETIC_MAIN_ANSWER", null, null, List.of());
            result.add(new Query(id, "MAIN", category.name(), difficulty, checkedQuery(builder, main), main));
            if (categories.add(category)) {
                var target = points.stream().filter(p -> p.pointType() == QuestionPointType.CORE).findFirst().orElseThrow();
                String followUp = "请补充这一机制的成立条件，并举一个例子。" + target.content();
                var context = new EvaluationContext(2L, 2L, 1L, 1L, EvaluationMode.FOLLOW_UP_ANSWER, category,
                        main.knowledgePoint(), main.mainQuestionContent(), main.referenceAnswerSnapshot(), points,
                        main.mainAnswerContent(), followUp, "D3_SYNTHETIC_FOLLOWUP_ANSWER", List.of(target.scoringPointId()));
                followups.add(new Query(id + "-F", "FOLLOW_UP", category.name(), difficulty, checkedQuery(builder, context), context));
            }
        }
        D3Corpus.require(followups.size() == 9, "FOLLOWUP_DOMAINS");
        result.addAll(followups);
        return List.copyOf(result);
    }

    private static String checkedQuery(EvaluationRagQueryBuilder builder, EvaluationContext context) {
        String query = builder.build(context);
        D3Corpus.require(!query.contains("D3_SYNTHETIC_"), "ANSWER_LEAK");
        return query;
    }

    static List<Query> custom(Path path, String expectedHash, String type, int expectedSize) throws Exception {
        JsonNode rows = readVerified(path, expectedHash).required("queries");
        D3Corpus.require(rows.size() == expectedSize, "QUERY_COUNT_" + type);
        List<Query> result = new ArrayList<>();
        for (JsonNode row : rows) {
            String text = row.required("query").asText();
            D3Corpus.require(!text.isBlank() && text.equals(text.strip()), "QUERY_BYTES");
            String category = row.required("domains").get(0).asText();
            var context = new EvaluationContext(1L, 1L, 1L, 1L, EvaluationMode.MAIN_ANSWER,
                    QuestionCategory.valueOf(category), "d3_retrieval_fixture", text, "合成 Prompt 参考文本。", List.of(),
                    "D3_SYNTHETIC_MAIN_ANSWER", null, null, List.of());
            result.add(new Query(row.required("id").asText(), type, category, "N/A", text, context));
        }
        D3Corpus.require(result.stream().map(Query::id).distinct().count() == expectedSize, "DUPLICATE_QUERY_ID");
        return List.copyOf(result);
    }

    private static JsonNode readVerified(Path path, String hash) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        D3Corpus.require(hash.equals(D3Corpus.sha(bytes)), "FROZEN_QUERY_INPUT_SHA");
        return D3Corpus.JSON.readTree(bytes);
    }
}
