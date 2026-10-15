# P4 Semantic Evidence Gate 实验

全部代码位于 tools/rag-p4，未注册Spring Bean，不改变Java生产默认RAG路径。

- `SemanticEvidenceGate.java`：四分类JSON、候选身份、问题锚点、版本、正文逐字引用验证；只准入CORE_SUPPORT；部分核心支持和整题充分性分开。
- `P4DeepSeek.java`：复用现有DeepSeekEvaluationClient.completeJson，捕获usage及原始响应；一题一请求，禁止重试、重定向和第二次调用。
- `p4.py`：真实P3数据及修正来源核验、A/B离线对比、费用门禁、SHA冻结、单次执行、C结果分析与报告。
- `system-prompt.txt` / `protocol-v1.json`：冻结语义规则与验收方案，不包含候选标签或每题预设答案。
- `run.ps1`：本机PowerShell7入口，授权和密钥只走交互/私有stdin。
- Java/Python/PowerShell测试：仅Mock及离线验证，无真实外部调用。

```powershell
pwsh -NoProfile -File .\tools\rag-p4\run.ps1 -Mode Offline
pwsh -NoProfile -File .\tools\rag-p4\run.ps1 -Mode Verify
# 后续一次性明确授权后，在本机交互控制台执行：
pwsh -NoProfile -File .\tools\rag-p4\run.ps1 -Mode ExecuteOnce
# 只读派生分析，不再发请求：
pwsh -NoProfile -File .\tools\rag-p4\run.ps1 -Mode Analyze
# 填写单独语义复核副本后，只读计算冻结验收，历史results不修改：
pwsh -NoProfile -File .\tools\rag-p4\run.ps1 -Mode Analyze -SemanticReview .\target\rag-p4\my-semantic-review.json
```

密钥使用P4_DEEPSEEK_API_KEY/DEEPSEEK_API_KEY，缺失时隐藏输入，不写入文件或日志。执行一次后保留target/rag-p4/run-once，失败也不删除、不重试、不恢复。没有授权时不要运行ExecuteOnce。Offline无需Docker或数据库。使用已有JDK21及缓存依赖，编译到独立target/rag-p4/offline/classes。

最多12次DeepSeek请求，输入原题+Rerank Top2完整正文及标题，不先做0.50筛选、不取Rank3补位。格式、身份、引用、版本失败一律空证据并停止后续批次；不回退固定Top2。引用验证证明原文存在，不能证明模型语义判断正确。全部24个候选的真实判断及核心点拆分必须另行核验。

报告与浏览页面：target/rag-p4/prepared/report.md、review.html。真实结果：target/rag-p4/run-once/results.json，各题request/response/decision，comparison-semantic.json及独立semantic-review-template.json。这些12题现在是开发集；不宣称P4独立泛化或直接接生产。原P3标签和审计保留，AI修订9条严格标记USER_AUTHORIZED_AI_CORRECTION。

复核时保留模板，另存填写副本，指定reviewSource=HUMAN_PER_ITEM、reviewer、全部12题核心拆分是否正确（boolean）、全部24条humanCoreSupport（boolean）及reason。不得把AI建议填写成人工确认。验收结果按该复核文件SHA保存在run-once/quality-reviews；核心误准入、保留率和冻结验收自动计算。未复核不会自动质量PASS；通过状态仅PASS_DEVELOPMENT_SET_ONLY。原0/1/2标签指标始终保留PROVISIONAL来源。
