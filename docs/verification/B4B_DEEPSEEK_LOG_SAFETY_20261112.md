# B4-B：DeepSeek 异常链与日志敏感信息保护

执行日期：2026-11-12（Asia/Shanghai）。范围：DeepSeek HTTP、响应解析及异常日志边界。真实模型、Embedding、Milvus 调用均未启用。

## 1. Git 基线与工作区

- 仓库：`/Users/kun/Developer/projects/ai-interview-system`；分支：`main`。
- 入场 HEAD：`89b77dc082272e38cfbbecb4b57c37edae9ee74d`，与用户参考基线一致。本地 `origin/main` 同值；未 fetch，不将本地跟踪引用当作远端实时核验。
- 入场暂存区为空；`git diff` 只有 `mvnw` 的 `100644 → 100755` 权限变化。未跟踪项为 `compose.mac.local.yaml` 和 B4-A 审计报告。
- B4-A 报告、Compose 文件及 `mvnw` 正文/权限均保留，结束时校验 SHA-256。没有执行 `git add/commit/push/reset/clean/stash`。
- 实测运行环境：macOS 26.7.1 ARM64、Temurin 21.0.13、Maven Wrapper 3.9.16。依赖源码核实版本为 Spring Framework 6.2.19。
- 本轮没有修改数据库 Schema、YAML、依赖、全局异常体系、业务状态机或前端。

## 2. 修复前的真实传播链

HTTP 发起点是 `DeepSeekEvaluationClient.requestCompletion()` 中的 `RestClient.post().uri("/chat/completions").body(request).retrieve().body(...)`。评价通过 `DeepSeekJudgeProvider` 调用它；报告在 `DeepSeekInterviewReportGenerator.generate()` 中调用 `deepSeekClient.completeJson(...)`，共享同一 Client。

核实本机 `/Users/kun/.m2/repository/org/springframework/spring-web/6.2.19/spring-web-6.2.19-sources.jar`：

1. `StatusHandler.defaultHandler()` 默认识别 4xx/5xx，并非所有非 2xx。它读取 statusText、headers、body，调用 `getErrorMessage()` 将正文放进异常 message。
2. 400/401/402/429 产生 `HttpClientErrorException` 或其子类；500/503 产生 `HttpServerErrorException` 或其子类。异常对象也保留响应字节和 headers。
3. 旧 Client 捕获 `RestClientException`，抛出 `new ExternalServiceException("调用DeepSeek评价服务失败", exception)`。`ExternalServiceException` 的双参构造器直接调用 `super(message, cause)`，原始异常仍可达。
4. `EvaluationOrchestrationService.evaluate()` 尝试持久化失败 trace 后重抛；`InterviewWorkflowService.evaluateAnswer()` 尝试标记答案 FAILED 后重抛。收尾失败通过 suppressed 附加，不替换原始异常。
5. 报告的 `InterviewReportService.generateReport()` 同样先以当前 attemptVersion 标记 FAILED，再抛原异常；收尾失败仍可附加 suppressed。
6. `GlobalExceptionHandler.handleExternalServiceException()` 第 130 行执行 `log.error("外部服务异常", e)`。Logback 渲染完整异常，包含 message、cause 和 suppressed。
7. 同一处理器只向 HTTP 客户端返回状态 502 和 `Result(code=502, message="外部服务暂时不可用", data=null)`，未把异常正文放进响应 DTO。

因此必须分别判断：**HTTP 响应原本已脱敏；日志原本会泄漏；异常对象内部原本仍持有正文。** 修改外层中文文案不能解除 cause 引用，检查 `getMessage()` 一层也不能证明安全。

其他日志入口的检索结果：全局未知异常处理器的 `log.error("系统异常", e)` 也会打印完整链；评价/报告 Service 没有自己的异常日志，也没有主动打印 Prompt、请求体或 Authorization。知识处理模块另有完整异常日志，但不在 DeepSeek 调用链内，本轮未改。现有请求参数异常 handler 的 `getMessage()` 日志也未作全局治理。

另核实两个同边界旁路：评价和报告的 Jackson 解析异常 cause 可含模型正文片段；旧 Client 直接拼接任意 `finish_reason`，可包含恶意换行。这两处一并作最小修复。

## 3. 确定性复现证据

生产代码修改前，新增 Mock HTTP + 模拟 Controller + 真实 `GlobalExceptionHandler` 复现测试。仅使用合成标记：

```text
B4B_TEST_SENSITIVE_BODY_20261112
B4B_FAKE_API_KEY
B4B_FAKE_SYSTEM_PROMPT
B4B_FAKE_USER_ANSWER
```

错误正文还包含 CR/LF、ESC、NUL 和 `ERROR FORGED_LOG_RECORD`。请求使用假 Bearer Key；没有使用或输出真实模型凭据、真实用户答案或真实 Prompt。

逐一返回 HTTP 400、401、402、429、500、503。六个用例均实际验证：

- 业务调用抛出 `ExternalServiceException`，外层 message 不含 sentinel。
- cause 为 `RestClientResponseException`，其 message 和 `getResponseBodyAsString()` 都含 sentinel。
- `Throwable.printStackTrace()` 完整文本含 sentinel。
- Logback `ListAppender<ILoggingEvent>` 捕获的 `formattedMessage` 仅为“外部服务异常”，但 `ThrowableProxyUtil.asString()` 含 sentinel。
- `PatternLayout("%level %logger - %msg%n%ex{full}")` 的最终文本含 sentinel、假 Key、假 Prompt 和假答案。
- MockMvc 响应仍为固定安全 502。

修复前运行 `baseline`：**Tests 6 / Failures 0 / Errors 0 / Skipped 0，退出 0**。这六项是“确认存在泄漏”的断言，通过代表复现成功，不代表修复前安全。每个状态输出一条证据摘要，例如：

```text
B4B_BASELINE status=401 type=ExternalServiceException outerSafe=true causeBody=true stackBody=true renderedBody=true response=502-safe
```

临时证据保存在 `/private/tmp/ai-b4b-20261112/baseline.log`、`baseline-reports/` 和 `DeepSeekLogSafetyTest.before.java`。正式测试已替换为安全断言，不保留允许泄漏的测试开关。

## 4. 候选方案与取舍

**A：只改 GlobalExceptionHandler 日志。** 不采用。去掉全局栈输出可降低这一处暴露，但原始异常仍持有正文，其他调用方打印仍会泄漏；同时损失其他模块排障信息。只改最外层 message 更无法阻止 cause 输出。

**B：在 DeepSeek Client 边界转换。** 采用。单次请求的 `onStatus` 优先于 Spring 默认 handler，在其创建含正文的异常之前抛出新的安全 `ExternalServiceException`。处理器不读取错误正文、statusText 或诊断 headers；只取数字状态。按请求配置使手工构造 Client 的测试与生产共享同一边界，不依赖全局 RestClient 自定义器。

**C：增加受控诊断信息。** 以最小形式结合 B：复用现有异常类型，在固定 message 中加入白名单 category、固定 stage 和数值 HTTP 状态；保留新异常的本地调用栈。不新增异常类、诊断 record 或通用日志框架，当前没有结构化字段消费者需要这些抽象。

网络和解析异常可能在原始 message/cause/suppressed 中携带不可信内容，均不继续挂入新异常。分类只检查 Java 类型，最多遍历 10 层 cause，超深链降为受控通用类别，不复制任意异常类名或 message。不广泛捕获 `RuntimeException`；独立编程错误仍原样传播，有专门测试确认。

## 5. 文件修改与原因

所有路径均为本机绝对路径。

1. 修改 `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java`：增加请求级状态 handler；转换网络/响应解码异常；评价解析不保留危险 cause；未知 `finish_reason` 映射为 UNKNOWN。
2. 修改 `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGenerator.java`：报告 JSON 解析失败只保留安全阶段/类别，去掉 Jackson 原始 cause。
3. 修改 `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClientTest.java`：两个旧断言从“必须保留原始 cause”更新为“无 cause 且保留状态/阶段”。原有成功契约和其他失败断言保留。
4. 新增 `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekLogSafetyTest.java`：确定性 HTTP/IO/JSON 故障、异常对象、完整栈和真实日志渲染的 36 项测试。
5. 修改 `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java`：扩充 HTTP 400/401/402、外层非法 JSON、恶意 finish reason；在实际 Controller/Service/MyBatis 路径捕获应用日志并验证显式恢复。保留原有状态、幂等、成功、历史兼容等断言；类内由 85 项增为 95 项。
6. 新增本文 `/Users/kun/Developer/projects/ai-interview-system/docs/verification/B4B_DEEPSEEK_LOG_SAFETY_20261112.md`。

未修改 `GlobalExceptionHandler`、`ExternalServiceException`、`DeepSeekConfiguration` 或任何业务 Service 生产代码。两个生产文件合计仅调整边界错误处理。

## 6. 修复后的异常与日志契约

- HTTP 非 2xx：受控异常，例如 `调用DeepSeek评价服务失败 [stage=chat_completion, category=HTTP_STATUS, httpStatus=429]`；没有原始 cause/suppressed、body、headers 或状态文本。除必测六个错误状态外，测试还覆盖 302 和 599；非成功状态不能被当作正常完成继续解析。
- 连接拒绝/DNS：`CONNECTION_ERROR`；JDK 连接超时：`CONNECT_TIMEOUT`；请求/读取超时：`TIMEOUT`；其他 IO：`IO_ERROR`；外层 JSON 解码：`RESPONSE_JSON`；其他 RestClient 错误：`HTTP_CLIENT_ERROR`。均为固定字符串，仍抛 `ExternalServiceException`。
- 内层评价 JSON：`stage=evaluation_parse, category=INVALID_JSON`；报告 JSON：`stage=report_parse, category=INVALID_JSON`。空值、缺字段、空内容仍按原校验失败。
- `finish_reason` 仅显示已知的 `length/content_filter/insufficient_system_resource/tool_calls`，缺失显示 MISSING，其余显示 UNKNOWN；任何非 stop 值仍失败。
- 全局仍记录一次 ERROR 和完整本地调用栈。没有隐藏异常、改成成功、增加 HTTP 自动重试或重复记录错误。控制字符、任意上游文本不会被复制到上述消息或 cause 链。
- 对外响应维持 502 和“外部服务暂时不可用”；上游 401/402/429 不误映射成应用自己的鉴权/限流响应。
- 共享网络阶段名是 `chat_completion`，评价/报告由本地栈帧区分；解析阶段另有明确标签。没有扩展为耗时、关联 ID、指标或费用统计项目。

这项保证限于本轮 DeepSeek 边界输出。Java 仍允许上层添加独立的数据库收尾异常作为 suppressed；本轮不改变该错误保全契约，也不宣称所有第三方库和所有日志入口已经全局脱敏。

## 7. 故障测试与业务契约证据

`DeepSeekLogSafetyTest` 共 36 项：

- 六个指定 HTTP 状态 × 回显/不回显输入，共 12 项；请求确实带假授权头，上游诊断 header 同样含合成恶意内容。
- 错误正文的 InputStream 一旦被读取就抛断言错误、statusText 一旦被读取也抛断言错误，证明安全 handler 在默认正文读取前终止；允许框架正常关闭响应流。
- 302、599 两项补充非成功状态。
- 七项有类型的网络故障：连接拒绝、DNS、连接超时、请求超时、Socket 读取超时、普通 IO、包装后的超时；原始异常附有敏感 suppressed。
- 200 响应读取流超时；任意 RestClient 包装及 suppressed；独立编程异常保真，各一项。
- 十一项响应异常：外层非法 JSON/类型错误、不支持媒体类型、空响应、内层非法 JSON/分数字符串、JSON null、空白内容、截断、内容过滤、恶意 finish reason。

安全断言不是只检查 `getMessage()`：同时检查实际异常类型、cause/suppressed、`toString()`、Java 完整 stack trace、Logback throwable proxy、最终 PatternLayout 文本和 MockMvc HTTP 响应；还要求日志保留 ERROR、阶段/类别/状态、本地 Client 栈，防止以“完全不打日志”通过。

`FullInterviewBusinessOfflineTest` 的 15 项答案故障和 17 项报告故障通过真实认证、Controller、Service、MyBatis 与临时 MySQL，只有外部模型/Embedding/Milvus 为替身。应用日志捕获覆盖 `com.kun.aiinterview`，要求恰好一条安全错误日志且包含相应业务 Service 栈。

- 答案故障后落为 FAILED，Session 不推进、不插入评价/报告/弱项；同 requestId 改答案仍 409；原 payload 显式重试成功后仅一条评价，成功重放不再调用模型。
- 报告故障后 Session 维持 COMPLETED，report_status=FAILED，报告/弱项不发布；显式重试进入 READY，重复请求复用报告，弱项计数不重复增加。
- 初次失败模型调用次数为 1，用户显式恢复后为 2，成功重放仍为 2；没有自动 HTTP 重试。
- 成功 JSON 协议、严格整数校验、Java 计分/追问、历史 opaque 评价兼容保留，由既有测试及完整回归共同验证。

Mock HTTP 与类型化异常注入不证明真实网络超时秒数、供应商可用性或真实计费行为。

## 8. 执行环境、各轮结果与打包

本轮创建独立容器 `ai-interview-b4b-20261112-mysql`：MySQL 8.0.45，`127.0.0.1:13323`，tmpfs 数据目录，全局时区 `+08:00`。只存在 `ai_interview_test` 和 MySQL 系统库。由仓库 SQL 的临时副本仅替换建库/USE 名称，不改版本管理 Schema，不连接正式 local/evaluation 数据库或其他项目的容器。

执行器 `/private/tmp/ai-b4b-20261112/run_maven.py` 每轮归档旧 Surefire，保留本轮 XML 和 summary，避免把上次报告混入计数。子进程清除所有继承的 `RUN_REAL_*`、`SPRING_*`、`MILVUS_*`、`DEEPSEEK_*`、`JEV_*`、`AI_JUDGE_*`、旧 DB/TEST_DB 与 JVM/Maven 覆盖；只注入本轮临时测试连接，`DEEPSEEK_ENABLED=false`。测试类显式装配的模型路径由 Mock HTTP 接管。`MILVUS_ENABLED` 与真实 Smoke 开关保持 unset，隔离初始化器与双门禁不变。

为沿用 B4-A 已确认的 Mockito 自挂载兼容方式，仅向本轮子进程传入：

```text
JAVA_TOOL_OPTIONS=-javaagent:/Users/kun/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar
```

未修改 pom、持久环境或项目安全配置。Docker socket 的普通沙箱只读检查最初因权限失败，通过允许的执行环境后继续；这不是测试失败。没有发生自动审批拒绝。

各轮 T/F/E/S 表示 Tests/Failures/Errors/Skipped，以下计数不可相加当成独立用例总数：

1. `baseline`：**6/0/0/0，退出 0**，确认修复前泄漏。
2. `focused-client`：**79/0/36/0，退出 1**。新测试改用生产配置构造器后漏填超时属性，36 项在 setUp 失败；其余 43 项通过。补齐假配置，不改生产配置。
3. `focused-client-fixed`：**79/1/0/0，退出 1**。无读取测试错误地禁止了 close 中获取 InputStream；改为禁止真正的 `read()`，保留不读正文的原始验证目标。
4. `focused-final`：**281/32/0/0，退出 1**。日志断言把安全栈帧 `AuthorizationFilter` 匹配成 Header 泄漏；32 项均只命中类名。改为检查 `Authorization:`、`Bearer ` 和完整假 Key/Token，保留全部正文/控制字符/栈断言。
5. `focused-verified`：**281/0/0/0，退出 0**，最终聚焦全部通过。实际 Maven 日志中本轮 sentinel 出现 **0 次**。

最终聚焦的完整 Maven 子命令：

```sh
sh mvnw -B -ntp -Dtest=DeepSeekLogSafetyTest,DeepSeekEvaluationClientTest,DeepSeekInterviewReportGeneratorTest,DeepSeekConfigurationTest,DeepSeekPropertiesTest,DeepSeekJudgeProviderTest,DeepSeekInterviewReportGeneratorConfigurationTest,GlobalExceptionHandlerMvcTest,FullInterviewBusinessOfflineTest,InterviewWorkflowServiceTest,InterviewReportServiceTest,EvaluationOrchestrationServiceTest test
```

6. `full-regression`：`sh mvnw -B -ntp test`，**1635/0/0/19，退出 0，BUILD SUCCESS**，Maven 报告耗时 32.509 秒。本轮 sentinel 在完整 Maven 日志中出现 **0 次**。当前总量来自本轮 Surefire XML；与历史 1589 的差值为新增专项 36 项和现有业务参数化新增 10 项，不把历史数当门槛。
7. `package`：`sh mvnw -B -ntp -DskipTests package`，**退出 0，BUILD SUCCESS**。本命令未执行测试，Tests/Failures/Errors/Skipped 均不适用；执行器汇总为 0/0/0/0 仅表示没有测试 XML，不表示又通过一轮回归。此前完整回归已通过。
8. 最终 `git diff --check`：**退出 0**。新增未跟踪源码/报告另做末尾空白及冲突标记检查。

19 个跳过项逐类核对：`RealEmbeddingSmokeTest` 1、`RealMilvusVectorStoreSmokeTest` 1、`KnowledgePersistenceIntegrationTest` 10、`RealKnowledgeDocumentProcessingSmokeTest` 2、`RealKnowledgeDocumentProcessingFailureIntegrationTest` 2、`RealMilvusSchemaValidationSmokeTest` 2、`AiSystemBaselineEvaluationTest` 1。均属 opt-in 门禁；没有把普通 MySQL Mapper/事务测试跳过。

J1～B3 与并发保护的本轮完整回归证据（下列均 F/E/S=0）：`JudgeConfigurationTest` 7、`FullInterviewBusinessOfflineTest` 95、`InterviewConcurrentReplayIntegrationTest` 18、`InterviewPostClaimFailureIntegrationTest` 3、`InterviewReportFinalConsistencyIntegrationTest` 24、`InterviewWorkflowTransactionServiceIntegrationTest` 6、`InterviewReportTransactionServiceIntegrationTest` 9、`InterviewEvaluationClaimConcurrencyIntegrationTest` 11、`StaleRecoveryCasIntegrationTest` 3。覆盖单模型配置、成功/历史回放、失败收尾、FINAL 一致性、stale 接管、attemptId/version fencing 和 Session CAS；真实 MySQL 用例确实运行。

构建产物：

- `/Users/kun/Developer/projects/ai-interview-system/target/ai-interview-system-0.0.1-SNAPSHOT.jar`（Spring Boot 可执行包）。
- `/Users/kun/Developer/projects/ai-interview-system/target/ai-interview-system-0.0.1-SNAPSHOT.jar.original`（repackage 之前的原始包）。

逐轮临时日志/报告位于 `/private/tmp/ai-b4b-20261112/`：每个运行标签对应 `<label>.log`、`<label>-summary.json` 和有执行测试时的 `<label>-reports/`。完整 XML 已归档到 `full-regression-reports/`，打包不会抹掉回归证据。临时 DB 口令只在该临时目录的权限 0600 文件和子进程环境中使用，不写入源码、报告或受版本管理配置。

原有文件结束时 SHA-256 与入场一致：

```text
mvnw  cae96cef89ebea3531221f4ae17c23cf8edf67d00eae8306d4186ae1bbed4d02  mode 0755
compose.mac.local.yaml  60eb30bf23a96d95ed98305e813a0492703e92eeebeb03ab14379cc223a81974
B4A_DEEPSEEK_RELIABILITY_AUDIT_20261112.md  841937e21a4d0b8b086ade02ef5c254c90c8a7079d3115dea1fb299cac9375f3
```

## 9. 未验证事项与独立风险

- 未调用真实 DeepSeek、Embedding、Milvus；门禁跳过不代表真实外部集成通过。
- 网络故障验证是确定性类型/流注入；未做真实 socket 延迟、断网、慢 body、代理、TLS、负载或费用实验。
- 未开启并审计 wire-level DEBUG、APM/Java agent 报文采集或部署端自定义日志拦截器。本修复不保证其他组件主动打印原始请求时也安全。
- 未扫描真实历史数据库。现有 rawResult 持久化/历史读取契约保留，不把业务数据库中的原始模型结果当作可打印诊断数据。
- Embedding/Milvus 异常链、请求参数日志、数据库恢复 suppressed、历史持久文本解析等独立日志风险没有扩展治理。本轮测试限定实际 DeepSeek 失败链。
- B4-A 已记录的 Milvus SDK 重试、评分时间预算、并发容量、前端 GENERATING 恢复入口仍属于独立批次；没有在本轮加入自动退避、线程池、Redis、MQ 或新 Provider。

## 10. 后续建议与交付状态

B4-B 结束后可人工审阅两个生产文件、专项测试和本报告，再决定后续版本管理操作。未来诊断扩展继续使用固定字段；不要为排障重新挂回原始供应商异常，也不要在 Logger 中拼接上游正文。仅在单独授权的批次验证时间预算、真实 socket 行为或其他模块日志。

本轮已完成编码、编译、Mock/单元验证、隔离真实 MySQL 回归、打包和 diff 检查。测试结束后按容器名及 purpose 标签核验，已停止并移除本轮 tmpfs MySQL 容器；其他容器和持久卷未操作。代码尚未暂存、提交、推送；不自动进入 B4-C/D 或执行外部发布。

## 11. 约 14 分钟源码学习路径

1. **0～2 分钟：请求与状态 handler。** 阅读 [DeepSeekEvaluationClient.java:148](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java:148)，再看本地 Spring source jar 的 `StatusHandler.defaultHandler/getErrorMessage` 和 `DefaultRestClient.onStatusInternal/readWithMessageConverters`。理解 `retrieve()` 建立响应处理规则，`.body()` 执行请求/提取；请求级 handler 排在默认 handler 前。默认 4xx/5xx 会复制正文，本次提前抛安全异常。
2. **2～4 分钟：Throwable 的四个部分。** 阅读 [ExternalServiceException.java:3](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/common/exception/ExternalServiceException.java:3)。message 是描述，cause 是另一个异常对象引用，stackTrace 是调用位置，suppressed 保存处理同一操作时的附加失败。`printStackTrace()` 不只输出最外层 message；传入原始 cause 就会保留可达正文。此次创建新异常而不挂原 cause，仍有本地栈可定位边界。
3. **4～6 分钟：客户端脱敏与日志脱敏。** 阅读 [GlobalExceptionHandler.java:125](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/common/exception/GlobalExceptionHandler.java:125) 和报告第 3 节。处理器分别做 Logger 调用和 Result 构造；DTO 安全不影响 Logger 的异常遍历。思考为什么 `log.error("固定文案", e)` 仍可能泄漏。
4. **6～8 分钟：安全诊断取舍。** 阅读 [DeepSeekEvaluationClient.java:191](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java:191)、298、308，以及 [DeepSeekInterviewReportGenerator.java:99](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGenerator.java:99)。类型分类只返回常量；数字状态有用，任意供应商 message 没有安全保证。解析器可能引用错误 token，所以连 JSON 异常 cause 也要处理。未知 finish reason 只显示 UNKNOWN。
5. **8～11 分钟：真实日志断言。** 阅读 [DeepSeekLogSafetyTest.java:110](/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekLogSafetyTest.java:110) 和 228 行。比较 formattedMessage、throwable proxy、完整栈和 PatternLayout；测试既要求敏感内容不存在，也要求状态/类别/本地栈存在。`AuthorizationFilter` 是安全类名，不能把它和 `Authorization: Bearer ...` 混为一谈。
6. **11～14 分钟：业务失败为何仍能恢复。** 阅读 [FullInterviewBusinessOfflineTest.java:343](/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java:343)，跟随 `InterviewWorkflowService` 的 catch/markEvaluationFailed 和 `InterviewReportService` 的 recoverReportFailure。异常类型保持 ExternalServiceException，因此 502/FAILED 契约不变；显式重试是新业务请求，和增加自动 HTTP 重试是两件事。最后查完整回归中的 stale、attempt fencing、Session CAS 测试，理解日志边界修复无需改动业务状态机。
