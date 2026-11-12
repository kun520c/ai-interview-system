# C3：B4-A～B4-C 累计审查与阶段收口

日期：2026-11-12（Asia/Shanghai）。仓库：`/Users/kun/Developer/projects/ai-interview-system`。

## 1. 收口结论

**B4-B/B4-C 累计代码通过本轮审查与回归，可进入人工提交授权环节。未发现新增 P0/P1 或阻断提交的 P2 代码缺陷。** 日志安全边界、固定安全 502、单次应用级 HTTP 尝试、Java 评分和报告流程、FAILED/stale/attemptId/version CAS 均保持。

本轮实际结果：聚焦 **136/0/0/0**，业务回归 **336/0/0/0**，完整回归 **1698/0/0/19**（依次为 Tests/Failures/Errors/Skipped），三轮退出码均为 **0**。完整回归包含隔离真实 MySQL/MyBatis 事务及并发测试。另完成打包、日志 sentinel、差异与敏感信息检查、三次提交的补丁顺序验证。

有一项 **P2 验收文档口径澄清**：不能把 DeepSeek 专用 sentinel 的零命中泛化成“所有测试日志均无敏感标记”。业务和完整回归各有 3 次 `PRIVATE_MODEL_OUTPUT`，来自基线已有的内部编程异常测试；不是本次 DeepSeek 脱敏失效，也不代表真实秘密泄漏。详见第 5 节。本报告补充该证据，不改写此前历史报告。

本轮不新增功能，不修改生产/测试源码、配置或数据库结构。仅在仓库新增本收口文档；原有 12 个未提交文件的内容和权限均保留。没有执行 `git add/commit/push/reset/clean/stash`，没有 fetch。真实 DeepSeek、Embedding、Milvus Smoke 未执行；普通回归成功不能代表真实供应商集成通过。

## 2. 前置核验与完整变更清单

### 2.1 Git 和环境

- 入场及收口 HEAD：`89b77dc082272e38cfbbecb4b57c37edae9ee74d`；分支 `main`。
- 提交主题：`docs: record J1-B3 closeout and source learning guide`。
- 本地 `origin/main` 同值；未连接远端核实实时状态。
- 暂存区为空，`.git/index` 字节摘要保持入场值；所有 B4-A～B4-C 内容仍在工作区，尚未提交或推送。
- 已读取 `/Users/kun/Developer/projects/ai-interview-system/AGENTS.md` 和下列三份真实存在的报告。涉及历史描述与现行源码不同的地方，以现行源码为准，例如成功 RAG trace 当前在受 attempt 保护的工作流收口事务中提交。
- 实测 macOS 26.7.1 ARM64、Temurin 21.0.13、Maven Wrapper 3.9.16；与环境说明中的 macOS 26.6 有差异，使用实际运行值。
- `src/main/resources/application-local.yaml` 不存在，其路径仍被 Git 忽略；未为测试创建真实 local 配置。

### 2.2 入场 5 个已跟踪修改

1. `/Users/kun/Developer/projects/ai-interview-system/mvnw`：仅 `100644 → 100755`，无正文变动；原有用户修改，排除出 B4 提交。
2. `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java`：B4-B 的 HTTP/JSON/finish_reason 安全边界与 B4-C 分类共同修改。
3. `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGenerator.java`：B4-B 报告 JSON 异常去除危险 cause。
4. `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClientTest.java`：B4-B 更新两处 cause 契约断言及测试名称。
5. `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java`：B4-B 日志捕获/故障传播和 B4-C 追加故障类型共同修改。

### 2.3 入场 7 个未跟踪文件

1. `/Users/kun/Developer/projects/ai-interview-system/compose.mac.local.yaml`：原有本地文件，保留并排除出 B4 提交。
2. `/Users/kun/Developer/projects/ai-interview-system/docs/verification/B4A_DEEPSEEK_RELIABILITY_AUDIT_20261112.md`：B4-A 审计设计及历史验证。
3. `/Users/kun/Developer/projects/ai-interview-system/docs/verification/B4B_DEEPSEEK_LOG_SAFETY_20261112.md`：B4-B 修复和验收记录。
4. `/Users/kun/Developer/projects/ai-interview-system/docs/verification/B4C_DEEPSEEK_TIMEOUT_RETRY_20261112.md`：B4-C 分类、超时、单次尝试政策和验收记录。
5. `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekLogSafetyTest.java`：B4-B 的 36 项日志安全测试。
6. `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekFailurePolicyTest.java`：B4-C 的 42 项分类及尝试次数测试。
7. `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekHttpTimeoutTest.java`：B4-C 的 4 项本机 HTTP 超时测试。

本轮新增第 8 个未跟踪文件：`/Users/kun/Developer/projects/ai-interview-system/docs/verification/C3_B4A_B4C_CLOSEOUT_20261112.md`。遵循仓库既有 `docs/verification/` 验收文档约定，不另生成内容重复的独立报告。

## 3. 累计代码审查

### 3.1 异常链脱敏与 B4-C 兼容性

[DeepSeekEvaluationClient.java:148](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java:148) 在每次请求的 `retrieve().onStatus(...)` 中拦截非 2xx，读取数字状态后构造无 cause 的 `ExternalServiceException`。不读取错误正文、statusText 或诊断 headers；专用测试通过会在正文 `read()`/状态文本读取时失败的夹具验证这一点。

B4-C 追加的 `safeHttpFailureReason` 只返回固定白名单字符串。`safeFailureCategory` 仅读异常类型；没有输出异常 message、类名、headers、Prompt、答案或密钥，没有新增 Logger，也没有挂回原始 cause/suppressed。

评价 JSON 和报告 JSON 的解析异常分别在 [Client:332](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java:332)、[Report Generator:99](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGenerator.java:99) 转换为安全阶段信息。未知 finish_reason 为 UNKNOWN，缺失为 MISSING，合法失败原因只保留白名单。正常 stop 分支不变。

保证范围是 DeepSeek 边界及已测业务传播链。上层数据库收尾仍可附加独立 suppressed，其他模块及任意编程异常没有因此获得全局脱敏保证。

### 3.2 嵌套超时识别

[Client:208](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java:208) 最多检查含外层在内的 10 个 cause 节点。优先级为 CONNECT_TIMEOUT → TIMEOUT → CONNECTION_ERROR → RESPONSE_JSON → IO_ERROR/HTTP_CLIENT_ERROR。

- Jackson 包装 SocketTimeoutException 时，内层明确超时不会被外层 JSON 类型遮蔽。
- HttpTimeoutException 包装 HttpConnectTimeoutException 时，保留更具体的 CONNECT_TIMEOUT。
- 没有具体超时类型的普通 IOException 不依据“timeout”文字或测量耗时猜测为 TIMEOUT。当前生产 JDK factory 关闭正文流的场景得到 IO_ERROR，与本轮实测一致。
- cause 深度限制使环形/超深链不会无限遍历；深于检查范围的类型可能降级，这是诊断边界，不改变失败/502/不重试。现有新增测试没有单独参数化覆盖第 10/11 层或环形链，不能声称这些边界均经过运行验证。

没有发现本次优先级修改引入已证实的错误分类。仍不把分类字符串当作重试授权。

### 3.3 HTTP 安全 502 契约

[GlobalExceptionHandler.java:125](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/common/exception/GlobalExceptionHandler.java:125) 和 `ExternalServiceException` 未修改。外部错误保持 HTTP 502、code=502、message=`外部服务暂时不可用`、data=null；上游 401/403 不变成用户 JWT 鉴权错误，429 不变成应用自身限流响应。

日志仍记录一次 ERROR 和安全本地调用栈。测试同时要求有诊断和栈、无正文和危险 cause，未用关闭日志或删除错误处理来通过验收。独立编程异常仍保真并返回 500，见第 5 节。

### 3.4 未新增自动 HTTP 重试

Client 仍只有一条 RestClient POST 路径，无恢复循环、RetryTemplate、`@Retryable`、退避等待或 Retry-After 解析。生产 DeepSeekConfiguration/Properties、Embedding 单次 RetryTemplate 和 Milvus 配置均无差异。

42 项 FailurePolicy 测试验证状态码、网络/内容错误各一次尝试，8 类 Retry-After 不等待或重发；只有调用方第二次显式调用才出现第二次请求。业务测试以 1 → 2 → 2 验证首次失败、显式恢复、成功重放。

“未新增应用级重试”不等于网络 exactly-once。JDK 原有底层连接恢复与 Milvus SDK 原有隐式重试不在本次改动内；本轮没有重新故障注入验证其全部路径。不能据此承诺真实供应商只生成/计费一次。

### 3.5 FAILED、stale、attemptId 和 CAS

生产 Orchestration、Workflow、Report Service、短事务、Mapper XML、Schema 均与 HEAD 一致。

[InterviewWorkflowService.java:142](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java:142) 仍在 claim/reclaim 后带 attemptId 执行评价；失败 trace 和 FAILED 收尾检查当前所有权。[InterviewWorkflowTransactionService.java:390](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java:390) 锁 Answer，拒绝失效 attempt，再原子提交 trace/评价/流程变化。

[InterviewReportService.java:106](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java:106) 仍使用 claim 后的确定 version；失败不会重新读取新 version 去覆盖继任者。Report + Weakness + READY 在原短事务中一起提交。

本轮真实 MySQL 通过旧 attempt 拒绝写入、失败审计 fencing、报告新旧任务完成顺序/旧任务失败、单赢家 CAS、幂等重放等测试。stale 仍只隔离提交权，不取消旧 HTTP/RPC。

### 3.6 成功评分和报告

请求参数、模型配置、严格整数 JSON 解析、Java 五维计分与总分、评分点校验、确定性追问、FINAL 汇总、报告字段校验均未改变。既有 Client/Generator 成功测试、业务离线成功链、历史结果回放、FINAL 一致性与完整回归通过。

“正常流程未受影响”的证据为 Mock HTTP + 真实业务代码/隔离 MySQL，不是本轮真实 DeepSeek 成功调用。原始模型结果的数据库持久化契约未改变。

### 3.7 测试是否验证真实传播

- `DeepSeekLogSafetyTest` 使用真实 Client、模拟 HTTP 与真实 GlobalExceptionHandler；验证异常对象、cause/suppressed、Java stack trace、ThrowableProxy、PatternLayout 和 HTTP 响应，而非只检查顶层 message。
- `FullInterviewBusinessOfflineTest` 将真实 Client/DeepSeekJudgeProvider/Report Generator 接入实际 Controller、JWT、Service、MyBatis；只有外部服务为替身。24 类答案故障、25 类报告故障均断言数据库失败状态、没有部分发布、同 payload 显式恢复及重放次数。
- 业务类的事务化夹具不单独代替事务并发证据；另运行真实短事务与 latch 并发测试，验证提交/回滚和旧任务隔离。
- Loopback 测试使用生产配置创建的 JDK request factory，补足 MockRestServiceServer 无法验证网络等待的限制。

## 4. 本轮测试与构建结果

### 4.1 隔离环境

C3 专用容器 `ai-interview-c3-20261112-mysql`，镜像 MySQL 8.0.45 ARM64，`127.0.0.1:13325`，tmpfs `/var/lib/mysql`，全局时区 `+08:00`。创建前核查现有 Docker 容器；只在本容器用仓库 SQL 的临时副本将建库/USE 名称改为 `ai_interview_test`，13 张表，未改仓库 Schema。创建后及清理前均确认只有 test 库及系统库，无 formal local/evaluation 库。

执行器 `/private/tmp/ai-c3-20261112/run_maven.py` 清除继承的 `RUN_REAL_*`、`SPRING_*`、`MILVUS_*`、`DEEPSEEK_*`、`JEV_*`、`AI_JUDGE_*`、DB/TEST_DB 与 JVM/Maven 覆盖，再只注入本轮测试连接和 `DEEPSEEK_ENABLED=false`。`MILVUS_ENABLED`、真实 Smoke 开关保持 unset，隔离初始化器与双层门禁不变。测试内显式启用的业务 Bean 由外部替身接管。

沿用已验证的临时 Mockito agent：`JAVA_TOOL_OPTIONS=-javaagent:/Users/kun/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar`。随机测试口令仅保存在权限 0600 的临时文件和子进程环境中，不进入报告/Git。没有持久修改用户环境、pom 或测试配置。

首次普通沙箱 Docker 只读查询因 socket 权限失败（退出 1），随后获准使用执行环境完成检查与测试；没有自动审批拒绝。该工具环境失败不计为 Java 测试失败。

### 4.2 每轮准确结果

每轮先归档旧 Surefire，再只统计当轮 XML；下列重复测试不能相加当作独立用例总量。

1. **focused**：136 / 0 / 0 / 0，退出 **0**，BUILD SUCCESS，8.506s。先 clean，重新编译 177 个生产源文件和 135 个测试源文件，防止残留字节码影响验收。
2. **business**：336 / 0 / 0 / 0，退出 **0**，BUILD SUCCESS，23.985s。
3. **full**：1698 / 0 / 0 / 19，退出 **0**，BUILD SUCCESS，40.136s；1679 项非跳过用例通过。
4. **package**：退出 **0**，BUILD SUCCESS，0.922s；使用 `-DskipTests package`，没有执行测试，T/F/E/S **不适用**。执行器 summary 的 0/0/0/0 仅表示无当轮 XML，不能描述成又通过一次回归。
5. **git diff --check** 与 **git diff --cached --check**：均退出 **0**。未跟踪文件另查行尾空白及冲突标记。

实际 Maven 子命令（工作目录均为仓库根）：

```sh
sh mvnw -B -ntp clean -Dtest=DeepSeekFailurePolicyTest,DeepSeekHttpTimeoutTest,DeepSeekLogSafetyTest,DeepSeekEvaluationClientTest,DeepSeekInterviewReportGeneratorTest,DeepSeekConfigurationTest,DeepSeekPropertiesTest test
sh mvnw -B -ntp -Dtest=FullInterviewBusinessOfflineTest,InterviewWorkflowServiceTest,InterviewReportServiceTest,EvaluationOrchestrationServiceTest,InterviewConcurrentReplayIntegrationTest,InterviewPostClaimFailureIntegrationTest,InterviewReportFinalConsistencyIntegrationTest,InterviewWorkflowTransactionServiceIntegrationTest,InterviewReportTransactionServiceIntegrationTest,InterviewEvaluationClaimConcurrencyIntegrationTest,StaleRecoveryCasIntegrationTest,JudgeConfigurationTest,DeepSeekJudgeProviderTest,DeepSeekInterviewReportGeneratorConfigurationTest,LlmEvaluationValidatorTest,GlobalExceptionHandlerMvcTest test
sh mvnw -B -ntp test
sh mvnw -B -ntp -DskipTests package
```

这些命令由上述执行器提供隔离环境运行，不能把缺少凭据的裸命令当作已复现环境。Maven `clean` 只清理构建产物，不是 `git clean`。

19 项跳过：RealEmbeddingSmokeTest 1、RealMilvusVectorStoreSmokeTest 1、KnowledgePersistenceIntegrationTest 10、RealKnowledgeDocumentProcessingSmokeTest 2、RealKnowledgeDocumentProcessingFailureIntegrationTest 2、RealMilvusSchemaValidationSmokeTest 2、AiSystemBaselineEvaluationTest 1。其余普通真实 MySQL Mapper/事务测试没有跳过。

关键完整回归分类（均失败/错误/跳过=0）：DeepSeekLogSafetyTest 36，DeepSeekFailurePolicyTest 42，DeepSeekHttpTimeoutTest 4，FullInterviewBusinessOfflineTest 112，InterviewConcurrentReplayIntegrationTest 18，InterviewPostClaimFailureIntegrationTest 3，InterviewReportFinalConsistencyIntegrationTest 24，InterviewWorkflowTransactionServiceIntegrationTest 6，InterviewReportTransactionServiceIntegrationTest 9，InterviewEvaluationClaimConcurrencyIntegrationTest 11，StaleRecoveryCasIntegrationTest 3，JudgeConfigurationTest 7。

Loopback 的 600ms readTimeout 实测：聚焦 headers/body/partial-json/trickle 分别 **611/608/602/607ms**；完整回归分别 **606/602/606/607ms**，每例收到请求 1 次，trickle 成功发出 16/15 段。headers 为 TIMEOUT，其余为 IO_ERROR，均在上游 latch 未释放时结束调用方等待。未验证真实公网连接超时、TLS、代理、HTTP/2、计费或生产吞吐。

### 4.3 打包与证据路径

- `/Users/kun/Developer/projects/ai-interview-system/target/ai-interview-system-0.0.1-SNAPSHOT.jar`：Spring Boot 包，73,089,968 字节；含当前 DeepSeek Client 类，未含 application-local.yaml、.env 或私钥类文件名。
- `/Users/kun/Developer/projects/ai-interview-system/target/ai-interview-system-0.0.1-SNAPSHOT.jar.original`：repackage 前包。
- `/private/tmp/ai-c3-20261112/focused.log`、`business.log`、`full.log`、`package.log`：各轮日志。
- `/private/tmp/ai-c3-20261112/focused-summary.json`、`business-summary.json`、`full-summary.json`、`package-summary.json`：退出码及计数。
- `/private/tmp/ai-c3-20261112/full-reports/`：完整 Surefire XML；对应 focused/business 的 `*-reports/` 和 `*-suite-counts.json` 保存逐类证据，打包不会覆盖这些归档。

临时证据不纳入 Git。未运行前端测试/构建，因为本轮没有前端修改；没有将此前历史测试结果替代本轮结果。

## 5. 日志 sentinel 与敏感信息检查

本轮扫描完整 Maven 输出，而不只读取 ListAppender 断言。10 个标记为：`B4B_TEST_SENSITIVE_BODY_20261112`、`B4B_FAKE_API_KEY`、`B4B_FAKE_SYSTEM_PROMPT`、`B4B_FAKE_USER_ANSWER`、`B4C_PRIVATE_UPSTREAM_DATA`、`PRIVATE_MODEL_OUTPUT`、`PRIVATE_CANDIDATE_INPUT`、`offline-test-key`、`FORGED_LOG_RECORD`、`loopback-fake-key`。

- focused：全部 10 项均 **0**。
- business/full：除 `PRIVATE_MODEL_OUTPUT` 各 **3** 外，其余 9 项均 **0**。
- 专用 DeepSeek 安全测试与业务故障捕获断言全部通过；安全 ERROR 和本地栈仍存在。

**C3-P2-01（验收文档口径，已在本报告澄清）**：`FullInterviewBusinessOfflineTest.internalExceptionsKeep500WithoutModelRetry` 在 [当前源码:306](/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java:306) 主动构造带同一合成标记的 IllegalArgumentException、IllegalStateException、NullPointerException。它们直接从替身 Judge 抛出，不经过 DeepSeek HTTP/JSON 安全边界；全局未知异常处理器按原设计打印原始栈。HEAD 基线已有这三个 fixture（基线 295～299 行），B4-B 和 B4-C 历史 full-regression.log 也各有 3 次同标记。

因此，B4-C 第 8.3 节“敏感 sentinel、假 Key 和假答案标记出现次数均为 0”的陈述应按其专用扫描集合理解，并在累计交付中明确扫描集合与例外；不得扩展成“所有原始文本都不会进入任何日志”。本轮没有关闭全局日志、删测试、改假标记来制造零结果，也没有发现 B4-C 重新引入 DeepSeek 正文泄漏。此项不阻断当前安全修复提交；将来若治理未知异常或数据库 suppressed，应另立范围和实证测试。

源文件安全检查覆盖 10 个 B4 候选文件及本 C3 报告：私钥头、长 provider key、GitHub token、JWT 字面量模式，临时测试口令精确匹配、行尾空白、冲突标记及人工检查新增授权头/凭据字段。未发现真实秘密；测试中的假 key、sentinel 和文档示例为有意保留的合成数据。该检查是本次变更范围检查，不宣称完成全 Git 历史、所有业务数据或所有私有本地文件的密钥审计。

证据：`/private/tmp/ai-c3-20261112/log-sentinel-scan.json`、`/private/tmp/ai-c3-20261112/source-safety-scan.json`。`mvnw`/Compose 不在候选提交集合，使用摘要保护，不把本地文件内容复制到报告。

## 6. 三次提交规划与精确 hunk 归属

### 6.1 拆分依据和顺序验证

B4-C 入场快照位于 `/private/tmp/ai-b4c-20261112/entry/`，其 9 个文件均已按原 `entry-manifest.json` SHA-256 核验。它提供可复原的 **B4-B 完成状态**，无需凭累计 diff 猜测中间版本。

已生成三份临时评审补丁：

- `/private/tmp/ai-c3-20261112/01-b4a.patch`
- `/private/tmp/ai-c3-20261112/02-b4b.patch`
- `/private/tmp/ai-c3-20261112/03-b4c.patch`

在仓库之外、不含 `.git` 的普通临时目录以 HEAD 文件为起点，逐份执行 `git apply --check` 和应用，再逐字节比较结果。B4-A → B4-B → B4-C 顺序可应用且最终重建当前候选内容。**没有在用户仓库应用补丁，没有创建提交、暂存内容或 Git 工作树。** 初版临时补丁生成漏写 new-file mode 元数据，检查退出 1；补齐元数据后顺序检查退出 0，该工具错误与源码/测试无关。

提交授权后应再次核验 HEAD 和文件摘要；若工作区已有后续修改，重新拆分，不能盲用旧临时补丁。禁止直接整文件暂存两个共享文件给 B4-B，否则会提前混入 B4-C。

### 6.2 提交 1：B4-A 审计

建议主题：`docs: audit DeepSeek reliability and recovery boundaries`。

只包含 `/Users/kun/Developer/projects/ai-interview-system/docs/verification/B4A_DEEPSEEK_RELIABILITY_AUDIT_20261112.md`，整文件新增 468 行。它是历史审计和后续计划，不将其中计划描述为已实施；本 C3 报告提供累计最新状态。

### 6.3 提交 2：B4-B 安全边界及测试

建议主题：`fix: sanitize DeepSeek failure chains and logs`。

包含第 2 节所列 Client、Report Generator、ClientTest、DeepSeekLogSafetyTest、FullInterviewBusinessOfflineTest，以及 B4B 报告，共 6 个文件。Report Generator、ClientTest 的全部当前差异，LogSafetyTest/B4B 报告的整文件新增，均归 B4-B。

共享 Client 必须采用 **B4-C entry 快照中的 B4-B 版本**：增加网络类型 imports；onStatus 只记 category 与 httpStatus，不含 reason 方法；RestClient 异常断链和 B4-B 原始逐节点分类；finish_reason 白名单；评价 JSON 去 cause。HEAD → B4-B 的精确 hunk 头：

```diff
@@ -16,7 +16,14 @@
@@ -153,6 +160,13 @@
@@ -168,10 +182,32 @@
@@ -228,7 +264,7 @@
@@ -257,6 +293,16 @@
@@ -282,8 +328,7 @@
```

共享业务测试的 B4-B 部分：日志 imports/常量；`withSafeProviderLog` 及两个故障调用点；假授权头；原始故障 body 替换为恶意合成文本；outer-json/malicious-finish；HTTP 响应 sentinel 断言。**答案参数为 15 类，报告参数为 17 类，总测试 95 项**；不带 B4-C 追加类型。精确 hunk 头：

```diff
@@ -1,5 +1,11 @@
@@ -49,6 +55,7 @@
@@ -59,6 +66,7 @@
@@ -92,6 +100,9 @@
@@ -332,7 +343,7 @@
@@ -343,7 +354,7 @@
@@ -358,7 +369,7 @@
@@ -370,11 +381,11 @@
@@ -425,6 +436,39 @@
@@ -435,7 +479,8 @@
@@ -445,19 +490,21 @@
@@ -476,7 +523,7 @@
@@ -504,7 +551,8 @@
```

这些是“HEAD → 中间版本”的 hunk，不能直接用当前混合 diff 的 hunk 编号替代。当前同一参数行、onStatus 行、分类方法和故障工厂有跨阶段修改，应按中间快照或补丁编辑，不是简单全选/取消选择整个 hunk。

### 6.4 提交 3：B4-C 分类/超时验证及 C3 收口

建议主题：`fix: classify DeepSeek failures and verify timeout boundaries`。

包含 Client 和业务测试的 B4-C 余量，加下列整文件新增：

- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekFailurePolicyTest.java`
- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekHttpTimeoutTest.java`
- `/Users/kun/Developer/projects/ai-interview-system/docs/verification/B4C_DEEPSEEK_TIMEOUT_RETRY_20261112.md`
- `/Users/kun/Developer/projects/ai-interview-system/docs/verification/C3_B4A_B4C_CLOSEOUT_20261112.md`

共 6 个文件。C3 文档归最后一次提交，以便三次提交后拥有完整累计验收记录。

Client 的 B4-B → B4-C 余量：onStatus 增加局部数字 status 和固定 reason；新增 `safeHttpFailureReason`；分类器改成有界遍历中积累类型、再按具体传输异常优先返回。精确 hunk 头：

```diff
@@ -162,9 +162,10 @@
@@ -188,24 +189,47 @@
```

业务测试余量：两处参数行增加 403/408/502/504、connect-timeout/http-timeout、resource/tool finish；答案另加 missing-fields；故障工厂新增两种超时及扩充 finish 白名单。答案 **24 类**、报告 **25 类**，总 **112 项**。精确 hunk 头：

```diff
@@ -343,7 +343,7 @@
@@ -369,7 +369,7 @@
@@ -492,6 +492,8 @@
@@ -504,7 +506,7 @@
```

共享文件虽相互依赖，顺序为 B4-B 安全边界先行、B4-C 分类补强后置；B4-C 专项测试不提前进入 B4-B，否则会要求尚未实现的 reason/嵌套类型优先级。中间版本本轮只验证拆分及内容一致性，没有重新编译/测试 B4-B 独立快照；其独立运行记录来自 B4-B 报告，本轮新运行的全部结果针对累计最终树。

### 6.5 明确排除

`mvnw` 权限变化、`compose.mac.local.yaml`、`target/`、`/private/tmp/ai-c3-20261112/`、任何 credentials/env 文件都不进入上述三次提交。没有使用 `git add .`，没有暂存意外文件。未来授权必须明确区分提交和推送；本轮止于规划。

## 7. 遗留风险与 B4-D 建议审查范围

P0：未发现；P1：未确认。新增必须修复的 P2 代码缺陷：未发现。以下是 B4-A 已识别且本轮未解决的 **P2 独立风险/缺口**，不是 B4-C 回归：

1. **整链时间预算与 Milvus 隐式重试**：当前 DeepSeek 单次超时验证不代表整个评分有硬截止时间。Milvus SDK 重试、数据库等待及 CPU/调度仍影响整体预算；合法 stale 配置可能早于旧任务结束。优先独立审查 search 总尝试/取消语义，不能顺带改变 insert/delete/补偿。
2. **业务并发容量**：没有统一业务任务上限；多用户或多个报告可占请求线程。公开多用户使用前验证有界许可、快速拒绝和异常释放。当前没有实测容量数据，不能推测已发生服务耗尽。
3. **前端 GENERATING 恢复入口**：GET 轮询不能触发后端 stale POST 接管；恢复页面入口仍是独立前端任务。本轮未验证崩溃端到端场景。
4. **安全调用可观测性**：本轮只有错误类别/状态和本地栈，尚无每次调用耗时、成功/失败总数、业务 attempt 与供应商 attempt 关联。不能用已落库结果数当作供应商调用总数。

建议 B4-D 先做范围清晰的审查与故障注入：

- D1 沿 B4-A 的并发许可方向，审查共享评分/报告容量、claim 前后位置、READY/EVALUATED 重放豁免、fresh/stale/CAS 失利、finally 释放；用 latch 合成任务验证超额快速拒绝、无许可泄漏和普通查询可用性，不压真实模型。
- D2 审查安全事件字段与一次调用一次统计，固定 provider/operation/category/status，单调时钟耗时；避免正文、headers、Token、原始异常以及无界标签。继续保留本轮日志捕获和 502 回归。
- 将 Milvus search 预算、全链 deadline/取消和前端恢复入口明确列为独立依赖任务；在预算尚未验证前继续保持 DeepSeek 单次应用尝试。
- 若扩展全局日志范围，单独处理 Embedding/Milvus 异常、数据库收尾 suppressed、历史 JSON 解析及 APM/wire-level 采集；本次通用 sentinel 三次命中可作为范围区别示例，不据此扩大本轮代码修改。

仍未验证：真实 DeepSeek/Embedding/Milvus、真实费用/重复生成、生产负载、WAN/TLS/代理/HTTP2 故障、全生命周期最多调用次数。没有通过添加 Redis、MQ、线程池、熔断框架或提高超时参数来掩盖这些边界。

## 8. 原有修改保护与结束状态

入场清单：`/private/tmp/ai-c3-20261112/entry-manifest.json`，12 个文件的内容/权限快照位于同目录 `entry/`。收口时逐项 SHA-256 和 mode 比较均一致；`.git/index` 内容摘要、HEAD、暂存区状态不变。

重点保护项：

```text
mvnw
  SHA-256 cae96cef89ebea3531221f4ae17c23cf8edf67d00eae8306d4186ae1bbed4d02
  mode 0755（保留原有执行权限）
compose.mac.local.yaml
  SHA-256 60eb30bf23a96d95ed98305e813a0492703e92eeebeb03ab14379cc223a81974
  mode 0644（保持未跟踪）
```

回归完成后按名称、purpose 标签、镜像、tmpfs 及无持久卷核验，仅停止并移除 C3 自建 MySQL 容器，确认已不存在；没有操作其他项目容器、正式数据库或持久卷。清理证据：`/private/tmp/ai-c3-20261112/cleanup.json`。

状态明确区分：B4-B/B4-C 已有代码保留；本轮干净编译、Mock/单元、Loopback HTTP、真实隔离 MySQL 回归、完整测试及打包已验证；真实供应商 Smoke 未执行；全部候选文件未暂存、未提交、未推送。

## 9. 本阶段 10 分钟源码学习指南

1. **0～2 分钟：安全边界。** 读 [DeepSeekEvaluationClient.java:148](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java:148) 和 [GlobalExceptionHandler.java:125](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/common/exception/GlobalExceptionHandler.java:125)。回答：固定 HTTP 502 为什么不等于日志安全？新异常无 cause 为什么仍有可诊断的本地栈？
2. **2～4 分钟：分类不是重试。** 读 [Client:192](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java:192) 和 [DeepSeekFailurePolicyTest.java:67](/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekFailurePolicyTest.java:67)。找出 HTTP reason、嵌套超时优先级及调用次数断言。解释为什么 IO_ERROR 不能仅凭 message 猜成 TIMEOUT。
3. **4～6 分钟：真实等待与 Mock 的区别。** 读 [DeepSeekConfiguration.java:25](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekConfiguration.java:25) 和 [DeepSeekHttpTimeoutTest.java:28](/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekHttpTimeoutTest.java:28)。找出生产 factory、latch、计时和 requests=1；解释“调用方返回”为什么不等于“供应商停止计费”。
4. **6～8 分钟：失败、恢复与重放。** 读 [FullInterviewBusinessOfflineTest.java:343](/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java:343)、[InterviewWorkflowTransactionService.java:390](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java:390)。串起 FAILED → 同 payload 显式恢复 → 成功重放的 1/2/2 次模型计数；解释 attemptId 只控制哪一种权利。
5. **8～10 分钟：证据与提交边界。** 读 [DeepSeekLogSafetyTest.java:228](/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekLogSafetyTest.java:228)、[报告旧版本并发测试:307](/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/InterviewReportTransactionServiceIntegrationTest.java:307)，对照本报告第 5/6 节。回答：为何通用 sentinel 三次命中不等于专用 DeepSeek 测试失效？为什么两个共享文件必须以 B4-B 中间版本拆分，而不能按当前整个 hunk 暂存？

## 10. 交付与停止点

正式新增文件：`/Users/kun/Developer/projects/ai-interview-system/docs/verification/C3_B4A_B4C_CLOSEOUT_20261112.md`。构建产物和临时证据实际路径见第 4/6/8 节，不将它们加入版本管理。

本阶段累计审查、回归验收和提交准备完成，等待用户的提交授权；不自动进入 B4-D 或执行任何 Git 写入操作。
