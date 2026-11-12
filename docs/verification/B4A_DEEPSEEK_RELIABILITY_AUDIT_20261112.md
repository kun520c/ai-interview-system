# B4-A：DeepSeek 外部服务可靠性专项审计

审计日期：2026-11-12（Asia/Shanghai）。审计范围：当前源码、依赖实现、现有离线测试与后续测试设计。本文是审计交付，不代表修复已实施。

## 1. 结论与实际基线

当前单模型调用架构可以保留。优先修复日志边界，再约束整条评分链的时间预算；现在不宜直接给 DeepSeek 加两次或三次自动重试。

主要结论：

1. **已确认服务端日志泄漏通道，未发现同一路径的客户端错误响应体泄漏。** 上游 HTTP 错误 body 进入 Spring 异常 message，原始 cause 被保留，全局处理器打印异常栈；HTTP API 返回固定 502 文案。
2. **评分、反馈和追问建议共用一次 DeepSeek 调用。** 追问答案另行评分；报告是独立 POST 中的一次调用。反馈、追问生成不是独立的额外 HTTP 调用。
3. **项目没有 DeepSeek 应用级自动重试、调用总预算或业务并发上限。** FAILED 允许用户重试，stale 接管只隔离数据库写入，不能撤回旧的供应商调用或费用。
4. **Milvus SDK 隐式重试使“20 + 10 + 60 秒就是评分上限”的判断不成立。** 当前 SDK 默认最多执行 75 次尝试；特定可重试错误下，检索阶段本身即可超过前端 120 秒，甚至与 600 秒 stale 阈值冲突。
5. **报告 stale 恢复存在前端入口缺口。** 后端 POST 可以接管；前端 GENERATING 只轮询 GET，进程崩溃后遗留的 GENERATING 不会自行恢复。

本轮没有确认 P0/P1 缺陷；识别出的必修缺陷与需验证风险按 P2 分层记录，不能据此声称发生过生产事故或真实秘密泄漏。

### 1.1 Git 与环境

| 项目 | 实际观察 |
| --- | --- |
| 仓库 | `/Users/kun/Developer/projects/ai-interview-system` |
| HEAD | `89b77dc082272e38cfbbecb4b57c37edae9ee74d` |
| 提交主题 | `docs: record J1-B3 closeout and source learning guide` |
| 本地远程跟踪引用 `origin/main` | 与 HEAD 相同；本轮未 fetch，未将本地引用冒充远端实时验证 |
| 原有工作区变化 | `mvnw` 仅 `100644 → 100755`；未跟踪 `compose.mac.local.yaml` |
| 本轮源码/测试/配置修改 | 无；只新增本文，未暂存、提交、推送 |
| 本轮运行环境 | Java Temurin 21.0.13、Maven Wrapper 3.9.16、aarch64；Maven 报告宿主 macOS 26.7.1 |
| 关键依赖 | Spring Boot 3.5.16，实际 BOM 对应 Spring Framework 6.2.19、Tomcat 10.1.55；Milvus SDK 2.6.20 |
| 本地服务配置 | `src/main/resources/application-local.yaml` 不存在；本进程的八个模型/检索超时及 stale 环境覆盖变量均未设置 |
| 对外访问 | 只阅读公开官方文档/源码；未调用真实 DeepSeek、Embedding、Milvus 或数据库，未读取真实业务数据 |

时间预算使用受版本管理的默认配置，不声称它就是另一个 IDEA、Docker 或部署进程的运行配置。未读取、打印或复制真实密钥。原有 Compose 文件未修改，其内容不作为本次运行配置来源。

### 1.2 证据状态的含义

- **源码确认**：当前项目源码或当前依赖实现足以确定行为。
- **现有测试验证**：本轮执行的现有单元、Mock HTTP、MockMvc 测试验证了对应已有契约。
- **待故障注入**：行为的出现条件、时间或负载影响尚未用专项实验验证。
- **可选增强**：有收益，但不是当前业务正确性的必需功能。

本轮未新增 sentinel、socket 故障、并发负载测试；这些在第 10 节给出可执行设计。现有 Mock HTTP 测试通过不等于真实网络超时、真实计费或服务可用性验证。

## 2. 源码证据索引

以下行号对应上述 HEAD。后文 E 编号引用本表，方便定位真实类与方法。链接使用本机绝对路径。

| 编号 | 文件、方法与关键行 |
| --- | --- |
| E01 | [InterviewController.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/controller/InterviewController.java:89)：`submitAnswer` 89–102；`getReport` 105–116；`generateReport` 119–130 |
| E02 | [InterviewService.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewService.java:223)：`submitAnswer` 223–301；`handleExistingAnswer` 415–462 |
| E03 | [InterviewWorkflowService.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java:48)：`evaluateAnswer` 48–186；claim/retry/reclaim 188–295；`markEvaluationFailed` 317–332；`applyDecision` 409–465；`handleFollowUp` 512–560 |
| E04 | [EvaluationOrchestrationService.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/orchestration/EvaluationOrchestrationService.java:104)：`evaluate` 104–153；`prepareEvaluation` 155–210；模型元数据及 rawResult 303–317 |
| E05 | [DeepSeekJudgeProvider.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/judge/DeepSeekJudgeProvider.java:16)：`evaluate` 16–19；[JudgeConfiguration.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/judge/JudgeConfiguration.java:8) 8–14：唯一生产 Judge 装配 |
| E06 | [DeepSeekEvaluationClient.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java:51)：`evaluate` 51–75；`completeJson` 78–94；`buildRequest` 97–127；`requestCompletion` 141–174；`validateResponse` 177–259；`parseSuggestion` 262–288；响应 DTO 324–345 |
| E07 | [DeepSeekConfiguration.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekConfiguration.java:25)：`deepSeekRestClient` 25–58；[DeepSeekProperties.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekProperties.java:19) 19–52 |
| E08 | [LlmEvaluationValidator.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidator.java:20)：`validate`、`validateScore` 20–130；[FollowUpPolicy.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpPolicy.java:32)：`decide` 32–109 |
| E09 | [InterviewWorkflowTransactionService.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java:34)：提交 34–90；claim/retry/reclaim/fail 93–146；三类收口事务 148、280、351 起；`persistOwnedEvaluation` 390–422 |
| E10 | [RagTracePersistenceService.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/service/RagTracePersistenceService.java:79)：`persistFailedAttempt` 79–88；`persist` 91 起 |
| E11 | [InterviewReportService.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java:61)：GET 61–94；`generateReportForApi` 96–104；`generateReport` 106–162；`generateClaimedReport` 173–187；已有报告恢复 208–234；FINAL 校验 248–335；`recoverReportFailure` 495–501 |
| E12 | [DeepSeekInterviewReportGenerator.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGenerator.java:57)：`generate` 57–96；解析 99–119；字段校验 122–178 |
| E13 | [InterviewReportTransactionService.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java:26)：`completeReportGeneration` 26–67 |
| E14 | [ExternalServiceException.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/common/exception/ExternalServiceException.java:3) 3–13；[GlobalExceptionHandler.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/common/exception/GlobalExceptionHandler.java:125)：`handleExternalServiceException` 125–134；未知异常 24–28 |
| E15 | [application.yaml](/Users/kun/Developer/projects/ai-interview-system/src/main/resources/application.yaml:30)：Embedding 30–38；Milvus 40–48；recovery 50–53；DeepSeek 55–62 |
| E16 | [StaleRecoveryProperties.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/common/recovery/StaleRecoveryProperties.java:19)：默认值/最小值 19–26；判定与校验 40–92 |
| E17 | [EvaluationRetrievalAdapter.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRetrievalAdapter.java:27) 27–49；[KnowledgeRetrievalService.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalService.java:48)：`retrieve` 48–136 |
| E18 | [EmbeddingConfiguration.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/embedding/EmbeddingConfiguration.java:65)：超时 65–75；模型/单次尝试 RetryTemplate 98–124；常量 29 |
| E19 | [MilvusConfiguration.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusConfiguration.java:20)：`milvusConnectConfig` 20–38、`milvusClient` 41–45；[MilvusVectorStoreClient.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusVectorStoreClient.java:109)：`search` 109 起，SDK 调用 126 |
| E20 | [InterviewAnswerMapper.xml](/Users/kun/Developer/projects/ai-interview-system/src/main/resources/mapper/InterviewAnswerMapper.xml:104)：锁行/claim/retry/reclaim/终态 104–157；[InterviewSessionMapper.xml](/Users/kun/Developer/projects/ai-interview-system/src/main/resources/mapper/InterviewSessionMapper.xml:134)：报告 CAS 134–181 |
| E21 | [httpClient.ts](/Users/kun/Developer/projects/ai-interview-system/frontend/src/api/httpClient.ts:61)：Axios 61–64、拦截器 80–98；[useInterviewSession.ts](/Users/kun/Developer/projects/ai-interview-system/frontend/src/features/interview/composables/useInterviewSession.ts:407)：提交 407–416，外部失败 489–499，EVALUATING 514–517，原请求重试 698–725 |
| E22 | [useInterviewReport.ts](/Users/kun/Developer/projects/ai-interview-system/frontend/src/features/interview/composables/useInterviewReport.ts:31)：3 秒轮询 31、106–114；可生成条件 82–86、310–329；GET 防重入 148–160；失败恢复 249–301；[InterviewReportView.vue](/Users/kun/Developer/projects/ai-interview-system/frontend/src/features/interview/views/InterviewReportView.vue:135) 135–175 |
| E23 | [InterviewReportPromptBuilder.java](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/report/prompt/InterviewReportPromptBuilder.java:113)：`toPromptEvaluation` 113–149；白名单输入 record 194–207 |
| E24 | [pom.xml](/Users/kun/Developer/projects/ai-interview-system/pom.xml:5)：Boot 5–10；依赖 58–135；[application-test.yaml](/Users/kun/Developer/projects/ai-interview-system/src/test/resources/application-test.yaml:12) 12–28：假 Embedding 配置、关闭 Milvus/DeepSeek |

依赖证据 D01：本机 `/Users/kun/.m2/repository/org/springframework/spring-web/6.2.19/spring-web-6.2.19-sources.jar` 中，`StatusHandler.defaultHandler` 74–98、`getErrorMessage` 122–136；`JdkClientHttpRequest.executeInternal` 99–119、`TimeoutHandler` 244–288；`JdkClientHttpRequestFactory` 60–64。已与[官方同版本 StatusHandler](https://raw.githubusercontent.com/spring-projects/spring-framework/v6.2.19/spring-web/src/main/java/org/springframework/web/client/StatusHandler.java)和[JdkClientHttpRequest](https://raw.githubusercontent.com/spring-projects/spring-framework/v6.2.19/spring-web/src/main/java/org/springframework/http/client/JdkClientHttpRequest.java)核对；网页提取可能折叠空行，以上为本地 source jar 行号。

依赖证据 D02：本机 `/Users/kun/.m2/repository/io/milvus/milvus-sdk-java/2.6.20/milvus-sdk-java-2.6.20.jar` 的 `javap -p -c`：`MilvusClientV2.search` 调用 `RpcUtils.retry`；`lambda$search$41` 每次调用 `getRpcStub` 重建 deadline；`RetryConfig$RetryConfigBuilder` 构造默认值；`RpcUtils.retry` 重试循环。没有伪造 SDK 源码行号。[官方同版本 MilvusClientV2](https://raw.githubusercontent.com/milvus-io/milvus-sdk-java/v2.6.20/sdk-core/src/main/java/io/milvus/v2/client/MilvusClientV2.java)也显示这两个入口；RpcUtils 官方页面抓取失败，结论以实际 jar 为准。

## 3. 完整调用链、事务与恢复

### 3.1 主答评分、反馈与追问建议

入口：`POST /api/interviews/{sessionId}/answers` → `InterviewController.submitAnswer` → `InterviewService.submitAnswer`（E01、E02）。

顺序如下：

1. JWT 已认证的用户 ID、会话归属、题目归属、requestId 和答案精确相等性检查。新答经 `InterviewWorkflowTransactionService.submitAnswer` 短事务插入答案、更新题目；幂等成功请求直接重建已有响应。
2. `InterviewWorkflowService.evaluateAnswer` 生成 attemptId，通过短事务 CAS 取得 SUBMITTED、FAILED 或 stale EVALUATING 的处理权。新鲜 EVALUATING 返回 409；CAS 失败重新读取状态，不直接再次调用模型。
3. `EvaluationOrchestrationService.evaluate` 先查已有 AnswerEvaluation；存在则复用。不存在则 `EvaluationContextService.buildContext`、生成 retrievalBatchId、`EvaluationRetrievalAdapter.retrieve`。
4. RAG 串行执行一次 Query Embedding → Milvus search → MySQL 资格过滤和正文回填（E17）。Query 不使用用户答案；此处没有项目层面的 RAG 降级重试。
5. 构造 Evaluation Prompt → `DeepSeekJudgeProvider.evaluate` → `DeepSeekEvaluationClient.evaluate` → `completeJson` → `requestCompletion` → 同步 `RestClient.post().retrieve().body(...)`（E04–E07）。发送同一个配置模型、JSON 格式、非流式请求、关闭 thinking、max_tokens=4096。
6. Java 严格解析整数、`LlmEvaluationValidator.validate`、`EvaluationScoreCalculator.calculate`、等级判定、`FollowUpPolicy.decide`。反馈字段与追问候选来自这次响应，最终分数与工作流动作仍由 Java 决定。
7. `applyDecision` 调用相应收口短事务。`persistOwnedEvaluation` 先锁 Answer 并校验 attemptId，然后在同一个事务内写 RAG trace、AnswerEvaluation、Answer 终态、下一题/追问题目和 Session CAS（E09）。旧 attempt 不得提交。

**当前事实与旧文档不同：** 本基线成功 trace 不是在 DeepSeek 前先提交；成功 trace 与最终工作流事务一起写入。准备评价失败时，`persistFailedAttempt` 以独立短事务检查当前 attempt 后保存已完成检索的 trace。若 Embedding/Milvus 本身抛错，尚未返回 retrievalResult，不会走这个 trace 保存分支（E04:135–151、E10）。

### 3.2 追问答案与反馈展示

主答符合确定性条件时，Java 落库 INITIAL 和唯一 FOLLOW_UP；追问题干来自上一次已校验的建议，**创建追问不再调用一次 DeepSeek**。候选为空/不合法不能绕过校验。

用户提交 FOLLOW_UP 答案时仍走同一 answers POST 和评分链，以 MAIN 与 FOLLOW_UP 的上下文形成综合 FINAL。`FollowUpPolicy.decide` 在 FOLLOW_UP_ANSWER 分支直接走 FINAL，不产生二次追问（E08）。前端显示已落库的反馈/下一题，不另设反馈生成请求。

若 N 道主问题全部完成且没有失败重试，Java 发起的 DeepSeek 调用数为 `N + F + 1`（F 是实际追问数，0 ≤ F ≤ N；最后的 1 是用户生成报告）。这不是供应商计费次数保证，也不包含失败、stale 接管和传输层重发。

### 3.3 报告生成

入口：独立 `POST /api/interviews/{sessionId}/report` → `generateReportForApi` → `generateReport`（E01、E11）。最后一题 FINISH 不在同一个后端请求中自动生成报告。

- 先检查归属、COMPLETED、READY。READY 直接复用；其他可处理状态通过单条 Mapper CAS 取得 GENERATING，成功后用原 version+1 作为本次 attemptVersion，禁止重新加载较新 version 来“补写”。这里没有跨模型调用的大事务。
- 已有报告行时恢复 READY，避免再调用 Generator/再更新弱项。没有报告时只查询并验证每道 MAIN 恰好一个 FINAL，Java 算平均分。
- `DeepSeekInterviewReportGenerator.generate` → `completeJson`：一次 DeepSeek 请求；不调用 Embedding/Milvus。输入白名单包括题目快照、分数、strengths/missingPoints/correction；不直接传原始答案、原始评价 rawResult 或 RAG 日志。但衍生反馈仍可能含用户内容，不能视为公开数据（E23）。
- 解析与长度校验后，`completeReportGeneration` 短事务统一插入 Report、更新 UserWeakness、按本次 version 标记 READY。失败全部回滚（E13）。报告结果等级仍由 Java 计算。
- 捕获异常后 `markReportFailed(sessionId, attemptVersion)`；零行代表已失去所有权，不用新版本重复写。恢复失败作为 suppressed 附加，不掩盖原始异常（E11:495–501）。

### 3.4 失败、重复请求和线程模型

| 情形 | 当前恢复/外部调用行为 |
| --- | --- |
| 同 requestId、同题目、完全相同答案，已 EVALUATED | 重放落库结果；零次新 DeepSeek 调用 |
| 同 requestId 但内容变化/题目变化，或同题使用另一 requestId | 409；不产生新评分调用 |
| SUBMITTED 或 FAILED | CAS 胜者进入一次新评分流程；FAILED 会重新执行 Embedding、Milvus、DeepSeek，未限制累计次数或冷却期 |
| 新鲜 EVALUATING/GENERATING | 409；并发重复请求不获得新的处理权 |
| stale EVALUATING | 请求驱动 reclaim，更换 attemptId；已有评价可重放，否则重新调用 RAG/DeepSeek |
| stale GENERATING | POST 驱动 version CAS；已有报告则协调 READY，否则重新生成 |
| 旧请求在接管后仍执行 | 写入被 attemptId/version 拦住；没有取消旧 HTTP/RPC 的机制，也不能消除供应商可能已发生的费用 |
| FAILED/进程崩溃 | FAILED 需新请求重试；崩溃遗留进行中状态需达到阈值后由新请求接管，没有自动后台调度 |
| 前端 Axios 超时/页面离开 | 未实现与后端任务相连的取消传播；不能据此前端结束等待推断服务器任务已经停止 |

这些入口返回普通同步值，没有 `@Async`、MVC `Callable`/`DeferredResult` 或业务线程池；因此在普通 Servlet/Tomcat 请求工作线程中同步等待完整流程。JDK HttpClient 内部用 `sendAsync`，Spring 随即 `future.get()`，不会释放当前 Tomcat 线程（D01）。外部 HTTP/RPC 没有被包在业务数据库事务中，这是应保留的正确边界。

## 4. 日志敏感信息审计

### 4.1 非成功响应的传播路径

`retrieve()` 的默认错误判断是 HTTP 4xx/5xx，不是所有非 2xx。400/401/402/429 → `HttpClientErrorException`（部分有子类）；500/503 → `HttpServerErrorException`（部分有子类）。默认处理器读 response body，以 status、statusText 和 body 构造 message，并把 body 留在异常对象中。格式化换行不等于脱敏，`formatValue(..., -1, true)` 不设置长度截断（D01）。

随后：

1. E06:168–173 只替换最外层 message，仍将原 `RestClientException` 作为 cause。
2. E14 的 `ExternalServiceException` 原样调用 `super(message, cause)`。
3. 评分/报告 Service 没有自己的 `log.error(..., exception)`；它们执行失败状态处理后继续抛异常，失败收尾也可能添加 suppressed。不能把“Service 不打印”误判为整个路径安全。
4. `GlobalExceptionHandler.handleExternalServiceException` 130 行 `log.error("外部服务异常", e)` 打印完整 cause/suppressed 链。未知异常处理器也打印完整栈。
5. HTTP 响应只返回 `502 + 外部服务暂时不可用`；未输出 e.message、cause、status/body。现有 MockMvc 已验证固定外部错误响应（第 11 节）。

**确定性结论：** 非空上游错误 body 可进入服务端日志。**条件性影响：** 若上游/代理回显 Prompt、答案、鉴权内容等，这些内容也可随错误 body 入日志。不能推断 DeepSeek 当前一定回显 API key，也不能声称已发生真实密钥泄漏。当前代码未主动打印 Authorization，`DeepSeekProperties.toString` 排除 apiKey，但这两点阻止不了响应回显。

### 4.2 同批需要覆盖的两个旁路

- E06:229–231 直接拼接未受信的 `finishReason`。正常枚举值无敏感性，但未知值可任意长并包含 sentinel/控制字符；不能直接作为安全日志字段。
- 评分 `parseSuggestion` 和报告 `parseResponse` 保留 Jackson 原始异常 cause（E06:282–287、E12:114–118）；非法 token、字段类型错误可能使解析器 message 包含响应片段。具体异常格式随 Jackson 配置变化，需 sentinel 复现，不能因关闭 source location 就断言没有内容片段。

持久化 `rawResult` 是既有内部审计契约，本轮不改。该字段不是 HTTP 错误 body，也不应作为监控标签或日志补充。

### 4.3 最小修复取舍

| 方案 | 收益与缺点 | 决策 |
| --- | --- | --- |
| 只改最外层文案/对 message 做关键字替换 | 改动小，但 cause/suppressed 仍泄漏；无法穷举用户答案/Prompt | 不采用 |
| 只让全局 handler 不打栈 | 可暂时降低暴露，但原始异常仍可被其他层打印，且全局去栈会削弱其他故障诊断 | 不作为唯一修复 |
| DeepSeek HTTP 边界使用专用 status handler，错误正文不进入异常 | 在默认处理器构造 body-bearing exception 前拦截；能保留状态、受控类别 | 推荐核心方案；不要改变其他 RestClient 的行为 |
| 捕获后分类映射成安全内部异常 | 兼容网络、反序列化失败；不能把原异常继续放 cause/suppressed 或额外字段后又序列化 | 与上项配合，覆盖解析与 finishReason 旁路 |
| 安全字段日志 | 保留可诊断性；只打印允许的字段并控制基数/长度 | 推荐；全局只记录一次，避免 Client/Service/handler 三重错误日志 |

最小安全字段：provider=`deepseek`、operation=`evaluation/report`、受控 errorCategory、HTTP 数值状态（无则空）、耗时毫秒、providerAttempt 序号、应用生成的关联 ID。可另记录受限的上游 request ID，但必须约束字符集与长度；不得打印整个 headers、URL query、响应 body、任意上游 message。`finish_reason` 只允许已知枚举，其他映射 `UNKNOWN`。

建议使用一个很小的 DeepSeek 专用异常/诊断记录，仍兼容现有 `ExternalServiceException → 502`。可以保留**重新构造的安全异常**及本地调用栈；不要保留含原始内容的 Throwable 引用。内部编程错误仍可保留必要栈，不能把所有 `RuntimeException` 都吞成“供应商失败”。

## 5. HTTP/内容错误分类矩阵

当前共性：E06 对 `RestClientException` 统一包装；没有分类驱动的自动重试；评分侧失败码统一 `EVALUATION_ERROR`（E03:37–38），报告侧只记 FAILED。矩阵中的“可重试”是下一轮设计判断，**不是当前已有功能，也不保证免计费**。

| 错误场景 | 当前处理 | 是否适合自动重试 | 最大风险 | 建议处理 | 离线测试方法 |
| --- | --- | --- | --- | --- | --- |
| DNS/连接被拒绝 | ResourceAccessException 等 → ExternalServiceException → 502/FAILED | 确认尚未提交且属短暂故障时最多 1 次；永久地址错误不重试 | 掩盖配置错误/高频空转 | `CONNECT_FAILURE`，保留类型不留原 message | 注入 ConnectException/UnknownHostException；拒绝连接端口 |
| 连接超时 | 同上，cause 可为 HttpConnectTimeoutException | 有预算时最多 1 次；需区分配置/网络问题 | 时间累积，底层连接尝试叠加 | `CONNECT_TIMEOUT`；超时类别优先按类型，别解析异常文本 | 合成异常测分类；可控网络故障夹具测超时，不能依赖公网黑洞 |
| 读取/响应体超时 | 同上；body 阶段可能表现为读取 IOException/转换异常 | **默认不自动重试** | 上游已生成/已计费而客户端不知，重复生成 | `RESPONSE_TIMEOUT` 或 `IO_AMBIGUOUS`；走现有显式业务重试 | loopback 延迟 headers/截断或延迟 body；用真实 JDK request factory |
| HTTP 400 | 4xx 包装，502 | 否 | 无效请求循环耗费调用/日志 | `INVALID_REQUEST`，修调用参数 | Mock HTTP 400 + sentinel |
| HTTP 401 | 4xx 包装，502 | 否 | 无效凭据导致持续失败 | `AUTHENTICATION`，运维修配置；不能转成用户 JWT 401 | Mock HTTP 401 |
| HTTP 402 | 4xx 包装，502 | 否 | 余额不足重复请求无益 | `BALANCE`，人工处理；本轮不充值 | Mock HTTP 402 |
| HTTP 403 | 4xx 包装，502 | 否 | 权限/策略问题被误作短暂故障 | `PERMISSION`；保留客户端安全 502 | Mock HTTP 403 |
| HTTP 422（补充） | 同其他 4xx | 否 | 参数错误持续重试 | `INVALID_REQUEST` | Mock HTTP 422 |
| HTTP 408 | 4xx 包装，502 | 默认否；无法确认上游未处理 | 服务已执行但响应丢失 | `UPSTREAM_TIMEOUT`，显式恢复 | Mock HTTP 408；验证调用次数为 1 |
| HTTP 429 | 4xx 包装，502；不使用 Retry-After | 条件允许最多 1 次，默认可先关闭 | 立即重试放大限流，用户重试与内部重试相乘 | `RATE_LIMIT`；解析合法 Retry-After，预算不足立即结束 | seconds/date/缺失/非法/超长 Retry-After；调用计数 |
| HTTP 500 | 5xx 包装，502 | 默认不启用；确认短暂、快速失败后才考虑白名单 | 可能在生成后失败，费用不确定 | `UPSTREAM_SERVER`，一次业务失败可人工重试 | Mock 500；无条件二次调用必须失败 |
| HTTP 502 | 5xx 包装，502 | 可列为有限策略候选，但保留重复费用风险 | 网关不知真实处理状态 | `UPSTREAM_GATEWAY`；受总预算控制 | 502→成功、502→502，最多两次 |
| HTTP 503 | 5xx 包装，502 | 短暂过载候选，最多 1 次 | 集体重试再次压垮上游 | `UPSTREAM_UNAVAILABLE`；Retry-After/抖动 | 503→成功及持续503 |
| HTTP 504 | 5xx 包装，502 | 默认不启用 | 网关超时不代表生成未完成 | `UPSTREAM_TIMEOUT`，显式恢复 | Mock 504；验证没有隐式追加请求 |
| HTTP 200/204 空 body | response==null → ExternalServiceException | 默认否 | 成功状态但内容丢失，重复成本 | `EMPTY_RESPONSE` | 空串、204、JSON null |
| 外层 JSON 非法/类型错误 | RestClient 转换异常被包装 | 默认否 | 供应商/代理契约问题与响应内容泄漏 | `INVALID_RESPONSE_JSON` | 外层错误 token/字段类型；捕获 cause/log |
| 内层评分/报告 JSON 非法 | Jackson 异常被包装，保留 cause | 默认否 | 内容片段入日志；自动重试掩盖契约 | `INVALID_CONTENT_JSON` | 分别测试评分与报告的 malformed/type mismatch |
| model/choices/index/message/role/content 不完整 | E06 显式 ExternalServiceException | 默认否 | 不完整数据被误当成功 | `INVALID_RESPONSE_STRUCTURE`；继续严格拒绝 | 现有参数化结构测试扩充 |
| finish_reason 非 stop | 直接拒绝；当前把原值拼进 message | `length`/`content_filter`/`tool_calls` 否；resource 类将来可单独评估 | 截断评分、绕过内容规则、日志注入 | `INCOMPLETE_COMPLETION` + 白名单 reason；不自动提高 tokens | 已知枚举、null、未知长文本 sentinel |
| 分数越界/评分点覆盖/追问约束校验失败 | LlmEvaluationValidator 抛 ExternalServiceException，502/FAILED | 默认否 | 不合法结果被补救重试悄悄替换；增加费用 | `RESULT_VALIDATION`；保留 Java 校验与评分规则 | 现有 47 项 Validator 测试及工作流“不提交”断言 |

DeepSeek 官方列出的 400、401、402、422、429、500、503 原因与上述分类一致；403/408/502/504 同时考虑代理/网关路径，不能把官方未列出的状态说成绝不可能。[DeepSeek 错误码说明](https://api-docs.deepseek.com/quick_start/error_codes/)。

### 5.1 适合当前规模的策略

第一步先分类、记录安全耗时、证明预算，不因“支持恢复”就必须自动重试。业务 FAILED 重试已有价值，读取超时后保持显式恢复是合理默认。

以后如启用自动重试：

- **总尝试数最多 2（第一次 + 1 次恢复）**，默认关闭；初始白名单只考虑明确连接失败、429、快速返回的 502/503。500/408/504/读取超时默认不在名单。
- 只包住一次 DeepSeek HTTP 调用，不包整个 Workflow，不重跑已经取得的 RAG，不重复提交事务，不变更 requestId、attemptId 或报告 attemptVersion。
- 429/503 有合法 Retry-After 时解析秒数或 HTTP-date；这是最早可重试时间。若超出短退避上限或剩余预算，则结束本次请求，**不能截短等待后提前冲击上游**。不假定供应商一定提供这个头。
- 无合法 Retry-After 时，可使用 250–750ms 一次随机退避（仅设计值，需压测）；最多一次恢复不需要复杂指数算法。只有将来增加多次尝试，才有指数退避加上限的必要，当前不推荐增加次数。
- 每次尝试前校验剩余预算/处理中断/仍有所有权（至少在较长等待后）。不足则不发起。仅仅检查预算但第二次仍固定 60 秒，会继续超预算；必须选择“剩余不足整次预算则不试”，或实现经测试的逐请求剩余超时。
- `max_tokens=4096` 是输出限制，不是每用户费用配额；本地幂等不会传给供应商成为去重承诺。即使是 5xx/timeout，也不保证供应商没有生成或计费。
- 每个业务 attempt 最多两次，不等于一个答案最多两次。若用户重试 m 次，最多可能发起 `2m` 次 DeepSeek，还可能有 stale 旧任务。因此要分别观察 businessAttempt、providerAttempt、FAILED 重试和接管，不能混成一个 retryCount。

## 6. 端到端时间预算

### 6.1 已核实的配置及语义

| 层次 | 配置/行为 | 限制与证据 |
| --- | --- | --- |
| DeepSeek | connect 3s，read 60s；max_tokens 4096 | E07、E15；没有总业务 deadline/重试预算 |
| Query Embedding | connect 3s，read 20s；Spring AI RetryTemplate maxAttempts=1 | E18；一次 query 只有一个文本，不涉及多批串行 embedding |
| Milvus | connect 5s，rpcDeadline 10s | E19；5s 主要是建连配置，不是每次 search 固定多加5s；SDK 重试见下节 |
| evaluation 专用 profile | Milvus connect 15s、request 20s | `application-evaluation.yaml:13–14`；不拿实验配置冒充普通 API 默认值 |
| 前端 | Axios 120,000ms | E21；接口调用未设置更长超时，无 axios 自动重试插件/拦截器重发逻辑 |
| 前端报告轮询 | 3s GET；同页面同 session 防重入 | E22；GET 不调用模型，也不触发 stale reclaim |
| 恢复阈值 | EVALUATING 10m，GENERATING 10m；允许配置最小1m | E16；是请求驱动接管阈值，不是旧任务取消计时器 |
| Tomcat | 项目未覆盖线程/连接/排队参数，未启用虚拟线程 | 本机 Boot 3.5.16 metadata 默认 threads.max=200、accept-count=100、max-connections=8192；未测部署进程实际值 |
| MySQL/事务/代理 | 没有项目级统一业务 deadline；Vite proxy 未显式配置请求超时 | DB 等待、连接获取、CPU/GC、反向代理均不能视作0或已被模型超时覆盖 |

**不要机械地把 JDK 实现理解成“先3秒连接，再独立60秒读取”。** 当前 Spring 6.2.19 在 `sendAsync` 后启动 TimeoutHandler，超时可取消响应 future，已收到 headers 时可关闭响应体；这个计时覆盖了大部分建连/响应等待，不是每收到一个字符就重新开始的 idle timeout。读 body 超时的异常形状仍需本地 socket 测试。CPU 解析、调度延迟与调用前后数据库阶段不受这个 timer 提供的硬业务保证（D01）。

### 6.2 SDK 隐式重试：预算中的实质风险

E19 未调用 `withRetry`，因此实际 Milvus 2.6.20 使用 D02 的默认：`maxRetryTimes=75`、`initialBackOffMs=10`、`backOffMultiplier=3`、`maxBackOffMs=3000`、`retryOnRateLimit=true`、`maxRetryTimeoutMs=0`。循环从1到75，意为最多75次 callable 执行，不是首次之外再75次。

`DEADLINE_EXCEEDED`、权限/认证/非法参数等直接失败；UNAVAILABLE 等可重试 RPC 故障以及供应商 RateLimit 分支可重复执行。每次尝试重新创建10秒 deadline。**不能说所有 Milvus 超时都会重试75次，也不能把10秒称为整个 search 的最大时间。**

如果连续75次都是快速可重试错误，仅74次退避之和就是：

`0.01 + 0.03 + 0.09 + 0.27 + 0.81 + 2.43 + 68 × 3 = 207.64 秒`。

若每次在10秒内返回可重试错误，单 search 的粗保守估算为 `75 × 10 + 207.64 = 957.64 秒 + 本地开销`。这是故障模型下的预算估算，不是实测、严格 JVM wall-clock 上界，也不是“连续75次 deadline exceeded”的行为；实际若出现 deadline exceeded 会提前停止。SDK 内部辅助 RPC/处理和调度仍要通过实验核实。

这一因素发生在 DeepSeek 之前。旧评分任务在 Milvus 等待期间被 stale 接管后，仍可能继续走到 DeepSeek，因为 E04 在 retrieval 返回到 judge 前没有再次检查 attempt 所有权；最终写入会被 fencing 拦住，但外部开销已经产生。

### 6.3 至少三个场景的预算

令 Δ 为本地处理、数据库、连接获取、HTTP 返回以及调度等额外耗时；它没有当前项目配置可证明的统一上限。下面的90秒、60秒等只表示指定假设下的外部阶段预算，**不是正常平均耗时或 P95**。

| 场景 | 当前行为/假设 | 评分请求预算 | 报告请求预算 | 与120秒前端/600秒接管的关系 |
| --- | --- | --- | --- | --- |
| 正常成功，各外部阶段一次且 Milvus 不触发 SDK 重试 | Embedding≤20s、search≤10s、DeepSeek≤60s，普通计时器及时生效 | ≤90s + Δ | ≤60s + Δ | 有余量但无硬端到端保证；没有真实耗时测量 |
| DeepSeek 第一次响应超时，用户随后显式恢复 | 当前没有自动重试；首次 FAILED 后原请求可再次提交，评分重新执行 RAG | 每个请求约≤90s+Δ；两次总用户等待约≤180s+两段Δ+人为间隔 | 两次各≤60s+Δ | 两次是不同 HTTP 请求；上游可能两次计费，业务最终只提交一次 |
| 假设给 DeepSeek 直接加1次完整60s自动重试 | 同一请求复用 RAG，退避 b | ≤20+10+60+b+60=150s+b+Δ | ≤120s+b+Δ | 都可能先于服务端完成触发 Axios 超时；不应直接上线这种策略 |
| 多次 DeepSeek 429/503 快速失败 | 当前一次请求只发起一次 Java 调用；m 次显式重试累计m次，无冷却 | 每次重新付出 RAG + 错误返回耗时；累计随m增长 | 每次错误返回耗时；累计随m增长 | 没有自动重试风暴，但人为/脚本重试可形成风暴；不能用无限指数退避解决 |
| Milvus 持续快速限流/可重试故障 | 默认75次，尚未进入 DeepSeek | search 退避约207.64s，另加Embedding/错误耗时 | 不涉及Milvus | 单请求已可超过120s；慢可重试故障接近上述957.64s估算时也可超过600s |

客户端超时后，同答案新鲜 EVALUATING 会返回409而非立即生成第二份结果，这是已有保护。但后台任务仍占线程；达到 stale 阈值后的接管会启动另一外部链。报告使用相同的版本隔离原则。**fencing 解决“谁能提交”，deadline/取消/并发控制解决“资源用多久、用多少”。**

### 6.4 推荐的预算设计边界

先明确并验证 Milvus search 的总尝试策略，再讨论 DeepSeek 恢复。最小优先项是检索操作有限尝试、明确外部阶段总预算；不能全局粗暴改写 SDK 所有写入/补偿行为，从而影响既有 B2 契约。单纯在外层 `Future.get(timeout)` 超时后放任 RPC/HTTP 继续运行，也不算完成资源约束。

可作为下一轮验证目标：总同步业务预算100秒，给120秒客户端留出20秒；报告预算80秒；DeepSeek保留最多60秒单次等待。数字是候选设计而非现状。先保留默认单次模型请求；若评分已经用掉RAG30秒和DeepSeek60秒，剩余10秒不足第二次完整60秒，则不重试。快速429/503后剩余充足，才可能放行唯一一次恢复。

跨配置关系应检查 `业务最大处理预算 + 收口余量 < stale阈值` 且小于客户端等待时间；当前只验证 stale≥1分钟，合法1分钟配置已可能早于一次评分完成。若数据库阶段仍无界，则100秒只能称“应用外部调用预算”，不能宣传为端到端硬 SLA。不得靠把 Axios/stale 一路调大来掩盖无界等待。

## 7. 并发资源与保护方案

### 7.1 现有保护的边界

- 同一答案 CAS、唯一约束、一个当前题目、一个活跃面试共同减少重复评分，但不等于每用户 QPS 限流。一个用户可以对多个历史已完成会话的未成功报告并行发请求；失败可反复重试。
- 没有统一 DeepSeek 并发信号量、按用户配额、调用等待队列长度或排队超时。大量不同用户或不同报告可以同时占用请求线程。前端按钮禁用只约束正常页面交互，不能限制脚本。
- Tomcat 200线程的默认值是整个Web应用的共享资源上限，不是允许200个模型任务的合理容量。慢请求占满后，普通查询/登录也可能受影响。未做负载实验，不报告实际吞吐/耗尽人数。
- DeepSeek的RestClient Bean复用一个JDK HttpClient，连接可复用；不是每请求新建连接池。但没有项目级最大活动请求/连接获取等待策略。JDK HTTP/1.1 `connectionPoolSize` 描述的是空闲keep-alive缓存，不能替代业务并发上限；HTTP/2多路复用也不是配额控制。[JDK 21 网络配置说明](https://docs.oracle.com/en/java/javase/21/core/java-networking.html)。
- JDK存在底层连接恢复行为，项目“无自动重试”应精确表述为**没有应用级 HTTP 状态/内容恢复循环**，不保证线上恰好只传输一次。项目未设置 `enableAllMethodRetry` 等系统参数；不通过开启所有方法重试来治理POST。
- Spring JDK request factory默认可能使用`SimpleAsyncTaskExecutor`处理请求体发布；这些内部线程不等于已做业务线程隔离（D01）。

### 7.2 候选比较

| 方案 | 具体收益 | 风险/维护成本 | 适用条件与本项目结论 |
| --- | --- | --- | --- |
| A 保持现状 | 零改造，现有状态机清晰 | 多个慢请求占共享线程；脚本可反复消耗上游 | 仅受控个人演示可暂用；必须先修日志，不作为公开多用户服务长期方案 |
| B JVM内有界并发 | 单实例可明确限制正在进行的AI业务任务，超额快速拒绝；无需新依赖 | JVM之间不共享；若阻塞等许可又占Tomcat；需要finally释放、拒绝语义与测试 | **优先候选**。非阻塞tryAcquire，零等待队列；初始小容量需离线负载试验，不凭空宣称供应商配额 |
| C 独立业务线程池 | 配合MVC异步可释放Tomcat线程，并单独限定工作线程和队列 | 若在Servlet中submit().get()，仍占Tomcat还多占一个线程；需要取消、上下文、拒绝和关机治理 | 暂缓。只有实测表明B不足且需要独立长任务执行才采用；不用无界队列 |
| D Redis全局限流 | 多实例共享用户/QPS预算 | 新外部依赖、故障策略、原子计数/窗口维护；不等于线程隔离或幂等 | 本轮约束禁止引入；当前体量无必要，推迟到真实多实例需求 |
| E 熔断器 | 持续故障时快速失败，减少无效上游调用 | 半开探测/恢复窗口/错误归因复杂；低流量样本不足可能误熔断 | 暂缓；先有安全分类、失败率/耗时证据，再判断收益；不引新框架 |

推荐B若实施，应先明确作用域：

1. 在DeepSeek client边界共享许可，能约束评分和报告的实际模型并发，但**不能**防止请求提前卡在Embedding/Milvus。
2. 为保护整条同步评分链，宜在确认需要新工作后、claim之前或紧邻claim且已设计失败收尾的位置，给评分/报告共用小规模“业务任务许可”。已有成功重放与普通GET不消耗许可；claim失利、异常、超时都释放。优先claim前获得许可，避免超额拒绝遗留EVALUATING/GENERATING。
3. 若选择整条业务任务许可，RAG期间也占许可，会牺牲少量DeepSeek利用率，但实现简单并保护Tomcat；当前体量比叠加两套队列更合适。不能在finally提前释放却仍让取消失败的后台任务继续跑。
4. 用户访问限流约束“谁、多久发几次”，上游并发控制约束“同时多少个”，线程隔离约束“占哪个执行资源”，幂等约束“是否重复改变业务状态”。四者不可互相冒充。
5. 单用户公平性可以先靠统计识别；确需每用户并发限制时再加有界、可清理的JVM用户许可，不建立无限增长的userId map。报告多会话并发与普通单答案重放需要分别验证。

全局容量拒绝可采用安全503；若将来新增每用户访问限流才考虑429。不能把容量拒绝误标成业务409，也不能为了保护资源改变已验证的attemptId/version收口规则。前端对应错误处理要一起验证。

## 8. 当前可观测性与最小增量

源码全局搜索未发现生产评分/报告的MeterRegistry、Timer、MDC、调用拦截器统计、业务执行线程池或熔断器。pom没有Actuator starter/指标导出配置；传递依赖里即使有Micrometer Observation，也不等于应用已经有可查询的模型指标。

| 要回答的问题 | 当前能否可靠回答 | 最小增量 |
| --- | --- | --- |
| 模型调用成功/失败次数 | 不能；成功落库行不是调用总数，失败/重试/旧attempt可能无结果 | 每次供应商调用一条安全完成事件，success/failure各计一次 |
| HTTP错误分布 | 栈里可能看到状态，但没有稳定聚合字段且有泄漏 | httpStatus + errorCategory；无HTTP状态与HTTP200内容失败要区分 |
| 耗时分布 | 不能；updated_at不是模型时长 | 单调时钟记录elapsedMs，离线汇总p50/p95；不能用墙钟相减跨时钟漂移 |
| 业务阶段失败 | 评分DB只记EVALUATION_ERROR；报告只有FAILED | stage=`retrieval/prompt/provider/validation/commit/recovery`固定枚举 |
| 一个业务请求调用模型几次 | 不能；无providerAttempt、无全链关联 | requestCorrelationId + attemptId/attemptVersion + providerAttempt |
| FAILED重试/stale接管 | 当前状态和trace不足以还原所有历史尝试 | attemptStarted事件含reason=`initial/failed_retry/stale_reclaim`；CAS失利/旧写入拒绝独立计数 |
| HTTP请求与模型调用关联 | answerId/requestId/retrievalBatchId只存在于部分数据流，缺安全日志串联 | 应用生成关联ID；不要直接信任用户requestId成为日志内容 |
| 成本估计 | 不能精确计算；响应DTO忽略usage/id等字段，只保留model/choices | 可选保留可信usage、实际返回model与调用次数；unknown使用null，不能填0 |

近期优先复用SLF4J完成安全结构化字段日志，足以让小体量项目按错误种类和耗时排查问题。成功事件一条、失败事件一条，避免同一次异常重复计数；流程失败与供应商失败是两类事件。attemptId等高基数字段适合受控日志，**不适合metrics标签**。不把用户答案、Prompt、API key、原始响应、完整URL、userId/requestId作为指标标签。

若后续确需持续看仪表盘，再引入Micrometer Timer/Counter和必要的Actuator暴露，设置固定provider/operation/category/status标签及合适桶；Actuator端点要受保护。不要为了五个计数器先引入新的监控基础设施。

成本统计是可选增强：模型返回usage可用于有响应调用的估算；超时/断连调用是否收费需要供应商账单核对。`max_tokens`不是实际token数，不能按字符数伪造token用量。报告当前保存配置model，评分保存响应model（E12:94、E04:303–305），因此费用估算应明确记录来源并保持未知项。

## 9. 问题清单与排序

| ID | 问题 | 证据 | 风险等级 | 是否需要修复/证据状态 | 推荐方案 | 验证方式 |
| --- | --- | --- | --- | --- | --- | --- |
| B4A-01 | 上游HTTP错误body经cause进入日志；解析异常与未知finishReason有类似旁路 | E06、E12、E14、D01 | P2 | 必修；HTTP传播链与拼接为源码确认，真实敏感内容出现未验证 | DeepSeek边界安全分类异常+白名单字段；客户端502保持 | H01–H04 sentinel全栈捕获；现有HTTP安全响应回归 |
| B4A-02 | 缺安全、稳定的错误分类，无法据此诊断/选择恢复；FAILED无累计次数/冷却 | E03:37、E06:168、E11:495、E20 | P2 | 分类必修；重试滥用影响为静态风险待验证 | 小型内部类别；先不新增自动重试；必要时轻量冷却/并发拒绝 | H05、H06；分类与调用计数、原请求重放 |
| B4A-03 | 无整链预算，SDK默认75次重试；合法stale配置可短于任务预算 | E15–E19、D02、E04:135–169 | P2 | 机制已由源码/字节码确认，持续故障耗时待注入；增加DeepSeek重试前必须处理 | 明确search总尝试边界、阶段预算和配置关系；不粗暴扩大前端等待 | H07–H09；SDK替身+loopback超时；旧attempt迟到写入回归 |
| B4A-04 | 前端GENERATING没有POST恢复入口，崩溃遗留报告可一直停在生成中 | E11:61–94/128–133；E22 | P2 | 功能缺口源码确认；崩溃端到端复现未执行 | 用户显式“尝试恢复”POST，由后端判断fresh/stale；GET仍无副作用 | H10；fresh409、stale单赢家、READY复用、GET不生成 |
| B4A-05 | 不同答案/会话无业务并发上限，可耗尽共享线程与供应商配额 | E01、E03、E07、E11；无相关生产控制类 | P2 | 静态风险；公开多用户使用前应修，未测实际容量 | JVM非阻塞有界任务许可；暂缓线程池/Redis/熔断 | H11；独立用户/多个历史报告、拒绝和finally释放 |
| B4A-06 | 无调用级成功/失败/时延/阶段/关联记录 | E04、E06、E11、E24及生产日志搜索 | P2（运维债务） | 缺口源码确认；与分类同批做基础日志 | 安全完成事件+attempt事件，先不用Actuator | H12；次数、关联、基数与敏感字段断言 |
| B4A-07 | 无可信usage留存，不能估算全部调用成本 | E06响应DTO 324–345、E12:94 | 可选增强 | 非正确性缺陷；暂缓，真实成本需求出现再做 | usage可空、区分未知费用，不加DB表 | 合成usage响应；缺失/不可信数据为unknown |

P0：未发现。P1：本轮未确认。B4A-03/B4A-05若故障注入证实普通负载即可持续影响核心API，可再依据影响面调整优先级；不能用推测的流量把所有风险提升为P1。

## 10. 后续离线故障注入方案（本轮未实施）

统一约束：使用纯合成内容，例如 `B4A_BODY_SENTINEL_7F2A`、`B4A_FAKE_KEY_7F2A`、`B4A_FAKE_PROMPT_7F2A`、`B4A_FAKE_ANSWER_7F2A`。MockRestServiceServer在内存接管请求，socket测试只绑定loopback随机端口；禁止真实API、真实秘钥、真实用户内容。测试fixture失败时也不要dump完整请求。

| 编号 | 设计与断言 |
| --- | --- |
| H01 | 对400/401/402/403/408/429/500/502/503/504参数化返回带sentinel的JSON/文本body。通过真实Client及真实GlobalExceptionHandler触发捕获。修复前应能在渲染栈中发现sentinel；修复后message、cause、suppressed、最终HTTP响应、日志均不得包含。另断言状态/category/elapsed/关联ID保留。 |
| H02 | Logback ListAppender捕获`ILoggingEvent`，同时检查formattedMessage和`ThrowableProxyUtil.asString(event.getThrowableProxy())`；只检查formattedMessage会漏掉cause。可辅以OutputCapture覆盖最终console渲染。测试后detach appender，避免跨测试污染。 |
| H03 | 200外层非法JSON、内层评分非法JSON、报告非法JSON/类型错误分别嵌入sentinel；未知finish_reason中嵌入sentinel与换行。验证原文不被拼入异常或日志，已知reason仅用白名单值。 |
| H04 | 请求带合成key/Prompt/答案，Mock上游分别“不回显”和“回显到错误body”；两种情况均不得在日志出现。证明风险来自错误传播，不能只测属性toString。测试api响应仍为固定502；增加suppressed故障链用例。 |
| H05 | 类型化注入ConnectException、HttpConnectTimeoutException、HttpTimeoutException、读取IOException、Jackson错误，验证类别、对外状态、业务失败收尾；非供应商编程异常不可被广泛转换成502。 |
| H06 | 若将来开启有限重试：429/503→成功、持续错误、不可重试400/401/402/403/408/504、读取超时；断言最多2次、禁重试场景1次、没有重复RAG。Retry-After覆盖秒、日期、负数、过期、非法和超过剩余预算。fake clock/可注入sleeper验证退避，不用分钟级sleep。 |
| H07 | 本地HTTP server分别延迟headers、先回headers再卡住body、分段慢发/截断body；通过**生产的DeepSeekConfiguration**构造JDK factory，使用短测试超时与latch协调，AssertTimeout作兜底。记录允许调度误差；MockRestServiceServer本身不能验证真实JDK网络计时。 |
| H08 | 对实际SDK RpcUtils/替身RPC重复返回UNAVAILABLE、RateLimit和DEADLINE_EXCEEDED，核对75次与不重试分支；小测试backoff避免等待207秒。再验证提议的有限search策略；不修改Milvus collection、不调用真实Milvus。不能只mock掉整个VectorStoreClient后声称已验证SDK。 |
| H09 | A占有任务后阻塞，调整测试时间/数据库updated_at使B接管；释放A与B，验证旧attempt不得提交trace/evaluation/报告/弱项或污染FAILED。若新增deadline，确认到期后不会启动下一次DeepSeek；不能声称取消必然撤回供应商费用。真实MyBatis验证只用专用测试schema，作为后续完整回归的一部分。 |
| H10 | 前端fake timer：GET持续GENERATING不会自动POST；显式恢复fresh返回409继续轮询，stale恢复成功展示READY；多标签页只一个后端CAS胜者。模拟进程崩溃后的GENERATING快照和页面重进。 |
| H11 | 用latch阻塞N个合成任务，第N+1个快速拒绝，观测最大inFlight≤N、无无界队列。覆盖评分/报告共享容量、同请求重放、失败/中断/finally释放、不同用户与单用户多个报告；普通GET保持可处理。不得用真实模型压测。 |
| H12 | 每个providerAttempt恰好一个完成事件；FAILED retry/stale reclaim/失去提交权可关联；没有跨线程MDC残留或请求ID伪造注入。若引入指标，用SimpleMeterRegistry验证固定标签，不放userId/answerId/正文。 |

## 11. 本轮实际验证结果

只运行既有离线测试，没有新增测试源码或修改断言。未启动全应用/真实服务。Maven使用`-o`，避免测试过程中下载依赖；JUnit/Mock上下文自行使用本地假配置。

首轮命令：`./mvnw -o -B -ntp -Dtest=DeepSeekPropertiesTest,DeepSeekConfigurationTest,DeepSeekEvaluationClientTest,DeepSeekJudgeProviderTest,DeepSeekInterviewReportGeneratorTest,DeepSeekInterviewReportGeneratorConfigurationTest,EvaluationOrchestrationServiceTest,InterviewWorkflowServiceTest,InterviewReportServiceTest,GlobalExceptionHandlerMvcTest,LlmEvaluationValidatorTest,StaleRecoveryPropertiesTest test`。

子进程移除所有继承的`RUN_REAL_*`、`MILVUS_ENABLED`、`SPRING_PROFILES_ACTIVE`，设置`DEEPSEEK_ENABLED=false`。测试中的局部ApplicationContextRunner可显式打开假配置Bean，但未发真实请求；没有把开关写入配置文件。

| 执行 | Tests | Failures | Errors | Skipped | 结果与解释 |
| --- | --- | --- | --- | --- | --- |
| 首轮12类定向测试 | 201 | 0 | 85 | 0 | BUILD FAILURE；116项成功，85项因Mockito inline Byte Buddy不能自挂载JVM而未执行到业务断言 |
| 受影响3类重跑 | 85 | 0 | 0 | 0 | BUILD SUCCESS；命令行预加载已有mockito-core代理，未改pom/测试/权限配置 |

重跑参数：`-DargLine=-javaagent:/Users/kun/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar`，`-Dtest=EvaluationOrchestrationServiceTest,InterviewWorkflowServiceTest,InterviewReportServiceTest`，其余Maven参数和环境清理同首轮。根因证据为 `MockitoInitializationException: Could not initialize inline Byte Buddy mock maker` / `Could not self-attach to current VM using external process`，不是本次代码回归。

本轮选定的201个不同用例最终均通过，分布如下：

| 测试类 | 用例数 | 已验证内容边界 |
| --- | --- | --- |
| DeepSeekPropertiesTest | 19 | 参数约束及属性安全性 |
| DeepSeekConfigurationTest | 2 | 条件Bean及超时属性绑定；不是网络计时测试 |
| DeepSeekEvaluationClientTest | 20 | Mock HTTP契约、500包装、空body、结构、finish_reason、内层JSON等 |
| DeepSeekJudgeProviderTest | 1 | Provider委派/元数据 |
| DeepSeekInterviewReportGeneratorTest | 13 | Mock HTTP报告输入/输出及拒绝非法数据 |
| DeepSeekInterviewReportGeneratorConfigurationTest | 2 | 报告Bean条件 |
| EvaluationOrchestrationServiceTest | 20 | Mock编排、准备结果与失败trace等 |
| InterviewWorkflowServiceTest | 41 | Mock状态竞争、失败恢复、旧结果复用等 |
| InterviewReportServiceTest | 24 | Mock报告状态与恢复、FINAL校验等 |
| GlobalExceptionHandlerMvcTest | 8 | MockMvc固定HTTP错误映射，含外部502脱敏响应 |
| LlmEvaluationValidatorTest | 47 | 不可信模型结果严格业务校验 |
| StaleRecoveryPropertiesTest | 4 | 阈值约束和stale判定 |

临时日志（非正式交付，不纳入Git）：

- `/private/var/folders/xx/vxww8vxd1hz_f8szvp9th6j40000gn/T/b4a-audit-dnu77b5b/focused-tests.log`
- `/private/var/folders/xx/vxww8vxd1hz_f8szvp9th6j40000gn/T/b4a-audit-dnu77b5b/mockito-rerun.log`

Surefire报告位于 `/Users/kun/Developer/projects/ai-interview-system/target/surefire-reports/`；后续运行会覆盖相应报告。**完整mvn test、前端测试、真实MySQL事务回归、sentinel专项复现、socket超时、SDK故障与负载测试均未执行。** 未用历史全回归数量填充本轮结果；定向测试的Skipped=0仅因为没有选中guarded real tests。

## 12. 后续最小开发批次

本节仅是路线，需下一轮明确实施指令。所有批次保持DeepSeek单模型、主问题固定题库、Java评分/追问决策、FINAL规则、requestId幂等及attemptId/version隔离，不修改数据库结构。

### 公共回归门槛 G

每个实际修改Java的批次：定向测试通过后运行普通完整`./mvnw -B -ntp test`，保留所有既有检查。清除六个既有`RUN_REAL_*`及其他继承的同前缀开关，保持`MILVUS_ENABLED` unset、`DEEPSEEK_ENABLED=false`；必要时沿用命令行Mockito javaagent。真实MyBatis测试只连接`ai_interview_test`，先验证Maven子进程的TEST_DB或DB凭据存在且不输出值，EnvironmentIsolationInitializer不得绕过。逐次记录实际Tests/Failures/Errors/Skipped，guarded real tests保持跳过，不把跳过当成功。

涉及前端时增加 `npm run test:unit`、`npm run typecheck`、`npm run lint`、`npm run build`（工作目录为仓库的frontend）。完整回归失败须区分环境故障与代码失败；没有真实服务条件时记录阻塞，不能降安全校验。以下每批的“G”即该完整回归要求。

### B4-B：安全错误边界与最小诊断

- **业务问题**：B4A-01，错误响应/解析片段可进入服务端日志。
- **最小文件范围**：修改DeepSeekConfiguration的专用status handler、DeepSeekEvaluationClient的网络/解析/finishReason映射、DeepSeekInterviewReportGenerator的解析异常；必要时新增一个小型安全DeepSeek异常/诊断record。GlobalExceptionHandler只在需要输出受控字段时定向改动，保持公共502文案。相应扩展现有Client/Generator/Handler测试。
- **取舍**：边界不保留危险cause；保留类型、HTTP状态、耗时与关联字段，不靠正则删除某几个key，不全局禁栈。暂不加重试、线程池、Redis。
- **回归风险**：错误映射被误改为用户401/403；报告共享Client遗漏；其他ExternalServiceException受牵连；重复日志。
- **定向测试**：H01–H05与既有DeepSeek/Handler/Report测试；通过后G。
- **可回滚条件**：若破坏原HTTP契约或诊断字段，停止上线并回退本批；可回到既有安全对外响应，同时临时关闭DeepSeek入口。不能以重新打印原始响应体作为排障回滚方案。
- **学习目标**：RestClient的status handler、异常cause链、日志渲染与对外DTO边界为何是不同层次。

### B4-C1：错误分类与时间预算，重试先保守

- **业务问题**：B4A-02/03；失败原因混在一起、SDK隐式重试与客户端/stale预算不匹配。
- **最小文件范围**：复用B4-B安全错误类别；DeepSeekProperties/Configuration/Client增加必要预算约束；Workflow/Report入口只携带预算/安全上下文，不变更事务CAS；针对Milvus search评估有限SDK策略，必要时定向修改MilvusConfiguration或search适配层及其测试。不得顺带重写向量写入和补偿语义。
- **取舍**：先验证D02并约束search，默认DeepSeek仍只一次；读取超时不自动重试。若有限恢复收益被离线测试证明，再在本批后半的小增量中打开最多一次快速故障恢复。不能新增“重跑整个业务方法”的Retry注解。
- **回归风险**：SDK共用客户端的策略意外影响insert/delete；误把预算耗尽当校验错误；旧attempt继续调用/写入；提前timeout却没有释放真实资源。
- **定向测试**：H05–H09，现有Embedding/Milvus契约、Workflow/Report及真实测试schema的fencing测试；通过后G。现有`InterviewWorkflowTransactionServiceIntegrationTest.staleAttemptCannotCommitTraceOrEvaluationAndFailureAuditIsFenced`（254行）、`InterviewReportTransactionServiceIntegrationTest.oldReportVersionCannotCommitOrFailReplacement`（307行）必须保留。
- **可回滚条件**：先关闭自动恢复回到maxAttempts=1；若检索策略改变既有成功结果或补偿契约，单独回退SDK策略部分。预算治理不得靠扩大stale阈值掩盖失败。
- **学习目标**：连接超时、一次RPC deadline、整个业务deadline、恢复阈值和最终提交权分别约束什么。

### B4-C2：补齐报告过期恢复的页面入口（独立小批）

- **业务问题**：B4A-04，崩溃遗留GENERATING无法从现有页面触发后端恢复。
- **最小文件范围**：useInterviewReport.ts、InterviewReportView.vue、reportCopy及对应前端测试；复用现有POST，优先不改API/数据库。增加用户主动“尝试恢复”动作，后端负责fresh409或stale接管，不让浏览器时间自行决定所有权。
- **取舍**：GET轮询始终只读；不把3秒轮询改成3秒自动POST。可以显示受控恢复按钮并禁重复点击；不需要暴露内部version/attemptId。
- **回归风险**：页面多发POST、fresh状态连续报错、READY重复生成、跨session旧响应覆盖当前页面。
- **定向测试**：H10，原ReportView/身份与页面状态隔离测试；完整前端门槛与G中的后端报告契约回归。
- **可回滚条件**：若引发自动重复生成，移除新自动行为/回退页面入口；后端stale CAS不变。旧界面缺口仍须明确记录，不能宣称恢复闭环完成。
- **学习目标**：后端有恢复能力不等于用户能触达；轮询查询、显式重试和幂等是不同交互。

### B4-D1：公开多用户使用前的JVM有界任务许可

- **业务问题**：B4A-05，独立任务共享Tomcat资源，无业务容量保护。
- **最小文件范围**：一个JVM许可组件及少量配置；Workflow/Report申请新attempt处定向包裹；必要的安全容量异常映射和前端错误处理。若仍只做个人演示可推迟，但公开使用前应完成。
- **取舍**：先零等待队列的tryAcquire和finally释放；先保护整条任务，后续根据数据再分用户公平性。暂不引执行线程池、Redis、熔断框架。
- **回归风险**：许可泄漏、正常重放被拒绝、拒绝后残留进行中状态、业务异常与容量错误混淆。
- **定向测试**：H11及CAS/重放回归；通过后G和前端受影响测试。
- **可回滚条件**：许可泄漏或合法单用户流程被错误拒绝时停止开放流量、回退本批；只能在受控流量下临时关闭并发控制，不以无限排队代替。
- **学习目标**：Semaphore与线程池的差别，快速拒绝为何能保护请求线程，permit生命周期如何覆盖异常路径。

### B4-D2：基础调用事件；指标平台与成本统计暂缓

- **业务问题**：B4A-06，无法回答失败率、阶段、耗时与一次业务调用多少次模型。
- **最小文件范围**：在已有DeepSeek边界记录一次安全完成事件；Workflow/Report记录attempt起因/失效；如需跨HTTP关联，新增一个小型关联ID过滤器或显式上下文。无需改表。可以在B4-B完成基础安全字段后独立补齐成功事件。
- **取舍**：先日志可统计，后续真实监控需求再引Micrometer/Actuator；成本usage留作B4A-07可选项，不强制实施。
- **回归风险**：重复计数、高基数、MDC串请求、把响应或用户内容写入日志。
- **定向测试**：H12和H01–H04；通过后G。
- **可回滚条件**：出现敏感字段或日志量异常，关闭新增事件输出并回退事件层；保留B4-B安全异常边界。
- **学习目标**：业务attempt与供应商attempt、事件与指标标签、unknown用量与零用量的区别。

**不列入近期实施清单**：Redis全局限流、独立业务线程池、熔断器、新模型、MQ、新框架、tokenizer、完整计费平台。没有证据表明这些是当前可靠性修复的前提。

## 13. 10～15分钟源码学习指南

建议总用时14分钟，只沿调用点阅读，不通读大文件。

1. **0～2分钟：HTTP入口与幂等。** E01:89 → E02:223、415。回答：重复提交为什么先检查requestId/答案内容？EVALUATED何时能零次模型调用？
2. **2～4分钟：处理权和事务。** E03:142、188、259 → E09:390 → E20。回答：attemptId只防止什么？为什么阻止旧写入仍不能避免旧模型费用？
3. **4～6分钟：评分、反馈、追问是一份结果。** E04:129–210 → E05:16 → E08:32。找出RAG、模型、校验、算分、追问决策顺序；确认当前成功trace与工作流一起提交。
4. **6～8分钟：日志泄漏链。** E06:145–173 → E14的异常构造与130行；再读D01 `StatusHandler.getErrorMessage`。区分顶层message、cause、客户端JSON与服务端日志。检查只改中文文案为什么无效。
5. **8～10分钟：时间预算。** E07:30–44、E15:37–62、E18:120、D02。解释为什么60秒不是整个评分流程上限，为什么SDK内部重试不能漏算，为什么加一次模型恢复会越过120秒。
6. **10～12分钟：报告闭环。** E11:106–187、495 → E12:68 → E13:26。确认FINAL、Java平均分、Report+Weakness+READY原子性，以及版本CAS失败后的行为。
7. **12～14分钟：前端恢复与测试证据。** E21:698、E22:82/128/310。找出答案有显式原请求重试、报告GENERATING却只GET的差别；最后看H01/H07/H09，分别说明Mock HTTP能证明什么、不能证明什么。

## 14. 交付状态

正式新增文件：`/Users/kun/Developer/projects/ai-interview-system/docs/verification/B4A_DEEPSEEK_RELIABILITY_AUDIT_20261112.md`。

已完成源码/依赖审计、既有201项离线用例的分轮验证、故障注入设计与后续小批次方案。未修改生产代码、测试源码、业务规则、数据库结构或配置；`mvnw`权限和未跟踪Compose文件保留。未暂存、提交或推送。未执行真实上游调用，未声称完成真实外部服务验证。下一步仅建议优先进入B4-B；本轮到审计报告结束，等待下一轮实施指令。
