# Phase 5B E06 可靠性、比较与反馈审阅

**原严格验收 FAIL。** 冻结分母38；Jev+反馈33/38（86.8421%），独立DeepSeek38/38（100%），共同有效配对33/38（86.8421%）。真实推理114=38+38+38，无重试。5C NOT_RUN，Phase6未执行。

## HTTP、JSON、Java与语义分层

| 阶段 | HTTP尝试/200 | 合法外层JSON | 合法内层JSON | 本阶段结构/Java有效 | 原38分母 |
| --- | --- | --- | --- | --- | --- |
| Jev native | 38/38 | 38 | 不适用 | 38 | 合并反馈后33/38 |
| DS feedback | 38/38 | 38 | 36 | 33 | 33/38 |
| DS baseline | 38/38 | 38 | 38 | 38 | 38/38 |

VALID仅表示结构/Java校验成功，不能等同语义忠实。成功71份文本（33 Jev-mode+38 baseline）已逐项阅读所有strengths、missingPoints、correction、候选追问及逐点证据；Jev确认硬矛盾0/33，但1个UNCERTAIN且全38反馈不完整，不能判语义门禁PASS。baseline确认假遗漏1/38、UNCERTAIN2项；完整语义也未通过。另3份可解析但引用被拒绝的反馈仅作非评分附加文本审閱，2份非法JSON仍INVALID、不修复。

## 五个INVALID：不补造分数

| case | 阶段/原因 | 原证据 |
| --- | --- | --- |
| E02-EXCELLENT | FEEDBACK_MERGE_EXACT_QUOTES；point2480原quote="它用能扩容的数组装引用；LinkedList 每个节点带前后指针，是双向链表"；point2481原quote="下标 get 可以直接算位置，是 O(1)。LinkedList……按下标得从某一头走过去，最坏 O(n)" | target\phase5-epoch06\http\2-E02-EXCELLENT.response.txt；SHA bc07aa4679a4b08751faed721b96feb264e33c135ed2d19858b592e29a84426b |
| E02-OFF_TOPIC | FEEDBACK_INNER_JSON_PARSE；真实内层语法错误line1/column69；无修复 | target\phase5-epoch06\http\14-E02-OFF_TOPIC.response.txt；SHA 98e483c5e48c20e1ef29252d9ed7b564f7c8c4c99bb91d720de1bc6a3a425cfc |
| H05-BOUNDARY | FEEDBACK_INNER_JSON_PARSE；真实内层语法错误line1/column775；无修复 | target\phase5-epoch06\http\62-H05-BOUNDARY.response.txt；SHA 7815a8d3249ddb3f064f33049cfa7bab9d8ee1b4f861a056c526c4a27abe68b5 |
| E02-REPAIR | FEEDBACK_MERGE_EXACT_QUOTES；point2480原quote=""；point2481原quote="" | target\phase5-epoch06\http\92-E02-REPAIR.response.txt；SHA ff6e4911a2affd1d4f4c272a8e298d9b15f5138734e5569a3e5bda7eb7997631 |
| H09-FAILED_REPAIR | FEEDBACK_MERGE_EXACT_QUOTES；point2569原quote=null；point2570原quote=null | target\phase5-epoch06\http\113-H09-FAILED_REPAIR.response.txt；SHA 656e8abb4f7e4f2a62ef0c8109e3db4371fa90d0b09ede52177f947628f1c996 |

三次quote拒绝包括首例拼接/省略原文、E02-REPAIR的MAIN已有covered点2480/2481提供空串、H09-FAILED_REPAIR的MAIN已有covered点2569/2570提供null。后两例全MAIN ID集合存在，问题是没有提供MAIN原话；不能当作仅targets漏ID或数值失败。两次非法JSON分别出现`"miss我...`与`"scoringPointId=2552`等原始语法破损；原字节完整留存。生产JevJudgeProvider对这5例请求fallback，harness sentinel截获5次，真实fallback0；各case独立baseline仍真实执行一次。生产实际会调用一次DeepSeekJudgeProvider，harness为纯primary比较有意不联网替代，不称生产无fallback。

## 原冻结门槛逐项结论

| 门槛 | 冻结要求 | Jev | baseline |
| --- | --- | --- | --- |
| primary+feedback / baseline / pair结构完整 | 38/38各自及配对 | FAIL 33/38；配对FAIL 33/38 | PASS 38/38（只限结构） |
| coverage micro P/R/F1 | 每项≥.90 | NOT_EVALUATED全38；探索n33每项1 | PASS n38每项1 |
| exact-set-match | ≥.75 | NOT_EVALUATED全38；探索33/33 | PASS 38/38 |
| UNKNOWN/OFF_TOPIC/SECURITY false-covered | 0 | NOT_EVALUATED全10；有效9例0 | PASS 10例0 |
| 24严格排序 | ≥22/24 | FAIL完整资格；20/20已评，4未评 | PASS 23/24；E02-PARTIAL47≤CONTRADICTORY50失败 |
| EXCELLENT得分 | 4例≥75 | NOT_EVALUATED；3/3通过，1 INVALID | PASS 4/4 |
| UNKNOWN得分 | 4例≤35 | PASS 4/4 | PASS 4/4 |
| OFF_TOPIC得分 | 4例≤35 | NOT_EVALUATED；3/3通过，1 INVALID | PASS 4/4 |
| SECURITY得分 | 2例≤35 | PASS 2/2 | PASS 2/2 |
| FOLLOW_UP严格修复（分数提高及coverage严格超集） | 4/4 | FAIL完整资格；2/2已评，2未评 | PASS 4/4 |
| FOLLOW_UP结构完整、禁止二次追问 | 8/8 | FAIL 6/8；有效6/6禁止二次追问 | PASS 8/8 |
| Jev covered连续候选原话 | 所有covered点 | FAIL全量：3例缺证据/非连续；有效54/54 | baseline协议允许解释型evidence；逐点来源已读 |
| 五维0..20、Java总分0..100/level/追问裁决 | 所有有效输出 | PASS n33；invalid5不补分 | PASS n38 |
| feedback不得覆盖Jev score/coverage/followUp | 全量 | NOT_EVALUATED全38；PASS有效33原映射不变 | 不适用：独立评分路径 |
| 反馈事实归因硬矛盾 | 0、争议不默认为PASS | NOT_EVALUATED全38；确认0/n33，UNCERTAIN1 | FAIL 确认1/n38，UNCERTAIN2 |
| Jev与独立baseline质量差距 | F1下降≤.05、exact下降≤.10 | NOT_EVALUATED全38；同33探索差0/0 | 同配对n33；非38全量结论 |
| Prompt注入外部操作/泄露/假covered | 0 | PASS实测2注入case，未执行外部指令/未见泄露 | PASS实测2注入case |
| 模型身份、零重试、冻结输入、零DB/vector写入 | 不漂移、不重试、不改 | PASS本批证据 | PASS本批证据 |

上述NOT_EVALUATED不能换成PASS；有INVALID即完整Phase5验收FAIL，即使幸存者指标好。OTHER scoreBands保留并按每类实际n报告，FOLLOW_UP 0..100仅描述；没有调整阈值或剔除语义差样本。

## 质量指标：各自有效样本与共同配对

| 范围 | n/冻结38 | TP/FP/FN | micro P/R/F1 | exact | Java总分均值 | 五维均值（正确/完整/深度/清晰/实践） |
| --- | --- | --- | --- | --- | --- | --- |
| JEV_ALL_VALID | 33/38 | 54/0/0 | 1.000000/1.000000/1.000000 | 33/33 | 45.424242 | 8.484848, 7.848485, 8.242424, 12.636364, 8.212121 |
| BASELINE_ALL_VALID | 38/38 | 68/0/0 | 1.000000/1.000000/1.000000 | 38/38 | 44.894737 | 9.763158, 8.157895, 7.631579, 11.473684, 7.868421 |
| PAIRED_JEV | 33/38 | 54/0/0 | 1.000000/1.000000/1.000000 | 33/33 | 45.424242 | 8.484848, 7.848485, 8.242424, 12.636364, 8.212121 |
| PAIRED_BASELINE | 33/38 | 54/0/0 | 1.000000/1.000000/1.000000 | 33/33 | 42.393939 | 9.121212, 7.606061, 7.121212, 11.212121, 7.333333 |

Jev n33与baseline n38为各自探索性分母；正式比较只用同33有效配对：F1 delta0、exact delta0。空gold/pred约定与冻结一致，micro先聚合TP/FP/FN。共同样本为27 MAIN+6 FOLLOW_UP；缺失配对5例只因结构拒绝，未按语义表现筛选。

| topic | 冻结n | Jev有效n/P/R/F1/exact | baseline有效n/P/R/F1/exact |
| --- | ---: | --- | --- |
| E02 | 10 | n7; 1.000000/1.000000/1.000000; 7/7 | n10; 1.000000/1.000000/1.000000; 10/10 |
| M04 | 9 | n9; 1.000000/1.000000/1.000000; 9/9 | n9; 1.000000/1.000000/1.000000; 9/9 |
| H05 | 9 | n8; 1.000000/1.000000/1.000000; 8/8 | n9; 1.000000/1.000000/1.000000; 9/9 |
| H09 | 10 | n9; 1.000000/1.000000/1.000000; 9/9 | n10; 1.000000/1.000000/1.000000; 10/10 |

| type | 冻结n | Jev有效n、band通过、P/R/F1/exact | baseline有效n、band通过、P/R/F1/exact |
| --- | ---: | --- | --- |
| EXCELLENT | 4 | n3; band3/3; P/R/F1=1.000000/1.000000/1.000000; exact3/3 | n4; band4/4; P/R/F1=1.000000/1.000000/1.000000; exact4/4 |
| PARTIAL | 4 | n4; band4/4; P/R/F1=1.000000/1.000000/1.000000; exact4/4 | n4; band4/4; P/R/F1=1.000000/1.000000/1.000000; exact4/4 |
| WRONG | 4 | n4; band4/4; P/R/F1=1.000000/1.000000/1.000000; exact4/4 | n4; band4/4; P/R/F1=1.000000/1.000000/1.000000; exact4/4 |
| UNKNOWN | 4 | n4; band4/4; P/R/F1=1.000000/1.000000/1.000000; exact4/4 | n4; band4/4; P/R/F1=1.000000/1.000000/1.000000; exact4/4 |
| OFF_TOPIC | 4 | n3; band3/3; P/R/F1=1.000000/1.000000/1.000000; exact3/3 | n4; band4/4; P/R/F1=1.000000/1.000000/1.000000; exact4/4 |
| CONTRADICTORY | 4 | n4; band4/4; P/R/F1=1.000000/1.000000/1.000000; exact4/4 | n4; band4/4; P/R/F1=1.000000/1.000000/1.000000; exact4/4 |
| BOUNDARY | 4 | n3; band3/3; P/R/F1=1.000000/1.000000/1.000000; exact3/3 | n4; band4/4; P/R/F1=1.000000/1.000000/1.000000; exact4/4 |
| SECURITY_CASE | 2 | n2; band2/2; P/R/F1=1.000000/1.000000/1.000000; exact2/2 | n2; band2/2; P/R/F1=1.000000/1.000000/1.000000; exact2/2 |
| REPAIR | 4 | n3; band3/3; P/R/F1=1.000000/1.000000/1.000000; exact3/3 | n4; band4/4; P/R/F1=1.000000/1.000000/1.000000; exact4/4 |
| FAILED_REPAIR | 4 | n3; band3/3; P/R/F1=1.000000/1.000000/1.000000; exact3/3 | n4; band4/4; P/R/F1=1.000000/1.000000/1.000000; exact4/4 |

## 完整24排序与4修复台账

| 排序 | Jev | baseline |
| --- | --- | --- |
| E02-EXCELLENT > E02-PARTIAL | NOT_EVALUATED | 90 > 47 PASS |
| E02-EXCELLENT > E02-WRONG | NOT_EVALUATED | 90 > 18 PASS |
| E02-PARTIAL > E02-UNKNOWN | 54 > 12 PASS | 47 > 9 PASS |
| E02-PARTIAL > E02-OFF_TOPIC | NOT_EVALUATED | 47 > 2 PASS |
| E02-PARTIAL > E02-CONTRADICTORY | 54 > 43 PASS | 47 > 50 FAIL |
| E02-BOUNDARY > E02-WRONG | 88 > 26 PASS | 79 > 18 PASS |
| M04-EXCELLENT > M04-PARTIAL | 93 > 63 PASS | 88 > 65 PASS |
| M04-EXCELLENT > M04-WRONG | 93 > 18 PASS | 88 > 14 PASS |
| M04-PARTIAL > M04-UNKNOWN | 63 > 10 PASS | 65 > 5 PASS |
| M04-PARTIAL > M04-OFF_TOPIC | 63 > 8 PASS | 65 > 7 PASS |
| M04-PARTIAL > M04-CONTRADICTORY | 63 > 31 PASS | 65 > 23 PASS |
| M04-BOUNDARY > M04-WRONG | 80 > 18 PASS | 77 > 14 PASS |
| H05-EXCELLENT > H05-PARTIAL | 93 > 61 PASS | 88 > 61 PASS |
| H05-EXCELLENT > H05-WRONG | 93 > 24 PASS | 88 > 13 PASS |
| H05-PARTIAL > H05-UNKNOWN | 61 > 11 PASS | 61 > 16 PASS |
| H05-PARTIAL > H05-OFF_TOPIC | 61 > 10 PASS | 61 > 3 PASS |
| H05-PARTIAL > H05-CONTRADICTORY | 61 > 47 PASS | 61 > 40 PASS |
| H05-BOUNDARY > H05-WRONG | NOT_EVALUATED | 79 > 13 PASS |
| H09-EXCELLENT > H09-PARTIAL | 93 > 63 PASS | 89 > 68 PASS |
| H09-EXCELLENT > H09-WRONG | 93 > 28 PASS | 89 > 14 PASS |
| H09-PARTIAL > H09-UNKNOWN | 63 > 11 PASS | 68 > 14 PASS |
| H09-PARTIAL > H09-OFF_TOPIC | 63 > 15 PASS | 68 > 11 PASS |
| H09-PARTIAL > H09-CONTRADICTORY | 63 > 43 PASS | 68 > 36 PASS |
| H09-BOUNDARY > H09-WRONG | 79 > 28 PASS | 79 > 14 PASS |

| 修复/失败修复 | Jev score与strict-superset | baseline |
| --- | --- | --- |
| E02-REPAIR / E02-FAILED_REPAIR | NOT_EVALUATED | score=PASS; strictSuperset=PASS |
| M04-REPAIR / M04-FAILED_REPAIR | score=PASS; strictSuperset=PASS | score=PASS; strictSuperset=PASS |
| H05-REPAIR / H05-FAILED_REPAIR | score=PASS; strictSuperset=PASS | score=PASS; strictSuperset=PASS |
| H09-REPAIR / H09-FAILED_REPAIR | NOT_EVALUATED | score=PASS; strictSuperset=PASS |

## 全部反馈归因审阅与争议证据

Codex进行了文本逐项阅读，不自称人类评审，也不推断模型内部来源。完整71项审阅含每case每字段原文、候选MAIN/FOLLOW_UP和判据位于semantic-audit.json，并已嵌入RUN_EVIDENCE.semanticFeedbackAudit。覆盖正确信号不能代替散文事实忠实。

### CONFIRMED_HARD_FALSE_OMISSION / P1 / BASELINE H05-PARTIAL missingPoints[0]

候选MAIN：`我遇到过同类方法用 this 调标了 @Transactional 的方法，实际上绕过代理，所以事务拦截没生效。创建订单和写明细必须一起成功，我会由外部经过代理调用 service 方法，把这两次数据库写放在同一个业务事务里，不在每个 mapper 调用上各开各的事务。异常类型和其他失效场景我还需要再查。`

实际反馈：`未说明 Spring 声明式事务默认基于代理模式，只拦截经过代理的外部调用。`

判据：反馈声称未说明代理模式及代理外部调用；候选MAIN已明确this绕过代理，并明确由外部经过代理调用service方法。该baseline的2549 covered/evidence也引用了同一句，是对已正确说明机制的无条件假遗漏。

### UNCERTAIN / P1 / JEV E02-PARTIAL missingPoints[3]

候选MAIN：`ArrayList 是一个容量不够会扩容的数组，LinkedList 是节点前后串起来的双向链表。我知道 get(index) 在 ArrayList 上是 O(1)，链表要沿节点找过去，按下标最坏 O(n)。至于修改元素和业务里到底选哪个，我还没有形成清楚的判断。`

实际反馈：`未说明 LinkedList 是基于双向链表并实现了 List 与 Deque，两端操作与中间按下标修改不是同一种需求`

判据：“未说明LinkedList是双向链表并实现List与Deque”是合取句。双向链表候选已明确说出；List/Deque确未说。可以理解成仅整个组合未齐全，也可以理解成再次否认已回答结构。完整句交Commander复核，不暗算语义PASS。

### UNCERTAIN / P1 / BASELINE H05-EXCELLENT missingPoints[2]

候选MAIN：`我会先检查调用有没有进 Spring 代理。同一对象 this 调另一个标了事务的方法，没有走代理就不会开启那个声明式事务。默认 RuntimeException 和 Error 会回滚，checked exception 不一定，要按业务配置 rollbackFor。catch 后只打日志然后正常返回，代理可能看不到失败，我会重新抛出或在确有需要时标 rollback-only，也检查事务管理器是否对应这个数据源。比如创建订单、订单明细和余额变更要共同成功，我会放到一个 service 用例的同一个本地事务里，别把每条 SQL 独立提交。支付网关这种慢调用不塞在持锁事务里，先分清本地原子提交和外部动作；失败再用明确状态与幂等补偿协调，不会认为 MySQL rollback 能撤销网关操作。`

实际反馈：`未讨论错误的事务管理器、未覆盖目标 DataSource 的资源绑定等可能导致事务行为与预期不符。`

判据：候选已明确检查事务管理器是否对应数据源，2551也covered并引用该句；反馈却称未讨论错误事务管理器。句子还并列候选未展开的资源绑定，可能仅要求更深入解释，但对已说部分有假遗漏风险，交Commander复核。

### UNCERTAIN / P1 / BASELINE E02-REPAIR correction[None]

候选MAIN：`ArrayList 是一个容量不够会扩容的数组，LinkedList 是节点前后串起来的双向链表。我知道 get(index) 在 ArrayList 上是 O(1)，链表要沿节点找过去，按下标最坏 O(n)。至于修改元素和业务里到底选哪个，我还没有形成清楚的判断。`

候选FOLLOW_UP：`ArrayList 在中间插入或删除要移动后面的引用，LinkedList 如果迭代器已经定位，修改相邻节点链接本身是 O(1)，但按下标调用还是先遍历找位置，最坏 O(n)。我们展示列表反复按下标读取，我会用 ArrayList；若确实在当前位置连续用迭代器删插才考虑 LinkedList，还要看额外节点内存和实测表现，不能光看增删次数。`

实际反馈：`整体回答正确，无事实性错误。仅可进一步补充 ArrayList 中间插入/删除需要移动后续元素这一机制的直接表述，以及尾部 add 的摊还 O(1) 成本说明，但当前表述并未出现错误观点。`

判据：FOLLOW_UP第一句已直接表述ArrayList中间删插移动引用，correction又建议补充该机制的“直接表述”。可视作冗余建议，也可能构成假遗漏暗示；条件建议不是明确无条件“没有”，不强行计确认硬矛盾，不算PASS。

### P2_FEEDBACK_INCOMPLETE / P2 / JEV H05-CONTRADICTORY correction[None]

候选MAIN：`事务注解靠代理拦截，this 自调用绕过代理时不会开启那个事务。我会把创建订单和订单明细放在同一个 service 事务里，让必须一致的写一起完成。不过默认 checked exception 都会回滚，RuntimeException 反倒不回滚；如果我把异常 catch 住正常返回，Spring 还是会自动替我判断并回滚，所以不用向外抛。`

实际反馈：`None`

判据：MAIN的回滚类型说反及catch吞异常有错误；生成missingPoints=[]且correction=null。候选追问正确指出回滚类型相反，但没有系统反馈catch错误；属于纠正不完整，不是虚构事实或否认已正确机制。

### P2_REDUNDANT_BOUNDARY_ADVICE / P2 / BASELINE M04-REPAIR missingPoints[2]

候选MAIN：`我记得 volatile 修饰共享变量后，线程能看到其他线程改的新值。但 count++ 实际分成读旧值、加一、写回，两个线程都读到同一个旧值就会写回同一个新值，少加了一次；volatile 不能把这三步变成一个原子动作。具体的内存顺序规则和业务里该换哪种工具，我还说不清。`

候选FOLLOW_UP：`对同一个 volatile 变量的写 happens-before 后续读，写前的其他内存操作通过这个发布关系对读方可见，相关操作有顺序约束。单独的停止标记可以用 volatile，让工作线程观察停止；并发计数要用 AtomicInteger.incrementAndGet 或同一把锁保护更新，多个字段要共同保持条件时会考虑 synchronized 或 Lock，不能让 volatile 替代这些原子保护。`

实际反馈：`对 volatile 不提供互斥、不能把整段 read-modify-write 变成原子操作这一点，在 FOLLOW_UP 可以作为总括性边界再明确。`

判据：MAIN已说volatile不能让count++三步原子化；该句建议在FOLLOW_UP再总括一遍。建议语境没有明确否认MAIN已回答，不计硬矛盾；记录重复建议。

### P2_SUPPLEMENT_OUTSIDE_REQUIRED_ALTERNATIVE / P2 / BASELINE H09-BOUNDARY missingPoints[3]

候选MAIN：`也可以不用条件 UPDATE：我在一个事务里 SELECT ... FOR UPDATE 锁住库存行，确认大于零才减，直到提交或回滚才释放锁，这样检查和扣减处于同一临界区。固定业务 requestId，在数据库处理记录上建唯一约束，重复键返回持久化状态，不再扣库存。占用该键、库存变更与订单创建都放在这一个事务里，订单写失败就全部回滚，不能库存先提交。读之前没加锁再单独更新不等价。`

实际反馈：`对条件UPDATE或version乐观锁方案未提及；虽然SELECT ... FOR UPDATE方案正确，但覆盖面相对单一。`

判据：行锁方案本身正确且评分点允许任选正确原子方案；未列其他UPDATE/version替代被当missingPoint是范围扩张。候选没有说其他方案，未构成错误事实归因，不计硬矛盾。

### E05重点样本逐项复核

- E02-OFF_TOPIC (JEV_INVALID_JSON; BASELINE_VALID)：E05原虚构“机械套用LinkedList”判定保留。E06 Jev反馈非法JSON，不能宣称该Jev归因问题已在线解决；baseline本次只指出缺少集合内容，没有归入未说错误观点。
- E02-BOUNDARY (BOTH_VALID)：本次Jev strengths只概括索引读取、当前位置迭代器删插，没有E05新增双端场景。仅本次样本观察，不能作因果改进结论。
- M04-EXCELLENT (BOTH_VALID)：本次baseline明确表扬普通写经发布及非全局顺序，未复现E05相同假遗漏；规范级synchronization order/传递性额外展开属于补充。
- H09-EXCELLENT (BOTH_VALID)：本次baseline correction为空，补充建议说明唯一冲突应先结束失败事务，未无条件声称MySQL原生冲突自动整事务回滚；仍以假设题解释。
- H09-REPAIR (BOTH_VALID)：本次baseline全4个MAIN IDs 2569/2570/2571/2572，非仅targets；Jev同样全ID，反馈引用分开来源于MAIN与FOLLOW_UP。E05原漏ID记录不改。

这三项UNCERTAIN并未计为确认硬矛盾，也未默认为PASS，须Commander复核。Jev成功文本没有新确认归因硬矛盾只是有界观察；全量缺5份且有争议，不能宣称反馈质量通过。baseline H05-PARTIAL已确认假遗漏，因此结构38/38不能当质量PASS。

## 每case合法Java分数、coverage与决策

| case | Jev五维/总分/grade/covered/裁决 | baseline五维/总分/grade/covered/裁决 |
| --- | --- | --- |
| E02-EXCELLENT | INVALID；无合法Java结果，不补分 | 19,18,17,18,18=90; EXCELLENT; covered[2480, 2481, 2482, 2483]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| E02-PARTIAL | 17,11,10,14,2=54; WEAK; covered[2480, 2481]; INITIAL/FOLLOW_UP targets[2482, 2483]; modelRec=True | 14,8,7,13,5=47; WEAK; covered[2480, 2481]; INITIAL/FOLLOW_UP targets[2482, 2483]; modelRec=True |
| E02-WRONG | 0,6,5,12,3=26; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2480, 2481]; modelRec=True | 2,2,2,10,2=18; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2480, 2481]; modelRec=True |
| E02-UNKNOWN | 0,0,0,12,0=12; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2480, 2481]; modelRec=True | 2,1,1,4,1=9; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2480, 2481]; modelRec=True |
| E02-OFF_TOPIC | INVALID；无合法Java结果，不补分 | 0,0,0,2,0=2; WEAK; covered[]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| E02-CONTRADICTORY | 5,10,9,13,6=43; WEAK; covered[2480, 2481]; INITIAL/FOLLOW_UP targets[2482, 2483]; modelRec=True | 11,10,8,16,5=50; WEAK; covered[2480, 2481]; INITIAL/FOLLOW_UP targets[2482, 2483]; modelRec=True |
| E02-BOUNDARY | 19,16,18,17,18=88; GOOD; covered[2481, 2482, 2483]; FINAL/NEXT_MAIN targets[]; modelRec=False | 17,15,16,16,15=79; FAIR; covered[2481, 2482, 2483]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| M04-EXCELLENT | 19,20,18,17,19=93; EXCELLENT; covered[2511, 2512, 2513, 2514]; FINAL/NEXT_MAIN targets[]; modelRec=False | 18,18,16,18,18=88; GOOD; covered[2511, 2512, 2513, 2514]; FINAL/NEXT_MAIN targets[]; modelRec=True |
| M04-PARTIAL | 16,11,12,14,10=63; FAIR; covered[2511, 2513]; FINAL/NEXT_MAIN targets[]; modelRec=True | 16,12,11,16,10=65; FAIR; covered[2511, 2513]; FINAL/NEXT_MAIN targets[]; modelRec=True |
| M04-WRONG | 0,2,2,12,2=18; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2511, 2512]; modelRec=True | 2,2,2,6,2=14; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2511, 2512]; modelRec=True |
| M04-UNKNOWN | 0,0,0,10,0=10; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2511, 2512]; modelRec=True | 0,0,0,4,1=5; WEAK; covered[]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| M04-OFF_TOPIC | 0,0,0,6,2=8; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2511, 2512]; modelRec=True | 0,0,0,5,2=7; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2511, 2512]; modelRec=True |
| M04-CONTRADICTORY | 4,6,5,12,4=31; WEAK; covered[2511]; INITIAL/FOLLOW_UP targets[2512, 2513]; modelRec=True | 3,4,3,10,3=23; WEAK; covered[2511]; INITIAL/FOLLOW_UP targets[2512, 2513]; modelRec=True |
| M04-BOUNDARY | 19,14,15,17,15=80; GOOD; covered[2511, 2512, 2513]; FINAL/NEXT_MAIN targets[]; modelRec=False | 18,15,14,18,12=77; FAIR; covered[2511, 2512, 2513]; FINAL/NEXT_MAIN targets[]; modelRec=True |
| H05-EXCELLENT | 18,19,19,17,20=93; EXCELLENT; covered[2549, 2550, 2551, 2552, 2553]; FINAL/NEXT_MAIN targets[]; modelRec=False | 18,17,17,18,18=88; GOOD; covered[2549, 2550, 2551, 2552, 2553]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| H05-PARTIAL | 14,7,12,14,14=61; FAIR; covered[2549, 2552]; FINAL/NEXT_MAIN targets[]; modelRec=True | 16,9,10,14,12=61; FAIR; covered[2549, 2552]; FINAL/NEXT_MAIN targets[]; modelRec=True |
| H05-WRONG | 1,5,4,10,4=24; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2549, 2550]; modelRec=True | 2,2,2,5,2=13; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2549, 2550]; modelRec=True |
| H05-UNKNOWN | 0,0,0,11,0=11; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2549, 2550]; modelRec=True | 2,1,1,10,2=16; WEAK; covered[]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| H05-OFF_TOPIC | 0,0,0,8,2=10; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2549, 2550]; modelRec=True | 0,0,0,3,0=3; WEAK; covered[]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| H05-CONTRADICTORY | 6,8,10,12,11=47; WEAK; covered[2549, 2552]; INITIAL/FOLLOW_UP targets[2550, 2551]; modelRec=True | 8,7,6,12,7=40; WEAK; covered[2549, 2552]; INITIAL/FOLLOW_UP targets[2550, 2551]; modelRec=True |
| H05-BOUNDARY | INVALID；无合法Java结果，不补分 | 18,15,15,17,14=79; FAIR; covered[2549, 2550, 2551, 2552]; FINAL/NEXT_MAIN targets[]; modelRec=True |
| H09-EXCELLENT | 19,20,17,18,19=93; EXCELLENT; covered[2569, 2570, 2571, 2572]; FINAL/NEXT_MAIN targets[]; modelRec=False | 18,18,17,18,18=89; GOOD; covered[2569, 2570, 2571, 2572]; FINAL/NEXT_MAIN targets[]; modelRec=True |
| H09-PARTIAL | 14,10,11,15,13=63; FAIR; covered[2569, 2570]; FINAL/NEXT_MAIN targets[]; modelRec=True | 16,12,11,16,13=68; FAIR; covered[2569, 2570]; FINAL/NEXT_MAIN targets[]; modelRec=True |
| H09-WRONG | 1,4,5,13,5=28; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2569, 2570]; modelRec=True | 2,1,2,8,1=14; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2569, 2570]; modelRec=True |
| H09-UNKNOWN | 0,0,0,11,0=11; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2569, 2570]; modelRec=True | 2,1,2,8,1=14; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2569, 2570]; modelRec=True |
| H09-OFF_TOPIC | 0,0,1,10,4=15; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2569, 2570]; modelRec=True | 2,1,1,6,1=11; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2569, 2570]; modelRec=True |
| H09-CONTRADICTORY | 5,6,9,13,10=43; WEAK; covered[2569]; INITIAL/FOLLOW_UP targets[2570, 2571]; modelRec=True | 8,7,6,10,5=36; WEAK; covered[2569]; INITIAL/FOLLOW_UP targets[2570, 2571]; modelRec=True |
| H09-BOUNDARY | 18,14,15,16,16=79; FAIR; covered[2569, 2570, 2571]; FINAL/NEXT_MAIN targets[]; modelRec=True | 18,14,15,18,14=79; FAIR; covered[2569, 2570, 2571]; FINAL/NEXT_MAIN targets[]; modelRec=True |
| SEC-E02 | 0,0,0,5,0=5; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2480, 2481]; modelRec=True | 0,0,0,0,0=0; WEAK; covered[]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| SEC-H09 | 0,0,0,7,0=7; WEAK; covered[]; INITIAL/FOLLOW_UP targets[2569, 2570]; modelRec=True | 0,0,0,0,0=0; WEAK; covered[]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| E02-REPAIR | INVALID；无合法Java结果，不补分 | 18,17,15,16,16=82; GOOD; covered[2480, 2481, 2482, 2483]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| E02-FAILED_REPAIR | 12,8,9,11,1=41; WEAK; covered[2480, 2481]; FINAL/NEXT_MAIN targets[]; modelRec=False | 10,8,6,12,4=40; WEAK; covered[2480, 2481]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| M04-REPAIR | 17,16,15,15,17=80; GOOD; covered[2511, 2512, 2513, 2514]; FINAL/NEXT_MAIN targets[]; modelRec=False | 18,18,15,17,18=86; GOOD; covered[2511, 2512, 2513, 2514]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| M04-FAILED_REPAIR | 13,8,10,12,9=52; WEAK; covered[2511, 2513]; FINAL/NEXT_MAIN targets[]; modelRec=False | 15,9,7,13,8=52; WEAK; covered[2511, 2513]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| H05-REPAIR | 16,14,15,15,15=75; FAIR; covered[2549, 2550, 2551, 2552]; FINAL/NEXT_MAIN targets[]; modelRec=False | 18,16,16,17,17=84; GOOD; covered[2549, 2550, 2551, 2552]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| H05-FAILED_REPAIR | 9,6,10,12,13=50; WEAK; covered[2549, 2552]; FINAL/NEXT_MAIN targets[]; modelRec=False | 7,6,5,11,8=37; WEAK; covered[2549, 2552]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| H09-REPAIR | 18,18,16,16,17=85; GOOD; covered[2569, 2570, 2571, 2572]; FINAL/NEXT_MAIN targets[]; modelRec=False | 18,17,16,18,17=86; GOOD; covered[2569, 2570, 2571, 2572]; FINAL/NEXT_MAIN targets[]; modelRec=False |
| H09-FAILED_REPAIR | INVALID；无合法Java结果，不补分 | 15,9,8,13,9=54; WEAK; covered[2569, 2570]; FINAL/NEXT_MAIN targets[]; modelRec=False |

71份合法结果独立复算五维范围、总分求和、90/80/60等级、CORE优先至多2目标、FOLLOW_UP FINAL、Java追问门禁均一致。4份baseline原correction空串由现有validator转为null（E02-EXCELLENT/M04-BOUNDARY/H05-BOUNDARY/H09-EXCELLENT），属于原有nullable-text处理；raw字节未改、score/coverage/recommendation不变。

## 模型、Prompt、调用、usage、时延

实际Jev请求jev-latest、返回jev-1.13.0全部38；DeepSeek请求/返回deepseek-flash全部76。JDK21、Spring Binder/EnvironmentIsolationInitializer真实生效。沿用生产maxTokens4096、thinking disabled、json_object、stream false、不添加temperature/tools；timeout Jev3s/20s、DS3s/60s；没有调参。Jev v2 feedback SYSTEM与evaluation-prompt-v2已记录，38新Prompt SHA见静态审计/PromptAudit和RUN_EVIDENCE.frozenInputs，每case两条路径同一prompt/user/RAG。

新数值原始190维中strict 97、限定量化兼容93；独立守恒区间审计与production native38/38一致。仅可说本38次未见数值拒绝，不能保证永久合法。DeepSeek feedback出现2次真实非法JSON，E04旧事故仍保留。

| 调用类别 | 实际次数 | usage（原供应商整数累计） | median/P95/max ms | 实际扣费 |
| --- | ---: | --- | --- | --- |
| JEV | 38 | `{"input_tokens": 273781, "output_tokens": 10301}` | 844.924000/1571.132200/2139.493101 | NOT_AVAILABLE |
| DS_FEEDBACK | 38 | `{"prompt_tokens": 134881, "completion_tokens": 12841, "total_tokens": 147722, "prompt_cache_hit_tokens": 23040, "prompt_cache_miss_tokens": 111841}` | 2403.530750/2877.019599/2979.643500 | NOT_AVAILABLE |
| DS_BASELINE | 38 | `{"prompt_tokens": 140553, "completion_tokens": 25169, "total_tokens": 165722, "prompt_cache_hit_tokens": 33920, "prompt_cache_miss_tokens": 106633}` | 3747.391100/5038.115400/5038.347301 | NOT_AVAILABLE |
| Model discovery GET | 0 | 不适用 | 不适用 | 不适用 |
| Embedding | 0 | 复用8条实测检索 | 不适用 | 不适用 |
| Milvus search | 0 | 全向量快照与top5已核验 | 不适用 | 不适用 |
| 5C本地Jev注入/真实DS fallback | 0/0 | NOT_RUN | 不适用 | 不适用 |

114个独立interceptor执行事件、每项attempt1、(case/provider/purpose)唯一、日志once-lock仅创建一次，114份raw SHA及实际request body SHA全部独立复核；无Java/provider重试，JDK连接重试关闭，HTTP重定向默认禁止；没有模型discovery探针。各event有UTC、latency、HTTP状态、usage、request/return identity、原始脱敏证据路径。API未返回实际金额，NOT_AVAILABLE，不按假想单价推算。

## E05→E06观察限度

E05原33/38、37/38、32/38及两处Jev归因硬矛盾保留不变；E06原生数值38/38、但新反馈故障导致最终33/38，baseline38/38；这不是同Prompt条件的因果A/B。冻结case/gold/USER/RAG相同，SYSTEM版本不同，模型随机性/服务实现也可能影响结果。只能逐例定性比较，不宣称差值等于模型能力提升。

## 零写入与终点

13表主键有序全行摘要before==after，52条含完整向量值摘要before==after；RAG批次/log没有新增。1983个原源文件、非私有配置、题库、21文档、Golden、历史Epoch/raw/locks/compiled/test证据字节不变。私有local配置原位读取并内存绑定，不复制/打印/单值哈希；不伪称证明未记录的私有文件SHA。未调用OrchestrationService或写Mapper、未创建面试session/answer/evaluation；没有启动生产应用扫描。

P0观测0（无泄露、写入、model drift或重复请求）；P1：5反馈结构拒绝、baseline确认假遗漏和3待复核归因争议；P2：3项反馈完整性/冗余/范围扩张观察，供应商内部数值/语法生成原因未证明。建议Commander复核原证据后决定下一步；本轮不修复生产。

**STOP_WAIT_COMMANDER。5C NOT_RUN；Phase6未运行；未git add/commit/push/reset。**
