# Phase 5 E02 Judge validation report

Date: 2026-10-08 (Asia/Shanghai). **E02已按首例异常规则停止。Jev首个真实请求HTTP503；无重试，无生产修改。**

## 验收状态

| gate | outcome | evidence |
|---|---|---|
| STATIC PATCH AUDIT | PASS | 实际源码/精度边界、原严格校验及fallback保持；冻结哈希一致 |
| OFFLINE TARGETED TESTS | PASS | 6套201项，Failures0 Errors0 Skipped0，新增专项63 |
| RUNTIME PREFLIGHT | PASS | JDK21.0.11；User凭证子进程SET；SpringBinder与EnvironmentIsolationInitializer执行 |
| EXECUTION | FAIL | E02-EXCELLENT，真实api.typesafe.ai请求HTTP503，STOP |
| PROTOCOL | NOT_EVALUATED | 未得到成功响应/answers/model，未运行本轮数值校验 |
| QUALITY | NOT_EVALUATED | Jev primary0/38，baseline0/38，pairs0/38，无质量分母 |
| PERSISTENCE / INTEGRITY | PASS | MySQL13表、Milvus52全向量、现有411文件前后hash相同 |
| PHASE 5C | NOT_RUN | 5B未完成，controlled fault与fallback均0 |
| PHASE 6 | NOT_RUN / BLOCKED | 无新授权，不推进 |

## 实际环境与调用

profile=local；MySQL ai_interview：question21 / question_scoring_point97 / knowledge_document21 / knowledge_chunk52。Milvus default/knowledge_chunk_vectors_local：52。Embedding qwen3.7-text-embedding，1024，profile qwen3.7-text-embedding-1024-dense-v1，topK5。没有覆盖目标到其他schema/collection，也没有绕过EnvironmentIsolationInitializer。

Spring真实绑定judge=JEV，deepseek.enabled=true；Jev host api.typesafe.ai、请求model jev-latest、minConfidence0、connect3s/read20s。DeepSeek host api.deepseek.com、请求model deepseek-v4-flash、connect3s/read60s、maxTokens4096。JEV/DeepSeek/Embedding/DB_URL/DB_USERNAME/DB_PASSWORD均SET；未记录原值或单凭证hash，未永久设置Windows环境。

真实调用：Jev1次HTTP503；DeepSeek feedback0，baseline0；Embedding8次HTTP200、Milvus search8次。8个distinct query hash检索一次并复用，共40真实evidence命中，冻结38context/prompt及gold于RUN_EVIDENCE。首请求UTC 2026-10-08T03:57:25.125689600Z → 2026-10-08T03:57:26.100125200Z，失败尝试974.6516ms。实际返回Jev model与token/cost NOT_AVAILABLE；不将E01 model作为E02 model。503原因不能由无body的状态码进一步定位。

失败请求在HTTP观察器立即停止，没有进入生产HTTP错误catch；本轮fallback触发请求0、实际执行0，不能声称fallback已验证。零重试、未开始case2。13表/52全向量前后完整快照hash一致，加只读连接及无写路径工具审查共同证明本轮业务/RAG/DDL/vector写入0。

## 原门槛与历史证据

38=30MAIN+8FOLLOW_UP，gold、bands、24排序、4repair与hardGates原样；SHA256 fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a。有效质量分母为0，全部质量value=null/NOT_EVALUATED，不能因为Mock201PASS、runtime/RAG成功或HTTP503而判定模型质量PASS或FAIL。每case调用数/未运行台账、完整统计门槛、请求时间、实际模型、原始安全响应、所有hash及trace均在新E02 evidence及comparison中。

E01原事故仍为首HTTP200、jev-1.13.0、数值差0.01被原0.001拒绝；其Jev1/DS0与原diagnostic/results/FAIL报告未覆盖。E02独立调用不改写E01；补丁源SHA d79eaa50533ea34be605c453de90dfb2010946440223a874ce2e98835dd28fa4，HEAD 6076a70f18f3062b8faff4fc179cf21f00a95036，本轮没有额外生产修改。

## 文件、验证和未完成项

本轮新增7个交付文件：

- judge/PHASE_5B_EPOCH_02_STATIC_PATCH_AUDIT.md：独立源码行号、严格边界、201离线及冻结核验。
- judge/PHASE_5B_EPOCH_02_RUN_EVIDENCE.json：真实runtime、9个HTTP事件、8检索、38冻结输入、13表/52向量/411文件前后hash及停止证据。
- judge/PHASE_5B_EPOCH_02_JEV_RESULTS.json：唯一失败HTTP尝试，accepted cases空，每case执行台账。
- judge/PHASE_5B_EPOCH_02_DEEPSEEK_BASELINE_RESULTS.json：NOT_RUN，HTTP0、accepted cases空。
- judge/PHASE_5B_EPOCH_02_COMPARISON_REVIEW.md：未完成比较的范围、原门槛与每case记录。
- judge/PHASE_5C_EPOCH_02_FALLBACK_PROOF.md：NOT_RUN及未满足前置条件。
- PHASE_5_EPOCH_02_JUDGE_VALIDATION_REPORT.md：本总报告。

target/phase5-epoch02保留可审查临时源码、隔离运行脚本、classpath来源、测试日志/201invocation与online-attempt.lock防止重跑；全部不含凭证。没有改动任何本轮前存在的源码、测试、应用配置、bank/corpus/golden或旧报告。原dirty工作树列表相同；未staged、committed或pushed。文档沿用现有Git ignore，本地交付。

实际离线测试：JevClientTest17、JevJudgeProviderTest46、JevScoreMappingTest16、JevScorePrecisionCompatibilityTest63、JudgeConfigurationTest12、LlmEvaluationValidatorTest47，各失败0/错误0/跳过0，总201/0/0/0，BUILD SUCCESS。未运行全Maven回归或条件real-smoke；本任务为定点审核与只读在线实验。临时javac前两次使用不存在的enabled getter失败，改为实际Spring配置Boolean绑定后编译通过；仅临时工具，无推理调用/生产修改，未冒充JUnit或代码回归。

P0未观察到安全/持久化/源码越界；P1 HTTP503是当前阻断；P2合成bench的泛化限制保持。推荐将这份停止报告交Commander审核，待明确新的epoch或服务可用性排查授权；不自行重试、改模型/端点/标准或进入Phase6。
