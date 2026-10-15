# FRONTEND_ARCHITECTURE

Status: Phase 8B-4 History + Detail + Report  
Frozen backend: `20a933ae8092ef908c2f03193c0b0f9547da71c1`  
Visual direction: Technical Interview Workspace V2 / V2.2  
Prototype: visual / interaction reference only. **Prototype is not the production frontend.**

```text
docs/frontend/prototype  ≠  frontend/
```

Phase 8A established the Vue 3 production skeleton.  
Phase 8B-1 implemented Auth + Product Shell.  
Phase 8B-2 implemented Dashboard and Create / Resume.  
Phase 8B-3 implemented Interview Session, durable submit, evaluation gate, and retry / recovery.  
Phase 8B-4 implements History, Interview Detail, and Interview Report. Profile and Admin remain placeholders.

---

## 1. Technology Decisions

| Choice | Decision |
|---|---|
| Framework | Vue 3 Composition API, `<script setup lang="ts">` |
| Bundler | Vite |
| Language | TypeScript |
| Router | Vue Router |
| Client state | Pinia, only for long-lived shared state |
| HTTP | Axios, `baseURL=/api` |
| Icons | `@lucide/vue` (Lucide outline family). `lucide-vue-next` is deprecated; same icon language. Custom `ProductMark` is not a Lucide icon |
| CSS | CSS variables + plain / Vue scoped CSS. No Tailwind. No Element Plus / Ant Design Vue / Vuetify / Bootstrap |
| Tests | Vitest + Vue Test Utils + jsdom |
| Query / form kits | Not introduced |
| Dev cross-origin | Vite proxy `/api` → local Spring Boot. Frozen backend CORS is not modified |

`vue-best-practices` skill was **MISSING** in project and user skill directories. Vue conventions still follow the project `ai-interview-frontend` skill: Composition API, minimal source state, computed derivation, feature composables, focused components.

---

## 2. Directory Structure

```text
frontend/
  src/
    main.ts
    App.vue
    app/
      router/
      stores/auth.ts
      session/sessionScope.ts   # user-scoped server-state invalidation
      styles/
      views/                 # Phase 8A placeholders + forbidden/404
    api/                     # HTTP client, Result unwrap, ApiError
    components/
      ui/                    # small primitives + ProductMark
      shell/                 # ProductShell, InterviewFocusShell, Session split
    features/
      auth/api + storage + views + validation + copy
      interview/api + stores + composables + storage + types
    types/                   # Frozen Backend DTO/VO
    utils/
  tests/
```

Feature folders exist only where there is real code.

```text
features/
  auth/        Phase 8B-1
  dashboard/   Phase 8B-2 views + page-scoped history composable
  history/     shared history API + HistoryView page-local state
  interview/   current owner, session, detail, report
```

Profile and admin **pages** remain placeholders. Dashboard recent history and the full History page both use `fetchInterviewHistory`; each keeps its own page-local state. There is no global history / detail / report Pinia store.

---

## 3. Dependency Boundaries

Allowed direction:

```text
views / shells
  → feature composables / stores
    → feature api
      → api/httpClient
        → Axios
```

Forbidden:

- Views calling Axios directly
- Axios generating `requestId`
- Axios auto-retrying POST/PUT
- Axios toasting business errors
- Feature stores copying each other's server session
- Production `frontend/src` importing `docs/frontend/prototype`

---

## 4. Routing Architecture

Routes follow `docs/frontend/ROUTE_MAP.md`.

| path | auth | role | shell |
|---|---|---|---|
| `/login` `/register` | anonymousOnly | — | public |
| `/` | requiresAuth | USER/ADMIN | product |
| `/interviews/new` | requiresAuth | USER/ADMIN | product |
| `/interview` | requiresAuth | USER/ADMIN | interview |
| `/interviews` | requiresAuth | USER/ADMIN | product |
| `/interviews/:sessionId` | requiresAuth | owner via API | product |
| `/interviews/:sessionId/report` | requiresAuth | owner via API | product |
| `/profile` | requiresAuth | USER/ADMIN | product |
| `/admin/**` | requiresAuth + requiresAdmin | ADMIN | product |
| `/forbidden` | requiresAuth | any | product |
| `*` | — | — | public 404 |

Route meta: `requiresAuth`, `requiresAdmin`, `anonymousOnly`, `shell`, `placeholderTitle`.

Guards:

1. Wait until auth is initialized. Concurrent `initialize()` callers share one Promise and one `GET /users/me`.
2. `anonymousOnly` + authenticated → `/` or safe `redirect`.
3. `requiresAuth` + anonymous → `/login?redirect=<safe-path>`.
4. `requiresAdmin` is a **client convenience only**. Backend HTTP 403 remains the authorization authority.

Admin guard:

| identity | `/admin/**` |
|---|---|
| anonymous | login |
| `resolved` USER | `/forbidden` (token kept) |
| `resolved` ADMIN | allow |
| authenticated + `unknown` / `unavailable` | **do not** map to forbidden; defer to the admin API / server 403 |

A stored token with `me=null` after `/me` network failure is not a confirmed USER.

Ownership is **not** a frontend role. Other users' sessions are backend 404. Detail and Report parse `:sessionId` on the client (positive integer only) and load `GET /api/interviews/{sessionId}` before rendering. `CREATED` / `IN_PROGRESS` replace to `/interview` without assigning question content, so pending MAIN text cannot flash. Report then loads `GET /report` only after COMPLETED.

### Safe redirect

`resolveSafeRedirect` accepts only in-app paths starting with a single `/`. It rejects `https://…`, `//host`, backslashes, and login/register loops.

---

## 5. Shell Architecture

Two shells, not one mega-layout with `if (isInterview)`.

### ProductShell

Used by Dashboard, Create, History, Detail, Report, Profile, Admin.

- 1440: 224px sidebar + workspace (max ~1200px)
- 1024: 200px compact sidebar
- 390: top bar + menu, no forced desktop rail

### InterviewFocusShell

Used only by `/interview`.

- No product sidebar
- Focus header: mark + 面试训练 + 面试进行中 + 退出到工作台
- Workspace hosts `SessionSplitWorkspace`

### Session split foundation

- 1440: question 40% / answer 60% (inside 38–42 / 58–62)
- 1024: 42% / 58% while readable
- 390: single column
- Dock is a **sibling slot** in the answer pane, not `position: fixed` overlay content
- Max workspace ~1240px

---

## 6. Auth Ownership

One Pinia store: `useAuthStore`.

Persisted credential only:

```text
accessToken
tokenType
expiresAt
userId          // localStorage namespace for submitRecord; never sent as API identity
```

`GET /api/users/me` is the identity authority (`me`). Login `role` may seed chrome until `/me` returns. It is not used as an admin authorization fallback when identity is unresolved.

`login()` returns `LoginOutcome`. Credential persistence and identity hydration are separate:

- `POST /auth/login` 200 means the access token is valid locally.
- `GET /users/me` success → `resolved` and `kind=authenticated`.
- `GET /users/me` transient failure → keep token, `unavailable`, `kind=authenticated_identity_unavailable`. Login UI must redirect into the product shell, not show “登录失败” / “账号或密码错误”.
- Identity chrome then shows “用户信息暂时无法加载” with retry.

`initialize()` is single-flight: `ready=true` returns immediately; overlapping callers share one Promise; `initializePromise` is cleared in `finally`.

Identity status:

```text
anonymous     no credential
unknown       credential present, /me not finished
resolved      /me succeeded; role is trusted for client chrome/guard convenience
unavailable   credential kept, /me failed transiently (network / 5xx / 502)
```

`isAdmin` is true only when `identityStatus=resolved` and `me.role=ADMIN`.

| Event | Behavior |
|---|---|
| Login 200 + `/me` 200 | Store credential; identity `resolved`; return `LoginOutcome.authenticated` |
| Login 200 + `/me` network/5xx/502 | Store credential; identity `unavailable`; return `LoginOutcome.authenticated_identity_unavailable`. **Do not throw. This is not bad credentials.** |
| Login 200 + `/me` 401 | Clear session; throw. True authentication end |
| Login 400 | Stay anonymous; throw; form inline error. Not a logout |
| Register 200 | Stay anonymous; Phase 8B prefills account on login |
| Password change 200 | `onPasswordChanged()` clears token immediately |
| HTTP 401 | Clear auth, single-flight redirect to login, keep submitRecord |
| HTTP 403 | Keep token; `/forbidden` only after identity is `resolved` USER |
| `/me` network failure | Keep token; `identityStatus=unavailable`; do not fake-403 |

No password is stored. No refresh-token API.

---

## 7. Server State Ownership

Backend remains server truth.

| Fact | Owner | Lifetime |
|---|---|---|
| Auth credential + `me` | `useAuthStore` | App |
| **Current interview** | `features/interview` `useCurrentInterviewStore` via `useCurrentInterview()` | App, one owner |
| History page | `features/history/composables/useInterviewHistory.ts` for `/interviews` (`page`/`pageSize` from the URL). Dashboard recent still uses `useDashboardHistory` (`page=1`, `pageSize=5`). | Route |
| Detail | `features/interview/composables/useInterviewDetail.ts` | Route |
| Report | `features/interview/composables/useInterviewReport.ts` | Route |
| Create difficulty | local form (`selectedDifficulty` starts `null`; no default) | Route |
| Admin lists | page state in Phase 8B | Route |

### current interview authority

`GET /api/interviews/current` has a single frontend owner:

```text
features/interview/stores/currentInterview.ts
features/interview/composables/useCurrentInterview.ts
```

Dashboard and Create **consume** this owner. They must not keep a second long-lived session copy.

`refresh()` is **single-flight** for the same user/epoch: overlapping callers share one in-flight `GET /api/interviews/current` Promise. Identity change still increments `epoch` through `invalidate()`, which drops the in-flight Promise so the next refresh issues a new HTTP request. Stale in-flight results with a mismatched epoch are ignored and must not repopulate the store.

After a successful `POST /api/interviews`, the owner uses `adoptCreatedSession(session)` rather than a view writing `store.session` directly. Adopt bumps `epoch`, clears in-flight GET, sets `loaded=true` / `loading=false` / `error=null`, and keeps a later stale GET from overwriting the created session. The Session page still confirms current with its own load strategy in Phase 8B-3.

Uncertain create failures do **not** auto-retry POST. They `GET /current` (via `refresh()`): active → treat as created/resumed and go to `/interview`; `null` → keep the selected difficulty and allow an explicit user POST; GET failure → Retry Check only.

Submit 200 evaluation is read through EvaluationGate, then GET current confirms the workspace after the user continues.

### User-scoped server state invalidation

`currentInterview` is app-lifetime but **user-scoped**. Auth does not import the interview feature.

```text
app/session/sessionScope.ts
  notifySessionIdentityChanged(userId | null)
  → generation++
  → currentInterview.invalidate()
```

Identity transitions that invalidate:

```text
userId A → null     logout / 401 / password change
null → B            login / initialize
A → B               account switch
```

`refresh()` captures `epoch` at start. `invalidate()` / `clear()` increment epoch. If the in-flight response returns with a stale epoch, it is ignored and must not repopulate the store.

Phase 8A provides the store/composable and typed API. Placeholders do not fetch live data.

---

## 8. Pinia Rules

Pinia is for cross-route, long-lived client state:

- auth
- current interview owner

Not Pinia by default:

- History pagination
- Report loading
- Create selected difficulty
- Admin filters
- Form drafts, including login/register fields
- Register `confirmPassword` (`LOCAL_FORM_STATE` only; never part of `RegisterRequest`)

Do not create one store per page.

---

## 9. API Client

- `httpClient` `baseURL=/api`
- Vite proxy: `/api` → `http://localhost:8080` (`AI_INTERVIEW_BACKEND_URL` override)
- Request interceptor: Bearer token only. Skips `/auth/login` and `/auth/register`
- Response interceptor: normalize to `ApiError`; 401 notify (single-flight); **no toast**; **no mutation retry**; **no requestId**
- Helpers `apiGet` / `apiPost` / `apiPut` unwrap `Result<T>` when `code === 200`
- Runtime envelope requires `object`, numeric `code`, string `message`, and `'data' in object`. `data` may be `null` (current interview, register, password change). Missing `data` is `ApiError` `响应格式无效`.

Feature modules own endpoint calls (`features/auth/api`, `features/interview/api`).

GET may be retried by a feature. POST/PUT recovery must follow `FRONTEND_API_MATRIX`:

| Mutation | Uncertain failure |
|---|---|
| Answer | same `requestId` + exact `answerContent` |
| Report | GET report first |
| Create interview | GET current first |
| Register / upload / admin create | no auto-retry |

---

## 10. Error Model

One class: `ApiError` + `classifyHttpStatus`.

| HTTP | kind | UI owner |
|---|---|---|
| 400 | validation | feature inline |
| 401 | unauthenticated | auth |
| 403 | forbidden | router `/forbidden` |
| 404 | not_found | page/resource |
| 409 | conflict | feature recovery |
| 413 | payload_too_large | upload |
| 502 | external_service | recoverable banner |
| 500 | unexpected | recoverable page; GET may retry |
| network | network | keep local state; mutation per matrix |

---

## 11. Storage Model

| Store | Key | Data |
|---|---|---|
| localStorage | `interview.auth.credential` | token, tokenType, expiresAt, userId |
| localStorage | `interview.submit.{userId}.{sessionId}.{interviewQuestionId}` | SubmitRecord |
| sessionStorage | `interview.evaluationGate.{sessionId}` | EvaluationGate UX pause (`userId` on the object is ownership only) |
| sessionStorage | `interview.evaluationGate.latestSessionId.{userId}` | latest EvaluationGate session pointer, per authenticated user |

401 / logout **must not** delete submitRecord keys.  
401 / logout **must not** `sessionStorage.clear()`. EvaluationGate may remain in sessionStorage because every read requires `expectedUserId` and `gate.userId === expectedUserId`. A later login by another user must not restore another user's gate.

---

## 12. requestId lifecycle

Owner: Interview Session feature. Axios must not mint ids.

```text
User clicks 提交
→ if no record: createRequestId()  (UUID, length ≤ 64)
→ snapshot exact answerContent
→ persistSubmitRecord to localStorage
→ persist must succeed
→ persist dispatchedAt (client identity lock)
→ only then POST
```

If persist of the logical pair fails: do not POST.

`dispatchedAt` means: **from this point onward the client must treat the request as possibly dispatched / identity locked**.

It is **not**:

- server confirmation that the Backend received the answer
- network-layer delivery proof
- `InterviewAnswerStatus`

Do not claim the Backend has received the answer merely because `dispatchedAt` is set. The durable lock is written **before** the HTTP call so a later persist failure cannot leave a network request without a local identity lock.

After the identity lock, network / timeout / 502 / 409 evaluating reuse the same pair. Never mint a new id because HTTP failed.

Irreconcilable 409 identity conflicts (`当前问题已经使用其他requestId提交过答案`, `requestId已用于其他问题`, `requestId已用于不同的答案内容`) persist `recoveryBlockedReason` / `recoveryBlockedAt` on the same SubmitRecord **before** treating the UI as `RECOVERY_BLOCKED`. Do not delete the record or mint a new `requestId`. Blocked takes priority over `dispatchedAt` during bootstrap. If GET `/current` has moved to another question, server wins and the old blocked record is not applied to the new question.

---

## 13. EvaluationGate

Frontend-only type. Not a Backend DTO.

```text
userId              // client storage ownership only; never sent to Backend
sessionId
createdAt
answeredQuestion
answerContent
evaluation
nextAction
nextQuestion
sessionStatus
```

Reads require `expectedUserId` from `auth.userId`. A stored gate is returned only when `gate.userId === expectedUserId`. Legacy or corrupt gates without `userId` are rejected and must not crash.

The latest pointer is user-scoped: `interview.evaluationGate.latestSessionId.{userId}`. The gate object key remains `interview.evaluationGate.{sessionId}`.

Same-user FINISH recovery remains: POST last answer → FINISH gate → GET current=null → refresh `/interview` restores that user's final evaluation. Another authenticated user with current=null must not restore that gate.

It pauses the workspace after submit 200 so the next question does not replace the answered question until an explicit continue.

---

## 14. Design System Production Mapping

Tokens live in `src/app/styles/tokens.css`. They match `DESIGN_SYSTEM.md` V2:

Canvas `--bg`, Surface `--surface`, Subtle `--surface-subtle`, Text / Text 2 / Text 3, Border, Primary / Hover / Soft / Border, Success / Warning / Danger + soft fills, spacing scale, radius 8 / 10, sidebar 224 / 200, session max 1240, workspace max 1200.

Icons: Lucide outline, 16–18px, `currentColor`, stroke 1.7.  
Micro identity: `ProductMark.vue` — question → answer geometry, 20–24px, no Q/A letters, no mascot.

No Desk Script: no serif-first, no warm paper, no article 720px column.

Production code must not fallback to representative prototype names or scores (`林可`, `76.43`). Missing data is loading / empty / error.

---

## 15. Responsive Architecture

| Width | Product | Session |
|---|---|---|
| 1440 | sidebar + workspace | split 40/60 |
| 1024 | compact sidebar | split if readable |
| 390 | top bar / menu | stacked + sibling dock |

No horizontal overflow on the 390 shell.

---

## 16. Feature Implementation Order

Phase 8A does **not** execute these. They are the recommended 8B/8C sequence:

1. **8B-1** Auth + Product Shell pages (login, register)
2. **8B-2** Dashboard + Create
3. **8B-3** Interview Session
4. **8B-4** History + Detail + Report
5. **8B-5** Profile
6. **8B-6** Admin Questions
7. **8B-7** Admin Knowledge
8. **8C** Real backend integration / full flow hardening

Replace each `ArchitecturePlaceholderView` when its feature is implemented.

---

## 17. Testing Strategy

Vitest unit tests for contracts that are easy to get wrong:

- Result / ApiError normalization, including `data: null` vs missing `data`
- Auth storage
- Auth initialize single-flight
- Safe redirect
- submitRecord key, persist-before-dispatch, exact answer, invalid/lost records
- requestId length
- Route meta / guards: anonymous protected, confirmed USER vs ADMIN, unresolved identity must not fake-403, 401, safe redirect
- currentInterview user-scoped invalidation and stale in-flight ignore
- currentInterview same-epoch single-flight refresh
- currentInterview adopt-while-GET-in-flight (stale GET cannot overwrite)
- Dashboard current/history independent loading, empty, region errors, CTA navigation
- Create peer difficulty selection, resume gate, POST body `{ difficulty }`, uncertain mutation recovery
- History URL pagination, canonicalized invalid query, null score copy, empty/error
- Detail COMPLETED timeline, displayOrder, IN_PROGRESS/CREATED spoiler redirect, invalid id
- Report Detail-first guard, status machine, POST once, uncertain GET-first recovery, poll cleanup

Phase 8B-4 does not claim E2E or real backend verification. Browser validation used a local fixture API, isolated from production source.

---

## 18. Anti-patterns

- Translating prototype HTML into Vue
- Importing prototype `data.js`
- Fallback sample product data
- Global Axios mutation retry
- Axios `requestId`
- Toast-all-errors interceptor
- Dashboard store + Interview store each holding a session
- JWT role as long-term authorization
- Treating `/me` network failure as confirmed USER / false `/forbidden`
- Treating 403 as logout
- Keeping currentInterview across logout / account switch
- Committing a stale in-flight current-interview response after identity change
- Open redirect via `?redirect=`
- UniversalCard wrapping every surface
- 30-file design-system kit before real reuse
- Empty feature folders
- `any` as an API model escape hatch
- Frontend-only fields on Backend DTO types
- Changing frozen backend / CORS / DTO for frontend convenience

---

## 19. Tracked Engineering Risks

### R-8B-1 currentInterview same-epoch concurrent refresh

Status: **CLOSED** in PHASE 8B-2  

`CURRENT_INTERVIEW_REFRESH_RACE = CLOSED`

Fix: same-epoch `refresh()` is single-flight (one shared Promise, one `GET /current`). `invalidate()` still bumps `epoch` and drops in-flight work so logout / account switch cannot apply a stale payload. `adoptCreatedSession()` also bumps `epoch` so a slower GET cannot overwrite a POST success.

Covered by `tests/currentInterview.spec.ts`.

### R-8B-1 vue-best-practices skill

Status: MISSING in project and user skill directories. Vue conventions follow `ai-interview-frontend`: Composition API, `<script setup lang="ts">`, local form state, computed derivation, focused components. Do not claim the vue-best-practices skill was applied.
