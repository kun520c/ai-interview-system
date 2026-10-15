param(
    [ValidateSet('Prepare','ExecuteResumeOnce')][string]$Mode='Prepare',
    [string]$Authorization=''
)
$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskRoot
$taskJavaHome='C:\Program Files\Java\jdk-21.0.11'
$taskPreparation=Join-Path $taskRoot 'target/final-e2e/repair-20261013'
$taskAttempt=Join-Path $taskRoot 'target/final-e2e/attempts/final-e2e-20261013-002'
if($Mode -eq 'ExecuteResumeOnce') {
    if($Authorization -cne 'AUTHORIZE_RESUME_150_ROTATE_TEST_PASSWORD_ONCE_MAX_3_CNY') {throw 'New explicit authorization must cover only test user 427 credential rotation and at most 3 CNY additional model calls'}
    if(Test-Path -LiteralPath $taskAttempt) {throw 'Continuation already reserved; preserve evidence and locks, no automatic retry'}
    $taskReady=Get-Content -LiteralPath (Join-Path $taskPreparation 'resume-ready.json') -Raw | ConvertFrom-Json
    if($taskReady.offlineRepairStatus -cne 'PASS' -or $taskReady.nextRealRunReady -cne 'YES') {throw 'Offline repair and SELECT-only admission must pass first'}
    $taskSeal=Get-Content -LiteralPath (Join-Path $taskPreparation 'resume-seal.json') -Raw | ConvertFrom-Json
    foreach($taskEntry in $taskSeal.PSObject.Properties) {
        if((Get-FileHash -LiteralPath $taskEntry.Name -Algorithm SHA256).Hash.ToLowerInvariant() -cne $taskEntry.Value) {throw 'Reviewed source, test evidence or original attempt changed'}
    }
}
New-Item -ItemType Directory -Force -Path (Join-Path $taskPreparation 'classes') | Out-Null
if($Mode -eq 'Prepare') {
    @{offlineRepairStatus='IN_PROGRESS';nextRealRunReady='NO';newModelCalls=0;databaseWrites=0} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskPreparation 'resume-ready.json') -Encoding utf8
}
$taskOriginal=Get-Content -LiteralPath (Join-Path $taskPreparation 'original-artifacts.sha256.json') -Raw | ConvertFrom-Json
function Assert-PreservedArtifacts {
    foreach($taskEntry in $taskOriginal.PSObject.Properties) {
        if((Get-FileHash -LiteralPath $taskEntry.Name -Algorithm SHA256).Hash.ToLowerInvariant() -cne $taskEntry.Value) {throw 'Original failed attempt evidence changed'}
    }
}
function Invoke-ResumeChild {
    param([string]$Executable,[string[]]$ChildArguments,[switch]$Database,[switch]$Models)
    $taskStart=[Diagnostics.ProcessStartInfo]::new($Executable)
    $taskStart.WorkingDirectory=$taskRoot;$taskStart.UseShellExecute=$false;$taskStart.CreateNoWindow=$true
    $taskStart.RedirectStandardOutput=$true;$taskStart.RedirectStandardError=$true;$taskStart.Environment.Clear()
    foreach($taskName in @('SystemRoot','WINDIR','COMSPEC','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA','PATHEXT')) {
        $taskValue=[Environment]::GetEnvironmentVariable($taskName,'Process');if($taskValue){$taskStart.Environment[$taskName]=$taskValue}
    }
    $taskStart.Environment['JAVA_HOME']=$taskJavaHome
    $taskStart.Environment['PATH']="$taskJavaHome\bin;$env:SystemRoot\System32;$env:SystemRoot;$env:SystemRoot\System32\WindowsPowerShell\v1.0"
    if($Database) {
        foreach($taskName in @('FINAL_DB_USERNAME','FINAL_DB_PASSWORD')) {
            $taskValue=[Environment]::GetEnvironmentVariable($taskName,'Process')
            if(-not $taskValue){$taskValue=[Environment]::GetEnvironmentVariable($taskName,'User')}
            if(-not $taskValue){$taskValue=[Environment]::GetEnvironmentVariable($taskName.Replace('FINAL_',''),'User')}
            if(-not $taskValue){throw "Existing credential missing: $taskName"}
            $taskStart.Environment[$taskName]=$taskValue
        }
    }
    if($Models) {
        foreach($taskName in @('EMBEDDING_API_KEY','DEEPSEEK_API_KEY')) {
            $taskValue=[Environment]::GetEnvironmentVariable($taskName,'Process')
            if(-not $taskValue){$taskValue=[Environment]::GetEnvironmentVariable($taskName,'User')}
            if(-not $taskValue){throw "Existing provider credential missing: $taskName"}
            $taskStart.Environment[$taskName]=$taskValue
        }
    }
    foreach($taskArgument in $ChildArguments){$taskStart.ArgumentList.Add($taskArgument)}
    $taskProcess=[Diagnostics.Process]::Start($taskStart)
    $taskStdout=$taskProcess.StandardOutput.ReadToEndAsync();$taskStderr=$taskProcess.StandardError.ReadToEndAsync()
    $taskProcess.WaitForExit()
    # Preserve console diagnostics outside the original attempt, including future execution failures.
    $taskLog=if($Mode -eq 'ExecuteResumeOnce' -and (Test-Path -LiteralPath $taskAttempt)){Join-Path $taskAttempt 'console.log'}else{Join-Path $taskPreparation 'repair-console.log'}
    if($taskStdout.Result){Add-Content -LiteralPath $taskLog -Value $taskStdout.Result -Encoding utf8;Write-Host $taskStdout.Result}
    if($taskStderr.Result){Add-Content -LiteralPath $taskLog -Value $taskStderr.Result -Encoding utf8;Write-Host $taskStderr.Result}
    if($taskProcess.ExitCode -ne 0){throw "Resume preparation/execution child failed: $($taskProcess.ExitCode). No automatic retry."}
}
try {
    Assert-PreservedArtifacts
    Invoke-ResumeChild -Executable $env:COMSPEC -ChildArguments @('/d','/c','mvnw.cmd -o -B -ntp -DskipTests compile dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile=target/final-e2e/repair-20261013/classpath.txt')
    $taskCp=(Join-Path $taskRoot 'target/classes')+';'+(Get-Content (Join-Path $taskPreparation 'classpath.txt') -Raw).Trim()
    if($taskCp -match 'target[/\\]test-classes'){throw 'Test runtime forbidden for continuation'}
    $taskSources=@(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'tools/rag-e2e') -Filter '*.java' | ForEach-Object FullName)
    $taskSources+=@(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | ForEach-Object FullName)
    Invoke-ResumeChild -Executable (Join-Path $taskJavaHome 'bin/javac.exe') -ChildArguments (@('-proc:none','-encoding','UTF-8','-cp',$taskCp,'-d',(Join-Path $taskPreparation 'classes'))+$taskSources)
    $taskArgs=@('-Djdk.httpclient.disableRetryConnect=true','-Djdk.httpclient.enableAllMethodRetry=false','-cp',((Join-Path $taskPreparation 'classes')+';'+$taskCp))
    if($Mode -eq 'ExecuteResumeOnce') {
        Invoke-ResumeChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments ($taskArgs+@('FinalE2eResume',$Authorization)) -Database -Models
    } else {
        Invoke-ResumeChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments ($taskArgs+@('FinalE2eRepairOfflineTest',$taskPreparation))
        Invoke-ResumeChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments ($taskArgs+@('FinalE2eRepair','inspect',$taskPreparation)) -Database
        # Ready seal is produced only after the focused business regression has also passed.
        [xml]$taskSuite=Get-Content -LiteralPath (Join-Path $taskRoot 'target/surefire-reports/TEST-com.kun.aiinterview.interview.service.FullInterviewBusinessOfflineTest.xml')
        if([int]$taskSuite.testsuite.errors -ne 0 -or [int]$taskSuite.testsuite.failures -ne 0 -or [int]$taskSuite.testsuite.skipped -ne 0){throw 'Focused offline business regression failed'}
        if(-not (Select-String -LiteralPath (Join-Path $taskPreparation 'maven-focused.log') -Pattern 'BUILD SUCCESS' -Quiet)) {throw 'This repair must have its own successful focused Maven run'}
        @{offlineRepairStatus='PASS';nextRealRunReady='YES';requiresNewAuthorization=$true;
            runId='final-e2e-20261013-002';resumeSessionId=150;resumeUserId=427;newModelCalls=0;databaseWrites=0;
            additionalBudgetCny=3;maximumNewRequests=@{embedding=9;jev=0;deepseek=10};
            separatelyAuthorizedAction='Rotate only synthetic test user 427 password, then normal login; retain all original business results'} |
            ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskPreparation 'resume-ready.json') -Encoding utf8
        $taskHashes=[ordered]@{}
        $taskFiles=@(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'src/main') -File -Recurse | Where-Object Name -ne 'application-local.yaml')
        $taskFiles+=@(Get-ChildItem -LiteralPath $PSScriptRoot -File -Recurse)
        $taskFiles+=@(Get-ChildItem -LiteralPath (Join-Path $taskRoot 'target/final-e2e/attempts/final-e2e-20261013-001') -File -Recurse)
        $taskFiles+=@(Get-Item -LiteralPath (Join-Path $taskPreparation 'diagnosis.json'),(Join-Path $taskPreparation 'repair-offline-tests.json'),
            (Join-Path $taskPreparation 'resume-ready.json'),(Join-Path $taskPreparation 'maven-focused.log'),
            (Join-Path $taskPreparation 'original-artifacts.sha256.json'),(Join-Path $taskRoot 'src/test/java/com/kun/aiinterview/interview/service/FullInterviewBusinessOfflineTest.java'))
        foreach($taskFile in $taskFiles){$taskHashes[[IO.Path]::GetRelativePath($taskRoot,$taskFile.FullName).Replace('\','/')]=(Get-FileHash -LiteralPath $taskFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
        $taskHashes | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $taskPreparation 'resume-seal.json') -Encoding utf8
    }
} catch {
    if($Mode -eq 'Prepare') {
        @{offlineRepairStatus='BLOCKED';nextRealRunReady='NO';newModelCalls=0;databaseWrites=0;reason='See repair-console.log; no real attempt started'} |
            ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskPreparation 'resume-ready.json') -Encoding utf8
    }
    throw
} finally {Assert-PreservedArtifacts}
