# AI 模拟面试系统：新对话首读

> **2026-11-16 收口更新：**下文是 11-15 的原始时点快照，关于未提交候选、部署和备份的当时判断不代表当前状态；先读[后续部署与冻结记录](2026-11-16-本机部署收口与工程档案.md#最终交付冻结与恢复演练)，再按需查阅本历史资料。原文保留用于追溯。

核查日期：2026-11-15（Asia/Shanghai）。请以本机工作区、[完整交接](HANDOFF_TO_CHATGPT_PHASE2.md)、[证据索引](HANDOFF_SOURCE_INDEX.md)和之后重新核实的源码/Git 状态为准。本文是入口，不替代完整材料，也不授权开发、提交或部署。

**证据标记：**`VERIFIED_SOURCE` = 当前源码/Git 核对；`VERIFIED_TEST` = 有历史执行证据，本轮未重跑；`REPORTED` = 历史报告或用户陈述；`DESIGN_ONLY` = 设计或实验、非正式路径；`UNVERIFIED` = 证据不足；`NO_GO` = 未通过相应准入。

## 项目和当前状态

- **VERIFIED_SOURCE：**面向 Java 后端实习和校招的单体模拟面试项目，提供认证、固定题库随机组卷、答题与追问、RAG 辅助评分、综合报告、历史和弱项。主问题固定题库出题；LLM 提建议，Java 负责权限、分数校验、追问、状态和事务。
- **VERIFIED_SOURCE：**本地 `main` / HEAD `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`；本地 `origin/main` 和本次 GitHub 连接器读取的远端 `main` 同 SHA。交接前有 29 个已跟踪文件修改、277 个未跟踪文件，暂存区空；三份交接文件为本次新增。数字会随工作区变化。M1-B～E 的 RocketMQ、报告和前端改动在**本地未提交工作区**，不是远端 main 已含该功能。外层仓库忽略一个独立嵌套仓库，不要清理或误改。
- **VERIFIED_SOURCE：**当前代码的报告异步开关默认关闭，关闭时没有旧同步生成回退。`README.md` 和 `AGENTS.md` 中关于“未接入 MQ”或较早阶段的描述有历史滞后。本次只读检查观察到 15174、18482、3308、19532 的回环监听；监听不证明服务健康或运行当前源码，数据库和 MQ 实际可用性 **UNVERIFIED**。历史隔离演示不是生产部署。

## 架构和最新阶段

- **VERIFIED_SOURCE：**Java 21、Spring Boot 3.5.16、MyBatis 3.0.5、MySQL、Spring Security/JWT、Spring AI 1.1.8 Embedding、Milvus SDK 2.6.20、DeepSeek `deepseek-flash`；前端 Vue 3 + TypeScript；本地未提交报告实现使用 RocketMQ client 5.3.2。
- **VERIFIED_SOURCE：**`InterviewController` → 会话/题目快照 → 答案与 `EvaluationOrchestrationService` → Embedding/Milvus 检索并由 MySQL 过滤 → DeepSeek → Java 校验、计算、`FollowUpPolicy` → 每道 MAIN 一份 FINAL。无追问时主答直接 FINAL，有追问时主答 INITIAL、追答综合 FINAL；报告和弱项只使用合法 FINAL。
- **VERIFIED_SOURCE：**报告 POST 在短事务内将 Session 置 GENERATING 并写单表任务/Outbox；提交后有界提示唤醒，500 ms 扫描兜底；租约与消费者 CAS/固定 executionVersion 防旧执行者写入。模型在事务外；Report、Weakness、Session READY、Task SUCCEEDED 在最终短事务内一起提交。MQ 消息仅含稳定 ID；不保证分布式原子、消息零丢失或外部模型恰好一次。
- **VERIFIED_SOURCE + VERIFIED_TEST：**最新 M1-E 保留 P1 提交后唤醒和 P3 生成期间展示**已保存的逐题结果**；1 秒报告轮询在最新收口中合并同一会话的手动/定时在途 GET。P2 提前发报告请求、精简 Prompt、Java 报告替代及 SSE 均未成为正式默认路径。

## 已验证成果与数字边界

- **VERIFIED_TEST，本次核对原日志：**M1-E 历史普通后端 **1947 项：1913 通过、0 失败、0 错误、34 跳过**；真实 MySQL + RocketMQ 指定专项 **2/2 通过**，生成器为模型桩。M1-E 测量构建前端为 **346 项通过**；后续最终收口构建最新前端为 **349 项通过**，报告页定向 59 项通过，原模型响应的 Mock HTTP 重放 14 项通过。后端全量是收口复用的较早运行，并未在收口时重跑；本交接也未重跑任何功能测试。
- **VERIFIED_TEST：**12 次真实 DeepSeek 报告 HTTP 尝试中 **11 READY、1 FAILED**；失败是列表数量越界，Java 正确拒绝，未写报告和弱项。HTTP 200 和 11 READY 均不等于语义正确或内容发布通过。
- **VERIFIED_TEST，本次复算 CSV：**固定 **1 秒模型桩**、并发 1、每组 8 条，受理事务提交后到报告完成事务提交的中位数 **1.463→1.040 秒**；不是模型提速。真实浏览器仅 **3 对**，首次有用内容中位数 **4.223→0.085 秒**；基线是完整报告，候选是既有逐题结果，不是完整报告加速。真实完整报告时长尚未证明稳定下降。
- **VERIFIED_TEST：**原 M1-C 在 1 秒桩、并发 10 下，同步/异步客户端 READY P95 **1046.5/6967.8 ms**，闭环完成 **7.252/1.489 次/秒**；R2 隔离固定桩调整消费线程 2→4 和扫描 2000→500 ms 后，同协议的异步闭环 **1.775→3.531 次/秒**，仍不能当作相对同步加速或生产容量。POST 受理、数据库 READY、页面 READY、首次有用内容和闭环吞吐必须分别报告。

## 未解决和下一步

1. **NO_GO：**Q1/Q2 暴露评分事实和覆盖错误；v5 Prompt 已撤回，Q2-R2 最新规则候选未完成有效对照。独立人工 gold 和留出评审尚不足。
2. **NO_GO：**综合报告仍可能继承上游事实错误或无依据地推断具体缺点；M1-E 结构越界失败的总体发生率和原因未知。不要放宽校验或将 AI 自审当作人工质量验收。
3. **UNVERIFIED：**消费者在模型返回后崩溃，当前是到期 FAILED、用户显式重试，可能再次付费；JDBC 提交未知、长期积压、多节点重平衡、生产级 Broker/MySQL 故障和告警尚未完整验收。
4. **建议顺序：**先复核 M1-E 安全与体验收口和现有差异，保护未提交文件；再由用户明确指令审查、拆分并规范提交；按精确目标批准准备隔离迁移与真实验收；分别推进评分和报告质量，并按需进行真实并发、部署/回退和源码学习。未获授权不要运行迁移、清库、重启服务、推送或调用收费模型。

## 文件入口和分享

- 完整项目状态、源码调用链、阶段验收与测试表：[HANDOFF_TO_CHATGPT_PHASE2.md](HANDOFF_TO_CHATGPT_PHASE2.md)。
- 每份报告/源码的证据等级、隐私和推荐顺序：[HANDOFF_SOURCE_INDEX.md](HANDOFF_SOURCE_INDEX.md)。
- 项目规则：[AGENTS.md](../../AGENTS.md)；最新报告：[M1-E 主报告与最终收口](m1e-20261115/DECISION_AND_VERIFICATION.md)；评分风险：[Q2 诊断](../evaluation/q2-20261114/DIAGNOSTIC.md)。
- 外部 ChatGPT 不一定能访问本机绝对路径；优先提供三份交接 Markdown，再按索引脱敏补充必要源码或报告。不要打包 `.env`、本地配置、密钥、数据库备份、私有日志、原始用户回答或完整 Prompt。
