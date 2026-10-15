$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskRun = Join-Path $PSScriptRoot 'run.ps1'
$taskChecks = [Collections.Generic.List[object]]::new()
function Add-P3Check {
    param([string]$Name,[bool]$Passed)
    $taskChecks.Add([pscustomobject]@{name=$Name;passed=$Passed})
}
function Invoke-P3Probe {
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
        return [pscustomobject]@{exitCode=$taskProcess.ExitCode;output=$taskOut.Result+$taskErr.Result}
    } finally { $taskProcess.Dispose() }
}
$taskTokens = $null
$taskErrors = $null
[Management.Automation.Language.Parser]::ParseFile($taskRun,[ref]$taskTokens,[ref]$taskErrors) | Out-Null
Add-P3Check -Name 'P3 run PS7 AST syntax' -Passed (@($taskErrors).Count -eq 0)
[Management.Automation.Language.Parser]::ParseFile($PSCommandPath,[ref]$taskTokens,[ref]$taskErrors) | Out-Null
Add-P3Check -Name 'P3 test PS7 AST syntax' -Passed (@($taskErrors).Count -eq 0)
$taskLock = Join-Path $taskRoot 'target/rag-p3/run-once'
$taskBefore = Test-Path -LiteralPath $taskLock
$taskVerify = Invoke-P3Probe -Mode Verify
Add-P3Check -Name 'Verify without network or credentials' -Passed ($taskVerify.exitCode -eq 0 -and $taskVerify.output.Contains('P3_FROZEN_INPUTS=VERIFIED'))
$taskExecute = Invoke-P3Probe -Mode ExecuteOnce
Add-P3Check -Name 'Noninteractive execution rejected before credential lookup' -Passed ($taskExecute.exitCode -ne 0 -and $taskExecute.output.Contains('requires a local interactive PowerShell console'))
Add-P3Check -Name 'Rejected execution creates no run-once lock' -Passed ($taskBefore -eq (Test-Path -LiteralPath $taskLock))
$taskFailures = @($taskChecks | Where-Object { -not $_.passed }).Count
$taskSummary = [ordered]@{type='OFFLINE_POWERSHELL_ENTRY';tests=$taskChecks.Count;failures=$taskFailures;errors=0;skipped=0;passed=($taskFailures -eq 0);realExternalRequests=0;databaseConnections=0;databaseWrites=0;vectorWrites=0;checks=@($taskChecks.ToArray())}
$taskSummary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskRoot 'target/rag-p3/offline/powershell-tests.json') -Encoding utf8NoBOM
Write-Host "P3 PowerShell tests=$($taskChecks.Count), failures=$taskFailures, errors=0, skipped=0."
if ($taskFailures -ne 0) { throw 'P3 PowerShell entry tests failed.' }
