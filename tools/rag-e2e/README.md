# 单次真实 RAG HTTP E2E 准备交付

## 2026-10-12 第三次执行后的只读一致性复验

第三次 `rag-e2e-attempt-003-20261012` 已执行。原 report.json 保留 FAIL / RAW_MODEL_TO_DB_MISMATCH，
原两个锁、用户361、会话139及历史数据全部保留。答案77已 EVALUATED，评价31已 FINAL / 90 / EXCELLENT / NEXT_MAIN。
失败是 Runner 将 MySQL JSON 文本与模型原始 JSON 字符串直接比较；MySQL 对对象键顺序及格式进行转换。
另修正评分点 JSON 的 Jackson IntNode/LongNode 表示差异，完整字段、数组顺序和数值内容仍严格校验。

`RagE2eConsistency.verifyModel` 先验证模型原始 content 的 SHA 与 gate 记录一致，再比较数据库完整 JSON。
不将数据库重新序列化的 SHA 冒充 wire SHA，也不把 DTO 字段子集作为完整原始 JSON 验证。

现有证据足以复验本次业务契约：数据库内容按确定格式复建后精确匹配原 rawEvaluationSha256；
使用生产 Context/Prompt/Validator/Score/FollowUpPolicy 复算，使用生产 getDetail 的纯只读投影，
复建的 Prompt 和原 POST/GET 响应逐字节 SHA 均与本次报告匹配。没有提交答案、启动 Spring、创建外部客户端或调用模型。
真实 SELECT CAST(... AS JSON) 能产生与保存 raw_result 完全相同的字符串。

唯一复验命令（无需第四次付费 E2E）：

```powershell
pwsh -NoProfile -File .\tools\rag-e2e\verify-existing.ps1
```

该入口使用现有 E2E 进程凭据，缺少时使用已有 Windows User-scope DB 凭据；仅传入隔离子进程，
不输出或保存凭据，不导入任何模型 Key。固定连接 ai_interview_test，强制 JDBC/MySQL READ ONLY，
确认 schema、server UUID、transaction_read_only=1，只执行 SELECT，结束 rollback。
每次输出独立 `target/rag-e2e/reverification/<runId>/<timestamp-unique>/`，不覆盖原报告或 review seal。
运行前后核对历史锁/报告 SHA，并核对原 seal 中199个生产源文件。独立编译新增工具，不运行任何会写库的 Maven 测试。

RAG 实际证据为 raw_hit_count=5，水合与 Prompt 引用2条（chunk 525/526，document581）。
阈值候选2条由本次证据推导：原运行确认52向量完整映射、全部冻结chunk可水合；唯一真实水合查询于21:49:40返回2行。
报告明确标记该计数为推导值。未保留的3条低分 raw hit 详情不补造；完整 DeepSeek HTTP envelope 也仅保留历史 SHA，
无法重新计算 envelope SHA。评价 content 则已经通过原 SHA 精确恢复并完成完整 JSON 比对。

新增工具：`RagE2eConsistency` 是表示无关的完整 JSON 检查；`RagE2eReadOnlySnapshot` 保存只读快照；
`RagE2eReadOnlyVerifier` 复核业务证据；两个新增 OfflineTest 覆盖内容篡改、字段缺失、数组重排、重复键、
尾随JSON、数字类型表示、原始SHA不符、Prompt/Trace/评分/HTTP/ownership不符；`verify-existing.ps1` 是安全只读入口。
既有 OfflineTest 仅新增可选输出路径，使历史测试报告保持原样。

以下章节是历史准备记录，不能据此重跑第三次或删除锁。

## 闪退后第三次尝试的恢复入口

仅准备，未执行第三次 E2E。恢复后的只读准入已通过：测试 MySQL 四表完整 SHA 与冻结合同一致，191行；
user 360 是唯一测试用户（USER/ENABLED），其他八张业务表均为空。测试 writer 的 user.id UPDATE 列权限已实际存在，
不是只修改了 SQL。测试集合仍为 469534467320352840，已加载、Bounded、52向量、完整 SHA 与 MySQL 映射通过。
本轮没有数据库/Milvus 写入、业务 HTTP、模型调用或真实尝试预约。

[recovery-plan.json](recovery-plan.json) 固定 `rag-e2e-attempt-003-20261012`，绑定旧锁、第二次原报告及首次诊断记录的 SHA。
首次原 report 曾被旧入口覆盖，现有首次诊断档案及原报告历史 SHA 记录全部保留；不伪造原报告。
旧 `EXECUTION_CONSUMED.lock` 和 `run-once/report.*` 保留，旧 ExecuteOnce 入口继续拒绝执行。

新入口在任何服务访问前原子预约独立目录 `target/rag-e2e/attempts/rag-e2e-attempt-003-20261012`，写入 ATTEMPT_RESERVED.lock；
首次业务写入前再写本次 EXECUTION_CONSUMED.lock。该 ID 被预约后，无论成功、失败或闪退，不能再次运行或覆盖报告。
不同 ID 也被拒绝。以后若需要第四次尝试，须有新的人类决定、重新审查计划和 seal；不得删除任意旧锁。

恢复只访问测试库和测试集合，缺少或改变的语料/向量直接停止，不调用 copyMissingCorpus/prepareVectors，
不读取源库/源集合，不自动补复制、建集合或加载集合。正常生产初始化器在 E2E Milvus 观察代理的禁止写入约束下验证已有 Schema。
用户360及其所有历史记录保留，恢复不需要其密码。第三次正常 POST 注册唯一的新测试 USER，使用 SecureRandom 生成
36个 ASCII 字符的随机密码，仅保存在运行内存中（业务服务仍正常持久化 BCrypt）；注册后用同一密码正式登录取得 JWT。
确认登录账号一致、返回 USER、新 userId 为正且不是360，并检查新用户没有既有会话，再一次 POST 创建 EASY 五题会话、
一次提交第一题受控合成答案。没有破解、密码重置、哈希修改或直接制造 JWT。
最多1次 Query Embedding和1次DeepSeek，沿用原有外部请求预算、Java业务状态机和只读结果核验。

PowerShell退出后，只需在新 PowerShell 7 窗口、项目根目录安全恢复 writer 的现有密码（不需要用户360、源账号或管理员密码；不执行GRANT）：

```powershell
. .\tools\rag-e2e\restore-recovery-environment.ps1
```

该脚本仅有一次安全密码输入，只将 writer 密码存入当前进程；两个模型 Key 和可选 Milvus token 沿用既有用户环境。
随后取得新的明确执行授权；以下是未来第三次的唯一命令，当前未执行，命令字符串本身不代表已有授权：

```powershell
pwsh -NoProfile -File .\tools\rag-e2e\run.ps1 -Mode ExecuteRecoveryOnce -RunId rag-e2e-attempt-003-20261012 -Authorization RAG_E2E_RECOVERY_REGISTER_NEW_USER_REUSE_ONE_SESSION_ONE_ANSWER
```

离线定向验证：104项，0失败/0错误/0跳过/0真实调用/0真实写入；新增流程测试运行实际 AuthService、BCrypt、JWT，
以 fake HTTP/JDBC 覆盖注册→登录→会话创建→一次答案→只读详情，包括前置失败、历史用户/ADMIN响应拒绝、答案500和超时不重试，
并检查密码/JWT/BCrypt不进入报告。注册所需 user SELECT/INSERT 和锁定所需 UPDATE(id) 已只读确认实际授予；
没有新增 GRANT 或实际注册。本轮未重跑P0/P1或真实 E2E，先前恢复准入的真实服务只读证据保留。
本段是当前恢复状态，后续章节保留历史准备和第二次失败记录，不应据此重复建设环境。

## 2026-10-12 第二次执行后的 HTTP 修复

本次历史结果仍为 FAIL，不重新执行 ExecuteOnce。失败点是 `POST /api/interviews`，注册及登录已经成功。
测试库只读核对确认留下 `user.id=360`（USER/ENABLED），未留下会话、面试题或答案。
MySQL performance_schema 在 16:33:40 记录 `SELECT user ... FOR UPDATE` 失败，writer 账号在相同时刻记录
1142 / ER_TABLEACCESS_DENIED_ERROR。旧权限仅给 user 表 SELECT、INSERT，缺少锁定读取要求的权限。
[MySQL 8.0 锁定读取契约](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html)要求 FOR UPDATE
除 SELECT 外还需 UPDATE、DELETE 或 LOCK TABLES 之一；仅增补测试 user.id 列的 UPDATE，保留业务独占行锁。
新增 capability 是 `UPDATE(id)@user`，writer 共 28 项；整表 user UPDATE、password/role/status 列权限仍被拒绝。
provision SQL 只描述新账号的完整权限，不能直接重跑它来修复已有账号。已有账号仅需这一条增量：

```sql
GRANT UPDATE (`id`) ON `ai_interview_test`.`user` TO 'rag_e2e_writer'@'127.0.0.1';
```

所有当前业务接口契约仍是 HTTP 200 + 整数 Result.code=200，未扩大为任意 2xx。
旧 Runner 没有保存实际状态码/响应且 logging.root=OFF，因此旧 HTTP 状态、响应正文及业务 Java 异常栈无法恢复。
由 MySQL 历史错误及 GlobalExceptionHandler 源码推定为 HTTP 500 / `系统异常，请稍后重试`，这不是现场 HTTP 证据。
新 Runner 将每次业务 HTTP 的方法、路径、实际/预期状态、响应 SHA 和安全 Result 摘要写入报告，剔除 data 和未审查的消息；
E2E-only observer 记录实际 MVC 异常处理器收到的异常类型、栈及 SQL 错误码，不导出异常消息/SQL/凭据。
HTTP 请求开始前立即更新 currentOperation，避免沿用已结束的 Milvus.query；注册、登录及会话标识逐步记录。

已有 191 SQL 行与 52 测试向量保留并优先复用，集合 ID 为 469534467320352840。历史 report.json、report.md
和 EXECUTION_CONSUMED.lock 必须保留；本次修复及 Offline 模式不触碰它们。新的 review seal 仅表示离线审查通过，
不会撤销执行锁，也不构成一次新执行的授权。锁仍阻止 ExecuteOnce，后续执行安排须另有明确人类决定。
本段之后的准备交付记录是早前状态，不能据此认定本轮未执行或要求重做 MySQL/Milvus 建设。

2026-10-12，当前分支 `codex/rag-p1-cosine-candidates-20261012`。

**E2E_PREP = BLOCKED。离线执行资产已准备并通过定向测试；真实环境未验收。**
本轮没有访问真实 MySQL、Milvus、Embedding 或 Judge，没有提交或推送代码。全部新增文件仅在本目录；已有生产、测试配置及 Cursor 工作文件未修改。

## 最短数据路径

唯一业务数据源是 `ai_interview_test`，知识水合与业务持久化都用它。Milvus 固定 `default/knowledge_chunk_vectors_test`。保留现有 `EnvironmentIsolationInitializer`，使用 `test-real,rag-p1`；没有双数据源路由或隔离门禁例外。

现有测试资源是否完整尚未在线确认。未来获授权后，入口先审查真实身份和账号权限，再只读 `ai_interview` 中四张冻结语料表及 `knowledge_chunk_vectors_local`：

- MySQL server UUID 固定 `6ccac806-1bb1-11f1-b45f-107c61ba59a5`；源集合 ID 固定 `468137482006301501`。
- 冻结 MySQL：question 21、question_scoring_point 97、knowledge_document 21、knowledge_chunk 52；完整行 SHA 必须与合同相同。
- 冻结向量：52 条，完整向量 SHA、document_id、chunk_index、embedding_version 必须匹配。只读 scalar query；不对源集合做 Search/load/insert/delete。
- 优先复用测试副本。测试语料和向量必须是源数据的完全相同子集；发现同 ID 不同值或额外语料/向量即停止。只 INSERT 缺失项，最多 191 SQL 行和 52 向量，不覆盖、不重建、不删除。保持全部关联 ID 和 vector_id；这不是重新 Embedding 语料。
- MySQL 四表补齐在一个事务中；向量插入最多一次。测试集合不存在时才用现有生产初始化器创建测试集合及 AUTOINDEX/COSINE，必要时只加载测试集合。已有集合 Schema 不符直接停止。
- 完整副本必须在测试集合的 Bounded scalar 视图中可见，最多 10 次条件查询。业务 Search 不指定一致性，核实集合默认确为 Bounded。先前源库的 Strong 结果不作为本次业务证明。
- 21 题已有 EASY 五类计划满足正常会话创建。使用业务 API 创建五题会话，只提交第一个 MAIN 的一份受控合成答案（该题已冻结的 reference answer），不覆盖源问题或主问题选择逻辑。

若测试 Schema 根本不存在，管理员未来另行执行 [schema-test-only.sql](schema-test-only.sql)。它仅摘取既有 Phase 0.5 测试库 DDL，不含 evaluation、不改现有表；已存在但结构不符时不得直接执行，也没有自动 ALTER。通常不需要这一步。

## 受限账号与真实写入边界

[provision-accounts.sql](provision-accounts.sql) 是未执行的精确授权方案。密码标记须在仓库外私有副本中替换；文件首行执行保护会主动报错，管理员只有在获授权后才能移除。已有 `r08_source_ro` 可复用，仍须通过完整自授权审查。

| 账号 | 权限 |
|---|---|
| rag_e2e_source_ro | ai_interview 四张语料表 SELECT；可复用已有仅 SELECT 的源账号 |
| rag_e2e_writer | ai_interview_test 13 张业务表 SELECT；下列 11 张表 INSERT；仅三张工作流表 UPDATE |

测试 INSERT 表：`question`、`question_scoring_point`、`knowledge_document`、`knowledge_chunk`（仅补齐副本），`user`、`interview_session`、`interview_question`、`interview_answer`、`answer_evaluation`、`rag_retrieval_batch`、`rag_hit_log`。

测试 UPDATE 表：`interview_session`、`interview_question`、`interview_answer`；另外仅允许 `user.id` 列 UPDATE 以通过原有锁定读取权限检查。

不会写 `interview_report` 或 `user_weakness`。账号禁止 root、角色、全局/Schema 级宽权限、GRANT OPTION、DELETE、DDL、源库写权限。真实 grants 从 `CURRENT_USER()` 取得并在内存核验，不导出原 grants。

源账号只需看到四张语料表。13 张表的结构、外键和业务写入检查只发生在测试库的 `rag_e2e_writer` 连接上。已有 `r08_source_ro` 若仍只有源库 SELECT，也可以继续通过授权审查。

执行锁在只读准入成功后、第一次测试库写入之前创建。准入失败可以在修正账号或结构后再次明确执行。锁一旦写上，后续向量或模型失败都不会自动清理、第二次 insert 或整流程重跑；已完成的测试副本保留，新一次操作须有新的明确人类决定。

## 配置、HTTP 与预算

入口创建一个独立且清空后重建环境的子进程。只导入明确列出的 E2E 凭据与两个模型 Key，不继承 DB_USERNAME/PASSWORD、TEST_DB 回退、SPRING_*、Jev、RUN_REAL_*、JAVA_TOOL_OPTIONS 或其他模型覆盖项，不读取 local 配置。最高优先级运行属性固定：

`test-real,rag-p1`；`127.0.0.1:3306/ai_interview_test`；`127.0.0.1:19530/default/knowledge_chunk_vectors_test`；`qwen3.7-text-embedding`，1024 维，`qwen3.7-text-embedding-1024-dense-v1`；北京端点 `dashscope.aliyuncs.com`；Judge=`deepseek`，官方模型名 `deepseek-flash`；TopK=5；原始 Float32 COSINE ≥ 0.70。

合法接口序列：

1. `POST /api/auth/register`（唯一合成 USER，随机密码，仅存内存及业务 BCrypt）；`POST /api/auth/login` 取得 Bearer JWT。
2. `POST /api/interviews {difficulty:EASY}` 创建真实五题计划。
3. **一次** `POST /api/interviews/{sessionId}/answers {interviewQuestionId,answerContent,requestId}`。它同步运行评价与工作流；发送前落盘意图。
4. `GET /api/interviews/{sessionId}` 查询详情，最多 30 次，等待该答案终态 `EVALUATED` 或 `FAILED`。GET 不会重新认领或重试评价。没有独立 evaluation-status endpoint。

“终态”指这一份答案处理完成。若 Java 合法决策为 FOLLOW_UP，评价阶段仍为 INITIAL，报告按实际记录，绝不声称综合 FINAL。入口不提交第二份答案。成功必须有原 POST 200、GET 评价与 SQL 相符；HTTP 未成功或超时不会允许再次提交。

| 模型 HTTP | 成功预期 | 可转发最大值 |
|---|---:|---:|
| 百炼 `/compatible-mode/v1/embeddings`，单 Query | 1 | 1 |
| DeepSeek `/chat/completions`，JSON、thinking disabled、max_tokens=4096 | 1 | 1 |
| Jev、fallback、报告、追问答案、语料 Embedding | 0 | 0 |

生产 Embedding RetryTemplate 为一次尝试，DeepSeek RestClient 无业务重试。追问候选和反馈包含在同一次 DeepSeek 输出中，不另生成。两类模型通过本机 HTTP 计数代理；预算在上游发送前消耗，失败不退还，后续请求禁止转发。关闭 HTTP 重定向与 JDK POST/连接自动重试设置；模型路径、模型名、维度和输出上限再次校验。

额外网络：MySQL 身份/grants/Schema/只读源语料/副本写入及读回；Hikari 连接与业务 SQL、认证每次查询当前 USER；Milvus describe/index/load-state/source scalar/target scalar（目标观察最多 10 次）、可选测试 collection/create/index/load、一次测试向量 insert、一次业务 Search。SDK 可能为 Search 自动读取 Collection metadata；SDK retryConfig 固定 maxRetryTimes=1。连接握手、gRPC 重连不宣称为“没有网络请求”。不会因业务失败重试模型或答案。

价格已按官方标价固定在代码中，不再读取费率环境变量。当前 Embedding 地域是北京 `dashscope.aliyuncs.com`，采用 ¥0.50/百万输入 Token；新加坡 ¥0.525 只保留给国际域名，本次不会调用。Judge 使用官方 `deepseek-flash`，按高峰缓存未命中输入 ¥2/百万 Token、输出 ¥8/百万 Token 估算。

保守估算上界：

`(8320 × 0.50 + 66048 × 2 + 4096 × 8) / 1,000,000 = 0.169024 元`。

这个数字把字节上限当成 Token 上限，只用于超过 1 元时停止启动。它不是账户扣费硬上限。硬边界是最多 1 次 Embedding HTTP 和 1 次 DeepSeek HTTP，以及供应商账户自己的额度。脚本不查询账户余额。结果记录实际 usage 数字（若提供）。

## 唯一入口及阻断项

PowerShell 7、Java 21 和已缓存 Maven 依赖。本轮离线命令已执行：

```powershell
pwsh -NoProfile -File .\tools\rag-e2e\run.ps1 -Mode Offline
```

未来一次明确授权（含必要测试副本/测试 Milvus 准备以及一次答案提交）后，唯一真实入口：

```powershell
pwsh -NoProfile -File .\tools\rag-e2e\run.ps1 -Mode ExecuteOnce -Authorization RAG_E2E_TEST_COPY_AND_ONE_ANSWER
```

命令中的确认字符串不是已有授权；本轮没有执行该命令。首次真实执行前还需要：

- 配置 `E2E_DB_USERNAME/PASSWORD`（非 root 测试账号）、`E2E_SOURCE_DB_USERNAME/PASSWORD`（只读源账号）。当前缺失。无 TEST_DB 或 root 回退。费率已固定，不必再设置。
- 两个模型 Key 在用户环境中已存在；未验证有效性。本地 Milvus token 为空或占位，默认不传 token。
- 在线确认受限 grants、13 表结构、测试副本和集合身份/Bounded；这是后续获授权执行的首个 gate。现在不把未知测试资源写成已就绪。
- 若 Milvus 配置了鉴权，提供本次允许范围的 `E2E_MILVUS_TOKEN`；本轮不验证服务器鉴权状态。
- 实际副本不符/额外数据/模型拒绝/无 ≥0.70 水合证据即 FAIL，停止，不自动修改共享测试数据或再提交。

输出：`target/rag-e2e/prep-status.json`、`offline-tests.json`、`review-seal.json`；未来真实运行输出 `target/rag-e2e/run-once/report.json` 与 `report.md`。报告含模型请求/响应 SHA、实际 usage、原始命中数量/阈值后数量/SQL 水合数量/Prompt 知识 ID+标题+内容 SHA、模型 JSON SHA、Java 分数/决策、数据库 ID 和 HTTP 比对。答案原文、JWT、密码、Key 和模型反馈正文不导出；业务数据库原本约定的 answer/raw_result 正常持久化。

## 文件与离线验证

- `run.ps1`：唯一启动入口、干净子进程、离线编译、来源 SHA 复核、真实执行确认门禁。
- `RagE2eRunner.java`：身份/权限/副本准备、真实 Spring Boot 和合法 HTTP、只读轮询、SQL/模型/HTTP 证据核验。
- `RagE2eAudit.java`：仅 E2E 注册的观察代理，委托原业务实现，记录实际链路并禁止运行期向量写入。
- `RagE2eModelGate.java`：最多两个真实模型请求的转发与 SHA/usage 记录，不替代模型输出。
- `RagE2ePolicy.java`：可离线验证的账号/目标/请求/预算保护。
- `RagE2eOfflineTest.java`：新增保护措施的定向测试，fake transport 不开 socket、不连接数据库、不调用 Milvus/模型。
- `contract.json`：只复制冻结证据的 Schema、ID、计数和 SHA；未包含向量值、答案或凭据。
- 两份 SQL 与 `config/application-test-real.yaml`：精确的未来管理员准备及独立运行配置。

最后定向运行：**50 tests，0 failures，0 errors，0 skipped**；沿用已有 runtime classpath，独立 Java 21 编译成功。未重跑全量回归、16 题构建、P0 排障或阈值回放。这些结果是离线保护措施验证，不是真实 E2E 成功证据。
