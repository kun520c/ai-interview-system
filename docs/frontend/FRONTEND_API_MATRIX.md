# FRONTEND_API_MATRIX

Status: Design Gate 1 hard artifact  
Rebuilt from frozen backend `20a933ae8092ef908c2f03193c0b0f9547da71c1`  
Envelope for every HTTP JSON API:

```json
{ "code": 200, "message": "success", "data": {} }
```

Errors use the same envelope with `data: null`.  
`code` matches HTTP status for errors. Success `code` is 200.

Auth header for all non-public endpoints:

```text
Authorization: Bearer <accessToken>
```

Do not send `userId` in bodies or query strings. Identity comes from JWT → database user.

---

## Legend

- **Auth**: Public | Bearer
- **Role**: any authenticated | ADMIN
- **Idempotency**: what the frontend must reuse or not

---

## A. Authentication

### A1. Register

| Field | Value |
|---|---|
| Page / Feature | P-REGISTER |
| User Action | 提交注册 |
| Method | POST |
| Endpoint | `/api/auth/register` |
| Request | `RegisterRequest`: `account` ≤50, `username` ≤50, `password` 8–72, `email` ≤100 `@Email` |
| Response | `Result<Void>` (`data` null) |
| Auth | Public. Filter skipped for this POST even if an old Bearer is present |
| Role | — |
| Possible Status | 200; 400 validation / `账号或邮箱已存在`; 405; 500 |
| Frontend Source State | Local form |
| Derived State | — |
| Loading UX | Disable submit |
| Success UX | Go to Login; prefills account; no auto-login |
| Error UX | Inline backend `message` |
| Recovery | User may click 注册 again. **Do not auto-retry POST.** Duplicate → 400 |
| Idempotency | Not idempotent. Repeat may 400 duplicate |
| Notes | Always creates `USER`. No role field |

### A2. Login

| Field | Value |
|---|---|
| Page / Feature | P-LOGIN |
| User Action | 登录 |
| Method | POST |
| Endpoint | `/api/auth/login` |
| Request | `LoginRequest`: `account`, `password` 8–72 |
| Response | `LoginResponse`: `userId`, `account`, `username`, `role`, `accessToken`, `tokenType`=`Bearer`, `expiresInSeconds` (config 2h) |
| Auth | Public |
| Role | — |
| Possible Status | 200; 400 `账号或密码错误` / `账号已被禁用` / validation; 500 |
| Frontend Source State | Local form → Auth State |
| Derived State | `expiresAt` |
| Loading UX | Disable submit |
| Success UX | Store token; `GET /users/me` optional; go `redirect` or `/` |
| Error UX | Inline. **Not 401** |
| Recovery | Retry credentials |
| Idempotency | Repeat login issues a new token |
| Notes | No refresh-token field |

### A3. Refresh token

| Field | Value |
|---|---|
| Page / Feature | Auth |
| User Action | Stay signed in after 2h |
| Endpoint | **NO FROZEN API** |
| Notes | 401 → Login |

### A4. Logout / Forgot password

| Field | Value |
|---|---|
| Logout | Client discards token. **NO FROZEN API** |
| Forgot password | **NO FROZEN API**. Do not show |

---

## B. Current user

### B1. Me

| Field | Value |
|---|---|
| Page / Feature | U-DASHBOARD, U-PROFILE, chrome |
| User Action | Load identity |
| Method | GET |
| Endpoint | `/api/users/me` |
| Request | — |
| Response | `CurrentUserResponse`: `userId`, `account`, `username`, `email`, `role`, `status`, `createdAt` |
| Auth | Bearer |
| Role | any authenticated |
| Possible Status | 200; 401; 400 `用户不存在` / `账号已被封禁` (service-level; disabled users usually fail the filter as 401 first); 500 |
| Frontend Source State | Server State `me` |
| Derived State | `isAdmin` |
| Loading UX | Header initials skeleton |
| Success UX | Greeting; admin nav if ADMIN |
| Error UX | 401 login |
| Recovery | Re-login |
| Idempotency | Read |

### B2. Change password

| Field | Value |
|---|---|
| Page / Feature | U-PROFILE |
| User Action | 修改密码 |
| Method | PUT |
| Endpoint | `/api/users/me/password` |
| Request | `ChangePasswordRequest`: `currentPassword`, `newPassword` 8–72 |
| Response | `Result<Void>` |
| Auth | Bearer |
| Role | any authenticated |
| Possible Status | 200; 400 `请输入正确的现存密码` / `新密码不能与原密码相同` / validation / `密码修改失败`; 401; 500 |
| Frontend Source State | Local form |
| Derived State | Token becomes invalid immediately |
| Loading UX | Disable submit |
| Success UX | Forced login with new password |
| Error UX | Inline |
| Recovery | Correct current password. **Do not auto-retry PUT** |
| Idempotency | Concurrent two changes: one wins (row lock) |
| Notes | No email/username update API |

### B3. Weaknesses

| Field | Value |
|---|---|
| Page / Feature | Historical weakness page |
| Endpoint | **NO FROZEN API** (`GET /api/users/me/weaknesses` was design-only) |
| Notes | Server updates `user_weakness` during report READY transaction. User sees report `weaknesses[]` only |

---

## C. Interview session lifecycle

### C1. Create or resume

| Field | Value |
|---|---|
| Page / Feature | U-CREATE, U-DASHBOARD |
| User Action | 开始面试 / 系统恢复未完成场 |
| Method | POST |
| Endpoint | `/api/interviews` |
| Request | `CreateInterviewRequest`: `{ "difficulty": "EASY\|MEDIUM\|HARD" }` only |
| Response | `InterviewSessionResponse`: `sessionId`, `difficulty`, `status`, `plannedQuestionCount`, `completedQuestionCount`, `currentQuestion` |
| `currentQuestion` | `InterviewQuestionResponse`: `interviewQuestionId`, `category`, `knowledgePoint`, `questionType`, `content` |
| Auth | Bearer |
| Role | any authenticated |
| Possible Status | 200; 400 validation; 401; 500 (e.g. insufficient ENABLED questions for the plan — internal) |
| Frontend Source State | difficulty form; then Server State current |
| Derived State | `hasActiveInterview`; if returned `difficulty` ≠ requested → resumed |
| Loading UX | “正在准备本场题目” |
| Success UX | `/interview`. If resume, banner “已为你继续未完成的面试” |
| Error UX | Inline/banner |
| Recovery | After uncertain POST: **GET current first**. If a session exists, go `/interview`. If `data=null`, user may click 开始 again. **Do not auto-retry POST.** Create is resume-idempotent only when an active session already exists |
| Idempotency | Second POST returns the existing CREATED/IN_PROGRESS session; does not create another |
| Notes | Counts: EASY 5, MEDIUM 7, HARD 9 as explanation only. Peer choices. No recommended default. No target role / stack / duration |

### C2. Get current

| Field | Value |
|---|---|
| Page / Feature | U-DASHBOARD, U-SESSION, U-CREATE, refresh |
| User Action | 我现在有没有进行中的面试？ |
| Method | GET |
| Endpoint | `/api/interviews/current` |
| Request | — |
| Response | `InterviewSessionResponse` **or `data: null`** |
| Auth | Bearer |
| Role | any authenticated |
| Possible Status | **200 even when null**; 401; 500 |
| Frontend Source State | Server State current |
| Derived State | `noActiveInterview` vs `activeInterview` |
| Loading UX | Next-action skeleton |
| Success UX | null → start CTA; payload → continue CTA |
| Error UX | Region error. null is not error |
| Recovery | Retry GET |
| Idempotency | Read |
| Notes | Not 404 when empty |

### C3. Cancel interview

| Field | Value |
|---|---|
| Endpoint | **NO FROZEN API** |
| Notes | Enum `CANCELLED` is not user-reachable. No abandon button |

### C4. Finish interview (dedicated)

| Field | Value |
|---|---|
| Endpoint | **NO FROZEN API** (`POST .../finish` was design-only) |
| Notes | Completion happens when the last answer evaluation returns `nextAction=FINISH` |

---

## D. Answer submission

### D1. Submit answer / replay / retry evaluation

| Field | Value |
|---|---|
| Page / Feature | U-SESSION |
| User Action | 提交答案; HTTP retry; FAILED retry; EVALUATED replay |
| Method | POST |
| Endpoint | `/api/interviews/{sessionId}/answers` |
| Request | `SubmitInterviewAnswerRequest`: `interviewQuestionId` >0, `answerContent` non-blank UTF-8 ≤65535 bytes, `requestId` non-blank ≤64 |
| Response | `SubmitInterviewAnswerResponse`: `evaluation` (`InterviewEvaluationResponse`), `nextAction` (`FOLLOW_UP\|NEXT_MAIN\|FINISH`), `sessionStatus`, `nextQuestion` (null if completed) |
| Evaluation fields | `evaluationPhase` INITIAL\|FINAL; scores correctness/completeness/depth/clarity/practice; `totalScore`; `level` EXCELLENT\|GOOD\|FAIR\|WEAK; `strengths[]`; `missingPoints[]`; `correction` |
| Auth | Bearer |
| Role | owner (else 404) |
| Possible Status | 200; 400 validation / `面试评价服务当前不可用`; 401; 404 `面试会话不存在` / `面试问题不存在`; 409 conflicts (below); 502 `外部服务暂时不可用`; 500 |
| 409 messages | `当前问题已经使用其他requestId提交过答案`; `当前问题不可提交答案`; `当前InterviewSession不可提交答案`; `提交的问题不是当前问题`; `requestId已用于其他问题`; `requestId已用于不同的答案内容`; `答案正在评估中，请稍后重试` |
| Frontend Source State | `localStorage` submitRecord **before** POST; then evaluationGate on 200 |
| Derived State | `canSubmit`; locked snapshot after dispatch; `RECOVERY_BLOCKED` if lost record + other requestId |
| Loading UX | Long wait. Button “正在评估”. Do not navigate |
| Success UX | Show evaluation **on the answered question**. Do not auto-load nextQuestion into the workspace |
| Error UX | 409 evaluating: poll current **question**; reuse pair. 502/network/timeout after dispatch: reuse pair, lock snapshot. Other requestId + missing local record: `RECOVERY_BLOCKED` copy. Never mint a new id after dispatch |
| Recovery | Durable persist **before** HTTP. See STATE_MODEL §4.4. `GET /current` does **not** expose AnswerStatus/`requestId` — do not invent a current-based status detector |
| Idempotency | Same `requestId` + same question + **exact** `answerContent`. Retry allowed **only** with that pair. EVALUATED replay: no second LLM. EVALUATING fresh: 409. Stale EVALUATING: backend reclaim |
| Notes | Unique `interview_question_id` and `request_id`. DuplicateKey recovered server-side. No global Axios mutation retry |

---

## E. History, detail, report

### E1. History

| Field | Value |
|---|---|
| Page / Feature | U-HISTORY, U-DASHBOARD recent |
| User Action | 浏览已完成面试 |
| Method | GET |
| Endpoint | `/api/interviews/history` |
| Request | query `page` default 1; `pageSize` default 10, max 50 |
| Response | `InterviewHistoryPageResponse`: `page`, `pageSize`, `total`, `totalPages`, `items[]` |
| Item | `sessionId`, `difficulty`, `status` (COMPLETED), `reportStatus`, `totalScore` (nullable until report READY), `plannedQuestionCount`, `completedQuestionCount`, `startedAt`, `endedAt`, `createdAt` |
| Auth | Bearer |
| Role | own rows only |
| Possible Status | 200 (empty list ok); 400 `页码必须大于等于1` / `每页数量必须在1到50之间`; 401; 500 |
| Frontend Source State | Server history page |
| Derived State | `totalPages`; pending-report flag |
| Loading UX | List skeleton |
| Success UX | Rows. Empty: CTA start |
| Error UX | Inline |
| Recovery | Fix page params; retry |
| Idempotency | Read |
| Notes | Active sessions excluded |

### E2. Detail

| Field | Value |
|---|---|
| Page / Feature | U-DETAIL, U-REPORT evidence |
| User Action | 查看时间线 |
| Method | GET |
| Endpoint | `/api/interviews/{sessionId}` |
| Request | path `sessionId` >0 |
| Response | `InterviewDetailResponse`: session meta + `questions[]` |
| Question | `interviewQuestionId`, `questionType`, `category`, `knowledgePoint`, `questionContent`, `planOrder` (null on FOLLOW_UP), `displayOrder`, `status`, `answer` \| null, `evaluation` \| null |
| Answer | `answerContent`, `answerStatus`, `submittedAt` — **no requestId** |
| Auth | Bearer |
| Role | owner; other user → 404 `面试会话不存在` |
| Possible Status | 200; 400 `sessionId必须大于0`; 401; 404; 500 |
| Frontend Source State | Server detail |
| Derived State | Ordered timeline |
| Loading UX | Timeline skeleton |
| Success UX | COMPLETED: full timeline. IN_PROGRESS: **do not render** — redirect `/interview` |
| Error UX | 404 not found / no access |
| Recovery | Back to history |
| Idempotency | Read |
| Notes | Does not expose snapshots, requestId, errorCode, rawResult, RAG ids, followUpRecommended. Report route uses this GET as the **COMPLETED** guard. Detail `answerStatus` is timeline display, not live `requestId` recovery |

### E3. Get report

| Field | Value |
|---|---|
| Page / Feature | U-REPORT (only after session COMPLETED) |
| User Action | 查看报告状态或内容 |
| Method | GET |
| Endpoint | `/api/interviews/{sessionId}/report` |
| Request | path id |
| Response | Always `InterviewReportResponse`. If not READY: `sessionId`, `reportStatus`, and `overallScore/result/summary/strengths/weaknesses/suggestions` are **null** |
| READY fields | `overallScore` BigDecimal; `result` `EXCELLENT\|GOOD\|FAIR\|WEAK`; `summary`; lists |
| Auth | Bearer |
| Role | owner; else 404 |
| Possible Status | 200; 401; 404; 500 (READY but missing row is 500 internal) |
| Frontend Source State | Server report **after** session GET proved COMPLETED |
| Derived State | `reportReady` |
| Loading UX | Skeleton |
| Success UX | Branch on `reportStatus`. `NOT_STARTED` is generate-able **only** when session is COMPLETED. Do not render null fields as empty cards |
| Error UX | 404 |
| Recovery | GET is retryable |
| Idempotency | Read; no generation side effect |
| Notes | An IN_PROGRESS session can still GET `NOT_STARTED`. Route must not treat that as 生成报告 |

### E4. Generate / retry report

| Field | Value |
|---|---|
| Page / Feature | U-REPORT |
| User Action | 生成报告 / 失败重试 |
| Method | POST |
| Endpoint | `/api/interviews/{sessionId}/report` |
| Request | empty body |
| Response | READY `InterviewReportResponse` (also used for idempotent READY replay) |
| Auth | Bearer |
| Role | owner |
| Possible Status | 200; 400 `报告生成服务当前不可用`; 401; 404; 409 `面试尚未完成，无法生成报告` / `报告正在生成中，请稍后重试` / `报告生成状态已发生变化，请重试`; 502; 500 |
| Frontend Source State | Transient generating; then server READY |
| Derived State | — |
| Loading UX | Blocking generation. If client timeout: **GET report first**, do not auto-POST |
| Success UX | Reading order Outcome → Meaning → Strength → Gap → Action; then Detail evidence |
| Error UX | 409 generating: poll GET. Uncertain failure: GET report (READY stop / GENERATING poll / FAILED or NOT_STARTED → POST). Completeness must already be known from session GET — do not use 409-not-completed as the primary guard |
| Recovery | After uncertain failure: GET report first. READY: stop. GENERATING: poll. FAILED / NOT_STARTED (session COMPLETED): POST |
| Idempotency | READY POST returns existing, **does not** recall LLM or increment weakness counts |
| Notes | Weakness updates only in the READY transaction. No global mutation retry |

---

## F. Admin questions

All `/api/admin/**` : Bearer + ADMIN. USER → 403. Anonymous → 401.

### F1. Page

| Field | Value |
|---|---|
| Page / Feature | A-QUESTION-LIST |
| User Action | 筛选分页 |
| Method | GET |
| Endpoint | `/api/admin/questions` |
| Request | `QuestionPageQuery` query: `pageNum` default 1, `pageSize` default 10 max 50, optional `category`, `difficulty`, `status`, `keyword` ≤100 |
| Response | `AdminQuestionPageResponse`: `records`, `total`, `pageNum`, `pageSize`, `totalPages` |
| List item | `id`, `category`, `knowledgePoint`, `difficulty`, `questionContent`, `status`, `updatedAt` |
| Possible Status | 200; 400 page bounds; 401; 403; 500 |
| Loading / empty / error | Table skeleton; empty filter; standard |
| Idempotency | Read |

Enums: `QuestionCategory` JAVA_BASIC, JAVA_COLLECTION, JVM, JAVA_CONCURRENCY, SPRING, MYSQL, REDIS, NETWORK, PROJECT_SCENARIO. `QuestionDifficulty` EASY MEDIUM HARD. `QuestionStatus` ENABLED DISABLED.

### F2. Detail

| Field | Value |
|---|---|
| Method | GET |
| Endpoint | `/api/admin/questions/{questionId}` |
| Response | `AdminQuestionDetailResponse`: `id`, `category`, `knowledgePoint`, `difficulty`, **`content`** (not questionContent), `referenceAnswer`, `status`, timestamps, `scoringPoints[]` (`pointType`, `content`, `weight`) |
| Possible Status | 200; 400 bad id; 401; 403; 404 `题目不存在` |

### F3. Create

| Field | Value |
|---|---|
| Method | POST |
| Endpoint | `/api/admin/questions` |
| Request | `CreateQuestionRequest`: category, knowledgePoint ≤100, difficulty, questionContent TEXT, referenceAnswer TEXT, `scoringPoints` non-empty |
| Scoring point | `pointType` CORE\|KEY\|PRINCIPLE\|INTERNAL\|ADVANCED; content TEXT; weight 1–100; **sum 100** |
| Response | `CreateQuestionResponse`: `{ questionId }` |
| Possible Status | 200; 400 validation / `评分点权重和必须为100` / create failures; 401; 403; 500 |
| Idempotency | Not naturally idempotent |
| Recovery | Keep form. **Do not auto-retry POST** on 500/network. User may click 创建 again (can create a second question) |

### F4. Whole replace

| Field | Value |
|---|---|
| Method | PUT |
| Endpoint | `/api/admin/questions/{questionId}` |
| Request | `UpdateQuestionRequest` (same shape as create) |
| Response | Void |
| Possible Status | 200; 400; 401; 403; 404 `题目不存在`; 500 |
| Notes | Replaces scoring points as a set |
| Recovery | Keep form. **Do not auto-retry PUT** on network/500. User resubmits consciously |

### F5. Status

| Field | Value |
|---|---|
| Method | PUT |
| Endpoint | `/api/admin/questions/{questionId}/status` |
| Request | `UpdateQuestionStatusRequest`: `{ "status": "ENABLED\|DISABLED" }` |
| Response | Void |
| Possible Status | 200; 400; 401; 403; 404; 500 |
| Notes | Historical `/enable` `/disable` paths: **NO FROZEN API** |

---

## G. Admin knowledge

### G1. Upload

| Field | Value |
|---|---|
| Page / Feature | A-KNOWLEDGE |
| User Action | 上传文档 |
| Method | POST |
| Endpoint | `/api/admin/knowledge/documents` |
| Request | `multipart/form-data`: `file`, `title` ≤200, `category` (`KnowledgeCategory`, same names as question categories), optional `source` ≤255 |
| File | UTF-8 `.md` / `.markdown` / `.txt`; max **5 MiB**; empty rejected |
| Response | `UploadKnowledgeDocumentResponse`: `documentId`, `fileName`, `fileType` MARKDOWN\|TEXT, `documentVersion` (1), `processingStatus` **UPLOADED** |
| Possible Status | 200; 400 empty/type/UTF-8/title/size-as-business; **413** `上传文件不能超过5MB`; 401; 403; 500 |
| Success UX | Show id + “尚未处理”. Enable process |
| Recovery | Keep form + file. **Do not auto-retry POST.** Each successful upload inserts a new document |
| Idempotency | Each upload inserts. Duplicate content is **not** rejected |
| Notes | Does not start chunking / embedding / Milvus |

### G2. Process

| Field | Value |
|---|---|
| User Action | 开始处理 / FAILED 重试 / stale PROCESSING 恢复 |
| Method | POST |
| Endpoint | `/api/admin/knowledge/documents/{documentId}/process` |
| Request | path id >0 |
| Response | Void |
| Possible Status | 200 (READY path); 400 `文档ID必须大于0` / `知识文档处理服务当前不可用`; 401; 403; 404 `知识文档不存在`; 409 `文档已处理完成，不能重复处理` / `文档正在处理中，请稍后重试` / `文档当前状态不允许处理`; 502; 500 |
| Loading UX | Long. Disable double click |
| Success UX | Local recent list → READY |
| Recovery | Same `documentId` only. Fresh PROCESSING: wait (409). Stale PROCESSING / FAILED: POST process again. READY: stop. 502/network: **state-specific** retry of this process, not a new upload |
| Idempotency | READY rejected. FAILED/stale PROCESSING occupy then reconcile vectors by documentId |
| Notes | No GET document. Frontend must remember `documentId`. No global mutation retry |

### G3. Missing knowledge APIs

| Historical | Frozen |
|---|---|
| GET documents | **NO FROZEN API** |
| GET document detail | **NO FROZEN API** |
| enable / disable | **NO FROZEN API** (`DISABLED` enum unused by admin HTTP) |
| named reprocess / READY rebuild | **NO FROZEN API** |

---

## H. Other missing capabilities (do not invent)

| Capability | Frozen |
|---|---|
| Admin user list / disable / enable | **NO FROZEN API** |
| Admin global interview list | **NO FROZEN API** |
| Admin LLM test | **NO FROZEN API** |
| Dashboard aggregate / streak / ranking | **NO FROZEN API** |
| Retrieval HTTP for users | **NO FROZEN API** (RAG is internal to evaluation) |

---

## I. HTTP status catalog for UX mapping

| HTTP | Handler | Public message pattern | Frontend |
|---|---|---|---|
| 200 | success | `success` | Use `data` (nullable for current) |
| 400 | validation, `BusinessException` | field defaultMessage or business text | Inline |
| 401 | `RestAuthenticationEntryPoint` | `未认证或访问令牌无效` | Login |
| 403 | `RestAccessDeniedHandler` | `权限不足，无法访问资源` | Forbidden, stay in |
| 404 | `ResourceNotFoundException` or missing handler | `面试会话不存在` / `面试问题不存在` / `题目不存在` / `知识文档不存在` / `请求的资源不存在` | Not-found copy; no IDOR leak |
| 405 | method not supported | `不支持的请求方法` | Treat as client error |
| 409 | `ConflictException` | state / idempotency messages | State-specific wait/resume |
| 413 | multipart oversize | `上传文件不能超过5MB` | Upload form |
| 500 | uncaught / illegal state | `系统异常，请稍后重试` | **GET** may retry. **POST/PUT**: no generic retry — §L |
| 502 | `ExternalServiceException` | `外部服务暂时不可用` | Per §L (answer same pair; report GET-first; process same documentId) |

Login/register failures are **400**, not 401.

---

## L. Mutation retry contract

Global HTTP client must **not** auto-retry POST/PUT.

| Mutation | After uncertain failure (network / timeout / unknown / 500 / 502) |
|---|---|
| Answer POST | Allowed **only** with same `requestId` + exact `answerContent` (durable record written **before** first dispatch) |
| Report POST | GET report first. READY → stop. GENERATING → poll. FAILED / NOT_STARTED on COMPLETED session → POST |
| Interview create POST | GET current first. If active, resume. If null, user-initiated POST only |
| Knowledge process POST | Same `documentId`, state-specific |
| Register POST | Do not auto-retry |
| Admin question create POST | Do not auto-retry |
| Knowledge upload POST | Do not auto-retry |
| Password PUT / question PUT / status PUT | Do not auto-retry; user resubmits |
| Login POST | User-initiated new attempt |

GET requests are normally retryable.

---

## J. Frontend-visible enums (exact names)

```text
UserRole: USER, ADMIN
UserStatus: ENABLED, DISABLED
QuestionDifficulty: EASY, MEDIUM, HARD
QuestionCategory / KnowledgeCategory:
  JAVA_BASIC, JAVA_COLLECTION, JVM, JAVA_CONCURRENCY,
  SPRING, MYSQL, REDIS, NETWORK, PROJECT_SCENARIO
QuestionStatus: ENABLED, DISABLED
QuestionPointType: CORE, KEY, PRINCIPLE, INTERNAL, ADVANCED
InterviewSessionStatus: CREATED, IN_PROGRESS, COMPLETED, CANCELLED
InterviewQuestionType: MAIN, FOLLOW_UP
InterviewQuestionStatus: PENDING, WAITING_ANSWER, ANSWERED, SKIPPED
InterviewAnswerStatus: SUBMITTED, EVALUATING, EVALUATED, FAILED
EvaluationPhase: INITIAL, FINAL
DecisionAction: FOLLOW_UP, NEXT_MAIN, FINISH
EvaluationLevel: EXCELLENT, GOOD, FAIR, WEAK
InterviewReportStatus: NOT_STARTED, GENERATING, READY, FAILED
KnowledgeFileType: MARKDOWN, TEXT
KnowledgeProcessingStatus: UPLOADED, PROCESSING, READY, FAILED, DISABLED
```

`CANCELLED`, `SKIPPED`, knowledge `DISABLED` are not driven by current user/admin HTTP flows. Do not build controls for them.

`UserWeaknessStatus` is persistence-only.

---

## K. Contract the UI must not leak

Never display even if a future mapper returns them:

- reference answers and scoring-point snapshots on user interview pages
- `requestId`, `errorCode`, `version`, `retrievalBatchId`, `rawResult`
- `followUpRecommended`, `suggestedFollowUp` as a chat suggestion independent of Java `nextAction`
- JWT internals, password hashes, embedding keys
