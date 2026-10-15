# Phase 4B 冻结基线检索报告

执行完整性：**PASS**。检索质量：**BASELINE_RECORDED**。21 道冻结 MAIN 查询各执行一次 topK=5 的真实生产检索。本文只记录当前行为；Phase 4C 决策等待 Commander 审核。

执行窗口：2026-10-05T16:52:48.120766500Z — 2026-10-05T16:52:57.453889900Z（UTC；本地日期 2026-10-06）。JDK 21.0.11。

## 1. 必需摘要

以下值均为本次实测或从本次唯一结果计算的宏平均；比率使用 0–1，1 表示 100%。

```text
QUERY_COUNT: 21
SUCCESSFUL_RETRIEVALS: 21
TOP_K: 5
EMBEDDING_CALLS: 21
MILVUS_SEARCH_CALLS: 21
DEEPSEEK_CALLS: 0
JEV_CALLS: 0
TOTAL_RAW_HITS: 105
TOTAL_EVIDENCE_HITS: 105
RAW_HITS_FILTERED_OUT: 0
QUERIES_WITH_RAW_EVIDENCE_COUNT_MISMATCH: 0
POSITIVE_QUERY_COUNT: 9
NO_DIRECT_GOLD_QUERY_COUNT: 12
CHUNK_HIT_AT_1: 0.888888889
CHUNK_HIT_AT_3: 0.888888889
CHUNK_HIT_AT_5: 1
CHUNK_PRECISION_AT_1: 0.888888889
CHUNK_PRECISION_AT_3: 0.740740741
CHUNK_PRECISION_AT_5: 0.466666667
CHUNK_RECALL_AT_1: 0.407407407
CHUNK_RECALL_AT_3: 0.888888889
CHUNK_RECALL_AT_5: 1
CHUNK_MRR_AT_5: 0.916666667
DOCUMENT_HIT_AT_1: 1
DOCUMENT_HIT_AT_3: 1
DOCUMENT_HIT_AT_5: 1
DOCUMENT_PRECISION_AT_1: 1
DOCUMENT_PRECISION_AT_3: 0.333333333
DOCUMENT_PRECISION_AT_5: 0.2
DOCUMENT_RECALL_AT_1: 1
DOCUMENT_RECALL_AT_3: 1
DOCUMENT_RECALL_AT_5: 1
DOCUMENT_MRR_AT_5: 1
NO_DIRECT_FALSE_EVIDENCE_RATE_AT_1: 1
NO_DIRECT_FALSE_EVIDENCE_RATE_AT_3: 1
NO_DIRECT_FALSE_EVIDENCE_RATE_AT_5: 1
NO_DIRECT_RELATED_ONLY_HIT_RATE_AT_1: 0.416666667
NO_DIRECT_RELATED_ONLY_HIT_RATE_AT_3: 0.416666667
NO_DIRECT_RELATED_ONLY_HIT_RATE_AT_5: 0.416666667
NO_DIRECT_IRRELEVANT_HIT_RATE_AT_1: 0.583333333
NO_DIRECT_IRRELEVANT_HIT_RATE_AT_3: 0.75
NO_DIRECT_IRRELEVANT_HIT_RATE_AT_5: 1
CANONICAL_5_HIT_AT_1: 1
CANONICAL_5_HIT_AT_3: 1
CANONICAL_5_HIT_AT_5: 1
CANONICAL_5_MRR_AT_5: 1
CROSS_TOPIC_4_HIT_AT_1: 0.75
CROSS_TOPIC_4_HIT_AT_3: 0.75
CROSS_TOPIC_4_HIT_AT_5: 1
CROSS_TOPIC_4_MRR_AT_5: 0.8125
SAME_DOCUMENT_MULTI_HIT_QUERY_RATE: 1
AVERAGE_DISTINCT_DOCUMENTS_IN_TOP5: 2.571428571
MAX_SAME_DOCUMENT_HITS_IN_ONE_QUERY: 3
M05_3_HIT_COUNT: 5
H07_3_HIT_COUNT: 3
RAG_RETRIEVAL_BATCH_COUNT_POST: 0
RAG_HIT_LOG_COUNT_POST: 0
MYSQL_WRITES: 0
MILVUS_WRITES: 0
```

## 2. 冻结与生产代码完整性

| 冻结文件 | 执行前后 SHA-256 |
| --- | --- |
| docs/rag/evaluation/PHASE_4A_PRODUCTION_QUERY_SET.json | 8251e2ee7307b9a03ec258328e7f29d91475d51b640f11a37c4f04d29e029d35 |
| docs/rag/evaluation/PHASE_4A_GOLDEN_RELEVANCE.json | f3870b3432856dd44efbe384656367b798bb730d4c9313bdc6cee6259ac1cd22 |
| docs/rag/evaluation/PHASE_4A_GOLDEN_RELEVANCE_REVIEW.md | b12685faf1a79f221ab970b0ff34d01772526ad56972b37ed38fcfe1b5e91c54 |
| docs/rag/PHASE_4A_GOLDEN_SET_REPORT.md | 63429f9ea73cdbcad00f1a065250830b27333d9803a442aadc31faf7e777c862 |


冻结集合：21 queries / 14 chunks / 294 judgments；DIRECT=21、RELATED_ONLY=34、IRRELEVANT=239；POSITIVE=9、NO_DIRECT_GOLD=12、UNCERTAIN=0。所有实际 Query UTF-8 SHA-256 在检索前逐题核验为 21/21；还在第一笔 Embedding 前整体核验全部 Query。用户答案及追问内容均为 null，followUpTargetPointIds 为空。题干、参考答案及97个评分点与当前 DB/冻结题库逐字匹配。

| 当前工作树源码/Mapper | 执行前 SHA-256 | 执行后 SHA-256 | 一致 |
| --- | --- | --- | --- |
| src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRagQueryBuilder.java | 2866ca2aeccd9c7da473a31107e4b9a87d298bf4b0495482715c262a115bd97c | 2866ca2aeccd9c7da473a31107e4b9a87d298bf4b0495482715c262a115bd97c | PASS |
| src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRetrievalAdapter.java | 9aa5a8abc76d4f74169283c388ca2e587684d0e2f5f088ecfb790ef1ad5b2cda | 9aa5a8abc76d4f74169283c388ca2e587684d0e2f5f088ecfb790ef1ad5b2cda | PASS |
| src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalService.java | 45b1707f9b1b9c1462e8cd3cdf575258edc97d3c0051d74164d9c44c711aa70c | 45b1707f9b1b9c1462e8cd3cdf575258edc97d3c0051d74164d9c44c711aa70c | PASS |
| src/main/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingClient.java | ebd342809aea17c48721d8f57777aaea939e10ca66e8a83c3e0b35836b1d4642 | ebd342809aea17c48721d8f57777aaea939e10ca66e8a83c3e0b35836b1d4642 | PASS |
| src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusVectorStoreClient.java | 5c585e380420a4b7d90850e42c59eae0ab7c4888072645803bc59039058d91d1 | 5c585e380420a4b7d90850e42c59eae0ab7c4888072645803bc59039058d91d1 | PASS |
| src/main/java/com/kun/aiinterview/knowledge/mapper/KnowledgeChunkMapper.java | dc1d633689fe21b1891acea6ad02f375f1a394ce9068d90aed9048430350bc44 | dc1d633689fe21b1891acea6ad02f375f1a394ce9068d90aed9048430350bc44 | PASS |
| src/main/resources/mapper/KnowledgeChunkMapper.xml | 63889346dce0bb981b7df9132f78aa976e07f7e942f06b7778cda4a6a000e2dc | 63889346dce0bb981b7df9132f78aa976e07f7e942f06b7778cda4a6a000e2dc | PASS |
| src/main/java/com/kun/aiinterview/knowledge/embedding/EmbeddingConfiguration.java | a3a17632bb4b0ca72bf29eeea478946367f198b03b7be8ef26c3d7ff8126aa52 | a3a17632bb4b0ca72bf29eeea478946367f198b03b7be8ef26c3d7ff8126aa52 | PASS |
| src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusConfiguration.java | 15dcca7ac419f5568e028498e2d9ef0fd3a4d72b1bcdb3fe35f3cf4c4838c53b | 15dcca7ac419f5568e028498e2d9ef0fd3a4d72b1bcdb3fe35f3cf4c4838c53b | PASS |
| src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusCollectionInitializer.java | 57d675fce5cdd9652ca709fe7378b4ff5e2447b15a12a4a202bdb63ff8a2b9dd | 57d675fce5cdd9652ca709fe7378b4ff5e2447b15a12a4a202bdb63ff8a2b9dd | PASS |
| src/main/java/com/kun/aiinterview/config/EnvironmentIsolationInitializer.java | a4de695816d6a111dae040219017da340653b7bc3f8735bad76f03825e8e9bce | a4de695816d6a111dae040219017da340653b7bc3f8735bad76f03825e8e9bce | PASS |


受保护既有文件 843 个，前后内容变化 0；排除 generated/cache 和本次临时目录。新增交付文件仅为本阶段三份报告。所有 181 个当前生产 Java 源文件以 JDK21 重新编译到临时目录，运行时优先使用这些类及工作树 resources；依赖从现有本地 Spring Boot jar 解出，不下载、不使用远程源码。

## 3. 运行环境与前后核验

Normal local：MySQL `localhost:3306/ai_interview`，服务器 8.0.45；Milvus `http://localhost:19530`，`default/knowledge_chunk_vectors_local`，1024 / COSINE / AUTOINDEX；Embedding `qwen3.7-text-embedding`，profile `qwen3.7-text-embedding-1024-dense-v1`。

| MySQL 表 | 前 count | 后 count | 前后全行 SHA-256 |
| --- | --- | --- | --- |
| question | 21 | 21 | 3f0fd117120cbc21a21dd741573d41156f70d29353209a75ae28555de754f504 |
| question_scoring_point | 97 | 97 | a0a4ff0dee5b46c3bb46854dee73d01645f2cfff8f7baaef0e2f9f603ec0c34d |
| knowledge_document | 5 | 5 | cc0b0165cc3c1d4f3fabc2d5fb6acc7d646c160a145f989f01e67890d54c66e9 |
| knowledge_chunk | 14 | 14 | 759ea11b6c3780e477802e31f03349533b5722dee77f95b6ce052d99432e11af |
| rag_retrieval_batch | 0 | 0 | 4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945 |
| rag_hit_log | 0 | 0 | 4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945 |


前后均为5个READY/current version=1文档、14个ACTIVE/current version=1切片，14个golden vectorId逐项匹配MySQL chunkId/documentId/chunkIndex/内容与Milvus metadata；无额外或丢失身份。文档自身content_hash与原文匹配。Milvus元数据、Schema和完整14个既有文档向量的摘要前后一致；仅摘要进入交付，不保存query embedding向量。

| Milvus 核验 | 前 | 后 |
| --- | --- | --- |
| entityCount | 14 | 14 |
| consistency | STRONG | STRONG |
| dimension | 1024 | 1024 |
| metric | COSINE | COSINE |
| index | AUTOINDEX | AUTOINDEX |
| schemaSha256 | 048944ca2d38365515e2d4ae8afd5cbcd74e01355c3e9ffbdc3f6d838a6222e9 | 048944ca2d38365515e2d4ae8afd5cbcd74e01355c3e9ffbdc3f6d838a6222e9 |
| corpusRowsSha256 | 54f88c31f9f54b3ed4f2349b20a403fdfbc219fa908115fb8f86475c54bcff7b | 54f88c31f9f54b3ed4f2349b20a403fdfbc219fa908115fb8f86475c54bcff7b |


SQL使用独立read-only JDBC连接，服务器确认transaction_read_only=1、REPEATABLE-READ。后核验重新连接，避免复用前置事务快照。全表行摘要范围为上列6表。没有调用任何写入SQL、Trace服务、Milvus写入或Schema操作。生产Collection初始化Runner未调用；只执行它现有的只读Schema/index校验方法，Collection不存在会停止。

## 4. 实际生产路径与调用计数

```text
EvaluationRagQueryBuilder -> EvaluationRetrievalAdapter.retrieve(context,5) -> KnowledgeRetrievalService.retrieve(query,5) -> production SpringAiEmbeddingClient/EmbeddingModel -> production MilvusVectorStoreClient.search -> real MyBatis KnowledgeChunkMapper.selectRetrievableByVectorIds -> EvaluationRetrievalResult
```

临时Spring Boot context仅导入生产EmbeddingConfiguration与MilvusConfiguration，保留项目EnvironmentIsolationInitializer，并在bean初始化前额外限制normal-local目标。无业务组件扫描、SQL自动初始化、完整评判编排或judge bean。计数delegate转发原始参数到真实生产adapter；MyBatis使用当前KnowledgeChunkMapper.xml及真实数据库，只允许selectRetrievableByVectorIds。

EmbeddingClient=21、MilvusVectorStoreClient.search=21、真实MyBatis检索SELECT=21；没有额外Embedding探针、独立topK1/3检索、重试Query、DeepSeek或Jev调用。生产Embedding一次attempt配置保持原样。

临时工具第一次只读预检因Trace表不存在id排序列而收到SQLSyntaxErrorException，发生于MYSQL_PREFLIGHT，bankId=null，successful/Embedding/Search/writes均为0。只修正临时工具对空Trace表的SELECT排序后继续预检；不是数据漂移、生产源码修复或失败Query重试。时间和错误链保存在RESULTS.json.runtime.temporaryToolPreflightCorrection。正式21题运行期间无失败、无重试。

## 5. 冻结指标与宏平均

仅DIRECT相关。Chunk按未经压缩的生产vectorRank取前K；Precision分母固定K，Recall分母为本题所有DIRECT gold chunks。MRR取首个DIRECT的倒数排名。Document仅派生自同一top-five evidence的稳定首次去重，相关文档含至少一个DIRECT gold chunk，Precision仍固定K。所有正例宏平均只含9题；NO_DIRECT诊断只含12题。冻结metricDefinitions原文和摘要存于RESULTS.json，没有事后通过阈值。

| 指标 | Chunk / 9题 | 派生 Document / 9题 |
| --- | --- | --- |
| Hit@1 | 0.888888889 | 1 |
| Precision@1 | 0.888888889 | 1 |
| Recall@1 | 0.407407407 | 1 |
| Hit@3 | 0.888888889 | 1 |
| Precision@3 | 0.740740741 | 0.333333333 |
| Recall@3 | 0.888888889 | 1 |
| Hit@5 | 1 | 1 |
| Precision@5 | 0.466666667 | 0.2 |
| Recall@5 | 1 | 1 |
| MRR@5 | 0.916666667 | 1 |


| NO_DIRECT_GOLD / 12题 | @1 | @3 | @5 |
| --- | --- | --- | --- |
| FALSE_DIRECT_EVIDENCE_RATE | 1 | 1 | 1 |
| RELATED_ONLY_HIT_RATE | 0.416666667 | 0.416666667 | 0.416666667 |
| IRRELEVANT_HIT_RATE | 0.583333333 | 0.75 | 1 |


12道NO_DIRECT_GOLD均返回非空evidence，因此FALSE_DIRECT_EVIDENCE_RATE@1/3/5数学上均为100%，符合当前固定topK=5、无相似度阈值、无abstention的合同。这体现稀疏五主题语料对没有DIRECT gold题目仍准入证据；不是指标bug，也不能仅据此称Embedding检索失败。RELATED_ONLY与IRRELEVANT事件允许重叠。

## 6. Canonical 与 Cross-topic 子集

| 子集 | 题数 | Hit@1 | Hit@3 | Hit@5 | MRR@5 |
| --- | --- | --- | --- | --- | --- |
| Canonical | 5 | 1 | 1 | 1 | 1 |
| Cross-topic | 4 | 0.75 | 0.75 | 1 | 0.8125 |


| Bank ID | 子集 | top1 chunk | top1 label | top1 score | 首个DIRECT rank | 来源文档首个rank | DIRECT top5数 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| E04 | Cross-topic | H06-2 | DIRECT | 0.586621225 | 1 | 1 | 2 |
| M01 | Cross-topic | M02-1 | DIRECT | 0.50064826 | 1 | 1 | 1 |
| M02 | Canonical | M02-1 | DIRECT | 0.915717363 | 1 | 1 | 3 |
| M04 | Canonical | M04-1 | DIRECT | 0.869748712 | 1 | 1 | 2 |
| M05 | Canonical | M05-1 | DIRECT | 0.817581892 | 1 | 1 | 3 |
| H05 | Cross-topic | M05-2 | DIRECT | 0.718982518 | 1 | 1 | 3 |
| H06 | Canonical | H06-1 | DIRECT | 0.915229738 | 1 | 1 | 3 |
| H07 | Canonical | H07-1 | DIRECT | 0.84299612 | 1 | 1 | 3 |
| H09 | Cross-topic | H06-1 | RELATED_ONLY | 0.534749866 | 4 | 1 | 1 |


## 7. 排名标签构成与 eligibility

| Bank ID | classification | top5冻结标签 | raw/evidence |
| --- | --- | --- | --- |
| E01 | NO_DIRECT_GOLD | IRRELEVANT, IRRELEVANT, IRRELEVANT, IRRELEVANT, IRRELEVANT | 5/5 |
| E02 | NO_DIRECT_GOLD | RELATED_ONLY, RELATED_ONLY, IRRELEVANT, IRRELEVANT, IRRELEVANT | 5/5 |
| E03 | NO_DIRECT_GOLD | RELATED_ONLY, RELATED_ONLY, RELATED_ONLY, IRRELEVANT, IRRELEVANT | 5/5 |
| E04 | POSITIVE | DIRECT, RELATED_ONLY, DIRECT, IRRELEVANT, IRRELEVANT | 5/5 |
| E05 | NO_DIRECT_GOLD | IRRELEVANT, IRRELEVANT, IRRELEVANT, IRRELEVANT, IRRELEVANT | 5/5 |
| M01 | POSITIVE | DIRECT, RELATED_ONLY, RELATED_ONLY, IRRELEVANT, IRRELEVANT | 5/5 |
| M02 | POSITIVE | DIRECT, DIRECT, DIRECT, IRRELEVANT, IRRELEVANT | 5/5 |
| M03 | NO_DIRECT_GOLD | IRRELEVANT, IRRELEVANT, IRRELEVANT, IRRELEVANT, IRRELEVANT | 5/5 |
| M04 | POSITIVE | DIRECT, DIRECT, IRRELEVANT, IRRELEVANT, IRRELEVANT | 5/5 |
| M05 | POSITIVE | DIRECT, DIRECT, DIRECT, IRRELEVANT, IRRELEVANT | 5/5 |
| M06 | NO_DIRECT_GOLD | RELATED_ONLY, IRRELEVANT, RELATED_ONLY, RELATED_ONLY, IRRELEVANT | 5/5 |
| M07 | NO_DIRECT_GOLD | RELATED_ONLY, RELATED_ONLY, RELATED_ONLY, IRRELEVANT, IRRELEVANT | 5/5 |
| H01 | NO_DIRECT_GOLD | IRRELEVANT, IRRELEVANT, IRRELEVANT, IRRELEVANT, IRRELEVANT | 5/5 |
| H02 | NO_DIRECT_GOLD | RELATED_ONLY, RELATED_ONLY, RELATED_ONLY, IRRELEVANT, RELATED_ONLY | 5/5 |
| H03 | NO_DIRECT_GOLD | IRRELEVANT, IRRELEVANT, IRRELEVANT, IRRELEVANT, IRRELEVANT | 5/5 |
| H04 | NO_DIRECT_GOLD | IRRELEVANT, IRRELEVANT, IRRELEVANT, IRRELEVANT, IRRELEVANT | 5/5 |
| H05 | POSITIVE | DIRECT, DIRECT, DIRECT, IRRELEVANT, RELATED_ONLY | 5/5 |
| H06 | POSITIVE | DIRECT, DIRECT, DIRECT, IRRELEVANT, IRRELEVANT | 5/5 |
| H07 | POSITIVE | DIRECT, DIRECT, DIRECT, IRRELEVANT, IRRELEVANT | 5/5 |
| H08 | NO_DIRECT_GOLD | IRRELEVANT, IRRELEVANT, IRRELEVANT, IRRELEVANT, IRRELEVANT | 5/5 |
| H09 | POSITIVE | RELATED_ONLY, RELATED_ONLY, RELATED_ONLY, DIRECT, RELATED_ONLY | 5/5 |


| 生产 rank | DIRECT | RELATED_ONLY | IRRELEVANT |
| --- | --- | --- | --- |
| 1 | 8 | 6 | 7 |
| 2 | 6 | 7 | 8 |
| 3 | 6 | 6 | 9 |
| 4 | 1 | 1 | 19 |
| 5 | 0 | 3 | 18 |
| 合计 | 21 | 23 | 61 |


TOTAL_RAW_HITS=105、TOTAL_EVIDENCE_HITS=105、RAW_HITS_FILTERED_OUT=0、QUERIES_WITH_RAW_EVIDENCE_COUNT_MISMATCH=0。本次无丢失、无rank gap、无未知身份。raw与evidence均完整保存；没有补位、over-fetch或修改MySQL过滤。

## 8. 同文档多命中与 LOW 尾片

| 诊断 | 值 |
| --- | --- |
| SAME_DOCUMENT_MULTI_HIT_QUERY_RATE | 1 |
| AVERAGE_DISTINCT_DOCUMENTS_IN_TOP5 | 2.571428571 |
| MAX_SAME_DOCUMENT_HITS_IN_ONE_QUERY | 3 |


| Bank ID | 不同文档数 | 重复槽位 | 单文档最多命中 |
| --- | --- | --- | --- |
| E01 | 3 | 2 | 2 |
| E02 | 3 | 2 | 3 |
| E03 | 3 | 2 | 3 |
| E04 | 2 | 3 | 3 |
| E05 | 4 | 1 | 2 |
| M01 | 2 | 3 | 3 |
| M02 | 3 | 2 | 3 |
| M03 | 3 | 2 | 2 |
| M04 | 2 | 3 | 3 |
| M05 | 2 | 3 | 3 |
| M06 | 2 | 3 | 3 |
| M07 | 2 | 3 | 3 |
| H01 | 3 | 2 | 2 |
| H02 | 3 | 2 | 3 |
| H03 | 3 | 2 | 2 |
| H04 | 2 | 3 | 3 |
| H05 | 3 | 2 | 3 |
| H06 | 2 | 3 | 3 |
| H07 | 3 | 2 | 3 |
| H08 | 2 | 3 | 3 |
| H09 | 2 | 3 | 3 |


| Bank ID | 重复文档 | ranks/chunks |
| --- | --- | --- |
| E01 | 577 | 1:M04-2, 2:M04-1 |
| E01 | 578 | 3:M05-3, 5:M05-1 |
| E02 | 576 | 1:M02-1, 2:M02-2, 3:M02-3 |
| E03 | 578 | 1:M05-2, 2:M05-3, 3:M05-1 |
| E04 | 579 | 1:H06-2, 2:H06-1, 3:H06-3 |
| E04 | 577 | 4:M04-1, 5:M04-2 |
| E05 | 580 | 2:H07-1, 4:H07-2 |
| M01 | 576 | 1:M02-1, 2:M02-3, 3:M02-2 |
| M01 | 577 | 4:M04-2, 5:M04-1 |
| M02 | 576 | 1:M02-1, 2:M02-2, 3:M02-3 |
| M03 | 577 | 1:M04-2, 2:M04-1 |
| M03 | 576 | 3:M02-3, 4:M02-1 |
| M04 | 577 | 1:M04-1, 2:M04-2 |
| M04 | 579 | 3:H06-3, 4:H06-1, 5:H06-2 |
| M05 | 578 | 1:M05-1, 2:M05-2, 3:M05-3 |
| M05 | 577 | 4:M04-2, 5:M04-1 |
| M06 | 579 | 1:H06-1, 3:H06-3, 4:H06-2 |
| M06 | 576 | 2:M02-1, 5:M02-2 |
| M07 | 580 | 1:H07-1, 2:H07-3, 3:H07-2 |
| M07 | 576 | 4:M02-1, 5:M02-3 |
| H01 | 576 | 1:M02-3, 5:M02-2 |
| H01 | 577 | 2:M04-2, 3:M04-1 |
| H02 | 576 | 1:M02-1, 2:M02-2, 3:M02-3 |
| H03 | 579 | 1:H06-2, 5:H06-3 |
| H03 | 577 | 2:M04-2, 4:M04-1 |
| H04 | 577 | 1:M04-2, 5:M04-1 |
| H04 | 576 | 2:M02-2, 3:M02-3, 4:M02-1 |
| H05 | 578 | 1:M05-2, 2:M05-3, 3:M05-1 |
| H06 | 579 | 1:H06-1, 2:H06-2, 3:H06-3 |
| H06 | 577 | 4:M04-1, 5:M04-2 |
| H07 | 580 | 1:H07-1, 2:H07-2, 3:H07-3 |
| H08 | 577 | 1:M04-2, 5:M04-1 |
| H08 | 580 | 2:H07-2, 3:H07-1, 4:H07-3 |
| H09 | 579 | 1:H06-1, 3:H06-3, 4:H06-2 |
| H09 | 580 | 2:H07-1, 5:H07-2 |


Phase2C的9条overlap-boundary P2 warning原样携带。同文档多命中仅是候选集中证据集中度诊断，不能把它直接等同于重复内容；本阶段未重新验证或调整切片边界。

### M05-3：5 次命中

| Bank ID | rank | label | score |
| --- | --- | --- | --- |
| E01 | 3 | IRRELEVANT | 0.263773978 |
| E03 | 2 | RELATED_ONLY | 0.367994815 |
| E05 | 3 | IRRELEVANT | 0.277427673 |
| M05 | 3 | DIRECT | 0.781364441 |
| H05 | 2 | DIRECT | 0.611378789 |


### H07-3：3 次命中

| Bank ID | rank | label | score |
| --- | --- | --- | --- |
| M07 | 2 | RELATED_ONLY | 0.455613881 |
| H07 | 3 | DIRECT | 0.762037456 |
| H08 | 4 | IRRELEVANT | 0.29839316 |


两条LOW尾片均保留。它们在冻结gold中也有DIRECT判断，因此LOW不等于全部无关或可删。没有在Chunk指标之前做文档去重。

## 9. 相似度分布与 margin

百分位方法：升序样本的零基位置(n−1)×p线性插值（R-7 / NumPy linear）。分数只用于观察，不重新判标签，不选择或推荐阈值。

| 冻结 label | count | min | p25 | median | p75 | max | mean |
| --- | --- | --- | --- | --- | --- | --- | --- |
| DIRECT | 21 | 0.50064826 | 0.611378789 | 0.781364441 | 0.84299612 | 0.915717363 | 0.746922944 |
| RELATED_ONLY | 23 | 0.342247367 | 0.383382246 | 0.458616585 | 0.531591356 | 0.656521916 | 0.463306296 |
| IRRELEVANT | 61 | 0.24822396 | 0.29839316 | 0.3355667 | 0.373332471 | 0.483203262 | 0.342795799 |


| POSITIVE Bank ID | best DIRECT | highest non-DIRECT | margin |
| --- | --- | --- | --- |
| E04 | 0.586621225 | 0.553303123 | 0.033318102 |
| M01 | 0.50064826 | 0.484699219 | 0.015949041 |
| M02 | 0.915717363 | 0.341864794 | 0.573852569 |
| M04 | 0.869748712 | 0.449322969 | 0.420425743 |
| M05 | 0.817581892 | 0.343832374 | 0.473749518 |
| H05 | 0.718982518 | 0.374831021 | 0.344151497 |
| H06 | 0.915229738 | 0.473080248 | 0.44214949 |
| H07 | 0.84299612 | 0.420553803 | 0.422442317 |
| H09 | 0.524487853 | 0.534749866 | -0.010262012 |


| NO_DIRECT Bank ID | top1 score | top5 min score |
| --- | --- | --- |
| E01 | 0.373332471 | 0.253384948 |
| E02 | 0.394769669 | 0.310265601 |
| E03 | 0.381789684 | 0.24822396 |
| E05 | 0.301545829 | 0.27262193 |
| M03 | 0.427465022 | 0.344950527 |
| M06 | 0.451177448 | 0.351409167 |
| M07 | 0.458616585 | 0.3355667 |
| H01 | 0.344070852 | 0.279635549 |
| H02 | 0.656521916 | 0.474096477 |
| H03 | 0.344538122 | 0.32537365 |
| H04 | 0.346717983 | 0.295065969 |
| H08 | 0.340284288 | 0.275321811 |


## 10. 实测优点、限制与验证

Canonical五题全部top1 DIRECT，top5取回全部各题DIRECT gold chunks。Cross-topic中的E04、M01、H05也是top1 DIRECT；H09只有第4名H06-2为DIRECT，第1名H06-1为RELATED_ONLY，DIRECT margin为负。H09的派生文档rank1相关不能替代它的chunk首次DIRECT rank4。

正例全部Hit@5且Chunk Recall@5=1，仍有non-DIRECT候选占据剩余top5槽位。105条返回中仅21条DIRECT，跨全部21题的标签构成不是正例Precision宏平均。所有21题均有同文档多切片占位，候选文档多样性见上表；NO_DIRECT题高分RELATED_ONLY也可能出现，例如H02。不同标签分数区间重叠，分数观察不改变冻结判断。

验证：离线Maven compile通过；JDK21从当前工作树重新编译全部生产类和临时工具通过；真实服务21/21检索成功，逐题Query/身份/标签核验通过；独立前后全行/向量摘要核验通过；离线两套算法交叉检查指标与报告字段。本阶段没有执行完整mvn test或自动化业务测试，避免产生不在范围内的DB写入；不把历史测试计数作为本次结果。临时工具的编译调整仅在临时目录发生。

## 11. 交付与停止

- `docs/rag/evaluation/PHASE_4B_BASELINE_RETRIEVAL_RESULTS.json`
- `docs/rag/evaluation/PHASE_4B_BASELINE_RETRIEVAL_REVIEW.md`
- `docs/rag/PHASE_4B_BASELINE_RETRIEVAL_REPORT.md`

JSON保存完整结果、raw/evidence、每题/聚合指标、代码摘要、前后运行核验、只读预检工具错误记录及最终临时Java执行工具源码（含其SHA-256），便于审核执行路径。临时依赖、class、日志和工具文件在交付前移除。没有修改生产源码/配置、冻结gold、题库或知识文档，没有提交或push。等待Commander审核，不进入Phase4C。

当前三份报告被现有 `.git/info/exclude` 第8行 `docs/` 规则忽略，属于本地交付，未暂存。未修改忽略规则。

PHASE 4B BASELINE RETRIEVAL EXECUTION GATE

PHASE4A_GOLDEN_HASHES_MATCH: PASS
QUERY_SET_21_INTACT: PASS
CORPUS_14_INTACT: PASS
PRODUCTION_QUERY_HASHES_21_21_MATCH: PASS
REAL_EVALUATION_RETRIEVAL_PATH_USED: PASS
TOPK_5_UNCHANGED: PASS
RETRIEVALS_21_21_SUCCESS: PASS
RETURNED_IDENTITIES_ALL_IN_FROZEN_CORPUS: PASS
FROZEN_LABELS_USED_WITHOUT_MUTATION: PASS
METRICS_COMPUTED_FROM_FROZEN_DEFINITIONS: PASS
DEEPSEEK_CALLS_0: PASS
JEV_CALLS_0: PASS
MYSQL_WRITES_0: PASS
MILVUS_WRITES_0: PASS
RAG_TRACE_TABLES_UNCHANGED: PASS
PRODUCTION_CODE_UNCHANGED: PASS

PHASE_4B_EXECUTION: PASS
PHASE_4B_RETRIEVAL_QUALITY: BASELINE_RECORDED
