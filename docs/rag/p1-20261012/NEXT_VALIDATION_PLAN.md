# P1 独立质量验证：下一阶段最短执行计划

状态：READY_FOR_AUTHORIZED_RUN。16条输入已冻结，实际检索和真实 MySQL 集成均为 NOT_RUN，需要分别获得后续明确授权。没有质量 PASS 结论。当前代码审核 PASS 来自用户转述的 Cursor 独立审核；本轮补充的是本地证据校验。

## 冻结输入与 QueryBuilder

固定输入：`src/test/resources/rag-p1-independent/queries-v1.json`。

SHA256：`4f42970bbdb3682c475056e512ff725832329d213b8b49e3a27ac45b5afbd456`。

8条独立改写使用 MAIN_ANSWER；4条边界追问使用 FOLLOW_UP_ANSWER，新的主问题上下文、追问和目标评分点已一起冻结；4条库外问题使用 MAIN_ANSWER。使用已有 `EvaluationRagQueryBuilder`，不是把问题原文直接当作本项目生产Query：

- MAIN：主问题 + 完整评分点内容。
- FOLLOW_UP：主问题 + 追问 + 目标评分点内容，顺序按冻结目标ID列表。
- 用户答案、参考答案、coverageBasis、库外理由、文件标题/路径及相关性标签不进入Query。

合成评分点ID只供离线上下文使用，不新增题库记录。新集与当前人工标签、existing/expanded各30条Golden、旧holdout分开保存，不能混合求一个“总体提升率”。只核对问题文本、类别、来源覆盖，不预填score或humanLabel。库外问题也不能先验要求每个命中都标0；确有通用帮助的内容仍可由人判断为部分相关。

生产链仍是 Answer → ContextService → Orchestration(TopK=5) → RetrievalAdapter/QueryBuilder → EmbeddingClient → MilvusVectorStoreClient → 应用阈值 → 生产Mapper SQL水合 → Trace → Prompt → Judge → Java校验/评分。下一阶段质量检索只运行到读候选/水合，不调用答案提交API、Orchestration、Trace或Judge，不启动会运行MilvusCollectionInitializer的完整Spring应用。

## 受控只读检索：每条只取得一批真实TopK5

1. 校验测试集及四个生产文件SHA；记录新运行目录。使用只读MySQL身份和已存在Collection，检查目标/维度/索引metric及Collection默认一致性，不执行Load/Create/Insert/Delete。任何不符或调用失败立即停止，不自动重跑整批。
2. 对16条冻结的productionStyleQueryText各调用一次项目EmbeddingClient，固定qwen3.7-text-embedding/1024/profileVersion。每条取得一个向量，保存输入SHA、供应方model、usage、索引及Float32向量SHA；不重做52个语料向量，不切换模型。
3. 每个查询向量调用一次生产MilvusVectorStoreClient Search，COSINE、TopK5、相同profile过滤。保存原始顺序、rank、ID和原始Float32 score；允许服务返回少于5条。不得先看结果再改问题、评分点或0.70门槛。
4. 对每条raw候选的ID集合，调用一次真实生产 `KnowledgeChunkMapper.selectRetrievableByVectorIds`；MySQL连接显式只读，16次水合尽量置于一个短RR只读事务的一致快照中。记录实际资格结果，并把该结果作为同一查询两个版本共享的只读水合快照。空raw列表跳过SQL。
5. 真实候选和水合结果落入新的独立运行文件后，切断真实服务边界。离线缓存Embedding/VectorStore/Mapper输入，分别通过现有KnowledgeRetrievalService与Adapter/PromptBuilder执行V0/V1，后续比较不再发第二次Embedding、Search或SQL。V0关闭阈值；V1仅启用原始score≥0.70F，其他SQL资格规则相同。沿用实际生产服务，不能另写一个近似过滤器冒充生产行为。
6. 人工先盲标同一批raw TopK，不展示score或V0/V1准入结论。标注单独输出、新建文件，默认待标；再固定人工标注SHA并计算V0/V1差异。AI复核若以后有授权，只能另存建议。本阶段不需要LLM、Judge或Reranker。

V0/V1对比记录queryId/queryHash、同批raw IDs/score bits、SQL资格、保留/过滤ID、原始rank、实际Prompt条数和正文SHA、是否无证据降级。逐类型统计直接相关/部分相关/不相关/U、证据保留率、有效正文/评分点信息损失、零结果率及库外误准入。报告32个Prompt的构造结果；没有模型调用，所以不能声称答案评分质量提升。全体统计之外，必须分别查看8/4/4三组，尤其避免“全滤空”掩盖正例证据损失。

## 预计调用数量和费用

| 操作 | 一次完整成功运行的计划量 | 说明 |
|---|---:|---|
| Embedding逻辑文本数/单文本HTTP请求 | 16 / 16 | 与生产service每Query embed(List.of(query))一致；V0/V1共用结果 |
| Milvus Search | 16 | 每Query一次TopK5；最多80个raw候选位置，不保证80个唯一Chunk |
| Milvus只读Describe | 2 | Collection身份/默认一致性/Schema + vector index/COSINE；不再重做P0 RPC故障诊断 |
| MySQL候选水合SELECT | 0～16，按16预算 | raw空时跳过；同一批结果共享给V0/V1 |
| MySQL只读目标/事务核对SELECT | 2 | DATABASE()/server UUID/身份，以及当前事务只读和隔离级别；预计SELECT合计最多18 |
| 额外MySQL事务控制 | 1次只读事务begin/结束 | 另计控制语句；与INSERT/UPDATE无关，不谎称只有18条全部协议操作 |
| DeepSeek/Jev/Reranker/语料Embedding/真实Trace写入 | 0 | 构造32个Prompt在离线完成 |

数量为应用层无失败的计划量，实际SDK内部重试/连接元数据命令须计入执行日志；出错停止，不以本表证明实际已调用。若后续选择一次同步Embedding输入数组16条，可在batchSize≥16时降低为1个HTTP请求，但不是当前生产单Query调用的直接复验；它也不是官方享半价的异步Batch产品，首轮建议仍按16次生产路径执行。

冻结Query合计2499个Unicode字符、6309个UTF-8字节，二者都不是Token数。估计以总输入3000～8000 Token做粗预算；精确值取Embedding usage。预留20000 Token预算以容纳误差，不能把字符数当成计费Token或保证该预算必不超出。

截至2026-10-12，qwen3.7-text-embedding北京实时输入价为0.5元/百万Token，3000～8000 Token约0.0015～0.004元，20000 Token预算约0.01元；新加坡列价为0.525元/百万Token，对应20000 Token约0.0105元。地域和免费额度未读取账号状态，真实执行前按实际地域确认，不假设免费。价格来源：[阿里云百炼官方模型价格](https://help.aliyun.com/zh/model-studio/model-pricing)。本地已运行的Milvus/MySQL不新增第三方按次模型API收费；运行资源成本不包含在上述Embedding估计中。

## Bounded与历史Strong的差异

本轮离线检查SDK 2.6.20：SearchReq默认consistencyLevel为null，VectorUtils在转换gRPC请求时设置use_default_consistency=true；CreateCollectionReq默认BOUNDED。生产代码没有覆盖请求一致性，因此使用Collection默认，在项目默认建库路径为Bounded。这里没有新RPC确认目标Collection实际的默认值；下一阶段Describe必须记录并核对它，不能把SDK默认当成live读证据。

历史Strong Runner显式要求Strong，能约束搜索看到的写入时间边界；Bounded允许一定陈旧度，刚写入/删除时两个视图可能不同。冻结语料稳定时有机会得到相同结果，但不能预先保证。V0/V1必须共享同一次生产一致性Search，才能把候选变化归因于阈值。首轮不额外跑16次Strong、不修改生产一致性，也不修改冻结Collection；要做Strong/Bounded对照需另加授权和16次Search预算。

## 独立test MySQL的Mapper/Trace/Prompt集成方案（未执行）

只允许 `local,test` 或明确test profile，目标必须为ai_interview_test与knowledge_chunk_vectors_test；保留EnvironmentIsolationInitializer。真实Embedding/Milvus/DeepSeek/Jev禁用，用缓存或合成外部边界数据。Milvus disabled时手工在测试配置中组装RetrievalService，不启动生产Collection initializer。

测试只在ai_interview_test写本测试创建的数据：1个测试用户/Session/MainQuestion/Answer和若干唯一命名Document/Chunk，不复制或修改冻结191行与52向量。通过现有Mapper取得真实generated key和资格结果；Trace使用Spring代理事务，Prompt使用真实Builder。拟验证六类场景：

1. 同批5个合成raw命中：V0资格通过后5条；V1保留部分；v1关闭snapshot逐字节/完整结构兼容，v2启用记录0.70/COSINE/FLOAT32。
2. SQL排除非READY、非ACTIVE、旧documentVersion和不同model/profile；剩余rank保留空洞，不补齐。
3. 同documentId相邻互补Chunk同时保留，日志chunkId/answerId/batchId/rank与实际Prompt逐条对应。
4. V1全部滤空：跳过IN查询，Batch rawHitCount=5/evidenceHitCount=0，无rag_hit_log，Prompt明确无证据且没有虚构引用。
5. SQL资格全空或Milvus raw空：同样能构造空证据Prompt，raw计数准确。
6. Hit写入约束故障或受控失败时整段Trace事务回滚，包括Batch；成功分支精确核对影响行数。

测试数据由事务回滚或按本测试ID精确清理，不TRUNCATE、不清共享表，不改Schema、不删除他人数据。测试预计一个专门的六场景类/组；它包含真实数据库写入，必须在另行授权后才实现执行门禁并运行。目前无这部分真实集成通过证据，不能用Mock成功代替。

## 最短推进顺序与风险

先取得“16条独立查询只读检索+Embedding费用”授权，完成一次读取与离线V0/V1，人工标注后报告。独立test数据库六场景写入验证另取授权，单独运行；无需恢复evaluation或重新发行P0 Runner。

主要风险：小样本/问题写法代表性、评分点使完整Query与裸问题不同、模型/profile不一致、Bounded陈旧视图、SQL资格变化、0.70误删有效证据或对库外问题误准入、待标U/人工意见分歧。新的16条未调参，不在其结果上反向改写问题；如以后改门槛，应另设验证集或清楚标成开发集，不能继续称同一集独立验证。
