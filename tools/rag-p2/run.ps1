[CmdletBinding()]
param(
    [ValidateSet('Offline', 'Verify', 'ExecuteOnce', 'Analyze')]
    [string]$Mode = 'Offline'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'PowerShell 7 is required.' }
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskPython = (Get-Command python -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$taskKey = $null
$taskSecure = $null
$taskPointer = [IntPtr]::Zero

function Invoke-P2Child {
    param([string[]]$ChildArguments, [string]$PrivateInput = '')
    $taskStart = [Diagnostics.ProcessStartInfo]::new($taskPython)
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
    foreach ($taskName in @('SystemRoot', 'WINDIR', 'COMSPEC', 'TEMP', 'TMP', 'USERPROFILE', 'APPDATA', 'LOCALAPPDATA')) {
        $taskValue = [Environment]::GetEnvironmentVariable($taskName, 'Process')
        if ($taskValue) { $taskStart.Environment[$taskName] = $taskValue }
    }
    # Isolated Python ignores inherited Python configuration; no third-party dependencies.
    foreach ($taskArgument in (@('-I', '-B', '-X', 'utf8') + $ChildArguments)) {
        $taskStart.ArgumentList.Add($taskArgument)
    }
    $taskProcess = $null
    try {
        $taskProcess = [Diagnostics.Process]::Start($taskStart)
        $taskStdout = $taskProcess.StandardOutput.ReadToEndAsync()
        $taskStderr = $taskProcess.StandardError.ReadToEndAsync()
        if ($PrivateInput) { $taskProcess.StandardInput.Write($PrivateInput) }
        $taskProcess.StandardInput.Close()
        $taskProcess.WaitForExit()
        foreach ($taskText in @($taskStdout.Result, $taskStderr.Result)) {
            if ($taskKey) { $taskText = $taskText.Replace($taskKey, '[REDACTED]') }
            if ($taskText) { Write-Host $taskText.TrimEnd() }
        }
        if ($taskProcess.ExitCode -ne 0) {
            throw "P2 child stopped (exit $($taskProcess.ExitCode)). No automatic retry; preserve run-once if reserved."
        }
    } finally {
        $taskStart.Environment.Clear()
        $PrivateInput = $null
        if ($taskProcess) { $taskProcess.Dispose() }
    }
}

function Get-P2ExistingValue {
    param([string]$Name)
    $taskValue = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if (-not $taskValue) { $taskValue = [Environment]::GetEnvironmentVariable($Name, 'User') }
    return $taskValue
}

try {
    $taskScript = Join-Path $PSScriptRoot 'p2.py'
    if ($Mode -eq 'Offline') {
        Invoke-P2Child -ChildArguments @($taskScript, 'prepare')
        Invoke-P2Child -ChildArguments @((Join-Path $PSScriptRoot 'test_p2.py'))
        & (Join-Path $PSScriptRoot 'test-entry.ps1')
        Write-Host 'P2_RERANK_PREP=READY; real external requests=0; database/vector writes=0.'
        Write-Host "Report: $taskRoot/target/rag-p2/prepared/report.md"
        Write-Host "Visual: $taskRoot/target/rag-p2/prepared/review.html"
        return
    }
    if ($Mode -eq 'Analyze') {
        Invoke-P2Child -ChildArguments @($taskScript, 'analyze')
        return
    }
    Invoke-P2Child -ChildArguments @($taskScript, 'verify')
    if ($Mode -eq 'Verify') { return }

    if ([Console]::IsInputRedirected -or -not [Environment]::UserInteractive) {
        throw 'ExecuteOnce requires a local interactive PowerShell console. Redirected/noninteractive consent is rejected.'
    }
    if (Test-Path -LiteralPath (Join-Path $taskRoot 'target/rag-p2/run-once')) {
        throw 'P2 real run already reserved. Preserve the directory; do not delete it or rerun charged queries.'
    }
    $taskWorkspace = Get-P2ExistingValue -Name 'P2_RERANK_WORKSPACE_ID'
    if (-not $taskWorkspace) { $taskWorkspace = Get-P2ExistingValue -Name 'DASHSCOPE_WORKSPACE_ID' }
    if (-not $taskWorkspace) {
        $taskWorkspace = Read-Host 'Beijing Workspace ID from Bailian workspace management (not an API URL)'
    }
    if ($taskWorkspace -cnotmatch '^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$') {
        throw 'Missing/invalid Workspace ID. No HTTP sent and no execution reserved.'
    }
    $taskKeySource = 'Hidden interactive input'
    foreach ($taskName in @('P2_RERANK_API_KEY', 'DASHSCOPE_API_KEY', 'EMBEDDING_API_KEY')) {
        $taskKey = Get-P2ExistingValue -Name $taskName
        if ($taskKey) { $taskKeySource = $taskName; break }
    }
    if (-not $taskKey) {
        $taskSecure = Read-Host 'Bailian Beijing API Key with this workspace/model permission (hidden)' -AsSecureString
        if ($taskSecure.Length -eq 0) { throw 'Empty API Key; no execution reserved.' }
        $taskPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecure)
        $taskKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPointer)
    }
    if ([string]::IsNullOrWhiteSpace($taskKey) -or $taskKey -match '[\s\x00-\x1f\x7f]') {
        throw 'Invalid API Key; no execution reserved.'
    }
    $taskPlan = Get-Content -LiteralPath (Join-Path $taskRoot 'target/rag-p2/prepared/requests.json') -Raw | ConvertFrom-Json
    $taskManifest = Get-Content -LiteralPath (Join-Path $taskRoot 'target/rag-p2/prepared/manifest.json') -Raw | ConvertFrom-Json
    Write-Host 'Model: qwen3.7-text-rerank; Beijing input price: CNY 0.50 / million tokens.'
    Write-Host "Endpoint: https://$taskWorkspace.cn-beijing.maas.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank"
    Write-Host "API Key source: $taskKeySource (value hidden; model access remains unverified)."
    Write-Host "Exactly 16 original questions x 5 full chunks; at most 16 HTTPS POST attempts, no retry/redirect/fallback."
    Write-Host "Conservative byte cost proxy: CNY $($taskPlan.conservativeCostProxyCny); authorization budget reference: CNY 0.50."
    Write-Host 'Proxy is not actual tokens or a guaranteed invoice cap. Missing usage or failure stops the run.'
    Write-Host 'Embedding/Milvus Search/DeepSeek/Jev=0; database connections/writes and vector writes=0.'
    Write-Host "Review exact requests first: $taskRoot/target/rag-p2/prepared/requests.json"
    $taskConsent = Read-Host 'To authorize this one paid experiment, type exactly: AUTHORIZE P2 RERANK 16 ONCE'
    if ($taskConsent -cne 'AUTHORIZE P2 RERANK 16 ONCE') {
        throw 'Authorization not granted; no HTTP sent and no execution reserved.'
    }
    $taskEnvelope = @{
        authorization = $taskConsent
        planSha256 = $taskManifest.planSha256
        workspaceId = $taskWorkspace
        apiKey = $taskKey
    } | ConvertTo-Json -Compress
    Invoke-P2Child -ChildArguments @($taskScript, 'execute-once') -PrivateInput $taskEnvelope
    $taskEnvelope = $null
    Write-Host "Results: $taskRoot/target/rag-p2/run-once/results.json"
    Write-Host "Visual: $taskRoot/target/rag-p2/run-once/review.html"
} finally {
    if ($taskPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPointer) }
    if ($taskSecure) { $taskSecure.Dispose() }
    $taskKey = $null
    $taskEnvelope = $null
    $taskConsent = $null
}
