#!/usr/bin/env python3
"""Fixed-host D1 runner. Container credentials stay in child environment, never stdout or files."""
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
os.chdir(ROOT)
os.umask(0o077)
mode = sys.argv[1]
assert mode in ('baseline', 'validate', 'rollback', 'import', 'verify')
base = ROOT / 'backups/D1_20261114'
env = {k: v for k, v in os.environ.items() if k in ('PATH', 'HOME', 'JAVA_HOME', 'LANG', 'TMPDIR')}
if mode != 'validate':
    container = json.loads(subprocess.check_output(['docker', 'inspect', 'ai-interview-mac-mysql-1']))[0]
    assert container['Name'] == '/ai-interview-mac-mysql-1'
    assert container['State']['Running'] and container['State']['Health']['Status'] == 'healthy'
    assert container['NetworkSettings']['Ports']['3306/tcp'] == [{'HostIp': '127.0.0.1', 'HostPort': '3308'}]
    values = dict(x.split('=', 1) for x in container['Config']['Env'])
    env.update(EVALUATION_DB_USERNAME='root', EVALUATION_DB_PASSWORD=values['MYSQL_ROOT_PASSWORD'])
cp = str(base) + ':' + (base / 'classpath.txt').read_text().strip()
with (base / ('question-' + mode + '.log')).open('x') as f:
    result = subprocess.run(['java', '-cp', cp, 'D1QuestionBankImport', mode], env=env,
                            stdout=f, stderr=subprocess.STDOUT, timeout=100)
print('mode=' + mode + ' exit=' + str(result.returncode))
print((base / ('question-' + mode + '.log')).read_text())
sys.exit(result.returncode)
