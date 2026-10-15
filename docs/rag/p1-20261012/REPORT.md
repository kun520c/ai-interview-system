# RAG P1 第一轮：应用层 COSINE 候选过滤与离线回放

完成日期：2026-10-12（Asia/Shanghai）。本地分支：`codex/rag-p1-cosine-candidates-20261012`，起点 `6076a70f18f3062b8faff4fc179cf21f00a95036`。已编码、编译、完成离线业务链路验证；未暂存、未提交、未推送。分支保留了任务开始前的未提交改动；本报告和 diff 仅归属本轮 P1。

## 结论与适用范围

第一阶段仍请求 TopK=5，在应用服务中用原始 Float32 COSINE score 过滤，允许 0～5 条候选，不补齐、不重排、不按 documentId 去重。默认关闭，只有显式实验配置才启用 0.70。现有 SQL 资格检查随后进一步减少候选；进入 Prompt 的最终数量可能少于通过阈值的数量。

已有 LOCAL 8×5 数据在 0.70 下保留 17 条，各查询实际构造的 Prompt 输入数量为 2/2/3/2/2/2/2/2。该数据没有出现空结果；空结果路径另用明确标记的合成场景覆盖。不能把这次离线回放、Mock 边界的业务链路测试，或历史 Strong 证据表述为新的 live 检索、真实 SQL 或真实模型质量验收。

附件中的建议作为审查材料读取，没有当作额外执行授权，也没有自动修改人工标签。

## 生产调用链审查

1. [InterviewController.java](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/interview/controller/InterviewController.java:89) 的 `POST /api/interviews/{sessionId}/answers` 调用 `InterviewService.submitAnswer`；完成所有权、幂等和答案持久化后，[InterviewService.java](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewService.java:290) 调用 `InterviewWorkflowService.evaluateAnswer`。
2. [InterviewWorkflowService.java](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java:47) 控制状态/CAS，再调用 `EvaluationOrchestrationService.evaluate`。[EvaluationOrchestrationService.java](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/interview/orchestration/EvaluationOrchestrationService.java:43) 的生产常量 `RETRIEVAL_TOP_K=5` 决定请求上限。
3. [EvaluationContextService.java](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/EvaluationContextService.java:45) 从问题与答案快照构造上下文；`EvaluationRetrievalAdapter` → `EvaluationRagQueryBuilder` 构造主问题、评分点或追问目标的 Query，不包含用户答案。
4. [KnowledgeRetrievalService.java](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalService.java:48) 调用 `EmbeddingClient.embed` 取得模型/profile 和 Query 向量，再调用 `VectorStoreClient.search`。
5. [MilvusVectorStoreClient.java](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusVectorStoreClient.java:395) 是实际 Milvus SDK SearchReq：`COSINE`、版本模板过滤、单查询向量、`limit(topK)`，本轮未改。score 在同文件 561 行取 `Float`，检查 null/finite，再用 `doubleValue()` 无损拓宽；没有 L2/IP 混用、归一化或自行构造 score。适配器拒绝返回超过 topK 的结果。
6. 本轮在 `KnowledgeRetrievalService` 中于 Search 后筛选 SQL 用 vectorIds，并在按原始 hits 遍历时再次执行同一条件，避免宽泛 SQL 响应重新引入被滤候选。`RetrievedChunk.vectorRank=i+1` 仍使用原始位置。全部过滤时保留 raw hits，跳过 SQL，返回空 items；不会产生 `IN ()`。
7. [KnowledgeChunkMapper.xml](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/resources/mapper/KnowledgeChunkMapper.xml:56) 用真实生产 MyBatis SQL 水合：ACTIVE Chunk、READY Document、当前 documentVersion、同 embeddingModel/profile；结果按 vectorId 映射，Java 校对 documentId/chunkIndex 并恢复原始向量顺序。模型/profile 不相符的旧知识不会通过 SQL 门禁。本轮没有改 SQL 或 Schema。
8. `EvaluationRetrievalAdapter` 原样传递 raw hits/items。`EvaluationOrchestrationService` 先保存检索 trace，再使用同一 evidence 列表构造 Prompt。[RagTracePersistenceService.java](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/service/RagTracePersistenceService.java:102) 对零证据只保存 Batch，跳过 hit INSERT；hit 使用原始 rank、chunkId、answerId 和 batchId 关联。Batch 仍记录 requested topK、rawHitCount、evidenceHitCount；新增阈值快照记录 enabled、COSINE、minScore、FLOAT32，不单独持久化被滤 raw hits。
9. [EvaluationPromptBuilder.java](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilder.java:246) 按 evidence 列表循环拼接，每个 Chunk 一条。零条时原有明确分支为 `无可用 RAG Evidence。`；系统 Prompt 要求依据问题、参考答案、评分点评价，并明确不得虚构检索内容。本轮无需更改 Prompt。现有输出 `scoringPointResults.evidence` 指用户答案覆盖依据，并非 RAG 引用；零证据时不创建虚假的 Chunk 或 RAG hit 引用。
10. `JudgeConfiguration` 按 `ai.judge.provider` 选择模型调用：默认 DeepSeek 路径是 `DeepSeekJudgeProvider` → `DeepSeekEvaluationClient.evaluate/completeJson` → `/chat/completions`。若配置 JEV，则 `JevJudgeProvider` 将该 Prompt 交给 Jev，反馈生成仍使用 DeepSeek，失败回到 DeepSeek judge。两条路径均接收动态证据 Prompt。随后 `LlmEvaluationValidator`、`EvaluationScoreCalculator` 和 `FollowUpPolicy` 执行 Java 校验、五维总分计算、确定性决策；不依赖“必须五条证据”。当前运行实例的 provider 未通过启动应用查询，不推断其环境覆盖值。

审查结论：生产代码请求最多五条，原先已经允许 SQL 资格检查后少于五条及零条；因此“生产强制用五条知识”不成立。P1 新增的是候选分数门槛。冻结 Runner 明确 Strong，而生产 SDK SearchReq 没有显式 `consistencyLevel`（继承 Collection 默认；SDK 默认建库为 Bounded，见下方收口复核）；不把 Runner 的 Strong 设置推断成生产行为，本轮不扩展修改一致性。

## 修改文件与最小实现

生产修改：

- [KnowledgeRetrievalService.java](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalService.java)：构造器注入配置；Search 后过滤，保持 raw hits/rank；空候选跳过 SQL。
- [RagTracePersistenceService.java](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/service/RagTracePersistenceService.java)：注入同一配置，在已有 JSON filterSnapshot 中记录阈值策略；启用时标记 `evaluation-rag-filter-v2`，关闭时返回原版 v1，既有 JSON 结构和文本不变，不含 cosineCandidate 字段。

生产新增：

- [RetrievalProperties.java](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/retrieval/RetrievalProperties.java)：`rag.retrieval` 的配置绑定与 finite/[-1,1] 校验。默认 `cosine-threshold-enabled=false`，门槛默认 0.70F。
- [application-rag-p1.yaml](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/resources/application-rag-p1.yaml)：独立 `rag-p1` 实验 profile，仅配置候选门槛，不启用 Milvus/模型，也不指定数据目标。

测试修改：`KnowledgeRetrievalServiceTest`、`KnowledgeRetrievalRawHitTest`、`RagTracePersistenceServiceTest` 只适配新增配置参数，原断言未删改。

测试和离线工具新增：

- [KnowledgeRetrievalThresholdTest.java](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalThresholdTest.java)：配置绑定/profile 隔离、无效门槛、非有限 score、Float32 包含边界、rank 空洞、同文档互补、SQL 宽响应不能重新加入、关闭开关恢复行为。
- [RagP1OfflineBusinessReplayTest.java](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/test/java/com/kun/aiinterview/knowledge/service/RagP1OfflineBusinessReplayTest.java)：真实 QueryBuilder、RetrievalService、Adapter、TraceService、PromptBuilder、Orchestration、Validator、ScoreCalculator、FollowUpPolicy 连起来；Embedding/向量/Mapper/模型是离线边界替身。覆盖四档×八 Query、动态 0～5、MAIN/FOLLOW_UP 零证据、模型收到的 Prompt 和 trace/评分关联。
- [local-replay.json](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/test/resources/rag-p1/local-replay.json)：由冻结原始结果与人工标签校验后生成的独立离线 fixture；保留原 humanLabel，另存 AI 建议。原始导出缺少完整 SQL 文档 metadata，因此标题来自标签导出，category 是测试占位，source 未伪造；测试不声称重新验证真实 SQL metadata。
- [replay.py](E:/learning/IDEA_PROJECTJ/ai-interview-system/tools/rag-p1/replay.py)：只读输入校验、四档原始 Float32 回放、内容规则候选报告，无服务客户端。
- [test_replay.py](E:/learning/IDEA_PROJECTJ/ai-interview-system/tools/rag-p1/test_replay.py)：内容规则候选和浮点门槛的九项离线测试。

最小生产补丁：[production-minimal.diff](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/rag/p1-20261012/production-minimal.diff)；完整本轮代码补丁（含测试与 fixture）：[all-code.diff](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/rag/p1-20261012/all-code.diff)；文件清单/SHA：[source-files.json](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/rag/p1-20261012/source-files.json)。未把已有 AGENTS、Prompt、Jev、隔离器、测试 profile 等未提交改动纳入 P1 补丁。

配置启用与回滚：后续获得运行授权后，可以在原本合规 profile 集合上追加 `rag-p1`，或单独设置 `rag.retrieval.cosine-threshold-enabled=true` 和 `rag.retrieval.min-cosine-score=0.70`。本轮没有启动任何生产应用、改 application.yaml/local.yaml 或覆盖正常生产默认。回滚只需移除实验 profile/覆盖设置，或显式关闭开关；不需要数据库/向量迁移。正常现有环境隔离门禁仍生效。

比较使用原始 SDK score 和 Float32 表示的门槛，避免 `0.70F` 无损拓宽为 double 后与 `0.70D` 比较出现边界误差；测试验证刚好 0.70F 包含、相邻更小 Float32 排除。没有改动原始 score，也没有添加 epsilon。

## 四档回放结果（原始人工标签）

初版标签：2=16、1=11、0=13、U=0。AI 建议 6 项仅供人工复核，没有应用。统计是这 40 条历史候选的观察值，不是独立正式 Gold 或新检索的质量结论。

| 门槛 COSINE ≥ | 保留 | 标签2 | 标签1 | 标签0 | E02Q01～08 保留数量 |
|---:|---:|---:|---:|---:|---|
| 0.60 | 22 | 16 | 6 | 0 | 2 / 2 / 5 / 3 / 2 / 2 / 3 / 3 |
| 0.70 | 17 | 15 | 2 | 0 | 2 / 2 / 3 / 2 / 2 / 2 / 2 / 2 |
| 0.75 | 16 | 15 | 1 | 0 | 2 / 2 / 2 / 2 / 2 / 2 / 2 / 2 |
| 0.80 | 13 | 12 | 1 | 0 | 2 / 2 / 1 / 2 / 2 / 1 / 1 / 2 |

四档均没有历史空结果。0.70 共过滤 23 条，其中原标 0 的 13 条、原标 1 的 9 条、原标 2 的 1 条。原标直接相关保留率为 15/16；被移除的 E02Q03 rank4/Chunk557 是仅参考列表、人工初标2的争议项，须人工决定，不由脚本重标。对原标“1或2”的保留率为 17/27，也应关注被过滤的部分相关信息。不得直接把保留结果没有标签0解释为普适质量保证。

| Query | 原始保留 rank | 阈值候选数 | 实际 Prompt 数 | 被过滤 vectorId（按原始顺序） |
|---|---|---:|---:|---|
| E02Q01 | 1, 2 | 2 | 2 | `21386f00-d4e2-4c78-a756-78e726f2e4c5`, `165d0ecd-574a-4d7e-a1aa-c5216b19a341`, `62ac2ccb-059b-4cb6-8f21-f694e8d6b9ce` |
| E02Q02 | 1, 2 | 2 | 2 | `386cff31-caea-40c4-8ac8-3104227f5f61`, `01007bac-4a36-469b-b900-68a533baf566`, `ff1feeef-a2b0-4476-a153-24068ca6b8c3` |
| E02Q03 | 1, 2, 3 | 3 | 3 | `1d7f249b-5f7c-4515-b5c0-10b3fe6ccdb5`, `dcb2c373-d228-494c-ada8-d65f2e8a8aba` |
| E02Q04 | 1, 2 | 2 | 2 | `d6f8ab60-7e3e-4bd3-aa4c-213b473328c4`, `ed530c30-f53a-437c-87d6-e5a70ea9d04e`, `fd3e23c5-b267-4443-910d-2836acb3cf61` |
| E02Q05 | 1, 2 | 2 | 2 | `21386f00-d4e2-4c78-a756-78e726f2e4c5`, `62ac2ccb-059b-4cb6-8f21-f694e8d6b9ce`, `165d0ecd-574a-4d7e-a1aa-c5216b19a341` |
| E02Q06 | 1, 2 | 2 | 2 | `386cff31-caea-40c4-8ac8-3104227f5f61`, `46e503f6-8a10-40ff-b5fd-a6af228daff6`, `ff1feeef-a2b0-4476-a153-24068ca6b8c3` |
| E02Q07 | 1, 2 | 2 | 2 | `1d7f249b-5f7c-4515-b5c0-10b3fe6ccdb5`, `739582b8-6da4-44e9-9547-c35c88f46ddb`, `f03c5447-d038-4541-a707-31a2c1f15bd6` |
| E02Q08 | 1, 2 | 2 | 2 | `d6f8ab60-7e3e-4bd3-aa4c-213b473328c4`, `ed530c30-f53a-437c-87d6-e5a70ea9d04e`, `fd3e23c5-b267-4443-910d-2836acb3cf61` |

[threshold-replay.json](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/rag/p1-20261012/threshold-replay.json) 包含全部四档逐 Query 的保留顺序、保留/过滤 ID、数量、实际 Prompt 证据 SHA。32 条历史变换均与 Java 业务链路输出逐条匹配。历史 8×5 是来源数据规模，不能与“本轮新检索了40条”混淆。

## 第二层质量过滤候选（本轮未启用生产过滤）

| 问题 | 本数据发现 | 候选规则与测试边界 |
|---|---|---|
| 空/纯空白正文 | 40条中0条 | `strip()` 后为空；有合成测试。没有设置统一最小字符数。 |
| 仅参考链接/引用列表/metadata | Chunk557、562，共4次命中，0.70均被分数门槛移除 | 全部行是引用定义/来源列表，或来源标题与metadata组合，先标记人工复核。普通说明正文带参考链接不会因此被标记。匹配规则仍可能有边界误判，不能作为全库自动删除结论。 |
| 代码围栏不完整/切片开头是代码续片 | Chunk528、556，共4次命中，0.70均保留且原标2 | 围栏不平衡仅触发结合相邻 Chunk 的审查。这里与同文档前块互补，不代表整个 Chunk 无效。不能粗暴删除或按 documentId 去重。 |

候选清单：[content-rule-candidates.json](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/rag/p1-20261012/content-rule-candidates.json)。九项规则测试覆盖空白、参考列表、metadata、正文带引用、短有效知识、平衡代码、不平衡代码续片、单段说明带链接、Float32 包含边界。规则只在离线工具生成问题清单，不在生产应用中强制筛除；没有额外 LLM/Reranker、字符数门槛、自动 Gold 修改或向量重建。

## 测试执行与证据

| 运行 | tests | failures | errors | skipped | 结果 |
|---|---:|---:|---:|---:|---|
| 首次聚焦尝试，Mockito动态attach被沙箱pipe拒绝 | 仅完成4项，整次无最终汇总 | 已完成项0 | 已完成项0；环境阻塞无整次计数 | 已完成项0 | 中止，不能作为整次通过 |
| 改为JVM启动时javaagent，12个聚焦类 | 237 | 0 | 0 | 0 | BUILD SUCCESS |
| 扩大离线选择，仍在沙箱且没有显式test profile | 1411 | 7 | 115 | 19 | BUILD FAILURE；原因见下文 |
| 同一94类，显式隔离test profile，允许本机loopback | 1411 | 0 | 0 | 19 | BUILD SUCCESS |
| Python内容规则测试 | 9 | 0 | 0 | 0 | OK |

扩大运行的第一次失败来自旧 WebMvc 类未显式选择 test/test-real，被现有 `EnvironmentIsolationInitializer` 拒绝，以及 JDK HttpClient 初始化 loopback signal socket 时 `Permission denied: connect`。保留隔离器和原有测试，未修改生产配置或弱化断言；给 Maven 进程显式 `-Dspring.profiles.active=test` 并允许本机 loopback 后，同一选择通过。新增 YAML 隔离断言在最终回归中执行，阈值测试类最终11项、P1业务回放类13项。

最终运行共94个类，选择文件 [offline-test-selection.txt](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p1-20261012/offline-test-selection.txt)。34个真实数据库/完整应用测试类没有运行，名单 [database-tests-not-run.txt](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p1-20261012/database-tests-not-run.txt)；它们没有被删除或改成跳过。这里不是全量普通 `mvn test` 验收，也不是新的真实 SQL 事务验收。

19项 skipped 为：RealEmbedding 1、RealMilvus 1、Schema 2、KnowledgePersistence 10、KnowledgePipeline 2、KnowledgeFailure 2、AI baseline 1。所有 `RUN_REAL_*` 和 `MILVUS_ENABLED` / `DEEPSEEK_ENABLED` 在测试子进程清除；没有持久化开关，没有绕过双重 guard，没有 evaluation 恢复。

最终命令采用进程临时 JDK21、清除真实测试开关；Maven 为：

```powershell
$p1tests = (Get-Content -Raw target/rag-p1-20261012/offline-test-selection.txt).Trim()
.\mvnw.cmd -B -ntp '-Dspring.profiles.active=test' '-DargLine=-javaagent:C:/Users/USER/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.8/byte-buddy-agent-1.17.8.jar' "-Dtest=$p1tests" test
```

日志：[focused-tests-with-agent.log](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p1-20261012/focused-tests-with-agent.log)、[offline-regression.log](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p1-20261012/offline-regression.log)、[offline-regression-isolated.log](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p1-20261012/offline-regression-isolated.log)、[python-tests-final.log](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p1-20261012/python-tests-final.log)。Java实际Prompt回放输出：[business-replay.json](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p1-20261012/business-replay.json)。测试模型固定返回五维建议，用于验证Java路径，不用于判断模型评分改善。

## 首轮冻结保全与输入来源（历史证据）

679个既有文档、LOCAL历史run、P0 v2 Runner、Cursor验收、标注工具文件的任务前后 SHA 检查：变更0，[frozen-files-check.json](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/rag/p1-20261012/frozen-files-check.json)。另外校对下列原始输入 SHA；人工标签 SHA 仍为原值。未发起 MySQL/Milvus 读写，因此关于52向量/191行的数量和哈希来自既有冻结证据，没有新增live复验。没有修改旧epoch或历史验收文件。

| 只读来源 | SHA256 |
|---|---|
| `E:\learning\IDEA_PROJECTJ\ai-interview-system\target\local-rag-live-runs\LOCAL_20261011T103308698Z\local-rag-result.json` | `29894d0d68f5c6a7be39c6030ef594117754fe59adde59a620c4157c223006c0` |
| `C:\Users\USER\Downloads\LOCAL_20261011T103308698Z-human-labels.json` | `8f606a53d695f6a6704ab186d3c8ca73c1cb610cc944dc87a7e5e6e47959f2df` |
| `C:\Users\USER\Downloads\RAG_P1_%E4%BA%BA%E5%B7%A5%E6%A0%87%E6%B3%A8%E4%BA%89%E8%AE%AE%E9%A1%B9%E5%A4%8D%E6%A0%B8%E5%BB%BA%E8%AE%AE_20261012.json` | `72aa044219dad3340268462f7f5f9466a7aa46e3e0e91f0de517e3efa16191dc` |
| `E:\learning\IDEA_PROJECTJ\ai-interview-system\docs\evaluation\judge\PHASE_5A_JUDGE_BENCHMARK.json` | `fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a` |

## 风险、待验证项与建议下一步

- 0.70来自这八条观察，可能对这组数据过拟合。需要独立问题验证，区分有效正文覆盖、误删和零结果率，之后才决定默认启用或最终 Prompt 准入策略。
- 本轮不做额外内容质量/重复/Token预算生产过滤。仅链接与代码截断候选仍须人工和相邻内容复核；同文档互补 Chunk 保留。
- 真实 MySQL hydration、trace事务写入和冻结Collection上的新Search没有重跑。现有 Mapper XML未改变；完整数据库回归需要新的明确授权，并只能使用隔离 test 目标。新live检索、真实 Embedding/Jev/DeepSeek费用、冻结环境操作均未授权。
- 生产 Search 的一致性设置与冻结 Runner 的 Strong 证明不同，后续生产live验证需明确其运行配置；本轮没有借机改 SDK请求。
- 开启门槛可能改变LLM所见上下文和后续评分；离线证明结构、数目、空证据降级与Java流程正确，不证明真实模型分数或事实准确度改善。

推荐先人工复核6项建议，作为独立修订文件保留来源；再在另行授权的独立问题集上验证实验候选，决定 Prompt 第二层规则和是否默认开启。当前交付只完成本轮最小工程改动与离线验证。

## P1 收口：Cursor修复、独立测试集与READY判定

更新日期：2026-10-12。本轮没有重写COSINE过滤，也未修改四个生产文件；只核对Cursor已经落地的snapshot兼容修复、刷新当前证据并新增独立测试资源/测试。当前状态 **READY_FOR_AUTHORIZED_RUN**，真实质量验证仍 **NOT_RUN**。

默认关闭分支返回独立的FILTER_SNAPSHOT_V1，其Java文本块与本轮P1最初基线的原始FILTER_SNAPSHOT逐字符一致；不添加cosineCandidate。启用分支只生成evaluation-rag-filter-v2。现有兼容断言、v2断言和完整离线链路通过。

production-minimal.diff已按当前源码重新生成，严格只含两个Service修改、RetrievalProperties和application-rag-p1.yaml四个生产文件。两个修改文件的任务前基线还与当前HEAD内容逐一相等，因此没有混入历史未提交Prompt/Jev/AGENTS/application.yaml/隔离器等改动。补丁SHA：`0fb7026796932c818ac13e045db2472ddc5ee8a0d4cd1abf654cbc3c3bbd67cd`。当前15个本轮源码/测试/资源的字节SHA见[source-files.json](source-files.json)，详细对应关系见[closeout-evidence.json](closeout-evidence.json)。原P1构建脚本属于首轮历史产物；当前刷新入口是target/rag-p1-closeout-20261012/refresh_delivery.py，不用原脚本覆盖本收口报告。

本轮测试：复用94类离线选择，**1412 tests / 0 failures / 0 errors / 19 skipped**；独立测试集4项验证，**4 tests / 0 failures / 0 errors / 0 skipped**。19个真实服务/AI基线测试仍按guard跳过，34个真实数据库/完整应用测试类未运行，未绕过EnvironmentIsolationInitializer。日志为target/rag-p1-closeout-20261012/offline-regression.log和independent-query-tests-final.log。未运行有写入风险的真实数据库集成。

当前代码的32个历史阈值变换已再次与实际Prompt测试输出匹配；threshold-replay.json增加当前四个生产源码SHA，避免旧产物与修复后源码不对应。679文件保全记录属于首轮历史证据，本轮没有再次读取/操作历史LOCAL运行目录。对本轮允许读取的21篇知识文档与4份旧查询/标签资源做SHA和题目隔离检查：25份输入无变化，12条覆盖依据摘录逐条匹配正文，未复制已有问题/ID。

新集：[16条问题清单](independent/QUESTIONS.md)，冻结JSON位于src/test/resources/rag-p1-independent/queries-v1.json；SHA256=`4f42970bbdb3682c475056e512ff725832329d213b8b49e3a27ac45b5afbd456`。8条现有知识点独立场景改写、4条技术边界追问、4条明确库外问题。coverage-audit.json记录全部21篇真实正文范围，input-audit.json记录来源SHA、摘录与隔离核验。未填人工相关性标签，未预览或伪造Milvus候选。已用真实Java QueryBuilder校验16条完整查询及其SHA；用户答案/覆盖依据不进入查询。

一致性补充：SDK2.6.20的SearchReq默认null，VectorUtils转换时使用Collection默认；建库DTO默认BOUNDED。历史Runner显式Strong。本轮只构建DTO和读本地字节码，没有RPC或生产一致性修改，也没有新证据确认live Collection默认。

下一阶段计划：[NEXT_VALIDATION_PLAN.md](NEXT_VALIDATION_PLAN.md)。同一批16次真实TopK5分别离线经过V0关闭和V1启用0.70，32个Prompt共用相同向量、raw候选及SQL资格快照；不能为两个版本分别做两轮检索。预计16次单文本Embedding、16 Search+2 Describe、最多16水合SELECT+2目标/只读核对SELECT，无Judge/Reranker/语料Embedding。按北京实时0.5元/百万Token，2万Token预留约0.01元；新加坡约0.0105元，真实usage与地域须另核实。价格引用及费用估计边界见计划。

独立ai_interview_test里的真实Mapper/Trace/Prompt六场景验证已形成方案，只使用本测试合成数据、真实Mapper和事务，外部边界缓存/替身；目前未执行。真实运行需要后续授权，代码PASS与READY都不是新的质量PASS。当前知识文档/Golden及交付报告被本机Git exclude忽略，测试仅依赖新冻结resource；当前源文档核对是显式离线审计，不让普通JUnit依赖这些本机忽略文件。没有暂存、提交、推送或evaluation恢复。

补丁复核补充：git apply --check通过；补丁实际应用到独立本地scratch目录后，四个生产文件的完整文本与当前源码逐一相等。没有向工作区生产源码或Git index应用补丁。READY表示输入、审核实现、离线证据和执行设计就绪；下一阶段的新live执行入口与真实test数据库六场景组尚未编码/运行，获授权后按最短计划补齐受控入口，不新建冻结发行框架。


> 公开副本仅隐去本机用户目录。原始报告保留本地；历史实验结论、执行锁和冻结规则未修改。
