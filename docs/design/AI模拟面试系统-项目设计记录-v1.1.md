AI 模拟面试系统｜项目设计记录 1.1

版本：v1.1

更新日期：2026-07-10

1. ## 项目定位

日期：2026-07-09

设计点：确定项目用途、目标用户和整体技术方向。

### 1.1 项目定位

本项目是一个面向 Java 后端实习、校招和初级岗位求职场景的 RAG 模拟面试训练平台。

系统不是通用 AI 聊天工具，也不按大型商业系统设计。项目以 Java 后端业务为主线，围绕用户、题库、面试会话、回答、评价、报告和薄弱知识点形成完整流程，并使用 RAG 和 LLM 完成知识检索、回答分析、纠错和追问建议。

项目目标：

1. Java 后端业务结构完整，数据库、接口、权限和状态流转能够对应。

2. RAG 真正参与回答评价，而不是只包装一次大模型调用。

3. LLM 的输出能够被 Java 后端校验和使用，不直接修改数据库或控制业务状态。

4. 项目规模适合个人独立完成，能够在实习或校招面试中说明主要设计取舍。

### 1.2 核心业务闭环

```Plain
用户注册或登录→ 选择面试难度→ 创建面试会话和面试计划→ 按计划提出主问题→ 用户回答→ RAG 检索相关知识片段→ LLM 结合题目、回答、评分点、检索结果和统一标准进行评价→ Java 后端决定追问、进入下一题或结束面试→ 生成面试报告→ 更新用户薄弱知识点
```

### 1.3 核心职责边界

- 题库决定具体问什么。

- RAG 在用户回答后检索相关知识依据，不负责出题。

- LLM 负责自然语言分析、结构化评分、纠错、复习建议和候选追问。

- Java 后端负责题目选择、权限校验、状态流转、评分结果校验、追问决策、报告汇总和薄弱点更新。

- LLM 不能直接修改数据库，也不能直接改变面试状态。

### 1.4 第一版功能

1. 用户注册、登录、JWT 鉴权、修改密码和忘记密码重置。

2. 用户选择面试难度并创建面试。

3. 后端根据难度、知识领域和历史薄弱点生成本场面试计划。

4. 用户逐题回答，系统保存主问题和追问的完整过程。

5. RAG 根据当前问题、知识点、评分点和用户回答检索 Java 后端知识库。

6. LLM 输出固定结构的五维评价、评分点覆盖情况、纠错内容和候选追问。

7. Java 后端根据固定规则决定是否追问；每道主问题**最多追问一次**。

8. 面试结束后生成报告并更新用户薄弱知识点。

9. 普通用户查看自己的当前面试、历史面试、报告和薄弱知识点。

10. 管理员进行基础用户管理、题库管理、知识库管理，并查看面试记录。

### 1.5 第一版技术栈

- 后端：Spring Boot、MyBatis

- 数据库：MySQL

- 缓存和验证码：Redis

- 认证：JWT、BCrypt

- 接口文档：Knife4j

- AI 应用框架：LangChain4j

- LLM：DeepSeek API

- Embedding：具体模型在 RAG 开发前确定

- 向量存储：开发验证阶段使用 `InMemoryEmbeddingStore`，完整 MVP 使用 Milvus

- 邮件：Spring Mail，对接可配置 SMTP 服务

- 部署：Docker、Docker Compose

### 1.6 第一版暂不实现

- 视频面试、语音面试、简历解析

- 复杂 Agent、多模型调度、Graph RAG、知识图谱

- 多路召回、复杂 Rerank、复杂推荐算法

- 微服务、消息队列、Kubernetes、分布式事务

- 企业级 RBAC、多租户和复杂组织权限
2. ## 核心业务流程

日期：2026-07-09

设计点：确定普通用户完成一场模拟面试的业务流程和状态变化。

### 2.1 注册与登录

1. 用户使用账号、展示名称、邮箱和密码注册。

2. 注册接口固定创建 `USER` 角色，客户端不能指定管理员角色。

3. 用户登录成功后获得 JWT。

4. 用户被禁用或密码发生变化后，旧 JWT 失效。

5. 忘记密码时，系统向已绑定邮箱发送一次性验证码。验证码保存在 Redis 中，设置有效期、发送间隔和最大尝试次数。

### 2.2 创建面试

1. 用户选择面试难度并调用创建接口。

2. 同一个用户同时最多存在一场 `CREATED` 或 `IN_PROGRESS` 面试。存在未完成面试时，接口返回原会话，不重复创建。

3. Java 后端读取难度配置、固定知识领域和用户历史薄弱点，选择本场所有主问题。

4. 所有主问题在创建会话时一次性写入 `interview_question`，状态为 `PENDING`。这些记录就是本场面试计划。

5. 后端将第一道主问题改为 `WAITING_ANSWER`，将会话改为 `IN_PROGRESS` 并返回问题。

6. 创建后不再重新抽取主问题，确保刷新、断线或服务重启后计划不变化。

### 2.3 回答主问题

1. 用户提交当前 `WAITING_ANSWER` 问题的回答。

2. 后端校验用户身份、会话归属、会话状态、当前问题和幂等标识。

3. 回答保存为 `SUBMITTED`，随后进入 `EVALUATING`。

4. RAG 模块构造 Query，检索当前知识领域下的相关 chunk，并保存同一检索批次的命中记录。

5. LLM 接收当前问题、用户回答、评分点快照、RAG 结果和五维评分标准，返回结构化评价。

6. Java 后端校验 JSON 结构和分数范围，并计算总分。

7. 如果不需要追问，本次评价标记为 `FINAL`，当前主问题完成。

8. 如果满足追问条件，主回答评价标记为 `INITIAL`，后端保存一条 `FOLLOW_UP` 问题并返回给用户。

### 2.4 回答追问

1. 每道主问题最多创建一道追问。

2. 追问关联原主问题，并记录针对的评分点。

3. 用户回答追问后，LLM 同时接收主问题、主回答、追问、追问回答、主问题评分点快照和本次 RAG 结果。

4. LLM 生成综合评价，Java 后端校验后将其保存为该主问题的 `FINAL` 评价。

5. 报告和薄弱点只使用每道主问题的 `FINAL` 评价，避免主回答和追问重复计分。

### 2.5 Java 后端追问规则

LLM 可以给出 `followUpRecommended` 和候选追问，但最终决策由 Java 后端执行。v1.1 使用以下规则：

```Plain
当前问题是 MAIN并且该主问题尚未追问并且（总分低于 60，或者存在未覆盖的 CORE 评分点）并且会话状态为 IN_PROGRESS→ 创建一次追问
```

不满足条件时进入下一道主问题。候选追问为空或格式不合格时，后端使用遗漏的核心评分点生成固定模板追问。

### 2.6 面试完成、恢复和取消

1. 当前主问题产生 `FINAL` 评价后，后端查找下一条 `PENDING` 主问题。

2. 存在下一题时，将其改为 `WAITING_ANSWER` 并返回。

3. 不存在下一题时，自动完成面试，计算总分并生成报告。

4. 用户关闭页面或网络中断不会改变会话状态。`GET /api/interviews/current` 返回当前会话和等待回答的问题。

5. 用户明确放弃时调用取消接口，会话进入 `CANCELLED`，不再接受回答。

6. 报告生成失败不回滚已完成的回答。会话保存报告状态，可通过幂等完成接口重试报告生成。

3. ## MVP 功能边界

日期：2026-07-09

设计点：保证第一版能够跑通完整流程，同时控制个人开发规模。

### 3.1 MVP 核心目标

```Plain
注册或登录→ 创建固定领域覆盖的面试计划→ 主问题回答→ RAG 检索→ LLM 结构化评价→ Java 后端决定是否追问→ 完成全部计划问题→ 生成报告→ 更新薄弱知识点
```

### 3.2 知识领域

第一版通过统一枚举管理知识领域：

```Plain
JAVA_BASICJAVA_COLLECTIONJVMJAVA_CONCURRENCYSPRINGMYSQLREDISNETWORKPROJECT_SCENARIO
```

不同难度对应题目难度、计划题数和需要覆盖的领域。具体映射通过后端配置维护，不在请求中由用户自定义。

### 3.3 固定广度，自适应深度

- 固定广度：创建会话时确定本场需要覆盖的领域和主问题，中途不改变主问题计划。

- 自适应深度：根据当前回答结果决定是否追加一次追问。

- 历史薄弱点影响主问题选择权重，但不能挤掉本场预定领域。

- 第一版每个主问题最多追问一次。

### 3.4 MVP 必须实现

1. 用户认证和普通用户、管理员两种角色。

2. 题库和评分点基础管理。

3. Markdown、纯文本知识文档的上传、切片、Embedding 和向量入库。

4. 面试计划、主问题、追问、回答和状态恢复。

5. 结构化五维评分及评分点覆盖结果。

6. RAG 命中日志和评价依据追溯。

7. 面试报告和用户薄弱知识点。

8. 普通用户数据归属校验和管理员只读查看全部面试。

### 3.5 MVP 暂不实现

第一版暂不实现第 1.6 节列出的扩展能力。评价算法采用固定、可解释的规则，不加入模型路由、复杂工作流或动态推荐算法。

4. ## 模块拆分

日期：2026-07-09

设计点：确定模块职责，避免 Java 后端、RAG 和 LLM 互相越界。

### 4.1 用户模块

- 用户注册、登录、修改密码和找回密码

- JWT 生成和身份认证

- 用户状态与密码变更时间校验

- 当前用户信息查询

### 4.2 管理员模块

- 查看和禁用、恢复用户

- 只读查看全部面试记录和报告

- 题库、评分点和知识库基础管理

### 4.3 面试模块

- 创建会话和持久化面试计划

- 控制主问题、追问和知识领域切换

- 校验当前问题和会话归属

- 维护会话、问题和回答状态

- 根据结构化评价执行 `FOLLOW_UP`、`NEXT_MAIN` 或 `FINISH`

- 处理重复提交和失败恢复

### 4.4 题库模块

- 维护题目分类、知识点、难度和状态

- 维护结构化评分点

- 根据难度、领域、历史记录和薄弱点选择主问题

- 控制近期题目重复率

### 4.5 LLM 模块

- 对接 DeepSeek API

- 管理评价 Prompt 和版本

- 输出五维分数、评分点覆盖结果、纠错、优点、遗漏点和候选追问

- 生成报告中的自然语言总结和复习建议

- 处理超时、有限次数重试、结构化输出校验和解析失败

LLM 不负责数据库写入、面试状态修改、总分计算和最终追问决策。

### 4.6 RAG 检索模块

- 构造带稳定锚点的检索 Query

- 按 `category` 和有效文档版本过滤向量

- 执行 TopK 检索

- 保存检索批次和命中片段

- 将知识片段提供给 LLM

### 4.7 知识库模块

- 知识文档上传、解析和处理状态维护

- 按标题、段落和语义完整性切片

- 生成 Embedding

- 维护 chunk 版本和向量数据

- 停用文档、重新处理和向量版本切换

### 4.8 报告模块

- 汇总每道主问题的 `FINAL` 评价

- 由 Java 后端计算总体分数和领域表现

- 调用 LLM 生成自然语言总结和建议

- 保存面试报告

- 根据固定规则更新用户薄弱知识点

**5.数据库设计**

日期：2026-07-09

更新日期：2026-07-12

设计点：确定 MVP 需要长期保存的数据、关系、状态和历史快照。

当前版本已完成 12 张核心表的物理数据库设计，并形成可直接执行的建表 SQL。完整 SQL 单独保存在 `AI面试官v1.1.sql` 中，本章记录表职责、核心字段、关键约束、索引和设计规则。

### 5.1 数据库原则

1. MySQL 是用户、面试和知识元数据的唯一事实来源。

2. 能通过稳定关联得到的数据不重复保存；会影响历史解释的内容保存快照。

3. 一场面试的全部主问题在创建时持久化。

4. 题目、评分点和知识文档后续修改不能改变历史评价依据。

5. 外部 LLM、Embedding 和向量检索调用不放在长数据库事务中。

6. 依靠状态字段、唯一约束、短事务、幂等请求 ID 和乐观锁处理重复提交及状态竞争。

7. 核心历史数据默认不使用级联删除，主要通过 `ON DELETE RESTRICT` 保证历史记录完整。

8. 业务状态使用 `VARCHAR` 保存，由 Java 枚举、数据库 `CHECK` 约束和 Service 层业务校验共同保证合法性。

9. 完整建表 SQL 以 `AI面试官v1.1.sql` 为唯一可执行基准，飞书和 Markdown 主要记录设计说明和修改原因。

### 5.2 核心表

第一版保留 12 张核心表：

```Plain
userquestionquestion_scoring_pointinterview_sessioninterview_questioninterview_answeranswer_evaluationknowledge_documentknowledge_chunkrag_hit_loginterview_reportuser_weakness
```

### 5.3 用户表 user

一行代表一个普通用户或管理员。

核心字段：

```Plain
idaccountusernamepasswordemailrolestatuspassword_changed_atcreated_atupdated_at
```

枚举：

```Plain
role: USER, ADMINstatus: ENABLED, DISABLED
```

约束：

- account 唯一。

- email 唯一。

- role 只能为 USER 或 ADMIN。

- status 只能为 ENABLED 或 DISABLED。

- username 是展示名称，不作为唯一登录凭证。

- password 只保存 BCrypt 哈希值，不保存明文密码。

- 普通注册接口只能写入 USER。

- 用户核心历史数据不级联删除。

### 5.4 题库表 question

一行代表一道固定 Java 后端面试题。

核心字段：

```Plain
idcategoryknowledge_pointdifficultyquestion_contentreference_answerstatuscreated_atupdated_at
```

约束：

- status 只能为 ENABLED 或 DISABLED。

- 建立 `(category, difficulty, status)` 联合索引，用于按知识领域、难度和启用状态选题。

说明：

- reference_answer 用于管理员维护题目和评分点，不作为运行时唯一评分依据。

- 主问题由 Java 后端从固定题库中选择，不由 LLM 自由生成。

- 题目原则上只能停用，不能物理删除。

### 5.5 题目评分点表 question_scoring_point

一行代表一道题的一个评分要点。

核心字段：

```Plain
idquestion_idpoint_typecontentweightsort_orderstatuscreated_atupdated_at
```

评分点类型：

```Plain
COREKEYPRINCIPLEINTERNALADVANCED
```

约束：

- question_id 外键关联 question.id。

- weight 范围为 1～100。

- status 只能为 ENABLED 或 DISABLED。

- 建立 `(question_id, status, sort_order)` 联合索引。

- 同一题所有启用评分点的 weight 总和必须为 100。

- 该规则涉及多行汇总，不能由单行 CHECK 完成，因此由 Java Service 在事务中校验。

- 创建面试计划时，将评分点 ID、类型、内容和权重写入主问题快照。

### 5.6 面试会话表 interview_session

一行代表一整场模拟面试。

核心字段：

```Plain
iduser_iddifficultystatuscurrent_interview_question_idplanned_question_countcompleted_question_counttotal_scorereport_statusversionstarted_atended_atcreated_atupdated_at
```

状态：

```Plain
status: CREATED, IN_PROGRESS, COMPLETED, CANCELLEDreport_status: NOT_STARTED, GENERATING, READY, FAILED
```

约束：

- user_id 外键关联 user.id。

- completed_question_count 不能大于 planned_question_count。

- total_score 为空或位于 0～100。

- 建立 `(user_id, created_at)` 联合索引。

- 建立 `(user_id, status)` 联合索引。

说明：

- planned_question_count 只统计主问题。

- completed_question_count 只统计已经产生 FINAL 评价的主问题。

- total_score 在完成面试时由 Java 后端计算。

- status 管理面试本身，report_status 单独管理报告生成过程。

- 允许出现面试已经 COMPLETED，但报告生成 FAILED 的情况。

- version 用于乐观锁，防止重复点击、浏览器重试、多标签页或网络重发导致并发状态覆盖。

- current_interview_question_id 允许为空。

- 当前版本不为 current_interview_question_id 添加外键，避免 interview_session 与 interview_question 形成不必要的循环耦合，由 Java Service 校验当前题必须属于同一场面试。

- MVP 通过事务检查保证每个用户最多一场 CREATED 或 IN_PROGRESS 面试。

### 5.7 本场面试题目表 interview_question

一行代表某场面试中实际出现的一道主问题或追问。创建会话时预先插入全部主问题，因此本表同时保存面试计划。

核心字段：

```Plain
idsession_idquestion_idcategoryknowledge_pointquestion_contentscoring_points_snapshotquestion_typeparent_question_idfollow_up_target_pointsplan_orderdisplay_orderstatuscreated_atupdated_at
```

枚举：

```Plain
question_type: MAIN, FOLLOW_UPstatus: PENDING, WAITING_ANSWER, ANSWERED, SKIPPED
```

约束：

- session_id 外键关联 interview_session.id。

- question_id 外键关联 question.id。

- parent_question_id 自关联 interview_question.id。

- `(session_id, plan_order)` 联合唯一。

- `(session_id, display_order)` 联合唯一。

- 建立 `(session_id, question_type, status, plan_order)` 联合索引。

- 建立 `(parent_question_id, question_type)` 联合索引。

规则：

- MAIN 必须满足：question_id 非空，parent_question_id 为空，plan_order 非空。

- FOLLOW_UP 必须满足：question_id 为空，parent_question_id 非空，plan_order 为空。

- 主问题来源于题库，question_id 非空。

- question_content 保存用户实际看到的内容。

- category、knowledge_point 和 scoring_points_snapshot 保存创建面试时的快照。

- 追问由 LLM 提供候选内容，question_id 为空，parent_question_id 指向同一会话的主问题。

- follow_up_target_points 保存追问针对的评分点 ID。

- plan_order 只用于主问题计划顺序，display_order 用于实际问答展示顺序。

- 同一会话同一时刻最多有一道 WAITING_ANSWER 问题，由 Java Service 在事务中保证。

- 每道主问题最多追问一次，由 Java 后端业务规则保证。

### 5.8 用户回答表 interview_answer

一行代表用户对一道实际问题的一份有效回答。

核心字段：

```Plain
idinterview_question_idanswer_contentstatusrequest_iderror_codesubmitted_atcreated_atupdated_at
```

状态：

```Plain
SUBMITTEDEVALUATINGEVALUATEDFAILED
```

约束：

- interview_question_id 外键关联 interview_question.id。

- interview_question_id 唯一，一道实际问题只能提交一份有效回答。

- request_id 唯一，用于处理前端重试。

- 不重复保存 user_id 和 session_id，通过问题和会话关联查询。

说明：

- interview_question_id 唯一解决同一道题重复回答问题。

- request_id 唯一解决前端重试、网络重发导致同一请求重复执行问题。

- FAILED 只表示评价处理失败，原始回答仍然保留，可以重新处理。

### 5.9 回答评价表 answer_evaluation

一行代表一次回答处理产生的结构化评价。没有追问时，主回答评价就是最终评价；有追问时，主回答先产生 INITIAL，追问回答对应的评价保存主问题的综合最终结果。

核心字段：

```Plain
idanswer_idmain_interview_question_idevaluation_phasecorrectness_scorecompleteness_scoredepth_scoreclarity_scorepractice_scoretotal_scorelevelstrengthsmissing_pointscorrectionscoring_point_resultsfollow_up_recommendedsuggested_follow_updecision_actionretrieval_batch_idllm_modelprompt_versionevaluation_standard_versionraw_resultcreated_at
```

枚举：

```Plain
evaluation_phase: INITIAL, FINALdecision_action: FOLLOW_UP, NEXT_MAIN, FINISH
```

评分规则：

- 五个维度每项为 0～20。

- total_score 范围为 0～100，并且必须等于五个维度得分之和。

- 五维分数由 LLM 返回，但必须经过后端 JSON Schema、枚举、数值范围和业务规则校验。

- total_score 由 Java 后端计算，不直接信任模型返回的总分。

- 报告和薄弱点只查询每道主问题的 FINAL 评价。

- follow_up_recommended 是模型建议，decision_action 是后端最终决定。

约束：

- answer_id 外键关联 interview_answer.id，并且唯一。

- main_interview_question_id 外键关联 interview_question.id。

- follow_up_recommended 只能为 0 或 1。

- 建立 `(main_interview_question_id, evaluation_phase)` 联合索引。

- 为 retrieval_batch_id 建立索引。

说明：

- answer_id 表示哪一份回答触发了本次评价。

- main_interview_question_id 表示本次评价最终归属于哪一道主问题。

- 有追问时，主回答和追问回答虽然 answer_id 不同，但都归属于同一个 main_interview_question_id。

- LLM 不能直接改变数据库状态或推进面试流程。

- retrieval_batch_id 指向本次评价实际使用的 RAG 检索批次，保证评分依据可追溯。

### 5.10 知识文档表 knowledge_document

一行代表一篇上传的 Java 后端知识文档。MVP 只支持 UTF-8 Markdown 和纯文本。

核心字段：

```Plain
idtitlecategoryfile_namefile_typecontentcontent_hashsourcedocument_versionprocessing_statuserror_messagecreated_atupdated_at
```

文件类型：

```Plain
MARKDOWNTEXT
```

处理状态：

```Plain
UPLOADEDPROCESSINGREADYFAILEDDISABLED
```

约束：

- document_version 不小于 1。

- 建立 `(category, processing_status)` 联合索引。

- 为 content_hash 建立单列索引。

说明：

- content 使用 MEDIUMTEXT 保存完整原始文档。

- content_hash 使用 SHA-256 内容指纹，保存为 64 位十六进制字符串，用于识别重复内容。

- content_hash 当前只建立普通索引，不使用唯一约束，由 Java Service 根据业务规则决定是否允许相同内容重复存在。

- 停用文档后，其 chunk 不再参与新检索。

### 5.11 知识切片表 knowledge_chunk

一行代表文档某个版本的一个知识片段，是实际参与 Embedding 和 RAG 检索的数据。

核心字段：

```Plain
iddocument_iddocument_versionchunk_indexcontenttoken_countvector_idembedding_modelembedding_versionstatuscreated_atupdated_at
```

状态：

```Plain
ACTIVEINACTIVE
```

约束：

- document_id 外键关联 knowledge_document.id。

- document_version 不小于 1。

- token_count 大于 0。

- vector_id 唯一。

- `(document_id, document_version, chunk_index)` 联合唯一。

- 建立 `(document_id, document_version, status)` 联合索引。

说明：

- `(document_id, document_version, chunk_index)` 保证同一篇文档的同一个版本中，同一个位置只能有一个 chunk。

- vector_id 是该 chunk 在向量存储中的唯一标识。

- 重新处理文档时创建新的文档版本和 chunk，不覆盖旧 chunk。

- 新版本全部写入成功后再将旧版本标记为 INACTIVE，从而保留历史 RAG 依据。

### 5.12 RAG 命中记录表 rag_hit_log

一行代表一次检索批次命中的一个知识片段。

核心字段：

```Plain
idanswer_idretrieval_batch_idchunk_idsimilarity_scorerank_noquery_texttop_kfilter_snapshotembedding_modelembedding_versioncreated_at
```

约束：

- answer_id 外键关联 interview_answer.id。

- chunk_id 外键关联 knowledge_chunk.id。

- rank_no 不小于 1。

- top_k 不小于 1。

- rank_no 不能大于 top_k。

- `(retrieval_batch_id, rank_no)` 联合唯一。

- 建立 `(answer_id, retrieval_batch_id)` 联合索引。

- 为 chunk_id 建立索引。

说明：

- 同一次检索的所有命中使用相同 retrieval_batch_id。

- 一次 TopK 检索会产生多行 rag_hit_log。

- 同一份回答在失败重试后可以产生新的检索批次。

- answer_evaluation.retrieval_batch_id 保存最终评分实际使用的批次 ID，解决重试后无法判断评分依据的问题。

- 第一版允许在每条命中中重复保存 query_text、top_k 和 filter_snapshot，不单独增加检索请求表。

### 5.13 面试报告表 interview_report

一行代表一场面试的一份最终报告。

核心字段：

```Plain
idsession_idoverall_scoreresultsummarystrengthsweaknessessuggestionsllm_modelprompt_versioncreated_atupdated_at
```

约束和规则：

- session_id 外键关联 interview_session.id。

- session_id 唯一。

- overall_score 范围为 0～100。

- overall_score 由 Java 后端汇总主问题 FINAL 评价得到。

- LLM 只负责生成自然语言总结和建议。

- result 当前保留为字符串字段，具体等级和分数区间在报告规则实现时统一确定。

- 用户名、题目和回答通过 session_id 查询，不在报告中重复保存。

### 5.14 用户薄弱知识点表 user_weakness

一行代表用户在一个具体知识点上的长期薄弱记录。

核心字段：

```Plain
iduser_idcategoryknowledge_pointweakness_scorediscovered_countlast_discovered_atstatusresolved_atcreated_atupdated_at
```

约束：

```Plain
UNIQUE(user_id, category, knowledge_point)
```

- user_id 外键关联 user.id。

- weakness_score 范围为 0～100。

- discovered_count 不小于 1。

- ACTIVE 时 resolved_at 必须为空。

- RESOLVED 时 resolved_at 必须非空。

- 建立 `(user_id, status, weakness_score)` 联合索引。

- 建立 `(user_id, last_discovered_at)` 联合索引。

状态：

```Plain
ACTIVERESOLVED
```

第一版更新规则：

- 主问题 FINAL 总分低于 60，或存在未覆盖的 CORE 评分点时，更新对应薄弱知识点。

- 不存在该薄弱点时新增记录。

- 已存在且为 ACTIVE 时，更新薄弱分数、累计次数和最近发现时间。

- 已经 RESOLVED，但之后再次表现较差时，重新变为 ACTIVE 并清空 resolved_at。

说明：

- interview_report.weaknesses 只表示某一场面试的总结。

- user_weakness 表示跨多场面试长期积累的用户薄弱知识点画像。

- weakness_score 越高表示越薄弱，具体计算公式由 Java Service 在实现阶段确定。

### 5.15 核心关系

```Plain
user 1:N interview_sessioninterview_session 1:N interview_questioninterview_question 1:1 interview_answerinterview_answer 1:1 answer_evaluationquestion 1:N question_scoring_pointquestion 1:N interview_questionknowledge_document 1:N knowledge_chunkinterview_answer 1:N rag_hit_logknowledge_chunk 1:N rag_hit_loginterview_session 1:1 interview_reportuser 1:N user_weakness
```

补充说明：

- interview_question.parent_question_id 用于建立追问到主问题的自关联。

- answer_evaluation.main_interview_question_id 用于把主回答初评和追问后的最终评价归入同一道主问题。

- answer_evaluation.retrieval_batch_id 用于锁定本次评价实际采用的 RAG 检索批次。

- interview_session.current_interview_question_id 用于恢复当前面试进度，但当前版本不添加数据库外键，由 Service 层校验目标问题属于同一会话。

### 5.16 SQL 实现基准

12 张核心表的完整可执行建表 SQL 统一保存在：

```Plain
AI面试官v1.1.sql
```

该 SQL 文件作为当前 v1.1 数据库结构的唯一可执行基准。

6. ## 接口设计

日期：2026-07-10

设计点：定义 MVP 对外接口及其权限、请求参数和主要行为。

### 6.1 认证接口

```Plain
POST /api/auth/registerPOST /api/auth/loginPOST /api/auth/password/reset-codePOST /api/auth/password/reset
```

规则：

- 注册接口不接收 `role`。

- 登录前检查用户状态。

- 重置验证码保存在 Redis，验证成功后原子删除。

- 重置成功后更新 `password_changed_at`。

- `reset-code` 对存在和不存在的邮箱返回相同提示。

### 6.2 当前用户接口

```Plain
GET /api/users/mePUT /api/users/me/passwordGET /api/users/me/weaknesses
```

用户 ID 统一从 JWT 获取，不由前端传入。

### 6.3 面试接口

#### `POST /api/interviews`

创建面试计划并返回第一道问题。

请求：

```JSON
{  "difficulty": "MEDIUM"}
```

响应主要字段：

```JSON
{  "sessionId": 1,  "status": "IN_PROGRESS",  "plannedQuestionCount": 6,  "currentQuestion": {    "interviewQuestionId": 11,    "category": "JAVA_COLLECTION",    "questionType": "MAIN",    "content": "HashMap 为什么线程不安全？"  }}
```

如果用户已有进行中会话，返回现有会话及当前问题。

#### `POST /api/interviews/{sessionId}/answers`

提交当前问题的回答，并执行 RAG、LLM 评价和后端状态决策。

请求：

```JSON
{  "interviewQuestionId": 11,  "answerContent": "……",  "requestId": "9e4ea36f-..."}
```

响应主要字段：

```JSON
{  "evaluation": {    "phase": "FINAL",    "correctnessScore": 16,    "completenessScore": 14,    "depthScore": 12,    "clarityScore": 16,    "practiceScore": 10,    "totalScore": 68,    "missingPoints": ["未说明并发扩容问题"]  },  "nextAction": "NEXT_MAIN",  "nextQuestion": {}}
```

`nextAction` 固定为：

```Plain
FOLLOW_UPNEXT_MAINFINISH
```

相同 `requestId` 的重试返回原处理结果，不重复保存回答。

#### `GET /api/interviews/current`

返回当前用户进行中的会话、当前等待回答的问题和已完成主问题数量。没有进行中会话时返回空结果。

#### `GET /api/interviews/history`

分页查询当前用户的历史面试。

#### `GET /api/interviews/{sessionId}`

查询面试详情，包括按 `display_order` 排序的问题、回答和评价。

普通用户只能查询自己的会话；管理员可以只读查询全部会话。

#### `GET /api/interviews/{sessionId}/report`

查询报告。报告尚未生成时返回当前 `report_status`。

#### `POST /api/interviews/{sessionId}/finish`

幂等完成接口。仅当所有计划主问题都已产生 `FINAL` 评价时允许执行，主要用于报告生成失败后的重试。正常流程由最后一次提交回答自动调用相同完成逻辑。

#### `POST /api/interviews/{sessionId}/cancel`

用户主动放弃当前面试。仅会话所有者可以调用，管理员不能代替用户取消。

### 6.4 会话接口权限规则

- 所有带 `sessionId` 的接口都在服务层校验会话归属。

- 普通用户只能读写自己的会话。

- 管理员可以读取其他用户的详情和报告，但不能提交回答、完成或取消其他用户的会话。

- `answers` 只接受当前 `WAITING_ANSWER` 问题。

### 6.5 管理员用户接口

```Plain
GET /api/admin/usersPUT /api/admin/users/{userId}/disablePUT /api/admin/users/{userId}/enable
```

查询接口使用分页。禁用用户后，其现有 JWT 在下一次请求时失效。

### 6.6 管理员面试接口

```Plain
GET /api/admin/interviews
```

分页查询全部用户的面试记录。详情和报告复用第 6.3 节的查询接口，通过权限判断扩大只读范围。

### 6.7 题库管理接口

```Plain
POST /api/admin/questionsPUT /api/admin/questions/{questionId}PUT /api/admin/questions/{questionId}/disablePUT /api/admin/questions/{questionId}/enableGET /api/admin/questionsGET /api/admin/questions/{questionId}
```

新增和修改题目时同时提交评分点。后端校验启用评分点权重总和为 100。修改题目和评分点使用同一事务，不物理删除已被历史面试引用的题目。

### 6.8 知识库管理接口

```Plain
POST /api/admin/knowledge/documentsGET /api/admin/knowledge/documentsGET /api/admin/knowledge/documents/{documentId}PUT /api/admin/knowledge/documents/{documentId}/disablePUT /api/admin/knowledge/documents/{documentId}/enablePOST /api/admin/knowledge/documents/{documentId}/reprocess
```

上传接口使用 `multipart/form-data`，第一版只接受 Markdown 和纯文本。上传后执行：

```Plain
保存原文→ 解析→ 切片→ Embedding→ 向量写入→ 文档状态改为 READY
```

失败时保存 `FAILED` 和错误原因。重新处理创建新版本，成功后切换有效版本。

### 6.9 管理员测试接口

```Plain
POST /api/admin/llm/testPOST /api/admin/llm/evaluate-test
```

测试接口只供管理员在开发和演示时使用，不写入正式面试数据。评价测试输入题目、回答、评分点和 RAG 片段，返回与正式评价相同的结构化结果。

### 6.10 RAG 内部接口

RAG 不向普通用户暴露独立 HTTP 接口。内部调用链为：

```Plain
InterviewService→ RagService→ KnowledgeVectorStore→ LlmService→ InterviewService 校验评价并执行状态决策
```

7. ## RAG 设计

日期：2026-07-10

设计点：确定知识入库、检索、评分输入和日志追溯方式。

### 7.1 RAG 的作用

RAG 不负责出题。题库选择主问题，RAG 在用户回答后检索与题目和回答相关的知识依据，LLM 使用这些依据完成评价、纠错和候选追问。

### 7.2 知识入库

```Plain
管理员上传文档→ 保存原文和处理状态→ 按标题、段落和语义完整性切片→ 生成 Embedding→ 写入向量存储→ 保存 chunk 与向量 ID→ 文档版本改为 READY
```

向量元数据至少包含：

```Plain
chunk_iddocument_idcategorydocument_versionstatusembedding_version
```

### 7.3 切片策略

- 优先按照 Markdown 标题、段落和语义完整性切片。

- 初始 chunk 大小为约 `300～800 tokens`。

- 初始 overlap 为约 `50～100 tokens`。

- 参数通过配置维护，开发过程中使用固定测试问题调整。

这些参数是初始值，不需要在第一版实现动态切片算法。

### 7.4 Query 构造

Query 使用稳定信息作为主体，用户回答作为受限补充：

```Plain
知识领域+ 知识点+ 当前题目+ 本题评分点关键词+ 截断后的用户回答
```

规则：

- 用户回答为空、明显离题或超过长度限制时，不加入检索 Query。

- 用户完整回答始终单独传给 LLM，不依赖 Query 保存完整内容。

- 评分点关键词用于稳定检索方向，降低错误回答把结果带偏的概率。

### 7.5 检索流程

```Plain
构造 Query→ 生成 Query Embedding→ 按 category、ACTIVE 和当前文档版本过滤→ 返回相似度最高的 4 个 chunk→ 保存 retrieval_batch_id 和命中日志→ 将 chunk 传给 LLM
```

v1.1 默认 `TopK=4`，通过配置项维护。第一版不做动态 TopK、Query Rewrite、多路召回和 Rerank。

### 7.6 LLM 评价输入与输出

输入：

```Plain
当前主问题主回答追问及追问回答（如果存在）主问题评分点快照RAG TopK 知识片段统一五维评分标准
```

结构化输出示例：

```JSON
{  "correctnessScore": 16,  "completenessScore": 14,  "depthScore": 12,  "clarityScore": 16,  "practiceScore": 10,  "strengths": ["说明了并发 put 可能覆盖数据"],  "missingPoints": ["未说明并发扩容问题"],  "correction": "……",  "scoringPointResults": [    {      "scoringPointId": 101,      "covered": true,      "evidence": "用户提到多个线程同时 put 会发生覆盖"    }  ],  "followUpRecommended": true,  "followUpQuestion": "HashMap 并发扩容时可能出现什么问题？"}
```

LLM 不输出业务状态。Java 后端校验各维度为 `0～20`，计算 `totalScore`，再执行追问规则。

### 7.7 检索日志和历史追溯

每次检索生成新的 `retrieval_batch_id`。日志记录回答、Query、过滤条件、TopK、Embedding 版本、命中 chunk、排名和相似度。评价保存实际使用的批次 ID。

知识文档重新处理时保留旧 chunk，因此历史评价仍能查看当时使用的知识内容。

### 7.8 向量存储解耦

业务层定义 `KnowledgeVectorStore`，包含第一版需要的写入、按元数据检索、停用和版本切换能力。LangChain4j 的具体 `EmbeddingStore` 实现放在基础设施层。

开发验证阶段使用 `InMemoryEmbeddingStore`。应用启动时从所有 `READY`、`ACTIVE` chunk 重建内存索引。完整 MVP 联调和部署时切换到 Milvus。

### 7.9 第一版不做

```Plain
Query RewriteRerank混合检索多路召回动态 TopKAgentic RAGGraph RAG知识图谱
```

8. ## 技术选型

日期：2026-07-10

设计点：选择能够支撑业务闭环、适合个人学习和面试说明的技术栈。

### 8.1 后端基础技术

- Spring Boot：实现 REST API、依赖注入、参数校验、统一异常处理和业务服务。

- MyBatis：保留 SQL 可见性，便于学习表关系、索引和查询设计。

- MySQL：保存用户、题库、面试、评价、报告以及知识库元数据。

- Redis：保存密码重置验证码、发送冷却和尝试次数；不保存面试核心状态。

- JWT：完成无状态接口认证。

- BCrypt：保存密码哈希。

- Knife4j：维护和调试接口文档。

- Spring Mail：通过配置化 SMTP 服务发送密码重置邮件。

### 8.2 AI 与 RAG

#### LangChain4j

用于 LLM 调用、Embedding、文档切片、结构化输出和向量存储接入。业务层仍保留自己的服务接口，避免面试流程直接依赖具体向量库。

#### DeepSeek API

用于回答分析、五维评分、纠错、候选追问和报告文本生成。模型名称、Prompt 版本和原始结果随评价记录保存。

#### Embedding 模型

在 RAG 模块开发前，通过一组中文 Java 技术问题确定具体模型，同时记录模型名称、向量维度、模型版本、中文技术文本检索效果和使用成本。同一有效知识库版本统一使用同一 Embedding 版本。

#### 向量存储

- 链路验证：`InMemoryEmbeddingStore`，启动时重建索引。

- 完整 MVP：Milvus，通过 Docker Compose 本地部署。

迁移时重新为当前有效 chunk 建立索引。上层 `RagService` 只依赖 `KnowledgeVectorStore`。

### 8.3 部署

开发前期直接本地运行 Spring Boot、MySQL 和 Redis。完成核心流程后使用 Docker Compose 部署：

```Plain
Spring BootMySQLRedisMilvus
```

第一版不加入 Kubernetes、服务治理、消息队列或分布式事务。

### 8.4 技术选型结论

当前技术栈能够覆盖 Java 后端主流程和 RAG 评价链路。Redis、Milvus 和 LangChain4j 都有明确使用位置，不为了增加技术数量而加入额外组件。

9. ## 开发日志

### 2026-07-09｜核心数据库模型设计

**开发内容：** 完成 MVP 核心数据库的逻辑设计，确定 `user`、`question`、`question_scoring_point`、`interview_session`、`interview_question`、`interview_answer`、`answer_evaluation`、`knowledge_document`、`knowledge_chunk`、`rag_hit_log`、`interview_report` 和 `user_weakness` 共 12 张表，并整理主外键、唯一约束、检查约束和索引方案。

**技术选型：** 本阶段继续沿用既定的 MySQL 关系型数据库方案，通过业务主表、过程表和结果表分别保存用户、题库、面试快照、评价与 RAG 追溯信息。当前资料未记录与其他数据库或大宽表方案的正式比较，因此不补充不存在的候选方案。

**核心实现：** 形成数据库 v1.1 逻辑基线：主问题来自固定题库；面试创建时持久化题目快照；评价区分 `INITIAL` 与 `FINAL`；报告和薄弱点仅使用最终评价；RAG 命中通过批次记录追溯。此时主要成果是字段、关系和 SQL 草案，不是 Java 业务代码。

**问题与修复：** 审查初版结构时发现 `user` 缺少 `username`、密码字段长度不足、密码修改时间命名不统一，以及 `question` 缺少 `knowledge_point`。随后补齐字段并统一命名，使结构能够支撑用户展示、BCrypt 密码、旧 JWT 失效控制和题目分类分析。

**测试验证：** 本阶段完成设计审查和建表 SQL 整理，未记录编译、自动化测试或 12 张表全部执行成功的证据。不能写成 12 张表均已在真实数据库创建并验收。

**Git 提交：** 当前资料未确认 Git 提交。

**当前状态：** 12 张表的逻辑结构和 SQL 方案已完成；此时尚未确认全部物理表创建成功，也尚未实现 Entity、Mapper、Service、Controller 或业务闭环。

**学习状态：** 已理解数据库设计需要从业务状态、历史快照和查询路径反推字段与约束，外键保证关系完整性，唯一约束和检查约束承担数据库最后一道数据保护。

**下一步：** 初始化 Spring Boot 工程，完成 Maven 与 MySQL 本地环境配置，并先验证真实数据库连接和三张基础表。

### 2026-07-10｜项目初始化与 MySQL 连接验证

**开发内容：** 完成 Java 21、Spring Boot 3.5.16、Maven、MyBatis 3.0.5 和 MySQL 8.0.45 的项目初始化；创建 `ai_interview` 数据库，并确认 `user`、`question`、`question_scoring_point` 三张基础表已创建。`DatabaseConnectionTest` 通过真实数据源执行 `SELECT 1`，验证 Spring Boot 能连接本地 MySQL。

**技术选型：** 公共 `application.yaml` 使用环境变量占位符，本地账号、密码等敏感配置放入被 `.gitignore` 排除的 `application-local.yaml`。该方案在公开仓库安全与本地开发便利之间取平衡；代价是启动和测试时必须正确激活 Profile 或提供环境变量。

**核心实现：** 配置 MySQL 驱动、DataSource、MyBatis Mapper 路径和下划线转驼峰映射；同时修正 Maven 安装目录与本地依赖仓库目录混用的问题，使工具安装与依赖缓存职责分离。

**问题与修复：** 主要风险是把数据库凭据写入公共配置。通过公共配置占位符、本地配置文件和 `.gitignore` 完成隔离。本阶段未记录新的业务代码问题。

**测试验证：** 可确认 `DatabaseConnectionTest` 已连接真实 MySQL 并成功执行 `SELECT 1`。当前资料未确认该次 Maven 的 `Tests run` 总数，也未提供其余九张表全部执行成功的证据。

**Git 提交：** 当前资料未确认本阶段独立 Git 提交。

**当前状态：** 工程和真实 MySQL 基础连接已可用，三张基础表有创建成功证据；其余九张表只有 SQL 设计和审查记录，尚不能写成全部验收完成。

**学习状态：** 已理解公共配置、本地敏感配置和运行环境变量的分工，也能区分“数据库连接成功”“SQL 设计完成”和“全部表创建成功”三个不同验证层级。

**下一步：** 实现 `User` 实体、角色与状态枚举、MyBatis Mapper 和真实数据库查询测试，为注册业务准备持久化层。

### 2026-07-12｜用户持久化层与真实 MySQL 查询

**开发内容：** 新增 `User`、`UserRole`、`UserStatus`、`UserMapper` 和 `UserMapper.xml`，实现按 ID、账号和邮箱查询用户，并启用 MyBatis 下划线转驼峰映射。

**技术选型：** 本阶段继续沿用 MyBatis XML 和真实 MySQL 集成测试。相比只 Mock Mapper，真实数据库测试能够验证 XML SQL、列名、枚举和字段映射；代价是依赖本地 Profile、数据库初始状态和测试数据清理。

**核心实现：** Mapper 接口声明 `getUserById`、`getUserByAccount` 和 `getUserByEmail`，XML 使用数据库真实列名查询并映射到 `User`。测试前后按固定账号和邮箱清理数据，保证用例可重复运行。

**问题与修复：** 测试先后暴露三个真实问题：未激活 `local` Profile 导致无法取得本地数据源；SQL 混用 Java 属性名 `passwordChangedAt` 与数据库列名 `password_changed_at`；残留测试数据触发唯一约束。分别通过 `@ActiveProfiles("local")`、修正 XML 列名和前后清理数据解决。

**测试验证：**`UserMapperTest` 明确包含 3 个连接真实 MySQL 的查询测试，分别覆盖账号、ID 和邮箱，均通过。当前资料未确认这 3 个测试是否与其他测试在同一次 Maven 全量执行中运行。

**Git 提交：**`a962ae905ab7d3ab2a811a5f3245e56fe8878d12 feat: initialize project and add user persistence layer`；`4945253ce099ba93eff5e37d9bdb7596e6403909 test: add user mapper integration tests and fix field mapping`。两项现已在远端 `main` 确认。

**当前状态：** 用户查询持久化链路已通过真实 MySQL 验证；注册、登录、HTTP 接口、JWT 和 Spring Security 尚未实现。

**学习状态：** 已理解 SQL 左侧必须使用数据库列名，MyBatis 驼峰映射只负责结果映射；真实数据库测试还必须主动管理 Profile 和可重复的数据初始状态。

**下一步：** 实现注册 DTO、Service、密码哈希和用户插入，并用独立数据场景验证重复账号、重复邮箱和正常注册。

### 2026-07-13｜用户注册 Service 与测试纠错

**开发内容：** 新增 `RegisterRequest`、`AuthService`、`BusinessException`、`PasswordConfig` 和 `UserMapper.insertUser`，完成账号与邮箱重复检查、BCrypt 密码哈希、`USER` 角色和 `ENABLED` 状态固定赋值，以及 Service 到 MySQL 的注册核心链路。

**技术选型：** 比较了 DTO 直接进入 Mapper 与 Service 显式转换为 Entity，最终选择后者，避免明文密码 DTO 越过业务层；当前不拆分 `AuthService` 接口与 Impl，避免过早抽象；只引入密码编码能力并注册 `PasswordEncoder` Bean，不提前接入完整 Spring Security。测试选择真实 MySQL而非 Mock Mapper，代价是需要本地数据库和数据清理。

**核心实现：**`AuthService` 按账号、邮箱顺序检查重复，使用 `PasswordEncoder.encode()` 生成哈希，再构造完整 `User` 交给 `insertUser`。普通注册不能由客户端指定管理员角色或用户状态。

**问题与修复：** 重复邮箱用例曾同时使用重复账号，导致先抛账号异常而“假通过”；随后还发现两个重复场景写反、测试邮箱格式非法、异常消息未断言和清理范围不完整。测试数据改为每例只触发一个分支并断言消息。构造器注入 `PasswordEncoder` 后因缺少 Bean 导致 Spring Context 启动失败，新增 `PasswordConfig` 修复；静态审查又发现 `AuthService` 误标 `@Configuration`，随后删除。

**测试验证：**`AuthServiceTest` 明确包含 3 个真实 MySQL 集成测试：重复账号、重复邮箱和正常注册。均已通过，并验证数据库密码不是明文、BCrypt `matches()` 成功、角色为 `USER`、状态为 `ENABLED`。连同上一阶段 3 个 Mapper 测试，资料明确记录 6 个测试方法，但未确认同一次 Maven 全量统计。

**Git 提交：**`e93a3504901be055aa71a19d3b0187dc3fadac8d feat: implement user registration core logic`；`e9b29ba15d419a37ea44e741c992f6bb478fd1d4 test: complete registration service integration tests`；`cb9971c67eb48a84a7e4e2c86215ea68aefe010e fix: remove incorrect configuration annotation from auth service`。均已在远端 `main` 确认。

**当前状态：** 注册 Service 到真实 MySQL 的核心链路完成；此时尚无 `AuthController`、统一异常响应或注册 HTTP 接口。

**学习状态：** 已理解 DTO、Entity、Service 和 Mapper 的职责边界，掌握 BCrypt 哈希验证、构造器注入与 Bean 注册，也认识到异常测试必须验证目标分支而不能只检查“抛了异常”。

**下一步：** 调整 `auth` 与 `user` 的模块边界，并实现注册 Controller、统一响应、参数校验和 HTTP 集成测试。

### 2026-07-14—2026-07-15｜注册 HTTP 链路与认证模块整理

**开发内容：** 将注册 DTO 和 Service 归入 `auth` 模块，新增 `AuthController`，实现 `POST /api/auth/register`。同时新增通用 `Result<T>` 和 `GlobalExceptionHandler`，统一处理业务异常、Bean Validation、请求体解析错误和未知异常，并把数据库唯一键冲突转换为可控业务错误。

**技术选型：** 模块结构选择“业务模块优先、模块内分层”，将注册、登录和 JWT 归入 `auth`，用户资料归入 `user`。异常处理比较 Controller 内重复 `try-catch` 与全局异常处理，最终选择后者。并发重复注册不增加 Java 锁，而是采用“预查询提供精确提示＋数据库唯一索引最终兜底”，代价是必须转换 `DuplicateKeyException`，且数据库约束仍是最终可信边界。

**核心实现：**`AuthController` 使用 `@RequestBody @Valid` 接收 `RegisterRequest`，Controller 只负责绑定、调用和响应；`AuthService` 负责重复校验、密码哈希与持久化；`GlobalExceptionHandler` 统一生成 `Result` JSON。注册调用链形成 `JSON → DTO → Controller → Service → Mapper → MySQL → Result`。

**问题与修复：** 预查询无法消除两个并发请求同时通过检查的窗口，因此补充数据库唯一键异常转换；包移动后同步修正测试包名和引用，避免认证与用户资料职责继续混杂。本阶段未记录新的编译或数据库故障。

**测试验证：** 资料明确记录 `AuthControllerTest` 的正常注册、空密码、重复账号 3 个 HTTP 场景，以及 `AuthServiceTest` 的正常注册、重复账号、重复邮箱 3 个业务场景，共 6 个明确场景。测试包含真实数据库写入和 BCrypt 校验，但当前资料未保留准确的 Maven 全量 `Tests run`。

**Git 提交：**`e27167372c0efbbd98cfe971a13dbaa97cb476da feat: implement auth register controller`；`e099edfd9dfb73f4c0b733b8a2f717d9ae057839 refactor: clean up auth module and update project context`。均已在远端 `main` 确认。

**当前状态：** 注册 HTTP 闭环和统一异常响应已经实现；登录、JWT 和请求鉴权尚未实现。

**学习状态：** 已理解 `@RequestBody`、`@Valid`、全局异常处理和统一响应结构的协作方式，也能解释为什么业务预检查不能替代数据库唯一约束。

**下一步：** 实现账号密码登录，控制错误提示和响应字段，并暂不提前接入 JWT。

### 2026-07-16｜账号密码登录与状态信息保护

**开发内容：** 新增 `LoginRequest` 和 `LoginResponse`，通过 `POST /api/auth/login` 完成账号查询、BCrypt 密码验证和用户状态检查。登录成功仅返回用户 ID、账号、用户名和角色，不返回 `User` Entity、密码或数据库内部时间字段。本阶段尚未签发 JWT。

**技术选型：** 本阶段继续沿用独立响应 VO 和统一 `Result`。用户不存在与密码错误比较了差异化提示和统一提示，最终统一为“账号或密码错误”，避免账号枚举；状态检查放在密码验证之后，只有掌握正确密码者才能获知账号被禁用。代价是外部错误信息更少，但安全边界更清晰。

**核心实现：**`AuthController` 完成 JSON 绑定和校验，`AuthService` 按“查询用户—验证密码—检查状态—映射 LoginResponse”执行；`PasswordEncoder.matches(raw, encoded)` 负责校验，不直接比较 BCrypt 字符串。

**问题与修复：** 初版判断顺序可能在密码错误时暴露账号禁用状态。修复为先验证凭证，再检查 `ENABLED` 状态，并新增禁用账号使用错误密码的测试，确认只返回通用凭证错误。

**测试验证：**`AuthServiceTest` 和 `AuthControllerTest` 覆盖登录成功、用户不存在、密码错误、账号禁用、非法参数，以及禁用账号使用错误密码不泄露状态。当前资料未确认准确测试方法数和 Maven 全量 `Tests run`，不能只凭“测试全部通过”补写数字。

**Git 提交：**`2ddccf0a0208c32660da14ee20c53cda80668ed8 feat: implement user login flow`；`188403b3549016334053c8bf9ea0ed63bbb63e03 fix: prevent account status disclosure during login`；`fdd7d4ed7091372b498fe766460d68526c2de80f docs: update authentication development stage`。均已在远端 `main` 确认。

**当前状态：** 账号密码登录 HTTP 链路已实现并有测试代码覆盖；尚未生成 Access Token，也没有 Spring Security 请求认证。

**学习状态：** 已理解登录验证顺序本身属于安全设计，错误消息需要在可用性和账号信息保护之间取舍，响应 VO 应采用字段白名单。

**下一步：** 引入 JWT Access Token，先完成签发、解析和独立验证，再进入请求级鉴权。

### 2026-07-17｜JWT Access Token 签发与独立验证

**开发内容：** 引入 JJWT 0.13.0 的 `jjwt-api`、`jjwt-impl` 和 `jjwt-jackson`，新增 `JwtProperties` 与 `JwtTokenService`。登录成功后签发 Access Token，`LoginResponse` 增加 `accessToken`、`tokenType` 和 `expiresInSeconds`；Claims 保存 `sub`、`account`、`role`、`iss`、`iat` 和 `exp`。

**技术选型：** 本阶段选择 HMAC-SHA256 签名的 JWT Access Token，并通过外部配置注入 Base64 密钥；资料未记录与其他 JWT 库的正式候选比较，因此不虚构替代方案。JWT 只作为签名身份凭证，不保存密码、邮箱、用户状态或完整 Entity；代价是 Token 签发后不能自动反映数据库角色和状态变化。

**核心实现：**`JwtProperties` 绑定 `secret`、`issuer` 和有效期；`JwtTokenService` 使用 `Decoders.BASE64.decode()` 恢复密钥字节，再以 `Keys.hmacShaKeyFor()` 构造 `SecretKey`，负责 Token 生成及签名、签发者、过期时间验证。Controller 不直接处理 JWT。

**问题与修复：** 明确 JWT Payload 只编码、不加密，不能放入敏感信息；Base64 字符串不能直接 `getBytes()` 当作原始密钥。另一个已识别限制是 JWT 内角色属于登录时快照，后续请求仍需查询数据库状态和角色，但本阶段尚未实现该过滤链。

**测试验证：**`JwtTokenServiceTest` 覆盖正常生成与解析、Claims、过期、篡改、错误密钥和错误 issuer；`JwtPropertiesTest` 覆盖空、零和负有效期；登录测试验证返回 `Bearer`、7200 秒有效期且不含密码和 `refreshToken`。当前资料未确认准确 `Tests run` 总数。

**Git 提交：**`c8134862b92f81054f6906ef407508289c398669 feat: implement jwt access token`；`189bcfc7dc7235d83c30814dad142e784ba44e89 docs: update jwt authentication development stage`。均已在远端 `main` 确认。

**当前状态：** JWT 签发和独立解析验证已实现；尚未接入 Spring Security、请求 Filter、接口权限、Redis、Refresh Token 或退出登录失效。

**学习状态：** 已理解 JWT 的 Header、Payload、Signature 分工，签名保证完整性而不提供加密，也能解释为什么数据库当前状态不能永久信任 Token 中的快照。

**下一步：** 先学习 Spring Security 核心 API，再实现无状态请求认证、401/403 JSON 响应和数据库当前角色授权。

### 2026-07-18｜Spring Security 无状态 JWT 请求认证

**开发内容：** 新增 `SecurityConfiguration`、`JwtAuthenticationFilter`、`RestAuthenticationEntryPoint`、`RestAccessDeniedHandler` 和 `AuthenticatedUser`，建立无状态 JWT 请求认证。注册与登录公开，其他接口默认要求认证，`/api/admin/**` 要求管理员权限；受保护请求根据 JWT `sub` 查询数据库，并以当前用户状态和角色构造权限。

**技术选型：** 比较了由 JWT Claims 直接授权和每次请求回查数据库，最终选择后者，避免禁用用户继续访问或旧管理员角色长期有效；项目采用 `SessionCreationPolicy.STATELESS`，关闭表单登录、HTTP Basic 和 Session 持久化。401/403 使用 Security 专用处理器而非只依赖 MVC 全局异常，因为过滤器异常发生在 Controller 之前。代价是每次认证增加一次数据库查询。

**核心实现：**`JwtAuthenticationFilter` 继承 `OncePerRequestFilter`，解析 Bearer Token、加载用户、检查 `ENABLED`、把 `USER/ADMIN` 转为 `ROLE_USER/ROLE_ADMIN`，创建已认证 `Authentication` 并写入新的 `SecurityContext`。公开认证接口通过 `shouldNotFilter` 跳过旧 Token 检查，允许客户端重新登录。

**问题与修复：** 审查中处理了 JWT Filter 作为 Bean 与过滤链组件可能重复注册的问题，并清理误生成说明、临时 diff、zip 和无关格式变更。相关提交恢复干净的代码范围；这也暴露出使用 `git add .` 容易带入临时文件，需要提交前检查完整暂存差异。

**测试验证：** 测试代码覆盖公开接口、无 Token 的 401、过期和篡改 Token、非法 `sub`、用户不存在或禁用、数据库新角色覆盖 JWT 旧角色、普通用户 403、管理员成功访问和无 Session 状态。阶段资料未保留可靠的 Maven 测试总数；旧学习文档中的“75 项”不是本阶段可核验汇总，不能使用。

**Git 提交：**`f45155d7e6bb6b7830a81b57f2130d376fd45193 chore: revert unrelated formatting changes`；`ae9491bb988a812ce814e2fa60285c992658f4cf docs: start spring security authentication phase`；`b758fdd232e012c447c7dde3bf420b8f8e63da04 feat: add spring security authentication framework`；`f7ea4d8407ee8eaab0725373c6f8aefc76df2a18 fix: clean up spring security authentication framework`；`df4b1d3bb5b1980b47360b6037b60774609fce15 fix: remove temporary security review artifacts`；`8dfe7da65e5045b37748c4dd7a3f89d1216a1432 chore: remove accidental framework note`。均已在远端 `main` 确认。

**当前状态：** 无状态认证、数据库状态校验和管理员授权代码已实现；准确全量测试数量未确认，RAG、外部模型和业务面试流程均未开始。

**学习状态：** 已理解 JWT 验证、认证身份恢复和授权规则是三层不同职责，并掌握 `SecurityFilterChain`、`Authentication`、`SecurityContext`、401 与 403 的边界。

**下一步：** 基于认证上下文实现当前用户资料查询，避免 Controller 再次解析 JWT，并通过 VO 控制响应字段。

### 2026-07-20｜当前用户信息查询与契约修正

**开发内容：** 新增 `UserController`、`UserService` 和 `CurrentUserResponse`，实现 `GET /api/users/me`。Controller 通过 `@AuthenticationPrincipal AuthenticatedUser` 取得当前 userId，Service 重新查询数据库并返回账号、用户名、邮箱、角色、状态和创建时间，不暴露密码、改密时间、JWT 或完整 Entity。

**技术选型：** 比较了 Controller 重新解析 JWT、直接返回 `AuthenticatedUser` 与根据 principal 的 userId 回查数据库，最终选择第三种方案，使认证逻辑只存在于 Filter，并保证资料来自当前数据库。响应使用独立 VO 而不是 Entity，代价是新增映射代码，但字段白名单更安全。

**核心实现：** 调用链为 `JwtAuthenticationFilter → SecurityContext → @AuthenticationPrincipal → UserController → UserService → UserMapper.getUserById → CurrentUserResponse`。当前用户 ID 只来自认证上下文，客户端不能通过参数查询其他用户。

**问题与修复：** 初版路径为 `/api/user/me`，生产代码和测试使用同一错误契约，因此测试仍然通过。对照设计记录后确认标准路径是 `/api/users/me`，同步修改 Controller、测试和 `AGENTS.md`，并补充 `createdAt` 断言。经验是测试只能证明实现符合测试假设，不能替代 API 契约审查。

**测试验证：** MockMvc 集成测试覆盖无 Token 返回 JSON 401和合法 Token 成功访问；成功用例使用真实 `JwtTokenService` 和 Security Filter Chain，并检查响应不含 `password`、`passwordHash`、`passwordChangedAt` 和 `accessToken`。当前资料未确认准确 `Tests run` 总数。

**Git 提交：**`7e2d8c2f8b2b021315951e7df95a10cd96cf21b3 feat: add current user endpoint`；`6ab7dfac7efaf9aadd9513d400d540bd68a84b36 fix: align current user endpoint path`。均已在远端 `main` 确认。

**当前状态：** 当前用户查询接口及路径契约已完成；用户资料修改和密码修改尚未实现。

**学习状态：** 已理解 `@AuthenticationPrincipal` 读取的是 `Authentication.getPrincipal()`，也进一步掌握 Entity 与响应 VO 的职责区别和字段白名单原则。

**下一步：** 实现登录态修改密码，并解决数据库密码变化后旧 JWT 仍可继续使用的问题。

### 2026-07-21｜登录态修改密码与旧 JWT 失效

**开发内容：** 实现 `PUT /api/users/me/password`，新增 `ChangePasswordRequest`。Service 验证当前密码、拒绝复用旧密码、生成新 BCrypt 哈希，并在同一条 SQL 和事务中更新 `password` 与 `password_changed_at`；Filter 比较 JWT `iat` 和数据库改密时间，使改密前 Token 在后续请求中返回 401。

**技术选型：** 讨论了 Redis Token 黑名单、修改全局 JWT 密钥、`token_version` 和密码修改时间戳。当前选择复用 `iat + password_changed_at`，因为无需新增 Redis 状态且改动较小；未选择全局换密钥是因为会使所有用户 Token 失效，`token_version` 保留为更严格方案。代价是时间精度和时区边界，当前采用“早于才失效、同秒暂时允许”。

**核心实现：**`UserService.changePassword` 使用 `@Transactional`，通过 `PasswordEncoder.matches()` 验证原密码和新旧差异，要求 Mapper 影响行数恰好为 1。`JwtAuthenticationFilter` 在创建 `Authentication` 前加载 `password_changed_at`；缺少必要 `iat` 或签发时间更早时抛 `BadCredentialsException`，清空上下文并交给 401 处理器。

**问题与修复：** JWT 使用 `Date`，数据库实体使用 `LocalDateTime`，直接比较类型和精度不一致。修复为按当前项目时区转换并去除纳秒，测试覆盖早于、等于和晚于。功能提交还一度混入三个过时学习文档，后续采用独立 `chore` 提交取消跟踪并加入忽略规则，没有重写已推送历史。

**测试验证：** 集成测试代码覆盖无 Token、参数错误、当前密码错误、新旧密码相同、成功更新、BCrypt 验证、旧密码登录失败、新密码登录成功、旧 Token 401和新 Token 可用。资料明确确认 4 个改密时间相关 Filter 边界测试，但未提供本阶段完整 Maven `Tests run` 总数；不能写成准确全量测试已经核验。

**Git 提交：**`44d2a02a9fac048d84e86f1c595669616bcf183d feat: add password change and invalidate old tokens`；`11f89b8b5a2454d2499f056163c5b3b4519f37e7 chore: keep local learning docs untracked`。均已在远端 `main` 确认。

**当前状态：** 改密和旧 Token 撤销链路已有代码与测试覆盖；同一秒边界仍是明确接受的 MVP 技术债，尚未使用 Redis 黑名单或 `token_version`。

**学习状态：** 已理解 JWT 签名有效不等于业务凭证仍有效，掌握 BCrypt `matches(raw, encoded)` 参数顺序、事务更新和 Filter 认证异常边界。

**下一步：** 进入管理员题库模块，先实现题目及评分点的事务创建、主键回填和批量插入。

### 2026-07-22｜管理员创建题目与评分点

**开发内容：** 实现 `POST /api/admin/questions`。管理员提交题目基础信息和评分点列表后，Service 校验评分点非空、单项权重 1～100 且总和为 100，先插入 `Question` 并取得自增主键，再生成连续 `sortOrder`、固定 `ENABLED` 状态的 `QuestionScoringPoint` 并批量写入。

**技术选型：** 题目与评分点采用一个 Service 事务创建，避免主记录成功而从记录失败。评分点比较逐条插入与 MyBatis `<foreach>` 批量插入，选择后者减少数据库交互并便于校验影响行数；题目状态和评分点状态使用两个枚举，避免相同字符串造成类型混用。代价是批量写入不回填每个评分点 ID，但当前业务不需要立即使用。

**核心实现：**`CreateQuestionRequest` 和 `ScoringPointRequest` 不接收 ID、状态或排序等内部字段。`QuestionMapper.insertQuestion` 使用 `useGeneratedKeys=true` 和 `keyProperty=id` 回填主键，`QuestionScoringPointMapper.batchInsert` 通过 `@Param` 与 `<foreach>` 写入全部评分点；`QuestionAdminService.createQuestion` 使用 `@Transactional` 控制事务。

**问题与修复：** 本阶段未记录新的代码故障。实现中主动检查题目影响行数、主键回填和评分点插入数量，任一异常均抛出运行时异常以触发事务回滚。

**测试验证：** 测试代码包含真实 Mapper 主键回填与批量插入、Service 参数及影响行数、事务回滚、MockMvc 401/403/400 和管理员成功创建，以及真实数据库中题目与评分点同时存在。当前资料未确认准确测试总数和本阶段 Maven 全量汇总。

**Git 提交：**`6e9cbdff92d09e55385595ef013fa8b73e9e9a5d feat: add admin question creation`，已在远端 `main` 确认。

**当前状态：** 管理员创建题目及评分点的事务链路已实现；题目修改、分页、详情和启停尚未实现。

**学习状态：** 已理解 MyBatis 自增主键回填、批量 INSERT、Service 事务边界和运行时异常触发回滚的机制。

**下一步：** 设计管理员修改题目接口，并在整体替换与评分点差量更新之间作出选择。

### 2026-07-23｜管理员整体替换题目与评分点

**开发内容：** 实现 `PUT /api/admin/questions/{questionId}`。Service 校验 ID、请求和评分点权重，确认题目存在后更新五个可编辑字段，删除该题旧评分点，再按新请求重新构造并批量插入评分点；题目原状态保持不变。

**技术选型：** 比较了评分点差量更新与整体替换。差量更新需要客户端提交内部 ID，并处理归属、新增、修改、删除和遗漏；当前评分点只是题目的附属规则，因此选择事务内整体替换，客户端只提交最终列表。代价是每次修改都会重写评分点，未来评分点若拥有独立引用或历史身份，再考虑版本化或差量更新。

**核心实现：**`QuestionAdminService.updateQuestion` 使用 `@Transactional` 串联题目存在性查询、基础字段 UPDATE、旧评分点 DELETE 和新评分点批量 INSERT。公共辅助方法按列表顺序生成评分点，并被创建与修改功能复用；SQL 只更新允许修改的字段，不更改 ID、状态和创建时间。

**问题与修复：** 为区分“题目不存在”和“提交内容与原值一致可能返回 0 行”，实现先查询存在性再更新；删除旧评分点返回 0 在当前“不允许空评分点”的数据约束下按异常处理，使前面的题目更新回滚。本阶段未记录独立的编译或 SQL 故障。

**测试验证：** 测试覆盖真实 Mapper 更新与删除、Service 非法参数和影响行数、Mapper 调用顺序、真实事务回滚、MockMvc 权限与参数，以及真实 JWT、Security、MyBatis、MySQL 下的评分点整体替换和其他题目隔离。当前资料未确认准确 `Tests run` 总数。

**Git 提交：**`e2d740bd84c7125f1e631559d9d631edcf998f9d feat: add admin question update`，已在远端 `main` 确认。

**当前状态：** 管理员创建和整体修改题目已实现；分页、详情和启停尚未完成。

**学习状态：** 已理解一对多附属数据整体替换的适用边界，以及为什么更新、删除、重建必须处于同一事务。

**下一步：** 实现管理员题库分页查询，统一 COUNT 与列表筛选条件，并保证稳定排序和响应字段最小化。

2026-07-24｜管理员题库分页查询

**开发内容：** 实现 `GET /api/admin/questions`，支持页码、每页数量、分类、难度、状态和关键词筛选。Controller 使用 `@Valid @ModelAttribute` 接收查询参数，Service 负责默认值、关键词清理、偏移量和总页数计算，Mapper 执行 COUNT 与分页 SELECT。

**技术选型：** 分页列表返回独立 `AdminQuestionListItem`，不加载参考答案和评分点，避免一对多 JOIN 造成重复记录。COUNT 与列表查询共用 `QuestionPageFilter` 动态 SQL；排序固定为 `updated_at DESC, id DESC`，不允许客户端传 SQL 排序字段。代价是当前采用 OFFSET 分页，数据量很大时可能需要演进为游标分页。

**核心实现：**`QuestionPageQuery` 限制 `pageNum≥1`、`pageSize` 为 1～50、关键词不超过 100；Service 使用 `long offset=(pageNum-1)*pageSize`，`total=0` 时跳过列表查询，总页数通过整数向上取整计算。关键词用 `#{}` 同时匹配 `knowledge_point` 和 `question_content`。

**问题与修复：** 两轮静态审查未发现阻塞性功能问题。确认 `AGENTS.md` 当时仍把分页标为未开发，并存在通配符导入、无用 import、`countQuestion` 命名和 `Long offset` 等非阻塞维护问题；本阶段资料没有证据表明这些项已全部单独修复。

**测试验证：** 提交中新增 Controller、Service、Mapper 和完整接口测试，覆盖参数错误、401/403、默认分页、关键词清理、偏移量、总页数、筛选组合、空结果、稳定排序和真实数据库主流程。资料明确说明没有可核验的 `Tests run/Failures/Errors/Skipped` 数字，因此只记录测试代码与审查证据，不写准确全量数量。

**Git 提交：**`8b0c302b80fe0bb1d569317694c4b9f55a9e2f4a feat: add admin question pagination`，已推送远端 `main`。

**当前状态：** 分页筛选功能已实现并经远端静态审查；准确 Maven 测试总数未确认，题目详情和启停尚未实现。

**学习状态：** 已理解 COUNT 与列表条件必须一致、稳定双字段排序的意义、OFFSET 溢出防护，以及测试通过仍需独立检查接口契约和提交范围。

**下一步：** 将题目详情查询和题目启停作为同一小阶段实现，重点验证一对多组装、最小字段更新与状态幂等。

### 2026-07-25｜管理员题目详情与启停管理

**开发内容：** 实现 `GET /api/admin/questions/{questionId}` 和 `PUT /api/admin/questions/{questionId}/status`。详情由 Service 分别查询题目主记录和按 `sort_order` 排序的评分点并组装 VO；状态接口只更新 `question.status`，重复设置相同状态时直接成功返回。

**技术选型：** 详情比较单条 JOIN 与两次查询，选择“主记录一次＋评分点一次”，避免题目字段重复返回，且单个详情不存在分页 N+1。状态修改采用最小字段 UPDATE 与幂等语义，不回写完整 Entity。代价是详情固定产生两次查询，但结构和职责更清晰。

**核心实现：** 新增 `AdminQuestionDetailResponse`、`AdminScoringPointDetail` 和 `UpdateQuestionStatusRequest`。评分点只返回 `pointType`、`content`、`weight`；状态只允许 `ENABLED/DISABLED`。Service 检查 ID、题目存在性和 Mapper 影响行数，状态相同则跳过 UPDATE。

**问题与修复：** 真实查询发现评分点详情 SQL 存在多余尾逗号，导致 MySQL 失败，修正 XML 后通过；删除 Service 中重复枚举判断，移除详情 VO 多余的评分点状态字段；同时修复上一阶段遗留的题目 UPDATE 影响行数校验，异常时不再继续删除并重建评分点。

**测试验证：** 本阶段相关测试运行 91 个，0 失败、0 错误、0 跳过；完整 `mvn test` 运行 245 个，全部通过，`BUILD SUCCESS`。覆盖真实 MyBatis、Service、MockMvc 和完整 JWT/Security/MySQL 集成，包括 401、403、详情组装、状态持久化、幂等和题目隔离。

**Git 提交：**`2b2b2ba5fb861676bc3093b8e16a708565d88ae7 feat: add admin question detail and status management`，已推送远端 `main`。

**当前状态：** 管理员题库 MVP 已具备创建、整体修改、分页、详情和启停五项能力；尚未开始知识文档、Embedding 或向量存储。

**学习状态：** 已理解一对多详情不必强行使用 JOIN、状态接口幂等的含义，以及最小字段更新可避免覆盖无关或并发修改。

**下一步：** 进入知识库模块，先实现管理员上传 Markdown/TXT 文档、正文规范化、内容哈希和原文元数据持久化。

### 2026-07-27｜知识文档上传与原文元数据持久化

**开发内容：** 实现 `POST /api/admin/knowledge/documents`，以 `multipart/form-data` 接收文件、标题、分类和可选来源。当前支持 UTF-8 的 `.md`、`.markdown` 和 `.txt`，上限 5 MiB；服务端清理客户端路径、严格解码、去除 BOM、统一换行、拒绝空白正文、计算 SHA-256，并把规范化正文与元数据写入 `knowledge_document`。

**技术选型：** 文件类型主要按清理后的扩展名白名单判断，不只信任不稳定的 Content-Type；正文直接保存 MySQL，不使用本地磁盘 `transferTo()`，便于与版本、状态和元数据统一管理。哈希在 BOM 与换行规范化后计算，使相同正文跨系统保持稳定。代价是大文件受数据库字段和内存读取限制，因此 MVP 设置 5 MiB 上限。

**核心实现：** Controller 使用 `@Valid @ModelAttribute`；Service 完成文件校验、严格 UTF-8 Decoder、 删除、CRLF/CR 转 LF、SHA-256 和 Entity 构造；Mapper 插入并回填主键。后端固定 `documentVersion=1`、`processingStatus=UPLOADED`、`errorMessage=null`，响应 VO 不暴露正文、哈希和错误详情。

**问题与修复：** 审查修复了上传 DTO 校验注解与字段类型不匹配、Mapper INSERT 漏写 `fileType`、读取前未先拒绝空文件、独立 CR 未规范化、文件名路径及长度边界不足、`konwledge` 包名拼写错误，并删除误加入的 Kotlin 依赖和构建插件，保持纯 Java 构建。

**测试验证：** 知识库相关测试运行 62 个，0 失败、0 错误、0 跳过；完整回归运行 307 个，0 失败、0 错误、0 跳过，`BUILD SUCCESS`。包含真实 MyBatis/MySQL、Service、MockMvc 和完整 JWT/Security 集成；`MockMultipartFile` 只模拟上传文件对象，数据库验证仍为真实 MySQL。

**Git 提交：**`d271a2e9c41be9b545414bc41b5b29b60c804434 feat: add knowledge document upload`，已推送远端 `main`。

**当前状态：** 原文和元数据上传闭环已完成，文档状态停留在 `UPLOADED`；尚未切片、生成 Embedding、写入 Milvus 或推进 `READY/FAILED`。

**学习状态：** 已理解 multipart 参数绑定、严格 UTF-8 解码、BOM 与换行规范化、内容指纹，以及文档版本与处理状态是两个独立概念。

**下一步：** 实现可独立测试的文本切片器；在没有向量和版本元数据前，只生成内存草稿，不写入正式 `knowledge_chunk`。

### 2026-07-28｜知识文档文本切片器第一版

**开发内容：** 新增 `KnowledgeChunkDraft` 和 `KnowledgeTextChunker`。组件输入已规范化正文，输出内存中的有序切片草稿；每个草稿只包含从 1 连续递增的 `chunkIndex`、`content` 和最终文本的 `characterCount`。

**技术选型：** 比较当前可控的字符规则切片与尚未接入的 Tokenizer、Markdown 结构感知和语义切片，MVP 选择最大 1200 个 Java 字符、约 150 字符重叠、自然边界优先的规则方案。其实现简单、确定且易测试；代价是 `String.length()` 不是模型 Token 数，且不理解标题、代码块或真实语义变化，后续可替换升级。

**核心实现：**`split()` 维护起点、硬终点、实际边界和切片编号；`findNaturalBoundary()` 按段落、普通换行、中英文句末标点、最大长度硬切的顺序选择边界；`calculateNextStart()` 计算重叠并尽量避免英文从单词中间开始，同时保留非推进防御。

**问题与修复：** 本阶段生产代码刚完成，尚未取得正式代码审查结果，因此不把随后发现的问题提前写成本日已修复事项。已识别的局限是字符数不等于 Token 数、没有 Markdown 或语义感知。

**测试验证：** 本日未确认已执行的自动化测试数量、编译结果或全量回归；不能把后续 27 个切片测试和 334 个全量测试倒填到本日。

**Git 提交：** 当前资料未确认本日 Git 提交。

**当前状态：** 切片生产代码已编写，但尚未完成审查与测试闭环；没有新增 Controller、Mapper 或 SQL，没有写入 `knowledge_chunk`，文档状态仍为 `UPLOADED`。

**学习状态：** 已理解最大长度、自然边界和重叠分别用于控制输入规模、减少语义硬切和保留跨片上下文，也明确临时切片对象不能伪装成正式向量记录。

**下一步：** 围绕公开 `split()` 方法补充确定性单元测试并执行第一次代码审查，重点检查边界查找、`strip()`、重叠推进和并发复用。

### 2026-07-29｜文本切片器测试设计与审查准备

**开发内容：** 完成切片原理复盘、测试方案和 Codex 审查任务设计。测试只通过公开 `split()` 验证行为，不反射调用私有方法，也不为了测试扩大私有方法可见性；同时要求同步更新 `AGENTS.md` 的切片参数、边界与未实现范围。

**技术选型：** 本阶段继续沿用 JUnit 5 快速单元测试，不启动 Spring Context、不连接 MySQL 或外部服务。参数化测试覆盖中英文句末符号，辅助方法生成确定长文本；不使用随机数据、当前时间或 `Thread.sleep()`，保证可重复。代价是只验证纯函数行为，不覆盖尚不存在的业务流水线。

**核心实现：** 测试设计覆盖 null/空白、短文本、恰好上限、超限一字符、长文本硬切、段落/换行/句末优先级、重叠、连续编号、原文覆盖、英文单词边界、无空格中文、超长英文单词、确定性和单例并发复用。

**问题与修复：** 审查重点已明确，包括段落 `lastIndexOf` 是否越过当前窗口、`strip()` 对计数和上下文的影响、英文边界调整是否缩短重叠、无空格文本是否可能死循环，以及 Spring 单例是否存在共享可变状态；本日尚未收到最终问题清单和修复结果。

**测试验证：** 截至本日仅完成测试设计和审查任务下发，未确认实际执行数量、`BUILD SUCCESS` 或全量回归结果。

**Git 提交：** 当前资料未确认 Git 提交。

**当前状态：** 生产代码已完成、测试与审查仍在进行；不能用后续 `ac1f020` 和 334 个全量测试覆盖本日状态。

**学习状态：** 已能解释切片器公开方法及私有辅助方法的职责，也理解应通过公开输入输出间接验证内部逻辑；切片质量会直接影响向量数量、存储规模和召回粒度。

**下一步：** 等待审查结果，修复真实问题，执行切片定向测试和全量回归后再提交；随后进入 Embedding 客户端选型与实现。

### 2026-07-30—2026-08-02｜文本切片、Embedding 与 Milvus 基础链路

**开发内容：** 完成切片器审查与测试闭环，实现百炼 Embedding 请求客户端，以及 Milvus Collection 初始化和批量插入请求构造。当前三个组件可按“正文切片—请求生成向量—构造 Milvus 写入”组合使用，但尚未由业务 Service 编排成完整文档处理流水线。

**技术选型：** 切片继续采用 1200 字符、150 字符重叠和自然边界优先，Tokenizer、Markdown 结构感知与语义切片暂缓。Embedding 选择百炼 `qwen3.7-text-embedding`、1024 维、单批 20 条；在 Spring AI `EmbeddingModel` 与自定义客户端间选择项目自有 `EmbeddingClient + RestClient`，以控制批处理、索引恢复和异常，代价是自行维护 DTO、超时和供应商适配。向量存储比较 Spring AI Milvus VectorStore 与 Milvus Java SDK，最终选择官方 SDK 2.6.20 并置于自有 `VectorStoreClient` 后；Spring AI 保留为后续替换方案。

**核心实现：**`KnowledgeTextChunker` 输出 `KnowledgeChunkDraft` record；`DashScopeEmbeddingClient` 负责分批请求、局部索引转全局索引、乱序恢复、模型、数量、1024 维和有限值校验；`MilvusVectorStoreClient` 构造批量 `InsertReq`。Collection 以 Java 生成的 `vector_id` 为主键，保存 `document_id`、`chunk_index`、`embedding_version` 和向量，使用 `AUTOINDEX + COSINE`。删除和检索方法仍抛出 `UnsupportedOperationException`。

**问题与修复：** 切片器修复了非空切片判断、段落边界跨越硬终点和提示文字错误。Milvus 阶段修复有限浮点数判断取反、空 `documentId` 自动拆箱、超时校验取反、`List.copyOf()` 早于空元素检查、错误 `hasText` 调用、SDK 废弃 Schema API，以及未校验空响应和插入数量；PowerShell 拆分 Maven `-Dincludes` 参数则通过整体加引号解决。

**测试验证：** 切片器定向测试 27 个、阶段全量 334 个；Embedding 定向测试 80 个、阶段全量 414 个；Milvus 定向测试 83 个、最终全量 497 个。各次均为 0 失败、0 错误、0 跳过并记录 `BUILD SUCCESS`。Embedding HTTP 使用 `MockRestServiceServer`；Milvus 使用 Mockito、`ApplicationContextRunner` 和构造器 Mock，均未连接真实外部服务。

**Git 提交：**`ac1f020c7875670d1078222dd94555bc9fa0bbbf feat: add knowledge text chunker`；`8b62020d5e5d229be27b1396c0bb2924baf5dc24 feat: add DashScope embedding client`；`d6e2bd6457d2a230ec888a3f0b5cbf59fa41bab3 feat: add tested Milvus vector storage infrastructure`。三项均已进入远端 `main`，当前 `main` 指向最后一项提交。

**当前状态：** 已完成切片生产代码、Embedding 请求构造与 Mock 响应验证、Milvus Schema/InsertReq 构造及插入响应校验。尚未真实调用百炼或 Milvus，尚未持久化 `knowledge_chunk`，也未实现状态推进、删除、检索、补偿、重试和 RAG；不能描述为真实向量已经存入 Milvus。

**学习状态：** 已理解切片粒度与重叠、Embedding 与生成式 LLM 的区别、供应商索引恢复、MySQL 与 Milvus 的职责边界，以及 Mock 测试不能替代真实外部服务联调。

**下一步：** 先设计并实现按 `vectorId` 和 `documentId` 删除的补偿能力，再实现带 `embeddingVersion` 过滤的相似度检索和既有 Collection 的 Schema 一致性检查；随后设计文档状态推进、MySQL/Milvus 幂等、失败补偿与重试，最后组装完整文档处理流水线。

### 2026-08-05—2026-08-10｜真实 Embedding 与 Milvus 联调闭环

**开发内容：** 在 Milvus 批量写入、删除和相似度检索能力已经完成的基础上，本阶段没有继续扩展 RAG 业务，而是优先补齐此前缺失的真实外部服务验证。新增 `RealEmbeddingSmokeTest` 和 `RealMilvusVectorStoreSmokeTest`，分别验证真实百炼 Embedding，以及“Embedding—Milvus 写入—COSINE 检索—按 vectorId 删除—按 documentId 删除—数据清理”的完整基础链路；同时补充真实测试安全开关、local Profile、普通测试隔离和 Agent 测试状态表达规范，最终完成 B2 Milvus 基础设施阶段闭环。

**技术选型：** 真实联调没有额外增加调试 Controller 或生产 Service，而是继续放在 JUnit/Spring Boot 测试体系中，通过现有 `EmbeddingClient` 和 `VectorStoreClient` 直接验证生产实现，避免为了测试引入业务接口。真实配置继续使用被 Git 忽略的 `application-local.yaml`，没有把 API Key、数据库密码等复制到 `application-test.yaml`；两个 Smoke Test 使用 `@ActiveProfiles("local")` 获取真实配置，同时通过 `RUN_REAL_EMBEDDING_TEST` 和 `RUN_REAL_MILVUS_TEST` 显式开启。安全保护最初可以只依赖 `@EnabledIfEnvironmentVariable`，但考虑到真实 Embedding 会产生外部请求、Milvus 测试会真实写删数据，最终保留 `@EnabledIfEnvironmentVariable + Assumptions.assumeTrue(...)` 双层保护，以牺牲少量重复代码换取误执行保护。真实测试正式入口则在 IDEA 与 Maven/Surefire 间选择后者，因为 IDEA 强制执行 disabled JUnit Test 时可能绕过第一层条件判断，而 Maven 的条件执行行为更加稳定且可复现。

**核心实现：** `RealEmbeddingSmokeTest` 不只检查“请求成功并返回向量”，而是在真实调用前固定验证模型必须为 `qwen3.7-text-embedding`、维度必须为 1024、Profile Version 必须为 `qwen3.7-text-embedding-1024-dense-v1`。这一约束来自代码审查时发现的风险：如果测试仅把返回值与当前配置互相比较，那么配置本身填错时也可能形成“错误配置与错误结果相互一致”的假通过，因此真实 Smoke Test 应直接验证当前项目基础设施约定。

`RealMilvusVectorStoreSmokeTest` 使用 A/B/C 三组唯一测试数据：A、B 属于同一个 `documentA`，但 `chunkIndex` 不同；C 属于另一个 `documentB`。这样既可以验证 `deleteByVectorIds(A)` 只删除单个向量，也可以继续通过 `deleteByDocumentId(documentA)` 验证文档级删除，同时利用 C 检查非目标数据没有被误删。检索最初只考虑确认 A 能出现在 topK 中，但审查后认为这不足以证明 COSINE 检索语义正确，因此增强为 A 自身向量检索时必须排名第一，`documentId`、`chunkIndex`、`embeddingVersion` 必须正确，score 必须为有限值且接近 1，并验证全部返回结果按 1e-6 容差保持非递增。没有为了方便测试新增“按 vectorId 查询”的生产接口，而是继续利用现有相似度检索观察数据状态，避免测试需求反向污染生产 API。

Milvus 写入和删除存在数据可见性延迟，因此没有使用固定 `Thread.sleep` 猜测等待时间，而使用 Awaitility 根据实际条件轮询；测试结束也没有 drop Collection 或全量清空，而是在 `finally` 中只删除本次 UUID 对应的数据。这样虽然测试代码更长，但能够同时降低时序不稳定和误删其他开发数据的风险。

**问题与修复：** 第一轮静态审查发现两个 P1 问题：一是 self-search 只检查 A 是否存在，没有验证第一名和 score 排序，无法真正证明 COSINE 检索结果正确；二是 Embedding 模型和维度只与配置属性比较，错误配置可能自洽通过。前者补充第一名、score 接近 1 和整体非递增断言，后者改为在请求前固定检查模型、1024 维和 Profile Version。对于 token count 则没有继续增加严格数值断言，因为供应商 token 统计属于容易变化的实现细节，不应让 Smoke Test 因无关变化变脆。

第一次在 IDEA 中直接运行 `RealMilvusVectorStoreSmokeTest` 时真实 Embedding、insert、search 和 delete 实际已经成功，但检查启动参数发现 IDEA 动态加入了 `-Djunit.jupiter.conditions.deactivate=org.junit.*Enabled*Condition`，意味着 `@EnabledIfEnvironmentVariable` 第一层保护被运行器绕过。进一步检查 Run Configuration 后确认手工 VM options 只有 `-ea`，因此没有修改测试去适配 IDEA，而是将 Maven/Surefire 确定为正式执行入口。随后在不设置 `RUN_REAL_MILVUS_TEST` 时通过 Maven 得到 `1 test / 1 skipped / BUILD SUCCESS`，证明默认安全门有效；再临时设置开关运行，得到 `1 test / 0 failures / 0 errors / 0 skipped / BUILD SUCCESS`，完成正式真实联调。

普通完整测试第一次由 Codex 执行时出现 `575 tests / 1 failure / 85 errors / 2 skipped / BUILD FAILURE`，Hikari 收到的 JDBC URL 是未解析的 `${DB_URL}`。最初需要判断这是新增 Smoke Test 引起的代码回归，还是运行环境问题；检查后发现 Codex 子进程没有继承 `DB_URL`、`DB_USERNAME` 和 `DB_PASSWORD`，而用户 PowerShell 中三个变量均为 `Process=True、User=True`。因此没有为了让 Codex 测试通过去修改 `application.yaml` 或把真实数据库配置写入 `application-test.yaml`，而是在能够正确继承环境变量的本地 PowerShell 中重新执行完整测试，最终恢复为 0 失败、0 错误。这一问题也促使 `AGENTS.md` 增加“执行环境缺失不能直接判定为代码失败”的约束。

**测试验证：** 默认关闭真实测试开关时，两个 Real Smoke Test 定向执行均正常 skipped；Maven 无开关执行 `RealMilvusVectorStoreSmokeTest` 为 1 个测试、1 个跳过、`BUILD SUCCESS`，确认第一层安全保护有效。显式临时开启 `RUN_REAL_MILVUS_TEST=true` 后，真实测试完成百炼 Embedding、Milvus insert、COSINE search、`deleteByVectorIds`、`deleteByDocumentId` 和 targeted cleanup，结果为 1 个测试、0 失败、0 错误、0 跳过、`BUILD SUCCESS`。最终普通完整测试为 575 个、0 失败、0 错误、2 跳过、`BUILD SUCCESS`；两个跳过项正是 `RealEmbeddingSmokeTest` 和 `RealMilvusVectorStoreSmokeTest`，说明普通回归与真实外部服务测试之间的默认隔离有效。

**Git 提交：** `8aceb06bdb5f34b2a971f9930d6ec2a41abf834f test: add guarded real embedding and Milvus smoke tests`，提交两个真实 Smoke Test 和对应 Agent 安全执行规范。推送后再次检查远端 `AGENTS.md`，发现 Current Development Stage 仍保留“真实百炼和真实 Milvus 尚未验证”的旧描述，与刚完成的真实测试冲突，因此没有直接进入下一阶段，而是追加 `f5dfcfed6313e49b33f01a3de81f72ff12a14063 docs: sync completed Milvus B2 stage status` 修正状态。两次提交均已进入远端 `main`，最终 `main` 指向 `f5dfcfed6313e49b33f01a3de81f72ff12a14063`。

**当前状态：** B2 Milvus 基础设施阶段已完成。`MilvusVectorStoreClient` 的批量写入、按 vectorId 删除、按 documentId 删除和带 `embeddingVersion` 过滤的 COSINE 相似度检索均已有生产实现；Mock/单元测试、真实百炼 Embedding、本地 Docker Milvus CRUD Smoke Test 和普通完整回归均已通过，并建立了默认跳过和显式 opt-in 的真实测试机制。当前验证范围仍是本地 Docker Milvus，不代表生产或远程 Milvus 已部署；Existing Collection Schema 一致性检查、真实网络超时后的最终状态与恢复行为仍未完成。

**学习状态：** 已理解真实 Smoke Test 不能只以“接口没有报错”为目标，而应先确定要证明的工程事实，再反推测试数据和断言；相似度检索不仅要确认能查到数据，还应检查 self-search 排名和 score 顺序，删除也不能只确认目标消失，还要验证非目标数据继续存在。进一步理解了 Mock、真实联调、JUnit 条件执行、IDEA Runner、Maven/Surefire 和操作系统进程环境之间的区别，以及为什么环境变量缺失、编译成功、Mock 通过和真实联调通过必须作为不同状态记录。

**下一步：** 不再继续扩展 B2 Milvus 基础能力，进入“知识文档处理流水线设计与实现”。先明确 `knowledge_document` 从 `UPLOADED → PROCESSING → READY / FAILED` 的状态推进规则，以及现有 `KnowledgeTextChunker` 与 `knowledge_chunk` 持久化的关系；随后确定“文本切片—MySQL chunk 写入—Embedding—Milvus 写入”的调用顺序和事务边界，再设计 MySQL、Embedding、Milvus 任一步骤部分失败后的幂等、补偿、重试与恢复策略。上述边界确定后再进入 Service 实现，RAG retrieval orchestration 放在文档入库链路稳定之后。

### 2026-08-11—2026-08-12｜完成知识文档处理 Pipeline 与 Milvus 阶段收口

**开发内容**

本阶段完成知识文档入库 Pipeline，将此前已有的文本切片、Embedding 和 Milvus 能力串联起来：

```Plain
UPLOADED→ PROCESSING→ 文本切片→ Embedding→ Milvus 向量写入→ knowledge_chunk 持久化→ READY
```

同时补充 `KnowledgeChunk` 实体与 Mapper、文档状态推进、MySQL 短事务、失败补偿，以及 Existing Collection Schema/Index 一致性检查。

**技术选型**

1. **token_count 允许 NULL**

当前 Embedding 接口只能获得批次总 token 数，无法得到可靠的 per-chunk token 数。

没有选择用字符数或平均 token 数代替，而是将：

```Plain
token_count
```

调整为允许 `NULL`，表示“当前暂无可靠统计”，后续接入 Tokenizer 后再补充。

2. **文档状态使用数据库 CAS**

没有采用 `select → Java 判断 → update`，而是通过条件 UPDATE 实现：

```Plain
UPLOADED → PROCESSINGPROCESSING → READY / FAILED
```

避免同一文档被多个处理流程重复抢占。

3. **不使用跨外部服务的大事务**

Pipeline 涉及 MySQL、Embedding HTTP 和 Milvus，Spring `@Transactional` 无法统一回滚这些系统。

因此采用：

```Plain
Milvus 写入→ MySQL 短事务→ 失败时显式补偿
```

MySQL 中 `knowledge_chunk` 写入与 `READY` 状态更新放在独立短事务中。

**核心实现**

新增：

- `KnowledgeChunk`

- `KnowledgeChunkMapper`

- `KnowledgeDocumentProcessingService`

- `KnowledgeDocumentProcessingTransactionService`

每个 chunk 生成唯一 `vectorId`，同一个 ID 同时保存到 MySQL 和 Milvus，用于数据关联和失败后的精准补偿。

Pipeline 在 Milvus 写入被尝试后，如果后续失败，会根据本次 `vectorIds` 调用 `deleteByVectorIds`，避免留下孤立向量。

Milvus Existing Collection 也由原来的“存在即跳过”调整为：

```Plain
存在→ 校验 Schema→ 校验 Index→ 一致则启动→ 不一致则 fail fast
```

不会自动 drop 或重建已有 Collection。

**问题与修复**

开发过程中主要处理了：

- `KnowledgeChunkMapper.xml` 初版存在字段遗漏、逗号和 `vectorId` 拼写问题，修复后通过真实 MySQL 验证。

- 原数据库 `token_count NOT NULL` 与当前 Pipeline 实际能力冲突，最终改为 nullable，并同步 SQL 基线。

- Processing Service 初版存在事务边界和 Bean 依赖问题，拆分独立 Transaction Service。

- Milvus disabled 时 Processing Service 无法正常装配，增加条件装配。

- 真实测试过程中多次遇到 Maven 子进程无法继承数据库、Embedding 等环境变量的问题，确认属于执行环境问题，没有为此修改生产配置。

**测试验证**

最终回归结果：

```Plain
Focused Unit/Mock：200 passed普通完整测试：627 tests0 failures0 errors13 skipped真实 MySQL Integration：6 passed真实完整 Pipeline：通过真实失败补偿：2 passed真实 Milvus Schema 校验：2 passedB2 Real Milvus 回归：通过
```

真实 Pipeline 已验证：

```Plain
MySQL→ Chunker→ DashScope Embedding→ Milvus→ MySQL→ READY
```

Embedding 使用：

```Plain
qwen3.7-text-embeddingdimension = 1024profile = qwen3.7-text-embedding-1024-dense-v1
```

同时验证了 MySQL/Milvus `vectorId` 对应、COSINE 自检索 top1、真实事务 rollback 和真实跨存储补偿。

**Git 提交**

当前尚未提交、尚未推送。

`AGENTS.md` 已同步 C1 最终完成状态。

部分文件此前存在 `AM` 状态，因此提交前需要重新完整暂存最终工作树，避免提交早期 staged 版本。

**当前状态**

C1 知识文档 Ingestion / Milvus 阶段已经完成：

```Plain
文档状态机ChunkingEmbeddingknowledge_chunk 持久化Milvus Insert/Search/DeletePipeline 编排MySQL 短事务跨存储失败补偿Existing Collection Schema/Index 校验最终完整回归
```

尚未完成的增强项包括真实网络 timeout 故障复现、FAILED retry、文档重处理、可靠 per-chunk token 统计和生产 Milvus 部署。

**学习状态**

本阶段重点理解了：

- 数据库 CAS 与并发状态抢占；

- `@Transactional` 的边界；

- MySQL 与 Milvus 跨存储一致性；

- 外部调用异常下的“不确定状态”；

- 显式补偿与异常保留；

- Mock、真实数据库测试和真实外部服务测试之间的区别；

- 基础设施 Schema fail-fast 校验。

**下一步**

先完成：

```Plain
重新完整暂存→ staged diff 审查→ commit→ push→ 远端复核
```

完成 C1 Git 收口后，再正式进入 **RAG Retrieval 设计与实现**。

### 2026-08-17｜完成 RAG Retrieval R1：从语义 Query 到结构化 Evidence

**开发内容**

本阶段开始实现 RAG 的 Retrieval 第一版，将此前已经完成的知识入库能力真正用于语义检索。

此前 C1 已经解决：

KnowledgeDocument

→ 文本切片

→ Embedding

→ Milvus 向量写入

→ knowledge_chunk 持久化

→ READY

但此时系统只是“能够把知识存进去”，还没有真正具备：

用户 Query

→ 找到相关知识

因此本阶段新增 Retrieval R1，最终链路为：

Query

→ EmbeddingClient

→ Query Vector

→ Milvus COSINE TopK Search

→ VectorSearchHit

→ MySQL 批量回表

→ 业务有效性过滤

→ RetrievedChunk

→ RetrievalResult

其中：

- Milvus 负责语义相似度检索和排序；

- MySQL 负责判断知识当前是否仍然有效，并提供正文及文档元数据；

- Java Retrieval Service 负责串联两者并组装最终 Evidence。

本阶段新增：

KnowledgeRetrievalRow

RetrievedChunk

RetrievalResult

KnowledgeRetrievalService

并扩展：

KnowledgeChunkMapper

KnowledgeChunkMapper.xml

---

**技术选型**

1. **Query Embedding 继续复用现有** **`EmbeddingClient`**

没有为 Retrieval 单独创建：

QueryEmbeddingClient

而是直接复用：

EmbeddingClient.embed(List<String> texts)

原因是无论知识入库还是 Query 检索，本质都是：

Text

→ Embedding Model

→ Vector

两者使用的是同一种能力。

如果额外创建 Query 专用接口，会出现：

DocumentEmbeddingClient

QueryEmbeddingClient

但底层实际上仍然调用同一个 Embedding Provider，反而制造重复抽象。

因此 R1 保持：

EmbeddingClient

作为统一 Embedding 边界。

同时 Retrieval 不自己读取配置推测 Embedding Version，而是直接使用本次真实返回的：

EmbeddingBatchResult.profileVersion()

保证 Query Vector 与 Milvus 中被搜索的向量处于同一个 Embedding Profile。

---

2. **相似度计算和 TopK 排名交给 Milvus**

R1 没有在 Java 中重新实现：

cosine similarity

也没有拿到候选结果后重新按照 score 排序。

当前职责是：

Milvus

→ COSINE

→ TopK

→ relevance ranking

Java 只接收：

List<VectorSearchHit>

原因是向量数据库本身就是专门为这一类操作设计的。

如果 Java 再：

读取大量 Vector

→ 自己计算 cosine

→ 自己排序

不仅重复 Milvus 已有能力，也会增加：

- 向量传输成本；

- Java 计算成本；

- 实现复杂度；

- 两套排序逻辑不一致的风险。

因此 R1 明确：

Milvus 决定“语义上谁更相关”

Java 不重新计算相关性

---

3. **Milvus 命中后必须回 MySQL，而不是直接使用向量结果**

Milvus 中目前只保存检索需要的最小字段：

vector_id

document_id

chunk_index

embedding_version

vector

没有保存完整：

Chunk content

Document title

category

source

processing_status

chunk status

current document version

因此：

Milvus 命中

≠

这个 Chunk 当前一定还能使用

例如某个向量可能属于：

旧 documentVersion

INACTIVE Chunk

DISABLED Document

FAILED Document

所以 R1 采用：

Milvus

负责语义召回

MySQL

负责业务有效性 + 正文

真正可以进入 RetrievalResult 的 Chunk 必须满足：

knowledge_chunk.status = ACTIVE

knowledge_document.processing_status = READY

knowledge_chunk.document_version

=

knowledge_document.document_version

knowledge_chunk.embedding_model

=

本次 Embedding model

knowledge_chunk.embedding_version

=

本次 profileVersion

因此 MySQL 仍然是业务数据的 Source of Truth。

---

4. **Milvus → MySQL 使用** **`vectorId`** **作为主要关联键**

最初可以考虑两种方案：

方案 A：

vectorId

方案 B：

documentId + chunkIndex

R1 最终选择：

vectorId

原因是 C1 入库阶段已经保证：

同一个 UUID

↓

knowledge_chunk.vector_id

↓

Milvus Primary Key

并且 MySQL 中：

vector_id UNIQUE

因此 `vectorId` 是天然的一对一跨存储 Identity。

同时没有完全放弃：

documentId

chunkIndex

它们继续用于交叉一致性检查。

例如：

Milvus:

vectorId = X

documentId = 10

chunkIndex = 3

MySQL:

vectorId = X

documentId = 11

chunkIndex = 3

虽然 vectorId 能找到记录，但这说明 MySQL 与 Milvus 已经出现数据不一致。

因此 Service 会直接抛出：

IllegalStateException

而不是静默继续使用这条数据。

---

5. **MySQL 回表采用一次 Batch IN，不做 N+1**

没有采用：

Milvus Hit 1 → SELECT

Milvus Hit 2 → SELECT

Milvus Hit 3 → SELECT

...

而是：

收集所有 vectorId

→ 一次 SELECT ... WHERE vector_id IN (...)

即：

Milvus TopK

→ List<vectorId>

→ MySQL Batch Query

这样数据库查询次数不会随着 TopK 线性增加。

---

6. **MySQL 查询结果不负责恢复相关性排名**

SQL：

WHERE vector_id IN (...)

不能保证结果按照传入的 vectorId 顺序返回。

因此不能直接：

遍历 MySQL rows

→ 返回 RetrievalResult

R1 采用：

MySQL rows

→ Map<vectorId, KnowledgeRetrievalRow>

然后：

遍历 Milvus hits

→ 根据 vectorId 查 Map

→ 组装 RetrievedChunk

这样最终顺序仍然由：

Milvus relevance ranking

决定。

这里的 Java 操作只是**恢复原排名**，不是重新 rerank。

---

7. **MySQL 过滤后不强制补满 TopK**

例如：

Milvus Top5

rank1 A

rank2 B

rank3 C

rank4 D

rank5 E

其中：

B → Document 已失效

D → Chunk 已 INACTIVE

最终 Retrieval 可以返回：

A rank1

C rank3

E rank5

而不是强制再调用 Milvus 找两个结果补成 5 个。

因此：

requestedTopK = 5

不代表：

items.size() == 5

并且保留：

vectorRank = 1 / 3 / 5

而不是重新压缩成：

1 / 2 / 3

这样可以保留真实召回来源和排名信息。

R1 暂时不实现：

oversampling

自动补满 TopK

重复向量检索

避免第一版过早增加复杂度。

---

8. **R1 暂不增加 Similarity Score Threshold**

当前也没有规定：

similarityScore < 某个值

→ 丢弃

原因是目前还没有构建真实标注的 Retrieval Evaluation Dataset。

在缺少数据的情况下直接指定：

0.6

0.7

0.8

本质上只是人为猜测。

所以第一版保留所有 Milvus TopK 返回的有限 score，包括：

0

负有限值

后续如果有真实 Retrieval Evaluation 数据，再决定：

threshold

reranker

TopK

如何调整。

---

9. **Retrieval 与 Prompt / LLM Orchestration 分离**

R1 最终只返回：

RetrievalResult

└── List<RetrievedChunk>

没有直接：

拼 Prompt

调用 LLM

写 answer_evaluation

写 rag_hit_log

职责划分为：

Retrieval

= 找什么知识

Prompt / Evaluation Orchestration

= 怎么把这些知识交给 LLM

因此 Retrieval Service 不需要知道：

answerId

sessionId

interviewQuestionId

也不会修改面试业务状态。

这样后续无论 Query 最终采用：

用户答案

还是：

面试题 + 用户答案

或者：

知识点 + 面试题 + 用户答案

都可以由上层 Orchestration 决定，而不需要重写 Retrieval。

---

**问题与修复**

10. **Retrieval SQL** **`<foreach>`** **遗漏参数**

第一版 Mapper XML 中写成：

<foreach collection="vectorIds"

item="vectorId"

open="("

separator=","

close=")">

</foreach>

遗漏：

#{vectorId}

导致实际 SQL 无法正确构造：

WHERE vector_id IN (...)

静态审查发现后补充：

#{vectorId}

---

11. **SQL 列名出现拼写错误**

第一版存在：

docuemnt_version

soruce

而数据库真实字段为：

document_version

source

如果进入真实 MySQL 会直接出现：

Unknown column

因此在进入 Integration Test 前完成修复。

---

12. **MySQL rows 错误使用** **`content`** **作为 Map Key**

第一版实现中：

Collectors.toMap(

KnowledgeRetrievalRow::getContent,

Function.identity()

)

但后续实际上执行：

rowByVectorId.get(hit.vectorId())

即：

Map key = content

查询 key = vectorId

即使 SQL 正常返回数据，最终也会因为查不到对应 Row 而全部跳过。

修复为：

KnowledgeRetrievalRow::getVectorId

形成正确链路：

Milvus vectorId

→ Map<vectorId, Row>

→ MySQL Chunk

---

13. **补充 Query Embedding 返回契约校验**

为了避免 Service 默认相信外部 Embedding Provider 一定正确，增加：

EmbeddingResult != null

vectors.size() == 1

EmbeddingVector != null

inputIndex == 0

只有满足单 Query → 单 Vector 的契约，才进入 Milvus Search。

---

14. **跨存储异常类型调整**

原实现将：

Milvus / MySQL documentId 不一致

Milvus / MySQL chunkIndex 不一致

作为：

IllegalArgumentException

但这并不是用户 Query 或 topK 参数错误，而是系统内部两个存储之间状态异常。

因此修改为：

IllegalStateException

使异常语义更加准确。

---

15. **Real MySQL 测试首次失败：Maven 未继承数据库环境变量**

第一次执行真实 MyBatis Integration 时出现：

Driver com.mysql.cj.jdbc.Driver

claims to not accept jdbcUrl, ${DB_URL}

当时：

Tests run: 10

Failures: 0

Errors: 10

定位发现失败发生在 DataSource 创建阶段，Retrieval SQL 尚未真正执行。

原因不是：

Mapper SQL 错误

而是 Maven 子进程没有继承已有的：

DB_URL

DB_USERNAME

DB_PASSWORD

随后只将 Windows User Scope 中已有变量临时复制到 Maven 子进程，不修改 YAML 和生产配置。

重新执行后：

Tests run: 10

Failures: 0

Errors: 0

Skipped: 0

BUILD SUCCESS

---

16. **Full Regression 首次出现大量数据库测试失败**

第一次执行：

.\mvnw.cmd -B -ntp test

结果：

Tests run: 651

Failures: 1

Errors: 85

Skipped: 18

BUILD FAILURE

错误仍然集中于：

${DB_URL}

进一步确认：

`application-test.yaml` 当前没有独立 DataSource，而部分普通测试同时使用 `local,test` Profile。

因此即使：

Real Test 开关全部关闭

普通数据库测试仍需要正常 DataSource。

处理方式仍然没有修改代码和 YAML，只向本次 Maven Process 临时提供已有 DB 环境变量。

先验证：

DatabaseConnectionTest

Tests run: 1

Failures: 0

Errors: 0

Skipped: 0

BUILD SUCCESS

随后重新运行 Full Regression：

Tests run: 651

Failures: 0

Errors: 0

Skipped: 18

BUILD SUCCESS

因此该问题最终归类为：

测试执行环境问题

而不是：

R1 Production Regression

---

**测试与验证**

本阶段按照：

Static Review

→ Compile

→ Unit / Mock

→ Real MyBatis

→ Real Retrieval

→ Full Regression

逐层验证。

17. **编译**

Compiling 84 source files

BUILD SUCCESS

---

18. **KnowledgeRetrievalService Unit / Mock**

Tests run: 19

Failures: 0

Errors: 0

Skipped: 0

重点验证：

Milvus 排名保持

MySQL 乱序回表

TopK 过滤后少于请求数量

rank 不压缩

Embedding Profile 传递

一次 Batch Mapper 调用

跨存储一致性异常

低 similarityScore 不过滤

---

19. **Real MyBatis + MySQL**

Tests run: 10

Failures: 0

Errors: 0

Skipped: 0

其中包含原有 C1 数据库测试和新增 Retrieval Mapper 测试。

真实验证：

vectorId Batch IN

ACTIVE

READY

current documentVersion

embeddingModel

embeddingVersion

Enum Mapping

nullable source

---

20. **Real Retrieval Smoke Test**

使用真实 Query：

Java 中什么 Map 适合多线程并发访问？

测试文档内容围绕：

ConcurrentHashMap

并发访问

HashMap 并发写入问题

Query 并不是 Chunk 原文。

真实链路：

Query

→ Bailian Embedding

→ Milvus COSINE Search

→ MySQL 回表

→ RetrievalResult

实际一次测试命中：

vectorRank = 1

similarityScore = 0.784863

并通过本次测试 `documentId` 明确确认：

命中的确是本轮刚入库的测试文档

而不是 Milvus 中已有的其他历史数据。

该 ID、Chunk ID 和 Vector ID 仅为本次 Smoke Test 临时执行数据，测试后已经清理。

同时加入 sentinel 向量验证定向 Cleanup：

删除目标向量

→ sentinel 仍然存在

→ 证明没有误删其他向量

→ 再单独删除 sentinel

---

21. **Final Regression**

最终：

Tests run: 651

Failures: 0

Errors: 0

Skipped: 18

BUILD SUCCESS

其中 18 个 skipped 均为默认关闭的 Real Service Tests。

普通 Regression 没有调用真实：

DashScope

Milvus

---

**阶段结论**

至此 RAG 已经完成两个基础方向：

C1 Knowledge Ingestion

Document

→ Chunk

→ Embedding

→ Milvus + MySQL

→ READY

以及：

R1 Knowledge Retrieval

Query

→ Embedding

→ Milvus TopK

→ MySQL Eligibility + Hydration

→ Structured Evidence

系统已经从：

“能够把知识写入知识库”

推进到：

“能够根据自然语言 Query

重新检索出语义相关的知识 Evidence”

但当前仍然**没有完成完整 RAG**。

尚未实现：

Evidence

→ Prompt Context

→ LLM

→ Evaluation

下一阶段将单独设计：

Retrieval Evidence

→ Prompt / Evaluation Orchestration

不会直接把 LLM 调用塞入 `KnowledgeRetrievalService`，继续保持 Retrieval 与后续评分编排职责分离。

### 2026-08-31｜启动 Answer Evaluation E1：完成评价持久化基础

**开发内容**

RAG Retrieval R1 完成后，系统已经具备：

Query

→ Embedding

→ Milvus COSINE TopK Search

→ MySQL 回表

→ RetrievalResult

的独立知识检索能力。

但此时 Retrieval 仍然只是独立基础设施，还没有真正接入：

用户回答

→ RAG Evidence

→ LLM Evaluation

→ Java Score Calculation

→ Follow-up Decision

这一条回答评价链路。

因此本阶段开始进入：

Answer Evaluation Orchestration E1

完整目标链路为：

InterviewAnswer

→ Evaluation Context

→ RAG Retrieval

→ LLM Evaluation

→ Java Validation

→ Java Score Calculation

→ Follow-up Decision

→ answer_evaluation

考虑到后续 RAG、Prompt、LLM 和评分逻辑都会依赖一份稳定的回答与题目上下文，本阶段没有直接开始调用 LLM，而是先完成 E1-A：

**Answer Evaluation Persistence Foundation**

即建立：

InterviewAnswer

→ InterviewQuestion

→ 历史评价依据

的最小持久化读取能力。

本阶段新增：

InterviewQuestion

InterviewAnswer

InterviewQuestionType

InterviewQuestionStatus

InterviewAnswerStatus

InterviewQuestionMapper

InterviewAnswerMapper

InterviewQuestionMapper.xml

InterviewAnswerMapper.xml

并增加真实 MySQL Mapper 测试。

---

**技术选型**

1132. **先建立评价持久化基础，而不是直接开发 LLM Client**

一种更直接的方案是：

用户提交回答

→ Service 查询数据库

→ 立即构造 Prompt

→ 调用 DeepSeek

这种方案代码推进速度快，但会让：

数据库查询

历史快照解析

MAIN / FOLLOW_UP 关系

RAG Query

Prompt

LLM 调用

评分规则

全部逐渐集中到同一个评价 Service 中。

后续出现评价错误时，也很难判断问题来自：

数据读取

RAG

Prompt

模型响应

还是 Java 评分逻辑。

因此 E1 选择分阶段实现。

第一阶段只建立：

answerId

→ InterviewAnswer

→ InterviewQuestion

→ 后续 EvaluationContext 所需的数据基础

暂时不实现：

EvaluationContext

Prompt Builder

LLM Client

Score Calculator

FollowUpPolicy

完整 InterviewService

这样可以先保证评价所依赖的业务事实是稳定、可验证的，再逐层接入 AI 能力。

---

2. **`interview_question`** **保存参考答案快照，而不是评价时重新查询** **`question.reference_answer`**

数据库原设计已经保存：

question_content

category

knowledge_point

scoring_points_snapshot

这些字段用于冻结本场面试实际使用的题目和评分标准。

但实现 E1 时发现一个缺口：

`reference_answer` 仍然只存在于实时 `question` 表中。

如果出现：

创建面试

→ 管理员修改题库 reference_answer

→ 用户回答评价失败后重新执行

那么旧面试可能使用新的参考答案重新评分。

这样会导致：

同一场历史面试

在不同时间评价

使用不同的评价依据

这与项目原本的数据库原则：

“题目和评分标准后续修改不能改变历史评价依据”

相冲突。

理论上也可以继续通过：

interview_question.question_id

→ question.reference_answer

动态关联得到参考答案。

但这种方案无法恢复已经被覆盖的旧参考答案，因此 Java 逻辑本身无法解决历史一致性问题。

最终决定在：

interview_question

增加：

`reference_answer_snapshot`

MAIN 问题在创建面试计划时冻结：

question_content

reference_answer_snapshot

scoring_points_snapshot

FOLLOW_UP 本身不重复保存 reference answer，而是在综合 FINAL 评价时使用所属 MAIN 的快照。

因此：

`question_id`

只表示这道主问题最初来源于哪一道题库记录。

而真正参与这场面试评价的是：

question_content

reference_answer_snapshot

scoring_points_snapshot

---

3. **`scoring_points_snapshot`** **和** **`follow_up_target_points`** **暂时使用 String 映射 JSON**

MySQL 中：

scoring_points_snapshot

follow_up_target_points

都是 JSON 字段。

一种方案是在 Entity 层直接映射为：

List<ScoringPointSnapshot>

List<Long>

这通常需要：

自定义 MyBatis TypeHandler

或在持久化层直接引入 JSON 解析。

但当前 E1-A 的职责只是：

MySQL

↔

Persistence Entity

真正的业务结构化解析会在后续：

EvaluationContext

阶段完成。

因此当前选择：

```Plain
scoringPointsSnapshot : StringfollowUpTargetPoints : String
```

后续再通过 Jackson：

JSON String

→ ScoringPointSnapshot

→ EvaluationContext

这样可以保持：

Entity 负责数据库映射

Evaluation Context 负责评价业务语义

两层职责分离。

同时避免为了两个 JSON 字段提前引入不必要的 TypeHandler。

---

4. **面试题分类直接复用** **`QuestionCategory`**

`interview_question.category` 虽然保存的是历史快照，但它与题库中的 category 在业务语义上仍然完全一致。

如果重新创建：

InterviewQuestionCategory

则会产生两组完全相同的枚举：

QuestionCategory

InterviewQuestionCategory

后续还需要额外做转换。

因此：

InterviewQuestion.category

直接使用：

QuestionCategory

数据库只是把创建面试时的枚举值冻结下来，而不是产生一种新的分类体系。

---

5. **Mapper 只实现当前评价阶段真正需要的查询**

没有为：

InterviewQuestionMapper

InterviewAnswerMapper

一次性补全完整 CRUD。

当前只实现：

InterviewQuestionMapper

→ getInterviewQuestionById

InterviewAnswerMapper

→ getInterviewAnswerById

→ getInterviewAnswerByInterviewQuestionId

其中：

getInterviewAnswerByInterviewQuestionId

主要用于后续 FOLLOW_UP 综合评价。

链路为：

Follow-up Answer

→ Follow-up InterviewQuestion

→ parentQuestionId

→ MAIN InterviewQuestion

→ MAIN InterviewAnswer

当前还没有开始：

面试计划创建

状态推进

回答提交

因此没有提前增加：

insert

updateStatus

selectNextQuestion

等暂时没有真实调用方的方法。

这样避免为了“以后可能使用”而提前扩张 Mapper 接口。

---

**数据库调整**

E1 实现前确认了两个具体 Schema 问题。

4141. `interview_question` 增加：

```Plain
reference_answer_snapshot TEXT NULL
```

用于保存 MAIN 创建时的参考答案快照。

2. `interview_answer.error_code`

原结构为：

```Plain
VARCHAR(20)
```

但 E1 已确定的错误码之一：

```Plain
LLM_RESULT_VALIDATION_FAILED
```

超过 20 个字符。

如果保持原设计，评价结果校验失败时无法完整保存错误码。

因此调整为：

```Plain
VARCHAR(50)
```

数据库仍采用当前项目的 Schema 快照管理方式：

`sql/ai_interview.sql`

只保存当前最终表结构。

实际数据库修改时使用的临时 `ALTER TABLE` 不作为 migration 文件保留在仓库中。

当前项目仍处于单人 MVP 开发阶段，因此暂不额外引入 Flyway 或 Liquibase。

---

**问题与修复**

4624. **Mapper 方法名拼写错误**

初始：

InterviewAnswerMapper.java

中的接口方法为：

```Plain
getInterviewAnswerById
```

但：

InterviewAnswerMapper.xml

中误写为：

```Plain
getInterviewAnserById
```

少了一个：

`w`

Java 编译本身不能发现这种 MyBatis Statement ID 不匹配问题。

如果运行到该 Mapper 方法，会因为找不到对应 Statement 导致：

`BindingException`

生产审查时发现后，将 XML 的 select id 修正为：

```Plain
getInterviewAnswerById
```

使 Mapper 接口与 XML 严格一致。

---

2. **Mapper XML 文件名拼写错误**

初始文件误命名为：

```Plain
InterviewAnserMapper.xml
```

修正为：

```Plain
InterviewAnswerMapper.xml
```

虽然 MyBatis 在当前配置下主要依赖 XML 内容中的 namespace 识别 Mapper，文件名本身不一定直接决定映射是否成功，但错误命名会降低项目可读性，并容易在后续维护和排查时产生混淆，因此同步修正。

---

3. **清理无效 import**

审查发现：

InterviewAnswer.java

存在未使用的：

```Plain
LocalDate
```

InterviewQuestionMapper.java

存在无效 Lombok import。

两处均删除。

本次没有顺带重构无关代码。

---

4. **首次真实 Mapper 测试全部 Error**

新增 E1-A Mapper 测试第一次运行：

```Plain
Tests run: 6Failures: 0Errors: 6Skipped: 0
```

错误表现为：

`${DB_URL}`

未解析。

进一步检查后确认：

本地 Windows User scope 已经存在：

DB_URL

DB_USERNAME

DB_PASSWORD

但当前 Maven 子进程没有继承这些环境变量。

因此这是：

**测试执行环境问题**

而不是：

Mapper SQL 错误

数据库 Schema 错误

或生产代码 Regression。

本次没有为了让测试运行而修改：

application.yaml

application-test.yaml

也没有把本地数据库密码写入 Git 配置。

将已有 Windows 用户作用域变量仅传入当前 Maven 进程后重新运行，测试全部通过。

这也再次说明：

IDEA

PowerShell

Maven 子进程

不一定拥有完全相同的环境变量上下文。

---

**测试验证**

5931. 编译验证：

```Plain
.\mvnw.cmd -B -ntp -DskipTests compile
```

结果：

```Plain
91 production source filesBUILD SUCCESS
```

该结果只证明：

Java 生产代码能够完成编译。

不将其描述为 Mapper 功能验证通过。

---

2. E1-A Mapper 真实 MySQL 测试：

```Plain
.\mvnw.cmd -B -ntp "-Dtest=InterviewQuestionMapperTest,InterviewAnswerMapperTest" test
```

正确数据库环境下结果：

```Plain
Tests run: 6Failures: 0Errors: 0Skipped: 0BUILD SUCCESS
```

该测试实际连接：

本地 MySQL

并执行：

真实 MyBatis Mapper XML

真实 SQL

真实数据库外键和字段约束。

`InterviewQuestionMapperTest` 验证：

MAIN 全字段映射

`reference_answer_snapshot` 与实时 `question.reference_answer` 隔离

`scoring_points_snapshot` JSON 能以 String 正确读取

FOLLOW_UP nullable 字段

parentQuestionId

followUpTargetPoints

不存在 ID 返回 null

`InterviewAnswerMapperTest` 验证：

按 answer id 查询

按 interviewQuestionId 查询

不同问题的回答不会互相误匹配

不存在 ID 返回 null

`error_code` 当前数据库长度为 50

`LLM_RESULT_VALIDATION_FAILED`

能够完整写入并读取，没有截断。

---

3. 普通全量回归：

```Plain
.\mvnw.cmd -B -ntp test
```

运行前未开启：

RUN_REAL_*

MILVUS_ENABLED

最终结果：

```Plain
Tests run: 657Failures: 0Errors: 0Skipped: 18BUILD SUCCESS
```

18 个 skipped 均为项目原有受保护的真实外部：

Embedding

Milvus

Knowledge Pipeline

Smoke / Integration Tests。

因此本次 E1-A：

**真实验证了 MySQL / MyBatis。**

但：

**没有调用真实 Embedding API。**

**没有调用真实 Milvus。**

不能将 18 个 skipped 描述为外部服务验证成功。

---

**当前状态**

E1-A 已完成：

InterviewQuestion Entity

InterviewAnswer Entity

3 个 Interview 枚举

InterviewQuestionMapper

InterviewAnswerMapper

两个 Mapper XML

数据库历史参考答案快照修正

error_code 长度修正

真实 MySQL Mapper Tests

普通全量 Regression

当前已经能够完成：

```Plain
answerId→ InterviewAnswer→ interviewQuestionId→ InterviewQuestion
```

并获得后续评价需要的：

题目内容

参考答案快照

评分点快照

MAIN / FOLLOW_UP 关系

用户回答

但当前仍然**没有实现**：

EvaluationContext

RAG Evaluation Query Builder

rag_hit_log 编排

Prompt Builder

DeepSeek Evaluation Client

LLM Result Validation

Coverage / Score Calculator

Follow-up Policy

answer_evaluation 持久化

完整 InterviewService

回答提交 HTTP 接口

因此当前不能描述为：

“回答评价链路已经完成”。

---

**学习状态**

本阶段进一步理解了：

- 为什么历史评价依据不能只通过外键回查实时题库数据；

- “数据库关联能够查到”并不代表“不需要快照”；

- MyBatis Mapper 接口和 XML 的方法名属于运行时绑定关系，Java 编译无法覆盖所有映射错误；

- JSON 数据库字段不一定需要在 Persistence Entity 层立即结构化；

- Entity 与 Evaluation Context 应承担不同职责；

- 数据库 Schema 修改必须来自具体无法由 Java 合理解决的问题；

- Maven 测试失败需要区分代码错误和执行环境错误；

- 编译通过、真实 MySQL 测试通过和真实外部服务联调是三个不同的验证层级。

---

**下一步**

进入：

**E1-B：Evaluation Context**

下一阶段先设计并实现：

ScoringPointSnapshot

EvaluationContext

MAIN Answer Context Assembly

FOLLOW_UP Answer Context Assembly

Context 业务关系校验

目标是把当前：

```Plain
InterviewAnswer+InterviewQuestion+MAIN / FOLLOW_UP 关系+JSON Snapshot
```

转换为后续 RAG 和 LLM 可以稳定消费的一份结构化评价上下文。

E1-B 完成之前仍然不直接实现：

Prompt

LLM Client

评分计算

追问决策。

### 2026-09-01｜完成 Evaluation Context：统一 MAIN / FOLLOW_UP 评价上下文

**开发内容**

E1-A 已经完成了评价链路最底层的持久化基础：

```Plain
answerId→ InterviewAnswer→ InterviewQuestion
```

并且已经能够读取：

```Plain
questionContentreferenceAnswerSnapshotscoringPointsSnapshotMAIN / FOLLOW_UP 关系answerContent
```

但此时这些数据仍然保持数据库持久化形式。

其中：

```Plain
scoring_points_snapshotfollow_up_target_points
```

仍然是 JSON String。

如果后续：

```Plain
RAG Query BuilderPrompt BuilderLLM EvaluationScore Calculator
```

分别直接操作 `InterviewQuestion` 和这些 JSON 字符串，就会导致多个模块重复：

```Plain
读取数据库 Entity→ 判断 MAIN / FOLLOW_UP→ 找 parent MAIN→ 解析 JSON→ 校验快照
```

业务边界会越来越混乱。

因此本阶段进入：

**E1-B：Evaluation Context**

目标是在数据库持久化层和后续 AI 评价链之间建立统一的数据边界：

```Plain
Persistence Entity→ EvaluationContextService→ EvaluationContext→ RAG / Prompt / LLM / Score
```

本阶段新增：

```Plain
EvaluationModeScoringPointSnapshotEvaluationContextEvaluationContextService
```

并新增：

```Plain
EvaluationContextServiceTest
```

最终系统已经能够完成：

```Plain
answerId→ InterviewAnswer→ InterviewQuestion→ MAIN / FOLLOW_UP 关系解析→ 历史 Snapshot JSON 解析→ Java 业务一致性校验→ EvaluationContext
```

从这一层以后，后续评价模块不再直接解析数据库 JSON，也不需要重新读取实时题库评分标准。

---

**技术选型**

1259. **增加 EvaluationContext，而不是让后续模块直接使用 Entity**

一种更简单的实现方式是：

```Plain
RAG Query Builder→ 直接接收 InterviewQuestion + InterviewAnswerPrompt Builder→ 再接收 InterviewQuestion + InterviewAnswer
```

但这样会产生一个问题：

数据库 Entity 表达的是：

> 数据库当前如何保存数据。

而 RAG / Prompt / Score 真正关心的是：

> 一次评价完整需要哪些业务事实。

例如 FOLLOW_UP 的评价实际需要：

```Plain
MAIN QuestionMAIN AnswerMAIN referenceAnswerSnapshotMAIN scoringPointsSnapshot+FOLLOW_UP QuestionFOLLOW_UP AnswerfollowUpTargetPoints
```

这些信息并不存在于某一个 Entity 中，而需要跨：

```Plain
FollowUp AnswerFollowUp InterviewQuestionparent MAIN InterviewQuestionMAIN InterviewAnswer
```

组合得到。

因此增加：

```Plain
EvaluationContext
```

作为评价领域的统一只读输入。

后续：

```Plain
RAGPromptLLMScore
```

只消费 `EvaluationContext`，不再自行理解数据库表关系。

---

2. **EvaluationMode 使用** **`MAIN_ANSWER / FOLLOW_UP_ANSWER`****，而不是** **`INITIAL / FINAL`**

最初很容易把主回答评价理解成：

```Plain
MAIN Answer=INITIAL
```

但这种关系并不成立。

主回答完成一次完整评价之后：

```Plain
不需要追问→ 直接保存 FINAL需要追问→ 保存 INITIAL
```

也就是说在：

```Plain
RAGLLMJava Score
```

真正执行之前，还无法确定这次评价最终是：

```Plain
INITIAL还是FINAL
```

因此本阶段定义：

```Plain
MAIN_ANSWERFOLLOW_UP_ANSWER
```

用于表达：

> 当前正在评价哪一种回答。

而未来：

```Plain
INITIALFINAL
```

只表示：

> 评价完成以后如何持久化到 answer_evaluation。

这样避免把：

```Plain
回答类型
```

和：

```Plain
评价持久化阶段
```

混成同一个概念。

---

3. **ScoringPointSnapshot 独立于 QuestionScoringPoint Entity**

数据库中的：

```Plain
QuestionScoringPoint
```

包含：

```Plain
idquestionIdpointTypecontentweightsortOrderstatuscreatedAtupdatedAt
```

但一次历史评价真正需要的评分点信息只有：

```Plain
scoringPointIdpointTypecontentweight
```

因此定义不可变：

```Plain
ScoringPointSnapshot
```

而不是直接把：

```Plain
QuestionScoringPoint Entity
```

传入评价链。

这样可以明确：

`ScoringPointSnapshot`

表示：

> 创建面试时已经冻结下来的评分标准。

而不是实时题库中的评分点。

同时继续复用现有：

```Plain
QuestionPointTypeCOREKEYPRINCIPLEINTERNALADVANCED
```

没有额外创建重复的 EvaluationPointType。

---

4. **EvaluationContext 使用 record，而不是可变 DTO**

EvaluationContext 一旦构造完成，后续：

```Plain
RAGPromptLLMScore
```

应该只能读取，不能修改其业务事实。

因此采用：

```Plain
record
```

而不是：

```Plain
@Data@Builder可变 POJO
```

其中两个 List：

```Plain
scoringPointsfollowUpTargetPointIds
```

通过：

```Plain
List.copyOf(...)
```

进行 defensive copy。

避免类似：

```Plain
Context 创建完成→ 外部继续修改原 ArrayList→ Context 内容被间接改变
```

的问题。

因此 EvaluationContext 表达的是一份：

**构造完成后不可变的评价事实快照。**

---

5. **JSON 解析放在 EvaluationContext 边界完成，不增加 MyBatis TypeHandler**

E1-A 中：

```Plain
scoringPointsSnapshotfollowUpTargetPoints
```

仍然由 Entity 使用：

```Plain
String
```

接收数据库 JSON。

E1-B 中真正进入评价业务时，再通过 Jackson：

```Plain
JSON String→ List<ScoringPointSnapshot>→ List<Long>
```

这样职责形成：

```Plain
Persistence Entity负责数据库字段映射EvaluationContextService负责数据库数据 → 评价业务模型
```

本阶段没有为了 JSON 字段增加：

```Plain
MyBatis TypeHandler
```

也没有单独创建：

```Plain
ScoringPointSnapshotParserEvaluationJsonParser
```

因为目前只有：

```Plain
EvaluationContextService
```

一个生产调用方。

在没有第二个真实使用者之前，不提前制造新的抽象层。

---

**MAIN Answer Context**

MAIN Answer 的组装链路为：

```Plain
answerId→ InterviewAnswer→ interviewQuestionId→ MAIN InterviewQuestion→ parse scoringPointsSnapshot→ validate→ EvaluationContext
```

生成的 Context 满足：

```Plain
mode = MAIN_ANSWERcurrentAnswerId=mainAnswerIdcurrentInterviewQuestionId=mainInterviewQuestionId
```

评价依据全部来自 MAIN 的历史快照：

```Plain
questionContentreferenceAnswerSnapshotscoringPointsSnapshot
```

以及：

```Plain
mainAnswerContent
```

此时：

```Plain
followUpQuestionContent = nullfollowUpAnswerContent = nullfollowUpTargetPointIds = []
```

整个过程中：

**EvaluationContextService 不依赖 QuestionMapper。**

因此不会出现：

```Plain
历史面试→ 再查实时 question.reference_answer
```

或者：

```Plain
历史面试→ 再查实时 question_scoring_point
```

的问题。

---

**FOLLOW_UP Answer Context**

FOLLOW_UP 的数据关系更复杂：

```Plain
FollowUp Answer→ FollowUp InterviewQuestion→ parentQuestionId→ MAIN InterviewQuestion→ MAIN InterviewAnswer
```

最终 Context 同时包含：

```Plain
MAIN QuestionMAIN AnswerMAIN referenceAnswerSnapshotMAIN scoringPointsSnapshot
```

以及：

```Plain
FOLLOW_UP QuestionFOLLOW_UP AnswerfollowUpTargetPointIds
```

其中评价标准始终属于 MAIN。

FOLLOW_UP 不拥有独立：

```Plain
referenceAnswerSnapshotscoringPointsSnapshot
```

因此：

```Plain
MAIN scoring points
```

定义的是：

> 这一道主问题到底考什么。

而：

```Plain
followUpTargetPointIds
```

只是表示：

> 这一次追问针对 MAIN 中哪些评分点继续确认。

这使后续 FINAL Evaluation 可以同时看到：

```Plain
主问题主回答追问追问回答
```

并对同一道 MAIN 产生一次综合最终评价。

---

**Java 业务一致性校验**

EvaluationContextService 不只是简单的数据组装器。

它同时承担：

```Plain
Persistence→ Evaluation Domain
```

之间的业务边界校验。

MAIN 主要验证：

```Plain
questionType = MAINquestionId 存在parentQuestionId 为空planOrder 存在sessionId 存在category 存在knowledgePoint 非空questionContent 非空referenceAnswerSnapshot 非空scoringPointsSnapshot 非空answerContent 非空
```

评分点 JSON 解析以后继续验证：

```Plain
评分点列表非空不包含 nullscoringPointId > 0scoringPointId 不重复pointType 非空content 非空weight 在 1～100所有 weight 总和 = 100
```

虽然这些规则正常情况下应该在创建面试计划时已经成立，但正式进入 AI Evaluation 之前仍然进行一次防御性验证。

避免损坏的历史 Snapshot 继续进入：

```Plain
RAGLLMScore
```

链路。

---

FOLLOW_UP 额外验证：

```Plain
questionType = FOLLOW_UPquestionId 为空parentQuestionId 存在planOrder 为空parent MAIN 必须存在parent 必须确实是 MAINFOLLOW_UP 和 MAIN 必须属于同一个 sessionMAIN Answer 必须存在
```

其中：

```Plain
FOLLOW_UP.sessionId=MAIN.sessionId
```

这一条无法仅靠数据库普通外键保证。

数据库只能保证：

```Plain
parent_question_id
```

指向一个存在的 `interview_question`。

但不能保证：

> parent 一定属于同一场 interview_session。

因此该约束由 Java Service 继续校验。

---

**追问目标评分点校验**

FOLLOW_UP 的：

```Plain
follow_up_target_points
```

解析为：

```Plain
List<Long>
```

后继续验证：

```Plain
非空没有 null没有重复 ID
```

并且：

```Plain
所有 targetPointId必须存在于MAIN scoringPointsSnapshot
```

例如 MAIN 只有：

```Plain
101102103
```

如果历史数据中出现：

```Plain
[102, 999]
```

虽然：

```Plain
JSON 合法数据库字段也合法
```

但：

```Plain
999
```

并不属于这道 MAIN 的评分标准。

这种数据不能继续进入评价链。

当前没有在 Context Service 中限制：

```Plain
targetPointIds.size() <= 2
```

因为：

“最多追问两个高度相关评分点”

属于后续：

```Plain
FollowUpPolicy
```

在**生成追问时**需要保证的规则。

而 EvaluationContextService 当前负责的是：

> 已经存在的追问数据是否能够形成合法评价上下文。

两种职责保持分离。

---

**问题与修复**

7242. **补充 Mapper 返回对象自身 ID 的一致性校验**

初始实现主要依赖：

```Plain
Mapper 查询条件
```

推断返回对象应该对应请求 ID。

Code Review 后补充了返回实体自身：

```Plain
id
```

及关联字段的一致性检查。

原因是 EvaluationContextService 属于正式评价前的数据边界。

不能只因为：

```Plain
Mapper 返回了一个对象
```

就默认它一定和当前业务关系完全一致。

如果：

```Plain
Mapper Mock映射错误历史异常数据后续 Mapper 修改
```

导致实体 ID 与预期关系不一致，应在 Context 构建阶段立即失败，而不是把错误数据继续传入 AI 链路。

---

2. **nullable ID 比较改用** **`Objects.equals`**

部分 ID 关系最初直接使用：

```Plain
a.equals(b)
```

在正常数据库约束下通常不会出现 null。

但 Context Service 本身正在做防御性状态校验，因此如果异常持久化对象出现 null：

```Plain
a.equals(b)
```

可能先抛出：

```Plain
NullPointerException
```

而不是得到明确的业务状态错误。

Review 后统一使用：

```Plain
Objects.equals(a, b)
```

进行 nullable-safe 比较。

这样异常状态仍然可以走预期的数据一致性校验路径。

---

3. **统一内部持久化异常语义**

本阶段遇到的错误主要属于：

```Plain
历史快照损坏MAIN / FOLLOW_UP 关系异常父问题不存在评分点 JSON 不合法关联对象不一致
```

这些不是普通用户请求参数错误，而是：

> 系统持久化状态无法形成合法 Evaluation Context。

因此 Review 中统一了这类内部异常的表达方式。

当前阶段没有因此建立：

```Plain
EvaluationContextExceptionEvaluationErrorHierarchy
```

等复杂异常体系。

后续完整 Evaluation Orchestration 真正需要映射：

```Plain
FAILEDerror_code
```

时，再在上层定义正式评价失败分类。

---

4. **清理冲突及无效 import**

Code Review 同时发现并清理了：

```Plain
冲突 import未使用 import
```

避免无关依赖残留。

本次没有顺带重构其他模块。

---

**测试验证**

本阶段 `EvaluationContextService` 属于业务组装层，因此测试策略与 E1-A 不同。

E1-A 已经通过真实 MySQL 验证：

```Plain
Mapper↔ MyBatis XML↔ MySQL
```

因此 E1-B 不再重复创建完整数据库 Fixture。

Focused Test 使用：

```Plain
JUnit 5Mockito Mapper真实 Jackson ObjectMapper
```

测试重点变成：

```Plain
Persistence Object→ JSON parsing→ relationship validation→ EvaluationContext
```

没有 Mock：

```Plain
ObjectMapper
```

因为 JSON String → typed snapshot 本身就是 E1-B 的核心能力之一。

---

Focused Test：

```Plain
.\mvnw.cmd -B -ntp -Dtest=EvaluationContextServiceTest test
```

结果：

```Plain
Tests run: 52Failures: 0Errors: 0Skipped: 0
```

覆盖包括：

```Plain
正常 MAIN Context正常 FOLLOW_UP ContextscoringPoints JSON 解析非法 JSON空评分点重复 scoringPointId非法 pointType / content / weightweight 总和不等于 100parent MAIN 不存在parent 类型错误跨 session parentMAIN Answer 不存在followUpTargetPoints JSON 非法target list 为空target nulltarget 重复target 不属于 MAIN scoring snapshotEvaluationContext List 不可变性
```

Focused Test：

**没有连接真实 MySQL。**

---

普通全量回归：

```Plain
.\mvnw.cmd -B -ntp test
```

最终结果：

```Plain
Tests run: 709Failures: 0Errors: 0Skipped: 18BUILD SUCCESS
```

18 个 skipped 仍然来自项目原有受保护的真实外部：

```Plain
EmbeddingMilvusKnowledge Pipeline
```

Smoke / Integration Tests。

本阶段：

```Plain
没有真实调用 Embedding API没有真实调用 Milvus
```

full regression 中项目已有数据库测试仍然连接本地 MySQL，但这不代表：

`EvaluationContextServiceTest`

本身依赖真实数据库。

---

**当前状态**

E1-B 已完成并推送。

Commit：

```Plain
a202fd02a1c2f3e59ec0dabf4498cfc16d40e1a8
```

Commit message：

```Plain
feat: build evaluation context foundation
```

当前远端：

```Plain
origin/main=a202fd02a1c2f3e59ec0dabf4498cfc16d40e1a8
```

当前已经形成：

```Plain
answerId→ InterviewAnswer→ InterviewQuestion→ MAIN / FOLLOW_UP Relationship→ Historical Snapshot Parsing→ Java Validation→ EvaluationContext
```

因此后续评价链已经不需要直接理解：

```Plain
InterviewQuestion EntityscoringPointsSnapshot JSON StringfollowUpTargetPoints JSON String
```

---

**学习状态**

本阶段进一步理解了：

- Entity 和 Evaluation Context 的职责并不相同；

- 数据库能通过外键关联，不代表业务层不需要重新校验跨记录关系；

- MAIN / FOLLOW_UP 是回答评价模式，而 INITIAL / FINAL 是评价持久化阶段，两者不能混用；

- FOLLOW_UP 不是一套新的评分标准，而是 MAIN 原有评分标准上的定向补充；

- record 适合表达创建后不应该再改变的评价上下文；

- `List.copyOf` 不只是返回不可修改 List，同时也是 defensive copy 的手段；

- Jackson `TypeReference` 用于解决泛型 JSON 反序列化时的类型信息问题；

- Mock Mapper 并不等于“测试不真实”，测试方式应该根据当前层真正负责的职责选择；

- 数据库集成测试、业务单元测试、真实外部服务测试分别证明不同层面的正确性。

---

**下一步**

进入：

E1-C：RAG Query Builder / Evaluation Retrieval Adapter

下一阶段需要解决两个问题。

第一：

```Plain
EvaluationContext→ Stable RAG Query
```

MAIN Answer 的 Query 将围绕：

```Plain
MAIN Question+完整 scoring points
```

构造。

不会把用户答案加入 Query，避免错误回答把检索方向带偏。

FOLLOW_UP Answer 的 Query 将围绕：

```Plain
MAIN Question+FOLLOW_UP Question+followUpTargetPoints
```

构造。

第二，需要处理现有 RAG Retrieval R1 与 E1 之间的边界差异。

当前 R1：

```Plain
Milvus TopK→ MySQL eligibility→ RetrievalResult
```

而 E1 后续要求区分：

```Plain
Raw Milvus Top5和真正进入 LLM 的 Evidence
```

以支持：

```Plain
rag_hit_logsimilarity threshold0～5 个最终 Evidence
```

因此下一阶段需要先设计：

```Plain
RAG Query BuilderEvaluation Retrieval Adapter
```

以及现有 Retrieval Result 是否需要最小边界扩展。

E1-C 尚未开始实现：

```Plain
similarity thresholdrag_hit_log orchestrationPrompt BuilderDeepSeek Evaluation ClientScore CalculatorFollowUpPolicyanswer_evaluation persistence
```

仍然按照小阶段逐层推进。

### 2026-09-02｜完成 Evaluation Core：打通 RAG → Prompt → DeepSeek → Java Validation / Score

**开发内容**

上一阶段 E1-B 已经将数据库中的：

```Plain
InterviewAnswer→ InterviewQuestion→ MAIN / FOLLOW_UP Relationship→ Historical Snapshot
```

统一转换为：

```Plain
EvaluationContext
```

后续评价模块已经不需要直接理解数据库 Entity 和 JSON String。

但此时完整的回答评价链仍然缺少：

```Plain
EvaluationContext→ RAG Evidence→ Evaluation Prompt→ LLM Evaluation→ Java Validation→ Java Score
```

因此本阶段继续完成 Answer Evaluation E1 的核心能力，并依次实现：

```Plain
E1-C Evaluation RetrievalE1-D Evaluation Prompt / DeepSeek ClientE1-E Java Validation / Score
```

最终形成：

```Plain
EvaluationContext        ↓EvaluationRagQueryBuilder        ↓EvaluationRetrievalAdapter        ↓KnowledgeRetrievalService        ↓EvaluationRetrievalResult        ↓EvaluationPromptBuilder        ↓EvaluationPrompt        ↓DeepSeekEvaluationClient        ↓LlmEvaluationSuggestion        ↓LlmEvaluationValidator        ↓ValidatedEvaluationSuggestion        ↓EvaluationScoreCalculator        ↓EvaluationScore
```

至此，系统已经具备从一次回答评价上下文出发，完成知识检索、Prompt 构造、LLM 结构化评价建议、Java 业务校验和最终总分计算的核心技术链路。

---

**技术选型**

1. **RAG Query 不加入用户回答，而使用问题和评分点构造稳定 Query**

一种直观方案是：

```Plain
Question+User Answer→ RAG Query
```

这样看起来可以让检索内容更贴近用户实际回答。

但回答评价场景存在一个问题：

> 用户回答本身可能就是错误的。

如果用户对 HashMap 的并发问题给出了错误解释，再将该回答直接参与向量检索，就可能让错误语义反过来影响召回方向。

因此当前选择：

MAIN：

```Plain
MAIN Question+全部 MAIN scoring point content
```

FOLLOW_UP：

```Plain
MAIN Question+FOLLOW_UP Question+target scoring point content
```

构造 Retrieval Query。

用户回答只在后面的 Evaluation Prompt 中使用，不参与知识检索方向的确定。

这样将：

```Plain
Retrieval
```

定义为：

> 寻找评价所需要的正确知识依据。

而不是：

> 围绕用户当前回答寻找相似内容。

当前第一版没有加入：

```Plain
Query RewriteRerankcategory filterdynamic TopK
```

避免在 MVP 阶段扩大检索复杂度。

---

2. **保留 Raw Milvus Hits，但只有通过 MySQL eligibility 的 Evidence 才能进入 LLM**

现有 R1 的流程是：

```Plain
Milvus TopK→ MySQL hydration / eligibility→ RetrievedChunk
```

实现 Evaluation Retrieval 时发现，仅保留最终 `RetrievedChunk` 会丢失：

```Plain
Milvus 原始排名原始 similarity被 MySQL eligibility 淘汰的命中
```

后续：

```Plain
rag_hit_logsimilarity thresholdretrieval trace
```

都会需要区分：

```Plain
Raw Milvus Hits
```

和：

```Plain
真正允许参与评价的 Evidence
```

因此对现有 `RetrievalResult` 做最小扩展：

```Plain
rawHitsitems
```

其中：

```Plain
rawHits
```

保存 Milvus 原始命中；

```Plain
items / evidence
```

保存通过 MySQL 当前文档版本、状态和 Embedding Profile 等条件校验后的可用 chunk。

Evaluation Prompt 只读取：

```Plain
evidence
```

不会把 `rawHits` 发给 LLM。

因此一个已经过期、被禁用或者版本不匹配的向量，即使仍然存在于 Milvus TopK 中，也不能重新进入评价依据。

本阶段只是保留了未来 trace 所需的数据边界，尚未实现：

```Plain
rag_hit_log persistencesimilarity threshold
```

---

3. **增加 EvaluationRetrievalAdapter，而不是让 Evaluation 直接操作 Embedding / Milvus**

可以让评价 Service 直接执行：

```Plain
EmbeddingClient→ VectorStoreClient→ KnowledgeChunkMapper
```

但这样会导致 Evaluation 模块重新理解 R1 已经封装好的：

```Plain
EmbeddingMilvus SearchMySQL hydrationEligibilityRank restore
```

逻辑。

因此增加：

```Plain
EvaluationRetrievalAdapter
```

评价模块只负责：

```Plain
EvaluationContext→ Stable Query→ KnowledgeRetrievalService→ EvaluationRetrievalResult
```

现有 R1 继续保持为独立知识检索能力。

这样后续即使 Milvus 或 Retrieval 内部策略发生变化，Evaluation Core 也不需要重新实现向量检索细节。

---

4. **Evaluation Prompt 将 System Prompt 和 User Prompt 分离**

一种简单实现是直接构造一个很长的 String：

```Plain
规则+问题+回答+参考答案+评分点+RAG Evidence
```

全部发送给模型。

但 Chat Completions 本身已经提供：

```Plain
systemuser
```

两个不同语义的消息角色。

因此定义：

```Plain
EvaluationPrompt
```

包含：

```Plain
promptVersionsystemPromptuserPrompt
```

其中：

System Prompt 表达稳定规则：

```Plain
LLM 的职责五维评分范围JSON contractMAIN / FOLLOW_UP 规则Java 与 LLM 的业务边界
```

User Prompt 表达本次评价实际数据：

```Plain
EvaluationModeQuestionCategoryKnowledgePointMAIN QuestionReference Answer SnapshotScoring Points SnapshotMAIN AnswerFOLLOW_UP Question / AnswerRAG Evidence
```

当前 Prompt 固定版本：

```Plain
evaluation-prompt-v1
```

为后续 `answer_evaluation.prompt_version` 历史追溯提供基础。

---

5. **Retrieval Query 和 Raw Hits 不发送给 LLM**

`EvaluationRetrievalResult` 中仍然保存：

```Plain
queryrawHitsevidence
```

但 Prompt Builder 只消费：

```Plain
evidence
```

Retrieval Query 本身主要用于：

```Plain
tracedebugrag_hit_log
```

它不是额外的评价事实。

Raw Hits 同样不是可靠知识依据。

因此 LLM 最终只看到：

```Plain
问题历史参考答案历史评分点用户回答eligible RAG evidence
```

而不会看到：

```Plain
raw Milvus hitsretrieval query
```

继续保持 Retrieval Trace 与 LLM Evaluation Evidence 的边界。

---

6. **DeepSeek 继续使用 RestClient，而没有引入 Spring AI / LangChain4j**

当前 LLM 评价需求本质上只有：

```Plain
EvaluationPrompt→ HTTP JSON Request→ Chat Completions→ JSON Response
```

项目现有 Embedding 模块已经使用：

```Plain
Spring RestClientJacksonExternalServiceException
```

因此 DeepSeek 第一版继续沿用同样的外部服务调用方式。

新增：

```Plain
DeepSeekPropertiesDeepSeekConfigurationDeepSeekEvaluationClient
```

而没有为了一个 HTTP Chat API 提前引入：

```Plain
Spring AILangChain4j
```

原因是当前并不需要：

```Plain
AgentTool Calling WorkflowMemory复杂模型路由统一 AI Framework abstraction
```

直接使用 RestClient 能够减少额外依赖和框架学习成本，同时让：

```Plain
HTTP RequestHTTP ResponseJSON Contract异常边界
```

保持可见，比较适合当前项目的学习和面试说明目标。

---

7. **DeepSeek 使用 JSON structured output，并在第一版关闭 Thinking**

评价结果需要直接进入 Java Validator，因此不能依赖：

```Plain
“这个回答整体还可以，大概 80 分……”
```

这种自然语言再做二次解析。

当前要求 DeepSeek 返回结构化：

```JSON
{  "correctnessScore": 18,  "completenessScore": 16,  "depthScore": 14,  "clarityScore": 17,  "practiceScore": 12,  "strengths": [],  "missingPoints": [],  "correction": "...",  "scoringPointResults": [],  "followUpRecommended": false,  "suggestedFollowUp": null}
```

请求使用：

```Plain
POST /chat/completionsresponse_format.type = json_objectstream = false
```

当前评价已经有：

```Plain
QuestionReference AnswerScoring PointsRAG Evidence
```

作为明确约束，因此 MVP 第一版选择：

```Plain
thinking.type = disabled
```

优先保证：

```Plain
延迟成本结构化输出稳定性
```

后续如果真实评价测试证明复杂项目题或系统设计题质量不足，再通过配置切换模型或 Thinking 策略，而不提前修改整个架构。

---

8. **DeepSeek 默认关闭，避免普通测试依赖真实 API Key**

公共配置增加：

```Plain
deepseek.enabled=false
```

只有明确设置：

```Plain
DEEPSEEK_ENABLED=true
```

时才创建：

```Plain
DeepSeekPropertiesDeepSeek RestClientDeepSeekEvaluationClient
```

因此：

```Plain
普通 mvn testSpring Context Test
```

不需要真实 DeepSeek API Key。

测试配置和 Git 仓库中也不保存真实密钥。

这与现有真实 Embedding / Milvus opt-in 验证原则保持一致：

> 普通自动化测试不能意外访问真实外部服务。

---

9. **LlmEvaluationSuggestion 被视为不可信外部输入**

DeepSeek 返回 JSON 后，Jackson 可以将其解析为：

```Plain
LlmEvaluationSuggestion
```

但：

```Plain
JSON 可以解析
```

并不代表：

```Plain
业务结果合法
```

例如模型完全可能返回：

```Plain
correctnessScore = 30completenessScore = -5
```

或者返回：

```Plain
重复 scoringPointId不存在的 scoringPointIdFOLLOW_UP 再建议一次追问
```

因此没有直接让后续业务逻辑消费 `LlmEvaluationSuggestion`。

而是建立：

```Plain
LlmEvaluationSuggestion        ↓LlmEvaluationValidator        ↓ValidatedEvaluationSuggestion
```

只有通过 Java 校验以后，才转换为内部可信业务对象。

原始 LLM DTO 使用：

```Plain
IntegerBooleannullable List / String
```

以保留外部 JSON 可能缺字段的事实。

而校验后的对象使用：

```Plain
intboolean
```

表示这些字段已经经过 Java 确认。

---

10. **评分点结果必须与历史 MAIN Scoring Points 完整对应**

LLM 不只是给出五维分数，还需要逐项返回：

```Plain
scoringPointIdcoveredevidence
```

Java Validator 检查：

```Plain
数量一致ID 合法ID 属于 MAIN snapshot不能重复最终 ID 集合完整覆盖 MAIN scoring pointscovered 非 nullcovered=true 时必须有回答 evidence
```

例如 MAIN 评分点为：

```Plain
101102103
```

模型返回：

```Plain
101101999
```

即使数量仍然是 3，也必须判定为非法。

这里的：

```Plain
evidence
```

表示：

> 用户回答中支持 covered 判断的文本依据。

不是 RAG Evidence。

这样后续报告可以解释：

> 用户为什么被认为覆盖或没有覆盖某一个知识点。

---

11. **LLM 只建议五维分数，最终 totalScore 由 Java 计算**

当前评分体系为：

```Plain
correctness       0 ~ 20completeness      0 ~ 20depth             0 ~ 20clarity           0 ~ 20practice          0 ~ 20
```

LLM 可以提供五维评分建议，但不会提供可信业务总分。

Java 在校验以后执行：

```Plain
totalScore =correctness+ completeness+ depth+ clarity+ practice
```

最终范围：

```Plain
0 ~ 100
```

评分点 `weight` 当前用于描述：

```Plain
一道题内部哪些知识点更重要
```

并没有再次乘入五维总分。

这样避免同时存在：

```Plain
五维评分体系+评分点权重二次加权体系
```

导致总分语义不清晰。

---

12. **FOLLOW_UP 仍然最多一次，LLM 只有建议权**

当前固定业务规则仍然是：

```Plain
每个 MAIN 最多一个 FOLLOW_UP
```

因此 MAIN 评价：

```Plain
followUpRecommended=true
```

时必须提供：

```Plain
suggestedFollowUp
```

如果：

```Plain
followUpRecommended=false
```

则不能同时返回一个真实追问问题。

FOLLOW_UP 评价必须：

```Plain
followUpRecommended=falsesuggestedFollowUp=null
```

避免形成第三轮问题。

但当前：

```Plain
followUpRecommended
```

仍然只是 LLM Suggestion。

真正的：

```Plain
FollowUpPolicyINITIAL / FINAL
```

将在后续 Evaluation Orchestration 中由 Java 决定。

---

**问题与修复**

本次 Code Review 和测试过程中发现两个实际代码问题。

第一，`EvaluationScoreCalculator` 中五维求和表达式手写时遗漏分号，导致编译失败。

修复：

```Plain
补充分号
```

没有修改评分算法。

第二，`DeepSeekEvaluationResult` 中：

```Plain
finishReason
```

曾误写为：

```Plain
finishReanson
```

虽然属于字段拼写错误，但会直接产生错误的 record accessor，属于 API Contract 问题。

修复为：

```Plain
finishReason
```

除此之外没有发现 Evaluation Core 的真实逻辑 Bug。

Code Review 没有因为：

```Plain
代码风格命名偏好抽象模式
```

对已经通过验证的生产代码进行额外重构。

---

**测试验证**

本阶段按照不同职责分别使用：

```Plain
JUnit 5AssertJMockitoMockRestServiceServerJackson ObjectMapperSpring Context Test
```

E1-E Validation / Score focused verification：

```Plain
Tests run: 51Failures: 0Errors: 0Skipped: 0
```

随后 Evaluation 扩展 focused：

```Plain
Tests run: 109Failures: 0Errors: 0Skipped: 0
```

D1 Prompt focused：

```Plain
Tests run: 22Failures: 0Errors: 0Skipped: 0
```

Prompt 接入后的 Evaluation focused：

```Plain
Tests run: 118Failures: 0Errors: 0Skipped: 0
```

D2 DeepSeek focused：

```Plain
Tests run: 40Failures: 0Errors: 0Skipped: 0
```

覆盖：

```Plain
POST /chat/completionsBearer Authorizationmodelresponse_format=json_objectthinking=disabledmax_tokensstream=falsesystem/user message order正常 JSON 反序列化HTTP 500null / blank response fields异常 choicesfinish_reason=length非法 message roleblank content非法 JSONProperties Validationdeepseek.enabled 条件配置API key 不出现在 toString()
```

完整 Evaluation focused regression：

```Plain
Tests run: 171Failures: 0Errors: 0Skipped: 0BUILD SUCCESS
```

本阶段所有 DeepSeek Client 测试使用：

```Plain
MockRestServiceServer
```

没有真实调用 DeepSeek API。

因此当前只能证明：

```Plain
DeepSeek HTTP ContractJSON Parsing配置边界Evaluation Java Pipeline
```

已经通过 Mock / Unit / focused verification。

不能描述为：

```Plain
真实 DeepSeek 集成已经验证
```

普通项目全量回归的最终实际结果在 commit / push 前执行，并以当天最终 Maven 输出为准。

---

**当前状态**

截至本阶段，Evaluation Core 已经形成：

```Plain
EvaluationContext→ Evaluation Retrieval→ RAG Evidence→ Evaluation Prompt→ DeepSeek structured suggestion→ Java Validation→ Java Score
```

已经完成：

```Plain
E1-C Evaluation RetrievalE1-D Evaluation Prompt / DeepSeek ClientE1-E Java Validation / Score
```

当前仍未完成：

```Plain
Evaluation OrchestrationINITIAL / FINAL decisionFollowUpPolicyanswer_evaluation persistencerag_hit_log persistenceInterview WorkflowReportWeakness真实 DeepSeek Smoke Test
```

因此当前状态应理解为：

> 回答评价所依赖的 RAG、LLM 和 Java 评分核心能力已经建立，但这些能力还没有被一个完整的 Evaluation Orchestration Service 串成最终业务事务。

Commit SHA、Commit message 和最终 `origin/main` 在完成当天最终 full regression 和 Git push 后补入本节。

---

**学习状态**

本阶段进一步理解了：

- Retrieval Query 与 Evaluation Prompt 的目的不同：前者寻找正确知识，后者评价用户实际回答；

- 用户错误回答不应该反过来控制 RAG 的知识检索方向；

- Milvus 原始 TopK 与真正可作为 LLM Evidence 的 chunk 不是同一个概念；

- LLM 能返回合法 JSON，不代表返回的是合法业务数据；

- 外部 DTO 使用 nullable wrapper type，可以保留模型缺字段这一事实；

- Java Validator 是 LLM 输出进入业务可信边界之前的重要防线；

- scoring point coverage 不只是检查数量，还需要校验 ID 归属、重复和完整集合；

- Prompt 中的回答 evidence 与 RAG Evidence 是两个不同概念；

- System Prompt 和 User Prompt 分离有利于区分稳定规则和本次评价事实；

- Prompt Version 是未来评价历史可追溯的一部分；

- RestClient 足以满足当前简单 Chat Completions 场景，没有必要为了一个 HTTP API 提前引入 AI Framework；

- Mock HTTP 测试证明的是 API Contract，而不是真实外部服务可用性；

- `finish_reason=length` 代表结构化 JSON 可能被截断，不能继续作为完整业务结果处理；

- Java 必须掌握最终 totalScore 和后续业务状态的控制权；

- AI 能力和确定性 Java 业务规则之间需要明确的信任边界。

---

**下一步**

进入：

```Plain
Evaluation Orchestration
```

下一阶段需要把今天已经独立完成的：

```Plain
EvaluationContextRAG RetrievalPrompt BuilderDeepSeek ClientLLM ValidatorScore Calculator
```

正式串起来。

目标链路：

```Plain
answerId→ EvaluationContext→ RAG→ Prompt→ DeepSeek→ Java Validation→ Java Score→ FollowUpPolicy→ INITIAL / FINAL→ answer_evaluation persistence
```

首先需要解决：

```Plain
MAIN Answer→ 什么情况下直接 FINAL→ 什么情况下保存 INITIAL 并生成一次 FOLLOW_UP
```

以及：

```Plain
FOLLOW_UP Answer→ 如何结合 MAIN + FOLLOW_UP→ 形成该 MAIN Question 唯一的 FINAL Evaluation
```

同时开始实现：

```Plain
FollowUpPolicyanswer_evaluation persistence
```

后续报告和用户薄弱点仍然只消费：

```Plain
FINAL Evaluation
```

暂不扩展：

```Plain
Query RewriteRerank复杂 similarity thresholdMQSpring AILangChain4jAgent Workflow
```

继续优先完成 9 月中旬前的完整 MVP 业务闭环。

### 2026-09-03｜完成 Evaluation Orchestration 与 RAG Trace Persistence，评价核心链路正式闭环

今日目标

昨天已经完成 Evaluation Core 的基础链路：

```Plain
EvaluationContext→ RAG Query→ Retrieval→ Prompt→ DeepSeek→ Java Validation→ Java Score
```

但当时仍缺少两个关键部分：

```Plain
1. 评价结果如何形成 Java 最终业务决策并落库2. 一次评价到底使用了哪些 RAG Evidence，如何进行历史追溯
```

因此今天的目标是完成：

```Plain
F1 AnswerEvaluation PersistenceF2 FollowUpPolicy + EvaluationStandardF3 Evaluation OrchestrationF4 RAG Retrieval Trace Persistence
```

最终希望形成：

```Plain
Answer→ EvaluationContext→ RAG Retrieval→ RAG Trace→ Prompt→ DeepSeek→ Java Validation→ Java Score→ EvaluationStandard→ FollowUpPolicy→ AnswerEvaluation→ EvaluationOrchestrationResult
```

---

F1：建立 AnswerEvaluation 持久化基础

首先补齐 `answer_evaluation` 的 Java 持久化能力。

新增：

```Plain
AnswerEvaluationEvaluationPhaseDecisionActionAnswerEvaluationMapperAnswerEvaluationMapper.xml
```

`answer_evaluation` 一行表示：

> 某个 Answer 对应的一次结构化评价结果。

其中：

```Plain
INITIAL
```

表示 MAIN 回答之后决定继续追问时产生的初评；

```Plain
FINAL
```

表示该 MAIN 问题最终可用于报告和薄弱点统计的评价结果。

这里没有让 LLM 直接决定业务状态，而是继续保持：

```Plain
LLM → 给建议Java → 做最终决策
```

数据库保存：

```Plain
follow_up_recommended
```

表示 LLM 是否建议追问；

而：

```Plain
decision_action
```

表示 Java 最终决定：

```Plain
FOLLOW_UPNEXT_MAINFINISH
```

二者刻意分离，避免模型输出直接控制面试流程。

---

F2：把评分等级和追问规则收回 Java

EvaluationStandard

确定第一版评分等级：

```Plain
90～100 → EXCELLENT80～89  → GOOD60～79  → FAIR0～59   → WEAK
```

版本：

```Plain
evaluation-standard-v1
```

这里没有采用 PASS / FAIL。

原因是当前 `level` 描述的是：

> 当前回答质量等级

而不是：

> 候选人是否通过面试。

同时将 `60` 作为 WEAK 与非 WEAK 的边界，可以和后面的追问阈值保持一致，避免出现两套相互冲突的“薄弱标准”。

FollowUpPolicy

最终第一版追问规则：

```Plain
只允许 MAIN 触发追问并且：LLM followUpRecommended = trueAND(    totalScore < 60    OR    存在未覆盖 CORE scoring point)
```

结果规则：

```Plain
MAIN + 需要追问→ INITIAL + FOLLOW_UPMAIN + 不需要追问→ FINAL + NEXT_MAIN / FINISHFOLLOW_UP Answer→ 始终 FINAL + NEXT_MAIN / FINISH
```

这里没有让 Java 自己凭空生成追问。

原因是 Java 可以判断：

> 要不要追问

但具体自然语言追问内容仍然需要 LLM 已经生成合法候选。

因此 Java 的职责是：

```Plain
接受 / 拒绝 LLM 的追问建议
```

而不是自己生成语义问题。

---

F3：建立 Evaluation Orchestration

新增：

```Plain
EvaluationOrchestrationServiceEvaluationOrchestrationResult
```

将此前分散的组件真正串成：

```Plain
answerId→ 幂等检查→ EvaluationContext→ Retrieval→ Prompt→ DeepSeek→ Validation→ Score→ Level→ FollowUpPolicy→ AnswerEvaluation
```

其中首先执行：

```Plain
AnswerEvaluationMapper.getByAnswerId(answerId)
```

如果已经存在评价，则直接返回已有结果，不再次调用 RAG 和 DeepSeek。

这样实现了第一层：

```Plain
顺序重试幂等
```

但没有把它描述成完整并发幂等，因为：

```Plain
check→ external calls→ insert
```

之间仍存在双请求同时进入的 race condition。

并发评价控制留给后续 Interview Workflow，通过 Answer 状态、Session version、数据库唯一约束和短事务共同解决。

---

为什么 EvaluationOrchestrationService 没有加 @Transactional

Evaluation Orchestration 中包含：

```Plain
EmbeddingMilvusDeepSeek
```

这些都是可能耗时较长的外部调用。

如果直接：

```Plain
@Transactionalpublic EvaluationOrchestrationResult evaluate(...)
```

就意味着数据库事务可能横跨整个网络调用过程。

这会造成：

```Plain
事务时间过长连接长期占用锁持有时间扩大外部服务延迟直接影响数据库事务
```

因此最终选择：

> Orchestration 本身不启用长事务。

真正需要原子性的数据库操作，再拆成独立短事务。

这个原则后来也直接用于 RAG Trace Persistence。

---

F4：为什么需要新增 rag_retrieval_batch

原数据库只有：

```Plain
rag_hit_log
```

它要求：

```Plain
chunk_id NOT NULLFK → knowledge_chunk.id
```

最开始需要解决的问题是：

> `rag_hit_log` 到底记录 Milvus raw hits，还是最终真正进入 Prompt 的 Evidence？

检查当前 Retrieval 实现后发现真实流程是：

```Plain
Milvus rawHits        ↓MySQL eligibility        ↓RetrievedChunk evidence        ↓Prompt
```

MySQL 会继续检查：

```Plain
knowledge_chunk.status = ACTIVEknowledge_document.processing_status = READY当前 document versionembedding model 一致embedding version 一致
```

因此一个 Milvus raw hit 可能最后并没有进入 Prompt。

如果把 raw hit 写进 `rag_hit_log`，以后查看日志会产生错误含义：

> LLM 当时使用过这个知识片段。

实际上它可能早已被 MySQL 淘汰。

因此最终确定：

```Plain
rawHits= Milvus 原始候选，仅作为运行时诊断信息evidence= 通过 MySQL eligibility 后真正进入 Prompt 的知识rag_hit_log= 只记录 evidence
```

---

Zero Evidence 暴露了原数据库设计缺口

继续分析时发现：

```Plain
rawHits = 0evidence = 0
```

和：

```Plain
rawHits = 5evidence = 0
```

在原有 `rag_hit_log` 中都会表现为：

```Plain
0 rows
```

因此无法判断：

> 到底根本没有召回候选，还是召回了候选但全部被业务有效性过滤。

更重要的是，也无法单独证明：

> 这次 Retrieval 确实执行过。

因此新增：

```Plain
rag_retrieval_batch
```

一行表示：

> 一次成功返回 `EvaluationRetrievalResult` 的真实 Retrieval。

新增：

```Plain
raw_hit_countevidence_hit_count
```

于是可以表达：

```Plain
raw=0 / evidence=0raw=5 / evidence=0raw=5 / evidence=3
```

而不需要把被淘汰的 raw hits 全部持久化。

---

RAG Trace 最终数据模型

新增关系：

```Plain
interview_answer        │        └── 1 : N            rag_retrieval_batch                    │                    └── 1 : 0..N                        rag_hit_log                                │                                └── knowledge_chunk
```

同一个 Answer 可以产生多个 Retrieval Batch。

没有增加：

```Plain
UNIQUE(answer_id)
```

因为：

```Plain
Retrieval 成功→ DeepSeek 失败→ 用户重试
```

第二次评价应该生成新的 Retrieval Batch。

同时新增：

```Plain
rag_hit_log.retrieval_batch_idFK→ rag_retrieval_batch.retrieval_batch_id
```

防止孤立 Hit。

但暂时没有给：

```Plain
answer_evaluation.retrieval_batch_id
```

增加数据库 FK。

当前保持逻辑关联，等 Workflow 和 fixture 体系稳定后再判断是否需要加强数据库约束。

---

rank_no 为什么保留 Milvus 原始排名

假设 Milvus：

```Plain
rank 1 → eligiblerank 2 → 被 MySQL 淘汰rank 3 → eligiblerank 4 → 被淘汰rank 5 → eligible
```

最终保存：

```Plain
135
```

而不是重新编号为：

```Plain
123
```

这样日志仍然能够表达：

> 这个 Evidence 在原始向量召回中的真实位置。

因此 `rag_hit_log.rank_no` 的语义最终固定为：

```Plain
original Milvus vector rank
```

而不是：

```Plain
eligible evidence rank
```

---

使用独立短事务保存 RAG Trace

新增：

```Plain
RagTracePersistenceService
```

事务只覆盖：

```Plain
INSERT rag_retrieval_batch→ INSERT rag_hit_log × 0..N
```

即：

```Plain
@Transactionalpersist(...)
```

但是：

```Plain
EmbeddingMilvusPromptDeepSeek
```

全部处于事务之外。

这样既保证：

```Plain
Batch + Hits
```

具有原子性，又不会让外部调用占用数据库事务。

真实 MySQL Integration Test 验证：

```Plain
Batch INSERT 成功→ Hit INSERT 因 chunk FK 失败→ RuntimeException→ 整个短事务 rollback
```

最终数据库：

```Plain
batch = 0hits = 0
```

证明事务边界真实生效，而不是仅通过 Mock 推断。

---

为什么 DeepSeek 失败后仍保留 Retrieval Trace

最终 Orchestration 顺序确定为：

```Plain
Retrieval→ Trace Persistence→ Prompt→ DeepSeek
```

而不是：

```Plain
Retrieval→ DeepSeek→ 成功以后再写 Trace
```

原因是：

> Retrieval 本身已经是真实发生的系统行为。

即使：

```Plain
RAG 成功→ Trace 成功→ DeepSeek 网络失败
```

本次检索记录依然具有诊断价值。

因此允许数据库出现：

```Plain
rag_retrieval_batch 有rag_hit_log 有或为空answer_evaluation 无
```

这不是脏数据，而表示：

> Retrieval 成功，但后续评价失败。

重新评价时生成新的 batch。

最终成功的：

```Plain
answer_evaluation.retrieval_batch_id
```

只记录真正产生该次评分的 Retrieval Batch。

---

遇到的问题与修复

问题 1：重复构造器导致生产代码无法编译

`EvaluationOrchestrationService` 同时存在：

```Plain
显式全参构造器@RequiredArgsConstructor
```

Lombok 又生成了一份相同签名构造器，造成编译失败。

修复：

```Plain
保留显式构造器删除 @RequiredArgsConstructor
```

没有修改业务逻辑。

问题 2：RagRetrievalBatchMapper 缺失 INSERT XML

Java Mapper 已定义：

```Plain
insertBatch()
```

但 XML 中遗漏对应：

```Plain
<insert>
```

会在运行期产生 MyBatis `BindingException`。

通过真实 Mapper Test 暴露后，补齐完整 INSERT。

问题 3：MySQL CHECK 异常类型与测试预期不同

FK / UNIQUE 违反被 Spring 翻译为：

```Plain
DataIntegrityViolationException
```

但 MySQL 8.0.45 的 CHECK constraint error 3819 在当前 MyBatis/Spring 环境下表现为：

```Plain
UncategorizedSQLException
```

数据库约束本身已经正确生效。

因此没有修改生产代码，而是按照真实驱动异常行为调整测试断言。

问题 4：JSON 测试最初依赖表现形式

Orchestration 测试中 JSON 比较最初存在断言失败。

原因不是生产 JSON 映射错误，而是测试比较方式依赖字符串表示。

修复为：

```Plain
ObjectMapper→ JsonNode→ JSON 语义比较
```

避免 whitespace 和字段顺序影响结果。

---

最终验证

生产编译：

```Plain
124 production source filesBUILD SUCCESS
```

F4 Orchestration：

```Plain
20 tests0 failures0 errors0 skipped
```

Evaluation + RAG Trace focused regression：

```Plain
197 tests0 failures0 errors0 skipped
```

完整回归：

```Plain
917 tests0 failures0 errors18 skippedBUILD SUCCESS
```

其中 18 个 skipped 为保持关闭的真实外部服务 Smoke Tests。

本轮：

```Plain
真实 MySQL 8.0.45：已验证真实 Embedding：未调用真实 Milvus：未调用真实 DeepSeek：未调用DeepSeek Client：仅 Mock HTTP 验证
```

测试结束后确认：

```Plain
rag_retrieval_batch = 0rag_hit_log = 0
```

无测试残留。

---

当前完成状态

截至今天，评价核心链路已经完整实现：

```Plain
Answer→ EvaluationContext→ Evaluation RAG Query→ Embedding→ Milvus rawHits→ MySQL Eligibility→ EvaluationRetrievalResult→ RAG Trace Short Transaction   ├─ rag_retrieval_batch   └─ rag_hit_log→ Evaluation Prompt→ DeepSeek Suggestion→ Java Validation→ Java Score→ EvaluationStandard→ FollowUpPolicy→ AnswerEvaluation Persistence→ EvaluationOrchestrationResult
```

当前已经能够回答：

```Plain
用户回答了什么评价使用了什么题目和评分点RAG 查询是什么Milvus 返回了多少候选多少 Evidence 真正参与评价具体使用了哪些 KnowledgeChunk使用了哪个 Embedding 模型和版本使用了哪个 DeepSeek 模型使用了哪个 Prompt Version使用了哪个 Evaluation StandardLLM 建议是否追问Java 最终为什么选择 FOLLOW_UP / NEXT_MAIN / FINISH最终五维评分和总分是多少
```

评价核心已经形成可追踪、可验证的闭环。

---

Git 阶段收口

本阶段已正式提交并推送到 GitHub：

```Plain
commit:621efe50e196033706894723c806370b9bd4e646message:feat: complete evaluation orchestration and rag trace persistence
```

最终：

```Plain
HEAD = origin/mainworking tree clean
```

未执行：

```Plain
mergerebaseresetforce push
```

---

下一步

下一阶段进入：

```Plain
Interview Workflow
```

重点解决：

```Plain
评价什么时候触发Answer：SUBMITTED→ EVALUATING→ EVALUATED / FAILEDINITIAL / FINAL 如何驱动流程FOLLOW_UP 时如何创建新的 InterviewQuestionMAIN / FOLLOW_UP Question 状态如何推进hasNextMainQuestion 如何从真实题目计划计算Session 如何推进当前题以及结束并发重复评价如何控制哪些操作必须进入同一个短事务
```

Workflow 完成后，当前已经实现的 Evaluation Core 才会真正从：

```Plain
“可以独立评价一个 Answer”
```

进入：

```Plain
“可以驱动一场完整模拟面试”
```

### 2026-09-10

开发内容：开始进入 Interview Session 创建与启动阶段，在已完成 Evaluation Core 和 Interview Workflow 的基础上，为“创建一场真实面试”补充持久化和选题基础能力。新增 Session 活跃状态查询、Session 创建与启动相关 Mapper，补充 MAIN 面试题批量持久化能力，并增加按难度查询启用题目、按题目查询启用评分点等查询接口，为后续一次性生成整场 MAIN 计划做准备。

关键问题：此前系统已经能够对现有 Answer 执行评价并根据结果进入 FOLLOW_UP、NEXT_MAIN 或 FINISH，但还缺少一条真正从 `userId + difficulty` 开始创建面试的入口链路。创建 Session 不只是插入一条 `interview_session` 数据，还必须同时解决题目数量、领域覆盖、评分点快照、MAIN 顺序以及第一题激活等问题。另外，如果题目和评分点在面试过程中继续直接读取题库实时数据，管理员后续修改题目可能导致已经开始的面试内容发生变化。

修改原因：将“面试计划生成”与已经完成的“回答后的 Workflow 推进”分开处理。Workflow 继续只负责已有 Session 中的回答、评价和状态推进；Session 创建阶段负责确定本场面试要问哪些 MAIN，并在创建时冻结题目内容、参考答案和评分点。这样可以保证一场已经开始的面试不受后续题库修改影响，同时避免把选题职责混入 Evaluation 或 Workflow。

验证结果：完成 Session 创建阶段所需 Mapper 和 SQL 基础能力，确定 MAIN 在 Session 创建时一次性生成和持久化的方案；候选题只从指定难度且 `ENABLED` 的题库中读取，评分点只读取启用数据。明确后续继续实现 `InterviewPlanConfig`、`QuestionPlanSelector`、MAIN Snapshot 和 Session 创建事务，不提前进入 Controller/API。

---

### 2026-09-14

开发内容：完成 Interview Session 创建、选题、MAIN 快照和启动链路。新增 `InterviewPlanConfig`，统一维护不同难度的面试计划，目前 EASY、MEDIUM、HARD 分别规划 5、7、9 道 MAIN；新增确定性的 `QuestionPlanSelector`，先满足必需知识领域覆盖，再从剩余题目中补足数量。完成 `InterviewQuestionSnapshotFactory`，在面试创建时冻结题目内容、参考答案和评分点；随后实现 `InterviewSessionService` 与 `InterviewSessionTransactionService`，将 Session 创建、全部 MAIN 持久化、第一题激活和 Session 启动串成完整业务链。

关键问题：第一，Session 创建存在并发风险。如果两个请求同时查询不到活跃 Session，仅依靠 Java 中的一次 `getActiveSessionByUserId` 检查，两个请求仍可能分别创建一场面试。第二，批量插入 MAIN 后不能依赖 MyBatis/MySQL 一定为 Java 集合中的每个对象可靠回填自增 ID，而启动 Session 又必须获得第一道 MAIN 的真实数据库 ID。第三，评分点快照字段从历史实现中的 `scoringPointId` 调整为更简洁的 `id` 后，还需要避免破坏已经存在的 Workflow 快照解析逻辑。第四，Session、MAIN 和第一题状态必须作为一个整体成功或失败，不能留下只创建了 Session、但题目没有完整生成的中间状态。

修改原因：并发控制最终没有使用 JVM `synchronized`，也没有为了 MVP 修改数据库 Schema，而是在创建事务开始后对当前用户行执行 `SELECT ... FOR UPDATE`，获得同一用户维度的数据库排他锁，再在事务内第二次检查是否已经存在 `CREATED/IN_PROGRESS` Session。外层第一次查询继续保留，作为普通重复请求的快速幂等路径；事务内第二次查询才承担真正的并发正确性。MAIN 使用批量插入提高持久化完整性，但第一题不依赖批量 generated key 回填，而是通过 `sessionId + planOrder = 1` 从数据库重新读取。Session 的创建、MAIN 快照生成和持久化、第一题 `PENDING → WAITING_ANSWER`、Session `CREATED → IN_PROGRESS` 全部放在一个只包含本地数据库操作的短事务中，不包含 DeepSeek、Embedding、Milvus、RAG 或其他外部网络调用。

快照方面，`scoringPointsSnapshot` 新写入只保留 `id`、`pointType`、`content` 和 `weight`，不保存 `questionId`、状态、排序字段和时间字段；同时保留对旧 `scoringPointId` JSON 的兼容读取，避免破坏已有数据和 Workflow。Snapshot Factory 在正式生成面试计划前再次校验评分点 ID、题目归属、内容、权重以及总权重必须为 100，防止异常题库数据进入正在执行的面试。

验证结果：Session 创建链已经完整闭合：

`userId + difficulty → InterviewPlanConfig → QuestionPlanSelector → scoring points → CREATED Session → MAIN snapshots → batch persistence → first MAIN WAITING_ANSWER → Session IN_PROGRESS`。

真实 MySQL 双线程并发测试验证，同一用户同时发起两个创建请求时最终只存在 1 个活跃 Session，两个请求返回相同 Session ID。Session 新增单元及 Mock 测试 78 个全部通过；真实 Mapper/事务测试 4 个全部通过，并覆盖正常创建、事务回滚和并发双创建；原有 Interview Workflow 回归 51 个测试全部通过。最终全量测试 `1024 / 0 / 0 / 18`，`BUILD SUCCESS`，18 个跳过项仍为未启用的真实外部服务 Smoke Tests，本阶段没有真实调用 DeepSeek、Embedding 或 Milvus。

完成后更新 `AGENTS.md`，同步 Session 创建链、5/7/9 计划规则、快照结构、事务边界和并发方案，并提交推送至 `main`。最终提交为 `5322cf2d2d0cb682238a9c80de27a65f2fce632e`，本地 `main` 与 `origin/main` 一致，工作区 clean。下一阶段进入 External Interview API。

### 2026-09-14｜面试会话创建、计划持久化与并发控制

**开发内容：** 完成 Interview Session 创建与启动流程。用户提交 `userId + difficulty` 后，系统先检查是否已有活动会话，再根据难度配置确定计划题数和必选知识领域，从启用题库中确定性选择全部 MAIN 主问题，读取启用评分点并生成题目快照，最后一次性持久化整场面试计划并激活第一道题。当前题量规则为 `EASY=5`、`MEDIUM=7`、`HARD=9`。

**技术选型：** 面试计划采用“创建时一次性确定并持久化”的方案，而不是每答完一道题后重新抽题，保证刷新、断线、服务重启后计划不漂移。选题第一版采用确定性策略：先满足必选分类，再按稳定顺序补足，不加入随机、历史薄弱点加权或近期题目去重。并发创建没有使用 JVM `synchronized`，而是采用 MySQL 用户行 `SELECT ... FOR UPDATE` 加事务内二次活动会话检查，利用数据库锁解决同一用户同时创建两场面试的问题。

**核心实现：** `InterviewSessionService` 负责请求校验、活动会话快速检查、读取 `InterviewPlanConfig`、加载候选题、执行 `QuestionPlanSelector` 和准备快照数据；`InterviewSessionTransactionService` 在短事务中锁定用户行、重新检查活动会话、插入 Session、批量插入所有 MAIN、激活 `planOrder=1` 的第一道题并将 Session 从 `CREATED` 推进到 `IN_PROGRESS`。MAIN 快照固定保存 `questionId`、`category`、`knowledgePoint`、`questionContent`、`referenceAnswerSnapshot` 和评分点快照，后续题库修改不影响历史面试。

**问题与修复：** 并发场景中仅在 Service 外层检查活动 Session 存在竞态窗口：两个请求可能同时检查到“没有活动会话”。最终把真正的并发边界放入事务，通过锁定同一用户行后再次执行活动 Session 查询解决。真实数据库测试第一次运行还出现 Maven 子进程未继承 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，导致 `${DB_URL}` 未解析；确认属于执行环境问题后，仅向 Maven 子进程注入已有 Windows 用户环境变量，真实 MySQL 测试恢复通过。

**测试验证：** Session Unit/Mock 聚焦测试运行 78 个，0 失败、0 错误、0 跳过；真实 Mapper / Transaction 测试运行 4 个，全部通过，覆盖正常创建、主键回填、真实 Mapper SQL、事务回滚和双线程并发创建。现有 Workflow 回归运行 51 个测试全部通过。最终完整回归运行 1024 个测试，0 失败、0 错误、18 个受保护的真实外部服务测试跳过，`BUILD SUCCESS`。本阶段未调用真实 DeepSeek、Embedding 或 Milvus。

**Git 提交：** `5322cf2d2d0cb682238a9c80de27a65f2fce632e feat: complete interview session creation workflow`，已推送远端 `main`。

**当前状态：** 面试创建、完整 MAIN 计划持久化、第一题激活和同用户并发创建保护已经完成；计划创建后不再重新选题。此时内部 Workflow 已可推进面试，但面向前端的正式 Interview HTTP API 尚未完整开放。

**学习状态：** 已理解“快速幂等检查”和“真正并发安全边界”不是同一件事；数据库悲观锁可以用于锁定某个用户的业务创建窗口，而外部 AI 调用不应进入该事务。也进一步理解题目快照是保证历史事实稳定的重要手段。

**下一步：** 完成 External Interview API，把创建会话、恢复当前面试和提交回答三条内部业务链路安全暴露给前端，并重点解决 HTTP 重试下的回答幂等。

### 2026-09-15｜External Interview API 与回答提交幂等

**开发内容：** 完成普通用户面试主流程的外部 API，包括 `POST /api/interviews`、`GET /api/interviews/current` 和 `POST /api/interviews/{sessionId}/answers`。创建接口支持首次创建或返回已有活动 Session；当前会话接口在没有活动面试时返回成功的空数据；回答接口把已有 Workflow、Evaluation、RAG 和状态推进能力连接成完整用户调用链。

**技术选型：** 外部接口继续使用 JWT 恢复的 `AuthenticatedUser` 作为唯一用户身份来源，客户端不允许提交 `userId`。Controller 和 Response 使用独立 DTO/VO，不直接暴露 Entity。回答幂等采用应用层状态恢复加数据库唯一约束双层保护：`requestId` 用于识别同一次请求，`UNIQUE(interview_question_id)` 和 `UNIQUE(request_id)` 作为最终并发兜底。没有增加 JVM 锁，也没有修改 Schema。

**核心实现：** 新回答先在短事务中插入 `interview_answer` 并把当前 Question 从 `WAITING_ANSWER` 改为 `ANSWERED`，随后提交事务，再由 `InterviewWorkflowService.evaluateAnswer()` 在事务外执行 Embedding、Milvus、RAG、DeepSeek 和 Evaluation。已有 Answer 根据状态恢复：`EVALUATED` 直接读取持久化 Evaluation 和最终 Session 状态重建响应；`SUBMITTED` 继续进入 Workflow；`FAILED` 使用原重试路径；`EVALUATING` 拒绝启动第二次 AI 评价。最终下一题始终通过数据库中的 `currentInterviewQuestionId` 重新加载，而不是相信 LLM 或中间返回值。

**问题与修复：** 初版幂等流程先检查“请求中的题目是不是当前题”，导致 Q1 已成功完成并推进到 Q2 后，客户端重放同一个 Q1 `requestId` 会被错误拒绝。修复为先按 `requestId` 查询已有 Answer，再区分“已有请求恢复”和“真正的新请求”；只有新请求才执行当前题校验。并修正 `SubmitInterviewAnserRequest`、相关字段及 `claritySocre` 等拼写错误，统一真实枚举类型。并发唯一键冲突通过 `DuplicateKeyException` 后重新查询数据库，恢复相同幂等请求或拒绝真实冲突。

**测试验证：** `InterviewServiceTest` 与 `InterviewControllerTest` 聚焦运行 29 个测试，0 失败、0 错误、0 跳过。最终完整回归运行 1053 个测试，0 失败、0 错误、18 个 guarded real-service tests 跳过，`BUILD SUCCESS`。普通真实 MySQL Mapper 和事务测试均通过；DeepSeek、Embedding 和 Milvus 的真实外部调用保持关闭。

**Git 提交：** `28d17ac06a2526415358dbc7620b316e0f169a12 feat: complete external interview api`，已推送远端 `main`。

**当前状态：** 普通用户已经可以通过正式 HTTP API 创建面试、恢复当前会话、提交 MAIN/FOLLOW_UP 回答，并获得持久化后的评价、下一动作和下一道真实问题。核心 Interview 主流程已经可以从外部调用。

**学习状态：** 已理解 HTTP 幂等不能简单等同于“重复请求直接报错”，成功请求在状态推进后仍应能够根据数据库事实恢复原结果；同时进一步掌握唯一约束、状态机和短事务在重复提交与并发保护中的分工。

**下一步：** 实现面试结束后的 Interview Report：确定每道 MAIN 的最终有效 Evaluation、总体分数计算、报告生成状态机、DeepSeek 报告生成以及安全的 Report API。

### 2026-09-17｜Interview Report 生成、状态机与外部 API

**开发内容：** 完成 Interview Report 全链路，包括最终有效 Evaluation 聚合、Java 总分计算、Report 状态机、`DeepSeekInterviewReportGenerator`、报告持久化事务以及 `GET /api/interviews/{sessionId}/report`、`POST /api/interviews/{sessionId}/report` 两个外部接口。报告失败后允许重试，READY 状态重复请求保持幂等。

**技术选型：** Report 与 Session 完成状态保持独立：Session 可以已经 `COMPLETED`，而 Report 仍处于 `NOT_STARTED / GENERATING / READY / FAILED`。最终评价采用“每个 MAIN 只计一次”的规则：存在追问 FINAL 时优先 FINAL，否则使用 INITIAL；FOLLOW_UP 不作为独立题目重复计分。总体分数由 Java 使用 `BigDecimal` 计算最终有效 MAIN `totalScore` 的算术平均，保留两位并 `HALF_UP`，LLM 无权重新计算或覆盖。DeepSeek Report 复用既有 JSON completion Client，不重新建立第二套 HTTP 基础设施。

**核心实现：** Report 状态机为 `NOT_STARTED → GENERATING → READY`，失败时 `GENERATING → FAILED`，FAILED 可再次 claim 到 GENERATING。`InterviewReportService` 在事务外查询最终有效 Evaluation、校验完整性、计算 `overallScore` 并调用 Generator；`InterviewReportTransactionService` 使用短事务完成 `INSERT interview_report + Session GENERATING→READY + total_score`。Generator 的 system prompt 明确把题目和 Evaluation 文本视为不可信数据，只允许 LLM 输出 summary、strengths、weaknesses 和 suggestions；`result` 根据现有 `EvaluationStandard` 由 Java 确定。所有 LLM JSON 都经过长度、空值、列表数量和字段内容校验。

**问题与修复：** 开发过程中发现 `InterviewReport` 缺少 `promptVersion`、时间字段命名不一致，Mapper XML 还存在列名和 `session_id` 查询条件错误，均在真实数据库验证前修正。另一个关键问题是 claim 到 `GENERATING` 后若后续步骤失败，Session 可能永久卡住，因此加入失败恢复：重新加载最新 Session，仅在仍为 GENERATING 时执行 FAILED 更新，同时保留并重新抛出原始异常。真实 MySQL 聚焦测试首次同样因 Maven 子进程未继承数据库环境变量而失败，正确注入现有用户环境变量后验证通过。

**测试验证：** Report/API 聚焦测试运行 78 个，0 失败、0 错误、0 跳过，包含真实本地 MySQL Mapper 和事务测试，并真实验证 READY 更新失败时已插入的 Report 会回滚。最终完整回归运行 1117 个测试，0 失败、0 错误、18 个受保护真实外部服务测试跳过，`BUILD SUCCESS`。Report DeepSeek 行为使用 Mock HTTP 验证，没有调用真实 DeepSeek、Embedding 或 Milvus。

**Git 提交：** `b1e85b2beefc44f02552a6a25a2f28144a2f562c feat: complete interview report generation and api`，已推送远端 `main`。

**当前状态：** 一场已完成面试可以生成唯一 Report，失败后可安全重试，READY 后重复请求不会再次调用模型。GET Report 为无副作用状态查询，POST Report 负责首次生成和 FAILED retry，外部响应只暴露用户真正需要的报告字段。

**学习状态：** 已理解“业务完成状态”和“派生结果生成状态”应解耦；外部 LLM 应只负责自然语言能力，分数、等级、状态转换和数据库写入仍由 Java 控制。同时进一步掌握 claim 状态、乐观锁、失败恢复和跨步骤事务原子性的设计。

**下一步：** 根据最终有效 MAIN Evaluation 更新长期 `user_weakness`，解决同一知识点重复发现、解决、复发和 Report retry 下重复累计的问题。

### 2026-09-18｜User Weakness 长期薄弱点跟踪与事务一致性

**开发内容：** 完成 `user_weakness` Java 业务层，实现用户长期薄弱知识点的发现、重复累计、解决和复发。新增 `UserWeakness`、`UserWeaknessStatus`、Mapper、Service 和真实 MySQL 测试，并把 Weakness 更新接入 Report 成功事务。

**技术选型：** Weakness 不再依赖 LLM 报告文本，而是直接使用已经通过校验并持久化的 MAIN 最终有效 Evaluation。一次 Session 内先按 `QuestionCategory + knowledgePoint` 聚合，同一知识点出现多道 MAIN 时计算本场平均分并只处理一次。判断标准复用已有 `EvaluationStandard`，不新建第二套阈值：`FAIR / WEAK` 即平均分低于 80 时记为 weakness，`GOOD / EXCELLENT` 时尝试解决 ACTIVE weakness。`weaknessScore = 100 - sessionKnowledgeScore`，数值越高表示越薄弱。

**核心实现：** `sessionKnowledgeScore` 使用 `BigDecimal` 算术平均并保留两位。发现 weakness 时使用 MySQL `INSERT ... ON DUPLICATE KEY UPDATE`，利用 `(user_id, category, knowledge_point)` 唯一约束原子完成首次 INSERT 和重复 UPDATE：首次 count=1；再次发现 `discovered_count + 1`；RESOLVED 后再次变差则重新 ACTIVE 并清空 `resolved_at`。表现达到 GOOD/EXCELLENT 时只 UPDATE 现有 ACTIVE 记录为 RESOLVED，不插入新记录、不增加 `discovered_count`、不修改 `last_discovered_at`。`discovered_count` 表示该知识点被多少场面试发现为薄弱，而不是做错多少道题。

**问题与修复：** 初版 `UserWeaknessMapper.xml` 把业务查询错误写成 `WHERE id = #{id}`，与真实参数 `userId + category + knowledgePoint` 不匹配，修正为按唯一业务键查询。Service 初版还缺少 Evaluation 内部数据校验，并单独硬编码 80 分阈值；随后补齐 null、blank、分数越界等检查并直接复用 `EvaluationStandard`。事务方面，为防止 Report 失败重试造成 `discovered_count` 重复增加，把 Weakness 更新放入 `InterviewReportTransactionService`：`INSERT Report → apply Weakness → mark READY` 使用同一事务，READY 重放不会再次进入 Weakness 更新。

**测试验证：** `UserWeaknessMapperTest` 真实 MySQL 运行 8 个测试，全部通过；`UserWeaknessServiceTest` 运行 18 个测试，全部通过。真实事务测试验证 Report INSERT、Weakness 更新和 Session READY 一起成功；使用 stale version 使 `markReportReady` 失败时 Report 和 Weakness 同时回滚；Weakness SQL 自身失败时 Report 同样回滚且 Session 不进入 READY。最终完整回归运行 1145 个测试，0 失败、0 错误、18 个 guarded real-service tests 跳过，`BUILD SUCCESS`。

**Git 提交：** `76b5234cded46c28361ce0de87a970c87bb6ed2e feat: add user weakness tracking`，已推送远端 `main`。

**当前状态：** 长期薄弱知识点更新已经进入正式 Report 完成链路，并具备首次发现、重复发现、解决和复发语义；同一场面试同一知识点最多使 `discovered_count` 增加一次。当前阶段没有增加 Weakness 查询 API，也没有修改 Schema。

**学习状态：** 已理解 MySQL UPSERT 的适用场景、数据库唯一键与业务唯一键的区别，以及 SQL 中 `user_id = #{userId}` 左侧是数据库字段、右侧是 Java Mapper 参数。也进一步理解派生数据如果参与幂等计数，必须与最终成功状态处于同一个事务边界。

**下一步：** 完成用户历史面试列表和单场面试详情查询，为前端提供完整的只读面试数据接口，并避免详情查询中的 N+1 和内部敏感字段泄漏。

### 2026-09-18｜Interview History / Detail 与后端业务开发收口

**开发内容：** 完成 `GET /api/interviews/history?page=1&pageSize=10` 和 `GET /api/interviews/{sessionId}`。History 分页返回当前用户已完成面试；Detail 返回某一场 Session 的题目、回答和评价时间线。该阶段完成后，当前 MVP 规划中的正式后端业务功能开发结束。

**技术选型：** History 采用 MySQL `LIMIT / OFFSET + COUNT(*)`，不额外引入 PageHelper；只查询 `COMPLETED` Session，当前进行中的面试仍由 `/api/interviews/current` 负责。历史排序固定为 `ended_at DESC, id DESC`，保证相同结束时间下分页稳定。Detail 不使用逐题查询，而是批量读取 Session Questions、Answers 和 Evaluations，再在 Java 中通过 Map 关联，避免明显 N+1。所有接口继续通过 JWT `AuthenticatedUser` 获取 userId，并先执行 ownership 校验。

**核心实现：** History 默认 `page=1`、`pageSize=10`，限制 `page>=1` 且 `pageSize` 为 1～50，响应包含页码、每页数量、总数、总页数和安全的 Session 列表字段。Detail 从 `interview_question` 历史快照按 `display_order ASC, id ASC` 返回 MAIN 与 FOLLOW_UP 的真实时间线；Question 无 Answer 时返回 null，Answer 无 Evaluation 时 Evaluation 为 null。MAIN 展示自己的 INITIAL Evaluation，FOLLOW_UP 展示自己的 FINAL Evaluation，不把 Report 使用的 FINAL-effective 结果覆盖回原 MAIN。`strengths` 和 `missingPoints` JSON 使用 Jackson 转成 `List<String>`。

**问题与修复：** 本阶段重点不是状态机，而是查询边界。实现中明确先校验 Session ownership，再读取 Questions / Answers / Evaluations，避免通过猜测 sessionId 越权读取他人数据；批量 Mapper 在 ID 列表为空时直接跳过，避免生成非法 `IN ()`。同时通过专用 VO 白名单排除 `referenceAnswerSnapshot`、`scoringPointsSnapshot`、`followUpTargetPoints`、Answer `requestId/errorCode`、Evaluation `rawResult`、RAG 检索标识、LLM 元数据、内部 decision 和 Session version 等字段。非法持久化 JSON 直接视为内部数据不一致，不静默替换为空列表。

**测试验证：** `InterviewHistoryMapperTest` 真实 MySQL 运行 2 个测试，0 失败、0 错误、0 跳过；`InterviewDetailMapperTest` 运行 1 个真实 MySQL 测试并通过。`InterviewHistoryServiceTest` 运行 16 个测试，`InterviewControllerTest` 运行 14 个测试。History / Detail 聚焦共运行 33 个测试，全部通过并 `BUILD SUCCESS`。最终完整回归运行 1168 个测试，0 失败、0 错误、18 个 guarded real-service tests 跳过，`BUILD SUCCESS`。本阶段没有调用真实 DeepSeek、Embedding 或 Milvus。

**Git 提交：** `bea3b2cf4231a7c497694a7df42558bac4e1c996 feat: add interview history and detail api`，已推送远端 `main`。

**当前状态：** Evaluation Core、Interview Workflow、Session Creation、External Interview API、Interview Report、User Weakness、Interview History / Detail 均已完成。当前 `HEAD == origin/main`，工作区 clean。后端正式业务功能开发到此停止，下一阶段不再继续新增后端功能。

**学习状态：** 已理解列表接口与详情接口的查询粒度差异，掌握稳定分页排序、SQL 层用户隔离、Service ownership 校验、批量查询避免 N+1 和响应 VO 字段白名单。也明确 Report 聚合视角与 Detail 真实时间线视角不同：前者每个 MAIN 只取最终有效评价，后者保留实际 MAIN / FOLLOW_UP 答题过程。

**下一步：** 进入 MVP Full Code Review，对整个后端仓库进行一次系统性代码审查和最终回归，只修复真实 blocker、权限/事务/并发/异常边界问题；审查通过后进入前端开发、Demo 验证、文档整理和项目 Freeze。

10. ## 面试追问记录

日期：待项目完成后填写

设计点：整理项目可能被追问的技术问题和回答要点。

计划覆盖：

- 为什么题库负责出题而 RAG 不负责出题

- Java 后端如何限制每道主问题最多追问一次

- 如何避免 LLM 直接控制业务状态

- 为什么需要保存题目、评分点和知识 chunk 的历史版本

- 回答已保存但 LLM 调用失败时如何恢复

- `InMemoryEmbeddingStore` 迁移到 Milvus 时如何减少业务代码修改
