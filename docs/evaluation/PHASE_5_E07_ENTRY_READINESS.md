# E07 准入准备与小样本提案（NOT_RUN）

本轮为 Commander 授权的离线取证与文字加固。真实模型/Embedding API 0，数据库与Milvus客户端初始化0、写入0；E07未运行、5C NOT_RUN、Phase6 BLOCKED。E06 原严格门禁 FAIL，38案例/114推理/零重试、native38/38、baseline38/38、联合反馈及配对33/38保持不变。

**仅完成离线准入材料，等待Commander审核与新的真实canary授权；不能自动启动E07。** 两个v3 system文字和394项离线测试已完成，Gate A未发现新报告冲突。三项原UNCERTAIN仍待裁定，v3实际模型服从/质量/时延/费用未验证，当前服务运行时门禁本轮未检验。

## 先≤8独立新epoch canary

建立新的独立canary epoch/目录/once-lock，不复用E06响应、不在E06重试、不重新编号历史。暂按冻结benchmark相对顺序取八例：五个旧失败 + 两个已成功对照（MAIN边界与FOLLOW_UP修复）+baseline H05-PARTIAL语义复核。

| case | 用途 | 冻结context SHA | 拟使用v3完整Prompt SHA |
| --- | --- | --- | --- |
| E02-EXCELLENT | 旧非连续quote | 1cf6e8cd3c7967e66cfbbb5dc2701807db6b00482fa6080bef48e3db9fdc9d13 | baf9d21dc033e260775728d3567c48ce67ba9190b6dd844451663994398789fb |
| E02-OFF_TOPIC | 旧非法JSON | e9a01653603d51f0cc99ed965f9602cdd939fe20fd59834ff067ed5cb6abfaa9 | fc273b40e49ddce48eaf7292cf9bdd4fad36c023febc2894c82163d47aed5f98 |
| E02-BOUNDARY | 成功MAIN对照 | 2eedfe37aea5d84111c44c554d70bb45c11bad6535fb0be622acae206459534d | aff56ced2aa52d3702c54224f74b3972491cb4b5f311f071b562a2ae058c2938 |
| H05-PARTIAL | baseline确认假遗漏复核 | 8b5f28118452cf9a148a19699968e6ed2964645a56029803fa8a3d634e0cffda | 62c1a9028d86419f66dd7666e23f658bc493609d261871c8963105748c2dc14a |
| H05-BOUNDARY | 旧非法JSON | e5e8c0e982c6519b9e6ba8a3533e9832ad63da1c54b050f37dc46e993354f2ee | 5889b14f45278762b13ca9ed44dbe93495b580bee6593e322fd2dbfc3b280277 |
| E02-REPAIR | 旧MAIN空quote | de11e5d43852d1294d45fdcad475388be04932c8de7a100ef0e576857b4a6997 | 554ca5f91d64a4c853284aa615c378fec834d774f3fb0f2581772784a11504a0 |
| H09-REPAIR | 成功FOLLOW_UP全ID/双来源对照 | a2e4df68ba620257ec309c1fff6fb1aa46677e45045bbd9971887abb687e0d97 | c14c5f9b08f2b71185c0ec94f8e94363cfec9d77fe8e18cbf4b1d7c3e597473b |
| H09-FAILED_REPAIR | 旧MAIN null quote | 7d292e4f5483fadbd75c40c2743f089d6caf9f596619228c9fff96733127e30a | 7d5412812d4c254e8d668291ca835b6773f8118c9d3728d42587d499fe8fbe93 |

所有case/rubric/reference/gold/USER/RAG原样，benchmark SHA `fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a`。case/rubric/gold原完整内容及context hash、旧/新Prompt hash见 `target/phase5-e06-offline-forensics/canary-proposal-NOT_RUN.json`；实际运行前必须用冻结原序列化约定重算，而不是将新hash口径替代旧case SHA。拟版本jev-judge-v3/evaluation-prompt-v3；FEEDBACK_SYSTEM SHA `883d0e103ce3467be349db6b3b9fbe8a3f7371dfa5b7315b3bfc741180a0e112`。Jev仍jev-latest并审计返回模型（E06实际jev-1.13.0），DeepSeek仍deepseek-flash，所有参数与数值规则冻结，发现漂移STOP。

默认每case路径一次：native Jev1 +feedback DeepSeek1 +独立baseline DeepSeek1，最多24次推理，提前STOP则更少。真实harness由Commander另行授权后再建立/审核，本轮没有创建可执行E07在线runner、没有创建E07锁、没有探针或模型GET。既有fallback不得偷偷混入judge质量样本；若质量采集触发fallback应记录would-fallback并STOP，不能用产品兜底冒作Jev通过。

## 真实执行前必须通过的门禁

Commander先审核四类取证及三个完整争议，确认最小文字diff、版本/hash和394测试证据；另行批准canary预算/新epoch。运行时按当前AGENTS/EnvironmentIsolationInitializer实际解析环境，AI baseline采用evaluation profile与专用MySQL/Milvus目标；历史E06 local启动信息不作为新的启动授权。若目标没有同样冻结知识/题库/向量/RAG证据或源码hash不符，STOP交Commander，不绕过隔离、不迁移/补写数据。复用RAG需重新证明源数据、向量、query/top5和检索代码一致，不把旧DB摘要当现在的预检结果。

实测前保存readonly数据/向量与文件/锁快照、完整实际请求非秘密参数和system/USER hash；真实凭证只原位内存使用，不入报告/fixture/header日志。native/feedback/baseline各保留request+response SHA、返回model、finish_reason、HTTP、时间/usage/无重试证据。所有现有严格JSON、exact quote、完整ID、数值/legend/choice/confidence校验保留。

## 指标与停止条件

首试成功率以8个计划case为分母分别记录native、联合反馈、独立baseline及paired；另列executed与NOT_RUN，不将提前停止未执行样本删除。当前未批准重试，retry count=0、retry-success metric=NOT_RUN，禁止把它写成首试100%。以后若获批最多1结构重试，另列first-pass失败与after-retry通过，不能覆盖初始raw/分类。

任何HTTP/模型/请求次数异常、native严格拒绝、feedback JSON/schema/quote/all-ID无效、候选错归因/假遗漏/纠正已正确事实、秘密泄露、数据/向量/hash变化、预期外源码或冻结数据变化即STOP。人工语义审阅不能由“结构VALID”自动代替：全部8个case两路成功文本逐字段审阅，H05-PARTIAL重点比较已回答代理与外部调用；不能确认的归因也先停交Commander。新语义报告不改E06的确认/争议分类。

canary全通过仅表明小样本本次符合要求，不等于38全量或Phase5B PASS。提交完整canary报告，再由Commander新授权决定是否全38；不能自动重复114次，不以113/114或补一个失败例为目标。全量另建独立epoch，38每路径首试各一次，记录最多114个尝试；不混用canary输出补满全量、不在旧epoch重试。任意停止条件仍优先预算。

## 原严格门槛全部保留

完整38/38结构有效、38配对是必要条件，全部原质量指标还须通过：micro P/R/F1各≥0.90、exact set≥0.75、排序≥22/24、excellent≥75、UNKNOWN/OFF_TOPIC/SECURITY≤35、低信息及注入假coverage=0、全部4修复及8FOLLOW_UP完整性/Java决定按原规则验证、hardFeedbackContradictions=0，材料性比较F1下降限0.05/exact下降限0.10按原协议。未知/争议不自动记PASS。

```text
{
  "microPrecision": 0.9,
  "microRecall": 0.9,
  "microF1": 0.9,
  "exactSetMatch": 0.75,
  "orderingMinPass": 22,
  "orderingCount": 24,
  "lowInformationAndInjectionFalseCoverage": 0,
  "excellentMin": 75,
  "unknownOffTopicSecurityMax": 35,
  "hardFeedbackContradictions": 0,
  "materialF1Drop": 0.05,
  "materialExactDrop": 0.1
}
```

Jev primary与baseline分别算质量，产品fallback可用性另算；不能以fallback/baseline补Jev结果或以Mock替代真实5C。E06与未来v3不构成严格单因素A/B能力证明：两个system文字变更、供应商随机性/服务实现皆可能影响。Phase5C原受控fallback规则不变且NOT_RUN，Phase6 BLOCKED，均须Commander新授权。

## Commander需要的审阅包

- [五例根因及完整语义争议](judge/PHASE_5_E06_FAILURE_ROOT_CAUSE_REVIEW.md)：raw位置/SHA、实际v2 system、全MAIN/FOLLOW_UP及评分点；71份逐字段重读另附semantic-rereview.json。
- [分项修复与重试/span提案](judge/PHASE_5_E06_FEEDBACK_FIX_OPTIONS.md)：已做与未实现边界、成本/幂等/产品fallback差别。
- [最小补丁diff与SHA](judge/PHASE_5_E06_PROMPT_HARDENING_PATCH_REVIEW.md)：4项授权编辑、2新增测试文件、38新旧Prompt hash、历史前后摘要。
- [394测试与原响应仍拒绝](judge/PHASE_5_E06_PROMPT_HARDENING_TEST_RESULTS.md)：真实Surefire数量/XML SHA、独立离线compile/replay日志，Mock不算真实验证。
- `target/phase5-e06-offline-forensics/deliverable-manifest.json`与`final-file-manifest.json`：五份新报告SHA、所有本轮及原dirty/untracked文件SHA、入口相对diff和历史归档前后SHA。

完成/编译/Mock验证：DONE；真实v3验证：NOT_RUN；staged/committed/pushed：均未执行。本轮结束，**STOP_WAIT_COMMANDER**。
