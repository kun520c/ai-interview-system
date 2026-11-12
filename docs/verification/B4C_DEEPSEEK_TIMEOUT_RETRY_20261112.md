# B4-C：DeepSeek 错误分类、超时预算与重试治理

执行日期：2026-11-12（Asia/Shanghai）。本轮选择方案 A：保留单次应用级 HTTP 尝试，修复安全诊断分类，验证实际 HTTP 超时与既有业务恢复。没有启用自动重试，没有修改超时数值或公共错误语义。

## 1. 基线、范围与保护

- 仓库：`/Users/kun/Developer/projects/ai-interview-system`，分支 `main`，HEAD `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，与用户参考基线一致。没有 fetch，不把本地远程引用当作远端实时核验。
- 已读取仓库 `AGENTS.md`、实际 Git 状态、B4-A 审计报告、B4-B 验收报告及相关调用链。当前以源码为准；AGENTS 中部分历史阶段说明不代表当前 trace 提交顺序。
- 入场暂存区为空。已有修改：`mvnw` 权限、DeepSeek Client、Report Generator、ClientTest、FullInterviewBusinessOfflineTest；已有未跟踪文件：Compose、B4-A/B4-B 报告、DeepSeekLogSafetyTest。
- 入场九个文件的内容与权限记录在 `/private/tmp/ai-b4c-20261112/entry-manifest.json`，副本在同目录 `entry/`。本轮只继续扩展其中 Client 和业务离线测试；另外七个文件 SHA-256 保持一致，包括 B4-B 的生产报告解析修复及完整日志安全测试。
- 没有执行 add、commit、push、reset、clean、stash；没有改 Schema、YAML、pom、Provider、前端或业务事务。
- 本机实测：macOS 26.7.1 ARM64、Temurin 21.0.13、Maven Wrapper 3.9.16；Spring Framework 6.2.19、Milvus SDK 2.6.20。`application-local.yaml` 不存在。没有读取真实密钥；模型测试全部使用假输入。

## 2. 原始调用链及准确重试行为

### 2.1 评分

`InterviewController.submitAnswer → InterviewService.submitAnswer → InterviewWorkflowService.evaluateAnswer → EvaluationOrchestrationService.evaluate → EvaluationRetrievalAdapter → Query Embedding → Milvus search → MySQL 资格过滤/回填 → EvaluationPromptBuilder → DeepSeekJudgeProvider → DeepSeekEvaluationClient.evaluate/completeJson/requestCompletion → RestClient POST /chat/completions`。

返回后依次经过 `LlmEvaluationValidator → EvaluationScoreCalculator → EvaluationStandard → FollowUpPolicy → Workflow 收口短事务`。主问题来自已冻结题库快照；Java 决定分数和追问动作。主答评价、反馈和候选追问共用一次模型响应；追问答案是另一次完整评分。

Workflow 先以 requestId/内容做幂等判断，以新的 attemptId 通过 CAS 占有答案。成功结果及 RAG trace 在持有当前 attempt 的短事务中一起提交；模型或校验失败时，已完成检索的失败 trace 经过 ownership 检查独立落库，再标记 FAILED。已有有效评价直接重放。

### 2.2 报告

`InterviewController.generateReport → InterviewReportService.generateReportForApi/generateReport → Session version CAS 占有 GENERATING → 查询/校验每道 MAIN 的最终有效 FINAL → Java 聚合分数 → DeepSeekInterviewReportGenerator.generate → 同一 DeepSeekEvaluationClient.completeJson → JSON/长度校验 → completeReportGeneration 短事务`。

报告不执行 Embedding/Milvus。最后一题完成与报告生成是两个后端 HTTP 请求。Report、Weakness 和 READY 一起提交；失败按本次 attemptVersion 标记 FAILED。READY 直接复用；已有报告的恢复不重复生成/增加弱项次数。

### 2.3 同步线程、异常与业务恢复

- 两个入口返回普通同步值，没有 MVC 异步返回、`@Async` 或业务执行池。整个调用链占用 Servlet/Tomcat 请求线程。
- Spring JDK factory 内部使用 `sendAsync`，随后 `future.get()`；这不释放调用线程。本轮确定性 Mock 验证请求在调用线程执行，本机 HTTP 夹具验证调用方在响应停滞时仍等待。
- DeepSeek 不使用供应商 SDK，也不经过 Spring AI RetryTemplate。应用只执行一次 RestClient POST，没有 HTTP 状态重试、内容重试、自动退避或 Retry-After 等待。Embedding 的 Spring AI RetryTemplate 明确 `maxAttempts=1`。
- `ExternalServiceException` 类型不变，DeepSeek 边界不挂原始 cause/suppressed。GlobalExceptionHandler 仍返回 `HTTP 502 / code=502 / 外部服务暂时不可用 / data=null`；上游 401/403 不变成用户 JWT 鉴权失败。
- FAILED 重试是新的客户端请求。评分重新执行 RAG 和模型；报告重新调用生成器。无累计次数限制或冷却期。新鲜 EVALUATING/GENERATING 返回 409；stale 才允许接管。
- stale 只隔离提交权，不取消旧网络请求。旧 attemptId/旧 Session version 不能提交迟到的有效结果或覆盖新 attempt 的失败状态；不能据此声称供应商只执行/计费一次。

### 2.4 底层隐式行为

本机 JDK `lib/src.zip` 中的 `MultiExchange` 已核实：默认 `enableAllMethodRetry=false`，但 `disableRetryConnect=false`。连接失败分支可再尝试一次，即使请求方法是 POST；连接重试沿用剩余 connect timeout。HTTP/2 标记为未处理的交换也有恢复分支。`retriedOnce` 限制此类恢复；另有默认 5 的交换/重定向总保护值，不能把它解释成每个模型请求固定执行 5 次。项目没有修改这些 JVM 参数，没有配置认证重试器或自动跟随重定向。

因此本报告的“1 次”是 **应用调用次数，或指定测试夹具实际收到的请求数**，不是对所有真实 DNS/TLS/代理/HTTP2 情况的 exactly-once 承诺。[JDK 21 网络参数说明](https://docs.oracle.com/en/java/javase/21/core/java-networking.html)、[OpenJDK MultiExchange 源码](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/java.net.http/share/classes/jdk/internal/net/http/MultiExchange.java)可辅助阅读，准确版本判断以本机 21.0.13 源码为准。

Milvus 保留原实现。本轮重新检查实际 jar 的 `RetryConfig`、`RetryConfigBuilder`、`RpcUtils` 字节码：默认 `maxRetryTimes=75`（最多 75 次 callable）、初始退避 10ms、乘数 3、上限 3000ms、`retryOnRateLimit=true`、`maxRetryTimeoutMs=0`。DEADLINE_EXCEEDED、认证、权限、非法参数等直接失败；UNAVAILABLE 等可重试 RPC 与业务 RateLimit 分支可重复执行，不能说所有超时都会重试 75 次。应用配置每次 RPC deadline 为 10 秒，不是 search 整体上限。字节码证据保存于本轮临时目录；没有运行真实 Milvus 或对 SDK 做新的故障注入/治理。

## 3. 故障注入与问题确认

先新增 `DeepSeekFailurePolicyTest`，在 B4-B 生产代码上运行，再实施修复。

修复前结果为 **42 / 25 / 0 / 0，退出 1**（Tests/Failures/Errors/Skipped）。失败分布：14 项 HTTP reason 缺失、8 项 Retry-After 场景同样缺少 reason、1 项显式恢复测试首个失败缺少 reason、2 项嵌套类型分类错误。没有把这 25 个诊断断言失败说成 25 个独立业务 bug。

确认的问题：

1. 所有 HTTP 非成功状态只有 `category=HTTP_STATUS` 和数字状态，无法按固定原因分类；400 配置/请求错误、429 限流、503 过载混在一个类别中。
2. `JsonParseException → SocketTimeoutException` 被外层 JSON 类型提前截断，误分类为 RESPONSE_JSON；`HttpTimeoutException → HttpConnectTimeoutException` 提前得到 TIMEOUT，丢失明确的连接超时类型。

没有复现“必须增加自动重试才能正确恢复”的问题。既有 FAILED 显式恢复和幂等结果重放可用。本机 HTTP 实验也没有发现 DeepSeek 单次读取等待无限延长，不改已有 3s/60s 参数。

## 4. 错误分类矩阵与恢复政策

以下均由 Mock HTTP、类型化异常或既有模型校验测试覆盖；HTTP 状态还保留原 `category=HTTP_STATUS, httpStatus=N`，只追加固定 `reason`。所有场景当前均 **不自动重试**，失败仍走安全 502 和现有 FAILED 收尾。

- **400 / 422 → INVALID_REQUEST**：优先修复请求格式、模型参数或代码；无条件重复没有收益。配置/代码修复后可通过原业务请求恢复。
- **401 → AUTHENTICATION**：开发者/运维修复上游凭据；不要使用户重新登录来解决供应商 Key 问题。
- **402 → BALANCE**：账户资源问题，需账户持有人处理；本轮不充值。相同条件下不适合重试。
- **403 → PERMISSION**：检查供应商权限、策略或代理配置；不误判成应用自身授权失败。
- **408 / 504 → UPSTREAM_TIMEOUT**：可能临时，但执行结果不确定；显式恢复可能再次生成/计费，不自动重发。
- **429 → RATE_LIMIT**：可能临时，应等待容量恢复；不能立即循环调用。后续有限策略可考虑 Retry-After 和剩余预算，本轮不等待、不解析、不转发该头。
- **500 → UPSTREAM_SERVER**：可能临时，也可能已处理后失败；保留显式恢复，持续发生时调查上游。
- **502 → UPSTREAM_GATEWAY**：网关故障，不能据此断言模型未执行；暂态恢复候选，不代表已授予重试权限。
- **503 → UPSTREAM_UNAVAILABLE**：过载/不可用，等待后可能恢复；有限重试候选，当前仍一次尝试。
- **302 / 404 / 599 等 → UNEXPECTED_STATUS**：非成功一律拒绝，检查地址、路由、代理或供应商契约；不把未知状态自动认定为暂态。
- **连接拒绝/DNS → CONNECTION_ERROR**：网络临时问题可能恢复，错误主机/端口则需修配置。**连接超时 → CONNECT_TIMEOUT**；明确的请求/Socket 超时 → **TIMEOUT**。只判断类型，不分析不可信 message。网络超时不能证明上游没有执行。
- **其他 IO → IO_ERROR**：结果可能不明确。本机正文超时实际可表现为关闭流后的普通 IOException；类型不足时保持保守分类，不根据耗时/文字猜测成 TIMEOUT。
- **200 空正文、204、JSON null**：受控“空响应”失败；成功状态不等于有效业务结果，不自动生成第二份。
- **非法外层 JSON → RESPONSE_JSON**；评价/报告内层 JSON 非法 → `stage=evaluation_parse/report_parse, category=INVALID_JSON`；原始 Jackson cause 不外传。持续契约错误需开发者修复，偶发异常可在上游恢复后显式重试。
- **外层 model/choices/index/message/role/content 缺失，模型输出字段不完整**：继续按已有固定校验消息拒绝。评价完整性由 LlmEvaluationValidator、报告字段由 Generator 校验；Java 不填补分数、不接受残缺结果。外层结构不能解析成功就当作评价完整。
- **finish_reason 非 stop**：length、content_filter、tool_calls 不自动恢复；先检查截断、输出契约或策略。insufficient_system_resource 可能临时，但本轮仍显式恢复。缺失为 MISSING，未知值为 UNKNOWN；只保留白名单，不输出任意上游文本。

“业务允许再次提交”和“应该立即重复请求”是不同判断。业务测试在首次失败后把模拟上游切换为合法响应，验证配置修复/服务恢复后的路径；不意味着真实 401、402 会自行消失。[DeepSeek 官方错误码](https://api-docs.deepseek.com/quick_start/error_codes/)列出 400/401/402/422/429/500/503；403/408/502/504 另外考虑通用 HTTP/代理路径。

## 5. 时间预算、线程占用与适用边界

### 5.1 当前参数

- DeepSeek：connect 3s、read 60s、max_tokens 4096；DeepSeekProperties 只要求非空、正数/正 Duration，没有业务截止时间或跨配置关系校验。
- Query Embedding：connect 3s、read 20s、一次 Spring AI 尝试。评分 Query 是单文本，不涉及文档批量 Embedding 的多批串行。
- Milvus：connect 5s、每次 RPC 10s，SDK 重试见上节；evaluation profile 的实验覆盖不当作正常 API 默认。
- 前端 `frontend/src/api/httpClient.ts`：Axios 120000ms，无自动重发拦截器。
- EVALUATING/GENERATING：默认 stale 10m，最小允许 1m。阈值是请求驱动接管资格，不是旧任务取消时间；合法 1m 配置仍可能早于完整评分结束。

Spring 6.2.19 `JdkClientHttpRequest.executeInternal/TimeoutHandler` 在 `sendAsync` 后建立一次定时器；到期取消未返回的 future，已收到 headers 时关闭 response body。它不是每收到一个字节就重置的 idle timeout，也不是机械的“3s 建连之后再加 60s 读取”。请求组装/CPU 解析、数据库、线程调度等不受它提供完整 wall-clock 硬保证。依赖证据为本机 `/Users/kun/.m2/repository/org/springframework/spring-web/6.2.19/spring-web-6.2.19-sources.jar`。

### 5.2 三类预算情形

令 Δ 为数据库/连接获取、CPU、调度和响应发送等额外时间。当前没有可证明的 Δ 统一上限。

1. **各外部阶段只有一次、计时器及时生效**：评分外部阶段约 20+10+60=90s，报告约 60s；再加 Δ。这是条件预算，不是性能实测、P95 或整体最大耗时。
2. **加一次完整模型自动重试**：评分约 20+10+60+退避+60=150s+退避+Δ；报告约 120s+退避+Δ。都可能超过 Axios 120s。快速失败后一次成功有收益，但不能只测快速 503 就承诺所有错误都满足预算。
3. **Milvus 持续可重试故障**：75 次执行之间的 74 次退避可累计 207.64s；若每次接近 10s 才返回可重试错误，粗估 957.64s 加本地开销。不是 DEADLINE_EXCEEDED 连续重试的模型，也不是本轮实测或硬上界。评分进入 DeepSeek 之前即可超过 120s，甚至与默认 600s stale 发生重叠。
4. **现有 FAILED 显式恢复**：两次独立 HTTP 请求分别消耗各自的外部阶段；评分重新做 RAG。每个业务 attempt 一次应用级模型尝试，用户重复 m 次可累计 m 次，旧 stale 任务还可能继续；没有无成本或全生命周期次数保证。

Axios 结束等待/页面离开不等于后端停止，也不等于供应商取消。网络超时、网关错误或客户端断开不能证明上游未生成/未计费。数据库 fencing 能保证有效结果唯一，无法撤销供应商副作用。

### 5.3 本机 HTTP 实测

`DeepSeekHttpTimeoutTest` 使用生产 DeepSeekConfiguration 和真实 JDK request factory；只绑定 `127.0.0.1` 随机端口。测试 readTimeout=600ms、connectTimeout=1s；latch 控制上游停滞，40ms 定时发送用于慢正文场景，没有 Thread.sleep。每例都有 future 3s、JUnit 10s 和夹具清理期限，避免测试无限等待。

完整回归实测：

- 响应 headers 停滞：604ms，收到请求 1 次，TIMEOUT。
- 已回 headers、正文停滞：607ms，收到请求 1 次，IO_ERROR。
- 已发 JSON 前缀后停滞：604ms，收到请求 1 次，IO_ERROR。
- JSON 前缀后每 40ms 发空白：605ms，收到请求 1 次，期间成功发送 16 段，IO_ERROR。

测试断言 400～3000ms（允许环境调度误差），并确认：收到请求后调用 future 尚未完成；上游 latch 尚未释放时调用线程已因超时返回；没有第二个请求。聚焦轮对应为 612/606/602/606ms，也全部通过。这证明当前依赖在上述本机 HTTP 场景中关闭读取并释放调用方；不证明真实 WAN/TLS/代理/HTTP2 或供应商停止计算，不是生产容量压测。

## 6. 候选方案与实际选择

**A：单次应用级 HTTP 尝试 + 安全分类（采用）。** 可区分配置、余额、权限、临时故障与无效输出；保留显式恢复。改动集中在一个生产类，无退避等待、无额外模型调用，线程仍占原一次请求时间。收益是诊断和恢复决策准确，不声称提高真实供应商成功率。

**B：错误白名单 + 明确总预算内最多两次尝试（未实施）。** 对快速连接错误、429、502/503 后成功可能减少一次人工操作；代价是最多两次模型调用及重复费用，且同一请求线程持续等待。实现必须同时处理 Retry-After、剩余预算、可取消 HTTP、所有权和业务重试叠加；不能把整个 Workflow 加 Retry 注解，也不能重跑已取得的 RAG。当前整链预算不成立，没有真实故障频率/恢复率/费用证据；仅模拟“失败后成功”不能证明净收益。本轮不新增开关、重试框架或默认两次策略。

**C：统一外部调用截止时间（后续独立设计）。** 单调时钟传递剩余预算有必要，评分须从检索前计时，并由 Embedding/Milvus/DeepSeek 各层真正兑现取消和剩余超时；只包 Future.get(timeout) 而遗留后台任务不算解决。候选 100s 外部预算可为 120s 客户端留余量，但数据库与 SDK 行为尚未整体约束，不能将此候选数字直接上线为硬 SLA。本轮按范围停止该项实现，先由独立 Milvus 治理任务确定 search 总尝试与预算，再决定跨层截止时间及 stale 关系；不扩大前端等待来掩盖问题。

429/503 的 Retry-After 只有在决定重试、合法秒数或 HTTP-date、等待加下一次调用能落入总预算时才适合遵守。它表示不能更早重试，不能把超长等待截短后立即调用。缺失、非法、过期、溢出应有确定策略；本轮没有重试所以不新增解析器。8 个带零/短/超长秒数、未来/过去日期、负数、非法文本、超大整数的 429 用例均快速失败且只调用一次。

指数退避适合连续多次恢复，随机抖动降低大量客户端同时再次请求的峰值；两者都增加同步线程占用。最多一次恢复不需要复杂指数算法。当前不启用自动恢复，所以不引入 sleeper、随机数或额外等待。

## 7. 实际文件变更

相对入场工作区（不是相对 HEAD 的 B4-B+B4-C 混合 diff）：

1. 修改 `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java`：HTTP 白名单 reason；保留原 category/status；最多检查 10 层 cause，优先明确的连接超时/超时/连接错误，再归类 JSON/一般 IO。仅读类型，不输出原始异常，也没有记录新的请求/响应内容。
2. 新增 `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekFailurePolicyTest.java`：42 项确定性故障、调用次数、Retry-After 无等待、显式二次调用恢复和同步线程验证。
3. 新增 `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekHttpTimeoutTest.java`：4 项本机 HTTP 真实传输层超时测试，覆盖 headers、body、部分 JSON 和持续慢正文。
4. 修改 `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java`：原 95 项增加为 112 项，加入缺失的 HTTP 状态、连接/请求超时、模型字段不完整、resource/tool finish reason；原 B4-B 安全断言和全部业务断言保留。
5. 新增 `/Users/kun/Developer/projects/ai-interview-system/docs/verification/B4C_DEEPSEEK_TIMEOUT_RETRY_20261112.md`，即本文。

DeepSeekProperties/Configuration、DeepSeekJudgeProvider、Report Generator、Orchestration/Workflow/Report Service、ExternalServiceException、前端和 stale 参数均已审查而未修改。B4-B 的日志脱敏结果保持；没有新危险 cause、完整 Prompt、答案、Key 或上游原始错误正文日志。

## 8. 测试命令、计数与环境

### 8.1 执行环境

专用临时容器 `ai-interview-b4c-20261112-mysql`，MySQL 8.0.45，端口 `127.0.0.1:13324`，tmpfs 数据目录，时区 `+08:00`。仅有 `ai_interview_test` 和系统库。现有项目容器、正式 local/evaluation 库和数据卷未操作。

`/private/tmp/ai-b4c-20261112/run_maven.py` 为临时执行器：每轮先归档旧 Surefire，再只汇总本轮 XML；清除继承的 RUN_REAL_*、SPRING_*、MILVUS_*、DEEPSEEK_*、JEV_*、AI_JUDGE_*、DB/TEST_DB 和 JVM/Maven 覆盖；只注入隔离测试连接，DEEPSEEK_ENABLED=false，MILVUS_ENABLED 与真实 Smoke 开关 unset。隔离初始化器、双层 opt-in 门禁及 application-test.yaml 均未改。

Mockito 沿用 B4-B 已验证的临时 Java agent：`JAVA_TOOL_OPTIONS=-javaagent:/Users/kun/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar`，只传入子进程。随机临时数据库凭据只在权限 0600 的临时文件和子进程环境中，不写入报告或 Git。

### 8.2 每轮实际结果

以下计数均为 **Tests / Failures / Errors / Skipped**，不要跨轮相加当作独立用例总量。

1. `classification-before`：`sh mvnw -B -ntp -Dtest=DeepSeekFailurePolicyTest test` → **42 / 25 / 0 / 0，退出 1**。生产修复前诊断断言失败，原因见第 3 节。
2. `timeout-before`：`sh mvnw -B -ntp -Dtest=DeepSeekHttpTimeoutTest test` → **4 / 0 / 4 / 0，退出 1**。本机 bind 被沙箱拒绝，四项均 `SocketException: Operation not permitted`，尚未执行 HTTP；不是生产超时失败。随后通过允许的执行环境运行，没有降低测试或绕过项目隔离。
3. `client-focused` → **136 / 0 / 0 / 0，退出 0**，命令：

```sh
sh mvnw -B -ntp -Dtest=DeepSeekFailurePolicyTest,DeepSeekHttpTimeoutTest,DeepSeekLogSafetyTest,DeepSeekEvaluationClientTest,DeepSeekInterviewReportGeneratorTest,DeepSeekConfigurationTest,DeepSeekPropertiesTest test
```

4. `business-regression` → **336 / 0 / 0 / 0，退出 0**，命令：

```sh
sh mvnw -B -ntp -Dtest=FullInterviewBusinessOfflineTest,InterviewWorkflowServiceTest,InterviewReportServiceTest,EvaluationOrchestrationServiceTest,InterviewConcurrentReplayIntegrationTest,InterviewPostClaimFailureIntegrationTest,InterviewReportFinalConsistencyIntegrationTest,InterviewWorkflowTransactionServiceIntegrationTest,InterviewReportTransactionServiceIntegrationTest,InterviewEvaluationClaimConcurrencyIntegrationTest,StaleRecoveryCasIntegrationTest,JudgeConfigurationTest,DeepSeekJudgeProviderTest,DeepSeekInterviewReportGeneratorConfigurationTest,LlmEvaluationValidatorTest,GlobalExceptionHandlerMvcTest test
```

5. `full-regression`：`sh mvnw -B -ntp test` → **1698 / 0 / 0 / 19，退出 0，BUILD SUCCESS**；Maven 用时 38.716s。普通真实 MySQL Mapper/事务测试确实运行。
6. `package`：`sh mvnw -B -ntp -DskipTests package` → **退出 0，BUILD SUCCESS**。不执行测试，T/F/E/S 不适用；执行器记录零 XML 不代表另一次测试通过。
7. `git diff --check` → **退出 0**；新增文件另行检查末尾空白/冲突标记。

19 项跳过逐类为：RealEmbeddingSmokeTest 1、RealMilvusVectorStoreSmokeTest 1、KnowledgePersistenceIntegrationTest 10、RealKnowledgeDocumentProcessingSmokeTest 2、RealKnowledgeDocumentProcessingFailureIntegrationTest 2、RealMilvusSchemaValidationSmokeTest 2、AiSystemBaselineEvaluationTest 1。没有调用真实 DeepSeek、Embedding、Milvus；跳过不是外部集成成功。

### 8.3 关键验收证据

- 故障矩阵每个失败只允许一次 Mock HTTP 请求；类型化网络错误、HTTP 状态、空响应、非法 JSON 和 finish_reason 都受控失败。Retry-After 用例没有等待其值或重复调用。
- 答案 24 类、报告 25 类故障经真实 Controller/认证/Service/MyBatis 和临时 MySQL 验证：首次失败模型调用数 1；显式恢复后为 2；成功重放保持 2。答案只有一条有效评价，报告只有一份且弱项计数不重复。不可重试错误也允许在上游条件修复后显式恢复。
- 单次尝试失败即进入已有 FAILED；Session 不误推进，报告保持 COMPLETED/FAILED，不发布部分报告/弱项。没有引入自动尝试，因此“所有尝试失败”即本次唯一尝试失败。
- `InterviewWorkflowTransactionServiceIntegrationTest` 6 项全部通过，包含 `staleAttemptCannotCommitTraceOrEvaluationAndFailureAuditIsFenced`：旧 attempt 的 trace/评价提交失败，新 attempt 成功提交一次。
- `InterviewReportTransactionServiceIntegrationTest` 9 项全部通过，包含 latch 控制旧/新任务完成顺序和旧任务抛错的四种组合；旧版本不能提交/污染新状态，最终仅一份报告和一次弱项更新。
- J1～B3 相关完整回归：JudgeConfigurationTest 7、FullInterviewBusinessOfflineTest 112、InterviewConcurrentReplayIntegrationTest 18、InterviewPostClaimFailureIntegrationTest 3、InterviewReportFinalConsistencyIntegrationTest 24、InterviewEvaluationClaimConcurrencyIntegrationTest 11、StaleRecoveryCasIntegrationTest 3；上述均 F/E/S=0。
- B4-B 原 `DeepSeekLogSafetyTest` **36/0/0/0**，文件未修改。异常 message/cause/suppressed、完整栈、Logback proxy、最终日志渲染及固定 502 继续通过。扩展业务故障也捕获实际全局错误日志并检查脱敏。
- 聚焦、业务、完整回归日志中检查的 B4-B/B4-C 敏感 sentinel、假 Key 和假答案标记出现次数均为 0。没有用“关闭所有日志”通过测试。

## 9. 产物、未验证边界及交付状态

正式报告及源码路径见第 7 节。打包产物：

- `/Users/kun/Developer/projects/ai-interview-system/target/ai-interview-system-0.0.1-SNAPSHOT.jar`
- `/Users/kun/Developer/projects/ai-interview-system/target/ai-interview-system-0.0.1-SNAPSHOT.jar.original`

逐轮日志、JSON summary 和 Surefire XML 保存于 `/private/tmp/ai-b4c-20261112/`，例如 `full-regression.log`、`full-regression-summary.json`、`full-regression-reports/`；这些是临时证据而非版本管理交付。打包没有覆盖已归档的完整回归证据。

仍未验证或未实施：

1. 真实 DeepSeek 成功率、429 恢复分布、计费/重复生成、WAN/DNS/TLS/代理/HTTP2 故障、真实连接超时秒数及底层重连次数。连接超时分类只有确定性类型注入；本机 HTTP 测试不冒充公网验证。
2. Milvus 75 次重试的运行时故障试验、SDK 内部辅助 RPC/取消、整个评分请求的硬最大耗时。Milvus 重试治理按用户要求保留为独立任务，本轮只有静态复核。
3. 全链统一截止时间、数据库等待上限、业务并发容量及生产 P95。没有新增无界等待或自动重试，但也没有声称现有整链资源风险已消除。
4. 前端 GENERATING 恢复入口保持独立任务；本轮未改前端，因此没有运行前端构建/测试。
5. 所有上游异常全局脱敏、wire-level 日志或 APM 采集不属于本轮保证；保证范围是当前 DeepSeek 边界与已测业务传播链。

回归结束后已核验本轮容器名称、purpose 标签、镜像和 tmpfs，停止并移除该测试容器，确认已不存在；没有删除持久卷或操作其他容器。

本轮代码已编译，聚焦/普通全回归及隔离真实 MySQL 验证通过，已打包；尚未暂存、提交、推送。保留 requestId、attemptId、Session CAS、短事务、Java 计分和追问规则。完成 B4-C 后停止，不自动进入独立 Milvus/前端任务。

## 10. 约 14 分钟源码学习路径

1. **0～2 分钟：模型 HTTP 边界。** 阅读 [DeepSeekEvaluationClient.java:148](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java:148)。理解 onStatus 先于默认错误正文读取，为什么保留状态/固定 reason 而不保留上游 cause。
2. **2～4 分钟：诊断与重试分开。** 阅读同文件 192、208 行，以及 [DeepSeekFailurePolicyTest.java](/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekFailurePolicyTest.java)。解释为什么 HTTP 503 可临时恢复，但不等于现在应该在内部自动重发；为什么需要看完有界异常链再决定类别。
3. **4～6 分钟：单次时间与线程。** 阅读 [DeepSeekConfiguration.java:25](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekConfiguration.java:25)、DeepSeekProperties 和 [DeepSeekHttpTimeoutTest.java](/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekHttpTimeoutTest.java)。对照 Spring source jar 的 TimeoutHandler：future/流关闭怎样结束调用线程等待，为什么缓慢发送仍会到期。
4. **6～8 分钟：整链预算。** 阅读 [EvaluationOrchestrationService.java:104](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/orchestration/EvaluationOrchestrationService.java:104)、EmbeddingConfiguration 的 maxAttempts=1 和 MilvusConfiguration。解释 60s、10s RPC、120s 前端与 600s stale 各控制什么，为何 90s 不是整体上限。
5. **8～11 分钟：显式恢复与旧结果。** 阅读 [InterviewWorkflowService.java:142](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java:142) 和 [FullInterviewBusinessOfflineTest.java:343](/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java:343)。沿同 requestId 原 payload 看 FAILED → 成功 → 重放，对照旧 attempt 事务测试；区分唯一有效结果和唯一上游执行。
6. **11～14 分钟：报告及安全回归。** 阅读 [InterviewReportService.java:106](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java:106)、Report Generator、ReportTransactionService 及旧版本 latch 测试。最后回看 B4-B DeepSeekLogSafetyTest：HTTP 响应、异常链和最终日志是三个都需要保护的边界。
