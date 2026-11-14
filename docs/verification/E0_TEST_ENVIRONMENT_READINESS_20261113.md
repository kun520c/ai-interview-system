# E0｜真实测试环境准备与数据补审

执行日期：2026-11-13（Asia/Shanghai）。代码基线：`0c6f92afe331954f7cea37d33db688fe781cbf2c`。**结论：BLOCKED**：MySQL 已恢复，但 Milvus 未能启动，跨库身份、真实向量和 evaluation 隔离环境均未核验。本报告只记录本轮观测，不把历史数字视为现状。

## 1. 环境盘点与恢复

- Docker Desktop Engine/Client 29.9.2，`desktop-linux`，arm64。恢复前共 68 个容器（8 运行、60 退出）和 52 个命名卷；这不是全部卷均属于本项目的意思。恢复后 9 运行、59 退出。
- `ai-interview-mac-mysql-1`：MySQL 8.0.45，原退出码 0，专用卷 `ai-interview-mac_ai-mysql-data`，回环 `127.0.0.1:3308→3306`。启动前只读核实卷内已有 MySQL 系统库、`ai_interview` 和 `ai_interview_test` 目录；未发现 `ai_interview_evaluation` 目录。目录存在不替代 SQL 核验。原容器 `docker start` 后 healthy，重启次数 0，保留运行供后续检查。
- `ai-interview-mac-milvus-1`：Milvus 2.6.24，standalone、embedded etcd 和 local storage，专用卷 `ai-interview-mac_ai-milvus-data`，回环 19530/9091。原先退出码 134；原容器启动后健康检查失败并自动重启 9 次，服务端口拒绝连接。日志出现 `etcdserver: leader changed`，随后 session 初始化 panic；这不足以证明数据损坏或定位根因。为终止循环已停止，最终 exited/unhealthy，退出码 134，OOMKilled=false。卷未删除、重建或清空。
- 项目 bridge 无活动 peer，两个卷挂载归属已核实；启动前 3308、19530、9091 空闲。另有六个历史临时 MySQL 容器，端口 13318–13323，`/var/lib/mysql` 为 tmpfs 且均停止，不当作可恢复持久数据；没有改动这些容器。其他项目容器和卷未操作。
- 恢复前经宿主机 TCP 探测 3306、3307、3308、19530、9091 均拒绝连接；沙箱内 PermissionError 不是停服证据。恢复后 3308 可连接；Milvus 停止后 19530/9091 不可连接。
- `compose.mac.local.yaml` 是未跟踪的用户文件：MySQL 8.0.45 和 Milvus 2.6.24 均为 ARM64、绑定 loopback、分别使用项目命名卷；Milvus 需 `vector` profile。Compose 5.6.1 `config --quiet` 退出 0，识别 mysql/milvus 和两个卷。MySQL 配有只读 schema 初始化挂载，因此空数据卷启动可能触发初始化；本轮先确认已有库目录，再直接启动原容器，未执行 `compose up`、数据库初始化或导入。未执行 `down -v`、删卷或清库。
- `.env` 记载 local 目标为 `127.0.0.1:3308/ai_interview`、测试库 `ai_interview_test`，Milvus 目标 `default/knowledge_chunk_vectors_local`，并设置 `MILVUS_ENABLED=false`；这是配置而非运行中应用的解析结果。当前 shell 未导出相关配置，`application-local.yaml` 不存在；不能假定 Spring 自动加载 `.env`。源码默认 Milvus URI 为 `localhost:19530`，默认 embedding 模型 `qwen3.7-text-embedding`、维度 1024、profile `qwen3.7-text-embedding-1024-dense-v1`。

## 2. 只读数据核验

2026-11-13 20:00:29–20:00:34 +08:00，JDBC 先确认实际库及服务器，再使用 REPEATABLE READ、READ ONLY 一致性快照及显式 `ROLLBACK`。宿主 `127.0.0.1:3308` 对应 MySQL 8.0.45、容器 hostname `02ccbfd0e120`、内部 `@@port=3306`。

- `ai_interview`：13 张核心 InnoDB 表均存在；`user=1`，其余 12 表各为 0，包括 question、question_scoring_point、knowledge_document、knowledge_chunk、interview_session、interview_answer、answer_evaluation、interview_question、interview_report、user_weakness、rag_retrieval_batch、rag_hit_log。未读取用户正文。
- question 的 difficulty/category/status 分组、评分点分组及逐题全部/启用评分点和权重，以及 document/chunk 状态、model/profile 分组均为 **0 行**；没有题目可核验权重是否为 100。仓库 21 个题目逻辑身份和 21 篇文档在此库均无匹配。空库不能证明历史数据不存在于其他实例。
- 36 项完整性 SELECT 成功、异常计数均为 0，覆盖评分点孤儿、缺失与重复、文档/chunk 孤儿及版本、vector ID 重复、面试快照/答案/评价/追踪/报告关联等；业务数据为空，属于空样本结果，不证明有数据时正确。文档和 chunk 身份集合前后指纹相同，均为空集合。
- `ai_interview_test` 同一实例 13 表均存在且各 0 行；两个库均可见 `interview_answer.attempt_id` 和 `knowledge_document.processing_attempt_id`。业务账号只看到各自业务库及系统 schema；随后用容器内已有管理权限只读 `SHOW DATABASES` 和 information_schema，确认该实例仅有这两个业务库及四个系统库，`ai_interview_evaluation` 不存在，其他实例未查询。
- 首次检查结束时 JDBC 在 autocommit 模式调用 `rollback()` 报错，且一条 rag_hit_log 检查 SQL 列名错误；随后修正并重跑，最终 36/36 成功且显式 SQL 回滚成功。无数据写入。去敏临时结果 `/private/tmp/ai-e0-readonly.rwjASE/report-confirmed.json`；临时文件不作为永久备份。

**Milvus BLOCKED**：未能连接，database、collection 是否存在、schema、索引、实际实体数、ID 和 MySQL chunk 的双向关系均未知。源码约定的 5 字段、1024 维、COSINE/AUTOINDEX 和配置 profile 不能替代实际 describe/query。历史 52 chunk/向量或本轮离线切片不得当作现状。

修复后只读 list/describe，并以强一致 scalar count 和分页枚举 `vector_id/document_id/chunk_index/embedding_version`，与 MySQL 的 ACTIVE、READY、当前版本、匹配 model/profile 的 chunk 双向比较；分别报告缺失、孤儿、版本不符及重复。`get_stats.rowCount` 可能包含已删除 segment 行，不能单独证明有效实体数。冻结写入或记录前后水位，避免把跨库时间差误判为不一致；不调用 embedding、不通过应用初始化器自动创建 collection。

## 3. 恢复与独立环境方案：均为下一步，未执行

优先保留原数据，先确认备份目标和可用空间，离线备份整个停止的 Milvus 卷（包含内嵌 etcd 和本地数据），记录时间、卷身份、校验值及恢复演练；在独立副本、独立端口诊断，不能删除原 etcd 元数据或强制新建集群。恢复结果需重新做身份和双向核验。

如原实例无法安全恢复，可另建独立 Compose project/bridge、独立命名卷，建议 MySQL `127.0.0.1:13324/ai_interview_evaluation`，Milvus `127.0.0.1:19531`、health `9092`、专用实例 `default/knowledge_chunk_vectors_evaluation`；本轮这三个端口连接被拒且未发现同名前缀容器/卷，实施前仍须重新检查占用。显式配置 `evaluation` profile 及对应 JDBC URL、Milvus URI/database/collection；`application-evaluation.yaml` 默认 MySQL 端口是 3306，不能假设它指向 Mac 的 3308。普通测试使用 `ai_interview_test` 和 test collection，local 维持 `ai_interview` 与 local collection；测试或 evaluation 不得指向 local。`EnvironmentIsolationInitializer` 不能替代服务器身份、Milvus URI/database 的人工核验。

**下一独立写入步骤的实施清单（本轮未授权执行）**：

1. 明确实例身份、目标库/collection、端口、卷、账号权限、数据来源和备份恢复方案；确认非目标服务与端口冲突。
2. 对空的 `ai_interview_evaluation` 准备并审阅仅该目标的 schema 脚本；`sql/ai_interview.sql` 含 `CREATE/USE ai_interview`，不得原样导入。`docs/environment/ISOLATED_SCHEMAS.sql` 缺后续两个 attempt 字段，不可直接当当前 schema；先比对信息架构和现行结构，避免重复迁移。
3. 若源数据及双向身份、model/profile 经核验，再单独审查复制题库/评分点及文档/chunk/向量的映射方案；保持或显式重映射 document ID，不复制用户、凭据和面试数据。否则从审查后的源重建，并预算所有需重做的 Embedding。任何导入、集合创建、Schema 执行及模型调用另行明确授权。
4. 测试数据采用 runId、独立合成账号、请求及精确 ID 清单；先备份并停写后验证，仅清理本次 ID 和依赖，核验非目标数据保全。不 drop 共享库、集合或卷。备份分 MySQL 一致逻辑快照与 Milvus 及其存储元数据配套快照，不假定跨库天然原子。

## 4. 数据准备与后续预算

- 离线仓库核验 21 个题目定义（E/M/H=5/7/9）和 97 个评分点，每题权重 100、至少一个 CORE；这些是来源文件而非在线数据。20 个非 H01 主题为人工冻结候选；H01 保留主题身份，修订 JSON/Markdown 参考答案和 SP4 的 Java 21 `instanceof` 静态类型条件，保持历史会话快照。实时身份以 difficulty/category/knowledgePoint 和字段比较，不按历史自增 ID 假定对应。
- 规划 42 新题：E06–E15 10 题、M08–M21 14 题、H10–H27 18 题；目标 15/21/27 共 63，依赖原 21 主题可复用。已有 selector 确定性选首题，扩容不自动产生随机组卷；本轮不修改算法或编写新题。
- 21 篇现有 Markdown 为复用候选；离线 97 个评分点映射中 H01-SP4 有条件歧义，其余仍需人工审查。复核来源许可、版本、题目定向偏差；M02 的移动分支需固定版本。目标 51 篇是 21+30 篇规划，缺口为 30 篇规划正文，最终数量依覆盖质量调整；现有 READY 的更新/迁移需独立设计，不能直接覆盖并假定向量同步。
- 后续顺序：核验原数据与身份 → 审核 H01、题目和来源 → 分批编写/人工复审 → 内容、评分点、切片及版本清单校验 → 独立授权后在 evaluation 隔离环境导入题及点、上传文档并显式处理 → 跨库双向核验 → 独立检索评估 → 人工与多 AI 面试。随机 selector 为单独阶段。
- D4 计划 A0 先 1 场 EASY，上限 11 DeepSeek、10 query Embedding、10 考生请求，候选 ¥20；A1 12 场上限 180、168、168 次，候选 ¥100。均为规划上限，分别批准，真实价格、usage、配额、金额当前未知；80% 停开新场，硬上限停止外呼，恢复储备另批。导入 Embedding 请求数按各篇实际 chunk 的 `Σceil(chunks/batchSize)`，batchSize ≤20；token/费用未知。B-live 与 A 另计预算；B-load 使用替身、装配尚未实现。
- 独立账号、JWT、session、runId 和结果清单；evaluation 共用时串行并分批隔离，考生模型不见答案、评分点、RAG 或其他角色结果；人工评分独立。停止条件、记录及清理须在后续真实测试前明确。

## 5. 验证与边界

本轮离线静态检查：21 题、97 点、5/7/9，题目逻辑身份及题干无精确重复、每题权重 100 且有 CORE；21 篇源文档、42 个唯一新增题号和 30 个唯一文档计划编号。未重跑 D0 的生产 chunker。

本轮没有代码变更、测试运行、批量导入、真实模型请求、Git commit/push/reset/clean/stash；JUnit、Mock、完整构建、真实模型和真实 Milvus 测试均未执行，T/F/E/S 不适用。MySQL SQL 为只读核验而非功能集成测试。SQL 写入和模型调用 0；启动原容器会产生正常服务运行时磁盘写入，不等于数据文件完全未变化。原有未提交文件保留；本轮文档未暂存、未提交、未推送。

详见 [D0 补审](D0_DATA_READINESS_AUDIT_20261113.md)、[D1](D1_QUESTION_BANK_EXPANSION_PLAN_20261113.md)、[D3](D3_RAG_CORPUS_EXPANSION_PLAN_20261113.md)、[D4](D4_MULTI_AGENT_TEST_PLAN_20261113.md)。

## 6. E0-R2 后续更新（2026-11-13）

以上结论保留为 E0 当时状态。新授权的 [E0-R2 验收](E0R2_MAC_MILVUS_DEPLOYMENT_20261113.md) 已对停止的旧 Milvus 卷完成只读归档、原卷比较与独立解包文件校验，并保留原卷；尚未验证旧服务恢复或历史跨库关联。新建 `ai-interview-e0r2` 独立实例、卷和 `127.0.0.1:19532`，真实 Java SDK 1024 维 COSINE、精确删除、一次重启后数据持久化通过，定向 Maven 25/0/0/0。第二次主动重启后 SDK 只读健康与集合检查通过。

**稳定性仍有条件**：新实例启动期出现 `leader changed` 警告，两次主动重启的关闭阶段均有 `panic: context canceled`，之后服务恢复 healthy；v2.6.26 不包含上游 embedded etcd 启动竞态修复。短时观察不能证明长期稳定或原数据恢复。MySQL 未写入，未调用模型或导入 RAG；后续正式测试及扩大数据仍需单独授权和费用预算。
