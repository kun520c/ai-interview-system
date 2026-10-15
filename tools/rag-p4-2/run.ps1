[CmdletBinding()]
param([ValidateSet('Offline','Verify')][string]$Mode = 'Offline')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'PowerShell 7 required.' }
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskPython = (Get-Command python -CommandType Application | Select-Object -First 1).Source
$taskJava = 'C:\Program Files\Java\jdk-21.0.11\bin'
$taskPrepared = Join-Path $taskRoot 'target/rag-p4-2/prepared'
$taskOffline = Join-Path $taskRoot 'target/rag-p4-2/offline'
function Invoke-P42Local {
    param([string]$Executable, [string[]]$ChildArguments)
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
        $taskProcess.StandardInput.Close()
        $taskProcess.WaitForExit()
        foreach ($taskText in @($taskOut.Result, $taskErr.Result)) { if ($taskText) { Write-Host $taskText.TrimEnd() } }
        if ($taskProcess.ExitCode -ne 0) { throw "Local P4.2 check failed: exit=$($taskProcess.ExitCode). No model requests or retries." }
    } finally { $taskProcess.Dispose() }
}
function Invoke-P42Python {
    param([string]$Action)
    Invoke-P42Local -Executable $taskPython -ChildArguments @('-I','-B','-X','utf8',(Join-Path $PSScriptRoot 'prepare.py'),$Action)
}
if ($Mode -eq 'Verify') { Invoke-P42Python -Action Verify; exit 0 }
$taskAlreadyFrozen = Test-Path -LiteralPath (Join-Path $taskPrepared 'freeze.json')
if ($taskAlreadyFrozen) { Invoke-P42Python -Action Verify } else { Invoke-P42Python -Action Prepare }
$taskClasses = Join-Path $taskOffline 'classes'
New-Item -ItemType Directory -Path $taskClasses -Force | Out-Null
$taskCp = (Join-Path $taskRoot 'target/classes') + ';' + (Get-Content -LiteralPath (Join-Path $taskRoot 'target/rag-e2e/runtime-classpath.txt') -Raw).Trim()
foreach ($taskEntry in $taskCp.Split(';')) { if (-not (Test-Path -LiteralPath $taskEntry)) { throw 'Cached dependency missing. No automatic network dependency installation.' } }
$taskSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'java') -Recurse -Filter '*.java' -File | Sort-Object FullName | ForEach-Object FullName)
$taskSources += @((Join-Path $PSScriptRoot 'P42OfflineTest.java'), (Join-Path $taskRoot 'tools/rag-p4/SemanticEvidenceGate.java'),
    (Join-Path $taskRoot 'src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekEvaluationClient.java'),
    (Join-Path $taskRoot 'src/main/java/com/kun/aiinterview/interview/evaluation/llm/deepseek/DeepSeekJsonCompletionResult.java'))
Invoke-P42Local -Executable (Join-Path $taskJava 'javac.exe') -ChildArguments (@('-proc:none','-encoding','UTF-8','-cp',$taskCp,'-d',$taskClasses) + $taskSources)
$taskMockito = @($taskCp.Split(';') | Where-Object { $_ -match 'mockito-core-[\d.]+\.jar$' })
if ($taskMockito.Count -ne 1) { throw 'Expected one cached Mockito test agent.' }
Invoke-P42Local -Executable (Join-Path $taskJava 'java.exe') -ChildArguments @("-javaagent:$($taskMockito[0])",'-Dfile.encoding=UTF-8','-cp',($taskClasses + ';' + $taskCp),'P42OfflineTest',$taskRoot)
Invoke-P42Local -Executable $taskPython -ChildArguments @('-I','-B','-X','utf8',(Join-Path $PSScriptRoot 'verify_tests.py'))
if ($taskAlreadyFrozen) { Invoke-P42Python -Action Verify } else { Invoke-P42Python -Action Freeze }
Write-Host 'P4_2_PREP=READY; independent adapter verified offline; production default unchanged; paid requests=0.'
