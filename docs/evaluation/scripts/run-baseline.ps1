param(
    [ValidateSet('inventory', 'rag', 'judge', 'e2e', 'pipeline', 'recovery', 'regression', 'focused')]
    [string]$Phase = 'inventory',
    [ValidateSet('existing', 'expanded')]
    [string]$Dataset = 'existing',
    [switch]$ValidateEnvironmentOnly
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $projectRoot
$ordinaryTests = $Phase -in @('regression', 'focused')
$profile = if ($ordinaryTests) { 'test' } else { 'evaluation' }
$database = if ($ordinaryTests) { 'ai_interview_test' } else { 'ai_interview_evaluation' }
$collection = if ($ordinaryTests) { 'knowledge_chunk_vectors_test' } else { 'knowledge_chunk_vectors_evaluation' }
$prefix = if ($ordinaryTests) { 'TEST' } else { 'EVALUATION' }
$settingNames = @('DB_USERNAME','DB_PASSWORD','JWT_SECRET',
    'EMBEDDING_API_KEY','EMBEDDING_BASE_URL','EMBEDDING_MODEL',
    'EMBEDDING_DIMENSION','EMBEDDING_PROFILE_VERSION','JEV_API_KEY','JEV_MODEL',
    'DEEPSEEK_API_KEY','DEEPSEEK_BASE_URL',
    "$($prefix)_DB_URL","$($prefix)_DB_USERNAME","$($prefix)_DB_PASSWORD",
    "$($prefix)_MILVUS_COLLECTION_NAME","$($prefix)_MILVUS_DATABASE_NAME")
$changedNames = @($settingNames + @('JAVA_HOME','PATH','SPRING_PROFILES_ACTIVE',
    'SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD',
    'MILVUS_COLLECTION_NAME','MILVUS_DATABASE_NAME','MILVUS_ENABLED',
    'DEEPSEEK_ENABLED','AI_JUDGE_PROVIDER','RUN_REAL_AI_BASELINE'))
$changedNames += @(Get-ChildItem Env: | Where-Object Name -Like 'RUN_REAL_*' | ForEach-Object Name)
$previous = @{}
foreach ($name in ($changedNames | Select-Object -Unique)) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    # Existing credentials are read into this process only; local DB_URL is never imported.
    foreach ($name in $settingNames) {
        if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) {
            $value = [Environment]::GetEnvironmentVariable($name, 'User')
            if ($value) { [Environment]::SetEnvironmentVariable($name, $value, 'Process') }
        }
    }
    $jdbcUrl = [Environment]::GetEnvironmentVariable("$($prefix)_DB_URL", 'Process')
    if (-not $jdbcUrl) {
        $jdbcUrl = "jdbc:mysql://localhost:3306/$($database)?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
    }
    if (-not $jdbcUrl.StartsWith('jdbc:mysql://')) { throw 'Isolated MySQL JDBC URL required' }
    $parsedUrl = [Uri]$jdbcUrl.Substring(5)
    if (-not $parsedUrl.Host -or $parsedUrl.AbsolutePath -cne "/$database") {
        throw "Refusing to run: expected isolated schema $database"
    }
    if ($env:SPRING_DATASOURCE_URL -and $env:SPRING_DATASOURCE_URL -cne $jdbcUrl) {
        throw 'Refusing conflicting SPRING_DATASOURCE_URL override'
    }
    $configuredCollection = [Environment]::GetEnvironmentVariable("$($prefix)_MILVUS_COLLECTION_NAME", 'Process')
    if ($configuredCollection -and $configuredCollection -cne $collection) {
        throw "Refusing to run: expected isolated collection $collection"
    }
    if ($env:MILVUS_COLLECTION_NAME -and $env:MILVUS_COLLECTION_NAME -cne $collection) {
        throw 'Refusing conflicting MILVUS_COLLECTION_NAME override'
    }
    $env:SPRING_PROFILES_ACTIVE = $profile
    $env:SPRING_DATASOURCE_URL = $jdbcUrl
    $env:SPRING_DATASOURCE_USERNAME = [Environment]::GetEnvironmentVariable("$($prefix)_DB_USERNAME", 'Process')
    if (-not $env:SPRING_DATASOURCE_USERNAME) { $env:SPRING_DATASOURCE_USERNAME = $env:DB_USERNAME }
    $env:SPRING_DATASOURCE_PASSWORD = [Environment]::GetEnvironmentVariable("$($prefix)_DB_PASSWORD", 'Process')
    if (-not $env:SPRING_DATASOURCE_PASSWORD) { $env:SPRING_DATASOURCE_PASSWORD = $env:DB_PASSWORD }
    $env:MILVUS_COLLECTION_NAME = $collection
    $env:MILVUS_DATABASE_NAME = [Environment]::GetEnvironmentVariable("$($prefix)_MILVUS_DATABASE_NAME", 'Process')
    if (-not $env:MILVUS_DATABASE_NAME) { $env:MILVUS_DATABASE_NAME = 'default' }
    if ($ValidateEnvironmentOnly) {
        [pscustomobject]@{
            Profile=$profile; MySqlHost=$parsedUrl.Host; MySqlPort=$parsedUrl.Port
            MySqlDatabase=$database; MilvusDatabase=$env:MILVUS_DATABASE_NAME
            MilvusCollection=$collection; Credentials='***REDACTED***'; Validation='PASS'
        } | ConvertTo-Json
        exit 0
    }
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
    Get-ChildItem Env: | Where-Object Name -Like 'RUN_REAL_*' | ForEach-Object {
        Remove-Item -LiteralPath ('Env:' + $_.Name) -ErrorAction SilentlyContinue
    }
    foreach ($name in @('MILVUS_ENABLED','DEEPSEEK_ENABLED','AI_JUDGE_PROVIDER')) {
        Remove-Item -LiteralPath ('Env:' + $name) -ErrorAction SilentlyContinue
    }
    if ($ordinaryTests) {
        foreach ($name in @('JWT_SECRET','EMBEDDING_BASE_URL','EMBEDDING_API_KEY','JEV_API_KEY','DEEPSEEK_API_KEY')) {
            Remove-Item -LiteralPath ('Env:' + $name) -ErrorAction SilentlyContinue
        }
    } elseif (-not $env:DEEPSEEK_API_KEY) {
        # Captured credential from the existing official provider; never printed or persisted.
        $providerKey = & 'D:\python\python.exe' -c "import pathlib,tomllib,urllib.parse; e=tomllib.loads((pathlib.Path.home()/'.codex/config.toml').read_text(encoding='utf-8')).get('shell_environment_policy',{}).get('set',{}); print(e.get('ANTHROPIC_AUTH_TOKEN','') if urllib.parse.urlparse(e.get('ANTHROPIC_BASE_URL','')).hostname=='api.deepseek.com' else '')"
        if ($providerKey) { $env:DEEPSEEK_API_KEY = $providerKey.Trim() }
    }
    New-Item -ItemType Directory -Force -Path 'target/evaluation' | Out-Null
    if ($Phase -eq 'regression') {
        & .\mvnw.cmd -B -ntp test *> 'target/evaluation/regression.log'
    } elseif ($Phase -eq 'focused') {
        & .\mvnw.cmd -B -ntp '-Dtest=JevClientTest,JevJudgeProviderTest,JevScoreMappingTest,JudgeConfigurationTest,LlmEvaluationValidatorTest' test *> 'target/evaluation/focused.log'
    } else {
        $env:RUN_REAL_AI_BASELINE = 'true'
        $env:DEEPSEEK_ENABLED = 'true'
        $env:MILVUS_ENABLED = 'true'
        $env:AI_JUDGE_PROVIDER = if ($env:JEV_API_KEY) { 'jev' } else { 'deepseek' }
        & .\mvnw.cmd -B -ntp '-Dtest=AiSystemBaselineEvaluationTest' '-Dspring.profiles.active=evaluation' "-Devaluation.phase=$Phase" "-Devaluation.dataset=$Dataset" test *> "target/evaluation/$Phase.log"
    }
    $runExitCode = $LASTEXITCODE
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}
Select-String -Path "target/evaluation/$Phase.log" -Pattern 'Tests run:|BUILD SUCCESS|BUILD FAILURE|BASELINE_' | ForEach-Object Line
exit $runExitCode
