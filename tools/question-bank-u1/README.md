# U1 evaluation 两题执行入口

从项目根目录运行。真实入口需先核对当前空闲窗口、授权的 evaluation 数据库凭据来源，以及原反馈会话的所有者和 ID。`u1_questions.py` 只从 `~/.config/ai-interview/evaluation-db.env` 加载数据库字段并注入子进程，不执行配置文件，也不回退项目 `.env`。目录须为 0700、文件须为 0600；字段和固定目标见 [D3 说明](../rag-d3/README.md)。生产代码的读取和连接隔离仍由 `EnvironmentIsolationInitializer` 校验，执行器再检查 evaluation 的实际数据库 UUID。

历史 D1/D2 复验脚本保留了原批次使用的高权限入口，D2 还会读取其他 Schema；它们不属于当前受限的 U1-R1 数据库入口，不得用于绕过本批账户和数据库边界。

已有 Java 21 编译产物和 D3 classpath 时，可以先验证候选 JSON。以下脚本只把编译产物写进临时目录。不得用过期的 `target/classes` 作为新服务正在运行的证据。

```sh
python3 -B tools/question-bank-u1/u1_questions.py validate
```

`inspect` 会在所有者核验通过后提取两道 MAIN 与关联 FOLLOW_UP 的私有资料。下例使用占位环境变量；每次必须使用经用户确认的会话与账号。请先审原答、快照、评分和批次，核对同一份在用旧题库再应用候选。

采样中的 `current_chunk_content` 是按历史命中 ID 关联到的当前 Chunk 正文，并带版本与时间；`rag_hit_log` 本身不保存正文快照。如果正文后来发生变化，不能仅凭当前内容断言它与当时发送给模型的文本完全一致。

```sh
python3 -B tools/question-bank-u1/u1_questions.py inspect --session "$SESSION_ID" --account "$CONFIRMED_ACCOUNT"
```

取得空闲窗口并完成上述证据核对后，以下入口使用同一事务中的两次正式 `QuestionAdminService.updateQuestion`，然后独立读回。私有 `question-apply-intent.json` 是提交前保存的旧记录、审批候选和保护范围摘要。若执行中断，先看数据库状态与 intent 再决定下一步。已有 intent 时工具拒绝重复应用。对外保留的 `candidateRevision` 只是文档版本，当前评分点 ID 依正式 Service 分配，旧会话依历史快照读取。

```sh
python3 -B tools/question-bank-u1/u1_questions.py apply --session "$SESSION_ID" --account "$CONFIRMED_ACCOUNT" --window-confirmed
```

`python3 -B tools/question-bank-u1/u1_timing.py verify` 仅做编译和浏览器脚本语法检查。真实合成浏览器运行共享 U1 费用锁，启动本次专用回环进程，需已完成评分对照、授权凭据、空闲窗口、重新核对的剩余费用预留和指定的空闲端口。现场无法满足门禁时保存离线结果。真实计时结果应能用 `X-Interview-Timing-Id` 把浏览器和 `interview.timing` 匹配起来。详细边界与当前状态见 [U1 报告](../../docs/verification/U1_MANUAL_FEEDBACK_REVIEW_20261114.md)。

组卷存在随机性；浏览器验收必须另外确认新的会话确实包含两道目标题。现有计时脚本本身不检查目标题全部出现，也没有覆盖快速重复点击或失败恢复，不能单独作为这些验收项的通过证据。
