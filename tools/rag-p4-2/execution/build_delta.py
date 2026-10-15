"""One-time offline forensic build. Refuses to overwrite any existing evidence."""
import difflib
import hashlib
import json
import pathlib
import shutil
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[3]
OUT = ROOT / 'target/rag-p4-2/execution-prep-20261014/evidence'
PREP = ROOT / 'target/rag-p4-2/prepared'
JDK = pathlib.Path('C:/Program Files/Java/jdk-21.0.11/bin')
PREFIX = 'src/main/java/com/kun/aiinterview/interview/evaluation/judge/'
TEST = 'src/test/java/com/kun/aiinterview/interview/'
SOURCES = [PREFIX + p for p in ('JudgeConfiguration.java', 'jev/JevClient.java', 'jev/JevProperties.java')]
PATHS = SOURCES + ['src/main/resources/application.yaml', TEST + 'controller/InterviewControllerTest.java',
                  TEST + 'evaluation/judge/JudgeConfigurationTest.java', TEST + 'evaluation/judge/jev/JevClientTest.java',
                  TEST + 'evaluation/judge/jev/JevJudgeProviderTest.java']
CLASSES = ['target/classes/com/kun/aiinterview/interview/evaluation/judge/' + p for p in
           ('JudgeConfiguration.class', 'JudgeConfiguration$1.class', 'jev/JevClient.class', 'jev/JevProperties.class')]
SNAPSHOT = ROOT / 'target/phase5-e06-offline-forensics/build/classes'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('xb') as file:
        file.write(data)


def json_new(path, data):
    save(path, (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode())


def main():
    assert not OUT.exists(), 'Evidence already exists; never regenerate the accepted byte allowlist'
    OUT.mkdir(parents=True)
    baseline = json.loads((PREP / 'protected-history.json').read_text())
    freeze = json.loads((PREP / 'freeze.json').read_text())['filesSha256']
    assert not (baseline.keys() & freeze.keys())
    baseline.update(freeze)
    differences = {k for k, v in baseline.items() if sha(ROOT / k) != v}
    assert len(baseline) == 1975 and differences == set(PATHS + CLASSES)
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip()
    entries = []
    notes = {
        PATHS[0]: 'Only Jev bean transport delegates to JevClient.createHttpClient; shared builder remains cloned.',
        PATHS[1]: 'Adds dedicated fixed HTTP ProxySelector, connect timeout and NEVER redirects; no JVM global mutation or direct retry.',
        PATHS[2]: 'Adds nested proxy properties and validation; default enabled=false; host excluded from toString.',
        PATHS[3]: 'Only adds ai.judge.jev.proxy enabled/host/port; enabled defaults to false.',
        PATHS[4]: 'Only ActiveProfiles test annotation and its import; no assertions or requests changed.',
        PATHS[5]: 'Adds proxy binding, default-off, invalid-property and shared-client isolation tests only.',
        PATHS[6]: 'Only adds HTTP451 to existing sanitized HTTP-error parameter set.',
        PATHS[7]: 'Only adds HTTP451 fallback parameter and one Java-validation fallback test; frozen v3 rubrics unchanged.'}
    for k in PATHS:
        current = (ROOT / k).read_bytes()
        if k == PATHS[3]:
            old = (SNAPSHOT / 'application.yaml').read_bytes()
            provenance = 'target/phase5-e06-offline-forensics/build/classes/application.yaml'
        elif k == PATHS[7]:
            # Exact inverse of the two proxy-stage additions, proven against the original frozen SHA.
            old = current.replace(b'{401, 429, 451, 500, 529}', b'{401, 429, 500, 529}')
            start = old.index(b'    @Test\n    void region451FallbackCanPassExistingJavaEvaluationValidation()')
            end = old.index(b'    @Test\n    void timeoutFallsBackOnceToOriginalDeepSeekJudge()', start)
            old = old[:start] + old[end:]
            provenance = 'Exact inverse reconstruction of the named 451 test and parameter, SHA-proven against protected-history; retains all pre-freeze v3 changes.'
        else:
            old = subprocess.check_output(['git', 'show', head + ':' + k], cwd=ROOT)
            provenance = 'git:' + head + ':' + k + ' (original LF blob)'
        assert hashlib.sha256(old).hexdigest() == baseline[k], k
        save(OUT / 'old' / k, old)
        save(OUT / 'current' / k, current)
        diff = ''.join(difflib.unified_diff(old.decode().splitlines(True), current.decode().splitlines(True),
                                         fromfile='frozen/' + k, tofile='current/' + k))
        save(OUT / 'diffs' / (pathlib.Path(k).name + '.diff'), diff.encode())
        entries.append(dict(path=k, oldSha256=baseline[k], currentSha256=sha(ROOT / k), explanation=notes[k], oldSource=provenance))
    for k in CLASSES:
        old = SNAPSHOT / pathlib.Path(k).relative_to('target/classes')
        assert sha(old) == baseline[k], k
        save(OUT / 'old' / k, old.read_bytes())
        save(OUT / 'current' / k, (ROOT / k).read_bytes())
        source = PREFIX + ('JudgeConfiguration.java' if 'JudgeConfiguration' in k else 'jev/' + pathlib.Path(k).stem + '.java')
        entries.append(dict(path=k, oldSha256=baseline[k], currentSha256=sha(ROOT / k), explanation='Exact controlled javac reproduction of corresponding source; no arbitrary binary override.',
                            source=source, oldSource=old.relative_to(ROOT).as_posix()))
    # Preserve all earlier execution code/status without changing historical evidence.
    for folder in ('tools/rag-p4-2/execution', 'target/rag-p4-2/execution-prep'):
        for p in (ROOT / folder).glob('*'):
            if p.is_file(): save(OUT / 'previous-execution' / p.relative_to(ROOT), p.read_bytes())
    for name in ('execute.ps1', 'EXECUTION.md'):
        p = ROOT / 'tools/rag-p4-2' / name
        save(OUT / 'previous-execution' / p.relative_to(ROOT), p.read_bytes())
    logs = ['docs/JEV_PROXY_NETWORK_FIX_20261014.md', 'target/jev-proxy-network/preparation/offline-maven-final-2.log',
            'target/final-e2e/repair-20261013/maven-focused.log', 'pom.xml', 'target/rag-e2e/runtime-classpath.txt']
    for k in logs: save(OUT / 'sources' / k, (ROOT / k).read_bytes())
    cp = (ROOT / 'target/rag-e2e/runtime-classpath.txt').read_text(encoding='utf-8-sig').strip()
    jars = [pathlib.Path(p) for p in cp.split(';')]
    lombok, = [p for p in jars if p.name.startswith('lombok-')]
    commands = []
    for version in ('old', 'current'):
        dest = OUT / ('build-' + version)
        cmd = [str(JDK / 'javac.exe'), '-g', '-parameters', '--release', '21', '-encoding', 'UTF-8',
               '-cp', str(ROOT / 'target/classes') + ';' + cp, '-processorpath', str(lombok), '-d', str(dest)]
        cmd += [str(OUT / version / k) for k in SOURCES]
        result = subprocess.run(cmd, cwd=ROOT, capture_output=True)
        save(OUT / ('javac-' + version + '.log'), result.stdout + result.stderr)
        assert result.returncode == 0, result.stderr.decode(errors='replace')
        comparisons = {}
        for k in CLASSES:
            built = dest / pathlib.Path(k).relative_to('target/classes')
            wanted = baseline[k] if version == 'old' else sha(ROOT / k)
            comparisons[k] = dict(builtSha256=sha(built), expectedSha256=wanted, exact=sha(built) == wanted)
            assert sha(built) == wanted, (version, k, sha(built), wanted)
        # ProxySettings is new rather than a historical difference; also prove and bind its exact bytes.
        if version == 'current':
            k = 'target/classes/com/kun/aiinterview/interview/evaluation/judge/jev/JevProperties$ProxySettings.class'
            assert sha(dest / pathlib.Path(k).relative_to('target/classes')) == sha(ROOT / k)
            comparisons[k] = dict(builtSha256=sha(ROOT / k), expectedSha256=sha(ROOT / k), exact=True)
        commands.append(dict(argv=cmd, exitCode=result.returncode, comparisons=comparisons))
    build = dict(javaVersion=subprocess.check_output([str(JDK / 'javac.exe'), '-version']).decode().strip(),
                 compilerSha256=sha(JDK / 'javac.exe'), commands=commands,
                 dependenciesSha256={str(p): sha(p) for p in jars}, sourceCompilerOptions=['-g', '-parameters', '--release', '21', '-encoding', 'UTF-8'],
                 annotationProcessor='lombok-1.18.46', realCalls=0)
    json_new(OUT / 'controlled-build.json', build)
    json_new(OUT / 'initial-integrity.json', dict(total=1975, unchanged=1963, differences=12, originalFreezeSha256=sha(PREP / 'freeze.json')))
    json_new(OUT / 'delta.json', dict(schema='P42_EXACT_HISTORICAL_DELTA_V1', originalFreezeSha256=sha(PREP / 'freeze.json'),
                                    protectedHistorySha256=sha(PREP / 'protected-history.json'), baselineCount=1975, unchangedCount=1963,
                                    differences=entries, filesSha256={p.relative_to(ROOT).as_posix(): sha(p) for p in sorted(OUT.rglob('*')) if p.is_file()},
                                    semanticsChanged=False, realCalls=0))
    print('DELTA_SHA256=' + sha(OUT / 'delta.json'))


if __name__ == '__main__':
    main()
