# D2 随机组卷真实 MySQL 验收工具

目标固定为 `127.0.0.1:3308/ai_interview_evaluation`，MySQL 8.0.45，服务器 UUID `85dd1e00-cf98-11f1-8677-9eb9380f25e7`。Spring 只启用 `evaluation`，由项目的 `EnvironmentIsolationInitializer` 校验。工具禁用 SQL 初始化、HTTP 监听、Milvus 和 DeepSeek，Embedding 配置仅使用不可用的本机占位端点。数据库凭据由启动器从现有 MySQL 容器取得并只传给子进程。

在仓库根目录准备 Java 21、已编译的 `target/classes` 以及项目 Maven 依赖 classpath。执行目录 `backups/D2_20261114` 应为 0700，包含 `classpath.txt`，内容为 `target/classes` 加依赖路径（以 `:` 分隔）。使用 `umask 077` 编译到该目录。保留历史证据，阶段产物使用独占新建。

```zsh
umask 077
mkdir -m 700 -p backups/D2_20261114
python3 tools/question-plan-d2/run_acceptance.py backup
javac -cp "$(cat backups/D2_20261114/classpath.txt)" \
  -d backups/D2_20261114 tools/question-plan-d2/D2QuestionPlanAcceptance.java
python3 tools/question-plan-d2/run_acceptance.py baseline
python3 tools/question-plan-d2/run_acceptance.py exercise
python3 tools/question-plan-d2/run_acceptance.py verify
```

每一步确认成功后再执行下一步。`backup` 核验容器、端口和服务器身份，导出三库逻辑备份及数据摘要。`baseline` 核验 D1 两个源文件的 SHA-256、所有启用题与有效评分点的实际 ID 及字段指纹、15/21/27 难度分布、21 个计划分类各 3 候选，以及 evaluation 13 张 InnoDB 表的行数和摘要。`exercise` 在任何写入前校验同目录备份文件与 `backup-receipt.json`，重新匹配真实基线并写 `write-plan.json`。任何目标、备份或基线不符均中止。`verify` 使用新进程只读对账三库数据与另外两库的完整逻辑导出。

`write-plan.json` 记录全部专属 `d2_…` 账户、计划写入及数量上限。29 个回滚事务最多创建 29 个禁用测试账户、28 场 Session 和 194 行 MAIN 快照。24 场为每难度各 8 次，另 3 场在不同线程中同时开始创建。每场还对同一用户请求另一难度，检查返回原 Session 且快照不变。每场从 MySQL 读回 MAIN，逐行核对所属 Session、难度、分类、题号、题干、答案、评分点 JSON、题序和状态。

两次专用事务暂时禁用计划中第一个 EASY 分类的 3 道指定题，检查真实 SQL 排除禁用候选。第一次请求应报缺失分类，且不写 Session。第二次通过正式 `QuestionAdminService` 创建一题一点评分点填回该分类，创建并读回 Session 后用正式更新入口修改该临时题并重建临时评分点，再次读回历史快照逐字段比对。正式 63 道题保留原行，正式 298 个评分点不参与更新或删除。

所有写入都加入外层 `REPEATABLE_READ` 事务，并在 `finally` 标记回滚。每次回滚后用 `DriverManager` 创建独立只读连接核对全部 13 表行摘要，包括失败路径。任何异常后均以最后一次独立对账和数据库导出确认现场。`writes.jsonl` 记录事务内生成的标识，`result.json` 仅在最终对账后生成，包含组合、分类频次和快照标识。频次是小样本诊断，工具不对随机频次设置通过阈值。

外层事务使正常流程中的独立只读快照事务和短写事务加入同一验收事务。该探针验证真实 SQL、应用服务、并发隔离及回滚内读回，不证明提交后可见性或正常流程的独立事务传播。本批未在受保护测试库重跑既有事务集成测试。回滚可能消耗自增 ID，业务行摘要不包含自增元数据。备份与日志存放在 Git 忽略的受限目录，禁止将含业务行的备份提交或外传。
