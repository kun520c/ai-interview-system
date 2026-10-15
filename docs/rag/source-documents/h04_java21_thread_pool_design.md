# ThreadPoolExecutor：容量、拒绝与任务责任

> Title: ThreadPoolExecutor：容量、拒绝与任务责任
> Bank ID: H04
> Knowledge category: JAVA_CONCURRENCY
> Knowledge point: thread_pool_design
> Difficulty: HARD
> Target question: 如果一个后端服务会突然收到大量任务，而且任务不能轻易丢失，你会如何设计 ThreadPoolExecutor 的核心线程数、最大线程数、队列和拒绝策略？为什么？
> Version scope: Java SE 21 ThreadPoolExecutor API；容量、监控、持久化与重试策略为工程设计指导。
> Primary official source: [POOL]
> Secondary official sources: 无（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

突发任务场景需要同时约束执行并发、排队容量和失败责任。ThreadPoolExecutor 管理进程内的工作线程与任务队列；它本身不是持久化任务存储。设计应明确任务何时算接收成功、拒绝时由谁重试，以及进程退出后怎样恢复，不能只给几个线程数就宣称“任务不会丢”。[POOL]

## 2. Main mechanism

execute 的主要提交路径是：运行线程少于 corePoolSize 时优先创建 worker；达到核心规模后优先入 workQueue；无法入队时才尝试增加线程到 maximumPoolSize；仍不能接受或已经 shutdown，则交给 RejectedExecutionHandler。这里是主要路径，关闭等状态变化还会触发复核，不能假设入队即永远一定执行。[POOL]

keepAliveTime 默认用于超过核心规模的空闲线程回收；启用 allowCoreThreadTimeOut 后可让核心线程也适用超时。无界队列通常使任务在核心线程忙时继续排队，maximumPoolSize 不会因此自动发挥削峰作用。[POOL]

## 3. Important boundary

线程数需结合 CPU 密集或 I/O 等待、机器资源、连接池与下游承载能力：增加线程可能利用等待空隙，也可能放大竞争或压垮下游。队列容量要考虑任务内存与允许排队时间；有界队列让过载尽早可见，不能用无界队列把所有突发流量无限积累。以上是依据 API 资源权衡作出的工程指导，没有万能线程数公式。[POOL]

不能静默丢失业务任务时，避免 Discard/DiscardOldest。可用明确失败后由调用者按稳定业务键幂等重试，或在确实需要跨崩溃恢复时另行设计持久化接收与恢复；这些职责不由线程池自动提供。CallerRuns 可让提交线程执行任务形成反压，但 shutdown 时会丢弃，并不保证持久化；使用它还应核查提交线程能否承受阻塞。[POOL]

## 4. Common misconceptions

监控可观察 active threads、queue depth、reject rate、task latency，并通过突发、持续过载和下游变慢的压测反馈调整参数。getActiveCount 是近似活动数，getQueue 可用于监控；拒绝计数与排队/执行延迟可由自定义 handler、任务包装或 hooks 记录，不能说每项都是线程池内置精确统计。[POOL]

上述监控与压测是待实施的设计方案，本阶段未运行。线程池、入队成功与任务业务成功是不同状态；重试也需业务幂等。不要把 CallerRuns 或最大线程数设大，当作故障恢复协议。

## 5. Official references

- [POOL] ThreadPoolExecutor API，Java SE 21，Queuing / Rejected tasks / hooks / getActiveCount / getQueue

[POOL]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
