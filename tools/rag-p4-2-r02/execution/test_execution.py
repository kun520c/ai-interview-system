"""Offline control, integrity and analyzer tests over Mock artifacts only."""
import copy
import importlib.util
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[3]
PREP = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'target/rag-p4-2/execution-r02-20261014'
spec = importlib.util.spec_from_file_location('p42_analyze', pathlib.Path(__file__).with_name('analyze.py'))
a = importlib.util.module_from_spec(spec)
spec.loader.exec_module(a)
passed = []
failures = []
errors = []


def test(name, fn):
    try:
        fn()
        passed.append(name)
    except AssertionError:
        failures.append(dict(name=name, code='ASSERTION_FAILED'))
    except Exception as error:
        errors.append(dict(name=name, code=str(error)))


def check(value):
    assert value


def rejects(fn, code=None):
    try:
        fn()
    except (ValueError, FileNotFoundError) as error:
        if code:
            assert code in str(error), str(error)
    else:
        raise AssertionError('Expected rejection')


def overwrite(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')


def reseal(run):
    files = {p.relative_to(run).as_posix(): a.sha(p) for p in run.rglob('*') if p.is_file() and p.name not in ('artifacts.json', 'artifacts.sha256')}
    overwrite(run / 'artifacts.json', files)
    (run / 'artifacts.sha256').write_text(a.sha(run / 'artifacts.json'), encoding='ascii')


original = pathlib.Path((PREP / 'mock-complete-path.txt').read_text(encoding='utf-8'))
temp = pathlib.Path(tempfile.mkdtemp(prefix='analyze-mock-', dir=PREP))
run = temp / 'complete'
shutil.copytree(original, run)
test('complete Mock ledger reports 24/8 and no quality PASS without review', lambda: check(
    a.analyze(run, verify=False)['paidAttempts'] == 24 and a.analyze(run, verify=False)['calls']['MILVUS_SEARCH'] == 8 and
    a.analyze(run, verify=False)['qualityStatus'] == 'PENDING_INDEPENDENT_HUMAN_REVIEW' and 'metrics' not in a.analyze(run, verify=False)))
test('known usage and stage median/p95 use saved receipts', lambda: check(
    a.analyze(run, verify=False)['latency']['SEMANTIC_GATE']['count'] == 8 and a.analyze(run, verify=False)['costCompleteForAttempts']))
test('existing frozen question, plan, corpus and P0-P4.1 SHA gate unchanged', a.verify_prepared)
integrity_root = temp / 'integrity-fixture'
integrity_root.mkdir()
(integrity_root / 'fixed.java').write_text('frozen', encoding='utf-8')
(integrity_root / 'allowed.java').write_text('accepted', encoding='utf-8')
(integrity_root / 'allowed.class').write_bytes(b'accepted class')
mapping = {'fixed.java': a.sha(integrity_root / 'fixed.java'), 'allowed.java': 'old source', 'allowed.class': 'old class'}
entries = [dict(path=p, oldSha256=mapping[p], currentSha256=a.sha(integrity_root / p)) for p in ('allowed.java', 'allowed.class')]
test('exact delta accepts only the proven new bytes with old SHA binding', lambda: a.verify_baseline_map(mapping, entries, integrity_root))


def changed_file_rejected(name):
    p = integrity_root / name
    old = p.read_bytes()
    try:
        p.write_bytes(b'unexpected later mutation')
        rejects(lambda: a.verify_baseline_map(mapping, entries, integrity_root), 'SHA_DRIFT')
    finally:
        p.write_bytes(old)


for name in ('fixed.java', 'allowed.java', 'allowed.class'):
    test('reject later mutation: ' + name, lambda n=name: changed_file_rejected(n))
test('delta wrong old SHA cannot replace the original baseline', lambda: rejects(
    lambda: a.verify_baseline_map(mapping, [dict(entries[0], oldSha256='other')], integrity_root), 'DELTA_OLD_SHA_BINDING'))
test('delta duplicate or unknown paths cannot expand allowance', lambda: (
    rejects(lambda: a.verify_baseline_map(mapping, entries + entries[:1], integrity_root), 'DELTA_PATH_BINDING'),
    rejects(lambda: a.verify_baseline_map(mapping, [dict(entries[0], path='unknown.java')], integrity_root), 'DELTA_PATH_BINDING')))
test('added class cannot shadow a sealed runtime dependency', lambda: rejects(
    lambda: a.verify_class_inventory(dict(classDirectories=['.'], filesSha256={}), integrity_root), 'UNSEALED_CLASS'))
test('controlled current and frozen builds reproduce all four historical binaries exactly', lambda: check(
    all(v['exact'] for c in a.read(a.DELTA.parent / 'controlled-build.json')['commands'] for v in c['comparisons'].values())))
test('exact delta and original freeze hashes remain pinned in both verifiers', lambda: check(
    a.sha(a.DELTA) == a.DELTA_SHA and a.DELTA_SHA in (ROOT / 'tools/rag-p4-2/execution/P42ExecuteOnce.java').read_text()))


def delta_seal_drift():
    previous = a.DELTA
    tampered_delta = temp / 'altered-delta.json'
    tampered_delta.write_bytes(previous.read_bytes() + b' ')
    try:
        a.DELTA = tampered_delta
        rejects(a.verify_prepared, 'EXACT_DELTA_SEAL_DRIFT')
    finally:
        a.DELTA = previous


def dependency_drift():
    previous = a.SEAL
    jar = temp / 'dependency.jar'
    jar.write_bytes(b'cached dependency')
    manifest = temp / 'dependency-manifest.json'
    overwrite(manifest, dict(dependenciesSha256={str(jar): a.sha(jar)}))
    try:
        a.SEAL = manifest
        a.dependencies()
        jar.write_bytes(b'altered dependency')
        rejects(a.dependencies, 'DEPENDENCY_SHA_DRIFT')
    finally:
        a.SEAL = previous


test('altered delta bytes fail before any historical override', delta_seal_drift)
test('cached dependency Jar byte mutation is rejected', dependency_drift)
test('execution-only analysis does not consult provisional expected coverage', lambda: check('expectedCoverage' not in str(a.analyze(run, verify=False))))

tamper = temp / 'tampered'
shutil.copytree(run, tamper)
(tamper / 'H42-01/EMBEDDING/request.json').write_text('{}', encoding='utf-8')
test('altered saved request cannot be analyzed as PASS', lambda: rejects(lambda: a.analyze(tamper, verify=False), 'SHA_DRIFT'))
interrupted = temp / 'interrupted'
shutil.copytree(run, interrupted)
for name in ('summary.json', 'artifacts.json', 'artifacts.sha256', 'H42-01/EMBEDDING/receipt.json'):
    (interrupted / name).unlink()
test('interrupted reserved request counts as attempted and unknown cost', lambda: check(
    a.analyze(interrupted, verify=False)['executionStatus'] == 'STOPPED_OR_INCOMPLETE' and
    a.analyze(interrupted, verify=False)['paidAttempts'] == 24 and not a.analyze(interrupted, verify=False)['costCompleteForAttempts']))
test('missing run lock is blocked', lambda: rejects(lambda: a.analyze(temp / 'absent', verify=False), 'NO_EXECUTION_LOCK'))
test('SHA inventory catches added artifact', lambda: (tamper.joinpath('extra.json').write_text('{}'), rejects(lambda: a.analyze(tamper, verify=False))))

# Synthetic metrics fixture only: no claim of semantic correctness, no tuning of the holdout gate.
audit = {r['questionId']: r['expectedCoverage'] for r in a.read(a.PREP / 'coverage-audit-v1.json')['reviews']}
questions = a.read(run / 'frozen-questions.json')['questions']
labels = dict(schema='P42_INDEPENDENT_HUMAN_REVIEW_V1', questionSha256=a.QUESTIONS_SHA,
              reviewer=dict(type='HUMAN', name='SYNTHETIC_TEST_REVIEW_NOT_A_REAL_HUMAN', independent=True, labelsAuthoredWithoutModelPredictions=True), questions=[])
summary = a.read(run / 'summary.json')
for q in questions:
    qid = q['questionId']
    row = a.read(run / qid / 'result.json')
    is_covered = audit[qid] == 'COVERED'
    primary = [r['id'] for r in q['requirements'] if r['role'] == 'PRIMARY_MECHANISM']
    ids = [r['vectorId'] for r in row['ranking'][:2]]
    row['finalAdmittedCandidateIds'] = ids if is_covered else []
    row['gate']['admittedCandidateIds'] = row['finalAdmittedCandidateIds']
    row['gate']['status'] = 'ADMITTED' if is_covered else 'EMPTY_NO_PRIMARY_SUPPORT'
    for prediction in row['gate']['candidates']:
        prediction['verdict'] = 'CORE_SUPPORT' if is_covered else 'NO_SUPPORT'
        prediction['supports'] = [dict(requirementId=primary[0], relation='DIRECT_MECHANISM')] if is_covered else []
    overwrite(run / qid / 'result.json', row)
    summary['questions'][int(qid[-2:]) - 1] = row
    raw = a.read(run / qid / 'MILVUS_SEARCH/hydrated.json')
    candidates = [dict(vectorId=h['vectorId'], contentSha256=h['contentSha256'], label='CORE_SUPPORT' if is_covered and h['vectorId'] in ids else 'NO_SUPPORT',
                       primaryRequirementIds=primary[:1] if is_covered and h['vectorId'] in ids else [], versionCondition='MATCH', gateAssessment='CORRECT') for h in raw]
    labels['questions'].append(dict(questionId=qid, coverage=audit[qid], coverageConfirmed=True, candidates=candidates))
overwrite(run / 'summary.json', summary)
reseal(run)
labels['runArtifactsSha256'] = a.sha(run / 'artifacts.json')
labels_path = temp / 'synthetic-review.json'
overwrite(labels_path, labels)
test('locked quality metrics compute 4/4 4/4 zero wrong admission and 100% retention', lambda: check(
    a.analyze(run, labels_path, verify=False)['qualityStatus'] == 'PASS_ON_THIS_8_CASE_HOLDOUT_ONLY' and
    a.analyze(run, labels_path, verify=False)['metrics']['usefulRetentionRelativeToTop2'] == 1.0))


def changed_labels(mutator):
    data = copy.deepcopy(labels)
    mutator(data)
    p = temp / ('review-' + str(len(passed)) + '.json')
    overwrite(p, data)
    return p


test('AI suggested labels cannot unlock quality PASS', lambda: rejects(lambda: a.analyze(run, changed_labels(lambda d: d['reviewer'].update(type='AI')), verify=False), 'INDEPENDENT_BLIND'))
test('nonblind labels cannot unlock quality PASS', lambda: rejects(lambda: a.analyze(run, changed_labels(lambda d: d['reviewer'].update(labelsAuthoredWithoutModelPredictions=False)), verify=False), 'INDEPENDENT_BLIND'))
test('wrong run receipt binding cannot unlock quality PASS', lambda: rejects(lambda: a.analyze(run, changed_labels(lambda d: d.update(runArtifactsSha256='other')), verify=False), 'REVIEW_RUN_BINDING'))
test('missing candidate labels blocked', lambda: rejects(lambda: a.analyze(run, changed_labels(lambda d: d['questions'][0]['candidates'].pop()), verify=False), 'ALL_TOP5'))
test('coverage challenge blocks formal PASS without rewriting groups', lambda: check(
    a.analyze(run, changed_labels(lambda d: d['questions'][0].update(coverage='HARD_NEGATIVE')), verify=False)['qualityStatus'] == 'BLOCKED_COVERAGE_CHALLENGE'))


def remove_useful(d):
    for q in d['questions']:
        for c in q['candidates']:
            c.update(label='NO_SUPPORT', primaryRequirementIds=[])


test('zero denominator is inconclusive and never divides by zero', lambda: check(
    a.analyze(run, changed_labels(remove_useful), verify=False)['qualityStatus'] == 'INCONCLUSIVE_REVIEW'))
test('wrong primary evidence admission is FAIL under frozen rule', lambda: check(
    a.analyze(run, changed_labels(lambda d: d['questions'][0]['candidates'][4].update(label='BACKGROUND_ONLY', primaryRequirementIds=[])), verify=False)['qualityStatus'] == 'FAIL_UNDER_PRELOCKED_RULE'))
test('independent quote entailment error is FAIL and reported per question', lambda: check(
    a.analyze(run, changed_labels(lambda d: d['questions'][0]['candidates'][4].update(gateAssessment='INCORRECT')), verify=False)['metrics']['independentQuoteConditionErrors'] == 1))
test('uncertain review cannot produce quality PASS', lambda: check(
    a.analyze(run, changed_labels(lambda d: d['questions'][0]['candidates'][0].update(label='UNCERTAIN')), verify=False)['qualityStatus'] == 'INCONCLUSIVE_REVIEW'))
test('tampered label body SHA is blocked', lambda: rejects(lambda: a.analyze(run, changed_labels(lambda d: d['questions'][0]['candidates'][0].update(contentSha256='other')), verify=False), 'LABEL_CONTENT'))
test('unconfirmed coverage is blocked', lambda: rejects(lambda: a.analyze(run, changed_labels(lambda d: d['questions'][0].update(coverageConfirmed=False)), verify=False), 'COVERAGE_CONFIRMATION'))
test('quality review cannot upgrade interrupted execution', lambda: rejects(lambda: a.analyze(interrupted, labels_path, verify=False), 'QUALITY_REQUIRES_COMPLETE'))

test('PowerShell entry parses without executing real mode', lambda: check(subprocess.run([
    'C:/Program Files/PowerShell/7/pwsh.exe', '-NoProfile', '-Command',
    "$taskTokens=$null; $taskErrors=$null; [void][System.Management.Automation.Language.Parser]::ParseFile('" + (ROOT / 'tools/rag-p4-2-r02/execute.ps1').as_posix() + "',[ref]$taskTokens,[ref]$taskErrors); if($taskErrors.Count){exit 1}"
], capture_output=True).returncode == 0))
test('execution launcher retains interactive consent, isolated child env and disabled retries', lambda: check(
    all(term in (ROOT / 'tools/rag-p4-2-r02/execute.ps1').read_text(encoding='utf-8') for term in (
        '[Console]::IsInputRedirected', '$taskStart.Environment.Clear()', 'AUTHORIZE P4.2 P42-R02-20261014 8 EACH ONCE MAX24 CNY1',
        '-Djdk.httpclient.disableRetryConnect=true', '-Djdk.httpclient.enableAllMethodRetry=false'))))

java = a.read(PREP / 'java-tests.json')
total = len(passed) + len(failures) + len(errors)
overwrite(PREP / 'python-tests.json', dict(tests=total, failures=len(failures), errors=len(errors), skipped=0, newRealCalls=0, checks=passed, failureDetails=failures, errorDetails=errors))
core = a.read(PREP / 'core-mock-tests.json')
overwrite(PREP / 'tests.json', dict(tests=java['tests'] + total + core['tests'], failures=len(failures) + core['failures'], errors=len(errors) + core['errors'], skipped=core['skipped'], newRealCalls=0,
                                  javaMockTests=java['tests'], pythonOfflineTests=total, coreMockTests=core['tests']))
print(f'P42 Analyze/entry tests={total}, failures={len(failures)}, errors={len(errors)}, skipped=0; real calls=0')
for item in failures + errors:
    print(json.dumps(item, ensure_ascii=False))
sys.exit(1 if failures or errors else 0)
