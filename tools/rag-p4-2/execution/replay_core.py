"""Replay the existing frozen 25-case Mock suite into a new output tree."""
import json
import os
import pathlib
import shutil
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[3]
OUT = ROOT / 'target/rag-p4-2/execution-prep-20261014'
COPY = OUT / 'core-replay-root'
assert not COPY.exists(), 'Core replay evidence already exists; preserve it'
files = ['target/rag-p4-2/prepared/development-fixtures.json', 'tools/rag-p4-2/system-prompt.txt']
for family, ids in [('rag-p4', ['g%02d' % i for i in range(1, 6)]), ('rag-p4-1', ['t%d' % i for i in range(1, 4)])]:
    for qid in ids:
        files += [f'target/{family}/prepared/inputs/{qid}.json', f'target/{family}/run-once/{qid}/response.json']
for name in files:
    dest = COPY / name
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ROOT / name, dest)
(COPY / 'target/rag-p4-2/offline').mkdir(parents=True)
cp = ';'.join([str(ROOT / 'target/rag-p4-2/offline/classes'), str(ROOT / 'target/classes'),
               (ROOT / 'target/rag-e2e/runtime-classpath.txt').read_text(encoding='utf-8-sig').strip()])
agent, = [p for p in cp.split(';') if pathlib.Path(p).name.startswith('mockito-core-')]
env = {k: v for k, v in os.environ.items() if k in ('SystemRoot', 'WINDIR', 'COMSPEC', 'TEMP', 'TMP', 'USERPROFILE', 'APPDATA', 'LOCALAPPDATA')}
result = subprocess.run(['C:/Program Files/Java/jdk-21.0.11/bin/java.exe', '-javaagent:' + agent, '-Dfile.encoding=UTF-8',
                         '-cp', cp, 'P42OfflineTest', str(COPY)], cwd=ROOT, env=env, capture_output=True)
with (OUT / 'core-mock.log').open('xb') as file:
    file.write(result.stdout + result.stderr)
assert result.returncode == 0, result.stderr.decode(errors='replace')
summary = json.loads((COPY / 'target/rag-p4-2/offline/java-tests.json').read_text())
assert summary['tests'] == 25 and summary['failures'] == summary['errors'] == summary['skipped'] == summary['newRealModelCalls'] == 0
with (OUT / 'core-mock-tests.json').open('xb') as file:
    file.write((json.dumps(summary, ensure_ascii=False, indent=2) + '\n').encode())
print('P42 existing core Mock tests=25, failures=0, errors=0, skipped=0; real calls=0')
