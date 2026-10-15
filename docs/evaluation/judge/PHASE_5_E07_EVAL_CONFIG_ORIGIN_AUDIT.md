# E07 evaluation 配置来源审计

**Gate A PASS：已确定真实origin。Milvus开关仍为false，未改或覆写。**

## 有效值与覆盖顺序

JDK21.0.11 + Spring Boot实际配置加载，唯一active profile=evaluation。绑定 `milvus.enabled` 和MilvusProperties.enabled均false。ConfigurationPropertyCandidates首项 `class path resource [application-evaluation.yaml] - 8:12`，raw `${MILVUS_ENABLED:false}`；次项 `application.yaml - 41:12`。文件复制到独立build的字节来源为当前实际工作树，源码/配置hash被保护，未假设GitHub版本。

当前process/User/Machine中MILVUS_ENABLED均MISSING。CANARY-01脚本原样保留：它导入现有User值，没有创建缺失的MILVUS_ENABLED；命令行仅注入evaluation等非secret启动参数，没有milvus.enabled=true。当前origin重演不执行那个脚本、不写任何环境变量；变量缺失与先前effective false记录相符。无法逆向证明一个已经退出的旧进程全部环境，但本轮三层presence、原runner逻辑、旧Binder及当前origin给出一致证据。

`configurationProperties` 是Spring聚合适配视图，不能把它误当成新配置文件。实际底层优先级如下（只列非secret开关内容）：

| rank | PropertySource | milvus.enabled raw | origin |
| --- | --- | --- | --- |
| 1 | configurationProperties | ${MILVUS_ENABLED:false} | class path resource [application-evaluation.yaml] - 8:12 |
| 2 | commandLineArgs | 无此项 | — |
| 3 | systemProperties | 无此项 | — |
| 4 | systemEnvironment | 无此项 | — |
| 5 | random | 无此项 | — |
| 6 | Config resource 'class path resource [application-evaluation.yaml]' via location 'optional:classpath:/' | ${MILVUS_ENABLED:false} | class path resource [application-evaluation.yaml] - 8:12 |
| 7 | Config resource 'class path resource [application.yaml]' via location 'optional:classpath:/' | ${MILVUS_ENABLED:false} | class path resource [application.yaml] - 41:12 |
| 8 | applicationInfo | 无此项 | — |

本次实际命令行来源可控制profile，但没有开关值；systemProperties/systemEnvironment也没有对应项。未发现spring.config.import/location/additional-location有效项，未加载application-local.yaml。故evaluation YAML覆盖基础YAML；占位符再查找MILVUS_ENABLED，缺失后回落false。通用优先级只按本次实际PropertySources报告，不将Windows注册表当成Spring会自动读取的PropertySource。

## 初始化器与开关边界

原spring.factories注册EnvironmentIsolationInitializer，实际启动中执行。它只检查profile冲突、MySQL schema、Milvus collection以及Hikari隐藏覆盖，拒绝init SQL；没有调用setProperty、addPropertySource或强制milvus.enabled。即使Milvus disabled仍检查专用Collection名。此次绑定目标通过；它不会替本轮证明数据已经存在。

实际ConditionalOnProperty控制：MilvusConfiguration（ConnectConfig/MilvusClientV2）、MilvusCollectionInitializer、MilvusVectorStoreClient、KnowledgeRetrievalService、KnowledgeDocumentProcessingService、EvaluationRetrievalAdapter。EvaluationOrchestrationService和InterviewWorkflowService同时要求milvus.enabled与deepseek.enabled为true。MilvusProperties Java默认也是false，但此次false已有YAML来源，不能只归因于Java字段初始化。

Embedding配置及DeepSeek/Jev配置有各自规则；这个开关不是所有LLM功能的总开关。开启基础设施后，CollectionInitializer可能在缺失时createCollection，所以在未知Collection情况下不能直接启动完整生产应用。此次只做EmptyConfig解析，没有扫描这些Bean。

既有 `docs/evaluation/scripts/run-baseline.ps1` 在baseline非ordinary分支中设置MILVUS_ENABLED=true；它还会改变其它环境与触发受控测试/模型，**本轮只读审阅没有执行**。历史E06runner使用local配置，本轮不能借此加载私有YAML或混用local。用户/系统环境均未改。

## 离线实际验证

| Suite | tests | failures | errors | skipped |
| --- | ---: | ---: | ---: | ---: |
| EnvironmentIsolationInitializerTest | 23 | 0 | 0 | 0 |
| KnowledgeDocumentProcessingServiceConfigurationTest | 2 | 0 | 0 | 0 |
| MilvusConfigurationTest | 4 | 0 | 0 | 0 |
| MilvusPropertiesTest | 22 | 0 | 0 | 0 |

总51/0/0/0，Maven离线本地缓存、JDK21绝对路径；只写新target/build。EnvironmentIsolationInitializerTest的23项包含真实YAML解析、危险local/schema/collection/Hikari覆盖拒绝；MilvusConfigurationTest通过构造Mock避免真实SDK网络连接。未放宽测试或增加网络依赖。XML路径/SHA与完整命令见offline-test-evidence.json。本次origin实测另验证initializer加载及最终false，没有真实基础设施Bean。

## 建议

安全默认本身无需“修复成常开”。最小执行选项为未来批准的evaluation专用一次runner参数 `--milvus.enabled=true`，runner须在建立Bean前拒绝非唯一evaluation profile与非专用目标，继续调用原初始化器；不得保存为全局/IDEA共用配置。参数可以影响任何接受它的启动，所以只由严格限定evaluation的runner传入。

若希望变量层面也分离，可另审批仅改application-evaluation.yaml第8行为 `enabled: ${EVALUATION_MILVUS_ENABLED:false}` 并在未来专用evaluation子进程明确opt-in。此可选方案只作用evaluation YAML，不改基础/local/test默认；不能同时混用local（原guard拒绝），也不能持久保存环境开关。两种方案均未执行，且均须先解决数据未就绪。完整授权范围见REPAIR_PROPOSAL。
