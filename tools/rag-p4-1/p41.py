"""Independent P4.1 prompt version and three-case micro experiment; old P4 remains failed."""
import argparse
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import statistics
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
PREP = ROOT / 'target/rag-p4-1/prepared'
OFFLINE = ROOT / 'target/rag-p4-1/offline'
RUN = ROOT / 'target/rag-p4-1/run-once'
AUTH = 'AUTHORIZE P4.1 C01 N02 N06 3 ONCE'
sys.path.insert(0, str(ROOT / 'tools/rag-p4'))
import p4  # reuse usage accounting only; never invoke old prepare/execute/verify


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def write(path, value, exclusive=False):
    path = Path(path); path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x' if exclusive else 'w', encoding='utf-8', newline='\n') as file:
        json.dump(value, file, ensure_ascii=False, indent=2); file.write('\n')


def utc():
    return datetime.now(timezone.utc).isoformat()


def protocol():
    value = read(ROOT / 'tools/rag-p4-1/protocol-v1.json')
    require(value['queryIds'] == ['P3-C01', 'P3-N02', 'P3-N06'] and value['maxCalls'] == 3 and
            value['retryCount'] == 0 and value['maxOutputTokens'] == 3072 and value['requestedModel'] == 'deepseek-v4-flash', 'Three-case/model limit')
    return value


def review():
    authored = read(ROOT / 'tools/rag-p4-1/development-review-v1.json')
    dataset = read(ROOT / 'target/rag-p4/prepared/dataset-with-provenance.json')['queries']
    oldplan = read(ROOT / 'target/rag-p4/prepared/plan.json')['queries']
    rows = []
    for item in authored['cases']:
        query = next(q for q in dataset if q['queryId'] == item['queryId'])
        candidate = query['candidates'][int(item['candidateId'][1:]) - 1]
        require(item['quote'] in candidate['content'] and item['questionAnchor'] in query['question'], 'Offline review quote/anchor must be real')
        row = dict(item, vectorId=candidate['vectorId'], chunkId=candidate['chunkId'],
                   legacyLabel=candidate['label'], legacyLabelSource=candidate['labelSource'],
                   reviewSource=authored['source'])
        if query['queryId'] in [p['queryId'] for p in oldplan[:5]]:
            identity = next(p['questionId'] for p in oldplan if p['queryId'] == query['queryId'])
            outer = read(ROOT / f'target/rag-p4/run-once/{identity}/response.json')
            model = json.loads(outer['choices'][0]['message']['content'])
            original = next(c for c in model['candidates'] if c['candidateId'] == item['candidateId'])
            row.update(originalModelVerdict=original['verdict'], originalModelReason=original['reason'])
        else:
            row['originalModelVerdict'] = None  # no original P4 response for these unattempted cases
        rows.append(row)
    require(len(rows) == 14, 'Ten paid candidates plus four negative diagnostics')
    return rows


def mock_response(payload, rows):
    points, candidates = [], []
    for index, item in enumerate(rows, 1):
        point = f'p{index}'
        points.append({'id': point, 'questionQuote': item['questionAnchor'], 'description': item['mechanism']})
        candidates.append({'candidateId': item['candidateId'], 'verdict': item['suggestedVerdict'],
                           'versionAssessment': 'NOT_APPLICABLE', 'supports': [
                               {'pointId': point, 'quote': item['quote'], 'explanation': item['mechanism']}
                           ] if item['suggestedVerdict'] == 'CORE_SUPPORT' else [],
                           'reason': 'DEVELOPER_AUTHORED_MOCK_NOT_REAL_PROMPT_RESULT'})
    return {'questionId': payload['questionId'], 'mainMechanism': {'questionQuote': points[0]['questionQuote'], 'description': 'Mock requested mechanism'},
            'corePoints': points, 'candidates': candidates}


def history_paths():
    frozen = read(ROOT / 'target/rag-p4/prepared/freeze.json')
    files = {Path(p) for p in frozen['historySha256']}
    for folder in ('tools/rag-p3', 'target/rag-p3', 'tools/rag-p4', 'target/rag-p4', 'tools/rag-p4-repair', 'target/rag-p4-repair'):
        files.update(p.resolve() for p in (ROOT / folder).rglob('*') if p.is_file())
    return sorted(files)


def source_paths():
    files = list((ROOT / 'tools/rag-p4-1').glob('*')) + list((OFFLINE / 'classes').rglob('*.class'))
    files += [ROOT / 'tools/rag-p4/SemanticEvidenceGate.java', ROOT / 'tools/rag-p4/P4DeepSeek.java', ROOT / 'tools/rag-p4/p4.py',
              ROOT / 'tools/rag-p3/p3.py', ROOT / 'tools/rag-p2/p2.py', ROOT / 'target/rag-e2e/runtime-classpath.txt']
    files += list((ROOT / 'src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek').glob('*.java'))
    files += list((ROOT / 'target/classes/com/kun/aiinterview/interview/evaluation/llm/deepseek').glob('*.class'))
    return sorted({p.resolve() for p in files if p.is_file()})


def prepare():
    require(not RUN.exists(), 'P4.1 run reserved; cannot replace its frozen preparation')
    config = protocol(); reviewed = review()
    write(PREP / 'development-review.json', {'scope': config['scope'], 'status': 'PROVISIONAL_OFFLINE_INFERENCE', 'candidates': reviewed})
    previous = read(ROOT / 'target/rag-p4/prepared/plan.json')['queries']
    prompt = (ROOT / 'tools/rag-p4-1/system-prompt.txt').read_text(encoding='utf-8')
    queries = []
    for query_id, identifier in zip(config['queryIds'], config['opaqueQuestionIds'], strict=True):
        old = next(q for q in previous if q['queryId'] == query_id)
        payload = read(old['payloadPath']); payload['questionId'] = identifier
        # Data whitelist: raw question + unchanged title/body. Never labels, scores or analyst mechanisms.
        require(set(payload) == {'questionId', 'question', 'candidates'} and
                all(set(c) == {'candidateId', 'title', 'content'} for c in payload['candidates']), 'No evaluation leakage')
        path = PREP / f'inputs/{identifier}.json'; write(path, payload)
        input_proxy = len(json.dumps(payload, ensure_ascii=False).encode('utf-8')) + len(prompt.encode('utf-8')) + 2048
        cost = (Decimal(input_proxy) * Decimal('2') + Decimal(3072) * Decimal('8')) / Decimal(1000000)
        queries.append({'queryId': query_id, 'questionId': identifier, 'payloadPath': str(path), 'payloadSha256': sha(path),
                        'originalP4InputSha256': old['payloadSha256'], 'originalP4QuestionId': old['questionId'],
                        'candidateVectorIds': old['candidateVectorIds'], 'inputTokenProxy': input_proxy, 'peakCostProxyCny': str(cost)})
        selected_review = [r for r in reviewed if r['queryId'] == query_id]
        mock = mock_response(payload, selected_review)
        write(PREP / ('mock-c01-response.json' if identifier == 't1' else f'mock-{identifier}-response.json'), mock)
    total = sum(Decimal(q['peakCostProxyCny']) for q in queries)
    require(total <= Decimal(str(config['budgetCny'])), 'Budget proxy exceeded')
    write(PREP / 'plan.json', {'protocol': config, 'queries': queries, 'peakCostProxyCny': str(total),
                             'inputTokenProxy': sum(q['inputTokenProxy'] for q in queries), 'maximumOutputTokens': 3 * 3072,
                             'separateExperimentNotP4Resume': True, 'newPromptRealPerformanceVerified': False})
    prepared_files = [p for p in PREP.rglob('*.json') if p.name != 'freeze.json']
    files = set(history_paths() + source_paths() + prepared_files)
    freeze = {'schema': 'p4-1-independent-freeze-v1', 'createdUtc': utc(),
              'parentP4ResultSha256': sha(ROOT / 'target/rag-p4/run-once/results.json'),
              'scope': config['scope'], 'fileSha256': {str(p): sha(p) for p in sorted(files)}}
    write(PREP / 'freeze.json', freeze)
    (PREP / 'freeze.sha256').write_text(sha(PREP / 'freeze.json') + '\n', encoding='ascii')
    report(); print('P4_1_OFFLINE_PREPARATION=COMPLETE')


def verify(tests=False):
    freeze = read(PREP / 'freeze.json'); seal = sha(PREP / 'freeze.json')
    require(seal == (PREP / 'freeze.sha256').read_text('ascii').strip(), 'P4.1 freeze seal')
    for path, value in freeze['fileSha256'].items():
        require(Path(path).is_file() and sha(path) == value, 'Sealed file changed: ' + path)
    if tests:
        proof = read(OFFLINE / 'test-summary.json')
        require(proof['passed'] and proof['freezeSha256'] == seal, 'Offline verification receipt missing/stale')
        for path, value in proof['evidenceSha256'].items():
            require(sha(path) == value, 'Test evidence changed')
    return seal


def ready():
    seal = verify()
    paths = [OFFLINE / n for n in ('java-tests.json', 'python-tests.json', 'entry-tests.json', 'historical-replay.json')]
    results = [read(p) for p in paths[:3]]
    require(all(t['tests'] > 0 and t['failures'] == t['errors'] == t['skipped'] == 0 for t in results), 'Focused test gate')
    write(OFFLINE / 'test-summary.json', {'passed': True, 'freezeSha256': seal, 'tests': sum(t['tests'] for t in results),
                                        'failures': 0, 'errors': 0, 'skipped': 0, 'paidCalls': 0,
                                        'evidenceSha256': {str(p): sha(p) for p in paths}})
    report(); print('P4_1_PREP=READY')


def java_call(item, folder, credentials):
    cp = str(OFFLINE / 'classes') + ';' + str(ROOT / 'target/classes') + ';' + (ROOT / 'target/rag-e2e/runtime-classpath.txt').read_text(encoding='utf-8-sig').strip()
    environment = {n: os.environ[n] for n in ('SystemRoot', 'WINDIR', 'COMSPEC', 'TEMP', 'TMP', 'USERPROFILE') if n in os.environ}
    child = subprocess.run(['C:/Program Files/Java/jdk-21.0.11/bin/java.exe', '-Djdk.httpclient.disableRetryConnect=true',
                            '-Djdk.httpclient.enableAllMethodRetry=false', '-cp', cp, 'P41Once', item['payloadPath'], str(folder)],
                           input=json.dumps(credentials, ensure_ascii=False).encode('utf-8'), env=environment, cwd=ROOT,
                           capture_output=True, timeout=90)
    require(child.returncode == 0, 'P4.1 Java stopped; preserve intent and lock; no retry')
    return read(folder / 'decision.json')


def execute(credentials, runner=java_call, origin='REAL'):
    seal = verify(tests=True)
    require(credentials.get('authorization') == AUTH and credentials.get('freezeSha256') == seal, 'New explicit P4.1 authorization/seal required')
    require(isinstance(credentials.get('apiKey'), str) and credentials['apiKey'].strip(), 'Missing API key')
    plan = read(PREP / 'plan.json')
    require(len(plan['queries']) == 3 and Decimal(plan['peakCostProxyCny']) <= Decimal(str(plan['protocol']['budgetCny'])), 'Three-call budget')
    RUN.mkdir(exist_ok=False)
    write(RUN / 'EXECUTION_RESERVED.lock', {'authorization': AUTH, 'freezeSha256': seal, 'utc': utc()}, exclusive=True)
    result = {'origin': origin, 'freezeSha256': seal, 'startedUtc': utc(), 'P4_1_EXECUTION_RESULT': 'IN_PROGRESS',
              'P4_1_QUALITY_RESULT': 'PENDING_SEMANTIC_REVIEW', 'attemptsReserved': 0, 'retryCount': 0,
              'otherCalls': plan['protocol']['otherCalls'], 'queries': [
                  {'queryId': p['queryId'], 'questionId': p['questionId'], 'status': 'NOT_STARTED'} for p in plan['queries']]}
    write(RUN / 'results.json', result)
    try:
        for item, record in zip(plan['queries'], result['queries'], strict=True):
            verify(tests=True)
            folder = RUN / item['questionId']; folder.mkdir(exist_ok=False)
            write(folder / 'REQUEST_INTENT.lock', {'freezeSha256': seal, 'payloadSha256': item['payloadSha256'], 'utc': utc()}, exclusive=True)
            record['status'] = 'INTENT_RESERVED'; result['attemptsReserved'] += 1; write(RUN / 'results.json', result)
            response = runner(item, folder, credentials); record.update(response)
            record['candidateVectorIds'] = item['candidateVectorIds']
            record['status'] = 'COMPLETE' if response['gate']['status'] == 'VALIDATED_MODEL_JUDGMENT' else 'SAFE_EMPTY_FAILED'
            record['artifactSha256'] = {p.name: sha(p) for p in folder.glob('*.json')}; write(RUN / 'results.json', result)
            require(record['status'] == 'COMPLETE' and response['attempts'] == 1, 'Safe empty failure; stop no retry')
        result['P4_1_EXECUTION_RESULT'] = 'PASS'
    except Exception:
        result['P4_1_EXECUTION_RESULT'] = 'FAIL_STOPPED_NO_RETRY'
        for q in result['queries']:
            if q['status'] == 'INTENT_RESERVED':
                q['status'] = 'SAFE_EMPTY_FAILED'; q['gate'] = {'status': 'SAFE_EMPTY', 'admittedCandidateIds': []}
    finally:
        result['endedUtc'] = utc(); result['cost'] = p4.usage_cost(result['queries'], plan['protocol'])
        try: verify(); result['historyPreserved'] = True
        except Exception: result['historyPreserved'] = False; result['P4_1_EXECUTION_RESULT'] = 'FAIL_HISTORY_CHANGED'
        write(RUN / 'results.json', result)
        (RUN / 'results.sha256').write_text(sha(RUN / 'results.json') + '\n', encoding='ascii')
    if origin == 'REAL':
        analyze()
    print(('P4_1_EXECUTION_RESULT=' if origin == 'REAL' else 'P4_1_MOCK_EXECUTION_RESULT=') + result['P4_1_EXECUTION_RESULT'])


def analyze():
    verify(); result = read(RUN / 'results.json')
    require(result['origin'] == 'REAL' and sha(RUN / 'results.json') == (RUN / 'results.sha256').read_text('ascii').strip(), 'Saved real result SHA')
    cards = []
    for item, record in zip(read(PREP / 'plan.json')['queries'], result['queries'], strict=True):
        for name, value in record.get('artifactSha256', {}).items():
            require(sha(RUN / record['questionId'] / name) == value, 'Saved response modified')
        input_data = read(item['payloadPath']); admitted = record.get('gate', {}).get('admittedCandidateIds', [])
        cards.append({'queryId': item['queryId'], 'selectedCount': len(admitted),
                      'evidence': [dict(c, vectorId=item['candidateVectorIds'][n]) for n, c in enumerate(input_data['candidates']) if c['candidateId'] in admitted],
                      'gate': record.get('gate'), 'status': record['status']})
    normal = result['P4_1_EXECUTION_RESULT'] == 'PASS'
    uncertain = any(c['verdict'] == 'UNCERTAIN' for q in result['queries'] for c in q.get('gate', {}).get('candidates', []))
    structural = normal and not uncertain and cards[0]['selectedCount'] == 2 and cards[1]['selectedCount'] == cards[2]['selectedCount'] == 0
    latencies = [q['elapsedMs'] for q in result['queries'] if 'elapsedMs' in q]
    write(RUN / 'analysis.json', {'scope': 'TARGETED_MICRO_DEVELOPMENT_SET_ONLY', 'P4_1_STRUCTURE_CHECK': 'MATCH_PENDING_SEMANTIC_REVIEW' if structural else 'FAIL_OR_UNCERTAIN',
                                 'P4_1_QUALITY_RESULT': 'PENDING_SEMANTIC_REVIEW', 'queries': cards, 'cost': result['cost'],
                                 'callsReserved': result['attemptsReserved'], 'meanModelCallMs': statistics.mean(latencies) if latencies else None,
                                 'requiredReview': 'Verify C01 c2 quotes support positioning/iterator; N02/N06 no requested mechanism; correct point decomposition and versions. Counts and exact quotes alone are not semantic Ground Truth.'})
    report()


def report():
    plan, reviewed = read(PREP / 'plan.json'), read(PREP / 'development-review.json')['candidates']
    text = '# RAG P4.1：部分核心支持的误拒修复准备\n\n'
    text += '状态：离线实现及冻结准备；新版Prompt真实模型效果尚未验证。P4旧结果仍FAIL_STOPPED_NO_RETRY，P4.1为新版本开发集微验证，不续跑旧7题，不宣称独立泛化或生产接入。\n\n'
    text += '| 题目/候选 | 历史模型判定 | 离线建议 | 主要发现 |\n|---|---|---|---|\n'
    for row in reviewed:
        text += f"| {row['queryId']}/{row['candidateId']} | {row['originalModelVerdict'] or '未调用P4'} | {row['suggestedVerdict']} | {row['finding']} |\n"
    text += '\n这些建议来自AI工程师的离线原文分析，均为PROVISIONAL；旧标签及9条用户授权AI修订继续保留来源，不作为在线输入或完整人工Ground Truth。C03 c2的标签1不意味着支持发布机制，不能为达90%强行升级。14条分析中前10条是已有5题真实响应，后4条只是P3候选的缺口诊断。逐字引用见development-review.json。\n'
    text += '\n核心模式：把缺少完整展开/所有细节等同于单条无价值；参考列表掩盖了前段机制说明；同类摘要在不同题尺度不一致。此外C04 c1有支持点过度声明，不能把改变配置的分支当成题设默认分支。修复Prompt按最小机制命题匹配，至少一个点直接有依据就保留，简洁结论也可有核心价值；没有请求机制的同域背景仍拒绝。没有基于C/N编号、关键词、标签、COSINE或分数的特殊准入代码。\n'
    text += '\n实现：新system-prompt.txt/protocol-v1.json；沿用已修复的SemanticEvidenceGate与P4DeepSeek.capture/evaluate，不改变schema或老Prompt。只新建P41Once作为独立锁/路径入口，底层仍复用DeepSeekEvaluationClient.completeJson。Java逐字验证引文、核心ID及声明的版本状态；实际机制蕴含和版本兼容仍由模型判断，不能以返回MATCH当成独立证明，必须复核真实结果。\n'
    summary_path = OFFLINE / 'test-summary.json'
    if summary_path.exists():
        summary = read(summary_path)
        text += f"\n必要测试：{summary['tests']}项，failures=0/errors=0/skipped=0；5条历史真实响应gate输出与修复后基线完全相同，C01/C05 c2仍是旧BACKGROUND_ONLY。Mock对新版预期输出只是合同测试，不证明真实模型改善；SUPPORTED_POINT和编码的既有结论作为前提，不再次开展排查。\n"
    else:
        text += '\n测试尚待执行。\n'
    text += f"\n最小真实计划：仅C01/N02/N06，各1次同时检查2条，最多3次DeepSeek，Embedding/Milvus/Reranker/Jev/数据库连接和写入均0。沿用deepseek-v4-flash兼容别名（官方实际服务DeepSeek-V4.1-Flash），POST https://api.deepseek.com/chat/completions，thinking=disabled、JSON mode、max_tokens=3072。输入是原题+完整正文及标题，仅questionId换为t1/t2/t3；没有标签或预期覆盖状态。\n"
    text += f"\n预算：UTF-8字节+每请求2048 framing保守输入代理{plan['inputTokenProxy']}，最大输出9216 tokens；[官方当前高峰价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)未缓存输入¥2/百万、输出¥8/百万，费用代理¥{plan['peakCostProxyCny']}，门禁¥0.25（不是实际tokens或账单；实际usage/cache/延迟另存）。独立freeze.json绑定新Prompt、3组输入、程序、历史和离线编译；新run-once和逐题锁，无重试、失败安全空证据并停批，不删除锁或续跑。\n"
    text += '\n后续授权后的入口：`pwsh -NoProfile -File .\\tools\\rag-p4-1\\run.ps1 -Mode ExecuteOnce`；唯一授权短语AUTHORIZE P4.1 C01 N02 N06 3 ONCE。本轮尚未执行，也不请求普通开发审批。费用授权不能用旧P4/P3短语代替。密钥走隐藏输入/private stdin。\n'
    text += '\n真实判据预先冻结：3/3正常校验，C01 c1/c2均保留且c2引用确实支持反复定位/iterator前进，N02/N06正常判为无核心并各返回0；UNCERTAIN或调用失败虽安全空知识，不能算成功拒绝。需核验引用蕴含、条件和版本；分析程序只自动给结构检查，不自动质量PASS。3题通过也仅支持微验证，不证明C02～C05新版效果或完整90%门槛，更不证明独立泛化。\n'
    if (RUN / 'analysis.json').exists():
        analysis = read(RUN / 'analysis.json')
        text += '\n真实运行结构状态：' + analysis['P4_1_STRUCTURE_CHECK'] + '；语义质量：' + analysis['P4_1_QUALITY_RESULT'] + '。\n'
    (PREP / 'report.md').write_text(text, encoding='utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('mode', choices=['prepare', 'verify', 'ready', 'execute-once', 'analyze'])
    args = parser.parse_args()
    try:
        if args.mode == 'prepare': prepare()
        elif args.mode == 'verify': verify(); print('P4_1_FROZEN_INPUTS=VERIFIED')
        elif args.mode == 'ready': ready()
        elif args.mode == 'analyze': analyze()
        else: execute(json.load(sys.stdin))
    except Exception as error:
        print('P4_1_STOPPED: ' + str(error), file=sys.stderr); sys.exit(1)
