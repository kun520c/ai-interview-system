# Jev 独立 HTTP 代理修复与免费网络诊断

日期：2026-10-14（Asia/Shanghai）。代理修复和定向回归已完成；用户随后授权的唯一一次 Jev POST 返回 HTTP200，但运行器后置校验失败，评分验收未通过。未提交、未推送，未重试。

```text
JEV_PROXY_READY=YES
CLASH_HTTP_PORT=7897
PROXY_CONNECTIVITY=HTTP_200_AND_HTTPS_CONNECT_200
JEV_PROXY_ROUTING=VERIFIED_EXPLICIT_HTTP_PROXY_WHEN_ENABLED
DIRECT_FALLBACK=DISABLED
PROVIDER_REGION_ACCESS=CONFIRMED_FOR_THIS_AUTHORIZED_SG_REQUEST
OFFLINE_TESTS=274_PREVIOUS_PASS;CLOSEOUT_RUNNER_20_PASS;HISTORICAL_76_RESPONSE_REPLAY_PASS
REAL_JEV_CALLS=1
NEXT_ACTION=OFFLINE_CLOSEOUT_COMPLETE_NO_ADDITIONAL_PAID_CALL
```

`DIRECT_FALLBACK=DISABLED` 指启用 Jev 代理后的失败路径。按要求，生产默认配置仍关闭代理；正常应用使用 Jev 时必须显式启用下列三个配置，新运行器则强制使用代理。

## 网络证据

Clash Verge（PID 30136）和 clash-meta（PID 31264）正在运行。配置和监听均确认：mixed-port `127.0.0.1:7897`；独立 HTTP 端口 7899，SOCKS 端口 7898，控制器 9097。系统代理指向 7897，当前 Clash 为 global，GLOBAL 选择为代理，TUN 已开启。本轮只读取状态，没有修改系统代理、TUN、路由、节点或订阅。

| 分层检查 | 结果 | 能证明什么 |
| --- | --- | --- |
| 7897 监听及 TCP 连接 | 成功 | 本地代理端口可访问 |
| 公共 `http://example.com/` 经代理 | HTTP 200 | HTTP 转发成功 |
| `CONNECT example.com:443` | 200 | HTTPS 隧道建立成功 |
| 公共 `https://example.com/` 经代理 | HTTP 200 | 隧道后的 TLS 和 HTTPS 转发成功 |
| Java 21.0.11 默认 ProxySelector | `sun.net.spi.DefaultProxySelector`，Jev URI 选择 DIRECT；`java.net.useSystemProxies=false` | 当前 JVM 默认没有显式 HTTP 代理 |
| 生产 Jev HttpClient 的启用代理路径 | Jev HTTPS URI 只选择 HTTP 代理 | 配置进入实际 transport，未改变 JVM 全局选择器 |
| Cloudflare trace：无 HTTP 代理 / 显式代理 | 两者 HTTP 200；分别 CN / SG，出口不同 | 当前公共测试请求的国家识别及路径差异 |
| 官方 Jev API 区域与账户准入 | 用户确认允许 SG 路径；唯一 POST 返回 HTTP200 | 此次账户和 SG 请求路径可访问，不代表其他地区或后续访问保证 |

最后一次 Java 免费诊断为 14:12:36。国家代码来自 Cloudflare 对当前请求的识别，不是服务商允许地区名单；TUN 开启，因此“无 HTTP 代理”也不能等同于没有任何系统转发。公网 IP 只在内存用于比较，不输出、不落盘。原始脱敏结果：[network-diagnostic.json](../target/jev-proxy-network/network-diagnostic.json)。

10 月 8 日的 80 次 HTTP200 与 10 月 13 日的明确区域拒绝 HTTP451沿用既有证据，未重跑。公开资料没有提供已确认的账户地区名单；[服务协议 2.3](https://typesafe.ai/legal/mca)要求遵守访问限制。准备阶段的准入为 UNKNOWN；15:01 用户已确认允许的新加坡出口并授权一次请求，运行器在免费预检 SG、HTTP200 后发送唯一 POST，获得官方 HTTP200。本结论仅限此次经授权的账户和路径。

## 修改范围和使用方式

| 文件 | 用途 |
| --- | --- |
| `src/main/.../judge/jev/JevProperties.java` | 新增 `proxy.enabled/host/port`；启用时校验缺失配置、主机格式及端口 1–65535；不支持代理凭据，toString 排除 host |
| `src/main/.../judge/jev/JevClient.java` | 增加专属 HttpClient 构建方法，启用时使用固定 `ProxySelector.of`，禁止重定向；失败抛既有脱敏异常 |
| `src/main/.../judge/JudgeConfiguration.java` | 仅 Jev 使用上述 transport，继续克隆共享 RestClient Builder |
| `src/main/resources/application.yaml` | 增加三项配置及环境变量，默认关闭 |
| `src/test/.../judge/JudgeConfigurationTest.java` | 配置绑定、缺失/非法配置、默认关闭、共享 Builder 和其他 provider 隔离 |
| `src/test/.../judge/jev/JevClientTest.java` | 增加 HTTP451 脱敏验证 |
| `src/test/.../judge/jev/JevJudgeProviderTest.java` | 增加 HTTP451 单次 DeepSeek 降级及 Java 合法性校验；保留已有测试改动 |
| `src/test/.../judge/jev/JevProxyTransportTest.java`（新） | 实际 ProxySelector、生产 POST 到本地代理、不可用代理对活跃 origin 的零直连验证 |
| `tools/jev-proxy-network/JevNetworkDiagnostic.java`（新） | 复用生产 transport 的免费公共 GET/CONNECT 诊断 |
| `tools/jev-proxy-network/JevScoreOnce.java`（新） | 直接使用生产 JevClient 的独立一次评分入口 |
| `tools/jev-proxy-network/JevScoreOnceOfflineTest.java`（新） | 一次锁、授权、预算、HTTP451、重定向和断线门禁的本地测试 |
| `tools/jev-proxy-network/run.ps1`（新） | 准备、编译、离线验证、制品校验及隔离执行子进程 |
| 本报告（新） | 本轮唯一交付报告 |

应用的三项配置为 `ai.judge.jev.proxy.enabled`、`ai.judge.jev.proxy.host`、`ai.judge.jev.proxy.port`。对应本机进程环境设置：

```powershell
$env:JEV_PROXY_ENABLED='true'
$env:JEV_PROXY_HOST='127.0.0.1'
$env:JEV_PROXY_PORT='7897'
```

这不切换评分 provider，也不授权真实调用。启用后，固定选择器只返回该 HTTP 代理，包含 HTTPS 与 loopback；代理失败不会重试 Jev 直连，正常业务仍可由 `JevJudgeProvider` 降级到 DeepSeek。DeepSeek、Embedding、数据库配置、生产默认 RAG 策略和 ignored 的 `application-local.yaml` 均未修改。历史最终 E2E 门禁使用自己的 HttpClient，未修改或重新执行；新运行器与其执行锁独立。

## 验证结果

最终 Maven 定向命令：`mvnw.cmd -o -B -ntp "-Dtest=JudgeConfigurationTest,Jev*Test,DeepSeekJudgeProviderTest,DeepSeekConfigurationTest,DeepSeekEvaluationClientTest,EmbeddingConfigurationTest,MilvusConfigurationTest,TestProfileExternalServiceIsolationTest" test`。

| 运行 | 测试 / 失败 / 错误 / 跳过 | 结论 |
| --- | --- | --- |
| 首次受限环境 | 已完成部分 22 / 0 / 3 / 0；随后中止 | 沙箱拒绝 Java 回环连接；不是代码回归，无完整总数 |
| 首次主机定向回归 | 268 / 0 / 1 / 0 | 原有隔离用例的 MySQL 认证失败；Maven 子进程缺少 DB 凭据 |
| 补齐临时子进程凭据 | 272 / 0 / 0 / 0 | BUILD SUCCESS |
| 增补默认空配置和合法降级后最终回归 | 274 / 0 / 0 / 0 | BUILD SUCCESS |
| 独立运行器准备阶段离线检查 | 15 / 0 / 0 / 0 | PASS；本地代理桩，不触及官方 API |
| 真实请求后修正范围校验的离线检查 | 17 / 0 / 0 / 0 | PASS；新增三档 Score 0–2 范围及超界/非有限值检查，本地成功响应覆盖 score=1.8 |
| 包装脚本无授权检查 | 1 个预期拒绝检查通过 | 子进程未启动，真实执行目录与锁尚未创建 |

覆盖了默认关闭、启用绑定、实际代理选择、代理 POST 路由、断线无直连、HTTP451 脱敏、单次 DeepSeek 合法降级和其他客户端隔离。Maven 回归中的 3 个环境隔离检查包含专用测试库 `SELECT DATABASE()`；没有修改数据库。没有运行全量 `mvn test`，没有运行 opt-in 真实模型/Embedding/Milvus Smoke Tests。所有 `RUN_REAL_*` 及 `MILVUS_ENABLED` 均在这些测试子进程中清除。日志保存在 `target/jev-proxy-network/preparation/`；修正了准备脚本子进程缺少 Windows PowerShell PATH 的问题，最终编译和准备成功。

## 唯一一次真实测试（已执行，禁止重跑）

离线结果：[runner-offline-status.json](../target/jev-proxy-network/preparation/runner-offline-status.json)。真实证据：[result.json](../target/jev-proxy-network/score-once-20261014-001/result.json)。执行锁 `EXECUTION_RESERVED.lock` 于 15:01:16 创建，`POST_CONSUMED.lock` 于 15:01:18 创建，两者均已消费并保留。原始结果未改写，SHA-256 为 `5C4C0319C2070EB87659335C934AA4248201D14F3A5831D44E5FBC1F484EAFC5`。修正前的执行源与制品封印另保存在同目录 `executed-JevScoreOnce.java` 和 `executed-seal.json`。

用户新授权原文：“授权通过已确认允许的新加坡出口执行一次 Jev 真实评分请求，费用预算上限 0.01 美元，不重试。”执行前重新核对官方价格、19 项制品哈希全部一致及离线 PASS；现有 Jev Key 从用户环境仅注入临时执行进程，未输出或落盘。下列为已执行命令的记录，不能再次执行；重新 Prepare 也不会恢复该次授权或重置执行锁。

```powershell
& .\tools\jev-proxy-network\run.ps1 -Mode ExecuteOnce -Authorization 'AUTHORIZE_JEV_PROXY_ONCE_MAX_USD_0_01' -ProviderAccessConfirmed -PricingConfirmed -AllowedCountry 'SG' -ConfirmedInputUsdPerMillion 0.042
```

实际结果：免费代理预检 SG、HTTP200；官方 Jev HTTP200；`jevPostAttempts=1`、`retryCount=0`，DeepSeek/Embedding/Milvus 调用及 MySQL 写入均为 0。生产 JevClient 已完成响应 JSON/模型/问题集合解析，随后运行器 `validateResponse()` 抛 `IllegalStateException`，最终为 `STOPPED_NO_RETRY`。

本地复核发现运行器把三个评分档位的合法 score 错限为 0–1；[官方 Score 定义](https://docs.typesafe.ai/primitives/score)是 0–2。这是运行器实现缺陷，已离线改为依据请求档位数检查范围，并把脱敏模型/分数/置信度保存移到校验之前。**原调用没有保存这些数值，无法确认当时具体触发字段或补报实际模型/评分/usage，修复后也没有重发请求，不能将原结果改判为评分验证通过。** 生产评分实现与既有评分质量策略未修改。

执行前重新确认，[官方模型页](https://docs.typesafe.ai/models)标价输入 $0.042/Mtok、输出免费，当前模型最大输入 64k；按 65,536 token 保守计算为 $0.002752512，低于 $0.01。固定请求正文限制 1024 字节。此上界依赖服务商价格与模型约束，**不等于已确认的实际扣费或服务商账单硬限额**；此次 usage 未保留，实际账单费用 UNKNOWN。

此次独立代理出网和 API 访问已真实验证，数值评分验收仍未完成；本次授权已用完，停止收费调用并保留证据。Java 默认代理及固定选择器行为参考 [Java 21 HttpClient.Builder](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.Builder.html)。

## 纯离线收口（15:20，未新增真实调用）

```text
JEV_NETWORK=PASS
JEV_SCORING_OFFLINE_REPLAY=PASS
JEV_PRODUCTION_CLIENT_STATUS=NO_DEFECT_FOUND_IN_76_ORIGINAL_RESPONSE_REPLAYS
JEV_REAL_ONE_CALL_SCORING=UNVERIFIED
REMAINING_ISSUES=OCT14_RAW_RESPONSE_SCORE_USAGE_MISSING;NO_MATCHING_THREE_LEVEL_REAL_SAMPLE
```

10 月 14 日执行目录只有两个已消费锁、`result.json`、执行源码及封印；**没有原始响应或可重放的评分/usage**。未恢复、推算或编造它们。此前的三档范围错误已修复，但不能据此把已消费调用改判成功。

本次读取 E05/E06 官方 HTTP200 事件及其原始响应字节，校验 SHA-256 后通过**纯内存 transport**交给当前生产 `JevClient`，以历史原请求调用生产 `JevJudgeProvider.validateAnswers`，再验证实际 `mapScore` 的五维映射。结果见 [historical-replay.json](../target/jev-proxy-network/closeout-offline/historical-replay.json)：

| 检查 | 结果 |
| --- | --- |
| E05 / E06 原始 HTTP200 响应 | 38 / 38，共 76 |
| 原始响应哈希、生产 JSON 解析、完整 Score/Choice 原生校验 | 各 76/76 通过 |
| 五维原分数按既有 0–4 → 0–20、HALF_UP 映射 | 380/380 通过 |
| 真实响应保留的合法 input/output usage | 76/76 存在 |
| 匹配三档原请求的真实响应 | 0；历史 380 个 Score 全部是五档 |

三档和五档使用相同 `type=score`、按 criteria 下标定义分数的协议原语；档位和请求语义不同。历史回放始终使用原五档 criteria、原 score/probabilities/legend，不裁剪、重标度或替换为 `accuracy` 三档问题。`JEV_SCORING_OFFLINE_REPLAY=PASS` **仅表示原 E05/E06 五维协议与映射复验通过**；三档 0–2 边界由合成离线样本验证，不冒充三档真实协议验收。不重审历史反馈质量，不重跑 E2E。

仅修改诊断工具 `tools/jev-proxy-network/JevScoreOnce.java`、其离线测试，并新增 `JevHistoricalReplay.java`；生产客户端、评分策略和数据库配置没有新增修改。诊断工具现在：

- 收到响应即以 `CREATE_NEW` 保存 HTTP 状态，再读取最多 64 KiB 成功响应并保存 SHA-256；错误正文不保存。
- 在生产解析和后续验证前，持久化 `response-capture.json`，保存合法模型、Score 范围、原 legend/probabilities/confidence 和整数非负 usage；过滤未知字段、请求内容、响应头及 Key 回显。
- JSON 严格拒绝重复键、尾随内容和标量类型强制转换；评分非法时只留状态、哈希及合法 usage，不把非法结果写成通过；usage 缺失或非法显式标注，绝不生成推测值。
- 将原字节交回生产客户端，保存行为不改变其 JSON 解析语义。保存失败会停止本地验证，不重试请求。

本轮验证：诊断运行器 **20 测试、0 失败、0 错误、0 跳过**；历史回放 **76 案例、0 失败**。新增覆盖成功评分/usage 的磁盘保存、评分失败时仍保留 usage、重复键/数字字符串拒绝、usage 缺失/负数/null/字符串的显式处理。首次新增复验工具编译缺少当前 Spring `getAttributes()` 接口实现，补齐后编译和验证通过；未作重复收费或全量回归。

对 E05/E06、既有评估证据、此次收费执行目录及已有执行锁共 **3,881 文件**做前后 SHA-256 核验，全部未变；[完整性结果](../target/jev-proxy-network/closeout-offline/historical-integrity-after.json)。原 `result.json` 的 SHA 保持不变，历史请求与执行锁没有重写。收口阶段真实模型请求 **0**，累计本轮授权 Jev POST 仍为 **1**。现有证据足够结束网络及历史五维协议收口，不申请新的收费调用。
