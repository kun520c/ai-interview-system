# 本地运行指南 · AI 模拟面试系统

本文档以 **Windows PowerShell、JDK 21、MySQL 8、Node.js** 为例。适用于自行运行项目，不等同于云端部署指南。

[返回项目首页](../README.md) · [前端工程说明](../frontend/README.md) · [配置源文件](../src/main/resources/application.yaml)

## 1. 运行模式

| 模式 | 需要什么 | 能完成什么 |
| --- | --- | --- |
| 基础后端 + Vue | JDK 21、MySQL 8、Node.js | 注册/登录、题库管理、页面和基本 API；不进行真实 AI 评分或文档 Embedding |
| 完整 AI 流程 | 上述环境 + Milvus Standalone + Embedding API + DeepSeek API | 知识入库、RAG 检索、模拟面试评分与报告；可能产生模型费用 |
| 离线测试 | 独立 `ai_interview_test` 库及测试依赖 | 运行项目默认 Maven 测试；外部服务真实调用按 opt-in 开关排除 |

项目没有提供一键部署脚本或 Milvus Compose 文件。不要把 `tools/` 目录里的实验脚本当作生产启动方式。

## 2. 获取仓库与初始化数据库

```powershell
git clone https://github.com/kun520c/ai-interview-system.git
cd ai-interview-system
java -version
.\mvnw.cmd -v
mysql -u root -p
```

在 MySQL 交互终端输入：

```sql
SOURCE sql/ai_interview.sql;
EXIT;
```

`sql/ai_interview.sql` 建立 `ai_interview` 数据库及 13 张表，**不会创建演示账号或初始化面试题目**。请使用你自己新建的本地数据库，不要在已有数据的生产数据库运行此脚本。

### 已有数据库升级到 2026-10-19 并发修复版

如果使用本仓库最新 `sql/ai_interview.sql` **新建空库**，其中已经包含任务身份字段，**不要重复运行增量迁移**。

如果是在**保留原有数据的旧版 `ai_interview` 库**上升级：

1. 先停止并排空所有旧版 Spring Boot 实例以及可能继续写入的文档处理任务，禁止新旧版本同时写入。
2. 备份旧库并确认备份可恢复；核对当前连接地址、Schema，切勿在其他项目数据库或生产库直接试验。
3. 检查以下字段是否存在：

   ```sql
   SELECT DATABASE();
   SHOW COLUMNS FROM interview_answer LIKE 'attempt_id';
   SHOW COLUMNS FROM knowledge_document LIKE 'processing_attempt_id';
   ```

4. 仅为**缺失**的字段执行 `sql/migrations/20261015_timeout_attempt_fencing.sql` 中对应的 `ALTER TABLE`。新增字段分别是 `interview_answer.attempt_id`、`knowledge_document.processing_attempt_id`，均为可空 `varchar(32)`；历史记录无需回填。
5. 核验两个字段及旧数据、约束后，再启动新版本。MySQL 两条 DDL **不能作为一个整体事务回滚**；部分成功时检查字段状态并补齐，不要盲目重跑。

本项目的超时接管由**后续请求触发**，不是自动后台调度。已发出的 Milvus RPC 可能迟到，正式数据的孤立向量只允许在独立维护窗口依照[手动对账指南](../tools/orphan-vectors/README.md)安全处理；默认 dry-run，不应在日常启动时运行删除。详见[并发设计说明](design/timeout-task-fencing.md)及[2026-10-19 验收记录](verification/CONCURRENCY_FINAL_ACCEPTANCE_20261019.md)。

## 3. 启动基础后端

在仓库根目录的 PowerShell 中配置本机环境变量：

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/ai_interview?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME = Read-Host '本地 MySQL 用户名'
$dbPassword = Read-Host '本地 MySQL 密码' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbPassword).Password

$jwtBytes = [byte[]]::new(32)
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)

$env:MILVUS_ENABLED = 'false'
$env:DEEPSEEK_ENABLED = 'false'
$env:AI_JUDGE_PROVIDER = 'deepseek'
$env:EMBEDDING_BASE_URL = 'http://localhost:18080/v1'
$env:EMBEDDING_API_KEY = 'local-disabled-placeholder'

.\mvnw.cmd -B -ntp spring-boot:run
```

默认后端地址为 `http://localhost:8080`。本模式禁用真实向量存储及模型，Embedding 配置为必需的占位值；**不能执行真实知识处理、AI 评分及完整面试流程**。

密码和 API Key 不要写进公开仓库。另一种方式是将个人配置写入本地忽略的 `src/main/resources/application-local.yaml` 并使用 `-Dspring-boot.run.profiles=local` 启动；该文件不能提交。

## 4. 启动 Vue 前端

在新的终端：

```powershell
cd ai-interview-system/frontend
npm ci
npm run dev
```

Vite 默认在 `http://localhost:5173` 启动。`/api` 请求由 `frontend/vite.config.ts` 代理到 `http://localhost:8080`。如后端在其他地址运行，可以启动前设置：

```powershell
$env:AI_INTERVIEW_BACKEND_URL = 'http://localhost:8080'
npm run dev
```

启动仅表示页面可加载；完整面试仍取决于题库数据、Milvus 及 AI 服务的配置。

## 5. 创建本地账号与演示题库

1. 在前端注册账号，或请求 `POST /api/auth/register`（`account`、`username`、`password`、`email`；密码长度 8–72）。
2. 如果需要管理题库，在 **本机演示数据库** 中将自己的账号角色设为 `ADMIN`，然后重新登录：

   ```sql
   UPDATE user SET role='ADMIN' WHERE account='your-local-account';
   ```

3. 在管理员页面或通过 `POST /api/admin/questions` 创建并启用题目与评分点。EASY 面试至少需要 `JAVA_BASIC`、`JAVA_COLLECTION`、`SPRING`、`MYSQL`、`NETWORK` 五类各一题；MEDIUM/HARD 的规划见 [InterviewPlanConfig.java](../src/main/java/com/kun/aiinterview/interview/config/InterviewPlanConfig.java)。

题目接口请求示例：

```json
{
  "category": "JAVA_BASIC",
  "knowledgePoint": "接口与抽象类",
  "difficulty": "EASY",
  "questionContent": "Java 接口与抽象类有什么区别？",
  "referenceAnswer": "接口支持多实现；抽象类可具有实例状态和构造器。",
  "scoringPoints": [
    {
      "pointType": "CORE",
      "content": "正确区分多实现、单继承、状态和构造器",
      "weight": 100
    }
  ]
}
```

评分点的启用权重总和必须为 100；题目默认启用。上传知识文档不会自动生成面试题。

## 6. 启用完整 AI 链路（会产生外部模型调用费用）

准备 Milvus Standalone、具有模型调用权限的百炼/DashScope Embedding Key 与 DeepSeek Key。停止基础后端，在同一个 PowerShell 中设置：

```powershell
$env:EMBEDDING_BASE_URL = 'https://dashscope.aliyuncs.com/compatible-mode/v1'
$embeddingKey = Read-Host 'Embedding API Key' -AsSecureString
$env:EMBEDDING_API_KEY = [System.Net.NetworkCredential]::new('', $embeddingKey).Password

$env:MILVUS_ENABLED = 'true'
$env:MILVUS_URI = 'http://localhost:19530'
$env:MILVUS_COLLECTION_NAME = 'knowledge_chunk_vectors_local'

$env:DEEPSEEK_ENABLED = 'true'
$deepseekKey = Read-Host 'DeepSeek API Key' -AsSecureString
$env:DEEPSEEK_API_KEY = [System.Net.NetworkCredential]::new('', $deepseekKey).Password
$env:AI_JUDGE_PROVIDER = 'deepseek'

.\mvnw.cmd -B -ntp spring-boot:run
```

默认 Embedding 为 `qwen3.7-text-embedding`、1024 维；需要确保自己的 API 权限与向量 Collection 的维度匹配。当前默认 DeepSeek 模型及其他环境变量见 [application.yaml](../src/main/resources/application.yaml)。

以管理员身份上传 UTF-8 TXT/Markdown 知识文档（`POST /api/admin/knowledge/documents`，`multipart/form-data`，字段包含 `file`、`title`、`category`），再显式调用 `POST /api/admin/knowledge/documents/{documentId}/process`。知识库文档达到 `READY` 后，可开始完整面试。

创建 EASY 面试：`POST /api/interviews`，请求体 `{"difficulty":"EASY"}`。提交回答：

```json
{
  "interviewQuestionId": 1,
  "answerContent": "在这里填写演示回答",
  "requestId": "demo-answer-001"
}
```

将 ID 替换成接口返回的当前问题 ID。重试同一提交时必须复用完全相同的 `requestId`、问题 ID 与答案内容；下一题使用新的 requestId。最终评价确认后，可通过 `POST /api/interviews/{sessionId}/report` 生成报告。

## 7. 测试

前端：

```powershell
cd frontend
npm ci
npm run typecheck
npm run lint
npm run test:unit
npm run build
```

后端默认回归要求一套**独立的 MySQL 测试库**，库名必须是 `ai_interview_test`。使用测试专用 Schema 副本，不能指向 `ai_interview` 或任何业务库。按配置设置 `TEST_DB_URL` / `TEST_DB_USERNAME` / `TEST_DB_PASSWORD`，然后：

```powershell
.\mvnw.cmd -B -ntp test
```

真实模型、Milvus、Embedding 和 AI baseline 的测试通常由显式 opt-in 环境变量控制，默认不会调用收费服务。执行前检查并清除 `RUN_REAL_*` 与 `MILVUS_ENABLED` 等开关。严格 E2E 工具另见 [浏览器验收说明](../tools/browser-e2e/README.md)，它需要额外的本地运行环境并建立隔离实例。

## 8. 常见问题

**页面能打开但无法开始面试**：检查题库是否为对应难度及分类创建了足够启用题目；数据库默认是空库。

**能登录却不能评分**：基础模式禁用了 DeepSeek / Milvus；请勿把占位 API 地址当成真实服务。

**前端 API 报错**：先确认后端监听 8080，再检查 Vite 的代理地址；无需仅为本地开发开启 CORS。

**Maven 测试连不上数据库**：确认使用 `ai_interview_test`，且测试用户具备所需权限；不要改为业务库以“解决连接失败”。

**担心付费调用**：保持 `DEEPSEEK_ENABLED=false` 和 `MILVUS_ENABLED=false`，不要启用 `RUN_REAL_*`；本模式只能体验基础功能。

