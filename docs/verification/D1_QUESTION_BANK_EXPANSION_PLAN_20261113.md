# D1｜63 道题库扩容与随机组卷方案

日期：2026-11-13（Asia/Shanghai）。状态：**仅设计，未实现、未编写完整新增题目、未入库**。

依赖 [D0 审计](D0_DATA_READINESS_AUDIT_20261113.md)：仓库有 21 个题目定义，实际 MySQL 数据 BLOCKED；H01 版本措辞需后续修订。以下按保留 21 个主题位置规划新增 42 题，不声称数据库现在有 21 道有效启用题。

## 1. 目标与容量

每个“难度 × 必选分类”扩展为 3 个候选，仍每场每类取 1 道。EASY 由 5→15（新增 10）；MEDIUM 由 7→21（新增 14）；HARD 由 9→27（新增 18）；总数 21+42=63。若补审发现现有题缺失、禁用或废弃，应先分别列恢复/替换量，不能直接按数据库当前总数再加 42。

| 分类 | EASY 现有→目标 | MEDIUM 现有→目标 | HARD 现有→目标 | 新增合计 |
|---|---|---|---|---:|
| JAVA_BASIC | 1→3 | 1→3 | 1→3 | 6 |
| JAVA_COLLECTION | 1→3 | 1→3 | 1→3 | 6 |
| JVM | 0→0 | 1→3 | 1→3 | 4 |
| JAVA_CONCURRENCY | 0→0 | 1→3 | 1→3 | 4 |
| SPRING | 1→3 | 1→3 | 1→3 | 6 |
| MYSQL | 1→3 | 1→3 | 1→3 | 6 |
| REDIS | 0→0 | 1→3 | 1→3 | 4 |
| NETWORK | 1→3 | 0→0 | 1→3 | 4 |
| PROJECT_SCENARIO | 0→0 | 0→0 | 1→3 | 2 |

0→0 表示该难度不设此必选分类，并非遗漏。规划不改变 `InterviewPlanConfig`：EASY 5 题、MEDIUM 7 题、HARD 9 题；分类顺序保持，先随机题目身份，不额外随机出题顺序。

## 2. 新增题目主题清单（42 条）

E06～E15、M08～M21、H10～H27 是**规划逻辑编号**，不是数据库主键。每行仅给标题/分类/难度/知识点及拟考察范围，不包含可导入的 questionContent/referenceAnswer/scoringPoints 完整对象。N 编号对应 [D3 的拟新增文档](D3_RAG_CORPUS_EXPANSION_PLAN_20261113.md#4-新增-30-篇主题文档规划)。同一主题文档可服务多题，不把参考答案一题一份复制为知识库。

| 编号 | 标题 | 分类 | 难度 | knowledgePoint | 拟考察知识与边界 | 规划文档 |
|---|---|---|---|---|---|---|
| E06 | 异常分类与资源关闭责任 | JAVA_BASIC | EASY | `exceptions_resource_basics` | checked/unchecked；异常传播；try-with-resources | N01 |
| E07 | String 不可变与字符串比较 | JAVA_BASIC | EASY | `strings_immutability` | 不可变对象；==/equals；StringBuilder 使用边界 | N02 |
| E08 | List、Set、Map 的语义与选择 | JAVA_COLLECTION | EASY | `collection_semantics` | 有序与重复；键值映射；按业务需求选容器 | N03 |
| E09 | 用 Deque 表达栈和队列 | JAVA_COLLECTION | EASY | `deque_stack_queue` | 先进后出/先进先出；两端 API；容量与线程安全边界 | N03 |
| E10 | Bean 常用作用域与单例状态 | SPRING | EASY | `bean_scope_basics` | singleton/prototype/request；实例复用；共享可变状态 | N04 |
| E11 | Bean 初始化与资源释放 | SPRING | EASY | `bean_lifecycle_basics` | 创建/注入/初始化；销毁回调；容器管理范围 | N04 |
| E12 | 主键、唯一约束与 NULL | MYSQL | EASY | `mysql_constraints_null` | PRIMARY KEY/UNIQUE/NOT NULL；NULL 比较；约束用途 | N05 |
| E13 | INNER JOIN 与 LEFT JOIN 的结果差异 | MYSQL | EASY | `mysql_join_basics` | 匹配行；未匹配补 NULL；WHERE/ON 条件位置 | N06 |
| E14 | HTTP 方法和常见状态码 | NETWORK | EASY | `http_methods_status` | GET/POST；2xx/4xx/5xx；安全与幂等概念 | N07 |
| E15 | 从域名到服务地址的 DNS 查询 | NETWORK | EASY | `dns_resolution_basics` | 递归/迭代职责；缓存与 TTL；DNS 和 TCP 分层 | N08 |
| M08 | try-with-resources 与 suppressed 异常 | JAVA_BASIC | MEDIUM | `resource_exception_suppression` | 逆序关闭；主异常与关闭异常；finally 覆盖风险 | N01 |
| M09 | 泛型通配符与 PECS | JAVA_BASIC | MEDIUM | `generic_variance_pecs` | 不变性；extends/super；读写限制与 API 设计 | N09 |
| M10 | 迭代中修改集合与 fail-fast | JAVA_COLLECTION | MEDIUM | `iterator_modification` | Iterator.remove；结构修改；best-effort 与线程安全区别 | N10 |
| M11 | 集合视图、不可修改与防御性复制 | JAVA_COLLECTION | MEDIUM | `collection_copy_views` | unmodifiable 视图；copyOf；浅复制与元素可变性 | N11 |
| M12 | 类加载、链接和初始化的区别 | JVM | MEDIUM | `class_loading_initialization` | 加载/验证/准备/解析/初始化；触发条件；静态初始化 | N12 |
| M13 | 对象可达性、GC Roots 与内存泄漏 | JVM | MEDIUM | `gc_reachability_leaks` | 可达性；长期引用；泄漏与 OOM 区分；证据排查 | N13 |
| M14 | synchronized 与 ReentrantLock 的选择 | JAVA_CONCURRENCY | MEDIUM | `monitor_reentrant_lock` | 互斥和可见性；try/finally 解锁；中断/超时获取 | N14 |
| M15 | 线程中断与 wait/sleep 的边界 | JAVA_CONCURRENCY | MEDIUM | `thread_interruption_wait` | 协作取消；中断标志；锁释放；条件循环与虚假唤醒 | N15 |
| M16 | 多个实现如何进行依赖注入 | SPRING | MEDIUM | `dependency_injection_resolution` | 按类型；@Primary/@Qualifier；构造器依赖与歧义 | N16 |
| M17 | 接口参数校验与统一异常响应 | SPRING | MEDIUM | `mvc_validation_errors` | @Valid；绑定/校验异常；错误语义；避免内部信息泄露 | N17 |
| M18 | 联合索引与查询条件的匹配 | MYSQL | MEDIUM | `composite_index_access` | 最左前缀；范围与排序；覆盖；按计划核对而非口诀 | N18 |
| M19 | 如何用 EXPLAIN 分析慢查询 | MYSQL | MEDIUM | `explain_query_plan` | key/type/rows/Extra；估计与实测区别；选择性与验证 | N18 |
| M20 | 过期和淘汰有什么区别 | REDIS | MEDIUM | `redis_expiry_eviction` | TTL；过期处理；maxmemory 策略；缓存未命中责任 | N19 |
| M21 | RDB 与 AOF 的恢复取舍 | REDIS | MEDIUM | `redis_persistence_tradeoffs` | 快照/日志；刷盘与数据损失窗口；恢复成本；备份区别 | N20 |
| H10 | 反射、模块封装与访问边界 | JAVA_BASIC | HARD | `reflection_module_access` | getDeclared；访问控制；trySetAccessible；异常与信任边界 | N21 |
| H11 | Stream 惰性执行与并行副作用 | JAVA_BASIC | HARD | `stream_laziness_parallel` | 中间/终止操作；non-interference；共享状态；并行适用条件 | N22 |
| H12 | 并发 Map 中复合操作如何保持原子性 | JAVA_COLLECTION | HARD | `concurrent_map_atomicity` | get-then-put 竞态；putIfAbsent/compute；回调阻塞与副作用 | N23 |
| H13 | 快照迭代与弱一致迭代的取舍 | JAVA_COLLECTION | HARD | `concurrent_iteration_tradeoffs` | CopyOnWriteArrayList；ConcurrentHashMap；读写比例；内存成本 | N24 |
| H14 | 类加载器隔离为什么会产生类型不兼容 | JVM | HARD | `classloader_type_identity` | 类名+定义加载器；委派；上下文加载器；ClassCastException 排查 | N12 |
| H15 | 高 CPU、频繁 GC 与 OOM 如何定位 | JVM | HARD | `jvm_diagnostics_workflow` | 线程/堆/GC 证据；区分原因；最小复现；避免盲调堆 | N13 |
| H16 | 线程池中 ThreadLocal 的残留与串数据 | JAVA_CONCURRENCY | HARD | `threadlocal_pool_cleanup` | 线程复用；remove/finally；生命周期；上下文传播边界 | N25 |
| H17 | CompletableFuture 的异常、超时和线程池 | JAVA_CONCURRENCY | HARD | `future_timeout_composition` | thenCompose/allOf；异常传播；默认池；超时不等于任务终止 | N26 |
| H18 | REQUIRED、REQUIRES_NEW 和 NESTED 的事务取舍 | SPRING | HARD | `spring_transaction_propagation` | 逻辑/物理事务；rollback-only；savepoint；连接池代价 | N27 |
| H19 | 事务提交后执行外部动作如何处理失败 | SPRING | HARD | `transaction_external_side_effect` | 本地事务边界；提交后窗口；显式状态/补偿；不承诺原子远程调用 | N30 |
| H20 | RR 与 RC 下快照读和锁定读如何交互 | MYSQL | HARD | `isolation_snapshot_locking` | 快照建立；自身写入；锁定读；并发异常的条件 | N28 |
| H21 | 死锁与锁等待超时为什么恢复方式不同 | MYSQL | HARD | `deadlock_timeout_recovery` | 锁顺序；回滚范围；有限重试；相同业务键；诊断证据 | N28 |
| H22 | 缓存更新、热点失效和击穿如何取舍 | REDIS | HARD | `cache_aside_hotkey` | cache-aside 竞争窗口；TTL；单飞/反压；权威库一致性边界 | N19 |
| H23 | Pipeline、事务与 Lua 的原子性边界 | REDIS | HARD | `redis_batch_atomicity` | RTT；MULTI/EXEC/WATCH；脚本原子；错误不等于数据库式回滚 | N20 |
| H24 | HTTPS 如何验证服务器并建立加密通信 | NETWORK | HARD | `tls_authentication_basics` | 证书链/主机名；TLS 1.3；密钥协商；HTTPS 不等于业务鉴权 | N29 |
| H25 | HTTP 请求超时后是否可以自动重试 | NETWORK | HARD | `http_timeout_idempotency` | 连接/读取超时；提交不确定；方法语义；业务幂等与预算 | N29 |
| H26 | 多用户提交面试答案时的幂等与隔离 | PROJECT_SCENARIO | HARD | `interview_idempotency_ownership` | JWT 当前用户；归属；稳定 requestId+精确载荷；单胜者提交 | N30 |
| H27 | 报告生成崩溃后的恢复和一致性 | PROJECT_SCENARIO | HARD | `report_recovery_final_consistency` | FINAL 汇总；GENERATING/stale；旧任务 fencing；报告/弱项原子提交 | N30 |

## 3. 知识覆盖矩阵

下表为主题规划覆盖，不是评分点已编写/模型已判准。每行最终三个候选来自同一分类、同一难度；括号中旧题机制与新增机制可用于防止换标题复述。

| 难度 | 分类 | 保留题 | 新增题 | 覆盖增量 |
|---|---|---|---|---|
| EASY | JAVA_BASIC | E01 `interface_vs_abstract_class` | E06, E07 | 异常分类与资源关闭责任；String 不可变与字符串比较 |
| EASY | JAVA_COLLECTION | E02 `arraylist_vs_linkedlist` | E08, E09 | List、Set、Map 的语义与选择；用 Deque 表达栈和队列 |
| EASY | SPRING | E03 `ioc_di` | E10, E11 | Bean 常用作用域与单例状态；Bean 初始化与资源释放 |
| EASY | MYSQL | E04 `transaction_acid` | E12, E13 | 主键、唯一约束与 NULL；INNER JOIN 与 LEFT JOIN 的结果差异 |
| EASY | NETWORK | E05 `tcp_vs_udp` | E14, E15 | HTTP 方法和常见状态码；从域名到服务地址的 DNS 查询 |
| MEDIUM | JAVA_BASIC | M01 `equals_hashcode_contract` | M08, M09 | try-with-resources 与 suppressed 异常；泛型通配符与 PECS |
| MEDIUM | JAVA_COLLECTION | M02 `hashmap_put_resize` | M10, M11 | 迭代中修改集合与 fail-fast；集合视图、不可修改与防御性复制 |
| MEDIUM | JVM | M03 `runtime_data_areas` | M12, M13 | 类加载、链接和初始化的区别；对象可达性、GC Roots 与内存泄漏 |
| MEDIUM | JAVA_CONCURRENCY | M04 `volatile_jmm` | M14, M15 | synchronized 与 ReentrantLock 的选择；线程中断与 wait/sleep 的边界 |
| MEDIUM | SPRING | M05 `spring_aop_proxy` | M16, M17 | 多个实现如何进行依赖注入；接口参数校验与统一异常响应 |
| MEDIUM | MYSQL | M06 `innodb_index_structure` | M18, M19 | 联合索引与查询条件的匹配；如何用 EXPLAIN 分析慢查询 |
| MEDIUM | REDIS | M07 `redis_data_structures` | M20, M21 | 过期和淘汰有什么区别；RDB 与 AOF 的恢复取舍 |
| HARD | JAVA_BASIC | H01 `generic_type_erasure` | H10, H11 | 反射、模块封装与访问边界；Stream 惰性执行与并行副作用 |
| HARD | JAVA_COLLECTION | H02 `concurrent_hashmap` | H12, H13 | 并发 Map 中复合操作如何保持原子性；快照迭代与弱一致迭代的取舍 |
| HARD | JVM | H03 `g1_gc` | H14, H15 | 类加载器隔离为什么会产生类型不兼容；高 CPU、频繁 GC 与 OOM 如何定位 |
| HARD | JAVA_CONCURRENCY | H04 `thread_pool_design` | H16, H17 | 线程池中 ThreadLocal 的残留与串数据；CompletableFuture 的异常、超时和线程池 |
| HARD | SPRING | H05 `transaction_boundary` | H18, H19 | REQUIRED、REQUIRES_NEW 和 NESTED 的事务取舍；事务提交后执行外部动作如何处理失败 |
| HARD | MYSQL | H06 `mvcc_read_view` | H20, H21 | RR 与 RC 下快照读和锁定读如何交互；死锁与锁等待超时为什么恢复方式不同 |
| HARD | REDIS | H07 `distributed_lock` | H22, H23 | 缓存更新、热点失效和击穿如何取舍；Pipeline、事务与 Lua 的原子性边界 |
| HARD | NETWORK | H08 `tcp_connection_lifecycle` | H24, H25 | HTTPS 如何验证服务器并建立加密通信；HTTP 请求超时后是否可以自动重试 |
| HARD | PROJECT_SCENARIO | H09 `concurrent_inventory_idempotency` | H26, H27 | 多用户提交面试答案时的幂等与隔离；报告生成崩溃后的恢复和一致性 |

**邻接主题的控制。** E06 只要求异常和资源责任，M08 深入 suppressed；E10/E11 分别考作用域和生命周期；M12 考加载阶段，H14 考加载器身份冲突；M13 考可达性，H15 考证据驱动诊断；H02 是 8u 内部协调，H12/H13 是 Java 21 API 原子/迭代契约；M20 是生命周期，H22 是缓存并发窗口；H19 是 Spring 事务外副作用，H26/H27 是本项目提交权与恢复。后续人工审题若发现两个题核心评分点实质相同，应合并或换题，不为凑 63 重复出题。

H26/H27 的项目事实只引用当前仓库已实现调用链；H09 继续是假设库存题。Redis 相关题是领域题，不表示项目将引入 Redis。HARD 保持初级后端可讨论的机制/边界，不要求大型分布式系统设计或背诵内部字段。

### 3.1 内容编写门禁（下一阶段）

1. 先修订 H01 的 Java 21 条件并同步 JSON/Markdown；保持逻辑主题身份，保留旧版本来源，不在 D0 直接改。
2. 每题先明确口头回答的 1～3 分钟范围，再写参考答案和独立评分点。每题建议 4～6 点、至少 1 CORE、各点 1～100、合计 100；这是内容质量合同，不能声称现有管理员服务已经检查了 CORE 个数。
3. 评分点写机制和可接受替代方案，不要求关键词逐字命中；标明适用版本、反例、不可接受误解。题目难度由独立人工审题及试答校准，不由标题或模型自报决定。
4. 评分点权重表达覆盖重要性；当前 `EvaluationScoreCalculator` 将 5 个 0～20 维度相加得总分。不得将评分点权重直接相加冒充后端 totalScore。
5. 校验 DTO 字段和服务约束：category 枚举、knowledgePoint ≤100、题干/答案/点正文 UTF-8 TEXT ≤65535 字节；禁用/重复/空点/排序和状态单独核对。使用真实 MyBatis 验证 generated key、batch insert、回滚与 affected rows；HTTP 401/403/管理员/非法参数齐备。
6. 建立入库前逻辑 ID→题目指纹→数据库 ID 的显式清单；不得按旧 Windows 的自增主键导入或覆盖。新增与整体替换分批审阅，历史 Session 快照保持原样。

## 4. 随机组卷的最小改造设计

### 4.1 为什么需要改动

当前 Mapper 以 category/id 升序返回，selector 永远取每类第一个未选题。扩库后依然固定；随机应在 Java 选择层进行，以保留统一校验、测试可控和既有快照/事务边界。无需 `ORDER BY RAND()`、新增框架、随机种子数据库列或题目使用次数表。

建议仅在 `QuestionPlanSelector` 增加接收 `java.util.random.RandomGenerator` 的重载（同包可见即可），现有生产签名委派给 `ThreadLocalRandom.current()`。JDK 21 已有该接口和 `nextInt(bound)`，[官方 RandomGenerator API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/random/RandomGenerator.html) 可作实现依据；不用共享的可变 seeded Random 单例承担并发。

逻辑步骤：

1. 校验候选/规则；拒绝 null 题、null ID/category 和重复 ID，复制列表，不改变 Mapper 的入参。Mapper 继续限制 difficulty/ENABLED。
2. 按 ID 稳定排序后，按规则的 requiredCategories 分桶。每类缺候选就明确失败，不能借用其他分类补洞。
3. 从每个桶的未选 ID 中用 `nextInt(bucketSize)` 取 1 道，写入局部 selectedIds。保持 requiredCategories 顺序。
4. 当前规则必选分类数等于题数；仍保留通用“剩余名额”路径：从未选候选中无放回抽样补齐。最终再次校验数量和 ID 唯一，返回不可变列表。
5. 选好后由 `prepareSnapshot` 在原 REPEATABLE_READ 事务读取所有启用评分点；不延后到逐题答题时再选。

生产不接收用户传来的 seed，不用 `currentTimeMillis` 反复初始化造成同毫秒重复。测试每个用例独立使用 `new Random(fixedSeed)`，或受控返回索引的 RandomGenerator；复现实验记录算法、seed、候选 ID 顺序/哈希、题库版本及选中 ID，只有 seed 不足以复现。完整历史重放依据已持久化快照，**不需要保存 seed 才能读历史**。

### 4.2 事务与并发不变量

- 活跃 Session 预查成功直接返回，不消耗新随机抽题；创建接口当前不接收 requestId，创建幂等来自“一个用户的活跃会话复用”，不要混淆为答案 requestId 合同。
- 并发 A/B 可以在用户锁外分别准备不同随机草稿；短写事务仍按原顺序锁 user 行、重查活跃 Session。仅胜者写入，败者丢弃自己草稿并返回胜者完整 Session。无需在随机源上加用户级锁。
- 读快照和短写事务之间允许管理员更新题库；准备时的一致版本成为本次 Session 的固定内容。历史题库随后被禁用/整体替换也不能改变已创建面试。
- 不把候选查询、外部模型、用户历史大查询或随机重试塞进 user 行锁事务。不得改变 existing CAS、version 或 requestId/attempt fencing。
- SnapshotFactory 仍校验点归属、非空、权重和并序列化。随机选择不能“试到没点就换题”掩盖脏题库；不合格启用题须在题库准入中修正。

### 4.3 策略比较与选择

| 项目 | 每场独立随机（建议首批） | 结合用户历史优先避重（后续候选） |
|---|---|---|
| 行为 | 每个分类独立均匀选 1 道 | 同难度最近 2 场已完成 MAIN 的 questionId 优先排除 |
| 数据读取 | 现有候选 + 评分点 | 额外读取最近完成 Session 及 MAIN 题身份 |
| 重复 | 不保证相邻两场不重复 | 候选够时降低近场重复；不足时回退 |
| 冷启动 | 无特别逻辑 | 无历史时等同独立随机 |
| 回退 | 缺必选分类失败 | 先未见题；用尽后选最近最少见/最久未见层，再随机打破平局 |
| 代价 | 最少代码，易测试 | 历史窗口/取消状态/更新同 ID 内容的语义和额外 SQL |
| Schema | 无 | 首期也无需；复用 session.user_id/status/ended_at 和 interview_question.question_id |

选择先实施独立随机，因为当前只有单候选且数据未就绪；历史避重不能替代扩库。若后续采用，建议最近 2 场同难度 COMPLETED、按 ended_at/id 确定顺序；只看 MAIN。活跃/取消会话暂不纳入，身份为空或当前已禁用候选忽略；总候选不足时仍满足分类和题数。保留“软避重”，不承诺同一逻辑题重写之后会被当作新题。

历史读取应在短写事务外；现有索引先用 EXPLAIN/真实量级验证，再讨论优化。若证明现有结构无法满足可量化要求，才按 AGENTS 提出具体问题和迁移影响，单独申请 Schema 变更。本设计不预设新增表/列/索引。

每桶 3 道时，独立随机理论组合数 EASY=3^5=243、MEDIUM=3^7=2187、HARD=3^9=19683；相邻两场同类题相同概率为 1/3，预期重叠分别为 5/3、7/3、9/3 道。完全相同试卷概率为 1/243、1/2187、1/19683。以上是均匀独立假设的数学值，不是压测结果，也不保证“每次都不同”。

### 4.4 未来文件级改造范围（本轮均未修改）

| 文件 | 未来职责与理由 |
|---|---|
| `src/main/java/com/kun/aiinterview/interview/service/QuestionPlanSelector.java` | 引入受控随机源重载、按桶抽样和 ID 唯一防御；主要实现集中于此 |
| `src/test/java/com/kun/aiinterview/interview/service/QuestionPlanSelectorTest.java` | 固定 seed/索引源，覆盖抽样合同和无放回规则 |
| `src/test/java/com/kun/aiinterview/interview/service/InterviewQuestionPlanSnapshotServiceTest.java` | 验证选中题的完整评分点快照，输入题库不被修改 |
| `src/test/java/com/kun/aiinterview/interview/service/InterviewSessionServiceTest.java`、`InterviewSessionTransactionServiceTest.java` | 活跃会话复用、短事务检查及随机草稿丢弃的定向单测 |
| `src/test/java/com/kun/aiinterview/interview/service/InterviewSessionTransactionIntegrationTest.java`、`InterviewQuestionPlanSnapshotIntegrationTest.java` | 扩充每类 3 候选夹具，证明真实 MySQL 竞争者返回同一胜者计划、旧题更新不改变历史 |
| `InterviewPlanConfig`、生产 SnapshotService/TransactionService、Mapper/SQL | 首批预期无需修改，作为必须保全的合同；发现必要改动先解释 |
| 未来可选历史避重 | 再单独规划 Service/Mapper 的有界查询与策略参数，本轮不搭空接口/层 |

### 4.5 必须新增或保留的回归（计划，未运行）

1. 对 5/7/9 三规则各构造每类 3 候选；断言计数、必选分类、同场无重复和原候选不变。
2. 同算法/同 seed/同有序候选给相同计划；枚举受控索引 0/1/2 可覆盖所有候选。不同 seed 可能碰巧相同，不能写“一定不同”的脆弱断言。
3. 单候选退化、空/不足、缺类别、重复 ID、null、评分点空/非正/权重和错均验证明确失败。无需借用概率分布容差证明正确性。
4. 两个真实 MySQL 请求同时创建同账号：使用屏障控制草稿 A/B，不以 sleep 碰运气；最终只有一个活跃 Session、5/7/9 MAIN、两响应相同题目快照。不同账号并行互不覆盖。
5. 保留现有 RR 并发管理员更新测试：旧题+旧点完整一致；创建后替换/禁用题，历史/detail/评价仍读快照。写事务失败时 Session/题快照原子回滚。
6. 真 MyBatis 的 generated key、batch insert/affected rows；MockMvc 401/403/合法管理员与坏参数；完成后普通 `sh mvnw -B -ntp test` 清除 RUN_REAL_* 和 MILVUS_ENABLED，使用隔离 test 库，报告实际 T/F/E/S。
7. 若历史避重获准，另测窗口排序、候选耗尽回退、NULL questionId、更新同 ID 语义、用户隔离和复用活跃会话不重新抽题。

## 5. 实施顺序与停止点

先环境补审并冻结 21 原题身份 → 审核 H01 与 42 主题 → 分批编写题干/答案/评分点并静态校验 → 随机 selector 小改及定向/完整回归 → 授权后隔离入库核验 → D3/D4 数据与质量试验。数据扩容和随机逻辑是两个可独立验收的动作；任一未完成都不能声称 63 题随机面试可用。

本轮仅产生该方案及 D0/D3/D4 文档；没有新增 Java 类、修改 Mapper/Schema、生成全部完整题目或执行导入。新评分点数量、实际数据库分布、随机抽样结果与真实难度校准均待后续证据。
