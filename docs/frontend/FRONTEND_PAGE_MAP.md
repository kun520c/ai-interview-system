# FRONTEND_PAGE_MAP

Status: Design Gate 1 candidate  
Source of truth: frozen backend `20a933ae8092ef908c2f03193c0b0f9547da71c1`  
Pages below are IA contracts. They are not Vue SFCs.

Navigation groups:

```text
PUBLIC
AUTHENTICATED USER
ADMIN
```

---

## PUBLIC

### P-LOGIN Login

| Field | Contract |
|---|---|
| Purpose | Authenticate with account + password and receive a Bearer access token |
| Primary User Question | “我怎样进入自己的面试工作台？” |
| Primary Action | 登录 → `POST /api/auth/login` |
| Secondary Action | 去注册 |
| Data Source | Login form (local). Success: `LoginResponse` |
| Empty State | Empty form. No “forgot password” |
| Loading State | Disable submit; keep values |
| Error State | 400 field/business messages inline (`账号或密码错误`, `账号已被禁用`, validation). Network: retry. Do not map 400 to logout |
| Responsive Priority | Centered narrow form. 390px: full-width fields, large enough tap targets |

### P-REGISTER Register

| Field | Contract |
|---|---|
| Purpose | Create a `USER` account. Does not sign the user in |
| Primary User Question | “我怎样创建账号？” |
| Primary Action | 注册 → `POST /api/auth/register` then go to Login with account prefilled |
| Secondary Action | 去登录 |
| Data Source | Register form only. No token in response |
| Empty State | Empty form |
| Loading State | Disable submit |
| Error State | 400 `账号或邮箱已存在` or validation messages inline. No distinction between duplicate account vs email in UI (backend public message is unified) |
| Responsive Priority | Same as Login |

---

## AUTHENTICATED USER

### U-DASHBOARD Workbench

| Field | Contract |
|---|---|
| Purpose | Answer “我现在应该做什么？” and route the user into start, resume, or review |
| Primary User Question | “我现在应该做什么？” |
| Primary Action | If `GET /api/interviews/current` has data → 继续面试. Else → 开始面试 |
| Secondary Action | Open recent completed interview / report; open profile |
| Data Source | `GET /api/users/me` + `GET /api/interviews/current` + `GET /api/interviews/history?page=1&pageSize=5` |
| Empty State | No active interview + empty history: calm prompt to start the first interview. No fake stats |
| Loading State | Skeleton for the next-action region and the recent-list region independently |
| Error State | Current=null is success. 401 → login. 500/network on one request: fail that region, keep the others if loaded |
| Responsive Priority | Next-action block first. Recent list stacks. Never hide the primary CTA |

Forbidden: KPI cards, streaks, rankings, charts, weakness widgets, “AI insights” not returned by APIs.

### U-CREATE Create or Resume Interview

| Field | Contract |
|---|---|
| Purpose | Choose `EASY|MEDIUM|HARD` **or** resume the existing active session |
| Primary User Question | “我能开始一场什么样的面试？是否已有未完成的一场？” |
| Primary Action | No active: 开始面试 → `POST /api/interviews` `{ difficulty }`. Active: 继续当前面试 (POST also resumes; GET current is enough to navigate) |
| Secondary Action | Back to dashboard. Read-only explanation of MAIN counts (5 / 7 / 9) and “可能有一次追问” |
| Data Source | `GET /api/interviews/current` first. Create body: `CreateInterviewRequest.difficulty` only |
| Empty State | Three peer difficulty choices (`EASY` / `MEDIUM` / `HARD`) with MAIN counts (5 / 7 / 9) as explanation. **No recommended default.** User must select one before 开始面试. Not an empty wizard |
| Loading State | Disable start. If POST is slow (plan snapshot), show “正在准备本场题目” |
| Error State | 400 invalid difficulty. 500 if question bank cannot fill the plan (internal). Keep the choice. If POST returns an existing session whose `difficulty` ≠ requested, treat as resume notice, not as a successful new exam |
| Responsive Priority | Three difficulty choices must remain fully visible. No extra fake fields to “fill the page” |

### U-SESSION Interview Session

Highest-attention page in the product.

| Field | Contract |
|---|---|
| Purpose | Answer the current question, wait for evaluation, read feedback, then explicitly continue |
| Primary User Question | “这一题要我回答什么？我答得怎样？下一步是什么？” |
| Primary Action | 提交答案 → `POST /api/interviews/{sessionId}/answers` |
| Secondary Action | After evaluation: 继续追问 / 下一题 / 去生成报告. No cancel |
| Data Source | Durable `localStorage` submitRecord (before POST) + `GET /api/interviews/current` for question/progress. Submit 200 for evaluation + nextAction + nextQuestion |
| Empty State | `current data=null` → redirect dashboard. Do not render a blank exam |
| Loading State | (1) page load: question skeleton. (2) submit: snapshot locked, primary button evaluating, do not navigate away |
| Error State | See STATE_MODEL. 409 evaluating: wait/poll current question; same pair. 502/network after dispatch: same pair, no new id. `RECOVERY_BLOCKED` if other requestId and local record lost. 400 workflow unavailable |
| Responsive Priority | Question readable. Textarea usable (min ~8 rows desktop, ~6 on 390px, never a one-line input). Primary CTA sticky/visible. Evaluation readable as stacked text, not a disappearing toast |

Visual subject: Question + Answer.  
Quiet metadata: `completedQuestionCount + 1 / plannedQuestionCount`, category, knowledgePoint, `MAIN|FOLLOW_UP`.

After submit, **do not** swap in `nextQuestion` until the user confirms.

### U-HISTORY History

| Field | Contract |
|---|---|
| Purpose | Browse the user’s completed interviews |
| Primary User Question | “我以前完成过哪些场？哪一场要复盘？” |
| Primary Action | Open Detail or Report of one completed session |
| Secondary Action | Pagination; generate report if `reportStatus` is NOT_STARTED / FAILED |
| Data Source | `GET /api/interviews/history?page&pageSize` |
| Empty State | Honest empty: 还没有完成的面试. Link to start/resume. Do not show active session here |
| Loading State | List skeleton |
| Error State | 400 invalid page. 401 login. 500 retry |
| Responsive Priority | Each row: difficulty, endedAt, score-or-pending-report, status. Stack meta on 390px. Pagination remains usable |

`totalScore` may be null until report READY. Show “报告未生成”, never `0`.

### U-DETAIL Interview Detail

| Field | Contract |
|---|---|
| Purpose | Reconstruct the real question/answer/evaluation timeline of one **owned** session |
| Primary User Question | “这一场里我到底答了什么？每题反馈是什么？” |
| Primary Action | Read. If COMPLETED and report not READY, generate/open report |
| Secondary Action | Back to history |
| Data Source | `GET /api/interviews/{sessionId}` |
| Empty State | Session with no questions is an internal inconsistency; show error, not a fake timeline |
| Loading State | Timeline skeleton |
| Error State | 404 `面试会话不存在` (missing or not owned). 400 bad id |
| Responsive Priority | Question → answer → evaluation stack. Do not use a dense multi-column table on 390px |

Access rule:

- `COMPLETED` → render full timeline (all questions were asked).
- `IN_PROGRESS` / `CREATED` → **do not render**. Redirect to U-SESSION. Prevents spoiling `PENDING` MAIN content that the detail API can return.

MAIN evaluation shown is that answer’s own evaluation (often INITIAL). FOLLOW_UP shows its FINAL. Do not substitute report averages into rows.

### U-REPORT Report

Highest information-density page. **Completed-session surface only.**

| Field | Contract |
|---|---|
| Purpose | Deliver outcome, meaning, strengths, gaps, actions, then evidence |
| Primary User Question | “这场面试说明了什么？我下一步该补什么？” |
| Primary Action | If COMPLETED and not READY: 生成报告 / 重新生成 → `POST /api/interviews/{sessionId}/report`. If READY: read |
| Secondary Action | Open/scroll evidence from Detail; back to history |
| Data Source | **First** `GET /api/interviews/{sessionId}` (session status). Only if `COMPLETED`: `GET .../report`. Evidence: same Detail GET when READY |
| Empty State | COMPLETED + NOT_STARTED: “尚未生成报告” + generate CTA. GENERATING: waiting. FAILED: retry. Do not show generate CTA from report `NOT_STARTED` without COMPLETED |
| Loading State | Session GET then report GET skeletons. POST: blocking generation. Uncertain POST: poll GET report — do not auto-POST |
| Error State | 401 login. 404 not owned. 400 generator unavailable. 502: GET report first. Do not use POST 409 “尚未完成” as the completion check |
| Responsive Priority | Score + result stay at top. Long text stacks. Evidence accordion/sections, not nested cards |

If session is `CREATED` / `IN_PROGRESS` → this page must not render; redirect to U-SESSION.

Reading order is mandatory: Outcome → Meaning → Strength → Gap → Action → Evidence.

### U-PROFILE Profile / Password

| Field | Contract |
|---|---|
| Purpose | Show current identity and change password |
| Primary User Question | “这是我的账号吗？我怎样改密码？” |
| Primary Action | 修改密码 → `PUT /api/users/me/password` then forced re-login |
| Secondary Action | Back |
| Data Source | `GET /api/users/me` (read-only account, username, email, role, createdAt). Password form is local |
| Empty State | N/A |
| Loading State | Profile skeleton; password submit disabled |
| Error State | 400 wrong current password / same new password / validation. Success: token is dead — go to Login immediately |
| Responsive Priority | Read-only identity then password form. 390px full width |

No avatar upload, no email change, no weakness list.

---

## ADMIN

Admin pages require `role=ADMIN`. USER who navigates here sees P-FORBIDDEN, still logged in.

### A-QUESTION-LIST Question Bank List

| Field | Contract |
|---|---|
| Purpose | Find and manage question-bank items |
| Primary User Question | “题库里有哪些题？哪一题需要改或停用？” |
| Primary Action | Open detail / create |
| Secondary Action | Filter category, difficulty, status, keyword; pagination; jump to edit |
| Data Source | `GET /api/admin/questions` (`pageNum`, `pageSize`, optional filters) |
| Empty State | No records for this filter. CTA: 新建题目 |
| Loading State | Table/list skeleton |
| Error State | 401/403/400/500 as global rules |
| Responsive Priority | Desktop: table. 1024: fewer columns. 390: stacked rows with knowledgePoint + status |

### A-QUESTION-CREATE Create Question

| Field | Contract |
|---|---|
| Purpose | Add one question with scoring points summing to 100 |
| Primary User Question | “这道题和评分点怎么入库？” |
| Primary Action | 创建 → `POST /api/admin/questions` |
| Secondary Action | Cancel to list |
| Data Source | Form only |
| Empty State | One empty scoring-point row minimum |
| Loading State | Disable submit |
| Error State | Inline: missing fields, UTF-8 TEXT limit, weight sum ≠ 100 (`评分点权重和必须为100`) |
| Responsive Priority | Long textareas first. Scoring-point list stacks |

### A-QUESTION-DETAIL Question Detail

| Field | Contract |
|---|---|
| Purpose | Inspect question + scoring points before editing/status change |
| Primary User Question | “这道题现在长什么样？” |
| Primary Action | 编辑 |
| Secondary Action | Enable/disable via `PUT .../status`; back to list |
| Data Source | `GET /api/admin/questions/{questionId}` |
| Empty State | 404 `题目不存在` |
| Loading State | Detail skeleton |
| Error State | 404/403/401 |
| Responsive Priority | Content then scoring points. Reference answer is admin-only; never reuse this view in user interview |

### A-QUESTION-EDIT Replace Question

| Field | Contract |
|---|---|
| Purpose | Whole replacement of question and scoring points |
| Primary User Question | “我要怎样改这道题，而不拆开评分点单独补丁？” |
| Primary Action | 保存 → `PUT /api/admin/questions/{questionId}` |
| Secondary Action | Cancel to detail |
| Data Source | Prefill from detail GET |
| Empty State | Same as create |
| Loading State | Disable save |
| Error State | Same validation as create; 404 if deleted |
| Responsive Priority | Same as create |

### A-KNOWLEDGE Knowledge Upload / Process

| Field | Contract |
|---|---|
| Purpose | Upload a UTF-8 Markdown/TXT document, then trigger processing |
| Primary User Question | “这份资料怎样进入评价用的知识库？” |
| Primary Action | 上传 → multipart POST. Then 开始处理 → `POST .../{documentId}/process` |
| Secondary Action | Retry process for FAILED or stale PROCESSING using stored `documentId` |
| Data Source | Upload response. **No list API.** Browser-local recent uploads labeled “本机最近上传” |
| Empty State | Empty form + explanation that upload does not process automatically |
| Loading State | Upload: disable. Process: long-running; 409 if fresh PROCESSING |
| Error State | 400 type/empty/UTF-8/title. 413 oversize. 404 missing id. 409 READY or in-progress. 400 `知识文档处理服务当前不可用`. 502 external |
| Responsive Priority | Form first. Recent-upload list second. Never invent a server-side catalog |

READY reprocess is deferred. Do not show “重新向量化已完成文档”.

---

## Shared non-feature pages

### P-FORBIDDEN

Authenticated but lacking `ADMIN` for an admin route. Stay logged in. Explain 权限不足. Link to dashboard.

### P-NOT-FOUND

Unknown route, or resource 404 that the user opened directly.

### P-SESSION-EXPIRED

Optional dedicated copy on Login after 401: “登录已过期，请重新登录”. Not a separate backend page.

---

## Pages that must not exist in MVP

| Tempting page | Reason |
|---|---|
| Forgot password | NO FROZEN API |
| Weakness / 成长雷达 | NO FROZEN GET |
| Cancel interview confirm | NO FROZEN API |
| Admin users | NO FROZEN API |
| Admin all-interviews | NO FROZEN API |
| Knowledge document library | NO FROZEN LIST/DETAIL |
| Marketing landing with fake screenshots as product data | Out of MVP product loop |
| Chat-with-AI | Wrong mental model |
