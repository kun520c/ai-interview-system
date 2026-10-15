# Phase 3A Runtime Readiness Report

Execution date: 2026-10-05, Asia/Shanghai. Final live Milvus check: 16:57:14–16:57:16. Single Embedding probe, including its preceding MySQL recheck: 16:58:45–16:58:47.

Phase 3A runtime checks passed. No knowledge ingestion was performed. Stop at this report for Commander review.

## 1. Authorization, configuration, and execution method

Before any external operation, the owner explicitly confirmed: “已全部轮换并更新到本地配置”. This confirms completion of the credential-rotation prerequisite; it is an owner attestation, not an independent audit of the previous secrets.

A temporary standalone Java verifier loaded the current working tree's normal `local` profile using Spring Boot ConfigData. The registered production `EnvironmentIsolationInitializer` ran. The verifier independently required loopback MySQL `ai_interview` and Milvus `default/knowledge_chunk_vectors_local` before opening a connection. No test classpath, test/evaluation profile, or application business-service scan was used.

Existing Windows User environment values were copied into the verifier's short-lived process only where the process lacked the relevant database/Embedding variables. Normal local YAML and environment resolution then supplied the configuration. Credentials stayed in memory and were not printed or written into the verifier or this report. No User/Machine environment, local YAML, IDE configuration, or production source was changed.

The Milvus client and Embedding client were constructed through the current production configuration methods. The existing `MilvusCollectionInitializer` validated the already-existing local collection. The Embedding path was `EmbeddingConfiguration` → Spring AI `EmbeddingModel` → `SpringAiEmbeddingClient` / project `EmbeddingClient`.

## 2. Required runtime and operation summary

```text
JAVA_RUNTIME: Oracle JDK 21.0.11+9-LTS-211; C:\Program Files\Java\jdk-21.0.11
MAVEN_RUNTIME: Maven wrapper 3.9.16; Java 21.0.11; JAVA_HOME selected for this process

MYSQL_TARGET: localhost:3306/ai_interview
MYSQL_VERSION: 8.0.45
QUESTION_COUNT: 21
SCORING_POINT_COUNT: 97
KNOWLEDGE_DOCUMENT_COUNT: 0
KNOWLEDGE_CHUNK_COUNT: 0
RAG_RETRIEVAL_BATCH_COUNT: 0
RAG_HIT_LOG_COUNT: 0

MILVUS_ENDPOINT: http://localhost:19530 (configured loopback alias of the expected local endpoint)
MILVUS_REACHABLE: true
MILVUS_DATABASE: default
MILVUS_COLLECTION: knowledge_chunk_vectors_local
COLLECTION_EXISTED_BEFORE: true
COLLECTION_CREATED_THIS_PHASE: false
LIVE_COLLECTION_ENTITY_COUNT: 0
LIVE_VECTOR_DIMENSION: 1024
LIVE_METRIC: COSINE
LIVE_INDEX: AUTOINDEX

EMBEDDING_PROVIDER: Alibaba Cloud Bailian / DashScope; dashscope.aliyuncs.com
EMBEDDING_MODEL: qwen3.7-text-embedding
EMBEDDING_SMOKE_CALLS: 1
EMBEDDING_SMOKE_DIMENSION: 1024
EMBEDDING_SMOKE_RESULT: PASS; one returned vector; all 1024 values finite

SOURCE_DOCUMENT_COUNT: 5
SOURCE_DOCUMENT_HASHES_MATCH_PHASE_2C: NOT_COMPARABLE — Phase 2C did not record original-file SHA-256 values
SOURCE_DOCUMENT_FROZEN_PREVIEW_MATCH: PASS; all 14 chunk texts, offsets, and five normalized source lengths match

MYSQL_WRITES: 0
MILVUS_SCHEMA_WRITES: 0
MILVUS_VECTOR_WRITES: 0
DEEPSEEK_CALLS: 0
JEV_CALLS: 0
RAG_RETRIEVAL_CALLS: 0
```

## 3. MySQL read-only verification

`SELECT DATABASE(), VERSION()` returned `ai_interview` and `8.0.45`. Each of the six table counts above was obtained with `SELECT COUNT(*)`. The connection used a read-only, `REPEATABLE_READ` transaction and was rolled back and closed. The target and counts passed both the initial preflight and the recheck immediately before the single Embedding request.

The normal local question bank remains 21 questions / 97 scoring points. Knowledge documents, chunks, retrieval batches, and hit logs are all empty. No INSERT, UPDATE, DELETE, DDL, cleanup, or repair was issued. No connection to `ai_interview_test` or `ai_interview_evaluation` was made.

## 4. Milvus startup and live verification

The initial TCP probe failed both inside and outside the sandbox. `docker ps` also showed that the Docker Desktop Linux engine pipe was absent. These were execution-environment failures, not application-code failures.

Project history documents the existing Docker Desktop deployment. Docker Desktop was started in the background. Its existing restart configuration brought up the same three containers; no deployment, image installation, Compose rewrite, or container recreation was needed:

| Existing container | Image | Final state |
|---|---|---|
| milvus-standalone | milvusdb/milvus:v2.6.20 | healthy; host port 19530 |
| milvus-minio | minio/minio:RELEASE.2024-12-18T13-15-44Z | healthy |
| milvus-etcd | quay.io/coreos/etcd:v3.5.25 | healthy |

After startup, the configured endpoint became reachable. The target collection existed before initialization, so the production initializer took its describe/validation branch and performed no collection creation.

Live metadata and the initializer's unchanged validation confirmed exactly the five controlled fields:

| Field | Live type | Verified contract |
|---|---|---|
| vector_id | VarChar | primary key; autoID=false; maxLength=64 |
| document_id | Int64 | retrieval identifier |
| chunk_index | Int64 | retrieval identifier |
| embedding_version | VarChar | maxLength=128 |
| vector | FloatVector | dimension=1024 |

Collection and schema dynamic fields are disabled; collection autoID is false; primary field is `vector_id`. The vector index is `AUTOINDEX` with `COSINE`.

A scalar `count(*)` query with **STRONG** consistency returned **0 live entities**. Segment statistics separately reported **0** entities. This was a scalar count operation, not vector similarity search or RAG retrieval. No insert/upsert/delete, load/release, flush, compaction, index rebuild, collection drop, or evaluation/test collection operation was issued.

Docker Desktop and the existing Milvus containers remain running for owner review.

## 5. Exactly one Embedding smoke request

The sole input was:

```text
Phase 3A embedding readiness smoke test.
```

The current production adapter accepted the provider response. Its existing response-model validation requires the provider metadata model to equal `qwen3.7-text-embedding`; the returned project result also reported that model. The batch contained one vector with dimension 1024, and every value was non-null and finite.

One input fits in one production batch. The unchanged configuration uses a one-attempt retry template. A temporary attempt marker prevented an accidental second invocation. No second Embedding probe, model substitution, or provider substitution occurred. No vector was persisted or printed.

## 6. Frozen source integrity and hashes

The five source documents were checked before and after the live probes with strict UTF-8 decoding and the production upload normalization rules: remove one leading BOM if present; convert CRLF/CR to LF; retain all other content. Current raw bytes equal normalized UTF-8 bytes for all five files.

Every full chunk body in the Phase 2C preview matches the current source at its recorded UTF-16 offsets. Together, the overlapping chunks cover each source except its one stripped terminal newline. All five complete normalized source lengths match Phase 2C. The five source modification times remain earlier than the Phase 2C preview/report. No source file was edited during Phase 3A.

| Document | File | UTF-16 length | Current raw SHA-256 |
|---|---|---:|---|
| M02 | source-documents/m02_hashmap_jdk8u_put_resize_treeify.md | 2356 | c5e93fa3c3d3af8fb6e78b60db2d309ccb4e37a286e6ac47d4bb6f76fb053b22 |
| M04 | source-documents/m04_volatile_jmm_java21.md | 2228 | fd1f73f1e1ce307567bcf6b4cf737315b9c36591f1de3620689a979dea2b760c |
| M05 | source-documents/m05_spring_6_2_aop_proxy_self_invocation.md | 2443 | b622cf633bb2ccf7320c33feede070f3875f7670e7f92c89488e2a5ee5457788 |
| H06 | source-documents/h06_mysql_8_0_innodb_mvcc_consistent_read.md | 2451 | 17f181b4460fb6deca4af8e23a7188df3de3a7640f41c254d39aa0af00a06bea |
| H07 | source-documents/h07_redis_distributed_lock_baseline.md | 2563 | e79aff2f6d42ce77989566e6d7d611de77ac5720f962f325ffbae307a1ae88c7 |

These hashes are newly recorded in Phase 3A; they are not historical Phase 2C hashes. Phase 2C contains no raw-file SHA-256 baseline, so a direct historical byte-hash comparison cannot be claimed. `SOURCE_DOCUMENTS_UNCHANGED: PASS` below refers to the frozen normalized evidence content comparison described above. It does not certify historical BOM/line-ending/terminal-whitespace bytes absent from the Phase 2C record.

## 7. Phase 2C baseline carried forward

The following observations remain unchanged and were not tuned or repaired:

- 9 overlap-boundary P2 warnings.
- 2 LOW retrieval-value tail chunks: `M05-3`, `H07-3`.
- No P0 or P1 findings.
- All **23 actual frozen scoring points** have intact HIGH/MEDIUM evidence in the Phase 2C mapping.

The Phase 3A prompt says “25 scoring points”. The frozen question-bank JSON and Phase 2C preview instead agree on **23** for these five questions: M02=5, M04=4, M05=4, H06=5, H07=5. This is a prompt-count discrepancy, not missing evidence or a question-bank edit.

The chunker, source documents, chunk size, overlap, topK, similarity threshold, and retrieval/filter logic were not changed.

## 8. Compilation and verification status

The following offline Maven command completed with exit code 0 / `BUILD SUCCESS` under JDK 21.0.11:

```text
.\mvnw.cmd -o -B -ntp -DskipTests compile dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=<temporary-workspace-directory>\runtime-classpath.txt
```

Maven reported that production classes were already up to date. The temporary verifier was compiled against those production classes and resolved runtime dependencies. Its first compile exposed that `MilvusClientV2` has `close()` but does not implement `AutoCloseable`; the temporary verifier was corrected to close it in `finally`. A subsequent javac invocation needed quoted PowerShell JVM options. Final javac compilation succeeded. No production change was required.

| Verification | Executed | Final result |
|---|---:|---|
| Frozen source versus Phase 2C preview | 5 documents / 14 chunk bodies; repeated after probes | 0 mismatches |
| Maven offline compile / runtime classpath | 1 Maven run | BUILD SUCCESS |
| Standalone normal-local MySQL preflight | 2 successful read-only snapshots | PASS |
| Standalone live Milvus schema/index/count | 1 successful check after startup | PASS |
| Real production Embedding request | exactly 1 | PASS |

No JUnit/Surefire run was started: **tests=0, failures=0, errors=0, skipped=0** for this phase. This is not a claim of full-regression success. No guarded Smoke Test class was run, and this phase did not enable any `RUN_REAL_*` switch. No existing test was deleted, skipped selectively, weakened, or redirected to the formal local targets.

No golden evaluation, DeepSeek/Jev request, frontend test, RAG retrieval, knowledge upload/process, or Phase 3B operation was performed.

## 9. Files and review status

- Created: `docs/rag/PHASE_3A_RUNTIME_READINESS_REPORT.md`, containing live readiness evidence, operation counts, current hashes, and gate results.
- Modified production/config/source-document files: none.
- Temporary verifier sources, compiled classes, classpath, attempt marker, and sanitized result files: removed after report verification.
- Existing unrelated working-tree changes were preserved.
- This report is ignored by the existing `.git/info/exclude` `docs/` rule. It is neither staged, committed, nor pushed; no Git exclusion rule was changed.

Runtime prerequisites passed. The two documentation limitations are the absent historical Phase 2C raw-hash baseline and the prompt's 25-versus-23 scoring-point count. Commander review should consider those explicit limits. No automatic next phase is authorized by this result.

PHASE 3A RUNTIME READINESS GATE

CREDENTIAL_ROTATION_CONFIRMED: PASS
JAVA_21_RUNTIME: PASS
NORMAL_LOCAL_MYSQL_CONFIRMED: PASS
QUESTION_BANK_21_97_INTACT: PASS
KNOWLEDGE_TABLES_EMPTY: PASS
MILVUS_REACHABLE: PASS
LOCAL_COLLECTION_SCHEMA_VALID: PASS
LOCAL_COLLECTION_EMPTY: PASS
EMBEDDING_SMOKE_SUCCESS: PASS
EMBEDDING_DIMENSION_1024: PASS
SOURCE_DOCUMENTS_UNCHANGED: PASS
TEST_EVAL_TARGETS_UNTOUCHED: PASS
MYSQL_WRITES_0: PASS
MILVUS_VECTOR_WRITES_0: PASS
DEEPSEEK_CALLS_0: PASS
JEV_CALLS_0: PASS
RAG_RETRIEVAL_CALLS_0: PASS

PHASE_3A_OVERALL: PASS
