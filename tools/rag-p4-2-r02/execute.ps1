[CmdletBinding()]
param(
    [ValidateSet('Verify','Preflight','ExecuteOnce','Analyze')][string]$Mode = 'Verify',
    [string]$Labels = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'PowerShell 7 required.' }
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskPython = (Get-Command python -CommandType Application | Select-Object -First 1).Source
$taskJava = 'C:\Program Files\Java\jdk-21.0.11\bin'
$taskPrep = Join-Path $taskRoot 'target/rag-p4-2/execution-r02-20261014'
$taskClasses = Join-Path $taskPrep 'classes'
$taskSecrets = [Collections.Generic.List[string]]::new()
$taskCp = $taskClasses + ';' + (Join-Path $taskRoot 'target/rag-p4-2/execution-prep-20261014/classes') + ';' + (Join-Path $taskRoot 'target/rag-p4-2/offline/classes') + ';' + (Join-Path $taskRoot 'target/rag-p3/offline/classes') + ';' + (Join-Path $taskRoot 'target/classes') + ';' + (Get-Content -LiteralPath (Join-Path $taskRoot 'target/rag-e2e/runtime-classpath.txt') -Raw).Trim()

function Invoke-P42Child {
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
    foreach ($taskArg in $ChildArguments) { $taskStart.ArgumentList.Add($taskArg) }
    $taskProcess = [Diagnostics.Process]::Start($taskStart)
    try {
        $taskOut = $taskProcess.StandardOutput.ReadToEndAsync()
        $taskErr = $taskProcess.StandardError.ReadToEndAsync()
        if ($PrivateInput) { $taskProcess.StandardInput.Write($PrivateInput) }
        $taskProcess.StandardInput.Close()
        $taskProcess.WaitForExit()
        foreach ($taskText in @($taskOut.Result, $taskErr.Result)) {
            foreach ($taskSecret in $taskSecrets) { if ($taskSecret) { $taskText = $taskText.Replace($taskSecret, '[REDACTED]') } }
            if ($taskText) { Write-Host $taskText.TrimEnd() }
        }
        if ($taskProcess.ExitCode -ne 0) { throw "P4.2 child stopped: exit=$($taskProcess.ExitCode). Preserve locks and evidence; no retries or resume." }
    } finally { $taskProcess.Dispose(); $taskStart.Environment.Clear(); $PrivateInput = $null }
}
function Invoke-P42Analysis {
    param([string[]]$Arguments)
    Invoke-P42Child -Executable $taskPython -ChildArguments (@('-I','-B','-X','utf8',(Join-Path $PSScriptRoot 'execution/analyze.py')) + $Arguments)
}
function Invoke-P42Java {
    param([string]$Action, [string]$PrivateInput = '')
    Invoke-P42Child -Executable (Join-Path $taskJava 'java.exe') -ChildArguments @('-Djdk.httpclient.disableRetryConnect=true','-Djdk.httpclient.enableAllMethodRetry=false','-Dfile.encoding=UTF-8','-cp',$taskCp,'P42R02ExecuteOnce',$Action) -PrivateInput $PrivateInput
}
function Get-P42Value {
    param([string[]]$Names)
    foreach ($taskName in $Names) {
        $taskValue = [Environment]::GetEnvironmentVariable($taskName, 'Process')
        if (-not $taskValue) { $taskValue = [Environment]::GetEnvironmentVariable($taskName, 'User') }
        if ($taskValue) { return $taskValue }
    }
    return ''
}
function Read-P42Secret {
    param([string]$Prompt)
    $taskSecure = Read-Host $Prompt -AsSecureString
    $taskPointer = [IntPtr]::Zero
    try {
        if ($taskSecure.Length -eq 0) { throw 'Empty credential; no execution reserved.' }
        $taskPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecure)
        $taskPlain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPointer)
        $taskSecrets.Add($taskPlain)
        return $taskPlain
    } finally {
        if ($taskPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPointer) }
        $taskSecure.Dispose()
    }
}

Invoke-P42Analysis -Arguments @('Verify')
Invoke-P42Java -Action verify
if ($Mode -eq 'Verify') { return }
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
}
if ($Mode -eq 'Analyze') {
    Invoke-P42Java -Action validate-run
    $taskArgs = @('Analyze')
    if ($Labels) { $taskArgs += @('--labels',$Labels) }
    Invoke-P42Analysis -Arguments $taskArgs
    return
}
if ([Console]::IsInputRedirected -or -not [Environment]::UserInteractive) { throw 'ExecuteOnce requires an interactive local PowerShell console; redirected consent is rejected.' }
if (Test-Path -LiteralPath (Join-Path $taskRoot 'target/rag-p4-2/run-r02-20261014')) { throw 'P4.2 execution already reserved. Do not delete locks or rerun historical requests.' }
$taskWorkspace = Get-P42Value -Names @('P42_RERANK_WORKSPACE_ID','P3_RERANK_WORKSPACE_ID','P2_RERANK_WORKSPACE_ID','DASHSCOPE_WORKSPACE_ID')
if (-not $taskWorkspace) {
    $taskOld = Get-Content -LiteralPath (Join-Path $taskRoot 'target/rag-p2/run-once/results.json') -Raw | ConvertFrom-Json
    $taskWorkspace = ([uri]$taskOld.endpoint).Host.Split('.')[0]
}
if ($taskWorkspace -cnotmatch '^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$') { throw 'Invalid Beijing workspace.' }
$taskEmbeddingKey = Get-P42Value -Names @('P42_EMBEDDING_API_KEY','EMBEDDING_API_KEY','DASHSCOPE_API_KEY')
$taskRerankKey = Get-P42Value -Names @('P42_RERANK_API_KEY','P3_RERANK_API_KEY','P2_RERANK_API_KEY','DASHSCOPE_API_KEY','EMBEDDING_API_KEY')
$taskDeepseekKey = Get-P42Value -Names @('P42_DEEPSEEK_API_KEY','DEEPSEEK_API_KEY')
if (-not $taskEmbeddingKey) { $taskEmbeddingKey = Read-P42Secret -Prompt 'Bailian Embedding key (hidden)' }
if (-not $taskRerankKey) { $taskRerankKey = Read-P42Secret -Prompt 'Bailian Rerank key (hidden)' }
if (-not $taskDeepseekKey) { $taskDeepseekKey = Read-P42Secret -Prompt 'DeepSeek official Flash key (hidden)' }
$taskPassword = Get-P42Value -Names @('P42_READONLY_DB_PASSWORD','E2E_DB_PASSWORD','P1_DB_PASSWORD')
if (-not $taskPassword) { $taskPassword = Read-P42Secret -Prompt 'Existing rag_e2e_writer@127.0.0.1 password (hidden)' }
$taskToken = Get-P42Value -Names @('E2E_MILVUS_TOKEN')
foreach ($taskSecret in @($taskEmbeddingKey,$taskRerankKey,$taskDeepseekKey,$taskPassword,$taskToken)) { if ($taskSecret) { $taskSecrets.Add($taskSecret) } }
Write-Host 'Frozen H42-01..08: at most 8 Embedding + 8 Milvus Search + 8 Rerank + 8 DeepSeek Gate; paid calls <=24; retries=0.'
Write-Host 'Budget reference CNY1.00; frozen conservative proxy CNY0.707392. This is not an absolute cloud invoice cap. Missing usage or unsafe budget stops the batch.'
Write-Host 'MySQL ai_interview_test and existing Milvus default/knowledge_chunk_vectors_test; read-only; no collection initialization.'
Write-Host 'All failures retain locks and evidence. There is no resume, replay or fallback model. Independent human labels are required for quality PASS.'
$taskConsent = Read-Host 'Type exactly: AUTHORIZE P4.2 P42-R02-20261014 8 EACH ONCE MAX24 CNY1'
if ($taskConsent -cne 'AUTHORIZE P4.2 P42-R02-20261014 8 EACH ONCE MAX24 CNY1') { throw 'Authorization absent; no real requests or execution reservation.' }
$taskEnvelope = @{
    authorization=$taskConsent; executionSealSha256=(Get-Content -LiteralPath (Join-Path $taskPrep 'manifest.sha256') -Raw).Trim()
    workspaceId=$taskWorkspace; embeddingKey=$taskEmbeddingKey; rerankKey=$taskRerankKey; deepseekKey=$taskDeepseekKey
    dbPassword=$taskPassword; milvusToken=$taskToken; budgetReferenceCny=1.0
} | ConvertTo-Json -Compress
try {
    Invoke-P42Java -Action execute-once -PrivateInput $taskEnvelope
    Invoke-P42Java -Action validate-run
    Invoke-P42Analysis -Arguments @('Analyze')
} finally {
    $taskEnvelope = $null; $taskEmbeddingKey = $null; $taskRerankKey = $null; $taskDeepseekKey = $null; $taskPassword = $null; $taskToken = $null
    $taskSecrets.Clear()
}
