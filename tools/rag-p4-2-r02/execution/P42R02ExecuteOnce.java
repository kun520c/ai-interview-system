import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.config.EnvironmentIsolationInitializer;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.*;
import com.kun.aiinterview.knowledge.admission.*;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.retrieval.RetrievedChunk;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Standalone, sequential experiment. No Spring refresh, collection initializer or business writes. */
public final class P42R02ExecuteOnce {
    static final String AUTH = "AUTHORIZE P4.2 P42-R02-20261014 8 EACH ONCE MAX24 CNY1";
    static final String PREP_SHA = "5309ba13666879387a2c876c98deaf2dd2a34003de26fc83169a40cff014d064";
    static final String QUESTIONS_SHA = "73ca5d65649a0d2270a3f6cc209f36a0d2c775e7e43e4079f1f269a2e1ba318c";
    static final String PLAN_SHA = "16df13f6f7ed35968b6aa81b4f6504df36c4d07aabeee69a30b2742b7089fd47";
    static final Path PREP = Path.of("target/rag-p4-2/prepared");
    static final Path SEAL = Path.of("target/rag-p4-2/execution-r02-20261014/manifest.json");
    static final Path DELTA = Path.of("target/rag-p4-2/execution-prep-20261014/evidence/delta.json");
    static final String DELTA_SHA = "4359a5f12384f6f79a3fc66b6ac0bdbbcba8d5b56be2c08a1f2f3277d0845222";
    static final Path RUN = Path.of("target/rag-p4-2/run-r02-20261014");
    static final com.fasterxml.jackson.databind.ObjectMapper JSON = PrimarySemanticGate.JSON;
    enum Stage {
        EMBEDDING("qwen3.7-text-embedding", 3000, 0, .5, 0),
        MILVUS_SEARCH("MILVUS_COSINE", 0, 0, 0, 0),
        RERANK("qwen3.7-text-rerank", 38888, 0, .5, 0),
        SEMANTIC_GATE("deepseek-v4-flash", 21452, 3072, 2, 8);
        final String model; final long inputProxy, outputCap; final double inputRate, outputRate;
        Stage(String model, long inputProxy, long outputCap, double inputRate, double outputRate) {
            this.model=model; this.inputProxy=inputProxy; this.outputCap=outputCap; this.inputRate=inputRate; this.outputRate=outputRate;
        }
        double proxy() { return (inputProxy*inputRate + outputCap*outputRate)/1e6; }
    }
    static final class Stop extends RuntimeException { Stop(String code) { super(code); } }
    static void require(boolean ok, String code) { if (!ok) throw new Stop(code); }
    static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    static String sha(Path p) throws Exception { return hash(Files.readAllBytes(p)); }
    static JsonNode read(Path p) throws Exception { return JSON.readTree(Files.readAllBytes(p)); }
    static void write(Path p, byte[] bytes) throws Exception {
        try (var file=FileChannel.open(p, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var buffer=ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) file.write(buffer); file.force(true);
        }
    }
    static void writeJson(Path p, Object value) throws Exception { write(p, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value)); }
    static void verifyMap(JsonNode map) throws Exception {
        require(map.isObject() && !map.isEmpty(), "EMPTY_SHA_MAP");
        for (var it=map.fields(); it.hasNext();) { var e=it.next(); require(sha(Path.of(e.getKey())).equals(e.getValue().asText()), "FROZEN_FILE_DRIFT"); }
    }
    static final String EXECUTION_ID = "P42-R02-20261014";
    static JsonNode verifyFrozen() throws Exception {
        JsonNode dataset = P42ExecuteOnce.verifyFrozen(); // Original baseline, delta, binaries and seal stay mandatory.
        require(sha(SEAL).equals(Files.readString(SEAL.resolveSibling("manifest.sha256")).strip()), "R02_SEAL_DRIFT");
        JsonNode seal = read(SEAL);
        require(seal.path("executionId").asText().equals(EXECUTION_ID) && seal.path("authorization").asText().equals(AUTH), "R02_IDENTITY_BINDING");
        require(seal.path("questionSha256").asText().equals(QUESTIONS_SHA) && seal.path("planSha256").asText().equals(PLAN_SHA), "R02_FROZEN_BINDING");
        verifyMap(seal.path("filesSha256")); verifyMap(seal.path("dependenciesSha256"));
        verifyMap(read(SEAL.resolveSibling("preservation-before.json")));
        Set<String> actual = new HashSet<>(), expected = new HashSet<>();
        for (var folder : seal.path("classDirectories")) try (var files = Files.walk(Path.of(folder.asText()))) {
            for (var p : files.filter(f -> f.toString().endsWith(".class")).toList()) actual.add(p.toString().replace('\\', '/'));
        }
        seal.path("filesSha256").fieldNames().forEachRemaining(p -> { if (p.endsWith(".class")) expected.add(p); });
        require(actual.equals(expected), "UNSEALED_RUNTIME_CLASS");
        contracts(dataset); return dataset;
    }
    static void verifyBaselineMap(JsonNode baseline,JsonNode entries) throws Exception {
        Map<String,JsonNode> overrides=new HashMap<>();
        for(var e:entries) require(baseline.has(e.path("path").asText()) && overrides.put(e.path("path").asText(),e)==null, "DELTA_PATH_BINDING");
        for(var it=baseline.fields();it.hasNext();) {
            var e=it.next(); var override=overrides.get(e.getKey()); String expected=e.getValue().asText();
            if(override!=null) {
                require(override.path("oldSha256").asText().equals(expected) && !override.path("currentSha256").asText().equals(expected), "DELTA_OLD_SHA_BINDING");
                expected=override.path("currentSha256").asText();
            }
            require(sha(Path.of(e.getKey())).equals(expected), "FROZEN_FILE_DRIFT");
        }
    }
    static List<QuestionContract> contracts(JsonNode dataset) throws Exception {
        require(dataset.path("questions").isArray() && dataset.path("questions").size()==8, "EIGHT_QUESTIONS_REQUIRED");
        List<QuestionContract> result=new ArrayList<>(); int i=0;
        for (var q:dataset.path("questions")) {
            var keys=new HashSet<String>(); q.fieldNames().forEachRemaining(keys::add);
            require(keys.equals(Set.of("questionId","question","versionConstraint","requirements")), "ONLINE_LABEL_LEAK");
            var contract=JSON.treeToValue(q, QuestionContract.class);
            require(contract.questionId().equals("H42-%02d".formatted(++i)) && contract.question().getBytes(StandardCharsets.UTF_8).length<=3000, "QUESTION_ORDER_OR_SIZE");
            result.add(contract);
        }
        return List.copyOf(result);
    }
    static void isolation() {
        try (var ctx=new GenericApplicationContext()) {
            ctx.getEnvironment().setActiveProfiles("test-real");
            ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("p42-fixed-isolation", Map.of(
                    "spring.datasource.url", RagE2ePolicy.jdbc("ai_interview_test"), "milvus.collection-name", IndependentRetrieval.COLLECTION)));
            new EnvironmentIsolationInitializer().initialize(ctx); // Before JDBC or Milvus; never refresh.
        }
    }
    record Reply(int status, byte[] body) {}
    interface HttpTransport { Reply post(Stage stage, byte[] body) throws Exception; }
    interface RawSearchCapture { void save(JsonNode raw) throws Exception; }
    interface Resources extends AutoCloseable {
        Object before() throws Exception;
        P3ReadOnlyRetrieval.SearchBatch search(List<Float> vector,RawSearchCapture capture) throws Exception;
        Object after() throws Exception;
        void close() throws Exception;
    }
    interface ResourceFactory { Resources open() throws Exception; }
    interface Guard { void verify() throws Exception; }
    static final class Budget {
        final EnumMap<Stage,Integer> remaining=new EnumMap<>(Stage.class);
        double known; boolean unknown;
        Budget() { for (var s:Stage.values()) remaining.put(s,8); }
        void check() { require(!unknown && Double.isFinite(known) && known+remaining.entrySet().stream().mapToDouble(e->e.getValue()*e.getKey().proxy()).sum()<=1.0, "BUDGET_REFERENCE_STOP"); }
        void accept(Stage stage, JsonNode usage, ObjectNode receipt) {
            String inputField=stage==Stage.EMBEDDING ? "total_tokens" : "prompt_tokens";
            long in=token(usage, inputField), out=stage==Stage.SEMANTIC_GATE ? token(usage,"completion_tokens") : 0;
            if (stage==Stage.SEMANTIC_GATE) require(token(usage,"total_tokens")==Math.addExact(in,out), "USAGE_TOTAL_MISMATCH");
            double cost=(in*stage.inputRate+out*stage.outputRate)/1e6;
            receipt.set("usage", usage); receipt.put("inputTokensCharged",in).put("outputTokensCharged",out).put("estimatedCny",cost);
            known+=cost; remaining.put(stage,remaining.get(stage)-1); unknown=false;
            require(in<=stage.inputProxy && out<=stage.outputCap, "USAGE_EXCEEDS_FROZEN_PROXY_STOP"); check();
        }
        static long token(JsonNode usage,String field) {
            JsonNode v=usage.path(field); require(v.isIntegralNumber() && v.canConvertToLong() && v.longValue()>=0,"MISSING_OR_INVALID_USAGE_STOP"); return v.longValue();
        }
    }
    final Path output; final HttpTransport http; final Guard guard; final Budget budget=new Budget();
    final List<ObjectNode> rows=new ArrayList<>(), receipts=new ArrayList<>();
    final Set<String> attempts=new HashSet<>(); final EnumMap<Stage,Integer> counts=new EnumMap<>(Stage.class);
    final String prompt; final String sealHash; final String origin; final List<String> secrets;
    String failurePhase="BEFORE_RESERVATION";
    ObjectNode runtimeMetadata=JSON.createObjectNode().put("transport","INJECTED_MOCK_NO_NETWORK");
    @FunctionalInterface interface Operation<T> { T run() throws Exception; }
    <T> T measure(ObjectNode row,Stage stage,Operation<T> operation) throws Exception {
        failurePhase=stage.name();
        long start=System.nanoTime();
        ObjectNode validation=JSON.createObjectNode().put("stage",stage.name()).put("status","VALIDATED");
        try { return operation.run(); }
        catch(Exception error) { validation.put("status","FAILED_STOP").put("failureCode",code(error)); validation.set("failureDiagnostics",diagnostics(error,stage.name())); throw error; }
        finally {
            ObjectNode times=row.has("stageElapsedMs")?(ObjectNode)row.path("stageElapsedMs"):row.putObject("stageElapsedMs");
            times.put(stage.name(),ms(start));
            Path dir=output.resolve(row.path("questionId").asText()).resolve(stage.name());
            if(Files.isDirectory(dir)) { validation.put("fullStageElapsedMs",times.path(stage.name()).asDouble()); writeJson(dir.resolve("validation.json"),validation); }
        }
    }
    P42R02ExecuteOnce(Path output, HttpTransport http, Guard guard, String prompt, String sealHash, String origin, List<String> secrets) {
        this.output=output; this.http=http; this.guard=guard; this.prompt=prompt; this.sealHash=sealHash; this.origin=origin; this.secrets=List.copyOf(secrets);
        for(var s:Stage.values()) counts.put(s,0);
    }
    ObjectNode attempt(String id,Stage stage,byte[] body) throws Exception {
        guard.verify(); budget.check();
        require(attempts.add(id+":"+stage) && counts.get(stage)<8,"SECOND_ATTEMPT_FORBIDDEN");
        Path dir=output.resolve(id).resolve(stage.name()); Files.createDirectory(dir);
        write(dir.resolve("request.json"),body);
        ObjectNode receipt=JSON.createObjectNode().put("questionId",id).put("stage",stage.name()).put("requestedModel",stage.model)
                .put("attempt",1).put("startedUtc",Instant.now().toString()).put("requestSha256",hash(body)).put("status","INTENT_UNKNOWN_IF_INTERRUPTED")
                .put("remainingBudgetProxyCnyBefore",budget.remaining.entrySet().stream().mapToDouble(e->e.getValue()*e.getKey().proxy()).sum())
                .put("knownCostCnyBefore",budget.known);
        receipt.put("elapsedScope",stage==Stage.MILVUS_SEARCH?"SEARCH_AND_MYSQL_HYDRATION":"HTTP_AND_USAGE_VALIDATION");
        receipt.put("endpoint",runtimeMetadata.path("endpoints").path(stage.name()).asText(origin.equals("MOCK")?"MOCK":"FIXED_READ_ONLY_MILVUS_SEARCH"));
        writeJson(dir.resolve("ATTEMPT_RESERVED.lock"),receipt); // Durable before provider/RPC send.
        counts.put(stage,counts.get(stage)+1); receipts.add(receipt); return receipt;
    }
    static double ms(long start) { return (System.nanoTime()-start)/1e6; }
    // Future-only diagnostic addition: never persist Throwable messages, provider bodies or credentials.
    static ObjectNode failureDiagnostics(Throwable error,String phase) {
        var result=JSON.createObjectNode().put("phase",phase);
        var chain=result.putArray("causeChain"); var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(Throwable t=error;t!=null && seen.add(t) && chain.size()<12;t=t.getCause()) {
            var item=chain.addObject().put("type",t.getClass().getName());
            result.put("rootExceptionType",t.getClass().getName());
            if(t.getClass().getSimpleName().equals("StatusRuntimeException")) {
                try {
                    Object status=t.getClass().getMethod("getStatus").invoke(t);
                    String grpc=status.getClass().getMethod("getCode").invoke(status).toString();
                    if(grpc.matches("[A-Z_]{1,32}")) item.put("grpcCode",grpc);
                } catch(ReflectiveOperationException ignored) { item.put("grpcCode","UNAVAILABLE_TO_DIAGNOSTIC"); }
            }
            var frames=item.putArray("frames");
            for(var f:Arrays.stream(t.getStackTrace()).limit(6).toList())
                frames.addObject().put("class",f.getClassName()).put("method",f.getMethodName()).put("line",f.getLineNumber());
        }
        String grpc="NONE";
        for(var item:chain) if(item.has("grpcCode")) grpc=item.path("grpcCode").asText();
        result.put("grpcStatusCode",grpc).put("rootCauseSummary",phase+"; root="+result.path("rootExceptionType").asText()+"; grpc="+grpc+"; exception messages omitted");
        return result;
    }
    ObjectNode diagnostics(Throwable error,String phase) {
        var result=failureDiagnostics(error,phase); result.set("externalCallsAtFailure",JSON.valueToTree(counts)); return result;
    }
    static String code(Exception error) {
        for(Throwable cause=error; cause!=null; cause=cause.getCause()) {
            if(cause instanceof Stop) return cause.getMessage();
            String owner=cause.getStackTrace().length==0?"":cause.getStackTrace()[0].getClassName();
            if(Set.of("RagE2ePolicy",PrimarySemanticGate.class.getName(),BailianRerankClient.class.getName()).contains(owner) &&
                    cause.getMessage()!=null && cause.getMessage().matches("[A-Z0-9_]{1,128}")) return cause.getMessage();
        }
        return error.getClass().getSimpleName()+"_NO_RETRY";
    }
    byte[] sanitize(byte[] body,ObjectNode receipt) {
        String text=new String(body,StandardCharsets.UTF_8); boolean echo=false;
        for(String secret:secrets) if(!secret.isBlank() && text.contains(secret)) { echo=true; text=text.replace(secret,"[REDACTED]"); }
        receipt.put("secretRedacted",echo); return echo ? text.getBytes(StandardCharsets.UTF_8) : body;
    }
    Reply send(String id,Stage stage,byte[] body) throws Exception {
        ObjectNode receipt=attempt(id,stage,body); long start=System.nanoTime(); Path dir=output.resolve(id).resolve(stage.name());
        budget.unknown=true;
        try {
            Reply reply=http.post(stage,body); receipt.put("httpStatus",reply.status).put("responseCaptureComplete",reply.body.length<=1024*1024);
            receipt.put(reply.body.length<=1024*1024?"originalResponseSha256":"capturedOversizePrefixSha256",hash(reply.body));
            byte[] safe=sanitize(reply.body,receipt); write(dir.resolve("response.json"),safe); receipt.put("responseSha256",hash(safe));
            require(!receipt.path("secretRedacted").asBoolean(),"SECRET_ECHO_STOP");
            require(reply.status==200,"HTTP_STATUS_STOP"); require(safe.length<=1024*1024,"RESPONSE_SIZE_STOP");
            JsonNode response=JSON.readTree(safe); receipt.put("responseId",response.path("id").asText(response.path("request_id").asText()));
            receipt.put("returnedModel",response.path("model").asText(stage==Stage.RERANK ? "NOT_RETURNED_BY_PROVIDER" : ""));
            budget.accept(stage,response.path("usage"),receipt); receipt.put("status","RESPONSE_RECEIVED_USAGE_VERIFIED"); return reply;
        } catch(Exception error) { receipt.put("status","FAILED_STOP").put("failureCode",code(error)); receipt.set("failureDiagnostics",diagnostics(error,stage.name())); throw error; }
        finally { receipt.put("elapsedMs",ms(start)).put("endedUtc",Instant.now().toString()); writeJson(dir.resolve("receipt.json"),receipt); }
    }
    RestClient client(String id,Stage stage) {
        String base=stage==Stage.RERANK?rerankEndpoint().toString():"https://api.deepseek.com";
        return RestClient.builder().requestFactory((uri,method)->new org.springframework.http.client.AbstractClientHttpRequest() {
            private final ByteArrayOutputStream body=new ByteArrayOutputStream();
            public org.springframework.http.HttpMethod getMethod() { return method; }
            public URI getURI() { return uri; }
            protected OutputStream getBodyInternal(HttpHeaders headers) { return body; }
            protected ClientHttpResponse executeInternal(HttpHeaders headers) { throw new Stop("UNACCOUNTED_TRANSPORT_FORBIDDEN"); }
        }).requestInterceptor((request,body,unused)-> {
            require(request.getURI().toString().equals(stage==Stage.RERANK?base:"https://api.deepseek.com/chat/completions"),"ADAPTER_ENDPOINT_PROTOCOL_DRIFT");
            Reply reply;
            try { reply=send(id,stage,body); } catch(IOException e) { throw e; } catch(Exception e) { throw new IOException("P42_STAGE_STOP",e); }
            return new ClientHttpResponse() {
                public HttpStatusCode getStatusCode() { return HttpStatusCode.valueOf(reply.status); }
                public String getStatusText() { return "P42_CAPTURED"; }
                public HttpHeaders getHeaders() { var h=new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }
                public InputStream getBody() { return new ByteArrayInputStream(reply.body); }
                public void close() {}
            };
        }).baseUrl(base).build();
    }
    URI rerankEndpoint() { return URI.create(runtimeMetadata.path("endpoints").path("RERANK").asText("https://mock.cn-beijing.maas.aliyuncs.com"+BailianRerankClient.PATH)); }
    static List<RetrievedChunk> chunks(List<Map<String,Object>> hits) {
        require(hits.size()==5,"FIVE_FROZEN_CANDIDATES_REQUIRED");
        List<RetrievedChunk> items=new ArrayList<>(); Set<String> ids=new HashSet<>(); int rank=0;
        for(var hit:hits) {
            JsonNode h=JSON.valueToTree(hit), k=h.path("knowledge"); String id=h.path("vectorId").asText();
            require(h.path("sqlEligible").asBoolean() && ids.add(id) && !id.isBlank(),"CANDIDATE_ID_ELIGIBILITY");
            require(k.path("content").isTextual() && !k.path("content").asText().isBlank() && k.path("content").asText().length()<=1200 &&
                    k.path("title").isTextual() && k.path("title").asText().length()<=256,"CANDIDATE_SIZE");
            require(k.path("vectorId").asText().equals(id) && k.path("documentId").asLong()==h.path("documentId").asLong() &&
                    k.path("chunkIndex").asInt()==h.path("chunkIndex").asInt(),"CANDIDATE_IDENTITY");
            items.add(new RetrievedChunk(++rank,h.path("cosineFloat32").asDouble(),k.path("chunkId").asLong(),k.path("documentId").asLong(),
                    k.path("documentVersion").asInt(),k.path("chunkIndex").asInt(),id,k.path("title").asText(),
                    KnowledgeCategory.valueOf(k.path("category").asText()),k.path("source").isNull()?null:k.path("source").asText(),k.path("content").asText()));
        }
        return List.copyOf(items);
    }
    P3ReadOnlyRetrieval.SearchBatch search(String id,List<Float> vector,Resources service) throws Exception {
        ObjectNode request=JSON.createObjectNode().put("collection",IndependentRetrieval.COLLECTION).put("database","default").put("topK",5)
                .put("metric","COSINE").put("embeddingVersion",RagE2ePolicy.VERSION);
        request.set("vector",JSON.valueToTree(vector));
        request.set("sdkRequest",JSON.valueToTree(IndependentRetrieval.searchRequest(vector)));
        var receipt=attempt(id,Stage.MILVUS_SEARCH,JSON.writeValueAsBytes(request)); long start=System.nanoTime(); Path dir=output.resolve(id).resolve(Stage.MILVUS_SEARCH.name());
        try {
            var batch=service.search(vector,raw-> {
                writeJson(dir.resolve("response.json"),raw); receipt.put("responseSha256",sha(dir.resolve("response.json")));
            });
            writeJson(dir.resolve("hydrated.json"),batch.hits());
            receipt.put("responseSha256",sha(dir.resolve("response.json"))).put("hydratedSha256",sha(dir.resolve("hydrated.json")))
                    .put("status","SUCCESS").put("estimatedCny",0); budget.remaining.put(Stage.MILVUS_SEARCH,budget.remaining.get(Stage.MILVUS_SEARCH)-1);
            return batch;
        } catch(Exception e) { receipt.put("status","FAILED_STOP").put("failureCode",code(e)); receipt.set("failureDiagnostics",diagnostics(e,Stage.MILVUS_SEARCH.name())); throw e; }
        finally { receipt.put("elapsedMs",ms(start)).put("endedUtc",Instant.now().toString()); writeJson(dir.resolve("receipt.json"),receipt); }
    }
    void run(JsonNode dataset,ResourceFactory factory) throws Exception {
        var contracts=contracts(dataset); guard.verify(); budget.check();
        Files.createDirectory(output); // Exclusive batch reservation, including preflight failures. Never delete/resume.
        writeJson(output.resolve("EXECUTION_RESERVED.lock"),Map.of("executionId",EXECUTION_ID,"authorization",AUTH,"prepSha256",PREP_SHA,"executionSealSha256",sealHash,"startedUtc",Instant.now().toString(),"origin",origin));
        writeJson(output.resolve("frozen-questions.json"),dataset); write(output.resolve("system-prompt.txt"),prompt.getBytes(StandardCharsets.UTF_8));
        writeJson(output.resolve("runtime-metadata.json"),runtimeMetadata);
        ObjectNode summary=JSON.createObjectNode().put("executionId",EXECUTION_ID).put("origin",origin).put("executionStatus","IN_PROGRESS").put("qualityStatus","PENDING_INDEPENDENT_HUMAN_REVIEW")
                .put("retryCount",0).put("prepSha256",PREP_SHA).put("executionSealSha256",sealHash).put("budgetReferenceCny",1).put("estimateIsInvoiceHardCap",false)
                .put("startedUtc",Instant.now().toString());
        for(var c:contracts) { var row=JSON.createObjectNode().put("questionId",c.questionId()).put("status","NOT_STARTED"); rows.add(row); Files.createDirectory(output.resolve(c.questionId())); }
        Resources service=null; Exception stopped=null; long batchStart=System.nanoTime(); failurePhase="ISOLATION";
        try {
            isolation(); failurePhase="RESOURCE_PREFLIGHT"; service=factory.open(); writeJson(output.resolve("resources-before.json"),service.before()); failurePhase="CASE_EXECUTION";
            for(int i=0;i<contracts.size();i++) {
                var q=contracts.get(i); var row=rows.get(i); row.put("status","RUNNING"); long started=System.nanoTime();
                try {
                    byte[] body=JSON.writeValueAsBytes(Map.of("model",Stage.EMBEDDING.model,"input",List.of(q.question()),"dimensions",1024,"encoding_format","float"));
                    var vector=measure(row,Stage.EMBEDDING,()->IndependentRetrieval.embeddingValues(JSON.readTree(send(q.questionId(),Stage.EMBEDDING,body).body)));
                    Resources currentService=service;
                    var items=measure(row,Stage.MILVUS_SEARCH,()->chunks(search(q.questionId(),vector,currentService).hits()));
                    var reranker=new BailianRerankClient(client(q.questionId(),Stage.RERANK),rerankEndpoint());
                    var ranked=measure(row,Stage.RERANK,()->reranker.rank(q.question(),items)); var top2=ranked.stream().limit(2).toList();
                    var rawRank=read(output.resolve(q.questionId()+"/RERANK/response.json"));
                    var ranking=JSON.createArrayNode(); int r=0; for(var item:ranked) {
                        ranking.addObject().put("rank",++r).put("vectorId",item.vectorId()).put("relevanceScore",rawRank.at("/output/results/"+(r-1)+"/relevance_score").asDouble());
                    }
                    row.set("ranking",ranking); var gate=new PrimarySemanticGate(); var gateInput=gate.request(q,top2);
                    require(JSON.writeValueAsBytes(gateInput).length<=16000,"GATE_INPUT_SIZE_STOP");
                    var settings=new DeepSeekProperties(); settings.setModel(Stage.SEMANTIC_GATE.model); settings.setMaxTokens(3072);
                    var decision=measure(row,Stage.SEMANTIC_GATE,()-> {
                        var completion=new DeepSeekEvaluationClient(client(q.questionId(),Stage.SEMANTIC_GATE),settings,JSON).completeJson(prompt,gateInput.toString());
                        require(Set.of("deepseek-v4-flash","deepseek-flash","deepseek-v4.1-flash").contains(completion.model()),"UNEXPECTED_GATE_MODEL_STOP");
                        var accepted=gate.validate(q,top2,completion.rawJson());
                        row.set("gate",accepted.diagnostics());
                        require(!accepted.diagnostics().path("status").asText().contains("UNCERTAIN"),"GATE_UNCERTAIN_STOP");
                        return accepted;
                    }); row.set("gate",decision.diagnostics());
                    row.put("status","COMPLETE").put("wholeAnswerSufficiency","NOT_INDEPENDENTLY_VERIFIED");
                } catch(Exception e) {
                    row.put("status","FAILED_STOP").put("failureCode",code(e)).put("safeFallback","EMPTY_EVIDENCE_BATCH_STOP_NO_RETRY");
                    row.set("finalAdmittedCandidateIds",JSON.createArrayNode()); throw e;
                } finally {
                    if(row.path("status").asText().equals("COMPLETE")) row.set("finalAdmittedCandidateIds",row.path("gate").path("admittedCandidateIds"));
                    row.put("elapsedMs",ms(started)); writeJson(output.resolve(q.questionId()+"/result.json"),row);
                }
            }
            guard.verify(); summary.put("executionStatus","COMPLETE");
        } catch(Exception e) { stopped=e; summary.put("executionStatus","STOPPED").put("failureCode",code(e)); summary.set("failureDiagnostics",diagnostics(e,failurePhase)); }
        finally {
            if(service!=null) {
                try { writeJson(output.resolve("resources-after.json"),service.after()); summary.put("resourcesUnchanged",true); }
                catch(Exception e) { stopped=e; summary.put("executionStatus","STOPPED").put("resourcesUnchanged",false).put("resourceFailureCode",code(e)); summary.set("resourceFailureDiagnostics",diagnostics(e,"RESOURCE_POSTCHECK")); }
                try { service.close(); } catch(Exception e) { stopped=e; summary.put("executionStatus","STOPPED").put("closeFailureCode",code(e)); summary.set("closeFailureDiagnostics",diagnostics(e,"RESOURCE_CLOSE")); }
            }
            summary.set("calls",JSON.valueToTree(counts)); summary.set("questions",JSON.valueToTree(rows)); summary.set("stages",JSON.valueToTree(receipts));
            summary.put("estimatedKnownCny",budget.known).put("costCompleteForAttempts",!budget.unknown).put("wallElapsedMs",ms(batchStart)).put("endedUtc",Instant.now().toString());
            writeJson(output.resolve("summary.json"),summary);
            Map<String,String> artifacts=new TreeMap<>(); try(var paths=Files.walk(output)) {
                for(var p:paths.filter(Files::isRegularFile).toList()) artifacts.put(output.relativize(p).toString().replace('\\','/'),sha(p));
            }
            writeJson(output.resolve("artifacts.json"),artifacts); write(output.resolve("artifacts.sha256"),sha(output.resolve("artifacts.json")).getBytes(StandardCharsets.US_ASCII));
        }
        if(stopped!=null) { var stop=new Stop("P42_STOPPED_PRESERVE_LOCKS_ANALYZE_OFFLINE"); stop.initCause(stopped); throw stop; }
    }
    static void credentials(JsonNode e) {
        require(AUTH.equals(e.path("authorization").asText()),"EXPLICIT_AUTH_REQUIRED");
        for(String key:List.of("embeddingKey","rerankKey","deepseekKey","dbPassword")) {
            String v=e.path(key).asText(); require(!v.isBlank() && !v.contains("\r") && !v.contains("\n"),"NECESSARY_CREDENTIAL_MISSING");
        }
        require(e.path("workspaceId").asText().matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"),"BEIJING_WORKSPACE_REQUIRED");
        require(e.path("budgetReferenceCny").isNumber() && e.path("budgetReferenceCny").asDouble()==1.0,"BUDGET_REFERENCE_MUST_BE_ONE");
    }
    static void validateSaved(Path run) throws Exception {
        var dataset=read(run.resolve("frozen-questions.json")); var qs=contracts(dataset);
        for(var q:qs) {
            Path dir=run.resolve(q.questionId()); if(!Files.exists(dir.resolve("result.json"))) continue;
            JsonNode row=read(dir.resolve("result.json")); if(!row.path("status").asText().equals("COMPLETE")) continue;
            var original=chunks(JSON.convertValue(read(dir.resolve("MILVUS_SEARCH/hydrated.json")),new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>() {}));
            JsonNode raw=read(dir.resolve("RERANK/response.json"));
            List<RetrievedChunk> ranked=new ArrayList<>(); Set<Integer> indexes=new HashSet<>(); double previous=1;
            require(raw.at("/output/results").size()==5,"SAVED_RANK_COUNT");
            int position=0;
            for(var r:raw.at("/output/results")) {
                int idx=r.path("index").asInt(-1); double score=r.path("relevance_score").asDouble(Double.NaN);
                require(r.path("index").isIntegralNumber() && idx>=0 && idx<5 && indexes.add(idx) && r.path("relevance_score").isNumber() && score>=0 && score<=previous,"SAVED_RANK_INVALID");
                previous=score; var c=original.get(idx); ranked.add(c);
                JsonNode saved=row.path("ranking").get(position++);
                require(saved.path("vectorId").asText().equals(c.vectorId()) && saved.path("relevanceScore").asDouble()==score,"SAVED_RANK_DRIFT");
            }
            var top2=ranked.stream().limit(2).toList(); var gate=new PrimarySemanticGate();
            JsonNode request=read(dir.resolve("SEMANTIC_GATE/request.json"));
            require(JSON.readTree(request.at("/messages/1/content").asText()).equals(gate.request(q,top2)) && request.at("/messages/0/content").asText().equals(Files.readString(run.resolve("system-prompt.txt"))) &&
                    request.path("model").asText().equals(Stage.SEMANTIC_GATE.model) && request.path("max_tokens").asInt()==3072 && !request.path("stream").asBoolean() &&
                    request.at("/thinking/type").asText().equals("disabled") && request.at("/response_format/type").asText().equals("json_object"),"SAVED_GATE_INPUT_DRIFT");
            JsonNode response=read(dir.resolve("SEMANTIC_GATE/response.json"));
            require(response.at("/choices/0/finish_reason").asText().equals("stop") && Set.of("deepseek-v4-flash","deepseek-flash","deepseek-v4.1-flash").contains(response.path("model").asText()),"SAVED_GATE_ENVELOPE");
            var decision=gate.validate(q,top2,response.at("/choices/0/message/content").asText());
            require(decision.diagnostics().equals(row.path("gate")) && row.path("finalAdmittedCandidateIds").equals(decision.diagnostics().path("admittedCandidateIds")),"SAVED_DECISION_DRIFT");
            JsonNode embedding=read(dir.resolve("EMBEDDING/request.json"));
            require(embedding.path("input").size()==1 && embedding.path("input").get(0).asText().equals(q.question()) && embedding.path("model").asText().equals(Stage.EMBEDDING.model) &&
                    embedding.path("dimensions").asInt()==1024 && embedding.path("encoding_format").asText().equals("float"),"SAVED_EMBEDDING_QUERY_DRIFT");
            JsonNode rankRequest=read(dir.resolve("RERANK/request.json"));
            require(rankRequest.at("/input/query").asText().equals(q.question()) && rankRequest.path("model").asText().equals(Stage.RERANK.model) && rankRequest.at("/parameters/top_n").asInt()==5 && rankRequest.at("/input/documents").size()==5,"SAVED_RERANK_QUERY_DRIFT");
            for(int i=0;i<5;i++) require(rankRequest.at("/input/documents/"+i).asText().equals(original.get(i).title()+"\n\n"+original.get(i).content()),"SAVED_RERANK_BODY_DRIFT");
        }
    }
    public static void main(String[] args) {
        try {
            require(args.length==1 && Set.of("verify","execute-once","validate-run").contains(args[0]),"EXPLICIT_MODE_REQUIRED");
            JsonNode dataset=verifyFrozen(); isolation();
            if(args[0].equals("verify")) { System.out.println("P4_2_EXECUTION_FROZEN=VERIFIED; external connections=0"); return; }
            if(args[0].equals("validate-run")) { validateSaved(RUN); System.out.println("P4_2_SAVED_DECISIONS=VERIFIED; model calls=0"); return; }
            JsonNode e=JSON.readTree(System.in.readNBytes(65536)); credentials(e);
            require(sha(SEAL).equals(e.path("executionSealSha256").asText()),"AUTH_SEAL_BINDING");
            require("true".equals(System.getProperty("jdk.httpclient.disableRetryConnect")) && "false".equals(System.getProperty("jdk.httpclient.enableAllMethodRetry")),"HTTP_RETRY_DISABLE_REQUIRED");
            require(!Files.exists(RUN),"ALREADY_RESERVED_NO_RESUME");
            List<String> secrets=List.of(e.path("embeddingKey").asText(),e.path("rerankKey").asText(),e.path("deepseekKey").asText(),e.path("dbPassword").asText(),e.path("milvusToken").asText());
            try(var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()) {
                HttpTransport transport=(stage,body)-> {
                    String endpoint=switch(stage) {
                        case EMBEDDING -> "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
                        case RERANK -> "https://"+e.path("workspaceId").asText()+".cn-beijing.maas.aliyuncs.com"+BailianRerankClient.PATH;
                        case SEMANTIC_GATE -> "https://api.deepseek.com/chat/completions";
                        default -> throw new Stop("HTTP_STAGE_REJECTED");
                    };
                    String key=e.path(stage==Stage.EMBEDDING?"embeddingKey":stage==Stage.RERANK?"rerankKey":"deepseekKey").asText();
                    int timeout=stage==Stage.EMBEDDING?20:stage==Stage.RERANK?10:30;
                    var request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(timeout)).header("Content-Type","application/json")
                            .header("Authorization","Bearer "+key).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
                    var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
                    try(var stream=response.body()) { return new Reply(response.statusCode(),stream.readNBytes(1024*1024+1)); }
                };
                var runner=new P42R02ExecuteOnce(RUN,transport,P42R02ExecuteOnce::verifyFrozen,Files.readString(Path.of("tools/rag-p4-2/system-prompt.txt")),sha(SEAL),"REAL",secrets);
                runner.runtimeMetadata=JSON.createObjectNode().put("transport","JDK_HTTP_ONE_SEND_REDIRECT_NEVER").put("httpConnectRetries",false)
                        .put("httpMethodRetries",false).put("milvusMaxRetryTimes",1).put("milvusRetryOnRateLimit",false)
                        .put("database","ai_interview_test").put("collection",IndependentRetrieval.COLLECTION).put("collectionId",Long.toString(IndependentRetrieval.COLLECTION_ID));
                var endpoints=runner.runtimeMetadata.putObject("endpoints");
                endpoints.put("EMBEDDING","https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings")
                        .put("RERANK","https://"+e.path("workspaceId").asText()+".cn-beijing.maas.aliyuncs.com"+BailianRerankClient.PATH)
                        .put("SEMANTIC_GATE","https://api.deepseek.com/chat/completions");
                runner.run(dataset,()-> {
                    var nativeResources=new P3ReadOnlyRetrieval.NativeResources(e.path("dbPassword").asText(),e.path("milvusToken").asText());
                    return new Resources() {
                        public Object before() throws Exception { return nativeResources.before(); }
                        public P3ReadOnlyRetrieval.SearchBatch search(List<Float> v,RawSearchCapture capture) throws Exception {
                            var response=nativeResources.milvus.search(IndependentRetrieval.searchRequest(v));
                            JsonNode raw=JSON.valueToTree(response); capture.save(raw); // Preserve RPC output even if hydration/validation fails.
                            var hits=IndependentRetrieval.rawHits(response);
                            require(hits.size()==5,"FIVE_FROZEN_CANDIDATES_REQUIRED");
                            nativeResources.helper.hydrate(nativeResources.sql,hits);
                            return new P3ReadOnlyRetrieval.SearchBatch(hits,raw);
                        }
                        public Object after() throws Exception { return nativeResources.after(); }
                        public void close() throws Exception { nativeResources.close(); }
                    };
                });
            }
            System.out.println("P4_2_EXECUTION=COMPLETE; QUALITY=PENDING_INDEPENDENT_HUMAN_REVIEW");
        } catch(Exception error) { System.err.println("P4_2_EXECUTION_STOPPED: "+code(error)+"; diagnostics="+failureDiagnostics(error,"TERMINAL")+"; preserve locks; no retry."); System.exit(1); }
    }
}
