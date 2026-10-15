[CmdletBinding()]
param([ValidateSet('Offline','Verify','ExecuteOnce','Analyze')][string]$Mode = 'Offline',[string]$SemanticReview = '')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'PowerShell 7 required.' }
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskPython = (Get-Command python -CommandType Application | Select-Object -First 1).Source
$taskJava = 'C:\Program Files\Java\jdk-21.0.11\bin'
$taskOffline = Join-Path $taskRoot 'target/rag-p4/offline'
$taskScript = Join-Path $PSScriptRoot 'p4.py'
$taskSecret = $null
$taskEnvelope = $null

function Invoke-P4Child {
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
        foreach ($taskText in @($taskOut.Result,$taskErr.Result)) {
            if ($taskSecret) { $taskText = $taskText.Replace($taskSecret,'[REDACTED]') }
            if ($taskText) { Write-Host $taskText.TrimEnd() }
        }
        if ($taskProcess.ExitCode -ne 0) { throw "P4 child stopped (exit $($taskProcess.ExitCode)); no retry, preserve locks." }
    } finally { $taskStart.Environment.Clear(); $PrivateInput = $null; $taskProcess.Dispose() }
}
function Invoke-P4Python {
    param([string[]]$PythonArguments,[string]$PrivateInput = '')
    Invoke-P4Child -Executable $taskPython -ChildArguments (@('-I','-B','-X','utf8') + $PythonArguments) -PrivateInput $PrivateInput
}

try {
    if ($Mode -eq 'ExecuteOnce' -and ([Console]::IsInputRedirected -or -not [Environment]::UserInteractive)) {
        throw 'ExecuteOnce requires a local interactive PowerShell console; no noninteractive consent.'
    }
    if ($Mode -eq 'Offline') {
        if (Test-Path -LiteralPath (Join-Path $taskRoot 'target/rag-p4/run-once')) { throw 'P4 real execution already reserved; do not overwrite its preparation.' }
        $taskClasses = Join-Path $taskOffline 'classes'
        New-Item -ItemType Directory -Path $taskClasses -Force | Out-Null
        $taskCp = (Join-Path $taskRoot 'target/classes') + ';' + (Get-Content -LiteralPath (Join-Path $taskRoot 'target/rag-e2e/runtime-classpath.txt') -Raw).Trim()
        foreach ($taskEntry in $taskCp.Split(';')) {
            if (-not (Test-Path -LiteralPath $taskEntry)) { throw 'Cached Java dependency missing; no network dependency installation during offline mode.' }
        }
        $taskSources = @('tools/rag-p4/SemanticEvidenceGate.java','tools/rag-p4/P4DeepSeek.java','tools/rag-p4/SemanticEvidenceGateOfflineTest.java',
            'src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java',
            'src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekJsonCompletionResult.java')
        Invoke-P4Child -Executable (Join-Path $taskJava 'javac.exe') -ChildArguments (@('-J-Duser.language=en','-proc:none','-encoding','UTF-8','-cp',$taskCp,'-d',$taskClasses) + $taskSources)
        Invoke-P4Python -PythonArguments @($taskScript,'prepare')
        Invoke-P4Child -Executable (Join-Path $taskJava 'java.exe') -ChildArguments @('-cp',($taskClasses + ';' + $taskCp),'SemanticEvidenceGateOfflineTest',
            (Join-Path $taskRoot 'target/rag-p4/prepared'),$taskOffline,(Join-Path $PSScriptRoot 'system-prompt.txt'))
        Invoke-P4Python -PythonArguments @((Join-Path $PSScriptRoot 'test_p4.py'))
        & (Join-Path $PSScriptRoot 'test-entry.ps1')
        Invoke-P4Python -PythonArguments @($taskScript,'ready')
        Write-Host "Report: $taskRoot/target/rag-p4/prepared/report.md"
        return
    }
    Invoke-P4Python -PythonArguments @($taskScript,'verify')
    if ($Mode -eq 'Verify') { return }
    if ($Mode -eq 'Analyze') {
        $taskAnalyzeArgs = @($taskScript,'analyze')
        if ($SemanticReview) { $taskAnalyzeArgs += @('--review',$SemanticReview) }
        Invoke-P4Python -PythonArguments $taskAnalyzeArgs
        return
    }
    if (Test-Path -LiteralPath (Join-Path $taskRoot 'target/rag-p4/run-once')) { throw 'P4 execution reserved; preserve lock, no rerun or resume.' }
    $taskPlan = Get-Content -LiteralPath (Join-Path $taskRoot 'target/rag-p4/prepared/plan.json') -Raw | ConvertFrom-Json
    $taskSeal = (Get-Content -LiteralPath (Join-Path $taskRoot 'target/rag-p4/prepared/freeze.sha256') -Raw).Trim()
    Write-Host 'P4 development set: max 12 DeepSeek requests, one per question for two candidates; retries=0.'
    Write-Host 'Embedding/Milvus/Rerank/Jev/MySQL connections and writes=0. Production default unchanged.'
    Write-Host 'Model: deepseek-v4-flash legacy alias -> DeepSeek-V4.1-Flash; POST https://api.deepseek.com/chat/completions.'
    Write-Host "Peak no-cache cost proxy CNY $($taskPlan.peakCostProxyCny), budget CNY 1.00, max output 3072 per request. Actual usage/invoice may differ."
    Write-Host "Review report and frozen inputs: $taskRoot/target/rag-p4/prepared"
    $taskConsent = Read-Host 'Type exactly to authorize this sealed single run: AUTHORIZE P4 12 DEEPSEEK ONCE'
    if ($taskConsent -cne 'AUTHORIZE P4 12 DEEPSEEK ONCE') { throw 'No authorization; no HTTP, no lock reserved.' }
    foreach ($taskName in @('P4_DEEPSEEK_API_KEY','DEEPSEEK_API_KEY')) {
        $taskSecret = [Environment]::GetEnvironmentVariable($taskName,'Process')
        if (-not $taskSecret) { $taskSecret = [Environment]::GetEnvironmentVariable($taskName,'User') }
        if ($taskSecret) { break }
    }
    if (-not $taskSecret) {
        $taskSecure = Read-Host 'DeepSeek API key (hidden, stdin only)' -AsSecureString
        $taskPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecure)
        try { $taskSecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPointer); $taskSecure.Dispose() }
    }
    $taskEnvelope = @{authorization=$taskConsent;freezeSha256=$taskSeal;apiKey=$taskSecret} | ConvertTo-Json -Compress
    Invoke-P4Python -PythonArguments @($taskScript,'execute-once') -PrivateInput $taskEnvelope
    Write-Host "Results: $taskRoot/target/rag-p4/run-once/results.json; semantic quality still requires review."
} finally { $taskSecret = $null; $taskEnvelope = $null }
