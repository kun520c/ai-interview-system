# H42-01 人工审查材料

来源：R02 已保存证据。整理日期：2026-10-14（Asia/Shanghai）。请先记录独立判断，再查看末尾折叠对照。前半部分仅提供原题、完整候选切片和待核对引用。

## 原始问题

版本条件：Java SE 21。

~~~text
Java 21中，Parent<T>声明accept(T)，Child extends Parent<Integer>覆盖accept(Integer)。javap还能看到Child中标记为ACC_BRIDGE的accept(Object)。为什么编译器生成这个方法，它如何在擦除后的调用中维持覆盖与多态，而不是一个独立的业务重载？
~~~

原题：[R02 frozen-questions.json](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p4-2/run-r02-20261014/frozen-questions.json)，questions 中 questionId=H42-01。

## Rerank Top2 原文

以下逐字展开保存的 MySQL 水合 knowledge.content，保持原换行，未从其他切片或外部链接补入正文。Target question、Authoring status 等为文档自带文字。

### Top1：Java 泛型擦除、桥接与运行时边界

documentId=590，documentVersion=1，chunkId=546，chunkIndex=1；vectorId=cbeb0d56-683a-41e7-b6c9-b0b9ec703597。

证据：[MySQL 水合快照](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p4-2/run-r02-20261014/H42-01/MILVUS_SEARCH/hydrated.json:51)，JSON 指针 /1/knowledge/content。
正文 UTF-8 SHA-256：e970fb9f14f7fcd30b4efc7fa996812ad18fba9adc15079a52401914dd0ffa1a。

~~~text
# Java 泛型擦除、桥接与运行时边界

> Title: Java 泛型擦除、桥接与运行时边界
> Bank ID: H01
> Knowledge category: JAVA_BASIC
> Knowledge point: generic_type_erasure
> Difficulty: HARD
> Target question: Java 的泛型擦除是什么？它会带来哪些限制？为什么很多泛型类型信息在运行时不存在？
> Version scope: 规范以 JLS/JVMS SE 21 为准；Oracle JDK 8 教程仅作擦除与 bridge 示意，不决定 Java 21 限制。
> Primary official source: [JLS4]
> Secondary official sources: ERASURE, JLS8, BRIDGE, JVMS4, JLS15（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

Java 泛型主要在编译期检查类型关系。擦除将类型变量替换为其最左侧 bound 的擦除；未写显式上界时通常为 Object。参数化类型擦除为对应原始类型。例如 `T extends Number` 的擦除依据 Number，而不是一律替换成 Object。[JLS4][ERASURE]

同一个泛型类的不同类型实参通常不生成独立运行时 Class：例如 `ArrayList<String>` 与 `ArrayList<Integer>` 的实例共享 ArrayList 的 Class。不同具体实现类不能据此混同。[JLS8][JLS4]

## 2. Main mechanism

编译器可在擦除后的访问位置插入类型转换，保留源代码需要的类型检查；为维持覆盖方法的多态关系，还可能生成 bridge method。例如父类方法接受 T，子类以 Integer 覆盖时，擦除后的参数类型可能不同，桥接方法可接受擦除类型、转换后委派给子类方法。class 文件的 ACC_BRIDGE 标记识别这类编译器桥接方法。[ERASURE][BRIDGE][JVMS4]

## 3. Important boundary

典型限制包括：不能直接 `new T()`；不能直接创建 `new List<String>[n]` 这种元素类型不可实化的数组；两个方法不能仅靠擦除后相同的泛型参数签名重载，例如分别接受 `List<String>` 与 `List<Integer>` 的同名方法。[JLS15][JLS8][JLS4]
~~~

### Top2：Java 泛型擦除、桥接与运行时边界

documentId=590，documentVersion=1，chunkId=548，chunkIndex=3；vectorId=504ae501-0222-4f8c-abce-7d3fec11d284。

证据：[MySQL 水合快照](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p4-2/run-r02-20261014/H42-01/MILVUS_SEARCH/hydrated.json:20)，JSON 指针 /0/knowledge/content。
正文 UTF-8 SHA-256：88854bbf3028d3fd0cb547d7cbea8f761e4ef84a2e4d616d9dc2ff2524710f2c。

~~~text
JDK 8 Tutorial，Bridge Methods（机制示意）
- [JVMS4] JVMS SE 21，§4.6 ACC_BRIDGE、§4.7.9 Signature
- [JLS15] JLS 21 Expressions，§15.9、§15.10.1、§15.20.2

[JLS4]: https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html
[ERASURE]: https://docs.oracle.com/javase/tutorial/java/generics/erasure.html
[JLS8]: https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html
[BRIDGE]: https://docs.oracle.com/javase/tutorial/java/generics/bridgeMethods.html
[JVMS4]: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html
[JLS15]: https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html
~~~

## 待核对的三个引用

Q1–Q3 均对应上述 Top1。此处仅保留引用文字。

Q1：

~~~text
Java 泛型主要在编译期检查类型关系。擦除将类型变量替换为其最左侧 bound 的擦除；未写显式上界时通常为 Object。参数化类型擦除为对应原始类型。
~~~

Q2：

~~~text
为维持覆盖方法的多态关系，还可能生成 bridge method。例如父类方法接受 T，子类以 Integer 覆盖时，擦除后的参数类型可能不同，桥接方法可接受擦除类型、转换后委派给子类方法。class 文件的 ACC_BRIDGE 标记识别这类编译器桥接方法。
~~~

Q3：

~~~text
桥接方法可接受擦除类型、转换后委派给子类方法。
~~~

人工记录（由你填写）：Top1 的支持范围及条件：________；Top2 的支持范围及条件：________；Q1/Q2/Q3 各自能支持的最小命题及不足：________。

## 引用存在性核验

以水合 JSON 解码后的 Top1 knowledge.content 为坐标源，使用逐字、区分大小写的连续子串匹配重新核验；没有拼接、改写或空白归一化。UTF-16 区间采用从 0 开始、含起点且不含终点的 [start,end)；正文行、列从 1 开始，列按 UTF-16 计数。这些不是 JSON 文件行号或本文排版行号。

| 引用 | UTF-16 区间 | UTF-16 长度 | 正文位置（列含首尾） | 连续匹配 | 出现次数 | 与历史偏移一致 |
| --- | --- | ---: | --- | --- | ---: | --- |
| Q1 | [482,560) | 78 | 第 16 行，第 1–78 列 | 是 | 1 | 是 |
| Q2 | [811,942) | 131 | 第 22 行，第 34–164 列 | 是 | 1 | 是 |
| Q3 | [884,907) | 23 | 第 22 行，第 107–129 列 | 是 | 1 | 是 |

三个引用均连续存在。Q3 完全包含于 Q2，两者不是两段互不重叠的证据。本核验仅确认文字存在与位置，不给出引用蕴含、候选质量、版本适用性或整题充分性的人工结论。

必要上游证据：[Milvus 原始 Top5](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p4-2/run-r02-20261014/H42-01/MILVUS_SEARCH/response.json)；[MySQL 五条水合原文](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p4-2/run-r02-20261014/H42-01/MILVUS_SEARCH/hydrated.json)；[Rerank 原始排名](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p4-2/run-r02-20261014/H42-01/RERANK/response.json)；[R02 SHA 索引](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p4-2/run-r02-20261014/artifacts.json)。Rerank 返回 index=1、0 为前两名，对应水合数组的第 2、第 1 项。

---

<details>
<summary>独立对照：记录人工判断后再展开（含模型标签、Java 决策与未来策略）</summary>

### DeepSeek 原始判断

以下标签、理由和解释均原样摘自 [DeepSeek 原始响应](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p4-2/run-r02-20261014/H42-01/SEMANTIC_GATE/response.json) 的 choices[0].message.content，属于模型判断。此处没有替你填写人工标签。

| 候选 | verdict | versionAssessment | reason 原文 |
| --- | --- | --- | --- |
| Top1 | CORE_SUPPORT | MATCH | 正文直接解释了擦除导致子类覆盖方法参数类型不同，编译器为此生成 ACC_BRIDGE 桥接方法，桥接方法接受擦除类型并转换委派给子类方法。未直接对比“独立业务重载”或明确说明 javap 中 ACC_BRIDGE 与重载的区别，但主要机制 r1、r2 已有直接支持。 |
| Top2 | NO_SUPPORT | UNCERTAIN | 候选正文仅包含版本范围字样、参考文献标题和链接列表，虽有 JDK 8 Tutorial 与 JVMS SE 21 等元信息，但没有可引用的连续机制正文来解释桥接方法的生成原因或调用委派机制，也未直接讨论题设的覆盖与多态。 |

| 引用 | requirementId | relation | explanation 原文 |
| --- | --- | --- | --- |
| Q1 | r1 | DIRECT_MECHANISM | 直接说明擦除机制：类型变量替换为 bound 擦除、无上界时为 Object，这是桥接方法生成的原因前提。 |
| Q2 | r1 | DIRECT_MECHANISM | 直接支持生成桥接方法的原因（擦除后参数类型不同、维持覆盖多态）以及 ACC_BRIDGE 标记含义。 |
| Q3 | r2 | DIRECT_MECHANISM | 直接说明桥接方法接受擦除类型并转换委派到子类方法，即在擦除后调用中如何维持覆盖与多态。 |

Top2 的 supports 原样为空数组 []；其 UNCERTAIN 出现在 versionAssessment，verdict 本身是 NO_SUPPORT。

### Java 的候选判断与最终决策

| 层次 | 保存结果 |
| --- | --- |
| PrimarySemanticGate 候选校验 | admittedCandidateIds=[cbeb0d56-683a-41e7-b6c9-b0b9ec703597]；status=ADMITTED_WITH_UNCERTAIN_CANDIDATE |
| R02 执行器最终结果 | H42-01 status=FAILED_STOP；failureCode=GATE_UNCERTAIN_STOP；safeFallback=EMPTY_EVIDENCE_BATCH_STOP_NO_RETRY；finalAdmittedCandidateIds=[] |
| 批次与人工审查状态 | executionStatus=STOPPED；qualityStatus=PENDING_INDEPENDENT_HUMAN_REVIEW |

原始路径：[H42-01 result.json](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p4-2/run-r02-20261014/H42-01/result.json:31)；[R02 summary.json](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/rag-p4-2/run-r02-20261014/summary.json:4)。

### 为什么 Top2 的不确定性使 Top1 最终也被清空

冻结的 [PrimarySemanticGate.java](E:/learning/IDEA_PROJECTJ/ai-interview-system/tools/rag-p4-2/java/com/kun/aiinterview/knowledge/admission/PrimarySemanticGate.java:43) 对所有输入候选累计 uncertain：verdict 或 versionAssessment 为 UNCERTAIN 都会置位，未以该候选是否准入为条件。Top1 经校验进入候选准入列表；Top2 虽因 NO_SUPPORT 未进入列表，其版本不确定性仍使全局状态成为 ADMITTED_WITH_UNCERTAIN_CANDIDATE（第 79 行）。

冻结的 [R02 执行器](E:/learning/IDEA_PROJECTJ/ai-interview-system/tools/rag-p4-2-r02/execution/P42R02ExecuteOnce.java:325) 随后要求状态不含 UNCERTAIN，否则抛出 GATE_UNCERTAIN_STOP。第 330–331 行异常路径记录 FAILED_STOP、将 finalAdmittedCandidateIds 设为空数组，再向批次传播停止。因此 Gate 的中间准入记录仍保留 Top1，最终证据列表为空；触发清空的是执行器的整题／批次安全门禁，并非重新判定 Top1 没有支持。

### 未来业务策略能否区分两类不确定性

业务上可以讨论按候选归属区分，但须另行预先定义和验证，本轮不改变规则。对于准备作为证据保留的候选，支持关系或必要版本条件未确认，应拒绝该候选；当前合同已要求 CORE_SUPPORT 的非空版本约束必须 MATCH。对于已因 NO_SUPPORT 拒绝的候选，单独的版本不确定性可考虑只记录诊断、继续排除该候选，在其余证据独立通过校验时保留它们。

区分策略仍须保留整份响应结构、身份、引用真实性等校验；无法隔离到单个候选的失败不能被“已拒绝候选”豁免。它可能减少有效候选被连带清空，也可能掩盖模型误拒，是否合理需独立人工标签验证。本次引用存在性核验不能证明 Top1 的质量，不能据此给出质量 PASS 或将 R02 的 STOPPED 改判。

本轮仅新增本材料；R02 的 29 个索引所列文件 SHA 与封存匹配，索引 SHA 与 artifacts.sha256 匹配；Top2 正文与已保存 Gate 输入、正文 SHA 一致。未运行执行器、模型测试或 Maven 回归；无新模型调用、MySQL/Milvus 访问、规则或配置修改、R03、Git commit/push。三个引用核验结果为 3/3 连续存在，失败 0；人工质量判断仍留空。

</details>
