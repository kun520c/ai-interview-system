# 候选级语义 Gate：独立离线实验

从项目根目录运行：

```powershell
python -I -B -X utf8 tools/rag-candidate-gate/run_offline.py
```

使用已缓存的 Java 21 和依赖，只编译到新建的 `target/rag-candidate-gate/offline-<uuid>/`。没有收费执行模式、模型请求、应用启动、数据库或 Milvus 访问入口；子 JVM 不继承凭据、真实测试开关或 Java 注入参数。历史冻结文件、执行锁及原编译目录只读。

`CandidateLevelSemanticGate` 先用未修改的 P4.2 `PrimarySemanticGate` 校验原始完整响应，再区分不确定性作用域。只有 `NO_SUPPORT + supports=[] + versionAssessment=UNCERTAIN` 可以成为候选级诊断，候选始终被拒绝。其他版本不确定、verdict/relation 不确定、非法结构/身份/引用和同一 requirement 上的 DIRECT_MECHANISM/CONDITION_CONFLICT 矛盾均全局拒绝。

调用方取得明确的 `globalRejected`、`globalRejectionReasons` 和 `uncertaintySources`，不根据状态文本是否包含 UNCERTAIN 决定准入。全局拒绝时 `evidence` 和 `admittedCandidateIds` 都为空，缺失机制覆盖按实际准入重新计算。原响应的 verdict、versionAssessment、supports 和真实引用校验结果不改写。

该类没有 Spring 注解或生产注册，仅在工具目录。版本适用性和引用的语义关系仍是已有协议下经 Java 校验的声明，不代表 Java 已独立证明任意自然语言蕴含。

输出包含独立安全 Mock 结果、相同历史输入比较、原冻结实现离线回归，以及全量保护文件的前后 SHA。原冻结测试写入新复制的 fixture 根目录。只有 H42-01 存在本轮可回放的真实响应，历史 R02 保持 STOPPED，不能据此形成八题质量指标或整体质量提升结论。

交付记录：`docs/RAG_CANDIDATE_LEVEL_GATE_20261014.md`。
