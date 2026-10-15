# RAG P1 独立检索：单次、只读、交互入口

在本机 PowerShell 7 运行（必须由用户手动发起收费检索）：

```powershell
pwsh -NoProfile -File "E:\learning\IDEA_PROJECTJ\ai-interview-system\tools\rag-p1\run-independent.ps1" -Mode ExecuteOnce
```

先离线编译和测试，然后通过 `Read-Host -AsSecureString` 隐藏输入既有 `rag_e2e_writer@127.0.0.1` 密码。复用 rag-e2e 的 BSTR 清理和子进程环境白名单，不调用其业务入口。密码只存于本次 PowerShell 与 Java 子进程内存/子进程环境；不写文件、日志、参数、Process 父环境或 User/Machine 环境。运行结束清除环境副本并释放 BSTR/SecureString；托管字符串不承诺可验证的内存擦除。API key 复用已有 `EMBEDDING_API_KEY` Process/User 设置，地址复用已有 `EMBEDDING_BASE_URL` 的官方白名单值。不会读取 root 凭据或本地 YAML。可选 Milvus token 只从本次 Process 的 `E2E_MILVUS_TOKEN` 读取。

离线模式（不问密码、不连接数据库、不调用外部服务）：

```powershell
pwsh -NoProfile -File "E:\learning\IDEA_PROJECTJ\ai-interview-system\tools\rag-p1\run-independent.ps1" -Mode Offline
```

## 只读与资源门禁

- JDBC 地址固定 `127.0.0.1:3306/ai_interview_test`；账号固定 `rag_e2e_writer`。服务 UUID、CURRENT_USER、DATABASE、roles 必须匹配现有冻结环境。
- JDBC `setReadOnly(true)`、REPEATABLE_READ、关闭 autocommit，再固定执行 `START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY`。首个 SELECT 检查服务器 `@@session.transaction_read_only=1`，并检查 JDBC 只读标记。不通过就停止。连接最后仅 rollback，不 commit。
- SQL 接口不接受调用者任意 SQL：只允许固定身份/权限 SELECT、冻结合同产生的四个表 SELECT、最多五个占位参数的生产等价水合 SELECT 与拒绝原因诊断 SELECT。禁止 SHOW、写 SQL、多语句、锁定读、其他库查询。事务控制语句只有上述固定 START。
- 明确记录此账号已有写权限；保护来自服务器 READ ONLY 加固定 SELECT 路径，不把写权限账号当作只读权限的证明。
- 191 行四表完整 SHA（21 question、97 scoring point、21 document、52 chunk）必须匹配已有 `tools/rag-e2e/contract.json`。Milvus `default/knowledge_chunk_vectors_test` 的 ID 必须为 `469534467320352840`；验证五字段 Schema、1024维、AUTOINDEX/COSINE、Bounded、loaded、52 条全向量 SHA 及 MySQL 映射。仅 Describe/Query/Search；不初始化或 load 集合，不启动 Spring Boot。
- 题集 SHA 固定 `4f42970bbdb3682c475056e512ff725832329d213b8b49e3a27ac45b5afbd456`；8/4/4 分组，21 篇源文档 SHA 与当前 Java QueryBuilder 都核对。
- 使用冻结 `builderInput` 构造查询，含原已冻结的合成评分点与追问主问题上下文；不增加答案/评分点/覆盖依据。这是生产式查询合同测试，不是仅问题文本测试。Embedding 使用直接 OpenAI 兼容 HTTP，保持固定模型、1024维、float；没有启动 Spring AI 或业务应用。

## 调用及费用

Embedding HTTP 与 Milvus Search 分别硬计数最多 16 次；一题各一次，V0/V1共享候选。JDK HTTP 重试关闭、不跟随重定向；Milvus SDK `maxRetryTimes=1`、`retryOnRateLimit=false`，离线验证 SDK 只执行一次失败 callable。没有 DeepSeek、Jev 或业务提交路径。

原始 Float32 分数按生产 `double >= 0.70d` 比较，保存原始 bits/Rank，不四舍五入判定；`0.70f` 实际为 `0.699999988079071`，会被过滤。

实际 usage 与原始成功响应保留。费用按已返回 total_tokens × 北京0.50元/百万Token（国际0.525元）估算；参考[百炼官方价格](https://help.aliyun.com/zh/model-studio/model-pricing)，2026-10-12 核对。缺少 usage 或请求异常时明确标记不完整，不能把已知部分费用当账单硬上限。

单次输出目录用原子创建保留，再写锁。失败立即停止，不重跑已收费问题；每次调用前保存 intent，超时/中断不退还次数。再次运行同一入口拒绝已有目录。保留目录与锁，勿删除后擅自重跑。

## 输出与人工审查

固定目录：`E:\learning\IDEA_PROJECTJ\ai-interview-system\target\rag-p1-independent\run-once\`

- `results.json`：16 题查询合同、模型/usage、TopK5、原始Rank/Float32/bits/vectorId、完整标题正文/内容SHA、SQL资格与拒绝理由、V0/V1计数、执行账本、只读身份、前后冻结摘要、客观计数。失败保留未开始与失败题状态。
- 每题 `*.embedding-request.json`、`*.embedding-response.json`、`*.milvus-response.json`：不含认证头的请求与成功原始响应，SHA 写入报告。
- `results.sha256`：最终 JSON 文件 SHA。
- `annotation.html`：直接双击或浏览器打开。2/1/0/U 初始为空，每条候选标一次；支持浏览器保存、标签 JSON 导出/导入及原始证据下载。标签单独绑定 results SHA，原始结果不改写。部分标注/U 保留待审状态，全部完成后显示直接/部分/无关计数、无关保留比例、有用证据误删、每Query覆盖、库外准入情况；不自动给阈值结论。

运行后重新以新的只读快照检查测试191行、52向量及集合ID。源库/源集合不会被访问，本工具不能声称对源库实施了本轮在线复验。离线测试只证明工具保护路径，真实身份、READ ONLY 和资源一致性须由用户这次实际运行门禁验证。

## 文件职责

`run-independent.ps1`：交互、缓存编译、环境隔离、单次入口。
`IndependentReadOnlySql.java`：服务器只读事务与固定SELECT。
`IndependentRetrieval.java`：冻结/资源门禁、16次共享候选检索、原始证据与HTML输出。
`annotation-template.html`：独立人工标签存储/导出及标后指标。
`IndependentRetrievalOfflineTest.java`：无网络 JDBC 代理、身份/事务/SQL拒绝、SDK重试、预算/失败/锁、Float32边界和离线HTML样例测试。
`test-annotation.cjs`：无浏览器 DOM 替身测试保存、导入、导出、人工指标和原始证据字节不变。

最终准备验证：Java 离线测试71项、HTML JavaScript/DOM 替身测试14项，均0失败、0错误、0跳过。PowerShell解析0错误。Java入口编译成功，保留SDK弃用API和泛型转换的编译提示。初次编译遇到 `Map.of` 超过10项，改为 `Map.ofEntries` 后通过；初次HTML测试发现导入补齐空备注会改变标签表示，统一格式后通过。所有这些运行均无真实数据库连接、Embedding或Search；没有运行可能写测试库的Maven全量测试。

浏览器工具因禁止 `file://` 而无法做可视化验收；没有绕过限制。HTML功能通过离线脚本验证，用户手动运行后仍需在本机浏览器确认显示效果。
