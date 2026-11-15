# M1-A：RocketMQ 异步报告最小架构设计

日期：2026-11-14（Asia/Shanghai）。状态：**设计与源码审查；已完成独立 MQ 最小收发探针，未实施业务或迁移**。本轮不改变 v4、评分、题库或 Q2 未通过候选。项目基线及其他未提交改动见工程台账。本设计服务于 M1-B 的可审查范围，不构成 Schema 变更授权。

## 1. 已有事实和边界

- [ReportService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java)：GET 先校验归属；READY 读取原报告。同步 POST 校验归属和 COMPLETED，READY 复用；其他状态 CAS 领取 GENERATING 后才完整验证 FINAL、主问题/追问/答案归属和顺序，Java 算术平均，再调用生成器。已有非 READY 报告行的恢复也须验证 FINAL。
- [Session XML](../../src/main/resources/mapper/InterviewSessionMapper.xml) 的 NOT_STARTED/FAILED claim 以及旧 GENERATING reclaim 和 READY/FAILED 都以 version 条件更新。现有 stale 使用 Session `updated_at`，默认 10 分钟；它没有区分排队和真实执行。排队超过阈值时旧人工恢复 POST 会错误接管，导致排队消息晚到后重复执行。因此异步模式必须停用旧式 `updated_at` 直接 reclaim，并分别管理排队和执行截止时间。
- [ReportTransactionService](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java) 在一个事务内 INSERT 报告、更新 Weakness、CAS READY；失败整体回滚。`interview_report.session_id` 唯一。生成器和 DeepSeek 当前在该事务外；旧 version 不能最终提交，CAS 不会取消外部调用。
- [Controller](../../src/main/java/com/kun/aiinterview/interview/controller/InterviewController.java) 当前 POST 同步返回 READY；GET 返回四状态。Vue [useInterviewReport](../../frontend/src/features/interview/composables/useInterviewReport.ts) 收到 GENERATING 已能每 3 秒 GET，READY/FAILED 停止；人工恢复会 POST，冲突/不确定结果先 GET；epoch 防迟到回包，刷新或重新登录重新查询 Detail 和报告。B4-E 的真实 MySQL/前端测试与 B3 验收为历史证据，不是本轮执行。
- 仓库 [Schema 快照](../../sql/ai_interview.sql) 包含 13 核心表、上述四状态、Session version 与 updated_at、报告 session 唯一约束。本轮没有凭据连接数据库，实际在线 Schema 未核实。U1-R1 真实浏览器历史：正常答题 n=4，P50 3.105 秒；报告 READY 可见 n=1，3.147 秒，后端 3.116 秒，其中 DeepSeek 3.074 秒。U2 真实浏览器验证了刷新/重新登录和已有报告读取，但该场没有真实追问。小样本并非性能分布。

## 2. 为什么引入 MQ，及替代方案

目标是用户不用让一个 HTTP 请求持续等待外部模型，任务可持久化和进程重启后收口；独立队列可削峰并限制消费者并发，但本项目尚无峰值、吞吐或用户留存实测，不能宣称已改善。单体内的数据库任务表 + 定时工作线程可用更少基础设施满足当前规模，值得基准对照；这里按用户明确要求设计 RocketMQ，并承担 broker、派发、扫描、监控、消息重复及故障恢复成本。MQ 与 MySQL **不是天然原子事务**。

A. **普通发送 + 状态**：先提交 DB 再发，崩溃会漏发；先发再提交，消费者可能看到未提交或回滚任务。回执丢失无法知道是否成功，重发必有重复。若增加可持久扫描的待发送记录，其实已经变为 B；仅状态字段和内存重试不能证明可靠。

B. **Transactional Outbox，推荐**：同一 MySQL 事务写 Session GENERATING + 待派发任务；受控扫描器事务外同步发送普通消息，回执后有条件记 SENT。崩溃、失败或未知回执都可租约到期后重发，同一事件 ID 可能收到多次；消费者以 DB 状态和 fencing 幂等。需一张任务/Outbox 表、扫描器、截止及告警，处理 broker 遗失/长期不可用与死信。DB 事务保证的是状态与待投递记录一起提交，并非 DB 与 MQ 原子提交；可证明的前提是 DB 持久化、扫描器恢复运行、broker 可用及配置的持久化可靠，接受最终失败收口。

C. **RocketMQ 事务消息**：先写 half message，执行本地事务，向 broker 提交/回滚，回执或进程异常由事务状态回查；仍需可持久且稳定可查询的事务结果、回查超时、重复消费、消费失败和任务状态恢复。事务消息不可用时请求前半段也可能失败，回查超时可回滚消息；消费端仍需业务幂等，不能将模型调用纳入数据库事务。相比一表 Outbox，半消息/回查和 broker 状态操作学习成本更高。详见 [Apache 事务消息机制](https://rocketmq.apache.org/docs/featureBehavior/04transactionmessage/)及[消费重试](https://rocketmq.apache.org/docs/featureBehavior/10consumerretrypolicy/)。

## 3. 单表事务 Outbox 与版本模型（设计草案）

### 3.1 冻结 Schema 的例外及迁移

问题：不能仅靠内存或 Session 单个 `updated_at` 同时持久表达待投递、派发租约、排队截止、执行租约、投递重试和 attempt 身份；进程崩溃后 Java 内存消失。可对 Session 加多个列，但会把历史 attempt 与状态耦合，丢失多次失败记录；独立表是较小、可审计的持久化边界。**提议新增 `interview_report_task` 一表，不改现有四状态和 report 唯一约束**。迁移涉及本地/test/evaluation 三套独立 Schema、备份、版本化向前迁移、灰度切换、回退和测试；必须先取得用户对具体 DDL/数据迁移的明确批准。禁止在本轮执行。旧版与新版并存会绕过同一领取协议，部署须先停写/排空旧版执行，再切换单版本；升级前清点原 GENERATING 与既有报告，明确迁移协调，禁止盲目批量变 FAILED。

拟有：`id`（事件/任务 ID）、`session_id`、`attempt_version`（申请时 CAS 后 session version）、`execution_version`（消费者领取后 session version）、`task_state`（QUEUED/RUNNING/SUCCEEDED/FAILED）、`delivery_state`（PENDING/LEASED/SENT）、`dispatch_token`、`dispatch_lease_until`、`next_send_at`、`send_attempts`、`queued_at`、`queue_deadline`、`started_at`、`execution_deadline`、`failure_code`、`created_at/updated_at`。`UNIQUE(session_id, attempt_version)`，索引覆盖到期待投递和到期任务；字段类型、约束、时区精度和保留策略由迁移审查确定。单 session 允许多条历史任务，活跃唯一性由锁定 Session + CAS 保证，不把仅依赖应用内锁当作并发保证。

消息示意：`{"schemaVersion":1,"eventId":"<task-id>","sessionId":123,"attemptVersion":42}`；key 使用稳定 eventId。只传标识，不传用户、答案、评价、报告正文或凭据；消息中的身份和版本仅为定位与一致性校验，数据库是权威，消费者绝不能信任消息中的用户归属或把它当新版本。内部安全日志用事件关联，不记录正文或凭据。

### 3.2 请求和投递

1. POST 在事务外先读并校验登录用户归属、COMPLETED 和 FINAL 完整性；事务内重新锁定并复核状态和适用不变量，NOT_STARTED/FAILED 的 version CAS 成功后写 QUEUED/PENDING 任务并提交；单条事务失败全部回滚。若允许完成后的 FINAL 被其他路径修改，必须在事务内用适当锁/一致性约束防止校验与入队竞态，消费者执行前还要复核；不能只信预检查。持有事务不能发 MQ 或调用模型。
2. 领取成功返回现有 Result 包装的 HTTP 200 + GENERATING；不等待 MQ。READY 返回已有报告。重复 POST 遇到有效 QUEUED/RUNNING 返回同一 GENERATING，不新建任务；CAS 竞争失利重读，若 READY 返回报告，若 GENERATING 返回状态，其余受控冲突/错误。FAILED 的显式 POST 在完整校验通过后创建新 attempt，不能重复使用旧事件。
3. 派发器小批量查到期 PENDING 或过期 LEASED，用数据库时间和条件更新领取短租约，提交后发送；仅确定成功回执且租约 token 仍匹配才改 SENT。发送失败/结果未知退避后重试；进程在成功发送与标记间崩溃时再次发送同一 eventId。发送完成时消费者可能已将任务变 RUNNING 或终态，派发器更新仅限投递列且按任务/租约条件执行，不得回退业务状态。租约到期时旧派发结果不能覆盖新租约。无需假设 broker 恰好一次。
4. 收到消息按统一锁顺序 Session → Task 开短事务，确认消息与当前 Session、attempt 一致，QUEUED 且未超排队截止；CAS 将 task RUNNING、设置 `started_at` 和执行截止，并令 Session version 再加一作为 executionVersion。仅成功者调用模型；重复、终态、过期或旧事件在可确认 DB 状态后 ACK。此独立领取是必要的：仅申请阶段的 version 不能阻止两条同一消息同时调用生成器。
5. 模型前再验证 FINAL（含已有报告恢复分支），Java 算分，事务外执行现有生成器；最终短事务锁 Session → Task，验证 RUNNING、taskId 与 executionVersion，保留已有报告的 FINAL 校验恢复路径；无报告时 INSERT + Weakness + READY + task SUCCEEDED 一起提交。任一条件不成立全部回滚，旧执行者不得借新版本提交，也不得重复应用 Weakness。失败回写以相同锁顺序、任务 ID 和 executionVersion 条件设置 FAILED，未持有执行权者不得改状态。

`queue_deadline` 与 `execution_deadline` 分离，均由数据库时间生成、比较；举例初始排队上限 10 分钟、执行上限 10 分钟，仅为测试配置候选而非 SLA，最终根据超时配置、负载与故障注入决定，必须留模型和数据库收口余量。截止是一种有界失败政策，不是证明旧调用已停止。消费者与清理器在同一行锁下比较截止；临界时刻恰好一个胜者。排队长不会被误判为执行超时；**排队超自己的截止**会明确 FAILED，而不是无限排队或暗中二次调用。

### 3.3 恢复与 ACK 细节

- 应用内同一个受控扫描器负责恢复派发与超时收口；启动时及周期性扫描 QUEUED 超 queue_deadline、RUNNING 超 execution_deadline，按 Session → Task 锁序验证 attempt、条件将任务及 Session 改 FAILED 并提升 version，旧消息/执行者被隔离。此策略在可恢复窗口不偷偷自动再次调用 DeepSeek；用户可看到 FAILED 后显式重试。多实例可依靠条件更新/行锁，不能依赖单实例锁。
- DB 不可用时不能安全 ACK 未确认消息或假称已 FAILED；消息按客户端明确的失败/重试策略处理，重试耗尽后 DLQ 告警。若进程或数据库完全停机，无法承诺状态在墙上时限内变化；服务恢复、扫描器运行及数据库可写后才会收口。Broker 可用性不应阻止应用与恢复扫描器启动；不同异常需有限退避，避免热循环。
- 模型超时、非法 JSON、确定业务失败：CAS 成功持久 FAILED 后 ACK，避免无界付费重试；失败状态写入不确定/DB 不可用则返回消费失败，待重投或扫描器恢复。成功提交后 ACK 失败会重投，但 READY/SUCCEEDED 检查直接 ACK；重复消息到达 RUNNING 时 ACK 是安全的，因为死进程由独立 deadline 扫描收口，而非依赖重投触发再执行。
- DLQ 必须有告警、可查询 eventId、操作手册和受控重放；到 DLQ 不自动等于业务失败已写入，持久截止扫描负责最终对齐。重放只可对仍有效任务、按 DB 状态走同一 CAS；终态直接 ACK。配置并验证消息保存期限、消费超时、最大重试和 DLQ，不能把 DLQ 当处理完成。单机单副本测试不能证明 broker 故障零丢失。
- 历史 GENERATING 且无任务属于升级遗留：关闭旧 worker 后按现有 B3 检查已有报告/FINAL，再设计受控的单胜者修复或 FAILED；不能从时间猜测旧模型已停。执行 token 始终来自本次 CAS，绝不重新读取较新版本充当旧任务令牌。

## 4. API 与前端

沿用 `POST/GET /api/interviews/{sessionId}/report` 和 `InterviewReportResponse`，四个状态及 READY 字段保持不变。HTTP 200 + Result 包装用于已受理/处理中/READY（兼容当前 Axios）；不能声称旧 POST 一定 READY 的外部客户端无行为变化，须发布契约变更及回归。GET 无副作用，NOT_STARTED/GENERATING/FAILED 的报告内容仍为空。权限 401、其他用户或不存在 404、未完成 409 保留。FINAL 不完整走现有受控错误分类，不能接受任务后才隐藏不一致。

GENERATING 的人工按钮改为“检查状态”，调用 GET；不允许旧 POST 的 Session `updated_at` reclaim 绕过任务系统。现有客户端如仍 POST，也只返回当前状态；恢复由服务端扫描器执行。FAILED 用户明确重试创建新 attempt；重放网络超时先 GET。若刷新/重新登录，按现有 Detail + GET 还原；保留 401、404、GET 失败、同会话防重入、epoch 和路由变更防旧响应。受理成功但 MQ 持续不可用时有条件最终变 FAILED，见 3.3 的 DB/应用可用性限制。

## 5. 必测故障与断言

- POST 重复/并发、所有权、COMPLETED、FINAL 缺失、错绑、追问、历史已有报告恢复：单个活跃任务；不正确数据不入队；READY 不重新模型调用。
- 提交后发前 kill；发送成功丢回执；派发租约过期、旧回执迟到；MQ 停机及恢复；两派发器：记录不丢、相同 ID 可重复、最终运行或有界 FAILED；事务回滚不得遗留半成品。
- 重复消息、两个消费者竞争、消息早于 SENT 更新、超长排队、队列截止边界竞态：最多一个执行领取；排队不按执行 stale 回收；SENT 写入不能覆盖 RUNNING/终态。
- 模型超时、非法 JSON、DB 不可写、模型成功后 commit 前杀进程、commit 成功 ACK 前断链、失败回写失败、执行超时后旧消费者返回：报告唯一、Weakness 一次、同事务回滚、旧令牌不能 READY/FAILED；不承诺外部调用恰好一次。
- 重启后派发器与 deadline 恢复；非法消息/DLQ/受控重放；broker 存储和消息保存期假设；启动迁移时遗留 GENERATING。数据库完全不可用时不虚假 ACK/成功。
- 真实隔离 MySQL/MyBatis 事务和多线程 CAS；Mock 生成器 + loopback/独立 RocketMQ 真实送达、杀进程和 ACK 注入；前端 fake timers + API Mock 验证 200 GENERATING、失败、刷新/重新登录、状态查询、旧响应、鉴权。完整 Maven、前端测试/typecheck/lint/build；保留所有现有真实服务开关隔离。测试计数分层报告，Mock 不冒充真实 MQ/模型。

## 6. 版本、环境与限制

候选组合：Java 21 + Spring Boot 3.5.16 + MyBatis 3.0.5（现项目），RocketMQ Broker/NameServer 官方 `apache/rocketmq:5.3.2`，候选 `org.apache.rocketmq:rocketmq-spring-boot-starter:2.3.6`（remoting）+ broker 5.3.2。官方 [Docker 示例](https://rocketmq.apache.org/docs/quickStart/03quickstartWithDockercompose/)采用 5.3.2，[镜像 manifest](https://hub.docker.com/layers/apache/rocketmq/5.3.2/images/sha256-d923ab25ddbbf539eaf64268d250d5c41cd6d58f1be4d220f800e09aa9d6bae2)列出 linux/arm64。2.3.6 [父 POM](https://raw.githubusercontent.com/apache/rocketmq-spring/rocketmq-spring-all-2.3.6/rocketmq-spring-boot-parent/pom.xml) 列 Spring Boot 2.7.18、RocketMQ client 5.3.2，源码含 [Boot 3 自动配置入口](https://raw.githubusercontent.com/apache/rocketmq-spring/rocketmq-spring-all-2.3.6/rocketmq-spring-boot/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)；不能仅据此宣称 Boot 3.5/JDK21 已兼容；本轮独立探针的有限实测见第 9 节。M1-B 仍需验证完整项目依赖树、Jakarta/日志冲突、重试/ACK/关闭和测试禁用；不通过则评估官方客户端显式配置，不以降级项目技术栈掩盖问题。MyBatis 无直接协议依赖，仍需集成验证。

本机 arm64、Java 21.0.13、Docker 29.9.2；Docker daemon aarch64、10 CPU、约 7.75 GiB 内存，已有本项目 MySQL/Milvus 和其他项目独立 RocketMQ 等运行；当前 MySQL 暴露 127.0.0.1:3308，数据库凭据环境变量未设置，未读取实际 Schema。磁盘可用约 282 GiB。仅计划独立 Compose 项目、独立网络和数据目录、loopback 绑定的经实际检查空闲端口（候选宿主 NameServer 19876、Broker 20911；Broker 容器 `listenPort=20911`、`brokerIP1=127.0.0.1`，宿主和容器同端口映射，NameServer 容器内部仍用 9876；需要的 fast/HA 端口按 SDK 实测决定并隔离映射），设置小 JVM 堆、容器内存/CPU 上限，避免影响现有服务；不使用或清理其他项目容器/卷。单节点非高可用；本轮临时探针结果见第 9 节，M1-B 必须重新核验资源和端口。无需 Proxy 的 remoting 方案不开放 Proxy；不能只改宿主端口而忽略 broker 对客户端通告的端口。

## 7. 预计文件与分批范围

- M1-B0：隔离环境、版本 spike 和无业务副作用的连接验证；`pom.xml`、独立测试 Compose/配置、测试 profile、兼容性测试，需审阅依赖。运行关闭开关时普通测试不得启动 MQ。
- M1-B1：**先审批具体 DDL**；新增版本化迁移与测试 Schema；`InterviewReportTask`、Mapper/XML、请求事务服务，实现一次状态+Outbox 提交、FINAL 预检查及重复请求。迁移和既有 GENERATING 部署策略必须先演练。
- M1-B2：派发器、RocketMQ producer、消费者和超时恢复；`InterviewReportService` 拆分申请/消费领取/生成，`InterviewSessionMapper/XML` 增加执行 fencing、禁用旧 stale 旁路，`InterviewReportTransactionService` 把任务终态纳入最终原子事务；既有 `InterviewReportGenerator` 和 `InterviewReportMapper/XML` 优先复用，只在证据证明必要时改动。限界派发和消费线程、关闭恢复。
- M1-B3：`InterviewController` 的 POST 返回契约、`useInterviewReport.ts`、`InterviewReportView.vue`、`reportCopy.ts` 和相关测试；兼容旧客户端的 GENERATING POST，不变更 GET 结构。
- M1-B4：隔离 MySQL + 独立真实 RocketMQ 的故障注入、浏览器验收、回归和性能记录，再决定是否发布。每批分别记录准确测试数、已编码/编译/Mock/真实服务状态及提交状态。任何代码/Schema 名称为实施计划，最终以审查批准为准。

性能分别记录：同步 HTTP 完成、异步 POST 受理、申请到 READY、入队到 broker 回执、broker 到消费领取、DeepSeek、故障到恢复/FAILED；说明时钟来源、样本、分位数、并发、失败和超时，既有 MVC ThreadLocal 请求埋点跨消息线程不会自动传播，需独立关联与释放。HTTP 更早返回不等于实际生成更快。

公开工程亮点需先有实际证据：可靠入队需 DB 原子事务及 kill 注入；幂等需并发 CAS、报告/Weakness 对账及 ACK 丢失重投；故障恢复需 broker 停机、进程重启与截止收口；用户体验需可复现实测的受理和 READY 时间。实施前只能表述为“设计”，不声称已上线、零丢失、恰好一次或性能提升。

## 8. M1-A 补充：最小实现清单（2026-11-14）

约束：一个 Spring Boot 单体内的 Producer、派发器、Consumer；一个报告 Topic、一个消费组、一张任务表；普通消息 + Outbox。不加事务消息、CDC、Redis、分布式锁、SSE/WebSocket 或通用任务平台。以下类名/方法是实施候选而非现有代码或已完成工作。运行旧同步路径与新异步路径并存不安全；上线时全量切换而非按请求随机选择。

### 8.1 精确文件和方法

1. **配置**：`pom.xml` 新增候选 `rocketmq-spring-boot-starter`；`src/main/resources/application.yaml` 配置可关闭的报告 MQ、Topic、group、派发间隔、短租约、派发次数上限、排队/执行截止与消费并发，非密钥；`application-test.yaml` 明确禁用。`EnvironmentIsolationInitializer` 扩展独立测试 MQ 目标的 fail-closed 校验，不因为普通 profile 加载而连接外部 Broker。生产用途前确认配置、启动失败策略和依赖树；真实测试使用独立 profile/容器及 opt-in 门禁。
2. **任务记录**：新增 `interview/entity/InterviewReportTask.java`、`interview/mapper/InterviewReportTaskMapper.java` 和 `src/main/resources/mapper/InterviewReportTaskMapper.xml`。候选方法 `insertQueued`、`getByIdForUpdate`、`listDueDispatchIds`、`claimDispatch`、`markSent`、`rescheduleDispatch`、`listExpiredIds`、`claimExecution`、`finishSuccess`、`finishFailure`；有限批量，SQL 明确条件、受影响行必须为 1。新增 `sql/migrations/` 版本化 DDL，测试 Schema 也要按隔离流程更新；不新增平台抽象。枚举按实际需要嵌入报告业务包，不引入空层。
3. **请求及聚合**：`InterviewReportService.generateReportForApi` 改为申请并返回已有结构；`generateReport` 不再承担同步外部模型调用，也不能保留对外可达的第二入口。提取/复用 `loadOwnedSession`、`validateCompleted`、`aggregateFinalEvaluations`、`validateEvaluations`、`validateEvaluationAssociations`、`calculateOverallScore`、`existingReportOrContinue`、`buildReport`：保留既有语义，避免复制两个不同 FINAL 校验。`generateClaimedReport` 只由消费者在已持执行版本时执行；`claimReportGeneration`/`recoverReportFailure` 按任务与版本收口，禁用 `reclaimStaleGenerating` 的异步旁路。需全项目搜索 `generateReport` 的调用者和测试并一起迁移，确认无人保留同步调用路径。
4. **事务及 Session Mapper**：扩展 `InterviewReportTransactionService` 的 `completeReportGeneration`；新增 `acceptReportTask`、`claimTaskExecution`、`failTask`/`expireTask`（可按职责合理命名）均通过 Spring 代理短事务调用。`InterviewSessionMapper.java` / XML 复用 `claimReportGeneration`、`markReportReady`、`markReportFailed`，增加 `get...ForUpdate` 与仅允许本轮待执行 GENERATING/version 的执行领取 CAS；异步路径不调用 `reclaimStaleGenerating`。事务内统一 Session → Task 锁顺序，禁止因代理自调用失去事务。`InterviewReportMapper`、XML、`InterviewReportGenerator.generate` 原样复用，除非测试暴露具体必要改动；`UserWeaknessService.applySessionEvaluations` 的调用仍仅在完成事务内。
5. **MQ 与扫描**：报告业务包新增 `ReportMessageProducer.send(taskId, sessionId, attemptVersion)`、`ReportOutboxDispatcher.dispatchDue/expireDue` 和 `ReportMessageConsumer.onMessage`；调度只扫描本表，受控批量、限并发、停止时等待有界退出。Consumer 在监听回调线程完成业务并以持久结果决定正常返回/抛异常；不先 ACK 后交给内存线程池。新增仅承载稳定标识的 `ReportTaskMessage`。不用新远程服务或泛化框架。
6. **API/Vue**：`InterviewController.generateReport/getReport` 路径、`Result<InterviewReportResponse>` 和 GET 保持；POST 可为 GENERATING 或 READY。`frontend/src/features/interview/composables/useInterviewReport.ts` 的 `recoverGenerating`、`submitReport` / `handleGenerateFailure` 适配查询型恢复，复用 `refreshReport`、`startPolling` 和 epoch；`InterviewReportView.vue`、`copy/reportCopy.ts` 修改提示和按钮行为。旧客户端对 GENERATING 的 POST 永远只返回持久状态，不同步生成。`interviewApi.ts` 与类型若无必要不改。
7. **测试**：扩展 `InterviewReportServiceTest`、`InterviewReportApiServiceTest`、`InterviewReportTransactionServiceIntegrationTest`、`InterviewReportFinalConsistencyIntegrationTest`、`InterviewSessionMapperTest`、`InterviewControllerTest`、`StaleRecoveryCasIntegrationTest`，新增报告任务 Mapper/事务竞争、派发/消费/恢复及独立 RocketMQ opt-in 测试；扩展 `frontend/tests/InterviewReportView.spec.ts`。文件清单为风险控制，不要求无意义地修改每个文件。

### 8.2 双进度、版本、时间和 ACK

- **执行进度** `task_state`：QUEUED → RUNNING → SUCCEEDED 或 FAILED；QUEUED 也可因派发/排队期限失败。一次显式 FAILED 重试插入新 task ID，绝不重用旧行。**投递进度** `delivery_state`：PENDING → LEASED → SENT；失败/未知回执仅以匹配 lease token 回 PENDING 并安排下一次，超期后投递状态仅作历史记录。二者独立，不用一个状态同时表示 MQ 和业务。
- Session 原 version 为 v：申请事务 `claimReportGeneration(v)` 成功产生 v+1，记录 `attempt_version=v+1` 和唯一 taskId；监听消费在锁定 Session/Task 后核验 taskId、sessionId、attemptVersion、状态、期限及 Session version=v+1，CAS 后变 v+2，记录 `execution_version=v+2`，它是本次工作独有的 fencing token。仅在本次成功领取后有模型调用资格；重复消息同一 task 的 RUNNING/终态，在 DB 可读且状态确定时返回成功。FAILED 重试产生新 task ID、更新后的 attemptVersion；旧消息不满足当前版本，旧 Consumer 不得从数据库获取新版本冒领。version 溢出失败并告警，不回绕。
- **抢跑竞态**：Producer 发出后，Consumer 可以在派发器记录 SENT 前成功领取并完成。派发器 `markSent` 只 `SET delivery_state='SENT', ...`，WHERE 包含 taskId、`delivery_state='LEASED'` 和本次 `dispatch_token`；**不写 `task_state`、Session、execution_version 或执行时间**。因此 RUNNING/SUCCEEDED 不会被还原为 QUEUED；迟到的失败/超时处理也必须匹配 token 且不得重新开启终态。发送未知时允许按 token 退避重发，即使消息已执行也只能由状态检查 ACK。
- 候选默认值，用数据库统一时钟和 `DATETIME(6)`：申请时 `queued_at` 与 `queue_deadline=queued_at+10分钟`；每次派发 `dispatch_lease_until=now+30秒`，发送超时 5 秒，`next_send_at` 指数退避 2、4、8、16、32、60 秒后封顶 60 秒；最多 20 次发送尝试；第 20 次仍失败或回执未知则条件失败收口。若已确认 SENT，则不因发送次数收口，继续等待消费直到 queue_deadline；排队截止到达时无论是否 SENT 都条件失败收口。消费者领取时 `started_at`、`execution_deadline=started_at+10分钟`，执行无自动续租；必须实测整链预算小于期限并留数据库收口余量，数值是可验证配置候选、不是 SLA。租约只控制派发并发；queue/execution 截止才判业务失败。数据库/扫描器不可用时不保证按时变更，恢复后按当前数据库时间补扫。
- 同一完成事务：锁 Session 再锁 Task；核对 taskId、RUNNING、COMPLETED/GENERATING、Session version 与 executionVersion。先做最终资格检查，再 INSERT Report（唯一 session）→ `applySessionEvaluations` → version CAS READY → task SUCCEEDED；每步 affected-row / generated-key 检查，任何失败整个回滚。已有非 READY 报告恢复也须 FINAL 校验，原先不重复更新弱项；协调 READY 与任务成功同事务提交，不假定历史不一致行的弱项一定正确。生成器在此事务外；旧线程可能仍付费，但所有权失效后提交/失败回写都被拒绝。
- **ACK 必须有持久依据**：回调直接执行到最终事务成功，或执行失败后条件写入 FAILED 成功，或已通过 DB 确认重复/终态/旧任务等无需处理，才正常返回；DB 结果不确定、持久失败或无法验证所有权则抛出异常交 MQ 基础设施重试。不能先正常返回再用 `@Async`/本地线程去生成。模型业务超时/非法 JSON 成功落 FAILED 后 ACK，用户显式触发新轮才再次调用；不是每次 MQ 重投都调用模型。进程崩溃的 RUNNING 由扫描超时失败，ACK 无法恢复的重复消息不会再次领取。

### 8.3 DDL 候选与索引依据（未执行，待独立批准）

下面是审查候选，不是迁移授权，也不对现有数据库执行。`interview_session.id`/`version` 类型依据仓库 SQL 快照；实时环境须先只读核对。示例 `CURRENT_TIMESTAMP(6)` 和期限比较必须固定数据库连接会话时区，避免和 Java cutoff 混用；根据真实 MySQL 版本验证 CHECK 生效与 SQL 执行计划。

```sql
CREATE TABLE interview_report_task (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  session_id BIGINT UNSIGNED NOT NULL,
  attempt_version INT UNSIGNED NOT NULL,
  execution_version INT UNSIGNED NULL,
  task_state VARCHAR(16) NOT NULL,
  delivery_state VARCHAR(16) NOT NULL,
  dispatch_token CHAR(36) NULL,
  dispatch_lease_until DATETIME(6) NULL,
  next_send_at DATETIME(6) NOT NULL,
  send_attempts INT UNSIGNED NOT NULL DEFAULT 0,
  queued_at DATETIME(6) NOT NULL,
  queue_deadline DATETIME(6) NOT NULL,
  started_at DATETIME(6) NULL,
  execution_deadline DATETIME(6) NULL,
  failure_code VARCHAR(40) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_task_session_attempt (session_id, attempt_version),
  KEY idx_report_task_pending (task_state, delivery_state, next_send_at, id),
  KEY idx_report_task_lease (task_state, delivery_state, dispatch_lease_until, id),
  KEY idx_report_task_queue_expiry (task_state, queue_deadline, id),
  KEY idx_report_task_execution_expiry (task_state, execution_deadline, id),
  CONSTRAINT fk_report_task_session FOREIGN KEY (session_id) REFERENCES interview_session(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_report_task_state CHECK (task_state IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
  CONSTRAINT chk_report_task_delivery CHECK (delivery_state IN ('PENDING','LEASED','SENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

唯一索引约束同一 Session/attempt 不重复；它本身**不能**保证同 Session 仅一个活跃任务，依靠申请事务先锁 Session + 原状态/version CAS。两个派发索引分别服务 PENDING 的 `next_send_at` 和 LEASED 的 `dispatch_lease_until`，不要用不加界的 OR 扫描；两个期限索引用于分别扫描 QUEUED、RUNNING。每条查询限制批量并按截止、id 排序；实际 `EXPLAIN` 与数据规模验证后删减冗余索引。约束不试图代替事务内状态组合校验。

**迁移与回退：**(1) 单独审批 SQL 和精确目标，备份及只读对账，核验空闲、会话状态、权限和环境隔离；先在独立临时 MySQL 测试 forward/应用和回退，不扩大 evaluation 账号权限。(2) 停止旧版接收报告 POST 并排空所有旧执行者，清点旧 GENERATING 和报告，按 FINAL/唯一约束逐项审查，再部署 DDL 和新代码；不支持旧/新混合运行。(3) 回退应用前先暂停新受理和派发、排空消费者，按任务和 Session 版本核验 QUEUED/RUNNING/已有报告，按批准的单胜者流程收口或协调；停止新消费者后才运行旧同步版本，不能简单切流导致旧同步 reclaim 并发。若不能确认旧执行者停止或数据一致，保持停写并人工处理。(4) 回退时默认保留表与历史任务，不执行 DROP、不清理 Topic/消息；旧代码忽略新表。若未来需删除，另给数据保留证据、精确目标和单独批准。MySQL DDL 可能隐式提交，不能声称 DDL 与应用切换原子回滚。

### 8.4 四类关键故障窗口与最小探针

1. **DB 提交 → MQ 发送前进程退出**：在 request commit 后的测试钩子 kill 进程；重启派发器。查 Session 与任务在同一提交出现、pending 被重新扫描，最终唯一 READY 或期限 FAILED；事务回滚注入则二者都不存在。探针：临时隔离 MySQL 真事务、进程屏障、taskId/attemptVersion 对账。
2. **MQ 已接收 → 回执丢失／发送记录前退出**：测试代理转发真实 Broker 回执后丢弃或 kill 派发器，等租约过期；观察同 eventId 重发和 Consumer 抢跑时后写 SENT。探针：发送次数、dispatch token、broker 消息 key、任务状态变化与一次执行领取；断言后写不回退 RUNNING/SUCCEEDED。
3. **消费重复／模型完成 → DB 提交前退出或执行超时**：两个消费者重复投相同消息，Mock 模型 latch 阻塞，令旧执行超时、产生新 attempt 后释放旧者；另在生成后事务提交前 kill。探针：真实 MySQL CAS affected rows、task/session version、模拟模型调用数、Report 唯一数和 Weakness 前后对账；旧者零有效写，显式新轮可能再调模型，不能声称外部恰好一次。
4. **DB 已提交 → ACK 丢失、MQ/DB 故障**：模拟 ACK 前进程退出/丢确认并重投；验证 SUCCEEDED/READY 的持久读取后 ACK，不重复更新 Weakness。单独使 DB 不可写、MQ 不可用，断言未确认时抛出消费失败、派发退避且期限后受控 FAILED；恢复时重新扫描。探针：Broker 投递/重投与 DLQ、受影响行、报告和 Weakness 持久值、日志不含正文/密钥；Mock 与真实服务分别报告。

## 9. 独立版本兼容性探针实测（2026-11-14）

- 环境：macOS ARM64，Temurin Java 21.0.13；独立临时 Maven 项目 `/private/tmp/ai-interview-m1a-swJBsI` 和该目录专属 Maven 本地仓库；Spring Boot 3.5.16、`rocketmq-spring-boot-starter` 2.3.6；解析出的 `rocketmq-client`/`rocketmq-remoting` 5.3.2，Spring Framework 6.2.19。依赖树记录在该目录 `dependency-tree.log`，探针源码、POM、Compose 和 `spike-run.log` 同目录。临时目录可能被系统清理。
- 独立 Compose 项目 `ai-interview-m1a-spike`、独立网络、单 NameServer + Broker `apache/rocketmq:5.3.2`，镜像 linux/arm64，索引摘要 `sha256:4a80ff5cd4b0a3d3ef6dc338ce2028a85bd995648cb3aa5c3e753746d2dbc587`。仅绑定 127.0.0.1:19876、20911、20909；Broker 设置 listenPort 20911 和 brokerIP1 127.0.0.1、测试专属 Topic，不使用其他项目 Broker。NameServer 限额 512 MiB，Broker 限额 1 GiB；取样占用分别约 162 MiB 和 523 MiB。容器无持久卷，Broker 配置仅只读挂载本轮临时文件。
- 官方镜像下载成功，NameServer 和 Broker 日志显示 boot success；临时 Broker 成功创建 `ai-interview-m1a-report-test`。临时 POM 的 `mvnw ... -DskipTests package` **退出 0，BUILD SUCCESS**；测试未运行，T/F/E/S 不适用。依赖树命令退出 0，但 Maven 有 `org.javassist:javassist:3.21.0-GA` effective model 告警，完整项目集成时需要排查，不将其视为已解决。
- 运行 Spring Boot 探针的 `RocketMQTemplate.syncSend`，返回 **`SEND_OK`**，`@RocketMQMessageListener` 监听器收到内容并输出 **`SPIKE_RECEIVED=true`**；进程退出 0。样本为 **1 次发送、1 次监听器收到**，消息为合成固定字符串，不涉及 DB 或模型。该结果证明这组版本在此机器上能完成最小启动与单次真实收发，不证明 ACK 持久化、重试、死信、故障恢复、性能或完整项目依赖兼容。
- 原沙箱内 Maven Central DNS 失败；经授权的网络环境下 Maven Central 访问与依赖下载成功。未访问现有数据库、未更改权限、未调用 DeepSeek/Embedding，未切换常驻服务。专属容器与网络已按本次 Compose 项目关闭移除，没有删除数据卷或其他项目资源；镜像缓存保留。未对正式服务发送消息。
