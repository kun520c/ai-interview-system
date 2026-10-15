# Jev 组合评分 E06 最终缺陷收口

2026-10-14。仅离线复验原始证据；本轮没有新增生产或测试代码修改，没有真实模型调用、数据库/Milvus访问、E2E重跑或 Git 提交/推送。

```text
JEV_COMPOSITE_ROOT_CAUSES=2_INVALID_FEEDBACK_JSON;1_NONCONTIGUOUS_QUOTE;2_MISSING_MAIN_COVERED_QUOTES
PRODUCTION_CODE_DEFECTS=0_CONFIRMED_IN_THE_5_CASES
OFFLINE_REPAIR_STATUS=COMPLETE_NO_NEW_REPAIR_REQUIRED_STRICT_GATES_PRESERVED
E06_REPLAY_RESULT=NATIVE_38_OF_38;COMPOSITE_33_OF_38;EXPECTED_REJECTIONS_5_OF_5
REMAINING_MODEL_QUALITY_ISSUES=5_FEEDBACK_PROTOCOL_VIOLATIONS_NOT_RECLASSIFIED_AS_SUCCESS
```

完整核验了 E06 的 38 份原始 Jev 响应及 38 份对应 DeepSeek 反馈，76 份 SHA-256 全部匹配冻结事件。当前生产代码按冻结上下文和原响应复验，成功 33 例，其余 5 例在与历史相同的阶段被拒绝。复验使用的 Judge、PromptBuilder 和 Validator 编译字节与本轮新隔离构建一致。

| 失败案例 | 原始缺陷与定位 | 分类与 Java 判定 | 原始反馈证据 |
| --- | --- | --- | --- |
| E02-OFF_TOPIC | 外层合法，内层 JSON 键/分隔符损坏；严格解析定位第 1 行第 69 列 | 模型格式协议违规；PARSE 拒绝正确，不是解析器缺陷 | [14-E02-OFF_TOPIC.response.txt](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/phase5-epoch06/http/14-E02-OFF_TOPIC.response.txt) |
| H05-BOUNDARY | 内层出现 `"scoringPointId=2552,` 等损坏键；第 1 行第 775 列 | 模型格式协议违规；PARSE 拒绝正确，不补引号或转宽松 JSON | [62-H05-BOUNDARY.response.txt](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/phase5-epoch06/http/62-H05-BOUNDARY.response.txt) |
| E02-EXCELLENT | 2480 改标点并拼接；2481 插入省略号并删改内容，两段都不是回答中的连续子串 | 模型引用协议违规；MERGE 真实性校验正确拒绝 | [2-E02-EXCELLENT.response.txt](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/phase5-epoch06/http/2-E02-EXCELLENT.response.txt) |
| E02-REPAIR | Jev 将 2480/2481 判为 covered，但反馈引用为 `""`；FOLLOW_UP targets 为 2482/2483，不能因此丢掉 MAIN 证据 | 模型漏证据；MERGE 对空引用的拒绝正确 | [92-E02-REPAIR.response.txt](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/phase5-epoch06/http/92-E02-REPAIR.response.txt) |
| H09-FAILED_REPAIR | Jev 将 2569/2570 判为 covered，但反馈引用为 `null`；FOLLOW_UP targets 为 2571/2572 | 模型漏证据；MERGE 对 MAIN 已覆盖点的空引用拒绝正确 | [113-H09-FAILED_REPAIR.response.txt](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/phase5-epoch06/http/113-H09-FAILED_REPAIR.response.txt) |

五例反馈均为 HTTP200、`finish_reason=stop`，历史请求已设置 `response_format=json_object`，并要求 JSON 与回答原文证据。没有传输截断或缺少全部协议指示的证据；无法进一步确认服务商内部生成/后处理成因。当前已有 v3 Prompt 对合法 JSON、逐字连续引用、所有 MAIN 评分点及非空证据作了明确约束，本轮不重复改写，也不宣称其线上输出质量已提升。

保留既有行为：非法 JSON 不修补；covered 不改写；引用必须非空且逐字连续存在于 MAIN 或 FOLLOW_UP 的其中一份回答；禁止拼接、改标点、空白/Unicode归一化或自动填证据。正确拒绝可沿现有 `ExternalServiceException → DeepSeekJudgeProvider` 路径降级；本轮只用 Mock 验证一次降级，不发送实际降级请求。**历史 E06 的实际 fallback 为 0，33/38 与冻结质量门禁 FAIL 保持不变，不能改称 38/38 成功。**

定向离线回归：`JevFeedbackPromptHardeningTest` 29、`JevJudgeProviderTest` 49、`DeepSeekEvaluationClientTest` 19、`FollowUpOutputCompletenessHardeningTest` 2，共 **99 测试，0 失败、0 错误、0 跳过，BUILD SUCCESS**。覆盖五份原反馈拒绝、独立合规 Mock 成功、连续原文/空/null/改写边界、MAIN ID 完整性、严格 JSON 和单次降级。合规 Mock 未替换历史反馈，不计入 E06 的 33/38。

本轮只新增此报告和新的隔离取证/构建目录。对历史 E06、原离线取证档案、既有执行锁及相关生产源文件共 **4,243 文件**做前后哈希核验，全部未变；生产评分标准、数据库、Milvus和默认 RAG 策略未修改。

必要证据：

- [本轮38例回放及5例拒绝位置](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/jev-composite-closeout-20261014/replay.json)
- [五例原始文件、哈希、空引用及连续子串检查](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/jev-composite-closeout-20261014/failure-evidence.json)
- [99项定向测试日志](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/jev-composite-closeout-20261014/tests.log)
- [4,243文件完整性结果](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/jev-composite-closeout-20261014/integrity-after.json)
- [冻结E06原始运行记录](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/evaluation/judge/PHASE_5B_EPOCH_06_RUN_EVIDENCE.json)及[原Java拒绝日志](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/phase5-epoch06/invalid-diagnostics.json)

本轮工程缺陷核查已收口；剩余风险是模型反馈不遵守格式或证据协议，继续由严格校验和既有降级边界处理，不为达到100%放宽判定，不申请新的收费调用。
