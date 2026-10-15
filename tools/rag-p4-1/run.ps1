[CmdletBinding()]
param([ValidateSet('Offline','Verify','ExecuteOnce','Analyze')][string]$Mode = 'Offline')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'PowerShell 7 required.' }
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskPython = (Get-Command python -CommandType Application | Select-Object -First 1).Source
$taskJava = 'C:\Program Files\Java\jdk-21.0.11\bin'
$taskScript = Join-Path $PSScriptRoot 'p41.py'
$taskOffline = Join-Path $taskRoot 'target/rag-p4-1/offline'
$taskSecret = $null
$taskEnvelope = $null
function Invoke-P41Child {
    param([string]$Executable,[string[]]$ChildArguments,[string]$PrivateInput = '')
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
        $taskValue = [Environment]::GetEnvironmentVariable($taskName,'Process')
        if ($taskValue) { $taskStart.Environment[$taskName] = $taskValue }
    }
    foreach ($taskArg in $ChildArguments) { $taskStart.ArgumentList.Add($taskArg) }
    $taskProcess = [Diagnostics.Process]::Start($taskStart)
    try {
        $taskOut = $taskProcess.StandardOutput.ReadToEndAsync(); $taskErr = $taskProcess.StandardError.ReadToEndAsync()
        if ($PrivateInput) { $taskProcess.StandardInput.Write($PrivateInput) }
        $taskProcess.StandardInput.Close(); $taskProcess.WaitForExit()
        foreach ($taskText in @($taskOut.Result,$taskErr.Result)) {
            if ($taskSecret) { $taskText = $taskText.Replace($taskSecret,'[REDACTED]') }
            if ($taskText) { Write-Host $taskText.TrimEnd() }
        }
        if ($taskProcess.ExitCode -ne 0) { throw "P4.1 child stopped ($($taskProcess.ExitCode)); no retry, preserve locks." }
    } finally { $taskStart.Environment.Clear(); $PrivateInput = $null; $taskProcess.Dispose() }
}
function Invoke-P41Python {
    param([string[]]$Arguments,[string]$PrivateInput = '')
    Invoke-P41Child -Executable $taskPython -ChildArguments (@('-I','-B','-X','utf8') + $Arguments) -PrivateInput $PrivateInput
}
try {
    if ($Mode -eq 'ExecuteOnce' -and ([Console]::IsInputRedirected -or -not [Environment]::UserInteractive)) {
        throw 'ExecuteOnce requires a local interactive PowerShell console; no noninteractive consent.'
    }
    if ($Mode -eq 'Offline') {
        if (Test-Path -LiteralPath (Join-Path $taskRoot 'target/rag-p4-1/run-once')) { throw 'P4.1 reserved; cannot replace frozen preparation.' }
        $taskClasses = Join-Path $taskOffline 'classes'; New-Item -ItemType Directory -Path $taskClasses -Force | Out-Null
        $taskCp = (Join-Path $taskRoot 'target/classes') + ';' + (Get-Content -LiteralPath (Join-Path $taskRoot 'target/rag-e2e/runtime-classpath.txt') -Raw).Trim()
        foreach ($taskEntry in $taskCp.Split(';')) { if (-not (Test-Path -LiteralPath $taskEntry)) { throw 'Cached dependency missing.' } }
        Invoke-P41Child -Executable (Join-Path $taskJava 'javac.exe') -ChildArguments @('-proc:none','-encoding','UTF-8','-cp',$taskCp,'-d',$taskClasses,
            (Join-Path $taskRoot 'tools/rag-p4/SemanticEvidenceGate.java'),(Join-Path $taskRoot 'tools/rag-p4/P4DeepSeek.java'),
            (Join-Path $PSScriptRoot 'P41Once.java'),(Join-Path $PSScriptRoot 'P41OfflineTest.java'),
            (Join-Path $taskRoot 'src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java'),
            (Join-Path $taskRoot 'src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekJsonCompletionResult.java'))
        Invoke-P41Python -Arguments @($taskScript,'prepare')
        Invoke-P41Child -Executable (Join-Path $taskJava 'java.exe') -ChildArguments @('-cp',($taskClasses + ';' + $taskCp),'P41OfflineTest',$taskRoot)
        Invoke-P41Python -Arguments @((Join-Path $PSScriptRoot 'test_p41.py'))
        & (Join-Path $PSScriptRoot 'test-entry.ps1')
        Invoke-P41Python -Arguments @($taskScript,'ready')
        Write-Host "Report: $taskRoot/target/rag-p4-1/prepared/report.md"
        return
    }
    Invoke-P41Python -Arguments @($taskScript,'verify')
    if ($Mode -eq 'Verify') { return }
    if ($Mode -eq 'Analyze') { Invoke-P41Python -Arguments @($taskScript,'analyze'); return }
    if (Test-Path -LiteralPath (Join-Path $taskRoot 'target/rag-p4-1/run-once')) { throw 'P4.1 reserved; no rerun/resume/delete.' }
    $taskPlan = Get-Content -LiteralPath (Join-Path $taskRoot 'target/rag-p4-1/prepared/plan.json') -Raw | ConvertFrom-Json
    $taskSeal = (Get-Content -LiteralPath (Join-Path $taskRoot 'target/rag-p4-1/prepared/freeze.sha256') -Raw).Trim()
    Write-Host 'P4.1 NEW prompt micro experiment: C01/N02/N06; max 3 DeepSeek requests, two candidates each, retry=0.'
    Write-Host 'Separate P4.1 run locks; not a resume of old P4. Embedding/Milvus/Reranker/Jev/DB connections and writes=0.'
    Write-Host 'Model deepseek-v4-flash legacy alias -> DeepSeek-V4.1-Flash; https://api.deepseek.com/chat/completions.'
    Write-Host "Peak no-cache proxy CNY $($taskPlan.peakCostProxyCny); budget CNY 0.25; max output 3072 per request."
    Write-Host "Review $taskRoot/target/rag-p4-1/prepared/report.md and freeze.json before authorization."
    $taskConsent = Read-Host 'Type exactly for this NEW sealed run: AUTHORIZE P4.1 C01 N02 N06 3 ONCE'
    if ($taskConsent -cne 'AUTHORIZE P4.1 C01 N02 N06 3 ONCE') { throw 'No authorization; no request or lock reserved.' }
    foreach ($taskName in @('P41_DEEPSEEK_API_KEY','DEEPSEEK_API_KEY')) {
        $taskSecret = [Environment]::GetEnvironmentVariable($taskName,'Process')
        if (-not $taskSecret) { $taskSecret = [Environment]::GetEnvironmentVariable($taskName,'User') }
        if ($taskSecret) { break }
    }
    if (-not $taskSecret) {
        $taskSecure = Read-Host 'DeepSeek key (hidden; private stdin)' -AsSecureString
        $taskPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecure)
        try { $taskSecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPointer); $taskSecure.Dispose() }
    }
    $taskEnvelope = @{authorization=$taskConsent;freezeSha256=$taskSeal;apiKey=$taskSecret} | ConvertTo-Json -Compress
    Invoke-P41Python -Arguments @($taskScript,'execute-once') -PrivateInput $taskEnvelope
    Write-Host "Results: $taskRoot/target/rag-p4-1/run-once/results.json; semantic review required."
} finally { $taskSecret = $null; $taskEnvelope = $null }
