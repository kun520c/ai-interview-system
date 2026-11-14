# E0-R3｜Milvus 稳定性收口与正式数据入库准入

执行：2026-11-13，Asia/Shanghai。结论：**CONDITIONAL_GO**，仅允许在以下条件满足且另行授权后，向隔离 evaluation 环境进行小规模、串行的正式数据试入库；不是长期生产稳定性证明，也不授权本轮导入或付费调用。原因是已刷盘合成向量在一次受控停启后完整恢复，SDK 同客户端读请求恢复，但停止 panic 持续复现、启动竞态修复尚未回移、未刷盘写入和长期运行均未验证。若出现持续启动失败、对账不一致、数据损坏或无法恢复，停止入库并改判 NO_GO。

## 1. 范围、实例与旧数据保护

已阅读 AGENTS.md、E0-R1、E0-R2、工程台账、D1/D3 计划及相关配置/实现。本轮只在新实例上操作唯一合成集合和 Schema Smoke 自建的唯一临时 mismatch 集合，不部署、不升级 SDK、不调用模型、不导入业务资料，不修改旧容器/卷、数据库业务数据或业务源码。

- 新容器 `ai-interview-e0r2-milvus`（ID `9e708121918a0bde1568d15c8684e7b4145bd73287fee1327d41ed98f85abe4e`）镜像为 **`milvusdb/milvus:v2.6.26`**，固定摘要 `sha256:f43e0cd0c9912c35379c349f7f3a3a806b9308462d43d1bfa18d007c6642d693`，不是题面中的 `milvusdb/milvus.6.26`；127.0.0.1:19532→19530、9093→9091，独立卷 `ai-interview-e0r2-milvus-data`。
- 旧 `ai-interview-mac-milvus-1`（ID `c86ec51440aafc2e3ff88a354327b2263feda5b2142442c7be03cc851535fee8`，v2.6.24）仍 exited/unhealthy，RestartCount 9，挂载 `ai-interview-mac_ai-milvus-data`；未启动或更改。MySQL 8.0.45 的 `ai-interview-mac-mysql-1` healthy，3308 TCP 连接成功，实际只读 SQL 成功。
- 原卷离线备份 `backups/E0R2_MILVUS_20261113/old-milvus-volume.tar` 150,538,240 字节，本轮 SHA-256 重算 `1112ab06387ad2c5c6c3e5dc409f578c63678d729abb601b52a22fdddb98f533` 与 E0-R2 一致；135 文件、32 目录。E0-R2 的 `tar -df` 对比原卷、独立解包后逐文件哈希/大小/模式/mtime 对比通过；宿主解包未还原 root 属主。**未进行旧服务级恢复演练或跨库对账**，不证明旧业务已恢复或卷中无数据。

## 2. 实际验证与时序

本轮 `RealMilvusSchemaValidationSmokeTest` 使用其 `local,test-real` 配置及临时环境变量指向 19532/test 集合，Maven/Surefire 离线执行 **2 tests / 0 failures / 0 errors / 0 skipped**，BUILD SUCCESS；验证项目 Milvus 配置和 initializer 的 1024 维 schema、COSINE/AUTOINDEX 与不匹配拒绝，唯一临时 mismatch 集合已清理确认。该定向测试不等于完整应用端到端启动；未调用 MySQL 或模型。Java 21、项目 SDK 2.6.20 的独立探针调用 `MilvusConfiguration → MilvusCollectionInitializer → MilvusVectorStoreClient → Milvus SDK`；此为真实服务合成验证，非 JUnit。

新集合 `e0r3_synthetic_6a1183deeacca38effe5a1d3b70a51f2`：3 个 1024 维正交单位向量 A/B/C；按 ID 删除 A 后强一致读出 B/C 两条，A 与 B 共用 document ID，C 使用不同 document ID，用于验证精确删除；显式 flush。重启前后 B/C ID、document ID、chunk index、数量均相同；B、C 各自查询的分数分别为自身 1.0、另一条 0.0。重启后独立新客户端再次验证，精确删除 B/C，flush 后强一致 count=0，集合保留未 drop。seed/watch/verify/cleanup **4/0/0/0**（阶段/失败/错误/跳过，非 JUnit），各退出 0。

北京时间：20:36:09 同一 SDK 客户端 baseline 成功；20:36:21 请求 `docker stop`（30 秒超时），20:36:22 退出 **134**、OOM=false，日志显示 embedded etcd 已关闭后 `CloseEtcdClient` 抛 `panic: context canceled`。20:36:31 检索在 10004 ms 返回 `ExternalServiceException → MilvusClientException → DEADLINE_EXCEEDED`。20:36:37 显式启动；20:36:38.911 出现 `leader changed`、慢 read-only range 1.595 秒，20:36:38.912 etcd ready，随后 Proxy ready。启动窗口另有两次 `no available shard leaders`，20:36:49 同一客户端成功检索且强一致查询 B/C，累计三次失败。上述时间仅为本次观测，不构成恢复时间保证；读请求恢复不证明中断期间写入安全或自动重试。健康检查也不保证所有 shard 立即可检索。

关闭 panic 在本次及 E0-R2 两次操作中均出现，本轮已 flush 数据恢复、服务恢复；**未证明未刷盘或在途写入不丢失，也未查明 panic 根因**。本次 `leader changed` 是可恢复的启动警告，但 E0-R1 的旧实例确曾因同类选主错误在 `Session.Init` panic 并持续退出，不能一概忽略。重启后观察期间未检出新的 panic/ERROR/FATAL（按日志关键字筛查，不等于所有 warning 已解释）；初次启动/恢复期还有组件未就绪等 warning 与 SDK shard unavailable，服务最终恢复。无本次数据损坏或持续不可用证据，但也未做全量磁盘完整性验证。

检查快照：新容器重启前运行约 9 分钟、Docker RestartCount 0，恢复后 running/healthy、RestartCount 0、OOMKilled=false；该计数不包含人工 stop/start，不能推断过去无限期无异常。HTTP `/healthz` 返回 OK，SDK health=true。`docker stats` 单点约 1.97% CPU、330.7 MiB/4 GiB，非峰值、压力测试或容量承诺。采样时其他容器也在运行，资源竞争仍需监控。

## 3. 当前数据基线（2026-11-13 本次快照）

- 仓库有 **21 题、97 个评分点**（难度 5/7/9，每题权重 100）及 **21 篇** RAG Markdown，属于离线资产；D1 **新增 42 题**为方案，目标总数 63，不是已写好的 42 条；D3 **新增 30 篇**为方案，目标总数 51，不是已完成的 51 篇。历史 52 chunk 仅离线切片结果。
- MySQL 3308 的只读计数：`ai_interview` 和 `ai_interview_test` 的 question、question_scoring_point、knowledge_document、knowledge_chunk 各为 0；本次未统计其他业务表。该实例仅列出这两个 `ai_interview%` schema，尚无 `ai_interview_evaluation`。这是查询时快照，不是 Windows 数据或未来状态。
- 新 Milvus `default` 的 SDK 强一致 `count(*)`：E0-R2 合成集合、E0-R3 合成集合、`knowledge_chunk_vectors_test` 各 0；当前列举共三集合，evaluation 集合不存在。只能说明该实例/数据库当前无向量，旧实例卷不可访问、向量数量未知；不可把它与新实例的 0 混同。

## 4. 版本与风险

官方 [v2.6.26…v2.6.27 差异](https://github.com/milvus-io/milvus/compare/v2.6.26...v2.6.27)存在其他变更，但 embedded etcd 启动及 session 关键路径相同，均依赖 etcd 3.5.23。两个 tag 和核验时 [2.6 分支固定提交 `706cb6e`](https://github.com/milvus-io/milvus/blob/706cb6ef3fe720004542dd0aa0962f2528d06942/pkg/util/etcd/etcd_server.go) 的 `etcd_server.go`、`session_util.go`、`etcd_util.go`、`roles.go` 对应 Git blob 分别一致。原始实现 `StartEtcd` 后未等待 ReadyNotify，session 获取 ID 遇暂时性错误可能上抛至 panic。

[PR #53367](https://github.com/milvus-io/milvus/pull/53367) 以 [ecdaf12](https://github.com/milvus-io/milvus/commit/ecdaf12d0000471ff8ebc3f95da3d751a3607bb6) 合入主线，增加 embedded etcd ReadyNotify 等待及 server ID 的暂时性错误重试。提交关系与对应实现对比均不支持其已回移至所核验的 2.6 分支；[issue #53144](https://github.com/milvus-io/milvus/issues/53144) 描述关联启动故障。因此 **不能仅因 v2.6.27 更新就认为竞态已修复**。该 PR 针对启动，不能据此宣称修复停止阶段 panic。后续若考虑含等价修复的版本或独立 etcd，应先另行设计新卷、数据布局/迁移、备份、恢复和回滚，在隔离环境验证；本轮不实施。

## 5. 准入约束及 D1/D3 顺序（方案，未执行）

1. 入库前另行授权与预算确认；先备份并验证恢复路径，记录实例身份、版本、卷、schema/collection、run ID、输入清单与基线；评估 Docker 内存余量。准备并审阅当前完整 13 表 DDL，创建专用 `ai_interview_evaluation`；`sql/ai_interview.sql` 含 `CREATE/USE ai_interview`，不能原样执行到 evaluation；旧 `ISOLATED_SCHEMAS.sql` 缺两个 attempt 字段，不可直接当完整脚本。使用单一 `evaluation` profile，明确 MySQL `127.0.0.1:3308/ai_interview_evaluation` 与 Milvus `http://127.0.0.1:19532/default/knowledge_chunk_vectors_evaluation`，保留环境隔离门禁；再次核实实际目标。
2. 审核 H01 评分口径及题库内容，先将仓库原 21 题/97 评分点经管理员接口串行导入，按逻辑 ID、内容指纹、数据库 ID、状态、权重和难度计数复核。新增 42 题先离线编写完整题干、答案、评分点并审核，按 D1 的 EASY +10、MEDIUM +14、HARD +18 扩至 63；随机选题为独立实施验收，不假定已实现。
3. 对原 21 篇文档先冻结版本、来源和许可，复查 H01 与 M02 来源固定版本；重新离线切片审查。经单独授权与付费预算，先选 1–3 篇：逐篇上传取得 UPLOADED，再显式 process，等待 READY 后才继续。记录规范化 hash、document/chunk ID、版本、model/profile、vector ID；核对 MySQL READY/ACTIVE 与 Milvus 双向身份及可检索性。通过后串行完成原 21 篇，每小批留快照和核验记录。
4. 新增 42 题与 30 篇主题文档可并行离线准备，但先审核并冻结扩题与证据映射，再小批串行扩知识语料至 51 篇，逐批对账。Embedding 成本按实际每篇 chunk 数、batchSize 和供应商计费另行估算；不把历史 52 作为实际写入数。
5. 任一 FAILED、超时、启动异常、未确认提交、孤儿向量或对账不符时立即停止后续处理，保留日志与 attempt ID；先查明实际状态和备份可恢复性，再制定补偿。只可按本轮明确生成的 ID 清理，禁止 drop 共享 collection、清空 schema/卷或覆盖原数据。任何扩大规模必须重新评估风险。

## 6. 证据、验证限制与 Git

保留 `backups/E0R3_MILVUS_20261113/` 内探针源码、阶段日志、只读盘点、Maven 日志及重启日志摘录；探针 SHA-256 `2c3b823218879a9d7dd627d7148681f2afd7396f5615a53d78ec59bd54744bd3`。原始报告 E0-R1/R2 保留。未运行完整回归、完整应用/RAG 端到端、真实 Embedding/DeepSeek、负载/长时测试、断电或未刷盘写入故障测试；不能以本次短测证明长期稳定。

HEAD `0c6f92afe331954f7cea37d33db688fe781cbf2c`，实时远端 main 与本地跟踪引用 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`，本地领先 8、落后 0；本批无提交 SHA。保留原有未提交更改，本轮未 add、commit、push、reset、stash 或 clean。
