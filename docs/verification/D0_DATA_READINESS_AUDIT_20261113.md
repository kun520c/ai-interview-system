# D0｜真实数据与多用户测试准备审计

执行日期：2026-11-13（Asia/Shanghai）。审计基线：`0c6f92afe331954f7cea37d33db688fe781cbf2c`。

**后续 E0 补审：MySQL 已恢复并查得当前数据，Milvus 仍不可用；详见第 7 节。** 以下保留 D0 执行时的原始结论与授权边界。

**离线审计与设计已完成；MySQL、Milvus 实时数据核验为 BLOCKED。** 本报告不认定当前数据库已有 21 道有效题或 52 个 Chunk。用户补充指令已确认：按现有配置记录阻塞，不创建数据库、不启动或重建容器、不更改连接配置，继续离线交付。

## 1. 范围、Git 与既有文件保护

- 已读取 `AGENTS.md`、两份工程清单、[C6 收口](C6_RELIABILITY_PHASE_CLOSEOUT_20261113.md)、台账中的 [C7 记录](../engineering/IMPROVEMENT_LEDGER.md#c7)，以及题库、组卷、快照、管理接口、Mapper/Schema 和 RAG 处理/检索调用链。
- 本地分支 `main`，HEAD 如上；C7 最后文档提交就是该 SHA，累计源码提交为 `71aeb22f7da906660763e9efb1100a20df7705c9`。本轮 GitHub 连接器实时读取远端 main 为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，本地相对该提交 **0 落后 / 8 领先**。只核验 main，不推断其他分支；未 fetch/add/commit/push。
- 入场 5 项：`mvnw` 100644→100755；未跟踪 `.DS_Store`、`compose.mac.local.yaml`、`docs/.DS_Store`、`docs/design/MQ_ARCHITECTURE_FEASIBILITY_REVIEW_20261113.md`。没有覆盖或归并为本轮工作。
- 入场 999 个跟踪/非忽略文件建立 SHA-256 和权限清单；index 摘要为 `bb9a6b94106e07a0d9d80088849305560d7748958a4616cda70554700f9989af`。仅允许四份新报告及两份工程清单变更。忽略的 `.env` 只在内存解析配置，未输出密码、Token 或 Key。
- C6 的 `1832/0/0/19` 后端、`336/0/0/0` 前端是历史运行；C7 是独立导出版本编译/类型检查，不等于重跑功能测试。本次仅阅读这些验收事实，不重新认证其全部原始归档，不将历史数字算入 D0 测试。

## 2. 实际服务目标与连接证据

### 2.1 配置不是在线事实

当前忽略的 `.env` 声明 `SPRING_PROFILES_ACTIVE=local`，MySQL 为 `jdbc:mysql://127.0.0.1:3308/ai_interview?...`，测试库为同端口 `ai_interview_test`；Milvus collection 为 `knowledge_chunk_vectors_local`。`application.yaml` 的 Milvus URI/default database 对应 `localhost:19530/default`。`compose.mac.local.yaml` 的宿主端口映射与此相符。

当前 Codex 子进程未继承 DB_URL/DB_USERNAME/DB_PASSWORD、MILVUS_URI、SPRING_PROFILES_ACTIVE；`application-local.yaml` 不存在。Spring 不会仅因仓库有 `.env` 就自动加载全部变量。因此以上是**已找到的本地配置目标**，不是一个正在运行的 Spring 进程的有效配置证明。

历史 Phase 4D4/4D5 使用 `localhost:3306/ai_interview`；不能将历史 Windows/其他实例导入记录套用到当前 Mac 3308 数据卷。

### 2.2 独立只读端口核验

2026-11-13 **19:28:58 +08:00**，在获得本机网络读取权限后，以 Python `socket.create_connection(("127.0.0.1", port), 2)` 探测：

| 端口 | 目标 | 实际结果 |
|---|---|---|
| 3308 | 当前 MySQL 配置 | `ConnectionRefusedError:61` |
| 3306 | 历史默认端口辅助检查 | `ConnectionRefusedError:61` |
| 19530 | Milvus API | `ConnectionRefusedError:61` |
| 9091 | Milvus 健康端口 | `ConnectionRefusedError:61` |
| 8080 | 后端常用端口辅助检查 | `ConnectionRefusedError:61` |

首次沙箱内探测是 `PermissionError:1`，不能据此判断停服；上表为解除该限制后的独立结果。3308 没有可连接的 TCP 服务，尚未到 MySQL 握手、认证或 SELECT 阶段；这不证明数据库为空、密码错误或卷内数据不存在。8080 仅为辅助端口，不据此推断所有后端进程。

只读 Docker 元数据提供补充证据：

- `ai-interview-mac-mysql-1`：mysql:8.0.45，exited/退出码 0，2026-11-02 15:00:17 +08:00 停止；3308→3306，数据卷 `ai-interview-mac_ai-mysql-data`。
- `ai-interview-mac-milvus-1`：milvusdb/milvus:v2.6.24，exited/退出码 134，同日约 15:00:17 停止；19530/9091，数据卷 `ai-interview-mac_ai-milvus-data`。退出码不能单独说明故障原因，本轮不诊断或重启。
- 其他项目运行中的 MySQL 不作为替代目标；未挂载或直接读取停止容器的数据目录。

### 2.3 三种数据口径

| 对象 | 当前仓库离线事实 | 当前实时服务数据 | 历史或计划 |
|---|---|---|---|
| 题目 | 21 个题目定义，E/M/H=5/7/9；JSON 未包含 status | **BLOCKED：数量/状态/分布未知** | 历史导入 21 题；规划最终 15/21/27=63 题 |
| 评分点 | 97 个定义；每题合计 100 | **BLOCKED：启用点/权重/孤儿点未知** | 新 42 题评分点尚未编写，不编造最终点数 |
| 知识文档 | 21 篇 Markdown 源文件 | **BLOCKED：READY/FAILED 等状态未知** | 规划保留/修订 21 篇 + 新增 30 篇 = 51 篇 |
| Chunk | 当前源码离线 dry-run 52 个；未持久化 | **BLOCKED：MySQL 行数/内容/版本未知** | 历史入库报告记录 52 行 |
| Milvus | 配置/源码约定 5 字段、1024/COSINE/AUTOINDEX | **BLOCKED：Collection 存在、Schema、实体数和对应关系均未知** | 历史报告 52 vectors；不作为现状 |

## 3. 仓库题库审计

JSON SHA-256：`ce6fff9bd42b064009b01c0300e2da5da16e73d04c363e3cf7f2b351cc1b6fd3`。逐项核对 Markdown 中题干、参考答案、评分点正文，**139/139 文本存在**；不是仅比较两份文件标题。

逻辑题号 E01～H09 取自 Markdown，JSON 本身没有 bankId、数据库 questionId 或 status。下面的“合格”仅指离线结构校验，H01 有需要修订的版本措辞；启用状态须另查 MySQL。

| 题号 | 分类 | 难度 | knowledgePoint | 评分点数 | 按原顺序权重 | 权重和 | CORE 数 |
|---|---|---|---|---:|---|---:|---:|
| E01 | JAVA_BASIC | EASY | `interface_vs_abstract_class` | 4 | 30/25/25/20 | 100 | 1 |
| E02 | JAVA_COLLECTION | EASY | `arraylist_vs_linkedlist` | 4 | 30/25/25/20 | 100 | 1 |
| E03 | SPRING | EASY | `ioc_di` | 4 | 30/25/25/20 | 100 | 1 |
| E04 | MYSQL | EASY | `transaction_acid` | 5 | 20/20/20/20/20 | 100 | 1 |
| E05 | NETWORK | EASY | `tcp_vs_udp` | 4 | 30/25/20/25 | 100 | 1 |
| M01 | JAVA_BASIC | MEDIUM | `equals_hashcode_contract` | 4 | 25/25/30/20 | 100 | 1 |
| M02 | JAVA_COLLECTION | MEDIUM | `hashmap_put_resize` | 5 | 25/20/25/20/10 | 100 | 1 |
| M03 | JVM | MEDIUM | `runtime_data_areas` | 5 | 20/20/25/20/15 | 100 | 1 |
| M04 | JAVA_CONCURRENCY | MEDIUM | `volatile_jmm` | 4 | 30/25/30/15 | 100 | 1 |
| M05 | SPRING | MEDIUM | `spring_aop_proxy` | 4 | 25/20/35/20 | 100 | 2 |
| M06 | MYSQL | MEDIUM | `innodb_index_structure` | 5 | 20/20/25/20/15 | 100 | 1 |
| M07 | REDIS | MEDIUM | `redis_data_structures` | 5 | 20/20/20/20/20 | 100 | 1 |
| H01 | JAVA_BASIC | HARD | `generic_type_erasure` | 5 | 25/20/20/25/10 | 100 | 1 |
| H02 | JAVA_COLLECTION | HARD | `concurrent_hashmap` | 5 | 20/25/30/15/10 | 100 | 1 |
| H03 | JVM | HARD | `g1_gc` | 5 | 25/25/15/20/15 | 100 | 1 |
| H04 | JAVA_CONCURRENCY | HARD | `thread_pool_design` | 5 | 25/20/20/20/15 | 100 | 1 |
| H05 | SPRING | HARD | `transaction_boundary` | 5 | 20/20/20/25/15 | 100 | 1 |
| H06 | MYSQL | HARD | `mvcc_read_view` | 5 | 20/20/25/20/15 | 100 | 1 |
| H07 | REDIS | HARD | `distributed_lock` | 5 | 25/25/20/15/15 | 100 | 1 |
| H08 | NETWORK | HARD | `tcp_connection_lifecycle` | 5 | 20/20/20/20/20 | 100 | 1 |
| H09 | PROJECT_SCENARIO | HARD | `concurrent_inventory_idempotency` | 4 | 30/25/20/25 | 100 | 1 |

结构复核：21/21 非空题干/参考答案/知识点；97/97 类型合法且权重在 1～100；21/21 权重和为 100，均有 CORE；题干、knowledgePoint、完整参考答案的完全重复组均为 0。评分点类型分布 CORE=22、KEY=38、PRINCIPLE=19、INTERNAL=8、ADVANCED=10。结构通过不等于内容完全准确、题目难度已由真实候选人校准或数据库已启用。

分类总数：JAVA_BASIC/JAVA_COLLECTION/SPRING/MYSQL 各 3；JVM/JAVA_CONCURRENCY/REDIS/NETWORK 各 2；PROJECT_SCENARIO 1。每个“难度 × 必选分类”当前只有 1 个仓库候选。

### 3.1 内容结论与修订项

- **H01-SP4 版本条件缺失**：题库参考答案和评分点将“不能 instanceof List<String>”作为一般限制。Java 21 是否允许需看操作数静态类型；不能用这句话惩罚正确描述可用特例的回答。现有 H01 知识文档反而已说明 Java 16 后边界，题库和证据措辞不完全一致。
- 临时编译实证：`boolean test(ArrayList<String> x) { return x instanceof List<String>; }` 在 Temurin 21.0.13 编译成功；将参数改为 `Object x` 编译失败，符合预期。源码和 class 只在 `/private/tmp`。规范依据：[JLS 21 §15.20.2](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.20.2)。后续修订建议限定为“Object 静态类型不能直接检查 List<String> 元素实参”，保留题目身份和主题；本轮不改题库。
- M02/H02 明示 JDK 8，属于有意限定实现版本，并非因项目 Java 21 就自动作废；新增题以 Java 21 为主，保留这两题的版本标签。M02 文档主链接仍是 8u/master，后续应固定源码 tag/commit，而不能将移动分支当不可变来源。
- E02 已区分“已定位节点后改链接”与“按索引先遍历”，未发现“增删都 O(1)”错误；H03 把暂停时间视为目标；H04 不把线程池当持久队列；H05 保留默认回滚条件；H06 区分快照读/锁定读；H07 保留租约失效边界；H09 明示假设库存场景，不能冒充已实现模块。
- E01→M01→H01、M02→H02、M05→H05、E04→M06→H06 属于基础/机制/边界的递进，存在主题相邻但不判为重复题。尚未做独立人工难度盲评、答题时长或语义重复量化；其结果均为待验证。
- 因 H01 措辞待修订及数据库未知，**“已有 21 道有效题”是扩容的条件假设，不是本轮数据库验收结论**。规划保留 21 个主题位置，H01 审核修正后继续计为原题；若后续判废，则用同难度同分类替换补足，不能让 42 新题的算术掩盖缺口。

### 3.2 当前选题与快照合同

`QuestionMapper.selectEnabledQuestionsForInterview` 只按 difficulty/status 过滤，再 `ORDER BY category ASC,id ASC`；`QuestionPlanSelector.select` 对每个必选分类调用 `findFirstUnselectedByCategory`，余量也按输入顺序补齐。**当前没有随机数调用，选的是每类最前面的可用 ID。** 只有 21 题时三个难度本就各只有一套；即使导入 63 题，不改 selector 仍会固定首题。

创建链：`InterviewController → InterviewService.createOrResume → InterviewSessionService.createSession → InterviewQuestionPlanSnapshotService.prepareSnapshot → QuestionPlanSelector → InterviewSessionTransactionService.createAndStartSession → InterviewQuestionSnapshotFactory`。

先复用活跃 Session；无活跃会话时，在独立 readOnly/REPEATABLE_READ 事务读题和评分点；之后短写事务锁 user 行、再查活跃 Session，竞争失败方返回已有会话。一次写入全部 MAIN 快照、激活首题并推进 Session。题干/分类/knowledgePoint/参考答案/评分点 ID、类型、内容和权重固化到 `interview_question`，后续不能回连最新题库覆盖历史。

`QuestionAdminService` 的创建/整体替换在事务内检查权重和 100、每项 1～100、影响行数和生成键；快照工厂再次校验评分点归属和权重。Schema 没有题干或 knowledgePoint 唯一约束，也没有跨行权重和约束；不能从“表有 CHECK”推断数据无重复或全题合格。详情 Mapper 会返回所有状态的评分点，而组卷读启用点，实时审计必须同时给出两种口径。无需为 63 题扩容新增 Schema。

## 4. RAG 离线结论

当前 21 篇源文件可读；使用**本次 javac 编译的当前生产 KnowledgeTextChunker**离线处理全部文件，得到 52 个 Chunk，52/52 的题号-序号及 UTF-16 长度匹配历史审查表。未启动 Spring、数据库或模型。长度一致不是历史内容哈希相等证明；历史完整 JSON digest/向量清单缺失，因此没有声称重建历史跨库验收。

未发现将任一道完整 referenceAnswer 原文作为知识正文的复制，规范化后跨文档 ≥250 字长段落完全重复组为 0；这些只排除精确复制，不能证明无语义过拟合。旧文档含 Target question/Bank ID 元数据，有题目定向语料偏差，后续应采用主题文档与独立留出问题验证。

历史审查表的 97/97 点覆盖可建立源码内容映射，但 H01-SP4 须先消除版本歧义。历史 8 LOW、6 REFERENCES_ONLY、26 上下文 WARN 属于旧人工标注，本轮长度复核不等于重新盲评每个质量标签。完整矩阵、新 30 篇来源/版本与 PDF 路径见 [D3 方案](D3_RAG_CORPUS_EXPANSION_PLAN_20261113.md)。

**当前缺失的历史机器证据**至少包括：`docs/rag/full-bank/PHASE_4D1_SCORING_POINT_SOURCE_MATRIX.json`、`PHASE_4D4_REAL_INGESTION_MANIFEST.json`、`PHASE_4D4_CROSS_STORE_INTEGRITY.json`，以及 `docs/rag/evaluation/PHASE_4D5_FULL_BANK_GOLDEN_RELEVANCE.json`、`PHASE_4D5_FULL_BANK_BASELINE_RESULTS.json` 和整个 evaluation 目录。仅存在相关 Markdown 叙述，不从其指标反推当前真实命中率。是否还在 Windows 原环境待后续核实。

## 5. 环境恢复后的只读补审清单（本轮未执行）

由后续明确环境准备任务处理已有服务/迁移问题；先确认目标和备份，不自动重建卷或应用 DDL。服务可读后，应在同一短期只读一致性快照中执行以下 SQL。下列只是模板，不是已执行结果：

```sql
SELECT DATABASE() AS db_name, @@hostname AS server_host,
       @@port AS server_port, VERSION() AS mysql_version,
       @@transaction_isolation AS tx_isolation, @@session.time_zone AS time_zone;
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;
SELECT difficulty, category, status, COUNT(*) AS n
FROM question GROUP BY difficulty, category, status;
SELECT q.id, q.difficulty, q.category, q.knowledge_point, q.status,
       COUNT(p.id) AS all_points,
       SUM(CASE WHEN p.status='ENABLED' THEN 1 ELSE 0 END) AS enabled_points,
       SUM(CASE WHEN p.status='ENABLED' THEN p.weight ELSE 0 END) AS enabled_weight,
       SUM(CASE WHEN p.status='ENABLED' AND p.point_type='CORE' THEN 1 ELSE 0 END) AS enabled_core
FROM question q LEFT JOIN question_scoring_point p ON p.question_id=q.id
GROUP BY q.id;
SELECT difficulty, category, knowledge_point, COUNT(*) AS n
FROM question GROUP BY difficulty, category, knowledge_point HAVING COUNT(*)>1;
SELECT SHA2(question_content,256) AS content_digest, COUNT(*) AS n
FROM question GROUP BY SHA2(question_content,256) HAVING COUNT(*)>1;
SELECT processing_status, category, COUNT(*) AS n
FROM knowledge_document GROUP BY processing_status, category;
SELECT status, embedding_model, embedding_version, COUNT(*) AS n
FROM knowledge_chunk GROUP BY status, embedding_model, embedding_version;
SELECT d.id, d.title, d.document_version, d.processing_status, d.content_hash,
       COUNT(c.id) AS chunks
FROM knowledge_document d LEFT JOIN knowledge_chunk c ON c.document_id=d.id
GROUP BY d.id;
SELECT c.id, c.document_id, c.document_version, c.chunk_index, c.vector_id,
       c.embedding_model, c.embedding_version, c.status,
       SHA2(c.content,256) AS content_digest
FROM knowledge_chunk c ORDER BY c.document_id,c.document_version,c.chunk_index;
ROLLBACK;
```

后续还需只读获取 question/point 的必要正文，在内存按 `(difficulty, category, knowledge_point)` 关联仓库逻辑题号，再逐字段比较题干/答案/有序评分点；报告分“唯一完全匹配、唯一但变更、多重匹配、库内新增、仓库缺失、禁用”。不要按历史主键认同一题，也不要将别名或同义改写自动认定重复。

Milvus 补审只允许 list/describe/query：database/collection 存在性、动态字段和主键、5 字段长度/维度、COSINE/AUTOINDEX、实际一致性设置；分页读取 `vector_id/document_id/chunk_index/embedding_version`，和 MySQL 可检索 ACTIVE + READY + 当前 documentVersion + 相同 model/profile 集合双向比对。分别报告原始实体数、有效身份数、孤儿/缺失/版本不符；只做 count 无法证明一一对应。不加载缺失集合、不通过 Spring 启动初始化器建集合、不调用 Embedding 或相似搜索。暂停写入或记录前后水位以识别跨库快照差异。

## 6. 本轮验证与交付状态

- Python 离线题库检查：21 题 / 97 点；139/139 正文对应；结构异常 0、精确重复组 0。
- 真实生产 chunker 的独立临时编译退出 0；离线 21/21 文档成功，52 Chunk；历史长度对照 52/52，差异 0；这不是 MySQL/Embedding/Milvus 集成测试。
- Java 21 泛型边界编译探针 2 项符合预期：1 个应成功 exit 0，1 个应拒绝 exit 1。后者是负例通过，不能写成项目编译失败。
- 最终文档静态校验 **20 项通过、0 项失败**：42 题编号/分布、21 个分类难度桶各3候选、30篇新增文档与42题映射、97个旧SP映射、调用预算算术、68条学习记录、文件链接/表格/围栏、保全和敏感模式检查；这是自定义检查，不是JUnit回归。`git diff --check` 与 `git diff --cached --check` 均退出0。
- 入场999文件中，除两份授权工程清单外的 **997/997 文件正文及权限保持**，包括5项用户改动；HEAD/index不变。四份新报告不包含在入场文件数中，原验收报告和题库/语料均未修改。
- JUnit/Surefire/Vitest、全项目构建、真实 HTTP 面试、多用户压测、真实 DeepSeek/Embedding：**NOT_RUN**，T/F/E/S 不适用；不把未运行写成全部跳过。SQL/Milvus 数据核验：**BLOCKED**，实际数据查询 0。模型调用 0、数据导入 0、Milvus 写入 0。
- 新报告和台账修改均未暂存、未提交、未推送；D0 无提交 SHA。学习条目一律待学习（未确认）。
- 临时复核证据：`/private/tmp/ai-d0-20261113/` 的 entry-manifest.json、port-probes-host.json、bank-audit.json、chunk-dryrun.json、offline-audit-summary.json、generic-boundary-probes.json；临时文件不作为永久数据库证据或发布附件。

正式交付四份文档及工程清单见下方；所有路径均在本仓库指定 docs 目录：

1. `/Users/kun/Developer/projects/ai-interview-system/docs/verification/D0_DATA_READINESS_AUDIT_20261113.md`：本次事实、阻塞、审计及准入。
2. `/Users/kun/Developer/projects/ai-interview-system/docs/verification/D1_QUESTION_BANK_EXPANSION_PLAN_20261113.md`：42 个新增主题与随机组卷设计。
3. `/Users/kun/Developer/projects/ai-interview-system/docs/verification/D3_RAG_CORPUS_EXPANSION_PLAN_20261113.md`：现有覆盖、51 篇语料目标与来源规范。
4. `/Users/kun/Developer/projects/ai-interview-system/docs/verification/D4_MULTI_AGENT_TEST_PLAN_20261113.md`：质量测试和多用户可靠性两套计划。
5. `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/IMPROVEMENT_LEDGER.md`：追加 D0 工程事实及 Git 状态。
6. `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/SOURCE_LEARNING_BACKLOG.md`：新增待学习条目。

下一步是独立环境准备/只读数据补审，再审核 H01 修订和新增主题范围；是否实施随机组卷、编写完整题目、入库或真实测试均等待后续明确任务。本轮至此停止在离线方案边界。

## 7. E0 实时补审（2026-11-13，取代上文的实时阻塞状态，不改写 D0 历史记录）

详见 [E0 环境与数据补审](E0_TEST_ENVIRONMENT_READINESS_20261113.md)。E0 经确认原容器和专用卷后恢复 MySQL；当前 `127.0.0.1:3308` 为 MySQL 8.0.45、容器内部端口 3306。只读一致性快照确认 `ai_interview` 的 13 张核心表中 `user=1`、其余 12 表均为 0，`ai_interview_test` 的 13 表均为 0。题目、评分点、知识文档、chunk、会话、答案和评价均为 0；难度/分类/状态和权重分组为空，不能声称实际题目权重合格。仓库 21 题和 21 篇文档均无数据库匹配。36 项完整性查询在空样本上返回零异常，并不证明入库后完整。管理权限只读检查表明该 MySQL 实例无 `ai_interview_evaluation`；不能推断其他机器或实例的数据状态。

原 Milvus 容器启动后发生 embedded etcd `leader changed` 并反复重启，已停止以终止循环；原卷保留，故障原因和卷内容尚未确认。真实 collection/schema/索引/向量和跨库 ID 一致性均未核验，整体仍 **BLOCKED**，不是 EMPTY_READY。E0 的下一步是先备份停止的完整 Milvus 卷并在副本诊断，或另拟独立 evaluation 环境；创建库、schema、数据/向量迁移、模型调用均为后续独立写入步骤。本次没有数据导入、模型调用或业务代码修改。
