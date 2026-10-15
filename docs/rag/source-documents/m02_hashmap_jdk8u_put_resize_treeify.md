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

扩容关注的是整张表的容量与映射数量，而树化关注的是某一个桶内的冲突数量，两者是不同条件。JDK 8u 的 `HashMap` 默认 load factor 为 `0.75`；构造方法也允许调用者给出其他正数负载因子。threshold 是触发整体扩容的数量边界，通常由容量与负载因子共同确定。较低负载因子会让扩容更早发生并占用较大的桶表；较高负载因子允许更多映射共享当前容量。默认值 0.75 是默认配置，不是所有实例都不能改变的固定值。

## 3. Important boundary

OpenJDK JDK 8u 将 `TREEIFY_THRESHOLD` 定义为 8。源码注释描述的是：向一个已经至少有 8 个节点的桶添加元素时，进入树化判断；因此不能把它简化成“第 8 个节点一到就一定立即变树”。`putVal` 在普通链表中新插入节点后才按 bin count 触发 `treeifyBin`，而 `treeifyBin` 还会检查表容量。若 table 长度小于 `MIN_TREEIFY_CAPACITY = 64`，实现选择先 `resize`；达到该容量边界后，才会把桶链表转换为树桶。已有 key 的 value 更新不是新增节点，也不会因更新而触发此插入路径。

树化条件是 JDK 8u 的实现行为，不是 `Map` 接口对所有实现的承诺。Java SE 8 `HashMap` API 说明容量和 load factor 与性能有关，并给出默认负载因子 0.75；`HashMap` 构造方法允许指定负载因子。面试中回答树化问题时，应同时说出桶节点数触发点与最小 table 容量边界，不能只背“阈值 8”。这些常量和 `putVal` 的具体顺序可能随 JDK 实现版本变化，不应无条件外推到其他版本或其他 Map 类型。

## 4. Common misconceptions

“桶里有 8 个节点就必然立刻成为红黑树”忽略了触发动作发生在向已有节点的桶继续插入时，也忽略 `MIN_TREEIFY_CAPACITY`。容量不足 64 时，JDK 8u 先扩容而不是直接树化。另一个误区是把 0.75 当成不可调整的常量；它只是默认 load factor，构造时可指定其他正值。相同 key 再次 `put` 的语义是替换映射的 value，而不是新建第二个相同键节点。

## Official references

- Primary: [OpenJDK JDK 8u `java.util.HashMap`](https://github.com/openjdk/jdk8u/blob/master/jdk/src/share/classes/java/util/HashMap.java) — `putVal`, `resize`, `treeifyBin` 与相关常量。
- Secondary: [Oracle Java SE 8 `HashMap` API](https://docs.oracle.com/javase/8/docs/api/java/util/HashMap.html) — 容量、load factor、默认值及 API 性能说明。
