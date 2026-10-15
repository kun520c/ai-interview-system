import com.fasterxml.jackson.databind.*;
import com.kun.aiinterview.interview.evaluation.*;
import com.kun.aiinterview.question.enums.*;
import io.milvus.v2.client.*;
import io.milvus.v2.common.*;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import io.milvus.v2.service.vector.request.*;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Standalone retrieval only. No Spring application, ingestion, evaluation or write-capable business adapter. */
public final class IndependentRetrieval {
    static final ObjectMapper JSON=new ObjectMapper();
    static final Path DATASET=Path.of("src/test/resources/rag-p1-independent/queries-v1.json");
    static final Path CONTRACT=Path.of("tools/rag-e2e/contract.json");
    static final Path OUTPUT=Path.of("target/rag-p1-independent/run-once");
    static final String QUERY_SHA="4f42970bbdb3682c475056e512ff725832329d213b8b49e3a27ac45b5afbd456";
    static final String CONTRACT_SHA="308862b7f4f8bcd97d8b0a3d62701cae5678492dda283cdecba648d583acdac8";
    static final String COLLECTION="knowledge_chunk_vectors_test";
    static final long COLLECTION_ID=469534467320352840L;
    static final Path MAPPER=Path.of("src/main/resources/mapper/KnowledgeChunkMapper.xml");
    final Map<String,Object> report=new LinkedHashMap<>();
    final List<Map<String,Object>> queries=new ArrayList<>(), events=new ArrayList<>();
    final Budget embedding=new Budget(), search=new Budget();
    Path out; JsonNode dataset,contract; String stage="OFFLINE_GATE";
    static final class Budget {
        int attempts;
        void take() { RagE2ePolicy.require(attempts<16,"CALL_LIMIT_16"); attempts++; }
    }
    static void check(boolean condition,String code) { RagE2ePolicy.require(condition,code); }
    static String env(String name) { return Objects.toString(System.getenv(name),""); }
    static String sha(Path file) throws Exception { return RagE2ePolicy.hash(Files.readAllBytes(file)); }
    static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+"," ").replaceAll("\\s*\\.\\s*",".")
                .replaceAll("\\s*,\\s*",",").replaceAll("\\s*=\\s*","=").strip();
    }
    static void checkMapper() throws Exception {
        String xml=Files.readString(MAPPER);
        var match=java.util.regex.Pattern.compile("(?s)<select id=\"selectRetrievableByVectorIds\".*?>(.*?)</select>").matcher(xml);
        check(match.find(),"PRODUCTION_HYDRATION_SQL_NOT_FOUND");
        String sql=match.group(1).replaceAll("(?s)<foreach.*?</foreach>","(?)")
                .replace("#{embeddingModel}","?").replace("#{embeddingVersion}","?");
        check(normalizeSql(sql).equals(normalizeSql(IndependentReadOnlySql.idsSql(IndependentReadOnlySql.HYDRATE,1))),
                "PRODUCTION_HYDRATION_SQL_CHANGED");
    }
    static String buildQuery(JsonNode query) {
        var input=query.path("builderInput"); var points=new ArrayList<ScoringPointSnapshot>();
        for (var point:input.path("scoringPoints")) points.add(new ScoringPointSnapshot(point.path("scoringPointId").asLong(),
                QuestionPointType.valueOf(point.path("pointType").asText()),point.path("content").asText(),point.path("weight").asInt()));
        var targets=new ArrayList<Long>(); input.path("followUpTargetPointIds").forEach(p->targets.add(p.asLong()));
        var context=new EvaluationContext(1L,2L,1L,2L,EvaluationMode.valueOf(input.path("mode").asText()),
                QuestionCategory.valueOf(query.path("category").asText()),query.path("knowledgePoint").asText(),
                input.path("mainQuestionContent").asText(),null,points,null,
                input.path("followUpQuestionContent").asText(null),null,targets);
        String actual=new EvaluationRagQueryBuilder().build(context);
        check(actual.equals(query.path("productionStyleQueryText").asText())
                && RagE2ePolicy.hash(actual).equals(query.path("productionStyleQuerySha256").asText()),"FROZEN_QUERY_BUILDER_MISMATCH");
        return actual;
    }
    void offlineGate() throws Exception {
        check(sha(DATASET).equals(QUERY_SHA),"FROZEN_DATASET_SHA_MISMATCH");
        check(sha(CONTRACT).equals(CONTRACT_SHA),"FROZEN_CORPUS_CONTRACT_SHA_MISMATCH");
        dataset=JSON.readTree(DATASET.toFile()); contract=JSON.readTree(CONTRACT.toFile());
        check(dataset.path("queries").size()==16,"QUERY_COUNT");
        var types=new HashMap<String,Integer>(); var ids=new HashSet<String>();
        for (var query:dataset.path("queries")) {
            check(ids.add(query.path("queryId").asText()),"DUPLICATE_QUERY_ID");
            types.merge(query.path("testType").asText(),1,Integer::sum);
            RagE2ePolicy.modelRequest(JSON.valueToTree(Map.of("model",RagE2ePolicy.MODEL,"dimensions",1024,
                    "encoding_format","float","input",List.of(buildQuery(query)))),true);
        }
        check(types.equals(Map.of("INDEPENDENT_PARAPHRASE",8,"BOUNDARY_FOLLOW_UP",4,"OUT_OF_COVERAGE",4)),"QUERY_SPLIT");
        for (var entries=dataset.path("corpusDocumentSha256").fields();entries.hasNext();) {
            var entry=entries.next(); check(sha(Path.of(entry.getKey())).equals(entry.getValue().asText()),"FROZEN_SOURCE_DOCUMENT_CHANGED");
        }
        checkMapper();
    }
    Map<String,String> corpusSql() {
        var statements=new LinkedHashMap<String,String>();
        for (String table:RagE2ePolicy.CORPUS) {
            var columns=new ArrayList<String>();
            contract.path("sqlColumns").forEach(c->{if(table.equals(c.path("table").asText()))columns.add(c.path("column").asText());});
            statements.put(table,IndependentReadOnlySql.corpusSql(table,columns));
        }
        return statements;
    }
    Map<String,List<Map<String,String>>> corpus(IndependentReadOnlySql sql) throws Exception {
        var result=new LinkedHashMap<String,List<Map<String,String>>>();
        for (var statement:corpusSql().entrySet()) {
            var rows=sql.query(statement.getValue()); var expected=contract.path("mysqlFrozen").path(statement.getKey());
            check(rows.size()==expected.path("count").asInt()
                    && RagE2ePolicy.rowHash(rows).equals(expected.path("rowSnapshotSha256").asText()),"FROZEN_TEST_CORPUS_CHANGED");
            result.put(statement.getKey(),rows);
        }
        RagE2ePolicy.corpusProfile(result.get("knowledge_document"),result.get("knowledge_chunk")); return result;
    }
    static void validateSchema(DescribeCollectionResp description,DescribeIndexResp index) {
        check(description!=null && description.getCollectionID()==COLLECTION_ID,"MILVUS_COLLECTION_ID_MISMATCH");
        check(description.getConsistencyLevel()==ConsistencyLevel.BOUNDED,"MILVUS_BOUNDED_REQUIRED");
        check(Boolean.FALSE.equals(description.getAutoID()) && Boolean.FALSE.equals(description.getEnableDynamicField())
                && "vector_id".equals(description.getPrimaryFieldName()),"MILVUS_SCHEMA_FLAGS");
        var schema=description.getCollectionSchema(); check(schema!=null && !schema.isEnableDynamicField(),"MILVUS_SCHEMA_MISSING");
        var fields=new HashMap<String,CreateCollectionReq.FieldSchema>();
        for (var field:schema.getFieldSchemaList()) check(field!=null && fields.put(field.getName(),field)==null,"MILVUS_DUPLICATE_FIELD");
        check(fields.keySet().equals(Set.of("vector_id","document_id","chunk_index","embedding_version","vector")),"MILVUS_FIELD_SET");
        var id=fields.get("vector_id"); var version=fields.get("embedding_version"); var vector=fields.get("vector");
        check(id.getDataType()==DataType.VarChar && Boolean.TRUE.equals(id.getIsPrimaryKey())
                && Boolean.FALSE.equals(id.getAutoID()) && Objects.equals(id.getMaxLength(),64),"MILVUS_PRIMARY_SCHEMA");
        check(fields.get("document_id").getDataType()==DataType.Int64 && fields.get("chunk_index").getDataType()==DataType.Int64,
                "MILVUS_METADATA_SCHEMA");
        check(version.getDataType()==DataType.VarChar && Objects.equals(version.getMaxLength(),128)
                && vector.getDataType()==DataType.FloatVector && Objects.equals(vector.getDimension(),1024),"MILVUS_VECTOR_SCHEMA");
        check(index!=null && index.getIndexDescriptions()!=null,"MILVUS_INDEX_MISSING");
        var indexes=index.getIndexDescriptions().stream().filter(i->i!=null && "vector".equals(i.getFieldName())).toList();
        check(indexes.size()==1 && indexes.getFirst().getMetricType()==IndexParam.MetricType.COSINE
                && indexes.getFirst().getIndexType()==IndexParam.IndexType.AUTOINDEX,"MILVUS_INDEX_MISMATCH");
    }
    List<TreeMap<String,Object>> vectors(MilvusClientV2 client) {
        var response=client.query(QueryReq.builder().collectionName(COLLECTION).filter("")
                .outputFields(List.of("vector_id","document_id","chunk_index","embedding_version","vector"))
                .consistencyLevel(ConsistencyLevel.BOUNDED).limit(53).build());
        check(response!=null && response.getQueryResults()!=null,"MILVUS_VECTOR_QUERY_INVALID");
        return response.getQueryResults().stream().map(r->new TreeMap<>(r.getEntity()))
                .sorted(Comparator.comparing(r->r.get("vector_id").toString())).toList();
    }
    String checkVectors(List<TreeMap<String,Object>> rows,List<Map<String,String>> chunks) throws Exception {
        String hash=RagE2ePolicy.hash(JSON.writeValueAsBytes(rows));
        check(rows.size()==52 && hash.equals(contract.path("milvusFrozen").path("fullVectorRowsSha256").asText()),"FROZEN_VECTORS_CHANGED");
        var byId=new HashMap<String,Map<String,String>>(); chunks.forEach(c->byId.put(c.get("vector_id"),c));
        var ids=new HashSet<String>();
        for (var row:rows) {
            String id=row.get("vector_id").toString(); var chunk=byId.get(id);
            check(ids.add(id) && chunk!=null && chunk.get("document_id").equals(row.get("document_id").toString())
                    && chunk.get("chunk_index").equals(row.get("chunk_index").toString())
                    && RagE2ePolicy.VERSION.equals(row.get("embedding_version")),"VECTOR_SQL_MAPPING_MISMATCH");
        }
        check(ids.equals(byId.keySet()),"VECTOR_ID_SET_MISMATCH"); return hash;
    }
    static SearchReq searchRequest(List<Float> vector) {
        return SearchReq.builder().collectionName(COLLECTION).annsField("vector").metricType(IndexParam.MetricType.COSINE)
                .data(List.of(new FloatVec(vector))).limit(5).filter("embedding_version == {embeddingVersion}")
                .filterTemplateValues(Map.of("embeddingVersion",RagE2ePolicy.VERSION))
                .outputFields(List.of("document_id","chunk_index","embedding_version"))
                .consistencyLevel(ConsistencyLevel.BOUNDED).build();
    }
    static List<Map<String,Object>> rawHits(SearchResp response) {
        check(response!=null && response.getSearchResults()!=null && response.getSearchResults().size()==1
                && response.getSearchResults().getFirst()!=null,"MILVUS_SEARCH_GROUP_INVALID");
        var group=response.getSearchResults().getFirst(); check(group.size()<=5,"MILVUS_TOPK_EXCEEDED");
        var hits=new ArrayList<Map<String,Object>>(); var seen=new HashSet<String>();
        for (var result:group) {
            check(result!=null && result.getId() instanceof String && result.getEntity()!=null
                    && result.getScore()!=null && Float.isFinite(result.getScore()),"MILVUS_HIT_INVALID");
            String id=(String)result.getId(); var entity=result.getEntity();
            check(!id.isBlank() && seen.add(id) && entity.get("document_id") instanceof Long d && d>0
                    && entity.get("chunk_index") instanceof Long c && c>0 && c<=Integer.MAX_VALUE
                    && RagE2ePolicy.VERSION.equals(entity.get("embedding_version")),"MILVUS_HIT_METADATA_INVALID");
            var hit=new LinkedHashMap<String,Object>(); hit.put("rank",hits.size()+1); hit.put("vectorId",id);
            hit.put("cosineFloat32",(double)result.getScore());
            hit.put("cosineFloat32Bits",String.format("%08x",Float.floatToRawIntBits(result.getScore())));
            hit.put("documentId",entity.get("document_id")); hit.put("chunkIndex",entity.get("chunk_index"));
            hit.put("embeddingVersion",entity.get("embedding_version")); hits.add(hit);
        }
        return hits;
    }
    static List<Float> embeddingValues(JsonNode response) {
        check(RagE2ePolicy.MODEL.equals(response.path("model").asText()),"EMBEDDING_RESPONSE_MODEL_MISMATCH");
        var data=response.path("data"); check(data.isArray() && data.size()==1 && data.get(0).path("index").isIntegralNumber()
                && data.get(0).path("index").canConvertToInt()
                && data.get(0).path("index").asInt()==0,"EMBEDDING_RESPONSE_INDEX_INVALID");
        var values=data.get(0).path("embedding"); check(values.isArray() && values.size()==1024,"EMBEDDING_RESPONSE_DIMENSION");
        var vector=new ArrayList<Float>();
        for (var value:values) { check(value.isNumber() && Float.isFinite(value.floatValue()),"EMBEDDING_NONFINITE_OR_NULL"); vector.add(value.floatValue()); }
        if (response.has("usage")) for (String field:List.of("prompt_tokens","total_tokens")) {
            var count=response.path("usage").get(field);
            if(count!=null) check(count.isIntegralNumber() && count.canConvertToLong() && count.asLong()>=0,"EMBEDDING_USAGE_INVALID");
        }
        return List.copyOf(vector);
    }
    interface EmbeddingTransport { HttpReply post(byte[] request) throws Exception; }
    record HttpReply(int status,byte[] body) { }
    Map<String,Object> intent(String queryId,String operation,Budget budget) throws Exception {
        budget.take(); var event=new LinkedHashMap<String,Object>();
        event.put("sequence",events.size()+1); event.put("queryId",queryId); event.put("operation",operation);
        event.put("attempt",budget.attempts); event.put("startedUtc",Instant.now().toString());
        event.put("outcome","INTENT_PERSISTED_UNKNOWN_IF_INTERRUPTED"); events.add(event); save(); return event;
    }
    List<Float> embed(String id,String query,Map<String,Object> record,EmbeddingTransport transport) throws Exception {
        byte[] request=JSON.writeValueAsBytes(Map.of("model",RagE2ePolicy.MODEL,"input",List.of(query),"dimensions",1024,"encoding_format","float"));
        RagE2ePolicy.modelRequest(JSON.readTree(request),true);
        Files.write(out.resolve(id+".embedding-request.json"),request,StandardOpenOption.CREATE_NEW);
        var event=intent(id,"EMBEDDING_HTTP",embedding); event.put("requestSha256",RagE2ePolicy.hash(request));
        save(); var reply=transport.post(request); // Exactly one POST; no retry wrapper.
        event.put("httpStatus",reply.status()); event.put("responseSha256",RagE2ePolicy.hash(reply.body()));
        check(reply.status()==200,"EMBEDDING_HTTP_FAILED_NO_RETRY");
        String body=new String(reply.body(),StandardCharsets.UTF_8);
        for (String key:List.of("P1_DB_PASSWORD","EMBEDDING_API_KEY","E2E_MILVUS_TOKEN"))
            check(env(key).isEmpty() || !body.contains(env(key)),"PROVIDER_RESPONSE_SECRET_REJECTED");
        Files.write(out.resolve(id+".embedding-response.json"),reply.body(),StandardOpenOption.CREATE_NEW);
        var response=JSON.readTree(reply.body()); var info=new LinkedHashMap<String,Object>();
        info.put("model",response.path("model").asText()); info.put("profile",RagE2ePolicy.VERSION);
        info.put("requestedDimension",1024); info.put("dimension",response.path("data").path(0).path("embedding").size());
        info.put("usage",response.get("usage")); info.put("requestSha256",event.get("requestSha256"));
        info.put("responseSha256",event.get("responseSha256")); record.put("embedding",info); save();
        var vector=embeddingValues(response); event.put("outcome","SUCCESS"); event.put("endedUtc",Instant.now().toString()); save(); return vector;
    }
    static List<String> rejection(Map<String,String> row,Map<String,Object> hit) {
        var reasons=new ArrayList<String>(); if (row==null) return List.of("VECTOR_NOT_FOUND_IN_MYSQL");
        if (row.get("joinedDocumentId")==null) reasons.add("DOCUMENT_NOT_FOUND");
        if (!"ACTIVE".equals(row.get("chunkStatus"))) reasons.add("CHUNK_NOT_ACTIVE");
        if (!"READY".equals(row.get("documentStatus"))) reasons.add("DOCUMENT_NOT_READY");
        if (!Objects.equals(row.get("documentVersion"),row.get("currentDocumentVersion"))) reasons.add("DOCUMENT_VERSION_MISMATCH");
        if (!RagE2ePolicy.MODEL.equals(row.get("embeddingModel"))) reasons.add("EMBEDDING_MODEL_MISMATCH");
        if (!RagE2ePolicy.VERSION.equals(row.get("embeddingVersion"))) reasons.add("EMBEDDING_PROFILE_MISMATCH");
        if (!hit.get("documentId").toString().equals(row.get("documentId"))) reasons.add("MILVUS_DOCUMENT_ID_MISMATCH");
        if (!hit.get("chunkIndex").toString().equals(row.get("chunkIndex"))) reasons.add("MILVUS_CHUNK_INDEX_MISMATCH");
        return reasons;
    }
    static void annotateHit(Map<String,Object> hit,Map<String,String> row,boolean selected) {
        var reasons=rejection(row,hit); check(selected==reasons.isEmpty(),"HYDRATION_SQL_DIAGNOSTIC_DISAGREEMENT");
        boolean threshold=((Number)hit.get("cosineFloat32")).doubleValue()>=0.70d;
        hit.put("sqlEligible",selected); hit.put("sqlRejectionReasons",reasons); hit.put("passes070",threshold);
        hit.put("v0Retained",selected); hit.put("v1Retained",selected && threshold);
        hit.put("knowledge",row); hit.put("contentSha256",row==null || row.get("content")==null?null:RagE2ePolicy.hash(row.get("content")));
    }
    void hydrate(IndependentReadOnlySql sql,List<Map<String,Object>> hits) throws Exception {
        if (hits.isEmpty()) return;
        var ids=hits.stream().map(h->h.get("vectorId")).toList(); var parameters=new ArrayList<Object>(ids);
        parameters.add(RagE2ePolicy.MODEL); parameters.add(RagE2ePolicy.VERSION);
        var eligible=sql.query(IndependentReadOnlySql.idsSql(IndependentReadOnlySql.HYDRATE,ids.size()),parameters.toArray());
        var diagnostics=sql.query(IndependentReadOnlySql.idsSql(IndependentReadOnlySql.LOOKUP,ids.size()),ids.toArray());
        var selected=new HashSet<String>(); eligible.forEach(r->selected.add(r.get("vectorId")));
        var rows=new HashMap<String,Map<String,String>>(); diagnostics.forEach(r->check(rows.put(r.get("vectorId"),r)==null,"DUPLICATE_SQL_VECTOR_ID"));
        for (var hit:hits) annotateHit(hit,rows.get(hit.get("vectorId")),selected.contains(hit.get("vectorId")));
    }
    static Map<String,Object> counts(List<Map<String,Object>> hits) {
        long v0=hits.stream().filter(h->Boolean.TRUE.equals(h.get("v0Retained"))).count();
        long v1=hits.stream().filter(h->Boolean.TRUE.equals(h.get("v1Retained"))).count();
        return Map.of("rawTopKCount",hits.size(),"v0EvidenceCount",v0,"v1EvidenceCount",v1,
                "v0ZeroEvidence",v0==0,"v1ZeroEvidence",v1==0,"v1FilteredCount",hits.size()-v1);
    }
    void save() throws Exception {
        if(out==null) return;
        report.put("stage",stage); report.put("calls",Map.of("embeddingHttp",embedding.attempts,"milvusSearch",search.attempts,
                "deepseek",0,"jev",0,"businessAnswers",0,"mysqlWrites",0,"milvusWrites",0));
        report.put("queries",queries); report.put("executionLedger",events);
        long tokens=0; boolean complete=true; int withUsage=0;
        for (var query:queries) if(query.containsKey("embedding")) {
            var info=JSON.valueToTree(query.get("embedding")); var usage=info.path("usage").path("total_tokens");
            if(usage.isIntegralNumber() && usage.canConvertToLong() && usage.asLong()>=0) { tokens+=usage.asLong(); withUsage++; }
            else complete=false;
        }
        double rate=report.containsKey("embeddingBase")?RagE2ePolicy.embeddingRate(report.get("embeddingBase").toString()):0.50;
        report.put("cost",Map.of("reportedTotalTokens",tokens,"responsesWithUsage",withUsage,
                "completeForAttemptedRequests",complete && withUsage==embedding.attempts,"cnyPerMillionTokens",rate,
                "estimatedCnyFromReportedUsage",tokens*rate/1_000_000d,"estimateIsBillingHardCap",false,
                "priceSource","https://help.aliyun.com/zh/model-studio/model-pricing","priceCheckedDate","2026-10-12"));
        var scores=new ArrayList<Double>(); int completed=0,v0=0,v1=0,zero0=0,zero1=0;
        for (var query:queries) if("COMPLETE".equals(query.get("status"))) {
            completed++; var hits=(List<Map<String,Object>>)query.get("rawTopK5");
            for(var hit:hits) { scores.add(((Number)hit.get("cosineFloat32")).doubleValue()); if(Boolean.TRUE.equals(hit.get("v0Retained")))v0++; if(Boolean.TRUE.equals(hit.get("v1Retained")))v1++; }
            if(((Number)query.get("v0EvidenceCount")).intValue()==0)zero0++;
            if(((Number)query.get("v1EvidenceCount")).intValue()==0)zero1++;
        }
        scores.sort(Double::compareTo);
        var objective=new LinkedHashMap<String,Object>(); objective.put("completedQueries",completed);
        objective.put("v0EvidenceCount",v0); objective.put("v1EvidenceCount",v1); objective.put("filteredEligibleCount",v0-v1);
        objective.put("v0ZeroQueries",zero0); objective.put("v1ZeroQueries",zero1); objective.put("allCosinesSorted",scores);
        objective.put("relevanceMetrics","PENDING_HUMAN_LABELS"); report.put("objectiveSummary",objective);
        Path pending=out.resolve("results.json.pending"); JSON.writerWithDefaultPrettyPrinter().writeValue(pending.toFile(),report);
        Files.move(pending,out.resolve("results.json"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    void html() throws Exception {
        String encoded=Base64.getEncoder().encodeToString(Files.readAllBytes(out.resolve("results.json")));
        String template=Files.readString(Path.of("tools/rag-p1/annotation-template.html"));
        Files.writeString(out.resolve("annotation.html"),template.replace("__RESULTS_BASE64__",encoded)
                .replace("__RESULTS_SHA256__",sha(out.resolve("results.json"))),StandardOpenOption.CREATE_NEW);
        Files.writeString(out.resolve("results.sha256"),sha(out.resolve("results.json"))+"  results.json\n",StandardOpenOption.CREATE_NEW);
    }
    static Path reserve(Path output) throws Exception {
        Files.createDirectories(output.getParent()); Files.createDirectory(output);
        Files.writeString(output.resolve("EXECUTION_RESERVED.lock"),Instant.now().toString(),StandardOpenOption.CREATE_NEW); return output;
    }
    void run() throws Exception {
        offlineGate();
        check(!env("P1_DB_PASSWORD").isBlank() && !env("EMBEDDING_API_KEY").isBlank(),"LOCAL_CREDENTIALS_MISSING");
        String base=env("EMBEDDING_BASE_URL"); if(base.isBlank())base=RagE2ePolicy.EMBEDDING_ENDPOINT;
        RagE2ePolicy.endpoint(base,true); final String endpoint=base;
        out=reserve(OUTPUT); report.put("INDEPENDENT_RETRIEVAL_RUN","BLOCKED"); report.put("startedUtc",Instant.now().toString());
        report.put("datasetSha256",QUERY_SHA); report.put("corpusContractSha256",CONTRACT_SHA); report.put("embeddingBase",base);
        report.put("queryContract","Exact current Java EvaluationRagQueryBuilder output from frozen builderInput. Frozen synthetic scoring points and main/follow-up context included; no answers, coverageBasis, labels or extra context. This is not a question-text-only evaluation.");
        report.put("embeddingTransport","Direct OpenAI-compatible HTTP, one input per POST, dimensions=1024, encoding_format=float; no Spring context or retries.");
        report.put("hydrationMapperSha256",sha(MAPPER)); report.put("sqlAudit",new ArrayList<>());
        report.put("queryBuilderSourceSha256",sha(Path.of("src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRagQueryBuilder.java")));
        int totalQueryBytes=0;
        for(var query:dataset.path("queries"))totalQueryBytes+=buildQuery(query).getBytes(StandardCharsets.UTF_8).length;
        double rate=RagE2ePolicy.embeddingRate(base);
        double estimate=totalQueryBytes*rate/1_000_000d;
        check(estimate<1.0,"CONSERVATIVE_ESTIMATE_ABOVE_ONE_CNY");
        report.put("budgetGate",Map.of("maxEmbeddingHttp",16,"maxMilvusSearch",16,"retries",0,
                "totalQueryUtf8Bytes",totalQueryBytes,"estimateTreatingEveryByteAsTokenCny",estimate,
                "estimateIsBillingHardCap",false,"maxBytesPerQuery",8192));
        for(var query:dataset.path("queries")) {
            var item=new LinkedHashMap<String,Object>(); item.put("queryId",query.path("queryId").asText());
            item.put("questionText",query.path("questionText").asText()); item.put("testType",query.path("testType").asText());
            item.put("actualQueryText",buildQuery(query)); item.put("querySha256",query.path("productionStyleQuerySha256").asText());
            item.put("frozenBuilderInput",query.get("builderInput")); item.put("status","NOT_STARTED"); queries.add(item);
        }
        save(); MilvusClientV2 client=null; Connection connection=null;
        try {
            stage="MYSQL_READ_ONLY_GATE";
            connection=DriverManager.getConnection(RagE2ePolicy.jdbc("ai_interview_test"),"rag_e2e_writer",env("P1_DB_PASSWORD"));
            try(var sql=new IndependentReadOnlySql(connection,corpusSql())) {
                report.put("sqlAudit",sql.audit); report.put("mysqlIdentity",sql.identity);
                String grantee="'rag_e2e_writer'@'127.0.0.1'";
                var grants=sql.query(IndependentReadOnlySql.PRIVILEGES,grantee); var schemaGrants=sql.query(IndependentReadOnlySql.SCHEMA_PRIVILEGES,grantee);
                for(String table:RagE2ePolicy.CORPUS) check(grants.stream().anyMatch(g->table.equals(g.get("TABLE_NAME")) && "SELECT".equals(g.get("PRIVILEGE_TYPE")))
                        || schemaGrants.stream().anyMatch(g->"SELECT".equals(g.get("PRIVILEGE_TYPE"))),"CORPUS_SELECT_PRIVILEGE_MISSING");
                report.put("mysqlPrivileges",grants); report.put("mysqlSchemaPrivileges",schemaGrants);
                report.put("readOnlyProtection","Fixed START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY; Connector/J readOnly=true; server transaction_read_only=1; fixed SELECT allowlist; rollback only. Account write grants do not protect this run.");
                var before=corpus(sql); report.put("corpusBefore",contract.get("mysqlFrozen")); save();
                stage="MILVUS_READ_ONLY_GATE";
                var config=ConnectConfig.builder().uri("http://127.0.0.1:19530").dbName("default").connectTimeoutMs(5000).rpcDeadlineMs(20000);
                if(!env("E2E_MILVUS_TOKEN").isBlank())config.token(env("E2E_MILVUS_TOKEN"));
                client=new MilvusClientV2(config.build()); client.retryConfig(RetryConfig.builder().maxRetryTimes(1).retryOnRateLimit(false).build());
                var description=client.describeCollection(DescribeCollectionReq.builder().collectionName(COLLECTION).build());
                var index=client.describeIndex(DescribeIndexReq.builder().collectionName(COLLECTION).fieldName("vector").build());
                validateSchema(description,index);
                check(Boolean.TRUE.equals(client.getLoadState(GetLoadStateReq.builder().collectionName(COLLECTION).build())),"MILVUS_COLLECTION_NOT_LOADED");
                String vectorHash=checkVectors(vectors(client),before.get("knowledge_chunk"));
                report.put("milvusIdentity",Map.ofEntries(Map.entry("database","default"),Map.entry("collection",COLLECTION),
                        Map.entry("collectionId",Long.toString(COLLECTION_ID)),Map.entry("rows",52),Map.entry("dimension",1024),
                        Map.entry("consistency","BOUNDED"),Map.entry("metric","COSINE"),Map.entry("schema",JSON.valueToTree(description)),
                        Map.entry("index",JSON.valueToTree(index)),Map.entry("fullVectorRowsSha256",vectorHash),Map.entry("mappingVerified",true),
                        Map.entry("authentication",env("E2E_MILVUS_TOKEN").isBlank()?"LOCAL_ANONYMOUS_NO_SERVER_RBAC_PROOF":"TOKEN_SUPPLIED_NO_SERVER_RBAC_PROOF")));
                report.put("milvusProtection","Only describeCollection, describeIndex, getLoadState, query, search and close; no initializer, load, create, insert, upsert or delete. SDK maxRetryTimes=1 means one RPC attempt.");
                save();
                var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
                EmbeddingTransport transport=body->{
                    var request=HttpRequest.newBuilder(URI.create(endpoint+"/embeddings")).timeout(Duration.ofSeconds(20))
                            .header("Content-Type","application/json").header("Authorization","Bearer "+env("EMBEDDING_API_KEY"))
                            .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
                    var response=http.send(request,HttpResponse.BodyHandlers.ofByteArray()); return new HttpReply(response.statusCode(),response.body());
                };
                stage="RETRIEVAL";
                for(var query:queries) {
                    String id=query.get("queryId").toString(); query.put("status","RUNNING"); save();
                    var vector=embed(id,query.get("actualQueryText").toString(),query,transport);
                    var request=searchRequest(vector); var event=intent(id,"MILVUS_SEARCH",search);
                    event.put("request",JSON.valueToTree(request)); save();
                    var response=client.search(request);
                    Files.writeString(out.resolve(id+".milvus-response.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(response),StandardOpenOption.CREATE_NEW);
                    event.put("responseSha256",sha(out.resolve(id+".milvus-response.json")));
                    var hits=rawHits(response); query.put("rawTopK5",hits); save();
                    hydrate(sql,hits); query.putAll(counts(hits)); query.put("status","COMPLETE");
                    event.put("outcome","SUCCESS"); event.put("endedUtc",Instant.now().toString()); save();
                    System.out.println(id+" V0="+query.get("v0EvidenceCount")+" V1="+query.get("v1EvidenceCount"));
                }
                stage="UNCHANGED_AFTER_GATE";
                // End the consistent snapshot; a new connection/READ ONLY snapshot sees current committed data.
                sql.close(); connection=null;
                try(var after=new IndependentReadOnlySql(DriverManager.getConnection(RagE2ePolicy.jdbc("ai_interview_test"),"rag_e2e_writer",env("P1_DB_PASSWORD")),corpusSql())) {
                    corpus(after); sql.audit.addAll(after.audit);
                }
                checkVectors(vectors(client),before.get("knowledge_chunk"));
                var afterDescription=client.describeCollection(DescribeCollectionReq.builder().collectionName(COLLECTION).build());
                check(afterDescription.getCollectionID()==COLLECTION_ID,"MILVUS_COLLECTION_CHANGED_AFTER");
                check(sha(DATASET).equals(QUERY_SHA),"FROZEN_DATASET_CHANGED_DURING_RUN");
                report.put("unchanged",Map.of("testCorpus191Rows",true,"testVectors52",true,"frozenQueries",true,
                        "sourceCorpusAndVectors","NOT_ACCESSED_BY_THIS_RUN; frozen source hashes reused"));
                report.put("INDEPENDENT_RETRIEVAL_RUN","PASS");
            }
        } catch(Exception error) {
            report.put("INDEPENDENT_RETRIEVAL_RUN",embedding.attempts==0 && search.attempts==0?"BLOCKED":"FAIL");
            // Only controlled codes/classes, never provider/JDBC messages or stack traces carrying secrets.
            report.put("failure",Map.of("stage",stage,"exceptionClass",error.getClass().getName(),
                    "code",error instanceof SecurityException?Objects.toString(error.getMessage(),"GUARD_FAILED"):"EXTERNAL_OR_EXECUTION_FAILURE_NO_RETRY"));
            for(Throwable cause=error;cause!=null;cause=cause.getCause()) if(cause instanceof SQLException databaseError) {
                report.put("sqlFailure",Map.of("sqlState",Objects.toString(databaseError.getSQLState(),"UNKNOWN"),
                        "vendorCode",databaseError.getErrorCode())); break;
            }
            for(var event:events) if(event.get("outcome").equals("INTENT_PERSISTED_UNKNOWN_IF_INTERRUPTED"))event.put("outcome","UNKNOWN_OR_FAILED_NO_RETRY");
            for(var query:queries) if("RUNNING".equals(query.get("status")))query.put("status","FAILED_NO_RETRY");
        } finally {
            if(connection!=null)try{connection.close();}catch(Exception ignored){}
            if(client!=null)try{client.close();}catch(Exception ignored){}
            report.put("endedUtc",Instant.now().toString()); save(); html();
        }
        System.out.println("INDEPENDENT_RETRIEVAL_RUN="+report.get("INDEPENDENT_RETRIEVAL_RUN")+"; "+out.toAbsolutePath());
        if(!"PASS".equals(report.get("INDEPENDENT_RETRIEVAL_RUN")))throw new IllegalStateException("STOPPED_NO_RETRY_SEE_RESULTS_JSON");
    }
    public static void main(String[] args) throws Exception {
        if(args.length==1 && args[0].equals("offline")) { new IndependentRetrieval().offlineGate(); System.out.println("OFFLINE_GATE=PASS; liveCalls=0"); return; }
        check(args.length==1 && args[0].equals("execute-once"),"EXPLICIT_EXECUTE_ONCE_REQUIRED"); new IndependentRetrieval().run();
    }
}
