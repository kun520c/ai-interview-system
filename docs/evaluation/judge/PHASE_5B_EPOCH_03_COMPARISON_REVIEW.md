# Phase 5B E03 comparison review — stopped at first feedback

**服务探针PASS，Jev原生校验PASS（1次）；执行/模型身份门禁FAIL，质量NOT_EVALUATED。** 同冻结首例E02-EXCELLENT在E03独立调用；没有重试，没有覆盖旧epoch。

## 真实事件与停止位置

1. GET https://api.typesafe.ai/v1/models：2026-10-08T06:24:05.743165800Z → 2026-10-08T06:24:06.630105700Z，HTTP200，886.8065ms；只列出jev-latest/jev-preview，无身份信息或error body，GET总1。该GET是非quality探针。
2. Jev POST /v1/systemone：2026-10-08T06:25:39.255165500Z → 2026-10-08T06:25:40.447897400Z，HTTP200，1192.9392ms；请求jev-latest，实际jev-1.13.0。真实生产JevClient和Provider.validateAnswers通过并进入DeepSeek反馈。Jev POST总1。
3. DeepSeek反馈 POST /chat/completions：2026-10-08T06:25:40.506106600Z → 2026-10-08T06:25:42.871163100Z，HTTP200，2365.5362ms；请求deepseek-v4-flash，实际deepseek-flash，finish_reason=stop。临时实验HTTP观察器的模型身份门禁抛出UNEXPECTED_DEEPSEEK_RETURNED_MODEL并立即STOP。DeepSeek反馈总1。

独立baseline0；其余37case0；fallback请求0/实际0；controlled Jev/DeepSeek0；Embedding/Milvus search本轮0。38成对有效样本0/38，Jev-primary完整返回0/38，独立baseline0/38。没有将反馈冒充baseline，也没有把Jev HTTP200或离线诊断当作完整primary。

## DeepSeek模型身份诊断

请求配置原样deepseek-v4-flash。HTTP200及stop已捕获，中止由临时实验模型身份检查产生；生产DeepSeekEvaluationClient只检查返回model非空，未添加或修改生产model匹配逻辑。

停止后只读查证：[DeepSeek官方首次调用说明](https://api-docs.deepseek.com/zh-cn/)明确旧deepseek-v4-flash对应模型已下线，仍接受旧名称的请求由DeepSeek-V4.1-Flash提供服务。其建议当前名称为deepseek-flash。结合本次实际model字段，可解释为供应商模型替换，无法声称实际评测了原V4 Flash。没有改请求model、配置或实验门槛，也未在查证后恢复E03。Commander需决定新的实际模型冻结和新epoch授权。

## Jev原生数值证据（诊断，不计质量样本）

| dimension | raw score0..4 | displayed E | delta | validation path | diagnostic Java0..20 |
|---|---:|---:|---:|---|---:|
| correctnessScore | 3.87 | 3.87 | 0.00 | ORIGINAL_TIGHT_CHECK | 19 |
| completenessScore | 3.96 | 3.96 | 0.00 | ORIGINAL_TIGHT_CHECK | 20 |
| depthScore | 3.82 | 3.82 | 0.00 | ORIGINAL_TIGHT_CHECK | 19 |
| clarityScore | 3.53 | 3.52 | 0.01 | VERSION_AND_GRID_UNIT_MASS_COMPATIBILITY | 18 |
| practiceScore | 3.8 | 3.79 | 0.01 | VERSION_AND_GRID_UNIT_MASS_COMPATIBILITY | 19 |

clarity/practice的展示差0.01均通过已有jev-1.13.0+cent-grid+unit-mass可行区间兼容，原score和probabilities不改。Native覆盖2480/2481/2482/2483，followUp=no。原生usage input7105/output262。完整JSON、概率、confidence、legend在RUN_EVIDENCE/JEV_RESULTS，来源是E03真实响应，不是E01重放或Mock。

停止后临时纯离线reflection重新调用生产buildRequest/validateAnswers/mapDecision/parseFeedback/mergeFeedback及Java validator/score/level/policy。捕获反馈JSON、逐评分点exact连续原文、score/coverage/followup保留均通过；诊断Java19+20+19+18+19=95、EXCELLENT、FINAL/NEXT_MAIN。反馈文本逐条支持候选原文，未观察到本首例的硬事实冲突。这仅证明捕获内容的结构与数值；模型身份STOP、primary未返回、baseline未运行的事实保持，不把诊断95或4/4覆盖计入38个质量样本。额外Provider HTTP0、业务写0。

DeepSeek捕获usage prompt3324/completion418/total3742、cachehit0。实际计费金额未知，不根据价格估计。各耗时是单次观测，含本地观察器取证开销，无法给成功38例latency分布。

## 冻结数据与原门槛

benchmark SHA=fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a；38=30MAIN+8FOLLOW_UP；24排序、4repair、原gold/bands/hardGates保持。源MySQL13表和Milvus52全向量hash/IDs与E02完全相同，8冻结query各完整top5的document/chunk IDs、score、content和profile保留；38个生产PromptBuilder输出与E02逐字相同，两路径本应共用，但baseline未调用。查询不含候选回答。

P/R/F1≥.90、exact-set≥.75，UNKNOWN/OFF_TOPIC/SECURITY false-covered0，24排序≥22，EXCELLENT4≥75，UNKNOWN4/OFF_TOPIC4/SEC2≤35，repair4/4及FOLLOW_UP8/8，无硬反馈冲突、baseline F1差不低于−.05/exact差不低于−.10原样。全部最终micro/macro/分topic-class/score bands/ordering/repair/quotes/preservation/Java veto/相对baseline指标NOT_EVALUATED、value=null、有效分母0。不能用单次原生结果或离线重放填充缺失37case和baseline。

## 逐case台账

| case | mode | status | Jev POST | DS feedback | DS baseline |
|---|---|---|---:|---:|---:|
| E02-EXCELLENT | MAIN_ANSWER | STOPPED_DEEPSEEK_MODEL_IDENTITY | 1 | 1 | 0 |
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

## 完整性和严重度

13核心表全部行数及内容hash相同，52完整向量5字段/IDs/hash相同，450个原有源码/配置/基线/旧epoch artifacts/锁/日志hash相同。业务/RAG/DDL/vector写入0。原Git状态相同，未add/commit/push，原dirty不覆盖。P0未观察到安全/持久化越界；P1实际DeepSeek模型替换不符合冻结原模型身份，阻断5B/5C；P2单例捕获和合成benchmark无法证明整体语义质量。Phase6 NOT_RUN。
