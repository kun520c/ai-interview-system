# Jev HTTP200 与 HTTP451 历史证据诊断

调查日期：2026-10-14，Asia/Shanghai。范围：Jev 历史调用、客户端、出网路径及最小修复准备。唯一新增文件为本报告；未修改业务代码、配置、代理、系统网络或历史证据，未调用付费模型，未重跑面试、P3/P4/P4.1，未暂存、提交或推送。

## 1. 结论与证据边界

**10月8日真实 Jev HTTP200 已确认，而且当天后续批次曾通过 Java 业务评分；10月13日451是 API 返回的明确区域不可用拒绝。** 两者访问同一 TypeSafe API、同一路径，请求同一 `jev-latest` 别名。10月13日没有返回模型身份或评分响应，不能判断实际模型版本。

已确认的直接阻碍是响应正文中的区域拒绝。尚不能把它精确归因为“中国大陆出口 IP 被限制”，也不能断言“Java 必然直连”或“配置代理即可解决”。两天没有保存公网出口 IP、地理识别结果或响应头；当前代理文件在10月14日更新，不能回填历史网络状态。

有价值的实际差异是：10月8日通过生产 JevClient 直接请求供应商；10月13日通过生产 JevClient → 本地转发门禁 → 另一个 JDK HttpClient 请求供应商。后者还清空子进程环境。双方均未显式指定 Jev 专用 ProxySelector，实际出口仍受当时 JVM 默认代理及系统路由/TUN 影响。

历史成功不代表 Jev 全场 E2E 验收：E06 原生评分校验38/38，通过反馈合并及 Java 评分33/38，严格质量门禁 FAIL；这些是只读合成案例实验，没有创建并持久化真实面试业务数据。最终五题 E2E 的实际评分来自 DeepSeek，Jev451后的真实降级已成功。

## 2. 10月8日成功调用的原始证据

### 2.1 首次200与 clarityScore 偏差

原始运行记录 [PHASE_5B_RUN_EVIDENCE.json](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/evaluation/judge/PHASE_5B_RUN_EVIDENCE.json) 的 `events[0]` 与 [PHASE_5B_JEV_RESULTS.json](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/evaluation/judge/PHASE_5B_JEV_RESULTS.json) 的 `attempts[0].rawResponse` 保存：

| 项目 | 核验结果 |
| --- | --- |
| 时间 | `2026-10-08T02:48:07.523466700Z`，北京时间10:48:07；原记录字段为 `at`，不冒充完整请求起止时间 |
| 案例 | `E02-EXCELLENT` |
| HTTP | 真实供应商调用200，一次；不是 Mock200 |
| 请求 / 返回模型 | `jev-latest` → `jev-1.13.0` |
| 地址 | 历史绑定记录为 `https://api.typesafe.ai`，生产路径 `/v1/systemone`；该首事件自身没有独立 endpoint 字段 |
| 响应 | 五维 Score、四个评分点 Choice、`followUp` Choice；顶层字段 `model / answers / usage` |
| usage / request ID | 首事件只保留 usage 字段名称，没有保留数值；没有 request ID 或响应头，不能补造 |
| Java结果 | `Jev分数或概率分布不一致`；primary未接受，fallback入口被首调用停止观察器拦截，实际DeepSeek请求0 |

数学复核来自原始 answers，而不是仅引用文字报告：

| 维度 | 返回 score（0～4） | 按返回概率计算的期望 | 绝对差 |
| --- | ---: | ---: | ---: |
| correctnessScore | 3.87 | 3.87 | 0 |
| completenessScore | 3.96 | 3.96 | 0 |
| depthScore | 3.82 | 3.82 | 0 |
| clarityScore | 3.56 | 3.55 | 0.01 |
| practiceScore | 3.82 | 3.82 | 0 |

clarity 的概率为 `{0:0, 1:0, 2:0.02, 3:0.41, 4:0.57}`，总和1；期望 `2×0.02 + 3×0.41 + 4×0.57 = 3.55`。历史严格容差0.001，故0.01被拒绝。详见 [FIRST_CALL_PROTOCOL_DIAGNOSTIC](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/evaluation/judge/PHASE_5B_FIRST_CALL_PROTOCOL_DIAGNOSTIC.json)。该 diagnostic 是离线捕获响应复核，不是第二次真实调用。

四个评分点2480～2483都返回 `covered`，`followUp=no`，分布和选择字段存在；完整五维和结构可读取，但这一次没有形成被接受的业务评价。HTTP成功、JSON读取成功与业务校验成功必须分别记录。

### 2.2 同日后续成功，不能遗漏

逐项统计原始运行事件，10月8日有 **80次 Jev POST200，另有1次 POST503**。GET模型列表另计；不以GET200代替评分POST200。

| 批次 | 北京时间 / 运行范围 | Jev POST | 原生校验与业务结果 |
| --- | --- | --- | --- |
| E01 | 10:48:07观测首响应 | 1次200 | clarity偏差拒绝，完整primary0 |
| E02 | 11:57:25～11:57:26首请求 | 1次503 | 没有有效评分响应，立即停止 |
| E03 | 14:25:39～14:25:40首请求 | 1次200 | Jev原生校验通过；DeepSeek反馈模型身份门禁停止，完整primary0 |
| E04 | 15:33:12～15:42:29运行 | 2次200 | 两次Jev+反馈+Java评分通过；第二例独立DeepSeek baseline JSON失败使批次停止 |
| E05 | 16:34:09～16:38:31运行 | 38次200 | 原生33/38；完整primary33/38；5次数值拒绝，严格验收FAIL |
| E06 | 19:37:53～19:42:57运行 | 38次200 | 原生38/38；完整primary33/38；5次反馈/合并拒绝，严格质量FAIL |

E03～E06证据分别为 [E03 RUN_EVIDENCE](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/evaluation/judge/PHASE_5B_EPOCH_03_RUN_EVIDENCE.json)、[E04 RUN_EVIDENCE](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/evaluation/judge/PHASE_5B_EPOCH_04_RUN_EVIDENCE.json)、[E05 RUN_EVIDENCE](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/evaluation/judge/PHASE_5B_EPOCH_05_RUN_EVIDENCE.json)、[E06 RUN_EVIDENCE](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/evaluation/judge/PHASE_5B_EPOCH_06_RUN_EVIDENCE.json)。E02失败证据为 [E02 RUN_EVIDENCE](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/evaluation/judge/PHASE_5B_EPOCH_02_RUN_EVIDENCE.json)。

E05/E06的76份独立原始响应字节文件仍存在。本轮只读复算全部76份 SHA-256，均匹配事件中的 `responseSha256`，模型均为 `jev-1.13.0`。例如：

- [E05首响应](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/phase5-epoch05/http/1.response.txt)：请求08:34:14.194750800Z，响应08:34:15.502974300Z，200；usage为7105 input / 261 output；响应SHA `3b668bf880f8ae0945065a0f48385d7db98d1381a5a1eababea3450dce917f70`。
- [E06首响应](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/phase5-epoch06/http/1-E02-EXCELLENT.response.txt)：请求11:37:57.356301600Z，响应11:37:59.494826400Z，200；usage为7105 input / 262 output；响应SHA `b82283f232e70c6b36e6a6195fe9eef250a2405b4c74b2d49a4896032e38115b`。该例原生通过，随后反馈拒绝，不能把这份200称为完整primary通过。

这些响应不是Mock：观察器调用实际 `exec.execute(req, body)`；运行器调用生产 `JudgeConfiguration.jevClient`，事件记录供应商host/path；E06事件还记录 `externalHttp=true / attempt=1`。控制故障的本地服务分支属于独立CONTROLLED路径，未计入这里的80次。

### 2.3 控制台80次 / Tokens / 消费的交叉核验

用户提供的控制台数据为80次、597485 Tokens、约$0.0242；本轮没有登录控制台或取得账单导出。这是用户提供的线索，不能称为本轮独立核验的账单。

历史200次数 `1+1+2+38+38=80` 与该线索一致。E03～E06共79份200保留 usage，合计568749 input、21369 output、590118 total；相对597485缺7367 Tokens，恰好对应尚未取得usage数值的E01一条，但不能因此认定其具体input/output。按当前公开input单价，79条约$0.023887458；若缺失条也为7105 input / 262 output，合计会约$0.024185868，四舍五入吻合$0.0242。**后一句只是相容性推算，不是恢复出的原始usage或账单。** [官方模型价格](https://docs.typesafe.ai/models)

## 3. 10月13日451的原始证据与发生阶段

定位：`target/final-e2e/attempts/final-e2e-20261013-001/providers/`，操作 `answer-1`，首主答案，未发生Jev重试。

| 项目 | 原始证据 |
| --- | --- |
| 发送 | `2026-10-13T11:34:12.668725400Z`，北京时间19:34:12 |
| 接收 | `2026-10-13T11:34:13.591258100Z`，北京时间19:34:13 |
| 上游endpoint | `https://api.typesafe.ai/v1/systemone` |
| 请求model | 原始JSON为 `jev-latest` |
| HTTP状态 | provider-index记录451，原始正文也含 `status:451` |
| 完整原始正文 | `{"title":"Typesafe is not available in your region.","status":451}` |
| 错误代码 | 没有独立 `code` / `error.code`，只有title与status |
| 评分 / model / usage | 均未返回；index的responseModel为空，usage=null |
| request ID / 响应头 | 没有保存；无法确定供应商内部请求ID或具体CDN/网关层 |
| 请求大小 | 19694 bytes，state为6925个Java字符串字符，10个questions |
| 请求头 | JSON Content-Type及Bearer Authorization存在；门禁验证凭证非空并匹配运行时 `JEV_API_KEY`，随后转发。只核验存在及方案，不输出值 |

原始 [request.json](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/final-e2e/attempts/final-e2e-20261013-001/providers/02-answer-1-jev.request.json)、[response.json](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/final-e2e/attempts/final-e2e-20261013-001/providers/02-answer-1-jev.response.json)、[provider-index](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/final-e2e/attempts/final-e2e-20261013-001/providers/provider-index.json)。本轮重新计算两份文件的SHA，分别匹配：

```text
request:  95dda9d99e984e2366a8ece4ceced4849a4424f3e653622f64ca3a0264dab85d
response: 9aad89d541d3f6208b641cf6811714e94b4e9557cf2c3b74dcdb2d17fbe4b122
```

发生顺序为：Embedding200 → MySQL合格证据检索/Prompt → Jev上游POST451 → 本地门禁原样返回451 → JevClient状态处理抛ExternalServiceException → JevJudgeProvider降级 → DeepSeek200 → Java校验/计分/持久化。**451发生在读取并解析正常JevResponse、数值兼容校验和业务评分之前**，不是clarity容差再次失败。

DeepSeek同operation发送11:34:13.601499400Z、返回11:34:17.286529600Z，实际model `deepseek-flash`，usage3745 prompt / 648 completion / 4393 total。[历史数据库快照](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/final-e2e/attempts/final-e2e-20261013-001/99-final-or-failure.database.json) 的answer100为EVALUATED，evaluation50为 `llm_model=deepseek-flash / total_score=7 / INITIAL / FOLLOW_UP`，证明降级结果真正进入业务。

首次attempt的BLOCKED是后续运行器日期序列化比对问题，不能把整个BLOCKED归给Jev451；见 [repair diagnosis](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/final-e2e/repair-20261013/diagnosis.json)。本轮未重做该修复或E2E；已有 [最终只读验收状态](E:/learning/IDEA_PROJECTJ/ai-interview-system/target/final-e2e/second-repair-20261014/verification-status.json) 为PASS、`NOT_NEEDED_ALL_BUSINESS_COMPLETE`、newModelCalls=0。

## 4. 成功与失败完整对比

| 对比项 | 10月8日真实成功批次 | 10月13日首次E2E的Jev失败 | 判定 |
| --- | --- | --- | --- |
| 日期与环境 | JDK21.0.11；只读合成benchmark，E01～E06 | JDK21.0.11；真实五题业务，attempt001首题 | 用途不同，已证实 |
| API提供商 | TypeSafe，api.typesafe.ai | 同一提供商/域名 | 一致 |
| 上游Base URL / 路径 | https://api.typesafe.ai + POST /v1/systemone | 完全相同 | 一致，不是OpenAI chat协议 |
| 应用内Base URL | 生产client直接使用供应商地址 | 临时 `http://127.0.0.1:<动态端口>/jev`，门禁转发到上述HTTPS地址 | 出网链路不同，已证实 |
| 请求 / 实际model | jev-latest → jev-1.13.0（80次200） | 请求jev-latest，451无实际model | 请求一致；失败实际版本UNKNOWN |
| Key来源/标识 | Windows User-scope JEV_API_KEY，经子进程环境绑定 ai.judge.jev.api-key | Process优先、缺失再读User-scope JEV_API_KEY，注入相同配置标识 | 来源选择顺序不同；无历史key身份记录，不证明两天值相同 |
| 鉴权/Content-Type | 生产配置Bearer；application/json | 门禁验证Bearer存在、匹配后转发；application/json | 方案一致；451不证明服务端已认可Key |
| Spring Profile | local；ai_interview / knowledge_chunk_vectors_local，历史只读环境 | test-real；ai_interview_test / knowledge_chunk_vectors_test | 已证实，数据库隔离差异本身不能解释区域拒绝 |
| HTTP客户端 | RestClient → JdkClientHttpRequestFactory → java.net.http.HttpClient | 同一生产client访问loopback；门禁另用JDK HttpClient外发 | 上游发送者不同；均JDK实现 |
| 超时 | connect3s / read20s | 生产read150s；门禁connect3s、单请求65s | 已证实；451约0.923s返回，非超时 |
| 环境继承 | E03脚本保留其他进程环境；E06明确清除JVM options后启动，加载User-scope模型凭证 | ProcessStartInfo.Environment.Clear，仅白名单系统项、JDK和必要凭证；无HTTP_PROXY/JAVA_TOOL_OPTIONS继承 | 已证实；不能等同“代理变化是根因” |
| 显式Jev代理 | 未配置 | 生产client和上游transport均未配置 | 一致；实际默认代理/TUN状态未知 |
| 出口IP/地理位置 | UNKNOWN，没有同期记录 | UNKNOWN，没有同期记录 | 禁止按所在地或浏览器访问推断国家 |
| JSON顶层 | model / state / questions | 相同 | 协议一致 |
| 评分/覆盖/追问请求 | 五维score，criteria为5级数组；point_<id> covered/uncovered；MAIN followUp yes/no | 同样结构，四个点2476～2479；10 questions | 业务题目/ID不同，未见schema错误证据 |
| Prompt/内容 | 冻結ArrayList/LinkedList等案例；E06示例state7025字符；反馈SYSTEM历经v1/v2 | 首题接口/抽象类低信息答案，state6925字符；现有v3 | 内容不同；反馈SYSTEM不是Jev HTTP协议 |
| chat参数 | Jev没有messages、max_tokens、response_format、temperature | 同样没有 | DeepSeek参数不能套用到Jev |
| HTTP | 80次200；另1次503 | 一次451 | 原始证据确认 |
| 响应结构 | model / answers / usage；五维和choice分布 | title / status；无answers/model/usage | 明确不同 |
| request ID | 所查响应没有ID，headers未保存 | 没有ID，headers未保存 | 服务商侧需按时间查日志 |
| Java业务解析 | E01精度拒绝；E04两例成功；E06 native38、完整33 | 正常响应解析未开始，直接fallback | API、schema、业务层须分开 |
| 完整业务E2E | 未执行/未持久化Jev面试闭环 | 首题DeepSeek降级持久化，随后已完成整场；无全场Jev评分 | 不把DeepSeek成功归属Jev |

## 5. 当前Jev客户端与已存在的精度兼容

当前 [JudgeConfiguration](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/judge/JudgeConfiguration.java:44) 在 `deepseek.enabled=true` 且显式选择 `ai.judge.provider=jev` 时装配Jev。创建专属JDK HttpClient，设置连接超时，交给JdkClientHttpRequestFactory设置readTimeout；克隆RestClient.Builder并设置Base URL和Bearer。配置默认provider为DeepSeek，Jev默认model为jev-latest、connect3s/read20s/minConfidence0。[application.yaml](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/resources/application.yaml:65)

[JevProperties](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevProperties.java) 没有proxy字段；API Key不进入toString。`JudgeConfiguration` 在当前HEAD和工作区均没有显式代理代码。本轮读取的项目相关实现未发现 `ProxySelector.setDefault` 或 `java.net.useSystemProxies` 设置。

[JevClient](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevClient.java:32) 单次POST `/v1/systemone`，没有应用级HTTP重试。HTTP错误统一安全抛出ExternalServiceException，生产代码不保留供应商错误正文；本次451原文来自受控实验门禁。成功正文用专属ObjectMapper严格读取，检查模型和完整questions键集合；[JevResponse](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevResponse.java) 只映射model/answers，生产结果不会保留usage或HTTP request ID。

[JevJudgeProvider](E:/learning/IDEA_PROJECTJ/ai-interview-system/src/main/java/com/kun/aiinterview/interview/evaluation/judge/jev/JevJudgeProvider.java:95) 验证五维、rubric/legend、choice、probabilities、confidence后，将0～4分乘5并HALF_UP为0～20。再调用DeepSeek生成文字反馈和候选追问，合并后交Java validator；任一ExternalServiceException走DeepSeek judge fallback。最终totalScore和状态转移仍由Java控制。

当前已有精度兼容：保留严格sum容差0.0001及期望容差0.001；仅当返回model恰为 `jev-1.13.0`，Score展示值满足cent或相邻binary64条件，才进一步验证各展示值±0.005区间能否**共同形成总概率1与一致期望**。不篡改原始响应、不对任意模型放宽。这一兼容已存在，E06真实native38/38支持其有效性；不能重复开发，也与HTTP451无关。当前未提交修改的发布状态不能冒充已部署。

## 6. 代理路径：只读检查结果

### 6.1 当前快照，不能回填历史

本轮安全读取环境变量、Windows Internet Settings及现有Clash配置；只输出本地端口、开关和是否存在，不读取或输出节点订阅、密码或鉴权值。

| 来源 | 当前观测 |
| --- | --- |
| Windows User环境 | HTTP_PROXY、HTTPS_PROXY指向 `http://127.0.0.1:7897`；ALL_PROXY指向 `socks5://127.0.0.1:7899` |
| 当前Codex子进程环境 | HTTP_PROXY/HTTPS_PROXY/ALL_PROXY均不存在；JEV_API_KEY仅User-scope存在，未显示值 |
| JVM选项环境 | 所查Process/User/Machine的JAVA_TOOL_OPTIONS、JDK_JAVA_OPTIONS、_JAVA_OPTIONS均不存在 |
| WinINET注册表 | ProxyEnable=1；ProxyServer=`127.0.0.1:7897`；AutoConfigURL不存在 |
| Clash Verge设置文件 | enable_system_proxy=true、enable_tun_mode=true |
| Clash生成文件 | mode=global；mixed-port7897、socks-port7898、HTTP port7899；clash-verge.yaml的tun.enable=true / auto-route=true |
| 历史可靠性 | verge.yaml、clash-verge.yaml、config.yaml在10月14日更新；不证明10月8日或13日同样设置或运行状态 |

文件位置为 [verge.yaml](C:/Users/USER/AppData/Roaming/io.github.clash-verge-rev.clash-verge-rev/verge.yaml:18)、[clash-verge.yaml](C:/Users/USER/AppData/Roaming/io.github.clash-verge-rev.clash-verge-rev/clash-verge.yaml:3)、[config.yaml](C:/Users/USER/AppData/Roaming/io.github.clash-verge-rev.clash-verge-rev/config.yaml:3)。ALL_PROXY声明socks5端口7899，而当前生成文件的socks-port为7898，说明持久环境与文件并非完全一致；本轮不验证监听或修正。Jev独立HTTP代理若获准，应使用已核实协议的HTTP/mixed入口，不能盲用ALL_PROXY。

`netsh winhttp show proxy` 在执行环境提示该命令不可用；网络适配器CIM读取不可访问，无法核验TUN实际接口/路由。WinHttpSettings注册表二进制存在但未解码，不冒充有效WinHTTP配置。这些是调查环境限制，不是项目代码失败；本轮没有开启端口探测或出口IP查询。

### 6.2 浏览器和Java是否可能不同出口

**可能。** 浏览器可能通过WinINET代理、扩展或自身设置访问console.typesafe.ai；Java的默认ProxySelector和系统代理启用规则不同。Java21的 `java.net.useSystemProxies` 默认false，只在启动时读取；未调用HttpClient.Builder.proxy时仍会使用默认ProxySelector，因此“builder没有proxy”不等于“实际必定无代理”。HTTP_PROXY/HTTPS_PROXY环境存在也不能作为该JDK客户端已使用代理的证明。[Java21网络属性](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/doc-files/net-properties.html)、[HttpClient.Builder.proxy](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.Builder.html)

此外TUN可在操作系统路由层接管流量，即使Java不使用HTTP代理也可能走代理出口；文件true不证明当时TUN运行。console.typesafe.ai与api.typesafe.ai是不同host，控制台登录和充值成功不证明API区域准入、出网路径或账户授权相同。[官方站点的控制台入口](https://typesafe.ai/)

### 6.3 10月13日的特殊门禁路径

[FinalE2eRunner.boot](E:/learning/IDEA_PROJECTJ/ai-interview-system/tools/final-e2e/FinalE2eRunner.java:203) 将生产Jev Base URL改为loopback；[FinalE2eGate.forward](E:/learning/IDEA_PROJECTJ/ai-interview-system/tools/final-e2e/FinalE2eGate.java:82) 把已验证请求转发到固定官方HTTPS地址；[RagE2eModelGate.realTransport](E:/learning/IDEA_PROJECTJ/ai-interview-system/tools/rag-e2e/RagE2eModelGate.java:24) 使用独立JDK HttpClient，不指定proxy，禁止redirect，单次65s请求。[run.ps1](E:/learning/IDEA_PROJECTJ/ai-interview-system/tools/final-e2e/run.ps1:32) 清空环境，仅注入白名单与必要凭证。

因此仅给生产Jev HttpClient加proxy，并不能改变这个历史门禁的上游请求路径，还可能误把loopback送进代理。任何未来最小诊断要让Jev直接使用获准的上游客户端，或在新诊断门禁内仅为Jev上游创建独立transport。不得重跑已消费的最终E2E入口或改变已有锁。

## 7. 根因与按证据排序的待验证原因

| 排序 | 结论/原因 | 证据强度与下一项需要的证据 |
| --- | --- | --- |
| 已证实直接原因 | 对官方API的请求返回区域不可用451 | 原始正文、status、endpoint、时间和SHA均可核验；无正常评分结果 |
| 1 | 两次实际出口或出口地理识别不同 | 出网发送者/环境继承不同且本机有代理/TUN配置；缺两天出口记录与供应商识别日志，仍是假设 |
| 2 | 服务商API区域策略、网关地理数据库或对该请求的判定变化 | 同一端点过去200、现在明确区域拒绝；没有同期政策变更公告，不能确认变更或误判 |
| 3 | Key/账户准入不同，或账户级地区判定 | 两次凭证加载顺序不同，未保存身份标识；原始错误没有account/code字段，不能证明Key变化或账户封禁 |
| 较低 | 请求schema、模型名、余额、限流、超时或Java精度错误 | 路径/别名/结构与真实成功一致；原文不报告这些错误，451在解析前且快速返回，无支持证据 |

本轮查阅官方 [API reference](https://docs.typesafe.ai/api)、[Models](https://docs.typesafe.ai/models)、[Legal](https://docs.typesafe.ai/legal)、[Master Customer Agreement](https://typesafe.ai/legal/mca) 与 [AUP](https://typesafe.ai/legal/acceptable-use-policy)，未取得可用于确认“中国大陆专属451限制”的国家名单或451地区映射说明。检索无结果不证明地区限制不存在。MCA 2.3(g)涉及不得绕过访问限制，16.12涉及出口约束；这里只据此将修复限定为服务商允许的访问路径，不作国家准入的法律推断。

精确到国家/IP/账户规则的根因，需要服务商按 `2026-10-13T11:34:12.668725400Z`、API host/path、请求model和错误正文查记录，说明识别地区、拒绝规则、允许地区及账户状态。可以另附10月8日成功时间作为对照，无需发送密钥、完整答案或JWT。**本轮没有联系服务商或向任何人发送消息。**

## 8. 已通过与尚未通过的能力

| 能力层 | 状态 | 实际证据及限制 |
| --- | --- | --- |
| API请求成功 | 历史YES；当前可用性未恢复验证 | 10月8日80次200；10月13日一次451 |
| 正常JSON与typed response解析 | 历史YES | 五维和choice可读取；E06外层38/38，非仅Mock |
| 原生数值/概率/rubric校验 | 历史YES，限定版本 | E01失败、E05五例失败；现有兼容后E06 native38/38 |
| 五维业务评分、评分点覆盖、追问建议、Java裁决 | PARTIAL | E04两例、E05/E06各33例完整通过；严格全样本验收未通过 |
| 代表性业务成功例 | CONFIRMED | E06 E02-PARTIAL：五维17/11/10/14/2，Java总54；covered2480/2481，quote通过；INITIAL/FOLLOW_UP targets2482/2483，有合法候选追问 |
| 全部文字反馈/证据schema | 尚未全通过 | E06有2例非法反馈JSON，1例非连续quote，2例MAIN covered quote空/null；Jev native成功并不保证组合结果成功 |
| 语义质量 | 严格门禁FAIL，不能称校准完成 | E05硬反馈矛盾、E06反馈/语义争议；合成benchmark也不能外推实际人群准确率 |
| Jev评价持久化与完整五题闭环 | 尚未验证 | 10月8日实验零业务写入；最终面试实际DeepSeek评分 |
| 真实Jev451→DeepSeek降级 | YES | 同operation451→200及真实数据库EVALUATED/INITIAL/FOLLOW_UP |
| 当前代理或当前地区可用性 | UNKNOWN | 本轮没有网络验证，历史出口缺失 |

代表性记录见 [E06 JEV_RESULTS](E:/learning/IDEA_PROJECTJ/ai-interview-system/docs/evaluation/judge/PHASE_5B_EPOCH_06_JEV_RESULTS.json) 的 `cases[caseId=E02-PARTIAL]`，包括validatedSuggestion、javaScore、javaDecision和exactQuoteChecks。**REAL_JEV_SCORING_VERIFIED=PARTIAL** 指已有真实业务评分证据，但缺严格全样本和Jev完整持久化E2E，不表示所有真实评分均未做过。

## 9. 最小修复方案及是否需要Jev专用代理

目前 **JEV_PROXY_REQUIRED=UNKNOWN**。先核实供应商允许的地区/路径及本账户准入；若允许当前访问，应由其纠正错误区域判定，或在获准路径下再做一次验证。若供应商确认该账户/访问地区不允许，继续使用已验收的DeepSeek路径。浏览器能充值不能代替准入确认。

若供应商允许某HTTP代理出口，且必须让Java稳定使用该出口，建议增加可选的Jev专用HTTP代理。它用于明确单客户端路径，不保证解决账户或区域政策问题。修复准备的文件计划如下，**本轮均未实施**：

| 待修改/新增文件 | 最小职责 |
| --- | --- |
| JevProperties.java | 添加 ai.judge.jev.proxy.enabled/host/port；默认关闭，启用时校验host及1～65535端口；不引入节点选择或代理管理 |
| JudgeConfiguration.java | 仅Jev的HttpClient.Builder启用 `proxy(ProxySelector.of(new InetSocketAddress(host, port)))`；继续使用自己的requestFactory与builder.clone |
| application.yaml | 添加可选JEV_PROXY_ENABLED/HOST/PORT占位；默认关闭，具体本地值留在忽略配置或单次进程中 |
| JevPropertiesTest / JudgeConfigurationTest | 聚焦关闭默认行为、启用绑定与selector、非法端口及其他客户端装配不受修改影响；Mock验证，不额外收费 |
| 新的一次性诊断工具（如确有授权） | 专属预算/锁/记录；Jev直接上游或仅Jev独立transport，不复用已消费E2E入口 |

核心接线只是Jev所持有HttpClient的可选 `.proxy(...)`。关闭时保持当前默认行为；不调用 `ProxySelector.setDefault`，不修改JVM全局http/https代理属性，不设置系统环境或切换TUN/全局节点，也不共用代理后的requestFactory。HTTPS官方API通过HTTP代理CONNECT隧道访问，API Bearer仍只给API，不能当代理认证；代理认证若确有需求再单独设计。

DeepSeek、Embedding已有各自JDK客户端和requestFactory，MySQL使用JDBC、Milvus使用原SDK；仅改Jev实例不会给这些组件设置代理。但**修改系统TUN/路由会影响更多流量**，不能用它满足本次“仅Jev受影响”的要求。未来离线检查应验证其他客户端配置仍与变更前一致。[HttpClient的每客户端代理配置](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html)

451诊断另有一个可选小改进：保留结构化HTTP状态及白名单request-ID元数据供内部诊断，继续不记录任意错误正文、Headers或用户答案。它不是解除451的必要修复，本阶段不扩展实现。

## 10. 仍需真实测试时：一次最小预算、入口和授权

**完成本报告与解释历史差异不需要真实测试。** 只有供应商确认允许的访问路径后，要证明修复后的当前连通性，才需要另一次新授权。原五题E2E无需再次执行。

建议第一步只做 **1次Jev POST /v1/systemone**：小型合成state、五维Score、一个covered/uncovered点及一个yes/no追问Choice，使用生产JevClient的HTTP和严格JSON路径，记录状态、返回模型、完整安全响应、usage、可用request ID及显式proxy选择。固定一次，无重试，无Embedding/MySQL/Milvus，无DeepSeek反馈或fallback；失败即停止。该单请求只证明API/JSON/原生协议，不能声称完整JevJudgeProvider业务评分恢复。

当前官方价为每百万input Tokens $0.042，output免费。以历史7105 input估算一次约$0.00029841；按7500 input预算约$0.000315，计划批准上限 **$0.01**。按官方单请求64k token上限折算input成本约$0.002688，仍低于该批准上限；价格/限额执行前再核验，返回账单可能另有规则，不把估算当扣费记录。[官方模型价格与上下文限制](https://docs.typesafe.ai/models)

入口应是**另行创建且评审的一次性工具**，建议 `tools/jev-diagnostic/run.ps1 -Mode ExecuteOnce`；该入口本轮未创建，目前不可执行。先完成Offline/Prepare并审核请求、目的地、代理选择和单次预算，再让用户明确授权“仅1次Jev、最多$0.01、使用服务商允许路径、禁止重试和其他模型调用”。旧final-e2e/run.ps1、E02～E06历史入口及once-lock不得重用或删除。

如后续目标升级为确认当前“Jev+文字反馈+Java评分”组合，还需单独授权最多1次DeepSeek反馈，属于另一验收范围；不把它隐藏进上述1次Jev预算或自动降级。已有历史33例业务成功本轮不重复验证。

## 11. 对简历项目描述的影响

可继续描述已经验收的项目闭环，例如：

> 实现基于固定题库的Java后端模拟面试，使用RAG检索评价证据、Java校验与计分、确定性追问及报告聚合；完成五题真实服务E2E，接入可选Jev结构化Judge，并验证其区域拒绝时降级到DeepSeek。

若强调Jev，应写“完成生产适配及真实结构化评分实验，保留协议校验和模型降级”，明确真实实验已有成功但严格质量验收未全通过。不能写“Jev完整五题评分已验收”“Jev评分准确率达标”“代理已修复”或“所有响应均可靠”。451不推翻DeepSeek已完成的业务E2E，也不需要为了简历再次消耗模型请求。

## 12. 本轮核验、交付及状态

- 本轮JUnit/Maven/Smoke/E2E：未执行；新测试执行/失败/错误/跳过为0/0/0/0，不能将未执行称为测试通过。
- 原始文件核验：E05/E06的76份Jev原始响应、10月13日一份Jev请求和一份响应，共78份，SHA匹配78、缺失0、不匹配0。这是历史文件核验，不是新模型或集成测试。
- 交付核验：18份关键源码、配置和证据文件前后SHA不变；入场Git状态不变；报告30个本地文件链接均存在，6个最终状态字段齐全，未检出凭证值格式。
- 历史测试引用：E06报告记载定向365/0/0/0；本轮没有复跑。历史native38、完整33是实际模型实验分母，不能与JUnit数量混用。
- 新增：本报告，用于证据对比与修复准备。修改生产/测试代码和配置：0；付费请求：0；系统网络修改：0；历史文件及锁删除：0。
- 当前HEAD为6076a70；入场已有未提交改动，保留原样。报告匹配仓库既有ignore规则，是本地交付，未暂存/提交/推送；既有代理兼容代码也不能仅凭文档认定已发布。
- 公共官方文档仅用于核实协议、Java代理行为与条件预算；文档浏览不是本机API或出口连通性测试，不能提供本机地区证据。
- 剩余问题：服务商识别的地区、两天实际出口、账户准入、允许的代理路径及当前可用性未知；Jev严格质量及持久化完整E2E尚未取得通过证据。
- 推荐下一步：由用户通过官方支持渠道核实451准入原因和允许路径；需要时另行授权可选Jev代理小改动及一次最小验证。本轮调查已经完成，无需继续模型调用。

```text
JEV_HISTORICAL_HTTP200=CONFIRMED
JEV_HTTP451_ROOT_CAUSE=CONFIRMED_REGION_UNAVAILABLE_RESPONSE; EXACT_IP_REGION_OR_ACCOUNT_RULE_UNKNOWN
JEV_API_PROTOCOL_STATUS=HISTORICALLY_VERIFIED_JEV_1_13_0_NATIVE38_OF38; FULL_COMPOSITE33_OF38; CURRENT_REQUEST_BLOCKED_BEFORE_PARSE
JEV_PROXY_REQUIRED=UNKNOWN
REAL_JEV_SCORING_VERIFIED=PARTIAL
NEXT_ACTION=CONFIRM_PROVIDER_ALLOWED_ACCESS_PATH; THEN_OPTIONAL_JEV_ONLY_PROXY_AND_NEW_ONE_CALL_AUTHORIZATION
```


> 公开副本仅隐去本机用户目录。原始报告保留本地；历史实验结论、执行锁和冻结规则未修改。
