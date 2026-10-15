# E07 source / target 与manifest审查

**Gate B/C NOT_RUN；bootstrap-manifest是阻断记录，executionPermitted=false。不能用它进行DML。**

## 固定授权身份

Source预期MySQL localhost:3306/ai_interview，Milvus127.0.0.1:19530/default/knowledge_chunk_vectors_local；始终只读。Target仅MySQL ai_interview_evaluation的question、question_scoring_point、knowledge_document、knowledge_chunk四表，Milvus default/knowledge_chunk_vectors_evaluation。user/认证及面试、报告、RAG、weakness表复制或写入不允许。

当前Bootstrap没有连接source/target，所以真实server/schema权限、SHOW CREATE TABLE、字段顺序/type/null/default/collation、indexes/FK/CHECK、PK/auto_increment兼容性均UNKNOWN。ENV-01此前“13空表/15FK”只是历史记录，不替本轮目标before证明。

## 冻结参考与本轮边界

Benchmark SHA `fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a`；E06同一算法的原参考如下：

| source域 | E06参考count | E06参考全行SHA | 本轮 |
| --- | ---: | --- | --- |
| question | 21 | a7762438ec109add66bc45f56316173d40b322c89cd55272c1e923c5f6b66705 | 未读取 |
| question_scoring_point | 97 | 6b521d2493a0b75b5c7c151b7f9d9f90198afa94bd68290a7f4b09ea814de68d | 未读取 |
| knowledge_document | 21 | 3a64b29224297cd7639058653d18b40ac20e352f10abc01d177ce901b72fa01d | 未读取 |
| knowledge_chunk | 52 | 9fe12f6b3f824e36e72dc6578da78f15917c69df836b1cbe40dd0fda4983d8f0 | 未读取 |

E06参考向量52，完整原值+metadata SHA `89c62c1f3bb4751f4ada6cd6f4ab62e8518eb660143f3892a29c67a7a6b25a9f`，模型qwen3.7-text-embedding、profile qwen3.7-text-embedding-1024-dense-v1、1024维、topK5。manifest保留52个冻结参考UUID但不标为当前实际实体，不做numeric转换或推断内容。

## 不可变阻断manifest

`target/phase5-e07-eval-bootstrap01/bootstrap-manifest.json` 独立SHA `130c0930348f666294c555059fd6e310e8cea79fa1c7e9442aa91b67bae720aa`，旁存bootstrap-manifest.sha256。source/target实时identity、snapshots、DDL比较和payloadDigest均未验证；新insert/no-op精确PK/UUID清单都为空，manifestMayBeUsedForDml=false。空清单的含义是“没有批准执行的已核验对象”，不能表示源/目标确实为空。

Gate C原逐行/逐vector摘要和关联/时间戳manifest未完成，不能制造hash、从历史hash反推值或将counts相同当许可。没有取得source全字段/浮点数组，没有任何user/secret导出。旧manifest、Prompt、gold和实验未重写。

后续必须经新执行范围按A部署→B精确结构/权限→C同算法全hash→D精确新插入顺序重新审核；不能跳过Gate A或沿用此不可执行manifest写入。
