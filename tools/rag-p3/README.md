# RAG P3：动态证据准入与独立验证准备

`P3_ADMISSION_PREP=READY`。独立工具已编译并通过离线Unit/Mock与PowerShell入口测试。当前**新题真实调用0、数据库/Milvus服务连接0、写入0**；尚无新题泛化结果，生产默认策略未修改。

## 16题纯离线对照

来源是已保存且复验通过的P2：16/16、0重试、42966 Token。人工10条与AI建议70条均原样保留，以下为**IN_SAMPLE_EXPLORATORY / COMBINED_PROVISIONAL**：

| 策略 | 保留 | 有用1/2 | 直接2 | 无关0 | 覆盖误拒 | 库外正确拒绝 |
|---|---:|---:|---:|---:|---:|---:|
| A 原Milvus Top2 | 32 | 21 | 14 | 11 | 0/12 | 0/4 |
| B Rerank Top2 | 32 | 23 | 15 | 9 | 0/12 | 0/4 |
| C Rerank Top2 + score≥0.50 | 23 | 23 | 15 | 0 | 0/12 | 4/4 |
| D Rerank Top2 + 低信息诊断 | 32 | 23 | 15 | 9 | 0/12 | 0/4 |

C相对固定Rerank Top2保留100%的有用与直接候选，删掉9条暂定无关候选。其中8条来自库外题，另1条是Q08第二条（约0.418）。以原Top5所有27条有用候选为分母，C的保留率为23/27=85.19%，误删率4/27=14.81%；直接相关15/15=100%。剩下4条有用候选是Top2本身没有选到的，不能把候选截断误报为全部召回。

C动态数量为0条4题、1条1题、2条11题。D没有改善本批Top2，说明引用列表诊断不能排除Q08那种有解释正文、却不真正解决题面目的的Chunk。

Q02第一条人工标签1、分数约0.936，仍可准入。相关/有用不等于整题充分：代码始终保留`NOT_ASSESSED`，不把高分或标签1变成完整答案依据。新题另收集人工`SUFFICIENT/PARTIAL/NONE/U`。

HUMAN_ONLY、AI_ONLY_PROVISIONAL、COMBINED_PROVISIONAL三视图保存在`comparison.json`。人工视图仅2题10条，没有库外题评价资格。分数扫描仅发生在旧P2（0.01～0.95若干候选），明确标记样本内探索，不扫描新holdout阈值。

## 预先锁定的规则与题集

主要实验规则C：只检查原Rerank前2条，保留`relevanceScore >= 0.50`者，最多2条，全部不通过返回空列表；不从Rank3补齐，也不保证每题至少1条。分数不是跨问题校准概率。D作为预定独立对照使用P2参考列表诊断，首轮不新增语义模型、不将两规则组合后临时挑优。

题集阅读版：[questions-v1.md](questions-v1.md)。原始题面、逐题SHA和覆盖假设在`queries-v1.json`；覆盖依据/相邻但不解题的文档段落在`coverage-review-v1.json`。

| 覆盖6题 | Hard Negative 6题 |
|---|---|
| C01 LinkedList反复下标定位与已有游标 | N01 Redis Cluster多key Lua、CROSSSLOT/hash tag |
| C02 JDK8u ConcurrentHashMap空桶CAS与桶头复核 | N02 InnoDB范围锁、gap/next-key与插入等待 |
| C03 volatile发布普通字段及顺序边界 | N03 ZGC彩色指针与load barrier重定位 |
| C04 Spring默认checked exception与rollback-only | N04 不同ClassLoader的同名类身份 |
| C05 RR第一次一致性读快照与FOR UPDATE | N05 响应式事务的Reactor Context与订阅 |
| C06 Redis Sorted Set成员唯一与同score | N06 Linux TCP_USER_TIMEOUT与keepalive计时 |

独立性是**在新检索前根据全部21份冻结文档核查**，不是独立人工审查。六道负例是相同技术领域中的未覆盖机制，不从P2低分Chunk拼题；缺少关键词只是辅助检查，另有语义边界理由。新题与此前56项已保存题面进行精确重复检查，并记录最近文本相似度。覆盖假设仍须人工核对，不能称为新题人工Ground Truth；若人工发现覆盖分类有问题，记录challenge并阻断正式PASS，不看结果后自动换组。

固定SHA256：

```text
queries-v1.json         c32f89f36f1e636440bc33bb4489089ba2eebe3998c4093ed0e649ac72f675e8
admission-v1.json       5bd12e04cfa981523016c066c66df117fe29cabef1e5bfed553bf4b9400d498a
protocol-v1.json        b14d721d2a977e434a2feac5548bf844dd771cb66ee476e50eea5a23b73b9c64
coverage-review-v1.json 12821ea71274c3677cc8d89a8d9da87b2c520bd39cb60947725372d9cd7d1b60
freeze.json            7137961d0ab2a8514e08df4a28b8f61cc3a14eeae20fe544e561e41aee8c2565
```

四份core JSON有各自`.sha256`文件；freeze把core、876份历史文件、21份语料源文档和费用计划绑定。入口拒绝任何原地改参/改题及历史证据变化。运行后不能修改规则再把这批题当未见过的验证集。

## 真实入口（本轮未执行）

离线准备、编译及测试：

```powershell
pwsh -NoProfile -File "E:\learning\IDEA_PROJECTJ\ai-interview-system\tools\rag-p3\run.ps1" -Mode Offline
```

**获得后续一次性授权后**，由用户在本机交互式PowerShell执行：

```powershell
pwsh -NoProfile -File "E:\learning\IDEA_PROJECTJ\ai-interview-system\tools\rag-p3\run.ps1" -Mode ExecuteOnce
```

先复验冻结文件和离线测试源代码SHA，再读取已有Key，隐藏输入既有`rag_e2e_writer@127.0.0.1`密码。Workspace默认复用已复验P2北京空间；可由`P3_RERANK_WORKSPACE_ID/P2_RERANK_WORKSPACE_ID/DASHSCOPE_WORKSPACE_ID`提供。核对新题覆盖审查和费用后，输入：

```text
AUTHORIZE P3 12 EACH ONCE
```

非交互/重定向输入被拒绝。没有force、重试、删锁或续跑入口。P3只创建自己的原子`run-once`与锁，不修改P0/P1/P2锁。失败/中断仍占用已保留次数，停止后保留所有账本。

Embedding固定北京兼容地址`https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings`，模型`qwen3.7-text-embedding`、1024维、float、一题一个input。Rerank使用P2已验证的Workspace原生接口，模型`qwen3.7-text-rerank`、top_n=5、原题面+原标题/完整正文，无覆盖标志、标签或参考答案。

Embedding与Rerank均用**原题面**。这与P1/生产带合成评分点的Embedding查询不完全相同；A～D共享同一次候选，可比较此次查询流程内的准入效果，但不能据此声称生产QueryBuilder已完成等价验证。后续Java接入仍须一个单独、可审查的小增量。

最多12次Embedding + 12次Milvus Search + 12次Rerank；DeepSeek/Jev均0。先完成12题只读检索与冻结复验，再对这些候选执行12题重排。SQL复用P1固定SELECT白名单、服务器`START TRANSACTION ... READ ONLY`、REPEATABLE_READ与rollback-only，固定测试schema/账号/server UUID；Milvus仅describe/query/search，校验Collection ID、Schema/index、52条全向量SHA、MySQL映射；不初始化、不load、不写入。前后快照必须匹配191行/52向量。只读元数据/快照Query是额外诊断RPC，不计作12次相似度Search。

HTTP/JDK连接与方法重试禁用，Milvus SDK`maxRetryTimes=1`且不重试限流；不跟随重定向，不替换模型或自动换Key再请求。凭据只通过私有子进程stdin传入，不写argv/文件/User或Machine环境；子进程环境白名单清除无关数据库/模型/代理配置，输出脱敏。托管字符串不承诺可验证的内存擦除。

## 费用与延迟

北京Embedding与Rerank公开输入原价均¥0.50/百万Token：[Embedding官方价格](https://help.aliyun.com/zh/model-studio/qwen3-7-text-embedding)、[Rerank官方价格](https://help.aliyun.com/zh/model-studio/qwen3-7-text-rerank)，2026-10-13核对。无免费额度假设，不新增计费模型。

P2按12/16缩放的Rerank费用参考约¥0.01611225；新候选长度未知，不能当作实际费用。原题面UTF-8保守代理（含framing reserve）6688，候选按每条最多8192 UTF-8 bytes预检的Rerank最坏代理529460，合计原价代理**¥0.268074**。授权预算参考**¥0.50**；这些代理不是实际Token或账单硬上限。

各模型请求前检查已知费用与剩余保守输入；响应记录usage、请求ID及延迟，未知收费不能写0。费用按实际Embedding total_tokens与Rerank prompt_tokens分别核算公开原价，实际账单另查控制台。延迟保存Embedding、检索/水合、Rerank及总服务工作时长；工作时长不包含批阶段等待及冻结资源门禁，不混称交互端到端延迟。

## 已锁定的独立验收

protocol-v1.json预先固定：12题全部执行完整，0重试，资源/历史不变；人工完成全部60条候选的0/1/2标签、12题覆盖确认和12题主要策略证据充分性判断，才允许正式质量结论。AI建议另存来源，只用于探索。

主要准入验收：覆盖题有用命中6/6、直接证据命中6/6；库外正确返回0条6/6；无关Chunk准入0；有用和直接证据相对固定Rerank Top2保留率均≥90%；每题0～2条。另报告相对Top5的有用误删/直接保留、准入无关率及逐题配对差异。

`P3_EXECUTION_RESULT=PASS`只表示模型/检索执行完整。未标注时`P3_QUALITY_RESULT=PENDING_HUMAN_HOLDOUT_LABELS`。人工充分性独立报告，不由分数自动决定；部分有用证据只可称为部分支持。通过时结论限定`PASS_ON_THIS_12_CASE_HOLDOUT_ONLY`，仍不自动接生产默认策略或声称广泛泛化。

一次失败必须如实报告`FAIL_UNDER_PRELOCKED_RULE`；如果简单规则明显失效，才在另一个授权阶段研究独立语义充分性判断模型。本轮不引入Agent、Judge或框架。

执行后导出/填写单独`labels-template.json`，保留`humanLabels`与`aiSuggestions`分开，再离线评估：

```powershell
pwsh -NoProfile -File "E:\learning\IDEA_PROJECTJ\ai-interview-system\tools\rag-p3\run.ps1" -Mode Analyze -Labels "本机单独导出的标签JSON绝对路径"
```

模板不会从分数自动标注。Analyze核验请求/响应/结果SHA、索引映射、usage、冻结绑定，原执行结果不改写；各标签版本报告另存`quality-reviews/<标签SHA前16位>/`。

## 新文件与输出

| 新文件 | 用途 |
|---|---|
| queries-v1.json / questions-v1.md | 原题面机器版与阅读版、每题SHA、6/6假设 |
| coverage-review-v1.json | 引用段落、文档SHA、全21文档辅助检查及审查来源 |
| admission-v1.json / protocol-v1.json / 四份.sha256 | 参数与预定验收/费用/模型合同锁定 |
| p3.py | 原P2复验、离线指标、冻结门禁、真实编排、标签来源和报告 |
| P3ReadOnlyRetrieval.java | 独立新题检索，复用现有只读/Schema/资源SHA保护 |
| test_p3.py / P3ReadOnlyRetrievalOfflineTest.java | 新增离线Unit/Mock验证，不执行旧P1 runner |
| run.ps1 / test-entry.ps1 | 缓存编译、交互授权、入口验证 |
| README.md / VERIFICATION.md | 运行与真实状态记录 |

无既有源码/配置/数据文件改动，无Schema修改，无Git stage/commit/push。使用已有Java21、Python标准库、SDK与缓存Maven依赖；不启动Docker，不下载新框架。

输出根目录`E:\learning\IDEA_PROJECTJ\ai-interview-system\target\rag-p3\`：

- `prepared/`：freeze、manifest、16题comparison/旧分数扫描、report.md、review.html。
- `offline/`：Java/Python/PowerShell测试摘要、编译类与Mock夹具。
- `run-once/`：仅后续手动授权创建；新题Embedding/Milvus/Rerank原响应、执行锁、usage/费用/延迟与完整60候选。当前不存在。
- `quality-reviews/`：独立标签版本的指标与质量报告。
