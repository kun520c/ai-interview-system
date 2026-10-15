$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskRun = Join-Path $PSScriptRoot 'run.ps1'
$taskChecks = [Collections.Generic.List[object]]::new()
function Add-P41Check { param([string]$Name,[bool]$Passed) $taskChecks.Add([pscustomobject]@{name=$Name;passed=$Passed}) }
function Invoke-P41Probe {
    param([string]$Mode)
    $taskStart = [Diagnostics.ProcessStartInfo]::new((Join-Path $PSHOME 'pwsh.exe'))
    $taskStart.WorkingDirectory = $taskRoot; $taskStart.UseShellExecute = $false; $taskStart.CreateNoWindow = $true
    $taskStart.RedirectStandardInput = $true; $taskStart.RedirectStandardOutput = $true; $taskStart.RedirectStandardError = $true
    foreach ($taskArg in @('-NoProfile','-NonInteractive','-File',$taskRun,'-Mode',$Mode)) { $taskStart.ArgumentList.Add($taskArg) }
    $taskProcess = [Diagnostics.Process]::Start($taskStart)
    try {
        $taskProcess.StandardInput.Close(); $taskOut = $taskProcess.StandardOutput.ReadToEndAsync(); $taskErr = $taskProcess.StandardError.ReadToEndAsync()
        $taskProcess.WaitForExit(); return [pscustomobject]@{code=$taskProcess.ExitCode;text=$taskOut.Result+$taskErr.Result}
    } finally { $taskProcess.Dispose() }
}
foreach ($taskFile in @($taskRun,$PSCommandPath)) {
    $taskTokens = $null; $taskErrors = $null
    [Management.Automation.Language.Parser]::ParseFile($taskFile,[ref]$taskTokens,[ref]$taskErrors) | Out-Null
    Add-P41Check -Name "PS7 syntax $(Split-Path $taskFile -Leaf)" -Passed (@($taskErrors).Count -eq 0)
}
$taskBefore = Test-Path -LiteralPath (Join-Path $taskRoot 'target/rag-p4-1/run-once')
$taskVerify = Invoke-P41Probe -Mode Verify
Add-P41Check -Name 'Verify without network/credentials' -Passed ($taskVerify.code -eq 0 -and $taskVerify.text.Contains('P4_1_FROZEN_INPUTS=VERIFIED'))
$taskExecute = Invoke-P41Probe -Mode ExecuteOnce
Add-P41Check -Name 'Noninteractive paid execution blocked' -Passed ($taskExecute.code -ne 0 -and $taskExecute.text.Contains('requires a local interactive PowerShell console'))
Add-P41Check -Name 'Rejected run reserves no new lock' -Passed ($taskBefore -eq (Test-Path -LiteralPath (Join-Path $taskRoot 'target/rag-p4-1/run-once')))
$taskFailures = @($taskChecks | Where-Object {-not $_.passed}).Count
@{tests=$taskChecks.Count;failures=$taskFailures;errors=0;skipped=0;paidCalls=0;checks=@($taskChecks.ToArray())} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskRoot 'target/rag-p4-1/offline/entry-tests.json') -Encoding utf8NoBOM
Write-Host "P4.1 entry tests=$($taskChecks.Count), failures=$taskFailures, errors=0, skipped=0."
if ($taskFailures) { throw 'P4.1 entry test failed.' }
