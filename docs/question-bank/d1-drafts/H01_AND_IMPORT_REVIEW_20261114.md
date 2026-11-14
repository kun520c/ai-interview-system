# D1 H01 与导入工具独立审查

2026-11-14，Asia/Shanghai。审查人 author_easy_medium。H01 和导入工具由 root 编写。内容结论及逐项核对记录见 [H01 检查表](H01_INDEPENDENT_REVIEW_20261114.json)。

## H01 内容

**PASS，未发现未解决 P0/P1。** 题目继续考查 Java 21 泛型擦除。修订答案给出相反的两个静态类型例子，明确运行时检查容器类，不会逐个检查元素。SP4 允许任意两项正确限制，不强制复述例子。旧 SP1 的 bound 是简写，参考答案给出最左边界的精确定义。旧 SP2 可以用同一 ArrayList 实现表述得更清楚；属于 P2 措辞优化，本轮保留。声明签名元数据有 JVM 规范依据。 [JLS 21 §4.6](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.6) · [JLS 21 §15.20.2](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.20.2) · [JVMS 21 §4.7.9](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.9)

## 导入工具

**独立代码审查通过。** 导入还须满足最终内容审查、真实目标和保护数据核验、回滚演练和独立后验对账。此记录仅说明代码走读的结论。

- 初审发现题库解析与审核哈希分两次读取文件，存在文件在两次读取之间变化的窗口。修订后解析和摘要使用同一份 `bankBytes`，并将逐题审核结论绑定到该摘要。要求 42 道新增题和 H01 共 43 个独立审核项均通过。
- 初审发现只按题目总数判断，没有硬校验 D1 逻辑 ID 与 DTO 对应关系。修订后读取固定摘要的 D1 计划，核对逻辑 ID、难度、分类和知识点，使用现有 `InterviewPlanConfig` 检查每个必选桶从 1 扩到 3，且无计划外桶。
- 工具限定固定本机 evaluation URL 和 MySQL UUID，仅激活 `evaluation` profile，保留环境隔离初始化器。检查 13 个核心表及其 InnoDB 引擎后，在外层 REPEATABLE READ 事务中对表主键作锁定扫描，再与基线行数据对账。表锁定读防止其他连接在同一时段改写或插入范围内业务记录。 [MySQL 8.0 InnoDB 事务隔离](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html)
- H01 整体替换和 42 道新增通过正式代理 `QuestionAdminService` 的 REQUIRED 事务方法，加入同一事务。H01 保留题目主键，评分点由正式替换机制重建。候选和评分点经真实 MyBatis 读取，对比题库字段、有序评分点和生产快照构造器的内存结果。保护表和其他 20 道旧题逐行比较。 [Spring Framework 6.2 事务传播](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html)
- 演练在真实 H01 替换和 41 次创建后提交不合规的第 42 题给 Service，要求出现业务异常并回滚；对账通过才生成回执。正式导入检查题库与基线摘要匹配的演练回执，提交前再次检查基线、目标和保护行。InnoDB 回滚可以留下自增 ID 空洞，应报告行数据一致，不能推断自增序列不变。
- 若提交后回执写入失败，先用 `verify` 读取持久状态。重复导入会被已有回执或基线变化拒绝。正式业务库、测试库和 Milvus 的前后证据由主执行者在导入工具之外独立采集。
