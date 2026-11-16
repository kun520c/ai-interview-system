# AI 模拟面试系统工程历史档案（2026-11-11—2026-11-15）

记录日期：2026-11-15，Asia/Shanghai。范围：旧对话摘要、本地现有提交、未提交工作区及其历史验收材料。本文件是学习与证据索引，不是新的功能验收或发布许可。配套：[源码学习路线](PROJECT_SOURCE_LEARNING_GUIDE.md)；[面试案例](PROJECT_INTERVIEW_CASES.md)。

## 阅读前先区分证据

- **用户摘要**：本轮任务文字提供的旧对话摘要。归档者没有读取完整旧对话；不能根据它推断每一条个人贡献或完整讨论过程。
- **源码核对**：本轮只读核查当前文件、关键方法与 Git 历史；证明工作区或提交中有什么，不证明当前服务正在运行同一构建，也不等于逐行安全审计。
- **历史验收**：原报告中的测试、浏览器或实验记录；本轮没有重跑，测试结果属于相应历史时点。原始数据可得时单独列出。T/F/E/S = 总数/失败/错误/跳过，实际通过 = T−F−E−S；不同批次和重叠测试不能相加。
- **待核证**：缺原始材料、因果链、独立人工标注、使用者验收或当前运行证据。保留未解决、失败和冲突，不能以合理推测补齐。
- 本轮不运行模型、数据库迁移或大规模测试，不读取或复制凭据。含用户原答或原始模型请求的受限材料只给路径及脱敏摘要。

2026-11-15 本轮只读快照：本地 main 的 HEAD 和本地缓存 origin/main 都指向 84e3489a85d0fb6c8b54ad2a130c52d7476fb897；**没有刷新远端，不能据此证明实时远端状态**。M1-B 至 M1-E 的实现、实验和 Q1/Q2 材料包含未提交文件；共同的 HEAD 不能充当这些版本的完整构建标识。S1 在隔离 evaluation 环境曾做受控切换和验收，不等于 M1 系列已正式部署。当前服务是否运行及其身份本轮未核查。

历史口径中“13 核心表”是旧快照；M1-B 曾获具体范围批准在独立测试目标手工增加任务表。迁移文件存在不证明其他数据库已迁移，本次也不执行迁移。

## 阶段时间线与索引

- **A｜11-11—11-13：**并发重放、领取后失败、FINAL 一致性及外部服务边界，H01–H04。对应本地提交 1df25ea、7d25981、6de9e7a、f764c08、01ac774、f71da96、71aeb22。
- **B｜11-13—11-14：**隔离环境、题库和随机组卷、真实检索与反例，H05–H06。历史导入和当前在线数量必须分开。
- **C｜11-14：**U1/U2 反馈恢复与 S1 可见性，H07–H08。本地功能提交 d090d25、f1fb02b、4bed0e2；S1 文档在 84e3489。
- **D｜11-14：**Q1 独立技术复核、Q2 v5 失败、契约审查与七条候选受阻，H09–H12。质量 **NO-GO**。
- **E/F｜11-14：**M1-A 异步与 Outbox 决策、M1-B 实施及隔离验收，H13–H14。实施与研究成果仍是未提交工作区。
- **G/H｜11-15：**M1-C 所有权锁与缓存、崩溃及内容质量、性能退化、R1/R2 定位与调参，H15–H18。
- **I/J｜11-15：**M1-D 三种报告策略、M1-E 提交后唤醒和提前展示，以及最新同域查询合并，H19–H21。性能测量与收口测试对应不同构建时间。

以下每案固定记录背景、发现、复现、根因、尝试、修复、验证、结果、风险、学习、简历与外部证据。身份、代价和失败均保留；简历句子仅供本人核对实际贡献、理解后选用。

## A｜原有后端与可靠性

<a id="h01"></a>

### H01｜B1：并发完成后合法重放仍返回 500

1. **背景：**同一 requestId 和相同载荷允许重放已保存评价，Session 可能已推进。
2. **发现：**历史 B1 验收用受控并发暴露异常；用户摘要称其为并发完成后的重放问题。
3. **复现：**真实隔离 MySQL、MockMvc、模型替身；请求 A 在两处之一暂停，B 完成同一答案并走五种状态转移，A 恢复。
4. **根因：**A 首读旧 Answer，随后看到新 Session 当前题，若先按“当前题不匹配”报内部异常，就忽略了 B 已完成的事实。
5. **尝试：**唯一键和领取 CAS 可防重复写，但不能单独消除旧读取误判；历史夹具最初误用 MyBatis 代理，五项失败属于测试问题。
6. **修复：**Workflow 的 evaluateAnswer 首读 EVALUATED 即走重放；发现当前题不一致时再重新读取，确认 EVALUATED 才复用。归属、requestId/完整载荷、attempt fencing 与异常检查保留。提交 1df25ea。
7. **验证：**历史复现 16/10/0/0；修复后业务定向 286/0/0/0，普通全量 1549/0/0/19；有真实 MySQL、模型替身，非真实模型测试。
8. **结果：**指定 2×5 交错可恢复，已保存结果复用；记录中没有将所有竞态证明为穷尽。
9. **风险：**不存在评价却标完成、身份或载荷不同仍须拒绝；不能承诺外部模型绝对只调一次。
10. **学习：**沿 InterviewService.submitAnswer → Workflow.evaluateAnswer → requireReloadedAnswer → 响应构造，画双请求时间线。
11. **简历：**可讲“用确定性交错定位重放误报，并通过重读持久状态修复”；会被追问唯一约束、身份边界和 CAS 为什么不够。
12. **证据：**[B1 原验收](../verification/B1_CONCURRENT_IDEMPOTENT_REPLAY_20261111.md)、[Workflow](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java)、[并发测试](../../src/test/java/com/kun/aiinterview/interview/service/InterviewConcurrentReplayIntegrationTest.java)。原始历史 XML 路径由报告定位，可能受临时目录生命周期影响。

<a id="h02"></a>

### H02｜B2：领取成功后前置查询失败，状态没有收口

1. **背景：**请求身份用于识别同一业务提交；每次实际评价 attemptId 用于判定哪个执行者拥有写权，二者不能混同。
2. **发现：**历史故障注入在领取提交后让查下一道 MAIN 抛错；同键重试长期遇到 EVALUATING。
3. **复现：**独立 MySQL 连接观察领取已提交，再注入 findNextPendingMainQuestion 异常；不是制造真实数据库断网。
4. **根因：**领取短事务已把 Answer 置 EVALUATING 并绑定 attempt，但下一题查询在 try 外，异常绕过 FAILED 回写。
5. **尝试：**扩大整个服务的长事务会把外部调用和锁放在一起；未取得执行权就盲目写失败也可能覆盖真正执行者。
6. **修复：**在确定 OWNED 且排除 REPLAY 后，把查询纳入异常保护；以原 attempt 条件写 FAILED，回写异常作为 suppressed，保留原始异常。提交 7d25981；短事务是无外层事务时的 REQUIRED，并非宣称使用 REQUIRES_NEW。
7. **验证：**历史改前 44/7/0/0，修复 44/0/0/0，业务 296/0/0/0，全量 1559/0/0/19；其中三项由独立 MySQL 连接验证提交状态。
8. **结果：**该已领取故障可受控失败，并可按现有状态规则重新尝试。
9. **风险：**进程硬退出、数据库长时间不可写或失败回写本身失败仍可能留下执行态，不能声称全部自动恢复。
10. **学习：**阅读 Workflow 的 claimOrRecoverEvaluation、markEvaluationFailed、事务 Service 和 Answer Mapper 条件更新。
11. **简历：**可讲“围绕执行权缩小失败收口边界”；追问请求幂等键和 attemptId 的区别。
12. **证据：**[B2 验收](../verification/B2_POST_CLAIM_FAILURE_RECOVERY_20261111.md)、[事务 Service](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java)、[Answer SQL](../../src/main/resources/mapper/InterviewAnswerMapper.xml)、[工作流测试](../../src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowServiceTest.java)。

<a id="h03"></a>

### H03｜B3：报告必须对每道 MAIN 恰好使用有效 FINAL

1. **背景：**无追问时 MAIN 评价直接 FINAL；有追问时主答 INITIAL、追答综合评价 FINAL。报告和弱项只使用 FINAL。
2. **发现：**B3 审查发现旧查询以 INITIAL 兜底并用窗口排名掩盖重复 FINAL；首修后已有报告但非 READY 的恢复分支仍可能绕过检查。
3. **复现：**隔离 MySQL 构造缺失、重复、错绑、追问归属不匹配和恢复路径；单看条数可出现“数量正确、集合错误”。
4. **根因：**数据库查询提前隐藏坏数据，Java 又未完整对照计划 MAIN 集、答案状态和追问归属。
5. **尝试：**只依赖 COUNT 或窗口取第一条会掩盖不一致；对已有 READY 历史数据直接重算会产生未经授权的历史变更。
6. **修复：**SQL 只取 FINAL 并保留重复以供拒绝；Java 校验 MAIN 唯一集合、顺序、已答及最终答案绑定；报告、弱项、READY 在事务中提交。恢复路径复用校验；已 READY 历史保持可读。提交 6de9e7a；当前未提交 MQ 版本将聚合搬到 FinalReader。
7. **验证：**历史改前 55/14/0/0；恢复旁路 2/2/0/0；扩展 134/0/0/0、业务 320/0/0/0、全量 1589/0/0/19；真实 MySQL 专项 24 项，模型替身。
8. **结果：**指定损坏和恢复路径被拒绝，合法 FINAL 用于聚合；历史数据没有被批量修复。
9. **风险：**结构一致不代表模型语义正确；历史已 READY 报告与弱项是否正确仍需单独审计。
10. **学习：**从 AnswerEvaluationMapper.xml 到 InterviewReportFinalReader.aggregateFinalEvaluations，再看 UserWeaknessService 和最终事务。
11. **简历：**可讲“以集合不变量而非数量判断报告输入，覆盖恢复旁路”；追问事务回滚、历史兼容与 FINAL 唯一性。
12. **证据：**[B3 验收](../verification/B3_REPORT_FINAL_CONSISTENCY_20261111.md)、[FINAL Reader](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportFinalReader.java)、[SQL](../../src/main/resources/mapper/AnswerEvaluationMapper.xml)、[真实 MySQL 测试](../../src/test/java/com/kun/aiinterview/interview/service/InterviewReportFinalConsistencyIntegrationTest.java)。

<a id="h04"></a>

### H04｜DeepSeek、Embedding 与 Milvus：分类、日志和重试边界

1. **背景：**模型、Embedding 与向量库都是外部依赖，网络失败、上游正文和 SDK 内部重试可能影响延迟、安全和重复副作用。
2. **发现：**历史审计发现固定对外 502 不妨碍异常 cause/日志泄露上游正文；嵌套异常会掩盖真正超时；SDK 的内部尝试与单次 RPC deadline 也不等于整链预算。
3. **复现：**Mock HTTP 注入非 2xx、响应解析和嵌套超时；Milvus 用本机 gRPC/真实 JAR 与 Mock SDK 分别检验，不能混同真实网络或生产集群。
4. **根因：**HTTP/Jackson 异常及任意上游字段可能进入日志；Spring AI/SDK 有自身转换与重试边界；请求超时不证明服务端停止或不收费。
5. **尝试：**仅替换客户端消息或关闭全局堆栈不能消除异常链；直接自动重试未知写入或未知付费结果可能重复调用。
6. **修复：**DeepSeek 在非 2xx 边界拒绝危险正文、采用固定分类并有界检查异常类型；Embedding 在框架转换前控制安全错误并做向量校验；Milvus 缩小 SDK 尝试次数、限制初始化并拒绝异常 null。相关提交 f764c08、01ac774、f71da96、71aeb22。
7. **验证：**历史 DeepSeek 日志专项 281/0/0/0、全量 1635/0/0/19；超时专项 136/0/0/0、全量 1698/0/0/19，本机 600 ms 等待约 604–607 ms。Embedding 专项 195/0/0/1、全量 1832/0/0/19；Milvus 专项 180/0/0/0、全量 1732/0/0/19。均为不同历史运行。
8. **结果：**指定异常分类和脱敏边界得到验证；不是整网 SLA 或全局日志安全证明。
9. **风险：**本地超时后外部结果未知；只对具备可重放身份、无未知副作用或经业务幂等保护的操作考虑有界重试。不可把模型/向量未知结果直接当“未发生”。wire DEBUG、APM 和独立创建的 SDK 客户端需另审。
10. **学习：**读 DeepSeekEvaluationClient.completeJson、EmbeddingOpenAiApi、SpringAiEmbeddingClient.embed、MilvusConfiguration.milvusClient；对照 cause 与日志。
11. **简历：**可讲“用失败分类与脱敏把上游错误限制在可信边界”；追问超时、未知结果和重试代价。
12. **证据：**[DeepSeek 日志](../verification/B4B_DEEPSEEK_LOG_SAFETY_20261112.md)、[超时](../verification/B4C_DEEPSEEK_TIMEOUT_RETRY_20261112.md)、[Milvus](../verification/B4D_MILVUS_RETRY_DEADLINE_20261113.md)、[Embedding](../verification/B5_P2B_EMBEDDING_LOG_SAFETY_20261113.md)、[日志测试](../../src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekLogSafetyTest.java)。

## B｜真实题库、组卷与 RAG

<a id="h05"></a>

### H05｜D1/D2：扩充题库与分类内随机组卷

1. **背景：**面试必须从固定题库出 MAIN，创建时保存全部题目及评分标准快照，不能由模型临时生成或中途重新选题。
2. **发现：**原选择器固定取排序靠前题目，题库扩容不自动实现随机；旧对话给出 63 题、298 点基线。
3. **复现：**本轮离线读取 D1 冻结 JSON，实际 63 题、298 点，EASY/MEDIUM/HARD 15/21/27；不是当前线上数据库实时盘点。
4. **根因：**数据规模与抽样算法是两个独立条件；如果不快照，后续修改题库会改变正在进行或历史面试的评分依据。
5. **尝试：**直接 SQL 随机排序和共享可变随机状态会增加测试与维护负担；跨场永不重复并非项目承诺。
6. **修复：**QuestionPlanSelector.select 按计划类别分桶、ID 去重、稳定排序后抽样，生产使用 ThreadLocalRandom，测试注入随机索引；InterviewQuestionPlanSnapshotService.prepareSnapshot 持久化题干、答案和评分点快照。提交 d090d25。
7. **验证：**历史 D1 定向 113/0/0/0；D2 定向 268/0/0/0，真实 MySQL 29 个回滚探针，包括 24 串行、3 并发、空桶和旧快照；固定种子 300 次诊断为 99/99/102。
8. **结果：**历史测试目标覆盖抽样和快照不变，回滚探针不证明独立提交可见性或长期分布。
9. **风险：**题目难度效度、未来跨场不重复和真实使用下分布尚未由这些数据证明。
10. **学习：**从选择器 select 到 prepareSnapshot，再看 session 创建、评分上下文构造与持久化 SQL。
11. **简历：**可讲“扩充有权重的题库并对随机选择和历史快照分别验证”；追问种子、去重和快照一致性。
12. **证据：**[D1 数据](../question-bank/D1_QUESTION_BANK_V2_20261114.json)、[D1 验收](../verification/D1_QUESTION_BANK_EXPANSION_20261114.md)、[D2 报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-D2-随机组卷与真实数据库验收.md)、[选择器测试](../../src/test/java/com/kun/aiinterview/interview/service/QuestionPlanSelectorTest.java)。

<a id="h06"></a>

### H06｜隔离环境与 RAG：命中不等于证据相关

1. **背景：**RAG 在回答后检索 Java 知识作为评价证据；MySQL 保存业务事实，Milvus 保存向量和映射标识。
2. **发现：**D3 的 Hit@1 看似很好，但直接相关证据比例较低；历史测试也出现首次失败，不能只选成功指标。
3. **复现：**历史 E1–E3 与 D3 在隔离 MySQL/Milvus 中导入、真实 Embedding/检索；生产组 63 MAIN、每题选入 5 条，另有公开集和独立集。标签主要来自临时 AI 判断，缺独立人工 gold。
4. **根因：**命中至少一条与五条全部相关是不同问题；向量相似、MySQL 可用性过滤和 Prompt 证据选择各有边界，且不能单凭相关性推断评分质量因果。
5. **尝试：**D3-E 比较多种更强过滤：B 丢失公开集五条原有正例命中，C 漏掉部分跨主题机制，D 虽在极少数非空结果上呈现高精度，却让公开集 59/60 查询为空、生产 MAIN 丢失七个 CORE。没有候选同时满足相关率、覆盖和无答案拒绝，因此没有强行替换生产规则。
6. **修复/现状：**保留既有检索路径和环境隔离校验；检索 query 不含用户答案，实际入 Prompt 的合格证据才持久化 hit trace。历史 51 READY 文档、52 ACTIVE chunk/向量是当时快照；语料、工具和验收材料分别见本地提交 9b7e356、854b9ea、921f8cd，不能据此虚构检索算法修复提交。
7. **验证：**历史生产 MAIN Hit@1 63/63，但直接相关 75/315=23.81%；公开 67/300=22.33%，独立 21/120=17.50%，独立正例 Hit@5 15/18。直接相关率是实际送入 Prompt 的 query–chunk 微平均精度，包含无答案查询，不是评分点覆盖率；标签主要是 AI 临时判断。D3 定向 103/0/0/0；并非本次重跑。
8. **结果：**揭示证据精度风险，不能用高命中证明 RAG 已改善评分。旧 E2E 首次 42 次答案中 1 次 500、5 次报告中 2 次 502；历史恢复不抹去首次失败，原始失败 JSON 缺失，原因不可臆测。
9. **风险：**标签偏差、样本有限、语料版本、独立集与真实人群差异，以及 SQL/向量的跨系统一致性仍限制推广；不能假定当前数据库数量仍相同。
10. **学习：**阅读 EnvironmentIsolationInitializer、EvaluationRagQueryBuilder.build、KnowledgeRetrievalService.retrieve、EvaluationRetrievalAdapter.retrieve 与 PromptBuilder。
11. **简历：**可讲“用隔离数据和反例区分检索命中与证据相关”；追问负样本、人工 gold 和离线指标边界。
12. **证据：**[隔离验收](../verification/E1_E3_REAL_DATA_EXECUTION_20261113.md)、[D3 验收](../verification/D3_BCD_FINAL_ACCEPTANCE_20261114.md)、[证据质量复核](../verification/D3E_EVIDENCE_QUALITY_REVIEW_20261114.md)、[E2E 报告](../evaluation/E2E_EVALUATION_REPORT.md)、[检索测试](../../src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalServiceTest.java)。

## C｜前端体验与数据可见性

<a id="h07"></a>

### H07｜U1/U2：题目表达、等待反馈与已保存评价恢复

1. **背景：**候选人需要清晰题干和等待提示；评分标准用于后台评价，不应混成提示答案；用户刷新后仍应看到已有反馈。
2. **发现：**U1 真人反馈指出部分题意/预期不清；U1-R1 的响应丢失与重启组合场景暴露上一题反馈恢复缺口，不等于所有单次刷新都失败。
3. **复现：**历史真实 Chrome 在服务端已提交并推进后丢弃成功响应，刷新、重启前后端及全新浏览器登录；旧页面本地记录按旧题，而恢复按当前题查找。
4. **根因：**业务成功与 HTTP 响应送达不是同一事件；新浏览器没有原设备内存或本地提交标志。集合题旧表述把保序、去重、按 ID 保存最后结果和首次顺序混在一段；ACID 题的题干要求和参考标准对隔离级别等拓展的边界不够清楚，容易让候选人不知必答范围。
5. **尝试：**重复提交答案或再调模型会带来重复费用和状态风险；新增接口并非必要。
6. **修复：**使用已有受归属保护的详情，按顺序选当前题之前最近的 EVALUATED 结果并复用组件；账号、Session、当前题、生命周期隔离旧响应。集合题分行明确保留全部结果、ID 去重、同 ID 最后结果和首次顺序；ACID 题明确基本含义、事务用途与不要求的隔离级别/日志细节。U1-R1 在用户确认窗口经正式 Service 更新两题并独立读回，旧会话继续使用旧快照；功能提交 f1fb02b。
7. **验证：**U1 的 8 对合成答案真实模型对照为 16/16 HTTP 200、旧 7/8 新 8/8 通过 Java 校验，但仍存在漏认和部分覆盖争议；U1-R1 实际 Chrome 测试双击仅提交一次、受控丢响应后同键恢复。历史 U2 后端定向 165/0/0/0、全量 1848/0/0/19；前端 346/0/0/0。U2 真实浏览器恢复无新增答案 POST/模型调用；该 U2 真实场次无追问，追问覆盖来自模型替身测试。
8. **结果：**指定刷新/重启/重新登录场景能取回同一持久化反馈；U1 小样本答题 n=4 P50 3.105 秒、报告 n=1 为 3.147 秒，不能当稳定分布。
9. **风险：**前端恢复不等于模型评分正确；并发切题、跨账号与追问仍依赖相应边界测试。
10. **学习：**读 PreviousInterviewFeedback.vue、InterviewService.getDetail、InterviewSessionView.spec.ts。
11. **简历：**可讲“响应丢失后从持久化业务结果恢复体验，避免重算”；追问安全读取和迟到响应。
12. **证据：**[U1 反馈](../verification/U1_MANUAL_FEEDBACK_REVIEW_20261114.md)、[U2 验收](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-U2-恢复修复与运行安全验收.md)、[反馈组件](../../frontend/src/features/interview/components/PreviousInterviewFeedback.vue)、[前端测试](../../frontend/tests/InterviewSessionView.spec.ts)。

<a id="h08"></a>

### H08｜S1：详情 API 不得泄露未来题目

1. **背景：**创建时必须预存全套题目，但候选人只能看到当前题和允许查看的已答历史。
2. **发现：**历史真实 HTTP 新建 EASY 后直接返回 5 道 MAIN，其中 4 道是未来题；隐藏前端组件不能保护 API。
3. **复现：**以有权用户直接请求 session detail；另用匿名、普通用户和其他用户测试权限。
4. **根因：**存储全量计划与对外可见集被混为一谈，详情返回未作答题。
5. **尝试：**只在 UI 不渲染仍能从响应读取；删除历史快照会破坏重放和评分依据。
6. **修复：**InterviewService.getDetail 先校验归属，再只返回当前题及有答案的 ANSWERED 历史；独立 VO 不暴露参考答案和评分点快照。提交 4bed0e2。
7. **验证：**历史定向 179/0/0/0、U2 前端 61/0/0/0；首次全量 1850/8/0/19 因测试 MySQL 时区不一致，校正后 1850/0/0/19。S1-R1 隔离 evaluation 的真实 HTTP/浏览器验证当前题、已答历史及 401/403/404。
8. **结果：**历史安全局部门禁 GO；未证明原样本发生跨用户泄露，也未发现评分点泄露。后续历史记录显示 S1 在 evaluation 有受控切换。
9. **风险：**安全局部 GO 不代表评分质量 GO；运行实例身份必须按各次构建单独核查，本轮未探测服务。
10. **学习：**看 getDetail 的所有权和过滤，再看独立 VO 与 InterviewVisibilityHttpIntegrationTest。
11. **简历：**可讲“保留持久化计划，同时缩小候选人 API 可见范围”；追问对象归属与历史反馈兼容。
12. **证据：**[S1 验收](../verification/S1_INTERVIEW_VISIBILITY_SECURITY_20261114.md)、[S1-R1 实例验收](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-S1-R1-题目接口安全修复部署与终验.md)、[服务](../../src/main/java/com/kun/aiinterview/interview/service/InterviewService.java)、[HTTP 测试](../../src/test/java/com/kun/aiinterview/interview/controller/InterviewVisibilityHttpIntegrationTest.java)。

## D｜评分质量研究与失败候选

<a id="h09"></a>

### H09｜Q1：12 张风险卡的独立技术复核

1. **背景：**结构合法、五维分数可相加，并不等于模型理解答案或纠错正确。
2. **发现：**用户独立查看 12 张风险挑选卡，第一轮主要给总体意见，未逐点完整标注；后续工程复核和解盲发现争议。备份前曾读取解盲索引，不能称双盲金标准。
3. **复现：**Q1 真实评分和合成业务面试保留评分快照；风险分层选 12 张，工程审阅逐卡对照原答、模型逐点结果及技术反例。本轮只读复核 JSON 为 12 卡。
4. **根因：**自由语言同义、可选实现、复合要求和额外错误会混杂；模型可能把已答误列遗漏，也可能肯定额外错误。原因不能一概归罪 RAG。
5. **尝试：**只看总体分、HTTP 200 或模型自评会漏掉事实问题；独立审核仍应保留人与人的分歧而非事后改原盲卡。
6. **修复/现状：**没有完成模型质量修复。后续用户认可 ACID 含义与名称应分开，名称缺失可按部分覆盖；卡 09 用途可被认可，C/I/D 的明确技术错误仍需各自指出。
7. **验证：**历史 MySQL SQL 反例 ON 返回 1、2，WHERE OR 仅返回 2；Java 21 的 javac --release 8 拒绝接口 private、--release 9 接受。工程复核卡 01、03、04、06、07、08、11 共 7/12 至少有一项可确认输出问题，类别可重叠。
8. **结果：**7/12 是风险挑选样本的工程口径，不是总体错误率或 5/12 准确率；用户总体意见也不是逐点人工 gold。
9. **风险：**卡 01 四项含义正确但缺正式名称；卡 09 有用途但 C/I/D 存错；卡 06 LEFT JOIN 主方案正确、额外 WHERE 变体可丢行；卡 08 的 removeIf 已答却被误判；卡 04 Java 版本纠错错误；卡 03 已答 Spring 风险被列遗漏而另一子项未答；简短正确答案不能仅因短判漏。每一项须按题干与事实独立判定。
10. **学习：**区分事实错误、遗漏和部分覆盖；读 validator、Java 求分、FollowUpPolicy，再手写 SQL 反例并对照盲卡。
11. **简历：**可讲“用技术反例和人工复核审查 AI 评价”；追问抽样偏差、事实与覆盖、模型自评偏差。
12. **证据：**[解盲报告](../evaluation/q1-r2-20261114/UNBLIND_REPORT.md)、[工程争议](../evaluation/q1-r2-20261114/CONFIRMED_ISSUES_AND_DISAGREEMENTS.md)、[后续用户复核](../evaluation/q2-20261114/USER_TECHNICAL_REVIEW.md)、[SQL 反例](../evaluation/q1-20261114/LEFT_JOIN_COUNTEREXAMPLE.sql)。

<a id="h10"></a>

### H10｜Q2：v5 Prompt 候选退化并撤回

1. **背景：**针对名称、等价答案、复合点及额外错误，尝试最小 Prompt 改动。
2. **发现：**真实旧样本对照发现新退化、既有漏判和非法 JSON。
3. **复现：**14 个已揭盲旧样本，其中四个有/无历史证据配对，共 18 次 DeepSeek 调用，每臂一次，非独立留出集。
4. **根因：**候选修改了提示合同，但模型仍可误归因或不遵守 JSON；单次观察不足以确定模型内部原因或普遍因果。
5. **尝试：**v5 某事务用途单次改善，ACID 一致性退化、removeIf 持续漏认；不能靠重试或修补 JSON 隐藏失败。
6. **修复/决策：**撤回 v5，保留候选 patch 和对照；当前源码 Prompt 常量为 v4，没有 v5 修复提交。
7. **验证：**18 次 HTTP 200 且有 usage，17 次解析及 Java 校验通过、1 次语法失败；历史 Prompt 定向改前 43/0/0/0、候选 44/0/0/0、恢复后 43/0/0/0。Mockito 自附加导致扩展测试 87/0/20/0，配置项目 agent 后 87/0/0/0。
8. **结果：**候选未通过，保留生产 v4；HTTP 成功与结构通过均不是质量 GO。
9. **风险：**单臂单次、已揭盲样本、无新独立人工留出，不能断言准确率或稳定修复；1 次 JSON 失败未证明由截断造成。
10. **学习：**读 EvaluationPromptBuilder、DeepSeekEvaluationClient、LlmEvaluationValidator 和候选 patch。
11. **简历：**可讲“对照失败后撤回 Prompt 并保留负面证据”；追问测试污染、结构校验和采样波动。
12. **证据：**[Q2 诊断](../evaluation/q2-20261114/DIAGNOSTIC.md)、[候选补丁](../evaluation/q2-20261114/CANDIDATE_V5.patch)、[逐项对照](../evaluation/q2-20261114/COMPARISON.json)。

<a id="h11"></a>

### H11｜Q2-R1：二值评分契约与三种方案

1. **背景：**评价模型返回评分点 covered、证据、missingPoints 和五维分数；Java 再决定总分与追问。
2. **发现：**源码和离线契约审查发现二值字段无法明确区分部分覆盖与完全未答，复合 CORE 点可能放大追问影响。
3. **复现：**人工预先给定子项布尔值，比较 ALL/ANY 映射与文字来源；离线脚本不是让模型对真实答案重新评分。
4. **根因：**covered 为 Boolean；true 要求 evidence 非空，但 false 不强制有证据，程序不验证证据是否来自答案、是否支持结论或与 missingPoints 矛盾。五维由模型建议，Java 求和；权重/covered 不是总分直接公式。
5. **尝试：**A：修订少量评分文字、继续二值，成本低但不保证识别正确；B：内部细化必答 ALL、可选 ANY，再折算二值，需稳定 ID、版本和可恢复存储；C：引入 PARTIAL 新契约，能表达更多但影响 DTO、持久化、追问、重放和回滚。
6. **修复/决策：**先人工审题并做 B 的离线原型，暂缓 C；历史 false 无法反推出部分还是完全未答。评分契约和生产题库未因审查修改。
7. **验证：**历史离线 Python 9/0/0/0，DeepSeek 客户端等 Mock 定向 98/0/0/0；均不证明自然语言判断准确。
8. **结果：**复合点的部分覆盖可能映射 false，而追问还需模型推荐及 Java 门槛；不是每个 false 都必然触发追问。
9. **风险：**原文片段存在也不证明技术真实；missingPoints 与评分点无严格语义关联；历史 JSON 兼容和版本化需求尚未验证。
10. **学习：**读 LlmEvaluationValidator.validateScoringPointResults、EvaluationScoreCalculator.calculate、FollowUpPolicy.decide 和 FollowUpTargetResolver.resolve。
11. **简历：**可讲“审查结构校验与语义真值的边界”；追问五维计分和旧评价如何迁移。
12. **证据：**[契约审查](/Users/kun/Documents/Reports/ai-interview-system/2026-11-14-Q2-R1-评分契约审计与离线验证.md)、[验证器](../../src/main/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidator.java)、[追问策略](../../src/main/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpPolicy.java)、[验证器测试](../../src/test/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidatorTest.java)。

<a id="h12"></a>

### H12｜Q2-R2：七条评分口径候选与网络阻塞

1. **背景：**尝试仅澄清七条评分规则，不改变权重、二值 JSON、追问或 Schema。
2. **发现：**Spring 事务传播评分点 552 的风险列举存在 ALL/OR 歧义；用户明确选择至少准确解释一种相关风险、合理机制及对应限制即可。
3. **复现：**七条候选为 ACID 五点、ArrayList 一点和 Spring 一点；计划旧新配对并冻结四条留出样本，但这些样本尚未经独立人工审核。
4. **根因：**产品评分要求不明确会与模型事实判断相混；“有风险”没有机制和限制，不能满足用户确认的口径。
5. **尝试：**计划 40 次对照，第一条旧规则调用即异常；后续无凭据 DNS/HEAD 预检无法解析。DNS 发生于后续检查，不能证明首请求是否到达或供应商故障。
6. **修复/决策：**保存 552 OR 候选与另六点，未应用正式题库、未改旧快照或生产 v4；没有直接上复杂三态。
7. **验证：**一次尝试、0 个已记录 HTTP 响应、0 usage、0 有效新旧对照；后续预检 DeepSeek/Embedding 新调用为 0。离线逐字段与计划一致性通过，不代表模型效果。
8. **结果：**没有有效对照，候选不可宣称有效；未知费用只作预算预留，不是假定零费用或实际账单。
9. **风险：**正式整题替换可能重建评分点 ID，评分点正文也参与 RAG query；即使只改文字，也需要版本、检索和历史 replay 验证。
10. **学习：**对照题库更新、EvaluationRagQueryBuilder 与 FollowUpTargetResolver；自行把必答、可选与限制画成规则。
11. **简历：**可讲“先冻结产品口径，在网络受阻时停止无证据上线”；追问 OR/ALL、费用未知和实验设计。
12. **证据：**[Q2-R2 总结](../evaluation/q2-20261114/q2-r2/COMPARISON_SUMMARY.md)、[七条候选](../evaluation/q2-20261114/q2-r2/CANDIDATE_RULES.json)、[552 OR](../evaluation/q2-20261114/q2-r2/CANDIDATE_552_OR.json)。

## E/F｜M1-A 设计与 M1-B 实施

<a id="h13"></a>

### H13｜M1-A：为什么选择 RocketMQ 加单表 Transactional Outbox

1. **背景：**原同步 POST 等待 DeepSeek 报告完成，用户在长 HTTP 等待期间难以感知进度；但旧生成器本已在短数据库事务外，不能把它误说成一直占有数据库事务。
2. **发现：**历史浏览器报告样本仅 n=1、约 3.147 秒；足以提出体验假设，不足以估算容量或长期分布。
3. **复现：**查看原同步调用链：归属与 FINAL 校验 → 状态领取 → 事务外模型 → 报告、弱项、READY 短事务。
4. **根因：**只改成后台线程会丢失进程退出后的任务；先提交 DB 再发 MQ 有发送前崩溃窗口，先发再提交则可能消费到未提交或回滚状态。
5. **尝试：**纯 MySQL 任务表 + 有界 Worker 是单体 MVP 更少组件的可行备选；RocketMQ 事务消息需要半消息、本地事务回查、超时及消费幂等，仍不能把外部模型纳入原子事务。按明确研究范围选普通消息 + Outbox，不同时引入事务消息、CDC 或分布式锁。
6. **修复/设计：**一次 MySQL 事务写 GENERATING 和持久任务，任务表兼 Outbox；发送可重复，消费者由数据库领取 CAS、attemptVersion、executionVersion 和期限决定写权。投递 PENDING/LEASED/SENT 与任务 QUEUED/RUNNING/SUCCEEDED/FAILED 分开。
7. **验证：**M1-A 时只完成设计与独立最小兼容探针，1 次 SEND_OK/接收；探针使用 Starter，M1-B 实际采用官方 rocketmq-client 显式生命周期，不能把两者混为同一依赖。
8. **结果：**形成可审阅迁移及故障矩阵；Outbox 保证的是 DB 内“业务状态+待发记录”同提交，在扫描器恢复、数据库和 Broker 等条件满足时有界派发，不构成 DB+MQ 分布式原子提交。
9. **风险：**新表、扫描、Broker、死信和运维增加成本；排队与执行期限必须分离。M1-A 时未经具体 DDL 批准，后续 M1-B 的批准仅适用其具体测试目标。
10. **学习：**先比较旧同步版本 git show 84e3489:src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java，再看任务表、Dispatcher 和 Consumer。
11. **简历：**可讲“比较数据库 Worker、事务消息与 Outbox 后选择可追溯任务”；追问双写窗口、回查和重复投递。
12. **证据：**[M1-A 设计](2026-11-14-M1A-ROCKETMQ-ASYNC-REPORT-DESIGN.md)、[M1-B 准入](2026-11-14-M1B-DATABASE-ADMISSION.md)。同步版可从本地提交 84e3489 只读查看。

<a id="h14"></a>

### H14｜M1-B：受理、派发、消费与最终事务

1. **背景：**异步 POST 需要快速返回 GENERATING，又须使已接受任务在进程退出后可追踪，并保护最终报告和弱项。
2. **发现：**设计故障矩阵要求覆盖 DB 提交前后、发送回执未知、重复消息和最终提交；测试又暴露消费者可能比生产者记 SENT 更快。
3. **复现：**历史独立 MySQL + RocketMQ，以模型替身触发真实重复投递、租约竞争、Broker 停复、DLQ 与数据库回滚；不等于生产多副本。
4. **根因：**“已发送”与“已执行”不是同一进度；单靠消息唯一或 session_id 唯一无法处理旧执行者和迟到回执。
5. **尝试：**原同步入口和旧 GENERATING stale 接管会绕过任务围栏，不能与新入口并行；发送结果不确定时需要同一事件重发，而非假装 DB 与 MQ 原子。
6. **修复：**迁移新增 interview_report_task，以 session_id+attempt_version 唯一；分别建立 pending、lease、queue_expiry、execution_expiry 索引和任务/投递 CHECK。受理时 Session CAS 与插入任务同事务；Dispatcher 租约发普通消息；Consumer 领取时取固定 executionVersion；模型在事务外；完成时 Report、Weakness、Session READY、Task SUCCEEDED 同事务。markSent 仅按 token 更新投递字段；删除旧同步生产旁路。
7. **验证：**历史后端最终 1923/0/0/23（1900 实际通过）、前端 341/0/0/0；真实 MySQL 专项 45/0/0/0，真实 MQ+MySQL 指定方法 2/0/0/0，模型均为替身。首次 DLQ 探针 2/0/2/0 为 CLI 格式误判，修工具后 2/0/0/0；全量早期受测试连接池耗尽影响，调整测试池后恢复，未削弱断言。
8. **结果：**历史覆盖内具备持久受理、重复消息保护及最终原子提交；约 53 个文件的旧摘要缺精确同批边界清单，只能保留为待核证的历史说法。
9. **风险：**M1-B 当时未真实杀进程、丢弃网络 ACK、停共享 MySQL 或验生产长期积压；DLQ 不是业务 FAILED 的同义词。未正式部署，不能宣称零丢失或模型 exactly once。
10. **学习：**依次读 acceptReportTask → insertQueued → dispatchDue/claimDispatch → onMessage/claimTaskExecution → generateClaimedReport → completeReportGeneration，逐项核对 SQL 行数。
11. **简历：**可讲“将受理、投递与执行业务状态分离，并用事务和版本控制重复消息”；追问投递 token 与 executionVersion 的职责。
12. **证据：**[M1-B 验收](2026-11-14-M1B-ROCKETMQ-ASYNC-REPORT-VERIFICATION.md)、[迁移 DDL](../../sql/migrations/20261114_interview_report_task.sql)、[任务 SQL](../../src/main/resources/mapper/InterviewReportTaskMapper.xml)、[MQ 测试](../../src/test/java/com/kun/aiinterview/interview/service/RealReportRocketMqSmokeTest.java)。

## G/H｜M1-C 故障、退化和归因

<a id="h15"></a>

### H15｜跨用户锁等待与 MyBatis 一级缓存

1. **背景：**用户申请报告时需要校验 Session 所属；无权请求应尽快按不存在处理，同时有效请求仍要在锁内验证状态。
2. **发现：**真实 MySQL 并发故障测试发现非所有者会等待别人 Session 的行锁；第一次修复后归属变化测试又揭示旧缓存。
3. **复现：**事务 A 持有目标 Session 行锁，非所有者 B 请求，修复前定向 1/0/1/0（2 秒等待超时）；再让合成 Session 的 owner 在锁等待期间变化，首修 1/1/0/0。
4. **根因：**原代码先按主键 SELECT version FOR UPDATE 再校验 userId，造成无权请求争抢目标行；首修在一个 SqlSession 内锁前后复用相同查询，MyBatis 一级缓存可能返回锁前快照。useCache=false 主要不是关闭一级缓存的开关。
5. **尝试：**只做锁前授权存在 TOCTOU：预查到加锁之间数据可能变化；全局禁缓存或只改隔离级别不是必要的最小方案。
6. **修复：**以 getReportOwnerId 普通查询预检，失败即拒绝；有权者按 Session→Task 锁序持有目标行后，用不同 Mapper 语句 getReportSession 重新读取并校验 owner。报告事务使用 READ_COMMITTED；新 SQL 语句避免复用原查询的本地缓存，不能脱离事务隔离和锁单独宣称数据永远新鲜。
7. **验证：**历史真实 MySQL 两种并发测试和相关定向 75/0/0/0；最终后端全量 1938/0/0/34。两次失败现场保留，最终版本又重跑 5 组进程故障场景。
8. **结果：**目标行被锁时非所有者及时拒绝；模拟归属改变时旧所有者拒绝。此测试不证明线上曾发生真实跨用户数据泄露，也未假设有会话转让功能。
9. **风险：**只证明指定交错和目标行锁范围，非高并发恶意压测；若其他入口绕过锁后复核仍需单独检查。
10. **学习：**看 acceptReportTask 和 lockSession、Session Mapper 中三个 SELECT，再对照 ReportOwnershipLockIsolationTest 的两个线程。
11. **简历：**可讲“真实 MySQL 发现无权请求锁等待，首修再暴露 MyBatis 一级缓存，最终通过锁前预检与锁后新查询复核解决”；追问 TOCTOU 和隔离级别。
12. **证据：**[故障记录](2026-11-15-M1C-FAILURE-INJECTION.md)、[事务源码](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java)、[Mapper SQL](../../src/main/resources/mapper/InterviewSessionMapper.xml)、[真实 MySQL 并发测试](../../src/test/java/com/kun/aiinterview/interview/service/ReportOwnershipLockIsolationTest.java)。

<a id="h16"></a>

### H16｜消费者崩溃、未知结果与无依据弱项

1. **背景：**报告任务经过 MQ、外部模型及数据库事务，崩溃发生在不同位置意味着完全不同的业务状态。
2. **发现：**M1-C 进程故障注入和两次真实模型报告分别暴露恢复限制、内容质量缺口。
3. **复现：**模型替身返回后、最终报告事务前杀测试 JVM；以缩短期限和显式触发过期模拟执行超时。另一组在 DB 成功提交但 consumer 尚未返回 ACK 时杀进程并观察真实 Broker 重投。
4. **根因：**模型响应不等于业务提交；领取中的旧任务不能由重复消息重新执行，执行权超时后才受控失败。报告 Prompt 只含题目与 FINAL 反馈，若输入缺证据，模型仍可能编造具体欠缺；JSON 合法无法证明事实来源。
5. **尝试：**自动无限重试会产生不可控外部费用和重复调用；把无依据报告归咎于 MQ 无法修复语义。ACK 丢失式重投与模型成功后未提交是两种不同窗口。
6. **修复/现状：**超期旧任务 FAILED，用户显式重试创建新 attempt；旧版本不能覆盖新事务。DB 已 READY/SUCCEEDED 的重投经终态检查不再调用模型。内容问题未修复。
7. **验证：**历史最终版 5/5 进程故障场景达到各自断言，杀死进程的非零退出不是 JUnit 通过；旧执行者真实 MySQL 四交错 4/0/0/0。模型返回后崩溃案例两次桩调用、1 报告和 1 次弱项；两次真实模型的空反馈输入却出现无依据的 HashMap 具体弱项。
8. **结果：**已提交后重投与未提交后显式重试的边界得到场景级证据；后者不自动 READY，外部模型不能承诺恰好一次。
9. **风险：**真实 DeepSeek 成功后 kill、JDBC 提交结果未知、真实 MySQL 停机、Broker 永久损坏、长期积压及生产多副本未完整实测；内容发布 NO-GO。
10. **学习：**追踪 claimTaskExecution、failExecution、expireTask、finishSuccess 条件，再逐个画崩溃窗口与 ACK。
11. **简历：**可讲“通过故障注入验证最终数据库提交的幂等与显式失败恢复”；追问费用、重复外呼和消息确认。
12. **证据：**[M1-C 故障矩阵](2026-11-15-M1C-FAILURE-INJECTION.md)、[故障探针](../../src/test/java/com/kun/aiinterview/interview/service/RealReportCrashWindowProbeTest.java)、[安全合成报告证据](m1c-20261114/real-report-evidence.json)、[事务 Service](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java)。

<a id="h17"></a>

### H17｜M1-C：异步受理变快，完整 READY 和吞吐退化

1. **背景：**需要验证异步方案是否真正改善用户获得报告的时间，而不是只量 POST 返回。
2. **发现：**M1-C 在所有权锁最终修复后，按同一实验协议复测，同步/异步结果出现明显反例。
3. **复现：**本地固定 1 秒模型桩、客户端并发 10、每配置每组 30 条，两轮各 15；A 为同步 POST 完成即 READY，B 为异步 POST 返回 GENERATING，B 以 200 ms GET 轮询观察 READY；闭环客户端等待完成后才发下一条。
4. **根因：**受理语义不同；B 存在派发等待、Broker/调度、有限消费者线程、排队、额外一致性检查及轮询，不能全部归因于 MQ 本身。
5. **尝试：**对照仅 POST P95 会得到误导性结论；真实浏览器另以当时 3 秒轮询独立实验，不能与 HTTP 样本混算。
6. **修复/现状：**该实验保留原默认参数与真实退化；后续 R1/R2 再分解瓶颈，未删除一致性或权限校验。
7. **验证：**A/B POST P95 1046.5/24.9 ms；客户端观察 READY P95 1046.5/6967.8 ms；闭环完成吞吐 7.252/1.489 次/秒。30 条是小样本，固定模型桩而非真实 DeepSeek。
8. **结果：**B 受理更快，但完整 READY 更慢、完成率吞吐更低；HTTP 九组均呈该方向。闭环吞吐受下一次到达依赖影响，不是开放式固定到达容量。
9. **风险：**两边背景 Session 行数和运行资源不完全相同；原样本未逐条哈希完整模型请求体。不能推断生产 QPS、费用或稳定 P95。
10. **学习：**读原 requests.csv、statistics.csv 和 benchmark.py；区分 API latency、任务提交、完成与客户端观测时间。
11. **简历：**应主动讲退化而非声称“MQ 提速”；追问为什么吞吐下降、P95 口径和闭环/开放式区别。
12. **证据：**[最终版 A/B](m1c-20261115-after-fix/AB_RESULTS.md)、[原始指标](m1c-20261115-after-fix/statistics.csv)、[浏览器独立结果](m1c-20261115-browser/BROWSER_RESULTS.md)、[负载工具](../../tools/m1c/benchmark.py)。

<a id="h18"></a>

### H18｜M1-C-R1/R2：分离并发、扫描与轮询的最小调优

1. **背景：**H17 不能只归因“RocketMQ 慢”，需要先分离执行并发和任务入队等待。
2. **发现：**R1 用原始请求区间推导：固定一秒桩、并发 10 时 A 模型在途峰值约 10，B 单实例两条消费执行线程约 2；这是区间约束推导，不是历史直接计数器。
3. **复现：**并发 1 时两侧模型并发均为 1，B queued→started P50 仍约 874.1 ms，原扫描周期 2000 ms；该区间还包含 Broker 和调度，不能全算扫描。
4. **根因：**I/O 等待期间有限消费线程限制并行在途；固定延迟扫描会增加等下一次扫描的时间。线程加大受模型、数据库、CPU、连接池和服务商限额制约；扫描变快也增加数据库查询。
5. **尝试：**R2 新探针直接记录阶段和桩在途，以 2/4/8 线程、2000/500 ms 等有界单变量对照；浏览器覆盖工具和退出清理曾失败，修正后重测，失败样本保留。
6. **修复：**默认消费线程 2→4，Outbox 扫描 2000→500 ms；保留 8 为已测候选上限而非默认，保留 CAS、FINAL、所有权与事务围栏。
7. **验证：**同 R2 20 条闭环协议，客户端完成速率 1.775→3.531 次/秒，READY P95 6067.4→3563.6 ms；定向 131/0/0/0，全量 1938/0/0/34，真实 MQ 两项 2/0/0/0，模型为桩。结果不能与 H17 的 30 条实验混算。
8. **结果：**调优改善受控样本，但完整 READY 仍可能慢于同步；理论扫描 SQL 频率约 2→8 条/秒，模型并发也增加。
9. **风险：**小样本、单实例与桩无法证明长期生产容量或成本收益；R2 没有完整重做崩溃矩阵或真实模型质量。
10. **学习：**读 ReportAsyncProperties、ReportOutboxDispatcher、R2 task-timeline 和 stage-attribution；不要用两个不同任务的 P95 相减推导阶段耗时。
11. **简历：**可讲“先分解等待、再有限调参并同时报告代价和剩余退化”；追问 I/O 并发、背压和数据库扫描压力。
12. **证据：**[R1 推导](M1C_R1_BOTTLENECK_REVIEW.md)、[R2 索引](m1c-r2-20261115/README.md)、[阶段归因](m1c-r2-20261115/stage-attribution.json)、[配置](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportAsyncProperties.java)。

## I/J｜报告策略与最近优化

<a id="h19"></a>

### H19｜M1-D：快并不自动等于报告质量足够

1. **背景：**逐题 FINAL 已包含评价，再调用 DeepSeek 综合报告需要额外等待和费用；比较是否值得保留。
2. **发现：**同三组合成完整面试的真实对照中，精简报告遗漏弱项或丢失主答/追问限定，规则报告虽然很快却综合建议不足。
3. **复现：**完整 DeepSeek、精简 v1 各 3 次，精简 v2 实际 2 次；规则原型经现有 MQ 使用相同来源。总共 8 次真实 DeepSeek，非新业务生产请求。
4. **根因：**压缩不能保证模型保留语义范围；结构约束与文本事实是不同关卡；固定 5 项上限和简单排序会挤掉重要非首条遗漏。
5. **尝试：**完整模型调用耗时中位约 4943 ms，精简 v1 约 3536 ms，但某场更慢；v2 有 1 次输出列表超限 FAILED。Java 原型生成器中位约 0.658 ms，但建议机械，三场选中遗漏 5/11、5/10、4/4；不以更快直接替换。
6. **修复/决策：**保留完整 DeepSeek 报告；仅把前端轮询由 3 秒改为 1 秒。精简/Java 生成器仍是工具原型；不提前写 READY。
7. **验证：**历史 3.416 秒模型时长桩浏览器回放各 2 条，页面可见中位 6.10365→4.0978 秒，GET 2→4；后端全量初次 1938/125/0/34 因隔离夹具题目状态错误，修夹具后 1938/0/0/34；前端 341/0/0/0、真实 MQ 两项 2/0/0/0。8 次 usage 的历史估算 $0.004268064 非账单。
8. **结果：**轮询缩短的是观察延迟，不是模型推理时间；完整模型也继承上游 Java 版本错误，内容质量没有通过。
9. **风险：**三场与 8 次调用规模小，未做真人盲审；更频繁轮询会增加 GET 压力；不能根据单次快慢推断长期收益。
10. **学习：**读报告 Prompt、DeepSeekInterviewReportGenerator.generate、原型工具和 QUALITY_AUDIT，再区分原始 FINAL 事实与报告总结。
11. **简历：**可讲“同时比较时延、来源和质量后拒绝过早替换”；追问引用为何不能证明事实正确。
12. **证据：**[M1-D 索引](m1d-20261115/README.md)、[技术事实审查](m1d-20261115/QUALITY_AUDIT.md)、[费用记录](m1d-20261115/cost-summary.json)、[真实对照报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-M1-D-报告生成优化与真实效果对照.md)。

<a id="h20"></a>

### H20｜M1-E P1：提交后有界唤醒 Outbox

1. **背景：**500 ms 扫描仍可能使已提交任务等待下一轮；目标是减少派发等待而不改变可靠性边界。
2. **发现：**隔离实验逐阶段计时表明单并发时存在可减少的 Outbox 等待。
3. **复现：**固定 1 秒模型桩，准确并发 1、基线与候选各 8 条；另有并发 8 各 8 条，以及 6 对真实模型调用。
4. **根因：**仅靠周期扫描，任务在提交后可能等下一次调度；请求线程直接发 MQ 会把网络失败与已提交事务混淆。
5. **尝试：**直接替换周期扫描会使内存提示丢失后无兜底；无限执行器或 CallerRuns 可能使请求线程承担网络工作。
6. **修复：**任务插入后发布事务事件，AFTER_COMMIT 监听仅向独立单线程、容量 1 的有界执行器提交可丢失提示；AtomicBoolean 合并，仍走原 dispatchDue 和租约 CAS；拒绝或进程退出由 500 ms 扫描兜底，不新增业务提交旁路。
7. **验证：**历史单并发桩 Outbox P50 416.69→0.68 ms，提交到成功流水线 1462.57→1040.46 ms；并发 8 为 2817.34→2820.29 ms 无收益。P1 单元 5 项、真实 MySQL 4 项，真实 MQ 指定方法 2/0/0/0；后端全量 1947/0/0/34。回滚不唤醒、拒绝仍可扫描、竞争及迟到回执被覆盖。
8. **结果：**6 对真实 DeepSeek 共 12 次，11 READY、1 FAILED；五对双方成功样本的配对流水线收益中位约 45.25 ms，波动大，不能证明完整报告稳定下降。历史按非高峰价格估算 $0.004890084，非供应商账单；本次归档新增调用 0。
9. **风险：**所有进程停机时没有扫描，需恢复健康实例；任务可能因期限 FAILED。结构违约与内容质量仍未解决，生产级 MQ 全故障矩阵未完成。
10. **学习：**从 acceptReportTask 的 publishEvent 读到 ReportOutboxWakeup.onTaskAccepted，再看 Dispatcher.claimDispatch、执行器拒绝和真实事务测试。
11. **简历：**可讲“在持久 Outbox 上加提交后有界提示，并用定时扫描兜底”；追问 AFTER_COMMIT、任务丢失与执行器饱和。
12. **证据：**[M1-E 主报告](m1e-20261115/DECISION_AND_VERIFICATION.md)、[唤醒器](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportOutboxWakeup.java)、[事务测试](../../src/test/java/com/kun/aiinterview/interview/service/ReportOutboxWakeupIntegrationTest.java)、[历史测试账本](m1e-20261115/test-results.json)。

<a id="h21"></a>

### H21｜M1-E P3 与收口：先展示已保存结果、合并在途查询

1. **背景：**综合报告生成期间，服务端已经保存并授权逐题结果；等待完整报告前页面可以呈现这些结果。
2. **发现：**历史三对真实浏览器观察候选可更早看到逐题分数；收口时又发现手动检查与定时 GET 重叠可能重复查询。
3. **复现：**A/B/C 各一对实际浏览器：基线首次有用内容是完整报告，候选是已保存 FINAL 结果；再用前端测试令一次轮询未完成时手动检查。
4. **根因：**原页面把已有结果的展示耦合到综合报告 READY；旧 recoverGenerating 增加 epoch 会破坏同域请求复用。
5. **尝试：**提前伪造基础评价或直接设置 READY 会混淆模型与已保存数据；提早自动触发完整报告的 P2 仅工具原型，未默认启用。
6. **修复：**报告页复用受归属保护的详情/时间线，标明综合报告尚未完成，FAILED 也不抹去授权结果；recoverGenerating 复用同 Session/epoch 在途 Promise，路由/POST 保留 epoch 隔离，离页停止新轮询并忽略旧响应。
7. **验证：**历史三对浏览器首份有用内容中位数 4.223→0.085 秒，内容类别不同；完整报告页 A 4.233→5.219 秒、C 4.212→4.189 秒，B 候选 FAILED。M1-E 原前端全量 346/0/0/0；收口修复后前端全量 **349/0/0/0**、报告页 59/0/0/0，Mock HTTP 后端 14/0/0/0；收口复用已有后端 1947/0/0/34，未重跑全后端。
8. **结果：**更早看到已保存结果有小样本页面证据，不等于完整 AI 报告或模型加速。真实失败原始 JSON 的 strengths/weaknesses/suggestions 为 7/7/5，离线重放首先被既有 1–5 条校验拒绝。
9. **风险：**12 次真实调用中一次结构失败，模型内部为何违约未知；旧请求未做网络 abort，只做到结果隔离；内容发布仍 NO-GO，未正式切换常驻服务或生产部署。
10. **学习：**读 InterviewReportView 的已保存结果、reportCopy、useInterviewReport 的 queryReportForScope/recoverGenerating 与对应前端 spec；再看生成器结构校验。
11. **简历：**可讲“复用授权持久化结果做渐进展示，并合并并发状态查询”；追问数据来源、身份切换和终态停止轮询。
12. **证据：**[M1-E 主报告及收口](m1e-20261115/DECISION_AND_VERIFICATION.md)、[收口验证](m1e-20261115/closeout/VERIFICATION.json)、[页面](../../frontend/src/features/interview/views/InterviewReportView.vue)、[前端测试](../../frontend/tests/InterviewReportView.spec.ts)。

## 尚缺证据与不能倒推的结论

1. 完整旧对话未提供；各次建议的完整上下文、个人独立手写范围及学习掌握程度未知。用户明确参与技术审查、提出约束并确认 552 OR 口径，但不能据 Git 作者推断独立开发。
2. M1-B “约 53 文件”缺同一批次精确增量边界；当前 diff 已叠加后续工作，不能反推。
3. Q1 12 卡是风险挑选，首轮不是逐点 gold；独立人工留出及可推广总体评分质量缺失。Q2-R2 无有效新旧模型对照。**评分和报告内容发布 NO-GO**。
4. M1-C 最终 B JAR 的早期真实模型验证不完整；后续 M1-E 的 12 次针对其另一个候选和独立环境，不可倒填成 M1-C 同一构建的验收。
5. 自然长超时、JDBC 提交结果未知、MySQL 真停机、Broker 永久损坏、同组多进程重平衡、长期积压和生产切换/回滚没有完整新验收。不能称全故障自动恢复、零丢失或外部模型 exactly once。
6. 小样本模型桩与浏览器数字保留环境、样本和语义；缺生产真实负载和稳定性能分布。旧会话服务是否仍在运行，本轮没有探测；本地缓存远端引用不等于实时远端。
7. 较早临时日志/XML 可能不再存在；应优先使用仓库原报告、结构化索引及受限备份。无法重新打开的原始材料只能按历史报告引用，不伪称本次复算。

## 本轮证据盘点方式

本轮读取并定位 **44 个源码/配置/SQL 文件、26 个测试文件**，检查了关键调用、SQL 条件及测试方法；这不是逐行审计，也不是运行 70 项测试。下方列出实际盘点文件，便于逐项追溯；证据报告及原始实验数据在各案例中分别链接。本轮离线复核 D1 JSON 为 63/298、Q1 解盲 JSON 为 12 卡、Q2 JSON 为 18 次记录；数值的解释仍依各原报告。测试、构建、服务及模型调用本轮均未执行。

### 源码、配置和 SQL：44 个

- [src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowService.java)
- [src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java](../../src/main/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionService.java)
- [src/main/java/com/kun/aiinterview/interview/service/InterviewService.java](../../src/main/java/com/kun/aiinterview/interview/service/InterviewService.java)
- [src/main/java/com/kun/aiinterview/interview/service/InterviewQuestionPlanSnapshotService.java](../../src/main/java/com/kun/aiinterview/interview/service/InterviewQuestionPlanSnapshotService.java)
- [src/main/java/com/kun/aiinterview/interview/service/QuestionPlanSelector.java](../../src/main/java/com/kun/aiinterview/interview/service/QuestionPlanSelector.java)
- [src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportService.java)
- [src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportTransactionService.java)
- [src/main/java/com/kun/aiinterview/interview/service/InterviewReportFinalReader.java](../../src/main/java/com/kun/aiinterview/interview/service/InterviewReportFinalReader.java)
- [src/main/resources/mapper/InterviewReportTaskMapper.xml](../../src/main/resources/mapper/InterviewReportTaskMapper.xml)
- [src/main/resources/mapper/InterviewSessionMapper.xml](../../src/main/resources/mapper/InterviewSessionMapper.xml)
- [src/main/resources/mapper/InterviewAnswerMapper.xml](../../src/main/resources/mapper/InterviewAnswerMapper.xml)
- [src/main/resources/mapper/AnswerEvaluationMapper.xml](../../src/main/resources/mapper/AnswerEvaluationMapper.xml)
- [src/main/java/com/kun/aiinterview/interview/report/async/ReportAsyncConfiguration.java](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportAsyncConfiguration.java)
- [src/main/java/com/kun/aiinterview/interview/report/async/ReportAsyncProperties.java](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportAsyncProperties.java)
- [src/main/java/com/kun/aiinterview/interview/report/async/ReportMessageConsumer.java](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportMessageConsumer.java)
- [src/main/java/com/kun/aiinterview/interview/report/async/ReportMessageProducer.java](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportMessageProducer.java)
- [src/main/java/com/kun/aiinterview/interview/report/async/ReportMqRuntime.java](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportMqRuntime.java)
- [src/main/java/com/kun/aiinterview/interview/report/async/ReportOutboxDispatcher.java](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportOutboxDispatcher.java)
- [src/main/java/com/kun/aiinterview/interview/report/async/ReportOutboxWakeup.java](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportOutboxWakeup.java)
- [src/main/java/com/kun/aiinterview/interview/report/async/ReportTaskMessage.java](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportTaskMessage.java)
- [src/main/java/com/kun/aiinterview/interview/report/async/ReportExecution.java](../../src/main/java/com/kun/aiinterview/interview/report/async/ReportExecution.java)
- [src/main/java/com/kun/aiinterview/interview/report/prompt/InterviewReportPromptBuilder.java](../../src/main/java/com/kun/aiinterview/interview/report/prompt/InterviewReportPromptBuilder.java)
- [src/main/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGenerator.java](../../src/main/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGenerator.java)
- [src/main/java/com/kun/aiinterview/interview/service/EvaluationContextService.java](../../src/main/java/com/kun/aiinterview/interview/service/EvaluationContextService.java)
- [src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRagQueryBuilder.java](../../src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRagQueryBuilder.java)
- [src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRetrievalAdapter.java](../../src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRetrievalAdapter.java)
- [src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalService.java](../../src/main/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalService.java)
- [src/main/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilder.java](../../src/main/java/com/kun/aiinterview/interview/evaluation/prompt/EvaluationPromptBuilder.java)
- [src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java](../../src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java)
- [src/main/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidator.java](../../src/main/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidator.java)
- [src/main/java/com/kun/aiinterview/interview/evaluation/score/EvaluationScoreCalculator.java](../../src/main/java/com/kun/aiinterview/interview/evaluation/score/EvaluationScoreCalculator.java)
- [src/main/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpPolicy.java](../../src/main/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpPolicy.java)
- [src/main/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpTargetResolver.java](../../src/main/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpTargetResolver.java)
- [src/main/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingClient.java](../../src/main/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingClient.java)
- [src/main/java/com/kun/aiinterview/knowledge/embedding/springai/EmbeddingOpenAiApi.java](../../src/main/java/com/kun/aiinterview/knowledge/embedding/springai/EmbeddingOpenAiApi.java)
- [src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusConfiguration.java](../../src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusConfiguration.java)
- [src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusCollectionInitializer.java](../../src/main/java/com/kun/aiinterview/knowledge/vector/milvus/MilvusCollectionInitializer.java)
- [src/main/java/com/kun/aiinterview/user/service/UserWeaknessService.java](../../src/main/java/com/kun/aiinterview/user/service/UserWeaknessService.java)
- [src/main/java/com/kun/aiinterview/config/EnvironmentIsolationInitializer.java](../../src/main/java/com/kun/aiinterview/config/EnvironmentIsolationInitializer.java)
- [frontend/src/features/interview/composables/useInterviewReport.ts](../../frontend/src/features/interview/composables/useInterviewReport.ts)
- [frontend/src/features/interview/views/InterviewReportView.vue](../../frontend/src/features/interview/views/InterviewReportView.vue)
- [frontend/src/features/interview/copy/reportCopy.ts](../../frontend/src/features/interview/copy/reportCopy.ts)
- [frontend/src/features/interview/components/PreviousInterviewFeedback.vue](../../frontend/src/features/interview/components/PreviousInterviewFeedback.vue)
- [sql/migrations/20261114_interview_report_task.sql](../../sql/migrations/20261114_interview_report_task.sql)

### 测试文件：26 个

- [src/test/java/com/kun/aiinterview/interview/service/InterviewConcurrentReplayIntegrationTest.java](../../src/test/java/com/kun/aiinterview/interview/service/InterviewConcurrentReplayIntegrationTest.java)
- [src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowServiceTest.java](../../src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowServiceTest.java)
- [src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionServiceIntegrationTest.java](../../src/test/java/com/kun/aiinterview/interview/service/InterviewWorkflowTransactionServiceIntegrationTest.java)
- [src/test/java/com/kun/aiinterview/interview/service/InterviewReportFinalConsistencyIntegrationTest.java](../../src/test/java/com/kun/aiinterview/interview/service/InterviewReportFinalConsistencyIntegrationTest.java)
- [src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekLogSafetyTest.java](../../src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekLogSafetyTest.java)
- [src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekHttpTimeoutTest.java](../../src/test/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekHttpTimeoutTest.java)
- [src/test/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingLogSafetyTest.java](../../src/test/java/com/kun/aiinterview/knowledge/embedding/springai/SpringAiEmbeddingLogSafetyTest.java)
- [src/test/java/com/kun/aiinterview/interview/service/QuestionPlanSelectorTest.java](../../src/test/java/com/kun/aiinterview/interview/service/QuestionPlanSelectorTest.java)
- [src/test/java/com/kun/aiinterview/interview/service/InterviewQuestionPlanSnapshotIntegrationTest.java](../../src/test/java/com/kun/aiinterview/interview/service/InterviewQuestionPlanSnapshotIntegrationTest.java)
- [src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalServiceTest.java](../../src/test/java/com/kun/aiinterview/knowledge/service/KnowledgeRetrievalServiceTest.java)
- [src/test/java/com/kun/aiinterview/interview/controller/InterviewVisibilityHttpIntegrationTest.java](../../src/test/java/com/kun/aiinterview/interview/controller/InterviewVisibilityHttpIntegrationTest.java)
- [frontend/tests/InterviewSessionView.spec.ts](../../frontend/tests/InterviewSessionView.spec.ts)
- [src/test/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidatorTest.java](../../src/test/java/com/kun/aiinterview/interview/evaluation/validation/LlmEvaluationValidatorTest.java)
- [src/test/java/com/kun/aiinterview/interview/evaluation/score/EvaluationScoreCalculatorTest.java](../../src/test/java/com/kun/aiinterview/interview/evaluation/score/EvaluationScoreCalculatorTest.java)
- [src/test/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpPolicyTest.java](../../src/test/java/com/kun/aiinterview/interview/evaluation/decision/FollowUpPolicyTest.java)
- [src/test/java/com/kun/aiinterview/interview/service/ReportTaskMapperIntegrationTest.java](../../src/test/java/com/kun/aiinterview/interview/service/ReportTaskMapperIntegrationTest.java)
- [src/test/java/com/kun/aiinterview/interview/service/ReportTaskTransactionTest.java](../../src/test/java/com/kun/aiinterview/interview/service/ReportTaskTransactionTest.java)
- [src/test/java/com/kun/aiinterview/interview/report/async/ReportOutboxDispatcherTest.java](../../src/test/java/com/kun/aiinterview/interview/report/async/ReportOutboxDispatcherTest.java)
- [src/test/java/com/kun/aiinterview/interview/report/async/ReportMessageConsumerTest.java](../../src/test/java/com/kun/aiinterview/interview/report/async/ReportMessageConsumerTest.java)
- [src/test/java/com/kun/aiinterview/interview/service/RealReportRocketMqSmokeTest.java](../../src/test/java/com/kun/aiinterview/interview/service/RealReportRocketMqSmokeTest.java)
- [src/test/java/com/kun/aiinterview/interview/service/ReportOwnershipLockIsolationTest.java](../../src/test/java/com/kun/aiinterview/interview/service/ReportOwnershipLockIsolationTest.java)
- [src/test/java/com/kun/aiinterview/interview/service/RealReportCrashWindowProbeTest.java](../../src/test/java/com/kun/aiinterview/interview/service/RealReportCrashWindowProbeTest.java)
- [src/test/java/com/kun/aiinterview/interview/report/async/ReportOutboxWakeupTest.java](../../src/test/java/com/kun/aiinterview/interview/report/async/ReportOutboxWakeupTest.java)
- [src/test/java/com/kun/aiinterview/interview/service/ReportOutboxWakeupIntegrationTest.java](../../src/test/java/com/kun/aiinterview/interview/service/ReportOutboxWakeupIntegrationTest.java)
- [src/test/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGeneratorTest.java](../../src/test/java/com/kun/aiinterview/interview/report/deepseek/DeepSeekInterviewReportGeneratorTest.java)
- [frontend/tests/InterviewReportView.spec.ts](../../frontend/tests/InterviewReportView.spec.ts)

## 2026-11-16 版本化补记：研究收口与单机验收

本节只补充本文件原有时间范围之后的入口，不改写 11-11～11-15 历史结论。RocketMQ 最终公平 K=4 对照、资源成本、故障矩阵和保留实现的 CONDITIONAL GO 见 [净收益档案](mq-net-value-20261116/README.md)；评分人工意见及修订、被拒绝的 Prompt/报告候选和真实反向退化见 [评分索引](../evaluation/2026-11-16-scoring-quality/REPORT.md)、[可靠性索引](../evaluation/2026-11-16-reliability/README.md)。

[单机部署收口](2026-11-16-本机部署收口与工程档案.md) 记录现有卷复用、当前候选发送前进程故障与恢复、重启前后报告及偏移、当前 MySQL 逻辑备份和实际回归。受控演示 GO；机器重启后模型配置、独立备份恢复、跨存储一致恢复及公网发布均未放行。所有本轮与历史模型桩、真实模型、Mock 和受条件跳过测试保持区分。

**同日后续：**上述“独立备份恢复未放行”为原验收时点；随后已在新建无网络临时 MySQL 实例完成 14 表单库恢复和重导指纹对照，跨存储、应用级及离机恢复仍未验证。详见[最终收口](2026-11-16-本机部署收口与工程档案.md#最终交付冻结与恢复演练)。
