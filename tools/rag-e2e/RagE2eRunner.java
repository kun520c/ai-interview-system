import com.fasterxml.jackson.databind.*;
import com.kun.aiinterview.AiInterviewSystemApplication;
import com.kun.aiinterview.interview.evaluation.standard.EvaluationStandard;
import com.kun.aiinterview.knowledge.vector.VectorWriteItem;
import com.kun.aiinterview.knowledge.vector.milvus.*;
import io.milvus.v2.client.*;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.shaded.io.grpc.StatusException;
import io.milvus.shaded.io.grpc.StatusRuntimeException;
import org.springframework.boot.*;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** One opt-in process: isolated data admission, real Spring Boot HTTP, evidence verification. */
public final class RagE2eRunner {
    static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
    static final Path BASE=Path.of("tools/rag-e2e"), OUT=Path.of("target/rag-e2e/run-once");
    static final String RECOVERY_RUN_ID="rag-e2e-attempt-003-20261012";
    static Path outputDirectory=OUT, executionLock=Path.of("target/rag-e2e/EXECUTION_CONSUMED.lock");
    static boolean recoveryAttempt;
    static JsonNode recoveryPlan;
    static final String CONTRACT_SHA="308862b7f4f8bcd97d8b0a3d62701cae5678492dda283cdecba648d583acdac8";
    static final List<String> REQUIRED=List.of("E2E_DB_USERNAME","E2E_DB_PASSWORD",
            "E2E_SOURCE_DB_USERNAME","E2E_SOURCE_DB_PASSWORD","EMBEDDING_API_KEY","DEEPSEEK_API_KEY");
    static JsonNode contract;
    static final Map<String,Object> report=new LinkedHashMap<>();
    static final List<Map<String,Object>> businessHttp=new ArrayList<>();
    static final RagE2ePolicy.Budget submissions=new RagE2ePolicy.Budget(1);
    static final Map<String,List<Map<String,String>>> sourceRows=new LinkedHashMap<>();
    static String requestId, baseUrl, token, candidatePassword;
    static long sessionId, questionId, userId;
    static JsonNode submitResponse, terminalDetail;
    static int submitHttpStatus;
    static String stage="OFFLINE";

    static String env(String name) { String value=System.getenv(name); return value==null?"":value; }
    static void check(boolean condition,String code) { RagE2ePolicy.require(condition,code); }
    static String safeGrpcDescription(String description,List<String> secrets) {
        if(description==null)return null;
        for(String secret:secrets)if(secret!=null && !secret.isBlank())description=description.replace(secret,"[REDACTED]");
        return description.replaceAll("(?i)(authorization\\s*[:=]\\s*(?:bearer\\s+|basic\\s+)?)[^\\s,;]+","$1[REDACTED]")
                .replaceAll("(?i)((?:api[_-]?key|password|token)\\s*[:=]\\s*)[^\\s,;]+","$1[REDACTED]");
    }
    static Map<String,Object> failureDetails(Throwable error) {
        var details=new LinkedHashMap<String,Object>();
        details.put("exceptionClass",error.getClass().getName());
        details.put("operation",report.get("currentOperation"));
        var secrets=new ArrayList<>(REQUIRED.stream().map(RagE2eRunner::env).toList());
        secrets.add(env("E2E_MILVUS_TOKEN")); secrets.add(candidatePassword); secrets.add(token);
        List<Map<String,Object>> chain=new ArrayList<>();
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable cause=error;cause!=null && seen.add(cause);cause=cause.getCause()) {
            chain.add(Map.of("exceptionClass",cause.getClass().getName(),
                    "stackTrace",Arrays.stream(cause.getStackTrace()).map(StackTraceElement::toString).toList()));
            var status=cause instanceof StatusRuntimeException grpc?grpc.getStatus():
                    cause instanceof StatusException grpc?grpc.getStatus():null;
            if(status!=null && !details.containsKey("grpcStatus")) {
                details.put("grpcStatus",status.getCode().name());
                details.put("grpcDescription",safeGrpcDescription(status.getDescription(),secrets));
            }
            if(cause instanceof SQLException sql && !details.containsKey("sqlErrorCode")) {
                details.put("sqlErrorCode",sql.getErrorCode());
                details.put("sqlState",sql.getSQLState()==null?"UNKNOWN":sql.getSQLState());
            }
        }
        details.put("causeChain",chain); return details;
    }
    static void writeReport() throws Exception {
        Files.createDirectories(outputDirectory);
        JSON.writerWithDefaultPrettyPrinter().writeValue(outputDirectory.resolve("report.json").toFile(),report);
        Files.writeString(outputDirectory.resolve("report.md"),"E2E = "+report.get("E2E")+"\n\n"
                +"Stage: "+stage+"\n\nEvidence: report.json; no credentials, answer or model prose exported.\n"
                +"\nNo automatic resubmission or whole-flow retry.\n",StandardCharsets.UTF_8);
    }
    static void loadContract() throws Exception {
        byte[] bytes=Files.readAllBytes(BASE.resolve("contract.json"));
        check(RagE2ePolicy.hash(bytes).equals(CONTRACT_SHA),"CONTRACT_SEAL_MISMATCH");
        contract=JSON.readTree(bytes);
    }
    static List<String> missingEnvironment() {
        var required=new ArrayList<>(REQUIRED);
        return required.stream().filter(n->!recoveryAttempt || !n.startsWith("E2E_SOURCE_"))
                .filter(n->env(n).isBlank()).toList();
    }
    static void loadRecoveryPlan(String runId) throws Exception {
        check(RECOVERY_RUN_ID.equals(runId),"UNREVIEWED_RECOVERY_RUN_ID");
        recoveryPlan=JSON.readTree(Files.readAllBytes(BASE.resolve("recovery-plan.json")));
        check(runId.equals(recoveryPlan.path("runId").asText())
                && "REGISTER_NEW_USER".equals(recoveryPlan.path("authMode").asText())
                && "RAG_E2E_RECOVERY_REGISTER_NEW_USER_REUSE_ONE_SESSION_ONE_ANSWER"
                    .equals(recoveryPlan.path("authorization").asText())
                && recoveryPlan.path("corpusWritesAllowed").isBoolean() && !recoveryPlan.path("corpusWritesAllowed").asBoolean()
                && recoveryPlan.path("milvusWritesAllowed").isBoolean() && !recoveryPlan.path("milvusWritesAllowed").asBoolean()
                && recoveryPlan.path("maxAnswerPosts").asInt()==1 && recoveryPlan.path("maxEmbeddingHttp").asInt()==1
                && recoveryPlan.path("maxDeepSeekHttp").asInt()==1
                && recoveryPlan.path("expectedUserIds").equals(JSON.valueToTree(List.of(360)))
                && "469534467320352840".equals(recoveryPlan.path("expectedCollectionId").asText()),"RECOVERY_PLAN_MISMATCH");
        var evidence=recoveryPlan.path("previousEvidence");
        check(evidence.isObject() && evidence.size()==4,"RECOVERY_HISTORY_REQUIRED");
        for(var entries=evidence.fields();entries.hasNext();) {
            var entry=entries.next();
            check(Set.of("target/rag-e2e/EXECUTION_CONSUMED.lock","target/rag-e2e/run-once/report.json",
                    "target/rag-e2e/run-once/report.md","target/rag-e2e/failure-diagnosis-20261012/review.md")
                    .contains(entry.getKey()),"RECOVERY_HISTORY_PATH");
            check(RagE2ePolicy.hash(Files.readAllBytes(Path.of(entry.getKey()))).equals(entry.getValue().asText()),
                    "RECOVERY_HISTORY_CHANGED");
        }
    }
    static Path reserveRecoveryAttempt(Path attemptsRoot,String runId) throws Exception {
        check(RECOVERY_RUN_ID.equals(runId),"UNREVIEWED_RECOVERY_RUN_ID");
        Path root=attemptsRoot.toAbsolutePath().normalize(), attempt=root.resolve(runId).normalize();
        check(attempt.getParent().equals(root),"RECOVERY_OUTPUT_PATH");
        Files.createDirectories(root);
        // Atomic reservation before any service access. Even a preflight failure cannot reuse this ID.
        Files.createDirectory(attempt);
        Files.writeString(attempt.resolve("ATTEMPT_RESERVED.lock"),Instant.now().toString(),StandardOpenOption.CREATE_NEW);
        return attempt;
    }
    static void guardLegacyAttempt() {
        check(!Files.exists(Path.of("target/rag-e2e/EXECUTION_CONSUMED.lock")) && !Files.exists(OUT),
                "LEGACY_EXECUTION_ALREADY_CONSUMED");
    }
    static void offline() throws Exception {
        loadContract();
        Map<String,Object> status=new LinkedHashMap<>();
        status.put("E2E_PREP","BLOCKED"); status.put("offlineAssets","READY");
        status.put("missingProcessEnvironment",missingEnvironment());
        status.put("unverifiedOnlineGates",List.of("NONROOT_ACCOUNT_GRANTS","TEST_SCHEMA_SHAPE",
                "TEST_CORPUS_REUSE_OR_MATCHING_SUBSET","TEST_COLLECTION_52_MAPPING_AND_BOUNDED"));
        status.put("profiles",List.of("test-real","rag-p1")); status.put("schema",RagE2ePolicy.TARGET);
        status.put("collection",RagE2ePolicy.COLLECTION); status.put("modelHttpMax",Map.of("embedding",1,"deepseek",1,"jev",0));
        status.put("liveCalls",0); status.put("liveWrites",0);
        status.put("recoveryRunId",RECOVERY_RUN_ID);
        status.put("recoveryRequiresNewAuthorization",true);
        Files.createDirectories(Path.of("target/rag-e2e"));
        JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/rag-e2e/prep-status.json").toFile(),status);
        System.out.println(JSON.writeValueAsString(status));
    }
    static void priceGate() {
        double embedding=RagE2ePolicy.embeddingRate(RagE2ePolicy.EMBEDDING_ENDPOINT);
        double input=RagE2ePolicy.JUDGE_INPUT_RATE_CNY_PER_M;
        double output=RagE2ePolicy.JUDGE_OUTPUT_RATE_CNY_PER_M;
        check(embedding==0.50 && input==2.0 && output==8.0,"OFFICIAL_RATE_MISMATCH");
        // Byte ceilings are converted as if each byte were a token. This overstates the bill.
        // It is only a preflight tripwire. Provider account quota is the monetary hard limit.
        double estimatedMax=(8320*embedding+66048*input+4096*output)/1_000_000d;
        check(Double.isFinite(estimatedMax) && estimatedMax<=1.0,"ESTIMATED_COST_ABOVE_ONE_CNY");
        var cost=new LinkedHashMap<String,Object>();
        cost.put("embeddingRegion","cn-beijing");
        cost.put("embeddingHost","dashscope.aliyuncs.com");
        cost.put("embeddingRateCnyPerM",embedding);
        cost.put("judgeModel",RagE2ePolicy.JUDGE_MODEL);
        cost.put("judgeInputRateCnyPerM",input);
        cost.put("judgeOutputRateCnyPerM",output);
        cost.put("judgePriceBasis","peak cache-miss input and peak output");
        cost.put("conservativeEstimateCny",estimatedMax);
        cost.put("maxModelHttp",Map.of("embedding",1,"deepseek",1));
        cost.put("maxCompletionTokens",4096);
        cost.put("embeddingInputBytesMax",8192);
        cost.put("judgeInputBytesMax",65536);
        cost.put("providerBillingHardCapVerified",false);
        cost.put("estimateIsAccountDebitCap",false);
        cost.put("ratesSource","official list price pinned for cn-beijing and DeepSeek peak");
        report.put("cost",cost);
    }
    static Connection connection(boolean writer) throws Exception {
        report.put("currentOperation",writer?"MYSQL_TARGET_CONNECT":"MYSQL_SOURCE_CONNECT");
        return DriverManager.getConnection(RagE2ePolicy.jdbc(writer?RagE2ePolicy.TARGET:RagE2ePolicy.SOURCE),
                env(writer?"E2E_DB_USERNAME":"E2E_SOURCE_DB_USERNAME"),
                env(writer?"E2E_DB_PASSWORD":"E2E_SOURCE_DB_PASSWORD"));
    }
    static List<Map<String,String>> select(Connection connection,String sql,Object... parameters) throws Exception {
        check(sql.startsWith("SELECT ") || sql.equals("SHOW GRANTS FOR CURRENT_USER()"),"SELECT_ALLOWLIST");
        try(var statement=connection.prepareStatement(sql)) {
            statement.setQueryTimeout(8); statement.setMaxRows(1001);
            for(int i=0;i<parameters.length;i++)statement.setObject(i+1,parameters[i]);
            try(var result=statement.executeQuery()) {
                List<Map<String,String>> rows=new ArrayList<>(); var columns=result.getMetaData();
                while(result.next()) {
                    check(rows.size()<1000,"SQL_ROW_CAP"); Map<String,String> row=new LinkedHashMap<>();
                    for(int i=1;i<=columns.getColumnCount();i++)row.put(columns.getColumnLabel(i),result.getString(i));
                    rows.add(row);
                }
                return rows;
            }
        }
    }
    static void auditConnection(Connection c,boolean writer) throws Exception {
        report.put("currentOperation",writer?"MYSQL_TARGET_READ_ONLY_AUDIT":"MYSQL_SOURCE_READ_ONLY_AUDIT");
        var identity=select(c,"SELECT DATABASE() AS db, CURRENT_USER() AS account, CURRENT_ROLE() AS roles, "
                +"@@mandatory_roles AS mandatory, @@server_uuid AS uuid").getFirst();
        check((writer?RagE2ePolicy.TARGET:RagE2ePolicy.SOURCE).equals(identity.get("db"))
                && RagE2ePolicy.SERVER_UUID.equals(identity.get("uuid")),"MYSQL_IDENTITY_MISMATCH");
        var grants=select(c,"SHOW GRANTS FOR CURRENT_USER()").stream().map(r->r.values().iterator().next()).toList();
        var privileges=RagE2ePolicy.grantSet(identity.get("account"),identity.get("roles"),identity.get("mandatory"),grants,writer);
        report.put(writer?"writerPrivilegeAudit":"sourcePrivilegeAudit",Map.of("status","PASS",
                "schema",identity.get("db"),"capabilities",privileges.stream().sorted().toList(),"rawGrantsSaved",false));
        Set<String> required=writer?RagE2ePolicy.TABLES:new HashSet<>(RagE2ePolicy.CORPUS);
        var shapes=select(c,"SELECT TABLE_NAME AS t, ENGINE AS engine, TABLE_TYPE AS kind FROM information_schema.TABLES "
                +"WHERE TABLE_SCHEMA=DATABASE()");
        var visible=shapes.stream().filter(r->required.contains(r.get("t"))).toList();
        check(visible.size()==required.size()
                && visible.stream().allMatch(r->"InnoDB".equals(r.get("engine"))&&"BASE TABLE".equals(r.get("kind"))),
                writer?"THIRTEEN_BASE_TABLES_REQUIRED":"SOURCE_CORPUS_TABLES_REQUIRED");
        var columns=select(c,"SELECT TABLE_NAME AS `table`, COLUMN_NAME AS `column`, COLUMN_TYPE AS `type`, "
                +"IS_NULLABLE AS nullable, COLUMN_KEY AS `key`, EXTRA AS extra FROM information_schema.COLUMNS "
                +"WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION");
        var expectedColumns=JSON.createArrayNode();
        contract.path("sqlColumns").forEach(column->{ if(required.contains(column.path("table").asText())) expectedColumns.add(column); });
        check(JSON.valueToTree(columns.stream().filter(r->required.contains(r.get("table"))).toList()).equals(expectedColumns),
                writer?"FROZEN_TABLE_COLUMNS_REQUIRED":"SOURCE_CORPUS_COLUMNS_REQUIRED");
        if(!writer) {
            var sourceCross=select(c,"SELECT COUNT(*) AS n FROM information_schema.KEY_COLUMN_USAGE "
                    +"WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('question','question_scoring_point',"
                    +"'knowledge_document','knowledge_chunk') AND REFERENCED_TABLE_NAME IS NOT NULL "
                    +"AND REFERENCED_TABLE_SCHEMA<>DATABASE()");
            check(sourceCross.getFirst().get("n").equals("0"),"SOURCE_CROSS_SCHEMA_FK_REJECTED");
            return;
        }
        var cross=select(c,"SELECT COUNT(*) AS n FROM information_schema.KEY_COLUMN_USAGE "
                +"WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_SCHEMA<>DATABASE()");
        check(cross.getFirst().get("n").equals("0"),"CROSS_SCHEMA_FK_REJECTED");
        var foreignKeys=select(c,"SELECT TABLE_NAME AS `table`, COLUMN_NAME AS `column`, "
                +"REFERENCED_TABLE_NAME AS parent, REFERENCED_COLUMN_NAME AS parentColumn "
                +"FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() "
                +"AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME,COLUMN_NAME");
        check(JSON.valueToTree(foreignKeys.stream().filter(r->RagE2ePolicy.TABLES.contains(r.get("table"))).toList())
                .equals(contract.path("sqlForeignKeys")),"FROZEN_FK_GRAPH_REQUIRED");
    }
    static List<String> columns(String table) {
        check(RagE2ePolicy.CORPUS.contains(table),"CORPUS_TABLE_ONLY");
        List<String> columns=new ArrayList<>();
        contract.path("sqlColumns").forEach(c->{ if(table.equals(c.path("table").asText()))columns.add(c.path("column").asText()); });
        check(!columns.isEmpty() && columns.stream().allMatch(c->c.matches("[a-z_]+")),"COLUMN_ALLOWLIST");
        return columns;
    }
    static List<Map<String,String>> corpus(Connection c,String table) throws Exception {
        return select(c,"SELECT "+columns(table).stream().map(n->"`"+n+"`").collect(Collectors.joining(","))
                +" FROM `"+table+"` ORDER BY id LIMIT 1001");
    }
    static void readSource(Connection c) throws Exception {
        report.put("currentOperation","MYSQL_FROZEN_SOURCE_READ");
        try(var statement=c.createStatement()) { statement.execute("SET SESSION TRANSACTION READ ONLY"); }
        c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); c.setAutoCommit(false);
        check(select(c,"SELECT @@session.transaction_read_only AS ro").getFirst().get("ro").equals("1"),"SOURCE_READ_ONLY_REQUIRED");
        for(String table:RagE2ePolicy.CORPUS) {
            var rows=corpus(c,table); var expected=contract.path("mysqlFrozen").path(table);
            check(rows.size()==expected.path("count").asInt()
                    && RagE2ePolicy.rowHash(rows).equals(expected.path("rowSnapshotSha256").asText()),"FROZEN_SOURCE_ROW_MISMATCH");
            sourceRows.put(table,rows);
        }
        // Verify the existing EASY plan is possible before any target INSERT/model request.
        var easy=sourceRows.get("question").stream().filter(q->"EASY".equals(q.get("difficulty"))&&"ENABLED".equals(q.get("status"))).toList();
        check(easy.size()>=5 && easy.stream().map(q->q.get("category")).collect(Collectors.toSet())
                .containsAll(Set.of("JAVA_BASIC","JAVA_COLLECTION","SPRING","MYSQL","NETWORK")),"EASY_PLAN_CORPUS_INCOMPLETE");
        RagE2ePolicy.corpusProfile(sourceRows.get("knowledge_document"),sourceRows.get("knowledge_chunk"));
        report.put("sourceCorpus",Map.of("status","FROZEN_MATCH","rows",191,"chunks",52,"readOnlyTransaction",true,
                "profile",RagE2ePolicy.VERSION,"documentVersionAligned",true));
    }
    static Map<String,Map<String,String>> byId(List<Map<String,String>> rows) {
        Map<String,Map<String,String>> result=new HashMap<>();
        for(var row:rows)check(result.put(row.get("id"),row)==null,"DUPLICATE_CORPUS_ID"); return result;
    }
    static void copyMissingCorpus(Connection target) throws Exception {
        check(!recoveryAttempt,"RECOVERY_CORPUS_WRITE_FORBIDDEN");
        Map<String,List<Map<String,String>>> missing=new LinkedHashMap<>();
        target.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); target.setAutoCommit(false);
        try {
            // Check ALL tables before first write. No overwrites, DELETE, DDL or INSERT IGNORE.
            for(String table:RagE2ePolicy.CORPUS) {
                var source=byId(sourceRows.get(table)); var existing=byId(corpus(target,table));
                for(var entry:existing.entrySet())check(entry.getValue().equals(source.get(entry.getKey())),"TEST_CORPUS_CONFLICT_STOP");
                missing.put(table,sourceRows.get(table).stream().filter(r->!existing.containsKey(r.get("id"))).toList());
            }
            for(String table:RagE2ePolicy.CORPUS) {
                var names=columns(table);
                String sql="INSERT INTO `"+table+"` ("+names.stream().map(n->"`"+n+"`").collect(Collectors.joining(","))
                        +") VALUES ("+String.join(",",Collections.nCopies(names.size(),"?"))+")";
                try(var statement=target.prepareStatement(sql)) {
                    for(var row:missing.get(table)) {
                        for(int i=0;i<names.size();i++)statement.setString(i+1,row.get(names.get(i)));
                        check(statement.executeUpdate()==1,"CORPUS_INSERT_AFFECTED_ROWS");
                    }
                }
            }
            for(String table:RagE2ePolicy.CORPUS)check(RagE2ePolicy.rowHash(corpus(target,table))
                    .equals(contract.path("mysqlFrozen").path(table).path("rowSnapshotSha256").asText()),"TEST_COPY_READBACK_MISMATCH");
            target.commit();
            report.put("testCorpusInserted",missing.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,e->e.getValue().size())));
        } catch(Exception error) { target.rollback(); throw error; }
        finally { target.setAutoCommit(true); }
    }
    static MilvusProperties properties(String collection) {
        var properties=new MilvusProperties(); properties.setEnabled(true);
        properties.setUri(URI.create("http://127.0.0.1:19530")); properties.setDatabaseName("default");
        properties.setCollectionName(collection); properties.setDimension(1024);
        properties.setConnectTimeout(Duration.ofSeconds(5)); properties.setRequestTimeout(Duration.ofSeconds(20));
        properties.setToken(env("E2E_MILVUS_TOKEN")); return properties;
    }
    static MilvusClientV2 milvus() {
        var config=ConnectConfig.builder().uri("http://127.0.0.1:19530").dbName("default")
                .connectTimeoutMs(5000).rpcDeadlineMs(20000);
        if(!env("E2E_MILVUS_TOKEN").isBlank())config.token(env("E2E_MILVUS_TOKEN"));
        // SDK 2.6.20 performs Connect and then ListDatabases inside its constructor.
        stage="MILVUS_CONNECT_ADMISSION"; report.put("currentOperation","MilvusClientV2.<init>");
        var client=new MilvusClientV2(config.build()); client.retryConfig(RetryConfig.builder().maxRetryTimes(1).build());
        return client;
    }
    static List<TreeMap<String,Object>> vectors(MilvusClientV2 client,String collection) {
        return vectors(client,collection,ConsistencyLevel.STRONG);
    }
    static List<TreeMap<String,Object>> vectors(MilvusClientV2 client,String collection,ConsistencyLevel consistency) {
        report.put("currentOperation","MilvusClientV2.query:"+collection);
        var response=client.query(QueryReq.builder().collectionName(collection).filter("")
                .outputFields(List.of("vector_id","document_id","chunk_index","embedding_version","vector"))
                .consistencyLevel(consistency).limit(53).build());
        check(response!=null && response.getQueryResults()!=null,"VECTOR_QUERY_EMPTY_RESPONSE");
        var rows=response.getQueryResults().stream().map(r->new TreeMap<>(r.getEntity()))
                .sorted(Comparator.comparing(r->r.get("vector_id").toString())).toList();
        check(rows.size()<=52,"TEST_OR_SOURCE_VECTOR_EXTRA_ROWS"); return rows;
    }
    static String vectorHash(List<TreeMap<String,Object>> rows) throws Exception {
        return RagE2ePolicy.hash(JSON.writeValueAsBytes(rows));
    }
    static List<TreeMap<String,Object>> readFrozenSource(MilvusClientV2 client) throws Exception {
        stage="SOURCE_VECTOR_READ";
        report.put("currentOperation","MilvusClientV2.describeCollection:"+RagE2ePolicy.SOURCE_COLLECTION);
        var source=client.describeCollection(DescribeCollectionReq.builder().collectionName(RagE2ePolicy.SOURCE_COLLECTION).build());
        check(source.getCollectionID()==RagE2ePolicy.SOURCE_COLLECTION_ID,"SOURCE_COLLECTION_ID_MISMATCH");
        report.put("currentOperation","MilvusClientV2.getLoadState:"+RagE2ePolicy.SOURCE_COLLECTION);
        check(Boolean.TRUE.equals(client.getLoadState(GetLoadStateReq.builder().collectionName(RagE2ePolicy.SOURCE_COLLECTION).build())),
                "SOURCE_COLLECTION_NOT_LOADED");
        var frozen=vectors(client,RagE2ePolicy.SOURCE_COLLECTION);
        check(frozen.size()==52 && vectorHash(frozen).equals(contract.path("milvusFrozen").path("fullVectorRowsSha256").asText()),
                "FROZEN_SOURCE_VECTOR_SHA_MISMATCH");
        Map<String,Map<String,String>> chunks=new HashMap<>();
        sourceRows.get("knowledge_chunk").forEach(c->chunks.put(c.get("vector_id"),c));
        for(var vector:frozen) {
            var chunk=chunks.get(vector.get("vector_id").toString());
            check(chunk!=null && chunk.get("document_id").equals(vector.get("document_id").toString())
                    && chunk.get("chunk_index").equals(vector.get("chunk_index").toString())
                    && RagE2ePolicy.VERSION.equals(chunk.get("embedding_version"))
                    && RagE2ePolicy.VERSION.equals(vector.get("embedding_version").toString()),"SOURCE_SQL_VECTOR_MAPPING");
        }
        var vectorIds=frozen.stream().map(vector->vector.get("vector_id").toString()).collect(Collectors.toSet());
        check(chunks.size()==52 && vectorIds.equals(chunks.keySet()),"VECTOR_ID_SET_MISMATCH");
        return frozen;
    }
    static void prepareVectors(MilvusClientV2 client, List<TreeMap<String,Object>> frozen) throws Exception {
        check(!recoveryAttempt,"RECOVERY_MILVUS_WRITE_FORBIDDEN");
        stage="VECTOR_COPY_ADMISSION";
            // Only this exact test collection may be created/loaded/inserted into, after explicit authorization.
            new MilvusCollectionInitializer(client,properties(RagE2ePolicy.COLLECTION)).run(new DefaultApplicationArguments());
            var target=client.describeCollection(DescribeCollectionReq.builder().collectionName(RagE2ePolicy.COLLECTION).build());
            check(target.getConsistencyLevel()==ConsistencyLevel.BOUNDED,"TEST_COLLECTION_REQUIRES_BOUNDED");
            RagE2eAudit.targetCollectionId=target.getCollectionID();
            if(!Boolean.TRUE.equals(client.getLoadState(GetLoadStateReq.builder().collectionName(RagE2ePolicy.COLLECTION).build())))
                client.loadCollection(LoadCollectionReq.builder().collectionName(RagE2ePolicy.COLLECTION).build());
            var existing=vectors(client,RagE2ePolicy.COLLECTION);
            Map<String,TreeMap<String,Object>> sourceById=new HashMap<>(); frozen.forEach(v->sourceById.put(v.get("vector_id").toString(),v));
            Set<String> present=new HashSet<>();
            for(var row:existing) {
                String id=row.get("vector_id").toString();
                check(JSON.valueToTree(row).equals(JSON.valueToTree(sourceById.get(id)))&&present.add(id),"TEST_VECTOR_CONFLICT_STOP");
            }
            List<VectorWriteItem> missing=new ArrayList<>();
            for(var row:frozen) if(!present.contains(row.get("vector_id").toString())) {
                var values=((List<?>)row.get("vector")).stream().map(v->((Number)v).floatValue()).toList();
                missing.add(new VectorWriteItem(row.get("vector_id").toString(),((Number)row.get("document_id")).longValue(),
                        ((Number)row.get("chunk_index")).intValue(),RagE2ePolicy.VERSION,values));
            }
            if(!missing.isEmpty())new MilvusVectorStoreClient(client,properties(RagE2ePolicy.COLLECTION)).insert(missing);
            // Condition polling of target Bounded visibility; never retries insertion or uses Strong business Search.
            long deadline=System.nanoTime()+Duration.ofSeconds(25).toNanos();
            List<TreeMap<String,Object>> readback;
            int observations=0;
            do {
                readback=vectors(client,RagE2ePolicy.COLLECTION,ConsistencyLevel.BOUNDED); observations++;
                if(readback.size()==52)break;
                Thread.sleep(500); // bounded observation polling; never repeats insert
            } while(System.nanoTime()<deadline && observations<10);
            check(readback.size()==52 && vectorHash(readback).equals(vectorHash(frozen)),"TEST_52_VECTOR_READBACK_MISMATCH");
        report.put("testVectors",Map.of("collection",RagE2ePolicy.COLLECTION,"collectionId",RagE2eAudit.targetCollectionId,
                "copied",missing.size(),"entities",52,"mappingVerified",true,"defaultConsistency","BOUNDED",
                "boundedAdmissionQueries",observations));
    }
    static void verifyReusableCorpus(Connection target) throws Exception {
        stage="RECOVERY_READ_ONLY_ADMISSION"; report.put("currentOperation","MYSQL_RECOVERY_CORPUS_READ");
        try(var statement=target.createStatement()) { statement.execute("SET SESSION TRANSACTION READ ONLY"); }
        target.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); target.setAutoCommit(false);
        try {
            for(String table:RagE2ePolicy.CORPUS) {
                var rows=corpus(target,table); var expected=contract.path("mysqlFrozen").path(table);
                check(rows.size()==expected.path("count").asInt()
                        && RagE2ePolicy.rowHash(rows).equals(expected.path("rowSnapshotSha256").asText()),
                        "RECOVERY_CORPUS_MISSING_OR_CHANGED");
                sourceRows.put(table,rows);
            }
            RagE2ePolicy.corpusProfile(sourceRows.get("knowledge_document"),sourceRows.get("knowledge_chunk"));
            var users=select(target,"SELECT id,account,role,status FROM `user` ORDER BY id LIMIT 1001");
            check(users.size()==1 && "360".equals(users.getFirst().get("id"))
                    && users.getFirst().get("account").matches("e2e_[a-f0-9]{20}")
                    && "USER".equals(users.getFirst().get("role")) && "ENABLED".equals(users.getFirst().get("status")),
                    "RECOVERY_BUSINESS_BASELINE_CHANGED");
            Map<String,Integer> businessCounts=new TreeMap<>();
            for(String table:RagE2ePolicy.TABLES) if(!RagE2ePolicy.CORPUS.contains(table) && !table.equals("user")) {
                int count=Integer.parseInt(select(target,"SELECT COUNT(*) AS n FROM `"+table+"`").getFirst().get("n"));
                check(count==0,"RECOVERY_EXISTING_BUSINESS_RECORDS_STOP"); businessCounts.put(table,count);
            }
            report.put("recoveryBusinessBaseline",Map.of("preservedUserIds",List.of(360),"businessCounts",businessCounts,
                    "authMode","REGISTER_NEW_USER","sourceAccess",false));
            report.put("testCorpus",Map.of("status","REUSED_FROZEN_MATCH","rows",191,"chunks",52,"inserted",0));
        } finally { target.rollback(); target.setAutoCommit(true); }
    }
    static void verifyReusableVectors(MilvusClientV2 client) throws Exception {
        stage="RECOVERY_VECTOR_READ_ONLY_ADMISSION";
        report.put("currentOperation","MilvusClientV2.describeCollection:"+RagE2ePolicy.COLLECTION);
        check(Boolean.TRUE.equals(client.hasCollection(HasCollectionReq.builder().collectionName(RagE2ePolicy.COLLECTION).build())),
                "RECOVERY_TEST_COLLECTION_MISSING");
        var desc=client.describeCollection(DescribeCollectionReq.builder().collectionName(RagE2ePolicy.COLLECTION).build());
        check(Long.toString(desc.getCollectionID()).equals(recoveryPlan.path("expectedCollectionId").asText())
                && desc.getConsistencyLevel()==ConsistencyLevel.BOUNDED,"RECOVERY_COLLECTION_ID_OR_BOUNDED_MISMATCH");
        RagE2eAudit.targetCollectionId=desc.getCollectionID();
        check(Boolean.TRUE.equals(client.getLoadState(GetLoadStateReq.builder().collectionName(RagE2ePolicy.COLLECTION).build())),
                "RECOVERY_COLLECTION_NOT_LOADED");
        var rows=vectors(client,RagE2ePolicy.COLLECTION,ConsistencyLevel.BOUNDED);
        check(rows.size()==52 && vectorHash(rows).equals(contract.path("milvusFrozen").path("fullVectorRowsSha256").asText()),
                "RECOVERY_VECTOR_MISSING_OR_CHANGED");
        var chunks=sourceRows.get("knowledge_chunk").stream().collect(Collectors.toMap(r->r.get("vector_id"),r->r));
        for(var vector:rows) {
            var chunk=chunks.get(vector.get("vector_id").toString());
            check(chunk!=null && chunk.get("document_id").equals(vector.get("document_id").toString())
                    && chunk.get("chunk_index").equals(vector.get("chunk_index").toString())
                    && chunk.get("embedding_version").equals(vector.get("embedding_version").toString()),"RECOVERY_VECTOR_MAPPING");
        }
        report.put("testVectors",Map.of("collection",RagE2ePolicy.COLLECTION,"collectionId",desc.getCollectionID(),
                "copied",0,"entities",52,"mappingVerified",true,"defaultConsistency","BOUNDED","boundedAdmissionQueries",1));
    }
    static ConfigurableApplicationContext boot(RagE2eModelGate gate) {
        stage="SPRING_BOOT";
        report.put("currentOperation","SPRING_BOOT");
        Map<String,Object> fixed=new LinkedHashMap<>();
        fixed.put("spring.profiles.active","test-real,rag-p1");
        fixed.put("spring.config.location","classpath:/,file:tools/rag-e2e/config/");
        fixed.put("spring.datasource.url",RagE2ePolicy.jdbc(RagE2ePolicy.TARGET));
        fixed.put("spring.datasource.username",env("E2E_DB_USERNAME")); fixed.put("spring.datasource.password",env("E2E_DB_PASSWORD"));
        fixed.put("spring.datasource.hikari.jdbc-url",RagE2ePolicy.jdbc(RagE2ePolicy.TARGET));
        fixed.put("spring.datasource.hikari.maximum-pool-size",2);
        fixed.put("spring.datasource.hikari.connection-init-sql","");
        fixed.put("spring.sql.init.mode","never"); fixed.put("server.address","127.0.0.1"); fixed.put("server.port",0);
        fixed.put("embedding.base-url",gate.base(true)); fixed.put("embedding.api-key",env("EMBEDDING_API_KEY"));
        fixed.put("embedding.model",RagE2ePolicy.MODEL); fixed.put("embedding.dimension",1024);
        fixed.put("embedding.profile-version",RagE2ePolicy.VERSION); fixed.put("embedding.batch-size",1);
        fixed.put("embedding.connect-timeout","3s"); fixed.put("embedding.read-timeout","20s");
        fixed.put("deepseek.enabled",true); fixed.put("deepseek.base-url",gate.base(false));
        fixed.put("deepseek.api-key",env("DEEPSEEK_API_KEY")); fixed.put("deepseek.model",RagE2ePolicy.JUDGE_MODEL);
        fixed.put("deepseek.max-tokens",4096); fixed.put("deepseek.connect-timeout","3s"); fixed.put("deepseek.read-timeout","70s");
        fixed.put("ai.judge.provider","deepseek"); fixed.put("milvus.enabled",true);
        fixed.put("milvus.uri","http://127.0.0.1:19530"); fixed.put("milvus.database-name","default");
        fixed.put("milvus.collection-name",RagE2ePolicy.COLLECTION); fixed.put("milvus.dimension",1024);
        fixed.put("milvus.token",env("E2E_MILVUS_TOKEN")); fixed.put("milvus.connect-timeout","5s"); fixed.put("milvus.request-timeout","20s");
        fixed.put("rag.retrieval.cosine-threshold-enabled",true); fixed.put("rag.retrieval.min-cosine-score",0.70);
        // Ephemeral signing key, proper auth endpoints and current DB USER lookup still apply.
        fixed.put("jwt.secret",Base64.getEncoder().encodeToString(new java.security.SecureRandom().generateSeed(64)));
        fixed.put("jwt.issuer","ai-interview-system"); fixed.put("jwt.access-token-expiration","2h");
        fixed.put("logging.level.root","OFF"); fixed.put("spring.main.banner-mode","off");
        var environment=new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("e2e-fixed-child-process",fixed));
        var app=new SpringApplication(AiInterviewSystemApplication.class,RagE2eAudit.class);
        app.setEnvironment(environment); app.setAddCommandLineProperties(false);
        app.addInitializers(context->{
            var e=context.getEnvironment();
            check(new HashSet<>(Arrays.asList(e.getActiveProfiles())).equals(Set.of("test-real","rag-p1")),"EXACT_E2E_PROFILES_REQUIRED");
            RagE2ePolicy.auditJdbc(e.getProperty("spring.datasource.url"));
            check(RagE2ePolicy.COLLECTION.equals(e.getProperty("milvus.collection-name"))
                    && "deepseek".equals(e.getProperty("ai.judge.provider")),"RESOLVED_CONFIG_MISMATCH");
        });
        // Existing EnvironmentIsolationInitializer remains registered and runs before infrastructure.
        var context=app.run();
        baseUrl="http://127.0.0.1:"+((ServletWebServerApplicationContext)context).getWebServer().getPort();
        report.put("resolvedConfiguration",Map.of("profiles",List.of("test-real","rag-p1"),"database",RagE2ePolicy.TARGET,
                "collection",RagE2ePolicy.COLLECTION,"provider","deepseek","judgeModel",RagE2ePolicy.JUDGE_MODEL,
                "embeddingModel",RagE2ePolicy.MODEL,"embeddingVersion",RagE2ePolicy.VERSION,"dimension",1024,"cosineThreshold",0.70));
        return context;
    }
    // Construct only in authorized execution: JDK HttpClient itself opens an internal loopback pipe.
    static final class BusinessClient {
        static final HttpClient INSTANCE=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(3)).build();
    }
    interface BusinessTransport { HttpResponse<String> send(HttpRequest request) throws Exception; }
    static BusinessTransport realBusinessTransport() {
        return request->BusinessClient.INSTANCE.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    static String randomCandidatePassword() {
        byte[] bytes=new byte[24]; new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)+"aA9!";
    }
    static int expectedHttpStatus(String method,String path) {
        // These controllers return Result.success with HTTP 200, including registration/session creation.
        // Do not accept arbitrary 2xx (201/202/204 would break the current response/workflow contract).
        check(method.equals("POST") && (Set.of("/api/auth/register","/api/auth/login","/api/interviews").contains(path)
                || path.matches("/api/interviews/[1-9][0-9]*/answers"))
                || method.equals("GET") && path.matches("/api/interviews/[1-9][0-9]*"),"BUSINESS_ENDPOINT_NOT_ALLOWED");
        return 200;
    }
    static HttpRequest businessRequest(String method,String path,Object body) throws Exception {
        expectedHttpStatus(method,path);
        report.put("currentOperation","BUSINESS_HTTP "+method+" "+path);
        var builder=HttpRequest.newBuilder(URI.create(baseUrl+path)).timeout(Duration.ofSeconds(150));
        if(token!=null)builder.header("Authorization","Bearer "+token);
        if(method.equals("GET"))builder.GET();
        else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return builder.build();
    }
    static Map<String,Object> businessHttpEvidence(HttpResponse<String> response) throws Exception {
        var evidence=new LinkedHashMap<String,Object>();
        String method=response.request().method(), path=response.request().uri().getPath();
        evidence.put("method",method); evidence.put("path",path); evidence.put("httpStatus",response.statusCode());
        evidence.put("expectedHttpStatus",expectedHttpStatus(method,path));
        evidence.put("responseSha256",RagE2ePolicy.hash(response.body()));
        // Never export data (login JWT, answers, snapshots, model prose) or unreviewed server messages.
        try {
            var result=JSON.readTree(response.body()); var safe=new LinkedHashMap<String,Object>();
            if(result!=null && result.path("code").isIntegralNumber() && result.path("code").canConvertToInt())
                safe.put("code",result.path("code").asInt());
            String message=result==null?"":result.path("message").asText();
            safe.put("message",Set.of("success","系统异常，请稍后重试","外部服务暂时不可用","未认证或访问令牌无效",
                    "权限不足，无法访问资源","请求体格式错误","请求参数不合法","difficulty不能为空",
                    "账号或密码错误","账号已被禁用").contains(message)?message:"[OMITTED_UNREVIEWED_MESSAGE]");
            safe.put("dataOmitted",true); evidence.put("sanitizedResponse",safe);
        } catch(com.fasterxml.jackson.core.JsonProcessingException invalid) {
            evidence.put("sanitizedResponse",Map.of("bodyOmitted",true,"reason","NON_JSON_RESPONSE"));
        }
        return evidence;
    }
    static HttpResponse<String> http(String method,String path,Object body) throws Exception {
        return http(method,path,body,realBusinessTransport());
    }
    static HttpResponse<String> http(String method,String path,Object body,BusinessTransport transport) throws Exception {
        var request=businessRequest(method,path,body);
        var response=transport.send(request);
        var evidence=businessHttpEvidence(response);
        businessHttp.add(evidence); report.put("businessHttp",List.copyOf(businessHttp));
        report.put("lastBusinessHttp",evidence);
        report.put("businessServiceExceptions",List.copyOf(RagE2eAudit.serviceFailures));
        return response;
    }
    static JsonNode successful(HttpResponse<String> response) throws Exception {
        String method=response.request().method(), path=response.request().uri().getPath();
        check(response.statusCode()==expectedHttpStatus(method,path),"BUSINESS_HTTP_STATUS_MISMATCH");
        var result=JSON.readTree(response.body());
        check(result!=null && result.path("code").isIntegralNumber() && result.path("code").canConvertToInt()
                && result.path("code").asInt()==200,"BUSINESS_RESULT_NOT_SUCCESS");
        return result.path("data");
    }
    static void submitOnce(Connection target) throws Exception {
        submitOnce(target,realBusinessTransport());
    }
    static void submitOnce(Connection target,BusinessTransport transport) throws Exception {
        stage="BUSINESS_HTTP";
        report.put("currentOperation","BUSINESS_HTTP_PREPARATION");
        String account="e2e_"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        String password=randomCandidatePassword(); candidatePassword=password;
        report.put("registeredAccountSha256",RagE2ePolicy.hash(account));
        successful(http("POST","/api/auth/register",Map.of("account",account,"username","RAG E2E synthetic candidate",
                "password",password,"email",account+"@example.invalid"),transport));
        report.put("registrationSucceeded",true);
        var login=successful(http("POST","/api/auth/login",Map.of("account",account,"password",password),transport));
        check("USER".equals(login.path("role").asText()) && "Bearer".equalsIgnoreCase(login.path("tokenType").asText()),"NORMAL_USER_LOGIN_REQUIRED");
        check(account.equals(login.path("account").asText()),"LOGIN_ACCOUNT_MISMATCH");
        token=login.path("accessToken").asText(); userId=login.path("userId").asLong();
        check(!token.isBlank()&&userId>0,"LOGIN_TOKEN_REQUIRED");
        if(recoveryAttempt) {
            check(userId!=360,"RECOVERY_REQUIRES_NEW_NORMAL_USER");
            check(select(target,"SELECT id FROM interview_session WHERE user_id=? LIMIT 1",userId).isEmpty(),
                    "RECOVERY_NEW_SESSION_REQUIRED");
        }
        report.put("businessIdentifiers",Map.of("userId",userId,"loginSucceeded",true));
        var created=successful(http("POST","/api/interviews",Map.of("difficulty","EASY"),transport));
        sessionId=created.path("sessionId").asLong(); questionId=created.path("currentQuestion").path("interviewQuestionId").asLong();
        report.put("businessIdentifiers",Map.of("userId",userId,"sessionId",sessionId,"interviewQuestionId",questionId));
        check(sessionId>0&&questionId>0&&created.path("plannedQuestionCount").asInt()==5,"CREATED_EASY_PLAN_REQUIRED");
        report.put("currentOperation","MYSQL_CREATED_QUESTION_READ");
        var question=select(target,"SELECT question_id, status, question_type, reference_answer_snapshot "
                +"FROM interview_question WHERE id=? AND session_id=?",questionId,sessionId).getFirst();
        check("MAIN".equals(question.get("question_type"))&&"WAITING_ANSWER".equals(question.get("status")),"INITIAL_MAIN_STATE_REQUIRED");
        var source=byId(sourceRows.get("question")).get(question.get("question_id"));
        check(source!=null && source.get("reference_answer").equals(question.get("reference_answer_snapshot")),"QUESTION_SNAPSHOT_NOT_FROZEN_CORPUS");
        String answer=question.get("reference_answer_snapshot");
        check(answer!=null&&!answer.isBlank(),"CONTROLLED_SYNTHETIC_ANSWER_REQUIRED");
        requestId="e2e-"+UUID.randomUUID();
        report.put("businessIdentifiers",Map.of("userId",userId,"sessionId",sessionId,"interviewQuestionId",questionId,
                "requestId",requestId,"answerFixture","existing reference answer, synthetic candidate",
                "answerSha256",RagE2ePolicy.hash(answer),"initialQuestionStatus","WAITING_ANSWER"));
        submissions.take();
        // Persist intent BEFORE POST. A timeout or non-200 never permits a retry.
        report.put("answerPostIntentRecorded",true); report.put("E2E","RUNNING"); writeReport();
        try {
            var response=http("POST","/api/interviews/"+sessionId+"/answers",Map.of("interviewQuestionId",questionId,
                    "answerContent",answer,"requestId",requestId),transport);
            submitHttpStatus=response.statusCode(); report.put("submitHttpStatus",submitHttpStatus);
            if(submitHttpStatus==200)submitResponse=successful(response);
        } catch(Exception unknown) { report.put("submitTransportOutcome","UNKNOWN_NO_RESUBMISSION"); }
        stage="READ_ONLY_STATUS_POLL";
        // Existing GET detail is read-only; it never claims/retries evaluation.
        long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos(); int polls=0;
        do {
            var detail=successful(http("GET","/api/interviews/"+sessionId,null,transport)); polls++;
            for(var q:detail.path("questions"))if(q.path("interviewQuestionId").asLong()==questionId) {
                String state=q.path("answer").path("answerStatus").asText();
                if(Set.of("EVALUATED","FAILED").contains(state)) { terminalDetail=detail; break; }
            }
            if(terminalDetail!=null)break;
            Thread.sleep(1000); // bounded GET observation, never POST retry
        } while(System.nanoTime()<deadline && polls<30);
        report.put("statusGetPolls",polls);
        check(terminalDetail!=null,"NO_TERMINAL_ANSWER_STATE");
    }
    static void verify(Connection target,RagE2eModelGate gate) throws Exception {
        stage="PERSISTENCE_AND_HTTP_VERIFY";
        report.put("currentOperation","MYSQL_PERSISTENCE_AND_HTTP_VERIFY");
        var answers=select(target,"SELECT id,interview_question_id,status,answer_content FROM interview_answer WHERE request_id=?",requestId);
        check(answers.size()==1,"EXACTLY_ONE_ANSWER_REQUIRED"); var answer=answers.getFirst();
        check("EVALUATED".equals(answer.get("status")),"ANSWER_NOT_EVALUATED");
        var evaluations=select(target,"SELECT * FROM answer_evaluation WHERE answer_id=?",answer.get("id"));
        check(evaluations.size()==1,"EXACTLY_ONE_EVALUATION_REQUIRED"); var saved=evaluations.getFirst();
        check(RagE2eAudit.prompt!=null && RagE2eAudit.judgeResult!=null && RagE2eAudit.validated!=null
                && RagE2eAudit.score!=null && RagE2eAudit.decision!=null,"JAVA_CHAIN_AUDIT_INCOMPLETE");
        check(gate.embedding.attempted.get()==1&&gate.judge.attempted.get()==1
                && RagE2eAudit.embeddingCalls.attempted.get()==1&&RagE2eAudit.searches.attempted.get()==1
                && RagE2eAudit.judgeCalls.attempted.get()==1,"EXACT_MODEL_AND_SEARCH_COUNTS_REQUIRED");
        check(RagE2eAudit.embeddingDimension==1024 && RagE2eAudit.searchUsesCollectionDefault,"REAL_EMBEDDING_OR_BOUND_DEFAULT_MISSING");
        var retrieval=RagE2eAudit.retrieval;
        var passed=RagE2eAudit.rawHits.stream().filter(h->h.similarityScore()>=0.70F).toList();
        check(RagE2eAudit.sqlVectorIds.equals(passed.stream().map(h->h.vectorId()).distinct().toList()),"THRESHOLD_TO_SQL_IDS_MISMATCH");
        check(RagE2eAudit.sqlRows.size()==retrieval.evidence().size() && !retrieval.evidence().isEmpty(),"NONEMPTY_REAL_HYDRATION_REQUIRED");
        var batches=select(target,"SELECT * FROM rag_retrieval_batch WHERE answer_id=?",answer.get("id"));
        check(batches.size()==1,"EXACTLY_ONE_TRACE_BATCH_REQUIRED"); var batch=batches.getFirst();
        check(batch.get("retrieval_batch_id").equals(saved.get("retrieval_batch_id"))
                && Integer.parseInt(batch.get("raw_hit_count"))==RagE2eAudit.rawHits.size()
                && Integer.parseInt(batch.get("evidence_hit_count"))==retrieval.evidence().size()
                && batch.get("top_k").equals("5") && batch.get("query_text").equals(retrieval.query())
                && RagE2ePolicy.MODEL.equals(batch.get("embedding_model"))
                && RagE2ePolicy.VERSION.equals(batch.get("embedding_version")),"TRACE_BATCH_CONTENT_MISMATCH");
        check(gate.evidence.stream().anyMatch(e->"bailian".equals(e.get("provider"))
                && RagE2ePolicy.hash(retrieval.query()).equals(e.get("querySha256"))),"ACTUAL_EMBEDDING_QUERY_MISMATCH");
        var filter=JSON.readTree(batch.get("filter_snapshot")).path("cosineCandidate");
        check(filter.path("enabled").asBoolean() && filter.path("minScore").asDouble()==0.70
                && "COSINE".equals(filter.path("metric").asText()),"TRACE_THRESHOLD_MISMATCH");
        var hits=select(target,"SELECT chunk_id,rank_no,similarity_score FROM rag_hit_log WHERE retrieval_batch_id=? ORDER BY rank_no",batch.get("retrieval_batch_id"));
        check(hits.size()==retrieval.evidence().size(),"TRACE_HIT_COUNT_MISMATCH");
        List<Map<String,Object>> evidence=new ArrayList<>();
        for(int i=0;i<hits.size();i++) {
            var selected=retrieval.evidence().get(i); var hit=hits.get(i);
            check(Long.parseLong(hit.get("chunk_id"))==selected.chunkId()
                    && Integer.parseInt(hit.get("rank_no"))==selected.vectorRank()
                    && Math.abs(Double.parseDouble(hit.get("similarity_score"))-selected.similarityScore())<0.000001,
                    "TRACE_PROMPT_CHUNK_MISMATCH");
            check(RagE2eAudit.prompt.userPrompt().contains("  content="+selected.content()+"\n\n"),"ACTUAL_PROMPT_CONTENT_MISSING");
            var hydration=RagE2eAudit.sqlRows.stream().filter(r->r.getChunkId().equals(selected.chunkId())).findFirst().orElseThrow();
            check(hydration.getContent().equals(selected.content()),"HYDRATED_CONTENT_TO_PROMPT_MISMATCH");
            evidence.add(Map.of("chunkId",selected.chunkId(),"documentId",selected.documentId(),"rank",selected.vectorRank(),
                    "vectorId",selected.vectorId(),"cosine",selected.similarityScore(),"title",selected.title(),
                    "contentSha256",RagE2ePolicy.hash(selected.content())));
        }
        RagE2eConsistency.verifyModel(saved.get("raw_result"),RagE2eAudit.judgeResult.rawJson(),
                saved.get("llm_model"),RagE2eAudit.judgeResult.model(),gate.evidence);
        check(RagE2eConsistency.equivalent(JSON.readTree(saved.get("scoring_point_results")),JSON.valueToTree(RagE2eAudit.validated.scoringPointResults())),
                "VALIDATED_COVERAGE_TO_DB_MISMATCH");
        var dimensions=List.of("correctness_score","completeness_score","depth_score","clarity_score","practice_score");
        int total=dimensions.stream().mapToInt(k->Integer.parseInt(saved.get(k))).sum();
        check(total==Integer.parseInt(saved.get("total_score")) && total==RagE2eAudit.score.totalScore(),"JAVA_TOTAL_SCORE_MISMATCH");
        var score=RagE2eAudit.score;
        check(Integer.parseInt(saved.get("correctness_score"))==score.correctnessScore()
                && Integer.parseInt(saved.get("completeness_score"))==score.completenessScore()
                && Integer.parseInt(saved.get("depth_score"))==score.depthScore()
                && Integer.parseInt(saved.get("clarity_score"))==score.clarityScore()
                && Integer.parseInt(saved.get("practice_score"))==score.practiceScore(),"JAVA_DIMENSIONS_TO_DB_MISMATCH");
        check(saved.get("decision_action").equals(RagE2eAudit.decision.decisionAction().name())
                && saved.get("evaluation_phase").equals(RagE2eAudit.decision.evaluationPhase().name()),"JAVA_DECISION_TO_DB_MISMATCH");
        check(saved.get("level").equals(new EvaluationStandard().resolveLevel(total).name()),"JAVA_LEVEL_MISMATCH");
        JsonNode detailEvaluation=null;
        for(var q:terminalDetail.path("questions"))if(q.path("interviewQuestionId").asLong()==questionId)detailEvaluation=q.path("evaluation");
        check(detailEvaluation!=null && detailEvaluation.path("totalScore").asInt(-1)==total
                && detailEvaluation.path("evaluationPhase").asText().equals(saved.get("evaluation_phase")),"GET_EVALUATION_TO_DB_MISMATCH");
        check(submitResponse!=null && submitHttpStatus==200 && submitResponse.path("evaluation").equals(detailEvaluation),"POST_GET_HTTP_RESULT_MISMATCH");
        check(submitResponse.path("nextAction").asText().equals(saved.get("decision_action")),"HTTP_DECISION_MISMATCH");
        var session=select(target,"SELECT status,completed_question_count,current_interview_question_id,report_status "
                +"FROM interview_session WHERE id=? AND user_id=?",sessionId,userId).getFirst();
        check("IN_PROGRESS".equals(session.get("status")) && "NOT_STARTED".equals(session.get("report_status")),"SINGLE_ANSWER_SESSION_STATE");
        var next=select(target,"SELECT question_type,parent_question_id,status FROM interview_question WHERE id=?",session.get("current_interview_question_id")).getFirst();
        boolean followUp="FOLLOW_UP".equals(saved.get("decision_action"));
        check("WAITING_ANSWER".equals(next.get("status")) && (followUp?
                "FOLLOW_UP".equals(next.get("question_type")) && Long.toString(questionId).equals(next.get("parent_question_id"))
                        && session.get("completed_question_count").equals("0"):
                "MAIN".equals(next.get("question_type")) && session.get("completed_question_count").equals("1")),"WORKFLOW_STATE_TRANSITION_MISMATCH");
        check(select(target,"SELECT id FROM interview_report WHERE session_id=?",sessionId).isEmpty(),"UNEXPECTED_REPORT_GENERATION");
        report.put("retrieval",Map.of("rawHitCount",RagE2eAudit.rawHits.size(),"afterCosineCount",passed.size(),
                "sqlHydratedCount",RagE2eAudit.sqlRows.size(),"promptEvidenceCount",evidence.size(),"knowledge",evidence,
                "batchId",batch.get("retrieval_batch_id"),"querySha256",RagE2ePolicy.hash(retrieval.query()),
                "promptSha256",RagE2ePolicy.hash(RagE2eAudit.prompt.userPrompt())));
        report.put("evaluation",Map.of("answerId",answer.get("id"),"evaluationId",saved.get("id"),"answerState","EVALUATED",
                "phase",saved.get("evaluation_phase"),"decision",saved.get("decision_action"),"totalScore",total,
                "dimensions",dimensions.stream().collect(Collectors.toMap(k->k,k->Integer.parseInt(saved.get(k)))),
                "providerContentSha256",RagE2ePolicy.hash(RagE2eAudit.judgeResult.rawJson()),
                "databaseJsonTextSha256",RagE2ePolicy.hash(saved.get("raw_result")),"httpPostGetDbAgree",true));
        report.put("sessionAfter",session); report.put("milestones",RagE2eAudit.milestones);
        report.put("modelHttp",gate.evidence); report.put("businessSdkMethodCalls",RagE2eAudit.sdkCalls);
        report.put("E2E","PASS");
    }
    public static void main(String[] args) throws Exception {
        if(args.length==0 || args[0].equals("offline")) { offline(); return; }
        recoveryAttempt=args.length==2 && args[0].equals("execute-recovery-authorized-once");
        check(recoveryAttempt || args.length==1&&args[0].equals("execute-authorized-once"),"EXPLICIT_EXECUTION_MODE_REQUIRED");
        if(recoveryAttempt) {
            loadRecoveryPlan(args[1]);
            outputDirectory=reserveRecoveryAttempt(Path.of("target/rag-e2e/attempts"),args[1]);
            executionLock=outputDirectory.resolve("EXECUTION_CONSUMED.lock");
            report.put("runId",args[1]); report.put("priorAttempts",recoveryPlan.path("priorAttempts"));
            report.put("previousEvidenceVerified",true);
        } else {
            // A direct Java invocation must not overwrite the legacy failure report in finally either.
            guardLegacyAttempt();
        }
        int exit=1; ConfigurableApplicationContext app=null; RagE2eModelGate gate=null; MilvusClientV2 vectorsClient=null;
        try {
            loadContract(); check(missingEnvironment().isEmpty(),"REQUIRED_PROCESS_ENVIRONMENT_MISSING"); priceGate();
            if(recoveryAttempt)check("rag_e2e_writer".equals(env("E2E_DB_USERNAME")),"RECOVERY_WRITER_IDENTITY_REQUIRED");
            Files.createDirectories(outputDirectory);
            report.put("startedUtc",Instant.now().toString()); report.put("E2E","RUNNING");
            stage="DB_READ_ONLY_ADMISSION";
            if(recoveryAttempt) {
                try(var target=connection(true)) {
                    auditConnection(target,true); verifyReusableCorpus(target);
                    vectorsClient=milvus(); verifyReusableVectors(vectorsClient);
                    Files.writeString(executionLock,Instant.now().toString(),StandardOpenOption.CREATE_NEW);
                    writeReport();
                    gate=new RagE2eModelGate(RagE2ePolicy.EMBEDDING_ENDPOINT,"https://api.deepseek.com",
                            env("EMBEDDING_API_KEY"),env("DEEPSEEK_API_KEY"),RagE2eModelGate.realTransport());
                    gate.start(); app=boot(gate); submitOnce(target); verify(target,gate);
                    for(String table:RagE2ePolicy.CORPUS)check(RagE2ePolicy.rowHash(corpus(target,table))
                            .equals(contract.path("mysqlFrozen").path(table).path("rowSnapshotSha256").asText()),
                            "POST_RUN_TEST_CORPUS_CHANGED");
                    exit=0;
                }
            } else try(var source=connection(false); var target=connection(true)) {
                vectorsClient=milvus();
                stage="DB_READ_ONLY_ADMISSION";
                auditConnection(source,false); auditConnection(target,true); readSource(source);
                var frozen=readFrozenSource(vectorsClient);
                // Read-only admission can be corrected and explicitly rerun. The lock starts at the first test write.
                Files.writeString(executionLock,Instant.now().toString(),
                        StandardOpenOption.CREATE_NEW);
                copyMissingCorpus(target); source.rollback();
                prepareVectors(vectorsClient,frozen);
                gate=new RagE2eModelGate(RagE2ePolicy.EMBEDDING_ENDPOINT,"https://api.deepseek.com",
                        env("EMBEDDING_API_KEY"),env("DEEPSEEK_API_KEY"),RagE2eModelGate.realTransport());
                gate.start(); app=boot(gate); submitOnce(target); verify(target,gate);
                // Confirm the isolated corpus still matches; source data was only read.
                for(String table:RagE2ePolicy.CORPUS)check(RagE2ePolicy.rowHash(corpus(target,table))
                        .equals(contract.path("mysqlFrozen").path(table).path("rowSnapshotSha256").asText()),"POST_RUN_TEST_CORPUS_CHANGED");
                exit=0;
            }
        } catch(Exception error) {
            report.put("E2E","FAIL");
            String message=error.getMessage(); report.put("failureCode",message!=null&&message.matches("[A-Z0-9_]{1,100}")
                    ?message:error.getClass().getSimpleName());
            report.put("failedStage",stage); report.put("requiresNewHumanDecision",true);
            report.put("failureDetails",failureDetails(error));
        } finally {
            if(gate!=null)report.put("modelHttp",gate.evidence);
            report.put("businessServiceExceptions",List.copyOf(RagE2eAudit.serviceFailures));
            report.put("answerPostAttempts",submissions.attempted.get());
            report.put("endedUtc",Instant.now().toString()); writeReport();
            if(vectorsClient!=null) vectorsClient.close();
            if(app!=null)app.close(); if(gate!=null)gate.close(); token=null; candidatePassword=null;
        }
        System.out.println("E2E = "+report.get("E2E")+"; evidence = "+outputDirectory.resolve("report.json"));
        System.exit(exit);
    }
}
