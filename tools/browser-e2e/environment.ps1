param([ValidateSet('Prepare','Start','Services','RestartBackend','Snapshot','SnapshotAfterRegression','Stop')][string]$Mode='Prepare')
$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskRoot
$taskRunId=if($env:BROWSER_E2E_RUN_ID){$env:BROWSER_E2E_RUN_ID}else{'20261018-v3'}
if($taskRunId -notmatch '^[a-z0-9-]{1,64}$'){throw 'Invalid browser run ID'}
$taskOut=Join-Path $taskRoot ('target/browser-e2e/'+$taskRunId)
$taskJava=if($env:BROWSER_E2E_JAVA_HOME){$env:BROWSER_E2E_JAVA_HOME}elseif($env:JAVA_HOME){$env:JAVA_HOME}else{throw 'Set JAVA_HOME or BROWSER_E2E_JAVA_HOME to Java 21'}
$taskMysql=if($env:BROWSER_E2E_MYSQL_BIN){$env:BROWSER_E2E_MYSQL_BIN}else{Split-Path -Parent (Get-Command mysqld.exe -ErrorAction Stop).Source}
$taskMysql=(Resolve-Path -LiteralPath $taskMysql).Path
$taskMysqlBase=Split-Path -Parent $taskMysql
$taskNode=if($env:BROWSER_E2E_NODE){$env:BROWSER_E2E_NODE}else{(Get-Command node.exe -ErrorAction Stop).Source}
foreach($taskRequired in @("$taskJava/bin/java.exe","$taskJava/bin/javac.exe","$taskMysql/mysqld.exe","$taskMysql/mysql.exe","$taskMysql/mysqladmin.exe",$taskNode)) {
  if(-not (Test-Path -LiteralPath $taskRequired -PathType Leaf)){throw "Missing test runtime: $taskRequired"}
}
$taskCpFile=Join-Path $taskOut 'classpath.txt'
$taskPidFile=Join-Path $taskOut 'processes.json'
function Assert-FreePorts {
  foreach($taskPort in @(13326,18096,18097,15183)) {
    $taskSocket=[Net.Sockets.TcpClient]::new()
    try {
      $taskSocket.Connect('127.0.0.1',$taskPort)
      throw "Port $taskPort is already occupied; do not touch the existing service"
    } catch [Net.Sockets.SocketException] {
      # Connection refused means no existing service owns the port.
      if($_.Exception.SocketErrorCode -ne [Net.Sockets.SocketError]::ConnectionRefused){throw}
    } finally {$taskSocket.Dispose()}
  }
}
function Start-Owned([string]$Name,[string]$Exe,[string[]]$Arguments,[string]$Directory=$taskRoot) {
  $taskProcess=Start-Process -FilePath $Exe -ArgumentList $Arguments -WorkingDirectory $Directory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskOut "$Name.stdout.log") -RedirectStandardError (Join-Path $taskOut "$Name.stderr.log")
  $taskRecord=@{name=$Name;id=$taskProcess.Id;started=$taskProcess.StartTime.ToUniversalTime().ToString('o');executable=$Exe}
  $taskRecords=@(); if(Test-Path $taskPidFile){$taskRecords=@(Get-Content $taskPidFile -Raw|ConvertFrom-Json)}
  @($taskRecords+$taskRecord)|ConvertTo-Json -Depth 4|Set-Content $taskPidFile
  Write-Output "$Name PID=$($taskProcess.Id)"
}
function Invoke-Sql([string]$File) {
  $taskInfo=[Diagnostics.ProcessStartInfo]::new((Join-Path $taskMysql 'mysql.exe'))
  $taskInfo.UseShellExecute=$false; $taskInfo.RedirectStandardInput=$true
  $taskInfo.StandardInputEncoding=[Text.UTF8Encoding]::new($false)
  foreach($taskArg in @('--protocol=TCP','--host=127.0.0.1','--port=13326','--user=root','--default-character-set=utf8mb4')){$taskInfo.ArgumentList.Add($taskArg)}
  $taskProcess=[Diagnostics.Process]::Start($taskInfo)
  $taskProcess.StandardInput.Write([IO.File]::ReadAllText($File));$taskProcess.StandardInput.Close();$taskProcess.WaitForExit()
  if($taskProcess.ExitCode -ne 0){throw 'Private fixture SQL failed'}
}
if($Mode -eq 'Prepare') {
  if(Test-Path $taskOut){throw 'Run directory already exists; do not overwrite acceptance evidence'}
  Assert-FreePorts
  New-Item -ItemType Directory -Path "$taskOut/mysql-data","$taskOut/classes","$taskOut/screenshots" -Force|Out-Null
  & (Join-Path $taskMysql 'mysqld.exe') --no-defaults --initialize-insecure --basedir="$taskMysqlBase" --datadir="$taskOut/mysql-data" --console 2>&1 | Set-Content "$taskOut/mysql-init.log"
  if($LASTEXITCODE -ne 0){throw 'Private MySQL initialization failed'}
  $env:JAVA_HOME=$taskJava
  & .\mvnw.cmd -o -B -ntp -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=$taskCpFile" *> "$taskOut/compile.log"
  if($LASTEXITCODE -ne 0){throw 'Maven compilation failed'}
  $taskCp=(Join-Path $taskRoot 'target/classes')+';'+(Get-Content $taskCpFile -Raw).Trim()
  & "$taskJava/bin/javac.exe" -proc:none -encoding UTF-8 -cp $taskCp -d "$taskOut/classes" "$PSScriptRoot/BrowserE2eServer.java"
  if($LASTEXITCODE -ne 0){throw 'Harness compilation failed'}
  Write-Output 'PREPARED'
} elseif($Mode -eq 'Start') {
  if(Test-Path $taskPidFile){throw 'Processes already recorded; inspect before starting again'}
  Assert-FreePorts
  Start-Owned 'mysql' (Join-Path $taskMysql 'mysqld.exe') @('--no-defaults',"--basedir=`"$taskMysqlBase`"","--datadir=`"$taskOut/mysql-data`"",'--bind-address=127.0.0.1','--port=13326','--mysqlx=OFF','--skip-log-bin','--console')
  $taskReady=$false
  for($taskIndex=0;$taskIndex -lt 30;$taskIndex++) {
    & "$taskMysql/mysqladmin.exe" --protocol=TCP --host=127.0.0.1 --port=13326 --user=root ping 2>$null |Out-Null
    if($LASTEXITCODE -eq 0){$taskReady=$true;break}; Start-Sleep -Milliseconds 500
  }
  if(-not $taskReady){throw 'Private MySQL not ready'}
  $taskMysqlRecord=Get-Content $taskPidFile -Raw|ConvertFrom-Json|Where-Object {$_.name -eq 'mysql'}
  $taskMysqlProcess=Get-Process -Id $taskMysqlRecord.id -ErrorAction Stop
  if($taskMysqlProcess.StartTime.ToUniversalTime().Ticks -ne ([datetime]$taskMysqlRecord.started).ToUniversalTime().Ticks -or
      [IO.Path]::GetFullPath($taskMysqlRecord.executable) -ne $taskMysqlProcess.Path){throw 'Private MySQL process identity mismatch'}
  Invoke-Sql "$taskRoot/tools/rag-e2e/schema-test-only.sql"
  Invoke-Sql "$PSScriptRoot/fixtures.sql"
  & $PSCommandPath -Mode Services
} elseif($Mode -eq 'Services') {
  Start-Owned 'mock-provider' $taskNode @('tools/browser-e2e/mock-provider.mjs')
  $taskCp="$taskOut/classes;$taskRoot/target/classes;"+(Get-Content $taskCpFile -Raw).Trim()
  Start-Owned 'backend' "$taskJava/bin/java.exe" @('-cp',"`"$taskCp`"",'BrowserE2eServer')
  $env:AI_INTERVIEW_BACKEND_URL='http://127.0.0.1:18096'
  Start-Owned 'frontend' $taskNode @('node_modules/vite/bin/vite.js','--host','127.0.0.1','--port','15183','--strictPort') "$taskRoot/frontend"
} elseif($Mode -eq 'RestartBackend') {
  foreach($taskRecord in @(Get-Content $taskPidFile -Raw|ConvertFrom-Json|Where-Object {$_.name -like 'backend*'})) {
    $taskProcess=Get-Process -Id $taskRecord.id -ErrorAction SilentlyContinue
    if($taskProcess -and $taskProcess.StartTime.ToUniversalTime().Ticks -eq ([datetime]$taskRecord.started).ToUniversalTime().Ticks -and [IO.Path]::GetFullPath($taskRecord.executable) -eq $taskProcess.Path){
      Stop-Process -Id $taskProcess.Id
      if(-not $taskProcess.WaitForExit(5000)){throw 'Owned backend did not stop'}
    }
  }
  $taskCp="$taskOut/classes;$taskRoot/target/classes;"+(Get-Content $taskCpFile -Raw).Trim()
  $taskBackendName='backend-fixed-'+[DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
  Start-Owned $taskBackendName "$taskJava/bin/java.exe" @('-cp',"`"$taskCp`"",'BrowserE2eServer')
} elseif($Mode -in @('Snapshot','SnapshotAfterRegression')) {
  $taskCp="$taskOut/classes;$taskRoot/target/classes;"+(Get-Content $taskCpFile -Raw).Trim()
  $taskSnapshotArgs=@('snapshot');if($Mode -eq 'SnapshotAfterRegression'){$taskSnapshotArgs+=@('after-regression')}
  & "$taskJava/bin/java.exe" -cp $taskCp BrowserE2eServer @taskSnapshotArgs
  if($LASTEXITCODE -ne 0){throw 'Snapshot failed'}
} else {
  foreach($taskRecord in @(Get-Content $taskPidFile -Raw|ConvertFrom-Json)) {
    $taskProcess=Get-Process -Id $taskRecord.id -ErrorAction SilentlyContinue
    if($taskProcess -and $taskProcess.StartTime.ToUniversalTime().Ticks -eq ([datetime]$taskRecord.started).ToUniversalTime().Ticks -and [IO.Path]::GetFullPath($taskRecord.executable) -eq $taskProcess.Path) {
      if($taskRecord.name -eq 'mysql') {
        & "$taskMysql/mysqladmin.exe" --protocol=TCP --host=127.0.0.1 --port=13326 --user=root shutdown
      } else {Stop-Process -Id $taskProcess.Id}
      if(-not $taskProcess.WaitForExit(10000)){throw "Owned service did not exit: $($taskRecord.name)"}
    }
  }
  Write-Output 'OWNED_SERVICES_STOPPED'
}
