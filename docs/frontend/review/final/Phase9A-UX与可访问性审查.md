# PHASE 9A REVIEW B — FINAL UX + ACCESSIBILITY REVIEW

## Review metadata

- Date: 2026-09-21
- Reviewer: Kimi K3
- Mode: READ-ONLY. No production code, backend, tests, `package.json`, vite config, or any file under `frontend/src` was modified. Exactly one output file (this document) was created.
- Independence note: this review was performed without access to Review A or Review C. `Phase9A-前端工程审查.md` and `Phase9A-独立盲审.md` were not read. No findings were merged or consolidated from other reviews.
- Backend Freeze SHA referenced by the design docs: `20a933ae8092ef908c2f03193c0b0f9547da71c1`
- Frozen design direction: Technical Interview Workspace V2 / V2.2 (`docs/frontend/DESIGN_SYSTEM.md`)

## Sources inspected

Documents (read to EOF):

- `docs/frontend/PRODUCT_UX_SPEC.md`
- `docs/frontend/FRONTEND_PAGE_MAP.md`
- `docs/frontend/DESIGN_SYSTEM.md`
- `docs/frontend/ROUTE_MAP.md`
- `docs/frontend/STATE_MODEL.md`

Frontend source (read for rendered-truth verification of structure, copy wiring, ARIA, focus, tap targets, responsive rules):

- `frontend/index.html`
- `frontend/src/app/styles/tokens.css`, `base.css`, `reset.css`, `shells.css`, `utilities.css`
- `frontend/src/components/ui/`: `BaseButton.vue`, `BaseIconButton.vue`, `BaseInput.vue`, `BaseTextarea.vue`, `EmptyState.vue`, `LoadingBlock.vue`, `PageHeader.vue`, `StatusNotice.vue`
- `frontend/src/components/shell/`: `ProductShell.vue`, `InterviewFocusShell.vue`, `SessionSplitWorkspace.vue`, `useDismissibleMenu.ts`
- `frontend/src/features/auth/`: `LoginView.vue`, `RegisterView.vue`, `AuthField.vue`, `PasswordField.vue`
- `frontend/src/features/dashboard/`: `DashboardView.vue`, `DashboardRecentList.vue`
- `frontend/src/features/interview/`: `CreateInterviewView.vue`, `InterviewSessionView.vue`, `InterviewReportView.vue`, `InterviewDetailView.vue`, `DifficultyCard.vue`, `InterviewQuestionPane.vue`, `InterviewAnswerPane.vue`, `InterviewEvaluationPane.vue`, `InterviewProgress.vue`, `InterviewSessionDock.vue`, `InterviewTimeline.vue`, `InterviewTimelineItem.vue`, `useInterviewSession.ts` (alert/tone mapping), `sessionCopy.ts`, `evaluationLevelDisplay.ts`, `historyScoreDisplay.ts`
- `frontend/src/features/history/`: `HistoryRow.vue`, `HistoryPagination.vue`
- `frontend/src/features/admin/`: `AdminQuestionListView.vue`, `AdminQuestionFormView.vue`, `AdminQuestionDetailView.vue`, `AdminKnowledgeView.vue`, `knowledgeAdminCopy.ts`
- `frontend/src/features/profile/`: `ProfileView.vue`
- `frontend/src/app/views/`: `ForbiddenView.vue`, `NotFoundView.vue`

Verification commands run (read-only, no source change):

- `npm run typecheck` (vue-tsc) — PASS, 0 errors
- `npm run lint` (eslint) — PASS, 0 errors
- `npm run test:unit` (vitest) — PASS, 36 files / 266 tests, 0 failures

## Screenshot inventory actually viewed

Screenshot sets found: `docs/frontend/review/screenshots-8b1` (8 files), `screenshots-8b2` (11), `screenshots-8b3` (14), `screenshots-8b4` (15), `screenshots-8b5` (13). **No `screenshots-8c` directory exists anywhere in the repo** (searched recursively), consistent with Phase 8C being `PARTIAL_PASS_WITH_ENV_BLOCKER`.

Method disclosure (important limitation): in this environment the file-reading tool returned no perceivable image content and the IDE browser tool could not keep a tab open (tab creation succeeded but every subsequent navigation failed with "No browser tab available" / "Browser view not found"; verified across repeated attempts). Therefore every screenshot file was inspected **quantitatively** (pixel dimensions, full-frame color histograms, top-left canvas pixel, horizontal color-run geometry, content bounding boxes) via a throwaway script, and every visual claim below is corroborated by the source code that deterministically produces the rendered state. No finding rests on an unverified visual impression.

All 61 PNG files across the five sets were measured. Per-file measured facts used as evidence:

- Palette compliance (all 61 files): purple-family pixels ≈ 0.00–0.11% everywhere; page top-left pixel is `#F7F8FA` or `#FFFFFF` everywhere (no deprecated V1 warm-paper page background); mid-row distinct-color scans show no gradient bands. The V2 palette (`#F7F8FA` canvas, `#FFFFFF` surface, `#2563EB` primary, semantic soft insets) is confirmed in the rendered output.
- Geometry calibration on `screenshots-8b4/history-1440.png` and `screenshots-8b3/session-answering-1440.png`: sidebar white band = 334px ≈ 224 CSS × 1.5 DPR; session split panels measure 708:1068px = 39.9:60.1 ≈ the frozen `--question-pane: 40%` / `--answer-pane: 60%`; split width ≈ 1192 CSS = `--session-max: 1240px` − 2×24px padding. The rendered layout matches the frozen tokens.
- `screenshots-8b3/session-evaluating-1440.png`: 5.1% of frame is `#FEF3F2` (danger-soft) — a large red-tinted region during a *waiting* state (used as evidence for UX-02).
- `screenshots-8b3/session-evaluation-followup-1440.png` / `detail-1440.png`: semantic soft insets (`#ECFDF3` 3.3–5.6%, `#FFFAEB` 3.3–4.1%, `#EFF6FF` 1.4–4.5%) are local blocks, not page-wide washes — consistent with DESIGN_SYSTEM §12/§13.

## Viewport coverage and gaps

Nominal coverage vs. measured pixel dimensions:

| Set | "1440" files | "1024" files | "390" files | 390-width captures genuine? |
|---|---|---|---|---|
| 8b1 | 1665×1620 | — | 1665×1620 | **No** — identical desktop-class dimensions as the set's "1440" files |
| 8b2 | 1440×900 | 1024×800 | 390×844 | Yes (390 CSS @ DPR 1) |
| 8b3 | 2880×1620 | 2880×1620 | 2880×1620 | **No** — all "-390" files are desktop-width captures |
| 8b4 | 2880×1620 | 2880×1620 | 780×1688 | Yes (390 CSS @ DPR 2) |
| 8b5 | 1662×1620 | — | 1662×1620 | **No** — identical desktop-class dimensions as the set's "1440" files |

Consequences:

1. **True 390 rendering is verifiable only for Dashboard, Create (8b2) and History, Detail, Report (8b4).** Mobile rendering of Login/Register, the Interview Session (answering / evaluation / recovery / dock), Profile, Admin Questions, and Admin Knowledge could not be confirmed from the provided sets, because the files labeled "390" in 8b1/8b3/8b5 have desktop-class pixel widths. (The 8b1 `product-mobile-menu-390.png` does contain the mobile-menu backdrop color, so a mobile *layout* was active in that capture, but its viewport width cannot be confirmed as 390.) For those surfaces, mobile behavior was instead verified from the responsive CSS (`@media (max-width: 720px)` rules) and component source.
2. **No Forbidden (P-FORBIDDEN) or NotFound (P-NOT-FOUND) screenshot exists in any set.** These surfaces were reviewed from source only.
3. **No 8C screenshot set exists**; states blocked by `EMPTY_QUESTION_BANK`, `DEEPSEEK_UNAVAILABLE`, and `NO_AUTHORIZED_ADMIN_TEST_CREDENTIAL` (e.g., a real completed session with real evaluation text, real READY report with real LLM content) are covered only by mock-API captures from 8b2–8b5. This review does not treat fixture renders as real E2E evidence.
4. Fixture/mock API was **not** run for this review: the only channel available to view a rendered page (IDE browser) was non-functional, so rendering fixtures would have produced no inspectable evidence. Typecheck/lint/unit tests were run instead (all pass).

## Findings

### UX-01 — Live question text does not use the frozen `question` typography token

- Severity: P2
- Surface: Interview Session (U-SESSION), question pane
- Viewport: all (1440 / 1024 / 390)
- Evidence: `frontend/src/features/interview/components/InterviewQuestionPane.vue` — `.question-content { font-size: 16px; line-height: 1.7; }` with inherited weight 400. `DESIGN_SYSTEM.md` §4 freezes `question` at **20px desktop / 18px mobile, line 1.55, weight 650**, and §16 makes the question the visual subject of the highest-attention page ("Question + Answer dominate"). Corroborated by `screenshots-8b3/session-answering-1440.png` geometry: the question pane contains no display-scale text block.
- Why it violates the frozen design: the primary subject of the core page is rendered as plain body text (16px/400), identical to secondary copy, weakening the frozen visual hierarchy on the one page where hierarchy matters most.
- Minimal recommended repair: apply the frozen token to `.question-content` — 20px/650/1.55 desktop, 18px at ≤720px.

### UX-02 — Evaluation-pending (waiting) states are styled as danger/error

- Severity: P2
- Surface: Interview Session — 409 evaluating, 502/external failure, uncertain submit outcome (`EVALUATION_PENDING`)
- Viewport: all; evidenced at 1440
- Evidence: `InterviewSessionView.vue` renders every `session.alertMessage` through `<StatusNotice tone="danger" role="alert">`; `useInterviewSession.ts` maps `EVALUATION_PENDING` to `alertMessage` for `evaluatingHint` ("答案正在评估中，请稍后…"), `serviceUnavailable`, `externalFailure`, and `unexpectedFailure`. `screenshots-8b3/session-evaluating-1440.png` measures 5.1% of the frame as `#FEF3F2` (danger-soft), the same signature as `session-recovery-blocked-1440.png` — a waiting state is visually indistinguishable from a true failure.
- Why it violates the frozen design: DESIGN_SYSTEM §12 reserves Danger for "true error; WEAK; RECOVERY_BLOCKED" and assigns Warning to caution/waiting ("GENERATING caution"). PRODUCT_UX_SPEC §9 frames 409-evaluating as "explain the state", not as an error. Red + `role="alert"` for a routine wait over-signals failure and trains users to distrust the state language.
- Minimal recommended repair: render `EVALUATION_PENDING` messages with `tone="warning"` (and `role="status"`); keep `tone="danger"`/`role="alert"` for `RECOVERY_BLOCKED` and hard `ERROR` only.

### UX-03 — `--text-3` (#98A2B3) used for meaningful 12px text below contrast threshold

- Severity: P2
- Surface: Session evaluation pane (phase label 阶段性评价 / 本题最终评价); Product shell nav group label (管理)
- Viewport: all
- Evidence: `InterviewEvaluationPane.vue` `.evaluation-phase { font-size: 12px; color: var(--text-3); }`; `shells.css` `.nav-group-label { font-size: 12px; color: var(--text-3); }`. `tokens.css` itself documents `--text-3: #98a2b3` as "Decorative connectors / **unused for small text**". #98A2B3 on #FFFFFF measures ≈ 2.8:1, below WCAG 1.4.3's 4.5:1 for small text.
- Why it violates the frozen design/accessibility: the evaluation phase label is real state information (INITIAL vs FINAL drives whether a follow-up exists), not decoration; the implementation contradicts the token's own frozen contract and fails small-text contrast.
- Minimal recommended repair: use `--text-2` (#667085, ≈ 5.7:1) for both labels.

### UX-04 — No `h1` on the Interview Session page and the NotFound page

- Severity: P3
- Surface: U-SESSION (highest-attention page), P-NOT-FOUND
- Viewport: all
- Evidence: `InterviewFocusShell.vue` renders the product title as `<p class="focus-title">`; `InterviewSessionView.vue` contains no `h1` (the evaluation pane defaults to `h2`, question pane uses a `<p>` kicker) — the heading outline starts at h2. `NotFoundView.vue` renders only `EmptyState` (h2). All other product pages have exactly one `h1` via `PageHeader` or an explicit element.
- Why it violates accessibility expectations: WCAG 2.4.6 / heading-hierarchy best practice — the page a user spends the most time on has no level-1 heading identifying it.
- Minimal recommended repair: add an `h1` (visually styled as the current focus title, or `u-sr-only` "面试进行中 · 第 n 题") in `InterviewFocusShell`; add an `h1` to `NotFoundView`.

### UX-05 — `document.title` is static across all routes

- Severity: P3
- Surface: all
- Viewport: all
- Evidence: `index.html` has a fixed `<title>面试训练</title>`; a repo-wide search for `document.title` in `frontend/src` returns no matches, so no router-level title updates exist.
- Why it violates accessibility expectations: WCAG 2.4.2 (Page Titled) intent — browser tab / history / screen-reader context never reflects the current surface (登录 vs 面试进行中 vs 报告), which matters for a multi-tab practice workflow.
- Minimal recommended repair: set `document.title` in a router `afterEach` hook from route meta (e.g., `面试训练 · 历史`).

### UX-06 — Dashboard recent-row action link lacks the 44px tap target used elsewhere

- Severity: P3
- Surface: Dashboard (U-DASHBOARD) recent-completed list
- Viewport: 390 (most acute), all
- Evidence: `DashboardRecentList.vue` `.recent-action` is an inline link with no `min-height` (clickable height ≈ 14px font × 1.6 line ≈ 22px). The equivalent action in `HistoryRow.vue` (`.history-action`) sets `min-height: 44px`, as do pagination buttons, report links, and mobile menu items.
- Why it violates the frozen design: inconsistent tap-target sizing for the same class of action (open report/detail) across two near-identical row components; on 390 the primary "生成报告 / 查看报告" entry from the workbench is the smallest tap target on the page.
- Minimal recommended repair: give `.recent-action` `min-height: 44px` with inline-flex alignment (matching `HistoryRow`), at minimum inside the ≤720px media query.

### UX-07 — Five-dimension score labels shrink to 11px on mobile

- Severity: P3
- Surface: Session evaluation pane / Detail / Report evidence (shared `InterviewEvaluationPane`)
- Viewport: 390 (≤720px media query)
- Evidence: `InterviewEvaluationPane.vue` `@media (max-width: 720px)` sets `.score-label { font-size: 11px; }` and `.score-value { font-size: 15px; }`; the frozen `meta` token is 13px (DESIGN_SYSTEM §4).
- Why it violates the frozen design: dimension names (正确 / 完整 / 深度 / 清晰 / 实践) are meaningful labels, and 11px is below the system's own minimum meta size; two-character Chinese labels at 11px are at the edge of comfortable readability.
- Minimal recommended repair: keep labels at ≥12px and let the 5-column row wrap (e.g., 3+2) instead of shrinking type.

## FALSE POSITIVES REJECTED

1. **"Left-stuck narrow column with 25% empty canvas on 1440" (8b3/8b4 desktop shots) — rejected as capture artifact.** Raw content bounding boxes end at ~75% of frame width, which initially suggested a §10 density violation. Pixel calibration shows the sidebar measures 334px ≈ 224 CSS × 1.5 DPR and the session split measures exactly 40/60 with ≈1192 CSS content width = `--session-max` − padding, centered within a 1440-CSS emulated viewport; the extra right-hand gray is screenshot canvas wider than the emulated viewport, not app layout. Rendered density matches the frozen tokens (`--workspace-max: 1200`, `--session-max: 1240`).
2. **Input focus indicator (`outline: none` on `.base-input:focus`) — accepted.** The 1px border change #E4E7EC → #2563EB spans the full perimeter with >3:1 contrast change, satisfying WCAG 2.4.11; the faint `#EFF6FF` halo is supplementary, not the sole indicator. Buttons keep the global 2px `:focus-visible` outline.
3. **Semantic-colored feedback blocks (做得好的 / 需要补充 / 参考修正) — accepted.** They are local Level-2 insets with icon + text companions (never color-only), explicitly allowed by DESIGN_SYSTEM §12/§13; measured ink/paper contrasts (≈7:1 success, ≈6:1 warning) pass. Histograms confirm they never wash a full page.
4. **Mobile navigation menu (`role="dialog" aria-modal="true"`) — accepted.** `useDismissibleMenu.ts` implements initial focus into the panel, Tab/Shift+Tab wrap, Escape close, focus return to the trigger, and body scroll lock; the trigger is 44px in the mobile top bar.
5. **`.auth-form-error:focus { outline: none }` — accepted.** The error container is a programmatic focus target (`tabindex="-1"`) used to announce submission failure, not a keyboard-navigable control.
6. **"Warm paper" histogram hits (3–4%) in evaluation/detail screenshots — rejected.** A loose color heuristic flagged `#FFFAEB` (warning-soft insets) as V1 paper; the page canvas top-left pixel is `#F7F8FA`/`#FFFFFF` in all 61 files, and no serif/warm-paper V1 signature exists. V2 palette compliance confirmed; purple ≈ 0%, no gradients detected.
7. **Full-card `opacity: 0` radio inputs on DifficultyCard — accepted.** Native radio semantics inside `<label>`, grouped by `fieldset`/`legend` + `role="radiogroup"`, with a `:has(input:focus-visible)` focus indicator; keyboard-operable peer selection with no recommended default, as frozen.
8. **BaseButton default 40px min-height — accepted.** Meets WCAG 2.5.8 (24px minimum), and primary mobile contexts (session dock, auth forms, filter bars, report states, mobile menu) override to 44px.
9. **Admin table → stacked-row transformation at ≤720px hides `thead` — accepted risk.** Visual `data-label` labels compensate; the DOM table remains for wide viewports and the transformation matches the FRONTEND_PAGE_MAP responsive contract ("390: stacked rows with knowledgePoint + status").
10. **Google Fonts CDN link for IBM Plex Mono in `index.html` — accepted with note.** Mono usage degrades gracefully to `ui-monospace`/Consolas per the `--mono` stack; no frozen rule forbids the CDN, though offline demos will fall back.

## Count summary

| Severity | Count | IDs |
|---|---|---|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 3 | UX-01, UX-02, UX-03 |
| P3 | 4 | UX-04, UX-05, UX-06, UX-07 |

Overall assessment: the rendered product is faithful to the frozen Technical Interview Workspace V2/V2.2 direction — palette, density tokens, split-session geometry, honest empty/loading/error states, durable-submit UX, and admin utility surfaces all verify against the frozen documents. No P0/P1 issues found. The three P2 items are bounded and local: question typography scale, danger-tone misuse for waiting states, and sub-contrast `--text-3` small text. The main verification gap is evidentiary, not product: true-390 captures exist only for 8b2/8b4 surfaces, and no 8C set exists due to the known environment blockers.
