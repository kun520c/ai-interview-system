# Jev AI Judge 局部接入学习报告

验收日期：2026-10-03。代码基线：`20a933a`。

此次改造已编码、编译、通过离线 HTTP 契约测试、既有真实 MySQL 测试和完整回归，并完成 Spring Boot JAR 打包。改动留在工作区，未暂存、未提交、未推送。

```text
JEV_INTEGRATION=PASS
CHANGE_SCOPE=SMALL
BACKWARD_COMPATIBILITY=PASS
DEEPSEEK_FALLBACK=AVAILABLE
DB_SCHEMA_CHANGED=NO
FRONTEND_API_CHANGED=NO
```

这里的 PASS 表示代码接入与离线验收通过。此次没有调用真实在线 Jev、DeepSeek、Embedding 或 Milvus，不代表已经验证 Jev 在中文 Java 面试评分上的准确率、延迟或成本。

## 1. 只读审查结果和范围判断

实际定位如下，均基于仓库代码：

| 检查项 | 当前代码 | 实际职责 |
| --- | --- | --- |
| DeepSeek API Client | `interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java` | RestClient 调用 `/chat/completions`，检查响应结构、finish reason，解析评价 JSON；`completeJson()` 同时供报告生成使用 |
| 答案评价 Service | `interview/orchestration/EvaluationOrchestrationService.java` | 幂等查找、Context、检索与 trace、模型建议、校验、评分、追问决策、评价落库 |
| Evaluation DTO | `EvaluationContext`、`ScoringPointSnapshot`、`LlmEvaluationSuggestion`、`ValidatedEvaluationSuggestion` | 区分输入快照、外部不可信建议和已校验结果；外部 `InterviewEvaluationResponse` 保持原契约 |
| 五维评分 | `LlmEvaluationValidator`、`EvaluationScoreCalculator`、`EvaluationStandard` | 五维各为整数 0～20，总分为 Java 相加所得的 0～100；等级阈值仍为 60/80/90 |
| scoring point | `InterviewQuestionPlanSnapshotService`、`EvaluationContextService`、`FollowUpTargetResolver` | 从 MAIN 历史快照评价；评分点 ID 唯一、权重合计 100；Java 优先选缺失 CORE 点，最多两个追问目标 |
| RAG 输入 | `EvaluationRetrievalAdapter`、`KnowledgeRetrievalService`、`EvaluationPromptBuilder` | 检索 query 不包含用户回答；通过 MySQL eligibility 的 evidence 才进入 prompt；raw Milvus hits 不传给模型 |
| 追问生成 | 原 `EvaluationPromptBuilder` + `DeepSeekEvaluationClient` | DeepSeek 原来同时建议是否追问并生成候选追问；`FollowUpPolicy` 决定最终业务动作 |
| Session 推进 | `InterviewWorkflowService`、`InterviewWorkflowTransactionService`、`InterviewSessionMapper.xml` | 答案 CAS、事务、Session version/current-question CAS 和幂等恢复由 Java 与 SQL 控制 |

评分点权重不是目前 Java 总分的另一套乘法公式；总分仍是五维分数相加。本次没有把“覆盖权重合计”替代五维总分。

局部接入成立的原因是：模型调用集中在一个边界，模型建议与 Validator、评分、工作流已经分开。只替换该调用依赖即可。

新增生产代码位于一个 judge 包，共 9 个文件、522 行。已有生产 Java 仅修改一个文件（9 行新增、10 行删除）；另有生产/测试配置、测试及本报告。数据库 SQL、Mapper、Controller、前端、RAG、Session 状态机、Auth、原 DeepSeek Client 均未修改。

## 2. 修改文件列表

下表中的生产路径均相对 `src/main/java/com/kun/aiinterview/`，测试路径相对 `src/test/java/com/kun/aiinterview/`。

| 文件 | 类型 | 为什么需要、改了什么 |
| --- | --- | --- |
| `interview/evaluation/judge/JudgeProvider.java` | 新增 | 定义单一评价策略接口；内部 Result 携带模型、prompt 版本、raw JSON 和原有建议 DTO，避免编排层绑定供应商 |
| `interview/evaluation/judge/DeepSeekJudgeProvider.java` | 新增 | 包装原 Client；原 prompt、请求、JSON、分数与元数据不变，既是默认策略也是 fallback |
| `interview/evaluation/judge/JudgeProperties.java` | 新增 | 用枚举配置 DeepSeek/Jev，默认 DeepSeek；非法 provider 在装配时失败 |
| `interview/evaluation/judge/JudgeConfiguration.java` | 新增 | 装配唯一 JudgeProvider；仅选择 Jev 才创建其 Properties/Client；配置 Bearer 和连接/读取 timeout |
| `interview/evaluation/judge/jev/JevProperties.java` | 新增 | 绑定 URL、环境变量 key、model、timeout、min-confidence；校验地址、时长、阈值、模型长度；toString 排除 key |
| `interview/evaluation/judge/jev/JevRequest.java` | 新增 | 表达官方 model/state/questions 请求；Score 使用等级数组，Choice 使用选项描述 map |
| `interview/evaluation/judge/jev/JevResponse.java` | 新增 | 表达 model/answers，以及 Score/Choice、probabilities、confidence、legend；忽略不使用的供应商附加字段 |
| `interview/evaluation/judge/jev/JevClient.java` | 新增 | 使用现有 RestClient 调用 HTTP；解析严格 JSON，拒绝重复字段、尾随 JSON、字符串到数值的隐式转换和不匹配的问题集合；错误信息脱敏 |
| `interview/evaluation/judge/jev/JevJudgeProvider.java` | 新增 | 构建语义 rubric、校验与映射 Jev 判断、调用 DeepSeek 生成文本、合并并复用 Validator、一次 fallback；不进行数据库或状态操作 |
| `interview/orchestration/EvaluationOrchestrationService.java` | 修改 | 将直接 DeepSeek 调用换成 JudgeProvider；从选中策略保存真实 model/promptVersion/rawJson，其余管线保留 |
| `src/main/resources/application.yaml` | 修改 | 增加 ai.judge 配置和环境变量入口，默认 deepseek；不包含真实 key |
| `src/test/resources/application-test.yaml` | 修改 | 固定测试 provider=deepseek，保留 JWT/Embedding 隔离与 DeepSeek/Milvus 关闭设置 |
| 测试 `interview/evaluation/judge/jev/JevClientTest.java` | 新增 | 验证官方 HTTP/JSON 结构、异常响应、timeout、HTTP 错误脱敏 |
| 测试 `interview/evaluation/judge/jev/JevJudgeProviderTest.java` | 新增 | 验证判断映射、文本分工、MAIN/FOLLOW_UP、Java 追问否决、confidence 与故障 fallback、生成结果无法覆盖判断 |
| 测试 `interview/evaluation/judge/jev/JevScoreMappingTest.java` | 新增 | 验证 0/4 端点、HALF_UP 边界、null/非有限/越界拒绝 |
| 测试 `interview/evaluation/judge/DeepSeekJudgeProviderTest.java` | 新增 | 经真实原 Client 与 Mock HTTP 验证默认策略保持旧 prompt/JSON/分数/元数据 |
| 测试 `interview/evaluation/judge/JudgeConfigurationTest.java` | 新增 | 验证默认策略、显式 Jev、按需配置、参数绑定、无效配置失败、key 不进入 toString |
| 测试 `interview/evaluation/orchestration/EvaluationOrchestrationServiceTest.java` | 修改 | 原测试通过 DeepSeek 适配器继续运行，没有删除或弱化断言；新增选中策略的 model/promptVersion/rawJson 落库测试 |
| `JEV_INTEGRATION_LEARNING_REPORT.md` | 新增 | 保存审查、调用链、设计解释、学习清单、执行记录与验收边界 |

## 3. 修改前实际调用链

```text
InterviewService.submitAnswer
  -> InterviewWorkflowTransactionService.submitAnswer
       [短事务：Answer + Question 状态]
  -> InterviewWorkflowService.evaluateAnswer
       [答案 claim/retry/recovery CAS；只有获得资格者继续]
  -> EvaluationOrchestrationService.evaluate
       -> 已有 AnswerEvaluation? -> 幂等返回
       -> EvaluationContextService.buildContext
            [MAIN 历史快照；FOLLOW_UP 合并主答与追答]
       -> EvaluationRetrievalAdapter
            -> query -> Embedding -> Milvus -> MySQL eligibility
       -> RagTracePersistenceService.persist [独立短事务]
       -> EvaluationPromptBuilder [只使用合格 evidence]
       -> DeepSeekEvaluationClient.evaluate
            [五维建议 + 评分点覆盖 + 自然语言 + 候选追问]
       -> LlmEvaluationValidator
       -> EvaluationScoreCalculator [Java 五维相加]
       -> EvaluationStandard
       -> FollowUpPolicy + FollowUpTargetResolver
       -> AnswerEvaluationMapper.insertEvaluation
  -> InterviewWorkflowService.applyDecision
       -> moveToFollowUp / advanceToNextMain / finishSession
            [短事务 + Session version/current-question CAS]
```

模型建议追问不等于最终 FOLLOW_UP。MAIN 必须同时满足现有 Java 条件：模型建议追问、总分 <60 或缺失 CORE、且有合法缺失目标。FOLLOW_UP 回答绝不能再次追问。

## 4. 修改后调用链

```text
Answer + MAIN Question Snapshot + Scoring Points
  -> 原 Context / RAG / Trace / EvaluationPrompt
  -> JudgeProvider [由配置选择]
       |
       +-- deepseek [默认]
       |     -> DeepSeekJudgeProvider
       |     -> 原 DeepSeekEvaluationClient.evaluate
       |
       +-- jev
             -> JevClient [同一批合格 RAG evidence]
             -> Structured Decision
                  [五维 Score；每个评分点 Choice；MAIN 追问 Choice]
             -> Java 校验类型、选项、legend、概率、confidence、范围
             -> Java 映射为原五维整数 0..20
             -> DeepSeek.completeJson [仅生成文本]
                  [strengths / missingPoints / correction / 回答原文 / 候选追问]
             -> Java 合并；文本不能覆盖分数或 covered
             -> 原 Validator 预校验
             |
             +-- HTTP/timeout/解析/非法判断/低 confidence/非法生成结果
                   -> 原 DeepSeekJudgeProvider [只回退一次]
  -> 原 LlmEvaluationValidator
  -> 原 Java Score / EvaluationStandard / FollowUpPolicy
  -> 原 AnswerEvaluation 落库
  -> 原 Workflow 事务 / CAS / next question / finish
```

Jev 成功分支的 `llm_model` 保存实际 Jev 模型，`prompt_version` 为 `jev-judge-v1`。既有 `raw_result` JSON 保存 provider、Jev 判断（含概率/confidence）、DeepSeek 生成结果（含其模型）和合并建议。fallback 返回原 DeepSeek 元数据。无需数据库迁移，也不向前端暴露这些内部字段。

## 5. Jev 与 DeepSeek 的分工和评分兼容

Jev 的接口直接接受有界问题，返回类型化判断；这里将五维评价拆为独立 Score，将每个评分点和追问建议拆为 Choice。Choice 自带选项及概率分布，避免为 yes/no 额外发明概率阈值。[官方 API](https://docs.typesafe.ai/api)

这使 Jev 在接口设计上适合 Judge，但不是“技术判断永远正确”。自然语言点评、改进说明和具体追问需要生成文本，仍交给 DeepSeek。官方也建议将生成任务交给生成模型，并明确记录 Jev 的语义、数字和对抗输入限制。[官方模型限制](https://docs.typesafe.ai/model-jaggedness/jev-1.13)

五维名称、0～20 范围、0～100 总分、等级阈值和报表结构保持原语义。Jev 的每个维度使用五个文字等级，索引为 0～4；其返回值是这些等级的期望位置。[官方 Score 说明](https://docs.typesafe.ai/primitives/score)

```text
dimensionScore = HALF_UP(jevScore / 4 * 20)
totalScore = correctness + completeness + depth + clarity + practice

0   -> 0
2   -> 10
2.5 -> 13
4   -> 20
```

Java 拒绝越界、null、NaN、Infinity，不通过裁剪掩盖异常。评分点 weight 被保留在 state/评分点问题中，仍不成为第二套 Java 总分公式。这是量表适配，不是对模型期望分数精度的承诺；若未来更改 rubric，需提升 prompt 版本并用标注样本评估。

## 6. Java 设计解释

- **Provider / Strategy**：编排只依赖一个 `evaluate(context, prompt)` 接口。配置选择实现，不让编排理解 Jev HTTP 细节。DeepSeek 实现是小适配器，没有重写其旧实现。没有再创建重复的 JevJudgeService 层。
- **DTO**：原 `LlmEvaluationSuggestion` 继续作为业务管线输入。新增 Jev DTO 仅隔离供应商协议；DeepSeek 的私有 Feedback DTO 只有文本与评分点证据，不允许接受分数或覆盖结论。后端在合并时使用 Jev 数值和布尔结果。
- **HTTP Client**：复用 RestClient 与 JDK HTTP factory，不添加 SDK、Maven 依赖或新测试框架。默认连接 3s、读取 20s，单次 HTTP 请求，不自动重试。
- **Exception handling**：HTTP/JSON/外部内容问题转为已有 `ExternalServiceException`。Jev 错误体、解析消息不进入日志或异常 cause；fallback 日志只有固定说明。内部序列化缺陷仍作为代码错误抛出，不用广泛 catch 掩盖它。
- **Fallback**：Jev/文本生成无法形成合法评价时，回到原完整 DeepSeek Judge。不会重复 RAG、写第二条业务评价或推进 Session。DeepSeek 自身也失败时异常沿原 Workflow 失败路径传播，不制造分数，不递归重试。
- **Properties**：`ai.judge.provider` 默认 deepseek。Jev 配置只在被选中时绑定和校验，默认分支不需要 JEV_API_KEY。选中 Jev 却缺少 key 时启动失败，避免静默误配置。
- **confidence**：`min-confidence=0.0` 默认不启用低置信度回退，但仍验证 confidence/概率合法且保存原值。设置阈值后任一判断低于阈值就完整 fallback。阈值应由项目标注数据决定，不能把 confidence 当成评分准确率。[官方 Confidence](https://docs.typesafe.ai/confidence)

## 7. 最值得自己阅读的代码

| 优先级 / 类 | 它负责什么、为什么这样设计 | 面试可能怎么问 |
| --- | --- | --- |
| 1. `JevJudgeProvider` | 先校验判断，再生成文本和合并；供应商接入集中在这里，评分、DB、状态机不混进来 | 如何保证生成模型不能覆盖 Judge 分数？非法证据怎么办？两家服务都失败怎么办？ |
| 2. `JudgeProvider` + `DeepSeekJudgeProvider` | 统一调用边界和旧实现适配；既能切换，又保留默认行为 | Strategy 与简单 if/else 的取舍？为什么不再加 Service/Impl 或 Factory？ |
| 3. `JevClient` | 单次 HTTP、严格 DTO 解析、协议身份校验与异常脱敏 | 为什么 DTO 解析成功还不等于评价合法？timeout 与业务重试有什么区别？如何避免日志泄露？ |
| 4. `JudgeConfiguration` | 选中一种策略，按需创建 Jev 组件，复用原 DeepSeek 作为生成与 fallback 依赖 | 条件 Bean 为什么重要？默认配置如何保证不改变原系统？为何 Jev 模式仍需要 DeepSeek enabled？ |
| 5. 原 `LlmEvaluationValidator` | 所有模型最后都必须经过同一信任边界；五维范围、评分点全集、证据、追问契约统一校验 | 为什么供应商说 type-safe 仍要 Java 校验？模型建议与最终业务决定怎么分开？ |

阅读时配合 `JevJudgeProviderTest`，先看正常 MAIN/FOLLOW_UP，再看错误响应和 fallback；这样能直接看到边界对应的行为。

## 8. 本地配置与回滚

正式运行仍需要原有 DeepSeek、Embedding、MySQL 和 Milvus 配置；原 `deepseek.enabled=true`、`milvus.enabled=true` 的工作流装配条件不变。

在已有可运行的本地环境中，通过进程环境变量选择 Jev：

```powershell
$env:AI_JUDGE_PROVIDER = 'jev'
$env:JEV_API_KEY = '<从 TypeSafe 官方控制台获取的 key>'
# JEV_MODEL / JEV_CONNECT_TIMEOUT / JEV_READ_TIMEOUT 可选
# JEV_MIN_CONFIDENCE 默认 0.0，先用标注样本校准再调整
```

也可以设置 Spring 属性 `ai.judge.provider=jev`。官方地址默认 `https://api.typesafe.ai`，Client 调用 `/v1/systemone`，不使用第三方代理网关。[官方 HTTP 契约](https://docs.typesafe.ai/api)

切回原 Judge：设置 `AI_JUDGE_PROVIDER=deepseek` 并重启。该策略是启动配置，不是运行时热切换。无需数据迁移。此次未修改被 Git 忽略的 `application-local.yaml`，也未写入任何真实 API Key。

## 9. 实际验证记录

所有运行使用 Java 21。数据库变量缺失于 agent 进程时，只将已有 User-scope 的 DB_URL/DB_USERNAME/DB_PASSWORD 复制到 Maven 子进程，不打印值、不改生产配置。

| 实际执行 | Tests | Failures | Errors | Skipped | 结果 |
| --- | ---: | ---: | ---: | ---: | --- |
| 初次编译 `mvnw -B -ntp -DskipTests compile` | — | — | — | — | BUILD SUCCESS，180 个生产源文件 |
| 首次完成的聚焦运行 | 139 | 0 | 0 | 0 | BUILD SUCCESS |
| 首次完整回归 `mvnw -B -ntp test` | 1401 | 0 | 0 | 18 | BUILD SUCCESS |
| `mvnw -B -ntp -DskipTests package` | 不重复执行测试 | — | — | — | BUILD SUCCESS，Spring Boot 可执行 JAR |
| 补充三条测试后的聚焦运行 | 142 | 0 | 0 | 0 | BUILD SUCCESS |
| 最终完整回归 `mvnw -B -ntp test` | 1404 | 0 | 0 | 18 | BUILD SUCCESS |

另有一次聚焦命令在服务重启时被中断，未产生完成结果，不计为通过或代码失败。

聚焦命令：

```text
.\mvnw.cmd -B -ntp "-Dtest=JevClientTest,JevJudgeProviderTest,JevScoreMappingTest,JudgeConfigurationTest,DeepSeekJudgeProviderTest,EvaluationOrchestrationServiceTest,DeepSeekEvaluationClientTest,DeepSeekConfigurationTest,DeepSeekInterviewReportGeneratorTest" test
```

最终新增 judge 测试：JevClient 17、JevJudgeProvider 39、ScoreMapping 16、JudgeConfiguration 12、DeepSeekJudgeProvider 1，共 85 项。既有编排测试新增 1 项，变为 23 项。

完整回归中的真实 MySQL 示例：AnswerEvaluationMapper 6 项、RagTracePersistenceServiceIntegration 3 项、InterviewWorkflowTransactionServiceIntegration 1 项、InterviewEvaluationClaimConcurrencyIntegration 2 项、InterviewSessionMapper 14 项、InterviewReportTransactionServiceIntegration 3 项，均失败 0、错误 0、跳过 0。这里没有把 Mock HTTP 测试称为真实在线模型验证。

18 项跳过：RealEmbedding 1、RealMilvusVectorStore 1、KnowledgePersistenceIntegration 10、RealKnowledgeDocumentProcessing 2、RealKnowledgeDocumentProcessingFailureIntegration 2、RealMilvusSchemaValidation 2。原有双层 opt-in 保护保留；全量运行前清除所有 RUN_REAL_*、MILVUS_ENABLED、DEEPSEEK_ENABLED 和 AI_JUDGE_PROVIDER 的进程变量，测试 profile 固定隔离设置。

日志保存在 Git 忽略的 `target/jev-compile.log`、`target/jev-focused.log`、`target/jev-regression.log`、`target/jev-package.log`、`target/jev-focused-final.log`、`target/jev-regression-final.log`。

## 10. 剩余边界与下一步

没有未解决的测试失败。真实 Jev 账户权限、在线响应和评估质量尚未验证；没有用真实在线接口做测试。Jev 分支为满足既有反馈契约，正常评价仍需调用一次 DeepSeek，所以不能据此承诺更快或更便宜。若文本生成失败后回退，还可能增加一次 DeepSeek 请求。

建议先保持默认 DeepSeek。下一步用一小批匿名、人工标注的 Java 回答，分别比较两种 provider 的评分、覆盖、追问建议与耗时，校准 rubric 和 confidence；在有数据之前不调整默认 provider 或阈值，也不扩大系统架构。
