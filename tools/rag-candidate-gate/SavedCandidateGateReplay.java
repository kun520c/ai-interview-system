import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.knowledge.admission.PrimarySemanticGate;
import com.kun.aiinterview.knowledge.admission.QuestionContract;
import com.kun.aiinterview.knowledge.admission.experimental.CandidateLevelSemanticGate;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Saved files only. Rebuilds the same Top2 from saved hydration and ranking, never calls a provider. */
public final class SavedCandidateGateReplay {
    private static JsonNode read(Path path) throws Exception { return PrimarySemanticGate.JSON.readTree(Files.readAllBytes(path)); }
    private static void require(boolean value, String code) { if (!value) throw new AssertionError(code); }
    private static String sha(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    public static void main(String[] args) throws Exception {
        Path run = Path.of(args[0]); Path output = Path.of(args[1]);
        ObjectNode report = PrimarySemanticGate.JSON.createObjectNode().put("origin", "OFFLINE_SAVED_RAW_RESPONSES")
                .put("newModelCalls", 0).put("realSearch", 0).put("productionIntegration", "NOT_ENABLED");
        var comparisons = report.putArray("comparisons");
        int tests = 0;
        JsonNode summary = read(run.resolve("summary.json"));
        require(summary.path("executionStatus").asText().equals("STOPPED"), "HISTORY_MUST_REMAIN_STOPPED"); tests++;
        for (JsonNode question : read(run.resolve("frozen-questions.json")).path("questions")) {
            String id = question.path("questionId").asText();
            Path caseDir = run.resolve(id);
            if (!Files.isRegularFile(caseDir.resolve("SEMANTIC_GATE/response.json"))) continue;
            QuestionContract contract = PrimarySemanticGate.JSON.treeToValue(question, QuestionContract.class);
            JsonNode hydrated = read(caseDir.resolve("MILVUS_SEARCH/hydrated.json"));
            List<RetrievedChunk> chunks = new ArrayList<>();
            for (JsonNode hit : hydrated) {
                JsonNode k = hit.path("knowledge");
                require(hit.path("sqlEligible").asBoolean() && k.path("vectorId").asText().equals(hit.path("vectorId").asText()), "HYDRATION_IDENTITY");
                require(hit.path("contentSha256").asText().equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(k.path("content").asText().getBytes(StandardCharsets.UTF_8)))), "CONTENT_SHA");
                chunks.add(new RetrievedChunk(chunks.size() + 1, hit.path("cosineFloat32").asDouble(), k.path("chunkId").asLong(),
                        k.path("documentId").asLong(), k.path("documentVersion").asInt(), k.path("chunkIndex").asInt(),
                        hit.path("vectorId").asText(), k.path("title").asText(), KnowledgeCategory.valueOf(k.path("category").asText()),
                        k.path("source").isNull() ? null : k.path("source").asText(), k.path("content").asText()));
            }
            List<RetrievedChunk> ranked = new ArrayList<>(); Set<Integer> indexes = new HashSet<>();
            JsonNode savedResult = read(caseDir.resolve("result.json"));
            for (JsonNode result : read(caseDir.resolve("RERANK/response.json")).at("/output/results")) {
                int index = result.path("index").asInt(-1);
                require(index >= 0 && index < chunks.size() && indexes.add(index), "RANK_INDEX");
                RetrievedChunk c = chunks.get(index);
                require(c.vectorId().equals(savedResult.path("ranking").get(ranked.size()).path("vectorId").asText()), "RANK_IDENTITY");
                ranked.add(c);
            }
            require(ranked.size() == chunks.size() && chunks.size() == 5, "SAME_TOP5"); tests++;
            List<RetrievedChunk> top2 = ranked.stream().limit(2).toList();
            var frozen = new PrimarySemanticGate();
            Path requestPath = caseDir.resolve("SEMANTIC_GATE/request.json"), responsePath = caseDir.resolve("SEMANTIC_GATE/response.json");
            JsonNode request = read(requestPath);
            require(PrimarySemanticGate.JSON.readTree(request.at("/messages/1/content").asText()).equals(frozen.request(contract, top2))
                    && request.at("/messages/0/content").asText().equals(Files.readString(run.resolve("system-prompt.txt"))), "BYTE_SAVED_INPUT_BINDING"); tests++;
            JsonNode outer = read(responsePath), choice = outer.at("/choices/0");
            require(outer.path("choices").size() == 1 && choice.path("index").asInt(-1) == 0
                    && choice.path("finish_reason").asText().equals("stop") && choice.at("/message/role").asText().equals("assistant"), "COMPLETE_SAVED_RESPONSE");
            String raw = choice.at("/message/content").asText();
            var oldDecision = frozen.validate(contract, top2, raw);
            require(oldDecision.diagnostics().equals(savedResult.path("gate")), "FROZEN_DECISION_DRIFT"); tests++;
            boolean oldStop = oldDecision.diagnostics().path("status").asText().contains("UNCERTAIN");
            require(oldStop && savedResult.path("failureCode").asText().equals("GATE_UNCERTAIN_STOP")
                    && savedResult.path("finalAdmittedCandidateIds").isEmpty(), "HISTORICAL_ACTUAL_ADMISSION"); tests++;
            var newDecision = new CandidateLevelSemanticGate().validate(contract, top2, raw);
            require(newDecision.evidence().equals(List.of(top2.getFirst())) && !newDecision.diagnostics().path("globalRejected").asBoolean(), "VALID_TOP1_PRESERVED"); tests++;
            require(newDecision.diagnostics().path("rejectedCandidateIds").toString().equals(PrimarySemanticGate.JSON.valueToTree(List.of(top2.getLast().vectorId())).toString())
                    && newDecision.diagnostics().at("/candidates/1/verdict").asText().equals("NO_SUPPORT")
                    && newDecision.diagnostics().at("/candidates/1/supports").isEmpty(), "UNSUPPORTED_TOP2_REJECTED"); tests++;
            require(newDecision.diagnostics().at("/candidates/0/supports").equals(oldDecision.diagnostics().at("/candidates/0/supports"))
                    && newDecision.diagnostics().at("/candidates/0/versionAssessment").asText().equals("MATCH")
                    && newDecision.diagnostics().path("missingPrimaryRequirementIdsAcrossAdmitted").equals(oldDecision.diagnostics().path("missingPrimaryRequirementIdsAcrossAdmitted")), "STRICT_CHECKS_UNCHANGED"); tests++;
            require(newDecision.diagnostics().at("/uncertaintySources/0/scope").asText().equals("CANDIDATE")
                    && newDecision.diagnostics().at("/uncertaintySources/0/candidateId").asText().equals(top2.getLast().vectorId()), "UNCERTAINTY_SOURCE"); tests++;
            ObjectNode row = comparisons.addObject().put("questionId", id).put("requestSha256", sha(requestPath)).put("responseSha256", sha(responsePath))
                    .put("sameHistoricalInput", true).put("strictEvidenceValidation", "PRESERVED").put("oldExecutionFailure", "GATE_UNCERTAIN_STOP");
            row.set("frozenGate", oldDecision.diagnostics()); row.set("frozenActualAdmittedCandidateIds", savedResult.path("finalAdmittedCandidateIds"));
            row.set("candidateLevelGate", newDecision.diagnostics());
        }
        require(comparisons.size() == 1, "EXACTLY_ONE_SAVED_REAL_CASE"); tests++;
        report.put("tests", tests).put("failures", 0).put("errors", 0).put("skipped", 0)
                .put("historicalExecutionStatus", summary.path("executionStatus").asText())
                .put("historicalQualityStatus", summary.path("qualityStatus").asText())
                .put("generalQualityConclusion", "NOT_EVALUATED");
        Files.writeString(output, report.toPrettyString(), StandardOpenOption.CREATE_NEW);
        System.out.println("Saved candidate gate replay: tests=" + tests + ", failures=0, errors=0, skipped=0; raw cases=1; models=0");
    }
}
