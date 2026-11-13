# C6：可靠性修复阶段累计收口

执行日期：2026-11-13（Asia/Shanghai）。仓库：`/Users/kun/Developer/projects/ai-interview-system`。

**结论：CONDITIONAL_GO。** B4-A～B5-P2-B 的累计工作区具备开展隔离、合成数据、单人串行真实测试的工程基线；本次未发现新的、未解决的 P0/P1。真实测试启动前仍须完成第 6 节准入单，不能据此宣布真实第三方集成、浏览器整链、生产容量或公开发布已验收。

C6 只新增本报告并更新两份工程清单；没有修改生产代码、测试源码、依赖、配置、SQL 或历史验收报告。按用户允许的复用规则，**本轮未重跑 Maven/Vitest 测试或构建**，而是核对源码快照、依赖、原始运行日志及 XML：后端最终 **1832/0/0/19**，前端最终 **336/0/0/0**，均为已核实的历史运行，不冒充 C6 新执行结果。现有打包产物一致；本轮重新执行了差异、依赖一致性、日志与敏感信息检查。

## 1. 基线、远端与既有修改保护

- 已阅读根 AGENTS、工程台账、学习清单、B4-A～E、B5-0、B5-P2-A/B 验收，以及 C3 和 B5 真实测试计划；实际行为以当前源码及准确版本依赖为准，不把历史章节当作当前运行合同。
- 当前分支 `main`，HEAD `01ac774caff491b978dd2268e8b427355a64b97a`；暂存区为空。
- 2026-11-13 通过 GitHub 连接器只读 `fetch_commit(main)` 核实远端为 [`89b77dc082272e38cfbbecb4b57c37edae9ee74d`](https://github.com/kun520c/ai-interview-system/commit/89b77dc082272e38cfbbecb4b57c37edae9ee74d)，与缓存 `origin/main` 一致。`git rev-list --left-right --count <remote>...HEAD` 为 **0 / 3**。未 fetch、未修改 Git 引用，未检查所有远端分支。
- B4-A 本地提交 `004e434d3c1352c15d022bc1bf08abdfb02f4a02`；B4-B `f764c087f75d6b515b09bf2a0aef3f1d9a20c906`；B4-C/C3 为当前 HEAD。三者尚未进入本次核验的远端 main。B4-D/E、B5-P2-A/B 及新工程文档均无提交 SHA。
- 入场 **54 个变动路径**：34 个已跟踪修改、20 个未跟踪。C6 结束为原 54 项加本报告，共 55 项；台账原本已未跟踪，不能说它们由 C6 首次创建。
- `mvnw` 的 100644→100755 只涉及权限，正文 SHA-256 为 `cae96cef89ebea3531221f4ae17c23cf8edf67d00eae8306d4186ae1bbed4d02`，本轮保留。Compose、两处 `.DS_Store`、MQ 文档和其他既有文件均保留；不清理、覆盖或回退用户修改。
- 入场对 998 个 Git 跟踪／非忽略未跟踪文件记录 SHA-256 和 mode，并保存 index 哈希。临时证据位于 `/private/tmp/ai-c6-20261113/`，最终保全结果见 `preservation-check.json`。除两份授权台账外，入场文件字节及 mode 不变；HEAD/index 不变。

### 1.1 回归证据对应的代码快照

不是只凭测试总数或文件时间判断一致：

1. B4-D 的 **11 个源码／测试／验收文件**与 B4-E 入场 `entry-manifest.json` 全部一致。
2. B4-E 的 **5 个文件**与 B5-0 入场 `baseline.json` 全部一致。
3. B5-P2-A 的 **21 个文件**与该批最终 `b5-p2a-final-files.json` 全部一致；其 23 项清单中仅两份工程台账有后续更新。
4. B5-P2-B 的最终 **9 项交付快照**在 C6 入场全部一致，包含本批 7 个文件和两份台账；该批入场 47 项中，仅两份台账有本批后续变动，其余 45 项一致。
5. B4-B/C 已提交的生产文件和专项测试没有工作区 diff；共享业务测试当前新增部分归 B5-P2-B。pom、Maven Wrapper 配置、前端 package/lock、测试配置及隔离初始化器无累计修复之外的漂移。

详细映射：`batch-history-hash-audit.json`、`historical-snapshot-audit.json`。C6 入场源码／配置／构建／工具 688 文件摘要为 `6309802a92b18cb4eaebab2fde0ef771acb8c4e10a3ec33f1988f72419350449`；算法为路径排序后累加 `path + NUL + SHA256(file) + LF`，不含工程 Markdown，mode 由入场清单另记。该摘要是工作区身份，不是 Git commit SHA。

## 2. 累计代码审查

### 2.1 B4-A 与 B4-B/C：DeepSeek 边界

B4-A 是审计／设计，不是代码修复。其日志通道、分类、Milvus 和前端入口问题已经由后续批次分别处理；统一整链 deadline、业务并发许可、安全事件／usage 观测仍未实施。

`DeepSeekEvaluationClient.requestCompletion` 以请求级 `onStatus` 在默认错误处理前拒绝所有非 2xx，只保留数字状态、固定 stage/category/reason；不读取错误正文、statusText 或诊断 headers。网络与外层 JSON 只捕获 RestClientException，最多检查 10 层 cause 的类型；明确连接超时优先，其次 timeout、连接故障、JSON、一般 IO。未根据不可信异常文案猜类别。

`parseSuggestion` 与 `DeepSeekInterviewReportGenerator.parseResponse` 不挂 Jackson 原始 cause；未知 finish_reason 变为 UNKNOWN 且仍拒绝。对外仍是固定 502，上游 401/403 不冒充用户鉴权失败。程序缺陷仍按原始异常传播，没有改成假成功或一律 502。日志保留本地栈，未关闭全局 ERROR。

评分的主答评价、反馈和追问建议共用一次 DeepSeek 请求；追问答案是另一次评价，报告是另一个 POST 的一次调用。应用没有自动重发或 Retry-After 等待。显式 FAILED/stale 恢复属于新业务尝试，可产生新的费用；JDK 连接恢复不在应用“一次尝试”的 exactly-once 保证内。

证据：最新完整 XML 中 DeepSeekLogSafety **36/0/0/0**、DeepSeekFailurePolicy **42/0/0/0**、DeepSeekHttpTimeout **4/0/0/0**；后者使用真实 JDK HTTP 与 loopback 合成服务器，不是真实 DeepSeek。

### 2.2 B4-D：Milvus SDK 重试及异常传播

实际依赖仍为 Milvus SDK **2.6.20**。`MilvusConfiguration.milvusClient` 在 Bean 发布前固定 `maxRetryTimes=1`，绕过该版本会吞掉耗尽／预算／中断异常的多次 callable 分支；构造期 Connect/ListDatabases 置于 cancellable gRPC Context，finally detach、cancel、shutdown。`MilvusProperties` 拒绝亚毫秒和不可转换的 Duration；`MilvusCollectionInitializer.run` 对 null hasCollection 明确失败，不当作“不存在”而创建。

`MilvusVectorStoreClient` 原有空响应、计数及搜索结构校验仍在，SDK 失败仍作为失败传播。`KnowledgeRetrievalService` 没有 catch 后返回空证据的旁路。文档处理在 insert 前标记可能已有副作用，只补偿本次 attempt 的向量 ID；MySQL 提交确认／旧 attempt fencing 和 FAILED 恢复保持。

一次 callable 可能包含 Schema 查询、SchemaMismatch 刷新后再次 insert 等内部 RPC，不能称为服务器恰好一次。requestTimeout 是该次 stub deadline，也不是整个评分／全部初始化的严格 wall-clock 上界。独立 `OrphanVectorMaintenance` CLI 直接 new 客户端，未继承 Spring 策略；本轮未运行或治理该入口。

证据：真实 SDK 特征 **15/0/0/0**、合成 loopback gRPC **12/0/0/0**、真实 MySQL attempt/补偿 **19/0/0/0**、检索服务 **21/0/0/0**。真实 Milvus 持久化、Schema/索引兼容、迟到写入最终清理、TLS/代理仍未验证。

### 2.3 B4-E：GENERATING 恢复与报告语义

`useInterviewReport.recoverGenerating → submitReport` 复用既有 POST；正常 3 秒轮询仍只有 GET。前端没有用页面停留时间判 stale，也没有自动重发 POST。posting 同步守卫、epoch、路由／卸载检查防止重复提交及旧 GET/POST 覆盖当前状态。409、超时和 5xx 后用一次 GET 确认；无法确认时进入 RECOVERY_UNKNOWN，只允许查状态。

后端 GET 只读；POST 校验所有者和 COMPLETED，再用数据库 updated_at、默认 10m 与 Session version CAS 领取。READY 返回已有报告；已有报告的非 READY 恢复验证 FINAL 来源，不重复生成或更新弱项。新报告仍由 Java 聚合有效 FINAL，Report + Weakness + READY 同一短事务，失败回写受 attemptVersion 限制。

本批无后端 API/SQL/事务改动。与 B4-D/P2-B 的失败传播兼容：失败仍会进入原收尾路径，前端按服务端事实确认，不凭网络错误制造终态。单页面守卫不替代跨标签页 CAS；浏览器离开不取消服务器操作。

证据：前端报告 **56/0/0/0**；最新后端 ReportTransactionIntegration **9/0/0/0**、ReportFinalConsistency **24/0/0/0**、StaleRecoveryCas **3/0/0/0**。jsdom/MockMvc 不能替代真实浏览器崩溃恢复。

### 2.4 B5-P2-A：密码、事务及 JWT

注册／登录／新密码沿 DTO 的 `@Utf8ByteSize` 与服务 `PasswordPolicy.validate` 限制为最多 72 UTF-8 字节；最小长度保留 8 个 UTF-16 单位。当前密码使用 `validateCurrent`，不追加 8 位下限。前端 TextEncoder 做字节预检，移除会裁剪输入的 maxlength；后端独立拒绝，不能只依赖浏览器。空白判断与当前 Hibernate Validator 合同一致，未 trim、截断、归一化或预散列密码。

`UserService.changePassword` 仍在同一事务中锁 user 行、校验当前密码及新旧不同、BCrypt 编码、检查单行更新。JWT `credential_version` 仍绑定数据库当前散列，过滤器重新读取用户状态／角色／凭据版本。改密成功立即使旧 Token 失效；失败不写散列或两个时间列，旧 Token 仍有效。

证据：PasswordUtf8Boundary 单测 **29/0/0/0**、无外围事务的真实 MySQL + MockMvc 集成 **19/0/0/0**、真实并发改密 **1/0/0/0**、JwtAuthenticationFilter **32/0/0/0**、JwtTokenService **6/0/0/0**。历史系统若曾接受超过 72 字节密码，当前完整超长输入会被拒绝；散列不能反推出原密码长度。该兼容风险保留，未扫描正式用户或实施重置模块。

### 2.5 B5-P2-B：Embedding 安全边界

`EmbeddingConfiguration → EmbeddingOpenAiApi → OpenAiEmbeddingModel → SpringAiEmbeddingClient` 仍使用 Spring AI **1.1.8**。专用 error handler 在读取上游错误文本前转换为安全异常；子类在框架转换／空响应日志前拒绝 null body/data/item。适配器仅转换 RestClient 家族异常，模型名不匹配使用固定文案，不回显供应商值。

独立 BusinessException、IllegalStateException、NPE 等仍原样传播，未用 catch-all 掩盖程序错误。维度、顺序、索引、有限浮点及 usage 校验继续存在，失败不生成假向量。RetryTemplate 仍是 maxAttempts=1/noBackoff；正常分批发送不是失败重试。

证据：SpringAiEmbeddingLogSafety **40/0/0/0** 同时检查 HTTP、Throwable、cause/suppressed、完整栈、根 Logger 与最终 PatternLayout；FullInterviewBusinessOffline **124/0/0/0** 含 11 种 Embedding 答案失败／显式恢复和知识处理失败／恢复。该业务类有外围事务，跨请求提交及 fencing 另由独立集成测试证明，不能混称真实模型整链成功。

### 2.6 跨批不变量与审查级别

- requestId 仍要求原问题 ID 和原答案精确相等；成功重放读取持久评价，不调用模型。B1 ConcurrentReplay **18/0/0/0**。
- claim/retry/reclaim 只允许单胜者，attemptId 决定答案／trace 的提交权，Session version 决定推进／报告提交权；原成功领取后的 try 范围未退回。PostClaimFailure **3/0/0/0**、EvaluationClaimConcurrency **11/0/0/0**、WorkflowTransactionIntegration **6/0/0/0**。
- RAG 查询不包含用户答案；只有 MySQL 资格合格并实际进入 Prompt 的证据落有效 trace。外部失败不会被视作合法零命中；主问题仍来自固定题库，Java 计分、追问及 FINAL-only 规则未变。
- test/evaluation 隔离配置、EnvironmentIsolationInitializer 和全部真实测试双门禁保持；隔离初始化器 **23/0/0/0**。当前进程未设置 RUN_REAL_*／DB／模型／Milvus／Spring 覆盖变量，本轮未注入这些开关。

**P0：未发现；P1：未发现。** 没有确认需要本轮修改生产代码的新缺陷。第 5 节的已知限制与真实验证缺口仍有效，不将“未发现”写成全系统无缺陷证明。

## 3. 回归证据核验及逐项复用决定

T/F/E/S = Tests / Failures / Errors / Skipped，总数含跳过；不同轮次不能相加。C6 新执行 JUnit/Vitest 用例数为 **0（未运行，不是 0 项通过）**。本轮重新解析 37 个非 previous 的历史归档运行目录，结果保存于 `historical-xml-audit.json`；目录内全部 TEST-*.xml 逐份汇总，不把 target 残留或归档副本混入。

### 3.1 各批最终及关键历史结果

| 批次 | 已核实证据 | T/F/E/S | 结论 |
| --- | --- | --- | --- |
| B4-A | focused-tests.log；mockito-rerun.log | 201/0/85/0；85/0/0/0 | 首轮 attach 环境错误，受影响 85 项重跑；没有独立全量，不拼成一轮 201 全通过 |
| B4-B | full-regression-reports，132 类 | 1635/0/0/19 | 历史全量退出 0 |
| B4-C | full-regression-reports，134 类 | 1698/0/0/19 | 历史全量退出 0 |
| C3 | full-reports，134 类 | 1698/0/0/19 | 独立历史累计运行，不能与 B4-C 相加 |
| B4-D | full-reports，136 类 | 1732/0/0/19 | 历史全量退出 0；28:17 wall time 未归因为性能问题或改善 |
| B4-E | backend-report-reports，10 类；前端 full-frontend.log | 123/0/0/0；317/0/0/0 | 当时后端定向、前端全量；未运行后端全量 |
| B5-0 | backend-full-reports，136 类；前端日志 | 1732/0/0/19；317/0/0/0 | 当时累计全量退出 0 |
| B5-P2-A | backend-final-full-reports，138 类；frontend-final2.log | 1780/0/0/19；336/0/0/0 | 最终版本，前端 36 文件，无未处理异常 |
| B5-P2-B | final-regression-reports，139 类 | 1832/0/0/19 | 最新累计全量，1813 非跳过，退出 0 |

历史红测和错误未抹掉：B4-B 的 6/0/0/0、P2-B 两轮 25/0/0/0 是允许观察泄漏的改前实验；B4-C 42/25/0/0、B4-D 45/3/0/0 与 10/8/0/0、P2-A 两轮 16/5/0/0 是修复前契约证据。B4-B 79/0/36/0、79/1/0/0、281/32/0/0，B4-D 180/3/0/0，前端 56/2/0/0 等夹具／断言问题按原报告保留；B4-C 4/0/4/0 是沙箱 bind 失败。B4-D 两次 testCompile 失败无测试计数。已核对 XML 和原报告解释，不将历史失败重写为通过，也不将环境错误认定为生产代码故障。

最新 19 skipped 逐类为：KnowledgePersistenceIntegrationTest 10、RealEmbeddingSmokeTest 1、RealMilvusVectorStoreSmokeTest 1、RealKnowledgeDocumentProcessingSmokeTest 2、RealKnowledgeDocumentProcessingFailureIntegrationTest 2、RealMilvusSchemaValidationSmokeTest 2、AiSystemBaselineEvaluationTest 1。七类均保留 EnabledIfEnvironmentVariable 与 assumeTrue；普通 MySQL Mapper/事务测试确实运行，不受这些开关控制。

### 3.2 为什么本轮不重跑

| 项目 | 历史执行命令／结果 | C6 决定与原因 |
| --- | --- | --- |
| 后端完整回归 | `sh mvnw -B -ntp test`；1832/0/0/19，退出 0 | 复用；代码快照、139 XML、summary、BUILD SUCCESS 和实际依赖一致，无源码变动 |
| 前端完整回归 | `npm run test:unit`；336/0/0/0，36 文件，退出 0 | 复用；B4-E/P2-A 文件及 lock 无漂移，原日志可读，已安装依赖与 lock 一致 |
| 前端类型／lint／构建 | `npm run lint`、`npm run build`；退出 0 | 复用最终 frontend-final2 日志；build 已含 `vue-tsc -b`，不把类型检查当测试或额外重复执行 |
| 日志安全专项 | 最终全量中 DeepSeek 36、Embedding 40，均 F/E/S=0；P2-B 客户端聚焦195/0/0/1、业务302/0/0/0 | 复用断言；C6 另外重扫最终日志并分类所有命中，见第 4 节 |
| 后端打包 | `sh mvnw -B -ntp -DskipTests package`；退出 0 | 复用构建；本轮重新打开 JAR 检查哈希、类及依赖／敏感文件，未将 package 计为测试 |
| Git diff／敏感信息 | 本轮 `git diff --check`、`git diff --cached --check`、路径／内容扫描 | 新执行；工作区和文档是当前状态，不能用旧检查代替 |
| 真模型／Embedding／Milvus／浏览器 E2E | 未执行 | 本轮未获启动这些测试的任务范围，且准备条件未完成；不自动开启任何真实门禁 |

复用不是无条件沿用历史数字。后续只要源码、测试、配置、lock／JAR、运行目标或 profile 改变，就要重新评估相应验证；没有此处快照一致证据的环境不得宣称复用 C6 基线。

### 3.3 环境与原始证据入口

当前实测 macOS **26.7.1 ARM64**、Temurin **21.0.13**、Node **24.22.1**、npm **11.19.1**；历史 Maven Wrapper **3.9.16**，配置未变。与用户设备说明的版本不一致处以实际命令输出为准。后端最后运行使用独立 MySQL **8.0.45** tmpfs，`127.0.0.1:65293/ai_interview_test`，时区 +08:00；运行器清除继承开关，仅注入专用数据库及临时 Mockito javaagent，DEEPSEEK_ENABLED=false。该容器已由原批次清理，本轮没有新建数据库或启动服务，不能把它描述成当前在线环境。

- 后端最新：`/private/tmp/ai-b5p2b-20261113/final-regression.log`、`final-regression-summary.json`、`final-regression-reports/`、`run.py`、`cleanup.json`。
- 前端最新：`/private/var/folders/xx/vxww8vxd1hz_f8szvp9th6j40000gn/T/b5-p2a-20261113-p3rjz9s7/frontend-final2.log`、`frontend-final2-lint.log`、`frontend-final2-build.log` 及同名 summary。
- 早期批次：`/private/tmp/ai-b4b-20261112/`、`ai-b4c-20261112/`、`ai-c3-20261112/`、`ai-b4d-20261113/`、`ai-b4e-20261113/`；B4-A/B5-0/P2-A 原目录见各原报告。
- C6 复核：`/private/tmp/ai-c6-20261113/` 中的快照、历史 XML 汇总、依赖／JAR／敏感扫描、提交归属与最终保全 JSON。只保存必要摘要，未复制或公开整个历史日志目录。

139 份最新 XML 按文件名排序累加 `name + NUL + SHA256(file) + LF` 的摘要为 `cf2fc547c08b074f33767cb8e9d1d859813e4fe3cf44429327c4e17670e5d01c`。临时原始证据可能被系统清理；正式报告、测试源码和以上摘要提供长期索引，不等于已建立持久原始日志归档。

## 4. 日志、敏感信息、依赖及打包检查

- 当前 Git 跟踪及非忽略未跟踪文本执行私钥、provider key、GitHub token、AWS access key 高特异模式扫描：入场 950 个文本文件，疑似命中 **0**；跟踪敏感配置／私钥文件名命中 **0**。Compose 与两处二进制 `.DS_Store` 不在文本扫描结论内，均明确排除提交并保持原样。扫描有漏检边界，不是绝对无秘密证明。
- `application-local.yaml` 当前不存在，忽略合同保持；没有读取或复制真实凭据到测试配置。未上传原始日志、XML 或临时凭据。
- 重新扫描 P2-B client-focused、service-focused、final-regression 三份日志：DeepSeek/Embedding 专用 sentinel、假 Key、Prompt／用户答案和知识正文标记均为 **0**。但通用 `PRIVATE_MODEL_OUTPUT` 在后两份各 **3** 次，来自 `FullInterviewBusinessOfflineTest.internalExceptionsKeep500WithoutModelRetry` 人工构造的 IllegalArgumentException、IllegalStateException、NPE（当前 409～411 行），绕过外部客户端。与 C3 已记录事实一致，非新上游脱敏失败，也不是真实秘密泄漏。不能写“所有标记全部为零”。
- 本轮 `npm ls --all --json` 退出 **0**、无 problems；278 个已安装 package 版本逐项匹配 lock，24 个未安装项均标记 optional。未 install/upgrade。前端 dist 仍为本地 ignored 构建产物，不是已部署站点。
- JAR 为 `/Users/kun/Developer/projects/ai-interview-system/target/ai-interview-system-0.0.1-SNAPSHOT.jar`，SHA-256 **`1a12fe23c58bb9d966eaba618987cde97721654676448294d308c1fadfa9b11b`**，与 P2-B 打包记录完全一致。560 个 ZIP entry；PasswordPolicy、EmbeddingOpenAiApi、SpringAiEmbeddingClient、Milvus 三个生产类及 DeepSeek Client 均存在。
- 100 个打包依赖中，99 个与最新 Surefire classpath 中当前本机 JAR 逐字节一致；另一个为 Boot 打包工具 `spring-boot-jarmode-tools-3.5.16.jar`，不是测试 classpath 业务依赖。实际保留 Spring AI 1.1.8、Milvus 2.6.20、Spring Web 6.2.19、crypto 6.5.11；无依赖升级。
- JAR 中未发现 application-local/test/test-real、.env 或私钥文件名；`.jar.original` 为历史 repackage 前产物。打包内容检查不证明应用真实启动或模型可用。
- 两个 Git diff check、未跟踪文件尾空白／冲突标记、三份交付文档链接及保全检查均按最终状态复核。未执行 add、commit、push、reset、stash、clean、rebase 或 worktree 操作。

## 5. 未解决风险与停止条件

1. **历史超长密码兼容（保留 P2）**：超过 72 字节的登录／当前密码现在 400；历史散列不能确认原始长度。没有授权账号恢复方案前，不截断 fallback、不批量改密，不承诺所有旧账号无影响。
2. **外部调用结果与耗时不确定（保留 P2）**：超时、502、断网、页面离开不证明供应商未处理或未收费。stale 只围栏数据库提交，不取消旧调用；无全链硬 deadline、全局并发许可或累计重试预算，不提供容量／P95／成本改善结论。
3. **真实 Milvus 及迟到孤儿未验收**：合成 gRPC 不能替代真实部署验证；一次补偿后仍可能迟到写入。MySQL eligibility 阻止无效证据，不等于物理孤儿最终清理。维护 CLI 的重试与 iterator 另行治理，禁止直接拿共享集合做清理实验。
4. **答案恢复键依赖原浏览器（B5-P2-03）**：换设备／清空存储后可能无法恢复 FAILED/stale 答案。保留原 requestId 与原文，不删 Answer 或绕过 CAS 解锁。
5. **日志保证有范围**：独立 Milvus／数据库 suppressed、请求解析错误、程序缺陷、APM/wire DEBUG 与外部代理不在本次全局脱敏保证内。真实资料、真实密钥不得作为故障回显样本。
6. **实测覆盖仍有限**：真实浏览器、真实模型协议／质量／费用、真实公网故障及生产负载均未验证；历史其他阶段 Smoke 不能自动覆盖当前累计树。现有本机缺少 local 配置，真实测试预算／专用凭据／evaluation 数据准备尚未确认。
7. **证据与发布**：基线仍有未提交文件，远端 main 不包含此树；不能仅拉远端启动测试并引用本报告。临时证据非持久审计仓库；开发者掌握状态仍未确认。

若真实测试出现越权、重复有效评价／Session 推进／弱项累计、非法模型输出被接受、FINAL 分数与报告不一致、既定恢复路径无法结束停滞、凭据进入可分享日志，立即停止相应场景并将准入降为 **NO_GO**。单纯缺环境或供应商不可达记 **BLOCKED/未执行**，先区分配置、供应商与代码问题；不把“尚未测试”写成失败或通过。

## 6. 后续真实人工／AI 测试准入

继续使用 [B5 真实测试计划](B5_REAL_WORLD_TEST_PLAN_20261113.md) 的 H01～H14、AI 场景和只读 SQL 核对，**以下 C6 条款更新其历史前提**：基线须包含 P2-A/P2-B；H14 的多字节密码缺陷已修，合法 72 字节应成功、超过 72 字节应 400 且数据库不变，不能仍预期 500；正常合成账号可使用合法多字节密码。原计划和旧报告保持原历史文本。

准入单当前均为“待下一轮实际准备／确认”，不在 C6 自动执行：

1. **版本与隔离 MySQL**：记录 HEAD、dirty 摘要或后续真实提交 SHA。人工／AI 运行用单独 `evaluation` profile、`ai_interview_evaluation`，核对当前 13 表、attempt_id／processing_attempt_id、唯一约束与 Schema。仅使用获授权隔离实例／库，不覆盖现有数据，不连正式 ai_interview。
2. **隔离 Milvus**：同一 evaluation 实例配置 `default/knowledge_chunk_vectors_evaluation`，与 local/test 分开；核对五字段 Schema、1024 维、COSINE/AUTOINDEX 和 embedding profile。记录实际地址与配置值，evaluation 默认 connect/request 为 15s/20s。未验证事项先按小规模 Smoke 单独验收，不自动 drop/recreate 集合。
3. **专用测试配置**：DeepSeek 与 Embedding 端点、专用 Key、模型、维度、批量、超时和测试 JWT/数据库凭据仅存受保护本地配置或进程环境。分别验证模型权限、网络和余额；不把配置存在当请求成功。不要混用 local/test/evaluation profile 来绕过 EnvironmentIsolationInitializer。
4. **API 额度及调用预算**：后续执行人明确货币硬上限、DeepSeek/Embedding 各自请求上限、数据准备预算、重试预留和停止阈值后才能开始。单场 EASY 的无恢复正常上界为 5 MAIN＋最多 5 FOLLOW_UP＋1 报告，即最多 11 次 DeepSeek 应用调用、最多 10 次 Query Embedding；知识入库按 chunk 批数另计，恢复／质量重复另计。此为请求计划，不是供应商 exactly-once 或金额保证；实际 usage／费用来自供应商账单，缺证据填 UNKNOWN，不推算收费结论。
5. **账号和合成数据**：USER A/B、获授权隔离 ADMIN；五类别 EASY 启用题，每题启用评分点总权重 100 且至少一个 CORE；合成知识上传后显式 process，核实 READY/chunk/vector 关联。用 runId 记录所有新建 ID，不用真实简历、个人答案或真实账号。
6. **清理方案**：先记录本 run 的 user/session/question/document/vector IDs 与关联关系；停止本轮写入和旧任务后，按清单、外键依赖和精确 ID 清理并验证非目标数据。只允许移除本轮拥有的临时实例／集合；不得清空共享库／集合／卷。迟到写入或补偿不确定时暂停清理并核实，独立维护 CLI 须另行授权，不能默认继承 Spring 安全策略。
7. **HTTP／数据库／日志证据**：记录 runId/caseId、时间、状态码、耗时、session/question/request/attempt/version 及去敏结果摘要。浏览器 Network/HAR 分享前移除 Authorization/Cookie/密码；数据库用原计划 Q1～Q6 只读核对 FINAL、唯一数量、均分、状态及弱项变化，不导出密码散列或 JWT；应用保留正常级别安全诊断及本地栈，禁止 wire DEBUG 打印报文。真实模型原输出只用于合成样本质量盲审，受限保存并关联版本，不能直接进普通日志或 Git。
8. **执行顺序**：单操作者、最多一个主动评分／报告任务，先一场 EASY 再复核预算与证据；保留原浏览器恢复记录与默认 10m/10m/15m stale，不为了测试缩短到正常任务预算以下。质量重复使用新的业务请求／场次，幂等重放另测；未自然触发的追问分支记 NOT_OBSERVED，不无限付费重试。

普通自动化回归仍用 ai_interview_test／knowledge_chunk_vectors_test 并禁用外部调用。若后续另行授权 guarded Smoke，必须 Maven/Surefire、临时对应 RUN_REAL_* 和测试自己的 local,test-real；AI baseline 仅 evaluation + RUN_REAL_AI_BASELINE。本轮所有真实模型／Embedding／Milvus 调用数为 0。

## 7. Git 提交规划（仅计划，未暂存）

依赖顺序：保留现有 `004e434 → f764c08 → 01ac774`，其后建议 **B4-D → B4-E → B5-P2-A → B5-P2-B → 工程文档** 五次提交。不得重写前三个提交，不使用整仓 `git add .`。提交与推送是两个独立授权事项，本轮均不执行。

各代码组与对应历史已验证快照逐字节一致（11/5/21/7 文件）；B4-D 有 1732 后端全量，B4-E 有前端 317／后端定向123，P2-A 有1780／前端336，P2-B 有1832。C6 没有重新创建或编译中间提交；合理依赖来自历史快照、引用关系及已核验的当批测试，不能称为新中间提交测试通过。

以下清单以仓库根为相对基准；hunk 标记来自 C6 入场 `git diff --unified=0 HEAD`，`-` 为 HEAD 行、`+` 为当前行。每行所列 **全部 hunk 一起归该批**；新增文件完整归属。实际提交前若文件变化，须重算，不能机械按过期行号暂存。

### 7.1 B4-D（11 个文件）

建议主题：`fix: bound Milvus SDK attempts and initialization deadline`。

| 文件 | HEAD→当前 hunk／归属 |
| --- | --- |
| [docs/verification/B4D_MILVUS_RETRY_DEADLINE_20261113.md](../../docs/verification/B4D_MILVUS_RETRY_DEADLINE_20261113.md) | 新增完整文件 |
| [src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusCollectionInitializer.java](../../src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusCollectionInitializer.java) | `@@ -55,4 +55,4 @@` |
| [src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusConfiguration.java](../../src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusConfiguration.java) | `@@ -4,0 +5,2 @@`；`@@ -9,0 +12,3 @@`；`@@ -45 +50,18 @@` |
| [src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusProperties.java](../../src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusProperties.java) | `@@ -45 +45 @@`；`@@ -47,3 +47 @@`；`@@ -52 +50 @@`；`@@ -54,3 +52,8 @@` |
| [src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeAttemptFencingIntegrationTest.java](../../src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeAttemptFencingIntegrationTest.java) | `@@ -188,0 +189,57 @@` |
| [src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalServiceTest.java](../../src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalServiceTest.java) | `@@ -61,0 +62,22 @@` |
| [src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusCollectionInitializerTest.java](../../src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusCollectionInitializerTest.java) | `@@ -54,0 +55,9 @@` |
| [src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusConfigurationTest.java](../../src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusConfigurationTest.java) | `@@ -4,0 +5,2 @@`；`@@ -87,0 +90,3 @@` |
| [src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusGrpcDeadlineTest.java](../../src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusGrpcDeadlineTest.java) | 新增完整文件 |
| [src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusPropertiesTest.java](../../src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusPropertiesTest.java) | `@@ -175,0 +176,11 @@` |
| [src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusSdkRetryBehaviorTest.java](../../src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusSdkRetryBehaviorTest.java) | 新增完整文件 |

### 7.2 B4-E（5 个文件）

建议主题：`fix: expose explicit recovery for generating reports`。

| 文件 | HEAD→当前 hunk／归属 |
| --- | --- |
| [docs/verification/B4E_REPORT_STALE_RECOVERY_20261113.md](../../docs/verification/B4E_REPORT_STALE_RECOVERY_20261113.md) | 新增完整文件 |
| [frontend/src/features/interview/composables/useInterviewReport.ts](../../frontend/src/features/interview/composables/useInterviewReport.ts) | `@@ -11 +10,0 @@`；`@@ -13 +11,0 @@`；`@@ -32,0 +31,5 @@`；`@@ -70,2 +72,0 @@`；`@@ -89 +90,4 @@`；`@@ -151 +155 @@`；`@@ -246 +250 @@`；`@@ -252,0 +257 @@`；`@@ -265,0 +271,11 @@`；`@@ -268,3 +284 @@`；`@@ -278 +292 @@`；`@@ -282,11 +296,2 @@`；`@@ -311 +316,10 @@`；`@@ -316 +330 @@`；`@@ -320,4 +334,3 @@`；`@@ -338 +351 @@`；`@@ -449,0 +463 @@`；`@@ -453,0 +468 @@` |
| [frontend/src/features/interview/copy/reportCopy.ts](../../frontend/src/features/interview/copy/reportCopy.ts) | `@@ -16,0 +17,4 @@` |
| [frontend/src/features/interview/views/InterviewReportView.vue](../../frontend/src/features/interview/views/InterviewReportView.vue) | `@@ -17 +17 @@`；`@@ -26,0 +27 @@`；`@@ -29,0 +31 @@`；`@@ -178,0 +181,11 @@` |
| [frontend/tests/InterviewReportView.spec.ts](../../frontend/tests/InterviewReportView.spec.ts) | `@@ -1 +1,2 @@`；`@@ -4,0 +6,3 @@`；`@@ -13,0 +18 @@`；`@@ -30,0 +36,2 @@`；`@@ -64,0 +72,4 @@`；`@@ -81,0 +93,2 @@`；`@@ -164,0 +178,313 @@` |

### 7.3 B5-P2-A（21 个文件）

建议主题：`fix: enforce password UTF-8 byte limits`。

| 文件 | HEAD→当前 hunk／归属 |
| --- | --- |
| [docs/verification/B5_P2A_PASSWORD_UTF8_BOUNDARY_20261113.md](../../docs/verification/B5_P2A_PASSWORD_UTF8_BOUNDARY_20261113.md) | 新增完整文件 |
| [frontend/src/features/auth/validation/authFormRules.ts](../../frontend/src/features/auth/validation/authFormRules.ts) | `@@ -5 +5 @@`；`@@ -9,0 +10 @@`；`@@ -14,0 +16,5 @@`；`@@ -38 +44 @@`；`@@ -41,2 +47,5 @@`；`@@ -48 +57 @@`；`@@ -51,2 +60,2 @@` |
| [frontend/src/features/auth/views/LoginView.vue](../../frontend/src/features/auth/views/LoginView.vue) | `@@ -151 +150,0 @@` |
| [frontend/src/features/auth/views/RegisterView.vue](../../frontend/src/features/auth/views/RegisterView.vue) | `@@ -156 +155,0 @@`；`@@ -173 +171,0 @@` |
| [frontend/src/features/profile/composables/useProfile.ts](../../frontend/src/features/profile/composables/useProfile.ts) | `@@ -13 +13 @@`；`@@ -161 +160,0 @@` |
| [frontend/src/features/profile/views/ProfileView.vue](../../frontend/src/features/profile/views/ProfileView.vue) | `@@ -26 +25,0 @@`；`@@ -96 +94,0 @@`；`@@ -114 +111,0 @@`；`@@ -132 +128,0 @@` |
| [frontend/tests/LoginView.spec.ts](../../frontend/tests/LoginView.spec.ts) | `@@ -206,0 +207,20 @@` |
| [frontend/tests/ProfileView.spec.ts](../../frontend/tests/ProfileView.spec.ts) | `@@ -309,0 +310,30 @@` |
| [frontend/tests/RegisterView.spec.ts](../../frontend/tests/RegisterView.spec.ts) | `@@ -99,0 +100,24 @@` |
| [frontend/tests/authFormRules.spec.ts](../../frontend/tests/authFormRules.spec.ts) | `@@ -19 +19 @@`；`@@ -35,0 +36,36 @@` |
| [src/main/java/com/kun/aiinterview/auth/dto/LoginRequest.java](../../src/main/java/com/kun/aiinterview/auth/dto/LoginRequest.java) | `@@ -2,0 +3,2 @@`；`@@ -21 +23,2 @@` |
| [src/main/java/com/kun/aiinterview/auth/dto/RegisterRequest.java](../../src/main/java/com/kun/aiinterview/auth/dto/RegisterRequest.java) | `@@ -2,0 +3,2 @@`；`@@ -25 +27,2 @@` |
| [src/main/java/com/kun/aiinterview/auth/service/AuthService.java](../../src/main/java/com/kun/aiinterview/auth/service/AuthService.java) | `@@ -6,0 +7 @@`；`@@ -34,0 +36 @@`；`@@ -66,0 +69 @@` |
| [src/main/java/com/kun/aiinterview/common/validation/PasswordPolicy.java](../../src/main/java/com/kun/aiinterview/common/validation/PasswordPolicy.java) | 新增完整文件 |
| [src/main/java/com/kun/aiinterview/user/dto/ChangePasswordRequest.java](../../src/main/java/com/kun/aiinterview/user/dto/ChangePasswordRequest.java) | `@@ -2,0 +3,2 @@`；`@@ -18 +20 @@`；`@@ -21 +23,2 @@` |
| [src/main/java/com/kun/aiinterview/user/service/UserService.java](../../src/main/java/com/kun/aiinterview/user/service/UserService.java) | `@@ -3,0 +4 @@`；`@@ -46,0 +48,2 @@` |
| [src/test/java/com/kun/aiinterview/auth/PasswordSample.java](../../src/test/java/com/kun/aiinterview/auth/PasswordSample.java) | 新增完整文件 |
| [src/test/java/com/kun/aiinterview/auth/PasswordUtf8BoundaryIntegrationTest.java](../../src/test/java/com/kun/aiinterview/auth/PasswordUtf8BoundaryIntegrationTest.java) | 新增完整文件 |
| [src/test/java/com/kun/aiinterview/auth/PasswordUtf8BoundaryTest.java](../../src/test/java/com/kun/aiinterview/auth/PasswordUtf8BoundaryTest.java) | 新增完整文件 |
| [src/test/java/com/kun/aiinterview/auth/controller/AuthControllerTest.java](../../src/test/java/com/kun/aiinterview/auth/controller/AuthControllerTest.java) | `@@ -274 +274 @@`；`@@ -282 +282 @@` |
| [src/test/java/com/kun/aiinterview/user/controller/UserControllerIntegrationTest.java](../../src/test/java/com/kun/aiinterview/user/controller/UserControllerIntegrationTest.java) | `@@ -155 +155 @@` |

### 7.4 B5-P2-B（7 个文件）

建议主题：`fix: sanitize embedding upstream failures`。

| 文件 | HEAD→当前 hunk／归属 |
| --- | --- |
| [docs/verification/B5_P2B_EMBEDDING_LOG_SAFETY_20261113.md](../../docs/verification/B5_P2B_EMBEDDING_LOG_SAFETY_20261113.md) | 新增完整文件 |
| [src/main/java/com/kun/aiinterview/knowledge/embedding/EmbeddingConfiguration.java](../../src/main/java/com/kun/aiinterview/knowledge/embedding/EmbeddingConfiguration.java) | `@@ -4,0 +5 @@`；`@@ -90,6 +91 @@` |
| [src/main/java/com/kun/aiinterview/knowledge/embedding/springai/EmbeddingOpenAiApi.java](../../src/main/java/com/kun/aiinterview/knowledge/embedding/springai/EmbeddingOpenAiApi.java) | 新增完整文件 |
| [src/main/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingClient.java](../../src/main/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingClient.java) | `@@ -2,0 +3 @@`；`@@ -15 +16,10 @@`；`@@ -113,3 +123,7 @@`；`@@ -117,2 +131,2 @@`；`@@ -122,0 +137,28 @@`；`@@ -191,4 +233 @@` |
| [src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java](../../src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java) | `@@ -32,0 +33,2 @@`；`@@ -33,0 +36 @@`；`@@ -95,0 +99 @@`；`@@ -103,0 +108 @@`；`@@ -254,0 +260,96 @@`；`@@ -460 +561 @@` |
| [src/test/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingClientTest.java](../../src/test/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingClientTest.java) | `@@ -426 +426,2 @@`；`@@ -432 +433,2 @@` |
| [src/test/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingLogSafetyTest.java](../../src/test/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingLogSafetyTest.java) | 新增完整文件 |

### 7.5 docs（6 个文件）

建议主题：`docs: close reliability repair phase and record commit plan`。

| 文件 | HEAD→当前 hunk／归属 |
| --- | --- |
| [AGENTS.md](../../AGENTS.md) | `@@ -391,0 +392,6 @@` |
| [docs/engineering/IMPROVEMENT_LEDGER.md](../../docs/engineering/IMPROVEMENT_LEDGER.md) | 未跟踪完整累计文档；不按历史批次机械拆 hunk |
| [docs/engineering/SOURCE_LEARNING_BACKLOG.md](../../docs/engineering/SOURCE_LEARNING_BACKLOG.md) | 未跟踪完整累计文档；不按历史批次机械拆 hunk |
| [docs/verification/B5_0_SYSTEM_READINESS_REVIEW_20261113.md](../../docs/verification/B5_0_SYSTEM_READINESS_REVIEW_20261113.md) | 新增完整文件 |
| [docs/verification/B5_REAL_WORLD_TEST_PLAN_20261113.md](../../docs/verification/B5_REAL_WORLD_TEST_PLAN_20261113.md) | 新增完整文件 |
| [docs/verification/C6_RELIABILITY_PHASE_CLOSEOUT_20261113.md](../../docs/verification/C6_RELIABILITY_PHASE_CLOSEOUT_20261113.md) | 新增完整文件 |

### 7.6 共享文件与明确排除项

- `FullInterviewBusinessOfflineTest.java`：B4-B/C 增量已在本地提交。HEAD→工作区只有 P2-B：新增 Embedding imports、properties 注入、sentinel、两个测试／useHttpEmbedding，以及共享日志断言扩展。共 6 个零上下文 hunk（见上表），应连同 P2-B 三个生产文件及两个其他测试整体提交；不得重新拆出 B4-B/C 或把 helper／imports 留到另一提交。
- `DeepSeekEvaluationClient.java` 与原专项：当前无未提交 diff。B4-B 安全边界在 f764c08，B4-C 分类在 01ac774，按现有历史保留，不再次拆分。
- 两份工程台账从建档以来一直未跟踪，已合并多批记录；在最后一个文档提交完整纳入，避免凭空重构过去没有提交过的台账中间版。AGENTS 只有既有 6 行维护规范 hunk，可归同一文档组；它不是 C6 新写的内容。
- `authFormRules.ts` 的常量改名、三视图移除 maxlength、useProfile 导出移除及相应测试归同一 P2-A 组；PasswordPolicy 与引用 DTO/Service 同组。不得只提交调用方造成缺类／缺导出。
- Embedding API 子类与 Configuration 接线、适配器及测试同组；Milvus Configuration/Properties/Initializer 与相关测试同组。pom／SQL 没有本轮 hunk，无需凭空拆依赖提交。

明确不进入这五个提交：`mvnw` mode、`compose.mac.local.yaml`、根 `.DS_Store`、`docs/.DS_Store`、`docs/design/MQ_ARCHITECTURE_FEASIBILITY_REVIEW_20261113.md`、ignored target/dist/node_modules、本机配置／密钥及 `/private/tmp` 证据。前五项都是既有用户文件，继续留在原位置；MQ 文档不属于本次可靠性收口授权范围。

未来获准执行提交时，只按精确清单选择文件／hunk，先检查暂存 diff；若组内源码有新变化，重跑对应编译／聚焦检查。最后核对完整树与此基线，再按实际 SHA 更新台账。未获推送授权不得 push，也不能将本计划写成已提交。

## 8. 工程记录、学习与交付

本轮正式生成／更新的实际绝对路径只有：

1. `/Users/kun/Developer/projects/ai-interview-system/docs/verification/C6_RELIABILITY_PHASE_CLOSEOUT_20261113.md`：新增累计审查、复用证据、风险、准入和文件／hunk 提交计划。
2. `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/IMPROVEMENT_LEDGER.md`：追加 C6 累计审查记录，校正当前状态及历史候选的后续结果；保留原验收事实。
3. `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/SOURCE_LEARNING_BACKLOG.md`：新增 L-C6-01～03，覆盖证据复用、跨批边界、提交依赖／真实准入；总计 63 条，全部掌握未确认。

学习建议先读 L-B4B/C、L-B4D、L-B4E、L-B5P2A/B 的具体源码，再用 L-C6-01～03 独立复算 XML、解释 1→2→2 调用与提交权、按 hunk 复述提交边界。学习完成需开发者本人讲解、复现或独立修改证据；Codex 编写代码、审查与测试通过均不能自动升级掌握状态。

本轮停止于工程基线和提交计划。没有开启真实测试、准备付费调用、修改业务数据、部署、暂存、提交或推送。下一步仅在新的明确任务中完成第 6 节实际准入准备，或执行经授权的精确 Git 提交。
