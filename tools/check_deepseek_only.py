"""Compile active E2E harnesses and run portable, synthetic J1 contracts. No paid calls."""
import ast
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'target' / ('deepseek-only-' + uuid.uuid4().hex)


def main():
    OUTPUT.mkdir(parents=True)
    allowed = {'PATH', 'JAVA_HOME', 'HOME', 'USERPROFILE', 'SYSTEMROOT', 'WINDIR',
               'COMSPEC', 'TEMP', 'TMP', 'APPDATA', 'LOCALAPPDATA', 'PROGRAMDATA'}
    environment = {k: v for k, v in os.environ.items() if k.upper() in allowed}
    commands = []

    def run(name, arguments, cwd=ROOT, expected=0):
        result = subprocess.run(arguments, cwd=cwd, env=environment, capture_output=True)
        (OUTPUT / (name + '.log')).write_bytes(result.stdout + result.stderr)
        commands.append({'name': name, 'command': arguments, 'exitCode': result.returncode,
                         'expectedExitCode': expected})
        (OUTPUT / 'commands.json').write_text(json.dumps(commands, indent=2) + '\n')
        print(name + ' exit=' + str(result.returncode), flush=True)
        if result.returncode != expected:
            raise RuntimeError(name + ' failed; see ' + str(OUTPUT / (name + '.log')))
        return result

    # These assertions guard capability removal, not the absence of historical vocabulary.
    checks = 0
    for directory in ['src/main/java', 'src/main/resources']:
        for path in (ROOT / directory).rglob('*'):
            if path.is_file() and path.suffix in ('.java', '.yaml') and path.name != 'application-local.yaml':
                text = path.read_text()
                assert not any(term in text for term in ('Jev', 'JEV_', 'ai.judge', 'AI_JUDGE_PROVIDER', 'typesafe.ai')), path
        checks += 1
    for filename in ['docs/evaluation/scripts/run-baseline.ps1', 'tools/final-e2e/run.ps1',
                     'tools/browser-e2e/BrowserE2eServer.java']:
        text = (ROOT / filename).read_text()
        assert not any(term in text for term in ('JEV_API_KEY', 'AI_JUDGE_PROVIDER', 'ai.judge', 'JevClientTest')), filename
        checks += 1
    for filename in ['docs/evaluation/scripts/build-reports.py', 'docs/evaluation/scripts/check-jev-access.py',
                     'tools/rag-p4-2/execution/build_delta.py']:
        ast.parse((ROOT / filename).read_text())
        result = run(Path(filename).stem, [sys.executable, '-B', str(ROOT / filename)], expected=1)
        assert b'RETIRED' in result.stderr
        checks += 1
    retired = (ROOT / 'tools/jev-proxy-network/run.ps1').read_text().strip()
    assert retired.startswith("throw 'RETIRED:") and len(retired.splitlines()) == 1
    checks += 1
    assert (ROOT / 'target/classes').exists(), 'Run Maven test-compile first'
    wrapper = ['cmd.exe', '/d', '/c', 'mvnw.cmd'] if os.name == 'nt' else ['sh', 'mvnw']
    cp_file = OUTPUT / 'classpath.txt'
    run('classpath', wrapper + ['-B', '-ntp', 'dependency:build-classpath', '-DincludeScope=test',
                               '-Dmdep.outputFile=' + str(cp_file)])
    classpath = str(ROOT / 'target/classes') + os.pathsep + cp_file.read_text().strip()
    extension = '.exe' if os.name == 'nt' else ''
    java_home = environment.get('JAVA_HOME')
    java = str(Path(java_home) / ('bin/java' + extension)) if java_home else shutil.which('java')
    javac = str(Path(java_home) / ('bin/javac' + extension)) if java_home else shutil.which('javac')
    if not java or not javac:
        raise RuntimeError('Java 21 JDK required')
    classes = OUTPUT / 'classes'
    classes.mkdir()
    sources = sorted(str(p) for name in ['final-e2e', 'rag-e2e', 'browser-e2e']
                     for p in (ROOT / 'tools' / name).glob('*.java'))
    run('compile', [javac, '-proc:none', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes)] + sources)
    # Separate working directory: no old target evidence, execution lock or frozen hash is replaced.
    working = OUTPUT / 'synthetic-work'
    (working / 'target/final-e2e/preparation').mkdir(parents=True)
    runtime_cp = str(classes) + os.pathsep + classpath
    run('final-gate', [java, '-cp', runtime_cp, 'FinalE2eOfflineTest'], cwd=working)
    run('rag-consistency', [java, '-cp', runtime_cp, 'RagE2eConsistencyOfflineTest',
                            str(OUTPUT / 'rag-consistency.json')], cwd=working)
    results = [json.loads((working / 'target/final-e2e/preparation/gate-offline-tests.json').read_text()),
               json.loads((OUTPUT / 'rag-consistency.json').read_text())]
    summary = {'status': 'PASS', 'staticChecks': checks, 'compiledSources': len(sources),
               **{key: sum(r[key] for r in results) for key in ('tests', 'failures', 'errors', 'skipped')},
               'modelCalls': 0, 'databaseAccess': False, 'historicalArchiveReplays': 'NOT_RUN',
               'powershellExecution': 'NOT_RUN (static contract only)'}
    (OUTPUT / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps(summary), flush=True)
    print('OUTPUT=' + str(OUTPUT), flush=True)


if __name__ == '__main__':
    main()
