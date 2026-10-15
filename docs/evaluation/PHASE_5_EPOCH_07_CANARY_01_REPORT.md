# Phase 5 E07-CANARY-01 停止报告

**E07_CANARY_EXECUTION=STOPPED。Gate 0 通过；Gate 1 的有效 Spring Binder 配置 `milvus.enabled=false` 触发环境准入阻断，立即停止。真实推理 0/24、执行案例 0/8、完整配对 0/8、重试 0。**

本轮只获准执行 8 个固定样本的独立 Canary。使用 JDK21.0.11、唯一 `evaluation` profile，`EnvironmentIsolationInitializer` 实际执行，未加载私有 local YAML、未切换 local、未绕过隔离。绑定目标为 MySQL `ai_interview_evaluation`、Milvus `default/knowledge_chunk_vectors_evaluation`，维度1024；但 Milvus 开关为 false。未擅自设置开关、迁移数据或初始化数据库/向量客户端。

## 已完成的独立验证

- 两个授权生产文件 SHA 全匹配：JevJudgeProvider `af2c5182e157e64dae2bc36005d25c0a41b2ff28fd6ee079030418e29cc73615`；EvaluationPromptBuilder `98bfd63ed31a175d0de0615bfe4f1233e3b48a89d5e8f8244716f105a15a3878`。相对 E06 入场源码，生产变化仅为两个 SYSTEM 常量及版本，其他字节不变。
- 独立 Maven 离线编译成功，编译181个生产源文件；20套定向测试实际394项、failures0/errors0/skipped0，全部测试名称与先前XML逐项匹配，原365项保留。未运行全项目回归、Smoke 或生产持久化 E2E。
- 实际重建38个 v3完整Prompt，全部与批准SHA一致；38份USER及Jev请求树不变。FEEDBACK_SYSTEM runtime SHA `883d0e103ce3467be349db6b3b9fbe8a3f7371dfa5b7315b3bfc741180a0e112`。旧E06回放native38接受、feedback33接受/5拒绝，五个旧错误的阶段及异常完全保持。
- Phase5A benchmark SHA `fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a`；Golden、Prompt之外的原输入、历史实验与旧锁没有改写。
- Binder实测 `AI_JUDGE_PROVIDER=jev`、DeepSeek启用、Jev请求模型 `jev-latest`、DeepSeek请求模型 `deepseek-flash`，host/超时/4096 maxTokens一致。API/数据库凭证仅记录SET/MISSING，实际返回模型、HTTP及生成输出尚无在线证据。

## 停止原因与证据边界

预检UTC 2026-10-09T03:57:31.209941900Z 至 2026-10-09T03:57:32.131004400Z。首个实际阻断是 `MILVUS_TARGET_MISMATCH`，具体字段是 `milvus.enabled=false`；不是已证实的MySQL行数错误、Collection缺失或模型质量失败。凭证 presence 有效，但未据此连接真实服务。

MySQL13表、21/97/21/52源数据、Milvus52全向量及8 query真实topK5均未完成本轮读取，标记 **UNKNOWN / NOT_VERIFIED**，不能借用E06 local快照宣称当前evaluation准入通过。数据库写语句0、Milvus写操作0为本轮调用路径记录；缺少实时前后快照，数据保存完整性仍为UNKNOWN。

## 固定分母及执行台账

| 顺序 | 案例 | native/feedback/baseline尝试 | 状态 | 离线已核对v3 Prompt SHA |
| --- | --- | --- | --- | --- |
| 1 | E02-EXCELLENT | 0 / 0 / 0 | NOT_RUN | baf9d21dc033e260775728d3567c48ce67ba9190b6dd844451663994398789fb |
| 2 | E02-OFF_TOPIC | 0 / 0 / 0 | NOT_RUN | fc273b40e49ddce48eaf7292cf9bdd4fad36c023febc2894c82163d47aed5f98 |
| 3 | E02-BOUNDARY | 0 / 0 / 0 | NOT_RUN | aff56ced2aa52d3702c54224f74b3972491cb4b5f311f071b562a2ae058c2938 |
| 4 | H05-PARTIAL | 0 / 0 / 0 | NOT_RUN | 62c1a9028d86419f66dd7666e23f658bc493609d261871c8963105748c2dc14a |
| 5 | H05-BOUNDARY | 0 / 0 / 0 | NOT_RUN | 5889b14f45278762b13ca9ed44dbe93495b580bee6593e322fd2dbfc3b280277 |
| 6 | E02-REPAIR | 0 / 0 / 0 | NOT_RUN | 554ca5f91d64a4c853284aa615c378fec834d774f3fb0f2581772784a11504a0 |
| 7 | H09-REPAIR | 0 / 0 / 0 | NOT_RUN | c14c5f9b08f2b71185c0ec94f8e94363cfec9d77fe8e18cbf4b1d7c3e597473b |
| 8 | H09-FAILED_REPAIR | 0 / 0 / 0 | NOT_RUN | 7d5412812d4c254e8d668291ca835b6773f8118c9d3728d42587d499fe8fbe93 |

三个阶段各首试有效0/计划8，实际尝试均0；实际尝试有效率不可计算，不把0/8解释为模型质量失败或首试成功率。所有HTTP、返回model、finish、token、latency、request/response SHA和Java分数均null/NOT_EVALUATED。未发生would-fallback或真实fallback；未创建在线推理锁。

## 末轮保存核验与交付

入场3598项文件的末轮SHA全部不变；原3079保护清单及1983清单（四项既有v3授权差异单独核对）通过，原3083入场文件除四个既有授权编辑外全保持。E06全部114份raw response、8个历史once-lock的SHA/内容保持，HEAD及完整Git状态相同：`6076a70f18f3062b8faff4fc179cf21f00a95036`。未git add/commit/push，未改生产代码、配置、Prompt、Golden或历史报告。

新增内容只有本轮六份报告/JSON和 `target/phase5-epoch07-canary01/` 内的取证工具、两份独立锁、构建/XML、日志及manifest。文件清单和SHA见 `target/phase5-epoch07-canary01/deliverable-manifest.json`。详细审计见 [STATIC_RUNTIME_AUDIT](judge/PHASE_5B_EPOCH_07_CANARY_01_STATIC_RUNTIME_AUDIT.md)，实测字段见 [RUN_EVIDENCE](judge/PHASE_5B_EPOCH_07_CANARY_01_RUN_EVIDENCE.json)，语义边界见 [SEMANTIC_REVIEW](judge/PHASE_5B_EPOCH_07_CANARY_01_SEMANTIC_REVIEW.md)。

工具诊断完整保留：最初读取historical manifest使用了错误字段名 `sha256`，实际授权编辑字段为 `currentSha256`；另一次Git状态格式比较使用了 `--untracked-files=all`，旧报告使用默认格式，产生工具STOP旗标。按相同参数独立比较完全一致，逐文件SHA也无漂移；保存原审计及解释，不覆盖旧证据。两次均发生在任何服务初始化前，只修正E07取证工具，无模型重试。实际Gate1阻断没有修复或重跑。

E06严格质量FAIL和三个历史UNCERTAIN保持。E07没有在线结果，不能宣称五个旧失败或H05-PARTIAL假遗漏已解决；也不构成同Prompt因果对照。下一步由Commander审阅Milvus开关与evaluation专用数据就绪条件，另行裁定新的一次执行授权；本轮不自行继续。

```text
E07_CANARY_EXECUTION=STOPPED
E07_CANARY=STOPPED_PENDING_COMMANDER_REVIEW
PLANNED_CASES=8
EXECUTED_CASES=0
FULL_PAIRS=0/8
REAL_INFERENCE_CALLS=0
RETRIES=0
PHASE5_FULL_QUALITY=NOT_EVALUATED
PHASE5C=NOT_RUN
PHASE6=BLOCKED
```
