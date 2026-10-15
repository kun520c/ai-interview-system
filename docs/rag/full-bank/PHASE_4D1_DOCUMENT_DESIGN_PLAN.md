# Phase 4D1 Document Design Plan

日期：2026-10-06。**仅文件名和结构计划；不创建以下 16 篇 source documents。** 来源与冻结评分点见 [Source Manifest](PHASE_4D1_FULL_KB_SOURCE_MANIFEST.md) / [JSON Matrix](PHASE_4D1_SCORING_POINT_SOURCE_MATRIX.json)。

## 1. 编写约束与冻结切片契约

现有 5 篇保持原样，新增按一个 bank topic 一个主知识文档，不合并成通用大教程。拟正文以后可采用 UTF-8 Markdown；文件名稳定英文，标题为中文主体 + 必要技术英文。不要创建空文档或空目录。本阶段只有规划产物。

已阅读生产 `KnowledgeTextChunker`：max=1200、overlap=150、自然边界最小距离=800，均为 **Java UTF-16 code units**；不是 tokens，也不是 UTF-8 字节数。边界优先为窗口内最后一个可用空行、普通换行、`。！？；.!?;`，再硬切；相邻块起点可向前进到 whitespace，实际 overlap 可变，最终 strip。chunker 不理解 Markdown fence 或技术段落语义，因此不能认为代码块/标题绝不被切开。

设计目标：每篇约 1600–2400 UTF-16（H09 1800–2500）且优先 2–3 个有用 chunk，每个主要语义组约 700–1100；不为达标填充。元信息/引用计入总长度。S1/S2 组合为模型与机制组，S2/S3 组合为条件与边界组，S4 为短误区；S5 精简原始引用。逻辑组之间用空行，组内机制+条件+边界放在同一紧凑段落。由于自然边界、overlap 和长 URL，**预计 chunk 数不是实测，最终总数可能超过 3 或出现引用尾块**；以后获授权编写后应先审查真实生产切片的自足性，而不能为追求数字改 chunker。

每个主要组开头简短重述主题名及必要版本；不能只靠文档顶部元信息。不要在两个块之间拆开条件与结论。少量有意义的短背景允许重复，禁止整段复制现有 pilot。每个 S1–S4 的评分覆盖通过 JSON plannedDocumentSection 定位，S5 不单独承担评分事实。

读取检索契约仅用于语义自足设计：主问题+冻结评分点/追问目标组成 query，不包含用户回答；production topK 交由调用方，raw hits 经 MySQL ACTIVE/READY、文档版本、model/profile 过滤后保留原始 rank；没有补位。本文不通过标题假设存在 bankId/knowledgePoint 元数据过滤，不增 schema，不改变 query/topK/阈值。

## 2. 16 篇主文档计划

| bankId | 工作文件名（未创建） | 版本 | 总长目标 UTF-16 | 预计有用 chunks | 编写状态 |
|---|---|---|---|---|---|
| E01 | `e01_java21_interface_vs_abstract_class.md` | Java SE 21 language; JLS 8/9 | 1600–2400 | 2–3，仅估计 | PLANNED_ONLY_PENDING_COMMANDER |
| E02 | `e02_java21_arraylist_vs_linkedlist.md` | Java SE 21 API; implementation-level performance predictions labelled guidance | 1600–2400 | 2–3，仅估计 | PLANNED_ONLY_PENDING_COMMANDER |
| E03 | `e03_spring_6_2_ioc_di.md` | Project Spring Framework 6.2.19; reference line 6.2 | 1600–2400 | 2–3，仅估计 | PLANNED_ONLY_PENDING_COMMANDER |
| E04 | `e04_mysql_8_0_transaction_acid.md` | MySQL 8.0 InnoDB; durability configuration/hardware boundaries explicit | 1600–2400 | 2–3，仅估计 | PLANNED_ONLY_PENDING_COMMANDER |
| E05 | `e05_rfc9293_rfc768_tcp_vs_udp.md` | RFC 9293 TCP / RFC 768 UDP; no QUIC guarantee inherited by UDP | 1600–2400 | 2–3，仅估计 | PLANNED_ONLY_PENDING_COMMANDER |
| M01 | `m01_java21_equals_hashcode_contract.md` | Java SE 21 API contract; OpenJDK jdk-21+35 HashMap implementation illustration | 1600–2400 | 2–3，仅估计 | PLANNED_ONLY_PENDING_COMMANDER |
| M03 | `m03_jvm21_runtime_data_areas.md` | JVMS SE 21 specification; Oracle JDK 21 HotSpot Metaspace separately | 1600–2400 | 2–3，仅估计 | PLANNED_ONLY_PENDING_COMMANDER |
| M06 | `m06_mysql_8_0_innodb_index_structure.md` | MySQL 8.0 InnoDB; manual B-tree/B+Tree-family wording, no invented page layout | 1600–2400 | 2–3，仅估计 | PLANNED_ONLY_PENDING_COMMANDER |
| M07 | `m07_redis_classic_data_structures.md` | Redis official command/data-type semantics accessed 2026-10-06; classic commands only; no deployed Redis version claimed | 1600–2400 | 2–3，仅估计 | PLANNED_ONLY_PENDING_COMMANDER |
| H01 | `h01_java21_generic_type_erasure.md` | JLS/JVMS SE 21 normative; Oracle JDK 8 tutorial illustrations explicitly secondary | 1600–2400 | 2–3，仅估计 | PLANNED_ONLY_PENDING_COMMANDER |
| H02 | `h02_openjdk8u_concurrent_hashmap.md` | OpenJDK jdk8u402-b06; frozen question explicitly JDK 8 | 1600–2400 | 2–3，仅估计 | PLANNED_ONLY_PENDING_COMMANDER |
| H03 | `h03_hotspot21_g1_gc.md` | Oracle JDK 21 HotSpot G1; not all JVM collector implementations | 1600–2400 | 2–3，仅估计 | PLANNED_ONLY_PENDING_COMMANDER |
| H04 | `h04_java21_thread_pool_design.md` | Java SE 21 API; sizing/measurement/retry guidance clearly nonnormative | 1600–2400 | 2–3，仅估计 | PLANNED_ONLY_PENDING_COMMANDER |
| H05 | `h05_spring_6_2_transaction_boundary.md` | Spring Framework 6.2.19 resolved; reference 6.2; ordinary defaults unless explicit rollbackOn override | 1600–2400 | 2–3，仅估计 | PLANNED_ONLY_PENDING_COMMANDER |
| H08 | `h08_rfc9293_tcp_connection_lifecycle.md` | RFC 9293 TCP; ordinary active/passive close, simultaneous close noted | 1600–2400 | 2–3，仅估计 | PLANNED_ONLY_PENDING_COMMANDER |
| H09 | `h09_mysql_8_0_inventory_idempotency_scenario.md` | MySQL 8.0 official semantics + repository HEAD 6076a70f18f3062b8faff4fc179cf21f00a95036 / current file hashes; NO inventory module | 1800–2500 | 2–3，仅估计 | PLANNED_ONLY_COMMANDER_APPROVED_HYPOTHETICAL_SCENARIO |

### E01 — Java 21 接口与抽象类：契约、状态与选择

- 文件名：`e01_java21_interface_vs_abstract_class.md`；category：`JAVA_BASIC`；knowledgePoint：`interface_vs_abstract_class`。
- 版本：Java SE 21 language; JLS 8/9。
- 顶部元信息：Title / Knowledge category / Target question (E01) / Version scope / Primary source (JLS9)。
- 总长：1600–2400 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | 对比契约与共享基类，先声明 Java 21 范围 | E01-SP1 |
| S2 | Main mechanism | 类单继承、多接口；实例字段、构造器和方法；接口 default/static/private | E01-SP2, E01-SP3 |
| S3 | Important boundary | 接口字段为 public static final；default 不等于共享对象实例状态；设计选择 | E01-SP1, E01-SP3, E01-SP4 |
| S4 | Common misconceptions | 避免接口只能抽象方法、接口始终优于抽象类的断言 | E01-SP4 |
| S5 | Official references | 列出 JLS 8/9 对应章节 | 来源索引，不承载独立评分结论 |

**必须同组保留：** 契约、多实现与单类继承限制；抽象类状态/构造器与接口常量/default 边界。

**危险拆分边界：** default 示例与无普通实例字段的限制分离；只截取优先接口结论漏掉共享状态条件。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** ≤12 行声明式对比；不创建项目新类。

**重叠风险及控制：** E03 只重复依赖抽象的短背景，IoC 生命周期归 E03；H01 泛型规则单独写。具体风险等级见第 3 节。

**不可外推：** 不支持接口没有实现、接口拥有普通实例字段或统一的最佳设计。

### E02 — ArrayList 与 LinkedList：访问与修改成本

- 文件名：`e02_java21_arraylist_vs_linkedlist.md`；category：`JAVA_COLLECTION`；knowledgePoint：`arraylist_vs_linkedlist`。
- 版本：Java SE 21 API; implementation-level performance predictions labelled guidance。
- 顶部元信息：Title / Knowledge category / Target question (E02) / Version scope / Primary source (ARRAY)。
- 总长：1600–2400 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | 动态数组与双向链表 | E02-SP1 |
| S2 | Main mechanism | 随机访问、顺序遍历；数组移动与链表定位后重连 | E02-SP2, E02-SP3 |
| S3 | Important boundary | LinkedList 必须计入定位成本；区分按索引修改、已有 iterator、两端操作 | E02-SP3, E02-SP4 |
| S4 | Common misconceptions | 避免所有链表插入 O(1)、所有场景更快；无未经测量的缓存局部性数值 | E02-SP4 |
| S5 | Official references | ArrayList/LinkedList API | 来源索引，不承载独立评分结论 |

**必须同组保留：** 访问复杂度与底层结构；链表修改必须包含位置已知前提及按索引遍历成本。

**危险拆分边界：** 复杂度表与前提脚注分离；长代码把定位与修改拆到不同 chunk。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** ≤8 行按索引 vs 已定位 iterator 示例；不做 benchmark。

**重叠风险及控制：** M02/H02 同为集合但本文不展开哈希桶/树化。具体风险等级见第 3 节。

**不可外推：** 不支持 LinkedList 总是更快或总是 O(1) 插入；不写固定 ArrayList 扩容倍数。

### E03 — Spring 6.2 IoC 与 DI：容器与依赖

- 文件名：`e03_spring_6_2_ioc_di.md`；category：`SPRING`；knowledgePoint：`ioc_di`。
- 版本：Project Spring Framework 6.2.19; reference line 6.2。
- 顶部元信息：Title / Knowledge category / Target question (E03) / Version scope / Primary source (IOC)。
- 总长：1600–2400 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | IoC 把创建、配置和依赖组装交给容器；DI 是方式 | E03-SP1, E03-SP4 |
| S2 | Main mechanism | 构造器和 setter 注入的机制与职责 | E03-SP2 |
| S3 | Important boundary | 依赖抽象便于替换测试实现；创建、初始化、销毁由容器管理 | E03-SP3, E03-SP4 |
| S4 | Common misconceptions | 避免把 IoC 等同某个注解；不宣称 DI 自动解决所有耦合 | 来源索引，不承载独立评分结论 |
| S5 | Official references | 容器介绍、DI 与生命周期页 | 来源索引，不承载独立评分结论 |

**必须同组保留：** IoC 定义与 DI/组装关系；注入方式与依赖来源；生命周期不等于事务生命周期。

**危险拆分边界：** 仅保留注解示例而丢容器组装机制；创建与销毁割裂。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** ≤10 行构造器注入示意；不用生产项目配置。

**重叠风险及控制：** E01 抽象契约只用一段；M05/H05 代理/事务边界不在此扩写。具体风险等级见第 3 节。

**不可外推：** 不支持所有对象都自动成为 bean、DI 必须 field injection。

### E04 — MySQL 8.0 InnoDB 事务与 ACID

- 文件名：`e04_mysql_8_0_transaction_acid.md`；category：`MYSQL`；knowledgePoint：`transaction_acid`。
- 版本：MySQL 8.0 InnoDB; durability configuration/hardware boundaries explicit。
- 顶部元信息：Title / Knowledge category / Target question (E04) / Version scope / Primary source (ACID)。
- 总长：1600–2400 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | ACID 与逻辑工作单元 | E04-SP1, E04-SP5 |
| S2 | Main mechanism | 原子提交/回滚；一致性约束和业务不变量；隔离级别 | E04-SP1, E04-SP2, E04-SP3, E04-SP5 |
| S3 | Important boundary | 持久性依赖正确配置和存储条件；隔离性不等于所有事务无并发 | E04-SP3, E04-SP4 |
| S4 | Common misconceptions | 避免事务自动满足任意业务规则、提交在任意硬件故障下都绝对可靠 | 来源索引，不承载独立评分结论 |
| S5 | Official references | ACID、隔离级别、事务语句 | 来源索引，不承载独立评分结论 |

**必须同组保留：** 每个 ACID 含义及保障条件；事务多步骤与整体回滚；持久性和刷盘/硬件条件。

**危险拆分边界：** ACID 四行表拆到不同 chunk 无上下文；持久性结论与配置条件分离。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** ≤8 行 BEGIN/两步写/COMMIT 或 ROLLBACK 的假设例；不执行 SQL。

**重叠风险及控制：** H06 负责 Read View/undo；M06 负责索引布局；本文仅短背景。具体风险等级见第 3 节。

**不可外推：** 不支持业务不变量凭事务自动正确或最高隔离意味着完全无异常。

### E05 — TCP 与 UDP：语义与应用取舍

- 文件名：`e05_rfc9293_rfc768_tcp_vs_udp.md`；category：`NETWORK`；knowledgePoint：`tcp_vs_udp`。
- 版本：RFC 9293 TCP / RFC 768 UDP; no QUIC guarantee inherited by UDP。
- 顶部元信息：Title / Knowledge category / Target question (E05) / Version scope / Primary source (TCP)。
- 总长：1600–2400 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | TCP 连接字节流与 UDP 数据报 | E05-SP1 |
| S2 | Main mechanism | 可靠有序传输、ACK/重传；UDP 基础服务无同等保证 | E05-SP2, E05-SP3 |
| S3 | Important boundary | TCP 流量/拥塞与状态维护；UDP 应用仍需处理拥塞与可靠性需求 | E05-SP3, E05-SP4 |
| S4 | Common misconceptions | 按业务可靠性、延迟和消息边界选择；不要简单 TCP 慢 UDP 快 | E05-SP4 |
| S5 | Official references | RFC 9293 2.2/3.8 与 RFC 768 | 来源索引，不承载独立评分结论 |

**必须同组保留：** 服务语义、传输单位与基础保障；应用选择与应用层补充保障条件。

**危险拆分边界：** UDP 不可靠与应用自行保障分离；只留下快慢结论。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** 简短对比表即可；无需 socket 程序。

**重叠风险及控制：** H08 握手/挥手状态细节归 H08；本文保留一句连接背景。具体风险等级见第 3 节。

**不可外推：** 不支持 UDP 本身提供可靠流或 TCP 保留应用消息边界。

### M01 — equals 与 hashCode：契约与哈希集合

- 文件名：`m01_java21_equals_hashcode_contract.md`；category：`JAVA_BASIC`；knowledgePoint：`equals_hashcode_contract`。
- 版本：Java SE 21 API contract; OpenJDK jdk-21+35 HashMap implementation illustration。
- 顶部元信息：Title / Knowledge category / Target question (M01) / Version scope / Primary source (OBJECT)。
- 总长：1600–2400 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | equals 逻辑相等及自反/对称/传递/一致/null 契约 | M01-SP1 |
| S2 | Main mechanism | 相等对象相同 hashCode；碰撞不意味着 equals 相等；哈希定位后确认 key | M01-SP2, M01-SP3 |
| S3 | Important boundary | 覆盖 equals 通常配套 hashCode；修改 key 相等字段的危险边界 | M01-SP4 |
| S4 | Common misconceptions | 反例解释查找/去重失效，不声称不同对象不得碰撞 | M01-SP4 |
| S5 | Official references | Object API、HashMap API 与固定 Java 21 源码 | 来源索引，不承载独立评分结论 |

**必须同组保留：** equal ⇒ 同 hash，与反向不成立；哈希定位/equals 确认与覆盖配套规则。

**危险拆分边界：** equals 代码与 hashCode 反例条件分离；碰撞示例被误读成 equals。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** ≤12 行不完整实现反例与修正职责；不写整页 boilerplate。

**重叠风险及控制：** M02/H02 只引用契约一句，不复制树化或并发实现。具体风险等级见第 3 节。

**不可外推：** 不支持 hash 相同必相等、所有 Map 使用 hashCode、hash 恒定一定立即功能失败。

### M03 — JVM SE 21 运行时数据区与 HotSpot 边界

- 文件名：`m03_jvm21_runtime_data_areas.md`；category：`JVM`；knowledgePoint：`runtime_data_areas`。
- 版本：JVMS SE 21 specification; Oracle JDK 21 HotSpot Metaspace separately。
- 顶部元信息：Title / Knowledge category / Target question (M03) / Version scope / Primary source (JVMS2)。
- 总长：1600–2400 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | 共享和线程私有运行时数据区 | M03-SP1, M03-SP2 |
| S2 | Main mechanism | Heap 对象、线程栈 frame、局部变量/引用、operand stack、PC 和 native stack | M03-SP2, M03-SP3, M03-SP4 |
| S3 | Important boundary | Method Area 是规范概念；Metaspace 是 HotSpot 的类元数据实现部分 | M03-SP5 |
| S4 | Common misconceptions | 对象/引用变量区分；不能把所有方法区内容直接等同 Metaspace | 来源索引，不承载独立评分结论 |
| S5 | Official references | JVMS 2.5/2.6 与 Oracle JDK 21 Class Metadata | 来源索引，不承载独立评分结论 |

**必须同组保留：** 区域名称、共享/私有和用途一体；引用局部变量与所指对象；Method Area 规范与 Metaspace 实现边界。

**危险拆分边界：** 引用变量/对象两行分离；规范及 HotSpot 图拆开失去标签。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** 小区域表与 3 行对象引用例；不运行 JVM 探针。

**重叠风险及控制：** H03 负责 Heap Region 回收；本文不展开 G1 过程。具体风险等级见第 3 节。

**不可外推：** 不支持所有对象必须物理分配于堆而忽略优化，或 Method Area=Metaspace。

### M06 — InnoDB 索引：页、聚簇与覆盖

- 文件名：`m06_mysql_8_0_innodb_index_structure.md`；category：`MYSQL`；knowledgePoint：`innodb_index_structure`。
- 版本：MySQL 8.0 InnoDB; manual B-tree/B+Tree-family wording, no invented page layout。
- 顶部元信息：Title / Knowledge category / Target question (M06) / Version scope / Primary source (INDEX)。
- 总长：1600–2400 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | 多路有序树与页访问模型；扇出/树高关系为结构推导 | M06-SP1 |
| S2 | Main mechanism | 聚簇叶子行数据、主键/备用聚簇键；二级索引键+主键定位 | M06-SP3, M06-SP4 |
| S3 | Important boundary | 等值/范围 vs 普通 Hash；覆盖条件及回表路径 | M06-SP2, M06-SP5 |
| S4 | Common misconceptions | 覆盖不等于任意查询绝不访问聚簇记录；不臆造固定扇出/高度 | 来源索引，不承载独立评分结论 |
| S5 | Official references | 索引类型、物理结构、B-tree/Hash 对比、覆盖与 glossary | 来源索引，不承载独立评分结论 |

**必须同组保留：** 高扇出/高度推导与页单位；二级键+主键及所需列决定是否回表；覆盖条件与查询例。

**危险拆分边界：** 回表路径与覆盖条件分离；索引页图与主键缺失时备用选择分离。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** 一个 ≤8 行查询/索引示意；不执行 DDL、不画不受支持的记录字节布局。

**重叠风险及控制：** E04 ACID/H06 可见性仅说明边界，不复制 MVCC 内部。具体风险等级见第 3 节。

**不可外推：** 不支持默认页大小永远不可改、所有二级索引都必须回表、空间索引也是普通 B-tree。

### M07 — Redis 五类常用数据结构与选择

- 文件名：`m07_redis_classic_data_structures.md`；category：`REDIS`；knowledgePoint：`redis_data_structures`。
- 版本：Redis official command/data-type semantics accessed 2026-10-06; classic commands only; no deployed Redis version claimed。
- 顶部元信息：Title / Knowledge category / Target question (M07) / Version scope / Primary source (REDISTYPES)。
- 总长：1600–2400 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | 五类常用结构不是 Redis 的完整类型清单 | M07-SP1 |
| S2 | Main mechanism | String 单值/计数；Hash 字段；List 有序两端操作 | M07-SP2, M07-SP3 |
| S3 | Important boundary | Set 唯一与集合运算；Sorted Set 唯一成员和 score 排序 | M07-SP4, M07-SP5 |
| S4 | Common misconceptions | 简单 List 队列不等于持久可靠消息系统；不展开锁算法或底层编码 | 来源索引，不承载独立评分结论 |
| S5 | Official references | 官方概览及 String/Hash/Set/Sorted Set、LPUSH/RPOP | 来源索引，不承载独立评分结论 |

**必须同组保留：** 每类结构、关键语义和适用操作；List 顺序及 LPUSH/RPOP 方向；ZSet 成员唯一+score 两个条件。

**危险拆分边界：** 五类表被截成无类型名用例；List 简单队列与可靠性条件分离。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** ≤8 条经典命令，按结构成组；单元素 LPUSH/RPOP，排除新版本选项。

**重叠风险及控制：** H07 只重复 String 可存 token；锁获取/释放/租期归 H07。具体风险等级见第 3 节。

**不可外推：** 不支持项目已部署 Redis、五类就是全部 Redis 类型、List 自动保证任务不丢。

### H01 — Java 泛型擦除、桥接与运行时边界

- 文件名：`h01_java21_generic_type_erasure.md`；category：`JAVA_BASIC`；knowledgePoint：`generic_type_erasure`。
- 版本：JLS/JVMS SE 21 normative; Oracle JDK 8 tutorial illustrations explicitly secondary。
- 顶部元信息：Title / Knowledge category / Target question (H01) / Version scope / Primary source (JLS4)。
- 总长：1600–2400 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | 编译期类型安全、bound/Object 擦除与参数化实例共享 Class | H01-SP1, H01-SP2 |
| S2 | Main mechanism | 插入 cast 与 bridge method 保持类型检查和多态 | H01-SP3 |
| S3 | Important boundary | new T、泛型数组及擦除后冲突签名至少两项限制；Java 16+ instanceof 有静态类型边界 | H01-SP4, H01-SP5 |
| S4 | Common misconceptions | Signature 元数据可保留并由反射获取，不等同对象携带完整泛型类型实化 | H01-SP5 |
| S5 | Official references | JLS 4/8/15、JVMS 4、官方擦除/bridge 教程 | 来源索引，不承载独立评分结论 |

**必须同组保留：** bound/Object 擦除与编译期安全；bridge/强转的目的及示例；限制的操作与版本/静态类型前提；反射 Signature 与实例类型实化差别。

**危险拆分边界：** 仅留泛型全部消失口号；instanceof 限制与 Java 16+ 例外分离。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** ≤12 行泛型方法/bridge 示意；至少两个明确编译期限制；不编译示例。

**重叠风险及控制：** E01 类接口与 M01 equals 类型判断只保留短背景；泛型元数据归本文。具体风险等级见第 3 节。

**不可外推：** 不支持 Java 21 所有参数化 instanceof 都非法、所有泛型信息完全消失。

**版本重点：** Java 16+ allows some parameterized instanceof when statically checked; use new T(), generic-array creation, or same-erasure overload as the at-least-two limitations. Preserve frozen text exactly, but do not author the overly broad old-language shorthand.

### H02 — JDK 8 ConcurrentHashMap：分桶协调与扩容

- 文件名：`h02_openjdk8u_concurrent_hashmap.md`；category：`JAVA_COLLECTION`；knowledgePoint：`concurrent_hashmap`。
- 版本：OpenJDK jdk8u402-b06; frozen question explicitly JDK 8。
- 顶部元信息：Title / Knowledge category / Target question (H02) / Version scope / Primary source (CHM8)。
- 总长：1600–2400 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | table、链表/树桶；不是旧 Segment 主体结构 | H02-SP1 |
| S2 | Main mechanism | 空桶 CAS、非空桶头 synchronized 并复核；范围是 bin | H02-SP2, H02-SP3 |
| S3 | Important boundary | get 的可见性及无同等更新锁；并发协作搬迁/遇迁移状态 | H02-SP4, H02-SP5 |
| S4 | Common misconceptions | 不声称全部操作 lock-free，thread-safe 不等于复合业务操作自动原子 | 来源索引，不承载独立评分结论 |
| S5 | Official references | 固定 8u 源码中的 Node/TreeBin、putVal/get/transfer | 来源索引，不承载独立评分结论 |

**必须同组保留：** 空/非空桶条件与 CAS/monitor 路径；桶粒度协调和复核条件；读可见性、不获得相同更新锁及复合操作边界。

**危险拆分边界：** CAS 与非空桶条件分离；扩容流程长摘录截断，误以为全部锁自由。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** ≤10 行路径伪代码即可，不粘 6000 行源码、不要求内部名字背诵。

**重叠风险及控制：** M02 哈希桶词汇短重复；并发协调归 H02，HashMap 树化阈值不复制。具体风险等级见第 3 节。

**不可外推：** 不支持 Java 21 私有实现与 8u 完全相同、Segment 为主体、全部写无锁。

### H03 — HotSpot Java 21 G1：Region 与暂停目标

- 文件名：`h03_hotspot21_g1_gc.md`；category：`JVM`；knowledgePoint：`g1_gc`。
- 版本：Oracle JDK 21 HotSpot G1; not all JVM collector implementations。
- 顶部元信息：Title / Knowledge category / Target question (H03) / Version scope / Primary source (G1)。
- 总长：1600–2400 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | Region 与 Eden/Survivor/Old 角色 | H03-SP1 |
| S2 | Main mechanism | Young 存活复制/晋升；CSet 回收收益与预计成本 | H03-SP2, H03-SP3 |
| S3 | Important boundary | 并发标记老年代存活；Mixed 年轻代加部分老年代；仍有 STW | H03-SP4, H03-SP5 |
| S4 | Common misconceptions | pause goal 是目标不是实时保证；Remembered Set 只作来源支持的简短背景 | H03-SP5 |
| S5 | Official references | Java 21 G1 Heap Layout/Cycle/Collection Set | 来源索引，不承载独立评分结论 |

**必须同组保留：** Region 角色与不要求整个代连续；CSet 成本、收益与非硬暂停保证；并发标记→Mixed，STW 必须一起。

**危险拆分边界：** 只留并发误导无 STW；Young/Mixed 定义与各自回收对象分离。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** 一个过程表/简短文本时间线；不新增 JVM 参数或 GC 压测。

**重叠风险及控制：** M03 Heap 位置与线程私有短背景；本文不重复全数据区表。具体风险等级见第 3 节。

**不可外推：** 不支持 G1 没有 STW、暂停目标绝对保证、所有 JVM 都用相同 Region 布局。

### H04 — ThreadPoolExecutor：容量、拒绝与任务责任

- 文件名：`h04_java21_thread_pool_design.md`；category：`JAVA_CONCURRENCY`；knowledgePoint：`thread_pool_design`。
- 版本：Java SE 21 API; sizing/measurement/retry guidance clearly nonnormative。
- 顶部元信息：Title / Knowledge category / Target question (H04) / Version scope / Primary source (POOL)。
- 总长：1600–2400 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | 突发任务且不能静默丢失的约束 | 来源索引，不承载独立评分结论 |
| S2 | Main mechanism | core→queue→maximum→reject 提交顺序；keepAlive 非核心空闲回收 | H04-SP1 |
| S3 | Important boundary | CPU/I/O、资源/下游与有界排队；拒绝明确失败/反压及 shutdown 边界 | H04-SP2, H04-SP3, H04-SP4 |
| S4 | Common misconceptions | active/queue/reject/latency 与压测；executor 本身不持久化、不保证跨崩溃不丢 | H04-SP4, H04-SP5 |
| S5 | Official references | API Queuing/Rejected tasks/hooks/statistics | 来源索引，不承载独立评分结论 |

**必须同组保留：** 提交顺序与队列满前 maximum 不扩展；容量与内存/排队压力；不能丢失与 rejection/shutdown/崩溃边界。

**危险拆分边界：** 只留下 CallerRuns 保证不丢的错误结论；参数模板与负载假设分离。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** ≤12 行参数/handler 示意；不指定万能数字；不创建线程池或压测。

**重叠风险及控制：** M04 可见性一句背景；H09 持久幂等不是线程池提供的能力。具体风险等级见第 3 节。

**不可外推：** 不支持 volatile 保证队列正确性、CallerRuns 永不丢任务、内存队列自动持久可靠。

### H05 — Spring 6.2 事务边界与回滚规则

- 文件名：`h05_spring_6_2_transaction_boundary.md`；category：`SPRING`；knowledgePoint：`transaction_boundary`。
- 版本：Spring Framework 6.2.19 resolved; reference 6.2; ordinary defaults unless explicit rollbackOn override。
- 顶部元信息：Title / Knowledge category / Target question (H05) / Version scope / Primary source (TX)。
- 总长：1600–2400 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | 业务用例的数据库原子边界 | H05-SP4 |
| S2 | Main mechanism | 代理调用/self-invocation；RuntimeException/Error/checked 与 rollbackFor | H05-SP1, H05-SP2 |
| S3 | Important boundary | 吞异常、错误 manager/调用边界；6.2 rollbackOn=ALL_EXCEPTIONS 可改变默认 | H05-SP2, H05-SP3 |
| S4 | Common misconceptions | 缩短持锁范围；远程/跨资源超出 local JDBC transaction，补偿/消息仅候选设计 | H05-SP4, H05-SP5 |
| S5 | Official references | Transactional、Rolling Back、local/global manager | 来源索引，不承载独立评分结论 |

**必须同组保留：** 代理/自调用的前提与失效现象；异常类型、默认、rollbackFor 与 6.2 override；local resource、原子边界和远程调用风险。

**危险拆分边界：** checked 默认与 override 分离；跨资源建议被截为本项目已实现消息补偿。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** ≤12 行代理入口/rollbackFor 和吞异常反例；不修改项目配置。

**重叠风险及控制：** M05 只简述代理调用必要背景；本篇不复制 JDK/CGLIB 选择教程。具体风险等级见第 3 节。

**不可外推：** 不支持所有 @Transactional 调用均生效、本地事务回滚远程结果、checked 永远不回滚。

### H08 — TCP 连接生命周期与状态诊断

- 文件名：`h08_rfc9293_tcp_connection_lifecycle.md`；category：`NETWORK`；knowledgePoint：`tcp_connection_lifecycle`。
- 版本：RFC 9293 TCP; ordinary active/passive close, simultaneous close noted。
- 顶部元信息：Title / Knowledge category / Target question (H08) / Version scope / Primary source (TCP)。
- 总长：1600–2400 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | 序列号和状态同步、旧重复请求问题 | H08-SP1, H08-SP2 |
| S2 | Main mechanism | 常见 SYN/SYN-ACK/ACK；全双工独立 FIN/ACK 关闭 | H08-SP1, H08-SP3 |
| S3 | Important boundary | TIME-WAIT 的 2MSL 和最后 ACK/旧报文；ACK+FIN 可合并 | H08-SP3, H08-SP4 |
| S4 | Common misconceptions | CLOSE-WAIT 收 FIN 后本地尚未 close；数量分析为诊断假设而非直接定罪 | H08-SP5 |
| S5 | Official references | RFC 9293 3.3.2/3.4.1/3.5/3.6/3.10 | 来源索引，不承载独立评分结论 |

**必须同组保留：** SYN/ISN 确认与旧重复请求动机；独立关闭方向、FIN/ACK 及可能合并；状态含义、正常条件、诊断证据。

**危险拆分边界：** 握手箭头与 ISN 说明分离；TIME-WAIT 正常条件与异常数量建议分离。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** 小握手/关闭文本图，每图含上下文 ≤12 行；不用抓包/网络试验。

**重叠风险及控制：** E05 只重复可靠字节流/双工一句；应用协议选型归 E05。具体风险等级见第 3 节。

**不可外推：** 不支持挥手必须永远四包、TIME-WAIT 必为 bug、CLOSE-WAIT=应用已关闭。

### H09 — MySQL 库存与幂等：假设场景与项目事实边界

- 文件名：`h09_mysql_8_0_inventory_idempotency_scenario.md`；category：`PROJECT_SCENARIO`；knowledgePoint：`concurrent_inventory_idempotency`。
- 版本：MySQL 8.0 official semantics + repository HEAD 6076a70f18f3062b8faff4fc179cf21f00a95036 / current file hashes; NO inventory module。
- 顶部元信息：Title / Knowledge category / Target question (H09) / Version scope / Primary source (LOCK)。
- 总长：1800–2500 UTF-16；主要语义组 700–1100；预计 2–3 个有用 chunk，未实测，不硬凑字数。

| 节 | 标题 | 计划内容边界 | 对应评分点 |
|---|---|---|---|
| S1 | Core model | 明确这是 Commander 已批准的假设 MySQL 库存设计；当前项目无库存模块，面试提交幂等实现仅作类比，不作为库存证据 | 来源索引，不承载独立评分结论 |
| S2 | Main mechanism | 规划库存条件 UPDATE/affected rows 或事务 FOR UPDATE；稳定键及唯一约束 | H09-SP1, H09-SP2 |
| S3 | Important boundary | 在同一 MySQL 事务性资源中，将库存变更、幂等状态及必须原子提交的其他数据库记录放入同一事务；处理唯一冲突，不声称本地事务覆盖任意远程副作用 | H09-SP3 |
| S4 | Common misconceptions | 区分 deadlock 事务回滚、lock-wait 默认可能仅语句回滚与客户端/网络超时结果不明；有限重试复用同键，先查/恢复持久状态再决定是否扣减；不声称项目已有库存恢复 | H09-SP4 |
| S5 | Official references | 官方 SQL/事务/失败语义、工程设计推理与项目 source/schema/tests 类比明确分栏 | 来源索引，不承载独立评分结论 |

**必须同组保留：** 无库存实现声明与任何库存示例；条件更新及 affected rows；行锁+检查+扣减同事务；唯一稳定键、重复请求查结果与异常回滚；deadlock/lock wait/unknown commit 分型及同键重试。

**危险拆分边界：** 把面试 CAS 当成库存实现；唯一冲突处理与扣减放在不同块；把超时直接写为整事务已回滚。按本节列出的条件与结论一体编写，避免把关键例外仅放文末。

**代码/例子需求：** Commander 已批准假设场景定位；未来另行授权 Phase 4D2 后可写 ≤10 行 SQL/状态示意。本阶段不创建知识正文、DDL 或库存测试。

**重叠风险及控制：** H06 快照读不是锁临界区；H07 锁 token 不是持久业务幂等键；H04 内存任务不是已提交结果。具体风险等级见第 3 节。

**不可外推：** 不支持项目已实现库存、订单、version 扣减、库存重试；Redis 锁不替代最终数据库约束。

**Commander 假设场景已批准：** H09-SP1..SP4 均为 SUPPORTED；不属于 PROJECT_SPECIFIC。projectImplementationPresent=false，projectEvidenceRole=ANALOGY_ONLY_NOT_INVENTORY_EVIDENCE，hypotheticalScenarioApprovedByCommander=true。当前项目没有库存/商品/订单实现；面试幂等仅用于短类比，不能证明库存行为。无需等待另一个库存实现来源，但本补丁不创建 `h09_mysql_8_0_inventory_idempotency_scenario.md`，不进入 Phase 4D2。

## 3. 21 主题跨主题重叠审计

风险只按共享概念与文档结构推理，不使用 Phase 4C2/4C3 query 分数或 ranks。LOW/MEDIUM/HIGH 是设计警示，**不是测得的检索错误率**。下表覆盖 21 个不同 bankId；分组行表示该组相关关系的语义比较，不声称不存在其他低风险重叠。原 5 篇仅作为只读边界对照，不因本表而重写。

| 对比主题 | 共同概念 | 允许短重复 | 必须保留于各自主题 | 检索混淆风险 |
|---|---|---|---|---|
| M02 ↔ H02 | 哈希表、bin、链/树、扩容 | 键定位及冲突一句，8u 版本标签 | M02 put/树化条件；H02 CAS、bin monitor、协作搬迁 | HIGH |
| M04 ↔ H04 | 并发、共享状态、可见性 | 并发约束必须明确的一句背景 | M04 JMM/volatile 非复合原子；H04 容量/队列/拒绝，不用 volatile 当调度保证 | MEDIUM |
| M05 ↔ H05 | Spring 代理、自调用 | 必须经过代理及 self-invocation 边界短重复 | M05 JDK/CGLIB 与代理能力；H05 回滚、manager 与事务范围 | HIGH |
| E04 ↔ H06 | 事务、隔离、读取 | 隔离级别存在不同保证 | E04 ACID 逻辑单元；H06 undo/版本/Read View/快照时点 | HIGH |
| E04 ↔ M06 | InnoDB、表与事务 | InnoDB 版本一句 | E04 原子一致持久；M06 页树、聚簇、二级、覆盖 | MEDIUM |
| M06 ↔ H06 | 聚簇记录、数据读取 | 聚簇记录承载行数据一句 | M06 查询路径/所需列；H06 记录版本与可见性，不能认为覆盖必然免所有访问 | HIGH |
| M07 ↔ H07 | Redis String、key/value | String 可存 token 一句 | M07 五类结构用法；H07 SET NX PX、Lua/token/租期/故障边界 | HIGH |
| H09 ↔ H06 | MySQL 并发、FOR UPDATE | 快照读与锁定读区别一句 | H09 库存原子扣减/持久幂等假设；H06 MVCC，不把普通快照当锁保护 | HIGH |
| H09 ↔ H07 | 并发与重试正确性 | 最终数据层须有正确性保障 | H09 持久请求键/唯一/事务；H07 租约 token，不把 token 当业务 requestId | HIGH |
| E05 ↔ H08 | TCP 连接、可靠性、双工 | TCP 字节流和连接背景 | E05 TCP/UDP 服务取舍；H08 ISN/FIN/状态/2MSL | HIGH |
| E01 ↔ E03 | 接口抽象、多实现、解耦 | 依赖稳定契约利于替换的短背景 | E01 语言能力/状态；E03 容器组装/DI/生命周期 | MEDIUM |
| M01 ↔ M02 ↔ H02 | hashCode、equals、key | equal ⇒ same hash；冲突不代表相等 | M01 Object 契约与配套覆盖；M02 8u put/树化；H02 8u 并发协调，版本不可互换 | HIGH |
| E02 ↔ M02 ↔ H02 | 集合、数组/链表 | 存储结构影响操作成本 | E02 List 的索引/iterator 成本；另两篇 Map 哈希桶不能替代 List 解释 | MEDIUM |
| M03 ↔ H03 | Heap 与对象、JVM 内存 | 堆承载对象的规范背景 | M03 所有运行时区域和线程作用域；H03 Java 21 HotSpot G1 Region/回收 | MEDIUM |
| H01 ↔ E01 | 类、接口、类型 | 语言版本标签及类型背景 | H01 擦除/bridge/Signature；E01 契约/继承/共享实例状态 | LOW |
| H09 ↔ E04 ↔ H05 | 原子性与事务边界 | 相关数据库步骤必须共同成功/失败 | H09 库存是假设场景且已获 Commander 定位批准；E04 ACID；H05 Spring 拦截/rollbackFor | HIGH |
| H04 ↔ H09 | 任务不丢、重试 | 业务必须明确定义失败/重试去向 | H04 executor 非持久任务调度；H09 持久去重/未知提交结果，不能由线程池替代 | MEDIUM |

## 4. 后续 Commander 审核项目

1. Commander 已接受 16 篇一题一文的来源与设计，并已批准 H09 假设方案定位，SOURCE_GAP_COUNT=0。最终核验须保持官方机制、工程设计和项目事实分离；项目面试幂等只能作类比。
2. 确认 Java 21 instanceof 条件、Spring 6.2 可变默认回滚、CallerRuns shutdown 丢弃及 executor 非持久队列、MySQL deadlock/lock-wait rollback 范围这些必须保持同组的边界。
3. PHASE_4D2: ELIGIBLE_AFTER_COMMANDER_PATCH_VERIFICATION；最终状态为 ELIGIBLE_PENDING_COMMANDER_FINAL_CHECK。编写/静态切片检查/上传/processing/真实 full-bank Baseline 均是后续步骤；本补丁未进入 Phase 4D2，没有执行其中任一步，不添加 reranker、阈值或技术栈。

RERANKER_DECISION: **DEFER_UNTIL_FULL_BANK_BASELINE**。
