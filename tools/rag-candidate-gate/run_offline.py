"""Files-only Java experiment. No application startup, model calls, JDBC, Milvus or execution-lock writes."""
import hashlib
import importlib.util
import json
import os
import pathlib
import shutil
import subprocess
import sys
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).resolve().parent
OUT = ROOT / 'target/rag-candidate-gate' / ('offline-' + uuid.uuid4().hex)
JAVA = pathlib.Path('C:/Program Files/Java/jdk-21.0.11/bin')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def save(path, value):
    with path.open('x', encoding='utf-8', newline='\n') as file:
        json.dump(value, file, ensure_ascii=False, indent=2)
        file.write('\n')


def protected_snapshot():
    protected = {}
    for relative in ('tools/rag-p4-2', 'tools/rag-p4-2-r02', 'target/rag-p4-2', 'src', 'sql'):
        for path in (ROOT / relative).rglob('*'):
            if path.is_file():
                protected[path.relative_to(ROOT).as_posix()] = sha(path)
    for relative in ('AGENTS.md', 'pom.xml', 'target/rag-e2e/runtime-classpath.txt'):
        protected[relative] = sha(ROOT / relative)
    return protected


def child(name, arguments):
    # Credentials, provider switches, Java injection options and proxy settings are not inherited.
    environment = {k: v for k, v in os.environ.items() if k.upper() in
                   {'SYSTEMROOT', 'WINDIR', 'COMSPEC', 'TEMP', 'TMP', 'USERPROFILE', 'APPDATA', 'LOCALAPPDATA'}}
    result = subprocess.run(list(map(str, arguments)), cwd=ROOT, env=environment, capture_output=True)
    (OUT / (name + '.log')).write_bytes(result.stdout + result.stderr)
    print((result.stdout + result.stderr).decode('utf-8', errors='replace').strip(), flush=True)
    if result.returncode:
        raise RuntimeError(f'{name} failed: exit={result.returncode}; retained logs; no model calls')


def main():
    OUT.mkdir(parents=True)
    print('Offline output:', OUT, flush=True)
    before = protected_snapshot()
    save(OUT / 'preservation-before.json', before)
    development = ROOT / 'target/rag-candidate-gate/development-preservation.json'
    if development.exists() and read(development) != before:
        raise AssertionError('Protected development source/history/config drift')
    spec = importlib.util.spec_from_file_location('frozen_r02_verifier', ROOT / 'tools/rag-p4-2-r02/execution/analyze.py')
    verifier = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(verifier)
    analysis = verifier.analyze()  # Read-only seals, locks, receipts, artifact inventory; no live service.
    verifier.dependencies()
    assert analysis['integrity'] == 'SHA_VERIFIED'
    assert read(verifier.RUN / 'summary.json')['executionStatus'] == 'STOPPED'
    assert (verifier.RUN / 'EXECUTION_RESERVED.lock').is_file()

    classes = OUT / 'classes'
    classes.mkdir()
    dependencies = (ROOT / 'target/rag-e2e/runtime-classpath.txt').read_text(encoding='utf-8-sig').strip()
    cp = ';'.join((str(ROOT / 'target/classes'), dependencies))
    assert all(pathlib.Path(p).exists() for p in cp.split(';')), 'Only existing cached dependencies allowed'
    frozen_sources = sorted((ROOT / 'tools/rag-p4-2/java').rglob('*.java'))
    sources = frozen_sources + sorted((HERE / 'java').rglob('*.java')) + [
        HERE / 'CandidateLevelGateOfflineTest.java', HERE / 'SavedCandidateGateReplay.java',
        ROOT / 'tools/rag-p4-2/P42OfflineTest.java', ROOT / 'tools/rag-p4/SemanticEvidenceGate.java',
        ROOT / 'src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java',
        ROOT / 'src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekJsonCompletionResult.java']
    child('javac', [JAVA / 'javac.exe', '-J-Duser.language=en', '-proc:none', '-encoding', 'UTF-8', '-cp', cp, '-d', classes, *sources])
    runtime = str(classes) + ';' + cp
    child('candidate-tests', [JAVA / 'java.exe', '-Dfile.encoding=UTF-8', '-cp', runtime,
                              'CandidateLevelGateOfflineTest', OUT / 'candidate-tests.json'])
    child('historical-replay', [JAVA / 'java.exe', '-Dfile.encoding=UTF-8', '-cp', runtime,
                               'SavedCandidateGateReplay', verifier.RUN, OUT / 'comparison.json'])

    # The frozen suite writes only into a fresh copied fixture root, leaving all original evidence intact.
    fixture = OUT / 'frozen-suite-fixtures'
    paths = ['target/rag-p4-2/prepared/development-fixtures.json', 'tools/rag-p4-2/system-prompt.txt']
    for family, count in [('rag-p4', 5), ('rag-p4-1', 3)]:
        for i in range(1, count + 1):
            qid = f'g{i:02}' if family == 'rag-p4' else f't{i}'
            paths.extend([f'target/{family}/prepared/inputs/{qid}.json', f'target/{family}/run-once/{qid}/response.json'])
    for relative in paths:
        destination = fixture / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / relative, destination)
    (fixture / 'target/rag-p4-2/offline').mkdir(parents=True)
    agents = [p for p in cp.split(';') if pathlib.Path(p).name.startswith('mockito-core-') and p.endswith('.jar')]
    assert len(agents) == 1
    child('frozen-regression', [JAVA / 'java.exe', f'-javaagent:{agents[0]}', '-Dfile.encoding=UTF-8', '-cp', runtime, 'P42OfflineTest', fixture])
    shutil.copyfile(fixture / 'target/rag-p4-2/offline/java-tests.json', OUT / 'frozen-regression.json')
    after = protected_snapshot()
    save(OUT / 'preservation-after.json', after)
    assert before == after, 'Protected source/history/config was modified or a protected file was added/removed'
    assert verifier.analyze()['integrity'] == 'SHA_VERIFIED'
    suites = {name: read(OUT / filename) for name, filename in (
        ('candidateSafetyMocks', 'candidate-tests.json'), ('sameInputHistoricalReplay', 'comparison.json'),
        ('frozenOfflineRegression', 'frozen-regression.json'))}
    summary = {key: sum(s[key] for s in suites.values()) for key in ('tests', 'failures', 'errors', 'skipped')}
    summary.update(newModelCalls=0, realSearch=0, mysqlAccess=0, milvusAccess=0, productionIntegration='NOT_ENABLED',
                   preservation='BYTE_IDENTICAL', protectedFiles=len(before), historicalExecutionStatus='STOPPED',
                   historicalQualityReclassified=False, generalQualityConclusion='NOT_EVALUATED', suites=suites)
    save(OUT / 'tests.json', summary)
    assert summary['failures'] == summary['errors'] == summary['skipped'] == 0
    print(f"OFFLINE_TESTS={summary['tests']}; failures=0; errors=0; skipped=0; protected={len(before)}; model calls=0", flush=True)
    print('Report:', OUT / 'tests.json', flush=True)


if __name__ == '__main__':
    main()
