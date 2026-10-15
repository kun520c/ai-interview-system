# DESIGN_SYSTEM

Status: Phase 7C-V2 visual direction + Phase 7D-V2.1 density repair + Phase 7D-V2.2 composition polish  
Frozen backend: `20a933ae8092ef908c2f03193c0b0f9547da71c1`  
This file is visual direction. It does not change product IA, routes, or APIs.

```text
V1 Desk Script / 案头墨卷 = DEPRECATED VISUAL DIRECTION
V2 Technical Interview Workspace / 技术面试训练工作台 = CURRENT
```

V1 failed Gate 2 visual review: warm paper + serif-first + huge whitespace + rule-as-structure read as Claude / Anthropic editorial, not as a training product.

---

## 0. Deprecated: Desk Script / 案头墨卷

Do **not** implement V1:

- warm paper `#F4F0E8` / `#FBF8F2`
- serif-first (Noto Serif SC as display/question)
- manuscript rules as the main organizer
- 720px article column as the product shell
- “typography instead of interaction” to the extreme
- Report as a long essay

Keep from V1 only the **product contracts**: sequential interview, no recommended difficulty, no fake charts, Evaluation before next question, RECOVERY_BLOCKED honesty.

---

## 1. Design intent V2

### Concept name

**Technical Interview Workspace / 技术面试训练工作台**

The product is a calm, structured workspace for sitting a Java backend mock interview:

- a workbench that answers “what should I do now?”
- a difficulty picker that is obviously interactive
- a sequential interview focus mode (one current question)
- a structured review of scores, strengths, gaps, and next actions

It is **not**:

- an editorial blog or documentation site
- Claude / Anthropic UI
- an enterprise admin console
- a generic SaaS landing page
- an AI chatbot
- an exam-management system
- an IDE / hacker terminal
- a question library / course catalog

AI is a capability inside evaluation and report writing. It is not the visual theme.

One memorable thing: **after login, the UI should feel like a training product you can operate**, not like a long article you read.

### Principles

1. Task first. Start, continue, answer, read structured feedback, review.
2. Controlled surfaces. A bordered panel is allowed when it groups a real action or a real information cluster.
3. Sans-first. Type is clear, not a publication costume.
4. Primary blue is for actions, selection, and focus only.
5. Session is sequential. Never preview future questions.
6. Honesty: empty means empty; pending report means pending; no fake charts.

---

## 2. Visual direction

| Layer | Direction |
|---|---|
| Surface | Neutral gray canvas `#F7F8FA` + white panels `#FFFFFF` |
| Text | Near-black `#111827`, secondary `#667085` |
| Structure | Left nav + main workspace on product pages; focus mode on Session |
| Accent | `#2563EB` for primary actions and selected state only |
| Score | Tabular mono; level is a short word |
| Depth | 1px `#E5E7EB` borders, radius 8–10px, almost no shadow |
| Density | Use the page. No giant empty hero. No article-width desert |

Avoid (mandatory):

Claude/Anthropic editorial combo (warm paper + serif + huge whitespace + thin rules + article layout); purple brand; gradient; glow; glass; giant radius; teal-as-brand; fake KPI tiles; fake charts; nested card soup; dark admin sidebar; future-question catalog; marketing hero.

---

## 3. Color

| Role | Token | Hex | Use |
|---|---|---|---|
| Canvas | `--bg` | `#F7F8FA` | App background |
| Surface | `--surface` | `#FFFFFF` | Level 1 functional surfaces |
| Subtle | `--surface-subtle` | `#F2F4F7` | Level 2 inset wells |
| Text | `--text` | `#101828` | Titles, questions, scores, body |
| Text 2 | `--text-2` | `#667085` | Meta, hints |
| Text 3 | `--text-3` | `#98A2B3` | Decorative connectors / unused for small text |
| Border | `--border` | `#E4E7EC` | Panels, inputs, rows |
| Primary | `--primary` | `#2563EB` | Buttons, selected border, focus, current nav |
| Primary hover | `--primary-hover` | `#1D4ED8` | Hover |
| Primary soft | `--primary-soft` | `#EFF6FF` | Selected option fill, current nav wash, 参考修正 inset |
| Primary border | `--primary-border` | `#BFDBFE` | Selected card edge |
| Success | `--success` | `#16803C` | GOOD / EXCELLENT / 做得好的 |
| Success soft | `--success-soft` | `#ECFDF3` | Local positive inset only |
| Warning | `--warning` | `#B54708` | FAIR / 需要补充 / GENERATING caution |
| Warning soft | `--warning-soft` | `#FFFAEB` | Local caution inset only |
| Danger | `--danger` | `#B42318` | WEAK / FAILED / RECOVERY_BLOCKED |
| Danger soft | `--danger-soft` | `#FEF3F2` | Local error inset only |
| Placeholder | `--placeholder` | `#667085` | Input hint; label still required |

No gradients. No second brand purple. Color is never the only status signal. Semantic soft backgrounds are local, never page-wide.

---

## 4. Typography

Sans-first. Prototype fallback:

```text
system-ui, "PingFang SC", "Microsoft YaHei", sans-serif
```

Technical tokens / enums / scores:

```text
"IBM Plex Mono", ui-monospace, monospace
```

| Token | Size | Line | Weight | Use |
|---|---|---|---|---|
| `display` | 24 / 22 compact | 1.3 | 700 | Workspace page title |
| `title` | 20 | 1.35 | 650 | Section / question |
| `question` | 20 desktop / 18 mobile | 1.55 | 650 | Live question |
| `body` | 16 | 1.6 | 400 | Copy, evaluation, report |
| `meta` | 13 | 1.4 | 400 | timestamps, category |
| `score` | 36 report / 28 evaluation | 1 | Mono 500 | overall / totalScore |
| `button` | 14 | 1 | 600 | |

Do not use serif as the product voice.

---

## 5. Spacing, width, radius

Scale: `4, 8, 12, 16, 24, 32, 40, 48, 64`

| Context | Width |
|---|---|
| Sidebar | 224px (compact 200px at 1024) |
| Dashboard / Create main | fluid inside workspace, max ~1200px |
| Session | split workspace, max ~1240px; see §16 |
| Report main | fluid; optional 3-pane on 1440; reading measure still capped |

| Token | Value |
|---|---|
| `radius` | 8px (10px max on large panels) |
| Border | 1px `--border` |
| Shadow | none by default; optional `0 1px 2px rgba(17,24,39,0.04)` on primary training surface only |

---

## 6. Product shell

Dashboard, Create, History, Profile, Admin (future):

```text
LEFT NAV (224px) + MAIN WORKSPACE
```

Sidebar contains: product name, 工作台, 历史, 账号, quieter 管理工具. Not a black admin rail. Not an icon farm.

**Interview Session hides the sidebar.** Focus mode top bar only:

```text
面试训练 · 面试进行中 · MEDIUM · 第 3 / 7 题 · 退出到工作台
```

Never a future-question list.

390: do not force the desktop sidebar. Use a compact top bar + menu overlay.

---

## 7. Cards / surfaces

Allowed when they encode interaction or grouping:

- Dashboard primary training surface
- Difficulty option cards
- Session feedback summary
- Report grouped sections

Not allowed: every paragraph is a card; nested cards; five huge KPI cards; decorative empty cards.

---

## 8. Four core pages (visual only)

Product questions stay frozen. Visual implementation:

### Dashboard

Greeting + **two-column workbench** on 1440: primary training surface + compact interview-flow explanation. Recent-completed **table-like rows** (not article rules, not one card per row). Empty history is honest copy, never sample rows.

### Create

Three **peer** selectable cards occupy the main workspace. EASY 5 / MEDIUM 7 / HARD 9 with frozen required categories listed in full. No recommended default. Start disabled until a choice. Below: static process explanation. Active session → resume page (facts + why a second interview cannot start). No Cancel.

### Session

Question + answer dominate. Light progress: difficulty, current MAIN number, bar from `completedQuestionCount / plannedQuestionCount`. After submit: structured feedback (score · level + 5 compact metrics + 做得好的 / 需要补充 / 参考修正). Do **not** fill Session with history, related questions, or report previews. CTA: 继续追问 / 下一题 / 完成本场并查看报告. RECOVERY_BLOCKED remains honest. Mobile: scroll region + sibling dock (do not overlay last evaluation text).

### Report

Structured review, not an essay and not analytics. Order frozen: Outcome + Meaning together → Strength / Gap pair → numbered Action → Evidence accordion. 1440 may use section nav + main + light session summary. 1024 moves the summary into main. 390 stacks. **No charts.**

---

## 9. What this system refuses

If a later implementer restores warm paper + serif display + article rules, they have left V2.

The test: screenshot Dashboard and Session at 1440. If they look like a documentation homepage or a Claude conversation, it fails. If they look like a training workspace you can operate, it passes.

---

## 10. Workspace Density Principles

These rules repair V2 emptiness without changing the V2 visual direction.

1. **Whitespace must serve hierarchy or focus.** Empty gray that is left over after the real work is placed is a layout defect, not “calm.”
2. **Product pages should use the available workspace.** Dashboard, Create, and Report target about 70–85% of the main column through 2-column grouping, wider functional surfaces, and structured records — not by stretching a paragraph to 1200px.
3. **Session occupies the screen with the current task.** Desktop Session is a split workspace: current question | current answer or structured feedback. Do not add history, related questions, recommended topics, future-question lists, or report previews in order to occupy pixels.
4. **Use real contextual data before adding decoration.** Fill with Frozen Backend fields, deterministic derived state, or static product explanation of the frozen workflow. Never invent study-time, streaks, accuracy, ranking, radar charts, or other metrics the backend does not return.
5. **Controlled cards, no card soup.** A surface groups a real action or a real information cluster. Do not wrap every sentence. Do not stack three large action cards when `01 / 02 / 03` rows suffice.
6. **Honest empty / loading / error.** No fake history rows, no mock score while generating, no sample report after failure.
7. **1440 is grouping, not compression.** Prefer training surface + flow, strength + gap, resume + explanation. Do not keep a left-stuck 960px article column beside unused canvas.
8. **1024 reflows.** Stack or reduce columns; do not shrink type to preserve the 1440 grid.
9. **390 is single column.** No horizontal scroll, no tiny text, no CTA hidden under the session dock.

---

## 11. Micro identity

A 20–24px geometric **question → answer** line mark: two rounded panels and a chevron. One color (`--primary`). Not letterforms, not a mascot, not a flower, not a robot/brain/sparkle.

Used in:

- sidebar product lockup (mark + 面试训练)
- session focus header
- Dashboard idle training surface (small)

Do not enlarge into a logo system. Do not add a second brand symbol.

---

## 12. Semantic color hierarchy

| Token | Duty | Companion (required) |
|---|---|---|
| Primary | main action, selected, focus, active rail/dot | selected check, label, or 3px rail |
| Success | explicit positive feedback; EXCELLENT / GOOD | ✓ icon + “做得好的” / 优秀·良好 |
| Warning | needs supplement; FAIR; generating caution | warning icon + “需要补充” / 一般 |
| Danger | true error; WEAK; RECOVERY_BLOCKED | error icon + 薄弱 / 失败 copy |

Score numerals stay `--text`. Level words carry semantic color. Never color-only state. Never wash a whole page in green / amber / red.

---

## 13. Surface hierarchy

| Level | Style | Use |
|---|---|---|
| 0 Canvas | `--bg` | page background |
| 1 Primary functional | white, 1px `--border`, radius 8–10, no shadow | Dashboard training, difficulty cards, session panes, report outcome |
| 2 Inset | `--surface-subtle` or semantic soft | five-dimension row, selected wash, feedback subsections, session summary, status notice |
| Divider | row + 1px separator, hover wash | history rows, evidence accordion, report action list |

Do not nest a Level 1 card inside another Level 1 card.

---

## 14. Icon language

Outline SVG, 16–18px, one stroke family, `currentColor`.

Navigation: Workbench, Start, History, Profile, Admin list/file.  
Function: check, warning, error, retry, report, arrow.

Decorative icons are `aria-hidden`. Meaningful status always has a text companion. No emoji, no filled/outline mix.

---

## 15. Spacing rhythm

Component internal: 8 / 12 / 16. Related blocks: 16 / 24. Section: 32 / 40. Major page section: 40 / 48. Page top/bottom: 32–48.

Motion: hover/selected 140ms, accordion 180ms. No page animation, bounce, glow, or spinner-only loading.

---

## 16. Session split workspace

Desktop (1440, and 1024 when readable):

```text
Interview header (difficulty + 第 n / planned + progress)
┌ CURRENT QUESTION (38–42%) ┬ YOUR ANSWER / FEEDBACK (58–62%) ┐
│ category · MAIN/FOLLOW_UP │ textarea or structured feedback │
│ question content          │                                 │
│ knowledgePoint            │                      Submit →   │
│ submitted answer (after)  │                                 │
└───────────────────────────┴─────────────────────────────────┘
```

- Main workspace ~1180–1240px. Textarea min-height ~420–520px on desktop.
- After submit: left = question + submitted answer; right = feedback (score · level, one Level 2 metric row, 做得好的 / 需要补充 / 参考修正).
- 390: single column (progress → question → answer → sibling dock). Keep the dock-sibling repair; no content under the dock.
- Never a future-question list, never a second product sidebar.
