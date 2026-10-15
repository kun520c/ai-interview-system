"""Compile isolated tooling and run focused Mock/offline suites. Never run ExecuteOnce."""
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
PREP = ROOT / 'target/rag-p4-2/execution-r02-20261014'
OUT = PREP / ('offline-' + uuid.uuid4().hex)
JAVA = pathlib.Path('C:/Program Files/Java/jdk-21.0.11/bin')
assert not (PREP / 'manifest.json').exists(), 'Already sealed; use Verify. Do not overwrite frozen tests/classes.'
OUT.mkdir()
environment = {k: v for k, v in os.environ.items() if k.upper() in {'SYSTEMROOT', 'WINDIR', 'COMSPEC', 'TEMP', 'TMP', 'USERPROFILE', 'APPDATA', 'LOCALAPPDATA'}}
cp = ';'.join([str(PREP / 'classes'), str(ROOT / 'target/rag-p4-2/execution-prep-20261014/classes'),
               str(ROOT / 'target/rag-p4-2/offline/classes'), str(ROOT / 'target/rag-p3/offline/classes'), str(ROOT / 'target/classes'),
               (ROOT / 'target/rag-e2e/runtime-classpath.txt').read_text(encoding='utf-8-sig').strip()])


def run(name, args):
    result = subprocess.run(list(map(str, args)), cwd=ROOT, env=environment, capture_output=True)
    (OUT / (name + '.log')).write_bytes(result.stdout + result.stderr)
    print((result.stdout + result.stderr).decode('utf-8', errors='replace'))
    if result.returncode:
        raise SystemExit(f'{name} failed: exit={result.returncode}; retained evidence; no model calls')


run('original-verify', [sys.executable, '-I', '-B', '-X', 'utf8', ROOT / 'tools/rag-p4-2/execution/analyze.py', 'Verify'])
run('javac', [JAVA / 'javac.exe', '-J-Duser.language=en', '-proc:none', '-encoding', 'UTF-8', '-cp', cp, '-d', PREP / 'classes',
              *sorted((HERE / 'execution').glob('*.java'))])
run('runner-tests', [JAVA / 'java.exe', '-Dfile.encoding=UTF-8', '-cp', cp, 'P42R02RunnerTest', OUT])
(OUT / 'diagnostic-tests').mkdir(exist_ok=True)
run('diagnostic-tests', [JAVA / 'java.exe', '-Dfile.encoding=UTF-8', '-cp', cp, 'P42StopDiagnosticsTest', OUT / 'diagnostic-tests'])
run('identity-diagnostic-tests', [JAVA / 'java.exe', '-Dfile.encoding=UTF-8', '-cp', cp, 'P42R02IdentityDiagnosticsTest', OUT])

fixture = OUT / 'core-replay-root'
files = ['target/rag-p4-2/prepared/development-fixtures.json', 'tools/rag-p4-2/system-prompt.txt']
for family, count, prefix in [('rag-p4', 5, 'g'), ('rag-p4-1', 3, 't')]:
    for i in range(1, count + 1):
        qid = f'g{i:02}' if prefix == 'g' else f't{i}'
        files.extend([f'target/{family}/prepared/inputs/{qid}.json', f'target/{family}/run-once/{qid}/response.json'])
for path in files:
    dest = fixture / path
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ROOT / path, dest)
(fixture / 'target/rag-p4-2/offline').mkdir(parents=True, exist_ok=True)
agent = next(p for p in cp.split(';') if pathlib.Path(p).name.startswith('mockito-core-') and p.endswith('.jar'))
run('core-tests', [JAVA / 'java.exe', f'-javaagent:{agent}', '-Dfile.encoding=UTF-8', '-cp', cp, 'P42OfflineTest', fixture])
shutil.copyfile(fixture / 'target/rag-p4-2/offline/java-tests.json', OUT / 'core-mock-tests.json')
run('python-tests', [sys.executable, '-I', '-B', '-X', 'utf8', HERE / 'execution/test_execution.py', OUT])
run('r02-python-tests', [sys.executable, '-I', '-B', '-X', 'utf8', HERE / 'execution/test_r02.py', OUT])
suites = [json.loads((OUT / name).read_text(encoding='utf-8')) for name in
          ('java-tests.json', 'python-tests.json', 'core-mock-tests.json', 'identity-diagnostic-tests.json', 'r02-python-tests.json')]
# The retained diagnostic suite has six explicit assertions/tests and a successful process exit.
assert 'tests=6, failures=0, errors=0, skipped=0' in (OUT / 'diagnostic-tests.log').read_text(encoding='utf-8')
suites.append(dict(tests=6, failures=0, errors=0, skipped=0, newRealCalls=0))
summary = {key: sum(s[key] for s in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
summary.update(newRealCalls=0, suites=suites)
assert summary['failures'] == summary['errors'] == summary['skipped'] == 0
(OUT / 'tests.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
with (PREP / 'passing-offline-path.txt').open('x', encoding='utf-8') as file:
    file.write(OUT.relative_to(ROOT).as_posix() + '\n')
print('R02 offline tests:', summary['tests'], '; failures=0; errors=0; skipped=0; new model calls=0')
