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
* MyBatis 3.0.5
* MySQL 8.0.45
* BCrypt password hashing via spring-security-crypto
* JJWT 0.13.0 for JWT access-token signing and validation
* Spring Security for stateless request authentication and authorization

Planned technologies used only when their corresponding modules are developed:

* Redis
* Spring Mail
* Knife4j
* LangChain4j
* DeepSeek API
* Milvus
* Docker
* Docker Compose

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

The current database version is v1.1.

It contains 12 core tables:

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

`RealEmbeddingSmokeTest` and `RealMilvusVectorStoreSmokeTest` are opt-in real integration / Smoke Tests. They are not Mock tests or ordinary unit tests, and they must be skipped during an ordinary `mvn test` run unless explicitly enabled.

Their explicit opt-in switches are:

* `RealEmbeddingSmokeTest`: `RUN_REAL_EMBEDDING_TEST=true`
* `RealMilvusVectorStoreSmokeTest`: `RUN_REAL_MILVUS_TEST=true`

Both tests must retain two layers of protection: `@EnabledIfEnvironmentVariable` and `Assumptions.assumeTrue(...)`. Agents must not remove or weaken either layer, make the tests run by default, store `RUN_REAL_*` in Git-managed configuration, persist the switches in Windows user or system environment variables, or enable them during an ordinary full test run. Clear temporary switches promptly after the real test finishes.

Formal real Smoke Test verification must use Maven / Surefire with a temporary `RUN_REAL_*` environment variable and the test's own `@ActiveProfiles("local")`. Do not use an IntelliJ IDEA forced run of a conditionally disabled JUnit test as formal evidence: IDEA may inject `-Djunit.jupiter.conditions.deactivate=org.junit.*Enabled*Condition`, bypassing the `@EnabledIfEnvironmentVariable` layer.

`application-local.yaml` is the local real-service configuration file. It must remain ignored and outside Git. Agents must not expose secrets read from it or copy real API keys, database passwords, JWT secrets, Milvus tokens, or other local credentials into `application-test.yaml` merely to make tests pass. The real Smoke Tests use `@ActiveProfiles("local")`; ordinary automated tests must retain the isolated test configuration.

Before an ordinary full test run, ensure `RUN_REAL_EMBEDDING_TEST` and `RUN_REAL_MILVUS_TEST` are unset, then use:

```text
.\mvnw.cmd -B -ntp test
```

The real Smoke Tests must be reported as skipped in that run. Keep the existing `application-test.yaml` isolation: test JWT configuration, test Embedding configuration, and `milvus.enabled=false`.

Real Milvus Smoke Tests must use unique test data and clean up only data created by that test run. They must never drop a Collection or clear an entire Collection. After deletion, verify that non-target data remains. Use Awaitility or equivalent polling for eventual consistency instead of fixed `Thread.sleep` as the primary synchronization mechanism.

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
知识库模块第二阶段 B2：Milvus 向量存储基础设施、批量写入、删除与相似度检索
```

The B2 Milvus infrastructure and batch-insert increment was completed on 2026-08-02. The deletion and similarity-search production review, production corrections, focused Mock tests, and full regression verification were completed on 2026-08-03. The working tree is ready for the developer's review and submission.

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
* The result is not written to `knowledge_chunk`, does not update `knowledge_document.processing_status`, and does not generate vectors or embedding metadata.

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
* `MilvusCollectionInitializer` checks `hasCollection()` at application startup. An existing Collection is left unchanged; a missing Collection is created. Existing-Collection Schema consistency validation is not implemented.
* The Collection uses dynamic fields disabled and the following Schema:

    * `vector_id`: `VarChar`, primary key, `autoID=false`, maximum length 64.
    * `document_id`: `Int64`.
    * `chunk_index`: `Int64`.
    * `embedding_version`: `VarChar`, maximum length 128.
    * `vector`: `FloatVector`, dimension from `MilvusProperties`, default 1024.

* The vector field uses `AUTOINDEX` with `COSINE` similarity.
* `MilvusSchemaConstants` is a non-instantiable, non-Spring utility class shared by Collection initialization and insert-row construction.
* `VectorWriteItem` validates identifiers, document ID, chunk index, embedding version, non-empty finite float values, and defensive list copying. Store-specific vector dimension and Milvus string lengths are validated by `MilvusVectorStoreClient` rather than by the provider-neutral record.
* `VectorSearchHit` accepts any finite similarity score, including zero and negative COSINE values, and validates its identifiers and indexes.
* `MilvusVectorStoreClient.insert()` rejects null or empty batches, indexed null elements, duplicate vector IDs, overlong strings, and configured-dimension mismatches before calling Milvus. It sends each vector as a Gson `JsonArray` of JSON numbers with the exact Schema field names.
* SDK `MilvusClientException` failures during insertion are converted to `ExternalServiceException` with the original cause. A null `InsertResp` or an `InsertResp.getInsertCnt()` value different from the requested batch size is also rejected as an external-service failure.
* `MilvusCollectionInitializer` currently propagates SDK runtime failures during startup so Spring startup fails visibly; it does not silently continue after a failed existence check or Collection creation.
* `deleteByVectorIds()` rejects null or empty lists, indexed null or blank IDs, overlong IDs, and duplicates. It does not trim or rewrite IDs and builds `vector_id in {vectorIds}` with a validated immutable `List<String>` template value rather than interpolating IDs into the filter.
* `deleteByDocumentId()` rejects non-positive IDs and builds `document_id == {documentId}` with the document ID as a template value.
* Both deletion methods share response validation. A null response or negative `deleteCnt` is rejected, while `deleteCnt == 0` is accepted for idempotent compensation. SDK `MilvusClientException` failures are converted to `ExternalServiceException` with the original cause.
* `search()` validates and defensively copies one finite FloatVector query whose dimension matches `MilvusProperties.dimension`; it rejects blank or overlong embedding versions and non-positive `topK` values without trimming caller input.
* Search requests use one `FloatVec`, `COSINE`, `.limit(topK)`, and the template filter `embedding_version == {embeddingVersion}`. Requested output fields are only `document_id`, `chunk_index`, and `embedding_version`; the vector field is not returned and the `vector_id` primary key comes from `SearchResp.SearchResult.getId()`.
* Search responses must contain exactly one non-null result group for the single query vector. An empty inner result list is valid, result counts may be below `topK`, and Milvus result order is retained in an immutable result list.
* Search-result mapping requires a non-blank String primary key, Long values for the two Int64 output fields, a positive document ID, a chunk index within the Java int range, an exact embedding-version match, and a finite Float score. Zero and negative COSINE scores are valid.
* A null or structurally invalid `SearchResp` is exposed as `ExternalServiceException`; SDK `MilvusClientException` failures are converted with the original cause, while unrelated runtime exceptions are not broadly caught.

Synchronized test scope and current result:

* Real MyBatis/MySQL tests execute `KnowledgeDocumentMapper.xml` and query actual stored column values, generated timestamps, nullable source, and generated IDs.
* Service tests cover supported extensions and case variants, metadata trimming, client-path cleanup, UTF-8/BOM/newline behavior, SHA-256 stability, controlled entity defaults, file boundaries, mapper row counts, key population, and exception propagation.
* MockMvc tests execute multipart binding and the real security filter chain with a mocked Service, covering HTTP 401, HTTP 403, administrator access, invalid form fields, DTO forwarding, unified `Result`, and safe response fields.
* Full integration tests execute real Spring Security, JWT, Controller, Service, MyBatis XML, and MySQL, and verify database row counts and intended stored values.
* Text-chunker tests use the public `split()` method only: 17 test methods and 27 executed cases, including 12 parameterized cases, with no reflection, database, or Spring context.
* Text-chunker coverage includes blank validation, short and stripped content, exact and over-limit lengths, hard cuts, boundary priority, all eight sentence terminators, overlap and full-source coverage, English word starts, overlong words, Chinese without spaces, deterministic independent results, and concurrent singleton calls.
* Embedding-focused tests cover result-object validation and immutability, property validation and API-key log protection, request and response JSON mappings, input validation, single and multiple batches, final-batch boundaries, local-to-global index conversion, supplier reordering, vector dimensions and finite values, token accumulation and missing usage, HTTP 400/401/429/500, empty or malformed responses, and invalid response models, counts, indexes, vectors, and token values.
* Embedding HTTP tests use Spring's `MockRestServiceServer`, small test vectors, and a test-only API key. Unit and HTTP tests do not call the real Bailian service or depend on a real API key.
* Milvus-focused tests cover `VectorWriteItem`, `VectorSearchHit`, property binding and validation, token log protection, disabled and enabled conditional bean assembly, blank and non-blank token handling, SDK-client bean closing, Collection existence and Schema construction, insert validation, exact `InsertReq` JSON rows, delete validation and template requests, delete response semantics, SearchReq construction, SearchResp structure, result ordering and immutability, Long-based Int64 field mapping, finite score semantics, and SDK exception conversion.
* Milvus tests use Mockito, `ArgumentCaptor`, `ApplicationContextRunner`, and constructor mocking to prevent the `MilvusClientV2` constructor from opening a real network connection. They do not start Milvus, create a real Collection, or execute a real insert, delete, or similarity search.
* `.\mvnw.cmd -B -ntp -DskipTests compile`: `BUILD SUCCESS`.
* `.\mvnw.cmd -B -ntp "-Dtest=VectorWriteItemTest,VectorSearchHitTest,MilvusPropertiesTest,MilvusConfigurationTest,MilvusCollectionInitializerTest,MilvusVectorStoreClientTest" test`: 159 tests run, 0 failures, 0 errors, 0 skipped, `BUILD SUCCESS`.
* Full `.\mvnw.cmd -B -ntp test`: 573 tests run, 0 failures, 0 errors, 0 skipped, `BUILD SUCCESS`.
* 真实百炼 API 端到端联调尚未执行。
* No real Milvus service was started, no real Collection was created, and no real insert, delete, or similarity search was executed. Real COSINE ordering and the final state after a real network timeout remain unverified. Successful compilation, Spring context isolation, and Mock tests do not constitute real Milvus integration verification.

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

Next development stage:

```text
知识库模块第二阶段 B2 后续增量：
真实 Milvus 冒烟联调或文档处理流水线设计
```

The B2 infrastructure, insert, deletion, and similarity-search increments are ready for the developer's review and submission. Do not add Docker Milvus, real Milvus integration code, or the document-processing pipeline until the developer explicitly authorizes the next increment.

The next increment must first complete the following design work:

1. Prefer a real local Milvus smoke verification of insert, search, deletion, COSINE ordering, and timeout uncertainty when the environment is available.
2. Decide how startup should detect an existing Collection whose Schema differs from the configured Schema, without adding automatic destructive migration.
3. Design `knowledge_document` transitions through `PROCESSING`, `READY`, and `FAILED`.
4. Design idempotency, compensation, and retry behavior for partial MySQL, Embedding, and Milvus failures.
5. Define the complete document-chunk ingestion transaction boundary and recovery procedure.

The Embedding HTTP client and vector-generation capability are implemented and verified with mock HTTP. Real supplier integration, vector persistence, and RAG are separate completion states.

The following capabilities are not implemented in the current stage:

* Tokenizer integration
* Real model token-count calculation
* Real Bailian API end-to-end integration
* Existing-Collection Schema consistency validation
* Docker or real Milvus deployment
* Real Milvus end-to-end integration
* Verified insertion into a real Milvus Collection
* Verified similarity search against real Milvus
* `knowledge_chunk` database insertion
* A document-processing pipeline
* `knowledge_document.processing_status` advancement from `UPLOADED` to `PROCESSING`, `READY`, or `FAILED`
* MySQL and Milvus partial-failure compensation and retry
* Document reprocessing
* RAG retrieval
* A Spring AI replacement implementation; it remains only a later candidate behind `EmbeddingClient`
* Markdown-heading-aware or code-block-aware chunking
* Semantic chunking
* Knowledge-document pagination, detail, or enable/disable management
* Mandatory rejection of duplicate content

Until the developer explicitly authorizes a later stage, also do not implement interview sessions, answer submission or evaluation, LLM integration, interview reports, user weaknesses, password reset, database changes, or broad unrelated refactoring.
