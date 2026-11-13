# D3 语料、入库及评测工具

从仓库根目录运行。Java 21，Python 3.9 或更新版本。工作簿只读导出用 `openpyxl`。`backups/D3_BCD_20261114` 是 Git 忽略的 0700 工作目录，受保护文件按 0600 保存。

```bash
umask 077
mkdir -p backups/D3_BCD_20261114
./mvnw -o -B -ntp -DskipTests compile dependency:build-classpath \
  -Dmdep.outputFile="$PWD/backups/D3_BCD_20261114/classpath.txt"
python3 -B tools/rag-d3/run.py preflight read-only-review.json
```

预检校验现有 evaluation Collection 后启动 Spring，保留 `EnvironmentIsolationInitializer`。它核对库和集合身份、生产 Bean、Spring 事务代理、MySQL 当前内容与 Milvus 强一致查询。运行期从现有 owner-only 配置读取凭据，秘密不进入参数。首次无付费预检使用占位 Embedding 配置并保持 DeepSeek 关闭。

`D3Corpus`、`D3Queries` 是离线入口，使用生产 Chunker 和 QueryBuilder。`run.py ingest` 和 `run.py retrieve` 是本次真实执行的有状态入口，需要当地盘点和冻结审查回执。执行器检查相同 READY 身份再复用，遇到 FAILED、PROCESSING、未知向量或未决 HTTP 记录停止。需要处置时先读库、记录 attempt 和唯一向量 ID，再选择既有受控恢复。入库所有成功行保留在 evaluation。测试用户和独立集都只存在于受保护位置或本机 evaluation。

`production_metrics.py`、`public_labels.py score`、`independent_metrics.py` 可用既有预测离线重算。生产和公开查询的冻结文件可审计。独立题目与预测逐题原文保留在本机。提交的独立摘要保留用量、指标和缺口主题。`start_evaluation.py` 的正常入口及人工操作见 [D4 指南](../../docs/verification/D4_MANUAL_AI_TEST_START_HERE.md)。两个 `--smoke` 模式是有持久记账、次数及一次执行门禁的验证入口。结束的会话不会再次创建。
