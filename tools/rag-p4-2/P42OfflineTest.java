import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kun.aiinterview.knowledge.admission.*;
import com.kun.aiinterview.knowledge.embedding.*;
import com.kun.aiinterview.knowledge.enums.KnowledgeCategory;
import com.kun.aiinterview.knowledge.mapper.KnowledgeChunkMapper;
import com.kun.aiinterview.knowledge.retrieval.*;
import com.kun.aiinterview.knowledge.service.KnowledgeRetrievalService;
import com.kun.aiinterview.knowledge.vector.*;
import com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.file.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

public final class P42OfflineTest {
    private static final List<String> passed = new ArrayList<>();
    private interface Checked { void run() throws Exception; }
    private static void test(String name, Checked fn) throws Exception { fn.run(); passed.add(name); }
    private static void check(boolean value) { if (!value) throw new AssertionError("P4.2 check failed"); }
    private static QuestionContract contract(JsonNode node) throws Exception { return PrimarySemanticGate.JSON.treeToValue(node, QuestionContract.class); }
    private static List<RetrievedChunk> chunks(JsonNode input) {
        List<RetrievedChunk> list = new ArrayList<>(); int index = 0;
        for (JsonNode c : input.path("candidates")) {
            index++; list.add(new RetrievedChunk(index, .01, (long) index, 1L, 1, index, c.path("candidateId").asText(),
                    c.path("title").asText(), KnowledgeCategory.JAVA_BASIC, null, c.path("content").asText()));
        }
        return List.copyOf(list);
    }
    private static ObjectNode response(QuestionContract q, List<RetrievedChunk> cs, String verdict, String requirement, String relation, String quote) {
        var model = PrimarySemanticGate.JSON.createObjectNode().put("questionId", q.questionId());
        var candidates = model.putArray("candidates");
        for (RetrievedChunk c : cs) {
            var item = candidates.addObject().put("candidateId", c.vectorId()).put("verdict", verdict).put("versionAssessment", "MATCH").put("reason", "OFFLINE_MOCK_ONLY");
            var supports = item.putArray("supports");
            if (quote != null) supports.addObject().put("requirementId", requirement).put("relation", relation).put("quote", quote).put("explanation", "Authored fixture; not a new real model result");
        }
        return model;
    }
    private static void reject(QuestionContract contract, List<RetrievedChunk> candidates, JsonNode reply, String error) throws Exception {
        try { new PrimarySemanticGate().validate(contract, candidates, reply.toString()); throw new AssertionError("Unsafe reply accepted"); }
        catch (IllegalArgumentException failure) { check(failure.getMessage().equals(error)); }
    }
    private static ExperimentalEvidenceStrategy strategy(ExperimentalEvidenceStrategy.Candidates source, ExperimentalEvidenceStrategy.Reranker ranker, ExperimentalEvidenceStrategy.GateModel model) {
        return new ExperimentalEvidenceStrategy(true, (q, k) -> { throw new AssertionError("Enabled path must not fall back to old evidence"); }, source, ranker, model, new PrimarySemanticGate());
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]), prepared = root.resolve("target/rag-p4-2/prepared"), offline = root.resolve("target/rag-p4-2/offline");
        var fixtures = PrimarySemanticGate.JSON.readTree(Files.readAllBytes(prepared.resolve("development-fixtures.json")));
        var c01 = contract(fixtures.path("C01").path("contract")); var c01Chunks = chunks(fixtures.path("C01").path("input"));
        String iteratorQuote = fixtures.path("C01").path("primaryQuote").asText();
        var one = List.of(c01Chunks.get(1));
        test("C01 concise partial primary evidence retained; missing primary points remain explicit", () -> {
            var model = response(c01, one, "CORE_SUPPORT", "r2", "DIRECT_MECHANISM", iteratorQuote);
            var decision = new PrimarySemanticGate().validate(c01, one, model.toString());
            check(decision.evidence().size() == 1 && decision.diagnostics().path("missingPrimaryRequirementIdsAcrossAdmitted").size() > 0);
            check(decision.diagnostics().path("wholeAnswerSufficiency").asText().equals("NOT_INDEPENDENTLY_VERIFIED"));
        });
        test("N02 truthful MVCC/locking-read context cannot be promoted even by DIRECT label", () -> {
            var q = contract(fixtures.path("N02").path("contract")); var cs = chunks(fixtures.path("N02").path("input"));
            var model = response(q, List.of(cs.getFirst()), "CORE_SUPPORT", "r3", "DIRECT_MECHANISM", fixtures.path("N02").path("contextQuote").asText());
            reject(q, List.of(cs.getFirst()), model, "CORE_REQUIRES_PRIMARY_MECHANISM");
            ((ObjectNode) model.path("candidates").get(0)).put("verdict", "BACKGROUND_ONLY");
            check(new PrimarySemanticGate().validate(q, List.of(cs.getFirst()), model.toString()).evidence().isEmpty());
        });
        test("N06 TIME-WAIT context remains zero with genuine body quote", () -> {
            var q = contract(fixtures.path("N06").path("contract")); var cs = List.of(chunks(fixtures.path("N06").path("input")).getFirst());
            var model = response(q, cs, "BACKGROUND_ONLY", "r3", "SUPPORTING_CONTEXT", fixtures.path("N06").path("contextQuote").asText());
            check(new PrimarySemanticGate().validate(q, cs, model.toString()).evidence().isEmpty());
        });
        test("C03 partial happens-before primary support retained", () -> {
            var q = contract(fixtures.path("C03").path("contract")); var cs = List.of(chunks(fixtures.path("C03").path("input")).getFirst());
            var d = new PrimarySemanticGate().validate(q, cs, response(q, cs, "CORE_SUPPORT", "r1", "DIRECT_MECHANISM", fixtures.path("C03").path("primaryQuote").asText()).toString());
            check(d.evidence().size() == 1 && d.diagnostics().path("missingPrimaryRequirementIdsAcrossAdmitted").size() == 1);
        });
        test("C02 CAS retry partial mechanism and C05 concise snapshot summary follow same generic rule", () -> {
            for (String key : List.of("C02", "C05")) {
                var q = contract(fixtures.path(key).path("contract"));
                var cs = List.of(chunks(fixtures.path(key).path("input")).get(key.equals("C05") ? 1 : 0));
                var d = new PrimarySemanticGate().validate(q, cs, response(q, cs, "CORE_SUPPORT", "r1", "DIRECT_MECHANISM", fixtures.path(key).path("primaryQuote").asText()).toString());
                check(d.evidence().size() == 1 && d.diagnostics().path("missingPrimaryRequirementIdsAcrossAdmitted").size() == 1);
            }
        });
        test("C04 changed-default branch is context/conflict, not default-transaction support", () -> {
            var q = contract(fixtures.path("C04").path("contract")); var cs = List.of(chunks(fixtures.path("C04").path("input")).getFirst());
            var wrong = response(q, cs, "CORE_SUPPORT", "r3", "CONDITION_CONFLICT", fixtures.path("C04").path("conflictQuote").asText());
            reject(q, cs, wrong, "CORE_REQUIRES_PRIMARY_MECHANISM");
            var right = response(q, cs, "CORE_SUPPORT", "r2", "DIRECT_MECHANISM", fixtures.path("C04").path("primaryQuote").asText());
            check(new PrimarySemanticGate().validate(q, cs, right.toString()).evidence().size() == 1);
        });
        test("invented or model-generated requirement IDs cannot change the contract", () -> {
            reject(c01, one, response(c01, one, "CORE_SUPPORT", "r9", "DIRECT_MECHANISM", iteratorQuote), "UNKNOWN_REQUIREMENT");
            var extra = response(c01, one, "CORE_SUPPORT", "r2", "DIRECT_MECHANISM", iteratorQuote); extra.putArray("corePoints");
            reject(c01, one, extra, "FIELDS");
        });
        test("same primary point may carry multiple independently validated quotations", () -> {
            var model = response(c01, one, "CORE_SUPPORT", "r2", "DIRECT_MECHANISM", iteratorQuote);
            var supports = (com.fasterxml.jackson.databind.node.ArrayNode) model.path("candidates").get(0).path("supports");
            var second = (ObjectNode) supports.get(0).deepCopy();
            second.put("quote", "链表修改的优势需要“位置已知或只操作两端”等条件");
            supports.add(second);
            check(new PrimarySemanticGate().validate(c01, one, model.toString()).evidence().size() == 1);
            ((ObjectNode) supports.get(1)).put("quote", "后一次引用是伪造的说明，原文中不存在这段内容。");
            reject(c01, one, model, "QUOTE_NOT_GROUNDED_BODY");
        });
        test("explicit version constraint rejects NOT_APPLICABLE or MISMATCH core evidence", () -> {
            var q = contract(fixtures.path("C04").path("contract")); var cs = List.of(chunks(fixtures.path("C04").path("input")).getFirst());
            for (String version : List.of("NOT_APPLICABLE", "MISMATCH", "UNCERTAIN")) {
                var model = response(q, cs, "CORE_SUPPORT", "r2", "DIRECT_MECHANISM", fixtures.path("C04").path("primaryQuote").asText());
                ((ObjectNode) model.path("candidates").get(0)).put("versionAssessment", version);
                reject(q, cs, model, "CORE_VERSION_UNCONFIRMED");
            }
        });
        test("UNCERTAIN is distinguishable from successful outside rejection", () -> {
            var reply = response(c01, one, "UNCERTAIN", "r2", "UNCERTAIN", null);
            var d = new PrimarySemanticGate().validate(c01, one, reply.toString());
            check(d.evidence().isEmpty() && d.diagnostics().path("status").asText().equals("SAFE_EMPTY_UNCERTAIN"));
        });
        test("headers, URLs and reference-list quotes do not establish mechanism evidence", () -> {
            var q = contract(fixtures.path("C03").path("contract")); var cs = List.of(chunks(fixtures.path("C03").path("input")).getFirst());
            String header = cs.getFirst().content().lines().filter(l -> l.startsWith("> Target question:")).findFirst().orElseThrow();
            reject(q, cs, response(q, cs, "CORE_SUPPORT", "r1", "DIRECT_MECHANISM", header), "QUOTE_NOT_GROUNDED_BODY");
            var reference = new RetrievedChunk(1, .1, 1L, 1L, 1, 1, "ref", "mock", KnowledgeCategory.JAVA_BASIC, null,
                    "# 仅用于测试的标题，不能当作机制依据\n\n- [SPEC] 官方规范只有链接而没有机制正文\nhttps://example.invalid/reference/long-path");
            for (String quote : List.of("仅用于测试的标题，不能当作机制依据", "官方规范只有链接而没有机制正文", "https://example.invalid/reference/long-path"))
                reject(q, List.of(reference), response(q, List.of(reference), "CORE_SUPPORT", "r1", "DIRECT_MECHANISM", quote), "QUOTE_NOT_GROUNDED_BODY");
        });
        test("duplicate JSON keys and trailing objects are rejected", () -> {
            String good = response(c01, one, "CORE_SUPPORT", "r2", "DIRECT_MECHANISM", iteratorQuote).toString();
            reject(c01, one, PrimarySemanticGate.JSON.createObjectNode(), "FIELDS");
            try { new PrimarySemanticGate().validate(c01, one, good + "{}"); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { check(expected.getMessage().equals("TRAILING_JSON")); }
            try { new PrimarySemanticGate().validate(c01, one, good.replaceFirst("\\{", "{\"questionId\":\"duplicate\",")); throw new AssertionError(); }
            catch (com.fasterxml.jackson.core.JsonProcessingException expected) { /* strict duplicate detection */ }
        });
        test("caller requirement anchors/roles cannot be invented and a context-only contract is invalid", () -> {
            try { new QuestionContract("q", "question", "", List.of(new QuestionContract.Requirement("r1", QuestionContract.Role.PRIMARY_MECHANISM, "invented", "requirement"))); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { check(expected.getMessage().equals("INVALID_QUESTION_REQUIREMENT")); }
            try { new QuestionContract("q", "question", "", List.of(new QuestionContract.Requirement("r1", QuestionContract.Role.SUPPORTING_CONTEXT, "question", "requirement"))); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { check(expected.getMessage().equals("PRIMARY_REQUIRED")); }
        });
        test("disabled service delegates exact legacy query/topK/result with zero new calls", () -> {
            var raw = new RetrievalResult("old rubric query", 3, "model", "version", c01Chunks);
            var legacy = mock(KnowledgeRetrievalService.class); when(legacy.retrieve("old rubric query", 3)).thenReturn(raw);
            var s = new ExperimentalEvidenceStrategy(false, legacy::retrieve, null, null, null, null);
            var result = s.retrieve(c01.question(), "old rubric query", 3, null);
            check(result.retrieval() == raw && result.rerankCalls() == 0 && result.gateCalls() == 0);
            verify(legacy, times(1)).retrieve("old rubric query", 3);
        });
        var all = new ArrayList<>(c01Chunks);
        for (int n = 3; n <= 5; n++) all.add(new RetrievedChunk(n, .001, (long) n, 1L, 1, n, "mock-v" + n, "title", KnowledgeCategory.JAVA_BASIC, null, "这段模拟正文仅用于服务调用次数和排序验证。"));
        var raw = new RetrievalResult(c01.question(), 5, "qwen", "version", List.of(), all);
        test("enabled service uses Top5 then exact Top2; no Rank3 backfill", () -> {
            var source = mock(ExperimentalEvidenceStrategy.Candidates.class); var rank = mock(ExperimentalEvidenceStrategy.Reranker.class); var model = mock(ExperimentalEvidenceStrategy.GateModel.class);
            when(source.retrieve(c01.question())).thenReturn(raw); when(rank.rank(c01.question(), raw.items())).thenReturn(raw.items());
            var reply = response(c01, c01Chunks, "BACKGROUND_ONLY", "r2", "SUPPORTING_CONTEXT", null);
            when(model.complete(any())).thenReturn(reply.toString());
            var result = strategy(source, rank, model).retrieve(c01.question(), "ignored rubric", 1, c01);
            check(result.retrieval().items().isEmpty() && result.rerankCalls() == result.gateCalls());
            verify(source, times(1)).retrieve(c01.question()); verify(rank, times(1)).rank(c01.question(), raw.items()); verify(model, times(1)).complete(any());
        });
        test("admission preserves rerank order and exposes supported/missing diagnostics to the caller", () -> {
            var reply = response(c01, c01Chunks, "CORE_SUPPORT", "r2", "DIRECT_MECHANISM", iteratorQuote);
            var first = (ObjectNode) reply.path("candidates").get(0);
            ((ObjectNode) first.path("supports").get(0)).put("quote", "LinkedList 的下标操作从较近的头端或尾端遍历到目标位置；随机下标访问通常为 O(n)。").put("requirementId", "r1");
            var array = (com.fasterxml.jackson.databind.node.ArrayNode) reply.path("candidates");
            var second = array.get(1).deepCopy(); array.removeAll().add(second).add(first);
            var result = strategy(q -> raw, (q, cs) -> cs, request -> reply.toString()).retrieve(c01.question(), "legacy", 5, c01);
            check(result.retrieval().items().equals(c01Chunks) && result.gateDiagnostics().path("wholeAnswerSufficiency").asText().equals("NOT_INDEPENDENTLY_VERIFIED"));
        });
        test("missing contract or no eligible chunks incurs no rerank/gate cost", () -> {
            var source = mock(ExperimentalEvidenceStrategy.Candidates.class); var rank = mock(ExperimentalEvidenceStrategy.Reranker.class); var model = mock(ExperimentalEvidenceStrategy.GateModel.class);
            var s = strategy(source, rank, model); check(s.retrieve(c01.question(), "legacy", 5, null).retrieval().items().isEmpty()); verifyNoInteractions(source, rank, model);
            when(source.retrieve(c01.question())).thenReturn(new RetrievalResult(c01.question(), 5, "qwen", "version", List.of()));
            check(s.retrieve(c01.question(), "legacy", 5, c01).gateCalls() == 0); verifyNoInteractions(rank, model);
        });
        test("gate timeout, API error, malformed JSON all fail closed without fallback/retry", () -> {
            for (int mode = 0; mode < 3; mode++) {
                var source = mock(ExperimentalEvidenceStrategy.Candidates.class); var rank = mock(ExperimentalEvidenceStrategy.Reranker.class); var model = mock(ExperimentalEvidenceStrategy.GateModel.class);
                when(source.retrieve(c01.question())).thenReturn(raw); when(rank.rank(anyString(), anyList())).thenReturn(raw.items());
                if (mode == 0) when(model.complete(any())).thenThrow(new HttpTimeoutException("MOCK_TIMEOUT"));
                else if (mode == 1) when(model.complete(any())).thenThrow(new IllegalStateException("MOCK_API"));
                else when(model.complete(any())).thenReturn("not JSON");
                var d = strategy(source, rank, model).retrieve(c01.question(), "legacy", 5, c01);
                check(d.retrieval().items().isEmpty() && d.status().startsWith("SAFE_EMPTY_GATE") && d.gateCalls() == 1);
                if (mode == 0) check(d.status().endsWith("TIMEOUT")); if (mode == 2) check(d.status().endsWith("INVALID"));
                verify(model, times(1)).complete(any());
            }
        });
        test("rerank failure prevents gate invocation", () -> {
            var source = mock(ExperimentalEvidenceStrategy.Candidates.class); var rank = mock(ExperimentalEvidenceStrategy.Reranker.class); var model = mock(ExperimentalEvidenceStrategy.GateModel.class);
            when(source.retrieve(c01.question())).thenReturn(raw); when(rank.rank(anyString(), anyList())).thenThrow(new IllegalArgumentException("MOCK_RANK"));
            var d = strategy(source, rank, model).retrieve(c01.question(), "legacy", 5, c01);
            check(d.retrieval().items().isEmpty() && d.gateCalls() == 0); verifyNoInteractions(model);
        });
        test("retrieval/eligibility failure exposes SAFE_EMPTY without invoking either new model", () -> {
            var rank = mock(ExperimentalEvidenceStrategy.Reranker.class); var model = mock(ExperimentalEvidenceStrategy.GateModel.class);
            var result = strategy(q -> { throw new IllegalStateException("MOCK_RETRIEVAL"); }, rank, model).retrieve(c01.question(), "legacy", 5, c01);
            check(result.status().equals("SAFE_EMPTY_RETRIEVAL_API_OR_MAPPING_FAILURE") && result.retrieval().items().isEmpty());
            check(result.rerankCalls() == 0 && result.gateCalls() == 0); verifyNoInteractions(rank, model);
        });
        test("candidate loader preserves low-COSINE Top5 and uses existing eligibility SQL only", () -> {
            var embedding = mock(EmbeddingClient.class); var vector = mock(VectorStoreClient.class); var mapper = mock(KnowledgeChunkMapper.class);
            when(embedding.embed(List.of("original"))).thenReturn(new EmbeddingBatchResult("qwen", "v1", 1, List.of(new EmbeddingVector(0, List.of(1F))), 1L));
            List<VectorSearchHit> hits = List.of(new VectorSearchHit("eligible", 1L, 1, "v1", .001), new VectorSearchHit("inactive", 1L, 2, "v1", .9));
            when(vector.search(List.of(1F), "v1", 5)).thenReturn(hits);
            var row = new KnowledgeRetrievalRow(); row.setVectorId("eligible"); row.setDocumentId(1L); row.setChunkIndex(1); row.setChunkId(1L); row.setDocumentVersion(1);
            row.setTitle("title"); row.setContent("满足SQL资格的模拟正文，用于验证不按COSINE预删。"); row.setCategory(KnowledgeCategory.JAVA_BASIC);
            when(mapper.selectRetrievableByVectorIds(List.of("eligible", "inactive"), "qwen", "v1")).thenReturn(List.of(row));
            var result = new MilvusTop5Candidates(embedding, vector, mapper).retrieve("original");
            check(result.rawHits().size() == 2 && result.items().size() == 1 && result.items().getFirst().similarityScore() == .001);
            verify(mapper, never()).batchInsert(anyList()); verify(mapper, never()).deleteByDocumentIdAndVersion(anyLong(), anyInt());
        });
        test("Bailian native rerank request returns all input indices exactly once", () -> {
            URI endpoint = URI.create("https://offline-test.cn-beijing.maas.aliyuncs.com" + BailianRerankClient.PATH);
            var builder = RestClient.builder().baseUrl(endpoint.toString()); var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo(endpoint.toString())).andExpect(request -> {
                var mock = (org.springframework.mock.http.client.MockClientHttpRequest) request;
                JsonNode body = PrimarySemanticGate.JSON.readTree(mock.getBodyAsBytes());
                check(body.path("model").asText().equals("qwen3.7-text-rerank") && body.at("/parameters/top_n").asInt() == 2);
                check(body.at("/input/query").asText().equals(c01.question()) && body.at("/input/documents").size() == 2);
                check(!body.has("return_documents"));
            }).andRespond(withSuccess("{\"output\":{\"results\":[{\"index\":1,\"relevance_score\":0.9},{\"index\":0,\"relevance_score\":0.5}]}}", MediaType.APPLICATION_JSON));
            check(new BailianRerankClient(builder.build(), endpoint).rank(c01.question(), c01Chunks).getFirst().equals(c01Chunks.get(1))); server.verify();
            try { new BailianRerankClient(builder.build(), URI.create("https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings")); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { /* incompatible API rejected */ }
        });
        test("existing DeepSeek JSON client carries the new contract in one capped non-thinking request", () -> {
            var reply = response(c01, one, "CORE_SUPPORT", "r2", "DIRECT_MECHANISM", iteratorQuote);
            var envelope = PrimarySemanticGate.JSON.createObjectNode().put("model", "deepseek-flash");
            var choice = envelope.putArray("choices").addObject().put("index", 0).put("finish_reason", "stop");
            choice.putObject("message").put("role", "assistant").put("content", reply.toString());
            var builder = RestClient.builder().baseUrl("https://api.deepseek.com");
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("https://api.deepseek.com/chat/completions")).andExpect(request -> {
                var body = PrimarySemanticGate.JSON.readTree(((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsBytes());
                check(body.path("max_tokens").asInt() == 3072 && !body.path("stream").asBoolean());
                check(body.at("/thinking/type").asText().equals("disabled") && body.at("/response_format/type").asText().equals("json_object"));
                var online = PrimarySemanticGate.JSON.readTree(body.at("/messages/1/content").asText());
                check(online.path("requirements").size() == 2 && online.path("candidates").size() == 1);
                check(!online.toString().contains("suggestedVerdict") && !online.toString().contains("labelSource"));
            }).andRespond(withSuccess(envelope.toString(), MediaType.APPLICATION_JSON));
            var settings = new DeepSeekProperties(); settings.setModel("deepseek-v4-flash"); settings.setMaxTokens(3072);
            var client = new com.kun.aiinterview.interview.evaluation.llm.deepseek.DeepSeekEvaluationClient(builder.build(), settings, PrimarySemanticGate.JSON);
            var request = new PrimarySemanticGate().request(c01, one);
            String system = Files.readString(root.resolve("tools/rag-p4-2/system-prompt.txt"));
            var result = client.completeJson(system, request.toString());
            check(new PrimarySemanticGate().validate(c01, one, result.rawJson()).evidence().size() == 1); server.verify();
        });
        test("Spring missing/false switch creates no experimental clients or strategy", () -> {
            for (String value : List.of("MISSING", "false")) {
                var runner = new ApplicationContextRunner().withUserConfiguration(P42Configuration.class);
                if (!value.equals("MISSING")) runner = runner.withPropertyValues("rag.experimental-evidence.enabled=false");
                runner.run(context -> { check(context.getStartupFailure() == null); check(!context.getBean(P42Properties.class).isEnabled());
                    check(context.getBeansOfType(ExperimentalEvidenceStrategy.class).isEmpty()); check(context.getBeansOfType(BailianRerankClient.class).isEmpty()); });
            }
        });
        test("Spring explicit true composes existing ports without calling them or altering DeepSeek defaults", () -> {
            var existing = mock(KnowledgeRetrievalService.class); var embedding = mock(EmbeddingClient.class); var vector = mock(VectorStoreClient.class); var mapper = mock(KnowledgeChunkMapper.class);
            var settings = new DeepSeekProperties(); settings.setBaseUrl(URI.create("https://api.deepseek.com")); settings.setApiKey("MOCK_KEY"); settings.setModel("deepseek-v4-flash"); settings.setMaxTokens(9999);
            settings.setConnectTimeout(java.time.Duration.ofSeconds(3)); settings.setReadTimeout(java.time.Duration.ofSeconds(20));
            new ApplicationContextRunner().withUserConfiguration(P42Configuration.class)
                    .withBean("p42HttpClient", java.net.http.HttpClient.class, () -> mock(java.net.http.HttpClient.class))
                    // This isolated context registers only explicit Mock ports, never MilvusConfiguration.
                    .withPropertyValues("milvus.enabled=true", "rag.experimental-evidence.enabled=true", "rag.experimental-evidence.rerank-workspace-id=offline-test", "rag.experimental-evidence.rerank-api-key=MOCK_KEY",
                            "rag.experimental-evidence.gate-prompt-path=" + root.resolve("tools/rag-p4-2/system-prompt.txt"))
                    .withBean(KnowledgeRetrievalService.class, () -> existing).withBean(EmbeddingClient.class, () -> embedding)
                    .withBean(VectorStoreClient.class, () -> vector).withBean(KnowledgeChunkMapper.class, () -> mapper).withBean(DeepSeekProperties.class, () -> settings)
                    .run(context -> { check(context.getStartupFailure() == null); check(context.getBeansOfType(ExperimentalEvidenceStrategy.class).size() == 1); check(settings.getMaxTokens() == 9999); });
            verifyNoInteractions(existing, embedding, vector, mapper);
        });
        // Validate unchanged historical outputs with the old, already repaired contract. No pretend V4.2 output.
        var legacy = PrimarySemanticGate.JSON.createObjectNode().put("origin", "SAVED_REAL_LEGACY_PROTOCOL_REPLAY"); var rows = legacy.putArray("queries");
        for (String family : List.of("rag-p4", "rag-p4-1")) {
            int count = family.equals("rag-p4") ? 5 : 3;
            for (int i = 1; i <= count; i++) {
                String id = family.equals("rag-p4") ? "g%02d".formatted(i) : "t" + i;
                var input = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(root.resolve("target/" + family + "/prepared/inputs/" + id + ".json")));
                var response = SemanticEvidenceGate.JSON.readTree(Files.readAllBytes(root.resolve("target/" + family + "/run-once/" + id + "/response.json")));
                var decision = SemanticEvidenceGate.validate(input, response.at("/choices/0/message/content").asText());
                rows.addObject().put("family", family).put("questionId", id).set("gate", decision);
            }
        }
        Files.writeString(offline.resolve("legacy-replay.json"), legacy.toPrettyString());
        var summary = PrimarySemanticGate.JSON.createObjectNode().put("tests", passed.size()).put("failures", 0).put("errors", 0).put("skipped", 0)
                .put("newRealModelCalls", 0).put("legacyResponsesReplayed", 8).put("newRuleRealSemanticsVerified", false);
        var names = summary.putArray("checks"); passed.forEach(names::add);
        Files.writeString(offline.resolve("java-tests.json"), summary.toPrettyString());
        System.out.println("P4.2 Java tests=" + passed.size() + ", failures=0, errors=0, skipped=0; saved legacy replay=8; real model calls=0.");
    }
}
