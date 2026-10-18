# Frontend V3 · 实施与验收记录

日期：2026-10-17（Asia/Shanghai）。开发分支：`codex/frontend-v3`。基础提交：`6076a70`。

## 交付状态

已实现且已验证：P0 面试双栏、原题与已提交答案、评分层级、五维细条、评价分组及操作栏；P1 工作台和报告；P2 难度、认证、历史与基础控件；P3 题库、题目编辑／详情和知识文档的视觉统一。类型检查、Lint、单元测试、构建和真实 Chrome / Mock API 浏览器检查通过。

已实现但尚未验证：未新增一套待验证功能。此次外观在真实后端数据环境中的再次联合验收、Firefox / Safari、实体手机软键盘与屏幕阅读器人工检查尚未执行。

因环境限制未完成：本轮没有启动可用于新验收的真实后端依赖组合，浏览器使用已有本地 Mock API。Vben 演示停在启动屏；Soybean 登录页首次可观察，保存时连接关闭；shadcn/ui 官方页面由网络读取工具取得文档与源码，浏览器未取得可用页面截图。已保存 shadcn-vue Sidebar、Login-02 与 Pure Admin 登录截图，未声称完成这些模板的所有页面测试。

后续可选优化：使用隔离真实后端和免费／本地 AI 替身再做联合回归；补 Safari / Firefox 与实体手机软键盘验证。它们不改变本轮已完成的前端 Mock 验收结果。

## 工作区保护与范围

原工作区：`<repo>`，分支 `codex/rag-p1-cosine-candidates-20261012`，存在大量未提交后端改动，`frontend/` 尚未跟踪。因此在 `.worktrees/frontend-v3/` 建立独立 Git Worktree，并复制当前前端与设计资料作为基线。未复制本地环境秘密或真实账号配置，未修改后端 Java、数据库、REST API 或原工作区前端文件。

最初的托管 Worktree 位于另一磁盘；Windows 跨卷移动失败后，该空托管 Worktree 已交由归档工具处理，正式开发使用当前目录中的独立 Worktree。

没有暂存、提交、推送或合并。Worktree 的 AGENTS.md 是原工作区规则快照，未作本任务的内容修改。由于前端原本未跟踪，Git 不能直接显示 V2→V3 差异；`v3/change-manifest.json` 与 `v3/V3_SOURCE.patch` 专门记录相对原前端快照的差异。补丁仅涉及前端源码、两个测试文件和 QA 脚本。

仓库现有 `.git/info/exclude` 排除了 `docs/`，本轮未改动该规则。新文档和截图实际保存在 Worktree，并同时打包到根目录的 `frontend-v3-delivery.zip`（含完整前端源码、设计资料、截图和日志，不含 node_modules、构建目录、本地配置或账号凭证）。以后若要提交文档，需要单独处理既有排除规则；本轮没有擅自暂存。

逐字节比较确认 package.json、package-lock.json、Vite 配置，以及 API、types、router、auth store、session scope、interview composables / stores / storage / utils 均与原前端一致。状态机、requestId、答案快照、请求过期判断和恢复实现未修改。

## 工程验证

| 执行 | 结果 | 计数 / 边界 |
|---|---|---|
| 基线 `npm run test:unit` 首次 | 执行环境错误 | 27 个 suite 未能导入临时缓存，9 个 suite 通过；30 项用例通过，未形成完整业务测试结果 |
| 基线：临时目录改到 Worktree 后重跑 | 通过 | 36 个文件，289 项通过，失败 0、错误 0、跳过 0 |
| V3 首轮单元测试 | 发现并修复回归 | 291 项通过，2 项失败：CTA 装饰箭头进入精确文案；改为 aria-hidden 装饰的 CSS 内容，未改动原有断言 |
| 最终 `npm run typecheck` | 通过 | 退出码 0 |
| 最终 `npm run lint` | 通过 | 退出码 0，0 errors / 0 warnings；QA 脚本追加后再次通过 |
| 最终 `npm run test:unit` | 通过 | 36 个文件，293 项通过，失败 0、错误 0、跳过 0；15.10 秒 |
| 最终 `npm run build` | 通过 | vue-tsc + Vite 成功，Vite 857ms |

新增 4 项用例：三种难度的中文名、原枚举和无默认选中；五维真实值的 0 / 20 边界、隐藏装饰条，以及总分保留服务端值。所有原有 289 项用例保留。

保留并执行的业务保证包括：创建与恢复、持久化后提交、原身份重试、评价前不前进、FOLLOW_UP / NEXT_MAIN / FINISH、RECOVERY_BLOCKED、账号切换与过期异步响应、Detail 的未完成面试防剧透、报告轮询／失败／不确定提交的 GET 优先恢复、401／403、管理员权限和上传状态。

Windows 沙箱首次运行 Vitest 时出现临时缓存 ENOENT。只在测试进程中设置 `TEMP` / `TMP` 为 Worktree 内目录后通过，未修改 Vite、Vitest 或业务配置。没有执行 Java Maven 回归，因为本轮没有后端实现修改，也没有调用 Embedding、Milvus 或收费模型。

## 实际浏览器验证

执行工具：本机 Google Chrome 155.0.8059.82 + 已安装的 Playwright；另用 Codex in-app browser 对实际 Vue 页面完成登录、继续面试、填写答案、提交、等待、评价关卡与焦点复核。所有交互均在本机隔离 Mock API，`PAID_MODEL_CALLS=0`。

前端：`http://127.0.0.1:25178`；Mock API：`http://127.0.0.1:28083`。不连接真实后端、数据库或外部模型。

自动化真实视口：1440×900、1024×768、390×844。每张截图记录 innerWidth、innerHeight、scrollWidth、文档标题；V3 另外检查可见 DOM 元素边界，避免根元素的 overflow-x 隐藏掩盖裁切。未聚焦的离屏 skip-link 按其无障碍用途排除。

最终自动化：**83 张 V3 截图、28 条汇总检查记录、0 个失败、0 个未捕获页面异常**。28 条为汇总项目，不冒充所有断言的总数量。另有 1 张 in-app browser 人工操作截图 `MOCK-manual-evaluation.png`，工具默认视口，仅作交互证据，不计入三尺寸自动验收。

| 已执行浏览器行为 | 证据 |
|---|---|
| 三种视口的登录、注册、开始／继续工作台、难度、作答、等待、评价、追问、恢复受阻、报告、历史、账号、题库、题目表单、知识上传 | before / after 同名截图；完整 viewport 日志 |
| 空答案禁用、提交等待时 readonly、刷新不自动 POST、原 requestId + 原答案精确重试 | QA 内联断言，三种视口均执行 |
| 评价时未来追问不可见、手动继续才出现追问题 | 三种视口的 gate / followup 截图与断言 |
| 评价末尾完整可见，修正区在 dock 上方 | 三种视口 feedback-bottom 截图与 boundingBox 断言 |
| USER 管理路由受限，ADMIN 能访问管理页 | 三种视口路由断言和管理页截图 |
| 手机菜单 Escape 关闭并恢复触发按钮焦点 | mobile-menu-390.png + activeElement 断言 |
| 工作台空、加载、失败；历史空；原生 radio 键盘选中；实际创建及刷新恢复 | 1440 / 390 补充截图与断言 |
| 报告 NOT_STARTED → GENERATING → FAILED → 显式重试 → READY；失败前不重复 POST，生成时没有假分数 | 1440 / 390 的 report-* 截图与请求计数 |
| 长连续技术词、长知识点、多行回答；下一题确认关卡 | 1440 / 390 的 session-long-answer + NEXT_MAIN 断言 |
| FINISH 关卡刷新后保留、显式确认后才进报告；401 返回登录 | 1440 / 390 的 finish-gate / auth-expired 截图与断言 |

账号切换和旧请求不覆盖新账号的详细竞争场景，本次证据为已通过的现有单元／组件测试，未冒充真实跨账号后端集成。

新增／刷新与报告状态补充用例使用 Playwright 的本地响应夹具增强已有 Mock 的状态覆盖；没有改生产 API，也没有把 Mock 分数写进产品源码。截图统一包含 `VISUAL QA · MOCK API` 标记；人工截图在文件名与本报告中标注 Mock。

## 截图与审查入口

`v3/COMPARISON.html` 提供 48 张改版前与 83 张改版后截图的交互对照。此前进度中“51 张基线”是清点前的估算，最终以日志中的 **48 张** 为准。48 个同名对照采用相同夹具和视口；其余为补充状态。V3 的截图并未使用生成式图像或设计稿替代实际浏览器。

原始日志：`v3/before/browser-log.json`、`v3/after/browser-log.json`、`v3/verification/`。源码审查：`v3/V3_SOURCE.patch`、`v3/change-manifest.json`、`V3_FILE_MANIFEST.md`。

本机截图对照入口：`http://127.0.0.1:25179/COMPARISON.html`。该地址只供本机预览；静态 HTML 和 PNG 可离线打开。没有部署或发布。

## 复现

在 Worktree 的 `frontend/` 中，使用现有 npm lockfile 安装依赖后执行四条工程命令。此环境运行 Vitest 时可仅为当前 PowerShell 设置：

```powershell
$env:TEMP = Join-Path (Get-Location) '.test-temp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force $env:TEMP | Out-Null
npm run typecheck
npm run lint
npm run test:unit
npm run build
```

Mock 进程设置 `PHASE_9B_MOCK_PORT=28083` 后运行 `node scripts/phase-9b-mock-api.mjs`。另一个终端设置 `AI_INTERVIEW_BACKEND_URL=http://127.0.0.1:28083`，运行 `npm run dev -- --host 127.0.0.1 --port 25178 --strictPort`。

QA 脚本的 Playwright 是环境工具依赖，不是项目新依赖。将 `BROWSER_NODE_MODULES` 指向本机已安装 Playwright 的 node_modules，随后执行：

```powershell
$env:V3_BASE_URL = 'http://127.0.0.1:25178'
$env:V3_MOCK_URL = 'http://127.0.0.1:28083'
$env:V3_OUTPUT = '../docs/frontend/v3/after'
$env:V3_EXTRA_CHECKS = 'true'
node scripts/v3-visual-check.mjs
```

脚本只接受 localhost / 127.0.0.1，默认不会触发远端真实服务。所有环境变量为当前进程用途，不持久化到系统或受 Git 管理的真实配置。

## GitHub 发布归档说明（2026-10-18）

上述截图数量与补丁路径描述原始完整交付包。公开仓库精选 16 张 V2、24 张 V3 和 5 张真实后端 / 本地模型截图；完整矩阵、第三方参考截图及两个重复源码补丁仍在本地原位保留。公开日志仅脱敏机器路径，结果、断言和失败记录不变。最终源码请审查 Git Diff，当前验收以 V3_BLOCKER_FIX_20261018.md 为准。
