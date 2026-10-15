"""Seal the prepared R02 identity, byte-exact predecessor, offline tests and real read-only evidence."""
import difflib
import importlib.util
import pathlib
import json

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).resolve().parent
OUT = ROOT / 'target/rag-p4-2/execution-r02-20261014'
spec = importlib.util.spec_from_file_location('r02_analyze', HERE / 'execution/analyze.py')
a = importlib.util.module_from_spec(spec)
spec.loader.exec_module(a)
assert not a.SEAL.exists(), 'Already sealed; use Verify. Never overwrite an execution seal.'
a.verify_prepared()
a.verify_map(a.read(OUT / 'preservation-before.json'))
old_seal = ROOT / 'target/rag-p4-2/execution-prep-20261014/manifest.json'
old = a.read(old_seal)
assert a.sha(old_seal) == old_seal.with_name('manifest.sha256').read_text(encoding='ascii').strip()
a.verify_map(old['filesSha256'])
a.verify_class_inventory(old)
for path, expected in old['dependenciesSha256'].items():
    assert a.sha(pathlib.Path(path)) == expected
passing = ROOT / (OUT / 'passing-offline-path.txt').read_text(encoding='utf-8').strip()
assert passing.resolve().is_relative_to(OUT.resolve())
tests = a.read(passing / 'tests.json')
assert tests['tests'] == 99 and tests['failures'] == tests['errors'] == tests['skipped'] == tests['newRealCalls'] == 0
preflight = a.read(OUT / 'read-only-preflight-01/result.json')
assert preflight['status'] == preflight['javaResourcePreflight'] == 'PASS'
assert preflight['models'] == preflight['milvusSearch'] == preflight['mysqlWrites'] == preflight['milvusWrites'] == 0
assert preflight['collectionId'] == '469534467320352840' and preflight['dimension'] == 1024 and preflight['loaded'] is True
identity = a.read(OUT / 'EXECUTION_ID_RESERVED.lock')
assert identity['executionId'] == a.EXECUTION_ID and identity['authorization'] == a.AUTH
assert identity['status'] == 'PREPARED_IDENTITY_ONLY_NOT_EXECUTED_OR_AUTHORIZED' and not a.RUN.exists()
for old_path, new_path, name in [
    ('tools/rag-p4-2/execution/P42ExecuteOnce.java', 'tools/rag-p4-2-r02/execution/P42R02ExecuteOnce.java', 'runner-delta.patch'),
    ('tools/rag-p4-2/execution/analyze.py', 'tools/rag-p4-2-r02/execution/analyze.py', 'analyzer-delta.patch')]:
    diff = ''.join(difflib.unified_diff((ROOT / old_path).read_text(encoding='utf-8').splitlines(True),
                                      (ROOT / new_path).read_text(encoding='utf-8').splitlines(True), fromfile=old_path, tofile=new_path))
    with (OUT / name).open('x', encoding='utf-8', newline='\n') as file:
        file.write(diff)
# Scan the whole new prep so even synthetic .class fixtures are bound as evidence.
classes = old['classDirectories'] + [OUT.relative_to(ROOT).as_posix()]
files = {p for folder in (OUT, HERE) for p in folder.rglob('*') if p.is_file()}
files.add(ROOT / 'docs/RAG_P42_ENVIRONMENT_RECOVERY_R02_20261014.md')
for folder in classes:
    files.update((ROOT / folder).rglob('*.class'))
manifest = dict(schema='P42_R02_INDEPENDENT_EXECUTION_SEAL_V1', executionId=a.EXECUTION_ID, authorization=a.AUTH,
                status='READY_UNAUTHORIZED_UNEXECUTED', prepSha256=a.PREP_SHA, questionSha256=a.QUESTIONS_SHA, planSha256=a.PLAN_SHA,
                originalExecutionSealSha256=a.sha(old_seal), predecessorArtifactsSha256=a.sha(ROOT / 'target/rag-p4-2/run-once/artifacts.json'),
                predecessorLockSha256=a.sha(ROOT / 'target/rag-p4-2/run-once/EXECUTION_RESERVED.lock'), deltaSha256=a.DELTA_SHA,
                classDirectories=classes, runtimeClassDirectories=old['classDirectories'] + [OUT.relative_to(ROOT).as_posix() + '/classes'],
                filesSha256={p.relative_to(ROOT).as_posix(): a.sha(p) for p in sorted(files)},
                dependenciesSha256=old['dependenciesSha256'], maxPaidCalls=24, maxSearch=8, retries=0, budgetReferenceCny=1.0,
                costProxyCny=.707392, estimateIsInvoiceHardCap=False, newRealCalls=0, realSearch=0,
                offlineTests=tests, realReadOnlyPreflight=preflight,
                qualityStatus='NOT_EVALUATED', runDirectory=a.RUN.relative_to(ROOT).as_posix())
a.save_new(a.SEAL, manifest)
with a.SEAL.with_name('manifest.sha256').open('x', encoding='ascii') as file:
    file.write(a.sha(a.SEAL) + '\n')
a.verify_execution()
a.dependencies()
print('NEW_EXECUTION_PREP=READY; executionId=' + a.EXECUTION_ID + '; sealSha256=' + a.sha(a.SEAL) + '; new models=0; real Search=0')
