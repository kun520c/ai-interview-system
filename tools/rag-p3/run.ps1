[CmdletBinding()]
param(
    [ValidateSet('Offline', 'Verify', 'ExecuteOnce', 'Analyze')][string]$Mode = 'Offline',
    [string]$Labels = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'PowerShell 7 required.' }
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskPython = (Get-Command python -CommandType Application | Select-Object -First 1).Source
$taskJavaRoot = 'C:\Program Files\Java\jdk-21.0.11'
$taskOffline = Join-Path $taskRoot 'target/rag-p3/offline'
$taskScript = Join-Path $PSScriptRoot 'p3.py'
$taskSecrets = [Collections.Generic.List[string]]::new()
$taskSecureValues = [Collections.Generic.List[Security.SecureString]]::new()
$taskPointers = [Collections.Generic.List[IntPtr]]::new()
$taskEnvelope = $null

function Invoke-P3Child {
    param([string]$Executable, [string[]]$ChildArguments, [string]$PrivateInput = '')
    $taskStart = [Diagnostics.ProcessStartInfo]::new($Executable)
    $taskStart.WorkingDirectory = $taskRoot
    $taskStart.UseShellExecute = $false
    $taskStart.CreateNoWindow = $true
    $taskStart.RedirectStandardInput = $true
    $taskStart.RedirectStandardOutput = $true
    $taskStart.RedirectStandardError = $true
    $taskStart.StandardInputEncoding = [Text.UTF8Encoding]::new($false)
    $taskStart.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
    $taskStart.StandardErrorEncoding = [Text.UTF8Encoding]::new($false)
    $taskStart.Environment.Clear()
    foreach ($taskName in @('SystemRoot','WINDIR','COMSPEC','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA')) {
        $taskValue = [Environment]::GetEnvironmentVariable($taskName, 'Process')
        if ($taskValue) { $taskStart.Environment[$taskName] = $taskValue }
    }
    foreach ($taskArgument in $ChildArguments) { $taskStart.ArgumentList.Add($taskArgument) }
    $taskProcess = $null
    try {
        $taskProcess = [Diagnostics.Process]::Start($taskStart)
        $taskOut = $taskProcess.StandardOutput.ReadToEndAsync()
        $taskErr = $taskProcess.StandardError.ReadToEndAsync()
        if ($PrivateInput) { $taskProcess.StandardInput.Write($PrivateInput) }
        $taskProcess.StandardInput.Close()
        $taskProcess.WaitForExit()
        foreach ($taskText in @($taskOut.Result, $taskErr.Result)) {
            foreach ($taskSecret in $taskSecrets) { if ($taskSecret) { $taskText = $taskText.Replace($taskSecret, '[REDACTED]') } }
            if ($taskText) { Write-Host $taskText.TrimEnd() }
        }
        if ($taskProcess.ExitCode -ne 0) { throw "P3 child stopped (exit $($taskProcess.ExitCode)); preserve locks and results. No automatic retry." }
    } finally {
        $taskStart.Environment.Clear()
        $PrivateInput = $null
        if ($taskProcess) { $taskProcess.Dispose() }
    }
}

function Invoke-P3Python {
    param([string[]]$PythonArguments, [string]$PrivateInput = '')
    Invoke-P3Child -Executable $taskPython -ChildArguments (@('-I','-B','-X','utf8') + $PythonArguments) -PrivateInput $PrivateInput
}

function Get-P3Value {
    param([string[]]$Names)
    foreach ($taskName in $Names) {
        $taskValue = [Environment]::GetEnvironmentVariable($taskName, 'Process')
        if (-not $taskValue) { $taskValue = [Environment]::GetEnvironmentVariable($taskName, 'User') }
        if ($taskValue) { return $taskValue }
    }
    return ''
}

function Read-P3Secret {
    param([string]$Prompt)
    $taskSecure = Read-Host $Prompt -AsSecureString
    $taskSecureValues.Add($taskSecure)
    if ($taskSecure.Length -eq 0) { throw 'Empty credential; execution not reserved.' }
    $taskPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecure)
    $taskPointers.Add($taskPointer)
    return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPointer)
}

try {
    if ($Mode -eq 'Offline') {
        New-Item -ItemType Directory -Force -Path $taskOffline | Out-Null
        Invoke-P3Python -PythonArguments @($taskScript,'prepare')
        $taskClasses = Join-Path $taskOffline 'classes'
        New-Item -ItemType Directory -Force -Path $taskClasses | Out-Null
        $taskCpFile = Join-Path $taskRoot 'target/rag-e2e/runtime-classpath.txt'
        if (-not (Test-Path -LiteralPath $taskCpFile)) { throw 'Existing cached runtime classpath missing; no dependency download.' }
        $taskCp = (Join-Path $taskRoot 'target/classes') + ';' + (Get-Content -LiteralPath $taskCpFile -Raw).Trim()
        foreach ($taskEntry in $taskCp.Split(';')) {
            if (-not (Test-Path -LiteralPath $taskEntry)) { throw 'Cached dependency missing.' }
        }
        $taskSources = @(
            'tools/rag-e2e/RagE2ePolicy.java',
            'tools/rag-p1/IndependentReadOnlySql.java',
            'tools/rag-p1/IndependentRetrieval.java',
            'tools/rag-p1/IndependentRetrievalOfflineTest.java',
            'src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationRagQueryBuilder.java',
            'src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationContext.java',
            'src/main/java/com/kun/aiinterview/interview/evaluation/ScoringPointSnapshot.java',
            'src/main/java/com/kun/aiinterview/interview/evaluation/EvaluationMode.java',
            'src/main/java/com/kun/aiinterview/question/enums/QuestionCategory.java',
            'src/main/java/com/kun/aiinterview/question/enums/QuestionPointType.java',
            'tools/rag-p3/P3ReadOnlyRetrieval.java',
            'tools/rag-p3/P3ReadOnlyRetrievalOfflineTest.java'
        )
        Invoke-P3Child -Executable (Join-Path $taskJavaRoot 'bin/javac.exe') -ChildArguments (
            @('-J-Duser.language=en','-proc:none','-encoding','UTF-8','-cp',$taskCp,'-d',$taskClasses) + $taskSources)
        Invoke-P3Child -Executable (Join-Path $taskJavaRoot 'bin/java.exe') -ChildArguments @(
            '-Djdk.httpclient.disableRetryConnect=true','-Djdk.httpclient.enableAllMethodRetry=false',
            '-cp',($taskClasses + ';' + $taskCp),'P3ReadOnlyRetrievalOfflineTest',$taskOffline)
        Invoke-P3Python -PythonArguments @((Join-Path $PSScriptRoot 'test_p3.py'))
        & (Join-Path $PSScriptRoot 'test-entry.ps1')
        Invoke-P3Python -PythonArguments @($taskScript,'ready')
        Write-Host "Report: $taskRoot/target/rag-p3/prepared/report.md"
        Write-Host "Visual: $taskRoot/target/rag-p3/prepared/review.html"
        return
    }
    if ($Mode -eq 'Analyze') {
        if (-not $Labels) { throw 'Analyze requires -Labels with the separate human/AI annotation JSON.' }
        Invoke-P3Python -PythonArguments @($taskScript,'analyze','--labels',$Labels)
        return
    }
    Invoke-P3Python -PythonArguments @($taskScript,'verify')
    if ($Mode -eq 'Verify') { return }
    if ([Console]::IsInputRedirected -or -not [Environment]::UserInteractive) {
        throw 'ExecuteOnce requires a local interactive PowerShell console; redirected/noninteractive consent is rejected.'
    }
    if (Test-Path -LiteralPath (Join-Path $taskRoot 'target/rag-p3/run-once')) {
        throw 'P3 run already reserved. Preserve its lock; no rerun, deletion or automatic resume.'
    }
    Invoke-P3Python -PythonArguments @($taskScript,'ready')
    $taskWorkspace = Get-P3Value -Names @('P3_RERANK_WORKSPACE_ID','P2_RERANK_WORKSPACE_ID','DASHSCOPE_WORKSPACE_ID')
    if (-not $taskWorkspace) {
        # Reuse the verified P2 workspace identity, not an Embedding address.
        $taskPrevious = Get-Content -LiteralPath (Join-Path $taskRoot 'target/rag-p2/run-once/results.json') -Raw | ConvertFrom-Json
        $taskWorkspace = ([uri]$taskPrevious.endpoint).Host.Split('.')[0]
    }
    if ($taskWorkspace -cnotmatch '^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$') { throw 'Invalid Beijing Workspace ID.' }
    $taskRerankKey = Get-P3Value -Names @('P3_RERANK_API_KEY','P2_RERANK_API_KEY','DASHSCOPE_API_KEY','EMBEDDING_API_KEY')
    $taskEmbeddingKey = Get-P3Value -Names @('P3_EMBEDDING_API_KEY','EMBEDDING_API_KEY','DASHSCOPE_API_KEY')
    if (-not $taskRerankKey) { $taskRerankKey = Read-P3Secret -Prompt 'Bailian Rerank API Key (hidden)' }
    if (-not $taskEmbeddingKey) { $taskEmbeddingKey = Read-P3Secret -Prompt 'Bailian Beijing Embedding API Key (hidden)' }
    $taskPassword = Read-P3Secret -Prompt 'Existing rag_e2e_writer@127.0.0.1 password (hidden; no root fallback)'
    $taskToken = [Environment]::GetEnvironmentVariable('E2E_MILVUS_TOKEN','Process')
    foreach ($taskSecret in @($taskRerankKey,$taskEmbeddingKey,$taskPassword,$taskToken)) {
        if ($taskSecret) { $taskSecrets.Add($taskSecret) }
    }
    $taskManifest = Get-Content -LiteralPath (Join-Path $taskRoot 'target/rag-p3/prepared/manifest.json') -Raw | ConvertFrom-Json
    Write-Host 'P3: 12 NEW frozen queries; max 12 Embedding + 12 Milvus Search + 12 Rerank; DeepSeek/Jev=0; retries=0.'
    Write-Host 'Rule locked: exact Rerank Top2, score >= 0.50, output 0-2; no Rank3 backfill; no sufficiency inference.'
    Write-Host 'MySQL ai_interview_test (191 rows), Milvus default/knowledge_chunk_vectors_test (52 vectors); READ ONLY and before/after SHA gates.'
    Write-Host "Rerank: https://$taskWorkspace.cn-beijing.maas.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank"
    Write-Host "Combined conservative cost proxy CNY $($taskManifest.budget.conservativeCombinedCostProxyCny); budget reference CNY 0.50; actual usage/invoice may differ."
    Write-Host "Review frozen questions and coverage audit: $taskRoot/tools/rag-p3/queries-v1.json and coverage-review-v1.json"
    $taskConsent = Read-Host 'After reviewing the 6 covered/6 hard-negative audit and budget, type exactly: AUTHORIZE P3 12 EACH ONCE'
    if ($taskConsent -cne 'AUTHORIZE P3 12 EACH ONCE') { throw 'No authorization; no HTTP or resource connection; no execution reserved.' }
    $taskEnvelope = @{
        authorization = $taskConsent
        freezeSha256 = $taskManifest.freezeSha256
        workspaceId = $taskWorkspace
        apiKey = $taskRerankKey
        embeddingKey = $taskEmbeddingKey
        dbPassword = $taskPassword
        milvusToken = $(if ($taskToken) { $taskToken } else { '' })
        coverageAuditAcknowledgedBeforeRetrieval = $true
    } | ConvertTo-Json -Compress
    Invoke-P3Python -PythonArguments @($taskScript,'execute-once') -PrivateInput $taskEnvelope
    Write-Host "Results: $taskRoot/target/rag-p3/run-once/results.json; quality awaits independent human labels."
} finally {
    foreach ($taskPointer in $taskPointers) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPointer) }
    foreach ($taskSecure in $taskSecureValues) { $taskSecure.Dispose() }
    $taskSecrets.Clear()
    $taskEnvelope = $null
    $taskRerankKey = $null
    $taskEmbeddingKey = $null
    $taskPassword = $null
    $taskToken = $null
}
