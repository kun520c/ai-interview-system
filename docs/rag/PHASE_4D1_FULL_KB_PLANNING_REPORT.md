# Phase 4D1 Full-KB Planning Report

日期：2026-10-06。当前阶段：**规划技术执行及 Commander H09 来源修订 PASS，等待 Commander 最终核验**。H09 四点已按假设设计改为 SUPPORTED，SOURCE_GAP_COUNT=0；保留当前项目没有库存/商品/订单实现的事实。本补丁仍属于 Phase 4D1，没有编写知识正文或进入 Phase 4D2。

## 1. 本阶段解决的问题及输出

冻结题库为 21 题 / 97 评分点，原官方知识库试点只有 5 篇 / 23 点。新增规划 **16 篇、一题一主文档、74 个评分点**，形成 21/21 的题目覆盖计划。新增评分点逐项保留 frozen 原文、pointType、weight，附官方来源、具体章节、拟文档节及 coverageStatus。规划不等于知识正文已写、已上传、已切片、已入库或完整题库检索效果已验证。

| 规划文件（首次创建，本补丁仅修订） | 用途 |
|---|---|
| [PHASE_4D1_FULL_KB_SOURCE_MANIFEST.md](full-bank/PHASE_4D1_FULL_KB_SOURCE_MANIFEST.md) | 16 题版本/官方来源、74 点可读矩阵、H09 假设设计支持与项目类比边界、45 个采用 URL 的访问记录 |
| [PHASE_4D1_SCORING_POINT_SOURCE_MATRIX.json](full-bank/PHASE_4D1_SCORING_POINT_SOURCE_MATRIX.json) | 机器可读逐点映射、精确冻结内容、源目录、文件哈希、计划与静态计数 |
| [PHASE_4D1_DOCUMENT_DESIGN_PLAN.md](full-bank/PHASE_4D1_DOCUMENT_DESIGN_PLAN.md) | 16 篇标题/文件名/大纲、语义组、切片风险、例子需求、21 主题重叠表 |
| [PHASE_4D1_FULL_KB_PLANNING_REPORT.md](PHASE_4D1_FULL_KB_PLANNING_REPORT.md) | 本阶段结果、限制、核验、Commander 审核项与 gate |

首次规划创建上表 4 份产物且未修改其他既有文件。本次 Commander 补丁：**仅修改上表 4 份规划文件，新建文件 0，知识正文新建 0**。未提交或推送，没有建立临时 helper/script 文件。

## 2. 输入与证据边界

- 冻结银行：`docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json`，SHA-256 `ce6fff9bd42b064009b01c0300e2da5da16e73d04c363e3cf7f2b351cc1b6fd3`，按对应 Markdown 的 E01…H09 标识核对；JSON 没有数据库评分点 ID，计划逻辑 ID 是 `bankId-SP序号`，不得当成真实数据库主键。
- 冻结 pilot：M02/M04/M05/H06/H07 共 5 篇，逐篇内容只读，原 SHA-256 记录于 manifest 和 JSON。
- 生产契约：只读 chunker、retrieval service、RAG query builder、MyBatis retrieval XML。1200/150/800 的 UTF-16 契约、无答案的 query、MySQL eligibility 与原 rank 保留均未改变。无新增 threshold/admission/rerank。
- 官方资料：45 个采用的一手 URL 已在首次规划时由浏览工具取得正文，访问日 2026-10-06；本补丁保留完整 source catalog，不新增/更换 URL，不重新调用外部服务。不能把浏览内容取得称为服务联调/HTTP 状态测量。初选失败或正文超限的页面不进入采用 URL 清单；只换为已取得正文的官方路径/命令页。
- 版本：Java/JVM SE 21；H02 固定 OpenJDK `jdk8u402-b06`；M01 示例固定 `jdk-21+35`；Spring 项目解析 6.2.19（本地 Boot 3.5.16 dependency POM，只读），参考版本线 6.2；MySQL 8.0；Redis 文档经典语义，不推定项目 Redis 运行版本；RFC 9293/768。
- H09：当前 repository HEAD `6076a70f18f3062b8faff4fc179cf21f00a95036` 加工作区逐文件 SHA-256。读取本地代码/schema/tests；未运行测试、SQL 或数据库查询。源码测试的存在只证明有对应测试设计/断言，不是本阶段执行成功。
- Phase 4C2/4C3：holdout 保持 evaluation-only；本阶段不读取或使用逐 query 的 scores/ranks 编写计划/分配重叠风险。提示中 0.49 已被 holdout 否决的事实仅作为冻结约束，不产生替代阈值。

## 3. 静态结果

| 检查项 | 实际值 |
|---|---:|
| EXISTING_FROZEN_TOPICS | 5 |
| NEW_PLANNED_TOPICS | 16 |
| FINAL_PLANNED_COVERAGE | 21 |
| FINAL_PLANNED_COVERAGE_IS_ACTUAL_INGESTION | false |
| FROZEN_BANK_QUESTION_COUNT | 21 |
| FROZEN_BANK_SCORING_POINT_COUNT | 97 |
| EXISTING_FROZEN_SCORING_POINT_COUNT | 23 |
| REMAINING_SCORING_POINT_COUNT | 74 |
| EVERY_NEW_TOPIC_HAS_PRIMARY_OFFICIAL_SOURCE | true |
| EVERY_SCORING_POINT_HAS_SOURCE_STATUS | true |
| THIRD_PARTY_GROUND_TRUTH_SOURCE_COUNT | 0 |
| SOURCE_GAP_COUNT | 0 |
| SUPPORTED_COUNT | 74 |
| PROJECT_SPECIFIC_COUNT | 0 |
| VERSION_SCOPE_COUNT | 16 |
| DOCUMENT_PLAN_COUNT | 16 |
| OFFICIAL_URL_COUNT | 45 |
| CROSS_TOPIC_AUDIT_ROWS | 17 |
| CROSS_TOPIC_IDS_COVERED | 21 |

每个新增主题评分点权重和仍为 100。74 个逻辑 ID 唯一、每点一次，content/pointType/weight 与 JSON 的同一题内序号严格相等；不是靠人工抄写近似原文。主 URL、访问状态与 sourceId 均有定义，16 个版本边界、16 个文件名和五节计划均非空。

Commander 修订后逐点来源结果为 **74 SUPPORTED / 0 PROJECT_SPECIFIC / 0 SOURCE_GAP**。官方来源目录可支持范围和不能证明的结论单独写明，结构推论/工程指导不冒充规范要求。CURRENT_SOURCE_STATUS_PRESENT=74/74 并不等于 PROJECT_IMPLEMENTATION_SUPPORTED=74/74。

跨主题共有 17 行，涉及全部 21 个 bankId；覆盖全部要求对比，包括 HashMap/ConcurrentHashMap、volatile/线程池、AOP/事务、ACID/MVCC/索引、Redis 结构/锁、库存/锁读/Redis 锁、TCP 语义/生命周期。风险等级仅为语义设计判断，未执行检索测量。

## 4. Commander Review：H09 来源审核已解决

审核输入：`C:/Users/USER/Downloads/PHASE_4D1_COMMANDER_REVIEW_DECISION.md` 与 `PHASE_4D1_CODEX_COMMANDER_PATCH_PROMPT.md`。Commander 接受 16 主题 / 74 评分点的来源与结构计划，裁定 H09 是明确的假设 MySQL 事实来源库存设计题，不是要求描述仓库已有库存代码。此前将必须存在库存实现作为来源门槛的判定过严，现按审核修订。

```text
H09 SOURCE_GAP REVIEW:
RESOLVED_AS_HYPOTHETICAL_DESIGN

SUPPORTED_COUNT: 74
PROJECT_SPECIFIC_COUNT: 0
SOURCE_GAP_COUNT: 0

PHASE_4D2: ELIGIBLE_AFTER_COMMANDER_PATCH_VERIFICATION
```

**当前项目没有库存/商品/订单实现**，该事实保留。面试流程中的 requestId 唯一约束、重复载荷检查、唯一冲突恢复和事务回滚断言是项目事实；对于 H09 只能作为类比/上下文，不能证明库存不超卖、不重扣或库存恢复已经实现。无需先补库存模块，也无需等待另一个库存实现来源来回答假设题。

| 评分点 | Commander 批准的支持类型 | 支持边界 | 当前状态 |
|---|---|---|---|
| H09-SP1 | OFFICIAL_MYSQL_MECHANISM_HYPOTHETICAL_DESIGN | MySQL 条件 UPDATE + 正确解释 affected rows，或同事务 FOR UPDATE + 检查 + 扣减；不称项目已有实现 | SUPPORTED |
| H09-SP2 | OFFICIAL_CONSTRAINT_PLUS_ENGINEERING_IDEMPOTENCY_DESIGN | 稳定业务幂等键 + 持久 UNIQUE/等价机制；重复请求复用同键查/重放已有结果，项目面试 requestId 仅作类比 | SUPPORTED |
| H09-SP3 | OFFICIAL_TRANSACTION_SEMANTICS_HYPOTHETICAL_DESIGN | 同一 MySQL 事务性资源内，让库存变更、幂等状态和必须原子提交的其他数据库记录共同提交/回滚；不使任意远程调用原子 | SUPPORTED |
| H09-SP4 | OFFICIAL_FAILURE_SEMANTICS_PLUS_ENGINEERING_RECOVERY_DESIGN | 官方失败语义加工程恢复推理：deadlock 可回滚被选中事务、应用准备重试；lock-wait 默认可能仅回滚语句；客户端/网络超时不确定提交结果。有限重试复用同键，结果不明时先查询/恢复持久状态，不能盲目二次扣减 | SUPPORTED |

四点均记录：`projectImplementationPresent=false`、`projectEvidenceRole=ANALOGY_ONLY_NOT_INVENTORY_EVIDENCE`、`hypotheticalScenarioApprovedByCommander=true`，且 `sourceGapReason=null`。没有将任何点改为 PROJECT_SPECIFIC。官方机制、工程设计推理、项目实际事实明确分栏，所选 source IDs 和官方链接保持不变。

H09 仍为一篇文档的 **计划**：1800–2500 UTF-16、2–3 个估计有用 chunk、S1–S5、原 overlap 风险均保留。authoringStatus 改为 `PLANNED_ONLY_COMMANDER_APPROVED_HYPOTHETICAL_SCENARIO`；当前补丁没有创建该知识正文。

## 5. 易被错误简化的边界

- H01：保持评分点原文；编写选择 `new T()`、泛型数组或相同擦除签名的限制作为至少两项。Java 16+ 对部分参数化 `instanceof` 有放宽，Java 21 不能沿用一律禁止的旧说法。反射保留 Signature 不等于所有实例泛型参数完全实化。
- H04：线程/队列参数与 CPU/I/O、下游、负载相关，属于工程指导；CallerRuns 在 shutdown 时仍会丢弃，executor 本身不持久化任务；监控拒绝/时延需要业务 handler/hooks，不虚构 API 自带全部指标。
- H05：通常默认 RuntimeException/Error 回滚、checked 不回滚；`rollbackFor` 与 Spring 6.2 全局 `rollbackOn` 可以改变规则。local JDBC transaction 不覆盖任意远程效果；消息/补偿仅后续方案，不宣称项目已采用。
- M06：采用手册的 B-tree 类结构用语，B-tree 多子节点与页访问支持高扇出推理；不编造固定高度、扇出或字节布局。覆盖可以减少回表，不概括成所有 MVCC 场景永不访问聚簇记录。
- H09：deadlock 整事务回滚与 lock-wait 默认语句回滚有区别；重试前必须明确事务状态。网络/客户端超时不是数据库提交失败证明，拟方案须复用稳定业务键查询已有结果；Redis 锁 token 不替代持久幂等键和最终数据层约束。

以上边界均是来源规划/写作约束，本阶段没有修源码或加入实现。

## 6. 验证与未执行事项

已完成静态检查：题库 21/97 计数，既有 23 点/新增 74 点的集合差，74 个映射唯一且精确一致，16 个主题与版本/文件设计齐全，45 个一手采用 URL 有正文访问记录，17 行重叠表涉及 21 主题，Markdown 本地产物交叉链接与 JSON 可解析性。

首次规划完整性核验：以 **856 个非生成仓库文件**为基线，原文件哈希不变，只新增上表 4 份规划文件。本次补丁另以 **860 个现有非生成仓库文件**建立修订前 SHA-256 基线，核验文件集合不增不减、改变路径只为四份规划文件；其他 **856 个文件**保持原哈希。排除 .git、node_modules、target、dist、build、coverage、.idea、__pycache__、.pytest_cache 等生成/缓存目录。冻结题库与 5 篇正文的 SHA-256 单独复核。此检查证明本阶段没有改动该范围的既有文件；不把它当成测试通过或远程部署状态。

应用/单元/集成测试执行：**0 runs**；本次执行的是规划产物的本地静态核验，不启动应用。未启动 Maven/前端构建，未连接 MySQL/Milvus，未调用 Embedding/RAG/rerank/DeepSeek/Jev，没有 HTTP 入库/processing、schema 变更、参数修改、commit 或 push。编译状态、单元测试/Mock、真实服务联调和新知识入库状态均为“本阶段未执行”，不引用历史通过数量冒充本次结果。

## 7. 架构决策与停止

RERANKER_DECISION: **DEFER_UNTIL_FULL_BANK_BASELINE**。先完成所有冻结主题经审核的权威覆盖，在后续独立授权阶段形成 full-bank corpus 并测 Baseline，再判断是否需要 reranker/admission 增强。当前既不宣称永久不必要，也不宣称已经必要，不引入 qwen rerank 或新阈值。

本补丁已停止于 4 份规划产物修订，等待 Commander 最终核验；没有进入 Phase 4D2。来源与设计已获 Commander 接受，后续状态为 **ELIGIBLE_PENDING_COMMANDER_FINAL_CHECK**，而非已经执行。Reranker 决策仍推迟至完整题库 Baseline。

## 8. Gate

```text
PHASE 4D1 FULL-KB PLANNING GATE

FROZEN_BANK_21_97_READ_ONLY: PASS
EXISTING_5_DOCS_UNCHANGED: PASS
REMAINING_TOPICS_16_16_PLANNED: PASS
ALL_PLANNED_TOPICS_HAVE_PRIMARY_OFFICIAL_SOURCE: PASS
ALL_REMAINING_SCORING_POINTS_MAPPED: PASS
THIRD_PARTY_GROUND_TRUTH_0: PASS
VERSION_SCOPES_EXPLICIT_16_16: PASS
DOCUMENT_DESIGNS_16_16: PASS
CROSS_TOPIC_OVERLAP_AUDIT_COMPLETE: PASS
NO_RETRIEVAL_EXECUTED: PASS
NO_EMBEDDING_EXECUTED: PASS
NO_RERANK_EXECUTED: PASS
NO_DEEPSEEK_JEV_CALLS: PASS
NO_DB_MILVUS_WRITES: PASS
NO_PRODUCTION_MUTATION: PASS

PHASE_4D1_OVERALL: PASS
RERANKER_DECISION: DEFER_UNTIL_FULL_BANK_BASELINE
```


## 9. Commander 补丁静态核验与 Gate

| 核验项 | 本次结果 |
|---|---|
| 冻结题目 / 全部评分点 | 21 / 97 |
| 既有 pilot / 新增规划评分点 | 23 / 74 |
| SUPPORTED / PROJECT_SPECIFIC / SOURCE_GAP | 74 / 0 / 0 |
| H09 冻结评分原文 / 类型 / 权重 / ordinal 保持不变 | 各 4/4；权重和 100 |
| 非 H09 评分映射 | 70/70 对象语义完整保持，15 个主题计划保持，仅更新 H09 交叉引用 |
| 官方 source catalog / H09 source IDs | 全部保持原样，无新来源 |
| 既有 5 篇 source-document SHA-256 | 5/5 与冻结及补丁前一致 |
| 文件范围 | 860 个原文件均保留，只改 4 份规划文件，其他 856 个原始 SHA-256 不变；新文件 0 |
| 知识正文 / 生产代码配置 schema / 外部服务运行调用 | 均无新增、修改或调用 |
| 产物结构 | JSON 可解析，74 ID 唯一且逐点匹配冻结题库；Markdown 本地交叉链接有效 |

本次没有请求或执行 MySQL/Milvus/Embedding/retrieval/rerank/DeepSeek/Jev，不启动 Maven，不进行知识正文编写、commit/push 或 Phase 4D2 操作。Commander 的假设设计批准已经应用；最后核验依然是本阶段停止点。

```text
PHASE 4D1 COMMANDER SOURCE REVIEW GATE

COMMANDER_H09_REVIEW_APPLIED: PASS
H09_HYPOTHETICAL_BOUNDARY_EXPLICIT: PASS
H09_PROJECT_IMPLEMENTATION_NOT_CLAIMED: PASS
H09_SCORING_POINTS_4_4_SUPPORTED: PASS
SUPPORTED_74: PASS
PROJECT_SPECIFIC_0: PASS
SOURCE_GAP_0: PASS
ALL_74_REMAINING_POINTS_MAPPED: PASS
EXISTING_5_DOCS_UNCHANGED: PASS
OTHER_70_SOURCE_MAPPINGS_UNCHANGED: PASS
NO_KNOWLEDGE_DOCUMENT_AUTHORED: PASS
NO_RETRIEVAL_EMBEDDING_RERANK: PASS
NO_DB_MILVUS_WRITES: PASS
NO_PRODUCTION_MUTATION: PASS

PHASE_4D1_COMMANDER_REVIEW: PASS
PHASE_4D2_STATUS: ELIGIBLE_PENDING_COMMANDER_FINAL_CHECK
```


> 公开副本仅隐去本机用户目录。原始报告保留本地；历史实验结论、执行锁和冻结规则未修改。
