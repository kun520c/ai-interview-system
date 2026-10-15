import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;

/** Injected Mock HTTP and Mock resources only. The holdout is used to test control flow, never semantic tuning. */
public final class P42R02RunnerTest {
    static int passed;
    interface Check { void run() throws Exception; }
    static void check(boolean ok) { if(!ok) throw new AssertionError(); }
    static void test(String name,Check fn) throws Exception { fn.run(); passed++; System.out.println("PASS "+name); }
    static void rejects(Check fn) throws Exception { try { fn.run(); throw new AssertionError("Expected rejection"); } catch(Exception expected) {} }
    static JsonNode dataset;
    static Path base;
    static int sequence;
    static final class MockServices implements P42R02ExecuteOnce.HttpTransport, P42R02ExecuteOnce.Resources {
        int httpCalls, searches, before, after, close;
        String failStage="", variant="";
        public Object before() { before++; return Map.of("MOCK_ONLY",true); }
        public Object after() { after++; if(failStage.equals("AFTER")) throw new P42R02ExecuteOnce.Stop("MOCK_RESOURCE_DRIFT"); return Map.of("MOCK_ONLY",true); }
        public void close() { close++; }
        public P3ReadOnlyRetrieval.SearchBatch search(List<Float> vector,P42R02ExecuteOnce.RawSearchCapture capture) throws Exception {
            searches++;
            if(failStage.equals("MILVUS_SEARCH")) throw new P42R02ExecuteOnce.Stop("MOCK_SEARCH_ERROR");
            List<Map<String,Object>> hits=new ArrayList<>();
            for(int i=0;i<5;i++) {
                String body="Mock正文，只用于运行器流程检查，不能代表冻结语料或题目的真实语义。";
                Map<String,String> k=new LinkedHashMap<>(); k.put("chunkId",""+(i+1)); k.put("documentId","1"); k.put("documentVersion","1"); k.put("chunkIndex",""+(i+1));
                k.put("vectorId","mock-v"+i); k.put("content",body); k.put("title","Mock title"); k.put("category","JAVA_BASIC"); k.put("source",null);
                var hit=new LinkedHashMap<String,Object>(); hit.put("vectorId","mock-v"+i); hit.put("documentId",1L); hit.put("chunkIndex",i+1); hit.put("cosineFloat32",.9-i*.1);
                hit.put("sqlEligible",true); hit.put("knowledge",k); hit.put("contentSha256",RagE2ePolicy.hash(body)); hits.add(hit);
            }
            JsonNode raw=P42R02ExecuteOnce.JSON.valueToTree(hits); capture.save(raw);
            if(failStage.equals("HYDRATION")) throw new P42R02ExecuteOnce.Stop("MOCK_HYDRATION_ERROR");
            return new P3ReadOnlyRetrieval.SearchBatch(hits,raw);
        }
        public P42R02ExecuteOnce.Reply post(P42R02ExecuteOnce.Stage stage,byte[] body) throws Exception {
            httpCalls++;
            if(failStage.equals(stage.name())) throw new java.net.http.HttpTimeoutException("MOCK_TIMEOUT");
            JsonNode request=P42R02ExecuteOnce.JSON.readTree(body); ObjectNode reply=P42R02ExecuteOnce.JSON.createObjectNode();
            var usage=reply.putObject("usage");
            if(stage==P42R02ExecuteOnce.Stage.EMBEDDING) {
                check(request.path("input").size()==1 && request.path("dimensions").asInt()==1024);
                reply.put("model",stage.model); var item=reply.putArray("data").addObject().put("index",0); var values=item.putArray("embedding"); for(int i=0;i<1024;i++) values.add(.01);
                usage.put("total_tokens",100).put("prompt_tokens",100);
            } else if(stage==P42R02ExecuteOnce.Stage.RERANK) {
                check(request.at("/parameters/top_n").asInt()==5 && request.at("/input/documents").size()==5);
                var results=reply.putObject("output").putArray("results"); for(int i=4;i>=0;i--) results.addObject().put("index",i).put("relevance_score",.5+i*.1);
                if(variant.equals("BAD_RANK")) ((ObjectNode)results.get(1)).put("index",4);
                usage.put("prompt_tokens",200);
            } else {
                check(request.path("max_tokens").asInt()==3072 && request.at("/thinking/type").asText().equals("disabled"));
                JsonNode input=P42R02ExecuteOnce.JSON.readTree(request.at("/messages/1/content").asText());
                check(!input.has("expectedCoverage") && !input.has("label") && !input.has("answer"));
                ObjectNode gate=P42R02ExecuteOnce.JSON.createObjectNode().put("questionId",input.path("questionId").asText());
                var candidates=gate.putArray("candidates");
                for(var c:input.path("candidates")) { var out=candidates.addObject().put("candidateId",c.path("candidateId").asText()).put("verdict",variant.equals("UNCERTAIN")?"UNCERTAIN":"NO_SUPPORT")
                        .put("versionAssessment","MATCH").put("reason","Mock control flow only"); out.putArray("supports"); }
                reply.put("model",variant.equals("WRONG_MODEL")?"other-paid-model":"deepseek-v4-flash");
                var choice=reply.putArray("choices").addObject().put("index",0).put("finish_reason",variant.equals("TRUNCATED")?"length":"stop");
                choice.putObject("message").put("role","assistant").put("content",variant.equals("INVALID_GATE")?"{}":gate.toString());
                usage.put("prompt_tokens",300).put("completion_tokens",100).put("total_tokens",400);
            }
            if(variant.equals("MISSING_USAGE") && stage==P42R02ExecuteOnce.Stage.EMBEDDING) reply.remove("usage");
            if(variant.equals("EXCESS_USAGE") && stage==P42R02ExecuteOnce.Stage.EMBEDDING) usage.put("total_tokens",3001);
            if(variant.equals("ECHO_SECRET")) reply.put("echo","MOCK_SECRET");
            return new P42R02ExecuteOnce.Reply(variant.equals("HTTP_429")?429:200,P42R02ExecuteOnce.JSON.writeValueAsBytes(reply));
        }
    }
    static P42R02ExecuteOnce runner(MockServices mock) throws Exception {
        return new P42R02ExecuteOnce(base.resolve("case-"+(++sequence)),mock,()->{},Files.readString(Path.of("tools/rag-p4-2/system-prompt.txt")),"MOCK_SEAL","MOCK",List.of("MOCK_SECRET"));
    }
    static JsonNode summary(P42R02ExecuteOnce r) throws Exception { return P42R02ExecuteOnce.read(r.output.resolve("summary.json")); }
    static void variant(String v,int http,int searches) throws Exception {
        var m=new MockServices(); m.variant=v; var r=runner(m); rejects(()->r.run(dataset,()->m));
        check(m.httpCalls==http && m.searches==searches && summary(r).path("executionStatus").asText().equals("STOPPED"));
        check(Files.exists(r.output.resolve("EXECUTION_RESERVED.lock")) && Files.exists(r.output.resolve("artifacts.sha256")));
        check(summary(r).at("/questions/0/finalAdmittedCandidateIds").isEmpty());
    }
    public static void main(String[] args) throws Exception {
        base=Files.createTempDirectory(Path.of(args[0]),"mock-"); dataset=P42R02ExecuteOnce.read(P42R02ExecuteOnce.PREP.resolve("holdout-questions-v1.json"));
        test("complete frozen eight questions 24 Mock HTTP and eight Mock searches; offline replay",()-> {
            var m=new MockServices(); var r=runner(m); r.run(dataset,()->m);
            check(m.httpCalls==24 && m.searches==8 && m.before==1 && m.after==1 && m.close==1);
            check(summary(r).path("executionStatus").asText().equals("COMPLETE") && summary(r).path("qualityStatus").asText().contains("PENDING"));
            P42R02ExecuteOnce.validateSaved(r.output);
            Files.writeString(Path.of(args[0]).resolve("mock-complete-path.txt"),r.output.toAbsolutePath().toString());
            int previous=m.httpCalls; rejects(()->r.run(dataset,()->m)); check(m.httpCalls==previous);
        });
        for(String stage:List.of("EMBEDDING","MILVUS_SEARCH","RERANK","SEMANTIC_GATE")) test("exception stops at "+stage+" and retains all evidence",()-> {
            var m=new MockServices(); m.failStage=stage; var r=runner(m); rejects(()->r.run(dataset,()->m));
            int expected=switch(stage) { case "EMBEDDING","MILVUS_SEARCH" -> 1; case "RERANK" -> 2; default -> 3; };
            check(m.httpCalls==expected && summary(r).path("executionStatus").asText().equals("STOPPED") && m.after==1 && m.close==1);
            check(Files.exists(r.output.resolve("H42-01/"+stage+"/ATTEMPT_RESERVED.lock")));
        });
        for(String v:List.of("MISSING_USAGE","EXCESS_USAGE","HTTP_429","ECHO_SECRET")) test(v+" stops before search without retry",()->variant(v,1,0));
        for(String v:List.of("INVALID_GATE","WRONG_MODEL","TRUNCATED","UNCERTAIN")) test(v+" retains response but safe-empty stops batch",()->variant(v,3,1));
        test("invalid rerank indexes stop before Gate",()->variant("BAD_RANK",2,1));
        test("resource drift after complete calls is STOPPED, never PASS",()-> {
            var m=new MockServices(); m.failStage="AFTER"; var r=runner(m); rejects(()->r.run(dataset,()->m));
            check(m.httpCalls==24 && !summary(r).path("resourcesUnchanged").asBoolean() && summary(r).path("executionStatus").asText().equals("STOPPED"));
        });
        test("preflight resource failure retains batch lock and zero attempts",()-> {
            var m=new MockServices(); var r=runner(m); rejects(()->r.run(dataset,()-> { throw new P42R02ExecuteOnce.Stop("MOCK_OPEN_ERROR"); }));
            check(m.httpCalls==0 && Files.exists(r.output.resolve("EXECUTION_RESERVED.lock")));
        });
        test("hydration failure preserves raw Milvus response and stops before rerank",()-> {
            var m=new MockServices(); m.failStage="HYDRATION"; var r=runner(m); rejects(()->r.run(dataset,()->m));
            check(m.httpCalls==1 && m.searches==1 && Files.exists(r.output.resolve("H42-01/MILVUS_SEARCH/response.json")));
        });
        test("frozen drift before call stops without sending or backfill",()-> {
            var m=new MockServices(); int[] guardCalls={0}; var r=new P42R02ExecuteOnce(base.resolve("drift"),m,()-> { if(++guardCalls[0]==3) throw new P42R02ExecuteOnce.Stop("MOCK_FROZEN_DRIFT"); },"mock","MOCK_SEAL","MOCK",List.of());
            rejects(()->r.run(dataset,()->m)); check(m.httpCalls==1 && m.searches==0);
        });
        test("budget .707392 initial proxy; unsafe known costs and unknown usage reject",()-> {
            var b=new P42R02ExecuteOnce.Budget(); check(Math.abs(b.remaining.entrySet().stream().mapToDouble(e->e.getValue()*e.getKey().proxy()).sum()-.707392)<1e-12); b.check();
            b.known=.4; rejects(b::check); b.known=0; b.unknown=true; rejects(b::check);
        });
        test("missing auth, credentials and changed budget are rejected before resources",()-> {
            var e=P42R02ExecuteOnce.JSON.createObjectNode().put("authorization",P42R02ExecuteOnce.AUTH).put("embeddingKey","MOCK").put("rerankKey","MOCK").put("deepseekKey","MOCK").put("dbPassword","MOCK")
                    .put("workspaceId","mock-beijing").put("budgetReferenceCny",1.0); P42R02ExecuteOnce.credentials(e);
            for(String k:List.of("authorization","embeddingKey","rerankKey","deepseekKey","dbPassword","workspaceId","budgetReferenceCny")) { var copy=e.deepCopy(); copy.remove(k); rejects(()->P42R02ExecuteOnce.credentials(copy)); }
        });
        test("question contract rejects appended labels; no reverse contract authoring",()-> {
            var copy=dataset.deepCopy(); ((ObjectNode)copy.path("questions").get(0)).put("expectedCoverage","COVERED"); rejects(()->P42R02ExecuteOnce.contracts(copy));
            check(P42R02ExecuteOnce.sha(P42R02ExecuteOnce.PREP.resolve("holdout-questions-v1.json")).equals(P42R02ExecuteOnce.QUESTIONS_SHA));
        });
        test("duplicate stage durable intent refuses second HTTP",()-> {
            var m=new MockServices(); var r=runner(m); Files.createDirectory(r.output); Files.createDirectory(r.output.resolve("H42-01"));
            byte[] body="{\"input\":[\"Mock\"],\"dimensions\":1024}".getBytes(); r.send("H42-01",P42R02ExecuteOnce.Stage.EMBEDDING,body); rejects(()->r.send("H42-01",P42R02ExecuteOnce.Stage.EMBEDDING,body)); check(m.httpCalls==1);
        });
        test("ninth attempt in each stage is rejected before HTTP or search",()-> {
            for(var stage:P42R02ExecuteOnce.Stage.values()) {
                var m=new MockServices(); var r=runner(m); Files.createDirectory(r.output); Files.createDirectory(r.output.resolve("H42-01"));
                r.counts.put(stage,8); rejects(()->r.attempt("H42-01",stage,"{}".getBytes()));
                check(m.httpCalls==0 && m.searches==0 && r.counts.get(stage)==8);
            }
            check(Arrays.stream(P42R02ExecuteOnce.Stage.values()).filter(s->s!=P42R02ExecuteOnce.Stage.MILVUS_SEARCH).count()*8==24);
        });
        test("exact delta rejects both non-exempt source and later exempt source or class mutation",()-> {
            Path fixed=base.resolve("frozen-source.java"), changed=base.resolve("allowed-source.java"), binary=base.resolve("allowed.class");
            Files.writeString(fixed,"frozen"); Files.writeString(changed,"accepted proxy"); Files.writeString(binary,"accepted class");
            var map=P42R02ExecuteOnce.JSON.createObjectNode().put(fixed.toString(),P42R02ExecuteOnce.sha(fixed)).put(changed.toString(),"old source").put(binary.toString(),"old class");
            var entries=P42R02ExecuteOnce.JSON.createArrayNode();
            entries.addObject().put("path",changed.toString()).put("oldSha256","old source").put("currentSha256",P42R02ExecuteOnce.sha(changed));
            entries.addObject().put("path",binary.toString()).put("oldSha256","old class").put("currentSha256",P42R02ExecuteOnce.sha(binary));
            P42R02ExecuteOnce.verifyBaselineMap(map,entries);
            for(Path p:List.of(fixed,changed,binary)) {
                byte[] original=Files.readAllBytes(p); Files.writeString(p,"unexpected drift"); rejects(()->P42R02ExecuteOnce.verifyBaselineMap(map,entries)); Files.write(p,original);
            }
            entries.add(entries.get(0)); rejects(()->P42R02ExecuteOnce.verifyBaselineMap(map,entries));
        });
        Files.write(Path.of(args[0]).resolve("java-tests.json"),P42R02ExecuteOnce.JSON.writeValueAsBytes(Map.of("tests",passed,"failures",0,"errors",0,"skipped",0,"newRealCalls",0)));
        System.out.println("P42 Runner Mock tests="+passed+", failures=0, errors=0, skipped=0; real calls=0");
    }
}
