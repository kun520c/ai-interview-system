# E06 Prompt加固定向测试结果

本轮为 Commander 授权的离线取证与文字加固。真实模型/Embedding API 0，数据库与Milvus客户端初始化0、写入0；E07未运行、5C NOT_RUN、Phase6 BLOCKED。E06 原严格门禁 FAIL，38案例/114推理/零重试、native38/38、baseline38/38、联合反馈及配对33/38保持不变。

**JDK21 compile PASS；原19套365 + 新1套29 = 394项，failures0/errors0/skipped0。** 原19套testcase名字逐项与E06 Surefire XML一致。没有跑全项目mvn test、真实Smoke或baseline模型复测。

## 隔离执行与可复现命令

使用独立 `target/phase5-e06-offline-forensics/offline-pom.xml`，沿用原pom依赖/插件，绝对source/test目录，私有application-local.yaml从resources排除，build目录独立。脚本在子进程清除真实模型/DB/testDB/JWT/Milvus凭证变量、RUN_REAL_*、MILVUS_ENABLED、SPRING配置覆盖及JAVA/MAVEN选项；不读取Windows User/Machine凭证或local YAML。Maven `-o` 离线、本地缓存依赖，所选测试用MockRestServiceServer/Mockito/隔离内存上下文，无真实网络、datasource或vector客户端。

```powershell
& .\target\phase5-e06-offline-forensics\offline.ps1 -Mode compile
& .\target\phase5-e06-offline-forensics\offline.ps1 -Mode test
& .\target\phase5-e06-offline-forensics\offline.ps1 -Mode replay-after
```

compile实际底层：`.\mvnw.cmd -o -B -ntp -f target\phase5-e06-offline-forensics\offline-pom.xml -DskipTests compile`；test同pom，`-Dtest=<targeted-suites.txt里的19原套+JevFeedbackPromptHardeningTest> test`。JAVA_HOME指向JDK21.0.11。compile编译181个生产源文件，test重编译测试后运行20套。日志SHA：

| 日志 | SHA256 |
| --- | --- |
| target/phase5-e06-offline-forensics/compile.log | 736de36e5e78f729fe1aad2a0f5a271d4c386e365a5490dd66fb1671f260cb6b |
| target/phase5-e06-offline-forensics/tests.log | f6d6fedf95d3a73f52b0648b315c29744fceb3cfcdb38da5db8c60eb839cb4df |

| Suite | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| FollowUpPolicyTest | 15 | 0 | 0 | 0 |
| FollowUpTargetResolverTest | 7 | 0 | 0 | 0 |
| DeepSeekJudgeProviderTest | 1 | 0 | 0 | 0 |
| JevClientTest | 17 | 0 | 0 | 0 |
| JevDisplayQuantizationHardeningTest | 32 | 0 | 0 | 0 |
| JevFeedbackPromptHardeningTest | 29 | 0 | 0 | 0 |
| JevJudgeProviderTest | 47 | 0 | 0 | 0 |
| JevScoreMappingTest | 16 | 0 | 0 | 0 |
| JevScorePrecisionCompatibilityTest | 63 | 0 | 0 | 0 |
| JudgeConfigurationTest | 12 | 0 | 0 | 0 |
| DeepSeekConfigurationTest | 2 | 0 | 0 | 0 |
| DeepSeekEvaluationClientTest | 19 | 0 | 0 | 0 |
| DeepSeekPropertiesTest | 19 | 0 | 0 | 0 |
| EvaluationOrchestrationServiceTest | 23 | 0 | 0 | 0 |
| EvaluationPromptBuilderTest | 11 | 0 | 0 | 0 |
| EvaluationPromptTest | 13 | 0 | 0 | 0 |
| FollowUpOutputCompletenessHardeningTest | 2 | 0 | 0 | 0 |
| EvaluationScoreCalculatorTest | 4 | 0 | 0 | 0 |
| EvaluationStandardTest | 15 | 0 | 0 | 0 |
| LlmEvaluationValidatorTest | 47 | 0 | 0 | 0 |

每suite XML路径与SHA、原测试名字不变检查见 `test-evidence.json`。新增29：五份captured invalid原内容仍走既有fallback一次（5）；独立构造合规mock全MAIN ID/双来源合法quote通过（5）；exact MAIN/FOLLOW_UP、emoji、组合字符原样、双空格、转义JSON通过，Unicode规范化、空白折叠、改标点、拼接/省略号、空/纯空白/null、未转义JSON拒绝（15）；只targets、漏MAIN已covered ID、duplicate/foreign ID拒绝（4）。所有native covered保持原始选择，uncovered最终evidence=null。

新Mock调用现有DeepSeekJudgeProvider，但DeepSeek客户端是Mock；sentinel输出仅标识fallback路由，不当作合法评价或5C模型证明。已有DeepSeekEvaluationClientTest/FollowUpOutputCompletenessHardeningTest继续校验json_object/thinking disabled/完整ID等请求契约。新增system实际传给completeJson的文字被Mock验证。测试只证明Java边界和发送约束，不证明模型会产出遵守约束的文本，也不验证线上strengths/missingPoints语义改善。

## 原响应离线回放（补丁前与后）

| 回放项目 | v2原编译类 | v3本轮编译类 |
| --- | --- | --- |
| 实际原native验证 | 38 PASS | 38 PASS |
| 实际原feedback验证 | 33 PASS / 5 REJECT | 33 PASS / 5 REJECT |
| 相同5例异常/阶段 | 冻结E06一致 | 逐字段与v2一致 |
| USER/Jev数值request树 | 38/38不变 | 38/38不变 |
| 额外真实调用 | 0 | 0 |

replay-before使用E06原build/classes，只读取旧文件；replay-after使用本轮独立build。reflection调用生产buildRequest/validateAnswers/mapDecision/parseFeedback/mergeFeedback和LlmEvaluationValidator，严格Jackson位置另行复现。两个illegal JSON依旧报原ExternalServiceException；三个无效quote依旧拒绝。旧raw字节不变，合规Mock另建，绝未把旧输出改成valid。

本轮临时工具调试记录：最初在内存valueToTree比较时Jackson数值节点类型导致request树比较误差，改为与原E06 PromptAudit一致的serialize/readTree后38项通过；Prompt字节补丁脚本最初未匹配原混合LF/CRLF，校验断言停止写该文件，按实际块换行完成后仅授权文本变化。语义源核对明确covered=false evidence由既有merge置null。以上均为离线工具错误/预期映射核对，不是测试代码或模型重试；没有隐瞒新增JUnit失败，正式compile/test一次均通过。

历史3079项保护清单前后SHA相同 `1c4cc1d13b01f2a7a6e0eddc3c05ca01bf385d243557d4fed6096e14aaef441a`；生产方法/数值容差/validator逐字节不变。无真实模型/DB/Milvus/Embedding测试，效果边界停留在OFFLINE_VERIFIED。

**STOP_WAIT_COMMANDER；E06 FAIL保持。**
