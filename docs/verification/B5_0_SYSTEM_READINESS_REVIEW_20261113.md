# B5-0：全系统缺陷收口与真实测试准入审查

执行日期：2026-11-13（Asia/Shanghai）。执行者：Codex。仓库：`/Users/kun/Developer/projects/ai-interview-system`。

**准入结论：CONDITIONAL_GO。** 当前工作区可以进入隔离环境中的小规模人工及 AI 模拟测试；尚不具备“真实第三方集成已验收”或公开多用户使用的证据。本轮未确认新的 P0/P1；确认两项未修 P2：多字节密码导致正常输入返回 500、Embedding 上游错误正文进入服务端日志。另保留恢复范围、时间预算、观测与真实依赖验证风险。

本结论针对 **HEAD `01ac774caff491b978dd2268e8b427355a64b97a` 加入场 B4-D/B4-E 工作区**，不针对只拉取远端 main 的版本。本轮只新增两份指定文档并更新两份工程清单，没有修改业务代码、测试源码、配置、依赖或 Schema，没有 commit/push。

## 1. 准入条件与停止条件

进入下一轮真实测试前，必须完成 [真实测试计划](B5_REAL_WORLD_TEST_PLAN_20261113.md) 的环境准入记录：隔离目标、实际配置、题库/知识库、前后端启动、测试账号、调用预算及证据保存位置。当前进程没有 DB/模型凭据环境变量，`src/main/resources/application-local.yaml` 不存在，本轮也没有启动项目真实 Milvus/模型链；不能把本报告当成已经可直接点击演示的声明。

允许范围：

- 单操作者、最多一个主动评分/报告任务，先完成一场 EASY，再逐阶段开展模型质量测试；不做压力测试或公开访问。
- 只用合成账号、答案和知识材料；使用专用测试凭据，日志仅本机受限保存，分享前脱敏。Embedding 日志缺口修复前，不引入真实简历或敏感资料。
- 正常路径使用 8～72 个 ASCII 字符的测试密码。多字节边界作为已知缺陷用例单独记录，不能把避开它写成已修复。
- 答案恢复保留同一浏览器站点存储、同一用户、原 requestId/原始文本；跨设备或清空存储后的失败答案恢复不在保证范围内。
- 真实调用须在后续执行任务明确预算后进行。本轮模型调用为 0，不自动启用任一 `RUN_REAL_*`。

以下情况立即停止相应场景并重新评估为 NO_GO：越权读写、重复有效评价/重复推进/重复弱项累计、模型非法结构仍被接受、FINAL 与报告数值不一致、按既定恢复路径仍无法退出停滞状态、真实凭据进入可分享日志。未完成环境预检则该执行场景记 BLOCKED，不将“尚未执行”写成通过。

## 2. Git、累计批次和用户修改保护

入场执行了 `pwd`、`git status --short`、`git branch -vv`、`git log -18 --oneline`、`git diff --stat`、`git diff --cached --stat`，并保存完整 diff 和 24 个既有变动文件的 SHA-256/mode。暂存区为空。原有 `AGENTS.md`、`mvnw` 的 0755 权限、Compose、MQ 文档、两处 `.DS_Store`、B4-D/E 源码及验收文档均不属于本轮开发成果。

2026-11-13 经 GitHub 连接器 `fetch_commit(main)` 实时核实远端 main 为 `89b77dc082272e38cfbbecb4b57c37edae9ee74d`；与缓存 origin/main 一致。`git rev-list --left-right --count 89b77dc...HEAD` 为 **0 / 3**。未 fetch 或改引用；未检查所有其他远端分支，不声称某提交从未发布到任何位置。

- **J1**：`b525683ce72def1f59432a33959f672c2c87b7a1`，移除 Jev 运行时、保留 DeepSeek 和业务恢复；已本地提交且在远端 main。历史验收 [J1](JEV_REMOVAL_DEEPSEEK_ONLY_20261111.md)，最终普通回归 1526/0/0/19。
- **B1**：`1df25ea6dfe6c818cf0c75a58c47f58c09ed524a`，并发完成后重读并重放；已在远端 main。[B1](B1_CONCURRENT_IDEMPOTENT_REPLAY_20261111.md)，历史全量 1549/0/0/19。
- **B2**：`7d259818c7650b4c57e3d642d3821cb17291e4a0`，成功 claim 后查询异常纳入失败收尾；已在远端 main。[B2](B2_POST_CLAIM_FAILURE_RECOVERY_20261111.md)，历史全量 1559/0/0/19。
- **B3**：`6de9e7aecb69a5269d1c8fa466f3d2dc57865ee4`，报告/弱项 FINAL-only、计划和答案关联校验；已在远端 main。[B3](B3_REPORT_FINAL_CONSISTENCY_20261111.md)，历史全量 1589/0/0/19。
- **B4-A**：`004e434d3c1352c15d022bc1bf08abdfb02f4a02`，审计和故障矩阵，不能计为代码修复。已本地提交，未进入本次核验的远端 main。[B4-A](B4A_DEEPSEEK_RELIABILITY_AUDIT_20261112.md) 首轮 201/0/85/0 为 Mockito attach 环境问题，85 项重跑 85/0/0/0；没有该批独立完整回归。
- **B4-B**：`f764c087f75d6b515b09bf2a0aef3f1d9a20c906`，DeepSeek 错误日志安全边界；已本地提交，未进入远端 main。[B4-B](B4B_DEEPSEEK_LOG_SAFETY_20261112.md)，历史全量 1635/0/0/19。
- **B4-C/C3**：当前 HEAD，DeepSeek 类型化错误/嵌套超时分类与累计收口；已本地提交，未进入远端 main。[B4-C](B4C_DEEPSEEK_TIMEOUT_RETRY_20261112.md)、[C3](C3_B4A_B4C_CLOSEOUT_20261112.md)，历史全量各 1698/0/0/19，不能相加。
- **B4-D**：3 个 Milvus 生产文件、7 个新增/修改测试文件及文档在工作区，未暂存/提交/推送，无 SHA。[B4-D](B4D_MILVUS_RETRY_DEADLINE_20261113.md) 历史全量 1732/0/0/19；真实 SDK/合成 gRPC 与真实 MySQL均有证据，真实 Milvus 未验证。
- **B4-E**：4 个前端源码/测试文件及文档在工作区，未暂存/提交/推送，无 SHA。[B4-E](B4E_REPORT_STALE_RECOVERY_20261113.md) 历史前端 317/0/0/0、后端定向 123/0/0/0；当时未全量 Maven。本轮已补累计全量。

另核对 [C0 收口](J1_B3_PHASE_CLOSEOUT_20261111.md)、[源码指南](J1_B3_SOURCE_LEARNING_GUIDE_20261111.md) 和两份工程清单。历史失败、环境错误、当时 Git 状态保留在原报告，不覆写为本轮结果。`b1f8c6b` 的既有 MySQL 锁等待诊断修正仍在当前基线。

## 3. 按真实操作顺序的业务审查

### 3.1 注册、登录与身份认证

`AuthController → AuthService → BCrypt/UserMapper/JwtTokenService`；前端 `authFormRules → auth store → /auth/*`。重复账号/邮箱统一 400；错误凭据是本项目既有 400，受保护请求缺失/无效 Token 是 401，不能混为一谈。`JwtAuthenticationFilter` 每次查当前数据库用户状态/角色，并校验 credential_version 和密码变更时间；管理员接口以当前角色授权，USER 是 403。公开登录/注册不会被旧 Token 阻断。

当前鉴权与归属回归通过；多字节密码边界另列 P2-01，不能作“所有正常输入均无 500”的结论。未开展暴力破解/公网限流测试。

### 3.2 创建或恢复面试

`createOrResume → InterviewSessionService → snapshotService → createAndStartSession`。已有 active 会话直接恢复；新建时固定题库快照，短事务锁 user 行后再查 active，批量落全部 MAIN，激活首题。EASY 为 5 个 MAIN：JAVA_BASIC/JAVA_COLLECTION/SPRING/MYSQL/NETWORK。题库不足受控业务失败，不能让模型补出 MAIN。

前端 current/create API 字段和状态枚举一致，恢复不重新选题。真实 MySQL 的用户锁、快照一致性、创建事务测试本轮执行；未用静态源码代替这些证据。

### 3.3 主问题展示与提交

页面 `useInterviewSession.submitAnswer` 先持久化按 user/session/question 分组的原文及 requestId，并标记 dispatched，再发 POST。同步锁防页面并行提交；发送后锁定答案；存储写入失败则不发送。后端先校验所有权、问题归属、requestId 和完整原文，再短事务写 Answer。

65535 UTF-8 字节限额前后端一致。服务不可用是既有 400 + `面试评价服务当前不可用`，前端识别该文案；409 的进行中、原 requestId 载荷不一致和其他 requestId 分支可区分。当前未知并发 409 则刷新权威状态，不自动更换 requestId。

### 3.4 RAG、DeepSeek、Java 校验与持久化

`Workflow claim → EvaluationContext → EvaluationRetrievalAdapter → Query Embedding → Milvus search → MySQL eligibility → Prompt → DeepSeekJudgeProvider → LlmEvaluationValidator → Java sum/level → FollowUpPolicy → Workflow短事务`。

检索 Query 不含用户答案，Prompt 可以包含；只有 MySQL 校验合格、实际提供给 Prompt 的证据落 rag_hit_log。检索异常向上传播，不伪装成合法空命中。评分五维各为整数 0～20，Java 求和；评分点 ID 必须完整唯一、属于 MAIN 快照；声称 covered 必须有 evidence。结构校验不证明语义判断正确，后续仍须人工盲审。

新评价及成功 trace 在 `persistOwnedEvaluation` 的 answer 行锁/attemptId 检查之后，与状态推进同一事务提交。失败轨迹使用独立 fenced 审计路径；RAG 本身失败、尚未取得 retrievalResult 时不应硬要求存在完整批次证据。LLM 不能写业务状态。

### 3.5 追问、切题与结束

MAIN 仅在 `followUpRecommended=true` 且（总分 <60 或遗漏 CORE）且存在未覆盖目标时追问；目标最多 2 个、CORE 优先。低分并不必然追问，建议追问也不必然采纳。FOLLOW_UP 不允许再次建议追问；违反结构约束应 502/FAILED，不能继续造题。

追问时 MAIN 评价为 INITIAL，完成 MAIN 的计数不增加；FOLLOW_UP 的综合 FINAL 才完成该 MAIN。无追问则 MAIN 直接 FINAL。下一 MAIN 存在时 NEXT_MAIN；否则 FINISH、COMPLETED、current=null、completed=planned。页面“继续/结束”消费后端决策并刷新，不另发第二个业务推进请求。最后一次提交已经结束业务，不能要求按钮再结束一次。

### 3.6 同 requestId 恢复与评价 stale 接管

EVALUATED 优先重放持久评价和最新 Session；B1 在当前题不匹配时重读 Answer，避免已完成竞态误报 500。SUBMITTED/FAILED 通过 claim/retry；fresh EVALUATING 是 409；stale 按数据库 updated_at 与默认 10m 阈值竞争新 attempt。B2 将下一题查询放入已取得所有权的 try 中；异常按原 attempt 标 FAILED，不能覆盖接管者。

前端“按原答案重新获取评价”重用原请求；“检查当前状态”只查询。FAILED/stale 新尝试可能再次调用模型；数据库只允许当前 attempt 提交，不保证供应商只执行/计费一次。原浏览器存储缺失时的限制见 P2-03。

### 3.7 报告生成、失败与 GENERATING 恢复

报告 GET 是只读；POST 才执行 claim/reclaim。B4-E 为 GENERATING 增加显式按钮，仍每 3s GET 轮询，不凭浏览器停留时间判 stale。POST 期间暂停轮询，epoch 使旧响应失效；409/网络/5xx 后确认 GET，未知结果进入 RECOVERY_UNKNOWN，不能自动补发 POST。

ReportService 先验证所有权/COMPLETED，再按 version CAS 领取。已有 READY 直接返回；非 READY 已有报告恢复会校验 FINAL 来源，不重复调用模型/更新弱项。新报告检查 MAIN 计划、唯一 FINAL、最终 Answer 与 MAIN/FOLLOW_UP 关联，Java 用 FINAL 平均分保留两位 HALF_UP。Report + Weakness + READY 同一短事务，旧 version 收尾失败不污染新任务。

READY 历史结果直接读取，不能据此声称历史损坏数据已重新审计或修复。报告自然语言也可能与分数矛盾，属于真实模型质量判定范围。

### 3.8 历史、详情和跨用户隔离

历史仅返回本用户 COMPLETED，分页边界受控，按 ended_at/id 倒序。Detail/Report/Answer 对不存在和非本人会话均返回不泄露存在性的 404。VO 不暴露 requestId、attemptId、参考答案快照或 raw_result；前端历史入口可进入详情/报告。该边界也意味着客户端无法从 Detail 找回丢失的恢复键，不能把数据最小暴露等同于跨设备恢复已实现。

前端路由、身份代次和页面作用域测试通过；HTTP 状态已核对 400/401/403/404/405/409/413/502/500。405 在前端归入通用未知错误，仅为非法方法诊断，不影响现有 UI 正常路径。

## 4. 外部依赖、安全与隔离

- **DeepSeek**：基础配置 model=`deepseek-flash`，connect=3s、read=60s、max_tokens=4096、JSON object、thinking disabled、stream=false。单次应用调用，没有自动 HTTP 重试；HTTP reason/type 分类和安全异常边界仍在。未发送 temperature/seed，不能把供应商默认采样值写成已知。真实服务可用性、协议兼容、费用和评价质量未验证。
- **Embedding**：`qwen3.7-text-embedding`，1024 维、批量最多20、connect=3s/read=20s；Spring AI 1.1.8、RetryTemplate maxAttempts=1。校验模型、数量、索引、有限浮点、维度、usage；无异常转假向量。上游异常 cause 保留造成 P2-02。
- **Milvus**：实际 JAR 2.6.20。基础 connect=5s/request=10s；Spring 工厂固定一次 callable、初始化 cancellable Context、null hasCollection 拒绝；配置毫秒边界有效。**evaluation profile 实际覆盖为 15s/20s**，不能把基础默认当所有运行环境实际值。独立维护 CLI 不继承此工厂。
- **时间与重试**：前端 Axios 120s 只结束客户端等待；EVALUATING/GENERATING 默认 stale=10m、PROCESSING=15m。**B4-D 仍不承诺整链严格 wall-clock 上界**，也不承诺 HTTP 断开或 stale 接管取消旧供应商操作。迟到向量可能在一次补偿后成为物理孤儿；MySQL eligibility 防止其进入有效证据，不证明最终已清理。
- **环境**：本轮 Maven 用 test / 既有 local,test 与专用 ai_interview_test。所有 RUN_REAL_*、MILVUS_ENABLED 及旧 SPRING/JVM 配置覆盖均从子进程环境清除，DEEPSEEK_ENABLED=false。test 的 Embedding 是 loopback 假配置，Milvus/DeepSeek disabled，EnvironmentIsolationInitializer 未绕过。
- **安全检查**：978 个 Git 跟踪文件做私钥/provider-key/GitHub-token 模式扫描，疑似命中 0；敏感文件名跟踪命中 0。忽略规则仍覆盖 application-local；本轮 JAR 不含 application-local/application-test、无 Jev 类，保留 milvus-sdk-java-2.6.20。模式扫描有漏检边界，不宣称绝对无秘密。未输出或复制用户真实凭据。

## 5. P0/P1/P2 发现、影响与建议

### P0 / P1

本轮源码审查、累计回归及下列定向复现中**未确认未解决 P0/P1**。此结论不是全系统无缺陷证明，尤其不覆盖真实外部服务、生产负载和未穷尽的并发交错。

### B5-P2-01：多字节密码通过校验后返回 500（新确认，未修）

- 证据：`RegisterRequest.password` / `ChangePasswordRequest.newPassword` 使用 `@Size(max=72)`，前端 `authFormRules.validatePassword` 使用 String.length；`PasswordConfig` 使用真实 BCrypt。临时 `ReadinessProbe` 通过真实监听端口、Spring 安全链和隔离 MySQL复现。
- 输入与实际：72 ASCII 注册 200；24 个“界”（72 UTF-8字节）注册200；25个“界”（75字节、25 UTF-16单位）注册500，错误栈 `password cannot be more than 72 bytes`，没有新增用户。合法账号修改成同样75字节新密码也500，原密码哈希不变。对已有账号用75字节错误密码登录为400，不错误声称登录也500。前端3组检查均接受。
- 影响/分级：合法字符长度输入进入服务器异常路径；不涉及越权、密码覆盖或常规ASCII路径失效，列P2。不能因全量测试通过而隐去此缺陷。
- 建议独立修复批次 **B5-FIX-A（候选，未授权实施）**：统一注册/登录/改密的 UTF-8 字节上限与文案、前端预校验和服务边界，覆盖ASCII72/73、中文72/75、emoji边界及事务不变性。不要截断密码或静默改写已存密码，也不要只给所有 IllegalArgumentException 加通用400来掩盖根因。

### B5-P2-02：Embedding 上游错误正文进入日志（既有风险，本轮实证）

- 证据：`EmbeddingConfiguration.createOpenAiApi → Spring AI错误处理 → SpringAiEmbeddingClient.requestBatch(cause保留) → GlobalExceptionHandler.handleExternalServiceException(log.error)`。MockRestServiceServer返回400和合成标记 `B5_SYNTHETIC_UPSTREAM_ECHO_ONLY`，1次Mock HTTP；最终Logback消息/Throwable包含该标记。公开Result仍为502且不含标记。
- 影响：上游回显内容可进入本地日志；本次只有合成数据，没有真实密钥泄露证据。B4-B只保证DeepSeek边界，不能外推成全局安全。
- 建议独立修复批次 **B5-FIX-B（候选）**：Embedding HTTP/解析/供应商模型名等不可信字段使用安全分类，保留诊断且避免正文通过cause/suppressed及框架日志旁路泄露；补全渲染日志断言。Milvus与全局请求解析日志另行盘点，不未经证据扩大重构。敏感数据测试/外部日志汇聚前应先修复。

### B5-P2-03：失败答案恢复依赖原浏览器保存的请求键（范围限制）

- 证据：`submitRecordStorage` 使用localStorage；`retrySamePair`要求dispatched记录；后端对另一requestId返回409；Detail VO不返回requestId。现有InterviewSessionView测试覆盖RECOVERY_BLOCKED及刷新仍阻断。
- 影响：换浏览器/清空存储后，若原答案仍FAILED或stale EVALUATING，新页面无法通过已有入口恢复原请求，且active会话不能另起一场。原会话已完成时仍可看历史，不应笼统写成所有换设备都失败。本轮未做真实双浏览器丢键实验，因果链为源码与既有Mock分支证据。
- 建议：测试阶段保留原浏览器数据；记录专门负向用例，后续单独评估安全的所有者恢复合同。不要通过删除Answer、换requestId或绕过CAS“解锁”。

### B5-P2-04：时间上界、迟到写入与独立维护CLI（保留B4-D边界）

- 证据：B4-D实际SDK/gRPC故障测试、本轮MilvusGrpcDeadlineTest 12项与fencing19项；CLI直接new MilvusClientV2。一次callable仍可包含内部RPC；进程/数据库/Embedding/DeepSeek不共享deadline。
- 影响：请求可能超过前端等待；stale恢复可能产生额外供应商调用，迟到孤儿未必即时清理。未证实普通单人负载有持续不可用事故，不提升为已确认P1。
- 建议：保持默认stale，不为缩短测试随意缩到任务预算以下；停写后按精确ID受控维护；CLI治理/整链预算独立批次，本轮不运行维护删除。

### B5-P2-05：当前版本真实服务及浏览器整链证据缺口（准入前置）

- 证据：19项opt-in跳过，本轮真实DeepSeek/Embedding/Milvus和完整浏览器面试均未执行；本机应用local配置缺失。历史其他阶段的真实服务报告不自动覆盖J1～B4-E累计树。
- 影响：只能允许按计划补验证，不能宣布可公开演示或正式发布。
- 建议：先完成隔离环境和1场真实EASY；若服务不可用/题库不足，记环境BLOCKED；故障发生后区分配置、第三方与业务回归。

### B5-P2-06：调用成本、质量随机性与并发容量仍缺证据

- 证据：DeepSeek响应DTO只保留model/choices，不保留usage；数据库无调用级费用账。没有业务全局有界许可；B4-A的并发许可/安全事件仍是未编号候选。
- 影响：仅靠请求次数不能给出实际成本；不能将一次模型答案当稳定评分，也不能推出多用户容量。FAILED/stale额外尝试可能额外收费。
- 建议：串行分阶段、每阶段记供应商账单/usage来源和未知项；以人工评分点覆盖裁决，重复实验使用新业务请求，幂等重放单独验证。没有本轮性能提升或成本下降结论。

## 6. 本轮实际测试、命令与结果

T/F/E/S = Tests / Failures / Errors / Skipped。测试数包括跳过，不跨轮相加。运行目录：`/private/var/folders/xx/vxww8vxd1hz_f8szvp9th6j40000gn/T/b5-0-20261113-_puq9a_q/`（下称证据目录）。

实测：macOS26.7.1 ARM64、Temurin21.0.13、Maven3.9.16、Node24.22.1、npm11.19.1。Docker镜像mysql:8.0.45 ARM64；本轮专用容器 `ai-interview-b5-0-20261113-mysql`，127.0.0.1:62043，`/var/lib/mysql` tmpfs、无持久卷、+08:00。只初始化ai_interview_test的13表与系统库；当前仓库SQL只在内存中替换库名，未连接ai_interview或evaluation，未修改仓库SQL。

`run_checks.py` 清除RUN_REAL_*、SPRING_*、MILVUS_*、DEEPSEEK_*、EMBEDDING_*、旧DB和JVM/Maven覆盖，向backend子进程注入临时测试库和Mockito javaagent。未修改pom或持久配置。旧Surefire先归档，本轮XML在工具回归覆盖target前已单独保存。

在仓库根执行的实际子命令：

```sh
sh mvnw -B -ntp test
sh mvnw -B -ntp -DskipTests package
python3 -B tools/check_deepseek_only.py
python3 -B tools/run_public_offline.py
git diff --check
git diff --cached --check
```

- 完整Maven：**1732/0/0/19，退出0**，136套件，1713非跳过，BUILD SUCCESS。Maven总时长40.514s仅为本机此次观测，不能与B4-D28:17比较后声称优化。
- 后端package：退出0，skipTests，测试数不适用；产物 `target/ai-interview-system-0.0.1-SNAPSHOT.jar`。
- DeepSeek-only：**37/0/0/0，退出0**；21个工具源编译、9项静态检查；3个退役入口均按预期退出1并包含RETIRED，不是3个失败测试。没有执行Windows PowerShell或历史付费重放。
- Public offline：Java **28/0/0/0**、Python **9/0/0/0**、候选gate Java **54/0/0/0**，总入口退出0；Java28项与全量有重叠，候选工具不等于生产策略或真实模型验证。

在 `frontend/` 执行：

```sh
npm run test:unit
npm run typecheck
npm run lint
npm run build
```

- Vitest：**317/0/0/0，36文件，退出0**，无未处理异常；含InterviewReportView56项。
- typecheck、ESLint、build均退出0，测试计数不适用；产物在ignored `frontend/dist/`，未发布。

关键后端套件在本次全量中的数量（均F/E/S=0）：

- 真实MySQL/事务/受控并发：ConcurrentReplay18、PostClaimFailure3、ReportFinalConsistency24、EvaluationClaimConcurrency11、WorkflowTransaction6、ReportTransaction9、SessionTransaction3、QuestionPlanSnapshot1、StaleRecoveryCas3、KnowledgeAttemptFencing19、ConcurrentPasswordChange1。
- 真实MySQL+Mock HTTP业务链：FullInterviewBusinessOffline112；该类有外围测试事务，不能单独证明跨请求提交和事务外模型调用，须结合前述独立事务测试。
- Mock/配置：JwtAuthenticationFilter32、EnvironmentIsolationInitializer23、DeepSeekFailurePolicy42、DeepSeekLogSafety36。
- Loopback/真实依赖：DeepSeekHttpTimeout4使用JDK HTTP与本地合成服务；MilvusSdkRetryBehavior15使用实际SDK，MilvusGrpcDeadline12使用合成gRPC。均不是真实第三方服务。

19项跳过：RealEmbeddingSmokeTest1、RealMilvusVectorStoreSmokeTest1、KnowledgePersistenceIntegrationTest10、RealKnowledgeDocumentProcessingSmokeTest2、RealKnowledgeDocumentProcessingFailureIntegrationTest2、RealMilvusSchemaValidationSmokeTest2、AiSystemBaselineEvaluationTest1。每个类保留环境条件与assumption双门禁。普通MyBatis/MySQL测试没有被这些开关跳过。

新增临时探针（不加入仓库、不计入1732或317）：

- 首次 `javac -proc:none -encoding UTF-8 -cp <test+main+依赖> -d <证据目录> ReadinessProbe.java` 退出1，两个HttpRequest通配符导入歧义；执行测试0，JUnit计数不适用。只改临时脚本为明确导入，重编译退出0。
- `java -cp <证据目录:test-classes:classes:依赖> ReadinessProbe <证据目录>/readiness-probe.json`：退出0，**7项观察、0探针断言失败/错误/跳过**；其中6项真实HTTP+真实MySQL、1项Embedding Mock HTTP+Logback。该成功表示确认现状，包括两个500和日志缺口，不表示产品缺陷已通过修复验收。
- Node直接导入authFormRules：3组密码边界检查，退出0，记录于frontend-password-probe.log；同样是缺陷复现，不是浏览器E2E。
- 敏感模式/JAR检查退出0；diff与新增文档链接/空白检查退出0。测试计划Q1～Q6的7条SELECT以sessionId/userId=0在专用ai_interview_test真实执行，语法/字段校验退出0，无写操作；这是SQL可执行性检查，不是实际人工场景通过。开始时Docker只读查询在沙箱内权限失败，获准执行环境后继续；不是业务测试失败。首次GitHub查询参数名错误后更正为commit_sha，随后成功读取远端；不计测试失败。

执行器实际入口为 `python3 <证据目录>/run_checks.py frontend` 和 `python3 <证据目录>/run_checks.py backend`；前者在沙箱内成功，后者在允许loopback/MySQL访问的执行环境成功。探针classpath完整值保存在probe-classpath.txt，临时Java源码在ReadinessProbe.java；前端直接执行命令为 `node --input-type=module -e '<frontend-password-probe.mjs的完整源码>'`（另有同内容stdin观测），没有增改仓库测试文件。Python/Subprocess安全传参，凭据不写命令字符串。

## 7. 交付、证据留存与停止范围

新增：

- `/Users/kun/Developer/projects/ai-interview-system/docs/verification/B5_0_SYSTEM_READINESS_REVIEW_20261113.md`：本轮准入结论、累计状态、缺陷及实际回归。
- `/Users/kun/Developer/projects/ai-interview-system/docs/verification/B5_REAL_WORLD_TEST_PLAN_20261113.md`：可执行人工/AI场景、只读数据库核对、结果模板与阶段门禁。

定向更新：

- `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/IMPROVEMENT_LEDGER.md`：B5-0审查记录与本轮状态，不覆盖原批次验收。
- `/Users/kun/Developer/projects/ai-interview-system/docs/engineering/SOURCE_LEARNING_BACKLOG.md`：原B5占位拆为6个具体学习条目；清单54条，全部保持待学习/掌握未确认。

原始证据包括baseline.json、git-diff-before.patch、remote-main.json、mysql-preflight.json、backend-full-summary.json/套件XML、前端四份日志、工具summary、ReadinessProbe.java/log/json、安全扫描、preservation-check.json、cleanup.json。临时目录不是持久档案；报告保存可追溯摘要和复现条件，不上传整个日志目录或临时凭据。日志可能含测试夹具/合成输入，仍应审阅后分享。

收尾核对：24个入场变动文件中仅两份授权工程清单被本轮更新，其余22个字节/mode保持；暂存区仍空，HEAD不变。原已跟踪diff保持。专用MySQL按名称、任务标签、tmpfs和无持久挂载核验后删除，临时随机口令移除；未触碰既有容器或数据卷。详情见对应证据JSON。

完成审查、回归和计划后停止。B5-FIX-A/B仅为独立建议；真实模型/人工场景尚未执行，不自动进入修复、付费测试、MQ/Redis、部署、commit或push。
