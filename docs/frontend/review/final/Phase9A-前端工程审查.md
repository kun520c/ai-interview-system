# FINAL ENGINEERING REVIEW

Status: READ-ONLY  
Phase: 9A Review A  
Date: 2026-09-21  
Reviewer: Codex + GPT-5.6  
Independence: This review did not consume Review B or Review C reports. Findings are from current `frontend/src/**`, `frontend/tests/**`, frozen frontend docs, and frozen backend SHA `20a933ae8092ef908c2f03193c0b0f9547da71c1`.

```text
BACKEND_FREEZE = 20a933ae8092ef908c2f03193c0b0f9547da71c1
FRONTEND        = untracked working tree (no frontend freeze SHA)
SCOPE           = engineering correctness / state / auth / races
PRODUCTION EDITS = NONE
```

---

## 1. Method

Sources read to EOF:

- `agents/skills/ai-interview-frontend/SKILL.md`
- `agents/skills/ai-interview-finalization/SKILL.md`
- `docs/frontend/PRODUCT_UX_SPEC.md`
- `docs/frontend/FRONTEND_PAGE_MAP.md`
- `docs/frontend/ROUTE_MAP.md`
- `docs/frontend/STATE_MODEL.md`
- `docs/frontend/FRONTEND_API_MATRIX.md`
- `docs/frontend/DESIGN_SYSTEM.md`
- `docs/frontend/FRONTEND_ARCHITECTURE.md`

Implementation inspected (not inferred from old review reports):

- `frontend/src/**` composables, stores, router, HTTP, storage, views, types
- `frontend/tests/**` as coverage evidence only (passing tests ≠ proof)
- `frontend/package.json`, `frontend/vite.config.ts`, `frontend/eslint.config.js`
- Frozen backend VOs at `20a933ae8092ef908c2f03193c0b0f9547da71c1` for DTO truth

Commander carry-in **P9-CARRY-01** was inspected first, then analogous after-await reactive reads.

Validation run (no source changes):

| Command | Result |
|---|---|
| `npm run typecheck` | PASS |
| `npm run lint` | PASS |
| `npm run test:unit` | 36 files / 266 tests / 0 failed |
| `npm run build` | PASS (`vue-tsc -b && vite build`, 2138 modules, ~595ms) |

---

## 2. Count summary

| Severity | Count | IDs |
|---|---|---|
| P0 | 0 | — |
| P1 | 3 | ENG-02, ENG-03, ENG-04 |
| P2 | 3 | ENG-01, ENG-05, ENG-06 |
| P3 | 2 | ENG-07, ENG-08 |

---

## 3. Findings

### ENG-01

Severity: **P2**  
ID origin: Commander carry-in **P9-CARRY-01**  
File: `frontend/src/features/admin/knowledge/composables/useAdminKnowledge.ts`  
Lines: 167–184, 111–118, 334–354; category control: `AdminKnowledgeView.vue` 117–121

Concrete code evidence:

HTTP dispatch reads `category.value` when building the request. After `await`, the local catalog row reads `category.value` again instead of a captured dispatch value. Title/file/source are captured (`trimmedTitle`, `currentFile`, `trimmedSource`); category is not. Persist runs **before** the identity-scope guard.

```167:187:frontend/src/features/admin/knowledge/composables/useAdminKnowledge.ts
      const response = await uploadKnowledgeDocument({
        file: currentFile,
        title: trimmedTitle,
        category: category.value,
        ...(trimmedSource ? { source: trimmedSource } : {}),
      })
      const document: LocalKnowledgeDocument = {
        documentId: response.documentId,
        title: trimmedTitle,
        category: category.value,
        fileName: response.fileName,
        processingStatus: response.processingStatus,
        updatedAt: new Date().toISOString(),
        fileType: response.fileType,
        documentVersion: response.documentVersion,
        recoveryState: null,
      }
      const persistResult = persistCaptured(operationUserId, document)
      if (!currentIdentityMatches(operationUserId, operationGeneration)) {
        return
      }
```

Identity/session change re-enables the category control while the original POST is still in flight: `resetIdentityScopedUi()` sets `uploading = false` and does **not** reset `category`. `canUpload` becomes true, so `#knowledge-category` is no longer disabled.

Real trigger sequence:

1. Admin A selects category `SPRING`, starts upload (slow).
2. Session generation changes (logout / 401 / account switch). `subscribeSessionScope` / `watch(auth.userId)` calls `resetIdentityScopedUi()`.
3. If the view is still mounted for any interval, category is editable again. A later user (or the same instance) can set `REDIS`.
4. Upload 200 returns. `persistCaptured(operationUserId=A, document)` writes A's `admin.knowledge.recent.{A}` with `category: REDIS`.
5. UI for the new identity is correctly skipped (`currentIdentityMatches` false). A's durable local row is already wrong.

Same-user mutation during upload is blocked by `:disabled="!canUpload"` until identity/session reset clears `uploading`.

User/product impact:

- Server document category is the dispatch value (correct).
- The only knowledge catalog the MVP has is the browser recent list. It can display the wrong category for a real `documentId`.
- Process still uses `documentId` only; this is local metadata corruption, not a second upload.

Frozen contract violated:

- STATE_MODEL §6: local recent list `{ documentId, title, category, ... }` must reflect the uploaded document.
- FRONTEND_ARCHITECTURE identity-scoped async: values used after `await` must be captured at dispatch when identity/generation can change.
- FRONTEND_API_MATRIX G1: category is part of the upload contract the local handle must remember.

Recommended repair:

1. `const uploadedCategory = category.value` (with title/file) before `await`.
2. Build `LocalKnowledgeDocument.category` from `uploadedCategory` only.
3. Optionally skip `persistCaptured` when identity no longer matches if product decides the original user should not receive a post-logout write; if persist-to-origin-user remains, it must still use the captured category.
4. Add a test: change `category` after identity/generation bump, resolve upload, assert stored category equals dispatch category. Existing `does not persist a pending upload into the next user recent list` does not assert category.

---

### ENG-02

Severity: **P1**  
File: `frontend/src/features/interview/views/CreateInterviewView.vue`  
Lines: 69–86, 108–124

Concrete code evidence:

`startInterview()` does not capture session generation / `userId`. After `await createInterview(...)` it unconditionally `adoptCreatedSession(created)` into the app-lifetime Pinia owner. `adoptCreatedSession` increments `epoch` and drops in-flight `GET /current`.

```69:86:frontend/src/features/interview/views/CreateInterviewView.vue
async function startInterview(): Promise<void> {
  if (!selectedDifficulty.value || submitting.value || verifying.value || session.value) {
    return
  }
  submitting.value = true
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

`verifyAfterUncertainPost()` likewise has no identity guard: it `refresh()` then `router.push({ name: 'interview-session' })`.

Real trigger sequence:

1. User A clicks 开始面试. POST `/api/interviews` is slow (plan snapshot).
2. User A logs out (ProductShell remains available on Create). Identity notify invalidates `currentInterview` and bumps epoch.
3. User B logs in. Dashboard `onMounted` `refresh()` starts `GET /current` for B.
4. A's POST 200 returns. `adoptCreatedSession(A's session)` bumps epoch again and writes A's session into the shared store.
5. B's in-flight GET is ignored (`requestEpoch !== epoch`). Dashboard still mounted shows **继续当前面试** with A's `knowledgePoint` / category until B remounts or manually retries.

`/interview` bootstrap later `refresh()`es and can self-heal from the server. The dashboard/create Pinia surface does not.

User/product impact:

- Cross-account interview metadata (question category / knowledge point / progress) displayed to B on a shared browser.
- B cannot submit as A (Bearer is B), so this is client-store leakage, not server IDOR.
- Uncertain-create recovery can `router.push('/interview')` on a later identity.

Frozen contract violated:

- FRONTEND_ARCHITECTURE §7: `currentInterview` is user-scoped; identity change must invalidate; stale in-flight results must not repopulate the store. `adoptCreatedSession` is the POST counterpart of a stale GET.
- STATE_MODEL §3: one owner for current session; must not keep another user's session as live workspace truth.
- PRODUCT_UX_SPEC §4: 401/logout must not leave another user's protected interview facts on screen.

Recommended repair:

1. Capture `operationUserId` + `operationGeneration` before POST.
2. After await, apply `adoptCreatedSession` / resume navigation only if `isCurrentIdentityScope(...)`.
3. Same guard on `verifyAfterUncertainPost()`.
4. Test: pending create POST, switch user, resolve A's POST, assert B's store is not A's session and B's in-flight GET is not killed into A's payload.

Note: re-reading `selectedDifficulty.value` after await for resume-notice comparison is **not** the same class of bug while `selectDifficulty` is blocked by `submitting` (see FALSE POSITIVES). The defect is the unguarded Pinia adopt / navigation.

---

### ENG-03

Severity: **P1**  
File: `frontend/src/features/interview/composables/useInterviewDetail.ts`  
Lines: 60–107 (no `onUnmounted` epoch bump)

Concrete code evidence:

`load()` increments a local `epoch` and, for `CREATED` / `IN_PROGRESS`, `router.replace({ name: 'interview-session' })` after await. Leaving the route does not bump `epoch`. Contrast: `useInterviewReport` increments `epoch` on unmount.

```60:99:frontend/src/features/interview/composables/useInterviewDetail.ts
  async function load(): Promise<void> {
    const current = ++epoch
    const id = sessionId.value
    detail.value = null
    // ...
      response = await fetchInterviewDetail(id)
    // ...
    if (current !== epoch) {
      return
    }
    if (isActiveInterviewStatus(response.status)) {
      pageState.value = DetailPageState.REDIRECTING
      await router.replace({ name: 'interview-session' })
      return
    }
```

Spoiler rendering itself is safe: `detail` is assigned only after `COMPLETED`, and IN_PROGRESS uses a skeleton (`InterviewDetailView.vue` 45–50, 105–106). The defect is **navigation hijack**, not pending-MAIN flash.

Real trigger sequence:

1. Authenticated owner opens `/interviews/{activeSessionId}` (direct URL / stale history).
2. `GET /api/interviews/{id}` is in flight.
3. User navigates to `/interviews` or `/`.
4. GET returns `IN_PROGRESS`. `current === epoch` still holds.
5. `router.replace({ name: 'interview-session' })` overrides the user's new route.

User/product impact:

- Core workspace route is forced after the user already left Detail.
- Same pattern is weaker but present on history/admin query canonicalize (ENG-05).

Frozen contract violated:

- ROUTE_MAP: IN_PROGRESS Detail → `/interview` is required **on that route**, not after the route is gone.
- FRONTEND_ARCHITECTURE: route-scoped loaders must drop in-flight results on unmount (report already does).

Recommended repair:

1. `onUnmounted(() => { epoch += 1 })` (and `mounted` if also guarding `router.replace`).
2. Test: start detail load for IN_PROGRESS, navigate to history before resolve, resolve, assert current route is history not `/interview`.

---

### ENG-04

Severity: **P1**  
File: `frontend/src/features/interview/composables/useInterviewSession.ts`  
Lines: 296–323, 451–525

Concrete code evidence:

Answer POST correctly uses the captured `SubmitRecord` (`sessionId`, `interviewQuestionId`, `answerContent`, `requestId`). After the long evaluation `await`, `openEvaluationGate` reads **reactive** `workspaceQuestion`, which is Pinia `current.session.currentQuestion` when `gate` is still null.

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
      createdAt: new Date().toISOString(),
      answeredQuestion,
      answerContent: record.answerContent,
      evaluation: response.evaluation,
      nextAction: response.nextAction,
      nextQuestion: response.nextQuestion,
      sessionStatus: response.sessionStatus,
    }
    gate.value = nextGate
    writeEvaluationGate(record.sessionId, nextGate)
```

`useInterviewSession` does not capture `getSessionGeneration()` around `postAnswer`. Pinia `currentInterview` is app-global. An unmounted A's callback still reads B's `current.session`.

Real trigger sequence:

1. User A submits an answer. Evaluation HTTP is long (`httpClient` timeout 120s).
2. A logs out / 401. Store invalidates. Login as B. B opens `/interview`. Bootstrap loads **B's** current question into Pinia.
3. A's original promise resolves 200. Old closure runs `openEvaluationGate`.
4. `workspaceQuestion` is B's current question. Gate object `userId` is still A's. Key is `interview.evaluationGate.{A.sessionId}`.
5. `sessionStorage` now stores B's question body under A's session gate. Latest pointer is `interview.evaluationGate.latestSessionId.{A}`.

User/product impact:

- Cross-account question text in A's sessionStorage (shared browser).
- If A later restores a FINISH/same-question gate, the “answered question” pane can show B's content with A's evaluation.
- Identity-null path (`workspaceQuestion === null`) drops a successful 200 gate write entirely.

Frozen contract violated:

- STATE_MODEL §4.6: EvaluationGate.answeredQuestion is the question that was answered, not whoever now occupies `GET /current`.
- PRODUCT_UX_SPEC spoiler rule: another user's live question must not enter this client's gate storage.
- FRONTEND_ARCHITECTURE §13: gate reads are user-scoped; writes must not mix another user's Pinia session into the payload.

Recommended repair:

1. Capture `answeredQuestion` (the `question` already resolved in `submitAnswer` before persist) and pass it through `postAnswer` → `openEvaluationGate`.
2. After await, write the gate only if `isCurrentIdentityScope(record.userId, operationGeneration, auth.userId)` **or** write using only captured fields and skip mutating a later user's UI refs.
3. Test: pending submit, switch user onto `/interview`, resolve A's 200, assert B's question text is not in `interview.evaluationGate.{A.sessionId}`.

---

### ENG-05

Severity: **P2**  
Files:

- `frontend/src/features/history/composables/useInterviewHistory.ts` 23–32, 87–104
- `frontend/src/features/admin/questions/composables/useAdminQuestionList.ts` 27–36, 64–66, 139–156

Concrete code evidence:

Both loaders `router.replace` after await (query canonicalize; admin 403 → forbidden). `mounted` is set true on mount and never set false. No unmount epoch bump (admin list `403` replace is therefore still live after leave).

```23:32:frontend/src/features/history/composables/useInterviewHistory.ts
  async function canonicalizeQuery(): Promise<boolean> {
    const parsed = parseHistoryQuery(route.query)
    if (parsed.canonical) {
      return true
    }
    await router.replace({
      name: 'interview-history',
      query: historyQueryRecord(parsed.page, parsed.pageSize),
    })
    return false
  }
```

Real trigger sequence:

1. Open `/interviews?page=0` (or other non-canonical query) or `/admin/questions` as unresolved identity that later 403s.
2. Click 工作台 before `replace` completes.
3. In-flight `replace({ name: 'interview-history' | 'admin-questions' | 'forbidden' })` wins.

User/product impact:

- Bounded: only when canonicalize/403 is actually in flight. Default `/interviews` is already canonical.
- Still a real route-scope race, same family as ENG-03 with a narrower trigger.

Frozen contract violated:

- Route-scoped recovery must not steal navigation after leave (ROUTE_MAP refresh recovery is for **that** page).

Recommended repair:

Same unmount epoch / `mounted = false` as report/detail. Guard every `router.replace` with that flag. Test invalid history query → navigate away → no bounce back to history.

---

### ENG-06

Severity: **P2**  
Files: `frontend/src/App.vue` 5–8; `frontend/src/components/shell/ProductShell.vue` 264–266  
Viewport: keyboard, all product routes (1440 / 1024 / 390)

Concrete code evidence:

Global skip link targets `#app-main`, a wrapper around the entire `RouterView` (sidebar + workspace). ProductShell already has a real `<main class="product-workspace">` without an id. Interview focus shell correctly skips to `#interview-main`.

```5:8:frontend/src/App.vue
  <a class="skip-link" href="#app-main">跳到正文</a>
  <div id="app-main">
    <RouterView />
  </div>
```

User/product impact:

- Keyboard users on Dashboard / History / Report / Admin do not skip the 224px nav (or the 390 overlay structure). Frozen a11y bar requires keyboard usability.

Frozen contract violated:

- `ai-interview-frontend` skill §33; DESIGN_SYSTEM keyboard/focus; ProductShell architecture (sidebar is chrome, workspace is the task).

Recommended repair:

Point the product skip link at `ProductShell`'s `<main>` (give it `id="product-main"`). Keep interview skip to `#interview-main`. Avoid two competing skip links that land on the shell chrome.

---

### ENG-07

Severity: **P3**  
File: `frontend/src/app/views/ArchitecturePlaceholderView.vue`

Concrete code evidence:

The SFC still renders `DEV / Phase 8B placeholder。这不是完成页面。` It is not referenced from `routes.ts` (all routes use real feature views). It is not in the production import graph.

User/product impact:

None unless a future route rewires to it. Maintainability hazard only.

Frozen contract violated:

- FRONTEND_ARCHITECTURE §16: placeholders were to be replaced; leftover placeholder copy must not re-enter production routes.
- Skill §36: no production mock leakage — currently not leaked, but the file remains.

Recommended repair:

Delete the unused view in a later repair batch, or keep it only if an explicit architecture note says it is dead.

---

### ENG-08

Severity: **P3**  
File: `frontend/src/features/auth/views/LoginView.vue` 61–66, 88–90  
Related: `frontend/src/app/stores/auth.ts` 123–140

Concrete code evidence:

`loginFailureMessage` surfaces backend text only for `ErrorKind.VALIDATION`. Login 200 + `GET /users/me` 401 throws `unauthenticated`; the form shows `登录请求暂时失败` after `clearSession()`. ROUTE_MAP optional copy “登录已过期，请重新登录” is not used for 401 redirects either.

Real trigger sequence:

Disabled/revoked user: filter 401 on `/me` after a successful login POST; or expired token during initialize then landing on login without notice.

User/product impact:

- User may believe the network failed rather than that the credential is unusable.
- Does not log them into a protected shell (session already cleared).

Frozen contract violated:

- Weak: PRODUCT_UX_SPEC 401 language; ROUTE_MAP P-SESSION-EXPIRED is optional. Not a workflow break.

Recommended repair:

Map `unauthenticated` on the login form to the frozen 401 public message or the optional expired-session sentence. Do not treat it as password-mismatch 400.

---

## 4. Audit checklist (29 items)

| # | Area | Result |
|---|---|---|
| 1 | Auth lifecycle | Login 200 vs `/me` hydration, single-flight `initialize`, password-change `clearSession`, no refresh-token. Gap: ENG-08 copy. |
| 2 | Router guards | `anonymousOnly` / `requiresAuth` / `requiresAdmin` only after `resolved` USER. Unresolved identity does not fake-403. Safe redirect rejects `//`, schemes, login loops. |
| 3 | Identity / stale async | Knowledge (ENG-01), Create adopt (ENG-02), Session gate (ENG-04), Detail/history replace (ENG-03/05). `currentInterview.refresh` epoch ignore is correct. |
| 4 | HTTP 401/403 | Interceptor 401 only, scoped to request identity/generation. 403 not logout. Login/register public paths skipped. |
| 5 | currentInterview ownership | Single Pinia owner; Dashboard/Create consume it. Broken by unguarded `adoptCreatedSession` (ENG-02). |
| 6 | Create uncertain recovery | GET current first; no auto-retry POST. Missing identity guard (ENG-02). |
| 7 | Durable requestId | Persist record then `dispatchedAt` then POST. Axios does not mint ids. UUID ≤64. |
| 8 | EvaluationGate | sessionStorage + `expectedUserId`. Latest key user-scoped. Write payload can mix Pinia question (ENG-04). |
| 9 | RECOVERY_BLOCKED | 409 other-requestId persists reason; bootstrap prefers blocked over dispatched. |
| 10 | History | COMPLETED API only; null score → 报告未生成; pageSize capped 50. Unmount replace (ENG-05). |
| 11 | Detail spoiler | `detail` assigned only if COMPLETED; IN_PROGRESS skeleton + redirect. No pending MAIN body flash. Navigation hijack is ENG-03. |
| 12 | Report completion guard | Detail GET first; CREATED/IN_PROGRESS → `/interview`; no generate CTA from report NOT_STARTED alone. |
| 13 | Report poll / route-scope | 3s GET poll; `epoch` + `stopPolling` on unmount; `isCurrentScope` includes `sessionId`. |
| 14 | Report mutation uncertainty | Uncertain POST → GET report first; READY stop / GENERATING poll. |
| 15 | Profile/password | `PUT` then `onPasswordChanged`; no auto-retry; identity guard present. |
| 16 | Admin Questions | `questionContent` vs detail `content`; status `PUT .../status`; form `buildRequest()` captured before await. List unmount (ENG-05). |
| 17 | Admin Knowledge | Two-step upload/process; local recent labeled 本机; ENG-01 category capture. Process spreads pre-await `current` object (safe analog). |
| 18 | Storage ownership | Auth / submit / gate / knowledge keys user-namespaced as specified. 401 does not `clear()` submit records or `sessionStorage.clear()`. |
| 19 | Cross-account isolation | Knowledge recent + gate **reads** check userId. Writes after await can mix another user's reactive/Pinia data (ENG-01/02/04). |
| 20 | Mutation retry | No Axios POST/PUT retry. Answer same pair; report GET-first; create GET-first; register/upload/admin create not auto-retried. |
| 21 | Vue boundaries | Views compose feature components; session logic in composable. Create view still owns mutation (ENG-02). |
| 22 | Pinia ownership | Only `auth` and `currentInterview`. History/detail/report page-local. |
| 23 | TypeScript DTOs | Match frozen VOs inspected (`InterviewQuestionResponse`, `InterviewEvaluationResponse`, `UploadKnowledgeDocumentResponse`, `AdminQuestionDetailResponse` / scoring points without extra ids). `overallScore` JSON number vs `BigDecimal` acceptable. `result` typed as `EvaluationLevel`. Knowledge upload reuses `QuestionCategory` names equal to backend `KnowledgeCategory`. |
| 24 | Accessibility semantics | Labels/errors on auth/profile; focus-visible global; session dock 44px at 720. Skip-link (ENG-06). Login alert `outline: none` is P3-adjacent, not listed separately. |
| 25 | Production mock leakage | No `林可` / `76.43` / prototype import in `src`. Fixture scripts live under `frontend/scripts/` and are not imported by production. ENG-07 dead placeholder. |
| 26 | Error / unhandled promise | Report poll `.catch(() => {})`; login/register focus alerts. Session submit 401 swallowed by design. |
| 27 | Route-change / unmount | Report good; Detail/history/admin list not (ENG-03/05). |
| 28 | Maintainability / duplicate state | No second session store besides ENG-02 pollution. `uncertainMutation` imported into admin/profile from interview utils — P3 duplication not raised as a defect. |
| 29 | Security-sensitive client behavior | Token in localStorage (accepted SPA). JWT role not used as admin authority. `userId` not sent as API identity. Open-redirect guarded. XSS of localStorage not claimed as a new defect. |

---

## 5. FALSE POSITIVES REJECTED

| Pattern | Why it is not a finding |
|---|---|
| `CreateInterviewView` re-reads `selectedDifficulty.value` after await for resume notice | `selectDifficulty` returns while `submitting`; fieldset disabled. Identity change does not rewrite the ref. HTTP body is built at dispatch. Defect is adopt/navigation (ENG-02), not difficulty capture. |
| `useAdminKnowledge.processDocument` spreads `current` after await | `current` is the object found **before** await. Category/title on that object are not re-read from form refs. Identity mismatch still persists to `operationUserId` by design (tested). |
| `useAdminQuestionForm.buildRequest()` then await | Request snapshot is taken before HTTP. Post-await uses `created.questionId` / captured `id`. Identity + route + mutationEpoch guarded. |
| `useProfile.submitPassword` reads password refs at `changePassword({...})` | That is the dispatch snapshot. After await, passwords are not persisted. Fields disabled while `submitting`. Identity guarded before navigation. |
| `useInterviewReport` poll vs route change | `epoch++` on bootstrap and unmount; `isCurrentScope` checks `sessionId`; `stopPolling` on unmount. |
| EvaluationGate key `interview.evaluationGate.{sessionId}` without userId | Documented. Reads require `gate.userId === expectedUserId`. Legacy gates without `userId` rejected. The real hole is **writing** another user's Pinia question into that object (ENG-04). |
| `currentInterview.refresh` same-epoch single-flight | Closed in 8B-2; stale epoch ignored. Broken only when `adoptCreatedSession` runs for the wrong user (ENG-02). |
| 403 treated as logout | Not in `httpClient`. Admin features `replace` forbidden. Guard keeps token. |
| JWT `seedRole` as admin gate | `isAdmin` requires `identityStatus === resolved` and `me.role`. Unresolved ≠ USER forbidden. |
| 401 deletes submitRecord | `clearSession` only removes `interview.auth.credential`. Submit keys remain. |
| Axios mutation retry / Axios `requestId` | Interceptor has neither. Timeout 120s. |
| Detail spoiler flash of PENDING MAIN | `detail` is null until COMPLETED; IN_PROGRESS is skeleton. |
| Report generate from IN_PROGRESS `NOT_STARTED` | Bootstrap requires COMPLETED session first. |
| History `totalScore === 0` displayed | Real Java score 0 is allowed; null uses 报告未生成. |
| `expiresAt` never pre-empts HTTP | Frozen contract uses 401, not client clock, as auth end. Stored for credential shape only. |
| Manual EVALUATION_PENDING “检查当前题目” instead of interval poll | User can retry the same pair (allowed). Not a missing requestId. |
| `isAuthPublicPath` substring | Only `/auth/login` and `/auth/register`. |
| Knowledge `fileInputKey` unused / file not cleared after upload | Duplicate upload is allowed by frozen API (each insert). Polish only; not raised. |
| `processDocument` `finally` clears `processingId` without identity match | Compared to captured `documentId`; identity reset already nulls it; IDs are global PKs. |
| Production fixture/mock in `frontend/src` | No product sample names. `frontend/scripts/phase-8b*-mock-api.mjs` are review/dev servers, not app imports. |
| Tests 266 passing ⇒ behavior correct | Rejected as proof. ENG-01/02/04 are untested races. |

---

## 6. Analogous after-await reactive reads

Searched every `await` in `frontend/src`. Confirmed same class as P9-CARRY-01:

| Location | Captured for request | Re-read after await | Verdict |
|---|---|---|---|
| Knowledge `upload` `category.value` | HTTP yes (call time) | Local catalog `category.value` | **ENG-01** |
| Session `workspaceQuestion.value` | POST uses `record` | Gate `answeredQuestion` | **ENG-04** |
| Create `adoptCreatedSession` / `router.push` | POST body difficulty | Pinia + navigation | **ENG-02** (identity, not difficulty) |
| Create `selectedDifficulty` comparison | — | difficulty ref | Rejected (UI lock) |
| Knowledge `processDocument` | `documentId` + `current` object | spread `current` | Rejected |
| Admin question submit | `request` snapshot | response id | Rejected |
| Profile passwords | dispatch args | not reused | Rejected |
| Report `operationSessionId` | captured | used after await | Rejected (correct) |

---

## 7. Missing tests (not separate defects)

These are the tests that should accompany repairs; they are not extra P0/P1 by themselves:

- Knowledge upload category capture across identity change (P9-CARRY-01).
- Create POST resolve after account switch must not `adoptCreatedSession`.
- Session submit 200 after account switch must not write B's question into A's gate.
- Detail IN_PROGRESS GET resolve after leaving the route must not `replace` to `/interview`.

---

## 8. Commands

| Command | Exact result |
|---|---|
| `npm run typecheck` | PASS (`vue-tsc -b --pretty false`) |
| `npm run lint` | PASS (`eslint .`) |
| `npm run test:unit` | 36 files, **266 passed**, 0 failed, duration ~14s |
| `npm run build` | PASS (`vue-tsc -b && vite build`, 2138 modules, ~595ms) |

Limitations: real E2E not run. Environment blockers EMPTY_QUESTION_BANK / DEEPSEEK_UNAVAILABLE / NO_AUTHORIZED_ADMIN_TEST_CREDENTIAL unchanged. This review is implementation inspection plus unit tests, not 8C evidence.

---

## 9. Scope confirmation

```text
PRODUCTION CODE EDITS = NONE
BACKEND EDITS = NONE
CONSOLIDATION = NOT IN THIS FILE
```
