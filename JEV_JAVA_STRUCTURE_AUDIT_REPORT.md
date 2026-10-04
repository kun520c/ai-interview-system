# Jev 接入后的 Java 结构与 Dead Code 审查

审查日期：2026-10-03。审查基线：任务开始时的工作区，HEAD 为 `20a933a`。

本次先完成只读审查，再进行两批小范围修改。检查范围包含 180 个生产 Java 文件、120 个测试 Java 文件、13 个 Mapper XML、应用配置、Maven 配置以及 Jackson / Spring / MyBatis / 反射入口。没有调用在线 Jev、DeepSeek、Embedding 或 Milvus 来评价模型质量。没有执行暂存、提交或推送。

## A. Java 架构结论

```text
JAVA_STRUCTURE=PASS
```

| 层 / 类型 | 实际职责 | 结论 |
|---|---|---|
| JudgeProvider | 接收既有 Context / Prompt，返回未信任的 suggestion 和统一元数据 | 两个实际实现，构成合理切换边界；保留 |
| DeepSeekJudgeProvider | 调用既有 DeepSeekEvaluationClient，适配 Result 并补充 promptVersion | 没有复制 DeepSeek HTTP、JSON 或评分逻辑；保留 |
| JevJudgeProvider | 构造结构化问题、校验 Jev 判断、映射五维分数和覆盖、生成反馈、按 ID 合并、控制一次 fallback | 不访问 Mapper，不推进 Session，不处理 CAS 或业务事务 |
| JevClient | 单次 HTTP 请求、序列化 / 反序列化、响应信封检查和 HTTP / JSON 错误转换 | 没有业务评分计算；结构性信封检查符合 Client 边界 |
| JudgeProperties / JevProperties | 分别配置 provider 选择与 Jev 连接、模型、超时、confidence 阈值 | `ai.judge` 和 `ai.judge.jev` 绑定不同职责，没有重复配置实现 |
| JudgeConfiguration | 用简单 switch 创建唯一 JudgeProvider；只在 Jev 模式创建 Jev 配置和 Client | 默认仍为 DeepSeek；DeepSeek 关闭时不创建 Judge；没有额外 Factory 框架 |
| EvaluationOrchestrationService | 保持 Context → Retrieval / Trace → Prompt → Judge → Java 校验 / 总分 / 决策 → Evaluation 持久化 | 没有 Jev 分支或 Jev 特有业务逻辑；Workflow 和 CAS 边界不变 |

当前 `interview.evaluation.judge` 及其 `jev` 子包规模适当：顶层 4 个文件，Jev 子包 5 个文件。没有新增单类 `adapter` / `factory` / `wrapper` 目录。Request / Response 放在 Jev 包中容易阅读，无须为了示例再拆一个 dto 包。私有 Feedback / PointEvidence record 属于内部生成协议，不需要提升成额外公开 DTO。

没有发现需要删除的抽象父类、Builder、空 Service 或无转换职责的中间层。DeepSeekJudgeProvider 虽短，但承担供应商结果到统一 Judge 结果的适配以及 promptVersion 传递，有实际职责。

### 重复、异常与 fallback

- 0～4 到 0～20 的映射只有 `JevJudgeProvider.mapScore` 一个实现。验证阶段和组装阶段调用同一函数，没有两份公式；采用乘 5、HALF_UP，拒绝 null、NaN、Infinity 和超范围值，不钳制非法分数。
- merge 只有 `mergeFeedback` 一个实现；scoring point key 只有 `pointKey` 一个实现。已有 LlmEvaluationValidator 被复用，没有复制完整 Evaluation 校验器。
- Provider 在返回 Jev 组合结果前复用校验器，以便不合法的外部生成反馈触发完整 fallback；编排层仍对所有 Provider 结果执行统一可信边界校验。这是两处调用同一校验器，不是重复实现。
- 没有新增 JevClientException / JevApiException / JudgeException 等异常层级。外部 HTTP、JSON 和非法供应商响应使用既有 ExternalServiceException；内部序列化失败使用 IllegalStateException。
- Jev 外层只捕获 ExternalServiceException，没有 `catch (Exception)`。对现有 validator 的 IllegalArgumentException / IllegalStateException 转换仅限于该校验调用；NPE、IndexOutOfBoundsException 不被 fallback 吞掉。
- fallback 只有一个调用位置，配置明确传入 DeepSeekJudgeProvider。fallback 的异常直接向上抛出，不递归，不再次 fallback。
- Jev 成功：一次 Jev 请求 + 一次 DeepSeek 文本生成。Jev 判断阶段失败：一次完整 DeepSeek Judge，不调用文本生成。生成或 merge 阶段失败：已进行的一次生成尝试 + 一次完整 DeepSeek Judge；这是完整恢复结果所需的调用，没有额外循环或重试。
- fallback 直接返回完整 DeepSeek Result。新增断言同时检查 suggestion 身份、model、promptVersion、rawJson 和 evaluate 调用次数，避免保留局部 Jev 结果。

### 本次修复的边界缺陷

原 Choice 校验在遍历概率项时，会与 `probabilities.get(choice)` 比较。如果选中项为 null 且排序在有效替代项之后，就先拆箱 null，抛 NPE。修复前的两个回归用例均复现该问题：2 tests / 0 failures / 2 errors / 0 skipped。

修改仅将全部概率值的 `requireProbability` 检查前置，然后执行已有求和和比较。没有扩大 catch、修改评分规则或修改 confidence 策略。现在非法外部概率被明确转换为 ExternalServiceException，并触发一次完整 fallback。

### Evaluation merge 与边界

`mergeFeedback` 用 record 构造器显式组装新 LlmEvaluationSuggestion：五维分数、covered、followUpRecommended 全部取自 Jev decision；strengths、missingPoints、correction、证据文字、suggestedFollowUp 来自生成反馈。没有 setter 顺序、BeanUtils、Map 覆盖或 JSON 合并风险。生成协议不接受额外的评分 / covered 字段，覆盖尝试会使组合失败并返回完整 DeepSeek Judge 结果。

证据按 scoringPointId 匹配；缺失、重复、外来 ID 被拒绝。返回顺序遵循冻结的 scoring point 顺序。covered=true 必须引用 MAIN 或 FOLLOW_UP 回答中的连续原文，covered=false 的最终 evidence 为 null。

confidence 先验证有限值及 [0,1] 范围，再使用 `< minConfidence` 比较；阈值 0.0 接受 confidence=0.0。零、一个和多个评分点的 Provider 合并均有测试；零评分点仅是合成 Context 的组件边界验证，实际 EvaluationContextService 仍拒绝空评分快照，业务规则未变化。

最终总分仍由 EvaluationScoreCalculator 相加计算，FollowUpPolicy 仍保留 Java 否决权。FOLLOW_UP 模式不请求新的追问建议，也不能生成下一次追问。

## B. 安全删除内容

没有删除完整 Java 类、接口、Enum、Exception、DTO 或 Java 文件。

| 类 / 文件 / 代码 | 原用途 | 为什么确认无用 | 删除风险 |
|---|---|---|---|
| EvaluationOrchestrationService.buildAnswerEvaluation 的 prompt 参数及唯一调用处实参 | 旧实现读取 promptVersion | Jev 接入后版本来自 JudgeProvider.Result；方法体不再读取参数。私有方法无框架注解、反射或资源注册，唯一调用已同步清理 | SAFE |
| KnowledgeDocumentProcessingServiceTest.validDocument() | 返回 PROCESSING 文档的旧测试 helper | 精确名称只出现于定义；没有调用、注解、MethodSource、反射、Spring / MyBatis / Jackson / SPI 注册。其他 processingDocument helper 仍被使用 | SAFE |
| main 下 admin、llm、rag、report | 预留目录 | 删除前逐个确认普通目录、无 ReparsePoint，包含隐藏文件在内的条目数为 0；没有 Java、package-info 或资源 | SAFE |
| main/test 下 knowledge.embedding.dashscope | 旧 Embedding 实现迁移后的空目录 | 没有残留 Java 文件，当前 SpringAiEmbeddingClient 仍保留；空目录无框架注册或映射内容 | SAFE |
| test 下 konwledge.controller、mapper、service 及清空后的 konwledge 父目录 | 旧拼写目录 | 叶目录及最后的父目录分别在为空时删除，没有测试文件被删除 | SAFE |

共清理 10 个空目录：5 个生产目录、5 个测试目录。Git 不跟踪空目录，因此不会出现 10 条 Git 文件删除记录。

执行顺序：第一批清理参数 / 空目录并修复校验顺序 → package → Judge 聚焦测试；第二批删除已证明未使用的私有测试 helper → package → 该测试类；最后进行全量回归。

## C. 看似无用但保留的内容

| 文件 / 类型 | 为什么看似无引用 | 实际使用证据与保留理由 |
|---|---|---|
| AiInterviewSystemApplication | 没有其他生产类调用 main | Spring Boot 启动入口、组件扫描和测试的 SpringBootConfiguration 根 |
| AuthController、UserController、AdminQuestionController、AdminKnowledgeDocumentController、InterviewController | 不由业务 Java 显式调用 | RestController 和 HTTP Mapping 注册；保留全部 API |
| GlobalExceptionHandler | 处理方法没有普通 Java 调用 | RestControllerAdvice / ExceptionHandler 由 MVC 分发 |
| PasswordConfig | 没有其他生产类引用配置类名 | Configuration / Bean 提供 PasswordEncoder |
| RecoveryConfiguration、JwtConfiguration | 类体接近空壳 | EnableConfigurationProperties 分别启用 recovery / jwt 配置绑定，删除会丢失 Bean 注册 |
| JudgeConfiguration、DeepSeekConfiguration、EmbeddingConfiguration、MilvusConfiguration、SecurityConfiguration | 大部分仅被测试显式引用 | Spring 配置扫描和 Bean 注册；ConditionalOnProperty 控制真实基础设施是否创建 |
| MilvusCollectionInitializer | 没有生产 Java 类直接引用它 | Component + ApplicationRunner，启用 Milvus 时由 Boot 执行启动 Schema 检查 |
| MilvusVectorStoreClient、DeepSeekInterviewReportGenerator | 上层只引用接口 | Spring 按 VectorStoreClient / InterviewReportGenerator 类型注入实际实现 |
| 所有 Mapper interface、实体、映射 Enum / KnowledgeRetrievalRow | XML、生成代理或反射赋值可能没有普通方法调用 | 已检查 namespace、statement id、parameterType、resultMap / resultType 和 SQL 映射；保留 |
| RagHitLogMapper.listByBatchId、RagRetrievalBatchMapper.getByBatchId、UserWeaknessMapper.getByUserCategoryAndKnowledgePoint | 当前没有生产 service 调用这些读方法 | Mapper XML 有实际 statement；真实 MyBatis / 事务测试使用它们验证持久化、回滚和弱项更新，属于有效验证设施 |
| JevRequest / JevResponse 及嵌套 record、Feedback / PointEvidence、DeepSeek 内部 HTTP DTO | 部分成员没有 getter 调用或类为 private | HTTP 序列化、readValue、Jackson record 构造器和 JSON 字段协议实际使用 |
| JevProperties 的 AssertTrue 方法及其他配置校验方法 | 没有普通 Java 调用 | Jakarta Bean Validation 执行属性校验；不能删 |
| Utf8ByteSizeValidator.isValid / initialize、QuestionPageQuery 的 setter | 可能只有定义出现 | ConstraintValidator 生命周期、Spring 参数绑定实际调用 |
| EmbeddingClient、VectorStoreClient、InterviewReportGenerator | 当前分别只有一个生产实现 | 外部服务 / 报告生成的明确架构边界，且测试有 Mock / fake 使用；没有删除理由 |
| DeepSeekEvaluationResult、DeepSeekJsonCompletionResult | 和 JudgeProvider.Result 字段相近 | 仍是既有 Client 的返回契约，前者被 DeepSeek provider 适配，后者同时服务反馈生成和报告生成 |
| ValidatedEvaluationSuggestion | 与 LlmEvaluationSuggestion 形状相似 | 表达校验后的可信状态，使用 primitive 和防御复制，是 Java 可信边界而非无意义转发 |
| 其余测试配置、Mock helper、真实测试工具 | 通常没有生产引用 | 120 个测试类由 Surefire / JUnit 发现；已核查 MethodSource 与反射用法，保留有效基础设施 |

类型引用筛查中，68 个 Spring / 启动入口可到达全部 180 个生产文件，没有发现孤立的生产类群。这只是源码筛查证据；删除决定还结合了注解、配置、XML、JSON、反射、SPI 和测试核查，没有将 grep 的零引用直接视为可删证据。

项目没有自有 Class.forName / ServiceLoader 生产入口或 META-INF/services、spring.factories、AutoConfiguration.imports 注册文件；测试中的反射用于检查事务方法契约，相关方法全部保留。应用本地配置也进行了不输出值的类型引用检查，未修改或披露任何本地凭据。

## D. 未删除候选

- LIKELY_SAFE：未发现需要人工决定的类 / 接口删除候选。
- UNCERTAIN：未发现需要人工决定的类 / 接口删除候选。
- 没有生产调用但被真实 Mapper / 事务测试使用的三个查询方法，已经有保留证据，分类为 KEEP；不能把它们当作待删除垃圾代码。

不存在为了达到清理数量而删除类、扩大抽象或重排 package 的修改。

## E. 最终 Judge 架构

```text
EvaluationOrchestrationService
        |
        v
JudgeProvider
   |-- DeepSeekJudgeProvider（默认）
   |       `-- DeepSeekEvaluationClient.evaluate
   |
   `-- JevJudgeProvider
           |-- JevClient.evaluate（一次 HTTP）
           |       `-- 五维分数 / coverage / follow-up recommendation
           |-- DeepSeekEvaluationClient.completeJson（一次生成）
           |       `-- feedback / evidence / candidate follow-up
           |-- mergeFeedback（保留 Jev 结构化字段）
           `-- 外部失败或组合不合法
                   `-- fallback 一次 → DeepSeekJudgeProvider
                                      `-- 完整 DeepSeek Result

统一 Result → LlmEvaluationValidator → Java 总分 / 等级 / FollowUpPolicy
            → AnswerEvaluation → EvaluationOrchestrationResult
```

## F. 修改统计与验证

统计相对于本次审查开始时的工作区，不包含此前未提交的 Jev 接入改动。

```text
JAVA_FILES_ADDED=0
JAVA_FILES_MODIFIED=4
JAVA_FILES_DELETED=0
PACKAGES_DELETED=10
PRODUCTION_FILES_MODIFIED=2
```

| 修改文件 | 目的 |
|---|---|
| src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java | 前置全部概率值校验，避免非法外部响应产生 NPE |
| src/main/java/com/kun/aiinterview/interview/orchestration/EvaluationOrchestrationService.java | 删除不再使用的私有参数及实参 |
| src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProviderTest.java | 新增 7 个参数化执行用例；加强 fallback rawJson 断言 |
| src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeDocumentProcessingServiceTest.java | 删除 4 行未使用的私有 helper；保留全部测试和断言 |

另外新增本报告 Markdown 文件。执行日志、开始时的文件哈希、变更前源码快照和差异存放在忽略目录 `target/java-structure-audit/`。

| 执行 | 总计 | Passed | Failed | Errors | Skipped | 结果 |
|---|---:|---:|---:|---:|---:|---|
| 修复前 null Choice 概率复现 | 2 | 0 | 0 | 2 | 0 | 两个用例均复现 NPE |
| 第一批 `mvnw -B -ntp -DskipTests package` | — | — | — | — | — | BUILD SUCCESS；生产和测试源码编译、Boot JAR 打包，测试未执行 |
| Judge 聚焦测试 | 134 | 134 | 0 | 0 | 0 | PASS |
| 第二批最终 `mvnw -B -ntp -DskipTests package` | — | — | — | — | — | BUILD SUCCESS |
| KnowledgeDocumentProcessingServiceTest | 23 | 23 | 0 | 0 | 0 | PASS |
| 第一轮普通全量回归 | 1411 | 1392 | 1 | 0 | 18 | 执行环境清理错误，见下文 |
| 配置隔离测试重跑 | 2 | 2 | 0 | 0 | 0 | PASS |
| 最终普通全量回归 | 1411 | 1393 | 0 | 0 | 18 | BUILD SUCCESS；120 个测试类 |

Judge 聚焦选择：DeepSeekJudgeProviderTest（1）、JudgeConfigurationTest（12）、JevClientTest（17）、JevJudgeProviderTest（46）、JevScoreMappingTest（16）、DeepSeekEvaluationClientTest（19）、EvaluationOrchestrationServiceTest（23）。覆盖默认 DeepSeek、HTTP / JSON、fallback 次数与传播、映射端点 / 小数 / 非有限值、confidence=0、merge 不覆盖、覆盖证据、缺失 / 重复 / 外来评分点、Follow-up 和元数据持久化。

第一轮全量的唯一失败在 TestProfileExternalServiceIsolationTest：当前 PowerShell 将静态 `.NET SetEnvironmentVariable(name, null)` 调用转换为空字符串，变量仍存在，导致环境中的空 MILVUS_ENABLED 覆盖 YAML，Boolean 解析为 null。已独立验证：调用前 Exists=false；该调用后 Exists=true 且长度 0；`Remove-Item Env:MILVUS_ENABLED` 后 Exists=false。随后使用 Env provider 真正移除所有开关，原断言和配置不变，隔离测试及全量均通过。不是通过修改生产代码或弱化测试来解决。

最终全量使用 `.\mvnw.cmd -B -ntp test`。DB_URL / DB_USERNAME / DB_PASSWORD 从现有用户环境仅注入当前 Maven 子进程；所有 RUN_REAL_*、MILVUS_ENABLED、DEEPSEEK_ENABLED 和 AI_JUDGE_PROVIDER 均实际不存在，test profile 保持隔离。没有持久化环境变量或复制凭据到测试配置。

18 个跳过项全部来自受保护的真实服务测试：RealEmbeddingSmokeTest 1、RealMilvusVectorStoreSmokeTest 1、KnowledgePersistenceIntegrationTest 10、RealKnowledgeDocumentProcessingSmokeTest 2、RealKnowledgeDocumentProcessingFailureIntegrationTest 2、RealMilvusSchemaValidationSmokeTest 2。两层保护和 local profile 未修改；这些 skips 不是在线集成成功的证据。普通 Mapper / 事务测试使用真实 MySQL 执行，不能与 Mock 结果混称。

最后检查了 `git diff`、`git diff --check` 和开始时哈希。对未跟踪的 Jev Java 文件额外使用源码快照做 no-index diff。只有上表四个 Java 文件相对审查基线改变，没有其他 src 文件新增、删除或变化；SQL、Mapper XML、配置、API、RAG、Workflow、CAS / version、JWT / Auth 和依赖均未改变。工作区已有 application.yaml、application-test.yaml 的 Jev 接入差异是任务开始前就存在的改动，本次未修改。

未解决代码问题：本次范围内无阻塞项。编译有现有 JevClient Jackson API 的 deprecation 提示，不影响编译；本次没有升级依赖或为消除提示扩大重构。

建议下一步：人工查看这四个文件的小差异和报告，按既定流程决定是否暂存 / 提交。当前无新增在线评分质量结论。

## G. Gate

```text
JAVA_STRUCTURE=PASS
DEAD_CODE_AUDIT=PASS
SAFE_DEAD_CODE_REMOVED=YES
JUDGE_ARCHITECTURE=PASS
FALLBACK_STRUCTURE=PASS
EVALUATION_MERGE=PASS
COMPILE=PASS
FOCUSED_TESTS=PASS
FULL_REGRESSION=PASS
DB_SCHEMA_CHANGED=NO
FRONTEND_API_CHANGED=NO
RAG_CHANGED=NO
WORKFLOW_CHANGED=NO
```
