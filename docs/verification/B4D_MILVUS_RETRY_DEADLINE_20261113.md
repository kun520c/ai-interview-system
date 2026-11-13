# B4-D：Milvus SDK 重试与超时治理验收

执行日期：2026-11-13（Asia/Shanghai）。基线：`01ac774caff491b978dd2268e8b427355a64b97a` 加本轮未提交修改。

本轮确认 **SDK 2.6.20 缺陷实际可达**，并完成应用 Spring Milvus 客户端的最小修复：关闭有缺陷的多次 callable 重试循环，给构造期 RPC 加可取消的 gRPC deadline，拒绝未知 Collection 存在性与无效毫秒超时。生产依赖仍为 2.6.20。完整普通回归 **1732/0/0/19**，打包成功；真实 MySQL 已验证，真实 Milvus、Embedding、DeepSeek 未执行。

## 1. 基线与原有修改保护

- 项目：`/Users/kun/Developer/projects/ai-interview-system`；分支 `main`。实际环境：macOS 26.7.1 ARM64、Temurin 21.0.13、Maven Wrapper 3.9.16。
- 本地 B4-A：`004e434d3c1352c15d022bc1bf08abdfb02f4a02`；B4-B：`f764c087f75d6b515b09bf2a0aef3f1d9a20c906`；B4-C/C3：上述 HEAD。三个提交均保留。
- `git ls-remote` 在沙箱内 DNS 失败，允许网络环境中 SSH 公钥认证失败。随后 GitHub 连接器 `fetch_commit(main)` 实时核验远端为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，与本地缓存 `origin/main` 一致；祖先计数为远端独有 0、本地独有 3。B4-A～C 尚未进入该远端 main，不据此判断其他分支是否发布。未 fetch、未写引用。
- 入场已有 `AGENTS.md` 的 6 行维护说明、`mvnw` mode 100644→100755、未跟踪 Compose、MQ 可行性文档和两份工程台账。台账尚未提交，本轮只定向更新其 B4-D 内容及当前汇总；其他用户内容保留。
- 结束时发现未跟踪的 MQ 可行性文档与入场摘要不同，属于本轮未编辑路径的外部变化；保留当前内容，未恢复快照。AGENTS、mvnw、Compose、HEAD/index 摘要均一致。
- `compose.mac.local.yaml` 指向既有本地环境，本轮未启动、修改它；`mvnw` 正文及 0755 权限保留。工作期间另观察到根目录与 docs 下未跟踪 `.DS_Store`，未改动或删除。
- 入场摘要／副本：`/private/tmp/ai-b4d-20261113/entry-manifest.json`、`entry/`。最终保护核验见同目录 `preservation-check.json`。暂存区为空，HEAD/index 保持不变；未执行 add/commit/push/reset/stash/clean/rebase。**B4-D 无提交 SHA，也未推送。**

## 2. 实际依赖与源码证据

Maven 实际解析命令：

```text
./mvnw -B -ntp -o dependency:tree '-Dincludes=io.milvus:milvus-sdk-java,io.grpc:*' -DoutputFile=/private/tmp/ai-b4d-20261113/dependency-tree.txt
```

退出 0，实际 `io.milvus:milvus-sdk-java:jar:2.6.20:compile`。该包将 gRPC 重定位到 `io.milvus.shaded.io.grpc`，没有独立依赖树条目不意味着不使用 gRPC。测试使用 JAR 内真实重定位类型，未引入新依赖。

依据包括本机 JAR、`javap -c -p` 字节码，以及从 Maven Central 下载的准确 [2.6.20 sources.jar](https://repo.maven.apache.org/maven2/io/milvus/milvus-sdk-java/2.6.20/milvus-sdk-java-2.6.20-sources.jar)、[2.6.21 sources.jar](https://repo.maven.apache.org/maven2/io/milvus/milvus-sdk-java/2.6.21/milvus-sdk-java-2.6.21-sources.jar)。[官方变更记录](https://github.com/milvus-io/milvus-sdk-java/blob/master/CHANGELOG.md)提及 2.6.21 修复 `RpcUtils.retry()` 异常吞掉问题；本轮结论来自准确版本源码及执行测试，而非仅据 changelog。

SHA-256：

- 2.6.20 二进制：`0233121cda704d5f6945f7e1631a5318a1d068bde9f8d5b1638e33127b99d512`。
- 2.6.20 源码：`5b85f1518372ed3b928c3b8744f6f8d5d46b5e32f66b748f86ddbebb35ed7200`。
- 2.6.21 源码：`480474c41cc5f8319ddd133e333fd2c41744912b8304c9216da9152aa5703ce6`。

临时证据位于 `/private/tmp/ai-b4d-20261113/`：`sdk-hashes.json`、`RpcUtils.bytecode.txt`、`2.6.20/io/milvus/v2/`、`2.6.21/io/milvus/v2/`。源码入口：`client/RetryConfig`、`utils/RpcUtils.retry`、`client/MilvusClientV2.connect/getRpcStub/retryConfig`、`utils/ClientUtils.checkDatabaseExist`、`service/vector/VectorService`、`service/collection/CollectionService`。均为 V2；没有套用 V1 的连接/重试配置。

## 3. SDK 的真实行为与问题分级

### B4D-P2-01：异常吞掉与长等待，已复现并规避

- 默认 `maxRetryTimes=75` 是**含首次的 callable 总尝试上限**，不是额外重试 75 次。默认初始退避 10ms、乘数 3、上限 3000ms、`retryOnRateLimit=true`、`maxRetryTimeoutMs=0`（禁用总重试计时）。官方 V2 API 为 `retryConfig(RetryConfig)` / `withRetry(RetryConfig)`；两者都修改客户端共享 `RpcUtils`，不能在并发请求中临时切策略。
- `insert/delete/search/hasCollection/describeCollection/describeIndex/createCollection` 都由 `MilvusClientV2` 包装进同一个 `RpcUtils.retry`。Callable 不一定等于单个底层 RPC。
- 2.6.20 的 gRPC 不重试名单为 DEADLINE_EXCEEDED、PERMISSION_DENIED、UNAUTHENTICATED、INVALID_ARGUMENT、ALREADY_EXISTS、RESOURCE_EXHAUSTED、UNIMPLEMENTED。UNAVAILABLE、UNKNOWN、INTERNAL、CANCELLED 等其他状态进入重试分支；**CANCELLED 在该版本并不在不重试名单内**。
- Milvus server status 只有 legacy RateLimit=49 或新 server code=8 在开关开启时重试；普通业务错误直接抛出。global cluster 另有 routing refresh 特例，项目普通本地 URI 不启用；本轮不验证全局集群。
- 其他 callable 异常包装为 `MilvusClientException(CLIENT_ERROR)`，不继续重试。单次分支将 gRPC 异常包装为 RPC_ERROR，并保留 cause；MilvusClientException 原样传播。
- 多次分支使用 `System.currentTimeMillis` 检查总重试耗时；超时检查中抛出的 TIMEOUT 被本地 `catch (Exception ignored)` 吞掉。最后一次尝试后抛出的耗尽异常同样被吞掉，循环最终 `return null`。退避中的 InterruptedException 也被吞掉并清除中断标记。
- 仅把最大次数改成 2/3 或设置 `maxRetryTimeoutMs` 不能修正这些传播问题。默认 74 次退避之和理论为 207.64s；这是源码推导，**没有故意运行三分钟故障测试，也不是实际业务耗时或 SLA**。

### B4D-P2-02：初始化未知状态与 void 静默成功，已复现并阻断

- 原 `Boolean.TRUE.equals(hasCollection(...))` 把耗尽后的 `null` 当成“不存在”，继续发创建请求；新增测试在原代码上失败。
- `createCollection` 的返回类型为 void，底层耗尽返回 null 甚至会表现为正常结束。用真实 SDK + 合成 gRPC、2 次短退避复现 create 收到 2 次 UNAVAILABLE，initializer 仍正常返回。仅增加 hasCollection 非空检查不能解决这个 void 旁路；必须一起限制客户端重试策略。
- insert/delete/search 已有空响应与非预期结构检查，会转受控 `ExternalServiceException`；未发现它们将 null 当成成功的生产漏洞。搜索失败仍失败，不能伪造成零证据成功。

### B4D-P2-03：构造器数据库检查无截止时间，已复现并修复

- 构造器 Connect RPC 使用 `connectTimeoutMs`；随后 `checkDatabaseExist` 直接在 `.withWaitForReady()` stub 上发 `listDatabases`，不应用 connect timeout 或 rpcDeadline。
- 合成服务观察原 SDK 此 RPC 的 `Context.getDeadline()==null`；修复前工厂测试也观察不到 deadline。不是据运行卡住猜测。
- 修复在构造期附加 gRPC cancellable Context，以 connectTimeout 作为 Connect + 数据库检查的共享预算；实际 transport 可观察 deadline/cancellation。成功或异常均 detach、cancel 并关闭仅用于初始化的定时 executor；不引入业务执行池或 Future 超时包装。

### B4D-P2-04：正 Duration 可能转换为 0ms，已复现并修复

原校验允许 1ns，`toMillis()` 得到 0，SDK 将 rpcDeadline=0 解释为禁用。过大 Duration 也可能溢出。现在要求至少 1ms 且不超过 `Duration.ofMillis(Long.MAX_VALUE)`；null 继续由 NotNull 拒绝，URI/名称/维度/令牌隐藏等原校验保留。

## 4. 超时语义、方案取舍与调用链

`requestTimeout → ConnectConfig.rpcDeadlineMs`。`getRpcStub()` 在每个外层 callable 尝试开始时设置新的绝对 deadline；同一次尝试里的多个 SDK 内部 RPC 共用该 stub 的剩余时间，**不是每个内部 RPC 都重新得到完整时间**。旧 B4-A/B4-C 的“每次 RPC 10s”应据本次源码细化为“每次外层尝试的新 stub deadline”。

- search 通常发一次 Search RPC；insert 可能先 describe Schema，SchemaMismatch 时还有 SDK 内部递归刷新和再插入，沿用同一个 stub deadline。`maxRetryTimes=1` 不等于所有情形下底层 insert 恰好一次。
- 按模板 filter 删除不需要先取 PK Schema；SDK 其他按 ids 的路径可能读取 Schema。
- createCollection 可包含 create、createIndex、load 等内部 RPC。本项目使用显式 Schema + COSINE/AUTOINDEX，SDK 内部 sync=false；该方法的同次内部调用共享 deadline。initializer 多个高层调用之间仍分别创建 deadline，不能把全部启动检查说成一次 requestTimeout。

选择：**保持 SDK 2.6.20，在单例构造完成后、发布 Bean 之前固定 `maxRetryTimes=1`**。这是官方支持的单次分支，依据是确定性传播缺陷与写入结果不确定性，不是“1 次或 3 次一定性能最好”的假设。代价：短暂 UNAVAILABLE/限流不再由 SDK 自动掩盖，失败更早暴露，后续恢复依赖既有显式业务请求。

未选择的方案：

1. 只配置较小次数和总预算：仍进入有缺陷的循环，耗尽/超时/中断风险尚在。
2. Future.get(timeout) 或新业务线程池：不能证明外部操作取消，容易留下后台写入；未采用。
3. 复制 SDK retry 实现：扩大维护面，且需要同时治理内部 RPC、取消和线程状态；当前不必要。
4. 升级 2.6.21：准确源码 diff 显示移除吞异常 try/catch、在退避前检查预算、在中断时恢复标记并失败；确实修复本次可达循环缺陷。但默认仍 75 次，构造期数据库探测未因此获得 deadline，成功 callable 也不是严格总时限检查。本轮对 ConnectConfig、DataType、IndexParam、InsertReq/DeleteReq/SearchReq 的版本源码比较均相同，未证明全部二进制/协议与真实服务兼容。升级需单独审批并重跑向量/Schema/索引/真实隔离服务测试；**未升级、无需阻断本次配置修复**。

修改前：

`Spring Bean → SDK Connect(5s) → 无 deadline 的 ListDatabases → 默认 75 次 RpcUtils → 每次新 10s stub → SDK Service → gRPC → null/异常/成功`。

修改后（默认 local 配置值未变）：

`Spring Bean → 初始化 Context(connectTimeout=5s) 内 Connect + ListDatabases → 固定一次 callable → SDK Service(同次内部 RPC 共用 requestTimeout=10s deadline) → MilvusVectorStoreClient 校验/ExternalServiceException`。

业务链保持：

- 检索：EvaluationRetrievalAdapter → Query Embedding → KnowledgeRetrievalService → VectorStoreClient.search → MySQL 二次资格/身份校验 → Prompt；搜索错误直接传播，不生成伪证据。
- 处理：claim attempt → chunk/Embedding → 生成 attempt-index 向量 ID → **调用 insert 前**标记 milvusWriteMayExist → insert → 短事务锁当前 attempt、落 chunks/READY。失败时只补偿本次 ID，再按本次 attempt 标记 FAILED。
- MySQL 提交确认丢失仍锁行确认；确认不可用保留可能已提交向量，旧 attempt 不得影响替换任务；新请求 FAILED/stale CAS 重试不同于 SDK 自动重试。未改 requestId、attemptId、Session CAS、分数、RAG trace、主问题与报告规则。

## 5. 故障注入与真实次数/耗时

下列耗时来自 `milvus-final.log` 的 System.nanoTime 记录，为本机一次观测，不作性能提升结论。计数区分 callable、服务器收到 RPC、Mock SDK 调用。受控夹具只使用合成内容与临时端口。

- 默认 75 次 callable，测试仅将初始退避改为 0：实际 75 次，1ms，返回 null。没有把该 1ms 写成生产默认耗时。
- UNAVAILABLE→UNAVAILABLE→成功：3 次 callable，2ms；server RateLimit 同序列：3 次，2ms。
- 总重试预算设 1ms、退避 15ms：UNAVAILABLE 3 次后成功耗时 32ms；RateLimit 3 次后成功 31ms。均证实预算异常被吞掉。
- 7 种不重试 gRPC 状态各 1 次，记录为 0ms（毫秒向下取整）。普通 server 错误和非 SDK callable 异常各 1 次，无重试。
- CANCELLED + 人工设置调用线程中断：仍执行第 2 次并成功，中断位被清除；单次分支 UNAVAILABLE 仅 1 次并保留原因链。
- 真实 loopback gRPC 的第三次成功：每次响应延后 200ms、每次尝试 deadline 500ms，收到 3 次 HasCollection，628ms，总等待超过单次 500ms；同时验证三次绝对截止时间向后移动。
- 原 SDK 2 次耗尽：HasCollection 收到 2 次，2ms，结果 null；CreateCollection 收到 2 次，3ms，void 调用静默返回。
- 修复后的 UNAVAILABLE Search：服务端收到 1 次，0ms，ExternalServiceException；server RateLimit Search：1 次，32ms，同样失败，不伪造空结果。
- 停滞构造期数据库检查：ListDatabases 1 次，803ms，配置 800ms，服务端观察 cancellation，调用失败。
- 停滞 Insert：Insert 1 次，307ms，配置 300ms；调用失败且服务端观察 cancellation。随后先补偿、再模拟不配合取消的服务端迟到写入，旧向量仍出现；第二次定向删除移除它，非目标向量始终保留。
- 主动中断停滞 Search：1 次，3ms，调用失败、中断标记保留、服务端观察 cancellation；没有进入重试循环。
- 已写入但响应 UNAVAILABLE：Insert 1 次，2ms（含后续操作），精确 ID 删除 2 次，分别删除 1/0 条，非目标数据保留。
- 真实隔离 MySQL + 真实 adapter + Mock SDK，UNAVAILABLE/DEADLINE_EXCEEDED/null 三例：每例首次 insert 1 次后补偿、FAILED、零 chunk；重复删除幂等，再显式处理成功。总 SDK insert=2、delete=2；各 21/13/13ms。这些不是服务器 RPC 次数。

完整回归再次观测：构造超时 804ms、insert deadline 304ms、第三次成功 642ms。不同运行不混写，未断言精确毫秒或系统调度硬上限。测试使用 latch、server-side Context listener、scheduled response；测试自身 future/join 超时只防夹具卡住，不当作生产取消实现。

## 6. 修改文件及责任

生产只改 3 个文件：

- `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusConfiguration.java`：milvusClient 固定单次策略，构造阶段 Context deadline 和资源释放；不调整正常配置数值。
- `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusProperties.java`：毫秒可表达性与非零边界。
- `/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusCollectionInitializer.java`：未知存在性 fail-fast，不误创建。

新增测试：

- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusSdkRetryBehaviorTest.java`：15 个准确版本特征测试，故意记录原 SDK 缺陷，升级时须复核这些断言。
- `/Users/kun/Developer/projects/ai-interview-system/src/test/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusGrpcDeadlineTest.java`：12 个真实 SDK/loopback transport 测试；包含未修 SDK 对照与应用配置路径。

扩展测试：MilvusConfigurationTest（策略与 close）、MilvusPropertiesTest（转换边界）、MilvusCollectionInitializerTest（null）、KnowledgeAttemptFencingIntegrationTest（新增 3 项真实 MySQL + adapter 补偿/显式恢复）、KnowledgeRetrievalServiceTest（新增 2 项空响应/超时传播，不回填假证据）。路径均在上述仓库的 `src/test/java/com/kun/aiinterview/knowledge/` 下。

文档：本报告及 `docs/engineering/IMPROVEMENT_LEDGER.md`、`docs/engineering/SOURCE_LEARNING_BACKLOG.md`。原 B4-A/B/C/C3 报告保留。pom、SQL、YAML、MilvusVectorStoreClient、两项 Knowledge Service 生产实现及前端均未改。

## 7. 分层验证、命令与完整运行历史

T/F/E/S = Tests/Failures/Errors/Skipped，包含参数化实例与跳过；不同运行重叠，不能相加当成独立总量。

环境：本轮专用 `ai-interview-b4d-20261113-mysql`，mysql:8.0.45 ARM64，127.0.0.1:13326、`/var/lib/mysql` tmpfs、timezone +08:00，只有 `ai_interview_test` 与系统库。DDL 仅临时副本替换 CREATE DATABASE/USE，未改仓库 Schema。未连接 ai_interview/evaluation 或原 Compose。临时随机密码仅通过子进程传递，未入 Git 配置。

`/private/tmp/ai-b4d-20261113/run_maven.py` 为每次 Maven 清除 RUN_REAL_*、SPRING_*、MILVUS_*、DEEPSEEK_*、旧 DB/TEST_DB 与 JVM/Maven 覆盖，再注入专用测试库、DEEPSEEK_ENABLED=false 与 Mockito javaagent。每轮先归档旧 Surefire，再复制本轮 XML。没有绕过 EnvironmentIsolationInitializer 或双层 opt-in。下列命令均由该执行器启动：

```text
sh mvnw -B -ntp -Dtest=MilvusSdkRetryBehaviorTest test
sh mvnw -B -ntp -Dtest=MilvusConfigurationTest,MilvusPropertiesTest,MilvusCollectionInitializerTest test
sh mvnw -B -ntp -Dtest=MilvusGrpcDeadlineTest test
sh mvnw -B -ntp '-Dtest=Milvus*Test,KnowledgeAttemptFencingIntegrationTest' test
sh mvnw -B -ntp '-Dtest=Knowledge*Test,AdminKnowledge*Test,Embedding*Test,SpringAiEmbedding*Test,Vector*Test,OrphanVectorMaintenanceTest,Rag*Test,EvaluationRetrievalAdapterTest,EvaluationOrchestrationServiceTest,StaleRecovery*Test' test
sh mvnw -B -ntp test
sh mvnw -B -ntp -DskipTests package
git diff --check
```

实际历史：

1. `sdk-characterization`：15/0/0/0，退出 0，修复前实际 JAR 特征证据。
2. `contract-before`：45/3/0/0，退出 1。三项预期回归失败：策略未装配、null 存在性未拒绝、Duration 边界未拒绝。
3. `grpc-before`：testCompile 失败，测试 0，JUnit F/E/S 不适用；退出 1。新增夹具 documentId 使用 int 而 record 要求 Long，改为 1L。
4. `grpc-before-valid`：10/8/0/0，退出 1。7 项被前置配置断言发现 75≠1 而提前终止，避免长时间等待；1 项构造数据库无 deadline。另 2 项原 SDK 特征通过。不能将 8 个失败说成 8 个独立生产 bug。
5. `milvus-focused`：159/0/0/0，退出 0，首轮生产修复通过。
6. `expanded-focused`：testCompile 失败，测试 0，JUnit F/E/S 不适用，退出 1；修正 SDK builder 方法名 `InsertCnt`。
7. `expanded-focused-valid`：180/3/0/0，退出 1。新增 MySQL 夹具配置 3 维而既有 fake embedding 是 1 维；生产维度防线正确拒绝。仅修正夹具维度，未放松生产校验。
8. `milvus-final`：**180/0/0/0，退出 0**。Milvus 配置/adapter/Schema/SDK/gRPC 共 161，真实 MySQL fencing/补偿 19。
9. `rag-knowledge`：**450/0/0/10，退出 0**。10 skipped 全为 KnowledgePersistenceIntegrationTest 的双层门禁；普通真实 MySQL Mapper/事务均执行。
10. `full`：**1732/0/0/19，退出 0**，1713 非跳过。136 个测试套件。Maven 记录总耗时 28:17；少数无 Milvus 的既有 Unit/业务测试出现异常长 wall time（例如快照 Mock 测试 1049.811s），本轮未采样证明原因，不将该总时长解释为 Milvus 等待或性能基准。Milvus gRPC 套件为 2.433s。
11. `package`：退出 0，使用 skipTests，测试计数不适用。JAR 检查退出 0；最后 git diff --check 退出 0。

全量关键类均 F/E/S=0：MilvusVectorStoreClientTest 89、MilvusConfigurationTest 4、MilvusPropertiesTest 23、MilvusCollectionInitializerTest 18、MilvusSdkRetryBehaviorTest 15、MilvusGrpcDeadlineTest 12、KnowledgeAttemptFencingIntegrationTest 19、KnowledgeRetrievalServiceTest 21、RagTraceMapperTest 11、RagTracePersistenceServiceIntegrationTest 3、FullInterviewBusinessOfflineTest 112、B4-B 日志 36、B4-C 分类 42/本机 HTTP 4、B1 并发重放 18、B2 领取后恢复 3、B3 FINAL 一致性 24。

19 skipped：RealEmbeddingSmokeTest 1、RealMilvusVectorStoreSmokeTest 1、KnowledgePersistenceIntegrationTest 10、RealKnowledgeDocumentProcessingSmokeTest 2、RealKnowledgeDocumentProcessingFailureIntegrationTest 2、RealMilvusSchemaValidationSmokeTest 2、AiSystemBaselineEvaluationTest 1。**它们不是通过的真实服务证据。**

证据：上述 label 的 `*.log`、`*-summary.json`、`*-reports/`、最终三轮 `*-suite-counts.json`。临时目录不是长期持久存储，仓库报告与可运行测试保存结论和复现入口；不上传包含运行环境的原始日志。

打包产物：`/Users/kun/Developer/projects/ai-interview-system/target/ai-interview-system-0.0.1-SNAPSHOT.jar`，73,090,767 字节；含本次 3 个生产类及 milvus-sdk-java-2.6.20.jar；未发现 application-local.yaml/.env/私钥类文件名。`.jar.original` 为 repackage 前包。检查依据见 `package-inspection.json`；这不是全仓库秘密审计。

## 8. 数据一致性与不能证明的边界

- 真实 MySQL 实际执行 claim/FAILED/READY CAS、attempt-index 对应、事务回滚、lost commit ack 锁行确认、替换任务 fencing、二次资格 SQL。向量状态使用合成服务/替身，不冒充真实 Milvus 持久化、复制或一致性级别验证。
- timeout/UNAVAILABLE 不等于“未写入”。失败仍补偿本次 ID；删除 0 条仍成功。旧任务、非目标向量与已确认 MySQL 提交的当前批次受原规则保护。搜索异常不构造空 RetrievalResult；只有合法成功空响应才是零证据。
- 迟到写入可能晚于一次补偿，留下物理孤儿；MySQL 回填不允许它成为有效证据，但不能声称孤儿已经最终清理。既有 OrphanVectorMaintenance 必须在停写/旧实例停止并显式确认目标后单独运行，本轮未运行该 CLI，也未删除真实向量。
- **独立维护 CLI 边界**：`OrphanVectorMaintenance.main` 仍直接 new MilvusClientV2，未使用 Spring 工厂；其默认重试和构造期数据库检查不自动继承本次策略。它不是请求路径，也不自动运行。本轮审查并记录此剩余 P2，未把维护工具/iterator 生命周期扩展进生产修复；后续如治理该入口，应同时核实其 V2 queryIterator 返回的独立 iterator 分页路径。不能宣称所有仓库 SDK 实例都已治理。
- 一次 callable 仍可能有 SchemaMismatch 内部刷新/再插入；gRPC 透明传输恢复也未被本次配置承诺禁用。因此没有 exactly-once 或服务器恰好执行一次结论。
- Context deadline 确实传给了本机 gRPC 并可观察取消；服务端业务可能不响应取消。线程中断在本机单次分支生效，不等于 HTTP 客户端断开会自动中断 Servlet，也不等于 stale 接管会取消旧任务。
- 连接建立、DNS/本机 hostname、初始化清理（SDK close 最多等待其自身期限）、序列化/CPU、JVM 暂停/调度、数据库/Embedding/DeepSeek 等不共享统一业务预算。默认 5s 初始化 RPC scope 和 10s callable stub deadline 不是整链严格 wall-clock 上界。没有 `Future.get(timeout)` 假取消；未做公网 TLS/代理、系统 suspend、真实生产容量验证。
- SDK 原始异常及其内部日志的全局脱敏不是 B4-D 内容；本轮仅合成数据，B4-B/C DeepSeek 安全边界完整回归通过。

## 9. 10～15 分钟源码学习导航

学习状态全部 **待学习**；工程验证不等于开发者已掌握。详见 L-B4D-01～07。

1. 0～2 分钟：读 SDK RetryConfig 与 RpcUtils.retry。手算 75 是总尝试还是附加次数，找出三个吞异常 catch，并解释为什么 DEADLINE_EXCEEDED 不应画成 75 次循环。
2. 2～4 分钟：读 MilvusClientV2.getRpcStub、VectorService.insert 和 CollectionService.createCollectionWithSchema。区分 high-level 调用、callable、内部 RPC、共享绝对 deadline；解释 SchemaMismatch 为什么可能重发 insert。
3. 4～6 分钟：读项目 MilvusConfiguration.milvusClient 和 MilvusProperties。解释为什么初始化 Context 用于底层 RPC，而 Future 等待超时不够；跟踪 attach/detach/cancel/shutdown；说明 1ns 如何变成无 deadline。
4. 6～8 分钟：读 MilvusCollectionInitializer.run 与 MilvusGrpcDeadlineTest。追踪 null→不存在、void→假成功两个不同入口，对照修复后明确失败。
5. 8～10 分钟：读 KnowledgeDocumentProcessingService.processDocument/compensateMilvus，找出 milvusWriteMayExist 在 insert **之前**赋值，以及锁行确认 MySQL 提交后为何不补偿。
6. 10～12 分钟：读 KnowledgeAttemptFencingIntegrationTest 的 ambiguousSdkInsert 与 compensationBeforeLateInsert，沿 KnowledgeChunkMapper.xml 的资格 SQL，说明物理孤儿与可检索证据的区别。
7. 12～15 分钟：读 MilvusSdkRetryBehaviorTest、gRPC interruption/late-write 测试并口述证据层级。练习追问：配置一次尝试为什么仍不能承诺 exactly-once？失败后显式恢复和 SDK retry 分别生成什么身份？何种证据才足以声称整体时间上界？

## 10. 收口与下一步边界

B4-D 的应用修复、故障复现、聚焦/真实 MySQL/完整回归、打包和两份台账同步完成，未暂存、提交、推送。下一步仅建议人工审阅本轮 diff；独立维护 CLI、SDK 升级、真实隔离 Milvus 复核须单独确定范围与门禁，不自动执行。不开展 B4-E、真实模拟面试、Docker 部署、CI、MQ、Redis 或无关 MySQL 优化。

临时 MySQL 已在核对本轮容器名、purpose、镜像与 tmpfs 后停止并移除，随机测试凭据已删除，结果记录于 `/private/tmp/ai-b4d-20261113/cleanup.json`；未触碰既有容器或持久卷。
