import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;

/** Exercises the real runner guard on preserved continuation rows, including negative tamper cases. */
public final class FinalE2eSecondRepairOfflineTest {
    interface Checked {
        void run() throws Exception;
    }

    static final List<String> passed = new ArrayList<>();
    static void test(String name, Checked action) throws Exception {
        action.run();
        passed.add(name);
    }

    static void blocked(Checked action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected rejection");
        } catch (SecurityException expected) { }
    }

    static ObjectNode row(JsonNode snapshot, String key) {
        return (ObjectNode)snapshot.path(key).get(0);
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        var before = FinalE2eSecondRepair.read("00-resumed-existing-session.database.json");
        var after = FinalE2eSecondRepair.read("99-final-or-failure.database.json");
        test("full saved wire/prompt/Java scoring/report replay", () -> FinalE2eSecondRepair.replay());
        test("original whole-row failure reproduced", () -> {
            FinalE2eRunner.continuationSessionId = 0;
            blocked(() -> FinalE2eSecondRepair.verifySnapshotHistory(before, after));
        });
        test("actual continuation history contract passes before paid work", () -> FinalE2eSecondRepair.verifyHistoryContract());
        test("correct scope permits real successful progression", () -> {
            FinalE2eRunner.continuationSessionId = 150;
            FinalE2eSecondRepair.verifySnapshotHistory(before, after);
        });
        test("wrong session scope rejected", () -> {
            FinalE2eRunner.continuationSessionId = 151;
            blocked(() -> FinalE2eSecondRepair.verifySnapshotHistory(before, after));
        });
        FinalE2eRunner.continuationSessionId = 150;
        for (String field : List.of("user_id", "difficulty", "planned_question_count", "started_at", "created_at"))
            test("session immutable "+field, () -> {
                var tampered = after.deepCopy();
                row(tampered, "session").put(field, "tampered");
                blocked(() -> FinalE2eSecondRepair.verifySnapshotHistory(before, tampered));
            });
        for (String field : List.of("question_id", "session_id", "plan_order", "question_content", "reference_answer_snapshot", "scoring_points_snapshot"))
            test("question snapshot immutable "+field, () -> {
                var tampered = after.deepCopy();
                row(tampered, "questions").put(field, "tampered");
                blocked(() -> FinalE2eSecondRepair.verifySnapshotHistory(before, tampered));
            });
        for (String key : List.of("answers", "evaluations", "ragBatches", "ragHits")) {
            test("preserved original "+key+" still immutable", () -> {
                var tampered = after.deepCopy();
                row(tampered, key).put("created_at", "tampered");
                blocked(() -> FinalE2eSecondRepair.verifySnapshotHistory(before, tampered));
            });
            test("deleting original "+key+" rejected", () -> {
                var tampered = after.deepCopy();
                ((ArrayNode)tampered.path(key)).remove(0);
                blocked(() -> FinalE2eSecondRepair.verifySnapshotHistory(before, tampered));
            });
        }
        test("unrelated session workflow cannot change", () -> {
            var b = before.deepCopy();
            var a = after.deepCopy();
            var other = row(b, "session").deepCopy();
            other.put("id", "999");
            ((ArrayNode)b.path("session")).add(other);
            var changed = other.deepCopy();
            changed.put("status", "COMPLETED");
            ((ArrayNode)a.path("session")).add(changed);
            blocked(() -> FinalE2eSecondRepair.verifySnapshotHistory(b, a));
        });
        test("unrelated question workflow cannot change", () -> {
            var b = before.deepCopy();
            var a = after.deepCopy();
            var other = row(b, "questions").deepCopy();
            other.put("id", "999");
            other.put("session_id", "999");
            ((ArrayNode)b.path("questions")).add(other);
            var changed = other.deepCopy();
            changed.put("status", "PENDING");
            ((ArrayNode)a.path("questions")).add(changed);
            blocked(() -> FinalE2eSecondRepair.verifySnapshotHistory(b, a));
        });
        var report = FinalE2eSecondRepair.read("report.json");
        test("changed persisted score rejected by Java replay", () -> {
            var changed = after.deepCopy();
            row(changed, "evaluations").put("total_score", "8");
            blocked(() -> FinalE2eSecondRepair.verifyCompleted(changed, report));
        });
        test("invalid terminal state rejected", () -> {
            var changed = after.deepCopy();
            row(changed, "session").put("status", "IN_PROGRESS");
            blocked(() -> FinalE2eSecondRepair.verifyCompleted(changed, report));
        });
        test("report average tamper rejected", () -> {
            var changed = after.deepCopy();
            row(changed, "report").put("overall_score", "75.00");
            blocked(() -> FinalE2eSecondRepair.verifyCompleted(changed, report));
        });
        test("duplicate weakness update rejected", () -> {
            var changed = after.deepCopy();
            row(changed, "weaknesses").put("discovered_count", "2");
            blocked(() -> FinalE2eSecondRepair.verifyCompleted(changed, report));
        });
        test("RAG link tamper rejected", () -> {
            var changed = after.deepCopy();
            ((ObjectNode)changed.path("ragBatches").get(1)).put("retrieval_batch_id", "tampered");
            blocked(() -> FinalE2eSecondRepair.verifyCompleted(changed, report));
        });
        test("history mismatch identifies table and row", () -> {
            blocked(() -> FinalE2eRunner.assertHistory("interview_session", Map.of("150", "old"), Map.of("150", "new")));
            FinalE2eRunner.check(FinalE2eRunner.report.get("historyMismatch").equals(Map.of("table", "interview_session", "rowId", "150")), "HISTORY_DIAGNOSTIC");
        });
        test("all model requests blocked before transport", () -> {
            var gate = new FinalE2eGate(out, (uri, auth, body) -> {
                throw new AssertionError("Unexpected model transport");
            }, Map.of("embedding", 0, "deepseek", 0, "jev", 0), 0);
            gate.begin("answer-1");
            for (String provider : List.of("embedding", "deepseek", "jev"))
                blocked(() -> gate.admit(provider, "Bearer offline", "{}".getBytes()));
            FinalE2eRunner.check(gate.total() == 0, "OFFLINE_MODEL_COUNT");
        });
        FinalE2eRunner.report.remove("historyMismatch");
        FinalE2eRunner.save(out.resolve("second-repair-offline-tests.json"), Map.of("tests", passed.size(), "failures", 0, "errors", 0, "skipped", 0,
                "passedCases", passed, "newModelCalls", 0, "databaseWrites", 0));
        System.out.println("SECOND_REPAIR_OFFLINE_PASS: "+passed.size()+" tests; failures=0; errors=0; skipped=0; new model calls=0; database writes=0");
    }
}
