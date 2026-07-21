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
Authentication module — logged-in password change and old JWT invalidation
```

JWT access-token issuance, standalone validation, and stateless Spring Security request authentication and authorization are complete.

The completed security chain includes `SecurityConfiguration`, `JwtAuthenticationFilter`, `AuthenticatedUser`, JSON 401 and 403 handlers, and focused security tests.

The read-only current-user endpoint is complete. The current increment is authenticated password change and invalidation of access tokens issued before the password change. The approved endpoint path is `PUT /api/users/me/password`.

Completed in this stage:

* User entity, `UserRole` and `UserStatus`
* `UserMapper` and MyBatis XML mapping, including user queries by id, account and email, plus user insertion
* `BCryptPasswordEncoder` exposed through a `PasswordEncoder` bean
* Complete registration flow: JSON → `RegisterRequest` DTO → `@Valid` → `AuthController` → `AuthService` → `UserMapper` → MySQL → `Result` JSON
* `POST /api/auth/register`
* Account and email duplicate pre-checks, with database unique constraints and `DuplicateKeyException` translated to `BusinessException` as the concurrency fallback
* `LoginRequest` with Jakarta Bean Validation and password-safe `toString`
* `LoginResponse` containing userId, account, username, role, accessToken, tokenType and expiresInSeconds
* Access token excluded from `LoginResponse.toString()`
* Complete login flow: JSON → `LoginRequest` → `@Valid` → `AuthController` → `AuthService` → `UserMapper` → BCrypt password verification → account status validation → JWT generation → `LoginResponse` → `Result` JSON
* `POST /api/auth/login`
* Generic credential-error response for missing users and incorrect passwords
* Password verification before disabled-account status disclosure
* Disabled accounts with an incorrect password still receive the generic credential error
* `Result<T>` unified response structure, `BusinessException`, and `GlobalExceptionHandler`
* Unified handling for validation failures, unreadable JSON request bodies, business exceptions and unknown exceptions
* MyBatis Mapper integration tests, AuthService registration and login integration tests, and MockMvc AuthController registration and login integration tests
* JJWT 0.13.0 dependency setup using `jjwt-api`, `jjwt-impl` and `jjwt-jackson`
* Type-safe JWT configuration through `JwtProperties`
* JWT secret loaded externally through `JWT_SECRET`
* Base64 secret decoding and `SecretKey` creation
* HS256 access-token generation after successful password and user-status validation
* Standard claims: `sub`, `iss`, `iat` and `exp`
* Custom claims: `account` and `role`
* `JwtParser` configured with signature verification and required issuer
* Token expiration validation
* Dedicated test profile JWT configuration using a non-production test secret
* Unit tests for normal parsing, expiration, tampering, wrong key and wrong issuer
* Integration tests confirming successful login returns a valid access token
* Claims minimization: passwords, password hashes, email, username, status and full `User` objects are not stored in JWT
* Stateless Spring Security configuration with form login, HTTP Basic, logout, request caching and HTTP Session persistence disabled
* Public `POST /api/auth/register` and `POST /api/auth/login` endpoints that ignore malformed or expired old tokens
* `JwtAuthenticationFilter` registered once in the Spring Security filter chain
* Bearer-token authentication using `JwtTokenService`, the JWT `sub` claim and a current database user lookup
* Current database status and role used as the trusted authorization source
* `AuthenticatedUser` stored as the authenticated principal in `SecurityContextHolder`
* JSON HTTP 401 and 403 responses through `RestAuthenticationEntryPoint` and `RestAccessDeniedHandler`
* Focused tests for the JWT filter, authenticated principal, security configuration, authentication entry point and access-denied handler
* `GET /api/users/me` using `@AuthenticationPrincipal AuthenticatedUser`
* Database-backed current-user response containing userId, account, username, email, role, status and createdAt without sensitive fields
* Focused `UserControllerIntegrationTest` coverage for missing and valid access tokens

Fixed authentication design decisions:

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
10. Invalid, expired or tampered tokens on protected requests must produce HTTP 401.
11. Authenticated users without sufficient authority must receive HTTP 403.
12. Security errors must return the project's JSON `Result` structure rather than HTML or redirects.
13. The JWT authentication filter is a Servlet Filter, not a Spring MVC `HandlerInterceptor`.
14. The filter should execute once per request and be registered in the Spring Security filter chain.
15. Authorization rules remain separate from JWT parsing and authentication.

Fixed password-change design decisions:

1. The approved endpoint is `PUT /api/users/me/password`.
2. The current user id must come from `@AuthenticationPrincipal AuthenticatedUser`, never from the request body.
3. The request body contains only `currentPassword` and `newPassword`.
4. The current password must be verified with `PasswordEncoder.matches(rawPassword, encodedPassword)`.
5. The new password must be different from the current password and must be stored only as a BCrypt hash.
6. `password` and `password_changed_at` must be updated together in one database statement and transaction.
7. The Java service must require exactly one affected database row.
8. A successful password-change request may complete using the authentication established at the start of that request.
9. Subsequent protected requests must compare the JWT `iat` with the current database `password_changed_at` after loading the user and before creating `Authentication`.
10. If `password_changed_at` is null, no password-change revocation check is required.
11. If `password_changed_at` is non-null and JWT `iat` is missing, authentication must fail with HTTP 401.
12. If JWT `iat` is before `password_changed_at`, the token is invalid and must produce the project's JSON HTTP 401 response.
13. If JWT `iat` equals or is after `password_changed_at`, the token is accepted for the current stage.
14. JWT revocation failures must use an `AuthenticationException`, clear the security context and delegate to `RestAuthenticationEntryPoint`.
15. The user must log in again with the new password to obtain a new access token.

Components for the current increment:

* `ChangePasswordRequest`
* `UserService`
* `UserController`
* `UserMapper` and `UserMapper.xml`
* `JwtAuthenticationFilter`
* Focused `UserControllerIntegrationTest`
* Focused `JwtAuthenticationFilterTest`
* Focused `UserMapperTest`

Exact class names may be adjusted slightly to fit the existing package structure, but package organization must remain business-module-first and responsibilities must remain clear.

Current allowed scope:

* Implementing `PUT /api/users/me/password`
* Reading the current principal through `@AuthenticationPrincipal AuthenticatedUser`
* Validating `currentPassword` and `newPassword` through `ChangePasswordRequest`
* Passing only the authenticated user id and password-change request from `UserController` to `UserService`
* Querying the current user through `UserMapper.getUserById(userId)`
* Rejecting a missing or disabled current user
* Verifying the current password and rejecting a reused password through `PasswordEncoder`
* BCrypt-encoding the new password
* Updating `password` and `password_changed_at` together
* Requiring exactly one affected update row and using a transaction boundary
* Returning the response through `Result.success()`
* Comparing JWT `iat` and database `password_changed_at` in `JwtAuthenticationFilter`
* Returning JSON HTTP 401 for access tokens issued before the password change
* Verifying password change, old-token invalidation, old-password rejection and new-password login through the real Spring Security filter chain, `JwtTokenService`, `PasswordEncoder` and test database

Current forbidden scope:

* Refresh tokens
* Redis token storage
* Redis blacklist
* Logout token invalidation
* Token rotation
* OAuth2
* OpenID Connect
* Session-based login
* Remember-me
* Complex RBAC
* Method-level authorization unless explicitly requested
* Email verification
* Password reset
* RAG
* LLM
* Unrelated user-profile or business features
