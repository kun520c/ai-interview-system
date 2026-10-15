# JDK 8 ConcurrentHashMap：分桶协调与扩容

> Title: JDK 8 ConcurrentHashMap：分桶协调与扩容
> Bank ID: H02
> Knowledge category: JAVA_COLLECTION
> Knowledge point: concurrent_hashmap
> Difficulty: HARD
> Target question: JDK 8 的 ConcurrentHashMap 如何实现并发安全？CAS、`synchronized` 和桶结构分别起什么作用？
> Version scope: OpenJDK jdk8u402-b06 的 JDK 8 实现；不外推为所有 JDK 的内部结构承诺。
> Primary official source: [CHM8]
> Secondary official sources: 无（见 S5）
> Authoring status: PHASE_4D2_AUTHORED_NOT_INGESTED

## 1. Core model

该 JDK 8 ConcurrentHashMap 的主体是 table 桶数组，普通桶使用节点链表，冲突较多时可使用树桶；不再以旧式 Segment 分段锁作为主要组织结构。源码保留某些兼容字段不意味着主要访问路径仍按 Segment 加锁，判断实现应看实际 table、节点与读写流程。[CHM8]

并发安全来自数据结构、可见性和协调方式共同作用，不是把普通 HashMap 的每个方法包进一把全局锁。桶结构描述存储组织，CAS 与 synchronized 描述更新不同状态时的协调；不能仅背“数组加链表加树”就解释线程安全。

## 2. Main mechanism

写入路径先检查 table 是否已经初始化，再定位目标桶。目标为空时，线程尝试用 CAS 将新节点安装到这个空位置；竞争失败后重新检查并重试，避免覆盖其他线程刚插入的节点。只有确认位置仍为空才能成功安装，并非看到空桶就直接普通赋值。[CHM8]

非空桶修改链表或树时，putVal 对当前桶头对象 synchronized，并在取得锁后复核 table 该位置仍指向这个桶头，然后按桶类型查找、更新或插入。这主要串行化同一个桶的结构修改，不是锁住整个 Map。复核很关键：等待期间桶可能已被迁移或更换，不能继续按过期的头节点操作。[CHM8]

```text
空桶：CAS 安装；失败则重新检查
迁移标记：在允许条件下帮助迁移
普通非空桶：锁住当前头节点，复核，再修改
```


## 3. Important boundary

普通 get 通常不走与 put 相同的互斥更新流程。table/节点可见性与表槽访问机制使读者能够安全观察已发布结构；Node 的 val、next 是 volatile，表槽通过相应可见性访问，特殊桶还有自己的查找处理。这里不能把无普通桶更新锁理解成所有分支都完全没有同步。[CHM8]

扩容允许多个线程协作搬迁不同桶。写线程遇到迁移标记时可进入 helpTransfer，在扩容仍有效且有迁移工作等条件满足时参与 transfer，然后使用新表继续操作。读取遇到迁移节点也有相应查找路径；“遇到扩容就必须等待唯一搬迁线程完成”不是此实现的概括。[CHM8]

## 4. Common misconceptions

“所有操作都 lock-free”遗漏非空桶更新的 synchronized；“一把锁保护整个 Map”忽略桶范围。树桶、初始化、扩容又有各自协调，不能把普通链表桶的路径推广到每个内部动作。

容器线程安全也不使 get 后判断再 put 的复合业务流程自动原子；需要时应采用对应原子 API 或明确保护整个业务条件。这是由单个操作保证与多步调用之间的区别得到的使用边界。本题与 M02 的区别在并发发布、桶协调和协作扩容，不复述 HashMap 树化阈值。[CHM8]

## 5. Official references

- [CHM8] OpenJDK jdk8u402-b06 ConcurrentHashMap.java，Overview/Node/putVal/get/helpTransfer/transfer

[CHM8]: https://raw.githubusercontent.com/openjdk/jdk8u/jdk8u402-b06/jdk/src/share/classes/java/util/concurrent/ConcurrentHashMap.java
