#!/usr/bin/env python3
"""Fixed-host D2 backup/read-only checks and rollback-only acceptance launcher."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'backups/D2_20261114'
CONTAINER = 'ai-interview-mac-mysql-1'
SCHEMAS = ('ai_interview', 'ai_interview_test', 'ai_interview_evaluation')
UUID = '85dd1e00-cf98-11f1-8677-9eb9380f25e7'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def container_environment():
    c = json.loads(subprocess.check_output(['docker', 'inspect', CONTAINER]))[0]
    if (c['Name'] != '/' + CONTAINER or not c['State']['Running']
            or c['State']['Health']['Status'] != 'healthy'
            or c['NetworkSettings']['Ports']['3306/tcp'] != [
                {'HostIp': '127.0.0.1', 'HostPort': '3308'}]):
        raise RuntimeError('MySQL target mismatch')
    values = dict(item.split('=', 1) for item in c['Config']['Env'])
    env = {k: v for k, v in os.environ.items()
           if k in ('PATH', 'HOME', 'JAVA_HOME', 'LANG', 'TMPDIR')}
    env.update(EVALUATION_DB_USERNAME='root',
               EVALUATION_DB_PASSWORD=values['MYSQL_ROOT_PASSWORD'])
    return env


def mysql(sql):
    # The existing container environment supplies the secret, never command arguments/output.
    return subprocess.check_output([
        'docker', 'exec', '-i', CONTAINER, 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw'],
        input=sql.encode(), timeout=30)


def dump(schema, data_only=False):
    args = ('--no-create-info --skip-triggers --compact' if data_only
            else '--routines --events --triggers')
    return subprocess.check_output([
        'docker', 'exec', CONTAINER, 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot '
        '--single-transaction --order-by-primary --skip-dump-date '
        '--hex-blob --set-gtid-purged=OFF ' + args + ' --databases ' + schema],
        timeout=60)


def check_identity():
    identity = mysql('SELECT @@server_uuid, @@version;').decode()
    if identity.splitlines()[1:] != [UUID + '\t8.0.45']:
        raise RuntimeError('MySQL identity mismatch')
    return identity


def backup():
    identity = check_identity()
    audit = mysql('''USE ai_interview_evaluation;
SELECT status, COUNT(*) AS questions FROM question GROUP BY status;
SELECT difficulty, category, COUNT(*) AS candidates FROM question
 WHERE status='ENABLED' GROUP BY difficulty,category ORDER BY difficulty,category;
SELECT status,COUNT(*) AS points FROM question_scoring_point GROUP BY status;
SELECT q.id FROM question q LEFT JOIN question_scoring_point p
 ON p.question_id=q.id AND p.status='ENABLED' GROUP BY q.id
 HAVING COUNT(p.id)=0 OR SUM(p.weight)<>100 OR MIN(p.weight)<=0;
''')
    # A stricter per-field D1 comparison is performed by the Java baseline before any write.
    with (BASE / 'preflight.txt').open('xb') as f:
        f.write(identity.encode() + audit)
    evidence = {}
    for schema in SCHEMAS:
        full, rows = dump(schema), dump(schema, True)
        with (BASE / ('before-' + schema + '.sql')).open('xb') as f:
            f.write(full)
        with (BASE / ('before-' + schema + '-rows.sql')).open('xb') as f:
            f.write(rows)
        evidence[schema] = {'backupSha256': digest(full), 'rowsSha256': digest(rows),
                            'backupBytes': len(full), 'rowsBytes': len(rows)}
    with (BASE / 'database-backups.json').open('x') as f:
        json.dump(evidence, f, indent=2)
    receipt = {'target': '127.0.0.1:3308/ai_interview_evaluation', 'serverUuid': UUID,
               'backupFile': 'before-ai_interview_evaluation.sql',
               'backupSha256': evidence['ai_interview_evaluation']['backupSha256']}
    with (BASE / 'backup-receipt.json').open('x') as f:
        json.dump(receipt, f, indent=2)
    print(identity.strip())
    print(audit.decode())
    print(json.dumps(evidence, indent=2))


def verify():
    before = json.loads((BASE / 'database-backups.json').read_text())
    result = {}
    for schema in SCHEMAS:
        rows = dump(schema, True)
        with (BASE / ('after-' + schema + '-rows.sql')).open('xb') as f:
            f.write(rows)
        equal = digest(rows) == before[schema]['rowsSha256']
        result[schema] = {'rowsSha256': digest(rows), 'unchanged': equal}
        if schema != 'ai_interview_evaluation':
            full = dump(schema)
            result[schema]['schemaAndDataUnchanged'] = (
                digest(full) == before[schema]['backupSha256'])
    with (BASE / 'protection-result.json').open('x') as f:
        json.dump(result, f, indent=2)
    print(json.dumps(result, indent=2))
    if not all(r['unchanged'] and r.get('schemaAndDataUnchanged', True)
               for r in result.values()):
        raise RuntimeError('Protected database state changed; stop and preserve evidence')


def main():
    os.chdir(ROOT)
    os.umask(0o077)
    BASE.mkdir(mode=0o700, exist_ok=True)
    mode = sys.argv[1] if len(sys.argv) == 2 else ''
    if mode not in ('backup', 'baseline', 'exercise', 'verify'):
        raise ValueError('Expected backup, baseline, exercise, or verify')
    env = container_environment()
    check_identity()
    if mode == 'backup':
        backup()
    elif mode == 'verify':
        verify()
    else:
        cp = str(BASE) + ':' + (BASE / 'classpath.txt').read_text().strip()
        with (BASE / (mode + '.log')).open('x') as log:
            result = subprocess.run([
                'java', '-cp', cp, 'D2QuestionPlanAcceptance', mode, str(BASE)],
                env=env, stdout=log, stderr=subprocess.STDOUT, timeout=240)
        print('mode=' + mode + ' exit=' + str(result.returncode))
        print('log=' + str(BASE / (mode + '.log')))
        sys.exit(result.returncode)


if __name__ == '__main__':
    main()
