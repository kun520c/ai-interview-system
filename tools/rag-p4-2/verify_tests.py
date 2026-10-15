"""Local structural/metric checks. Semantic fixtures are development data, never holdout tuning."""
import importlib.util
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('p42prepare', ROOT / 'tools/rag-p4-2/prepare.py')
p42 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p42)
passed = []


def test(name, fn):
    fn()
    passed.append(name)


def check(value):
    assert value


questions = p42.read('target/rag-p4-2/prepared/holdout-questions-v1.json')['questions']
audit = p42.read('target/rag-p4-2/prepared/coverage-audit-v1.json')
plan = p42.read('target/rag-p4-2/prepared/validation-plan-v1.json')
test('holdout 4 covered plus 4 mechanism hard negatives; pre-retrieval AI coverage marked provisional', lambda: check(
    len(questions) == 8 and sum(r['expectedCoverage'] == 'COVERED' for r in audit['reviews']) == 4 and all(not r['humanConfirmed'] for r in audit['reviews'])))
test('authoritative roles have question anchors; online contracts contain no labels or expected answer', lambda: check(
    all(set(q) == {'questionId', 'question', 'versionConstraint', 'requirements'} and
        any(r['role'] == p42.P for r in q['requirements']) and all(r['questionQuote'] in q['question'] for r in q['requirements']) for q in questions)))
test('new holdout cases never used as semantic Mock fixtures', lambda: check(
    all(q['questionId'] not in (ROOT / 'tools/rag-p4-2/P42OfflineTest.java').read_text(encoding='utf-8') for q in questions)))
test('corpus remains all 21 original source hashes', lambda: p42.verify_map(audit['corpusSha256']))
test('all protected history, locks, production/test sources unchanged since preparation', lambda: p42.verify_map(p42.read('target/rag-p4-2/prepared/protected-history.json')))
test('model attempts bounded to 8 each; no retries/writes/evaluation calls planned', lambda: check(
    plan['callsUpperBound'] == {'Embedding': 8, 'MilvusSearch': 8, 'Rerank': 8, 'DeepSeekSemanticGate': 8, 'DeepSeekAnswerEvaluation': 0, 'Jev': 0, 'MySqlOrVectorWrites': 0}
    and 'retries=0' in plan['qualityCriteriaLocked']['execution'] and plan['budget']['combinedPeakNoCacheProxyCny'] < plan['budget']['hardRunBudgetCny']))
test('no paid execution entry or production enablement', lambda: check(
    "ValidateSet('Offline','Verify')" in (ROOT / 'tools/rag-p4-2/run.ps1').read_text(encoding='utf-8') and
    'enabled: false' in (ROOT / 'tools/rag-p4-2/experimental-defaults.yaml').read_text(encoding='utf-8')))

data = p42.read('target/rag-p4/prepared/dataset-with-provenance.json')['queries']
metrics = {}
for strategy in ('RERANK_TOP2', 'RERANK_TOP2_SCORE_GE_0.50'):
    selected = {q['queryId']: [c for c in q['candidates'][:2] if strategy == 'RERANK_TOP2' or c['relevanceScore'] >= .50] for q in data}
    flattened = [c for cs in selected.values() for c in cs]
    metrics[strategy] = dict(selected=len(flattened), useful=sum(c['label'] >= 1 for c in flattened), direct=sum(c['label'] == 2 for c in flattened),
                            irrelevant=sum(c['label'] == 0 for c in flattened),
                            coveredUsefulRetained=sum(any(c['label'] >= 1 for c in selected[q['queryId']]) for q in data if q['covered']),
                            outsideCorrectEmpty=sum(not selected[q['queryId']] for q in data if not q['covered']))
test('P3 corrected provisional metrics reproduced without changing results', lambda: check(
    metrics['RERANK_TOP2']['selected'] == 24 and metrics['RERANK_TOP2']['useful'] == 17 and
    metrics['RERANK_TOP2_SCORE_GE_0.50']['selected'] == 15 and metrics['RERANK_TOP2_SCORE_GE_0.50']['useful'] == 15 and
    metrics['RERANK_TOP2_SCORE_GE_0.50']['coveredUsefulRetained'] == 6 and metrics['RERANK_TOP2_SCORE_GE_0.50']['outsideCorrectEmpty'] == 4))
replay = p42.read('target/rag-p4-2/offline/legacy-replay.json')['queries']
test('saved historical gate replay has 5+3 rows and no validator failure', lambda: check(
    len(replay) == 8 and all(r['gate']['status'] != 'SAFE_EMPTY' for r in replay)))
repaired = p42.read('target/rag-p4-repair/support-point-v1/final-report-v2/repaired-revalidation.json')['queries']
test('legacy replay exactly matches repaired P4 baseline and stored P4.1 decisions', lambda: check(
    all(r['gate'] == repaired[i]['gate'] for i, r in enumerate(replay[:5])) and
    all(r['gate'] == p42.read(f"target/rag-p4-1/run-once/{r['questionId']}/decision.json")['gate'] for r in replay[5:])))

labels = p42.read('target/rag-p3/p3-labels-corrected-user-authorized.json')
# Preserve provenance verbatim in the new analysis, including the distinction between historical usefulness and core support.
analysis = dict(scope='P3_P4_P41_DEVELOPMENT_DATA_ONLY_NO_NEW_REAL_P42_RESPONSES',
                labelSource='P3_USER_AUTHORIZED_CORRECTION_MIXED_PROVENANCE_PROVISIONAL',
                correctedLabelsSha256=p42.sha(ROOT / 'target/rag-p3/p3-labels-corrected-user-authorized.json'),
                sourceDescriptions=sorted({c['labelSource'] for q in data for c in q['candidates']}),
                historicalUsefulDefinition='label>=1; may be background and is not PRIMARY_MECHANISM support', p3Baselines=metrics,
                newGateQuality='NOT_MEASURED; Mock assertions and legacy replay are not revised-prompt performance',
                legacyReplay=[dict(family=r['family'], questionId=r['questionId'], status=r['gate']['status'], admitted=r['gate'].get('admittedCandidateIds', [])) for r in replay])
p42.save('development-analysis.json', analysis)
(ROOT / 'target/rag-p4-2/offline/python-tests.json').write_text(json.dumps(dict(tests=len(passed), failures=0, errors=0, skipped=0, checks=passed), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'P4.2 local structural/metric checks={len(passed)}, failures=0, errors=0, skipped=0; no model calls.')
