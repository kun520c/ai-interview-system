# P2离线准备验证记录（2026-10-13，Asia/Shanghai）

`P2_RERANK_PREP=READY`。适配与离线测试已完成；真实模型、Workspace权限和语义排序质量尚未验证。没有真实请求、数据库连接或数据库/向量写入。

## 测试记录

| 运行 | Tests | Failures | Errors | Skipped | 结论 |
|---|---:|---:|---:|---:|---|
| 初次PS入口启动 | 未启动测试 | — | — | — | Get-Command返回Python真实安装与Windows别名两个路径；选择首个可执行程序后修复 |
| 第一轮Python | 51 | 2 | 32 | 0 | 两个预期问题；多数error及cleanup重复error来自子进程继承的Windows sandbox TEMP拒绝写入 |
| 第二轮Python | 51 | 1 | 0 | 0 | SHA损坏被更早的原文件校验拒绝，测试改为直接验证副本校验分支 |
| 修复后Python | 51 | 0 | 0 | 0 | 通过 |
| 最终Python（加入保存结果防篡改复算） | 53 | 0 | 0 | 0 | 通过，socket网络调用0 |
| 最终PowerShell入口 | 5 | 0 | 0 | 0 | 通过 |

首轮预期问题是测试中抄错Float32 bits，以及参考列表的首行版本片段未被低信息诊断识别；修改的是新P2测试/诊断，没有修改原始内容、标签或旧测试，也没有删除/跳过测试。临时测试目录改为workspace内`target/rag-p2/offline/fixtures`，创建路径校验在该目录内，完成后只清理各自测试夹具。

最终执行命令：

```powershell
pwsh -NoProfile -File tools\rag-p2\run.ps1 -Mode Offline
```

机器证据：`target/rag-p2/offline/test-results.json`、`powershell-tests.json`、`run.log`。第一/二轮摘要另外保留为`test-results-initial.json`、`test-results-second.json`。

Python验证真实80条本地候选与旧报告指标；提供商响应、网络状态、超时与usage均为**Mock**。不把模拟完成记录写成真实PASS。PS入口测试明确以重定向/非交互模式拒绝ExecuteOnce，拒绝发生在读取Key/授权前，无真实运行目录。

新增程序已由Python解释器执行通过；PowerShell AST两项0语法错误。生产Java/POM没有改动，因此未编译Java、未运行Maven全量、未重复P0/P1或E2E。普通MyBatis/事务测试会写数据库，本轮用户未授权MySQL写入，不能为了全量回归违反边界。HTML完成80卡片/全文/来源/危险字符转义的离线校验；未声称完成浏览器视觉验收。

## 数据核验与离线对照

原始结果SHA256：`75cf6cf1ba1606e24ae651eb3b63133e09e906a44cf1ee110630ab7ae5dc39d7`。
问题集SHA256：`4f42970bbdb3682c475056e512ff725832329d213b8b49e3a27ac45b5afbd456`。

人工JSON与两份旧报告也在测试前后逐字节SHA核对，五份原文件全部不变。10条human label/note完全保留；Q02 Rank3仍为人工原标签1。70条AI建议来源独立保留。

| 策略，COMBINED_PROVISIONAL | 保留 | 直接2 | 部分1 | 无关0 | 有用误删 | 覆盖命中 | 库外准入 |
|---|---:|---:|---:|---:|---:|---:|---:|
| A 原Top5 | 80 | 15 | 12 | 53 | 0 | 12/12 | 4/4 |
| B 原Top3 | 48 | 15 | 10 | 23 | 2 | 12/12 | 4/4 |
| C 原COSINE≥0.70 | 5 | 3 | 2 | 0 | 22 | 3/12 | 0/4 |
| D COSINE≥0.60历史对照 | 20 | 12 | 7 | 1 | 8 | 12/12 | 0/4 |

四组数值与旧报告一致。E/F/G没有真实结果，不填模拟质量值。费用代理为180489×0.50/百万=¥0.0902445；真实usage、延迟、账单仍无数据，授权预算参考¥0.50。

## 剩余事项

用户从北京控制台获取Workspace ID后，在本机交互式PowerShell执行ExecuteOnce并一次性授权。已有Key是否有模型权限须由这次被授权请求验证；若失败，记录阻断并停止，不擅自重试或换模型。成功后独立报告完整16题原Rank/Rerank对照、三种来源视图、usage、延迟和费用估算。精度提升与动态准入仍须新题和人工确认验证。

本阶段没有数据库Schema修改、生产默认接入、数据重建、Git stage/commit/push。
