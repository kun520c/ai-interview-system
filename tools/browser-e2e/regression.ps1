param([ValidatePattern('^[a-z0-9-]+$')][string]$ReportLabel='backend-full-serial')
$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $taskRoot
$taskRunId=if($env:BROWSER_E2E_RUN_ID){$env:BROWSER_E2E_RUN_ID}else{'20261018-v3'}
if($taskRunId -notmatch '^[a-z0-9-]{1,64}$'){throw 'Invalid browser run ID'}
$taskOut=Join-Path $taskRoot ('target/browser-e2e/'+$taskRunId)
$taskTemporaryPom=Join-Path $taskRoot '.browser-e2e-regression.pom.xml'
if(Test-Path $taskTemporaryPom){throw 'Temporary regression POM already exists'}
[xml]$taskPom=Get-Content -LiteralPath (Join-Path $taskRoot 'pom.xml') -Raw
$taskNamespace=$taskPom.DocumentElement.NamespaceURI
$taskPlugins=$taskPom.project.build.plugins
$taskPlugin=$taskPom.CreateElement('plugin',$taskNamespace)
foreach($taskPair in @(@('groupId','org.apache.maven.plugins'),@('artifactId','maven-surefire-plugin'))) {
  $taskElement=$taskPom.CreateElement($taskPair[0],$taskNamespace);$taskElement.InnerText=$taskPair[1];$taskPlugin.AppendChild($taskElement)|Out-Null
}
$taskConfiguration=$taskPom.CreateElement('configuration',$taskNamespace)
foreach($taskPair in @(@('reportsDirectory',(Join-Path $taskOut "$ReportLabel-reports")),@('argLine','-XX:+UseSerialGC -Xmx768m'))) {
  $taskElement=$taskPom.CreateElement($taskPair[0],$taskNamespace);$taskElement.InnerText=$taskPair[1];$taskConfiguration.AppendChild($taskElement)|Out-Null
}
$taskPlugin.AppendChild($taskConfiguration)|Out-Null;$taskPlugins.AppendChild($taskPlugin)|Out-Null
try {
  $taskPom.Save($taskTemporaryPom)
  if($env:BROWSER_E2E_JAVA_HOME){$env:JAVA_HOME=$env:BROWSER_E2E_JAVA_HOME}
  if(-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath "$env:JAVA_HOME/bin/javac.exe")){throw 'Set JAVA_HOME or BROWSER_E2E_JAVA_HOME to Java 21'}
  $env:TEST_DB_URL='jdbc:mysql://127.0.0.1:13326/ai_interview_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
  $env:TEST_DB_USERNAME='root';$env:TEST_DB_PASSWORD='';$env:DB_USERNAME='root';$env:DB_PASSWORD=''
  Get-ChildItem Env: | Where-Object { $_.Name -like 'RUN_REAL_*' -or $_.Name -eq 'MILVUS_ENABLED' } | Remove-Item
  & .\mvnw.cmd -B -ntp -f $taskTemporaryPom '-Dspring.datasource.password=' test *> "$taskOut/$ReportLabel.log"
  $taskExit=$LASTEXITCODE
} finally {Remove-Item -LiteralPath $taskTemporaryPom}
Get-Content "$taskOut/$ReportLabel.log" -Tail 18
exit $taskExit
