param(
    [ValidateSet('Prepare','Offline','ExecuteOnce')][string]$Mode='Offline',
    [string]$Authorization=''
)
$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskRoot
$taskJavaHome='C:\Program Files\Java\jdk-21.0.11'
$taskPreparation=Join-Path $taskRoot 'target/final-e2e/preparation'
$taskAttempt=Join-Path $taskRoot 'target/final-e2e/attempts/final-e2e-20261013-001'
if ($Mode -eq 'ExecuteOnce') {
    if ($Authorization -cne 'AUTHORIZE_FINAL_E2E_ONCE_MAX_5_CNY') { throw 'Explicit one-time human fee authorization is required' }
    if (Test-Path -LiteralPath $taskAttempt) { throw 'Attempt already reserved; preserve locks and evidence. No automatic retry.' }
    $taskSeal=Get-Content -LiteralPath (Join-Path $taskPreparation 'source-seal.json') -Raw | ConvertFrom-Json
    foreach ($taskEntry in $taskSeal.PSObject.Properties) {
        if ((Get-FileHash -LiteralPath (Join-Path $taskRoot $taskEntry.Name) -Algorithm SHA256).Hash.ToLowerInvariant() -cne $taskEntry.Value) {
            throw 'Reviewed source or historical evidence changed; run offline preparation again before authorization'
        }
    }
    $taskStatus=Get-Content -LiteralPath (Join-Path $taskPreparation 'offline-status.json') -Raw | ConvertFrom-Json
    if ($taskStatus.status -cne 'OFFLINE_PASS') { throw 'Full offline preparation must pass before paid execution' }
}
New-Item -ItemType Directory -Force -Path (Join-Path $taskPreparation 'classes') | Out-Null
function Invoke-FinalChild {
    param([string]$Executable,[string[]]$ChildArguments,[switch]$Database,[switch]$Models)
    $taskStart=[Diagnostics.ProcessStartInfo]::new($Executable)
    $taskStart.WorkingDirectory=$taskRoot
    $taskStart.UseShellExecute=$false
    $taskStart.CreateNoWindow=$true
    $taskStart.RedirectStandardOutput=$true
    $taskStart.RedirectStandardError=$true
    $taskStart.Environment.Clear()
    foreach ($taskName in @('SystemRoot','WINDIR','COMSPEC','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA','PATHEXT')) {
        $taskValue=[Environment]::GetEnvironmentVariable($taskName,'Process')
        if ($taskValue) { $taskStart.Environment[$taskName]=$taskValue }
    }
    $taskStart.Environment['JAVA_HOME']=$taskJavaHome
    $taskStart.Environment['PATH']="$taskJavaHome\bin;$env:SystemRoot\System32;$env:SystemRoot;$env:SystemRoot\System32\WindowsPowerShell\v1.0"
    if ($Database) {
        foreach ($taskName in @('FINAL_DB_USERNAME','FINAL_DB_PASSWORD')) {
            $taskValue=[Environment]::GetEnvironmentVariable($taskName,'Process')
            if (-not $taskValue) { $taskValue=[Environment]::GetEnvironmentVariable($taskName,'User') }
            if (-not $taskValue) { $taskValue=[Environment]::GetEnvironmentVariable($taskName.Replace('FINAL_',''),'User') }
            if (-not $taskValue) { throw "Existing database credential missing: $taskName" }
            $taskStart.Environment[$taskName]=$taskValue
        }
    }
    if ($Models) {
        foreach ($taskName in @('EMBEDDING_API_KEY','DEEPSEEK_API_KEY','JEV_API_KEY')) {
            $taskValue=[Environment]::GetEnvironmentVariable($taskName,'Process')
            if (-not $taskValue) { $taskValue=[Environment]::GetEnvironmentVariable($taskName,'User') }
            if (-not $taskValue) { throw "Existing provider credential missing: $taskName" }
            $taskStart.Environment[$taskName]=$taskValue
        }
    }
    foreach ($taskArgument in $ChildArguments) { $taskStart.ArgumentList.Add($taskArgument) }
    $taskProcess=[Diagnostics.Process]::Start($taskStart)
    $taskStdout=$taskProcess.StandardOutput.ReadToEndAsync()
    $taskStderr=$taskProcess.StandardError.ReadToEndAsync()
    $taskProcess.WaitForExit()
    if ($taskStdout.Result) { Write-Host $taskStdout.Result }
    if ($taskStderr.Result) { Write-Host $taskStderr.Result }
    if ($taskProcess.ExitCode -ne 0) { throw "Final E2E child failed ($($taskProcess.ExitCode)); no automatic retry" }
}
# Only cached compilation and runtime dependencies; no Maven tests or paid calls from this wrapper.
Invoke-FinalChild -Executable $env:COMSPEC -ChildArguments @('/d','/c','mvnw.cmd -o -B -ntp -DskipTests compile dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile=target/final-e2e/preparation/classpath.txt')
$taskCp=(Join-Path $taskRoot 'target/classes')+';'+(Get-Content (Join-Path $taskPreparation 'classpath.txt') -Raw).Trim()
if ($taskCp -match 'target[/\\]test-classes') { throw 'Test runtime forbidden in real acceptance runner' }
# Reuse original corpus, vector, JSON and HTTP utility classes. Never execute their paid entrypoints.
$taskSources=@(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'tools/rag-e2e') -Filter '*.java' | ForEach-Object FullName)
$taskSources+=@(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | ForEach-Object FullName)
Invoke-FinalChild -Executable (Join-Path $taskJavaHome 'bin/javac.exe') -ChildArguments (@('-proc:none','-encoding','UTF-8','-cp',$taskCp,'-d',(Join-Path $taskPreparation 'classes'))+$taskSources)
$taskRuntimeCp=(Join-Path $taskPreparation 'classes')+';'+$taskCp
$taskArgs=@('-Djdk.httpclient.disableRetryConnect=true','-Djdk.httpclient.enableAllMethodRetry=false','-cp',$taskRuntimeCp)
if ($Mode -eq 'ExecuteOnce') {
    Invoke-FinalChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments ($taskArgs+@('FinalE2eRunner','execute',$Authorization)) -Database -Models
} elseif ($Mode -eq 'Prepare') {
    Invoke-FinalChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments ($taskArgs+@('FinalE2eRunner','prepare')) -Database
} else {
    Invoke-FinalChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments ($taskArgs+@('FinalE2eOfflineTest'))
    $taskPreflight=Get-Content -LiteralPath (Join-Path $taskPreparation 'report.json') -Raw | ConvertFrom-Json
    if ($taskPreflight.preflight.status -cne 'PASS' -or $taskPreflight.productionStartup.status -cne 'PASS') {
        throw 'Run Prepare successfully before final Offline seal; no fee authorization can be consumed yet'
    }
    $taskTests=@('AuthServiceTest','AuthControllerTest','JwtAuthenticationFilterTest','InterviewControllerTest',
        'InterviewSessionServiceTest','InterviewWorkflowServiceTest','InterviewWorkflowTransactionServiceIntegrationTest',
        'InterviewReportServiceTest','InterviewReportTransactionServiceIntegrationTest','UserWeaknessServiceTest',
        'JevJudgeProviderTest','JevClientTest','DeepSeekEvaluationClientTest','DeepSeekInterviewReportGeneratorTest',
        'KnowledgeRetrievalServiceTest','EvaluationOrchestrationServiceTest','EnvironmentIsolationInitializerTest','FullInterviewBusinessOfflineTest')
    $taskTotal=0
    $taskSuites=@()
    New-Item -ItemType Directory -Force -Path (Join-Path $taskPreparation 'focused-reports') | Out-Null
    foreach ($taskTest in $taskTests) {
        $taskMatches=@(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'target/surefire-reports') -Filter "TEST-*.$taskTest.xml")
        if ($taskMatches.Count -ne 1) { throw "Focused test evidence missing or ambiguous: $taskTest" }
        [xml]$taskXml=Get-Content -LiteralPath $taskMatches[0].FullName
        $taskSuite=$taskXml.testsuite
        if ([int]$taskSuite.failures -ne 0 -or [int]$taskSuite.errors -ne 0 -or [int]$taskSuite.skipped -ne 0) {
            throw "Focused regression did not pass: $taskTest"
        }
        $taskTotal+=[int]$taskSuite.tests
        $taskSuites+=@{name=$taskTest;tests=[int]$taskSuite.tests;failures=0;errors=0;skipped=0}
        Copy-Item -LiteralPath $taskMatches[0].FullName -Destination (Join-Path $taskPreparation 'focused-reports')
    }
    @{status='OFFLINE_PASS';focused=@{tests=$taskTotal;failures=0;errors=0;skipped=0;suites=$taskSuites};
        gateTests=@{tests=10;failures=0;errors=0;skipped=0};realModelCalls=0;
        finalStatus='BLOCKED';remaining='Explicit one-time fee authorization'} | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath (Join-Path $taskPreparation 'offline-status.json') -Encoding utf8NoBOM
    $taskHashes=[ordered]@{}
    $taskFiles=@(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'src/main') -File -Recurse | Where-Object Name -ne 'application-local.yaml')
    $taskFiles+=@(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'tools') -File -Recurse | Where-Object { $_.FullName -notmatch '__pycache__' })
    $taskFiles+=@(Get-Item -LiteralPath (Join-Path $taskRoot 'src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java'),
        (Join-Path $taskRoot 'src/test/java/com/kun/aiinterview/interview/controller/InterviewControllerTest.java'),
        (Join-Path $taskPreparation 'report.json'),(Join-Path $taskPreparation 'offline-status.json'))
    # Seal all existing historical execution locks and raw reports without rewriting them.
    $taskFiles+=@(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'target/rag-e2e') -File -Recurse | Where-Object { $_.Name -match '\.lock$|^report\.(json|md)$|^review-seal\.json$' })
    foreach ($taskFile in ($taskFiles | Sort-Object FullName -Unique)) {
        $taskHashes[[IO.Path]::GetRelativePath($taskRoot,$taskFile.FullName).Replace('\','/')]=(Get-FileHash -LiteralPath $taskFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $taskHashes | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $taskPreparation 'source-seal.json') -Encoding utf8NoBOM
    Write-Host "OFFLINE_PASS: $taskTotal focused tests + 10 forwarding gate checks; zero real model calls"
}
