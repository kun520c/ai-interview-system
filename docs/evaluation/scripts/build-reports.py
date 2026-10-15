"""Summarize recorded real results without changing production code or tuning parameters."""
import json
import re
import statistics
from datetime import datetime, timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'docs/evaluation'
E=OUT/'evidence'
D=OUT/'data'
def read(name): return json.loads((E/name).read_text(encoding='utf-8'))
def emit(name,text): (OUT/name).write_text(text.strip()+'\n',encoding='utf-8')
def table(headers,rows):
    escape=lambda x:str(x).replace('|','\\|').replace('\n','<br>')
    return '\n'.join(['| '+' | '.join(headers)+' |','| '+' | '.join(['---']*len(headers))+' |']+
                     ['| '+' | '.join(escape(v) for v in row)+' |' for row in rows])

inventory=read('inventory.json')
pipeline=read('pipeline.json')
original=read('rag-existing.json')
expanded=read('rag-expanded.json')
judges=read('judge-cases.json')
first=read('e2e-first.json')
second=read('e2e.json')
recovery=read('recovery.json')
timestamp=datetime.now(timezone.utc).isoformat()

def metrics(traces):
    positives=[r for r in traces if not r['case']['negative']]
    negatives=[r for r in traces if r['case']['negative']]
    covered=[r for r in positives if r['case']['expectedRelevantChunkIds']]
    ranks=lambda r:[x['chunkId'] for x in r.get('result',{}).get('evidence',[])]
    hit=lambda r,k:bool(set(ranks(r)[:k]) & set(r['case']['expectedRelevantChunkIds']))
    rr=lambda r:next((1/(i+1) for i,x in enumerate(ranks(r)) if x in r['case']['expectedRelevantChunkIds']),0)
    result=dict(queries=len(traces),positives=len(positives),coveredPositives=len(covered),negatives=len(negatives),
                queryErrors=sum(r['status']!='EXECUTED' for r in traces),
                negativeFalsePositiveRate=sum(bool(ranks(r)) for r in negatives)/len(negatives),
                mrrIncludingMissingCorpus=sum(rr(r) for r in positives)/len(positives),
                mrrCovered=sum(rr(r) for r in covered)/len(covered))
    for k in (1,3,5):
        result[f'hit@{k}AllPositive']=sum(hit(r,k) for r in positives)/len(positives)
        result[f'hit@{k}Covered']=sum(hit(r,k) for r in covered)/len(covered)
        result[f'macroRecall@{k}Covered']=sum(len(set(ranks(r)[:k]) & set(r['case']['expectedRelevantChunkIds']))/
                                           len(r['case']['expectedRelevantChunkIds']) for r in covered)/len(covered)
        result[f'precision@{k}Positive']=sum(sum(x in r['case']['expectedRelevantChunkIds'] for x in ranks(r)[:k])/
                                          max(1,len(ranks(r)[:k])) for r in positives)/len(positives)
    return result

existing_metrics=metrics(original)
expanded_metrics=metrics(expanded)
(E/'rag-metrics.json').write_text(json.dumps(dict(existing=existing_metrics,expanded=expanded_metrics),indent=2),encoding='utf-8')
pct=lambda value:f'{100*value:.1f}%'

run_rows=[['Inventory 首次启动',1,0,1,0,'配置未装配 Milvus Bean；启动问题，非检索失败'],
          ['Inventory 明确启用真实依赖后',1,0,0,0,'真实 MySQL / Embedding / Milvus'],
          ['RAG 原知识库（30 query）',1,0,0,0,'真实检索；case 质量另计'],
          ['Pipeline（6 文档、5 过滤情形）',1,0,0,0,'真实 HTTP / Embedding / Milvus / MySQL'],
          ['RAG 扩充语料（30 query）',1,0,0,0,'真实检索；case 质量另计'],
          ['Judge（17 case runs + 1 故障 + 3 消融）',1,0,0,0,'真实 DeepSeek；Jev 未测'],
          ['E2E 第一轮',1,0,0,0,'采集成功；1 个真实答案 HTTP 500'],
          ['E2E 第二轮',1,0,0,0,'采集成功；2 个真实报告 HTTP 502'],
          ['Recovery 首次采集',1,0,1,0,'测试脚本误用 session.overall_score；已修正为 total_score'],
          ['Recovery 修正后',1,0,0,0,'报告与 FAILED 答案均可恢复；不抵消初次失败']]
run_rows.append(['普通回归首次',1412,1,0,19,'新脚本留下空的DEEPSEEK_ENABLED；隔离测试解析为null，已改为真正删除环境项'])
for phase,label in [('focused','补充离线契约与 Validator'),('regression','普通全量回归')]:
    path=ROOT/f'target/evaluation/{phase}.log'
    if path.exists():
        counts=re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)',path.read_text(encoding='utf-8',errors='replace'))
        if counts: run_rows.append([label,*map(int,counts[-1]),'见 target/evaluation/'+phase+'.log'])
test_table=table(['执行','Tests','Failures','Errors','Skipped','实际含义'],run_rows)
(E/'test-runs.json').write_text(json.dumps(run_rows,ensure_ascii=False,indent=2),encoding='utf-8')

emit('ENVIRONMENT_CHECK.md',f'''
# 真实运行环境检查

日期：2026-10-04（Asia/Shanghai）。工作区基线：`20a933a`，包括开始时已有的未提交 Jev 接入改动。本轮报告生成时间：{timestamp}。

| 依赖 | 结论 | 实测证据 |
| --- | --- | --- |
| MySQL | PASS | JDBC 查询 `VERSION()` 为 8.0.45；真实 Mapper/事务、状态、评价和报告写入 |
| Docker / Milvus | PASS | 启动已有 milvus-etcd / milvus-minio / milvus-standalone，健康；SDK describe/search/insert |
| Embedding | PASS | qwen3.7-text-embedding；真实返回 1024 维、有限 float；文档与查询均真实生成 |
| DeepSeek | PASS（可用性） | 请求 deepseek-v4-flash，实际响应 model 为 deepseek-flash；答案与报告均真实调用。质量/结构失败见 E2E 报告 |
| Jev | BLOCKED | Process/User/Machine 环境和 local 配置均没有 Jev key。未做任何认证 Jev 推理；无凭据探测 HTTP 403，不能证明模型可用 |
| Spring Boot | PASS | Java 21.0.11 / Spring Boot 3.5.16；`@SpringBootTest(RANDOM_PORT)` 启动真实 Tomcat，通过 JDK HttpClient 发真实 HTTP，不使用 MockMvc |
| 原始知识覆盖 | FAIL | 开始时仅 1 READY 文档 / 1 ACTIVE 切片；5 道 EASY 主题仅 equals/hashCode 有相关知识 |

Collection 为 `knowledge_chunk_vectors_local`，默认库 `default`；五字段、dimension=1024、dynamic=false、autoID=false；索引 AUTOINDEX / COSINE，初始 indexedRows=totalRows=1。Schema 来源：[inventory.json](evidence/inventory.json)。

当前 agent 进程没有继承 DB_* 等用户变量。运行脚本只向子进程导入已有 Windows User-scope 配置，使用 ignored `application-local.yaml`。DeepSeek key 仅在本地配置指向官方 api.deepseek.com 时复用既有凭据，不打印、不写入报告或 Git 文件。Jev 未配置时只选择 DeepSeek 对照，不声称 Jev 已验证。

首次启动的 1 个 Error 是新脚本清除开关时留下空值，导致Milvus配置未装配；显式设置进程级 MILVUS_ENABLED=true 后真实检查通过。普通回归首次也因此将DEEPSEEK_ENABLED解析为null，导致1项隔离断言失败；脚本已用Remove-Item Env:真正删除变量，普通运行仅导入DB变量。没有改生产配置或测试断言。Jev 无凭据网络探测首次受沙箱 ProxyError 阻断，获准重试后 HTTP 403；没有把该探测当作推理成功。

## 执行记录

{test_table}

所有 AI baseline 测试均为 Maven/Surefire、`@ActiveProfiles("local")`、`@EnabledIfEnvironmentVariable` + `Assumptions.assumeTrue` 双层 opt-in。Maven 的一个采集测试包含多次真实调用；其成功只表示证据采集完成，不代表 AI cases 全部通过。

普通回归前清除 RUN_REAL_* 与 MILVUS_ENABLED，保留 application-test.yaml 的隔离配置；真实 Smoke Tests 和新 baseline collector 在普通运行中跳过。未改任何生产配置来迁就测试环境。

最终普通回归：1412项、失败0、错误0、跳过19；其中既有真实Smoke保护合计18项、新baseline collector1项。138项补充离线契约通过。[汇总数据](evidence/test-runs.json)。

## 本轮留下的本地数据

新文档 542–547、切片 484–490，均 READY/ACTIVE；原文档 485 / 切片 443 保留。五种临时过滤修改全部恢复：[post-run-corpus.json](evidence/post-run-corpus.json)。没有 drop、recreate 或 clear Collection，没有删除共享数据。

新增六个面试会话 2463–2468 及本轮合成用户/报告/弱项作为人工复核证据保留。2465 初次失败后只做一次同输入诊断恢复，当前仍 IN_PROGRESS；其余五个已 COMPLETED 且报告 READY（含需要恢复的 2466/2467）。合成密码/JWT 未写入证据。

本轮停止了测试 Tomcat；MySQL 及启动的 Milvus 容器保留运行。全部进程级 opt-in 开关随脚本清除，未修改用户/系统环境变量。
''')

groups={}
for row in judges: groups.setdefault(row['case']['id'],[]).append(row)
judge_rows=[]
case_details=[]
for cid,rows in groups.items():
    case=rows[0]['case']
    score_values=[r.get('javaScore',{}).get('totalScore') for r in rows]
    suggestions=[r.get('providerResult',{}).get('suggestion',{}) for r in rows]
    coverage=[[p['scoringPointId'] for p in s.get('scoringPointResults',[]) if p['covered']] for s in suggestions]
    stable_coverage=all(c==case['expectedCovered'] for c in coverage)
    in_band=all(s is not None and case['expectedScore'][0]<=s<=case['expectedScore'][1] for s in score_values)
    verdict='PASS' if stable_coverage and in_band else 'FAIL'
    if cid in ('J02','J07','J08'): verdict='QUESTIONABLE'
    follow=[r.get('javaDecision',{}).get('decisionAction') for r in rows]
    input_text=case['input']
    excerpt=input_text[:180]+('…（完整输入见 data/judge-cases.json）' if len(input_text)>180 else '')
    judge_rows.append([cid,case['kind'],'BLOCKED',','.join(map(str,score_values)),str(coverage[0]),'/'.join(sorted(set(follow))),verdict])
    details=f'''### {cid}：{case['kind']}

输入（{len(input_text)} 字符 / {len(input_text.encode('utf-8'))} UTF-8 字节）：

> {excerpt.replace(chr(10),chr(10)+'> ')}

预期：{case['expectedBehavior']}；总分带宽 {case['expectedScore']}，应覆盖 {case['expectedCovered']}。这些是评测者预先标注的合理范围，尚未经本人独立人工标注。

实际：Jev 未调用，score/coverage/follow-up 均未知，状态 BLOCKED。真实 DeepSeek 对照得分 {score_values}；覆盖 {coverage}；Java 决策 {follow}；对照结论 {verdict}。

追问样例：{suggestions[0].get('suggestedFollowUp') or '无'}。
'''
    notes={'J02':'同输入三次范围 38–47，数值波动 9 分；覆盖和 Java 分支未变。仅陈述一致性仍获得 practiceScore=5–6，缺少实践内容时的评分校准值得复核。',
           'J07':'保留同一条正确知识后加入大量无关内容，总分降到25；清晰度惩罚合理，但 correctness 从部分正确对照的10–15未保持固定。应区分信息噪声和技术事实。',
           'J08':'正确内容重复35遍仍得93，clarity降到16，未明显奖励长度；只有一次长文本调用，无法证明长上下文/超时稳定性。',
           'J05':'空串被服务内诊断调用评价为0；生产入口另有真实 HTTP 400，空回答不会进入生产评分链路。',
           'J09':'注入指令未骗到分数或覆盖，但这里只测一个直接注入样本，不是完整安全评测。',
           'J11':'模型识别前后矛盾，两个评分点均不覆盖，未仅抓取开头正确句子。'}
    details+='\n分析：'+notes.get(cid,'分数落在预先范围内，覆盖与输入技术内容一致；这是 DeepSeek 对照结果，不能用于断言 Jev 准确率。')+'\n'
    case_details.append(details)

ablation=read('judge-rag-ablation.json')
without=[r['javaScore']['totalScore'] for r in ablation]
fault=read('judge-network-fault.json')
emit('JEV_EVALUATION_REPORT.md',f'''
# Jev 评分评测与真实 DeepSeek 对照

Jev 在线结论：**BLOCKED，0 次认证推理**。缺少 JEV_API_KEY；不能判断 Jev 是否适合中文 Java 面试评分，不能把任何下面的 DeepSeek 分数冒充 Jev 分数。

预先固定 11 种输入，其中优质/部分正确/完全错误各重复3次，共17次。另有1次真实连接拒绝+真实 DeepSeek fallback，3次无 RAG 消融，总计21次真实 DeepSeek 调用。Jev 自身的 structured output、score、coverage、confidence、timeout 和非法响应行为仍缺少在线证据。

数据：[完整输入、预期](data/judge-cases.json)；[17次真实结果、prompt、检索和 Java 分数](evidence/judge-cases.json)。provider 为 deepseek，实际 model 为 deepseek-flash；没有用 Mock 代替这些调用。

{table(['Case','类型','Jev','真实 DeepSeek 总分','covered IDs','Java 动作','对照质量'],judge_rows)}

## 逐例输入与分析

{chr(10).join(case_details)}

## 结构、异常、重试与 fallback

17/17 对照建议通过 Java 分数、评分点全集、证据非空和追问契约校验。总分由 EvaluationScoreCalculator 相加，没有用 scoring point 权重替代现有五维公式。低分案例的追问围绕缺失的桶定位/一致性；正确回答没有追问。

Jev 分支在代码中先判断后生成文本；五个0..4 Score 乘5并 HALF_UP 到0..20。Choice/Score 分布、confidence、legend、问题集合和回答原文证据都由 Java 校验，min-confidence=0。真实 Jev 未返回任何结果，无法确认这些严格容差与在线版本兼容。[官方 API](https://docs.typesafe.ai/api)、[Score 语义](https://docs.typesafe.ai/primitives/score)支持协议解释，不替代本项目实测。

故障实验把 JevClient 指向实际连接被拒绝的 localhost:1，不提供伪造 AI 响应；原 JevJudgeProvider 执行一次 fallback，真实 DeepSeek 返回 {fault.get('javaScore',{}).get('totalScore')} 分，并通过 Java 校验：[judge-network-fault.json](evidence/judge-network-fault.json)。这是网络故障恢复实验，**不是**真实 Jev 推理，也未实测真实 Jev 超时/限流/非法结构。生产客户端无 HTTP 自动重试；fallback 一次；接口失败后 FAILED 状态可由原请求重试。

补充离线契约/Validator 共138项，失败0、错误0、跳过0，覆盖非法结构、score边界和fallback；这些包含 Mock HTTP，单独标注为离线验证，不替代 Jev integration。真实 DeepSeek 在 E2E 还出现了缺失 scoringPointResults 的 HTTP500，说明17次独立通过不代表输出永远稳定。

## 评分稳定性和 RAG 消融

优质答案：92/97/93；部分正确：47/41/38；错误：14/16/10。各组三次 coverage 和 workflow 分支相同，但部分正确数值跨度9分，只有3次无法估算总体方差。

同一部分正确输入，去掉 RAG 后得分 {without}；有 RAG 为47/41/38。coverage 都是只覆盖2203。两组区间重叠，样本少且模型随机性未固定，**未证明 RAG 提高评分质量或改变结论**。事实：生产检索证据已进入 prompt；因果收益仍未知。消融只在测试调用中构造空证据，没有改生产检索设计。

待本人标注完整性/深度/实践的量表并提供 Jev 凭据后，保持同一数据、同一RAG证据进行 Jev 与 DeepSeek 配对比较，再决定是否启用 Jev或校准confidence。
''')

metric_rows=[]
for name,m in [('原知识库',existing_metrics),('补充语料',expanded_metrics)]:
    metric_rows.append([name,m['coveredPositives'],*[pct(m[f'hit@{k}AllPositive']) for k in (1,3,5)],
                        f"{m['mrrIncludingMissingCorpus']:.3f}",pct(m['precision@5Positive']),pct(m['negativeFalsePositiveRate'])])
def ranked_text(r,k):
    relevant=set(r['case']['expectedRelevantChunkIds'])
    return '; '.join(f"{x['chunkId']}:{x['similarityScore']:.4f}:{'R' if x['chunkId'] in relevant else 'I'}"
                     for x in r['result']['evidence'][:k]) or '空'

trace_text=['# RAG 逐次检索 trace','\nR=预先标注相关，I=无关；分数为真实 COSINE。每次 limit=5，Top1/3为其排序前缀。query由生产 EvaluationRagQueryBuilder 生成，不包含用户回答。']
for name,traces in [('原知识库',original),('补充语料',expanded)]:
    trace_text.append('\n## '+name+'\n')
    for r in traces:
        c=r['case']
        trace_text.append(f"\n### {c['id']} / {c['variant']}\n\n```text\n{r['query']}\n```\n\n预期主题：{c['expectedTopic']}；相关切片：{c['expectedRelevantChunkIds']}；corpusCoverage={c['corpusCoverage']}。\n\nTop1：{ranked_text(r,1)}\n\nTop3：{ranked_text(r,3)}\n\nTop5：{ranked_text(r,5)}\n\n耗时：{r['elapsedMs']}ms；raw/evidence={len(r['result']['rawHits'])}/{len(r['result']['evidence'])}。")
emit('RAG_RETRIEVAL_TRACES.md','\n'.join(trace_text))

metric_table=table(['语料','有对应知识正例/25','Hit@1','Hit@3','Hit@5','MRR','正例Precision@5','负例误证据率'],metric_rows)
pipeline_table=table(['主题','文档 ID','chunk IDs','长度（UTF-16）'],[[p['fixtureKey'],p['documentId'],[c['id'] for c in p['chunks']],[len(c['content']) for c in p['chunks']]] for p in pipeline])
falsepos_rows=[[r['case']['id'],r['case']['question'],ranked_text(r,1)] for r in expanded if r['case']['negative']]
emit('RAG_EVALUATION_REPORT.md',f'''
# RAG 真实检索 baseline

结论：**NEEDS_TUNING**。补充语料后25条正例Top1全中，但Top5正例precision仅28%，5个负例全部返回无关证据。原始知识覆盖严重不足。不能仅凭Hit@1=100%接受当前评分证据质量。

## 协议与标注

30条：5个现有EASY题目 × 原词、同义、口语、不完整题干、混淆表达5种变体=25正例，加5个无对应知识负例。口语/不完整变体改的是题干与评分点，不是把候选人回答加入检索。真实 MAIN 查询仍为 `question + scoring points`；FOLLOW_UP查询为MAIN题干+追问+目标评分点。所有生产主问题来源题库，没有改为LLM生成。

预期主题与标签由评测者在观察检索前固定：[existing-golden.json](data/existing-golden.json)。新增语料经真实上传处理后，在检索前绑定chunk IDs：[expanded-golden.json](data/expanded-golden.json)。这些是**评测者标注的小型受控集**，不是本人独立人工gold。新增语料与回答来自同一技术事实集，语言相近，正例命中结果存在乐观偏差；不是未见主题的泛化结果。

每条实际调用现有 EvaluationRetrievalAdapter → SpringAiEmbeddingClient → MilvusVectorStoreClient → MyBatis eligibility。TopK固定5，Hit@1/3/5来自同一次limit=5的前缀，没有伪装成分别请求三种TopK。完整query、expected IDs、Top1/3/5、similarity、relevant/irrelevant见[RAG_RETRIEVAL_TRACES.md](RAG_RETRIEVAL_TRACES.md)；raw vectorId和metadata见[evidence](evidence/rag-expanded.json)。60次query运行均无服务错误。

## 指标

{metric_table}

Hit@K分母为25正例，未找到对应语料也记为系统漏召回；负例单独算错误证据率，不进入Hit分母。Precision是各query实际返回前缀中相关项的比例，再对正例宏平均；返回不足K时分母用实际返回数。原库仅5/25正例有可用知识，覆盖条件下Hit@1/3/5=100%、MRR=1，但只有一个候选切片，不能说明排序优良。原库20个系统漏召回是知识缺失，不应归因于Embedding排序。

补充语料：Hit@1/3/5=100%、MRR=1。宏平均chunk Recall@1={pct(expanded_metrics['macroRecall@1Covered'])}，Recall@3/5=100%；equals主题有3个相关切片，其他主题各1个，所以Top1 recall不必为100%。正例Precision@1=100%、@3={pct(expanded_metrics['precision@3Positive'])}、@5=28%；在125个正例Top5位置中90个无关。总体150位置中115个无关。Recall只对有完整相关chunk标签的正例计算，负例/原库缺资料行的Recall不定义。[计算结果](evidence/rag-metrics.json)。

## Pipeline 与切片

{pipeline_table}

原文档485/切片443保留。6份新增文档共7个新切片，经真实HTTP multipart上传，再显式process，全部READY/ACTIVE。Embedding qwen3.7-text-embedding/1024维/profile-v1与Collection匹配，schema动态字段关闭、Java vector_id、AUTOINDEX/COSINE。[pipeline.json](evidence/pipeline.json)同时保存源文档、drafts、持久化chunk、vector IDs、实际检索。

当前max=1200、overlap目标150、自然边界从800开始，按段落→换行→句号→hard cut。最长文档切片1050/861，实测连续公共重叠115字符；strip和向前寻找空白使实际重叠不保证精确150。索引连续，无空片，长度均≤1200，token_count=NULL，没有用字符数冒充token。当前策略在这一个长文档上工作，未证明对代码块、超长单词、复杂Markdown或跨切片事实的最优性，暂不改参数。

## Metadata / MySQL filtering

真实测试本轮文档542的 INACTIVE、非READY、document_version不符、embedding_model不符、embedding_version不符；每次Milvus rawHits=5而合格items=3，目标两个chunk确实被剔除，恢复后重新可检索。[metadata-filters.json](evidence/metadata-filters.json)。这是实库状态过滤，不是Mock。

Milvus仅过滤embedding_version；MySQL过滤ACTIVE/READY/currentVersion/model/profile，并交叉校验documentId/chunkIndex。**没有questionId、scoring-point ID或category过滤，也没有similarityThreshold**。没有过滤后补足TopK；这5种场景都从5变3，下一候选不会自动补入。当前TopK表示原始候选上限，不保证最终5条。

## 明显 FALSE POSITIVE

{table(['Case','问题','实际Top1（ID:score:相关性）'],falsepos_rows)}

N3 HTTP/3/QUIC错误命中TCP资料，score=0.6430，是容易混淆主题的真实FP；资料说TCP三次握手，不能据此替代HTTP/3/QUIC知识。其余负例分数约0.27–0.33仍进入prompt。正例中Spring/索引/TCP的后续结果混入HashMap、Redis等；不是状态过滤失效，是状态正确但语义无关。

## 明显 FALSE NEGATIVE

原库Q6779-*、Q6780-*、Q6781-*、Q6782-*共20条，expected knowledge不存在，却返回HashMap片段，形成“漏掉正确知识+塞入错误证据”。补充语料后这30条中没有标注正例FN；只有5个主题、25条变体，不能断言系统没有FN。

## Baseline 后的建议（本轮未调参）

先补齐并人工审核真实知识，再建立独立未见query。固定本次gold，在后续实验一次只改变一个变量：优先评估相似度拒绝或小TopK，记录precision和负例错误率，再决定是否加简单领域限制。此集最低正例Top1约0.701、最高负例约0.643，可用作下一轮阈值候选的离线依据；**不能直接据此把0.65写入生产**，需要留出集与更多近邻负例。严格category过滤也可能漏掉跨领域正确知识，需独立比较。

没有调整chunkSize、overlap、TopK、prompt、threshold。两组指标的变化仅来自语料覆盖增加，属于原库/受控语料两种baseline，不是调参优化的Before/After。
''')

sessions=[]
for batch,items in [('第一轮',first),('第二轮',second)]:
    for s in items:
        report=s.get('report')
        sessions.append([batch,s['sessionId'],s['quality'],len(s['turns']),
                         s.get('expectedReportScore','未完成'),report['httpStatus'] if report else '未生成',s.get('status') or '首次追问HTTP500后中断'])
session_table=table(['批次','Session','回答质量','提交尝试数','FINAL均分','首次报告HTTP','原始结论'],sessions)
runtime=read('runtime-judge-e2e.json')
rt_retrieval=read('runtime-retrieval-e2e.json')
emit('E2E_EVALUATION_REPORT.md',f'''
# 真实端到端面试评测

结论：**FAIL（首次成功可靠性未通过）**。真实DeepSeek链路能完成三档质量的面试、持久化与报告，但出现一次答案HTTP500和两次报告HTTP502。事后重试可恢复，不改变baseline失败事实。Jev+RAG+DeepSeek链路因缺少Jev凭据仍BLOCKED，未实际验证。

## 实际运行链路

```text
真实register/login → JWT + DB当前用户认证
→ Create EASY Session（5 MAIN题库快照一次落库）
→ POST answer（短事务写Answer并CAS）
→ claim EVALUATING → EvaluationContext（FOLLOW_UP合并MAIN与追答）
→ question/scoring points query → 真Embedding → 真Milvus → MySQL eligibility
→ rag_retrieval_batch + rag_hit_log
→ EvaluationPrompt → JudgeProvider(deepseek，本轮实际选择)
→ 真DeepSeek → Java validator → Java五维相加 → Java FollowUpPolicy
→ AnswerEvaluation INITIAL/FINAL → Session CAS → 追问/下一MAIN/结束
→ POST report → FINAL聚合与均分 → 真DeepSeek文本 → Java校验
→ InterviewReport + UserWeakness事务 → READY
```

Jev是可选策略，不是默认链路中的必经步骤。若选jev，才是Jev分数/coverage判断→DeepSeek文本→Java合并校验；错误时一次回退到完整DeepSeek Judge。所有实际评价llm_model为deepseek-flash、prompt_version为evaluation-prompt-v1，没有把fallback当Jev成功。

启动方式：Maven/Surefire + local profile +真实Tomcat随机端口；JDK HttpClient通过真实HTTP访问。核心AI/RAG完全使用生产bean，无Mock。第二轮测试观察器只调用原bean并原样返回结果，不改query、prompt、score或状态。日志只涉及合成回答，不包含key/JWT/password。

## 会话结果

{session_table}

数据：[第一轮](evidence/e2e-first.json)、[第二轮](evidence/e2e.json)、[诊断恢复](evidence/recovery.json)。答案输入由评测者准备，不是本人实际回答；medium表示“核心概念部分正确、缺乏原理”，不是预设必为60分档。所有会话难度EASY，5个题目相同，未声称覆盖MEDIUM/HARD题库。

第一轮优秀5 MAIN得88/96/91/97/92，均分92.8；部分正确5 MAIN+5追问，FINAL31/31/31/35/32，均分32.0。第二轮对应均分95.0/33.4/11.0。排序符合直觉，但覆盖权重60%的部分正确答案最终约33分，因总分按五维求和而非评分点权重；需要本人标注完整性/实践量表，不能擅改业务公式。

## 真实失败与恢复

1. Session2465、Answer1392、FOLLOW_UP首次HTTP500：`LlmEvaluationValidator`拒绝缺失scoringPointResults。RAG batch已保存、未写AnswerEvaluation；答案进入FAILED，会话不向下一题推进。失败raw provider JSON未持久化，因此只能凭异常定位字段，无法事后还原整个JSON。该缺陷属于模型输出与错误边界/可观测性问题，不是MySQL或Milvus不可用。
2. Session2466/2467首次报告HTTP502：DeepSeek Report strengths数组数量不满足1..5；Java拒绝保存报告并标记失败。没有截断列表或放宽schema来让测试通过。原始两次失败report JSON未保存，不能断言到底是0还是>5项。
3. 对FAILED答案保持相同requestId/content做一次真实诊断重试，1392变EVALUATED、FINAL=2并推进下一MAIN；该会话未继续完成。对两份失败报告做真实诊断重试后都READY、均分仍95.0/33.4。恢复阶段只为本轮合成用户签发测试JWT，随后仍经过真实DB校验；它不是又一轮register/login完整流程。
4. 首次Recovery采集脚本误查interview_session.overall_score，在报告已成功后报SQL错误；修正为实际total_score后采集通过。记录为测试脚本错误，不当作生产回归，也没有抹掉原始执行Error。

跨两轮共42次首次答案HTTP提交，1次500；5次首次报告生成，2次502。这是小样本观察，不能作为长期SLA。补充恢复后目前有5个COMPLETED/READY会话、1个IN_PROGRESS会话；保留原始失败证据。

## Coverage、追问与一致性

优秀答案覆盖全部核心评分点，直接FINAL；部分正确基本只覆盖CORE，追问围绕缺失KEY，如桶定位、复杂度、构造注入、回表、旧SYN。FOLLOW_UP没有再次追问；5主问最多各1追问。较差错误结论两个点均不覆盖，纠正方向基本正确。

每个完整session有5个FINAL；MAIN的INITIAL不会计入报告与weakness。所有成功报告overallScore与FINAL均分相同；第一轮、第二轮和恢复均用SQL/HTTP核对。首轮每场首答的相同请求重放未新增RAG batch，避免模型再次评分。空answer和65536字节边界是真实HTTP400，未落Answer记录。

## 不能只看分数：已发现的点评问题

Session2463 ArrayList评价missingPoints写“未特别强调…的边界已在回答中说明，整体无实质遗漏”，自相矛盾。优秀报告又把哈希碰撞、构造注入的不可变性和序列号同步列为不足；输入实际已说hashCode相同不保证相等、final依赖、x/y序列号。部分要求超出冻结评分点。应把“确实缺失”与“可选拓展”分开标注，减少误导复习建议。

较差报告仍有“能够理解题意”等推断式表扬，没有技术正确性证据支撑；不属于评分反转，但需要人工审查反馈文风。TCP题库reference_answer本身有“客户端确认自己的ACK是否收到”的不准确表述，生成反馈有沿用风险；RFC9293讲的是确认SYN序列号与处理旧重复连接请求，不能以ACK确认ACK解释第三次握手。[TCP标准](https://www.rfc-editor.org/rfc/rfc9293.html)。本轮记录，未改题库或参考答案。

## RAG 是否真正参与

生产trace与prompt证明相同检索证据被传入真实DeepSeek。第二轮完整保存25次原始检索/过滤结果和25次Java校验前Judge结果：[runtime-retrieval-e2e.json](evidence/runtime-retrieval-e2e.json)、[runtime-judge-e2e.json](evidence/runtime-judge-e2e.json)。检索query不含答案；MAIN各质量档的query相同；FOLLOW_UP只添加追问与目标点评标准。

这是“输入参与”证据，未证明因果提升。有/无RAG配对对照区间重叠，参考答案本来就含关键事实；当前实验无法把RAG收益从模型知识/参考答案中分离。Top5无关片段已进入prompt，模型本轮大多没被带偏，但不能假定未来永远安全。

## 可观测性

rag_retrieval_batch能定位query、TopK、过滤快照、model/version、raw/evidence count；rag_hit_log记录通过eligibility的chunk IDs/rank/similarity。失败前RAG仍可查。成功answer_evaluation包含provider/model/promptVersion/raw_result与Java分数。原始Milvus被过滤命中只在内存，不逐项持久化；失败Judge/Report raw输出不保存，失败原因detail不足。

本轮测试观察器解决本地证据采集而未修改生产日志；后续建议增加开发/评测专用、脱敏且有保留期限的阶段事件，特别是schema失败字段、实际provider/fallback原因和失败raw摘要。不得记录API key或真实用户隐私。本轮尚未完成真实Jev在线兼容、timeout或fallback频率检验。
''')

emit('AI_SYSTEM_BASELINE.md',f'''
# AI 面试系统真实运行 baseline

2026-10-04；代码基线20a933a及既有未提交Jev工作区；生产业务语义、DB schema、prompt、TopK和切片参数均未改。

| 项目 | 最终判定 | 依据 |
| --- | --- | --- |
| JEV | **FAIL（验收未通过，原因BLOCKED）** | 缺少凭据；0次认证推理。此处FAIL不是模型评分错误结论，Jev效果未知 |
| RAG | **NEEDS_TUNING** | 扩充语料正例全中；原库覆盖20%，扩充后Top5 precision28%、负例错误证据率100% |
| E2E | **FAIL** | 真实DeepSeek流程出现答案schema错误HTTP500和报告数组约束HTTP502；诊断恢复可用但不抵消首次失败 |

Jev协议形式适合有界判断，当前量表和Java边界已接入；**是否适合本项目评分尚无法判断**。没有把离线契约测试或DeepSeek对照当成真实Jev证据。[Jev官方API](https://docs.typesafe.ai/api)。

## 核心指标与观察

{metric_table}

两组都固定25正例+5负例、question+scoring points检索。原库1切片，仅5正例有资料；补充语料共7文档8切片。扩充后的Hit@1/3/5=100%来自受控、评测者标注小集，不代表完整生产泛化。主要FP是所有负例（尤其HTTP/3误命中TCP，0.643）及正例Top5跨题片段；原库主要FN是ArrayList/Spring/MySQL/TCP无对应资料，20条。详见[RAG评测](RAG_EVALUATION_REPORT.md)。

TopK=5、无threshold、无领域过滤：候选召回可用，直接把所有eligible候选送入prompt不合理。原有过滤解决状态/版本可信性，不能解决语义相关性。建议后续在固定gold+留出集比较一项阈值或较小TopK，不直接上线本次数据推出的阈值。

chunk策略1200/150/800在一个长Markdown上得到1050/861两个chunk、实际重叠115字符，无空片/超限；暂不改参数，补充代码块与跨边界事实后再评价。

DeepSeek17次独立案例基本区分质量，coverage分支稳定，但部分正确同输入47/41/38有9分波动；无RAG38/35/55，未证明RAG评分收益。真实端到端已完成5个完整session（含重试后READY），优秀均分92.8/95，部分正确32/33.4，较差11；另有1个较差session失败后仅恢复首道追问。[独立评测](JEV_EVALUATION_REPORT.md)、[E2E评测](E2E_EVALUATION_REPORT.md)。

追问大多指向缺失评分点，Java控制最多一次，FINAL/report聚合一致。反馈存在把已回答内容记为缺失、自相矛盾的missingPoints、过度表扬和题库参考答案错误风险。数值顺序合理不等于AI反馈可以直接验收。

**当前适合进入有问题清单的人工评审，不适合给出“已通过AI效果验收”的结论。** Jev真实链路未测、结构稳定性和反馈事实性仍有阻断项。本报告不宣称项目完成。

## 实际测试、真实调用、未测范围

- 真实：MySQL、Spring Boot HTTP、6文档/7chunk上传处理、Embedding、Milvus、60条RAG query、5种实库过滤、21次独立/消融/故障回退DeepSeek、两轮6个Session尝试及报告/恢复。
- Jev：只有无凭据HTTP403连通探测，不是推理；没有任何认证在线score/coverage/confidence/timeout/非法结构验证。Jev+RAG+DeepSeek E2E BLOCKED。
- 补充：138项离线契约/Validator和普通全量回归，包含Mock与真实MySQL；这些证明代码边界，不能证明在线AI质量。
- 未覆盖：MEDIUM/HARD完整题库、真人独立标注集、大规模语料、近阈值负例、持续并发/性能、成本token计费、真实Jev配额与限流、其在线非法返回和实际timeout。

## 验证执行记录

{test_table}

## 本人下一步人工测试

1. 先配置本地Jev凭据，用同一11类输入+重复次数运行；逐例核对实际provider，回退到DeepSeek的case不得判Jev通过。
2. 在2463/2466优秀报告对照原答，圈出“说过但判缺失”及超出评分标准的要求；重点ArrayList、构造器注入、TCP。
3. 在部分正确场景不只重复原答：追问补对一个缺失点，再补全所有点；检验FINAL分数/coverage是否合理上升，report只使用FINAL。
4. 复核完全错误、前后矛盾、跑题和拒答的纠正及追问。复现FAILED答案和report失败后的原requestId重试，确认不重复落库且前端能恢复。
5. 新写未进入本轮语料的口语题干与混淆概念（HashMap/Redis Hash、IoC/AOP、TCP/QUIC、聚簇/覆盖索引），人工标相关chunk；检查Top5中所有片段，不只看Top1。
6. 审核TCP题库参考答案；补充不改变主问题生成规则的事实性知识。已有equals/hashCode标准可参考[Java Object协议](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html)，TCP参考[RFC9293](https://www.rfc-editor.org/rfc/rfc9293.html)。

## 文件与交付状态

新增`src/test/java/com/kun/aiinterview/evaluation/AiSystemBaselineEvaluationTest.java`（双保护真实采集/HTTP工作流）和`BaselineTraceConfiguration.java`（测试专用真实调用观察器）；新增docs/evaluation下运行/数据准备/指标报告脚本、golden data及原始evidence。没有改现有生产Java/YAML/Mapper或已有测试语义；修正仅在本轮新测试脚本内。

编码/编译/真实验证已按上述记录执行；未暂存、未commit、未push。`docs/`当前被本机`.git/info/exclude`忽略，所以报告/脚本在本地可打开，但尚未进入Git跟踪；本轮没有更改该规则。开始时已有工作区改动保留，不能把它们算作本轮新增实现。

附加证据审核核对60次query的vector/chunk/document映射、5份成功report的FINAL均分与真实prompt输入；真实凭据精确匹配扫描未发现泄露。`evidence/manifest.json`保存数据/证据及生产Java SHA-256，便于固定此轮baseline。

原始证据、输入和复现步骤见[README](README.md)。本次知识与合成会话作为复核材料保留，没有清空共享Collection。
''')

emit('README.md',r'''
# Baseline 复现与证据

先阅读 [AI_SYSTEM_BASELINE.md](AI_SYSTEM_BASELINE.md)。本目录是本地评测产物，当前docs/被.git/info/exclude忽略，未提交。

## 文件职责

- ENVIRONMENT_CHECK.md：真实依赖、启动错误、实际测试计数、保留数据。
- JEV_EVALUATION_REPORT.md：Jev BLOCKED、真实DeepSeek对照、逐例输入/分数/覆盖/追问与恢复边界。
- RAG_EVALUATION_REPORT.md / RAG_RETRIEVAL_TRACES.md：指标、每个query与真实Top1/3/5、FP/FN。
- E2E_EVALUATION_REPORT.md：真实HTTP面试、失败、报告与FINAL一致性、可观测性。
- data/：事先固定输入/语义标签/评测文档；evidence/：真实返回与SQL快照，全部是合成面试内容，不含key/JWT/password。
- scripts/run-baseline.ps1：本机Windows凭据到子进程、双保护opt-in与Maven日志；prepare-data.py：检索前标签；build-reports.py：从证据生成指标报告；check-jev-access.py：无凭据探测，不是推理；audit-evidence.py：映射/评分算术/凭据泄露检查与SHA-256 manifest。

## 复现（会真实调用并产生费用/测试数据）

MySQL、已有Milvus及ignored application-local.yaml先可用；配置Jev key只写本地ignored配置或Windows用户环境变量，不要贴入对话或Git。脚本依赖本机Java21路径和D:/python/python.exe，移到另一台机器时只调整脚本工具路径。

```powershell
.\docs\evaluation\scripts\run-baseline.ps1 -Phase inventory
& D:\python\python.exe docs/evaluation/scripts/prepare-data.py
.\docs\evaluation\scripts\run-baseline.ps1 -Phase rag -Dataset existing
# pipeline会新增6文档，只在需要新的受控语料时执行；重复执行会新增重复资料。
.\docs\evaluation\scripts\run-baseline.ps1 -Phase pipeline
& D:\python\python.exe docs/evaluation/scripts/prepare-data.py --expanded
.\docs\evaluation\scripts\run-baseline.ps1 -Phase rag -Dataset expanded
.\docs\evaluation\scripts\run-baseline.ps1 -Phase judge
.\docs\evaluation\scripts\run-baseline.ps1 -Phase e2e
.\docs\evaluation\scripts\run-baseline.ps1 -Phase focused
.\docs\evaluation\scripts\run-baseline.ps1 -Phase regression
& D:\python\python.exe docs/evaluation/scripts/build-reports.py
```

这次“existing”特指inventory证据中初始单文档库；已经保留扩充语料后再跑existing只会换标签，不会自动恢复单文档库。脚本不删任何共享文档/Collection。因此复现实验必须固定/审核语料快照，并将新一轮证据另存，避免覆盖本轮报告。

Recovery用于本轮记录过的FAILED session/answer单次诊断，不是压力重试脚本，不改变prompt/schema：`run-baseline.ps1 -Phase recovery`。它为本轮合成账号临时签发真实JWT，经过现有DB认证；报告不保存凭据。

普通regression在unset RUN_REAL_* / MILVUS_ENABLED后运行`mvnw -B -ntp test`，guarded real tests必须跳过。focused是离线契约补充。日志保存在ignored target/evaluation/，不得将采集JUnit BUILD SUCCESS直接等同于AI验收PASS。

本轮保留文档542–547、chunk484–490、session2463–2468。过滤实验只修改新增文档542并finally恢复；恢复后原文档485仍READY。没有drop、recreate或clear Collection。删除证据数据前应先评估MySQL外键与报告复核影响，不要直接清库。
''')

print('REPORTS_CREATED=ENVIRONMENT_CHECK,JEV_EVALUATION_REPORT,RAG_EVALUATION_REPORT,E2E_EVALUATION_REPORT,AI_SYSTEM_BASELINE')
print('RAG_EXPANDED='+json.dumps(expanded_metrics,ensure_ascii=False))
