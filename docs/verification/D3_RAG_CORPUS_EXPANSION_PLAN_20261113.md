# D3｜RAG 语料覆盖与扩容方案

日期：2026-11-13（Asia/Shanghai）。状态：**离线审计/规划完成；实时 MySQL/Milvus 核验 BLOCKED；未编写新增知识正文、未入库、未调用模型**。

## 1. 现有、实时与计划的区分

- 现有仓库：`docs/rag/source-documents` 共 21 篇 Markdown；`full-bank` 中现有 5 份 Markdown 规划/审查记录。当前源文件确实存在，可逐篇阅读；文档中的 `AUTHORED_NOT_INGESTED` 只是历史作者状态标记，不说明当前数据库状态。
- 本次离线执行：直接编译当前 `KnowledgeTextChunker` 等三个生产类到临时目录，对 21 篇文件做 BOM/换行规范化后切片，得到 52 个 Chunk，长度 ≤1200；逐序号长度 52/52 与 Phase 4D3 表匹配。没有把这些 Chunk 持久化。
- 历史报告：[4D4](../rag/PHASE_4D4_FULL_BANK_INGESTION_REPORT.md) 记载 2026-10-06 的 21 documents / 52 chunks / 52 vectors；[4D5](../rag/PHASE_4D5_FULL_BANK_RETRIEVAL_REPORT.md) 记载当时 21 次真实查询 Hit@1=1。它们限定原实例、原题库和模型版本；本次未重新运行检索，当前不宣称这些指标成立。
- 当前实时：3308/19530/9091 均拒绝连接；实际 document/chunk/vector 状态、数量、content_hash、schema 和交叉身份**全部未知**。详情见 [D0](D0_DATA_READINESS_AUDIT_20261113.md#2-实际服务目标与连接证据)。不得把“离线 52”写成“MySQL 当前 52”。
- 计划：保留并按审查修订 21 篇，新增 30 篇主题文档，暂定总量 **51 篇**。范围允许 40～60；按评分机制缺口合并/拆分，不能为了篇数或命中率扩写重复内容。

## 2. 现有 21 篇与 97 个评分点的覆盖矩阵

逻辑 SP ID = 题号 + 原 JSON 中的从 1 开始序号，**不是数据库 point ID**。以下“SP→Chunk”映射来自已存在的 4D3 人工审查表；本轮重新阅读文档正文并用当前切片器核对序号/长度。来源 JSON、完整历史 hash/向量身份文件在当前仓库缺失，因此该表是**可追溯的离线内容映射**，不是新的实时检索、逐摘录独立盲评或跨库完整性验收。

所有 97 个 SP 都有文档/切片映射；其中 H01-SP4 的评分文字缺版本条件，记 **CONDITIONAL**，其余 96 点可用作下一步人工冻结候选。不能把“映射了 97 点”写成“全部 97 点评分规则已无歧义”。

| 题号 / 分类 | 当前源文档 | 当前离线 Chunk 数 | 逐点评分映射 | 边界/复核项 |
|---|---|---:|---|---|
| E01 / JAVA_BASIC | [e01_java21_interface_vs_abstract_class.md](../rag/source-documents/e01_java21_interface_vs_abstract_class.md) | 2 | SP1→E01-1；SP2→E01-1；SP3→E01-1；SP4→E01-1 | 保留正文适用版本、机制和反例条件 |
| E02 / JAVA_COLLECTION | [e02_java21_arraylist_vs_linkedlist.md](../rag/source-documents/e02_java21_arraylist_vs_linkedlist.md) | 2 | SP1→E02-1；SP2→E02-1；SP3→E02-1；SP4→E02-2 | 保留正文适用版本、机制和反例条件 |
| E03 / SPRING | [e03_spring_6_2_ioc_di.md](../rag/source-documents/e03_spring_6_2_ioc_di.md) | 2 | SP1→E03-1；SP2→E03-1；SP3→E03-1,E03-2；SP4→E03-2 | 保留正文适用版本、机制和反例条件 |
| E04 / MYSQL | [e04_mysql_8_0_transaction_acid.md](../rag/source-documents/e04_mysql_8_0_transaction_acid.md) | 2 | SP1→E04-1；SP2→E04-1；SP3→E04-1；SP4→E04-1,E04-2；SP5→E04-1 | 保留正文适用版本、机制和反例条件 |
| E05 / NETWORK | [e05_rfc9293_rfc768_tcp_vs_udp.md](../rag/source-documents/e05_rfc9293_rfc768_tcp_vs_udp.md) | 2 | SP1→E05-1；SP2→E05-1；SP3→E05-1；SP4→E05-1 | 保留正文适用版本、机制和反例条件 |
| M01 / JAVA_BASIC | [m01_java21_equals_hashcode_contract.md](../rag/source-documents/m01_java21_equals_hashcode_contract.md) | 3 | SP1→M01-1；SP2→M01-1；SP3→M01-1；SP4→M01-2 | 引用尾部有低价值切片 |
| M02 / JAVA_COLLECTION | [m02_hashmap_jdk8u_put_resize_treeify.md](../rag/source-documents/m02_hashmap_jdk8u_put_resize_treeify.md) | 3 | SP1→M02-1；SP2→M02-1；SP3→M02-1；SP4→M02-2；SP5→M02-2 | 8u/master 链接需固定 tag/commit |
| M03 / JVM | [m03_jvm21_runtime_data_areas.md](../rag/source-documents/m03_jvm21_runtime_data_areas.md) | 2 | SP1→M03-1；SP2→M03-1；SP3→M03-1,M03-2；SP4→M03-1；SP5→M03-2 | 保留正文适用版本、机制和反例条件 |
| M04 / JAVA_CONCURRENCY | [m04_volatile_jmm_java21.md](../rag/source-documents/m04_volatile_jmm_java21.md) | 2 | SP1→M04-1；SP2→M04-1；SP3→M04-2；SP4→M04-2 | 保留正文适用版本、机制和反例条件 |
| M05 / SPRING | [m05_spring_6_2_aop_proxy_self_invocation.md](../rag/source-documents/m05_spring_6_2_aop_proxy_self_invocation.md) | 3 | SP1→M05-1；SP2→M05-1；SP3→M05-1；SP4→M05-2 | 保留正文适用版本、机制和反例条件 |
| M06 / MYSQL | [m06_mysql_8_0_innodb_index_structure.md](../rag/source-documents/m06_mysql_8_0_innodb_index_structure.md) | 3 | SP1→M06-1；SP2→M06-1；SP3→M06-1；SP4→M06-1；SP5→M06-1,M06-2 | 引用尾部有低价值切片 |
| M07 / REDIS | [m07_redis_classic_data_structures.md](../rag/source-documents/m07_redis_classic_data_structures.md) | 3 | SP1→M07-1；SP2→M07-1；SP3→M07-1；SP4→M07-1；SP5→M07-1 | 经典类型非穷举；引用尾部偏长 |
| H01 / JAVA_BASIC | [h01_java21_generic_type_erasure.md](../rag/source-documents/h01_java21_generic_type_erasure.md) | 3 | SP1→H01-1；SP2→H01-1；SP3→H01-1；SP4→H01-1,H01-2；SP5→H01-2 | SP4 条件歧义；知识正文已限定 Java 21，题库待同步 |
| H02 / JAVA_COLLECTION | [h02_openjdk8u_concurrent_hashmap.md](../rag/source-documents/h02_openjdk8u_concurrent_hashmap.md) | 2 | SP1→H02-1；SP2→H02-1；SP3→H02-1；SP4→H02-2；SP5→H02-2 | 保留正文适用版本、机制和反例条件 |
| H03 / JVM | [h03_hotspot21_g1_gc.md](../rag/source-documents/h03_hotspot21_g1_gc.md) | 2 | SP1→H03-1；SP2→H03-1；SP3→H03-1；SP4→H03-2；SP5→H03-2 | 保留正文适用版本、机制和反例条件 |
| H04 / JAVA_CONCURRENCY | [h04_java21_thread_pool_design.md](../rag/source-documents/h04_java21_thread_pool_design.md) | 2 | SP1→H04-1；SP2→H04-1；SP3→H04-1；SP4→H04-2；SP5→H04-2 | 保留正文适用版本、机制和反例条件 |
| H05 / SPRING | [h05_spring_6_2_transaction_boundary.md](../rag/source-documents/h05_spring_6_2_transaction_boundary.md) | 3 | SP1→H05-1；SP2→H05-1,H05-2；SP3→H05-2；SP4→H05-1,H05-2；SP5→H05-2 | Spring 6.2 默认/覆盖回滚规则须保留 |
| H06 / MYSQL | [h06_mysql_8_0_innodb_mvcc_consistent_read.md](../rag/source-documents/h06_mysql_8_0_innodb_mvcc_consistent_read.md) | 3 | SP1→H06-1；SP2→H06-1；SP3→H06-1；SP4→H06-2；SP5→H06-2 | 保留正文适用版本、机制和反例条件 |
| H07 / REDIS | [h07_redis_distributed_lock_baseline.md](../rag/source-documents/h07_redis_distributed_lock_baseline.md) | 3 | SP1→H07-1；SP2→H07-1；SP3→H07-1,H07-2；SP4→H07-2；SP5→H07-2 | Redis 最低命令版本不代表当前部署版本 |
| H08 / NETWORK | [h08_rfc9293_tcp_connection_lifecycle.md](../rag/source-documents/h08_rfc9293_tcp_connection_lifecycle.md) | 2 | SP1→H08-1；SP2→H08-1；SP3→H08-1；SP4→H08-2；SP5→H08-2 | 保留正文适用版本、机制和反例条件 |
| H09 / PROJECT_SCENARIO | [h09_mysql_8_0_inventory_idempotency_scenario.md](../rag/source-documents/h09_mysql_8_0_inventory_idempotency_scenario.md) | 3 | SP1→H09-1；SP2→H09-1；SP3→H09-1,H09-2；SP4→H09-2 | 假设库存；项目类比不能冒充实现 |

### 2.1 对当前语料质量的判断

- 正文是围绕机制、边界和误解的知识解释；规范化比较未发现任一完整 referenceAnswer 被整段复制为知识正文。≥250 字规范化长段落的跨文档完全重复组为 0；不等于已排除语义重叠或题库泄漏。
- 多数文档含目标题原文/Bank ID，21 题与 21 文档一一对齐；对这 21 条固定检索 Query 的高命中可能受题目定向元数据影响。新语料应按主题编写，题目-SP-文档映射留在独立审查清单，不把题干/评分点文本重复塞进正文。
- 历史 4D3 标注 8 LOW、6 REFERENCES_ONLY、26 self-contained WARN。当前同序号长度仍匹配，但这些人工等级不作为本轮新测试总数。引用尾部、重叠从半句开始、标题与代码边界仍需在后续内容修订后逐块检查。
- 不能以“来自官方”推断实现适用所有版本。M02 的移动 8u/master 来源、Redis latest 页面及 6.2 在线页要保存准确访问日期和版本边界；H02 固定 8u402-b06，H01 明示 JLS/JVMS 21，不能无标记混用。
- H01 题库与知识正文对 `instanceof` 的条件不一致，优先修正评分口径；不为让模型命中错误评分点而改坏知识文档。

## 3. 生产管道的当前约束

读取入口包括 `KnowledgeTextChunker.split`、`KnowledgeDocumentProcessingService.processDocument/occupyProcessing`、`KnowledgeDocumentProcessingTransactionService.persistChunksAndMarkReady`、`KnowledgeRetrievalService.retrieve` 和 `KnowledgeChunkMapper.selectRetrievableByVectorIds`。

### 3.1 上传与切片

上传只接受严格 UTF-8 的 `.md/.markdown/.txt`，≤5 MiB；去 BOM、CRLF/CR→LF、拒绝空白，内容哈希基于规范化 UTF-8。上传只创建 UPLOADED；处理需要管理员显式 POST 和 Milvus 基础设施。

切片器最大 1200、重叠目标 150、从 800 开始寻找自然边界；优先段落→换行→句末→硬切；`String.length()` 是 UTF-16 code units，不是 token。它不理解 Markdown 标题、表格、代码块语义，英文词边界调整也不保证中文句义完整。新文档应使一个机制段落及其关键前提尽量能独立理解，不能仅靠放大 overlap 掩盖碎片化。

### 3.2 处理与恢复

当前实现已支持 UPLOADED、FAILED、stale PROCESSING 的 attempt 领取，READY 拒绝重复处理；按 attempt fencing 收口。切片→Embedding→Milvus 写入→短事务替换当前版本 Chunk 并标 READY；MySQL 提交确认和精确本批 vector ID 补偿保留。成功提交后才清理退休向量，清理失败不能删除刚生效的批次。早期 AGENTS 中“先 deleteByDocumentId 再处理”等历史叙述不能替代当前源码。

本轮不调用 process，也不通过启动应用顺便运行 CollectionInitializer。已有 READY 文档没有通用的重新处理入口；内容修订的更新/迁移、旧文档禁用及数据关系需要后续明确设计和授权，不能悄悄覆盖 READY 内容并假定向量会自动同步。

### 3.3 检索和评价证据

生产检索 Query 来自主问题/评分点上下文，不加入用户答案。`EmbeddingClient` 获得 query vector 后，Milvus 以 embedding profile 过滤并返回 TopK；MySQL 一次 IN hydration 只接受 ACTIVE Chunk、READY 文档、当前 documentVersion、相同 model/profile，并校验 documentId/chunkIndex。原始排名保留，过滤后不补齐、不重排；RAG 内容作为待分析数据，不能获得控制工作流的权限。

当前 `RetrievalProperties` 有可选 COSINE 阈值，默认关闭，minCosineScore 默认 0.70；旧报告所称“无阈值”只适用于当时有效配置。D3 不启用阈值/类别过滤/重排，不把旧 0.49 或新默认值当已经验证的质量策略。真实后续试验必须记录实际绑定值。

评价追踪持久化的是最终入 Prompt 的合格证据，不是全部 Milvus 原始命中；原始命中、合格候选、Prompt 选中证据分开计数。单独的检索工具不得为了“覆盖测试”写业务 trace 或推进 Session。

## 4. 新增 30 篇主题文档规划

每篇为**原创解释 + 官方事实引用**，尚无正文/数据库 ID/向量。下表给主来源；编写时还需固定小节、补充来源和逐点证据。访问日期为 2026-11-13，已通过浏览获取下表主来源页面；这是来源定位，不表示许可已逐篇获确认或整页事实都已验证。Redis 动态页仅取 7.2 基线机制，版本变化字段需再查 tag/命令 history。Spring 范围为本项目 6.2.19，不跟随页面建议升级到 7.x。

| 文档 ID | 拟主题 | 分类 | 版本范围 | 对应新增题 | 官方主来源 |
|---|---|---|---|---|---|
| N01 | 异常传播与可关闭资源 | JAVA_BASIC | Java SE 21；JLS 11/14.20.3 | E06, M08 | [来源](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3) |
| N02 | 字符串不可变、相等与构建 | JAVA_BASIC | Java SE 21 API | E07 | [来源](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html) |
| N03 | 集合契约、栈与队列选型 | JAVA_COLLECTION | Java SE 21 Collection/Deque API | E08, E09 | [来源](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collection.html) |
| N04 | Bean 作用域与生命周期 | SPRING | Spring Framework 6.2；核验页 6.2.19 | E10, E11 | [来源](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html) |
| N05 | MySQL 约束与 NULL 语义 | MYSQL | MySQL 8.0；不外推 8.4 | E12 | [来源](https://dev.mysql.com/doc/refman/8.0/en/create-table.html) |
| N06 | JOIN 条件与结果集 | MYSQL | MySQL 8.0 | E13 | [来源](https://dev.mysql.com/doc/refman/8.0/en/join.html) |
| N07 | HTTP 方法、状态与缓存语义 | NETWORK | RFC 9110/9111；按节记录 | E14 | [来源](https://www.rfc-editor.org/rfc/rfc9110.html) |
| N08 | DNS 名称解析与缓存边界 | NETWORK | RFC 1034 基础；后续更新需单独标注 | E15 | [来源](https://www.rfc-editor.org/rfc/rfc1034.html) |
| N09 | 泛型不变性、通配符和类型边界 | JAVA_BASIC | JLS 21 §4；补 §5 转换规则 | M09 | [来源](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html) |
| N10 | 迭代器与结构修改 | JAVA_COLLECTION | Java SE 21 Iterator/ArrayList API | M10 | [来源](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collection.html) |
| N11 | 集合视图与防御性复制 | JAVA_COLLECTION | Java SE 21 Collection/List/Collections API | M11 | [来源](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collection.html) |
| N12 | 类加载、初始化与类型身份 | JVM | JVMS SE 21 Chapter 5 | M12, H14 | [来源](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html) |
| N13 | 可达性、泄漏与 JVM 诊断 | JVM | JDK 21 Troubleshooting；HotSpot 专属项明示 | M13, H15 | [来源](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshooting-memory-leaks.html) |
| N14 | Monitor 与显式锁 | JAVA_CONCURRENCY | Java SE 21；ReentrantLock API/JLS 17 | M14 | [来源](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html) |
| N15 | 协作取消与条件等待 | JAVA_CONCURRENCY | Java SE 21 Object/Thread API | M15 | [来源](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html) |
| N16 | Bean 候选消歧与依赖组织 | SPRING | Spring Framework 6.2.19 范围 | M16 | [来源](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html) |
| N17 | MVC 校验与异常契约 | SPRING | Spring Framework 6.2.19；Jakarta Validation 版本待依赖复核 | M17 | [来源](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html) |
| N18 | 索引访问路径与执行计划 | MYSQL | MySQL 8.0 EXPLAIN；ANALYZE 执行查询须标注 | M18, M19 | [来源](https://dev.mysql.com/doc/refman/8.0/en/explain.html) |
| N19 | 缓存生命周期与一致性窗口 | REDIS | Redis 7.2 命令基线；latest 页须筛除后续特性 | M20, H22 | [来源](https://redis.io/docs/latest/develop/reference/eviction/) |
| N20 | Redis 持久化与批量执行边界 | REDIS | Redis 7.2 基线；AOF/MULTI/Lua 分节 | M21, H23 | [来源](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/) |
| N21 | 反射访问与模块封装 | JAVA_BASIC | Java SE 21 AccessibleObject API | H10 | [来源](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AccessibleObject.html) |
| N22 | Stream 执行模型和共享状态 | JAVA_BASIC | Java SE 21 Stream API | H11 | [来源](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html) |
| N23 | ConcurrentHashMap 原子 API | JAVA_COLLECTION | Java SE 21 API；不混用 8u 实现细节 | H12 | [来源](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html) |
| N24 | 并发集合的迭代一致性 | JAVA_COLLECTION | Java SE 21 COW/CHM API | H13 | [来源](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html) |
| N25 | ThreadLocal 生命周期与线程复用 | JAVA_CONCURRENCY | Java SE 21 API；内部弱引用需固定源码另证 | H16 | [来源](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html) |
| N26 | 异步结果组合、异常与超时 | JAVA_CONCURRENCY | Java SE 21 CompletableFuture API | H17 | [来源](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html) |
| N27 | Spring 事务传播与资源占用 | SPRING | Spring Framework 6.2.19 | H18 | [来源](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html) |
| N28 | InnoDB 隔离、锁和失败恢复 | MYSQL | MySQL 8.0；RR/RC 配置必须明示 | H20, H21 | [来源](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html) |
| N29 | TLS 身份验证与 HTTP 超时语义 | NETWORK | RFC 8446 TLS 1.3 + RFC 9110 | H24, H25 | [来源](https://www.rfc-editor.org/rfc/rfc8446.html) |
| N30 | 本地事务、幂等与外部副作用恢复 | PROJECT_SCENARIO | 本仓库 HEAD 0c6f92a + MySQL 8.0；原创工程推导 | H19, H26, H27 | [来源](https://dev.mysql.com/doc/refman/8.0/en/innodb-consistent-read.html) |

这些主题覆盖全部 42 个新增题的拟考察机制；新增题尚无正式 SP，因此不伪造“新增评分点全覆盖率”。下一阶段先冻结新 SP，再将每点映射到具体文档版本/节/完整证据段/切片 ID，缺口标 SOURCE_GAP，不靠模型自行补事实。

补充来源与范围：N04 生命周期使用 [Spring Bean lifecycle](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html)；N07 缓存用 [RFC 9111](https://www.rfc-editor.org/rfc/rfc9111.html)；N19 TTL 用 [EXPIRE](https://redis.io/docs/latest/commands/expire/)；N20 批处理用 [Pipelining](https://redis.io/docs/latest/develop/using-commands/pipelining/) 和 [Transactions](https://redis.io/docs/latest/develop/using-commands/transactions/)；N28 快照用 [MySQL consistent reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-consistent-read.html)；N29 超时/方法语义用 [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html)。这些补充页本轮取得正文。

N03/N10/N11 的主来源为集合总契约，后续必须补齐具体 Deque/Iterator/List/Collections 方法页；N15 需补 Thread 中断契约；N17 需核实 Jakarta Validation 的实际依赖版本；N18 需补联合索引节；N28 需补死锁及锁等待回滚节。**主来源可访问不等于所有子主题证据闭合**。N19 cache-aside/击穿策略与 N30 恢复架构属于工程推导，应明确假设、反例和项目代码证据，不冒充官方保证。

N30 的项目证据固定到 D0 HEAD 下的 InterviewService、InterviewWorkflowTransactionService、InterviewReportService/TransactionService 和各 Mapper；描述当前行为和局限，库存示例另标假设。文档分类只能用现有单值枚举，N30 取 PROJECT_SCENARIO；跨主题标签放离线 manifest，无需多标签 Schema。

### 4.1 分类数量和缺口优先级

| 分类 | 已有文档 | 拟新增文档 | 规划总计 | 主要补足 |
|---|---:|---:|---:|---|
| JAVA_BASIC | 3 | 5 | 8 | 异常/类型/API 使用边界 |
| JAVA_COLLECTION | 3 | 5 | 8 | 容器契约/复制/原子/迭代 |
| JVM | 2 | 2 | 4 | 加载身份/泄漏诊断 |
| JAVA_CONCURRENCY | 2 | 4 | 6 | 锁/取消/上下文/异步 |
| SPRING | 3 | 4 | 7 | 生命周期/注入/校验/传播 |
| MYSQL | 3 | 4 | 7 | 约束/JOIN/计划/锁失败 |
| REDIS | 2 | 2 | 4 | TTL/淘汰/持久/批处理 |
| NETWORK | 2 | 3 | 5 | HTTP/DNS/TLS/重试 |
| PROJECT_SCENARIO | 1 | 1 | 2 | 本项目幂等/恢复/外部副作用 |

优先 P0：修正 H01 评分边界、补齐证据版本和现有运行数据；P1：N01～N20 覆盖 EASY/MEDIUM 新主题；P2：N21～N30 深入边界及场景。N19/N20/N30 较宽，若切片后机制互相割裂，应拆为更聚焦的文档；总量仍以 40～60 为参考，不强制保留 51。新 Chunk 数和 Embedding token 数当前 UNKNOWN，不按字数直接当 token。

## 5. 来源、许可、版本与正文规范

1. 每篇记录 docKey、title/category、作者、原创/改编标识、source URL/具体章节、产品/规范版本、访问日期、来源 tag/commit、使用方式及许可核查结果、内容版本、规范化 SHA-256、审查人和适用边界。数据库 `source` 仅 varchar(255)，可保存主来源或简短 manifest 定位；完整来源和许可清单存 Git 管理的旁路 manifest，不改表。
2. 优先官方规范/API 和有明确许可的资料，使用自己的语言重述已核实事实；引用只取必要短句并标注出处。网页可访问不等于整篇复制获许可；Oracle/MySQL/Redis 网站内容、开源源码和文档的许可不能互相替代。未确认的复制/改编许可填 LICENSE_REVIEW_PENDING，禁止整页搬入；可改用经核实的原创解释加链接。
3. 开源代码只保留解释机制所需的最小例子，核实具体版本 LICENSE/NOTICE；项目源码引用先检查仓库授权范围。公开标准遵循其自身版权通知。此处是内容准入流程，不是已授予版权许可的结论。
4. 原创段落区分“规范事实”“固定实现细节”“工程推导”“项目当前实现”“待验证建议”。不伪造作者、论文、运行结果或版本；不放用户真实答案/简历/密钥。
5. 正文建议 1200～3000 UTF-16 字符只是写作起点；机制前提/反例比长度重要。使用短标题、完整句、紧凑来源，避免巨大引用尾巴和题干关键词堆积。不能把每道参考答案原样复制，也不能将评分点本身当独立外部证据。
6. 若涉及版本更换，新增不可变内容版本和审查记录，再设计数据更新；旧 Session 的题目和已落库证据不随之重写。需要更换 embedding model/dimension/profile 时另行提出兼容与迁移方案，本轮固定 qwen3.7-text-embedding/1024/profile-v1。

## 6. PDF 与现有 Markdown/TXT 管道的差异

本轮没有必须使用 PDF 的缺口，先选官方 HTML/API 与原创 Markdown。现有上传管道会拒绝 `.pdf`，把 PDF 改后缀为 `.txt` 也不能得到正确 UTF-8 正文。

后续如确需 PDF：先核查来源/版本/许可，再在**入库前离线**提取文本；数字 PDF 需处理栏序、页眉页脚、连字符、代码/表格，扫描 PDF 还需 OCR 和人工核对数字、符号、否定词。逐页建立“原 PDF 页码/节 → Markdown 段落”追溯，保存原文件 hash、提取工具版本、人工修订和转换后 hash；不将乱码/错表格直接向量化。

转换后输出审核过的 UTF-8 Markdown/TXT，再走现有大小/规范化/切片/Embedding/处理流程。PDF 的页数、字节数与 Chunk/token 数没有固定对应。此方案不要求当前控制器支持 PDF，也不引入 PDF 运行时依赖；原 PDF 与许可记录是否能分发应单独确认。

## 7. 后续分层验收设计

### 7.1 离线内容与切片门禁

- 冻结题库、来源 manifest、语料正文 hash；独立审阅所有新 SP 的事实正确性及版本。每个 CORE 和其他评分机制均应有具体证据，无法支持标缺口。
- 直接运行现有 chunker dry-run；检查 UTF-8/字节上限、连续序号、覆盖、完整前提、跨代码/表格边界、重复比例、引用-only 和 context-WARN。关键机制至少有一块独立可理解正文，不用总 Chunk 数冒充质量。
- 新增/修改后的每个完整证据摘录必须在真实 Chunk 中逐字存在；分开记录“文档覆盖”“切片后可用”“实际检索召回”“最终 Prompt 入选”，不能连跳四级。

### 7.2 授权后的隔离入库与一致性

AI 质量基线仅用 evaluation profile、`ai_interview_evaluation`、`knowledge_chunk_vectors_evaluation`；普通测试用 test，local 不作为压测目标。任何导入必须在后续任务中授权，并核实 EnvironmentIsolationInitializer；调用 process 前记录预算、runId 和精确目标。验证 READY、Chunk 状态/version/model/profile、唯一 vector_id、实际 Collection schema，做双向身份及内容哈希核对。

入库模型请求量按 `Σ ceil(每篇实际 Chunk 数 / batchSize)` 预算，当前 batchSize 上限 20；这不是 token/金额。数据库和 Milvus 不提供天然跨库同一快照，核验时冻结写入或记录水位，识别在途变化。清理仅能按本 run 新增 ID 及依赖关系，不 drop 共享库/集合。

### 7.3 检索评估，独立于判分质量

后续先冻结 63 个主问题的生产 Query；另设 21 个不在正文/题干元数据中出现的改写或边界留出问题、9 个跨主题/无直接证据问题，共 **93 个计划查询**。改写由未看检索结果的审题人确定；Query 不能拼入考生答案。训练式调文/参数选择与留出集隔离，不能看失败结果后反复改同一留出集再报告首次命中率。

人工先标每个 query×候选证据的 DIRECT/RELATED_ONLY/IRRELEVANT、支持的 SP/前提和版本；“同题同文档”本身不算 DIRECT。先小批构建全量或经说明的候选池金标准，防止把检索系统自己返回的内容当全集真值。

报告：Hit@1/3/5、MRR@5、Precision@5、按 SP 权重的证据覆盖（只是覆盖度，不是评分总分）、全 CORE 证据率、无证据问题误报率、各分类/难度、去重文档数量、错误来源；同时记录 raw/hydrated/Prompt-selected 数量和原因。候选准入目标可设主问题 Hit@5≥90%、CORE 机制有可用正文 100%、严重版本错误 0；须在实际运行前审核冻结，当前未达标也未失败。

首轮不启用重排、阈值扫描、补召回或类别硬过滤。若失败，先定位来源缺口/切片损失/Query/过滤/排名，再提出最小变更；不能直接从 63 题覆盖推断需要新技术。真实 Embedding+Milvus 的检索通过仍不能证明 DeepSeek 判分正确；后者由 [D4-A](D4_MULTI_AGENT_TEST_PLAN_20261113.md#2-a多-ai-模拟面试质量测试) 独立人工对照。

## 8. 本轮状态与后续门禁

本次只有离线源码/语料分析、当前 chunker 21 文档 dry-run、引用定位及规划；模型/Embedding 调用 0、SQL 查询 0、Milvus 数据查询与写入 0。没有生成 30 篇新正文，也没有把 51 篇规划描述为已入库。实时数据/许可逐篇核查/新 SP 冻结/留出金标准/费用均待后续准备。学习状态保持待学习（未确认）。

浏览中旧 Redis 路径返回空正文，已改用官方 using-commands 路径；一个旧 Java 21 GC 路径重定向到 26，未作为版本依据，替换为 Java 21 诊断/GC 指南。来源核验保留这些限制，不把重定向页面当所需版本。现有历史报告和源文件本轮均保留原样。
