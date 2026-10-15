import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/** Pure guards shared by offline tests and the opt-in runner. No network or database calls. */
final class RagE2ePolicy {
    static final String SOURCE = "ai_interview", TARGET = "ai_interview_test";
    static final String COLLECTION = "knowledge_chunk_vectors_test";
    static final String SOURCE_COLLECTION = "knowledge_chunk_vectors_local";
    static final String SERVER_UUID = "6ccac806-1bb1-11f1-b45f-107c61ba59a5";
    static final long SOURCE_COLLECTION_ID = 468137482006301501L;
    static final String MODEL = "qwen3.7-text-embedding";
    static final String VERSION = "qwen3.7-text-embedding-1024-dense-v1";
    static final String EMBEDDING_ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    static final double EMBEDDING_RATE_CNY_PER_M = 0.50;
    static final double SINGAPORE_EMBEDDING_RATE_CNY_PER_M = 0.525;
    static final double JUDGE_INPUT_RATE_CNY_PER_M = 2.0;
    static final double JUDGE_OUTPUT_RATE_CNY_PER_M = 8.0;
    static final String JUDGE_MODEL = "deepseek-flash";
    static final List<String> CORPUS = List.of("question", "question_scoring_point",
            "knowledge_document", "knowledge_chunk");
    static final Set<String> TABLES = Set.of("user", "question", "question_scoring_point",
            "interview_session", "interview_question", "interview_answer", "answer_evaluation",
            "knowledge_document", "knowledge_chunk", "rag_retrieval_batch", "rag_hit_log",
            "interview_report", "user_weakness");
    static final Set<String> INSERT = Set.of("user", "question", "question_scoring_point",
            "knowledge_document", "knowledge_chunk", "interview_session", "interview_question",
            "interview_answer", "answer_evaluation", "rag_retrieval_batch", "rag_hit_log");
    static final Set<String> UPDATE = Set.of("interview_session", "interview_question", "interview_answer");
    static final Set<String> MILVUS_READ = Set.of("hasCollection", "describeCollection", "describeIndex",
            "getLoadState", "query", "search", "close", "toString", "hashCode", "equals");

    static void require(boolean condition, String code) {
        if (!condition) throw new SecurityException(code);
    }
    static final class Budget {
        final AtomicInteger attempted = new AtomicInteger();
        final int max;
        Budget(int max) { this.max = max; }
        void take() { require(attempted.incrementAndGet() <= max, "REQUEST_BUDGET_EXHAUSTED"); }
    }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }
    static String hash(String text) { return hash(text.getBytes(StandardCharsets.UTF_8)); }
    static String rowHash(List<Map<String,String>> rows) {
        StringBuilder data = new StringBuilder();
        for (var row : rows) {
            for (String cell : row.values()) data.append(cell == null ? "NULL;" : cell.length()+":"+cell+";");
            data.append('\n');
        }
        return hash(data.toString());
    }
    static String jdbc(String schema) {
        require(Set.of(SOURCE,TARGET).contains(schema), "DATABASE_ALLOWLIST");
        // Fixed URL, no inherited URL/driver extension/connection-init SQL.
        return "jdbc:mysql://127.0.0.1:3306/"+schema
                +"?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                +"&connectTimeout=3000&socketTimeout=10000&allowPublicKeyRetrieval=true";
    }
    static void auditJdbc(String actual) {
        require(jdbc(TARGET).equals(actual), "TARGET_JDBC_MISMATCH");
    }
    static double embeddingRate(String endpoint) {
        endpoint(endpoint, true);
        if (endpoint.startsWith("https://dashscope.aliyuncs.com/")) return EMBEDDING_RATE_CNY_PER_M;
        if (endpoint.startsWith("https://dashscope-intl.aliyuncs.com/")) return SINGAPORE_EMBEDDING_RATE_CNY_PER_M;
        throw new SecurityException("EMBEDDING_REGION_RATE_UNKNOWN");
    }
    /** Frozen corpus rows must already agree before any test insert. */
    static void corpusProfile(List<Map<String,String>> documents, List<Map<String,String>> chunks) {
        Map<String,String> versions = new HashMap<>();
        for (var document : documents) {
            require("READY".equals(document.get("processing_status")), "DOCUMENT_NOT_READY");
            require(versions.put(document.get("id"), document.get("document_version")) == null, "DUPLICATE_DOCUMENT");
        }
        Set<String> vectorIds = new HashSet<>();
        for (var chunk : chunks) {
            String vectorId = chunk.get("vector_id");
            String documentVersion = versions.get(chunk.get("document_id"));
            require(MODEL.equals(chunk.get("embedding_model")) && VERSION.equals(chunk.get("embedding_version")),
                    "CHUNK_PROFILE_MISMATCH");
            require("ACTIVE".equals(chunk.get("status")), "CHUNK_NOT_ACTIVE");
            require(documentVersion != null && documentVersion.equals(chunk.get("document_version")),
                    "DOCUMENT_VERSION_MISMATCH");
            require(vectorId != null && !vectorId.isBlank() && vectorIds.add(vectorId), "CHUNK_VECTOR_ID_INVALID");
        }
    }
    static void endpoint(String value, boolean embedding) {
        URI uri = URI.create(value);
        require("https".equals(uri.getScheme()) && uri.getUserInfo()==null && uri.getQuery()==null
                && uri.getFragment()==null && uri.getPort()==-1, "MODEL_ENDPOINT_INVALID");
        require(embedding ? Set.of("dashscope.aliyuncs.com", "dashscope-intl.aliyuncs.com").contains(uri.getHost())
                && "/compatible-mode/v1".equals(uri.getPath())
                : "api.deepseek.com".equals(uri.getHost()) && Set.of("", "/v1").contains(uri.getPath()),
                "MODEL_ENDPOINT_NOT_OFFICIAL");
    }
    static void modelRequest(JsonNode request, boolean embedding) {
        require((embedding?MODEL:JUDGE_MODEL).equals(request.path("model").asText()), "MODEL_ID_MISMATCH");
        if (embedding) {
            require(request.path("dimensions").asInt()==1024 && "float".equals(request.path("encoding_format").asText()),
                    "EMBEDDING_DIMENSION_ENCODING");
            require(request.path("input").isArray() && request.path("input").size()==1
                    && request.path("input").get(0).isTextual(), "ONE_QUERY_ONLY");
            require(request.path("input").get(0).asText().getBytes(StandardCharsets.UTF_8).length<=8192,
                    "EMBEDDING_INPUT_BYTE_CAP");
        } else {
            require(request.path("max_tokens").asInt()>0 && request.path("max_tokens").asInt()<=4096
                    && !request.path("stream").asBoolean(true), "JUDGE_OUTPUT_CAP");
            require("disabled".equals(request.path("thinking").path("type").asText())
                    && "json_object".equals(request.path("response_format").path("type").asText()), "JUDGE_MODE");
            require(request.path("messages").isArray() && request.path("messages").size()==2, "TWO_MESSAGES_ONLY");
            int bytes = 0;
            for (var message : request.path("messages")) bytes+=message.path("content").asText().getBytes(StandardCharsets.UTF_8).length;
            require(bytes<=65536, "JUDGE_INPUT_BYTE_CAP");
        }
    }
    static Set<String> grantSet(String currentUser, String roles, String mandatory,
                                List<String> grants, boolean writer) {
        String user = currentUser.split("@",-1)[0];
        require(currentUser.matches("[a-z][a-z0-9_]{2,40}@(?:localhost|127\\.0\\.0\\.1)")
                && !Set.of("root","mysql","admin").contains(user), "RESTRICTED_DB_ACCOUNT_REQUIRED");
        require("NONE".equals(roles) && mandatory.isEmpty(), "ROLES_NOT_ALLOWED");
        require(!grants.isEmpty() && grants.size()<65, "GRANTS_REQUIRED");
        String recipient = "(?:`"+Pattern.quote(user)+"`@`(?:localhost|127\\.0\\.0\\.1)`|'"
                +Pattern.quote(user)+"'@'(?:localhost|127\\.0\\.0\\.1)')";
        Pattern pattern = Pattern.compile("GRANT ([A-Za-z`, ()]+) ON (\\*\\.\\*|`[a-z_]+`\\.`[a-z_]+`) TO "+recipient);
        Set<String> capabilities = new HashSet<>();
        for (String grant : grants) {
            var match = pattern.matcher(grant);
            require(match.matches(), "UNKNOWN_OR_BROAD_GRANT");
            String scope = match.group(2), privileges = match.group(1);
            if (privileges.equals("USAGE")) { require(scope.equals("*.*"), "USAGE_SCOPE"); continue; }
            require(!scope.equals("*.*"), "GLOBAL_PRIVILEGE_REJECTED");
            String[] names=scope.replace("`", "").split("\\.");
            require((writer?TARGET:SOURCE).equals(names[0]) && TABLES.contains(names[1]), "GRANT_SCOPE");
            for (String privilege : privileges.split(", ")) {
                if(privilege.equals("UPDATE (`id`)"))privilege="UPDATE(id)";
                require(privilege.equals("SELECT") || writer && (
                        privilege.equals("INSERT") && INSERT.contains(names[1])
                        || privilege.equals("UPDATE") && UPDATE.contains(names[1])
                        || privilege.equals("UPDATE(id)") && names[1].equals("user")), "EXCESS_DB_PRIVILEGE");
                capabilities.add(privilege+"@"+names[1]);
            }
        }
        Set<String> needed = new HashSet<>();
        // Source needs SELECT on the four corpus tables. Extra SELECT on the other source tables is allowed,
        // so the existing r08_source_ro account can still pass. Source writes cannot.
        for (String table : writer?TABLES:new HashSet<>(CORPUS)) needed.add("SELECT@"+table);
        if (writer) {
            INSERT.forEach(t->needed.add("INSERT@"+t)); UPDATE.forEach(t->needed.add("UPDATE@"+t));
            // MySQL FOR UPDATE needs a write privilege even though the workflow only locks this row.
            // Limit it to the primary key; password, role and status remain outside the grant.
            needed.add("UPDATE(id)@user");
        }
        require(capabilities.containsAll(needed), "MISSING_REQUIRED_DB_PRIVILEGE");
        return capabilities;
    }
    static void milvusMethod(String method) { require(MILVUS_READ.contains(method), "MILVUS_MUTATION_FORBIDDEN"); }
}
