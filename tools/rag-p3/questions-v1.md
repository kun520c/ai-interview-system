# P3独立新题（冻结阅读版）

12题：6道覆盖假设 + 6道机制缺口Hard Negative。覆盖来源是检索前AI文档审查，须独立人工确认；不是候选Chunk的Ground Truth。

问题集SHA256：`c32f89f36f1e636440bc33bb4489089ba2eebe3998c4093ed0e649ac72f675e8`。本文件由冻结JSON派生，机器入口只读取JSON。

## P3-C01 · COVERED

一段代码对LinkedList执行for循环，并在每次循环中调用get(i)处理第i个元素；处理过程中还会在当前位置插入少量元素。为什么总遍历成本可能不是线性的？改用持续前进的ListIterator后，定位成本和当前位置插入成本分别发生什么变化？

题面SHA256：`7830600827ca68ab748cc57347c53cc4e7cabae8e6831f98b6f6b1764c609457`。

核对机制：链表下标定位、反复get与已有游标的区别。

## P3-C02 · COVERED

在JDK 8u ConcurrentHashMap中，两个线程同时向同一个空桶put不同键，其中一个CAS失败后应如何继续？如果桶已经非空，为什么锁住桶头后还要复核数组中的桶头没有变化？请说明这两条写入路径的协调边界。

题面SHA256：`aa3705a48e58f98df28151c5af4aae870f8bf29d25618459e9fef74f17858fbf`。

核对机制：空桶CAS失败复核、非空桶头同步及位置复核。

## P3-C03 · COVERED

一个生产线程先给普通字段config赋值，再将volatile字段ready写为true；消费线程读到这次ready=true后才读取config。为什么前面的普通字段写入可以对消费者可见？如果生产者在写ready之后又修改config，能否仅凭这次ready读取保证看到后面的修改？

题面SHA256：`6d326c1336723cd62339958b2db932f5b0e63044fb9fdf63bcb663ab9dbfc582`。

核对机制：volatile发布与传递happens-before只覆盖相应写入前的动作；不把可见性当互斥。

## P3-C04 · COVERED

Spring 6.2默认proxy事务模式下，外部调用一个事务方法；方法更新两条数据库记录后抛出IOException，但没有配置rollbackFor，也没有改变全局默认。它是否必然回滚？如果方法内部catch后正常返回，又为什么不能简单断言必然提交？

题面SHA256：`6462dc5daf15b85a214b13c20b026a9c0e47cf1d99711d419566b8d37f9287cc`。

核对机制：checked exception默认及全局/方法规则、吞异常与rollback-only边界。

## P3-C05 · COVERED

MySQL 8.0 InnoDB的REPEATABLE READ事务A已经BEGIN但尚未读取。事务B先提交一次更新，A才执行第一次普通SELECT；B随后再次提交更新，A再做普通SELECT和SELECT FOR UPDATE。这三次读取应按怎样的快照时点或锁定读语义理解，为什么不能把BEGIN时间当成所有读取的固定快照？

题面SHA256：`78c85993b6669fb283bb317b6d3fad94caea2c451b2ab00d59453bc7a41c8ee0`。

核对机制：RR第一次一致性读快照与FOR UPDATE最新可用数据的区别；未引入自身更新边界。

## P3-C06 · COVERED

Redis排行榜用Sorted Set存放用户和积分，两个不同用户积分相同，随后又用新的积分写入其中一个已有用户。成员唯一性与score排序分别约束什么？相同score会不会挤掉另一个用户，已有成员再次写入会不会产生第二份成员？

题面SHA256：`91741834cf4978cbc1a3c7b0adb4de33e48be1a921151f7b84b7a80d1c8ec0c4`。

核对机制：成员唯一、不同成员可同score及排序，重复成员仍唯一。

## P3-N01 · NOT_COVERED

Redis Cluster中，一个Lua脚本想同时比较两个锁key的token并删除它们，却收到CROSSSLOT。两个key应满足什么哈希槽条件？怎样使用hash tag安排key才能让多key脚本在同一槽执行，为什么脚本原子性不能自动消除这个限制？

题面SHA256：`51c3dc50a0bda78ccb987ddcc21b99f37f41633300954d4631eb401280b1ff5c`。

核对机制：文档只说明单key compare-and-delete Lua及单实例边界；未解释Cluster槽、hash tag或多key路由。

## P3-N02 · NOT_COVERED

MySQL 8.0 InnoDB在REPEATABLE READ下通过非唯一索引做范围SELECT FOR UPDATE时，另一个事务插入范围内一个原本不存在的索引值也会等待。不存在的行为什么还能阻止插入？record lock、gap lock和next-key lock各自覆盖什么，为什么不能只用MVCC快照解释这次等待？

题面SHA256：`29167ef2a120b73bbc06192228cc57dfb459d6af7aa8576a4c2932ded27541c6`。

核对机制：文档区分快照与锁定读，但未定义间隙/next-key及非唯一索引范围锁区间。

## P3-N03 · NOT_COVERED

Java 21的ZGC在并发重定位堆对象时，应用线程可能仍读到指向旧地址的引用。colored pointer中记录的状态与load barrier怎样协作，让应用最终使用正确的对象地址？这一读取路径为何不需要应用先等待整个堆完成搬迁？

题面SHA256：`54a8ba1b942682ed816ea806e2dc5be332a77b4b94c6a9d72968ec47c9d46dec`。

核对机制：冻结文档限定G1 Region、Collection Set及暂停复制；没有ZGC彩色指针和load barrier重定位机制。

## P3-N04 · NOT_COVERED

两个插件分别由不同的自定义ClassLoader定义同名的com.example.Message类。对象打印出的类名完全相同，交给另一个插件后强制转换却抛ClassCastException。JVM中的类身份由哪些因素共同确定，为什么共享同一份类字节也不能只按类名判断类型相同？

题面SHA256：`c63c3ee9fc16139e4aa53917da356dc4181228ffff4e7b3696f32288efd075f6`。

核对机制：文档解释Method Area/Metaspace类元数据位置；H01解释泛型擦除的Class，不定义二进制名+defining loader类型身份。

## P3-N05 · NOT_COVERED

Spring响应式事务使用ReactiveTransactionManager，返回的Mono链中发生publishOn线程切换。事务状态应通过Reactor Context还是普通线程绑定方式传播？TransactionalOperator把哪些订阅执行放进事务，为什么不能直接套用本地JDBC事务的线程绑定解释？

题面SHA256：`44926ed94883fd0b6f09f9300ae1f7383d5edffaa0991036c7e010c04e1a6bc2`。

核对机制：冻结文档只讨论默认proxy和本地JDBC资源边界，没有Reactor Context、响应式订阅及TransactionalOperator。

## P3-N06 · NOT_COVERED

Linux中的TCP连接没有收到对端FIN，发送的数据却长期得不到ACK。SO_KEEPALIVE的探测间隔与TCP_USER_TIMEOUT分别控制什么？把TCP_USER_TIMEOUT设短，会不会同时改变重传或keepalive探测发送的时间，为什么不能用TIME_WAIT的2MSL解释这次断开？

题面SHA256：`248d634f35eace170eba4fe564c67ccac5fbd5ae3e644567033bca0379c9b126`。

核对机制：冻结文档解释FIN关闭、TIME_WAIT/CLOSE_WAIT，不包含Linux keepalive与USER_TIMEOUT的计时和关闭语义。
