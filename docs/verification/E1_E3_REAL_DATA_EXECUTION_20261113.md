# E1–E3｜Evaluation 环境与首批数据执行验收

日期：2026-11-13（Asia/Shanghai）。仓库：`/Users/kun/Developer/projects/ai-interview-system`。代码基线：`0c6f92afe331954f7cea37d33db688fe781cbf2c`。

**A PASS；B PASS；C PASS；D 离线准备 PASS、真实调用 BLOCKED（待单独付费授权）。** 已实际创建独立 evaluation 数据库及 Collection，通过正式业务 Service 原子导入冻结的 **21 题、97 个评分点**。知识文档、Chunk、向量及模型调用均为 **0**。H01 保留原文，修订建议单列；不能把本次导入验收当成该题评分口径已修复。

本报告合并记录各阶段和最终结果，保留全部 E0/E1 历史报告。未修改生产代码、测试源码、正式 DDL、冻结题库或应用配置；没有执行 Git add、commit、push、reset、clean、stash、rebase。原有工作区修改和本地提交保留。

## A．离线备份与独立恢复：PASS

现场重新核验新容器 `ai-interview-e0r2-milvus`，ID `9e708121918a0bde1568d15c8684e7b4145bd73287fee1327d41ed98f85abe4e`；镜像实际为 `milvusdb/milvus:v2.6.26`、ARM64、摘要 `sha256:f43e0cd0c9912c35379c349f7f3a3a806b9308462d43d1bfa18d007c6642d693`。仅该容器引用新卷 `ai-interview-e0r2-milvus-data`，挂到 `/var/lib/milvus`；19532/9093 仅绑定宿主回环。停服前 healthy，lsof 没有该端口已建立的客户端连接，运行中的 Java 进程属于其他项目。真实 SDK 对三个既有 Collection 的 Strong count 均为 0。

正常提交并通过执行环境审批后，`docker stop --timeout 30 ai-interview-e0r2-milvus` 命令退出 0，容器确实停止；**进程退出码 134，关闭阶段再次发生 `panic: context canceled`**。这不是优雅停机成功。确认 Running=false、Restarting=false、OOMKilled=false 后，将停止卷只读挂入无网络临时容器归档。以下恢复证据支持本次空业务向量环境的离线副本可用，不覆盖未刷盘或在途写入。

- 新归档：`/Users/kun/Developer/projects/ai-interview-system/backups/E1E3_20261113/new-milvus-volume.tar`，**81,510,400 字节**。
- SHA-256：`4a28237ee5b9d8a7911c1e9a6d4e9344ac0e069f473e1af78a8f31d4f01e25a4`。
- GNU tar 与停止原卷逐项比较通过；独立新空卷 `ai-interview-e1e3-restore-20261113-data` 解包后再次 tar 比较通过。归档含 64 个普通文件、73 个目录，无符号链接、特殊对象、绝对路径或 `..` 越界路径；逐文件可读。
- 恢复容器 `ai-interview-e1e3-restore-20261113` 使用相同固定镜像、恢复副本卷和备份的 embedded-etcd 配置，独立端口 **19533/9094**。HTTP health 与 Java SDK 通过，三个集合的 ID、Schema、索引、加载状态和 Strong count 与停服前一致。恢复副本已停止，容器和卷保留，不自动删除。
- 原新实例重新启动并恢复 healthy。停服前、恢复副本、原实例重启后三个 SDK 日志逐字节一致，摘要均为 `2a8ea18850796bcbac2972c874426dfacfd542abb3a01812a15658ff3e9eac69`。
- 旧容器 `ai-interview-mac-milvus-1`、旧卷 `ai-interview-mac_ai-milvus-data` 保留。旧归档重算 SHA-256 仍为 `1112ab06387ad2c5c6c3e5dc409f578c63678d729abb601b52a22fdddb98f533`。

一次执行器兼容性错误如实记录：归档及原卷比对成功后，宿主 Python 缺少 `hashlib.file_digest`，脚本退出 1。改用流式 SHA-256 继续验证**现有归档**，未覆盖归档、未改原卷；验证及恢复全部完成后才开放 B。关闭 panic、embedded-etcd 启动竞态和长期可靠性仍未修复；遇到后续不一致或恢复失败仍须停止写入。

## B．新建隔离环境：PASS

MySQL 实例为 `127.0.0.1:3308`，服务 8.0.45，UUID `85dd1e00-cf98-11f1-8677-9eb9380f25e7`；容器内部端口 3306。现场确认 evaluation Schema 不存在后，执行已审阅的 `docs/environment/E1_EVALUATION_SCHEMA_20261113.sql`。其 SHA-256 为 `1288682adc24b1abe40add640a5638d31a2b40b4c542d6eba41125fec33ea431`，与正式 SQL 仅建库/USE 前缀不同，没有迁移、DROP、清空或覆盖。

实际新对象为 **`ai_interview_evaluation` 和以下 13 张表**：user、question、question_scoring_point、interview_session、interview_question、interview_answer、answer_evaluation、knowledge_document、knowledge_chunk、rag_retrieval_batch、rag_hit_log、interview_report、user_weakness。

创建后各表均为空。现场 SHOW CREATE 对当前正式 SQL 的归一化比较通过：**163 列、42 个索引（13 PK、12 UNIQUE、17 普通）、15 FK、42 CHECK**。普通索引包含 InnoDB 为外键补充的 `interview_question.question_id` 索引。核对内容包括 `interview_answer.attempt_id`、`knowledge_document.processing_attempt_id`、request/question/answer/session 幂等唯一键、会话 `version`、字段类型/顺序、外键及状态约束。没有使用缺 attempt 字段的历史 `ISOLATED_SCHEMAS.sql`。

首次将原始 SHOW CREATE 文本与旧测试库直接比较时退出 1，差异只有旧库历史 AUTO_INCREMENT 计数、ASCII 状态字面量的 `_utf8mb4`/`_latin1` 标记。独立复核确认没有字段、列顺序或约束差异；只归一化这两类表示差异后通过，并另用正式 SQL 比较器验证。未改变数据库结构来迎合检查器。

Milvus 新建 **`http://127.0.0.1:19532/default/knowledge_chunk_vectors_evaluation`**。先检查不存在，再调用生产 `MilvusCollectionInitializer` 创建并进入既存 Schema 校验；五个字段、vector_id 人工主键、禁动态字段、1024 维、COSINE/AUTOINDEX 通过。初次检查在创建后遇到加载尚未完成，Java 退出 1；没有重跑创建。随后只读校验器先要求对象已存在，再验证 Schema/index、loaded=true 和 Strong count=0，通过。

`application-evaluation.yaml` 和 `EnvironmentIsolationInitializer` 保留原样。其隔离检查限制逻辑库/Collection，不固定物理主机端口或 Milvus database；本次执行器另行固定上述端点、`default` 和 MySQL UUID。实际应用上下文只启用 `evaluation`，禁命令行覆盖及外部环境属性源，禁 SQL 自动初始化，使用正式环境隔离初始化器；Milvus/DeepSeek 业务开关为 false，Embedding 指向不可用 loopback 占位端点，无真实模型密钥。该配置用于题库导入，不冒充真实 RAG 已启动验收。

## C．冻结题库原子导入：PASS

输入 JSON SHA-256：`ce6fff9bd42b064009b01c0300e2da5da16e73d04c363e3cf7f2b351cc1b6fd3`；Markdown：`6278fd0ca2bf48c6256fa9bfd1f72ffa1925bb1a70eb2bb11fa67c5f8a06e296`。离线审查确认 139 项正文和 97 个评分点的顺序/类型/权重一致，未发现归一化重复，每题至少一个 CORE 点、启用权重和 100。EASY/MEDIUM/HARD 分别 5/7/9 题，每档必选分类齐全。

一次性执行器为 Git 忽略目录中的 `QuestionImport.java`。调用链：冻结 SHA 校验 → Jakarta Validator → evaluation-only Spring 上下文 → 物理端点/UUID/13表校验 → 外层 `TransactionTemplate` → Spring 代理 `QuestionAdminService.createQuestion` → 正式 MyBatis Mapper → 读回真实题库 → `InterviewQuestionPlanSnapshotService` → `InterviewQuestionSnapshotFactory` 内存快照 → 提交 → 独立 JDBC 复核。没有手写业务 INSERT。

三个模式分开执行，各退出 0：

1. **rollback**：空库真实写入前 20 题/93 点后，以最后一题的临时非法权重触发正式 Service 拒绝，整个外层事务回滚。独立新连接确认 13 表全部为 0。源文件未改；自增序列产生间隙是正常行为，不重置计数器。
2. **import**：再次锁定并确认所有表为空，单事务写入 21 题/97 点，核对生成 ID、题干、完整参考答案、分类、难度、状态、时间字段、评分点顺序/类型/正文/权重。事务内生成 21 个 MAIN 内存快照并反序列化核对 97 个评分点后提交；独立连接确认 21/97，其余 11 表为 0。
3. **verify**：单独只读事务重新读取三个 5/7/9 题计划和全部内容/快照，再用独立 JDBC 连接核对行数。没有持久化任何用户、会话、面试题快照、答案或报告。

### H01 单独修订建议（未应用）

当前 SP4（25 分）及参考答案把“不能 `instanceof List<String>`”表述得过于绝对。Java 21 应区分静态类型允许的类型测试和运行时可验证的元素实参。建议保留题号、权重和“至少两项限制”，改为：**不能用 instanceof 确认运行时元素类型实参；Object 值可以检查 List<?>；在静态类型允许时，List<Integer> 值可测试 instanceof ArrayList<Integer>，运行时仍只检查容器类型。** 参考答案同步使用相同口径。[JLS 21 §15.20.2](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.20.2)

本轮遵守冻结输入原样导入，没有默改原文或单独禁用 H01。**真实评分使用 H01、或将它计入 D1 有效内容验收前，须审阅修订并通过正式整题替换路径更新。** 当前 C 的 PASS 是数据导入、事务和快照验收，不是此缺陷已解决。

## D．真实 RAG 准备：离线 PASS，付费调用 BLOCKED

试入库仅选 `docs/rag/source-documents/e01_java21_interface_vs_abstract_class.md`，Java SE 21、JAVA_BASIC，对应 E01。已逐项核对单继承、多接口、抽象类构造与实例状态、接口 public static final 字段、default/static/private 方法；设计选择保留为工程判断。[JLS 21 第8章](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html) · [第9章](https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html)

直接运行现有 `KnowledgeTextChunker`，不启动应用、不读取模型密钥：规范化文档 1612 个 UTF-16 单元、3162 UTF-8 字节，SHA-256 `4b82a2724e53946fffa082560fde1c348201114ac4f490e409c2ddbba5071de0`；两块分别为 1178/460 个 UTF-16 单元，入模文本含重叠共 **1638 个 UTF-16 单元、3188 UTF-8 字节**。首块包含四个评分点证据，次块包含常见误解和来源；字节和字符不是实际 token 计数。

单独待授权的预算范围：**阿里云百炼 DashScope，qwen3.7-text-embedding，2 条输入，1 次同步 /embeddings 请求，1024 维 float，batchSize≥2，profile=qwen3.7-text-embedding-1024-dense-v1，费用上限 ¥0.15**。估算预留 4000 输入 tokens，按核验时较高公开价 ¥0.525/百万 tokens 为约 ¥0.0021；按两条各最大 128000 tokens 的接口边界为 ¥0.1344。预算上限不是实际消费，也不是当前已获授权；不依赖免费额度。执行时须再确认服务地域、端点及价格未变。[模型价格](https://help.aliyun.com/zh/model-studio/qwen3-7-text-embedding) · [同步接口](https://help.aliyun.com/zh/model-studio/text-embedding-synchronous-api)

授权后使用 `KnowledgeDocumentAdminService.uploadDocument → KnowledgeDocumentProcessingService.processDocument`，串行上传1篇后显式处理；不另写向量业务实现，不把模型调用包入数据库长事务。既有处理 attempt 领取、短事务提交 READY、按 attempt 精确补偿和 stale 恢复机制已静态核查，未在本轮用真实模型验证。提交结果未知时保留现场并双向对账，禁止盲目重试；旧任务迟到或补偿失败仍可能留下孤儿。超时配置 3 秒连接/20 秒读取、单次应用尝试；本次预算不包含再次 process、付费检索或 DeepSeek。

真实试入库验收应核对1篇 READY v1、2个 ACTIVE Chunk、2条 Milvus 向量，双向匹配 document ID、chunk index、vector ID、model/profile、hash及1024个有限浮点值，并验证索引与Strong读；可复用已生成向量自检以避免额外付费查询。本轮尚未上传、处理或调用模型，故没有这类成功证据。

## 测试、终态保护与未执行事项

- Maven/Surefire 第1组 **123/0/0/0**：EnvironmentIsolationInitializerTest 23、QuestionAdminServiceTest 53、InterviewQuestionSnapshotFactoryTest 2、MilvusCollectionInitializerTest 18、KnowledgeTextChunkerTest 27。
- 第2组 **27/0/0/0**：AdminQuestionControllerTest 20（含401/403/管理员/非法请求）、CreateQuestionRequestValidationTest 7。两组无重叠，共 **150/0/0/0**，均 BUILD SUCCESS，属于 Unit/Mock/MockMvc。
- 第1组命令：`./mvnw -B -ntp -o -Dtest=EnvironmentIsolationInitializerTest,QuestionAdminServiceTest,InterviewQuestionSnapshotFactoryTest,MilvusCollectionInitializerTest,KnowledgeTextChunkerTest test`。
- 第2组命令：`./mvnw -B -ntp -o -Dtest=AdminQuestionControllerTest,CreateQuestionRequestValidationTest test`。运行前清除 RUN_REAL_*、MILVUS_ENABLED、DEEPSEEK_ENABLED。XML 独立归档，未用历史数量替代。
- 真实 MySQL 手工 Java 验证为 rollback/import/verify **3阶段/0失败/0错误/0跳过**，不是JUnit。失败注入被预期捕获并验证回滚，记为成功场景。
- 真实 Milvus 四次完整盘点（before/restored/restarted/final）退出均0；独立恢复、既存目标Schema验证通过。创建后的首次加载检查退出1，随后只读复核退出0，分别保留日志，不隐藏初次错误。
- 全量 `mvn test` **未执行**：现有普通 Mapper/事务用例会写 `ai_interview_test`，与本轮明确禁止写原两库相冲突。没有改测试目标、关闭保护或把 guarded 测试强制启用；上述选定测试实际 skipped=0，不虚报19项跳过。
- 终态原两库完整 mysqldump 与入场文件**逐字节相同**，SHA-256 `3048c1b5926ecac9ad5aeb9906d23366a4bba556e5acd123c071fe32c76165e8`，包含原有 user 行。新 evaluation 只有21题/97点；知识文档/Chunk为0，向量Strong count为0，属空RAG一致性，不是已有知识入库成功。
- 三个既有 Collection 的完整元数据/计数块前后完全相同；旧卷和旧容器未操作。终态新 Milvus 与 MySQL healthy，恢复副本和旧 Milvus 停止。恢复副本卷保留，占用少量磁盘，未自动删除。
- 实际收费 Embedding **0**、DeepSeek **0**、完整AI面试 **0**。没有长期压力、断电、未刷盘写入、真实收费故障恢复或旧实例恢复证据。

## Git、交付文件与后续准入

HEAD 保持 `0c6f92afe331954f7cea37d33db688fe781cbf2c`。本次 SSH 443 的 `git ls-remote` 因 publickey 认证失败，随后 GitHub 连接器只读查询 `main` 实时为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，与缓存一致，本地仍领先8。未 fetch、改引用、暂存或提交推送；本批无 commit SHA。没有把本地完成称为已发布。

正式交付路径：

- `/Users/kun/Developer/projects/ai-interview-system/docs/verification/E1_E3_REAL_DATA_EXECUTION_20261113.md`：本分阶段验收及最终总结。
- `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/IMPROVEMENT_LEDGER.md`：追加E1–E3事实，不改历史结论。
- `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/SOURCE_LEARNING_BACKLOG.md`：新增3条待学习项，不认定开发者已掌握。
- `/Users/kun/Developer/projects/ai-interview-system/backups/E1E3_20261113/`：0700、Git忽略的执行证据及备份目录；普通文件0600。内含新卷归档、配置、容器快照、SQL快照和dump、SDK日志与Java源码、一次性导入器、3个导入日志、150项测试XML、离线RAG清单。`artifact-manifest.json`列出每个生成文件的实际绝对路径、字节数和摘要；不得公开上传该目录。
- `/Users/kun/Developer/projects/ai-interview-system/backups/E1E3_20261113/mysql-evaluation-after.sql`：导入后的逻辑备份；未做该MySQL dump的服务级恢复演练。A的Milvus归档是**建evaluation前**的恢复点，不能称为收费入库后的完整跨库备份。
- `/Users/kun/Developer/projects/ai-interview-system/backups/E1E3_20261113/rag-pilot-manifest.json`：精确试入库文本指纹、切片证据、预算与authorized=false。

下一阶段准入：

1. **D1 题库扩容**：先审阅H01修订，新增42题完整题干/参考答案/评分点经来源、唯一性及权重审查，再分批事务导入并对账；目标63题不是本轮已完成数量。
2. **D2 随机组卷**：当前仍为既有确定性选择。单独设计并验证难度/分类覆盖、固定种子可重现、无重复、候选不足拒绝、并发幂等及创建时一次性冻结快照；候选池须足以体现随机性，不因本轮环境完成而自动开发。
3. **D3 RAG扩容**：先获上述单次预算授权、核验实际服务配置，1篇试入库达到READY/ACTIVE/双向对账且无孤儿；保留失败恢复和备份路径后再逐批导入现有21篇。新增30篇至约51篇须先离线核查来源、版本、内容与切片，并另核预算；不自动启用DeepSeek。
