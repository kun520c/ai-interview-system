# AI Interview System — Codex Project Instructions

## 1. Project Overview

This project is an AI mock interview system for Java backend internship, campus recruitment, and junior backend developer positions.

The project is intended to be a serious undergraduate portfolio project suitable for resumes and technical interviews. It should demonstrate solid Java backend engineering, complete business workflows, reasonable database design, and practical RAG/LLM integration.

Do not design this as a large enterprise system. Avoid unnecessary complexity, premature abstraction, and overengineering.

## 2. Technology Stack

Current core stack:

* Java 21
* Spring Boot 3.5.16
* Spring Web and Jakarta Bean Validation
* Spring RestClient
* Alibaba Cloud Bailian / DashScope OpenAI-compatible Embeddings API
* `qwen3.7-text-embedding`
* Current embedding dimension: 1024
* DeepSeek OpenAI-compatible Chat Completions API
* Structured JSON evaluation output
* Default DeepSeek model: `deepseek-v4-flash`
* MyBatis 3.0.5
* MySQL 8.0.45
* `io.milvus:milvus-sdk-java:2.6.20`
* Local Docker / Docker Compose Milvus for development and real-service verification
* BCrypt password hashing via spring-security-crypto
* JJWT 0.13.0 for JWT access-token signing and validation
* Spring Security for stateless request authentication and authorization

Planned technologies used only when their corresponding modules are developed:

* Redis
* Spring Mail
* Knife4j
* LangChain4j

Spring AI is not a current dependency. A later version may evaluate a Spring AI implementation, but it must continue to integrate through the project's own `EmbeddingClient` interface. The hand-written DashScope implementation remains the current production implementation.

Do not introduce new technologies unless they solve a concrete project requirement.

## 3. Base Package

The base package is:

```text
com.kun.aiinterview
```

## 4. Package Organization

Use:

```text
business module first + classic layering inside each module
```

Example:

```text
com.kun.aiinterview.user
├── controller
├── dto
├── entity
├── enums
├── mapper
├── service
└── vo
```

Do not create empty packages in advance.

Only create a package when it is needed by the current development stage.

## 5. Core Architecture Rules

The following rules are fixed project decisions.

### Main question generation

Main interview questions come from the fixed question bank.

The LLM must not freely generate main interview questions.

### RAG responsibility

RAG is used after the user answers a question.

Its purpose is to retrieve relevant Java backend knowledge used as evaluation evidence.

RAG does not generate main questions.

### LLM responsibility

The LLM may provide:

* Natural-language analysis
* Five-dimensional score suggestions
* Scoring-point coverage analysis
* Corrections
* Strengths
* Missing points
* Review suggestions
* Candidate follow-up questions
* Natural-language report summaries

The LLM must not:

* Directly modify database business states
* Directly change interview session states
* Directly decide final workflow transitions without Java-side validation
* Directly write business data into the database

### Java backend responsibility

The Java backend controls:

* Authentication and authorization
* Session ownership validation
* Interview state transitions
* Score validation
* Total score calculation
* Follow-up decisions
* Report aggregation
* Weakness updates
* Idempotency
* Transaction boundaries
* Concurrency control

### Evaluation core data flow

The current Evaluation Core flow is:

```text
Answer
→ EvaluationContext
→ RAG Retrieval
→ RAG Trace Persistence
→ Evaluation Prompt
→ DeepSeek suggestion
→ Java Validation
→ Java Score
→ EvaluationStandard
→ FollowUpPolicy
→ AnswerEvaluation
→ EvaluationOrchestrationResult
```

The Retrieval Query does not contain the user's answer, while the Evaluation Prompt may contain it. Raw Milvus hits remain available in the in-memory Retrieval result for runtime diagnostics but are not individually persisted or sent to the LLM. Only evidence that passed MySQL eligibility and was selected as Evaluation Prompt input is persisted in `rag_hit_log`. The LLM suggestion remains untrusted external input until Java validation succeeds, and the final `totalScore` is always calculated by Java.

## 6. Interview Rules

The following business rules are fixed.

* All main questions for one interview session are selected and persisted when the interview is created.
* Main questions are not reselected after creation.
* Each main question can have at most one follow-up question.
* Without a follow-up, the main-answer evaluation is directly FINAL.
* With a follow-up:

    * The main-answer evaluation is INITIAL.
    * The follow-up answer produces the comprehensive FINAL evaluation for that main question.
* Reports and user weaknesses only use the FINAL evaluation of each main question.

The backend decides whether to follow up using deterministic rules.

The LLM may only recommend a follow-up.

## 7. Database Rules

The current database snapshot extends v1.1 with the F4 RAG Retrieval Batch trace table.

It contains 13 core tables:

```text
user
question
question_scoring_point
interview_session
interview_question
interview_answer
answer_evaluation
knowledge_document
knowledge_chunk
rag_retrieval_batch
rag_hit_log
interview_report
user_weakness
```

The database structure has been reviewed and should be treated as frozen unless a concrete implementation problem proves that a change is necessary.

Do not casually modify table structures.

Before proposing a database change:

1. Explain what concrete problem exists.
2. Explain why Java logic cannot reasonably solve it.
3. Explain the migration impact.
4. Wait for explicit approval before changing the database.

## 8. Development Principles

Keep code:

* Simple
* Clear
* Maintainable
* Appropriate for a serious undergraduate Java backend project

Avoid:

* Unnecessary design patterns
* Premature interface abstraction
* Empty architecture layers
* Generic utility dumping grounds
* Microservices
* Message queues without a real requirement
* Distributed transactions
* Kubernetes
* Complex RBAC
* Multi-tenancy
* Complex Agent workflows
* Graph RAG
* Knowledge graphs
* Complex reranking pipelines

Do not add complexity only to make the project appear more advanced.

## 9. Development Workflow

Before modifying code:

1. Read the existing project structure.
2. Read relevant existing files.
3. Understand the current development stage.
4. State:

    * What problem is being solved.
    * Which files need to be created or modified.
    * Why each file is needed.
    * What implementation approach is proposed.

Do not immediately make broad changes without first understanding the repository.

## 10. Task Scope Control

Only implement the explicitly requested development stage.

Do not automatically expand into later stages.

For example, when working only on a login endpoint, do not also implement:

* JWT
* Spring Security
* Redis
* Password reset
* Email verification

unless explicitly requested.

## 11. Learning-Oriented Development

This project is developed primarily by an undergraduate Java backend learner.

For new technical concepts:

1. Explain why the technology or mechanism is needed.
2. Explain the core principle.
3. Then implement it.

Do not turn the project into a large block of unexplained generated code.

Prefer small development increments.

### Spring Security implementation approach

The authentication and authorization concepts required for Spring Security implementation have been reviewed.

The developer will write the main implementation manually.

Before each implementation increment, Codex must first explain the responsibility of each new class and provide a file-level implementation plan.

Codex must not immediately generate the complete Spring Security module unless explicitly requested.

Implementation must proceed in small reviewable increments, and every increment must include focused tests.

Do not reformat unrelated existing files.

Do not modify unrelated imports, whitespace, YAML or Mapper XML.

The JWT payload is signed but not encrypted. JWT validation proves signature integrity, issuer and expiration, but does not guarantee that the database user is still enabled or that the role is still current.

Protected requests must therefore query the current user from the database and use the database status and role as the trusted state source.

## 12. Testing

After making changes:

* Run the relevant tests.
* Report exactly what was tested.
* Report whether tests passed.
* Report unresolved issues clearly.

For question management changes:

* Use real MyBatis SQL rather than relying only on mocks.
* Mapper tests must verify generated-key population and scoring-point batch insertion.
* Service tests must verify scoring-point weight rules, transaction behavior, and affected-row checks.
* MockMvc tests must verify HTTP 401, HTTP 403, valid administrator requests, and invalid request parameters.
* Run `mvn test` after the changes are complete.
* Do not delete, skip, or weaken existing tests merely to make the test suite pass.

Do not claim that code works without running reasonable verification when execution is available.

### Test status reporting

When reporting project status, agents must distinguish among work that is designed, coded, compiled, verified by Mock or unit tests, verified against real services, staged, committed, and pushed.

Do not:

* Describe planned work as implemented.
* Describe Mock or unit-test success as real integration success.
* Describe compilation success as functional verification.
* Describe a local commit as pushed.
* Describe an execution-environment failure as a code failure without evidence.

Report the actual test, failure, error, and skipped counts for each relevant run. Do not treat historical counts as permanent expectations. A conditionally skipped real-service test is not a failure, but it is also not evidence that real integration passed.

### Real external Smoke Tests

The guarded real-service integration / Smoke Tests listed below are opt-in tests. They are not Mock tests or ordinary unit tests, and they must be skipped during an ordinary `mvn test` run unless explicitly enabled. Ordinary real-MySQL Mapper and transaction tests, including the F1/F4 tests, are not controlled by these `RUN_REAL_*` switches and may run in the ordinary full regression when `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` are available.

Their explicit opt-in switches are:

* `RealEmbeddingSmokeTest`: `RUN_REAL_EMBEDDING_TEST=true`
* `RealMilvusVectorStoreSmokeTest`: `RUN_REAL_MILVUS_TEST=true`
* `KnowledgePersistenceIntegrationTest`: `RUN_REAL_KNOWLEDGE_DB_TEST=true`
* `RealKnowledgeDocumentProcessingSmokeTest`: `RUN_REAL_KNOWLEDGE_PIPELINE_TEST=true`
* `RealKnowledgeDocumentProcessingFailureIntegrationTest`: `RUN_REAL_KNOWLEDGE_FAILURE_TEST=true`
* `RealMilvusSchemaValidationSmokeTest`: `RUN_REAL_MILVUS_SCHEMA_TEST=true`

All of these tests must retain two layers of protection: `@EnabledIfEnvironmentVariable` and `Assumptions.assumeTrue(...)`. Agents must not remove or weaken either layer, make the tests run by default, store `RUN_REAL_*` in Git-managed configuration, persist the switches in Windows user or system environment variables, or enable them during an ordinary full test run. Clear temporary switches promptly after the real test finishes.

Formal real Smoke Test verification must use Maven / Surefire with a temporary `RUN_REAL_*` environment variable and the test's own `@ActiveProfiles("local")`. Do not use an IntelliJ IDEA forced run of a conditionally disabled JUnit test as formal evidence: IDEA may inject `-Djunit.jupiter.conditions.deactivate=org.junit.*Enabled*Condition`, bypassing the `@EnabledIfEnvironmentVariable` layer.

`application-local.yaml` is the local real-service configuration file. It must remain ignored and outside Git. Agents must not expose secrets read from it or copy real API keys, database passwords, JWT secrets, Milvus tokens, or other local credentials into `application-test.yaml` merely to make tests pass. The real Smoke Tests use `@ActiveProfiles("local")`; ordinary automated tests must retain the isolated test configuration.

Before an ordinary full test run, ensure all switches listed above and `MILVUS_ENABLED` are unset, then use:

```text
.\mvnw.cmd -B -ntp test
```

The guarded real tests must be reported as skipped in that run. Keep the existing `application-test.yaml` isolation: test JWT configuration, test Embedding configuration, and `milvus.enabled=false`.

Real Milvus Smoke Tests must use unique test data and clean up only data created by that test run. They must never drop, recreate, or clear the real/shared Collection. A Schema-mismatch test may drop only a uniquely named temporary Collection that the same test created, and must confirm its removal in cleanup. After vector deletion, verify that non-target data remains. Use Awaitility or equivalent polling for eventual consistency instead of fixed `Thread.sleep` as the primary synchronization mechanism.

### Test execution environment

The database configuration in `application.yaml` depends on `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Codex child processes, IntelliJ IDEA, PowerShell, and Maven may inherit different environments; never assume that variables available in one process are available in another.

If `${DB_URL}` remains unresolved or the MySQL driver reports `Driver com.mysql.cj.jdbc.Driver claims to not accept jdbcUrl, ${DB_URL}`, first check whether the Maven process inherited all three database variables. Distinguish missing execution environment from a code regression, and do not modify production configuration merely to accommodate the agent's environment.

## 13. Git Rules

Do not execute:

```text
git commit
git push
```

unless explicitly requested.

At the end of each development stage, report:

* Files created
* Files modified
* Purpose of each file
* Tests executed
* Test results
* Remaining issues
* Recommended next step

## 14. Current Development Stage

Current stage:

```text
Evaluation Core                COMPLETE
Interview Workflow             COMPLETE
Interview Session Creation     COMPLETE
External Interview API         COMPLETE
Interview Report               COMPLETE
→ Next stage: Weakness
→ Then: History / Detail → MVP Final Regression / Documentation / Freeze
```

The F1-F4 changes have passed their technical verification gate. Determine their commit and push status from the current Git history; do not infer publication state from this document alone.

The B2 Milvus infrastructure stage is complete. Its production implementation, focused Mock tests, guarded real-service Smoke Tests, and full regression verification were committed and pushed to `origin/main` in commit `8aceb06bdb5f34b2a971f9930d6ec2a41abf834f`.

The C1 knowledge-document processing stage is technically complete, passed its final regression gate, and was committed and pushed to `origin/main` in commit `78f6e31` (`feat: complete knowledge ingestion pipeline`).

The RAG Retrieval R1 stage is technically complete, passed its layered verification and final regression gate, and was committed and pushed to `origin/main` in commit `d3756e6` (`feat: complete rag retrieval r1`). Milvus remains the current production vector database implementation; `InMemoryEmbeddingStore` is not a production replacement.

Question-management MVP record:

1. Administrator question and scoring-point creation — completed.
2. Administrator whole replacement of a question and its scoring points — completed.
3. Administrator question pagination — completed.
4. Administrator question-detail query — completed.
5. Administrator question-status update — completed.

Knowledge-base first-stage completed capability:

* `POST /api/admin/knowledge/documents`
* `multipart/form-data` with `file`, `title`, `category`, and optional `source`
* UTF-8 Markdown (`.md` and `.markdown`) and TXT uploads only
* A hard-coded 5 MiB maximum checked before reading the full byte array
* Removal of Windows and Unix client directory components from the stored file name
* Strict UTF-8 decoding, UTF-8 BOM removal, newline normalization, and blank-content rejection
* SHA-256 over the normalized UTF-8 content
* Original normalized content stored in `knowledge_document.content`
* Backend-controlled `documentVersion = 1`, `processingStatus = UPLOADED`, and `errorMessage = null`
* Mapper affected-row and generated-key checks
* An independent response VO that does not expose content, content hash, or error details
* Existing `/api/admin/**` authorization: no token is HTTP 401 and a current database `USER` is HTTP 403

Knowledge-base second-stage A completed capability:

* A reusable, stateless Spring `@Component` named `KnowledgeTextChunker` accepts normalized document content as a `String`.
* The component returns an in-memory `List<KnowledgeChunkDraft>`; `KnowledgeChunkDraft` is a Java record containing only `chunkIndex`, `content`, and `characterCount`.
* Chunk indexes start at 1 and increase continuously.
* `MAX_CHUNK_CHARACTERS = 1200`, `OVERLAP_CHARACTERS = 150`, and `MIN_NATURAL_BOUNDARY_DISTANCE = 800`.
* Natural boundaries are considered only from character 800 onward in each maximum 1200-character window.
* Boundary priority is paragraph (`\n\n`), ordinary newline (`\n`), supported sentence terminators, then a maximum-length hard cut.
* Supported sentence terminators are Chinese `。！？；` and English `.!?;`; the terminator remains in the preceding chunk.
* Paragraph-boundary lookup is restricted so a `\n\n` delimiter cannot extend beyond the current hard-end window.
* When no natural boundary exists, hard cutting still advances safely and the final chunk may be shorter than 1200 characters.
* Adjacent chunks retain overlap; an English overlap start is moved forward to whitespace when practical to avoid starting in the middle of a word.
* If no whitespace exists, including long Chinese content or an overlong English word, the overlap candidate is used as a stable fallback.
* Defensive progress checks prevent a non-advancing chunk loop.
* Each final chunk uses `strip()`; empty stripped slices are not returned, and `characterCount` is calculated from the stripped content.
* `characterCount` is Java `String.length()` (UTF-16 code-unit count), not a tokenizer or model token count.
* `KnowledgeTextChunker` itself remains an in-memory, side-effect-free component: calling it alone does not write `knowledge_chunk`, change document state, or generate vectors. The completed C1 processing service now consumes its drafts as one step in the larger persistence pipeline.

Second-stage A production-review corrections completed:

* Corrected the chunk-append condition so non-empty chunks are added and stripped empty slices are skipped.
* Corrected paragraph-boundary lookup so `\n\n` cannot be matched across `hardEnd`.
* Corrected the blank-content business message from `带切片` to `待切片`.
First-review production corrections completed:


* `UploadKnowledgeDocumentRequest` now uses type-compatible validation: `@NotNull` for the multipart file and category, `@NotBlank` for title, and database-aligned length limits for title and source.
* `KnowledgeDocumentMapper.xml` now supplies all ten INSERT values, including `#{fileType}`, while retaining generated-key population.
* `KnowledgeDocumentAdminService.uploadDocument()` rejects null and empty files before size checking and byte-array reading.
* Content normalization now converts both CRLF and standalone CR line endings to LF.
* File-name cleanup now handles Windows and Unix client paths, rejects root-only, trailing-separator, empty, overlong, and invalid path values with controlled business errors, and never persists a client directory.
* Service-boundary length checks protect the database limits for title, source, and the cleaned file name.

Additional first-review cleanup:

* The misspelled `konwledge` package was consistently corrected to `knowledge` in production code, tests, and MyBatis XML references.
* Unrelated Kotlin dependencies and build plugins were removed; the Maven build remains Java-only and no longer compiles Java sources twice.
* The knowledge module introduced its own `KnowledgeCategory`; the question module still uses `QuestionCategory`. No shared-category enum migration occurred in this stage, so no migration claim should be made.

Knowledge-base second-stage B1 completed capability:

* The project owns a provider-neutral `EmbeddingClient` interface. `DashScopeEmbeddingClient` is the current hand-written production implementation, and DashScope request and response DTOs are not exposed to upper business layers.
* `EmbeddingVector` and `EmbeddingBatchResult` are project-internal result records. Their list values are defensively copied and exposed as unmodifiable lists.
* `EmbeddingProperties` centrally binds `baseUrl`, `apiKey`, `model`, `dimension`, `batchSize`, `profileVersion`, `connectTimeout`, and `readTimeout`.
* Configuration validation rejects blank required text, non-positive dimensions and batch sizes, and null or non-positive timeouts. The API key is excluded from the generated `toString()` output.
* `EmbeddingConfiguration` registers `EmbeddingProperties` and creates a dedicated `embeddingRestClient`.
* The dedicated client uses `Bearer ` authorization and applies both connection and read timeouts.
* The OpenAI-compatible request DTO serializes `model`, `input`, `dimensions`, and `encoding_format`.
* The response DTO deserializes `data[].index`, `data[].embedding`, `model`, `usage.prompt_tokens`, and `usage.total_tokens`, while ignoring unrelated supplier fields.
* Jackson handles the request JSON serialization and response JSON deserialization.
* The client rejects a null or empty input list and rejects null, empty, or whitespace-only elements with the corresponding element index in the error message.
* Valid input is defensively copied, and text content is sent without trimming or other modification.
* Total input larger than `batchSize` is automatically split into multiple HTTP requests, including correct handling of the final short batch.
* Each request uses the configured model and dimension with float encoding.
* Supplier-local indexes are validated and converted with `globalIndex = batchStart + localIndex`; out-of-order supplier data is restored to original input order before batches are merged.
* Responses are rejected when the model, data count, indexes, vector dimensions, or floating-point values are invalid.
* Null data items, null or non-finite vector values, and null, negative, duplicate, or out-of-range indexes are rejected.
* Multiple batches are merged into one ordered `EmbeddingBatchResult`.
* Token usage is treated as request-level metadata and accumulated across trustworthy batch responses. If any batch lacks trustworthy usage or `total_tokens`, the complete call's `totalTokenCount` is null.
* Negative token counts are rejected. `characterCount` is not used as a token count, and token totals are not distributed across individual vectors.
* RestClient HTTP, response-body, and JSON problems are exposed to callers as `ExternalServiceException`.

Knowledge-base second-stage B2 completed capability:

* The project owns a provider-neutral `VectorStoreClient` interface. Its batch insertion, deletion by vector IDs, deletion by document ID, and similarity search operations are implemented by `MilvusVectorStoreClient`.
* The current implementation uses `io.milvus:milvus-sdk-java:2.6.20` directly rather than Spring AI Milvus VectorStore. Direct SDK use provides explicit control over precomputed vectors, Collection Schema, Java-generated primary keys, and the compensation-deletion boundary. Spring AI remains a later replaceable implementation candidate behind `VectorStoreClient`, not a permanently rejected option.
* MySQL remains the source of truth for document and chunk content and business processing state. Milvus stores vectors and the minimum retrieval identifiers needed to map a hit back to MySQL.
* Java generates `vectorId`; Milvus does not generate primary keys for this Collection.
* `MilvusProperties` binds `enabled`, `uri`, optional `token`, `databaseName`, `collectionName`, `dimension`, `connectTimeout`, and `requestTimeout`. Required values, positive dimensions, and positive timeouts are validated, and `token` is excluded from `toString()`.
* `milvus.enabled` defaults to `false`. When it is not `true`, the `ConnectConfig`, `MilvusClientV2`, `MilvusCollectionInitializer`, and `MilvusVectorStoreClient` beans are not created, so ordinary application tests do not require Milvus.
* When enabled, `MilvusConfiguration` creates `ConnectConfig` using the configured URI, database name, millisecond connection timeout, and millisecond RPC deadline. Blank tokens are not passed to the SDK. Spring invokes the SDK client's public `close()` method when the bean is destroyed.
* `MilvusCollectionInitializer` checks `hasCollection()` at application startup. A missing Collection is created. An existing Collection is described and validated before startup continues; it is never automatically dropped, recreated, or migrated.
* The Collection uses dynamic fields disabled and the following Schema:

    * `vector_id`: `VarChar`, primary key, `autoID=false`, maximum length 64.
    * `document_id`: `Int64`.
    * `chunk_index`: `Int64`.
    * `embedding_version`: `VarChar`, maximum length 128.
    * `vector`: `FloatVector`, dimension from `MilvusProperties`, default 1024.

* The vector field uses `AUTOINDEX` with `COSINE` similarity.
* Existing-Collection validation requires exactly the five controlled fields above. It verifies `vector_id` type, primary-key flag, `autoID=false`, and maximum length; both Int64 metadata fields; `embedding_version` type and maximum length; vector type and configured dimension; Collection-level `enableDynamicField=false`, `autoID=false`, and primary field; and the vector index field, `COSINE` metric, and `AUTOINDEX` type.
* Schema or index mismatch is a fail-fast infrastructure error. The initializer never performs destructive automatic repair.
* `MilvusSchemaConstants` is a non-instantiable, non-Spring utility class shared by Collection initialization and insert-row construction.
* `VectorWriteItem` validates identifiers, document ID, chunk index, embedding version, non-empty finite float values, and defensive list copying. Store-specific vector dimension and Milvus string lengths are validated by `MilvusVectorStoreClient` rather than by the provider-neutral record.
* `VectorSearchHit` accepts any finite similarity score, including zero and negative COSINE values, and validates its identifiers and indexes.
* `MilvusVectorStoreClient.insert()` rejects null or empty batches, indexed null elements, duplicate vector IDs, overlong strings, and configured-dimension mismatches before calling Milvus. It sends each vector as a Gson `JsonArray` of JSON numbers with the exact Schema field names.
* SDK `MilvusClientException` failures during insertion are converted to `ExternalServiceException` with the original cause. A null `InsertResp` or an `InsertResp.getInsertCnt()` value different from the requested batch size is also rejected as an external-service failure.
* `MilvusCollectionInitializer` propagates SDK runtime failures during startup and throws `IllegalStateException` for an incompatible existing Schema or index, so startup fails visibly rather than continuing with an unsafe Collection.
* `deleteByVectorIds()` rejects null or empty lists, indexed null or blank IDs, overlong IDs, and duplicates. It does not trim or rewrite IDs and builds `vector_id in {vectorIds}` with a validated immutable `List<String>` template value rather than interpolating IDs into the filter.
* `deleteByDocumentId()` rejects non-positive IDs and builds `document_id == {documentId}` with the document ID as a template value.
* Both deletion methods share response validation. A null response or negative `deleteCnt` is rejected, while `deleteCnt == 0` is accepted for idempotent compensation. SDK `MilvusClientException` failures are converted to `ExternalServiceException` with the original cause.
* `search()` validates and defensively copies one finite FloatVector query whose dimension matches `MilvusProperties.dimension`; it rejects blank or overlong embedding versions and non-positive `topK` values without trimming caller input.
* Search requests use one `FloatVec`, `COSINE`, `.limit(topK)`, and the template filter `embedding_version == {embeddingVersion}`. Requested output fields are only `document_id`, `chunk_index`, and `embedding_version`; the vector field is not returned and the `vector_id` primary key comes from `SearchResp.SearchResult.getId()`.
* Search responses must contain exactly one non-null result group for the single query vector. An empty inner result list is valid, result counts may be below `topK`, and Milvus result order is retained in an immutable result list.
* Search-result mapping requires a non-blank String primary key, Long values for the two Int64 output fields, a positive document ID, a chunk index within the Java int range, an exact embedding-version match, and a finite Float score. Zero and negative COSINE scores are valid.
* A null or structurally invalid `SearchResp` is exposed as `ExternalServiceException`; SDK `MilvusClientException` failures are converted with the original cause, while unrelated runtime exceptions are not broadly caught.

Knowledge-base C1 completed capability:

* `KnowledgeChunk` and `KnowledgeChunkMapper` now provide the persistence foundation for document ID, document version, chunk index, content, nullable token count, Java-generated vector ID, embedding model/profile metadata, status, and timestamps.
* `KnowledgeChunk.tokenCount` is `Integer`. `NULL` means that no trustworthy per-chunk model token count is currently available. Neither `characterCount` nor a divided request-level token total is stored as a per-chunk token count.
* The SQL baseline uses `token_count INT UNSIGNED NULL` with `CHECK (token_count IS NULL OR token_count > 0)`.
* Chunk deletion requires both `document_id` and `document_version`.
* The first document-processing state machine is `UPLOADED -> PROCESSING -> READY`, with `PROCESSING -> FAILED` on failure.
* `claimProcessing` is a single database conditional update with `WHERE id = ? AND processing_status = 'UPLOADED'`. `markReady` and `markFailed` require the current state to be `PROCESSING`.
* `KnowledgeDocumentProcessingService.processDocument()` performs claim, reload, chunking, Embedding, chunk/vector assembly, Milvus insertion, and the final MySQL persistence step. The whole method is deliberately not transactional across HTTP, Milvus, and MySQL.
* `KnowledgeDocumentProcessingTransactionService` is a separate Spring bean. Its short `@Transactional` method atomically executes `knowledge_chunk` batch insertion and `PROCESSING -> READY`.
* The pipeline writes Milvus before MySQL. Each chunk receives one pre-generated UUID that is reused as both `KnowledgeChunk.vectorId` and `VectorWriteItem.vectorId`.
* Embedding results are matched through `EmbeddingVector.inputIndex`; count, range, duplicate, and missing-index errors are rejected before Milvus is called.
* Before calling Milvus insert, the pipeline records that a Milvus side effect may already exist. This covers response ambiguity where insertion may have succeeded before Java receives an exception.
* Any later failure triggers targeted `deleteByVectorIds` compensation using only the UUIDs generated for that processing attempt, followed by `PROCESSING -> FAILED`.
* Compensation or `markFailed` failures never replace the primary pipeline exception; they are retained as suppressed exceptions when applicable.
* The design does not claim a distributed transaction, exactly-once delivery, or automatic Collection-wide cleanup. It never drops or clears the Collection during compensation.
* `KnowledgeDocumentProcessingService` is created only when `milvus.enabled=true`; with Milvus disabled, the processing bean is not created and ordinary test contexts remain isolated.
* C1-7 completed Existing-Collection Schema and index validation. The real local `knowledge_chunk_vectors_local` Collection passed validation, and a test-owned temporary dimension-mismatch Collection failed fast and was cleaned up without changing the real Collection.

Synchronized test scope and current result through C1-8:

* Real MyBatis/MySQL tests execute `KnowledgeDocumentMapper.xml` and query actual stored column values, generated timestamps, nullable source, and generated IDs.
* Service tests cover supported extensions and case variants, metadata trimming, client-path cleanup, UTF-8/BOM/newline behavior, SHA-256 stability, controlled entity defaults, file boundaries, mapper row counts, key population, and exception propagation.
* MockMvc tests execute multipart binding and the real security filter chain with a mocked Service, covering HTTP 401, HTTP 403, administrator access, invalid form fields, DTO forwarding, unified `Result`, and safe response fields.
* Full integration tests execute real Spring Security, JWT, Controller, Service, MyBatis XML, and MySQL, and verify database row counts and intended stored values.
* Text-chunker tests use the public `split()` method only: 17 test methods and 27 executed cases, including 12 parameterized cases, with no reflection, database, or Spring context.
* Text-chunker coverage includes blank validation, short and stripped content, exact and over-limit lengths, hard cuts, boundary priority, all eight sentence terminators, overlap and full-source coverage, English word starts, overlong words, Chinese without spaces, deterministic independent results, and concurrent singleton calls.
* Embedding-focused tests cover result-object validation and immutability, property validation and API-key log protection, request and response JSON mappings, input validation, single and multiple batches, final-batch boundaries, local-to-global index conversion, supplier reordering, vector dimensions and finite values, token accumulation and missing usage, HTTP 400/401/429/500, empty or malformed responses, and invalid response models, counts, indexes, vectors, and token values.
* Embedding HTTP tests use Spring's `MockRestServiceServer`, small test vectors, and a test-only API key. Unit and HTTP tests do not call the real Bailian service or depend on a real API key.
* Milvus-focused tests cover `VectorWriteItem`, `VectorSearchHit`, property binding and validation, token log protection, disabled and enabled conditional bean assembly, blank and non-blank token handling, SDK-client bean closing, Collection creation, Existing-Collection Schema/index validation and mismatch handling, insert validation, exact `InsertReq` JSON rows, delete validation and template requests, delete response semantics, SearchReq construction, SearchResp structure, result ordering and immutability, Long-based Int64 field mapping, finite score semantics, and SDK exception conversion.
* Milvus unit tests use Mockito, `ArgumentCaptor`, `ApplicationContextRunner`, and constructor mocking to prevent the `MilvusClientV2` constructor from opening a real network connection. They do not start Milvus, create a real Collection, or execute a real insert, delete, or similarity search.
* C1 processing Mock/unit coverage has 27 passing tests for validation, claim failure, happy-path ordering, out-of-order Embedding indexes, vector-ID consistency, nullable token counts, failure-state updates, compensation, suppressed secondary failures, short-transaction control flow, and disabled-Milvus bean semantics.
* Final compile processed 80 production Java source files and completed with `BUILD SUCCESS`.
* Final focused Unit/Mock regression ran 200 tests: 27 C1 processing tests plus 173 affected B2 Milvus tests, with 0 failures, 0 errors, and 0 skipped.
* All guarded real tests are disabled by default. The final default-isolation run executed 13 tests, all 13 skipped, with 0 failures and 0 errors.
* Final ordinary `.\mvnw.cmd -B -ntp test`: 627 tests run, 0 failures, 0 errors, 13 skipped, `BUILD SUCCESS`.
* `KnowledgePersistenceIntegrationTest`: 6 real MySQL tests passed. They verify document insert/select mapping, claim CAS and second-claim rejection, READY/FAILED preconditions, real chunk batch insertion including `token_count=NULL`, vector/embedding metadata, version-scoped deletion, the transaction success path, and real rollback of chunk insertion when `markReady` fails.
* `RealKnowledgeDocumentProcessingSmokeTest`: the real MySQL -> chunker -> DashScope -> Milvus -> MySQL/READY pipeline passed. It generated two chunks using `qwen3.7-text-embedding`, dimension 1024, and profile `qwen3.7-text-embedding-1024-dense-v1`; verified nullable token counts and MySQL/Milvus vector-ID correspondence; and produced a finite COSINE self-search top-1 score close to 1.
* `RealKnowledgeDocumentProcessingFailureIntegrationTest`: two tests passed. One uses real Milvus write plus deterministic test-only post-write fault injection. The other uses real Milvus write followed by a real MySQL unique-constraint failure. Both verified `FAILED`, no pipeline chunk residue, targeted vector deletion, and preservation of unrelated fixture/sentinel data.
* The post-write fault-injection test validates the timeout-ambiguity compensation design but does not reproduce a real network-layer timeout. The final external state after an actual network timeout remains unverified.
* `RealMilvusSchemaValidationSmokeTest`: two tests passed. The real `knowledge_chunk_vectors_local` Collection passed Schema/index validation. A test-owned temporary dimension-mismatch Collection failed fast and was specifically dropped during cleanup; the real Collection was not dropped or recreated.
* `RealMilvusVectorStoreSmokeTest` passed again with real DashScope Embedding, real Milvus insert and COSINE search, `deleteByVectorIds`, `deleteByDocumentId`, and targeted cleanup. The standalone `RealEmbeddingSmokeTest` was not repeated in the final gate because C1 success/failure tests and the B2 real test already exercised the same real Embedding client and fixed model profile; its own earlier guarded real Smoke verification remains valid.
* Final cleanup confirmed zero C1 test documents and chunks, removed all pipeline/sentinel/B2 test vectors, removed the temporary mismatch Collection, and retained the real `knowledge_chunk_vectors_local` Collection.
* These real tests verify the local development MySQL and Docker Milvus environment, not a production or remote Milvus deployment.

RAG Retrieval R1 completed capability:

* Retrieval has one responsibility: find relevant knowledge and return structured evidence. Its implemented boundary is `Query -> EmbeddingClient -> query vector -> VectorStoreClient/Milvus -> VectorSearchHit -> one MySQL batch hydration -> RetrievedChunk -> RetrievalResult`.
* Retrieval does not format an LLM prompt, call an LLM, evaluate an answer, write `rag_hit_log`, or control interview workflow. Those concerns remain outside R1.
* Query Embedding reuses the existing `EmbeddingClient.embed(List<String>)`; no `QueryEmbeddingClient` or second Embedding abstraction was introduced.
* `KnowledgeRetrievalService` validates the query and `topK`, strips the query, requires exactly one non-null `EmbeddingVector` with `inputIndex == 0`, and passes the actual `EmbeddingBatchResult.profileVersion()` to Milvus search.
* Milvus remains responsible for COSINE similarity, TopK, and relevance ordering. Java does not calculate cosine similarity, re-sort by score, rerank, or apply an unverified score threshold.
* `vectorId` is the primary Milvus-to-MySQL association key because it is the Milvus primary identity, `knowledge_chunk.vector_id` is unique, and C1 writes the same generated UUID to both stores. `documentId` and `chunkIndex` are additional cross-store consistency checks; a mismatch fails with `IllegalStateException`.
* MySQL remains the source of truth for content, document metadata, and business eligibility. Retrieval requires an `ACTIVE` chunk, a `READY` document, matching current document versions, the actual query Embedding model, and the actual query profile version.
* Retrieval collects all Milvus hit vector IDs and performs one MyBatis `IN` query. It does not perform one MySQL query per hit and therefore does not introduce N+1 retrieval.
* MySQL `IN` result order is not trusted. Rows are mapped by `vectorId`, then hydrated while iterating the original Milvus hit order. Duplicate mapper `vectorId` values fail rather than being silently overwritten.
* MySQL eligibility filtering may reduce the final item count below `requestedTopK`. Missing or ineligible hits are skipped without oversampling, retrying Milvus, or automatically filling TopK.
* `vectorRank` preserves the original Milvus rank. For example, if ranks 2 and 4 fail MySQL eligibility, the returned ranks may remain `1 / 3 / 5` rather than being compressed.
* `VectorSearchHit` continues to accept every finite similarity score, including zero and negative values. R1 adds no hard score threshold.
* `RetrievalResult` and `RetrievedChunk` return structured evidence rather than a preformatted prompt string.
* `KnowledgeRetrievalService` is created only when `milvus.enabled=true`, matching the conditional `VectorStoreClient` implementation and preserving Milvus-disabled application startup.

RAG Retrieval R1 production-review corrections completed:

* Added the missing `#{vectorId}` body to the MyBatis `foreach` used by `selectRetrievableByVectorIds`.
* Corrected `kc.docuemnt_version` to `kc.document_version` and `kd.soruce` to `kd.source`.
* Corrected the hydration Map key from chunk content to `KnowledgeRetrievalRow.vectorId` while retaining duplicate-key failure semantics without a merge function.
* Added explicit query Embedding result validation for a non-null batch result, exactly one vector, a non-null vector, and `inputIndex == 0` before Milvus search.
* Changed Milvus/MySQL `documentId` and `chunkIndex` mismatch failures to `IllegalStateException`.
* Replaced unnecessary Guava `Objects` use with `java.util.Objects` and removed clearly unused imports from the Retrieval service.

RAG Retrieval R1 files and verification record:

* Production additions are `KnowledgeRetrievalRow`, `RetrievalResult`, `RetrievedChunk`, and `KnowledgeRetrievalService`. Production changes are `KnowledgeChunkMapper.java` and `KnowledgeChunkMapper.xml`.
* Test addition is `KnowledgeRetrievalServiceTest`. Retrieval coverage was also added to `KnowledgePersistenceIntegrationTest` and `RealKnowledgeDocumentProcessingSmokeTest`.
* Review-fix compile used `.\mvnw.cmd -B -ntp -DskipTests compile`, compiled 84 production source files, and completed with `BUILD SUCCESS`. Compilation alone was not treated as functional verification.
* `KnowledgeRetrievalServiceTest` ran 19 tests with 0 failures, 0 errors, and 0 skipped. It covers normal retrieval, query stripping, model/profile propagation, query-vector propagation, one mapper batch call, MySQL row reordering, partial hydration with original ranks, empty Milvus results, parameter and Embedding contract failures, cross-store mismatches, duplicate vector IDs, and finite zero/negative scores.
* `EmbeddingBatchResultTest`, `EmbeddingVectorTest`, and `VectorSearchHitTest` ran 43 tests with 0 failures, 0 errors, and 0 skipped.
* `KnowledgePersistenceIntegrationTest` ran 10 real MySQL/MyBatis tests with 0 failures, 0 errors, and 0 skipped: 6 existing C1 cases and 4 Retrieval Mapper cases. The Retrieval cases execute the real `foreach IN` SQL and verify ACTIVE/READY/current-version/model/profile filtering, exclusion of unrequested or ineligible rows, enum mapping, nullable source, and full projection mapping.
* The first real MyBatis execution failed before Retrieval SQL ran because the Maven child process had not inherited `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; MySQL reported `Driver com.mysql.cj.jdbc.Driver claims to not accept jdbcUrl, ${DB_URL}`. This was an execution-environment failure, not a Retrieval SQL bug. No production configuration, test configuration, Schema, or test logic was changed; the existing Windows User-scope values were copied only into the Maven process, and the rerun passed all 10 tests.
* `RealKnowledgeDocumentProcessingSmokeTest` ran 2 real tests with 0 failures, 0 errors, and 0 skipped. The added `shouldRetrieveProcessedDocumentThroughRealEmbeddingMilvusAndMysql` executed the real path from a semantic query through DashScope/Bailian Embedding, Milvus COSINE TopK, MyBatis/MySQL hydration, and `RetrievalResult`.
* The real query `Java 中什么 Map 适合多线程并发访问？` was not the chunk text. The test required a hit for the document created by that run, preventing old Collection data from producing a false positive. One observed run returned test-owned `documentId=91`, `chunkId=58`, `documentVersion=1`, `vectorRank=1`, score `0.784863`, and vector ID `f8539bae-f1d6-4cdf-91af-0a86fe50758f`; these values were specific to that cleaned-up Smoke Test run and are not permanent fixtures.
* Retrieval cleanup deleted only the target vector IDs, verified that a separate test sentinel remained, then deleted the sentinel separately. MySQL test chunks and documents were also removed. No shared Collection was dropped, recreated, or cleared.
* The first R1 full regression ran 651 tests with 1 failure, 85 errors, and 18 skipped because the Maven process again lacked `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. `application-test.yaml` does not currently define an independent datasource, and some ordinary database-backed tests use the `local,test` profiles. This was an execution-environment failure, not an R1 production regression, Mapper bug, Spring Context bug, or real-test-switch leak.
* No code or YAML changed for that regression failure. After copying the existing User-scope database variables only into the Maven process, `.\mvnw.cmd -B -ntp -Dtest=DatabaseConnectionTest test` ran 1 test with 0 failures, 0 errors, and 0 skipped. The subsequent `.\mvnw.cmd -B -ntp test` ran 651 tests with 0 failures, 0 errors, and 18 skipped and completed with `BUILD SUCCESS`.
* The 18 ordinary-regression skips were exactly the guarded real tests: `RealEmbeddingSmokeTest` 1, `RealMilvusVectorStoreSmokeTest` 1, `KnowledgePersistenceIntegrationTest` 10, `RealKnowledgeDocumentProcessingSmokeTest` 2, `RealKnowledgeDocumentProcessingFailureIntegrationTest` 2, and `RealMilvusSchemaValidationSmokeTest` 2. The ordinary full regression did not call real DashScope or Milvus.
* R1 technical implementation, static review, compile, Unit/Mock, real MyBatis/MySQL, real local-service Retrieval E2E, cleanup, and final regression are complete. The completed changes were committed and pushed to `origin/main` in commit `d3756e6` (`feat: complete rag retrieval r1`).

E1-0 Schema correction completed:

* `interview_question.reference_answer_snapshot TEXT NULL` was added to the final Schema snapshot in `sql/ai_interview.sql`. A MAIN question stores the reference answer frozen when that interview is created, so historical evaluation and failure retry do not read a possibly administrator-modified `question.reference_answer`. A FOLLOW_UP does not store its own reference answer; later context assembly must obtain the frozen reference answer from its parent MAIN question.
* `interview_answer.error_code` was widened from `VARCHAR(20)` to `VARCHAR(50)` because the confirmed E1 error code `LLM_RESULT_VALIDATION_FAILED` exceeds 20 characters. A real MySQL Mapper test verified the database column length and full round-trip value.
* The repository continues to keep `sql/ai_interview.sql` as the final complete Schema snapshot. No ALTER migration SQL was added.

E1-A completed capability:

* The `com.kun.aiinterview.interview` module now has only the persistence layers required at this stage: `entity`, `enums`, and `mapper`. No empty Controller, Service, DTO, or VO packages were created.
* `InterviewQuestion` maps the persisted interview-question snapshot and structure, including `referenceAnswerSnapshot`, `scoringPointsSnapshot`, question type, nullable parent question, nullable follow-up target points, ordering, status, and timestamps.
* `InterviewQuestion.category` reuses `com.kun.aiinterview.question.enums.QuestionCategory`; no duplicate interview-category enum was introduced.
* `InterviewQuestion.scoringPointsSnapshot` and `followUpTargetPoints` currently map MySQL JSON values as `String`. E1-A does not introduce a custom JSON TypeHandler.
* `InterviewAnswer` maps answer content, state, request identity, nullable `errorCode`, submission time, and timestamps. `errorCode` remains a `String`; no premature `EvaluationErrorCode` enum was introduced.
* `InterviewQuestionType` contains `MAIN` and `FOLLOW_UP`; `InterviewQuestionStatus` contains `PENDING`, `WAITING_ANSWER`, `ANSWERED`, and `SKIPPED`; `InterviewAnswerStatus` contains `SUBMITTED`, `EVALUATING`, `EVALUATED`, and `FAILED`. These values match the database CHECK constraints.
* `InterviewQuestionMapper` currently provides only `getInterviewQuestionById(Long id)`. `InterviewAnswerMapper` currently provides only `getInterviewAnswerById(Long id)` and `getInterviewAnswerByInterviewQuestionId(Long interviewQuestionId)`. Complete CRUD was deliberately not added.

E1-A production-review corrections completed:

* Corrected the Mapper statement id from `getInterviewAnserById` to `getInterviewAnswerById`; the misspelling would have left the interface method without a matching MyBatis statement and risked a `BindingException`.
* Corrected the resource filename from `InterviewAnserMapper.xml` to `InterviewAnswerMapper.xml`; the old spelling is not retained.
* Removed the unused `LocalDate` import from `InterviewAnswer` and the unused Lombok import from `InterviewQuestionMapper`.

E1-A files and verification record:

* Production additions are `InterviewQuestion`, `InterviewAnswer`, the three interview status/type enums, both minimal Mapper interfaces, and their two Mapper XML files. Production Schema changes are limited to the E1-0 corrections in `sql/ai_interview.sql`.
* Test additions are `InterviewQuestionMapperTest` and `InterviewAnswerMapperTest`. Both use `@SpringBootTest`, `@ActiveProfiles({"local", "test"})`, `@Transactional`, and `JdbcTemplate` fixtures that obey the real foreign keys and use UUID-based unique values.
* `InterviewQuestionMapperTest` covers complete MAIN mapping, isolation of `reference_answer_snapshot` from live `question.reference_answer`, JSON-to-String mapping for `scoring_points_snapshot`, FOLLOW_UP nullable fields, parent mapping, `follow_up_target_points`, and a missing ID.
* `InterviewAnswerMapperTest` covers lookup by answer ID and interview-question ID, isolation from another question, a missing ID, the real `error_code` column length, and complete persistence of `LLM_RESULT_VALIDATION_FAILED`.
* `.\mvnw.cmd -B -ntp -DskipTests compile` compiled 91 production source files and completed with `BUILD SUCCESS`. Compilation alone was not treated as functional verification.
* The first focused Mapper run reached 6 tests with 0 failures, 6 errors, and 0 skipped because its Maven process had not inherited `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; `${DB_URL}` remained unresolved. This was an execution-environment failure, not a production-code failure. No YAML or test logic was changed.
* After copying the already-existing Windows User-scope database values only into the Maven process, `.\mvnw.cmd -B -ntp "-Dtest=InterviewQuestionMapperTest,InterviewAnswerMapperTest" test` ran 6 real MySQL/MyBatis tests with 0 failures, 0 errors, and 0 skipped and completed with `BUILD SUCCESS`.
* With every `RUN_REAL_*` switch and `MILVUS_ENABLED` disabled, the ordinary `.\mvnw.cmd -B -ntp test` regression ran 657 tests with 0 failures, 0 errors, and 18 skipped and completed with `BUILD SUCCESS`.
* E1-A verification used the local real MySQL service. It did not call the real Embedding API or real Milvus. The 18 guarded skips are not evidence of real Embedding or Milvus success.
* E1-A was committed and pushed to `origin/main` in commit `cf3e03a8421a5ddecb76e6bb88069085299ddb31` (`feat: establish evaluation persistence foundation`).

E1-B completed capability:

* `EvaluationMode` separates the type of answer currently being evaluated into `MAIN_ANSWER` and `FOLLOW_UP_ANSWER`. It deliberately does not use `INITIAL` or `FINAL`; those values belong to the later evaluation-persistence phase because a MAIN answer may ultimately be stored as either phase.
* `ScoringPointSnapshot` is an immutable record containing only `scoringPointId`, the existing `QuestionPointType`, `content`, and `weight`. It does not duplicate question IDs, status, or timestamps.
* `EvaluationContext` is an immutable record containing current and MAIN answer/question identities, mode, MAIN question metadata and historical evaluation snapshots, MAIN answer content, optional follow-up question/answer content, and follow-up target point IDs. Both List fields use `List.copyOf` for defensive copying.
* `EvaluationContextService.buildContext(answerId)` is the only public assembly entry. It resolves the current answer and interview question, branches by MAIN or FOLLOW_UP, parses historical JSON, validates persistence relationships, and returns the typed Context.
* MAIN assembly uses only the current `InterviewAnswer` and the persisted MAIN `InterviewQuestion` snapshots. It does not depend on `QuestionMapper`, live `question.reference_answer`, or live `question_scoring_point` rows.
* FOLLOW_UP assembly resolves the parent MAIN question and MAIN answer, preserves both MAIN and follow-up question/answer content, and inherits the reference answer and complete scoring-point criteria from the parent MAIN snapshot. A FOLLOW_UP does not own separate evaluation criteria.
* MAIN validation requires the MAIN type, question-bank identity, no parent, a plan order, session/category/knowledge-point/question content, historical reference-answer and scoring-point snapshots, and valid answer content.
* FOLLOW_UP validation requires the FOLLOW_UP type, no question-bank identity or plan order, a parent MAIN in the same session, no independent reference-answer or scoring-point snapshot, valid target-point JSON, and an existing MAIN answer belonging to the parent.
* Scoring-point JSON must parse to a non-empty typed List with non-null elements, positive unique IDs, a non-null point type, non-blank content, weights from 1 through 100, and a total weight of exactly 100.
* Follow-up target JSON must parse to a non-empty List with non-null, unique IDs, and every target must belong to the parent MAIN scoring snapshot. E1-B deliberately does not enforce a maximum of two targets; that is a later `FollowUpPolicy` generation concern.
* JSON is parsed with the injected real Jackson `ObjectMapper` and `com.fasterxml.jackson.core.type.TypeReference`. No MyBatis JSON TypeHandler or separate Snapshot Parser was introduced because the Context Service is currently the only production consumer.
* No Service interface/Impl pair, Factory, Assembler, Resolver, or complex evaluation-specific exception hierarchy was introduced. Invalid internal persistence state currently fails with simple Java exceptions at the Context boundary.

E1-B production-review corrections completed:

* Internal persistence-state failures for missing or invalid InterviewQuestion relationships were made consistent as `IllegalStateException`, while null or missing external `answerId` input remains an argument failure.
* Added defensive identity checks so Mapper results must match the requested answer/question IDs, and the resolved MAIN answer must belong to the expected parent MAIN question.
* Replaced nullable direct equality calls with `java.util.Objects.equals` where relationship comparison could otherwise be fragile, and removed unused/conflicting imports.

E1-B files and verification record:

* Production additions are `EvaluationMode`, `ScoringPointSnapshot`, `EvaluationContext`, and `EvaluationContextService`. Test addition is `EvaluationContextServiceTest`. E1-B did not modify the database Schema, E1-A Mapper interfaces/XML, or RAG Retrieval R1.
* `.\mvnw.cmd -B -ntp -DskipTests compile` compiled 95 production source files and completed with `BUILD SUCCESS`. Compilation alone was not treated as functional verification.
* `.\mvnw.cmd -B -ntp -Dtest=EvaluationContextServiceTest test` ran 52 Unit/Mock test cases with 0 failures, 0 errors, and 0 skipped. It uses Mockito Mapper dependencies and a real Jackson `ObjectMapper`; it does not use MySQL.
* Focused coverage includes normal MAIN and FOLLOW_UP Contexts, persisted entity and relationship validation, malformed and invalid scoring snapshots, weight validation, parent/session/MAIN-answer rules, malformed and invalid target IDs, key Mapper interactions, and immutable defensive List copies.
* The first E1-B ordinary regression ran 709 tests with 1 failure, 91 errors, and 18 skipped because its Maven process had not inherited `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; `${DB_URL}` remained unresolved. This was an execution-environment failure rather than an E1-B code regression, and no code or configuration was changed for it.
* After copying the existing Windows User-scope database values only into the Maven process, `.\mvnw.cmd -B -ntp test` ran 709 tests with 0 failures, 0 errors, and 18 skipped and completed with `BUILD SUCCESS`.
* Every `RUN_REAL_*` switch and `MILVUS_ENABLED` remained disabled. Existing ordinary database-backed tests used the local MySQL test datasource during the full regression; the focused E1-B test did not. Neither run called the real Embedding API or real Milvus, and the 18 guarded skips are not evidence of external-service verification.
* E1-B technical implementation, production review, focused Unit/Mock verification, and full regression are complete. E1-B was committed and pushed in commit `a202fd0` (`feat: build evaluation context foundation`).

Existing fixed authentication decisions remain unchanged:

1. The application remains a stateless REST API.
2. Spring Security must not use HTTP Session to persist authentication.
3. Existing custom registration and credential-based login remain unchanged.
4. `POST /api/auth/register` and `POST /api/auth/login` are public endpoints.
5. Public authentication endpoints must remain usable even when the client has an expired or malformed old token.
6. A custom JWT authentication filter restores authentication for protected requests.
7. The filter must:

    * Read the `Authorization` header.
    * Accept the `Bearer` scheme only.
    * Extract and validate the JWT through `JwtTokenService`.
    * Read the user id from `sub`.
    * Query the current user by primary key.
    * Reject missing or disabled users.
    * Use the current database role rather than the stale JWT role for authorization.
    * Convert `USER` and `ADMIN` into `ROLE_USER` and `ROLE_ADMIN`.
    * Create an authenticated `Authentication` object.
    * Store it in `SecurityContextHolder`.

8. JWT claims are not the authoritative source for current user status or role.
9. Requests without a token must not be authenticated.
10. Invalid, expired, or tampered tokens on protected requests must produce HTTP 401.
11. Authenticated users without sufficient authority must receive HTTP 403.
12. Security errors must return the project's JSON `Result` structure rather than HTML or redirects.
13. The JWT authentication filter is a Servlet Filter, not a Spring MVC `HandlerInterceptor`.
14. The filter must execute once per request and be registered in the Spring Security filter chain.
15. Authorization rules remain separate from JWT parsing and authentication.

Existing fixed password-change decisions remain unchanged:

1. The endpoint is `PUT /api/users/me/password`.
2. The current user id comes from `@AuthenticationPrincipal AuthenticatedUser`, never from the request body.
3. The request body contains only `currentPassword` and `newPassword`.
4. The current password is verified with `PasswordEncoder.matches(rawPassword, encodedPassword)`.
5. The new password must differ from the current password and is stored only as a BCrypt hash.
6. `password` and `password_changed_at` are updated together in one database statement and transaction.
7. The Java service requires exactly one affected database row.
8. A successful password-change request may complete using the authentication established at the start of that request.
9. Subsequent protected requests compare JWT `iat` with the current database `password_changed_at` after loading the user and before creating `Authentication`.
10. If `password_changed_at` is null, no password-change revocation check is required.
11. If `password_changed_at` is non-null and JWT `iat` is missing, authentication fails with HTTP 401.
12. If JWT `iat` is before `password_changed_at`, the token is invalid and produces the project's JSON HTTP 401 response.
13. If JWT `iat` equals or is after `password_changed_at`, the token is accepted.
14. JWT revocation failures use an `AuthenticationException`, clear the security context, and delegate to `RestAuthenticationEntryPoint`.
15. The user must log in again with the new password to obtain a new access token.

Evaluation Core F1-F4 completed capability on 2026-09-03:

### Evaluation persistence and standard

* `AnswerEvaluation` and `AnswerEvaluationMapper` map the complete `answer_evaluation` persistence contract. MySQL generates the numeric primary key through MyBatis, and the orchestration layer checks generated-key population and affected rows.
* `EvaluationPhase` contains only `INITIAL` and `FINAL`. `DecisionAction` contains only `FOLLOW_UP`, `NEXT_MAIN`, and `FINISH`.
* `EvaluationDecision` permits exactly `INITIAL + FOLLOW_UP`, `FINAL + NEXT_MAIN`, and `FINAL + FINISH`.
* Evaluation JSON columns remain Java `String` values at the persistence boundary; no custom JSON TypeHandler was introduced.
* `EvaluationStandard.VERSION` is the fixed value `evaluation-standard-v1`.
* Level resolution depends only on Java-calculated `totalScore`: `90-100 -> EXCELLENT`, `80-89 -> GOOD`, `60-79 -> FAIR`, and `0-59 -> WEAK`. Scores outside `0-100` fail.

### Follow-up MVP policy

* Only a `MAIN_ANSWER` can produce a follow-up decision.
* A MAIN answer produces `INITIAL + FOLLOW_UP` only when the validated LLM suggestion has `followUpRecommended=true` and either `totalScore < 60` or at least one `CORE` scoring point is uncovered.
* An LLM recommendation is required because Java does not generate the natural-language follow-up question in this stage.
* A MAIN answer that does not produce a follow-up is `FINAL + NEXT_MAIN` or `FINAL + FINISH`, based on `hasNextMainQuestion`.
* A `FOLLOW_UP_ANSWER` can never produce another follow-up and is always `FINAL + NEXT_MAIN` or `FINAL + FINISH`.
* `followUpRecommended` is the validated LLM recommendation, while `decisionAction` is the Java decision. They are deliberately independent and may differ.

### RAG Retrieval Trace persistence

* `rag_retrieval_batch` represents one persisted trace for an Evaluation Retrieval that successfully returned an `EvaluationRetrievalResult`.
* `rag_hit_log` records only MySQL-eligible evidence selected as input to the Evaluation Prompt. Rejected raw Milvus hits are not persisted as used evidence and are not individually persisted elsewhere.
* `raw_hit_count` and `evidence_hit_count` distinguish `raw=0/evidence=0`, `raw>0/evidence=0`, and `raw>0/evidence>0` without storing rejected raw hits.
* `rank_no` preserves the original Milvus rank. Gaps such as `1 / 3 / 5` are valid and are not compressed.
* `rag_hit_log.retrieval_batch_id` has a database foreign key to `rag_retrieval_batch.retrieval_batch_id`.
* `answer_evaluation.retrieval_batch_id` remains a logical association and currently has no database foreign key to `rag_retrieval_batch`.
* The first trace Schema keeps the existing repeated batch metadata in `rag_hit_log` to avoid an unnecessary table redesign.
* `RagTracePersistenceService.persist()` uses an independent short `@Transactional` boundary for one batch and its zero or more eligible evidence hits. Embedding, Milvus, Prompt construction, DeepSeek, and Evaluation are outside that transaction.
* If Retrieval Trace persistence succeeds and a later Prompt, DeepSeek, validation, scoring, decision, or Evaluation insert step fails, the committed Trace is intentionally retained. The same answer may produce another retrieval batch on retry.
* The final `AnswerEvaluation` stores only the `retrievalBatchId` used by the attempt that successfully produced and inserted the Evaluation.

### Evaluation orchestration

* `EvaluationOrchestrationService.evaluate()` has no `@Transactional` annotation because its call chain includes Embedding, Milvus, and DeepSeek operations.
* The implemented order is `getByAnswerId -> buildContext -> generate retrievalBatchId -> retrieve -> persist trace -> build prompt -> DeepSeek -> validate -> calculate score -> resolve level -> decide follow-up -> insert AnswerEvaluation -> return result`.
* One UUID retrieval batch ID is generated per new attempt and reused by Trace persistence, `AnswerEvaluation`, and `EvaluationOrchestrationResult`.
* Trace persistence must complete before Prompt construction and DeepSeek evaluation. If Trace persistence fails, Evaluation does not continue and `AnswerEvaluation` is not inserted.
* A pre-existing `AnswerEvaluation` is returned before Context, Retrieval, Trace, Prompt, DeepSeek, or downstream Evaluation work begins.
* `getByAnswerId` plus database `UNIQUE(answer_id)` provides the first idempotency layer for sequential retries. The current check-then-insert flow does not solve a concurrent double-request race.
* For a FOLLOW_UP answer, Trace persistence belongs to `context.currentAnswerId()`. The resulting Evaluation also uses that current answer ID while `mainInterviewQuestionId` associates the FINAL Evaluation with the original MAIN question.
* F3/F4 orchestration deliberately does not create a FOLLOW_UP `InterviewQuestion`, update answer/question/session states, advance the interview, or generate reports and weaknesses.
* `EvaluationOrchestrationService` is created only when both `milvus.enabled=true` and `deepseek.enabled=true`. `RagTracePersistenceService` is an ordinary database `@Service` and does not require those external-service flags itself.

### F1-F4 verification baseline

* Verification date: `2026-09-03`.
* `.\mvnw.cmd -B -ntp -DskipTests compile` compiled 124 production source files and completed with `BUILD SUCCESS`.
* The final ordinary `.\mvnw.cmd -B -ntp test` regression ran 917 tests with 0 failures, 0 errors, and 18 guarded real-service skips, and completed with `BUILD SUCCESS`.
* F1 and F4 Mapper/transaction tests executed real MyBatis XML and real local MySQL 8.0.45. They verified generated keys, round trips, database constraints, zero-evidence batches, original-rank gaps, short-transaction commit, and rollback of a successful batch insert when the hit insert failed.
* DeepSeek behavior was verified only through Mock HTTP. No real DeepSeek request was made for the F1-F4 gate.
* Real Embedding and Milvus Smoke Tests remained protected and disabled for the F1-F4 full regression. The 18 skips are not evidence of real-service success.

Interview Workflow core completed capability on 2026-09-09:

### Workflow orchestration and state transitions

* `InterviewWorkflowService` completes `MAIN -> FOLLOW_UP`, `MAIN -> NEXT_MAIN`, `MAIN -> FINISH`, `FOLLOW_UP -> NEXT_MAIN`, and `FOLLOW_UP -> FINISH` dispatch without a fallback transition.
* The Workflow calculates `hasNextMainQuestion` from the persisted pending MAIN plan ordered by `plan_order`. For a FOLLOW_UP answer, the next MAIN lookup uses its parent MAIN question's `planOrder`; a FOLLOW_UP question keeps `planOrder = null`.
* First evaluation ownership uses the database CAS transition `SUBMITTED -> EVALUATING`; failed evaluation retry uses the separate database CAS transition `FAILED -> EVALUATING`.
* Evaluation failure uses the conditional transition `EVALUATING -> FAILED`. The original exception is propagated, the Session is not advanced, and a later retry is permitted.
* Session advancement uses both `version` and `current_interview_question_id` in the database CAS condition. `NEXT_MAIN` and `FINISH` increment `completed_question_count` exactly once, while `FOLLOW_UP` does not increment it.
* `InterviewWorkflowService.evaluateAnswer()` is not transactional. Evaluation, Embedding, Milvus, RAG, validation, scoring, decision, and DeepSeek calls remain outside the short database transactions used for answer claim/failure and final Workflow state changes.
* FOLLOW_UP target scoring-point IDs are selected deterministically in Java from validated uncovered scoring-point results and the MAIN scoring-point snapshot: CORE points first, stable snapshot order, at most two IDs, and no ID outside the MAIN snapshot.
* Existing `AnswerEvaluation` recovery does not call DeepSeek again. It derives the same FOLLOW_UP target IDs through the same `FollowUpTargetResolver` using persisted `scoringPointResults` and the MAIN snapshot.
* FOLLOW_UP creation and recovery are idempotent. An existing child FOLLOW_UP is reused, each MAIN has at most one FOLLOW_UP, and the Session may recover by pointing to the existing child without inserting a duplicate.
* `InterviewWorkflowTransactionService` keeps answer evaluation completion, question transition, and Session CAS advancement in one short transaction for each final action. Answer submission and question `WAITING_ANSWER -> ANSWERED` also share one short transaction.

### Interview Workflow verification baseline

* Verification date: `2026-09-09`.
* The focused Workflow test run executed 125 tests with 0 failures, 0 errors, and 0 skips, and completed with `BUILD SUCCESS`.
* The final ordinary `.\mvnw.cmd -B -ntp test` regression executed 994 tests with 0 failures, 0 errors, and 18 guarded real-service skips, and completed with `BUILD SUCCESS`.
* Workflow Mapper and transaction tests used the existing real local MySQL test environment to verify Answer CAS, Session version/current-question CAS, generated FOLLOW_UP keys, and transaction rollback.
* Real DeepSeek, Embedding, and Milvus integrations remained protected and disabled for this regression. The 18 skips are not evidence of real external-service verification.

Interview Session creation/start completed capability on 2026-09-14:

### Session plan and deterministic selection

* The implemented creation chain is `userId + difficulty -> active Session fast idempotency check -> InterviewPlanConfig -> query ENABLED Questions by difficulty -> QuestionPlanSelector -> query ENABLED scoring points -> create CREATED Session draft -> transaction -> lock user row FOR UPDATE -> recheck active Session -> insert Session -> construct all MAIN snapshots -> batch insert all MAIN questions -> reload planOrder=1 MAIN -> first MAIN PENDING -> WAITING_ANSWER -> Session CREATED -> IN_PROGRESS`.
* All MAIN questions are selected, snapshotted, and persisted once when the Session is created. They are not reselected during the interview.
* The current MVP planned counts are `EASY = 5`, `MEDIUM = 7`, and `HARD = 9`. `plannedQuestionCount` and `requiredCategories` come only from `InterviewPlanConfig`; `InterviewSessionService` does not maintain a second difficulty-to-plan mapping.
* Every plan requires a positive question count and a non-empty, duplicate-free, immutable required-category List whose size does not exceed `plannedQuestionCount`.
* `QuestionPlanSelector` receives the ENABLED candidates for one difficulty. It first selects one question for every required category, then fills the remaining positions from still-unselected candidates in stable input order.
* Selected Question IDs are unique. An insufficient unique candidate count, a missing required category, or an invalid candidate identity/category fails Session creation before persistence.
* Selection is currently deterministic. It does not use randomness, user-weakness weighting, recent-question deduplication, or any other recommendation algorithm. The returned selection List is unmodifiable.

### MAIN question snapshots

* Every persisted MAIN freezes `questionId`, `category`, `knowledgePoint`, `questionContent`, `referenceAnswerSnapshot`, `scoringPointsSnapshot`, `planOrder`, and `displayOrder` from the selected question-bank data.
* A new MAIN always uses `questionType = MAIN`, `parentQuestionId = null`, `followUpTargetPoints = null`, `status = PENDING`, and `displayOrder = planOrder * 2 - 1`. The first MAIN is activated only after all MAIN rows have been inserted.
* Newly written `scoringPointsSnapshot` JSON contains only `id`, `pointType`, `content`, and `weight`. It does not persist scoring-point `questionId`, `status`, `sortOrder`, `createdAt`, or `updatedAt`.
* The internal typed record retains its `scoringPointId` accessor for the established Evaluation code. Jackson writes the new `id` field and continues to accept the legacy `scoringPointId` field when historical snapshots are parsed.
* Snapshot construction fails fast unless scoring points are non-empty; every point has a non-null ID, the same `questionId` as the selected Question, a non-null point type, non-blank content, and positive weight; and the enabled-point weights sum to exactly 100. This prevents invalid question-bank data from entering a formal Interview Plan.

### Idempotency, concurrency, and transaction boundary

* `InterviewSessionService` is intentionally not transactional. It validates the request, obtains the plan rule, loads candidates, performs deterministic selection, loads scoring points, and prepares immutable drafts.
* Its first `getActiveSessionByUserId(userId)` call is a fast idempotency path that avoids repeating ordinary planning work when the user already has a `CREATED` or `IN_PROGRESS` Session.
* The real concurrency boundary is `@Transactional -> SELECT user ... FOR UPDATE -> getActiveSessionByUserId(userId) again -> return the existing active Session or create a new one`. The second check runs only after the same user row is exclusively locked and prevents concurrent double creation.
* This design does not use JVM `synchronized` and did not require a database Schema change.
* `InterviewSessionTransactionService` keeps user locking, the active-Session recheck, Session INSERT, MAIN snapshot construction, MAIN batch INSERT, first-MAIN activation, and Session start in one local database transaction.
* The transaction contains no DeepSeek, Embedding, Milvus, RAG, or external HTTP work. Insert counts, generated Session identity, the reloaded first MAIN, activation count, Session-start CAS, and the final database state are all validated before commit.
* Two real MySQL test threads creating a Session for the same user concurrently returned the same Session ID, and the final number of active `CREATED`/`IN_PROGRESS` Sessions was exactly one.

### Session-creation Mapper capabilities

* `InterviewSessionMapper` provides `getActiveSessionByUserId`, `insertInterviewSession`, and the strict `startSession` CAS update.
* `InterviewQuestionMapper` provides `batchInsertMainQuestions` and `getMainQuestionByPlanOrder`.
* `QuestionMapper` provides `selectEnabledQuestionsForInterview` without random ordering or a database LIMIT.
* `QuestionScoringPointMapper` provides `selectEnabledByQuestionId` ordered by `sort_order` and ID.
* `UserMapper` provides `getUserByIdForUpdate`, whose SQL contains the real `FOR UPDATE` lock.

### Interview Session creation verification baseline

* Verification date: `2026-09-14`. Java 21 clean compilation completed with `BUILD SUCCESS`.
* The focused Session Unit/Mock run executed 78 tests with 0 failures, 0 errors, and 0 skips.
* The final real Mapper/transaction run executed 4 tests with 0 failures, 0 errors, and 0 skips. It covered normal creation, generated keys and real Mapper SQL, transaction rollback, and concurrent double creation.
* The first real Mapper/transaction attempt executed 4 tests with 0 failures, 4 errors, and 0 skips because that Maven child process had not inherited `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. `${DB_URL}` remained unresolved. This was an execution-environment problem rather than a code regression; after copying the existing Windows User-scope values only into the Maven process, all 4 tests passed.
* The focused Interview Workflow regression executed 51 tests with 0 failures, 0 errors, and 0 skips, confirming that the new creation path did not break the existing Workflow core.
* The final ordinary `.\mvnw.cmd -B -ntp test` regression executed 1024 tests with 0 failures, 0 errors, and 18 guarded real-service skips, and completed with `BUILD SUCCESS`.
* `MILVUS_ENABLED=false`, `DEEPSEEK_ENABLED=false`, and every `RUN_REAL_*` switch remained disabled. The 18 skips are the guarded real external-service Smoke Tests and are not evidence of real DeepSeek, Embedding, or Milvus verification. No real DeepSeek, Embedding, or Milvus call was made for this stage.

External Interview API completed capability on 2026-09-15:

### External Interview endpoints and data boundary

* `POST /api/interviews` creates an Interview Session by difficulty or resumes the authenticated user's existing active Session. The user identity comes only from JWT-backed `AuthenticatedUser`; clients cannot submit a user ID.
* Session creation continues through `InterviewSessionService -> InterviewSessionTransactionService`. The existing user-row lock, active-Session recheck, and one-time MAIN plan persistence were not changed.
* `GET /api/interviews/current` returns the authenticated user's active Session and current question. No active Session is a successful response with `data: null`.
* `POST /api/interviews/{sessionId}/answers` validates Session ownership, the Question-to-Session relationship, Session state, and the current-question identity before accepting a new answer.
* External responses use dedicated DTO/VO records rather than exposing Entities. `InterviewQuestionResponse` does not expose reference-answer or scoring-point snapshots, follow-up target points, question-bank IDs, parent IDs, version fields, or internal Question status.
* The Evaluation response is rebuilt from the persisted `AnswerEvaluation`, including all five dimension scores, total score, level, strengths, missing points, and correction. It is not assembled from the smaller `EvaluationOrchestrationResult`.
* Persisted `strengths` and `missingPoints` remain JSON Strings and are parsed into API Lists with Jackson. The real next question is always reloaded through the final `InterviewSession.currentInterviewQuestionId`; Workflow or LLM output is not used to guess it.

### Answer submission idempotency and recovery

* A matching `requestId` with an `EVALUATED` Answer does not call AI again. It may be replayed after the Session advances or reaches `COMPLETED`; the API reloads `AnswerEvaluation`, Session, and the real current question to rebuild the response.
* A matching `SUBMITTED` Answer is reused and sent through the existing Workflow. A matching `FAILED` Answer uses the existing Workflow retry path. A matching `EVALUATING` Answer does not start a second LLM call and returns a controlled retry-later business error.
* Reusing one `requestId` for another question and submitting a different `requestId` for a question that already has an Answer are rejected.
* Database `UNIQUE(interview_question_id)` and `UNIQUE(request_id)` constraints remain the final concurrency safety net. A new-Answer `DuplicateKeyException` triggers database re-queries that recover the same idempotent request or reject a conflicting request.
* The API introduced no JVM `synchronized` block and required no database Schema change.
* The request and response spelling errors in `SubmitInterviewAnserRequest`, its fields, and `InterviewEvaluationResponse.claritySocre` were corrected. `evaluationPhase` now uses the real `EvaluationPhase` enum. Existing-Answer lookup now precedes new-request current-question validation so a valid EVALUATED replay is not rejected after Session advancement.

### External API transaction and conditional-Bean boundary

* `InterviewService.submitAnswer()` is intentionally not transactional. A new Answer continues to use `InterviewWorkflowTransactionService.submitAnswer()`, whose short transaction inserts `interview_answer` and changes the current question from `WAITING_ANSWER` to `ANSWERED` before committing.
* Embedding, Milvus, DeepSeek, Evaluation, and later Workflow transitions run outside that Answer-submission transaction.
* `InterviewService` obtains the conditional `InterviewWorkflowService` through `ObjectProvider`. Create and current-Session APIs work when AI flags are disabled, and a new Answer is not persisted unless the Workflow Bean is available.

### External Interview API verification baseline

* Verification date: `2026-09-15`.
* The focused `InterviewServiceTest,InterviewControllerTest` run executed 29 tests with 0 failures, 0 errors, and 0 skips.
* The final ordinary `.\mvnw.cmd -B -ntp test` regression executed 1053 tests with 0 failures, 0 errors, and 18 guarded real-service skips, and completed with `BUILD SUCCESS`.
* Ordinary real-MySQL Mapper and transaction tests passed. Real DeepSeek, Embedding, and Milvus tests remained protected and disabled; the 18 skips are not evidence of real external-service verification.

Interview Report completed capability on 2026-09-17:

### Report aggregation, generation, and state

* The Report state machine is `NOT_STARTED -> GENERATING -> READY`, with `GENERATING -> FAILED` recovery and `FAILED -> GENERATING` retry. Claim, READY, and FAILED transitions require the expected Session version and valid source state.
* Report aggregation uses exactly one final effective Evaluation per MAIN question in `plan_order`: FINAL takes priority and INITIAL is used only when that MAIN has no FINAL Evaluation. FOLLOW_UP is not scored as a separate question.
* Java calculates `overallScore` as the arithmetic mean of the effective MAIN `totalScore` values with `BigDecimal`, two decimal places, and `HALF_UP`. The LLM cannot recalculate or override it.
* `DeepSeekInterviewReportGenerator` reuses the existing DeepSeek JSON-completion client. It receives Session data, question snapshots from `interview_question`, validated Evaluation fields, and Java's final score; it does not receive persisted raw LLM results or RAG hit data and never writes the database.
* The Report system prompt treats all supplied question and Evaluation text as untrusted data, rejects embedded instructions, and limits the LLM to summary, strengths, weaknesses, and suggestions. Java validates the JSON and derives `result` from the existing `EvaluationStandard`; model and prompt-version metadata also come from Java configuration/constants.
* `InterviewReportService` performs DeepSeek work outside a database transaction. `InterviewReportTransactionService` atomically inserts `interview_report` and changes the Session from GENERATING to READY with `total_score`; an affected-row failure rolls both operations back.
* READY generation requests are idempotent and return the existing Report without calling the Generator. FAILED requests may claim and retry. Failures after a successful claim attempt `GENERATING -> FAILED` without replacing the original exception.
* The real Generator Bean exists only when `deepseek.enabled=true` and does not depend on Milvus. With DeepSeek disabled, the ordinary ApplicationContext still starts and generation is rejected before claiming GENERATING.

### Report API and external boundary

* `GET /api/interviews/{sessionId}/report` is an ownership-checked, side-effect-free status query. NOT_STARTED, GENERATING, and FAILED return only the Session ID and Report status; READY returns the persisted user-visible Report and treats a missing row as internal inconsistency.
* `POST /api/interviews/{sessionId}/report` reuses the existing generation state machine for first generation, FAILED retry, READY idempotency, and GENERATING rejection. Both endpoints obtain the user ID only from JWT-backed `AuthenticatedUser`.
* `InterviewReportResponse` exposes only `sessionId`, `reportStatus`, `overallScore`, `result`, `summary`, `strengths`, `weaknesses`, and `suggestions`. Persisted JSON arrays are parsed with Jackson. Database IDs, version fields, LLM metadata, timestamps, snapshots, raw Evaluation results, and retrieval identifiers are not exposed.

### Interview Report verification baseline

* Verification date: `2026-09-17`.
* The Report/API focused run executed 78 tests with 0 failures, 0 errors, and 0 skips, and completed with `BUILD SUCCESS`. It included real local MySQL Mapper and transaction tests, including rollback when READY transition failed.
* The final ordinary `.\mvnw.cmd -B -ntp test` regression executed 1117 tests with 0 failures, 0 errors, and 18 guarded real-service skips, and completed with `BUILD SUCCESS`.
* `DEEPSEEK_ENABLED=false`, `MILVUS_ENABLED=false`, and every `RUN_REAL_*` switch remained disabled. DeepSeek Report behavior was verified with Mock HTTP only; no real DeepSeek, Embedding, or Milvus request was made, and the skipped tests are not evidence of real external-service verification.

The following capabilities remain unimplemented:

* User-weakness updates
* Interview history and detail APIs
* Real DeepSeek integration verification
* Similarity-threshold policy and category retrieval filtering

Next development stages:

```text
Weakness
→ History / Detail
→ MVP Final Regression / Documentation / Freeze
```

Other capabilities that also remain unimplemented include:

* Tokenizer integration
* Trustworthy real-model per-chunk token-count calculation
* Production or remote Milvus deployment and operations verification
* Reproduction and verification of final-state recovery after a real network-layer timeout; current coverage consists of timeout-ambiguity-aware code, Mock tests, and real Milvus write plus deterministic post-write fault injection
* FAILED-document retry
* Document reprocessing
* READY-document reprocessing
* A Retrieval Controller outside the future Interview Workflow
* A Spring AI replacement implementation; it remains only a later candidate behind `EmbeddingClient`
* Markdown-heading-aware or code-block-aware chunking
* Semantic chunking
* Knowledge-document pagination, detail, or enable/disable management
* Mandatory rejection of duplicate content

Until the developer explicitly authorizes the next stage, do not implement user weaknesses, interview history/detail, password reset, further database changes, or broad unrelated refactoring.
