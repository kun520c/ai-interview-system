"""Additional stopped-run diagnostics and preservation checks; offline only."""
import importlib.util
import pathlib
import json
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
PREP = ROOT / 'target/rag-p4-2/execution-r02-20261014'
OUT = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else PREP
spec = importlib.util.spec_from_file_location('r02_analyze', pathlib.Path(__file__).with_name('analyze.py'))
a = importlib.util.module_from_spec(spec)
spec.loader.exec_module(a)
checks = []


def test(name, value):
    assert value, name
    checks.append(name)


run = OUT / 'diagnostic-tests/mock-preflight'
result = a.analyze(run, verify=False)
test('preflight failure is explicitly NOT_EVALUATED and all cases NOT_STARTED',
     result['qualityStatus'] == 'NOT_EVALUATED_NO_CASES_EXECUTED' and all(r['executionStatus'] == 'NOT_STARTED' for r in result['perQuestion']))
test('gRPC status and zero counters remain visible in offline analysis',
     result['batchFailure']['failureDiagnostics']['grpcStatusCode'] == 'DEADLINE_EXCEEDED'
     and all(v == 0 for v in result['calls'].values()) and result['paidAttempts'] == 0)
try:
    a.analyze(run, ROOT / 'target/rag-p4-2/prepared/coverage-audit-v1.json', verify=False)
except ValueError as error:
    test('quality labels cannot upgrade unexecuted cases', str(error) == 'QUALITY_REQUIRES_COMPLETE_EXECUTION')
else:
    raise AssertionError('Expected rejection')
a.verify_map(a.read(PREP / 'preservation-before.json'))
test('all protected old execution evidence and diagnostic copies remain byte-identical', True)
old_summary = a.read(ROOT / 'target/rag-p4-2/run-once/summary.json')
test('original consumed execution is still stopped with zero calls; R02 is unconsumed',
     old_summary['executionStatus'] == 'STOPPED' and all(v == 0 for v in old_summary['calls'].values())
     and not a.RUN.exists() and a.AUTH != 'AUTHORIZE P4.2 8 EACH ONCE MAX24 CNY1')
a.save_new(OUT / 'r02-python-tests.json', dict(tests=len(checks), failures=0, errors=0, skipped=0, newRealCalls=0, checks=checks))
print(f'R02 additional Python tests={len(checks)}, failures=0, errors=0, skipped=0; real calls=0')
