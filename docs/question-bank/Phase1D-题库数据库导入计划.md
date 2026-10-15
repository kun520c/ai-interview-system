# Phase 1D Database Import Plan

Status: **pre-import plan**, created before any database write.

## Canonical input and derived artifact

- Canonical source: `docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.md`
- Canonical SHA-256: `6278FD0CA2BF48C6256FA9BFD1F72FFA1925BB1A70EB2BB11FA67C5F8A06E296`
- Derived request artifact: `docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json`
- Derived JSON SHA-256: `CE6FFF9BD42B064009B01C0300E2DA5DA16E73D04C363E3CF7F2B351CC1B6FD3`
- Static validation passed: 21 questions, 97 scoring points, EASY=5, MEDIUM=7, HARD=9; valid DTO/database text limits and enums; one question per configured category/difficulty slot; unique `(category, knowledgePoint)` keys; each question's scoring weights total 100.
- The downloaded source, repository root copy, SQL folder copy, and canonical docs copy have the same SHA-256. The supplied Grok review records the prior findings; the final source's section 5.4 records all P1/P2 recommendations resolved.

## Resolved environment and pre-import audit

- Profile: `local`, from the existing `AiInterview-Local-Real` Run Configuration.
- MySQL target: `localhost:3306/ai_interview`. The configuration was read without displaying credentials; the read-only connection confirmed `SELECT DATABASE() = ai_interview`.
- Normal local Milvus target: `default/knowledge_chunk_vectors_local`. The importer invocation will override `milvus.enabled=false`, so the Milvus client and collection initializer are not created and this phase makes zero Milvus writes.
- Pre-import counts: `question=0`, `question_scoring_point=0`, `enabled question=0`.
- Existing category/difficulty distribution: none. Existing question IDs and knowledgePoint values: none.
- Test/evaluation schemas are not import targets. The importer checks the active `local` profile and resolved schema before entering its write transaction.

## Import mechanism and atomicity

A temporary, local-profile-only runner will be added at `src/main/java/com/kun/aiinterview/question/service/Phase1QuestionBankImportRunner.java`. It requires the explicit one-shot property `phase1.question-bank.import.enabled=true`. It reads the derived JSON, validates all records before writing, and invokes `QuestionAdminService.createQuestion(CreateQuestionRequest)` once per record. It does not reproduce mapper SQL or issue raw SQL INSERTs.

The runner wraps all 21 service calls in one outer Spring `TransactionTemplate` transaction at repeatable-read isolation. `createQuestion()` uses the default `REQUIRED` propagation, so its question and scoring-point inserts join that transaction. The runner verifies the exact stored question and scoring-point values, statuses, sort order, counts, weight sums, configured plan selections, and in-memory snapshots before returning successfully. Any exception or failed assertion marks the transaction for rollback; no cleanup deletes are needed and no broad delete is used.

Plan and snapshot checks call `InterviewQuestionPlanSnapshotService.prepareSnapshot()` and `InterviewQuestionSnapshotFactory.createMainQuestion()` within the same transaction. The factory receives only an in-memory dummy session ID; no interview-session or interview-question row is inserted.

## Expected post-import state

- `question=21`, enabled questions `=21`
- `question_scoring_point=97`
- EASY/MEDIUM/HARD plan sizes `=5/7/9`
- Each question has at least one enabled scoring point and enabled weight sum 100.

## External-service boundary

The importer does not call Embedding, DeepSeek, or Jev clients, does not run RAG, and does not start frontend tests. Milvus is explicitly disabled for the one-shot process; only MySQL question-bank tables are written. Read-only plan and snapshot verification runs against the imported MySQL records.

## Verification

After import, a separate read-only MySQL audit will produce the per-question ID and integrity table and recheck that the formal import target is local. Focused existing DTO, service, mapper, plan/snapshot, and environment-isolation tests will use the isolated test configuration; no evaluation tests will run. Test results and exact counts will be recorded in `Phase1D-题库数据库导入验证.md`.
