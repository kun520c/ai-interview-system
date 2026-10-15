[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'PowerShell 7 required.' }
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskArchive = Join-Path $taskRoot 'target/rag-p4-repair/support-point-v1'
$taskPython = (Get-Command python -CommandType Application | Select-Object -First 1).Source
$taskJava = 'C:\Program Files\Java\jdk-21.0.11\bin'
$taskOutput = Join-Path $taskArchive ('reverification-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffffffZ'))

function Invoke-P4OfflineChild {
    param([string]$Executable,[string[]]$ChildArguments)
    $taskStart = [Diagnostics.ProcessStartInfo]::new($Executable)
    $taskStart.WorkingDirectory = $taskRoot
    $taskStart.UseShellExecute = $false
    $taskStart.CreateNoWindow = $true
    $taskStart.RedirectStandardInput = $true
    $taskStart.RedirectStandardOutput = $true
    $taskStart.RedirectStandardError = $true
    $taskStart.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
    $taskStart.StandardErrorEncoding = [Text.UTF8Encoding]::new($false)
    $taskStart.Environment.Clear()
    foreach ($taskName in @('SystemRoot','WINDIR','COMSPEC','TEMP','TMP','USERPROFILE')) {
        $taskValue = [Environment]::GetEnvironmentVariable($taskName,'Process')
        if ($taskValue) { $taskStart.Environment[$taskName] = $taskValue }
    }
    foreach ($taskArg in $ChildArguments) { $taskStart.ArgumentList.Add($taskArg) }
    $taskProcess = [Diagnostics.Process]::Start($taskStart)
    try {
        $taskProcess.StandardInput.Close()
        $taskOut = $taskProcess.StandardOutput.ReadToEndAsync()
        $taskErr = $taskProcess.StandardError.ReadToEndAsync()
        $taskProcess.WaitForExit()
        if ($taskOut.Result) { Write-Host $taskOut.Result.TrimEnd() }
        if ($taskErr.Result) { Write-Host $taskErr.Result.TrimEnd() }
        if ($taskProcess.ExitCode -ne 0) { throw "Offline child failed ($($taskProcess.ExitCode)). No provider request exists in this entry." }
    } finally { $taskStart.Environment.Clear(); $taskProcess.Dispose() }
}
Invoke-P4OfflineChild -Executable $taskPython -ChildArguments @('-I','-B','-X','utf8',(Join-Path $PSScriptRoot 'audit.py'),'preflight')
New-Item -ItemType Directory -Path $taskOutput | Out-Null
$taskBefore = Join-Path $taskOutput 'original-classes'
$taskAfter = Join-Path $taskOutput 'repaired-classes'
$taskBaseline = Join-Path $taskOutput 'baseline'
$taskRegression = Join-Path $taskOutput 'regression-tests'
foreach ($taskDir in @($taskBefore,$taskAfter,$taskBaseline,$taskRegression)) { New-Item -ItemType Directory -Path $taskDir | Out-Null }
[IO.File]::Copy((Join-Path $taskArchive 'original-SemanticEvidenceGate.java'),(Join-Path $taskBaseline 'SemanticEvidenceGate.java'),$false)
$taskCp = (Join-Path $taskRoot 'target/rag-p4/offline/classes') + ';' + (Join-Path $taskRoot 'target/classes') + ';' +
    (Get-Content -LiteralPath (Join-Path $taskRoot 'target/rag-e2e/runtime-classpath.txt') -Raw).Trim()
foreach ($taskEntry in $taskCp.Split(';')) {
    if (-not (Test-Path -LiteralPath $taskEntry)) { throw 'Cached dependency missing; no installation or network in this repair.' }
}
$taskSaved = Join-Path $PSScriptRoot 'SavedResponseReverification.java'
Invoke-P4OfflineChild -Executable (Join-Path $taskJava 'javac.exe') -ChildArguments @('-proc:none','-encoding','UTF-8','-cp',$taskCp,'-d',$taskBefore,
    (Join-Path $taskBaseline 'SemanticEvidenceGate.java'),$taskSaved)
Invoke-P4OfflineChild -Executable (Join-Path $taskJava 'java.exe') -ChildArguments @('-cp',($taskBefore + ';' + $taskCp),
    'SavedResponseReverification',$taskRoot,(Join-Path $taskOutput 'original-revalidation.json'))
Invoke-P4OfflineChild -Executable (Join-Path $taskJava 'javac.exe') -ChildArguments @('-proc:none','-encoding','UTF-8','-cp',$taskCp,'-d',$taskAfter,
    (Join-Path $taskRoot 'tools/rag-p4/SemanticEvidenceGate.java'),$taskSaved,(Join-Path $PSScriptRoot 'P4GateRepairOfflineTest.java'),
    (Join-Path $taskRoot 'tools/rag-p4/SemanticEvidenceGateOfflineTest.java'))
Invoke-P4OfflineChild -Executable (Join-Path $taskJava 'java.exe') -ChildArguments @('-cp',($taskAfter + ';' + $taskCp),
    'SavedResponseReverification',$taskRoot,(Join-Path $taskOutput 'repaired-revalidation.json'))
Invoke-P4OfflineChild -Executable (Join-Path $taskJava 'java.exe') -ChildArguments @('-cp',($taskAfter + ';' + $taskCp),
    'P4GateRepairOfflineTest',$taskRoot,(Join-Path $taskOutput 'repair-tests.json'))
Invoke-P4OfflineChild -Executable (Join-Path $taskJava 'java.exe') -ChildArguments @('-cp',($taskAfter + ';' + $taskCp),
    'SemanticEvidenceGateOfflineTest',(Join-Path $taskRoot 'target/rag-p4/prepared'),$taskRegression,
    (Join-Path $taskRoot 'tools/rag-p4/system-prompt.txt'))
Invoke-P4OfflineChild -Executable $taskPython -ChildArguments @('-I','-B','-X','utf8',(Join-Path $PSScriptRoot 'audit.py'),'report','--output',$taskOutput)
Write-Host "Read-only report: $taskOutput/report.md"
