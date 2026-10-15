import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.knowledge.admission.PrimarySemanticGate;
import com.kun.aiinterview.knowledge.admission.QuestionContract;
import com.kun.aiinterview.knowledge.admission.experimental.CandidateLevelSemanticGate;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Authored Mock responses on independent questions, never a semantic-quality benchmark. */
public final class CandidateLevelGateOfflineTest {
    private static final String QUOTE = "The dispatch operation reads the target slot and delegates to the selected handler.";
    private static final String CONTEXT = "Logging records the invocation but does not select or invoke the target handler.";
    private static final String CONFLICT = "Under different conditions the dispatch operation cannot delegate to the target handler.";
    private static final QuestionContract CONTRACT = contract("mock-dispatch", "Java SE 21");
    private static final List<RetrievedChunk> CHUNKS = List.of(chunk("mechanism", QUOTE + "\n" + CONTEXT + "\n" + CONFLICT),
            chunk("background", CONTEXT + "\n" + CONFLICT));
    private static final List<String> passed = new ArrayList<>();
    private static final List<String> failures = new ArrayList<>();
    private static final List<String> errors = new ArrayList<>();
    private interface Checked { void run() throws Exception; }

    private static QuestionContract contract(String id, String version) {
        return new QuestionContract(id, "Explain dispatch and lookup, compared with logging.", version, List.of(
                new QuestionContract.Requirement("r1", QuestionContract.Role.PRIMARY_MECHANISM, "dispatch", "dispatch mechanism"),
                new QuestionContract.Requirement("r2", QuestionContract.Role.PRIMARY_MECHANISM, "lookup", "lookup mechanism"),
                new QuestionContract.Requirement("r3", QuestionContract.Role.SUPPORTING_CONTEXT, "logging", "logging background")));
    }
    private static RetrievedChunk chunk(String id, String body) {
        return new RetrievedChunk(1, .1, 1L, 1L, 1, 1, id, "Independent Mock", KnowledgeCategory.JAVA_BASIC, null, body);
    }
    private static ObjectNode reply() {
        ObjectNode root = PrimarySemanticGate.JSON.createObjectNode().put("questionId", CONTRACT.questionId());
        ArrayNode candidates = root.putArray("candidates");
        ObjectNode first = candidates.addObject().put("candidateId", CHUNKS.getFirst().vectorId())
                .put("verdict", "CORE_SUPPORT").put("versionAssessment", "MATCH").put("reason", "Authored independent Mock mechanism");
        addSupport(first, "r1", "DIRECT_MECHANISM", QUOTE);
        candidates.addObject().put("candidateId", CHUNKS.getLast().vectorId()).put("verdict", "NO_SUPPORT")
                .put("versionAssessment", "UNCERTAIN").put("reason", "Authored Mock; no usable evidence").putArray("supports");
        return root;
    }
    private static ObjectNode candidate(ObjectNode root, int index) { return (ObjectNode) root.path("candidates").get(index); }
    private static ObjectNode support(ObjectNode root) { return (ObjectNode) candidate(root, 0).path("supports").get(0); }
    private static void addSupport(ObjectNode candidate, String id, String relation, String quote) {
        ArrayNode supports = candidate.has("supports") ? (ArrayNode) candidate.get("supports") : candidate.putArray("supports");
        supports.addObject().put("requirementId", id).put("relation", relation).put("quote", quote).put("explanation", "Authored Mock relationship");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Candidate gate invariant failed"); }
    private static CandidateLevelSemanticGate.Decision evaluate(ObjectNode reply) {
        return new CandidateLevelSemanticGate().validate(CONTRACT, CHUNKS, reply.toString());
    }
    private static void global(ObjectNode reply, String code) { global(evaluate(reply), code); }
    private static void global(CandidateLevelSemanticGate.Decision decision, String code) {
        check(decision.evidence().isEmpty() && decision.diagnostics().path("admittedCandidateIds").isEmpty());
        check(decision.diagnostics().path("globalRejected").asBoolean());
        check(decision.diagnostics().path("globalRejectionReasons").toString().contains("\"" + code + "\""));
        check(!decision.diagnostics().path("uncertaintySources").isEmpty());
        check(decision.diagnostics().path("missingPrimaryRequirementIdsAcrossAdmitted").size() == 2);
    }
    private static void test(String name, Checked run) {
        try { run.run(); passed.add(name); }
        catch (AssertionError failure) { failures.add(name + ": " + failure.getMessage()); }
        catch (Exception failure) { errors.add(name + ": " + failure.getClass().getSimpleName()); }
    }
    public static void main(String[] args) throws Exception {
        test("valid mechanism survives rejected version-uncertain candidate without rewriting response", () -> {
            ObjectNode raw = reply(); String before = raw.toString();
            var result = evaluate(raw); JsonNode d = result.diagnostics();
            check(result.evidence().equals(List.of(CHUNKS.getFirst())) && before.equals(raw.toString()));
            check(!d.path("globalRejected").asBoolean() && d.path("uncertaintySources").size() == 1);
            check(d.at("/uncertaintySources/0/scope").asText().equals("CANDIDATE"));
            check(d.at("/candidates/1/versionAssessment").asText().equals("UNCERTAIN"));
            check(d.path("rejectedCandidateIds").toString().equals("[\"background\"]"));
            var frozen = new PrimarySemanticGate().validate(CONTRACT, CHUNKS, before);
            check(frozen.diagnostics().path("status").asText().equals("ADMITTED_WITH_UNCERTAIN_CANDIDATE"));
            check(d.at("/candidates/0/supports").equals(frozen.diagnostics().at("/candidates/0/supports")));
            check(d.path("missingPrimaryRequirementIdsAcrossAdmitted").toString().equals("[\"r2\"]"));
        });
        test("eligible candidate's own uncertain version remains a global strict failure", () -> {
            var r = reply(); candidate(r, 0).put("versionAssessment", "UNCERTAIN"); global(r, "CORE_VERSION_UNCONFIRMED");
        });
        for (String version : List.of("MISMATCH", "NOT_APPLICABLE")) test("explicit version rejects CORE " + version, () -> {
            var r = reply(); candidate(r, 0).put("versionAssessment", version); global(r, "CORE_VERSION_UNCONFIRMED");
        });
        test("no version constraint accepts NOT_APPLICABLE without changing policy", () -> {
            var r = reply(); candidate(r, 0).put("versionAssessment", "NOT_APPLICABLE");
            check(new CandidateLevelSemanticGate().validate(contract(CONTRACT.questionId(), ""), CHUNKS, r.toString()).evidence().size() == 1);
        });
        test("uncertain verdict on otherwise rejected candidate cannot be isolated", () -> {
            var r = reply(); candidate(r, 1).put("verdict", "UNCERTAIN"); global(r, "VERDICT_UNCERTAIN");
        });
        test("uncertain background version cannot be isolated", () -> {
            var r = reply(); candidate(r, 1).put("verdict", "BACKGROUND_ONLY"); global(r, "VERSION_UNCERTAIN_NOT_ISOLATABLE");
        });
        test("uncertain relation on CORE cannot be silently ignored", () -> {
            var r = reply(); addSupport(candidate(r, 0), "r2", "UNCERTAIN", QUOTE); global(r, "RELATION_UNCERTAIN");
        });
        test("uncertain relation on rejected background is global", () -> {
            var r = reply(); candidate(r, 1).put("verdict", "BACKGROUND_ONLY").put("versionAssessment", "MATCH");
            addSupport(candidate(r, 1), "r3", "UNCERTAIN", CONTEXT); global(r, "RELATION_UNCERTAIN");
        });
        test("NO_SUPPORT with any claim is illegal even if version is uncertain", () -> {
            var r = reply(); addSupport(candidate(r, 1), "r3", "SUPPORTING_CONTEXT", CONTEXT); global(r, "NO_SUPPORT_WITH_CLAIMS");
        });
        test("cross-candidate direct and condition-conflict claims cause global rejection", () -> {
            var r = reply(); candidate(r, 1).put("verdict", "BACKGROUND_ONLY").put("versionAssessment", "MATCH");
            addSupport(candidate(r, 1), "r1", "CONDITION_CONFLICT", CONFLICT); global(r, "CONTRADICTORY_SUPPORT_RELATIONS");
            check(evaluate(r).diagnostics().path("contradictions").size() == 1);
        });
        test("same-candidate contradictory support also blocks admission", () -> {
            var r = reply(); addSupport(candidate(r, 0), "r1", "CONDITION_CONFLICT", CONFLICT); global(r, "CONTRADICTORY_SUPPORT_RELATIONS");
        });
        test("CORE with no quote is rejected rather than admitted by verdict", () -> {
            var r = reply(); candidate(r, 0).putArray("supports"); global(r, "CORE_REQUIRES_PRIMARY_MECHANISM");
        });
        for (String quote : List.of("Invented evidence which never appears in the candidate body.",
                "The dispatch operation ... delegates to the selected handler.")) test("absent or spliced quote: " + quote, () -> {
            var r = reply(); support(r).put("quote", quote); global(r, "QUOTE_NOT_GROUNDED_BODY");
        });
        for (String body : List.of("# " + QUOTE, "> " + QUOTE, "- [REF] " + QUOTE, "- Primary: [REF] " + QUOTE))
            test("header/metadata/reference is never mechanism: " + body, () -> {
                var r = reply(); global(new CandidateLevelSemanticGate().validate(CONTRACT,
                        List.of(chunk("mechanism", body), CHUNKS.getLast()), r.toString()), "QUOTE_NOT_GROUNDED_BODY");
            });
        test("URL inside Markdown remains unusable as quote", () -> {
            String url = "https://example.invalid/specification/dispatch";
            var r = reply(); support(r).put("quote", url);
            global(new CandidateLevelSemanticGate().validate(CONTRACT, List.of(chunk("mechanism", "[REF](" + url + ")"), CHUNKS.getLast()),
                    r.toString()), "QUOTE_NOT_GROUNDED_BODY");
        });
        test("context role cannot be upgraded through a DIRECT label", () -> {
            var r = reply(); support(r).put("requirementId", "r3").put("quote", CONTEXT); global(r, "CORE_REQUIRES_PRIMARY_MECHANISM");
        });
        test("background relation on primary role is not core mechanism", () -> {
            var r = reply(); support(r).put("relation", "SUPPORTING_CONTEXT"); global(r, "CORE_REQUIRES_PRIMARY_MECHANISM");
        });
        test("non-CORE verdict with direct primary support is contradictory", () -> {
            var r = reply(); candidate(r, 0).put("verdict", "BACKGROUND_ONLY"); global(r, "VERDICT_PRIMARY_CONTRADICTION");
        });
        test("all candidates unsupported gives empty evidence and preserves local uncertainty", () -> {
            var r = reply(); candidate(r, 0).put("verdict", "NO_SUPPORT").putArray("supports");
            var d = evaluate(r); check(d.evidence().isEmpty() && !d.diagnostics().path("globalRejected").asBoolean());
            check(d.diagnostics().path("status").asText().equals("EMPTY_NO_PRIMARY_SUPPORT"));
            check(d.diagnostics().path("rejectedCandidateIds").size() == 2 && d.diagnostics().path("uncertaintySources").size() == 1);
            candidate(r, 1).put("versionAssessment", "MATCH"); check(evaluate(r).diagnostics().path("uncertaintySources").isEmpty());
        });
        test("single unsupported uncertain candidate remains rejected", () -> {
            var r = reply(); ((ArrayNode) r.path("candidates")).remove(0);
            var d = new CandidateLevelSemanticGate().validate(CONTRACT, List.of(CHUNKS.getLast()), r.toString());
            check(d.evidence().isEmpty() && !d.diagnostics().path("globalRejected").asBoolean());
        });
        test("response order cannot change admission order or candidate identity", () -> {
            String lookup = "The lookup operation follows the slot index and returns the matching handler.";
            var chunks = List.of(CHUNKS.getFirst(), chunk("background", lookup)); var r = reply();
            candidate(r, 1).put("verdict", "CORE_SUPPORT").put("versionAssessment", "MATCH");
            addSupport(candidate(r, 1), "r2", "DIRECT_MECHANISM", lookup);
            ArrayNode cs = (ArrayNode) r.path("candidates"); var first = cs.remove(0); cs.add(first);
            check(new CandidateLevelSemanticGate().validate(CONTRACT, chunks, r.toString()).evidence().equals(chunks));
        });
        test("multiple real contiguous quotations on one requirement remain valid", () -> {
            var r = reply(); addSupport(candidate(r, 0), "r1", "DIRECT_MECHANISM", "delegates to the selected handler.");
            check(evaluate(r).evidence().size() == 1);
        });
        for (String id : List.of("unknown", "mechanism")) test("unknown or duplicate candidate identity: " + id, () -> {
            var r = reply(); candidate(r, 1).put("candidateId", id); global(r, "CANDIDATE_ID");
        });
        test("candidate identity cannot borrow another candidate's quote", () -> {
            var r = reply(); candidate(r, 0).put("candidateId", "background"); candidate(r, 1).put("candidateId", "mechanism");
            global(r, "QUOTE_NOT_GROUNDED_BODY");
        });
        test("wrong question identity is global", () -> { var r = reply(); r.put("questionId", "different"); global(r, "QUESTION_ID"); });
        test("missing candidate is global", () -> { var r = reply(); ((ArrayNode) r.path("candidates")).remove(1); global(r, "ARRAY_COUNT"); });
        test("numeric candidate identity is global", () -> { var r = reply(); candidate(r, 1).put("candidateId", 2); global(r, "TEXT_candidateId"); });
        test("unknown requirement is global", () -> { var r = reply(); support(r).put("requirementId", "r9"); global(r, "UNKNOWN_REQUIREMENT"); });
        for (String field : List.of("verdict", "versionAssessment")) test("unknown enum: " + field, () -> {
            var r = reply(); candidate(r, 1).put(field, "invented"); global(r, field.equals("verdict") ? "VERDICT" : "VERSION");
        });
        test("unknown relation is global", () -> { var r = reply(); support(r).put("relation", "invented"); global(r, "RELATION"); });
        for (int level = 0; level < 3; level++) {
            int depth = level;
            test("extra field at schema level " + level, () -> {
                var r = reply(); (depth == 0 ? r : depth == 1 ? candidate(r, 1) : support(r)).put("unexpected", true); global(r, "FIELDS");
            });
        }
        test("missing reason cannot be hidden by NO_SUPPORT", () -> { var r = reply(); candidate(r, 1).remove("reason"); global(r, "FIELDS"); });
        test("non-array supports cannot be isolated", () -> { var r = reply(); candidate(r, 1).putNull("supports"); global(r, "ARRAY_COUNT"); });
        for (String quote : List.of("short", "x".repeat(401))) test("quote length boundary " + quote.length(), () -> {
            var r = reply(); support(r).put("quote", quote); global(r, "QUOTE_LENGTH");
        });
        test("duplicate JSON key is rejected before any admission", () -> global(new CandidateLevelSemanticGate().validate(CONTRACT, CHUNKS,
                reply().toString().replaceFirst("\\{", "{\"questionId\":\"duplicate\",")), "INVALID_JSON"));
        test("trailing JSON cannot be swallowed", () -> global(new CandidateLevelSemanticGate().validate(CONTRACT, CHUNKS,
                reply() + "{}"), "TRAILING_JSON"));
        for (String raw : List.of("not JSON", "null", "[]", "", "{}")) test("malformed response: " + raw, () -> {
            var d = new CandidateLevelSemanticGate().validate(CONTRACT, CHUNKS, raw);
            check(d.evidence().isEmpty() && d.diagnostics().path("globalRejected").asBoolean()
                    && !d.diagnostics().path("globalRejectionReasons").isEmpty());
        });
        test("null response explicitly fails closed", () -> global(new CandidateLevelSemanticGate().validate(CONTRACT, CHUNKS, null), "MODEL_RESPONSE_REQUIRED"));
        test("invalid caller candidate identity explicitly fails closed", () -> global(new CandidateLevelSemanticGate().validate(CONTRACT,
                List.of(chunk("", QUOTE), CHUNKS.getLast()), reply().toString()), "CANDIDATE_INPUT"));
        test("diagnostics cannot be mutated after validation", () -> {
            var d = evaluate(reply()); ((ObjectNode) d.diagnostics()).put("globalRejected", true);
            check(!d.diagnostics().path("globalRejected").asBoolean());
            try { d.evidence().clear(); throw new AssertionError(); } catch (UnsupportedOperationException expected) { /* immutable */ }
        });
        ObjectNode report = PrimarySemanticGate.JSON.createObjectNode().put("tests", passed.size() + failures.size() + errors.size())
                .put("failures", failures.size()).put("errors", errors.size()).put("skipped", 0).put("newModelCalls", 0);
        report.set("checks", PrimarySemanticGate.JSON.valueToTree(passed));
        report.set("failedChecks", PrimarySemanticGate.JSON.valueToTree(failures)); report.set("erroredChecks", PrimarySemanticGate.JSON.valueToTree(errors));
        Files.writeString(Path.of(args[0]), report.toPrettyString(), StandardOpenOption.CREATE_NEW);
        System.out.println("Candidate gate tests=" + report.path("tests") + ", failures=" + failures.size() + ", errors=" + errors.size() + ", skipped=0; model calls=0");
        if (!failures.isEmpty() || !errors.isEmpty()) throw new AssertionError(report.toPrettyString());
    }
}
