# AI 评分质量评测资料索引

独立验收报告位于 [2026-11-16-AI评分质量评测可行性与首轮有限测试.md](/Users/kun/Documents/Reports/ai-interview-system/2026-11-16-AI评分质量评测可行性与首轮有限测试.md)，按用户的项目报告目录规范保存；此文件只作为仓库内索引，不复制报告正文。

- [评测协议](PROTOCOL.md)：人工标签、指标、冻结和归因规则。
- [逐题风险清单](bank-risk-review.csv)：evaluation 快照中 63 题的候选审查，AI_DRAFT，非人工验收。
- [人工判断历史和待复核项](HUMAN_REVIEW.md)：保留 ACID 原判断与后续修订。
- [脱敏指标](metrics-v1.json)：12 次真实模型测试的可复算统计。
- [失败记录](FAILURES.md)：调用前计划校验失败、运行库权限阻塞、历史失败与追答目标来源修订。
- 本机忽略文件：[evaluation 快照](evaluation-bank-snapshot.json)、[本轮开发集 v1](development-v1.json)、[追答目标来源修正版 v2](development-v2.json)、[冻结留出集](holdout-v1.json)。历史原卡和原始模型请求响应均属于本机私有材料，分享前需单独审查。

本轮规模化正式评测、评分内容质量及生产发布均为 NO-GO / 证据不足；此索引本身不代表质量通过。
