# Phase 4A — Production Query 与 Golden Relevance 冻结报告

日期：2026-10-05T23:58:16+08:00（Asia/Shanghai）。

**技术冻结与静态一致性验证：PASS。语义审核：APPROVED_WITH_4_LABEL_CORRECTIONS。12项语义审核已闭环，4个标签已校正，未决0项；本次未进入Phase4B，等待单独执行指令。**

本报告记录原始离线标注/只读MySQL取证，以及本次Commander裁决应用。本次仅更新3份Phase4A产物，未重新连接数据库或外部服务；第2、3节数据库证据来自原始冻结。未执行检索、Embedding、DeepSeek/Jev调用、模型评分或任何检索指标计算。没有调整标签来匹配未来结果，也没有设验收阈值。

## 1. 输入与现有路径映射

提示文件的英文路径对应仓库整理后的现有文件；未重建旧路径或修改这些输入。

| 提示路径 | 实际读取路径 |
|---|---|
| docs/rag/PHASE_3B_REAL_INGESTION_REPORT.md | docs/rag/Phase3B-真实知识库入库验证.md |
| docs/rag/PHASE_2C_CHUNK_PREVIEW.md | docs/rag/Phase2C-切片内容预览.md |
| Phase1冻结题库 | docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.md 与同名JSON |
| Phase1导入结果 | docs/question-bank/Phase1D-题库数据库导入验证.md |

canonical Markdown SHA-256：`6278fd0ca2bf48c6256fa9bfd1f72ffa1925bb1a70eb2bb11fa67c5f8a06e296`。

canonical JSON SHA-256：`ce6fff9bd42b064009b01c0300e2da5da16e73d04c363e3cf7f2b351cc1b6fd3`。

## 2. 只读数据库与生产Query Builder

目标仅为localhost:3306/ai_interview。临时Java工具使用直接JDBC，并校验SELECT DATABASE()及四张表的精确数量；没有启动Spring Boot、创建Spring容器或初始化任何Embedding/Milvus/LLM基础设施。EnvironmentIsolationInitializer没有被禁用或修改；此工具不执行应用启动流程。

JDBC连接设为readOnly、REPEATABLE_READ、autoCommit=false，读取结束rollback。最终复核确认MySQL服务端@@session.transaction_read_only=1，transaction_isolation=REPEATABLE-READ。工具的业务SQL仅允许SELECT开头，没有INSERT/UPDATE/DELETE/DDL。凭据仅从既有local配置和Windows用户环境读取到临时子进程，未输出、未写入交付文件、未修改持久环境变量。

首次成功快照：2026-10-05T15:43:09.127763Z；最终成功复核：2026-10-05T15:55:07.955170900Z（UTC）。

所有21道题按difficulty/category/knowledgePoint唯一匹配冻结JSON，逐字比较questionContent、referenceAnswer；97个评分点逐项比较type/content/weight/sortOrder/status。全部题目与评分点为ENABLED，权重每题合计100。顺序与生产Mapper一致：sort_order ASC, id ASC。

将现有6个生产源码文件原样交给JDK21 javac编译到临时目录：EvaluationRagQueryBuilder、EvaluationContext、ScoringPointSnapshot、EvaluationMode、QuestionCategory、QuestionPointType。仅使用本机缓存的项目版本依赖，未下载依赖；未复制或重写query拼接规则。

调用new EvaluationRagQueryBuilder().build(context)，mode=MAIN_ANSWER，question和scoringPoints取DB原文。mainAnswerContent与follow-up内容为null，followUpTargetPointIds为空。真实builder自身的strip和换行行为原样保留；JSON同时保存未改写的DB题干与评分点。没有调用EvaluationContextService的业务读取流程或任何检索适配器。

SHA-256计算对象是builder返回String的UTF-8字节，不额外加尾部换行。全部21条查询在最终独立DB读取后再次用builder生成，文本和哈希与交付一致。

## 3. 冻结语料与身份核查

当前5个knowledge_document均READY/version1/error_message=NULL，14个knowledge_chunk均ACTIVE/version1。文档完整内容、content_hash与原始source-documents逐一匹配；14个切片正文和UTF-16 character count与Phase2C四反引号文本块完全一致。

每个稳定语义key的document_id、mysql_chunk_id、vector_id、chunk_index和长度均与Phase3B身份表匹配；14个vectorId唯一。golden JSON保存完整切片正文及内容SHA-256，便于离线复查。

本阶段未连接Milvus。14个Milvus身份的来源是Phase3B已验证报告与本次MySQL vector_id一致性，不冒充本阶段重新验证了实时Milvus实体。

保留9项P2 overlap-boundary warning和M05-3/H07-3两个LOW retrieval-value尾片，语料中未删除任何切片。M05-3保留自身调用绕过代理的正文证据，H07-3保留token与Lua/EVAL原子释放的正文证据，因此仍有DIRECT标签；不把LOW身份当作预设负标签。

## 4. 必需统计

```text
MYSQL_QUESTION_COUNT: 21
MYSQL_SCORING_POINT_COUNT: 97
KNOWLEDGE_DOCUMENT_COUNT: 5
KNOWLEDGE_CHUNK_COUNT: 14

QUERY_BUILDER_USED: EvaluationRagQueryBuilder (current production source, directly invoked)
PRODUCTION_QUERY_COUNT: 21
QUERY_HASH_COUNT: 21

CORPUS_CHUNK_COUNT: 14
TOTAL_RELEVANCE_JUDGMENTS: 294

POSITIVE_QUERY_COUNT: 9
NO_DIRECT_GOLD_QUERY_COUNT: 12

DIRECT_JUDGMENT_COUNT: 21
RELATED_ONLY_JUDGMENT_COUNT: 34
IRRELEVANT_JUDGMENT_COUNT: 239
UNCERTAIN_JUDGMENT_COUNT: 0

EMBEDDING_CALLS: 0
MILVUS_SEARCH_CALLS: 0
RAG_RETRIEVAL_CALLS: 0
DEEPSEEK_CALLS: 0
JEV_CALLS: 0

MYSQL_WRITES: 0
MILVUS_WRITES: 0
SOURCE_DOC_MUTATIONS: 0
QUESTION_BANK_MUTATIONS: 0
```

原始矩阵为DIRECT20/RELATED_ONLY38/IRRELEVANT236，其中12项待审。最终矩阵21+34+239=294，所有12项已解决，UNCERTAIN=0。

POSITIVE：E04, M01, M02, M04, M05, H05, H06, H07, H09。

NO_DIRECT_GOLD：E01, E02, E03, E05, M03, M06, M07, H01, H02, H03, H04, H08。

这些数量来自语义矩阵，不是Hit@K、MRR、Precision、Recall或相似度测量。

## 5. 跨题判断与证据边界

- M02-1给出hash定位后通过equals识别key的机制，直接支持M01/2499。
- H06-2/3给出RR/RC快照保证或RR快照/locking read的适用边界，直接支持E04/2490中的隔离保证部分，不代表完整ACID覆盖。
- M05-1/2/3给出代理边界和self-invocation机制，直接支持H05/2549。
- HashMap结构不能直接外推CHM的Segment、CAS、桶头锁或协作扩容；JMM也不能代替CHM具体可见性实现。
- 聚簇记录事务元数据不等于完整的B+Tree/主键/回表/覆盖索引说明。
- H09/H06-2按Commander裁决直接支持SP2569允许的事务行锁方案组件；不覆盖H09其他幂等、共同提交或重试评分点。
- Redis锁唯一token不是业务幂等键；H07-2的数据约束背景保持RELATED_ONLY。

DIRECT严格要求正文至少实质支持本题一个评分点；支持评分点的一个明确子机制，不等于该评分点全部覆盖。每个DIRECT摘录均为本切片连续原文，而不是拼接多个片段、扩写正文或把参考链接当证据。

## 6. Commander最终语义审核

决定来源：PHASE_4A_COMMANDER_REVIEW_DECISION.md；SHA-256：`7643df35db15d50c34bd5ba950fdb0757483816f8b17f3d8791c978ff06d16ff`。

| Query | Chunk | 原标签 | 最终标签 | 裁决 |
|---|---|---|---|---|
| E01 | M05-1 | RELATED_ONLY | IRRELEVANT | 代理实现的接口/类差异不提供接口与抽象类设计选择的有用证据。 |
| E03 | M05-2 | RELATED_ONLY | RELATED_ONLY | 接受原RELATED_ONLY判断及现有理由。 |
| E04 | H06-1 | RELATED_ONLY | RELATED_ONLY | 接受原RELATED_ONLY判断及现有理由。 |
| M06 | H06-1 | RELATED_ONLY | RELATED_ONLY | 接受原RELATED_ONLY判断及现有理由。 |
| M07 | H07-1 | RELATED_ONLY | RELATED_ONLY | 接受原RELATED_ONLY判断及现有理由。 |
| H02 | M02-1 | RELATED_ONLY | RELATED_ONLY | 接受原RELATED_ONLY判断及现有理由。 |
| H02 | M04-1 | RELATED_ONLY | RELATED_ONLY | 接受原RELATED_ONLY判断及现有理由。 |
| H04 | M04-1 | RELATED_ONLY | IRRELEVANT | 通用JMM/volatile材料离ThreadPoolExecutor容量、队列、拒绝、可靠性和监控要求过远，不属于本题相关背景。 |
| H04 | M04-2 | RELATED_ONLY | IRRELEVANT | volatile复合更新和同步材料离ThreadPoolExecutor容量、队列、拒绝、可靠性和监控要求过远，不属于本题相关背景。 |
| H05 | H07-2 | RELATED_ONLY | RELATED_ONLY | 接受原RELATED_ONLY判断及现有理由。 |
| H09 | H06-2 | RELATED_ONLY | DIRECT | CORE评分点2569明确接受事务保护下SELECT ... FOR UPDATE方案；本片的locking read、排他行锁和读后写保护机制直接支持该方案组件，但不覆盖H09其余评分点。 |
| H09 | H07-2 | RELATED_ONLY | RELATED_ONLY | 接受原RELATED_ONLY判断及现有理由。 |

原有20个DIRECT判断、21条生产查询与哈希、14个切片正文/身份、指标定义和LOW尾片均保持不变。12项历史不确定性已全部解决。

新增H09/H06-2原文摘录（SP2569）：

> `SELECT ... FOR UPDATE` 是 locking read，不是普通的历史快照读。MySQL 8.0 手册说明，`SELECT ... FOR UPDATE` 读取最新可用数据，并对读取到的行设置排他锁。因此，普通 consistent nonlocking read 与 locking/current read 在可见数据和加锁行为上不同。MVCC 不代表“没有锁”；需要基于读后写保护时，应区分普通快照查询与 `FOR UPDATE` 等锁定读。

## 7. Phase4B指标定义冻结

定义已写入golden JSON的metricDefinitions及人工审核Markdown；本阶段没有计算指标值。

POSITIVE仅DIRECT相关，chunk/document层均冻结Hit@1/3/5、MRR@5、Precision@1/3/5、Recall@1/3/5。宏平均仅使用POSITIVE问题，Precision分母固定K，缺失位置不相关。Document层从未来top5 chunk候选按首次出现去重documentId，文档包含任一DIRECT gold切片即相关；须区别文档相关与实际返回片段相关，不伪称独立文档检索。

NO_DIRECT_GOLD按K=1/3/5冻结FALSE_DIRECT_EVIDENCE_RATE及独立RELATED_ONLY_HIT_RATE/IRRELEVANT_HIT_RATE。前者为至少出现一条RELATED_ONLY或IRRELEVANT返回的问题比例；后两个事件可重叠。空返回记0，语料外身份触发审核。因为这些问题没有DIRECT，非空语料内返回即为非DIRECT证据；指标含义是证据准入诊断，而不是模型事实错误率。未设pass threshold。

## 8. 原始冻结验证结果与执行问题

- 静态校验PASS：21个唯一Bank ID、21个唯一DB question ID、97个唯一评分点ID、21/21查询哈希、14/14完整切片和身份、每题14个唯一判断、总计294。
- 原始冻结DIRECT验证PASS：20/20摘录在对应切片逐字存在；每项至少一个ID且全部属于该题。非DIRECT评分点ID列表均为空。
- 内容验证PASS：人工Markdown逐题显示完整生产query和冻结评分点；汇总分类、三类判断计数、待审项与JSON完全一致；没有实际检索score/rank字段。
- 真实MySQL验证PASS：前后两阶段快照四表数量和完整行摘要一致，21条builder输出一致；rag_retrieval_batch/rag_hit_log均0且未变。
- 文件完整性PASS：839/839个预先存在的非生成文件保持SHA-256不变；新增文件仅在四个授权路径。源码、配置、Skill、已有文档与知识源均未改。
- 临时工具准备阶段曾因未继承DB环境变量而停止一次，随后从既有Windows用户环境读入子进程；JDBC返回LocalDateTime导致一次序列化问题，诊断复现后将时间元数据转为String。修正仅发生在临时工具中，之后编译、只读采集和最终复核均exit0。没有以此修改生产配置或将失败执行冒充成功取证。
- JUnit/Surefire：未运行，tests=0、failures=0、errors=0、skipped=0（表示没有该测试运行，不表示全回归通过）。没有启用RUN_REAL_*或启动前端/后端服务。
- 本阶段不宣称真实检索效果、模型质量、当前Milvus实时状态或完整业务回归已验证。

临时工具清理：COMPLETE。已核实删除本阶段唯一临时目录及Java/Python工具、编译类、DB快照和哈希检查点；未删除其他文件。最终交付仅保留四份授权产物。

## 9. 四份交付及Git状态

| 文件 | 用途 | SHA-256 |
|---|---|---|
| docs/rag/evaluation/PHASE_4A_PRODUCTION_QUERY_SET.json | 21条DB精确生产MAIN query与97个评分点及查询哈希 | 8251e2ee7307b9a03ec258328e7f29d91475d51b640f11a37c4f04d29e029d35 |
| docs/rag/evaluation/PHASE_4A_GOLDEN_RELEVANCE.json | 14个语料身份/正文，294个语义标签、证据、待审项及指标定义 | f3870b3432856dd44efbe384656367b798bb730d4c9313bdc6cee6259ac1cd22 |
| docs/rag/evaluation/PHASE_4A_GOLDEN_RELEVANCE_REVIEW.md | 逐题完整query、评分点、三类判断和边界的人工审核入口 | b12685faf1a79f221ab970b0ff34d01772526ad56972b37ed38fcfe1b5e91c54 |
| docs/rag/PHASE_4A_GOLDEN_SET_REPORT.md | 本阶段执行、验证与Gate报告 | 不嵌入自身哈希 |

本阶段没有暂存、commit或push，也没有修改忽略规则或Git索引。docs的新产物仍受既有.git/info/exclude规则影响，四份Phase4A文件仅存在于本地。

并行Git状态变化：开始时HEAD为e2cab27，三份Jev文档重命名已暂存；最终只读复核发现HEAD为6076a70f18f3062b8faff4fc179cf21f00a95036（chore: organize project documentation and archive historical files），这些重命名已经被该提交记录。本阶段没有执行该提交，未推断其推送状态；不能把Git状态称为完全未变。839个既有非生成文件的前后哈希仍完全一致，已有其他未提交改动仍保留。

停止状态：Commander审核已完成；本次不进入Phase4B、不执行检索、不计算指标、不调参，等待单独执行指令。

## 10. Commander裁决应用验证

本次应用时间：2026-10-05T16:20:39.371456+00:00。840/840个本次修改范围外的非生成文件SHA-256保持不变，包含原生产查询集、源码、配置、既有文档与知识源。

- 294/294矩阵判断一致，每题14个唯一切片；DIRECT21/RELATED_ONLY34/IRRELEVANT239、POSITIVE9/NO_DIRECT_GOLD12、UNCERTAIN0。
- 仅4个标签改变，逐项匹配Commander裁决；原有20个DIRECT判断完整对象未变。
- 全部21/21 DIRECT的评分点ID属于各自问题，摘录逐字存在于对应切片；新增H09/H06-2支持SP2569。
- 生产查询集文件字节未变，21/21查询哈希通过；14/14切片正文与Phase2C预览逐字一致，身份/哈希和指标定义保持不变。
- 人工Markdown保留21条完整查询及97个评分点，显示最终分类。所有live reviewRequired=false、reviewFlag=null、reviewFlags=[]、uncertainJudgments=[]。12项历史裁决单独记录。
- 本次不连接数据库或外部服务，不运行JUnit/Surefire或全回归，不创建临时工具；只修改3份现有Phase4A产物。历史MySQL取证不冒充本次实时复核。

PHASE 4A GOLDEN SET GATE

QUESTION_BANK_21_97_INTACT: PASS
CORPUS_14_INTACT: PASS
REAL_PRODUCTION_QUERY_BUILDER_USED: PASS
PRODUCTION_QUERIES_21_FROZEN: PASS
QUERY_HASHES_21_21: PASS
RELEVANCE_JUDGMENTS_294_294: PASS
DIRECT_LABELS_HAVE_SCORING_POINT_EVIDENCE: PASS
DIRECT_EXCERPTS_VERBATIM_MATCH_CHUNKS: PASS
NO_RETRIEVAL_EXECUTED: PASS
NO_EMBEDDING_EXECUTED: PASS
NO_DB_WRITES: PASS
NO_MILVUS_WRITES: PASS
NO_SOURCE_OR_QUESTION_MUTATION: PASS

PHASE_4A_OVERALL: PASS
