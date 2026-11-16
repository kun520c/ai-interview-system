# 2026-11-11—2026-11-15 对话决策与工程复盘核证草稿

记录日期：2026-11-15，Asia/Shanghai。记录者：Codex。本批范围：**仅归档、只读核证和离线复算，不修改业务代码。**

目标文件：`/Users/kun/Developer/projects/ai-interview-system/docs/engineering/CHAT_DISCUSSION_DECISIONS_20261111_20261115.md`。

**本文件是独立待合并草稿，不是新的功能验收，也不宣布 M1-D 完成。** 四份长期记录已有未提交内容，M1-D 材料在本轮读取期间继续变化，因此不直接编辑它们。下文提供 20 个案例、6 条简历候选、8 条学习补充和安全合并位置；历史记录保留，不用新结论覆盖旧失败。

## 1. 来源、证据等级与使用边界

### 1.1 对话来源

- 原对话：[AI 模拟面试系统｜Java 后端工程优化与功能演进专项](chatgpt-conversation://6af42423-bc00-83ec-b8e1-c011f613241a)。本轮通过 `read_thread(threadId=6af42423-bc00-83ec-b8e1-c011f613241a, turnLimit=10)` 取得 5 个最近回合，返回 `hasMore=false`、无更早游标；不能据此声称已取得 11 月 11—15 日的全部原文。
- 已取得的近期用户消息：`a1bfd134-20f7-43b1-82d3-899213ed3caf` 要求多因素归因；`9651b920-fbb5-473c-b55f-025c48c093dd` 报告 R2 数据并提出同步也慢；`123cb8ae-6c83-4cc1-9a12-085b423ec94d` 要求考虑逐题评价与综合报告职责差异、尽量少改代码；`4f2c3cd1-1ed4-40ac-b1dd-b40614bf06db` 要求记录问题、方法和成果。
- 早期 B1/B2/B3、Q1/Q2 等原对话未在本次返回中完整出现，使用**本次用户给定摘要**作为“对话报告”，再分别核对本地源码、提交、验收报告和可得原始材料。助手历史建议属于讨论材料，不是本轮执行指令、用户已批准上线或已实现的证明。
- 对话中曾有必要模型调用额度授权；**本次专项明确禁止模型调用**，本轮没有调用 DeepSeek、Embedding 或模型桩。

### 1.2 证据标签

- **[S｜源码核证]**：本轮实际读取本地源码、Git 提交差异或文件散列。证明实现/文件状态，不证明当前运行实例或业务效果。
- **[R｜原始材料复核]**：本轮从已保存 XML、日志计数、逐请求 CSV、原始事件或冻结 JSON 重新计算。是历史运行的离线复核，**不是本轮重跑通过**。
- **[H｜历史验收报告]**：本轮读到的已有验收记录；未独立重读其所有原始运行材料。实服务、Mock、故障注入类型按原报告保留。
- **[C｜对话报告]**：用户摘要或已取得原对话中的陈述；聊天事实不伪称独立验证。即使找到旁证，仍分别标明 C 与 S/R/H。
- **[D｜产品/方案决策]**：记录用户选择及取舍，不将选择本身写成效果证明。**[U｜待核证/候选]**：证据、实际实现、验收或个人贡献不足。

全文 `T/F/E/S` = 测试总数 / 失败 / 错误 / 跳过；实际通过数 = T−F−E−S。不同轮次重叠，不相加为独立用例数；Mock、真实 MySQL、真实 Broker、真实模型、浏览器分别记账。跳过不等于通过，HTTP 200 不等于 JSON 合法，结构校验不等于语义正确。

本文件不包含 API 密钥、口令、令牌、真实用户原答、完整私人 Prompt 或备份内容。原始日志只给定位信息与必要统计；私有档案不复制、不作为公开附件。没有核对本轮远端实时状态，也没有新建、停止或修改容器。

### 1.3 本轮核证快照

- 初始记录时点 **2026-11-15 04:02:36 +08:00**：分支 `main`，HEAD `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`，暂存区为空。记录了 1337 个相关源码/文档/工具文件的正文 SHA-256 与权限；这是本批保护清单，不是业务文件数或代码贡献数。
- 四份长期记录：`ENGINEERING_CASEBOOK.md` 与 `RESUME_EVIDENCE_BANK.md` 尚未跟踪；`IMPROVEMENT_LEDGER.md` 与 `SOURCE_LEARNING_BACKLOG.md` 已有未提交修改。M1-D 目录和工具已存在。04:05:31 复查发现 M1-D interim 的 summary 与两份 timeline 已变化，证实并行写入风险。
- 04:07:54 离线复核：B1/B2/B3/B4-B/B4-C/Embedding 共 **23 组历史 XML**；M1-B 最终日志计数；M1-C HTTP 594 行和浏览器 594 行（均含预热，合计 1188）；R2 14 组/224 条详细时间线与原始归档请求；Q1 的 12 卡标签、Q2 的 18 个结果和 D1 冻结题库。
- R2 `evidence-manifest.sha256` **28/28** 文件散列匹配；R2 最终生产源码清单 **211/211** 匹配当时工作区。该 211 项不包含前端，不能据此说整个工作树与 R2 相同。
- 入场前端工作树已含 `REPORT_POLL_INTERVAL_MS=1000`；R2 记录的正式前端仍为 3000 ms。**历史 R2 的 3 秒与本次工作树的 1 秒属于不同状态**；后者归为并行待验收改动，不回填成 R2 已实现成果。

## 2. Git 与构建身份索引

下面是本轮通过本地 `git log`、父提交关系及相关 diff 核对的身份。提交存在不等于生产上线；Git 作者字段也不证明代码由本人手写。旧报告的“当时未提交”保留，现有提交关系单列。

- **V01｜B1**：最终拆分提交的原始父版本 `b525683ce72def1f59432a33959f672c2c87b7a1` → 修复 `1df25ea6dfe6c818cf0c75a58c47f58c09ed524a`。
- **V02｜B2**：`1df25ea6dfe6c818cf0c75a58c47f58c09ed524a` → `7d259818c7650b4c57e3d642d3821cb17291e4a0`。
- **V03｜B3**：`7d259818c7650b4c57e3d642d3821cb17291e4a0` → `6de9e7aecb69a5269d1c8fa466f3d2dc57865ee4`。
- **V04｜DeepSeek**：B4-A 文档 `004e434d3c1352c15d022bc1bf08abdfb02f4a02`；以该父版本为起点，B4-B 修复 `f764c087f75d6b515b09bf2a0aef3f1d9a20c906`；B4-C 原始父版本为该 B4-B 提交，修复 `01ac774caff491b978dd2268e8b427355a64b97a`。
- **V05｜Milvus/Embedding**：Milvus `01ac774caff491b978dd2268e8b427355a64b97a` → `f71da96acb2fb8d8eec378341f7d9358e31eca19`；Embedding `6c4993c0f3948a84067b3d3372371db02d0cf5bc` → `71aeb22f7da906660763e9efb1100a20df7705c9`。中间 B4-E 和密码修复独立保留，不混为同一改动。
- **V06｜隔离/RAG**：E1–E3/D1/D3 当时运行基线为 `0c6f92afe331954f7cea37d33db688fe781cbf2c` 加各批工作区；语料 `9b7e35639178c9cc125d99e3a2cfe92a344fbb72`、工具 `854b9eadf7dfb165847e49f7bd1f1d3d27e5f9b3`、验收 `921f8cdb24bfda50e4e9a4d76d4e1017449fe032`、后续证据 `59667d93f96a685f2b8e4b57267d465f80ceb857`。这些主要是材料提交，不是新的检索算法修复 SHA；数据库导入也不能由 Git SHA 独立证明。
- **V07｜D1/D2**：D1 数据与工具归档 `b04976f0102c0edab63f87512d7f193d98072205`；D2 选择器 `921f8cdb24bfda50e4e9a4d76d4e1017449fe032` → `d090d25929bf21d9a69b4b2d3e8251c6ac6f9535`。D1 没有对应“业务代码修复 SHA”，内容版本为 D1-v2-20261114。
- **V08｜U1/U2**：合并后的功能提交 `d090d25929bf21d9a69b4b2d3e8251c6ac6f9535` → `f1fb02b429a92796b2dc0dd4c83c2259b5315d22`，并非每次浏览器实验各有独立 commit。
- **V09｜S1**：`f1fb02b429a92796b2dc0dd4c83c2259b5315d22` → `4bed0e2525b8359d7a21f0b6b7a3a990f905e3d9`；文档提交 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`。S1-R1 历史 evaluation 部署 JAR SHA-256 为 `48db442bff8d22f0b1c50186cace3627e5183923c0b167b799b6d87202a7b63f`，[H]，非本轮运行验收。
- **V10｜Q1/Q2/M1**：共同 Git 基线 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`；相关新材料、MQ 实现及后续改动在本轮入场时未提交。Q2 v5 已撤回，不能提供“v5 修复后 SHA”；M1-A 无业务修复 SHA，M1-B/C/R2 无独立提交 SHA。M1-D 无已验收最终 SHA。
- **V11｜M1-C 构建**：同步 A 来自 V10 clean HEAD，JAR `3fb095291adf7b21e8b69695f70aaa6085e6631b29522583f21af11a2e0d7c52`；最初 B 为 HEAD+未提交树，JAR `cf1a199e152acd4401c5a6ada1e6671d19f1af9d47f9a0ef1ac8c52a527d45ec`；锁/缓存最终修复 B 为 `f3b6095d5ed9cc465dfd8f000a1ea658a08c771576e19e8817e134de427e5334`。构建身份来自各自 environment/source-manifest，不把共同 HEAD 当作完整 B 版本。
- **V12｜R2 构建**：HEAD+未提交树，JAR `551d78688ecd01fafc16ef15d0e6fa1e745325e4220eb77156ebba789766eae8`；三个生产参数位置的补丁见 [R2 patch](m1c-r2-20261115/production-changes.patch)，源码 211 项清单见 [R2 source manifest](m1c-r2-20261115/final-source-manifest.json)。本轮仅核对清单，没有重新构建 JAR。

B1/B2/B3 的**原始实验**报告均以 `afbd1c5ab010f3a0a296e15e25c016f81eb2b74f` 加前批未提交工作树为基线；V01—V03 是后来拆分形成的准确 Git diff 边界，不能把父提交说成原实验当时运行的 clean checkout。精确复现实验仍需原报告的配置、样本和构建材料。

## 3. 工程案例：发现、取舍、证据与面试故事

### CD-01｜B1：数据库已成功，重放请求仍返回 500

- **对话报告 [C]**：并发重放误报 500。
- **发现/根因 [S+R]**：请求 A 提交后暂停，B 完成同请求并推进 Session，A 使用旧 Answer 快照或先检查新 Session 当前题，把合法重放当内部错误。仅有唯一键/CAS 不够，还须处理跨次读取的状态变化。
- **修复与取舍 [S]**：`InterviewWorkflowService.evaluateAnswer` 首读 EVALUATED 直接重放；当前题不一致时通过 `requireReloadedAnswer` 再确认 EVALUATED。保留归属、完整 payload、attempt fencing 和真实异常；不新增全局锁、外部调用长事务或任意循环重试。版本 V01。
- **失败/验证 [R]**：改前 XML **16/10/0/0**，10 个确定性交错实际 500；业务定向 **286/0/0/0**；全量 **1549/0/0/19**。测试为独立 MySQL 8.0.45 + MockMvc，外部模型替身，无外围测试事务，CountDownLatch 控制两暂停点×五条转移。初版夹具也有 10 个失败，但其中 5 个来自 MyBatis 代理 `callRealMethod()` 误用，不能算完整复现。
- **日志与结果**：[B1 报告](../verification/B1_CONCURRENT_IDEMPOTENT_REPLAY_20261111.md)；原始 `/private/tmp/ai-b1-20261111/reproduction-confirmed.log`、`reproduction-confirmed-reports/`、`focused-business-reports/`、`full-regression-reports/`。本轮重算上述 XML；没有重跑。指定交错中的重放恢复成立，不承诺穷尽竞态或外部调用 exactly once。
- **面试故事/待本人吃透**：可讲“单胜者写入成功后，旧读快照仍会造成错误 HTTP；通过确定性交错和两处只读完成判断修复”。源码入口 Workflow、`InterviewService.submitAnswer/buildSubmitAnswerResponse`、`InterviewConcurrentReplayIntegrationTest`；复用 L-B1-01～04，掌握未确认。

### CD-02｜B2：领取已提交，下一次查询失败却未收尾

- **对话报告 [C]**：任务领取后失败恢复。
- **根因 [S+R]**：claim 已在短事务提交 EVALUATING+attemptId，`findNextPendingMainQuestion` 却位于 try 外，异常跳过 FAILED 回写；后续同键请求只能等 stale。
- **修复/选择 [S]**：确认 OWNED 且排除 REPLAY 后，将下一题查询移入现有 try；按同一 attemptId 条件回写 FAILED。回写异常放入原异常 suppressed，原始 cause 保留。不把 try 扩到未取得资格前，不新增后台恢复。事务仍是无外层事务下的 REQUIRED，不是 REQUIRES_NEW。版本 V02。
- **失败/验证 [R]**：改前 **44/7/0/0**；修复 **44/0/0/0**；业务 **296/0/0/0**；全量 **1559/0/0/19**。其中真实隔离 MySQL 3 项通过独立连接观察已提交领取和 FAILED，查询故障为受控注入，非真实数据库断网。早期 AssertJ API 误用导致 testCompile 失败，不计为 JUnit 业务失败。
- **日志/残余**：[B2 报告](../verification/B2_POST_CLAIM_FAILURE_RECOVERY_20261111.md)；`/private/tmp/ai-b2-20261111/` 下 `reproduction-confirmed`、`focused-recovery`、`focused-business`、`full-regression` 对应日志与 `*-reports/`。数据库长期不可用、回写失败、进程退出、提交结果未知仍可能留下执行态。
- **面试故事/学习**：可讲“异常保护范围应从确认领取成功开始，而不是笼统给整个方法加事务”。看 Workflow 的 catch/helper、`InterviewWorkflowTransactionService.markEvaluationFailed`、`InterviewAnswerMapper.xml` 的 attempt 条件；L-B2-01～03 待确认。

### CD-03｜B3：FINAL 一致性与恢复旁路

- **对话报告 [C]**：报告 FINAL 一致性。
- **根因 [S+R]**：旧 SQL 用 INITIAL 兜底、窗口排名隐藏同一 MAIN 多条 FINAL；只检查条数无法证明题目集合、答案与追问归属。第一轮修复后，已有报告但非 READY 的恢复分支仍绕过校验。
- **修复/权衡 [S]**：SQL 只取 FINAL 且保留重复给 Java 检查；核对 MAIN 唯一集合/计划顺序/已答状态/最终答案及追问关联；Weakness 拒绝非 FINAL。恢复入口复用校验，READY 历史保持可读，不偷偷重算旧数据。当前 MQ 工作树把核心校验移到 `InterviewReportFinalReader`，旧报告中的 Service 行号不能直接套用。版本 V03，后续未提交迁移属 V10。
- **失败/验证 [R]**：正式改前 **55/14/0/0**，恢复旁路 **2/2/0/0**；扩展 **134/0/0/0**，业务 **320/0/0/0**，全量 **1589/0/0/19**。真实 MySQL 专项历史 24 项，报告模型为替身。早期缺 retrieval_batch_id/raw_result、CHECK 不满足和 Long/Integer 断言错误独立保留，不能算生产缺陷或通过降低约束解决。
- **原始证据**：[B3 报告](../verification/B3_REPORT_FINAL_CONSISTENCY_20261111.md)；`/private/tmp/ai-b3-20261111/` 的 `reproduction-verified`、`recovery-gap-reproduction`、`focused-complete`、`focused-business`、`full-regression` 日志/XML。
- **结果/学习**：Report+Weakness+READY 原子提交和 Java 均分保持；未扫描并修复历史错误数据，未证明模型语义质量。故事可讲“条数正确不等于业务集合正确，恢复分支也必须守同一不变量”；读 `AnswerEvaluationMapper.xml`、FinalReader、事务 Service、`UserWeaknessService`、`InterviewReportFinalConsistencyIntegrationTest`，L-B3-01～04 待确认。

### CD-04｜DeepSeek：固定 502 仍可能泄露日志，超时分类也不等于重试权限

- **对话报告 [C]**：DeepSeek 日志与可靠性治理。
- **发现/根因 [S+H]**：默认 HTTP/Jackson 异常 message、cause 和任意 finish_reason 可带上游正文；全局处理器打印 Throwable，客户端固定错误消息不能保护日志。嵌套 JSON 异常还会遮蔽实际传输超时。
- **实现/取舍 [S]**：Client 请求级 `onStatus` 提前拒绝非 2xx，不读取危险错误正文；网络/解析边界生成固定分类，不挂危险 cause；报告 JSON 异常同样隔离。B4-C 有界遍历 cause 类型、保留固定 reason；每次应用级请求仍一次，不按文本猜故障、不默认重试。版本 V04。
- **验证/失败 [R/H]**：B4-B 改前 **6/0/0/0** 是“观察到泄露”的成功断言，不是安全通过；修复聚焦 **281/0/0/0**、全量 **1635/0/0/19**。B4-C 改前 **42/25/0/0**、聚焦 **136/0/0/0**、业务 **336/0/0/0**、全量 **1698/0/0/19**，上述 XML 已复算。历史本机 HTTP 4 项、600 ms 配置实测约 604/607/604/605 ms，每例一次请求，仅证明本机等待返回，不能证明供应商停止计算/不计费。夹具漏超时、误把 close 获取流当 read、误匹配 `AuthorizationFilter` 均保留为测试问题。
- **证据/限制**：[B4-A](../verification/B4A_DEEPSEEK_RELIABILITY_AUDIT_20261112.md)、[B4-B](../verification/B4B_DEEPSEEK_LOG_SAFETY_20261112.md)、[B4-C](../verification/B4C_DEEPSEEK_TIMEOUT_RETRY_20261112.md)；`/private/tmp/ai-b4b-20261112/` 与 `/private/tmp/ai-b4c-20261112/` 的对应 `*-reports/` 和日志。未证明 WAN/TLS/代理、APM、wire DEBUG 或其他模块全局安全，也没有整链硬 SLA。
- **故事/学习**：可讲“安全边界是响应、异常对象和最终日志渲染三层；分类用来诊断，不自动授予付费重试”。看 `DeepSeekEvaluationClient`、`DeepSeekLogSafetyTest`、`DeepSeekHttpTimeoutTest`，L-B4A/B/C 现有条目待确认。

### CD-05｜Embedding 与 Milvus：框架边界、SDK 隐式重试和补偿

- **对话报告 [C]**：Embedding 日志可靠性与向量链路治理。
- **根因 [S+H]**：Spring AI 默认异常可携带 HTTP 正文，适配器保留 cause/拼接模型名会继续传播；Milvus SDK 2.6.20 默认多次 callable、部分耗尽路径返回 null，构造期数据库查询还存在 deadline 缺口。单 RPC timeout 不是整链超时。
- **实现/权衡 [S]**：`EmbeddingOpenAiApi` 在框架转换前检查安全形状并使用专属错误处理器，`SpringAiEmbeddingClient` 保留固定分类；不关闭全局栈，不复制整个模型。Milvus 单例发布前 `maxRetryTimes(1)`，构造期 gRPC Context 限时、finally 解绑；拒绝 null 存在性结果。减少自动瞬态恢复能力以获得明确失败边界，不承诺取消能撤销迟到写入。版本 V05。
- **验证 [R/H]**：Embedding 改前观察 **25/0/0/0**，有泄露是复现目标；聚焦 **195/0/0/1**、业务 **302/0/0/0**、最终全量 **1832/0/0/19**，已复算 XML。Milvus 历史报告：改前契约 **45/3/0/0**，修复聚焦 **180/0/0/0**，RAG/知识 **450/0/0/10**，全量 **1732/0/0/19**；本轮该组仅按报告，不冒称已复算。真实 JAR/本机 gRPC、真实 MySQL+Mock SDK 与真实 Milvus 服务器要分开。
- **失败/残余**：向量夹具维度不匹配由原校验正确拒绝；不为通过放松维度检查。CLI 独立 new 客户端未必继承 Spring 策略，长链预算、真实网络、迟到孤儿治理仍有边界；没有性能增益声明。
- **证据/学习**：[Embedding 验收](../verification/B5_P2B_EMBEDDING_LOG_SAFETY_20261113.md)、[Milvus 验收](../verification/B4D_MILVUS_RETRY_DEADLINE_20261113.md)；原始 `/private/tmp/ai-b5p2b-20261113/`、`/private/tmp/ai-b4d-20261113/`。故事可讲“先理解 SDK 真正的尝试和返回语义，再决定应用约束与按 attempt 补偿”；读上述实现、`KnowledgeDocumentProcessingService` 和 `KnowledgeAttemptFencingIntegrationTest`，L-B4D、L-B5P2B 待确认。

### CD-06｜MySQL/Milvus 隔离、RAG 指标与 E2E 失败边界

- **对话报告 [C]**：MySQL+Milvus 隔离和 RAG 测试。
- **已核证 [S/H]**：初始化器按解析后的 profile/schema/collection 拒绝 test/evaluation 混用，但不能代替主机、端口、实例身份核对。E1 初次方案曾因备份/权限未就绪 BLOCKED；后续 E1–E3 报告记录独立恢复和隔离目标落地。Milvus stop 命令成功但进程 134、`panic: context canceled`，不得写成优雅停机成功。版本 V06，不能给“RAG 准确率修复”虚构代码 SHA。
- **历史结果 [H]**：E1–E3 验证 13 表、163 列、42 索引、15 FK、42 CHECK，初始导入 21题/97点；其后 D1 扩至63/298。D3 最终 51 READY 文档、52 ACTIVE Chunk、52 向量，覆盖映射 298 点；不代表实时数据库仍是该数量。本轮未连接数据库或 Milvus。
- **负面指标/取舍 [H]**：D3 生产 MAIN Hit@1 为63/63，但 Prompt 直接相关证据仅75/315=23.81%；公开集67/300=22.33%，独立集21/120=17.50%，独立正例 Hit@5=15/18，均保留原分母/门槛。标签主要为 AI 临时判断，缺独立人工 gold；“检索进 Prompt”不能证明评分因果收益。更强过滤候选会损失 CORE/跨主题意图，未直接上线替换。
- **E2E 独立边界 [H]**：较早 [E2E 报告](../evaluation/E2E_EVALUATION_REPORT.md) 对42次首次答案请求记录1次500、5次首次报告记录2次502；恢复不抹去首次 FAIL，失败原始模型 JSON 未保存，不能猜数组究竟为空还是过长。该报告仍含已退役 Jev 时代上下文，不把旧 BLOCKED 当现行 DeepSeek-only 架构。D3 单场 EASY 经一次同键恢复后完成5 MAIN/3追问/5 FINAL/READY，首次502来自用量记录器重复消费响应流；属于测试工具问题，原失败和额外调用均保留。D3 定向103/0/0/0，没有本批全量或全面浏览器验证。
- **证据与日志**：[E1–E3](../verification/E1_E3_REAL_DATA_EXECUTION_20261113.md)、[D3 BCD](../verification/D3_BCD_FINAL_ACCEPTANCE_20261114.md)、[D3-E](../verification/D3E_EVIDENCE_QUALITY_REVIEW_20261114.md)、`docs/rag/d3-d/` 的生产/公开/独立指标 JSON，以及 E2E 的 `evidence/e2e-first.json`、`e2e.json`、`recovery.json`。私有恢复备份不随本文件交付；当前运行状态和账单未核验。
- **故事/学习**：可讲“命中率高而证据精度低，必须保留负例、独立集和首次失败”；读 `EnvironmentIsolationInitializer`、`EvaluationRagQueryBuilder`、`KnowledgeRetrievalService`、`EvaluationRetrievalAdapter`、PromptBuilder 与轨迹持久化；L-E1/E1E3、L-D3BCD/E 待确认。

### CD-07｜63 题、298 评分点与分类内随机组卷

- **对话报告 [C]**：63题298评分点随机组卷。
- **静态事实 [R]**：冻结 D1-v2 JSON 复算63题、298评分点，EASY/MEDIUM/HARD=15/21/27，每题权重和100。数据库真实导入63/298、21桶各3题由 D1/D2 历史验收支持 [H]，本轮不将文件统计冒充在线盘点。
- **根因/选择 [S]**：原选择器按排序取首题，扩容不自动带来随机。`QuestionPlanSelector.select` 以分类分桶、ID去重、稳定排序后均匀抽一题；生产用调用时 `ThreadLocalRandom`，测试注入 `IntUnaryOperator`；完整 MAIN 快照仍创建时一次保存。避免 SQL 随机排序和共享可变随机状态，不新增跨场去重承诺。版本 V07。
- **测试/失败 [H]**：D1 定向113/0/0/0、工具14项；初次113/0/73/0为 Mockito attach 环境错误。D2 定向268/0/0/0；真实 MySQL 29个回滚探针（24串行+3并发+空桶+历史快照），创建28场/194 MAIN，仅证明回滚内 SQL/隔离，不冒充独立 commit 可见性。固定 seed300次99/99/102只是诊断，另有合法索引可达性；不是长期随机分布保证。辅助脚本异常类型预期错误从4/1/0/0修至4/0/0/0。
- **证据/剩余**：[D1 验收](../verification/D1_QUESTION_BANK_EXPANSION_20261114.md)、[D1 冻结 JSON](../question-bank/D1_QUESTION_BANK_V2_20261114.json)、[D2 验收](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-D2-随机组卷与真实数据库验收.md)。原执行日志/XML由各报告的 D1/D2 受限证据索引定位，本轮不复制私有目录。未验证题目难度总体效度、跨场不重复或真实性能提升。
- **故事/学习**：可讲“数据扩容与随机算法分开验收，随机可测性和历史快照不变同样重要”；读 selector、`InterviewSessionService`、快照工厂与 `QuestionPlanSelectorTest`，L-D1/L-D2 待确认。

### CD-08｜U1/U2：响应丢失后，从持久化数据恢复上一题反馈

- **对话报告 [C]**：真浏览器反馈恢复。
- **根因 [S+H]**：服务端提交评分并推进下一题，返回丢失使页面未保存评价；本机提交记录按旧题存储，原恢复按当前题查找，全新浏览器更无本机状态。U1-R1 的组合重启场景暴露该缺口，不能扩大成所有单次刷新都失败。
- **修复/权衡 [S]**：U2 使用既有归属校验 GET详情，按 displayOrder 选择当前题之前最近 EVALUATED 评价，复用显示组件；账号代次、Session、当前题、生命周期保护迟到响应。无需重发答案、模型重算或新接口/Schema。另排除默认安全用户自动配置并覆盖测试切片，避免生成临时口令日志。版本 V08。
- **历史验证 [H]**：U2 后端定向165/0/0/0、全量1848/0/0/19，前端346/0/0/0；新反馈入口改前6项失败保留。真实 Chrome 丢弃成功响应后刷新，再重启前后端和全新浏览器登录，仍可取回同一反馈；恢复未新增答案 POST/模型调用，真实会话最终5 MAIN/5 FINAL/1 READY。该真实会话无追问，追问分支由 Mock 模型集成覆盖。
- **原始定位/负面指标**：[U1-R1 终验](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U1-R1-真实浏览器终验.md)、[U2 验收](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U2-恢复修复与运行安全验收.md)，U2 原 XML `/private/tmp/ai-u2-regression-rfsdkf_r/target/surefire-reports/`（本轮未重汇总）。真实浏览器轨迹位于原报告受限索引，未复制。U1正常评分 n=4/P50=3.105s，报告 n=1/3.147s；不能算可靠P95或后续MQ收益。选择器空值/自动化事件假设/端口冲突均为历史失败尝试，未混作业务回归。
- **结果/学习**：局部恢复验收成立，不提升评分质量 NO-GO，也不代表生产上线。故事可讲“响应丢失不等于业务未提交，读回持久评价比再次执行更合适”；读 `PreviousInterviewFeedback.vue`、`InterviewService.getDetail`、对应前端/离线业务测试，L-U1R1/L-U2 待确认。

### CD-09｜S1：前端不渲染，仍挡不住详情接口提前泄题

- **对话报告 [C]**：未作答未来题泄露修复。
- **发现/根因 [S+H]**：完整面试计划已持久化，详情接口直接返回全部题。修复前真实 HTTP 新建 EASY 后返回5道 MAIN，其中4道未来题；未证明跨用户泄露，也未发现该样本泄露参考答案或评分点。
- **修复/权衡 [S]**：`InterviewService.getDetail` 返回当前题和已有提交的 ANSWERED 历史；先校验所有者，继续使用独立 VO，不改保存快照。U2读回历史反馈仍成立；仅隐藏组件不足以保护 API。版本 V09。
- **验证/失败 [H]**：改前1项预期安全断言失败；定向179/0/0/0，U2前端61/0/0/0。全量首次1850/8/0/19源于临时 MySQL UTC 与应用时区不同，修正环境后相关41/0/0/0、最终1850/0/0/19；前端346/0/0/0。未降低断言。S1-R1 后续在 evaluation 安全窗口部署并经实际 HTTP/浏览器验证：起始仅当前1题可见，答后历史+当前可见，未来题不返回；权限401/403/404保留。
- **证据/边界**：[S1](../verification/S1_INTERVIEW_VISIBILITY_SECURITY_20261114.md)、[S1-R1](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-S1-R1-题目接口安全修复部署与终验.md)。运行JAR身份见V09；HTTP与受保护数据摘要才是历史实证，不把提交本身当部署。当前服务本轮未检查；评分质量仍NO-GO，安全局部门禁GO不代表全系统GO。
- **故事/学习**：可讲“持久化完整计划与客户端最小可见集是两条边界，修复须兼容上一题恢复”；读 `getDetail`、响应 VO、`InterviewVisibilityHttpIntegrationTest` 与U2组件，L-S1/L-S1R1 待确认。

### CD-10｜Q1：盲审、技术反例与 7/12 的正确解读

- **对话报告 [C]**：人工盲审12卡，07/06/09/01争议；12卡工程复核7卡至少一项模型输出问题，评分质量NO-GO。
- **已复核 [R/H]**：`UNBLIND_DATA.json`有12卡；按历史问题类别复算问题卡为 **01、03、04、06、07、08、11，共7卡**，类别可重叠；原盲卡备份 SHA 与登记一致。样本由风险分层挑选，**7/12不是总体错误率，也不能反推准确率5/12**。用户最初总体评语不是逐点评分 gold，工程审查者也不等于独立人工最终裁决。
- **争议与结论**：06主ON方案正确，额外WHERE OR变体有仅未支付订单时丢用户反例；不能将整答都判错，也不能因主方案好而认可变体。07一致性已表达却被误列遗漏，但隔离性绝对化另有风险。01未写正式名称但表达四项含义；后续用户认可概念理解、名称缺失按部分覆盖处理。09用户后续认可转账共同成功表达事务用途，同时要求分别指出C/I/D定义错误。04接口private版本被模型错误纠正；08的独立removeIf是可选正确方案；03已答风险被文字误列遗漏、未答NESTED仍应保留。以上技术结论为历史复核 [H]，不是本轮重新执行SQL/javac或重新评分。
- **可执行反例 [H]**：原MySQL字面量只读SQL的ON结果`1,2`、WHERE OR结果`2`；Temurin21的`javac --release 8`拒绝接口private、`--release 9`通过。知识正文已有相关依据，不能无证据把错误归罪RAG；原答中正确内容被遗漏，属于语义审查问题，Java求和正确不能治愈。
- **证据/失败**：[争议清单](../evaluation/q1-r2-20261114/CONFIRMED_ISSUES_AND_DISAGREEMENTS.md)、[解盲数据](../evaluation/q1-r2-20261114/UNBLIND_DATA.json)、[后续用户技术复核](../evaluation/q2-20261114/USER_TECHNICAL_REVIEW.md)、[SQL反例](../evaluation/q1-20261114/LEFT_JOIN_COUNTEREXAMPLE.sql)、[Q1-R1事实](../evaluation/q1-r1-20261114/analysis/CONFIRMED_FACTS.md)。历史盲卡核对脚本曾有换行/摘要断言错误；Q1-R2读取解盲索引早于备份的过程偏差已保留，不宣称双盲金标准。本轮不复制用户原答或原始私有材料。
- **状态/故事/学习**：没有修复模型错误，版本V10，质量NO-GO。可讲“用SQL反例和版本探针挑战模型与人的初评，保留分歧、抽样偏差和不可归因处”；读 `LlmEvaluationValidator`、`EvaluationScoreCalculator`、`FollowUpPolicy`，L-Q1/R1/R2 待确认。

### CD-11｜Q2 v5：提示词合同通过，真实输出仍退化，撤回候选

- **对话报告 [C]**：单次18个旧样本调用，17通过解析和Java校验，1个HTTP200但JSON语法无效，回退v4。
- **核证 [R/S]**：`COMPARISON.json`有18项，17 `VALIDATED`、1 `ERROR`；18次HTTP200和usage，输入63577、输出10523 tokens。当前读取的 `EvaluationPromptBuilder.PROMPT_VERSION`为`evaluation-prompt-v4`，v5只保留候选patch；没有v5修复后提交SHA，版本V10。
- **方案与失败 [H]**：v5尝试明确名称/含义、复合子项、等价方案及额外事实错误。A04事务用途单次改善，A02一致性退化，M05 removeIf继续漏认；H02有证据臂JSON在第1行第1319列缺冒号。HTTP200、finish_reason=stop、输出702/4096 token不足以认定截断，不能用降低校验、修补JSON或重试抹掉失败。
- **测试边界 [H]**：修改前43/0/0/0、候选44/0/0/0、恢复后43/0/0/0；扩展首轮87/0/20/0为Mockito附加错误，正确环境重跑87/0/0/0。18次是14个已揭盲旧样本加4组证据配对、每臂一次，不是18个独立新题或留出验收。未跑全量Maven或完整新业务面试。
- **证据与结果**：[Q2诊断](../evaluation/q2-20261114/DIAGNOSTIC.md)、[逐项对照](../evaluation/q2-20261114/COMPARISON.json)、[保留候选](../evaluation/q2-20261114/CANDIDATE_V5.patch)。原请求/响应/usage只由报告的受限索引定位，本轮未复制。历史估费¥0.08208628未核账单，不作为现在价格。本轮新调用0，评分质量仍NO-GO。
- **故事/学习**：可讲“提示词单元测试只证明合同，旧样本实测出现新退化时撤回候选并保留证据”；读PromptBuilder、DeepSeek解析器、validator和Q2对照，L-Q2-01～02待确认。

### CD-12｜Q2契约与552：布尔覆盖不表达PARTIAL，产品口径与模型效果分开

- **对话报告 [C]**：covered仅bool、无PARTIAL；证据非空不校验语义；552按“至少一种风险+机制+相应限制”的OR口径，七条候选仅离线，DNS阻塞无有效对照。
- **源码核证 [S]**：`LlmEvaluationSuggestion.ScoringPointResult`使用Boolean covered；validator拒绝null，仅在covered=true时要求evidence非空，并核对评分点ID与集合。**不是所有false也要求非空证据，更没有核验证据是否来自原答、是否技术正确、是否与missingPoints冲突。** 五维仍由模型建议、Java求和，布尔覆盖不是总分直接公式。
- **产品决策 [C/D/H]**：552原文连接词有歧义；用户明确至少解释一种相关实际风险，必须有合理机制并保留相应限制。“有风险”不足以覆盖，不要求列齐全部示例。该OR是出题人的产品裁决，不是框架文档规定的评分法；H01是否满足机制+限制仍需逐项审核，不能自动宣布整点covered。
- **方案/失败 [H]**：先保留v4和二值JSON，七条文字候选涉及ACID五点、removeIf一点、552一点；未上线、未更新题库或历史快照。离线9/0/0/0和客户端98/0/0/0不证明模型质量。旧候选计划40项，实际只有第一条旧规则1次尝试，0已记录HTTP响应、0 usage、0有效结果，后39项未执行；后来DNS errno8、无凭据HEAD退出6/HTTP000只证明当时执行环境预检失败，不能倒推首请求已送达/未送达、供应商故障或零费用。
- **证据/成本边界**：[Q2-R1报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-Q2-R1-评分契约审计与离线验证.md)、[Q2-R2总结](../evaluation/q2-20261114/q2-r2/COMPARISON_SUMMARY.md)、[七条旧候选](../evaluation/q2-20261114/q2-r2/CANDIDATE_RULES.json)、[552 OR候选](../evaluation/q2-20261114/q2-r2/CANDIDATE_552_OR.json)。¥0.071730只是未知调用预算预留，非实际账单；新OR候选未建立已核验可执行对照计划。版本V10，无修复后SHA。
- **权衡/故事/学习**：改变成三态会影响历史JSON、追问和回放；正式Service整题替换会重建评分点ID，正文又参与RAG查询，不能只改文字就宣称无行为变化。可讲“先明确评分合同，再决定二值兼容或粒度升级；无有效对照就保留未知”；读validator、`FollowUpTargetResolver`、`QuestionAdminService.updateQuestion`、`EvaluationRagQueryBuilder`，L-Q2R1/R2待确认。

### CD-13｜M1-A：单表Outbox与RocketMQ单体方案

- **对话报告 [C]**：单表Outbox+RocketMQ，DDL需批准，真实MQ最小收发可用。
- **设计/依据 [H/D]**：Session状态和待派发任务同MySQL事务，任务表兼Outbox；普通发送避免业务双写窗口，执行CAS/version fencing保护最终提交。排队期限、执行期限、派发租约分别表达；消息只含稳定ID，不带题答正文。对比事务消息/回查和数据库Worker，选择保持单体、单Topic、单消费组、一表，仍承担Broker、扫描、监控和运维成本。
- **验证 [R/H]**：本轮核到M1-A `spike-run.log`的`SEND_OK`和`SPIKE_RECEIVED=true`；历史探针为Java21+Boot3.5.16+Starter2.3.6+RocketMQ5.3.2，1次发/收，非业务事务或故障验收。M1-B最终选官方client显式生命周期，**不能把A的Starter探针写成B采用Starter**。
- **权限/版本**：[M1-A方案](2026-11-14-M1A-ROCKETMQ-ASYNC-REPORT-DESIGN.md)第8节是当时待批准DDL，后续批准范围见[M1-B准入](2026-11-14-M1B-DATABASE-ADMISSION.md)，仅限其明确目标，不扩展本轮。本次不执行DDL。原始/后续均V10，方案本身无业务修复SHA。
- **原始日志/残余**：`/private/tmp/ai-interview-m1a-swJBsI/spike-run.log`和`dependency-tree.log`；临时材料可能被清理。旧javassist effective-model告警未因最小收发成功而消失。未证明生产高可用、消息零丢失、外部模型恰好一次或生成加速。
- **故事/学习**：可讲“把数据库提交权和消息投递分开证明，先用最小探针排除基本兼容问题，再做业务验收”；读任务Mapper、Dispatcher、Consumer及设计故障窗口，L-M1A-01～04待确认。

### CD-14｜M1-B：受理、派发、执行和最终提交分离

- **对话报告 [C]**：代码53文件，1900通过/0失败/0错误/23跳过，前端341通过，真实MQ桩验证，尚未上线。
- **核证/校正 [R/S/U]**：最终原始日志为 **1923/0/0/23**，实际通过 **1900**；前端341/0/0/0由验收报告支持[H]。**“53文件”目前仅保留为对话报告，缺精确边界清单，不能写成53个业务代码文件或个人手写量。** 当前Git diff已叠加M1-C/R2/并行任务，不能倒数当前文件数补证历史范围。
- **实现/取舍 [S]**：POST同事务Session CAS+Outbox，消费CAS取得固定executionVersion；模型在事务外，Report+Weakness+READY+Task SUCCEEDED同事务。ACK只在可确认结果后返回；投递租约token与执行version各管不同进度。消费可能先于`markSent`完成，迟到回执只改投递字段，不倒退业务终态。已有报告恢复不重复更新Weakness。
- **真实/替身边界 [H]**：真实MySQL第一轮45/0/0/0、MQ+MySQL2/0/0/0、Broker停机1/0/0/0；模型为替身。DLQ初轮2/0/2/0源于CLI文本格式误判，修正后2/0/0/0；真实Broker重试/DLQ，DB不可读是注入异常，不冒称真实停共享MySQL。DLQ也不会自动把业务QUEUED改成FAILED。
- **失败/负面结果**：旧同步契约编译19个引用错误不是JUnit19失败；前端旧契约56/23/0/0迁移后51/0/0/0。全量1920/2/1/22包括stub写法错误及多Spring上下文Hikari池耗尽MySQL151连接上限；只限制test池max=4/minIdle=0，未降低并发断言。新增回滚测试曾对MyBatis代理误用callRealMethod，修夹具后验证真实NOT NULL导致Session/Outbox一并回滚。
- **证据/版本/剩余**：[M1-B验收](2026-11-14-M1B-ROCKETMQ-ASYNC-REPORT-VERIFICATION.md)、[运行手册](../../tools/m1b/README.md)；本轮复核`/private/tmp/m1b-full-maven-final-2.log`最后总计，其他日志按验收§4原名定位。版本V10，未提交实现、未正式上线；初期仅已批准test库建一表，不能扩大为local/evaluation已迁移。未证明真实ACK网络丢包、生产HA/长期积压/零丢失/模型exactly once。
- **故事/学习**：可讲“至少一次消息下，用两个身份令牌控制投递进度与业务提交权；提前完成的消费者反过来暴露迟到回执竞态”；读 `ReportOutboxDispatcher`、`ReportMessageConsumer`、`ReportMqRuntime`、任务SQL/事务，L-M1B-01～05待确认。

### CD-15｜M1-C：先鉴权后锁行，第一次修复又被MyBatis缓存击穿

- **对话报告 [C]**：跨用户锁等待；归属预查先于锁+锁后复查；第一次修复受一级缓存影响，独立Mapper后真实MySQL并发通过。
- **根因与反例 [S+H]**：原受理先锁Session后校验owner，无权请求也等待别人的行锁；首次改成锁前后同一完整查询，MyBatis事务内一级缓存可能复用旧owner。模拟等待期间归属变化后旧owner仍被接收。不是已证明实际跨用户泄露，也不假设产品有会话转让接口。
- **最终修复/边界 [S]**：普通`getReportOwnerId`预查→`getReportVersionForUpdate`锁行→不同MappedStatement的`getReportSession`→再次检查owner；当前事务READ_COMMITTED且入口无外层业务事务。不同缓存key与正确隔离级别共同构成依据，不能说`useCache=false`关闭一级缓存，也不全局禁缓存或降低鉴权/FINAL检查。版本V11，无独立修复提交SHA。
- **历史验证 [H]**：锁等待失败1/0/1/0；首次缓存修复失败1/1/0/0；最终相关定向75/0/0/0，全量1938/0/0/34，旧执行者四种交错4/0/0/0。真实MySQL并发测试与源码匹配；本轮没有重新执行这些测试。
- **原始定位**：[故障/修复记录](2026-11-15-M1C-FAILURE-INJECTION.md)、[R1缓存解释](M1C_R1_BOTTLENECK_REVIEW.md)、[原始文件索引](m1c-20261114/FILE_INDEX.md)，`ReportOwnershipLockIsolationTest`保留可执行反例。早期、首次修复和最终构建不混写，JAR见V11。
- **故事/学习**：可讲“第一版修复逻辑正确却仍读旧对象，最终从事务隔离和ORM缓存两个层次解释并复现”；读两个Mapper查询、`lockSession/acceptReportTask`、真实并发测试，L-M1C-04与L-M1C-R1-03待确认。

### CD-16｜M1-C：模型返回后崩溃与无依据报告内容

- **对话报告 [C]**：模型后消费者崩溃需用户显式重试；不保证外部调用exactly once；报告生成无依据弱项，最终实模型未验收，整体NO-GO。
- **恢复事实 [H/S]**：最终源码五组进程场景5/5满足各自断言，场景数不是JUnit通过数；只有相应恢复阶段启动最终JAR，其余有Surefire进程和替身窗口。提交后ACK前退出，真实Broker重投可不再调用模型替身或重复写报告/Weakness；这不等同抓包证明网络ACK丢失。模型返回、报告提交前退出则结果未持久化，RUNNING到期限后FAILED，再由用户显式请求新任务；替身调用共2次，最终报告/Weakness各1。
- **权衡/未完成**：版本fencing控制写入资格，不能控制外部服务只算一次。选择显式重试以避免无限重复付费；用户是否独立验收该恢复产品契约仍待补。历史测试缩短期限并显式触发`expireTask`，不是自然600秒调度实测；DB/扫描器停机也无法按墙钟保证失败收口。
- **内容证据 [H]**：早期两次真实DeepSeek输入的合成FINAL中strengths/missingPoints为空、correction=null，报告却给出具体技术欠缺。结构、分数、持久化和浏览器成立不等于文本有依据；两次使用较早B JAR，不能认证最终缓存修复JAR的模型质量。版本V11，内容问题未修复。
- **证据/日志**：[故障记录](2026-11-15-M1C-FAILURE-INJECTION.md)、`m1c-20261114/faults-initial.jsonl`、`faults-after-owner-fix.jsonl`、`faults-after-cache-fix.jsonl`及原报告里的`real-report-evidence.json`定位。本轮仅引用历史合成样本结论，不复制模型全文。MQ重投、DB事务、人工恢复、内容质量分开判；整体与内容质量保持NO-GO。
- **故事/学习**：可讲“把不重复业务提交与不重复付费调用分开承诺，并坦诚说明模型后崩溃窗口”；读 `ReportMessageConsumer`、`generateClaimedReport`、`expireTask`、`RealReportCrashWindowProbeTest`和报告Generator，L-M1C-02/03、L-M1C-R1-02待确认。

### CD-17｜M1-C性能：HTTP快速受理，完整READY和闭环吞吐退化

- **对话报告 [C]**：1秒桩、并发10、每组30条；POST P95同步1046.5ms/异步24.9ms，READY P95同步1046.5/异步6967.8ms，吞吐7.252→1.489次/秒（−79.5%），浏览器1356.7→9470.0ms。
- **本轮复算 [R]**：从最终`requests.csv`排除预热，各组n=30，P95用nearest-rank；POST为1046.4795/24.9442ms，READY为1046.4825/6967.7927ms。闭环速率按每轮最后完成时刻减该轮开始、两轮时长求和，得7.251908/1.488577次/秒，下降79.4733%。按原协议四舍五入与对话数字一致。
- **口径/负面结果**：A POST已完成READY，B POST仅返回GENERATING；请求到首次GET观察READY含200ms轮询误差，不是DB提交时间。固定1秒模型桩、独立MySQL+真实MQ、本机小样本；无付费模型，不是生产QPS/SLA。原9组每组30条，540条测量+54预热；A/B背景库已有行数不同，用户消息Prompt哈希配对一致不代表整个HTTP请求体逐字一致。
- **浏览器 [R/H]**：另一次真实Vue/无头Chrome实验、每组n=30、实际3秒轮询；本轮CSV复算1356.6934/9470.0498ms，点击指令到摘要可见，含自动化调度，不代表物理屏幕绘制。HTTP与浏览器不同时运行，不用两者P95相减分摊组件。
- **归因级别**：完整变慢为[R]事实；当时未直接测到各阶段，不能归因“MQ独立造成全部延迟”。历史较早B的1086.4/22.1、READY7089.4、吞吐7.206/1.476另存，不替换最终样本。版本V11。
- **证据/故事/学习**：[最终A/B](m1c-20261115-after-fix/AB_RESULTS.md)、[请求CSV](m1c-20261115-after-fix/requests.csv)、[浏览器](m1c-20261115-browser/BROWSER_RESULTS.md)、[浏览器CSV](m1c-20261115-browser/browser-requests.csv)、`tools/m1c/summarize.py`。可讲“受理延迟看似大幅变好，却用统一完成语义发现反向结果”；L-M1C-01、L-M1C-R1-01待确认。

### CD-18｜M1-C-R1：从区间重叠推断并发，不把配置上限当实测

- **对话报告 [C]**：同步峰值推断10、异步执行线程2；单并发入队到领取P50=874.1ms，扫描2秒。
- **证据级别 [H/S]**：R1依据既有请求/任务区间保守重叠估计A=10、B=2，不是运行时直接计数器。B为一个实例的两条消费执行线程，不是两个实例；允许上限8不代表实际8。并发1时均只有一个执行，B queued→started P50=874.115ms，说明还有固定调度等待。
- **选择依据与未知**：2秒fixedDelay与低负载空档相符，只能作为扫描贡献的假设；该区间还包含Broker、消费调度和领取。决定下一轮分别观测实际在途、事务afterCommit、发送/拉取/执行和观察延迟，做少量单变量对照；不通过删FINAL、幂等或鉴权提速。
- **状态/负面边界**：本轮R1当时只复核1188条历史请求，没有新增模型/压测/业务补丁，原始/后续版本仍V11；未给“根因修复SHA”。137退出且OOMKilled=false只是历史事实，缺信号链不能认定OOM、优雅停止或已修复。
- **证据与学习**：[R1报告](M1C_R1_BOTTLENECK_REVIEW.md)、M1-C最终CSV与原报告中`all-requests-timeline.csv`/`paired-task-timeline.csv`定位；本次重新确认HTTP594+浏览器594=1188行。故事可讲“并发瓶颈只是一个假设，用低并发反例证明还需研究调度成本”；读Consumer执行模式、Dispatcher周期、R1分析及原始区间，L-M1C-R1-01～03待确认。

### CD-19｜M1-C-R2：多瓶颈实测后的两项最小调参

- **对话报告 [C]**：执行线程2→4，扫描2000→500ms；闭环1.775→3.531次/秒，READY P95 6067→3564ms；全量1938/0/0/34、定向131/0/0/0、真实MQ2/0/0/0；仍比同步慢，生产可靠性和内容质量NO-GO。
- **实证归因 [R/H]**：测试埋点用同JVM单调计时、事务afterCommit和直接模型在途计数；2/4/8线程峰值确为2/4/8。原2线程/2秒、n=20条件下阶段均值：执行线程等待2313.3ms、提交后等派发1069.7ms、模型1005.5ms；发送确认仅毫秒级，不能把秒级差距都归给Broker。单并发扫描2秒→500ms，派发等待P50由834.2→355.1ms[H]。阶段采用逐任务配对均值，不相减不同实验P95。
- **实现 [S]**：只调整`ReportAsyncProperties`默认值、Dispatcher注解fallback及application.yaml默认4/500；上限8保留。补丁见V12，未更改ACK、Outbox、FINAL、所有权、fencing；8/500只是已测候选，不是R2默认。
- **复算 [R]**：相同1秒桩、客户端并发10、每组20条闭环，旧/新客户端速率 **1.775236→3.531387次/秒**，READY P95 **6067.3813→3563.5962ms**；数据库完成速率历史汇总 **1.800→3.641次/秒**。本轮重新用原始归档请求起止和224条时间线核对14组统计。同步A同组仍约9.631次/秒、READY P95约1041.2ms，不能写“MQ比同步快”。该20条协议不能与旧30条/两波协议混算。
- **验证/失败 [R/H]**：历史套件汇总逐类相加和总计一致，131/0/0/0、1938/0/0/34、2/0/0/0；没有把套件行与末尾总计重复相加。240条测量=224详细HTTP+4冒烟+12浏览器；34次预热含33成功/1探针500，模型桩273次、付费0次由归档计数支持。首次探针参数错误、浏览器覆盖未匹配Vite的3e3、清理stdin超时均保留，脚本exit1不写通过。
- **代价与残余**：两个扫描SQL的理论频率由约2→8条/秒、同时外部模型请求增加，不宣称零资源代价。历史3秒/测试1秒轮询各6条，观察中位等待下降但GET增多；R2正式前端仍3秒。本次工作树出现1秒见CD-20，不归入R2。没有重做全崩溃矩阵、长时间积压或最终真实模型质量。
- **证据/结论**：[R2索引](m1c-r2-20261115/README.md)、[summary](m1c-r2-20261115/results/summary.json)、[timeline](m1c-r2-20261115/results/task-timeline.csv)、[归档](m1c-r2-20261115/raw-events.jsonl.gz)、[测试](m1c-r2-20261115/test-results.json)、[正式报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-M1-C-R2-RocketMQ多瓶颈归因与最小调优.md)。本批覆盖内业务正确性GO；生产级MQ完整可靠性NO-GO；评分/报告内容质量NO-GO。可有条件保留作持久异步与可靠性工程展示，数据库任务+有界Worker是待评估替代，未实施替换。故事讲“用多阶段证据选择两个小改动，承认仍不如同步”；L-M1C-R2-01～04待确认。

### CD-20｜M1-D对话决策：第二次报告模型调用是否值得

- **用户判断 [C/D]**：同步本身也慢，不能只盯MQ线程；逐题评价和最终报告职责不同，减少一次调用需考虑如何综合，尽量少改代码，其他调度/轮询方案也值得研究。已取得原消息，见§1.1。历史助手提出的建议仍是方案，不是已实现事实。
- **源码事实 [S，04:07前后读取快照]**：逐题模型负责评分建议、覆盖、反馈及候选追问；有追问时再生成该MAIN的综合FINAL。`DeepSeekInterviewReportGenerator.generate`另外调用`completeJson()`，`InterviewReportPromptBuilder`输入题目快照、FINAL五维/总分/等级、strengths/missingPoints/correction；不重新把所有原始答案送入最终报告。Java控制均分/评级/事务，报告模型负责文本综合。
- **候选A [D/U]**：保留现有完整模型报告作基线，精简Prompt和输出作对照。潜在收益是减少token和等待；不能靠盲目压maxTokens、允许不合法JSON或删除事实依据换速度。当前Prompt已禁止虚构，但结构校验无法验证来源，这项风险要单独测试。
- **候选B [D/U]**：基于既有FINAL确定性Java汇总，报告阶段0次额外模型调用；按来源去重、排序，所有具体弱项追溯到原评价。优势是可控，代价是跨题综合与自然语言质量可能不足，且不能纠正逐题模型原有错误。现有报告列表要求1～5项，对空证据如何合法表达“无足够依据”需设计，不能为凑项造弱点。若接入应使用报告专用选择配置，不通过全局关闭DeepSeek连逐题评分一同关闭。
- **其他候选 [D/U]**：提交后即时唤醒+定时Outbox兜底；线程4/8小对照；轮询缩短/自适应并记录额外GET成本。基础报告立即可见、AI分析稍后补充涉及生命周期/API兼容，暂留设计。若Java汇总已经很轻，应重新评估MQ必要性；不同时放开两条可重复提交的正式路径，不取消fencing。
- **状态与证据（首轮读取时点）**：本轮看到`docs/engineering/m1d-20261115/`和`tools/m1d/`实验材料；前端读取值1000ms，且M1-D interim持续变化。**当时只证明有实验/工作区变化，不证明M1-D已完成、已部署或已验收。** 原始基线V10/V12；本批不评审其最终指标、不接受新实现，最终构建/真实对照/内容审核须另行核对并行材料。源码可能在本记录完成后变化，应按封存检查重新定位。
- **04:18:46并行收口旁注 [H/S]**：四份长期记录此时已被其他任务追加M1-D段落，[M1-D新索引](m1d-20261115/README.md)与[生产增量](m1d-20261115/production-changes.patch)已出现。本轮核到patch仅记录前端轮询3000→1000ms，与当前源码一致；新索引报告完整DeepSeek仍默认、精简/规则方案未替代、未提交/部署。其8次模型对照和新测试等属于**其他任务的验收陈述，本批未独立复算或认定完成**，不加入本稿简历成果。本稿保留原讨论时点，合并者应读取该新增材料，不将旧“研究中”误当整个项目永远未推进。此次记录专项本身仍为0次模型调用。
- **面试用途/学习**：只能讲“正在研究二次调用的价值、方案和验收方法”，不能写已节约调用/费用/时延。读Generator接口、DeepSeek实现、PromptBuilder、FinalReader与Report事务；新增学习补充L-CD-07/08，全部掌握未确认。

## 4. 简历素材候选与贡献边界

以下是**供本人理解、核对实际贡献后选用**的候选，不是已经核实的个人履历。统一前提：个人项目、AI辅助实现；用户的明确贡献目前可核证为提出多因素分析/最小改动约束、审查技术争议及确定552评分口径。本人独立编码、独立故障复现、源码掌握和生产运营经历没有充分证据，不能把Git作者或AI产出等同手写。

1. **RE-CD-01｜并发与事务。**“在AI协作下参与模拟面试后端并发恢复治理，针对提交成功后的幂等重放误报、领取后异常未收尾和FINAL来源不一致，结合CAS/attempt隔离与真实MySQL受控交错补齐回归证据。”对应CD-01～03；面试须能亲自解释一条完整交错与回滚路径。不可写消灭全部竞态/外部调用恰好一次。
2. **RE-CD-02｜模型边界与日志。**“参与DeepSeek/Embedding异常安全边界和Milvus调用策略审查，区分HTTP响应、异常链与最终日志泄露，保留固定诊断字段并验证失败恢复。”对应CD-04/05；可用已核证历史定向数，不能声称整个系统所有日志/APM绝无敏感信息。
3. **RE-CD-03｜题库、RAG与安全。**“基于63题/298评分点冻结题库实现分类内随机选择与历史快照保护，并修复详情接口提前返回未来题的问题，保留真实MySQL及HTTP权限证据。”对应CD-07/09；需加AI辅助实现说明，不能称服务了生产用户或难度/评分质量已达标。RAG的23.81%等负面相关率宜用于面试复盘，不包装为精准检索成果。
4. **RE-CD-04｜异步受理的反例。**“参与MySQL单表Outbox与RocketMQ异步报告原型；本机1秒模型桩、并发10、每组30条下，POST P95从同步完成1046.5ms变为异步受理24.9ms；完整READY P95却升至6967.8ms，闭环完成速率7.252降至1.489次/秒，推动后续归因。”对应CD-13～18。**限定负载、不同返回语义、完整退化必须一起出现**；版面不足时删数字，不能只保留24.9ms宣传加速。
5. **RE-CD-05｜小改动与有限收益。**“在AI协作下为异步链路补充阶段计时和实际在途观测，定位消费排队与Outbox等待；在同一20条、并发10、1秒桩闭环下，将2线程/2秒改为4线程/500ms，完成速率1.775升至3.531次/秒、READY P95 6067.4降至3563.6ms，仍慢于同步。”对应CD-19。不能写生产吞吐翻倍或MQ提升系统QPS；未上线，生产可靠性和内容质量仍NO-GO。
6. **RE-CD-06｜模型质量审查。**“组织风险样本盲审和技术反例核查，在12卡中记录7卡至少一项输出问题，识别错误认可、等价方案漏认与版本事实错误；18次旧样本Prompt候选诊断出现1次非法JSON及语义退化后撤回候选，保留v4。”对应CD-10～12。“组织”需本人确认实际参与范围；若无法确认，改为“参与审查”。7/12只描述风险样本，不是总体准确率，v5没有修复成功。

**禁止表述**：MQ加速生成、已上线/真实生产用户/生产QPS、生产高可用、零丢消息、外部模型exactly once、消费者崩溃总能自动READY、JSON通过就评分准确、7/12代表总体错误率、M1-D已节省一次调用、全部代码本人手写、已独立掌握。

## 5. 源码学习补充（不代替已有清单，不勾选完成）

所有新增ID使用L-CD命名空间，避免抢占M1-D编号。共同状态：**待学习（未确认）**；开发者掌握证据：未提供。验收可以先口述/画调用链/离线推演，本记录不要求立即调用模型或操作数据库。

### L-CD-01｜区分四种证据与历史运行版本

- 关联：CD-01～20、V01～V12；前置L-X-01/02。
- 入口：各报告、Git父提交、`tools/m1c/summarize.py`、`tools/m1c-r2/analyze.py`。
- 目标：本人解释“历史XML复算”“测试重跑”“源码当前可见”“运行JAR已部署”的不同；独立复算一个T/F/E/S和一个nearest-rank P95，说明为何跳过不能叫通过。
- 待确认：临时日志耐久性、旧运行脏树与后来拆分commit的区别；状态升级须附本人材料和日期。

### L-CD-02｜完成重放、领取资格与失败回写

- 关联：CD-01/02，V01/V02；前置L-B1/L-B2。
- 入口：`InterviewWorkflowService.evaluateAnswer/markEvaluationFailed`、`InterviewWorkflowTransactionService`、`InterviewAnswerMapper.xml`。
- 目标：独立推演旧快照误报500，解释为什么try起点应在OWNED后、为什么REQUIRED此时形成短事务、为什么suppressed不能替代原异常。
- 待确认：claim提交结果未知与数据库长期不可用时的边界；不以背诵术语代替逐行调用链。

### L-CD-03｜FINAL完整性和历史恢复

- 关联：CD-03/14，V03/V10；前置L-B3。
- 入口：`AnswerEvaluationMapper.listFinalEffectiveEvaluationsBySessionId`、`InterviewReportFinalReader.aggregateFinalEvaluations`、`completeReportGeneration`、`UserWeaknessService`。
- 目标：用“重复一个MAIN且遗漏另一个MAIN”的反例说明数量不够；解释INITIAL不入均分、READY历史兼容、Report/Weakness/Task事务回滚。
- 待确认：历史弱项审计与绕过应用直接改库不在现有保证内。

### L-CD-04｜日志安全与可取消调用的边界

- 关联：CD-04/05，V04/V05；前置L-B4B/C/D、L-B5P2B。
- 入口：DeepSeek Client、EmbeddingOpenAiApi、SpringAiEmbeddingClient、MilvusConfiguration与日志/超时专项测试。
- 目标：解释为什么固定HTTP错误仍可能泄露、为何不保留上游cause；区分一次应用尝试、SDK尝试、RPC、业务显式重试及服务商实际计算。
- 待确认：取消不保证撤销写入/计费，补偿需限定attempt身份。

### L-CD-05｜缓存、事务隔离和锁后复查

- 关联：CD-15，V11；前置L-M1C-R1-03。
- 入口：`acceptReportTask/lockSession`、SessionMapper三条查询、`ReportOwnershipLockIsolationTest`。
- 目标：本人画出第一次修复失败时序，解释MappedStatement ID、一级缓存和READ_COMMITTED各自解决哪一层；说明`useCache=false`不是一级缓存总开关。
- 待确认：真实生产无会话转让接口不妨碍用受控归属变化证明复查语义，但不能据此宣布已发生数据泄露。

### L-CD-06｜抽样、评分合同与产品裁决

- 关联：CD-10～12，V10；前置L-Q1/R1/R2、L-Q2R1/R2。
- 入口：LlmEvaluationSuggestion、validator、score calculator、FollowUpPolicy、552候选与SQL反例。
- 目标：本人说明7/12为何不是总体错误率、bool覆盖为何无法区分部分/完全未答；给出满足“至少一种风险+机制+限制”的正反例，并解释后续用户裁决如何更新但不篡改初评。
- 待确认：未见独立人工gold、用户口径不自动证明模型能执行。

### L-CD-07｜受理、DB READY、客户端和浏览器时间

- 关联：CD-17～20，V11/V12；前置L-M1C-01、L-M1C-R2-01～04。
- 入口：Dispatcher、ReportMqRuntime、Consumer、前端poll interval、原始CSV及R2测试探针。
- 目标：本人独立复算7.252→1.489和1.775→3.531，解释闭环/固定到达率、两波/单批、P95不能相减归因、推断峰值与直接在途计数的差别。
- 待确认：旧R2前端3秒与并行工作区1秒的版本边界，不能把后来改动记为早期成果。

### L-CD-08｜逐题FINAL与综合报告的职责

- 关联：CD-20，V10/V12；前置L-B3、L-Q2、L-M1B。
- 入口：`InterviewReportGenerator.generate`、DeepSeek实现、ReportPromptBuilder、FinalReader、完成事务。
- 目标：本人列出Java已确定事实与模型额外价值，设计保留模型/精简Prompt/规则聚合的相同输入对照；解释规则汇总如何防止制造新弱项及如何表达证据不足。
- 待确认：M1-D最终质量/耗时/成本证据、专用配置唯一装配、历史JSON兼容和无需MQ的可行性；当前都是待验收内容。

## 6. 四份长期记录的安全合并清单

本轮只新增本草稿，**没有直接更新四份长期记录**。后续安全合并应以当时最新文件为准，按稳定ID追加/补记，逐条去重；不复制本轮旧文件覆盖新文件，不把整段重复加到四份文档，不改历史失败数字。合并不等于业务发布，也不需要随手提交Git。

1. **ENGINEERING_CASEBOOK.md**：建议新增CD-01～12的案例提炼；已有Q2-R2和M1-A/B/C/R1/R2不得重复建案。CD-13～19作为已有条目的证据/口径补充，只补缺失的推理、原始失败和版本关联；CD-20留“讨论/研究中”，若M1-D另有最终验收，引用新报告并保留本时点状态。
2. **RESUME_EVIDENCE_BANK.md**：新增RE-CD-01/02/03/06；RE-CD-04/05与已有M1-C/R2候选去重合并，保留退化和负载条件。用户贡献与AI辅助实现声明作为共同前提，未确认个人掌握不升级。
3. **IMPROVEMENT_LEDGER.md**：保留所有历史批次，不再新增20个“实现批次”。增加一条“CD-20261115仅记录核证”入口，补M1-B总数/通过口径、53文件待证、Q1用户后续裁决、Q2/R2无模型有效对照、Git/构建身份与R2/并行前端差异；M1-D归讨论，不能宣布GO。
4. **SOURCE_LEARNING_BACKLOG.md**：现有各批ID继续保留；L-CD-01～08仅补跨批主题，能直接在现有项补充的优先补充，避免重复计数。所有条目仍待学习/待确认，不自动改已有累计数或勾选掌握。

**建议合并顺序**：等待并行任务形成稳定交付→重新读取四文件/状态→按上述条目与最新M1-D记录逐项去重→文档链接/敏感信息/口径检查→保留本草稿作来源索引。不要使用reset/stash/checkout覆盖或为文档合并执行DDL、模型调用、压测。

## 7. 待补证据与未验收事项

- **G01｜M1-B“53文件”**：[C/U]缺当时独立作用域清单及文件分类；需要逐文件路径、原始/最终hash、是否含测试/文档/工具，不能用当前累计diff倒推。
- **G02｜更早完整对话**：[C/U]11月11—14日相关聊天原文未全部返回；现用用户摘要+本地档案。若补原文，仅追加来源/异议，不替换已保存的用户初评。
- **G03｜个人掌握与贡献**：[U]未有本人独立复现、解释或改动材料；简历6条都是候选，不能由AI完成或Git作者推断手写/熟练。
- **G04｜M1-D最终交付**：[U]需另行核对稳定源码/构建、最终参数、相同FINAL对照、独立内容审核、实际调用/usage/成本、历史兼容、回归及发布范围。04:18新增索引已有相关陈述，见CD-20旁注，但本批未独立验收；目录和1秒轮询不能单独证明完整成果，本专项不接手该实验。
- **G05｜生产级MQ可靠性**：[U]实际JDBC提交未知、共享DB真实故障、长期积压、多实例重平衡、持久化损坏、优雅停机/恢复和生产HA不足。不能把指定崩溃探针扩成完整生产保证。
- **G06｜模型后崩溃产品契约**：[U]显式重试实现/历史探针有证据，用户独立接受该产品体验、自然期限扫描与最终真实模型崩溃窗口不足；不自动改为付费重试。
- **G07｜评分与报告质量**：[U]Q1/Q2风险样本非总体gold；独立未见人工留出与真实报告依据审核仍缺，v5撤回，OR候选未测。最终M1-C/R2模型质量未验收，NO-GO不变。
- **G08｜未知费用与失败原文**：[U]Q2-R2首请求无响应/usage/供应商请求ID，实际费用未知；较早E2E失败JSON没保存，不能重建当时正文。不得把预算预留称账单或把未核费用记零。
- **G09｜当前部署和远端**：[U]本轮只读本地Git/文件，未查实时远端、运行服务或实时数据库；历史evaluation部署不等于生产上线，Git提交不等于当前进程加载。
- **G10｜证据保管**：[U]原始临时目录可能被系统清理，部分其他批次仅复核报告而未读取全部原始材料。宜在未来单独授权下整理脱敏、最小可公开材料；本轮不迁移私有备份、不复制全日志或私人回答。

## 8. 本次交付与验证说明

本批新增20个CD案例、6个RE候选、8个L-CD学习补充、10个G待补证据项，均在本文件内；四份长期记录仅提供合并位置，没有覆盖修改。没有新建任何业务功能、改变评分契约、执行数据库SQL、调用收费模型、运行压测、操作容器或执行Git写命令。

本次实际执行的是：本地源码/提交/差异读取，23组历史XML计数、M1-B日志计数、M1-C两份CSV的1188行核对、R2原始请求及14组224行统计复算、28项证据hash与211项源码hash、Q1/Q2结构结果及冻结题库计数核对。业务测试、真实服务验收、浏览器测试与负载实验本轮均**未执行**，不得把历史通过数写成本轮测试通过。

交付后的版本判定以本文读取时点和独立核证结果为准。并行任务若更新已有文件，本轮只记录观察到的变化，不回退、不纳入本批贡献，也不将其视为本批破坏保护。最终交付索引另列本文件实际路径、检查结果及并行变化摘要。
