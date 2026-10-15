# RAG P4.2 正式执行停止诊断

日期：2026-10-14，Asia/Shanghai。原正式实验已消费批次锁，保持STOPPED，禁止重启、续跑或补发。

```text
P4_2_EXECUTION_STATUS=STOPPED_BEFORE_FIRST_CASE
ROOT_CAUSE=MILVUS_RESOURCE_PREFLIGHT_GRPC_FAILURE
STOPPED_CASE=NONE；H42-01至H42-08均NOT_STARTED
STOPPED_STAGE=RESOURCE_PREFLIGHT_MILVUS
EMBEDDING_CALLS=0
MILVUS_SEARCHES=0
RERANK_CALLS=0
DEEPSEEK_GATE_CALLS=0
CONFIRMED_COST_CNY=0.00（模型调用费用）
UNKNOWN_COST_EXPOSURE=NONE（没有已保留但状态未知的模型请求）
COMPLETED_CASES=0/8
EVIDENCE_INTEGRITY=SHA_VERIFIED；原证据、锁、源码及封存未变
RUNNER_DEFECT=YES（异常诊断信息缺失；不是模型调用或预算控制缺陷）
OFFLINE_FIX=诊断补丁已在独立副本编码、编译并通过9项测试；未替换已执行版本
QUALITY_EVALUATION_STATUS=NOT_EVALUATED_NO_CASES_EXECUTED
NEXT_ACTION=恢复Docker/Milvus后另行评估新实验授权；永久保留本次锁
```

## 停在哪里、为什么停止

原记录位于 `target/rag-p4-2/run-once`。北京时间18:32:34.688开始，18:32:40.623停止，耗时5933.5207ms。`summary.json`保存的原始失败类型为 `StatusRuntimeException_NO_RETRY`，所有题目为NOT_STARTED，`stages=[]`，四种调用计数均为0。目录只有7个批次文件及8个空题目目录，没有逐阶段attempt锁、请求、响应或result.json。

已执行源码的顺序为：批次占位锁→环境隔离→资源工厂→MySQL只读身份/冻结语料核验→Milvus连接及元数据/向量预检→题目循环。原异常来自资源工厂内的Milvus/gRPC路径，尚未进入第一题。资源工厂未返回，`resources-before.json`/`resources-after.json`均未生成。不是PowerShell exit=1、模型输出、Prompt协议、预算超限或冻结漂移导致。

本轮在主机权限下只读检查：MySQL 3306接受连接；19530、9091无监听且TCP连接超时；没有Docker/Milvus进程，Docker Linux引擎管道不存在。随后使用原缓存Milvus SDK、相同URI和5000ms连接参数，进行一次当前连接诊断，在 `MilvusClientV2`构造阶段取得 `io.milvus.shaded.io.grpc.StatusRuntimeException` / `DEADLINE_EXCEEDED`。该诊断没有进入Collection描述、Search或写入。

**原异常的gRPC code、description及栈没有保存，不能把本轮复现的DEADLINE_EXCEEDED补写为原实验事实。** 原实验可确定为Milvus资源预检gRPC失败；当前Docker/Milvus不可用是已核实的环境问题，与原故障相符。原故障具体发生于客户端构造还是后续哪个预检RPC，无法从现有记录精确区分。初始沙箱TCP探测的10013权限拒绝属于诊断环境限制，已用主机只读检查排除，不作为原故障根因。

## 已发生的操作和费用

| 原实验项目 | 原始证据结论 |
|---|---|
| 执行题目、完成题目 | 均为0；H42-01至08全部NOT_STARTED |
| Embedding / Rerank / DeepSeek Gate | 各0次 |
| Milvus Search | 0次；连接/元数据预检属于另一类操作，具体RPC次数未保存 |
| 成功模型响应 / 已占位但响应未知请求 | 0 / 0 |
| usage、provider HTTP状态、检索候选 | 均无记录，因为未进入任何题目阶段 |
| 模型费用 | ¥0.00；不是把缺失usage推算为0，计费请求根本尚未开始 |
| 原摘要费用字段 | estimatedKnownCny=0.0；costCompleteForAttempts=true |
| MySQL | 由已执行源码顺序可推断：连接、只读身份与冻结语料核验已通过，随后才进入Milvus；逐条SQL审计留在未返回对象内，未落盘，不能补报查询数 |
| 数据修改 | MySQL走服务器READ ONLY、SELECT白名单、rollback-only；Milvus尚在连接/只读预检，无业务写入、Collection初始化或Search路径 |

原预检没有完成资源前后快照，**不能宣称此次真实MySQL/Milvus完整集成校验通过或52向量复验完成**。模型调用的0来自摘要、空阶段清单、全部NOT_STARTED、无attempt锁，以及“先持久化attempt锁再发送”的已封存代码，证据相互一致。

## 原证据复验和最小离线修复

使用原入口 `execute.ps1 -Mode Analyze`，Java冻结/保存结果复验及Python SHA复验全部通过：integrity=SHA_VERIFIED、executionStatus=STOPPED_OR_INCOMPLETE、paidAttempts=0、actualUsage=[]、ambiguousReservedAttempts=[]。原Analyze只列阶段失败，因没有阶段请求而显示failures=[]；这不表示没有批次故障。其PENDING_INDEPENDENT_HUMAN_REVIEW也不能证明已有可审查结果，本次实际没有质量数据。

原 `artifacts.json` SHA为 `bfa07c6a23b51c1a53e5f7e9c53084dddbada70c8c97fe93e5f48f2e9e7b2213`；批次锁SHA为 `3e22a422e426171cbbdf948bafe3479215c84893bd9c9852e642d602e0b80d21`。原7个执行文件及8项执行源码/封存关键文件共15项前后SHA一致；原执行封存仍通过Verify。没有修改原freeze、增量、已执行源码/class、请求/响应、状态或锁。

诊断缺陷有两处：Java将原异常包装为通用STOPPED后，只输出外层code，丢失可安全记录的原始gRPC状态和栈；Python未展示批次预检失败，缺少result.json时没有利用摘要中的明确NOT_STARTED状态。

为保留已执行版本与其封存，最小补丁及编译副本隔离在 `target/rag-p4-2/stop-diagnosis-20261014/offline-fix/`：

- `runner-diagnostics.patch` / `P42ExecuteOnce.java`：增加预检阶段标记、异常类链、gRPC状态码和类/方法/行号；不保存异常message、provider正文、SQL参数或凭据。仍STOPPED、零重试、永久锁。
- `analyzer-diagnostics.patch` / `analyze.py`：展示摘要批次错误、保留NOT_STARTED；无题目执行时明确NOT_EVALUATED。质量判据和原实验状态不变。

这些补丁**未安装到本次封存的执行路径，也未重新封存或执行**。未来若授权新的实验，须先审查并纳入该实验独立执行封存。

| 本轮验证 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| Java诊断副本：三类gRPC状态、凭据不泄漏、包装异常、匹配预检失败和已消费锁 | 6 | 0 | 0 | 0 |
| Python诊断副本：原部分结果回放、匹配gRPC预检、未完成实验拒绝质量标签 | 3 | 0 | 0 | 0 |

诊断副本编译通过；未重跑83项或Maven全量回归。当前SDK只读连接诊断1次，失败为DEADLINE_EXCEEDED；本轮真实模型调用0、Search0、MySQL/Milvus写入0。

## 可复用结果与下一步

可复用的是原8题/语料/判据冻结、精确增量封存、原83项离线验证，以及此次永久锁、停止记录和零调用证据。没有候选、排序、Gate判断或完整题目结果，不能计算RAG质量指标，也不能宣布8题PASS或FAIL。

需要先恢复Docker/Milvus并单独做只读资源可用性检查。本次实验不可续跑。如果仍需原定8题质量结论，必须明确授权新的正式实验身份、独立封存与新锁；本次已消费锁及所有证据永久保留。本轮没有启动服务、创建新实验、重试收费调用或执行Git reset/commit/push。
