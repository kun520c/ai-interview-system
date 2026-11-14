# E0-R2｜Mac Milvus 独立环境部署与验收

日期：2026-11-13，Asia/Shanghai。工作区 `/Users/kun/Developer/projects/ai-interview-system`，源码基线 `0c6f92afe331954f7cea37d33db688fe781cbf2c`。

## 结论与边界

**基础功能通过；稳定性有条件，不能宣布完全验收或长期稳定。** 全新实例首次启动与两次主动重启后均 healthy；真实 Java SDK 完成 1024 维 COSINE 插入、检索、精确删除、非目标保留及首次重启后持久化。两次主动重启的关闭阶段均出现 `panic: context canceled`，随后新进程启动成功；新实例启动期间也出现 `etcdserver: leader changed` 警告。观察时间短，不证明今后启动必然成功或不存在数据风险。没有导入正式题库、21 篇 RAG 文档，没有调用 Embedding/DeepSeek、修改业务状态机或 MySQL 数据。

## 1. 安全检查和旧数据备份

- 主机 Apple Silicon ARM64、物理内存 24 GiB，根卷约 288 GiB 可用；Docker 29.9.2、Compose 5.6.1，Docker VM `aarch64`、10 CPU、7.75 GiB 可见内存。20:30:13 容器内 `/proc/meminfo` 的 VM 快照 `MemAvailable=4,988,004 KiB`（约 4.76 GiB），swap 已用约 9 MiB；这是当时估算而非容器可用内存保证。操作时其他运行容器合计约 2.32 GiB，存在资源竞争；[官方 macOS 前提](https://milvus.io/docs/v2.6.x/prerequisite-docker.md)建议 Docker VM 2 CPU、8 GB 起步，当前可见内存接近但未达到 8 GiB 二进制值，不能认定有充足余量。
- 旧容器 `ai-interview-mac-milvus-1` ID `c86ec51440aafc2e3ff88a354327b2263feda5b2142442c7be03cc851535fee8`，退出码 134，重启计数 9；旧镜像 `milvusdb/milvus:v2.6.24`，ARM64、镜像 ID `sha256:c55810ca95c97437cc003f913b835524600fb95a14ce04955a92517c3ccab73f`。旧卷 `ai-interview-mac_ai-milvus-data` 挂至 `/var/lib/milvus`，备份前仅该停止容器引用。其他容器、卷及挂载均保留。
- 项目 MySQL `ai-interview-mac-mysql-1` healthy，`127.0.0.1:3308`，单独的 `ai-interview-mac_ai-mysql-data` 挂到 `/var/lib/mysql`；本轮未挂载该卷、未执行 MySQL SQL。既有业务库和测试库保持原有隔离设置。
- 停止的旧卷以只读方式挂入无网络、只读临时容器，归档至被 Git 忽略的 `/Users/kun/Developer/projects/ai-interview-system/backups/E0R2_MILVUS_20261113/old-milvus-volume.tar`，150,538,240 字节，SHA-256 `1112ab06387ad2c5c6c3e5dc409f578c63678d729abb601b52a22fdddb98f533`。GNU tar `-df` 与原卷逐项比较通过。再在 `/private/tmp` 安全解包并比对：135 个文件、32 个目录，类型、POSIX 模式、mtime、大小及逐文件 SHA-256 一致，临时副本已清理。Mac 解包未还原 root 属主；此校验**不是**旧服务可恢复性或跨库一致性验证。
- 同目录保留原 Compose 配置和旧容器 `embedEtcd.yaml`，SHA-256 分别为 `60eb30bf23a96d95ed98305e813a0492703e92eeebeb03ab14379cc223a81974`、`bac4fc22a7408161f22fe58f77a6648d15be7c00f5c141edc0f703dca020b18c`。备份目录权限 0700、文件 0600；本机单份备份仍有设备故障风险。

## 2. 官方问题与版本选择

[Milvus issue #53144](https://github.com/milvus-io/milvus/issues/53144) 记录 embedded etcd 选主时 `Session.Init` 获取 server ID 遇 `leader changed` 而 panic，与 E0-R1 的直接失败路径相符；本机当时的选主变化根因仍未知。[PR #53367](https://github.com/milvus-io/milvus/pull/53367) 在主线增加等待 etcd 就绪和重试。核对 [v2.6.26](https://raw.githubusercontent.com/milvus-io/milvus/v2.6.26/pkg/util/etcd/etcd_server.go)、[v2.6.27](https://raw.githubusercontent.com/milvus-io/milvus/v2.6.27/pkg/util/etcd/etcd_server.go) 及各自 session 源码，这两个标签未包含该修复；不应把 issue 的 fixed 标签当作 2.6 镜像已修复的证据。

选择有完整 release notes、与 [官方 2.6 Docker 文档](https://milvus.io/docs/v2.6.x/install_standalone-docker.md)一致的 `v2.6.26`，而非声称它修复了竞态。`v2.6.27` 已发布但当时说明仍未完整。固定 ARM64 镜像：`milvusdb/milvus:v2.6.26@sha256:f43e0cd0c9912c35379c349f7f3a3a806b9308462d43d1bfa18d007c6642d693`；Docker 本机 image ID 同摘要。官方该标签 embedded 脚本本身仍写 v2.6.25，不能仅凭脚本名推断镜像版本。

## 3. 独立部署和操作

配置为仓库根目录 `compose.milvus.e0r2.yaml`：project `ai-interview-e0r2`，容器 `ai-interview-e0r2-milvus`，独立卷 `ai-interview-e0r2-milvus-data`，embedded etcd 与 local storage；宿主回环 `19532→19530`、`9093→9091`，不占用 E0-R1 候选 `19531/9092`。操作前确认这些新名字和端口未占用；未包含 MySQL 服务。容器内存上限 4 GiB，是本机共享 VM 的保守上限而非性能验证。启动后测得约 190–307 MiB，第二次重启后约 260.5 MiB；只是瞬时值，不是峰值或负载容量结论。

```sh
docker compose -f compose.milvus.e0r2.yaml config --quiet
docker compose -f compose.milvus.e0r2.yaml up -d
docker compose -f compose.milvus.e0r2.yaml ps
curl -fsS http://127.0.0.1:9093/healthz
docker compose -f compose.milvus.e0r2.yaml logs --tail 100 milvus
```

需要暂停时使用 `docker compose -f compose.milvus.e0r2.yaml stop`；后续用 `up -d`，确认卷名及端口仍一致。不得使用 `down -v`、全局清理或删除原卷。当前健康检查返回 `OK`；最后观察 Docker status running/healthy、自动 RestartCount 0、OOMKilled=false；这个计数不包含两次人工 `docker restart`。

## 4. 实测结果

- 首次启动 20:24:43 左右 etcd term 2 选主并 `ready to serve client requests`。第一次重启后约 20:26:17 term 3 就绪，第二次约 20:27:30 term 4 就绪。初次启动和第一次重启选主附近有 `leader changed` 警告。日志中未观察到启动期 `Session.Init` panic；不能推广到未来启动。
- 两次主动重启停止旧进程时，分别在 20:26:15.788、20:27:29.410 记录 `panic: context canceled`；第一次栈显示 `internal/util/dependency/kv.CloseEtcdClient`，此前 embedded etcd 已关闭。两次新进程均恢复 healthy；未证明此异常对所有未刷盘写入、恢复或长期运行无影响，也未确认根因。它是本次稳定性验收的重要遗留项。
- 手工真实 SDK 验收 3 个阶段 seed/verify/cleanup，**3/0/0/0**（阶段数/失败/错误/跳过，退出码均 0，不计入 JUnit）。Java 21、项目 Milvus SDK 2.6.20，使用项目 `MilvusConfiguration`、`MilvusCollectionInitializer`、`MilvusVectorStoreClient` 和隔离集合 `e0r2_synthetic_bcfb4ee2f2d34c1091856c2205d2a161`。插入三个互相正交的 1024 维合成单位向量 A/B/C；COSINE 自身 1.0、其他 0.0。按 A 的 vector ID 删除，B 与 C 保留（A/B 共享 document ID），强一致查询并 flush。首次重启后 B/C 仍存在且各自 COSINE 为 1.0，A 不存在；随后精确删除 B/C，强一致查询为空。集合保留为空，未 drop。测试源码与三份精简日志在 `backups/E0R2_MILVUS_20261113/sdk-evidence/`；源码 SHA-256 `aebed23bd8c7c1b4ff76dd67d94c7a57a55776fa2b7761effcb3947df6932516`。
- 第二次重启后另做一次 Java SDK 只读检查，退出码 0：服务版本 2.6.26，health=true，两个测试 collection 均存在，合成集合强一致查询仍为 0 行；源码及日志同在 `sdk-evidence/`。这是独立只读检查，不合并进上述 3 个写入验收阶段或 JUnit 计数。
- 定向 Maven/Surefire：`EnvironmentIsolationInitializerTest` **23/0/0/0**，真实 `RealMilvusSchemaValidationSmokeTest` **2/0/0/0**，合计 **25/0/0/0**（T/F/E/S），BUILD SUCCESS。真实测试使用自身 `local,test-real`、`default/knowledge_chunk_vectors_test`，验证 1024 维 schema、COSINE/AUTOINDEX 与错误 3 维 schema 拒绝，唯一临时 mismatch collection 清理并确认不存在；正常 test collection 保留。该 Spring 上下文未连接 MySQL 或模型服务。命令使用临时变量，未写入持久环境：

```sh
RUN_REAL_MILVUS_SCHEMA_TEST=true MILVUS_URI=http://127.0.0.1:19532 \
TEST_MILVUS_DATABASE_NAME=default TEST_MILVUS_COLLECTION_NAME=knowledge_chunk_vectors_test \
TEST_DB_URL=jdbc:mysql://127.0.0.1:3308/ai_interview_test \
./mvnw -B -ntp -o -Dtest=RealMilvusSchemaValidationSmokeTest,EnvironmentIsolationInitializerTest test
```

Surefire XML 在 `target/surefire-reports/`，可能被后续运行覆盖。SDK 工具首次执行遇 Zsh classpath 解析错误，修正后默认沙箱禁止回环连接；在获准访问回环后以上三个阶段通过。全量回归、真实 Embedding、真实 DeepSeek、完整应用端到端及长期压力测试均未运行，不以这些测试通过作推断。

## 5. 项目兼容、恢复和后续限制

基础配置 `application.yaml` 默认 URI 为 `localhost:19530`，不会自动指向本实例；将来显式启用 Milvus 时需使用 `MILVUS_URI=http://127.0.0.1:19532` 并确认目标服务器身份。`evaluation` 使用独立 `EVALUATION_MILVUS_URI`；local/test/evaluation 仍须遵循各自数据库与 collection 门禁，不得把测试或 evaluation 指向 formal local。此处的直接 SDK 和真实 schema 测试说明协议与项目配置兼容，不等于完整应用或 RAG 入库已通过。

恢复旧资料时先核验备份 SHA-256 和原配置、目标容器及卷的身份；仅在**新建、空的、独立命名卷**与独立端口上演练解包及原版启动，保留原卷和归档，严禁将归档覆盖原卷或新实例数据。属主/权限、旧版启动、schema、collection 内容和 MySQL/Milvus 对账均需单独验证；当前尚未执行服务级恢复演练。不能从此次合成向量验收推定旧数据完整或历史业务已恢复。

后续在正式 RAG 入库前，应先解决或明确接受两次关闭 panic 与 embedded etcd 启动竞态、改善 Docker 内存余量，并开展多次冷启动及较长时间观测；如考虑独立健康检查的外置 etcd，需另行设计与验证，不假定其已在本轮实施。现有新实例仅适合受控开发验证，不应据此承诺生产级可靠性。无 Git commit/push。
