# Phase 2C Chunk Preview — Actual KnowledgeTextChunker.split(String) Output

Inputs were decoded as strict UTF-8, had one leading UTF-8 BOM removed if present, and had CRLF/CR converted to LF. No other document content was changed before calling the production splitter. CHARACTER_COUNT is Java String.length() in UTF-16 code units, not a token count. Offsets refer to the normalized source and are found by matching each returned chunk's stripped content in source order; end offsets are exclusive. These offsets identify the returned stripped text, not any whitespace removed by strip().

## M02 — m02_hashmap_jdk8u_put_resize_treeify.md

DOCUMENT_TOTAL_CHARACTERS: 2356  
CHUNK_COUNT: 3  
MIN_CHUNK_LENGTH: 471  
MAX_CHUNK_LENGTH: 1109  
AVERAGE_CHUNK_LENGTH: 858.67

### M02 Chunk 1

DOCUMENT: m02_hashmap_jdk8u_put_resize_treeify.md  
CHUNK_INDEX: 1  
CHARACTER_COUNT: 996  
START_OFFSET_IF_AVAILABLE: 0  
END_OFFSET_IF_AVAILABLE: 996  
STARTS_WITH: # JDK 8 HashMap：put、扩容与树化机制\n\n> Knowledge category: `JAVA_COLLECTION`\n> Target question: M02 — JDK 8   
ENDS_WITH: ，则根据桶类型在链表或树桶中继续查找。发现相同 key 时更新对应节点的 value；没有发现时才插入新节点。新映射加入后，`size` 增加，超过 `threshold` 时调用 `resize`。

````text
# JDK 8 HashMap：put、扩容与树化机制

> Knowledge category: `JAVA_COLLECTION`
> Target question: M02 — JDK 8 HashMap 的底层结构、put 路径与树化条件
> Version scope: 以下数组、桶链表、树化常量和 `putVal` 流程均指 OpenJDK JDK 8u 实现；公开 API 的容量与负载因子说明按 Java SE 8。
> Primary source: https://github.com/openjdk/jdk8u/blob/master/jdk/src/share/classes/java/util/HashMap.java

## 1. Core model

JDK 8u 的 `HashMap` 以一个 `Node` 数组作为桶表（table）。每个数组位置对应一个桶（bin）；不同键经过哈希与索引计算后可能落入同一桶，这就是哈希冲突。普通桶由带有 `next` 引用的节点串成链表；达到实现条件后，桶内节点可以改为树节点并组织成树结构。因此，“数组 + 链表 + 树”是对 JDK 8u 桶结构的概括，树结构只用于冲突较集中的桶，不代表整个映射都变成一棵树。

桶索引由扰动后的 hash 和表长度共同决定。JDK 8u 的表容量是 2 的幂，`putVal` 使用 `(n - 1) & hash` 计算桶下标；同一桶中再比较节点保存的 hash，并通过键引用相同或 `equals` 相等来识别已有映射。键相同的 `put` 会替换已有节点的 value 并返回旧 value，不会无条件追加一个逻辑上重复的键。哈希相同但键不相等时，才继续在该桶中查找或加入新映射。

## 2. Main mechanism

一次 JDK 8u `put` 首先计算 key 的 hash，并进入 `putVal`。如果 table 尚未分配或长度为零，流程先初始化表；之后根据表长度计算桶下标。空桶直接放入新节点。非空桶先检查桶首节点是否就是目标映射；若不是，则根据桶类型在链表或树桶中继续查找。发现相同 key 时更新对应节点的 value；没有发现时才插入新节点。新映射加入后，`size` 增加，超过 `threshold` 时调用 `resize`。
````

### M02 Chunk 2

DOCUMENT: m02_hashmap_jdk8u_put_resize_treeify.md  
CHUNK_INDEX: 2  
CHARACTER_COUNT: 1109  
START_OFFSET_IF_AVAILABLE: 920  
END_OFFSET_IF_AVAILABLE: 2029  
STARTS_WITH: key 时更新对应节点的 value；没有发现时才插入新节点。新映射加入后，`size` 增加，超过 `threshold` 时调用 `resize`。\n\n扩容关注的是整张表的容量与映射数量，而树化关  
ENDS_WITH: ；它只是默认 load factor，构造时可指定其他正值。相同 key 再次 `put` 的语义是替换映射的 value，而不是新建第二个相同键节点。\n\n## Official references

````text
key 时更新对应节点的 value；没有发现时才插入新节点。新映射加入后，`size` 增加，超过 `threshold` 时调用 `resize`。

扩容关注的是整张表的容量与映射数量，而树化关注的是某一个桶内的冲突数量，两者是不同条件。JDK 8u 的 `HashMap` 默认 load factor 为 `0.75`；构造方法也允许调用者给出其他正数负载因子。threshold 是触发整体扩容的数量边界，通常由容量与负载因子共同确定。较低负载因子会让扩容更早发生并占用较大的桶表；较高负载因子允许更多映射共享当前容量。默认值 0.75 是默认配置，不是所有实例都不能改变的固定值。

## 3. Important boundary

OpenJDK JDK 8u 将 `TREEIFY_THRESHOLD` 定义为 8。源码注释描述的是：向一个已经至少有 8 个节点的桶添加元素时，进入树化判断；因此不能把它简化成“第 8 个节点一到就一定立即变树”。`putVal` 在普通链表中新插入节点后才按 bin count 触发 `treeifyBin`，而 `treeifyBin` 还会检查表容量。若 table 长度小于 `MIN_TREEIFY_CAPACITY = 64`，实现选择先 `resize`；达到该容量边界后，才会把桶链表转换为树桶。已有 key 的 value 更新不是新增节点，也不会因更新而触发此插入路径。

树化条件是 JDK 8u 的实现行为，不是 `Map` 接口对所有实现的承诺。Java SE 8 `HashMap` API 说明容量和 load factor 与性能有关，并给出默认负载因子 0.75；`HashMap` 构造方法允许指定负载因子。面试中回答树化问题时，应同时说出桶节点数触发点与最小 table 容量边界，不能只背“阈值 8”。这些常量和 `putVal` 的具体顺序可能随 JDK 实现版本变化，不应无条件外推到其他版本或其他 Map 类型。

## 4. Common misconceptions

“桶里有 8 个节点就必然立刻成为红黑树”忽略了触发动作发生在向已有节点的桶继续插入时，也忽略 `MIN_TREEIFY_CAPACITY`。容量不足 64 时，JDK 8u 先扩容而不是直接树化。另一个误区是把 0.75 当成不可调整的常量；它只是默认 load factor，构造时可指定其他正值。相同 key 再次 `put` 的语义是替换映射的 value，而不是新建第二个相同键节点。

## Official references
````

### M02 Chunk 3

DOCUMENT: m02_hashmap_jdk8u_put_resize_treeify.md  
CHUNK_INDEX: 3  
CHARACTER_COUNT: 471  
START_OFFSET_IF_AVAILABLE: 1884  
END_OFFSET_IF_AVAILABLE: 2355  
STARTS_WITH: 64 时，JDK 8u 先扩容而不是直接树化。另一个误区是把 0.75 当成不可调整的常量；它只是默认 load factor，构造时可指定其他正值。相同 key 再次 `put` 的语义是替换映射的  
ENDS_WITH: I](https://docs.oracle.com/javase/8/docs/api/java/util/HashMap.html) — 容量、load factor、默认值及 API 性能说明。

````text
64 时，JDK 8u 先扩容而不是直接树化。另一个误区是把 0.75 当成不可调整的常量；它只是默认 load factor，构造时可指定其他正值。相同 key 再次 `put` 的语义是替换映射的 value，而不是新建第二个相同键节点。

## Official references

- Primary: [OpenJDK JDK 8u `java.util.HashMap`](https://github.com/openjdk/jdk8u/blob/master/jdk/src/share/classes/java/util/HashMap.java) — `putVal`, `resize`, `treeifyBin` 与相关常量。
- Secondary: [Oracle Java SE 8 `HashMap` API](https://docs.oracle.com/javase/8/docs/api/java/util/HashMap.html) — 容量、load factor、默认值及 API 性能说明。
````

### M02 overlap audit

CHUNK_1 -> CHUNK_2  
OVERLAP_PRESENT: YES  
APPROX_OVERLAP_CHARS: 76  
OVERLAP_TEXT_PREVIEW: key 时更新对应节点的 value；没有发现时才插入新节点。新映射加入后，`size` 增加，超过 `threshold` 时调用 `resize`。  

CHUNK_2 -> CHUNK_3  
OVERLAP_PRESENT: YES  
APPROX_OVERLAP_CHARS: 145  
OVERLAP_TEXT_PREVIEW: 64 时，JDK 8u 先扩容而不是直接树化。另一个误区是把 0.75 当成不可调整的常量；它只是默认 load factor，构造时可指定其他正值。相同 key 再次 `put` 的语义是替换映射的 value，而不是新建第二个相同键节点...  

## M04 — m04_volatile_jmm_java21.md

DOCUMENT_TOTAL_CHARACTERS: 2228  
CHUNK_COUNT: 2  
MIN_CHUNK_LENGTH: 1142  
MAX_CHUNK_LENGTH: 1159  
AVERAGE_CHUNK_LENGTH: 1150.50

### M04 Chunk 1

DOCUMENT: m04_volatile_jmm_java21.md  
CHUNK_INDEX: 1  
CHARACTER_COUNT: 1159  
START_OFFSET_IF_AVAILABLE: 0  
END_OFFSET_IF_AVAILABLE: 1159  
STARTS_WITH: # Java 21 JMM：volatile、可见性与复合操作原子性\n\n> Knowledge category: `JAVA_CONCURRENCY`\n> Target question: M04   
ENDS_WITH: 线程观察，且写入本身满足业务逻辑，volatile 可能合适；若需求是多个线程对共享状态执行互斥的复合更新，则必须选择能覆盖整个更新过程的同步机制。\n\n## 3. Important boundary

````text
# Java 21 JMM：volatile、可见性与复合操作原子性

> Knowledge category: `JAVA_CONCURRENCY`
> Target question: M04 — `volatile` 的保证及 `volatile int count` 下 `count++` 的并发安全性
> Version scope: Java SE 21 Language Specification（JLS），尤其是 Chapter 17 的同步动作、synchronization order 与 happens-before 规则，以及 Chapter 15 的 postfix increment 定义。
> Primary source: https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html

## 1. Core model

Java Memory Model（JMM）用动作、程序顺序和线程间关系描述并发执行中读写的可见性与顺序。JLS 将对 `volatile` 变量的读和写定义为 synchronization actions，并为这些动作规定 synchronization order。这个定义描述语言层面的并发语义；它不等同于某一种 CPU 缓存或编译器实现，也不意味着每条机器指令都必须照源码顺序执行。

## 2. Main mechanism

对同一个 `volatile` 变量，某次写入按照 synchronization order 后续发生的读，与该写入建立 synchronizes-with 关系。JLS 的 happens-before 规则进一步规定：对 `volatile` 字段的写 happens-before 后续对该字段的读。因而，如果线程 A 先完成普通数据写入，再写入 volatile 状态变量；线程 B 随后读到该状态变量的 volatile 写入，那么 happens-before 的传递关系为这些相关动作提供可见性和顺序约束。可见性应按这些关系理解，而不是把它说成“volatile 禁止所有指令重排”。

`volatile` 单次读写提供同步语义，但不提供互斥。多个线程仍可以同时执行临界区中的代码；JMM 也不会因为变量声明为 volatile，就把围绕该变量的任意表达式自动合并成一个不可分割的同步动作。因此，若需求是让一个线程的状态标记被其他线程观察，且写入本身满足业务逻辑，volatile 可能合适；若需求是多个线程对共享状态执行互斥的复合更新，则必须选择能覆盖整个更新过程的同步机制。

## 3. Important boundary
````

### M04 Chunk 2

DOCUMENT: m04_volatile_jmm_java21.md  
CHUNK_INDEX: 2  
CHARACTER_COUNT: 1142  
START_OFFSET_IF_AVAILABLE: 1085  
END_OFFSET_IF_AVAILABLE: 2227  
STARTS_WITH: 可能合适；若需求是多个线程对共享状态执行互斥的复合更新，则必须选择能覆盖整个更新过程的同步机制。\n\n## 3. Important boundary\n\n`volatile int count; cou  
ENDS_WITH: racle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.14.2) — §15.14.2 postfix increment operator。

````text
可能合适；若需求是多个线程对共享状态执行互斥的复合更新，则必须选择能覆盖整个更新过程的同步机制。

## 3. Important boundary

`volatile int count; count++` 不是一个整体原子操作。按照 JLS 对 postfix increment 的定义，执行 `count++` 时会读取变量当前值，将数值加一，再把结果存回变量。两个线程可能都先读到相同旧值，各自计算出同一个新值，随后分别写回；后一写入覆盖前一写入时，一次逻辑增量就丢失了。把 `count` 声明为 volatile 可以为对 `count` 的读写建立 JMM 同步关系，但不会把“读、加、写”这三个步骤变成一个排他执行的整体。

当共享计数必须正确累加时，应让同步范围覆盖完整的 read-modify-write 更新。概念上可以使用 `synchronized` 或 `Lock` 将复合临界区串行化，也可以选用适合该更新语义的原子类，例如 `AtomicInteger`。选择依据是需要的语义：单次状态发布与观察可以考虑 volatile；需要互斥保护多个步骤或原子更新时，不能把 volatile 当作锁或复合操作原子性保证。

## 4. Common misconceptions

“volatile 等于轻量级锁”混淆了可见性、顺序约束与互斥。volatile 变量的单次读写参与 JMM 同步，但没有锁住临界区；`count++` 仍可能丢失更新。“happens-before 表示 CPU 必须逐条按源码顺序执行”也不准确：happens-before 是规范定义的可见性与排序关系，允许实现采用符合该关系的执行方式，并不描述某个处理器的具体指令调度。

## Official references

- Primary: [Java Language Specification, Java SE 21, Chapter 17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html) — §17.4.2、§17.4.4、§17.4.5。
- Secondary: [Java Language Specification, Java SE 21, Chapter 15 — Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.14.2) — §15.14.2 postfix increment operator。
````

### M04 overlap audit

CHUNK_1 -> CHUNK_2  
OVERLAP_PRESENT: YES  
APPROX_OVERLAP_CHARS: 74  
OVERLAP_TEXT_PREVIEW: 可能合适；若需求是多个线程对共享状态执行互斥的复合更新，则必须选择能覆盖整个更新过程的同步机制。\n\n## 3. Important boundary  

## M05 — m05_spring_6_2_aop_proxy_self_invocation.md

DOCUMENT_TOTAL_CHARACTERS: 2443  
CHUNK_COUNT: 3  
MIN_CHUNK_LENGTH: 514  
MAX_CHUNK_LENGTH: 1123  
AVERAGE_CHUNK_LENGTH: 898.67

### M05 Chunk 1

DOCUMENT: m05_spring_6_2_aop_proxy_self_invocation.md  
CHUNK_INDEX: 1  
CHARACTER_COUNT: 1059  
START_OFFSET_IF_AVAILABLE: 0  
END_OFFSET_IF_AVAILABLE: 1059  
STARTS_WITH: # Spring Framework 6.2：AOP 代理与 self-invocation\n\n> Knowledge category: `SPRING`\n> Target question: M0  
ENDS_WITH: 收者是目标对象自身，而不是外部代理。内部调用不会重新绕回 proxy，所以绑定在被调用方法上的 advice 在这条调用路径上没有机会运行。问题的核心是调用是否穿过代理边界，不是被调用方法是否有注解。

````text
# Spring Framework 6.2：AOP 代理与 self-invocation

> Knowledge category: `SPRING`
> Target question: M05 — Spring AOP 的代理机制及内部调用 `@Transactional` 方法的行为
> Version scope: Spring Framework 6.2.x；本项目版本审计解析到 6.2.19。以下 self-invocation 结论专指 Spring AOP 的 proxy 模式。
> Primary source: https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html

## 1. Core model

Spring AOP 的标准代理方式通过代理对象承接调用。调用者持有代理时，方法调用先到达 proxy；匹配的 interceptor 或 advice 可以在目标方法执行前后处理调用，然后 proxy 再将调用交给目标对象。事务、日志等横切逻辑依赖调用经过代理这一边界。目标对象本身并不会因为方法上有 advice，就自动改写所有直接调用路径。

Spring Framework 6.2 可用 JDK dynamic proxy 或 CGLIB 创建代理。JDK dynamic proxy 使用 JDK 内置代理机制，围绕目标对象实现的接口代理调用；CGLIB 是基于类的代理方式，通过目标类的子类承接调用。两种方式改变代理的形态，但都属于代理机制：需要拦截的调用必须经过代理。CGLIB 子类化受 Java 类型限制，例如 final class 不能被继承，final method 不能被子类覆盖，因此这类方法无法通过该类代理方式覆盖拦截。

## 2. Main mechanism

在 proxy-based AOP 中，外部调用先经过 Spring proxy，proxy 才有机会应用相应 advice。执行已经进入目标对象之后，如果目标方法用显式 `this.otherMethod()` 或隐式 `otherMethod()` 调用同一对象的另一个方法，接收者是目标对象自身，而不是外部代理。内部调用不会重新绕回 proxy，所以绑定在被调用方法上的 advice 在这条调用路径上没有机会运行。问题的核心是调用是否穿过代理边界，不是被调用方法是否有注解。
````

### M05 Chunk 2

DOCUMENT: m05_spring_6_2_aop_proxy_self_invocation.md  
CHUNK_INDEX: 2  
CHARACTER_COUNT: 1123  
START_OFFSET_IF_AVAILABLE: 925  
END_OFFSET_IF_AVAILABLE: 2048  
STARTS_WITH: 或隐式 `otherMethod()` 调用同一对象的另一个方法，接收者是目标对象自身，而不是外部代理。内部调用不会重新绕回 proxy，所以绑定在被调用方法上的 advice 在这条调用路径上没有机  
ENDS_WITH: B 的代理目标不同，但都不能让目标对象对自身的直接调用自动穿过外部代理。分析这类问题时，先确认运行模式和调用路径，再判断 advice 是否有机会执行。\n\n## Official references

````text
或隐式 `otherMethod()` 调用同一对象的另一个方法，接收者是目标对象自身，而不是外部代理。内部调用不会重新绕回 proxy，所以绑定在被调用方法上的 advice 在这条调用路径上没有机会运行。问题的核心是调用是否穿过代理边界，不是被调用方法是否有注解。

Spring Framework 6.2 的声明式事务文档说明，`@Transactional` 默认使用 proxy 模式；在该模式下，只有通过代理进入的外部方法调用才会被拦截。因此，目标对象内部 self-invocation 到另一个 `@Transactional` 方法时，该方法上的事务 advice 不会因这次调用而生效。这个结论解释的是 proxy 模式下的调用边界；不要把它泛化成所有事务问题的唯一成因，也不要把 proxy 模式结论套用到 AspectJ weaving。

## 3. Important boundary

优先通过重构调用边界来处理 self-invocation：将需要独立 advice 的职责放到另一个 Spring Bean / Service，再由调用方通过注入的 Bean 发起调用，使请求经过代理。Spring 文档还列出 self injection 作为备选方式，即通过自身代理引用调用；`AopContext.currentProxy()` 也可取得当前代理，但会让业务代码依赖 AOP 上下文，通常不是首选。无论使用 JDK proxy 还是 CGLIB，只要调用仍在目标对象内部直接发生，proxy-based AOP 的 self-invocation 限制仍然存在。

AspectJ weaving 不通过同一个外部代理拦截模型处理方法执行，因此不应说 AspectJ 模式与 proxy 模式有相同的 self-invocation 限制。本文讨论的是 Spring AOP 与默认声明式事务 proxy 模式。重构调用边界通常更清楚，也便于把事务 advice 的触发点显式放在 Bean 之间的调用路径上。

## 4. Common misconceptions

类中声明 `@Transactional` 不意味着目标对象内部的每条调用都会自动经过事务代理。`this` 调用不会自动回到外部 proxy；给内部方法再加注解也不会改变接收者。JDK dynamic proxy 与 CGLIB 的代理目标不同，但都不能让目标对象对自身的直接调用自动穿过外部代理。分析这类问题时，先确认运行模式和调用路径，再判断 advice 是否有机会执行。

## Official references
````

### M05 Chunk 3

DOCUMENT: m05_spring_6_2_aop_proxy_self_invocation.md  
CHUNK_INDEX: 3  
CHARACTER_COUNT: 514  
START_OFFSET_IF_AVAILABLE: 1928  
END_OFFSET_IF_AVAILABLE: 2442  
STARTS_WITH: dynamic proxy 与 CGLIB 的代理目标不同，但都不能让目标对象对自身的直接调用自动穿过外部代理。分析这类问题时，先确认运行模式和调用路径，再判断 advice 是否有机会执行。\n\n##  
ENDS_WITH: mework/reference/6.2/data-access/transaction/declarative/annotations.html) — 声明式事务的 proxy 模式与外部调用限制。

````text
dynamic proxy 与 CGLIB 的代理目标不同，但都不能让目标对象对自身的直接调用自动穿过外部代理。分析这类问题时，先确认运行模式和调用路径，再判断 advice 是否有机会执行。

## Official references

- Primary: [Spring Framework 6.2 Reference — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html) — JDK proxy、CGLIB、self-invocation 与建议的处理方式。
- Secondary: [Spring Framework 6.2 Reference — Using `@Transactional`](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html) — 声明式事务的 proxy 模式与外部调用限制。
````

### M05 overlap audit

CHUNK_1 -> CHUNK_2  
OVERLAP_PRESENT: YES  
APPROX_OVERLAP_CHARS: 134  
OVERLAP_TEXT_PREVIEW: 或隐式 `otherMethod()` 调用同一对象的另一个方法，接收者是目标对象自身，而不是外部代理。内部调用不会重新绕回 proxy，所以绑定在被调用方法上的 advice 在这条调用路径上没有机会运行。问题的核心是调用是否穿过代理边界...  

CHUNK_2 -> CHUNK_3  
OVERLAP_PRESENT: YES  
APPROX_OVERLAP_CHARS: 120  
OVERLAP_TEXT_PREVIEW: dynamic proxy 与 CGLIB 的代理目标不同，但都不能让目标对象对自身的直接调用自动穿过外部代理。分析这类问题时，先确认运行模式和调用路径，再判断 advice 是否有机会执行。\n\n## Official referenc...  

## H06 — h06_mysql_8_0_innodb_mvcc_consistent_read.md

DOCUMENT_TOTAL_CHARACTERS: 2451  
CHUNK_COUNT: 3  
MIN_CHUNK_LENGTH: 730  
MAX_CHUNK_LENGTH: 1064  
AVERAGE_CHUNK_LENGTH: 893.67

### H06 Chunk 1

DOCUMENT: h06_mysql_8_0_innodb_mvcc_consistent_read.md  
CHUNK_INDEX: 1  
CHARACTER_COUNT: 1064  
START_OFFSET_IF_AVAILABLE: 0  
END_OFFSET_IF_AVAILABLE: 1064  
STARTS_WITH: # MySQL 8.0 InnoDB：MVCC、一致性读与 Read View\n\n> Knowledge category: `MYSQL`\n> Target question: H06 — REPE  
ENDS_WITH: do 链提供构造旧版本的记录信息；这几个角色不可互换：事务 ID 标识修改版本，undo 信息保存历史重建材料，Read View 表达读取可见性。\n\n## 3. Important boundary

````text
# MySQL 8.0 InnoDB：MVCC、一致性读与 Read View

> Knowledge category: `MYSQL`
> Target question: H06 — REPEATABLE READ 下的 MVCC、Undo Log、事务 ID 与 Read View
> Version scope: MySQL 8.0 Reference Manual；本地版本审计记录为 MySQL 8.0.45。本文只描述 InnoDB 的一致性非锁定读与锁定读边界。
> Primary source: https://dev.mysql.com/doc/refman/8.0/en/innodb-multi-versioning.html

## 1. Core model

InnoDB 是支持多版本的存储引擎。更新行时，InnoDB 保留构造较早行版本所需的信息；这些信息既用于事务回滚，也可用于一致性读。这样，普通 consistent read 在需要读取历史可见版本时，可以依据事务可见性选择合适版本，而不必把所有读取都解释成等待当前写入结束。MVCC 描述的是版本与可见性机制，不表示 InnoDB 在任何场景下都不使用锁。

## 2. Main mechanism

MySQL 8.0 手册说明，InnoDB 聚簇索引记录包含修改该行的事务标识 `DB_TRX_ID`，以及名为 `DB_ROLL_PTR` 的 rollback pointer。该指针指向 rollback segment 中的 undo log record；对于已更新的行，undo 信息保存重建更新前行内容所需的数据。若一致性读不能看到当前记录版本，InnoDB 可以利用 undo 信息取得较早版本。因此 Undo Log 不只与 rollback 有关，也为一致性读重建历史版本提供材料。

consistent nonlocking read 按一个数据库快照判断记录版本是否可见。工程讨论中常把这类可见性快照称为 Read View；这里应把它理解为读操作的事务可见性边界，而不需要臆测手册没有规定的内部字段或判断算法。版本上的事务标识与读操作的可见性边界共同决定当前一致性读可观察哪个版本。若需回溯，undo 链提供构造旧版本的记录信息；这几个角色不可互换：事务 ID 标识修改版本，undo 信息保存历史重建材料，Read View 表达读取可见性。

## 3. Important boundary
````

### H06 Chunk 2

DOCUMENT: h06_mysql_8_0_innodb_mvcc_consistent_read.md  
CHUNK_INDEX: 2  
CHARACTER_COUNT: 887  
START_OFFSET_IF_AVAILABLE: 967  
END_OFFSET_IF_AVAILABLE: 1854  
STARTS_WITH: 链提供构造旧版本的记录信息；这几个角色不可互换：事务 ID 标识修改版本，undo 信息保存历史重建材料，Read View 表达读取可见性。\n\n## 3. Important boundary\n\n在  
ENDS_WITH: 读建立的快照；locking read 有自己的锁定与最新数据语义。MVCC 能支持版本化读取，但不能据此断言所有读写都无锁或所有并发异常都已自动解决。\n\n## Official references

````text
链提供构造旧版本的记录信息；这几个角色不可互换：事务 ID 标识修改版本，undo 信息保存历史重建材料，Read View 表达读取可见性。

## 3. Important boundary

在 `REPEATABLE READ` 隔离级别下，同一事务中的 consistent reads 使用该事务第一次 consistent read 建立的 snapshot；因此后续普通、非锁定 `SELECT` 会继续按该快照读取。这里的对象是 consistent read，不是事务中的所有 SQL。`READ COMMITTED` 则不同：同一事务内的每次 consistent read 都建立并读取一个新的 snapshot。回答隔离级别差异时，应说清比较的是一致性读的快照时点。

`SELECT ... FOR UPDATE` 是 locking read，不是普通的历史快照读。MySQL 8.0 手册说明，`SELECT ... FOR UPDATE` 读取最新可用数据，并对读取到的行设置排他锁。因此，普通 consistent nonlocking read 与 locking/current read 在可见数据和加锁行为上不同。MVCC 不代表“没有锁”；需要基于读后写保护时，应区分普通快照查询与 `FOR UPDATE` 等锁定读。

## 4. Common misconceptions

“Undo Log 只用于 rollback”忽略了 update undo log 也服务于一致性读中的旧版本构造。“REPEATABLE READ 下事务的所有 SQL 都读一个历史快照”把 consistent read 扩大到了锁定读和 DML。准确表达应限定为：同一 RR 事务的 consistent reads 通常沿用第一次一致性读建立的快照；locking read 有自己的锁定与最新数据语义。MVCC 能支持版本化读取，但不能据此断言所有读写都无锁或所有并发异常都已自动解决。

## Official references
````

### H06 Chunk 3

DOCUMENT: h06_mysql_8_0_innodb_mvcc_consistent_read.md  
CHUNK_INDEX: 3  
CHARACTER_COUNT: 730  
START_OFFSET_IF_AVAILABLE: 1720  
END_OFFSET_IF_AVAILABLE: 2450  
STARTS_WITH: RR 事务的 consistent reads 通常沿用第一次一致性读建立的快照；locking read 有自己的锁定与最新数据语义。MVCC 能支持版本化读取，但不能据此断言所有读写都无锁或所有并  
ENDS_WITH: ](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html) — `SELECT ... FOR UPDATE` 等锁定读。

````text
RR 事务的 consistent reads 通常沿用第一次一致性读建立的快照；locking read 有自己的锁定与最新数据语义。MVCC 能支持版本化读取，但不能据此断言所有读写都无锁或所有并发异常都已自动解决。

## Official references

- Primary: [MySQL 8.0 Reference Manual — InnoDB Multi-Versioning](https://dev.mysql.com/doc/refman/8.0/en/innodb-multi-versioning.html) — 行版本、`DB_TRX_ID`、`DB_ROLL_PTR` 与 undo 信息。
- Secondary: [Consistent Nonlocking Reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-consistent-read.html) — consistent read 与隔离级别下的快照。
- Secondary: [Transaction Isolation Levels](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html) — RR 与 RC 的一致性读快照时点。
- Secondary: [Locking Reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html) — `SELECT ... FOR UPDATE` 等锁定读。
````

### H06 overlap audit

CHUNK_1 -> CHUNK_2  
OVERLAP_PRESENT: YES  
APPROX_OVERLAP_CHARS: 97  
OVERLAP_TEXT_PREVIEW: 链提供构造旧版本的记录信息；这几个角色不可互换：事务 ID 标识修改版本，undo 信息保存历史重建材料，Read View 表达读取可见性。\n\n## 3. Important boundary  

CHUNK_2 -> CHUNK_3  
OVERLAP_PRESENT: YES  
APPROX_OVERLAP_CHARS: 134  
OVERLAP_TEXT_PREVIEW: RR 事务的 consistent reads 通常沿用第一次一致性读建立的快照；locking read 有自己的锁定与最新数据语义。MVCC 能支持版本化读取，但不能据此断言所有读写都无锁或所有并发异常都已自动解决。\n\n## Off...  

## H07 — h07_redis_distributed_lock_baseline.md

DOCUMENT_TOTAL_CHARACTERS: 2563  
CHUNK_COUNT: 3  
MIN_CHUNK_LENGTH: 501  
MAX_CHUNK_LENGTH: 1182  
AVERAGE_CHUNK_LENGTH: 951.33

### H07 Chunk 1

DOCUMENT: h07_redis_distributed_lock_baseline.md  
CHUNK_INDEX: 1  
CHARACTER_COUNT: 1182  
START_OFFSET_IF_AVAILABLE: 0  
END_OFFSET_IF_AVAILABLE: 1182  
STARTS_WITH: # Redis 分布式锁：互斥、租约与安全释放\n\n> Knowledge category: `REDIS`\n> Target question: H07 — Redis 分布式锁的获取、所有权校验与  
ENDS_WITH: a\nif redis.call('get', KEYS[1]) == ARGV[1] then\n  return redis.call('del', KEYS[1])\nend\nreturn 0\n```

````text
# Redis 分布式锁：互斥、租约与安全释放

> Knowledge category: `REDIS`
> Target question: H07 — Redis 分布式锁的获取、所有权校验与安全释放
> Version scope: 经典单实例锁基线为 Redis >= 2.6.12（`SET` 支持 `NX`、`PX`）；`EVAL` 自 Redis 2.6.0 可用。本文不推断本地 Redis 版本，也不描述项目当前运行时依赖。
> Primary source: https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/

## 1. Core model

带过期时间的 Redis 锁是有时限的租约，而不是持有者永久拥有资源的证明。基础目标是在一个 key 上一次只允许一个客户端成功取得锁，并让锁在客户端失败后最终自动过期。只用 `SETNX` 设置 key、再用另一条命令设置 TTL 存在命令间隔：如果进程在两条命令之间停止，key 已被设为锁却还没有过期时间，锁可能长期残留。获取与过期时间应由同一条 `SET` 命令完成。

## 2. Main mechanism

经典单实例获取方式是在一条命令中同时使用条件写入和过期时间：

```text
SET lockKey uniqueToken NX PX ttlMilliseconds
```

`NX` 表示仅当 key 不存在时才设置；`PX` 将毫秒级 TTL 与这次设置一起提交。成功响应表示该客户端取得当前租约，未成功则没有取得锁。Redis `SET` 文档记录 `NX` 与 `PX` 选项从 2.6.12 起可用，因此这是本文件采用的最低兼容基线。设置 TTL 可在客户端异常退出后释放遗留 key，但租约长度也限定了客户端可以安全完成临界区工作的时间窗口。

锁 value 应是本次锁请求唯一的 token，而不是所有请求共用的固定字符串。释放时不能直接执行无条件 `DEL lockKey`：客户端 A 的租约可能先过期，客户端 B 随后取得同名锁；若 A 的迟到释放命令直接删除 key，就会删除 B 的锁。安全释放需要先比较 key 当前 value 与 A 保存的 token，仅匹配时才删除，并且比较与删除必须作为一个原子操作执行。

在该兼容基线中，可用 Lua 脚本执行 compare-and-delete，并通过 `EVAL` 调用：

```lua
if redis.call('get', KEYS[1]) == ARGV[1] then
  return redis.call('del', KEYS[1])
end
return 0
```
````

### H07 Chunk 2

DOCUMENT: h07_redis_distributed_lock_baseline.md  
CHUNK_INDEX: 2  
CHARACTER_COUNT: 1171  
START_OFFSET_IF_AVAILABLE: 1037  
END_OFFSET_IF_AVAILABLE: 2208  
STARTS_WITH: 脚本执行 compare-and-delete，并通过 `EVAL` 调用：\n\n```lua\nif redis.call('get', KEYS[1]) == ARGV[1] then\n  retur  
ENDS_WITH: a/EVAL 原子比较和删除。本文的 Redis >= 2.6.12 是命令兼容基线，不是本地环境版本声明；`EVAL` 的最低版本单独为 2.6.0。\n\n## Official references

````text
脚本执行 compare-and-delete，并通过 `EVAL` 调用：

```lua
if redis.call('get', KEYS[1]) == ARGV[1] then
  return redis.call('del', KEYS[1])
end
return 0
```

脚本在 Redis 端完成 token 检查和删除，其他命令不会插入到这两个动作中间。调用方把锁 key 作为 `KEYS[1]`、自己的唯一 token 作为 `ARGV[1]` 传入。Redis 官方 `EVAL` 命令文档记录其自 2.6.0 可用；因此 `SET ... NX PX` 加唯一 token 与 Lua/EVAL 原子释放形成 Redis >= 2.6.12 的经典实现基线。

## 3. Important boundary

TTL 到期只表示 Redis 允许其他客户端再次尝试取得该 key，不会终止已经运行的旧客户端代码。若 A 的业务工作超过 TTL，B 可能已取得新租约，而 A 仍在执行；因此仅设置过期时间无法保证临界区无论运行多久都互斥。应用应按工作时长与故障恢复需求设计租约，并把续租视为需要正确所有权校验和失败处理的额外策略，而不是 Redis 自动赋予的锁属性。

对关键业务不变量，Redis 锁不能替代权威数据存储本身的约束或条件更新。这个工程边界来自租约会到期、客户端可能仍继续运行这一事实：如果重复写入会破坏正确性，最终数据层仍应校验唯一性、版本或业务条件。Redis 锁可用于协调竞争，但不能据此取消数据库已经需要的正确性约束。网络分区使持锁客户端无法联系 Redis 时，客户端无法确认或完成释放；仍可访问 Redis 的竞争者可能要等 TTL 到期后才能再次获取。单实例方案也不能被理解为覆盖所有节点故障：若 Redis 主节点在锁写入复制到副本前故障，异步复制下的副本提升可能使另一个客户端取得同名锁。Redis 官方资料将互斥安全性、可用性与故障场景分开讨论；本文说明这些边界，但不展开多实例算法。

## 4. Common misconceptions

“`SETNX` 成功就构成完整可靠的锁”忽略了原子过期设置；“锁一定会过期，所以任何客户端都能直接 `DEL`”忽略了过期后可能已有新持有者；“TTL 设置正确就能保护任意长的临界区”忽略了租约边界。经典做法是一次 `SET key token NX PX ttl` 获取、用请求唯一 token 标识所有权，并以 Lua/EVAL 原子比较和删除。本文的 Redis >= 2.6.12 是命令兼容基线，不是本地环境版本声明；`EVAL` 的最低版本单独为 2.6.0。

## Official references
````

### H07 Chunk 3

DOCUMENT: h07_redis_distributed_lock_baseline.md  
CHUNK_INDEX: 3  
CHARACTER_COUNT: 501  
START_OFFSET_IF_AVAILABLE: 2061  
END_OFFSET_IF_AVAILABLE: 2562  
STARTS_WITH: key token NX PX ttl` 获取、用请求唯一 token 标识所有权，并以 Lua/EVAL 原子比较和删除。本文的 Redis >= 2.6.12 是命令兼容基线，不是本地环境版本声明  
ENDS_WITH: \n- Secondary: [Redis `EVAL` command](https://redis.io/docs/latest/commands/eval/) — 服务端 Lua 执行与可用版本。

````text
key token NX PX ttl` 获取、用请求唯一 token 标识所有权，并以 Lua/EVAL 原子比较和删除。本文的 Redis >= 2.6.12 是命令兼容基线，不是本地环境版本声明；`EVAL` 的最低版本单独为 2.6.0。

## Official references

- Primary: [Redis — Distributed Locks with Redis](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/) — 锁安全性、唯一随机值、租约时长、释放与故障边界。
- Secondary: [Redis `SET` command](https://redis.io/docs/latest/commands/set/) — `NX` / `PX` 选项及版本历史。
- Secondary: [Redis `EVAL` command](https://redis.io/docs/latest/commands/eval/) — 服务端 Lua 执行与可用版本。
````

### H07 overlap audit

CHUNK_1 -> CHUNK_2  
OVERLAP_PRESENT: YES  
APPROX_OVERLAP_CHARS: 145  
OVERLAP_TEXT_PREVIEW: 脚本执行 compare-and-delete，并通过 `EVAL` 调用：\n\n```lua\nif redis.call('get', KEYS[1]) == ARGV[1] then\n  return redis.call('de...  

CHUNK_2 -> CHUNK_3  
OVERLAP_PRESENT: YES  
APPROX_OVERLAP_CHARS: 147  
OVERLAP_TEXT_PREVIEW: key token NX PX ttl` 获取、用请求唯一 token 标识所有权，并以 Lua/EVAL 原子比较和删除。本文的 Redis >= 2.6.12 是命令兼容基线，不是本地环境版本声明；`EVAL` 的最低版本单独为 2.6...  


## Semantic-quality audit

WARN marks a boundary or context weakness that does not make the underlying chunk technically misleading. N/A means the chunk contains no code fence.

| Chunk | SELF_CONTAINED | SINGLE_PRIMARY_TOPIC | BOUNDARY_COHERENT | CODE_FENCE_INTEGRITY | HEADING_CONTEXT_SUFFICIENT | RETRIEVAL_VALUE | Review note |
|---|---|---|---|---|---|---|---|
| M02-1 | PASS | PASS | PASS | N/A | PASS | HIGH | Metadata identifies JDK 8u HashMap; table/bin model, lookup, and put path remain together. |
| M02-2 | WARN | PASS | WARN | N/A | PASS | HIGH | Starts with the tail “key 时更新…”, but includes load factor, resize threshold, and the full treeification 8/64 boundary. |
| M02-3 | WARN | PASS | WARN | N/A | PASS | MEDIUM | Starts at “64 时…” inside a sentence; it retains the HashMap/JDK 8u correction and references, but adds little beyond chunk 2. |
| M04-1 | PASS | PASS | PASS | N/A | PASS | HIGH | Volatile synchronization, happens-before, visibility/order, and the distinction from mutual exclusion are intact. |
| M04-2 | WARN | PASS | WARN | N/A | PASS | HIGH | Starts with “可能合适…”, then presents the complete count++ lost-update explanation and alternatives. |
| M05-1 | PASS | PASS | PASS | N/A | PASS | HIGH | Proxy model and the self-invocation bypass mechanism are together; Spring 6.2.19 scope is present. |
| M05-2 | WARN | PASS | WARN | N/A | PASS | HIGH | Starts with “或隐式…”, then contains the default proxy-mode transaction consequence, refactoring fix, and AspectJ boundary. |
| M05-3 | PASS | PASS | WARN | N/A | WARN | LOW | Begins in the middle of the JDK dynamic-proxy/CGLIB sentence and is mostly repeated misconception text and references. |
| H06-1 | PASS | PASS | PASS | N/A | PASS | HIGH | DB_TRX_ID, DB_ROLL_PTR, undo reconstruction, and the Read View visibility concept are in one chunk. |
| H06-2 | WARN | PASS | WARN | N/A | PASS | HIGH | Starts with “链提供…”, but carries the RR/RC snapshot distinction and locking-read boundary with their consistent-read context. |
| H06-3 | PASS | PASS | WARN | N/A | PASS | MEDIUM | Starts after “同一” in an RR sentence; it repeats the snapshot/locking boundary and contains the full reference list. |
| H07-1 | PASS | PASS | PASS | PASS | PASS | HIGH | Redis compatibility baseline, SET NX PX, unique-token ownership, and complete Lua code are present. |
| H07-2 | WARN | PASS | WARN | PASS | PASS | HIGH | Starts with the tail “脚本执行…”, but includes a complete Lua block, atomic release explanation, TTL limit, and failure boundaries. |
| H07-3 | WARN | PASS | WARN | N/A | WARN | LOW | Starts inside the SET command phrase and mostly repeats the version boundary and official references. |

## Cross-chunk overlap observations

| Pair | Observation |
|---|---|
| M02-1 → M02-2 | 76 characters repeat the end of the put/resize explanation. The overlap begins at “key 时…” inside a sentence, so it carries useful context but not a clean sentence start. No code fence or URL is involved. |
| M02-2 → M02-3 | 145 characters repeat the capacity-64 correction and nearby text. It begins at “64 时…” inside a sentence; duplication is close to the configured overlap size. No URL is split. |
| M04-1 → M04-2 | 74 characters repeat the tail about when volatile may be suitable and the Important boundary heading. It begins at “可能合适…”, so the overlap is context-bearing but sentence-initial context is clipped. |
| M05-1 → M05-2 | 134 characters repeat the end of the self-invocation explanation, including that the target object is not the external proxy. This preserves context for the transactional consequence. |
| M05-2 → M05-3 | 120 characters repeat the proxy-type misconception. The overlap starts at “dynamic proxy…” within the original sentence; the tail chunk then moves into references. No URL is split. |
| H06-1 → H06-2 | 97 characters repeat the end of the undo-chain/Read View explanation and the Important boundary heading. The repeated phrase begins at “链提供…”, without its preceding “undo”. |
| H06-2 → H06-3 | 134 characters repeat the RR snapshot and locking-read boundary, followed by the Official references heading. This is useful context, though chunk 3 is reference-heavy. |
| H07-1 → H07-2 | 145 characters repeat the EVAL introduction and the complete Lua compare-and-delete example, including both code fences. This duplicates the whole short example rather than a broken fence fragment. |
| H07-2 → H07-3 | 147 characters repeat a tail of the SET/token/version sentence and the Official references heading. The overlap starts inside the SET phrase; no URL or code fence is split. |

Overlap lengths are measured as the longest exact suffix of the preceding returned chunk matching the prefix of the next returned chunk. The observed range is 74–147 UTF-16 code units; all nine consecutive pairs have overlap. Context is generally preserved, but the Chinese sentence starts noted above are clipped. The full Lua example is duplicated intact across H07 chunks 1 and 2.

## Scoring-point retrieval coverage

| Target scoring point | Primary evidence chunks | Coverage |
|---|---|---|
| M02-SP1 — bucket array with linked-list/tree bins | M02-1 | PASS |
| M02-SP2 — hash/bin lookup and same-key value replacement | M02-1 | PASS |
| M02-SP3 — main put path | M02-1 | PASS |
| M02-SP4 — treeification threshold and capacity boundary | M02-2 | PASS |
| M02-SP5 — load factor and resize threshold | M02-2 | PASS |
| M04-SP1 — visibility | M04-1 | PASS |
| M04-SP2 — volatile synchronizes-with / happens-before and ordering | M04-1 | PASS |
| M04-SP3 — compound count++ and lost update | M04-2 | PASS |
| M04-SP4 — volatile versus lock/atomic alternatives | M04-2 | PASS |
| M05-SP1 — proxy interception model | M05-1 | PASS |
| M05-SP2 — JDK dynamic proxy / CGLIB | M05-1 | PASS |
| M05-SP3 — self-invocation bypass and transactional consequence | M05-1, M05-2 | PASS |
| M05-SP4 — proxy-boundary remediation | M05-2 | PASS |
| H06-SP1 — multiple row versions support historical visible reads | H06-1 | PASS |
| H06-SP2 — transaction ID and undo reconstruction | H06-1 | PASS |
| H06-SP3 — Read View / snapshot visibility boundary | H06-1 | PASS |
| H06-SP4 — RR first consistent-read snapshot | H06-2 | PASS |
| H06-SP5 — consistent nonlocking versus locking/current read | H06-2 | PASS |
| H07-SP1 — atomic SET with NX and expiration | H07-1 | PASS |
| H07-SP2 — unique token and ownership-aware release | H07-1, H07-2 | PASS |
| H07-SP3 — atomic Lua/EVAL compare-and-delete | H07-1, H07-2 | PASS |
| H07-SP4 — TTL overrun boundary | H07-2 | PASS |
| H07-SP5 — network/node failure and authoritative data-store boundary | H07-2 | PASS |

Every scoring point has a primary evidence chunk rated HIGH or MEDIUM. No point depends on stitching together two individually meaningless fragments.

## Specific risk checks

| Risk | Result | Evidence |
|---|---|---|
| M02 treeification threshold separated from capacity 64 | PASS | Both conditions appear together in M02-2. |
| M02 same-key update detached from put flow | PASS | Both appear in M02-1; the transition sentence overlaps into M02-2. |
| M02 JDK 8u scope lost | PASS | Explicit metadata in M02-1 and repeated JDK 8u wording in M02-2. |
| M04 volatile happens-before separated from visibility/order | PASS | All are together in M04-1. |
| M04 count++ lost-update argument split | PASS | Complete argument is in M04-2. |
| M04 alternatives detached from compound-update need | PASS | Alternatives immediately follow the count++ argument in M04-2. |
| M05 proxy model separated from self-invocation | PASS | M05-1 contains both; M05-2 overlaps the call-path explanation. |
| M05 @Transactional consequence lacks proxy-mode context | PASS | M05-2 explicitly states default proxy mode and external-call interception. |
| M05 AspectJ boundary confused with proxy rule | PASS | The distinction appears in M05-2 and says the proxy limitation is not the AspectJ rule. |
| H06 DB_TRX_ID, DB_ROLL_PTR, and undo reconstruction split misleadingly | PASS | All are in H06-1, with a contextual overlap into H06-2. |
| H06 RR snapshot rule detached from consistent-read qualifier | PASS | H06-2 explicitly limits the rule to consistent reads. |
| H06 locking-read boundary detached from snapshot context | PASS | H06-2 places locking/current reads directly after the RR/RC snapshot explanation. |
| H07 SET NX PX code fence broken | PASS | SET example is complete in H07-1. |
| H07 token explanation detached from unsafe DEL | PASS | Unique token and the stale-owner DEL race are together in H07-1. |
| H07 Lua code fence broken | PASS | Full Lua code with opening and closing fences appears in H07-1 and H07-2. |
| H07 TTL overrun implies expiry alone guarantees safety | PASS | H07-2 explicitly states that expiration does not stop old client work. |
| H07 Redis >= 2.6.12 baseline lost | PASS | It is explicit in H07-1 metadata and the main SET explanation. |

## P2 findings

No P0 or P1 findings. The following minor issues do not block scoring-point retrieval:

| Document / chunk | Issue and exact evidence | Severity | Smallest candidate fix class |
|---|---|---|---|
| M02-2 | Starts with the mid-sentence fragment “key 时更新对应节点…”. Core resize/treeification evidence remains complete later in the same chunk. | P2 | SOURCE_DOCUMENT_EDIT |
| M02-3 | Starts with “64 时…” after the beginning of the capacity clause; mostly a repeated misconception and references. | P2 | SOURCE_DOCUMENT_EDIT |
| M04-2 | Starts with “可能合适；若需求…”, detached from the prior volatile-status example; count++ evidence remains complete. | P2 | SOURCE_DOCUMENT_EDIT |
| M05-2 | Starts with “或隐式 otherMethod()…”, the second half of the self-invocation sentence; consequence and remediation remain clear. | P2 | SOURCE_DOCUMENT_EDIT |
| M05-3 | Starts within “JDK dynamic proxy…”, then contains mostly references; low retrieval value. | P2 | SOURCE_DOCUMENT_EDIT |
| H06-2 | Starts with “链提供构造旧版本…”, missing the preceding “undo”; RR/locking evidence remains complete. | P2 | SOURCE_DOCUMENT_EDIT |
| H06-3 | Starts after “同一” in an RR sentence; boundary evidence is repeated, followed mostly by references. | P2 | SOURCE_DOCUMENT_EDIT |
| H07-2 | Starts with “脚本执行 compare-and-delete…”, after the omitted Lua-subject phrase; the full code and explanation remain intact. | P2 | SOURCE_DOCUMENT_EDIT |
| H07-3 | Starts inside the SET example phrase (“key token NX PX ttl…”), followed mostly by references; low retrieval value. | P2 | SOURCE_DOCUMENT_EDIT |

