# P1 独立质量测试集：16条新查询

文件 SHA256：`4f42970bbdb3682c475056e512ff725832329d213b8b49e3a27ac45b5afbd456`。8条独立改写、4条边界追问、4条明确库外问题；无相关性标签、无预期分数、无检索结果。仅由知识文档内容判定覆盖范围，不预判COSINE。4条边界查询实际按FOLLOW_UP构造，其新的主问题上下文和评分点也已包含在同一冻结JSON中。

| Query ID | 类型 | 问题原文 | 覆盖依据/未覆盖理由 |
|---|---|---|---|
| P1I-Q01 | INDEPENDENT_PARAPHRASE | 一个文件导出组件已经继承了框架提供的基类，现在还要统一暴露导出能力，并让几种实现复用各自的配置状态。接口和抽象类分别能承担哪部分职责？为什么不能再继承一个保存配置的父类？ | E01 |
| P1I-Q02 | INDEPENDENT_PARAPHRASE | 订单服务的构造器接收一个通知发送器，但方法内部又直接创建邮件发送器。测试传入的假发送器为什么没有生效？怎样调整依赖的获得方式，才能让业务逻辑使用组装方提供的实现？ | E03 |
| P1I-Q03 | INDEPENDENT_PARAPHRASE | 把一个用户对象作为HashMap的键保存后，又修改了参与相等判断的用户编号，此时用原对象查找也可能失败。对象还是同一个，为什么会发生这种现象？怎样选择更稳定的键？ | M01 |
| P1I-Q04 | INDEPENDENT_PARAPHRASE | 两个线程各自执行同一个方法，方法的局部变量都指向同一个缓存对象。局部变量属于各自线程，是否说明对该对象的修改也互不影响？请区分栈帧中的引用和被引用对象的归属。 | M03 |
| P1I-Q05 | INDEPENDENT_PARAPHRASE | 批量装入大量数据时，一个JDK 8u HashMap不断扩容。提前规划容量和调低负载因子分别改变什么？为什么不能把负载因子越小就越好当作通用优化结论？ | M02 |
| P1I-Q06 | INDEPENDENT_PARAPHRASE | 一张InnoDB表使用很长的字符串作为主键，并建立了多个二级索引。为什么这种主键选择可能使每个二级索引也占用更多空间？二级索引记录需要保存什么，才能找到完整行？ | M06 |
| P1I-Q07 | INDEPENDENT_PARAPHRASE | 把访问过页面的用户ID依次加入Redis集合时，有的业务需要保留每次访问，有的只需要知道有哪些用户来过。List和Set应怎样选择？重复加入同一ID后，两种结构保存的信息有什么区别？ | M07 |
| P1I-Q08 | INDEPENDENT_PARAPHRASE | 客户端通过TCP连续发送两条业务消息，服务端却在一次读取中拿到两条内容，也可能多次才读完一条。TCP已经保证有序，为什么还会这样？业务消息边界应由谁来确定？ | E05 |
| P1I-Q09 | BOUNDARY_FOLLOW_UP | 在Java 21中，参数静态类型为List<Integer>时测试instanceof ArrayList<Integer>，与把任意Object测试为List<String>有什么不同？为什么反射读取到泛型声明，仍不能说明所有对象都携带可检查的元素类型？ | H01 |
| P1I-Q10 | BOUNDARY_FOLLOW_UP | 当本次Collection Set只包含一部分Region时，怎样找到集合外指向集合内的引用？Remembered Set与并发标记获得的存活信息是在回答同一个问题吗？ | H03 |
| P1I-Q11 | BOUNDARY_FOLLOW_UP | 持锁记录还没复制到副本，主节点就故障并发生副本提升。为什么两个客户端可能都认为自己持有锁？仅依靠唯一token和安全释放能解决这个获取阶段的问题吗？关键写入还需要什么约束？ | H07 |
| P1I-Q12 | BOUNDARY_FOLLOW_UP | 线程池已经shutdown后继续提交，CallerRunsPolicy是否仍保证由调用线程执行？为什么这个拒绝策略不能作为任务不丢或跨进程崩溃恢复的保证？ | H04 |
| P1I-Q13 | OUT_OF_COVERAGE | Kafka生产者使用acks=1时，Leader确认后立即故障，而消息尚未复制到其他副本，会有什么丢失风险？acks=all与min.insync.replicas怎样影响发送成功的条件？ | H04只讨论进程内线程池，M07只讨论Redis经典结构与简单队列；21篇没有Kafka、ISR或生产者ack协议的机制证据。 |
| P1I-Q14 | OUT_OF_COVERAGE | Elasticsearch里同一个字符串分别映射成text和keyword后，使用match与term查询可能出现哪些差异？分析器怎样影响倒排索引中的词项以及查询匹配？ | M06是InnoDB有序树/聚簇索引，不包含Elasticsearch倒排索引、分析器、text/keyword映射或match/term语义。 |
| P1I-Q15 | OUT_OF_COVERAGE | 移动客户端采用OAuth 2.0授权码流程时为什么需要PKCE？code_verifier与code_challenge如何防止被截获的授权码直接换取令牌？ | E05/H08只解释TCP/UDP和连接状态；全部21篇没有OAuth授权码、PKCE或令牌交换机制。项目有JWT代码也不能充当知识库覆盖证据。 |
| P1I-Q16 | OUT_OF_COVERAGE | PostgreSQL的Serializable Snapshot Isolation怎样识别可能破坏可串行化的读写依赖？两个事务各自读取条件再更新不同行时，为什么其中一个可能需要中止并重试？ | E04/H06/H09限定MySQL8 InnoDB事务、版本读和库存幂等；没有PostgreSQL SSI依赖检测或谓词锁的机制证据。MYSQL仅是测试容器的相邻现有枚举，不代表覆盖归属。 |

评分点是这次离线测试的合成评价标准，ID不写入题库。Embedding输入严格使用JSON的productionStyleQueryText，由真实Java QueryBuilder离线校验；coverageBasis、uncoveredReason、标签状态均不进入Embedding。与原E02八查询、existing/expanded各30条Golden、旧20条holdout分开保存，没有合并标签或覆盖矩阵。所有Source SHA、对应正文摘录/行号、每条Query SHA在JSON中可复核。

本集的库外分类是“所需完整机制未在21篇中覆盖”，不是预先断言全部TopK不相关或必须过滤到零；通用边界若确实有帮助，后续仍可由人标为部分相关。
