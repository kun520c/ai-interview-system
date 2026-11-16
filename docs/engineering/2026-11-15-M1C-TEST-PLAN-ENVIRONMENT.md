# M1-C 测试方案、环境与口径

记录日期 2026-11-15（Asia/Shanghai）；所有权锁修复前 A/B 在 2026-11-14 23:31:47～23:51:16 执行，最终版另行重测并记录独立起止时间。目标是在不触及历史数据或其他项目服务的前提下，分别验证同步与异步报告的业务完成语义、消息恢复、资源成本和模型输出。完整结论见独立验收报告 `/Users/kun/Documents/Reports/ai-interview-system/2026-11-15-M1C-RocketMQ异步报告验收.md`；本文件不覆盖原 M1-A/M1-B 记录。

## 版本与隔离

- A：独立工作树 `/Users/kun/.codex/worktrees/m1c-sync-baseline/ai-interview-system`，同步代码 `84e3489a85d0fb6c8b54ad2a130c52d7476fb897`；S1 历史全量回归见 `docs/verification/S1_INTERVIEW_VISIBILITY_SECURITY_20261114.md`。本轮打包成功。A JAR SHA-256 `3fb095291adf7b21e8b69695f70aaa6085e6631b29522583f21af11a2e0d7c52`。
- B：当前工作区的 M1-B 未提交实现，基于相同 HEAD，**没有可代表完整 B 的 Git SHA**。首轮 B JAR SHA-256 `cf1a199e152acd4401c5a6ada1e6671d19f1af9d47f9a0ef1ac8c52a527d45ec`；首次所有权修复版 `8b59ecbfa078c78a697bac4d1cf26b30e97f5207a938edf14593c699291e2f86` 随后暴露 MyBatis 一级缓存问题；最终修复版 JAR `f3b6095d5ed9cc465dfd8f000a1ea658a08c771576e19e8817e134de427e5334`。首轮逐项散列见 [source-manifest.json](m1c-20261114/source-manifest.json)，最终构建另行留存，不得把首轮指标当成最终构建的指标。
- A/B 各自独立 `mysql:8.0.45` ARM64 容器，分别在 loopback 3317/3318 的 `ai_interview_test`；B 增加既有迁移的 14th 表。两库基础表定义的 SHA-256 均为 `398053c5d0b145d4981c8b463043aa53e7e726b9cb3ad2eb1de79c5c9990997c`。每条测量请求各用独立合成用户、Session、MAIN 和 FINAL；两组对应**用户消息 Prompt** 的 SHA 一致，未逐条哈希整个模型请求。A、B 绝未连接同一批业务 Session。
- RocketMQ 使用独立 `ai-interview-m1c` Compose 项目、专用网络、NameServer/Broker，loopback 19876/20911；名称虽沿用受测试隔离器约束的 `ai-interview-m1b-report-test` Topic/group，但 Broker 是全新 M1-C 容器，不是历史 M1-B 或其他项目服务。额外竞争场景另用唯一 Topic。故障后不删除容器、消息或卷。
- MySQL 镜像 ID `sha256:4af1f8815716546f5b12410f7621f37f93db8dd11a184706ef59111930b8c2ff`；RocketMQ 镜像索引 ID `sha256:4a80ff5cd4b0a3d3ef6dc338ce2028a85bd995648cb3aa5c3e753746d2dbc587`，均 ARM64。macOS 实测 26.7.1，Temurin 21.0.13、Maven 3.9.16、Docker 客户端/服务端 29.9.2、Node 24.22.1/npm 11.19.1。Docker 可用 10 CPU、8319504384 bytes；每个测试 MySQL 上限 1 CPU/768 MiB、NameServer 0.5 CPU/512 MiB、Broker 1 CPU/1 GiB，A/B JVM 均 `-Xms128m -Xmx512m`。同机其他服务保持运行，开始 load average 1.80/1.75/1.80；这是共享开发机而非专用基准机。
- 初始化时 B 容器的首次挂载脚本因 `/bin/bash` 执行权限退出 126；保留容器并核对空 schema 后显式执行初始化，未重建或删除卷。现存 B 容器的初次挂载与最终 Compose 描述有差异，不能对其盲目 `compose up` 重建。操作入口及保护见 [工具说明](../../tools/m1c/README.md)。
- 首轮停测前，B 合成任务为 SUCCEEDED 313、FAILED 1、QUEUED/RUNNING 0。只停止本轮四个容器并保留数据和消息；两个 MySQL 均 exit 0，Broker 和 NameServer 均 exit 137、`OOMKilled=false`，不能称其优雅排空。之后为所有权锁修复重启相同容器，Broker 恢复并重跑故障测试；不代表生产可靠性或所有消息持久性已验证。其他项目原运行容器仍为运行状态。

## 实验协议

先运行定向及完整功能回归，再做本地 HTTP A/B。模型替身仅绑定 `127.0.0.1:28889`，按配置延迟 1/3/5 秒并返回固定合法结构化报告；并发客户端 1/5/10。每组每配置预热 3 条、测量 2 轮各 15 条，交错为 ABBA/BAAB，配置顺序固定随机种子。共 54 条预热、540 条测量；每个配置的 A/B 均有 30 条有效请求。闭环客户端先 POST，B 每 200 ms GET 至 READY 后才发下一条。全部 594 次桩调用的用户消息 Prompt、返回内容和调用数一一配对校验通过。系统 Prompt 所属构造器、生成器、DeepSeek 客户端及配置类的 A/B 源码 SHA 相同，DeepSeek 默认配置片段也相同；未逐条记录整个 HTTP 请求体，不能称全请求哈希已验证。

POST 对 A 表示最终 READY，对 B 表示通常只受理 GENERATING，不能把二者都叫生成耗时。`ready_ms` 是客户端从申请到 200 ms 轮询观测 READY 的实测上界，含请求、轮询和调度误差；不等于真实浏览器绘制时刻。P50 为中位数、P95 为 nearest-rank；吞吐是两轮各自时间之和的闭环批次吞吐，不是持续开放负载下的最大 QPS。每一配置的两个 15 条轮次可在原始记录中独立检查，30 条并不代表线上统计分布。

B 的 `queued_at → started_at` 同时包含 Outbox、Broker 及消费者等待，不能解释成纯 MQ 投递延迟。任务表没有 `SENT` 时间戳，所以无法从现有记录分离纯 MQ 网络/投递时间；测试后另从 Broker 查询三条消息的 Born/Store 时间，作为[有时钟和样本限制的探索性区间](m1c-20261115-after-fix/MQ_TIMESTAMP_SAMPLES.md)。`started_at → updated_at` 是含提交处理的近似执行区间；后者为本地审计时区，已按数据库时差转换为 UTC，迟到的投递状态更新仍可能影响它。每约 10 秒加采样耗时读取 `docker stats`、JVM RSS/CPU、线程 dump 和 MySQL 连接；采样可能影响负载、跨轮次边界，CPU 为观测而非持续均值，HTTP 忙线程按不在 Tomcat 空闲队列的栈近似，连接数含采样连接。

所有权锁修复前的原始请求、比较值、资源采样、桩调用、图表与环境在 [m1c-20261114](m1c-20261114/AB_RESULTS.md)，日志保留于 `/Users/kun/Developer/projects/ai-interview-system/backups/M1C_20261114_1521UTC/`。试运行 01 因模型桩未处理 chunked 请求得到 HTTP 502；修复桩后试运行 02 暴露 `updated_at` 本地时区与 `started_at` UTC 混算出约 8 小时假耗时；修正后试运行 03 通过配对校验。两次错误和原日志均保留，均未纳入正式数据。首次所有权修复后性能重跑中途发现一级缓存问题，183 条完成记录保留为中断样本，不能纳入最终比较。

最终 JAR 的 HTTP 实验在 **2026-11-15 01:02:59～01:22:28（Asia/Shanghai）**执行：[九组结果及原始数据](m1c-20261115-after-fix/AB_RESULTS.md)。每组每配置 30 条测量及 3 条预热，共 594 条；每对用户消息 Prompt 哈希相同、594 次桩调用，测量成功 540 条，错误和超时 0。保留旧数据使 A/B 开始时分别已有 **619/633** 条 Session，新增实验数据成对一致，背景行数不同是限制。开始 1/5/15 分钟 load average **2.57/2.08/1.99**，实验停止后随即记录为 **1.19/1.33/1.64**（浏览器预检可能已启动）；同机其他服务仍运行，不是隔离的专用基准机。脚本 SHA、配置顺序、JAR 和源码清单见结果目录。

**浏览器补测**在 **2026-11-15 01:24:21～01:49:40（Asia/Shanghai）**独立执行，Chrome 157.0.8103.78 无头模式，A/B 使用相同当前 Vue 前端分别连接两版后端；同样 9 组、每组每配置 30 测量 + 3 预热，全部 594 次均观察到报告摘要可见，297 对用户消息 Prompt 哈希一致。B 使用页面实际 3 秒 GET 轮询；从自动化点击指令到摘要可见含调度开销，禁用后台计时器节流，不代表物理屏幕绘制或真人反应。该实验与 HTTP 测量分次执行，不能合并样本。[结果和逐条数据](m1c-20261115-browser/BROWSER_RESULTS.md)。

最终停测前，独立库无 QUEUED/RUNNING；保留终态为 `SUCCEEDED/SENT` 1023、`SUCCEEDED/PENDING` 6、`FAILED/PENDING` 3，PENDING 终态来自直接发消息与崩溃故障探针，不是活跃任务。专用消费组 Broker/consumer offset 均 1039、积压 0。仅停止四个 M1-C 容器：MySQL A/B 均 exit 0，Broker/NameServer 均 exit 137 且 `OOMKilled=false`；无法称为优雅关闭，最终关闭后的消息持久性未再验证。所有容器、数据库、消息和卷保留；其他项目及既有 MySQL/Milvus 容器在停止前后均仍运行，详见索引中的收尾原始日志。

## 安全与费用

正式负载仅使用本地模型桩，真实服务单独运行。用户明确批准 DeepSeek `deepseek-flash` 最多 2 次、总预算 0.05 美元；代理限定一次在途、无自动重试/重定向、每次请求体≤8 KiB、输出≤1024 token、无付费工具，先记录尝试并按高峰价做保守预留。两次均 HTTP 200 且有 usage，共输入 901、输出 529 token；按未命中高峰费率估算总计 **0.0009051 美元**，不是供应商账单。两个响应均未取得 `x-request-id`。未配置供应商侧财务硬限额，应用侧预留不是对供应商账单的数学保证；本轮两次实际 usage 已收齐，没有未知付费尝试。计数及 SHA 见 [real-model-usage.jsonl](m1c-20261114/real-model-usage.jsonl)。

原始 DeepSeek 官方费率参考：[Models & Pricing](https://api-docs.deepseek.com/quick_start/pricing/)，报告中仅使用采集时的最不利高峰费率估算；真实发票与账单仍是最终依据。两次真实调用和浏览器验收均发生在所有权锁修复前，批准的次数已用尽；修复后的对应路径仅用真实 MySQL、RocketMQ 和本地模型桩回归，不得称最终 JAR 已做真实模型调用。Embedding 真实调用 0。没有读取或修改正式/evaluation 数据、题库、生产评分 v4、RAG 或其他项目 Docker 容器；未提交或推送。
