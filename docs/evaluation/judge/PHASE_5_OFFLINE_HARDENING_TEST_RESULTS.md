# Phase 5 离线专项修复测试结果（Gate D）

实际结果：**compile PASS；19套定向测试365/0/0/0。** 真实Jev/DeepSeek/Embedding调用0，真实fallback0，未初始化datasource/Milvus。没有运行普通全量mvn test或opt-in真实Smoke；不能称完整项目回归/真实集成通过。

JDK为C:/Program Files/Java/jdk-21.0.11；Maven使用仓库现有wrapper和全部本地缓存、-o离线。编译181份生产source，定向测试同时编译现有测试source。原E04 242/0/0/0是不可改历史参考；本轮以独立Surefire XML计数如下，不能把历史数当固定目标。

| suite | tests | failures | errors | skipped | XML SHA256 |
| --- | ---: | ---: | ---: | ---: | --- |
| FollowUpPolicyTest | 15 | 0 | 0 | 0 | `9a6af1b6b685ebce691a1bd2d708a10b78782e963394159fa59effc3eaf3eac4` |
| FollowUpTargetResolverTest | 7 | 0 | 0 | 0 | `01ed26d31de20a1cb8fcf581546db9a88f7aceb2f4e6dce91654740d4fd74078` |
| DeepSeekJudgeProviderTest | 1 | 0 | 0 | 0 | `57124abc53dfefb3de85d6356fe18cf52ce1a141144f2688a3eb8b6dc37db52d` |
| JevClientTest | 17 | 0 | 0 | 0 | `6e80cdfe4a8e5058d6e5dff0ab37c71e140229b2afcd9b88ec5532df7a05a50b` |
| JevDisplayQuantizationHardeningTest | 32 | 0 | 0 | 0 | `2ea7d6d0f7d191381309e9fb3b5382e088265774aaf1ccdd4aec52bfd4618991` |
| JevJudgeProviderTest | 47 | 0 | 0 | 0 | `18511348bf8eba07a555a2b4da1168c91cd11d98e75d9786f44a5f0194499668` |
| JevScoreMappingTest | 16 | 0 | 0 | 0 | `fc3a107db455f4ec247fabc540ed7cb577fa5409cf06aab74b3f49df290c964c` |
| JevScorePrecisionCompatibilityTest | 63 | 0 | 0 | 0 | `ac8d3595102540dcdebaf85898458f0cbd1af4787a5e595193c1e0bd1f69ff83` |
| JudgeConfigurationTest | 12 | 0 | 0 | 0 | `ba505c868c5ea9d3e3edb8030df77d358210663e9ecc75be880f88dd9446db8e` |
| DeepSeekConfigurationTest | 2 | 0 | 0 | 0 | `095e0d493bb0382b1d5032d7a6ff33d468e2dac0764ce1732f9028fcff42ac5d` |
| DeepSeekEvaluationClientTest | 19 | 0 | 0 | 0 | `e3f05040cbebf5d92f65d89f43051b355826eb2cbd770e2ea342a89626bbfe6d` |
| DeepSeekPropertiesTest | 19 | 0 | 0 | 0 | `df62ddf9d7acf4a99a84302d2c86fa90a545afdf18c107d898e6e566e5f62c75` |
| EvaluationOrchestrationServiceTest | 23 | 0 | 0 | 0 | `c97a3fbef94b6fdbbcc483a9e1c0dbac5a99ae03590e0aed0cedb720840fe5ef` |
| EvaluationPromptBuilderTest | 11 | 0 | 0 | 0 | `c80586dfe89dd61ac45d2d1f731bb09716f398820663ae9d2e9b79a0ed11e67e` |
| EvaluationPromptTest | 13 | 0 | 0 | 0 | `13d1511b7332e97c440c2ba6b572c884dffabea21544786926e013514ff6d68e` |
| FollowUpOutputCompletenessHardeningTest | 2 | 0 | 0 | 0 | `2b9b7cd6b7ed0982c36482356ae5a68e72e5eaa58ab1e5c970bf867122ac9e06` |
| EvaluationScoreCalculatorTest | 4 | 0 | 0 | 0 | `e22fa71ccf39077af812bc2b55374a3ba3a740e3b6ac418bcfe7461073dbe055` |
| EvaluationStandardTest | 15 | 0 | 0 | 0 | `bea8ac479ed249bcef27cc176603e4b9fc3b08d69e47bc7b65500f1f69099d65` |
| LlmEvaluationValidatorTest | 47 | 0 | 0 | 0 | `714c13fff60b7ee05019b4125b6328dbf1d03743984db7c131c6b162d5b051cc` |
| TOTAL | 365 | 0 | 0 | 0 | 19 suites |

## 命令与复现入口

执行target/phase5-offline-hardening/offline.ps1 -Mode compile：

```powershell
.\mvnw.cmd -o -B -ntp -f target/phase5-offline-hardening/offline-pom.xml -DskipTests compile
```

-Mode test在同一临时POM运行`-Dtest=`加targeted-suites.txt中的19个精确suite。XML/文本输出位于build/surefire-reports，完整日志compile.log / tests.log。编译耗时9.130s，定向测试Maven耗时25.091s；两个进程均exit0。

脚本只设置本子进程JAVA_HOME，移除模型API凭证、DB/test DB/JWT/Milvus凭证、profile/location override与JAVA/Maven option注入，再清除RUN_REAL_*和MILVUS_ENABLED；不读取Windows User Key值，不设置持久开关。原配置/测试资源中的合成test key不是实际用户凭证，ignored application-local.yaml被排除，不进入新build。真实服务测试没有被选入本定向运行，因此skipped0并非真实Smoke成功。

## 覆盖点

1. 新32项JeV显示兼容：原5份纯数值fixture全5维回放、retained raw score/probabilities/confidence/legend相同；score和prob的一相邻binary64接受、两相邻拒绝；0/1截断邻接；sum0.99/1.01有/无联合witness；严重mass差、零质量、真高精度不一致、未知model、choice sum仍拒绝。
2. 原63项精度测试保留：p4=1/score3.97不可行；单点0..4、近0/4分数、剪裁端点、恶意误差预算、null/范围/keys/type/legend/confidence、NaN/Inf probability、choice argmax与一次fallback。一个旧sum案例根据本轮新合同用独立witness改为接受，另外三项拒绝，详见patch review；没有跳过失败项。
3. 原JeV Client/映射/配置测试：严格JSON、问题集合、错误响应、score NaN/Inf/范围、confidence门禁、fallback一次语义继续验证。
4. Jev反馈system来源约束与rubric不变；PromptBuilder的全MAIN ID/targets区分及两模式事实归因文字；2个Client级内存HTTP用同一schema发送新约束，完整4ID通过，仅2targets仍由原validator拒绝且rawJSON未补写。
5. DeepSeek原4套、validator、Java分数/level/follow-up target/policy、orchestration单元测试全部通过，没有变业务决策。

## 独立纯捕获回放（不计新模型样本）

| 回放 | 旧/新结果 | API次数 |
| --- | --- | ---: |
| 5份JeV实际raw，旧compiled private validateAnswers | INVALID5/5，原异常行187 | 0 |
| 相同5份raw，新compiled private validateAnswers | NUMERIC_ACCEPTED_OFFLINE_ONLY5/5 | 0 |
| E04第二baseline捕获内层JSON | 原parser仍INVALID_JSON | 0 |
| E05 H09-REPAIR实际2点结果 | 原validator仍INVALID_MAIN_IDS | 0 |
| 38旧prompt重建 | SHA与E05全部相同 | 0 |
| 38新prompt | 新SHA；38 USER与JeV请求树不变；8 FOLLOW_UP全MAIN IDs显式 | 0 |

独立数学证书另用Python Decimal与Fraction验证所有25score维度与5个显式HALF_UP witness。没有将概率normalize或改变模型数字，证书只证明可解释性。编译成功不等于功能验证；Mock成功不等于真实服务成功；这些回放不能更新E05 INVALID或累计为E06质量样本。

临时版本审计器初次受常量内联影响的顶部旧版本标记已归档并修正为反射，重新纯离线复核；未触发模型API或重跑已通过的365套件，生产补丁无额外变化。

终点：离线专项验证完成。**等待Commander批准新E06；5C/Phase6未运行。**
