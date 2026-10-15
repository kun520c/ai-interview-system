# Phase 5 E04 Judge 验证报告

本轮结论：配置迁移与运行环境PASS；第二条E02-PARTIAL独立DeepSeek baseline出现真实JSON协议失败，安全STOP。EXECUTION FAIL、PROTOCOL FAIL、QUALITY NOT_EVALUATED、INTEGRITY PASS；Phase5C NOT_RUN，Phase6 BLOCKED。完整配对仅1/38，不能宣称Phase5质量通过。

E04开始UTC 2026-10-08T07:33:12.632487600Z；最终运行结束UTC 2026-10-08T07:42:29.750591800Z。首例E02-EXCELLENT已完成独立新鲜三次调用：Jev95、DS baseline93，均EXCELLENT / FINAL / NEXT_MAIN、coverage4/4。第二例Jev原生53（INITIAL/FOLLOW_UP targets2482、2483）及反馈通过；独立baseline HTTP200且实际model匹配，却含未转义双引号，原DeepSeekEvaluationClient.parseSuggestion拒绝。离线捕获重放复现相同异常；额外HTTP=0。

| 状态维度 | 结论 |
| --- | --- |
| Model config / Binder / identity | PASS：请求与DS实际返回均deepseek-flash；JeV请求jev-latest、真实返回jev-1.13.0 |
| Gate0 compile / focused offline | PASS：242 tests /0 failures /0 errors /0 skipped，compile BUILD SUCCESS |
| Gate1 MySQL/Milvus/RAG只读预检 | PASS：21 questions /97 points /21 documents /52 chunks /52 vectors，1024维、topK5 |
| E04 first-case原门禁 | PASS，三次请求仅各一次；不复用E03模型输出 |
| Full execution / protocol | FAIL：第二条baseline非法JSON，未重试，未继续case3 |
| Full 38质量门禁 | NOT_EVALUATED：1/38完整配对、Jev2/38、baseline1/38 |
| Integrity | PASS：480原文件，授权配置之外差异0；13表与52完整向量摘要一致 |
| 5C / Phase6 | NOT_RUN / BLOCKED |

实际调用：DeepSeek GET /models1（HTTP200，available deepseek-flash / deepseek-v4-pro）；JeV POST2；DS feedback2；独立DS baseline2（第二次解析失败）；质量fallback0；受控fallback0；Embedding HTTP0；Milvus search0。没有模型HTTP重试、gold/Prompt变化、隐形降级或业务写入。

只修改生产文件 `src/main/resources/application.yaml:59` 的模型缺省值。本轮开始已有用户改为deepseek-v4.1-flash；HEAD原为deepseek-v4-flash，分别记录，本轮只改为授权deepseek-flash。Windows User/Process/Machine模型变量MISSING；保存的IDEA配置与local/evaluation profile无覆盖；未修改永久Windows环境。真实JDK21.0.11 / local / Spring Environment/Binder解析deepseek-flash、enabled=true、JEV。保留3s/60s、4096 tokens、JeV3s/20s、minConfidence0和其他生成参数。生产Java、Jev舍入补丁、测试和原数据没有修改。

| 摘要项 | SHA-256 |
| --- | --- |
| application.yaml E04开始 | `f5b823b87c95c0a1f8f48db923825a4befbaba8011b6eaa7021c41898f721dea` |
| application.yaml E04结束 | `d4b410386d16b2e6e4b8f846c60f863ccd3df72adc4abbb0af0a2f42b9bbec8a` |
| 冻结benchmark | `fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a` |
| 不变Jev补丁 | `d79eaa50533ea34be605c453de90dfb2010946440223a874ce2e98835dd28fa4` |
| 首次调用前临时runner | `fac58be5d7d636c6e1d7ac35aa7dcfcf5a8c29eb330111dc7473b4153e683b82` |
| 最终实际执行临时runner（修正实验辅助问题） | `628830ae7a26150609aca461d7727466426d97de9b53c58d27e9385c0ff5a82e` |
| 不合法baseline内容 | `5f22e991b0b3b07f35d5531690e253423828a5f4f6d5f1a6e0efff0c0072e5e8` |

代码HEAD为 `6076a70f18f3062b8faff4fc179cf21f00a95036`；已有未提交工作保留。480文件完整前/授权基线/后SHA在target/phase5-epoch04三份integrity JSON，数据库13表逐表全部行SHA和Milvus全52向量SHA在RUN_EVIDENCE。E01/E02/E03文件均经该清单核验保持原样；原事故不被改写。没有git add/commit/push，本轮结果是本地配置与报告，尚未暂存/提交/推送。

测试使用清除真实API Key、RUN_REAL_*、MILVUS_ENABLED及线上模型开关的独立离线子进程，未进行线上调用；原六套201项，加DeepSeek四套41项。以下是本次实际数量，不借用历史预期：

| Suite | Tests | Failures | Errors | Skipped |
| --- | --- | --- | --- | --- |
| JevScorePrecisionCompatibilityTest | 63 | 0 | 0 | 0 |
| JevJudgeProviderTest | 46 | 0 | 0 | 0 |
| JevClientTest | 17 | 0 | 0 | 0 |
| JevScoreMappingTest | 16 | 0 | 0 | 0 |
| JudgeConfigurationTest | 12 | 0 | 0 | 0 |
| LlmEvaluationValidatorTest | 47 | 0 | 0 | 0 |
| DeepSeekConfigurationTest | 2 | 0 | 0 | 0 |
| DeepSeekEvaluationClientTest | 19 | 0 | 0 | 0 |
| DeepSeekPropertiesTest | 19 | 0 | 0 | 0 |
| DeepSeekJudgeProviderTest | 1 | 0 | 0 | 0 |

本次只执行上述定向测试和compile，未运行普通全量mvn test；本报告不把Mock/单元测试当作真实Judge成功。真实成功证据只有明确列出的HTTP与有效结果。Jev两例TP6/FP0/FN0、exact2/2；baseline一例TP4/FP0/FN0、exact1/1；完整38指标NOT_EVALUATED。Jev排序只可核验1/24（95>53），低信息/安全/FOLLOW_UP尚未运行。usage逐次原字段已存证，无实际扣费信息，不估算成本。

临时检查器曾将 fast-track 原第24节（Jev反馈专用）的连续原文约束错误套用于独立 baseline，并在续跑预检因 IntNode/LongNode 类型差异误报数据变动。两次均保留原 runner、日志、证据的原字节副本；后者在任何剩余样本POST之前发生。修正仅限 target 临时实验检查器，不修改任何生产 Java、Prompt、gold、参数或原门禁。首例既有三次新鲜 E04 响应按原 provider-specific 合同重新独立复核后合格，没有重发；独立 baseline 的解释型 evidence 仍逐点审查候选来源和硬矛盾。最终STOP是第二baseline被原生产 parseSuggestion 拒绝，与这两项临时错误不同。

文件与用途：

- `src/main/resources/application.yaml`：唯一生产修改，授权模型缺省值更新。
- `docs/evaluation/PHASE_5_EPOCH_04_JUDGE_VALIDATION_REPORT.md`：本汇总与状态。
- `docs/evaluation/judge/PHASE_5_E04_MODEL_MIGRATION_REVIEW.md`：配置覆盖、diff、离线tests/SHA与迁移审阅。
- `PHASE_5B_EPOCH_04_RUN_EVIDENCE.json`：六次真实推理调用、GET、原始响应、快照、hash、独立审阅与诊断。
- `PHASE_5B_EPOCH_04_JEV_RESULTS.json`：两条合格原生JeV结果，非完整质量集。
- `PHASE_5B_EPOCH_04_DEEPSEEK_BASELINE_RESULTS.json`：仅一条合格baseline；第二条非法内容只在原始事件存证。
- `PHASE_5B_EPOCH_04_COMPARISON_REVIEW.md`：部分指标、全部门禁分母、协议错误与P0/P1/P2。
- `PHASE_5C_EPOCH_04_FALLBACK_PROOF.md`：诚实记录NOT_RUN及零次fallback。
- `target/phase5-epoch04/`：独立临时runner、受限恢复脚本、离线日志/XML清单、每例Codex审阅、捕获重放诊断与两次临时检查器误停的不可覆盖副本。

剩余问题：P0 E04-DS-JSON-001（真实非法JSON）；P1完整评测证据缺失；P2临时检查器两项错误已修正并保留。建议Commander审阅完整证据后再授权新epoch或明确修复范围。不会自行修改生产JSON解析、加重试/自动修复、换模型/参数、降低质量标准或进入Phase6。
