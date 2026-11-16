# PR #12：两项 P1 兼容性收口与切换条件

> 后续状态：本页第 1–7 节保留首次 P1 处置时点；最终全量回归、源码提交/推送及 CI 结果见[第 8 节](#8-最终回归与发布记录2026-11-16后续任务)。原始 `verification.json` 不改写，最新回归证据为 [final-regression.json](final-regression.json)。

日期：2026-11-16（Asia/Shanghai）。执行：Codex；本人贡献和源码掌握未确认。
本地基线与实时远端候选均为 `1a425fa5f7939907c5d3468449936d44daa413fd`，分支 `codex/phase2-git-closeout`；对照 main `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`。
本页及代码是基线之上的未暂存、未提交、未推送候选；没有部署。

## 1. 两项结论

- **P1-1：ACCEPTED（经用户明确允许的 Breaking Change）。** 新版报告生成以异步基础设施为必要条件，默认仍关闭。不恢复同步生成，也不在 MQ 故障时同步降级。已修正不可用原因的 HTTP 分类和前端提示，但没有恢复旧默认报告生成能力。
- **P1-2：RESOLVED，仅指已授权本机目标的历史切换核对项。** `127.0.0.1:3308/ai_interview` 于 16:26:16 的一致快照中，GENERATING 和相关异常均为 **0**，属于情况 A。没有修复历史数据，没有实现历史任务自动恢复。其他目标及未来切换时点未验证，须重新核对。
- 本地修订通过本轮定向技术门禁；**远端 PR #12 尚不能直接合并**：修订仍在工作区，GitHub 上的 Draft/NO-GO 描述和旧 HEAD 未更改。需另行授权提交、推送，检查新 HEAD 的 CI，并补充 PR 描述后才能确认实际合并准入。合并准入不等于迁移或部署授权。
- MQ 维持 **CONDITIONAL GO**，本机受控作品集演示维持 **GO**，长期运行与公网发布维持 **NO-GO**；评分 v4、报告 v1 冻结。无性能改善或内容质量改善主张。

## 2. P1-1：默认关闭与接口契约

发现过程：PR 描述及此前未提交审查台账提出兼容性风险；本轮读取 PR 描述、评论与 review threads（后两者为空），并对照 main 源码确认。旧 `InterviewReportService.generateReportForApi → generateReport` 在 HTTP 请求内完成模型生成和最终事务，成功返回 READY。新版 `generateReportForApi → acceptReportTask` 只受理持久任务。

根因在 `InterviewReportTransactionService.acceptReportTask`：`ReportAsyncProperties.enabled` 和 `application.yaml` 都默认 false，旧同步路径已移除；未启用、暂停受理、生成器缺失原来都抛相同 `BusinessException`，被 `GlobalExceptionHandler` 映射为 400。前端 `classifyHttpStatus` 将其标为参数校验类；原报告页面凭同一消息识别服务不可用，无法区分原因。

实际影响：拥有有效 FINAL 数据的已完成会话，首次生成 NOT_STARTED 或显式重试 FAILED，在默认配置下无法生成新报告。READY 可读且可重复 POST；GENERATING 保留只读状态，不保证执行者存在。登录、题库等基础功能不需要因此开启 MQ。

候选方案及选择：恢复完整同步路径会引入两套领取、完成、超时和弱项幂等路径；MQ 失败时隐式降级可能与已持久任务竞争；默认打开 MQ 会把未核准的数据库和中间件依赖带入启动。按本轮用户决策均不采用。选择公开接受异步必需条件，保留默认关闭，用小范围异常类型和客户端处理准确表达拒绝。

最终接口（不改变 URL、成功 JSON 字段或成功 HTTP 状态码）：

- `POST /api/interviews/{sessionId}/report`：首次 NOT_STARTED 或显式 FAILED 重试成功受理为 **HTTP 200 / code 200 / GENERATING**，报告字段为 null；只有最终事务完成后，GET 才返回 READY 和报告内容。不是 202，也不能把 200 当作报告生成完成。
- 正常 GENERATING 的重复 POST 只返回当前状态，不新建 attempt；READY 的 GET/POST 返回既有报告，不再次生成或累计弱项。
- 关闭异步时，NOT_STARTED/FAILED 为 **HTTP 503 / code 503**，`报告生成服务未启用，请联系管理员`；READY 可读，GENERATING 仍返回持久状态，不查询任务表，因此不强制关闭模式先迁移任务表。
- 启用但 `accepting=false` 或生成器 Bean 缺失时，新任务返回 **HTTP 503 / code 503**，`报告生成服务暂不受理新请求，请稍后重试`。既有任务/报告可查询；暂停受理不停止已在执行的任务。
- 上述已知 503 表示本次没有受理，不附 Retry-After，不自动 POST 重试。Broker 暂时不可达不等于受理拒绝：已提交 Outbox 仍走既有有界派发及超时策略，绝不转同步执行。

客户端审查：当前前端所有未知 5xx 原本走 GET-first 不确定结果恢复。此次只在报告页面把 **精确 HTTP 503 + 两个固定消息** 识别为明确拒绝，保留 NOT_STARTED/FAILED、展示具体原因并停止轮询；保留旧 HTTP 400 + 原固定消息的兼容处理。未知 503、502、网络丢响应仍先 GET，不能误当拒绝后再次 POST。未全局更改 5xx 分类。

残余兼容风险：依赖旧同步 POST 立即拿到完整报告，或只处理 400 的其他客户端必须适配。前后端应配套发布；本轮没有穷举仓库外客户端。**ACCEPTED 不等于旧功能已修复。**

## 3. P1-2：历史 GENERATING 与核对证据

旧 `generateReport/claimReportGeneration` 有 stale POST CAS 接管；新 `acceptReportTask` 不接管历史 GENERATING，消费者和 `ReportOutboxDispatcher.expireDue` 只认识持久任务行，前端 `recoverGenerating` 只 GET。因此旧执行者崩溃留下 GENERATING 且没有新任务时，GET 可以长期返回 GENERATING。这是代码确认的可能性，不代表本机已存在该数据。

本轮用户明确批准两个精确目标：现有 `ai-interview-mac-mysql-1`（容器身份 `02ccbfd0e120`，宿主 127.0.0.1:3308）上的 `ai_interview` **仅只读核对**；已迁移 `ai_interview_test` **仅本轮合成测试数据写入/按生成主键清理**。使用现有非 root `ai_interview@%` 和专用 `ai_interview_test@%`，凭据来自既有容器配置或项目 `.env`，没有输出凭据、扩权、迁移、停服或连接 evaluation。

实际只读使用 MySQL 8.0.45 的 REPEATABLE READ 一致快照、READ ONLY 事务，[SQL](report-cutover-readonly.sql) 已在两个获准目标执行成功。演示库：

- 会话：READY 1；GENERATING 0。任务：SUCCEEDED/SENT 1；活跃 QUEUED/RUNNING 0。
- GENERATING 有报告、无活跃任务、无报告且无活跃任务、仅终态任务、失败终态但无活跃任务、多活跃任务、非 COMPLETED：各 **0**。
- 活跃任务与会话状态不匹配、所有权版本不匹配、执行版本非法、已过期活跃任务：各 **0**。
- READY 缺报告、已有报告但非 READY：各 **0**。没有用“无活跃任务”单项计数代替完整核对。
- 版本判断：QUEUED 对比 `session.version = attempt_version`；RUNNING 对比 `session.version = execution_version`，且 `execution_version = attempt_version + 1`。RUNNING 的 Session version 不应简单要求等于 attemptVersion。

选择：实际为情况 A，保留安全不接管策略，不为不存在的数据建立恢复框架。极小诊断改进仅包括 POST 的历史告警覆盖单个活跃任务但所有权版本不符，并记录 sessionVersion；前端将“系统正在后台生成”改为“当前记录为生成中”，长时间无变化提示联系管理员。GET 保持无副作用，不为了诊断新增锁、跨查询一致性逻辑或新状态枚举。

残余限制：该提示不是自动诊断；GET 仍可能长期返回异常 GENERATING，前端沿用原有轮询，没有新增自动 POST 或无限重试任务。无任务、终态未同步、多活跃任务、旧执行者存活时，均不能据等待时长自动重建任务。该风险通过逐目标切换门禁控制，不外推“任意旧数据库可平滑升级”。

## 4. 切换前操作说明

每个新目标须先明确实例、端口、Schema、账号权限及操作授权；不能从本次本机授权推定其他目标获批。

1. 在旧版本还运行时可先做只读排查。确认目标后查询任务表是否存在：

   ```sql
   SELECT DATABASE(), @@hostname, VERSION(), CURRENT_USER();
   SELECT COUNT(*) AS task_table_exists FROM information_schema.tables
   WHERE table_schema=DATABASE() AND table_name='interview_report_task';
   SELECT s.id, s.status, s.report_status, s.version, r.id AS report_id
   FROM interview_session s LEFT JOIN interview_report r ON r.session_id=s.id
   WHERE s.report_status='GENERATING'
      OR (s.report_status='READY' AND r.id IS NULL)
      OR (s.report_status<>'READY' AND r.id IS NOT NULL);
   ```

   未迁移目标先执行以上不依赖任务表的 SELECT；不能直接执行完整核对 SQL，不能把“表不存在”当成自动建表授权。
2. 真正切换前，在获准停服范围内停止旧同步写入口及旧执行者，等待排空，确认没有旧模型请求或最终事务在途。取得明确目标的备份及散列/恢复依据；**本轮没有停旧执行者，也没有执行切换**。本轮快照只证明核对时点。
3. 对已经迁移的目标执行 [report-cutover-readonly.sql](report-cutover-readonly.sql)，保存目标、时间和输出。活跃/异常状态必须逐项解释，不能仅因脚本退出 0 就判定可切换；本轮采用切换前无在途任务、无未处置异常的准入条件。
4. 新目标任务表缺失时，按独立批准的 [既有迁移](../../../sql/migrations/20261114_interview_report_task.sql) 执行一次；不得修改 Schema、重复建表或自动修复数据。本机已存在任务表，**不要再次执行迁移**。
5. 确认数据库 FINAL/报告一致性、任务表约束和索引、现有配置的 MQ NameServer/Broker/Topic/ConsumerGroup 可达且正确、模型生成器配置可用、隔离目标正确，以及消费者/派发日志可观测。配置 `REPORT_ASYNC_ENABLED=true` 不是健康检查；缺少目标准备时保持 false。
6. 完成以上条件后，按单独部署授权启用异步和受理。可先用 `REPORT_ASYNC_ACCEPTING=false` 暂停新申请；注意 enabled=true 已允许扫描和消费既有任务。需隔离验证时只能用获准合成会话，核对一次受理、GET 到终态、READY 重读和重复 POST 的报告/弱项计数。此次不重复真实模型或 MQ 测试。
7. 回退先停受理、排空并核对执行者与任务；关闭 enabled 不会恢复同步能力，也不能解除旧消息/任务所有权。保留任务表与数据，回退和历史数据操作另行审批。

若其他目标发现情况 B，先暂停其切换，按以下顺序准备具体处置并另取该目标的数据修改授权（此处没有可直接执行的 UPDATE/DELETE/补任务脚本）：

- **有报告但非 READY**：校验唯一报告、FINAL 总分及弱项是否已累计；不得再生成报告或补做无法证明未执行过的弱项累计。
- **无报告且无活跃任务**：确认旧执行者已停止、备份有效、明确 Session/version；再审查一次性的状态处理或新 attempt 方案，不能因为超时就自动接管。
- **任务终态但 Session 未同步**：联合核对终态原因、报告行、attempt/executionVersion 和 FINAL，禁止绕过事务只修改其中一方。SUCCEEDED 也不能直接推出报告与弱项完整。
- **旧执行者可能存活、多活跃任务或版本不符**：先证明执行所有权并隔离旧执行者；没有证明前不修复、不重发。任何方案都须证明报告唯一键、版本隔离和弱项不重放。

## 5. 修改清单与必要性

生产改动共 5 个文件：

- `src/main/java/com/kun/aiinterview/common/exception/ServiceUnavailableException.java`（新增）：表达受理前确定的服务拒绝。
- `src/main/java/com/kun/aiinterview/common/exception/GlobalExceptionHandler.java`：为该类型映射 HTTP/code 503，不改变其他异常映射。
- `src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java`：区分未启用与暂不受理；扩充历史所有权不一致日志。受理/完成事务、CAS、报告与弱项写入顺序不变。
- `frontend/src/features/interview/composables/useInterviewReport.ts`：精确识别两种拒绝，保留未知 5xx 的 GET-first 恢复。
- `frontend/src/features/interview/copy/reportCopy.ts`：区分两种拒绝文案，避免对仅有 GENERATING 状态过度承诺后台在执行。

测试改动共 4 个文件：`ReportTaskTransactionTest`（关闭四状态及暂不受理）、`InterviewControllerTest`（503 映射及 200/GENERATING）、`InterviewReportTransactionServiceIntegrationTest`（真实 MySQL 状态、回滚、幂等与异常历史不接管）、`frontend/tests/InterviewReportView.spec.ts`（拒绝显示、未知 503、长期 GENERATING 提示）。Java 测试位于 `src/test/java/com/kun/aiinterview/interview/` 的 service/controller 包。

文档：`docs/GETTING_STARTED.md` 增加接口与启用门禁入口；本目录新增本页、只读 SQL 和 `verification.json`；四份工程档案只在原文后追加。`InterviewReportService`、`ReportAsyncProperties`、`application.yaml`、Mapper、迁移和 Prompt 均只读未改。

## 6. 实际验证与证据边界

环境：运行工具实测 macOS 26.7.1/aarch64（用户环境说明中的 26.6 是先前描述），Temurin Java 21.0.13、Maven Wrapper 3.9.16、Spring Boot 3.5.16、MyBatis starter 3.0.5、MySQL 8.0.45；Node 24.22.1、npm 11.19.1、Vitest 5.0.1。源码为上述 HEAD 加本批差异，文件散列及逐类数量见 [verification.json](verification.json)。

- 后端本次一个定向 Maven 运行：**总数 200，通过 200，失败 0，错误 0，跳过 0**。其中 Unit/Mock/配置 106，MockMvc 35，真实 MySQL/MyBatis/事务 59，彼此不重复；真实数据库仅 ai_interview_test。含原有最终事务回滚、并发重复 POST、旧执行者迟到、既有报告不再累计弱项的回归。
- 前端报告页：**总数 65，通过 65，失败 0，错误 0，跳过 0**；typecheck 与定向 ESLint 退出 0。为 Vitest/DOM 模拟，不是本轮真实浏览器端到端。
- 测试库涉及的 9 张表，测试前后计数均为 0；fixture 使用唯一用户和生成主键、按关联清理本轮行，没有 TRUNCATE、历史 UPDATE 或额外清理。清理后活跃任务和异常均 0。
- 本轮未运行全量 Maven/前端、打包部署、真实 MQ、DeepSeek、Embedding、Milvus 或 evaluation。所有 RUN_REAL_* 和 MILVUS_ENABLED 在 Maven 子进程清除；生成器为替身，模型调用数 0。定向未包含 guarded Smoke，**0 跳过不代表那些测试已通过**；不重复冒用历史全量 `1950/0/0/34` 或历史前端 349 的结果。
- 环境故障：首次普通沙箱 `docker ps` 访问 socket 被拒绝，随后在已获准范围使用权限提升完成只读核验；不是业务/测试失败，没有自动审批拒绝、凭据扩权或安全检查降级。本批测试首轮全部通过，无失败测试被删除或跳过。

实际命令（凭据只由已有本机配置进入子进程，不写入命令或此页）：

```text
python3 tools/m1b/run-tests.py --approved-target 127.0.0.1:3308/ai_interview_test --tests ReportTaskTransactionTest,InterviewReportServiceTest,InterviewReportApiServiceTest,InterviewReportTransactionServiceTest,InterviewReportTransactionServiceIntegrationTest,ReportTaskMapperIntegrationTest,InterviewReportFinalConsistencyIntegrationTest,ReportAsyncConfigurationTest,InterviewControllerTest,HttpErrorTaxonomyControllerTest,ReportOutboxWakeupTest,ReportMessageConsumerTest,ReportOutboxDispatcherTest
npm run test:unit -- tests/InterviewReportView.spec.ts
npm run typecheck
npx eslint src/features/interview/composables/useInterviewReport.ts src/features/interview/copy/reportCopy.ts tests/InterviewReportView.spec.ts
```

三条前端命令在 `frontend/` 执行。上述 Maven 启动器是既有未提交工具，本轮只读取和复用，未将其纳入修改；等价核心命令是 Maven Wrapper 的 `-B -ntp -Dtest=<同一列表> test`，必须事先配置获准测试目标并清除外部服务开关。

本机原始证据：`/Users/kun/Developer/projects/ai-interview-system/backups/PR12_P1_20261116/`，保留 Maven/前端日志、13 份 Surefire XML、只读结果、原有改动快照与完整性检查。XML 可能含运行属性，不随仓库发布；公开 JSON 只记录脱敏统计及散列。入场 68 个已有 dirty 文件保留；四份工程档案原内容逐字保留，其他 64 个文件正文与 mode 保持不变。

## 7. PR 审查补充（待发布，不代替 GitHub 实际状态）

在修订获准推送后，PR 描述应保留最初发现过程，并增加：P1-1 经明确决策接受异步 Breaking Change，默认 false、无同步回退，两种拒绝已改 503 并适配前端；P1-2 已授权本机 ai_interview 只读核对异常 0，其他目标必须逐库核对，本轮没有数据修复；定向后端 200/0/0/0、前端 65/0/0/0，真实 MySQL 59 项且无真实 MQ/模型；修订提交 SHA、该 HEAD 的 CI 和仍未实施的部署步骤须据实补齐。

此前 PR 审查台账及四份未提交记录完整保留。此次未向 GitHub 发送评论、改描述或关闭 review thread，未暂存、提交、推送、合并或正式部署。

## 8. 最终回归与发布记录（2026-11-16，后续任务）

用户随后明确授权审查、全量回归、逐文件暂存、普通提交/推送及更新 Draft PR；没有授权合并或部署。17 个修订文件与首轮清单逐项核对，源码/测试散列完全一致，未发现越界改动或需要新增业务修复的问题。

### 当前源码与全量结果

代码/测试普通提交 **`9a95e53b13fc77be27962696202dc14169f18248`**，父提交为原候选 `1a425fa5f7939907c5d3468449936d44daa413fd`，恰好包含第 5 节的 5 个生产文件和 4 个测试文件。已正常推送到 `codex/phase2-git-closeout`，GitHub 实时读取 PR head 与该 SHA 相同；没有 force push、rebase 或合并。之后的文档提交只归档事实，最终文档 HEAD 的 SHA/远端/Checks 由 PR 描述与本机发布证据记录，不在文档中预填自己的 SHA。

- **后端全量及 package：PASS。** 本轮执行 `./mvnw -B -ntp package`，包含完整 test 生命周期，没有 `-Dtest`、skipTests 或缩小集合。151 类，**总数 1971，通过 1937，失败 0，错误 0，跳过 34**，退出 0；Spring Boot 重打包成功。运行于 16:48:44–16:49:44（Asia/Shanghai）。上轮 200 项定向集合在本轮全量内全部再次通过，不能与 1971 相加。
- **前端全量：PASS。** `npm run test:unit`：37 文件，**总数 355，通过 355，失败 0，错误 0，跳过 0**；报告页 65 项包含其中。`npm run typecheck`、`npm run lint`、`npm run build` 均退出 0。Node 24.22.1/npm 11.19.1/Vitest 5.0.1；没有把上轮定向当作本轮全量。
- **真实 MySQL 隔离：PASS。** 仅用获准 `127.0.0.1:3308/ai_interview_test` 和既有专用账号。执行前 14 表均为空，执行后精确计数仍均为 0，永久 Schema（排除预期自增计数）相同。已审查清理条件；现有迁移回归使用连接内 TEMPORARY 副本，不更改永久业务表。本轮没有连接演示业务库或 evaluation。
- **源码提交 GitHub Actions：PASS。** [运行 43834763837 / job 133188536470](https://github.com/kun520c/ai-interview-system/actions/runs/43834763837/job/133188536470)，checkout 的 source head 为上述提交；Node 22.23.4 下 typecheck、lint、Vitest **355/0/0/0** 和 build 全通过，Commit Status 无条目。GitHub 仍只有前端工作流，后端全量是本地证据，不冒称后端 CI。
- 34 项跳过分别为 Embedding 1、Knowledge DB 10、Knowledge failure 2、Knowledge pipeline 2、Milvus 1、Milvus schema 2、Report MQ 4、Report crash probe 11、AI baseline 1；所有 RUN_REAL_* 与 MILVUS_ENABLED 在 Maven 子进程清除。没有付费模型、真实 MQ/Milvus/Embedding 或 evaluation 调用。初轮即通过，无失败测试被删除、放宽或临时排除。

### 最终兼容性审查

1. 默认 `enabled=false` 是安全设计；关闭时无同步回退，P1-1 **ACCEPTED Breaking Change**，503 原因与文档一致。
2. 精确已知 503 保留 NOT_STARTED/FAILED 并展示原因，不展示成功报告、不自动 POST；未知 503/502/网络错误只 GET 确认，不无限自动重复提交。已有 GET 轮询、READY 展示、切换会话防旧响应覆盖的全量测试通过。
3. 正常 QUEUED/RUNNING 重复 POST 幂等，历史无任务/仅终态/多活跃/版本不符不被自动接管；既有报告、弱项和最终事务的回滚及幂等测试通过。没有增加报告写入或弱项累计路径。
4. P1-2 **RESOLVED 仅指本机已授权目标 16:26:16 的只读对账项**。本轮复用原始快照，不虚称重新核对业务库；其他数据库与未来切换时点均须重复核对。已有异常时仍需停旧执行者、明确目标及备份，另取数据修改授权。
5. **新增 P0／未接受 P1：未发现。MERGE GO 建议**以最终推送 HEAD 的 Checks 通过为前提；可转 Ready for Review，本任务按要求保留 Draft，由用户决定实际状态及合并。此结论只针对代码合并，不是部署准入。

保留的 P2/限制：后端无 GitHub CI；main 既有固定测试口令 smoke 的其他环境运行情况未知，若曾用于非隔离目标须另行核查；仓库外客户端尚需适配已接受的契约变化。这些不是本轮新引入且未被隐瞒的缺陷。MQ **CONDITIONAL GO**、AI 内容质量局限、所有负面实验、长期/公网 **NO-GO** 均不变。不新增业务优化任务。

### 归档与提交范围

本轮本机原件位于 `/Users/kun/Developer/projects/ai-interview-system/backups/PR12_FINAL_20261116/`：完整 Maven/package 日志、151 份 Surefire XML、前端各项日志、14 表前后状态/Schema、Git 入场快照、推送输出、GitHub job/PR 证据。公开 [final-regression.json](final-regression.json) 记录脱敏计数、代码散列、构建散列、源码 SHA 及证据散列；原 `verification.json` 和首轮目录完整保留，未上传 JAR、原始 XML、配置、用户回答或私有材料。

两个提交的范围为 9 个代码/测试文件，以及 9 个文档/证据文件（原 17 项中的 8 项文档，加 `final-regression.json`）。四份工程档案保留旧 PR 审查和 P1 记录并追加本轮结果，不改写历史；其余原有 64 个 dirty 文件保持内容和 mode，不纳入提交。实际提交文件清单可由两个普通提交的 `git show --name-only` 核对。

- 发布前安全检查补记：精确本机配置值扫描首轮命中 GETTING_STARTED 中的既有禁用示例占位值；核对原 HEAD 与当前均仅出现 1 次，未新增真实凭据。原始命中与脱敏核查存于本轮 credential-scan-triage.json。保持其他精确值与凭据模式检查，不将占位值误报为泄露，也不将这次扫描中断计作测试失败。
