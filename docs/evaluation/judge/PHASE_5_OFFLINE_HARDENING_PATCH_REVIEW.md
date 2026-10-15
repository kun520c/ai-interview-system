# Phase 5 离线专项修复审阅（Gate B/C）

状态：**已编码、已离线编译、已通过Mock/单元测试；尚未真实服务验证、未暂存/提交/推送。** 此次生产改动只有JevJudgeProvider.java和EvaluationPromptBuilder.java。原配置、DeepSeekClient、validator、评分/追问业务规则、DB schema、gold及RAG不改。

## 问题与改法

旧数值检查在sum严格一致后才允许cent-score期望兼容；实际三个邻接binary64 token不满足“十进制精确cent”资格，两个sum0.99被前置门禁拒绝。五例原始字节已核实、25维有独立守恒证书，5个失败维度有显式HALF_UP witness后才实施修复。

原strict通道保留。限定returned model=jev-1.13.0的score分支允许cent或一相邻数参与精确十进制量化区间证明，先保证潜在概率总质量1，再求期望极值与score区间交集。无需扩大sum阈值，无normalize、原raw数字不改；仍保留非null/有限/[0,1]、keys/type/legend/rubric、score[0,4]、confidence门禁、choice原sum/argmax、反馈merge、严格JSON与一次fallback。原域边界截断和闭half-step端点保留，真实更高精度不能借cent预算。

反馈和baseline只强化文本约束，版本递增。FOLLOW_UP system动态完整ID列表不改变schema；不替模型补结果，不添加Java散文过滤/事实纠正，不改主问题生成、评分维度或状态决策。38 USER及Jev原生请求树相同。

## 本次相对E05入口文件diff

Git HEAD前已存在批准的舍入补丁、模型default更新及其他用户修改；不能将Git HEAD的全部dirty diff归为本轮。本轮逐文件比对入口快照，仅下面5个已有文件+3个新测试/fixture改变。完整review diff在target/phase5-offline-hardening/patch-from-e05-entry.diff，old/new source SHA如下。

| 文件 | 操作 | 用途 | 入口SHA → 本轮SHA |
| --- | --- | --- | --- |
| `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java` | 修改 | 版本限定的网格资格/联合守恒可行域；Jev反馈文本与版本 | `d79eaa50533ea34be605c453de90dfb2010946440223a874ce2e98835dd28fa4` → `2f6286e3f59337054b9973d32a7cc8e01ce1805c9a09443aa476144cd7c1b4f1` |
| `src/main/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilder.java` | 修改 | 候选事实归因约束；FOLLOW_UP动态完整MAIN ID检查；版本 | `d85385c5057a03626e9ae0fd52d0e2940296fbe9e69c807acc5f19eb33108e89` → `df7b5f99f90f6621a2370e505d88f8ac46ef51c8b3b04643802922d2a5549ae9` |
| `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevScorePrecisionCompatibilityTest.java` | 修改 | 保留63项；更新一个有witness的授权合同预期 | `ebab2b2ccb5bff0a1e7e3ba921dd3cf63471aa87618660df02e11691db2f1a5d` → `12bdac2cabb1727054ce9ec2e46a464c74d27c9af5b2bc0f932436dac6522486` |
| `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProviderTest.java` | 修改 | 版本断言v2；新增反馈作用域与rubric不变检查 | `da5b6b37fd8fc995e953adff2c3bafbb787515cab68ef3618cf350cd52ca2caa` → `ba5a7037f10260a1d72742934a8b08bf405b452d23d3cff70b3ab833f4a96946` |
| `src/test/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilderTest.java` | 修改 | 版本断言v2；新增两模式候选归因和全MAIN ID约束检查 | `cd2f69620e5f04eb4f8ffd17ca9ee2c6fafcb60b45b23a6e9fd219181da3904a` → `853e736fa4c49f2d0e46cdb1c595eb29e9f70b940dfc3090f1c8327d96fb6460` |
| `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevDisplayQuantizationHardeningTest.java` | 新增 | 32项原数值回放/ULP/守恒/反例测试 | `无` → `5cbb1d14fd848fc024b7335ac924ba8010722b8a1c5abdaa790e8fe1c51055e0` |
| `src/test/java/com/kun/aiinterview/interview/evaluation/prompt/FollowUpOutputCompletenessHardeningTest.java` | 新增 | 2项真实Client内存HTTP测试，仍拒绝仅targets输出 | `无` → `4234d972dca7f15b06ace7ab1d7530ff0e4558c954dcdb6e63ee82859e061e5e` |
| `src/test/resources/evaluation/jev-e05-numeric-captures.json` | 新增 | 5份纯数值捕获fixture，不含candidate/gold/凭证 | `无` → `979eb210b0906bf6d5ddc4b84c3d09fb9499e61b8de4c770315fae044a527dff` |

测试合同变更透明说明：原JevScorePrecisionCompatibilityTest的四个sum案例全部保留；+0.00011、−0.00011仍因真实高精度不一致拒绝，−0.01仍因score域不相交拒绝，只有+0.01在明确p*=(.00125,0,.015,.405,.57875)、Σp*=1、E=3.56、按HALF_UP复现显示概率后按**本轮授权新合同**改为接受，且新增witness断言。没有删、跳过或减弱其他原测试。两个旧prompt版本字面断言按新版本更新，仍检查精确版本。

## 离线证据与限度

- JDK21 offline Maven compile：181生产Java文件，BUILD SUCCESS。
- 定向19套：tests365/failures0/errors0/skipped0；历史242只作参考，当前原10套243（新增Jev反馈作用域1项），其余122为新增或扩展的相关套件。
- 旧捕获JeV5全部旧版INVALID；新版5/5仅NUMERIC_ACCEPTED_OFFLINE_ONLY，原数字不变。
- 捕获E04非法JSON仍INVALID_JSON；E05 H09-REPAIR仍INVALID_MAIN_IDS，未修复原输出。
- 原Prompt38个哈希重建与E05冻结一致；新38个不同哈希、USER与原生Jev请求树38/38一致。
- 真实Jev/DeepSeek/Embedding0、真实fallback0；测试HTTP仅MockRestServiceServer内存截获；API Key没有读取/导入。

纯文本规则不能保证生成忠实；邻接/量化兼容是该模型观测协议的有界可解释性，不是供应商官方保证或语义质量证据。严格拒绝依然可能发生于新版本、真正高精度不一致或不可行输出；原fallback策略不改。整数mapScore仍使用原score×5 HALF_UP，grid仅用于可行性证明。

## 隔离与历史

临时offline-pom.xml复制原POM依赖/插件，仅重定向源码/测试资源与build目录，并排除ignored application-local.yaml资源；原pom.xml不改。编译/测试仅写target/phase5-offline-hardening/build，原target/classes与target/surefire-reports保持字节不变，E01–E05原raw/log/once-lock/report也不改。

前后复核358个历史文件全部SHA相同，完整manifest在history-before.json / integrity-after.json（清单文件SHA `f6529f75264cc449b3b3259f51356c782e41c0e6317c942333175c36472b5802`）。私有local配置不读取、不复制；不据此谎称读取了该配置或证明每个secret值未变。测试子进程清除JEV/DS/Embedding等凭证和RUN_REAL_*、MILVUS_ENABLED，不查询Windows User凭证。没有初始化datasource/Milvus，未做本轮实时13表/52向量对比。

原Phase5A SHA `fb21c8427a0af1d7afcde9f7ccc0f4107a991984d19a8603b35bbd8ec07d9f0a`，38 gold、24排序、4修复、所有质量门槛保持。E05最后真实21/97/21/52/52/topK5记录及快照仍在旧报告；**本轮未重新查询DB/vector**，只证明项目文件未改及本轮没有访问/写入，不能把历史实测标成当前新实测。

主要余项：P1真实反馈忠实度与全部MAIN输出仍需新epoch检验；P1首尝试完整可靠性需38/38双路径；P2供应商tie/内部序列化原因未确认；P2临时Prompt版本常量内联误标已修正并归档。没有授权自动JSON修复、重试、新模型、schema变更或后续阶段。

**READY_FOR_COMMANDER_E06_ADMISSION_REVIEW_ONLY。E06 NOT_RUN，5C NOT_RUN，Phase6 BLOCKED。**
