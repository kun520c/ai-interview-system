# JVM SE 21 运行时数据区与 HotSpot 边界

> Title: JVM SE 21 运行时数据区与 HotSpot 边界
> Bank ID: M03
> Knowledge category: JVM
> Knowledge point: runtime_data_areas
> Difficulty: MEDIUM
> Target question: JVM 运行时数据区主要有哪些？哪些区域是线程私有的，哪些是线程共享的？对象、局部变量、类元数据等通常分别位于哪里？
> Version scope: JVMS SE 21 运行时数据区；HotSpot 类元数据部分依据 Oracle JDK 21。
> Primary official source: [JVMS2]
> Secondary official sources: META（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

JVM 规范描述 Heap、Method Area、Java Virtual Machine Stack、PC Register、Runtime Constant Pool，以及实现支持时的 Native Method Stack。它规定逻辑职责和共享关系，并不规定某个实现必须采用哪种操作系统内存布局。[JVMS2]

| 区域 | 共享关系 | 主要职责 |
|---|---|---|
| Heap | 线程共享 | 类实例与数组 |
| Method Area | 线程共享 | 每类结构、方法代码等 |
| JVM Stack | 每线程私有 | 保存调用的 Frame |
| PC Register | 每线程私有 | 跟踪非 native 方法执行位置 |
| Native Method Stack | 支持时通常每线程分配 | 支持 native 方法执行 |


## 2. Main mechanism

每次方法调用创建一个 Frame，调用结束时销毁；当前 Frame 支持局部变量表、操作数栈及与运行时常量池相关的动态链接等职责。局部变量表可以保存基本类型值或对象引用，操作数栈支持指令求值。Java 调用栈按线程建立，另一个线程不会把自己的调用栈帧当成当前线程的帧。[JVMS2]

对象与引用变量不能混为一谈。例如下面的局部变量 ref 位于当前 Frame 的局部变量表，ref 指向的对象在规范的 Heap 中分配：

```java
Object ref = new Object();
Object another = ref;
```

两个局部变量可以指向同一个对象；“引用在栈帧”不表示对象也因此位于该栈帧。字段中的引用则是字段的组成部分，不能把所有引用都一律说成局部变量。[JVMS2]

## 3. Important boundary

Method Area 是 JVMS 的逻辑区域，保存每类结构、运行时常量池、方法数据和代码等；Runtime Constant Pool 是各类或接口在 Method Area 中的运行时表示之一。规范对物理位置、垃圾回收与压缩方式留有实现自由，不能把逻辑区域名称直接当作操作系统内存分配方式。[JVMS2]

HotSpot 的 Metaspace 是具体实现中的类元数据存储，Oracle JDK 21 文档说明类元数据使用 native memory 分配。这不等于整个 Method Area 的全部内容都能逐项等同为 Metaspace，也不能把所有 JVM 实现都描述成 HotSpot 的布局。[META][JVMS2]

## 4. Common misconceptions

“对象在 Heap，引用一定在栈”遗漏字段引用；“Method Area 就是 Metaspace”混淆规范与实现。JVMS 还允许 Frame 的物理存储采用实现选择，例如帧可由堆分配；逻辑上归属线程调用栈不等于强制一块连续的物理栈内存。讨论 JVM 优化后的具体分配还需实现证据，本题不从规范概念推导逃逸分析效果或具体对象字节布局。[JVMS2][META]

## 5. Official references

- [JVMS2] JVMS SE 21，§2.5.1–2.5.6 运行时数据区、§2.6 栈帧
- [META] Oracle JDK 21 GC Tuning Guide，Other Considerations / Class Metadata

[JVMS2]: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html
[META]: https://docs.oracle.com/en/java/javase/21/gctuning/other-considerations.html
