# E07 Host Context / Evaluation Bootstrap 审查报告

**已完成附件规定的受限访问分支：CODEX_CONTEXT_ACCESS_LIMITED，STOP_WAIT_COMMANDER。历史完整性PASS；Codex当前三项只读检查均被拒绝，Gate A–D NOT_RUN。交付已审计的普通PowerShell只读helper；未对host进行任何恢复。**

Commander明确提供的正常用户PowerShell结果已标USER_SUPPLIED_HOST_EVIDENCE：WSL2、docker-desktop Running、Docker Linux Engine29.6.1可访问，milvus-standalone/minio/etcd Up/healthy。Ubuntu Stopped和当前Codex拒绝访问都不作为主机损坏证据。用户证据并未证明evaluation数据库/Collection一致或本进程权限。

本轮Codex docker version与ps -a各exit1，独立stderr均为 `permission denied while trying to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`；wsl list exit4294967295，实际stdout `Wsl/EnumerateDistros/Service/E_ACCESSDENIED`，独立stderr为空。三项并行各一次、各10秒限时、超时0；没有重试、提权或追加系统探测。详细UTC、elapsed、实际argv及6个stream文件在command-ledger.json。

具体权限机制尚UNKNOWN；已观察到的是当前Codex调用失败与用户声明普通host成功的差异，不宣称已修复或已在普通host执行。本轮没有MySQL/Milvus API连接或数据复制，未创建Collection；source/target实时SHA、52向量和runtime均UNKNOWN。

## 普通PowerShell运行方法

在刚才已确认访问正常的**普通、非管理员Windows PowerShell**中执行以下精确命令，无需改变当前目录：

```powershell
& 'E:\learning\IDEA_PROJECTJ\ai-interview-system\target\phase5-e07-host-context-bootstrap01\host-readonly-check.ps1'
```

脚本SHA-256：`968fae507f6fd4a5077fefcae14bd9889c977ad7a405441e134594aaeec2e1a6`。脚本支持Windows PowerShell 5.1语法，已用当前安装的PowerShell解析完整文件，无语法错误；实际普通host运行仍NOT_RUN。脚本拒绝管理员会话，不申请UAC、不修改执行策略。若当前会话的策略阻止脚本，保留报错交Commander裁定，不自动绕过。

helper只调用docker version、带安全字段format的docker ps -a、wsl --list --verbose各一次；不会显示容器Command或Env/Secret。输出只写入脚本目录下新建的 `host-readonly-results/host-UTC-GUID/`，通过CreateNew禁止覆盖，含6份独立stdout/stderr、once-lock、host-observations与SHA manifest。退出时显示确切结果目录；提供该目录中的host-observations.json及manifest供审查。helper不会自动启动Bootstrap或读取数据库。

每次CLI等候上限10秒；如超时，仅允许终止helper刚创建的CLI Process对象，绝不结束Docker/WSL engine、服务或发行版。数据准备、运行时enabled预检和Canary不会随helper结果自动启动，须Commander审查之后另行继续。


## 本轮文件与用途

- HOST_CONTEXT_COMPARISON.md：区分用户host证据与Codex实际结果，附helper运行说明。
- EVAL_BOOTSTRAP_READINESS.md：逐门禁PASS/NOT_RUN及写入禁用边界。
- EVAL_DATA_INTEGRITY.md：区分历史参考与当前未知，准确保留空实际写入清单。
- EVAL_RUNTIME_BINDING.md：明确未运行Spring/evaluation enabled预检。
- EXECUTION_EVIDENCE.json与command-ledger.json：实际调用、计数、证据路径、完整性与helper审计。
- host-readonly-check.ps1及helper-static-audit.json：三项只读检查脚本与静态审核结果；host实际运行未验证。
- deliverable-manifest.json：所有新文件SHA，旧工件不覆盖。

## 不变性、计数与终点

历史保护文件4674项SHA复核PASS，Git入口HEAD `6076a70f18f3062b8faff4fc179cf21f00a95036` 与完整status保持原状。用户原有dirty文件保留。源码/Prompt/Golden/YAML/benchmark/旧报告/响应/manifest/once-lock未写入；旧stderr缺口获明确豁免，没有回填。

Docker version、ps -a、wsl list各一次，并行提交；各最多10秒，超时0，没有重试或权限提升。所有真实stdout/stderr分文件保存，WSL错误位于stdout而stderr确实为空。调用计时、argv和证据路径见command-ledger.json。仅入口Git另有两次只读调用。

Docker/Desktop/容器启动重启、WSL启动/shutdown/terminate、更新/安装/reset、权限/组/配置/长期env修改、MySQL连接/SELECT/DDL/DML、Milvus API/Collection创建/向量读写/检索、Jev/DeepSeek/Embedding/fallback/重试、runtime runner、Git变更全0。原数据库/向量before/after实时SHA为UNKNOWN：没有连接，不能拿历史SHA冒充当前验证。空写入清单只表示本轮未写入，不能证明目标空库。

此分支只交付host只读helper和说明，不生成数据库写入脚本；helper由Codex执行0次。JUnit/生产编译0次，静态语法检查不构成host真实运行成功。

```text
CODEX_CONTEXT=CODEX_CONTEXT_ACCESS_LIMITED
BOOTSTRAP_GATES_A_TO_D=NOT_RUN
EVALUATION_DATA_READINESS=UNKNOWN
CANARY_02=NOT_AUTHORIZED
PHASE5C=NOT_RUN
PHASE6=BLOCKED
END_STATE=STOP_WAIT_COMMANDER
```

