param([string]$RunId='rag-e2e-attempt-003-20261012')
$ErrorActionPreference='Stop'
if($RunId -cne 'rag-e2e-attempt-003-20261012'){throw 'Only preserved attempt 003 is supported'}
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskRoot
$taskJavaHome='C:\Program Files\Java\jdk-21.0.11'
$taskOriginal=Join-Path 'target/rag-e2e/attempts' $RunId
foreach($taskName in @('report.json','report.md','ATTEMPT_RESERVED.lock','EXECUTION_CONSUMED.lock')) {
    if(-not(Test-Path -LiteralPath (Join-Path $taskOriginal $taskName))){throw "Preserved artifact required: $taskName"}
}
# Existing credentials are read only into this child, never printed or written to disk.
$taskDbUser=[Environment]::GetEnvironmentVariable('E2E_DB_USERNAME','Process')
$taskDbPassword=[Environment]::GetEnvironmentVariable('E2E_DB_PASSWORD','Process')
if(-not $taskDbUser -or -not $taskDbPassword) {
    $taskDbUser=[Environment]::GetEnvironmentVariable('DB_USERNAME','User')
    $taskDbPassword=[Environment]::GetEnvironmentVariable('DB_PASSWORD','User')
}
if(-not $taskDbUser -or -not $taskDbPassword){throw 'Existing MySQL credentials missing; restore them without starting ExecuteRecoveryOnce'}
$taskOut=Join-Path ('target/rag-e2e/reverification/'+$RunId) ((Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $taskOut | Out-Null
$taskProtected=[ordered]@{}
$taskFiles=@(Get-ChildItem -LiteralPath $taskOriginal -File)
$taskFiles+=@(Get-ChildItem -LiteralPath 'target/rag-e2e/run-once' -File)
$taskFiles+=@(Get-ChildItem -LiteralPath 'target/rag-e2e/failure-diagnosis-20261012' -File -Recurse)
$taskFiles+=@(Get-Item -LiteralPath 'target/rag-e2e/EXECUTION_CONSUMED.lock','target/rag-e2e/review-seal.json')
foreach($taskFile in $taskFiles){$taskProtected[$taskFile.FullName]=(Get-FileHash -LiteralPath $taskFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
$taskProtected | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $taskOut 'protected-artifacts.before.json') -Encoding utf8NoBOM
function Invoke-ReadOnlyChild {
    param([string]$Executable,[string[]]$ChildArguments,[switch]$DatabaseCredentials)
    $taskStart=[Diagnostics.ProcessStartInfo]::new($Executable)
    $taskStart.WorkingDirectory=$taskRoot
    $taskStart.UseShellExecute=$false
    $taskStart.CreateNoWindow=$true
    $taskStart.RedirectStandardOutput=$true
    $taskStart.RedirectStandardError=$true
    $taskStart.Environment.Clear()
    foreach($taskName in @('SystemRoot','WINDIR','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA')) {
        $taskValue=[Environment]::GetEnvironmentVariable($taskName,'Process')
        if($taskValue){$taskStart.Environment[$taskName]=$taskValue}
    }
    if($DatabaseCredentials) {
        $taskStart.Environment['DIAG_DB_USERNAME']=$taskDbUser
        $taskStart.Environment['DIAG_DB_PASSWORD']=$taskDbPassword
    }
    foreach($taskArgument in $ChildArguments){$taskStart.ArgumentList.Add($taskArgument)}
    $taskProcess=[Diagnostics.Process]::Start($taskStart)
    $taskStdout=$taskProcess.StandardOutput.ReadToEndAsync()
    $taskStderr=$taskProcess.StandardError.ReadToEndAsync()
    $taskProcess.WaitForExit()
    if($taskStdout.Result){Write-Host $taskStdout.Result}
    if($taskStderr.Result){Write-Host $taskStderr.Result}
    if($taskProcess.ExitCode -ne 0){throw "Read-only/offline child failed: $($taskProcess.ExitCode)"}
}
try {
    $taskCp=(Join-Path $taskRoot 'target/classes')+';'+(Get-Content 'target/rag-e2e/runtime-classpath.txt' -Raw).Trim()
    $taskClasses=Join-Path $taskOut 'classes'
    New-Item -ItemType Directory -Path $taskClasses | Out-Null
    $taskSources=@(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | ForEach-Object FullName)
    Invoke-ReadOnlyChild -Executable (Join-Path $taskJavaHome 'bin/javac.exe') -ChildArguments (@('-proc:none','-encoding','UTF-8','-cp',$taskCp,'-d',$taskClasses)+$taskSources)
    $taskRuntimeCp=(Join-Path $taskRoot $taskClasses)+';'+$taskCp
    $taskJava=Join-Path $taskJavaHome 'bin/java.exe'
    Invoke-ReadOnlyChild -Executable $taskJava -ChildArguments @('-cp',$taskRuntimeCp,'RagE2eConsistencyOfflineTest',(Join-Path $taskOut 'consistency-offline-tests.json'))
    Invoke-ReadOnlyChild -Executable $taskJava -ChildArguments @('-cp',$taskRuntimeCp,'RagE2eOfflineTest',(Join-Path $taskOut 'existing-offline-tests.json'))
    $taskSnapshot=Join-Path $taskOut 'database.snapshot.json'
    Invoke-ReadOnlyChild -Executable $taskJava -ChildArguments @('-cp',$taskRuntimeCp,'RagE2eReadOnlySnapshot',$taskSnapshot) -DatabaseCredentials
    Invoke-ReadOnlyChild -Executable $taskJava -ChildArguments @('-cp',$taskRuntimeCp,'RagE2eReadOnlyReplayOfflineTest',$taskSnapshot,(Join-Path $taskOut 'negative-replay-tests'))
    Invoke-ReadOnlyChild -Executable $taskJava -ChildArguments @('-cp',$taskRuntimeCp,'RagE2eReadOnlyVerifier',$taskSnapshot,(Join-Path $taskOut 'reverification.json'))
} finally {
    foreach($taskEntry in $taskProtected.GetEnumerator()) {
        if((Get-FileHash -LiteralPath $taskEntry.Key -Algorithm SHA256).Hash.ToLowerInvariant() -cne $taskEntry.Value){throw 'Preserved artifact changed'}
    }
    @{allProtectedArtifactsUnchanged=$true;protectedFiles=$taskProtected.Count} | ConvertTo-Json | Set-Content (Join-Path $taskOut 'protected-artifacts.after.json') -Encoding utf8NoBOM
}
Write-Host "Read-only verification evidence: $taskOut/reverification.json"
