import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kun.aiinterview.question.dto.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Negative data-boundary cases, no Spring context or database connection. */
public final class D1ImportOfflineTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static Method read, validate;
    private static List<CreateQuestionRequest> old;
    private static Validator validator;
    private static int passed;
    public static void main(String[] args) throws Exception {
        read = D1QuestionBankImport.class.getDeclaredMethod("readBank", byte[].class, List.class); read.setAccessible(true);
        validate = D1QuestionBankImport.class.getDeclaredMethod("validate", List.class, Validator.class, int.class); validate.setAccessible(true);
        old = JSON.readValue(Files.readAllBytes(Path.of("docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json")), new TypeReference<>(){});
        JsonNode root = JSON.readTree(Files.readAllBytes(Path.of("docs/question-bank/D1_QUESTION_BANK_V2_20261114.json")));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
            check("valid 63 DTOs", root.deepCopy(), true);
            ObjectNode n = root.deepCopy(); first(n).put("bankId", "H01"); check("duplicate identity", n, false);
            n = root.deepCopy(); first(n).put("knowledgePoint", "unexpected_point"); check("unapproved identity mapping", n, false);
            n = root.deepCopy(); first(n).put("category", "JVM"); check("wrong category bucket", n, false);
            n = root.deepCopy(); first(n).put("difficulty", "MEDIUM"); check("wrong difficulty", n, false);
            n = root.deepCopy(); point(n).put("weight", 30.5); check("fractional weight", n, false);
            n = root.deepCopy(); point(n).put("weight", 0); check("zero weight", n, false);
            n = root.deepCopy(); point(n).put("weight", 31); check("weight total 101", n, false);
            n = root.deepCopy(); point(n).put("pointType", "UNKNOWN"); check("invalid point enum", n, false);
            n = root.deepCopy(); point(n).put("surprise", "field"); check("unknown point property", n, false);
            n = root.deepCopy(); first(n).put("referenceAnswer", ""); check("blank answer", n, false);
            n = root.deepCopy(); first(n).put("referenceAnswer", "\u4e2d".repeat(21846)); check("UTF8 TEXT overflow", n, false);
            n = root.deepCopy(); ((ArrayNode)first(n).get("scoringPoints")).set(0, NullNode.instance); check("null point", n, false);
            n = root.deepCopy(); n.put("bankVersion", "unreviewed-v3"); check("wrong version", n, false);
        }
        System.out.println("PASS offline boundary cases=" + passed + " failures=0 errors=0 skipped=0");
    }
    private static ObjectNode first(JsonNode n) { return (ObjectNode)n.path("questions").get(0); }
    private static ObjectNode point(JsonNode n) { return (ObjectNode)first(n).path("scoringPoints").get(0); }
    private static void check(String label, JsonNode node, boolean accepted) throws Exception {
        boolean success;
        try {
            Object requests = read.invoke(null, JSON.writeValueAsBytes(node), old);
            validate.invoke(null, requests, validator, 63); success = true;
        } catch (InvocationTargetException rejected) {
            Throwable cause = rejected.getCause();
            if (!cause.getClass().getName().equals("D1QuestionBankImport$GateFailure")
                    && !(cause instanceof com.fasterxml.jackson.core.JacksonException)) throw rejected;
            success = false;
        }
        if (success != accepted) throw new AssertionError(label + " unexpectedly accepted=" + success);
        passed++;
        System.out.println("PASS " + label);
    }
}
