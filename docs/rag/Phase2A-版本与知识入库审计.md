# Phase 2A Version and Ingestion Audit

审计日期：2026-10-05  
仓库：`kun520c/ai-interview-system`，本地 `HEAD=e2cab27db7c103a52eb03417b82115e0910da2e1`。审计按当前工作树执行；工作树有未提交改动，本报告读取了这些本地配置和源码。  
审计边界：只读源码、配置、Maven 依赖和 MySQL 元数据。只执行了 MySQL `SELECT` 查询；没有写入 MySQL/Milvus，没有启动应用，没有调用 Embedding、DeepSeek、Jev 或 RAG，也没有创建文档或 chunk。只新增本报告。

## 1. Executive Summary

```text
SPRING_BOOT_VERSION: 3.5.16
SPRING_FRAMEWORK_VERSION: 6.2.19
SPRING_AI_VERSION: 1.1.8
MILVUS_DEPENDENCY_VERSION: 2.6.20 (native milvus-sdk-java)
REDIS_CLIENT: NOT_DIRECTLY_PRESENT
REDIS_CLIENT_VERSION: NOT_DIRECTLY_PRESENT

JAVA_RUNTIME: JDK 21.0.11 used for the project-aligned Maven audit; the default shell Maven runtime was Java 8u503
MYSQL_SERVER_VERSION: 8.0.45
REDIS_SERVER_VERSION: UNRESOLVED

EMBEDDING_PROVIDER: Alibaba Cloud DashScope-compatible endpoint via Spring AI OpenAI-compatible EmbeddingModel
EMBEDDING_MODEL: qwen3.7-text-embedding
EMBEDDING_DIMENSION: 1024
EMBEDDING_BASE_URL: https://dashscope.aliyuncs.com/compatible-mode/v1
EMBEDDING_API_KEY: CONFIGURED (value omitted)
EMBEDDING_ENABLED_CONFIG: NOT_CONFIGURED (no embedding.enabled switch)
EMBEDDING_TIMEOUT_CONFIG: connect 3s; read 20s

LOCAL_MILVUS_DATABASE: default
LOCAL_MILVUS_COLLECTION: knowledge_chunk_vectors_local
VECTOR_DIMENSION: 1024
METRIC_TYPE: COSINE
INDEX_TYPE: AUTOINDEX

CHUNKER_IMPLEMENTATION: KnowledgeTextChunker.split(String)
CHUNK_SIZE: maximum 1200 Java String UTF-16 code units
CHUNK_OVERLAP: nominal 150 code units; adjusted to whitespace when practical
RETRIEVAL_TOP_K: 5
SIMILARITY_THRESHOLD: NOT_CONFIGURED
```

Maven resolves Spring Framework 6.2.19 from the dependency tree; it is not inferred from the Spring Boot version. MySQL 8.0.45 came from a read-only query to the local `ai_interview` database. Redis cannot be versioned because the local Redis probe could not connect. The Milvus settings below are resolved local-profile configuration and source schema; the live Collection could not be queried because `127.0.0.1:19530` was unreachable.

## 2. Evidence

| Finding | Evidence | Non-secret result |
|---|---|---|
| Spring Boot / Java / Spring AI declarations | `pom.xml:9,41-50` | Parent 3.5.16; Java 21; imported Spring AI BOM 1.1.8 |
| Resolved Spring Framework, Spring AI, Milvus versions | Maven commands below; effective POM | Spring Framework 6.2.19; Spring AI 1.1.8; Milvus SDK 2.6.20 |
| Redis client dependency | `pom.xml`; filtered Maven dependency tree | No Lettuce, Jedis, Redisson, Spring Data Redis, or Redis starter dependency node: `NOT_DIRECTLY_PRESENT`. The Boot effective POM manages some optional ecosystem versions, which does not add those libraries to this project. |
| Maven runtime | `mvnw.cmd -version` | Default shell resolved Java 8u503. With `JAVA_HOME=C:\Program Files\Java\jdk-21.0.11`, Maven resolved JDK 21.0.11. `.idea/misc.xml:60` sets project SDK/language level to 21. |
| MySQL server | Local IntelliJ run configuration supplied the local datasource target; `mysql.exe` executed `SELECT VERSION()` against `localhost:3306/ai_interview` | `8.0.45`. No credentials are included in this report. |
| Redis server | Read-only RESP `INFO server` probe to `127.0.0.1:6379`; checked local CLI/process/service availability | TCP endpoint unavailable; no Redis CLI/server was available. `UNRESOLVED`; no version was guessed. |
| Embedding local values | `application.yaml:30-38`, `application-local.yaml:11-13`, current local IDE environment (secret values redacted) | DashScope-compatible base URL, model `qwen3.7-text-embedding`, dimension 1024, API key configured, connect/read timeout 3s/20s. |
| Local Milvus target | `application-local.yaml:16-23`, current local IDE environment (secret values redacted) | Enabled; URI `http://127.0.0.1:19530`; database `default`; Collection `knowledge_chunk_vectors_local`; dimension 1024. Live endpoint did not respond to a TCP probe. |
| Collection schema and vector store | `MilvusCollectionInitializer.java`; `MilvusSchemaConstants.java`; `MilvusVectorStoreClient.java` | Source declares the fields, primary key, metric, index, insert mapping, and search filter documented below. No live describe request was made. |
| Retrieval configuration | `EvaluationOrchestrationService.java:43,135-138`; `KnowledgeRetrievalService.java`; `MilvusVectorStoreClient.java`; `KnowledgeChunkMapper.xml:44-82` | `topK=5`; COSINE search filtered by embedding version; MySQL eligibility filters apply after vector search; no score threshold or reranker is configured. |
| Environment isolation | `META-INF/spring.factories`; `EnvironmentIsolationInitializer.java:15-58`; profile YAML files | Initializer is registered. Evaluation/test targets are checked and isolated; local target is supplied by the local profile/run environment. |

Commands and results:

```text
.\mvnw.cmd -B -ntp dependency:tree '-Dincludes=org.springframework.boot:*,org.springframework:*,org.springframework.ai:*,io.milvus:*,io.lettuce:*,redis.clients:*,org.redisson:*'
  BUILD SUCCESS; resolved org.springframework.boot 3.5.16, org.springframework 6.2.19,
  org.springframework.ai 1.1.8, io.milvus:milvus-sdk-java 2.6.20; no Redis client nodes.

.\mvnw.cmd -B -ntp help:effective-pom | Select-String -Pattern '<artifactId>spring-boot-starter-parent</artifactId>|<spring-ai.version>|<artifactId>spring-context</artifactId>|<artifactId>spring-core</artifactId>|<artifactId>spring-aop</artifactId>|<artifactId>spring-web</artifactId>|<artifactId>spring-ai-openai</artifactId>|<artifactId>milvus-sdk-java</artifactId>|<artifactId>lettuce-core</artifactId>|<artifactId>jedis</artifactId>' -Context 1,2
  Parent 3.5.16; Spring Framework modules 6.2.19; Spring AI 1.1.8; Milvus SDK 2.6.20.

mysql.exe --no-defaults --protocol=TCP --host localhost --port 3306 --database ai_interview --execute 'SELECT VERSION()'
  8.0.45 (local datasource credentials were supplied in-process and are omitted).

SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT, DATA_TYPE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='ai_interview' AND TABLE_NAME='knowledge_chunk' AND COLUMN_NAME='token_count'
  token_count | YES | NULL | int
```

The effective POM also showed Boot-managed Redis ecosystem version entries, but the filtered resolved dependency tree contains no Redis client. Therefore both requested Redis client fields are `NOT_DIRECTLY_PRESENT`.

### Embedding configuration details

- `EmbeddingConfiguration.embeddingModel()` creates Spring AI's `OpenAiEmbeddingModel` over an `OpenAiApi`; `EMBEDDING_BASE_URL` resolves locally to DashScope's OpenAI-compatible endpoint.
- `EmbeddingConfiguration.createEmbeddingOptions()` sends the configured model and dimension with `encoding_format=float`. `SpringAiEmbeddingClient.embed()` batches by the configured batch size (default 20) and calls the model; no provider call was made during this audit.
- `EmbeddingConfiguration.createNoRetryTemplate()` configures one attempt. `EmbeddingProperties` binds base URL, API key, model, dimension, batch size, profile version, and timeouts. The API key is excluded from `toString()` and its value is not recorded here.
- There is no `embedding.enabled` property. The Embedding beans are declared unconditionally; the ingestion and retrieval services that invoke them are conditional on `milvus.enabled=true`.

## 3. Knowledge Ingestion Pipeline

当前上传先存原文，只有管理员显式调用处理接口后才进入切片、Embedding 和向量写入。以下是代码路径，不代表本轮执行过这些方法。

### STEP 1 — Knowledge source to upload request

- **Class / method:** `AdminKnowledgeDocumentController.uploadDocument(UploadKnowledgeDocumentRequest)`
- **Input:** `multipart/form-data` 的 Markdown（`.md` / `.markdown`）或 TXT 文件，以及 title/category/source。
- **Output:** 调用 `KnowledgeDocumentAdminService.uploadDocument()`。
- **Evidence:** `AdminKnowledgeDocumentController.java:20-31`; upload constraints in `KnowledgeDocumentAdminService.java` and `application.yaml`.

### STEP 2 — Normalize and persist `KnowledgeDocument`

- **Class / method:** `KnowledgeDocumentAdminService.uploadDocument()`
- **Processing:** file size is checked against 5 MiB; client path components are stripped; UTF-8 is decoded strictly; BOM is removed; CRLF/CR become LF; blank content is rejected; SHA-256 is computed over normalized content.
- **Output:** `KnowledgeDocument` with `documentVersion=1`, `processingStatus=UPLOADED`, and original normalized content.
- **MySQL write entry:** `KnowledgeDocumentMapper.insertDocument()` → `KnowledgeDocumentMapper.xml` `insertDocument` (`INSERT INTO knowledge_document`). Generated ID and affected rows are checked.
- **Evidence:** `KnowledgeDocumentAdminService.java:39-108`; `KnowledgeDocumentMapper.java:12`; `mapper/KnowledgeDocumentMapper.xml:7-29`.

### STEP 3 — Explicit processing trigger and state claim

- **Class / method:** `AdminKnowledgeDocumentController.processDocument(Long)` → `KnowledgeDocumentAdminService.processDocument(Long)` → `KnowledgeDocumentProcessingService.processDocument(Long)`.
- **Input:** positive document ID from `POST /api/admin/knowledge/documents/{documentId}/process`.
- **Behavior:** processing service exists only when `milvus.enabled=true`; it claims `UPLOADED`, may claim `FAILED`, and may reclaim stale `PROCESSING`. Before rebuild, `reconcileExistingVectors()` deletes vectors for that document and deletes MySQL chunks for the current document version.
- **Evidence:** `AdminKnowledgeDocumentController.java:34-38`; `KnowledgeDocumentAdminService.java:117-130`; `KnowledgeDocumentProcessingService.java:32-60,198-214`.

### STEP 4 — Split document text

- **Class / method:** `KnowledgeTextChunker.split(String)`
- **Input:** `KnowledgeDocument.content`.
- **Output:** ordered in-memory `List<KnowledgeChunkDraft>` with 1-based chunk indexes, stripped content, and Java string length.
- **Evidence:** `KnowledgeTextChunker.java:16-78`.

### STEP 5 — Generate Embeddings

- **Class / method:** `KnowledgeDocumentProcessingService.processDocument()` → `EmbeddingClient.embed(texts)` → `SpringAiEmbeddingClient.embed(List<String>)` → Spring AI `EmbeddingModel.call(EmbeddingRequest)`.
- **Input:** chunk texts; the adapter restores provider results to input order and validates response count/index/dimension/value.
- **Output:** ordered embedding vectors, configured model/profile version and dimension.
- **Evidence:** `KnowledgeDocumentProcessingService.java:63-76`; `SpringAiEmbeddingClient.java:25-62,102-120`; `EmbeddingConfiguration.java:82-122`.

### STEP 6 — Build MySQL and vector records

- **Class / method:** `KnowledgeDocumentProcessingService.buildProcessingItems()`
- **Output:** each chunk gets a Java `UUID.randomUUID().toString()` vector ID, a `KnowledgeChunk`, and a `VectorWriteItem` with the same ID, document ID, chunk index, embedding version, and vector values. `KnowledgeChunk.tokenCount` is currently set to `null`; the local MySQL column is nullable and has no default.
- **Evidence:** `KnowledgeDocumentProcessingService.java:217-264`; `KnowledgeChunk.java`; local read-only `information_schema.COLUMNS` query in §2.

### STEP 7 — Write vector to Milvus

- **Class / method:** `KnowledgeDocumentProcessingService.processDocument()` → `VectorStoreClient.insert()` → `MilvusVectorStoreClient.insert()` → `buildInsertRequest()`.
- **Output:** native Milvus SDK insert rows. The `vector_id` string is the Milvus primary key and is the same value stored in MySQL `knowledge_chunk.vector_id`.
- **Evidence:** `KnowledgeDocumentProcessingService.java:91-96,233-263`; `MilvusVectorStoreClient.java:54-66,192-205,226-270`; `MilvusSchemaConstants.java`.

### STEP 8 — Persist chunks and mark document ready

- **Class / method:** `KnowledgeDocumentProcessingTransactionService.persistChunksAndMarkReady()` (`@Transactional`)
- **MySQL writes:** `KnowledgeChunkMapper.batchInsert()` → `KnowledgeChunkMapper.xml` `batchInsert`; then `KnowledgeDocumentMapper.markReady()` → `KnowledgeDocumentMapper.xml` `markReady`. Affected-row counts are checked.
- **Failure handling:** processing attempts vector compensation by generated vector IDs and marks the document failed. These are code paths only; none ran in this audit.
- **Evidence:** `KnowledgeDocumentProcessingTransactionService.java:19-51`; `KnowledgeChunkMapper.java:13`; `mapper/KnowledgeChunkMapper.xml:5-36`; `KnowledgeDocumentMapper.java:25`; `mapper/KnowledgeDocumentMapper.xml`.

There is no parser that interprets Markdown structure before chunking. The Markdown file type is retained as metadata; text is chunked from the normalized content string.

## 4. Chunking Audit

| Field | Current implementation |
|---|---|
| `CHUNKER_IMPLEMENTATION` | Project-owned Spring component `KnowledgeTextChunker`; method `split(String)` |
| `CHUNK_SIZE` / `MAX_CHUNK_SIZE` | Maximum 1200 Java `String` UTF-16 code units. Natural boundaries can end a chunk earlier; the final chunk can be shorter. |
| `CHUNK_OVERLAP` | Candidate overlap is 150 code units. The next start moves to whitespace when practical, so actual overlap can be shorter; the candidate is the fallback. |
| `TOKEN_BASED_OR_CHAR_BASED` | Character/code-unit-based; not token-based. No tokenizer or Spring AI text splitter is used. |
| `MIN_CHUNK_SIZE` | `NOT_CONFIGURED`. The 800-unit setting is the earliest natural-boundary search point, not a minimum emitted chunk size. |
| `MAX_CHUNK_SIZE` | 1200 code units. |
| `SECTION_AWARE` | No heading/section model. It recognizes paragraph/newline boundaries as plain text. |
| `MARKDOWN_AWARE` | No. Markdown is accepted, but there is no Markdown AST/parser or code-fence handling. |
| `CUSTOM_SPLITTING_RULES` | Search backward within the 1200-unit window, starting at 800 units: paragraph `\n\n`, newline `\n`, then Chinese `。！？；` / English `.!?;`, otherwise hard cut. Terminator stays in the preceding chunk. Chunk text is `strip()`ped; empty chunks are skipped. |

Evidence: `KnowledgeTextChunker.java:12-14,16-78,81-150`. `KnowledgeChunkDraft.characterCount` uses `String.length()`; the processing service does not convert that value into tokens and writes `token_count=NULL`.

## 5. RAG Retrieval Audit

### Evaluation query → evidence path

1. `EvaluationOrchestrationService` builds `EvaluationContext` for the answer and passes the fixed `RETRIEVAL_TOP_K=5` to `EvaluationRetrievalAdapter.retrieve()`.
2. `EvaluationRagQueryBuilder.build()` creates a query from the main question and scoring points, or from the main question, follow-up question, and follow-up target scoring points. The builder does not add the candidate's answer to the retrieval query.
3. `EvaluationRetrievalAdapter.retrieve()` delegates to `KnowledgeRetrievalService.retrieve(query, topK)`.
4. `KnowledgeRetrievalService` strips the query, calls `EmbeddingClient.embed()` for one query vector, then calls `VectorStoreClient.search(vector, embeddingVersion, topK)`.
5. `MilvusVectorStoreClient.buildSearchRequest()` searches one vector using `COSINE`, `limit=topK`, and the template filter `embedding_version == {embeddingVersion}`. It requests `document_id`, `chunk_index`, and `embedding_version`; primary `vector_id` is obtained from the Milvus result ID.
6. `KnowledgeRetrievalService` sends distinct hit vector IDs to `KnowledgeChunkMapper.selectRetrievableByVectorIds()`. MySQL joins `knowledge_chunk` to `knowledge_document`, then keeps only `kc.status='ACTIVE'`, `kd.processing_status='READY'`, current `document_version`, and exact embedding model/version matches. It maps MySQL chunk content/metadata back to the original Milvus hit order and checks document/chunk IDs for consistency.
7. The resulting `RetrievedChunk` list is the eligible evidence supplied to evaluation. In the normal evaluation flow, `RagTracePersistenceService.persist()` records a retrieval batch and selected evidence hits; this audit did not execute that flow.

### Retrieval settings

| Setting | Current result |
|---|---|
| `RETRIEVAL_TOP_K` | 5 (`EvaluationOrchestrationService.RETRIEVAL_TOP_K`) |
| `SIMILARITY_THRESHOLD` | `NOT_CONFIGURED`; no score cutoff appears in the retrieval path. |
| `CATEGORY_FILTER` | `NOT_CONFIGURED`; category is returned as metadata, not a filter. |
| `KNOWLEDGE_POINT_FILTER` | `NOT_CONFIGURED`. |
| `DOCUMENT_STATUS_FILTER` | MySQL post-filter: `knowledge_document.processing_status='READY'`. |
| `CHUNK_STATUS_FILTER` | MySQL post-filter: `knowledge_chunk.status='ACTIVE'`. |
| `MYSQL_POST_FILTER` | Current document version, READY document, ACTIVE chunk, exact embedding model and embedding version, and vector ID membership. |
| `RERANKING` | `NOT_CONFIGURED`. |
| `DEDUPLICATION` | `vectorIds.distinct()` deduplicates IDs for the MySQL lookup only. The final evidence list is built by iterating the original hit list; no separate final-evidence deduplication is configured. |

Evidence: `EvaluationOrchestrationService.java:43,127-140`; `EvaluationRagQueryBuilder.java:10-68`; `EvaluationRetrievalAdapter.java:27-50`; `KnowledgeRetrievalService.java:44-194`; `MilvusVectorStoreClient.java:395-425`; `mapper/KnowledgeChunkMapper.xml:44-82`; `RagTracePersistenceService.java:17-31,47-86`.

## 6. Environment Isolation

| Profile / mode | MySQL target | Milvus database | Milvus Collection | `milvus.enabled` |
|---|---|---|---|---|
| LOCAL | `ai_interview` (current local run target: `localhost:3306/ai_interview`) | `default` | `knowledge_chunk_vectors_local` | `true` |
| EVALUATION | `ai_interview_evaluation` by default; `EVALUATION_DB_URL` may select the URL but the initializer validates the database name | `default` by default | `knowledge_chunk_vectors_evaluation` by default | `${MILVUS_ENABLED:false}` |
| TEST | `ai_interview_test` by default; `TEST_DB_URL` may select the URL but the initializer validates the database name | `default` by default | `knowledge_chunk_vectors_test` by default | `false` |

`application-test-real.yaml` keeps the test database and test Collection targets. In the documented `local,test-real` smoke profile combination, local credentials/configuration can be used while the test-real file overrides data targets; the guarded real tests are outside this audit and were not run.

`EnvironmentIsolationInitializer` is registered in `src/main/resources/META-INF/spring.factories`. It rejects evaluation mixed with local/test, checks evaluation/test database and Collection names before datasource/Milvus initialization, and requires a test profile for test runtime. For a pure local profile it returns without enforcing a local DB/Collection name; the present local target is therefore confirmed from the local configuration/run environment, not by that guard. The three configured Collection names and MySQL databases are distinct. No Collection was created, deleted, or modified.

Evidence: `EnvironmentIsolationInitializer.java:15-58`; `application-local.yaml:1-23`; `application-evaluation.yaml:1-13`; `application-test.yaml:1-25`; `application-test-real.yaml:1-10`; `META-INF/spring.factories`.

### Milvus schema and mapping

- Collection schema has dynamic fields disabled.
- `vector_id`: `VarChar`, primary key, `autoID=false`, max length 64.
- `document_id`: `Int64`; `chunk_index`: `Int64`.
- `embedding_version`: `VarChar`, max length 128.
- `vector`: `FloatVector`, configured dimension 1024.
- Vector index: `AUTOINDEX`, metric `COSINE`.
- Milvus search applies only an exact embedding-version filter; it returns document/chunk/version metadata and reads the vector ID from the primary key.
- `knowledge_chunk.vector_id` stores the same Java-generated UUID string used as Milvus `vector_id`. Search result mapping uses that value to fetch the authoritative MySQL chunk row.
- The live endpoint at `127.0.0.1:19530` was unreachable. Collection existence and live schema/index were therefore not queried; the properties above describe the configured target and the source-controlled schema contract.

Evidence: `MilvusSchemaConstants.java`; `MilvusCollectionInitializer.java:65-99,125-185,335-350`; `MilvusVectorStoreClient.java:192-270,395-425,439-486`; `KnowledgeDocumentProcessingService.java:217-263`; `KnowledgeChunkMapper.xml:44-82`.

## 7. Official Source Compatibility

| Topic | Local / project version | Required official source version | Status |
|---|---|---|---|
| M02 HashMap put / treeify / resize | Project Java 21; topic source policy explicitly fixes the implementation reference to JDK 8u | [OpenJDK JDK 8u `java.util.HashMap`](https://github.com/openjdk/jdk8u/blob/master/jdk/src/share/classes/java/util/HashMap.java) and [Oracle Java SE 8 `HashMap` API](https://docs.oracle.com/javase/8/docs/api/java/util/HashMap.html). Label claims as JDK 8u behavior. | READY |
| M04 volatile / JMM | Java 21 | [JLS SE 21, Chapter 17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html), especially synchronization order, happens-before, volatile read/write, and the distinction between visibility and atomicity. | READY |
| M05 Spring AOP proxy / self-invocation | Spring Framework 6.2.19 resolved | [Spring Framework 6.2 Reference — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html) and [Using `@Transactional`](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html). | READY |
| H06 InnoDB MVCC / Read View | Local MySQL Server 8.0.45 | [MySQL 8.0 Reference Manual — InnoDB Multi-Versioning](https://dev.mysql.com/doc/refman/8.0/en/innodb-multi-versioning.html), [Consistent Nonlocking Reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-consistent-read.html), and [Transaction Isolation Levels](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html). Keep claims within the 8.0 manual. | READY |
| H07 Redis distributed lock | Redis server version unresolved | [Redis `SET` command](https://redis.io/docs/latest/commands/set/) and [Distributed Locks with Redis](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/). Version-specific command compatibility cannot yet be checked; do not treat newer commands as applicable to the local server. | UNRESOLVED |

No known topic is marked `VERSION_MISMATCH`. Redis remains unresolved because the local server version is unavailable; therefore overall official-source compatibility is not yet established.

## 8. Risks Before Knowledge-Base Creation

### P0

- **Redis version gate is unresolved.** No Redis CLI/server was found and `127.0.0.1:6379` did not accept the read-only `INFO server` probe. H07 cannot be version-checked against the local Redis server, so the source compatibility gate must remain failed.

### P1

- **The configured Milvus endpoint is currently unreachable.** This audit confirms the local profile and schema contract, but not whether `knowledge_chunk_vectors_local` exists or matches it. `MilvusCollectionInitializer.run()` creates the Collection if it is missing, so normal app startup with local profile is not a read-only inspection method.
- **Chunking is not Markdown-aware and is character-based.** The splitter can cut long code blocks/fences or headings at a plain-text boundary; 1200 UTF-16 code units do not guarantee a model-token limit. This behavior should be considered when authoring the five code/concurrency/database topics.

### P2

- **Default Maven shell runtime differs from the project target.** The unmodified shell resolved Java 8u503, while the project targets Java 21. JDK 21.0.11 is installed and was selected explicitly for the dependency audit; later project builds should use that JDK.
- `knowledge_chunk.token_count` remains `NULL` because no tokenizer/counting step is configured. The local MySQL column permits `NULL`; this is not an ingestion failure, but token-count-dependent reporting would not have a value from this pipeline.

## 9. Phase Gate

```text
PHASE 2A VERSION AUDIT GATE

SPRING_VERSION_RESOLVED: PASS
MYSQL_VERSION_RESOLVED: PASS
REDIS_VERSION_RESOLVED: FAIL
EMBEDDING_MODEL_RESOLVED: PASS
MILVUS_CONFIG_RESOLVED: PASS (configured target/schema resolved; live Collection not reachable)
INGESTION_PIPELINE_RESOLVED: PASS
CHUNKING_CONFIG_RESOLVED: PASS
RETRIEVAL_CONFIG_RESOLVED: PASS
ENVIRONMENT_ISOLATION_CONFIRMED: PASS
OFFICIAL_SOURCE_COMPATIBILITY: FAIL (H07 Redis source compatibility unresolved)

PHASE_2A_OVERALL: FAIL
```

Phase 2A remains **FAIL** until the local Redis server version is independently resolved and the H07 source compatibility can be checked. This report does not authorize or begin Phase 2B.
