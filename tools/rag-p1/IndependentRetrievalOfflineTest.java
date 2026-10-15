import io.milvus.v2.client.RetryConfig;
import io.milvus.v2.common.*;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.utils.RpcUtils;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** No sockets: JDBC proxies, native SDK DTOs/RpcUtils, and injected fake Embedding transport. */
public final class IndependentRetrievalOfflineTest {
    interface Checked {void run() throws Exception;}
    static int tests;
    static void test(Checked work) throws Exception {work.run();tests++;}
    static void ok(boolean value) {if(!value)throw new AssertionError();}
    static void rejects(Checked work) throws Exception {
        try {work.run();throw new AssertionError("Expected rejection");}catch(SecurityException expected){}
    }
    @SuppressWarnings("unchecked")
    static <T>T proxy(Class<T> type,InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    static Map<String,String> identity() {
        return new LinkedHashMap<>(Map.of("db","ai_interview_test","account","rag_e2e_writer@127.0.0.1", "roles","NONE",
                "mandatory","","uuid",RagE2ePolicy.SERVER_UUID,"read_only","1"));
    }
    static ResultSet rows(List<Map<String,String>> data) {
        var columns=new ArrayList<>(data.isEmpty()?List.of("empty"):data.getFirst().keySet()); var index=new AtomicInteger(-1);
        ResultSetMetaData metadata=proxy(ResultSetMetaData.class,(p,m,a)->switch(m.getName()) {
            case "getColumnCount"->columns.size(); case "getColumnLabel"->columns.get((int)a[0]-1);
            default->throw new AssertionError(m.getName());
        });
        return proxy(ResultSet.class,(p,m,a)->switch(m.getName()) {
            case "next"->index.incrementAndGet()<data.size(); case "getMetaData"->metadata;
            case "getString"->data.get(index.get()).get(columns.get((int)a[0]-1)); case "close"->null;
            default->throw new AssertionError(m.getName());
        });
    }
    static final class MockDb {
        final List<String> calls=new ArrayList<>(); final Map<String,String> identity=identity(); boolean readonly,auto=true;
        Connection connection() {
            return proxy(Connection.class,(p,m,a)->{
                switch(m.getName()) {
                    case "setTransactionIsolation":ok((int)a[0]==Connection.TRANSACTION_REPEATABLE_READ);calls.add("ISOLATION");return null;
                    case "setReadOnly":readonly=(boolean)a[0];ok(readonly);calls.add("READ_ONLY");return null;
                    case "setAutoCommit":auto=(boolean)a[0];ok(!auto);calls.add("AUTOCOMMIT_OFF");return null;
                    case "isReadOnly":return readonly;
                    case "getAutoCommit":return auto;
                    case "createStatement":return proxy(Statement.class,(sp,sm,sa)->{
                        if(sm.getName().equals("close"))return null;
                        if(sm.getName().equals("execute")){ok(sa[0].equals("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY"));calls.add(sa[0].toString());return false;}
                        throw new AssertionError(sm.getName());
                    });
                    case "prepareStatement":String sql=a[0].toString();ok(sql.startsWith("SELECT "));calls.add(sql);
                        return proxy(PreparedStatement.class,(sp,sm,sa)->switch(sm.getName()) {
                            case "setQueryTimeout","setMaxRows","setObject","close"->null;
                            case "executeQuery"->rows(sql.equals(IndependentReadOnlySql.IDENTITY)?List.of(identity):List.of());
                            default->throw new AssertionError("Non-SELECT execution: "+sm.getName());
                        });
                    case "rollback","close":calls.add(m.getName());return null;
                    default:throw new AssertionError("Forbidden connection call: "+m.getName());
                }
            });
        }
    }
    static DescribeCollectionResp schema() {
        var schema=CreateCollectionReq.CollectionSchema.builder().enableDynamicField(false).build();
        schema.addField(AddFieldReq.builder().fieldName("vector_id").dataType(DataType.VarChar).maxLength(64).isPrimaryKey(true).autoID(false).build());
        schema.addField(AddFieldReq.builder().fieldName("document_id").dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("chunk_index").dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("embedding_version").dataType(DataType.VarChar).maxLength(128).build());
        schema.addField(AddFieldReq.builder().fieldName("vector").dataType(DataType.FloatVector).dimension(1024).build());
        return DescribeCollectionResp.builder().collectionName(IndependentRetrieval.COLLECTION).collectionID(IndependentRetrieval.COLLECTION_ID)
                .databaseName("default").autoID(false).enableDynamicField(false).primaryFieldName("vector_id")
                .collectionSchema(schema).consistencyLevel(ConsistencyLevel.BOUNDED).build();
    }
    static DescribeIndexResp index() {
        return DescribeIndexResp.builder().indexDescriptions(List.of(DescribeIndexResp.IndexDesc.builder().fieldName("vector")
                .metricType(IndexParam.MetricType.COSINE).indexType(IndexParam.IndexType.AUTOINDEX).build())).build();
    }
    static Map<String,String> knowledge() {
        return new LinkedHashMap<>(Map.ofEntries(Map.entry("chunkId","1"),Map.entry("documentId","2"),Map.entry("chunkIndex","1"),
                Map.entry("joinedDocumentId","2"),Map.entry("chunkStatus","ACTIVE"),Map.entry("documentStatus","READY"),
                Map.entry("documentVersion","1"),Map.entry("currentDocumentVersion","1"),Map.entry("embeddingModel",RagE2ePolicy.MODEL),
                Map.entry("embeddingVersion",RagE2ePolicy.VERSION),Map.entry("content","离线虚构正文，不是检索结果"),Map.entry("title","OFFLINE FIXTURE")));
    }
    static Map<String,Object> hit(int rank,float score) {
        return new LinkedHashMap<>(Map.of("rank",rank,"vectorId","offline-"+rank,"documentId",2L,"chunkIndex",1L,
                "cosineFloat32",(double)score,"cosineFloat32Bits",String.format("%08x",Float.floatToRawIntBits(score))));
    }
    static byte[] reply() throws Exception {
        return IndependentRetrieval.JSON.writeValueAsBytes(Map.of("model",RagE2ePolicy.MODEL,"data",List.of(Map.of("index",0,
                "embedding",Collections.nCopies(1024,0.1))),"usage",Map.of("prompt_tokens",9,"total_tokens",9)));
    }
    public static void main(String[] args) throws Exception {
        Path build=Path.of(args[0]); var runner=new IndependentRetrieval(); runner.offlineGate();
        for(String excluded:List.of("DB_USERNAME","DB_PASSWORD","P1_DB_PASSWORD","DEEPSEEK_API_KEY","JAVA_TOOL_OPTIONS","_JAVA_OPTIONS"))
            test(()->ok(System.getenv(excluded)==null));
        test(()->ok(runner.dataset.path("queries").size()==16));
        for(var query:runner.dataset.path("queries"))test(()->ok(IndependentRetrieval.buildQuery(query).equals(query.path("productionStyleQueryText").asText())));
        test(IndependentRetrieval::checkMapper);
        var db=new MockDb(); var sql=new IndependentReadOnlySql(db.connection(),runner.corpusSql());
        test(()->ok(db.calls.subList(0,4).equals(List.of("ISOLATION","READ_ONLY","AUTOCOMMIT_OFF","START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY"))));
        for(String forbidden:List.of("INSERT INTO knowledge_chunk VALUES(1)","UPDATE knowledge_document SET processing_status='READY'",
                "DELETE FROM knowledge_chunk","DROP TABLE knowledge_chunk","SELECT 1; DELETE FROM knowledge_chunk","SELECT * FROM ai_interview.knowledge_chunk",
                "SELECT * FROM knowledge_chunk FOR UPDATE","SELECT SLEEP(10)","SHOW GRANTS FOR CURRENT_USER()"))
            test(()->{int before=db.calls.size();rejects(()->sql.query(forbidden));ok(before==db.calls.size());});
        for(String table:RagE2ePolicy.CORPUS)test(()->sql.query(runner.corpusSql().get(table)));
        test(()->{sql.close();sql.close();ok(Collections.frequency(db.calls,"rollback")==1 && Collections.frequency(db.calls,"close")==1);});
        for(var bad:List.of(Map.of("account","root@localhost"),Map.of("db","ai_interview"),Map.of("read_only","0"),
                Map.of("uuid","other-server"),Map.of("roles","admin"),Map.of("mandatory","admin")))
            test(()->{var invalid=identity();invalid.putAll(bad);rejects(()->IndependentReadOnlySql.validateIdentity(invalid));});
        test(()->{var disabled=new MockDb();disabled.identity.put("read_only","0");rejects(()->new IndependentReadOnlySql(disabled.connection(),runner.corpusSql()));
            ok(disabled.calls.stream().filter(s->s.startsWith("SELECT")).count()==1);});
        test(()->rejects(()->IndependentReadOnlySql.corpusSql("user",List.of("password"))));
        test(()->rejects(()->IndependentReadOnlySql.corpusSql("knowledge_chunk",List.of("id;DROP TABLE"))));
        test(()->IndependentRetrieval.validateSchema(schema(),index()));
        test(()->{var invalid=schema();invalid.setCollectionID(123L);rejects(()->IndependentRetrieval.validateSchema(invalid,index()));});
        test(()->{var invalid=schema();invalid.setConsistencyLevel(ConsistencyLevel.STRONG);rejects(()->IndependentRetrieval.validateSchema(invalid,index()));});
        test(()->{var invalid=schema();invalid.setEnableDynamicField(true);rejects(()->IndependentRetrieval.validateSchema(invalid,index()));});
        test(()->{var invalid=schema();invalid.getCollectionSchema().getFieldSchemaList().getLast().setDimension(768);rejects(()->IndependentRetrieval.validateSchema(invalid,index()));});
        test(()->{var invalid=index();invalid.getIndexDescriptions().getFirst().setMetricType(IndexParam.MetricType.L2);rejects(()->IndependentRetrieval.validateSchema(schema(),invalid));});
        test(()->{var request=IndependentRetrieval.searchRequest(Collections.nCopies(1024,0.1f));ok(request.getLimit()==5 && request.getConsistencyLevel()==ConsistencyLevel.BOUNDED
                && request.getMetricType()==IndexParam.MetricType.COSINE && request.getFilter().equals("embedding_version == {embeddingVersion}"));});
        test(()->ok(IndependentRetrieval.JSON.valueToTree(IndependentRetrieval.searchRequest(Collections.nCopies(1024,0.1f))).path("limit").asLong()==5));
        test(()->{var result=SearchResp.SearchResult.builder().id("offline").score(0.71f).entity(Map.of("document_id",2L,"chunk_index",1L,"embedding_version",RagE2ePolicy.VERSION)).build();
            var hits=IndependentRetrieval.rawHits(SearchResp.builder().searchResults(List.of(List.of(result))).build());ok(hits.size()==1 && hits.getFirst().get("rank").equals(1));});
        test(()->rejects(()->IndependentRetrieval.rawHits(SearchResp.builder().searchResults(List.of()).build())));
        test(()->ok(IndependentRetrieval.embeddingValues(IndependentRetrieval.JSON.readTree(reply())).size()==1024));
        test(()->{var response=IndependentRetrieval.JSON.readTree(reply());((com.fasterxml.jackson.databind.node.ObjectNode)response).put("model","wrong");rejects(()->IndependentRetrieval.embeddingValues(response));});
        test(()->{var response=IndependentRetrieval.JSON.readTree(reply());((com.fasterxml.jackson.databind.node.ArrayNode)response.path("data").get(0).path("embedding")).set(1,IndependentRetrieval.JSON.nullNode());rejects(()->IndependentRetrieval.embeddingValues(response));});
        test(()->{var kept=hit(3,Math.nextUp(0.70f));IndependentRetrieval.annotateHit(kept,knowledge(),true);ok(kept.get("v1Retained").equals(true) && kept.get("rank").equals(3));
            var removed=hit(4,0.70f);IndependentRetrieval.annotateHit(removed,knowledge(),true);ok(removed.get("v1Retained").equals(false));});
        // Float32 0.70f is 0.699999988..., so the production double >= 0.70 comparison rejects it.
        test(()->{var invalid=knowledge();invalid.put("chunkStatus","INACTIVE");var hit=hit(1,0.99f);IndependentRetrieval.annotateHit(hit,invalid,false);
            ok(hit.get("passes070").equals(true) && hit.get("v1Retained").equals(false) && hit.get("sqlRejectionReasons").equals(List.of("CHUNK_NOT_ACTIVE")));});
        test(()->rejects(()->IndependentRetrieval.annotateHit(hit(1,0.99f),knowledge(),false)));
        test(()->{var row=knowledge();row.put("documentId","99");ok(IndependentRetrieval.rejection(row,hit(1,0.8f)).contains("MILVUS_DOCUMENT_ID_MISMATCH"));});
        test(()->{var budget=new IndependentRetrieval.Budget();for(int i=0;i<16;i++)budget.take();rejects(budget::take);ok(budget.attempts==16);});
        test(()->{var utils=new RpcUtils();utils.retryConfig(RetryConfig.builder().maxRetryTimes(1).retryOnRateLimit(false).build());var calls=new AtomicInteger();
            try{utils.retry(()->{calls.incrementAndGet();throw io.milvus.shaded.io.grpc.Status.UNAVAILABLE.asRuntimeException();});throw new AssertionError();}catch(Exception expected){}
            ok(calls.get()==1);});
        Path fixtures=Files.createTempDirectory(build,"fixtures-");
        test(()->{Path once=fixtures.resolve("reserved");IndependentRetrieval.reserve(once);try{IndependentRetrieval.reserve(once);throw new AssertionError();}catch(FileAlreadyExistsException expected){}
            ok(Files.exists(once.resolve("EXECUTION_RESERVED.lock")));});
        var fakeRunner=new IndependentRetrieval();fakeRunner.out=Files.createDirectory(fixtures.resolve("fake-run"));
        var posts=new AtomicInteger();
        test(()->{for(int i=0;i<16;i++)fakeRunner.embed("FAKE-"+i,"offline query",new LinkedHashMap<>(),body->{posts.incrementAndGet();return new IndependentRetrieval.HttpReply(200,reply());});
            rejects(()->fakeRunner.embed("FAKE-17","offline query",new LinkedHashMap<>(),body->{posts.incrementAndGet();return new IndependentRetrieval.HttpReply(200,reply());}));
            ok(posts.get()==16 && fakeRunner.embedding.attempts==16);});
        test(()->{var failing=new IndependentRetrieval();failing.out=Files.createDirectory(fixtures.resolve("failed-run"));var calls=new AtomicInteger();
            rejects(()->failing.embed("FAKE-FAIL","offline",new LinkedHashMap<>(),body->{calls.incrementAndGet();return new IndependentRetrieval.HttpReply(500,new byte[0]);}));
            ok(calls.get()==1 && failing.embedding.attempts==1 && Files.exists(failing.out.resolve("results.json")));});
        test(()->{var failing=new IndependentRetrieval();failing.out=Files.createDirectory(fixtures.resolve("timeout-run"));var calls=new AtomicInteger();
            try{failing.embed("FAKE-TIMEOUT","offline",new LinkedHashMap<>(),body->{calls.incrementAndGet();throw new java.net.http.HttpTimeoutException("offline");});throw new AssertionError();}
            catch(java.net.http.HttpTimeoutException expected){}ok(calls.get()==1 && failing.embedding.attempts==1);});
        // Clearly marked offline HTML fixture, never a live report or a prefilled human label.
        var preview=new IndependentRetrieval();preview.out=Files.createDirectory(fixtures.resolve("html-preview"));
        preview.report.put("INDEPENDENT_RETRIEVAL_RUN","OFFLINE_FIXTURE_NOT_REAL_RETRIEVAL");preview.report.put("datasetSha256",IndependentRetrieval.QUERY_SHA);
        var example=new LinkedHashMap<String,Object>();example.put("queryId","OFFLINE-EXAMPLE");example.put("testType","INDEPENDENT_PARAPHRASE");
        example.put("questionText","离线 UI 测试示例，不是真实检索结果。<script>invalid</script>");example.put("status","COMPLETE");
        example.put("actualQueryText","offline query");example.put("querySha256",RagE2ePolicy.hash("offline query"));
        var h1=hit(1,0.91f);IndependentRetrieval.annotateHit(h1,knowledge(),true);var h2=hit(2,0.69f);IndependentRetrieval.annotateHit(h2,knowledge(),true);
        example.put("rawTopK5",List.of(h1,h2));example.putAll(IndependentRetrieval.counts(List.of(h1,h2)));preview.queries.add(example);preview.save();preview.html();
        var summary=Map.of("tests",tests,"failures",0,"errors",0,"skipped",0,"liveEmbedding",0,"liveMilvusSearch",0,"databaseConnections",0,
                "htmlFixture",preview.out.resolve("annotation.html").toAbsolutePath().toString());
        IndependentRetrieval.JSON.writerWithDefaultPrettyPrinter().writeValue(build.resolve("offline-tests.json").toFile(),summary);
        System.out.println(IndependentRetrieval.JSON.writeValueAsString(summary));
    }
}
