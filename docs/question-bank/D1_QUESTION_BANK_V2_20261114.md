# D1｜63 道 Java 后端正式候选题库 v2

日期：2026-11-14。基线 V1 的原始文件保留；本文件由同版 JSON 生成。新增 42 题，H01 修订答案与 SP4。
题号是内容身份，数据库 ID 在导入验收清单中另行映射。所有题目状态目标为 ENABLED。
参考答案是适合口述的参考表达，评分接受准确等价表述和合理替代方案。独立代理审查不等于真人试答校准。
当前内容共 63 题 / 298 点，新增 42 题 / 201 点。

[逐题审查与修订记录](../verification/D1_QUESTION_BANK_CONTENT_REVIEW_20261114.md) · [版本与指纹清单](D1_BANK_MANIFEST_20261114.json)

## E01｜Interface vs Abstract Class

分类：`JAVA_BASIC`；难度：`EASY`；知识点：`interface_vs_abstract_class`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

Java 中接口和抽象类有什么区别？实际开发中应该如何选择？

### 参考答案

接口更偏向定义一种能力或契约，多个没有共同父类的类都可以实现同一个接口，而且一个类可以实现多个接口。抽象类更适合描述具有共同基础特征的一组类，可以保存实例状态，也可以同时提供已经实现的方法和抽象方法。

Java 类只能继承一个直接父类，但可以实现多个接口。因此，如果几个类只是需要遵守同一套行为规范，我通常会优先使用接口；如果它们本身存在明显的继承关系，而且需要共享状态或公共实现，可以考虑抽象类。

现在接口也可以有 `default`、`static` 等有实现的方法，所以不能简单理解成“接口只能有抽象方法”。两者最主要的差别还是定位：接口强调契约和能力，抽象类更适合共享基类状态和实现。

### 评分点（合计 100）

1. **E01-SP1 · CORE · 30**：能说明接口主要用于定义行为契约或能力，抽象类更适合表达具有共同基础特征和共享实现的类层次。
2. **E01-SP2 · KEY · 25**：能说明 Java 类只能直接继承一个类，但可以实现多个接口，因此接口更适合需要多种能力组合或多个不同实现的场景。
3. **E01-SP3 · KEY · 25**：能说明抽象类可以保存实例状态、定义构造过程并同时包含抽象方法和具体实现；接口不能保存普通对象实例字段。
4. **E01-SP4 · PRINCIPLE · 20**：能根据实际设计场景做出合理选择：共享状态和公共基类实现时考虑抽象类，仅需要稳定契约、解耦或多实现时优先考虑接口，而不是机械地认为其中一种始终更好。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — E01：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/e01_java21_interface_vs_abstract_class.md) — E01：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## E02｜ArrayList vs LinkedList

分类：`JAVA_COLLECTION`；难度：`EASY`；知识点：`arraylist_vs_linkedlist`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

ArrayList 和 LinkedList 在底层结构、常见操作复杂度和实际使用场景上有什么区别？

### 参考答案

ArrayList 底层是可扩容数组，因此根据下标随机访问通常很快，`get` 是 O(1)。尾部追加通常也比较高效，但中间插入或删除往往需要移动后面的元素。

LinkedList 是双向链表，根据下标查找需要从链表中遍历，因此随机访问是 O(n)。如果已经拿到了目标节点附近的位置，链表插入和删除不需要搬移大量元素。

实际开发里不能简单说“增删多就一定用 LinkedList”。很多业务仍然更适合 ArrayList，因为它随机访问好、内存布局更紧凑；只有确实需要大量链表式操作时才考虑 LinkedList。

### 评分点（合计 100）

1. **E02-SP1 · CORE · 30**：能说明 ArrayList 基于动态数组，而 LinkedList 基于双向链表，两者底层存储结构不同。
2. **E02-SP2 · KEY · 25**：能说明 ArrayList 根据下标随机访问通常为 O(1)，而 LinkedList 按下标访问通常需要遍历，为 O(n)。
3. **E02-SP3 · KEY · 25**：能说明 ArrayList 中间插入或删除通常需要移动元素；LinkedList 在已经定位到节点后修改链接本身开销较低，但定位节点仍可能需要 O(n)。
4. **E02-SP4 · PRINCIPLE · 20**：能结合实际访问模式选择集合，而不是机械地认为“增删多就用 LinkedList、查询多就用 ArrayList”。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — E02：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/e02_java21_arraylist_vs_linkedlist.md) — E02：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## E03｜Spring IoC / DI

分类：`SPRING`；难度：`EASY`；知识点：`ioc_di`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

什么是 IoC 和 DI？Spring 容器主要帮开发者解决了什么问题？

### 参考答案

IoC 可以理解为把对象创建、配置以及对象之间依赖关系的管理交给容器，而不是业务代码自己到处 `new` 和查找依赖。

DI 是 IoC 的一种具体实现方式。一个对象声明自己需要什么依赖，例如通过构造方法，然后 Spring 在创建 Bean 时把这些依赖注入进去。

Spring 的 `ApplicationContext` 会负责 Bean 的实例化、配置、组装以及部分生命周期管理。这样业务对象和具体依赖的创建方式解耦，也更方便替换实现和测试。

对必需依赖，实际开发中通常更适合使用构造器注入。

### 评分点（合计 100）

1. **E03-SP1 · CORE · 30**：能说明 IoC 将对象创建、配置以及依赖关系管理的控制权交给 Spring 容器，而不是由业务对象自行创建和查找所有依赖。
2. **E03-SP2 · KEY · 25**：能说明 DI 是 IoC 的主要实现方式，对象声明所需依赖，由容器在创建 Bean 时通过构造器、Setter 等方式提供依赖。
3. **E03-SP3 · PRINCIPLE · 25**：能说明 IoC/DI 可以降低业务类与具体依赖创建方式之间的耦合，使实现替换和单元测试更加容易。
4. **E03-SP4 · KEY · 20**：能说明 Spring 容器除了注入依赖之外，还负责 Bean 的实例化、配置、组装以及相关生命周期管理。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — E03：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/e03_spring_6_2_ioc_di.md) — E03：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## E04｜Transaction ACID

分类：`MYSQL`；难度：`EASY`；知识点：`transaction_acid`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

MySQL 事务的 ACID 分别指什么？为什么数据库需要事务？

### 参考答案

A 是原子性，一组操作要么全部成功，要么全部回滚；C 是一致性，事务执行前后数据库应该满足定义好的数据约束和业务不变量；I 是隔离性，并发事务之间需要按照一定隔离规则运行；D 是持久性，事务提交成功以后结果应该能够被持久保存。

事务的意义是把多个相关数据库操作组成一个逻辑工作单元。例如转账不能只扣一个账户的钱却没有给另一个账户加钱。

不同隔离级别会在并发性能和隔离程度之间做权衡。

### 评分点（合计 100）

1. **E04-SP1 · CORE · 20**：能说明原子性表示事务中的操作作为一个整体成功或整体回滚。
2. **E04-SP2 · KEY · 20**：能说明一致性表示事务执行前后数据仍满足数据库约束和业务不变量。
3. **E04-SP3 · KEY · 20**：能说明隔离性用于控制并发事务之间的相互影响，不同隔离级别提供不同程度的隔离保证。
4. **E04-SP4 · KEY · 20**：能说明持久性表示事务成功提交后的结果应当被可靠保存。
5. **E04-SP5 · PRINCIPLE · 20**：能说明事务用于把多个相关数据库操作组成一个逻辑工作单元，避免出现部分成功导致的业务不一致。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — E04：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/e04_mysql_8_0_transaction_acid.md) — E04：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## E05｜TCP vs UDP

分类：`NETWORK`；难度：`EASY`；知识点：`tcp_vs_udp`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

TCP 和 UDP 有什么主要区别？实际开发中分别适合哪些场景？

### 参考答案

TCP 是面向连接的可靠字节流协议，在通信前需要建立连接，并提供顺序、重传、流量控制和拥塞控制等机制。

UDP 面向数据报，不需要先建立 TCP 这样的连接，也不保证数据一定到达、按顺序到达或者自动重传，因此协议机制更简单、额外开销也更少。

对可靠、有序传输要求较高的业务，例如数据库连接和很多传统应用层协议，通常会使用 TCP。实时音视频和部分 DNS 可以使用 UDP。QUIC 是基于 UDP 的传输协议，可靠性与拥塞控制由 QUIC 提供，而不是由 UDP 本身提供。

UDP 并不代表应用一定“不可靠”，而是这些保证不是由 UDP 本身提供。

### 评分点（合计 100）

1. **E05-SP1 · CORE · 30**：能说明 TCP 是面向连接的可靠字节流协议，而 UDP 是面向数据报、无 TCP 式连接建立过程的传输协议。
2. **E05-SP2 · KEY · 25**：能说明 TCP 提供顺序、确认、丢失重传等可靠传输机制，而 UDP 本身不保证数据一定到达或保持顺序。
3. **E05-SP3 · KEY · 20**：能说明 TCP 还具有流量控制、拥塞控制等机制，因此协议状态和开销通常高于 UDP。
4. **E05-SP4 · PRINCIPLE · 25**：能根据可靠性、时延、应用层或上层协议控制需求选择协议，并认识到 UDP 本身不提供 TCP 式可靠性保证。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — E05：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/e05_rfc9293_rfc768_tcp_vs_udp.md) — E05：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## E06｜异常分类与资源关闭责任

分类：`JAVA_BASIC`；难度：`EASY`；知识点：`exceptions_resource_basics`；修订：1；来源：D1_NEW。

适用版本：Java SE 21

### 面试问题

Java 方法从文件读取内容时，可能抛出 IOException，调用者传入非法参数时，也可能抛出 IllegalArgumentException。这两类异常在编译时分别有什么要求？读取代码如何保证自己打开的文件在读失败时也能关闭？什么时候适合在当前方法捕获读取异常？

### 参考答案

IOException 是受检异常。编译器要求方法处理可能抛出的受检异常，例如用 try/catch 捕获，或在方法签名用 throws 声明并交由调用方处理。IllegalArgumentException 是 RuntimeException 的子类，属于非受检异常，编译器不要求显式捕获或声明，但它仍能传播给调用方。

我会把自己打开的文件流放进 try-with-resources。只要流成功初始化，离开 try 块时就会调用 close()，读取失败也一样。我只在能恢复或能给调用方提供清楚的业务语义时捕获，避免吞掉异常或在读取失败后返回假成功。

### 评分点（合计 100）

1. **E06-SP1 · CORE · 30**：区分 IOException 是受检异常，并说明在此处捕获或通过 throws 声明向调用方传播。
2. **E06-SP2 · KEY · 25**：区分 IllegalArgumentException 是非受检异常，编译器没有捕获或声明的要求，但异常仍可向外传播。
3. **E06-SP3 · KEY · 30**：说明用 try-with-resources 管理自己打开的 AutoCloseable 文件流，成功取得的资源在正常或异常退出时均调用 close。
4. **E06-SP4 · PRINCIPLE · 15**：提出有实际处理意义的捕获策略，如返回明确失败、合理恢复或保留原因后转成业务异常，避免吞掉失败。

### 技术依据

- [Java 语言规范 SE 21 · 第 11 章](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2) — §11.1.1、§11.2：受检与非受检异常的分类和编译时检查。
- [Java 语言规范 SE 21 · 第 14 章](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3) — §14.20.3：成功初始化的资源在退出时自动关闭。

覆盖差异：与 E01 的接口和类设计不同。与 M08 共用资源语法背景，本题只考异常处理义务和基本关闭责任，M08 才考多个关闭异常的传播顺序。

可接受表达／替代方案：

接受用等价异常或明确说明怎样正确使用 finally 关闭资源，能说明 try-with-resources 的保证即可。

应识别的误解：

受检异常一定要当场捕获。非受检异常不会传播。只在读取成功后调用 close 就足够。

## E07｜String 不可变与字符串比较

分类：`JAVA_BASIC`；难度：`EASY`；知识点：`strings_immutability`；修订：1；来源：D1_NEW。

适用版本：Java SE 21

### 面试问题

两个 String 显示的文字相同，为什么用 == 可能得到 false？如果调用 s.replace(...) 后不接收返回值，s 的内容会变吗？在循环里反复追加文本时，你会怎么写？你会让多个线程共用同一个构建器吗？

### 参考答案

== 对引用比较的是不是同一个对象。内容相同可以来自不同对象，因此比较文本应该用 equals。如果两边可能为 null，可以用 Objects.equals。

String 对象一旦创建，字符序列就不会原地变化。replace 的结果通过返回值得到，不接收就不会改变 s 所引用的对象。给 s 重新赋值，是让变量引用别的对象。循环里要反复追加，可以在当前方法内复用一个 StringBuilder，用 append 累积，最后再用 toString 得到结果。StringBuilder 可变且不保证线程安全，我会让它只由当前线程使用。

### 评分点（合计 100）

1. **E07-SP1 · CORE · 35**：说明 == 比较引用身份，String.equals 比较字符序列内容，文本相同并不保证同一对象。
2. **E07-SP2 · KEY · 30**：说明 replace 不会修改原 String，调用者需要使用返回值，变量重新赋值和对象内容变化是两回事。
3. **E07-SP3 · KEY · 25**：说明循环累积场景可复用 StringBuilder，通过 append 构建后再 toString。
4. **E07-SP4 · PRINCIPLE · 10**：说明 StringBuilder 不保证线程安全，适合由当前线程独占，不能无保护地多线程共享。

### 技术依据

- [Java 语言规范 SE 21 · 第 15 章](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.21.3) — §15.21.3：引用 == 比较对象身份，并提示字符串内容比较使用 equals。
- [Java SE 21 API · String](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html#replace(char,char)) — 类概述、equals、replace：String 不可变，equals 判断内容，replace 返回结果。
- [Java SE 21 API · StringBuilder](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/StringBuilder.html) — 类概述：StringBuilder 是可变字符序列，提供 append 和 toString，且不保证同步。
- [Java SE 21 API · Objects](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Objects.html#equals(java.lang.Object,java.lang.Object)) — equals(Object,Object)：两个参数可能为 null 时可用 Objects.equals 比较。

覆盖差异：与 E01 的类型设计不同。M01 虽涉及 equals，但考自定义对象哈希契约，本题考 String 的值比较、不可变与构建。

可接受表达／替代方案：

允许用 literal 与 new String(...)、变量重新赋值、Objects.equals 等等价例子。

应识别的误解：

用 == 比字符串文本。认为 replace 改写 String 对象。断言所有 + 拼接都会创建 StringBuilder，或认为 StringBuilder 可直接并发写。

## E08｜List、Set、Map 的语义与选择

分类：`JAVA_COLLECTION`；难度：`EASY`；知识点：`collection_semantics`；修订：1；来源：D1_NEW。

适用版本：Java SE 21

### 面试问题

一个导入程序要按原顺序保存每条处理结果（允许重复），另外要记录每个出现过的用户 ID，并能按 ID 查询该用户最新的一条结果。这三份数据分别适合用哪种 Java 集合接口？如果去重后的 ID 也要按首次出现顺序输出，怎么选？

### 参考答案

逐条结果用 List。它能保留元素顺序，常见实现也允许相同元素重复。只记录出现过的 ID，可以用 Set，一个 ID 只保留一份。按 ID 查最新结果，用 Map，以 ID 为键，处理同一 ID 的新结果时替换该键对应的值。

如果去重后的 ID 还需要按首次加入的顺序输出，可以选 LinkedHashSet。Set 接口本身不保证首次加入顺序，要看具体实现。HashSet 没有这个顺序承诺。

### 评分点（合计 100）

1. **E08-SP1 · CORE · 25**：为按输入顺序保存全部处理记录选择允许重复的 List 实现，说明它保留位置顺序。
2. **E08-SP2 · KEY · 25**：为 ID 去重选择 Set，说明它用于保存不重复元素。
3. **E08-SP3 · KEY · 30**：为按 ID 查询最新结果选择 Map，说明每个键最多关联一个值，重复出现可更新对应值。
4. **E08-SP4 · PRINCIPLE · 20**：说明需按首次出现顺序遍历去重结果时选 LinkedHashSet 或能保证同样顺序的实现，不能依赖 HashSet 的遍历顺序。

### 技术依据

- [Java SE 21 API · List](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/List.html) — 类概述：List 保留顺序，通常允许重复。
- [Java SE 21 API · Set](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Set.html) — 类概述：Set 不含重复元素。
- [Java SE 21 API · Map](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html) — 类概述：Map 按键关联值，每个键最多对应一个值。
- [Java SE 21 API · LinkedHashSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashSet.html) — 类概述：LinkedHashSet 默认按插入顺序遍历，普通重复 add 不改变顺序。

覆盖差异：与 E02 的 ArrayList/LinkedList 实现取舍不同。本题先从保序、去重和按键索引的业务语义选接口。E09 关注两端操作和进出顺序。

可接受表达／替代方案：

允许举 ArrayList、HashSet、HashMap 等具体实现。若用 LinkedHashMap 的键视图实现首次出现的唯一 ID，需说明如何保序。

应识别的误解：

所有 Set 都无序或都会保留插入顺序。用一个普通 Map 保留同一 ID 的全部记录。把 Set 理解为按索引读取。

## E09｜用 Deque 表达栈和队列

分类：`JAVA_COLLECTION`；难度：`EASY`；知识点：`deque_stack_queue`；修订：1；来源：D1_NEW。

适用版本：Java SE 21

### 面试问题

现在有两个单线程场景，一个是撤销最近一次编辑，一个是按到达顺序处理待办。怎么用 Java 的 Deque 分别实现？以 ArrayDeque 为例，如果还需要固定容量或多线程共享，你会考虑什么？

### 参考答案

撤销用后进先出，在同一端存和取，例如 push 进头部，pop 从头部取最新的操作。待办用先进先出，例如 offerLast 从尾部加入，pollFirst 从头部取最早的任务。空队列时 pollFirst 返回 null，pop 则会抛出异常，所以按调用方约定处理空状态。

单线程中可以用 ArrayDeque。构造时的容量参数用于初始容量，实例会按需要增长，没有固定业务上限，也不保证线程安全。如果要并发访问并限制待办数量，可以用显式设了容量的 LinkedBlockingDeque，再决定满时等待还是返回失败。

### 评分点（合计 100）

1. **E09-SP1 · CORE · 30**：能用 Deque 同一端入出实现栈并说明后进先出，例如 push/pop。
2. **E09-SP2 · KEY · 30**：能用 Deque 不同端入出实现队列并说明先进先出，例如 offerLast/pollFirst。
3. **E09-SP3 · KEY · 20**：能区分 ArrayDeque 的初始容量与固定上限，说明固定容量需要额外限制或有界实现。
4. **E09-SP4 · PRINCIPLE · 20**：知道 ArrayDeque 本身不保证线程安全，能为并发共享选择合适的并发实现或正确加锁。

### 技术依据

- [Java SE 21 API · Deque](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Deque.html) — 类概述和方法对应表：Deque 支持从同端模拟栈或从不同端模拟队列，并提供不同的空/满返回约定。
- [Java SE 21 API · ArrayDeque](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayDeque.html) — 类概述和 ArrayDeque(int)：可扩容且非线程安全，构造参数不构成固定上限。
- [Java SE 21 API · LinkedBlockingDeque](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/LinkedBlockingDeque.html#%3Cinit%3E(int)) — 类概述和 LinkedBlockingDeque(int)：双端阻塞队列可用显式容量限制。

覆盖差异：与 E02 的数组与链表复杂度和 E08 的通用容器语义不同，本题按操作顺序选择端点，识别实现的容量和并发边界。

可接受表达／替代方案：

接受 addFirst/removeFirst、addLast/removeFirst 等等价配对。可以建议有边界和溢出处理的外部同步方案。

应识别的误解：

栈和队列都从入队端取元素。认为 ArrayDeque 构造参数限定最大元素数，或把 Deque 接口当成并发保证。

## E10｜Bean 常用作用域与单例状态

分类：`SPRING`；难度：`EASY`；知识点：`bean_scope_basics`；修订：1；来源：D1_NEW。

适用版本：Spring Framework 6.2（Spring Boot 3.5）

### 面试问题

Spring 的 singleton、prototype 和 request 作用域分别在什么范围复用 Bean？一个默认作用域的 Service 把当前请求的用户 ID 暂存在实例字段里，并发请求时会有什么风险？

### 参考答案

Spring 默认的 singleton 是同一个容器里，同一个 Bean 定义复用同一个实例。它不保证整个 JVM 中这个 Java 类只有一个对象。prototype 则是每次向容器请求该 Bean 时得到新实例，例如重新查找或为另一个注入点解析依赖。把一个 prototype 直接注入单例，并不会让单例每次调用方法都拿到新对象。

request 是 Web 应用的作用域，同一个 HTTP 请求内复用，下一个请求有独立实例。默认 Service 被多个请求共用，把用户 ID 放到实例字段就可能被并发请求互相覆盖。通常把请求数据保存在方法参数和局部变量里，需要请求级组件时再考虑 request 作用域。

### 评分点（合计 100）

1. **E10-SP1 · CORE · 30**：说明 singleton 是默认作用域，在单个容器中按 Bean 定义复用实例，并非按 Java 类在整个 JVM 全局唯一。
2. **E10-SP2 · KEY · 20**：说明 prototype 每次向容器取得该 Bean 时创建新实例，普通注入到单例的引用不会在每次业务调用时自动替换。
3. **E10-SP3 · KEY · 20**：说明 Web request 作用域按 HTTP 请求复用，同一请求与不同请求的实例关系正确。
4. **E10-SP4 · PRINCIPLE · 30**：识别默认 Service 的请求级可变字段会被不同请求共享并可能串数据，给出方法参数、局部变量或正确的请求级组件等做法。

### 技术依据

- [Spring Framework 6.2 · Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html) — The Singleton Scope / The Prototype Scope / Request Scope / Singleton Beans with Prototype-bean Dependencies：作用域的实例复用范围及单例注入 prototype 的边界。

覆盖差异：与 E03 的依赖由谁创建和注入不同。本题考实例复用及请求状态隔离。E11 考注入后的初始化与销毁时机。

可接受表达／替代方案：

可用 request-scoped 组件、方法局部变量或传递不可变请求上下文解释隔离。无需背代理细节。

应识别的误解：

把 Spring singleton 理解为每个类 JVM 只有一个实例，或假设 singleton 自动线程安全、注入的 prototype 每次方法调用都会重建。

## E11｜Bean 初始化与资源释放

分类：`SPRING`；难度：`EASY`；知识点：`bean_lifecycle_basics`；修订：1；来源：D1_NEW。

适用版本：Spring Framework 6.2（Spring Boot 3.5）

### 面试问题

一个 Spring 容器管理的单例 Bean 需要在依赖就绪后检查配置，并在应用正常关闭时释放它自己持有的资源。这两个动作应该放在哪类回调中？如果同一个类由业务代码直接 new，情况有什么不同？

### 参考答案

对于普通容器 Bean，Spring 先创建对象，提供构造器依赖，按配置完成属性等依赖注入。依赖到位后可以通过 @PostConstruct 做初始化，例如校验配置。这个检查属于受管 Bean 的初始化流程。

在应用上下文正常关闭时，可用 @PreDestroy 关闭该 Bean 自己持有的资源，例如它创建的客户端。直接 new 出来的对象如果没有交给容器管理，Spring 不会自动调用这些回调，创建者要承担相应的初始化和清理责任。进程被强制结束时也不能依赖容器关闭回调执行。

### 评分点（合计 100）

1. **E11-SP1 · CORE · 30**：说明实例化、注入依赖和初始化的先后关系，知道依赖就绪后的检查放在初始化阶段。
2. **E11-SP2 · KEY · 25**：给出有效的初始化回调机制，例如 @PostConstruct 或配置 initMethod。
3. **E11-SP3 · KEY · 30**：说明应用上下文正常关闭时可用 @PreDestroy 或配置 destroyMethod 释放该受管单例自己持有的资源。
4. **E11-SP4 · PRINCIPLE · 15**：说明仅由业务代码 new 且未交给容器管理的实例不会自动获得 Spring 生命周期回调，使用方负责清理。

### 技术依据

- [Spring Framework 6.2 · Customizing the Nature of a Bean](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html#beans-factory-lifecycle) — Lifecycle Callbacks / Initialization Callbacks / Destruction Callbacks：初始化、正常销毁及受管对象的配置回调。
- [Spring Framework 6.2 · Using @PostConstruct and @PreDestroy](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/postconstruct-and-predestroy-annotations.html) — Using @PostConstruct and @PreDestroy：容器处理 jakarta.annotation 生命周期注解。

覆盖差异：与 E03 的 IoC/DI 定义不同。本题追问依赖就绪后的初始化和正常停机后的资源释放，与 E10 的实例复用范围分开。

可接受表达／替代方案：

接受 InitializingBean/DisposableBean、@Bean(initMethod=..., destroyMethod=...) 等确实由 Spring 调用的方式。

应识别的误解：

认为构造器之后一定已完成所有注入、注解对任意 new 实例自动生效，或把正常停机回调当成强制终止也有保证的机制。

## E12｜主键、唯一约束与 NULL

分类：`MYSQL`；难度：`EASY`；知识点：`mysql_constraints_null`；修订：1；来源：D1_NEW。

适用版本：MySQL 8.0 / InnoDB

### 面试问题

用户表要有稳定的行 ID，邮箱选填，但填了的邮箱不能重复。用 MySQL 8.0 怎样声明 ID 和邮箱约束？两条邮箱都为 NULL 的记录是否冲突，又该怎样查询没填写邮箱的用户？空字符串也会被这个查询找到吗？

### 参考答案

可以用 id 作 PRIMARY KEY，主键唯一且不允许 NULL。邮箱列允许 NULL，再对整列建立 UNIQUE 约束。按该列排序规则比较相等的非 NULL 邮箱会冲突，但 MySQL 的可空唯一列允许多个 NULL，所以未填邮箱的用户可以有多条。

查询没填写的用户用 WHERE email IS NULL。email = NULL 的结果是 UNKNOWN，WHERE 只保留为真的行。如果业务要求所有用户必填且唯一，就给 email 同时加 NOT NULL 和 UNIQUE。空字符串和 NULL 是两个值，应用可以在写入前按业务规则统一处理。

### 评分点（合计 100）

1. **E12-SP1 · CORE · 25**：说明以 id 做主键，主键约束同时保证行标识唯一且非空。
2. **E12-SP2 · KEY · 30**：为选填邮箱使用允许 NULL 的完整列 UNIQUE 约束，说明非空相等邮箱冲突而多个 NULL 可以共存。
3. **E12-SP3 · KEY · 30**：正确写出或口述 IS NULL 并解释普通的 = NULL 不产生 TRUE。
4. **E12-SP4 · PRINCIPLE · 15**：能区分空字符串与 NULL，说明 IS NULL 不匹配空字符串，业务若要把空字符串视作未填需另做归一化或筛选。

### 技术依据

- [MySQL 8.0 Reference Manual · CREATE TABLE](https://dev.mysql.com/doc/refman/8.0/en/create-table.html) — PRIMARY KEY / UNIQUE：主键列 NOT NULL，UNIQUE 可空列允许多个 NULL。
- [MySQL 8.0 Reference Manual · Working with NULL Values](https://dev.mysql.com/doc/refman/8.0/en/working-with-null.html) — Working with NULL Values：NULL 的比较、IS NULL 和空字符串的区别。

覆盖差异：与 E04 的事务 ACID 不同。本题考表约束和空值表达，与 E13 的连接结果补 NULL 也有独立目标。

可接受表达／替代方案：

接受内联约束或表级约束语法。不要求 DDL 字符串逐字一致。

应识别的误解：

认为 UNIQUE 一定禁止多个 NULL、空字符串等于 NULL，或用 email = NULL 查询空值。

## E13｜INNER JOIN 与 LEFT JOIN 的结果差异

分类：`MYSQL`；难度：`EASY`；知识点：`mysql_join_basics`；修订：1；来源：D1_NEW。

适用版本：MySQL 8.0 / InnoDB

### 面试问题

有 users 和 orders 两张表。想列出所有用户及其已支付订单，没有已支付订单的用户也要保留。INNER JOIN 和 LEFT JOIN 的结果有何不同？你会把 o.status = 'PAID' 放在 ON 还是 WHERE，为什么？

### 参考答案

INNER JOIN 只保留两边满足连接条件的行，没匹配到订单的用户就没有结果行。这里我会用 users u LEFT JOIN orders o ON o.user_id = u.id AND o.status = 'PAID'。这样先规定右表只匹配已支付订单。没有任何订单或者只有未支付订单的用户仍被保留，订单列补 NULL。

如果只按用户 ID 做 LEFT JOIN，再写 WHERE o.status = 'PAID'，补 NULL 的行无法让这个条件为 TRUE，只有未支付订单的用户也会被过滤，所以这次需求无法满足。

### 评分点（合计 100）

1. **E13-SP1 · CORE · 25**：说明 INNER JOIN 只返回满足连接条件的左右匹配行。
2. **E13-SP2 · KEY · 25**：说明 LEFT JOIN 保留左表未匹配行，并将右表字段补 NULL。
3. **E13-SP3 · KEY · 30**：在本场景把已支付条件放入 ON 或先在右表子查询内过滤，保证没有已支付订单的用户保留。
4. **E13-SP4 · PRINCIPLE · 20**：解释若将右表支付状态等值条件放到 WHERE，补 NULL 行不会满足条件，未匹配用户因而丢失。

### 技术依据

- [MySQL 8.0 Reference Manual · JOIN Clause](https://dev.mysql.com/doc/refman/8.0/en/join.html) — ON / WHERE / LEFT JOIN：ON 描述连接匹配，LEFT JOIN 无匹配时补右表 NULL，WHERE 过滤结果。
- [MySQL 8.0 Reference Manual · Outer Join Simplification](https://dev.mysql.com/doc/refman/8.0/en/outer-join-simplification.html) — Null-rejected conditions：右表谓词对补 NULL 行不可能为真时，该行被过滤。

覆盖差异：与 E04 的事务语义不同。E12 考存储和筛选 NULL，这道题考外连接生成补 NULL 行和筛选顺序。

可接受表达／替代方案：

可先把 orders 按已支付过滤成派生表再左连接，不要求写完整 SELECT。

应识别的误解：

认为 LEFT JOIN 无论 WHERE 怎么写都保留左表。用 WHERE status='PAID' OR status IS NULL 冒充通用等价写法，漏掉只有未支付订单的用户。

## E14｜HTTP 方法和常见状态码

分类：`NETWORK`；难度：`EASY`；知识点：`http_methods_status`；修订：1；来源：D1_NEW。

适用版本：IETF RFC 9110 · HTTP 语义

### 面试问题

给任务管理系统设计两个 HTTP 接口，一个查询任务，一个创建任务。你会分别选择 GET 还是 POST？查询成功、创建成功、参数错误、查无此任务和服务端意外异常时，可以分别返回什么状态码？怎样用安全性和幂等性解释两种方法的差异？

### 参考答案

查询单个任务一般用 GET，创建由服务端分配 ID 的任务可以用 POST。查询成功用 200，创建成功用 201，有需要时通过 Location 告知新资源地址。参数格式错误用 400，资源不存在用 404，服务端遇到未预期的错误用 500。通常 2xx 表示成功，4xx 表示请求侧问题，5xx 表示服务器无法完成请求。

GET 的标准语义是安全的，客户端请求读取，不要求服务端改变资源状态；服务端记访问日志并不冲突。GET 也是幂等的，多次相同请求的预期服务端效果与一次相同，响应内容仍可能因数据变化不同。POST 没有通用幂等保证，重复提交可能创建多条。

### 评分点（合计 100）

1. **E14-SP1 · CORE · 25**：为读取现有任务选择 GET，为服务端处理创建请求选择 POST，并与接口意图对应。
2. **E14-SP2 · KEY · 20**：正确匹配查询和创建成功，例如 200 和 201，并知道它们属于 2xx。
3. **E14-SP3 · KEY · 20**：合理匹配参数格式错误、资源不存在和服务器意外异常，例如 400、404、500。
4. **E14-SP4 · PRINCIPLE · 15**：说明安全性看请求表达的意图，GET 请求读取，服务端正常记日志等附带动作不否定安全性。
5. **E14-SP5 · PRINCIPLE · 20**：说明幂等比较多次相同请求的预期服务端效果，GET 具备该语义而 POST 无通用保证，幂等不要求每次响应完全相同。

### 技术依据

- [RFC 9110 · HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.2.1) — §9.2.1–§9.2.2、§9.3.1、§9.3.3：方法安全性和幂等性，以及 GET / POST 语义。
- [RFC 9110 · HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html#section-15) — §15.3.1、§15.3.2、§15.5.1、§15.5.5、§15.6.1：200、201、400、404、500 的含义。

覆盖差异：与 E05 的 TCP/UDP 传输选择不同。本题只考应用层方法与响应语义，H25 才考发生超时后的重试决策和业务幂等。

可接受表达／替代方案：

如果明确区分参数语法错误与语义校验失败，接受后者使用 422。状态码选择需与候选人明示的接口语义一致。

应识别的误解：

把安全性当成 HTTPS 加密。认为幂等要求响应完全相同，或断言所有 POST 必然产生副作用。

## E15｜从域名到服务地址的 DNS 查询

分类：`NETWORK`；难度：`EASY`；知识点：`dns_resolution_basics`；修订：1；来源：D1_NEW。

适用版本：IETF RFC 1034 / RFC 9499 · 常规 DNS 地址查询

### 面试问题

客户端访问一个未缓存的普通站点域名，从发起查询到拿到 IP 大致会经过谁？在有缓存时，TTL 起什么作用？拿到地址就意味着已经和站点建立 TCP 连接了吗？

### 参考答案

客户端通常经本机的 DNS 客户端向配置好的递归解析器请求地址，递归解析器负责给客户端返回最终结果或查询失败。以解析器不转发、且没有可用缓存为例，它可以从根服务器得到顶级域的指引，再向顶级域问到目标域的权威服务器，最后从权威侧得到地址记录。每一级返回的可以只是下一站信息。

解析器可以按记录的 TTL 缓存结果，有有效缓存时就能减少上游查询。A、AAAA 记录分别提供 IPv4、IPv6 地址。拿到地址只是解析完成。若应用选择基于 TCP 通信，之后还要用地址和端口建立到服务端的 TCP 连接。

### 评分点（合计 100）

1. **E15-SP1 · CORE · 25**：区分客户端通常请求递归解析器，递归解析器负责给出最终答案或失败结果。
2. **E15-SP2 · KEY · 30**：在无可用缓存且直接向权威层级查询时，能概括根、顶级域与目标域权威服务的转介链。
3. **E15-SP3 · KEY · 25**：说明 TTL 控制 DNS 记录可缓存多久，有有效缓存时可减少上游查询。
4. **E15-SP4 · PRINCIPLE · 20**：区分 DNS 取得地址和应用连接服务端，知道使用 TCP 的应用需要另行建连。

### 技术依据

- [RFC 1034 · Domain Names—Concepts and Facilities](https://www.rfc-editor.org/rfc/rfc1034.html#section-5.3.3) — §3.6、§4.3.1、§5.3.3：递归和非递归模式、解析步骤、记录 TTL 与地址记录。
- [RFC 9499 · DNS Terminology](https://www.rfc-editor.org/rfc/rfc9499.html#section-5) — §5 Resource Records、§6 DNS Servers and Clients：TTL、解析器与递归服务术语。
- [RFC 3596 · DNS Extensions to Support IP Version 6](https://www.rfc-editor.org/rfc/rfc3596.html#section-2) — §2 AAAA Resource Record Type：AAAA 记录携带 IPv6 地址。
- [RFC 9293 · Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html#section-3.5) — §3.5 Establishing a Connection：应用目标的 TCP 连接建立是独立的传输层过程。

覆盖差异：与 E05 的 TCP/UDP 传输性质不同。本题考域名解析的责任链、转介和缓存，无需复述 H08 的 TCP 连接状态。

可接受表达／替代方案：

允许提到本机缓存、hosts、转发解析器或 CNAME，前提是能说清常见缓存未命中路径。无需背 DNS 报文格式。

应识别的误解：

认为客户端必然自己依次访问所有权威层级，或认为 TTL 等于全网同步时间、解析成功就是应用 TCP 已连通。

## M01｜equals / hashCode Contract

分类：`JAVA_BASIC`；难度：`MEDIUM`；知识点：`equals_hashcode_contract`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

`equals()` 和 `hashCode()` 之间有什么约定？如果只重写 `equals()` 而不重写 `hashCode()`，在 HashMap / HashSet 中可能出现什么问题？

### 参考答案

`equals` 用于定义对象的逻辑相等关系。Java 的基本约定是，如果两个对象 `equals` 返回 true，那么它们的 `hashCode` 必须相同；但两个对象 hashCode 相同并不代表它们一定 equals。

HashMap、HashSet 这类基于哈希的数据结构通常先根据 hash 定位候选位置，再通过 equals 判断逻辑相等。

如果两个逻辑上相等的对象产生不同 hash，就可能被分到不同位置，导致原本应该相同的 key 查找不到，或者 Set 中出现逻辑重复对象。

因此重写 equals 时通常必须同时按照同一相等语义重写 hashCode。

### 评分点（合计 100）

1. **M01-SP1 · CORE · 25**：能说明 equals 用于判断对象的逻辑相等关系，而 hashCode 提供供哈希数据结构使用的哈希值。
2. **M01-SP2 · KEY · 25**：能明确说明 equals 相等的两个对象必须具有相同 hashCode，但 hashCode 相同的两个对象不一定 equals。
3. **M01-SP3 · PRINCIPLE · 30**：能解释 HashMap/HashSet 通常先根据 hash 缩小候选位置，再使用 equals 判断 key 是否真正相等，因此 equals 与 hashCode 契约必须保持一致。
4. **M01-SP4 · KEY · 20**：能说明只重写 equals 而不正确重写 hashCode 可能导致逻辑相等对象无法按预期查找、去重或作为同一个 key 使用。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — M01：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/m01_java21_equals_hashcode_contract.md) — M01：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## M02｜HashMap put / resize

分类：`JAVA_COLLECTION`；难度：`MEDIUM`；知识点：`hashmap_put_resize`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

JDK 8 中 HashMap 的底层结构是什么？一次 `put` 操作大致经历哪些步骤？什么时候链表会转成红黑树？

### 参考答案

JDK 8 的 HashMap 可以理解为数组加链表加红黑树。底层有一个桶数组，每个 key 先计算 hash，再根据数组长度定位到对应桶。

`put` 时，如果数组还没有初始化，会先初始化或扩容；然后根据 hash 定位桶。如果桶为空就直接插入。如果桶里已经有节点，会先判断是不是相同 key；相同就更新 value。发生哈希冲突时，如果当前桶是普通链表，就沿链表查找或追加；如果已经是红黑树，则按树结构插入。

树化条件需要注意实现细节：向一个已经至少有 8 个节点的桶继续插入元素时会尝试 treeify；如果当前 table capacity 小于 64，则优先 resize，而不是直接树化。面试中通常可以概括为“树化阈值是 8，并且容量至少需要达到 64”。

HashMap 默认负载因子是 0.75，当元素数量超过 threshold 时会触发扩容。扩容的目的之一是降低桶冲突概率。

### 评分点（合计 100）

1. **M02-SP1 · CORE · 25**：能说明 JDK 8 HashMap 以桶数组为基础，哈希冲突时桶内可以形成链表，在满足条件后可以转换为红黑树。
2. **M02-SP2 · PRINCIPLE · 20**：能说明 put 会根据 key 的 hash 计算桶位置，并通过 hash 与 key 相等性判断已有映射，因此相同 key 会更新已有 value 而不是无条件新增节点。
3. **M02-SP3 · KEY · 25**：能描述 put 的主要路径：必要时初始化或扩容、定位桶、空桶直接插入，冲突时在链表或红黑树中查找或插入。
4. **M02-SP4 · INTERNAL · 20**：能说明 JDK 8 树化的关键条件：向已经至少有 8 个节点的桶继续插入时会尝试树化；如果 table capacity 小于 64，则优先 resize。评测时接受“树化阈值 8 且容量至少 64”的常见面试表达。
5. **M02-SP5 · KEY · 10**：能说明 HashMap 通过容量阈值控制整体扩容，默认 load factor 为 0.75，元素数量超过当前 threshold 后会进行 resize。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — M02：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/m02_hashmap_jdk8u_put_resize_treeify.md) — M02：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## M03｜JVM Runtime Data Areas

分类：`JVM`；难度：`MEDIUM`；知识点：`runtime_data_areas`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

JVM 运行时数据区主要有哪些？哪些区域是线程私有的，哪些是线程共享的？对象、局部变量、类元数据等通常分别位于哪里？

### 参考答案

JVM 规范定义了堆、方法区、运行时常量池、Java 虚拟机栈、PC 寄存器以及本地方法栈等运行时区域。

PC 寄存器、JVM 栈以及本地方法栈按线程创建。每次 Java 方法调用会对应一个栈帧，里面包含局部变量表、操作数栈等内容。

堆是 JVM 各线程共享的区域，普通 Java 对象通常分配在堆上。方法区也是共享区域，用于保存类级别的数据；运行时常量池是与类或接口相关的运行时数据结构。

需要区分 JVM 规范概念和具体 HotSpot 实现，例如 Metaspace 是 HotSpot 对方法区相关内容的一种具体实现方式，不能简单把“方法区”和“Metaspace”完全当成同一个规范概念。

### 评分点（合计 100）

1. **M03-SP1 · CORE · 20**：能说出 Heap、Method Area、Java Virtual Machine Stack 和 PC Register 这些主要运行时数据区域。
2. **M03-SP2 · KEY · 20**：能区分线程私有与共享区域：PC Register、JVM Stack、Native Method Stack 按线程创建，而 Heap 和 Method Area 为 JVM 线程共享区域。
3. **M03-SP3 · KEY · 25**：能说明普通对象通常位于 Heap，方法局部变量位于栈帧的局部变量表中，并区分引用变量本身与其指向的对象。
4. **M03-SP4 · PRINCIPLE · 20**：能说明方法调用通过 JVM Stack 中的 Frame 执行，Frame 包含局部变量表、操作数栈等运行信息。
5. **M03-SP5 · ADVANCED · 15**：能区分 JVM Specification 中的 Method Area 与 HotSpot Metaspace 等具体 JVM 实现。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — M03：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/m03_jvm21_runtime_data_areas.md) — M03：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## M04｜volatile / JMM

分类：`JAVA_CONCURRENCY`；难度：`MEDIUM`；知识点：`volatile_jmm`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

`volatile` 能保证什么？为什么 `volatile int count` 仍然不能保证 `count++` 在线程并发下是安全的？

### 参考答案

volatile 最重要的作用是提供线程之间的可见性和特定的内存顺序保证。一个线程对 volatile 变量的写，与之后另一个线程对同一变量的读之间存在 happens-before 关系。

但 volatile 不是互斥锁。`count++` 不是单独一次不可分割操作，它包含读取 count、加一、再写回。两个线程可能同时读取相同旧值，然后分别写回，从而丢失更新。

因此 volatile 很适合状态标记这类“一个线程写、其他线程观察”的场景；如果需要对共享计数做复合原子更新，应该考虑 `synchronized`、Lock 或 `AtomicInteger` 等机制。

### 评分点（合计 100）

1. **M04-SP1 · CORE · 30**：能说明 volatile 能提供共享变量修改的可见性，使线程能够按照 Java Memory Model 的规则观察到其他线程的 volatile 写入。
2. **M04-SP2 · PRINCIPLE · 25**：能说明对 volatile 变量的写与后续对同一变量的读建立 happens-before 关系，并对相关内存操作提供顺序约束。
3. **M04-SP3 · KEY · 30**：能说明 count++ 属于读取、计算、写回组成的复合 read-modify-write 操作，volatile 不能把整个复合操作变成原子操作，因此可能发生丢失更新。
4. **M04-SP4 · KEY · 15**：能根据场景选择 volatile、synchronized/Lock 或 AtomicInteger，而不是认为 volatile 可以替代所有线程同步机制。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — M04：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/m04_volatile_jmm_java21.md) — M04：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## M05｜Spring AOP Proxy

分类：`SPRING`；难度：`MEDIUM`；知识点：`spring_aop_proxy`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

Spring AOP 的基本实现思路是什么？为什么同一个类内部直接调用 `@Transactional` 方法时，事务可能不生效？

### 参考答案

Spring AOP 的核心是代理。调用者拿到的通常是目标对象的代理对象，方法调用先经过代理，代理才能执行事务、日志等拦截逻辑，然后再调用真正的目标方法。

Spring 可以使用 JDK 动态代理或基于 CGLIB 的代理方式。

如果目标对象内部使用 `this.xxx()` 或隐式 `this` 调用自己的另一个方法，这次调用直接发生在目标对象内部，没有重新经过外部代理，因此绑定在那个方法上的 AOP advice 没有机会执行，`@Transactional` 就可能失效。

比较好的处理方式通常是重新划分 Bean/Service 边界，让事务方法通过代理调用，而不是依赖内部 self-invocation。

### 评分点（合计 100）

1. **M05-SP1 · CORE · 25**：能说明 Spring AOP 主要通过代理对象拦截方法调用，在真正执行目标方法前后织入事务、日志等横切逻辑。
2. **M05-SP2 · KEY · 20**：能说明 Spring AOP 可以使用 JDK 动态代理或 CGLIB 代理，并理解两者都是代理机制而不是直接修改普通业务调用语义。
3. **M05-SP3 · CORE · 35**：能解释同类内部通过 this 或隐式 this 直接调用另一个方法时调用没有经过 Spring proxy，因此该方法上的事务 advice 可能无法执行。
4. **M05-SP4 · KEY · 20**：能给出合理解决方向，例如重新划分 Service 使事务方法通过代理调用，而不是简单认为增加 @Transactional 注解一定有效。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — M05：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/m05_spring_6_2_aop_proxy_self_invocation.md) — M05：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## M06｜InnoDB Index Structure

分类：`MYSQL`；难度：`MEDIUM`；知识点：`innodb_index_structure`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

InnoDB 为什么使用 B+Tree 作为主要索引结构？聚簇索引、二级索引、回表和覆盖索引之间是什么关系？

### 参考答案

InnoDB 普通索引属于 B-tree 家族实现，通常在面试中描述为 B+Tree。一个节点页可以保存多个 key 和指针，因此树的分支很多、层数较低，适合磁盘页访问；有序索引结构也适合范围查询。

InnoDB 每张表都有聚簇索引，通常由主键形成，聚簇索引的叶子记录包含完整行数据。

二级索引叶子记录保存二级索引列和对应主键值。如果查询还需要二级索引中没有的列，就要利用主键再次查询聚簇索引，这就是常说的回表。

如果所需要的列已经都可以从该索引中取得，就可以避免额外回表，这通常称为覆盖索引。

### 评分点（合计 100）

1. **M06-SP1 · CORE · 20**：能说明 InnoDB 索引采用多路、有序的 B-tree/B+Tree 类结构，相比普通二叉搜索树具有更高扇出和更低树高，适合以页为单位的存储访问。
2. **M06-SP2 · PRINCIPLE · 20**：能说明有序索引结构不仅支持等值定位，也适合范围扫描，因此比普通 Hash 索引更适合作为通用数据库索引。
3. **M06-SP3 · KEY · 25**：能说明 InnoDB 聚簇索引的叶子记录包含完整行数据，并且通常以主键作为聚簇索引。
4. **M06-SP4 · KEY · 20**：能说明 InnoDB 二级索引记录包含二级索引列和主键值，需要额外列时可以根据主键再次访问聚簇索引，这一过程通常称为回表。
5. **M06-SP5 · KEY · 15**：能说明覆盖索引表示一次查询所需数据已经可以由索引提供，从而能够避免不必要的额外回表访问。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — M06：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/m06_mysql_8_0_innodb_index_structure.md) — M06：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## M07｜Redis Data Structures

分类：`REDIS`；难度：`MEDIUM`；知识点：`redis_data_structures`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

Redis 常用数据结构有哪些？如果要分别实现对象缓存、元素去重、按顺序保存数据和排行榜，你会选择什么数据结构？为什么？

### 参考答案

String 是最基础的数据类型，可以保存字符串、数字或二进制内容，常用于缓存值、计数器等。

Hash 适合保存由多个 field-value 组成的对象；List 是有顺序的元素序列，可以用于列表或者简单队列场景；Set 适合去重、成员关系判断、集合运算；Sorted Set 在 Set 的唯一成员基础上增加 score，可以按分值排序，很适合排行榜等场景。

Redis 现在还有 Stream、Geo 等其他结构，所以不应该把这五种理解成 Redis 的全部数据类型。

选结构时应该根据业务真正需要的操作选择，而不是先统一序列化成 String。

### 评分点（合计 100）

1. **M07-SP1 · CORE · 20**：能识别 Redis 常见 String、Hash、List、Set、Sorted Set 五类通用数据结构，并知道 Redis 不只是简单的字符串 Key-Value 存储。
2. **M07-SP2 · KEY · 20**：能说明 String 适合普通缓存值、计数器等单值场景，Hash 适合保存和操作对象的多个字段。
3. **M07-SP3 · KEY · 20**：能说明 List 是有序序列，适合需要按顺序从两端操作元素的列表或简单队列类场景。
4. **M07-SP4 · KEY · 20**：能说明 Set 强调成员唯一性，适合去重、成员判断以及集合交并差等需求。
5. **M07-SP5 · KEY · 20**：能说明 Sorted Set 同时具有唯一成员和 score 排序能力，适合排行榜、按权重或时间排序等业务。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — M07：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/m07_redis_classic_data_structures.md) — M07：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## M08｜try-with-resources 与 suppressed 异常

分类：`JAVA_BASIC`；难度：`MEDIUM`；知识点：`resource_exception_suppression`；修订：1；来源：D1_NEW。

适用版本：Java SE 21

### 面试问题

在 Java 的 try-with-resources 中先声明资源 A，再声明资源 B，两者都已成功初始化。假设每个 close() 都抛出异常，所有抛出的异常彼此是不同的对象且都启用了抑制机制。当 try 块自身也抛出异常时，先关谁、调用者看到哪个异常、剩下的异常在哪？若 try 块正常结束呢？手写 finally 关闭时容易丢失什么？

### 参考答案

先关闭 B，再关闭 A。B 的关闭失败也不会阻止尝试关闭 A。try 块抛出异常时，它仍是向外传播的主异常，B、A 的关闭异常按发生顺序挂在主异常的 getSuppressed() 中，不是 cause 链。

若 try 块正常完成，B 的关闭异常成为主异常，A 的关闭异常附加到它的 suppressed 列表。手写 try/finally 时，如果 finally 又抛出异常而不做处理，finally 的异常可能替换原本正在传播的异常，导致丢失原始失败信息。

### 评分点（合计 100）

1. **M08-SP1 · CORE · 25**：说明按初始化的逆序关闭 B 再关闭 A，并说明 B.close 失败不会阻止 A.close 被尝试。
2. **M08-SP2 · KEY · 25**：说明 try 块抛异常时，该异常仍是向外传播的主异常。
3. **M08-SP3 · KEY · 20**：说明关闭异常附在主异常的 getSuppressed()，可按 B、A 顺序定位，而非把关闭异常当成 cause。
4. **M08-SP4 · PRINCIPLE · 20**：说明 try 块正常结束时，先关闭的 B 抛出的异常成为主异常，A 的关闭异常被附加。
5. **M08-SP5 · PRINCIPLE · 10**：解释普通 finally 不加处理就抛出清理异常，可能覆盖原本的业务异常。

### 技术依据

- [Java 语言规范 SE 21 · 第 14 章](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3) — §14.20.2、§14.20.3.1：普通 finally 的异常完成规则，以及多资源逆序关闭、主异常和 suppressed 的规则。
- [Java SE 21 API · Throwable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Throwable.html#getSuppressed()) — addSuppressed / getSuppressed：同层多个异常并列记录，区别于异常 cause。

覆盖差异：与 M01 的对象相等和哈希契约不同。E06 考基本关闭责任，本题给出可执行顺序和异常树，评分落在失败时异常保真。

可接受表达／替代方案：

可用简短伪代码、异常树或描述 getSuppressed 表达。若补充资源初始化失败或关闭抑制被显式关闭的边界，不影响题设下结论。

应识别的误解：

认为资源按声明顺序关闭、一次关闭异常就停止后续关闭、主异常被最后一个关闭异常覆盖或 suppressed 等同 cause。

## M09｜泛型通配符与 PECS

分类：`JAVA_BASIC`；难度：`MEDIUM`；知识点：`generic_variance_pecs`；修订：1；来源：D1_NEW。

适用版本：Java SE 21

### 面试问题

要写一个方法把源列表里的 Number 值追加到目标列表。源可能是 List<Integer> 或 List<Double>，目标可能是可修改的 List<Number> 或 List<Object>。不用原生类型或强制转换，参数类型应怎么写？为什么不能把 List<Integer> 当成 List<Number>，两种通配符分别允许怎样读写？

### 参考答案

我会写成 appendNumbers(List<? extends Number> source, List<? super Number> target)。List<Integer> 不能赋给 List<Number>，因为 Java 泛型参数在这里不具协变关系。若允许，其他代码就可能经 List<Number> 引用写入 Double，破坏原本的整数列表。

源列表只保证未知元素类型是 Number 或其子类，所以能把元素读成 Number，不能向其中加入任意非 null 的 Number。目标列表的未知元素类型是 Number 或它的某个父类型，所以可以加入 Number 及其子类。读取目标元素时一般只保证 Object。这就是把生产值的参数写成 extends、消费值的参数写成 super。

### 评分点（合计 100）

1. **M09-SP1 · CORE · 25**：说明 List<Integer> 并非 List<Number> 的子类型，并用写入 Double 等例子说明不变性的类型安全价值。
2. **M09-SP2 · KEY · 20**：选择 List<? extends Number> 作为源，说明从中读取时可以用 Number 接收。
3. **M09-SP3 · KEY · 15**：说明源的实际元素类型未知，不能向其中任意加入非 null 的 Number 或任意指定子类型元素。
4. **M09-SP4 · KEY · 25**：选择 List<? super Number> 作为目标，说明可加入 Number 及其子类，适用于题设的两类目标。
5. **M09-SP5 · PRINCIPLE · 15**：说明从 List<? super Number> 读取一般只能按 Object 接收，目标也可能预先含有非 Number 对象。

### 技术依据

- [Java 语言规范 SE 21 · 第 4 章](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.5.1) — §4.5.1、§4.5.2、§4.10：通配符界限、成员类型和泛型参数的不变性。
- [Java SE 21 API · Collections](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collections.html#copy(java.util.List,java.util.List)) — copy(List<? super T>, List<? extends T>)：源/目标参数的 PECS 示例。官方方法按索引覆盖，题目的追加由候选实现。

覆盖差异：与 M01 的哈希契约无重合。与 H01 同属泛型，但本题考编译期参数设计及读写方向，H01 考擦除和运行时边界。

可接受表达／替代方案：

接受保留类型约束的泛型签名，例如 <T extends Number> void append(List<? extends T> src, List<? super T> dst)。不要求背 PECS 缩写。

应识别的误解：

认为 List<Integer> 可当 List<Number>。向 extends 目标任意追加 Number。把 super 列表读出的每个元素都当作 Number。

## M10｜迭代中修改集合与 fail-fast

分类：`JAVA_COLLECTION`；难度：`MEDIUM`；知识点：`iterator_modification`；修订：1；来源：D1_NEW。

适用版本：Java SE 21

### 面试问题

有一个可修改的 ArrayList，代码用增强 for 遍历，命中条件就直接 list.remove。为什么这种写法可能抛出 ConcurrentModificationException？如果只调用 set 替换已有位置的元素，是否属于同类修改？单线程应如何安全地删除当前元素？能否靠这个异常判断或处理多线程并发修改？

### 参考答案

增强 for 遍历 ArrayList 使用迭代器。直接调用 list.remove 会绕过迭代器修改列表结构，后续执行迭代器检查时可能抛出 ConcurrentModificationException。ArrayList 的结构修改包括增删元素；普通 set 替换现有元素不属于结构修改。

在同一线程里，我会显式取得 Iterator，每次 next 后先判断，再用这个迭代器的 remove 删除刚返回的元素。一次 next 最多允许 remove 一次。如果只需要按谓词过滤，ArrayList 也支持 removeIf，不必自己持有一个迭代器。

fail-fast 是尽力检测，即使只有一个线程也可能触发，并不保证所有修改都会被发现。共享集合要使用正确的同步策略，或按业务语义选择并发容器。

### 评分点（合计 100）

1. **M10-SP1 · CORE · 25**：解释增强 for 依赖迭代器，直接通过列表删除绕过该迭代器，使迭代器可能发现结构改变并抛出 ConcurrentModificationException。
2. **M10-SP2 · KEY · 25**：提供安全的单线程删除方案，例如 next 之后调用同一个迭代器的 remove，或独立用 removeIf 过滤。
3. **M10-SP3 · KEY · 15**：能区分元素增删与替换现有元素，说明 ArrayList 的 set 不属于结构性修改。
4. **M10-SP4 · PRINCIPLE · 20**：说明 fail-fast 是尽力检测，单线程也可能触发，既不保证必抛也不能作为正确性前提。
5. **M10-SP5 · PRINCIPLE · 15**：说明共享列表应采用同步或具备符合业务语义的并发集合，捕获 ConcurrentModificationException 不能代替并发控制。

### 技术依据

- [Java 语言规范 SE 21 · 第 14 章](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.14.2) — §14.14.2：增强 for 对 Iterable 使用迭代器。
- [Java SE 21 API · ArrayList](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html) — 类概述、iterator、removeIf：ArrayList 的结构修改、fail-fast 及按谓词删除。
- [Java SE 21 API · Iterator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Iterator.html#remove()) — remove()：删除最近一次 next 返回的元素，每次 next 最多一次。

覆盖差异：与 M02 的 HashMap 插入与扩容不同。本题考 ArrayList 的迭代修改契约。H13 考选型时的快照或弱一致迭代语义。

可接受表达／替代方案：

接受 removeIf、在迭代结束后另行修改、保持语义的索引倒序遍历，前提是能说明为何不再从同一个迭代器背后修改列表。

应识别的误解：

认为异常只会在多线程出现、对所有修改都必抛，或 catch 后重试就获得线程安全。

## M11｜集合视图、不可修改与防御性复制

分类：`JAVA_COLLECTION`；难度：`MEDIUM`；知识点：`collection_copy_views`；修订：1；来源：D1_NEW。

适用版本：Java SE 21

### 面试问题

接口要返回一个列表，要求调用者不能增删，后续修改内部列表也不能改变已返回列表的元素个数和顺序。Collections.unmodifiableList 和 List.copyOf 哪个合适？如果元素本身是可变 DTO，这个方案还有什么限制？如果源列表可能含有 null 元素，还要注意什么？

### 参考答案

Collections.unmodifiableList(backing) 是底层列表的不可修改视图。调用方不能从视图中添加或删除，内部直接修改 backing 后，视图看到的内容也会变化。List.copyOf(backing) 返回不可修改的列表，底层集合后续的增删和换位不会影响它，符合这里的列表结构要求。

这是对元素引用的浅复制。如果某个 DTO 被改了字段，拿到同一个 DTO 的人仍会看到变化。若要求连元素状态都固定，可以返回不可变 DTO，或者在构建快照时复制元素状态。还要注意 List.copyOf 不接受 null 元素，如果业务允许 null，应选保留 null 的防御性复制加只读包装。

### 评分点（合计 100）

1. **M11-SP1 · CORE · 25**：说明 unmodifiableList 返回不可修改视图，原列表后续改动仍会反映在视图中。
2. **M11-SP2 · KEY · 30**：说明 List.copyOf 返回不可修改的列表结构快照，原列表后续增删或重新排序不反映到已返回列表。
3. **M11-SP3 · KEY · 20**：区分浅复制与深复制，明确可变 DTO 字段仍可通过共享对象引用发生变化。
4. **M11-SP4 · PRINCIPLE · 15**：为需要固定元素状态的场景提出不可变元素、复制 DTO 或等价防御措施。
5. **M11-SP5 · PRINCIPLE · 10**：说明 List.copyOf 拒绝 null 元素，或明确采用能保留 null 的独立复制加只读包装。

### 技术依据

- [Java SE 21 API · Collections](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)) — unmodifiableList：返回会读取底层列表最新状态的不可修改视图。
- [Java SE 21 API · List](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)) — Unmodifiable Lists / copyOf：返回结构不可修改且不随输入变化的列表，拒绝 null，元素自身可变性仍有影响。

覆盖差异：与 M02 的 HashMap 写入和扩容不同，与 M10 的遍历删除也不同。本题考 API 输出是否隔离内部结构和元素状态。

可接受表达／替代方案：

接受 Collections.unmodifiableList(new ArrayList<>(backing)) 等独立浅复制方案。对可变元素可使用不可变投影或复制构造。无需声称 List.copyOf 每次都分配新对象。

应识别的误解：

把不可修改视图当成独立快照。把 List.copyOf 当成深复制或认为它必定返回不同实例。

## M12｜类加载、链接和初始化的区别

分类：`JVM`；难度：`MEDIUM`；知识点：`class_loading_initialization`；修订：1；来源：D1_NEW。

适用版本：Java SE 21，普通类和同一类加载环境。

### 面试问题

Java 21 中，有个尚未初始化的类 Config，声明了 static final int PORT = 8080 和 static int retries = 3，静态代码块会打印一行日志。依次读取 Config.PORT、读取 Config.retries、new Config()，哪一步首次触发初始化？加载、链接、初始化分别做了什么？

### 参考答案

首次触发的是读取 Config.retries。PORT 是用常量表达式初始化的编译期常量，读它不触发 Config 初始化。retries 是 Config 声明的非常量静态字段，首次读它前会执行初始化。new Config() 也属于可以触发初始化的主动使用，这次类已初始化，所以静态代码块不会重跑。
加载根据类的二进制表示建立运行时的类。链接把类接入 JVM，包括验证二进制是否合规、为静态字段准备空间和默认值，以及解析符号引用。解析可以延迟，并不要求类初始化前已完成所有引用的解析。初始化阶段执行静态字段初始化和静态代码块，普通静态字段的初始化表达式和静态块按源码顺序执行。

### 评分点（合计 100）

1. **M12-SP1 · CORE · 25**：判断首次读取 Config.retries 会触发 Config 初始化，并解释该字段是本类声明的非常量静态字段。
2. **M12-SP2 · KEY · 20**：判断读取 Config.PORT 不触发初始化，依据是 int 常量变量由编译期常量表达式初始化。
3. **M12-SP3 · KEY · 15**：指出创建实例可以触发类初始化，但同一次成功初始化后再 new 不会重跑静态代码块。
4. **M12-SP4 · PRINCIPLE · 20**：区分加载与初始化：加载建立运行时类表示，初始化执行静态字段初始化和静态代码块。
5. **M12-SP5 · PRINCIPLE · 20**：概括验证、准备、解析的职责，知道符号引用的解析可以延后。

### 技术依据

- [Java SE 21 语言规范](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.2) — §12.2、§12.3：建立类表示、验证、准备、解析以及解析允许延迟。
- [Java SE 21 语言规范](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.4.1) — §12.4.1 何时初始化：创建实例、读取本类声明的非常量静态字段触发初始化，读取常量变量除外。
- [Java SE 21 语言规范](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.12.4) — §4.12.4 常量变量：本题 PORT 满足 final、基本类型和常量表达式三个条件。

覆盖差异：旧 M03 考运行时区域及存放内容。M12 要预测何时执行静态初始化。相邻 H14 考类加载器定义的类型身份，答题不需要解释委派。

可接受表达／替代方案：

- 可以用类首次主动使用或 getstatic 的概念解释给定结果。
- 可说明已初始化的父类等细节，但不要求背诵完整初始化顺序。

应识别的误解：

- 读任意 static final 字段都不触发类初始化。
- 加载或链接完成就说明静态代码块已经运行。
- 每次 new 都执行类的静态初始化。

## M13｜对象可达性、GC Roots 与内存泄漏

分类：`JVM`；难度：`MEDIUM`；知识点：`gc_reachability_leaks`；修订：1；来源：D1_NEW。

适用版本：Java SE 21 的堆对象可达性与 HotSpot 诊断场景。

### 面试问题

一个 Java 服务把每次请求的详情放进 static Map，业务处理完却从不清理。流量稳定，堆在多次垃圾收集后的基线仍不断上升。请求方法都已经返回，为什么详情可能仍留在堆里？你会怎样确认是引用保留引起的泄漏，并验证修复？

### 参考答案

方法返回只结束了该次调用。从仍存活的类可沿着静态字段找到 Map，再沿着 Map 的普通强引用找到详情，所以请求结束后这些对象仍可达。GC 根据可达性工作，它并不知道业务已经用完这些数据。稳定流量下，回收后仍存活的堆持续增长是线索。我会对比相近负载下的类实例数、堆转储和到 GC Roots 的保留路径，确认是不是这张 Map 保留了大量详情。OOM 只说明某次内存需求无法满足，容量不足、瞬时压力也可能造成 OOM。这里要根据保留需求选择完成后移除、容量上限或到期清理，再用相同负载观察回收后基线和保留数量是否趋稳。

### 评分点（合计 100）

1. **M13-SP1 · CORE · 30**：指出仍存活的类经 static 字段、Map 条目到详情的保留链，并能从该路径识别需要检查的持有方。
2. **M13-SP2 · PRINCIPLE · 20**：解释 GC 根据可达性判断，方法返回不会解除独立存在的 Map 强引用，业务上已用完的强可达对象仍保留。
3. **M13-SP3 · KEY · 20**：给出堆转储保留链等证据来确认引用来源，并以稳定负载下回收后存活量持续增长作为线索。
4. **M13-SP4 · KEY · 15**：区分泄漏与 OOM，说明仅凭 OOM 或单次内存升高无法确认泄漏，并举出容量不足或瞬时峰值一类其他可能。
5. **M13-SP5 · KEY · 15**：提出符合场景的生命周期或容量约束，并在同类负载下比较修复前后的保留数量或回收后基线。

### 技术依据

- [Java SE 21 引用 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ref/package-summary.html#reachability) — Reachability：区分强可达和其他可达性，说明强引用保留的基础。
- [Oracle Java 21 故障排查指南](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshooting-memory-leaks.html) — The java.lang.OutOfMemoryError Error / Detecting a Memory Leak：用稳定负载下回收后存活集判断泄漏线索，并区分容量原因。
- [Oracle Java 21 故障排查指南](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshooting-memory-leaks.html) — Diagnosing Java Memory Leaks：堆转储、对象统计和对象保留用于定位 Java 堆泄漏。

覆盖差异：旧 M03 考 JVM 内存区，旧 H03 考 G1 回收设计。M13 只考堆上强引用保留和验证，H15 才需要区分高 CPU、频繁 GC、不同 OOM 来源。

可接受表达／替代方案：

- 可通过堆转储路径、JFR Old Object Sample 或等效可定位根路径的工具给证据。
- 按题意选择即时清理或有界保留均可。

应识别的误解：

- 方法返回后其创建的对象立即回收。
- 只要触发一次 OOM 就能证明内存泄漏。
- 调用 System.gc() 或增大堆能解除仍存在的强引用。

## M14｜synchronized 与 ReentrantLock 的选择

分类：`JAVA_CONCURRENCY`；难度：`MEDIUM`；知识点：`monitor_reentrant_lock`；修订：1；来源：D1_NEW。

适用版本：Java 21，比较内置监视器与标准库 ReentrantLock。

### 面试问题

一个订单服务需要在内存中用同一把锁保护计数和状态。普通入口可以等待，另一个入口最多等 100 毫秒拿锁，并且等待期间要响应线程中断。你会怎样选择 synchronized 或 ReentrantLock？如何保证异常、超时和中断时锁都处理正确？

### 参考答案

两个入口都要用同一把锁保护同一份状态。只有简单互斥的场景，synchronized 写法短，离开同步块时会自动释放，即使抛异常也一样。这里有带超时、可中断的获取需求，两个入口可统一使用同一把 ReentrantLock。普通入口先 lock，再在 try 中操作，在 finally 解锁。限时入口调用 tryLock(100, MILLISECONDS)，返回 false 就走超时分支，抛 InterruptedException 就按取消约定传播或处理，只有成功获取才进入临界区和 finally。两种锁都可重入，同一把锁的释放和后续成功获取建立可见性。只要求可中断等待、不设时限时，还可以用 lockInterruptibly。

### 评分点（合计 100）

1. **M14-SP1 · CORE · 25**：为同一份共享状态选择同一 ReentrantLock，并用带超时的 tryLock 取得有上限且可中断的等待。
2. **M14-SP2 · KEY · 20**：区分 tryLock 成功、返回 false 和 InterruptedException 三条路径，只在成功获取后进入临界区并解锁。
3. **M14-SP3 · KEY · 20**：说明普通 lock 在取得锁后进入 try，且 finally 中释放，从而覆盖业务异常路径。
4. **M14-SP4 · PRINCIPLE · 20**：说明两种锁都可重入，且同一把锁释放与后续成功获取提供互斥保护及相应的内存可见性。
5. **M14-SP5 · KEY · 15**：说明 synchronized 退出同步块会自动释放锁，适合只需简单互斥、没有限时获取需求的代码。

### 技术依据

- [Java 21 ReentrantLock](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html) — 类说明、tryLock(long, TimeUnit)、lockInterruptibly：可重入、带超时获取会响应中断，成功获取后用 try/finally。
- [Java 21 Lock](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/locks/Lock.html) — Memory Synchronization、接口说明：成功加解锁的内存同步语义与内置监视器一致，显式锁需要结构化释放。
- [Java SE 21 语言规范](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.19) — §14.19 synchronized：同步块正常或异常退出时释放监视器。

覆盖差异：旧 M04 考 volatile 对可见性及复合操作的限制，旧 H04 考线程池容量与过载。M14 考选锁、共用锁和获取失败路径。

可接受表达／替代方案：

- 给出等价伪代码，或用显式 acquired 标志且仅在为真时 unlock。
- 对中断传播异常，或在不能传播时恢复中断标志并结束工作。

应识别的误解：

- 不同入口各用一把锁也能保护同一个不变量。
- tryLock 超时或抛中断时照样 unlock。
- synchronized 进入监视器的等待本身提供可中断、限时获取接口。

## M15｜线程中断与 wait/sleep 的边界

分类：`JAVA_CONCURRENCY`；难度：`MEDIUM`；知识点：`thread_interruption_wait`；修订：1；来源：D1_NEW。

适用版本：Java 21，普通 Java 线程和对象监视器。

### 面试问题

消费者在 synchronized (monitor) 中等待队列变为非空，生产者也需要拿同一个 monitor 才能入队。消费者用 Thread.sleep() 等，为什么可能妨碍生产者？改用 Object.wait() 时应怎样写等待条件，以及怎样处理退出信号 interrupt()？

### 参考答案

sleep 只让线程暂停，持有的监视器不会释放。如果消费者睡着时仍占着 monitor，生产者这段时间就拿不到锁。消费者可以在同步块里用 while 检查队列是否为空，然后 monitor.wait()。wait 要先持有该对象的监视器，等待时只释放这一把监视器，恢复执行前会重新取得它。生产者在同一把锁内入队，再通知等待者。醒来后还要重新检查条件，因为可能虚假唤醒，也可能在重新拿到锁前有其他消费者拿走元素。interrupt 是协作退出信号。wait 或 sleep 抛 InterruptedException 时会清除中断标志。处理方应按约定清理后退出或继续向上抛出，如果无法抛出且要保留信号，就重新设置中断标志。

### 评分点（合计 100）

1. **M15-SP1 · CORE · 25**：说明 sleep 不释放 monitor，所以持锁休眠期间生产者不能进入需要同一监视器的同步块。
2. **M15-SP2 · PRINCIPLE · 20**：说明 wait 必须已持有目标监视器，等待时释放目标监视器而保留其他监视器，返回前重新取得目标监视器。
3. **M15-SP3 · KEY · 20**：用同一监视器保护队列状态，在 while 中重新检查谓词，能解释虚假唤醒或被其他消费者抢先取走。
4. **M15-SP4 · KEY · 15**：让生产者在同一监视器内先改变状态再通知等待者，避免将通知当作条件已成立的证明。
5. **M15-SP5 · PRINCIPLE · 20**：把 interrupt 解释为协作信号，知道 wait 或 sleep 抛 InterruptedException 会清标志，并能提出传播、结束或恢复标志的正确处理。

### 技术依据

- [Java 21 Object](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html#wait(long,int)) — wait、notifyAll：拥有监视器、等待时释放目标锁、返回前重取、用循环应对虚假唤醒及中断。
- [Java 21 Thread](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#sleep(long)) — sleep、interrupt、interrupted：sleep 保留监视器，阻塞操作中断与状态清除。

覆盖差异：旧 M04 问 volatile 与共享变量可见性。M14 问互斥锁获取。M15 用生产者消费者考监视器等待、唤醒条件和中断协作。

可接受表达／替代方案：

- 用 while (queue.isEmpty()) 与 notifyAll() 给伪代码即可。
- 可以补充 BlockingQueue 是业务代码中的替代，但仍要解释题目指定的 monitor 行为。

应识别的误解：

- sleep 会释放 synchronized 的锁。
- wait 会释放当前线程持有的全部锁。
- 被唤醒后用 if 不重查条件，或者捕获中断后忽略并继续等待。

## M16｜多个实现如何进行依赖注入

分类：`SPRING`；难度：`MEDIUM`；知识点：`dependency_injection_resolution`；修订：1；来源：D1_NEW。

适用版本：Spring Framework 6.2，基于注解的构造器注入和单值依赖。

### 面试问题

Spring 容器同时注册了邮件和短信两个 NotificationSender。普通通知应优先使用邮件，短信验证码必须使用短信。两个消费者都通过构造器依赖这个接口。怎样配置默认实现和指定实现？只写接口类型为什么可能启动失败？如果某处需要注入所有实现，又会怎样？

### 参考答案

容器先找类型匹配的 Bean。有两个候选、且没有进一步消歧的信息时，单值依赖无法决定注入谁，创建依赖 Bean 会报候选不唯一。可以给邮件 Bean 加 @Primary，让普通的单值注入优先选它。给短信 Bean 声明短信限定符，并在验证码消费者的构造器参数上使用相同的 @Qualifier，在类型候选中缩小到短信。限定符可以用语义标签或自定义注解。若某处确实需要全部实现，可注入不带限定符的 List<NotificationSender>，@Primary 不会把其他候选从列表剔除。Spring 6.2 中单构造器的消费者可以省略 @Autowired。

### 评分点（合计 100）

1. **M16-SP1 · CORE · 25**：说明先按接口类型得到候选，若两个候选缺少足以消歧的规则，单值构造器依赖会因候选不唯一而创建失败。
2. **M16-SP2 · KEY · 25**：为邮件 Bean 配置 @Primary，说明它针对有多个候选时的默认单值选择。
3. **M16-SP3 · KEY · 25**：在短信消费者的构造器参数使用与短信 Bean 匹配的 @Qualifier，将类型候选缩到短信。
4. **M16-SP4 · PRINCIPLE · 15**：说明不带限定符的 List<NotificationSender> 可以接收所有符合条件的实现，@Primary 只影响单值优先选择。
5. **M16-SP5 · KEY · 10**：采用构造器参数表达必需依赖，知道 Spring Bean 只定义一个构造器时可以省略 @Autowired。

### 技术依据

- [Spring Framework 6.2 Reference](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html) — Using @Autowired：单构造器可省略 @Autowired，也可注入与元素类型匹配的所有 Bean。
- [Spring Framework 6.2 Reference](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-primary.html) — Fine-tuning Annotation-based Autowiring with @Primary or @Fallback：唯一 primary 候选优先用于单值依赖，集合等多值注入仍包含所有符合条件的 Bean。
- [Spring Framework 6.2 Reference](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired-qualifiers.html) — Fine-tuning Annotation-based Autowiring with Qualifiers：限定符缩小类型候选范围，也支持语义标签。
- [Spring Framework 6.2 Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/beans/factory/NoUniqueBeanDefinitionException.html) — NoUniqueBeanDefinitionException：预期单个 Bean 而匹配多个时的异常。

覆盖差异：旧 E03 考 IoC 和依赖注入的基本职责，旧 M05 考代理。M16 要配置两个真实的注入点并解释候选不唯一。

可接受表达／替代方案：

- 可以用自定义限定符，或明确按限定值、Bean 名称回退规则举例。
- 两个注入点各显式标记限定符也能完成业务选择，回答仍应说明 @Primary 怎样提供题设默认。

应识别的误解：

- @Primary 会把其他同类型 Bean 从容器删除。
- @Qualifier 可以匹配类型不兼容的 Bean。
- 凡是存在两个实现，Spring 就必然启动失败。

## M17｜接口参数校验与统一异常响应

分类：`SPRING`；难度：`MEDIUM`；知识点：`mvc_validation_errors`；修订：1；来源：D1_NEW。

适用版本：Spring Framework 6.2 MVC，启用 Jakarta Bean Validation，假定未使用类级 @Validated 的 AOP 方法校验。

### 面试问题

一个 Spring MVC 6.2 创建用户接口启用了 Bean Validation，用 @Valid @RequestBody CreateUserRequest 接收 JSON，邮箱字段标有 @NotBlank，未使用 BindingResult 或类级 @Validated。客户端分别发来语法错误的 JSON 和能解析但邮箱为空的 JSON。两个错误在哪一步产生？如果又给同一个方法加了查询参数 @RequestParam @Min(1) int page，统一错误响应要注意什么？

### 参考答案

Spring MVC 先通过消息转换器读请求体。JSON 语法不合法时通常会出现 HttpMessageNotReadableException，此时无法正常得到 DTO。能解析成 DTO 时，@Valid 才会触发字段校验，例如用 @NotBlank 表达邮箱必填。没有紧随的 BindingResult，且方法未触发内建方法校验时，字段不符合约束会抛 MethodArgumentNotValidException。加了直接标在参数上的 @Min 之后，MVC 内建的方法校验会覆盖普通参数级校验，也检查 @Valid 标记的嵌套 DTO。输入约束失败时可得到 HandlerMethodValidationException。可在 @RestControllerAdvice 里统一这些客户端输入错误为 HTTP 400，返回稳定错误码和安全的字段提示。内部异常细节和堆栈只用于受控诊断。

### 评分点（合计 100）

1. **M17-SP1 · CORE · 25**：区分反序列化与 DTO 字段校验：损坏 JSON 在消息转换阶段失败，能够解析的对象才进入 Bean Validation。
2. **M17-SP2 · KEY · 20**：用 @Valid 触发 DTO 校验，用 @NotBlank 等约束表达邮箱必填，指出普通参数级 DTO 校验失败对应 MethodArgumentNotValidException（题设无紧随的 BindingResult）。
3. **M17-SP3 · KEY · 20**：说明参数上的直接约束可触发 MVC 方法校验，并覆盖同一方法的普通参数级校验，包括嵌套 DTO 的约束。输入校验异常可为 HandlerMethodValidationException。
4. **M17-SP4 · KEY · 20**：说明用 @RestControllerAdvice 等入口统一 HTTP 400 和稳定的结构化错误码或字段提示，覆盖不可读取的请求体。
5. **M17-SP5 · PRINCIPLE · 15**：说明客户端提示可定位错误，但避免返回原始请求中的敏感值、完整异常或堆栈。

### 技术依据

- [Spring Framework 6.2 Reference](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html) — Validation：@Valid 的 DTO 校验和直接约束触发的方法校验，以及两个不同校验异常。
- [Spring Framework 6.2 Reference](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-rest-exceptions.html) — Error Responses：统一问题详情和拦截 Spring MVC 异常，可定制详情以控制对外内容。
- [Spring Framework 6.2 Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseEntityExceptionHandler.html) — handleHttpMessageNotReadable、handleMethodArgumentNotValid、handleHandlerMethodValidationException：统一处理不可读取的请求体、参数对象校验和方法校验错误。

覆盖差异：旧 E03 是容器入门，旧 M05 考代理。M17 考请求转换和校验时序，以及不同来源的输入错误如何对外表达。

可接受表达／替代方案：

- 可以用 ProblemDetail 或稳定的自定义错误对象。
- 接受合适的 ResponseEntityExceptionHandler 继承或具体的 @ExceptionHandler 映射。
- 可补充 BindingResult 与类级 @Validated 会改变路径，但按题设回答即可。

应识别的误解：

- @Valid 自己就是字段必填约束。
- 损坏 JSON 与字段不合法一定是同一种异常。
- 把解析器抛出的完整错误和请求内容原样返回。

## M18｜联合索引与查询条件的匹配

分类：`MYSQL`；难度：`MEDIUM`；知识点：`composite_index_access`；修订：1；来源：D1_NEW。

适用版本：MySQL 8.0 InnoDB，普通 B-tree 二级索引，题设 SQL 为升序且无额外执行提示。

### 面试问题

订单表已有联合索引 (user_id, status, created_at)。查询 A 是 WHERE user_id = ? AND status = ? AND created_at >= ? ORDER BY created_at，查询 B 去掉 status 条件，其余相同。两个查询的索引定位和排序有什么区别？如果 A 只返回 status、created_at，和额外返回未入索引的 remark 有何区别？你会怎样确认优化器实际用了哪些能力？

### 参考答案

索引按 user_id、status、created_at 的顺序组织。A 的前两列等值后，可在该用户和状态下从起始时间做范围扫描，同一段里的时间已有序，有机会直接用索引顺序返回。B 可以用 user_id 前缀，但中间缺少 status，单个普通连续区间不能同时按所有状态的 created_at 下界精确定位。这个用户的不同 status 各有自己的时间顺序，整体不天然按时间排序，优化器可能另行排序。A 只投影 status 和 created_at 时，过滤、排序与返回所需列都在这个索引里，具备覆盖条件。加上 remark 就通常需要取表行。看 EXPLAIN 中的 key、访问方式和 Extra，确认实际选择的索引、是否额外排序以及是否覆盖。即使 created_at 不能缩小 B 的普通连续范围，它的条件仍然可以参与剩余过滤。

### 评分点（合计 100）

1. **M18-SP1 · CORE · 25**：按联合索引列顺序分析 A：user_id 与 status 等值固定前缀，created_at 条件可以构成接下来的时间范围。
2. **M18-SP2 · PRINCIPLE · 25**：分析 B：user_id 前缀仍可用，但缺少中间 status 使普通连续范围不能直接用 created_at 完成跨状态定位。
3. **M18-SP3 · KEY · 20**：判断 A 可利用固定前缀后的 created_at 顺序，B 在多个 status 上扫描时整体并无 created_at 顺序保证。
4. **M18-SP4 · KEY · 20**：判断只取索引中所需列可形成覆盖，额外取不在索引的 remark 通常需取表行。
5. **M18-SP5 · PRINCIPLE · 10**：说明不能用于定位的列仍可参与剩余过滤，并用 EXPLAIN 的实际索引及 Using filesort、Using index 等信息核对结论。

### 技术依据

- [MySQL 8.0 参考手册](https://dev.mysql.com/doc/refman/8.0/en/multiple-column-indexes.html) — Multiple-Column Indexes：复合索引按列序组织，可按最左前缀查找。
- [MySQL 8.0 参考手册](https://dev.mysql.com/doc/refman/8.0/en/range-optimization.html) — Range Access and Multiple-Part Indexes：等值可以继续使用后续索引列来构造范围，范围比较及缺失前缀会改变可用于构造区间的列。
- [MySQL 8.0 参考手册](https://dev.mysql.com/doc/refman/8.0/en/order-by-optimization.html) — Use of Indexes to Satisfy ORDER BY：固定前缀后可按索引列排序，使用索引排序仍取决于计划成本。
- [MySQL 8.0 参考手册](https://dev.mysql.com/doc/refman/8.0/en/index-condition-pushdown-optimization.html) — Index Condition Pushdown Optimization：在适用条件下，存储引擎可在取完整行前使用索引列做剩余过滤。
- [MySQL 8.0 参考手册](https://dev.mysql.com/doc/refman/8.0/en/explain-output.html) — EXPLAIN Extra Information：Using index 表示查询所需列可由索引提供，Using index condition 表示适用索引条件下推。

覆盖差异：旧 M06 考 InnoDB 索引结构和回表概念。M18 要把索引列序应用到两个具体查询的定位和排序，覆盖只作为投影差异。M19 则从观察到的计划反推问题。

可接受表达／替代方案：

- 可以用排序后的三元组或按用户、状态分组示意。
- 可提出其他索引或全表扫描也可能成为计划，但应保留对本索引普通连续范围和排序能力的判断。

应识别的误解：

- WHERE 条件书写顺序必须与索引列顺序一致。
- 中间一列缺失时整个索引毫无用处。
- 只要命中索引，就不会发生额外排序或回表。

## M19｜如何用 EXPLAIN 分析慢查询

分类：`MYSQL`；难度：`MEDIUM`；知识点：`explain_query_plan`；修订：1；来源：D1_NEW。

适用版本：MySQL 8.0，传统表格 EXPLAIN 与 8.0.18 起可用的 EXPLAIN ANALYZE。

### 面试问题

一个 MySQL 8.0 订单查询变慢了。传统 EXPLAIN 显示 possible_keys=idx_user、key=NULL、type=ALL、rows=200000、filtered=2.00、Extra=Using where。你如何读这份计划？接下来怎样判断该调整查询、更新统计信息还是改索引，并验证调整有用？

### 参考答案

possible_keys 表示优化器看到了一个可能用于找行的索引，key 为空、type 为 ALL，说明该表在这份计划里是全表扫描。rows 是预计检查约二十万行。filtered 约为百分之二，估计这张表过完条件后留下约四千行，Using where 表示有条件用于过滤。它们是估计和访问方式，不是实际耗时，也不能仅凭 ALL 决定强制用索引。先用实际查询参数和代表性数据看条件选择性、数据倾斜、统计信息以及查询是否能用索引过滤。然后在合适的验证环境对这个 SELECT 用 EXPLAIN ANALYZE，查看各迭代器实际的行数、耗时和循环次数，找估计偏差或真正的耗时步骤。若统计失准就检验更新统计是否改善估计，若索引或查询写法不匹配就验证有针对性的改动。用相同参数和可比负载对照计划、耗时和扫描量。

### 评分点（合计 100）

1. **M19-SP1 · CORE · 25**：结合 possible_keys、key 和 type 判断：idx_user 是候选，但本计划未选择索引，该表访问为全表扫描。
2. **M19-SP2 · KEY · 20**：解释 rows 是估计检查数，filtered 是估计通过比例，正确推算约四千行通过过滤，并能说明 Using where 表示应用过滤条件。
3. **M19-SP3 · PRINCIPLE · 20**：说明传统 EXPLAIN 输出是计划和估计。EXPLAIN ANALYZE 会真正执行给定 SELECT，提供迭代器实际时间、行数和循环等证据。
4. **M19-SP4 · KEY · 20**：根据真实参数检验选择性、统计偏差或索引和谓词不匹配，提出与所观察原因对应的一项改动。
5. **M19-SP5 · KEY · 15**：用可比参数和数据对照修改前后，至少验证实际耗时或扫描量，并避免仅凭是否出现索引判断性能。

### 技术依据

- [MySQL 8.0 参考手册](https://dev.mysql.com/doc/refman/8.0/en/explain-output.html) — EXPLAIN Output Columns / Join Types / Extra：定义 possible_keys、key、type、rows、filtered、Using where 和 ALL。
- [MySQL 8.0 参考手册](https://dev.mysql.com/doc/refman/8.0/en/explain.html) — EXPLAIN ANALYZE / Obtaining Execution Plan Information：EXPLAIN ANALYZE 真正执行查询并展示实际数据，手册也说明更新表统计对计划选择的用途。

覆盖差异：旧 M06 考索引结构，M18 考根据 SQL 推导索引能力。M19 直接解读一组计划输出，考估计与实测的区分及证据驱动改动。

可接受表达／替代方案：

- 可用优化器 trace 或慢查询记录补充判断，在说明传统 EXPLAIN 与 ANALYZE 区别后再展开。
- 允许认为全表扫在题设的某些数据分布或成本下合理，需给出验证方法。

应识别的误解：

- possible_keys 中出现的索引已经被选用。
- rows 表示实际结果行数或过滤后的行数。
- EXPLAIN ANALYZE 只显示估计，不会执行查询。
- 全表扫描必慢，强制索引必快。

## M20｜过期和淘汰有什么区别

分类：`REDIS`；难度：`MEDIUM`；知识点：`redis_expiry_eviction`；修订：1；来源：D1_NEW。

适用版本：Redis OSS 7.2，单实例，key 级 TTL 与 maxmemory 淘汰。

### 面试问题

商品缓存设置了 30 分钟 TTL，流量高峰时有些键不到 30 分钟就查不到。Redis 的过期和内存淘汰分别怎么触发？allkeys-lru、volatile-lru 和 noeviction 对这个现象有什么影响，应用应怎样处理和排查未命中？

### 参考答案

TTL 给键设定一个到期时间。访问时发现已到期，Redis 会把它当作不存在并处理过期，也会定期主动清理部分过期键，不保证所有键在到期瞬间都已从内存删除。淘汰是受 maxmemory 限制时，Redis 按配置尝试回收空间，键可以在到期前被移除。allkeys-lru 在全部键中近似按最近使用情况淘汰，volatile-lru 只从有过期时间的键里选。如果没有可淘汰的候选，或设置了 noeviction，需要更多内存的命令可能报错，读取通常还能继续。应用把未命中作为正常分支，向权威数据源取值并在条件允许时重建缓存。排查时结合 TTL 设置、实际内存与策略，以及一段时间内的 expired_keys、evicted_keys 计数变化。实例计数能提示原因，不能单凭它确定某个键为何消失。

### 评分点（合计 100）

1. **M20-SP1 · CORE · 25**：区分按键到期时间触发的过期与按 maxmemory 和策略触发的淘汰，说明有 TTL 的键也可能提前被淘汰。
2. **M20-SP2 · PRINCIPLE · 20**：说明过期可在访问时被动处理，也有周期性的主动清理，物理删除并非必须精确发生在到期时刻。
3. **M20-SP3 · KEY · 25**：区分 allkeys-lru 的全体键候选和 volatile-lru 的有过期时间候选，理解 LRU 策略是近似选择。
4. **M20-SP4 · KEY · 15**：说明 noeviction 或候选不足时，需要增加内存的操作可能报错，读请求通常还能服务。
5. **M20-SP5 · KEY · 15**：提出从权威数据源回源并按需回填，同时能用内存配置与过期和淘汰统计区分实例上的可能原因。

### 技术依据

- [Redis EXPIRE 命令文档](https://redis.io/docs/latest/commands/expire/) — How Redis expires keys：访问时被动过期与后台主动检查。
- [Redis Key eviction](https://redis.io/docs/latest/develop/reference/eviction/) — Eviction policies / Using the INFO command：maxmemory、策略候选范围和排查计数。
- [Redis 7.2 配置源码](https://raw.githubusercontent.com/redis/redis/7.2/redis.conf) — maxmemory / maxmemory-policy：7.2 配置对近似 LRU、volatile 策略与无候选时写入错误的说明。
- [Redis INFO 命令文档](https://redis.io/docs/latest/commands/info/) — Stats：expired_keys 和 evicted_keys 是实例计数。

覆盖差异：旧 M07 按业务场景选择 Redis 数据结构。M20 考缓存键何时消失及原因排查，H22 才讨论缓存更新竞态和并发回源。

可接受表达／替代方案：

- 可补充主库不存在时的受控空值处理，或用等效监控项观察淘汰趋势。
- 明确 TTL 是最长有效期的一项约束，命中仍受其他删除原因影响。

应识别的误解：

- 有 TTL 就保证键在到期前一直存在。
- 设置 noeviction 后 Redis 会停止正常过期。
- volatile-lru 与 allkeys-lru 都会淘汰任何键。

## M21｜RDB 与 AOF 的恢复取舍

分类：`REDIS`；难度：`MEDIUM`；知识点：`redis_persistence_tradeoffs`；修订：1；来源：D1_NEW。

适用版本：Redis OSS 7.2，单实例本地持久化，讨论普通故障和恢复目标。

### 面试问题

有个 Redis 7.2 实例保存可重新计算的业务状态。团队能接受丢最近少量更新，但希望重启尽快恢复。RDB 和 AOF 分别保存什么、怎样恢复？如果使用 AOF 的 everysec，你会怎样解释丢失窗口、恢复成本和独立备份的关系？

### 参考答案

RDB 按时间点保存数据快照，重启读入快照重建数据，故障时可能丢失最近一次成功快照后的修改。快照文件通常紧凑，加载较直接，但后台生成快照仍有 fork、写盘等成本。AOF 追加记录会改变数据的操作，恢复时读取基础文件并应用增量，重写可以压缩历史操作。使用 everysec 时通常每秒刷盘一次，常把进程或主机故障下近期写入的典型丢失窗口估在约一秒，具体会受磁盘、操作系统和故障类型影响。更频繁刷盘一般提高耐久性，但也增加 I/O 或延迟。若 RDB 和 AOF 都启用，正常重启从 AOF 恢复。选择前先给出可接受的数据损失和恢复时间，拿真实数据量演练重启。持久化文件若与实例共盘，磁盘损坏或误操作仍可能影响数据，应独立备份并验证可恢复。

### 评分点（合计 100）

1. **M21-SP1 · CORE · 25**：说明 RDB 保存某时刻的数据快照，恢复时加载最近可用快照，之后的更新存在丢失窗口。
2. **M21-SP2 · KEY · 25**：说明 AOF 记录写操作，恢复依靠基础状态与增量，重写用于限制历史文件体积。
3. **M21-SP3 · PRINCIPLE · 20**：说明 everysec 是周期性刷盘，约一秒是典型量级，且更频繁刷盘与持久性及 I/O 成本存在取舍。
4. **M21-SP4 · KEY · 15**：结合数据损失容忍度与恢复时间比较快照加载、日志应用等成本，用恢复演练验证选择。
5. **M21-SP5 · KEY · 15**：说明本地持久化不能覆盖共盘损坏或误删，提出独立备份和恢复验证。

### 技术依据

- [Redis 持久化文档](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/) — RDB advantages and disadvantages / AOF / Backing up Redis data：快照与日志恢复、刷盘策略、重写、多段 AOF 和备份。
- [Redis 7.2 配置源码](https://raw.githubusercontent.com/redis/redis/7.2/redis.conf) — SNAPSHOTTING / APPEND ONLY MODE：确认快照策略、appendfsync everysec 和 Redis 7.2 的 AOF 配置。

覆盖差异：旧 M07 考数据结构。M20 考在线 TTL 和内存限制。M21 考实例重启时可恢复的状态及独立备份。H23 的原子性和执行错误也不属于本题。

可接受表达／替代方案：

- 若状态完全可从权威库重建，可选择 RDB 为快速回暖，或用 AOF 缩小丢失窗口并说明恢复演练计划。
- 接受用 RPO 和 RTO 表达目标，无需背出固定的恢复速度排序。

应识别的误解：

- everysec 对磁盘和系统故障都能保证最多丢一秒。
- AOF 永远无法压缩或总是比 RDB 恢复慢。
- 同盘开启两种持久化就等于独立备份。

## H01｜泛型擦除与运行时类型边界

分类：`JAVA_BASIC`；难度：`HARD`；知识点：`generic_type_erasure`；修订：2；来源：V1_REVISED。

适用版本：Java SE 21（instanceof 参数化类型测试放宽自 Java 16 起）

### 面试问题

Java 的泛型擦除是什么？它会带来哪些限制？为什么很多泛型类型信息在运行时不存在？

### 参考答案

Java 泛型主要在编译期提供类型检查。擦除会将参数化类型映射到对应的原始类型，将类型变量映射到其最左边界的擦除类型；没有显式边界时是 Object。编译器按需要插入类型转换，并生成桥接方法以维持重写后的多态。

例如 ArrayList<String> 和 ArrayList<Integer> 不会各自生成一个不同的 ArrayList 运行时 Class，运行时不能仅凭容器对象确认元素的泛型实参。不能直接 new T()、new T[] 或 new List<String>[]，也不能仅靠擦除后相同的参数签名构成有效重载。

instanceof 的限制要看操作数静态类型：Object x 不能直接写 x instanceof List<String>，但可以检查 List<?>；Java 21 中，List<Integer> x 可以写 x instanceof ArrayList<Integer>，这依赖编译期已经满足的类型关系，运行时仍只检查容器类型，不检查集合里每个元素是不是 Integer。因此不能笼统说所有带具体泛型参数的 instanceof 都不合法。

擦除也不意味着 class 文件完全没有泛型信息：字段、方法或父类型声明的泛型签名可供反射读取，但它不同于任意对象携带完整的实际类型参数。

### 评分点（合计 100）

1. **H01-SP1 · CORE · 25**：能说明 Java 泛型主要提供编译期类型安全，编译后类型参数按照擦除规则被替换为其 bound 或 Object。
2. **H01-SP2 · PRINCIPLE · 20**：能说明不同参数化类型通常不会生成各自独立的运行时 Class，例如 List<String> 与 List<Integer> 共享同一个原始类实现。
3. **H01-SP3 · INTERNAL · 20**：能说明编译器为了保持类型安全和多态语义可能插入类型转换以及生成 bridge method。
4. **H01-SP4 · KEY · 25**：能正确解释至少两项限制及其条件，例如不能直接 new T()、new T[] 或 new List<String>[]，以及不能仅靠擦除后相同的参数签名重载。若谈 instanceof，应区分 Object 不能直接测试 List<String> 与 Java 21 中 List<Integer> 可以测试 ArrayList<Integer>；不能用该测试证明元素实际泛型类型。无需背出此例，其他准确等价解释同样给分。
5. **H01-SP5 · ADVANCED · 10**：能指出“泛型信息全部在运行时消失”并不严谨，class 声明的 generic signature 等元数据仍可能通过反射获得。

### 技术依据

- [JLS 21 Type Erasure](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.6) — 4.6, 4.7：擦除、最左边界和可具体化类型
- [JLS 21 instanceof](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.20.2) — 15.20.2：Java 16 后参数化类型测试的静态条件与示例
- [JVMS 21 Signature attribute](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.9) — 4.7.9：Signature 属性保留声明泛型信息，支持反射读取，与对象的运行时元素实参不同

覆盖差异：沿用 H01 原有泛型擦除主题，仅修正参考答案与 SP4 的条件；与 M09 的 PECS API 读写约束不同。

可接受表达／替代方案：

限制任选两项准确解释即可；不要求复述例子。准确指出合法的参数化 instanceof 情形不能扣分。

应识别的误解：

错误：任何具体参数化 instanceof 都不合法；错误：运行时逐个验证 List 元素的实参；错误：任何泛型声明信息都完全消失。

## H02｜ConcurrentHashMap

分类：`JAVA_COLLECTION`；难度：`HARD`；知识点：`concurrent_hashmap`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

JDK 8 的 ConcurrentHashMap 如何实现并发安全？CAS、`synchronized` 和桶结构分别起什么作用？

### 参考答案

JDK 8 的 ConcurrentHashMap 不再使用 JDK 7 那种 Segment 分段锁结构，整体也是 table + bin 的组织方式，桶内可以是链表或树结构。

插入时如果目标桶为空，可以通过 CAS 尝试直接放入新节点，不需要获得传统互斥锁。

如果桶已经有节点并且需要修改桶内结构，会对桶头节点使用 `synchronized`，因此锁粒度主要缩小到具体桶，而不是整个 Map。

扩容时还设计了多个线程协作迁移机制；普通 `get` 通常不需要像写操作一样获取桶锁，而是依靠并发数据结构的内存可见性设计读取。

所以不能把 ConcurrentHashMap 简化理解为“所有操作都加 synchronized”。

### 评分点（合计 100）

1. **H02-SP1 · CORE · 20**：能说明 JDK 8 ConcurrentHashMap 不再以 Segment 作为主要分段锁结构，而使用 table 与桶内链表/树结构组织数据。
2. **H02-SP2 · INTERNAL · 25**：能说明目标桶为空时，写入线程可以通过 CAS 将新节点安装到桶位置，从而避免无必要的互斥加锁。
3. **H02-SP3 · INTERNAL · 30**：能说明桶已存在并需要修改桶内链表或树时，会对当前桶头节点进行 synchronized，同一时间主要限制对该桶结构的并发修改，而不是锁住整个 Map。
4. **H02-SP4 · PRINCIPLE · 15**：能说明普通读取通常不需要和写入采用相同的互斥锁流程，而依靠并发结构的内存可见性设计安全读取。
5. **H02-SP5 · ADVANCED · 10**：能说明扩容时多个线程可以协作搬迁桶中的数据，例如线程遇到迁移状态后继续参与迁移。能够正确说明协作扩容行为即覆盖，不要求背出具体内部类名或方法名。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — H02：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/h02_openjdk8u_concurrent_hashmap.md) — H02：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## H03｜G1 GC

分类：`JVM`；难度：`HARD`；知识点：`g1_gc`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

G1 垃圾收集器的核心设计思路是什么？Region、Young GC 和 Mixed GC 如何帮助它控制停顿时间？

### 参考答案

G1 把 Java Heap 划分成很多大小相等的 Region，而不是要求整个年轻代和老年代始终对应一段固定连续地址。Region 可以承担 Eden、Survivor、Old 等不同角色。

G1 会根据回收价值和预测成本选择本次 Collection Set，目标之一是在指定的暂停时间目标附近完成尽可能有效的回收。

Young GC 主要回收年轻代 Region，并把存活对象复制到 Survivor 或 Old Region。

当老年代达到一定条件后，G1 会进行并发标记，分析 Old Region 的存活情况。之后 Mixed GC 会在回收 Young Region 的同时加入部分回收价值较高的 Old Region。

G1 不是“没有 STW”，而是通过 Region 化、并发阶段和对 Collection Set 的选择，提高暂停时间的可预测性。

### 评分点（合计 100）

1. **H03-SP1 · CORE · 25**：能说明 G1 将堆划分为大量 Region，并根据需要让 Region 承担 Eden、Survivor、Old 等角色，而不是只依赖固定连续代空间。
2. **H03-SP2 · PRINCIPLE · 25**：能说明 G1 根据回收收益和预计处理成本选择 Collection Set，其重要目标是尽量满足可配置的暂停时间目标。
3. **H03-SP3 · KEY · 15**：能说明 Young GC 主要处理年轻代 Region，并通过对象复制完成存活对象晋升或转移。
4. **H03-SP4 · INTERNAL · 20**：能说明 G1 会进行并发标记来分析老年代 Region 的存活情况，为后续选择需要回收的 Old Region 提供依据。
5. **H03-SP5 · ADVANCED · 15**：能说明 Mixed GC 会同时处理 Young Region 和部分 Old Region，并认识到 G1 仍存在 Stop-The-World 阶段，并非完全无停顿 GC。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — H03：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/h03_hotspot21_g1_gc.md) — H03：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## H04｜ThreadPoolExecutor Design

分类：`JAVA_CONCURRENCY`；难度：`HARD`；知识点：`thread_pool_design`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

如果一个后端服务会突然收到大量任务，而且任务不能轻易丢失，你会如何设计 ThreadPoolExecutor 的核心线程数、最大线程数、队列和拒绝策略？为什么？

### 参考答案

我不会先背一个固定线程数公式，而会先判断任务是 CPU 密集还是大量等待 I/O，并根据机器核数、下游容量和压测结果决定 `corePoolSize` 与 `maximumPoolSize`。

ThreadPoolExecutor 一般先尝试使用 core worker；达到 core 后任务进入 `workQueue`；队列无法继续接收时，才会在 `maximumPoolSize` 允许范围内继续创建线程；线程和队列都达到上限后执行拒绝策略。

我一般不会使用无限队列来隐藏流量尖峰，因为任务堆积可能拖垮内存并制造巨大延迟。需要结合业务设置有界队列。

如果任务不能丢，就不能选择静默丢弃任务的拒绝策略。可以通过 CallerRuns 类似的反压、业务持久化队列、返回明确失败并由幂等机制重试等办法保证任务有去处。

最后还需要监控 active count、queue size、reject count、任务耗时，并通过真实压测调整参数。

### 评分点（合计 100）

1. **H04-SP1 · CORE · 25**：能正确说明 ThreadPoolExecutor 的主要提交路径：core worker、workQueue、maximumPoolSize、RejectedExecutionHandler 之间的先后关系。
2. **H04-SP2 · PRINCIPLE · 20**：能根据 CPU/I/O 特征、机器资源和下游系统承载能力设计线程数量，而不是使用固定万能线程数公式。
3. **H04-SP3 · KEY · 20**：能意识到队列必须考虑容量和排队延迟，避免使用无界队列把突发流量无限转化为内存和延迟压力。
4. **H04-SP4 · KEY · 20**：在任务不能丢失的约束下，能够采用反压、持久化、明确失败后幂等重试等方案，而不是使用 Discard/DiscardOldest 后静默丢弃业务任务。
5. **H04-SP5 · ADVANCED · 15**：能设计线程池监控和压测反馈机制，根据 active threads、queue depth、reject rate、task latency 等指标持续调整参数。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — H04：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/h04_java21_thread_pool_design.md) — H04：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## H05｜Spring Transaction Boundary

分类：`SPRING`；难度：`HARD`；知识点：`transaction_boundary`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

Spring 事务有哪些常见失效场景？如果一个业务方法同时调用多个数据库操作，应该如何设计事务边界和异常回滚策略？

### 参考答案

Spring 声明式事务通常依赖 AOP 代理，因此调用必须真正经过代理。常见问题包括同类内部自调用绕过代理、某些无法被代理的方法形式，以及错误的事务配置。

另一个很常见的问题是异常处理。Spring 默认情况下，`RuntimeException` 和 `Error` 会触发回滚，而 checked exception 默认不触发回滚；如果业务需要，应该显式配置 `rollbackFor` 等回滚规则。

如果事务内部把异常 catch 住以后正常返回，外层事务基础设施可能不知道应该回滚，所以异常处理和 rollback 语义必须一起设计。

事务边界一般应该围绕一个完整业务用例，让必须保持原子一致的数据库操作处于同一个事务中。同时不应该随意把耗时远程调用放进长数据库事务，否则可能长时间占用数据库连接和锁。

如果不同资源之间不能靠单数据库事务保证一致性，就需要另外设计消息、补偿或最终一致性机制，而不是扩大一个本地事务就认为问题解决了。

### 评分点（合计 100）

1. **H05-SP1 · CORE · 20**：能说明 Spring 声明式事务主要建立在代理拦截之上，并识别 self-invocation 等绕过代理导致 @Transactional 不生效的情况。
2. **H05-SP2 · KEY · 20**：能说明 Spring 默认 rollback 规则与异常类型有关，RuntimeException/Error 默认触发回滚，而 checked exception 默认不触发回滚；业务需要时可配置 rollbackFor 等规则。
3. **H05-SP3 · KEY · 20**：能认识到 catch 后吞掉异常、错误的事务管理器或错误的调用边界都可能使实际事务行为与开发者预期不同。
4. **H05-SP4 · PRINCIPLE · 25**：能按照完整业务用例定义事务边界，使必须共同成功或失败的数据库操作位于同一事务，并避免不必要地扩大事务范围。
5. **H05-SP5 · ADVANCED · 15**：能意识到长时间远程调用、跨资源操作不能简单依赖单数据库本地事务解决，并能提出缩短事务或采用消息、补偿等一致性方案。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — H05：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/h05_spring_6_2_transaction_boundary.md) — H05：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## H06｜InnoDB MVCC / Read View

分类：`MYSQL`；难度：`HARD`；知识点：`mvcc_read_view`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

InnoDB 在可重复读隔离级别下如何通过 MVCC 实现一致性读？Undo Log、事务 ID 和 Read View 分别发挥什么作用？

### 参考答案

InnoDB 会保存行的多版本信息。聚簇索引记录内部包含最后修改该行的事务 ID 等隐藏信息，并能够通过 rollback pointer 与 Undo Log 中的旧版本关联。

当一致性读不能直接看到当前版本时，InnoDB 可以借助 Undo Log 重建较早版本。

Read View 相当于当前一致性读使用的可见性快照，结合记录版本对应的事务 ID 判断一个版本对当前事务是否可见。

在 REPEATABLE READ 下，同一个事务中的普通 consistent read 通常使用第一次一致性读建立的快照，因此其他事务之后提交的数据不会突然出现在后续快照读中。

但这需要区分一致性快照读和 locking/current read，例如 `SELECT ... FOR UPDATE` 不是普通的历史快照读语义。

### 评分点（合计 100）

1. **H06-SP1 · CORE · 20**：能说明 MVCC 通过维护同一逻辑行的多个版本，使普通读取可以读取满足当前可见性条件的历史版本，而不是所有读取都必须阻塞在写锁上。
2. **H06-SP2 · INTERNAL · 20**：能说明 InnoDB 记录包含修改版本相关的事务标识，并能够借助 Undo Log/rollback chain 找到或重建较早版本。
3. **H06-SP3 · PRINCIPLE · 25**：能说明 Read View 描述一次一致性读所看到的事务可见性边界，并通过记录版本对应的事务 ID 判断版本是否可见。
4. **H06-SP4 · KEY · 20**：能说明在 REPEATABLE READ 下，一个事务中的一致性快照通常基于第一次 consistent read 建立，因此后续普通快照读具有可重复读效果。
5. **H06-SP5 · ADVANCED · 15**：能区分 consistent nonlocking read 与 SELECT ... FOR UPDATE 等 locking/current read，认识到二者并不完全遵循同一历史快照读取语义。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — H06：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/h06_mysql_8_0_innodb_mvcc_consistent_read.md) — H06：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## H07｜Redis Distributed Lock

分类：`REDIS`；难度：`HARD`；知识点：`distributed_lock`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

如何使用 Redis 实现一个相对可靠的分布式锁？为什么简单的 `SETNX` 后直接 `DEL` 可能出错？

### 参考答案

一个基础的单 Redis 实例锁至少应该在一次原子 `SET` 中同时使用 `NX` 和过期时间，例如：

```text
SET lockKey randomValue NX PX ttl
```

这样可以避免先 `SETNX` 成功后程序崩溃、还没来得及设置过期时间导致永久锁。

value 不能只是固定字符串，而应该是本次持锁者的唯一 token。释放锁时必须先判断 token 仍然属于自己，再删除 key，而且比较和删除必须原子执行，可以使用 Lua 等原子方式实现。

否则可能出现 A 的锁过期后 B 获得锁，A 业务结束后直接 `DEL`，把 B 的新锁误删。

还要处理业务执行时间超过 TTL 的问题，可以根据业务选择合理 TTL、受控续租机制或者 fencing token 等更强约束。

Redis 锁有自己的故障和网络分区边界。如果真正的数据正确性最终落在数据库等系统中，还应该让最终数据层拥有自己的并发约束，不能因为拿到了 Redis 锁就假设所有故障场景绝对安全。

### 评分点（合计 100）

1. **H07-SP1 · CORE · 25**：能使用一次原子 SET 同时设置 NX 与合理的过期时间，避免 SETNX 和设置 TTL 分成两个操作造成永久锁风险。
2. **H07-SP2 · KEY · 25**：能为每次获取锁生成唯一 token，并在释放锁时只允许删除 token 仍属于当前客户端的锁。
3. **H07-SP3 · PRINCIPLE · 20**：能说明检查 token 与删除锁必须原子执行，例如使用 Lua，从而避免锁过期并被其他客户端获得后旧持有者误删新锁。
4. **H07-SP4 · INTERNAL · 15**：能考虑业务执行超过锁 TTL 的情况，并提出合理 TTL、受控续租或 fencing 等处理思路。
5. **H07-SP5 · ADVANCED · 15**：能认识 Redis 分布式锁存在网络分区、节点故障等边界，对于关键数据仍应结合数据库约束或其他一致性机制建立最终正确性保障。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — H07：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/h07_redis_distributed_lock_baseline.md) — H07：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## H08｜TCP Connection Lifecycle

分类：`NETWORK`；难度：`HARD`；知识点：`tcp_connection_lifecycle`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

TCP 为什么需要三次握手和四次挥手？TIME_WAIT 和 CLOSE_WAIT 分别为什么出现，数量异常时通常意味着什么？

### 参考答案

建立 TCP 连接时双方都需要发送自己的初始序列号，并确认已经正确收到对方的序列号。第二步可以把 ACK 和自己的 SYN 合在一个报文里，因此形成常见的三次握手。

三次握手除了同步双方状态和序列号，也有助于避免历史重复连接请求造成错误连接。

TCP 是全双工的，两边的数据发送方向可以独立关闭。一方发送 FIN 只表示自己不再发送数据，对方仍可能还有数据要发，因此双方通常各需要发送自己的 FIN 并收到 ACK，表现为常说的四次挥手；实际 ACK 和 FIN 也可能在时机合适时合并。

TIME_WAIT 通常出现在主动关闭的一方，用来保证最后 ACK 有机会重传并避免旧连接的延迟报文影响后续连接。

CLOSE_WAIT 表示已经收到对方 FIN，但本地应用还没有完成自己的 close。大量长期 CLOSE_WAIT 往往意味着应用没有及时关闭 socket；大量 TIME_WAIT 则要结合是否存在大量短连接和主动关闭行为分析，不能看到数量高就直接认为是 bug。

### 评分点（合计 100）

1. **H08-SP1 · CORE · 20**：能说明三次握手使通信双方交换并确认彼此的初始序列号与连接状态，SYN 与 ACK 可以组合，因此形成三报文建立过程。
2. **H08-SP2 · PRINCIPLE · 20**：能说明三次握手的重要目的之一是避免旧的重复连接请求造成双方状态混乱，而不是只回答“因为 TCP 规定三次”。
3. **H08-SP3 · KEY · 20**：能说明 TCP 是全双工连接，两个发送方向可以独立关闭，因此双方通常分别发送 FIN 并确认，形成常见的四次挥手过程。
4. **H08-SP4 · INTERNAL · 20**：能说明 TIME_WAIT 通常位于主动关闭端，用于处理最后 ACK 可靠性和旧连接延迟报文，并理解它不是天然异常状态。
5. **H08-SP5 · ADVANCED · 20**：能说明 CLOSE_WAIT 表示本地已收到远端 FIN 但本地应用尚未完成关闭，并能根据大量 CLOSE_WAIT/TIME_WAIT 的不同成因进行故障判断。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — H08：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/h08_rfc9293_tcp_connection_lifecycle.md) — H08：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## H09｜Concurrent Inventory + Idempotency

分类：`PROJECT_SCENARIO`；难度：`HARD`；知识点：`concurrent_inventory_idempotency`；修订：1；来源：V1_UNCHANGED。

适用版本：沿用冻结 V1 的显式技术版本；M02/H02 为 JDK 8 实现，其余见对应历史来源。

### 面试问题

假设库存最终以 MySQL 为事实来源，当前库存只剩 1 个，同时来了很多个扣减请求，而且客户端还可能因为超时重复请求。你会如何设计，保证不超卖、不重复扣减，并能够正确处理失败和重试？

### 参考答案

我会先把两个问题分开：一个是并发请求导致超卖，另一个是同一个业务请求因为重试导致重复扣减。

对防超卖，如果库存最终以 MySQL 为准，可以使用数据库原子条件更新，例如：

```sql
UPDATE product
SET stock = stock - 1
WHERE id = ?
  AND stock > 0;
```

然后检查 affected rows，只有更新成功的请求才算扣减成功。

也可以使用带 version 的乐观锁。另一种正确方案是在一个事务里先通过 `SELECT ... FOR UPDATE` 锁住对应库存行，确认库存大于 0 后再扣减。关键点是库存检查和扣减必须受到同一个原子数据库操作或事务行锁临界区保护，不能无锁地先 SELECT，再在 Java 里判断后单独 UPDATE。

对重复请求，我会让一次业务操作携带稳定的 requestId 或业务幂等键，并在数据库建立唯一约束。第一次请求创建处理记录并执行扣减；同一个 requestId 再进来时不能再次扣库存，而是返回之前的处理状态或结果。

库存扣减、幂等记录以及与业务强相关的订单状态，应该设计清楚事务边界。事务中任何关键步骤失败，就不要留下“库存已经扣了但业务记录没成功”这种半完成状态。

对数据库死锁、锁等待超时或者网络超时，可以设计有限重试，但重试必须继续携带同一个幂等键。特别是客户端或网络超时并不代表数据库操作一定失败，因此不能简单生成一个新的 requestId 再执行一次扣减，而应先结合已有幂等状态判断前一次操作结果。

### 评分点（合计 100）

1. **H09-SP1 · CORE · 30**：能把库存检查和扣减放进同一次原子数据库操作，或者同一个数据库事务保护的行锁临界区，避免无锁地先查询库存、在 Java 中判断后再单独更新。条件 UPDATE 并检查更新行数、version 乐观锁、事务内 SELECT ... FOR UPDATE 后检查并扣减，任一正确方案均可覆盖。
2. **H09-SP2 · KEY · 25**：能为一次业务操作设计稳定的 requestId 或幂等键，并通过数据库唯一约束或等价的持久化机制保证同一业务请求即使被客户端重复提交，也不会再次执行库存扣减。
3. **H09-SP3 · PRINCIPLE · 20**：能明确设计事务边界，使库存变更、幂等状态以及必须保持一致的核心业务记录不会留下部分成功状态；事务失败时能够整体回滚或进入明确的可恢复状态。
4. **H09-SP4 · ADVANCED · 25**：能正确处理失败与重试语义：认识到客户端或网络超时不等于数据库操作失败；对死锁、锁等待超时等可重试失败采用有限重试；重试继续复用同一个业务幂等键，并能够根据已有处理状态避免重复扣减。

### 技术依据

- [冻结 V1 题库](/Users/kun/Developer/projects/ai-interview-system/docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json) — H09：原始题干、答案和评分点；本轮逐字段保留（H01 另记修订）
- [既有知识资料与原始官方来源](/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/h09_mysql_8_0_inventory_idempotency_scenario.md) — H09：历史已审查技术资料；本轮不改知识库、不重复宣称真实检索通过

覆盖差异：保留 D0 已审查主题。与新增题的差异见同难度同分类新题的 coverageDifference。

可接受表达／替代方案：

评分按机制、条件和可验证解释；不要求逐字复述参考答案。

应识别的误解：

以既有来源中的边界为准，不将参考答案用词作为唯一正确措辞。

## H10｜反射、模块封装与访问边界

分类：`JAVA_BASIC`；难度：`HARD`；知识点：`reflection_module_access`；修订：1；来源：D1_NEW。

适用版本：Java 21，两个具名模块，普通 private 实例字段，不考虑自定义 SecurityManager。

### 面试问题

Java 21 中，序列化模块通过 getDeclaredField 找到了另一个具名模块中 public DTO 的 private 字段。DTO 所在包 exports 了但没有 opens。为什么找到字段仍可能无法读取？trySetAccessible 和 setAccessible(true) 会有什么区别，你会怎样修改代码或模块配置？如果外部请求可以指定字段名，应限制哪些访问？

### 参考答案

getDeclaredField 可以找到类自己声明的私有字段，所以能获得字段的元数据。真正执行 Field.get 时，默认还要做访问检查，跨模块读取这个 private 字段会失败。exports 提供外部模块访问公开 API 的能力，包仅 exports 时并没有允许外部模块做这样的深层反射。按题设 trySetAccessible 会返回 false，setAccessible(true) 会抛 InaccessibleObjectException，未成功放宽检查时直接 Field.get 则会抛 IllegalAccessException。如果能改 DTO，优先使用对外公开的 getter、工厂方法或专门的映射接口。框架确实要访问私有字段，可以将该 DTO 包定向 opens 给序列化模块，并在访问失败时明确报错或回退。反射对象和字段范围由应用配置控制，避免把任意类名、字段名当作无条件可信的请求输入。

### 评分点（合计 100）

1. **H10-SP1 · CORE · 25**：区分查找成员元数据与读取成员：getDeclaredField 能找到本类 private 字段，Field.get 默认仍执行访问检查。
2. **H10-SP2 · PRINCIPLE · 25**：区分 exports 的公开 API 边界与 opens 的深层反射权限，说明题设跨模块 private 访问不能只靠 exports。
3. **H10-SP3 · KEY · 20**：准确区分题设下 trySetAccessible 返回 false 与 setAccessible(true) 抛 InaccessibleObjectException，且未放宽时直接读取可能抛 IllegalAccessException。
4. **H10-SP4 · KEY · 20**：提出通过公开访问方法，或仅将目标包定向 opens 给可信模块解决，并给出访问失败时的明确处理。
5. **H10-SP5 · ADVANCED · 10**：说明反射范围应由应用控制，避免把不可信的类型名或成员名直接变成任意访问目标。

### 技术依据

- [Java 21 Class](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getDeclaredField(java.lang.String)) — getDeclaredField：返回当前类或接口声明字段的元数据，成员发现与调用权限分离。
- [Java 21 AccessibleObject](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AccessibleObject.html#trySetAccessible()) — trySetAccessible / setAccessible：跨模块 private 深反射的包开放条件与失败表现。
- [Java 21 Field](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html#get(java.lang.Object)) — get：执行访问检查且目标不可访问时抛 IllegalAccessException。
- [Java SE 21 语言规范](https://docs.oracle.com/javase/specs/jls/se21/html/jls-7.html#jls-7.7.2) — §7.7.2 Exported and Opened Packages：exports 与 opens 的用途、定向开放语法。

覆盖差异：旧 H01 考泛型擦除与类型检查限制。H10 考普通字段反射和模块权限，题意无需讨论泛型。H14 考类型加载身份，也无需解释反射打开权限。

可接受表达／替代方案：

- 接受给出 opens dto.pkg to serializer.module 的示意。
- 若部署环境明确允许，可以提出定向 --add-opens 的临时兼容办法，说明其范围和长期取舍。

应识别的误解：

- 能找到私有字段就一定能成功读取。
- exports 等于 opens。
- setAccessible(true) 在 Java 21 可以绕过所有模块边界。

## H11｜Stream 惰性执行与并行副作用

分类：`JAVA_BASIC`；难度：`HARD`；知识点：`stream_laziness_parallel`；修订：1；来源：D1_NEW。

适用版本：Java 21 标准 Stream API，输入为顺序稳定、执行时未被外部修改的普通 List。

### 面试问题

有人用 list.stream().peek(audit::add).count() 同时计数和收集审计数据，发现 audit 可能为空。另一段代码用 list.parallelStream().forEach(out::add) 把结果写进普通 ArrayList。为什么这两段可能出错？如果要筛选结果并按原顺序产出，再记录审计，你会怎样改写并决定是否并行？

### 参考答案

peek 是惰性的中间操作，count 是终止操作。某些已知大小的流可以直接算出 count，只要前面的操作不改变计数，peek 可能不执行，不能把必要审计挂在这里。第二段把多个工作线程写入同一个普通 ArrayList，可能丢数据或破坏结构，即使换成同步容器，forEach 也不保证原顺序。先用不改流来源、也不依赖可变共享状态的 filter 和 map 生成数据，再用 toList 等归约收集。对于有固定遍历顺序的 List，toList 会保留结果顺序。然后在明确的顺序和错误处理下记录审计。只有当数据易拆分、每项计算足够重且彼此独立、实测收益大于调度与合并开销时，再考虑并行。

### 评分点（合计 100）

1. **H11-SP1 · CORE · 25**：区分中间操作和终止操作，说明 count 对已知大小且不改变计数的管线可能跳过遍历，peek 审计副作用无法作为保证。
2. **H11-SP2 · CORE · 25**：识别并行 forEach 写普通 ArrayList 的共享可变状态竞态，同时知道并行 forEach 不保证按流的遍历顺序执行。
3. **H11-SP3 · PRINCIPLE · 20**：要求流函数不干扰来源，并通常不依赖执行时会变化的共享状态。
4. **H11-SP4 · KEY · 20**：提出先用 filter、map 和保序收集得到结果，再在明确的独立步骤执行审计。
5. **H11-SP5 · ADVANCED · 10**：说明选择并行需要考虑数据能否拆分、计算量与调度合并成本，并用实际负载度量。

### 技术依据

- [Java 21 Stream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html#count()) — count、peek：已知结果时可直接求 count，可能跳过 peek 等中间操作。
- [Java 21 Stream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html#forEach(java.util.function.Consumer)) — forEach / forEachOrdered / toList：forEach 不保证并行遇到顺序，toList 按存在的遇到顺序收集。
- [Java 21 Stream 包规范](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html) — Non-interference / Stateless behaviors / Side-effects / Parallelism：流行为参数对干扰、共享可变状态和副作用的约束。
- [Java 21 ArrayList](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html) — 类说明：普通 ArrayList 的并发结构修改需要外部同步。

覆盖差异：旧 H01 考泛型。M10 考普通集合迭代中结构修改，M11 考复制与视图。H11 核心是流的惰性优化、并行行为参数和保序归约。

可接受表达／替代方案：

- 可使用顺序循环完成收集与审计。若选择流，可用保持遇到顺序的收集或在正确位置显式使用 forEachOrdered。
- 能解释同步容器只解决部分并发安全问题，仍需处理顺序与失败。

应识别的误解：

- 调用 count 后所有 peek 必定执行。
- parallelStream 里的 forEach 自动保留输入顺序。
- 结果 List 保序说明所有 map 或 peek 的执行也保序。

## H12｜并发 Map 中复合操作如何保持原子性

分类：`JAVA_COLLECTION`；难度：`HARD`；知识点：`concurrent_map_atomicity`；修订：1；来源：D1_NEW。

适用版本：Java 21 ConcurrentHashMap 的公开 API 契约。

### 面试问题

服务用 ConcurrentHashMap 存放按用户 ID 累计的请求次数。现在先 get，空值按 0 处理，再 put(count + 1)，偶尔出现计数少了。为什么？如果还需要按键首次放入一个对象，putIfAbsent 和 computeIfAbsent 有什么区别？能否在计算回调里发远程请求或同时更新其他键？

### 参考答案

get 和 put 各自是线程安全的，但它们之间可以插入另一个线程的操作。两个线程同时读到 3，再各写 4，就丢了一次增量。这个例子可以用 merge(userId, 1L, Long::sum) 或 compute，把单键读改写放到一次原子操作中。对象已经准备好时，putIfAbsent 能原子地检查并写入。它接收的是已经求值的对象，所以并发线程可能都先构造了一遍。computeIfAbsent 只在该次原子调用发现键缺失时调用映射函数，返回非 null 才存入。函数返回 null、失败或映射后续被移除，未来仍可能再次计算。计算期间其他更新可能阻塞，所以回调保持短小，不在其中更新同一个 Map 的其他映射，也别放慢且可能重复产生副作用的远程动作。单键原子操作也不能维护跨键或跨系统的一致性。

### 评分点（合计 100）

1. **H12-SP1 · CORE · 25**：能用两个线程读到同一旧值并覆盖说明 get-then-put 的丢更新竞态。
2. **H12-SP2 · CORE · 25**：用 merge、compute 或等效同步机制原子地实现单键计数增量。
3. **H12-SP3 · KEY · 20**：区分 putIfAbsent 原子地登记已求值对象与 computeIfAbsent 在缺失时才调用函数，知道 null、异常或后续移除可以使未来调用重新计算。
4. **H12-SP4 · PRINCIPLE · 20**：说明计算回调会影响并发更新，避免阻塞 I/O 和在回调中修改同一个 Map 的映射。
5. **H12-SP5 · ADVANCED · 10**：说明单键原子性不能保证跨键不变量或远程副作用只执行一次，应单独设计协调。

### 技术依据

- [Java 21 ConcurrentHashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html) — putIfAbsent / computeIfAbsent / compute / merge：单键条件写入、计算与归并的原子性、映射函数边界和潜在阻塞。

覆盖差异：旧 H02 考 JDK 8 的桶、CAS、扩容等内部实现。H12 用 Java 21 API 修复丢更新与懒加载竞态。H13 考遍历时观察到的集合视图。

可接受表达／替代方案：

- 固定已初始化计数对象的场景，可说明 AtomicLong 配合安全初始化，以及映射被移除时要额外协调。
- 可用 LongAdder 说明高并发计数设计，需明确 sum 在并发更新期间的快照语义及是否满足精确读取要求。

应识别的误解：

- 线程安全容器上的多个调用自然组成一个原子操作。
- computeIfAbsent 在键的整个生命周期中永远只执行一次。
- 可以在回调内随意更新同一个 Map 或借它保证远程操作恰好一次。

## H13｜快照迭代与弱一致迭代的取舍

分类：`JAVA_COLLECTION`；难度：`HARD`；知识点：`concurrent_iteration_tradeoffs`；修订：1；来源：D1_NEW。

适用版本：Java 21 CopyOnWriteArrayList 与 ConcurrentHashMap 的迭代契约。

### 面试问题

一个服务要遍历监听器分发事件，监听器变更很少，希望本轮遍历看到开始时的注册列表。另一个任务定期遍历在线用户表做近似统计，允许遍历过程中并发加入和退出。分别使用 CopyOnWriteArrayList 和 ConcurrentHashMap 有什么好处？它们在遍历时会看到什么，付出什么代价？若在线统计改为要求同一时点的准确结果，需要怎样调整？

### 参考答案

监听器场景可以用 CopyOnWriteArrayList。迭代器抓住创建时的数组引用，本轮看到旧成员列表，即使某个监听器后来注销，也可能收到本轮通知。它保留的是元素引用，监听器内部状态仍要单独同步。更新通常会复制底层数组，写入越频繁或列表越大，复制和分配成本越明显。长寿命迭代器还会保留旧数组。在线表可用 ConcurrentHashMap。它的弱一致迭代允许读写并发进行，可能看到部分创建迭代器之后的更新，不承诺全表来自同一时点，也不会因此抛 ConcurrentModificationException。用来做近似统计可以。如果要求一个时点的精确集合，就要在应用层协调写入和快照，简单遍历复制仍可能混入并发更新。

### 评分点（合计 100）

1. **H13-SP1 · CORE · 25**：为低变更、高遍历的监听器选择 CopyOnWriteArrayList，说明其迭代器看到创建时的成员列表。
2. **H13-SP2 · KEY · 20**：说明快照保留元素引用，不能冻结可变元素的内部状态，本轮也可能仍看到已注销成员。
3. **H13-SP3 · PRINCIPLE · 20**：指出写入通常复制底层数组，带来 O(n) 级复制和内存开销，长期持有迭代器还会延长旧数组寿命。
4. **H13-SP4 · CORE · 25**：为允许近似的在线用户表选择 ConcurrentHashMap，说明弱一致迭代可与更新并发，可能看到部分后续变更，且不提供全表时点快照。
5. **H13-SP5 · ADVANCED · 10**：指出确需跨元素精确快照时需协调写入，例如统一锁保护复制或发布不可变版本，单独从并发 Map 遍历复制并不足够。

### 技术依据

- [Java 21 CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html) — 类说明及 iterator：写时复制、迭代器保留创建时数组引用及列表结构快照。
- [Java 21 ConcurrentHashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html) — 类说明与 keySet/entrySet：迭代器弱一致、可见部分并发更新，聚合状态不宜用于并发下的精确控制。

覆盖差异：旧 H02 考并发 Map 内部机制，H12 考单键原子更新。相邻 M10 考普通迭代器修改约定，M11 考只读视图。H13 要根据读取一致性及写入成本选并发容器。

可接受表达／替代方案：

- 监听器可用不可变列表副本配合原子发布实现等效语义。
- 准确快照可用统一读写锁或支持版本的不可变视图，需解释如何与写入对齐。

应识别的误解：

- CopyOnWriteArrayList 会深拷贝每个元素。
- ConcurrentHashMap 迭代器必定观察到所有最新更新。
- 不会抛 ConcurrentModificationException 就说明遍历结果是同一时刻的快照。

## H14｜类加载器隔离与类型身份

分类：`JVM`；难度：`HARD`；知识点：`classloader_type_identity`；修订：1；来源：D1_NEW。

适用版本：Java SE 21，普通类和接口。以宿主与插件各自定义 API 接口为故障条件，ServiceLoader 默认重载作为例子。

### 面试问题

一个 Java 插件启动后抛出 ClassCastException。宿主期望 com.example.Plugin，插件实现也声明了这个接口，两个接口的全限定名相同，但插件包内额外打了一份接口。怎样判断是不是类加载器造成的？应怎样组织宿主和插件的依赖？如果还用 ServiceLoader 发现插件实现，发现过程应使用什么加载器？

### 参考答案

同名接口也可能是两个运行时类型。对这里的普通类和接口，JVM 用二进制名称和定义它的加载器共同确定类型身份。如果宿主与插件分别定义了一份 Plugin，插件实现的是插件那份接口，宿主对自己的 Plugin 做转换就会失败。排查时应比较宿主期望的接口和插件实际实现的接口各自的 Class 对象、getClassLoader() 和类来源，重点确认接口是谁定义的，仅比较名字或 JAR 文件名不够。通常把跨边界接口放在宿主和插件共享的父加载器中，插件包只放实现和私有依赖，插件对共享接口委派给父加载器。如果用 ServiceLoader 发现插件，还需让发现过程使用能看见实现的加载器。默认 load(service) 使用线程上下文类加载器，加载到实现后仍要通过同一份共享接口检查。

### 评分点（合计 100）

1. **H14-SP1 · CORE · 30**：说明普通类或接口由二进制名称与定义加载器共同标识，并能据此解释同名接口转换失败。
2. **H14-SP2 · KEY · 25**：提出让宿主和插件使用同一份父加载器定义的共享接口，插件保留实现和私有依赖。
3. **H14-SP3 · KEY · 20**：区分发起加载和最终定义类型，说明默认父级委派可复用同一类型，自定义加载器策略可能不同。
4. **H14-SP4 · PRINCIPLE · 15**：给出能证实冲突的排查步骤，例如比较两侧接口的 Class 对象和定义加载器，再追查包来源。
5. **H14-SP5 · ADVANCED · 10**：说明 SPI 实现发现的加载器可显式选择，或通过线程上下文提供，发现实现后仍须满足共享接口类型关系。

### 技术依据

- [JVMS 21：创建与加载](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3) — §5.3：运行时类身份取决于二进制名与定义加载器，委派可使发起与定义加载器不同。
- [Java 21 ClassLoader API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html#loadClass(java.lang.String,boolean)) — loadClass(String, boolean)：默认实现先查已加载，再委派父级，最后查找自身。
- [Java 21 ServiceLoader API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html#load(java.lang.Class)) — load(Class) 与 load(Class, ClassLoader)：默认重载使用线程上下文加载器，也可显式指定发现服务的加载器。

覆盖差异：同桶 H03 考 G1 回收机制，H15 考 JVM 故障采证。本题与 M12 都涉及类加载，M12 考生命周期阶段，本题要求定位跨加载器的类型身份冲突。

可接受表达／替代方案：

可以用双方 getInterfaces()、Class.isAssignableFrom()、异常中的加载器信息或类加载日志来证明身份差异。共享接口可位于任一双方确实共用的定义加载器。

应识别的误解：

仅按类全限定名判断身份。把上下文加载器设置成任意插件加载器就可以让两个已定义的接口变成同一类型。

## H15｜Java 服务高 CPU 与内存问题诊断

分类：`JVM`；难度：`HARD`；知识点：`jvm_diagnostics_workflow`；修订：1；来源：D1_NEW。

适用版本：Java 21 HotSpot。题设没有给出 OOM 类型或根因，应说明如何获取证据。

### 面试问题

线上 Java 21 服务变慢，监控显示 CPU 持续升高、GC 频率增加，随后有一次 OutOfMemoryError。你会怎样收集证据，区分业务代码占 CPU、分配过快和对象被长期引用？接下来怎么验证修复？

### 参考答案

我会先对齐同一时段的流量、CPU、延迟、堆使用和 GC 日志，同时读 OOM 的具体信息。Java 堆空间不足、元空间不足和本地线程创建失败需要不同证据。高 CPU 时，可结合系统线程 CPU 和多次线程栈，或用 JFR 执行采样与 GC 事件判断时间主要花在哪儿。业务热点就沿调用栈查循环或计算，GC 活跃则同时看分配速率和回收后堆占用。稳定负载下，如果频繁分配但回收后占用能回落，先查临时对象热点。回收后仍持续上升才更像长寿命对象积累，可在评估停顿和磁盘成本后取堆转储，沿到 GC Roots 的引用链找持有者。修复后用相同输入和负载复现，比较 CPU、分配速率、回收后占用和延迟，再判断是否需要调整堆。

### 评分点（合计 100）

1. **H15-SP1 · CORE · 28**：建立同一时间线，用线程 CPU 加多次栈采样或 JFR 区分业务热点和 GC 消耗。
2. **H15-SP2 · KEY · 20**：先读 OOM 具体报错并区分 Java 堆、元空间或本地资源方向，按类型选证据。
3. **H15-SP3 · PRINCIPLE · 22**：在可比较负载下区分分配速率高与回收后存活对象持续增长，不用 GC 次数直接判定泄漏。
4. **H15-SP4 · KEY · 18**：说明如何用堆转储或同等对象保留证据定位到 GC Roots 的引用链，并考虑现场取证开销。
5. **H15-SP5 · KEY · 12**：用最小复现或可比较负载验证针对性修复，给出与原因相符的前后观测指标。

### 技术依据

- [JDK 21 排障指南：内存泄漏](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshooting-memory-leaks.html) — Understand the OutOfMemoryError Exception；Detect a Memory Leak：OOM 信息区分故障方向，稳定负载下回收后存活集增长可支持泄漏判断。
- [JDK 21 jcmd 手册](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html) — Thread.print、JFR.start、GC.heap_dump：可取线程栈、JFR 录制和堆转储，堆转储具有较高影响。
- [JDK 21 java 手册](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html) — Enable Logging with the JVM Unified Logging Framework：统一日志可收集带时间线的 GC 事件。

覆盖差异：同桶 H03 考 G1 算法，H14 考加载器身份。M13 解释可达性与保留引用，本题把证据串联起来区分 CPU、分配和保留问题，并要求在同负载验证修复。

可接受表达／替代方案：

可以用已有 JFR 录制、线程分析器和监控组合替代某个命令。能说明诊断开销时，可用对象统计作堆转储前的补充。

应识别的误解：

CPU 高与 GC 多足以证明泄漏。所有 OutOfMemoryError 都可以通过加大 Java 堆解决。

## H16｜线程池中的 ThreadLocal 残留与清理

分类：`JAVA_CONCURRENCY`；难度：`HARD`；知识点：`threadlocal_pool_cleanup`；修订：1；来源：D1_NEW。

适用版本：Java SE 21，复用平台工作线程的线程池，应用自己管理的请求上下文。

### 面试问题

线程池任务会用 ThreadLocal 保存当前请求的用户信息。请求 A 执行中抛异常，同一工作线程后来执行请求 B，读取到 A 的信息。为什么会串数据？你怎样修复，并处理任务内再次提交异步工作的情况？

### 参考答案

ThreadLocal 的值跟工作线程关联。线程池复用工作线程，A 抛异常退出时不会自动清理线程绑定的值。如果 B 有路径在设置自己的值前就读取，就可能拿到 A 的值。长寿命线程也可能继续持有请求对象，增加内存保留。对于任务自己拥有的 ThreadLocal，可以在任务入口设置，在 finally 中调用 remove。若是可嵌套的上下文作用域，退出时应恢复进入前的上下文，最外层没有旧值时移除。跨线程任务优先把需要的用户信息作为参数传递。如果必须适配依赖 ThreadLocal 的组件，可提交时捕获最少的不可变数据，到执行线程时安装，退出时清理或恢复。InheritableThreadLocal 在子线程创建时继承，预先创建或复用的线程池工作线程不会为每次提交刷新继承值。

### 评分点（合计 100）

1. **H16-SP1 · CORE · 32**：指出 ThreadLocal 按线程保存值，A 未清理且 B 在线程复用后先读才造成越请求残留。
2. **H16-SP2 · KEY · 25**：在自己控制的任务边界设置值并在 finally 中 remove，保证异常路径同样清理。
3. **H16-SP3 · PRINCIPLE · 15**：解释线程寿命长于请求时，线程残留值可继续持有请求对象，区分信息串用和对象保留两种后果。
4. **H16-SP4 · KEY · 20**：对异步任务采用显式参数，或在提交时捕获必要上下文并在执行线程中安装及清理或恢复。
5. **H16-SP5 · ADVANCED · 8**：说明 InheritableThreadLocal 在新建子线程时取初值，不能作为复用工作线程的按任务传播机制。

### 技术依据

- [Java 21 ThreadLocal API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html) — 类说明与 remove()：值归属于当前线程，remove 清除当前线程中该变量的值。
- [Java 21 ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html) — 类说明与 Hook methods：工作线程复用，可在任务执行边界组织上下文管理。
- [Java 21 InheritableThreadLocal API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/InheritableThreadLocal.html) — 类说明：初值继承发生在子线程创建时。

覆盖差异：同桶 H04 考容量、队列和拒绝，H17 考 Future 编排和资源超时。本题集中于线程复用下的上下文生命周期和隐私隔离。

可接受表达／替代方案：

可以使用统一任务装饰器或框架提供的上下文包装，能说明捕获、恢复与异常清理边界即可。嵌套时可用明确作用域或栈保存旧值。

应识别的误解：

ThreadLocal 隔离的是请求。线程池任务结束会自动清理所有 ThreadLocal。

## H17｜CompletableFuture 的编排、异常与超时

分类：`JAVA_CONCURRENCY`；难度：`HARD`；知识点：`future_timeout_composition`；修订：1；来源：D1_NEW。

适用版本：Java SE 21，普通 CompletableFuture 静态异步工厂与组合 API。题目假设阻塞 HTTP 和统一 800 毫秒预算。

### 面试问题

一个聚合接口要先查用户，再按用户 ID 查权限。同时可以独立查租户配置，最终把权限和配置合并。三个查询都是可能阻塞的 HTTP 调用，总预算 800 毫秒。你如何编排 CompletableFuture，如何处理失败和超时？

### 参考答案

先启动查用户和查租户配置。权限查询依赖用户 ID，可以用 thenCompose 把它返回的 Future 接成同一条链。两路都成功后用 thenCombine 合并，也可用 allOf 等两路完成后分别取结果。allOf 自身不返回结果列表，也不会因为一路失败就自动取消另一路。阻塞 HTTP 我会交给显式的、有容量限制的 I/O 执行器，避免把它们都塞进默认的共享池。权限是必需数据，查询失败就让整体失败，不能把异常当成默认授权。配置可选时，只有约定好的错误才能走安全默认值。把 800 毫秒作为端到端预算，传给下游剩余时间，同时让聚合结果能按时结束。orTimeout 只让尚未完成的 Future 异常完成，调用仍可能继续占用资源，所以要在 HTTP 客户端配置相应超时，并在支持时显式取消底层请求。

### 评分点（合计 100）

1. **H17-SP1 · CORE · 28**：正确编排依赖与并行关系，以 thenCompose 或等价扁平组合连接用户与权限，独立启动配置查询。
2. **H17-SP2 · KEY · 17**：说明如何汇合两路实际结果，若用 allOf 则说明它等待全部完成且结果需要分别读取。
3. **H17-SP3 · KEY · 20**：区分必需依赖的异常传播和可选依赖的有条件降级，避免把权限失败解释为成功。
4. **H17-SP4 · PRINCIPLE · 15**：为阻塞调用选择显式的受控执行器，并说明未指定执行器的常规异步方法使用共享池的风险。
5. **H17-SP5 · KEY · 20**：区分聚合结果超时和底层调用停止，提出总时限、下游剩余预算和客户端超时或受支持的取消。

### 技术依据

- [Java 21 CompletableFuture API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html) — 类说明、thenCompose、thenCombine、allOf、orTimeout、cancel：定义执行器默认策略、组合与异常完成语义。CompletableFuture 的取消不负责通过中断停止底层计算。
- [Java 21 HttpRequest.Builder API](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpRequest.Builder.html#timeout(java.time.Duration)) — timeout(Duration)：HTTP 请求层具有单独的超时设置，可用作客户端预算示例。

覆盖差异：同桶 H04 主考线程池参数，H16 主考上下文清理，H25 主考 HTTP 重试。本题需要在依赖图上安排工作、失败传播和超时资源边界。

可接受表达／替代方案：

用 thenCombine 无需背诵 allOf 的签名。支持取消的客户端可设置取消钩子。显式使用虚拟线程执行器且对外部 I/O 施加并发上限也可。

应识别的误解：

thenApply 会自动扁平化返回的 Future。allOf 在首个失败时停止所有请求。orTimeout 会中断阻塞中的 HTTP 调用。

## H18｜Spring 事务传播与保存点

分类：`SPRING`；难度：`HARD`；知识点：`spring_transaction_propagation`；修订：1；来源：D1_NEW。

适用版本：Spring Framework 6.2，命令式代理事务，单数据源 JDBC 事务管理器且驱动支持保存点。内层异常需满足其回滚规则。

### 面试问题

Spring 服务的外层事务负责创建订单。三个内层步骤分别要求：明细和订单一起成功或回滚，失败的可选优惠步骤可局部回滚，失败审计独立提交并在订单回滚后保留。假设走 Spring 事务代理且使用支持保存点的单数据源 JDBC 事务管理器。怎样选择传播级别？有什么实际风险？

### 参考答案

明细用 REQUIRED，加入外层的物理事务，外层最终提交或回滚时一起生效。若内层按回滚规则把共享事务标记为 rollback-only，外层即使捕获异常后继续，也可能在提交时报 UnexpectedRollbackException。可选优惠用 NESTED，在同一物理事务中建保存点，内层失败按回滚规则退回保存点，外层捕获该失败后可以继续。外层最终回滚时，保存点内的成功写入也一起回滚。审计可用 REQUIRES_NEW，先挂起外层事务，再在独立物理事务写审计。它提交后可独立保留，但通常需要额外数据库连接，且不应依赖读取外层未提交的订单，也要避免等待外层持有的锁。需要按并发量评估连接池，并保证调用确实经过代理。

### 评分点（合计 100）

1. **H18-SP1 · CORE · 25**：将 REQUIRED 对应订单明细，说明与已有外层事务共享物理事务和最终结果。
2. **H18-SP2 · KEY · 20**：说明内层标记 rollback-only 时外层捕获异常无法解除，提交可能抛出 UnexpectedRollbackException。
3. **H18-SP3 · CORE · 22**：将 NESTED 对应局部回滚，说明保存点只在支持时可用且外层回滚仍撤销嵌套成功写入。
4. **H18-SP4 · KEY · 21**：将 REQUIRES_NEW 对应独立审计，说明挂起外层并独立提交或回滚。
5. **H18-SP5 · ADVANCED · 12**：指出独立事务额外连接和依赖外层未提交数据或外层持锁的风险，给出相应限制。

### 技术依据

- [Spring Framework 6.2 事务传播](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html) — REQUIRED、REQUIRES_NEW、NESTED：给出逻辑与物理事务、rollback-only、保存点和连接池风险。
- [Spring Framework 6.2 声明式回滚](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/rolling-back.html) — Rolling Back a Declarative Transaction：异常导致回滚取决于事务规则，不能泛化为任意异常。

覆盖差异：同桶 H05 考代理生效和通用回滚策略，H19 考提交后的外部失败。本题已把代理生效作为前提，专门考三种传播选择和内部事务作用域。

可接受表达／替代方案：

可用独立 Bean 或 TransactionTemplate 表达三个边界。审计也可由独立异步持久化方案实现，但应先解释题设三种传播级别的行为。

应识别的误解：

捕获异常就能解除 rollback-only。NESTED 可以在外层回滚后独立保留。REQUIRES_NEW 一定能读到外层刚写的订单。

## H19｜事务提交后外部动作的失败恢复

分类：`SPRING`；难度：`HARD`；知识点：`transaction_external_side_effect`；修订：1；来源：D1_NEW。

适用版本：Spring Framework 6.2 的命令式事务事件。题设为通用订单通知场景，待办表或状态字段均是候选方案。

### 面试问题

保存订单后要通过 HTTP 通知另一个服务。你打算用 @TransactionalEventListener(AFTER_COMMIT)。如果订单已提交，但进程在发送前崩溃，或者通知已被对方处理而响应超时，怎样恢复？请给一个适合小项目的做法。

### 参考答案

AFTER_COMMIT 把监听放在本地事务提交之后，订单回滚时不发送。但如果只靠内存事件，提交后、发送前进程退出，通知就可能丢失。可以在保存订单的同一事务里写一条待通知记录，或把明确的通知状态和唯一业务键存到订单里。提交后尝试发送，另有定时或人工补偿扫描未完成状态。发送超时意味着结果未知，应优先查对方处理状态，或用同一个事件 ID 重发，由对方用唯一键识别重复并复用结果。成功确认后用新的短事务更新通知状态，失败则按有限次数和退避重试，超限留给对账处理。如果在 AFTER_COMMIT 回调里落库，也要使用能单独提交的新事务。这个设计让订单和待办一起持久化，通知和确认依然是跨系统过程。

### 评分点（合计 100）

1. **H19-SP1 · CORE · 26**：说明 AFTER_COMMIT 的执行时点并指出数据库已提交、内存事件尚未发送的崩溃窗口。
2. **H19-SP2 · CORE · 28**：提出在同一数据库事务内原子保存订单与可恢复待办，给出持久化状态和恢复入口。
3. **H19-SP3 · KEY · 21**：区分明确失败与响应超时结果未知，采用稳定业务键和接收方幂等，或先查对方结果。
4. **H19-SP4 · KEY · 15**：给出短事务确认发送结果以及有限退避、超限对账等恢复策略，说明确认需独立提交。
5. **H19-SP5 · PRINCIPLE · 10**：说明本地数据库事务覆盖订单和待办，跨系统通知无法借该事务得到端到端恰好一次。

### 技术依据

- [Spring Framework 6.2 事务事件](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/event.html) — Transaction-bound Events：监听器可绑定 AFTER_COMMIT；默认无活动事务时不触发。
- [Spring Framework 6.2 TransactionSynchronization API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html#afterCommit()) — afterCommit()：回调发生时原事务已提交，后续需要落库的操作应使用独立事务。
- [RFC 9110 HTTP 语义](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.2.2) — §9.2.2：解释响应中断后的重复请求边界，幂等性取决于方法语义或应用合同。

覆盖差异：同桶 H05 考数据库事务边界，H18 考事务传播。H25 只从 HTTP 调用方决定重试，本题要求把可恢复待办与业务状态一起提交。H27 基于项目现有报告状态机，无通知待办设计要求。

可接受表达／替代方案：

可以用轻量 outbox 表，或已有业务表中可查询的待办状态。远端支持查询时可先查结果，也可凭接收方稳定键幂等重发。

应识别的误解：

AFTER_COMMIT 能在进程重启后重放事件。回调异常可以回滚已提交订单。只要改用 REQUIRES_NEW 就能让数据库和外部 HTTP 原子提交。

## H20｜InnoDB 快照读与锁定读的交互

分类：`MYSQL`；难度：`HARD`；知识点：`isolation_snapshot_locking`；修订：1；来源：D1_NEW。

适用版本：MySQL 8.0 InnoDB，显式事务，主键等值查询，普通非锁定 SELECT，常规 FOR UPDATE，B 在 A 第二次读之前已提交。

### 面试问题

MySQL 8.0 有一条库存记录，主键为 1，库存为 10。A 开启事务并普通 SELECT 读到 10。B 将该行更新为 8 并提交。此时 A 先普通 SELECT，再 SELECT ... FOR UPDATE，随后把库存更新为 7 并再次普通 SELECT。没有其他并发写入时，A 在 REPEATABLE READ 和 READ COMMITTED 下各会读到什么？如果扣减前必须基于当前库存判断，应该怎样读写？

### 参考答案

在 RR 下，A 第一次普通 SELECT 建立快照。B 提交后，第二次普通 SELECT 沿用这个快照，还是 10。FOR UPDATE 是锁定读，这个时序下会锁住并读取当前记录 8。A 更新为 7 后，普通 SELECT 能读到本事务自己的写入，所以读到 7。在 RC 下，每次普通一致性读会建立新的快照，B 提交后的普通 SELECT 已经看到 8。随后锁定读读 8，更新后的普通 SELECT 读 7。题目特意让 B 先提交。如果 B 还持有冲突锁，常规 FOR UPDATE 可能等待。需要根据当前值写入时，我会在同一事务里先锁定再做判断，或者直接用带业务条件的原子 UPDATE，避免把早先的快照读误当成当前状态。

### 评分点（合计 100）

1. **H20-SP1 · CORE · 27**：在题设 RR 时序下给出普通读 10，并说明同一事务的后续普通一致性读沿用首个快照。
2. **H20-SP2 · KEY · 21**：说明 RC 每次普通一致性读取新快照，因 B 已提交而读到 8。
3. **H20-SP3 · CORE · 22**：两种隔离级别下锁定读在该时序均读到并锁定 8，能区分锁定读和快照读。
4. **H20-SP4 · KEY · 20**：两种隔离级别下 A 自己更新后均可普通读取 7，解释本事务写入对自身可见。
5. **H20-SP5 · ADVANCED · 10**：指出冲突写锁可能让锁定读等待，或给出对当前值做业务判断时的锁定读或条件更新方案。

### 技术依据

- [MySQL 8.0 InnoDB 一致性非锁定读](https://dev.mysql.com/doc/refman/8.0/en/innodb-consistent-read.html) — §17.7.2.3：说明 RR 和 RC 的快照时点、事务可见自身早先写入以及 DML 不沿用普通读快照。
- [MySQL 8.0 InnoDB 锁定读](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html) — §17.7.2.4：解释 FOR UPDATE 的锁定语义，包含冲突事务的等待情形。

覆盖差异：同桶 H06 考 Read View、Undo 与版本选择原理，H21 考错误后的回滚恢复。本题要求按时间线推导 RR/RC、锁定读及读己之写，能答内部字段但推导不出仍不能覆盖核心评分。

可接受表达／替代方案：

可用 A/B 双连接逐步演算，不要求背诵 Read View 字段。额外说明 START TRANSACTION WITH CONSISTENT SNAPSHOT 的条件时，应遵守题设默认的开启方式。

应识别的误解：

认为 RR 内每次读取都返回 10，或 FOR UPDATE 会刷新之后普通读使用的 RR 快照。

## H21｜InnoDB 死锁与锁等待超时的恢复

分类：`MYSQL`；难度：`HARD`；知识点：`deadlock_timeout_recovery`；修订：1；来源：D1_NEW。

适用版本：MySQL 8.0 InnoDB，innodb_deadlock_detect=ON，innodb_rollback_on_timeout=OFF，显式转账事务。讨论存储引擎默认回滚范围，应用事务策略需另行明确。

### 面试问题

两个 MySQL 8.0 InnoDB 转账事务按相反顺序锁账户，出现死锁。另一次是某条 UPDATE 单独等锁太久，在它之前的扣款语句已经执行成功。假设死锁检测开启，innodb_rollback_on_timeout 为默认关闭，遇到这两种报错分别怎样恢复和排查？

### 参考答案

死锁是两个事务形成循环等待。开启检测时，InnoDB 选择牺牲者并回滚它的整个事务，收到死锁错误的一方如要重试，需要从事务起点重跑。锁等待超时只说明等锁超过时限，未必有环。默认配置只回滚超时语句，前面的扣款仍可能留在未提交事务中。我会让整个业务事务回滚，然后在外层按有限次数、带退避地启动新事务重做，并复用转账业务键。如果是提交结果未知，要先核实结果或走已有的幂等合同，不能直接再扣一次。预防死锁可以让所有转账按账户 ID 固定顺序加锁，缩短事务持锁时间，并检查索引是否缩小锁定范围。排查死锁看 SHOW ENGINE INNODB STATUS 最近记录，正在等待时可以看 Performance Schema 的 data_lock_waits。

### 评分点（合计 100）

1. **H21-SP1 · CORE · 25**：说明开启检测时死锁牺牲者的整个 InnoDB 事务回滚，重试应开启新事务从头执行。
2. **H21-SP2 · CORE · 25**：说明默认锁等待超时只回滚失败语句，先前扣款可能仍留在未提交事务中，必须显式结束整笔业务事务以免部分提交。
3. **H21-SP3 · KEY · 20**：设计有上限的外层事务重试，保留稳定业务标识，并能处理未知提交结果或幂等去重。
4. **H21-SP4 · PRINCIPLE · 18**：通过统一加锁顺序打断循环，并给出缩短事务或减少不必要锁范围的措施。
5. **H21-SP5 · KEY · 12**：指出至少一种证据来源并能说明用途，例如最近死锁报告与当前阻塞者查询的区别。

### 技术依据

- [MySQL 8.0 InnoDB 错误处理](https://dev.mysql.com/doc/refman/8.0/en/innodb-error-handling.html) — §17.21.5：死锁回滚整个事务，锁等待超时默认回滚当前语句，选项可改变后者。
- [MySQL 8.0 处理死锁](https://dev.mysql.com/doc/refman/8.0/en/innodb-deadlocks-handling.html) — §17.7.5.3：建议固定操作顺序、缩短事务并查看最近死锁信息。
- [MySQL 8.0 Performance Schema data_lock_waits](https://dev.mysql.com/doc/refman/8.0/en/performance-schema-data-lock-waits-table.html) — data_lock_waits：提供请求锁和阻塞锁的关系，用于定位当前锁等待。

覆盖差异：同桶 H06 考一致性读可见性，H20 考两种隔离级别下的具体读值。本题围绕数据库错误的回滚范围、业务重试和锁诊断。

可接受表达／替代方案：

可以用 MySQL 错误码 1213 和 1205 辅助判断，或描述 Spring 将异常传播出事务边界再由外层重试。关键是准确解释数据库与应用事务边界，不强制背错误码。

应识别的误解：

锁等待超时等同死锁，且默认回滚整个事务。可以在原事务吞掉超时，然后提交前面执行成功的扣款。

## H22｜Cache-aside 的更新竞态与热点回源

分类：`REDIS`；难度：`HARD`；知识点：`cache_aside_hotkey`；修订：1；来源：D1_NEW。

适用版本：Redis Open Source 的基础 GET、SET 带 TTL、DEL 与单键条件操作，配合 MySQL cache-aside。题设两个故障场景独立。

### 面试问题

商品详情以 MySQL 为准，Redis 做 cache-aside。写请求更新数据库后删除缓存。并发读已经缓存未命中，并在更新前读到旧值，却在删除缓存后才回填。另外一个热门商品过期时大量请求同时查库。你会怎样分别处理旧值回填和热点回源？

### 参考答案

通常读先查缓存，未命中才查数据库并带 TTL 回填。写通常先提交数据库，再删缓存。如果提前删除，并发读可能在数据库提交前读到旧值并回填。不过这里的读请求拿着旧值，删除之后仍能把它填回去，因此这套顺序本身没有强一致保证。若业务容许短时旧值，可设合适 TTL，并对删除失败记录可重试的失效任务。如果这个竞态不能接受，回源前可读出每键版本，失效时在 Redis 内推进版本并删除缓存，回填时原子核对版本，拒绝已过期版本的结果。版本记录要能跨过删除，数据库提交到 Redis 失效之间仍有跨库窗口。要求关键读立即可靠时，直接读权威库更简单。热点过期时，可对同一键合并回源，获准回源者再检查一次缓存再查库，其他请求有限等待或降级。单实例可本地合并，多实例仍需考虑跨实例重复和回源并发上限。

### 评分点（合计 100）

1. **H22-SP1 · CORE · 24**：说明 cache-aside 读未命中回填和写提交后失效顺序，并指出数据库是权威来源。
2. **H22-SP2 · CORE · 26**：按题设时序解释旧读在删除后回填的竞态，说明更新库再删除缓存无法单独消除该竞态。
3. **H22-SP3 · KEY · 20**：按业务一致性要求给出可行选择，例如 TTL 与失效重试、跨失效的版本化回填保护，或关键读绕过缓存。
4. **H22-SP4 · KEY · 22**：为热点同键设计合并回源或等价限流，并说明持有回源资格后重查、等待上限或故障降级。
5. **H22-SP5 · ADVANCED · 8**：明确本地合并在多实例的作用范围，或给出回源并发上限与缓存失效故障下的保护。

### 技术依据

- [Redis 官方 cache-aside 指南](https://redis.io/docs/latest/develop/use-cases/cache-aside/) — Why the problem is hard；Cache-aside flow：介绍缓存未命中回源、TTL、显式删除和热点击穿。题中旧值回填是按给定并发时序推导的结果。
- [Redis 官方 EVAL 指南](https://redis.io/docs/latest/develop/programmability/eval-intro/) — Executing Lua in Redis：可在 Redis 侧原子执行条件检查与回填，原子范围不覆盖 MySQL。

覆盖差异：同桶 H07 考租约与安全解锁，H23 考 Redis 命令执行合同。M20 说明 TTL 与淘汰。本题重点是 MySQL 与 Redis 的并发竞态和回源压力，可用不涉及 Redis 分布式锁的做法。

可接受表达／替代方案：

可使用安全的单键代数或版本栅栏，或接受明确的短时旧值并以较短 TTL、可观测补偿失效为代价。单机按键 single-flight 与多机限流可以组合。

应识别的误解：

设置 TTL 或先写库再删缓存即可保证强一致。给各键过期时间加抖动一定能解决单个热点键的瞬时回源。

## H23｜Redis Pipeline、事务与 Lua 的边界

分类：`REDIS`；难度：`HARD`；知识点：`redis_batch_atomicity`；修订：1；来源：D1_NEW。

适用版本：Redis Open Source 7.x 单实例的标准命令语义，不涉及 Cluster 跨槽、复制或故障切换。

### 面试问题

假设用单个 Redis 实例实现接口配额。场景一是批量查询互不依赖的计数器，场景二是对单个计数器先判断剩余额度，再决定是否扣减。Pipeline、MULTI/EXEC 配合 WATCH，以及 Lua 分别如何选择？如果中间命令出错，之前的写入会怎样？

### 参考答案

独立批量读适合 Pipeline，客户端一次发送多条命令再收响应，减少网络往返，其他客户端仍可能在命令之间执行。单键先读后扣，可以 WATCH 目标键，再读额度，在 MULTI 后排入扣减并 EXEC。如果监听键被其他操作改动，EXEC 放弃本次事务，客户端有限重试。成功执行时队列中的命令不会和其他客户端命令交错。也可以用短 Lua 脚本把判断和扣减留在 Redis 服务端，中间没有其他命令插入，脚本太久会阻塞其他请求。MULTI 排队期遇到语法或参数数量这类错误，EXEC 会拒绝执行该队列。EXEC 内某条命令运行时出错，已经成功的命令不会回滚，后续仍可执行。Lua 运行时失败也不能自动撤销此前写入，应先做校验再做写入。

### 评分点（合计 100）

1. **H23-SP1 · CORE · 22**：为独立读选择 Pipeline，并说明其减少往返但没有事务或命令间隔离保证。
2. **H23-SP2 · CORE · 25**：给出 WATCH、读条件、MULTI 排队、EXEC 的乐观流程，并解释键发生变化导致放弃执行。
3. **H23-SP3 · KEY · 21**：说明短 Lua 脚本可原子完成单实例内条件检查与修改，且执行期间会占用服务端执行时间。
4. **H23-SP4 · PRINCIPLE · 22**：区分事务排队错误与 EXEC 执行期错误，能说清已经成功的写入不会因为另一命令运行时出错被回滚。
5. **H23-SP5 · ADVANCED · 10**：指出 Lua 运行时异常也不会撤销此前写入，并提出先校验后写入或查看逐条结果等应对。

### 技术依据

- [Redis 官方 Pipeline 文档](https://redis.io/docs/latest/develop/using-commands/pipelining/) — Redis pipelining；Pipelining vs Scripting：批量发命令降低 RTT，服务端原子读改写宜用脚本。
- [Redis 官方事务文档](https://redis.io/docs/latest/develop/using-commands/transactions/) — Errors inside a transaction；Optimistic locking using check-and-set：说明 EXEC 的隔离、排队与运行时错误、WATCH 冲突取消。
- [Redis 官方 Lua 脚本文档](https://redis.io/docs/latest/develop/programmability/eval-intro/) — Executing Lua in Redis；Interacting with Redis from a script：脚本独占执行，区分 redis.call 与 redis.pcall 对命令错误的处理。

覆盖差异：同桶 H07 考分布式锁所有权，H22 考跨库缓存和热点回源。本题考一个 Redis 实例的批处理、读改写原子性和错误边界。

可接受表达／替代方案：

仅用 Lua 完成条件扣减是可行设计，但须能解释 WATCH 方案与 Pipeline 为什么不同。用同时返回多值的原生命令可作为独立查询的补充。

应识别的误解：

Pipeline 自带原子性。Redis 事务与 Lua 像关系数据库一样自动回滚中间写入。

## H24｜TLS 1.3 的服务器认证与加密

分类：`NETWORK`；难度：`HARD`；知识点：`tls_authentication_basics`；修订：1；来源：D1_NEW。

适用版本：RFC 8446 TLS 1.3，首次完整的证书认证握手，使用临时 (EC)DHE。题设排除 PSK 恢复、0-RTT 与客户端证书认证。

### 面试问题

客户端首次访问 https://api.example.com，使用 TLS 1.3 的常规证书握手。客户端怎样确认对面就是这个域名的服务？为什么网络旁观者不能从握手消息直接算出会话密钥？握手成功后，服务是否就知道当前业务用户是谁？

### 参考答案

对服务器身份的校验包括根据本地信任根验证证书链及有效期等条件，还要用自己访问的 api.example.com 去匹配证书 SAN 中的 DNS 身份。证书可以被复制，服务器还要用证书对应的私钥给握手记录签名，由客户端验证 CertificateVerify。双方在握手中交换临时的密钥份额，用各自持有的私有部分与收到的公开份额计算共享秘密，再派生流量密钥，传输的公开份额不会直接给出秘密。Finished 验证握手记录与派生密钥，后续 HTTP 数据使用对称加密并校验完整性。普通单向 TLS 在这里认证的是服务器。用户是谁、是否能读取某条业务记录，还需要登录凭证和服务端授权。

### 评分点（合计 100）

1. **H24-SP1 · CORE · 28**：区分证书链信任与目标域名匹配，能说明有受信证书仍须检查请求主机名。
2. **H24-SP2 · KEY · 22**：说明 CertificateVerify 通过签名证明服务端掌握证书对应的私钥，并绑定本次握手。
3. **H24-SP3 · CORE · 25**：解释临时 (EC)DHE 使用公开份额与本地私有值计算共享秘密，再派生对称流量密钥。
4. **H24-SP4 · PRINCIPLE · 15**：说明 Finished 或等价握手完整性检查，以及后续记录使用带认证的对称加密。
5. **H24-SP5 · KEY · 10**：区分普通服务器认证与应用侧用户识别、访问授权。

### 技术依据

- [RFC 8446：TLS 1.3](https://www.rfc-editor.org/rfc/rfc8446.html#section-2) — §2、§4.4、§5.2、§7.1：证书签名和 Finished 绑定握手，(EC)DHE 与密钥派生提供流量密钥，记录层使用带认证的加密。
- [RFC 9525：TLS 服务身份](https://www.rfc-editor.org/rfc/rfc9525.html#section-6) — §6：对照客户端参考标识验证证书中的服务身份，DNS 由 SAN dNSName 表示。
- [RFC 5280：证书路径验证](https://www.rfc-editor.org/rfc/rfc5280.html#section-6.1) — §6.1：定义以信任锚为输入的证书路径验证及有效期等约束。

覆盖差异：同桶 H08 考 TCP 建连与关闭，H25 考 HTTP 超时重试。E14 考 HTTP 方法和状态码。本题围绕服务器身份、握手签名和密钥协商。

可接受表达／替代方案：

能用公钥份额、私钥与共享秘密解释即可，不要求背握手报文顺序或 HKDF 公式。业务用户凭证可以举 Session Cookie、访问令牌等例子。

应识别的误解：

只要证书由可信 CA 签发就无需检查域名。TLS 1.3 完整握手用证书公钥直接加密 HTTP 正文。服务端认证会替应用完成用户授权。

## H25｜HTTP 超时后的重试与业务幂等

分类：`NETWORK`；难度：`HARD`；知识点：`http_timeout_idempotency`；修订：1；来源：D1_NEW。

适用版本：RFC 9110 HTTP 语义。连接超时例子要求能够确认新连接尚未建立且这次请求未发送。POST 幂等键为题设应用合同。

### 面试问题

客户端向订单服务发请求，有时建立连接时超时，有时已经发出请求却在读响应时超时。对查询 GET、按 URI 替换资源的 PUT、创建订单的 POST，你会怎样判断能否自动重试？如果 POST 允许重试，需要什么服务端合同？

### 参考答案

明确在新连接建立前失败、且没有发送该请求时，服务端没有从这次尝试收到请求，确认同一业务操作也没有其他结果未知的尝试时，即使是 POST 也可以补发。读响应超时就不同，服务端可能没处理，也可能已经提交，只是客户端没拿到响应。GET 通常可以对短暂网络失败有限重试。语义正确的 PUT 重复发送同一请求，其预期资源效果等同一次，响应内容或状态码可以变化。创建订单的 POST 默认不能仅凭超时自动重放。若接口支持业务幂等，应在重试中复用同一个幂等键和相同有效载荷。服务端按租户或用户与操作约定键的作用范围，并原子登记键和业务结果，或以唯一业务标识实现等价保护。同键同内容可重放原结果或返回已有订单，同键不同内容应冲突。实际重试还要受总时限、退避和次数约束，权限或参数错误应直接处理。

### 评分点（合计 100）

1. **H25-SP1 · CORE · 26**：区分明确的请求尚未发送与等待响应超时，后者不能据此判断服务端是否提交。
2. **H25-SP2 · KEY · 22**：根据 GET、PUT 的幂等语义判断可重试条件，说明幂等指预期效果而非每次响应相同。
3. **H25-SP3 · CORE · 25**：指出创建型 POST 自动重放有重复业务风险，提出同一操作复用稳定幂等键和相同载荷。
4. **H25-SP4 · KEY · 17**：说明服务端原子去重与已有结果重放，并能拒绝同键异载荷或给出等价唯一业务标识方案。
5. **H25-SP5 · ADVANCED · 10**：使用总时限、有限次数和退避约束临时失败重试，不机械重试明显的非临时错误。

### 技术依据

- [RFC 9110：方法安全性与幂等性](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.2) — §9.2.1、§9.2.2、§9.3.3、§9.3.4：说明安全、幂等及自动重试非幂等请求的条件，区分 POST 与 PUT 语义。
- [RFC 9110：Retry-After](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3) — §10.2.3：支持从响应中读取服务建议等待时间，次数和总时限仍由客户端策略约束。
- [Java 21 HttpConnectTimeoutException](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpConnectTimeoutException.html) — 类说明：给出连接建立超时的客户端示例，不能用它推断所有库的异常分类。

覆盖差异：同桶 H08 考 TCP 状态，H24 考 TLS 认证。E14 只辨认 HTTP 方法和状态，本题聚焦结果不确定时能否重放及服务端合同。H26 考本项目真实归属校验和评估提交权。

可接受表达／替代方案：

服务端若提供按业务键查询的 API，可先确认结果。由客户端指定稳定资源 URI 且服务端遵守替换语义时，也可使用 PUT 表达创建或更新。

应识别的误解：

读超时必然没有提交。PUT 每次响应必须一样才算幂等。POST 随请求重试生成新键也能去重。

## H26｜面试答案提交的归属、重放与并发

分类：`PROJECT_SCENARIO`；难度：`HARD`；知识点：`interview_idempotency_ownership`；修订：1；来源：D1_NEW。

适用版本：当前项目 HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c。按真实提交与评价调用链作答，场景只用于口述推演。

### 面试问题

在这个 AI 面试系统中，用户 A 提交答案时超时，用原 requestId 重试。同时用户 B 知道 A 的 sessionId 和 interviewQuestionId。A 的两次提交也可能并发到达，系统如何阻止越权与重复写入？同一 requestId 换正文、已完成评价重放和评价仍在运行时，分别怎么处理？

### 参考答案

控制器使用认证主体的用户 ID。过滤器校验令牌后会查询当前启用用户，提交服务再验证会话属于此用户、题目属于此会话，之后才按 requestId 查已有答案。已存在时还要精确匹配题目和正文。同键换正文或换题返回冲突。同键同正文且已评价时，服务用持久化结果重建响应，即使面试已推进也能重放。新提交要处在会话当前等待作答的题目，数据库对 requestId 和每题的答案都有唯一约束，短事务一起写答案和更新题目。并发唯一键冲突后重读赢家，并按相同规则判断重放或冲突。评价再由条件更新抢占。新鲜的 EVALUATING 会返回冲突，过期或失败可按状态恢复。提交评价、推进流程以及失败更新都受本次评价 attemptId 约束，旧执行者不能覆盖新执行者的结果。

### 评分点（合计 100）

1. **H26-SP1 · CORE · 25**：从认证主体获取当前用户，依次校验会话归属及题目归属，且在按 requestId 重放前完成。
2. **H26-SP2 · CORE · 23**：说明重放必须匹配同一题目和完全相同的正文，同键换题或换正文返回冲突。
3. **H26-SP3 · KEY · 22**：指出数据库的 requestId 和每题答案唯一约束、短事务内答案和题目更新，并说明竞争后重读判定。
4. **H26-SP4 · KEY · 15**：说明已 EVALUATED 重建持久化响应且不要求题目仍是当前题，新鲜 EVALUATING 则返回冲突。
5. **H26-SP5 · PRINCIPLE · 15**：说明评价资格通过条件更新获得，并由本次 attemptId 约束持久化或失败更新，明确过期恢复仍有旧执行者。

### 技术依据

- [项目 JwtAuthenticationFilter](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/security/filter/JwtAuthenticationFilter.java) — HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · doFilterInternal、authenticate：校验 JWT、加载当前启用用户并建立认证主体。
- [项目 InterviewController](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/controller/InterviewController.java) — HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · submitAnswer：从 AuthenticationPrincipal 转发用户 ID。
- [项目 InterviewService](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewService.java) — HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · submitAnswer、loadOwnedSession、loadAndValidateQuestion、handleExistingAnswer、recoverFromDuplicateKey：归属先行；正文精确匹配；重放和竞态恢复。
- [项目 InterviewWorkflowService](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java) — HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · evaluateAnswer、claimOrRecoverEvaluation、recoverStaleEvaluating：区分评价抢占、已完成、活跃与过期恢复。
- [项目 InterviewWorkflowTransactionService](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java) — HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · submitAnswer、persistOwnedEvaluation、markEvaluationFailed：短事务保证答案和题目更新一致，attemptId 限制评价持久化和失败更新。
- [项目隔离数据库基线](/Users/kun/Developer/projects/ai-interview-system/docs/environment/ISOLATED_SCHEMAS.sql) — HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · uk_interview_answer_question、uk_interview_answer_request：每题答案和 request_id 分别唯一。

覆盖差异：同桶 H09 是假设库存并发扣减，H27 关注面试完结后的报告恢复。H25 属于通用 HTTP 重试。本题结合本项目授权顺序、精确载荷、答案唯一键与评价尝试令牌。

可接受表达／替代方案：

用归属、稳定幂等键、载荷一致性、数据库唯一约束、单胜者和尝试令牌表达即可，不要求背方法名、状态枚举或 SQL。

应识别的误解：

有 JWT 就可以访问任意 sessionId。按 requestId 查到记录即可返回。过期接管能停止已经在运行的旧任务。

## H27｜报告生成的过期接管与最终评价一致性

分类：`PROJECT_SCENARIO`；难度：`HARD`；知识点：`report_recovery_final_consistency`；修订：1；来源：D1_NEW。

适用版本：当前项目 HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c。区分报告会话版本和答案评价 attemptId。

### 面试问题

在这个 AI 面试系统中，会话已完成，但报告一直显示 GENERATING。后来用户重新发起生成，旧请求却在接管之后恢复执行。系统如何判断能否重试、怎样阻止旧请求覆盖结果？报告和用户弱项应该取哪些评价，怎样避免重复累计？

### 参考答案

报告生成先检查会话归属和 COMPLETED，READY 直接读已有报告。GENERATING 未过期时返回冲突，超过阈值后，重新发起的生成请求才能按状态、时间和会话版本做条件更新。抢占成功会递增版本，拿到的本次版本用于 READY 或 FAILED 更新，旧执行者不能重新读取新版本冒用资格。汇总前应核对每道计划主问题恰有一个对应最终答案的 FINAL。有追问取追问产生的综合 FINAL，无追问取主问题的 FINAL，INITIAL 不进入报告或弱项。模型生成摘要在短写事务外。结果完成时，在一个事务里写唯一报告、更新弱项并用本次版本推进 READY，条件更新失败就回滚整次写入。若接管时已有报告，校验评价后补齐状态即可，不重新生成或累计弱项。旧请求可能仍有外部调用，数据库事务和版本限制的是它的提交权。

### 评分点（合计 100）

1. **H27-SP1 · CORE · 23**：说明每道计划主问题恰有一个关联正确的 FINAL，有追问使用追问综合评价且排除 INITIAL。
2. **H27-SP2 · KEY · 18**：说明先检查会话归属与完成状态，READY 复用、活跃 GENERATING 冲突，过期后由生成请求触发条件更新抢占。
3. **H27-SP3 · CORE · 25**：解释接管递增会话版本，完成及失败只接受本次版本，旧请求不得改用新版本。
4. **H27-SP4 · KEY · 24**：说明报告唯一、弱项更新和 READY 处于同一短事务，版本检查失败会回滚，外部摘要调用不纳入该事务。
5. **H27-SP5 · KEY · 10**：说明非 READY 下若找到已有持久化报告，验证评价后对齐状态，不重复生成或更新弱项。

### 技术依据

- [项目 InterviewReportService](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java) — HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · generateReport、validateEvaluations、validateEvaluationAssociations、existingReportOrContinue、recoverReportFailure：请求触发占用、验证 FINAL 归属、已有报告对账、按捕获版本处理失败。
- [项目 InterviewSessionMapper](/Users/kun/Developer/projects/ai-interview-system/src/main/resources/mapper/InterviewSessionMapper.xml) — HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · claimReportGeneration、reclaimStaleGenerating、markReportReady、markReportFailed：过期接管检查时间与状态，更新时匹配期望版本。
- [项目 InterviewReportTransactionService](/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java) — HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · completeReportGeneration：同一事务插入报告、累计弱项、条件更新 READY。
- [项目 AnswerEvaluationMapper](/Users/kun/Developer/projects/ai-interview-system/src/main/resources/mapper/AnswerEvaluationMapper.xml) — HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · listFinalEffectiveEvaluationsBySessionId：获取本场用于报告的有效最终评价，业务层继续验证完整性与归属。
- [项目隔离数据库基线](/Users/kun/Developer/projects/ai-interview-system/docs/environment/ISOLATED_SCHEMAS.sql) — HEAD 0c6f92afe331954f7cea37d33db688fe781cbf2c · uk_interview_report_session：数据库限制每场一个报告。

覆盖差异：同桶 H09 是假设库存扣减，H26 是当前答案提交与评价。H19 是通用订单外部通知。本题基于项目已实现的报告版本占用、FINAL 关联校验与弱项累计原子性。

可接受表达／替代方案：

可用失败时序说明旧任务提交和失败回写都被挡住，可用数据库条件更新、乐观版本或持久化提交资格解释。只要求每主问题一个有效 FINAL，不要求背具体阈值。

应识别的误解：

只按 FINAL 行数就能保证评价覆盖。接管后旧请求可以读取新版本再提交。报告已经存在时应再次累计弱项。
