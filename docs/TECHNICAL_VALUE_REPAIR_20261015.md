# Jev 可靠性与 Spring AI 接入价值验证（2026-10-15，本地实验记录）

> **发布状态：本地实验，尚未合入 GitHub `main`。** 本节和专项报告记录 2026-10-15 本地工作区的 Jev 可靠性改动、Spring AI 契约测试及其回归日志。`main` 当前不包含 Jev 反馈重试、共享请求预算和对应新增测试；不能将以下离线结果视为 GitHub 版本已实现的能力。

面向 Java 后端实习、校招及初级岗位的模拟面试项目。Java 21、Spring Boot 3.5.16、MyBatis、MySQL、Spring AI 1.1.8、Milvus 和 DeepSeek；Jev 是显式选择的评分策略。开发与验收资料见[文档导航](README.md)。

主问题从固定题库选择并在创建面试时保存。回答后才进行 RAG 检索；Java 管理鉴权、状态转换、五维分数校验、总分计算、确定性追问规则和最终报告。模型输出均为待校验的外部输入。

## Jev 与 DeepSeek 的职责

默认 `AI_JUDGE_PROVIDER=deepseek`，保持原 DeepSeek 请求、模型 `deepseek-flash` 和评分策略。显式选择 `jev` 后：

```text
Jev 原生评分（仅一次）→ Java 校验并固定五维分数、covered、追问建议
  → DeepSeek 生成反馈 → 严格 JSON / 原文证据校验 → 原有 Java 评分及追问策略
  → 可修复的反馈协议失败：有预算时只重试反馈一次
  → 仍然失败或不可重试：有剩余预算时调用原 DeepSeek Judge 一次
```

- 原生调用失败与原生决策非法分别记录 `NATIVE_CALL_FAILED`、`NATIVE_DECISION_INVALID`，不重试 Jev。
- 反馈非法 JSON、缺少证据、非原文引用、评分点结构错误可以重试。反馈 HTTP/协议失败、超时和最终 Java 校验失败不重试；程序异常直接抛出。
- 重试保留原问题、回答、RAG 和已校验的 Jev 决策，只附加固定失败类别及协议提醒。失败反馈不回灌，不自动修补 JSON、不填补证据、不改变 covered 或分数。
- covered 证据仍须是 MAIN 或 FOLLOW_UP **其中一份**回答中的非空连续原文；不接受拼接、省略、改标点、空白或 Unicode 归一化。未知字段、重复键、尾随 JSON、数字冒充文本也会拒绝。
- 降级输出继续走现有编排层的 Java 校验；降级失败不递归、不制造分数。数据库结构和业务状态机保持原实现。

### 等待与调用预算

以下配置只作用于 Jev 组合策略，不改变默认 DeepSeek Judge：

| 环境变量 | 默认值 | 用途 |
|---|---:|---|
| `JEV_FEEDBACK_MAX_RETRIES` | `1` | 只能取 0 或 1；0 可关闭反馈重试 |
| `JEV_REQUEST_TIMEOUT` | `80s` | 一次 Judge 组合的累计预算，包含原生评分、反馈、重试及降级 |
| `JEV_FEEDBACK_TIMEOUT` | `20s` | 每次反馈的上限 |
| `JEV_FALLBACK_TIMEOUT` | `25s` | 主链路预留的降级预算，也是单次降级的上限 |

原生调用取 `JEV_READ_TIMEOUT`（默认 20s）与扣除降级预留后的余额中较小者。反馈也扣除降级预留；重试前余额不足 2s 则跳过重试。降级使用剩余总预算和降级上限中较小者。总预算配置最多 180s，反馈与降级各最多 60s，总预算必须大于降级预算加 2s。

计时使用单调时钟；每个外部操作在独立 Java 21 虚拟线程执行，超时取消该操作。中断的调用者不再启动反馈或降级。已用本机 HTTP 慢响应验证 JDK 传输读取半截正文时能够取消。取消不能证明供应商停止推理或免于扣费，迟到结果不会被采用，也不能启动新的调用。该预算覆盖 Judge 链路；不包括此前 RAG 或之后数据库事务的时间。

每次组合最多 1 次 Jev、2 次反馈、1 次降级，共 4 次客户端调用；首轮成功仍为 2 次。默认 DeepSeek `max-tokens=4096` 保持原值，新增重试最多增加一次该上限的反馈请求。Jev 独立代理实现保持不变。

### 可观测性

每次输出一条 `judge_execution` JSON 日志：实际返回 provider、降级来源阶段、主链路失败分类、降级失败分类、重试终止原因、各阶段调用次数、各次耗时和总耗时。恢复成功也保留首次失败类别。日志不包含异常对象、密钥、Prompt、用户回答或响应正文。`FALLBACK_RETURNED` 只代表降级返回，之后仍须经过编排层校验。

Jev 成功结果的既有内部 raw JSON 增加 `execution` 元数据；原有 judge / generation / suggestion 存储契约保留。降级保留原 DeepSeek 结果格式，诊断从日志读取。接口没有新增敏感字段，也没有新增数据库列。

## Spring AI 的实际价值与边界

业务只依赖项目自己的 `EmbeddingClient`。生产适配器 `SpringAiEmbeddingClient` 调用 Spring AI `EmbeddingModel.call(EmbeddingRequest)`；`EmbeddingConfiguration` 组装 `OpenAiApi`、`OpenAiEmbeddingModel`、HTTP 连接配置和单次尝试的 RetryTemplate。

| 能力 | 实际负责方 |
|---|---|
| OpenAI 兼容 Embeddings 请求与响应 DTO、`/embeddings` 调用、Bearer、模型选项和响应 metadata 映射 | Spring AI OpenAI 适配层 |
| 服务地址、密钥、模型、dimensions、float 编码、超时、禁止自动重试、null float 拒绝 | 项目配置 Spring AI / RestClient |
| 输入校验、按 batchSize 分批、供应商索引校验及全局顺序恢复 | 项目 `SpringAiEmbeddingClient` |
| 返回模型精确匹配、维度、有限浮点数、结果数量、不可变结果、异常转换、可信 usage 累加 | 项目适配器；usage 识别目前依赖 OpenAI 原生格式 |
| 文档处理状态、MySQL 资格过滤、RAG 证据选择、索引版本和跨存储补偿 | 项目业务代码 |
| Milvus schema / index 校验、COSINE 搜索、版本过滤和删除补偿 | 原生 Milvus SDK 适配器 |

新增离线契约测试将同一业务端口和 `KnowledgeRetrievalService` 分别接到两个 Mock OpenAI 兼容地址，只改变配置中的地址、模型和 profile。实际运行 Spring AI 的序列化、响应映射及项目适配器，验证分批、乱序恢复、usage、检索版本传递；模型漂移、维度错误、异常响应在向量检索前被拒绝。**这证明当前 OpenAI 兼容协议内的切换能力，不证明任意供应商或非 OpenAI 协议可以只改配置。**

当前模型为 `qwen3.7-text-embedding`、1024 维，profile 为 `qwen3.7-text-embedding-1024-dense-v1`。更换模型即使维度相同，也不能假设向量空间兼容；必须分配新 profile 并重新入库。查询会按 profile 搜索，MySQL 再按模型与 profile 检查资格。profile 是人工配置，系统不能自动识别“模型换了但 profile 没换”的语义漂移。

维度变化还会使既有 Milvus collection 的 schema 校验失败，需要另行规划 collection 和重建；系统不会自动删除或迁移索引。空的新 profile 检索不到旧数据是预期边界。保留现有 Embedding 生产代码，没有为扩大 Spring AI 使用范围迁移 DeepSeek、Milvus 或 RAG。

## 已验证结果与限制（2026-10-15）

历史 [E06 原始评测](evaluation/PHASE_5_EPOCH_06_JUDGE_VALIDATION_REPORT.md)为 Jev 原生 38/38、组合 33/38（86.84%）、独立 DeepSeek baseline 38/38。5 次组合拒绝均来自反馈；历史真实降级为 **0**，质量门禁仍为 FAIL。

新增回放测试读取冻结的 38 例原始记录，所有 HTTP 均被 Mock 拦截。下面“混合有效”包含使用独立 baseline 历史响应模拟的降级，不能视作真实降级成功：

| 离线场景 | Jev + 反馈有效 | 混合有效 | 降级次数 | Jev / 反馈 / 降级调用 | 合计 |
|---|---:|---:|---:|---:|---:|
| 关闭反馈重试，原响应 | 33/38 | 38/38 | 5 | 38 / 38 / 5 | 81 |
| 开启重试，第二次仍为相同失败原响应 | 33/38 | 38/38 | 5 | 38 / 43 / 5 | 86 |
| 开启重试，第二次为独立合规合成 Mock | 38/38 | 38/38 | 0 | 38 / 43 / 0 | 81 |

第三行仅证明恢复路径和结构校验。合成引用使用真实原文子串，但不证明该子串语义上足够支持评分点；Java 的逐字子串校验也不能代替人工语义审核。真实恢复率、文本忠实度改善、真实降级可用性、上线后的延迟与费用均**未验证**。

历史可计算指标：38 次 Jev 的 HTTP 耗时合计 35,115.881ms、38 次反馈 90,941.259ms，串行组合均值约 3,317ms（包含失败例，仅 HTTP 耗时之和，不是端到端延迟）；独立 baseline 均值约 3,789ms。前者的 Jev input/output tokens 为 273,781 / 10,301，DeepSeek 反馈 prompt/completion tokens 为 134,881 / 12,841；baseline 为 140,553 / 25,169。服务商没有实际扣费金额，不能据此声称省钱或比较真实单价。

在本回放的五例持续失败场景，调用数量比关闭重试增加 5/81（6.17%）；恢复场景则用 5 次反馈替换 5 次完整评价降级，调用总数相同。默认 4096 输出上限下，这五次新增反馈最多增加 20,480 个输出 token 的额度，实际输入/输出、缓存命中和金额未知。每次新增反馈等待上限 20s，同时受 80s 组合总预算约束；没有用 Mock 的毫秒数推断真实模型性能。

### 自动化验证

- 修改前定向基线：343 tests，0 failures，0 errors，0 skipped。
- 扩展定向回归：698 tests，0 failures，0 errors，0 skipped。
- 完整 Maven 回归：1661 tests，0 failures，0 errors，19 skipped。跳过项为显式保护的真实服务 / AI baseline 测试；不是实际模型集成通过。
- 包含真实 MySQL **`ai_interview_test`** 的 Mapper、事务及并发回归，以及全业务链路的 Mock 外部端口测试。本次没有操作生产库或真实 Milvus，没有调用收费模型。

执行过程中的失败也保留在 `target/value-repair/`：首次动态 attach 被沙箱阻断后中止，未形成完整统计；加入 premain 后的沙箱基线为 343 tests / 7 failures / 29 errors / 0 skipped，原因是本机回环被拒绝，允许回环后同批 343 项全部通过。首轮新增测试为 409 tests / 5 failures / 0 errors / 0 skipped，五项均为新测试错误地将带 usage 的原始 JSON 与既有 Java record 序列化结果直接比较；改为比较完整类型化 JevResponse，并保留五维分数、covered 和追问建议不变的断言，后续定向与完整回归通过。

离线专项可用 `Jev*Test,JudgeConfigurationTest,DeepSeekEvaluationClientTest,Embedding*Test,SpringAiEmbedding*Test` 作为 Maven `-Dtest` 选择器。完整回归命令：

```powershell
.\mvnw.cmd -B -ntp test
```

执行前清除所有 `RUN_REAL_*` 与 `MILVUS_ENABLED`，保留 test profile 的服务隔离配置，并为 Maven 进程提供测试库凭据。受限 Windows 环境还需要本机回环权限；本次运行额外使用 Mockito/Byte Buddy premain，避免沙箱阻断动态 attach，未修改项目依赖或 POM。日志与机器可读的三组回放摘要位于忽略的 `target/value-repair/`。

### 本地变更文件清单（尚未合入 main）

| 状态 | 文件 | 用途 |
|---|---|---|
| 修改 | `../src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java` | 固定原生决策、反馈重试、阶段降级、严格 JSON 和安全日志 |
| 修改 | `../src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevProperties.java`、`../src/main/resources/application.yaml` | 重试及时间预算配置与启动校验 |
| 新增 | `../src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevStageException.java` | 固定失败类别与可重试标记 |
| 新增 | `../src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevRequestBudget.java` | 单调累计预算、超时取消和中断传播 |
| 新增 | `../src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevEvaluationTrace.java` | 不含正文的阶段、调用次数及耗时记录 |
| 新增 | `../src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevFeedbackReliabilityTest.java` | 五例恢复/拒绝、固定决策、超时、配置关闭及脱敏 |
| 新增 | `../src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevE06ReliabilityReplayTest.java` | 38 例三种离线场景及调用指标摘要 |
| 新增 | `../src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevRequestBudgetTest.java` | 累计预算、迟到结果与本机 JDK 慢响应取消 |
| 新增 | `../src/test/java/com/kun/aiinterview/knowledge/embedding/springai/EmbeddingProviderSwitchContractTest.java` | 两个兼容服务配置切换及不兼容输出拒绝 |
| 修改 | `../src/test/java/com/kun/aiinterview/interview/evaluation/judge/JudgeConfigurationTest.java`、`../src/test/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevFeedbackPromptHardeningTest.java` | 预算绑定/非法配置测试；原有严格拒绝测试纳入有限重试次数 |
| 记录 | 本专项文档；项目主页引用此报告 | 历史实验与本地验证边界（不代表代码已发布） |

## 两段面试讲解（应明确区分本地已验证与 GitHub 已发布能力）

**Spring AI：**“我用 Spring AI 收敛 OpenAI 兼容 Embeddings 的协议和响应映射，业务层仍只依赖自己的 EmbeddingClient。分批、乱序索引恢复、模型与维度校验、版本和异常处理属于项目适配器。两个离线兼容服务的契约测试证明，同协议内切换配置不需要改检索业务；换模型仍须更新 profile 和重建向量。我保留原生 Milvus 适配器，因为现有 schema 和补偿边界已经稳定，迁移收益不足。”

**Jev：**“我通过 JudgeProvider 接入 Jev，让它提供结构化评分，DeepSeek 负责自然语言反馈，Java 校验分数、计算总分并决定追问。E06 原生评分 38 次都通过，但反馈导致组合只有 33 次有效，所以我把失败按阶段分类，只对反馈协议问题限重试一次，并给原生、反馈和降级设置共享时间预算。离线测试证明不重复 Jev、不放宽原文证据标准，并能在合规反馈到来时恢复；真实成功率和费用收益仍需后续获准的模型评测验证，默认策略保持 DeepSeek。”