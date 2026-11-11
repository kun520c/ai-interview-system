# J1：移除 Jev，保留 DeepSeek 单模型业务与故障恢复

执行日期：2026-11-11（Asia/Shanghai）。代码、测试及工具修改已完成；普通回归与打包通过；未暂存、未 commit、未 push、未合并 PR。

报告实际路径：`/Users/kun/Developer/projects/ai-interview-system/docs/verification/JEV_REMOVAL_DEEPSEEK_ONLY_20261111.md`。
仓库根目录：`/Users/kun/Developer/projects/ai-interview-system`。下文相对文件清单均以此绝对目录为根；源码阅读路径另列绝对路径。

## 基线、环境与授权边界

- 工作分支 `main`；HEAD、本地 `origin/main`、HTTPS `git ls-remote` 返回的远端 main 均为 `afbd1c5ab010f3a0a296e15e25c016f81eb2b74f`，无新增提交差异需要复核。读取了仓库 `AGENTS.md`，没有回退基线。
- 初始未提交工作：`mvnw` 的可执行位变化、`src/test/java/com/kun/aiinterview/user/service/ConcurrentPasswordChangeIntegrationTest.java` 的两行 MySQL 锁诊断 SQL 修正、未跟踪 `compose.mac.local.yaml`。本轮不修改、不暂存这三项。修改范围无重叠，因此复用当前工作区，未创建新分支或 worktree。
- 实测 macOS 26.7.1 / ARM64、Temurin Java 21.0.13、Maven Wrapper 3.9.16。SSH 443 检查先遇沙箱 DNS 限制，授权执行后遇公钥认证失败；HTTPS 只读检查成功。
- 原进程没有继承 DB_* / TEST_DB_*，`application-local.yaml` 不存在。未输出 `.env` 的值，也未导入真实服务凭据。ignored `.env` 中原有旧 selector 保留原样；即使注入 `AI_JUDGE_PROVIDER=jev` 也不能激活已删除实现。迁移指南要求从实际部署/IDE/Shell 环境移除全部旧配置。
- 新建仅供本轮使用的容器 `ai-interview-j1-20261111-mysql`，镜像 MySQL 8.0.45，回环端口 13318，内存数据目录；只初始化 `ai_interview_test`。原 schema 快照仅在临时副本中替换建库/USE 目标，未修改仓库 SQL、表结构、已有数据库或数据卷。
- 首次容器默认 UTC，导致数据库 CURRENT_TIMESTAMP 与项目 Asia/Shanghai 的 stale 判断错位。仅对该临时实例执行 `SET GLOBAL time_zone='+08:00'` 后重新建立测试连接，改前完整回归通过。未修改应用配置或减弱断言。
- 所有 Maven 测试子进程移除 RUN_REAL_*、MILVUS_ENABLED、DEEPSEEK_ENABLED、SPRING_* 及旧 selector 等继承覆盖，只注入临时数据库连接。保留测试自身 profile、EnvironmentIsolationInitializer 和双层 opt-in 保护。
- 临时 MySQL 在全部验证完成后已停止。未连接正式 `ai_interview`、evaluation 数据库或真实模型；没有业务历史数据迁移。

## 清理前后调用链

1. `InterviewService.submitAnswer` 校验用户归属、Session/Question、requestId 及完整 payload 一致性。已有 EVALUATED 结果走查询重放；新增答案由 `InterviewWorkflowTransactionService.submitAnswer` 在短事务中插入，数据库唯一约束继续兜底。
2. `InterviewWorkflowService.evaluateAnswer` 读取问题和 Session 快照，以新的 attemptId 抢占 SUBMITTED、FAILED 或 stale EVALUATING；fresh EVALUATING 仍冲突。Session version 保留用于之后的写入 CAS。
3. `EvaluationOrchestrationService.evaluate` 先查已有评价，存在则从业务列恢复结果/追问目标，不重新调用模型。新评价经过 Context、RAG 检索、MySQL 文档/Chunk 资格二次校验、PromptBuilder，再调用 `JudgeProvider`。
4. **清理前**：`JudgeConfiguration` 根据 `ai.judge.provider` 选择 DeepSeek 或 Jev；Jev 判分、DeepSeek 反馈、专属预算/反馈重试和跨模型 fallback 都位于 Jev 组合实现。
5. **清理后**：`deepseek.enabled=true` 只构造 `DeepSeekJudgeProvider → DeepSeekEvaluationClient`，无 selector、Jev Bean 或跨模型 fallback。禁用时基础应用仍可启动，上层 ObjectProvider 缺失服务的受控响应保持不变。
6. 模型结果仍不可信：`LlmEvaluationValidator → EvaluationScoreCalculator → EvaluationStandard → FollowUpPolicy`。五维各 0～20，Java 求和为总分；评级 90/80/60 分界及 `evaluation-standard-v1` 不变。MAIN 有追问为 INITIAL，无追问为 FINAL；FOLLOW_UP 产生综合 FINAL，不能产生第二次追问。
7. 成功结果连同 pending RAG trace 返回 Workflow，在 `moveToFollowUp / advanceToNextMain / finishSession` 短事务中锁定答案并核对 attemptId，再写轨迹、评价、答案/问题状态和 Session CAS。**以当前基线代码为准：成功轨迹在模型返回后随 fenced 短事务提交，并非早期文档中的先独立落库。** 模型失败可以保留 `persistFailedAttempt` 审计轨迹，但不能形成有效评价；旧 attempt 也不能写失败轨迹或失败状态。
8. 报告链保持 `InterviewReportService → DeepSeekInterviewReportGenerator → DeepSeekEvaluationClient.completeJson → InterviewReportTransactionService`。生成在事务外，最终 report 插入、weakness 更新、Session READY/version CAS 原子提交。READY 直接复用已有报告。

仅保留上述既有设计；POM 没有独立 Jev SDK，未删 Spring Web、Jackson、JDK HTTP、Spring AI 或 Milvus 共享能力，未升级依赖、改 Schema 或外部 API。

## 单模型故障恢复的真实语义

- **A：单次 HTTP 尝试**。每次 DeepSeek evaluate/completeJson 只有一次应用层请求，没有本轮新增的 HTTP retry、第二模型或多层重试。生产连接/读取超时仍默认 3s/60s；工具原有有限超时保留。
- **B：FAILED 后显式业务再次请求**。原 requestId、同问题、完全相同答案可重新竞争评价资格，新的 attemptId 再尝试一次；相同 requestId 改内容或问题仍冲突，不能覆盖原答案。报告 FAILED 使用同 sessionId 再次请求，领取新的固定 version。
- **C：stale in-progress 接管**。后续请求在持久状态过期后执行单胜者 CAS；fresh 状态不接管。已有持久评价/报告优先复用，否则再调用一次模型；不是定时任务、后台队列或自动轮询 retry。旧 attempt/version 的迟到成功或失败不能覆盖新任务。
- 删除 fallback 仅删除“一次评价内部从 Jev 换到 DeepSeek”的供应商组合路径；B/C 由 MySQL 业务状态和 Java 事务服务实现，未删除。
- 429、500/503、连接异常、超时、空响应、空白 content、非 stop finish_reason、非法 JSON 均验证为脱敏 502；评分还覆盖五个维度的缺失/null、错误类型及越界，报告覆盖缺字段/错误类型。失败无有效评价、无会话推进、无伪造成功报告或 weakness 更新。之后明确重提可恢复；READY/评价重放不再调用模型。
- 没有实现新的错误分类、退避、总时间预算、并发上限，也不承诺外部调用 exactly-once。final-e2e 工具的请求计数门禁是既有实验工具限制，不能称为生产并发或总时间预算。

## 兼容性与测试迁移

- 保留 JudgeProvider 接口、DeepSeek 客户端/DTO、Validator、Calculator、Standard、FollowUpPolicy、PromptBuilder、报告生成器；上层构造器和业务事务代码不改。
- 保留鉴权、题库快照、唯一约束、requestId/payload 校验、attempt fencing、Session CAS、MySQL RAG 资格校验、report/weakness/READY 原子性。
- 历史 `llm_model=jev-*`、`prompt_version=jev-*`、复合 `raw_result` 均合法。没有改写任何既有业务数据，没有用 DeepSeek DTO 强制解析所有 raw_result。新增历史测试只改本测试新建并回滚的 fixture。
- `FullInterviewBusinessOfflineTest` 新增真实 MySQL 下 FINAL 与已有 FOLLOW_UP 的详情查询、API 重放和 Orchestration 重用：清空模型调用记录后断言无模型/Embedding/向量调用，评价整行、问题行、检索批次、命中轨迹保持不变。JSON 比较允许 MySQL 正常的键序/空白规范化，不改变内容。
- `EvaluationOrchestrationServiceTest` 保留模型元数据透传公共断言（用中立 fixture），并把已有 FINAL/FOLLOW_UP 重用用例明确设为历史 Jev 元数据；追问目标从结构化评分点列恢复，不解析 raw_result。
- `FullInterviewBusinessOfflineTest` 删除 Jev-fallback 重复参数维度，保留 DeepSeek 的全部公共分数/脱敏断言，扩大为五维逐项非法类型校验，新增 HTTP 故障恢复和报告恢复用例。原来 76 项，本轮 85 项。
- `JudgeConfigurationTest` 从旧 34 项改为 7 项：保留 disabled/唯一 DeepSeek 装配，补充旧 `jev`/未知/空 selector、旧环境变量和残留无效 Jev 属性均无法激活 Jev。专属代理/预算/密钥配置验证随相应实现退役。
- 删除 `judge/jev/` 下 10 个专属测试类，改前共 246 项，覆盖的是已删除协议、分数映射、反馈组合、代理、预算与 fallback。未删除或减弱公共 Validator、算分、追问、事务、并发及 RAG 测试。
- `AiSystemBaselineEvaluationTest` 保留真实 baseline 的双门禁、evaluation 隔离、RAG/Java 评分/消融/e2e/recovery，只移除 Jev 可用性判断和 Jev 故障后真实 DeepSeek fallback 实验；普通回归仍跳过。
- `FullInterviewBusinessOfflineTest` 有类级 `@Transactional`，其结果仅作为 HTTP/业务/MyBatis 与 fixture 回滚验证，**不能单独证明跨请求提交或外部调用无事务**。原独立 `InterviewWorkflowTransactionServiceIntegrationTest`、`InterviewEvaluationClaimConcurrencyIntegrationTest`、`InterviewReportTransactionServiceIntegrationTest`、`StaleRecoveryCasIntegrationTest` 均保留并运行。

## 实际验证记录

下面均为本次实际运行计数，顺序为 tests / failures / errors / skipped。执行记录在 `/tmp/ai-j1-20261111`；普通完整测试没有停在依赖解析阶段。

聚焦命令 F 为：

```text
sh mvnw -B -ntp -Dtest=JudgeConfigurationTest,DeepSeekJudgeProviderTest,DeepSeekEvaluationClientTest,DeepSeekConfigurationTest,DeepSeekPropertiesTest,LlmEvaluationValidatorTest,EvaluationScoreCalculatorTest,EvaluationStandardTest,FollowUpPolicyTest,EvaluationOrchestrationServiceTest,FullInterviewBusinessOfflineTest,InterviewWorkflowServiceTest,InterviewWorkflowTransactionServiceTest,InterviewWorkflowTransactionServiceIntegrationTest,InterviewEvaluationClaimConcurrencyIntegrationTest,InterviewReportServiceTest,InterviewReportTransactionServiceTest,InterviewReportTransactionServiceIntegrationTest,DeepSeekInterviewReportGeneratorTest,DeepSeekInterviewReportGeneratorConfigurationTest,StaleRecoveryCasIntegrationTest test
```

1. 改前 `sh mvnw -B -ntp test`，退出 **1**，**1790 / 7 / 0 / 19**；日志 `baseline.log`。临时 MySQL UTC 导致 stale/reclaim 时间比较及并发等待失败。
2. 仅校正临时 MySQL 时区后，改前相同命令，退出 **0**，**1790 / 0 / 0 / 19**；日志 `baseline-timezone-corrected.log`。
3. 首轮聚焦：F 在 `-Dtest=...` 前加 `clean`，退出 **1**，**367 / 14 / 0 / 0**；日志 `focused-initial.log`。新增 fixture 的报告 Properties 缺模型名（12 项）及 JDBC 更新绕过 MyBatis 一级缓存（2 项），不是生产回归。Maven clean 用于移除旧编译类；本轮原始日志已另存 /tmp。
4. 修正 fixture 后 `sh mvnw -B -ntp -Dtest=FullInterviewBusinessOfflineTest test`，退出 **0**，**85 / 0 / 0 / 0**；日志 `focused-fixture-corrected.log`。
5. `python3 tools/check_deepseek_only.py` 首轮退出 **0**：21 个 Java 源文件编译成功，**33 / 0 / 0 / 0**，9 项静态检查；输出 `target/deepseek-only-3ca25fd21d724e69b7a356026b10af10/`。
6. 代码复核发现 E2E 观察器会给 JudgeProvider 加 JDK 代理，工具的 DeepSeek 类型检查改为 `AopProxyUtils.ultimateTargetClass`，增加直接对象、两种原观察器代理及非 DeepSeek 拒绝测试。再次执行同一工具命令，退出 **0**，21 源文件编译、**37 / 0 / 0 / 0**、9 静态检查；最终输出 `target/deepseek-only-6c03a8b0f48e4ff1b90389479cbfa70a/`。
7. 最终 F，退出 **0**，**367 / 0 / 0 / 0**；日志 `focused-final.log`。
8. `python3 -B tools/run_public_offline.py` 首轮退出 **1**；其 `sh mvnw -o -B -ntp -Dtest=RagP1OfflineBusinessReplayTest,RagP1IndependentQuerySetTest,KnowledgeRetrievalThresholdTest test` 为 **28 / 0 / 13 / 0**。沙箱阻止 Mockito attach 和 `/var/tmp` 写入；日志 `target/public-offline-9b7f78750a6b4cce863771903088b899/rag-java.log`。不是依赖解析失败，也不是 RAG 代码失败。
9. 相同公开离线脚本获准在可用执行环境重跑，退出 **0**：Java **28 / 0 / 0 / 0**；`python3 -B -m unittest discover -s tools/rag-p1 -p test_replay.py` **9 / 0 / 0 / 0**；候选证据 Java gate **54 / 0 / 0 / 0**；classpath 生成和 javac 退出 0。输出 `target/public-offline-2046121345d24a95a0c2b45c9a954ea1/`，未访问数据库/模型。
10. 最终普通完整 `sh mvnw -B -ntp test`，退出 **0**，**1526 / 0 / 0 / 19**，BUILD SUCCESS；日志 `final-regression.log`、独立 XML 副本 `final-regression-reports/`。涵盖认证、题库、RAG、普通真实 MySQL Mapper/事务/并发测试。
11. `sh mvnw -B -ntp -DskipTests package`，退出 **0**，BUILD SUCCESS；此步**不执行测试，测试计数不适用**，不把历史 Surefire 报告算作本次运行。日志 `package.log`。
12. ZIP/JAR 检查退出 **0**：`target/ai-interview-system-0.0.1-SNAPSHOT.jar` 包含 DeepSeekJudgeProvider，无 `judge/jev/`、JudgeProperties 或旧 YAML 映射；结果 `package-inspection.json`。
13. 最终 `git diff --check` 退出 **0**。残留扫描用 `rg`，生产代码/有效配置零命中；详见白名单。

工具 gate 的精确子命令、退出码保存在最终输出目录 `commands.json`，包括 `dependency:build-classpath`、21 源文件 `javac -proc:none -encoding UTF-8`、`FinalE2eOfflineTest` 与 `RagE2eConsistencyOfflineTest`。两个 Java 测试各 17、20 项。三个退役 Python 入口的退出码 **1 是预期拒绝**，返回 RETIRED，不联网、不写历史报告。PowerShell 仅做静态契约检查；macOS 没有 pwsh，未声称执行了 Windows 脚本。

完整测试数量由实际 XML 汇总：1790 − 246（专属测试）− 27（装配用例净减少）+ 9（业务用例净增加）= 1526。这只是本次变更的数量核对，不是固定验收阈值。

19 skipped 分别为：KnowledgePersistenceIntegrationTest 10、RealEmbeddingSmokeTest 1、RealMilvusVectorStoreSmokeTest 1、RealKnowledgeDocumentProcessingSmokeTest 2、RealKnowledgeDocumentProcessingFailureIntegrationTest 2、RealMilvusSchemaValidationSmokeTest 2、AiSystemBaselineEvaluationTest 1。未执行真实 DeepSeek/Bailian/Milvus smoke、AI 质量/费用/性能实验。缺少本轮真实服务配置与独立付费执行条件，离线替身成功不能代表真实模型效果。

历史 final-e2e/rag-e2e 原始收费证据重放器仅编译，未运行需要私有 target 归档的回放；未伪造冻结 SHA、锁或历史原始响应。浏览器 harness 编译通过，本轮未运行浏览器 UI E2E。

## 原有问题与后续任务

本轮未改 Workflow、Report 的生产业务逻辑，也不把所有竞态都宣称已修复。按用户指定独立保留：

1. 并发重放仍可能在 Session 指针已推进、Workflow 重新校验当前题的窗口误报 500；现有单胜者测试通过不等于覆盖全部交错。
2. claim 后 `findNextPendingMainQuestion` 等查询的异常保护范围：相关查询仍在 try/catch 外，本轮不移动边界。
3. 报告 FINAL-only 规则：现 SQL 按 FINAL 优先排序，仍可能选 INITIAL；本轮不改查询和业务规则，不能宣称严格 FINAL-only 已修复。

下一轮逐项复现并另立小批次修复；错误分类、退避/总预算/并发上限另作需求设计。本轮到清理、兼容和回归结束，不进入这些改造，不生成简历亮点。

## 10～15 分钟源码阅读路径（约 14 分钟）

1. **2 分钟**：`/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewService.java:223`，再看 `handleExistingAnswer`（415）：理解 requestId + 问题 + 完整答案一致性，EVALUATED 重放与 FAILED 重提的分界。
2. **2 分钟**：`/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java:48`、178、249：观察新 attemptId、claim/retry/stale reclaim 和外部调用所在的无长事务编排边界。
3. **2 分钟**：`/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/orchestration/EvaluationOrchestrationService.java:104`，以及 `.../evaluation/judge/JudgeConfiguration.java`、`DeepSeekJudgeProvider.java`：先复用历史结果，再进入唯一 DeepSeek 路径；接口保留不等于仍有运行时模型选择。
4. **3 分钟**：`/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java` → 同 evaluation 下 `validation/LlmEvaluationValidator.java`、`score/EvaluationScoreCalculator.java`、`standard/EvaluationStandard.java`、`decision/FollowUpPolicy.java`：从一次 HTTP JSON 请求到可信的 Java 分数和决策。
5. **2 分钟**：`/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java:390`，向上看三个推进事务：attempt fencing、已有评价复用、trace/evaluation 写入和 Session version CAS 如何共同阻止迟到结果污染。
6. **3 分钟**：`/Users/kun/Developer/projects/ai-interview-system/src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java:90` → `.../interview/report/deepseek/DeepSeekInterviewReportGenerator.java` → `.../interview/service/InterviewReportTransactionService.java`。对照 `FullInterviewBusinessOfflineTest` 的 FAILED/READY 用例及独立报告并发集成测试，理解报告 version 与原子提交。

读完应能区分：模型 fallback 是一次评价内部换供应商；业务恢复是下一次请求依据持久状态领取新身份/版本或直接复用结果。删除前者不会删除后者。

## 文件清单及原因

本轮新增：

- `docs/verification/JEV_REMOVAL_DEEPSEEK_ONLY_20261111.md`：本报告。
- `tools/check_deepseek_only.py`：可移植工具编译、退役入口及离线契约检查，独立输出目录避免覆盖历史证据。
- `tools/jev-proxy-network/README.md`：明确旧实验退役及历史源码查阅基线。

本轮删除：

- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/JudgeProperties.java`：仅用于 provider 选择的配置类。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevClient.java`：Jev 专属生产协议/客户端/组合/预算/追踪实现。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevEvaluationTrace.java`：Jev 专属生产协议/客户端/组合/预算/追踪实现。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java`：Jev 专属生产协议/客户端/组合/预算/追踪实现。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevProperties.java`：Jev 专属生产协议/客户端/组合/预算/追踪实现。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevRequest.java`：Jev 专属生产协议/客户端/组合/预算/追踪实现。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevRequestBudget.java`：Jev 专属生产协议/客户端/组合/预算/追踪实现。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevResponse.java`：Jev 专属生产协议/客户端/组合/预算/追踪实现。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevStageException.java`：Jev 专属生产协议/客户端/组合/预算/追踪实现。
- `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevClientTest.java`：仅验证已删除 Jev 实现的专属测试；公共契约由现行 DeepSeek/业务测试保留。
- `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevDisplayQuantizationHardeningTest.java`：仅验证已删除 Jev 实现的专属测试；公共契约由现行 DeepSeek/业务测试保留。
- `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevE06ReliabilityReplayTest.java`：仅验证已删除 Jev 实现的专属测试；公共契约由现行 DeepSeek/业务测试保留。
- `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevFeedbackPromptHardeningTest.java`：仅验证已删除 Jev 实现的专属测试；公共契约由现行 DeepSeek/业务测试保留。
- `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevFeedbackReliabilityTest.java`：仅验证已删除 Jev 实现的专属测试；公共契约由现行 DeepSeek/业务测试保留。
- `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProviderTest.java`：仅验证已删除 Jev 实现的专属测试；公共契约由现行 DeepSeek/业务测试保留。
- `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevProxyTransportTest.java`：仅验证已删除 Jev 实现的专属测试；公共契约由现行 DeepSeek/业务测试保留。
- `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevRequestBudgetTest.java`：仅验证已删除 Jev 实现的专属测试；公共契约由现行 DeepSeek/业务测试保留。
- `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevScoreMappingTest.java`：仅验证已删除 Jev 实现的专属测试；公共契约由现行 DeepSeek/业务测试保留。
- `src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevScorePrecisionCompatibilityTest.java`：仅验证已删除 Jev 实现的专属测试；公共契约由现行 DeepSeek/业务测试保留。
- `tools/jev-proxy-network/JevHistoricalReplay.java`：依赖被删类型的旧 Jev 网络/协议回放实验，入口已明确退役；历史报告保留。
- `tools/jev-proxy-network/JevNetworkDiagnostic.java`：依赖被删类型的旧 Jev 网络/协议回放实验，入口已明确退役；历史报告保留。
- `tools/jev-proxy-network/JevScoreOnce.java`：依赖被删类型的旧 Jev 网络/协议回放实验，入口已明确退役；历史报告保留。
- `tools/jev-proxy-network/JevScoreOnceOfflineTest.java`：依赖被删类型的旧 Jev 网络/协议回放实验，入口已明确退役；历史报告保留。

本轮调整（不包含开始时已有用户改动）：

- `README.md`：架构、单次调用与业务恢复说明改为 DeepSeek 唯一模型。
- `docs/GETTING_STARTED.md`：删除启动 selector，新增环境清理、无 Schema 迁移及历史兼容说明。
- `docs/README.md`：当前学习入口指向 J1，旧 Jev 教材标为历史；不改其历史结论。
- `docs/evaluation/README.md`：区分当前 baseline 与旧评测，标注退役工具。
- `docs/evaluation/scripts/build-reports.py`：阻止硬编码旧实验的生成器覆盖历史报告，原文本保留供审查。
- `docs/evaluation/scripts/check-jev-access.py`：改为立即拒绝的退役入口，删除旧 API 网络请求。
- `docs/evaluation/scripts/run-baseline.ps1`：不读取 Jev key，不按凭据选模型，聚焦列表改为保留的公共/DeepSeek 测试。
- `src/main/java/com/kun/aiinterview/interview/evaluation/judge/JudgeConfiguration.java`：仅在 DeepSeek 启用时装配唯一 DeepSeekJudgeProvider。
- `src/main/resources/application.yaml`：删除 ai.judge、JEV_* 和运行时 selector 映射。
- `src/test/java/com/kun/aiinterview/evaluation/AiSystemBaselineEvaluationTest.java`：删除 Jev 专属分支与网络 fallback 实验，保留原 opt-in/隔离门禁。
- `src/test/java/com/kun/aiinterview/interview/evaluation/judge/JudgeConfigurationTest.java`：唯一模型/禁用/旧 selector 无效的装配回归。
- `src/test/java/com/kun/aiinterview/interview/evaluation/orchestration/EvaluationOrchestrationServiceTest.java`：公共元数据透传和历史 FINAL/FOLLOW_UP 重用断言。
- `src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java`：迁移公共分数断言；新增单次失败/显式恢复/报告/历史数据回归。
- `src/test/resources/application-test.yaml`：删除无用 selector；保留独立数据库、测试 JWT/Embedding、deepseek/milvus disabled。
- `src/test/resources/evaluation/README.md`：历史采样 fixture 保留说明。
- `tools/README.md`：工具现状、退役边界、新离线 gate 的运行方法。
- `tools/browser-e2e/BrowserE2eServer.java`：移除无效 selector，保留原隔离与本地替身。
- `tools/final-e2e/FinalE2eGate.java`：删除 Jev 路由/密钥/协议/目标地址，拒绝未知及退役 provider；单答案 DeepSeek 上限为一次。
- `tools/final-e2e/FinalE2eOfflineTest.java`：新增退役路由/代理 Bean 契约与单模型工具预算断言。
- `tools/final-e2e/FinalE2eRepairOfflineTest.java`：退役 provider 拒绝错误码对齐；历史完整性公共断言保留。
- `tools/final-e2e/FinalE2eResume.java`：删除 selector 赋值；历史已发生调用和零请求预算字段保留。
- `tools/final-e2e/FinalE2eRunner.java`：删除 Jev 选择和强类型响应解析；核验真实 DeepSeek Bean；历史 usage 只读兼容保留。
- `tools/final-e2e/README.md`：增加当前入口说明，原费用/成功/失败证据原样保留。
- `tools/final-e2e/run.ps1`：不传 Jev key，测试列表不再引用被删类。
- `tools/jev-proxy-network/run.ps1`：立即拒绝，不读 key、不联网。
- `tools/rag-e2e/README.md`：当前入口与历史执行记录分开说明。
- `tools/rag-e2e/RagE2eRunner.java`：移除 selector，并穿透原观察代理确认 DeepSeek 目标类型；RAG 职责保留。
- `tools/rag-p4-2/EXECUTION.md`：仅将旧 Jev 代理字节差异构建标为退役，不删除 RAG 工具。
- `tools/rag-p4-2/execution/build_delta.py`：立即拒绝依赖旧 Jev 类的历史一次性字节证据构建，旧源码/证据保留。

保留不改：JudgeProvider、DeepSeekJudgeProvider、DeepSeek 客户端/公共 DTO、Validator、Calculator、EvaluationStandard、FollowUpPolicy、PromptBuilder、报告生产实现、全部 Workflow/Report 事务服务和 Mapper、POM/SQL、Spring AI Embedding/Milvus/RAG、认证与题库业务。

历史归档策略：日期化报告、原始 fixture、原失败/成功结论及只读安全检查不为“零关键词”而删除；无需移动原文件。旧可执行入口直接 fail-closed，旧代码在 Git 历史可查。

## Jev 残留白名单

执行 `rg -n -i 'jev|ai\.judge|AI_JUDGE_PROVIDER|typesafe\.ai'`，并补查八个被删类名与 JudgeProperties。`src/main` 及打包后的有效配置零命中。以下逐文件解释保留原因；报告自身属于迁移与审计说明。未扫描输出个人凭据文件内容。

- `README.md`：当前迁移/退役说明与历史索引，不提供 Jev 调用能力。
- `docs/GETTING_STARTED.md`：当前迁移/退役说明与历史索引，不提供 Jev 调用能力。
- `docs/JEV_COMPOSITE_CLOSEOUT_20261014.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/JEV_HTTP200_VS_451_DIAGNOSIS_20261014.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/JEV_PROXY_NETWORK_FIX_20261014.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/PROJECT_FILE_ORGANIZATION_EXECUTION_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/PROJECT_IMPLEMENTATION_AND_VERIFICATION_HANDOFF_20261014.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/PROJECT_TECHNICAL_DETAILS_20261015.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/RAG_P42_ENVIRONMENT_RECOVERY_R02_20261014.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/RAG_P42_EXECUTION_PREP_20261014.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/README.md`：当前迁移/退役说明与历史索引，不提供 Jev 调用能力。
- `docs/TECHNICAL_VALUE_REPAIR_20261015.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/design/timeout-task-fencing.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/environment/Phase0.5-环境清理与隔离验证.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/AI_SYSTEM_BASELINE.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/E2E_EVALUATION_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/ENVIRONMENT_CHECK.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/JEV_EVALUATION_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/Jev-Java结构与无效代码审计.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/PHASE_5_E06_ENTRY_READINESS.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/PHASE_5_E07_DOCKER_WSL_DIAGNOSTIC_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/PHASE_5_E07_ENGINE_GATE_B_READONLY_DIAGNOSTIC_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/PHASE_5_E07_ENTRY_READINESS.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/PHASE_5_E07_EVALUATION_BOOTSTRAP_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/PHASE_5_E07_HOST_CONTEXT_BOOTSTRAP_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/PHASE_5_EPOCH_02_JUDGE_VALIDATION_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/PHASE_5_EPOCH_03_JUDGE_VALIDATION_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/PHASE_5_EPOCH_04_JUDGE_VALIDATION_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/PHASE_5_EPOCH_05_JUDGE_VALIDATION_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/PHASE_5_EPOCH_06_JUDGE_VALIDATION_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/PHASE_5_EPOCH_07_CANARY_01_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/PHASE_5_JUDGE_VALIDATION_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/README.md`：当前迁移/退役说明与历史索引，不提供 Jev 调用能力。
- `docs/evaluation/judge/PHASE_5A_JUDGE_BENCHMARK.json`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5A_JUDGE_BENCHMARK_FREEZE_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5B_EPOCH_02_COMPARISON_REVIEW.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5B_EPOCH_02_STATIC_PATCH_AUDIT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5B_EPOCH_03_COMPARISON_REVIEW.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5B_EPOCH_04_COMPARISON_REVIEW.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5B_EPOCH_05_RELIABILITY_COMPARISON.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5B_EPOCH_06_RELIABILITY_AND_COMPARISON.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5B_EPOCH_06_STATIC_PATCH_AUDIT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5B_EPOCH_07_CANARY_01_SEMANTIC_REVIEW.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5B_EPOCH_07_CANARY_01_STATIC_RUNTIME_AUDIT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5B_JUDGE_COMPARISON_REVIEW.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5C_EPOCH_02_FALLBACK_PROOF.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5C_EPOCH_03_FALLBACK_PROOF.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5C_EPOCH_04_FALLBACK_PROOF.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5C_EPOCH_05_FALLBACK_PROOF.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5C_EPOCH_06_FALLBACK_PROOF.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5C_FALLBACK_PROOF.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5_E04_MODEL_MIGRATION_REVIEW.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5_E05_FEEDBACK_AND_FOLLOWUP_AUDIT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5_E05_NUMERIC_ROOT_CAUSE_AND_FEASIBILITY.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5_E06_FAILURE_ROOT_CAUSE_REVIEW.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5_E06_FEEDBACK_FIX_OPTIONS.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5_E06_PROMPT_HARDENING_PATCH_REVIEW.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5_E06_PROMPT_HARDENING_TEST_RESULTS.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5_E07_DOCKER_WSL_MINIMAL_REPAIR_OPTIONS.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5_E07_ENGINE_GATE_B_RECOVERY_OPTIONS.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5_E07_EVAL_CONFIG_ORIGIN_AUDIT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5_E07_EVAL_RUNTIME_READINESS.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5_OFFLINE_HARDENING_PATCH_REVIEW.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5_OFFLINE_HARDENING_TEST_RESULTS.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5_PROTOCOL_PRECISION_PATCH_REVIEW.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/judge/PHASE_5_PROTOCOL_PRECISION_PATCH_TEST_RESULTS.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/evaluation/scripts/audit-evidence.py`：凭据泄露/被删能力的安全扫描名单，必须保留检测词。
- `docs/evaluation/scripts/build-reports.py`：明确退役，入口立即拒绝；其中历史生成源码只供查阅。
- `docs/evaluation/scripts/check-jev-access.py`：明确退役，入口立即拒绝；其中历史生成源码只供查阅。
- `docs/frontend/V3_BLOCKER_FIX_20261018.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/frontend/V3_INTEGRATION_20261018.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/frontend/V3_PUBLICATION_FILE_REVIEW.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/frontend/V3_PUBLICATION_REVIEW_20261018.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/frontend/v3/blocker-fix/candidate-final-export.json`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/frontend/v3/blocker-fix/change-list.json`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/frontend/v3/blocker-fix/clean-maven.json`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/frontend/v3/blocker-fix/early-focused-failure.json`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/frontend/v3/blocker-fix/final-source-index-proof.json`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/frontend/v3/blocker-fix/focused-tests.json`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/frontend/v3/blocker-fix/integration-maven.json`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/frontend/v3/integration/maven-summary-with-local-archive.json`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/frontend/v3/integration/maven-summary.json`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/question-bank/PHASE_1_QUESTION_BANK_FINAL.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/question-bank/Phase1-Grok题库审查.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/question-bank/Phase1D-题库数据库导入计划.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/question-bank/Phase1D-题库数据库导入验证.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/PHASE_4A_GOLDEN_SET_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/PHASE_4B_BASELINE_RETRIEVAL_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/PHASE_4C1_ADMISSION_POLICY_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/PHASE_4C2_HOLDOUT_FREEZE_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/PHASE_4C3_HOLDOUT_VALIDATION_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/PHASE_4D1_FULL_KB_PLANNING_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/PHASE_4D2_FULL_KB_AUTHORING_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/PHASE_4D3_PRODUCTION_CHUNK_DRYRUN_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/PHASE_4D4_FULL_BANK_INGESTION_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/PHASE_4D5_FULL_BANK_RETRIEVAL_REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/Phase2A-版本与知识入库审计.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/Phase2B-知识文档编写审查.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/Phase2C-离线切片验证.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/Phase3A-真实服务就绪验证.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/Phase3B-真实知识库入库验证.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/full-bank/PHASE_4D2_DOCUMENT_CONTENT_REVIEW.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/full-bank/PHASE_4D4_INGESTION_REVIEW.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/p1-20261012/NEXT_VALIDATION_PLAN.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/rag/p1-20261012/REPORT.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/study/Jev-Judge增量学习教材.html`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/study/Jev-Judge接入学习报告.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/verification/CONCURRENCY_FINAL_ACCEPTANCE_20261019.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `docs/verification/CONCURRENCY_FINAL_INTEGRATION_20261019.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `src/test/java/com/kun/aiinterview/interview/evaluation/judge/JudgeConfigurationTest.java`：负向配置兼容测试：旧 selector/旧属性不得激活 Jev。
- `src/test/java/com/kun/aiinterview/interview/evaluation/orchestration/EvaluationOrchestrationServiceTest.java`：历史 Jev 元数据/opaque raw_result 查询和重放 fixture，不依赖被删类。
- `src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java`：历史 Jev 元数据/opaque raw_result 查询和重放 fixture，不依赖被删类。
- `src/test/resources/evaluation/README.md`：历史采样 fixture 或说明；不作为活跃网络输入，不修改原始事实。
- `src/test/resources/evaluation/jev-e05-numeric-captures.json`：历史采样 fixture 或说明；不作为活跃网络输入，不修改原始事实。
- `src/test/resources/evaluation/jev-e06-feedback-captures.json`：历史采样 fixture 或说明；不作为活跃网络输入，不修改原始事实。
- `tools/README.md`：当前迁移/退役说明与历史索引，不提供 Jev 调用能力。
- `tools/check_deepseek_only.py`：凭据泄露/被删能力的安全扫描名单，必须保留检测词。
- `tools/final-e2e/FinalE2eGate.java`：保留旧报告格式的 jev=0 计数；admit 与 URL 路由拒绝该 provider。
- `tools/final-e2e/FinalE2eOfflineTest.java`：退役 provider 必须拒绝及零请求预算的负向断言。
- `tools/final-e2e/FinalE2eRepair.java`：历史 451/原调用计数/已有证据只读解析或续跑 jev=0 门禁，不发 Jev 请求。
- `tools/final-e2e/FinalE2eRepairOfflineTest.java`：退役 provider 必须拒绝及零请求预算的负向断言。
- `tools/final-e2e/FinalE2eResume.java`：历史 451/原调用计数/已有证据只读解析或续跑 jev=0 门禁，不发 Jev 请求。
- `tools/final-e2e/FinalE2eRunner.java`：仅历史 usage 字段/价格读取兼容，不含 Jev 路由、密钥或 DTO。
- `tools/final-e2e/FinalE2eSecondRepair.java`：历史 451/原调用计数/已有证据只读解析或续跑 jev=0 门禁，不发 Jev 请求。
- `tools/final-e2e/FinalE2eSecondRepairOfflineTest.java`：退役 provider 必须拒绝及零请求预算的负向断言。
- `tools/final-e2e/README.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `tools/final-e2e/resume.ps1`：历史 451/原调用计数/已有证据只读解析或续跑 jev=0 门禁，不发 Jev 请求。
- `tools/jev-proxy-network/README.md`：当前迁移/退役说明与历史索引，不提供 Jev 调用能力。
- `tools/jev-proxy-network/run.ps1`：明确退役，入口立即拒绝；其中历史生成源码只供查阅。
- `tools/rag-e2e/README.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `tools/rag-e2e/RagE2eRunner.java`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-e2e/run.ps1`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-p1/INDEPENDENT_README.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `tools/rag-p1/IndependentRetrieval.java`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-p2/README.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `tools/rag-p2/p2.py`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-p2/run.ps1`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-p3/P3ReadOnlyRetrieval.java`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-p3/README.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `tools/rag-p3/VERIFICATION.md`：历史报告/教材/执行记录或其索引；保留当时成功、失败与限制事实，不视为当前能力。
- `tools/rag-p3/p3.py`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-p3/protocol-v1.json`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-p3/run.ps1`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-p3/test_p3.py`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-p4-1/p41.py`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-p4-1/protocol-v1.json`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-p4-1/run.ps1`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-p4-2/EXECUTION.md`：当前迁移/退役说明与历史索引，不提供 Jev 调用能力。
- `tools/rag-p4-2/execution/build_delta.py`：明确退役，入口立即拒绝；其中历史生成源码只供查阅。
- `tools/rag-p4-2/prepare.py`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-p4-2/verify_tests.py`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-p4/p4.py`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
- `tools/rag-p4/run.ps1`：既有实验的零 Jev 调用计数、预算断言或禁止继承覆盖说明；无 Jev API 入口。
