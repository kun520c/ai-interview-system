# 真实运行环境检查

日期：2026-10-04（Asia/Shanghai）。工作区基线：`20a933a`，包括开始时已有的未提交 Jev 接入改动。本轮报告生成时间：2026-10-04T05:34:47.992272+00:00。

| 依赖 | 结论 | 实测证据 |
| --- | --- | --- |
| MySQL | PASS | JDBC 查询 `VERSION()` 为 8.0.45；真实 Mapper/事务、状态、评价和报告写入 |
| Docker / Milvus | PASS | 启动已有 milvus-etcd / milvus-minio / milvus-standalone，健康；SDK describe/search/insert |
| Embedding | PASS | qwen3.7-text-embedding；真实返回 1024 维、有限 float；文档与查询均真实生成 |
| DeepSeek | PASS（可用性） | 请求 deepseek-v4-flash，实际响应 model 为 deepseek-flash；答案与报告均真实调用。质量/结构失败见 E2E 报告 |
| Jev | BLOCKED | Process/User/Machine 环境和 local 配置均没有 Jev key。未做任何认证 Jev 推理；无凭据探测 HTTP 403，不能证明模型可用 |
| Spring Boot | PASS | Java 21.0.11 / Spring Boot 3.5.16；`@SpringBootTest(RANDOM_PORT)` 启动真实 Tomcat，通过 JDK HttpClient 发真实 HTTP，不使用 MockMvc |
| 原始知识覆盖 | FAIL | 开始时仅 1 READY 文档 / 1 ACTIVE 切片；5 道 EASY 主题仅 equals/hashCode 有相关知识 |

Collection 为 `knowledge_chunk_vectors_local`，默认库 `default`；五字段、dimension=1024、dynamic=false、autoID=false；索引 AUTOINDEX / COSINE，初始 indexedRows=totalRows=1。Schema 来源：[inventory.json](evidence/inventory.json)。

当前 agent 进程没有继承 DB_* 等用户变量。运行脚本只向子进程导入已有 Windows User-scope 配置，使用 ignored `application-local.yaml`。DeepSeek key 仅在本地配置指向官方 api.deepseek.com 时复用既有凭据，不打印、不写入报告或 Git 文件。Jev 未配置时只选择 DeepSeek 对照，不声称 Jev 已验证。

首次启动的 1 个 Error 是新脚本清除开关时留下空值，导致Milvus配置未装配；显式设置进程级 MILVUS_ENABLED=true 后真实检查通过。普通回归首次也因此将DEEPSEEK_ENABLED解析为null，导致1项隔离断言失败；脚本已用Remove-Item Env:真正删除变量，普通运行仅导入DB变量。没有改生产配置或测试断言。Jev 无凭据网络探测首次受沙箱 ProxyError 阻断，获准重试后 HTTP 403；没有把该探测当作推理成功。

## 执行记录

| 执行 | Tests | Failures | Errors | Skipped | 实际含义 |
| --- | --- | --- | --- | --- | --- |
| Inventory 首次启动 | 1 | 0 | 1 | 0 | 配置未装配 Milvus Bean；启动问题，非检索失败 |
| Inventory 明确启用真实依赖后 | 1 | 0 | 0 | 0 | 真实 MySQL / Embedding / Milvus |
| RAG 原知识库（30 query） | 1 | 0 | 0 | 0 | 真实检索；case 质量另计 |
| Pipeline（6 文档、5 过滤情形） | 1 | 0 | 0 | 0 | 真实 HTTP / Embedding / Milvus / MySQL |
| RAG 扩充语料（30 query） | 1 | 0 | 0 | 0 | 真实检索；case 质量另计 |
| Judge（17 case runs + 1 故障 + 3 消融） | 1 | 0 | 0 | 0 | 真实 DeepSeek；Jev 未测 |
| E2E 第一轮 | 1 | 0 | 0 | 0 | 采集成功；1 个真实答案 HTTP 500 |
| E2E 第二轮 | 1 | 0 | 0 | 0 | 采集成功；2 个真实报告 HTTP 502 |
| Recovery 首次采集 | 1 | 0 | 1 | 0 | 测试脚本误用 session.overall_score；已修正为 total_score |
| Recovery 修正后 | 1 | 0 | 0 | 0 | 报告与 FAILED 答案均可恢复；不抵消初次失败 |
| 普通回归首次 | 1412 | 1 | 0 | 19 | 新脚本留下空的DEEPSEEK_ENABLED；隔离测试解析为null，已改为真正删除环境项 |
| 补充离线契约与 Validator | 138 | 0 | 0 | 0 | 见 target/evaluation/focused.log |
| 普通全量回归 | 1412 | 0 | 0 | 19 | 见 target/evaluation/regression.log |

所有 AI baseline 测试均为 Maven/Surefire、`@ActiveProfiles("local")`、`@EnabledIfEnvironmentVariable` + `Assumptions.assumeTrue` 双层 opt-in。Maven 的一个采集测试包含多次真实调用；其成功只表示证据采集完成，不代表 AI cases 全部通过。

普通回归前清除 RUN_REAL_* 与 MILVUS_ENABLED，保留 application-test.yaml 的隔离配置；真实 Smoke Tests 和新 baseline collector 在普通运行中跳过。未改任何生产配置来迁就测试环境。

最终普通回归：1412项、失败0、错误0、跳过19；其中既有真实Smoke保护合计18项、新baseline collector1项。138项补充离线契约通过。[汇总数据](evidence/test-runs.json)。

## 本轮留下的本地数据

新文档 542–547、切片 484–490，均 READY/ACTIVE；原文档 485 / 切片 443 保留。五种临时过滤修改全部恢复：[post-run-corpus.json](evidence/post-run-corpus.json)。没有 drop、recreate 或 clear Collection，没有删除共享数据。

新增六个面试会话 2463–2468 及本轮合成用户/报告/弱项作为人工复核证据保留。2465 初次失败后只做一次同输入诊断恢复，当前仍 IN_PROGRESS；其余五个已 COMPLETED 且报告 READY（含需要恢复的 2466/2467）。合成密码/JWT 未写入证据。

本轮停止了测试 Tomcat；MySQL 及启动的 Milvus 容器保留运行。全部进程级 opt-in 开关随脚本清除，未修改用户/系统环境变量。
