# E07 最小修复选项与授权边界

**当前只批准诊断；Gate A证据缺失已触发STOP，Gate B NOT_RUN。最小下一步是Commander裁定证据补齐路径，不是修复Docker/WSL。根因与数据就绪UNKNOWN。**

## 当前证据与决策树

既有文件SHA一致；84次失败的stderr与独立active/cleanup时间线未持久保存。181.848−180=1.848仅为算术超出阈值，不能视为已测清理耗时。首次/末次poll完成时间记录存在，初始错误时间及准确停止/清理结束UNKNOWN。旧报告named pipe缺失摘录为叙述，不是原始错误码；WSL损坏、虚拟化关闭、Docker后端故障均未获证实。

| 条件 | 最小可审查下一步 | 新授权与影响 |
| --- | --- | --- |
| 当前：原始调用记录未封存 | 只读提取已有原始工具记录并在新目录写脱敏补充证据；不存在的cleanup计时继续UNKNOWN | Commander确认可读取哪一份原始记录；不回填旧锁/报告，不运行修复 |
| 原始记录无法补齐 | Commander书面接受已列缺口，重新授权有界只读Gate B | 只读WSL/虚拟化/服务/context/daemon/日志检查；不赋予重启或写入权 |
| 后续证明WSL健康而Docker backend出错 | 按具体错误码选择最小恢复动作 | 当前没有错误码，不能指定无条件重装/reset；重启可能中断所有Docker工作负载，需单独批准 |
| 后续WSL查询返回实际错误 | 先审查错误码与发行版/工作负载，再选择最小动作 | shutdown会停止全部发行版；terminate也中断指定发行版；二者均需新明确授权，update/repair不得捆绑执行 |
| 后续证明Windows虚拟化前置条件缺失 | 明确缺失Feature/固件状态后列出对应启用步骤 | 修改Windows Feature或固件需要显式许可，可能重启；本轮无实际状态证明 |
| 后续daemon健康、现有Milvus容器已识别且停止 | 报告真实名字、部署/挂载来源，提议有界start-existing-only | 仅另行批准后启动已核验现有容器；不能猜名字、compose up或重建；启动并不证明Collection健康 |
| 最终只有破坏性reset才可能恢复 | STOP；另行审查独立隔离基础设施方案 | 禁止删除/重置现有source数据、VHDX和volume；替代设施部署与数据准备也需独立批准 |

以上为条件分支，均非现状诊断或已执行修复。当前不建议无条件安装、更新、重装或reset。恢复engine后的Bootstrap B–E、enabled运行验证和Canary仍不获本任务授权。

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
