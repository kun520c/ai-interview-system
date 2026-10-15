param(
    [ValidateSet('Prepare','ExecuteOnce')][string]$Mode='Prepare',
    [string]$Authorization='',
    [switch]$ProviderAccessConfirmed,
    [switch]$PricingConfirmed,
    [string]$AllowedCountry='',
    [decimal]$ConfirmedInputUsdPerMillion=0.042
)
$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskJavaHome='C:\Program Files\Java\jdk-21.0.11'
$taskPrep=Join-Path $taskRoot 'target/jev-proxy-network/preparation'
$taskAttempt=Join-Path $taskRoot 'target/jev-proxy-network/score-once-20261014-001'
Set-Location -LiteralPath $taskRoot
function Invoke-JevChild {
    param([string]$Executable,[string[]]$ChildArguments,[switch]$WithJevKey)
    $taskStart=[Diagnostics.ProcessStartInfo]::new($Executable)
    $taskStart.UseShellExecute=$false
    $taskStart.CreateNoWindow=$true
    $taskStart.WorkingDirectory=$taskRoot
    $taskStart.RedirectStandardOutput=$true
    $taskStart.RedirectStandardError=$true
    $taskStart.Environment.Clear()
    foreach($taskName in @('SystemRoot','WINDIR','COMSPEC','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA','PATHEXT')) {
        $taskValue=[Environment]::GetEnvironmentVariable($taskName,'Process')
        if($taskValue) {$taskStart.Environment[$taskName]=$taskValue}
    }
    $taskStart.Environment['JAVA_HOME']=$taskJavaHome
    $taskStart.Environment['PATH']="$taskJavaHome\bin;$env:SystemRoot\System32;$env:SystemRoot;$env:SystemRoot\System32\WindowsPowerShell\v1.0"
    if($WithJevKey) {$taskStart.Environment['JEV_API_KEY']=$env:JEV_API_KEY}
    foreach($taskArgument in $ChildArguments) {$taskStart.ArgumentList.Add($taskArgument)}
    $taskProcess=[Diagnostics.Process]::Start($taskStart)
    $taskStdout=$taskProcess.StandardOutput.ReadToEndAsync()
    $taskStderr=$taskProcess.StandardError.ReadToEndAsync()
    $taskProcess.WaitForExit()
    if($taskStdout.Result) {Write-Host $taskStdout.Result}
    # Paid child emits only sanitized stdout. Never print arbitrary stderr from that child.
    if(!$WithJevKey -and $taskStderr.Result) {Write-Host $taskStderr.Result}
    if($taskProcess.ExitCode -ne 0) {throw "Jev child stopped (exit=$($taskProcess.ExitCode)); preserve locks, no retry"}
}
if($Mode -eq 'Prepare') {
    New-Item -ItemType Directory -Force -Path (Join-Path $taskPrep 'classes') | Out-Null
    Invoke-JevChild -Executable $env:COMSPEC -ChildArguments @('/d','/c','mvnw.cmd -o -B -ntp -DskipTests compile dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile=target/jev-proxy-network/preparation/classpath.txt')
    $taskCp=(Join-Path $taskRoot 'target/classes')+';'+(Get-Content (Join-Path $taskPrep 'classpath.txt') -Raw).Trim()
    $taskSources=@(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | ForEach-Object FullName)
    Invoke-JevChild -Executable (Join-Path $taskJavaHome 'bin/javac.exe') -ChildArguments (@('-proc:none','-encoding','UTF-8','-cp',$taskCp,'-d',(Join-Path $taskPrep 'classes'))+$taskSources)
    Invoke-JevChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -ChildArguments @('-Djdk.httpclient.disableRetryConnect=true','-Djdk.httpclient.enableAllMethodRetry=false','-cp',((Join-Path $taskPrep 'classes')+';'+$taskCp),'JevScoreOnceOfflineTest',(Join-Path $taskPrep 'runner-offline-status.json'))
    $taskSeal=[ordered]@{}
    $taskFiles=@($taskSources)+@($PSCommandPath)
    $taskFiles+=@(Get-ChildItem -LiteralPath (Join-Path $taskPrep 'classes') -Filter '*.class' | ForEach-Object FullName)
    foreach($taskClass in @('JevClient','JevProperties','JevProperties$ProxySettings','JevRequest','JevRequest$Question','JevResponse','JevResponse$Answer')) {
        $taskFiles+=Join-Path $taskRoot "target/classes/com/kun/aiinterview/interview/evaluation/judge/jev/$taskClass.class"
    }
    $taskFiles+=Join-Path $taskRoot 'target/classes/com/kun/aiinterview/interview/evaluation/judge/JudgeConfiguration.class'
    $taskFiles+=Join-Path $taskPrep 'classpath.txt'
    $taskFiles+=Join-Path $taskPrep 'runner-offline-status.json'
    foreach($taskFile in $taskFiles) {$taskSeal[[IO.Path]::GetRelativePath($taskRoot,$taskFile)]=(Get-FileHash -LiteralPath $taskFile -Algorithm SHA256).Hash}
    $taskSeal | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskPrep 'seal.json') -Encoding UTF8
    Write-Host 'PREPARED. Real Jev calls=0. New human authorization, provider/account/country permission and price confirmation required.'
    exit 0
}
if($Authorization -cne 'AUTHORIZE_JEV_PROXY_ONCE_MAX_USD_0_01' -or !$ProviderAccessConfirmed -or !$PricingConfirmed -or $AllowedCountry -cnotmatch '^[A-Z]{2}$') {
    throw 'New explicit human authorization and confirmed provider account/country access and pricing are required'
}
if($ConfirmedInputUsdPerMillion -le 0 -or ($ConfirmedInputUsdPerMillion*0.065536) -gt 0.01) {throw 'Confirmed price exceeds USD0.01 planning budget'}
if([string]::IsNullOrWhiteSpace($env:JEV_API_KEY)) {throw 'Supply JEV_API_KEY in this process environment; never as a command argument'}
if(Test-Path -LiteralPath $taskAttempt) {throw 'Independent attempt already reserved; never remove its locks or retry'}
$taskSeal=Get-Content -LiteralPath (Join-Path $taskPrep 'seal.json') -Raw | ConvertFrom-Json
foreach($taskEntry in $taskSeal.PSObject.Properties) {
    if((Get-FileHash -LiteralPath (Join-Path $taskRoot $taskEntry.Name) -Algorithm SHA256).Hash -cne $taskEntry.Value) {throw 'Prepared artifacts changed; prepare again before fresh authorization'}
}
$taskOffline=Get-Content -LiteralPath (Join-Path $taskPrep 'runner-offline-status.json') -Raw | ConvertFrom-Json
if($taskOffline.status -cne 'PASS') {throw 'Runner offline tests must pass'}
$taskCp=(Join-Path $taskPrep 'classes')+';'+(Join-Path $taskRoot 'target/classes')+';'+(Get-Content (Join-Path $taskPrep 'classpath.txt') -Raw).Trim()
Write-Host 'At most 1 Jev POST through 127.0.0.1:7897; no retries or other models/database. USD0.01 is a planning cap, not a provider invoice control.'
Invoke-JevChild -Executable (Join-Path $taskJavaHome 'bin/java.exe') -WithJevKey -ChildArguments @('-Djdk.httpclient.disableRetryConnect=true','-Djdk.httpclient.enableAllMethodRetry=false','-cp',$taskCp,'JevScoreOnce',$taskAttempt,$Authorization,'true','true',$AllowedCountry,$ConfirmedInputUsdPerMillion.ToString([Globalization.CultureInfo]::InvariantCulture))
