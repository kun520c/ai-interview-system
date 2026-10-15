import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.util.*;

/** Full JSON storage contract: object key order/whitespace are not persisted by MySQL JSON. */
final class RagE2eConsistency {
    private static final ObjectMapper JSON=new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    static boolean equivalent(JsonNode left,JsonNode right) {
        if(left==null || right==null)return left==right;
        // Jackson valueToTree(Long) uses LongNode; readTree(small integer) uses IntNode.
        if(left.isNumber()&&right.isNumber())return left.decimalValue().compareTo(right.decimalValue())==0;
        if(left.isObject()&&right.isObject()) {
            if(left.size()!=right.size())return false;
            for(var it=left.fields();it.hasNext();) {
                var entry=it.next();
                if(!right.has(entry.getKey())||!equivalent(entry.getValue(),right.get(entry.getKey())))return false;
            }
            return true;
        }
        if(left.isArray()&&right.isArray()) {
            if(left.size()!=right.size())return false;
            for(int i=0;i<left.size();i++)if(!equivalent(left.get(i),right.get(i)))return false;
            return true;
        }
        return left.equals(right);
    }

    static JsonNode object(String json) throws Exception {
        JsonNode node=JSON.readTree(json);
        RagE2ePolicy.require(node!=null && node.isObject(),"EVALUATION_JSON_OBJECT_REQUIRED");
        return node;
    }

    static void verifyModel(String storedJson,String providerJson,String storedModel,String providerModel,
                            List<Map<String,Object>> wireEvidence) throws Exception {
        // Verify the actual provider content before comparing its storage representation.
        RagE2ePolicy.require(wireEvidence.stream().anyMatch(e->"deepseek".equals(e.get("provider"))
                && Objects.equals(providerModel,e.get("responseModel"))
                && RagE2ePolicy.hash(providerJson).equals(e.get("rawEvaluationSha256"))),
                "WIRE_MODEL_OUTPUT_INTEGRITY_MISMATCH");
        RagE2ePolicy.require(Objects.equals(storedModel,providerModel)
                && equivalent(object(storedJson),object(providerJson)),"RAW_MODEL_TO_DB_MISMATCH");
    }
}
