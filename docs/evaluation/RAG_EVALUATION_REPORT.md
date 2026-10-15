# RAG 真实检索 baseline

结论：**NEEDS_TUNING**。补充语料后25条正例Top1全中，但Top5正例precision仅28%，5个负例全部返回无关证据。原始知识覆盖严重不足。不能仅凭Hit@1=100%接受当前评分证据质量。

## 协议与标注

30条：5个现有EASY题目 × 原词、同义、口语、不完整题干、混淆表达5种变体=25正例，加5个无对应知识负例。口语/不完整变体改的是题干与评分点，不是把候选人回答加入检索。真实 MAIN 查询仍为 `question + scoring points`；FOLLOW_UP查询为MAIN题干+追问+目标评分点。所有生产主问题来源题库，没有改为LLM生成。

预期主题与标签由评测者在观察检索前固定：[existing-golden.json](data/existing-golden.json)。新增语料经真实上传处理后，在检索前绑定chunk IDs：[expanded-golden.json](data/expanded-golden.json)。这些是**评测者标注的小型受控集**，不是本人独立人工gold。新增语料与回答来自同一技术事实集，语言相近，正例命中结果存在乐观偏差；不是未见主题的泛化结果。

每条实际调用现有 EvaluationRetrievalAdapter → SpringAiEmbeddingClient → MilvusVectorStoreClient → MyBatis eligibility。TopK固定5，Hit@1/3/5来自同一次limit=5的前缀，没有伪装成分别请求三种TopK。完整query、expected IDs、Top1/3/5、similarity、relevant/irrelevant见[RAG_RETRIEVAL_TRACES.md](RAG_RETRIEVAL_TRACES.md)；raw vectorId和metadata见[evidence](evidence/rag-expanded.json)。60次query运行均无服务错误。

## 指标

| 语料 | 有对应知识正例/25 | Hit@1 | Hit@3 | Hit@5 | MRR | 正例Precision@5 | 负例误证据率 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 原知识库 | 5 | 20.0% | 20.0% | 20.0% | 0.200 | 20.0% | 100.0% |
| 补充语料 | 25 | 100.0% | 100.0% | 100.0% | 1.000 | 28.0% | 100.0% |

Hit@K分母为25正例，未找到对应语料也记为系统漏召回；负例单独算错误证据率，不进入Hit分母。Precision是各query实际返回前缀中相关项的比例，再对正例宏平均；返回不足K时分母用实际返回数。原库仅5/25正例有可用知识，覆盖条件下Hit@1/3/5=100%、MRR=1，但只有一个候选切片，不能说明排序优良。原库20个系统漏召回是知识缺失，不应归因于Embedding排序。

补充语料：Hit@1/3/5=100%、MRR=1。宏平均chunk Recall@1=86.7%，Recall@3/5=100%；equals主题有3个相关切片，其他主题各1个，所以Top1 recall不必为100%。正例Precision@1=100%、@3=46.7%、@5=28%；在125个正例Top5位置中90个无关。总体150位置中115个无关。Recall只对有完整相关chunk标签的正例计算，负例/原库缺资料行的Recall不定义。[计算结果](evidence/rag-metrics.json)。

## Pipeline 与切片

| 主题 | 文档 ID | chunk IDs | 长度（UTF-16） |
| --- | --- | --- | --- |
| equals | 542 | [484, 485] | [1050, 861] |
| arraylist | 543 | [486] | [355] |
| ioc | 544 | [487] | [282] |
| index | 545 | [488] | [245] |
| tcp | 546 | [489] | [393] |
| confusion | 547 | [490] | [208] |

原文档485/切片443保留。6份新增文档共7个新切片，经真实HTTP multipart上传，再显式process，全部READY/ACTIVE。Embedding qwen3.7-text-embedding/1024维/profile-v1与Collection匹配，schema动态字段关闭、Java vector_id、AUTOINDEX/COSINE。[pipeline.json](evidence/pipeline.json)同时保存源文档、drafts、持久化chunk、vector IDs、实际检索。

当前max=1200、overlap目标150、自然边界从800开始，按段落→换行→句号→hard cut。最长文档切片1050/861，实测连续公共重叠115字符；strip和向前寻找空白使实际重叠不保证精确150。索引连续，无空片，长度均≤1200，token_count=NULL，没有用字符数冒充token。当前策略在这一个长文档上工作，未证明对代码块、超长单词、复杂Markdown或跨切片事实的最优性，暂不改参数。

## Metadata / MySQL filtering

真实测试本轮文档542的 INACTIVE、非READY、document_version不符、embedding_model不符、embedding_version不符；每次Milvus rawHits=5而合格items=3，目标两个chunk确实被剔除，恢复后重新可检索。[metadata-filters.json](evidence/metadata-filters.json)。这是实库状态过滤，不是Mock。

Milvus仅过滤embedding_version；MySQL过滤ACTIVE/READY/currentVersion/model/profile，并交叉校验documentId/chunkIndex。**没有questionId、scoring-point ID或category过滤，也没有similarityThreshold**。没有过滤后补足TopK；这5种场景都从5变3，下一候选不会自动补入。当前TopK表示原始候选上限，不保证最终5条。

## 明显 FALSE POSITIVE

| Case | 问题 | 实际Top1（ID:score:相关性） |
| --- | --- | --- |
| N1 | 如何做番茄炒鸡蛋？ | 443:0.2712:I |
| N2 | 周末在海边拍照应该穿什么？ | 488:0.2900:I |
| N3 | HTTP/3 的 QUIC 是否使用 TCP 三次握手？ | 489:0.6430:I |
| N4 | 如何计算 JVM G1 的混合回收触发条件？ | 443:0.3342:I |
| N5 | Kubernetes 多租户隔离网络策略如何设计？ | 443:0.3224:I |

N3 HTTP/3/QUIC错误命中TCP资料，score=0.6430，是容易混淆主题的真实FP；资料说TCP三次握手，不能据此替代HTTP/3/QUIC知识。其余负例分数约0.27–0.33仍进入prompt。正例中Spring/索引/TCP的后续结果混入HashMap、Redis等；不是状态过滤失效，是状态正确但语义无关。

## 明显 FALSE NEGATIVE

原库Q6779-*、Q6780-*、Q6781-*、Q6782-*共20条，expected knowledge不存在，却返回HashMap片段，形成“漏掉正确知识+塞入错误证据”。补充语料后这30条中没有标注正例FN；只有5个主题、25条变体，不能断言系统没有FN。

## Baseline 后的建议（本轮未调参）

先补齐并人工审核真实知识，再建立独立未见query。固定本次gold，在后续实验一次只改变一个变量：优先评估相似度拒绝或小TopK，记录precision和负例错误率，再决定是否加简单领域限制。此集最低正例Top1约0.701、最高负例约0.643，可用作下一轮阈值候选的离线依据；**不能直接据此把0.65写入生产**，需要留出集与更多近邻负例。严格category过滤也可能漏掉跨领域正确知识，需独立比较。

没有调整chunkSize、overlap、TopK、prompt、threshold。两组指标的变化仅来自语料覆盖增加，属于原库/受控语料两种baseline，不是调参优化的Before/After。
