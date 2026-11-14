# E3｜E01 单篇真实入库执行记录

后续更新（2026-11-14）：真实配置补齐后，E01已用1次授权请求完成READY及双向对账，见[实际入库验收](E3_E01_REAL_INGESTION_20261114.md)。以下保留2026-11-13阻塞时点事实。

日期：2026-11-13（Asia/Shanghai）。项目：`/Users/kun/Developer/projects/ai-interview-system`。实际 HEAD：`0c6f92afe331954f7cea37d33db688fe781cbf2c`。

**结论：BLOCKED，尚未发起真实 Embedding 请求，也未上传文档。** 用户已明确授权1次百炼请求、2个指定Chunk、1024维、最多人民币0.15元；本次阻塞是本机缺少真实服务配置，不是缺少调用授权。用户随后明确回复“尚未配置，先记录阻塞”。不再次申请同一范围授权，不自动轮询或继续执行。

## 已确认的阻塞

项目受保护 `.env` 存在、权限0600、Git忽略；其 Embedding Base URL 为 `http://127.0.0.1:18080/v1`，API Key 是占位值。`src/main/resources/application-local.yaml` 不存在，当前进程没有真实 Embedding Key，所查Shell启动配置也没有对应模型变量。本机18080由其他Java应用监听，不能将本次文档或未来真实凭据发往该地址。迁移记录已明确：该 `.env` 用于新本地实例，不代表Windows私人模型配置已迁移。

未打印或保存Key、授权头、口令、完整异常正文或完整向量。未修改 `.env`、正式YAML、源码、切片/补偿规则、H01或题库。没有生成可触发收费的执行器。

## 已执行的只读核验

- 重新读取AGENTS、E1–E3执行报告、台账及阶段D清单；确认工作目录和Git状态，既有用户修改保留。
- E01文件仍为 `docs/rag/source-documents/e01_java21_interface_vs_abstract_class.md`；原始及规范化SHA-256均为 `4b82a2724e53946fffa082560fde1c348201114ac4f490e409c2ddbba5071de0`。
- 直接运行既有Java离线切片探针，退出0，输出与阶段D清单逐字节一致：2块，UTF-8字节数2424+764=**3188**，UTF-16单元1178+460=1638。两块SHA分别为 `db9396ccf3b0d6d5b527c58c23bf032a776eeb7089e048796b8f231534f08951` 和 `2e276739b01c8d87c25b2f013b34e55d5f73757f3f7053af4147fc367919680e`。
- MySQL实际目标 `127.0.0.1:3308/ai_interview_evaluation`，UUID `85dd1e00-cf98-11f1-8677-9eb9380f25e7`；Milvus目标 `127.0.0.1:19532/default/knowledge_chunk_vectors_evaluation`。两个原服务均running/healthy，没有停服或重启。
- 真实MySQL只读盘点：evaluation仍为21题/97评分点，其余11表为0；因此没有可复用的E01 READY文档。本轮完整逻辑导出在内存与上轮备份逐字节比较：原两库以及evaluation均相同，没有写业务数据。
- Java SDK真实只读盘点退出0，四个Collection的元数据/索引/加载状态/Strong count输出与上轮终态逐字节相同；evaluation及另外三个集合的Strong count均为0。目标保持1024维、COSINE/AUTOINDEX。
- 原新卷归档重新计算SHA-256为 `4a28237ee5b9d8a7911c1e9a6d4e9344ac0e069f473e1af78a8f31d4f01e25a4`，与已验收恢复点一致。旧卷、恢复副本和历史备份未操作。上轮异常停机后独立恢复成功是历史证据；本轮没有再做恢复/断电实验。

## 价格与单请求限制的审查

当前官方公开价：北京同步文本输入 **¥0.50/百万tokens**，新加坡 **¥0.525/百万tokens**；同步接口每条最多128000 tokens、最多20条。固定两条输入的保守上限分别为¥0.128、¥0.1344，均低于授权¥0.15；4000 tokens只是估算，对应¥0.0020/¥0.0021，不能用3188字节冒充实际tokens。[模型价格](https://help.aliyun.com/zh/model-studio/qwen3-7-text-embedding) · [同步接口](https://help.aliyun.com/zh/model-studio/text-embedding-synchronous-api)

北京旧域名 `dashscope.aliyuncs.com`、新加坡旧域名 `dashscope-intl.aliyuncs.com` 对应HTTPS OpenAI兼容路径 `/compatible-mode/v1/embeddings`；也有地域绑定的业务空间域名。**本轮未取得真实配置，实际服务地域、适用单价和账户绑定尚未确认。** 百炼月预算停用有延迟，不能视为单次请求的实时硬预算；未来执行仍须依靠固定输入、单次请求和调用前估算，不依赖免费额度。[Base URL](https://help.aliyun.com/zh/model-studio/base-url) · [预算机制](https://help.aliyun.com/zh/model-studio/budget-management)

静态审查确认生产 `EmbeddingConfiguration` 使用Spring AI maxAttempts=1；Milvus配置采用有界SDK尝试。仅设置Spring重试次数仍不等于底层HTTP绝不会透明重发。本机JDK21源码审查得到后续独立执行器可使用的约束：`jdk.httpclient.redirects.retrylimit=1`、`jdk.httpclient.disableRetryConnect=true`、`jdk.httpclient.enableAllMethodRetry=false`，HTTP/1.1及NEVER重定向，发送前校验完整目标和两个输入指纹并只放行一次。**这些是后续执行建议，本轮未装配或运行验证。** [JDK21 HTTP客户端配置](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/module-summary.html)

## 实际调用、数据及验证状态

- Embedding HTTP尝试 **0**；DeepSeek及其他模型 **0**；上传 **0**；处理任务领取 **0**。
- 没有响应模型、向量维度实测、请求耗时或usage；实际tokens和按usage计算费用均为**未产生/不适用**，账单未查询。不能将预算估算写成实际消费或已核实扣费。
- 文档ID、Chunk ID和vectorId映射清单为空，因为没有新增对象；READY/ACTIVE、向量有限性及双向身份验收**未执行**。无本轮失败attempt或补偿，不能记为故障恢复测试通过。
- 已验证的是原数据保全和空RAG基线；不是非空RAG双向一致性、COSINE自检、独立语义检索、Hit@K或评分效果。
- 离线Java输入探针1次通过；真实MySQL只读核验1阶段通过；真实Milvus SDK盘点1次通过。这些不是JUnit数量。Maven/JUnit/Mock/全量回归 **0次，未执行**，没有写受保护测试库，也没有复用上轮150项计数作为本轮结果。

## 文件、Git及恢复条件

新增本报告：`/Users/kun/Developer/projects/ai-interview-system/docs/verification/E3_E01_REAL_INGESTION_20261113.md`。

更新两份工程记录：

- `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/IMPROVEMENT_LEDGER.md`
- `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/SOURCE_LEARNING_BACKLOG.md`

本轮本地证据：`/Users/kun/Developer/projects/ai-interview-system/backups/E3_E01_20261113/`（0700，文件0600，Git忽略），包含脱敏前置检查、原始输入复核输出、MySQL结构/计数、两类逻辑导出摘要、SDK盘点、服务状态和文件清单。没有模型Key或完整向量；未重复保存数据库业务dump。`artifact-manifest.json`列出本轮生成文件的绝对路径、字节数和SHA。

本轮没有git add/commit/push/reset/clean/stash/rebase；没有新提交SHA，未暂存。当前HEAD不变；本轮未重新查询远端，上一轮同日核验main为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，不能将其表述为本轮再次验证。

恢复执行条件：在本机受保护、Git忽略的配置中补齐真实百炼地域端点及匹配Key，只告知配置路径，无须在聊天中提供Key。随后重新核对输入、目标、已有READY复用、模型/profile、实际地域价格与底层单请求约束；全部通过后使用既有业务管道执行本轮已授权的一次请求。配置完成前保持BLOCKED；既有0.15元/1请求授权不扩大为重试或其他文档许可。
