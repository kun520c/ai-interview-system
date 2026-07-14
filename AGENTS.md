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

Planned technologies used only when their corresponding modules are developed:

* Redis
* JWT
* Spring Security
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
Authentication module — phase one: the complete user registration HTTP flow is complete
```

Completed in this stage:

* User entity, `UserRole` and `UserStatus`
* `UserMapper` and MyBatis XML mapping, including user queries by id, account and email, plus user insertion
* `BCryptPasswordEncoder` exposed through a `PasswordEncoder` bean
* Complete registration flow: JSON → `RegisterRequest` DTO → `@Valid` → `AuthController` → `AuthService` → `UserMapper` → MySQL → `Result` JSON
* `POST /api/auth/register`
* Account and email duplicate pre-checks, with database unique constraints and `DuplicateKeyException` translated to `BusinessException` as the concurrency fallback
* `Result<T>` unified response structure, `BusinessException`, and `GlobalExceptionHandler`
* Unified handling for validation failures, unreadable JSON request bodies, business exceptions and unknown exceptions
* MyBatis Mapper integration tests, AuthService integration tests and MockMvc AuthController integration tests

Current allowed scope:

* Login feature development

Current forbidden scope:

* JWT
* Spring Security
* Redis
* Email verification
* Password reset
* RAG
* LLM
