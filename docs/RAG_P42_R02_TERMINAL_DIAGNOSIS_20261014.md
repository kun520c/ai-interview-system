# P42-R02-20261014 停止诊断与现有证据收口

2026-10-14，Asia/Shanghai。结论：首题 Gate 返回了不确定候选，触发冻结安全门禁；执行了 1 题，完整完成 0/8。没有发生“8 题已完成、仅终止汇总错误”。原 STOPPED、执行封存、请求/响应、费用记录和永久锁均保留。

## 第 325 行的实际条件

实际执行封存 SHA 为 `00e15ec161e3a5c9ce6740fe3c4f87f1497f339f6490d811cbb526c480cd6a12`。本轮复验源码、运行 class、缓存依赖及原冻结门禁，均匹配该封存。实际源码是 [P42R02ExecuteOnce.java 第325行](E:/learning/IDEA_PROJECTJ/ai-interview-system/tools/rag-p4-2-r02/execution/P42R02ExecuteOnce.java:325)：

```java
require(!accepted.diagnostics().path("status").asText().contains("UNCERTAIN"),
        "GATE_UNCERTAIN_STOP");
```

预期：条件为 `true`，Gate status 不包含 `UNCERTAIN`。实际：status=`ADMITTED_WITH_UNCERTAIN_CANDIDATE`，`contains(...)` 为 `true`，取反后为 `false`，所以抛出 `Stop("GATE_UNCERTAIN_STOP")`。

原始 DeepSeek JSON 中，Rerank 第 1 候选 `cbeb0d56-683a-41e7-b6c9-b0b9ec703597` 是 `CORE_SUPPORT + MATCH`，包含 3 段主要机制引文；第 2 候选 `504ae501-0222-4f8c-abce-7d3fec11d284` 是 `NO_SUPPORT + UNCERTAIN`，supports 为空，正文为参考条目/链接列表。`PrimarySemanticGate` 对任一候选的 `versionAssessment=UNCERTAIN` 累积 uncertain 标志，因此得到上述混合状态。其局部决策曾保留第 1 候选；运行器随后按冻结规则拒绝该状态，将本题最终 evidence 清空并停止整批。

分类：**正确执行已冻结的保守安全门禁；RUNNER_DEFECT=NO（针对本次停止）**。冻结准备文档明确规定判据不确定即停止；原 Mock 也覆盖不确定停止。被拒绝候选的版本不确定仍触发整批停止，是该规则的保守取舍，不能在本轮放宽。模型返回 HTTP 200、完整合法 JSON、finish_reason=stop；模型别名通过第 322 行检查。不是网络/gRPC、预算超限、数据不一致或结果汇总错误，也不能据此判定模型的语义判断正确或错误。

第 320 行是 Semantic Gate 的 measure 调用；第 325 行是原始失败点。第 353 行在写完结果、资源后检查与 SHA 封存后包装并重新抛出已保存的停止异常。终端 `TERMINAL` 与 PowerShell exit=1 是外层报告，原 `summary.json` / `validation.json` 已明确保存 `SEMANTIC_GATE / GATE_UNCERTAIN_STOP`。

## 逐题、逐阶段事实

状态含义：REQUEST_SENT 表示已发请求；SUCCEEDED 表示该项成功；FAILED 表示阶段/题目失败；NOT_STARTED 表示未执行；UNKNOWN 表示无法确定发送/返回结果。本次没有 UNKNOWN，也没有仅发请求却缺失响应的请求。

| 题目 | 题目状态 | Embedding | Milvus Search | Rerank | Semantic Gate | 结果持久化 |
|---|---|---|---|---|---|---|
| H42-01 | FAILED | SUCCEEDED | SUCCEEDED | SUCCEEDED | FAILED¹ | SUCCEEDED² |
| H42-02 | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED |
| H42-03 | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED |
| H42-04 | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED |
| H42-05 | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED |
| H42-06 | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED |
| H42-07 | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED |
| H42-08 | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED | NOT_STARTED |

¹ 首题四阶段均 REQUEST_SENT 并有完整响应；三个模型均 HTTP 200，Search 返回 5 条并完成 MySQL 水合。Gate 的 HTTP/JSON/usage 接收成功，**Java 不确定状态门禁 FAILED**，不能把响应成功记成整阶段成功。前三阶段 validation=VALIDATED，Gate validation=FAILED_STOP。

² `H42-01/result.json` 成功保存的是 FAILED_STOP、Gate 诊断与空 finalAdmittedCandidateIds；它不是 COMPLETE。其余 7 个题目目录为空，无阶段锁、请求、响应或 result.json。

收费调用共 **3 次**：Embedding=1、Rerank=1、DeepSeek Gate=1；Milvus Search=1。四项逐阶段 attempt 锁、receipt、原响应与 summary 计数一致，无重试。资源前后保存的 191 行 MySQL 指纹与 52 向量 SHA 相同，resourcesUnchanged=true；本轮未连接 MySQL/Milvus。

## usage 与费用核算

以下使用已保存响应中的实际 usage，不使用 Token 代理。DeepSeek 请求/响应发生于北京时间 19:18:44–48，属于官方闲时；缓存命中=0、未命中=1840。闲时输入未命中 ¥1/百万 Token、输出 ¥4/百万 Token；官方仍接受旧请求名并按 Flash 价格计费。[DeepSeek 官方价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)

| 阶段 | 实际输入 Token | 实际输出 Token | 适用原价（元/百万 Token） | 核算费用（元） |
|---|---:|---:|---|---:|
| Embedding | 77 | 0 | 输入 0.5 | 0.0000385 |
| Rerank | 2621 | 0 | 输入 0.5 | 0.0013105 |
| DeepSeek Gate | 1840（全未命中） | 554 | 输入 1；输出 4 | 0.004056 |
| 合计 | 4538 | 554 | | **0.005405** |

百炼使用普通同步调用，按北京地区文本输入原价计费。[Embedding 官方价格](https://help.aliyun.com/zh/model-studio/qwen3-7-text-embedding)、[Rerank 官方价格](https://help.aliyun.com/zh/model-studio/qwen3-7-text-rerank)。价格于本轮 2026-10-14 核验。

`CONFIRMED_COST_CNY=0.005405` 指已确认响应 usage × 官方适用原价的核算值，**未核验账户实际账单、优惠或赠送额度**。`UNKNOWN_COST_EXPOSURE=NONE` 指无缺失响应/usage 的已保留请求，不代表核验了账单。原 summary 的 ¥0.009461 使用冻结峰价输入 2、输出 8 作保守估算；它保留原样。两种费用口径不同，不是丢失费用或本次停止原因。所有 usage 在冻结上限内；输出 554<3072，费用门禁仍有余量。

## 可完成的离线分析与缺失数据

已用封存的 Java 适配器离线回放首题：1024 维向量有效；五候选水合、Rerank 索引/分数及 Top2 对应一致；Gate 实际请求与冻结合同/Prompt 一致；从原始响应重新得到的完整 Gate diagnostics 与 result.json 逐字段一致，包括 3 段逐字引文的 UTF-16 偏移和混合不确定状态。第 325 行失败可直接离线重现，未调用 ExecuteOnce、模型或 Search。

现有证据足以支持 **H42-01 的技术诊断及对其 5 个候选、3 段引文的独立人工审查**。局部 Gate 保留 1 条、最终运行器输出 0 条是可观察事实；缺少独立人工标签时不能将它定性为误拒、计算主要机制保留率或宣布质量 PASS。

H42-02..08 缺少全部实际 Embedding、Search Top5/水合正文、Rerank、Gate 响应、usage 和最终结果，共缺 35 个检索候选及 7 题实际决策。它们保持 **NOT_EVALUATED**。完整完成题数为 0；8 题的 4/4 覆盖、4/4 拒绝、≥90% 保留率等冻结结论均无法计算。人工标签也不能补出未发生的请求结果。

本轮调用现有 Analyze 函数只读派生：SHA_VERIFIED、STOPPED_OR_INCOMPLETE、paidAttempts=3、ambiguousReservedAttempts=[]，未生成质量指标。其通用 PENDING_INDEPENDENT_HUMAN_REVIEW 不表示实验完成；本报告明确将批次质量记为 NOT_EVALUATED_INCOMPLETE。派生技术输出单独位于 `target/rag-p4-2/r02-terminal-diagnosis-20261014/`，不覆盖原执行证据。

## 离线核验与保留

本轮 10 项聚焦检查：Java 保存响应回放 4、Python 封存/阶段/usage/资源指纹/不完整质量门禁 6；**failures=0、errors=0、skipped=0**。没有运行 Maven 全量回归、修改运行器或创建未来收费入口；没有 Git commit/push。

原执行 artifacts SHA：`116b275973087f3335227e6348fc363904937d5ba48884c1dbdbfdcf9f1237b9`。原执行永久锁 SHA：`e62362bd0143038e220bd280f2015ecc8cac9c175734c1ff26c2a90a7b29b4b1`。核验前后保留清单与原执行目录文件清单一致；STOPPED、锁和所有原始数据均未修改。

下一步：仅独立人工审核现有 H42-01 候选与引文，并以部分证据收口 R02；8 题质量结论保持未评估。本轮不补发、续跑、重试、放宽冻结规则或提出第三次收费执行。
