# D3-B/C/D 最终验收与测试准入

2026-11-14（Asia/Shanghai）。**USER_TEST_READY 为 CONDITIONAL_GO。** 可以在本机 `evaluation` 开展人工测试和少量、串行的 AI 场景测试。三组检索的 Prompt 直接证据相关率均未达到预注册门槛，正式质量验收与扩大测试规模仍待后续验证。 [操作入口](D4_MANUAL_AI_TEST_START_HERE.md)

## 入场 Git 与数据基线

上一轮附件 `GIT_BEFORE_D3_BCD_PROMPT.md` 是收口任务书。本轮入场未找到完成收口的记录或提交。实际 `main` 为 `0c6f92afe331954f7cea37d33db688fe781cbf2c`，暂存区为空。GitHub API 确认远端 `main` 为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，入场领先 8、落后 0。SSH 443 本轮只读查询遇到 publickey 拒绝，未改变本地认证或 Git 引用。

D2 组卷源码和测试是入场工作区改动，本次构建和验证使用这份工作区源码。D1 V2、D3-A 规程、冻结查询和工作簿属于未跟踪输入，按精确路径归档本轮复现所需文件。来源未确认的嵌套仓库只核对 `.git` 存在及父仓库索引无 gitlink，原目录保留。原 `mvnw` 可执行位、私有 Compose 和其余前序变动保留。工作区前序台账变动仍待前序收口，本次仅归档新增段落。

入场 MySQL 8.0.45 `127.0.0.1:3308/ai_interview_evaluation` 与 D1 V2 逐字段和实际 ID 对账 63 题、298 点，难度分布 15/21/27。E01 按正文 SHA-256 定位，实查文档 ID 1，两个 ACTIVE Chunk。Milvus 2.6.26 `127.0.0.1:19532/default/knowledge_chunk_vectors_evaluation`，1024 维，COSINE/AUTOINDEX，两条 E01 向量身份与 Float32 摘要一致。既有 81,510,400 字节 Milvus 备份 SHA-256 已重核，恢复能力沿用 E1–E3 记录。未执行停服备份或故障注入。

## CONTENT 与 INGESTION

- **CONTENT 通过本轮 AI 技术门禁。** 新增 50 篇，E01 原字节复用。实际工作簿 51 篇、298 点、449 条历史来源定位已导出。新正文的 316 条文档到评分点定位与计划矩阵一致，覆盖 298 个不同点。三个角色交叉复核，人工抽查待做。生产切片 52 块，长度与原文摘要通过。H01.02 的 Java 21 探针 7 项通过，冻结评分点保持原值。 [正文审查](../rag/d3-b/CONTENT_REVIEW_20261114.md) · [语料及切片](../rag/d3-b/CORPUS_MANIFEST_20261114.json)
- **INGESTION 通过。** `KnowledgeDocumentAdminService.uploadDocument → KnowledgeDocumentProcessingService.processDocument → persistChunksAndMarkReady`。先验 1 篇，随后每批最多 5 篇、逐篇串行。最终 **51 READY / 52 ACTIVE / 52 向量**，新写 50 篇及 50 向量。MySQL 与 Milvus Strong 逐条核对文档、attempt、索引、向量 ID、profile 和有限 Float32 向量摘要。新向量与真实模型响应值相同，缺失、重复及孤立 ID 为 0。 [入库对账](../rag/d3-b/INGESTION_RECONCILIATION_20261114.json)
- 入库前后 evaluation 11 张非知识表的行摘要相同，E01 和两条向量身份完全保全。末次快照确认 63/298 摘要和全部语料身份在冒烟后仍一致。原 `ai_interview` 与 `ai_interview_test` 分别抽验 5 张表计数，另有 3 个非目标 Milvus Collection 计数不变。本轮执行入口仅写指定 evaluation 对象。对非目标对象的实测范围为这些计数，MySQL 和 Milvus 快照分别采集。 [入库对账](../rag/d3-b/INGESTION_RECONCILIATION_20261114.json)

## RETRIEVAL 未达标

生产查询通过真实 QueryBuilder 和检索适配器。公开与独立集只把固定用户问句送入检索。三组都实际调用百炼、Milvus Top5、MySQL 资格过滤和 PromptBuilder v3。阈值保持关闭，三组 A→B→C 本轮均无条目丢失。以下命中指标以正例为分母，Precision@5 固定分母 5，Prompt 相关率使用实际证据数。结论使用 AI 临时标签或生产先验机制映射，人工 gold 待建立。

- **生产业务集**。MAIN 63/63、FOLLOW_UP 9/9 有效。MAIN Hit@1/3/5 均 63/63，MRR@5 为 1.000，CORE 77/77，逐点评分依据覆盖 298/298，Precision@5 和 Prompt 直接证据占比均为 75/315（23.81%，门槛 65%）。FOLLOW_UP Hit@1/3/5 均 9/9，MRR@5 为 1.000，目标 CORE 9/9，Prompt 9/45（20.00%）。例如 M17 的第 2 至 5 条只有 Spring 域相邻内容；H27 的第二条涉及答案接管，报告恢复以 S10 为直接依据。MAIN P50/P95 为 321/385 ms。 [生产指标](../rag/d3-d/PRODUCTION_RETRIEVAL_METRICS_20261114.json)
- **公开冻结集**。60/60 有效，48 个正例的 Hit@1/3/5 为 41/46/46，MRR@5 0.90625，Precision@5 67/240（27.92%）。最终 Prompt 67/300（22.33%，门槛 65%）。Q01 和 Q05 的直接证据未进原始 Top5，Q32 缺 1 个跨主题子意图。跨主题覆盖 26/27，完整 11/12。12 个负例都返回非空，23 条 RELATED_ONLY、37 条 IRRELEVANT、有害 0。两 AI 角色排序隐藏下的 3120 对原始判断有 72 个标签分歧、一致率 97.69%、κ 0.805。裁决由一位原标注角色复核。P50/P95 为 324/375 ms。 [公开结果](../rag/d3-d/PUBLIC_RETRIEVAL_RESULTS_20261114.json) · [冻结 AI 标签](../rag/d3-d/PUBLIC_AI_LABELS_20261114.json)
- **独立验证集**。语料冻结后由新上下文编写，写作者未读项目、语料、题库、公开查询或排序。24/24 有效，18 个正例的 Hit@1/3/5 均 15/18（Hit@5 门槛 85%），MRR@5 0.83333，Precision@5 21/90（23.33%），最终 Prompt 21/120（17.50%，门槛 60%）。3 个语料缺口涉及 Java 引用按值传递、弱引用清除语义与 InnoDB 临键锁/间隙锁。跨主题覆盖 10/11，完整 3/4。6 个负例均非空，18 条 RELATED_ONLY、12 条 IRRELEVANT、有害 0。两 AI 角色排序隐藏下的 1248 对判断一致率 97.76%、κ 0.805，裁决由其中一位复核。P50 325 ms。原题和详细结果保留在 owner-only 本机目录。 [独立集摘要](../rag/d3-d/INDEPENDENT_RETRIEVAL_SUMMARY_20261114.json)

未调参或重跑真实评测。负例结果表示背景和无关内容会进入 Prompt。生产命中率不能独立验证新措辞泛化，也没有执行有无 RAG 的评分收益对照。 [评测补充规程](../rag/d3-d/PROTOCOL_SUPPLEMENT_20261114.md)

## FUNCTIONAL_SMOKE、服务与测试

**通过一次有界 EASY，包含一次受控恢复。** 真实 HTTP 执行注册、登录、身份读取、创建/恢复、答题、FINISH、报告生成与读取、详情和历史。先确认已有 18082 被其他进程占用，另选 18482。修正本轮启动器临时 JWT 的 Base64 编码后启动。首答 DeepSeek HTTP 200，但本轮 usage 记录器重复消费响应流，应用返回 502。通过独立 MySQL 确认原答为 FAILED、评价 0，修复记录器并离线确认重复读取返回相同字节且 usage 只记一次。随后保留原合成用户、原会话、原请求键和正文，单次恢复。全部模型调用计入账本。

独立 MySQL 连接核对 Session 29 已 `COMPLETED`、5/5，8 个 `EVALUATED` 答案，5 条不同 MAIN 的 `FINAL`、3 条 `INITIAL`。1 份 `READY` 报告，聚合分数等于 5 条 FINAL 均分，9 次检索追踪包括首答失败时已写入的一次。使用合成答文，实际走到有追问和无追问分支。未执行真实 MEDIUM/HARD 全流程、报告故障恢复、浏览器完整面试或并发测试。

- 后端离线编译成功。定向 JUnit 首次 **103/0/21/0**（总数/失败/错误/跳过），Mockito attach 被执行环境阻断。给同一依赖显式 `-javaagent` 后重跑 **103/0/0/0**。包括切片、隔离、QueryBuilder、PromptBuilder、检索及 D2 选择器。未执行普通全量 Maven 或写库测试。此轮生产源码无改动。
- Java 21 探针 **7/0/0/0**，用量包装探针 **3/0/0/0**。评测脚本合成验证分别为公开 6 项、生产 2 组、独立 2 组，并重算真实产物。MySQL/Milvus 真实对账与模型 HTTP 另计。前端 `npm run build` 成功，浏览器确认登录/注册入口，Vite 代理未登录 `/api/users/me` 返回 401。
- 交接时前端 [15174 登录](http://127.0.0.1:15174/login) 为 PID 76174，后端 [18482](http://127.0.0.1:18482) 为 PID 76627。仅回环监听。本机 owner-only 目录 `backups/D3_BCD_20261114/` 下有 `frontend.log`、`d4-backend.log`、`smoke-credentials.json` 及独立读回。进程在会话结束后能否持续运行未验证，停止时按操作指南重启。已有两个数据库容器均为 healthy。

## API 与累计自审

百炼北京单价 ¥0.50/百万输入 tokens，控制台未登录，实际额度不可见。本轮将「核对可见余额」按当前可见性记录，依用户已授权范围、核验价目与执行器预算预留继续。DeepSeek 官方免费余额查询返回可调用，具体余额省略。实际 **Embedding 215 次 HTTP 尝试、42,660 输入 tokens；DeepSeek 10 次模型 HTTP 尝试、34,773 输入、5,411 输出 tokens**。所有模型 HTTP 均返回 200，包含上文一次应用级失败。按周末时段公开价格合计估算 **¥0.07323116**，按高峰价口径约 ¥0.12513232。账单未核对。 [用量账本](../rag/d3-d/API_USAGE_20261114.json)

三个编写角色互审和独立留出角色复核了来源版本、切片内容、检索泄漏和标签局限。执行记录核对了入库身份、测试隔离、响应流错误及恢复、实际请求数量和预算。完整日志、账户文件、原始独立集与供应商响应均未提交。未改变现行评分、检索参数或 Milvus 版本。既有 Milvus 启停稳定性和在途写入风险沿用 E0-R3 记录。本轮可用性证据来自当前成功运行。

## 本地 Git 交接

- `9b7e35639178c9cc125d99e3a2cfe92a344fbb72` 冻结正文、来源和覆盖证据，归档复现所需的两个 D1、三个 D3-A 原始输入。
- `854b9eadf7dfb165847e49f7bd1f1d3d27e5f9b3` 归档切片、盘点、真实入库、只读检索、指标和定向冒烟工具。
- 本报告、脱敏实测证据、D4 操作指南及两份台账的本轮增量归档在后一条本地提交。该提交的 SHA 以 Git 实际读回为准。未推送。

审查后按明确路径暂存，历史证据与尚待前序收口的改动仍在工作区。两份工程台账的学习状态保持待学习、掌握未确认。
