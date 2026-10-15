$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskRun = Join-Path $PSScriptRoot 'run.ps1'
$taskChecks = [Collections.Generic.List[object]]::new()
function Add-P4Check { param([string]$Name,[bool]$Passed) $taskChecks.Add([pscustomobject]@{name=$Name;passed=$Passed}) }
function Invoke-P4Probe {
    param([string]$Mode)
    $taskStart = [Diagnostics.ProcessStartInfo]::new((Join-Path $PSHOME 'pwsh.exe'))
    $taskStart.WorkingDirectory = $taskRoot
    $taskStart.UseShellExecute = $false
    $taskStart.CreateNoWindow = $true
    $taskStart.RedirectStandardInput = $true
    $taskStart.RedirectStandardOutput = $true
    $taskStart.RedirectStandardError = $true
    foreach ($taskArg in @('-NoProfile','-NonInteractive','-File',$taskRun,'-Mode',$Mode)) { $taskStart.ArgumentList.Add($taskArg) }
    $taskProcess = [Diagnostics.Process]::Start($taskStart)
    try {
        $taskProcess.StandardInput.Close()
        $taskOut = $taskProcess.StandardOutput.ReadToEndAsync()
        $taskErr = $taskProcess.StandardError.ReadToEndAsync()
        $taskProcess.WaitForExit()
        return [pscustomobject]@{code=$taskProcess.ExitCode;text=$taskOut.Result+$taskErr.Result}
    } finally { $taskProcess.Dispose() }
}
foreach ($taskFile in @($taskRun,$PSCommandPath)) {
    $taskTokens = $null; $taskErrors = $null
    [Management.Automation.Language.Parser]::ParseFile($taskFile,[ref]$taskTokens,[ref]$taskErrors) | Out-Null
    Add-P4Check -Name "PS7 AST syntax $(Split-Path $taskFile -Leaf)" -Passed (@($taskErrors).Count -eq 0)
}
$taskReserved = Test-Path -LiteralPath (Join-Path $taskRoot 'target/rag-p4/run-once')
$taskVerify = Invoke-P4Probe -Mode Verify
Add-P4Check -Name 'Verify without credentials/network' -Passed ($taskVerify.code -eq 0 -and $taskVerify.text.Contains('P4_FROZEN_INPUTS=VERIFIED'))
$taskExecute = Invoke-P4Probe -Mode ExecuteOnce
Add-P4Check -Name 'Noninteractive paid execution rejected before secrets/locks' -Passed ($taskExecute.code -ne 0 -and $taskExecute.text.Contains('requires a local interactive PowerShell console'))
Add-P4Check -Name 'Rejected execution does not reserve run' -Passed ($taskReserved -eq (Test-Path -LiteralPath (Join-Path $taskRoot 'target/rag-p4/run-once')))
$taskFailures = @($taskChecks | Where-Object {-not $_.passed}).Count
$taskSummary = @{type='OFFLINE_POWERSHELL';tests=$taskChecks.Count;failures=$taskFailures;errors=0;skipped=0;paidRequests=0;checks=@($taskChecks.ToArray())}
$taskSummary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskRoot 'target/rag-p4/offline/powershell-tests.json') -Encoding utf8NoBOM
Write-Host "P4 PowerShell tests=$($taskChecks.Count), failures=$taskFailures, errors=0, skipped=0."
if ($taskFailures) { throw 'P4 entry tests failed.' }
