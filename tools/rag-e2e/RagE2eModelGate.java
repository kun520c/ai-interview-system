import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Loopback forwarding gate. Each provider budget is consumed BEFORE a single upstream POST. */
final class RagE2eModelGate implements AutoCloseable {
    static final ObjectMapper JSON = new ObjectMapper();
    final RagE2ePolicy.Budget embedding = new RagE2ePolicy.Budget(1), judge = new RagE2ePolicy.Budget(1);
    final List<Map<String,Object>> evidence = Collections.synchronizedList(new ArrayList<>());
    HttpServer server;
    interface Transport { Reply post(URI uri, String authorization, byte[] body) throws Exception; }
    record Reply(int status, byte[] body) { }
    final Transport transport;
    final String embeddingBase, judgeBase, embeddingKey, judgeKey;
    RagE2eModelGate(String embeddingBase, String judgeBase, String embeddingKey, String judgeKey, Transport transport) {
        RagE2ePolicy.endpoint(embeddingBase,true); RagE2ePolicy.endpoint(judgeBase,false);
        this.embeddingBase=embeddingBase; this.judgeBase=judgeBase;
        this.embeddingKey=embeddingKey; this.judgeKey=judgeKey; this.transport=transport;
    }
    static Transport realTransport() {
        HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return (uri,authorization,body)-> {
            var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(65))
                    .header("Content-Type","application/json").header("Authorization",authorization)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofByteArray());
            return new Reply(response.statusCode(),response.body());
        };
    }
    Reply forward(String path, String authorization, byte[] body) throws Exception {
        boolean embed=path.equals("/embedding/embeddings");
        RagE2ePolicy.require(embed || path.equals("/judge/chat/completions"),"MODEL_PATH_REJECTED");
        (embed?embedding:judge).take();
        RagE2ePolicy.require(("Bearer "+(embed?embeddingKey:judgeKey)).equals(authorization),"MODEL_AUTH_MISMATCH");
        RagE2ePolicy.require(body.length<=100000,"MODEL_BODY_CAP");
        JsonNode request=JSON.readTree(body);
        RagE2ePolicy.modelRequest(request,embed);
        if (!embed) {
            RagE2ePolicy.require(RagE2eAudit.prompt!=null,"PROMPT_AUDIT_MISSING");
            RagE2ePolicy.require(request.path("messages").get(0).path("content").asText().equals(RagE2eAudit.prompt.systemPrompt())
                    && request.path("messages").get(1).path("content").asText().equals(RagE2eAudit.prompt.userPrompt()),
                    "ACTUAL_WIRE_PROMPT_MISMATCH");
        }
        Map<String,Object> item=new LinkedHashMap<>();
        item.put("provider",embed?"bailian":"deepseek"); item.put("attempt",1);
        item.put("requestSha256",RagE2ePolicy.hash(body));
        item.put("model",request.path("model").asText());
        if (embed) item.put("querySha256",RagE2ePolicy.hash(request.path("input").get(0).asText()));
        else {
            item.put("systemPromptSha256",RagE2ePolicy.hash(request.path("messages").get(0).path("content").asText()));
            item.put("userPromptSha256",RagE2ePolicy.hash(request.path("messages").get(1).path("content").asText()));
            item.put("maxTokens",request.path("max_tokens").asInt());
        }
        evidence.add(item);
        try {
            Reply response=transport.post(URI.create((embed?embeddingBase:judgeBase)
                    +(embed?"/embeddings":"/chat/completions")),authorization,body);
            item.put("httpStatus",response.status()); item.put("responseSha256",RagE2ePolicy.hash(response.body()));
            if (response.status()==200) {
                JsonNode parsed=JSON.readTree(response.body());
                if (parsed.path("usage").isObject()) {
                    Map<String,Long> usage=new LinkedHashMap<>();
                    for (String key:List.of("prompt_tokens","completion_tokens","total_tokens"))
                        if (parsed.path("usage").path(key).isIntegralNumber()) usage.put(key,parsed.path("usage").path(key).asLong());
                    item.put("usage",usage);
                }
                item.put("responseModel",parsed.path("model").asText());
                if (!embed) {
                    JsonNode choice=parsed.path("choices").path(0);
                    item.put("finishReason",choice.path("finish_reason").asText());
                    item.put("rawEvaluationSha256",RagE2ePolicy.hash(choice.path("message").path("content").asText()));
                }
            }
            return response;
        } catch (Exception failure) {
            item.put("outcome","UNKNOWN_OR_FAILED_NO_RETRY");
            throw failure;
        }
    }
    void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            try {
                RagE2ePolicy.require("POST".equals(exchange.getRequestMethod()),"MODEL_POST_ONLY");
                byte[] body=exchange.getRequestBody().readNBytes(100001);
                Reply result=forward(exchange.getRequestURI().getPath(),exchange.getRequestHeaders().getFirst("Authorization"),body);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(result.status(),result.body().length);
                exchange.getResponseBody().write(result.body());
            } catch (Exception error) {
                byte[] body="{\"error\":\"E2E_GATE_FAILED_NO_RETRY\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(502,body.length); exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        server.start();
    }
    String base(boolean embed) { return "http://127.0.0.1:"+server.getAddress().getPort()+(embed?"/embedding":"/judge"); }
    public void close() { if(server!=null)server.stop(0); }
}
