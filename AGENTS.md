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
管理员题库管理第二阶段：修改题目及评分点
```

The implementation, Codex code review, and comprehensive test synchronization are complete. The working-tree implementation is ready for the developer's final staged-diff review and GitHub submission.

Completed in this stage:

* `PUT /api/admin/questions/{questionId}`
* Access restricted to users with the `ADMIN` role through the existing Spring Security rule for `/api/admin/**`
* `questionId` comes only from the path variable and must be a positive value
* `UpdateQuestionRequest` accepts only editable question fields and a scoring-point list
* Internal IDs, statuses, ordering, and time fields remain under backend control
* The scoring-point list must not be empty, each element is validated, each weight must be between 1 and 100, and the total weight must equal 100
* The service queries the question before updating it and reports a business error when the question does not exist
* The question update changes only category, knowledge point, difficulty, question content, and reference answer
* The question status is preserved, and `updated_at` remains database-managed
* Scoring points use the whole-replacement strategy: delete all old points, rebuild from request order, and batch-insert all replacements
* The backend generates `sortOrder` from 1 and fixes every replacement scoring-point status to `QuestionPointStatus.ENABLED`
* Deleting zero old scoring points is treated as a business failure under the current strict data-consistency rule
* The replacement batch-insert count must equal the new scoring-point list size
* The question update, old scoring-point deletion, and replacement insertion run in one public Service transaction
* Runtime failures roll back both the question update and the deletion of old scoring points
* The success response uses the existing `Result<Void>` structure and does not expose a complete `Question` entity
* Real MyBatis tests cover question querying and updating, scoring-point deletion, and replacement insertion
* Service tests cover parameter validation, affected-row checks, exception propagation, entity construction, and Mapper call order
* A Spring transaction integration test verifies that the original question and both old scoring points are restored when replacement insertion fails
* MockMvc and full integration tests cover HTTP 401, HTTP 403, invalid administrator requests, successful administrator updates, status preservation, replacement ordering, and isolation from other questions

Previously completed question-management capability remains unchanged:

* `POST /api/admin/questions`
* Question creation and scoring-point creation remain in one transaction
* New question and scoring-point statuses remain fixed to `ENABLED`
* Question creation still uses generated-key population, backend-generated scoring-point order, and affected-row checks

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
待当前阶段提交后由开发者明确
```

Until the developer explicitly defines the next stage, do not automatically implement:

* Question pagination, question detail queries, enable, disable, or delete operations
* Interview sessions
* Answer submission or evaluation
* RAG
* LLM
* Milvus
* Knowledge-base upload
* Interview reports or user weaknesses
* Password reset
* Refactoring the existing JWT, authentication, Spring Security, or user modules
* Database table changes
* Unrelated dependencies
* Broad cross-module refactoring
* Changes to the local `docs/learning` learning-material management rules
