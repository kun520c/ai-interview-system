# D1 H14–H27 作者自查记录

2026-11-14，Asia/Shanghai。自查对象为同目录的 `HARD_20261114.json`，覆盖 D1 矩阵中的 JVM、JAVA_CONCURRENCY、SPRING、MYSQL、REDIS、NETWORK、PROJECT_SCENARIO，每类两题。14 题各 5 点，共 70 点。每题权重合计 100，核心机制或场景判断优先计分。

- **H14** 比较同名接口的 Class 对象和定义加载器。父级委派通常可共享 API，SPI 发现使用上下文或显式加载器，仍需满足同一接口的类型关系。与 M12 的类加载阶段和 H15 的运行诊断分开。 [JVMS 21 §5.3](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3)
- **H15** 先看 OOM 类型和同一时间线，用线程采样或 JFR 查 CPU 热点，结合分配和回收后存活量区分短命对象分配与长期保留，验证修复时保持负载可比。命令可替换。 [JDK 21 排障指南](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshooting-memory-leaks.html) [jcmd 手册](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html)
- **H16** 每线程的值可活得比任务久，工作线程复用导致串值，异常清理和跨线程传播各自评分。InheritableThreadLocal 的边界是线程创建。 [ThreadLocal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html) [InheritableThreadLocal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/InheritableThreadLocal.html)
- **H17** 以依赖和并行编排、失败策略、阻塞执行器和超时资源边界区分 H04。允许 thenCombine 或 allOf。orTimeout 异常完成 Future，底层停止须由客户端超时或受支持的取消处理。 [CompletableFuture](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- **H18** 显式限定事务代理、支持保存点的单数据源 JDBC、符合回滚规则。三个传播级别各有场景，rollback-only 与独立事务额外连接分别评分。 [Spring 6.2 传播](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html)
- **H19** 在订单事务中持久化可恢复待办，提交后发送与远端幂等处理分开。AFTER_COMMIT 的内存监听存在发送前崩溃窗口，回调内的后续写入需要独立事务。与 H25 的 HTTP 调用方重试和 H27 的报告状态恢复分开。 [Spring 6.2 事务事件](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/event.html) [afterCommit()](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html#afterCommit())
- **H20** 对相同主键与明确提交顺序推导结果。B 提交后的三个观察值分别为 RR 的 10/8/7 和 RC 的 8/8/7。H06 考 MVCC 结构，本题按事务交错给分。 [MySQL 8.0 一致性读](https://dev.mysql.com/doc/refman/8.0/en/innodb-consistent-read.html) [锁定读](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html)
- **H21** 数据库回滚范围与应用主动结束事务分别说明。默认超时只回滚该语句，死锁牺牲者的事务全部回滚。固定锁顺序、证据与有界新事务重试各自评分。 [MySQL 8.0 错误处理](https://dev.mysql.com/doc/refman/8.0/en/innodb-error-handling.html) [死锁](https://dev.mysql.com/doc/refman/8.0/en/innodb-deadlocks-handling.html)
- **H22** 更新后旧值回填是题设时序推导，回源合并解决的是另一问题。版本在 Redis 内跟随失效推进，回填时原子比对。数据库提交至缓存失效之间仍有跨库窗口，要求立即读到权威数据时可直接查库。 [Redis Cache-Aside](https://redis.io/docs/latest/develop/use-cases/cache-aside/)
- **H23** 区分减少往返、WATCH 条件执行和 Lua 原子读改写。Redis 7.x 单实例下，排队期错误、EXEC 内执行期错误和脚本报错分别判断。 [Redis Pipeline](https://redis.io/docs/latest/develop/using-commands/pipelining/) [事务](https://redis.io/docs/latest/develop/using-commands/transactions/) [Lua](https://redis.io/docs/latest/develop/programmability/eval-intro/)
- **H24** 限于 TLS 1.3 首次完整证书握手。链信任、SAN 主机名、证书私钥持有证明、临时密钥协商及业务授权分别验证。 [RFC 8446](https://www.rfc-editor.org/rfc/rfc8446.html) [RFC 9525](https://www.rfc-editor.org/rfc/rfc9525.html) [RFC 5280](https://www.rfc-editor.org/rfc/rfc5280.html)
- **H25** 明确未发送与已发送但无响应分别处理，幂等看预期效果，同键异载荷冲突。题设 POST 幂等键按应用合同处理。 [RFC 9110 §9.2.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.2.2)
- **H26** 当前 HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`。认证、会话与题目归属、同键同正文、唯一约束、提交事务、评价尝试令牌为不同保护。已完成评价走持久化结果重建，工作流接管限制旧尝试写入。证据在题目 JSON 的源码方法和隔离库约束引用中。
- **H27** 同一 HEAD。生成请求先鉴权与检查完成状态，已 READY 可复用，过期接管要求条件更新并递增会话版本。每主问题取一个正确归属的 FINAL，短事务同时保存报告、更新弱项和标记 READY，已有报告时不重复累计。证据在题目 JSON 的源码方法、Mapper 和隔离库约束引用中。

结构脚本逐题对照 D1 计划、旧 21 题、DTO 字段、枚举、评分点、权重、文本字节和来源字段，14/14 通过，无题干、答案或知识点完全重复。题干、参考答案、评分点正文的最大 UTF-8 字节数分别为 413、975、156。独立的 Temurin 21.0.13 临时程序验证 H14 同名类不可赋值、H16 工作线程跨任务保留与清理、H17 Future 超时后任务可完成，3 项通过，0 失败。JUnit、Spring 集成、MySQL、Redis、模型和真实面试均未运行。H20–H23 的行为依据对应官方文档及题设推演。独立审查在同目录 `HARD_INDEPENDENT_REVIEW_20261114.json` 逐题记录。按反馈补齐 H14 的 SPI 发现和 H20 的写入判断，修订 H22 的失效时序与 H24 的身份校验措辞，审阅者已复核四处修订。
