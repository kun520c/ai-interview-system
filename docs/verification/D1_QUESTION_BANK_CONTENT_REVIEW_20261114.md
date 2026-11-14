# D1｜63 道正式题库内容审核报告

日期 2026-11-14（Asia/Shanghai）。冻结版 `D1-v2-20261114` 的内容门禁为 **PASS**。42 道新增题及修订的 H01 均完成独立代理逐题审查，43/43 通过，未解决 P0/P1 为 0。20 道旧题按原业务字段逐项核对后继承。真实写入、回滚演练和数据库对账另行验收。

- 冻结题库 SHA-256 为 `d4c1d9f20664308231ab383f5dce67154f3c5d874fc041b44f7499ce8647177e`。 [题库 JSON](../question-bank/D1_QUESTION_BANK_V2_20261114.json) · [可读题库](../question-bank/D1_QUESTION_BANK_V2_20261114.md) · [版本与计数清单](../question-bank/D1_BANK_MANIFEST_20261114.json)
- 机器检查表保留 43 条原始判断及四份审查源文件的 SHA-256，可与冻结题库精确对应。 [门禁 JSON](../question-bank/D1_REVIEW_CHECKLIST_20261114.json)
- 审查口径包括技术事实和版本、答案是否直接回答题目、评分点独立性、权重、难度、相邻覆盖差异和等价表述。各审查者复核了修订内容。审查稿的字段逐项匹配合并后的题目。
- 题库有 EASY 15、MEDIUM 21、HARD 27，共 63 题和 298 个评分点。新增 42 题和 201 点，旧题共 97 点，H01 仍为 5 点。D1 的 21 个有效「难度 × 必选分类」桶均有 3 题。题库文件中题目状态均为 ENABLED，逐题评分点权重合计 100。逻辑题号、知识点及规范化题干均无精确重复。
- 分类总数为 JAVA_BASIC、JAVA_COLLECTION、SPRING、MYSQL 各 9，JVM、JAVA_CONCURRENCY、REDIS、NETWORK 各 6，PROJECT_SCENARIO 3。 [D1 覆盖矩阵](D1_QUESTION_BANK_EXPANSION_PLAN_20261113.md)

## 审查范围与版本继承

冻结初版 JSON 的 SHA-256 为 `ce6fff9bd42b064009b01c0300e2da5da16e73d04c363e3cf7f2b351cc1b6fd3`。初版 JSON、初版 Markdown 和旧审查证据均与 HEAD 字节相同。本轮对 20 道继承题逐字段比较 category、knowledgePoint、difficulty、questionContent、referenceAnswer 和有序 scoringPoints，结果全部相同。旧题沿用初版审查，例如 M02、H02 保持 JDK 8 口径。 [旧版 JSON](../question-bank/PHASE_1_QUESTION_BANK_FINAL.json) · [旧版题面](../question-bank/PHASE_1_QUESTION_BANK_FINAL.md) · [旧版审查](../question-bank/Phase1-Grok题库审查.md) · [D0 审计](D0_DATA_READINESS_AUDIT_20261113.md)

H01 保留逻辑题号、知识点、分类、难度、题干和全部评分点类型及权重。业务字段只替换参考答案和 SP4 内容。Java 21 中，`Object` 类型操作数不能直接测试 `List<String>`，可测试 `List<?>`。具备恰当静态类型时，`List<Integer>` 可测试 `ArrayList<Integer>`，运行时检查容器类型。SP4 允许任选两项准确限制，正确的参数化 `instanceof` 特例和等价口述均接受。SP1 的 bound 简写和 SP2 的示例仍有 P2 措辞优化空间，本次按最小修订保留。 [JLS 21 §15.20.2](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.20.2) · [H01 独立审查](../question-bank/d1-drafts/H01_AND_IMPORT_REVIEW_20261114.md)

## 已复核的修订

- **E06、E11、M08**。E06 补问本层捕获的判断，和评分对齐。E11 收窄为受管 Bean 的初始化流程。M08 明确异常对象两两不同且均开启抑制，排除 Throwable 自抑制。
- **M17、M18、H10、H13**。M17 题干明确邮箱非空约束、Bean Validation 与验证路径前提，并纠正 Spring 6.2 参考链接。M18 去掉缺少适用条件的 skip scan 举例。H10 补问外部输入指定字段时的访问边界。H13 补问同一时点的精确统计。
- **H14、H20、H22、H24**。H14 补问 SPI 加载器。H20 补问按当前值写入。H22 修正提前删缓存的竞态，明确读旧值和版本保护的时点。H24 改写为 TLS 证书和主机名校验的职责，避免把描述误读为固定握手先后顺序。

## 验证边界

独立内容判断与静态数据检查各自留证。部分 Java 21 片段另用临时编译和运行验证具体技术边界，项目场景按冻结 HEAD 源码核对。题库评分点权重表示知识覆盖重要性，项目总分仍由 Java 的五维计算产生。尚未做真人候选人试答、实际作答时长统计和难度校准。

## 逐题检查

### EASY

**E06｜异常分类与资源关闭责任**（JAVA_BASIC，4 点）

作者 `author_easy_medium`，独立审查者 `author_medium_hard_java`，结论 **PASS**。

- **技术事实**。JLS 21 §11.1.1、§11.2、§14.20.3 支持 IOException 的捕获或声明、IllegalArgumentException 的非受检地位及成功获取后自动尝试关闭。 **版本口径**。规则针对 Java SE 21，正文无其他版本假设。
- **题干与答案**。现稿依次回答编译义务、正常或异常退出的资源关闭，以及何时值得在本层捕获。 **评分点独立性**。四点各评受检义务、非受检传播、资源所有权与捕获策略。
- **权重**。关闭和受检处理共 60 分，核心优先，捕获策略 15 分。 **难度**。以单个文件流和常见异常为例，符合 EASY。
- **覆盖差异**。E01 考抽象类型，M08 另考多个失败的关闭顺序与抑制结构。 **可接受表述**。正确的捕获、声明、关闭与可恢复策略可以用等价口述或伪代码。
- **发现及处理**。原末项的捕获策略未被直接提问。作者已补问当前层捕获时机，逐字复核确认对齐。
- **依据**。[Java 语言规范 SE 21 · 第 11 章 · §11.1.1、§11.2](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2) · [Java 语言规范 SE 21 · 第 14 章 · §14.20.3](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3)

**E07｜String 不可变与字符串比较**（JAVA_BASIC，4 点）

作者 `author_easy_medium`，独立审查者 `author_medium_hard_java`，结论 **PASS**。

- **技术事实**。JLS 21 §15.21.3 和 Java 21 String、StringBuilder API 证实引用身份、值比较、不可变及可变构建器并发边界。 **版本口径**。Java 21 只需 API 契约，没有依赖旧实现的字符串拼接方式。
- **题干与答案**。答案依次说明两个对象内容相同、replace 返回值、循环追加和线程共享。 **评分点独立性**。身份、对象状态、构建方式与线程安全独立。
- **权重**。比较与不可变共 65 分，追加及线程边界分别 25 和 10 分。 **难度**。常见 String 操作即可作答，不要求常量池或拼接字节码，符合 EASY。
- **覆盖差异**。M01 考自定义对象哈希契约。此题考 String 值语义及可变构建。 **可接受表述**。接受 new String、空值比较和局部构建器等同义例子。
- **依据**。[Java 语言规范 SE 21 · 第 15 章 · §15.21.3](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.21.3) · [Java SE 21 API · String · 类概述、equals、replace](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html#replace(char,char)) · [Java SE 21 API · StringBuilder · 类概述](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/StringBuilder.html) · [Java SE 21 API · Objects · equals(Object,Object)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Objects.html#equals(java.lang.Object,java.lang.Object))

**E08｜List、Set、Map 的语义与选择**（JAVA_COLLECTION，4 点）

作者 `author_easy_medium`，独立审查者 `author_medium_hard_java`，结论 **PASS**。

- **技术事实**。Java 21 的 List、Set、Map、LinkedHashSet API 支持位置顺序、唯一元素、键值映射和普通 add 保持首次出现顺序。 **版本口径**。LinkedHashSet 结论按普通 add，未把 Java 21 的 addFirst、addLast 也视为不改顺序。
- **题干与答案**。三份数据和额外去重保序要求均有对应选择及原因。 **评分点独立性**。四点分别评原序重复记录、唯一 ID、按键最新值与有序去重。
- **权重**。Map 题内任务含最新值更新，30 分略高合理，其余 20–25 分。 **难度**。从日常业务语义选标准接口，符合 EASY。
- **覆盖差异**。E02 比较两个 List 实现，E09 考同端和异端操作，考核结果不同。 **可接受表述**。可解释使用保持插入顺序的 LinkedHashMap 键视图，无须背统一措辞。
- **依据**。[Java SE 21 API · List · 类概述](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/List.html) · [Java SE 21 API · Set · 类概述](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Set.html) · [Java SE 21 API · Map · 类概述](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html) · [Java SE 21 API · LinkedHashSet · 类概述](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashSet.html)

**E09｜用 Deque 表达栈和队列**（JAVA_COLLECTION，4 点）

作者 `author_easy_medium`，独立审查者 `author_medium_hard_java`，结论 **PASS**。

- **技术事实**。Java 21 Deque 方法对照表、ArrayDeque 和 LinkedBlockingDeque API 支持两端映射、可扩容和显式并发有界实现。 **版本口径**。按 Java 21 API 解释 ArrayDeque 构造参数和实现边界。
- **题干与答案**。答案给出撤销、待办的入出端，说明空结果和容量并发条件。 **评分点独立性**。LIFO、FIFO、容量限制与并发访问独立，满时处理可作为后续说明。
- **权重**。两种核心操作各 30 分，容量和线程边界各 20 分。 **难度**。两种基本结构和 API 选择可在 1–2 分钟回答，符合 EASY。
- **覆盖差异**。E08 先按数据语义选接口，E02 考 List 结构，此题选择进出方向。 **可接受表述**。接受用同一端或不同端的等价 API，允许正确的外部同步及限制。
- **依据**。[Java SE 21 API · Deque · 类概述和方法对应表](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Deque.html) · [Java SE 21 API · ArrayDeque · 类概述和 ArrayDeque(int)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayDeque.html) · [Java SE 21 API · LinkedBlockingDeque · 类概述和 LinkedBlockingDeque(int)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/LinkedBlockingDeque.html#%3Cinit%3E(int))

**E10｜Bean 常用作用域与单例状态**（SPRING，4 点）

作者 `author_easy_medium`，独立审查者 `author_medium_hard_java`，结论 **PASS**。

- **技术事实**。Spring 6.2 Bean Scopes 参考支持 singleton 的容器和定义范围、prototype 的请求次数、Web request 作用域及单例依赖边界。 **版本口径**。Spring Framework 6.2 / Boot 3.5，不依赖特殊作用域或配置。
- **题干与答案**。答案逐个描述三个作用域，并解释共享字段在并发请求中串值及如何隔离。 **评分点独立性**。三个作用域及风险对策对应四项，未重复打分初始化或销毁。
- **权重**。singleton 与请求状态风险各 30 分，另外两个概念各 20 分。 **难度**。概念及日常场景判断足够，不要求代理细节，符合 EASY。
- **覆盖差异**。E03 考 IoC、DI，E11 考受管生命周期回调，范围清楚。 **可接受表述**。允许参数、局部变量、不可变上下文或有明确边界的 request 组件。
- **依据**。[Spring Framework 6.2 · Bean Scopes · The Singleton Scope / The Prototype Scope / Request Scope / Singleton Beans with Prototype-bean Dependencies](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html)

**E11｜Bean 初始化与资源释放**（SPRING，4 点）

作者 `author_easy_medium`，独立审查者 `author_medium_hard_java`，结论 **PASS**。

- **技术事实**。Spring 6.2 Lifecycle Callbacks 和 Jakarta 注解参考支持依赖就绪后初始化、正常关闭时销毁，作用对象为容器管理实例。 **版本口径**。Spring 6.2 下接受 Jakarta 注解、Spring 接口和显式配置的回调。
- **题干与答案**。初始化、销毁与直接 new 的责任均有对应内容，修订后不外推 Bean 提前暴露等特殊路径。 **评分点独立性**。时序、初始化回调选择、销毁回调选择、受管边界分别评分。
- **权重**。生命周期时序与正常关闭各 30 分，初始化机制 25 分，直接创建边界 15 分。 **难度**。题面限定正常停机和普通受管单例，不要求后处理器顺序，符合 EASY。
- **覆盖差异**。E10 考实例复用范围，E03 考由谁管理依赖，本题要放置两类资源回调。 **可接受表述**。允许候选人用等价回调机制和任一正确受管资源实例举例。
- **发现及处理**。原描述可能被读成无条件保证 Bean 完成创建后才注入其他对象。作者已改为检查属于受管 Bean 初始化流程。
- **依据**。[Spring Framework 6.2 · Customizing the Nature of a Bean · Lifecycle Callbacks / Initialization Callbacks / Destruction Callbacks](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html#beans-factory-lifecycle) · [Spring Framework 6.2 · Using @PostConstruct and @PreDestroy · Using @PostConstruct and @PreDestroy](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/postconstruct-and-predestroy-annotations.html)

**E12｜主键、唯一约束与 NULL**（MYSQL，4 点）

作者 `author_easy_medium`，独立审查者 `author_medium_hard_java`，结论 **PASS**。

- **技术事实**。MySQL 8.0 CREATE TABLE 与 Working with NULL Values 证实主键非空、可空 UNIQUE 多 NULL 和 IS NULL 的语义。 **版本口径**。MySQL 8.0/InnoDB 口径，答案保留排序规则对非 NULL 比较的影响。
- **题干与答案**。答案给出 ID 主键、邮箱完整列唯一、两条 NULL 不冲突及空字符串查询结果。 **评分点独立性**。行身份、唯一约束、三值筛选、空字符串分别评分。
- **权重**。最常见错误位于可空唯一和空值查询，各给 30 分合理。 **难度**。一个表和简单约束即可回答，符合 EASY。
- **覆盖差异**。E04 考 ACID，E13 考外连接时补出的 NULL；本题讨论实际存储值和约束。 **可接受表述**。接受列级或表级约束，能口述约束及筛选即可。
- **依据**。[MySQL 8.0 Reference Manual · CREATE TABLE · PRIMARY KEY / UNIQUE](https://dev.mysql.com/doc/refman/8.0/en/create-table.html) · [MySQL 8.0 Reference Manual · Working with NULL Values · Working with NULL Values](https://dev.mysql.com/doc/refman/8.0/en/working-with-null.html)

**E13｜INNER JOIN 与 LEFT JOIN 的结果差异**（MYSQL，4 点）

作者 `author_easy_medium`，独立审查者 `author_medium_hard_java`，结论 **PASS**。

- **技术事实**。MySQL 8.0 JOIN 及外连接简化手册明确未匹配右侧补 NULL，以及 WHERE 等值谓词对补 NULL 行不可能为真。 **版本口径**。MySQL 8.0 常规语义，未声称每种 WHERE 谓词都会破坏左连接。
- **题干与答案**。题面所求的所有用户及已支付订单由 LEFT JOIN 与 ON 条件达成。答案含没有订单和只有未支付订单两类反例。 **评分点独立性**。INNER 对照、LEFT 补行、ON 谓词放置和 WHERE 失败原因四项可独立确认。
- **权重**。完成正确 SQL 的条件位置权重 30，连接语义各 25，反例解释 20。 **难度**。单连接和等值谓词的结果推理适合 EASY，不需要了解优化器改写。
- **覆盖差异**。E12 考空值与约束，E04 考事务；本题考匹配阶段和过滤阶段。 **可接受表述**。接受先过滤 orders 再连接的派生表。
- **依据**。[MySQL 8.0 Reference Manual · JOIN Clause · ON / WHERE / LEFT JOIN](https://dev.mysql.com/doc/refman/8.0/en/join.html) · [MySQL 8.0 Reference Manual · Outer Join Simplification · Null-rejected conditions](https://dev.mysql.com/doc/refman/8.0/en/outer-join-simplification.html)

**E14｜HTTP 方法和常见状态码**（NETWORK，5 点）

作者 `author_easy_medium`，独立审查者 `author_medium_hard_java`，结论 **PASS**。

- **技术事实**。RFC 9110 §§9.2–9.3、§15 支持 GET 安全和幂等、POST 无通用保证及示例状态码。 **版本口径**。以 RFC 9110 的 HTTP 语义为准，不绑定 HTTP/1.1、2、3 的传输。
- **题干与答案**。读取、创建、常见失败与两类方法语义均有具体回答。 **评分点独立性**。方法选择、成功响应、错误响应、安全性和幂等性对应五项。
- **权重**。方法及状态码共 65 分，语义 35 分，适合应用层基础题。 **难度**。要求会设计常见 CRUD 响应，无需解释重试传输，符合 EASY。
- **覆盖差异**。E05 考传输，H25 分析提交状态不确定后的重试，不与方法基础重复。 **可接受表述**。状态码以题面语义为准，参数语义校验可有 422 等合理表达。
- **依据**。[RFC 9110 · HTTP Semantics · §9.2.1–§9.2.2、§9.3.1、§9.3.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.2.1) · [RFC 9110 · HTTP Semantics · §15.3.1、§15.3.2、§15.5.1、§15.5.5、§15.6.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-15)

**E15｜从域名到服务地址的 DNS 查询**（NETWORK，4 点）

作者 `author_easy_medium`，独立审查者 `author_medium_hard_java`，结论 **PASS**。

- **技术事实**。RFC 1034 §5.3.3 与 RFC 9499 的解析术语支持递归服务、referral 与缓存，RFC 3596 定义 AAAA。 **版本口径**。按 RFC 1034 与 RFC 9499 的常见路径，已经明确无可用缓存、不转发等限定。
- **题干与答案**。答案给出无缓存条件下常见解析路径、TTL 以及站点建连与地址查找的关系。 **评分点独立性**。客户和解析器职责、权威转介、缓存、后续建连分别评分。
- **权重**。解析路径和角色共 55 分，TTL 25，应用连接 20。 **难度**。无需背包格式或异常域名，口述常见路径符合 EASY。
- **覆盖差异**。E05 比较 TCP 和 UDP，H08 考连接状态，此题聚焦域名解析。 **可接受表述**。允许加 hosts、CNAME、上游转发或 IPv6，只要讲清常见主路径。
- **依据**。[RFC 1034 · Domain Names—Concepts and Facilities · §3.6、§4.3.1、§5.3.3](https://www.rfc-editor.org/rfc/rfc1034.html#section-5.3.3) · [RFC 9499 · DNS Terminology · §5 Resource Records、§6 DNS Servers and Clients](https://www.rfc-editor.org/rfc/rfc9499.html#section-5) · [RFC 3596 · DNS Extensions to Support IP Version 6 · §2 AAAA Resource Record Type](https://www.rfc-editor.org/rfc/rfc3596.html#section-2) · [RFC 9293 · Transmission Control Protocol · §3.5 Establishing a Connection](https://www.rfc-editor.org/rfc/rfc9293.html#section-3.5)

### MEDIUM

**M08｜try-with-resources 与 suppressed 异常**（JAVA_BASIC，5 点）

作者 `author_easy_medium`，独立审查者 `author_medium_hard_java`，结论 **PASS**。

- **技术事实**。JLS 21 §14.20.2–§14.20.3 和 Throwable API 支持逆序、业务失败主异常、关闭失败受抑制及 finally 覆盖。 **版本口径**。按 Java 21，异常对象明确互异且支持抑制，排除了自抑制异常和关闭抑制配置。
- **题干与答案**。题面要求两条退出路径与手写 finally，答案逐条给出主异常和列表顺序。 **评分点独立性**。关闭顺序、try 主异常、关闭信息所在、正常 try 情况及 finally 风险分别评分。
- **权重**。主要场景与异常保真 90 分，手写 finally 对照 10 分。 **难度**。要求从题设推理两个失败路径，介于基础用法和深入异常机制之间，符合 MEDIUM。
- **覆盖差异**。E06 考受检规则和基本关闭，M08 考多资源失败组合与信息保真。 **可接受表述**。接受伪代码、异常树和同义说明，不需背翻译展开。
- **发现及处理**。原题未明确 try 异常与两个 close 异常对象互不相同。作者已补充所有抛出的异常对象彼此不同，Throwable.addSuppressed 的自抑制边界不落入题设。
- **依据**。[Java 语言规范 SE 21 · 第 14 章 · §14.20.2、§14.20.3.1](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3) · [Java SE 21 API · Throwable · addSuppressed / getSuppressed](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Throwable.html#getSuppressed())

**M09｜泛型通配符与 PECS**（JAVA_BASIC，5 点）

作者 `author_easy_medium`，独立审查者 `author_medium_hard_java`，结论 **PASS**。

- **技术事实**。JLS 21 §4.5、§4.10 支持 List 参数不变、通配符上下界，Collections.copy 展示相同方向的参数形状。 **版本口径**。仅 Java SE 21 类型规则，未要求普通 List 实现支持对任何可变性行为。
- **题干与答案**。题目要求签名和读写边界。答案给出有效签名和反例，来源注明 Collections.copy 是覆盖而非追加。 **评分点独立性**。泛型不变性、源读、源写限制、目标写、目标读五项彼此可检验。
- **权重**。签名与可执行读写能力分担权重，最核心的不变性和目标写各 25。 **难度**。需要用通配符设计参数和说明类型安全，符合 MEDIUM。
- **覆盖差异**。H01 讨论擦除与运行时检查，M01 讨论对象比较。此题考编译期 API。 **可接受表述**。接受 <T extends Number> 的等价泛型方法，无需复述 PECS 缩写。
- **依据**。[Java 语言规范 SE 21 · 第 4 章 · §4.5.1、§4.5.2、§4.10](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.5.1) · [Java SE 21 API · Collections · copy(List<? super T>, List<? extends T>)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collections.html#copy(java.util.List,java.util.List))

**M10｜迭代中修改集合与 fail-fast**（JAVA_COLLECTION，5 点）

作者 `author_easy_medium`，独立审查者 `author_medium_hard_java`，结论 **PASS**。

- **技术事实**。JLS 21 §14.14.2、ArrayList API 和 Iterator.remove 支持增强 for、结构修改、set 和尽力检测边界。 **版本口径**。按 Java 21 ArrayList，未把 set 或必抛规则推广到所有集合。
- **题干与答案**。逐问解释直接删除、set、正确删除和并发策略。 **评分点独立性**。增强 for 产生的失效机制、删除方式、set、检测契约和并发控制分别评估。
- **权重**。解释原因和实际改法各 25 分，剩余按边界分配。 **难度**。可理解源码片段和 API 契约即可，无需记 modCount 字段，符合 MEDIUM。
- **覆盖差异**。M02 考 HashMap 写入，H13 考并发集合迭代视图，此题是普通 List 修改规则。 **可接受表述**。接受 removeIf、正确的倒序下标遍历及遍历后单独修改。
- **依据**。[Java 语言规范 SE 21 · 第 14 章 · §14.14.2](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.14.2) · [Java SE 21 API · ArrayList · 类概述、iterator、removeIf](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html) · [Java SE 21 API · Iterator · remove()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Iterator.html#remove())

**M11｜集合视图、不可修改与防御性复制**（JAVA_COLLECTION，5 点）

作者 `author_easy_medium`，独立审查者 `author_medium_hard_java`，结论 **PASS**。

- **技术事实**。Java 21 Collections.unmodifiableList 和 List.copyOf API 明确读穿视图、后续源改动隔离、null 拒绝及共享引用。 **版本口径**。Java 21 API 契约，不要求每次分配或特定运行时类型。
- **题干与答案**。答案覆盖容器结构、元素状态及 null 的兼容策略。 **评分点独立性**。视图联动、结构复制、共享元素引用、固定状态手段和 null 分别评分。
- **权重**。视图及复制共 55 分，元素边界 35 分，null 10 分。 **难度**。要求从接口输出需求选择实现并分辨结构与元素，符合 MEDIUM。
- **覆盖差异**。M10 考遍历删除。H13 的快照是并发容器迭代语义，此题考返回值对调用者的防御性隔离。 **可接受表述**。接受对新副本只读包装、不可变投影和等效 DTO 复制。
- **依据**。[Java SE 21 API · Collections · unmodifiableList](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)) · [Java SE 21 API · List · Unmodifiable Lists / copyOf](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection))

**M12｜类加载、链接和初始化的区别**（JVM，5 点）

作者 `author_medium_hard_java`，独立审查者 `author_hard`，结论 **PASS**。

- **技术事实**。JLS 21 §12.3–12.4.1 支持：读取常量变量不触发，首次读取本类声明的非常量静态字段触发，解析可延后。 **版本口径**。Java 21 JLS，常量必须由常量表达式初始化。
- **题干与答案**。按 PORT、retries、new 的顺序作出明确预测，再解释加载、链接和初始化。 **评分点独立性**。第 1–3 点考三次操作的不同结论，第 4 点辨认两个阶段，第 5 点专看链接子步骤。
- **权重**。25 分给首次触发判断，35 分给两条对照，40 分给阶段解释。 **难度**。在给定小段代码下分析触发条件，属于中等。
- **覆盖差异**。M03 考内存区域，H14 考运行时类型身份，这题以静态初始化时间线区分。 **可接受表述**。可说主动使用或讲触发规则，允许任一正确术语。
- **依据**。[Java SE 21 语言规范 · §12.2、§12.3](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.2) · [Java SE 21 语言规范 · §12.4.1 何时初始化](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.4.1) · [Java SE 21 语言规范 · §4.12.4 常量变量](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.12.4)

**M13｜对象可达性、GC Roots 与内存泄漏**（JVM，5 点）

作者 `author_medium_hard_java`，独立审查者 `author_hard`，结论 **PASS**。

- **技术事实**。Java 21 java.lang.ref 可达性约定及 HotSpot 排障指南支持：静态字段持续持有对象，稳定负载下回收后存活集上升是线索。 **版本口径**。Java 21 引用契约，诊断工具对应 HotSpot。
- **题干与答案**。说明方法返回后仍保留，给出证据及修复前后验证。 **评分点独立性**。第 1 点要求场景保留链，第 2 点说明可达性规则，第 3 点给诊断，第 4 点比较 OOM，第 5 点验证修复。
- **权重**。保留链和原理 50 分，确认故障与验证占 50 分，符合题设重点。 **难度**。具体 Map 故障已经给出，解释引用和验证即可。
- **覆盖差异**。M03 考区域，H03 考 G1，H15 需区分多种诊断方向，M13 固定检查 Java 堆引用。 **可接受表述**。允许 JFR、堆转储或能证明根路径的等价工具，修复可用到期、移除或容量上限。
- **依据**。[Java SE 21 引用 API · Reachability](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ref/package-summary.html#reachability) · [Oracle Java 21 故障排查指南 · The java.lang.OutOfMemoryError Error / Detecting a Memory Leak](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshooting-memory-leaks.html) · [Oracle Java 21 故障排查指南 · Diagnosing Java Memory Leaks](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshooting-memory-leaks.html)

**M14｜synchronized 与 ReentrantLock 的选择**（JAVA_CONCURRENCY，5 点）

作者 `author_medium_hard_java`，独立审查者 `author_hard`，结论 **PASS**。

- **技术事实**。Java 21 Lock/ReentrantLock API 和 JLS §14.19 支持：限时 tryLock 可被中断，成功后才 unlock，同步块退出自动释放。 **版本口径**。Java 21 内置监视器及标准 ReentrantLock。
- **题干与答案**。覆盖同一共享锁的选择、普通路径、限时路径和中断处理。 **评分点独立性**。第 2 点的获取失败路径与第 3 点普通路径的业务异常清理分开，第 4 点同步语义、第 5 点自动释放边界独立。
- **权重**。25 分给选锁，40 分给安全释放与处理失败，35 分给语义和简单场景。 **难度**。小范围 API 选择和伪代码，适合掌握基础并发后的中等题。
- **覆盖差异**。M04 考 volatile 可见性，M15 考条件等待，本题考显式锁获取策略。 **可接受表述**。接受等价伪代码、条件保护或恢复中断位，避免背调用签名。
- **依据**。[Java 21 ReentrantLock · 类说明、tryLock(long, TimeUnit)、lockInterruptibly](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html) · [Java 21 Lock · Memory Synchronization、接口说明](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/locks/Lock.html) · [Java SE 21 语言规范 · §14.19 synchronized](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.19)

**M15｜线程中断与 wait/sleep 的边界**（JAVA_CONCURRENCY，5 点）

作者 `author_medium_hard_java`，独立审查者 `author_hard`，结论 **PASS**。

- **技术事实**。Java 21 Object.wait 与 Thread API 支持：sleep 持锁，wait 释放并重获目标监视器，抛中断异常清除标志。 **版本口径**。Java 21 Object 与 Thread，不套用 Condition 或 BlockingQueue 的 API 契约。
- **题干与答案**。解释原代码阻塞生产者，描述 while、通知和退出路径。 **评分点独立性**。持锁休眠、wait 合同、消费者谓词、生产者通知、中断处理各自检验。
- **权重**。45 分给监视器机制，35 分给生产消费协议，20 分给协作中断。 **难度**。给定监视器和队列，考线程行为及短伪代码，符合中等。
- **覆盖差异**。M14 聚焦互斥锁获取，M04 聚焦 volatile，本题聚焦等待与唤醒协议。 **可接受表述**。接受 notifyAll 和 while 伪代码，退出或传播按调用约定选择。
- **依据**。[Java 21 Object · wait、notifyAll](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html#wait(long,int)) · [Java 21 Thread · sleep、interrupt、interrupted](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#sleep(long))

**M16｜多个实现如何进行依赖注入**（SPRING，5 点）

作者 `author_medium_hard_java`，独立审查者 `author_hard`，结论 **PASS**。

- **技术事实**。Spring 6.2 @Primary 适用单值优先选择，@Qualifier 筛选类型候选，单构造器可省略 @Autowired，集合仍接收匹配 Bean。 **版本口径**。Spring Framework 6.2 注解构造器注入。
- **题干与答案**。直接给邮件默认、短信指定、歧义触发条件及集合注入。 **评分点独立性**。候选发现、默认优先、显式限定、多值注入和构造器约定分开。
- **权重**。默认与限定 50 分，歧义 25 分，多值和构造器 25 分。 **难度**。在两个 Bean 上应用三种注入方式，中等。
- **覆盖差异**。E03 考 IoC 原理，M05 考代理，本题解构真实的依赖选择。 **可接受表述**。允许限定符的标签、自定义注解，或解释名称回退条件。
- **依据**。[Spring Framework 6.2 Reference · Using @Autowired](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html) · [Spring Framework 6.2 Reference · Fine-tuning Annotation-based Autowiring with @Primary or @Fallback](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-primary.html) · [Spring Framework 6.2 Reference · Fine-tuning Annotation-based Autowiring with Qualifiers](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html) · [Spring Framework 6.2 Javadoc · NoUniqueBeanDefinitionException](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/NoUniqueBeanDefinitionException.html)

**M17｜接口参数校验与统一异常响应**（SPRING，5 点）

作者 `author_medium_hard_java`，独立审查者 `author_hard`，结论 **PASS**。

- **技术事实**。Spring MVC 6.2 Validation 文档区分对象级校验和内建方法校验。消息转换读取失败与两类校验异常可单独映射。 **版本口径**。Spring 6.2 内建 MVC 校验，区分 6.1 前无内建方法校验与类级 AOP 路径。
- **题干与答案**。题干已交代 DTO @NotBlank、Bean Validation、无 BindingResult 和类级 @Validated，答案对应两种参数签名与统一响应。 **评分点独立性**。解析、DTO、方法级、错误协议、敏感值限制分别考查。
- **权重**。65 分给请求失败阶段及方法签名变化，35 分给错误响应可用性和安全。 **难度**。中等偏上。方法校验的差异有明确触发条件，异常名称可用准确行为替代以保持口述难度。
- **覆盖差异**。E03 是容器，M05 是 AOP，此题考 MVC 输入边界。 **可接受表述**。接受 ProblemDetail 或项目自定义结构。规范术语或说明行为均可。
- **发现及处理（P2，RESOLVED）**。原题未给邮箱非空约束及 BindingResult 前提。 题干明确 @NotBlank、已启用 Bean Validation、未使用 BindingResult 或类级 @Validated。
- **发现及处理（P2，RESOLVED）**。首个 Spring 参考链接目录错误。 更新为 Spring 6.2 MVC ann-validation 页面。
- **依据**。[Spring Framework 6.2 Reference · Validation](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html) · [Spring Framework 6.2 Reference · Error Responses](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-rest-exceptions.html) · [Spring Framework 6.2 Javadoc · handleHttpMessageNotReadable、handleMethodArgumentNotValid、handleHandlerMethodValidationException](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseEntityExceptionHandler.html)

**M18｜联合索引与查询条件的匹配**（MYSQL，5 点）

作者 `author_medium_hard_java`，独立审查者 `author_hard`，结论 **PASS**。

- **技术事实**。MySQL 8.0 range optimization 和复合索引约定支持：A 固定两个前缀后按时间范围，B 缺中间状态使普通连续范围及全局时间顺序不成立。 **版本口径**。MySQL 8.0 InnoDB 普通 B-tree 二级索引，实际计划仍由优化器选择。
- **题干与答案**。直接比较两个查询，并解释列覆盖、表行获取和 EXPLAIN 验证。 **评分点独立性**。两种定位、排序、覆盖和实际验证是五个独立判断。
- **权重**。70 分给索引定位与排序，20 分给覆盖，10 分给过滤与实证。 **难度**。以一个索引和两条 SQL 推理，适合中等。
- **覆盖差异**。M06 考索引结构，M19 解读已给的执行计划，本题从 SQL 推导索引能力。 **可接受表述**。可画三元组排序或分类扫描示意，不背最左前缀口诀。
- **发现及处理（P2，RESOLVED）**。旧替代表达提及 skip scan 但未限定覆盖等前提，题干有多余空格。 移除该旁支，保留其他索引或扫描的可能性，清理空格。
- **依据**。[MySQL 8.0 参考手册 · Multiple-Column Indexes](https://dev.mysql.com/doc/refman/8.0/en/multiple-column-indexes.html) · [MySQL 8.0 参考手册 · Range Access and Multiple-Part Indexes](https://dev.mysql.com/doc/refman/8.0/en/range-optimization.html) · [MySQL 8.0 参考手册 · Use of Indexes to Satisfy ORDER BY](https://dev.mysql.com/doc/refman/8.0/en/order-by-optimization.html) · [MySQL 8.0 参考手册 · Index Condition Pushdown Optimization](https://dev.mysql.com/doc/refman/8.0/en/index-condition-pushdown-optimization.html) · [MySQL 8.0 参考手册 · EXPLAIN Extra Information](https://dev.mysql.com/doc/refman/8.0/en/explain-output.html)

**M19｜如何用 EXPLAIN 分析慢查询**（MYSQL，5 点）

作者 `author_medium_hard_java`，独立审查者 `author_hard`，结论 **PASS**。

- **技术事实**。MySQL 8.0 EXPLAIN 文档支持：possible_keys 为候选，key=NULL 与 ALL 是未用索引的表扫，200000 × 2% 为估计 4000。 **版本口径**。MySQL 8.0，EXPLAIN ANALYZE 从 8.0.18 开始提供。
- **题干与答案**。先解读字段再用实际参数和 ANALYZE 定位，给出修改后核对。 **评分点独立性**。候选及访问类型、估计过滤、实际执行、原因改动和前后核验各自检查。
- **权重**。65 分给读计划和实测区别，35 分给诊断及核验。 **难度**。给足执行计划上下文，分析和校验难度中等。
- **覆盖差异**。M18 考复合索引能做什么，M19 考优化器实际选择和数据证据。 **可接受表述**。接受统计更新、调整谓词或索引，但需要依据及验证，不要求固定措施。
- **依据**。[MySQL 8.0 参考手册 · EXPLAIN Output Columns / Join Types / Extra](https://dev.mysql.com/doc/refman/8.0/en/explain-output.html) · [MySQL 8.0 参考手册 · EXPLAIN ANALYZE / Obtaining Execution Plan Information](https://dev.mysql.com/doc/refman/8.0/en/explain.html)

**M20｜过期和淘汰有什么区别**（REDIS，5 点）

作者 `author_medium_hard_java`，独立审查者 `author_hard`，结论 **PASS**。

- **技术事实**。Redis 7.2 配置和官方淘汰、EXPIRE 文档分别约束 maxmemory 与 TTL；LRU 近似，volatile 无候选时无法为内存增长命令淘汰。 **版本口径**。Redis OSS 7.2 单实例，避免把近似 LRU 当精确全局排序。
- **题干与答案**。直接说明提前未命中的可能机制及应用回源、监控证据。 **评分点独立性**。机制差别、主动被动过期、两种候选范围、无候选行为、回源与排查分开。
- **权重**。70 分给过期与策略，30 分给命令影响和应用处理。 **难度**。解释常用缓存行为与指标，不要求源码，属于中等。
- **覆盖差异**。M07 考数据类型，H22 考缓存更新竞态与热点并发，本题考键何时消失。 **可接受表述**。接受等效监控或回填手段，不凭聚合计数断言单键删除原因。
- **依据**。[Redis EXPIRE 命令文档 · How Redis expires keys](https://redis.io/docs/latest/commands/expire/) · [Redis Key eviction · Eviction policies / Using the INFO command](https://redis.io/docs/latest/develop/reference/eviction/) · [Redis 7.2 配置源码 · maxmemory / maxmemory-policy](https://raw.githubusercontent.com/redis/redis/7.2/redis.conf) · [Redis INFO 命令文档 · Stats](https://redis.io/docs/latest/commands/info/)

**M21｜RDB 与 AOF 的恢复取舍**（REDIS，5 点）

作者 `author_medium_hard_java`，独立审查者 `author_hard`，结论 **PASS**。

- **技术事实**。Redis OSS 持久化文档与 7.2 配置支持 RDB 快照、分段 AOF 恢复、重写及 everysec；约一秒应作为典型量级。 **版本口径**。Redis OSS 7.2 单实例，多段 AOF，不泛化旧版单文件实现。
- **题干与答案**。从数据丢失窗口和重启恢复成本回答选择问题，指出独立备份。 **评分点独立性**。快照、日志、刷盘、取舍验证、备份分别检查。
- **权重**。50 分给恢复机制，20 分给刷盘，30 分给选择与备份。 **难度**。用一般缓存状态和可接受损失做选择，中等。
- **覆盖差异**。M20 考在线键生命周期，H23 考 Redis 执行原子性，本题考重启持久性。 **可接受表述**。可用 RPO/RTO 或直接说明能丢多少、多久恢复，允许不同机制选择。
- **依据**。[Redis 持久化文档 · RDB advantages and disadvantages / AOF / Backing up Redis data](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/) · [Redis 7.2 配置源码 · SNAPSHOTTING / APPEND ONLY MODE](https://raw.githubusercontent.com/redis/redis/7.2/redis.conf)

### HARD

**H01｜泛型擦除与运行时类型边界**（JAVA_BASIC，5 点）

作者 `root`，独立审查者 `author_easy_medium`，结论 **PASS**。

- **技术事实**。JLS 21 §4.6、§4.7 和 §15.20.2 支持擦除与带参数化类型测试的边界。Object 无法直接测试 List<String>，List<Integer> 可以测试 ArrayList<Integer>，运行时检查容器类型。JVMS 21 §4.7.9 说明声明签名元数据仍可保留。 **版本口径**。限定 Java SE 21。合法的参数化类型测试取决于操作数静态类型能否进行对应测试，Java 16 起放宽。JVMS Signature 支撑声明信息保留。
- **题干与答案**。答案给出擦除规则、常见限制、合法与非法 instanceof 对照及声明元数据保留，直接回应旧题题干。 **评分点独立性**。SP1 考擦除映射，SP2 考运行时 Class 身份，SP3 考编译器插入转换和桥接，SP4 考限制与条件，SP5 考仍可反射读取的声明元数据。
- **权重**。保留 25/20/20/25/10。擦除机制和使用边界各 25，元数据边界 10，符合核心优先。 **难度**。适合本科 Java 后端 HARD，允许任选两项准确限制，不要求背诵规范节号或记忆特定代码。
- **覆盖差异**。M09 评估编译期通配符接口的读写，H01 评估擦除与运行时测试。H10 的模块反射访问边界也不重复。 **可接受表述**。可接受同等例子。合法的参数化 instanceof 不能因使用具体类型实参而扣分，也不能据此主张 JVM 逐一检查元素。
- **发现及处理**。P2 措辞可进一步精确。SP1 的 bound 是简写，完整参考答案已说明最左边界的擦除。SP2 的 List 例可改为同一 ArrayList 实现，避免把 List 接口与具体实现混读。本轮最小修订保留原 SP1/SP2。
- **依据**。[JLS 21 Type Erasure · 4.6, 4.7](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.6) · [JLS 21 instanceof · 15.20.2](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.20.2) · [JVMS 21 Signature attribute · 4.7.9](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.9)

**H10｜反射、模块封装与访问边界**（JAVA_BASIC，5 点）

作者 `author_medium_hard_java`，独立审查者 `author_hard`，结论 **PASS**。

- **技术事实**。Java 21 AccessibleObject 与 Field API 区分成员发现、访问检查和跨具名模块包开放，题设 trySetAccessible false 与强制访问异常成立。 **版本口径**。Java 21 两个具名模块的 private 字段，未配置 opens。
- **题干与答案**。明确回答 exports 的不足和两种调用结果，补问后覆盖可信字段约束。 **评分点独立性**。发现与访问、模块规则、API 失败方式、修复与信任边界分开。
- **权重**。70 分给关键权限机制与失败结果，30 分给实际处置。 **难度**。需要结合模块可见性、反射 API 和输入边界，符合困难实习题。
- **覆盖差异**。H01 是泛型，H11 是流语义，H14 是加载身份；本题考模块反射权限。 **可接受表述**。接受 getter、定向 opens、临时 --add-opens 等合理解释。
- **发现及处理（P2，RESOLVED）**。原题未询问外部输入的反射边界，评分却分配 10 分。 题干补问外部可指定字段名时的访问限制。
- **依据**。[Java 21 Class · getDeclaredField](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getDeclaredField(java.lang.String)) · [Java 21 AccessibleObject · trySetAccessible / setAccessible](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AccessibleObject.html#trySetAccessible()) · [Java 21 Field · get](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html#get(java.lang.Object)) · [Java SE 21 语言规范 · §7.7.2 Exported and Opened Packages](https://docs.oracle.com/javase/specs/jls/se21/html/jls-7.html#jls-7.7.2)

**H11｜Stream 惰性执行与并行副作用**（JAVA_BASIC，5 点）

作者 `author_medium_hard_java`，独立审查者 `author_hard`，结论 **PASS**。

- **技术事实**。Java 21 Stream.count 可省略有已知数量的遍历，parallel forEach 无顺序保证，ArrayList 并发写有风险，toList 保留遇到顺序。 **版本口径**。Java 21 标准 Stream，来源为有序 List，不能泛化到任意源。
- **题干与答案**。两段故障分别解释，并提出保序生成和独立审计。 **评分点独立性**。遍历优化、共享状态与顺序、函数契约、改写、并行收益是不同层面的判断。
- **权重**。两处故障 50 分，约束与修复 40 分，性能取舍 10 分。 **难度**。要理解惰性优化和并行副作用，符合困难题。
- **覆盖差异**。H01 泛型、H10 反射与模块，M10 普通迭代，本题聚焦 Stream。 **可接受表述**。允许显式循环或安全且有序的流方案，按行为判分。
- **依据**。[Java 21 Stream · count、peek](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html#count()) · [Java 21 Stream · forEach / forEachOrdered / toList](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html#forEach(java.util.function.Consumer)) · [Java 21 Stream 包规范 · Non-interference / Stateless behaviors / Side-effects / Parallelism](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html) · [Java 21 ArrayList · 类说明](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html)

**H12｜并发 Map 中复合操作如何保持原子性**（JAVA_COLLECTION，5 点）

作者 `author_medium_hard_java`，独立审查者 `author_hard`，结论 **PASS**。

- **技术事实**。Java 21 ConcurrentHashMap 公开 API 支持单键 merge/compute 的原子性，computeIfAbsent 的 null 或异常不建立值并可能未来再算。 **版本口径**。Java 21 ConcurrentHashMap，未把该保证推广到任意 Map 默认实现。
- **题干与答案**。给出丢更新交错，修复计数，并比较首次放入和回调边界。 **评分点独立性**。竞争复现与原子修复各 1 点，首次初始化、回调副作用和跨键不变量各 1 点。
- **权重**。50 分给计数竞态和修复，40 分给首次写入与回调，10 分给跨边界限制。 **难度**。要求并发交错和 API 合同，符合困难。
- **覆盖差异**。H02 考 JDK 8 内部桶，H13 考迭代一致性，本题考单键复合操作。 **可接受表述**。接受等价同步或满足明确语义的计数器，避免要求唯一代码。
- **依据**。[Java 21 ConcurrentHashMap · putIfAbsent / computeIfAbsent / compute / merge](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

**H13｜快照迭代与弱一致迭代的取舍**（JAVA_COLLECTION，5 点）

作者 `author_medium_hard_java`，独立审查者 `author_hard`，结论 **PASS**。

- **技术事实**。Java 21 CopyOnWriteArrayList 迭代保存创建时数组引用，ConcurrentHashMap 迭代弱一致，不能由遍历推出全表同一时点。 **版本口径**。Java 21 两个具体容器的契约，避免推广到所有并发集合。
- **题干与答案**。分别覆盖两场景和成本，补问后直接回答精确统计如何改造。 **评分点独立性**。容器选择、元素引用边界、复制成本、弱一致、精确快照各自检查。
- **权重**。50 分给两种关键遍历契约，40 分给实际边界和成本，10 分给精确快照。 **难度**。基于读写比例和一致性契约做取舍，符合困难。
- **覆盖差异**。H02 考内部并发，H12 考原子单键修改，M10 考普通 fail-fast，本题考并发迭代。 **可接受表述**。接受不可变版本发布或锁协调的等价设计。
- **发现及处理（P2，RESOLVED）**。原题未询问精确统计，评分却为精确快照分配 10 分。 题干明确补问同一时点的准确结果。
- **依据**。[Java 21 CopyOnWriteArrayList · 类说明及 iterator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html) · [Java 21 ConcurrentHashMap · 类说明与 keySet/entrySet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

**H14｜类加载器隔离与类型身份**（JVM，5 点）

作者 `author_hard`，独立审查者 `author_easy_medium`，结论 **PASS**。

- **技术事实**。JVMS 21 §5.3 按二进制名称和定义加载器识别普通类型。宿主和插件分别定义同名接口时，类型转换失败可以由 Class 对象和定义加载器确证。 **版本口径**。限定 Java 21 普通类和接口。ServiceLoader.load(Class) 使用上下文类加载器，显式重载可换成可见实现的加载器。
- **题干与答案**。修订题干已单独追问 ServiceLoader，答案覆盖冲突定位、共享依赖组织和发现加载器。 **评分点独立性**。类型身份、共享 API 部署、发起与定义的区分、取证、SPI 分开。共享 API 部署与委派点分别看方案和原因。
- **权重**。身份机制与部署合计 55，定位证据 15，SPI 10，主要分数落在解释和修复。 **难度**。需用运行时类型身份解释插件故障，适合 HARD。
- **覆盖差异**。M12 考加载阶段，H03 考 GC，H15 考性能诊断。 **可接受表述**。不要求背 SPI 配置或代理实现。等价的 isAssignableFrom 检查可接受。
- **发现及处理**。已关闭：原题干未提示按 10 分计的 SPI 加载器，修订后有明确问题。
- **依据**。[JVMS 21：创建与加载 · §5.3](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3) · [Java 21 ClassLoader API · loadClass(String, boolean)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html#loadClass(java.lang.String,boolean)) · [Java 21 ServiceLoader API · load(Class) 与 load(Class, ClassLoader)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html#load(java.lang.Class))

**H15｜Java 服务高 CPU 与内存问题诊断**（JVM，5 点）

作者 `author_hard`，独立审查者 `author_easy_medium`，结论 **PASS**。

- **技术事实**。Java 21 HotSpot 诊断文档区分堆、元空间与本地资源类 OOM。高分配、回收后持续增长、线程 CPU 和对象保留链各自是不同证据。 **版本口径**。限定 HotSpot 21；JFR 和 jcmd 是举例，Java 语言规范不保证某个采集命令。
- **题干与答案**。答案按同一时间线区分三个现象，并给出可比较负载下的修复验证。 **评分点独立性**。CPU 归因、OOM 分类、存活量/分配量判别、引用链定位、复测结果互不替代。
- **权重**。证据归因及分配/保留区分共 50，符合诊断优先，未把命令记忆设为核心。 **难度**。要求根据多个现象选择证据并复测，属 HARD。
- **覆盖差异**。M13 考可达性，H03 考 G1 结构，本题考跨观测的故障定位。 **可接受表述**。允许可等价复现的分析器或监控方案，避免凭一次 GC 频率断定泄漏。
- **依据**。[JDK 21 排障指南：内存泄漏 · Understand the OutOfMemoryError Exception；Detect a Memory Leak](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshooting-memory-leaks.html) · [JDK 21 jcmd 手册 · Thread.print、JFR.start、GC.heap_dump](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html) · [JDK 21 java 手册 · Enable Logging with the JVM Unified Logging Framework](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html)

**H16｜线程池中的 ThreadLocal 残留与清理**（JAVA_CONCURRENCY，5 点）

作者 `author_hard`，独立审查者 `author_easy_medium`，结论 **PASS**。

- **技术事实**。Java 21 ThreadLocal 的值绑定线程。池内线程复用、异常跳过清理与后续请求读取共同导致残留。InheritableThreadLocal 只在新线程取得初值。 **版本口径**。明确复用的工作线程与应用持有的 ThreadLocal；嵌套场景按作用域恢复，避免把 inheritable 当任务传播。
- **题干与答案**。答案覆盖 A/B 时序、finally 清理和二次异步提交的传递或恢复。 **评分点独立性**。根因、清理、对象保留、跨线程传播、继承边界分开。
- **权重**。根因与清理 57，跨线程 20，继承细节 8，严重性体现于前两项。 **难度**。跨请求越权与异步边界的推演适合 HARD。
- **覆盖差异**。H04 考线程池容量，H17 考 Future 编排。 **可接受表述**。允许参数传递、装饰器和受控的上下文包装，无需特定框架。
- **依据**。[Java 21 ThreadLocal API · 类说明与 remove()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html) · [Java 21 ThreadPoolExecutor API · 类说明与 Hook methods](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html) · [Java 21 InheritableThreadLocal API · 类说明](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/InheritableThreadLocal.html)

**H17｜CompletableFuture 的编排、异常与超时**（JAVA_CONCURRENCY，5 点）

作者 `author_hard`，独立审查者 `author_easy_medium`，结论 **PASS**。

- **技术事实**。Java 21 CompletableFuture 支持 thenCompose 与 thenCombine。allOf 等所有阶段完成且无返回值集合，orTimeout 改变完成状态而不能保证终止底层 HTTP。 **版本口径**。Java 21 API。常见静态 async 工厂默认共享池，JDK 文档另列公共池并行度小于 2 时的备用线程。
- **题干与答案**。按题设并行与串行依赖编排，区分权限失败与可选配置、端到端预算和客户端超时。 **评分点独立性**。依赖编排、汇合、失败策略、执行器和截止时间各有观察目标。
- **权重**。编排与真实结果汇合合计 45，权限失败及超时各 20，符合实际故障影响。 **难度**。要在依赖图上兼顾异常、线程资源和时限，适合 HARD。
- **覆盖差异**。H04 考池容量，H25 考 HTTP 重放决策，本题考任务图和完成语义。 **可接受表述**。允许 thenCombine 或 allOf、受控 I/O 线程池或有限并发虚拟线程，不要求复述固定写法。
- **依据**。[Java 21 CompletableFuture API · 类说明、thenCompose、thenCombine、allOf、orTimeout、cancel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html) · [Java 21 HttpRequest.Builder API · timeout(Duration)](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpRequest.Builder.html#timeout(java.time.Duration))

**H18｜Spring 事务传播与保存点**（SPRING，5 点）

作者 `author_hard`，独立审查者 `author_easy_medium`，结论 **PASS**。

- **技术事实**。Spring 6.2 在已有物理事务上 REQUIRED 共享，NESTED 使用 JDBC 保存点，REQUIRES_NEW 使用独立事务，内层 rollback-only 可让外层提交报 UnexpectedRollbackException。 **版本口径**。限定命令式 Spring 6.2、代理调用、单数据源 JDBC 和保存点支持。
- **题干与答案**。三项业务要求分别对应三类传播，答案补上保存点外层回滚和连接池风险。 **评分点独立性**。三个传播级别为独立映射，rollback-only 与额外连接为两类独立失败机制。
- **权重**。传播选择共 68，失败后果和资源成本 32，符合题干。 **难度**。要求比较逻辑与物理事务并判断持久结果，适合 HARD。
- **覆盖差异**。H05 考代理和通用事务边界，H19 考提交后副作用。 **可接受表述**。允许用 TransactionTemplate 表达边界，无需背注解书写。
- **依据**。[Spring Framework 6.2 事务传播 · REQUIRED、REQUIRES_NEW、NESTED](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html) · [Spring Framework 6.2 声明式回滚 · Rolling Back a Declarative Transaction](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/rolling-back.html)

**H19｜事务提交后外部动作的失败恢复**（SPRING，5 点）

作者 `author_hard`，独立审查者 `author_easy_medium`，结论 **PASS**。

- **技术事实**。Spring 6.2 事务事件 AFTER_COMMIT 位于成功提交后。普通内存事件无法跨进程恢复。后置回调仍可能看到已参与但不会再提交的资源，独立确认应另开事务。 **版本口径**。Spring Framework 6.2 事务监听与 RFC 9110 重放约束，题设为通用订单场景。
- **题干与答案**。两个崩溃窗口都给出办法，分别是持久待办和稳定事件键或先查结果。 **评分点独立性**。事务事件时机、待办持久化、远端结果不确定、确认与重试、本地原子性边界分开。
- **权重**。窗口加持久待办 54，远端重放 21，体现最关键两类一致性。 **难度**。需要讲清跨系统时序和失败恢复，适合 HARD。
- **覆盖差异**。H25 考客户端重试合同，H27 考仓库现有报告状态机，本题考订单与通知待办同事务。 **可接受表述**。允许轻量 outbox、已有表状态或经远端查询恢复，不强制消息中间件。
- **依据**。[Spring Framework 6.2 事务事件 · Transaction-bound Events](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/event.html) · [Spring Framework 6.2 TransactionSynchronization API · afterCommit()](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html#afterCommit()) · [RFC 9110 HTTP 语义 · §9.2.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.2.2)

**H20｜InnoDB 快照读与锁定读的交互**（MYSQL，5 点）

作者 `author_hard`，独立审查者 `author_easy_medium`，结论 **PASS**。

- **技术事实**。MySQL 8.0 文档规定 RR 首次一致性读建立快照、RC 每次建立快照；锁定读读当前版本，事务可见自己的写入。该时序 RR 是 10/8/7，RC 是 8/8/7。 **版本口径**。明确 MySQL 8.0 InnoDB、显式事务、主键等值、B 已提交，排除 NOWAIT 等其他选择。
- **题干与答案**。修订已补问按当前库存更新。答案用锁定后检查或条件 UPDATE 给出操作方式。 **评分点独立性**。RR、RC、锁定读、自写可见、实际写入策略各自独立。
- **权重**。时序和解释 90，实务建议 10，符合推导为主。 **难度**。无需背内部字段，但要准确推导交错时序，适合 HARD。
- **覆盖差异**。H06 考 Read View 与 Undo 内部机制，H21 考事务错误恢复。 **可接受表述**。接受正确列出时间线与等价原子更新方案。锁等待只是支持论据。
- **发现及处理**。已关闭：原题干未提示按 10 分计的基于当前值写入，修订后补问。
- **依据**。[MySQL 8.0 InnoDB 一致性非锁定读 · §17.7.2.3](https://dev.mysql.com/doc/refman/8.0/en/innodb-consistent-read.html) · [MySQL 8.0 InnoDB 锁定读 · §17.7.2.4](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html)

**H21｜InnoDB 死锁与锁等待超时的恢复**（MYSQL，5 点）

作者 `author_hard`，独立审查者 `author_easy_medium`，结论 **PASS**。

- **技术事实**。MySQL 8.0 InnoDB 死锁回滚牺牲事务。innodb_rollback_on_timeout=OFF 时锁等待超时通常只回滚当前失败语句。 **版本口径**。MySQL 8.0，题设明确死锁检测和 timeout 回滚配置。SHOW ENGINE 与 performance_schema 证据用途准确。
- **题干与答案**。正确区分引擎动作与应用选择，转账为多语句业务，先整体回滚可防止遗留扣款被提交。 **评分点独立性**。错误回滚范围分两项，另考重试、预防和取证。
- **权重**。死锁和超时分别 25，重试 20，防止诊断细节压过资金语义。 **难度**。要求根据两种错误选择事务恢复，适合 HARD。
- **覆盖差异**。H06/H20 考读一致性，本题考写事务冲突和恢复。 **可接受表述**。接受带业务幂等的安全重试和等价观察工具，不要求背具体错误编号。
- **依据**。[MySQL 8.0 InnoDB 错误处理 · §17.21.5](https://dev.mysql.com/doc/refman/8.0/en/innodb-error-handling.html) · [MySQL 8.0 处理死锁 · §17.7.5.3](https://dev.mysql.com/doc/refman/8.0/en/innodb-deadlocks-handling.html) · [MySQL 8.0 Performance Schema data_lock_waits · data_lock_waits](https://dev.mysql.com/doc/refman/8.0/en/performance-schema-data-lock-waits-table.html)

**H22｜Cache-aside 的更新竞态与热点回源**（REDIS，5 点）

作者 `author_hard`，独立审查者 `author_easy_medium`，结论 **PASS**。

- **技术事实**。Redis cache-aside 参考和 Lua 原子操作支持答案。预先捕获代数、失效推进代数与原子条件回填能拒绝跨代迟到回填，数据库提交和 Redis 更新仍有窗口。 **版本口径**。限 Redis 常规单键条件能力和 MySQL 权威读；版本保护不承诺跨库强一致。
- **题干与答案**。两个独立故障分别给出旧值隔离和热点同键回源合并，提交与删除的时序已纠正。 **评分点独立性**。基本读写路径、旧值时序、保护选择、热点合并、跨实例边界分开。
- **权重**。两个核心合计 50，旧值处置与热点缓解合计 42，覆盖主要风险。 **难度**。需要同时推演读写时序和回源保护，适合 HARD。
- **覆盖差异**。M20 考过期和淘汰，H07 考租约锁，H23 考 Redis 原子机制。 **可接受表述**。允许按一致性需求使用 TTL、异步失效重试或直接读权威库等方案，不强制分布式锁。
- **发现及处理**。已关闭：前稿混淆提前删除与未提交值。修订说明提前删除可能使并发读拿到旧值，并补清版本保护的三个操作时点。
- **依据**。[Redis 官方 cache-aside 指南 · Why the problem is hard；Cache-aside flow](https://redis.io/docs/latest/develop/use-cases/cache-aside/) · [Redis 官方 EVAL 指南 · Executing Lua in Redis](https://redis.io/docs/latest/develop/programmability/eval-intro/)

**H23｜Redis Pipeline、事务与 Lua 的边界**（REDIS，5 点）

作者 `author_hard`，独立审查者 `author_easy_medium`，结论 **PASS**。

- **技术事实**。Redis Pipeline 只批量收发，WATCH 条件冲突会使 EXEC 放弃；事务排队错误和执行错误不同。Lua 执行不与其他命令交错，但运行时错误无自动回滚。 **版本口径**。题设限定 Redis 7.x 单实例，文档中的标准命令行为不混入 Cluster 跨槽。
- **题干与答案**。独立读和条件扣减有不同方案，错误影响分别说明排队、执行和脚本三种情形。 **评分点独立性**。传输批量、乐观检查、服务端脚本、事务错误和脚本错误各自独立。
- **权重**。Pipeline、WATCH、Lua 合计 68，错误边界 32，符合题意。 **难度**。要求权衡 RTT、并发交错和失败语义，适合 HARD。
- **覆盖差异**。H07 考租约互斥，H22 考缓存竞态，本题考原子性契约。 **可接受表述**。接受正确的客户端 WATCH 重试或短脚本，区分原子执行和数据库式回滚。
- **依据**。[Redis 官方 Pipeline 文档 · Redis pipelining；Pipelining vs Scripting](https://redis.io/docs/latest/develop/using-commands/pipelining/) · [Redis 官方事务文档 · Errors inside a transaction；Optimistic locking using check-and-set](https://redis.io/docs/latest/develop/using-commands/transactions/) · [Redis 官方 Lua 脚本文档 · Executing Lua in Redis；Interacting with Redis from a script](https://redis.io/docs/latest/develop/programmability/eval-intro/)

**H24｜TLS 1.3 的服务器认证与加密**（NETWORK，5 点）

作者 `author_hard`，独立审查者 `author_easy_medium`，结论 **PASS**。

- **技术事实**。TLS 1.3 常规证书握手需验证信任路径、参考域名和 CertificateVerify；临时密钥份额用于协商，Finished 验证握手，后续数据走 AEAD。 **版本口径**。RFC 8446 和 RFC 9525，限定证书完整握手，排除恢复和客户端证书。
- **题干与答案**。答案分别说明服务端身份、旁观者无法推导会话密钥以及应用用户认证。修订消除了对握手顺序的暗示。 **评分点独立性**。路径和主机匹配、私钥持有、密钥协商、完整性及记录保护、业务权限可独立判断。
- **权重**。认证与协商占 75，业务授权 10，权重覆盖三个问题。 **难度**。要求解释证书和密钥协商作用，未要求报文字段，适合 HARD。
- **覆盖差异**。H08 考 TCP 状态，E14 考 HTTP，H25 考请求重试。 **可接受表述**。接受口述密钥份额和共享秘密，不要求背 HKDF 或精确报文次序。
- **发现及处理**。已关闭：原稿按「先」描述证书身份校验，可能被读成握手先后关系，修订改为校验职责。
- **依据**。[RFC 8446：TLS 1.3 · §2、§4.4、§5.2、§7.1](https://www.rfc-editor.org/rfc/rfc8446.html#section-2) · [RFC 9525：TLS 服务身份 · §6](https://www.rfc-editor.org/rfc/rfc9525.html#section-6) · [RFC 5280：证书路径验证 · §6.1](https://www.rfc-editor.org/rfc/rfc5280.html#section-6.1)

**H25｜HTTP 超时后的重试与业务幂等**（NETWORK，5 点）

作者 `author_hard`，独立审查者 `author_easy_medium`，结论 **PASS**。

- **技术事实**。RFC 9110 §9.2.2 以预期服务端效果界定幂等。方法缺省非幂等时只在知道请求未执行或业务语义可幂等时重试。 **版本口径**。RFC 9110 语义无版本混淆，连接超时结论以证实当前尝试没发出为前提。
- **题干与答案**。答案分别处理连接尚未发送与读响应结果未知，再给 GET、PUT、POST 和服务端稳定键合同。 **评分点独立性**。失败位置、方法语义、客户端同键与载荷、服务端原子去重、预算各自可评分。
- **权重**。判断不确定性 26、方法语义 22、POST 合同及落库 42，重心准确。 **难度**。需把 HTTP 契约、数据库去重和网络不确定性合起来，适合 HARD。
- **覆盖差异**。E14 只考方法与状态码；H26 用本仓库的答案与评价状态；H19 考待办持久化。 **可接受表述**。接受先按业务键查询、同一 URI 的正确 PUT 或等价唯一业务合同，不要求逐字命名。
- **依据**。[RFC 9110：方法安全性与幂等性 · §9.2.1、§9.2.2、§9.3.3、§9.3.4](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.2) · [RFC 9110：Retry-After · §10.2.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3) · [Java 21 HttpConnectTimeoutException · 类说明](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpConnectTimeoutException.html)

**H26｜面试答案提交的归属、重放与并发**（PROJECT_SCENARIO，5 点）

作者 `author_hard`，独立审查者 `author_easy_medium`，结论 **PASS**。

- **技术事实**。HEAD 0c6f92a 的 InterviewService 先检查用户会话、题目归属再查 requestId。请求键及答案题目各有唯一约束。工作流的 attemptId 校验与状态 CAS 保护提交资格。 **版本口径**。项目版本以 Git HEAD 固定，依据 InterviewController、InterviewService、两个 Workflow Service 与答案约束。
- **题干与答案**。回答了 B 越权、A 同键同文、同键换文、并发冲突、完成重放与 EVALUATING 恢复。重放是按持久化评价与最新会话重建，并非逐字节原响应。 **评分点独立性**。归属、载荷绑定、短事务与竞争恢复、状态重放、评价尝试围栏分开。
- **权重**。越权和精确重放合计 48，数据库写入 22，重放和围栏 30，贴合项目风险。 **难度**。要求读懂认证、写入和异步尝试三个层次，适合 HARD。
- **覆盖差异**。H09 假设库存，H25 通用 HTTP 幂等，H27 是报告恢复。 **可接受表述**。可用资格令牌和单胜者解释，不要求背 DTO 或方法名。
- **依据**。[项目 JwtAuthenticationFilter · HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · doFilterInternal、authenticate](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/security/filter/JwtAuthenticationFilter.java) · [项目 InterviewController · HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · submitAnswer](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/controller/InterviewController.java) · [项目 InterviewService · HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · submitAnswer、loadOwnedSession、loadAndValidateQuestion、handleExistingAnswer、recoverFromDuplicateKey](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewService.java) · [项目 InterviewWorkflowService · HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · evaluateAnswer、claimOrRecoverEvaluation、recoverStaleEvaluating](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java) · [项目 InterviewWorkflowTransactionService · HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · submitAnswer、persistOwnedEvaluation、markEvaluationFailed](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java) · [项目隔离数据库基线 · HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · uk_interview_answer_question、uk_interview_answer_request](/Users/kun/Developer/projects/ai-interview-system/docs/environment/ISOLATED_SCHEMAS.sql)

**H27｜报告生成的过期接管与最终评价一致性**（PROJECT_SCENARIO，5 点）

作者 `author_hard`，独立审查者 `author_easy_medium`，结论 **PASS**。

- **技术事实**。HEAD 0c6f92a 的 InterviewReportService、事务服务和 InterviewSessionMapper 的生成接管按版本 CAS，重新争抢仅有新的 attemptVersion；已持久报告补 READY 前校验 FINAL 关联，弱项与新报告同事务。 **版本口径**。固定 HEAD，核对 generateReport、existingReportOrContinue、completeReportGeneration 以及 FINAL 查询和关联校验。
- **题干与答案**。覆盖主动发起生成、过期阈值、旧请求提交、汇总来源和防重复累计。READY 直接读已有报告，非 READY 恢复已有报告需要验证。 **评分点独立性**。FINAL 业务关联、状态与重试入口、版本围栏、原子写入、已有报告恢复各自可核对。
- **权重**。版本 25、报告和弱项原子性 24、FINAL 23，重点体现正确性。 **难度**。结合多个数据库状态和失效尝试推演，适合 HARD。
- **覆盖差异**。H26 用答案 attemptId，H27 用会话 version，H19 是通用副作用恢复。 **可接受表述**。允许图示或时序口述，阈值常量与状态枚举名无需背诵。
- **依据**。[项目 InterviewReportService · HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · generateReport、validateEvaluations、validateEvaluationAssociations、existingReportOrContinue、recoverReportFailure](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java) · [项目 InterviewSessionMapper · HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · claimReportGeneration、reclaimStaleGenerating、markReportReady、markReportFailed](/Users/kun/Developer/projects/ai-interview-system/src/main/resources/mapper/InterviewSessionMapper.xml) · [项目 InterviewReportTransactionService · HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · completeReportGeneration](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java) · [项目 AnswerEvaluationMapper · HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · listFinalEffectiveEvaluationsBySessionId](/Users/kun/Developer/projects/ai-interview-system/src/main/resources/mapper/AnswerEvaluationMapper.xml) · [项目隔离数据库基线 · HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · uk_interview_report_session](/Users/kun/Developer/projects/ai-interview-system/docs/environment/ISOLATED_SCHEMAS.sql)

## 原始审查记录

- `175a66a64bb4ff88b83f7607f4ce424de6837efcd2ec348b022cb12ab1b7fd37`。 [EASY_MEDIUM_INDEPENDENT_REVIEW_20261114.json](../question-bank/d1-drafts/EASY_MEDIUM_INDEPENDENT_REVIEW_20261114.json)
- `7f2aa20fd84e3fc6cf410c417816015382a1a183c52dde83f7781cecae56cdf1`。 [MEDIUM_HARD_JAVA_INDEPENDENT_REVIEW_20261114.json](../question-bank/d1-drafts/MEDIUM_HARD_JAVA_INDEPENDENT_REVIEW_20261114.json)
- `876271575ad317baeba2b61b63d3c0309170fbc76befe0bc3d470eb73afb5d90`。 [HARD_INDEPENDENT_REVIEW_20261114.json](../question-bank/d1-drafts/HARD_INDEPENDENT_REVIEW_20261114.json)
- `20600f0b9c8a16699de63203b2e4afbc4bf8d0ba853942fd8af425a04a4ce532`。 [H01_INDEPENDENT_REVIEW_20261114.json](../question-bank/d1-drafts/H01_INDEPENDENT_REVIEW_20261114.json)
