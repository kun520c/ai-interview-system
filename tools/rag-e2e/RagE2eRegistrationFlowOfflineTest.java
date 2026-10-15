import com.fasterxml.jackson.databind.JsonNode;
import com.kun.aiinterview.auth.controller.AuthController;
import com.kun.aiinterview.auth.dto.*;
import com.kun.aiinterview.auth.service.AuthService;
import com.kun.aiinterview.security.jwt.*;
import com.kun.aiinterview.user.entity.User;
import com.kun.aiinterview.user.enums.*;
import com.kun.aiinterview.user.mapper.UserMapper;
import jakarta.validation.Validation;
import java.io.*;
import java.lang.reflect.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Real registration/login/BCrypt/JWT in memory; scripted HTTP/JDBC, no sockets, SQL writes or model calls. */
final class RagE2eRegistrationFlowOfflineTest {
    static void ok(boolean condition) { RagE2eOfflineTest.ok(condition); }
    static <T> T proxy(Class<T> type,InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler));
    }
    static JsonNode body(HttpRequest request) throws Exception {
        if(request.bodyPublisher().isEmpty())return RagE2eRunner.JSON.nullNode();
        var bytes=new ByteArrayOutputStream(); var done=new CompletableFuture<Void>();
        request.bodyPublisher().get().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer b) { byte[] part=new byte[b.remaining()]; b.get(part); bytes.writeBytes(part); }
            public void onError(Throwable t) { done.completeExceptionally(t); }
            public void onComplete() { done.complete(null); }
        });
        done.get(2,TimeUnit.SECONDS); return RagE2eRunner.JSON.readTree(bytes.toByteArray());
    }
    static ResultSet result(List<Map<String,String>> rows) {
        List<String> columns=rows.isEmpty()?List.of("id"):new ArrayList<>(rows.getFirst().keySet());
        int[] index={-1};
        ResultSetMetaData metadata=proxy(ResultSetMetaData.class,(p,m,a)->switch(m.getName()) {
            case "getColumnCount"->columns.size(); case "getColumnLabel"->columns.get((Integer)a[0]-1);
            default->throw new AssertionError("Unexpected metadata method");
        });
        return proxy(ResultSet.class,(p,m,a)->switch(m.getName()) {
            case "next"->++index[0]<rows.size(); case "getMetaData"->metadata;
            case "getString"->rows.get(index[0]).get(columns.get((Integer)a[0]-1)); case "close"->null;
            default->throw new AssertionError("Unexpected ResultSet method");
        });
    }
    static final class Fixture implements RagE2eRunner.BusinessTransport,AutoCloseable {
        final Path originalOutput=RagE2eRunner.outputDirectory;
        final String historicalReportSha=RagE2ePolicy.hash(Files.readAllBytes(RagE2eRunner.OUT.resolve("report.json")));
        final List<String> calls=new ArrayList<>();
        final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4); // fixture cost only, no production config change
        final JwtTokenService jwt;
        final AuthController auth;
        final jakarta.validation.ValidatorFactory validators=Validation.buildDefaultValidatorFactory();
        User newUser; String password,issuedToken;
        String failPath=""; int failStatus=500; boolean answerTimeout,loginAsHistoricalUser,loginAsAdmin;
        Fixture() throws Exception {
            var properties=new JwtProperties(); properties.setSecret(Base64.getEncoder().encodeToString(new byte[64]));
            properties.setIssuer("offline-e2e"); properties.setAccessTokenExpiration(Duration.ofMinutes(5));
            jwt=new JwtTokenService(properties);
            UserMapper mapper=proxy(UserMapper.class,(p,m,a)->switch(m.getName()) {
                case "getUserByAccount"->newUser!=null && newUser.getAccount().equals(a[0])?newUser:null;
                case "getUserByEmail"->newUser!=null && newUser.getEmail().equals(a[0])?newUser:null;
                case "insertUser"->{ ok(newUser==null); newUser=(User)a[0]; ok(newUser.getId()==null); newUser.setId(361L); yield 1; }
                default->throw new AssertionError("No historical-user update or other mapper operation is allowed");
            });
            auth=new AuthController(new AuthService(mapper,encoder,jwt));
            RagE2eRunner.outputDirectory=Files.createTempDirectory(Path.of("target/rag-e2e"),"offline-registration-flow-");
            RagE2eRunner.report.clear(); RagE2eRunner.businessHttp.clear(); RagE2eRunner.sourceRows.clear();
            RagE2eRunner.sourceRows.put("question",List.of(Map.of("id","7612","reference_answer","synthetic approved reference")));
            RagE2eRunner.recoveryAttempt=true; RagE2eRunner.baseUrl="http://127.0.0.1:12345";
            RagE2eRunner.token=null; RagE2eRunner.terminalDetail=null; RagE2eRunner.submitResponse=null;
            RagE2eRunner.submitHttpStatus=0; RagE2eRunner.submissions.attempted.set(0);
        }
        Connection target() {
            return proxy(Connection.class,(p,m,a)->{
                ok(m.getName().equals("prepareStatement")); String sql=(String)a[0]; ok(sql.startsWith("SELECT "));
                Map<Integer,Object> parameters=new HashMap<>();
                return proxy(PreparedStatement.class,(s,method,args)->switch(method.getName()) {
                    case "setQueryTimeout","setMaxRows","close"->null;
                    case "setObject"->{ parameters.put((Integer)args[0],args[1]); yield null; }
                    case "executeQuery"->{
                        if(sql.startsWith("SELECT id FROM interview_session")) {
                            ok(parameters.get(1).equals(361L)); yield result(List.of());
                        }
                        ok(sql.startsWith("SELECT question_id, status, question_type, reference_answer_snapshot ")
                                && parameters.get(1).equals(901L) && parameters.get(2).equals(801L));
                        var row=new LinkedHashMap<String,String>(); row.put("question_id","7612"); row.put("status","WAITING_ANSWER");
                        row.put("question_type","MAIN"); row.put("reference_answer_snapshot","synthetic approved reference");
                        yield result(List.of(row));
                    }
                    default->throw new AssertionError("No JDBC write or other operation is allowed");
                });
            });
        }
        void requireJwt(HttpRequest request) {
            String token=request.headers().firstValue("Authorization").orElseThrow().substring("Bearer ".length());
            var claims=jwt.parseAndValidate(token);
            ok(claims.getSubject().equals("361") && newUser.getRole()==UserRole.USER && newUser.getStatus()==UserStatus.ENABLED
                    && jwt.matchesCredentialVersion(claims.get(JwtTokenService.CLAIM_CREDENTIAL_VERSION,String.class),newUser.getPassword()));
        }
        public HttpResponse<String> send(HttpRequest request) throws Exception {
            String path=request.uri().getPath(); calls.add(request.method()+" "+path); var input=body(request);
            if(path.equals(failPath))return reply(request,failStatus,Map.of("code",failStatus,"message","系统异常，请稍后重试"));
            if(path.equals("/api/auth/register")) {
                var dto=RagE2eRunner.JSON.treeToValue(input,RegisterRequest.class);
                ok(validators.getValidator().validate(dto).isEmpty() && dto.getAccount().matches("e2e_[a-f0-9]{20}"));
                password=dto.getPassword(); ok(!dto.toString().contains(password));
                var registered=auth.register(dto);
                ok(newUser.getId()==361L && newUser.getRole()==UserRole.USER && newUser.getStatus()==UserStatus.ENABLED
                        && !newUser.getPassword().equals(password) && encoder.matches(password,newUser.getPassword()));
                return reply(request,200,registered);
            }
            if(path.equals("/api/auth/login")) {
                var dto=RagE2eRunner.JSON.treeToValue(input,LoginRequest.class);
                ok(dto.getAccount().equals(newUser.getAccount()) && dto.getPassword().equals(password));
                var login=auth.login(dto); issuedToken=login.getData().getAccessToken();
                if(loginAsHistoricalUser)login.getData().setUserId(360L);
                if(loginAsAdmin)login.getData().setRole(UserRole.ADMIN);
                return reply(request,200,login);
            }
            requireJwt(request);
            if(path.equals("/api/interviews")) {
                ok(input.path("difficulty").asText().equals("EASY"));
                return reply(request,200,Map.of("code",200,"data",Map.of("sessionId",801,"plannedQuestionCount",5,
                        "currentQuestion",Map.of("interviewQuestionId",901))));
            }
            if(path.endsWith("/answers")) {
                ok(path.equals("/api/interviews/801/answers") && input.path("interviewQuestionId").asLong()==901
                        && input.path("answerContent").asText().equals("synthetic approved reference")
                        && input.path("requestId").asText().equals(RagE2eRunner.requestId));
                var intent=RagE2eRunner.JSON.readTree(Files.readAllBytes(RagE2eRunner.outputDirectory.resolve("report.json")));
                ok(intent.path("answerPostIntentRecorded").asBoolean() && RagE2eRunner.submissions.attempted.get()==1);
                if(answerTimeout)throw new IOException("synthetic timeout; no retry");
                return reply(request,200,Map.of("code",200,"data",Map.of()));
            }
            ok(request.method().equals("GET") && path.equals("/api/interviews/801"));
            String state=answerTimeout || failPath.endsWith("/answers")?"FAILED":"EVALUATED";
            return reply(request,200,Map.of("code",200,"data",Map.of("questions",List.of(Map.of(
                    "interviewQuestionId",901,"answer",Map.of("answerStatus",state))))));
        }
        HttpResponse<String> reply(HttpRequest request,int status,Object value) throws Exception {
            return new RagE2eOfflineTest.FakeResponse(status,RagE2eRunner.JSON.writeValueAsString(value),request);
        }
        void execute() throws Exception { RagE2eRunner.submitOnce(target(),this); }
        long answerPosts() { return calls.stream().filter(c->c.equals("POST /api/interviews/801/answers")).count(); }
        public void close() throws Exception {
            ok(historicalReportSha.equals(RagE2ePolicy.hash(Files.readAllBytes(RagE2eRunner.OUT.resolve("report.json")))));
            validators.close(); RagE2eRunner.outputDirectory=originalOutput; RagE2eRunner.recoveryAttempt=false;
            RagE2eRunner.candidatePassword=null; RagE2eRunner.token=null; RagE2eRunner.terminalDetail=null;
            RagE2eRunner.report.clear(); RagE2eRunner.businessHttp.clear(); RagE2eRunner.sourceRows.clear();
            RagE2eRunner.submissions.attempted.set(0); // synthetic offline process only; never used by execution mode
        }
    }
    static void run() throws Exception {
        RagE2eOfflineTest.test(()->{
            String one=RagE2eRunner.randomCandidatePassword(),two=RagE2eRunner.randomCandidatePassword();
            ok(!one.equals(two) && one.length()==36 && one.getBytes(StandardCharsets.UTF_8).length==36);
        });
        RagE2eOfflineTest.test(()->{try(var f=new Fixture()) {
            f.execute(); ok(f.calls.equals(List.of("POST /api/auth/register","POST /api/auth/login","POST /api/interviews",
                    "POST /api/interviews/801/answers","GET /api/interviews/801")) && f.answerPosts()==1);
            String exported=RagE2eRunner.JSON.writeValueAsString(RagE2eRunner.report);
            ok(!exported.contains(f.password) && !exported.contains(f.issuedToken) && !exported.contains(f.newUser.getPassword()));
            var error=io.milvus.shaded.io.grpc.Status.INTERNAL.withDescription("password="+f.password).asRuntimeException();
            ok(!RagE2eRunner.JSON.writeValueAsString(RagE2eRunner.failureDetails(error)).contains(f.password));
        }});
        for(String path:List.of("/api/auth/register","/api/auth/login","/api/interviews"))
            RagE2eOfflineTest.test(()->{try(var f=new Fixture()) {
                f.failPath=path; f.failStatus=path.endsWith("login")?401:500;
                RagE2eOfflineTest.rejects(f::execute); ok(f.answerPosts()==0 && f.calls.getLast().endsWith(path));
            }});
        RagE2eOfflineTest.test(()->{try(var f=new Fixture()) {
            f.failPath="/api/interviews"; f.failStatus=201; RagE2eOfflineTest.rejects(f::execute); ok(f.answerPosts()==0);
        }});
        RagE2eOfflineTest.test(()->{try(var f=new Fixture()) {
            f.loginAsHistoricalUser=true; RagE2eOfflineTest.rejects(f::execute);
            ok(f.calls.size()==2 && f.answerPosts()==0);
        }});
        RagE2eOfflineTest.test(()->{try(var f=new Fixture()) {
            f.loginAsAdmin=true; RagE2eOfflineTest.rejects(f::execute); ok(f.calls.size()==2 && f.answerPosts()==0);
        }});
        RagE2eOfflineTest.test(()->{try(var f=new Fixture()) {
            f.failPath="/api/interviews/801/answers"; f.failStatus=500; f.execute();
            ok(f.answerPosts()==1 && RagE2eRunner.submitHttpStatus==500 && RagE2eRunner.submitResponse==null);
        }});
        RagE2eOfflineTest.test(()->{try(var f=new Fixture()) {
            f.answerTimeout=true; f.execute(); ok(f.answerPosts()==1
                    && RagE2eRunner.report.get("submitTransportOutcome").equals("UNKNOWN_NO_RESUBMISSION"));
        }});
    }
}
