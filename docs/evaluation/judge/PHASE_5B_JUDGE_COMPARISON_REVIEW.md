# Phase 5B real-provider comparison — interrupted

**未完成比较，不能得出 Jev 或 DeepSeek 质量结论。** 首个正式冻结样本 `E02-EXCELLENT` 调用 Jev 一次，HTTP 200，实际返回模型 `jev-1.13.0`（请求别名 `jev-latest`）。响应在生产 `JevJudgeProvider.validateAnswers` 中被拒绝；未进入 DeepSeek 反馈、合并、Java 评分或独立 baseline。

## 首调用阻断证据

`clarityScore=3.56`，返回概率为 `{0:0, 1:0, 2:0.02, 3:0.41, 4:0.57}`。由原始概率计算：

```text
2×0.02 + 3×0.41 + 4×0.57 = 3.55
abs(3.55 - 3.56) = 0.01 > 0.001
```

生产严格一致性校验抛出 `ExternalServiceException: Jev分数或概率分布不一致`。离线直接使用真实生产 `buildRequest` 和 `validateAnswers` 方法复现该拒绝，没有另一次模型调用，也没有更改分数、概率或容差。其余四个维度的期望分数计算一致。本次捕获的 question keys 匹配、score 范围及 confidence 本身没有发现该类问题；完整 38/38 响应校验门禁仍未通过。

这证明当前返回值与生产严格数学契约存在不兼容，不能归因为认证失败，也不能据此断言模型评分质量差。可能涉及供应商概率显示精度与 score 计算精度的差异，但该原因仅是假设，本次没有供应商确认或额外 API 探测。

生产 catch 分支请求了一次 fallback。根据恢复指令的首调用立即停止要求，临时观察器在 fallback 调用入口停止，不允许任何 DeepSeek HTTP 请求。**请求 fallback=1，实际 fallback 评判=0。** 该响应不计作 Jev primary 成功样本。

## 实际运行范围

冻结 38 个 Context 全部使用真实生产 QueryBuilder/PromptBuilder 构造。真实 RetrievalAdapter、SpringAiEmbeddingClient、MilvusVectorStoreClient 和真实 MyBatis XML 读取证据；topK=5，4 个 MAIN query + 4 个 FOLLOW_UP query。每个 query hash 仅检索一次，两种追问修复使用相同冻结追问。8 次 Embedding、8 次 Milvus search，40 个被选 evidence 命中记录保留于运行证据。MAIN query 不包含候选人答案。缓存为生产不可变结果，38 个 case 到 query hash 的复用关系完整保留。

没有任何一个 case 完成 Jev+DeepSeek 与原 DeepSeek 的成对比较。基准包含完整备选输入，但独立 baseline=0 次，不能把未执行描述为相同输入效果已经验证。

此前一次临时工具准备失败把 `rag_retrieval_batch` 主键误假定为 `id`；实际是 `retrieval_batch_id`。该尝试所有外部调用均为 0，记录于 `PHASE_5B_PRE_PROVIDER_TOOLING_FAILURE.json`，修正的仅是临时 SELECT。之后唯一正式 Jev 样本调用未重跑；没有模型重试。

## 未获得的指标

下列指标对两条路径及 topic / answer class / MAIN vs FOLLOW_UP 各分组均为 **NOT_RUN**，不填 0 或虚构百分比：micro/macro precision、recall、F1，exact coverage-set match，false-covered/missed-covered；score-band 命中率、24 对排序通过率、各类别均值/中位数及 per-topic 总分；追问排序/结构/候选 wording 差异；Java 总分/等级/DecisionAction 重算；38/38 Jev 映射、反馈决策字段保留、exact answer quotes 和 hard semantic contradictions。Jev 与 DeepSeek 差值及 materially-worse 门禁均不能评估。

## 问题等级与停止点

- P0：没有发现密钥泄露、数据库写入或 corpus 改动。
- P1：首个真实响应的 score/probability 严格一致性不通过，阻断 5B 和 Phase 6。
- P2：本基准是预先审查的合成中文样本，不构成独立真人评委或普适模型质量证据。

不放宽校验，不调整模型、提示词、confidence、RAG 或 benchmark。交 Commander 判断该协议兼容问题及任何后续修复/重新执行方案。
