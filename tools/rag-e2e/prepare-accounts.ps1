# Dot-source in the same window that will later run ExecuteOnce:
#   . .\tools\rag-e2e\prepare-accounts.ps1
# Offline check, no database connection:
#   pwsh -NoProfile -File .\tools\rag-e2e\prepare-accounts.ps1 -OfflineSelfTest
[CmdletBinding()]
param(
    [switch]$OfflineSelfTest
)
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'PowerShell 7 is required' }

$script:Root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$script:ToolDir = $PSScriptRoot
$script:SourceUser = 'r08_source_ro'
$script:WriterUser = 'rag_e2e_writer'
$script:HostName = '127.0.0.1'
$script:SourceSchema = 'ai_interview'
$script:TargetSchema = 'ai_interview_test'
$script:ServerUuid = '6ccac806-1bb1-11f1-b45f-107c61ba59a5'
$script:Corpus = @('question','question_scoring_point','knowledge_document','knowledge_chunk')
$script:Tables = @(
    'user','question','question_scoring_point','interview_session','interview_question','interview_answer',
    'answer_evaluation','knowledge_document','knowledge_chunk','rag_retrieval_batch','rag_hit_log',
    'interview_report','user_weakness'
)

function ConvertTo-SqlLiteral {
    param([Parameter(Mandatory)][string]$Value)
    foreach ($char in $Value.ToCharArray()) {
        if ([int]$char -lt 32) { throw 'Password contains a control character' }
    }
    return "'" + $Value.Replace('\','\\').Replace("'","''") + "'"
}

function ConvertFrom-SecurePlain {
    param([Parameter(Mandatory)][Security.SecureString]$Secure)
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Protect-Text {
    param([string]$Text, [string[]]$Secrets)
    if (-not $Text) { return '' }
    $safe = $Text
    foreach ($secret in $Secrets) {
        if ($secret -and $secret.Length -ge 1) { $safe = $safe.Replace($secret, '***') }
    }
    return $safe
}

function Get-GrantCapabilities {
    param(
        [Parameter(Mandatory)][string]$Account,
        [Parameter(Mandatory)][string[]]$Grants,
        [Parameter(Mandatory)][ValidateSet('source','writer')][string]$Kind
    )
    if ($Account -match '^(root|mysql|admin)$') { throw 'RESTRICTED_DB_ACCOUNT_REQUIRED' }
    if ($Grants -is [string]) { throw 'GRANTS_NOT_A_ROW_LIST' }
    if ($null -eq $Grants -or @($Grants).Count -eq 0) { throw 'GRANTS_QUERY_EMPTY' }
    if (@($Grants).Count -ge 65) { throw 'GRANT_LIST_TOO_LARGE' }
    $quoted = [regex]::Escape($Account)
    $recipient = "(?:``$quoted``@``(?:localhost|127\.0\.0\.1)``|'$quoted'@'(?:localhost|127\.0\.0\.1)')"
    $pattern = [regex]::new("^GRANT ([A-Za-z``, ()]+) ON (\*\.\*|``[a-z_]+``\.``[a-z_]+``) TO $recipient$")
    $schema = $(if ($Kind -eq 'writer') { $script:TargetSchema } else { $script:SourceSchema })
    $capabilities = [Collections.Generic.HashSet[string]]::new()
    foreach ($grant in $Grants) {
        $line = $grant.Trim().TrimEnd(';')
        $match = $pattern.Match($line)
        if (-not $match.Success) { throw "UNKNOWN_OR_BROAD_GRANT: $line" }
        $privileges = $match.Groups[1].Value
        $scope = $match.Groups[2].Value
        if ($privileges -eq 'USAGE') {
            if ($scope -ne '*.*') { throw 'USAGE_SCOPE' }
            continue
        }
        if ($scope -eq '*.*') { throw 'GLOBAL_PRIVILEGE_REJECTED' }
        $names = $scope.Replace('`','').Split('.')
        if ($names[0] -ne $schema -or $script:Tables -notcontains $names[1]) { throw "GRANT_SCOPE: $line" }
        foreach ($privilege in ($privileges -csplit ', ')) {
            if ($privilege -ceq 'UPDATE (`id`)') { $privilege = 'UPDATE(id)' }
            $allowed = $privilege -eq 'SELECT'
            if ($Kind -eq 'writer' -and $privilege -eq 'INSERT' -and @($script:Corpus + @('user','interview_session','interview_question','interview_answer','answer_evaluation','rag_retrieval_batch','rag_hit_log')) -contains $names[1]) {
                $allowed = $true
            }
            if ($Kind -eq 'writer' -and $privilege -eq 'UPDATE' -and @('interview_session','interview_question','interview_answer') -contains $names[1]) {
                $allowed = $true
            }
            if ($Kind -eq 'writer' -and $privilege -ceq 'UPDATE(id)' -and $names[1] -eq 'user') {
                $allowed = $true
            }
            if (-not $allowed) { throw "EXCESS_DB_PRIVILEGE: $privilege on $($names[1])" }
            [void]$capabilities.Add("$privilege@$($names[1])")
        }
    }
    if ($capabilities.Count -eq 0) { throw "NO_EFFECTIVE_PRIVILEGE lines=$(@($Grants).Count). The account has no table privilege." }
    return ,$capabilities
}

function Resolve-GrantSet {
    param($Value, [string]$Label)
    if ($null -eq $Value) { throw "$Label is missing. The privilege query did not produce a result set." }
    if ($Value -is [Collections.Generic.HashSet[string]]) {
        if ($Value.Count -eq 0) { throw "$Label is empty." }
        return $Value
    }
    $set = [Collections.Generic.HashSet[string]]::new()
    foreach ($item in @($Value)) {
        if ($null -eq $item -or [string]::IsNullOrWhiteSpace([string]$item)) { throw "$Label contains a blank privilege." }
        [void]$set.Add([string]$item)
    }
    if ($set.Count -eq 0) { throw "$Label is empty." }
    return $set
}

function Assert-SourceGrants {
    param($Capabilities)
    $actual = Resolve-GrantSet $Capabilities 'Capabilities'
    $expected = [Collections.Generic.HashSet[string]]::new()
    foreach ($table in $script:Corpus) { [void]$expected.Add("SELECT@$table") }
    $missing = @($expected | Where-Object { -not $actual.Contains($_) })
    $extra = @($actual | Where-Object { -not $expected.Contains($_) })
    if ($missing.Count) { throw ("SOURCE_SELECT_MISSING [{0}]" -f ($missing -join ',')) }
    foreach ($item in $extra) {
        if ($item -notmatch '^SELECT@[a-z_]+$' -or $script:Tables -notcontains $item.Substring(7)) {
            throw "SOURCE_GRANT_NOT_READONLY: $item"
        }
    }
    return ,([string[]]@($extra))
}

function Assert-WriterGrants {
    param($Capabilities, $Expected)
    $actual = Resolve-GrantSet $Capabilities 'Capabilities'
    $required = Resolve-GrantSet $Expected 'Expected'
    $missing = @($required | Where-Object { -not $actual.Contains($_) })
    $extra = @($actual | Where-Object { -not $required.Contains($_) })
    if ($missing.Count -or $extra.Count) {
        throw ("WRITER_GRANTS_MISMATCH missing=[{0}] extra=[{1}]" -f ($missing -join ','), ($extra -join ','))
    }
}

function Read-ProvisionStatements {
    $path = Join-Path $script:ToolDir 'provision-accounts.sql'
    $text = [IO.File]::ReadAllText($path)
    if ($text -notmatch "SIGNAL SQLSTATE '45000'") { throw 'provision SQL lost its execution guard' }
    $source = [Collections.Generic.List[string]]::new()
    $writer = [Collections.Generic.List[string]]::new()
    $section = ''
    foreach ($line in ($text -split "`r?`n")) {
        $trim = $line.Trim()
        if ($trim.StartsWith('--') -or $trim.Length -eq 0 -or $trim.StartsWith('SIGNAL ')) { continue }
        if ($trim -match "^CREATE USER 'rag_e2e_source_ro'@'$($script:HostName)'") { $section = 'source'; continue }
        if ($trim -match "^CREATE USER '$($script:WriterUser)'@'$($script:HostName)'") { $section = 'writer'; $writer.Add($trim); continue }
        if ($section -eq 'source' -and $trim -match '^GRANT ') { $source.Add($trim.TrimEnd(';')) }
        elseif ($section -eq 'writer' -and $trim -match '^GRANT ') { $writer.Add($trim) }
    }
    if ($source.Count -ne 4) { throw 'provision SQL no longer has exactly four source grants' }
    return [pscustomobject]@{ SourceGrants = $source; WriterStatements = $writer }
}

function New-WriterSql {
    param([Parameter(Mandatory)][string]$Password, [Parameter(Mandatory)]$Provision)
    $literal = ConvertTo-SqlLiteral $Password
    $lines = foreach ($statement in $Provision.WriterStatements) {
        $statement.Replace("'__PRIVATE_TEST_PASSWORD__'", $literal).TrimEnd(';') + ';'
    }
    $sql = ($lines -join "`n") + "`n"
    foreach ($statement in ($sql -split "`n")) {
        $trim = $statement.Trim()
        if ($trim.Length -eq 0) { continue }
        if ($trim -notmatch "^CREATE USER '$($script:WriterUser)'@'$($script:HostName)' IDENTIFIED BY " -and $trim -notmatch '^GRANT ') {
            throw 'writer SQL contains a statement outside CREATE USER and GRANT'
        }
    }
    foreach ($forbidden in @('__PRIVATE_', $script:SourceUser, 'SIGNAL ')) {
        if ($sql.Contains($forbidden)) { throw "writer SQL contains forbidden text: $forbidden" }
    }
    if ($sql -match '`ai_interview`') { throw 'writer SQL references the source schema' }
    return $sql
}

function Read-SchemaPlan {
    $path = Join-Path $script:ToolDir 'schema-test-only.sql'
    $text = [IO.File]::ReadAllText($path)
    if ($text -match '(?m)^\s*(DROP|ALTER|DELETE|INSERT|UPDATE|GRANT|TRUNCATE)\b') { throw 'schema SQL contains a mutation outside CREATE' }
    if ($text -match '`ai_interview`') { throw 'schema SQL references the source schema' }
    if ($text -notmatch 'CREATE DATABASE `ai_interview_test`') { throw 'schema SQL lost its database target' }
    $tables = [regex]::Matches($text, '(?m)^CREATE TABLE `([a-z_]+)`') | ForEach-Object { $_.Groups[1].Value }
    if (@($tables).Count -ne 13) { throw 'schema SQL no longer creates exactly 13 tables' }
    return [pscustomobject]@{ Text = $text; Tables = @($tables) }
}

function ConvertFrom-MysqlRows {
    param([AllowNull()][string]$Text)
    if ($null -eq $Text) { throw 'MYSQL_TEXT_NULL' }
    $rows = [Collections.Generic.List[string[]]]::new()
    foreach ($line in ($Text -split "`r?`n")) {
        if ($line.Length -eq 0) { continue }
        # PowerShell's -split delimiter, -1 does not split tab-separated mysql --batch rows.
        $rows.Add([string[]]$line.Split([char]9))
    }
    # Unary comma keeps a one-row List from being unwrapped into a string or string[].
    return ,$rows
}

function Get-RowList {
    param($Value, [string]$Label)
    if ($null -eq $Value) { throw "$Label query returned no result object" }
    if ($Value -is [Collections.Generic.List[string[]]]) { return ,$Value }
    if ($Value -is [string]) {
        $list = [Collections.Generic.List[string[]]]::new()
        $list.Add([string[]]@($Value))
        return ,$list
    }
    if ($Value -is [System.Array]) {
        $list = [Collections.Generic.List[string[]]]::new()
        if ($Value.Rank -eq 1 -and $Value.Length -gt 0 -and $Value.GetValue(0) -is [System.Array]) {
            foreach ($row in $Value) { $list.Add([string[]]@($row)) }
        } elseif ($Value -is [string[]]) {
            # A single unwrapped mysql row becomes string[]; keep it as one record.
            $list.Add([string[]]$Value)
        } else {
            foreach ($row in $Value) { $list.Add([string[]]@($row)) }
        }
        return ,$list
    }
    throw "$Label query returned an unexpected type $($Value.GetType().Name)"
}

function Get-RowFields {
    param($Row)
    if ($null -eq $Row) { throw 'MYSQL_ROW_MISSING' }
    # A one-column mysql row is unwrapped by PowerShell from string[] into a string.
    if ($Row -is [string]) { return ,([string[]]@($Row)) }
    $fields = [string[]]@($Row)
    if ($fields.Count -eq 0) { throw 'MYSQL_ROW_EMPTY' }
    return ,$fields
}

function Get-MysqlColumn {
    param($Rows, [int]$Index, [string]$Label, [switch]$AllowEmpty)
    $rowList = Get-RowList $Rows $Label
    $values = [Collections.Generic.List[string]]::new()
    foreach ($row in $rowList) {
        $fields = Get-RowFields $row
        if ($fields.Count -le $Index) { throw "$Label row is not a tab-separated record" }
        $values.Add([string]$fields[$Index])
    }
    if (-not $AllowEmpty -and $values.Count -eq 0) { throw "$Label query returned no rows" }
    return ,$values
}

function Invoke-Mysql {
    param(
        [Parameter(Mandatory)][string]$Exe,
        [Parameter(Mandatory)][string]$User,
        [Parameter(Mandatory)][string]$Password,
        [string]$Database,
        [Parameter(Mandatory)][string]$Sql
    )
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $Exe
    $start.WorkingDirectory = $script:Root
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in @('--protocol=TCP','-h',$script:HostName,'-P','3306','-u',$User,'--default-character-set=utf8mb4','--batch','--raw','--skip-column-names','--connect-timeout=5')) {
        $start.ArgumentList.Add($argument)
    }
    if ($Database) { $start.ArgumentList.Add('-D'); $start.ArgumentList.Add($Database) }
    $start.Environment['MYSQL_PWD'] = $Password
    $process = [Diagnostics.Process]::Start($start)
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.StandardInput.Write($Sql)
    if (-not $Sql.EndsWith("`n")) { $process.StandardInput.Write("`n") }
    $process.StandardInput.Close()
    $process.WaitForExit()
    $stdout = Protect-Text $stdoutTask.Result @($Password)
    $stderr = Protect-Text $stderrTask.Result @($Password)
    if ($process.ExitCode -ne 0) { throw "mysql exited $($process.ExitCode): $stderr" }
    if ($stderr -and $stderr -notmatch 'MYSQL_PWD|insecure') { Write-Host $stderr }
    return $stdout
}

function Invoke-AccountQuery {
    param(
        [Parameter(Mandatory)][string]$Exe,
        [Parameter(Mandatory)][string]$User,
        [Parameter(Mandatory)][string]$Password,
        [string]$Database,
        [Parameter(Mandatory)][string]$Sql
    )
    if ($script:MockMysql) { return (& $script:MockMysql $User $Database $Sql) }
    return Invoke-Mysql -Exe $Exe -User $User -Password $Password -Database $Database -Sql $Sql
}

function Find-MysqlClient {
    $command = Get-Command mysql -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    foreach ($candidate in @(
        'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
        'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
        'C:\Program Files\MySQL\MySQL Server 8.1\bin\mysql.exe'
    )) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    throw 'mysql client was not found on PATH or in the usual MySQL Server bin directory'
}

function Get-ExpectedWriterCapabilities {
    param($Provision)
    $grants = @($Provision.WriterStatements | Where-Object { $_ -match '^GRANT ' } | ForEach-Object { $_.TrimEnd(';') })
    return ,(Get-GrantCapabilities -Account $script:WriterUser -Grants $grants -Kind writer)
}

function Assert-Identity {
    param([string]$CurrentUser, [string]$Roles, [string]$Mandatory, [string]$ExpectedUser)
    if ($CurrentUser -ne "$ExpectedUser@$($script:HostName)") { throw "CURRENT_USER is $CurrentUser" }
    if ($Roles -ne 'NONE' -or -not [string]::IsNullOrEmpty($Mandatory)) { throw 'ROLES_NOT_ALLOWED' }
}

function Invoke-OfflineSelfTest {
    $provision = Read-ProvisionStatements
    $sourceCapabilities = Get-GrantCapabilities -Account 'rag_e2e_source_ro' -Grants $provision.SourceGrants -Kind source
    if ($sourceCapabilities -isnot [Collections.Generic.HashSet[string]]) { throw 'grant result lost its set type' }
    $null = Assert-SourceGrants $sourceCapabilities
    $schema = Read-SchemaPlan
    $expectedTables = [Collections.Generic.HashSet[string]]::new([string[]]$script:Tables)
    if ($schema.Tables.Count -ne $expectedTables.Count) { throw 'schema table count drifted' }
    foreach ($table in $schema.Tables) { if (-not $expectedTables.Contains($table)) { throw "unexpected schema table $table" } }
    $writerSql = New-WriterSql -Password "p'a\ss" -Provision $provision
    if (-not $writerSql.Contains("'p''a\\ss'")) { throw 'SQL literal escaping failed' }
    $writerCapabilities = Get-ExpectedWriterCapabilities $provision
    if ($writerCapabilities.Count -ne 28) { throw "writer capability count is $($writerCapabilities.Count)" }
    foreach ($bad in @('UPDATE', 'UPDATE (`password`)', 'UPDATE (`role`)', 'UPDATE (`status`)', 'UPDATE (`id`, `role`)')) {
        $rejected = $false
        try {
            $unsafe = @("GRANT $bad ON ``ai_interview_test``.``user`` TO ``rag_e2e_writer``@``127.0.0.1``")
            Get-GrantCapabilities -Account $script:WriterUser -Grants $unsafe -Kind writer | Out-Null
        } catch { $rejected = $true }
        if (-not $rejected) { throw "unsafe user privilege accepted: $bad" }
    }
    $broad = $provision.SourceGrants + @("GRANT INSERT ON ``$($script:SourceSchema)``.``question`` TO ``$($script:SourceUser)``@$($script:HostName)")
    $rejected = $false
    try { Get-GrantCapabilities -Account $script:SourceUser -Grants $broad -Kind source | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw 'source insert grant was accepted' }
    $contract = Get-Content -LiteralPath (Join-Path $script:ToolDir 'contract.json') -Raw | ConvertFrom-Json
    if ($contract.mysqlServerUuid -ne $script:ServerUuid) { throw 'contract server uuid drifted' }
    $probe = [Diagnostics.ProcessStartInfo]::new()
    $probe.FileName = (Get-Command pwsh).Source
    $probe.UseShellExecute = $false
    $probe.Environment['MYSQL_PWD'] = 'synthetic-not-a-secret'
    if ($env:MYSQL_PWD) { throw 'parent environment received MYSQL_PWD' }
    $scriptText = [IO.File]::ReadAllText($PSCommandPath)
    foreach ($persist in @('Set' + 'EnvironmentVariable', 'se' + 'tx ')) {
        if ($scriptText.Contains($persist)) { throw 'script can persist an environment variable' }
    }
    if ($scriptText -notmatch 'Read-Host -AsSecureString') { throw 'secure prompt missing' }
    $parsed = Get-RowList (ConvertFrom-MysqlRows (("answer_evaluation" + [char]9 + "InnoDB" + [char]9 + "BASE TABLE`n" + "user" + [char]9 + [char]9 + "BASE TABLE"))) 'parser'
    if ($parsed.Count -ne 2 -or $parsed[0].Count -ne 3 -or $parsed[0][0] -ne 'answer_evaluation' -or $parsed[0][1] -ne 'InnoDB' -or $parsed[0][2] -ne 'BASE TABLE' -or $parsed[1][1] -ne '') {
        throw 'mysql batch row parser collapsed tab-separated fields'
    }
    $single = Get-RowList (ConvertFrom-MysqlRows $script:ServerUuid) 'uuid'
    if ($single.Count -ne 1 -or (Get-RowFields $single[0])[0] -ne $script:ServerUuid) { throw 'single-column mysql row was unwrapped incorrectly' }
    $nullRejected = $false
    try { Assert-SourceGrants $null } catch { if ($_.Exception.Message -match 'Capabilities is missing') { $nullRejected = $true } }
    if (-not $nullRejected) { throw 'null Capabilities was accepted' }
    $usageOnly = @('GRANT USAGE ON *.* TO `r08_source_ro`@`127.0.0.1`')
    $emptyRejected = $false
    try { Get-GrantCapabilities -Account 'r08_source_ro' -Grants $usageOnly -Kind source | Out-Null } catch { if ($_.Exception.Message -match 'NO_EFFECTIVE_PRIVILEGE') { $emptyRejected = $true } }
    if (-not $emptyRejected) { throw 'USAGE-only grants were not reported as having no table privilege' }
    $broadSource = [Collections.Generic.List[string]]::new()
    $broadSource.Add('GRANT USAGE ON *.* TO `r08_source_ro`@`127.0.0.1`')
    foreach ($table in $script:Tables) { $broadSource.Add("GRANT SELECT ON ``ai_interview``.``$table`` TO ``r08_source_ro``@``127.0.0.1``") }
    $broadSet = Get-GrantCapabilities -Account 'r08_source_ro' -Grants @($broadSource) -Kind source
    $extraReadOnly = Assert-SourceGrants $broadSet
    if (@($extraReadOnly).Count -ne 9) { throw 'same-schema read-only grants were rejected' }
    Invoke-MockAccountScenarios
    Write-Output 'prepare-accounts offline self-test: PASS'
}

function Invoke-MockAccountScenarios {
    $contract = Get-Content -LiteralPath (Join-Path $script:ToolDir 'contract.json') -Raw | ConvertFrom-Json
    $script:MockTablesText = (($script:Tables | Sort-Object | ForEach-Object { "$_`tInnoDB`tBASE TABLE" }) -join "`n")
    $script:MockColumnsText = (($contract.sqlColumns | ForEach-Object { "$($_.table)`t$($_.column)`t$($_.type)`t$($_.nullable)`t$($_.key)`t$($_.extra)" }) -join "`n")
    $script:MockKeysText = (($contract.sqlForeignKeys | ForEach-Object { "$($_.table)`t$($_.column)`t$($_.parent)`t$($_.parentColumn)" }) -join "`n")
    $sourceLines = [Collections.Generic.List[string]]::new()
    $sourceLines.Add('GRANT USAGE ON *.* TO `r08_source_ro`@`127.0.0.1`')
    foreach ($table in $script:Tables) { $sourceLines.Add("GRANT SELECT ON ``ai_interview``.``$table`` TO ``r08_source_ro``@``127.0.0.1``") }
    $script:MockSourceText = ($sourceLines -join "`n")
    $provision = Read-ProvisionStatements
    $writerLines = [Collections.Generic.List[string]]::new()
    $writerLines.Add('GRANT USAGE ON *.* TO `rag_e2e_writer`@`127.0.0.1`')
    foreach ($statement in $provision.WriterStatements) {
        if ($statement -match '^GRANT ') {
            $writerLines.Add($statement.TrimEnd(';').Replace("'rag_e2e_writer'@'127.0.0.1'", '`rag_e2e_writer`@`127.0.0.1`'))
        }
    }
    $script:MockWriterText = ($writerLines -join "`n")
    foreach ($writerExists in @($false, $true)) {
        $script:MockLog = [Collections.Generic.List[string]]::new()
        $script:MockWriterReady = $writerExists
        $script:MockAdminUser = 'dbadmin'
        $script:MockAdminPassword = 'admin-secret'
        $script:MockSourcePassword = 'source-secret'
        $script:MockWriterPassword = 'writer-secret'
        $script:MockConfirm = { 'APPLY' }
        $script:MockMysql = {
            param($User, $Database, $Sql)
            $compact = ($Sql -replace '\s+', ' ').Trim()
            if ($compact.StartsWith('SELECT @@server_uuid')) { return $script:ServerUuid }
            if ($compact.Contains('information_schema.SCHEMATA')) { return $script:TargetSchema }
            if ($compact.Contains('FROM mysql.user')) {
                $rows = "$($script:SourceUser)`t$($script:HostName)"
                if ($script:MockWriterReady) { $rows += "`n$($script:WriterUser)`t$($script:HostName)" }
                return $rows
            }
            if ($compact.Contains('information_schema.TABLES')) { return $script:MockTablesText }
            if ($compact.Contains('information_schema.COLUMNS')) { return $script:MockColumnsText }
            if ($compact.Contains('COUNT(*) FROM information_schema.KEY_COLUMN_USAGE')) { return '0' }
            if ($compact.Contains('REFERENCED_TABLE_NAME')) { return $script:MockKeysText }
            if ($compact.StartsWith("SHOW GRANTS FOR 'r08_source_ro'")) { return $script:MockSourceText }
            if ($compact.StartsWith("SHOW GRANTS FOR 'rag_e2e_writer'")) { return $script:MockWriterText }
            if ($compact.StartsWith('SELECT CURRENT_USER()')) {
                if ($User -eq $script:SourceUser) { return "$($script:SourceUser)@$($script:HostName)`tNONE`t`t$($script:SourceSchema)" }
                if ($User -eq $script:WriterUser -and $script:MockWriterReady) { return "$($script:WriterUser)@$($script:HostName)`tNONE`t`t$($script:TargetSchema)" }
                throw 'LOGIN_QUERY_FAILED'
            }
            if ($compact.StartsWith('SHOW GRANTS FOR CURRENT_USER')) {
                if ($User -eq $script:SourceUser) { return $script:MockSourceText }
                if ($User -eq $script:WriterUser -and $script:MockWriterReady) { return $script:MockWriterText }
                throw 'LOGIN_GRANTS_EMPTY'
            }
            if ($compact.StartsWith('CREATE DATABASE')) { $script:MockLog.Add('schema'); throw 'schema DDL was not expected' }
            if ($compact.StartsWith('CREATE USER')) {
                if ($compact.Contains('rag_e2e_source_ro') -or $compact -match '\bDROP\b|\bALTER\b') { throw 'writer SQL crossed the source boundary' }
                $script:MockLog.Add('writer')
                $script:MockWriterReady = $true
                return ''
            }
            throw "UNMOCKED_SQL user=$User"
        }
        try {
            Invoke-AccountPreparation
            $mutations = @($script:MockLog)
            if ($writerExists -and $mutations.Count -ne 0) { throw 'existing writer was modified' }
            if (-not $writerExists -and (($mutations -join ',') -ne 'writer')) { throw 'missing writer was not the only mutation' }
        } finally {
            $script:MockMysql = $null
            $script:MockConfirm = $null
            foreach ($name in @('E2E_DB_USERNAME','E2E_DB_PASSWORD','E2E_SOURCE_DB_USERNAME','E2E_SOURCE_DB_PASSWORD','MYSQL_PWD')) {
                Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
            }
        }
    }
}

function Invoke-AccountPreparation {
    $mocked = $null -ne $script:MockMysql
    $mysql = if ($mocked) { 'mock-mysql' } else { Find-MysqlClient }
    $provision = Read-ProvisionStatements
    $schemaPlan = Read-SchemaPlan
    $expectedWriter = Get-ExpectedWriterCapabilities $provision
    if ($mocked) {
        $adminUser = $script:MockAdminUser
        $adminSecure = $null
        $sourceSecure = $null
        $writerSecure = $null
        $adminPassword = $script:MockAdminPassword
        $sourcePassword = $script:MockSourcePassword
        $writerPassword = $script:MockWriterPassword
    } else {
        $adminUser = Read-Host 'MySQL admin account'
        if ($adminUser -notmatch '^[A-Za-z_][A-Za-z0-9_]{0,31}$') { throw 'Admin account name is not a plain MySQL identifier' }
        $adminSecure = Read-Host 'MySQL admin password' -AsSecureString
        $sourceSecure = Read-Host "$($script:SourceUser) password" -AsSecureString
        $writerSecure = Read-Host "$($script:WriterUser) password (existing password, or the new password if that account is absent)" -AsSecureString
        $adminPassword = $null
        $sourcePassword = $null
        $writerPassword = $null
    }
    $writerSql = $null
    $exported = $false
    try {
        if (-not $mocked) {
            $adminPassword = ConvertFrom-SecurePlain $adminSecure
            $sourcePassword = ConvertFrom-SecurePlain $sourceSecure
            $writerPassword = ConvertFrom-SecurePlain $writerSecure
        }
        if (-not $adminPassword -or -not $sourcePassword -or -not $writerPassword) { throw 'A password was empty' }

        Write-Host 'stage=server-uuid'
        $uuidRows = Get-RowList (ConvertFrom-MysqlRows (Invoke-AccountQuery -Exe $mysql -User $adminUser -Password $adminPassword -Sql 'SELECT @@server_uuid')) 'server-uuid'
        if ($uuidRows.Count -ne 1) { throw 'SERVER_UUID_QUERY_FAILED' }
        $uuidFields = Get-RowFields $uuidRows[0]
        if ($uuidFields.Count -lt 1 -or [string]::IsNullOrWhiteSpace($uuidFields[0])) { throw 'SERVER_UUID_QUERY_FAILED' }
        $uuid = [string]$uuidFields[0]
        if ($uuid -ne $script:ServerUuid) { throw "MySQL server uuid is $uuid" }
        Write-Host 'stage=test-schema'
        $schemas = Get-RowList (ConvertFrom-MysqlRows (Invoke-AccountQuery -Exe $mysql -User $adminUser -Password $adminPassword -Sql "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$($script:TargetSchema)'")) 'schema'
        $schemaExists = $schemas.Count -eq 1
        Write-Host "stage=test-schema exists=$schemaExists"
        if ($schemaExists) { Assert-TestSchema -Exe $mysql -User $adminUser -Password $adminPassword }
        Write-Host 'stage=accounts'
        $accounts = Get-RowList (ConvertFrom-MysqlRows (Invoke-AccountQuery -Exe $mysql -User $adminUser -Password $adminPassword -Sql "SELECT User, Host FROM mysql.user WHERE User IN ('$($script:SourceUser)','$($script:WriterUser)')")) 'accounts'
        $sourceHost = [Collections.Generic.List[string]]::new()
        $writerHost = [Collections.Generic.List[string]]::new()
        for ($accountIndex = 0; $accountIndex -lt $accounts.Count; $accountIndex++) {
            $accountFields = Get-RowFields $accounts[$accountIndex]
            if ($accountFields.Count -lt 2) { throw 'ACCOUNT_ROW_NOT_TABULAR' }
            if ($accountFields[0] -eq $script:SourceUser) { $sourceHost.Add([string]$accountFields[1]) }
            elseif ($accountFields[0] -eq $script:WriterUser) { $writerHost.Add([string]$accountFields[1]) }
        }
        Write-Host "stage=accounts source=$($sourceHost.Count) writer=$($writerHost.Count)"
        if ($sourceHost.Count -ne 1 -or $sourceHost[0] -ne $script:HostName) { throw "$($script:SourceUser)@$($script:HostName) was not found" }
        if ($writerHost.Count -gt 1 -or ($writerHost.Count -eq 1 -and $writerHost[0] -ne $script:HostName)) { throw "$($script:WriterUser) exists on an unexpected host" }
        $writerExists = $writerHost.Count -eq 1

        Write-Host 'stage=source-grants'
        $sourceGrantText = Invoke-AccountQuery -Exe $mysql -User $adminUser -Password $adminPassword -Sql "SHOW GRANTS FOR '$($script:SourceUser)'@'$($script:HostName)'"
        $sourceGrantRows = Get-RowList (ConvertFrom-MysqlRows $sourceGrantText) 'source-grants'
        if ($sourceGrantRows.Count -eq 0) { throw 'SOURCE_GRANTS_QUERY_EMPTY' }
        $sourceGrants = Get-MysqlColumn $sourceGrantRows 0 'source-grants'
        $sourceCapabilities = Get-GrantCapabilities -Account $script:SourceUser -Grants @($sourceGrants) -Kind source
        $sourceExtra = Assert-SourceGrants $sourceCapabilities
        Write-Host "stage=source-grants lines=$($sourceGrants.Count) capabilities=$($sourceCapabilities.Count) extraReadOnly=$(@($sourceExtra).Count)"
        Assert-RestrictedLogin -Exe $mysql -User $script:SourceUser -Password $sourcePassword -Database $script:SourceSchema -ExpectedWriter:$false -Expected $null

        if ($writerExists) {
            Write-Host 'stage=writer-grants'
            $writerGrantText = Invoke-AccountQuery -Exe $mysql -User $adminUser -Password $adminPassword -Sql "SHOW GRANTS FOR '$($script:WriterUser)'@'$($script:HostName)'"
            $writerGrantRows = Get-RowList (ConvertFrom-MysqlRows $writerGrantText) 'writer-grants'
            if ($writerGrantRows.Count -eq 0) { throw 'WRITER_GRANTS_QUERY_EMPTY' }
            $writerGrants = Get-MysqlColumn $writerGrantRows 0 'writer-grants'
            $writerCapabilities = Get-GrantCapabilities -Account $script:WriterUser -Grants @($writerGrants) -Kind writer
            Assert-WriterGrants $writerCapabilities $expectedWriter
            Write-Host "stage=writer-grants lines=$($writerGrants.Count) capabilities=$($writerCapabilities.Count)"
            if ($schemaExists) {
                Assert-RestrictedLogin -Exe $mysql -User $script:WriterUser -Password $writerPassword -Database $script:TargetSchema -ExpectedWriter -Expected $expectedWriter
            }
        }

        $actions = [Collections.Generic.List[string]]::new()
        if (-not $schemaExists) {
            $actions.Add("CREATE DATABASE ``$($script:TargetSchema)`` and these tables: $($schemaPlan.Tables -join ', ')")
        }
        if (-not $writerExists) {
            $actions.Add("CREATE USER '$($script:WriterUser)'@'$($script:HostName)' and grant only the existing ai_interview_test business privileges")
        }
        if ($actions.Count -gt 0) {
            Write-Host 'Read-only checks passed. Admin changes that will run after APPLY:'
            foreach ($action in $actions) { Write-Host "- $action" }
            Write-Host 'No source account, password reset, DROP, or ALTER is included.'
            $confirmation = if ($script:MockConfirm) { & $script:MockConfirm } else { Read-Host 'Type APPLY to run only the changes above' }
            if ($confirmation -cne 'APPLY') { throw 'Stopped before any account or schema change' }
            if (-not $schemaExists) {
                Write-Host 'stage=create-test-schema'
                [void](Invoke-AccountQuery -Exe $mysql -User $adminUser -Password $adminPassword -Sql $schemaPlan.Text)
                Assert-TestSchema -Exe $mysql -User $adminUser -Password $adminPassword -AfterCreate
            }
            if (-not $writerExists) {
                Write-Host 'stage=create-writer'
                $writerSql = New-WriterSql -Password $writerPassword -Provision $provision
                [void](Invoke-AccountQuery -Exe $mysql -User $adminUser -Password $adminPassword -Sql $writerSql)
            }
        }
        Write-Host 'stage=writer-login'
        Assert-RestrictedLogin -Exe $mysql -User $script:WriterUser -Password $writerPassword -Database $script:TargetSchema -ExpectedWriter -Expected $expectedWriter

        Set-Item -Path Env:E2E_SOURCE_DB_USERNAME -Value $script:SourceUser
        Set-Item -Path Env:E2E_SOURCE_DB_PASSWORD -Value $sourcePassword
        Set-Item -Path Env:E2E_DB_USERNAME -Value $script:WriterUser
        Set-Item -Path Env:E2E_DB_PASSWORD -Value $writerPassword
        $exported = $true
        Write-Host "Phase A ready in this PowerShell process only. Source=$($script:SourceUser)@$($script:HostName); writer=$($script:WriterUser)@$($script:HostName); schema=$($script:TargetSchema)."
        Write-Host 'Run ExecuteOnce from this same window. Closing the window clears both passwords.'
    }
    finally {
        if ($adminSecure) { $adminSecure.Dispose() }
        if ($sourceSecure) { $sourceSecure.Dispose() }
        if ($writerSecure) { $writerSecure.Dispose() }
        $adminPassword = $null
        $sourcePassword = $null
        $writerPassword = $null
        $writerSql = $null
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        if (-not $exported) {
            foreach ($name in @('E2E_DB_USERNAME','E2E_DB_PASSWORD','E2E_SOURCE_DB_USERNAME','E2E_SOURCE_DB_PASSWORD')) {
                Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
            }
        }
    }
}

function Assert-TestSchema {
    param(
        [Parameter(Mandatory)][string]$Exe,
        [Parameter(Mandatory)][string]$User,
        [Parameter(Mandatory)][string]$Password,
        [switch]$AfterCreate
    )
    $stop = $(if ($AfterCreate) { 'The new test schema does not match and will not be altered.' } else { 'No DDL will be applied.' })
    $tables = Get-RowList (ConvertFrom-MysqlRows (Invoke-AccountQuery -Exe $Exe -User $User -Password $Password -Database $script:TargetSchema -Sql "SELECT TABLE_NAME, IFNULL(ENGINE,''), TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME")) 'tables'
    $expected = [Collections.Generic.HashSet[string]]::new([string[]]$script:Tables)
    if ($tables.Count -ne 13) { throw "ai_interview_test has $($tables.Count) tables; expected 13. $stop" }
    foreach ($row in $tables) {
        $fields = Get-RowFields $row
        if ($fields.Count -lt 3) { throw "table inventory row is not tabular. $stop" }
        if (-not $expected.Contains($fields[0]) -or $fields[1] -ne 'InnoDB' -or $fields[2] -ne 'BASE TABLE') {
            throw "ai_interview_test table $($fields[0]) does not match the frozen InnoDB contract. $stop"
        }
    }
    $contract = Get-Content -LiteralPath (Join-Path $script:ToolDir 'contract.json') -Raw | ConvertFrom-Json
    $columns = Get-RowList (ConvertFrom-MysqlRows (Invoke-AccountQuery -Exe $Exe -User $User -Password $Password -Database $script:TargetSchema -Sql "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, IFNULL(COLUMN_KEY,''), IFNULL(EXTRA,'') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME, ORDINAL_POSITION")) 'columns'
    $wantColumns = @($contract.sqlColumns)
    if ($columns.Count -ne $wantColumns.Count) { throw "column count $($columns.Count) does not match the contract. $stop" }
    for ($index = 0; $index -lt $columns.Count; $index++) {
        $live = Get-RowFields $columns[$index]
        if ($live.Count -lt 6) { throw "column inventory row is not tabular. $stop" }
        $want = $wantColumns[$index]
        if ($live[0] -ne $want.table -or $live[1] -ne $want.column -or $live[2] -ne $want.type -or $live[3] -ne $want.nullable -or $live[4] -ne $want.key -or $live[5] -ne $want.extra) {
            throw "column $($live[0]).$($live[1]) does not match the contract. $stop"
        }
    }
    $foreignKeys = Get-RowList (ConvertFrom-MysqlRows (Invoke-AccountQuery -Exe $Exe -User $User -Password $Password -Database $script:TargetSchema -Sql "SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME, COLUMN_NAME")) 'foreign-keys'
    $wantKeys = @($contract.sqlForeignKeys)
    if ($foreignKeys.Count -ne $wantKeys.Count) { throw "foreign-key count does not match the contract. $stop" }
    for ($index = 0; $index -lt $foreignKeys.Count; $index++) {
        $live = Get-RowFields $foreignKeys[$index]
        if ($live.Count -lt 4) { throw "foreign-key row is not tabular. $stop" }
        $want = $wantKeys[$index]
        if ($live[0] -ne $want.table -or $live[1] -ne $want.column -or $live[2] -ne $want.parent -or $live[3] -ne $want.parentColumn) {
            throw "foreign key $($live[0]).$($live[1]) does not match the contract. $stop"
        }
    }
    $cross = Get-RowList (ConvertFrom-MysqlRows (Invoke-AccountQuery -Exe $Exe -User $User -Password $Password -Database $script:TargetSchema -Sql "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_SCHEMA IS NOT NULL AND REFERENCED_TABLE_SCHEMA<>DATABASE()")) 'cross-schema'
    if ($cross.Count -ne 1) { throw "cross-schema query failed. $stop" }
    $crossFields = Get-RowFields $cross[0]
    if ($crossFields[0] -ne '0') { throw "ai_interview_test has a cross-schema foreign key. $stop" }
}

function Assert-RestrictedLogin {
    param(
        [string]$Exe,
        [string]$User,
        [string]$Password,
        [string]$Database,
        [switch]$ExpectedWriter,
        $Expected
    )
    Write-Host "stage=login user=$User"
    $identity = Get-RowList (ConvertFrom-MysqlRows (Invoke-AccountQuery -Exe $Exe -User $User -Password $Password -Database $Database -Sql "SELECT CURRENT_USER(), IFNULL(CURRENT_ROLE(),''), IFNULL(@@mandatory_roles,''), IFNULL(DATABASE(),'')")) 'login'
    if ($identity.Count -ne 1) { throw "LOGIN_QUERY_FAILED user=$User" }
    $identityFields = Get-RowFields $identity[0]
    if ($identityFields.Count -lt 4) { throw "LOGIN_QUERY_FAILED user=$User" }
    Assert-Identity -CurrentUser $identityFields[0] -Roles $identityFields[1] -Mandatory $identityFields[2] -ExpectedUser $User
    if ($identityFields[3] -ne $Database) { throw "DATABASE() is $($identityFields[3])" }
    $grantRows = Get-RowList (ConvertFrom-MysqlRows (Invoke-AccountQuery -Exe $Exe -User $User -Password $Password -Database $Database -Sql 'SHOW GRANTS FOR CURRENT_USER()')) 'login-grants'
    if ($grantRows.Count -eq 0) { throw "LOGIN_GRANTS_EMPTY user=$User" }
    $grants = Get-MysqlColumn $grantRows 0 'login-grants'
    $kind = $(if ($ExpectedWriter) { 'writer' } else { 'source' })
    $capabilities = Get-GrantCapabilities -Account $User -Grants @($grants) -Kind $kind
    if ($ExpectedWriter) { Assert-WriterGrants $capabilities $Expected } else { $null = Assert-SourceGrants $capabilities }
}

if ($OfflineSelfTest) {
    Invoke-OfflineSelfTest
    return
}
if ($MyInvocation.InvocationName -ne '.') {
    throw 'Dot-source this script so the two account passwords stay in this process only: . .\tools\rag-e2e\prepare-accounts.ps1'
}
Invoke-AccountPreparation
