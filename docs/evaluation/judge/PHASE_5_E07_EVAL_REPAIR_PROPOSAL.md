# E07 evaluation 最小修复及零执行初始化提案

**PROPOSAL_ONLY，执行0。仅打开开关不足：eval四张语料表全空，Milvus服务及Collection当前UNKNOWN。默认false保持，新的Canary/模型预算仍未授权。**

## 建议审批的最小分步范围

| 顺序 | 待批准操作 | 精确范围与成功条件 |
| --- | --- | --- |
| 1 | 恢复/确认已有Milvus基础设施访问，随后只读检查 | 启动现有Docker Desktop/既有Milvus容器（如确需）；不新建/删除容器、不改volume或部署配置。只探测已配置loopback:19530与default/knowledge_chunk_vectors_evaluation，记录实际schema/index/count/ID。若当前部署无法辨认，先STOP给出部署证据，不猜容器命令。 |
| 2 | 授权源只读与目标限定数据准备 | 只读取已确认冻结源的question21、scoring_point97、knowledge_document21、knowledge_chunk52及既有向量52；只向ai_interview_evaluation和knowledge_chunk_vectors_evaluation写入相同身份/内容/版本/浮点值。源local全程只读。逐域完整SHA匹配E06冻结基准，否则STOP。 |
| 3 | 数据完成后一次evaluation专用启用与零模型预检 | 明确允许严格限定evaluation runner的一次 `--milvus.enabled=true` 参数；不改application/local/test默认、不写User/Machine或IDEA共用环境。唯一evaluation profile、精确target、原initializer和全数据/检索证明必须先通过。无模型请求。 |
| 4 | 另行审阅是否授权全新Canary | 只在准备/预检通过后由Commander批准新epoch、8例、最多24推理、零重试；本提案及环境修复授权都不能替代模型授权。CANARY-01永不重试、报告/锁不改。 |

这些是待审查的具体范围，不是本轮 permission 请求，也没有执行迁移代码、SQL或开启服务。

## 配置最小方案

**优先：不改YAML，保留安全默认，未来只为一次已限定evaluation的进程传入明确enable参数。** 原评测入口需先确认唯一profile=evaluation、MySQL=ai_interview_evaluation、Milvus=default/knowledge_chunk_vectors_evaluation及完整已存在数据，并实际调用EnvironmentIsolationInitializer；遇到任何不匹配必须在创建基础设施Bean前停止。不能将相同参数注入local或共用启动配置。不得执行既有run-baseline.ps1来代替预检，因为它会启用服务、修改环境并可能触发模型/持久化。

可选加固（非最少动作）：单独批准application-evaluation.yaml第8行由 `${MILVUS_ENABLED:false}` 改为 `${EVALUATION_MILVUS_ENABLED:false}`，配合专用runner的显式opt-in。仅此一行、仅evaluation profile；基础application.yaml、local/test/生产逻辑不变。变量仍默认false，不持久保存，不顺带调整超时、模型、Prompt、gold、校验或schema。该选项隔离generic MILVUS_ENABLED对evaluation的影响；此刻不需要据此认定原默认是缺陷。

## 零执行的数据初始化设计

1. 先只读枚举已配置source/target身份与内容，保存批准manifest：源精确PK、vector_id、document/chunk index/version、embedding model/profile、1024个有限float、counts及完整域SHA。E06历史hash不能还原行/向量；若当前local源不匹配或原始冻结数据不可得，STOP，不能重选、改Gold或重新Embedding。不得读取/复制user账户、密码、session、answer、evaluation、report、RAG trace、weakness或真实凭据。
2. eval当前13表全空、结构存在，MySQL无需新建schema/表。只写question、question_scoring_point、knowledge_document、knowledge_chunk四表。真实FK已确认只有point→question与chunk→document，不需要user父记录。保持原PK及所有冻结知识字段，保留向量UUID与已批准metadata；任何字段（含原timestamp）无法满足冻结全行hash时交Commander裁定，不悄悄替换hash口径。
3. Milvus尚未确认Collection存在，**不能现在授权隐式自动创建**。恢复服务后先read-only describe，若存在且schema/index兼容则不做DDL；若缺失，单独明确审批创建恰好knowledge_chunk_vectors_evaluation、5字段、1024维、autoID=false/dynamic=false、AUTOINDEX/COSINE；若不兼容，STOP给出差异，不drop/重建/自动迁移。local与test Collection全部只读且禁止create/drop/delete。
4. 写入前再次确认evaluation精确目标、物理连接身份和源/目标摘要；schema与Collection共享同一引擎不影响这些检查。MySQL source/target使用分离连接，source只读。目标初始不存在同PK/UUID才允许作为本轮新数据；已有完全一致目标项仅作幂等no-op，任何冲突STOP。不得关闭FOREIGN_KEY_CHECKS、TRUNCATE、REPLACE/upsert覆盖或按宽filter清库。
5. 采用小批明确ID：先写evaluation专用Collection的52个既有向量，然后在eval的短MySQL事务中按question→point、document→chunk顺序插入、检查影响行数/全行hash并COMMIT。不会把模型/外部HTTP包进MySQL长事务，也不声称跨库原子提交。所有原始浮点值直接搬运，不计算或修改向量，不调用Embedding。
6. 补偿与回滚也必须明确批准，仅限这次manifest证明的新写入ID：Milvus插入或后续SQL失败，只删除本轮原先不存在、已经创建的UUID并核实无残留/非目标保持。SQL事务失败直接ROLLBACK；如果需撤销已COMMIT的本轮数据，先校验这些PK当前仍与本轮manifest完全相同，再按point→question、chunk→document顺序删除。本轮前存在项和任何不同内容不能删除。保留部分失败原证据，使用有界轮询核验可见一致性，不删除锁或假称成功。
7. 完成后的准入证据：13表完整只读摘要，四表21/97/21/52及精确frozen身份/内容/hash；Milvus52全向量、schema/index/profile/value hash；每个vector_id↔chunk/document/index/version一致；8个冻结query真实topK5的ID/score/content/source/version及原查询hash匹配。源local前后全摘要及所有历史文件/锁不变。不能只用计数或缓存命中作为PASS。

## 8-query检索的额外前提

本次禁止真实模型，故没有获取新query Embedding；52个知识向量也不能替代8个query向量。如果已有获批捕获的query vector字节，下一轮先离线证明query内容、embedding model/profile/dimension和SHA，再批准其针对evaluation Collection的只读生产搜索/SQL hydration；否则明确请求单独query Embedding获取授权（模型/调用预算、零重试、证据保存），不得隐式算进零模型环境阶段。本轮不判断这些向量已经可用。

## 实际执行状态

以上服务启动、DDL、DML、迁移、补偿删除、开关修改/覆写、环境写入、Embedding、模型推理、新Canary均为0。只交付具体提案，等待Commander决定下一次分步权限；没有自动申请执行或在报告生成后继续。E07环境BLOCKED / EVALUATION_DATA_NOT_READY，E07-CANARY-02 NOT_RUN，5C NOT_RUN，Phase6 BLOCKED。
