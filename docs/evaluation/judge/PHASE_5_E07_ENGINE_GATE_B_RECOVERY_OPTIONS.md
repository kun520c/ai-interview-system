# E07-ENGINE-DIAG-02 最小待授权恢复选项

**当前确证访问拒绝，基础设施故障根因UNKNOWN。最小下一动作是普通用户会话的有界只读对照；本轮没有修复或启动任何系统/容器。**

## 建议顺序与授权范围

| 条件/次序 | 最小可审查动作 | 风险与新授权 |
| --- | --- | --- |
| 1. 当前：WSL/Docker API/CIM拒绝访问，但Desktop进程与19530 TCP存在 | 在已确认权限的Windows用户普通PowerShell会话，对照WSL status/list和Docker version/info/ps，独立10秒限时与stdout/stderr记录 | Commander另行批准此执行上下文；不自动UAC、不启动发行版、不修改sandbox/ACL或环境。只用于区分诊断上下文限制与主机权限故障 |
| 2. 普通会话同样拒绝访问 | 先只读审核用户token、Docker named-pipe/服务访问策略；按确证缺项提出最小权限变更 | 读取权限信息与任何ACL/用户组变更必须分开授权；组或ACL变更可能扩大Docker控制权、需要重新登录，当前不执行 |
| 3. 需要确证虚拟化前置条件 | 单独批准管理员只读查询两个Feature与必要CIM字段 | 只读提升不等于启用Feature；本轮“不提升”仍有效。启用/固件改动和可能重启须再另批 |
| 4. 后续确证WSL正常而Linux daemon/backend有特定故障 | 依据具体新错误提出一次有界Docker Desktop恢复/重启 | 会中断Docker工作负载；必须明确批准。当前permission denied不是daemon崩溃证明，不预先执行重启 |
| 5. 后续确证WSL故障且需shutdown/terminate | 先核对所有发行版和工作负载，再选择有证据支持的最小恢复命令 | shutdown停止所有发行版；terminate中断指定发行版。二者当前均未授权，update/repair不可捆绑 |
| 6. 后续daemon可访问且已核验目标容器Stopped | 明确已有Milvus/minio/etcd容器名字/部署/挂载后，另批start-existing-only | 当前容器名字/状态UNKNOWN，无可批准的具体docker start列表；禁止猜名、compose up、重建或改restart policy |
| 7. 只有破坏性reset可能恢复 | STOP并审查独立隔离基础设施方案 | 不删除现有VHDX/volume/source数据；重置/重装或替代设施部署均需新审批，不能默认必要 |

当前不需要因helper服务com.docker.service=Stopped而直接Start-Service；该状态不能独立代表Linux daemon故障。19530连通也不能授权读取/复制evaluation数据。Docker API与WSL stdout/stderr原文路径见command-ledger；日志dxg -22/-2和allowed-orgs文件不存在仅为观察，不作为重装或reset依据。

所有恢复分支只建议，没有执行。对照检查成功也不会自动恢复Bootstrap B–E：数据库/Collection数据准备、MILVUS_ENABLED运行验证、Canary-02、Phase5C、Phase6需要各自明确授权。

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

