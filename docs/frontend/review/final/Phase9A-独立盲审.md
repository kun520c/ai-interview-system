# Phase 9A Review C — Independent Blind Review

| Field | Value |
|---|---|
| Date | 2026-09-21 |
| Reviewer | Independent blind reviewer (read-only) |
| Scope | Vue 3 + TypeScript frontend (`frontend/src/**`) after feature-complete implementation |
| Backend truth | Frozen SHA `20a933ae8092ef908c2f03193c0b0f9547da71c1` (checkout-free `git show`) |
| Stance | Implementation-first. Existing tests were used only to locate coverage gaps. Passing tests are not treated as proof of correctness. |
| Changes | None. No production, backend, test, config, seed, credential, or Git writes except this file. |

---

## Sources inspected

Product / IA / contract (read in full):

- `docs/frontend/PRODUCT_UX_SPEC.md`
- `docs/frontend/FRONTEND_PAGE_MAP.md`
- `docs/frontend/ROUTE_MAP.md`
- `docs/frontend/STATE_MODEL.md`
- `docs/frontend/FRONTEND_API_MATRIX.md`
- `docs/frontend/DESIGN_SYSTEM.md`
- `docs/frontend/FRONTEND_ARCHITECTURE.md`

Frontend implementation (source of truth): `frontend/src/**` including HTTP/auth/router, interview session/submit/gate, history/detail/report, profile, admin questions/knowledge, shells, types, and storage.

Frontend tests (coverage gaps only): `frontend/tests/**`, especially `InterviewSessionView.spec.ts`, `sessionBootstrap.spec.ts`, `sessionScope.spec.ts`, `evaluationGateStorage.spec.ts`, `submitRecordStorage.spec.ts`, `currentInterview.spec.ts`.

Tooling: `frontend/package.json`, `frontend/vite.config.ts`, `frontend/eslint.config.js`.

Frozen backend VOs/DTOs at SHA `20a933ae8092ef908c2f03193c0b0f9547da71c1` (and matching workspace copies, which were compared, not modified): interview session/answer/evaluation/report/history/detail VOs, `SubmitInterviewAnswerRequest`, `LoginResponse`, `CurrentUserResponse`, `CreateQuestionRequest`, `AdminQuestionDetailResponse`, `UploadKnowledgeDocumentResponse`, `KnowledgeCategory`, `EvaluationStandard`.

Screenshots under `screenshots-8b1`–`screenshots-8c` were not required to establish the code findings below.

Not read: any file under `docs/frontend/review/` except this output; `Phase9A-前端工程审查.md`; `Phase9A-UX与可访问性审查.md`; other reviewers’ notes.

---

## Method

1. Read product contracts, then traced `frontend/src` page-by-page and flow-by-flow (auth, create/resume, session submit/recovery/gate, history/detail/report, profile, admin).
2. Compared TypeScript DTO/enum shapes to frozen backend records/classes.
3. Inspected async continuations after `await` for identity generation, unmount, and overlapping mutations.
4. Inspected `localStorage` / `sessionStorage` key ownership and 401/403/logout behavior.
5. Used tests only to see what is *not* covered. Did not infer correctness from green tests.
6. Ran typecheck, lint, unit tests, and production build without changing source.

Environment blockers (`EMPTY_QUESTION_BANK`, `DEEPSEEK_UNAVAILABLE`, `NO_AUTHORIZED_ADMIN_TEST_CREDENTIAL`) were treated as environment facts. They did not license backend, seed, credential, or DeepSeek changes, and they were not used as excuses for missing frontend contracts.

---

## Findings

### BLIND-01

**Severity:** P1

**File/surface:** Interview Session — overlapping “检查当前面试状态” and “按原答案重新获取评价”; evaluation gate binding

**Evidence:**

`EVALUATION_PENDING` enables **both** retry POST and GET-current at the same time. Retry is gated only on `submitLocked`; check is gated only on `checkingCurrent`:

```36:42:frontend/src/features/interview/components/InterviewSessionDock.vue
    <template v-else-if="phase === SessionPhase.EVALUATION_PENDING">
      <BaseButton :disabled="submitLocked" :loading="submitLocked" @click="emit('retry')">
        {{ SESSION_COPY.pendingReplay }}
      </BaseButton>
      <BaseButton variant="ghost" :disabled="checkingCurrent" @click="emit('check')">
        {{ SESSION_COPY.checkCurrent }}
      </BaseButton>
    </template>
```

`retrySamePair` does not refuse while `checkingCurrent` is true. `checkCurrentStatus` does not refuse while a retry POST is in flight (it only self-gates):

```538:570:frontend/src/features/interview/composables/useInterviewSession.ts
  async function retrySamePair(): Promise<void> {
    if (submitLocked.value) {
      return
    }
    const record = activeRecord.value
    if (!record?.dispatchedAt) {
      return
    }
    if (phase.value !== SessionPhase.EVALUATION_PENDING) {
      return
    }
    submitLocked.value = true
    // ...
    await postAnswer(record)
  }

  async function checkCurrentStatus(): Promise<void> {
    if (checkingCurrent.value) {
      return
    }
    checkingCurrent.value = true
    try {
      await realignAfterServerChange()
    } finally {
      checkingCurrent.value = false
    }
  }
```

On 200, the gate’s **answered question** is taken from live `workspaceQuestion`, not from the submit record / the question identity captured at persist time:

```62:67:frontend/src/features/interview/composables/useInterviewSession.ts
  const workspaceQuestion = computed<InterviewQuestionResponse | null>(() => {
    if (gate.value) {
      return gate.value.answeredQuestion
    }
    return current.session.value?.currentQuestion ?? null
  })
```

```305:324:frontend/src/features/interview/composables/useInterviewSession.ts
  function openEvaluationGate(record: SubmitRecord, response: SubmitInterviewAnswerResponse): void {
    const answeredQuestion = workspaceQuestion.value
    if (!answeredQuestion) {
      phase.value = SessionPhase.ERROR
      alertMessage.value = SESSION_COPY.stateChanged
      return
    }
    const nextGate: EvaluationGate = {
      userId: record.userId,
      sessionId: record.sessionId,
      // ...
      answeredQuestion,
      answerContent: record.answerContent,
      evaluation: response.evaluation,
      nextAction: response.nextAction,
      nextQuestion: response.nextQuestion,
      sessionStatus: response.sessionStatus,
    }
```

There is no check that `answeredQuestion.interviewQuestionId === record.interviewQuestionId`.

`frontend/tests/InterviewSessionView.spec.ts` covers sequential retry of the same pair and stale-gate-on-load. It never overlaps check + retry. Grep over `frontend/tests` finds no `"检查当前面试状态"` usage.

**Reproduction:**

1. Submit an answer. Cause an uncertain outcome so the page is `EVALUATION_PENDING` (client timeout / network / 502 / 409 `答案正在评估中，请稍后重试`). Snapshot is locked; both dock buttons are visible.
2. Click **检查当前面试状态** (GET `/api/interviews/current` in flight).
3. Immediately click **按原答案重新获取评价** (allowed: retry is not disabled by `checkingCurrent`).
4. Server has already accepted the original answer and advanced. GET returns the **next** question. `applyBootstrapResult` treats the old submitRecord as a different question and switches the workspace to the new question (empty draft).
5. The in-flight POST then returns 200 (EVALUATED replay). `openEvaluationGate` stamps that evaluation onto `workspaceQuestion`, which is now the **next** question.

**Impact:** The user reads strengths / missing points / scores as if they belonged to the follow-up or next MAIN. Continue uses the **old** `nextAction`. The Java backend mapping remains by `interviewQuestionId`; this is a frontend workspace integrity failure, not a silent DB rewrite. It is still a serious correctness/state defect on the highest-attention page.

**Recommended fix:**

- Capture `answeredQuestion` at persist/dispatch time (the question id on the `SubmitRecord`) and pass that object into `openEvaluationGate`. Ignore a 200 whose `record.interviewQuestionId` does not match the workspace question; do not attach the evaluation to a different question.
- Mutual-exclusion: disable retry while `checkingCurrent`, and ignore check while `submitLocked` / `SUBMITTING`.
- If GET current has already moved on, keep the existing stale-gate copy and drop a late 200 rather than opening a mismatched gate.

---

### BLIND-02

**Severity:** P1

**File/surface:** Interview Session bootstrap after identity change / epoch mismatch

**Evidence:** `useCurrentInterviewStore.refresh()` returns `null` (does not throw) when the in-flight GET is stale after `invalidate()`:

```51:56:frontend/src/features/interview/stores/currentInterview.ts
        const nextSession = await fetchCurrentInterview()
        if (requestEpoch !== epoch.value) {
          return null
        }
        session.value = nextSession
```

Logout / 401 / login call `notifySessionIdentityChanged` → `invalidate()` (epoch++). `useInterviewSession` does **not** capture `getSessionGeneration()` / `auth.userId` / a mounted flag around `await`. After `refresh()` returns `null`, it always runs `applyBootstrapResult`:

```255:263:frontend/src/features/interview/composables/useInterviewSession.ts
  async function bootstrap(): Promise<void> {
    phase.value = SessionPhase.BOOTSTRAP
    resumeNotice.value = consumeInterviewResumeNotice()
    // ...
      const session = await current.refresh()
      await applyBootstrapResult(session?.sessionId ?? null)
```

`applyBootstrapResult(null)` with no FINISH gate for the **current** `auth.userId` navigates globally:

```182:187:frontend/src/features/interview/composables/useInterviewSession.ts
    if (result.kind === 'redirect-dashboard') {
      if (storedGate && sessionIdForGate !== null) {
        clearEvaluationGate(storedGate.sessionId, userId)
      }
      await router.replace({ name: 'dashboard' })
      return
    }
```

401 on GET `/current` is especially sharp: the HTTP interceptor clears the session (epoch++) **before** `refresh()`’s catch. Because the epoch no longer matches, the catch returns `null` instead of throwing `unauthenticated`, so bootstrap does **not** take the early `UNAUTHENTICATED` return at lines 265–267.

Profile, admin question form/detail, and knowledge **do** use `isCurrentIdentityScope` after `await`. Session does not. `frontend/tests/sessionScope.spec.ts` tests the helper; it does not test session bootstrap against it.

**Reproduction:**

1. User A is on `/interview`. `GET /api/interviews/current` is slow.
2. A 401 arrives (expired token) **or** A logs out and B logs in with `?redirect=/interview`.
3. Identity change invalidates current-interview epoch.
4. A’s in-flight GET finishes. `refresh()` returns `null`.
5. A’s still-running `applyBootstrapResult(null)` runs against whatever `auth.userId` is now. If B has no FINISH gate, `router.replace({ name: 'dashboard' })` fires.

**Impact:** Account switch or 401-then-relogin onto `/interview` can be stolen by a stale bootstrap: B never sees the live question, or A’s login recovery lands on dashboard instead of the session. Router `replace` runs even if the originating component has unmounted.

**Recommended fix:** Capture `operationUserId` + `operationGeneration` (+ `mounted`) at bootstrap/submit/continue start. After every `await`, abort if `!isCurrentIdentityScope(...)`. Treat epoch-mismatch `null` from `refresh()` as “stale operation”, not as “no active interview”. Do not `router.replace` from an aborted operation.

---

### BLIND-03

**Severity:** P2

**File/surface:** Create / resume interview — `POST /api/interviews` completion

**Evidence:** After the create POST, the view writes the returned session into the app-lifetime store and navigates, with no identity/generation check:

```69:86:frontend/src/features/interview/views/CreateInterviewView.vue
async function startInterview(): Promise<void> {
  // ...
    const created = await createInterview({ difficulty: selectedDifficulty.value })
    if (created.difficulty !== selectedDifficulty.value) {
      armInterviewResumeNotice()
    }
    adoptCreatedSession(created)
    await router.push({
      name: 'interview-session',
      state: created.difficulty !== selectedDifficulty.value ? interviewResumeNoticeState() : {},
    })
```

`adoptCreatedSession` bumps epoch and **sets** `session` (question content included). Uncertain-POST recovery (`verifyAfterUncertainPost`) has the same gap.

**Reproduction:**

1. User A clicks **开始面试**. POST is in flight (plan snapshot can be slow).
2. A logs out; B logs in in the same tab (sidebar 退出登录 is not disabled by `submitting`).
3. A’s POST 200 runs `adoptCreatedSession(A’s session)` and `router.push('/interview')` while B is authenticated.

**Impact:** Bounded. Session bootstrap normally `GET /current` with B’s token and overwrites the store. If that GET fails, the session ERROR branch still renders `workspaceQuestion` from `current.session` (the adopted A session), so B can see A’s current question text. Navigation hijack onto `/interview` can also surprise B.

**Recommended fix:** Same identity-scope abort as BLIND-02. Do not `adoptCreatedSession` or `router.push` if generation/userId changed. On abort, do not leave another user’s session in the Pinia owner.

---

### BLIND-04

**Severity:** P2

**File/surface:** Resume notice — process-wide boolean, not user-scoped

**Evidence:**

```1:14:frontend/src/features/interview/navigation/sessionNavigationContext.ts
const RESUME_NOTICE_STATE_KEY = 'interviewResumeNotice'

let pendingResumeNotice = false

export function armInterviewResumeNotice(): void {
  pendingResumeNotice = true
}

export function consumeInterviewResumeNotice(historyState?: unknown): boolean {
  const fromPending = pendingResumeNotice
  pendingResumeNotice = false
  const fromHistory = consumeHistoryResumeNotice(historyState)
  return fromPending || fromHistory
}
```

Session bootstrap always consumes this flag (`useInterviewSession.ts` line 257). It is not keyed by `userId`. History `state` is a second channel and is also not user-owned.

**Reproduction:**

1. User A’s create/resume arms the notice (`difficulty` mismatch or uncertain-POST recovery).
2. Before `/interview` consumes it, identity switches to B and B opens `/interview`.
3. B sees `已为你继续未完成的面试`.

**Impact:** Cross-account copy pollution. Not a token/data leak. Combined with BLIND-03’s `router.push` state it can fire on a hijacked navigation.

**Recommended fix:** Store the notice on the gate/session object or as `pendingResumeNoticeUserId`, and consume only when `auth.userId` matches. Clear on `notifySessionIdentityChanged`.

---

### BLIND-05

**Severity:** P2

**File/surface:** Interview Detail — empty `questions[]` on a COMPLETED session

**Evidence:** PAGE_MAP: “Session with no questions is an internal inconsistency; show error, not a fake timeline.” Implementation marks READY whenever status is COMPLETED, with no length check:

```96:107:frontend/src/features/interview/composables/useInterviewDetail.ts
    if (isActiveInterviewStatus(response.status)) {
      pageState.value = DetailPageState.REDIRECTING
      await router.replace({ name: 'interview-session' })
      return
    }
    if (!isCompletedInterviewStatus(response.status)) {
      pageState.value = DetailPageState.UNAVAILABLE
      return
    }
    detail.value = response
    pageState.value = DetailPageState.READY
```

```30:30:frontend/src/features/interview/views/InterviewDetailView.vue
const showTimeline = computed(() => pageState.value === DetailPageState.READY)
```

`hasIncompleteItems` is false when `timeline.length === 0`, so the incomplete-notice path does not fire either.

Spoiler redirect for `CREATED` / `IN_PROGRESS` **is** implemented (detail is nulled before assign; timeline computed requires COMPLETED). That path is sound.

**Reproduction:** Open `/interviews/{sessionId}` for a COMPLETED session whose detail payload has `questions: []` (inconsistency / truncated payload).

**Impact:** Header + empty surface, no error. User cannot tell this is a broken record versus “no answers yet”.

**Recommended fix:** If COMPLETED and `questions` is missing or empty, set ERROR/UNAVAILABLE with the existing load-error retry, not READY.

---

### BLIND-06

**Severity:** P2

**File/surface:** Product shell keyboard skip link

**Evidence:**

```1:9:frontend/src/App.vue
<template>
  <a class="skip-link" href="#app-main">跳到正文</a>
  <div id="app-main">
    <RouterView />
  </div>
</template>
```

`#app-main` wraps the entire routed tree, including `ProductShell`’s sidebar (`frontend/src/components/shell/ProductShell.vue` aside + nav). The first focusable node after skip is still product navigation (brand / 工作台), not `.product-workspace` / `<main>`.

`InterviewFocusShell` has a second skip to `#interview-main`, which does skip the focus header. Product pages do not.

**Reproduction:** Log in. On `/` (or History / Profile / Admin), Tab to **跳到正文**, Activate. Tab again.

**Impact:** Skip-link contract fails on every product-shell page. Keyboard users re-traverse the left nav (or the 390 overlay trigger) on every navigation. Session is only partially repaired by the second skip.

**Recommended fix:** Put a stable `id` on `ProductShell`’s `<main class="product-workspace">` and point the skip link at that (or per-shell targets). Do not wrap the sidebar in the skip target.

---

### BLIND-07

**Severity:** P2

**File/surface:** Dashboard recent list — READY report with null `totalScore`

**Evidence:** History page uses honest copy for READY + null score:

```5:19:frontend/src/features/history/utils/historyScoreDisplay.ts
  if (item.totalScore !== null && item.totalScore !== undefined) {
    return formatScore(item.totalScore)
  }
  // ...
  return '报告数据暂时异常'
```

Dashboard recent list does not:

```34:39:frontend/src/features/dashboard/components/DashboardRecentList.vue
function scoreText(item: InterviewHistoryItemResponse): string {
  if (item.totalScore !== null && item.totalScore !== undefined) {
    return formatScore(item.totalScore)
  }
  return reportScorePlaceholder(item.reportStatus)
}
```

```30:35:frontend/src/features/interview/copy/reportStatusDisplay.ts
export function reportScorePlaceholder(status: InterviewReportStatus): string {
  if (status === InterviewReportStatus.READY) {
    return '报告未生成'
  }
  return REPORT_SCORE_PLACEHOLDER[status]
}
```

PRODUCT_UX_SPEC / PAGE_MAP: show “报告未生成” for **null until READY**, never invent `0`. READY with null score is abnormal, not “未生成”.

**Reproduction:** Dashboard loads `GET /api/interviews/history?page=1&pageSize=5`. An item has `reportStatus=READY` and `totalScore=null`.

**Impact:** Dashboard lies that the report was never generated. History of the same item says 报告数据暂时异常. Bounded to an inconsistent payload, but it is a real honesty/copy contract miss on the workbench.

**Recommended fix:** Share `historyScoreText` (or the same READY-null branch) on the dashboard recent rows.

---

### BLIND-08

**Severity:** P2

**File/surface:** Accessibility / 390px — evaluation five-dimension row

**Evidence:** DESIGN_SYSTEM §9 / §16: 390 is single column; no tiny text. Evaluation pane keeps five columns and drops labels to 11px:

```158:165:frontend/src/features/interview/components/InterviewEvaluationPane.vue
.score-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
```

```225:237:frontend/src/features/interview/components/InterviewEvaluationPane.vue
@media (max-width: 720px) {
  .score-row {
    gap: 4px;
    padding: 10px 8px;
  }

  .score-label {
    font-size: 11px;
  }
```

Session dock on 390 is a sibling (not `position: fixed` overlay) — that part matches the spec. This finding is only the metric row.

**Reproduction:** Open an evaluation-gate (or detail timeline evaluation) at ~390px width.

**Impact:** Five cramped columns with 11px labels. Color still has a text companion (level word + dimension names), so this is not color-only status. It is a bounded readability/a11y miss on the feedback the product exists to deliver.

**Recommended fix:** Below 720px, stack the five metrics as rows (label + number) at 13–14px, or 2+3 wrap, not five 11px columns.

---

### BLIND-09

**Severity:** P2

**File/surface:** Tests — missing coverage for the P1 session races

**Evidence:** `frontend/tests/InterviewSessionView.spec.ts` (266 tests in the full unit run include this file) asserts persist-before-dispatch, same-pair retry, evaluation-not-auto-advance, FINISH-gate user isolation, and RECOVERY_BLOCKED reload. It does **not**:

- Overlap `checkCurrentStatus` with `retrySamePair` (BLIND-01).
- Run session `bootstrap()` across `notifySessionIdentityChanged` / epoch-mismatch `refresh() → null` (BLIND-02).
- Complete `createInterview` after userId/generation change (BLIND-03).

`useInterviewSession.ts` has no dedicated spec; session behavior is only reached through the view mount helper.

**Reproduction:** Read `frontend/tests/InterviewSessionView.spec.ts` and `frontend/tests/sessionScope.spec.ts`. No test clicks 检查当前面试状态 concurrently with replay; no test stubs a stale `refresh()` null after login-as-other-user.

**Impact:** The two P1 defects can stay green in CI. This is a verification gap, not a second functional bug.

**Recommended fix:** Add those three races as unit tests with `deferred()` GET/POST (the suite already uses `tests/helpers/deferred.ts`).

---

### BLIND-10

**Severity:** P3

**File/surface:** Session `canSubmit` vs STATE_MODEL §4.7

**Evidence:**

```89:97:frontend/src/features/interview/composables/useInterviewSession.ts
  const canSubmit = computed(() => {
    return (
      phase.value === SessionPhase.ANSWERING &&
      !submitLocked.value &&
      !answerLocked.value &&
      Boolean(current.session.value?.currentQuestion) &&
      draft.value.trim().length > 0
    )
  })
```

STATE_MODEL `canSubmit` also requires UTF-8 bytes ≤ 65535 and `IN_PROGRESS`. `validateAnswerContent` still blocks POST for blank/oversize (`frontend/src/features/interview/utils/answerValidation.ts`), so this is not a dispatch hole.

**Reproduction:** Paste an answer larger than 65535 UTF-8 bytes. Submit stays enabled until click; then inline `答案超出允许长度`.

**Impact:** Extra click and error. No oversize POST if validation runs.

**Recommended fix:** Include the UTF-8 check (and optionally `status === IN_PROGRESS`) in `canSubmit` so the primary button disables before click.

---

## TOP RISKS REVIEWED BUT ACCEPTED

These were inspected in code and judged safe, correctly bounded, or an accepted MVP limitation.

1. **401 vs 403.** `httpClient` notifies unauthorized only for `kind === 'unauthenticated'` and skips `/auth/login` and `/auth/register`. 403 does not clear the token. Admin features `router.replace({ name: 'forbidden' })`. Guard sends **resolved** USER on `/admin/**` to `/forbidden` and does **not** treat `unknown` / `unavailable` identity as USER. Login 400 stays inline (`LoginView.vue`); it is not mapped to 401.

2. **401 interceptor vs account switch.** Requests stamp `identityUserId` + `identityGeneration`. `notifyUnauthorized` runs only if that scope still matches `readAuthCredential().userId` and current generation (`httpClient.ts` 86–94, `sessionScope.ts` 15–25). A stale 401 after logout/login as another user does not wipe the new session.

3. **Durable submitRecord.** Key `interview.submit.{userId}.{sessionId}.{interviewQuestionId}` in `localStorage`. Persist + `dispatchedAt` happen before POST. Persist failure throws `SubmitRecordPersistError` and does **not** POST. Logout/`clearAuthCredential` does not delete submit keys. Axios does not mint `requestId` and has no mutation retry.

4. **EvaluationGate cross-user.** Object carries `userId`. Reads require `expectedUserId` and reject legacy/corrupt gates (`evaluationGateStorage.ts`). Latest pointer is `interview.evaluationGate.latestSessionId.{userId}`. Wrong-user FINISH restore is implemented and covered by a session view test; that path is accepted as correct.

5. **currentInterview owner.** Single Pinia store; Dashboard/Create consume it. Same-epoch `refresh()` is single-flight. `invalidate()` / `adoptCreatedSession()` bump epoch so a slower GET cannot overwrite. Auth does not import the interview feature; `sessionScope` is the bridge.

6. **Spoiler / report completeness.** Detail and Report load `GET /api/interviews/{sessionId}` first. `CREATED` / `IN_PROGRESS` replace to `/interview` **before** assigning `detail` / rendering questions. Report does not POST generate until COMPLETED. Uncertain report POST uses GET-first (`useInterviewReport.ts`). FINISH CTA navigates to the report route and does not POST generate.

7. **Idempotent answer retry.** 409 evaluating / 502 / network after dispatch reuse the same `requestId` + exact `answerContent`. RECOVERY_BLOCKED persists `recoveryBlockedReason` and does not mint a new id. Lost-record limitation (no AnswerStatus on GET `/current`) is honored.

8. **DTO vs frozen SHA.** Interview/auth/user/admin/knowledge TypeScript shapes match frozen VOs (including `clarityScore`, list vs detail `questionContent`/`content`, nullable history `totalScore`, report null fields when not READY). `KnowledgeCategory` and `QuestionCategory` share the same enum names; sending `QuestionCategory` in multipart `category` is wire-compatible. `evaluationLevelFromScore` on the dashboard uses the same 90/80/60 breakpoints as frozen `EvaluationStandard` and is display-only (history has no `result` field).

9. **Open redirect.** `resolveSafeRedirect` rejects `//`, schemes, backslashes, and login/register loops.

10. **JWT role.** Role is not decoded from the token. `isAdmin` requires `identityStatus === resolved` and `me.role === ADMIN`.

11. **No forbidden product pages.** No forgot-password link, no cancel interview, no weakness route, no admin user/interview monitor, no knowledge catalog API fiction (local “本机最近上传” only). `CANCELLED` / `SKIPPED` are not controls.

12. **`expiresAt` unused.** Stored but never used to preemptively logout. Spec: no refresh token; 401 is the expiry signal. Accepted.

13. **Cross-tab `localStorage` races** on the same submit key. Two tabs can interleave persist. Backend unique `request_id` / `interview_question_id` plus client lock are the safety net. Not a single-tab logic bug.

14. **409 evaluating has a manual check, not an auto poll.** STATE_MODEL allows poll of current **question identity**. The dock button is an explicit poll. Accepted as meeting the contract without a timer.

15. **HTTP timeout 120s** (`httpClient.ts`). A longer DeepSeek evaluation becomes network/pending and retries the same pair. Recovery is specified; not a new identity mint.

16. **`writeEvaluationGate` returning false** is ignored. In-memory gate still shows until refresh. Refresh then depends on sessionStorage. Bounded; not P1.

17. **Register `confirmPassword`** is local form state only and is omitted from `RegisterRequest`.

18. **Environment E2E blockers** (empty question bank, DeepSeek down, no admin test user) were not treated as frontend defects and were not used to change backend/DB/credentials.

---

## Missing-test notes

See **BLIND-09**. Additional gaps that are *not* raised as separate defects because the implementation was inspected and accepted:

- No component test that 403 on `/api/admin/**` keeps the token (logic is in interceptors + admin composables; `routerGuards.spec.ts` covers the resolved-USER client guard).
- No test that Dashboard region failure on current vs history is independent beyond `DashboardView.spec.ts` (implementation splits the two requests).
- No test for BLIND-05 empty COMPLETED timeline.

---

## Commands run

Working directory: `frontend/`. Source was not modified to make commands pass.

| Command | Result |
|---|---|
| `npm run typecheck` (`vue-tsc -b --pretty false`) | Exit 0 |
| `npm run lint` (`eslint .`) | Exit 0 |
| `npm run test:unit` (`vitest run`) | Exit 0 — **36** files, **266** tests passed, 0 failed. Passing tests were **not** used as proof of BLIND-01/02. |
| `npm run build` (`vue-tsc -b && vite build`) | Exit 0 — Vite 8.3.0 production build |

No real E2E / live backend run. Frozen-API comparison used `git show 20a933ae8092ef908c2f03193c0b0f9547da71c1:<path>`.

---

## Count summary

| Severity | Count | IDs |
|---|---|---|
| P0 | 0 | — |
| P1 | 2 | BLIND-01, BLIND-02 |
| P2 | 7 | BLIND-03, BLIND-04, BLIND-05, BLIND-06, BLIND-07, BLIND-08, BLIND-09 |
| P3 | 1 | BLIND-10 |
| **Total** | **10** | |

No P0 (no demonstrated cross-account token theft, no 403-as-logout, no submitRecord wipe on 401, no auto-advance of `nextQuestion`, no Axios mutation retry / `requestId` mint).
