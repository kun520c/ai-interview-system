# PRODUCT_UX_SPEC

Status: Design Gate 1 candidate (product / IA frozen)  
Source of truth: frozen backend `20a933ae8092ef908c2f03193c0b0f9547da71c1`  
Visual direction: see `DESIGN_SYSTEM.md` — **Technical Interview Workspace V2**. Desk Script / 案头墨卷 is deprecated as visual direction only.  
Audience: Commander / frontend implementer  
This document is product and UX specification only. It is not Vue implementation.

---

## 1. Product

### Who it is for

Primary user: a **computer-science student or junior Java backend candidate** preparing for internship, campus recruitment, or junior backend interviews.

Secondary user: a **project administrator** (the same developer in demo / portfolio use) who maintains the question bank and uploads knowledge documents that RAG later uses as evaluation evidence.

This is not a hiring platform, not an HR ATS, and not a general chatbot.

### Problem it solves

The user needs a complete, serious practice loop:

1. Sit a structured Java backend mock interview.
2. Answer real question-bank questions, not LLM-invented main questions.
3. Receive scored, dimensioned feedback after each answer.
4. Sometimes receive one follow-up on the same main question.
5. Finish the planned set.
6. Read a written report.
7. Revisit the actual question / answer / evaluation timeline.

The product should feel like **finishing a structured technical interview and then reviewing the marked script**, not like chatting with an assistant.

### The one complete loop that matters

```text
Register / Login
→ Dashboard (“what should I do now?”)
→ Start interview (choose difficulty) or Resume existing interview
→ Answer current question
→ Read evaluation
→ Continue: Follow-up / Next main / Finish
→ Generate report
→ Read report + question review
→ Later: History → Detail / Report
→ Optional: change password
```

Admin is a **supporting** loop, not the product identity:

```text
Admin login
→ Maintain questions and scoring points
→ Upload knowledge document
→ Trigger first-time / FAILED retry processing
```

---

## 2. User Mental Model

The user should believe they are:

```text
完成一场结构化技术模拟面试
→ 获得针对性反馈
→ 回看自己的回答
→ 得到下一步复习方向
```

The user should not believe they are:

- chatting with an AI;
- configuring a large enterprise exam engine;
- looking at a SaaS analytics dashboard;
- managing a knowledge graph;
- customizing job role / company / tech stack / duration.

AI is the **evaluation and report-writing capability**.  
Java is the **examiner**: it chooses questions, scores, decides follow-up, and advances the session.

Visible metaphor: **desk + interview script**, not robot, sparkle, or terminal.

---

## 3. Core Product Principles

| Principle | Meaning for UX |
|---|---|
| 专注 | Interview Session is the highest-attention page. Question + Answer dominate. |
| 学习 | Copy explains what will happen (difficulty, possible follow-up, waiting for evaluation). It does not lecture architecture. |
| 反馈 | After submit, the user must be able to **read the evaluation before** the next question takes over the screen. |
| 复盘 | Report and Detail are reading surfaces. They reconstruct the real timeline. They do not invent charts. |
| 成长 | Growth is expressed by real completed interviews, scores, weaknesses **in the report text**, and later practice. No fake streak / ranking / KPI. |

Rules:

- Every screen answers one user question.
- Every visual element must help a task.
- If frozen backend has no API, the feature is `DEFERRED_CAPABILITY`, not a fake page.

---

## 4. Roles and Auth Reality

Frozen roles: `USER`, `ADMIN`.

| Fact | UX consequence |
|---|---|
| `POST /api/auth/register` and `POST /api/auth/login` are public | Login / Register are the only public product pages |
| Register always creates `USER` | No role picker |
| Register does **not** return a token | After register, go to Login (account may be prefilled) |
| Login returns `accessToken`, `tokenType=Bearer`, `expiresInSeconds` (config: 2 hours) | Store one access token. No refresh-token API |
| Wrong password is HTTP 400 `账号或密码错误` | Do not treat login failure as 401 |
| Disabled account login is HTTP 400 `账号已被禁用` | Distinct from wrong password |
| Duplicate register is HTTP 400 `账号或邮箱已存在` | Same public message for account or email clash |
| Protected APIs need `Authorization: Bearer <token>` | Missing / invalid / expired / password-changed token → HTTP 401 `未认证或访问令牌无效` |
| `/api/admin/**` is `ROLE_ADMIN` only | USER → HTTP 403 `权限不足，无法访问资源`. Do not log the user out |
| Password change invalidates the current JWT immediately | Success → local logout → Login with the new password |
| No logout API | Logout is client-side token discard |
| No CORS configuration in frozen backend | Phase 8 must use same-origin / Vite proxy. Not a fake browser CORS toggle in UI |

Admin may also take interviews. Admin navigation is extra tools on the same product, not a separate “enterprise console” identity.

---

## 5. Interview Domain the User Can Perceive

### What the user chooses

Only:

```text
difficulty = EASY | MEDIUM | HARD
```

Do not present:

- target role
- technology stack
- question count editor
- category picker
- duration
- company
- custom topic

Backend plan (static, not user-editable):

| Difficulty | MAIN questions | Required categories (hidden implementation) |
|---|---|---|
| EASY | 5 | JAVA_BASIC, JAVA_COLLECTION, SPRING, MYSQL, NETWORK |
| MEDIUM | 7 | JAVA_BASIC, JAVA_COLLECTION, JVM, JAVA_CONCURRENCY, SPRING, MYSQL, REDIS |
| HARD | 9 | JAVA_BASIC, JAVA_COLLECTION, JVM, JAVA_CONCURRENCY, SPRING, MYSQL, REDIS, NETWORK, PROJECT_SCENARIO |

UX may **explain** these counts as expectation-setting copy.  
UX must **not** make them look like form fields.

### One active interview

`POST /api/interviews` is create **or resume**.

If the user already has `CREATED` or `IN_PROGRESS`:

- backend returns that session;
- requested difficulty is ignored.

Create Interview UX must therefore support:

```text
Resume Existing Interview
```

Never imply the user can start a second concurrent interview.

Successful create starts the session in the same transaction. The API the frontend normally sees is `IN_PROGRESS` plus the first `WAITING_ANSWER` question. `CREATED` is a brief persistence state. If `GET /current` ever returns `CREATED` (crash window), treat it as resume, not as a second setup wizard.

`InterviewSessionStatus.CANCELLED` exists in the enum. **There is no cancel API.** Do not show “放弃面试”.

### Current interview

`GET /api/interviews/current`

- Active session → HTTP 200 + session payload.
- No active session → HTTP 200 + `data: null`. **Not 404.**

### Progress the UI can honestly show

`InterviewQuestionResponse` does **not** include `planOrder`.

Use:

- `plannedQuestionCount`
- `completedQuestionCount`
- `currentQuestion.questionType`

Display rule:

```text
currentMainNumber = completedQuestionCount + 1   (while IN_PROGRESS)
FOLLOW_UP stays on the same main number
FOLLOW_UP does not increment completedQuestionCount
```

Example: `completedQuestionCount=0`, `questionType=FOLLOW_UP`, `plannedQuestionCount=5` → “第 1 / 5 题 · 追问”.

### Answer submission

Request:

```text
interviewQuestionId
answerContent
requestId
```

`requestId` is a real idempotency key (max 64 chars).

#### Durable submitRecord (MVP, mandatory)

Before any answer POST:

```text
1. User clicks 提交
2. If this question has no submitRecord: generate requestId
3. Snapshot exact answerContent
4. Persist submitRecord to localStorage (must succeed)
5. Only then dispatch HTTP
```

```text
key   = interview.submit.{userId}.{sessionId}.{interviewQuestionId}
store = localStorage   // not optional, not sessionStorage-as-primary
```

`userId` namespaces the key on this browser. It is **never** sent to the backend.

Value at least: `userId`, `sessionId`, `interviewQuestionId`, `requestId`, `answerContent`, `createdAt`, `updatedAt`.

| Event | Required frontend behavior |
|---|---|
| First submit click | Persist durable record, then POST |
| HTTP already dispatched (network / timeout / reset / unknown / 502 / 409 EVALUATING) | Reuse **same** `requestId` and **exact** `answerContent`. Lock the snapshot. Do not mint a new id |
| Client validation blocked the click, or localStorage persist failed before dispatch, or user edited **before** Submit | Edit allowed. No dispatched id. New id only on a later Submit |
| Same `requestId`, different question | HTTP 409 `requestId已用于其他问题` |
| Same `requestId`, different `answerContent` | HTTP 409 `requestId已用于不同的答案内容` |
| Question already answered with another `requestId` | HTTP 409 `当前问题已经使用其他requestId提交过答案` |
| Fresh `EVALUATING` | HTTP 409 `答案正在评估中，请稍后重试` — wait / poll current **question**; retry same pair |
| 200 with evaluation | Evaluation gate. Do not auto-advance |
| Answer UTF-8 bytes > 65535 | HTTP 400 — block **before** persist/dispatch |

Do **not** offer “我确认尚未提交，使用新内容”. The user cannot know whether the server persisted.

#### Lost record — `RECOVERY LIMITATION`

`GET /current` does not expose answer `requestId` or `InterviewAnswerStatus`. If localStorage submitRecord is gone and current still shows the same question, the frontend **cannot** distinguish never-persisted vs SUBMITTED vs EVALUATING vs FAILED. That is a **recovery limitation**, not a Backend Bug.

If a new `requestId` then hits `当前问题已经使用其他requestId提交过答案` and the original record is lost:

```text
当前答案已在服务端登记，但本机恢复信息已丢失。
请不要继续创建新的提交。
```

State: **`RECOVERY_BLOCKED`**. Do not mint another id.

`InterviewService.submitAnswer()` waits for evaluation on the happy path. The HTTP call is **long**. The UI must look like “evaluating”, not like a dead button.

Workflow / DeepSeek / Milvus beans may be absent: HTTP 400 `面试评价服务当前不可用`. Show a service-unavailable state, keep the **undispatched** draft if HTTP never left; if it did, keep the durable pair.

### Evaluation returned — do not auto-advance the page

Submit response contains:

```text
evaluation
nextAction          FOLLOW_UP | NEXT_MAIN | FINISH
sessionStatus
nextQuestion        present unless FINISH / COMPLETED
```

**Hard UX rule:** the presence of `nextQuestion` must not instantly replace the current question.

Required sequence:

1. Keep the answered question and the submitted answer visible.
2. Present evaluation as the new reading focus.
3. Only after an explicit primary action, move on:

| `nextAction` | Primary action | Destination |
|---|---|---|
| FOLLOW_UP | 继续追问 | Same session, follow-up question |
| NEXT_MAIN | 下一题 | Same session, next MAIN |
| FINISH | 完成本场并查看报告 | Session complete → report generation / report page |

The user must be able to finish reading strengths, missing points, correction, and the five dimension scores.

Evaluation the API actually returns:

- `evaluationPhase`: `INITIAL` (main that will follow up) or `FINAL`
- five integer dimension scores: correctness / completeness / depth / clarity / practice
- `totalScore` integer 0–100
- `level`: `EXCELLENT` (90–100), `GOOD` (80–89), `FAIR` (60–79), `WEAK` (0–59)
- `strengths[]`, `missingPoints[]`, `correction`

The UI must **not** show: raw LLM JSON, RAG hits, `retrievalBatchId`, scoring-point internals, `followUpRecommended`, `decisionAction` duplicated as a second truth (use `nextAction`).

Follow-up is a backend decision. User-facing copy:

> 部分题目在主回答之后可能会有 **一次** 追问。是否追问由系统根据评分结果决定，不由聊天决定。

Do not expose the exact CORE-uncovered / score&lt;60 / LLM-recommendation rule as a control.

### History and detail

History (`GET /api/interviews/history`):

- only `COMPLETED`;
- default `page=1`, `pageSize=10`, max `pageSize=50`;
- sort is backend `ended_at DESC, id DESC`;
- active interview is **not** in this list.

Detail (`GET /api/interviews/{sessionId}`):

- owner-only; missing or other user’s session → HTTP 404 `面试会话不存在` (no existence leak);
- real timeline: MAIN and FOLLOW_UP, `display_order`;
- each question may have nested `answer` and `evaluation`, or `null`;
- MAIN shows its own (usually INITIAL) evaluation when present;
- FOLLOW_UP shows its own FINAL evaluation;
- do not overlay report’s FINAL-effective score onto the original MAIN row.

**Spoiler rule:** at creation time all MAIN questions are persisted. Detail of an `IN_PROGRESS` session can contain unseen `PENDING` question content. Frontend must **not** render unseen question bodies.

- History only lists completed sessions, so the normal entry is safe.
- Direct URL to an in-progress session id → redirect to the active Interview Session page.
- Do not offer “查看详情” from the live session.

### Report

The report UI is for **COMPLETED** sessions only. Confirm via `GET /api/interviews/{sessionId}` **before** treating GET report as a generate surface.

`GET /report` is a side-effect-free status query. An `IN_PROGRESS` session can still return `reportStatus=NOT_STARTED`. That must **not** be shown as 生成报告.

`POST` generates / retries / returns READY idempotently, and only after COMPLETED is known. Do not discover incompleteness by POSTing into 409.

The following machine applies **after** `session.status = COMPLETED`:

| `reportStatus` | GET body | POST behavior | UX |
|---|---|---|---|
| NOT_STARTED | `sessionId` + status; score/text fields `null` | First generation | Primary: 生成报告 |
| GENERATING | status only | Fresh → 409 `报告正在生成中，请稍后重试`; stale reclaim is backend | Poll GET; do not spam POST |
| READY | full report | Return existing, no second LLM, no second weakness write | Show report |
| FAILED | status only | Retry allowed | Primary: 重新生成 |

After uncertain POST failure: GET report first (READY stop / GENERATING poll / FAILED or NOT_STARTED → POST).

Report content (READY only):

- `overallScore` — Java mean of effective MAIN `totalScore`, 2 decimal HALF_UP
- `result` — Java `EvaluationLevel.name()`: `EXCELLENT` / `GOOD` / `FAIR` / `WEAK`
- `summary`, `strengths[]`, `weaknesses[]`, `suggestions[]`

LLM does not recalculate the score or the result label.

Generator missing: 400 `报告生成服务当前不可用`.  
DeepSeek / external failure: 502 `外部服务暂时不可用`.

Reading order for the report page:

```text
Outcome (score + result)
→ Meaning (summary)
→ Strength
→ Gap
→ Action (suggestions)
→ Evidence (Detail timeline: question / answer / evaluation)
```

The report API does **not** include per-question data. The evidence section is loaded from Detail of the **same completed** session. That is composition of two real APIs, not fake data.

Do not build a radar chart from invented report dimensions. Optional later: a small five-dimension summary **derived only from Detail FINAL evaluations**. MVP default is typographic, not a dashboard chart.

User weakness rows are updated **inside** successful report persistence. There is **no** GET weakness API. Do not create a Weakness page. Report `weaknesses` text is the user-visible growth signal.

---

## 6. Core Flows

### 6.1 Register / Login

Supported.

Register fields: `account`, `username`, `password`, `email`.  
Login fields: `account`, `password`.

Password 8–72 characters. Account / username max 50. Email max 100.

No forgot-password flow. Login page must not advertise it.

### 6.2 Dashboard

Supported.

Primary user question: **“我现在应该做什么？”**

Priority:

1. If `GET /current` has a session → **继续面试**
2. Else → **开始面试**
3. Then: recent completed interviews from History `page=1, pageSize=5`
4. If a completed item has `reportStatus != READY` → prompt to generate / open report
5. Profile / password is secondary

Forbidden on Dashboard: fake KPI, streak, ranking, charts, AI score trend, activity metrics, weakness aggregates.

### 6.3 Create Interview

Supported, with resume.

User-visible configuration: difficulty only.

The three levels are **peers**. Copy may explain MAIN count (5 / 7 / 9) and coverage. Do **not** recommend MEDIUM (or any level) as “校招标准” or a default choice. The user must choose.

Copy should also set expectation:

- how many MAIN questions;
- that a follow-up may appear at most once per MAIN;
- that evaluation takes time;
- that an unfinished interview will be resumed instead of duplicated.

If an active session exists, this page becomes a resume gate, not a difficulty form that pretends to create another exam.

### 6.4 Interview Session

Supported. Highest-focus page.

Visual subject: Question + Answer.  
Auxiliary, visually quiet: progress, category, knowledgePoint, MAIN / FOLLOW_UP.

States the page must design:

- composing answer;
- submitting / evaluating (long request);
- evaluation reading (answered question stays);
- continue CTA based on `nextAction`;
- 409 evaluating (same requestId + exact content);
- 502 / network / timeout after dispatch (same pair, locked snapshot; never “new content”);
- `RECOVERY_BLOCKED` if submitRecord is lost and the server already has another requestId;
- 400 service unavailable;
- refresh recovery from `GET /current` **question identity** + **localStorage** submitRecord (current does not expose AnswerStatus).

No cancel. No sidebar of history. No live stats.

### 6.5 Report

Supported **only** as a completed-session surface.

The report route must load `GET /api/interviews/{sessionId}` first.

- `COMPLETED` → then GET report and follow NOT_STARTED / GENERATING / READY / FAILED.
- `CREATED` / `IN_PROGRESS` → go to the live session. GET report may still return `NOT_STARTED`; that is **not** permission to generate.
- Do not POST and wait for 409 `面试尚未完成，无法生成报告` to discover the session is still running.

GET for status / reading after COMPLETED is known. POST for generate and FAILED retry, with GET-report-first after uncertain POST failure (see STATE_MODEL).

### 6.6 History → Interview Detail

Supported.

History = completed list.  
Detail = timeline.  
Report = synthesis.  
Keep them distinct. Detail is not a second report. Report is not a reconstructed timeline.

### 6.7 Profile / Password

Supported: `GET /api/users/me`, `PUT /api/users/me/password`.

After password change, force login. Do not keep the old token.

No email-change API. Show email as read-only.

### 6.8 Admin Question Management

Supported:

- create
- whole replace of question + scoring points
- paginated list (`pageNum`, `pageSize`, optional category / difficulty / status / keyword)
- detail
- status `ENABLED` / `DISABLED`

Scoring points: at least one; each weight 1–100; **sum must be 100**; types `CORE|KEY|PRINCIPLE|INTERNAL|ADVANCED`.

List item field is `questionContent`. Detail field is `content`. Do not mix the names.

Historical `/disable` and `/enable` paths do not exist. Use `PUT /{id}/status`.

### 6.9 Admin Knowledge Upload / Process

Partially supported.

Exists:

- `POST /api/admin/knowledge/documents` multipart (`file`, `title`, `category`, optional `source`)
- `POST /api/admin/knowledge/documents/{documentId}/process`

Does **not** exist: list, detail GET, enable/disable, named reprocess, READY rebuild.

Upload only persists `UPLOADED`. Process is a second explicit action.

Process occupies `UPLOADED`, `FAILED`, or stale `PROCESSING`.  
READY and fresh PROCESSING → 409.

Because there is no list API, the admin UI must treat the upload response `documentId` as the handle. Persist a **local recent-upload list** (browser storage) labeled as this browser’s recent work, not as a server catalog.

Max file 5 MiB; UTF-8 Markdown / TXT only; 413 if oversize.

---

## 7. Historical Design vs Frozen Backend

Classification: `HISTORICAL_DESIGN_DIVERGENCE` / `D-EVOLUTION` or `D-DOC`.  
These are **not** backend bugs and **must not** enter MVP navigation.

| Historical v1.1 / early plan | Frozen backend | Frontend |
|---|---|---|
| Forgot password + Redis + mail | No API | No link, no page |
| `GET /api/users/me/weaknesses` | Weakness written only inside report transaction | No Weakness page. Report text only |
| Weakness-weighted question selection | Deterministic category-then-stable-order selection | Do not say “根据你的薄弱点出题” |
| `POST .../cancel` | Enum `CANCELLED` only | No cancel control |
| `POST .../finish` for report retry | Last answer `FINISH` completes session; report is `POST .../report` | No finish button during answering except after last evaluation |
| Admin can read all interviews | Owner check is strict; other user’s id → 404 | No global interview viewer |
| `GET/PUT /api/admin/users` | No controller | No admin user management |
| Knowledge list / detail / enable / disable / reprocess | Upload + process only; FAILED retry via process | Local recent-upload UX only |
| Upload auto-runs chunk/embed/Milvus | Upload ≠ process | Two-step admin copy |
| Knife4j, Redis, Spring Mail, LangChain4j | Not in runtime product | Do not surface |
| MEDIUM planned count 6 in an old example | MEDIUM = 7 | Use frozen counts |
| Template follow-up if LLM text empty | Follow-up requires non-blank `suggestedFollowUp` | If evaluation fails, show error, do not invent a question |
| Admin LLM test endpoints | None | None |
| `InterviewQuestionStatus.SKIPPED` | Enum unused by workflow | Do not show Skip |

---

## 8. DEFERRED_CAPABILITY

| Capability | Product value | Missing frozen API | MVP without it? |
|---|---|---|---|
| User weakness page | Long-term knowledge-point portrait | No GET weakness | Yes. Report weaknesses + later interviews |
| Cancel interview | Abandon a stuck session | No cancel API | Yes. User has one active session; they can finish it |
| Forgot password | Account recovery | No reset API | Yes. Demo / portfolio accounts |
| Refresh token | Stay signed in beyond 2h | No refresh API | Yes. Re-login on 401 |
| Dashboard aggregates | Trends | No aggregate API | Yes. Honest empty / recent history |
| Admin user management | Disable users | No admin user API | Yes |
| Admin global interview list | Monitor all sessions | No admin interview API | Yes |
| Knowledge document catalog | Find old uploads | No list/detail/disable | Yes, with local recent-upload caveat |
| READY document reprocessing | Rebuild vectors | Explicitly not implemented | Yes |

Design preference must not trigger backend-unfreeze.

---

## 9. Error Language (product-level)

Do not collapse all failures into one toast.

| HTTP | Typical cause | User-facing stance |
|---|---|---|
| 400 | Validation, business input, duplicate register/login, workflow bean missing | Inline on the form or the action. Use backend `message` |
| 401 | Missing / bad / expired / password-changed token | Clear session, go to Login, restore intended path after success |
| 403 | Authenticated USER hits admin | Stay logged in. Forbidden page |
| 404 | Missing question/session/document, or non-disclosing ownership | “不存在或无权查看”. Do not confirm other users’ ids |
| 405 | Wrong method | Treat as client/route defect; generic not-found / retry |
| 409 | Idempotency, evaluating, generating, knowledge processing, not completed | Explain the **state**. Answer: same pair only. Report 409-not-completed should not be the first guard (session GET already required) |
| 413 | Knowledge file too large | Stay on upload form |
| 500 | Unexpected server defect | GET may retry. POST/PUT: **no generic retry** — FRONTEND_API_MATRIX only |
| 502 | Sanitized external AI / vector failure | “外部服务暂时不可用”. Answer: keep durable pair. Report: GET report first. Process: same documentId |
| Network | Browser / proxy failure | Keep local state. Answer: same requestId + exact content. Register / create question / upload: do not auto-retry POST |

Login 400 is not 401. 403 is not logout.

---

## 10. Navigation Intent

MVP information architecture is a **practice product** with a small admin annex.

User:

```text
工作台
开始 / 继续面试
历史
（面试进行中：独立专注页）
报告
账号
```

Admin extra:

```text
题库
知识文档
```

Not in MVP nav:

```text
薄弱知识点
忘记密码
取消面试
用户管理
全站面试监控
知识文档库浏览
```

---

## 11. Success Criteria for Later Implementation

A later Vue implementation is faithful if:

- a new user can register, log in, start one difficulty, finish a session, generate a report, and reopen it from history;
- an existing active session is resumed rather than duplicated;
- evaluation is readable before the next question;
- refresh during submit does not mint a new `requestId` (durable localStorage record written **before** HTTP);
- Dashboard contains only real `me` / `current` / `history` data;
- no page depends on an unfrozen API.
