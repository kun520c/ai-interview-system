# E07-CANARY-01 静态及运行审计

**Gate0 PASS；Gate1 STOPPED；Gate2 NOT_RUN。** 首个真实阻断为 evaluation profile 的 `milvus.enabled=false`。独立预检没有component scan、自动配置或业务Service初始化，不包含在线模型/Embedding调用。

## 本地源码、dirty工作树与历史

读取实际AGENTS、EnvironmentIsolationInitializer、application及evaluation profile，以及E06根因/修复选项/补丁/测试/总报告和RUN_EVIDENCE。修复选项文件存在。Commander文档原路径和SHA保存在authorization.json。入场HEAD `6076a70f18f3062b8faff4fc179cf21f00a95036`，Git状态原样保存 entry-git-status.json；私有YAML和真实凭证未导出。

按E06离线入场字节比较两个生产类，移除指定常量及版本后完全一致。两份SHA全匹配Commander白名单；必要脱敏diff只含两个生产文件的SYSTEM和版本变化，见 entry-relative-production.diff。原dirty/untracked配置/测试/前端及环境隔离改动属于此前已有状态，与离线exit manifest逐文件核对734项无差异。原3079文件、E061983文件及原3083入口（四个既有授权编辑除外）通过。末轮3598项快照、114 raw和全部8个历史锁保持；E01没有原独立锁，未补造。

## 编译、定向测试与回放

复制此前离线pom/runner的执行约定到E07新目录，build只写E07。私有application-local.yaml排除；子进程清除真实模型/DB/JWT/Milvus凭证、RUN_REAL、MILVUS_ENABLED、Spring及Java覆盖选项，Maven使用 `-o` 和JDK21。实际命令：

```powershell
& 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -File 'target/phase5-epoch07-canary01/offline.ps1' -Mode compile
& 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -File 'target/phase5-epoch07-canary01/offline.ps1' -Mode test
& 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -File 'target/phase5-epoch07-canary01/offline.ps1' -Mode replay-after
```

181个生产源编译BUILD SUCCESS；394/0/0/0，20suite XML逐项SHA、testcase名字及日志SHA见gate0-offline-verification.json。既有deprecation/javac annotation processing提示不作为新编译失败。未做普通全量mvn test或真实Smoke。

| Suite | tests | failures | errors | skipped |
| --- | ---: | ---: | ---: | ---: |
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

通过原生产方法重建38个Prompt及Jev请求，所有冻结新SHA、旧SHA、USER及请求树匹配，v3版本与FEEDBACK_SYSTEM SHA匹配。五份E06捕获仍原阶段拒绝，没有宽松parser、补写quote、fallback混样或改分。原captured回放是离线诊断，不是本轮新鲜模型输出。

## 有效Spring Binder实测

独立EmptyConfig只加载配置与原EnvironmentIsolationInitializer，唯一active profile=evaluation；没有引入local YAML。preflight.ps1仅将现有User-scope变量送入子进程并沿用Process变量，不写用户/系统环境变量，也没有为门禁强行设置MILVUS_ENABLED。每次预检由CREATE_NEW原子锁保护，本轮只有一次实际Binder预检。

| 参数 | 实测 |
| --- | --- |
| JDK | 21.0.11 |
| provider / DeepSeek enabled | JEV / True |
| Jev host / requested model | api.typesafe.ai / jev-latest |
| Jev connect/read timeout / minConfidence | PT3S / PT20S / 0.0 |
| DeepSeek host / requested model | api.deepseek.com / deepseek-flash |
| DeepSeek connect/read timeout / maxTokens | PT3S / PT1M / 4096 |
| Embedding model/profile/dim | qwen3.7-text-embedding / qwen3.7-text-embedding-1024-dense-v1 / 1024 |
| MySQL隔离目标（initializer校验配置，尚未连接） | ai_interview_evaluation |
| Milvus host / database / collection | 127.0.0.1 / default / knowledge_chunk_vectors_evaluation |
| Milvus dim / connect/request timeout | 1024 / PT15S / PT20S |
| Milvus enabled | **false → STOP** |
| planned topK | 5；实时检索NOT_RUN |
| API/DB credential presence | 必需项SET；可选Milvus token MISSING；未存值/前缀/哈希 |

生产DeepSeek源码及Mock合同测试确认json_object、thinking disabled、stream=false、无tools/temperature；本次运行参数反射检查安排在Milvus门禁之后，因此实际阻断后没有继续执行该检查，不夸称在线生成协议已验证。返回model/HTTP/finish/usage均NOT_RUN；无模型GET/推理/Embedding/搜索。

## STOP执行边界

ReadOnlyPreflight在创建DriverManagerDataSource、MilvusClientV2之前发现false并停止。没有初始化生产集合/迁移/写入；MySQL13表与Milvus52向量实时快照无法取得，保存完整性UNKNOWN；旧RAG evidence没有被授权复用。运行预检日志、原子锁及runtime-preflight.json原样保留，未重跑。只允许完成证据及审查报告。

`preflight.ps1` 实際运行退出非零，由GateStop引起；是环境准入阻断，不是生产编译或JUnit失败。取证脚本的字段/格式诊断保存setup-diagnostic.json及gate0-entry-adjudication.json，不是供应商重试。最终验证final-verification.json及交付manifest保留文件SHA与Git状态。没有生产改动或Git发布，Phase5C NOT_RUN，Phase6 BLOCKED。
