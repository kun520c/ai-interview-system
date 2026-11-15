# M1-C 故障注入与问题记录

测试仅使用专用 MySQL、RocketMQ 和合成数据；模型替身与真实 DeepSeek 分开。`faults-initial.jsonl` 原样包含首轮探针错误，`faults-retest.jsonl` 保留修正后的唯一重测。所有实际进程终止均校验测试进程 PID、Surefire 命令及父子关系后只发 SIGKILL 给该测试 JVM；Maven 非零退出是预期注入结果，不计为 JUnit 通过。

下表 task 编号和 23.379 秒为所有权锁修复前的首轮证据。首次修复版另有[五组重跑记录](m1c-20261114/faults-after-owner-fix.jsonl)，但该版后来发现锁前后查询会命中 MyBatis 一级缓存。最终修复后的源码再次重跑五组进程级场景，**5/5 场景达到各自断言**；场景 1 的恢复阶段启动最终 B JAR `f3b6095d5ed9cc465dfd8f000a1ea658a08c771576e19e8817e134de427e5334`，其他故障窗口由相同源码的 Surefire 测试进程和测试替身触发，不称其全部为 JAR 进程。提交后退出/停复 Broker（task 817）的单次恢复为 **23.233 秒**，模型返回后（task 821）仍需显式重试。[最终版原始记录](m1c-20261114/faults-after-cache-fix.jsonl)。五组场景不是五条 JUnit 测试计数，刻意杀死的进程仍不计 JUnit 通过。旧执行者四交错及归属检查回归已另行在真实 MySQL 验证。

| 场景 | 注入和结果 | 自动恢复与边界 |
|---|---|---|
| 1. DB 提交后、发送前退出 | Spring 测试进程在 Session GENERATING、Task QUEUED/PENDING、发送次数 0 后杀死；见 task 421。Broker 随后停机，真实应用重启并发生一次失败发送；恢复后 READY/SUCCEEDED/SENT，发送尝试 4，报告 1、弱项 1、模型替身 1。 | 是；从专用 Broker `start` 命令到观测成功 23.379 秒，单次样本非 RTO。首次进程由测试 fixture 调 Service 而非真实 HTTP 入站。 |
| 2. Broker SEND_OK、未记 SENT 即退出 | task 422 在发送成功、租约 LEASED 时杀测试进程；测试将租约设为 6 秒以自然到期，再由真实 Dispatcher 与测试 Producer 适配器重发到真实 Broker。 | 两次投递均被观察到；最终 READY/SUCCEEDED/SENT、报告 1、弱项 1、模型替身 1。不是网络层丢弃发送回执；重发用测试适配器，非完整生产 Producer bean。 |
| 3. 复制投递与竞争 | 一个独立 Topic、两个不同消费组的两个真实消费者同时收到同一消息，barrier 促成 CAS 竞争；另外场景 2 对同任务实际发送两份。 | 两个实例实际进入处理，模型替身 1、报告 1、弱项计数 1。不同组是有意制造竞争，不是同组负载均衡或跨主机故障测试。 |
| 4. 业务提交后、ACK 前退出 | task 424 已 READY/SUCCEEDED、报告和弱项各 1；测试 consumer 进入成功回调但尚未返回 ACK 时杀进程，重启独立消费者后真实 Broker 重新投递。 | 重新投递被观测；第二进程模型调用 0，报告/弱项未重复。本探针直接发消息，delivery 仍 PENDING；不是抓包证明的网络 ACK 丢包。 |
| 5. 模型返回后、报告事务前退出 | task 426 已 RUNNING，模型替身返回一次后阻塞完成事务入口并杀进程；期限测试值 5 秒，随后扫描收口并显式 POST 服务调用创建新任务。 | 旧任务 `FAILED/EXECUTION_EXPIRED`，新任务最终 READY；合计两次模型替身、报告 1、弱项 1。**不会自动重新生成**；本案例模型为替身，超时由测试配置缩短。整场景约 15.99 秒，包含测试 JVM 启动及人工等效重试，不是自动恢复时间。 |
| 6. 旧任务超时后新执行者接管 | `oldReportVersionCannotCommitOrFailReplacement` 使用真实 MySQL、两个并发执行线程和模型替身；将目标测试任务 deadline 调到过去，释放旧/新结果顺序及旧模型成功/失败形成四种交错。 | **4/0/0/0**；新显式 attempt 的报告与一次弱项有效，旧执行者无法提交或失败覆盖新版本。不是自然等待 600 秒，也不是跨机器 MQ 故障。 |
| 7. MQ 停机、恢复与补偿 | 与场景 1 同一真实 Broker 故障：停专用 Broker、观察任务 PENDING 和一次发送失败，启动原 Broker 后原应用调度继续发送。 | 单次自动恢复 23.379 秒；长期离线达到发送/排队上限会变 FAILED，需显式新申请。未停其他项目容器。 |
| 8. 应用重启恢复待处理任务 | 与场景 1 同一事务已提交而未派发的真实进程终止，随后启动正式 B JAR 和定时派发。 | QUEUED/PENDING 自动完成；对已经 RUNNING 的死亡消费者则如场景 5，最终受控失败并需新申请。 |

最终版五组原始记录还提供整个探针的起止时间：场景 1/7/8 共 **34.400 秒**（其中 Broker 启动到观测成功 **23.233 秒**）、场景 2 **19.947 秒**、场景 3 **10.751 秒**、场景 4 **12.036 秒**、场景 5 **12.318 秒**。这些总时长包括进程启动、注入及断言，不等于各故障点的恢复时长；除场景 1 的明确计时外，其余窗口未单独测量故障到恢复的时间。场景 6 的四条 MySQL 交错测试合计 2.211 秒，注入 deadline 而非等待自然超时，也不能当作恢复时间。

## 复现、根因、修复与尚未解决

0. **M1C-OWNERSHIP-LOCK-01（已修复，MQ 改造引入）**：真实 MySQL 中先让会话所有者的事务持有 Session 行锁，非所有者 POST 对应 Service 在 2 秒内未返回资源不存在；修复前定向 **1/0/1/0**，错误为等待锁的 `TimeoutException`。根因是 `acceptReportTask` 先 `getReportVersionForUpdate`，后校验 `userId`。首次修复用相同 Mapper 查询在锁前后读取完整 Session；真实 MySQL 测试在锁等待期间改变合成会话归属后 **1/1/0/0**，发现 MyBatis 事务内一级缓存重用了锁前结果，未正确拒绝。最终修复改为独立的普通 `getReportOwnerId` SELECT 预查，再加锁并通过不同 Mapper 查询读取完整 Session、再次校验；[真实 MySQL 回归](../../src/test/java/com/kun/aiinterview/interview/service/ReportOwnershipLockIsolationTest.java)、Mapper 和归属变化单测在定向 **75/0/0/0** 中通过。两次修复前失败日志均保留。已证明在目标行被锁时非所有者请求可及时拒绝、模拟归属变化时旧所有者被拒绝；未进行高并发恶意压测，也不将该证据描述为已证明实际跨用户数据泄露。
1. **M1C-HARNESS-01（已修复，测试工具）**：试运行 01 的同步 POST 返回 502。根因是 Java HTTP 客户端使用 chunked 请求体，模型桩只接受 Content-Length 而返回 400；在 `tools/m1c/benchmark.py` 中增加有界 chunk 解码。试运行 03 和正式 594 次桩调用通过，保留 `pilot-01.log`；未改生产代码。
2. **M1C-HARNESS-02（已修复，测试口径）**：试运行 02 将 UTC `started_at` 与本地时区 `updated_at` 直接相减，出现约 8 小时的假执行时长。改为以数据库 UTC 偏移转换审计时间，再计算；试运行 03 和正式数据可追溯。原错误 TSV 保留；`updated_at` 仍可能被迟到派发确认改写，所以它只是近似值。
3. **M1C-HARNESS-03（已修复，故障探针）**：首轮场景 5 为 `1/0/1/0`，错误为 Mockito 尝试 spy Spring CGLIB 事务代理时未能解包，未到故障窗口。改为真实事务 bean 负责领取，只在完成事务入口使用测试替身阻塞；重测真实终止后成功到达显式重试结果。首轮和重测日志均保留，未改生产代码。
4. **M1C-HARNESS-04（已修复，证据导出）**：首次 MySQL CLI 导出将中文转为 `?`；浏览器和数据库原报告未改变。显式使用 `utf8mb4` 重新导出并核验中文，错误导出保存在 `backups/M1C_20261114_1521UTC/real-report-export-encoding-failed.json`。
5. **M1C-RECOVERY-01（未修复，原 M1-B 设计限制）**：已领取任务的消费者在模型返回后崩溃，重复消息不能再次领取；到执行期限后原任务受控失败，用户需明确重试，重试可再次产生外部调用。当前没有无上限自动调用，但也没有自动成功恢复。若产品要求自动生成，应先设计持久、可审计的有限重试预算及旧执行者 fencing，针对提交不确定和并发重复补测；本轮未改变任务状态机或数据库结构。
6. **M1C-REPORT-QUALITY-01（未修复，非 MQ 新引入）**：两次真实模型报告结构与 Java 分数正确，但两个合成 FINAL 的 `strengths=[]`、`missing_points=[]`、`correction=null`；报告仍断言考生未展现 HashMap 底层结构、冲突和扩容等细节。[真实合成报告及输入摘要](m1c-20261114/real-report-evidence.json)可复核。`InterviewReportPromptBuilder` 只发送问题快照和评价，不发送原答；`DeepSeekInterviewReportGenerator` 只验证 JSON 字段、数量和长度，不能证明文本事实来自评价。两次真实结果显示未经证据支撑的具体判断；无法由此估计总体错误率，也不能简单改一句 Prompt 就宣称解决。需设计证据约束与独立人工留出验证；不修改现有评分 v4、题库或 RAG。

真实 MySQL 任务受理同事务失败回滚、FINAL 绑定校验和唯一弱项由本轮定向回归覆盖。JDBC 提交结果未知、MySQL 真停机、Broker 磁盘永久损坏、生产多副本/长期积压、真实 DeepSeek 成功后 kill、同组多进程重平衡与模型语义正确率均未实测，不得写成已通过。
