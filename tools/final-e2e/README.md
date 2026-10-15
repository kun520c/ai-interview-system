# 求职前最终完整业务验收

## 第二次中断已完成只读恢复验收（2026-10-14）

**会话 150 的完整五题真实业务已完成，`REAL_E2E_PASS_RECONCILED`。无需第三次收费续跑。** attempt 001、002 的原始 `BLOCKED` 报告、全部证据和执行锁保持原样；以下历史准备说明不再是当前续跑指令。

attempt 002 首次异常来自当时 `FinalE2eRunner.java:161` 的 `verifyHistory` lambda，经 `check:43` / `RagE2ePolicy.require:38` 抛出 `HISTORICAL_BUSINESS_ROW_CHANGED`，由 `FinalE2eResume.java:85` 的收尾检查捕获；第 92 行仅最终包装。执行器在轮换密码后把现有会话 150 和待完成的问题也登记为整行不可变历史数据。真实执行的表名 TreeMap 和 `ORDER BY id` 顺序下，第一条不一致是 `interview_question.id=174` 的合法 `PENDING → ANSWERED` 与 `updated_at` 更新。因此属于执行器的续跑保护范围错误，业务、预检环境和鉴权均已成功。

attempt 002 确实轮换了合成测试用户 427 的密码（`password_changed_at=2026-10-14 00:12:27`），正常登录 HTTP 200，复用答案 100 / 评价 50而未重新付费评价。新增答案 108～113、评价 57～62、追问 190、6 Retrieval Batch、30 Hit Log、报告 14、薄弱点 38，共 51 行新增业务数据，并合法推进原会话和 5 条原问题状态。51 是新增行数，原始执行没有单独记录全部 SQL 写入语句次数。

该次新增真实模型请求 13 次：6 Embedding、6 DeepSeek 答案评价、1 DeepSeek 报告、0 Jev，均有原始 request/response SHA 和 HTTP 200 / usage；按原执行报告所保存费率估算约 ￥0.1046562，不是供应商账单核对结果。

当前真实 MySQL 只读核验与终态快照一致：5 MAIN（173～177）全部 ANSWERED，追问 178、190 全部完成；7 答案、7 评价（2 INITIAL + 5 FINAL）；FINAL 得分依次 96、92、91、82、10；报告 14 READY、均分 74.20；NETWORK/tcp_vs_udp 薄弱点 38 的 weakness_score=90.00、discovered_count=1。7 Retrieval Batch 和 35 Hit Log 全部对应实际 Prompt 证据；191 行冻结语料和 52 条冻结向量只读比对通过。

修复仅涉及工具：`FinalE2eRunner` 允许明确续跑会话的工作流字段、该会话问题的 status/updated_at 正常推进，仍保护题目快照、归属、已付费答案/评价/RAG及其他历史行；差异诊断记录 table/rowId。`FinalE2eResume` 在任何密码写入和模型调用前回放实际历史保护合同。新增 `FinalE2eSecondRepair`、`FinalE2eSecondRepairOfflineTest`、`second-repair.ps1` 进行纯证据回放和带 SQL READ ONLY 的当前状态核验，不启动 Spring、不登录、不构造模型 transport。原密码和 JWT 仅保存在已结束进程内存中；当前已完成，不需要再轮换。

本轮相关检查：日期修复回归 14 tests、第二次修复离线检查 33 tests，均 0 failures / 0 errors / 0 skipped；后者覆盖真实执行器历史保护、正确/错误作用域、原数据篡改/删除、各题模型 wire/Prompt/Java 评分和报告/薄弱点。真实只读核验通过。首次只读连接被沙箱以 `Permission denied: getsockopt` 阻断，允许本机连接后通过；这是执行权限问题，未修改生产配置。为避免无关重复验证，没有重跑完整 Maven 回归。本轮 0 新模型调用、0 业务数据库写入、0 向量写入，未 commit/push。

完整诊断和当前快照位于 `target/final-e2e/second-repair-20261014/`。审计边界：attempt 002 的全表历史 hash 基线仅在当时进程内存中，未落盘；保存的会话快照足以验证本场原成果，不能追溯证明所有无关历史行在当次执行中完全未变。当前只读核验通过不补造该历史证据。

唯一当前复核命令（已执行通过；费用预算 **0 元**，无需新收费/密码授权）：

```powershell
pwsh -NoProfile -File .\tools\final-e2e\second-repair.ps1 -Mode VerifyReadOnly
```

不准备 attempt 003。`NEXT_REAL_RUN_READY=NO` 的原因是 **NOT_NEEDED_ALL_BUSINESS_COMPLETE**，并非剩余业务被阻塞。下一步可整理 EASY 五题真实业务闭环的演示/简历说明；本验收不证明前端操作、线上部署或主观评分准确率。

## 首次执行阻断的修复与续跑

首次 `final-e2e-20261013-001` 原报告仍为 `BLOCKED`，15 个原始证据文件（含两把锁）全部保留。确切抛出点是首次源码 `verifyEvaluation()` 第 324 行，门禁为 `HTTP_DETAIL_DB_PROJECTION_MISMATCH`；第 549 行仅重新包装异常。执行器的独立 Jackson 默认把 `LocalDateTime` 序列化为数组，Spring HTTP 返回 ISO 字符串。用同一生产 DTO 和原始响应离线复现后，差异只有 `/startedAt`、`/createdAt`、`/questions/0/answer/submittedAt`。修复采用 Spring 的实际 `ObjectMapper`，完整严格比较保持不变，付费前先验证日期合同；异常记录改用本执行器当前操作和明确门禁代码。

首次已发生真实业务写入：用户 427、428，会话 150，5 MAIN + 1 FOLLOW_UP、答案 100、评价 50、1 Retrieval Batch、5 Hit Log，共 17 行新增数据以及工作流状态更新。更新语句次数未单独记录，不能把新增行数称为全部 SQL 写入次数。首题实际通过：答案 EVALUATED，评价 INITIAL / 7 / WEAK / FOLLOW_UP，追问 178 等待回答；其余 4 MAIN 待回答，报告和薄弱点尚未生成。

首次模型请求共 3 次：Embedding 200、Jev 451、DeepSeek 200。Jev 原始响应明确为 `Typesafe is not available in your region.`，生产合法降级到 DeepSeek。451 是否计费没有账单证据，不能把 3 次转发都称为成功收费。首题实际 Query、1024 维 Embedding、5 条水合证据、Prompt、评分点覆盖、Java 7 分及追问决策已通过原始 wire SHA 和数据库只读复算。原 stdout/stderr 未单独保存，无法补造完整控制台日志；保留的 15 条业务 HTTP 日志、3 组完整 provider 请求/响应、原异常栈和数据库快照是本次定位依据。

本轮修复：14 项纯离线检查全部通过（篡改日期、分数、答案、字段和题目顺序均仍被拒绝），2 项真实 MyBatis/事务 + Mock 外部端口的整场业务测试全部通过，均 0 failures / 0 errors / 0 skipped；本轮 0 新模型调用、0 验收业务数据写入。新增 `FinalE2eRepair` / `FinalE2eRepairOfflineTest` 用于纯回放和只读核验；`FinalE2eResume` / `resume.ps1` 仅准备未来授权后的续跑。生产业务代码和默认 RAG 没有修改。

只读诊断、最新数据库快照、测试日志、就绪状态和独立 seal 位于 `target/final-e2e/repair-20261013/`。诊断确认当前业务状态与原失败快照完全一致；同一测试 Collection 仍为冻结 52 向量。未来凭据轮换的权限只通过 SELECT information_schema 检查；MySQL/JDBC 在 READ ONLY 模式中拒绝了早先尝试的 `EXPLAIN UPDATE`，该准备错误已保存在 repair-console.log，没有执行 UPDATE，也没有放宽只读保护。旧 `run.ps1 -Mode ExecuteOnce` 继续拒绝再次执行。

新的独立 attempt 为 `final-e2e-20261013-002`，正常登录并续跑原会话 150，不重新创建面试，也不重新评价/检索首题 173。原随机密码仅在已结束进程的内存中，故新授权必须明确包含：只轮换合成测试用户 427 的密码，角色和状态保持不变，再走正常登录和数据库用户 JWT 鉴权。不会手工签发 JWT、迁移评价、写入期望分数或修改业务状态。

已知 Jev 区域限制不再重复尝试；续跑仅选择项目已有 DeepSeek Provider，生产默认配置文件保持不变。最多新增 9 Embedding + 10 DeepSeek（含最终报告），Jev=0；已有首题的 3 个请求不重发。预计新增 ￥0.05～0.50，按前述官方高峰未命中费率、字节上限及 8 CNY/USD 的保守计划换算约 ￥2.00352，申请预算 3 元。这是请求预算估算，不是账户扣费硬限额。

**本修复任务没有新收费或密码轮换授权，也没有执行下面的命令。** 新授权短语：**授权仅轮换合成测试用户427密码，正常登录续跑会话150一次，新增模型费用预算上限3元。**

未来获得这次独立授权后的唯一命令：

```powershell
pwsh -NoProfile -File .\tools\final-e2e\resume.ps1 -Mode ExecuteResumeOnce -Authorization AUTHORIZE_RESUME_150_ROTATE_TEST_PASSWORD_ONCE_MAX_3_CNY
```

续跑在任何业务写入前再次核对原证据、当前库状态、冻结资源及离线 seal；原子预约新目录，写入独立执行锁，不覆盖 attempt 001。先对首题原 requestId 做正常幂等回放并验证 0 模型调用，再从追问 178 完成剩余计划及真实报告。成功/失败均保留新锁和现场。只有本场全部五题及报告真实完成，新的续跑报告才能写 `REAL_E2E_PASS`；旧失败报告不改为 PASS。

本轮只验收生产业务闭环，不继续 P4.2 研究。`REAL_E2E_PASS` 仅可在一场真实 EASY 五题计划、实际追问、结束、真实报告和数据库一致性核验全部成功后使用；追问次数和薄弱点条数按现场事实记录，零条不能称为真实验证了新增记录。

当前生产路径：基础配置默认 DeepSeek；本机现有用户环境 `AI_JUDGE_PROVIDER=jev`。本轮采用 Jev 评分 + DeepSeek 反馈，保留生产 `JevJudgeProvider` 的合法 DeepSeek 降级。RAG 采用默认 dense TopK=5，不启用 `rag-p1` 的 0.70 阈值；P4.2 工具代码不进入运行 classpath。

仅使用 `ai_interview_test`、`default/knowledge_chunk_vectors_test`。保留 `EnvironmentIsolationInitializer`。复用冻结 191 行语料和既有 52 条向量；不重做 Embedding，不复制源库，不创建或加载集合，不变更表结构。

## 本轮准备及验证

- 新增 `FinalE2eRunner`：正常注册、登录、JWT 校验、五题创建/恢复、获取题目、回答、重复提交、按 Java 决策推进、报告、历史及持久化核验。所有直接 SQL 都是只读 SELECT，业务写入只经生产 HTTP API。
- 新增 `FinalE2eAudit`：透明记录实际 Embedding、Milvus、MySQL 水合、Prompt 和 Judge 结果，禁止向量写入。
- 新增 `FinalE2eGate`：保存真实模型 HTTP 请求/响应正文、SHA、provider、usage 与发送前数据库状态；不记录 Authorization header、密码或 JWT。
- 新增 `run.ps1` 和 `FinalE2eOfflineTest`：隔离子进程、固定数据目标、来源 seal、费用授权和永久执行锁；复用旧单题工具的只读/JSON/HTTP utilities，不执行旧付费入口。
- 新增 `FullInterviewBusinessOfflineTest`：真实 Spring MVC/JWT/MyBatis/事务，AI 与向量请求明确 Mock；覆盖五个 MAIN、一个 FOLLOW_UP、六次水合和引用持久化、五份 FINAL、报告均分、薄弱点、重复提交/报告幂等及外部故障终态。测试数据事务回滚。
- 修改 `InterviewControllerTest`：补充 `@ActiveProfiles("test")`，修复被环境隔离初始化器拒绝的测试配置。没有修改生产业务代码。

执行记录和计数集中保存在 `target/final-e2e/preparation/`。首次沙箱回归中断：已完成 78 项，0 failure、38 error、0 skipped，网络 loopback 权限问题；随后允许本机连接的定向回归 307 项、0 failure、16 error、0 skipped，定位到 Controller 测试缺少 profile；修复复验 18 项全部通过。非相关测试无需全量重跑。最新各 suite 的最终有效结果与整场新增测试结果由 Offline 模式读取实际 Surefire XML 汇总。

Docker Desktop 和原 Milvus/etcd/MinIO 容器已恢复。首次 Milvus 不可用的准备失败记录保存在 `preflight-milvus-unavailable.json`，没有消耗真实执行预约。只读准入及无模型 Key 的生产启动结果见 `preparation/report.json`。

## 唯一真实执行

必须先得到人类本轮单独一次性费用授权。旧 P0～P4、单题 E2E 授权不适用；命令中的字符串本身不是授权。

```powershell
pwsh -NoProfile -File .\tools\final-e2e\run.ps1 -Mode ExecuteOnce -Authorization AUTHORIZE_FINAL_E2E_ONCE_MAX_5_CNY
```

授权短语：**授权执行一次完整五题真实面试，费用预算上限 5 元（AUTHORIZE_FINAL_E2E_ONCE_MAX_5_CNY）**。

固定 attempt：`final-e2e-20261013-001`。入口先校验最终 Offline seal，再原子预约新目录。已有目录直接拒绝。首次业务运行前保存 `EXECUTION_CONSUMED.lock`；成功、失败、超时均不删除，不更换 ID 自动重试。所有旧执行锁、报告和 frozen corpus 保留。

预计 ￥0.10～0.60。无追问正常 5 Embedding + 5 Jev + 6 DeepSeek = 16 次；最多五次追问时正常 31 次；若反馈失败再经生产 fallback，保守最多 10 Embedding + 10 Jev + 21 DeepSeek = 41 次。每题模型调用在上游转发前消耗预算，无 HTTP 重试；每份答案最多 1 Embedding、1 Jev、2 DeepSeek，报告最多 1 DeepSeek。异常答案或报告不会由 Runner 自动重提。

每次模型请求 JSON 最大 65,536 字节、DeepSeek 输出最大 4,096 token。采用当前官方价格和 8 CNY/USD 的保守计划换算，字节视作输入 token 上界的估算为 ￥4.390569。5 元是本轮授权预算；请求计数和大小受程序强制限制，token 估算不是供应商账户的扣费硬限额。实际账单、缓存优惠和汇率以供应商为准；真实结果保存原始 usage 和可计算费用，不宣称核对了账户账单。

价格核对于 2026-10-13：[Jev](https://docs.typesafe.ai/models) 输入 $0.042/百万、输出免费；[DeepSeek Flash](https://api-docs.deepseek.com/quick_start/pricing/) 高峰未命中输入 $0.30/百万、输出 $1.20/百万；[北京 Embedding](https://help.aliyun.com/en/model-studio/qwen3-7-text-embedding) ￥0.50/百万输入。DeepSeek 使用当前官方 `deepseek-flash` 名称。

候选回答是明确的合成验收输入：首题和最后一题回答不完整，中间三题采用已冻结参考答案；首题追问用正常完整回答，最后一题追问仍不完整。目的是让实际 Java 规则有机会触发追问和薄弱点，绝不指定或手写模型分数、评价、追问或报告。模型若不建议追问则按现场合法决策完成，单独记录真实覆盖限制。

真实输出在 `target/final-e2e/attempts/final-e2e-20261013-001/`：单一 `report.json`、完整 providers 请求/响应、各次数据库快照和每题证据。若失败，报告为 `BLOCKED` 并保留现场与锁；模型降级是否发生、每题阶段、全部计划完成情况、报告/薄弱点一致性均可核查。Jev 在数据库存的是生产 typed response projection；完整 HTTP envelope 另存，不能把投影误称为完整原始 envelope。

## 暂不能写入简历

未授权并实际通过前不能写“完整真实 AI 面试端到端已验收”。本轮不证明 P4.2 生产启用、RAG 准确率提升、评分准确率、响应性能、成本节省、MEDIUM/HARD 完整真实验证、前端 UI 真实闭环或线上部署。若 Jev 全部降级，也不能写本场实际使用了 Jev 评分。

无 Git commit/push。项目原有未提交修改全部保留。
