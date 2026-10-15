# Phase 4D4 full-bank real ingestion report

Execution date: 2026-10-06, Asia/Shanghai. Phase4D3 passed before any runtime call. It produced exactly 52 chunks, including 38 for the authorized 16 new documents; expected final counts were derived from its manifest.

JDK 21.0.11; MySQL localhost:3306/ai_interview; Milvus localhost:19530, default/knowledge_chunk_vectors_local, 1024/COSINE/AUTOINDEX. Production model/profile qwen3.7-text-embedding / qwen3.7-text-embedding-1024-dense-v1. Existing-schema production validation ran read-only; no collection/index creation or schema change occurred.

Ingestion: 2026-10-06T06:51:00.699455900Z to 2026-10-06T06:51:21.557992200Z. Independent verifier: 2026-10-06T06:51:36.125275600Z to 2026-10-06T06:51:39.709013700Z.

| State | Before | After |
|---|---:|---:|
| Questions | 21 | 21 |
| Scoring points | 97 | 97 |
| Knowledge documents | 5 | 21 |
| Knowledge chunks | 14 | 52 |
| Milvus Strong entities | 14 | 52 |
| RAG retrieval batches | 0 | 0 |
| RAG hit logs | 0 | 0 |

All 16 documents reached READY in the fixed order, IDs 581–596. New MySQL chunk IDs 525–562 correspond to exactly 38 new vectors. Each new chunk matched its dry-run text/hash/length/index; all 52 final MySQL/Milvus identities match 1:1. Old 5 documents/14 chunks/14 vector IDs and complete row/vector digests are unchanged. All identity, content, document and version mismatch counts are zero. Full vector values were inspected only in memory. Exact IDs are in full-bank/PHASE_4D4_REAL_INGESTION_MANIFEST.json and full-bank/PHASE_4D4_CROSS_STORE_INTEGRITY.json.

16 observed production EmbeddingClient invocations, 38 inputs; returned model/profile/dimension confirmed on each call. 16 vector insert batches, 38 vectors. The unchanged production pipeline also ran 16 normal reconciliation operations targeting only the new IDs; 0 retries or failure-compensation calls. No retrieval, similarity search, rerank, DeepSeek, Jev, full evaluation, threshold experiment or Phase4D5 activity.

Source/code/config integrity: 220/220 initial hashes remain equal, including all 21 source documents, chunker, admin/processing/transaction services, embedding/vector adapters, mapper/XML and runtime application configurations. EnvironmentIsolationInitializer remained enabled; the transaction service was a real Spring proxy. The frozen bank and other business tables retained identical complete row hashes. No production/config/schema/test change was made.

Created four Part B outputs: full-bank/PHASE_4D4_REAL_INGESTION_MANIFEST.json (new IDs/counters/hashes), full-bank/PHASE_4D4_CROSS_STORE_INTEGRITY.json (independent 52-identity cross-store and old-data preservation proof), full-bank/PHASE_4D4_INGESTION_REVIEW.md (scope, findings, service/transaction evidence), this report (execution and gate). Existing unrelated changes preserved. Reports remain local and ignored by existing Git configuration; no staging/commit/push.

Validation: offline Maven compile/classpath BUILD SUCCESS; standalone runner compilation passed; real preflight, sequential ingestion and independent read-only verification passed. JUnit/Surefire tests=0, failures=0, errors=0, skipped=0. No full-regression result is claimed.

P0=0; P1=0; P2=4 consolidated Phase4D3 content diagnostics. Runtime integrity issues=0. Next step is Commander review before a new full-bank retrieval evaluation design. PHASE_4D5_STATUS: BLOCKED_PENDING_COMMANDER_REVIEW.

```text
PHASE 4D4 FULL-BANK REAL INGESTION GATE

PHASE4D3_OVERALL_PASS: PASS
LOCAL_RUNTIME_PREFLIGHT: PASS
OLD_PILOT_5_DOCS_UNCHANGED: PASS
OLD_PILOT_14_CHUNKS_UNCHANGED: PASS
OLD_PILOT_14_VECTORS_UNCHANGED: PASS

NEW_DOCUMENTS_16_16_INGESTED: PASS
NEW_DOCUMENTS_16_16_READY: PASS
NEW_CHUNK_COUNT_MATCHES_PHASE4D3: PASS
NEW_CHUNK_CONTENT_MATCHES_PHASE4D3: PASS

EMBEDDING_MODEL_CORRECT: PASS
EMBEDDING_VERSION_CORRECT: PASS
EMBEDDING_DIMENSION_1024: PASS

MYSQL_MILVUS_1_TO_1: PASS
MYSQL_WITHOUT_VECTOR_0: PASS
VECTOR_WITHOUT_MYSQL_0: PASS
DUPLICATE_VECTOR_ID_0: PASS
CONTENT_MISMATCH_0: PASS
VERSION_MISMATCH_0: PASS

RAG_RETRIEVAL_BATCH_0: PASS
RAG_HIT_LOG_0: PASS

RETRIEVAL_CALLS_0: PASS
RERANK_CALLS_0: PASS
DEEPSEEK_CALLS_0: PASS
JEV_CALLS_0: PASS

PRODUCTION_CODE_UNCHANGED: PASS
CONFIG_UNCHANGED: PASS
SOURCE_DOCS_21_21_UNCHANGED: PASS
TEMP_HARNESS_REMOVED: PASS

PHASE_4D4_OVERALL: PASS
```
