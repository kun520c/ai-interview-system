"""Read-only evidence/encoding audit and side-by-side saved-response report. No model transport."""
import argparse
from collections import Counter
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
ARCHIVE = ROOT / 'target/rag-p4-repair/support-point-v1'
P4 = ROOT / 'target/rag-p4'


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read(path):
    return json.loads(Path(path).read_bytes().decode('utf-8-sig', errors='strict'))


def save(path, value):
    with Path(path).open('x', encoding='utf-8', newline='\n') as file:
        json.dump(value, file, ensure_ascii=False, indent=2)
        file.write('\n')


def preserved():
    manifest = read(ARCHIVE / 'protected-before.json')
    changed = [p for p, value in manifest['protectedSha256'].items() if not Path(p).is_file() or sha(p) != value]
    require(not changed, 'Historical evidence changed: ' + repr(changed[:3]))
    require(sha(ARCHIVE / 'original-SemanticEvidenceGate.java') == manifest['originalValidatorSha256'], 'Original validator archive SHA')
    return manifest


def strings(node):
    if isinstance(node, str):
        yield node
    elif isinstance(node, dict):
        for k, v in node.items():
            yield k
            yield from strings(v)
    elif isinstance(node, list):
        for value in node:
            yield from strings(value)


def utf8_record(path):
    raw = Path(path).read_bytes()
    decoded = raw.decode('utf-8', errors='strict')
    parsed = json.loads(decoded)
    return {'path': str(path), 'sha256': sha(path), 'bytes': len(raw), 'strictUtf8': True,
            'utf8RoundTripEqual': decoded.encode('utf-8') == raw,
            'unicodeReplacementCharCount': sum(s.count('\ufffd') for s in strings(parsed)),
            'cjkCodepoints': sum(sum('\u4e00' <= c <= '\u9fff' for c in s) for s in strings(parsed))}


def evidence_audit():
    result = read(P4 / 'run-once/results.json')
    require(sha(P4 / 'run-once/results.json') == (P4 / 'run-once/results.sha256').read_text('ascii').strip(), 'Historical P4 result SHA')
    require(result['attemptsReserved'] == 5 and sum(q['status'] == 'NOT_STARTED' for q in result['queries']) == 7, 'Five attempts/seven unattempted')
    plan = read(P4 / 'prepared/plan.json')
    p3 = read(ROOT / 'target/rag-p3/run-once/retrieval/retrieval.json')
    prepared_rows, request_rows, encoding_files, supports = [], [], [], []
    total_quotes = 0
    for item, original in zip(plan['queries'], p3['queries'], strict=True):
        path = Path(item['payloadPath'])
        input_data = read(path)
        require(input_data['question'] == original['questionText'], 'Frozen question differs from P3')
        require(sha(path) == item['payloadSha256'], 'Frozen payload SHA mismatch')
        for index, candidate in enumerate(input_data['candidates']):
            hit = next(h for h in original['rawTopK5'] if h['vectorId'] == item['candidateVectorIds'][index])
            require(candidate['content'] == hit['knowledge']['content'] and candidate['title'] == hit['knowledge']['title'], 'Body/title differs from P3')
            require(hashlib.sha256(candidate['content'].encode('utf-8')).hexdigest() == hit['contentSha256'], 'Original content SHA')
        prepared_rows.append({'questionId': item['questionId'], 'queryId': item['queryId'], 'questionExactP3': True,
                              'twoBodiesExactP3': True, 'twoTitlesExactP3': True, 'payloadShaMatched': True})
        encoding_files.append(utf8_record(path))
    for index in range(5):
        item = plan['queries'][index]; folder = P4 / 'run-once' / item['questionId']
        record = result['queries'][index]
        input_data = read(item['payloadPath'])
        request, response, decision = [read(folder / f'{name}.json') for name in ('request', 'response', 'decision')]
        for name in ('request', 'response', 'decision'):
            require(sha(folder / f'{name}.json') == record['artifactSha256'][f'{name}.json'], 'Captured artifact SHA mismatch')
        model_input = json.loads(request['messages'][1]['content'])
        system = (ROOT / 'tools/rag-p4/system-prompt.txt').read_bytes().decode('utf-8')
        require(model_input == input_data, 'Actual HTTP request differs from frozen input')
        require(request['messages'][0]['content'] == system, 'Actual HTTP system prompt changed')
        require(decision['attempts'] == 1 and decision['httpStatus'] == 200, 'Actual attempt/status')
        model = json.loads(response['choices'][0]['message']['content'])
        declared = {point['id'] for point in model['corePoints']}
        require(all(point['questionQuote'] in input_data['question'] for point in model['corePoints']), 'Response Chinese question anchors invalid')
        row = {'questionId': item['questionId'], 'queryId': item['queryId'], 'declaredPointIds': sorted(declared), 'candidates': []}
        for candidate in model['candidates']:
            body = next(c['content'] for c in input_data['candidates'] if c['candidateId'] == candidate['candidateId'])
            refs = [s['pointId'] for s in candidate['supports']]
            details = [{'pointId': s['pointId'], 'declared': s['pointId'] in declared, 'quote': s['quote'],
                        'exactBodySubstring': s['quote'] in body,
                        'verifiedStartUtf16': len(body[:body.index(s['quote'])].encode('utf-16-le')) // 2 if s['quote'] in body else None,
                        'quoteUtf8Sha256': hashlib.sha256(s['quote'].encode('utf-8')).hexdigest(),
                        'explanation': s['explanation']} for s in candidate['supports']]
            require(all(s['declared'] and s['exactBodySubstring'] for s in details), 'Unknown point or invented quote in saved responses')
            total_quotes += len(details)
            row['candidates'].append({'candidateId': candidate['candidateId'], 'verdict': candidate['verdict'],
                                      'pointReferences': refs, 'repeatedPoints': {k: v for k, v in Counter(refs).items() if v > 1},
                                      'quotes': details, 'reason': candidate['reason']})
        supports.append(row)
        request_rows.append({'questionId': item['questionId'], 'capturedRequestEqualsFrozenInput': True,
                             'systemPromptExact': True, 'responseQuestionAnchorsExact': True, 'attempts': 1, 'httpStatus': 200})
        for name in ('request', 'response', 'decision'):
            encoding_files.append(utf8_record(folder / f'{name}.json'))
    require(all(f['strictUtf8'] and f['utf8RoundTripEqual'] and f['unicodeReplacementCharCount'] == 0 for f in encoding_files), 'Saved encoding corruption')
    g05 = supports[4]
    require(g05['declaredPointIds'] == ['p1', 'p2'] and g05['candidates'][0]['pointReferences'] == ['p1', 'p1', 'p2'], 'Exact root-cause pattern')
    return {'ENCODING_STATUS': 'UTF8_INTACT_FROZEN_AND_SENT_AND_SAVED', 'prepared': prepared_rows,
            'requests': request_rows, 'utf8Files': encoding_files, 'quoteChecks': supports, 'verifiedQuoteCount': total_quotes,
            'encodingInterpretation': '12 original questions / 24 bodies exact P3; actual captured HTTP body strings equal frozen data for g01-g05. Any terminal-only garbling is not found in request or response bytes. A later console rendering cannot be diagnosed solely from these saved files.',
            'ALREADY_PAID_REQUESTS': 5, 'REMAINING_UNATTEMPTED': 7, 'historicalCost': result['cost']}


def final_report(destination):
    manifest = preserved()
    audit = evidence_audit()
    old, fixed = read(destination / 'original-revalidation.json'), read(destination / 'repaired-revalidation.json')
    historic = read(P4 / 'run-once/results.json')
    compared = []
    for index, (before, after) in enumerate(zip(old['queries'], fixed['queries'], strict=True)):
        record = historic['queries'][index]
        require(before['gate'] == record['gate'], 'Archived original validator did not reproduce historical decision')
        require(after['gate']['status'] == 'VALIDATED_MODEL_JUDGMENT', 'Repair failed saved-response verification')
        if index < 4:
            require(before['gate'] == after['gate'], 'Repair altered first four model verdicts')
        else:
            require(before['gate']['failureCode'] == 'SUPPORTED_POINT' and after['gate']['admittedCandidateIds'] == ['c1'], 'Repair root-cause verification')
        compared.append({'questionId': before['questionId'], 'queryId': record['queryId'], 'historicalStatus': before['gate']['status'],
                         'historicalFailureCode': before['gate'].get('failureCode'), 'historicalAdmitted': before['gate']['admittedCandidateIds'],
                         'repairedStatus': after['gate']['status'], 'repairedAdmitted': after['gate']['admittedCandidateIds'],
                         'verdictsUnchanged': index < 4,
                         'modelVerdicts': [(c['candidateId'], c['verdict']) for c in after['gate']['candidates']]})
    g01 = read(P4 / 'prepared/inputs/g01.json')
    quoted = '顺序遍历链表应沿 iterator 前进，反复用 get(i) 会不断重新定位。'
    require(quoted in g01['candidates'][1]['content'], 'g01 partial core-support evidence')
    semantic = {'queryId': 'P3-C01', 'candidateId': 'c2', 'vectorId': read(P4 / 'prepared/plan.json')['queries'][0]['candidateVectorIds'][1],
                'originalModelVerdict': 'BACKGROUND_ONLY', 'reviewSuggestion': 'CORE_SUPPORT_PARTIAL',
                'reviewSource': 'AI_ENGINEER_OFFLINE_REVIEW_PROVISIONAL_NOT_HUMAN_GROUND_TRUTH',
                'supportedOriginalPointIds': ['p2', 'p3'], 'quote': quoted,
                'exactBodySubstring': True, 'wholeAnswerSufficiency': 'PARTIAL_NOT_FULL',
                'reason': '正文给出反复get(i)重新定位与iterator持续前进的定位机制，直接支持原模型p2/p3；无需再完整覆盖O(n)推导和插入复杂度才能有部分核心价值。原模型reason承认这些内容却因未展开所有机制判BACKGROUND_ONLY，属于模型语义误拒。',
                'automaticVerdictOverrideApplied': False}
    plan = read(P4 / 'prepared/plan.json')
    remaining = plan['queries'][5:]
    amount = sum(Decimal(item['peakCostProxyCny']) for item in remaining)
    labelled = read(P4 / 'prepared/dataset-with-provenance.json')['queries']
    covered_pool = [c for q in labelled if q['covered'] for c in q['candidates'][:2] if c['label'] > 0]
    paid_admitted = [c for q, validated in zip(labelled[:5], fixed['queries'], strict=True)
                     for n, c in enumerate(q['candidates'][:2], 1) if f'c{n}' in validated['gate']['admittedCandidateIds']]
    remaining_covered = [c for q in labelled[5:] if q['covered'] for c in q['candidates'][:2]]
    retention_bound = {'status': 'PROVISIONAL_LABEL_BASED_BOUND_NOT_HUMAN_CORE_GROUND_TRUTH',
                       'paid5Admitted': len(paid_admitted),
                       'maximumCoveredUsefulRemaining': sum(c['label'] > 0 for c in remaining_covered),
                       'maximumCoveredUsefulKept': sum(c['label'] > 0 for c in paid_admitted + remaining_covered),
                       'coveredUsefulPool': len(covered_pool),
                       'maximumCoveredUsefulRetention': sum(c['label'] > 0 for c in paid_admitted + remaining_covered) / len(covered_pool),
                       'maximumDirectKept': sum(c['label'] == 2 for c in paid_admitted + remaining_covered),
                       'directPool': sum(c['label'] == 2 for c in covered_pool),
                       'maximumDirectRetention': sum(c['label'] == 2 for c in paid_admitted + remaining_covered) / sum(c['label'] == 2 for c in covered_pool),
                       'reason': 'Even if remaining covered C06 keeps both useful chunks, completing the six negatives cannot restore already rejected covered evidence. Does not overwrite historical labels or model verdicts.'}
    supplement = {'status': 'PLAN_ONLY_NOT_AUTHORIZED_NOT_EXECUTABLE', 'schema': 'p4-supplement-g06-g12-v1',
                  'parentResultSha256': manifest['originalResultSha256'], 'parentFreezeSha256': manifest['originalP4FreezeSha256'],
                  'scope': 'UNFINISHED_P4_DEVELOPMENT_SET_NOT_NEW_HOLDOUT', 'queries': remaining,
                  'maxDeepSeekRequests': 7, 'retryCount': 0, 'maxCandidatesPerCall': 2,
                  'otherModelAndRetrievalCalls': 0, 'databaseConnectionsAndWrites': 0,
                  'peakCostProxyCny': str(amount), 'maxOutputTokens': 7 * 3072, 'proposedBudgetCny': '0.50',
                  'pricingSource': plan['protocol']['pricingSource'], 'pricePerMillionPeakCny': plan['protocol']['peakPricePerMillionTokensCny'],
                  'inputTokenProxy': sum(item['inputTokenProxy'] for item in remaining),
                  'proposedIndependentRunDirectory': str(ROOT / 'target/rag-p4-supplement-g06-g12-v1/run-once'),
                  'proposedAuthorizationPhrase': 'AUTHORIZE P4 SUPPLEMENT G06 G12 7 ONCE',
                  'mustSealBeforeAuthorization': {'fixedValidatorSha256': sha(ROOT / 'tools/rag-p4/SemanticEvidenceGate.java'),
                                                 'originalPromptSha256': sha(ROOT / 'tools/rag-p4/system-prompt.txt'),
                                                 'freshRunnerFreezeAndOfflineTests': 'REQUIRED_NOT_IMPLEMENTED_BY_THIS_REPAIR'},
                  'rules': ['Never call g01-g05 again; use their saved responses for comparisons.',
                            'Never resume ExecuteOnce or bypass/update its locks/freeze.',
                            'Copy only g06-g12 inputs with exact original byte SHA into a separate sealed plan.',
                            'Create independent run lock and per-question intent/HTTP-attempt locks, no retries/resume.',
                            'Keep original semantic prompt; prompt adjustment would require another separately versioned experiment.',
                            'Any later aggregate is a linked composite development-set analysis; never overwrite original P4 results.',
                            'Real calls require new explicit authorization, no executable continuation added now.']}
    tests = read(destination / 'repair-tests.json'); regress = read(destination / 'regression-tests/java-tests.json')
    require(all(t['failures'] == t['errors'] == t['skipped'] == 0 for t in (tests, regress)), 'Offline tests failed')
    summary = {'P4_FAILURE_ROOT_CAUSE': 'VALIDATOR_REJECTED_MULTIPLE_GROUNDED_QUOTES_FOR_SAME_POINT',
               'P4_OFFLINE_REVERIFICATION': 'PASS', 'ENCODING_STATUS': audit['ENCODING_STATUS'],
               'ALREADY_PAID_REQUESTS': 5, 'REMAINING_UNATTEMPTED': 7, 'newModelRequests': 0,
               'protectedFilesUnchanged': len(manifest['protectedSha256']), 'historicalP4ExecutionResult': historic['P4_EXECUTION_RESULT'],
               'realSemanticQualityResult': 'UNRESOLVED_MODEL_FALSE_REJECTION_AND_SEVEN_UNATTEMPTED',
               'offlineTests': {'tests': tests['tests'] + regress['tests'], 'failures': 0, 'errors': 0, 'skipped': 0},
               'originalValidatorSha256': manifest['originalValidatorSha256'],
               'fixedValidatorSha256': sha(ROOT / 'tools/rag-p4/SemanticEvidenceGate.java'),
               'originalFreezeUnmodified': True, 'oldEntrySealIntentionallyNotResealed': True,
               'note': 'Offline PASS means saved responses validate after minimal bug fix. It does not turn the original failed run or model semantic quality into PASS.'}
    initial_errors = [read(p) for p in sorted(ARCHIVE.glob('reverification-*/first-attempt-test-error.json'))]
    summary['earlierTestFixtureErrors'] = initial_errors
    for name, value in [('encoding-and-citation-audit.json', audit), ('side-by-side.json', compared),
                        ('g01-c2-provisional-semantic-review.json', semantic), ('supplement-plan.json', supplement),
                        ('provisional-retention-upper-bound.json', retention_bound), ('summary.json', summary)]:
        save(destination / name, value)
    lines = ['| 题目 | 历史校验 | 原准入 | 修复后离线校验 | 离线准入 |', '|---|---|---|---|---|']
    for row in compared:
        lines.append(f"| {row['queryId']} | {row['historicalStatus']} {row['historicalFailureCode'] or ''} | {','.join(row['historicalAdmitted']) or '0条'} | {row['repairedStatus']} | {','.join(row['repairedAdmitted'])} |")
    report = '# P4 第五题中断：纯离线归因与复验\n\n' + '\n'.join(f'{key}={summary[key]}' for key in
              ('P4_FAILURE_ROOT_CAUSE', 'P4_OFFLINE_REVERIFICATION', 'ENCODING_STATUS', 'ALREADY_PAID_REQUESTS', 'REMAINING_UNATTEMPTED')) + '\n\n'
    report += 'g05原始HTTP 200、finish_reason=stop。corePoints只声明p1、p2；c1 supports依次p1、p1、p2，c2无supports。没有未知pointId。c1的两条p1引用不同正文段：一条解释RR首次一致性读快照和后续普通SELECT，一条解释FOR UPDATE的锁定读、最新数据和排他锁；第三条p2解释不应将所有SQL视为历史快照。这些是对应核心机制正文，非标题/链接/参考列表。三条全文、SHA及UTF-16偏移详见encoding-and-citation-audit.json。\n\n'
    report += '旧代码`points.containsKey(pointId) && supported.add(pointId)`将已引用过的已声明点误判为SUPPORTED_POINT错误。原模型结构允许多段证据且原prompt未要求pointId唯一；此次属于校验器过严，不是未知ID/伪造引用或HTTP故障。我写的唯一性约束与这一证据结构不一致。主要修复将声明校验和覆盖集合更新拆开：每段仍独立验证原文和信息性，覆盖仍按distinct point计数。负例测试另外发现旧低信息检查对嵌在Markdown中的纯URL及`- Primary/Secondary:`参考条目识别不全，补充这两种拒绝条件；这是加强依据校验，不是放宽语义准入。\n\n'
    report += '\n'.join(lines) + '\n\n'
    report += '先用归档原源码重新编译并复现5/5历史gate结果；再用修复源码离线校验同样响应：5/5通过，前4题gate JSON完全相同，第5题准入c1。该PASS仅是格式/引用/程序复验，原P4执行仍FAIL_STOPPED_NO_RETRY，不能据此称语义质量PASS。\n\n'
    report += f"编码：12道冻结题面、24条正文及标题与P3字符串和内容SHA一致；g01～g05真实HTTP user payload逐项等于prepared输入，system prompt也逐字相同。27个JSON文件UTF-8严格解码/字节回编码一致，无U+FFFD；五组响应questionQuote和17段引用均能验证。未发现冻结输入、保存文本或已发送请求乱码；若终端出现乱码，与这些完好字节不同，应归为显示层问题。本次没有现场复现终端乱码。\n\n"
    report += f"g01 c2专项语义复核：原文“{quoted}”直接支持原模型p2（反复定位成本）和p3（游标前进避免重新定位）。按允许部分核心支持的规则，应建议CORE_SUPPORT_PARTIAL；原模型将缺少完整复杂度展开当成BACKGROUND_ONLY，存在语义误拒。这里只记录AI工程师离线复核建议，非人工Ground Truth，不改标签、不强行覆盖模型分类、不调原prompt。因此修复后g01仍只准入c1，这个质量风险未解决。\n\n"
    report += f"保留率上限（修正标签PROVISIONAL）：前5题修复后仍只选7条；剩余覆盖题C06最多再选2条。即使后7题得到最佳结果，覆盖有用保留至多{retention_bound['maximumCoveredUsefulKept']}/{retention_bound['coveredUsefulPool']}={100*retention_bound['maximumCoveredUsefulRetention']:.2f}%，直接保留至多{retention_bound['maximumDirectKept']}/{retention_bound['directPool']}={100*retention_bound['maximumDirectRetention']:.2f}%，仍低于原冻结90%门槛。后7题补测只能补齐拒绝能力证据，不能修复已有误拒或把本版质量变成PASS；不建议以‘补齐后通过’为理由收费补测。下一版语义规则需要另行版本化，保留原失败记录。\n\n"
    report += f"测试：专项{tests['tests']}项 + 原Java Mock回归{regress['tests']}项，总{summary['offlineTests']['tests']}；failures=0/errors=0/skipped=0。测试覆盖重复p1合法多引文、同点后续伪造/标题/URL/参考列表、未知点、引用跨候选、缺依据、版本冲突、覆盖去重和g01原分类保持。另有真实5响应复验与12输入/5请求编码审计。新增HTTP=0，Embedding/Milvus/数据库连接与写入=0。\n\n"
    if initial_errors:
        report += '首轮专项测试的URL负例错误地从不含URL的g05 c1取测试片段，在第6项触发NoSuchElementException：执行6项，5项通过、failures=0/errors=1，后7项未执行。测试夹具改为从真实c2正文构造内存负例后，最终13项专项和28项原回归均通过。首轮目录和first-attempt-test-error.json保留，没有删除失败记录。\n\n'
    report += f"历史{summary['protectedFilesUnchanged']}个文件全部SHA不变，包括prepared、全部request/response/decision/results和锁、旧编译产物、P0～P3证据。改动仅tools/rag-p4/SemanticEvidenceGate.java及新增tools/rag-p4-repair；新产物只写target/rag-p4-repair。原freeze继续引用旧源码SHA，故不对旧ExecuteOnce重新封印或解除门禁。\n\n"
    report += f"已发生5次请求：输入8398 tokens、输出3490，公开高峰价usage估算¥{historic['cost']['peakGrossEstimateCnyKnown']}，实际扣费仍需账单。\n\n"
    report += f"后7题只提供独立方案：g06/P3-C06与g07～g12/P3-N01～N06，在新的target/rag-p4-supplement-g06-g12-v1/run-once目录使用独立冻结配置及锁；输入/原prompt不变，采用修复校验器。不得续跑旧ExecuteOnce或重发g01～g05。最大7次DeepSeek，0重试；输入字节代理{supplement['inputTokenProxy']}、最大输出21504 tokens，按[官方当前Flash高峰价](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)无缓存保守代理¥{amount}，建议门禁¥0.50。代理不是实际token或账单。需要后续新授权及独立入口封存；本次未实现可收费的续跑入口、未预留补测HTTP锁、未发请求。\n"
    (destination / 'report.md').write_text(report, encoding='utf-8')
    artifacts = {str(p): sha(p) for p in destination.glob('*.json')}
    save(destination / 'artifact-sha256.json', artifacts)
    preserved()
    for key in ('P4_FAILURE_ROOT_CAUSE', 'P4_OFFLINE_REVERIFICATION', 'ENCODING_STATUS', 'ALREADY_PAID_REQUESTS', 'REMAINING_UNATTEMPTED'):
        print(key + '=' + str(summary[key]))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['preflight', 'report'])
    parser.add_argument('--output')
    args = parser.parse_args()
    try:
        preserved()
        if args.mode == 'preflight':
            evidence_audit(); print('P4_REPAIR_EVIDENCE_AND_ENCODING=PREFLIGHT_PASS')
        else:
            destination = Path(args.output).resolve()
            require(destination.is_relative_to(ARCHIVE), 'Independent report directory only')
            final_report(destination)
    except Exception as error:
        print('P4_OFFLINE_REVERIFICATION=FAIL: ' + str(error), file=sys.stderr)
        sys.exit(1)
