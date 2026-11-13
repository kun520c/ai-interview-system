# B4-E：面试报告 GENERATING 人工恢复入口验收

执行日期：2026-11-13（Asia/Shanghai）。项目：`/Users/kun/Developer/projects/ai-interview-system`。

本轮完成最小前端修复：GENERATING 页面提供“检查并尝试恢复报告”，显式调用既有 POST；正常等待仍只轮询 GET。恢复资格由后端数据库状态和 version CAS 决定。前端专项 56 项、全量 317 项、后端定向 123 项通过；typecheck、ESLint、build 和 diff 检查通过。没有修改后端生产代码、API、Schema、模型、评分、追问、FINAL-only 或事务规则。未暂存、提交或推送。

## 1. 原问题与修复前证据

已读取 AGENTS、两份工程台账及 B3、B4-A～B4-D/C3 验收记录，并以当前工作区源码复核结论。

原 `useInterviewReport.generate()` 只接受 NOT_STARTED/FAILED；GENERATING 的 `startPolling()` 每 3 秒调用 `refreshReport()`，只有 GET。页面仅展示等待文案，没有恢复按钮。后端 stale 接管仅可由 POST 触发，因此崩溃遗留的 GENERATING 即使已经过期，也不会因页面反复查询而恢复。

在生产修改前新增 `keeps long-running GENERATING polling read-only and offers explicit recovery`：使用 fake timers 和固定 GENERATING 响应，推进 **220 个轮询间隔，即模拟 11 分钟**。断言实际 GET **221** 次（首次查询加轮询）、POST **0** 次、仍是 GENERATING、没有 FAILED/READY；最后要求恢复按钮存在，失败于 `expected undefined to be defined`。原有 32 项通过，新契约 1 项失败；本轮 **T/F/E/S = 33/1/0/0，退出 1**。

证据：`/private/tmp/ai-b4e-20261113/pre-fix-report.log`。这是 Vue/jsdom + API Mock 的前端复现，不是实际等待 11 分钟，也不是模拟真实服务器进程崩溃。GET 无接管副作用另由后端源码、既有 API 服务测试及 CAS 回归支持，不能从前端 Mock 单独推断真实数据库行为。

## 2. 前后端完整调用链

### 原始查询与生成

1. 路由 `sessionId` → `bootstrap()` → GET Detail，校验 COMPLETED 与页面作用域；活动面试仍重定向，缺失/不可访问仍使用既有提示。
2. `fetchInterviewReport()` → `apiGet(endpoints.interviewReport(sessionId))` → `InterviewController.getReport()` → `InterviewReportService.getReport()` → 查询并校验用户归属；非 READY 返回状态，READY 读取已有报告。没有 claim/reclaim、模型调用或状态写入。
3. 原生成/失败重试：按钮 → `generate()` → `generateInterviewReport()` → `apiPost()` → `InterviewController.generateReport()` → `generateReportForApi()` → `generateReport()`。

### 既有后端 POST 合同（本轮未改）

- 身份来自 `AuthenticatedUser`；`loadOwnedSession()` 校验归属，`validateCompleted()` 校验会话。READY 直接 `requireExistingReport()`，不重新生成。
- NOT_STARTED/FAILED 通过 `claimReportGeneration()` 竞争；GENERATING 先调用 `StaleRecoveryProperties.isGeneratingStale(session.updatedAt)`。默认阈值 **10 分钟**、最低允许配置 1 分钟，cutoff 为后端当前时间减阈值。依据是读取自数据库的 `updated_at`，与浏览器进入页面的时间无关。
- fresh GENERATING 抛 `ConflictException`，HTTP 409。stale 调用 `InterviewSessionMapper.reclaimStaleGenerating()`；XML 的 WHERE 同时检查 id、COMPLETED、GENERATING、`updated_at <= cutoff`、expectedVersion。成功更新 `version + 1` 和 `updated_at`，状态仍是 GENERATING。CAS 失利重读：READY 返回已有报告，其余返回 409。
- CAS 胜者持有原 version 加 1 的 attemptVersion，不重读新 token。`existingReportOrContinue()` 在已有报告行时验证 FINAL 来源并恢复 READY，不调用生成器、不重复更新 Weakness。
- 无报告行：`listFinalEffectiveEvaluationsBySessionId()` → Java 完整 FINAL/归属校验 → Java 平均分 → `InterviewReportGenerator` / `DeepSeekInterviewReportGenerator` → 短事务 `completeReportGeneration()` 原子写 Report、Weakness 和 version CAS READY。外部模型调用仍在事务外。
- 异常沿既有 `recoverReportFailure()` / `markReportFailed()` 条件回写；旧 version 无权覆盖新任务。HTTP 失败不代表未发生状态变更，前端必须再查询。

### 新人工恢复链

GENERATING 按钮 → `recoverGenerating()` → 共享 `submitReport(GENERATING)` → 同一个 POST → `adoptReport()`；409/网络超时/5xx → `handleGenerateFailure()` → `recoverAfterMutationFailure()` 的一次 GET → `adoptReport()` 或 RECOVERY_UNKNOWN。此处没有新接口、新请求字段或后台任务。

## 3. 实际修改文件及取舍

本轮修改 6 个已有文件、新增 1 个报告。两份工程文档入场已是未跟踪文件，只定向更新，不当作本轮新建。

- `/Users/kun/Developer/projects/ai-interview-system/frontend/src/features/interview/composables/useInterviewReport.ts`：新增 `canRecoverGenerating/recoverGenerating/submitReport`，共享单 POST 与结果处理；`ReportSubmissionStatus` 包含 GENERATING，替代只支持 NOT_STARTED/FAILED 的跨请求可变恢复状态。409 用确认 GET；GENERATING 下服务不可用也先查真实状态，因为后端可能已经 claim 并回写 FAILED。`realignFromDetail()` 在提交期间同样走确认 GET。
- `/Users/kun/Developer/projects/ai-interview-system/frontend/src/features/interview/views/InterviewReportView.vue`：在 GENERATING 卡片显示恢复按钮、说明及 disabled/loading 状态，复用 BaseButton。其他报告内容和样式保持原状。
- `/Users/kun/Developer/projects/ai-interview-system/frontend/src/features/interview/copy/reportCopy.ts`：增加按钮、恢复边界和 409 友好提示。说明正常生成会拒绝重复执行、过期由服务器判断、点击不保证重生成或立即完成。
- `/Users/kun/Developer/projects/ai-interview-system/frontend/tests/InterviewReportView.spec.ts`：原 32 项保留，新增 24 项；使用 fake timers、deferred Promise、内存路由，以及 Axios adapter 合成 401。启用测试结束自动卸载，避免跨用例遗留轮询。
- `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/IMPROVEMENT_LEDGER.md`：将 B4-E 从占位转为本轮实施记录，补齐根因、调用链、测试与 Git 状态。
- `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/SOURCE_LEARNING_BACKLOG.md`：将 B4-E 占位拆为 7 条学习任务，全部“待学习”。
- `/Users/kun/Developer/projects/ai-interview-system/docs/verification/B4E_REPORT_STALE_RECOVERY_20261113.md`：本验收报告。

已核对但未改动 `interviewApi.ts`、`reportPageState.ts`，继续使用既有 API 和 GENERATING_POST/RECOVERY_UNKNOWN。没有引入状态管理框架、@Async、调度器、Redis、MQ 或任务表。

提交开始时递增既有 epoch，并暂停 GET 轮询；已发出的旧 GET 即使在 POST 完成后返回，也不能覆盖新状态。确认 GET 属于同一提交操作，`posting` 持续到确认结束，按钮和函数守卫共同防止连续点击产生并行 POST。确认 GENERATING 后恢复原 3 秒 GET 轮询，READY/FAILED/未知结果停止轮询。取舍是 POST 等待期间暂停轮询，用 POST 结果及必要的一次 GET 对齐；没有并发轮询持续覆盖提交结果的问题，也没有自动重发 POST。

## 4. 主要状态变化及覆盖

这里的 GENERATING_POST 是页面请求状态，不是新增数据库状态。

1. NOT_STARTED / FAILED → 用户点击 → GENERATING_POST → 采用服务端 READY；原生成/重试功能保留。
2. GENERATING → 自动计时 → GET GENERATING → 继续等待。模拟 11 分钟也不主动 POST、不伪造 FAILED/READY。
3. GENERATING → 用户点击一次恢复 → GENERATING_POST。请求未结束时 disabled/loading；同一渲染周期连续点击也只有 1 次 POST。
4. fresh/竞争失利 409 → GET GENERATING → GENERATING + 友好提示 → 只恢复 GET 轮询；若确认 GET 返回 READY，则展示既有报告并停止轮询。
5. stale 恢复 POST 返回 READY → 直接展示报告。前端 Mock 只证明消费 READY；实际过期判断、接管与已有结果复用由后端服务及真实 MySQL CAS 测试验证。
6. POST 超时或 502 → 先 GET。READY 展示已有报告；FAILED 展示显式重试；GENERATING 继续轮询。没有自动补发第二次 POST。
7. POST 409/超时/服务不可用后确认 GET 也失败 → RECOVERY_UNKNOWN，仅允许刷新状态；成功刷新为 GENERATING 后才重新开放恢复入口。
8. GENERATING 恢复时报服务不可用 → GET 实际 GENERATING/FAILED/READY；不凭旧恢复状态退回 NOT_STARTED 或 FAILED，不把 READY 覆盖为生成中。
9. 提交前已发出的 GET 晚到 GENERATING 或 404 → epoch 不匹配，忽略；旧 POST/确认 GET 在路由换会话后返回同样忽略。
10. 卸载后旧恢复 POST 成功或失败均不能启动轮询/确认 GET。401 仍走真实 Axios 拦截器和全局登录重定向；POST/确认 GET 404 使用原缺失记录页面。READY 完整/异常内容、Detail 守卫与时间线的原用例保留。

## 5. 实际测试命令、结果与环境

T/F/E/S = Tests/Failures/Errors/Skipped。各轮重叠，不能相加称为独立用例总量。Vitest 错误数按运行是否报告未处理异常计；本轮这些运行均无未处理异常。

前端工作目录：`/Users/kun/Developer/projects/ai-interview-system/frontend`。实测 Node **24.22.1**、npm **11.19.1**、Vitest **5.0.1**，未安装/升级依赖。

```sh
npm run test:unit -- tests/InterviewReportView.spec.ts
npm run test:unit
npm run typecheck
npm run lint
npm run build
```

运行历史及日志目录 `/private/tmp/ai-b4e-20261113/`：

- `pre-fix-report.log`：**33/1/0/0**，退出 1；预期发现缺少恢复按钮，前 32 项通过。
- `focused-report.log`：**54/0/0/0**，退出 0；首批恢复、并发与作用域用例通过。
- `focused-report-auth.log`：**56/2/0/0**，退出 1；新增两个 401 用例只 flushPromises 就断言懒加载路由完成，断言过早。未改认证生产代码，改成有界 `vi.waitFor()` 等待真实路由结果。
- `focused-report-auth-rerun.log`：**56/0/0/0**，退出 0；最终专项通过。
- `full-frontend.log`：**317/0/0/0**，36 个测试文件，退出 0；含全部 56 项报告测试及既有认证、路由、面试、管理页面回归。
- `typecheck.log`、`lint.log`、`build.log`：退出均 0；没有测试计数。构建产物位于 `/Users/kun/Developer/projects/ai-interview-system/frontend/dist/`（ignored），未发布。

后端工作目录为仓库根。实测 macOS **26.7.1 ARM64**、Java **21.0.13**；本轮专用 mysql:8.0.45 ARM64 容器 `ai-interview-b4e-20261113-mysql`，127.0.0.1:13327，`/var/lib/mysql` tmpfs，无持久数据卷。只初始化 `ai_interview_test` 的 13 表及系统库；Schema 来自现有 SQL 的临时库名替换，仓库 SQL 未改。

执行器 `/private/tmp/ai-b4e-20261113/run_maven.py` 清除 RUN_REAL_*、MILVUS_*、DEEPSEEK_*、SPRING_* 和旧 DB/JVM/Maven 覆盖，注入该临时库与 Mockito javaagent，设置 DEEPSEEK_ENABLED=false。测试 profile 保持 test 或 local,test，milvus.enabled=false；EnvironmentIsolationInitializer 与 Smoke 双门禁不变。实际 Maven 子命令：

```sh
sh mvnw -B -ntp -Dtest=InterviewReportServiceTest,InterviewReportApiServiceTest,InterviewReportTransactionServiceTest,InterviewReportTransactionServiceIntegrationTest,InterviewReportFinalConsistencyIntegrationTest,InterviewSessionMapperTest,InterviewReportMapperTest,StaleRecoveryPropertiesTest,StaleRecoveryCasIntegrationTest,InterviewControllerTest,HttpErrorTaxonomyControllerTest test
```

**123/0/0/0，退出 0，BUILD SUCCESS**。实际匹配 10 个测试类：Unit/配置/Mockito 共 43 项（Service 24、ApiService 9、TransactionService 6、StaleRecoveryProperties 4）；MockMvc 共 30 项（Controller 16、HttpErrorTaxonomy 14）；真实隔离 MySQL/MyBatis/事务/CAS 共 50 项（SessionMapper 14、ReportTransactionIntegration 9、ReportFinalConsistencyIntegration 24、StaleRecoveryCasIntegration 3）。命令中 `InterviewReportMapperTest` 没有同名测试类，未产生运行记录、不计为通过；报告插入、生成主键及回滚由上述事务集成测试覆盖。

证据为 `backend-report.log`、`backend-report-summary.json`、`backend-report-suites.json` 与 `backend-report-reports/`。旧 Surefire 输出先归档到 `backend-report-previous-reports/`，没有把历史测试算作本轮。临时库与口令在验证后按名称、用途标签和 tmpfs 核验后清理，结果见 `cleanup.json`；未操作已有容器或数据卷。

本轮未修改任何后端生产或测试文件，影响范围为前端调用已有报告 API；上述定向验证覆盖其读取、恢复、权限、CAS、事务和 FINAL 约束。因此 **没有重跑全部 Maven 回归**，也未重复后端 package。B4-D 历史 1732 项回归不是本轮结果。定向选集无 opt-in 测试，Skipped=0 不表示那些真实外部 Smoke 已运行。

`git diff --check`、`git diff --cached --check` 退出 0；另检查三个 Markdown 文件的行尾空白、冲突标记及本地链接。源文件定向复核无 API/配置/依赖变更。

## 6. 未执行事项、兼容性和遗留风险

- **未执行真实浏览器 E2E**、浏览器到运行中 Spring 服务的完整请求链、服务器崩溃后真实恢复、真实网络丢响应故障。Vue/jsdom、内存路由和 MockMvc 均不能替代这些验证。
- **未调用真实 DeepSeek、Embedding、Milvus**，没有费用、模型质量、实际恢复耗时、并发容量或性能改善结论。真实 MySQL/CAS 与模型替身应分开理解。
- stale 是后端现有时间阈值政策，不能证明旧外部任务已停止；CAS 保护数据库提交资格，不保证供应商只调用/计费一次。人工恢复保留这一既有边界，不创建新任务系统。
- 单页面防并行 POST 不等于跨标签页互斥；多客户端仍靠后端 CAS。离开页面使旧响应失效，不代表取消了服务器任务。
- GET/POST 返回结构、HTTP 状态、默认阈值、三秒轮询与 120 秒客户端超时保持不变。恢复按钮可能遇到正常 409；页面不预判 stale、不展示伪倒计时。
- 明确服务不可用后确认 GET 返回真实 FAILED 时允许显式重试，这是后端真实状态而非前端回退。结果无法确认时仍只查询，不自动生成。
- 未发现需要扩大为后端生产修复的独立缺陷。本轮完成后停止，不开展后续编号任务。

## 7. Git 状态与原有修改保护

- 分支 `main`，HEAD 始终 `01ac774caff491b978dd2268e8b427355a64b97a`。
- B4-A 本地提交 `004e434d3c1352c15d022bc1bf08abdfb02f4a02`；B4-B `f764c087f75d6b515b09bf2a0aef3f1d9a20c906`；B4-C/C3 为当前 HEAD。
- `git ls-remote` 沙箱内 DNS 失败，允许网络环境下 SSH 公钥认证失败；随后 GitHub 连接器 `fetch_commit(main)` 于 2026-11-13 只读核实远端 main 为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，与本地 origin/main 一致。祖先关系核对后，本地领先 3 个提交；B4-A～C 尚未进入本次核实的远端 main，不推断其他分支状态。
- 入场 19 个变动文件及 index 的 SHA-256/mode 记录于 `entry-manifest.json`。除按授权更新两份台账外，17 个既有文件及 index 保持不变：包括 B4-D 生产/测试/验收、AGENTS、mvnw 0755、Compose、MQ 文档及两处 .DS_Store。保护结果见 `preservation-check.json`。
- 本轮 4 个前端文件修改、两份已有未跟踪台账更新、1 份新验收报告。工作区总计 24 个变动路径，包含入场改动；不能全部归为 B4-E。
- 暂存区为空；B4-D 与 B4-E 均无本轮提交 SHA、未推送。未执行 add、commit、push、reset、clean、stash、rebase、fetch 或历史改写。

## 8. 约 13 分钟源码学习路径

1. **0～2 分钟：页面与入口。** 阅读 [View](../../frontend/src/features/interview/views/InterviewReportView.vue) 的 generating 分支及 [composable](../../frontend/src/features/interview/composables/useInterviewReport.ts) 的 `generate/recoverGenerating/submitReport`。解释按钮显示、disabled 与同步 posting 守卫各保护什么。
2. **2～4 分钟：查询与变更。** 阅读 [interviewApi](../../frontend/src/features/interview/api/interviewApi.ts) 和 [Controller](../../src/main/java/com/kun/aiinterview/interview/controller/InterviewController.java) 两个 report 方法。沿 `getReport/generateReport` 说明为何轮询 11 分钟不触发接管。
3. **4～6 分钟：过期与 CAS。** 阅读 [StaleRecoveryProperties](../../src/main/java/com/kun/aiinterview/common/recovery/StaleRecoveryProperties.java)、[ReportService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java) 和 [Session XML](../../src/main/resources/mapper/InterviewSessionMapper.xml) 的 reclaim。说明 updated_at、cutoff、version、单胜者及旧任务提交资格。
4. **6～8 分钟：超时结果不确定。** 阅读 `handleGenerateFailure/recoverAfterMutationFailure/adoptReport`。手推 POST 超时→GET READY，以及 POST 409→GET 失败；解释为何不能自动补发 POST、为何服务不可用不能总退回原状态。
5. **8～10 分钟：异步作用域。** 阅读 `epoch/isCurrentScope/refreshReport/bootstrap/onUnmounted`。解释同会话旧 GET 为什么也必须失效，以及旧 POST finally 为什么不能解除新会话的 busy 状态。
6. **10～13 分钟：确定性测试。** 阅读 [专项测试](../../frontend/tests/InterviewReportView.spec.ts) 的 long-running、pre-recovery poll、route/unmount、401 用例和 [deferred](../../frontend/tests/helpers/deferred.ts)。区分 fake timers、Promise 完成顺序、懒加载路由等待与真实时间；再对照 [StaleRecoveryCasIntegrationTest](../../src/test/java/com/kun/aiinterview/common/recovery/StaleRecoveryCasIntegrationTest.java) 的数据库并发单胜者断言。

对应 L-B4E-01～07 均为 **待学习**。本报告和测试通过不构成开发者已掌握的证据；后续仅根据本人讲解、复现或独立修改记录更新学习状态。
