# Phase 9B Final Findings Ledger

Authoritative source: Commander PHASE 9B consolidated repair list.

Backend freeze: `20a933ae8092ef908c2f03193c0b0f9547da71c1`

Independence reviews: ENG / UX / BLIND. Commander IDs below are the only accepted production findings.

P2-11 is bookkeeping, not a 21st production defect.

All items: **CLOSED**.

---

## FINAL-P1-01 SESSION EVALUATION GATE / CONCURRENT REALIGN RACE

- Source: ENG-04, BLIND-01, BLIND-09 (test gap)
- Commander severity: P1
- Repair files:
  - `frontend/src/features/interview/composables/useInterviewSession.ts`
  - `frontend/src/features/interview/views/InterviewSessionView.vue`
- Tests:
  - `frontend/tests/InterviewSessionView.spec.ts`
    - refuses retry while check current is in flight and refuses check while retrying
    - does not stamp a Q1 evaluation onto Q2 after current advanced during Q1 retry
    - does not apply user A evaluation onto user B question or A gate storage
    - keeps the evaluation-gate answeredQuestion id equal to the SubmitRecord interviewQuestionId
- Status: CLOSED

## FINAL-P1-02 SESSION BOOTSTRAP IDENTITY / STALE NULL

- Source: BLIND-02, BLIND-09 (test gap)
- Commander severity: P1
- Repair files:
  - `frontend/src/features/interview/composables/useInterviewSession.ts`
- Tests:
  - `frontend/tests/InterviewSessionView.spec.ts`
    - ignores a stale bootstrap null after logout and does not hijack user B
    - does not issue a secondary dashboard redirect when /current 401 clears identity
    - does not erase a newer adopted session when an older bootstrap refresh returns stale null
    - does not navigate after the session view unmounts during bootstrap
- Status: CLOSED

## FINAL-P1-03 CREATE / RESUME CROSS-IDENTITY RACE

- Source: ENG-02, BLIND-03
- Commander severity: P1
- Repair files:
  - `frontend/src/features/interview/views/CreateInterviewView.vue`
- Tests:
  - `frontend/tests/CreateInterviewView.spec.ts`
    - does not adopt user A session into user B store or kill B GET
    - does not navigate user B after user A uncertain POST verification
- Status: CLOSED

## FINAL-P2-01 KNOWLEDGE UPLOAD CATEGORY SNAPSHOT

- Source: ENG-01
- Commander severity: P2
- Repair files:
  - `frontend/src/features/admin/knowledge/composables/useAdminKnowledge.ts`
- Tests:
  - `frontend/tests/AdminKnowledge.spec.ts` (SPRING upload survives identity/session change while UI category becomes REDIS)
- Status: CLOSED

## FINAL-P2-02 ROUTE-SCOPED LATE NAVIGATION

- Source: ENG-03, ENG-05
- Commander severity: P2
- Repair files:
  - `frontend/src/features/interview/composables/useInterviewDetail.ts`
  - `frontend/src/features/history/composables/useInterviewHistory.ts`
  - `frontend/src/features/admin/questions/composables/useAdminQuestionList.ts`
  - `frontend/src/features/admin/questions/composables/useAdminQuestionForm.ts`
- Tests:
  - `frontend/tests/InterviewDetailView.spec.ts` (stale IN_PROGRESS detail must not redirect after leave)
  - `frontend/tests/HistoryView.spec.ts` (stale canonicalize must not pull back after leave)
  - `frontend/tests/AdminQuestions.spec.ts` (stale list 403 must not navigate a later route)
- Status: CLOSED

## FINAL-P2-03 RESUME NOTICE USER SCOPE

- Source: BLIND-04
- Commander severity: P2
- Repair files:
  - `frontend/src/features/interview/navigation/sessionNavigationContext.ts`
  - `frontend/src/features/interview/composables/useInterviewSession.ts`
  - `frontend/src/features/interview/views/CreateInterviewView.vue`
- Tests:
  - `frontend/tests/sessionNavigationContext.spec.ts` (cross-account notice is not consumed)
- Status: CLOSED

## FINAL-P2-04 COMPLETED DETAIL EMPTY TIMELINE

- Source: BLIND-05
- Commander severity: P2
- Repair files:
  - `frontend/src/features/interview/composables/useInterviewDetail.ts`
  - `frontend/src/features/interview/views/InterviewDetailView.vue`
- Tests:
  - `frontend/tests/InterviewDetailView.spec.ts` (COMPLETED + empty questions is ERROR/UNAVAILABLE, not READY empty body)
- Status: CLOSED

## FINAL-P2-05 PRODUCT SKIP LINK TARGET

- Source: ENG-06, BLIND-06
- Commander severity: P2
- Repair files:
  - `frontend/src/components/shell/ProductShell.vue`
  - `frontend/src/components/shell/InterviewFocusShell.vue`
  - `frontend/src/App.vue`
- Tests:
  - `frontend/tests/ProductShell.spec.ts` (skip target is `#product-main` workspace, not sidebar wrapper)
- Status: CLOSED

## FINAL-P2-06 DASHBOARD READY + NULL SCORE

- Source: BLIND-07
- Commander severity: P2
- Repair files:
  - `frontend/src/features/dashboard/components/DashboardRecentList.vue`
  - `frontend/src/features/history/utils/historyScoreDisplay.ts` (shared helper, unchanged contract)
- Tests:
  - `frontend/tests/DashboardView.spec.ts`
  - `frontend/tests/HistoryView.spec.ts`
  - READY + null never says 未生成; both surfaces use 报告数据暂时异常
- Status: CLOSED

## FINAL-P2-07 SESSION QUESTION TYPOGRAPHY

- Source: UX-01
- Commander severity: P2
- Repair files:
  - `frontend/src/features/interview/components/InterviewQuestionPane.vue`
- Tests: visual regression (fixture screenshots); unit coverage remains session question pane render
- Status: CLOSED

## FINAL-P2-08 EVALUATION_PENDING SEMANTIC TONE

- Source: UX-02
- Commander severity: P2
- Repair files:
  - `frontend/src/features/interview/views/InterviewSessionView.vue`
- Tests:
  - `frontend/tests/InterviewSessionView.spec.ts`
    - uses warning status semantics for EVALUATION_PENDING
    - uses danger alert semantics for RECOVERY_BLOCKED
- Status: CLOSED

## FINAL-P2-09 SMALL TEXT CONTRAST

- Source: UX-03
- Commander severity: P2
- Repair files:
  - `frontend/src/features/interview/components/InterviewEvaluationPane.vue`
  - `frontend/src/app/styles/shells.css`
- Tests: visual regression; `--text-3` token not globally rewritten
- Status: CLOSED

## FINAL-P2-10 MOBILE FIVE-DIMENSION FEEDBACK

- Source: BLIND-08, UX-07
- Commander severity: P2
- Repair files:
  - `frontend/src/features/interview/components/InterviewEvaluationPane.vue`
- Tests: visual regression at 390 on Session / Detail / Report shared pane; unit session/detail/report still mount the pane
- Status: CLOSED

## FINAL-P2-11 P1 REGRESSION TEST COVERAGE

- Source: BLIND-09
- Commander severity: P2 (bookkeeping)
- Repair files: none (production)
- Tests: absorbed into FINAL-P1-01 / P1-02 / P1-03
- Status: CLOSED
- Note: `BLIND-09 = MERGED_INTO_P1_REGRESSION_COVERAGE`

## FINAL-P3-01 UNUSED ARCHITECTURE PLACEHOLDER

- Source: ENG-07
- Commander severity: P3
- Repair files:
  - deleted `frontend/src/app/views/ArchitecturePlaceholderView.vue`
- Tests: confirmed zero remaining imports
- Status: CLOSED

## FINAL-P3-02 LOGIN 401 COPY

- Source: ENG-08
- Commander severity: P3
- Repair files:
  - `frontend/src/features/auth/views/LoginView.vue`
- Tests:
  - `frontend/tests/LoginView.spec.ts` (login 200 + /me 401 → 登录状态无效，请重新登录; not merged with 账号或密码错误)
- Status: CLOSED

## FINAL-P3-03 PAGE H1

- Source: UX-04
- Commander severity: P3
- Repair files:
  - `frontend/src/components/shell/InterviewFocusShell.vue`
  - `frontend/src/app/views/NotFoundView.vue`
- Tests: visual/a11y inspection; NotFound uses EmptyState `heading-tag="h1"`
- Status: CLOSED

## FINAL-P3-04 DOCUMENT TITLE

- Source: UX-05
- Commander severity: P3
- Repair files:
  - `frontend/src/app/router/documentTitle.ts`
  - `frontend/src/app/router/guards.ts`
  - `frontend/src/app/router/routes.ts`
- Tests:
  - `frontend/tests/routerGuards.spec.ts`
- Status: CLOSED

## FINAL-P3-05 DASHBOARD RECENT ACTION TAP TARGET

- Source: UX-06
- Commander severity: P3
- Repair files:
  - `frontend/src/features/dashboard/components/DashboardRecentList.vue`
- Tests: visual 390 dashboard; History action already met 44px
- Status: CLOSED

## FINAL-P3-06 CAN-SUBMIT PREVALIDATION

- Source: BLIND-10
- Commander severity: P3
- Repair files:
  - `frontend/src/features/interview/composables/useInterviewSession.ts`
  - `frontend/src/features/interview/utils/answerValidation.ts` (reused, not duplicated)
- Tests:
  - `frontend/tests/InterviewSessionView.spec.ts` (blank/oversize does not persist or POST)
  - `frontend/tests/answerValidation.spec.ts`
- Status: CLOSED

---

## Reviewer ID mapping

| Reviewer ID | Commander |
| --- | --- |
| ENG-01 | FINAL-P2-01 |
| ENG-02 | FINAL-P1-03 |
| ENG-03 | FINAL-P2-02 |
| ENG-04 | FINAL-P1-01 |
| ENG-05 | FINAL-P2-02 (MERGED) |
| ENG-06 | FINAL-P2-05 |
| ENG-07 | FINAL-P3-01 |
| ENG-08 | FINAL-P3-02 |
| UX-01 | FINAL-P2-07 |
| UX-02 | FINAL-P2-08 |
| UX-03 | FINAL-P2-09 |
| UX-04 | FINAL-P3-03 |
| UX-05 | FINAL-P3-04 |
| UX-06 | FINAL-P3-05 |
| UX-07 | FINAL-P2-10 (MERGED with BLIND-08) |
| BLIND-01 | FINAL-P1-01 |
| BLIND-02 | FINAL-P1-02 |
| BLIND-03 | FINAL-P1-03 |
| BLIND-04 | FINAL-P2-03 |
| BLIND-05 | FINAL-P2-04 |
| BLIND-06 | FINAL-P2-05 |
| BLIND-07 | FINAL-P2-06 |
| BLIND-08 | FINAL-P2-10 |
| BLIND-09 | MERGED_INTO_P1_REGRESSION_COVERAGE (FINAL-P2-11) |
| BLIND-10 | FINAL-P3-06 |

Accepted safe areas from independence reviews were not reopened and are not additional FINAL IDs.
