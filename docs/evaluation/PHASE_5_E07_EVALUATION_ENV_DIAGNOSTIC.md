# E07-EVAL-ENV-01 独立环境诊断

**诊断已完成并停止。环境仍 BLOCKED；确认分类 EVALUATION_DATA_NOT_READY。不能只打开 Milvus 开关：evaluation 库13表全部为空，Milvus 当前无法连接。未修改配置、环境变量或数据，未执行模型请求或 Canary。**

## 核心结论

1. `milvus.enabled=false` 的真实来源为 **`application-evaluation.yaml` 第8行、第12列**，原始值 `${MILVUS_ENABLED:false}`。当前进程、Windows User和Machine均没有MILVUS_ENABLED；Spring解析使用false默认值。基础application.yaml第41行是较低优先级的相同默认。没有命令行启用、配置导入、local profile或initializer重写。
2. 默认false符合既有隔离安全设计；它决定Milvus基础设施和相关检索/知识处理/评价编排Bean是否装配，不表示Collection不存在。原EnvironmentIsolationInitializer实际执行，仍要求evaluation唯一profile和专用目标；没有写PropertySource或开关。
3. MySQL真实只读连接确认 `localhost:3306/ai_interview_evaluation`，13个核心InnoDB表、15条FK存在；**13表全0行**。冻结目标21题/97评分点/21文档/52chunks全部缺失，不能复用local内容或只靠开关继续Canary。
4. Milvus目标仍为 `http://127.0.0.1:19530`、`default/knowledge_chunk_vectors_evaluation`、配置维度1024。仅一次合法REST只读describe请求，发生ConnectionError，未收到HTTP响应。Collection存在性、真实维度、索引、52向量与映射均UNKNOWN。Docker只读inventory也因Linux engine named pipe不可用失败；没有启动Docker或猜测Collection状态。

## 实际检查点

| 检查 | 状态 |
| --- | --- |
| Prior CANARY-01 report, artifacts and historical files | PASS |
| Actual Spring PropertySource and origin | PASS |
| Isolation initializer executed without rewrite | PASS |
| Milvus switch suitable for Canary | BLOCKED |
| Offline focused tests | PASS |
| Evaluation MySQL access and target identity | PASS |
| Core MySQL structure inventory | PASS |
| Full DDL/index/CHECK equivalence | UNKNOWN |
| Frozen evaluation corpus counts and identities | FAIL |
| Frozen corpus row hashes | UNKNOWN |
| Evaluation Milvus Collection existence | UNKNOWN |
| Evaluation Milvus schema/index/dimension/vector count | UNKNOWN |
| Cross-store IDs and eight real topK5 contexts | UNKNOWN |
| Docker Linux engine availability | BLOCKED |
| Zero real model/Embedding calls, data/config/env writes, Git mutations | PASS |

## 验证与执行边界

JDK21.0.11，通过直接调用本地Maven/JDK，在本輪独立build运行4套配置/Mock测试：51项、failures0/errors0/skipped0。没有改变JAVA_HOME/PATH或真实环境变量；PATH默认java实际是1.8.0_503，工具显式使用JDK21绝对路径。编译及测试日志/XML保留。该51项是本轮新验证；394项属于CANARY-01既有Gate0，不宣称本轮重新运行394或全项目回归。

本轮两个独立只读探测同时启动后，各自保留首次结果；SQL计数不匹配及Milvus连接失败之后，未继续内容迁移、搜索或数据修复。调用：MySQL连接1、SELECT17、Milvus只读HTTP尝试1/成功响应0、Docker只读命令1；模型/Embedding/搜索/重试/DDL/DML/配置和环境写入均0。凭据仅在内存/子进程stdin使用，报告只保留SET/MISSING，没有账号明文、token、密码或密钥哈希。未读取私有local YAML，未加载/强行创建Milvus Bean或CollectionInitializer。

## 最小后续动作，需要另行批准

先批准隔离环境准备：恢复/确认已有Milvus服务的访问，在专用evaluation目标核验Collection；随后只向该目标准备冻结的21/97/21/52及52个既有向量，保持ID、版本、内容与浮点值，不复制user/session/answer/凭据，不调用Embedding。当前4张语料表的真实FK只有评分点→题目、chunk→文档，不需要额外user父记录。Collection缺失或不兼容时，须再明确审批限定DDL，不能drop/重建。

准备完成并证明全部内容/向量/检索上下文一致后，另批准**evaluation专用的一次进程启用参数**及新一次预检，不改全局默认。这样最少无需改生产YAML。可选的一行evaluation专用变量加固见修复提案；不是本轮执行项，也不是数据缺失的替代方案。新的Canary epoch仍需单独模型预算授权。

## 保存与交付

E07-CANARY-01原报告SHA：`6b4f04f0b879847987ee8a00b9c0a70d80dfc710c0b09dbe1ba32318d5d861fe`，末轮完全相同。包含其全部构建/报告/锁及E01–E06历史的4113项保护文件均无差异，GitHEAD `6076a70f18f3062b8faff4fc179cf21f00a95036`、完整Git状态不变；未add/commit/push/reset。原失败、394项证据、Golden、Prompt和模型不改。

- [配置来源审计](judge/PHASE_5_E07_EVAL_CONFIG_ORIGIN_AUDIT.md)
- [数据就绪核验](judge/PHASE_5_E07_EVAL_DATA_READINESS.md)
- [零执行修复提案](judge/PHASE_5_E07_EVAL_REPAIR_PROPOSAL.md)
- 机器证据：`target/phase5-e07-eval-env01/readiness-evidence.json`；所有新文件SHA见该目录deliverable-manifest.json。

数据内容/哈希匹配、真实向量/跨库映射、8 query实时topK5尚未验证，明确UNKNOWN；不以历史E06 local结果当成evaluation在线证据。PHASE5_FULL_QUALITY=NOT_EVALUATED；E07-CANARY-02=NOT_AUTHORIZED/NOT_RUN；5C NOT_RUN；Phase6 BLOCKED。到此停止，交Commander审查。
