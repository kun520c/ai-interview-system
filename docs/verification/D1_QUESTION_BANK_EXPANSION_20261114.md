# D1｜63 道正式题库建设与受控导入验收

日期：2026-11-14（Asia/Shanghai）。仓库：`/Users/kun/Developer/projects/ai-interview-system`。源码基线：`0c6f92afe331954f7cea37d33db688fe781cbf2c`。

**PASS：新增 42 题、201 个评分点，evaluation 已真实提交并独立读回 63 题、298 个有效评分点。** EASY/MEDIUM/HARD 为 15/21/27，21 个必选分类难度桶各有 3 个候选。H01 已通过正式 Service 修订，旧冻结题库和历史审核证据保留。内容独立代理审查 43/43 通过，未解决 P0/P1 为 0。

**D2 数据准入 GO。** 当前 `QuestionPlanSelector` 仍确定性选择首个候选；本轮没有实现随机组卷。D2 可以在此候选池上开展独立开发和验证。本结论不代表完整 AI 面试质量或 RAG 覆盖已经通过；当前真实知识库仍仅 E01 一篇。

## 1. 交付与版本关系

- [新版 JSON](../question-bank/D1_QUESTION_BANK_V2_20261114.json)：`D1-v2-20261114`，包含题号、分类、难度、知识点、题干、口述参考答案、评分点、版本范围、资料链接、差异和可接受表达。
- [可读 Markdown](../question-bank/D1_QUESTION_BANK_V2_20261114.md)：与同版 JSON 对应的63题全文。
- [版本与指纹清单](../question-bank/D1_BANK_MANIFEST_20261114.json)：逻辑题号、修订号、当前/前版 DTO 指纹及计数。
- [内容审核报告](D1_QUESTION_BANK_CONTENT_REVIEW_20261114.md)及[逐题机器检查表](../question-bank/D1_REVIEW_CHECKLIST_20261114.json)：43项具体技术、版本、问答、评分、权重、差异和措辞判断，以及来源审查摘要。
- [真实导入映射](../question-bank/D1_EVALUATION_IMPORT_MANIFEST_20261114.json)：全部63个 bankId → questionId → pointIds，实际启用权重及数据保全摘要。
- [工具说明](../../tools/question-bank-d1/README.md)：一次性导入的边界、前提和失败恢复。

V1 JSON SHA-256 为 `ce6fff9bd42b064009b01c0300e2da5da16e73d04c363e3cf7f2b351cc1b6fd3`，V1 Markdown 为 `6278fd0ca2bf48c6256fa9bfd1f72ffa1925bb1a70eb2bb11fa67c5f8a06e296`，均未改。v2 SHA-256 为 `d4c1d9f20664308231ab383f5dce67154f3c5d874fc041b44f7499ce8647177e`。逻辑题号长期稳定，不等同数据库自增主键。63个当前 DTO 指纹均可复算；20旧题业务字段原样继承，H01 为修订2，42新题为修订1。

## 2. 入场与目标保护

阅读 AGENTS、D0/D1、E1–E3、E01真实验收、冻结题库、配置/选择器、管理员 Service/DTO 以及两份工程台账后，按授权实施内容扩容，不修改生产或测试源码、Schema、应用配置、RAG、AI评分或组卷逻辑。

真实 MySQL 8.0.45 位于 `127.0.0.1:3308`，UUID `85dd1e00-cf98-11f1-8677-9eb9380f25e7`。入场实测 evaluation 的21题/97点逐字段匹配V1，21个内存 MAIN 快照和97点序列化逐项通过。E01 为 documentId=1、READY v1，2个 ACTIVE Chunk。文档规范化内容 SHA-256 为 `4b82a2724e53946fffa082560fde1c348201114ac4f490e409c2ddbba5071de0`；两个 Chunk 内容和两向量全部2048个 Float32 数值摘要匹配 E01 原验收记录。

原 `ai_interview` 仅 user=1，其他12表为空；`ai_interview_test` 的13表均为空。evaluation 的会话、面试快照、答案、评价、报告及弱项等历史业务表为空。已保存三库逻辑备份、逐表行摘要和完整 evaluation 行快照。保护目录为 Git 忽略的 `backups/D1_20261114/`，不公开提交凭据、备份或原始行数据。

Milvus 固定为 `http://127.0.0.1:19532/default`。实际存在 evaluation、test 和两个 E0 合成集合，共4个；evaluation ID 为 `470437151302223600`，Strong count=2，其他三个为0。盘点仅执行查询/元数据读取，不使用初始化器，不插入、删除、重建、加载新集合或调用模型。

## 3. H01 修订

原 SP4 把“不能 instanceof List<String>”作为一般限制，缺少静态类型条件。新答案和 SP4 区分：`Object x` 不能直接测试 `List<String>`，可以测试 `List<?>`；Java 21 中 `List<Integer> x` 可以测试 `ArrayList<Integer>`，运行时仍检查容器类型，不逐元素确认泛型实参。可任选两项正确限制作答，不要求背诵固定例子。 [JLS 21 §15.20.2](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.20.2)

题干、分类、难度、知识点及评分点类型/权重均保留：25/20/20/25/10，总分100。SP1、SP2 的简写可进一步精炼，独审判为 P2 措辞建议，完整答案已给出准确边界，本次最小修订保留。声明泛型签名与普通运行时对象类型分别解释。 [JLS 21 §4.6](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.6) · [JVMS 21 §4.7.9](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.9)

真实更新调用 `QuestionAdminService.updateQuestion`，questionId **33** 保持；Service 整体替换使原5个评分点147–151被重建为392–396。该更新是5点替换，不计入新增201点。历史面试快照未改写；入场历史表为空，不能将此空样本包装为非空历史迁移实证。

## 4. 内容与数量验收

- EASY：原5 + 新10 = **15**，新增41点。
- MEDIUM：原7 + 新14 = **21**，新增70点。
- HARD：原9 + 新18 = **27**，新增90点。
- 总计：原21题/97点 + 新42题/201点 = **63题/298点**。
- JAVA_BASIC、JAVA_COLLECTION、SPRING、MYSQL：各 **9** 题。
- JVM、JAVA_CONCURRENCY、REDIS、NETWORK：各 **6** 题。
- PROJECT_SCENARIO：**3** 题。

全部63题 ENABLED，298点 ENABLED；每题4–5点，至少1个 CORE，全部点类型合法、非空、权重1–100、排序连续，逐题启用权重合计100。未发现孤儿点、重复逻辑ID/知识点或规范化精确重复题干/完整答案。数据库逐字段回读匹配已审输入，不依赖数据库恰好存在唯一约束来推断内容唯一。

结构检查同时验证 Java 枚举、实际 Create/Update DTO 和 UTF-8 TEXT 字节上限。v2最大题干/答案/评分点字节为434/1606/411，knowledgePoint 在100个 UTF-16 单元以内。全部身份、分类、难度和知识点匹配原 D1 矩阵；不存在计划外分类桶。

三位作者交叉审查他人题稿，主执行者另核对合并结果；H01 由不同代理复审。审查修正了问分不齐、异常自抑制前提、参数校验条件、索引跳扫条件、缓存竞态和 TLS 时序等问题。完整逐题证据及问题关闭情况在内容报告，不能用本节计数代替。未进行真人试答、实际答题时长或难度统计校准，也未以模型跑分声称题目已获得客观质量评级。

## 5. 独立导入阶段与恢复

执行链：同一份最终 JSON 字节解析与摘要 → 43项独审门禁 → Jakarta Validator → 仅 evaluation 的 Spring 上下文 → 现有 `EnvironmentIsolationInitializer` → 固定目标/UUID/13张 InnoDB 表校验 → 外层 REPEATABLE READ `TransactionTemplate` → 表主键范围锁定并对比基线 → 正式代理 `QuestionAdminService.updateQuestion/createQuestion` → 正式 MyBatis SQL → 全部源字段与内存快照校验 → COMMIT → 新进程只读验证。

没有手写业务 INSERT/UPDATE，没有改生产校验。导入器忽略外部 profile/目标覆盖，禁用 SQL 初始化、Milvus、DeepSeek，Embedding 使用不可用 loopback 占位端点，无真实模型密钥和对外 HTTP 监听。来源/审核摘要不同、分类身份不符、目标漂移或基线变化都拒绝写入。

各成功阶段退出码均0，真实MySQL手工验证 **4阶段/0失败/0错误/0跳过**，不是JUnit：

1. `baseline`：21题97点与原始V1完全匹配，保存完整13表行基线。
2. `rollback`：正式替换H01，成功创建前41题，再给第42题传权重99；确认为权重和校验异常后回滚。独立新连接对账三库39张表的行摘要全部相等。自增号段被消耗是预期，不重置序列，不声称物理数据库状态完全相同。
3. `import`：北京时区日志01:04:05开始，单事务完成H01替换及42新增，事务内验证63/298及保护数据后提交；保存提交回执。
4. `verify`：01:04:18启动独立进程，只读再验63题/298点以及63个生产快照工厂的内存结果；不创建任何真实面试或持久化快照。

另行验证重复导入：不提供数据库凭据运行同一导入入口，在 Spring/数据库初始化前即因已有提交回执拒绝（预期退出1）。该负例通过，不表示第二次导入失败后留下数据。提交结果不明时先 `verify` 对账，禁止盲目重试。没有演练网络断连或已提交后的灾难恢复；本地逻辑备份存在不等于本轮做过恢复覆盖。

## 6. 最终保全结果

- 原业务库、测试库完整 mysqldump 与入场逐字节相同，包含正式库原有用户行；具体摘要在真实导入映射的 `protection` 节。
- evaluation 另外11表的完整行摘要不变；E01 READY文档、2个ACTIVE Chunk原文/元数据不变。
- 其余20道旧题及92个旧评分点整行（含主键、时间字段）不变。
- Milvus4个集合的Schema、索引、计数、完整向量行前后完全一致，盘点文件SHA-256均为 `ac27c00aafb21ac1191e73b1b2f4c5f1d6cf7f810de6ccb64b18af500bd14a07`。E01向量逐值一致，不只是count一致。
- DeepSeek **0**、Embedding **0**；正式面试 **0**；生产算法和Schema改动 **0**。未改变任何真实模型凭据或环境配置。

## 7. 测试与如实记录的失败

实际定向命令：

```text
./mvnw -B -ntp -o -Dtest=EnvironmentIsolationInitializerTest,QuestionAdminServiceTest,CreateQuestionRequestValidationTest,AdminQuestionControllerTest,InterviewQuestionSnapshotFactoryTest,InterviewPlanConfigTest,QuestionPlanSelectorTest test
```

运行前清除 RUN_REAL_*、MILVUS_*、DEEPSEEK_*、SPRING_*、DB_*/TEST_DB_* 等环境覆盖。原两库不作为测试目标。

- 首次 Surefire **113/0/73/0**：Mockito/Byte Buddy 自附加在沙箱中失败。保存首次7份XML和日志，没有降低断言或跳过测试。
- 经执行环境审批后同组重跑 **113/0/0/0**，退出0。包含 Unit/Mock 93、MockMvc 20；涵盖401/403、合法管理员、非法DTO、生成键与影响行数分支、权重、事务调用及内存快照等。Mock结论不冒充真实SQL，真实MyBatis证据来自独立导入阶段。
- Java工具边界 **14/0/0/0**：1个合法63题输入与13个负例，覆盖身份、枚举、权重小数/越界/总和、未知评分点字段、空答案/null点、UTF-8溢出及版本。
- H01 Java21编译正/反例 **2组均符合预期**：合法组exit0，Object→List<String>非法组exit1。另三作者独立Java行为探针7+10+3=**20项通过**，非JUnit，不与113相加。作者原stdout仅在工具记录；主执行者归档源码和class后再运行同一批探针，20项确认通过，日志与命令见 `backups/D1_20261114/java-content-probe-summary.json`，重复运行不累计成40项。
- 整批回滚、真实提交、独立读回及重复导入拒绝分别留证；构建工具javac最终退出0，编译不替代功能验证。
- `git diff --check`、JSON/映射/链接与最终保护检查通过。

**普通全量 `mvn test` 未执行**：其中真实Mapper/事务测试会写 `ai_interview_test`，与本轮明确禁止写入原测试库冲突。没有改测试目标到正式库或evaluation，没有启用、跳过或削弱 guarded Smoke Tests。选定113项的skipped=0；未运行的全量不能记为通过或19项跳过。未跑前端、浏览器端到端和真实模型效果测试。

工具开发中出现并修正：首次Milvus盘点器误以为应有local集合，实际为两个E0合成集合，查询拒绝后依据既有日志修正；首次javac将int当对象调用equals失败；基线序列化缺Java时间模块以及误假设rag_retrieval_batch主键为id导致两次只读失败，修正后基线通过。Python临时py_compile尝试写系统缓存受沙箱拒绝，改用不生成缓存的运行方式。上述均保留日志，均发生在真实写入前；没有通过修改业务数据绕过失败。

独立工具审查还关闭了审核哈希与实际解析字节可能不一致的P1，以及身份矩阵、InnoDB和回滚回执门禁不足的问题。最终只读合并复核确认43个已审稿逐字段匹配、20旧题保留；数值节点经JSON规范化后再与保存的行基线比较。

## 8. Git、文件与学习状态

本地 HEAD 保持 `0c6f92afe331954f7cea37d33db688fe781cbf2c`。本轮通过 GitHub 连接器实时读取远端 main 为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，与缓存一致，本地领先8、落后0。没有 fetch 或修改引用；没有 add、commit、push、reset、clean、stash、rebase。D1 无提交SHA；数据库COMMIT与Git commit是不同状态。

入场1013个非忽略文件建立正文/权限指纹。除了用户明确授权追加的两份工程台账外，其余1011个文件保持；两份台账保留原文并追加D1记录；HEAD和index保持。正式交付、修改文件及本地执行证据的实际绝对路径统一列在 [D1交付清单](../question-bank/D1_DELIVERY_MANIFEST_20261114.json)。保留所有先前未提交文件及8个本地历史提交，不将其归为本轮新增。

新增 `L-D1-01`～`L-D1-04`，学习记录累计83条，均为待学习、掌握未确认。本轮AI编写、审查、工具和真实验证不代表开发者本人已掌握或独立开发经历。

## 9. 下一步边界

D2可以基于63题开展分类内随机选题；必须另验无重复、分类/难度覆盖、可复现实验、候选不足拒绝、并发会话复用与创建时一次性冻结快照。当前仍固定选题，本轮不提前实现。

D3知识库扩容和正式AI测试分别后续执行。现有E01之外没有补充新语料，不能由题库63题推断63题的RAG证据已充分。真人难度/时长校准、后续模型评分一致性及语义重复盲评仍缺少证据。部分来源使用当前工作区绝对路径，适于本机追溯，迁移机器时需按源码SHA重新定位；外部资料链接应结合已注明的技术版本阅读。
