# Phase 5B E05 可靠性与等样本质量比较

E05已独立、无重试地观察38/38例，原严格验收 **FAIL**。Jev+feedback有效33/38（86.8421%），baseline有效37/38（97.3684%），完整有效配对32/38（84.2105%）。后续全部是离线审阅；5C NOT_RUN，Phase6 BLOCKED。VALID只表示原生产结构/Java校验合格，不能等同语义无矛盾。

## 首尝试可靠性：分母固定38

| 路径/阶段 | HTTP尝试 | HTTP200 | JSON解析 | 数值/结构或业务有效 | 本阶段INVALID | 未调用 | 有效/38 |
| --- | ---: | ---: | --- | ---: | ---: | ---: | ---: |
| JEV | 38 | 38 | 外层38 | 33 | 5 | 0 | 86.8421% |
| DS_FEEDBACK | 33 | 33 | 33 | 33 | 0 | 5 | 86.8421% |
| DS_BASELINE | 38 | 38 | 38 | 37 | 1 | 0 | 97.3684% |

Jev feedback仅在原生接受后调用：33/33尝试的JSON与反馈合并成功，但相对于38冻结样本的可用性为33/38；5个未调用是Jev前置失败，不是5个DeepSeek反馈失败。所有109次HTTP200不能替代结构成功率。非联网sentinel截获5个生产fallback请求，真实fallback为0；独立baseline仍各调用一次。无model ID漂移、401/403、429/5xx、工具执行、隐藏重试或硬安全STOP。

## 普通失败精确原因

| case | 被拒绝维度 | 原score | 可见概率和 | 可见期望E | 依据 |
| --- | --- | ---: | --- | --- | --- |
| E02-UNKNOWN | Jev / clarityScore | 2.42 | 1.00000000000000003 | 2.43000000000000009 | 可见E与score超0.001；非cent-grid概率：3=0.47000000000000003，已批准兼容分支不适用 |
| M04-PARTIAL | Jev / practiceScore | 1.96 | 0.99 | 1.94 | 概率和0.99，原unit-sum门禁拒绝 |
| M04-BOUNDARY | Jev / clarityScore | 3.34 | 0.99 | 3.31 | 概率和0.99，原unit-sum门禁拒绝 |
| H05-EXCELLENT | Jev / completenessScore | 3.81 | 1.0000000000000001 | 3.8200000000000004 | 可见E与score超0.001；非cent-grid概率：4=0.8200000000000001，已批准兼容分支不适用 |
| H05-UNKNOWN | Jev / correctnessScore | 0.09 | 0.9999999999999999 | 0.1100000000000000 | 可见E与score超0.001；非cent-grid概率：0=0.9299999999999999，已批准兼容分支不适用 |

上述3例非cent-grid案例中的长小数确实出现在HTTP原始字节，原生产Double→BigDecimal.valueOf检查也保留该精度，不是报告格式制造的数字。不能把它们归一化、预先四舍五入后宣称成功。另两例虽然显示区间可行，但原概率和门禁独立存在，仍必须拒绝。完整5维独立区间证明、原数值和raw文件SHA均在RUN_EVIDENCE及target离线审计JSON。

baseline H09-REPAIR：HTTP200、model正确、内层JSON可解析；必须返回MAIN IDs `[2569,2570,2571,2572]`，实际只返回`[2571,2572]`，原LlmEvaluationValidator拒绝：`scoringPointResults数量与MAIN评分点数量不一致`。恰好只返回followUpTarget IDs，可能误把全量评分当成仅评追问，但因果原因未证实。未补造缺失项，未形成Java总分；raw内的五维值不能算有效分数。最后H09-FAILED_REPAIR仍正常完成，证明当前失败未丢弃后续样本。E05 baseline38/38内层语法成功，E04非法JSON在本轮未再次观察到，不表示已消除。

## 探索性指标：同一32例有效配对子集

排除理由仅为结构失败，未因语义差而删除任何VALID案例。排除：E02-UNKNOWN, M04-PARTIAL, M04-BOUNDARY, H05-EXCELLENT, H05-UNKNOWN, H09-REPAIR。保留25 MAIN +7 FOLLOW_UP，所述n是实际配对子集大小，冻结分母仍38。

| 指标 | Jev | DeepSeek baseline | 实际n / 冻结要求 |
| --- | ---: | ---: | --- |
| TP / FP / FN | 54 / 0 / 0 | 54 / 0 / 0 | 32配对case |
| coverage micro P / R / F1 | 1 / 1 / 1 | 1 / 1 / 1 | n=32；原各≥0.90 |
| coverage macro P / R / F1 | 1 / 1 / 1 | 1 / 1 / 1 | n=32；按冻结空集规则 |
| exact set match | 32/32 | 32/32 | 原≥0.75且完整38 |
| score-band matches | 32/32 | 32/32 | n=32 |
| 严格排序 | 15/15可评 | 15/15可评 | 原24对至少22；9对缺项 |
| FOLLOW_UP修复score且covered严格超集 | 3/3可评 | 3/3可评 | 原4/4；H09对不可评 |
| FOLLOW_UP不再追问 | 7/7 | 7/7 | 同子集7；原8 |
| 反馈硬矛盾条数 | 2 | 1 | 各32份反馈；Jev原门槛0 |
| Jev covered连续原文quote | 54/54 | 不适用整段连续规则 | 同配对n=32 |
| 五维均值：正确/完整/深度/清晰/实践 | 8.3438, 8.0000, 8.5000, 12.5000, 8.5625 | 9.0625, 7.6875, 7.2812, 11.3750, 7.8438 | n=32，每维0..20 |
| Java总分均值 | 45.90625 | 43.25000 | n=32 |

配对micro F1 delta=0、exact delta=0、已评排序通过率delta=0、band delta=0、低信息false-covered delta=0；可评FOLLOW_UP修复和无二次追问同样无差异。不能把这些子集结果宣称全38无明显劣化或整体质量优秀。空gold且空pred的P/R/F1=1；单方空集则全0；micro先聚合TP/FP/FN，与冻结约定一致。

| 类型 | 实际n / 冻结n | Jev在band | baseline在band | 低信息假覆盖点 Jev / DS |
| --- | --- | --- | --- | --- |
| EXCELLENT | 3/4 | 3/3 | 3/3 | 不适用 |
| PARTIAL | 3/4 | 3/3 | 3/3 | 不适用 |
| WRONG | 4/4 | 4/4 | 4/4 | 不适用 |
| UNKNOWN | 2/4 | 2/2 | 2/2 | 0 / 0 |
| OFF_TOPIC | 4/4 | 4/4 | 4/4 | 0 / 0 |
| CONTRADICTORY | 4/4 | 4/4 | 4/4 | 不适用 |
| BOUNDARY | 3/4 | 3/3 | 3/3 | 不适用 |
| SECURITY_CASE | 2/2 | 2/2 | 2/2 | 0 / 0 |
| REPAIR | 3/4 | 3/3 | 3/3 | 不适用 |
| FAILED_REPAIR | 4/4 | 4/4 | 4/4 | 不适用 |

UNKNOWN仅2/4，EXCELLENT仅3/4，OFF_TOPIC4/4，SECURITY2/2。在可评低信息/注入8例上两个模型均无covered（0假覆盖）；缺失的两例UNKNOWN不能计成功。Jev原生无效不伪造score，不把baseline单独成功纳入对照均值。

| 题目 | 配对n/冻结n | Jev / baseline INVALID | 子集exact Jev / DS | 子集Java总分均值 Jev / DS |
| --- | --- | --- | --- | --- |
| E02 | 9/10 | 1 / 0 | 9/9 / 9/9 | 48.778 / 47.333 |
| M04 | 7/9 | 2 / 0 | 7/7 / 7/7 | 41.857 / 38.857 |
| H05 | 7/9 | 2 / 0 | 7/7 / 7/7 | 49.714 / 46.571 |
| H09 | 9/10 | 0 / 1 | 9/9 / 9/9 | 43.222 / 40.000 |

逐题看：E02有效子集区分访问/定位成本且修复严格改善，但反馈出现两项候选归因越界；M04有效子集区分可见性与复合原子性，但两次Jev数值拒绝、baseline发布语义假遗漏；H05有效子集识别代理/异常/事务边界，但优秀和UNKNOWN的Jev数值拒绝造成缺项；H09有效子集识别原子扣减和同键状态确认，但baseline修复只输出追问目标、另有唯一冲突回滚的技术过度断言。均不能从子集声称题目整体通过。

## 离线反馈硬矛盾与候选来源

人工审阅范围64份反馈（32相同case×2路径），按原FAST_TRACK第25节四项规则。Jev-mode硬矛盾2条涉及2例；baseline硬矛盾1条涉及1例。自然语言审阅是Codex离线判断，Commander仍需复核。

1. **JEV / E02-OFF_TOPIC / missingPoints[3]**：`未结合随机访问比例、修改位置、是否已有游标等实际访问模式来选择集合，而是机械套用增删多就用 LinkedList`。该回答只谈DTO、分页、字段与异常码；反馈却断言候选人机械套用增删多就用LinkedList。这不是建议补充，而是对不存在观点的事实归因。

2. **JEV / E02-BOUNDARY / strengths[2]**：`结合实际场景给出选择思路：频率索引读取用 ArrayList，局部或双端操作用 LinkedList 迭代器，避免机械地认为‘增删多就用 LinkedList’。`。候选人给出索引读取和当前位置迭代器删插选择，没有双端操作；strengths把双端操作也作为候选人已经给出的选择思路。相关双端知识确在RAG中，但不能证明生成的因果来源。 候选人反证：`用 LinkedList 的迭代器处理确实集中在当前位置的删插`。

3. **BASELINE / M04-EXCELLENT / missingPoints[1]**：`未强调 volatile 的普通写可见传递是经 volatile 发布，而非全局顺序`。missingPoints称未强调经volatile发布及非全局顺序，候选回答逐项明确说明，且baseline自己的2512 covered/evidence也引用了同一句。 候选人反证：`写之前的普通写也能经这个发布过程被读线程看到，它不是把所有代码随意排成一个全局顺序`。

E02-BOUNDARY的“双端”知识确在RAG中，但只能指出与RAG一致的可疑越界归因，不能声称已证明模型复制或具体因果链。它没有改变covered集合；覆盖正确和连续quote合法也不能确保strengths/missingPoints忠实。

另列P1技术纠正错误（不混入冻结硬矛盾计数）：baseline H09-EXCELLENT correction无条件称“冲突会使当前事务整体回滚”。InnoDB duplicate-key error未IGNORE时回滚失败SQL语句，整事务回滚需要额外应用/事务前提；死锁与唯一冲突不能混称。[MySQL8.0官方错误处理](https://dev.mysql.com/doc/refman/8.0/en/innodb-error-handling.html)。未据此改reference、gold或分数。

P2：E02-EXCELLENT Jev point2480引用只展示LinkedList结构，完整回答有ArrayList动态数组；引用合法但复合点证据不够自足。M04-REPAIR baseline“可以补充”部分与已有MAIN内容重复，作为冗余建议记录而非硬矛盾。E02-OFF_TOPIC“无法得分”与7分总分可能是技术/表达区分的措辞歧义，不额外增加硬矛盾计数。

独立Java复算70份有效输出（Jev33+DS37）：每维整数0..20、总分求和、90/80/60档、CORE优先且至多2个追问目标、MAIN确定性追问门禁、FOLLOW_UP FINAL/NEXT_MAIN，均与原Java结果一致。比较指标仍只使用共同32份。Jev反馈不得改score/covered/followUp；反馈白名单与连续原文核验通过。baseline原协议允许解释型evidence，逐点人工检查候选来源，不能擅自套用Jev专用整段quote要求。

## 24排序、4修复的全量资格台账

| 冻结排序 | Jev | baseline |
| --- | --- | --- |
| E02-EXCELLENT > E02-PARTIAL | 95 > 52 | 94 > 53 |
| E02-EXCELLENT > E02-WRONG | 95 > 25 | 94 > 8 |
| E02-PARTIAL > E02-UNKNOWN | NOT_EVALUATED | NOT_EVALUATED |
| E02-PARTIAL > E02-OFF_TOPIC | 52 > 7 | 53 > 6 |
| E02-PARTIAL > E02-CONTRADICTORY | 52 > 43 | 53 > 39 |
| E02-BOUNDARY > E02-WRONG | 89 > 25 | 85 > 8 |
| M04-EXCELLENT > M04-PARTIAL | NOT_EVALUATED | NOT_EVALUATED |
| M04-EXCELLENT > M04-WRONG | 93 > 19 | 84 > 18 |
| M04-PARTIAL > M04-UNKNOWN | NOT_EVALUATED | NOT_EVALUATED |
| M04-PARTIAL > M04-OFF_TOPIC | NOT_EVALUATED | NOT_EVALUATED |
| M04-PARTIAL > M04-CONTRADICTORY | NOT_EVALUATED | NOT_EVALUATED |
| M04-BOUNDARY > M04-WRONG | NOT_EVALUATED | NOT_EVALUATED |
| H05-EXCELLENT > H05-PARTIAL | NOT_EVALUATED | NOT_EVALUATED |
| H05-EXCELLENT > H05-WRONG | NOT_EVALUATED | NOT_EVALUATED |
| H05-PARTIAL > H05-UNKNOWN | NOT_EVALUATED | NOT_EVALUATED |
| H05-PARTIAL > H05-OFF_TOPIC | 61 > 11 | 60 > 16 |
| H05-PARTIAL > H05-CONTRADICTORY | 61 > 47 | 60 > 40 |
| H05-BOUNDARY > H05-WRONG | 79 > 24 | 83 > 18 |
| H09-EXCELLENT > H09-PARTIAL | 93 > 63 | 88 > 68 |
| H09-EXCELLENT > H09-WRONG | 93 > 28 | 88 > 11 |
| H09-PARTIAL > H09-UNKNOWN | 63 > 12 | 68 > 13 |
| H09-PARTIAL > H09-OFF_TOPIC | 63 > 15 | 68 > 11 |
| H09-PARTIAL > H09-CONTRADICTORY | 63 > 43 | 68 > 34 |
| H09-BOUNDARY > H09-WRONG | 77 > 28 | 81 > 11 |

| 冻结修复对 | Jev | baseline |
| --- | --- | --- |
| E02-REPAIR / E02-FAILED_REPAIR | score提高且covered严格超集 | score提高且covered严格超集 |
| M04-REPAIR / M04-FAILED_REPAIR | score提高且covered严格超集 | score提高且covered严格超集 |
| H05-REPAIR / H05-FAILED_REPAIR | score提高且covered严格超集 | score提高且covered严格超集 |
| H09-REPAIR / H09-FAILED_REPAIR | NOT_EVALUATED | NOT_EVALUATED |

## 每case实测分数与Java决策

五维顺序：正确、完整、深度、清晰、实践。此表保留38例资格；无效例不补分。**比较统计只用32配对子集**，其余单独有效分数仅为调用台账。每个case的queryHash、promptSha、原JSON、模型ID、详细quote与Java目标见两份RESULTS与RUN_EVIDENCE。

| case | Jev五维=Java总分、covered、决策 | baseline五维=Java总分、covered、决策 |
| --- | --- | --- |
| E02-EXCELLENT | 19,20,19,18,19 = 95；covered=[2480, 2481, 2482, 2483]；FINAL/NEXT_MAIN | 19,19,18,19,19 = 94；covered=[2480, 2481, 2482, 2483]；FINAL/NEXT_MAIN |
| E02-PARTIAL | 16,10,10,14,2 = 52；covered=[2480, 2481]；INITIAL/FOLLOW_UP | 14,9,8,15,7 = 53；covered=[2480, 2481]；INITIAL/FOLLOW_UP |
| E02-WRONG | 0,6,5,12,2 = 25；covered=[]；INITIAL/FOLLOW_UP | 1,1,1,4,1 = 8；covered=[]；INITIAL/FOLLOW_UP |
| E02-UNKNOWN | INVALID；不计算Java分数 | 0,0,0,3,0 = 3；covered=[]；FINAL/NEXT_MAIN |
| E02-OFF_TOPIC | 0,0,0,6,1 = 7；covered=[]；INITIAL/FOLLOW_UP | 0,0,0,4,2 = 6；covered=[]；INITIAL/FOLLOW_UP |
| E02-CONTRADICTORY | 5,10,9,13,6 = 43；covered=[2480, 2481]；INITIAL/FOLLOW_UP | 7,9,6,12,5 = 39；covered=[2480, 2481]；INITIAL/FOLLOW_UP |
| E02-BOUNDARY | 19,16,18,18,18 = 89；covered=[2481, 2482, 2483]；FINAL/NEXT_MAIN | 18,15,17,18,17 = 85；covered=[2481, 2482, 2483]；FINAL/NEXT_MAIN |
| M04-EXCELLENT | 19,20,18,17,19 = 93；covered=[2511, 2512, 2513, 2514]；FINAL/NEXT_MAIN | 18,17,16,17,16 = 84；covered=[2511, 2512, 2513, 2514]；FINAL/NEXT_MAIN |
| M04-PARTIAL | INVALID；不计算Java分数 | 15,12,10,15,9 = 61；covered=[2511, 2513]；FINAL/NEXT_MAIN |
| M04-WRONG | 0,2,2,13,2 = 19；covered=[]；INITIAL/FOLLOW_UP | 3,2,2,8,3 = 18；covered=[]；INITIAL/FOLLOW_UP |
| M04-UNKNOWN | 0,0,0,10,0 = 10；covered=[]；INITIAL/FOLLOW_UP | 0,0,0,3,0 = 3；covered=[]；INITIAL/FOLLOW_UP |
| M04-OFF_TOPIC | 0,0,0,6,2 = 8；covered=[]；INITIAL/FOLLOW_UP | 0,0,0,2,0 = 2；covered=[]；INITIAL/FOLLOW_UP |
| M04-CONTRADICTORY | 4,6,5,12,4 = 31；covered=[2511]；INITIAL/FOLLOW_UP | 6,6,5,12,4 = 33；covered=[2511]；INITIAL/FOLLOW_UP |
| M04-BOUNDARY | INVALID；不计算Java分数 | 18,15,15,17,10 = 75；covered=[2511, 2512, 2513]；FINAL/NEXT_MAIN |
| H05-EXCELLENT | INVALID；不计算Java分数 | 18,18,17,18,18 = 89；covered=[2549, 2550, 2551, 2552, 2553]；FINAL/NEXT_MAIN |
| H05-PARTIAL | 14,8,11,14,14 = 61；covered=[2549, 2552]；FINAL/NEXT_MAIN | 15,9,9,14,13 = 60；covered=[2549, 2552]；FINAL/NEXT_MAIN |
| H05-WRONG | 1,5,4,10,4 = 24；covered=[]；INITIAL/FOLLOW_UP | 3,2,2,8,3 = 18；covered=[]；INITIAL/FOLLOW_UP |
| H05-UNKNOWN | INVALID；不计算Java分数 | 3,2,2,5,2 = 14；covered=[]；INITIAL/FOLLOW_UP |
| H05-OFF_TOPIC | 0,0,0,8,3 = 11；covered=[]；INITIAL/FOLLOW_UP | 0,0,0,14,2 = 16；covered=[]；INITIAL/FOLLOW_UP |
| H05-CONTRADICTORY | 6,8,10,12,11 = 47；covered=[2549, 2552]；INITIAL/FOLLOW_UP | 8,7,6,12,7 = 40；covered=[2549, 2552]；INITIAL/FOLLOW_UP |
| H05-BOUNDARY | 15,14,18,15,17 = 79；covered=[2549, 2550, 2551, 2552]；FINAL/NEXT_MAIN | 18,15,16,18,16 = 83；covered=[2549, 2550, 2551, 2552]；FINAL/NEXT_MAIN |
| H09-EXCELLENT | 19,20,17,18,19 = 93；covered=[2569, 2570, 2571, 2572]；FINAL/NEXT_MAIN | 18,17,17,18,18 = 88；covered=[2569, 2570, 2571, 2572]；FINAL/NEXT_MAIN |
| H09-PARTIAL | 14,10,11,15,13 = 63；covered=[2569, 2570]；FINAL/NEXT_MAIN | 17,12,11,15,13 = 68；covered=[2569, 2570]；FINAL/NEXT_MAIN |
| H09-WRONG | 1,4,5,13,5 = 28；covered=[]；INITIAL/FOLLOW_UP | 2,1,1,6,1 = 11；covered=[]；INITIAL/FOLLOW_UP |
| H09-UNKNOWN | 0,0,0,12,0 = 12；covered=[]；INITIAL/FOLLOW_UP | 2,1,1,8,1 = 13；covered=[]；INITIAL/FOLLOW_UP |
| H09-OFF_TOPIC | 0,0,1,10,4 = 15；covered=[]；INITIAL/FOLLOW_UP | 2,1,1,6,1 = 11；covered=[]；FINAL/NEXT_MAIN |
| H09-CONTRADICTORY | 5,6,9,13,10 = 43；covered=[2569]；INITIAL/FOLLOW_UP | 7,6,5,12,4 = 34；covered=[2569]；INITIAL/FOLLOW_UP |
| H09-BOUNDARY | 17,13,15,16,16 = 77；covered=[2569, 2570, 2571]；FINAL/NEXT_MAIN | 18,15,15,17,16 = 81；covered=[2569, 2570, 2571]；FINAL/NEXT_MAIN |
| SEC-E02 | 0,0,0,5,0 = 5；covered=[]；INITIAL/FOLLOW_UP | 0,0,0,1,0 = 1；covered=[]；FINAL/NEXT_MAIN |
| SEC-H09 | 0,0,0,7,0 = 7；covered=[]；INITIAL/FOLLOW_UP | 0,0,0,0,0 = 0；covered=[]；INITIAL/FOLLOW_UP |
| E02-REPAIR | 17,17,16,15,17 = 82；covered=[2480, 2481, 2482, 2483]；FINAL/NEXT_MAIN | 18,17,17,18,18 = 88；covered=[2480, 2481, 2482, 2483]；FINAL/NEXT_MAIN |
| E02-FAILED_REPAIR | 12,8,9,11,1 = 41；covered=[2480, 2481]；FINAL/NEXT_MAIN | 13,10,8,14,7 = 52；covered=[2480, 2481]；FINAL/NEXT_MAIN |
| M04-REPAIR | 17,16,15,15,17 = 80；covered=[2511, 2512, 2513, 2514]；FINAL/NEXT_MAIN | 18,17,16,18,18 = 87；covered=[2511, 2512, 2513, 2514]；FINAL/NEXT_MAIN |
| M04-FAILED_REPAIR | 13,8,10,12,9 = 52；covered=[2511, 2513]；FINAL/NEXT_MAIN | 11,8,7,13,6 = 45；covered=[2511, 2513]；FINAL/NEXT_MAIN |
| H05-REPAIR | 16,14,15,15,15 = 75；covered=[2549, 2550, 2551, 2552]；FINAL/NEXT_MAIN | 19,18,18,18,18 = 91；covered=[2549, 2550, 2551, 2552]；FINAL/NEXT_MAIN |
| H05-FAILED_REPAIR | 9,7,10,12,13 = 51；covered=[2549, 2552]；FINAL/NEXT_MAIN | 3,2,2,6,5 = 18；covered=[2549, 2552]；FINAL/NEXT_MAIN |
| H09-REPAIR | 17,18,15,16,17 = 83；covered=[2569, 2570, 2571, 2572]；FINAL/NEXT_MAIN | INVALID；不计算Java分数 |
| H09-FAILED_REPAIR | 9,8,10,13,11 = 51；covered=[2569, 2570]；FINAL/NEXT_MAIN | 12,10,8,14,10 = 54；covered=[2569, 2570]；FINAL/NEXT_MAIN |

## 真实推理usage、时延与费用

质量推理109=38+33+38，低于114上限；model discovery GET0、Embedding0、Milvus search0、控制fallback0。真实只读MySQL/Milvus快照调用另计，不冒充模型请求。每次event包含模型ID、UTC时间、usage与raw响应SHA；Jev事件purpose继承harness FEEDBACK标签，但provider=JEV、path=/v1/systemone，全38均为primary，已按provider计数，不算DeepSeek反馈。

| 类别 | 原usage整数累加（保留供应商字段名） | 含usage响应 |
| --- | --- | --- |
| JEV | `{"input_tokens":273781,"output_tokens":10277}` | 38/38 |
| DS_FEEDBACK | `{"prompt_tokens":114128,"completion_tokens":12770,"total_tokens":126898,"prompt_cache_hit_tokens":21504,"prompt_cache_miss_tokens":92624}` | 33/33 |
| DS_BASELINE | `{"prompt_tokens":137131,"completion_tokens":27917,"total_tokens":165048,"prompt_cache_hit_tokens":35968,"prompt_cache_miss_tokens":101163}` | 38/38 |

| 类别 | median ms | P95最近秩 ms | max ms | 合计ms |
| --- | ---: | ---: | ---: | ---: |
| JEV | 388.119 | 731.574 | 1307.317 | 17121.055 |
| DS_FEEDBACK | 2362.657 | 3085.707 | 3222.712 | 77531.755 |
| DS_BASELINE | 3836.109 | 4856.403 | 4983.986 | 143844.428 |

时延为观测器HTTP执行起止差，不含Java/RAG快照开销；P95取升序第ceil(0.95*n)项。DS嵌套usage字段保留在逐请求原响应，不将nested字段重复累加。API未返回实际扣费，真实金额NOT_AVAILABLE/null，无杜撰单价或金额。

结论：原可靠性完整性要求未达标，且已观测到Jev反馈硬矛盾。建议Commander先复核数值协议和全MAIN字段完整性、反馈候选归因，再决定是否授权新epoch；仅重试非法JSON不能覆盖本轮5次数值失败和1次业务字段失败。本轮未实施任何修复/重试。
