# D4 人工与 AI 模拟面试操作入口

本次交接适用于本机隔离的 `evaluation` 环境。语料与向量已完成逐条对账，生产业务集和公开冻结集的 Prompt 证据相关率均低于门槛。测试时记录具体误判、缺证据和恢复行为。最终准入与真实 HTTP 冒烟以 [D3-B/C/D 最终验收](D3_BCD_FINAL_ACCEPTANCE_20261114.md) 为准。

## 1. 在本机启动

使用 Java 21、Node 24 和项目 Maven Wrapper。后端目标固定为 MySQL `127.0.0.1:3308/ai_interview_evaluation` 和 Milvus `127.0.0.1:19532/default/knowledge_chunk_vectors_evaluation`。先用 `lsof` 确认端口属于本轮 evaluation 进程，再直接访问。否则在项目根目录启动后端。

```bash
cd /Users/kun/Developer/projects/ai-interview-system
umask 077
mkdir -p backups/D3_BCD_20261114
./mvnw -o -B -ntp -DskipTests compile dependency:build-classpath \
  -Dmdep.outputFile="$PWD/backups/D3_BCD_20261114/classpath.txt"
python3 -B tools/rag-d3/start_evaluation.py
```

后端监听 [127.0.0.1:18482](http://127.0.0.1:18482)。启动时会读取项目根目录 `.env` 及 `~/.config/ai-interview/evaluation-embedding.env`、`~/.config/ai-interview/evaluation-deepseek.env`。凭据由启动器加载到子进程。每次启动会生成新的本地 JWT 签名密钥，之前的登录状态失效时重新登录即可。可以在权限受控的终端前台查看日志，或在 `umask 077` 后将输出追加到 `backups/D3_BCD_20261114/d4-backend.log`。本轮进程和日志的现场状态见最终验收记录。

另开终端，从同一个仓库启动前端。已有 `frontend/node_modules` 时可直接执行，首次安装可用 `npm --prefix frontend ci`。

```bash
cd /Users/kun/Developer/projects/ai-interview-system
AI_INTERVIEW_BACKEND_URL=http://127.0.0.1:18482 \
  npm --prefix frontend run dev -- --host 127.0.0.1 --port 15174 --strictPort
```

打开 [注册](http://127.0.0.1:15174/register) 或 [登录](http://127.0.0.1:15174/login)。注册时用独立的合成账号、展示名和邮箱，例如使用 `example.com` 下的测试地址。口令用随机值，只保存到权限 600 的本地文件。浏览器端选用独立用户资料，避免多个测试身份复用同一登录存储。需要确认监听进程时，用 `lsof -nP -iTCP:18482 -iTCP:15174 -sTCP:LISTEN`。

## 2. 第一个人工场景

从 [开始面试](http://127.0.0.1:15174/interviews/new) 选 EASY，用自己的措辞作答，并在一题故意省略一个重要成立条件。观察评价能否指出具体遗漏，是否误把参考答案里的内容当作你已说过的话。如果出现追问，回答该追问再继续。EASY、MEDIUM、HARD 分别有 5、7、9 道主问题，每题最多一次追问。尚有进行中的面试时，创建入口会尝试恢复既有会话。

看到完成提示后进入报告页，点击「生成报告」，再从 [面试历史](http://127.0.0.1:15174/interviews) 打开复盘和报告。记录会话 ID、场内问题 ID、MAIN 或 FOLLOW_UP、是否有 FINAL、报告状态和具体差异，方便与证据对照。

## 3. AI 经真实 API 执行同一流程

每个 AI 场景使用独立的合成 USER，单场串行。把题干和本次允许的合成上下文给作答模型，JWT 保留在本机请求程序内。请求地址基于 `http://127.0.0.1:18482`。JSON 请求设置 `Content-Type: application/json`，登录后带 `Authorization: Bearer <accessToken>`。

1. `POST /api/auth/register`，提交 `account`、`username`、`password`、`email`。然后 `POST /api/auth/login`，提交 `account`、`password`，读取 `data.accessToken`。
2. `POST /api/interviews`，请求体例如 `{"difficulty":"EASY"}`。保留 `data.sessionId` 和 `data.currentQuestion.interviewQuestionId`。需要确认会话时调用 `GET /api/interviews/current`。
3. `POST /api/interviews/{sessionId}/answers`，提交 `interviewQuestionId`、`answerContent`、`requestId`。每道场内问题使用新请求键。遇到响应超时，先读会话详情确认进度。如需重放，复用原题目 ID、原正文和原请求键。按 `data.nextAction` 分支处理。 `FOLLOW_UP` 与 `NEXT_MAIN` 使用 `data.nextQuestion`；`FINISH` 表示答题流程结束。
4. 会话进入 `COMPLETED` 后，通过 `POST /api/interviews/{sessionId}/report` 发起生成。用 `GET /api/interviews/{sessionId}/report` 读取状态，用 `GET /api/interviews/{sessionId}` 和 `GET /api/interviews/history?page=1&pageSize=10` 核对详情与历史。

## 4. 报告状态与恢复

- `NOT_STARTED` 可显式生成，`FAILED` 可在确认故障已解除后显式重试。
- `GENERATING` 先用 GET 查状态。仍属正常执行时，重复 POST 会返回 409。长时间未结束时可用报告页「检查并尝试恢复报告」或同一报告 POST，请服务端按生成时间和版本条件判断是否过期。默认报告过期阈值为 10 分钟，由服务端时间判定。
- `READY` 可直接读取或再次进入报告页。超时结果不明时先读状态，避免循环发出生成或提交请求。

本次先验证单用户功能与少量 AI 场景。正式评分质量、人工独立标注、并发容量、跨场次题目去重和 MQ 异步报告均不属于这份操作单的验证结论。
