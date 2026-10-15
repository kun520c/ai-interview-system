param(
    [ValidateSet('Offline','ExecuteOnce','ExecuteRecoveryOnce')][string]$Mode='Offline',
    [string]$Authorization='',
    [string]$RunId=''
)
$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskRoot
$taskJavaHome='C:\Program Files\Java\jdk-21.0.11'
if (-not (Test-Path -LiteralPath (Join-Path $taskJavaHome 'bin/java.exe'))) { throw 'Java 21 runtime missing' }
if ($Mode -eq 'ExecuteOnce' -and $Authorization -cne 'RAG_E2E_TEST_COPY_AND_ONE_ANSWER') {
    throw 'Future explicit authorization must include test corpus/vector copy and one answer submission'
}
if ($Mode -eq 'ExecuteRecoveryOnce') {
    $taskRecoveryPlan=Get-Content -LiteralPath 'tools/rag-e2e/recovery-plan.json' -Raw | ConvertFrom-Json
    if ($RunId -cne $taskRecoveryPlan.runId -or $Authorization -cne $taskRecoveryPlan.authorization) {
        throw 'Recovery requires the exact reviewed attempt ID and new explicit authorization'
    }
    if (Test-Path -LiteralPath (Join-Path 'target/rag-e2e/attempts' $RunId)) {
        throw 'This recovery attempt was already reserved; no automatic retry or new ID is allowed'
    }
    if ($env:E2E_DB_USERNAME -cne 'rag_e2e_writer' -or -not $env:E2E_DB_PASSWORD) {
        throw 'Restore the existing writer password in this PowerShell process before reserving an attempt'
    }
    foreach ($name in @('EMBEDDING_API_KEY','DEEPSEEK_API_KEY')) {
        if (-not [Environment]::GetEnvironmentVariable($name,'Process') -and -not [Environment]::GetEnvironmentVariable($name,'User')) {
            throw "Required model credential is missing: $name (no attempt reserved)"
        }
    }
}
if ($Mode -ne 'ExecuteRecoveryOnce' -and $RunId) { throw 'RunId is only valid for the reviewed recovery mode' }

# Every launched child gets an allowlisted environment. No root credential fallback, local YAML import,
# SPRING_*, RUN_REAL_*, JAVA_TOOL_OPTIONS, _JAVA_OPTIONS, Maven options, Jev or provider overrides.
function Invoke-E2eChild {
    param([string]$Executable,[string[]]$ChildArguments,[switch]$WithE2eSecrets)
    $taskStart=[Diagnostics.ProcessStartInfo]::new()
    $taskStart.FileName=$Executable
    $taskStart.WorkingDirectory=$taskRoot
    $taskStart.UseShellExecute=$false
    $taskStart.CreateNoWindow=$true
    $taskStart.RedirectStandardOutput=$true
    $taskStart.RedirectStandardError=$true
    $taskStart.Environment.Clear()
    foreach ($name in @('SystemRoot','WINDIR','COMSPEC','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA','PATHEXT')) {
        $value=[Environment]::GetEnvironmentVariable($name,'Process')
        if ($value) { $taskStart.Environment[$name]=$value }
    }
    $taskStart.Environment['JAVA_HOME']=$taskJavaHome
    $taskStart.Environment['PATH']="$taskJavaHome\bin;$env:SystemRoot\System32;$env:SystemRoot;$env:SystemRoot\System32\WindowsPowerShell\v1.0"
    if ($WithE2eSecrets) {
        foreach ($name in @('E2E_DB_USERNAME','E2E_DB_PASSWORD','E2E_SOURCE_DB_USERNAME','E2E_SOURCE_DB_PASSWORD',
            'EMBEDDING_API_KEY','DEEPSEEK_API_KEY','E2E_MILVUS_TOKEN')) {
            if ($Mode -eq 'ExecuteRecoveryOnce' -and $name -like 'E2E_SOURCE_*') { continue }
            $value=[Environment]::GetEnvironmentVariable($name,'Process')
            if (-not $value) { $value=[Environment]::GetEnvironmentVariable($name,'User') }
            if ($value) { $taskStart.Environment[$name]=$value }
        }
    }
    foreach ($argument in $ChildArguments) { $taskStart.ArgumentList.Add($argument) }
    $taskProcess=[Diagnostics.Process]::Start($taskStart)
    $taskStdout=$taskProcess.StandardOutput.ReadToEndAsync()
    $taskStderr=$taskProcess.StandardError.ReadToEndAsync()
    $taskProcess.WaitForExit()
    if ($taskStdout.Result) { Write-Host $taskStdout.Result }
    if ($taskStderr.Result) { Write-Host $taskStderr.Result }
    if ($taskProcess.ExitCode -ne 0) { throw "E2E child exited $($taskProcess.ExitCode); no automatic retry" }
}

New-Item -ItemType Directory -Force -Path 'target/rag-e2e/classes' | Out-Null
if ($Mode -ne 'Offline') {
    if ($Mode -eq 'ExecuteOnce' -and ((Test-Path -LiteralPath 'target/rag-e2e/EXECUTION_CONSUMED.lock') -or (Test-Path -LiteralPath 'target/rag-e2e/run-once'))) {
        throw 'Legacy execution is already consumed; its lock and reports must be preserved'
    }
    if (-not (Test-Path -LiteralPath 'target/rag-e2e/review-seal.json')) { throw 'Run Offline first to produce a reviewable source seal' }
    $taskSeal=Get-Content -LiteralPath 'target/rag-e2e/review-seal.json' -Raw | ConvertFrom-Json
    foreach ($entry in $taskSeal.PSObject.Properties) {
        if ((Get-FileHash -LiteralPath $entry.Name -Algorithm SHA256).Hash.ToLowerInvariant() -cne $entry.Value) {
            throw 'Reviewed E2E source changed; stop before external access'
        }
    }
}

# Offline Maven only: compiler/classpath preparation, no tests, no application boot, no external downloads.
Invoke-E2eChild -Executable $env:COMSPEC -ChildArguments @('/d','/c','mvnw.cmd -o -B -ntp -DskipTests compile dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile=target/rag-e2e/runtime-classpath.txt')
$taskCp=(Join-Path $taskRoot 'target/classes')+';'+(Get-Content -LiteralPath 'target/rag-e2e/runtime-classpath.txt' -Raw).Trim()
if ($taskCp -match 'target[/\\]test-classes') { throw 'Test classpath forbidden for this standalone HTTP runner' }
$taskSources=@(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | ForEach-Object FullName)
Invoke-E2eChild -Executable (Join-Path $taskJavaHome 'bin/javac.exe') -ChildArguments (@('-proc:none','-encoding','UTF-8','-cp',$taskCp,'-d','target/rag-e2e/classes')+$taskSources)
$taskRuntimeCp=(Join-Path $taskRoot 'target/rag-e2e/classes')+';'+$taskCp
$taskJavaArgs=@('-Djdk.httpclient.disableRetryConnect=true','-Djdk.httpclient.enableAllMethodRetry=false','-cp',$taskRuntimeCp)
if ($Mode -eq 'Offline') {
    Invoke-E2eChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments ($taskJavaArgs+@('RagE2eOfflineTest'))
    # Include production source/resource hashes; preserve Cursor/user edits and require a new review if they change.
    $taskHashes=[ordered]@{}
    $taskFiles=@(Get-ChildItem -LiteralPath 'src/main' -File -Recurse | Where-Object Name -ne 'application-local.yaml')
    $taskFiles+=@(Get-ChildItem -LiteralPath $PSScriptRoot -File -Recurse)
    foreach ($file in ($taskFiles | Sort-Object FullName)) {
        $taskHashes[[IO.Path]::GetRelativePath($taskRoot,$file.FullName).Replace('\','/')]=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $taskHashes | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath 'target/rag-e2e/review-seal.json' -Encoding utf8NoBOM
    Invoke-E2eChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments ($taskJavaArgs+@('RagE2eRunner','offline')) -WithE2eSecrets
} else {
    $taskRunnerMode = if ($Mode -eq 'ExecuteRecoveryOnce') { @('execute-recovery-authorized-once',$RunId) } else { @('execute-authorized-once') }
    Invoke-E2eChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments ($taskJavaArgs+@('RagE2eRunner')+$taskRunnerMode) -WithE2eSecrets
}
