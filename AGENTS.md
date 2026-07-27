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
知识库模块第一阶段：管理员上传知识文档并保存原文元数据
```

The implementation, first Codex production review, production corrections, focused-test synchronization, and full regression verification were completed on 2026-07-27. The working tree is ready for the developer's final diff review and submission.

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

Synchronized test scope and current result:

* Real MyBatis/MySQL tests execute `KnowledgeDocumentMapper.xml` and query actual stored column values, generated timestamps, nullable source, and generated IDs.
* Service tests cover supported extensions and case variants, metadata trimming, client-path cleanup, UTF-8/BOM/newline behavior, SHA-256 stability, controlled entity defaults, file boundaries, mapper row counts, key population, and exception propagation.
* MockMvc tests execute multipart binding and the real security filter chain with a mocked Service, covering HTTP 401, HTTP 403, administrator access, invalid form fields, DTO forwarding, unified `Result`, and safe response fields.
* Full integration tests execute real Spring Security, JWT, Controller, Service, MyBatis XML, and MySQL, and verify database row counts and intended stored values.
* `.\mvnw.cmd -B -ntp -DskipTests compile`: `BUILD SUCCESS`.
* Knowledge-related tests: 62 tests run, 0 failures, 0 errors, 0 skipped, `BUILD SUCCESS`.
* Full `.\mvnw.cmd -B -ntp test`: 307 tests run, 0 failures, 0 errors, 0 skipped, `BUILD SUCCESS`.
* All 245 pre-existing authentication, user, security, and question-management regression tests passed.

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
建议：知识库模块第二阶段：文档处理、切片与处理状态管理
```

The first stage is ready for the developer's final review and submission. Do not start the suggested second stage until the developer explicitly authorizes it.

The following capabilities are not implemented in the current stage:

* Document chunking
* Embedding generation
* Vector-database insertion
* Milvus integration
* RAG retrieval
* Document processing-status advancement
* Document reprocessing
* Knowledge-document pagination, detail, or enable/disable management
* Mandatory rejection of duplicate content

Until the developer explicitly authorizes a later stage, also do not implement interview sessions, answer submission or evaluation, LLM integration, interview reports, user weaknesses, password reset, database changes, or broad unrelated refactoring.
