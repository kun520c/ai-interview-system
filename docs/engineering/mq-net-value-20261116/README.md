# RocketMQ 工程净收益证据索引

2026-11-16，Asia/Shanghai。结论 **CONDITIONAL GO**：保留现有实现，停止本轮 MQ 功能开发；持久化异步受理与恢复有价值，尚未证明 RocketMQ 相对简单持久异步的独立净收益，不宣称整体吞吐提升。不是生产部署或 AI 内容质量放行。

完整分析仅保存在[正式报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-16-RocketMQ工程净收益审查与后端收口.md)，此目录是数据和审计入口，不重复保存完整报告。

[交互审查视图](/Users/kun/.cursor/projects/Users-kun-Developer-projects-ai-interview-system-ai-interview-system/canvases/rocketmq-net-value-20261116.canvas.tsx)支持切换性能指标及筛选可靠性证据；TypeScript strict/noEmit检查通过，未执行视图浏览器渲染测试。

## 本轮新增证据

- [实验协议](../../../tools/mq-net-value-20261116/PROTOCOL.md)：固定1秒模型桩、实际模型上限 K=4、客户端并发1/4/8；每侧每层8测量+2预热。已有专用 MySQL 3317/3318、MQ 19876/20911；不访问 evaluation、不迁移、不删除旧数据。背景数据并不完全相同，Java 无 CPU 独占，只作有限对照。
- [性能汇总](performance-summary.json)：6组，均值/中位数/最小/最大；无新P95。包含服务端入口至最终事务提交、客户端POST、观察READY、闭环完成速率和资源采样。
- [48条逐请求数据](performance-requests.csv)：48/48成功、另12/12预热成功；B受理并非报告完成。客户端以200ms轮询，生产页面为1秒，两者不混用。
- [30对完整请求散列](request-pairs.json)：含预热，所有A/B配对请求一致；两侧实际模型峰值c1=1、c4/c8=4。60次桩调用，新增真实DeepSeek/Embedding/Milvus调用均为0，新增外部Token及费用为0。
- [13行可靠性矩阵](reliability-matrix.csv)及[JSON](reliability-matrix.json)：逐项区分当前候选已有通过、历史进程故障、Mock/桩与未验证；列出责任机制、原件路径和边界。本轮只新增正常链路，没有重新注入历史kill。
- [MQ资源快照摘要](resource-summary.json)：每容器5点。演示Broker/NameServer中位465.3/188.2MiB，非峰值；应用RSS额外约59～61MiB，c4/c8各仅1点；不组合为严格整机增量。Outbox/扫描/运维取舍详见正式报告。
- [75份预实验原件索引](pilot-evidence-index.json)：私有原件绝对路径、字节数及SHA256；包含trace、客户端响应、模型桩、数据库核对、每波启动和资源记录。解包JAR/编译class不计入75份。
- [补充证据索引](supporting-evidence-index.json)：环境身份、收尾、失败说明、旧报告、最终验证等原件；文件哈希用于发现变化，不代表未来运行环境永久不变。
- [最终保护及交付验证](verification.json)：入场75文件的保全结果、558份代码散列、四档案原内容前缀、未新增提交/暂存、数据数量与资源收尾。离线检查不是新增JUnit或E2E测试。

本轮B30份报告对应30个SUCCEEDED/SENT任务，send_attempts=1，每报告5条弱项且各累计一次；offset30/30、积压及在途0。A/B各保留30个新合成会话及报告；没有清表。四个原先停止的专用容器已恢复停止，原演示服务保留。

## 可复核工具与范围

- [run.py](../../../tools/mq-net-value-20261116/run.py) 是有界实验执行器，依赖已有 M1-C/R2 私有工具、JAR、隔离容器与已审准配置，不是公共检出即可一键运行的生产工具。输出目录必须不存在；本轮已完成，**不要为更好数据直接重跑**。
- [analyze.py](../../../tools/mq-net-value-20261116/analyze.py) 只读原件、离线复算本目录性能文件；会替换其派生JSON/CSV，不接触服务。校验60条桩记录、30对请求、48条成功、事件唯一、模型事务外和K上限。
- [失败与限制原记录](../../../backups/MQ_NET_VALUE_20261116/FAILURES.md)：沙箱Docker访问失败、mqadmin地址错误及其他查询问题保留，均发生在业务测量前；预实验业务失败0，未为追求数值重跑。
- 本轮未运行Maven/JUnit、Vitest、浏览器、全量回归或真实模型；不能把历史测试合并为本轮计数。

## 历史证据及完整性

- [历史源码/验收完整性](historical-integrity.json)：公开候选556/556源码/测试/SQL、MQ候选22/22证据、M1-E770/770原件匹配。当前JAR SHA256 `3346146f49fca7c7c604b2d70c3ebddc134d7d49a97467a07d80fe35c2fa07e0`。
- [本机E2E完整性](demo-evidence-integrity.json)：75份中71匹配；运行日志、演示账号和两份转发令牌文件已变，未读取凭据正文或推断原因。核心验收和停复证据匹配，不声明整个环境逐字节复原。
- [远端Git核验](git-remote.json)：候选分支 `0509cd6729c8dc6e45f35c9aef2b49985c59bf75` 已推送，main仍 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`；未合并。本轮全部文档/工具未提交、未推送。
- [M1-B验收](../2026-11-14-M1B-ROCKETMQ-ASYNC-REPORT-VERIFICATION.md)、[M1-C故障](../2026-11-15-M1C-FAILURE-INJECTION.md)、[五组历史进程故障原件](../m1c-20261114/faults-after-cache-fix.jsonl)。消费者模型返回后崩溃最终FAILED，需要显式重试，不自动保证READY。
- [M1-E决策与验证](../m1e-20261115/DECISION_AND_VERIFICATION.md)、[32条实验](../m1e-20261115/EXPERIMENTS.csv)、[收尾回归](../m1e-20261115/closeout/VERIFICATION.json)。12次真实报告11 READY/1 FAILED；首份内容改善来自展示已保存FINAL，完整报告未普遍加速。
- [RocketMQ历史性能审计](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-RocketMQ性能收益与工程价值审计.md)、[历史公平A/B准入报告](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-RocketMQ公平AB基线验证.md)、[本机真实E2E](/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-二期本机完整演示与真实端到端验收.md)。旧同步/异步实际模型并发10/2，不能全归因MQ；本轮已补共同K=4的最小实验。
- 当前候选已有后端T/F/E/S **1950/0/0/35**（1915执行通过）、真实MySQL **194/0/0/0**及单独回滚 **1/0/0/0**、前端 **349/0/0/0**、真实MQ **2/0/0/0**、唤醒 **9/0/0/0**（5 Unit/Mock、4真实MySQL+MQ替身）。不同运行有重叠，不能相加；35跳过不是外部集成通过。原始失败保留在各原记录。

## 决策、归因及后续边界

数据库/Java提供受理与完成事务、Outbox补发、状态机/CAS/执行版本隔离；MQ提供独立消息存储、分发、重投及消费组。方案C能够在概念上复用前者，并省去Broker/NameServer，但本轮未实现或验证C。当前经典5.3.2 PushConsumer实际为队列分配，1队列不证明同组多实例扩展；核对本地SDK字节码，勿套用其他5.x客户端的消息级负载均衡描述。

部署前仍需精确目标迁移/排空回退、持久启动与配置、备份恢复、资源/日志保留、监控与人工恢复；正式要求进程恢复时仅补当前构建必要kill，不因作品集收口无限追加。AI评分v4、报告v1保持；多轮真实模型、人工审核与技术复核后仍有语义误判，部分候选反向退化已撤回，不能宣称总体内容质量达标。

同步维护：[台账](../IMPROVEMENT_LEDGER.md#p2-mq-net-value-20261116)、[案例](../ENGINEERING_CASEBOOK.md#p2-mq-net-value-20261116)、[简历证据](../RESUME_EVIDENCE_BANK.md#p2-mq-net-value-20261116)、[学习入口](../SOURCE_LEARNING_BACKLOG.md#l-p2-mq-net-value-01)。开发者本人贡献/掌握待确认，本轮不开展教学。

官方机制参考：[消费负载均衡](https://rocketmq.apache.org/docs/featureBehavior/08consumerloadbalance/)、[经典Push消费者](https://rocketmq.apache.org/docs/4.x/consumer/02push/)、[部署架构](https://rocketmq.apache.org/docs/deploymentOperations/01deploy/)。本地实测和具体SDK优先于泛化推论。

**2026-11-16 后续核验：**75 份 pilot 原件散列仍为 75/75。补充证据索引在本轮编辑前为 67/71，编辑后为 63/71；变化的是四份持续追加的工程档案、三份加了时点提示的交接文件和本 README。索引是原时点快照，不覆盖旧散列；后续记录见[部署与冻结入口](../2026-11-16-本机部署收口与工程档案.md#最终交付冻结与恢复演练)。绝对路径、私有索引及被忽略原件仅在本机可完整访问，建议提交批次不包含需另行脱敏的原件索引；Git 检出中的本机证据链接可能不可用，不等于原件已丢失或可公开。
