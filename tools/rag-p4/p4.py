"""P4 preparation, sealed single execution, and provenance-aware development-set analysis."""
import argparse
from collections import Counter
from decimal import Decimal
import hashlib
import html
import json
import os
from pathlib import Path
import statistics
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/rag-p3'))
import p3

AUTH = 'AUTHORIZE P4 12 DEEPSEEK ONCE'
LABEL = 'target/rag-p3/p3-labels-corrected-user-authorized.json'
AUDIT = 'target/rag-p3/p3-authorized-correction-audit.json'
SEALED_INPUTS = {
    LABEL: 'fb5812fd2eddd987fd0d79476c351e27f178daba0a46527cc76011a0c23e9117',
    AUDIT: 'b913b41829ba02d7046c5dfa50a64d7bb398d1737eb6de3e061b207ad01c1a57',
    'target/rag-p3/run-once/results.json': '70e22508f7c85b92bbec57e0c5ba470a085a9901719f0e938cd5384280a4e843',
    'target/rag-p3/run-once/retrieval/retrieval.json': 'e7159f8d6c6fdc576f248bd19b70fd52cfaa259ba2bb649089de1cc67b7e24c3',
}
PREP = ROOT / 'target/rag-p4/prepared'
RUN = ROOT / 'target/rag-p4/run-once'
OFFLINE = ROOT / 'target/rag-p4/offline'


def require(ok, code):
    if not ok:
        raise ValueError(code)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def write(path, value, exclusive=False):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('xb' if exclusive else 'wb') as file:
        file.write(encoded(value))


def utc():
    return datetime.now(timezone.utc).isoformat()


def sources():
    files = list((ROOT / 'tools/rag-p4').glob('*'))
    files += [ROOT / 'tools/rag-p3/p3.py', ROOT / 'tools/rag-p2/p2.py',
              ROOT / 'target/rag-e2e/runtime-classpath.txt']
    files += list((ROOT / 'src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek').glob('*.java'))
    # Bytecode loaded from the existing project build, including its dependent DTOs.
    files += list((ROOT / 'target/classes/com/kun/aiinterview/interview/evaluation').rglob('*.class'))
    files += list((OFFLINE / 'classes').rglob('*.class'))
    return sorted({p.resolve() for p in files if p.is_file()})


def dataset():
    for path, sha in SEALED_INPUTS.items():
        require((ROOT / path).is_file(), 'Missing user-supplied evidence: ' + path)
        require(digest((ROOT / path).read_bytes()) == sha, 'Saved evidence SHA changed: ' + path)
    questions, _, _, _ = p3.verify(ROOT)
    labels, audit = read(ROOT / LABEL), read(ROOT / AUDIT)
    require(audit['correctedSha256'] == SEALED_INPUTS[LABEL] and audit['notFullHumanGroundTruth'], 'Correction provenance')
    retrieval, results = read(ROOT / 'target/rag-p3/run-once/retrieval/retrieval.json'), read(ROOT / 'target/rag-p3/run-once/results.json')
    rows = p3.validate_retrieval(retrieval, questions, 'REAL')
    require(results['P3_EXECUTION_RESULT'] == 'PASS' and results['origin'] == 'REAL' and results['retryCount'] == 0, 'P3 real run')
    amended = {c['key'] for c in audit['changes'] if c['field'] == 'humanLabels'}
    output = []
    for q, r in zip(rows, results['queries'], strict=True):
        require(q['queryId'] == r['queryId'] and q['questionText'] == r['questionText'], 'Query identity')
        hits = {h['vectorId']: h for h in q['rawTopK5']}
        require(len(r['rerankResults']) == 5 and {x['vectorId'] for x in r['rerankResults']} == hits.keys(), 'Rerank completeness')
        candidates = []
        for rank in sorted(r['rerankResults'], key=lambda x: x['rerankRank']):
            h = hits[rank['vectorId']]
            require(rank['contentSha256'] == h['contentSha256'] and rank['originalRank'] == h['rank'], 'Rank/content binding')
            key = f"{q['queryId']}/{h['rank']}/{h['vectorId']}"
            human, ai = labels['humanLabels'].get(key), labels['aiSuggestions'].get(key)
            annotation = human or ai
            require(annotation and str(annotation['label']) in ('0', '1', '2'), 'Missing annotation')
            provenance = 'USER_AUTHORIZED_AI_CORRECTION' if key in amended else 'HUMAN_ORIGINAL_AS_SUPPLIED' if human else 'AI_SUGGESTION_PROVISIONAL'
            candidates.append({'vectorId': h['vectorId'], 'chunkId': h['knowledge']['chunkId'], 'originalRank': h['rank'],
                               'rerankRank': rank['rerankRank'], 'relevanceScore': rank['relevanceScore'],
                               'contentSha256': h['contentSha256'], 'title': h['knowledge']['title'],
                               'content': h['knowledge']['content'], 'label': int(annotation['label']),
                               'labelSource': provenance, 'labelKey': key})
        output.append({'queryId': q['queryId'], 'question': q['questionText'],
                       'covered': q['expectedCoverage'] == 'COVERED', 'candidates': candidates})
    require(len(output) == 12 and sum(q['covered'] for q in output) == 6, 'Dataset count')
    return output


def online_input(q, number):
    # Opaque IDs prevent C/N names, coverage, labels, scores or analyst targetMechanism leaking to model.
    return {'questionId': f'g{number:02d}', 'question': q['question'], 'candidates': [
        {'candidateId': f'c{i}', 'title': c['title'], 'content': c['content']} for i, c in enumerate(q['candidates'][:2], 1)]}


def proxy_cost(payload, system, protocol):
    token_proxy = len(encoded(payload)) + len(system.encode('utf-8')) + 2048
    amount = (Decimal(token_proxy) * Decimal(str(protocol['peakPricePerMillionTokensCny']['inputCacheMiss'])) +
              Decimal(protocol['maxOutputTokens']) * Decimal(str(protocol['peakPricePerMillionTokensCny']['output']))) / Decimal(1000000)
    return token_proxy, amount


def metrics(rows, selected):
    chosen = [c for q in rows for c in q['candidates'] if c['vectorId'] in selected[q['queryId']]]
    base = [c for q in rows for c in q['candidates'][:2]]
    covered_base = [c for q in rows if q['covered'] for c in q['candidates'][:2] if c['label'] > 0]
    covered_keep = [c for q in rows if q['covered'] for c in q['candidates'] if c['vectorId'] in selected[q['queryId']] and c['label'] > 0]
    false_reject = sum(not any(c['label'] > 0 and c['vectorId'] in selected[q['queryId']] for c in q['candidates']) for q in rows if q['covered'])
    zero_negative = sum(not selected[q['queryId']] for q in rows if not q['covered'])
    return {
        'status': 'PROVISIONAL_DEVELOPMENT_SET', 'selected': len(chosen),
        'direct2': sum(c['label'] == 2 for c in chosen), 'partial1': sum(c['label'] == 1 for c in chosen),
        'irrelevant0': sum(c['label'] == 0 for c in chosen),
        'coveredFalseRejects': false_reject, 'coveredDenominator': 6,
        'outsideCorrectRejects': zero_negative, 'outsideDenominator': 6,
        'outsideNonCoreAdmitted': sum(len(selected[q['queryId']]) for q in rows if not q['covered']),
        'coveredUsefulRetentionRelativeToTop2': len(covered_keep) / len(covered_base),
        'directRetentionRelativeToTop2': sum(c['label'] == 2 for c in chosen) / sum(c['label'] == 2 for c in base),
        'usefulRetentionRelativeToTop2': sum(c['label'] > 0 for c in chosen) / sum(c['label'] > 0 for c in base),
        'irrelevantAdmissionRate': sum(c['label'] == 0 for c in chosen) / len(chosen) if chosen else None,
        'emptyResultRatio': sum(not v for v in selected.values()) / 12,
        'zeroOneTwo': {str(n): sum(len(v) == n for v in selected.values()) for n in range(3)},
        'labelSourceSelected': dict(Counter(c['labelSource'] for c in chosen)),
        'note': 'partial1 may be useful background, not core support; outsideNonCoreAdmitted uses frozen coverage with authorized corrections, not an online rule',
        'selectedVectorIds': selected,
    }


def prepare():
    require(not RUN.exists(), 'Real run reserved; preparation cannot replace its sealed configuration')
    rows = dataset()
    protocol, system = read(ROOT / 'tools/rag-p4/protocol-v1.json'), (ROOT / 'tools/rag-p4/system-prompt.txt').read_text(encoding='utf-8')
    plan = []
    for number, q in enumerate(rows, 1):
        payload = online_input(q, number)
        path = PREP / f'inputs/g{number:02d}.json'
        write(path, payload)
        proxy, amount = proxy_cost(payload, system, protocol)
        plan.append({'questionId': payload['questionId'], 'queryId': q['queryId'], 'payloadPath': str(path),
                     'payloadSha256': digest(path.read_bytes()), 'candidateVectorIds': [c['vectorId'] for c in q['candidates'][:2]],
                     'inputTokenProxy': proxy, 'peakCostProxyCny': str(amount)})
    total = sum(Decimal(p['peakCostProxyCny']) for p in plan)
    require(total <= Decimal(str(protocol['budgetCny'])), 'Cost proxy exceeds local budget')
    a = {q['queryId']: [c['vectorId'] for c in q['candidates'][:2]] for q in rows}
    b = {q['queryId']: [c['vectorId'] for c in q['candidates'][:2] if c['relevanceScore'] >= .5] for q in rows}
    write(PREP / 'baseline-comparison.json', {'A_RERANK_TOP2': metrics(rows, a), 'B_SCORE_050': metrics(rows, b),
                                            'C_SEMANTIC_GATE': {'status': 'PENDING_REAL_DEEPSEEK_AND_SEPARATE_SEMANTIC_REVIEW'}})
    write(PREP / 'dataset-with-provenance.json', {'scope': protocol['scope'], 'queries': rows})
    write(PREP / 'plan.json', {'protocol': protocol, 'queries': plan, 'peakCostProxyCny': str(total),
                             'inputTokenProxy': sum(p['inputTokenProxy'] for p in plan), 'maxOutputTokensAll': 12 * protocol['maxOutputTokens']})
    # Preserve all P0/P1/P2/P3 scripts, reports, responses and execution locks.
    history = set(p3.history_paths(ROOT))
    for folder in ('tools/rag-p3', 'target/rag-p3'):
        history.update(p.resolve() for p in (ROOT / folder).rglob('*') if p.is_file() and p.suffix not in ('.class', '.jar', '.pyc'))
    freeze = {'schema': 'p4-freeze-v1', 'scope': protocol['scope'], 'createdUtc': utc(),
              'historySha256': {str(p): digest(p.read_bytes()) for p in sorted(history)},
              'sourcesSha256': {str(p): digest(p.read_bytes()) for p in sources()},
              'preparedSha256': {str(p): digest(p.read_bytes()) for p in PREP.rglob('*.json') if p.name != 'freeze.json'}}
    write(PREP / 'freeze.json', freeze)
    (PREP / 'freeze.sha256').write_text(digest((PREP / 'freeze.json').read_bytes()) + '\n', encoding='ascii')
    report()
    print('P4_OFFLINE_PREPARATION=COMPLETE')


def verify(tests=False):
    freeze = read(PREP / 'freeze.json')
    seal = digest((PREP / 'freeze.json').read_bytes())
    require(seal == (PREP / 'freeze.sha256').read_text(encoding='ascii').strip(), 'Freeze seal')
    for key in ('historySha256', 'sourcesSha256', 'preparedSha256'):
        for path, sha in freeze[key].items():
            require(Path(path).is_file() and digest(Path(path).read_bytes()) == sha, 'Frozen file changed: ' + path)
    if tests:
        summary = read(OFFLINE / 'test-summary.json')
        require(summary['passed'] and summary['freezeSha256'] == seal, 'Offline tests not bound to current freeze')
        for p, sha in summary['evidenceSha256'].items():
            require(digest(Path(p).read_bytes()) == sha, 'Offline test evidence changed')
    return seal


def ready():
    seal = verify()
    files = [OFFLINE / n for n in ('java-tests.json', 'python-tests.json', 'powershell-tests.json')]
    tests = [read(p) for p in files]
    require(all(t['failures'] == t['errors'] == 0 and t['skipped'] == 0 and t['tests'] > 0 for t in tests), 'Offline test gate')
    write(OFFLINE / 'test-summary.json', {'passed': True, 'freezeSha256': seal, 'tests': sum(t['tests'] for t in tests),
                                        'failures': 0, 'errors': 0, 'skipped': 0, 'paidRequests': 0,
                                        'evidenceSha256': {str(p): digest(p.read_bytes()) for p in files}})
    report()
    print('P4_GATE_PREP=READY')


def usage_cost(records, protocol):
    known = Decimal(0); complete = True; token_counts = Counter()
    for record in records:
        if record['status'] == 'NOT_STARTED':
            continue
        usage = record.get('usage', {})
        prompt, completion = usage.get('prompt_tokens'), usage.get('completion_tokens')
        if type(prompt) is not int or type(completion) is not int or prompt < 0 or completion < 0:
            complete = False; continue
        hit, miss = usage.get('prompt_cache_hit_tokens'), usage.get('prompt_cache_miss_tokens')
        cache_known = type(hit) is int and type(miss) is int and hit >= 0 and miss >= 0 and hit + miss == prompt
        hit, miss = (hit, miss) if cache_known else (0, prompt)
        prices = protocol['peakPricePerMillionTokensCny']
        known += (Decimal(hit) * Decimal(str(prices['inputCacheHit'])) + Decimal(miss) * Decimal(str(prices['inputCacheMiss'])) +
                  Decimal(completion) * Decimal(str(prices['output']))) / Decimal(1000000)
        token_counts.update(prompt_tokens=prompt, completion_tokens=completion, cache_hit_tokens=hit, cache_miss_tokens=miss)
    return {'usageTotals': dict(token_counts), 'peakGrossEstimateCnyKnown': str(known), 'usageComplete': complete,
            'actualInvoiceCny': None, 'possibleUnaccountedCharge': not complete,
            'method': 'PUBLIC_PEAK_PRICE; cache unknown assumed miss; offpeak actual may be half; not invoice'}


def java_call(item, folder, credentials):
    cp = str(OFFLINE / 'classes') + ';' + str(ROOT / 'target/classes') + ';' + (ROOT / 'target/rag-e2e/runtime-classpath.txt').read_text(encoding='utf-8-sig').strip()
    env = {n: os.environ[n] for n in ('SystemRoot', 'WINDIR', 'COMSPEC', 'TEMP', 'TMP', 'USERPROFILE') if n in os.environ}
    java = 'C:/Program Files/Java/jdk-21.0.11/bin/java.exe'
    child = subprocess.run([java, '-Djdk.httpclient.disableRetryConnect=true', '-Djdk.httpclient.enableAllMethodRetry=false',
                            '-cp', cp, 'P4DeepSeek', item['payloadPath'], str(ROOT / 'tools/rag-p4/system-prompt.txt'), str(folder)],
                           input=encoded(credentials), cwd=ROOT, env=env, capture_output=True, timeout=90)
    require(child.returncode == 0, 'Java invocation stopped; no retry, preserve lock')
    return read(folder / 'decision.json')


def execute(credentials, runner=java_call, origin='REAL'):
    seal = verify(tests=True)
    require(credentials.get('authorization') == AUTH and credentials.get('freezeSha256') == seal, 'Explicit authorization/freeze required')
    require(isinstance(credentials.get('apiKey'), str) and credentials['apiKey'].strip(), 'Missing API key')
    plan = read(PREP / 'plan.json'); protocol = plan['protocol']
    require(len(plan['queries']) == protocol['maxCalls'] == 12 and Decimal(plan['peakCostProxyCny']) <= Decimal(str(protocol['budgetCny'])), 'Plan/budget')
    RUN.mkdir(exist_ok=False)
    write(RUN / 'EXECUTION_RESERVED.lock', {'authorization': AUTH, 'freezeSha256': seal, 'createdUtc': utc()}, exclusive=True)
    require(origin in ('REAL', 'MOCK_OFFLINE'), 'Execution origin')
    result = {'origin': origin, 'P4_EXECUTION_RESULT': 'IN_PROGRESS', 'P4_QUALITY_RESULT': 'PENDING_SEPARATE_SEMANTIC_REVIEW',
              'freezeSha256': seal, 'startedUtc': utc(), 'attemptsReserved': 0, 'retryCount': 0,
              'otherCalls': {'embedding': 0, 'milvusSearch': 0, 'jev': 0, 'mysqlWrites': 0, 'milvusWrites': 0},
              'queries': [{'queryId': p['queryId'], 'questionId': p['questionId'], 'status': 'NOT_STARTED'} for p in plan['queries']]}
    write(RUN / 'results.json', result)
    try:
        for item, record in zip(plan['queries'], result['queries'], strict=True):
            verify(tests=True)
            folder = RUN / item['questionId']; folder.mkdir(exist_ok=False)
            write(folder / 'REQUEST_INTENT.lock', {'payloadSha256': item['payloadSha256'], 'freezeSha256': seal, 'utc': utc()}, exclusive=True)
            record['status'] = 'INTENT_RESERVED'; result['attemptsReserved'] += 1
            write(RUN / 'results.json', result)
            decision = runner(item, folder, credentials)
            record.update(decision)
            record['candidateVectorIds'] = item['candidateVectorIds']
            record['status'] = 'COMPLETE' if decision['gate']['status'] == 'VALIDATED_MODEL_JUDGMENT' else 'SAFE_EMPTY_FAILED'
            record['artifactSha256'] = {p.name: digest(p.read_bytes()) for p in folder.glob('*.json')}
            write(RUN / 'results.json', result)
            require(record['status'] == 'COMPLETE' and decision['attempts'] == 1, 'Gate failure; empty evidence, stop, no retry')
        result['P4_EXECUTION_RESULT'] = 'PASS'
        verify(tests=True)
    except Exception:
        # No fallback to score/top2. Intent reservation survives even if transport outcome is unknown.
        for q in result['queries']:
            if q['status'] == 'INTENT_RESERVED':
                q['status'] = 'SAFE_EMPTY_FAILED'; q['gate'] = {'status': 'SAFE_EMPTY', 'admittedCandidateIds': []}
        result['P4_EXECUTION_RESULT'] = 'FAIL_STOPPED_NO_RETRY'
    finally:
        result['endedUtc'] = utc(); result['cost'] = usage_cost(result['queries'], protocol)
        try:
            verify(); result['historyPreserved'] = True
        except Exception:
            result['historyPreserved'] = False
            result['P4_EXECUTION_RESULT'] = 'FAIL_FROZEN_EVIDENCE_CHANGED'
        write(RUN / 'results.json', result)
        (RUN / 'results.sha256').write_text(digest((RUN / 'results.json').read_bytes()) + '\n', encoding='ascii')
    analyze()
    print(('P4_EXECUTION_RESULT=' if origin == 'REAL' else 'P4_MOCK_EXECUTION_RESULT=') + result['P4_EXECUTION_RESULT'])


def reviewed_quality(rows, result, selected, review):
    require(review.get('sourceResultSha256') == digest((RUN / 'results.json').read_bytes()), 'Review must reference exact P4 result')
    require(review.get('reviewSource') == 'HUMAN_PER_ITEM' and isinstance(review.get('reviewer'), str) and review['reviewer'].strip(),
            'Semantic review requires explicit source; AI suggestions cannot be full human Ground Truth')
    queries = review.get('queries', [])
    require(len(queries) == 12 and {q['queryId'] for q in queries} == {q['queryId'] for q in rows}, 'Review all12 questions')
    semantic = {}
    mechanism_valid = {}
    for row in rows:
        reviewed = next(q for q in queries if q['queryId'] == row['queryId'])
        require(type(reviewed.get('questionMechanismDecompositionValid')) is bool, 'Question mechanism review incomplete')
        mechanism_valid[row['queryId']] = reviewed['questionMechanismDecompositionValid']
        items = reviewed.get('coreCandidates', [])
        require(len(items) == 2 and {c['vectorId'] for c in items} == {c['vectorId'] for c in row['candidates'][:2]}, 'Review candidate identity')
        for item in items:
            require(type(item.get('humanCoreSupport')) is bool and isinstance(item.get('reason'), str) and item['reason'].strip(), 'Core-support review incomplete')
            semantic[row['queryId'], item['vectorId']] = item['humanCoreSupport']
    admitted = [(q, v) for q, ids in selected.items() for v in ids]
    false_admitted = sum(not semantic[q, v] for q, v in admitted)
    base_core = sum(semantic.values())
    kept_core = sum(semantic[q, v] for q, v in admitted)
    reviewed_covered_keep = sum(any(semantic[q['queryId'], v] for v in selected[q['queryId']]) for q in rows if q['covered'])
    m = metrics(rows, selected)
    checks = {
        '12CompleteValidatedResponses': result['P4_EXECUTION_RESULT'] == 'PASS' and all(q['status'] == 'COMPLETE' for q in result['queries']),
        'allQuestionMechanismsReviewedValid': all(mechanism_valid.values()),
        'coveredCore6of6': reviewed_covered_keep == 6,
        'outsideZero6of6': m['outsideCorrectRejects'] == 6,
        'N02AndN06Zero': not selected['P3-N02'] and not selected['P3-N06'],
        'C03KeepsPartialCore': any(semantic['P3-C03', v] for v in selected['P3-C03']),
        'coveredUsefulRetentionAtLeast90Percent': m['coveredUsefulRetentionRelativeToTop2'] >= .9,
        'directRetentionAtLeast90Percent': m['directRetentionRelativeToTop2'] >= .9,
        'zeroIrrelevantAdmission': m['irrelevant0'] == 0,
        'zeroReviewedCoreFalseAdmission': false_admitted == 0,
    }
    return {'P4_QUALITY_RESULT': 'PASS_DEVELOPMENT_SET_ONLY' if all(checks.values()) else 'FAIL_DEVELOPMENT_SET',
            'checks': checks, 'reviewedCoreFalseAdmissions': false_admitted,
            'reviewedCoreRetentionRelativeToTop2': kept_core / base_core if base_core else None,
            'reviewedCoreDeletions': base_core - kept_core,
            'formalGeneralizationVerified': False,
            'labelMetricsStillProvisional': True,
            'note': 'Human semantic review applies to these24 candidates; legacy useful/direct labels retain original/authorized-AI provenance.'}


def analyze(review_file=None):
    verify()
    rows = dataset(); result = read(RUN / 'results.json')
    require(result['origin'] == 'REAL' and digest((RUN / 'results.json').read_bytes()) == (RUN / 'results.sha256').read_text().strip(), 'Saved P4 run seal')
    selected = {q['queryId']: [] for q in rows}
    for record in result['queries']:
        if record['status'] == 'COMPLETE':
            for name, sha in record['artifactSha256'].items():
                require(digest((RUN / record['questionId'] / name).read_bytes()) == sha, 'P4 response artifact changed')
            indexes = [int(c[1:]) - 1 for c in record['gate']['admittedCandidateIds']]
            selected[record['queryId']] = [record['candidateVectorIds'][i] for i in indexes]
    out = metrics(rows, selected)
    out['modelCallsReserved'] = result['attemptsReserved']; out['cost'] = result['cost']
    latencies = sorted(q['elapsedMs'] for q in result['queries'] if 'elapsedMs' in q)
    out['latency'] = {'meanMs': statistics.mean(latencies) if latencies else None, 'p95NearestRankMs': latencies[-1] if latencies else None,
                      'includesJavaClientParsingAndValidation': True, 'fullBatchStartedUtc': result['startedUtc'], 'fullBatchEndedUtc': result['endedUtc']}
    out['P4_QUALITY_RESULT'] = 'PENDING_SEPARATE_HUMAN_SEMANTIC_REVIEW'
    write(RUN / 'comparison-semantic.json', out)
    decisions = []
    for q, record in zip(rows, result['queries'], strict=True):
        require(q['queryId'] == record['queryId'], 'Result order')
        details = []
        for number, candidate in enumerate(q['candidates'][:2], 1):
            classification = next((x for x in record.get('gate', {}).get('candidates', []) if x['candidateId'] == f'c{number}'), None)
            details.append({'candidateId': f'c{number}', 'vectorId': candidate['vectorId'], 'chunkId': candidate['chunkId'],
                            'originalRank': candidate['originalRank'], 'rerankRank': candidate['rerankRank'],
                            'admitted': candidate['vectorId'] in selected[q['queryId']], 'modelDecision': classification,
                            'offlineLabel': candidate['label'], 'offlineLabelSource': candidate['labelSource']})
        decisions.append({'queryId': q['queryId'], 'question': q['question'], 'candidates': details,
                          'admittedEvidence': [{k: c[k] for k in ('vectorId', 'chunkId', 'title', 'content', 'contentSha256')}
                                               for c in q['candidates'][:2] if c['vectorId'] in selected[q['queryId']]]})
    write(RUN / 'evidence-with-rank-comparison.json', {'scope': 'DEVELOPMENT_SET_ONLY', 'queries': decisions})
    template = {'status': 'UNREVIEWED_NO_AUTO_PASS', 'sourceResultSha256': digest((RUN / 'results.json').read_bytes()),
                'reviewSource': 'UNREVIEWED', 'reviewer': '',
                'instructions': 'Review original question, mechanism decomposition, all24 model verdicts and quotes. Values 0/1/2 do not prove core support. Record reviewer/source separately. Never adjust frozen prompt to claim this development set is an independent holdout.',
                'queries': [{'queryId': q['queryId'], 'coreCandidates': [{'vectorId': c['vectorId'], 'humanCoreSupport': 'UNREVIEWED', 'reason': ''} for c in q['candidates'][:2]],
                             'questionMechanismDecompositionValid': 'UNREVIEWED', 'wholeAnswerSufficiency': 'UNREVIEWED'} for q in rows]}
    if not (RUN / 'semantic-review-template.json').exists():
        write(RUN / 'semantic-review-template.json', template, exclusive=True)
    if review_file:
        review_path = Path(review_file)
        assessment = reviewed_quality(rows, result, selected, read(review_path))
        review_sha = digest(review_path.read_bytes())
        directory = RUN / 'quality-reviews' / review_sha
        if not directory.exists():
            directory.mkdir(parents=True)
            write(directory / 'quality.json', dict(assessment, reviewSha256=review_sha), exclusive=True)
            (directory / 'review-source.json').write_bytes(review_path.read_bytes())
        print('P4_QUALITY_RESULT=' + assessment['P4_QUALITY_RESULT'])
    report()


def report():
    baseline, plan = read(PREP / 'baseline-comparison.json'), read(PREP / 'plan.json')
    a, b = baseline['A_RERANK_TOP2'], baseline['B_SCORE_050']
    c = read(RUN / 'comparison-semantic.json') if (RUN / 'comparison-semantic.json').exists() else None
    table = '| 策略 | 选出 | 有用1/2 | 无关0 | 覆盖误拒 | 库外正确拒绝 | 库外非核心条数 |\n|---|---:|---:|---:|---:|---:|---:|\n'
    for name, m in [('A Rerank Top2', a), ('B Top2 score≥0.50', b), ('C Semantic Gate', c)]:
        table += f"| {name} | {m['selected']} | {m['direct2'] + m['partial1']} | {m['irrelevant0']} | {m['coveredFalseRejects']}/6 | {m['outsideCorrectRejects']}/6 | {m['outsideNonCoreAdmitted']} |\n" if m else f'| {name} | 待真实调用 | — | — | — | — | — |\n'
    text = '# P4 核心机制语义证据准入准备\n\n' + table
    text += f'\nP3 Analyze真实离线复算：FAIL_UNDER_PRELOCKED_RULE，未改变0.50规则。B覆盖有用保留12/12，直接保留11/11；全部有用保留15/17=88.24%。B空结果4/12，输出0/1/2分别4/1/7题；N02两条、N06一条为部分相关背景，仍不支持所问机制。\n'
    text += '\n来源：修正JSON SHA ' + SEALED_INPUTS[LABEL] + '；审计SHA ' + SEALED_INPUTS[AUDIT] + '。60条中51条保留原humanLabels来源，9条为USER_AUTHORIZED_AI_CORRECTION。旧Analyze兼容视图将humanLabels统一命名HUMAN_ONLY，本报告不把9条AI修订冒充逐项人工确认。原标签、修订审计、P3响应和旧锁保持字节不变。\n'
    text += '\n实现：SemanticEvidenceGate.java严格验证JSON、两个候选身份、问题原文锚点、四种分类、版本、核心点关联及正文逐字引用；只接纳CORE_SUPPORT。missingPointIds由Java计算，部分核心支持仍准入，ALL_MODEL_DECLARED_POINTS不等于整题答案充分。引用校验只能证明文本存在，机制蕴含、问题拆分和版本解释仍依赖模型，必须审核真实输出。低信息引用仅做安全校验，不用关键词或分数替代语义判定。\n'
    text += '\nDeepSeek：复用现有DeepSeekEvaluationClient.completeJson，不启动Spring上下文/数据库；实验拦截器保存请求、响应、usage、耗时及返回模型。POST https://api.deepseek.com/chat/completions，Bearer，JSON mode，thinking=disabled，stream=false，max_tokens=3072。请求兼容旧名deepseek-v4-flash；[官方文档](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)说明实际由DeepSeek-V4.1-Flash服务，返回型号原样记录。不切换Pro等其他计费档。\n'
    text += f"\n最多12次DeepSeek、每题一次同时检查2条；其他模型/检索/数据库连接及写入为0。失败不重试、不修复重发；返回空证据并停止余下批次，锁不删除、不自动恢复。保守高峰无缓存输入代理{plan['inputTokenProxy']}（UTF-8字节+每请求2048 framing，并非实际tokens），最多输出{plan['maxOutputTokensAll']} tokens；公开价估算上界代理¥{plan['peakCostProxyCny']}，门禁¥1.00。高峰每百万输入未缓存¥2/缓存¥0.04/输出¥8，空闲半价；实际以usage和账单为准。不能计量的失败费用标记unknown。\n"
    text += '\n冻结验收：12/12有效响应；覆盖6/6保留经审核核心证据；库外6/6返回空；N02/N06拒绝，C03至少保留部分核心支持；覆盖有用保留≥90%，直接保留≥90%，标签0误准入=0。全24条语义及核心拆分需另行人工核验；未核验只能PROVISIONAL，不自动宣布质量PASS。旧P3的12题是P4开发集，成功仅支持进入后续独立题验证，不能直接宣称泛化或接生产默认。\n'
    summary = read(OFFLINE / 'test-summary.json') if (OFFLINE / 'test-summary.json').exists() else None
    text += '\n离线测试：' + (f"{summary['tests']} tests，failures=0/errors=0/skipped=0；纯Mock/规则校验，真实语义质量尚未验证。" if summary else '待执行。')
    text += '\n\n一次性运行：`pwsh -NoProfile -File .\\tools\\rag-p4\\run.ps1 -Mode ExecuteOnce`。在本机交互控制台输入唯一授权短语；密钥仅通过stdin传入子进程，不写文件/命令行，不读历史secret配置。运行前验证freeze与测试证据。\n'
    text += '\n产物：target/rag-p4/prepared/report.md、review.html、baseline-comparison.json、freeze.json；真实运行保存在target/rag-p4/run-once（每题原始请求/响应/decision、总results.json、comparison-semantic.json、semantic-review-template.json）。\n'
    (PREP / 'report.md').write_text(text, encoding='utf-8')
    cards = []
    for q in read(PREP / 'dataset-with-provenance.json')['queries']:
        items = ''.join(f"<article><b>Rerank {x['rerankRank']} · score {x['relevanceScore']:.4f} · 标签{x['label']} ({html.escape(x['labelSource'])})</b><p>{html.escape(x['title'])}</p><details><summary>Chunk {x['chunkId']} 正文</summary><pre>{html.escape(x['content'])}</pre></details></article>" for x in q['candidates'][:2])
        decision = next((r for r in read(RUN / 'results.json')['queries'] if r['queryId'] == q['queryId']), None) if c else None
        gate = '<pre>' + html.escape(json.dumps(decision.get('gate', {}), ensure_ascii=False, indent=2)) + '</pre>' if decision else '<p>Semantic Gate：待真实请求；不展示Mock作为实测结果。</p>'
        cards.append(f"<section><h2>{q['queryId']}</h2><p>{html.escape(q['question'])}</p>{items}{gate}</section>")
    page = '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>P4 Semantic Evidence Gate</title><style>body{font:16px system-ui;background:#edf2f7;color:#162334;max-width:1080px;margin:32px auto;padding:0 20px}section,article{background:white;padding:18px;margin:14px 0;border-radius:10px}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:14px ui-monospace;line-height:1.6}h1,h2{color:#12546b}</style><h1>P4 核心机制证据准入</h1><p>开发集 · PROVISIONAL · C待真实语义请求 · 0～2条证据</p><pre>' + html.escape(table) + '</pre>' + ''.join(cards) + '</html>'
    (PREP / 'review.html').write_text(page, encoding='utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('mode', choices=['prepare', 'verify', 'ready', 'execute-once', 'analyze'])
    parser.add_argument('--review')
    args = parser.parse_args()
    try:
        if args.mode == 'prepare': prepare()
        elif args.mode == 'ready': ready()
        elif args.mode == 'verify': verify(); print('P4_FROZEN_INPUTS=VERIFIED')
        elif args.mode == 'analyze': analyze(args.review)
        else: execute(json.load(sys.stdin))
    except Exception as error:
        # Static evidence validation errors are safe; provider exceptions never propagate here.
        print('P4_GATE_PREP=BLOCKED: ' + str(error), file=sys.stderr); sys.exit(1)
