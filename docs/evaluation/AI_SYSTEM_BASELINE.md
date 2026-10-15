# AI 面试系统真实运行 baseline

2026-10-04；代码基线20a933a及既有未提交Jev工作区；生产业务语义、DB schema、prompt、TopK和切片参数均未改。

| 项目 | 最终判定 | 依据 |
| --- | --- | --- |
| JEV | **FAIL（验收未通过，原因BLOCKED）** | 缺少凭据；0次认证推理。此处FAIL不是模型评分错误结论，Jev效果未知 |
| RAG | **NEEDS_TUNING** | 扩充语料正例全中；原库覆盖20%，扩充后Top5 precision28%、负例错误证据率100% |
| E2E | **FAIL** | 真实DeepSeek流程出现答案schema错误HTTP500和报告数组约束HTTP502；诊断恢复可用但不抵消首次失败 |

Jev协议形式适合有界判断，当前量表和Java边界已接入；**是否适合本项目评分尚无法判断**。没有把离线契约测试或DeepSeek对照当成真实Jev证据。[Jev官方API](https://docs.typesafe.ai/api)。

## 核心指标与观察

| 语料 | 有对应知识正例/25 | Hit@1 | Hit@3 | Hit@5 | MRR | 正例Precision@5 | 负例误证据率 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 原知识库 | 5 | 20.0% | 20.0% | 20.0% | 0.200 | 20.0% | 100.0% |
| 补充语料 | 25 | 100.0% | 100.0% | 100.0% | 1.000 | 28.0% | 100.0% |

两组都固定25正例+5负例、question+scoring points检索。原库1切片，仅5正例有资料；补充语料共7文档8切片。扩充后的Hit@1/3/5=100%来自受控、评测者标注小集，不代表完整生产泛化。主要FP是所有负例（尤其HTTP/3误命中TCP，0.643）及正例Top5跨题片段；原库主要FN是ArrayList/Spring/MySQL/TCP无对应资料，20条。详见[RAG评测](RAG_EVALUATION_REPORT.md)。

TopK=5、无threshold、无领域过滤：候选召回可用，直接把所有eligible候选送入prompt不合理。原有过滤解决状态/版本可信性，不能解决语义相关性。建议后续在固定gold+留出集比较一项阈值或较小TopK，不直接上线本次数据推出的阈值。

chunk策略1200/150/800在一个长Markdown上得到1050/861两个chunk、实际重叠115字符，无空片/超限；暂不改参数，补充代码块与跨边界事实后再评价。

DeepSeek17次独立案例基本区分质量，coverage分支稳定，但部分正确同输入47/41/38有9分波动；无RAG38/35/55，未证明RAG评分收益。真实端到端已完成5个完整session（含重试后READY），优秀均分92.8/95，部分正确32/33.4，较差11；另有1个较差session失败后仅恢复首道追问。[独立评测](JEV_EVALUATION_REPORT.md)、[E2E评测](E2E_EVALUATION_REPORT.md)。

追问大多指向缺失评分点，Java控制最多一次，FINAL/report聚合一致。反馈存在把已回答内容记为缺失、自相矛盾的missingPoints、过度表扬和题库参考答案错误风险。数值顺序合理不等于AI反馈可以直接验收。

**当前适合进入有问题清单的人工评审，不适合给出“已通过AI效果验收”的结论。** Jev真实链路未测、结构稳定性和反馈事实性仍有阻断项。本报告不宣称项目完成。

## 实际测试、真实调用、未测范围

- 真实：MySQL、Spring Boot HTTP、6文档/7chunk上传处理、Embedding、Milvus、60条RAG query、5种实库过滤、21次独立/消融/故障回退DeepSeek、两轮6个Session尝试及报告/恢复。
- Jev：只有无凭据HTTP403连通探测，不是推理；没有任何认证在线score/coverage/confidence/timeout/非法结构验证。Jev+RAG+DeepSeek E2E BLOCKED。
- 补充：138项离线契约/Validator和普通全量回归，包含Mock与真实MySQL；这些证明代码边界，不能证明在线AI质量。
- 未覆盖：MEDIUM/HARD完整题库、真人独立标注集、大规模语料、近阈值负例、持续并发/性能、成本token计费、真实Jev配额与限流、其在线非法返回和实际timeout。

## 验证执行记录

| 执行 | Tests | Failures | Errors | Skipped | 实际含义 |
| --- | --- | --- | --- | --- | --- |
| Inventory 首次启动 | 1 | 0 | 1 | 0 | 配置未装配 Milvus Bean；启动问题，非检索失败 |
| Inventory 明确启用真实依赖后 | 1 | 0 | 0 | 0 | 真实 MySQL / Embedding / Milvus |
| RAG 原知识库（30 query） | 1 | 0 | 0 | 0 | 真实检索；case 质量另计 |
| Pipeline（6 文档、5 过滤情形） | 1 | 0 | 0 | 0 | 真实 HTTP / Embedding / Milvus / MySQL |
| RAG 扩充语料（30 query） | 1 | 0 | 0 | 0 | 真实检索；case 质量另计 |
| Judge（17 case runs + 1 故障 + 3 消融） | 1 | 0 | 0 | 0 | 真实 DeepSeek；Jev 未测 |
| E2E 第一轮 | 1 | 0 | 0 | 0 | 采集成功；1 个真实答案 HTTP 500 |
| E2E 第二轮 | 1 | 0 | 0 | 0 | 采集成功；2 个真实报告 HTTP 502 |
| Recovery 首次采集 | 1 | 0 | 1 | 0 | 测试脚本误用 session.overall_score；已修正为 total_score |
| Recovery 修正后 | 1 | 0 | 0 | 0 | 报告与 FAILED 答案均可恢复；不抵消初次失败 |
| 普通回归首次 | 1412 | 1 | 0 | 19 | 新脚本留下空的DEEPSEEK_ENABLED；隔离测试解析为null，已改为真正删除环境项 |
| 补充离线契约与 Validator | 138 | 0 | 0 | 0 | 见 target/evaluation/focused.log |
| 普通全量回归 | 1412 | 0 | 0 | 19 | 见 target/evaluation/regression.log |

## 本人下一步人工测试

1. 先配置本地Jev凭据，用同一11类输入+重复次数运行；逐例核对实际provider，回退到DeepSeek的case不得判Jev通过。
2. 在2463/2466优秀报告对照原答，圈出“说过但判缺失”及超出评分标准的要求；重点ArrayList、构造器注入、TCP。
3. 在部分正确场景不只重复原答：追问补对一个缺失点，再补全所有点；检验FINAL分数/coverage是否合理上升，report只使用FINAL。
4. 复核完全错误、前后矛盾、跑题和拒答的纠正及追问。复现FAILED答案和report失败后的原requestId重试，确认不重复落库且前端能恢复。
5. 新写未进入本轮语料的口语题干与混淆概念（HashMap/Redis Hash、IoC/AOP、TCP/QUIC、聚簇/覆盖索引），人工标相关chunk；检查Top5中所有片段，不只看Top1。
6. 审核TCP题库参考答案；补充不改变主问题生成规则的事实性知识。已有equals/hashCode标准可参考[Java Object协议](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html)，TCP参考[RFC9293](https://www.rfc-editor.org/rfc/rfc9293.html)。

## 文件与交付状态

新增`src/test/java/com/kun/aiinterview/evaluation/AiSystemBaselineEvaluationTest.java`（双保护真实采集/HTTP工作流）和`BaselineTraceConfiguration.java`（测试专用真实调用观察器）；新增docs/evaluation下运行/数据准备/指标报告脚本、golden data及原始evidence。没有改现有生产Java/YAML/Mapper或已有测试语义；修正仅在本轮新测试脚本内。

编码/编译/真实验证已按上述记录执行；未暂存、未commit、未push。`docs/`当前被本机`.git/info/exclude`忽略，所以报告/脚本在本地可打开，但尚未进入Git跟踪；本轮没有更改该规则。开始时已有工作区改动保留，不能把它们算作本轮新增实现。

附加证据审核核对60次query的vector/chunk/document映射、5份成功report的FINAL均分与真实prompt输入；真实凭据精确匹配扫描未发现泄露。`evidence/manifest.json`保存数据/证据及生产Java SHA-256，便于固定此轮baseline。

原始证据、输入和复现步骤见[README](README.md)。本次知识与合成会话作为复核材料保留，没有清空共享Collection。
