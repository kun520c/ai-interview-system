# Phase 1 External Question Bank Review

审查范围是已经写好的 21 道题，以及它们进入 Question、Scoring Point、Interview Snapshot、RAG、Jev、DeepSeek、Follow-up、Report、User Weakness 这条链路时的兼容性。本轮没有修改源码、数据库或题库数据，也没有生成入库 SQL。

## 1. Executive Summary

QUESTION_COUNT: 21
P0_COUNT: 0
P1_COUNT: 3
P2_COUNT: 7

TECHNICAL_CORRECTNESS: 通过。没有会把整题技术结论写反的错误；少量版本边界表述见 P2。
DIFFICULTY_CONSISTENCY: 通过。五道 EASY、七道 MEDIUM、九道 HARD 与当前面试计划一一对应，纵向难度没有倒置。
REFERENCE_ANSWER_QUALITY: 通过。答案整体处在优秀实习生 1 到 3 分钟口述的范围内；E05、H05 有两处过宽表述。
SCORING_POINT_QUALITY: 入库前需要修改。21 题权重和全部为 100，类型也都能落进枚举；M03、M05、H09 的判定口径会误伤正常回答。
JEV_COMPATIBILITY: 入库前需要修改。Jev 对每个评分点只做一次 covered / uncovered，FollowUpPolicy 又只把未覆盖的 CORE 当作独立追问条件。
RAG_COMPATIBILITY: 通过。评分点总体自包含，关键词稳定；H09 有一处轻度主题混入，不阻断。
KNOWLEDGE_POINT_STABILITY: 通过。21 个 knowledgePoint 都可以保留。
MULTI_SOLUTION_FAIRNESS: H04、H05、H07 可以通过；H09 的 CORE 把合格的悲观锁方案关在门外，入库前要改。

PHASE_1_EXTERNAL_REVIEW: PASS_WITH_FIXES

## 2. Repository Evidence

| Path | 为什么相关 | 从源码确认的约束 |
| --- | --- | --- |
| `sql/ai_interview.sql` | 题目、评分点、弱项的真实列和检查约束 | `question.knowledge_point varchar(100)`；`question_content` / `reference_answer` / `scoring point content` 都是 `text`；`point_type` 只能是 `CORE/KEY/PRINCIPLE/INTERNAL/ADVANCED`；`weight` 为 `tinyint unsigned` 且 `between 1 and 100`；`user_weakness` 的唯一键是 `(user_id, category, knowledge_point)`。21 题的分类、难度、知识点、评分点类型和权重都能放进这些约束。 |
| `question/enums/QuestionCategory.java` `QuestionDifficulty.java` `QuestionPointType.java` | 题库枚举 | 九个分类、三个难度、五个评分点类型与题目草稿一致。 |
| `question/dto/CreateQuestionRequest.java` `ScoringPointRequest.java` | 入库校验 | 知识点 `@Size(max = 100)`；题目、参考答案、评分点内容受 MySQL TEXT 的 65535 UTF-8 字节限制；每题至少一个评分点；单个权重 1 到 100。现有草稿远小于这些上限。 |
| `question/service/QuestionAdminService.java` | 权重业务规则 | 创建和整题替换都要求启用评分点权重之和严格等于 100。21 题均已满足。 |
| `interview/config/InterviewPlanConfig.java` | 第一版为什么是 21 题 | EASY 5 类、MEDIUM 7 类、HARD 9 类，`plannedQuestionCount` 与 `requiredCategories` 数量相同。EASY 不含 JVM、并发、Redis、项目场景；MEDIUM 不含网络和项目场景；HARD 九类都要。当前草稿与这个计划对齐。 |
| `interview/service/QuestionPlanSelector.java` | 固定题集如何被抽中 | 先按必需分类各取一道，再按稳定顺序补满计划数。分类数等于计划数时，每个难度只会抽到每个必需分类的第一道启用题。 |
| `interview/service/InterviewQuestionSnapshotFactory.java` | 快照再次校验权重 | 启用评分点权重合计不是 100 时，创建面试会直接失败。快照保存 `id`、`pointType`、`content`、`weight`。 |
| `interview/evaluation/ScoringPointSnapshot.java` | JSON 字段 | 序列化字段名是 `id`，并兼容历史名 `scoringPointId`。评分点正文会原样进入快照。 |
| `interview/evaluation/EvaluationRagQueryBuilder.java` | RAG 查询如何构成 | 主回答查询是“主问题 + 全部评分点 content”。候选人回答不进入查询。追问查询是“主问题 + 追问 + 被点名的评分点 content”。 |
| `interview/orchestration/EvaluationOrchestrationService.java` | 检索宽度 | `RETRIEVAL_TOP_K = 5`。一条查询的主题一旦被无关关键词带偏，五个证据位里就可能混入别的主题。 |
| `interview/evaluation/prompt/EvaluationPromptBuilder.java` | 参考答案和权重会不会进入评价 | 用户提示包含参考答案、评分点类型、权重和正文。系统提示要求五个维度各 0 到 20，并明确不要返回最终 `totalScore`。 |
| `interview/evaluation/judge/jev/JevJudgeProvider.java` | covered 的判定方式 | 每个评分点单独成为一个 choice：`covered` 或 `uncovered`。标准是“回答中有正确且足以支持该评分点的内容”。五个维度各是 0 到 4 的等级分，再映射成 0 到 20。证据必须是候选人原文。 |
| `interview/evaluation/score/EvaluationScoreCalculator.java` | 权重是不是分数 | `totalScore = correctness + completeness + depth + clarity + practice`。评分点权重不参与相加。 |
| `interview/evaluation/standard/EvaluationStandard.java` | 弱项阈值 | `>= 80` 为 GOOD / EXCELLENT，会解析已有弱项；低于 80 会记为发现弱项。 |
| `interview/evaluation/decision/FollowUpPolicy.java` | CORE 的运行时含义 | 只有 Jev 建议追问，并且 `totalScore < 60` 或存在未覆盖 CORE 时，Java 才真正追问。权重再高，只要类型不是 CORE，都不能单独打开这条门。 |
| `interview/evaluation/decision/FollowUpTargetResolver.java` | 追问打到哪一个点 | 最多两个目标。先取未覆盖 CORE，再按快照顺序取其他未覆盖点。 |
| `user/service/UserWeaknessService.java` `UserWeaknessMapper.xml` | 知识点是否适合长期聚合 | 按 `(category, knowledgePoint)` 聚合本次面试的最终评价。MySQL `utf8mb4_unicode_ci` 下唯一键按字符串匹配。知识点改名会把同一能力拆成两条弱项。 |
| `interview/service/InterviewWorkflowTransactionService.java` | 追问题是否换知识点 | FOLLOW_UP 复制父 MAIN 的 `category` 和 `knowledgePoint`。弱项键仍然落在原题知识点上。 |

据此核对的系统事实：

- 评分点权重是评价提示里的重要程度，不是“覆盖该点就加对应分数”。
- 一个点如果塞进多个独立事实，Jev 只能整点 covered 或整点 uncovered。
- 题干真正要考的能力如果没有标成 CORE，候选人总分已经达到 60 时，Java 不会仅因为这个点未覆盖而追问。
- 21 个知识点互不重复，长度都在 100 以内，没有和数据库约束冲突的字段。

## 3. P0 Findings

NONE

## 4. P1 Findings

### P1-01

ID: P1-01
Question ID: M03
Issue: CORE 评分点把六个运行时区域绑成一次二元判断。
Why: Jev 对一个评分点只返回 covered 或 uncovered。该点要求同时说出 Heap、Method Area、Java Virtual Machine Stack、PC Register，并且还要知道 Runtime Constant Pool 和 Native Method Stack。一名实习生完全可以正确回答题干：堆、方法区、虚拟机栈、程序计数器，对象在堆、局部变量在栈帧、类元数据在方法区，本地方法栈私有。只要没点名运行时常量池，这一条 25 分权重的 CORE 仍会整点未覆盖。未覆盖 CORE 会让 `FollowUpPolicy` 在总分已经不低时仍然追问。
Repository / authoritative evidence: `JevJudgeProvider.buildRequest()` 为每个评分点生成一个 covered/uncovered choice。`FollowUpPolicy.hasUncoveredCorePoint()` 只检查 `QuestionPointType.CORE`。JVMS 第 2.5 节确实把 pc、JVM Stack、Heap、Method Area、Run-Time Constant Pool、Native Method Stack 都列为运行时数据区，所以参考答案把它们写全是对的；错误在于六个独立名词共用一个二元评分点。
Exact recommended change: 保持本题五个点、权重和仍为 100，改成下面的判定口径。

1. CORE / 20：能说出堆、方法区、Java 虚拟机栈和程序计数器。接受“虚拟机栈、程序计数器、方法区”等中文同义说法。
2. KEY / 20：能说明程序计数器、Java 虚拟机栈和本地方法栈按线程创建，堆和方法区由各线程共享。
3. KEY / 25：维持原第 3 点，普通对象通常在堆，局部变量在栈帧局部变量表，引用和对象本身不是同一块存储。
4. PRINCIPLE / 20：维持原第 4 点，方法调用通过栈帧执行，栈帧包含局部变量和操作数栈。
5. ADVANCED / 15：能区分 JVM 规范中的方法区和 HotSpot Metaspace，并知道运行时常量池是按类保存的规范数据。说不出 Metaspace 或常量池，不影响前四个点。

### P1-02

ID: P1-02
Question ID: M05
Issue: 题干真正要追问的自调用失效不是 CORE，基础代理定义才是 CORE。
Why: 题目后半句问的是同类内部调用 `@Transactional` 为什么可能失效。这个点目前是 PRINCIPLE，权重 35，内容本身是对的。基础句“Spring AOP 通过代理拦截调用”才是 CORE。候选人讲清代理、JDK/CGLIB 和拆分 Service，但没解释 `this` 不经过代理时，CORE 仍然是 covered。此时只要五维总分达到 60，`FollowUpPolicy` 不会追问，尽管主问题没有回答。权重不会补上这个缺口，因为 Java 计算总分时不读取权重。
Repository / authoritative evidence: `FollowUpPolicy.shouldFollowUp()` 的条件是 `followUpRecommended` 为真，且 `totalScore < 60` 或存在未覆盖 CORE。`EvaluationScoreCalculator` 只把五个维度相加。Spring Framework 文档 *Understanding AOP Proxies* 说明目标对象内部的 `this.bar()` 打在目标对象上，advice 不会执行。评分点正文符合这份文档，需要改的是 `pointType`。
Exact recommended change: 第 3 点保持原文和权重 35，只把 `pointType` 从 `PRINCIPLE` 改为 `CORE`。第 1 点可以继续保持 CORE。这样漏掉自调用机制时，高分回答仍然满足 `hasUncoveredCorePoint`。不要把 JDK 动态代理和 CGLIB 也升成 CORE。

### P1-03

ID: P1-03
Question ID: H09
Issue: CORE 把防超卖收成条件更新和乐观锁，事务内悲观行锁会被判成未覆盖。
Why: 参考答案给出的 `UPDATE ... SET stock = stock - 1 WHERE id = ? AND stock > 0`，以及带版本号的乐观锁，都是正确方案。另一类同样正确的方案是：在一个事务里 `SELECT ... FOR UPDATE` 锁住库存行，确认库存大于 0 后再扣减。它满足“检查和扣减不能被并发请求拆开”，但不是评分点所写的“原子条件更新或等价的乐观锁/CAS”。Jev 面对这条 CORE 时，很容易把只回答悲观锁的正确设计判成 uncovered。该点权重 30，而且是 CORE，会直接推动追问和完整度判断。
Repository / authoritative evidence: MySQL 8.0 Reference Manual 17.7.2.4 *Locking Reads*：`SELECT ... FOR UPDATE` 对读到的行加锁，语义与对这些行执行 `UPDATE` 相同，其他事务不能同时改这些行。题目要求的是不超卖、不重复扣减和可恢复重试，没有规定只能使用乐观并发控制。
Exact recommended change: 将 CORE 点改为：

“能把库存检查和扣减放进同一次原子数据库操作，或同一个事务的行锁临界区，避免无锁地先查出库存、在 Java 中判断大于 0 后再单独更新。条件更新并检查更新行数、带版本号的乐观锁，以及事务中的 `SELECT ... FOR UPDATE` 后扣减，说出其中任意一种正确方案即覆盖。”

权重保持 30。参考答案补一句即可，不必重写整题：事务中先 `SELECT ... FOR UPDATE` 再扣减同样成立；没有行锁的先查后改不成立。第 2 到第 4 点已经用“或等价机制”容纳了多种幂等、事务和重试设计，保持不动。

## 5. P2 Findings

### P2-01

ID: P2-01
Question ID: M02
Issue: “桶节点达到 8 就尝试树化”比 OpenJDK 的触发时机略早，容易把更精确的回答判窄。
Why: `TREEIFY_THRESHOLD` 的值确实是 8，容量小于 64 时 `treeifyBin` 改为 `resize`，这两点都对。OpenJDK 的正式注释是：向一个已经至少有 8 个节点的桶再插入元素时，才把该桶转成树。`putVal` 的条件是 `binCount >= TREEIFY_THRESHOLD - 1`。现在的参考答案把“不是一到 8 就树化”的唯一例外写成容量，读者会理解成容量已经达到 64 且节点数达到 8 时就树化。
Repository / authoritative evidence: OpenJDK `java.util.HashMap`，JDK 8 至当前主干同一注释：`Bins are converted to trees when adding an element to a bin with at least this many nodes.` `TREEIFY_THRESHOLD = 8`，`MIN_TREEIFY_CAPACITY = 64`。
Exact recommended change: 参考答案和第 4 点改成：“向已经至少有 8 个节点的桶再插入时尝试树化；table 容量小于 64 时优先 resize。”评测时接受“阈值是 8，并且容量至少 64”。不要改成必须背第 8 个还是第 9 个节点，那会把 MEDIUM 题变成源码行号题。

### P2-02

ID: P2-02
Question ID: M02
Issue: 第 2 点和第 3 点重复判断“相同 key 更新 value”。
Why: 第 2 点的结论是相同 key 更新已有 value。第 3 点的路径末尾又要求“已有 key 则更新 value”。候选人用一段完整 put 路径就会同时覆盖两点；Jev 也会把同一次表述记到两个点上。两点仍有各自的信息，所以不是 P1。
Repository / authoritative evidence: 两个评分点的原文存在包含关系。Jev 按点独立判断，不会因为两点语义重叠而合并。
Exact recommended change: 第 3 点删去“已有 key 则更新 value”，只保留初始化或扩容、定位桶、空桶插入、链表或红黑树中的查找和插入。相同 key 的更新语义只留在第 2 点。权重不变。

### P2-03

ID: P2-03
Question ID: E05
Issue: 参考答案把 QUIC 和“应用层自己处理可靠性”写在同一类里。
Why: UDP 之上可以由应用自己补可靠性，这个评分点是对的。QUIC 则是运行在 UDP 之上的传输协议，可靠性和拥塞控制由 QUIC 提供，不是由业务应用临时补上，也不是 UDP 本身提供。参考答案会进入评价提示。候选人如果正确说 QUIC 是基于 UDP 的传输协议，有可能被参考答案里的“应用层”带偏。
Repository / authoritative evidence: RFC 9000 的标题是 *QUIC: A UDP-Based Multiplexed and Secure Transport Protocol*。评分点第 4 点没有出现 QUIC，问题出在参考答案的例子。
Exact recommended change: 把相应句子改为：“实时音视频和部分 DNS 可以使用 UDP。QUIC 是基于 UDP 的传输协议，可靠性与拥塞控制由 QUIC 提供，而不是由 UDP 提供。”评分点不用改。

### P2-04

ID: P2-04
Question ID: H01
Issue: 一个 KEY 点并列了四项擦除限制。
Why: 该点权重 25，正文用“例如”连接 `new T`、`instanceof List<String>`、泛型数组和擦除后签名相同的重载。语义上举出典型限制就该覆盖；模型很容易把它读成四项清单。候选人清楚说明擦除，并正确举出其中两项，仍可能整点未覆盖。
Repository / authoritative evidence: `JevJudgeProvider` 不能给一个评分点打部分覆盖。JLS 的擦除规则和编译器对 generic array creation、instanceof 参数化类型、same-erasure 重载的限制都是事实，参考答案没有写错。
Exact recommended change: 改为“能举出至少两项擦除带来的典型限制，例如不能直接 `new T()`、不能 `instanceof List<String>`、不能直接创建泛型数组，或不能仅靠擦除后相同的签名重载。”权重和类型保持 KEY / 25。

### P2-05

ID: P2-05
Question ID: H02
Issue: ADVANCED 点把 `ForwardingNode` 和 `helpTransfer` 写成了覆盖条件的一部分。
Why: 协作迁移是对的，也适合作为加分机制。题干问的是 CAS、`synchronized` 和桶结构，没有要求 JDK 私有类名或方法名。候选人说清“扩容时其他线程可以通过转发节点一起搬迁桶”，却没有背出这两个标识符时，可能被判未覆盖。
Repository / authoritative evidence: OpenJDK `ConcurrentHashMap` 在 JDK 8 及之后使用 `ForwardingNode` 和 `helpTransfer` 做多线程迁移；这是实现名称，不是题干要求的结论。该点权重只有 10，所以不阻断冻结。
Exact recommended change: 改为“能说明扩容时多个线程可以协作搬迁桶，例如通过转发节点继续帮助迁移。说出协作行为即覆盖，不要求背出类名或方法名。”

### P2-06

ID: P2-06
Question ID: H05
Issue: 参考答案把 checked exception 的默认回滚行为写成“不一定回滚”。
Why: Spring 的默认规则是确定的：`RuntimeException` 和 `Error` 回滚，checked exception 不回滚，除非配置了 rollback rule。第 2 个评分点已经写成这个确定规则。参考答案的“不一定”更弱，评价模型可能接受含糊回答。
Repository / authoritative evidence: Spring Framework 文档 *Rolling Back a Declarative Transaction*，以及 `@Transactional` 的 JavaDoc：默认只在 unchecked exception 和 `Error` 上回滚，checked exception 在默认配置下不回滚。
Exact recommended change: 参考答案改为“checked exception 默认不回滚；业务需要时显式配置 `rollbackFor`。”评分点第 2 点不用改。

### P2-07

ID: P2-07
Question ID: H09
Issue: 第 5 点要求讨论 Redis 锁，题干没有要求；这句话还会进入 RAG 查询。
Why: 完全基于 MySQL 原子更新、唯一幂等键和事务的回答，已经覆盖防超卖、防重复扣减、事务边界和重试。第 5 点仍要求说明 Redis 锁不能替代数据库约束。Jev 会因为回答里根本没有 Redis 而判未覆盖。`EvaluationRagQueryBuilder` 会把该句和库存、幂等键拼进同一次向量查询，`topK` 只有 5，存在把证据带向 Redis 锁文档的轻度偏移。
Repository / authoritative evidence: 题干只给定“库存最终以 MySQL 为事实来源”。RAG 主查询包含全部评分点正文，见 `EvaluationRagQueryBuilder.buildMainQuery()`。
Exact recommended change: 改为“如果方案使用 Redis 锁，能说明它只适合降低竞争，不能替代数据库里的原子库存条件和持久化幂等；完全不使用 Redis 的正确数据库方案也覆盖本点。”权重保持 ADVANCED / 10。

## 6. Per-Question Audit

### E01

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: PASS
JEV: PASS
RAG: PASS

Notes: 接口与抽象类的定位、单继承和多实现、实例状态、default/static 方法都准确。第 3 点同时包含状态、构造过程和具体方法，但都属于同一组差异，一名正常回答可以一起说清。四个点的检索词都落在接口、抽象类和继承上。

### E02

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: PASS
JEV: PASS
RAG: PASS

Notes: `ArrayList.get` 为 O(1)、`LinkedList` 按下标访问为 O(n)、定位后的链表改链成本低，这些表述符合 Java `ArrayList` 与双向 `LinkedList`。第 4 点拒绝“增删多就用 LinkedList”，口径适合 EASY。

### E03

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: PASS
JEV: PASS
RAG: PASS

Notes: IoC、DI、容器生命周期和构造器注入的分工清楚。DI 写成 IoC 的一种实现方式，与 Spring 文档把 IoC 和 dependency injection 放在一起的用法相容。知识点 `ioc_di` 适合长期复用。

### E04

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: PASS
JEV: PASS
RAG: PASS

Notes: 原子性、约束和业务不变量、隔离级别、持久性、逻辑工作单元各自独立，适合二元判断。只有原子性是 CORE；漏掉一致性或隔离性时，仍可由低总分打开追问。五个点权重都是 20，和 ACID 四个定义加一个用途的结构匹配。

### E05

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: ISSUE
SCORING_POINTS: PASS
JEV: PASS
RAG: PASS

Notes: TCP 字节流、序号、重传、流量控制和拥塞控制，以及 UDP 数据报本身不保证到达和顺序，符合 RFC 9293 与 RFC 768。见 P2-03：参考答案里的 QUIC 例子过宽。评分点没有把 QUIC 写进查询，RAG 主题仍然是 TCP 与 UDP。

### M01

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: PASS
JEV: PASS
RAG: PASS

Notes: `equals` 为真则 `hashCode` 必须相同、反过来不必成立，以及 HashMap 先按 hash 定位再用 `equals` 比较，符合 `Object` 的约定和 JDK `HashMap`。MEDIUM 要求解释一层机制，本题刚好停在这个深度。

### M02

TECHNICAL: ISSUE
DIFFICULTY: PASS
REFERENCE_ANSWER: ISSUE
SCORING_POINTS: ISSUE
JEV: ISSUE
RAG: PASS

Notes: 桶数组、链表、红黑树、相同 key 覆盖、负载因子 0.75 和容量 64 的主线正确，难度也适合 MEDIUM。见 P2-01 和 P2-02：树化时机少了一层“已有 8 个节点后再插入”，第 2 点与第 3 点有重叠。常量名有利于检索，查询主题没有漂到别的集合。

### M03

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: ISSUE
JEV: ISSUE
RAG: PASS

Notes: 参考答案对规范区域、线程私有和共享、对象与局部变量、栈帧、方法区与 Metaspace 的区分是准确的，MEDIUM 也合适。见 P1-01：问题在 CORE 点的打包方式，不在技术事实。Heap、Stack、Method Area、Metaspace 这些词对检索是稳定的。

### M04

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: PASS
JEV: PASS
RAG: PASS

Notes: volatile 写与后续读之间的 happens-before、`count++` 的读改写丢失更新，以及它不能替代锁或 `AtomicInteger`，符合 JLS 内存模型。happens-before 是本题该有的机制层，不是多余术语。`AtomicInteger` 和锁出现在同一点里，但是作为替代方案，不会把检索从 volatile 带走。

### M05

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: ISSUE
JEV: ISSUE
RAG: PASS

Notes: 代理、JDK 动态代理、CGLIB、自调用绕过 advice，符合 Spring AOP 代理模型。第 4 点用“例如重新划分 Service”，自注入或暴露代理等合理修法仍能覆盖。见 P1-02：要改的是自调用点的类型。查询词集中在 Spring AOP、proxy、`@Transactional`，和题目一致。

### M06

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: PASS
JEV: PASS
RAG: PASS

Notes: “B-tree 家族、面试中常称 B+Tree”避开了 MySQL 手册写 B-tree、实现是 B+Tree 变体的版本争议。聚簇索引通常由主键形成、二级索引叶子保存索引列和主键、回表与覆盖索引的关系准确。五个点各自对应一个判断，适合 Jev。

### M07

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: PASS
JEV: PASS
RAG: PASS

Notes: String、Hash、List、Set、Sorted Set 的用途匹配 Redis 数据模型，并明确五种不是全部类型。对象缓存同时可以是 String 序列化或 Hash 字段存储；参考答案强调按操作选择，评分点没有把 Hash 写成唯一答案。它是七道 MEDIUM 里最偏模型选择的一题，仍然要求说明原因，没有降到 EASY 的程度。

### H01

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: ISSUE
JEV: ISSUE
RAG: PASS

Notes: 擦除到 bound 或 `Object`、`List<String>` 与 `List<Integer>` 共享原始类、bridge method，以及 Signature 属性仍可被反射读到，这些边界写得仔细。HARD 停在编译器机制和限制，没有要求背私有字段。见 P2-04：四项限制需要改成“至少两项”。

### H02

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: ISSUE
JEV: ISSUE
RAG: PASS

Notes: JDK 8 取消 Segment、空桶 CAS、桶头 `synchronized`、普通 get 通常不走同一把桶锁，都符合 `ConcurrentHashMap`。题目点名 JDK 8 是合适的，知识点没有绑死小版本。见 P2-05：最后一点的类名和方法名过细。前四个点的检索词已经足够定位到这张表。

### H03

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: PASS
JEV: PASS
RAG: PASS

Notes: 等大 Region、按回收收益和预计成本选择 Collection Set、Young GC 复制存活对象、并发标记后 Mixed GC 同时回收年轻代和部分老年代，以及 G1 仍有 Stop-The-World，符合 Oracle G1 调优文档。没有要求 IHOP、Humongous 或 JVM 参数名。`g1_gc` 作为这一题的长期弱项键是稳定的。

### H04

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: PASS
JEV: PASS
RAG: PASS

Notes: core、队列、maximum、拒绝策略的顺序符合 `ThreadPoolExecutor.execute` 的 JavaDoc。评分点要求的是有界队列、不静默丢弃、按工作负载定线程数、监控和压测，没有规定唯一的线程数或唯一拒绝策略。`CallerRuns`、持久化和明确失败后的幂等重试都可以覆盖。这是设计题，不是参数背诵。

### H05

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: ISSUE
SCORING_POINTS: PASS
JEV: PASS
RAG: PASS

Notes: 自调用、默认回滚规则、吞异常、事务边界、远程调用和跨资源一致性都在题意内。评分点使用“缩短事务或消息、补偿等方案”，XA 或其他有边界的一致性设计没有被写成错误。见 P2-06：参考答案的“不一定回滚”弱于 Spring 的确定默认值。和 M05 的重叠是从代理机制走到事务设计，难度没有倒置。

### H06

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: PASS
JEV: PASS
RAG: PASS

Notes: REPEATABLE READ 下同一事务的普通一致性读使用第一次 consistent read 建立的快照，符合 MySQL 8.0 手册 17.7.2.3。Undo、事务 ID 和 Read View 的分工清楚，并把 `SELECT ... FOR UPDATE` 从快照读里分开，没有把 MVCC 说成可以单独消灭所有幻读。五个点分别对应版本、Undo、可见性、可重复读和当前读，是本卷里最适合 RAG 与 Jev 的一题。

### H07

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: PASS
JEV: PASS
RAG: PASS

Notes: 一次 `SET key token NX PX ttl`、唯一 token、原子校验后删除，符合 Redis 官方分布式锁文档。第 3 点写的是“例如 Lua”；Redis 8.4 的 `DELEX key IFEQ token` 仍能覆盖。续租、fencing 和数据库最终约束都是可选加强，不是唯一公式。查询词集中在 SET NX、token、TTL 和锁释放上。

### H08

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: PASS
JEV: PASS
RAG: PASS

Notes: 三次握手交换并确认初始序列号、全双工下双方各自关闭、ACK 与 FIN 有时可以合并、TIME_WAIT 位于主动关闭方、CLOSE_WAIT 表示应用尚未关闭，符合 RFC 9293 的连接状态机。大量 TIME_WAIT 不直接等同于缺陷，这个运维判断是 HARD 该有的边界。五个点各问一件事。

### H09

TECHNICAL: PASS
DIFFICULTY: PASS
REFERENCE_ANSWER: PASS
SCORING_POINTS: ISSUE
JEV: ISSUE
RAG: ISSUE

Notes: 条件更新、乐观锁、幂等键、超时不等于失败、重试沿用原键，这些设计都成立，参考答案也是一份可口述的合格方案。见 P1-03 和 P2-07：CORE 需要接纳悲观行锁；Redis 点不应惩罚纯数据库方案，也不应把检索从库存一致性拉开。

## 7. knowledgePoint Audit

弱项键来自面试快照上的 `(category, knowledgePoint)`，并写入 `user_weakness` 的唯一键。下面 21 个名字都短于 100，彼此不重复，也不绑定 JDK 小版本。默认保留。

| Question ID | Category | knowledgePoint | Verdict | reason |
| --- | --- | --- | --- | --- |
| E01 | JAVA_BASIC | `interface_vs_abstract_class` | KEEP | 稳定的语言设计主题，后续同类题可以复用。 |
| E02 | JAVA_COLLECTION | `arraylist_vs_linkedlist` | KEEP | 对应两种具体 List 实现的稳定对比，没有绑题干措辞。 |
| E03 | SPRING | `ioc_di` | KEEP | 容器基础能力的长期名称。 |
| E04 | MYSQL | `transaction_acid` | KEEP | 与 H05 的 `transaction_boundary` 分属 MYSQL 和 SPRING，唯一键不会误并。 |
| E05 | NETWORK | `tcp_vs_udp` | KEEP | 传输协议选择，和 H08 的连接生命周期分开。 |
| M01 | JAVA_BASIC | `equals_hashcode_contract` | KEEP | 契约本身是长期能力，不是某一道题的临时句子。 |
| M02 | JAVA_COLLECTION | `hashmap_put_resize` | KEEP | 覆盖 HashMap 写入和扩容，题目里的 JDK 8 没有写进键。 |
| M03 | JVM | `runtime_data_areas` | KEEP | 规范级区域划分，可继续用于后续 JVM 内存题。 |
| M04 | JAVA_CONCURRENCY | `volatile_jmm` | KEEP | 可见性和内存顺序的稳定主题。 |
| M05 | SPRING | `spring_aop_proxy` | KEEP | 代理模型可复用；事务边界已由 H05 另键记录。 |
| M06 | MYSQL | `innodb_index_structure` | KEEP | InnoDB 索引结构的长期名称，没有绑某一条 SQL。 |
| M07 | REDIS | `redis_data_structures` | KEEP | 对本题的五种通用结构足够稳定；现在没有更窄的第二道结构题需要拆开。 |
| H01 | JAVA_BASIC | `generic_type_erasure` | KEEP | 擦除是长期语言机制，不是编译器私有字段。 |
| H02 | JAVA_COLLECTION | `concurrent_hashmap` | KEEP | JDK 8 的行为写在题干里；键保留到后续 JDK 仍适用。 |
| H03 | JVM | `g1_gc` | KEEP | 本题考的就是 G1。将来如果增加别的收集器，应使用新键，而不是把本题改成笼统的 GC。 |
| H04 | JAVA_CONCURRENCY | `thread_pool_design` | KEEP | 线程池容量、队列和拒绝策略可以长期复用。 |
| H05 | SPRING | `transaction_boundary` | KEEP | 事务边界和回滚策略的稳定名称。 |
| H06 | MYSQL | `mvcc_read_view` | KEEP | InnoDB 一致性读的稳定机制名，没有写入某个小版本。 |
| H07 | REDIS | `distributed_lock` | KEEP | 与 H09 的项目场景键分属不同 category，不会合成一条弱项。 |
| H08 | NETWORK | `tcp_connection_lifecycle` | KEEP | 握手、挥手和两个等待状态是同一长期主题。 |
| H09 | PROJECT_SCENARIO | `concurrent_inventory_idempotency` | KEEP | 名字带库存场景，但这是当前唯一的项目场景题，category 已经把它和其他并发题分开。第一版不需要为了“看起来更抽象”而改名。 |

## 8. Difficulty Matrix Audit

计划与题量：

| Difficulty | 计划题量 | 必需分类 | 当前题目 |
| --- | --- | --- | --- |
| EASY | 5 | JAVA_BASIC、JAVA_COLLECTION、SPRING、MYSQL、NETWORK | E01–E05 |
| MEDIUM | 7 | 上述去掉 NETWORK，加上 JVM、JAVA_CONCURRENCY、REDIS | M01–M07 |
| HARD | 9 | 九个分类全部必需 | H01–H09 |

每个难度的题数等于必需分类数。以现有 `QuestionPlanSelector`，第一版每个难度、每个必需分类保留一道启用题，就能得到稳定题集。本轮不建议为了补齐空档而加题。

纵向梯度：

- JAVA_BASIC：接口与抽象类，到 equals/hashCode 契约，再到泛型擦除。从概念到机制，顺序合理。
- JAVA_COLLECTION：ArrayList/LinkedList，到 HashMap put 与树化，再到 ConcurrentHashMap。M02 信息密度高，仍然是机制题；H02 才进入并发边界。
- JVM：运行时数据区到 G1。计划里没有 EASY JVM。两题分别对应地图和收集器设计。
- JAVA_CONCURRENCY：volatile 到线程池设计。计划里没有 EASY。前者是内存模型，后者是容量和失败策略。
- SPRING：IoC/DI，到 AOP 代理，再到事务边界。M05 与 H05 都提到自调用，后者还要求回滚、事务范围和跨资源一致性，难度没有倒挂。
- MYSQL：ACID，到 B+Tree 与回表，再到 MVCC Read View。这是九个分类里最整齐的一条线。
- REDIS：数据结构选择到分布式锁。M07 是中等题里最接近“按模型选型”的一题，仍然要求说明原因。计划里没有 EASY Redis。
- NETWORK：TCP/UDP 直接到握手、挥手和 TIME_WAIT/CLOSE_WAIT。计划的 MEDIUM 不包含 NETWORK，所以中间空档是计划形状，不是这两题出难了。
- PROJECT_SCENARIO：只有 H09。计划只在 HARD 要求这一类。

没有发现 EASY 深过同分类 MEDIUM，也没有发现 MEDIUM 深过同分类 HARD。覆盖面服从 5/7/9 的计划：JVM、并发和 Redis 从 MEDIUM 开始，项目场景只在 HARD 出现。

## 9. Final Verdict

PHASE_1_EXTERNAL_REVIEW: PASS_WITH_FIXES

21 题可以进入现有表结构和面试计划。没有技术结论被整个写反，也没有字段、枚举、权重和 100 分总和与源码冲突。冻结前需要处理三个会进入 Jev 和追问闸门的评分口径：M03 的区域清单、M05 的 CORE 类型、H09 对悲观锁的排除。

BLOCKING_FIXES_BEFORE_FREEZE:

- M03：把六个运行时区域拆开判断。CORE 只要求堆、方法区、Java 虚拟机栈和程序计数器；本地方法栈、运行时常量池和 Metaspace 分别放到共享性或 ADVANCED 点。
- M05：自调用绕过代理这一点评为 CORE，正文和权重保持不变。
- H09：CORE 同时接受条件更新、乐观锁，以及事务内 `SELECT ... FOR UPDATE`；无锁先查后改仍然不覆盖。参考答案补一句悲观锁即可。

NON_BLOCKING_SUGGESTIONS:

- M02：树化写成“已有至少 8 个节点后再插入，容量小于 64 则 resize”；相同 key 更新只留在一个评分点。
- E05：QUIC 改为基于 UDP 的传输协议。
- H01：擦除限制改为至少举出两项。
- H02：协作迁移按行为判断，不要求 `ForwardingNode` 或 `helpTransfer`。
- H05：checked exception 改为“默认不回滚”。
- H09：纯 MySQL 的正确方案也覆盖 Redis 这一点，避免查询被 Redis 锁带偏。
- 21 个 knowledgePoint 全部保留，不为本轮改名，也不扩题。
