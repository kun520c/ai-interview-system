# E07 evaluation 数据就绪核验

**Gate B FAIL：EVALUATION_DATA_NOT_READY。MySQL隔离结构可读但语料全空；Milvus无法连接，其数据状态UNKNOWN。已停止后续探测。**

## 真实目标与隔离

| 对象 | evaluation目标 | local对比目标 | 当前证据 |
| --- | --- | --- | --- |
| MySQL | localhost:3306 / ai_interview_evaluation | localhost:3306 / ai_interview | eval实际SELECT DATABASE确认；local未连接 |
| Milvus | http://127.0.0.1:19530 / default / knowledge_chunk_vectors_evaluation | 同配置物理host/database / knowledge_chunk_vectors_local | eval首次describe失败；local未查询 |

同一配置物理host和default数据库不代表独立两套物理基础设施，schema/collection才是数据落点边界。MySQL实际server UUID `6ccac806-1bb1-11f1-b45f-107c61ba59a5` 与既有Phase0.5只读工具的本机预期标识一致，schema仍独立；没有声称账号GRANT级隔离。当前Milvus物理服务身份无法核实。

## MySQL只读证据

原EnvironmentIsolationInitializer在读工具SpringEmptyConfig加载中执行，再解析目标；不加载私有local YAML或生产DataSource Bean。已有合法DB凭据从Windows User/Process只读获取，经stdin送入JDBC工具内存，不修改环境。连接限制为已解析loopback+精确ai_interview_evaluation，autoReconnect=false、allowMultiQueries=false；JDBC readOnly=true，REPEATABLE_READ事务读完rollback，没有FOR UPDATE、DDL/DML。

13核心表均InnoDB，列与主键标识及15物理FK读取成功（PASS）；没有进行SHOW CREATE/全部索引/CHECK的完整DDL等价证明（UNKNOWN）。真实count如下：

| 表 | eval实际行数 |
| --- | ---: |
| user | 0 |
| question | 0 |
| question_scoring_point | 0 |
| interview_session | 0 |
| interview_question | 0 |
| interview_answer | 0 |
| answer_evaluation | 0 |
| knowledge_document | 0 |
| knowledge_chunk | 0 |
| rag_retrieval_batch | 0 |
| rag_hit_log | 0 |
| interview_report | 0 |
| user_weakness | 0 |

题库21→0、评分点97→0、知识文档21→0、chunks52→0，全部冻结语料缺失。benchmark里的4道MAIN题目及所有对应评分点也必然不存在；不能只凭空库结构相同声称内容一致。

| bank | 冻结questionId | 冻结scoringPointIds | eval状态 |
| --- | --- | --- | --- |
| E02 | 7613 | 2480, 2481, 2482, 2483 | 全缺失 |
| M04 | 7620 | 2511, 2512, 2513, 2514 | 全缺失 |
| H05 | 7628 | 2549, 2550, 2551, 2552, 2553 | 全缺失 |
| H09 | 7632 | 2569, 2570, 2571, 2572 | 全缺失 |

在count门禁失败后没有继续读取内容或查询local原文，因此frozen full-row SHA、document/chunk/vector identity及metadata匹配UNKNOWN。已有E06 SHA仅是校验基准，不能从哈希还原数据或宣称迁移源当前保持不变。此阶段没有读取user账户/密码行；user表只执行COUNT(*)。

已读实际FK：question_scoring_point.question_id→question.id、knowledge_chunk.document_id→knowledge_document.id。四个语料表没有user FK，所以未来准备冻结语料不需要复制user或新建用户父行；其它interview/RAG/user_weakness关联不属于本轮初始化数据范围。

## Milvus只读探测

参考已有Phase0.5合法REST读路径，但新工具只允许describe/indexes/query/stats，完全排除insert/delete/create/drop/load和模型/Embedding调用；不创建Spring Milvus Bean，不修改false，不调用可能createCollection的生产Initializer。初始请求：`POST /v2/vectordb/collections/describe`，dbName=default，collectionName=knowledge_chunk_vectors_evaluation。仅1次，requests无重试、不跟重定向，凭据Header不记录。

请求ConnectionError，未获取HTTP状态/响应；Collection存在性、真实1024维、AUTOINDEX/COSINE、向量52、embedding_version、ID对应、浮点数组哈希及跨库一致性全部UNKNOWN。没有执行后续index/query/stats/search，没有以local缓存top5冒作evaluation。

一次Docker只读inventory因Linux engine named pipe不可用失败。它支持“当前Docker API未可用”的结论，不能证明Collection被删、为空或Schema错误。没有启动Docker/容器，没有检查宿主进程来猜测数据存在性。仓库未找到Compose部署定义，不杜撰其内容。

8个冻结query及真实topK5复用条件尚未满足：本轮不能调用Embedding生成query向量，也未验证可直接使用的8个冻结query向量字节。先准备语料/52既有向量后，需另授权只读检索验证，并证明query向量来源/模型/profile/SHA一致；若缺query vector artifact则交Commander裁定单独获取许可，不能隐式新增真实模型调用。

## 停止与边界

两个独立受控探测同时开始，首次结果捕获后停止，未重试。实际MySQL连接1/SELECT17，Milvus只读HTTP尝试1/成功响应0，Docker读取命令1，模型/Embedding/搜索/数据写入0。源码及新工具没有数据库或向量写路径；MySQL只有一次一致性读，没有实时前后完整数据hash，因此不声称“全数据库保存完整性PASS”。历史文件保存已另行全SHA复核。

下一步需要Commander审批隔离数据与基础设施准备；只设flag=true不能解决此FAIL，未授权新Canary。详细方案见PHASE_5_E07_EVAL_REPAIR_PROPOSAL.md。
