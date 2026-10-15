# Phase 4D5 full-bank retrieval report

Execution PASS: 21 frozen queries ran once each through real unchanged production retrieval, topK=5. Quality: HEALTHY_CURRENT_BANK. Reranker decision: NOT_NEEDED_FOR_CURRENT_21_TOPIC_BASELINE.

| Summary | Actual |
|---|---|
| Golden freeze | 21 × 52 = 1092; old 294 unchanged + new 798 |
| Successful retrievals | 21/21 |
| Embedding calls / inputs | 21 / 21 |
| Milvus searches | 21 |
| MyBatis hydration batches | 21 |
| Raw / hydrated hits | 105 / 105 |
| Top1 DIRECT / RELATED_ONLY / IRRELEVANT | 21 / 0 / 0 |
| First DIRECT ranks | {'1': 21} |
| Returned D / R / I | {'DIRECT': 56, 'RELATED_ONLY': 24, 'IRRELEVANT': 25} |

Baseline execution: 2026-10-06T08:08:36.857144300Z to 2026-10-06T08:08:47.798600300Z. Oracle JDK 21.0.11; normal local ai_interview and default/knowledge_chunk_vectors_local; qwen3.7-text-embedding-1024-dense-v1, 1024/COSINE/AUTOINDEX. Preflight repeated before the only baseline attempt, rejecting any Phase4D4 row/vector/model/schema drift. EnvironmentIsolationInitializer remained registered and executed. No test/evaluation target override.

Hit@1=1.000000, Hit@3=1.000000, Hit@5=1.000000, MRR@5=1.000000. Candidate-recall and ranking thresholds were frozen before execution and were not adjusted. Detailed precision/recall, secondary label hit rates, difficulty/category/individual metrics, quantiles and overlap/document occupancy findings are in evaluation/PHASE_4D5_FULL_BANK_BASELINE_RESULTS.json and evaluation/PHASE_4D5_FULL_BANK_BASELINE_REVIEW.md.

Golden semantics use DIRECT for useful evidence to at least one frozen scoring-point mechanism; neighbor topics can legitimately be DIRECT. Same-document repeats are reported without dedup or causal claims about unobserved candidates. Hit@5 tests whether any DIRECT candidate survives, not whether every DIRECT chunk/document is recalled. Score summaries are diagnostics only; no global threshold was searched and the old 0.49 policy remains rejected.

| Immutable freeze artifact | Before SHA-256 | After SHA-256 |
|---|---|---|
| docs/rag/evaluation/PHASE_4D5_FULL_BANK_GOLDEN_RELEVANCE.json | d4e0fe47c7f103d4fcfb4798345aeeeb8cebdbf09817f015ea6bb30be6a06959 | d4e0fe47c7f103d4fcfb4798345aeeeb8cebdbf09817f015ea6bb30be6a06959 |
| docs/rag/evaluation/PHASE_4D5_FULL_BANK_GOLDEN_REVIEW.md | d0b9ca36c6b7f70a05d14a8c4d7c5106d00551a4ca353080a9e01b23a4b9eb40 | d0b9ca36c6b7f70a05d14a8c4d7c5106d00551a4ca353080a9e01b23a4b9eb40 |
| docs/rag/PHASE_4D5_FULL_BANK_GOLDEN_FREEZE_REPORT.md | baf309ca8be31478208114ddcc6bdf6d644c2bcb751958404fcdff8c5fdca97f | baf309ca8be31478208114ddcc6bdf6d644c2bcb751958404fcdff8c5fdca97f |

Source/config/old-artifact integrity: 359/359 hashes unchanged. Before/after all 13 MySQL row snapshots and all 52 exact vector identities/Float32 digests unchanged. RAG traces remain 0/0. No SQL/vector write, DeepSeek/Jev/rerank or query rerun. Scores and ranks are recorded exactly in JSON; no query vector stored.

Created six phase outputs: three immutable Part A files (Golden JSON, semantic review, freeze report) and three Part B files (results JSON with every raw/hydrated hit and metric, baseline review, this execution/quality report). No existing source/config/data/artifact changed. Existing unrelated worktree changes retained; docs/rag outputs remain ignored by existing Git rules, and nothing was staged/committed/pushed.

Validation: offline production compile/classpath and runner compilation passed; real read-only preflight and 21-query one-shot baseline passed; offline artifact/metric checks passed. JUnit/Surefire tests=0, failures=0, errors=0, skipped=0. No full-regression claim. Temporary runner directory removed; cleanup verified PASS. Independent offline QA checked all 68 DIRECT evidence excerpts/IDs, 105 raw/hydrated pairs, old/new judgment counts and every query/subgroup metric.

Interpretation applies only to the current frozen 21-question bank, 52 chunks, embedding model/profile and topK=5 production retrieval. It does not establish future/universal relevance quality or model-judge correctness. Stop for Commander review.

```text
PHASE 4D5 FULL-BANK EXECUTION GATE

PHASE4D5A_GOLDEN_FREEZE_PASS: PASS
QUERY_SET_HASH_MATCH: PASS
OLD_GOLDEN_HASH_MATCH: PASS
CHUNK_MANIFEST_VALID_52: PASS
GOLDEN_JUDGMENTS_1092_1092: PASS
OLD_294_JUDGMENTS_UNCHANGED: PASS
ALL_21_QUERIES_HAVE_DIRECT: PASS
UNCERTAIN_JUDGMENTS_0: PASS

FULL_BANK_GOLDEN_HASH_UNCHANGED_AFTER_RETRIEVAL: PASS
PRODUCTION_QUERY_HASHES_21_21_MATCH: PASS
REAL_PRODUCTION_RETRIEVAL_PATH_USED: PASS
TOPK_5_UNCHANGED: PASS
RETRIEVALS_21_21_SUCCESS: PASS

RETURNED_IDENTITIES_ALL_IN_52_CORPUS: PASS
NO_THRESHOLD_FILTER: PASS
NO_RERANK: PASS
NO_REFILL_NO_DEDUP: PASS

MYSQL_WRITES_0: PASS
MILVUS_WRITES_0: PASS
RAG_TRACE_TABLES_UNCHANGED: PASS
DEEPSEEK_CALLS_0: PASS
JEV_CALLS_0: PASS

PRODUCTION_CODE_UNCHANGED: PASS
CONFIG_UNCHANGED: PASS
SOURCE_DOCS_UNCHANGED: PASS

PHASE_4D5_EXECUTION: PASS

PHASE 4D5 FULL-BANK QUALITY GATE

HIT_AT_1: 1.0
HIT_AT_3: 1.0
HIT_AT_5: 1.0
MRR_AT_5: 1.0

TOP1_DIRECT_COUNT: 21
TOP1_RELATED_ONLY_COUNT: 0
TOP1_IRRELEVANT_COUNT: 0

CANDIDATE_RECALL_GATE_PASS: PASS
RANKING_ACCEPTABLE_WITHOUT_RERANKER: PASS
FULL_BANK_RETRIEVAL_STATUS: HEALTHY_CURRENT_BANK
RERANKER_DECISION: NOT_NEEDED_FOR_CURRENT_21_TOPIC_BASELINE
```
