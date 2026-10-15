# E06 feedback 修复选项与决策

本轮为 Commander 授权的离线取证与文字加固。真实模型/Embedding API 0，数据库与Milvus客户端初始化0、写入0；E07未运行、5C NOT_RUN、Phase6 BLOCKED。E06 原严格门禁 FAIL，38案例/114推理/零重试、native38/38、baseline38/38、联合反馈及配对33/38保持不变。

## 故障与手段分别匹配

| 问题 | 已确认边界 | 本轮选择 | 额外代价/残留风险 |
| --- | --- | --- | --- |
| 非法JSON 2/38 | 请求已json_object；严格parser发现坏键/分隔符 | 保持严格解析；文字提示补合法键/冒号/转义；重试只设计 | 不保证生成正确；新增文字token；真实收益未验证 |
| 非连续quote 1、blank 1、null 1 | MAIN/FOLLOW_UP可用，模型未按contract提供 | 一个非空逐字片段、不改空白标点/拼接，保留MAIN已覆盖证据 | 不保证服从；不自动抽取/修复，不改covered |
| baseline确认假遗漏1 + 争议3 | v2已禁止已答机制列遗漏，混合子项仍可能误读 | 先核对两份回答，missing只写具体缺项，correction不重复称尚缺 | 语义需人工审阅；结构validator不会识别全部假遗漏 |
| native数值 | 限定显示兼容E06 native38/38 | 保留已批准算法/模型限定/容差 | 本轮没有数值修复；不作未来100%保证 |
| 生产可用性 | Existing ExternalServiceException→DeepSeekJudgeProvider | 仅Mock回放现有一次fallback | 与原生judge质量分开；5C仍未真实验证 |

## 有限格式重试：设计，未实现、未授权执行

建议若另行审批，只在feedback内层JSON语法或完整schema/ID格式失败后最多1次，沿用原始context/rubric与一次已验证native decision，不重新调用Jev、不重评covered/分数、不转成宽松parser。非连续quote和语义归因不是这个窄结构重试范围；空/null证据若要列入格式重试，也需单独明确审批。不得因结果低分、gold不符、排序失败、语义争议而重试。修复指示需单独版本/hash，原始第一次response/错误/usage/时间永久保留。

定义首次成功率=首次完整验证成功case/计划case；最多一次重试后的成功率=首次或批准的第二次完整成功case/计划case；同时单列retry attempted、salvaged、still failed、未执行case。两个分母与初次失败名单不能合并或隐去，不把HTTP200当成功、不把第二次成功冒作第一次。若两次无效，保留失败并按未来获批产品政策选择现有fallback；质量benchmark路径仍记原始失败，不用fallback补样。

收益只能假设：E06两次syntax故障即2/38（5.26%），第二次若完全合规可挽救0–2例，没有在线证据。以本批分布做非预测性示例：新增2次DS feedback；其既有median≈2403.53ms/P95≈2877.02ms/max≈2979.64ms，每个重试案例串行增加一轮请求时延，故障/超时最坏受原60s readTimeout等影响。格式重试若每38例均需要，最坏增加38个feedback请求，而不是最多2个。native一次、feedback首次+retry+fallback的最坏产品分支可能累计4次推理。

E06 feedback总147722 tokens/38≈3887.42 tokens/请求，均值仅量级参考，重试附加指令/新增system使真实输入和缓存用量不同；output仍受4096原限制。金额NOT_AVAILABLE，不按未确认单价猜成本。一次推理重试会额外计费，不能靠业务幂等撤销供应商费用。

幂等边界：统一在验证成功后由原Java流程写最终业务结果，重试不能重复触发session/weakness/report/trace写入；保持原answer ownership/CAS/事务。必须审计HTTP客户端和供应商是否自行重试、避免多层重试放大，同case/purpose/attempt分别落证据；不记录Header或真实凭证。并发重复请求的existing-answer重放不能使外部推理无限重复。本轮没有更改或执行该设计。

## 确定性候选span/ID：下一步可审批的工程提案

可将MAIN与FOLLOW_UP按确定边界索引，每个span记录source、UTF-16 start/end、原文hash；模型选source/span ID，Java从原候选文本定位并验证片段，再验证评分点绑定。空白/中文标点/Unicode不得改写，不能将两处或两份回答拼成一个evidence，更不能从RAG/reference取候选证据。若固定span跨机制不够，模型只可提供同一source的连续start/end，Java逐字核验。

该方案可能减少转述和JSON内引用转义故障，不能保证选中片段足以支持native covered，更不能以合法ID代替语义证据。需要新增协议、schema、Prompt和算法测试、版本冻结，可能增加token并涉及字符边界与隐私日志；超出本轮“只改两个system常量”。未实现、未向现有schema添加字段，等待Commander是否授权。

## 产品fallback与质量门禁

现有JevJudgeProvider catch ExternalServiceException后调用DeepSeekJudgeProvider.evaluate(context,prompt)，一次fallback保留。新29项Mock含五份captured invalid原响应→真实provider代码分支→Mock DeepSeek客户端一次，失败不伪造Jev合法分数。Mock sentinel不是有效业务评价，只证明路由与调用次数；现有47项provider测试还覆盖fallback失败传播/不递归。

这不是5C真实fallback证明，也不能称产品可用率38/38；E06实际fallback0，5C NOT_RUN。native数值38/38、联合judge33/38、baseline结构38/38及baseline语义FAIL分别报告。建议先审核本轮取证与三个争议，再另行授权≤8新epoch canary；任何格式/归因问题停止，不自动完整114次。

**STOP_WAIT_COMMANDER。**
