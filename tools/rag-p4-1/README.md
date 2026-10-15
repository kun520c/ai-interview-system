# P4.1 部分核心机制支持

只新增独立tools/rag-p4-1及target/rag-p4-1，不修改旧P3/P4 Prompt、结果、冻结清单、执行锁或默认生产RAG策略。复用已经修复的SemanticEvidenceGate及P4DeepSeek.capture/evaluate。

规则调整在system-prompt.txt：引用能直接支持至少一个具体核心机制就保留；摘要、简短机制结论及包含参考列表的Chunk不自动降级。拆分最小机制点，避免把未支持整题误作完全无价值，也避免从相反条件/其他Chunk推导支持。仅同主题没有请求机制则背景；版本和引用要求保持严格。无题号/关键词特判。

```powershell
pwsh -NoProfile -File .\tools\rag-p4-1\run.ps1 -Mode Offline
pwsh -NoProfile -File .\tools\rag-p4-1\run.ps1 -Mode Verify
# 获得后续新费用授权后，在本机交互控制台一次性执行：
pwsh -NoProfile -File .\tools\rag-p4-1\run.ps1 -Mode ExecuteOnce
pwsh -NoProfile -File .\tools\rag-p4-1\run.ps1 -Mode Analyze
```

真实计划固定3题C01/N02/N06，各次同时评估原始Top2，最多3次DeepSeek。属于新版微验证，不能用旧P4授权短语，不是补跑旧7题。独立freeze及run-once、意图锁和HTTP尝试锁；失败空证据并停批、无重试/续跑/删除锁；原P4仍FAIL。

DEEPSEEK_API_KEY或P41_DEEPSEEK_API_KEY读取Process/User，缺失时隐藏输入；只走private stdin，不写入文件、命令行或输出。费用及封存输入在target/rag-p4-1/prepared/plan.json；输入白名单只有questionId/question/candidateId/title/content，不含标签、来源、机制评审、预期覆盖或分数。

development-review-v1.json为AI工程师离线评审PROVISIONAL，不输入在线模型；报告区分其推断、Mock结果、历史真实响应回归及未验证的新Prompt效果。Java只验证结构、逐字引用和声明的版本状态，不能证明机制蕴含及真实版本兼容。后续3题即使结构符合，也须审核语义；不自动宣称完整质量PASS或泛化成功。
