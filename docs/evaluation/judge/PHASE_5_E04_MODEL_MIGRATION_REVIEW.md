# Phase 5 E04 模型配置迁移审阅

Gate 0/1 本地运行预检：PASS。线上模型发现和质量 POST 尚未开始；本记录不代表 Phase 5 Judge 质量通过。

授权依据：用户指定的 PHASE_5_E04_DEEPSEEK_MODEL_UPDATE_AND_JUDGE_VALIDATION.md。只修改 `src/main/resources/application.yaml:59` 的缺省模型值为 `deepseek-flash`。HEAD 缺省值为 `deepseek-v4-flash`；E04 开始时已有用户未提交改动 `deepseek-v4.1-flash`，已单独记录。E04 的增量只替换该字段一次，保留其他字节与原有工作树。

| 项目 | 值 |
| --- | --- |
| E04 变更前 application.yaml SHA-256 | `f5b823b87c95c0a1f8f48db923825a4befbaba8011b6eaa7021c41898f721dea` |
| E04 变更后 application.yaml SHA-256 | `d4b410386d16b2e6e4b8f846c60f863ccd3df72adc4abbb0af0a2f42b9bbec8a` |
| Jev 补丁 SHA-256（保持原样） | `d79eaa50533ea34be605c453de90dfb2010946440223a874ce2e98835dd28fa4` |
| benchmark SHA-256（保持原样） | `fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a` |
| 代码 HEAD（不代表当前脏工作树已发布） | `6076a70f18f3062b8faff4fc179cf21f00a95036` |
| 实验临时 Java runner SHA-256 | `fac58be5d7d636c6e1d7ac35aa7dcfcf5a8c29eb330111dc7473b4153e683b82` |
| 原有文件审计 | 480 个；配置授权差异已记录，授权基线之外差异 0 |

Windows User/Process/Machine 的 `DEEPSEEK_MODEL` 均 MISSING。已保存 IDEA XML 的环境变量、VM options、program parameters 无模型覆盖；未检查 IDEA 未保存的临时运行状态。application-local.yaml 与 application-evaluation.yaml 无此字段覆盖。子进程仅临时恢复 User 凭证，未修改 Windows User/Machine 环境，也没有用临时模型覆盖掩盖默认值。真实 Spring Environment/Binder（local profile，JDK 21.0.11，注册的 EnvironmentIsolationInitializer）解析到 `https://api.deepseek.com` / `deepseek-flash` / enabled=true；ai.judge.provider=JEV，Jev=jev-latest，minConfidence=0。

原参数保持：Jev connect/read=3s/20s；DeepSeek connect/read=3s/60s，maxTokens=4096；embedding=qwen3.7-text-embedding，1024 维；topK=5。本次不修改 Java Provider、客户端、协议阈值、反馈 Prompt、temperature、RAG、gold、题库、数据库或集合。

本任务明确使用 local 的 ai_interview 和 default/knowledge_chunk_vectors_local；只读快照覆盖 13 张 MySQL 表及全部 52 个 Milvus 向量。21 questions /97 points /21 documents /52 chunks /52 vectors 均满足。E02 RAG 的8组 query hash、top5 evidence、38个冻结 Prompt及gold经全量内容摘要比对可复用；没有复用 E03 模型输出。运行预检阶段 Embedding HTTP=0、Milvus search=0、线上模型 HTTP=0。

离线验证使用未恢复真实 API Key 的独立子进程，清除 RUN_REAL_*、MILVUS_ENABLED 和线上模型开关；Maven `-o -B -ntp` 定向测试和 skipTests compile 均 BUILD SUCCESS。历史六套 201/0/0/0；追加 DeepSeek 四套41/0/0/0；总计242 tests /0 failures /0 errors /0 skipped。没有修改测试期望。本次未执行全量 mvn test，也不把这些 Mock/单元测试算作真实 Judge 成功。

| Suite | Tests | Failures | Errors | Skipped | Surefire XML SHA-256 |
| --- | --- | --- | --- | --- | --- |
| JevScorePrecisionCompatibilityTest | 63 | 0 | 0 | 0 | `462acd3c6dff1c5833665c385eb504d33af889693e92e946803418475385ac13` |
| JevJudgeProviderTest | 46 | 0 | 0 | 0 | `a784abd0b81815b1fac6d5fc6324ad8f066acb2b1f94375661fcec20fc188966` |
| JevClientTest | 17 | 0 | 0 | 0 | `de88fbb760e682cee51d301fabdec7731683dfdb5ec81a64e28b4b9847257482` |
| JevScoreMappingTest | 16 | 0 | 0 | 0 | `2ebb04b42b04eb3f0f6cca4db9192dc14bee400f4057367e3716eed91278d651` |
| JudgeConfigurationTest | 12 | 0 | 0 | 0 | `6feb8c13eac701ce5bf07f350b5a18e6a48e5b1c303952dd14823fa0449c31a7` |
| LlmEvaluationValidatorTest | 47 | 0 | 0 | 0 | `97f1099baaf52f03e6561d164e2c3cb061cb9914915d92a84966a8e74081ee08` |
| DeepSeekConfigurationTest | 2 | 0 | 0 | 0 | `b095ca0b321068dd8fa226b41a53b5043c5bda60d1f06ab6c1ee1fa30a5b832e` |
| DeepSeekEvaluationClientTest | 19 | 0 | 0 | 0 | `31f9ebbb5d667f5f71fd127995d69a75cd3f9e2f72a56864de31844edb4757f1` |
| DeepSeekPropertiesTest | 19 | 0 | 0 | 0 | `c7210bd67d70923f729889af0ef52ffbe4d42c5a67bd77e1dfab8ccee2bc01d6` |
| DeepSeekJudgeProviderTest | 1 | 0 | 0 | 0 | `510bb5da09cb3ffc67e2fd74c1fdc50d73c3b8dab2dbdb8e6e0efaffc146a182` |

临时实验 runner 首次 javac 出现2个泛型类型推断错误，限于捕获响应的离线诊断辅助代码；已在任何线上调用前修正，重新 javac 成功。生产编译和上述242项测试均未失败，生产 Java 未因该问题改动。

调用预算已在首次质量调用前冻结：模型 GET 至多1；38例每例 Jev primary 1、DeepSeek feedback 1、独立 baseline 1；条件5C仅本地Jev500一次和真实DeepSeek fallback一次；失败样本重试0。临时 runner 在每个有效 primary/baseline 之后等待 Codex 独立语义复核文件，确认原文证据、分数、Java决策和无硬反馈矛盾后才允许下一次调用；这是代理执行检查，无用户重复确认。未知 Jev family、DeepSeek返回身份不一致或任何HTTP/协议异常均安全STOP。

官方模型与模型发现参考：[DeepSeek官方文档](https://api-docs.deepseek.com/zh-cn/)、[官方模型列表API](https://api-docs.deepseek.com/api/list-models)。网页说明是迁移依据；E04的模型身份验收以本次真实响应为准。

源码/历史文件的完整480项SHA在 `target/phase5-epoch04/integrity-before.json` 和 `integrity-authorized-baseline.json`；预检、XML明细与调用预算存于同目录。后续线上状态见 E04 独立 RUN_EVIDENCE 和汇总报告。未执行 git add/commit/push；Phase6未授权。


## E04 线上结案补充

配置和身份验收真实PASS：GET一次HTTP200、DS两次feedback与两次baseline均request=return=deepseek-flash；Jev两次jev-latest→jev-1.13.0。第二baseline内容非法JSON被原生产解析拒绝，E04协议FAIL、完整质量NOT_EVALUATED；5C未运行，Phase6未授权。此前“尚未线上调用”描述是门禁冻结时点。

临时检查器曾将 fast-track 原第24节（Jev反馈专用）的连续原文约束错误套用于独立 baseline，并在续跑预检因 IntNode/LongNode 类型差异误报数据变动。两次均保留原 runner、日志、证据的原字节副本；后者在任何剩余样本POST之前发生。修正仅限 target 临时实验检查器，不修改任何生产 Java、Prompt、gold、参数或原门禁。首例既有三次新鲜 E04 响应按原 provider-specific 合同重新独立复核后合格，没有重发；独立 baseline 的解释型 evidence 仍逐点审查候选来源和硬矛盾。最终STOP是第二baseline被原生产 parseSuggestion 拒绝，与这两项临时错误不同。

初始及两次修订runner SHA、原误停副本及零额外请求证据均见独立RUN_EVIDENCE。本次未降低或修改原验收标准。
