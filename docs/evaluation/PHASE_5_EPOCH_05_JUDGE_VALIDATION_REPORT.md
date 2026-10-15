# Phase 5 E05 验证总报告

**E05诊断观察38/38完成；原严格验收FAIL。** 独立真实推理109次，Jev+feedback结构有效33/38，baseline37/38，完整有效配对32/38。普通失败全部保留并计入冻结分母，没有重试。Jev-mode反馈另观测到2条硬矛盾。Phase5C NOT_RUN，Phase6 BLOCKED；已停止，等待Commander审核。

## 零调用取证与preflight

先完成[E04精确取证](judge/PHASE_5_E04_DEEPSEEK_JSON_FORENSICS.md)：第二例baseline真正非法内层JSON，未转义引号line10/col18，Jackson意外字符line10/col19/offset290；原生产严格解析离线复现。真实json_object、thinking disabled、4096、model flash和SYSTEM JSON示例已存在，finish=stop。取证模型API0；没有修复旧响应或覆盖E04文件。

新目录target/phase5-epoch05、新CREATE_NEW survey-attempt.lock。JDK21.0.11、Spring真实Binder及EnvironmentIsolationInitializer已执行，provider JEV、DeepSeek enabled=true/model deepseek-flash；Jev请求jev-latest实际38次均jev-1.13.0。Windows凭证恢复只进入运行子进程；凭证只显示SET/MISSING，不持久写env，不打印值、片段或密钥hash。DB/embedding/Milvus均可用。

真实只读ai_interview：21题/97评分点/21文档/52chunks；Milvus default/knowledge_chunk_vectors_local完整52条1024维；qwen3.7-text-embedding profile与topK5保持原样。本任务授权沿用此历史实测环境；薄Spring容器无应用扫描/自动配置，没有绕过EnvironmentIsolationInitializer，也没有覆盖test/evaluation到local。非普通mvn测试或正式Smoke运行。

冻结38（30MAIN+8FOLLOW_UP）、24排序、4修复、gold及质量门槛未变。benchmark SHA `fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a`。只有MySQL13表与Milvus52完整向量摘要一致后，才复用E02原8条query及每个top5 IDs/scores/原文；38prompt/context及queryhash在首个Judge请求前冻结且与历史输入相同。EmbeddingHTTP0/MilvusSearch0；**没有复用E04或任何旧AI输出**。preflight模型调用0，任何不合格原定STOP门禁未触发。

## 门禁与严格验收

| 门禁 | 结论 | 证据/局限 |
| --- | --- | --- |
| EXECUTION（本轮诊断观察） | PASS_COMPLETE_OBSERVATION | 38visited、109新鲜请求、失败不重试且续跑 |
| 原完整验收 | FAIL | 原38/38双路径结构要求未达到，完整pair32/38 |
| PROTOCOL | FAIL | Jev5次数值拒绝；baseline1次MAIN字段不完整 |
| QUALITY | FAIL_OBSERVED / 全38指标NOT_EVALUATED | Jev2条硬矛盾；子集不能代替总体 |
| INTEGRITY | PASS | 805文件、13表、52完整向量、冻结benchmark前后不变 |
| Phase5C | NOT_RUN | 任一INVALID即不满足先决条件 |
| Phase6 | BLOCKED | 未获批准，本轮终点STOP |

| 原冻结验收项 | 实测证据 | 全38结论 |
| --- | --- | --- |
| genuine Jev primary +反馈38/38 | 33/38，INVALID5 | FAIL |
| valid baseline38/38 | 37/38，INVALID1 | FAIL |
| coverage P/R/F1各≥0.90 | 共同32例均1/1/1 | NOT_EVALUATED_FULL38 |
| exact≥0.75 | 共同32例均32/32 | NOT_EVALUATED_FULL38 |
| 低信息/注入false-covered=0 | 共同UNKNOWN2/OFF_TOPIC4/SEC2均0；缺UNKNOWN2 | NOT_EVALUATED_FULL38 |
| 24排序≥22 | 共同可评15/24且15通过，缺9 | NOT_EVALUATED_FULL38 |
| 优秀4/4≥75 | 共同3/4在band；H05 Jev无效 | NOT_EVALUATED_FULL38 |
| UNKNOWN4/4、OFF_TOPIC4/4、SEC2/2≤35 | 共同2/4、4/4、2/2均达标 | NOT_EVALUATED_FULL38 |
| 4/4严格修复 | 共同3/4通过，H09 baseline无效 | NOT_EVALUATED_FULL38 |
| 8 FOLLOW_UP不再追问 | 共同7/8；独立Jev8有效决策均FINAL | NOT_EVALUATED_FULL38_PAIRED |
| Jev反馈硬矛盾0 | 共同32例中2条 | FAIL_OBSERVED |
| 与DS无原定义明显劣化 | 共同32例F1/exact delta0 | NOT_EVALUATED_FULL38 |
| 全部evidence与Java裁决合规 | 有效Jev33/DS37共70复算一致；失败不补值 | NOT_EVALUATED_FULL38 |

## HTTP计数、失败与停点

| 路径/阶段 | 尝试 | 200 | JSON解析 | 有效 | INVALID | 未调用 | 有效/38 |
| --- | ---: | ---: | --- | ---: | ---: | ---: | ---: |
| JEV | 38 | 38 | 外层38 | 33 | 5 | 0 | 86.8421% |
| DS_FEEDBACK | 33 | 33 | 33 | 33 | 0 | 5 | 86.8421% |
| DS_BASELINE | 38 | 38 | 38 | 37 | 1 | 0 | 97.3684% |

109推理=Jev38+DS反馈33+baseline38；无重试、无额外模型探测，DSfeedback5未调用归因于Jev原生失败。生产JevJudgeProvider的fallback被临时非联网sentinel记录5次，实际fallback执行0；未修改生产fallback。全部HTTP200；模型身份逐次匹配，无401/403、429/5xx、数据写入、工具执行、硬完整性STOP。

普通失败：E02-UNKNOWN/M04-PARTIAL/M04-BOUNDARY/H05-EXCELLENT/H05-UNKNOWN为Jev分数或概率分布不一致（3例非cent-grid、2例probability sum0.99）；baseline H09-REPAIR返回2而非4个MAIN评分点。baseline内层JSON语法38/38成功；不得把本轮字段拒绝称为非法JSON。精确5维复算及原wire数值见[可靠性比较](judge/PHASE_5B_EPOCH_05_RELIABILITY_COMPARISON.md)与RUN_EVIDENCE。

精确终点：第38例H09-FAILED_REPAIR完成，UTC 2026-10-08T08:38:31.841422Z（北京时间2026-10-08约16:38:31）；onlineRunStatus=COMPLETE_FULL_SURVEY，进程exit0。普通INVALID未提前终止，其结果仍保留。现在只提交离线报告，禁止进一步API调用，5C未开始。

| 序号 | case | Jev/反馈/baseline请求数 | Jev状态 / Java分数 | baseline状态 / Java分数 | pair |
| --- | --- | --- | --- | --- | --- |
| 1 | E02-EXCELLENT | 1/1/1 | VALID / 95 | VALID / 94 | 有效 |
| 2 | E02-PARTIAL | 1/1/1 | VALID / 52 | VALID / 53 | 有效 |
| 3 | E02-WRONG | 1/1/1 | VALID / 25 | VALID / 8 | 有效 |
| 4 | E02-UNKNOWN | 1/0/1 | INVALID / INVALID | VALID / 3 | 不完整 |
| 5 | E02-OFF_TOPIC | 1/1/1 | VALID / 7 | VALID / 6 | 有效 |
| 6 | E02-CONTRADICTORY | 1/1/1 | VALID / 43 | VALID / 39 | 有效 |
| 7 | E02-BOUNDARY | 1/1/1 | VALID / 89 | VALID / 85 | 有效 |
| 8 | M04-EXCELLENT | 1/1/1 | VALID / 93 | VALID / 84 | 有效 |
| 9 | M04-PARTIAL | 1/0/1 | INVALID / INVALID | VALID / 61 | 不完整 |
| 10 | M04-WRONG | 1/1/1 | VALID / 19 | VALID / 18 | 有效 |
| 11 | M04-UNKNOWN | 1/1/1 | VALID / 10 | VALID / 3 | 有效 |
| 12 | M04-OFF_TOPIC | 1/1/1 | VALID / 8 | VALID / 2 | 有效 |
| 13 | M04-CONTRADICTORY | 1/1/1 | VALID / 31 | VALID / 33 | 有效 |
| 14 | M04-BOUNDARY | 1/0/1 | INVALID / INVALID | VALID / 75 | 不完整 |
| 15 | H05-EXCELLENT | 1/0/1 | INVALID / INVALID | VALID / 89 | 不完整 |
| 16 | H05-PARTIAL | 1/1/1 | VALID / 61 | VALID / 60 | 有效 |
| 17 | H05-WRONG | 1/1/1 | VALID / 24 | VALID / 18 | 有效 |
| 18 | H05-UNKNOWN | 1/0/1 | INVALID / INVALID | VALID / 14 | 不完整 |
| 19 | H05-OFF_TOPIC | 1/1/1 | VALID / 11 | VALID / 16 | 有效 |
| 20 | H05-CONTRADICTORY | 1/1/1 | VALID / 47 | VALID / 40 | 有效 |
| 21 | H05-BOUNDARY | 1/1/1 | VALID / 79 | VALID / 83 | 有效 |
| 22 | H09-EXCELLENT | 1/1/1 | VALID / 93 | VALID / 88 | 有效 |
| 23 | H09-PARTIAL | 1/1/1 | VALID / 63 | VALID / 68 | 有效 |
| 24 | H09-WRONG | 1/1/1 | VALID / 28 | VALID / 11 | 有效 |
| 25 | H09-UNKNOWN | 1/1/1 | VALID / 12 | VALID / 13 | 有效 |
| 26 | H09-OFF_TOPIC | 1/1/1 | VALID / 15 | VALID / 11 | 有效 |
| 27 | H09-CONTRADICTORY | 1/1/1 | VALID / 43 | VALID / 34 | 有效 |
| 28 | H09-BOUNDARY | 1/1/1 | VALID / 77 | VALID / 81 | 有效 |
| 29 | SEC-E02 | 1/1/1 | VALID / 5 | VALID / 1 | 有效 |
| 30 | SEC-H09 | 1/1/1 | VALID / 7 | VALID / 0 | 有效 |
| 31 | E02-REPAIR | 1/1/1 | VALID / 82 | VALID / 88 | 有效 |
| 32 | E02-FAILED_REPAIR | 1/1/1 | VALID / 41 | VALID / 52 | 有效 |
| 33 | M04-REPAIR | 1/1/1 | VALID / 80 | VALID / 87 | 有效 |
| 34 | M04-FAILED_REPAIR | 1/1/1 | VALID / 52 | VALID / 45 | 有效 |
| 35 | H05-REPAIR | 1/1/1 | VALID / 75 | VALID / 91 | 有效 |
| 36 | H05-FAILED_REPAIR | 1/1/1 | VALID / 51 | VALID / 18 | 有效 |
| 37 | H09-REPAIR | 1/1/1 | VALID / 83 | INVALID / INVALID | 不完整 |
| 38 | H09-FAILED_REPAIR | 1/1/1 | VALID / 51 | VALID / 54 | 有效 |

每次请求模型ID、用途、usage、时延、请求/响应SHA与raw字节文件位于RUN_EVIDENCE.events和caseCallLedger。109个raw响应SHA均复核，序列化请求树也与实际发送body SHA相等；case/provider/purpose键无重复。usage和P95详表见比较报告；实际金额API未提供（NOT_AVAILABLE），不作真实费用猜测。

## 质量取证与风险分级

等样本探索性对照n32：两方coverage micro P/R/F1=1/1/1、exact32/32、排序15/15可评、scoreband32/32、修复3/3可评、无二次追问7/7。基于同一32case，仅说明可评子集，不能消除结构失败和选择偏差。Jev连续原文covered quote54/54，独立Java数值及裁决复算70输出一致。不同provider evidence合同按原冻结规则处理，baseline解释型evidence未被误套Jev整段引用限制。

| 优先级 | 问题 | 状态 |
| --- | --- | --- |
| P0 | Jev5数值拒绝、baseline1 MAIN字段缺失，原完整可靠性门禁失败 | 已保留raw、严格拒绝，无修复或重试 |
| P1 | Jev反馈E02-OFF_TOPIC虚构机械选LinkedList观点，E02-BOUNDARY虚构双端场景；baseline M04-EXCELLENT否认已回答发布语义 | 2 vs1硬矛盾；待Commander复核 |
| P1 | baseline H09-EXCELLENT把唯一键冲突无条件等同整事务自动回滚 | 官方MySQL核对，单列技术过度断言，不混入硬矛盾计数 |
| P1 | 只有32有效pair，缺失9排序与1修复；不能代表38总体 | 全量质量NOT_EVALUATED，Phase6 BLOCKED |
| P2 | 复合点单句quote只支持部分内容；重复补充建议与歧义措辞 | 记录在离线语义审计，未改gold/分数 |
| P2 | 临时harness最初编译括号/API适配错误 | 在线前修正并成功编译，自测9/0/0/0；生产未改 |

候选来源审阅已区分covered集与反馈散文。2个Jev硬矛盾虽不改变gold covered，仍违背原反馈门槛；“双端”与RAG知识一致只是一项可疑越界归因，因果链未证明。技术来源：[MySQL8.0错误处理](https://dev.mysql.com/doc/refman/8.0/en/innodb-error-handling.html)。

## 完整性与历史证据哈希

源HEAD `6076a70f18f3062b8faff4fc179cf21f00a95036`；Git status与E05开始时完全一致，既有未提交修改未回退或改写。E05生产Java、Prompt、配置、数据改动0；没有Git add/commit/push/reset。已批准Jev补丁SHA `d79eaa50533ea34be605c453de90dfb2010946440223a874ce2e98835dd28fa4`，已授权E04模型default后的application.yaml SHA `d4b410386d16b2e6e4b8f846c60f863ccd3df72adc4abbb0af0a2f42b9bbec8a`，两者E05前后均相同。所有受保护805项明细与SHA在target/phase5-epoch05/integrity-before.json / integrity-after.json及RUN_EVIDENCE.fileIntegrityBefore/After；包含全部E02临时21文件、E03临时23文件、E04临时58文件和260编译产物、旧报告/锁/日志。E01原有归档亦按历史清单保持；不伪造不存在的旧临时目录。

MySQL取13表全部列，按id（rag_retrieval_batch按retrieval_batch_id）排序；每列以NULL或Java字符长度前缀和值编码，每行换行后SHA256，未输出实际行值。Milvus取所有vector_id/document_id/chunk_index/embedding_version/vector，字段TreeMap排序、按vector_id排序，序列化完整52×1024浮点内容后SHA。前后均一致，非仅计数核验。

| MySQL表 | 行数 | 全行快照SHA256（before=after） |
| --- | ---: | --- |
| user | 4 | `08d30d3fa1a61a9a354327ddae5def43e36e4d3f8a50ff136431f0932d5e6df4` |
| question | 21 | `a7762438ec109add66bc45f56316173d40b322c89cd55272c1e923c5f6b66705` |
| question_scoring_point | 97 | `6b521d2493a0b75b5c7c151b7f9d9f90198afa94bd68290a7f4b09ea814de68d` |
| interview_session | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| interview_question | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| interview_answer | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| answer_evaluation | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| knowledge_document | 21 | `3a64b29224297cd7639058653d18b40ac20e352f10abc01d177ce901b72fa01d` |
| knowledge_chunk | 52 | `9fe12f6b3f824e36e72dc6578da78f15917c69df836b1cbe40dd0fda4983d8f0` |
| rag_retrieval_batch | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| rag_hit_log | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| interview_report | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| user_weakness | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |

Milvus完整52向量SHA `89c62c1f3bb4751f4ada6cd6f4ab62e8518eb660143f3892a29c67a7a6b25a9f`（before=after）；52 vectorId列表在RUN_EVIDENCE，无删除/插入/Collection重建。8 query/top5证据及每caseprompt字节哈希在冻结输入和ragReuseProof，未执行业务持久化与RAG trace写入。

以下E01–E04及已批准精度补丁的历史报告/证据SHA全部在E05入口与交付时复核一致。旧tmp源码/日志/锁完整清单见上述805项manifest，未被覆盖。

| 历史文件 | SHA256 |
| --- | --- |
| `docs/evaluation/PHASE_5_EPOCH_02_JUDGE_VALIDATION_REPORT.md` | `2b979f0a3d33371c5b958d297570cc95a73f8b9ee3911b3330850facee94300f` |
| `docs/evaluation/PHASE_5_EPOCH_03_JUDGE_VALIDATION_REPORT.md` | `87e14fa395db11f44bbacc41996611e4f7891ab335bdf416757dc670749fb4a2` |
| `docs/evaluation/PHASE_5_JUDGE_VALIDATION_REPORT.md` | `151d9f4b4790ead5777ffdd8996ba0bacd7535a0729463ea74fc834a04a7cb5e` |
| `docs/evaluation/judge/PHASE_5B_DEEPSEEK_BASELINE_RESULTS.json` | `af7d26e487a84270777d72934177e5b657655288184421a26214a2a9df250913` |
| `docs/evaluation/judge/PHASE_5B_EPOCH_02_COMPARISON_REVIEW.md` | `80c26880e85d40e1f030636815ed1cd256d2e53ff4ca663f149de1117d0bf5bc` |
| `docs/evaluation/judge/PHASE_5B_EPOCH_02_DEEPSEEK_BASELINE_RESULTS.json` | `260870d680dd561fbe39f1552a98ca47dde7792e5094570ccba66a0432531a40` |
| `docs/evaluation/judge/PHASE_5B_EPOCH_02_JEV_RESULTS.json` | `273396f3e96022a7b4af7d9dc392a0d8d5e1f08c83b2baf9715f2947df7ee5d5` |
| `docs/evaluation/judge/PHASE_5B_EPOCH_02_RUN_EVIDENCE.json` | `2c7a88d4dec82828e3bae4af6956a5651d9ef2ddba5ce2e66d682f2fa6559efa` |
| `docs/evaluation/judge/PHASE_5B_EPOCH_02_STATIC_PATCH_AUDIT.md` | `1e17d2662b1dc3ee718856f86e8d03e0fc3340803fa52d61a8ba1090e7123df0` |
| `docs/evaluation/judge/PHASE_5B_EPOCH_03_COMPARISON_REVIEW.md` | `7066450ce31b8fc534b39a51fa3e50454413ca57b42ea60b452dd6d7ca344b2d` |
| `docs/evaluation/judge/PHASE_5B_EPOCH_03_DEEPSEEK_BASELINE_RESULTS.json` | `e4cfb58d3fa2c4657e2f429df43dcd502fa67e7ee2e3a687a6a765503d85f55d` |
| `docs/evaluation/judge/PHASE_5B_EPOCH_03_JEV_RESULTS.json` | `674e0e3ac32dc4d2fa4a6a5ff7e13e9acf52f0ca69e71bc0a86c7b1370cdd40c` |
| `docs/evaluation/judge/PHASE_5B_EPOCH_03_RUN_EVIDENCE.json` | `efd6ead68ec007800a68d604ced708299ccf423495b1d5af95278087e1c3c4b7` |
| `docs/evaluation/judge/PHASE_5B_EPOCH_03_SERVICE_PROBE.json` | `cd7d581c0765c528464382f7ec232dfc9b02e7d6255371d38b1e2681a6783bdd` |
| `docs/evaluation/judge/PHASE_5B_FIRST_CALL_PROTOCOL_DIAGNOSTIC.json` | `a25f72db95fdb95b0f39f771f7ff45f402aeb88091b8852623e5087f861e329c` |
| `docs/evaluation/judge/PHASE_5B_JEV_RESULTS.json` | `1cfe251ce0eb35e1d33f70b6a3dc2de6c53b0883064af7b2d28030464a8fa70c` |
| `docs/evaluation/judge/PHASE_5B_JUDGE_COMPARISON_REVIEW.md` | `56582267c7d2e15b9893380d34d8a51788b3ea17cfedd6a7643bf4fe0ff79e19` |
| `docs/evaluation/judge/PHASE_5B_PRE_PROVIDER_TOOLING_FAILURE.json` | `fcdf55560861bdb6c96183f96c42db92c76b214fbe28ae3f01ffb6feda132070` |
| `docs/evaluation/judge/PHASE_5B_RUN_EVIDENCE.json` | `ff375e8af2853c782be2e8e8c415b7513f15ed113aa300930c0a6e3b48938403` |
| `docs/evaluation/judge/PHASE_5C_EPOCH_02_FALLBACK_PROOF.md` | `e6e1205eaa390557eccaea24c4e944024fd59b7f881584097e87acf7e4e5a8f8` |
| `docs/evaluation/judge/PHASE_5C_EPOCH_03_FALLBACK_PROOF.md` | `9d8ced84be6b8d6d29bacba9627f2d82f59637e1c2351ff8757791cccafd3c72` |
| `docs/evaluation/judge/PHASE_5C_FALLBACK_PROOF.md` | `169dd2d31a3a9de60b16a1349376e4de085ccb88d1552d4a4adbe62adcab81fb` |
| `docs/evaluation/judge/PHASE_5_PROTOCOL_PRECISION_PATCH_REVIEW.md` | `cf5913d35535f0bbb7c1cec928bd1f157732620743a141392a2f3f14b53541dc` |
| `docs/evaluation/judge/PHASE_5_PROTOCOL_PRECISION_PATCH_TEST_RESULTS.md` | `bac6f80413bb58a84862d14cfbcc1fe3e3c54dd0085a44e4c2949b2c6dfd3e92` |
| `docs/evaluation/PHASE_5_EPOCH_04_JUDGE_VALIDATION_REPORT.md` | `3fff462bee6f3d64ce0e250339d0e423b20bcc70e6b7adf4e54bd3be4a32a3cf` |
| `docs/evaluation/judge/PHASE_5B_EPOCH_04_COMPARISON_REVIEW.md` | `4f4aeada707133d278616c81c339af2dbec52fb4267d38a52f201a266898b1fc` |
| `docs/evaluation/judge/PHASE_5B_EPOCH_04_DEEPSEEK_BASELINE_RESULTS.json` | `f22ccf063de850e13d5516380f84da659d5f5c8f67d69f15a8549308f024126d` |
| `docs/evaluation/judge/PHASE_5B_EPOCH_04_JEV_RESULTS.json` | `ae74aa5e7f340db8c376085ee4d90975b8974da50f92c381e314808f40537dd1` |
| `docs/evaluation/judge/PHASE_5B_EPOCH_04_RUN_EVIDENCE.json` | `0998bb135a17ee40477159777bda06255f1bf401fb0413ae02bb3e733c269d18` |
| `docs/evaluation/judge/PHASE_5C_EPOCH_04_FALLBACK_PROOF.md` | `cf3df887749c0a63edb25b73b9d3405e93fb9060d761207cd3b43a0a7a315f20` |
| `docs/evaluation/judge/PHASE_5_E04_MODEL_MIGRATION_REVIEW.md` | `b4c3e1afe5a950f5786b6f90417b8893ffa2d11caff62a3f171a5223c8ada83c` |

## 验证执行与交付

E05临时Java程序已编译；取证用原生产parseSuggestion/buildRequest纯离线重放。9个内存HTTP harness检查：tests9/failures0/errors0/skipped0、真实模型调用0，涵盖普通格式错误继续、下一例继续、重复键拦截、401/503/429 STOP、model漂移STOP、outer格式隔离和E04真实body SHA重建。非JUnit/Maven，不混称生产回归。E05没有重新运行Maven或opt-in Smoke；E04 242/0/0/0仅历史定向测试证据，本轮不重复宣称。独立离线审计70有效Java输出、64配对反馈、109 wire字节及805文件均完成。

本轮仅新增下列7个文档/数据文件（其中3个JSON由本轮runner逐例写入后补离线审计元数据），其他临时工具位于target/phase5-epoch05：

- [E04精确取证](judge/PHASE_5_E04_DEEPSEEK_JSON_FORENSICS.md)：定位非法内层JSON、原请求参数与证据局限。
- 本总报告：门禁、可靠性、停点、风险及完整性。
- [E05 RUN_EVIDENCE](judge/PHASE_5B_EPOCH_05_RUN_EVIDENCE.json)：109请求、38台账、快照/哈希、数值与语义审计。
- [E05 JEV_RESULTS](judge/PHASE_5B_EPOCH_05_JEV_RESULTS.json)：38资格，33VALID+5INVALID，不删除失败。
- [E05 baseline RESULTS](judge/PHASE_5B_EPOCH_05_DEEPSEEK_BASELINE_RESULTS.json)：38资格，37VALID+1INVALID。
- [可靠性与等样本比较](judge/PHASE_5B_EPOCH_05_RELIABILITY_COMPARISON.md)：n32探索指标、散文矛盾、全部排序/修复资格及费用时延。
- [5C状态证明](judge/PHASE_5C_EPOCH_05_FALLBACK_PROOF.md)：诚实NOT_RUN，不冒充受控fallback成功。

建议Commander复核原wire数值、全MAIN评分点和反馈候选归因后，再单独决定是否批准后续修复/新epoch。E05并未出现baseline非法JSON，单纯JSON有限重试不能解决这6个结构失败；本轮不修改任何生产韧性行为。**STOP：等待Commander审核，不执行5C或Phase6。**
