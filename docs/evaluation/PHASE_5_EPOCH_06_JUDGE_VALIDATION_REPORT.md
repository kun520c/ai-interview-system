# Phase 5B E06 双模型真实质量复验报告

**E06已完整执行，原严格质量验收FAIL。** 19套定向365项测试通过（failures0/errors0/skipped0），Gate0/1通过后执行38个冻结案例的新鲜真实推理114次，零重试；Jev native38/38，但primary+feedback33/38，独立baseline38/38，配对33/38。5C按门禁NOT_RUN。

## 执行与证据范围

独立批次UTC开始 2026-10-08T11:37:56.115764100Z，结束 2026-10-08T11:42:57.419356700Z；runner=target/phase5-epoch06；一次锁survey-attempt.lock。真实请求/返回：Jev jev-latest→jev-1.13.0（38），DeepSeek deepseek-flash→deepseek-flash（76）。Benchmark SHA `fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a`。冻结30 MAIN+8 FOLLOW_UP、24排序、4修复及全部原阈值未改。

本轮只新增E06报告/target工具，所有原dirty生产源文件、配置、Prompt、gold、历史报告未改；无Git暂存/提交/推送/重置。Gate0审查实际源码数值联合守恒、cent/一个相邻binary64及精确模型限定、候选来源和FOLLOW_UP MAIN IDs；JDK21纯离线19套365/0/0/0、新32项+原63项均复跑。原target/classes/surefire与历史358文件/1983项总冻结清单不变。静态审核详见[STATIC_PATCH_AUDIT](judge/PHASE_5B_EPOCH_06_STATIC_PATCH_AUDIT.md)。

Gate1真实Spring Environment/Binder/隔离初始化器加载；Jev provider、DeepSeek开关、host/model、timeout、maxTokens及request格式均实际绑定生产现值。只记录API/DB凭证SET/MISSING。MySQL21题/97评分点/21文档/52chunks；Milvus default/knowledge_chunk_vectors_local52向量、1024dim；Embedding qwen3.7-text-embedding-1024-dense-v1、topK5。E05 DB/vector全摘要、8query SHA、40个top5 evidence原chunkID/score/content以及检索源码版本一致，因此只复用真实生产RAG证据；没有复用模型输出。38份v2 Prompt重新生成并冻结，新SHA与批准加固一致、USER/Jev请求树与E05相同。

## 调用与可用性

| 类别 | HTTP尝试 | HTTP200 | 内层JSON合法 | 最终结构有效 |
| --- | ---: | ---: | ---: | ---: |
| Jev native | 38 | 38 | 外层38 | native38；合并33/38 |
| DeepSeek feedback | 38 | 38 | 36 | 33/38 |
| 独立DeepSeek baseline | 38 | 38 | 38 | 38/38 |
| 共同完整配对 | 不适用 | 不适用 | 不适用 | 33/38 |
| GET/Embedding/Milvus-search/5C外部fallback | 0/0/0/0 | 不适用 | 不适用 | 5C NOT_RUN |

全114次各attempt1，无失败修复、无重新调用、无silently fallback混样。5次sentinel记录would-fallback，真实fallback0。实际API未返回扣费金额，全部NOT_AVAILABLE；各request UTC/latency/usage/raw SHA/identity详见RUN_EVIDENCE.events与caseCallLedger。13表/向量快照读取是只读验证，单独于模型调用。

## 拒绝与质量结论

- Jev INVALID：E02-EXCELLENT（拼接/省略非连续quote）、E02-OFF_TOPIC（非法JSON）、H05-BOUNDARY（非法JSON）、E02-REPAIR（MAIN covered点quote空串）、H09-FAILED_REPAIR（MAIN covered点quote null）。均不产生合法Java分数，不补造；独立baseline继续且本38例均有效。
- 结构38/38门禁FAIL；因此不能把Jev幸存者n33 micro P/R/F1=1、exact33/33当完整PASS。baseline n38 micro P/R/F1=1、exact38/38；排序23/24、4修复及8追问结构通过，但文本确认假遗漏1项。
- 71份成功文本全字段审阅：Jev确认硬矛盾0/n33、有UNCERTAIN1；baseline确认H05-PARTIAL假遗漏1/n38、有UNCERTAIN2。3个争议全部候选原文+反馈+判据入报告，未暗算PASS；不是人类评审，也不声称模型内部原因。
- Jev全24排序仅20可评、4未评，修复仅2/4可评、FOLLOW_UP结构6/8；无效缺项不能从baseline补Jev结果。低信息/注入假covered有效9/10为0，缺1不可算通过全量。UNKNOWN4/4≤35、SECURITY2/2≤35实测通过；EXCELLENT3/4、OFF_TOPIC3/4不可全量通过。
- 5C NOT_RUN，绝不进入Phase6。完整每阈值PASS/FAIL/NOT_EVALUATED、每topic/type指标、24排序/4修复/38比分、五维/total/grade/Java裁决及usage详见[可靠性与比较](judge/PHASE_5B_EPOCH_06_RELIABILITY_AND_COMPARISON.md)。

## 零写入全摘要前后复核

| MySQL表 | 行数before/after | 主键有序全行SHA256 before=after |
| --- | --- | --- |
| user | 4/4 | 08d30d3fa1a61a9a354327ddae5def43e36e4d3f8a50ff136431f0932d5e6df4 |
| question | 21/21 | a7762438ec109add66bc45f56316173d40b322c89cd55272c1e923c5f6b66705 |
| question_scoring_point | 97/97 | 6b521d2493a0b75b5c7c151b7f9d9f90198afa94bd68290a7f4b09ea814de68d |
| interview_session | 0/0 | e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 |
| interview_question | 0/0 | e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 |
| interview_answer | 0/0 | e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 |
| answer_evaluation | 0/0 | e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 |
| knowledge_document | 21/21 | 3a64b29224297cd7639058653d18b40ac20e352f10abc01d177ce901b72fa01d |
| knowledge_chunk | 52/52 | 9fe12f6b3f824e36e72dc6578da78f15917c69df836b1cbe40dd0fda4983d8f0 |
| rag_retrieval_batch | 0/0 | e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 |
| rag_hit_log | 0/0 | e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 |
| interview_report | 0/0 | e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 |
| user_weakness | 0/0 | e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 |

Milvus52条完整向量值+元数据有序SHA before=after `89c62c1f3bb4751f4ada6cd6f4ab62e8518eb660143f3892a29c67a7a6b25a9f`；vector IDs也完全相同。题库/Golden/文档/RAG/源代码/所有历史Epoch及locks的1983项完整before/after清单见target/phase5-epoch06/integrity-before.json和integrity-after.json。私有local配置仅原位内存绑定，没有复制或记录其SHA/凭证值，非私有配置SHA已复核。

没有写RAG trace、没有创建生产Session/Answer/Evaluation、未调用持久化OrchestrationService、无Milvus insert/delete/create/drop；MySQL连接read-only，Mapper只读取现有知识。无401/403/429/5xx、模型漂移、隐藏重试、重复请求或数据库/vector/hash变化。运行中普通INVALID遵循完整诊断批次规则，未触发全局hardSTOP。

## 交付路径及实际状态

以下七份独立E06交付均落在本地，旧报告无覆盖。JSON含38分母/invalid目录/完整新鲜响应证据与v2 Prompt、query/top5/Java决定；静态审计包含准确源码行号和JUnit XML SHA。

- docs/evaluation/PHASE_5_EPOCH_06_JUDGE_VALIDATION_REPORT.md（本报告）
- docs/evaluation/judge/PHASE_5B_EPOCH_06_STATIC_PATCH_AUDIT.md（独立源码/快照/365测试门禁）
- docs/evaluation/judge/PHASE_5B_EPOCH_06_RUN_EVIDENCE.json（运行、环境、前后摘要、114事件与全部审核）
- docs/evaluation/judge/PHASE_5B_EPOCH_06_JEV_RESULTS.json（38条JeV资格，33有效/5INVALID）
- docs/evaluation/judge/PHASE_5B_EPOCH_06_DEEPSEEK_BASELINE_RESULTS.json（38独立真实有效结果）
- docs/evaluation/judge/PHASE_5B_EPOCH_06_RELIABILITY_AND_COMPARISON.md（门槛台账、指标、全71反馈审核与争议）
- docs/evaluation/judge/PHASE_5C_EPOCH_06_FALLBACK_PROOF.md（NOT_RUN及原因）

复现工具/日志/原始HTTP/manifest均在target/phase5-epoch06；完整精确文件清单与SHA在deliverable-manifest.json。tests.log实际365/0/0/0、harness内存自测9/0/0/0、Prompt重建38/38；未跑全量mvn test/真实Smoke，不能称全项目回归。入口shell -D解析错误发生在Java启动之前，原log归档；临时脚本引号修正后真正预检一次通过，没有服务/模型重试。离线独立auditor也修正其对原nullable-text规范化的错误假设，保留raw响应，不修改生产。

P0新观测0；P1为5结构拒绝、baseline确认硬矛盾1及3争议；P2为反馈不完整/重复建议/范围扩张和未证实供应商内部原因。E05仍原FAIL；v1→v2是新条件，同USER不同SYSTEM，E06结果不能作为严格因果A/B或直接能力提升差值。没有宣布模型质量通过。

**已停止，等待Commander审核。不得自行开始新Epoch、修复生产或进入Phase6。**

最终复核：1983项原文件SHA、Git status与HEAD均保持，114份新raw响应SHA全部一致；输出脱敏检查未发现已知凭证值匹配，不保存请求Header。完整检查摘要见target/phase5-epoch06/final-verification.json。历史E01没有可找到的独立once-lock，现存报告/诊断/嵌入证据已冻结，未补造旧档案。
