# Phase 5B E04 独立比较审阅

结论：EXECUTION FAIL / PROTOCOL FAIL / QUALITY NOT_EVALUATED / INTEGRITY PASS。完整配对1/38；Jev原生合格2/38，独立DeepSeek baseline合格1/38。不能把小样本成功外推为 Phase5 质量PASS。

最终停止：E02-PARTIAL 的独立 DeepSeekJudgeProvider baseline，HTTP200、requested=returned=deepseek-flash、finish_reason=stop，但message.content内部未转义双引号，原生产解析报“DeepSeek评价结果不是可解析的JSON对象”。离线用原生产 private parseSuggestion 重放捕获字符串复现同一异常，不发送任何HTTP；Python标准JSON解析也失败（line 10, column 19）。失败内容没有修复、补评分、当作有效样本或重试。

出错原始行：

```text
"对不确定的部分能坦诚说明"还没有形成清楚的判断"，没有强行编造。"
```

| 已通过Java与独立审阅的case | Provider | Java total | Level | Covered IDs | Java phase/action | Targets |
| --- | --- | --- | --- | --- | --- | --- |
| E02-EXCELLENT | Jev | 95 | EXCELLENT | [2480, 2481, 2482, 2483] | FINAL/NEXT_MAIN | [] |
| E02-PARTIAL | Jev | 53 | WEAK | [2480, 2481] | INITIAL/FOLLOW_UP | [2482, 2483] |
| E02-EXCELLENT | DS baseline | 93 | EXCELLENT | [2480, 2481, 2482, 2483] | FINAL/NEXT_MAIN | [] |

Jev已完成两例：TP6/FP0/FN0，micro P/R/F1=1.0，exact-set=2/2；DS baseline完成一例：TP4/FP0/FN0，P/R/F1=1.0，exact-set=1/1。以上只描述各自已完成子集，分母不相同。唯一同case配对E02-EXCELLENT：F1/exact差值0，Java95对93；完整38例相对门禁未评估。E02-PARTIAL的第二baseline格式不合格，没有可比Java分数，不能将原始文本里的数值计算为合格结果。

| 原冻结门禁 | 本次实际证据 | 完整门禁结论 |
| --- | --- | --- |
| Jev38/38原生 / DS38/38 / pairs38/38 | 2/38、1/38、1/38 | FAIL，协议停止 |
| micro P/R/F1各>=0.90、exact>=0.75 | 两例Jev/一例DS描述值见上 | NOT_EVALUATED |
| ordering>=22/24，ties fail | Jev可比较1/24：95>53；baseline可比较0/24 | NOT_EVALUATED |
| 4EXCELLENT>=75 | Jev1/4通过；DS1/4通过 | NOT_EVALUATED |
| UNKNOWN4/OFF_TOPIC4/SECURITY2<=35且无错误covered | 0/4、0/4、0/2执行 | NOT_EVALUATED |
| repair4/4且严格增加、coverage严格superset | 0/4执行 | NOT_EVALUATED |
| FOLLOW_UP8/8不能二次追问 | 0/8执行 | NOT_EVALUATED |
| 五维映射、Java分数/级别/决策 | Jev2例五维独立核算；DS1例独立核算 | PARTIAL_PASS，完整38未评估 |
| Jev反馈fixed decision preserved、连续候选原文引用100% | 2例、6个covered引文均通过；uncovered=null | PARTIAL_PASS |
| 严重反馈矛盾0 | Jev2例审阅0矛盾，非38例结论 | NOT_EVALUATED_FULL_38 |
| 相对DS F1下降不超过.05，exact不超过.10 | 唯一同case配对子集差值0 | NOT_EVALUATED |

JeV分数/概率的独立十项检查使用原0.001紧约束，必要时仅对原jev-1.13.0两位显示响应进行原补丁的单位概率和与闭区间可行性核算；没有更改原始score/p、容差或Java HALF_UP映射。完整数值证明、三个语义审阅记录在 RUN_EVIDENCE / target诊断文件。

临时检查器曾将 fast-track 原第24节（Jev反馈专用）的连续原文约束错误套用于独立 baseline，并在续跑预检因 IntNode/LongNode 类型差异误报数据变动。两次均保留原 runner、日志、证据的原字节副本；后者在任何剩余样本POST之前发生。修正仅限 target 临时实验检查器，不修改任何生产 Java、Prompt、gold、参数或原门禁。首例既有三次新鲜 E04 响应按原 provider-specific 合同重新独立复核后合格，没有重发；独立 baseline 的解释型 evidence 仍逐点审查候选来源和硬矛盾。最终STOP是第二baseline被原生产 parseSuggestion 拒绝，与这两项临时错误不同。

| 请求case | Provider / purpose | Requested | Returned | HTTP | Started UTC | Latency |
| --- | --- | --- | --- | --- | --- | --- |
| E02-EXCELLENT | JEV / FEEDBACK | jev-latest | jev-1.13.0 | 200 | 2026-10-08T07:33:15.374373Z | 1197.927 ms |
| E02-EXCELLENT | DEEPSEEK / FEEDBACK | deepseek-flash | deepseek-flash | 200 | 2026-10-08T07:33:16.611899800Z | 2133.880 ms |
| E02-EXCELLENT | DEEPSEEK / BASELINE | deepseek-flash | deepseek-flash | 200 | 2026-10-08T07:34:44.279419400Z | 3368.264 ms |
| E02-PARTIAL | JEV / FEEDBACK | jev-latest | jev-1.13.0 | 200 | 2026-10-08T07:41:44.283026Z | 1217.864 ms |
| E02-PARTIAL | DEEPSEEK / FEEDBACK | deepseek-flash | deepseek-flash | 200 | 2026-10-08T07:41:45.547626700Z | 2598.484 ms |
| E02-PARTIAL | DEEPSEEK / BASELINE | deepseek-flash | deepseek-flash | 200 | 2026-10-08T07:42:25.669973800Z | 3865.121 ms |

真实调用总计：DeepSeek /models GET=1；JeV POST=2；DS feedback POST=2；DS baseline POST=2；真实fallback=0；本地受控故障=0；Embedding HTTP=0；Milvus search=0。应用层每次请求仅一次，失败样本重试0；未把先前epoch输出当作E04数据。

| 请求 | 返回的真实usage（原字段） |
| --- | --- |
| E02-EXCELLENT / JEV / FEEDBACK | `{"input_tokens":7105,"output_tokens":261}` |
| E02-EXCELLENT / DEEPSEEK / FEEDBACK | `{"prompt_tokens":3324,"completion_tokens":391,"total_tokens":3715,"prompt_tokens_details":{"cached_tokens":3072},"prompt_cache_hit_tokens":3072,"prompt_cache_miss_tokens":252}` |
| E02-EXCELLENT / DEEPSEEK / BASELINE | `{"prompt_tokens":3507,"completion_tokens":638,"total_tokens":4145,"prompt_tokens_details":{"cached_tokens":384},"prompt_cache_hit_tokens":384,"prompt_cache_miss_tokens":3123}` |
| E02-PARTIAL / JEV / FEEDBACK | `{"input_tokens":6977,"output_tokens":268}` |
| E02-PARTIAL / DEEPSEEK / FEEDBACK | `{"prompt_tokens":3239,"completion_tokens":464,"total_tokens":3703,"prompt_tokens_details":{"cached_tokens":384},"prompt_cache_hit_tokens":384,"prompt_cache_miss_tokens":2855}` |
| E02-PARTIAL / DEEPSEEK / BASELINE | `{"prompt_tokens":3422,"completion_tokens":812,"total_tokens":4234,"prompt_tokens_details":{"cached_tokens":768},"prompt_cache_hit_tokens":768,"prompt_cache_miss_tokens":2654}` |

响应未提供实际扣费金额：cost NOT_AVAILABLE；不按价格表推算实际支出。调用预算原上限未变、未用满。

P0：E04-DS-JSON-001（真实JSON格式失败）。P1：E04-INCOMPLETE-001（全套门禁证据缺失）。P2：E04-HARNESS-001（已明确记录并修正的临时检查器错误，未重发样本）。Phase5C NOT_RUN，Phase6 BLOCKED。下一步由Commander审阅该协议事故，再决定新epoch或明确修复授权；本轮不擅自增加JSON修复/重试、改Prompt/模型/协议或生产代码。
