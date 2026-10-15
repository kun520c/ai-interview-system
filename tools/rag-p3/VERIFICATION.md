# P3离线验证记录

日期：2026-10-13（Asia/Shanghai）。`P3_ADMISSION_PREP=READY`。

| 运行 | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| 首轮Java Unit/Mock | 23 | 0 | 0 | 0 |
| 首轮Python Unit/Mock | 34 | 0 | 0 | 0 |
| 首轮PowerShell入口 | 5 | 0 | 0 | 0 |
| 最终Java（补查组合预算与SDK一次尝试） | 25 | 0 | 0 | 0 |
| 最终Python（补查标签复算与失败已知费用） | 36 | 0 | 0 | 0 |
| 最终PowerShell入口 | 5 | 0 | 0 | 0 |

最终合计66项通过。所有模型/资源行为为Mock；Python socket.connect调用0。真实新Embedding/Search/Rerank/DeepSeek/Jev均0，真实数据库/Milvus连接及写入均0。首轮摘要另存`python-tests-initial.json`与`java-tests-initial.json`；最终摘要为`target/rag-p3/offline/python-tests.json`、`java-tests.json`、`powershell-tests.json`，完整日志`run.log`。

Java工具及复用辅助类由现有JDK21.0.11成功编译。编译保留既有Milvus弃用API与P1 unchecked提示，不是执行失败。未运行Maven全量，因为普通Mapper/事务测试会写库，超出本轮0写入授权；未运行P0/P1/P2真实入口或业务E2E。

验证内容包括：原P2真实响应/SHA映射；旧四策略计数；人工/AI分母区分；Q08排除、Q02部分标签不升级；阈值边界与无Rank3补齐；新题6/6及文档引用/题面SHA；规则/题集/冻结SHA被改动拒绝；12次预算、intent先于调用、失败/超时无重试、重复锁拒绝；MySQL服务器READ ONLY及固定SELECT/rollback；SDK失败callable只执行一次；冻结资源后检失败；usage缺失/未知费用；新报告标签路径、请求/响应防篡改复算；AI建议不能通过正式人工验收；高分Hard Negative能触发冻结规则FAIL。

当前冻结清单具体包含**876份历史/源文件**，测试后全数SHA一致，包括P0/P1/P2原报告、响应、执行锁及原始数据。21份源文档SHA也与冻结P1合同一致。新题真实run-once目录尚未创建；旧锁原样保留。

### 已完成与未完成

- 已设计：12道独立新题、检索前机制覆盖审查、固定0.50的0～2条规则及验收协议。
- 已编码并编译：独立Java只读检索与Python/PowerShell入口；原生产文件未修改。
- 已验证：66项离线Unit/Mock/入口测试；16题真实旧结果的离线回放。
- 未验证：新12题的真实检索、分数分布、实际费用/延迟、人工相关性与充分性；现有服务当前在线状态也未在本轮探测。
- 未执行：Git stage、commit、push、生产默认接入、数据库/向量修改。

### 审查来源与外部参考边界

覆盖核查是AI读取冻结文档后的预检，独立于新检索输出，不是独立人工审查。负例的外部官方资料仅用于确认问题机制成立，绝不写入冻结知识库或加入模型候选。

N03冻结审查里保留的Oracle JDK21 ZGC链接当前重定向到JDK26首页，不能把该返回页当作Java21机制正文证据；OpenJDK JEP页本轮读取遇到403。ZGC彩色指针/load barrier这一基础机制可参考[OpenJDK作者介绍](https://cr.openjdk.org/~pliden/slides/ZGC-FOSDEM-2018.pdf)，不能把较新JDK默认模式套到Java21。这个外部链接问题未改变题面、规则、冻结SHA或“当前21份文档只覆盖G1”的语料缺口判断；用户仍须独立确认新题覆盖分类。

N05响应式事务上下文也通过[Spring Framework 6.2官方说明](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-decl-explained.html)核对。其余机制参考链接保留在coverage-review；缺少关键词不是覆盖缺口的唯一证明。

HTML已经生成，机器入口和标签复算路径已离线验证；未声称浏览器视觉验收。

建议下一步：先阅读新题与覆盖审查，再在本机一次性授权ExecuteOnce。新题结果若失败，只按已锁协议记录失败，不调整同批阈值后宣称通过；正式接入Java仍需要单独的小增量审查。
