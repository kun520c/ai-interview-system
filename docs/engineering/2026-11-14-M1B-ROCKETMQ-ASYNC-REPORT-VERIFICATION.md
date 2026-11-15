# M1-B RocketMQ 异步报告实施与验收

日期：2026-11-14（Asia/Shanghai）。仓库：`/Users/kun/Developer/projects/ai-interview-system`。基线：`main`，`84e3489a85d0fb6c8b54ad2a130c52d7476fb897`。本批已编码并完成下述分层验证，未部署正式服务、未暂存、未提交、未推送。M1-A 与既有验收内容保留，不能把本次证据回填成过去已实现。

## 1. 结果与边界

POST 保持原路径、Result 包装和四种报告状态，改为持久受理，通常返回 GENERATING；已有报告返回 READY。用户归属、COMPLETED、FINAL 完整性及 MAIN/Answer/Follow-up 关联校验保留。GET 无写副作用。GENERATING 页面操作改为 GET 查询，保留轮询、刷新恢复、鉴权、失败重试和旧响应防护。旧客户端“POST 成功即生成完成”的假设不再成立，必须接受 GENERATING 后继续 GET。

单体、一个业务 Topic/消费组、一张任务表、普通消息和数据库 Outbox。无事务消息、CDC、Redis、分布式锁、通用任务平台或 SSE/WebSocket。没有外部模型恰好一次、消息零丢失或性能提升的结论。

数据库唯一授权是用户明确批准 `127.0.0.1:3308/ai_interview_test` 执行最终迁移及本次测试数据写入/清理。该目标已应用；所有其他目标（含 local/evaluation/临时库）仍 NOT_AUTHORIZED，未访问 evaluation 数据、未新建 Schema、未扩权。完整 SQL 及执行前实例/权限/备份/影响核对见 [准入记录](2026-11-14-M1B-DATABASE-ADMISSION.md)。

## 2. 关键调用链与取舍

1. `InterviewController.generateReport → InterviewReportService.generateReportForApi → InterviewReportTransactionService.acceptReportTask`：短事务先以 Long 锁定 Session version，再读取安全 Session 投影，校验用户、完成状态和 FINAL；CAS GENERATING 与 QUEUED/PENDING 插入同一事务。相同 Session 的并发 POST 串行核对；FAILED 显式重试生成新的任务 ID 和 attempt。历史无任务 GENERATING 只告警，不通过 POST 猜测接管。
2. `ReportOutboxDispatcher.dispatchDue → TaskMapper.claimDispatch → ReportMessageProducer → ReportMqRuntime.send → markSent`：有限批量，UUID 租约 token，发送在事务外；未知结果有限退避，只有 SEND_OK 视为发送成功。迟到 markSent 仅修改匹配 token 的投递字段，可在业务终态后落下 SENT，不会回退 SUCCEEDED/FAILED。数据库结算失败保留租约恢复窗口。
3. `ReportMqRuntime callback → ReportMessageConsumer.onMessage → claimTaskExecution`：固定 Session→Task 加锁，匹配三个稳定标识、当前状态、数据库期限和 Session version。只有 CAS 胜者拿到固定 executionVersion 才能调用模型；不把新读取的 version 替换进旧执行令牌。
4. `generateClaimedReport → InterviewReportFinalReader → 既有 InterviewReportGenerator`：模型在事务外，重复/旧任务不调用；FINAL 读取失败的数据库异常向 MQ 传播。固定令牌用于最终提交或失败回写；失败回写失败也不 ACK。
5. `completeReportGeneration`：再次 Session→Task 加锁并校验执行权、期限、FINAL 和汇总分数，Report INSERT、既有 Weakness Service、Session READY、Task SUCCEEDED 同一事务。最后 SQL 再核验执行期限，任一步失败全部回滚。历史报告恢复校验 FINAL/分数，禁止重做 Weakness，并发出弱项历史待审计告警，不能据此断言历史弱项正确。
6. `expireDue → expireTask`：排队期限、执行期限和派发租约分别处理；旧任务只可标记自身 STALE_ATTEMPT，不能修改新执行者。DB/扫描器不可用时不能保证按时失败，恢复后有限补扫。

任务 attempt/execution 和锁定的无符号 Session version 使用 Long；仅在范围检查后转 Java Integer。受理预留三次递增，耗尽时受控 FAILED 并告警，不能回绕。报告 GET 使用安全投影，使数据库大于 Integer.MAX_VALUE 的版本不因映射失败遮住 FAILED 状态；未扩展为全项目版本类型重构。

ACK 只在回调内完成：成功提交、确定性失败已提交或 DB 确认重复/终态/旧任务后返回 SUCCESS；读取/提交不确定、错绑/非法消息、所有权无法证明均 RETRY，不转交本地异步线程后先 ACK。DLQ 是 MQ 状态，业务最终状态仍以数据库为准。

直接使用官方 `rocketmq-client:5.3.2`，没有引入 Spring starter。显式生命周期使 Broker 启动失败不会阻止应用提供状态查询/数据库过期扫描；普通测试和默认应用不创建 MQ 客户端。调度连接、派发、收口用三条独立调度线程。最终参数、资源限制、告警和重放步骤见 [运行手册](../../tools/m1b/README.md)。参数仍需实际部署负载校准。

全局检索确认生产代码没有 `reclaimStaleGenerating` 或旧 Service 同步 `generateReport` 旁路；控制器的同名 HTTP 方法只做异步受理。`generateClaimedReport` 的生产调用方仅 ReportMessageConsumer。原 HTTP Timing 仍计受理耗时，不再把 ThreadLocal 中模型/提交阶段当跨线程总耗时；本批没有性能试验，不能从测试日志计算收益。

## 3. 环境、迁移与依赖

实测 Java Temurin 21.0.13、Spring Boot 3.5.16、macOS ARM64、Node 24.22.1/npm 11.19.1，Docker 29.9.2 ARM64；Docker 分配 10 CPU、8319504384 bytes 内存，开始时磁盘可用约 282 GiB。既有 MySQL 容器 `ai-interview-mac-mysql-1`、8.0.45、宿主 3308；全程没有停止它或其他项目容器。

迁移文件 `sql/migrations/20261114_interview_report_task.sql` 无版本冲突，遵循现有日期命名手工增量。最终 DDL 与 M1-A §8.3 相同，唯一 CREATE TABLE、无 IF NOT EXISTS/DROP/自动启动执行。基础 `sql/ai_interview.sql` 仍是迁移前快照，只增加用途说明，避免初始化后重复定义。建表使用已存在容器 root，本次测试使用现有 ai_interview_test 账号，未扩大权限。备份 `/private/tmp/m1b-ai-interview-test-before.sql` 0600，SHA-256 `3e0bdf8184637570853f65ecfee28c0a65f82a5ba9f14fdc7d02e520102dd285`。DDL 隐式提交，不与应用切换原子回滚。

真实 MyBatis 验证生成主键、唯一 session+attempt、CHECK、更新行数、并发租约/CAS、受理及最终事务回滚。MySQL session/global=SYSTEM、system=CST；NOW−UTC_TIMESTAMP=08:00:00，所有任务期限 SQL 使用 UTC_TIMESTAMP(6)，与 Java UTC 读回差小于测试 5 秒容差。四条 EXPLAIN 分别选中 pending/lease/queue_expiry/execution_expiry 索引，rows=1、Using index condition；只是小 fixture 查询计划，不是规模/性能验证。

独立 MQ 项目 `ai-interview-m1b`、网络 `ai-interview-m1b_default`、两个容器 namesrv/broker；NameServer `127.0.0.1:19876`、Broker `127.0.0.1:20911`，镜像 RocketMQ 5.3.2 linux/arm64，镜像 ID `sha256:d923ab25ddbbf539eaf64268d250d5c41cd6d58f1be4d220f800e09aa9d6bae2`。唯一业务 Topic `ai-interview-m1b-report-test`、消费组 `ai-interview-m1b-report-test-consumer`。NameServer 上限 512 MiB/0.5 CPU，Broker 1 GiB/1 CPU。只对本项目 Broker 做 stop/start 故障，不操作其他项目卷或数据。

最终只停止该项目两个容器，保留容器文件、历史消息和网络，没有 down/DROP/清卷。停前内存快照 NameServer 168.8 MiB、Broker 480.2 MiB，不是峰值统计。Compose stop 后两者 exit=137、OOMKilled=false；不把此退出称作 Broker 优雅排空，本批也未做最后停止后的再次恢复验证。其他原有容器仍运行。最终已批准 Schema 14 张表 COUNT(*) 全为 0，与完整测试前核对相同，没有执行额外批量清理。

完整 Maven verbose dependency tree 已检查并保留。RocketMQ→remoting→reflections 0.9.11→javassist 3.21.0-GA 引出 effective-model 告警：旧 POM 的 mac-tools systemPath 指向 JDK 21 不存在的 `../lib/tools.jar`。该告警仍存在，未伪造 tools.jar、降级 JDK/安全检查、强制跳过测试或未经验证覆盖依赖。Boot 管理 Netty 4.1.135.Final、SLF4J 2.0.18、Logback 1.5.34；RocketMQ 自带隔离日志库 1.0.1、gRPC 1.53.0 等旧传递依赖仍需后续升级兼容/漏洞评估，本次不是全面供应链安全审计。当前编译、完整项目测试和真实 MQ 链通过不能证明所有环境长期兼容。

## 4. 测试记录

下列 `T/F/E/S`=运行总数/失败/错误/跳过，测试数包含跳过，各轮重叠不可相加为独立用例。原始日志在 `/Users/kun/Developer/projects/ai-interview-system/backups/M1B_20261114/`（Git 忽略、0600），哈希清单为 `evidence-manifest.json`；原始 `/private/tmp/m1b-*` 亦保留。所有真实 DB 测试仅使用已批准目标，模型和 Embedding 本批真实调用均 0。

- 生产 compile：退出 0，192 个源文件，无测试运行；初次 testCompile 19 个旧同步 API 引用编译错误，迁移对应测试后解决，不能记为 JUnit 19 失败。日志 `m1b-compile.log`、`m1b-test-compile.log`。
- 报告 Unit/Mock 第一轮：**77/0/0/0**，`m1b-unit-1.log`。后续新增禁用功能无任务表路径随最终全量覆盖。
- 真实 MySQL 专项第一轮：**45/0/0/0**，`m1b-mysql-1.log`，Mapper 10、事务 11、FINAL 一致性 24；模型为替身，没有真实 MQ。
- 前端报告测试旧契约首轮：**56/23/0/0**；把已废弃“GENERATING 再 POST”场景迁移为 GET 查询后 **51/0/0/0**，原权限/状态/错误/旧响应行为继续验证。日志 `m1b-frontend-report-1.log`、`-2.log`。
- 前端全量：**341/0/0/0**，37 文件；`npm run typecheck`、`lint`、`build` 均退出 0。环境 jsdom/Vitest 5.0.1；不是浏览器端到端验证。日志 `m1b-frontend-full.log` 及 typecheck/lint/build。
- 真实 RocketMQ+MySQL 初轮：**2/0/0/0**，`m1b-real-mq-1.log`；提交后未派发再恢复、真实重复发送、消费先于 markSent 提交，报告唯一、模型替身一次、Weakness 一次。
- 独立 Broker 停机：**1/0/0/0**，`m1b-mq-outage.log`；测试从 send_attempts=19 注入最后一次发送失败，真实持久 SEND_EXHAUSTED，另一个已过排队期限任务 QUEUE_EXPIRED；不是完整 20 次真实重试，也未停共享数据库。
- DLQ 探针首次：**2/0/2/0**，`m1b-mq-dlq.log`。Broker 已实际重试并入 DLQ，探针错误期待 MessageExt 文本而 CLI 输出 ID/QID/Offset，导致超时。修正只读解析后 **2/0/0/0**，`m1b-mq-dlq-2.log`；已记录 taskId 202/203 对应真实 DLQ offset 2/3，分别是注入的 DB 不可读异常和额外字段非法 JSON，业务仍 QUEUED，核验后受控重放各成功一次。DB 异常为 Mock 注入，Broker 重试/DLQ 和业务 SQL 是真实的。
- 完整 Maven 首轮：**1920/2/1/22**，`m1b-full-maven-1.log`。1 个错误为 Dispatcher 测试二次 `when` 触发既有抛异常 stub，改 doReturn；2 个失败来自未改动的 Evaluation 并发测试 latch 超时，MySQL max_connections=151、Max_used_connections=152、Connection_errors_max_connections=13，多缓存上下文 Hikari 默认各 10 连接耗尽实例。只将 test Hikari 限制为 max=4/minIdle=0，未削弱并发断言、延长等待或禁用测试。
- 定向恢复：**38/0/0/0**，`m1b-targeted-recovery.log`，含原 Evaluation 并发 11、Dispatcher 7、API 10、Task Mapper 10。
- 完整 Maven 第二轮：**1920/0/0/22**，`m1b-full-maven-2.log`。
- 增加受理事务 SQL 失败注入后的全量：**1923/1/0/23**，`m1b-full-maven-final.log`；唯一失败是新增测试对 MyBatis 接口代理使用 callRealMethod，未到真实 SQL。改用同一事务绑定 SqlSessionTemplate 执行真实 Mapper 后定向 **12/0/0/0**，`m1b-acceptance-rollback.log`；插入后实际 NOT NULL 违约证明 Session/Outbox 一起回滚。
- 最终全量：**1923/0/0/23**，`m1b-full-maven-final-2.log`，退出 0。实际通过 1900；23 跳过为既有真实外部服务 18、AI baseline 1、新 MQ 类 4 个禁用测试描述符（启用后参数化 DLQ 展开为两个案例）。所有真实外部开关未开启，跳过不是真实服务通过。
- 最后 `./mvnw -B -ntp -DskipTests package` 退出 0，生成 `target/ai-interview-system-0.0.1-SNAPSHOT.jar`；在上述全量已通过后只打包，不重复执行测试，T/F/E/S 不适用。日志 `m1b-package.log`，未启动此 JAR 或切换现有服务。

覆盖包括所有权/匿名权限、COMPLETED、FINAL 缺失及错绑、重复并发 POST、消费者竞争、过期排队/执行竞态、迟到租约回执、失败回写失败、旧 attempt、历史报告恢复及不一致拒绝、Integer 边界；前端异步 POST、轮询/手动查询、刷新、401/404/500、失败重试、路由变化、卸载和旧响应。真实 fixture 的默认模型替身断言事务不活跃；`FullInterviewBusinessOfflineTest` 有外围事务，单独不能证明模型在事务外，不能替代独立事务/MQ 测试。

## 5. 四个故障窗口的证据强度

1. DB 提交后发送前退出：真实事务已提交，暂不派发、随后派发成功；Outbox 可恢复已验证。没有实际杀应用进程，进程退出窗口未真实注入。
2. Broker 接收后回执丢失/记录前退出：真实 Broker 接收、真实消费者先提交后才 markSent，迟到 token 和换租约拒绝在真实 MySQL 验证。没有网络层丢弃 Broker 回执或真实进程 kill，不把“延后 markSent”写成 ACK 丢失实测。
3. 重复消费/执行超时/生成后提交前退出：真实 MQ 重复发送；真实 MySQL 双线程暂停旧模型、将该测试行期限设过去、恢复并显式新 attempt，旧执行者成功/抛错、替代执行者先后完成四种交错都无有效旧写入；真实 final SQL 失败全部回滚。期限由测试注入，未自然等待 10 分钟；没有生成后提交前真实 kill。
4. DB 提交后 ACK 丢失与 DB/MQ 故障：真实重复发送覆盖已提交终态幂等；真实 Broker 停机及恢复、实际 retry/DLQ/replay 完成。未丢弃真实消费 ACK、未停止共享 MySQL、未注入 JDBC 提交结果未知；DB 不可读和失败回写失败以 Mock 证明“不 ACK”异常契约，不冒充数据库故障实测。

独立 M1-A 探针只是历史最小兼容证据；本批项目内 Unit/Mock、真实 MySQL、真实 RocketMQ、假模型明确分开。未执行真实 DeepSeek 报告、真实浏览器端到端、部署切换/回退演练、长时间积压/容量/性能测试。没有计算受理或端到端 P50/P95。

## 6. 迁移、回退及残余风险

实际上线须对每个目标另获批准，停写、排空旧同步执行者，逐项核对历史 GENERATING/报告/FINAL/弱项；不允许旧同步与新异步同时运行。新代码默认关闭报告生成直到迁移和配置完成，没有自动降级为同步入口。accepting=false 只停新受理，排空后才能停派发/消费并换版本；短 shutdown 等待不是排空证据。完整步骤和告警/受控重放见运行手册。

默认保留任务表和消息；不 DROP，不删除历史业务数据，不自动恢复备份。唯一表约束防相同 attempt 重复，活跃任务唯一依赖所有业务入口按 Session 锁串行，不能允许外部 SQL 随意写活跃任务。历史报告告警需要人工核对弱项，系统不猜测补偿。消息可能重复、模型调用可能在旧执行超时后仍消耗外部资源，但旧执行者无法取得新 version 的数据库写权。

尚未验证的真实故障窗口、默认时限长期适用性、生产目标准入和依赖旧 POM 告警是明确剩余项。日志告警有实现，外部通知平台未配置。任务/消息保留策略未增加自动清理；当前独立 Broker 只作测试，不具备生产容灾保证。

## 7. 文件与已有修改保护

入场 50 个已修改/未跟踪文件 SHA/mode 快照 `/private/tmp/m1b-entry-files.json`；实施期间至追加工程记录前 50/50 未改变。本轮只在原台账、学习清单和案例库末尾追加 M1-B 内容，原前缀保留。Q1/Q2/U1、题库笔记、M1-A 方案及其他原未提交文件不改。全工作区 diff-check 的原有 HARD_AUTHOR_NOTES EOF 空行告警保留，不为通过检查修用户内容。

生产新增：

- [任务实体](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/entity/InterviewReportTask.java)、[Mapper 接口](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/mapper/InterviewReportTaskMapper.java)、[Mapper SQL](/Users/kun/Developer/projects/ai-interview-system/src/main/resources/mapper/InterviewReportTaskMapper.xml)：任务/投递两套状态、租约、CAS、DB 时钟、有限索引扫描。
- [FINAL Reader](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportFinalReader.java)：复用原 FINAL 及关联聚合校验。
- [配置](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/report/async/ReportAsyncConfiguration.java)、[参数](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/report/async/ReportAsyncProperties.java)：条件装配、独立调度和有界配置校验。
- [稳定消息](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/report/async/ReportTaskMessage.java)、[固定执行令牌](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/report/async/ReportExecution.java)、[Consumer](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/report/async/ReportMessageConsumer.java)：执行权及同步 ACK 边界。
- [运行时](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/report/async/ReportMqRuntime.java)、[Producer](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/report/async/ReportMessageProducer.java)、[Dispatcher](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/report/async/ReportOutboxDispatcher.java)：普通 MQ 发送消费、连接故障恢复、派发/收口。

生产修改：

- [Report Service](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java)、[事务 Service](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java)：同步入口退役，受理/执行/最终提交分离。
- [Session Mapper](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/mapper/InterviewSessionMapper.java)、[Session SQL](/Users/kun/Developer/projects/ai-interview-system/src/main/resources/mapper/InterviewSessionMapper.xml)：删除 stale 旁路、增加执行 CAS、溢出安全读取/失败。
- [pom.xml](/Users/kun/Developer/projects/ai-interview-system/pom.xml)、[应用配置](/Users/kun/Developer/projects/ai-interview-system/src/main/resources/application.yaml)、[隔离初始化器](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/config/EnvironmentIsolationInitializer.java)：SDK、默认 disabled 和测试/evaluation 隔离。
- [前端 composable](/Users/kun/Developer/projects/ai-interview-system/frontend/src/features/interview/composables/useInterviewReport.ts)、[文案](/Users/kun/Developer/projects/ai-interview-system/frontend/src/features/interview/copy/reportCopy.ts)、[View](/Users/kun/Developer/projects/ai-interview-system/frontend/src/features/interview/views/InterviewReportView.vue)：GENERATING GET 查询与请求世代防护。
- [唯一增量 SQL](/Users/kun/Developer/projects/ai-interview-system/sql/migrations/20261114_interview_report_task.sql)、[快照用途注释](/Users/kun/Developer/projects/ai-interview-system/sql/ai_interview.sql)：手工迁移与初始化用途分离。

测试与运行材料：

- 新增 `src/test/java/com/kun/aiinterview/interview/service/` 下 ReportUnitFixture、ReportDatabaseFixture、ReportTaskTransactionTest、ReportTaskMapperIntegrationTest、RealReportRocketMqSmokeTest；新增 `interview/report/async/` 下 ReportOutboxDispatcherTest、ReportMessageConsumerTest、ReportAsyncConfigurationTest。分别提供 Mock、真实事务及显式 opt-in MQ 证据。
- 修改同目录 InterviewReportServiceTest、InterviewReportApiServiceTest、InterviewReportTransactionServiceTest、InterviewReportTransactionServiceIntegrationTest、InterviewReportFinalConsistencyIntegrationTest、FullInterviewBusinessOfflineTest；以及 InterviewSessionMapperTest、StaleRecoveryCasIntegrationTest、EnvironmentIsolationInitializerTest、frontend/tests/InterviewReportView.spec.ts。将同步/stale 预期迁移到新契约，保留核心故障/权限断言；FINAL 24 项未删除。
- [test 配置](/Users/kun/Developer/projects/ai-interview-system/src/test/resources/application-test.yaml)、[test-real 配置](/Users/kun/Developer/projects/ai-interview-system/src/test/resources/application-test-real.yaml)：MQ 默认关闭；仅 test 限制 Hikari 资源。
- [Compose](/Users/kun/Developer/projects/ai-interview-system/tools/m1b/compose.yaml)、[Broker 配置](/Users/kun/Developer/projects/ai-interview-system/tools/m1b/broker.conf)、[测试运行器](/Users/kun/Developer/projects/ai-interview-system/tools/m1b/run-tests.py)、[运行手册](/Users/kun/Developer/projects/ai-interview-system/tools/m1b/README.md)：独立资源与目标保护。
- 本报告、[数据库准入](/Users/kun/Developer/projects/ai-interview-system/docs/engineering/2026-11-14-M1B-DATABASE-ADMISSION.md)、[工程台账](/Users/kun/Developer/projects/ai-interview-system/docs/engineering/IMPROVEMENT_LEDGER.md)、[学习清单](/Users/kun/Developer/projects/ai-interview-system/docs/engineering/SOURCE_LEARNING_BACKLOG.md)、[案例库](/Users/kun/Developer/projects/ai-interview-system/docs/engineering/ENGINEERING_CASEBOOK.md)：执行证据、未验证项与学习待确认记录。

本地 HEAD 不变；本轮通过 GitHub 连接器实时核对远端 main 同为 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，不将本轮未提交源码算作已发布。学习状态仍待用户本人讲解、复现或独立修改确认。

最终保护核验：50 个入场文件中 47 个 SHA 原样保留，3 份工程记录原始前缀 SHA 保留且仅追加；暂存区为空。本批作用域 diff-check 和新增文件行尾空白检查通过；全工作区仍只有入场 HARD_AUTHOR_NOTES 的既有 EOF 空行告警。实施中一次自动审批拒绝覆盖事务测试文件，证明该文件入场无用户修改并备份后改用审阅式补丁完成；没有覆盖用户改动或留下审批阻塞。
