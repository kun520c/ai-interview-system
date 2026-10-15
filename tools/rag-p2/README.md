# RAG P2：独立语义重排序实验入口

`P2_RERANK_PREP=READY`，表示接口适配、原数据校验、离线对比和入口门禁已通过验证。真实 Rerank 尚未调用，Workspace ID 待交互输入，Key 的空间/模型权限待真实请求验证；没有真实质量提升结论。

本工具只读已有16题80条候选。本轮不运行P0/P1检索、Embedding、Milvus Search、DeepSeek、Jev或业务E2E，不启动Spring或Docker，不连接数据库。生产Java、配置、MySQL语料与Milvus向量均不修改。

## 本机命令

无需新依赖；使用现有PowerShell 7与Python标准库，不改变执行策略、安全设置或系统环境。

离线准备、测试与报告生成：

```powershell
pwsh -NoProfile -File "E:\learning\IDEA_PROJECTJ\ai-interview-system\tools\rag-p2\run.ps1" -Mode Offline
```

**后续获得一次性授权后**，由用户在本机交互式PowerShell中运行：

```powershell
pwsh -NoProfile -File "E:\learning\IDEA_PROJECTJ\ai-interview-system\tools\rag-p2\run.ps1" -Mode ExecuteOnce
```

入口先核对已准备的全部数据及请求SHA，显示模型、确切北京URL、费用参考和请求文件。未配置时会提示输入北京Workspace ID，随后输入授权短语：

```text
AUTHORIZE P2 RERANK 16 ONCE
```

不提供跳过交互的授权参数。重定向输入/非交互进程被拒绝。Python执行端同时校验授权与计划SHA、通过的离线测试以及四个源脚本SHA；修改脚本后必须重新执行Offline。

执行后的纯离线复算：

```powershell
pwsh -NoProfile -File "E:\learning\IDEA_PROJECTJ\ai-interview-system\tools\rag-p2\run.ps1" -Mode Analyze
```

Analyze核对保存结果SHA、请求正文、响应SHA、索引映射、usage与费用后生成报告，无HTTP。

## 接口、Workspace与认证

固定模型：`qwen3.7-text-rerank`。正式原生接口：

```text
POST https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank
Authorization: Bearer <Bailian API Key>
Content-Type: application/json
```

Workspace ID须取自**北京地域业务空间管理**页面，Key须拥有对应业务空间和模型权限；不能把显示名、控制台URL、Embedding URL或API Key当作Workspace ID。本机检查发现未配置Workspace ID，已有Key存在，但本轮未请求模型验证其权限。可在运行入口直接输入ID；已有ID变量读取顺序为`P2_RERANK_WORKSPACE_ID`、`DASHSCOPE_WORKSPACE_ID`，各自先Process再User。

Key读取顺序为`P2_RERANK_API_KEY`、`DASHSCOPE_API_KEY`、`EMBEDDING_API_KEY`，各自先Process再User；不存在时隐藏输入。这只是同一百炼模型的认证来源选择，不更换付费模型。不会读取本地YAML或修改User/Machine环境。Key只通过私有子进程stdin传入，不放argv、文件或日志。子进程环境白名单清除无关数据库、模型、代理与Python配置。使用后清理引用/SecureString/BSTR，不承诺托管字符串的可验证内存擦除。

请求严格采用官方的嵌套HTTP格式：

```json
{
  "model": "qwen3.7-text-rerank",
  "input": {
    "query": "原始面试问题完整题面",
    "documents": ["原标题\n\n完整原Chunk正文，共五条"]
  },
  "parameters": {
    "top_n": 5,
    "instruct": "Given a web search query, retrieve relevant passages that answer the query."
  }
}
```

示例documents仅示意；实际requests.json每题有5个完整字符串。Query不使用含评分点的旧Embedding查询。候选正文完整保留，包括原文自身已有的标题/元数据；不增加标签、评价、参考答案或覆盖标志。此模型文档未支持`return_documents`，所以不发送该字段；全文本地保留。

成功响应通过`output.results[].index`关联原候选；要求恰好5条、index 0～4唯一、分数有限且在0～1并降序、request_id存在、prompt_tokens/total_tokens可信。原Rank、COSINE/Float32 bits、vectorId、chunkId和正文SHA均保留。分数相同时保留提供商顺序，不自行排序。

接口、价格、空间与认证按2026-10-13核对：[官方Rerank API](https://help.aliyun.com/zh/model-studio/text-rerank-api)、[模型信息与北京价格](https://help.aliyun.com/zh/model-studio/qwen3-7-text-rerank)、[长度计算规则](https://help.aliyun.com/zh/model-studio/rerank)、[Workspace与API Key](https://help.aliyun.com/zh/model-studio/first-api-call-to-qwen)。官方说明relevance_score主要用于同一次请求内排序，不能直接当跨问题绝对准入分数。

## 调用、费用与失败门禁

- 最多16次HTTPS POST；16题各一次；每题返回全部5条；顺序执行，无连接/状态码重试，无重定向，无模型降级。
- 北京输入原价¥0.50/百万Token；不假设免费额度或优惠。授权预算参考¥0.50。
- 完整输入的保守UTF-8字节代理为180489：逐候选重复Query/instruct，并为每对保留256的framing余量。费用代理¥0.0902445，**不是实际Token计数，也不是保证的账单硬上限**。不调用额外Tokenizer API。
- 每次请求前检查已知费用与剩余字节代理；实际prompt_tokens×0.50/1000000估算原价，另存total_tokens。实际账单需控制台确认。未知usage、超时或失败可能收费，完整费用标记不可信，不能当作0费用。
- 30秒socket超时；每次请求前持久化intent和请求SHA；固定run-once目录原子创建，一次失败即停，未开始题保留状态。401/403/模型不可用明确阻断，不自动换Key再请求或切换模型。
- 目录保留即拒绝再次执行。没有force、续跑或删锁入口。不要删除目录后重复收费；后续若需处理失败，先人工核对已收费记录并制定新授权范围。

## 对照与指标

固定比较A原Milvus Top5、B原Top3、C原COSINE≥0.70、D COSINE≥0.60历史对照、E/F/G Rerank Top1/2/3。COSINE按原Float32解码后的值与Java double阈值比较，不四舍五入、不修改原Rank。

三种标签视图分别输出：HUMAN_ONLY仅10条/2题；AI_ONLY_PROVISIONAL为70条/14题；COMBINED_PROVISIONAL为80条/16题。人工JSON的label/note逐字保留，70条AI建议从旧HTML读取并与旧Markdown、Rank、Chunk ID、题面和全文核对。用户确认AI验证用于本阶段探索，来源仍为AI_SUGGESTION，不提升为人工Ground Truth。

指标包含直接/部分/无关分布、useful/direct precision、原Top5候选内useful recall、有用误删、12题覆盖命中、4题库外误准入、nDCG@3逐题配对胜/负/平、label2进入Rank1/2、label0升降。未标注候选不当作0；HUMAN_ONLY没有库外题评价资格；全不相关题不计nDCG而单独统计误准入。

报告比较独立二分类/证据充分性判定、Top3语义检查、低信息Chunk诊断与动态0～3条输出。诊断标志不改变本轮5条模型输入。`admitted_evidence`仅提供离线研究接点：输入独立布尔检查，无标签/覆盖状态oracle，缺失检查拒绝、通过最多3条。它未接生产，也未调用第二模型。

不做同批反复调参后的泛化宣称。正式提升仍需新的独立问题和人工标签。执行`P2_RERANK_RESULT=PASS`只表示16题响应、映射、usage完整，质量结论独立保留PROVISIONAL。

## 文件与输出位置

| 新文件 | 职责 |
|---|---|
| p2.py | 原输入/标签读取、正式HTTP适配、不可重跑账本、费用与质量指标、报告/HTML |
| run.ps1 | 本机交互授权、空间/Key输入、私有stdin与子进程环境隔离 |
| test_p2.py | 数据绑定、请求泄漏、响应映射、指标、预算、失败停止/禁重跑、报告转义的离线Unit/Mock测试 |
| test-entry.ps1 | PS7语法、Verify模式、非交互授权拒绝、拒绝后无真实目录的入口测试 |
| README.md | 运行合同、接口与费用说明 |
| VERIFICATION.md | 本轮准确测试记录与限制 |

代码仅新增以上文件，无既有文件改动。当前仓库其他未提交改动保留。未staged、未commit、未push。

输出根目录：`E:\learning\IDEA_PROJECTJ\ai-interview-system\target\rag-p2\`（既有target忽略规则覆盖）：

- `inputs/`：五份旧证据的原字节副本，不覆盖旧文件。
- `prepared/manifest.json`：原位置/副本位置/完整SHA、计划SHA与权限未验证状态。
- `prepared/requests.json`：16个确切请求与index/原Rank/vectorId/chunkId映射。
- `prepared/labels.json`：10条原人工、70条AI建议与来源定位。
- `prepared/analysis.json`、`report.md`、`review.html`：已有A～D指标，E～G等待真实结果，80条全文审查。
- `offline/test-results.json`、`powershell-tests.json`、`run.log`：离线验证记录。
- `run-once/`：仅后续实际授权创建；全部请求、原始响应/必要脱敏响应、results.json/results.sha256、analysis.json、report.md、review.html。当前不存在。

不要将完整真实证据目录误认为Git已提交交付；本阶段未执行任何Git发布。
