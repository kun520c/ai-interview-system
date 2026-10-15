# Phase 4D1 Full Knowledge-Base Source Manifest

日期：2026-10-06。状态：**规划及 Commander H09 来源修订已完成，等待最终核验；未进入正文编写或入库**。

## 1. 冻结基线与使用规则

题库：[Phase 1 frozen JSON](../../question-bank/PHASE_1_QUESTION_BANK_FINAL.json)；原始 SHA-256：`ce6fff9bd42b064009b01c0300e2da5da16e73d04c363e3cf7f2b351cc1b6fd3`。21 题 / 97 评分点；已有 5 篇覆盖 23 点，新增规划 16 篇覆盖 **74 点**，每个新增评分点只出现一次。最终 **21/21 是规划覆盖，不是已编写、已入库或已验证覆盖**。

JSON 不含 bankId/scoringPointId；bankId 对应 Phase 1 Markdown 的 E01…H09 标识，按冻结 JSON 顺序、category 与 knowledgePoint 核对。逻辑评分点 ID 使用 `<bankId>-SP<题内从 1 开始的序号>`，**不是数据库主键**。原文、pointType、weight 不改写。

只采用 Oracle/OpenJDK、Spring、MySQL、Redis、IETF/RFC Editor 的一手来源。源目录、代码、配置、schema 和参数保持不变；Redis 数据结构是领域知识，不能据此推断项目已使用 Redis。技术事实与结构推论、工程建议、项目事实分开标记；引用不意味着已经执行过相应代码。

### 已有文档 KEEP_UNCHANGED

| bankId | 冻结路径 | SHA-256 |
|---|---|---|
| M02 | [m02_hashmap_jdk8u_put_resize_treeify.md](../source-documents/m02_hashmap_jdk8u_put_resize_treeify.md) | `c5e93fa3c3d3af8fb6e78b60db2d309ccb4e37a286e6ac47d4bb6f76fb053b22` |
| M04 | [m04_volatile_jmm_java21.md](../source-documents/m04_volatile_jmm_java21.md) | `fd1f73f1e1ce307567bcf6b4cf737315b9c36591f1de3620689a979dea2b760c` |
| M05 | [m05_spring_6_2_aop_proxy_self_invocation.md](../source-documents/m05_spring_6_2_aop_proxy_self_invocation.md) | `b622cf633bb2ccf7320c33feede070f3875f7670e7f92c89488e2a5ee5457788` |
| H06 | [h06_mysql_8_0_innodb_mvcc_consistent_read.md](../source-documents/h06_mysql_8_0_innodb_mvcc_consistent_read.md) | `17f181b4460fb6deca4af8e23a7188df3de3a7640f41c254d39aa0af00a06bea` |
| H07 | [h07_redis_distributed_lock_baseline.md](../source-documents/h07_redis_distributed_lock_baseline.md) | `e79aff2f6d42ce77989566e6d7d611de77ac5720f962f325ffbae307a1ae88c7` |

### 新增主题概览

| bankId | category | difficulty | knowledgePoint | 点数 | 主来源 | 规划状态 |
|---|---|---|---|---:|---|---|
| E01 | JAVA_BASIC | EASY | interface_vs_abstract_class | 4 | [JLS9: JLS 21 Chapter 9 Interfaces](https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html) | SUPPORTED / 等待审核 |
| E02 | JAVA_COLLECTION | EASY | arraylist_vs_linkedlist | 4 | [ARRAY: ArrayList API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html) | SUPPORTED / 等待审核 |
| E03 | SPRING | EASY | ioc_di | 4 | [IOC: Introduction to the Spring IoC Container](https://docs.spring.io/spring-framework/reference/6.2/core/beans/introduction.html) | SUPPORTED / 等待审核 |
| E04 | MYSQL | EASY | transaction_acid | 5 | [ACID: InnoDB and the ACID Model](https://dev.mysql.com/doc/refman/8.0/en/mysql-acid.html) | SUPPORTED / 等待审核 |
| E05 | NETWORK | EASY | tcp_vs_udp | 4 | [TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html) | SUPPORTED / 等待审核 |
| M01 | JAVA_BASIC | MEDIUM | equals_hashcode_contract | 4 | [OBJECT: Object API equals/hashCode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html) | SUPPORTED / 等待审核 |
| M03 | JVM | MEDIUM | runtime_data_areas | 5 | [JVMS2: JVMS 21 Chapter 2 Structure](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html) | SUPPORTED / 等待审核 |
| M06 | MYSQL | MEDIUM | innodb_index_structure | 5 | [INDEX: Clustered and Secondary Indexes](https://dev.mysql.com/doc/refman/8.0/en/innodb-index-types.html) | SUPPORTED / 等待审核 |
| M07 | REDIS | MEDIUM | redis_data_structures | 5 | [REDISTYPES: Redis data types](https://redis.io/docs/latest/develop/data-types/) | SUPPORTED / 等待审核 |
| H01 | JAVA_BASIC | HARD | generic_type_erasure | 5 | [JLS4: JLS 21 Types, Values, and Variables](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html) | SUPPORTED / 等待审核 |
| H02 | JAVA_COLLECTION | HARD | concurrent_hashmap | 5 | [CHM8: OpenJDK 8u ConcurrentHashMap.java](https://raw.githubusercontent.com/openjdk/jdk8u/jdk8u402-b06/jdk/src/share/classes/java/util/concurrent/ConcurrentHashMap.java) | SUPPORTED / 等待审核 |
| H03 | JVM | HARD | g1_gc | 5 | [G1: Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html) | SUPPORTED / 等待审核 |
| H04 | JAVA_CONCURRENCY | HARD | thread_pool_design | 5 | [POOL: ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html) | SUPPORTED / 等待审核 |
| H05 | SPRING | HARD | transaction_boundary | 5 | [TX: Using @Transactional](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html) | SUPPORTED / 等待审核 |
| H08 | NETWORK | HARD | tcp_connection_lifecycle | 5 | [TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html) | SUPPORTED / 等待审核 |
| H09 | PROJECT_SCENARIO | HARD | concurrent_inventory_idempotency | 4 | [LOCK: Locking Reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html) | SUPPORTED / COMMANDER_APPROVED_HYPOTHETICAL_DESIGN |

## 2. 来源与版本的核查方法

全部 45 个采用的官方 URL 在本阶段通过浏览工具取得页面或源码正文，访问日期均为 **2026-10-06**。这是取得内容的证据，不虚构 HTTP 状态码，不保证链接永久稳定。部分响应来自浏览服务缓存；以后编写前应再次核对相关章节。Spring 的 6.2 参考页为可更新版本线；本项目 Spring Boot 3.5.16 的本地 dependency-management POM 第 191 行解析为 **Spring Framework 6.2.19**，已只读核对，不执行 Maven。依赖 POM 路径见 JSON 的 springVersionEvidence。

H02 实现来源固定为 **OpenJDK jdk8u402-b06**；M01 哈希定位示例固定为 **OpenJDK jdk-21+35**。两者实现细节不能无标记混用。H01 官方 JDK 8 教程仅辅助说明擦除/bridge，Java 21 限制由 JLS/JVMS 21 决定。MySQL 以 8.0 手册为行为范围，不引入 8.4/9.x 特性。Redis 采用访问日的经典类型与命令语义，排除新版本选项及未经证实的底层编码。

地址处理：G1 初选路径未取得正文，改用 Oracle Java 21 目录中的当前 G1 页面；ConcurrentHashMap 改用已取得正文的固定 8u 标签。Redis List 详情页超过浏览工具正文大小限制，因此没有将该页列为采用来源，改用已取得正文的官方概览与 LPUSH/RPOP 命令页。这不是用第三方替代，也不是 Redis List 本身的技术来源缺失。

## 3. 按题的来源清单与逐点评分矩阵

`SUPPORTED` 表示选定一手来源能支持该点的编写计划，包括明确标注的结构推论/工程指导，不代表本文已验证功能。`PROJECT_SPECIFIC` 用于确有项目对应实现的完整评分点，本次数量为 0；面试提交幂等虽然有本地证据，不能冒充库存行为。`SOURCE_GAP` 表示选定来源不能支持要求的事实或设计；当前 SOURCE_GAP_COUNT=0。Commander 已确认 H09 是假设设计题，项目没有库存实现不构成回答该题的来源缺口。

文档节编号统一 S1 Core model / S2 Main mechanism / S3 Important boundary / S4 Common misconceptions / S5 Official references；完整大纲见 [Document Design Plan](PHASE_4D1_DOCUMENT_DESIGN_PLAN.md)。完整可读机器矩阵见 [JSON](PHASE_4D1_SCORING_POINT_SOURCE_MATRIX.json)。

### E01 — Java 21 接口与抽象类：契约、状态与选择

- category：`JAVA_BASIC`；knowledgePoint：`interface_vs_abstract_class`；difficulty：`EASY`。
- **冻结题目原文：** Java 中接口和抽象类有什么区别？实际开发中应该如何选择？
- 题目版本边界：Frozen question has no exact release pin; this plan chooses the explicit product/specification baseline in versionScope。
- 文档选定版本：Java SE 21 language; JLS 8/9。
- 拟编写文件：`e01_java21_interface_vs_abstract_class.md`；本阶段不创建该知识正文。
- 主官方来源：[JLS9: JLS 21 Chapter 9 Interfaces](https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html)；章节：9.1.3 superinterfaces; 9.3 fields; 9.4 abstract/default/static/private methods。
- 次来源/实现来源：[JLS8: JLS 21 Chapter 8 Classes](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html)（Java SE 21；8.1.1.1 abstract; 8.1.2 generic classes; 8.1.4 superclass; 8.1.5 interfaces; 8.3 fields; 8.4 methods; 8.8 constructors）。
- 权威性：产品维护者或标准制定机构的一手资料。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持接口没有实现、接口拥有普通实例字段或统一的最佳设计。

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| E01 | E01-SP1 | CORE | 30 | 能说明接口主要用于定义行为契约或能力，抽象类更适合表达具有共同基础特征和共享实现的类层次。 | [JLS8: JLS 21 Chapter 8 Classes](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html); [JLS9: JLS 21 Chapter 9 Interfaces](https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html)；8.1.1.1; 8.3/8.4; 9.1/9.4 | S1,S3 | SUPPORTED；OFFICIAL_FACT_AND_ENGINEERING_GUIDANCE；设计取舍由语言能力推导，标明工程选择 |
| E01 | E01-SP2 | KEY | 25 | 能说明 Java 类只能直接继承一个类，但可以实现多个接口，因此接口更适合需要多种能力组合或多个不同实现的场景。 | [JLS8: JLS 21 Chapter 8 Classes](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html); [JLS9: JLS 21 Chapter 9 Interfaces](https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html)；8.1.4/8.1.5; 9.1.3 | S2 | SUPPORTED；OFFICIAL_FACT |
| E01 | E01-SP3 | KEY | 25 | 能说明抽象类可以保存实例状态、定义构造过程并同时包含抽象方法和具体实现；接口不能保存普通对象实例字段。 | [JLS8: JLS 21 Chapter 8 Classes](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html); [JLS9: JLS 21 Chapter 9 Interfaces](https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html)；8.3; 8.4; 8.8; 9.3 | S2,S3 | SUPPORTED；OFFICIAL_FACT |
| E01 | E01-SP4 | PRINCIPLE | 20 | 能根据实际设计场景做出合理选择：共享状态和公共基类实现时考虑抽象类，仅需要稳定契约、解耦或多实现时优先考虑接口，而不是机械地认为其中一种始终更好。 | [JLS8: JLS 21 Chapter 8 Classes](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html); [JLS9: JLS 21 Chapter 9 Interfaces](https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html)；8.3/8.4; 9.3/9.4 | S3,S4 | SUPPORTED；ENGINEERING_GUIDANCE_FROM_OFFICIAL_FACTS；稳定契约/状态共享是选择依据，不是规范强制的设计模式 |

### E02 — ArrayList 与 LinkedList：访问与修改成本

- category：`JAVA_COLLECTION`；knowledgePoint：`arraylist_vs_linkedlist`；difficulty：`EASY`。
- **冻结题目原文：** ArrayList 和 LinkedList 在底层结构、常见操作复杂度和实际使用场景上有什么区别？
- 题目版本边界：Frozen question has no exact release pin; this plan chooses the explicit product/specification baseline in versionScope。
- 文档选定版本：Java SE 21 API; implementation-level performance predictions labelled guidance。
- 拟编写文件：`e02_java21_arraylist_vs_linkedlist.md`；本阶段不创建该知识正文。
- 主官方来源：[ARRAY: ArrayList API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html)；章节：Class description; get; add; remove。
- 次来源/实现来源：[LINKED: LinkedList API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedList.html)（Java SE 21；Class description; indexed traversal; ListIterator; Deque operations）。
- 权威性：产品维护者或标准制定机构的一手资料。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持 LinkedList 总是更快或总是 O(1) 插入；不写固定 ArrayList 扩容倍数。

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| E02 | E02-SP1 | CORE | 30 | 能说明 ArrayList 基于动态数组，而 LinkedList 基于双向链表，两者底层存储结构不同。 | [ARRAY: ArrayList API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html); [LINKED: LinkedList API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedList.html)；Class descriptions | S1 | SUPPORTED；OFFICIAL_FACT |
| E02 | E02-SP2 | KEY | 25 | 能说明 ArrayList 根据下标随机访问通常为 O(1)，而 LinkedList 按下标访问通常需要遍历，为 O(n)。 | [ARRAY: ArrayList API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html); [LINKED: LinkedList API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedList.html)；Class descriptions; get(index); indexed traversal | S2 | SUPPORTED；OFFICIAL_FACT |
| E02 | E02-SP3 | KEY | 25 | 能说明 ArrayList 中间插入或删除通常需要移动元素；LinkedList 在已经定位到节点后修改链接本身开销较低，但定位节点仍可能需要 O(n)。 | [ARRAY: ArrayList API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html); [LINKED: LinkedList API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedList.html)；add/remove; indexed traversal; ListIterator | S2,S3 | SUPPORTED；OFFICIAL_FACT_AND_STRUCTURAL_INFERENCE；链表重连常数成本与定位成本分开；为 API 结构推论 |
| E02 | E02-SP4 | PRINCIPLE | 20 | 能结合实际访问模式选择集合，而不是机械地认为“增删多就用 LinkedList、查询多就用 ArrayList”。 | [ARRAY: ArrayList API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html); [LINKED: LinkedList API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedList.html)；Class descriptions; RandomAccess / Deque interfaces | S3,S4 | SUPPORTED；ENGINEERING_GUIDANCE_FROM_OFFICIAL_FACTS；根据操作分布选择并测量，而非 universal speed claim |

### E03 — Spring 6.2 IoC 与 DI：容器与依赖

- category：`SPRING`；knowledgePoint：`ioc_di`；difficulty：`EASY`。
- **冻结题目原文：** 什么是 IoC 和 DI？Spring 容器主要帮开发者解决了什么问题？
- 题目版本边界：Frozen question has no exact release pin; this plan chooses the explicit product/specification baseline in versionScope。
- 文档选定版本：Project Spring Framework 6.2.19; reference line 6.2。
- 拟编写文件：`e03_spring_6_2_ioc_di.md`；本阶段不创建该知识正文。
- 主官方来源：[IOC: Introduction to the Spring IoC Container](https://docs.spring.io/spring-framework/reference/6.2/core/beans/introduction.html)；章节：IoC / DI; BeanFactory and ApplicationContext。
- 次来源/实现来源：[DI: Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html)（Spring Framework 6.2; project resolved 6.2.19；Constructor-based and Setter-based DI; constructor vs setter; dependencies）；[LIFECYCLE: Customizing the Nature of a Bean](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html)（Spring Framework 6.2; project resolved 6.2.19；Lifecycle callbacks; initialization and destruction）。
- 权威性：产品维护者或标准制定机构的一手资料。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持所有对象都自动成为 bean、DI 必须 field injection。

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| E03 | E03-SP1 | CORE | 30 | 能说明 IoC 将对象创建、配置以及依赖关系管理的控制权交给 Spring 容器，而不是由业务对象自行创建和查找所有依赖。 | [IOC: Introduction to the Spring IoC Container](https://docs.spring.io/spring-framework/reference/6.2/core/beans/introduction.html); [DI: Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html)；Introduction; Dependency Injection introduction | S1 | SUPPORTED；OFFICIAL_FACT |
| E03 | E03-SP2 | KEY | 25 | 能说明 DI 是 IoC 的主要实现方式，对象声明所需依赖，由容器在创建 Bean 时通过构造器、Setter 等方式提供依赖。 | [DI: Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html)；Constructor-based; Setter-based DI | S2 | SUPPORTED；OFFICIAL_FACT |
| E03 | E03-SP3 | PRINCIPLE | 25 | 能说明 IoC/DI 可以降低业务类与具体依赖创建方式之间的耦合，使实现替换和单元测试更加容易。 | [DI: Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html)；Dependency Injection introduction / testability | S3 | SUPPORTED；OFFICIAL_FACT |
| E03 | E03-SP4 | KEY | 20 | 能说明 Spring 容器除了注入依赖之外，还负责 Bean 的实例化、配置、组装以及相关生命周期管理。 | [IOC: Introduction to the Spring IoC Container](https://docs.spring.io/spring-framework/reference/6.2/core/beans/introduction.html); [LIFECYCLE: Customizing the Nature of a Bean](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html)；BeanFactory/ApplicationContext; Lifecycle callbacks | S1,S3 | SUPPORTED；OFFICIAL_FACT |

### E04 — MySQL 8.0 InnoDB 事务与 ACID

- category：`MYSQL`；knowledgePoint：`transaction_acid`；difficulty：`EASY`。
- **冻结题目原文：** MySQL 事务的 ACID 分别指什么？为什么数据库需要事务？
- 题目版本边界：Frozen question has no exact release pin; this plan chooses the explicit product/specification baseline in versionScope。
- 文档选定版本：MySQL 8.0 InnoDB; durability configuration/hardware boundaries explicit。
- 拟编写文件：`e04_mysql_8_0_transaction_acid.md`；本阶段不创建该知识正文。
- 主官方来源：[ACID: InnoDB and the ACID Model](https://dev.mysql.com/doc/refman/8.0/en/mysql-acid.html)；章节：17.2 Atomicity; Consistency; Isolation; Durability。
- 次来源/实现来源：[COMMIT: START TRANSACTION, COMMIT, and ROLLBACK](https://dev.mysql.com/doc/refman/8.0/en/commit.html)（MySQL 8.0 / InnoDB；15.3.1 transaction boundaries; rollback and nontransactional-table caveats）；[ISO: Transaction Isolation Levels](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html)（MySQL 8.0 / InnoDB；17.7.2.1 READ UNCOMMITTED / READ COMMITTED / REPEATABLE READ / SERIALIZABLE）。
- 权威性：产品维护者或标准制定机构的一手资料。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持业务不变量凭事务自动正确或最高隔离意味着完全无异常。

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| E04 | E04-SP1 | CORE | 20 | 能说明原子性表示事务中的操作作为一个整体成功或整体回滚。 | [ACID: InnoDB and the ACID Model](https://dev.mysql.com/doc/refman/8.0/en/mysql-acid.html); [COMMIT: START TRANSACTION, COMMIT, and ROLLBACK](https://dev.mysql.com/doc/refman/8.0/en/commit.html)；17.2 Atomicity; 15.3.1 COMMIT/ROLLBACK | S1,S2 | SUPPORTED；OFFICIAL_FACT |
| E04 | E04-SP2 | KEY | 20 | 能说明一致性表示事务执行前后数据仍满足数据库约束和业务不变量。 | [ACID: InnoDB and the ACID Model](https://dev.mysql.com/doc/refman/8.0/en/mysql-acid.html); [COMMIT: START TRANSACTION, COMMIT, and ROLLBACK](https://dev.mysql.com/doc/refman/8.0/en/commit.html)；17.2 Consistency; 15.3.1 transaction boundary | S2 | SUPPORTED；OFFICIAL_FACT_AND_ENGINEERING_GUIDANCE；数据库原子性不替代业务校验；业务不变量是工程责任 |
| E04 | E04-SP3 | KEY | 20 | 能说明隔离性用于控制并发事务之间的相互影响，不同隔离级别提供不同程度的隔离保证。 | [ACID: InnoDB and the ACID Model](https://dev.mysql.com/doc/refman/8.0/en/mysql-acid.html); [ISO: Transaction Isolation Levels](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html)；17.2 Isolation; 17.7.2.1 isolation levels | S2,S3 | SUPPORTED；OFFICIAL_FACT |
| E04 | E04-SP4 | KEY | 20 | 能说明持久性表示事务成功提交后的结果应当被可靠保存。 | [ACID: InnoDB and the ACID Model](https://dev.mysql.com/doc/refman/8.0/en/mysql-acid.html)；17.2 Durability | S3 | SUPPORTED；OFFICIAL_FACT |
| E04 | E04-SP5 | PRINCIPLE | 20 | 能说明事务用于把多个相关数据库操作组成一个逻辑工作单元，避免出现部分成功导致的业务不一致。 | [ACID: InnoDB and the ACID Model](https://dev.mysql.com/doc/refman/8.0/en/mysql-acid.html); [COMMIT: START TRANSACTION, COMMIT, and ROLLBACK](https://dev.mysql.com/doc/refman/8.0/en/commit.html)；17.2; 15.3.1 | S1,S2 | SUPPORTED；OFFICIAL_FACT；事务性表中的逻辑单元；排除非事务性表/隐式提交误用 |

### E05 — TCP 与 UDP：语义与应用取舍

- category：`NETWORK`；knowledgePoint：`tcp_vs_udp`；difficulty：`EASY`。
- **冻结题目原文：** TCP 和 UDP 有什么主要区别？实际开发中分别适合哪些场景？
- 题目版本边界：Frozen question has no exact release pin; this plan chooses the explicit product/specification baseline in versionScope。
- 文档选定版本：RFC 9293 TCP / RFC 768 UDP; no QUIC guarantee inherited by UDP。
- 拟编写文件：`e05_rfc9293_rfc768_tcp_vs_udp.md`；本阶段不创建该知识正文。
- 主官方来源：[TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html)；章节：2.2 Key TCP Concepts; 3.3.2 state machine; 3.4.1 ISN; 3.5 establishment; 3.6 closing; 3.8 data communication; 3.8.2 congestion; 3.8.6 receive window; 3.10.7 segment processing。
- 次来源/实现来源：[UDP: RFC 768 User Datagram Protocol](https://www.rfc-editor.org/rfc/rfc768.html)（RFC 768 (1980) UDP base protocol；Introduction; Format; User Interface; IP Interface）。
- 权威性：IETF 标准，由 RFC Editor 发布；IETF 标准体系，由 RFC Editor 发布。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持 UDP 本身提供可靠流或 TCP 保留应用消息边界。

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| E05 | E05-SP1 | CORE | 30 | 能说明 TCP 是面向连接的可靠字节流协议，而 UDP 是面向数据报、无 TCP 式连接建立过程的传输协议。 | [TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html); [UDP: RFC 768 User Datagram Protocol](https://www.rfc-editor.org/rfc/rfc768.html)；RFC 9293 2.2 / 3.5; RFC 768 Introduction/Format | S1 | SUPPORTED；OFFICIAL_FACT |
| E05 | E05-SP2 | KEY | 25 | 能说明 TCP 提供顺序、确认、丢失重传等可靠传输机制，而 UDP 本身不保证数据一定到达或保持顺序。 | [TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html); [UDP: RFC 768 User Datagram Protocol](https://www.rfc-editor.org/rfc/rfc768.html)；RFC 9293 2.2 / 3.8.1; RFC 768 Introduction | S2 | SUPPORTED；OFFICIAL_FACT |
| E05 | E05-SP3 | KEY | 20 | 能说明 TCP 还具有流量控制、拥塞控制等机制，因此协议状态和开销通常高于 UDP。 | [TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html); [UDP: RFC 768 User Datagram Protocol](https://www.rfc-editor.org/rfc/rfc768.html)；RFC 9293 3.8.2 / 3.8.6; RFC 768 Format | S2,S3 | SUPPORTED；OFFICIAL_FACT_AND_ENGINEERING_GUIDANCE；开销比较由协议机制推导，不保证固定延迟优势 |
| E05 | E05-SP4 | PRINCIPLE | 25 | 能根据可靠性、时延、应用层或上层协议控制需求选择协议，并认识到 UDP 本身不提供 TCP 式可靠性保证。 | [TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html); [UDP: RFC 768 User Datagram Protocol](https://www.rfc-editor.org/rfc/rfc768.html)；Key TCP Concepts; UDP Introduction/User Interface | S3,S4 | SUPPORTED；ENGINEERING_GUIDANCE_FROM_OFFICIAL_FACTS；应用取舍为工程指导；不把 QUIC 保证归因于 UDP |

### M01 — equals 与 hashCode：契约与哈希集合

- category：`JAVA_BASIC`；knowledgePoint：`equals_hashcode_contract`；difficulty：`MEDIUM`。
- **冻结题目原文：** `equals()` 和 `hashCode()` 之间有什么约定？如果只重写 `equals()` 而不重写 `hashCode()`，在 HashMap / HashSet 中可能出现什么问题？
- 题目版本边界：Frozen question has no exact release pin; this plan chooses the explicit product/specification baseline in versionScope。
- 文档选定版本：Java SE 21 API contract; OpenJDK jdk-21+35 HashMap implementation illustration。
- 拟编写文件：`m01_java21_equals_hashcode_contract.md`；本阶段不创建该知识正文。
- 主官方来源：[OBJECT: Object API equals/hashCode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html)；章节：equals equivalence contract; hashCode consistency and equal objects。
- 次来源/实现来源：[HASHAPI: HashMap API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html)（Java SE 21；Class description; hash table; key operations）；[HASH21: OpenJDK HashMap.java](https://raw.githubusercontent.com/openjdk/jdk/jdk-21%2B35/src/java.base/share/classes/java/util/HashMap.java)（OpenJDK jdk-21+35 tagged implementation；hash; getNode; putVal; key hash then identity/equals）。
- 权威性：产品维护者或标准制定机构的一手资料；OpenJDK 官方仓库，固定发布标签；只用于 Java 21 实现示例。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持 hash 相同必相等、所有 Map 使用 hashCode、hash 恒定一定立即功能失败。

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| M01 | M01-SP1 | CORE | 25 | 能说明 equals 用于判断对象的逻辑相等关系，而 hashCode 提供供哈希数据结构使用的哈希值。 | [OBJECT: Object API equals/hashCode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html)；equals(Object) | S1 | SUPPORTED；OFFICIAL_FACT |
| M01 | M01-SP2 | KEY | 25 | 能明确说明 equals 相等的两个对象必须具有相同 hashCode，但 hashCode 相同的两个对象不一定 equals。 | [OBJECT: Object API equals/hashCode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html)；hashCode()/equals() | S2 | SUPPORTED；OFFICIAL_FACT |
| M01 | M01-SP3 | PRINCIPLE | 30 | 能解释 HashMap/HashSet 通常先根据 hash 缩小候选位置，再使用 equals 判断 key 是否真正相等，因此 equals 与 hashCode 契约必须保持一致。 | [OBJECT: Object API equals/hashCode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html); [HASHAPI: HashMap API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html); [HASH21: OpenJDK HashMap.java](https://raw.githubusercontent.com/openjdk/jdk/jdk-21%2B35/src/java.base/share/classes/java/util/HashMap.java)；hashCode; Class description; hash/getNode/putVal | S2 | SUPPORTED；OFFICIAL_FACT；实现示例只限 jdk-21+35；不是所有 Map 的统一查找顺序 |
| M01 | M01-SP4 | KEY | 20 | 能说明只重写 equals 而不正确重写 hashCode 可能导致逻辑相等对象无法按预期查找、去重或作为同一个 key 使用。 | [OBJECT: Object API equals/hashCode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html); [HASHAPI: HashMap API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html); [HASH21: OpenJDK HashMap.java](https://raw.githubusercontent.com/openjdk/jdk/jdk-21%2B35/src/java.base/share/classes/java/util/HashMap.java)；hashCode contract; key lookup and putVal | S3,S4 | SUPPORTED；OFFICIAL_FACT；以不同逻辑相等实例演示失效，不作每次必失败断言 |

### M03 — JVM SE 21 运行时数据区与 HotSpot 边界

- category：`JVM`；knowledgePoint：`runtime_data_areas`；difficulty：`MEDIUM`。
- **冻结题目原文：** JVM 运行时数据区主要有哪些？哪些区域是线程私有的，哪些是线程共享的？对象、局部变量、类元数据等通常分别位于哪里？
- 题目版本边界：Frozen question has no exact release pin; this plan chooses the explicit product/specification baseline in versionScope。
- 文档选定版本：JVMS SE 21 specification; Oracle JDK 21 HotSpot Metaspace separately。
- 拟编写文件：`m03_jvm21_runtime_data_areas.md`；本阶段不创建该知识正文。
- 主官方来源：[JVMS2: JVMS 21 Chapter 2 Structure](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html)；章节：2.5.1 PC; 2.5.2 JVM stacks; 2.5.3 heap; 2.5.4 method area; 2.5.6 native stacks; 2.6 frames。
- 次来源/实现来源：[META: Other Considerations: Class Metadata](https://docs.oracle.com/en/java/javase/21/gctuning/other-considerations.html)（Oracle JDK 21 / HotSpot；Class Metadata; native-memory metadata and Metaspace）。
- 权威性：产品维护者或标准制定机构的一手资料。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持所有对象必须物理分配于堆而忽略优化，或 Method Area=Metaspace。

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| M03 | M03-SP1 | CORE | 20 | 能说出 Heap、Method Area、Java Virtual Machine Stack 和 PC Register 这些主要运行时数据区域。 | [JVMS2: JVMS 21 Chapter 2 Structure](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html)；2.5.1–2.5.4 | S1 | SUPPORTED；OFFICIAL_FACT |
| M03 | M03-SP2 | KEY | 20 | 能区分线程私有与共享区域：PC Register、JVM Stack、Native Method Stack 按线程创建，而 Heap 和 Method Area 为 JVM 线程共享区域。 | [JVMS2: JVMS 21 Chapter 2 Structure](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html)；2.5.1–2.5.4; 2.5.6 | S1,S2 | SUPPORTED；OFFICIAL_FACT |
| M03 | M03-SP3 | KEY | 25 | 能说明普通对象通常位于 Heap，方法局部变量位于栈帧的局部变量表中，并区分引用变量本身与其指向的对象。 | [JVMS2: JVMS 21 Chapter 2 Structure](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html)；2.5.3; 2.6.1 local variables | S2 | SUPPORTED；OFFICIAL_FACT |
| M03 | M03-SP4 | PRINCIPLE | 20 | 能说明方法调用通过 JVM Stack 中的 Frame 执行，Frame 包含局部变量表、操作数栈等运行信息。 | [JVMS2: JVMS 21 Chapter 2 Structure](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html)；2.5.2; 2.6/2.6.1/2.6.2 | S2 | SUPPORTED；OFFICIAL_FACT |
| M03 | M03-SP5 | ADVANCED | 15 | 能区分 JVM Specification 中的 Method Area 与 HotSpot Metaspace 等具体 JVM 实现。 | [JVMS2: JVMS 21 Chapter 2 Structure](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html); [META: Other Considerations: Class Metadata](https://docs.oracle.com/en/java/javase/21/gctuning/other-considerations.html)；2.5.4 Method Area; Class Metadata | S3 | SUPPORTED；OFFICIAL_FACT |

### M06 — InnoDB 索引：页、聚簇与覆盖

- category：`MYSQL`；knowledgePoint：`innodb_index_structure`；difficulty：`MEDIUM`。
- **冻结题目原文：** InnoDB 为什么使用 B+Tree 作为主要索引结构？聚簇索引、二级索引、回表和覆盖索引之间是什么关系？
- 题目版本边界：Frozen question has no exact release pin; this plan chooses the explicit product/specification baseline in versionScope。
- 文档选定版本：MySQL 8.0 InnoDB; manual B-tree/B+Tree-family wording, no invented page layout。
- 拟编写文件：`m06_mysql_8_0_innodb_index_structure.md`；本阶段不创建该知识正文。
- 主官方来源：[INDEX: Clustered and Secondary Indexes](https://dev.mysql.com/doc/refman/8.0/en/innodb-index-types.html)；章节：17.6.2.1 clustered index row data; primary/fallback key; secondary key and primary key。
- 次来源/实现来源：[PAGE: Physical Structure of an InnoDB Index](https://dev.mysql.com/doc/refman/8.0/en/innodb-physical-structure.html)（MySQL 8.0 / InnoDB；17.6.2.2 B-tree; leaf pages; default 16 KB configurable page）；[GLOSSARY: MySQL Glossary](https://dev.mysql.com/doc/refman/8.0/en/glossary.html)（MySQL 8.0；B-tree entry; clustered index / secondary index terminology）；[BTREE: Comparison of B-Tree and Hash Indexes](https://dev.mysql.com/doc/refman/8.0/en/index-btree-hash.html)（MySQL 8.0；10.3.9 equality/range vs hash equality restrictions）；[COVER: How MySQL Uses Indexes](https://dev.mysql.com/doc/refman/8.0/en/mysql-indexes.html)（MySQL 8.0；10.3.1 retrieval from index without consulting data rows）。
- 权威性：产品维护者或标准制定机构的一手资料。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持默认页大小永远不可改、所有二级索引都必须回表、空间索引也是普通 B-tree。

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| M06 | M06-SP1 | CORE | 20 | 能说明 InnoDB 索引采用多路、有序的 B-tree/B+Tree 类结构，相比普通二叉搜索树具有更高扇出和更低树高，适合以页为单位的存储访问。 | [PAGE: Physical Structure of an InnoDB Index](https://dev.mysql.com/doc/refman/8.0/en/innodb-physical-structure.html); [GLOSSARY: MySQL Glossary](https://dev.mysql.com/doc/refman/8.0/en/glossary.html)；17.6.2.2; Glossary B-tree | S1 | SUPPORTED；OFFICIAL_FACT_AND_STRUCTURAL_INFERENCE；官方多子节点及页模型支持更高扇出；更低高度为同规模平衡树结构推导，不承诺固定 I/O |
| M06 | M06-SP2 | PRINCIPLE | 20 | 能说明有序索引结构不仅支持等值定位，也适合范围扫描，因此比普通 Hash 索引更适合作为通用数据库索引。 | [BTREE: Comparison of B-Tree and Hash Indexes](https://dev.mysql.com/doc/refman/8.0/en/index-btree-hash.html); [GLOSSARY: MySQL Glossary](https://dev.mysql.com/doc/refman/8.0/en/glossary.html)；10.3.9 B-Tree/Hash; B-tree entry | S3 | SUPPORTED；OFFICIAL_FACT |
| M06 | M06-SP3 | KEY | 25 | 能说明 InnoDB 聚簇索引的叶子记录包含完整行数据，并且通常以主键作为聚簇索引。 | [INDEX: Clustered and Secondary Indexes](https://dev.mysql.com/doc/refman/8.0/en/innodb-index-types.html)；17.6.2.1 clustered-index selection and data storage | S2 | SUPPORTED；OFFICIAL_FACT |
| M06 | M06-SP4 | KEY | 20 | 能说明 InnoDB 二级索引记录包含二级索引列和主键值，需要额外列时可以根据主键再次访问聚簇索引，这一过程通常称为回表。 | [INDEX: Clustered and Secondary Indexes](https://dev.mysql.com/doc/refman/8.0/en/innodb-index-types.html)；17.6.2.1 secondary indexes | S2 | SUPPORTED；OFFICIAL_FACT |
| M06 | M06-SP5 | KEY | 15 | 能说明覆盖索引表示一次查询所需数据已经可以由索引提供，从而能够避免不必要的额外回表访问。 | [COVER: How MySQL Uses Indexes](https://dev.mysql.com/doc/refman/8.0/en/mysql-indexes.html); [GLOSSARY: MySQL Glossary](https://dev.mysql.com/doc/refman/8.0/en/glossary.html)；10.3.1 index-only retrieval; covering index entry | S3 | SUPPORTED；OFFICIAL_FACT；描述可以避免不必要回表；不把所有 MVCC 场景都写成永不回表 |

### M07 — Redis 五类常用数据结构与选择

- category：`REDIS`；knowledgePoint：`redis_data_structures`；difficulty：`MEDIUM`。
- **冻结题目原文：** Redis 常用数据结构有哪些？如果要分别实现对象缓存、元素去重、按顺序保存数据和排行榜，你会选择什么数据结构？为什么？
- 题目版本边界：Frozen question has no exact release pin; this plan chooses the explicit product/specification baseline in versionScope。
- 文档选定版本：Redis official command/data-type semantics accessed 2026-10-06; classic commands only; no deployed Redis version claimed。
- 拟编写文件：`m07_redis_classic_data_structures.md`；本阶段不创建该知识正文。
- 主官方来源：[REDISTYPES: Redis data types](https://redis.io/docs/latest/develop/data-types/)；章节：Strings / Hashes / Lists / Sets / Sorted Sets overview。
- 次来源/实现来源：[STRING: Redis Strings](https://redis.io/docs/latest/develop/data-types/strings/)（Redis documented classic String semantics; no new Redis 8.x options；Introduction; Strings as counters）；[HASH: Redis Hashes](https://redis.io/docs/latest/develop/data-types/hashes/)（Redis documented classic Hash semantics；Introduction; field-value operations）；[LPUSH: Redis LPUSH](https://redis.io/docs/latest/commands/lpush/)（Core list command since Redis 1.0.0; single-element form；Description; head insertion; complexity）；[RPOP: Redis RPOP](https://redis.io/docs/latest/commands/rpop/)（Core list command since Redis 1.0.0; single-element form; exclude newer count option；Description; tail removal）；[SET: Redis Sets](https://redis.io/docs/latest/develop/data-types/sets/)（Redis documented classic Set semantics；Unique members; membership; intersections/unions/differences）；[ZSET: Redis Sorted Sets](https://redis.io/docs/latest/develop/data-types/sorted-sets/)（Redis documented classic Sorted Set semantics；Unique members; scores; ranking/range use cases）。
- 权威性：产品维护者或标准制定机构的一手资料。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持项目已部署 Redis、五类就是全部 Redis 类型、List 自动保证任务不丢。

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| M07 | M07-SP1 | CORE | 20 | 能识别 Redis 常见 String、Hash、List、Set、Sorted Set 五类通用数据结构，并知道 Redis 不只是简单的字符串 Key-Value 存储。 | [REDISTYPES: Redis data types](https://redis.io/docs/latest/develop/data-types/)；Data types overview | S1 | SUPPORTED；OFFICIAL_FACT |
| M07 | M07-SP2 | KEY | 20 | 能说明 String 适合普通缓存值、计数器等单值场景，Hash 适合保存和操作对象的多个字段。 | [STRING: Redis Strings](https://redis.io/docs/latest/develop/data-types/strings/); [HASH: Redis Hashes](https://redis.io/docs/latest/develop/data-types/hashes/)；Strings as counters; Hash introduction/fields | S2 | SUPPORTED；OFFICIAL_FACT |
| M07 | M07-SP3 | KEY | 20 | 能说明 List 是有序序列，适合需要按顺序从两端操作元素的列表或简单队列类场景。 | [REDISTYPES: Redis data types](https://redis.io/docs/latest/develop/data-types/); [LPUSH: Redis LPUSH](https://redis.io/docs/latest/commands/lpush/); [RPOP: Redis RPOP](https://redis.io/docs/latest/commands/rpop/)；Lists overview; LPUSH head insertion; RPOP tail removal | S2 | SUPPORTED；OFFICIAL_FACT；List 详情页过大未采用；命令页已取得正文 |
| M07 | M07-SP4 | KEY | 20 | 能说明 Set 强调成员唯一性，适合去重、成员判断以及集合交并差等需求。 | [SET: Redis Sets](https://redis.io/docs/latest/develop/data-types/sets/)；Introduction; membership; SINTER/SUNION/SDIFF | S3 | SUPPORTED；OFFICIAL_FACT |
| M07 | M07-SP5 | KEY | 20 | 能说明 Sorted Set 同时具有唯一成员和 score 排序能力，适合排行榜、按权重或时间排序等业务。 | [ZSET: Redis Sorted Sets](https://redis.io/docs/latest/develop/data-types/sorted-sets/)；Introduction; member uniqueness and score ordering | S3 | SUPPORTED；OFFICIAL_FACT |

### H01 — Java 泛型擦除、桥接与运行时边界

- category：`JAVA_BASIC`；knowledgePoint：`generic_type_erasure`；difficulty：`HARD`。
- **冻结题目原文：** Java 的泛型擦除是什么？它会带来哪些限制？为什么很多泛型类型信息在运行时不存在？
- 题目版本边界：Frozen question has no exact release pin; this plan chooses the explicit product/specification baseline in versionScope。
- 文档选定版本：JLS/JVMS SE 21 normative; Oracle JDK 8 tutorial illustrations explicitly secondary。
- 拟编写文件：`h01_java21_generic_type_erasure.md`；本阶段不创建该知识正文。
- 主官方来源：[JLS4: JLS 21 Types, Values, and Variables](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html)；章节：4.4 bounds; 4.5 parameterized types; 4.6 erasure; 4.7 reifiable types。
- 次来源/实现来源：[ERASURE: Oracle Tutorial Type Erasure](https://docs.oracle.com/javase/tutorial/java/generics/erasure.html)（Official JDK 8 tutorial; mechanism illustration only, checked against JLS/JVMS 21；Replacing type parameters; inserted casts; generated bridge methods）；[JLS8: JLS 21 Chapter 8 Classes](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html)（Java SE 21；8.1.1.1 abstract; 8.1.2 generic classes; 8.1.4 superclass; 8.1.5 interfaces; 8.3 fields; 8.4 methods; 8.8 constructors）；[BRIDGE: Oracle Tutorial Effects of Erasure and Bridge Methods](https://docs.oracle.com/javase/tutorial/java/generics/bridgeMethods.html)（Official JDK 8 tutorial; mechanism illustration only, not Java 21 restrictions authority；MyNode example; bridge method and cast）；[JVMS4: JVMS 21 class File Format](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html)（JVM Specification SE 21；4.6 ACC_BRIDGE; 4.7.9 Signature attribute and generic metadata）；[JLS15: JLS 21 Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html)（Java SE 21；15.9 class instance creation; 15.10.1 array creation; 15.20.2 instanceof）。
- 权威性：产品维护者或标准制定机构的一手资料。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持 Java 21 所有参数化 instanceof 都非法、所有泛型信息完全消失。
- 版本复核提醒：Java 16+ allows some parameterized instanceof when statically checked; use new T(), generic-array creation, or same-erasure overload as the at-least-two limitations. Preserve frozen text exactly, but do not author the overly broad old-language shorthand.

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| H01 | H01-SP1 | CORE | 25 | 能说明 Java 泛型主要提供编译期类型安全，编译后类型参数按照擦除规则被替换为其 bound 或 Object。 | [JLS4: JLS 21 Types, Values, and Variables](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html); [ERASURE: Oracle Tutorial Type Erasure](https://docs.oracle.com/javase/tutorial/java/generics/erasure.html)；4.4/4.6; Type Erasure | S1 | SUPPORTED；OFFICIAL_FACT |
| H01 | H01-SP2 | PRINCIPLE | 20 | 能说明不同参数化类型通常不会生成各自独立的运行时 Class，例如 List<String> 与 List<Integer> 共享同一个原始类实现。 | [JLS8: JLS 21 Chapter 8 Classes](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html); [JLS4: JLS 21 Types, Values, and Variables](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html)；8.1.2 generic classes; 4.6 | S1 | SUPPORTED；OFFICIAL_FACT |
| H01 | H01-SP3 | INTERNAL | 20 | 能说明编译器为了保持类型安全和多态语义可能插入类型转换以及生成 bridge method。 | [ERASURE: Oracle Tutorial Type Erasure](https://docs.oracle.com/javase/tutorial/java/generics/erasure.html); [BRIDGE: Oracle Tutorial Effects of Erasure and Bridge Methods](https://docs.oracle.com/javase/tutorial/java/generics/bridgeMethods.html); [JVMS4: JVMS 21 class File Format](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html)；Type Erasure; bridge example; 4.6 ACC_BRIDGE | S2 | SUPPORTED；OFFICIAL_FACT；旧官方教程只支持稳定机制，不作为 Java 21 instanceof 限制的依据 |
| H01 | H01-SP4 | KEY | 25 | 能正确举出至少两项类型擦除带来的典型限制，例如不能直接 new T()、不能 instanceof List<String>、不能直接创建泛型数组，或不能仅靠擦除后相同的签名进行重载。 | [JLS15: JLS 21 Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html); [JLS8: JLS 21 Chapter 8 Classes](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html); [JLS4: JLS 21 Types, Values, and Variables](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html)；15.9; 15.10.1; 8.4.8.3 erasure/name clash; 4.7; 15.20.2 | S3 | SUPPORTED；OFFICIAL_FACT；优先选择 new T 与泛型数组/同擦除签名；不一概禁止参数化 instanceof |
| H01 | H01-SP5 | ADVANCED | 10 | 能指出“泛型信息全部在运行时消失”并不严谨，class 声明的 generic signature 等元数据仍可能通过反射获得。 | [JVMS4: JVMS 21 class File Format](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html)；4.7.9 Signature attribute | S3,S4 | SUPPORTED；OFFICIAL_FACT |

### H02 — JDK 8 ConcurrentHashMap：分桶协调与扩容

- category：`JAVA_COLLECTION`；knowledgePoint：`concurrent_hashmap`；difficulty：`HARD`。
- **冻结题目原文：** JDK 8 的 ConcurrentHashMap 如何实现并发安全？CAS、`synchronized` 和桶结构分别起什么作用？
- 题目版本边界：Frozen question explicitly JDK 8。
- 文档选定版本：OpenJDK jdk8u402-b06; frozen question explicitly JDK 8。
- 拟编写文件：`h02_openjdk8u_concurrent_hashmap.md`；本阶段不创建该知识正文。
- 主官方来源：[CHM8: OpenJDK 8u ConcurrentHashMap.java](https://raw.githubusercontent.com/openjdk/jdk8u/jdk8u402-b06/jdk/src/share/classes/java/util/concurrent/ConcurrentHashMap.java)；章节：Node/TreeBin/table; putVal; tabAt/casTabAt; get; addCount/helpTransfer/transfer。
- 次来源/实现来源：不需要，主来源覆盖本题。
- 权威性：OpenJDK 官方仓库，固定 8u 标签，与题目 JDK 8 范围一致。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持 Java 21 私有实现与 8u 完全相同、Segment 为主体、全部写无锁。

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| H02 | H02-SP1 | CORE | 20 | 能说明 JDK 8 ConcurrentHashMap 不再以 Segment 作为主要分段锁结构，而使用 table 与桶内链表/树结构组织数据。 | [CHM8: OpenJDK 8u ConcurrentHashMap.java](https://raw.githubusercontent.com/openjdk/jdk8u/jdk8u402-b06/jdk/src/share/classes/java/util/concurrent/ConcurrentHashMap.java)；Node; TreeBin; table; implementation design notes | S1 | SUPPORTED；OFFICIAL_FACT |
| H02 | H02-SP2 | INTERNAL | 25 | 能说明目标桶为空时，写入线程可以通过 CAS 将新节点安装到桶位置，从而避免无必要的互斥加锁。 | [CHM8: OpenJDK 8u ConcurrentHashMap.java](https://raw.githubusercontent.com/openjdk/jdk8u/jdk8u402-b06/jdk/src/share/classes/java/util/concurrent/ConcurrentHashMap.java)；putVal empty-bin branch; casTabAt | S2 | SUPPORTED；OFFICIAL_FACT |
| H02 | H02-SP3 | INTERNAL | 30 | 能说明桶已存在并需要修改桶内链表或树时，会对当前桶头节点进行 synchronized，同一时间主要限制对该桶结构的并发修改，而不是锁住整个 Map。 | [CHM8: OpenJDK 8u ConcurrentHashMap.java](https://raw.githubusercontent.com/openjdk/jdk8u/jdk8u402-b06/jdk/src/share/classes/java/util/concurrent/ConcurrentHashMap.java)；putVal synchronized(f) and tabAt recheck | S2 | SUPPORTED；OFFICIAL_FACT |
| H02 | H02-SP4 | PRINCIPLE | 15 | 能说明普通读取通常不需要和写入采用相同的互斥锁流程，而依靠并发结构的内存可见性设计安全读取。 | [CHM8: OpenJDK 8u ConcurrentHashMap.java](https://raw.githubusercontent.com/openjdk/jdk8u/jdk8u402-b06/jdk/src/share/classes/java/util/concurrent/ConcurrentHashMap.java)；get; Node volatile val/next; tabAt volatile read; class happens-before contract | S3 | SUPPORTED；OFFICIAL_FACT |
| H02 | H02-SP5 | ADVANCED | 10 | 能说明扩容时多个线程可以协作搬迁桶中的数据，例如线程遇到迁移状态后继续参与迁移。能够正确说明协作扩容行为即覆盖，不要求背出具体内部类名或方法名。 | [CHM8: OpenJDK 8u ConcurrentHashMap.java](https://raw.githubusercontent.com/openjdk/jdk8u/jdk8u402-b06/jdk/src/share/classes/java/util/concurrent/ConcurrentHashMap.java)；addCount; helpTransfer; transfer | S3 | SUPPORTED；OFFICIAL_FACT |

### H03 — HotSpot Java 21 G1：Region 与暂停目标

- category：`JVM`；knowledgePoint：`g1_gc`；difficulty：`HARD`。
- **冻结题目原文：** G1 垃圾收集器的核心设计思路是什么？Region、Young GC 和 Mixed GC 如何帮助它控制停顿时间？
- 题目版本边界：Frozen question has no exact release pin; this plan chooses the explicit product/specification baseline in versionScope。
- 文档选定版本：Oracle JDK 21 HotSpot G1; not all JVM collector implementations。
- 拟编写文件：`h03_hotspot21_g1_gc.md`；本阶段不创建该知识正文。
- 主官方来源：[G1: Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html)；章节：Basic Concepts; Heap Layout; Garbage Collection Cycle; Remembered Set; Collection Set; Space-Reclamation Phase Generation Sizing。
- 次来源/实现来源：不需要，主来源覆盖本题。
- 权威性：产品维护者或标准制定机构的一手资料。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持 G1 没有 STW、暂停目标绝对保证、所有 JVM 都用相同 Region 布局。

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| H03 | H03-SP1 | CORE | 25 | 能说明 G1 将堆划分为大量 Region，并根据需要让 Region 承担 Eden、Survivor、Old 等角色，而不是只依赖固定连续代空间。 | [G1: Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html)；Heap Layout | S1 | SUPPORTED；OFFICIAL_FACT |
| H03 | H03-SP2 | PRINCIPLE | 25 | 能说明 G1 根据回收收益和预计处理成本选择 Collection Set，其重要目标是尽量满足可配置的暂停时间目标。 | [G1: Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html)；Basic Concepts; Collection Set; Space-Reclamation Phase Generation Sizing | S2 | SUPPORTED；OFFICIAL_FACT |
| H03 | H03-SP3 | KEY | 15 | 能说明 Young GC 主要处理年轻代 Region，并通过对象复制完成存活对象晋升或转移。 | [G1: Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html)；Garbage Collection Cycle; evacuation | S2 | SUPPORTED；OFFICIAL_FACT |
| H03 | H03-SP4 | INTERNAL | 20 | 能说明 G1 会进行并发标记来分析老年代 Region 的存活情况，为后续选择需要回收的 Old Region 提供依据。 | [G1: Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html)；Young-Only Phase; Concurrent Mark/Remark/Cleanup | S3 | SUPPORTED；OFFICIAL_FACT |
| H03 | H03-SP5 | ADVANCED | 15 | 能说明 Mixed GC 会同时处理 Young Region 和部分 Old Region，并认识到 G1 仍存在 Stop-The-World 阶段，并非完全无停顿 GC。 | [G1: Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html)；Space-Reclamation Phase / Mixed; Basic Concepts | S3,S4 | SUPPORTED；OFFICIAL_FACT |

### H04 — ThreadPoolExecutor：容量、拒绝与任务责任

- category：`JAVA_CONCURRENCY`；knowledgePoint：`thread_pool_design`；difficulty：`HARD`。
- **冻结题目原文：** 如果一个后端服务会突然收到大量任务，而且任务不能轻易丢失，你会如何设计 ThreadPoolExecutor 的核心线程数、最大线程数、队列和拒绝策略？为什么？
- 题目版本边界：Frozen question has no exact release pin; this plan chooses the explicit product/specification baseline in versionScope。
- 文档选定版本：Java SE 21 API; sizing/measurement/retry guidance clearly nonnormative。
- 拟编写文件：`h04_java21_thread_pool_design.md`；本阶段不创建该知识正文。
- 主官方来源：[POOL: ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)；章节：Core and maximum pool sizes; Queuing; Rejected tasks; hooks; statistics。
- 次来源/实现来源：不需要，主来源覆盖本题。
- 权威性：产品维护者或标准制定机构的一手资料。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持 volatile 保证队列正确性、CallerRuns 永不丢任务、内存队列自动持久可靠。

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| H04 | H04-SP1 | CORE | 25 | 能正确说明 ThreadPoolExecutor 的主要提交路径：core worker、workQueue、maximumPoolSize、RejectedExecutionHandler 之间的先后关系。 | [POOL: ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)；Core and maximum pool sizes; Queuing; Rejected tasks | S2 | SUPPORTED；OFFICIAL_FACT |
| H04 | H04-SP2 | PRINCIPLE | 20 | 能根据 CPU/I/O 特征、机器资源和下游系统承载能力设计线程数量，而不是使用固定万能线程数公式。 | [POOL: ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)；Core and maximum pool sizes; Queuing; Bounded queues | S3 | SUPPORTED；ENGINEERING_GUIDANCE_FROM_OFFICIAL_FACTS；CPU/I/O、下游和压测是推理式容量指导，不是 Java 规定公式 |
| H04 | H04-SP3 | KEY | 20 | 能意识到队列必须考虑容量和排队延迟，避免使用无界队列把突发流量无限转化为内存和延迟压力。 | [POOL: ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)；Unbounded queues; Bounded queues | S3 | SUPPORTED；OFFICIAL_FACT |
| H04 | H04-SP4 | KEY | 20 | 在任务不能丢失的约束下，能够采用反压、持久化、明确失败后幂等重试等方案，而不是使用 Discard/DiscardOldest 后静默丢弃业务任务。 | [POOL: ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)；Rejected tasks; AbortPolicy; CallerRunsPolicy; Discard/DiscardOldest | S3,S4 | SUPPORTED；OFFICIAL_FACT_AND_ENGINEERING_GUIDANCE；选明确失败/CallerRuns 路径满足可选方案；幂等重试为业务设计，持久化需另证，不承诺不丢 |
| H04 | H04-SP5 | ADVANCED | 15 | 能设计线程池监控和压测反馈机制，根据 active threads、queue depth、reject rate、task latency 等指标持续调整参数。 | [POOL: ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)；getActiveCount/getQueue/getTaskCount; beforeExecute/afterExecute | S4 | SUPPORTED；ENGINEERING_GUIDANCE_FROM_OFFICIAL_FACTS；拒绝计数/时延由自定义 handler/hooks 采集；API 无现成 reject-rate 指标 |

### H05 — Spring 6.2 事务边界与回滚规则

- category：`SPRING`；knowledgePoint：`transaction_boundary`；difficulty：`HARD`。
- **冻结题目原文：** Spring 事务有哪些常见失效场景？如果一个业务方法同时调用多个数据库操作，应该如何设计事务边界和异常回滚策略？
- 题目版本边界：Frozen question has no exact release pin; this plan chooses the explicit product/specification baseline in versionScope。
- 文档选定版本：Spring Framework 6.2.19 resolved; reference 6.2; ordinary defaults unless explicit rollbackOn override。
- 拟编写文件：`h05_spring_6_2_transaction_boundary.md`；本阶段不创建该知识正文。
- 主官方来源：[TX: Using @Transactional](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html)；章节：Proxy mode/self-invocation; settings; rollback rules; multiple managers; 6.2 rollbackOn override。
- 次来源/实现来源：[ROLLBACK: Rolling Back a Declarative Transaction](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/rolling-back.html)（Spring Framework 6.2; project resolved 6.2.19；RuntimeException/Error defaults; checked exceptions; rollbackFor; programmatic rollback）；[TXRESOURCE: Understanding the Spring Framework Transaction Abstraction](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/strategies.html)（Spring Framework 6.2; project resolved 6.2.19；PlatformTransactionManager; local JDBC/DataSource vs global JTA）；[COMMIT: START TRANSACTION, COMMIT, and ROLLBACK](https://dev.mysql.com/doc/refman/8.0/en/commit.html)（MySQL 8.0 / InnoDB；15.3.1 transaction boundaries; rollback and nontransactional-table caveats）。
- 权威性：产品维护者或标准制定机构的一手资料。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持所有 @Transactional 调用均生效、本地事务回滚远程结果、checked 永远不回滚。

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| H05 | H05-SP1 | CORE | 20 | 能说明 Spring 声明式事务主要建立在代理拦截之上，并识别 self-invocation 等绕过代理导致 @Transactional 不生效的情况。 | [TX: Using @Transactional](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html)；proxy mode; self-invocation | S2 | SUPPORTED；OFFICIAL_FACT |
| H05 | H05-SP2 | KEY | 20 | 能说明 Spring 默认 rollback 规则与异常类型有关，RuntimeException/Error 默认触发回滚，而 checked exception 默认不触发回滚；业务需要时可配置 rollbackFor 等规则。 | [TX: Using @Transactional](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html); [ROLLBACK: Rolling Back a Declarative Transaction](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/rolling-back.html)；transactional settings; rollback rules; 6.2 rollbackOn | S2,S3 | SUPPORTED；OFFICIAL_FACT |
| H05 | H05-SP3 | KEY | 20 | 能认识到 catch 后吞掉异常、错误的事务管理器或错误的调用边界都可能使实际事务行为与开发者预期不同。 | [TX: Using @Transactional](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html); [ROLLBACK: Rolling Back a Declarative Transaction](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/rolling-back.html); [TXRESOURCE: Understanding the Spring Framework Transaction Abstraction](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/strategies.html)；manager qualifier; exception-driven rollback; manager resource scope | S3 | SUPPORTED；OFFICIAL_FACT；被吞异常不必然立即破坏所有事务；可能已 rollback-only，应明确上下文 |
| H05 | H05-SP4 | PRINCIPLE | 25 | 能按照完整业务用例定义事务边界，使必须共同成功或失败的数据库操作位于同一事务，并避免不必要地扩大事务范围。 | [TXRESOURCE: Understanding the Spring Framework Transaction Abstraction](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/strategies.html); [COMMIT: START TRANSACTION, COMMIT, and ROLLBACK](https://dev.mysql.com/doc/refman/8.0/en/commit.html)；PlatformTransactionManager / JDBC; 15.3.1 unit of work | S1,S4 | SUPPORTED；ENGINEERING_GUIDANCE_FROM_OFFICIAL_FACTS；围绕用例及缩短边界为工程指导 |
| H05 | H05-SP5 | ADVANCED | 15 | 能意识到长时间远程调用、跨资源操作不能简单依赖单数据库本地事务解决，并能提出缩短事务或采用消息、补偿等一致性方案。 | [TXRESOURCE: Understanding the Spring Framework Transaction Abstraction](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/strategies.html)；Local JDBC/DataSource vs global JTA resource scope | S4 | SUPPORTED；ENGINEERING_GUIDANCE_FROM_OFFICIAL_FACTS；短事务为建议；消息/补偿只是需另设计验证的替代，不声称项目有分布式事务 |

### H08 — TCP 连接生命周期与状态诊断

- category：`NETWORK`；knowledgePoint：`tcp_connection_lifecycle`；difficulty：`HARD`。
- **冻结题目原文：** TCP 为什么需要三次握手和四次挥手？TIME_WAIT 和 CLOSE_WAIT 分别为什么出现，数量异常时通常意味着什么？
- 题目版本边界：Frozen question has no exact release pin; this plan chooses the explicit product/specification baseline in versionScope。
- 文档选定版本：RFC 9293 TCP; ordinary active/passive close, simultaneous close noted。
- 拟编写文件：`h08_rfc9293_tcp_connection_lifecycle.md`；本阶段不创建该知识正文。
- 主官方来源：[TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html)；章节：2.2 Key TCP Concepts; 3.3.2 state machine; 3.4.1 ISN; 3.5 establishment; 3.6 closing; 3.8 data communication; 3.8.2 congestion; 3.8.6 receive window; 3.10.7 segment processing。
- 次来源/实现来源：不需要，主来源覆盖本题。
- 权威性：IETF 标准，由 RFC Editor 发布。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持挥手必须永远四包、TIME-WAIT 必为 bug、CLOSE-WAIT=应用已关闭。

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| H08 | H08-SP1 | CORE | 20 | 能说明三次握手使通信双方交换并确认彼此的初始序列号与连接状态，SYN 与 ACK 可以组合，因此形成三报文建立过程。 | [TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html)；3.4.1 ISN; 3.5 Establishing a Connection Fig 6 | S1,S2 | SUPPORTED；OFFICIAL_FACT |
| H08 | H08-SP2 | PRINCIPLE | 20 | 能说明三次握手的重要目的之一是避免旧的重复连接请求造成双方状态混乱，而不是只回答“因为 TCP 规定三次”。 | [TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html)；3.5 old duplicate SYN Fig 8; 3.4.1 | S1 | SUPPORTED；OFFICIAL_FACT |
| H08 | H08-SP3 | KEY | 20 | 能说明 TCP 是全双工连接，两个发送方向可以独立关闭，因此双方通常分别发送 FIN 并确认，形成常见的四次挥手过程。 | [TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html)；3.6 Closing a Connection; 3.6.1 half-closed; Fig 12/13 | S2,S3 | SUPPORTED；OFFICIAL_FACT |
| H08 | H08-SP4 | INTERNAL | 20 | 能说明 TIME_WAIT 通常位于主动关闭端，用于处理最后 ACK 可靠性和旧连接延迟报文，并理解它不是天然异常状态。 | [TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html)；3.6 2MSL; 3.10.7 FIN/TIME-WAIT; 3.10.8 timeouts | S3 | SUPPORTED；OFFICIAL_FACT；通常主动关闭；同时关闭等例外明示，不仅凭状态数量判断 |
| H08 | H08-SP5 | ADVANCED | 20 | 能说明 CLOSE_WAIT 表示本地已收到远端 FIN 但本地应用尚未完成关闭，并能根据大量 CLOSE_WAIT/TIME_WAIT 的不同成因进行故障判断。 | [TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html)；3.3.2 states; 3.6; 3.10.4 CLOSE; 3.10.7 FIN processing | S4 | SUPPORTED；ENGINEERING_GUIDANCE_FROM_OFFICIAL_FACTS；长期 CLOSE-WAIT 可推应用关闭迟滞；数量/短连接归因是工程诊断待验证 |

### H09 — MySQL 库存与幂等：假设场景与项目事实边界

- category：`PROJECT_SCENARIO`；knowledgePoint：`concurrent_inventory_idempotency`；difficulty：`HARD`。
- **冻结题目原文：** 假设库存最终以 MySQL 为事实来源，当前库存只剩 1 个，同时来了很多个扣减请求，而且客户端还可能因为超时重复请求。你会如何设计，保证不超卖、不重复扣减，并能够正确处理失败和重试？
- 题目版本边界：Frozen question is a hypothetical MySQL source-of-truth design scenario; current inventory implementation is not required to answer it; local project evidence is analogy only。
- 文档选定版本：MySQL 8.0 official semantics + repository HEAD 6076a70f18f3062b8faff4fc179cf21f00a95036 / current file hashes; NO inventory module。
- 拟编写文件：`h09_mysql_8_0_inventory_idempotency_scenario.md`；本阶段不创建该知识正文。
- 主官方来源：[LOCK: Locking Reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html)；章节：17.7.2.4 FOR UPDATE; transaction/autocommit requirement; counter example。
- 次来源/实现来源：[UPDATE: UPDATE Statement](https://dev.mysql.com/doc/refman/8.0/en/update.html)（MySQL 8.0 / InnoDB；15.2.17 WHERE and SET; affected/changed rows）；[UNIQUE: CREATE TABLE Statement](https://dev.mysql.com/doc/refman/8.0/en/create-table.html)（MySQL 8.0 / InnoDB；15.1.20 UNIQUE KEY; NOT NULL; constraints）；[COMMIT: START TRANSACTION, COMMIT, and ROLLBACK](https://dev.mysql.com/doc/refman/8.0/en/commit.html)（MySQL 8.0 / InnoDB；15.3.1 transaction boundaries; rollback and nontransactional-table caveats）；[ACID: InnoDB and the ACID Model](https://dev.mysql.com/doc/refman/8.0/en/mysql-acid.html)（MySQL 8.0 / InnoDB；17.2 Atomicity; Consistency; Isolation; Durability）；[DEADLOCK: How to Minimize and Handle Deadlocks](https://dev.mysql.com/doc/refman/8.0/en/innodb-deadlocks-handling.html)（MySQL 8.0 / InnoDB；17.7.5.3 transaction retry; consistent lock ordering; short transactions）；[ERROR: InnoDB Error Handling](https://dev.mysql.com/doc/refman/8.0/en/innodb-error-handling.html)（MySQL 8.0 / InnoDB；17.21.5 deadlock transaction rollback vs lock-wait statement rollback; innodb_rollback_on_timeout）；[TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html)（RFC 9293 (2022) normative TCP；2.2 Key TCP Concepts; 3.3.2 state machine; 3.4.1 ISN; 3.5 establishment; 3.6 closing; 3.8 data communication; 3.8.2 congestion; 3.8.6 receive window; 3.10.7 segment processing）。
- 权威性：产品维护者或标准制定机构的一手资料；IETF 标准，由 RFC Editor 发布。
- 可支持的范围：下表逐点计划；H09 已获 Commander 批准按假设设计支持；当前项目库存未实现，项目证据仅作类比。
- **不能用于证明：** 不支持项目已实现库存、订单、version 扣减、库存重试；Redis 锁不替代最终数据库约束。

| bankId | scoringPointId | pointType | weight | 冻结评分点原文 | 权威来源与具体章节 | 拟文档节 | 状态 / 支持边界 |
|---|---|---|---:|---|---|---|---|
| H09 | H09-SP1 | CORE | 30 | 能把库存检查和扣减放进同一次原子数据库操作，或者同一个数据库事务保护的行锁临界区，避免无锁地先查询库存、在 Java 中判断后再单独更新。条件 UPDATE 并检查更新行数、version 乐观锁、事务内 SELECT ... FOR UPDATE 后检查并扣减，任一正确方案均可覆盖。 | [LOCK: Locking Reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html); [UPDATE: UPDATE Statement](https://dev.mysql.com/doc/refman/8.0/en/update.html)；17.7.2.4 locking reads; 15.2.17 conditional update | S2 | SUPPORTED；OFFICIAL_MYSQL_MECHANISM_HYPOTHETICAL_DESIGN；Commander 已批准假设设计：可采用条件 UPDATE 并正确解释 affected rows，或同一事务内 FOR UPDATE、检查、扣减；官方 MySQL 机制为依据，不声称当前项目已有库存实现。；projectImplementationPresent=false；projectEvidenceRole=ANALOGY_ONLY_NOT_INVENTORY_EVIDENCE；hypotheticalScenarioApprovedByCommander=true |
| H09 | H09-SP2 | KEY | 25 | 能为一次业务操作设计稳定的 requestId 或幂等键，并通过数据库唯一约束或等价的持久化机制保证同一业务请求即使被客户端重复提交，也不会再次执行库存扣减。 | [UNIQUE: CREATE TABLE Statement](https://dev.mysql.com/doc/refman/8.0/en/create-table.html)；15.1.20 UNIQUE KEY + NOT NULL | S2 | SUPPORTED；OFFICIAL_CONSTRAINT_PLUS_ENGINEERING_IDEMPOTENCY_DESIGN；稳定业务 requestId/幂等键与持久 UNIQUE 是假设设计依据；重复请求复用同键并查找/重放已有结果。当前面试 requestId/唯一冲突处理仅作类比，不保护库存。；projectImplementationPresent=false；projectEvidenceRole=ANALOGY_ONLY_NOT_INVENTORY_EVIDENCE；hypotheticalScenarioApprovedByCommander=true |
| H09 | H09-SP3 | PRINCIPLE | 20 | 能明确设计事务边界，使库存变更、幂等状态以及必须保持一致的核心业务记录不会留下部分成功状态；事务失败时能够整体回滚或进入明确的可恢复状态。 | [COMMIT: START TRANSACTION, COMMIT, and ROLLBACK](https://dev.mysql.com/doc/refman/8.0/en/commit.html); [ACID: InnoDB and the ACID Model](https://dev.mysql.com/doc/refman/8.0/en/mysql-acid.html)；15.3.1 transaction boundary; 17.2 atomicity | S3 | SUPPORTED；OFFICIAL_TRANSACTION_SEMANTICS_HYPOTHETICAL_DESIGN；在同一 MySQL 事务性资源内，将库存变更、幂等状态和必须原子提交的其他数据库记录组成事务单元；不声称本地事务使任意远程副作用原子，也不声称当前项目已有库存事务。；projectImplementationPresent=false；projectEvidenceRole=ANALOGY_ONLY_NOT_INVENTORY_EVIDENCE；hypotheticalScenarioApprovedByCommander=true |
| H09 | H09-SP4 | ADVANCED | 25 | 能正确处理失败与重试语义：认识到客户端或网络超时不等于数据库操作失败；对死锁、锁等待超时等可重试失败采用有限重试；重试继续复用同一个业务幂等键，并能够根据已有处理状态避免重复扣减。 | [DEADLOCK: How to Minimize and Handle Deadlocks](https://dev.mysql.com/doc/refman/8.0/en/innodb-deadlocks-handling.html); [ERROR: InnoDB Error Handling](https://dev.mysql.com/doc/refman/8.0/en/innodb-error-handling.html); [TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html)；17.7.5.3 retry; 17.21.5 rollback scope; RFC 9293 3.8.3 connection failure | S4 | SUPPORTED；OFFICIAL_FAILURE_SEMANTICS_PLUS_ENGINEERING_RECOVERY_DESIGN；官方失败语义加工程恢复推理：deadlock 可回滚被选中事务并要求应用准备重试；lock-wait timeout 默认可能仅回滚语句，不普遍代表整事务回滚；客户端/网络超时不能确定提交结果。有限重试复用同键，结果不明时先查/恢复持久状态，不能盲目再扣；当前项目没有库存重试/恢复实现。；projectImplementationPresent=false；projectEvidenceRole=ANALOGY_ONLY_NOT_INVENTORY_EVIDENCE；hypotheticalScenarioApprovedByCommander=true |

## 4. H09 Commander 审核结论与假设设计边界

H09 冻结题目明确说“假设库存最终以 MySQL 为事实来源”，是设计场景。Commander 已确认回答该题不要求仓库已有库存/商品/订单模块；当前项目事实只作背景和类比。只读搜索 `src/main`、`src/test` 和隔离 schema 的 inventory/stock/product/库存，未发现库存扣减、商品/订单表或相应库存测试。测试评估脚本中的 inventory 名称是环境/产物清单阶段，不能当作商品库存实现。结论只限当前检查的仓库源码和 DDL；没有连接运行数据库做 introspection。

本地事实基线：HEAD `6076a70f18f3062b8faff4fc179cf21f00a95036`，另以当前工作区文件 SHA-256 标识实际阅读版本（忽略规则下的 schema 也必须以文件哈希定位，不能声称均来自已提交 HEAD）。

| 本地文件 | 阅读位置 | 实际证据 | SHA-256 |
|---|---|---|---|
| `src/main/java/com/kun/aiinterview/interview/service/InterviewService.java` | 225–292; 407–431; 488–520 | 稳定 requestId 查询、已处理结果/内容验证、DuplicateKeyException 恢复；仅面试答案 | `2a387120ce5b659278e2467e6667038a07a8381c73fbc767805be2f213b80cc8` |
| `src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java` | 27–84; 86–116 | submitAnswer 同事务 insertAnswer + markAnswered，检查更新行数/主键；评价 CAS；不含库存 | `c63e9df43c5587420e646284842659ebb807b6ec18f5d98294149052d504aecf` |
| `src/main/resources/mapper/InterviewAnswerMapper.xml` | 41–55; 78–140 | request_id 持久查询/插入与状态条件更新；不含 product/stock | `5d41dddbcde8a2795fd3e9949cdae87a7f5babbbdbdee9f0430c6b1ed34f82b9` |
| `docs/environment/ISOLATED_SCHEMAS.sql` | 118–133; 421–436; full CREATE TABLE inventory | 13 个核心表各隔离 schema；uk_interview_answer_request(request_id)，没有库存/商品/订单表；仅读 DDL，未执行 | `1684d7151e103847463009f7f8b2f465149234154ad89a44ebce5b34ae569d4b` |
| `src/test/java/com/kun/aiinterview/interview/service/InterviewServiceTest.java` | 371–413; 573–638; 818–906 | 已有源码测试重复重放、键/载荷冲突、唯一冲突后恢复；仅阅读，未运行 | `6fa628eba0a410910f7f433acdb693cdb6caa74646b135e44370fd3587d7c025` |
| `src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionServiceIntegrationTest.java` | 90–130 | 已有测试会验证 session CAS 失败时前序状态回滚；仅阅读，未运行，不声称本阶段真实 DB 验证 | `6ef0955e39cc67fa60b21663226c958b4f5810800033fc6849f4887e2e68cc09` |

**H09 SOURCE_GAP REVIEW: RESOLVED_AS_HYPOTHETICAL_DESIGN。** 先前将“当前项目没有库存实现”作为假设题的来源缺口判定过严，现依 Commander 审核将 H09-SP1..SP4 全部改为 SUPPORTED，且没有 PROJECT_SPECIFIC 点。保留“当前项目没有库存/商品/订单实现”事实。面试系统的 requestId 唯一约束、重复回答恢复、状态 CAS 与本地事务回滚测试仅作 **ANALOGY_ONLY_NOT_INVENTORY_EVIDENCE**，不能证明库存行为。

Commander 已批准 H09 定位为“明确假设的 MySQL 设计方案 + 项目面试幂等类比”。官方事实、工程推理、项目事实必须分开：MySQL 文档支持 SQL/锁/事务/失败语义；稳定幂等键、结果恢复和有限重试是明确标记的工程设计；本地文件只证明面试业务已使用类似概念。无需先补库存模块或另等库存实现来源。每点均记录 projectImplementationPresent=false、projectEvidenceRole=ANALOGY_ONLY_NOT_INVENTORY_EVIDENCE、hypotheticalScenarioApprovedByCommander=true，sourceGapReason=null。

假设设计必须保留以下边界：条件 UPDATE 正确解释 affected rows，或同事务 FOR UPDATE+检查+扣减；稳定持久唯一业务键的重复请求查/重放既有结果；库存、幂等状态和其他必须原子提交的数据库记录仅在同一事务性资源内组成事务，不能令任意远程副作用原子；deadlock 事务回滚、lock-wait 默认可能仅语句回滚和客户端/网络超时结果不明必须区分。有限重试复用同键，结果不明时先查/恢复持久状态，不能盲目再扣。

修订计数：SUPPORTED_COUNT=74；PROJECT_SPECIFIC_COUNT=0；SOURCE_GAP_COUNT=0。PHASE_4D2: ELIGIBLE_AFTER_COMMANDER_PATCH_VERIFICATION；最终状态为 ELIGIBLE_PENDING_COMMANDER_FINAL_CHECK。本补丁仍只改四份规划文件，不创建知识正文或进入 Phase 4D2。

## 5. 官方来源目录

下表集中记录来源级版本、具体页/节、权威性和访问状态。逐点用途在第 3 节，禁止据一个权威 URL 支持该主题范围外的结论。

| ID | 一手来源 | 版本范围 | 具体页/节 | 权威性 | 访问结果 |
|---|---|---|---|---|---|
| JLS8 | [JLS8: JLS 21 Chapter 8 Classes](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html) | Java SE 21 | 8.1.1.1 abstract; 8.1.2 generic classes; 8.1.4 superclass; 8.1.5 interfaces; 8.3 fields; 8.4 methods; 8.8 constructors | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| JLS9 | [JLS9: JLS 21 Chapter 9 Interfaces](https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html) | Java SE 21 | 9.1.3 superinterfaces; 9.3 fields; 9.4 abstract/default/static/private methods | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| ARRAY | [ARRAY: ArrayList API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html) | Java SE 21 | Class description; get; add; remove | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| LINKED | [LINKED: LinkedList API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedList.html) | Java SE 21 | Class description; indexed traversal; ListIterator; Deque operations | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| IOC | [IOC: Introduction to the Spring IoC Container](https://docs.spring.io/spring-framework/reference/6.2/core/beans/introduction.html) | Spring Framework 6.2; project resolved 6.2.19 | IoC / DI; BeanFactory and ApplicationContext | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| DI | [DI: Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html) | Spring Framework 6.2; project resolved 6.2.19 | Constructor-based and Setter-based DI; constructor vs setter; dependencies | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| LIFECYCLE | [LIFECYCLE: Customizing the Nature of a Bean](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html) | Spring Framework 6.2; project resolved 6.2.19 | Lifecycle callbacks; initialization and destruction | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| ACID | [ACID: InnoDB and the ACID Model](https://dev.mysql.com/doc/refman/8.0/en/mysql-acid.html) | MySQL 8.0 / InnoDB | 17.2 Atomicity; Consistency; Isolation; Durability | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| ISO | [ISO: Transaction Isolation Levels](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html) | MySQL 8.0 / InnoDB | 17.7.2.1 READ UNCOMMITTED / READ COMMITTED / REPEATABLE READ / SERIALIZABLE | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| COMMIT | [COMMIT: START TRANSACTION, COMMIT, and ROLLBACK](https://dev.mysql.com/doc/refman/8.0/en/commit.html) | MySQL 8.0 / InnoDB | 15.3.1 transaction boundaries; rollback and nontransactional-table caveats | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| TCP | [TCP: RFC 9293 Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html) | RFC 9293 (2022) normative TCP | 2.2 Key TCP Concepts; 3.3.2 state machine; 3.4.1 ISN; 3.5 establishment; 3.6 closing; 3.8 data communication; 3.8.2 congestion; 3.8.6 receive window; 3.10.7 segment processing | IETF 标准，由 RFC Editor 发布 | 2026-10-06；正文已取得 |
| UDP | [UDP: RFC 768 User Datagram Protocol](https://www.rfc-editor.org/rfc/rfc768.html) | RFC 768 (1980) UDP base protocol | Introduction; Format; User Interface; IP Interface | IETF 标准体系，由 RFC Editor 发布 | 2026-10-06；正文已取得 |
| OBJECT | [OBJECT: Object API equals/hashCode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html) | Java SE 21 | equals equivalence contract; hashCode consistency and equal objects | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| HASHAPI | [HASHAPI: HashMap API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html) | Java SE 21 | Class description; hash table; key operations | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| HASH21 | [HASH21: OpenJDK HashMap.java](https://raw.githubusercontent.com/openjdk/jdk/jdk-21%2B35/src/java.base/share/classes/java/util/HashMap.java) | OpenJDK jdk-21+35 tagged implementation | hash; getNode; putVal; key hash then identity/equals | OpenJDK 官方仓库，固定发布标签；只用于 Java 21 实现示例 | 2026-10-06；正文已取得 |
| JVMS2 | [JVMS2: JVMS 21 Chapter 2 Structure](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html) | JVM Specification SE 21 | 2.5.1 PC; 2.5.2 JVM stacks; 2.5.3 heap; 2.5.4 method area; 2.5.6 native stacks; 2.6 frames | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| META | [META: Other Considerations: Class Metadata](https://docs.oracle.com/en/java/javase/21/gctuning/other-considerations.html) | Oracle JDK 21 / HotSpot | Class Metadata; native-memory metadata and Metaspace | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| INDEX | [INDEX: Clustered and Secondary Indexes](https://dev.mysql.com/doc/refman/8.0/en/innodb-index-types.html) | MySQL 8.0 / InnoDB | 17.6.2.1 clustered index row data; primary/fallback key; secondary key and primary key | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| PAGE | [PAGE: Physical Structure of an InnoDB Index](https://dev.mysql.com/doc/refman/8.0/en/innodb-physical-structure.html) | MySQL 8.0 / InnoDB | 17.6.2.2 B-tree; leaf pages; default 16 KB configurable page | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| BTREE | [BTREE: Comparison of B-Tree and Hash Indexes](https://dev.mysql.com/doc/refman/8.0/en/index-btree-hash.html) | MySQL 8.0 | 10.3.9 equality/range vs hash equality restrictions | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| COVER | [COVER: How MySQL Uses Indexes](https://dev.mysql.com/doc/refman/8.0/en/mysql-indexes.html) | MySQL 8.0 | 10.3.1 retrieval from index without consulting data rows | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| GLOSSARY | [GLOSSARY: MySQL Glossary](https://dev.mysql.com/doc/refman/8.0/en/glossary.html) | MySQL 8.0 | B-tree entry; clustered index / secondary index terminology | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| REDISTYPES | [REDISTYPES: Redis data types](https://redis.io/docs/latest/develop/data-types/) | Redis official documented semantics, accessed 2026-10-06; no runtime-version claim | Strings / Hashes / Lists / Sets / Sorted Sets overview | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| STRING | [STRING: Redis Strings](https://redis.io/docs/latest/develop/data-types/strings/) | Redis documented classic String semantics; no new Redis 8.x options | Introduction; Strings as counters | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| HASH | [HASH: Redis Hashes](https://redis.io/docs/latest/develop/data-types/hashes/) | Redis documented classic Hash semantics | Introduction; field-value operations | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| LPUSH | [LPUSH: Redis LPUSH](https://redis.io/docs/latest/commands/lpush/) | Core list command since Redis 1.0.0; single-element form | Description; head insertion; complexity | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| RPOP | [RPOP: Redis RPOP](https://redis.io/docs/latest/commands/rpop/) | Core list command since Redis 1.0.0; single-element form; exclude newer count option | Description; tail removal | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| SET | [SET: Redis Sets](https://redis.io/docs/latest/develop/data-types/sets/) | Redis documented classic Set semantics | Unique members; membership; intersections/unions/differences | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| ZSET | [ZSET: Redis Sorted Sets](https://redis.io/docs/latest/develop/data-types/sorted-sets/) | Redis documented classic Sorted Set semantics | Unique members; scores; ranking/range use cases | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| JLS4 | [JLS4: JLS 21 Types, Values, and Variables](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html) | Java SE 21 | 4.4 bounds; 4.5 parameterized types; 4.6 erasure; 4.7 reifiable types | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| JLS15 | [JLS15: JLS 21 Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html) | Java SE 21 | 15.9 class instance creation; 15.10.1 array creation; 15.20.2 instanceof | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| JVMS4 | [JVMS4: JVMS 21 class File Format](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html) | JVM Specification SE 21 | 4.6 ACC_BRIDGE; 4.7.9 Signature attribute and generic metadata | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| ERASURE | [ERASURE: Oracle Tutorial Type Erasure](https://docs.oracle.com/javase/tutorial/java/generics/erasure.html) | Official JDK 8 tutorial; mechanism illustration only, checked against JLS/JVMS 21 | Replacing type parameters; inserted casts; generated bridge methods | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| BRIDGE | [BRIDGE: Oracle Tutorial Effects of Erasure and Bridge Methods](https://docs.oracle.com/javase/tutorial/java/generics/bridgeMethods.html) | Official JDK 8 tutorial; mechanism illustration only, not Java 21 restrictions authority | MyNode example; bridge method and cast | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| CHM8 | [CHM8: OpenJDK 8u ConcurrentHashMap.java](https://raw.githubusercontent.com/openjdk/jdk8u/jdk8u402-b06/jdk/src/share/classes/java/util/concurrent/ConcurrentHashMap.java) | OpenJDK jdk8u402-b06; JDK 8 implementation only | Node/TreeBin/table; putVal; tabAt/casTabAt; get; addCount/helpTransfer/transfer | OpenJDK 官方仓库，固定 8u 标签，与题目 JDK 8 范围一致 | 2026-10-06；正文已取得 |
| G1 | [G1: Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html) | Oracle JDK 21 / HotSpot G1 | Basic Concepts; Heap Layout; Garbage Collection Cycle; Remembered Set; Collection Set; Space-Reclamation Phase Generation Sizing | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| POOL | [POOL: ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html) | Java SE 21 | Core and maximum pool sizes; Queuing; Rejected tasks; hooks; statistics | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| TX | [TX: Using @Transactional](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html) | Spring Framework 6.2; project resolved 6.2.19 | Proxy mode/self-invocation; settings; rollback rules; multiple managers; 6.2 rollbackOn override | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| ROLLBACK | [ROLLBACK: Rolling Back a Declarative Transaction](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/rolling-back.html) | Spring Framework 6.2; project resolved 6.2.19 | RuntimeException/Error defaults; checked exceptions; rollbackFor; programmatic rollback | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| TXRESOURCE | [TXRESOURCE: Understanding the Spring Framework Transaction Abstraction](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/strategies.html) | Spring Framework 6.2; project resolved 6.2.19 | PlatformTransactionManager; local JDBC/DataSource vs global JTA | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| LOCK | [LOCK: Locking Reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html) | MySQL 8.0 / InnoDB | 17.7.2.4 FOR UPDATE; transaction/autocommit requirement; counter example | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| UPDATE | [UPDATE: UPDATE Statement](https://dev.mysql.com/doc/refman/8.0/en/update.html) | MySQL 8.0 / InnoDB | 15.2.17 WHERE and SET; affected/changed rows | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| UNIQUE | [UNIQUE: CREATE TABLE Statement](https://dev.mysql.com/doc/refman/8.0/en/create-table.html) | MySQL 8.0 / InnoDB | 15.1.20 UNIQUE KEY; NOT NULL; constraints | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| DEADLOCK | [DEADLOCK: How to Minimize and Handle Deadlocks](https://dev.mysql.com/doc/refman/8.0/en/innodb-deadlocks-handling.html) | MySQL 8.0 / InnoDB | 17.7.5.3 transaction retry; consistent lock ordering; short transactions | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |
| ERROR | [ERROR: InnoDB Error Handling](https://dev.mysql.com/doc/refman/8.0/en/innodb-error-handling.html) | MySQL 8.0 / InnoDB | 17.21.5 deadlock transaction rollback vs lock-wait statement rollback; innodb_rollback_on_timeout | 产品维护者或标准制定机构的一手资料 | 2026-10-06；正文已取得 |

## 6. 决策与停止条件

RERANKER_DECISION: **DEFER_UNTIL_FULL_BANK_BASELINE**。原试点仅 5/21 主题，应在全部题目获得经审核的权威正文覆盖并通过后续授权入库后，再测完整题库 Baseline，届时再决定是否需要 reranking。此结论既不表示永久不需要，也不表示现在就必须增加模型。

Phase 4C2/4C3 holdout 保持 evaluation-only。没有用逐 query 分数/排名设计文档措辞、定义此计划的重叠风险或调阈值；0.49 的否决不产生替代阈值。本阶段停止于四份规划文件，等待 Commander 审核。
