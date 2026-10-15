# HotSpot Java 21 G1：Region 与暂停目标

> Title: HotSpot Java 21 G1：Region 与暂停目标
> Bank ID: H03
> Knowledge category: JVM
> Knowledge point: g1_gc
> Difficulty: HARD
> Target question: G1 垃圾收集器的核心设计思路是什么？Region、Young GC 和 Mixed GC 如何帮助它控制停顿时间？
> Version scope: Oracle JDK 21 / HotSpot G1；暂停时间是目标，不是实时上限。
> Primary official source: [G1]
> Secondary official sources: 无（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

G1 将 Java 堆划分为大量等大小 Region，Region 在某时刻可以为空，或承担 Eden、Survivor、Old 等角色。年轻代与老年代是由这些 Region 组成的逻辑集合，不要求各代都占一块固定连续空间。Region 本身是连续的虚拟内存范围，不能把“不要求代连续”误写成“单个 Region 内存也不连续”。[G1]

分区使 G1 可以按一组区域逐步回收；它结合并行回收与部分并发工作，尝试在吞吐量和暂停时间之间取得平衡。该描述限定 HotSpot Java 21，不概括所有 JVM 的 GC。

## 2. Main mechanism

Collection Set 是本次需要回收空间的源 Region 集合。G1 根据存活数据、区域关联和历史成本估计挑选回收收益较高、成本较合适的候选区域，并按可配置的暂停目标控制一次处理的工作量；不是每次都处理整个老年代，也不是仅按垃圾比例孤立地选择。[G1]

Young GC 主要处理年轻代 Region，将存活对象复制到 Survivor 或 Old Region；晋升或转移伴随引用调整，原区域空间可供后续使用。对象年龄等因素影响目的区域，“Young GC”不表示所有存活对象都直接进入 Old。特殊大对象有不同处理，不能把普通对象复制路径泛化为全部对象。[G1]

```text
Young-only：年轻代回收与存活对象转移
Concurrent Start → 并发标记 → Remark/Cleanup
Mixed：年轻代 + 选定老年代 Region
```


## 3. Important boundary

并发标记分析老年代区域的可达存活对象，为后续老年代回收选择提供依据；应用在标记的主要并发部分仍可执行。标记过程并非全程无停顿，Concurrent Start、Remark、Cleanup 等阶段仍有暂停。[G1]

Mixed GC 同时处理年轻代和一部分候选 Old Region，复制相应存活对象并逐步回收空间。它不是 Full GC，也不是任意一次年轻代回收都称为 Mixed。Remembered Set 记录集合外指向集合内的引用位置，帮助处理跨区域引用；它不是另一份对象存活清单。[G1]

## 4. Common misconceptions

“设置暂停目标后绝不会超时”把模型目标当成硬保证：G1 不是实时收集器，估计与实际工作量可能不同。“并发 GC 完全没有 STW”则忽略回收复制、Remark 等停顿。应区分并发进行的标记与需要停顿的回收步骤。[G1]

理解 G1 时，把 Region 的分工、Collection Set 的选择、Young 的复制以及 Mixed 的部分老年代回收串起来；暂停控制依赖增量工作与成本模型，而不是简单一句“垃圾最多的 Region 先回收”。本篇只解释机制，没有运行 GC 探针、压测或修改 JVM 参数。

## 5. Official references

- [G1] Oracle JDK 21 GC Tuning Guide，G1：Heap Layout / GC Cycle / Collection Set / Space-Reclamation

[G1]: https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html
