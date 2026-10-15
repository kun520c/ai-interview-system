param([ValidateSet('Offline','VerifyReadOnly')][string]$Mode='VerifyReadOnly')
$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskRoot
$taskJavaHome='C:\Program Files\Java\jdk-21.0.11'
$taskOutput=Join-Path $taskRoot 'target/final-e2e/second-repair-20261014'
New-Item -ItemType Directory -Force -Path (Join-Path $taskOutput 'classes') | Out-Null
$taskManifest=Join-Path $taskOutput 'preserved-attempts.sha256.json'
if(-not(Test-Path -LiteralPath $taskManifest)) {
    $taskHashes=[ordered]@{}
    foreach($taskId in @('final-e2e-20261013-001','final-e2e-20261013-002')) {
        foreach($taskFile in Get-ChildItem -LiteralPath (Join-Path $taskRoot "target/final-e2e/attempts/$taskId") -File -Recurse) {
            $taskHashes[[IO.Path]::GetRelativePath($taskRoot,$taskFile.FullName).Replace('\','/')]=(Get-FileHash -LiteralPath $taskFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
    $taskHashes | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $taskManifest -Encoding utf8
}
$taskPreserved=Get-Content -LiteralPath $taskManifest -Raw | ConvertFrom-Json
function Assert-PreservedAttempts {
    foreach($taskEntry in $taskPreserved.PSObject.Properties) {
        if((Get-FileHash -LiteralPath $taskEntry.Name -Algorithm SHA256).Hash.ToLowerInvariant() -cne $taskEntry.Value) {throw 'Historical attempt changed; stop without any execution'}
    }
}
function Invoke-ReadOnlyChild {
    param([string]$Executable,[string[]]$ChildArguments,[switch]$Database)
    $taskStart=[Diagnostics.ProcessStartInfo]::new($Executable)
    $taskStart.WorkingDirectory=$taskRoot;$taskStart.UseShellExecute=$false;$taskStart.CreateNoWindow=$true
    $taskStart.RedirectStandardOutput=$true;$taskStart.RedirectStandardError=$true;$taskStart.Environment.Clear()
    foreach($taskName in @('SystemRoot','WINDIR','COMSPEC','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA','PATHEXT')) {
        $taskValue=[Environment]::GetEnvironmentVariable($taskName,'Process');if($taskValue){$taskStart.Environment[$taskName]=$taskValue}
    }
    $taskStart.Environment['JAVA_HOME']=$taskJavaHome
    $taskStart.Environment['PATH']="$taskJavaHome\bin;$env:SystemRoot\System32;$env:SystemRoot"
    if($Database) {
        foreach($taskName in @('FINAL_DB_USERNAME','FINAL_DB_PASSWORD')) {
            $taskValue=[Environment]::GetEnvironmentVariable($taskName,'Process')
            if(-not $taskValue){$taskValue=[Environment]::GetEnvironmentVariable($taskName,'User')}
            if(-not $taskValue){$taskValue=[Environment]::GetEnvironmentVariable($taskName.Replace('FINAL_',''),'User')}
            if(-not $taskValue){throw "Existing database credential missing: $taskName"}
            $taskStart.Environment[$taskName]=$taskValue
        }
    }
    # No model keys, RUN_REAL switches or inherited Java options are passed, even in live read-only mode.
    foreach($taskArgument in $ChildArguments){$taskStart.ArgumentList.Add($taskArgument)}
    $taskProcess=[Diagnostics.Process]::Start($taskStart)
    $taskStdout=$taskProcess.StandardOutput.ReadToEndAsync();$taskStderr=$taskProcess.StandardError.ReadToEndAsync()
    $taskProcess.WaitForExit()
    foreach($taskText in @($taskStdout.Result,$taskStderr.Result)) {
        if($taskText){Add-Content -LiteralPath (Join-Path $taskOutput 'console.log') -Value $taskText -Encoding utf8;Write-Host $taskText}
    }
    if($taskProcess.ExitCode -ne 0){throw "Read-only repair child failed: $($taskProcess.ExitCode). No business resume or retry."}
}
try {
    Assert-PreservedAttempts
    @{mode=$Mode;status='IN_PROGRESS';newModelCalls=0;databaseWrites=0} | ConvertTo-Json |
        Set-Content -LiteralPath (Join-Path $taskOutput 'verification-status.json') -Encoding utf8
    $taskCp=(Join-Path $taskRoot 'target/classes')+';'+(Get-Content -LiteralPath (Join-Path $taskRoot 'target/final-e2e/repair-20261013/classpath.txt') -Raw).Trim()
    if($taskCp -match 'target[/\\]test-classes'){throw 'Test runtime forbidden'}
    $taskSources=@(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'tools/rag-e2e') -Filter '*.java' | ForEach-Object FullName)
    $taskSources+=@(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | ForEach-Object FullName)
    Invoke-ReadOnlyChild -Executable (Join-Path $taskJavaHome 'bin/javac.exe') -ChildArguments (@('-proc:none','-encoding','UTF-8','-cp',$taskCp,'-d',(Join-Path $taskOutput 'classes'))+$taskSources)
    $taskArgs=@('-Djdk.httpclient.disableRetryConnect=true','-Djdk.httpclient.enableAllMethodRetry=false','-cp',((Join-Path $taskOutput 'classes')+';'+$taskCp))
    Invoke-ReadOnlyChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments ($taskArgs+@('FinalE2eRepairOfflineTest',$taskOutput))
    Invoke-ReadOnlyChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments ($taskArgs+@('FinalE2eSecondRepairOfflineTest',$taskOutput))
    if($Mode -eq 'VerifyReadOnly') {
        Invoke-ReadOnlyChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments ($taskArgs+@('FinalE2eSecondRepair','inspect',$taskOutput)) -Database
    } else {
        Invoke-ReadOnlyChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments ($taskArgs+@('FinalE2eSecondRepair','offline',$taskOutput))
    }
    @{mode=$Mode;status='PASS';newModelCalls=0;databaseWrites=0;nextRealRunReady='NO';reason='NOT_NEEDED_ALL_BUSINESS_COMPLETE'} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskOutput 'verification-status.json') -Encoding utf8
} catch {
    @{mode=$Mode;status='BLOCKED';newModelCalls=0;databaseWrites=0;reason='See console.log; no business resume was attempted'} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskOutput 'verification-status.json') -Encoding utf8
    throw
} finally {Assert-PreservedAttempts}
