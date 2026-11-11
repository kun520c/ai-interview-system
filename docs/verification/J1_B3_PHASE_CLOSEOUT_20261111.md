# C0：J1～B3 最终审查、验收与 Git 收口

执行日期：2026-11-11（Asia/Shanghai）。仓库根目录：`/Users/kun/Developer/projects/ai-interview-system`。

**结论：J1～B3 在本轮审查范围内具备提交条件。** 累计代码审查、553 项聚焦回归、1589 项完整普通回归、工具离线检查和打包均通过，未发现未解决的 P0/P1 回归。发现一项既有 P2 日志脱敏缺口，列入 B4-A，不在 C0 修改。提交必须采用 E 节的完整计划，明确保留本次通过所依赖的原有密码并发测试 SQL 修正；只提交四批代码而排除该修正，不能宣称得到与本轮相同的验证基线。

本轮仅新增本报告和源码学习指南，没有修改生产代码、测试源码、配置或已有报告，没有暂存、commit、push、merge、reset、Git/Maven clean 或 stash。临时脚本、编译探针和原始日志存于 `/tmp/ai-c0-20261111/`，构建产物存于 ignored `target/`。

## A. 当前基线与变更保全

- 当前分支：`main`；当前 HEAD：`afbd1c5ab010f3a0a296e15e25c016f81eb2b74f`，与指定 J1 前参考提交完全相同。
- `git log -8 --oneline --decorate` 最近提交是 PR #11 的 Java 工具可读性合并；本机 `origin/main`、`origin/HEAD` 同指 HEAD。本轮没有 fetch / ls-remote，不能据此断言远端实时状态。
- 入场工作区：**75 个路径**，其中 42 个已跟踪修改（包含 `mvnw` mode）、23 个删除、10 个未跟踪；`git diff --stat` 为 **65 files changed, 786 insertions(+), 4238 deletions(-)**，不含未跟踪文件。暂存区为空。
- 交付工作区：原 75 个路径保持字节和 mode 不变，加 C0 两份报告，共 **77 个路径**（65 个已跟踪变更、12 个未跟踪）；没有把原有变更重新归为 C0 开发。
- J1 前用户修改的三项：`mvnw` 100644 → 100755；`ConcurrentPasswordChangeIntegrationTest.java` 两处锁诊断 SQL 列名修正；未跟踪 `compose.mac.local.yaml`。现状与历史初始哈希一致。
- 归属证据并非只凭报告：逐项对照 B1/B2/B3 入场的 `initial-hashes.json`、B2 保留的 B1 结束版 Workflow 两个文件及当前 diff；两个 B1 结束版也与 B2 入场 SHA-256 相符。除明确的 B1/B2 重叠文件外，早期已有文件没有后续不可解释变化。**其他未归属变更：0。**
- 初始累计 diff SHA-256：`e84d8e707d6603e06e3c0a9e822e9bc4e4819ea2f9a3012ddccc1203bda13e1f`。含未跟踪源码及 mode 的初始 75 路径清单 SHA-256：`35697c3ce3f33bfdcd63871635b996182bdff718896a279968c3e8be67ac16e1`。证据在 `/tmp/ai-c0-20261111/initial.diff`、`initial-hashes.json`、`preservation-check.json`。

本报告中的相对路径均以以上仓库根目录为准。C0 是新的**已验证工作区基线**，目前不是新 Git 提交。

## B. 四批次核验

### J1：移除 Jev，保留单模型业务与恢复

**原问题与根因。** 旧 `JudgeConfiguration` 根据 `ai.judge.provider` 装配 Jev 组合实现，后者还负责 DeepSeek 反馈与跨模型 fallback；生产配置、基线脚本和 E2E 工具都存在相关入口。清理必须覆盖这些入口，同时保护 provider 无关的业务恢复。

**核实方案。** `JudgeConfiguration.java:12` 仅返回 `DeepSeekJudgeProvider`；删除 `JudgeProperties`、8 个 `judge/jev/` 实现、10 个专属测试和4个旧网络工具源码。生产 YAML 删除旧 selector/专属配置；活跃工具拒绝 Jev 路由、凭据与调用。三个退役 Python 入口及一个 PowerShell 启动器立即拒绝，历史报告、fixture 和只读 usage 字段保留。

**调用链。** `InterviewService.submitAnswer → InterviewWorkflowService.evaluateAnswer → EvaluationOrchestrationService.evaluate → JudgeProvider → DeepSeekEvaluationClient → LlmEvaluationValidator → EvaluationScoreCalculator → FollowUpPolicy → Workflow 短事务`。报告仍经 `DeepSeekInterviewReportGenerator`。RAG、Spring AI Embedding 和 Milvus 均未改；打包产物保留相应类。

**覆盖迁移。** Jev 专属的协议、映射、代理、预算及 fallback 测试随实现退役；公共鉴权、评分、追问、持久化、RAG、事务与并发测试保留。配置测试现在为7项；完整离线业务类85项，五维非法值覆盖扩大，新增 HTTP 故障/显式重试、历史评价重放等断言。历史 J1 数量变化 `1790 - 246 - 27 + 9 = 1526` 是历史交叉核对，不作为本轮运行结果。

**新证据。** 聚焦与全量均执行现行配置7、DeepSeek provider 1、Orchestration 20、FullInterviewBusinessOffline 85等相关用例；工具37项通过。另以临时 Java 探针执行两项真实 MySQL 交叉场景：将 B3 自建 MAIN/FOLLOW_UP fixture 的模型与 Prompt 改为历史 Jev，raw_result 改成不符合 DeepSeek DTO 的 opaque envelope；新报告分别为70.00/75.00，重复生成不重复更新 Weakness，评价整行保持不变。

**未验证。** 没有真实模型调用、费用/效果实验或 Windows PowerShell 执行。历史数据兼容为合成持久 fixture 证据，没有扫描真实业务库。完整文件清单在 E 节 J1 表。

### B1：并发完成后的幂等重放

**原问题与根因。** A 已提交答案后暂停，B 完成同请求评价并推进 Session；A 恢复后读到已完成答案或旧 Answer + 新 Session 指针，旧 Workflow 将正常完成当成当前题不一致，误报500。数据库 CAS 单胜者正确，并不保证应用层读取顺序正确。

**核实方案。** `InterviewWorkflowService.java:78` 首次 EVALUATED 直接发出原有重放信号；`:127` 当前题不一致时重读指定 Answer，仅 EVALUATED 可重放。未重新 claim、调用模型或写业务状态。`InterviewService` 是生产唯一调用者，已校验用户、Session/Question 归属、requestId与完整答案；它忽略 Workflow 返回值，重新读取持久评价和最新 Session 构建响应。

**过度放行检查。** `InterviewService.java:534` 对缺评价、错误 answerId、无效阶段/决策/level仍报错；新代码不是 catch-all 成功。数据库问题与答案绑定不提供业务修改入口，唯一约束未改。重读缺行/错误ID单测仍拒绝；完成标记却缺评价的真实 API 用例仍是500。直接调用内部 Workflow 得到 null 不是对外成功凭证。

**新证据。** 18项真实 MySQL + MockMvc 并发测试覆盖两种暂停点×五条转移，fresh409、不同payload/requestId、其他用户404、无token401、最新完成Session重放、缺评价拒绝；断言评价/trace数量、Session整行/version/完成计数及模型替身调用次数。Workflow41项（包含B2增量）、Service31项和既有并发/事务用例通过。无外围测试事务，暂停使用锁存器，未使用 sleep 制造随机竞争。

**文件。** 两个 Workflow 文件的 B1 hunks、`InterviewConcurrentReplayIntegrationTest.java`、B1报告。未验证真实模型网络交错；不承诺外部调用 exactly-once。

### B2：成功领取后的异常恢复

**原问题与根因。** claim 的短事务已提交，`findNextPendingMainQuestion` 却在 try 外；查询失败留下 fresh EVALUATING，必须等 stale 后再次请求才可接管。

**核实方案。** `InterviewWorkflowService.java:149` 的 try 现在覆盖下一题查询及之后的评价/推进。claim/retry/reclaim 及所有 REPLAY 返回仍在 try 外，只有 OWNED 才进入失败回写。已有 `markEvaluationFailed` 把本次 attemptId 交给事务服务，SQL 要求 `id + EVALUATING + attempt_id`；次要回写错误进入原异常 suppressed，原 SQLException cause及异常对象保留。

**新证据。** Workflow 单测覆盖三种领取来源、领取自身异常不回写、查询失败原异常和 suppressed。3项真实 MySQL用例验证 MAIN/FOLLOW_UP 即时 FAILED、同请求重试完成及旧查询失败无法覆盖新 attempt。独立连接观察领取和失败提交；旧任务 UPDATE 0行。B1 的18项重放继续通过。

**文件。** 两个 Workflow 文件的 B2 hunks、`InterviewPostClaimFailureIntegrationTest.java`、B2报告。没有新增事务、循环重试、模型调用或Mapper变更。

**边界。** 数据库不可用导致FAILED回写也失败、进程崩溃、claim提交结果不确定仍可能留下EVALUATING，依赖既有请求驱动stale恢复。Mock二次回写异常与真实0行fencing不等于已验证真实网络断连后的恢复。

### B3：报告及 Weakness 的 FINAL 一致性

**原问题与根因。** 旧SQL按FINAL优先、INITIAL兜底，且ROW_NUMBER隐藏重复MAIN；服务仅按数量校验，没有证明评价对应本场最终答案。非READY已有报告恢复还绕过来源检查。

**核实方案。** Mapper只提供FINAL候选，保留重复并按MAIN plan_order排序。`InterviewReportService.java:248` 检查phase、MAIN唯一集合/计划连续性、问题ANSWERED、答案EVALUATED、最终answerId及追问父MAIN INITIAL链。追问FINAL关联父MAIN快照，其自身知识点不混入统计。非READY恢复在`:223`复用同一校验；READY读取直接返回历史报告。`UserWeaknessService.java:87`在所有写入前拒绝非FINAL。

**原子性。** `InterviewReportTransactionService.java:28`继续将report插入、Weakness与READY CAS放在同一短事务；生成器在事务外。Weakness使用同一份已校验FINAL列表、按category/knowledgePoint聚合；没有第二套选分SQL。已有报告恢复只恢复READY，不重复Weakness，这是既有恢复契约，不是历史分数修复。

**新证据。** 新真实集成24项：正常MAIN/追问、17类异常数据、缺FINAL重试、历史READY、FAILED/stale已有报告恢复、真实report INSERT失败。Mapper6、ReportService24、ReportTransaction集成9、Weakness单测19与Mapper8等通过；既有失败回滚和旧version隔离断言保留。C0的2项历史Jev交叉探针另计，不混入1589。

**文件。** E节B3完整11项。没有Schema、DTO或HTTP API变化；Mapper签名未变，查询语义收紧属于本批明确目标。未扫描/纠正已READY历史错误分数；未验证绕过应用并发直接改库；额外批量读取问题/答案及每个追问一次INITIAL查询，规模受当前每场5/7/9题限制，未做性能基准。

## C. 累计审查结论与问题

| 交叉边界 | 结论与证据 |
| --- | --- |
| B1 REPLAY 与 B2 FAILED | 三种完成重放均在OWNED try之外；正常重放不回写FAILED，无资格也不回写。B1真实交错、B2单测与集成共同验证。 |
| B2异常范围与B1重放 | 仅移动下一题查询；没有将claim前校验或REPLAY放进catch。B1/B2重叠文件可由已校验历史快照精确拆分。 |
| J1历史评价与B3 | SQL/Java使用业务列，不按llm_model筛选，不反序列化raw_result为DeepSeek DTO。历史MAIN/追问到报告/Weakness的C0补验通过。 |
| FOLLOW_UP、FINISH与FINAL | FollowUpPolicy仍为MAIN INITIAL+FOLLOW_UP、无追问MAIN FINAL、追问FINAL。完成事务与B3来源规则一致，五条转移及完整离线链通过。 |
| Session / Answer / Evaluation一致性 | claim后模型执行在事务外；完成时锁Answer核验attempt，再同事务落trace/evaluation、EVALUATED和Session CAS。B1只读分支不改变该原子性。 |
| 异常传播 | Orchestration失败审计与Workflow状态失败处理职责不同，均保留primary/suppressed；没有新泛化catch或重复评价循环。 |
| SQL和接口 | B3是唯一生产Mapper SQL变更；使用参数绑定。Schema、POM、公共DTO/VO、Controller、安全链无diff；没有新增事务隔离级别或长事务。 |
| RAG和共享能力 | Embedding/Milvus业务代码无diff，普通RAG回归、公开离线gate及JAR类检查通过；不宣称真实外部服务通过。 |

**P0：未发现。P1：未发现。本轮新增实现回归：未发现。** 这表示已审查路径和本轮验证范围内的结论，不表示所有网络/生产场景无风险。

### C0-P2-001：既有 DeepSeek 错误响应可进入服务端日志

- 源码证据：`src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java:168` 将RestClientException保留为cause；`src/main/java/com/kun/aiinterview/common/exception/GlobalExceptionHandler.java:130`用 `log.error("外部服务异常", e)`打印完整异常链。这两个生产文件相对HEAD均无修改，属于既有技术债务。
- 实际复现：本轮 `FullInterviewBusinessOfflineTest.singleHttpFailureAllowsOnlyExplicitSamePayloadRecovery` 的429/500/503合成响应体含 `PRIVATE_MODEL_OUTPUT`。HTTP响应为脱敏502，但 `/tmp/ai-c0-20261111/full-regression.log:2217` 等位置的 `Caused by: HttpClientErrorException...` 仍含该标记。报告故障用例也可复现。没有真实密钥或候选人数据用于本实验，不能据此声称发生过真实凭据泄漏。
- 风险：上游错误体可能回显输入或其他敏感内容，进入应用日志。当前API脱敏断言不足以证明日志脱敏。
- 最小后续方案：B4-A先设计不含response body/Authorization的错误分类与可记录元数据，评估安全cause处理；添加日志捕获断言。保留状态码、超时类别与追踪标识，避免为脱敏丢掉必要诊断。C0不修改该既有路径。

### 非阻断审查备注

- `tools/rag-p4-2/EXECUTION.md` 除末尾新增J1退役说明外，将原53行CRLF归一为LF；`git diff --ignore-space-at-eol`证实无历史事实改写。归属J1，已记录，无需在只读C0再次改字节；以后可在明确授权的提交整理中决定是否保留该格式变化。
- `AGENTS.md`含早期“INITIAL兜底”“trace先单独落库”等历史阶段描述，与当前源码不完全一致。当前规则以顶层架构要求和本次实际源码为准；不覆盖历史记录，不据旧阶段数字判断本次结果。
- 重放返回null是既有内部约定，生产调用方不消费Workflow结果；目前不需要新增抽象。5/7/9题规模下也不需要为B3的来源核验引入缓存、Redis、MQ或通用批处理框架。
- READY历史报告继续可读是明确兼容决策，不代表其旧分数已通过新规则。报告恢复信任既有报告内容；C0不增加历史纠错/重新生成能力。

## D. 本轮最终回归

### 环境与隔离

实测macOS26.7.1、ARM64，Temurin21.0.13，Maven Wrapper3.9.16。未沿用环境说明中的版本假设。普通沙箱第一次Docker只读检查退出1（socket权限），通过允许的执行环境后可用；不是测试或代码失败。

新建且只使用 `ai-interview-c0-20261111-mysql`，MySQL8.0.45、`127.0.0.1:13322`、tmpfs数据目录、全局时区`+08:00`。仅在临时DDL副本替换建库/USE名称，没有改仓库SQL。实际数据库只有 `ai_interview_test` 与MySQL系统库。

```text
jdbc:mysql://127.0.0.1:13322/ai_interview_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
```

`/tmp/ai-c0-20261111/run_maven.py`为每个Maven子进程清除RUN_REAL_*、SPRING_*、MILVUS_*、DEEPSEEK_*、JEV_*、AI_JUDGE_*、旧DB_*/TEST_DB_*和JVM/Maven覆盖，仅注入本轮随机临时数据库连接。所有真实服务opt-in及MILVUS_ENABLED均unset；test配置和EnvironmentIsolationInitializer保留。部分业务测试显式装配业务Bean，但外部客户端有Mockito替身，不代表真实Milvus或DeepSeek启用。

Maven执行器每轮先归档旧Surefire，再复制本轮XML。全量131个测试类的计数来自 `full-regression-reports/`；公开离线脚本后续写入的target报告没有混入完整回归计数。没有读取正式`.env`内容；`application-local.yaml`不存在；没有连接正式或evaluation数据库。

### 命令及结果

T/F/E/S依次为测试总数/失败/错误/跳过，数量均为本轮实测，不要求等于历史阈值。聚焦与完整回归重叠，不能相加称独立测试总量。

| 执行项 | 实际命令 | T/F/E/S | 退出码 |
| --- | --- | --- | --- |
| 主/测试代码编译 | `python3 /tmp/ai-c0-20261111/run_maven.py compile -DskipTests test-compile`，底层 `sh mvnw -B -ntp -DskipTests test-compile` | 未执行测试；不适用 | 0 |
| 聚焦回归F | `python3 /tmp/ai-c0-20261111/run_maven.py focused -Dtest=<下方完整列表> test` | 553/0/0/0 | 0 |
| 完整普通回归 | `python3 /tmp/ai-c0-20261111/run_maven.py full-regression test`，底层 `sh mvnw -B -ntp test` | 1589/0/0/19 | 0 |
| J1工具gate | `python3 -B tools/check_deepseek_only.py` | 37/0/0/0；另21个Java源编译、9项静态检查 | 0 |
| 公开离线gate | `python3 -B tools/run_public_offline.py` | Java28/0/0/0；Python9/0/0/0；候选证据Java54/0/0/0 | 0 |
| 历史Jev×B3补验 | `python3 /tmp/ai-c0-20261111/run_cross.py` | 2个手工Java断言场景，失败/错误/跳过0；不是JUnit计数 | 0；内部javac和java也均0 |
| 打包 | `python3 /tmp/ai-c0-20261111/run_maven.py package -DskipTests package`，底层 `sh mvnw -B -ntp -DskipTests package` | 未执行测试；不适用 | 0 |
| JAR核验首次 | 内联Python zipfile断言，错误地查找 `knowledge/embedding/SpringAiEmbeddingClient.class` | 不适用 | 1（检查脚本路径错误） |
| JAR核验修正 | `python3 /tmp/ai-c0-20261111/inspect_package.py`，使用实际 `knowledge/embedding/springai/` 包 | 不适用 | 0 |
| 差异检查 | `git diff --check` | 不适用 | 0 |

首次JAR检查失败没有修改项目；实际class一直存在，只修正临时检查器的包路径。记录见 `initial-package-inspection-note.json`。没有测试失败被隐藏、跳过或弱化。

F的完整底层命令：

```text
sh mvnw -B -ntp -Dtest=JudgeConfigurationTest,DeepSeekJudgeProviderTest,DeepSeekEvaluationClientTest,DeepSeekConfigurationTest,DeepSeekPropertiesTest,LlmEvaluationValidatorTest,EvaluationScoreCalculatorTest,EvaluationStandardTest,FollowUpPolicyTest,EvaluationOrchestrationServiceTest,FullInterviewBusinessOfflineTest,InterviewServiceTest,InterviewWorkflowServiceTest,InterviewWorkflowTransactionServiceTest,InterviewWorkflowTransactionServiceIntegrationTest,InterviewEvaluationClaimConcurrencyIntegrationTest,InterviewConcurrentReplayIntegrationTest,InterviewPostClaimFailureIntegrationTest,InterviewControllerTest,InterviewAnswerMapperTest,InterviewSessionMapperTest,StaleRecoveryCasIntegrationTest,InterviewReportServiceTest,InterviewReportApiServiceTest,InterviewReportTransactionServiceTest,InterviewReportTransactionServiceIntegrationTest,InterviewReportFinalConsistencyIntegrationTest,AnswerEvaluationMapperTest,UserWeaknessServiceTest,UserWeaknessMapperTest,InterviewReportPromptBuilderTest,DeepSeekInterviewReportGeneratorTest,DeepSeekInterviewReportGeneratorConfigurationTest,HttpErrorTaxonomyControllerTest test
```

公开离线Java子命令为 `sh mvnw -o -B -ntp -Dtest=RagP1OfflineBusinessReplayTest,RagP1IndependentQuerySetTest,KnowledgeRetrievalThresholdTest test`；Python为 `python3 -B -m unittest discover -s tools/rag-p1 -p test_replay.py`。其余classpath、javac和Java gate命令由脚本固定，均退出0。

J1工具中 `python3 -B docs/evaluation/scripts/build-reports.py`、`python3 -B docs/evaluation/scripts/check-jev-access.py`、`python3 -B tools/rag-p4-2/execution/build_delta.py` 均按合同退出1并返回RETIRED，属于**预期拒绝**；未联网或覆盖历史证据。实际Python解释器绝对路径、完整javac classpath/21源清单与Java子命令均记录于本轮工具输出的 `commands.json`。补验编译/执行的完整命令在 `/tmp/ai-c0-20261111/cross-commands.json`，临时源码为同目录 `C0HistoricalFinalProbe.java`，仅复用B3 fixture和确定性报告文本生成器。

### 测试类型与已验证边界

| 类或组 | 本轮数量 | 实际边界 |
| --- | --- | --- |
| InterviewWorkflowServiceTest / InterviewServiceTest / EvaluationOrchestrationServiceTest | 41 / 31 / 20 | Java/Mockito，证明调用与校验，不证明DB提交 |
| FullInterviewBusinessOfflineTest | 85 | MockMvc/安全链、真实MyBatis/MySQL、Mock HTTP/外部端口；有外围测试事务，不能单独证明跨请求提交 |
| InterviewConcurrentReplayIntegrationTest | 18 | 真实MySQL/事务与MockMvc，双线程锁存器固定交错；模型、Embedding、向量客户端替身 |
| InterviewPostClaimFailureIntegrationTest | 3 | 真实claim/失败/完成提交，独立连接观察；查询故障和Orchestration替身 |
| InterviewReportFinalConsistencyIntegrationTest | 24 | 真实SQL、Report/Weakness事务与异常fixture，只有报告文本生成器替身 |
| InterviewEvaluationClaimConcurrencyIntegrationTest / StaleRecoveryCasIntegrationTest | 11 / 3 | 真实MySQL领取、接管与fencing |
| InterviewWorkflowTransactionServiceIntegrationTest / InterviewReportTransactionServiceIntegrationTest | 6 / 9 | 真实MySQL事务提交/回滚和并发；部分上游来源为Mock，不能替代B3完整来源测试 |
| AnswerEvaluationMapperTest / InterviewAnswerMapperTest / InterviewSessionMapperTest / UserWeaknessMapperTest | 6 / 6 / 14 / 8 | 真实MyBatis XML、专用MySQL Schema |
| ConcurrentPasswordChangeIntegrationTest | 1 | 真实MySQL行锁；包含J1前用户的诊断SQL修正 |

全量实际跳过19项：KnowledgePersistenceIntegrationTest10、RealEmbeddingSmokeTest1、RealMilvusVectorStoreSmokeTest1、RealKnowledgeDocumentProcessingSmokeTest2、RealKnowledgeDocumentProcessingFailureIntegrationTest2、RealMilvusSchemaValidationSmokeTest2、AiSystemBaselineEvaluationTest1。双门禁没有改动。**真实DeepSeek、Bailian Embedding、Milvus和AI baseline未执行**，不把Mock成功或跳过描述为真实外部服务成功。

其他未执行：浏览器UI E2E、Windows/pwsh启动器、依赖私有历史归档的收费证据回放、真实断网恢复、长时间压测/并发容量测试。没有重新执行或伪造历史收费实验。

### 证据、产物与清理

- Maven日志、各轮 `*-summary.json`、独立XML、完整131类计数及保全记录：`/tmp/ai-c0-20261111/`。关键 `focused.log`、`full-regression.log`、`full-regression-reports/`、`full-class-counts.json`。
- J1工具输出：`/Users/kun/Developer/projects/ai-interview-system/target/deepseek-only-9c96c8260b324292917c7206397689a3/`。
- 公开离线输出：`/Users/kun/Developer/projects/ai-interview-system/target/public-offline-87138a08e69141e0a3001594d168c6db/`。Java数量从该轮日志读取，未把混存的旧Surefire再汇总。
- JAR：`/Users/kun/Developer/projects/ai-interview-system/target/ai-interview-system-0.0.1-SNAPSHOT.jar`，SHA-256 `bd6791b92c41f762bff0e68329a27275dc49289ee936b3a254d2c4eced7695f1`。含DeepSeekJudgeProvider、SpringAiEmbeddingClient、MilvusVectorStoreClient；无judge/jev、JudgeProperties、application-local.yaml、.env或旧有效YAML映射。
- 结束时临时库用户、题库、答案、评价、报告、Weakness均0行；只停止本轮C0容器、移除本轮随机凭据文件，其他容器/数据卷未操作。保存诊断文件未发现本轮临时密码。`cleanup.json`记录容器exited。
- `/tmp`与target是临时证据，不作为Git提交内容；凭据已移除，未来重跑必须重新建立隔离实例，不能直接把旧连接指向正式库。

## E. Git归属与可执行提交计划

### 归属总表

| 分类 | 独占路径 | 共享路径 | 处理 |
| --- | --- | --- | --- |
| J1 | 55 | 0 | 单独提交全部列出的J1路径 |
| B1 | 2 | 2 | 新测试+报告；Workflow两个文件仅取B1 hunks |
| B2 | 2 | 同上2个 | 新测试+报告；Workflow仅取B2相对B1增量 |
| B3 | 11 | 0 | Mapper/Service/配套测试/报告作为完整逻辑单元 |
| J1前用户修改 | 3 | 0 | 单独归属；测试SQL修正建议作为明确授权的前置提交，其他两项保留未提交 |
| 其他未归属 | 0 | 0 | 无需猜测归属 |
| C0本轮 | 2 | 0 | 本报告+学习指南，最后独立文档提交 |

四批共72个不同路径；加3项用户改动为入场75项，加C0为交付77项。下面M为修改、D为删除、A为尚未跟踪的新文件。独占路径都取**整个文件当前差异/新增/删除**，不需要切割其内部hunks。两个共享文件按下一节切割。

### J1完整独占文件清单

| 操作 | 路径 | 目的 / 提交范围 |
| --- | --- | --- |
| M | `README.md` | 当前指南/退役标识，历史事实保留；全部差异 |
| M | `docs/GETTING_STARTED.md` | 当前指南/退役标识，历史事实保留；全部差异 |
| M | `docs/README.md` | 当前指南/退役标识，历史事实保留；全部差异 |
| M | `docs/evaluation/README.md` | 当前指南/退役标识，历史事实保留；全部差异 |
| M | `docs/evaluation/scripts/build-reports.py` | 退役入口，保留历史源码；全部差异 |
| M | `docs/evaluation/scripts/check-jev-access.py` | 退役，无网络调用；全部差异 |
| M | `docs/evaluation/scripts/run-baseline.ps1` | 删除Jev模型/凭据选择；全部差异 |
| M | `src/main/java/com/kun/aiinterview/interview/evaluation/judge/JudgeConfiguration.java` | 唯一DeepSeek装配；全部差异 |
| D | `src/main/java/com/kun/aiinterview/interview/evaluation/judge/JudgeProperties.java` | 删除依赖退役实现的旧实验源码；全部差异 |
| D | `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevClient.java` | 删除Jev专属实现；全部差异 |
| D | `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevEvaluationTrace.java` | 删除Jev专属实现；全部差异 |
| D | `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java` | 删除Jev专属实现；全部差异 |
| D | `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevProperties.java` | 删除Jev专属实现；全部差异 |
| D | `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevRequest.java` | 删除Jev专属实现；全部差异 |
| D | `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevRequestBudget.java` | 删除Jev专属实现；全部差异 |
| D | `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevResponse.java` | 删除Jev专属实现；全部差异 |
| D | `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevStageException.java` | 删除Jev专属实现；全部差异 |
| M | `src/main/resources/application.yaml` | 删除旧Jev有效配置；全部差异 |
| M | `src/test/java/com/kun/aiinterview/evaluation/AiSystemBaselineEvaluationTest.java` | 真实baseline单模型化；保留opt-in；全部差异 |
| M | `src/test/java/com/kun/aiinterview/interview/evaluation/judge/JudgeConfigurationTest.java` | 旧selector不再生效的装配断言；全部差异 |
| D | `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevClientTest.java` | 删除Jev专属测试；全部差异 |
| D | `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevDisplayQuantizationHardeningTest.java` | 删除Jev专属测试；全部差异 |
| D | `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevE06ReliabilityReplayTest.java` | 删除Jev专属测试；全部差异 |
| D | `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevFeedbackPromptHardeningTest.java` | 删除Jev专属测试；全部差异 |
| D | `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevFeedbackReliabilityTest.java` | 删除Jev专属测试；全部差异 |
| D | `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProviderTest.java` | 删除Jev专属测试；全部差异 |
| D | `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevProxyTransportTest.java` | 删除Jev专属测试；全部差异 |
| D | `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevRequestBudgetTest.java` | 删除Jev专属测试；全部差异 |
| D | `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevScoreMappingTest.java` | 删除Jev专属测试；全部差异 |
| D | `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevScorePrecisionCompatibilityTest.java` | 删除Jev专属测试；全部差异 |
| M | `src/test/java/com/kun/aiinterview/interview/evaluation/orchestration/EvaluationOrchestrationServiceTest.java` | 历史opaque结果重用及中立元数据；全部差异 |
| M | `src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java` | DeepSeek故障/重试、五维校验和历史兼容；全部差异 |
| M | `src/test/resources/application-test.yaml` | 删除无效selector；保留隔离配置；全部差异 |
| M | `src/test/resources/evaluation/README.md` | 当前指南/退役标识，历史事实保留；全部差异 |
| M | `tools/README.md` | 当前指南/退役标识，历史事实保留；全部差异 |
| M | `tools/browser-e2e/BrowserE2eServer.java` | 移除Jev路由/选择或调整离线门禁，保留RAG和隔离；全部差异 |
| M | `tools/final-e2e/FinalE2eGate.java` | 移除Jev路由/选择或调整离线门禁，保留RAG和隔离；全部差异 |
| M | `tools/final-e2e/FinalE2eOfflineTest.java` | 移除Jev路由/选择或调整离线门禁，保留RAG和隔离；全部差异 |
| M | `tools/final-e2e/FinalE2eRepairOfflineTest.java` | 移除Jev路由/选择或调整离线门禁，保留RAG和隔离；全部差异 |
| M | `tools/final-e2e/FinalE2eResume.java` | 移除Jev路由/选择或调整离线门禁，保留RAG和隔离；全部差异 |
| M | `tools/final-e2e/FinalE2eRunner.java` | 移除Jev路由/选择或调整离线门禁，保留RAG和隔离；全部差异 |
| M | `tools/final-e2e/README.md` | 当前指南/退役标识，历史事实保留；全部差异 |
| M | `tools/final-e2e/run.ps1` | 去掉Jev凭据/测试入口或立即拒绝退役执行；全部差异 |
| D | `tools/jev-proxy-network/JevHistoricalReplay.java` | 删除依赖退役实现的旧实验源码；全部差异 |
| D | `tools/jev-proxy-network/JevNetworkDiagnostic.java` | 删除依赖退役实现的旧实验源码；全部差异 |
| D | `tools/jev-proxy-network/JevScoreOnce.java` | 删除依赖退役实现的旧实验源码；全部差异 |
| D | `tools/jev-proxy-network/JevScoreOnceOfflineTest.java` | 删除依赖退役实现的旧实验源码；全部差异 |
| M | `tools/jev-proxy-network/run.ps1` | 去掉Jev凭据/测试入口或立即拒绝退役执行；全部差异 |
| M | `tools/rag-e2e/README.md` | 当前指南/退役标识，历史事实保留；全部差异 |
| M | `tools/rag-e2e/RagE2eRunner.java` | 移除Jev路由/选择或调整离线门禁，保留RAG和隔离；全部差异 |
| M | `tools/rag-p4-2/EXECUTION.md` | 当前指南/退役标识，历史事实保留；全部差异 |
| M | `tools/rag-p4-2/execution/build_delta.py` | 退役旧字节差异生成器；全部差异 |
| A | `docs/verification/JEV_REMOVAL_DEEPSEEK_ONLY_20261111.md` | 该批原始验证报告，保留历史事实；全部差异 |
| A | `tools/check_deepseek_only.py` | 可移植编译/离线/退役门禁；全部差异 |
| A | `tools/jev-proxy-network/README.md` | 当前指南/退役标识，历史事实保留；全部差异 |

### B1完整独占文件清单

| 操作 | 路径 | 目的 / 提交范围 |
| --- | --- | --- |
| A | `docs/verification/B1_CONCURRENT_IDEMPOTENT_REPLAY_20261111.md` | 该批原始验证报告，保留历史事实；全部差异 |
| A | `src/test/java/com/kun/aiinterview/interview/service/InterviewConcurrentReplayIntegrationTest.java` | 18项真实MySQL/API并发重放；全部差异 |

### B2完整独占文件清单

| 操作 | 路径 | 目的 / 提交范围 |
| --- | --- | --- |
| A | `docs/verification/B2_POST_CLAIM_FAILURE_RECOVERY_20261111.md` | 该批原始验证报告，保留历史事实；全部差异 |
| A | `src/test/java/com/kun/aiinterview/interview/service/InterviewPostClaimFailureIntegrationTest.java` | 3项真实领取后恢复验证；全部差异 |

### B3完整独占文件清单

| 操作 | 路径 | 目的 / 提交范围 |
| --- | --- | --- |
| M | `src/main/java/com/kun/aiinterview/interview/mapper/AnswerEvaluationMapper.java` | FINAL候选查询契约注释；全部差异 |
| M | `src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java` | MAIN/答案/追问来源校验及恢复入口；全部差异 |
| M | `src/main/java/com/kun/aiinterview/user/service/UserWeaknessService.java` | 统计前拒绝非FINAL；全部差异 |
| M | `src/main/resources/mapper/AnswerEvaluationMapper.xml` | FINAL-only且保留重复候选；全部差异 |
| M | `src/test/java/com/kun/aiinterview/interview/mapper/AnswerEvaluationMapperTest.java` | INITIAL排除、FINAL排序/隔离；全部差异 |
| M | `src/test/java/com/kun/aiinterview/interview/service/InterviewReportApiServiceTest.java` | 适配新构造依赖；全部差异 |
| M | `src/test/java/com/kun/aiinterview/interview/service/InterviewReportServiceTest.java` | 合法夹具与非法来源拒绝；全部差异 |
| M | `src/test/java/com/kun/aiinterview/interview/service/InterviewReportTransactionServiceIntegrationTest.java` | 补全来源fixture，保留真实事务断言；全部差异 |
| M | `src/test/java/com/kun/aiinterview/user/service/UserWeaknessServiceTest.java` | FINAL前置校验且零写入；全部差异 |
| A | `docs/verification/B3_REPORT_FINAL_CONSISTENCY_20261111.md` | 该批原始验证报告，保留历史事实；全部差异 |
| A | `src/test/java/com/kun/aiinterview/interview/service/InterviewReportFinalConsistencyIntegrationTest.java` | 24项真实MySQL一致性验证；全部差异 |

### B1/B2共享文件：精确到hunk

以下坐标来自可验证的**分阶段差异**；不是把最终累计diff里一个合并hunk机械全部归给B1。B1基于HEAD，B2基于B1结束版。两个B1结束版来自 `/tmp/ai-b2-20261111/InterviewWorkflowService.java` 和 `InterviewWorkflowServiceTest.java`，已核对入场SHA。

| 文件 | 批次 | hunk坐标 | 精确内容 |
| --- | --- | --- | --- |
| `src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java` | B1 | `@@ -74,6 +74,11 @@`；`@@ -118,6 +123,11 @@` | 首次EVALUATED返回；当前题不匹配时重读完成态 |
| 同上 | B2 | `@@ -146,16 +146,16 @@` | 将下一题查询和hasNext计算移入既有try；没有其他生产变化 |
| `src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowServiceTest.java` | B1 | `@@ -507,37 +507,50 @@` | 原EVALUATED拒绝改重放；增加3种旧状态重读、2种错误重读身份用例 |
| 同上 | B2 | `@@ -30,7 +30,9 @@` | DataAccessResourceFailureException与SQLException imports |
| 同上 | B2 | `@@ -347,6 +349,87 @@` | 三个新测试方法展开7项：成功领取后查询失败、suppressed、claim自身失败 |
| 同上 | B2 | `@@ -412,6 +495,7 @@`；`@@ -445,6 +529,7 @@`；`@@ -476,6 +561,7 @@`；`@@ -505,6 +591,7 @@`；`@@ -583,6 +670,7 @@`；`@@ -711,6 +799,7 @@` | 6处既有无资格/重放路径增加never markEvaluationFailed断言 |

C0已生成供后续审核的4份临时文本patch（没有应用）：`/tmp/ai-c0-20261111/B1-InterviewWorkflowService.java.patch`、`B1-InterviewWorkflowServiceTest.java.patch`、`B2-InterviewWorkflowService.java.patch`、`B2-InterviewWorkflowServiceTest.java.patch`。完整累计hunk索引为同目录 `hunks.json`。即使临时文件清理，以上源码方法及hunk内容仍可重建；执行提交前必须再次核对工作区指纹。

### 原有用户修改与提交依赖

| 路径 | 精确归属 / 处理 |
| --- | --- |
| `mvnw` | 仅mode 100644→100755，正文无diff。保留，不混入J1～B3；本次使用`sh mvnw`不依赖可执行位。 |
| `src/test/java/com/kun/aiinterview/user/service/ConcurrentPasswordChangeIntegrationTest.java` | `@@ -205,11 +205,11 @@`中两行将`waits.REQUESTING_ENGINE/BLOCKING_ENGINE`改为`waits.ENGINE`。J1前用户改动；本轮真实MySQL列检查只有ENGINE、没有前两个列。完整回归通过依赖这项现状，不应无声遗漏。 |
| `compose.mac.local.yaml` | 用户原有Mac正式local配置，60行；口令使用环境占位，没有硬编码秘密。涉及正式库/既有卷，本轮未启动它。保留未跟踪，独立环境任务再决定是否提交。 |

建议顺序（均仅计划，须用户明确授权）：

1. **前置用户修正** `test: correct MySQL lock-wait diagnostics`：只含上述密码并发测试两行；必须在授权时明确纳入用户已有改动。若用户要求排除，应保留其工作区并说明四批提交无法自动继承本次完整通过证据。
2. **J1** `refactor: remove Jev runtime and standardize DeepSeek evaluation`：J1表55项完整提交，尤其同时删除实现/测试/工具引用并修改配置；不能只删生产类留下编译依赖。
3. **B1** `fix: handle concurrent completed-answer replay`：B1独占2项+共享文件的B1 hunks。基于J1后的测试装配与当前基线。
4. **B2** `fix: recover evaluation failures after successful claim`：B2独占2项+相对B1的全部B2 hunks；不能把B1测试替换和B2末尾never断言误合并进前一提交。
5. **B3** `fix: enforce final evaluation consistency in reports`：B3表11项一起提交。新构造依赖、SQL语义和测试fixture必须同一提交；业务上不依赖B1/B2的源码改动，但本轮验收与阶段顺序基于它们。
6. **C0** `docs: record J1-B3 closeout and source learning guide`：仅 `docs/verification/J1_B3_PHASE_CLOSEOUT_20261111.md`、`docs/verification/J1_B3_SOURCE_LEARNING_GUIDE_20261111.md`，记录最终累计基线。

可能冲突集中在两个Workflow共享文件；B3还改ReportService构造器，若之后有新调用者需重新核对。独占文件无需逐hunk拆。后续应以独立索引/获授权的隔离提交环境准备各阶段，不用checkout/reset回退当前用户工作区，不用`git add .`混入环境文件。

**中间提交验证尚未执行。** 当前编译、聚焦和完整回归只证明最终累计工作区。后续授权后，前置修正至少跑对应锁测试，J1跑模型装配/工具编译，B1跑并发重放，B2跑领取后恢复，B3跑报告/Weakness，再在最终提交树做完整普通回归。不能把当前结果称为四个独立提交都已验证。

### 提交安全检查

- 逐文件核对累计diff及10个入场未跟踪文件；秘密模式扫描覆盖新增行和未跟踪全文，私钥/常见GitHub与provider token/JWT等模式0命中。人工核对敏感配置均为变量引用或明确测试占位；不把模式扫描当成穷尽证明。
- `.env`、`application-local.yaml`、target均被忽略；候选路径没有Maven输出、IDE缓存、日志、数据库备份或真实请求证据。没有新增真实凭据或真实用户数据。
- C0临时数据库密码不在留存诊断文件内，已删除凭据文件。日志中的PRIVATE_MODEL_OUTPUT为合成标记，P2说明不包含实际模型返回或秘密。
- 历史Jev报告/fixture没有改名、重写或删除，退休Java源码可从HEAD历史获取。退役生成器留下不可执行历史正文有明确理由，不是活跃死分支。
- 暂存区仍为空；Git add/commit/push均未获本轮授权，未执行。提交条件是审查结论，不等于已提交或推送。

## F. 下一阶段：仅定义 B4-A 审计范围

B4-A先审计现有DeepSeek调用与运行时表现，形成问题证据和最小方案；C0未开发B4。

1. **错误分类**：区分429、可重试5xx、连接失败、读取超时、空/非法JSON、非stop结束及业务校验错误；明确哪些已经发出请求、费用可能未知。入口为DeepSeekEvaluationClient、ExternalServiceException、GlobalExceptionHandler。
2. **有限重试与退避**：先确认RestClient/JDK传输行为和当前业务重提层，防止多层次数相乘。明确候选状态码、最大尝试数、退避/抖动和Retry-After规则；不是预设必须实现自动重试。
3. **HTTP总时间预算**：区别连接/读取超时与端到端墙钟预算，包含排队、RAG、模型、返回/校验与数据库收尾；旧Jev组合预算已删除，不能声称DeepSeek有同等总预算。
4. **线程资源与并发上限**：同步请求如何占用Servlet线程、HTTP连接和数据库连接；异常、超时或中断后是否释放；结合负载证据再决定是否需要简单本机并发控制。E2E工具计数门禁不等于生产限流。
5. **日志脱敏**：优先处理C0-P2-001；检查cause、suppressed、response body、Authorization与Prompt/答案等路径，增加可重复的日志捕获测试。
6. **可观测性**：区分模型请求失败、Java校验失败、claim冲突、stale接管、旧attempt丢弃、报告恢复和FAILED回写失败。记录安全关联标识、耗时与尝试数，避免记录完整内容或凭据。
7. **验证策略**：先使用Mock HTTP故障矩阵、确定性并发和隔离MySQL；如确需真实模型/费用实验，再独立明确范围与预算。继续保护attempt/version、短事务及现有opt-in。

不预设引入Redis、MQ、熔断器、新框架、后台任务或新Schema。

## G. 五类交付状态

| 状态 | 结论 | 证据 |
| --- | --- | --- |
| 开发完成 | J1～B3目标已核实；C0仅新增两份文档 | 累计源码diff与分批归属 |
| 测试通过 | 本轮553聚焦、1589完整（19门禁跳过）、离线工具、补验与打包通过 | D节命令、原始XML/日志；真实模型未验证 |
| 独立审查通过 | C0重新核验累计实现与调用链，未发现P0/P1；既有P2已记录 | C节证据与边界；不是第三方安全认证 |
| 已提交 | 否；暂存区为空，HEAD仍afbd1c5 | 实际Git状态 |
| 已推送 | 本轮四批未提交，因此没有本轮推送；远端实时状态未查询 | 未执行push/fetch |

交付文件：

- `/Users/kun/Developer/projects/ai-interview-system/docs/verification/J1_B3_PHASE_CLOSEOUT_20261111.md`：本报告，包含完整文件/hunk归属、命令/结果和提交依赖。
- `/Users/kun/Developer/projects/ai-interview-system/docs/verification/J1_B3_SOURCE_LEARNING_GUIDE_20261111.md`：按实际调用链组织的简明学习指南。

本轮在C0收口结束。下一步是用户审阅并明确授权上述提交计划；不自动执行Git写操作或开展B4。
