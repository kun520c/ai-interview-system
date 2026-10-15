# Phase 5B E02 comparison review — stopped at canary

**EXECUTION FAIL；PROTOCOL / QUALITY NOT_EVALUATED。** 新epoch首个真实Jev请求E02-EXCELLENT返回HTTP503，立即停止，无重试，无第二case、DeepSeek反馈、baseline或5C。HTTP错误body和headers未保存，未获得模型ID/answers/usage；不可推断认证错误、数学兼容失败或模型质量差，也无法确定503源于服务端内部还是中间网络链路。

UTC: 2026-10-08T03:57:25.125689600Z → 2026-10-08T03:57:26.100125200Z；单次失败尝试观测耗时974.6516 ms（包含临时观察器本地证据开销）。请求api.typesafe.ai/v1/systemone，model=jev-latest。实际返回model=NOT_AVAILABLE，不能用E01的jev-1.13.0冒充本轮响应。DeepSeek实际绑定api.deepseek.com/deepseek-v4-flash，未调用。推理token usage与计费均NOT_AVAILABLE。

## 真实调用与分母

Jev质量HTTP=1（HTTP503），Jev primary成功=0/38；DeepSeek反馈=0，独立baseline=0/38；有效成对case=0/38。fallback触发请求=0、fallback实际评判=0、controlled Jev/DeepSeek=0。临时HTTP观察器在非2xx状态即抛出停止信号，先于JevClient HTTP处理和生产catch；这不证明生产503 fallback成功。按授权首异常停止，5C前置38case完整条件未满足。

Embedding真实HTTP8/8为200，模型qwen3.7-text-embedding；Milvus真实search8；4MAIN+4FOLLOW_UP查询，各hash检索一次。topK5，8×5=40被选择MySQL证据。38个输入已冻结于RUN_EVIDENCE.frozenInputs；每个候选答案、追问、题目、参考及评分点都与原benchmark逐项一致，query复用hash映射与E01一致。MAIN/FOLLOW_UP生产QueryBuilder均不含候选答案。PromptBuilder使用真实不可变证据缓存。仅首例Jev消费该Prompt，baseline未消费，因此不能声称成对效果已验证。

## 质量与业务门禁

所有micro/macro P/R/F1、exact-set、分topic/answerClass false-covered、UNKNOWN/OFF_TOPIC/INJECTION错误覆盖、38个human bands、24排序、4repair与8FOLLOW_UP结构、Java维度/总分/等级/追问veto、反馈保留/连续引用/事实矛盾、与baseline差值均NOT_EVALUATED；有效分母0，指标value=null。不填0%或虚构通过。原门槛P/R/F1≥0.90、exact≥0.75、ordering≥22/24、EXCELLENT4≥75、UNKNOWN4/OFF_TOPIC4/SEC2≤35、特殊错误覆盖0、repair4/4、结构8/8、hard contradiction0、baseline F1差不低于−0.05及exact差不低于−0.10保持冻结。

## 每case执行台账

| case | mode | status | Jev HTTP | DS feedback | DS baseline |
|---|---|---|---:|---:|---:|
| E02-EXCELLENT | MAIN_ANSWER | FAILED_HTTP_503_BEFORE_RESPONSE_VALIDATION | 1 | 0 | 0 |
| E02-PARTIAL | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| E02-WRONG | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| E02-UNKNOWN | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| E02-OFF_TOPIC | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| E02-CONTRADICTORY | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| E02-BOUNDARY | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| M04-EXCELLENT | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| M04-PARTIAL | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| M04-WRONG | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| M04-UNKNOWN | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| M04-OFF_TOPIC | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| M04-CONTRADICTORY | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| M04-BOUNDARY | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H05-EXCELLENT | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H05-PARTIAL | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H05-WRONG | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H05-UNKNOWN | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H05-OFF_TOPIC | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H05-CONTRADICTORY | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H05-BOUNDARY | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H09-EXCELLENT | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H09-PARTIAL | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H09-WRONG | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H09-UNKNOWN | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H09-OFF_TOPIC | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H09-CONTRADICTORY | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H09-BOUNDARY | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| SEC-E02 | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| SEC-H09 | MAIN_ANSWER | NOT_RUN | 0 | 0 | 0 |
| E02-REPAIR | FOLLOW_UP_ANSWER | NOT_RUN | 0 | 0 | 0 |
| E02-FAILED_REPAIR | FOLLOW_UP_ANSWER | NOT_RUN | 0 | 0 | 0 |
| M04-REPAIR | FOLLOW_UP_ANSWER | NOT_RUN | 0 | 0 | 0 |
| M04-FAILED_REPAIR | FOLLOW_UP_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H05-REPAIR | FOLLOW_UP_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H05-FAILED_REPAIR | FOLLOW_UP_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H09-REPAIR | FOLLOW_UP_ANSWER | NOT_RUN | 0 | 0 | 0 |
| H09-FAILED_REPAIR | FOLLOW_UP_ANSWER | NOT_RUN | 0 | 0 | 0 |

## 来源与严重度

E01历史：同冻结首例HTTP200返回jev-1.13.0，原严格数值校验拒绝；HTTP Jev1/DS0，原结果保留。E02：补丁独立审核+201项离线通过后，新epoch独立首例HTTP503，未进入answers/概率/precision validator。两轮不合并成语义样本。离线捕获响应重放通过只支持协议补丁，不改写E01，也不能替代E02在线证据。

P0：未观察到凭证泄露、数据或源码写入，完整性核验通过。P1：首例API可用性HTTP503阻断5B与5C、Phase6。P2：本冻结集是合成中文bench；尚无本轮语义证据或真人评委泛化结论。后续交Commander审查本轮停止证据；任何新epoch/重试或生产修复需新指令，本轮保持STOP。

## 数据与工作树完整性

13核心MySQL表全行内容hash和计数前后相同；Milvus52条记录所有5字段含完整1024向量、IDs和内容hash前后相同；411现有源码/测试/配置/Phase4/旧报告文件哈希相同。业务/RAG trace写入、DDL、向量写入0。benchmark SHA=fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a。原脏文件列表相同，未add/commit/push。本轮只创建7个E02证据/报告文件及target临时工具。
