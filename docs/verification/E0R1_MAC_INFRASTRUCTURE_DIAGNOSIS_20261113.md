# E0-R1｜Windows 迁移 Mac 后的 MySQL / Milvus 环境专项诊断

执行日期：2026-11-13，Asia/Shanghai。工作目录：`/Users/kun/Developer/projects/ai-interview-system`。本轮结论：**MySQL 在线且当前业务数据基本为空；Milvus 不可用，直接退出原因可定位到 session 初始化遇到 etcd 选主变化，选主变化的根因尚未证实。** 本轮未启动、停止或重建服务，未执行数据写入、导入、清理、模型调用或测试；本文件为唯一交付写入。下面的状态是检查时的快照，不保证以后不变。

## 1. 范围、基线和保护

已阅读仓库 `AGENTS.md`、[E0](E0_TEST_ENVIRONMENT_READINESS_20261113.md)、[D0](D0_DATA_READINESS_AUDIT_20261113.md)、[本地运行指南](../GETTING_STARTED.md)、应用及测试配置、未跟踪的 `compose.mac.local.yaml`，并查阅 Mac 迁移记录 `/Users/kun/Documents/Mac-Dev-Setup/mac-java-development-validation-20261024.md`。未在报告记录凭据或完整环境变量。

入场 `main` 的 HEAD 为 `0c6f92afe331954f7cea37d33db688fe781cbf2c`，相对本机 `origin/main` 跟踪引用领先 8、落后 0；未联网刷新远端，不代表实时远端状态。既有未提交变更：两份工程台账、`mvnw` 的可执行位；未跟踪文件包括本地 Compose、两处 `.DS_Store`、MQ 设计报告及 D0/D1/D3/D4/E0 报告，均予保留。本报告未暂存、提交或推送。

## 2. 主机、Docker 和部署形态

- 实测 `uname -m` 为 `arm64`，macOS **26.7.1**；与环境说明中的 26.6 不同，以实测为准。Docker Desktop 4.96.3，Client/Engine 29.9.2，Server `linux/arm64`，Compose v5.6.1。Docker Engine 报告 10 CPU、8,319,504,384 字节（约 7.75 GiB）可用内存；这是 VM 可见内存，不能直接等同 Desktop 设置界面的精确分配值。根文件系统报告约 288 GiB 可用，不能代替 Docker 虚拟磁盘内剩余空间及 fsync 延迟测量。
- 官方 [Milvus 2.6 macOS 前提](https://milvus.io/docs/v2.6.x/prerequisite-docker.md)要求 Docker VM 至少 2 vCPU 和 8 GB 初始内存。CPU 数满足；VM 可见内存接近该门槛，设置值、其他容器竞争及故障发生时的可用内存未获可靠快照，不能断言内存不足导致此次故障，也不能视为充足余量。
- `docker ps -a` 与卷清单确认项目 MySQL、Milvus 容器及各自命名卷存在。项目容器指定、实际镜像均为 ARM64：MySQL `mysql:8.0.45`；Milvus `milvusdb/milvus:v2.6.24`。主机、Docker Server 和镜像架构一致，无架构不兼容的直接证据。迁移记录及其历史日志记录 2026-10-24 同名同版本 ARM64 Milvus 曾 healthy，2 个 schema 测试通过并验证两条合成向量 CRUD；缺少当时容器 ID / digest，不证明当前是同一不可变实例，也不证明当前可用。
- `ai-interview-mac-mysql-1` 运行且 healthy，`127.0.0.1:3308→3306`；`lsof` 确认 Docker 监听 3308。Milvus 已退出，19530 和 9091 无监听；因此当前没有这两个端口占用证据，但停止状态不能排除故障时曾有冲突。其他项目两个 MySQL 监听 13306、13316；六个项目历史临时 MySQL 容器均停止、数据目录为 tmpfs，不能当作持久备份。本轮 `lsof` 未发现候选 13324、19531、9092 的监听，未来实施前必须再次核验。
- 项目 Compose 的 Milvus 以 `milvus run standalone` 运行，`ETCD_USE_EMBED=true`，etcd 数据目录 `/var/lib/milvus/etcd`，配置路径 `/milvus/configs/embedEtcd.yaml`，本地存储；停止容器内配置的只读副本与 Compose 一致，etcd 内部端口 2379、backend quota 4 GiB、revision compaction 保留 1000；不是依赖独立 etcd/MinIO 容器的部署。全容器清单未发现独立 etcd/MinIO。嵌入式模式与 [Milvus 2.6 官方说明](https://milvus.io/docs/v2.6.x/install_standalone-docker.md)一致，但不能据此推定其内部数据健康。

## 3. Milvus 退出证据与诊断

### 3.1 实际容器及数据

`ai-interview-mac-milvus-1` 容器 ID 前缀 `c86ec51440aa`；镜像摘要 `sha256:c55810ca95c97437cc003f913b835524600fb95a14ce04955a92517c3ccab73f`，启动命令 `/tini -- milvus run standalone`；重启策略 `unless-stopped`，Docker 记录 RestartCount=9、Exited 134、health `unhealthy`、OOMKilled=false、State.Error 为空；无额外容器 Memory/NanoCpus/CpuQuota 限制。最近一次运行约为 19:57:05.274–19:57:06.483，约 1.21 秒。健康探针设置间隔 10 秒、超时 5 秒、重试 12 次、启动宽限 90 秒；留存健康记录属于 11 月 2 日，最近一次短运行没有新探针记录，不能将本次进程退出归因于健康检查。

唯一数据卷 `ai-interview-mac_ai-milvus-data` 挂载至 `/var/lib/milvus`，读写挂载、local 驱动；创建于 10 月 24 日 18:54:17 +08，与容器同秒，当前仅此容器引用。配置文件路径和整个数据卷均应保留。该创建时间及引用关系不能证明卷内容来源、完整历史或此前是否导入旧版数据；也没有足够证据认定错误复用了旧版卷。

### 3.2 最近一次启动的脱敏时间线

11 月 13 日北京时间约 19:57:05.425，etcd 打开已有 `/var/lib/milvus/etcd/member/snap/db`；05.461 以既有 member 重启；05.990 出现 etcd `apply request took too long`（约 996.554 ms，阈值 100 ms）；单 voter 经 term 49 无 leader 后在 term 50 自选 leader；06.463 在选出 leader 的同一毫秒出现 `etcdserver: leader changed`，06.466 session 初始化因该错误 panic，06.484 进程退出。时间线表明 **`leader changed` 是本次初始化失败的直接触发**；其发生在选主附近，并非能够被简单忽略的退出后噪声，但这一错误本身并未说明选主变化为什么发生。留存日志约 268,013 行，其中 11 月 1 日 UTC 即有 37 次同类 panic，11 月 13 日有 9 次；故障早于 E0，计数不能与 RestartCount 机械相加。日志中 etcd 版本为 3.5.23、选举超时 1 秒；打开后端数据库耗时约 0.267 ms。慢请求并不等于 fsync 耗时，不能测出实际持久化尾延迟或建立因果关系。v2.6.24 [对应源码](https://github.com/milvus-io/milvus/blob/v2.6.24/internal/util/sessionutil/session_util.go#L326-L340)显示 session 初始化获取 server ID 遇错会 panic；代码解释了退出路径，不足以证明 leader 变化根因。[etcd 官方说明](https://etcd.io/docs/v3.3/faq/)指出磁盘延迟尖峰可能影响 leader 存活；这只是待核验机制，不是本机根因证明。

### 3.3 排查结论和边界

- **架构**：均为 ARM64，无明确 `exec format` 或架构错误证据。历史成功减弱“该镜像必然不能在 ARM64 启动”的假设，不排除特定版本缺陷。
- **资源**：VM 内存接近官方起始建议，其他容器同时运行；未见本容器 OOMKilled，也无故障当时完整内存、CPU 或 VM 压力记录，因此资源不足仍为待验证假设。OOMKilled=false 不排除所有内存压力或应用自行退出。
- **路径和权限**：etcd 已打开现有快照并恢复 member；所看日志未见明确 permission denied、只读文件系统、空间耗尽或路径不存在错误。无法据此证明全部文件完整或权限无误；未运行写入型磁盘基准。
- **磁盘延迟**：有单次慢操作提示，无 fsync p99、持续 IO 等待或故障时 Docker 虚拟磁盘空间数据。不能归因磁盘延迟；不要对原卷做写入型 fio。
- **端口**：当前 19530/9091 空闲；无明确绑定冲突日志，故障瞬间占用无法追溯。
- **损坏及旧版本复用**：日志中的 `initial-corrupt-check:false` 是启动配置字段，不是损坏报错，也不代表已完成完整性检查；日志未出现可确认为数据损坏的报错，不能宣称已损坏，也不能宣称已验证无损；尚不知卷中数据由哪版写入、是否迁移自 Windows 或与 MySQL 匹配。未尝试修复元数据、清空或创建 collection。真实 collection、schema、实体及跨库关联均未知。

## 4. MySQL 实际数据与资产

本轮通过宿主 `127.0.0.1:3308` 完成两个真实只读一致性快照：REPEATABLE READ、`START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY`，执行精确计数后显式 `ROLLBACK`；未读取用户字段。服务为 MySQL 8.0.45，hostname `02ccbfd0e120`，内部端口 3306，datadir `/var/lib/mysql/`。管理权限只读枚举确认该实例只有 `ai_interview`、`ai_interview_test` 及四个系统库，**没有 `ai_interview_evaluation`**。首次管理查询因客户端选项不兼容失败，修正后只读查询成功。

- `ai_interview`：13 张核心 InnoDB 表存在；`user=1`，其余 12 表各 0，包括题目、评分点、文档、chunk、面试、评价、报告与 RAG 日志。
- `ai_interview_test`：13 张核心 InnoDB 表各 0。两个库均存在可空 `interview_answer.attempt_id` 和 `knowledge_document.processing_attempt_id`（`varchar(32)`）。26 项精确计数是当前观测，不代表其他实例、Windows 原机或以后状态。
- MySQL 持久卷 `ai-interview-mac_ai-mysql-data` 创建于 10 月 24 日 18:49:01 +08，挂到 `/var/lib/mysql`；原 Compose 另以只读方式挂载 `sql/ai_interview.sql` 到初始化目录。原容器 healthy、RestartCount=0。空卷首次启动可能执行该脚本，不可把 `compose up` 当作无写入检查。
- 当前忽略的 `.env` 指向 3308 的 `ai_interview` 和 `ai_interview_test`，Milvus local collection 为 `knowledge_chunk_vectors_local` 且 `MILVUS_ENABLED=false`；当前 shell 未继承 DB 配置，`application-local.yaml` 不存在。`.env` 不是 Spring 自动加载及当前运行应用有效配置的证明。基础 YAML 未覆盖时 collection 默认是 `knowledge_chunk_vectors`；`evaluation` 默认 MySQL `localhost:3306/ai_interview_evaluation` 和 Milvus `127.0.0.1:19530/default/knowledge_chunk_vectors_evaluation`，Mac 独立实例需要显式覆盖端口。测试默认 JDBC 也回退 3306。`EnvironmentIsolationInitializer` 的 schema/collection 检查不替代对服务器身份、端口与 Milvus URI/database 的核验。
- 检查了常见宿主 MySQL 数据目录、Documents、Downloads、Desktop、Developer 和已挂载卷内常见导出线索；有其他项目 SQL、仓库 schema/夹具及演示副本，未找到**可确认**的 Windows 业务导出。搜索范围和文件名有限，不能证明备份不存在；未访问 Windows 原机、未扫描其他项目数据库。2026-10-24 迁移记录也将 Windows 私有 MySQL 逻辑备份、Milvus 元数据/向量及跨库关联列为尚待迁移。
- 仓库离线资产：`docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.json` 的 21 个题目、97 个评分点，以及 `docs/rag/source-documents/` 中 21 篇源文档；D0 的 52 chunk 是离线切片结果而非当前在线记录。它们不是 Windows 业务备份，不能恢复用户、历史或历史向量身份；H01 等内容仍需按 D0 审查。不要将历史真实向量或召回结果当作现状。

## 5. 下一阶段两条路线（均未执行）

### A｜保留原容器与卷，备份后在副本恢复

先确定来源、可用空间和备份目标，对停止的 Milvus 做包含整个 `ai-interview-mac_ai-milvus-data`、嵌入 etcd、数据文件及对应配置的完整离线备份，记录校验值并验证可恢复；MySQL 在一致性快照下单独备份，不能假定跨库快照天然原子。仅在独立副本、独立 project/端口上用原版本和匹配配置诊断，避免修改原卷。优点是有机会找回历史 collection、向量及其身份；风险是底层数据、跨版本兼容性或原配置不明，副本也可能无法恢复，误连原卷可能造成不可逆变化；需要额外磁盘、备份与恢复验证时间，运行副本时会额外占用 CPU/内存。现有 19530/9091 留给原实例，副本可另选 19531/9092，但实施前检查。

恢复后先只读 list/describe 及一致性查询，核验 schema、索引和真实实体，再与 MySQL 当前 READY/ACTIVE、版本及 model/profile 匹配的 chunk 按 vector/document/index 双向对账。当前 local 为空，不应推定它就是原卷配套的 MySQL。若找回的是旧数据，先核对 Windows 来源、版本和 ID，再单独设计迁移；不能仅因名称一致直接复制。

### B｜旧环境不动，新建隔离 evaluation

建议以此作为后续测试主路线，A 的备份和资产寻找仍应保留：旧卷不动，单独 Compose project、网络、命名卷、MySQL 实例及 Milvus 实例；候选仅回环 `13324→3306`、`19531→19530`、`9092→9091`，实施前重新确认端口、实例身份及容量。独立 MySQL 实例中的 `ai_interview_evaluation` 与专用 Milvus 的 `default/knowledge_chunk_vectors_evaluation` 显式设置 `evaluation` profile、JDBC URL 和 Milvus URI。普通测试仍为 `ai_interview_test` / test collection，local 目标维持独立。隔离能降低覆盖旧数据风险并让可重复评估更简单，但**不能恢复 Windows 用户、面试记录或原向量**，仍需资源、独立卷、初始化和外部 Embedding 成本；Docker VM 内存接近官方建议起始值，同时运行多套服务需先评估资源，不能预设足够。

仅在另行授权后，先审阅并准备只针对 evaluation 的当前完整 13 表 schema，确认服务器和目标：`sql/ai_interview.sql` 明确建立/使用 `ai_interview`，不得原样导入 evaluation；`docs/environment/ISOLATED_SCHEMAS.sql` 缺后续两个 attempt 字段且含其他目标，也不能直接视为当前完整脚本。题库从经人工审查的仓库 JSON 导入题目与评分点，核对唯一逻辑身份、状态及权重；不导入用户、凭据或面试记录。知识文档审查来源和版本后通过管理员上传及显式处理，生成 chunk 并调用 Embedding 写入专用 Milvus；需要预算外部调用费用，导入后核验 MySQL 与 Milvus 的身份和版本。若未来取回旧向量，先验证维度、模型/profile、版本、document/chunk 映射及一致性，再另行设计复制或重建，不能直接混用。

## 6. 明确下一步与未确认事项

1. **本轮停止在诊断**。优先确认 Windows 原机是否有 MySQL 逻辑导出、Milvus 配套数据/元数据、原版本配置及备份；保留旧机器与两个 Mac 项目命名卷，任何备份、启动副本、建库或导入均属于下一独立步骤。
2. 对路线 A，先完成备份、校验与恢复演练的方案审阅，再在副本收集完整选主、资源和磁盘证据。不要删除/重置 etcd 元数据、原卷或 collection。
3. 对路线 B，先审阅隔离清单、目标、DDL、资源预算和数据来源，实施前重新检查 13324/19531/9092；不得覆盖 local/test 目标，保留隔离初始化校验。之后分别核验题库和 RAG，外部模型调用另计费用。
4. 未确认：Windows 原机和其他位置是否存在业务备份；原 Milvus 卷的真实来源、版本、collection 及实体；故障当时的 VM 压力、磁盘尾延迟和精确空间；etcd 选主变化的根因；备份是否可恢复；`.env` 是否被某个独立启动器加载。不能由本轮推出数据已损坏、已恢复或真实 RAG 已通过。

本轮仅执行主机/Docker 元数据、端口、日志白名单、配置与文件查阅，以及 MySQL 只读 SQL；未执行单元、集成、Smoke 或完整回归测试，测试数量不适用。默认沙箱曾拒绝 Docker socket，随后获准进行只读核验；该拒绝不表示 Docker 停止。没有执行数据库初始化、导入、修改用户、容器/卷删除、`docker compose down -v`、系统清理、Git commit/push 或其他破坏性操作。
