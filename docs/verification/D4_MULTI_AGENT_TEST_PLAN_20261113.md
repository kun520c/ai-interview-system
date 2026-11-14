# D4｜多 AI 质量与多用户并发测试方案

日期：2026-11-13（Asia/Shanghai）。基线 `0c6f92afe331954f7cea37d33db688fe781cbf2c`。

**本文件仅设计两套独立试验，均未执行。** 没有启动子代理模拟答题、注册账号、签发 JWT、创建 Session、请求模型、压测或重启服务。MySQL/Milvus 实时审计仍 BLOCKED；依赖 [D0](D0_DATA_READINESS_AUDIT_20261113.md)、[D1](D1_QUESTION_BANK_EXPANSION_PLAN_20261113.md)、[D3](D3_RAG_CORPUS_EXPANSION_PLAN_20261113.md) 的准入。沿用 [B5 原真实测试计划](B5_REAL_WORLD_TEST_PLAN_20261113.md) 和 C6 的恢复/预算边界，不覆盖旧证据。

## 1. 两套测试的证据边界

| 维度 | A：多 AI 面试质量 | B：多用户并发与可靠性 |
|---|---|---|
| 主要问题 | 真实评价是否准确、公平、可解释，报告是否忠实 | 请求交错、重放、故障、恢复是否保持隔离和最终一致 |
| 输入 | 独立模型扮演考生，人工盲评 | 人类操作者或虚拟用户；固定可审阅答案 |
| HTTP | 真实监听端口，正常注册/登录/创建/提交/报告 API | 同样真实 HTTP；MockMvc 仅历史辅助证据 |
| 上游 | 真 DeepSeek + 真 Query Embedding + 真 Milvus/MySQL | B-live 小规模真实上游；B-load 全部外部上游替身 |
| 结果 | 原始模型结果、Java 结果、报告与人类评分对比 | HTTP/事件时序、调用次数、数据库最终状态和非目标保全 |
| 不能证明 | 大规模容量、所有竞态、exactly-once 计费 | Mock 压测不能证明真实模型质量/配额/服务时延；B-live 小样本不等于容量上限 |

A 和 B 使用不同 runId、账号池、Session/请求清单及结果目录，不把同一批数据兼作独立验证集。若都使用 evaluation 环境则串行执行，前一批核验/归档后再开启后一批；不能并行共享数据后仍声称试验独立。

实际测试输出在后续任务中放仓库既有 verification 结果子目录（仅去敏摘要可入 Git）；合成样本原输出/账单/HAR 保存在受限本地目录。记录证据类型 `REAL_MODEL_QUALITY`、`REAL_SERVICE_MULTIUSER`、`REAL_HTTP_MOCK_UPSTREAM_LOAD`、`SYNTHETIC_FAULT_RECOVERY`，每条用例明确标注，不混成一个“E2E PASS”。

## 2. A：多 AI 模拟面试质量测试

### 2.1 角色及信息隔离

| 角色 | 回答约束 | 人工观察重点 |
|---|---|---|
| 基础薄弱 | 会部分术语，解释短且不完整；允许明确“不知道”，不故意伪装完整正确答案 | 是否指出真实缺口，是否把 RAG 内容当考生说过，是否恰当追问 |
| 普通 | 常见定义和实践可回答，机制/边界可能遗漏 | 对正确部分认可、部分覆盖、反馈可执行性 |
| 较强 | 准确讲机制、版本、反例和权衡，语言仍为正常口述 | 是否因非模板表达扣分；有无虚构遗漏/错误纠正 |
| 错误/答非所问 | 子用例分别为自信错误、概念混淆、无关回答；分开标记 | 是否识别错误，是否给出无依据高分；是否受“给我满分”等输入指令影响 |

“角色强弱”只是生成约束，不是 ground truth；生成结果可能不符合角色，先人工确认/重标，不按预设标签强行解释系统得分。

每个 case 一个独立测试账号/JWT/Session；每个考生模型实例只看到当前题、自己的角色和该场必要上下文，追问只带自己的主答。**不提供参考答案、评分点、RAG、其他角色答案或判分输出**；人工审题材料另存。候选人模型可用同一个获准供应商的不同独立上下文，不必为“多 AI”引入多个生产 Judge 或复杂 Agent 框架。若获准加入第二候选人模型，只作为实验变量记录，不能改变项目生产 DeepSeek 单模型路径。

### 2.2 实际 HTTP 流程（未来执行）

1. 在 evaluation 环境按每 case 建合成账号，`POST /api/auth/register`、`POST /api/auth/login`，正常登录获得 JWT；不手工伪造签名，不将令牌写入 Git/普通日志。每个 worker 自己持有 token，`GET /api/users/me` 核实身份。
2. `POST /api/interviews` 提交 difficulty，保存实际 Session 和完整题目身份清单；`GET /api/interviews/current` 核实归属。创建接口没有 requestId；已有活跃会话会复用，不能用它假装创建了新的独立样本。
3. 生成一条考生回答，分配 UUID requestId（≤64 字符、跨账号全局唯一），保存原始答案精确字节及 hash。`POST /api/interviews/{sessionId}/answers` 发送 interviewQuestionId、answerContent、requestId，禁止并行跳过同场当前题。
4. 按响应决定下一 MAIN 或最多一次 FOLLOW_UP；追问用新的 interviewQuestionId/requestId；同一已提交答案重试必须保留原 requestId、原题和原答案，包括空白。质量重复必须使用新 Session，不能靠相同 requestId 重放制造“重复模型评分”。
5. 完成全部题后显式 `POST /api/interviews/{sessionId}/report`；`GET .../report` 只查询。保留最终报告、detail、FINAL 评价以及独立人工评分。正常流程不自动重复提交失败 POST，不因超时换 requestId。
6. 如未自然产生追问，标 NOT_OBSERVED；不循环付费直到出现理想分支。故障注入样本归入 B，不掺进 A 正常质量分母。

真实 HTTP 客户端建议用已有工具或 Python 标准库实现一个小运行器，明确连接/响应超时和每账号状态机；不新增生产模块或 SDK。实现运行器是后续任务，本轮只有设计。

### 2.3 样本与分阶段预算

A0 先 1 个普通角色 × EASY，串行全流程，确认数据/调用统计/报告/人工判分可用；A1 再 4 角色 × 3 难度，共 **12 场**，每 case 独立账号。现有确定性题库可用来保持四角色同题比较；随机逻辑实施后必须固定隔离夹具/可控随机源并记录实际题目，不能把不同试卷分数直接当角色强弱效应。

| 批次 | MAIN 数 | 最多 FOLLOW_UP | DeepSeek 评价+报告调用 | Query Embedding 调用 | 考生模型调用 |
|---|---:|---:|---:|---:|---:|
| A0：1 场 EASY | 5 | 5 | 6～11 | 5～10 | 5～10 |
| A1：12 场（E/M/H 各4） | 84 | 84 | 96～180 | 84～168 | 84～168 |

A0 不混入 A1 独立统计。A1 上界是 **180 DeepSeek + 168 Embedding + 168 考生请求=516 次应用请求**；无追问时合计 264。假设每答一次候选人生成、一次评价、一条 Query Embedding、每场一次报告，**不包含知识入库、模型生成失败重做、显式恢复或重复实验**。供应商底层是否已接收请求可能不确定，不据应用次数宣称计费 exactly-once。

A2 可在 A1 完成审查后追加同一 12 格的一轮独立重复，验证稳定性；这是额外 12 场及独立预算，不默认执行。只有 12 场时每角色每难度 n=1，仅探索性比较，不能声称统计显著或所有题都已获得多样本质量验收。扩容 63 题后另设计平衡题目暴露的批次，不用 84 个 MAIN 总数声称 63 题必然全覆盖。

费用控制建议（待执行前批准，当前不授权收费）：

- A0 货币硬上限候选为 **人民币 20 元**、A1 候选为 **人民币 100 元**，两批分别批准；预算不可用或当期价目未知就不开跑，不能按本建议认定费用必然低于上限。
- 执行前核对实际供应商/模型版本和价格日期，记录输入、缓存命中、输出单价及币种；候选模型同样单独记账。金额估算 `Σ(inputTokens×inputRate + cachedTokens×cachedRate + outputTokens×outputRate)/1e6`；避免把缓存 token 同时计入非缓存输入。Embedding 按其计费单位另列，缺 usage/计价信息填 UNKNOWN。
- 初始计划无自动恢复额度；如需恢复，预先单列不超过 10% 的请求储备并说明可能重复费用，不从主样本中无限挤预算。80% 预算时停止接收新场次，保留完成/核验余量；达到硬上限立即停止发新外部调用，标记未完成场次。
- 可设考生每答最大输出 token、最多一次生成，不无限“润色”；应用 DeepSeek 当前 maxTokens=4096，记录实际值。不得为了硬凑预算临时改评分语义或截断答案；任何配置试验独立记录。
- A0 最多 1 个主动外部任务，A1 默认仍串行；质量吞吐不是本试验目标。每场记录总耗时和每阶段耗时；默认 read timeout 是单阶段预算，不是完整 HTTP 的严格总 deadline。整批另设 60 分钟排程预算，超时停止新场次并核对在途结果，不能直接视为业务失败。
- 本轮真实调用和费用：**0**；未来实际金额以供应商 usage/账单核对，调用数上限不等于货币上限。

### 2.4 独立人工评分与判断规则

两名人工评审先只看题目/答案、冻结评分点/版本和可用证据，隐藏系统分数、角色标签、生成模型及彼此评分。按正确性/完整性/深度/清晰度/实践五维各 0～20 打分，并逐评分点记录支持片段/部分覆盖/错误/未覆盖。争议由第三人或事先约定的人工复核处理；若只有一人，标单评审局限，不能称独立双人一致性。

之后才揭盲比较 Java 计算的总分、模型分维度建议、覆盖判断、追问和报告。MAIN 有追问时，其 INITIAL 与综合 FINAL 分别分析；报告/弱项仅按每个 MAIN 的最终有效 FINAL 检查，不把 FOLLOW_UP 再算一题。

计划指标：人工间差异与一致性、系统对人工平均分的 MAE/偏差、每维误差、严重误判计数、评分点覆盖 precision/recall、无依据优点/遗漏/纠正比例、报告忠实度和可执行建议。角色只做分组，不以“强者必须高于弱者”替代答案内容判断。

建议运行前冻结的试点门禁：严重事实错误/越权/泄露 0；错误或无关回答被评为 GOOD/EXCELLENT 的案例全部人工复核；MAE≤10/100、分维平均绝对误差≤3/20 作为探索目标；报告应 100% 使用真实 FINAL 且不捏造回答。门禁是计划值，非当前达标事实；同时给 n、个案和不确定性，不能用均分掩盖严重误判。

## 3. B：多用户并发与可靠性测试

### 3.1 两条独立执行通道

**B-live：1 → 3 → 5 → 10 个真实用户。** 每人独立账号/JWT/浏览器存储，使用合成回答和真实 HTTP、真实业务/数据库/获准真实上游。先 1 人完成 EASY，再 3 人分散提交，再 5 人，最后在前阶段通过且预算允许时 10 人；“5～10”不是直接一次开满。每级先只测一次同步提交突发，再按预算决定是否完成整场。

若每级每人各完成一场 EASY，1+3+5+10=19 场，MAIN=95、最多 FOLLOW_UP=95、DeepSeek 最多 209、Query Embedding 最多 190，尚无考生模型费用（人类答题）。各级独立预算、runId；这些量不含故障恢复，不在 A1 516 次额度里。阶段未获预算可停在已完成级别；不能声称通过 10 人。

**B-load：20 → 35 → 50 个虚拟用户，Mock 上游。** 不调用候选人模型或真实 DeepSeek/Bailian；使用真实后端监听端口、安全链、MySQL 和固定答案，HTTP 替身模拟 DeepSeek/Embedding 协议。向量依赖使用测试装配的 VectorStoreClient 替身或受控 gRPC 替身，明确写入证据标签；若后续额外用真 Milvus，作为单独 B-vector 试验，不能改名为“全真实上游”。

该负载装配目前**未实现**。`milvus.enabled=false` 会让现有 Workflow/Retrieval 条件 Bean 缺失，不能只关开关就声称完整负载链可用。后续用 test-only 配置提供替身、满足业务 Bean 装配，并隔离真实 MilvusConfiguration/Initializer；保留 EnvironmentIsolationInitializer，使用 `ai_interview_test` 和 test 集合标识，所有替身端点仅 loopback、无真 Key、外网调用防护。先做 1 VU 冒烟确认链路和零外呼，再扩负载。不得改生产 profile 绕开隔离检查。

建议每级 1 分钟渐增 + 5 分钟稳定 + 1 分钟停止新请求并收尾；每用户先取当前状态、同场最多一个正常写入，加 2～5 秒思考间隔。正常混合流量与重复/攻击/故障流量分批，记录实际发起率、在途数、拒绝数与完成数，不能只写 VU 就推断 QPS。

### 3.2 替身响应与观测

- 正常协议返回完整合法结构、固定模型元数据/1024 维 Embedding 和可追踪证据，保证真正经过生产解析/Java 校验；返回常量 HTTP 200 而跳过业务不是完整链。
- 时延档：200ms、2s、10s；故障档：明确 429/503、连接拒绝、读超时、截断/非法 JSON、迟到响应。时延和故障在替身侧控制，不缩小生产 stale 阈值制造假竞态。
- 对指定案例按服务端收到的 request/attempt 记录调用次数及开始/完成时刻，禁止使用日志中的用户原答案/密钥计数。对正常重放应无新调用；FAILED/stale 恢复允许新尝试；旧 attempt 迟到不得覆盖新结果。
- 观测 HTTP status/耗时/p50/p95/p99、吞吐、活动任务、CPU/RSS/heap/GC、MySQL 活跃连接/锁等待/死锁、线程与文件描述符。无现成指标时使用客户端与本地进程/只读 MySQL 观测，不因缺指标临时引入生产监控框架。
- 对正常小延迟档可预定错误率<1%、p95<5s 的试验目标；2s/10s 档按实际阶段数量另定预算，不沿用 5s。401/404/409 等预期业务拒绝和非预期 500 分开计数。无对照实验不写“性能提升”，Mac 共用 Docker 的结果也不是部署容量承诺。

### 3.3 核心场景矩阵（所有场景待执行）

| ID | 动作/受控交错 | HTTP/业务预期 | 数据库与外部调用不变量 |
|---|---|---|---|
| B01 | 同账号两个创建同时到达；不同账号同时创建作对照 | 同账号返回一个活跃 Session，不跨账号复用 | 每用户活跃会话≤1；全部 MAIN 快照完整；竞争失败草稿未写 |
| B02 | 同一 requestId+题ID+精确答案同时提交/完成后重放 | 完成返回已持久结果；处理中可409，最终可恢复读 | Answer/Evaluation 各一份；完成重放不增加模型调用/Session完成数 |
| B03 | 同 requestId 改正文/空白/题ID；同题换 requestId | 精确载荷冲突409；不覆盖旧答案 | 原文、attempt、评价不被伪装重放修改 |
| B04 | A JWT 读/答/生成 B Session；伪造 userId；无JWT/USER访问admin | 归属失败404、缺认证401、权限不足403；具体坏请求400另列 | B数据不变；A无B的题/答案/报告/弱项；拒绝前无上游调用 |
| B05 | 不同账号故意用相同 requestId，各自操作自己的合法题 | 不泄露另一账号答案；唯一冲突受控409 | request_id 当前全局唯一，不是每账号唯一；正常用例需全局UUID |
| B06 | 答案提交成功但 HTTP 响应被客户端侧丢弃；同原请求恢复 | 先GET/detail确认，完成则重放；fresh EVALUATING 409 | 不因客户端超时断言回滚；一次有效提交、相同结果，无额外成功副作用 |
| B07 | 上游429/503/坏JSON/超时，之后恢复并显式重试 | 当前依赖失败受控502；不自动上游重发；恢复后成功 | FAILED/attempt 状态合理；失效trace不冒充成功；最终一份评价 |
| B08 | 评价已claim后停应用；重启后先fresh再到默认10m stale恢复；旧任务迟到 | fresh拒绝/stale单胜者；恢复由请求驱动 | attempt fencing挡旧写入；FINAL/Session只推进一次；允许额外模型尝试 |
| B09 | 报告POST重复、响应丢失、生成中重启、默认10m后重试 | GET始终只读；READY重放；GENERATING fresh409/stale CAS | 1份报告；每主问题1份有效FINAL；弱项每Session每知识点最多一次累加 |
| B10 | 报告已持久但状态需协调；持久报告/FINAL不一致的故障夹具 | 合法协调无新模型/弱项更新；非法状态受控失败 | 不用伪造FINAL凑报告；Report+Weakness+READY原子，失败全部回滚 |
| B11 | 创建后管理员整体替换/禁用题；用户继续答题/查历史 | 当前Session仍固定原题/点/参考答案 | snapshot hash不变，不回连最新题库污染历史 |
| B12 | B-load达到20/35/50 VU并混入受控重放；停止新请求后排空 | 记录成功/预期拒绝/超时/未知，不能只看200比例 | 无跨用户数据、重复FINAL、卡死事务或无主任务；每case可追溯 |
| B13 | 知识处理claim后重启/迟到写入（另批可选，默认不并入面试负载） | 默认15m stale/FAILED显式恢复；READY拒绝重处理 | attempt和vector-ID补偿保留新有效批次；MySQL/Milvus双向核对 |

B08/B09 停止的是后续专用测试应用进程，数据库保持；需要测试数据库重启时另列 B14 专项和授权，不能在共享实例执行。重启不是清空表/卷。无法真实制造的窗口可用 test-only 屏障或受控网关/替身，证据标 SYNTHETIC_FAULT；时间等待不靠长 sleep 盲猜，使用状态轮询/受控 latch。正式真实环境保留默认阈值，不能把改旧 updated_at 的夹具说成真实等待恢复。

### 3.4 最终数据库核验（未来只读，按 runId 清单限定）

每次试验前后收集本 run 的 userId/sessionId/questionId/answerId/requestId/attemptId/version 及完整状态；不导出 password、JWT 或其他真实用户信息。检查不仅依赖唯一约束，还要校验正确关联和来源。绑定参数由测试清单提供，不拼接未知 SQL。

```sql
-- Q1: 单用户活跃会话；在准备语句中绑定本run用户ID
SELECT user_id, COUNT(*) AS active_count
FROM interview_session WHERE user_id IN (:run_user_ids)
AND status IN ('CREATED','IN_PROGRESS')
GROUP BY user_id HAVING COUNT(*)>1;
-- Q2: 每场主问题计划、身份、快照；应用侧断言数量=5/7/9且同场question_id不重复
SELECT session_id, id, question_id, question_type, parent_question_id,
       plan_order, display_order, SHA2(question_content,256) AS question_hash,
       SHA2(reference_answer_snapshot,256) AS reference_hash,
       SHA2(CAST(scoring_points_snapshot AS CHAR),256) AS points_hash
FROM interview_question WHERE session_id IN (:run_session_ids)
ORDER BY session_id,display_order,id;
-- Q3: Answer归属、状态、attempt与评价关联
SELECT q.session_id,q.id AS interview_question_id,a.id AS answer_id,
       a.request_id,a.attempt_id,a.status,a.error_code,
       e.id AS evaluation_id,e.evaluation_phase,e.main_interview_question_id,e.total_score
FROM interview_question q LEFT JOIN interview_answer a ON a.interview_question_id=q.id
LEFT JOIN answer_evaluation e ON e.answer_id=a.id
WHERE q.session_id IN (:run_session_ids);
-- Q4: 完成场次中每个MAIN恰好1个FINAL；同时核实该FINAL的answer确属MAIN或其FOLLOW_UP
SELECT q.session_id,q.id,COUNT(e.id) AS final_count
FROM interview_question q JOIN interview_session s ON s.id=q.session_id
LEFT JOIN answer_evaluation e ON e.main_interview_question_id=q.id AND e.evaluation_phase='FINAL'
WHERE q.session_id IN (:run_session_ids) AND q.question_type='MAIN' AND s.status='COMPLETED'
GROUP BY q.session_id,q.id HAVING COUNT(e.id)<>1;
-- Q5: 报告与会话状态；应用侧按有效FINAL用BigDecimal/HALF_UP复算均分
SELECT s.id,s.status,s.version,s.planned_question_count,s.completed_question_count,
       s.total_score,s.report_status,r.id AS report_id,r.overall_score
FROM interview_session s LEFT JOIN interview_report r ON r.session_id=s.id
WHERE s.id IN (:run_session_ids);
-- Q6: 弱项仅对本run用户，按前后快照和本场有效FINAL逐知识点复算
SELECT user_id,category,knowledge_point,status,weakness_score,discovered_count,
       last_discovered_at,resolved_at
FROM user_weakness WHERE user_id IN (:run_user_ids);
```

SQL 中 `:run_*` 是运行器展开的绑定占位说明，不是可直接粘贴 MySQL 的语法；模板未执行。Q4 数量通过还不够：FINAL 的 answer/session/main-parent/attempt 关系须按源码收口合同独立核对。Q6 不是固定“每题+1”：同场同 category+knowledgePoint 聚合一次，GOOD/EXCELLENT 可能只解析已有弱项、不新增计数；重放不重复。

同时核实每个已 EVALUATED Answer 有合法评价、五维0～20且Java总分相加一致、MAIN最多一次FOLLOW_UP、正确的INITIAL/FINAL组合、完成数/当前题/Session版本变化、RAG trace只来自成功入Prompt证据。对 Mock 上游记录替身调用计数；对真模型记录应用尝试及账单/usage，不用MySQL行数推算收费次数。

### 3.5 退出、清理与恢复

任一跨账号泄露、重复业务效果、错误FINAL来源、明文凭据日志立即停止发新请求并保存去敏证据；预计过载时先收紧客户端并发，而非放宽安全/完整性校验。停发后轮询在途任务，列出终态/可恢复/未知状态，未知不能当成功或直接删除。

先保存本run准确ID及前后非目标摘要；后续授权清理才按外键顺序/精确ID操作。不得 TRUNCATE、drop共享库/集合或删除数据卷。若有迟到外部写入，应先停旧执行者、确认提交权和最终状态，再清理；向量清理后核实非目标数据仍在。正式 test/evaluation 目标及数据清理均在独立执行任务审批范围，不由本方案自动触发。

## 4. 统一证据格式与准入

每个 case 记录：runId/caseId/试验类型、Git SHA+工作区摘要、配置脱敏指纹、数据/题库/语料/Prompt/评价标准版本、供应商和实际模型版本、角色模板hash、账号代号、Session/题目/request/attempt、时间线、HTTP状态/耗时、响应摘要、预期与实际DB差异、真实/替身边界、应用请求数、usage、实际/未知费用、人审结果、PASS/FAIL/BLOCKED/NOT_RUN/NOT_OBSERVED。

输出摘要将 Unit、Mock HTTP、MockMvc、真实HTTP+替身、真实MySQL、真实Milvus、真实模型、浏览器人工分栏；JUnit/Vitest以实际T/F/E/S和退出码报数，自定义HTTP用例报通过/失败/阻塞/未执行和样本量，不把未运行算为skipped。

开始任何 A/B-live 前必须完成：当前服务实际数据补审；题目/点/文档/向量对应；独立账号与JWT；合法来源和合成数据；有效模型凭据/配额和书面预算；默认隔离初始化器；安全日志；本run清理计划；人工评审可用。普通回归清除RUN_REAL_*和MILVUS_ENABLED；真正 guarded Smoke 按AGENTS用临时开关和local,test-real，AI baseline仅evaluation，不混用profile。

本轮未执行上述计划，不声称支持10人或50VU、不声称评分质量达标。D0的端口拒绝连接是执行环境阻塞，不是代码回归。后续先完成环境准备，再A0和B-load单用户试跑；只有各自通过并获下一阶段授权才扩样本。
