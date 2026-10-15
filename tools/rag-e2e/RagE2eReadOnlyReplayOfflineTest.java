import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Mutates copies of the captured snapshot only; no live services or original artifacts. */
public final class RagE2eReadOnlyReplayOfflineTest {
    static final ObjectMapper JSON=RagE2eReadOnlyVerifier.JSON;
    static int tests;
    static Path directory;
    static JsonNode original;
    static ObjectNode first(JsonNode data,String key) { return (ObjectNode)data.path(key).get(0); }
    static void rejects(String label,String expectedCode,Consumer<JsonNode> mutate) throws Exception {
        var copy=original.deepCopy();mutate.accept(copy);
        Path input=directory.resolve(label+".negative-fixture.json");
        Files.writeString(input,JSON.writeValueAsString(copy),StandardOpenOption.CREATE_NEW);
        try {
            RagE2eReadOnlyVerifier.main(new String[]{input.toString(),directory.resolve(label+".unexpected-pass.json").toString()});
            throw new AssertionError("Expected rejection: "+label);
        } catch(SecurityException expected) {
            if(!expectedCode.equals(expected.getMessage()))throw new AssertionError("Wrong rejection for "+label+": "+expected.getMessage());
            tests++;
        }
    }
    public static void main(String[] args) throws Exception {
        original=JSON.readTree(Path.of(args[0]).toFile());directory=Path.of(args[1]);Files.createDirectory(directory);
        rejects("provider-score-changed","ORIGINAL_CONTENT_CANNOT_BE_RECOVERED_NO_NEW_CALL",d->{
            var e=first(d,"evaluations");e.put("raw_result",e.path("raw_result").asText().replace("\"correctnessScore\": 19","\"correctnessScore\": 18"));
        });
        rejects("prompt-content-changed","ORIGINAL_WIRE_PROMPT_HASH_MISMATCH",d->first(d,"hits").put("content","changed test evidence"));
        rejects("trace-answer-changed","TRACE_HYDRATION_INVALID",d->first(d,"hits").put("answer_id","78"));
        rejects("batch-link-changed","TRACE_EVALUATION_LINK_MISMATCH",d->first(d,"batches").put("retrieval_batch_id","negative-fixture"));
        rejects("total-score-changed","JAVA_SCORE_OR_DECISION_MISMATCH",d->first(d,"evaluations").put("total_score","91"));
        rejects("validated-feedback-changed","VALIDATED_FIELDS_TO_DB_MISMATCH",d->first(d,"evaluations").put("correction","changed test feedback"));
        rejects("next-question-changed","ORIGINAL_POST_RESPONSE_HASH_MISMATCH",d->first(d,"sessions").put("current_interview_question_id","135"));
        rejects("trace-hit-missing","TRACE_COUNTS_MISMATCH",d->((com.fasterxml.jackson.databind.node.ArrayNode)d.path("hits")).remove(1));
        rejects("http-detail-date-changed","ORIGINAL_GET_RESPONSE_HASH_MISMATCH",d->first(d,"sessions").put("started_at","2026-10-12 21:49:38"));
        rejects("owner-changed","RUN_IDENTITIES_MISMATCH",d->first(d,"sessions").put("user_id","360"));
        var summary=Map.of("tests",tests,"failures",0,"errors",0,"skipped",0,"modelCalls",0,"databaseWrites",0);
        Files.writeString(directory.resolve("offline-tests.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(summary),StandardOpenOption.CREATE_NEW);
        System.out.println(JSON.writeValueAsString(summary));
    }
}
