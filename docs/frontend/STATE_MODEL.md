# STATE_MODEL

Status: Design Gate 1 candidate  
Goal: one source of truth per fact. Pinia / LocalStorage / component state must not each keep a competing interview truth.

This is a state contract. It is not a Pinia implementation.

---

## 1. State kinds

| Kind | Lives in | Examples | Authority |
|---|---|---|---|
| Server State | API responses, refetched | session, history, report, me, questions | Frozen backend |
| Auth State | memory + durable token storage | accessToken, tokenType, expiresAt, last known role/userId | Token is a credential. Role/status from DB via `/me` or 401/403 |
| Route State | URL | path, `page`, `sessionId`, `redirect` | URL |
| Local Form State | the form component | login fields, answer draft **before** persist, create difficulty, admin question fields | Component. Discard on leave unless specified |
| Transient UI State | component / ephemeral store | submitting, polling, toast, dialog open | Never treat as server truth |
| Derived State | computed from the above | `hasActiveInterview`, `currentMainNumber`, `canSubmit`, `reportReady`, `isAdmin` | Must not be stored separately |

---

## 2. Auth

### 2.1 Values

```text
anonymous
authenticated
expired / invalid token
role = USER | ADMIN
```

There is no refresh-token, no session cookie, no backend logout.

Recommended stored credential (auth only):

```text
accessToken
tokenType        // always Bearer
expiresAt        // client clock: loginTime + expiresInSeconds (2h config)
```

Do **not** store password.  
Do **not** treat JWT payload role as authorization; `/me` or admin 403 is the check. Login `role` may seed UI chrome until `/me` returns.

### 2.2 Transitions

| From | Event | To |
|---|---|---|
| anonymous | login 200 | authenticated |
| anonymous | register 200 | anonymous (then login page) |
| authenticated | logout click | anonymous (discard token) |
| authenticated | HTTP 401 | anonymous + login redirect |
| authenticated | password change 200 | anonymous + login (token already invalid) |
| authenticated | HTTP 403 | authenticated (forbidden page) |
| authenticated | `/me` 400 `账号已被封禁` | treat as unusable account; next API likely 401 — go login |

### 2.3 401 recovery

1. Stop in-flight UI that would display protected data.
2. Clear auth storage.
3. Keep the **durable submitRecord** in `localStorage` (namespaced by `userId`). Do not delete it on 401. See §4.4.
4. Redirect `/login?redirect=<safe path>`.
5. After login, resume the path. Re-fetch GET server state. Restore durable submitRecord if the key matches this user/session/current question. **Do not auto-POST.** The user retries Submit, which reuses the stored pair.

### 2.4 Who owns auth

One auth module. Router guards read it. Axios interceptor handles **401 only** (clear auth, redirect). It must **not** auto-retry POST/PUT. Components do not parse JWT for role.

---

## 3. Server state ownership

| Fact | Owner endpoint | Do not also store as |
|---|---|---|
| Who am I | `GET /api/users/me` | a second “profile store” copied from login forever |
| Active session + current question | `GET /api/interviews/current` | Dashboard-only copy that can go stale |
| History page | `GET /api/interviews/history` | fake dashboard stats |
| Timeline | `GET /api/interviews/{id}` | Report fields |
| Report | `GET /api/interviews/{id}/report` | Detail evaluations mashed into a fake summary |
| Admin question page | `GET /api/admin/questions` | — |
| Admin question detail | `GET /api/admin/questions/{id}` | — |

After `POST` that mutates interview/report, **re-fetch** the relevant GET. Submit response may be used immediately for evaluation reading, then GET current to confirm the workspace after the user continues.

---

## 4. Interview

### 4.1 Named UI/server phases

| Name | Meaning | How the frontend knows |
|---|---|---|
| `noActiveInterview` | User can start a new one | `GET /current` → `data=null` |
| `activeInterview` | CREATED or IN_PROGRESS exists | `GET /current` has session |
| `answerDraft` | User is writing; HTTP for this logical submit has **not** been dispatched | textarea editable; no submitRecord with `dispatched=true` |
| `answerSubmission` | HTTP submit in flight | transient `submitting=true`; durable submitRecord already written |
| `evaluationPending` | HTTP was dispatched; result is 409 evaluating, timeout, network, or 502 with the same question still current | local `dispatched=true` + that HTTP outcome. **Not** inferred from GET `/current` AnswerStatus (that field is not on current) |
| `evaluationReturned` | Evaluation is on screen; next question **not** yet shown as the workspace | submit 200 with `evaluation`; local `evaluationGate=open` |
| `followUp` | User accepted continue after FOLLOW_UP | `nextAction=FOLLOW_UP` and user confirmed; GET current type FOLLOW_UP |
| `nextMain` | User accepted continue after NEXT_MAIN | confirmed; GET current next MAIN |
| `completed` | Session COMPLETED | submit `sessionStatus=COMPLETED` or GET current null and last known session COMPLETED |

These names are for UX. The backend statuses remain `InterviewSessionStatus`, `InterviewAnswerStatus`, `EvaluationPhase`, `DecisionAction`.

### 4.2 Single interview truth

Authoritative live workspace:

```text
GET /api/interviews/current
```

Local additions allowed:

```text
answerDraft (unpersisted text)
submitRecord (mandatory localStorage identity for the logical submit; see §4.4)
evaluationGate (user has not confirmed continue)
```

Do not keep a second `session` object in a global store that Dashboard, Session, and Report each mutate.

### 4.3 Progress derivation

```text
planned = session.plannedQuestionCount
completed = session.completedQuestionCount
currentMainNumber = completed + 1        // while IN_PROGRESS
isFollowUp = currentQuestion.questionType === 'FOLLOW_UP'
```

`InterviewQuestionResponse` has no `planOrder`. Do not fetch Detail during the live session to get it.

### 4.4 `requestId` ownership — mandatory durable submitRecord

**Owner:** the Interview Session feature module.  
Axios / HTTP interceptors must **not** generate `requestId` and must **not** auto-retry this POST.

`GET /api/interviews/current` returns the current question, not the current answer’s `requestId` or `InterviewAnswerStatus`.  
`GET /api/interviews/{sessionId}` Detail answer has `answerStatus` but **no** `requestId`, and the live session must not use Detail for recovery (spoiler + still no requestId).  
Frontend therefore **cannot** reconstruct submit identity from Frozen APIs.

#### Mandatory sequence (first Submit)

```text
1. User clicks 提交
2. If this question has no submitRecord:
   generate requestId (UUID, length ≤ 64)
3. Snapshot exact answerContent
4. BEFORE HTTP dispatch:
   durable persist submitRecord to localStorage
5. Durable persist succeeds
6. Only then POST /api/interviews/{sessionId}/answers
```

If step 4 fails, **do not POST**. Show that local recovery storage is unavailable. Keep the draft editable; no `requestId` is considered dispatched.

#### MVP durable storage (not optional)

```text
storage = localStorage
key     = interview.submit.{userId}.{sessionId}.{interviewQuestionId}
```

`userId` is a **client namespace only**. It is never sent to the backend.

Value at least:

```text
{
  userId,
  sessionId,
  interviewQuestionId,
  requestId,
  answerContent,
  createdAt,
  updatedAt
}
```

Durable identity lock order (client safety):

```text
1. persist logical pair (requestId + exact answerContent)
2. persist dispatchedAt
3. only then POST
```

`dispatchedAt` means: from this point onward the client must treat the request as **possibly dispatched / identity locked**.

It is **not**:

- server confirmation that Backend received the answer
- network-layer delivery proof
- `InterviewAnswerStatus`

Do not claim the Backend has received the answer merely because `dispatchedAt` is set. Writing the lock before the HTTP call is the conservative safety strategy: it avoids a window where HTTP may already have been sent but the durable identity lock failed to persist.

Irreconcilable identity conflicts persist optional recovery fields on the same record:

```text
recoveryBlockedReason = OTHER_REQUEST_ID | REQUEST_ID_OTHER_QUESTION | DIFFERENT_CONTENT
recoveryBlockedAt
```

Do not delete the record and mint a new `requestId`. Bootstrap for the matching current question is:

```text
if recoveryBlockedReason → RECOVERY_BLOCKED
else if dispatchedAt → EVALUATION_PENDING
else → ANSWERING
```

Do not treat `sessionStorage` as the identity store. `sessionStorage` may still hold ephemeral `evaluationGate` UI (§4.6). Submit identity is `localStorage`.

Do not generate `requestId` on page load, on every keystroke, or on Axios retry.

#### Retry rule (HTTP already dispatched)

If the POST was handed to the network and the outcome is any of:

```text
network error
timeout
connection reset
unknown response
502
409 EVALUATING   // 答案正在评估中，请稍后重试
```

or any other case where **server persist is not a proven 200 evaluation replay**:

```text
reuse same requestId
reuse exact answerContent
```

Forbidden:

```text
mint new requestId
edit answerContent and submit as a new attempt
ask the user whether the server persisted
```

There is no UX of “我确认尚未提交，使用新内容”. The user cannot know server persist.

#### Editing rule

Edit / discard pending id / generate a new id on a later submit **only** when the frontend can prove HTTP was **never dispatched**, for example:

- client-side validation stopped the request (blank, UTF-8 over 65535);
- localStorage persist failed **before** dispatch;
- the user edited the textarea **before** pressing Submit (no record, or record exists but `dispatchedAt` is absent because persist was for a previous unsent click that was cancelled — MVP: do not persist until Submit; persist-then-dispatch is atomic from the user’s view).

Once HTTP has been dispatched:

- lock the textarea to the snapshot;
- keep that lock until this logical submit has a **definite** recovery result (200 with evaluation, or 409 that names a different requestId — see Lost Record).

#### When the record may be discarded

| Condition | Action |
|---|---|
| User edits **before** Submit (HTTP never dispatched) | No record yet, or delete undispatched record; next Submit generates a new id |
| `GET /current` `interviewQuestionId` ≠ record’s question | Question moved on. Keep the old key unused; do not apply it to the new question |
| User confirmed continue (FOLLOW_UP / NEXT_MAIN) after 200 evaluation | Stop using the previous question’s record |
| Session `COMPLETED` after 200 `FINISH` | Stop using session submit records for the workspace |
| Logout | **Keep** localStorage records (namespaced by userId) |

Never mint a new id because “the last HTTP failed”.

#### Lost Record — `RECOVERY LIMITATION`

If the durable submitRecord is **actually missing** and `GET /current` still returns the **same** question:

Frontend **cannot** tell from Frozen APIs whether the answer is:

```text
never persisted
vs SUBMITTED
vs EVALUATING
vs FAILED
```

`GET /current` does not expose answer `requestId` or answer status.  
This is **`RECOVERY LIMITATION`**, not a Backend Bug and not a backend-unfreeze trigger.

MVP avoids it by persisting **before** dispatch. Do not document a procedure that “detects AnswerStatus from current”.

If the user (or a buggy client) submits a **new** `requestId` and the backend returns:

```text
当前问题已经使用其他requestId提交过答案
```

and the original record is gone:

```text
当前答案已在服务端登记，但本机恢复信息已丢失。
请不要继续创建新的提交。
```

Mark UI state **`RECOVERY_BLOCKED`**. Do not mint another `requestId`. Do not offer a new-content submit.

#### Content lock vs 200 success

200 with `evaluation`: lock to snapshot (already locked if dispatched), open evaluationGate.  
409 evaluating / 502 / network after dispatch: lock to snapshot, retry **same pair** only (evaluating: wait/poll current question identity; still same pair).  
`RECOVERY_BLOCKED`: lock the field; disable submit.

### 4.5 Submit record vs evaluationGate

`submitRecord` = localStorage, durable, identity of the logical POST.  
`evaluationGate` = sessionStorage or memory, UX pause after **200**, not a substitute for `requestId`.

### 4.6 Evaluation gate (do not auto-advance)

When submit returns 200:

```text
evaluationGate = {
  userId,              // client ownership only; never sent to Backend
  sessionId,
  evaluation,
  nextAction,
  nextQuestion,
  sessionStatus,
  answeredQuestion,
  answerContent
}
workspaceQuestion = the question that was answered (keep on screen)
```

Storage:

```text
interview.evaluationGate.{sessionId}
interview.evaluationGate.latestSessionId.{userId}
```

Every read takes `expectedUserId` from `auth.userId` and returns the gate only when `gate.userId === expectedUserId`. A User A FINISH gate must not restore into User B's `/interview` when GET current is null. Same-user FINISH refresh recovery remains valid.

Do **not** set workspaceQuestion = nextQuestion yet.

On explicit continue:

- FOLLOW_UP / NEXT_MAIN: workspaceQuestion = nextQuestion (or GET current), clear evaluationGate, clear submitRecord for old id, new draft empty.
- FINISH: go to report route; current becomes null.

If the user refreshes during evaluationGate: GET current may already point at the next question. Recovery:

- If submitRecord exists for a **previous** question, do not restore that draft onto the new question.
- If GET current id equals the answered id, restore evaluation only if we still have it in sessionStorage (`interview.evaluationGate.{sessionId}`).
- If GET current id already moved on, skip the gate (user already advanced server-side). Show the new question. Optionally a one-line “上一题评价可在结束后的详情中查看”.

MVP should persist evaluationGate in sessionStorage until continue or question id changes.

### 4.7 Derived interview flags

```text
hasActiveInterview = current != null
primaryCta = hasActiveInterview ? continue : start
canSubmit = IN_PROGRESS
  && currentQuestion
  && !submitting
  && !evaluationGate
  && !RECOVERY_BLOCKED
  && draft non-blank
  && utf8 bytes ≤ 65535
  && (no dispatched lock || draft equals locked snapshot)
```

---

## 5. Report

Server enum: `NOT_STARTED | GENERATING | READY | FAILED`.

The report **page** is a completed-session surface. `GET /report` returning `NOT_STARTED` is **not** proof the session is complete (an `IN_PROGRESS` session also has `NOT_STARTED`). Route/state must load `GET /api/interviews/{sessionId}` first. See ROUTE_MAP.

| Session from Detail | Report GET | UX |
|---|---|---|
| CREATED / IN_PROGRESS | ignore for generation | Redirect `/interview`. Do not show 生成报告 |
| COMPLETED + NOT_STARTED | status only | CTA 生成报告 |
| COMPLETED + GENERATING | status only | Waiting; poll GET report; do not POST again |
| COMPLETED + READY | full body | Render; load Detail evidence |
| COMPLETED + FAILED | status only | CTA 重新生成 |
| 404 | — | Not found |
| 401 | — | Login |

After an **uncertain** report POST (timeout / network / unknown):

```text
GET report first
READY        → stop
GENERATING   → poll GET
FAILED / NOT_STARTED → POST according to that status
```

Do not blindly re-POST. Do not call Weakness APIs.

`overallScore` and `result` are server-calculated. Do not recompute `result` in the client except for display labels of the same enum.

---

## 6. Admin knowledge local list

Because there is no list API:

```text
localRecentDocuments = [{ documentId, title, category, fileName, processingStatus, updatedAt }]
```

This is **Local Form / device state**, labeled “本机最近上传”. After process 200, set status READY locally. After 409 READY, same. After 409 processing, mark PROCESSING. After 502, keep id for retry.

Never present this as the full knowledge base.

---

## 7. Error recovery map

### 7.1 Global retry principle

```text
GET
→ normally retryable

POST / PUT
→ retry semantics MUST come from FRONTEND_API_MATRIX
```

Forbidden: a global Axios (or equivalent) interceptor that automatically retries mutations on 500 / network / timeout.

Keep user-visible form/draft state after network failure. Whether a second POST is allowed is per feature.

### 7.2 Case map

| Case | UI | Recovery | State notes |
|---|---|---|---|
| 400 validation | Inline on field | Fix input | Keep form. HTTP never dispatched for invalid client checks |
| 400 login | Inline | User submits login again | Stay anonymous. Login is a new credential attempt, not interview idempotency |
| 400 service unavailable (evaluation / report / knowledge process) | Page banner | User may retry **that feature’s** allowed mutation later | Keep draft / local documentId |
| 401 | Login | Re-auth | Clear auth; **keep** durable submitRecord |
| 403 | Forbidden | Go dashboard | Keep auth |
| 404 | Not found / “不存在或无权查看” | Back | Do not probe other ids |
| 409 evaluating | Waiting on session page | Poll GET current **question identity** only; retry POST **same** requestId + exact content | Do not mint id. Do not read AnswerStatus from current |
| 409 generating | Waiting on report | Poll GET report | Do not POST storm |
| 409 `当前问题已经使用其他requestId提交过答案` | If durable record missing: `RECOVERY_BLOCKED` copy | Stop | Do not mint another id |
| 409 other requestId/content/question | Explain; lock to snapshot if dispatched | Same pair only | |
| 409 session not IN_PROGRESS / not current question | Stale UI | GET current and realign | Do not apply submitRecord to a different question |
| 409 interview not completed (report) | Must not be the first discovery | Route already required COMPLETED via GET session | If it still happens, redirect `/interview` |
| 409 knowledge READY / processing | Copy by status | Process retry is **state-specific** (same documentId) | Update local recent list |
| 413 | Upload message `上传文件不能超过5MB` | Smaller file | Do not auto-retry the same oversize POST |
| 405 | Not found / client defect | — | |
| 500 on **GET** | Banner `系统异常，请稍后重试` | GET may be retried | |
| 500 on **POST/PUT** | Banner | **No generic retry.** Follow FRONTEND_API_MATRIX for that endpoint | Keep local state |
| 502 | Banner `外部服务暂时不可用` | Answer: same pair. Report: GET report first. Knowledge process: same documentId, state-specific | |
| Network / timeout on **GET** | Banner | Retry GET | |
| Network / timeout on **POST/PUT** | Banner | Per matrix. Answer: same pair, locked snapshot. Register / question create / knowledge upload: **not** auto-retry | Unknown persist: never mint a new answer requestId |
| Refresh | Re-fetch GETs; restore submitRecord from localStorage | See §4.4 | |
| Duplicate click | Disable in-flight buttons | — | Transient UI |
| Stale UI (old nextQuestion) | Confirm with GET current after continue | — | Server wins |

### 7.3 Mutation retry (must match API Matrix)

| Mutation | Retry allowed? |
|---|---|
| Answer POST | Yes **only** with same `requestId` + exact `answerContent` after durable persist |
| Report POST | After uncertain failure: GET report first. READY stop; GENERATING poll; FAILED or NOT_STARTED on a **COMPLETED** session → POST |
| Interview create POST | GET current first. Resume if active; else user-initiated POST |
| Knowledge process POST | Same `documentId`, state-specific (FAILED retry; fresh PROCESSING wait; READY stop) |
| Register POST | Not naturally idempotent. Do not auto-retry. User may click again; duplicate → 400 `账号或邮箱已存在` |
| Admin question create POST | Not naturally idempotent. Do not auto-retry |
| Knowledge upload POST | Not naturally idempotent (each upload inserts). Do not auto-retry |
| Password PUT | Not auto-retry. User resubmits consciously |
| Login POST | New attempt with credentials; not interview idempotency |

---

## 8. What not to store

- Duplicate copies of `InterviewSession` in Dashboard store and Interview store that can diverge.
- JWT role as the long-term admin gate without `/me`.
- Fake KPI derived from nothing.
- Upcoming PENDING question content from Detail during an active session.
- `userId` sent to any backend API (APIs ignore client userId). `userId` **may** namespace localStorage keys for submit records; that is not an API identity spoof.
