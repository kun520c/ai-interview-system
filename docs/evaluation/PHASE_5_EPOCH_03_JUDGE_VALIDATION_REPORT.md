# Phase 5 E03 Judge validation report

Date: 2026-10-08 (Asia/Shanghai). **E03首例模型身份门禁STOP：Jev已恢复一次真实POST200并通过原生校验，DeepSeek旧名称实际由替换模型应答。** 本轮没有重试/换模型/改配置，不恢复同epoch，不推进Phase6。

## Gate结果

| gate | outcome | evidence |
|---|---|---|
| Gate0 freeze/source/runtime | PASS | SHA及450原文件一致；JDK21与真实SpringBinder/隔离初始化器、只读数据和RAG复用通过 |
| Gate1 GET model list | PASS | 唯一GET200，jev-latest可用；非quality样本 |
| Jev原生响应协议 | PASS（1次） | POST200、jev-1.13.0、score/概率/type/keys/confidence校验通过，进入反馈 |
| Gate2 canary / EXECUTION | FAIL | DS feedback实际model deepseek-flash≠冻结请求deepseek-v4-flash，临时身份观察器立即STOP |
| PROTOCOL / model identity | FAIL / BLOCKED | HTTP200/stop已捕获；原模型身份不可宣称，没有生产代码错误归因 |
| QUALITY | NOT_EVALUATED | 完整Jev-primary0/38、baseline0/38、pairs0/38 |
| PERSISTENCE / integrity | PASS | 13表、52完整向量、450原文件及E01/E02锁/日志hash一致 |
| Gate3 remaining37 | NOT_RUN | 首例未完整成功 |
| Gate4 Phase5C | NOT_RUN | controlled fault/fallback0 |
| Phase6 | NOT_RUN / BLOCKED | 未解锁 |

## 实际调用与模型

| authenticated API call | count | status | request → actual model |
|---|---:|---|---|
| TypeSafe GET /v1/models | 1 | 200 | alias jev-latest在列表 |
| Jev质量POST /v1/systemone | 1 | 200 | jev-latest → jev-1.13.0 |
| DeepSeek反馈POST | 1 | 200 / stop | deepseek-v4-flash → deepseek-flash |
| DeepSeek独立baseline | 0 | NOT_RUN | 未调用 |
| fallback / controlled Jev / controlled DS | 0 / 0 / 0 | NOT_RUN | 未调用 |
| Embedding / Milvus search新调用 | 0 / 0 | 复用E02证据 | 前后全部hash一致 |

GET UTC 2026-10-08T06:24:05.743165800Z → 2026-10-08T06:24:06.630105700Z，886.8065ms；Jev UTC 2026-10-08T06:25:39.255165500Z → 2026-10-08T06:25:40.447897400Z，1192.9392ms；DS UTC 2026-10-08T06:25:40.506106600Z → 2026-10-08T06:25:42.871163100Z，2365.5362ms。没有再次503，单次真实POST成功仅证明此时/此请求恢复，不证明后续38服务可用或Judge质量。

Jev token usage7105 input/262 output；DS feedback3324 prompt/418 completion/3742 total、cachehit0；实际cost未获取，不估计。GET模型列表只保留别名，无身份信息/Authorization/raw key；response headers未保存，candidate数据为预冻结合成输入。

## 模型漂移的官方核验与停止规则

停止由临时实验身份观察器触发，未修改生产DeepSeek客户端。生产客户端原逻辑仅要求model非空；HTTP200并不证明实际型号仍为冻结原V4 Flash。

[DeepSeek官方说明](https://api-docs.deepseek.com/zh-cn/)现已说明：旧deepseek-v4-flash模型下线，但旧名称仍接受，实际请求由DeepSeek-V4.1-Flash服务。该公开文档在STOP后只读查证，与本次实际返回deepseek-flash相符。没有将V4.1替换视作原V4质量验收，不自行改名、放宽身份门禁或继续请求。需Commander决定实际模型冻结及新的epoch范围。

## 冻结、RAG与离线诊断

Runtime profile=local、schema ai_interview；MySQL21题/97评分点/21docs/52chunks；Milvus default/knowledge_chunk_vectors_local共52、1024维；topK5，qwen3.7-text-embedding-1024-dense-v1。实际Spring judge=JEV、DeepSeek enabled=true；host api.typesafe.ai/jev-latest、connect3s/read20s/minConfidence0；DS api.deepseek.com/deepseek-v4-flash、connect3s/read60s/maxTokens4096。所需keys/DB均SET，只有presence检查，不保存/独立hash凭证；只导入子进程，无Windows永久env修改。EnvironmentIsolationInitializer注册并执行，未绕过。

源DB13表及52条含全部1024值的Milvus快照hash与E02一致，8个生产query hash的完整top5 IDs、score、content原样；38Context/Prompt/答案/参考/gold原样，E03全列于RUN_EVIDENCE。RAG query不含候选回答。本轮复用成功，Embedding/search次数0；没有伪称新增检索。

冻结SHA256 fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a。38=30MAIN+8FOLLOW_UP，原24排序/4repair/bands/hardGates保持。所有最终quality指标NOT_EVALUATED/null，分母0。Jev原生covered2480/2481/2482/2483及五维真实score已记录，clarity/practice展示差0.01由已有受限数值兼容验收。

停止后额外Provider调用0的纯离线捕获诊断通过：真实生产数值validator、反馈JSON/逐点评分点原文quote、合并保留、Java validation；诊断映射19/20/19/18/19、sum95/EXCELLENT/FINAL-NEXT_MAIN。该结果仅作结构诊断，反馈原实际model身份STOP仍保持；不是完整在线primary、质量PASS或baseline，不填补38样本分母。首例文本人工核对没有发现硬事实矛盾，未执行38例语义验收。

E03未重跑JUnit；离线补丁与E02六套历史证据201项、Failures0 Errors0 Skipped0（专项63）保持，明确不作为本轮新JUnit或在线质量证据。临时E03 runner javac通过；捕获重放helper首编因Double setter使用int 0失败，仅临时helper改0D后编译/离线重放通过，无生产修改、额外HTTP0。

## 交付、完整性和下一步

新增7文件：SERVICE_PROBE.json记录唯一GET；RUN_EVIDENCE.json记录所有真实事件/450hash/13表/52向量/38输入及原门槛；JEV_RESULTS.json保存一条真实尝试和中止后诊断、accepted cases空；DEEPSEEK_BASELINE_RESULTS.json明确NOT_RUN；COMPARISON_REVIEW.md保留38case台账及原指标分母；PHASE_5C_EPOCH_03_FALLBACK_PROOF.md明确NOT_RUN；本总报告。

Before/after源码/配置/Phase4 bank-corpus-golden/所有E01/E02报告和旧锁日志450/450字节相同，13/13表行数及内容hash相同，52/52完整vector与ID hash相同；business/RAG trace/DDL/vector writes0。未add/commit/push，原dirty状态保留，报告沿用docs ignore，为本地交付。target/phase5-epoch03保存独立once锁、工具源码及诊断，未覆写旧轮target文件。

P0：未发现凭证泄露或持久化/源码越界。P1：实际DeepSeek模型替换导致冻结身份不能成立，当前阻断是model drift，E02的历史503不改写。P2：本轮单例native成功和合成38集无法证明整体语义/泛化质量。建议Commander审核实际模型替换与是否授权新冻结身份、新epoch；本轮STOP，Phase5/Phase6均未解锁。
