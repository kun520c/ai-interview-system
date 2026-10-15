"""Create the fixed R02 tooling once from preserved sources; never edit the old execution."""
import hashlib
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).resolve().parent
OUT = ROOT / 'target/rag-p4-2/execution-r02-20261014'
ID = 'P42-R02-20261014'
AUTH = 'AUTHORIZE P4.2 P42-R02-20261014 8 EACH ONCE MAX24 CNY1'


def new(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        assert path.read_text(encoding='utf-8') == text, 'Refuse to overwrite: ' + str(path)
        return
    with path.open('x', encoding='utf-8', newline='\n') as file:
        file.write(text)


def replace(text, old, value):
    assert old in text, old
    return text.replace(old, value)


assert not (OUT / 'manifest.json').exists(), 'Already sealed; use Verify'
OUT.mkdir(parents=True, exist_ok=True)
old_manifest = json.loads((ROOT / 'target/rag-p4-2/execution-prep-20261014/manifest.json').read_text(encoding='utf-8'))
protected = {ROOT / p for p in old_manifest['filesSha256']}
for folder in ('target/rag-p4-2/prepared', 'target/rag-p4-2/run-once',
               'target/rag-p4-2/stop-diagnosis-20261014', 'tools/rag-p4-2'):
    protected.update(p for p in (ROOT / folder).rglob('*') if p.is_file())
protected.update(ROOT / 'target/rag-p4-2/execution-prep-20261014' / p for p in ('manifest.json', 'manifest.sha256'))
hashes = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(protected)}
new(OUT / 'preservation-before.json', json.dumps(hashes, indent=2) + '\n')
new(OUT / 'EXECUTION_ID_RESERVED.lock', json.dumps(dict(executionId=ID, authorization=AUTH,
    status='PREPARED_IDENTITY_ONLY_NOT_EXECUTED_OR_AUTHORIZED', predecessor='target/rag-p4-2/run-once',
    predecessorArtifactsSha256=hashes['target/rag-p4-2/run-once/artifacts.json'],
    purpose='Permanent identity reservation; the consumed execution lock is created only after fresh consent.'), indent=2) + '\n')

source = (ROOT / 'target/rag-p4-2/stop-diagnosis-20261014/offline-fix/P42ExecuteOnce.java').read_text(encoding='utf-8')
source = replace(source, 'P42ExecuteOnce', 'P42R02ExecuteOnce')
source = replace(source, 'AUTHORIZE P4.2 8 EACH ONCE MAX24 CNY1', AUTH)
source = replace(source, 'target/rag-p4-2/execution-prep-20261014/manifest.json', 'target/rag-p4-2/execution-r02-20261014/manifest.json')
# The original exact delta stays at its original immutable location.
source = replace(source, 'SEAL.resolveSibling("evidence/delta.json")', 'Path.of("target/rag-p4-2/execution-prep-20261014/evidence/delta.json")')
source = replace(source, 'target/rag-p4-2/run-once', 'target/rag-p4-2/run-r02-20261014')
start = source.index('    static JsonNode verifyFrozen()')
end = source.index('    static void verifyBaselineMap', start)
source = source[:start] + '''    static final String EXECUTION_ID = "P42-R02-20261014";
    static JsonNode verifyFrozen() throws Exception {
        JsonNode dataset = P42ExecuteOnce.verifyFrozen(); // Original baseline, delta, binaries and seal stay mandatory.
        require(sha(SEAL).equals(Files.readString(SEAL.resolveSibling("manifest.sha256")).strip()), "R02_SEAL_DRIFT");
        JsonNode seal = read(SEAL);
        require(seal.path("executionId").asText().equals(EXECUTION_ID) && seal.path("authorization").asText().equals(AUTH), "R02_IDENTITY_BINDING");
        require(seal.path("questionSha256").asText().equals(QUESTIONS_SHA) && seal.path("planSha256").asText().equals(PLAN_SHA), "R02_FROZEN_BINDING");
        verifyMap(seal.path("filesSha256")); verifyMap(seal.path("dependenciesSha256"));
        verifyMap(read(SEAL.resolveSibling("preservation-before.json")));
        Set<String> actual = new HashSet<>(), expected = new HashSet<>();
        for (var folder : seal.path("classDirectories")) try (var files = Files.walk(Path.of(folder.asText()))) {
            for (var p : files.filter(f -> f.toString().endsWith(".class")).toList()) actual.add(p.toString().replace('\\\\', '/'));
        }
        seal.path("filesSha256").fieldNames().forEachRemaining(p -> { if (p.endsWith(".class")) expected.add(p); });
        require(actual.equals(expected), "UNSEALED_RUNTIME_CLASS");
        contracts(dataset); return dataset;
    }
''' + source[end:]
source = replace(source, '        return result;\n    }\n    static String code(Exception error)', '''        String grpc="NONE";
        for(var item:chain) if(item.has("grpcCode")) grpc=item.path("grpcCode").asText();
        result.put("grpcStatusCode",grpc).put("rootCauseSummary",phase+"; root="+result.path("rootExceptionType").asText()+"; grpc="+grpc+"; exception messages omitted");
        return result;
    }
    ObjectNode diagnostics(Throwable error,String phase) {
        var result=failureDiagnostics(error,phase); result.set("externalCallsAtFailure",JSON.valueToTree(counts)); return result;
    }
    static String code(Exception error)''')
source = replace(source, '    ObjectNode runtimeMetadata=', '    String failurePhase="BEFORE_RESERVATION";\n    ObjectNode runtimeMetadata=')
source = replace(source, '        long start=System.nanoTime();\n        ObjectNode validation=', '        failurePhase=stage.name();\n        long start=System.nanoTime();\n        ObjectNode validation=')
source = replace(source, '.put("failureCode",code(error)); throw error;', '.put("failureCode",code(error)); validation.set("failureDiagnostics",diagnostics(error,stage.name())); throw error;') if source.count('.put("failureCode",code(error)); throw error;') == 1 else source
# Attach diagnostics at all stage captures (validation and receipts).
source = source.replace('validation.put("status","FAILED_STOP").put("failureCode",code(error)); throw error;',
    'validation.put("status","FAILED_STOP").put("failureCode",code(error)); validation.set("failureDiagnostics",diagnostics(error,stage.name())); throw error;')
source = source.replace('receipt.put("status","FAILED_STOP").put("failureCode",code(error)); throw error;',
    'receipt.put("status","FAILED_STOP").put("failureCode",code(error)); receipt.set("failureDiagnostics",diagnostics(error,stage.name())); throw error;')
source = source.replace('receipt.put("status","FAILED_STOP").put("failureCode",code(e)); throw e;',
    'receipt.put("status","FAILED_STOP").put("failureCode",code(e)); receipt.set("failureDiagnostics",diagnostics(e,Stage.MILVUS_SEARCH.name())); throw e;')
source = replace(source, 'String failurePhase="ISOLATION";', 'failurePhase="ISOLATION";')
source = replace(source, 'summary.set("failureDiagnostics",failureDiagnostics(e,failurePhase))', 'summary.set("failureDiagnostics",diagnostics(e,failurePhase))')
source = replace(source, 'summary.put("resourcesUnchanged",false).put("resourceFailureCode",code(e));',
    'summary.put("resourcesUnchanged",false).put("resourceFailureCode",code(e));') if 'summary.put("resourcesUnchanged",false).put("resourceFailureCode",code(e));' in source else source
source = replace(source, '.put("resourceFailureCode",code(e)); }', '.put("resourceFailureCode",code(e)); summary.set("resourceFailureDiagnostics",diagnostics(e,"RESOURCE_POSTCHECK")); }')
source = replace(source, '.put("closeFailureCode",code(e)); }', '.put("closeFailureCode",code(e)); summary.set("closeFailureDiagnostics",diagnostics(e,"RESOURCE_CLOSE")); }')
source = replace(source, 'Map.of("authorization",AUTH,"prepSha256",PREP_SHA', 'Map.of("executionId",EXECUTION_ID,"authorization",AUTH,"prepSha256",PREP_SHA')
source = replace(source, '.put("origin",origin).put("executionStatus","IN_PROGRESS")', '.put("executionId",EXECUTION_ID).put("origin",origin).put("executionStatus","IN_PROGRESS")')
new(HERE / 'execution/P42R02ExecuteOnce.java', source)

runner_test = (ROOT / 'tools/rag-p4-2/execution/P42RunnerTest.java').read_text(encoding='utf-8').replace('P42ExecuteOnce', 'P42R02ExecuteOnce').replace('P42RunnerTest', 'P42R02RunnerTest')
new(HERE / 'execution/P42R02RunnerTest.java', runner_test)
diagnostic_test = (ROOT / 'target/rag-p4-2/stop-diagnosis-20261014/offline-fix/P42StopDiagnosticsTest.java').read_text(encoding='utf-8').replace('P42ExecuteOnce', 'P42R02ExecuteOnce')
new(HERE / 'execution/P42StopDiagnosticsTest.java', diagnostic_test)

analyzer = (ROOT / 'target/rag-p4-2/stop-diagnosis-20261014/offline-fix/analyze.py').read_text(encoding='utf-8')
# The independent diagnostic copy had no project root; restore the known repository location.
analyzer = replace(analyzer, "ROOT = pathlib.Path(__file__).resolve().parents[3]", "ROOT = pathlib.Path(__file__).resolve().parents[3]") if 'parents[3]' in analyzer else analyzer
analyzer = replace(analyzer, "RUN = ROOT / 'target/rag-p4-2/run-once'", "RUN = ROOT / 'target/rag-p4-2/run-r02-20261014'")
analyzer = replace(analyzer, "SEAL = ROOT / 'target/rag-p4-2/execution-prep-20261014/manifest.json'", "SEAL = ROOT / 'target/rag-p4-2/execution-r02-20261014/manifest.json'")
analyzer = replace(analyzer, "DELTA = SEAL.parent / 'evidence/delta.json'", "DELTA = ROOT / 'target/rag-p4-2/execution-prep-20261014/evidence/delta.json'")
start = analyzer.index('def verify_execution():')
end = analyzer.index('def dependencies():', start)
analyzer = analyzer[:start] + '''EXECUTION_ID = 'P42-R02-20261014'
AUTH = 'AUTHORIZE P4.2 P42-R02-20261014 8 EACH ONCE MAX24 CNY1'


def verify_execution():
    verify_prepared()
    import importlib.util
    spec = importlib.util.spec_from_file_location('preserved_p42', ROOT / 'tools/rag-p4-2/execution/analyze.py')
    old = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(old)
    old.verify_execution()
    old.dependencies()
    require(sha(SEAL) == SEAL.with_name('manifest.sha256').read_text(encoding='ascii').strip(), 'R02_SEAL_DRIFT')
    seal = read(SEAL)
    require(seal['executionId'] == EXECUTION_ID and seal['authorization'] == AUTH, 'R02_IDENTITY_BINDING')
    require(seal['questionSha256'] == QUESTIONS_SHA and seal['planSha256'] == PLAN_SHA, 'R02_FROZEN_BINDING')
    verify_map(seal['filesSha256'])
    verify_class_inventory(seal)
    verify_map(read(SEAL.parent / 'preservation-before.json'))


def seal_execution():
    raise ValueError('USE_PREPARE_R02_OFFLINE_ONLY')


''' + analyzer[end:]
analyzer = replace(analyzer, "    if verify:\n        require(lock['executionSealSha256'] == sha(SEAL), 'LOCK_EXECUTION_BINDING')", "    if verify:\n        require(lock.get('executionId') == EXECUTION_ID and lock.get('authorization') == AUTH, 'R02_LOCK_IDENTITY')\n        require(lock['executionSealSha256'] == sha(SEAL), 'LOCK_EXECUTION_BINDING')")
analyzer = replace(analyzer, "'resourceFailureCode', 'closeFailureCode'", "'resourceFailureCode', 'resourceFailureDiagnostics', 'closeFailureCode', 'closeFailureDiagnostics'")
analyzer = replace(analyzer, "ROOT / 'target/rag-p4-2/analysis'", "ROOT / 'target/rag-p4-2/analysis-r02-20261014'")
new(HERE / 'execution/analyze.py', analyzer)

python_test = (ROOT / 'tools/rag-p4-2/execution/test_execution.py').read_text(encoding='utf-8')
python_test = replace(python_test, "PREP = ROOT / 'target/rag-p4-2/execution-prep-20261014'", "PREP = ROOT / 'target/rag-p4-2/execution-r02-20261014'")
python_test = python_test.replace('tools/rag-p4-2/execute.ps1', 'tools/rag-p4-2-r02/execute.ps1').replace('AUTHORIZE P4.2 8 EACH ONCE MAX24 CNY1', AUTH)
new(HERE / 'execution/test_execution.py', python_test)

launcher = (ROOT / 'tools/rag-p4-2/execute.ps1').read_text(encoding='utf-8')
launcher = launcher.replace("'Offline','Verify','ExecuteOnce','Analyze'", "'Verify','Preflight','ExecuteOnce','Analyze'")
launcher = replace(launcher, 'target/rag-p4-2/execution-prep-20261014', 'target/rag-p4-2/execution-r02-20261014')
launcher = replace(launcher, "$taskClasses + ';' +", "$taskClasses + ';' + (Join-Path $taskRoot 'target/rag-p4-2/execution-prep-20261014/classes') + ';' +")
launcher = launcher.replace("'P42ExecuteOnce'", "'P42R02ExecuteOnce'").replace('AUTHORIZE P4.2 8 EACH ONCE MAX24 CNY1', AUTH)
start = launcher.index("if ($Mode -eq 'Offline')")
end = launcher.index("Invoke-P42Analysis -Arguments @('Verify')", start)
launcher = launcher[:start] + launcher[end:]
launcher = replace(launcher, "if ($Mode -eq 'Verify') { return }", '''if ($Mode -eq 'Verify') { return }
if ($Mode -eq 'Preflight') {
    $taskPassword = Get-P42Value -Names @('P42_READONLY_DB_PASSWORD','E2E_DB_PASSWORD','P1_DB_PASSWORD')
    if (-not $taskPassword) { $taskPassword = Read-P42Secret -Prompt 'Existing rag_e2e_writer password (hidden; read-only check)' }
    $taskToken = Get-P42Value -Names @('E2E_MILVUS_TOKEN')
    foreach ($taskSecret in @($taskPassword,$taskToken)) { if ($taskSecret) { $taskSecrets.Add($taskSecret) } }
    $taskEnvelope = @{dbPassword=$taskPassword; milvusToken=$taskToken} | ConvertTo-Json -Compress
    $taskOutput = Join-Path $taskRoot ('target/rag-p4-2/read-only-r02-' + [Guid]::NewGuid().ToString('N'))
    try {
        Invoke-P42Child -Executable (Join-Path $taskJava 'java.exe') -ChildArguments @('-Dfile.encoding=UTF-8','-cp',$taskCp,'P42ResourcePreflight',$taskOutput) -PrivateInput $taskEnvelope
    } finally { $taskEnvelope=$null; $taskPassword=$null; $taskToken=$null; $taskSecrets.Clear() }
    return
}''')
launcher = replace(launcher, 'target/rag-p4-2/run-once', 'target/rag-p4-2/run-r02-20261014')
launcher = replace(launcher, "$taskPassword = Read-P42Secret -Prompt 'Existing rag_e2e_writer@127.0.0.1 password (hidden)'", "$taskPassword = Get-P42Value -Names @('P42_READONLY_DB_PASSWORD','E2E_DB_PASSWORD','P1_DB_PASSWORD')\nif (-not $taskPassword) { $taskPassword = Read-P42Secret -Prompt 'Existing rag_e2e_writer@127.0.0.1 password (hidden)' }")
new(HERE / 'execute.ps1', launcher)
print('R02 sources created; old files preserved; model calls=0; execution NOT authorized or consumed.')
