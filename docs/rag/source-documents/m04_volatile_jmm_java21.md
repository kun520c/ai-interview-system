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

`volatile int count; count++` 不是一个整体原子操作。按照 JLS 对 postfix increment 的定义，执行 `count++` 时会读取变量当前值，将数值加一，再把结果存回变量。两个线程可能都先读到相同旧值，各自计算出同一个新值，随后分别写回；后一写入覆盖前一写入时，一次逻辑增量就丢失了。把 `count` 声明为 volatile 可以为对 `count` 的读写建立 JMM 同步关系，但不会把“读、加、写”这三个步骤变成一个排他执行的整体。

当共享计数必须正确累加时，应让同步范围覆盖完整的 read-modify-write 更新。概念上可以使用 `synchronized` 或 `Lock` 将复合临界区串行化，也可以选用适合该更新语义的原子类，例如 `AtomicInteger`。选择依据是需要的语义：单次状态发布与观察可以考虑 volatile；需要互斥保护多个步骤或原子更新时，不能把 volatile 当作锁或复合操作原子性保证。

## 4. Common misconceptions

“volatile 等于轻量级锁”混淆了可见性、顺序约束与互斥。volatile 变量的单次读写参与 JMM 同步，但没有锁住临界区；`count++` 仍可能丢失更新。“happens-before 表示 CPU 必须逐条按源码顺序执行”也不准确：happens-before 是规范定义的可见性与排序关系，允许实现采用符合该关系的执行方式，并不描述某个处理器的具体指令调度。

## Official references

- Primary: [Java Language Specification, Java SE 21, Chapter 17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html) — §17.4.2、§17.4.4、§17.4.5。
- Secondary: [Java Language Specification, Java SE 21, Chapter 15 — Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.14.2) — §15.14.2 postfix increment operator。
