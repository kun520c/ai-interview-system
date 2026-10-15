# E07-ENGINE-DIAG-02 Gate B 只读诊断报告

**本轮完成有界只读采集。当前确证的是诊断进程的访问被拒绝；不能据此认定WSL损坏、虚拟化关闭或Linux daemon停止。Desktop/backend和WSL相关进程可见，127.0.0.1:19530一次连接成功；daemon功能、容器身份及evaluation数据就绪仍UNKNOWN。未执行任何恢复。**

## 当前故障分层

| 层 | 本轮结论 | 可复核证据及边界 |
| --- | --- | --- |
| WSL安装信息 | version读取成功 | 2.7.10.0；内核包6.18.33.2-2。安装版本不证明发行版健康 |
| WSL状态与发行版 | UNKNOWN / ACCESS_DENIED | --status、--list --verbose均exit4294967295；stdout为Wsl/EnumerateDistros/Service/E_ACCESSDENIED。没有发行版名单/运行状态/版本，未启动发行版 |
| Windows虚拟化前置条件 | UNKNOWN | 两项CIM拒绝访问；WSL/VirtualMachinePlatform Feature读取提示“请求的操作需要提升”，没有提升或UAC |
| Docker Desktop应用 | 进程存在，功能健康UNKNOWN | Desktop六进程、backend两进程；不能代替Server成功响应 |
| Windows服务 | 返回项的部分枚举有效 | com.docker.service=Stopped/Manual；WslService=Running/Automatic；vmcompute=Running/Manual。完整命令exit1、stderr实际为空；不以helper服务Stopped推断Linux daemon停机 |
| Docker CLI/context | 可用 | CLI29.6.1；desktop-linux*；npipe:////./pipe/dockerDesktopLinuxEngine。未切换context/backend |
| Linux daemon API | 当前访问失败，daemon健康UNKNOWN | docker version/info/ps各exit1；独立stderr均为permission denied while trying to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine |
| Milvus/minio/etcd既有容器 | UNKNOWN | ps权限拒绝，未枚举。不能声称容器数量0、缺失、停止或名字已知；inspect 0 |
| 127.0.0.1:19530 | LISTENING（TCP连接层） | 单次connected=true；没有验证服务身份、Milvus API、schema/index/count或Collection |

服务状态枚举按.NET名称展示，原始数值和输出保留。进程StartTime为null的项不据此判断不存在；WSL相关服务/进程存在，也不证明每个发行版健康。

## 新错误与日志解释

新采集WSL错误位于stdout：`Wsl/EnumerateDistros/Service/E_ACCESSDENIED`。新Docker独立stderr是`permission denied`，本轮不是用旧named pipe缺失叙述作证。CIM stderr=`Get-CimInstance: 拒绝访问`；Feature stderr=`Get-WindowsOptionalFeature: 请求的操作需要提升`。多种管理接口同时拒绝访问，支持“当前进程token/sandbox/ACL受限”候选，但没有身份/ACL对照，具体机制未确证。

仅读取Docker/log根、host、vm三个明确目录的文件元数据，读取3个明确日志的有界尾段（每文件最多512KiB，最近4小时01:25:25–05:25:25 UTC，最多15条错误/文件），没有递归全盘扫描。backend片段有allowed-orgs读取Windows registry key/registry.json不存在（05:07:02及05:20:03 UTC）；vm/init有dxg ioctl -22/-2、level=info（05:25:01 UTC）。这些片段没有证明engine致命退出，也不足以把allowed-orgs或GPU信息归因到当前API拒绝访问。

WSL Operational指定日志名称未匹配；Hyper-V-Compute provider未写入请求的System日志。二者标NOT_AVAILABLE，不推断“系统没有错误”。生命周期日志追加查询被总预算门禁跳过，不能宣称已查到engine READY。日志原文只保存最小必要脱敏片段，不上传原始文件。

## 根因裁定

CONFIRMED：当前调用上下文的WSL服务枚举、Docker API及CIM读取遭拒绝。UNKNOWN：权限限制究竟来自诊断sandbox、Windows用户token/ACL还是其他策略；底层daemon和历史启动故障原因。当前TCP已连通和进程存在降低“所有相关服务完全停机”的支持度，但不足以标CURRENTLY_HEALTHY或WSL_WORKS_DOCKER_ENGINE_DOWN。

最小建议先做普通用户会话的只读权限对照，再决定是否需要任何恢复。当前不具备重启、Feature启用、WSL修复或容器启动的证据条件。evaluation readiness始终UNKNOWN，本次没有初始化、runtime enabled验证或Canary。

## 调用、计时与证据完整性

20次实际命令（2次Git入口读取、18次Gate B系统或日志检查），exit=0共7次、非0共13次，超时0；非0包括2次输出有效JSON但有未呈现消息的部分枚举。command-ledger.json逐项保存真实argv、UTC开始/结束、独立stdout/stderr路径、exit、elapsed_ms、activeProbeDuration、cleanupDuration与TIMEOUT。WSL错误实际位于stdout，stderr空文件表示本次捕获为空，不是未采集。

| 顺序 | 检查 | exit | 实际毫秒 | TIMEOUT |
| --- | --- | ---: | ---: | --- |
| 1 | git_head | 0 | 43.332 | False |
| 2 | git_status | 0 | 51.846 | False |
| 3 | wsl_status | 4294967295 | 57.841 | False |
| 4 | wsl_version | 0 | 44.839 | False |
| 5 | wsl_distributions | 4294967295 | 55.283 | False |
| 6 | docker_wsl_services | 1 | 336.181 | False |
| 7 | docker_wsl_processes | 1 | 314.68 | False |
| 8 | hypervisor | 1 | 452.951 | False |
| 9 | cpu_virtualization | 1 | 382.112 | False |
| 10 | wsl_windows_feature | 1 | 527.123 | False |
| 11 | vmp_windows_feature | 1 | 459.65 | False |
| 12 | docker_cli_version | 0 | 100.685 | False |
| 13 | docker_contexts | 0 | 155.277 | False |
| 14 | docker_version | 1 | 160.794 | False |
| 15 | docker_info | 1 | 701.632 | False |
| 16 | docker_containers | 1 | 163.241 | False |
| 17 | milvus_tcp_once | 0 | 351.668 | False |
| 18 | docker_log_filter | 0 | 81.701 | False |
| 19 | wsl_operational_events | 1 | 541.58 | False |
| 20 | hyperv_compute_events | 1 | 562.119 | False |
| 21 | services_unsuppressed | NOT_RUN_BUDGET | — | — |
| 22 | processes_unsuppressed | NOT_RUN_BUDGET | — | — |
| 23 | engine_health_log_filter | NOT_RUN_BUDGET | — | — |

单个系统命令设10秒，日志筛选20秒；原生检查总截止120秒，不轮询。初始helper总耗时6.186165秒，实际子进程主动耗时5544.53毫秒、清理0毫秒、子进程wall合计5544.535毫秒。首个命令2026-10-09T05:25:20.827150+00:00，末个实际命令2026-10-09T05:25:26.420015+00:00。

首次服务/进程枚举使用SilentlyContinue：保留exit1、有效部分JSON和实际空stderr，不臆测整个命令成功。准备一次无抑制消息的澄清检查及生命周期日志筛选时，截止检查已经到121.752125秒；于是21–23全部NOT_RUN，未新增子进程。这个时间包含命令间的离线审阅与工具准备，不是主动探测或cleanup超时；后续不续开窗口。没有终止任何子进程、Docker、WSL、服务或用户进程。

旧证据缺口按Commander明确豁免保留HISTORICAL_UNRECOVERABLE_OR_UNVERIFIED；新记录没有冒充旧84次stderr或重建历史时间线。新任务仅创建新文件。

## 不变性和停止边界

4618项受保护历史/源码/配置/Prompt/Golden/locks哈希末轮PASS；入口HEAD=6076a70f18f3062b8faff4fc179cf21f00a95036，完整status与Bootstrap入口相同=True。未在预算结束后新增Git子进程，最终以文件SHA核对保护范围；旧工件未改写，未Git add/commit/push/reset。

Docker/Desktop启动重启、WSL启动/shutdown/terminate、软件更新重装reset、权限/Feature/固件/env修改、容器/volume/image/network修改、MySQL访问、Milvus API/检索/向量写入、Jev/DeepSeek/Embedding/fallback/模型重试全部0。只有一次TCP connect，无Milvus应用协议。JUnit/编译执行0；没有修改生产代码或YAML，没有启用MILVUS_ENABLED。

Commander审查后，最小下一步为在已确认权限的同一Windows用户普通PowerShell会话做有界只读对照：wsl --status、wsl --list --verbose、docker version/info/ps。该对照须另行明确授权，仍不得启动发行版、重启或修改权限/设置。若对照仍拒绝访问，再批准仅权限/ACL诊断或管理员只读Feature查询；恢复操作必须根据新证据单独授权。

```text
ENGINE_DIAGNOSTIC=COMPLETED_WITH_ACCESS_LIMITATIONS
END_STATE=STOP_WAIT_COMMANDER
EVALUATION_DATA_READINESS=UNKNOWN
CANARY_02=NOT_AUTHORIZED
PHASE5C=NOT_RUN
PHASE6=BLOCKED
```

