# ROUTE_MAP

Status: Design Gate 1 candidate  
This is a routing **contract**, not Vue Router code.

Auth source: one Bearer access token + `GET /api/users/me` (or login payload) for `role`.  
Server remains the authority for role and user status.

---

## 1. Route table

| route | auth | role | page | data dependency | entry source | redirect rule | refresh recovery |
|---|---|---|---|---|---|---|---|
| `/login` | anonymous | — | P-LOGIN | none | marketing, 401, logout, register success | If already authenticated → `/` | Stay. Form local |
| `/register` | anonymous | — | P-REGISTER | none | login link | If already authenticated → `/` | Stay |
| `/` | authenticated | USER or ADMIN | U-DASHBOARD | `GET /users/me`, `GET /interviews/current`, `GET /interviews/history?page=1&pageSize=5` | login, nav, finish | Unauthenticated → `/login?redirect=/` | Re-fetch all three |
| `/interviews/new` | authenticated | USER or ADMIN | U-CREATE | `GET /interviews/current`; `POST /interviews` | dashboard | Unauthenticated → login. If current exists, page is resume gate (still this route) | Re-fetch current. Do not auto-POST |
| `/interview` | authenticated | USER or ADMIN | U-SESSION | `GET /interviews/current`; `POST /interviews/{id}/answers` | dashboard, create, resume | No active session → `/`. Unauthenticated → login | GET current. Restore durable `localStorage` submitRecord for **this user + session + current question** only |
| `/interviews` | authenticated | USER or ADMIN | U-HISTORY | `GET /interviews/history` | nav, dashboard | Unauthenticated → login | Keep query `page`/`pageSize`. Re-fetch |
| `/interviews/:sessionId` | authenticated | owner only (enforced by API) | U-DETAIL | `GET /interviews/{sessionId}` | history | 401 login. 404 not-found page. If session is CREATED/IN_PROGRESS → `/interview` | Re-fetch detail. If now COMPLETED, stay |
| `/interviews/:sessionId/report` | authenticated | owner only | U-REPORT | **`GET /api/interviews/{sessionId}` first** (session status). If COMPLETED: `GET .../report`; POST generate only then; Detail already loaded may supply evidence | history, detail, session FINISH | 401 → login. 404 → not-found. `CREATED` / `IN_PROGRESS` → `/interview`. Never treat GET report `NOT_STARTED` as generate permission without COMPLETED | GET session first. If COMPLETED and GENERATING, poll GET report. Do not auto-POST on refresh |
| `/profile` | authenticated | USER or ADMIN | U-PROFILE | `GET /users/me`; `PUT /users/me/password` | nav | Unauthenticated → login | Re-fetch me |
| `/admin/questions` | authenticated | ADMIN | A-QUESTION-LIST | `GET /api/admin/questions` | admin nav | No token → login. USER → `/forbidden` | Keep list query string |
| `/admin/questions/new` | authenticated | ADMIN | A-QUESTION-CREATE | `POST /api/admin/questions` | list | USER → forbidden | Local form |
| `/admin/questions/:questionId` | authenticated | ADMIN | A-QUESTION-DETAIL | `GET /api/admin/questions/{id}` | list | USER → forbidden. 404 not-found | Re-fetch |
| `/admin/questions/:questionId/edit` | authenticated | ADMIN | A-QUESTION-EDIT | GET detail + `PUT /{id}` | detail | USER → forbidden | Re-fetch then allow edit |
| `/admin/knowledge` | authenticated | ADMIN | A-KNOWLEDGE | upload + process; local recent list | admin nav | USER → forbidden | Restore local recent-upload list. Do not assume server list |
| `/forbidden` | authenticated | any | P-FORBIDDEN | none | 403 admin routes | Anonymous → login | Stay logged in |
| `*` | any | — | P-NOT-FOUND | none | mistype | — | Stay |

Query conventions:

- History: `?page=1&pageSize=10` (pageSize max 50).
- Admin questions: `?pageNum=1&pageSize=10` plus optional `category`, `difficulty`, `status`, `keyword`.
- Login: `?redirect=` only for in-app paths. Ignore absolute URLs / other origins.

There is no `/weaknesses`, `/forgot-password`, `/interviews/:id/cancel`, `/admin/users`, `/admin/interviews`.

---

## 2. Actor matrix

### Unauthenticated

Allowed: `/login`, `/register`.

Any other in-app route: redirect `/login?redirect=<safe-path>`.

API 401 on a page already opened: same as token invalid (section 3).

### Authenticated USER

Allowed: all AUTHENTICATED USER routes.

Admin routes: client guard to `/forbidden`. If the guard is bypassed, API returns 403; still `/forbidden`, **not** logout.

### Authenticated ADMIN

Allowed: user routes + admin routes.

Landing after login is `/` (workbench), not the admin list. Admin is tools, not the product home.

### 401

Meaning: no valid authentication (missing, malformed, expired, disabled user, credential_version mismatch after password change).

Frontend:

1. Discard access token and in-memory auth.
2. Do not flash protected content.
3. Redirect `/login?redirect=<safe-path>` (except if already on login/register).
4. After successful login, go to redirect if it is an in-app path; else `/`.

Do not attempt token refresh. There is no API.

### 403

Meaning: authenticated, insufficient role.

Stay authenticated. Show `/forbidden`.

### Unknown route

Client `*` → P-NOT-FOUND.  
Backend unknown path → HTTP 404 `请求的资源不存在`. Do not treat as 401.

### Active interview

`GET /api/interviews/current` returns a session:

- Dashboard primary CTA → `/interview`
- `/interviews/new` shows resume, does not pretend to create a second exam
- `/interview` is the workspace
- `/interviews/:sessionId` for that active id → `/interview` (no spoiler)

### No active interview

`GET /current` → HTTP 200 `data: null`:

- Dashboard primary CTA → `/interviews/new`
- `/interview` → `/`
- History is independent

---

## 3. Guards (logical)

```text
anonymousOnly: login, register
requireAuth: everything else in the product
requireAdmin: /admin/**
activeInterviewRedirect: /interviews/:id when status is CREATED or IN_PROGRESS → /interview
completedSessionGuard: /interviews/:id/report
  GET /api/interviews/{sessionId} first
  COMPLETED → load report
  CREATED / IN_PROGRESS → /interview
  404 → not-found
  401 → login
```

Do not implement report generation UX by POSTing and catching 409 `面试尚未完成，无法生成报告`.

Ownership is **not** a client role. The API 404s other users’ sessions. Client does not fetch a userId from the URL.

---

## 4. Entry / exit of the interview workspace

Enter `/interview` from:

- dashboard continue;
- create success;
- resume;
- in-progress detail URL.

Leave `/interview` only when:

- `GET /current` is null (completed or never started) → dashboard or report of the just-finished session;
- user explicitly navigates to history/profile (discouraged copy, not a hard lock);
- 401.

After `nextAction=FINISH`, prefer:

```text
/interviews/:sessionId/report
```

not a silent drop on dashboard.

---

## 5. Query and path IDs

`sessionId` and `questionId` are positive longs. Non-numeric or ≤0: client 404 or backend 400 `sessionId必须大于0`.

Do not put `userId` in routes.  
Do not put `requestId` in routes; it is durable localStorage submit state (client namespace may include `userId` in the **key**, never in the URL or request body).
