# E1｜Evaluation 环境初始化验收（2026-11-13）

**结论：BLOCKED。** 两个目标现场均不存在，尚未创建 Schema、表或 Collection。旧 Milvus 卷备份可读且摘要吻合；现有 MySQL 两库已做本地逻辑备份。但 E0-R2 新 Milvus 实例的一致性离线卷备份尚未取得：停止该实例的请求被自动审批拒绝，理由是停机会中断已有 Collection 访问，且该实例此前停机曾 panic。依据本任务“保护条件无法确认则停止写入”，本轮未继续初始化。没有绕过该拒绝。

## 范围与已读资料

阅读项目 AGENTS.md、E0-R2/R3、`docs/environment/README.md`、`ISOLATED_SCHEMAS.sql`、`sql/ai_interview.sql`、实际 evaluation YAML 和两份工程记录；源码检查了环境隔离初始化器及 Milvus Collection 初始化器。当前工作区已有未提交修改，予以保留。本轮没有导入题库、账号、答案、报告或知识文档，没有调用 Embedding / DeepSeek，也没有执行 Git add、commit 或 push。

## 现场只读核验

- MySQL 8.0.45：`127.0.0.1:3308`，容器 `ai-interview-mac-mysql-1`，使用 `ai-interview-mac_ai-mysql-data`；`ai_interview` 和 `ai_interview_test` 各 13 表；`ai_interview_evaluation` 不在 schema 清单中。只读盘点和备份后以同样选项再导出并计算摘要，两个库的完整逻辑导出摘要一致：`3048c1b5926ecac9ad5aeb9906d23366a4bba556e5acd123c071fe32c76165e8`。此比较覆盖导出的表结构和行等内容，是本次两次快照相等的证据，并非持续监控。
- Milvus：真实 Java SDK 2.6.20 连接 `127.0.0.1:19532/default`，服务 2.6.26、health=true；三个现有集合 `e0r2_synthetic_bcfb4ee2f2d34c1091856c2205d2a161`、`e0r3_synthetic_6a1183deeacca38effe5a1d3b70a51f2`、`knowledge_chunk_vectors_test` 均 loaded，Strong `count(*)=0`；目标 `knowledge_chunk_vectors_evaluation` 不存在。仅盘点与强一致计数，未更改旧 Collection。未执行创建后 schema/index 检查。
- 新实例 `ai-interview-e0r2-milvus` 使用 `ai-interview-e0r2-milvus-data`；旧 Milvus 容器仍停止、使用独立的 `ai-interview-mac_ai-milvus-data`。结束时新实例 running、RestartCount=0；旧容器 exited、RestartCount=9。MySQL 和两个 Milvus 数据卷未删除或重建。新增对象将改变所用共享实例的物理数据卷，不能把新增后的卷字节不变作为验收条件；应验证旧库和旧集合逻辑内容不变。
- 旧卷归档 `backups/E0R2_MILVUS_20261113/old-milvus-volume.tar`：150,538,240 字节，SHA-256 `1112ab06387ad2c5c6c3e5dc409f578c63678d729abb601b52a22fdddb98f533`，本轮 `tar -tf` 成功；E0-R2 的原卷逐项比对和解包文件级核验有记录。两份配套配置哈希也与 E0-R2 一致。未做旧服务级恢复或跨库对账，不能宣称旧业务已恢复。

## DDL 审查候选

[专用候选 DDL](../environment/E1_EVALUATION_SCHEMA_20261113.sql) 从当前 `sql/ai_interview.sql` 原样保留 13 个表定义，仅将原 `CREATE DATABASE IF NOT EXISTS ai_interview` 和 `USE ai_interview` 前缀替换为不带 IF NOT EXISTS 的 `CREATE DATABASE ai_interview_evaluation` 和 `USE ai_interview_evaluation`；重跑在目标已存在时失败。静态检查表定义数为 13，未发现 DROP/TRUNCATE/INSERT/UPDATE/DELETE/ALTER 或指向旧库的跨库引用。候选 SHA-256：`1288682adc24b1abe40add640a5638d31a2b40b4c542d6eba41125fec33ea431`；来源 SHA-256：`47bf6a46b7308111911a079af16da0e0835e662886beb6d15a4673ff54af1a51`。

对两个旧库各 13 表的只读 `SHOW CREATE TABLE` 与当前 SQL 归一化核对：每库 163 列的类型、长度、可空与默认值，以及 13 个主键、12 个唯一键、15 个外键及引用规则、42 个 CHECK、16 个源码显式普通索引一致；MySQL 另为外键补充一个普通索引，每库实际共 42 个索引。各表均为 InnoDB、utf8mb4_unicode_ci。该检查不读取业务正文。只读 SHOW CREATE / 计数快照及比较脚本保存在 `backups/E1_EVALUATION_20261113/`（Git 忽略，文件 0600）；脚本退出 0。比较按名称和归一化定义进行，CHECK 的 token 归一化不是形式化语义证明，列顺序未纳入脚本比较；两个 attempt 字段顺序另按迁移的 AFTER 与实际定义核对。当前正式 SQL 已包含 `interview_answer.attempt_id varchar(32)` 和 `knowledge_document.processing_attempt_id varchar(32)`；历史 `ISOLATED_SCHEMAS.sql` 缺少两者，不应执行。本轮没有执行候选 DDL，因而没有对新库逐列、索引、唯一键、CHECK 和外键进行实际比对或验证其空数据。静态检查不是 MySQL 执行验证。后续获准并满足全部保护后必须重新确认两目标均不存在，并核验部署时同一源文件摘要。

## 配置核对

`application-evaluation.yaml` 选择 `ai_interview_evaluation` 和 `knowledge_chunk_vectors_evaluation`，凭据从环境读取；默认端口是 MySQL 3306、Milvus 19530，不是本机目标。后续运行须仅启用 `evaluation` profile，并在当前进程显式指定 `EVALUATION_DB_URL` 指向 `127.0.0.1:3308/ai_interview_evaluation`、`EVALUATION_MILVUS_URI` 指向 `http://127.0.0.1:19532`，Milvus database 使用 `default`。不得将密钥写入 Git。

`EnvironmentIsolationInitializer` 仍检查解析后的 MySQL schema 和 Milvus collection、拒绝 evaluation 与 local/test 混用及非空 connection-init SQL，但不保证主机端口或 Milvus database 的身份；须另行检查。DeepSeek 默认关闭；Embedding 使用占位参数且禁止触发调用。由于目标未创建，未运行完整 evaluation 应用上下文、数据库账户授权或启动检查；不能声称连接配置已实测通过。

## 备份、恢复和中止方案

- 受限本地备份 `backups/E1_EVALUATION_20261113/mysql-before.sql` 由 `mysqldump --single-transaction --order-by-primary --skip-dump-date --routines --events --triggers --databases ai_interview ai_interview_test` 生成，55,587 字节、0600，目录 0700、被 Git 忽略；摘要见上。它可能含敏感业务数据，不得提交、打印或外传。归档可读取且重复快照摘要一致；**未做恢复演练**。其中包含原库名，禁止直接导入当前共享实例；恢复前须经授权，使用新建隔离 MySQL 实例、独立空卷和端口，核验目标及版本、导入后只读核对结构、行数和摘要，原备份只读保留。
- 新 Milvus 实例的全卷一致性备份仍缺失。停服被拒绝，不能将运行中直接打包卷称为一致备份。若后续获得明确停机批准及自动审批通过：先再次记录容器、端口、卷、Collection 和 Strong count；停用新实例后将其卷只读挂载到无网络临时容器，归档到受限、Git 忽略的独立目录；验证归档可读取、摘要和卷逐项比较，再启动原实例并只读复核。任何备份或启动失败立即中止，保留原卷和日志。只在新空卷、独立端口演练恢复，绝不覆盖当前或旧卷；核对属主、版本、Schema、索引和内容。本次没有完成此备份或恢复演练。
- 真正创建 evaluation 后，未来导入前应另行形成该库逻辑备份与 Milvus 一致性快照、恢复演练和跨库对账；本轮空环境未创建，不存在可备份的 evaluation 对象。遇到目标已存在、Schema 不符、持续启动失败、计数不一致、未知部分成功或数据损坏时停止，保留现场；不得自动 DROP、TRUNCATE、重建或删除已有对象。

## 验收状态及余项

| 验收项 | 结果 |
|---|---|
| 目标名称及创建前不存在 | 只读核验通过；下次操作前须重新确认 |
| 13 表、列、索引、约束、空数据 | DDL 已准备；目标未创建、实际验证未执行 |
| Evaluation Collection、Schema、索引、空数据 | 未创建、未验证 |
| Java SDK 连接 | 对现有新实例只读连接和盘点通过；目标 Collection 未验证 |
| 原两 MySQL 库、旧 Collection 和旧卷 | 两次逻辑导出摘要一致；Collection 只读盘点；旧卷保留，未做完整物理逐字节前后比较 |
| 重启后连接 | 本轮未重启；E0-R3 的历史结果不能替代本轮验收 |
| 模型调用和敏感信息 | 未调用；本报告不含口令、密钥或业务内容。MySQL 备份仍属敏感文件 |

未运行 JUnit / Maven 全量回归；本轮没有代码修改。未实测新环境重启、恢复、应用连接或真实模型。E0-R3 已记录 Milvus 停机 panic 与启动竞态，且未证明未刷盘数据安全或长期稳定。下一步先取得对新 Milvus 短暂停机备份的明确批准并通过自动审批；确认保护措施和目标仍不存在后，才可在本次用户授权范围内继续空环境初始化，不自动开始 E2。
