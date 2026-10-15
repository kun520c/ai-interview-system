P4_2_EXECUTION_PREP=READY

2026-10-14已完成12项精确增量归因、受控编译和离线验证；真实收费模型调用0、真实8题ExecuteOnce未运行。完整逐项旧/新SHA、来源、文件用途及验证范围见 `docs/RAG_P42_EXECUTION_PREP_20261014.md`。

原1975项冻结及旧准备证据保留：1963项逐项匹配，12项仅接受此次已核验的字节。旧freeze.json SHA为 `5309ba13666879387a2c876c98deaf2dd2a34003de26fc83169a40cff014d064`；8题SHA `73ca5d65649a0d2270a3f6cc209f36a0d2c775e7e43e4079f1f269a2e1ba318c`；判据计划SHA `16df13f6f7ed35968b6aa81b4f6504df36c4d07aabeee69a30b2742b7089fd47`。

新增精确增量位于 `target/rag-p4-2/execution-prep-20261014/evidence/delta.json`，SHA为 `4359a5f12384f6f79a3fc66b6ac0bdbbcba8d5b56be2c08a1f2f3277d0845222`，固定在Java/Python源码。新增执行封存为同一新目录下 `manifest.json` / `manifest.sha256`，绑定增量、新执行源码、完整运行class清单、缓存Jar、Mock回放及测试证据。12项名单内文件再改变任何字节也会拒绝；原1963项门禁没有豁免。原prepare.py的历史严格Verify保持原行为，新入口使用增量校验，不重新Freeze。已有manifest后Offline禁止覆盖；保存证据和已消费锁禁止删除或重写。

已验证83项：原RAG核心Mock 25、执行器24、Python Analyze/入口/SHA 34；failure/error/skipped全部0。4个class旧/新受控编译与冻结/当前SHA均精确相等，新增ProxySettings.class也精确复现。jdeps检查40个实际执行相关class，无缺失依赖和Jev引用。没有运行Maven全量回归；这些结果不代表真实服务或8题语义质量已通过。

独立运行器不启动Spring业务应用：Embedding用固定原题，Milvus经P3只读资源加载器，Rerank用冻结Top5排序取Top2，Gate用冻结合同和Prompt。GenericApplicationContext只做环境隔离initialize、不refresh、不加载application.yaml；没有Jev/答案评价/provider降级路径。运行时不读取覆盖审计、人工标签或预期答案。

取得本次独立费用授权后，唯一真实ExecuteOnce命令（本轮未执行）：

```powershell
pwsh -NoProfile -File "E:\learning\IDEA_PROJECTJ\ai-interview-system\tools\rag-p4-2\execute.ps1" -Mode ExecuteOnce
```

入口要求本机交互式PowerShell。凭据优先读取现有Process/User环境变量，不存在时隐藏输入：`P42_EMBEDDING_API_KEY`（或`EMBEDDING_API_KEY`/`DASHSCOPE_API_KEY`）、`P42_RERANK_API_KEY`（或原P3/P2 Key）、`P42_DEEPSEEK_API_KEY`（或`DEEPSEEK_API_KEY`）。读取已有 `rag_e2e_writer@127.0.0.1` 密码，无root回退；Milvus可选 `E2E_MILVUS_TOKEN`。北京Workspace默认复用已保存P2身份，也可显式提供 `P42_RERANK_WORKSPACE_ID`。不自动尝试多个Key。凭据仅经私有子进程stdin传递，不保存至argv、证据文件或新环境配置。

授权短语必须完全一致：

```text
AUTHORIZE P4.2 8 EACH ONCE MAX24 CNY1
```

每题上限：1 Embedding、1 Milvus Search、1 Rerank、1 DeepSeek Semantic Gate。整批上限：24次收费模型调用、8次相似度Search；重试0、备用收费模型0、答案评价/Jev/业务写入0。启动及结束的只读SQL、Collection描述和向量快照RPC另计为资源保护，不是额外相似度Search。模型请求固定为 `qwen3.7-text-embedding`（1024维）、`qwen3.7-text-rerank`、`deepseek-v4-flash`，Gate输出最多3072 Token、thinking disabled、JSON object、非流式。DeepSeek返回别名按冻结客户端合同验证并原样记录，不改变请求模型。

预算参考¥1.00，冻结原价代理¥0.707392。采用冻结计划中的Embedding/Rerank输入¥0.50/百万Token和DeepSeek高峰无缓存输入¥2、输出¥8/百万Token。这是冻结费用估算，**不是云服务账单绝对硬上限，也不承诺UTF-8字节代理是Token上界**。运行前检查凭据、全批代理及原冻结；每次发送前检查已知费用加剩余代理。usage缺失/非法、输入usage超出冻结代理、输出超过3072或组合预算不安全立即停止，当前已发送请求可能仍产生费用。费用按保存的实际usage及冻结费率估算，最终账单应以云控制台为准。

MySQL/Milvus资源门禁复用P3只读加载器：固定测试库、账号、server UUID、Collection ID、Schema/index、191行指纹及52向量SHA；SQL白名单、服务器READ ONLY和rollback-only；无Collection初始化/加载/修改。 before/after均保存，结束检查失败不得标记执行完成。

执行输出固定在 `target/rag-p4-2/run-once`。以CreateNew保留批锁和每题每阶段attempt锁；请求/响应、HTTP状态、模型、Token、费用、原始SDK候选/向量ID、MySQL水合内容、Rerank全5排序与分数、Gate判断/引文/本地偏移验证、最终准入和分阶段耗时都存档，最终 `artifacts.json` 绑定全部文件SHA。原始服务响应如回显秘密会脱敏、保存原字节SHA并停止。HTTP响应超过1MiB安全边界时保存已捕获前缀、前缀SHA及 `responseCaptureComplete=false` 后停止，不能把前缀SHA称为完整响应SHA。失去响应的已保留尝试报告为可能已发送、费用未知，不写0。失败后不会续跑、回放、补题、删锁或生成质量PASS。

离线Analyze入口（只读取保存证据，不发送收费请求）：

```powershell
pwsh -NoProfile -File "E:\learning\IDEA_PROJECTJ\ai-interview-system\tools\rag-p4-2\execute.ps1" -Mode Analyze
```

它复验冻结、执行锁、文件SHA、请求内容、Rerank索引/分数、Gate结构化判断和本地准入结果，再报告尝试数/已返回响应数/失去响应的尝试、usage、分阶段median/p95和整批耗时、费用、逐题缺失点及停止原因。没有独立标签时为 `PENDING_INDEPENDENT_HUMAN_REVIEW`，不计算质量指标。异常退出未完成最终SHA封存时为 `INCOMPLETE_UNSEALED_AFTER_INTERRUPTION`，仅报告执行状态。

完整运行后生成 `target/rag-p4-2/analysis/execution-only/review-template.json`，无自动预测标签。独立人工先在未见模型预测的条件下确认8题覆盖类别、全部40个原始候选的CORE_SUPPORT/BACKGROUND_ONLY/NO_SUPPORT/UNCERTAIN、主要要求ID与版本条件；冻结这些标签后再核对已准入引文的真实蕴含，填写 `gateAssessment=CORRECT/INCORRECT`。保持AI建议独立存放，不允许其替代人工。模板要求审查姓名、独立/盲审声明、候选正文SHA、题集SHA和本次证据SHA；无法仅凭机器校验证明审查者确实独立。

用模板副本填写，原执行证据保持只读，然后：

```powershell
pwsh -NoProfile -File "E:\learning\IDEA_PROJECTJ\ai-interview-system\tools\rag-p4-2\execute.ps1" -Mode Analyze -Labels "人工审查JSON的绝对路径"
```

审查报告按完整标签SHA另存 `target/rag-p4-2/analysis/<标签SHA>/analysis.json`。冻结验收为覆盖题主要机制证据4/4、库外正常正确拒绝4/4、错误准入0、相对固定Rerank Top2的独立主要机制证据保留率≥90%、引文/ID/版本检查通过。SAFE_EMPTY错误/不确定不能算库外成功；分母0或审查不确定为INCONCLUSIVE；覆盖假设被挑战为BLOCKED。逐题报告缺失主要机制、误拒、误准入和引文判断错误，并对照Milvus Top2、Rerank Top2、Gate（0.50仅保留为原历史对照规则，不在本批调参）。失败保留为 `FAIL_UNDER_PRELOCKED_RULE`；通过也只称 `PASS_ON_THIS_8_CASE_HOLDOUT_ONLY`，不意味着整题充分或生产泛化已验证。

当前尚未验证：原定8题真实API兼容性与组合语义、真实延迟/usage/账单、独立人工质量指标。执行准备已READY，没有未解释SHA阻断；没有Git stage/commit/push。后续真实调用需要新的独立费用授权，历史Jev/E2E授权不能复用。
