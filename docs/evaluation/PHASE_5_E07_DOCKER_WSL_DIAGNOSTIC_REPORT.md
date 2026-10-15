# E07 Docker / WSL 诊断报告

**Gate A BLOCKED_PRIOR_EVIDENCE_INCOMPLETE。按Commander的明确停止条件，未进入Gate B原生诊断。Docker/WSL故障根因UNKNOWN；不能从旧named pipe失败推断WSL损坏或虚拟化关闭。**

## 门禁审查

实际读取Bootstrap主报告、Docker审计、once-lock、manifest、entry、poll JSON及记录脚本，复核历史交付哈希。既有资料相互一致地记录一次Desktop启动、84次exit=1、180秒设定和181.848秒loop总耗时；未发现记录表示第二次启动或数据/模型动作，但仅汇总不能独立排除未记录操作。

附件Gate A要求核对原始错误、首次失败、轮询结束、清理结束及隐藏后续动作，且规定：**“If prior evidence is missing or inconsistent, STOP and report the discrepancy before invoking any new diagnostic commands.”** 本次发现如下持久记录缺口，停止执行B：

1. 初始docker ps与84次poll均没有原始stderr持久副本。旧脚本捕获stderr但只保存at/exitCode。安全摘录“初始docker ps --all因Linux engine named pipe缺失失败。”来自旧报告，是概述，**不是原始stderr**。
2. 首个已记录poll完成UTC=2026-10-09T04:55:06.161770+00:00；末个=2026-10-09T04:58:05.822360+00:00。初始ps失败时间、各调用开始/耗时、独立截止点、loop结束与cleanup结束均UNKNOWN。
3. 181.848秒是代码中的整个loop计时，包含尾部sleep；180秒是配置阈值。差值1.848秒是算术差，**不是独立测得cleanupDuration**。因此无法从现存文件完整证实“active 180 + cleanup 1.848”。保留原报告，未回填或重写。
4. 启动路径/Hidden/count保留在汇总，但完整原始启动调用及exit/timestamp串没有封存。零写入/零模型是旧声明，记录审查未发现反例；不可把它升级为对所有外部动作的独立证明。

这些是证据粒度不足，未发现哈希篡改证据。状态不等于当前Docker仍不可用：当前应用、engine、WSL、虚拟化、容器、Milvus endpoint全UNKNOWN。受影响的已证实层是**历史审计记录完整性**，置信度HIGH；所有系统根因分类未评估。

本轮未运行wsl --status/version/-l -v、服务/进程/CIM/Windows Features检查、docker context/version/info/ps/volume、端口探测或Docker/WSL日志扫描。NOT_RUN不是NOT_AVAILABLE、ACCESS_DENIED或失败；没有伪造这些结论。

## 调用台账与计时

以下按提交顺序列出；前置读取分组并行，完成顺序和UTC未独立捕获，不补造。PowerShell工具合并输出没有独立stderr，标记NOT_SEPARATELY_CAPTURED；本轮没有系统诊断失败原文可提供。最后三次Git子进程设置独立10秒超时，只作用于本工具的Git子进程，无Docker/WSL终止。

| 序号 | 实际只读命令 | exit | 工具/子进程秒 |
| --- | --- | ---: | ---: |
| 1 | `Get-Content -Raw 'C:\Users\USER\Downloads\PHASE_5_E07_DOCKER_WSL_ENGINE_DIAGNOSTIC_CODEX.md'` | 0 | 0.5 |
| 2 | `Get-Content -Raw target\phase5-e07-eval-bootstrap01\docker-inventory.json` | 0 | 0.152 |
| 3 | `Get-Content -Raw target\phase5-e07-eval-bootstrap01\bootstrap-once.lock` | 0 | 0.183 |
| 4 | `Get-Content -Raw target\phase5-e07-eval-bootstrap01\docker-installation-observation.json` | 0 | 0.249 |
| 5 | `Get-Content -Raw target\phase5-e07-eval-bootstrap01\entry.json` | 0 | 0.261 |
| 6 | `rg --files -g AGENTS.md -g '*docker*wsl*' -g '!node_modules' -g '!.git' -g '!target/**'` | 0 | 0.276 |
| 7 | `Get-Content -Raw target\phase5-e07-eval-bootstrap01\docker_inventory.py` | 0 | 0.239 |
| 8 | `Get-Content -Raw docs\evaluation\PHASE_5_E07_EVALUATION_BOOTSTRAP_REPORT.md` | 0 | 0.147 |
| 9 | `Get-Content -Raw docs\evaluation\judge\PHASE_5_E07_DOCKER_MILVUS_RECOVERY_AUDIT.md` | 0 | 0.169 |
| 10 | `Get-ChildItem -LiteralPath target\phase5-e07-eval-bootstrap01 -File \| Select-Object Name,Length \| ConvertTo-Json` | 0 | 0.268 |
| 11 | `rg -n -i 'stderr\|error\|pipe\|cleanup\|polling\|launch\|Start-Process\|19530\|WSL\|Docker' target\phase5-e07-eval-bootstrap01\finalize_stopped.py` | 0 | 0.248 |
| 12 | `Get-Content -Raw AGENTS.md` | 0 | 0.246 |
| 13 | `git status --porcelain=v1` | 0 | 0.275 |
| 14 | `git rev-parse HEAD` | 0 | 0.284 |
| 15 | `Get-Content -Raw target\phase5-e07-eval-bootstrap01\prepare.py` | 0 | 0.152 |
| 16 | `git rev-parse HEAD` | 0 | 0.035721 |
| 17 | `git status --porcelain=v1` | 0 | 0.040692 |
| 18 | `git status --porcelain=v1 --untracked-files=all` | 0 | 0.045804 |

本轮Docker/WSL主动诊断耗时0、诊断清理耗时0；没有轮询。审计helper在组装证据时耗时0.647714秒，外层完整turn时间NOT_MEASURED。该数值不是系统恢复等待时间。新文件写入/最终封存不计作原生诊断子进程。

## 不变性及范围

受保护历史文件4612项，SHA复核PASS；原Bootstrap交付19项及其清单纳入保护，原4592项不变。Git HEAD=6076a70f18f3062b8faff4fc179cf21f00a95036，与原入口一致=True；完整untracked展开status与原入口相同=True。已有dirty文件保留，本轮未add/commit/push/reset。新版交付仅两个报告及新诊断目录，未修改旧报告、locks、源码、Prompt、Golden或配置。13表空库只是早期历史结果；当前evaluation数据就绪UNKNOWN。

本轮Docker/WSL原生命令、系统/端口/日志检查、启动/重启/终止/更新/reset、容器/volume/image/network修改均0；数据库连接/DDL/DML、向量读写、Jev/DeepSeek/Embedding/模型重试、配置更改、Bootstrap续跑、Canary均0。编译/JUnit未运行，不引用旧测试作为新验证。未读私有YAML或环境/凭证内容，未上传诊断。

## 下一次授权边界和停止状态

Commander必须先审核Gate A证据缺口：可授权只读提取上一轮原始工具记录以补齐stderr/调用时间线，或书面裁定在保留该缺口的条件下进入有界只读Gate B。补充证据须写新文件，旧工件不得覆盖。本轮未申请也未执行重启、shutdown、terminate、更新、reset、容器启动或任何数据准备。

```text
DOCKER_WSL_DIAGNOSTIC_ONLY=BLOCKED
EVALUATION_DATA_READINESS=UNKNOWN
CANARY_02=NOT_AUTHORIZED
PHASE5C=NOT_RUN
PHASE6=BLOCKED
```


## 工件生成与封存台账

上述只读命令之后，apply_patch创建新诊断脚本（完成，无shell exit码）；运行 D:/python/python.exe target/phase5-e07-docker-wsl-diagnostic01/audit_gate_a.py，exec exit=0、wall=1.2254089秒。该helper只校验文件/Git并生成本轮报告，没有原生Docker/WSL诊断。随后离线封存追加本台账与哈希QA，仅改写本轮新工件。详细artifactGenerationLedger见observations.json；没有取得新的系统错误stderr。


> 公开副本仅隐去本机用户目录。原始报告保留本地；历史实验结论、执行锁和冻结规则未修改。
