# B5-P2-B：Embedding 上游错误日志脱敏

执行日期：2026-11-13（Asia/Shanghai）。仓库：`/Users/kun/Developer/projects/ai-interview-system`。

## 1. 范围、基线与结论

**结论：B5-P2-B已完成技术验收。** 最终完整回归1832/0/0/19、打包及差异检查通过；本轮仍为未暂存、未提交工作区，真实模型／Milvus未验证。

本批修复 B5-0 的 B5-P2-02：Embedding HTTP 错误正文、网络异常链及供应商可控模型名可能进入服务器日志。采用离线 Mock HTTP 复现、边界转换、实际 Logback 捕获和隔离 MySQL 回归，没有调用真实 DeepSeek、Embedding 或 Milvus。

前置阅读：根 AGENTS.md、[B5-0](B5_0_SYSTEM_READINESS_REVIEW_20261113.md)、[B5-P2-A](B5_P2A_PASSWORD_UTF8_BOUNDARY_20261113.md)、[B4-B](B4B_DEEPSEEK_LOG_SAFETY_20261112.md) 和两份工程台账。原报告均保留，不将本轮结果反写为历史证据。

- 分支 main，HEAD `01ac774caff491b978dd2268e8b427355a64b97a`。本轮 GitHub 连接器 `fetch_commit(main)` 实时核验远端 main 为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，祖先比较 0 / 3；未 fetch 或修改引用，不推断其他远端分支。
- 入场 47 个已修改／未跟踪文件建立 SHA-256 和 mode 快照，含 B4-D、B4-E、B5-P2-A、AGENTS、mvnw、Compose 及其他文档。仅本轮获授权的两份工程台账允许更新，其余原有文件保持不变。
- 本批没有提交 SHA，未暂存、未提交、未推送；未执行 add、commit、push、reset、stash、clean 或 rebase。
- 生产变更仅 3 个 Embedding 文件；业务状态机、SQL、Schema、Collection、模型配置、DeepSeek、密码、前端及外部测试门禁无本批修改。

## 2. 实际异常传播链

审查本机 Maven 依赖的 Spring AI 1.1.8 JAR，并留存 OpenAiApi、OpenAiEmbeddingModel、RetryUtils 的 javap 结果；同时核对对应版本官方源码：

- [OpenAiApi 1.1.8](https://github.com/spring-projects/spring-ai/blob/v1.1.8/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/api/OpenAiApi.java)：`embeddings()` 使用 RestClient `retrieve().toEntity(...)`，默认错误处理器来自 RetryUtils。
- [RetryUtils 1.1.8](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-retry/src/main/java/org/springframework/ai/retry/RetryUtils.java)：默认错误处理器读取响应正文，拼入数字状态与消息；4xx 抛 NonTransientAiException，5xx 抛 TransientAiException。
- [OpenAiEmbeddingModel 1.1.8](https://github.com/spring-projects/spring-ai/blob/v1.1.8/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiEmbeddingModel.java)：`call()` 在 RetryTemplate 内调用 API，随后转换 metadata/data；空 body 时 WARN 记录 request，null data/item 会在转换中产生空指针。

本项目明确传入自己构建的 `maxAttempts=1`、`noBackoff` RetryTemplate，没有使用 RetryUtils 默认的 10 次重试及 WARN listener。因此不能把框架的默认重试次数当成本项目实际次数。

改前链路：

`EmbeddingConfiguration.createOpenAiApi → Spring AI 默认 HTTP 错误处理／RestClient 解码 → OpenAiEmbeddingModel.call → SpringAiEmbeddingClient.requestBatch catch(RuntimeException) → new ExternalServiceException(固定消息, 原始cause) → 业务失败收尾 → GlobalExceptionHandler.log.error(完整异常)`。

`ExternalServiceException` 双参构造器直接保留 cause；GlobalExceptionHandler 对外始终返回 HTTP 502、`code=502`、`message=外部服务暂时不可用`、`data=null`，但服务器日志渲染整条 cause/suppressed。`validateResponseModel()` 还直接拼接配置模型和供应商响应模型，后者可带换行／控制字符。其他结果校验仅使用本地固定文案、已解码整数和维度，不复制任意供应商文本。

知识文档处理链先标记失败再重抛；评价经 Retrieval/Orchestration/Workflow 收尾到 FAILED 后重抛，最终由同一 handler 记录。补偿或数据库失败可能另添 suppressed，这是独立的收尾异常契约，未改为全部吞掉。

## 3. 修改前确定性复现

证据根目录：`/private/tmp/ai-b5p2b-20261113/`。生产代码未改时先运行新增测试。只用以下合成数据：

- `B5_P2B_SENSITIVE_RESPONSE_SENTINEL`
- `B5_P2B_FAKE_API_KEY`
- `B5_P2B_PRIVATE_EMBEDDING_INPUT`
- CR/LF、ESC、NUL 和 `ERROR FORGED_LOG_RECORD`

测试绑定生产 Embedding 配置工厂到 MockRestServiceServer，经模拟 Controller 和真实 GlobalExceptionHandler；根 Logger 的 ListAppender 捕获应用及 Spring AI 日志，PatternLayout 使用 `%level %logger - %msg%n%ex{full}`。另保存 Java printStackTrace 和 Logback throwable proxy；Mock HTTP 的期望次数为一次。

两轮改前观察测试均 **25/0/0/0，退出 0**。这些测试断言客户端安全并记录泄漏观测，通过不表示改前安全：

1. `baseline`：六类 HTTP 状态、七类网络故障、十二种响应异常。14 项日志及异常栈含 sentinel：HTTP 400/401/403/429/500/503 六项；连接拒绝、DNS、连接超时、请求超时、Socket 超时、普通 IO、嵌套超时七项；恶意响应模型一项。
2. `baseline-json`：将两个 JSON 夹具改为未加引号的 model token 和含 sentinel 的非法 index 字符串，验证 Jackson 诊断真正包含敏感内容。相同 25 项中 **16 项**日志／栈含 sentinel；前述十四项加两项解析错误。两轮不能相加为独立用例总数。

具体观察：

- 六类 HTTP 的外层 message 安全，但 cause 中 NonTransientAiException/TransientAiException 带完整合成正文；完整栈和最终 Logback 均泄漏。
- 七类网络异常经 ResourceAccessException 包装，message/cause/suppressed 可包含合成敏感文本；日志同样泄漏。
- 恶意 model 直接进入最外层 message，包含控制字符，存在日志注入通道。
- 非法 JSON／index 在第二轮通过真实 Jackson 路径实证泄漏；首轮解析样本未泄漏，保留该观察，不以静态猜测替换结果。
- empty body 与 JSON null 的 Spring AI WARN 确实执行，但实际 request 输出为 `EmbeddingRequest@...` 对象标识，**本次没有输入文本泄漏**。前期源码阅读发现风险后，以此实测校正结论。
- null data/item 引起 Spring AI NPE，被旧 catch-all 包成外部错误；这两项没有 sentinel 泄漏。修复仍须保留非法响应拒绝，不能简单删 catch 后让它们变为 500。
- 25 项客户端均为固定安全 502，未泄漏 sentinel、假 Key 或输入。

证据文件：`baseline.log`、`baseline-json.log`、各自 `*-reports/` 与 `*-observations.json`、`baseline-captured.txt`、两份 `SpringAiEmbeddingLogSafetyTest.before*.java`。正式测试已换成安全断言，没有允许泄漏的运行开关。原始证据只有合成数据，临时目录仍不应未经审阅整体发布。

## 4. 最小修复与取舍

新增 `EmbeddingOpenAiApi extends OpenAiApi`，仍由 Spring AI 原实现发送请求、解析 JSON，业务端口依然是 EmbeddingClient，模型依然是 OpenAiEmbeddingModel。

1. 专属 ResponseErrorHandler 在默认错误正文读取前拒绝非 2xx，仅保留数字 HTTP 状态与固定 stage/category；不读取正文、statusText 或诊断 headers，不挂原始 cause/suppressed。
2. `embeddings()` 调用父类后仅检查空 body、null data、null item，保证在 Spring AI 记录空响应或解引用之前拒绝。其余模型、索引、维度、数值、usage 校验继续由现有项目适配器负责，避免复制整个框架转换流程。
3. `SpringAiEmbeddingClient.requestBatch()` 只捕获 RestClientResponseException／RestClientException。前者仅取数字状态作为自定义 handler 的防御性兜底；后者只按类型分类，不复制 message、任意类名、原始 cause 或 suppressed。
4. 分类：HTTP_STATUS、CONNECT_TIMEOUT、TIMEOUT、CONNECTION_ERROR、RESPONSE_JSON、IO_ERROR、HTTP_CLIENT_ERROR；阶段为 embedding_http。响应形状及模型不匹配另标 embedding_response／RESPONSE_SHAPE、MODEL_MISMATCH。只做诊断，不代表允许自动重试。
5. cause 最多检查 10 层，嵌套超时优先于解析／普通 IO 分类；环形链不会无限循环，超过深度的具体原因不猜测。原链完全丢弃，只保留新异常本地栈。
6. 模型不匹配使用固定文案，不复制两个模型名。无需对任意文本逐字符替换，因为该边界根本不允许任意上游文本进入可记录消息；CR/LF、ESC、NUL 等被一并排除。
7. BusinessException、IllegalStateException、NullPointerException、IllegalArgumentException 等独立业务／程序错误原样传播。没有无条件吞掉所有 RuntimeException，也没有将失败伪装为成功。

没有修改 GlobalExceptionHandler 或通用 ExternalServiceException。全局仍保留完整本地栈及既有 HTTP 合同；单次批请求仍 maxAttempts=1，批量切分不是失败自动重试。

未采用的方案：只清理外层 message 会保留危险 cause；只改全局日志会留下其他记录入口并损失其他模块诊断；捕获所有 NPE/RuntimeException 无法区分非法响应与程序缺陷；复制整个 OpenAiEmbeddingModel 或改用另一个 HTTP 客户端超出必要范围。小型 API 子类针对当前框架转换前的真实缺口，不新增通用错误框架或供应商层级。

## 5. 文件清单

新增：

- `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/embedding/springai/EmbeddingOpenAiApi.java`：HTTP 状态与框架转换前响应形状边界。
- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingLogSafetyTest.java`：40 项日志／异常／响应安全专项。
- `/Users/kun/Developer/projects/ai-interview-system/docs/verification/B5_P2B_EMBEDDING_LOG_SAFETY_20261113.md`：本报告。

修改：

- `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/embedding/EmbeddingConfiguration.java`：createOpenAiApi 接入专属 API；保留 Jackson、超时、模型及单次 RetryTemplate 配置。
- `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingClient.java`：requestBatch 的受控异常转换、safeFailureCategory、validateResponseModel 去除回显。
- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingClientTest.java`：将“任意 RuntimeException 必须保留 cause”改为真实类型的超时分类与无 cause 契约；原输入拒绝及其他结果校验测试保留。
- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java`：新增 11 类 Embedding 故障及显式答案恢复、1 项知识文档失败／恢复；实际 Spring AI + Mock HTTP 接入原业务链，日志断言增加本轮 sentinel。
- `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/IMPROVEMENT_LEDGER.md`：新增 B5-P2-B 实际工程记录。
- `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/SOURCE_LEARNING_BACKLOG.md`：新增 3 条待学习任务，更新旧候选后续状态。

## 6. 验证证据

日志专项 40 项同时覆盖公开 HTTP 响应、异常 message/toString、cause/suppressed、Java 完整 stack trace、Logback throwable proxy 和最终 PatternLayout；要求保留一条应用 ERROR、固定诊断及本地调用栈，不能以关日志通过。捕获范围包含根 Logger 的框架日志。

除改前矩阵外，新增不允许读取 error body/statusText 的 InputStream 断言、302/599、读取成功响应流时超时、JSON 包装下的连接超时、原始 RestClient HTTP 异常兜底、环形／超深 cause、无类型客户端异常、四类业务／程序错误保真、成功向量和被忽略的恶意 metadata。每项只允许一次 Mock HTTP 请求。没有把类型化 IO 注入说成真实网络延迟测量。

真实 MySQL 业务类由 112 项增为 124 项：

- HTTP 六种状态、连接失败、超时、非法 JSON、恶意模型名、空响应共 11 种 Embedding 故障：FAILED、Session 不推进、无评价／报告／弱项；不得更改原 payload；相同请求显式恢复成功，重放不再调用 Embedding，只有一条答案及评价。恢复后经过 Milvus 搜索替身和真实 MySQL 知识资格校验；Retrieval 输入断言不含用户答案。
- 知识文档处理：Embedding 503 后 FAILED、无 chunk、无向量写入／无不必要补偿、固定 error_message；显式重试后 READY、一个 ACTIVE chunk、一次向量写入替身调用。
- 本类有外围 @Transactional，不能单独证明跨请求提交或事务外模型调用。独立事务／并发／fencing 测试另行通过；Milvus 网络仍为替身。
- 原有 KnowledgeDocumentProcessingServiceTest 23、ProcessingTransactionServiceTest 9、KnowledgeAttemptFencingIntegrationTest 19、KnowledgeRetrievalServiceTest 21、RawHitTest 1、ThresholdTest 11 均通过，涵盖补偿／写入结果不确定／旧 attempt／MySQL 二次校验。
- DeepSeekLogSafetyTest 36、InterviewPostClaimFailureIntegrationTest 3、InterviewConcurrentReplayIntegrationTest 18、WorkflowTransactionServiceIntegrationTest 6、StaleRecoveryCasIntegrationTest 3 均通过；没有改动这些生产逻辑。

环境实测 macOS 26.7.1 ARM64、Temurin 21.0.13、Maven Wrapper 3.9.16。新建唯一容器 `ai-interview-b5p2b-20261113-mysql`：MySQL 8.0.45、127.0.0.1:65293、tmpfs 数据目录、+08:00，只初始化 ai_interview_test 的 13 张业务表及系统库。原 SQL 仅在内存副本替换 CREATE DATABASE/USE 名称，不连接正式 local/evaluation，不修改仓库 Schema。

临时执行器 `run.py` 清除所有继承的 RUN_REAL_*、SPRING_*、MILVUS_*、EMBEDDING_*、DEEPSEEK_*、JEV_*、AI_JUDGE_*、旧 DB/TEST_DB 及 JVM/Maven 覆盖；仅向子进程提供专用数据库和 Mockito javaagent，DEEPSEEK_ENABLED=false。MILVUS_ENABLED 与所有真实服务门禁 unset；测试显式开启业务装配时仍使用模型／Milvus 替身，EnvironmentIsolationInitializer 不变。普通沙箱的 Docker 只读检查最初受限，随后在允许的执行环境继续，不是业务测试失败。

以下为本轮实际 T/F/E/S（总数含跳过，各轮不可相加）：

1. `baseline`：25/0/0/0，退出 0，改前第一组观察。
2. `baseline-json`：25/0/0/0，退出 0，增强 JSON 泄漏夹具后改前观察。
3. `client-focused`：195/0/0/1，退出 0；1 项为 RealEmbeddingSmokeTest 门禁跳过。
4. `service-focused`：302/0/0/0，退出 0；真实隔离 MySQL + Mock HTTP/Mock/单测混合。
5. `full-regression`：1832/0/0/19，退出 0；首轮完整回归。
6. `final-regression`：**1832/0/0/19，139类，1813项非跳过，退出0**；最终审查仅将子类未使用的 chat 路径恢复为 Spring AI 原默认 `/v1/chat/completions`，再次运行完整回归对应交付树。
7. `package`：**退出0，BUILD SUCCESS**；`-DskipTests package` 不执行测试，测试计数不适用。执行器summary中的0/0/0/0仅表示没有执行测试，不能记为新一轮回归。
8. `git diff --check`、`git diff --cached --check`、新增文件空白/链接/冲突标记检查和保全检查：**退出0**，非功能测试。

实际命令统一由 `python3 /private/tmp/ai-b5p2b-20261113/run.py <标签> <Maven参数>` 调用；底层为：

```sh
sh mvnw -B -ntp -Dtest=SpringAiEmbeddingLogSafetyTest test
sh mvnw -B -ntp '-Dtest=*Embedding*Test,DeepSeekLogSafetyTest' test
sh mvnw -B -ntp -Dtest=FullInterviewBusinessOfflineTest,KnowledgeDocumentProcessingServiceTest,KnowledgeDocumentProcessingTransactionServiceTest,KnowledgeRetrievalServiceTest,KnowledgeAttemptFencingIntegrationTest,InterviewPostClaimFailureIntegrationTest,InterviewWorkflowServiceTest,InterviewWorkflowTransactionServiceIntegrationTest,EvaluationOrchestrationServiceTest,DeepSeekLogSafetyTest test
sh mvnw -B -ntp test
sh mvnw -B -ntp -DskipTests package
git diff --check
```

每轮先归档旧 Surefire，再复制该轮 XML；日志、`*-summary.json`、`*-reports/`、`*-suites.json` 独立保留，package 不覆盖已归档完整回归。没有删除、跳过或降低原测试来换取成功。

完整回归 19 项跳过：RealEmbeddingSmokeTest 1、RealMilvusVectorStoreSmokeTest 1、KnowledgePersistenceIntegrationTest 10、RealKnowledgeDocumentProcessingSmokeTest 2、RealKnowledgeDocumentProcessingFailureIntegrationTest 2、RealMilvusSchemaValidationSmokeTest 2、AiSystemBaselineEvaluationTest 1。普通 MyBatis/MySQL 测试确实执行，不属于这些 opt-in 开关。

## 7. 边界、剩余风险与停止点

- 没有真实模型、真实 Milvus、付费调用、真实网络故障／代理／TLS 或部署侧 APM 报文采集验收；Mock HTTP 与本次独立 MySQL 不能替代这些证据。未开展前端测试或部署，本轮没有前端改动。
- 日志保证限于当前 Embedding 边界和被测试的默认日志配置，不覆盖刻意开启的 wire DEBUG／外部拦截器打印原始请求；现有 DeepSeek 契约单独回归通过，不宣称全系统日志均安全。
- 知识处理／Workflow 可能添加独立数据库／Milvus 收尾异常为 suppressed，本轮不全面治理这些异常文本。内部程序错误保留原对象是有意的错误分类边界，调用者不应自行将敏感输入拼入编程异常。
- 10 层 cause 分类在过深链中可能失去具体类别，但输出仍为安全固定标签；本轮有环形／深链安全断言，不猜测缺少类型证据的 IO 是否超时。
- API 子类依赖 Spring AI 1.1.8 当前扩展点；升级依赖后需重跑形状／日志／单次调用合同。保留 Spring AI、1024 维生产配置、批量／顺序恢复、向量／usage 校验；未宣称性能或模型质量提升。
- 学习新增 L-B5P2B-01～03，全部待学习，工程测试成功不证明开发者掌握。B5-0 原报告的其他候选不因本批完成而自动实施。

完成本轮代码、完整回归、打包及三份工程档案后停止；无 Git 发布操作。

## 8. 最终收尾记录

- 两轮聚焦和两轮完整Maven日志均扫描本轮sentinel、假API Key、私密Embedding输入、私密知识内容标记，命中全部为0，见`/private/tmp/ai-b5p2b-20261113/log-scan.json`。完整本地栈仍记录，不是关闭日志所得。
- 打包产物：`/Users/kun/Developer/projects/ai-interview-system/target/ai-interview-system-0.0.1-SNAPSHOT.jar`；原始包：`/Users/kun/Developer/projects/ai-interview-system/target/ai-interview-system-0.0.1-SNAPSHOT.jar.original`。检查确认包含EmbeddingOpenAiApi、SpringAiEmbeddingClient及Spring AI1.1.8依赖，不含application-local.yaml或.env，见package-inspection.json。未部署。
- 47个入场文件中，两份工程清单按授权更新，另外45个正文及mode与入场相同；HEAD不变、暂存区为空，见preservation-check.json。当前新增／修改文件清单在第5节，不将B4-D/E/P2-A既有改动归入本批。
- 已核验唯一临时MySQL的任务标签、tmpfs、无持久数据卷及本批文档记录剩余0，随后删除该测试容器和随机凭据文件；共享容器、数据卷及工作区不清理。见cleanup.json。运行门禁没有写入持久配置。
- 新增学习条目全部标记待学习，清单60条；更新工程台账12条已开展批次。原B5-0报告保留当时未修事实，最新状态在本批台账及学习后续记录中说明。
- 本批原始日志／XML位于临时目录，可能被系统清理；正式报告与可执行测试源码提供长期可追溯摘要。无性能提升、真实服务成功或已发布声明。
