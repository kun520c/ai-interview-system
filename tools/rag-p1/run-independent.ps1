param([ValidateSet('Offline','ExecuteOnce')][string]$Mode='Offline')
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw 'PowerShell 7 is required'}
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskJavaHome='C:\Program Files\Java\jdk-21.0.11'
$taskBuild=Join-Path $taskRoot 'target/rag-p1-independent/offline'
$taskOutput=Join-Path $taskRoot 'target/rag-p1-independent/run-once'
$taskKey=$null
$taskPassword=$null
$taskSecure=$null
$taskPointer=[IntPtr]::Zero
$taskSecrets=@()

function Invoke-IndependentChild {
    param([string]$Executable,[string[]]$ChildArguments,[switch]$Online)
    $taskStart=[Diagnostics.ProcessStartInfo]::new($Executable)
    $taskStart.WorkingDirectory=$taskRoot
    $taskStart.UseShellExecute=$false
    $taskStart.CreateNoWindow=$true
    $taskStart.RedirectStandardOutput=$true
    $taskStart.RedirectStandardError=$true
    # Reuse rag-e2e's environment allowlist. No DB/root fallback, Spring, Judge, proxy or Java option overrides.
    $taskStart.Environment.Clear()
    foreach($taskName in @('SystemRoot','WINDIR','COMSPEC','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA')) {
        $taskValue=[Environment]::GetEnvironmentVariable($taskName,'Process')
        if($taskValue){$taskStart.Environment[$taskName]=$taskValue}
    }
    if($Online) {
        $taskStart.Environment['P1_DB_PASSWORD']=$taskPassword
        $taskStart.Environment['EMBEDDING_API_KEY']=$taskKey
        $taskStart.Environment['EMBEDDING_BASE_URL']=$taskBase
        if($taskToken){$taskStart.Environment['E2E_MILVUS_TOKEN']=$taskToken}
    }
    foreach($taskArgument in $ChildArguments){$taskStart.ArgumentList.Add($taskArgument)}
    try {
        $taskProcess=[Diagnostics.Process]::Start($taskStart)
        $taskStdout=$taskProcess.StandardOutput.ReadToEndAsync()
        $taskStderr=$taskProcess.StandardError.ReadToEndAsync()
        $taskProcess.WaitForExit()
        foreach($taskText in @($taskStdout.Result,$taskStderr.Result)) {
            foreach($taskSecret in $taskSecrets){if($taskSecret){$taskText=$taskText.Replace($taskSecret,'[REDACTED]')}}
            if($taskText){Write-Host $taskText}
        }
        if($taskProcess.ExitCode -ne 0){throw "Child stopped (exit $($taskProcess.ExitCode)); no automatic retry. Inspect results.json if reserved."}
    } finally {
        $taskStart.Environment.Clear()
        if($taskProcess){$taskProcess.Dispose()}
    }
}

Push-Location -LiteralPath $taskRoot
try {
    $taskExpected='4f42970bbdb3682c475056e512ff725832329d213b8b49e3a27ac45b5afbd456'
    if((Get-FileHash -LiteralPath 'src/test/resources/rag-p1-independent/queries-v1.json' -Algorithm SHA256).Hash.ToLowerInvariant() -cne $taskExpected){throw 'Frozen query SHA mismatch'}
    if($Mode -eq 'ExecuteOnce' -and (Test-Path -LiteralPath $taskOutput)){throw 'This independent run is already reserved. Preserve it; do not delete the lock or rerun paid queries.'}
    if(-not(Test-Path -LiteralPath (Join-Path $taskJavaHome 'bin/javac.exe'))){throw 'Cached Java 21 compiler missing'}
    $taskCpFile=Join-Path $taskRoot 'target/rag-e2e/runtime-classpath.txt'
    if(-not(Test-Path -LiteralPath $taskCpFile)){throw 'Existing cached rag-e2e runtime classpath missing; no dependencies downloaded'}
    $taskCp=(Join-Path $taskRoot 'target/classes')+';'+(Get-Content -LiteralPath $taskCpFile -Raw).Trim()
    foreach($taskEntry in $taskCp.Split(';')) {if(-not(Test-Path -LiteralPath $taskEntry)){throw 'Cached runtime dependency missing'}}
    New-Item -ItemType Directory -Force -Path $taskBuild | Out-Null
    $taskSources=@('tools/rag-e2e/RagE2ePolicy.java','tools/rag-p1/IndependentReadOnlySql.java',
        'tools/rag-p1/IndependentRetrieval.java','tools/rag-p1/IndependentRetrievalOfflineTest.java',
        'src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRagQueryBuilder.java',
        'src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationContext.java',
        'src/main/java/com/kun/aiinterview/interview/evaluation/ScoringPointSnapshot.java',
        'src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationMode.java',
        'src/main/java/com/kun/aiinterview/question/enums/QuestionCategory.java',
        'src/main/java/com/kun/aiinterview/question/enums/QuestionPointType.java')
    Invoke-IndependentChild -Executable (Join-Path $taskJavaHome 'bin/javac.exe') -ChildArguments (@('-J-Duser.language=en','-proc:none','-encoding','UTF-8','-cp',$taskCp,'-d',$taskBuild)+$taskSources)
    $taskJavaArgs=@('-Djdk.httpclient.disableRetryConnect=true','-Djdk.httpclient.enableAllMethodRetry=false','-cp',($taskBuild+';'+$taskCp))
    Invoke-IndependentChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments ($taskJavaArgs+@('IndependentRetrievalOfflineTest',$taskBuild))
    if($Mode -eq 'Offline'){return}

    # API key uses the existing rag-e2e Process/User lookup; no secrets copied into persistent variables or files.
    $taskKey=[Environment]::GetEnvironmentVariable('EMBEDDING_API_KEY','Process')
    if(-not $taskKey){$taskKey=[Environment]::GetEnvironmentVariable('EMBEDDING_API_KEY','User')}
    if(-not $taskKey){throw 'Existing EMBEDDING_API_KEY missing; execution not reserved'}
    $taskBase=[Environment]::GetEnvironmentVariable('EMBEDDING_BASE_URL','Process')
    if(-not $taskBase){$taskBase=[Environment]::GetEnvironmentVariable('EMBEDDING_BASE_URL','User')}
    if(-not $taskBase){$taskBase='https://dashscope.aliyuncs.com/compatible-mode/v1'}
    if($taskBase -cnotin @('https://dashscope.aliyuncs.com/compatible-mode/v1','https://dashscope-intl.aliyuncs.com/compatible-mode/v1')){throw 'Embedding endpoint outside fixed official allowlist'}
    $taskToken=[Environment]::GetEnvironmentVariable('E2E_MILVUS_TOKEN','Process')
    Write-Host 'One independent run: SELECT-only path + verified READ ONLY transaction; at most 16 Embedding POSTs and 16 Milvus Searches; no retry.'
    $taskSecure=Read-Host 'Existing rag_e2e_writer@127.0.0.1 password (hidden)' -AsSecureString
    if($taskSecure.Length -eq 0){throw 'Empty password; execution not reserved'}
    $taskPointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecure)
    $taskPassword=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPointer)
    $taskSecrets=@($taskPassword,$taskKey,$taskToken)
    Invoke-IndependentChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments ($taskJavaArgs+@('IndependentRetrieval','execute-once')) -Online
    Write-Host "Results: $taskOutput/results.json"
    Write-Host "Open in browser: $taskOutput/annotation.html"
} finally {
    if($taskPointer -ne [IntPtr]::Zero){[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPointer)}
    if($taskSecure){$taskSecure.Dispose()}
    $taskPassword=$null; $taskKey=$null; $taskToken=$null; $taskSecrets=@()
    Pop-Location
}
