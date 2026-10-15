# Baseline 复现与证据

先阅读 [AI_SYSTEM_BASELINE.md](AI_SYSTEM_BASELINE.md)。本目录是本地评测产物，当前docs/被.git/info/exclude忽略，未提交。

## 文件职责

- ENVIRONMENT_CHECK.md：真实依赖、启动错误、实际测试计数、保留数据。
- JEV_EVALUATION_REPORT.md：Jev BLOCKED、真实DeepSeek对照、逐例输入/分数/覆盖/追问与恢复边界。
- RAG_EVALUATION_REPORT.md / RAG_RETRIEVAL_TRACES.md：指标、每个query与真实Top1/3/5、FP/FN。
- E2E_EVALUATION_REPORT.md：真实HTTP面试、失败、报告与FINAL一致性、可观测性。
- data/：事先固定输入/语义标签/评测文档；evidence/：真实返回与SQL快照，全部是合成面试内容，不含key/JWT/password。
- scripts/run-baseline.ps1：本机Windows凭据到子进程、双保护opt-in与Maven日志；prepare-data.py：检索前标签；build-reports.py：从证据生成指标报告；check-jev-access.py：无凭据探测，不是推理；audit-evidence.py：映射/评分算术/凭据泄露检查与SHA-256 manifest。

## 复现（会真实调用并产生费用/测试数据）

Phase 0.5 后，真实 baseline 必须使用 evaluation profile、MySQL `ai_interview_evaluation` 和 Milvus `default/knowledge_chunk_vectors_evaluation`。普通测试使用 `ai_interview_test`；guarded real-service tests 使用 `local,test-real`，collection 为 `knowledge_chunk_vectors_test`。禁止指向正式 local 的 `ai_interview` / `knowledge_chunk_vectors_local`，启动前 guard 会拒绝冲突覆盖。

凭据只来自已有环境变量；可分别设置 `EVALUATION_DB_USERNAME/PASSWORD`、`TEST_DB_USERNAME/PASSWORD`，缺省使用现有 DB_USERNAME/PASSWORD。不要将真实凭据放入 Git。evaluation 不再依赖 local profile 的目标或密钥覆盖。脚本仍依赖本机 Java21 路径和 D:/python/python.exe。

两个隔离 schema/collection 不自动复制 local 数据。历史固定 ID 的 corpus/golden/recovery 不是新隔离环境的已准备数据；没有审核新隔离语料前不要重跑。历史 evidence 保留，不在 Phase 0.5 改写或重新生成。

只核对 runner 目标而不启动 Maven、不连接 AI 服务：

```powershell
.\docs\evaluation\scripts\run-baseline.ps1 -ValidateEnvironmentOnly
.\docs\evaluation\scripts\run-baseline.ps1 -Phase regression -ValidateEnvironmentOnly
```

```powershell
.\docs\evaluation\scripts\run-baseline.ps1 -Phase inventory
& D:\python\python.exe docs/evaluation/scripts/prepare-data.py
.\docs\evaluation\scripts\run-baseline.ps1 -Phase rag -Dataset existing
# pipeline会新增6文档，只在需要新的受控语料时执行；重复执行会新增重复资料。
.\docs\evaluation\scripts\run-baseline.ps1 -Phase pipeline
& D:\python\python.exe docs/evaluation/scripts/prepare-data.py --expanded
.\docs\evaluation\scripts\run-baseline.ps1 -Phase rag -Dataset expanded
.\docs\evaluation\scripts\run-baseline.ps1 -Phase judge
.\docs\evaluation\scripts\run-baseline.ps1 -Phase e2e
.\docs\evaluation\scripts\run-baseline.ps1 -Phase focused
.\docs\evaluation\scripts\run-baseline.ps1 -Phase regression
& D:\python\python.exe docs/evaluation/scripts/build-reports.py
```

这次“existing”特指inventory证据中初始单文档库；已经保留扩充语料后再跑existing只会换标签，不会自动恢复单文档库。脚本不删任何共享文档/Collection。因此复现实验必须固定/审核语料快照，并将新一轮证据另存，避免覆盖本轮报告。

Recovery用于本轮记录过的FAILED session/answer单次诊断，不是压力重试脚本，不改变prompt/schema：`run-baseline.ps1 -Phase recovery`。它为本轮合成账号临时签发真实JWT，经过现有DB认证；报告不保存凭据。

普通regression在unset RUN_REAL_* / MILVUS_ENABLED后运行`mvnw -B -ntp test`，guarded real tests必须跳过。focused是离线契约补充。日志保存在ignored target/evaluation/，不得将采集JUnit BUILD SUCCESS直接等同于AI验收PASS。

本轮保留文档542–547、chunk484–490、session2463–2468。过滤实验只修改新增文档542并finally恢复；恢复后原文档485仍READY。没有drop、recreate或clear Collection。删除证据数据前应先评估MySQL外键与报告复核影响，不要直接清库。
