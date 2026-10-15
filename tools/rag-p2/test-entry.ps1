$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskRun = Join-Path $PSScriptRoot 'run.ps1'
$taskOutput = Join-Path $taskRoot 'target/rag-p2/offline'
$taskChecks = [Collections.Generic.List[object]]::new()

function Add-P2Check {
    param([string]$Name, [bool]$Passed)
    $taskChecks.Add([pscustomobject]@{name = $Name; passed = $Passed})
}

function Invoke-P2EntryProbe {
    param([string]$Mode)
    $taskStart = [Diagnostics.ProcessStartInfo]::new((Join-Path $PSHOME 'pwsh.exe'))
    $taskStart.WorkingDirectory = $taskRoot
    $taskStart.UseShellExecute = $false
    $taskStart.CreateNoWindow = $true
    $taskStart.RedirectStandardInput = $true
    $taskStart.RedirectStandardOutput = $true
    $taskStart.RedirectStandardError = $true
    $taskStart.Environment.Remove('P2_RERANK_API_KEY') | Out-Null
    $taskStart.Environment.Remove('P2_RERANK_WORKSPACE_ID') | Out-Null
    foreach ($taskArg in @('-NoProfile', '-NonInteractive', '-File', $taskRun, '-Mode', $Mode)) {
        $taskStart.ArgumentList.Add($taskArg)
    }
    $taskProcess = [Diagnostics.Process]::Start($taskStart)
    try {
        $taskProcess.StandardInput.Close()
        $taskOut = $taskProcess.StandardOutput.ReadToEndAsync()
        $taskErr = $taskProcess.StandardError.ReadToEndAsync()
        $taskProcess.WaitForExit()
        return [pscustomobject]@{exitCode = $taskProcess.ExitCode; output = $taskOut.Result + $taskErr.Result}
    } finally {
        $taskProcess.Dispose()
    }
}

$taskTokens = $null
$taskErrors = $null
[Management.Automation.Language.Parser]::ParseFile($taskRun, [ref]$taskTokens, [ref]$taskErrors) | Out-Null
Add-P2Check -Name 'PowerShell 7 entry AST syntax' -Passed (@($taskErrors).Count -eq 0)
[Management.Automation.Language.Parser]::ParseFile($PSCommandPath, [ref]$taskTokens, [ref]$taskErrors) | Out-Null
Add-P2Check -Name 'PowerShell 7 test AST syntax' -Passed (@($taskErrors).Count -eq 0)
$taskLock = Join-Path $taskRoot 'target/rag-p2/run-once'
$taskBefore = Test-Path -LiteralPath $taskLock
$taskVerify = Invoke-P2EntryProbe -Mode 'Verify'
Add-P2Check -Name 'Verify entry completes offline' -Passed ($taskVerify.exitCode -eq 0 -and $taskVerify.output.Contains('P2_RERANK_PREP=READY'))
$taskExecute = Invoke-P2EntryProbe -Mode 'ExecuteOnce'
Add-P2Check -Name 'ExecuteOnce rejects redirected/noninteractive consent before credential lookup' -Passed (
    $taskExecute.exitCode -ne 0 -and $taskExecute.output.Contains('requires a local interactive PowerShell console'))
Add-P2Check -Name 'Rejected entry creates no real-run reservation' -Passed ($taskBefore -eq (Test-Path -LiteralPath $taskLock))
$taskFailed = @($taskChecks | Where-Object { -not $_.passed }).Count
$taskSummary = [ordered]@{
    type = 'OFFLINE_POWERSHELL_ENTRY_TESTS'
    tests = $taskChecks.Count
    failures = $taskFailed
    errors = 0
    skipped = 0
    passed = ($taskFailed -eq 0)
    runScriptSha256 = (Get-FileHash -LiteralPath $taskRun -Algorithm SHA256).Hash.ToLowerInvariant()
    realExternalRequests = 0
    databaseWrites = 0
    vectorWrites = 0
    checks = @($taskChecks.ToArray())
}
$taskSummary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $taskOutput 'powershell-tests.json') -Encoding utf8NoBOM
Write-Host "P2 PowerShell offline tests: $($taskChecks.Count), failures=$taskFailed, errors=0, skipped=0."
if ($taskFailed -ne 0) { throw 'PowerShell entry tests failed; no paid execution authorized.' }
