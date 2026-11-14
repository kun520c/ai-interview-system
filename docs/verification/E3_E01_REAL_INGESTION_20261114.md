# E3｜E01 真实 Embedding 入库验收

日期：2026-11-14（Asia/Shanghai）。项目：`/Users/kun/Developer/projects/ai-interview-system`。源码工作区基线 HEAD：`0c6f92afe331954f7cea37d33db688fe781cbf2c`。本轮未修改生产源码、配置、切片或补偿规则。

**PASS：E01 已通过正式业务管道保存为 READY，2 个 Chunk 与 2 条真实 1024 维向量完成双向逐条对账。百炼 HTTP 尝试 1 次，HTTP 200，请求级 total_tokens=821；按公开单价计算 ¥0.0004105，账单尚未核对。DeepSeek 调用 0 次。** 数据保留在 evaluation，本轮授权的一次 Embedding 请求已使用，执行结束。

此前 [2026-11-13 阻塞记录](E3_E01_REAL_INGESTION_20261113.md)保留当时事实。准备目录沿用开始核验时的日期 `E3_E01_LIVE_20261113`；发送门禁落标记时间为北京时间 2026-11-14 00:03:22.923948，随后执行本次付费请求；该时间不是独立抓包时间。

## 1. 配置、授权和目标

- 真实 Embedding 配置从 `/Users/kun/.config/ai-interview/evaluation-embedding.env` 读取，权限0600；非占位 Key，仅通过子进程环境进入应用。实际绑定北京业务空间 HTTPS OpenAI 兼容端点，路径 `/compatible-mode/v1/embeddings`；模型 `qwen3.7-text-embedding`，维度1024，profile `qwen3.7-text-embedding-1024-dense-v1`。
- `/Users/kun/.config/ai-interview/evaluation-deepseek.env` 仅检查文件权限0600和非占位配置；未加载进本次应用、未发连通性探针，不能据此宣称 DeepSeek 实际调用可用。用户已授权后续本项目既定真实功能、评分、追问、报告及多AI测试自主使用其额度；仍须隔离 evaluation、记录请求/usage/费用、限制并发和重试，不包含无限付费压测。该授权不自动启动后续测试。本轮 DeepSeek 关闭。
- MySQL：`127.0.0.1:3308/ai_interview_evaluation`；实例UUID `85dd1e00-cf98-11f1-8677-9eb9380f25e7`。首次无费用MySQL预检记录SQLSyntaxErrorException；调整为项目已有受保护管理员凭据后通过，固定 schema/实例门禁，不修改账号、授权或原数据库。失败JSON未保留SQLState及具体语句，不能仅由异常类推断SQL语法问题。
- Milvus：`http://127.0.0.1:19532`，database `default`，collection `knowledge_chunk_vectors_evaluation`，ID `470437151302223600`；1024/COSINE/AUTOINDEX，既存Schema经生产初始化器验证。
- `EnvironmentIsolationInitializer` 在真实 Spring 启动中保持有效，仅启用 evaluation；SQL初始化关闭，不启动HTTP监听。启动前先验证目标对象已存在及身份，未创建或重建数据库对象。执行前为21题/97评分点，文档/Chunk/向量均0，没有可复用READY E01。
- MySQL与新Milvus前后均healthy，StartedAt及RestartCount未变；没有停服、重启或故障注入。既有离线备份81,510,400字节，SHA-256 `4a28237ee5b9d8a7911c1e9a6d4e9344ac0e069f473e1af78a8f31d4f01e25a4` 已复核；独立恢复可行性引用E1–E3历史实测，本轮未重做恢复。

## 2. 冻结输入与一次请求控制

输入：`/Users/kun/Developer/projects/ai-interview-system/docs/rag/source-documents/e01_java21_interface_vs_abstract_class.md`。原始及规范化内容SHA-256均为 `4b82a2724e53946fffa082560fde1c348201114ac4f490e409c2ddbba5071de0`；规范化内容1612个UTF-16单元、3162个UTF-8字节。调用现有 `KnowledgeTextChunker` 后与阶段D清单逐块比对SHA-256，重叠切片共3188个UTF-8字节：

- Chunk 1：1178个UTF-16单元、2424字节，SHA-256 `db9396ccf3b0d6d5b527c58c23bf032a776eeb7089e048796b8f231534f08951`。
- Chunk 2：460个UTF-16单元、764字节，SHA-256 `2e276739b01c8d87c25b2f013b34e55d5f73757f3f7053af4147fc367919680e`。

临时执行器复用现有 `EmbeddingConfiguration`、`SpringAiEmbeddingClient` 及 Milvus适配器；Spring `RetryTemplate` 一次尝试。独立JVM显式限制 `jdk.httpclient.redirects.retrylimit=1`、`jdk.httpclient.disableRetryConnect=true`、`jdk.httpclient.enableAllMethodRetry=false`，HTTP/1.1、NEVER重定向和显式无代理；清理可注入JVM参数的继承环境。发送门禁校验POST目标、模型、维度、float编码和两块完整文本，只允许原子计数0→1；发送前以CREATE_NEW留下 `paid-attempt.json`，另有进程锁，禁止再次执行收费流程。无需付费连通性探针。

请求计数证据为拦截器的1次放行、进入HTTP执行、HTTP 200响应和重试限制；没有独立抓包或供应商请求明细，不能表述为另有线级计数。本次未触发第二次调用或故障重试，不以预算余额授权追加尝试。

调用前按北京公开单价 **¥0.50/百万输入tokens** 核验。接口每条上限128000 tokens，固定两条即使按256000 tokens的保守上限也为¥0.128，低于¥0.15授权；未依赖免费额度。百炼月预算停用存在延迟，不是实时单请求硬预算，本轮以固定输入、单次请求及事前估算控制支出。[模型定价](https://help.aliyun.com/zh/model-studio/qwen3-7-text-embedding)、[同步接口限制](https://help.aliyun.com/zh/model-studio/text-embedding-synchronous-api)、[地域端点](https://help.aliyun.com/zh/model-studio/base-url)、[预算机制](https://help.aliyun.com/zh/model-studio/budget-management)。

## 3. 正式业务处理与真实响应

调用链：`KnowledgeDocumentAdminService.uploadDocument → processDocument → KnowledgeDocumentProcessingService → claimProcessing → KnowledgeTextChunker → SpringAiEmbeddingClient → MilvusVectorStoreClient → persistChunksAndMarkReady`。使用真实Spring事务代理及既有短事务；没有外围大事务，没有手写SQL生成READY数据，没有假向量。

- HTTP尝试1次，返回200；模型响应 `qwen3.7-text-embedding`，两向量均1024维，全部元素有限，供应商索引经现有适配器校验并恢复输入顺序。
- 原始请求级usage：`prompt_tokens=821`、`completion_tokens=null`、`total_tokens=821`。未将null写成0，未平均分摊至Chunk；两个Chunk的token_count保持NULL。
- 按usage计算：`821 × 0.50 / 1,000,000 = ¥0.0004105`。这是请求usage乘公开价的计算值，尚未核实账单、优惠或实际扣费。
- HTTP执行到响应头1381ms；完整Embedding适配器调用1697ms；处理服务调用1756ms。这些是一次运行耗时，不是吞吐/P95或性能改进证据。
- documentId=1，documentVersion=1，状态READY，无错误信息；processing attempt `b6b493157f4545b8a5966ae61e845519`，profile与配置一致。无模型超时、响应异常、失败attempt或补偿执行。

## 4. 逐条身份核对及数据保全

本次新增映射如下，两Chunk均ACTIVE、documentId=1、documentVersion=1；顺序和完整内容与离线切片一致：

- Chunk ID 1 / chunkIndex 1 → `b6b493157f4545b8a5966ae61e845519-1`；向量Float32大端字节SHA-256 `5237351aeb955cf41d77cb61436c9fc4314c9d47c7de8d0ee4b0cc0a4acbc8c9`。
- Chunk ID 2 / chunkIndex 2 → `b6b493157f4545b8a5966ae61e845519-2`；向量Float32大端字节SHA-256 `2e02358ea71fed952afc26d80ed913419ad673b74b2a3b29784d5c382f7a327c`。

真实Java SDK使用 **STRONG** 查询，读取本集合全部行并与MySQL全部Chunk双向比对：vectorId集合完全相等，每行documentId、chunkIndex、embedding_version正确；MySQL文档/Chunk版本关系正确。两条读回向量分别逐元素等于本次响应，均1024维且有限，无缺失、孤立或重复身份。不是仅比较两个总数。

复用SDK读回且已与本次响应逐值核对相等的向量，执行1次含2个query vector的只读COSINE搜索；各自Top1返回自身，分数为0.99999994和1.0。没有额外Embedding请求。此自检只证明向量存储和检索链路工作，未验证独立语义检索、Hit@K或评分提升。

保全证据 `preservation.json`：原 `ai_interview` 与 `ai_interview_test` 完整逻辑导出前后逐字节一致，SHA-256 `3048c1b5926ecac9ad5aeb9906d23366a4bba556e5acd123c071fe32c76165e8`；evaluation除knowledge_document/knowledge_chunk外11张表导出块逐字节一致，仍为21题/97点，未修改H01，未新增用户/面试/评分报告。Milvus其余3个Collection的元数据和Strong计数记录一致、均0；evaluation既存Schema不变，仅新增2向量。旧卷和备份保留。

## 5. 实际验证、失败和限制

- 临时Java执行器完成编译；首次编译因SDK客户端不实现AutoCloseable而失败，改为finally显式close后成功，未改生产源码。编译不计功能测试。
- 无收费预检2次：首次MySQL SQLSyntaxErrorException退出1，调整凭据选择后退出0；两次均0模型请求、0文档上传。未输出完整异常正文。
- 正式真实管道执行1次，退出0；真实模型请求1次成功；同一执行内的MySQL/SDK逐条验收及复用向量搜索通过。独立前后MySQL保全、SDK集合盘点和服务状态核对通过。
- JUnit/Surefire、Unit/Mock、MockMvc及全量回归均 **0次，未运行，T/F/E/S不适用**。未为增加测试数量写受保护测试库，未把E1–E3的150项历史测试当成本轮结果。
- 未进行收费失败/补偿、在途写入中断、重启、断电、长期稳定或压力测试；本次成功不能证明这些场景。原Milvus停止panic及启动竞态风险仍保留。
- 未启动完整面试、AI评分、DeepSeek或其他文档处理。H01审查及D1题库扩容、D2随机组卷、D3语料扩容的准入仍按E1–E3报告，E01成功不自动批准其他Embedding调用。
- 未追加停服离线备份。已有离线恢复点早于E01；本次MySQL后置导出不等于包含新向量的同步跨库备份。账单核对和独立语义检索质量仍未验证。

## 6. 文件、证据与Git

新增本报告：`/Users/kun/Developer/projects/ai-interview-system/docs/verification/E3_E01_REAL_INGESTION_20261114.md`。旧阻塞报告增加后续完成链接；同步更新：

- `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/IMPROVEMENT_LEDGER.md`
- `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/SOURCE_LEARNING_BACKLOG.md`

本机证据：`/Users/kun/Developer/projects/ai-interview-system/backups/E3_E01_LIVE_20261113/`，目录0700、文件0600、Git忽略；包含临时执行器、脱敏响应元数据、逐条对账摘要、前后逻辑导出和服务盘点。`artifact-manifest.json`列文件绝对路径、字节数和SHA-256。未记录Key、授权头、完整异常正文或完整向量数组。原始业务导出仅保留在本地受保护证据目录。

执行使用已有 `target/classes`；其255个文件（238个class、17个资源）与C7从源码提交 `71aeb22f7da906660763e9efb1100a20df7705c9` 干净导出并独立编译的产物逐文件相同，文件集合摘要 `ab345adbd0c8d23bb6ff5e3f7aaebef567621afe5afa1730884bbd48db3e6c8a`。该提交到HEAD及当前工作区均无生产源码/POM差异；详见 `source-baseline.json`。本轮只编译临时执行器，没有声称重编全项目或重跑历史测试。

HEAD保持不变，本轮没有暂存、提交、推送或其他Git写操作；本报告/台账修改无新提交SHA。远端本轮未重新查询，2026-11-13历史核验为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，当时本地领先8。学习条目新增L-E3E01-02，全部仍待学习、掌握未确认。未改Codex自身模型提供商或全局配置。
