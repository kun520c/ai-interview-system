# P4.2 环境恢复与 R02 独立执行准备

日期：2026-10-14，Asia/Shanghai。执行编号：`P42-R02-20261014`。本轮仅恢复可用性、真实 Java 只读预检、诊断修复与离线准备，未授权或执行收费实验。

## 真实环境事实

此前停止诊断已核实 Docker/Milvus 不可用，并用同一 Java SDK 复现 `DEADLINE_EXCEEDED`；该状态是后续诊断事实，不能补写成原执行未保存的 gRPC code。

本轮取得主机只读权限时，Docker Desktop / Engine 已运行，既有 `milvus-2620` Compose 项目的 Milvus、etcd、MinIO 刚启动并全部 healthy。Milvus v2.6.20，etcd v3.5.25；原容器、挂载和 MinIO 卷均复用。19530/9091 的 Windows 映射可达。`com.docker.service` 显示 Stopped，但 WSL Docker Engine 实际可访问，不能据此判定 Engine 故障。本轮没有重启、创建容器、修改 Compose、删除卷或修改数据。

实际 Java 21.0.11 + 项目缓存 `milvus-sdk-java:2.6.20` 以 `http://127.0.0.1:19530`、5000ms connect timeout、20000ms RPC deadline 验证：

- `default` 数据库、`knowledge_chunk_vectors_test` Collection 均存在。
- Collection ID 为 `469534467320352840`；五字段 Schema、1024 维、BOUNDED、AUTOINDEX/COSINE、非自动主键、关闭动态字段及 loaded 状态匹配原冻结合同。
- 直接运行原 `P3ReadOnlyRetrieval.NativeResources` 的构造、before、after、close，完整通过正式执行相同的资源门禁，没有调用 search。
- 固定账号 `rag_e2e_writer@127.0.0.1`，固定测试库 `ai_interview_test` 与冻结 server UUID；Connector/J readOnly、服务器 transaction_read_only=1、固定 SELECT 白名单、一致性快照、rollback-only 均通过。只使用现有密码，经私有 stdin 传递，没有账号回退或凭据落盘。
- 原 21 question + 97 scoring point + 21 document + 52 chunk 共 191 行的逐表指纹前后完全匹配。全部 52 向量及 MySQL 映射匹配，向量 SHA 为 `89c62c1f3bb4751f4ada6cd6f4ab62e8518eb660143f3892a29c67a7a6b25a9f`。

真实证据位于 `target/rag-p4-2/execution-r02-20261014/read-only-preflight-01/`；Docker 脱敏证据为同级 `docker-state.json`。只读向量 query、元数据 RPC、SQL 与收费模型/相似度 Search 分开计数。本轮模型=0、Search=0、MySQL/Milvus 写入=0。

## 新文件和责任

原 `tools/rag-p4-2/`、原准备封存、`run-once`、原锁和独立修复副本均保持原字节。新增文件全部位于 `tools/rag-p4-2-r02/`、新执行准备目录和本报告：

- `execution/P42R02ExecuteOnce.java`：保留原四阶段调用、预算和证据控制；纳入已测试诊断副本，保存异常类链、gRPC code、阶段、无异常原文的根因摘要及已保留调用计数。阶段、资源后检查和 close 失败均保留诊断。失败仍永久保锁、封存并停止。
- `execution/P42ResourcePreflight.java`：独立真实只读入口；使用相同 SDK 和原资源加载器，检查数据库/Collection/Schema/index/load/191 行/52 向量；失败退出非零，不创建或消费实验执行锁，没有模型 HTTP transport。
- `execution/analyze.py`：展示批次诊断、准确保留 NOT_STARTED；零题执行明确为 NOT_EVALUATED。质量计算沿用原判据。
- `execution/P42R02RunnerTest.java`、`P42StopDiagnosticsTest.java`、`P42R02IdentityDiagnosticsTest.java`、`test_execution.py`、`test_r02.py`：Mock 调用控制、诊断脱敏、旧授权拒绝、新身份/锁及离线质量分析回归。
- `execute.ps1`：新唯一入口，仅 Verify、Preflight、ExecuteOnce、Analyze；交互确认、私有 stdin、隔离子进程环境和零重试保持不变。
- `prepare_sources.py`、`offline.py`、`seal.py`：一次性生成独立副本、隔离编译测试、绑定原封存与新证据；有最终 seal 后禁止覆盖准备类和测试。源码增量单独保存为 `runner-delta.patch` / `analyzer-delta.patch`。
- 新 `EXECUTION_ID_RESERVED.lock`：永久占位准备身份，状态明确为未执行、未授权。真正消费的 `run-r02-20261014/EXECUTION_RESERVED.lock` 仅在新费用授权后创建，目前该运行目录不存在。
- `preservation-before.json`：绑定原执行文件、原工具/准备/诊断和原封存全部关键证据；新验证同时执行原 Java/Python 冻结门禁并复验该保留清单。
- 新 `manifest.json` / `manifest.sha256`：绑定新身份、原失败 artifacts/锁、原精确增量、原 8 题/计划、全部新文件/运行 class/缓存 Jar、真实只读和离线测试证据。

未修改生产 Java、Spring 配置、Mapper、数据库结构、语料、Embedding 维度、Rerank 策略、Semantic Gate Prompt 或质量阈值。现有其他工作区修改属于之前工作，本轮没有 stage、commit 或 push。

## 验证结果与限制

最终完整离线运行：99 tests，0 failures，0 errors，0 skipped。组成：运行器 Mock 24、原诊断副本 6、新身份/诊断 5、原 RAG 核心 Mock/历史响应回放 25、Python 入口/分析/SHA 34、新 Python 诊断/保留 5。实际测试路径记录在 `passing-offline-path.txt`；全部日志和 JSON 参与新封存。

准备期间第一次编译发现新增 Search 诊断引用了不存在的局部变量，已改为固定 MILVUS_SEARCH；另一次 javac 命令的 PowerShell 参数解析错误已纠正。首次离线运行的 24+6 项通过，但新身份测试遇到一个测试夹具错误：重复运行在已有失败预算门禁处先被拒绝，而测试仅预期文件锁异常。修正测试以核对无新增调用且锁 SHA 不变后，独立重跑时又发现测试输出父目录缺失，已补齐测试目录创建。最终 99 项完整重跑全部通过；初次失败日志和输出保留，没有删除或弱化原测试。上述问题属于新增离线测试/诊断工具准备，不是 Milvus 环境或 RAG 质量失败。

未运行 Maven 全量回归；本轮没有修改业务实现，使用已冻结缓存及聚焦离线检查。真实只读资源验证不代表真实收费 API 兼容性、延迟、费用或 RAG 语义质量通过。P4.2 的 8 题质量仍未评估。

## 唯一新执行入口和费用授权

准备已 READY，费用授权仍未取得。原授权 `AUTHORIZE P4.2 8 EACH ONCE MAX24 CNY1` 被新 Java 入口拒绝，历史 Jev/E2E 授权也无效。

在本机交互式 PowerShell 7 中，唯一新 ExecuteOnce 命令为：

```powershell
pwsh -NoProfile -File "E:\learning\IDEA_PROJECTJ\ai-interview-system\tools\rag-p4-2-r02\execute.ps1" -Mode ExecuteOnce
```

新授权短语必须完全一致：

```text
AUTHORIZE P4.2 P42-R02-20261014 8 EACH ONCE MAX24 CNY1
```

保留 H42-01..08 原题 SHA `73ca5d65649a0d2270a3f6cc209f36a0d2c775e7e43e4079f1f269a2e1ba318c`，原计划 SHA `16df13f6f7ed35968b6aa81b4f6504df36c4d07aabeee69a30b2742b7089fd47`，原 Prompt、Rerank Top5→Top2、Semantic Gate 和人工质量判据。每题最多一次 Embedding/Search/Rerank/Gate；上限 24 次收费调用、8 次 Search、重试 0、预算门禁 ¥1.00，冻结代理 ¥0.707392。预算含义及 usage 停止规则与原版本完全相同，估算不是云账单绝对上限。

模型输入不读取 coverage audit、人工标签或预期答案；题目合同严格四字段，Embedding/Rerank 用原题，Gate 只收到问题合同与候选。Mock 仅检查控制流，不对这 8 题调参或生成质量标签。

正式结果仅写入 `target/rag-p4-2/run-r02-20261014`。原运行 `target/rag-p4-2/run-once` 永久保持 STOPPED，锁 SHA `3e22a422e426171cbbdf948bafe3479215c84893bd9c9852e642d602e0b80d21`，artifacts SHA `bfa07c6a23b51c1a53e5f7e9c53084dddbada70c8c97fe93e5f48f2e9e7b2213`。

下一步仅是用户明确授予上述新费用授权后运行新的独立 ExecuteOnce；本輪没有执行该命令。
