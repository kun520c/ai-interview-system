"""Offline evidence verification and independent human metrics. Never imports a network client."""
import argparse
import hashlib
import json
import math
import pathlib
import statistics

ROOT = pathlib.Path(__file__).resolve().parents[3]
PREP = ROOT / 'target/rag-p4-2/prepared'
RUN = ROOT / 'target/rag-p4-2/run-once'
SEAL = ROOT / 'target/rag-p4-2/execution-prep-20261014/manifest.json'
DELTA = SEAL.parent / 'evidence/delta.json'
DELTA_SHA = '4359a5f12384f6f79a3fc66b6ac0bdbbcba8d5b56be2c08a1f2f3277d0845222'
PREP_SHA = '5309ba13666879387a2c876c98deaf2dd2a34003de26fc83169a40cff014d064'
QUESTIONS_SHA = '73ca5d65649a0d2270a3f6cc209f36a0d2c775e7e43e4079f1f269a2e1ba318c'
PLAN_SHA = '16df13f6f7ed35968b6aa81b4f6504df36c4d07aabeee69a30b2742b7089fd47'
STAGES = ('EMBEDDING', 'MILVUS_SEARCH', 'RERANK', 'SEMANTIC_GATE')


def require(value, code):
    if not value:
        raise ValueError(code)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, 'DUPLICATE_JSON_KEY')
            result[key] = value
        return result
    return json.loads(path.read_text(encoding='utf-8-sig'), object_pairs_hook=pairs,
                      parse_constant=lambda value: require(False, 'NON_FINITE_JSON'))


def save_new(path, value):
    data = (json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n').encode('utf-8')
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        require(path.read_bytes() == data, 'REFUSE_OVERWRITE_EVIDENCE')
    else:
        with path.open('xb') as file:
            file.write(data)


def verify_map(mapping, root=ROOT):
    require(isinstance(mapping, dict) and mapping, 'EMPTY_SHA_MAP')
    for relative, expected in mapping.items():
        path = (root / relative).resolve()
        require(path.is_relative_to(root.resolve()) and path.is_file() and sha(path) == expected, 'SHA_DRIFT:' + relative)


def verify_baseline_map(mapping, entries, root=ROOT):
    overrides = {e['path']: e for e in entries}
    require(len(overrides) == len(entries) and set(overrides) <= set(mapping), 'DELTA_PATH_BINDING')
    for relative, old in mapping.items():
        entry = overrides.get(relative)
        if entry:
            require(entry['oldSha256'] == old and entry['currentSha256'] != old, 'DELTA_OLD_SHA_BINDING')
        verify_map({relative: entry['currentSha256'] if entry else old}, root)


def verify_prepared():
    for name, expected in [('freeze.json', PREP_SHA), ('holdout-questions-v1.json', QUESTIONS_SHA), ('validation-plan-v1.json', PLAN_SHA)]:
        require(sha(PREP / name) == expected, 'P42_FROZEN_DRIFT:' + name)
    require(sha(DELTA) == DELTA_SHA, 'EXACT_DELTA_SEAL_DRIFT')
    delta = read(DELTA)
    require(delta['originalFreezeSha256'] == PREP_SHA and delta['protectedHistorySha256'] == sha(PREP / 'protected-history.json'), 'DELTA_ORIGINAL_BINDING')
    frozen, history = read(PREP / 'freeze.json')['filesSha256'], read(PREP / 'protected-history.json')
    require(not (frozen.keys() & history.keys()), 'BASELINE_DUPLICATE_PATH')
    baseline = history | frozen
    require(len(baseline) == delta['baselineCount'] == 1975 and len(delta['differences']) == 12 and delta['unchangedCount'] == 1963, 'BASELINE_COUNT_BINDING')
    verify_baseline_map(baseline, delta['differences'])
    verify_map(delta['filesSha256'])


def verify_class_inventory(seal, root=ROOT):
    actual_classes = {p.relative_to(root).as_posix() for folder in seal['classDirectories'] for p in (root / folder).rglob('*.class')}
    sealed_classes = {p for p in seal['filesSha256'] if p.endswith('.class')}
    require(actual_classes == sealed_classes, 'UNSEALED_CLASS_OR_MISSING_CLASS')


def verify_execution():
    verify_prepared()
    require(sha(SEAL) == SEAL.with_name('manifest.sha256').read_text(encoding='ascii').strip(), 'EXECUTION_SEAL_DRIFT')
    seal = read(SEAL)
    require(seal['prepSha256'] == PREP_SHA, 'EXECUTION_PREP_BINDING')
    require(seal['deltaSha256'] == DELTA_SHA, 'EXECUTION_DELTA_BINDING')
    verify_map(seal['filesSha256'])
    verify_class_inventory(seal)


def seal_execution():
    verify_prepared()
    directory = SEAL.parent
    classes = ['target/rag-p4-2/execution-prep-20261014/classes', 'target/rag-p4-2/offline/classes', 'target/rag-p3/offline/classes', 'target/classes']
    files = set((ROOT / 'tools/rag-p4-2/execution').glob('*'))
    files.add(ROOT / 'tools/rag-p4-2/execute.ps1')
    files.add(ROOT / 'tools/rag-p4-2/EXECUTION.md')
    files.add(ROOT / 'target/rag-e2e/runtime-classpath.txt')
    files.add(DELTA)
    for name in ('dependency-paths.json', 'jdeps.log', 'core-mock-tests.json', 'core-mock.log', 'runner-javac.log', 'runner-mock.log'):
        require((directory / name).is_file(), 'OFFLINE_EVIDENCE_MISSING:' + name)
        files.add(directory / name)
    files.add(ROOT / 'docs/RAG_P42_EXECUTION_PREP_20261014.md')
    for folder in ('core-replay-root',):
        files.update(p for p in (directory / folder).rglob('*') if p.is_file())
    mock = pathlib.Path((directory / 'mock-complete-path.txt').read_text(encoding='utf-8'))
    require(mock.is_relative_to(directory) and (mock / 'artifacts.json').is_file(), 'MOCK_REPLAY_EVIDENCE_MISSING')
    files.add(directory / 'mock-complete-path.txt')
    files.update(p for p in mock.rglob('*') if p.is_file())
    for folder in classes:
        require((ROOT / folder).is_dir(), 'CACHED_CLASSES_MISSING')
        files.update((ROOT / folder).rglob('*.class'))
    for dep in (ROOT / 'target/rag-e2e/runtime-classpath.txt').read_text(encoding='utf-8-sig').strip().split(';'):
        p = pathlib.Path(dep)
        require(p.is_file() and p.suffix == '.jar', 'CACHED_DEPENDENCY_MISSING')
        # Dependency jars are outside the repository: bind separately with their absolute paths.
    deps = {str(pathlib.Path(p)): sha(pathlib.Path(p)) for p in (ROOT / 'target/rag-e2e/runtime-classpath.txt').read_text(encoding='utf-8-sig').strip().split(';')}
    tests = read(directory / 'tests.json')
    require(tests['failures'] == tests['errors'] == tests['skipped'] == tests['newRealCalls'] == 0, 'OFFLINE_GATE_FAILED')
    suites = [read(directory / name) for name in ('java-tests.json', 'python-tests.json', 'core-mock-tests.json')]
    require(tests['tests'] == sum(s['tests'] for s in suites) and all(s['failures'] == s['errors'] == s['skipped'] == 0 for s in suites), 'OFFLINE_SUITE_BINDING')
    require(suites[0]['newRealCalls'] == suites[1]['newRealCalls'] == suites[2]['newRealModelCalls'] == 0, 'OFFLINE_REAL_CALLS_FORBIDDEN')
    files.add(directory / 'tests.json')
    files.add(directory / 'java-tests.json')
    files.add(directory / 'python-tests.json')
    save_new(SEAL, dict(schema='P42_EXECUTION_SEAL_V2_EXACT_DELTA', prepSha256=PREP_SHA, deltaSha256=DELTA_SHA, questionSha256=QUESTIONS_SHA, planSha256=PLAN_SHA,
                       classDirectories=classes, filesSha256={p.relative_to(ROOT).as_posix(): sha(p) for p in sorted(files) if p.is_file()},
                       dependenciesSha256=deps, authorization='AUTHORIZE P4.2 8 EACH ONCE MAX24 CNY1',
                       maxPaidCalls=24, maxSearch=8, retries=0, budgetReferenceCny=1.0, costProxyCny=0.707392,
                       estimateIsInvoiceHardCap=False, newRealCalls=0))
    side = SEAL.with_name('manifest.sha256')
    data = (sha(SEAL) + '\n').encode('ascii')
    if side.exists():
        require(side.read_bytes() == data, 'EXECUTION_SIDECAR_DRIFT')
    else:
        side.write_bytes(data)
    verify_execution()


def dependencies():
    for path, expected in read(SEAL)['dependenciesSha256'].items():
        require(pathlib.Path(path).is_file() and sha(pathlib.Path(path)) == expected, 'DEPENDENCY_SHA_DRIFT')


def distribution(values):
    values = sorted(values)
    return dict(count=len(values), medianMs=statistics.median(values) if values else None,
                p95Ms=values[max(0, math.ceil(.95 * len(values)) - 1)] if values else None,
                totalMs=sum(values))


def analyze(run=RUN, labels_path=None, verify=True):
    if verify:
        verify_execution()
    require((run / 'EXECUTION_RESERVED.lock').is_file(), 'NO_EXECUTION_LOCK')
    lock = read(run / 'EXECUTION_RESERVED.lock')
    questions = read(run / 'frozen-questions.json')['questions']
    require(hashlib.sha256((json.dumps({'scope': 'NEW_PRE_RETRIEVAL_SET_NEVER_USED_IN_SEMANTIC_MOCKS', 'questions': questions}, ensure_ascii=False, indent=2) + '\n').encode()).hexdigest() == QUESTIONS_SHA,
            'QUESTION_SNAPSHOT_DRIFT')
    require(lock['prepSha256'] == PREP_SHA, 'LOCK_PREP_BINDING')
    if verify:
        require(lock['executionSealSha256'] == sha(SEAL), 'LOCK_EXECUTION_BINDING')
    artifacts_path = run / 'artifacts.json'
    integrity = 'INCOMPLETE_UNSEALED_AFTER_INTERRUPTION'
    if artifacts_path.exists():
        require(sha(artifacts_path) == (run / 'artifacts.sha256').read_text(encoding='ascii').strip(), 'ARTIFACT_SEAL_DRIFT')
        verify_map(read(artifacts_path), run)
        inventory = {p.relative_to(run).as_posix() for p in run.rglob('*') if p.is_file()} - {'artifacts.json', 'artifacts.sha256'}
        require(inventory == set(read(artifacts_path)), 'RUN_INVENTORY_DRIFT')
        integrity = 'SHA_VERIFIED'
    calls = dict.fromkeys(STAGES, 0)
    receipts = []
    rows = []
    known_cost = 0.0
    cost_complete = True
    for q in questions:
        qid = q['questionId']
        row_path = run / qid / 'result.json'
        row = read(row_path) if row_path.exists() else dict(questionId=qid, status='INTERRUPTED_OR_NOT_STARTED')
        rows.append(row)
        for stage in STAGES:
            d = run / qid / stage
            if not (d / 'ATTEMPT_RESERVED.lock').exists():
                continue
            calls[stage] += 1
            intent = read(d / 'ATTEMPT_RESERVED.lock')
            require(intent['attempt'] == 1 and intent['stage'] == stage and intent['questionId'] == qid, 'ATTEMPT_BINDING')
            require(sha(d / 'request.json') == intent['requestSha256'], 'REQUEST_SHA_DRIFT')
            receipt = read(d / 'receipt.json') if (d / 'receipt.json').exists() else intent
            require(receipt['requestSha256'] == intent['requestSha256'], 'RECEIPT_INTENT_DRIFT')
            if 'responseSha256' in receipt:
                require(sha(d / 'response.json') == receipt['responseSha256'], 'RESPONSE_SHA_DRIFT')
            if 'hydratedSha256' in receipt:
                require(sha(d / 'hydrated.json') == receipt['hydratedSha256'], 'HYDRATION_SHA_DRIFT')
            if (d / 'validation.json').exists():
                receipt['validation'] = read(d / 'validation.json')
            if stage != 'MILVUS_SEARCH':
                if 'estimatedCny' in receipt:
                    usage = receipt.get('usage', {})
                    field = 'total_tokens' if stage == 'EMBEDDING' else 'prompt_tokens'
                    token_in = usage.get(field)
                    token_out = usage.get('completion_tokens') if stage == 'SEMANTIC_GATE' else 0
                    require(type(token_in) is int and token_in >= 0 and type(token_out) is int and token_out >= 0, 'UNTRUSTWORTHY_SAVED_USAGE')
                    cost = (token_in * (2 if stage == 'SEMANTIC_GATE' else .5) + token_out * (8 if stage == 'SEMANTIC_GATE' else 0)) / 1e6
                    require(math.isclose(cost, receipt['estimatedCny'], abs_tol=1e-12) and receipt['inputTokensCharged'] == token_in and receipt['outputTokensCharged'] == token_out, 'SAVED_COST_DRIFT')
                    if stage == 'SEMANTIC_GATE':
                        require(usage.get('total_tokens') == token_in + token_out, 'SAVED_USAGE_TOTAL_DRIFT')
                    known_cost += cost
                else:
                    cost_complete = False
            receipts.append(receipt)
    require(all(v <= 8 for v in calls.values()) and sum(calls[s] for s in STAGES if s != 'MILVUS_SEARCH') <= 24, 'CALL_CAP_BREACH')
    summary = read(run / 'summary.json') if (run / 'summary.json').exists() else {}
    require(not summary or calls == summary['calls'], 'SUMMARY_CALL_DRIFT')
    require(not summary or math.isclose(known_cost, summary['estimatedKnownCny'], abs_tol=1e-12), 'SUMMARY_COST_DRIFT')
    complete = integrity == 'SHA_VERIFIED' and summary.get('executionStatus') == 'COMPLETE' and summary.get('resourcesUnchanged') is True and all(r['status'] == 'COMPLETE' for r in rows) and all(v == 8 for v in calls.values()) and all(r.get('validation', {}).get('status') == 'VALIDATED' for r in receipts)
    if complete:
        require(summary['questions'] == rows and all(r['status'] in ('SUCCESS', 'RESPONSE_RECEIVED_USAGE_VERIFIED') for r in receipts), 'SUMMARY_RESULT_DRIFT')
    result = dict(schema='P42_ANALYSIS_V1', origin=lock['origin'], integrity=integrity, executionStatus='COMPLETE' if complete else 'STOPPED_OR_INCOMPLETE',
                  qualityStatus='PENDING_INDEPENDENT_HUMAN_REVIEW', calls=calls, paidAttempts=sum(calls[s] for s in STAGES if s != 'MILVUS_SEARCH'), retries=0,
                  actualUsage=[{k: r[k] for k in ('questionId', 'stage', 'requestedModel', 'returnedModel', 'httpStatus', 'status', 'usage', 'failureCode') if k in r} for r in receipts],
                  latency={s: distribution([r['elapsedMs'] for r in receipts if r['stage'] == s and 'elapsedMs' in r]) for s in STAGES},
                  fullStageLatency={s: distribution([r['stageElapsedMs'][s] for r in rows if s in r.get('stageElapsedMs', {})]) for s in STAGES},
                  returnedResponses={s: sum(r['stage'] == s and 'responseSha256' in r for r in receipts) for s in STAGES},
                  ambiguousReservedAttempts=[dict(questionId=r['questionId'], stage=r['stage']) for r in receipts if 'responseSha256' not in r],
                  wallElapsedMs=summary.get('wallElapsedMs'), knownEstimatedCny=known_cost, costCompleteForAttempts=cost_complete,
                  budgetReferenceCny=1.0, originalProxyCny=.707392, estimateIsInvoiceHardCap=False,
                  failures=[r for r in receipts if r['status'] not in ('SUCCESS', 'RESPONSE_RECEIVED_USAGE_VERIFIED') or r.get('validation', {}).get('status') == 'FAILED_STOP'],
                  perQuestion=[dict(questionId=r['questionId'], executionStatus=r['status'], gateStatus=r.get('gate', {}).get('status'),
                                    missingPrimaryRequirementIds=r.get('gate', {}).get('missingPrimaryRequirementIdsAcrossAdmitted'),
                                    admittedCandidateIds=r.get('finalAdmittedCandidateIds', []), failureCode=r.get('failureCode'),
                                    evidenceInsufficiency='NOT_INDEPENDENTLY_REVIEWED', modelMisjudgment='NOT_INDEPENDENTLY_REVIEWED') for r in rows])
    if labels_path is None:
        return result
    require(complete, 'QUALITY_REQUIRES_COMPLETE_EXECUTION')
    labels = read(labels_path)
    require(labels.get('schema') == 'P42_INDEPENDENT_HUMAN_REVIEW_V1' and labels.get('runArtifactsSha256') == sha(artifacts_path) and
            labels.get('questionSha256') == QUESTIONS_SHA, 'REVIEW_RUN_BINDING')
    reviewer = labels.get('reviewer', {})
    require(reviewer.get('type') == 'HUMAN' and reviewer.get('independent') is True and reviewer.get('labelsAuthoredWithoutModelPredictions') is True and
            isinstance(reviewer.get('name'), str) and reviewer['name'].strip(), 'INDEPENDENT_BLIND_HUMAN_REVIEW_REQUIRED')
    by_question = {r['questionId']: r for r in labels['questions']}
    require(len(by_question) == len(labels['questions']) == 8 and set(by_question) == {q['questionId'] for q in questions}, 'REVIEW_QUESTION_COVERAGE')
    audit = {r['questionId']: r['expectedCoverage'] for r in read(PREP / 'coverage-audit-v1.json')['reviews']}
    covered_hit = negative_reject = wrong = useful_top2 = useful_retained = useful_top5 = 0
    comparisons = {s: dict(selected=0, coreUseful=0, backgroundOrNone=0) for s in ('MILVUS_TOP2', 'RERANK_TOP2', 'RERANK_TOP2_PRIMARY_GATE')}
    challenge = False
    uncertain = False
    claim_errors = 0
    for q, row, detail in zip(questions, rows, result['perQuestion']):
        qid = q['questionId']
        review = by_question[qid]
        require(review.get('coverageConfirmed') is True and review.get('coverage') in ('COVERED', 'HARD_NEGATIVE'), 'COVERAGE_CONFIRMATION_REQUIRED')
        challenge |= review['coverage'] != audit[qid]
        raw = read(run / qid / 'MILVUS_SEARCH/hydrated.json')
        candidates = {c['vectorId']: c for c in review['candidates']}
        require(len(candidates) == len(review['candidates']) == len(raw) and set(candidates) == {c['vectorId'] for c in raw}, 'ALL_TOP5_LABELS_REQUIRED')
        primary = {r['id'] for r in q['requirements'] if r['role'] == 'PRIMARY_MECHANISM'}
        for hit in raw:
            label = candidates[hit['vectorId']]
            require(label.get('contentSha256') == hit['contentSha256'] and label.get('label') in ('CORE_SUPPORT', 'BACKGROUND_ONLY', 'NO_SUPPORT', 'UNCERTAIN') and
                    label.get('versionCondition') in ('MATCH', 'MISMATCH', 'UNCERTAIN'), 'LABEL_CONTENT_OR_KIND')
            ids = label.get('primaryRequirementIds', [])
            require(isinstance(ids, list) and len(ids) == len(set(ids)) and set(ids) <= primary, 'REVIEW_REQUIREMENT_IDS')
            require((bool(ids) and label['versionCondition'] == 'MATCH') if label['label'] == 'CORE_SUPPORT' else not ids, 'REVIEW_CORE_CONTRACT')
            uncertain |= label['label'] == 'UNCERTAIN' or label['versionCondition'] == 'UNCERTAIN'
        useful = {vid for vid, c in candidates.items() if c['label'] == 'CORE_SUPPORT' and c['versionCondition'] == 'MATCH'}
        top2 = {c['vectorId'] for c in row['ranking'][:2]}
        admitted = set(row['finalAdmittedCandidateIds'])
        useful_top2 += len(useful & top2)
        useful_retained += len(useful & admitted)
        useful_top5 += len(useful)
        for strategy, selected in [('MILVUS_TOP2', {h['vectorId'] for h in raw[:2]}), ('RERANK_TOP2', top2), ('RERANK_TOP2_PRIMARY_GATE', admitted)]:
            comparisons[strategy]['selected'] += len(selected)
            comparisons[strategy]['coreUseful'] += len(selected & useful)
            comparisons[strategy]['backgroundOrNone'] += len(selected - useful)
        wrong_here = admitted - useful
        wrong += len(wrong_here)
        supported = set()
        errors_here = []
        for prediction in row['gate']['candidates']:
            c = candidates[prediction['candidateId']]
            if prediction['candidateId'] in admitted:
                require(c.get('gateAssessment') in ('CORRECT', 'INCORRECT'), 'ADMITTED_QUOTES_SEMANTIC_REVIEW_REQUIRED')
                claimed = {s['requirementId'] for s in prediction['supports'] if s['relation'] == 'DIRECT_MECHANISM' and s['requirementId'] in primary}
                if c['gateAssessment'] != 'CORRECT' or not claimed <= set(c['primaryRequirementIds']):
                    claim_errors += 1
                    errors_here.append(dict(vectorId=prediction['candidateId'], issue='INCORRECT_QUOTE_ENTAILMENT_OR_CONDITION'))
                else:
                    supported.update(s['requirementId'] for s in prediction['supports'] if s['relation'] == 'DIRECT_MECHANISM' and s['requirementId'] in c['primaryRequirementIds'])
            if (prediction['verdict'] == 'CORE_SUPPORT') != (c['label'] == 'CORE_SUPPORT'):
                errors_here.append(dict(vectorId=prediction['candidateId'], issue='FALSE_ADMISSION' if prediction['verdict'] == 'CORE_SUPPORT' else 'USEFUL_REJECTION'))
        if review['coverage'] == 'COVERED':
            covered_hit += bool(useful & admitted)
        else:
            negative_reject += not admitted and row['gate']['status'] == 'EMPTY_NO_PRIMARY_SUPPORT'
        detail.update(coverage=review['coverage'], coreEvidenceHit=bool(useful & admitted), wrongAdmittedIds=sorted(wrong_here),
                      evidenceInsufficiency=sorted(primary - supported), modelMisjudgment=errors_here,
                      usefulLostFromTop2=sorted((useful & top2) - admitted), usefulLostFromTop5=sorted(useful - admitted))
    retention = useful_retained / useful_top2 if useful_top2 else None
    metrics = dict(coveredCoreEvidenceHit=f'{covered_hit}/4', hardNegativeNormalCorrectRejection=f'{negative_reject}/4',
                   backgroundOrNoSupportAdmitted=wrong, usefulTop2Denominator=useful_top2, usefulRetained=useful_retained,
                   usefulRetentionRelativeToTop2=retention, usefulTop5Denominator=useful_top5,
                   usefulRetentionRelativeToTop5=useful_retained / useful_top5 if useful_top5 else None,
                   independentQuoteConditionErrors=claim_errors, coverageChallenge=challenge, uncertainLabels=uncertain)
    result['metrics'] = metrics
    result['comparisons'] = comparisons
    result['evidenceCountHistogram'] = {str(n): sum(len(r['finalAdmittedCandidateIds']) == n for r in rows) for n in (0, 1, 2)}
    result['labelsSha256'] = sha(labels_path)
    result['qualityStatus'] = 'BLOCKED_COVERAGE_CHALLENGE' if challenge else 'INCONCLUSIVE_REVIEW' if uncertain or retention is None else (
        'PASS_ON_THIS_8_CASE_HOLDOUT_ONLY' if covered_hit == negative_reject == 4 and wrong == claim_errors == 0 and retention >= .9 else 'FAIL_UNDER_PRELOCKED_RULE')
    return result


def review_template(run):
    result = analyze(run)
    require(result['executionStatus'] == 'COMPLETE', 'REVIEW_REQUIRES_COMPLETE_RUN')
    questions = []
    for q in read(run / 'frozen-questions.json')['questions']:
        questions.append(dict(questionId=q['questionId'], coverage=None, coverageConfirmed=False,
                              candidates=[dict(vectorId=h['vectorId'], contentSha256=h['contentSha256'], label=None,
                                               primaryRequirementIds=[], versionCondition=None, gateAssessment=None)
                                          for h in read(run / q['questionId'] / 'MILVUS_SEARCH/hydrated.json')]))
    return dict(schema='P42_INDEPENDENT_HUMAN_REVIEW_V1', runArtifactsSha256=sha(run / 'artifacts.json'), questionSha256=QUESTIONS_SHA,
                reviewer=dict(type='HUMAN', name='', independent=False, labelsAuthoredWithoutModelPredictions=False), questions=questions,
                aiSuggestions=[])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['Verify', 'VerifyBaseline', 'Seal', 'Analyze'])
    parser.add_argument('--labels', type=pathlib.Path)
    args = parser.parse_args()
    if args.mode == 'VerifyBaseline':
        verify_prepared()
        print('P42_ORIGINAL_BASELINE=1963_MATCH_12_EXACT_DELTA; real calls=0')
        return
    if args.mode == 'Seal':
        seal_execution()
    elif args.mode == 'Verify':
        verify_execution()
        dependencies()
    else:
        result = analyze(labels_path=args.labels)
        key = sha(args.labels) if args.labels else 'execution-only'
        dest = ROOT / 'target/rag-p4-2/analysis' / key
        save_new(dest / 'analysis.json', result)
        if result['executionStatus'] == 'COMPLETE' and args.labels is None:
            save_new(dest / 'review-template.json', review_template(RUN))
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return
    print('P4_2_EXECUTION_PREP=READY; frozen SHA verified; new real calls=0')


if __name__ == '__main__':
    main()
