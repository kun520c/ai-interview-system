# D1 题库版本与受控导入工具

本目录用于 2026-11-14 的 D1 内容扩容，不是应用启动任务，也不改变生产随机组卷、RAG 或评分逻辑。目标固定为 `127.0.0.1:3308/ai_interview_evaluation` 与已验证 MySQL UUID；不得替换成正式库或测试库。正式报告在 `docs/verification/D1_QUESTION_BANK_EXPANSION_20261114.md`。

- `revise_h01.py`：只生成 H01 新版题稿，冻结 V1 文件保留。
- `build_bank.py`：合并已审稿、验证 DTO 文本边界/枚举/身份/21 桶，生成 v2 JSON、Markdown 与指纹清单。拒绝 Python `-O`。它只证明结构与对应关系，不代替内容审查。
- `D1QuestionBankImport.java`：仅 evaluation 的一次性执行器，`baseline` 保存只读基线，`validate` 离线验证，`rollback` 在真实 Service 写入后触发整批回滚，`import` 原子更新 H01 并新增42题，`verify` 在新进程只读核验。保持 `EnvironmentIsolationInitializer`；禁用模型及 Milvus 写入能力。
- `D1ImportOfflineTest.java`：1 个有效输入和 13 个不合格输入的离线边界测试，不连接数据库。
- `D1MilvusInventory.java`：仅访问固定 `19532/default` 的既有四个集合，Strong 读取元数据和完整向量行；不调用初始化器、Embedding 或相似检索。
- `run_import.py`：受保护的一次性本机执行入口，从既有 Docker 容器元数据向子进程传递数据库凭据，不输出或保存凭据。原始行快照、日志和回执放在 Git 忽略、权限受限的 `backups/D1_20261114/`。

题库版本：原始 `PHASE_1_QUESTION_BANK_FINAL.json` SHA-256 固定为 `ce6fff9bd42b064009b01c0300e2da5da16e73d04c363e3cf7f2b351cc1b6fd3`。v2 的 `bankId` 是永久逻辑身份，数据库自增 ID 另由验收映射记录；不要按历史 ID 创建新题。各题指纹以 UTF-8、排序键、紧凑 JSON 的六个 CreateQuestionRequest 字段计算。原有20题字段不变；H01 只变答案和 SP4，修订号2；42新题修订号1。

执行顺序必须为内容独立审查与快照 → `baseline` → `validate`/离线负例 → `rollback` → 独立确认回滚 → `import` → 新进程 `verify` → 原两库、保护表和 Milvus 前后比对。`import` 要求最终题库字节的43项审核PASS、无未解决P0/P1、匹配题库/基线摘要的回滚回执、当前行数据仍与基线完全一致。

本轮编译使用 Java 21、当前项目 Maven classpath，输出仅在忽略的执行目录。`run_import.py` 要求该目录已有 `classpath.txt` 和已编译 class；需要复现时先按当前项目依赖重建 classpath，重新检查目标、审核版本和独立快照。它不是可在其他机器无检查重跑的通用迁移脚本。

失败恢复：

1. 事务未提交时任一步失败，H01 更新和所有新增题均回滚；重新读取全部13表确认。自增号段可能被消耗，不能重置序列或据 ID 不连续断言数据缺失。
2. COMMIT 已发生但响应或回执写入不确定时，停止重试，先执行 `verify` 和独立只读核验。若已为完整63题，补记录成功事实；如果仍是完整旧基线，再审查失败原因。任何部分或未知状态都保持现场。
3. 已成功导入再次执行 `import` 会因提交回执或基线不一致而拒绝。不提供自动删除、清库或覆盖回退。逻辑备份保留供明确授权的后续恢复分析；本轮未实施恢复覆盖。

H01 通过正式整题替换路径会重建5个评分点主键，题目主键保持。历史面试使用固化的评分点 JSON；本轮不持久化、改写任何面试快照。入场历史会话与面试题表为空，不能据空样本宣称验证了历史非空数据迁移。
