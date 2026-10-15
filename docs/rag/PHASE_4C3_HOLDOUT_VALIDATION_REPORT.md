# Phase 4C3 — Independent Holdout Validation Report

真实检索执行PASS；0.49独立holdout验证FAIL / REJECTED_BY_HOLDOUT。20条查询全部成功，各执行一次topK=5。原始100条hit全部hydrate，离线准入34条：21 DIRECT、7 RELATED_ONLY、6 IRRELEVANT。预注册规则C失败，A/B/D通过，无主观覆盖。

## 1. Required summary

```text
QUERY_COUNT: 20
SUCCESSFUL_RETRIEVALS: 20
TOP_K: 5
THRESHOLD: 0.49
EMBEDDING_CALLS: 20
MILVUS_SEARCH_CALLS: 20
DEEPSEEK_CALLS: 0
JEV_CALLS: 0
TOTAL_RAW_HITS: 100
TOTAL_EVIDENCE_HITS: 100
RAW_HITS_FILTERED_BY_HYDRATION: 0
RAW_POSITIVE_HIT_AT_1: 1.0
RAW_POSITIVE_HIT_AT_3: 1.0
RAW_POSITIVE_HIT_AT_5: 1.0
RAW_POSITIVE_MRR_AT_5: 1.0
RAW_POSITIVE_MACRO_DIRECT_RECALL_AT_5: 1.0
POSITIVE_HIT_AT_1_WITH_THRESHOLD: 1.0
POSITIVE_HIT_AT_3_WITH_THRESHOLD: 1.0
POSITIVE_HIT_AT_5_WITH_THRESHOLD: 1.0
POSITIVE_MRR_AT_5_WITH_THRESHOLD: 1.0
POSITIVE_MACRO_DIRECT_RECALL_AT_5_WITH_THRESHOLD: 1.0
RAW_OBSERVED_DIRECT_HIT_COUNT: 21
OBSERVED_DIRECT_HITS_RETURNED: 21
OBSERVED_DIRECT_HITS_ADMITTED: 21
OBSERVED_DIRECT_HITS_REMOVED: 0
OBSERVED_DIRECT_RETENTION: 1.0
NEAR_NEIGHBOR_ABSTAIN_COUNT: 3
NEAR_NEIGHBOR_ABSTENTION_RATE: 0.6
NEAR_NEIGHBOR_QUERIES_ADMITTING_RELATED_ONLY: 1
NEAR_NEIGHBOR_RELATED_ONLY_ADMISSION_RATE: 0.2
NEAR_NEIGHBOR_QUERIES_ADMITTING_IRRELEVANT: 2
NEAR_NEIGHBOR_IRRELEVANT_ADMISSION_RATE: 0.4
OUT_OF_SCOPE_ABSTAIN_COUNT: 5
OUT_OF_SCOPE_ABSTENTION_RATE: 1.0
OUT_OF_SCOPE_QUERIES_ADMITTING_RELATED_ONLY: 0
OUT_OF_SCOPE_RELATED_ONLY_ADMISSION_RATE: 0.0
OUT_OF_SCOPE_QUERIES_ADMITTING_IRRELEVANT: 0
OUT_OF_SCOPE_IRRELEVANT_ADMISSION_RATE: 0.0
ADMITTED_DIRECT_HIT_COUNT: 21
ADMITTED_RELATED_ONLY_HIT_COUNT: 7
ADMITTED_IRRELEVANT_HIT_COUNT: 6
TOTAL_ADMITTED_EVIDENCE_COUNT: 34
AVERAGE_ADMITTED_EVIDENCE_PER_QUERY: 1.7
HOLDOUT_MIN_RETURNED_DIRECT_SCORE: 0.5103922486305237
HOLDOUT_MAX_RETURNED_IRRELEVANT_SCORE: 0.6926523447036743
HOLDOUT_DIRECT_IRRELEVANT_GAP: -0.18226009607315063
RULE_A: PASS
RULE_B: PASS
RULE_C: FAIL
RULE_D: PASS
THRESHOLD_0_49_HOLDOUT_VALIDATION: FAIL
THRESHOLD_0_49_STATUS: REJECTED_BY_HOLDOUT
MYSQL_WRITES: 0
MILVUS_WRITES: 0
RAG_RETRIEVAL_BATCH_COUNT_POST: 0
RAG_HIT_LOG_COUNT_POST: 0
PRODUCTION_CODE_MUTATIONS: 0
CONFIG_MUTATIONS: 0
```

## 2. Frozen inputs / rules

| 冻结输入 | SHA-256 |
| --- | --- |
| docs/rag/evaluation/PHASE_4C2_HOLDOUT_QUERY_SET.json | 7cc73a8c8eaf438243b1109ae2bd8a4e62d2a7982551b5dbc6c0309cb6e68e95 |
| docs/rag/evaluation/PHASE_4C2_HOLDOUT_GOLDEN_RELEVANCE.json | c481a3e541799bfb525ec8528a1224069181fa20c0d679f0b4aeae2914f2f4b5 |
| docs/rag/evaluation/PHASE_4C2_HOLDOUT_REVIEW.md | d92d032315186f9fe586326912d14b8a1358bc111f90d6e73138615697c98959 |
| docs/rag/PHASE_4C2_HOLDOUT_FREEZE_REPORT.md | 16e6b38180bacc75a1d2496648e8132584d646edb78936cc68552616f4b97930 |

A–D规则UTF-8 SHA-256：91e3e59b741b10164c06e456416d7d1e79a07c3b2088aad608e2625ad723aa70。

```text
A. POSITIVE_HIT_AT_5_WITH_THRESHOLD = 1.0
   Every one of the 10 POSITIVE holdout queries retains at least one DIRECT hit in top5.

B. OBSERVED_DIRECT_RETENTION = 1.0
   The threshold removes zero DIRECT hits that were actually returned in holdout top5.

C. ADMITTED_IRRELEVANT_HIT_COUNT = 0
   No IRRELEVANT holdout evidence is admitted at similarity >= 0.49.

D. OUT_OF_SCOPE_ABSTENTION_RATE >= 0.80
   At least 4 of the 5 OUT_OF_SCOPE_NO_DIRECT queries admit zero evidence.
```

## 3. Normal-local runtime / read-only preflight and postflight

JDK21.0.11；profile仅local，MySQL localhost:3306/ai_interview，Milvus default/knowledge_chunk_vectors_local，1024维、COSINE、AUTOINDEX、STRONG核验。Embedding qwen3.7-text-embedding / qwen3.7-text-embedding-1024-dense-v1。没有切到test/evaluation，也没有覆盖它们到formal local。

五个文档READY、version=1，14个切片ACTIVE且为当前版本。冻结MySQL chunkId/documentId/vectorId/index/version/content/hash逐项匹配，无额外语料。题库21题/97点内容与冻结Phase1/4A核对一致。独立postflight重新连接，避免重用原事务快照。

| MySQL table | pre count | post count | full-row SHA-256 (pre=post) |
|---|---|---|---|
| question | 21 | 21 | 3f0fd117120cbc21a21dd741573d41156f70d29353209a75ae28555de754f504 |
| question_scoring_point | 97 | 97 | a0a4ff0dee5b46c3bb46854dee73d01645f2cfff8f7baaef0e2f9f603ec0c34d |
| knowledge_document | 5 | 5 | cc0b0165cc3c1d4f3fabc2d5fb6acc7d646c160a145f989f01e67890d54c66e9 |
| knowledge_chunk | 14 | 14 | 759ea11b6c3780e477802e31f03349533b5722dee77f95b6ce052d99432e11af |
| rag_retrieval_batch | 0 | 0 | 4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945 |
| rag_hit_log | 0 | 0 | 4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945 |

| Milvus verification | pre | post |
|---|---|---|
| entityCount | 14 | 14 |
| dimension | 1024 | 1024 |
| metric | COSINE | COSINE |
| index | AUTOINDEX | AUTOINDEX |
| consistency | STRONG | STRONG |
| schemaSha256 | 048944ca2d38365515e2d4ae8afd5cbcd74e01355c3e9ffbdc3f6d838a6222e9 | 048944ca2d38365515e2d4ae8afd5cbcd74e01355c3e9ffbdc3f6d838a6222e9 |
| corpusRowsSha256 | 54f88c31f9f54b3ed4f2349b20a403fdfbc219fa908115fb8f86475c54bcff7b | 54f88c31f9f54b3ed4f2349b20a403fdfbc219fa908115fb8f86475c54bcff7b |

服务器确认transaction_read_only=1、REPEATABLE-READ；只执行SELECT和事务控制，没有INSERT/UPDATE/DELETE/DDL。MyBatis仅放行selectRetrievableByVectorIds。Milvus只调用已有Collection的只读描述/Schema校验、scalar metadata query和20次生产search，没有写入或初始化Runner。既有文档向量仅记录摘要，不持久化1024维查询向量。

## 4. Actual production path / exact call counts

```text
EvaluationRagQueryBuilder -> EvaluationRetrievalAdapter.retrieve(context,5) -> KnowledgeRetrievalService.retrieve(query,5) -> SpringAiEmbeddingClient/EmbeddingModel -> MilvusVectorStoreClient.search -> real MyBatis KnowledgeChunkMapper.selectRetrievableByVectorIds -> EvaluationRetrievalResult
```

从当前工作树重新javac --release21编译181个生产Java文件和临时工具；离线Maven仅解析runtime classpath（108个本地缓存依赖），没有下载依赖。采用只导入现有EmbeddingConfiguration/MilvusConfiguration的基础设施Spring context，保留项目EnvironmentIsolationInitializer；未扫描业务组件、调用Trace或完整评判编排。生产一attempt Embedding配置保持原样。

使用冻结的合成MAIN_ANSWER context：负数builderSnapshotId、评分点类型/内容/顺序/权重、category/knowledgePoint/question原样，所有答案、referenceAnswer与追问字段为null/空。全部20个当前真实Builder输出先统一核对hash，并在每题检索前再次核对；没有重新编写题干。

Embedding delegate=20、生产Milvus search=20、真实MyBatis hydration SELECT=20，successful retrieval=20。按冻结顺序，仅一次topK=5，没有独立topK1/3调用、over-fetch、额外Embedding探针或重试。临时运行marker阻止第二次执行。纯Builder校验不计作远程检索。

第一次预检在LOCAL_CONFIGURATION因DB_URL等用户环境变量未继承到Codex子进程而停止，此时MySQL/Milvus/Embedding/检索/写入均为0。只把现有Windows User DB/Embedding变量读取进此次子进程，未修改用户/系统环境、项目配置或目标；预检随后通过。初次错误详情与时间保留在JSON，属于执行环境缺失，不是失败查询重跑。正式20条运行无错误。

本次未运行mvn test或业务测试；未调用DeepSeek/Jev。只读真实服务检索和静态产物校验是本阶段验证证据，不把编译成功或历史测试数当作本次功能结果。

## 5. A–D outcome

| 规则 | 固定验收要求 | 实际机械计算 | 结果 |
| --- | --- | --- | --- |
| A | POSITIVE_HIT_AT_5_WITH_THRESHOLD = 1.0 | 10/10 = 1.0 | PASS |
| B | OBSERVED_DIRECT_RETENTION = 1.0 | 21/21 = 1.0；removed=0 | PASS |
| C | ADMITTED_IRRELEVANT_HIT_COUNT = 0 | 6 != 0 | FAIL |
| D | OUT_OF_SCOPE_ABSTENTION_RATE >= 0.80 | 5/5 = 1.0 >= 0.80 | PASS |

所有正例raw/thresholded Hit@1/3/5、MRR@5、macro DIRECT Recall@5均为1.0；21条已返回DIRECT全部保留。所有5条OUT_OF_SCOPE abstain。近邻3/5 abstain、1/5准入RELATED_ONLY、2/5准入IRRELEVANT。

规则C要求IRRELEVANT准入数严格为0，实际6条；这6条来自2个正例和2个近邻，不因同文档/同领域或分数高而改变gold。

## 6. Every admitted IRRELEVANT

| holdoutId | original rank | chunkKey | similarityScore |
| --- | --- | --- | --- |
| HP-H06-02 | 3 | H06-3 | 0.675653874874115 |
| HP-H07-02 | 3 | H07-3 | 0.6926523447036743 |
| HN-COLLECTION-01 | 3 | M02-3 | 0.5564905405044556 |
| HN-COLLECTION-01 | 4 | M04-2 | 0.5095573663711548 |
| HN-REDIS-01 | 1 | H07-3 | 0.5022160410881042 |
| HN-REDIS-01 | 2 | H07-1 | 0.4905628561973572 |

## 7. Score distributions (diagnostics only)

| label | count | min | p25 | median | p75 | max | mean |
| --- | --- | --- | --- | --- | --- | --- | --- |
| DIRECT | 21 | 0.5103922486305237 | 0.6731438040733337 | 0.7634173035621643 | 0.771983802318573 | 0.8620902299880981 | 0.7317164852505639 |
| RELATED_ONLY | 9 | 0.4428434371948242 | 0.6014819741249084 | 0.6425583362579346 | 0.6982337236404419 | 0.823842465877533 | 0.6401669548617469 |
| IRRELEVANT | 70 | 0.2571953237056732 | 0.3226894959807396 | 0.37356720864772797 | 0.4316375330090523 | 0.6926523447036743 | 0.38468918417181286 |

最小已返回DIRECT=0.5103922486305237；最大已返回IRRELEVANT=0.6926523447036743；gap=-0.18226009607315063。分布有重叠，诊断不产生新阈值建议。百分位R-7，全部样本只来自本次返回top5。

## 8. Production / configuration / file integrity

开始前对全部181个生产Java文件和当前KnowledgeChunkMapper.xml取SHA，结束后全部字节一致。冻结Phase4C2四份输入、已有非生成仓库853个文件均保持不变。配置文件、AGENTS.md、pom.xml和Mapper XML均未修改。完整来源/前后hash见RESULTS.json.runtime.productionCodeHashes/configurationHashes；没有Git暂存、提交或push。

| Required integrity file | SHA-256 before=after |
|---|---|
| src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRagQueryBuilder.java | 2866ca2aeccd9c7da473a31107e4b9a87d298bf4b0495482715c262a115bd97c |
| src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRetrievalAdapter.java | 9aa5a8abc76d4f74169283c388ca2e587684d0e2f5f088ecfb790ef1ad5b2cda |
| src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalService.java | 45b1707f9b1b9c1462e8cd3cdf575258edc97d3c0051d74164d9c44c711aa70c |
| src/main/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingClient.java | ebd342809aea17c48721d8f57777aaea939e10ca66e8a83c3e0b35836b1d4642 |
| src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusVectorStoreClient.java | 5c585e380420a4b7d90850e42c59eae0ab7c4888072645803bc59039058d91d1 |
| src/main/java/com/kun/aiinterview/knowledge/mapper/KnowledgeChunkMapper.java | dc1d633689fe21b1891acea6ad02f375f1a394ce9068d90aed9048430350bc44 |
| src/main/resources/mapper/KnowledgeChunkMapper.xml | 63889346dce0bb981b7df9132f78aa976e07f7e942f06b7778cda4a6a000e2dc |

## 9. Outputs / stop

| 产物 | SHA-256 |
| --- | --- |
| docs/rag/evaluation/PHASE_4C3_HOLDOUT_VALIDATION_RESULTS.json | d9fb07b8394f08fbbd216fccbe3cb86ebabdf2840c48f5b617cfaea768d067ae |
| docs/rag/evaluation/PHASE_4C3_HOLDOUT_VALIDATION_REVIEW.md | 625870738d2ca61a9b201bfc2fc478dfb1174f34437bcd8be705a89868f170ba |
| docs/rag/PHASE_4C3_HOLDOUT_VALIDATION_REPORT.md | 交付校验输出，避免自引用 |

仅新增上述三个产物，临时执行工具、编译class、classpath和日志在交付前清理。现有docs/本地Git忽略规则保持原样，产物为本地未暂存交付。

THRESHOLD_0_49_STATUS=REJECTED_BY_HOLDOUT。此holdout成为evaluation-only，不能用于挑选任何替代阈值；没有threshold sweep、重写/重标注、重跑查询、生产配置修改或Phase4D。等待Commander审核。

PHASE 4C3 HOLDOUT VALIDATION EXECUTION GATE

PHASE4C2_QUERY_HASH_MATCH: PASS
PHASE4C2_GOLDEN_HASH_MATCH: PASS
PHASE4C2_REVIEW_HASH_MATCH: PASS
ACCEPTANCE_RULE_SHA_MATCH: PASS
HOLDOUT_QUERY_COUNT_20: PASS
FROZEN_LABELS_UNCHANGED: PASS
PRODUCTION_QUERY_HASHES_20_20_MATCH: PASS
REAL_PRODUCTION_RETRIEVAL_PATH_USED: PASS
TOPK_5_UNCHANGED: PASS
RETRIEVALS_20_20_SUCCESS: PASS
RETURNED_IDENTITIES_ALL_IN_FROZEN_CORPUS: PASS
THRESHOLD_APPLIED_POST_RETRIEVAL_ONLY: PASS
NO_REFILL_NO_RERANK: PASS
NO_REPLACEMENT_THRESHOLD_SEARCH: PASS
DEEPSEEK_CALLS_0: PASS
JEV_CALLS_0: PASS
MYSQL_WRITES_0: PASS
MILVUS_WRITES_0: PASS
RAG_TRACE_TABLES_UNCHANGED: PASS
PRODUCTION_CODE_UNCHANGED: PASS
CONFIG_UNCHANGED: PASS

PHASE_4C3_EXECUTION: PASS

PHASE 4C3 PRE-REGISTERED THRESHOLD GATE

RULE_A_POSITIVE_HIT_AT_5_1_0: PASS
RULE_B_OBSERVED_DIRECT_RETENTION_1_0: PASS
RULE_C_ADMITTED_IRRELEVANT_0: FAIL
RULE_D_OUT_OF_SCOPE_ABSTENTION_GE_0_80: PASS

THRESHOLD_0_49_HOLDOUT_VALIDATION: FAIL
THRESHOLD_0_49_STATUS: REJECTED_BY_HOLDOUT
