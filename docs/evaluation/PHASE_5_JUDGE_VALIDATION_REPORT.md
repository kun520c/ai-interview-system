# Phase 5 judge validation — first-call protocol stop

Date: 2026-10-08 (Asia/Shanghai). Worktree baseline: `6076a70` plus pre-existing uncommitted work.

**5A PASS；5B 首调用协议校验失败，中断；5C NOT_RUN；Phase 6 BLOCKED。** 执行环境凭证恢复已通过，不再是 2026-10-06 的 missing-key 阻断。

## 实测阻断

首个冻结样本 `E02-EXCELLENT` 经真实生产 JevJudgeProvider → JevClient 调用 `/v1/systemone` 一次，HTTP 200，请求 `jev-latest`，返回 `jev-1.13.0`。clarityScore=3.56，概率分布加权期望=3.55，差值0.01超过现有0.001容差，抛出 `ExternalServiceException`。离线原生产校验复现，未改阈值或数据。

生产 catch 请求 fallback=1。首调用立即停止观察器拦截了这个入口；实际 DeepSeek 请求=0，没有将 fallback 输出冒充 Jev 样本，没有后续在线调用或重跑第一个样本。

这是协议一致性阻断，尚不能评价两种模型的语义质量或认为 DeepSeek integration 通过。P1；未发现 P0 数据/凭证问题。合成样本的代表性限制为 P2。

## 运行环境与 5A

Windows User-scope Jev/DeepSeek/Embedding 密钥和 DB 用户凭证只装载到子进程环境。只报告 SET/MISSING；使用 JDK21.0.11 和真实 Spring Environment/Binder/Properties，EnvironmentIsolationInitializer 保持执行。未更改持久配置。已有 Docker 引擎起初未运行，启动 Docker Desktop 后已有 Milvus/minio/etcd 自动恢复并达到 healthy；没有创建或重建容器/Collection。

Runtime: local；MySQL ai_interview；Milvus default/knowledge_chunk_vectors_local。21 questions /97 scoring points /21 documents /52 chunks /52 vectors。Embedding qwen3.7-text-embedding-1024-dense-v1，dimension1024，topK5。Jev host api.typesafe.ai/model jev-latest/connect3s/read20s/minConfidence0；DeepSeek host api.deepseek.com/model deepseek-v4-flash/connect3s/read60s；prompt evaluation-prompt-v1 / jev-judge-v1。DeepSeek 绑定 enabled=true、provider=JEV，但真实 DeepSeek 推理仍未执行。

四题 E02/M04/H05/H09 的题干、reference、评分点 ID/type/content/weight 与数据库和 Phase1 冻结 bank 一致。38 contexts 和 gold 完整；24 MAIN 排序关系、4 FOLLOW_UP 对预注册；冻结前 Provider calls=0；UNCERTAIN_GOLD=0。

Benchmark SHA-256: `fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a`，调用前后相同。

## 调用与状态

8 个不同 query 各检索一次，完整 38 case→hash 复用关系保留于 PHASE_5B_RUN_EVIDENCE.json。准备了两个 Provider 共用的 Context 和 Prompt，但只有第一个 Jev 请求实际执行。

```text
BENCHMARK_CASES: 38
MAIN_CASES: 30
FOLLOW_UP_CASES: 8
TOPICS: 4
DISTINCT_RAG_QUERIES: 8
EMBEDDING_CALLS: 8
MILVUS_SEARCH_CALLS: 8
JEV_PRIMARY_CASES: 0
JEV_UNEXPECTED_FALLBACKS: 1 requested / 0 executed
JEV_HTTP_CALLS: 1
DEEPSEEK_FEEDBACK_CALLS_IN_JEV_MODE: 0
DEEPSEEK_BASELINE_CALLS: 0
JEV_MICRO_COVERAGE_PRECISION: NOT_RUN
JEV_MICRO_COVERAGE_RECALL: NOT_RUN
JEV_MICRO_COVERAGE_F1: NOT_RUN
JEV_EXACT_COVERAGE_SET_MATCH_RATE: NOT_RUN
DEEPSEEK_MICRO_COVERAGE_PRECISION: NOT_RUN
DEEPSEEK_MICRO_COVERAGE_RECALL: NOT_RUN
DEEPSEEK_MICRO_COVERAGE_F1: NOT_RUN
DEEPSEEK_EXACT_COVERAGE_SET_MATCH_RATE: NOT_RUN
JEV_ORDERING_PASS: NOT_RUN
DEEPSEEK_ORDERING_PASS: NOT_RUN
JEV_BAND_PASS_RATE: NOT_RUN
DEEPSEEK_BAND_PASS_RATE: NOT_RUN
JEV_LOW_INFO_FALSE_COVERAGE: NOT_RUN
DEEPSEEK_LOW_INFO_FALSE_COVERAGE: NOT_RUN
JEV_INJECTION_FALSE_COVERAGE: NOT_RUN
DEEPSEEK_INJECTION_FALSE_COVERAGE: NOT_RUN
FOLLOW_UP_REPAIR_ORDER_PASS: NOT_RUN
FOLLOW_UP_STRUCTURAL_PASS: NOT_RUN
JEV_MAPPING_VALID: NOT_RUN
JEV_DECISION_FIELDS_PRESERVED: NOT_RUN
ANSWER_EVIDENCE_EXACT_QUOTE: NOT_RUN
HARD_FEEDBACK_CONTRADICTIONS: NOT_RUN
CONTROLLED_FALLBACK_JEV_ATTEMPTS: 0
CONTROLLED_FALLBACK_DEEPSEEK_CALLS: 0
CONTROLLED_FALLBACK_PASS: NOT_RUN
MYSQL_WRITES: 0
MILVUS_WRITES: 0
RAG_TRACE_WRITES: 0
PRODUCTION_MUTATIONS: 0
```

## 完整性与验证

调用前后全部13核心 MySQL表按真实主键的全行摘要一致；52条向量的全部字段、身份和向量值整体摘要一致。350/350 源码/配置/题库/Golden/21 source-document 全文件 SHA-256 一致。rag_retrieval_batch/rag_hit_log 未写入；没有调用 EvaluationOrchestrationService、工作流或状态更新。摘要是整体文件/表完整性，不提取或单独 hash 凭证。

Maven offline compile + dependency classpath 成功；临时运行工具最终 javac 编译成功；真实只读预检成功；真实8-query检索成功；首个Jev响应拒绝；离线生产校验复现成功。JUnit/Surefire tests/failures/errors/skipped=0/0/0/0；没有运行完整回归，也不声称 DeepSeek或fallback验证通过。

另一次准备阶段 SELECT 误用了 rag_retrieval_batch.id，Provider/Embedding/Milvus search 均0，完整零调用证据保留。只修正临时工具为 retrieval_batch_id，未改生产SQL/Schema。第一次正式在线样本只尝试一次。

## 执行门禁

以下 FAIL (NOT_RUN) 表示验收没有取得证据，并非声称未调用的服务或语义质量已经失败。

```text
PHASE 5 JUDGE EXECUTION GATE
PHASE5A_BENCHMARK_FREEZE_PASS: PASS
BENCHMARK_HASH_UNCHANGED_AFTER_CALLS: PASS
REAL_JEV_PROVIDER_USED: PASS (first request; response rejected)
REAL_DEEPSEEK_PROVIDER_USED: FAIL (NOT_RUN)
JEV_PRIMARY_38_38: FAIL (0/38 accepted)
DEEPSEEK_BASELINE_38_38: FAIL (NOT_RUN)
NO_BENCHMARK_RERUNS: PASS
JEV_RESPONSE_MAPPING_VALID_38_38: FAIL (first response rejected)
JAVA_DECISION_RECOMPUTATION_38_38: FAIL (NOT_RUN)
JEV_DECISION_FIELDS_PRESERVED_38_38: FAIL (NOT_RUN)
ANSWER_EVIDENCE_EXACT_QUOTE_100_PERCENT: FAIL (NOT_RUN)
CONTROLLED_FALLBACK_EXACTLY_ONE_JEV_ATTEMPT: FAIL (NOT_RUN)
CONTROLLED_FALLBACK_EXACTLY_ONE_DEEPSEEK_CALL: FAIL (NOT_RUN)
CONTROLLED_FALLBACK_RESULT_VALID: FAIL (NOT_RUN)
MYSQL_WRITES_0: PASS
MILVUS_WRITES_0: PASS
RAG_TRACE_WRITES_0: PASS
PRODUCTION_CODE_UNCHANGED: PASS
CONFIG_UNCHANGED: PASS
RAG_CORPUS_UNCHANGED: PASS
TEMP_HARNESS_REMOVED: PASS
PHASE_5_EXECUTION: FAIL (first-call protocol stop)
```

## 质量门禁

全部质量指标未完成，不能计算真实 comparison delta。原阈值保持冻结；质量验收未通过（NOT_EVALUATED）。

```text
PHASE 5 JEV QUALITY GATE
JEV_COVERAGE_PRECISION_GE_0_90: FAIL (NOT_RUN)
JEV_COVERAGE_RECALL_GE_0_90: FAIL (NOT_RUN)
JEV_COVERAGE_F1_GE_0_90: FAIL (NOT_RUN)
JEV_EXACT_SET_MATCH_GE_0_75: FAIL (NOT_RUN)
LOW_INFO_AND_INJECTION_FALSE_COVERAGE_0: FAIL (NOT_RUN)
JEV_SCORE_ORDERING_GE_22_OF_24: FAIL (NOT_RUN)
EXCELLENT_4_OF_4_GE_75: FAIL (NOT_RUN)
UNKNOWN_4_OF_4_LE_35: FAIL (NOT_RUN)
OFF_TOPIC_4_OF_4_LE_35: FAIL (NOT_RUN)
SECURITY_2_OF_2_LE_35: FAIL (NOT_RUN)
FOLLOW_UP_REPAIR_ORDER_4_OF_4: FAIL (NOT_RUN)
FOLLOW_UP_MODE_NO_SECOND_FOLLOWUP_8_OF_8: FAIL (NOT_RUN)
HARD_FEEDBACK_CONTRADICTIONS_0: FAIL (NOT_RUN)
JEV_NOT_MATERIALLY_WORSE_THAN_DEEPSEEK_COVERAGE: FAIL (NOT_RUN)
PHASE_5_JEV_QUALITY: NOT_EVALUATED (acceptance not passed)
```

## 交付文件与后续

创建 PHASE_5A_JUDGE_BENCHMARK.json（冻结输入/gold/规则）、PHASE_5A_JUDGE_BENCHMARK_REVIEW.md（逐点语义审查）、PHASE_5A_JUDGE_BENCHMARK_FREEZE_REPORT.md（38 context与源文件哈希）；PHASE_5B_JEV_RESULTS.json（首个拒绝响应/零成功）、PHASE_5B_DEEPSEEK_BASELINE_RESULTS.json（NOT_RUN）、PHASE_5B_JUDGE_COMPARISON_REVIEW.md（阻断、不可得指标、等级）；PHASE_5C_FALLBACK_PROOF.md（NOT_RUN）。补充 PHASE_5B_RUN_EVIDENCE.json（HTTP/检索/数据前后摘要）、PHASE_5B_FIRST_CALL_PROTOCOL_DIAGNOSTIC.json（离线数学复核）、PHASE_5B_PRE_PROVIDER_TOOLING_FAILURE.json（零调用准备错误）。修改本总报告，替换前次凭证阻断状态。

临时工具目录与执行锁已删除并复核不存在；5C 本地故障服务未创建。最终离线 artifact 审查确认 38 个 case 的 query hash 复用关系、8 个真实检索结果/40 个 evidence、单次 Jev HTTP、零 DeepSeek、gold 与源文件哈希不变，PASS。未 git add/commit/push；原有未提交变更保留。docs/evaluation/judge 当前被既有 Git ignore 规则忽略，文件为本地交付，未发布。

交 Commander 审查真实响应与严格概率期望契约，决定供应商澄清或明确授权的后续协议兼容修复/执行方案。当前不得自行放宽容差、换模型、调confidence、改提示词或重跑冻结样本。

```text
PHASE5A_BENCHMARK_FREEZE: PASS
PHASE5B_PROVIDER_COMPARISON: INTERRUPTED_FIRST_CALL_PROTOCOL_VALIDATION
PHASE5C_CONTROLLED_FALLBACK: NOT_RUN
PHASE_5_OVERALL: FAIL
JEV_STATUS: REAL_FIRST_RESPONSE_REJECTED_BY_PRODUCTION_CONTRACT
DEEPSEEK_ROLE: NOT_VALIDATED_IN_THIS_RUN
PHASE_6_STATUS: BLOCKED_PENDING_COMMANDER_REVIEW
```
