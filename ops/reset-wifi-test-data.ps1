[CmdletBinding()]
param(
    [string]$HostAddress = '127.0.0.1',
    [ValidateRange(1, 65535)][int]$DatabasePort = 3306,
    [string]$DatabaseName = 'sis_wifi_test'
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$expectedUploadDirectory = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'uploads_wifi_test'))
$databaseHostPattern = '^(?:localhost|127\.0\.0\.1)$'
$databaseUrlPattern = '^jdbc:mysql://(?:localhost|127\.0\.0\.1)(?::(\d+))?/([A-Za-z0-9_]+)(?:[?;].*)?$'

if ($HostAddress -notmatch $databaseHostPattern) {
    throw 'Refusing cleanup: -HostAddress must be localhost or 127.0.0.1.'
}
if ($DatabaseName -notmatch '^[A-Za-z0-9_]+_test$') {
    throw 'Refusing cleanup: the database name must end in _test.'
}
if ([string]::IsNullOrWhiteSpace($env:DB_URL)) {
    throw 'Set DB_URL to the same local MySQL _test database specified by -DatabaseName.'
}
$databaseUrlMatch = [regex]::Match($env:DB_URL, $databaseUrlPattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
if (-not $databaseUrlMatch.Success) {
    throw 'Set DB_URL to the same local MySQL _test database specified by -DatabaseName.'
}
$configuredPort = if ([string]::IsNullOrEmpty($databaseUrlMatch.Groups[1].Value)) { 3306 } else { [int]$databaseUrlMatch.Groups[1].Value }
if ($databaseUrlMatch.Groups[2].Value -cne $DatabaseName -or $configuredPort -ne $DatabasePort) {
    throw "DB_URL targets '$($databaseUrlMatch.Groups[2].Value)' on port $configuredPort; expected '$DatabaseName' on port $DatabasePort."
}
if ([string]::IsNullOrWhiteSpace($env:DB_USER) -or [string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
    throw 'Set DB_USER and DB_PASSWORD for the dedicated local test database account.'
}
if ([string]::IsNullOrWhiteSpace($env:UPLOAD_DIR)) {
    throw "Set UPLOAD_DIR to exactly $expectedUploadDirectory."
}
$uploadDirectory = [System.IO.Path]::GetFullPath($env:UPLOAD_DIR)
if (-not [string]::Equals($uploadDirectory, $expectedUploadDirectory, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing cleanup: UPLOAD_DIR must be exactly $expectedUploadDirectory."
}
if (Test-Path -LiteralPath $expectedUploadDirectory) {
    $uploadItem = Get-Item -LiteralPath $expectedUploadDirectory -Force
    if (-not $uploadItem.PSIsContainer -or
        ($uploadItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'Refusing cleanup: uploads_wifi_test must be a real directory, not a file or reparse point.'
    }
}

$confirmation = Read-Host "This permanently removes every table and test upload in $DatabaseName. Type the database name to continue"
if ($confirmation -cne $DatabaseName) {
    Write-Output 'Cleanup cancelled; no data was changed.'
    return
}

Push-Location $projectRoot
$credentialFile = $null
$sqlFile = Join-Path $env:TEMP ("wifi-test-reset-" + [guid]::NewGuid().ToString('N') + '.sql')
$adminPassword = $null
$secureAdminPassword = $null
$passwordPointer = [IntPtr]::Zero
$previousEnvironment = @{
    SAMPLE_DATA_ENABLED = $env:SAMPLE_DATA_ENABLED
    SERVER_ADDRESS = $env:SERVER_ADDRESS
    SERVER_PORT = $env:SERVER_PORT
    SPRING_PROFILES_ACTIVE = $env:SPRING_PROFILES_ACTIVE
    SPRING_APPLICATION_JSON = $env:SPRING_APPLICATION_JSON
    UPLOAD_DIR = $env:UPLOAD_DIR
}

try {
    & .\mvnw.cmd -q -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw 'Application package build failed; no test data was removed.'
    }

    $mysqlCommand = Get-Command mysql -CommandType Application -ErrorAction Stop |
        Select-Object -First 1
    . (Join-Path $PSScriptRoot 'mysql-credentials.ps1')
    $credentialFile = New-MySqlCredentialOptionFile
    $mysqlBaseArguments = @(
        "--defaults-extra-file=$credentialFile"
        "--host=$HostAddress"
        "--port=$DatabasePort"
        "--user=$env:DB_USER"
        "--database=$DatabaseName"
        '--batch'
        '--skip-column-names'
    )

    $tableQuery = "SELECT TABLE_NAME FROM information_schema.tables WHERE TABLE_SCHEMA = '$DatabaseName' AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME"
    $tables = @(& $mysqlCommand.Source @mysqlBaseArguments "--execute=$tableQuery")
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not list test database tables; no test data was removed.'
    }

    $adminUsername = (Read-Host 'Administrator username to keep').Trim()
    $secureAdminPassword = Read-Host 'New administrator password (minimum 12 characters)' -AsSecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureAdminPassword)
    $adminPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    if ($adminUsername.Length -lt 3 -or $adminUsername.Length -gt 64 -or
        $adminPassword.Length -lt 12 -or
        $adminUsername.Equals($adminPassword, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Administrator username/password did not meet the required length or separation rules.'
    }

    $quotedTables = @($tables | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        ForEach-Object { '`' + ([string]$_).Replace('`', '``') + '`' })
    if ($quotedTables.Count -eq 0) {
        throw 'The test database has no tables; refusing to continue because it may not be the expected database.'
    }

    $dropSql = "SET FOREIGN_KEY_CHECKS=0;`r`nDROP TABLE IF EXISTS " +
        ($quotedTables -join ',') + ";`r`nSET FOREIGN_KEY_CHECKS=1;`r`n"
    [System.IO.File]::WriteAllText($sqlFile, $dropSql, [System.Text.UTF8Encoding]::new($false))
    $null = Get-Content -LiteralPath $sqlFile -Raw | & $mysqlCommand.Source @mysqlBaseArguments
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed while clearing the isolated test schema. Do not use this database until it has been reinitialized.'
    }

    $env:SAMPLE_DATA_ENABLED = 'false'
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:SERVER_PORT = '0'
    $env:SPRING_PROFILES_ACTIVE = 'dev'
    $env:SPRING_APPLICATION_JSON = @{
        sis = @{
            'test-admin-bootstrap' = @{
                enabled = $true
                username = $adminUsername
                password = $adminPassword
            }
        }
    } | ConvertTo-Json -Depth 5 -Compress

    & java -jar (Join-Path $projectRoot 'target\sis-0.0.1-SNAPSHOT.jar')
    if ($LASTEXITCODE -ne 0) {
        throw 'Schema migration or admin bootstrap failed. The disposable database was cleared; inspect the application error before retrying.'
    }

    $verification = @(& $mysqlCommand.Source @mysqlBaseArguments `
        '--execute=SELECT COUNT(*), COALESCE(SUM(role = ''ADMIN'' AND is_active = 1), 0) FROM users')
    $verificationExitCode = $LASTEXITCODE
    if ($verificationExitCode -ne 0 -or $verification.Count -ne 1 -or
        [string]::IsNullOrWhiteSpace([string]$verification[0]) -or
        $verification[0].Trim() -ne "1`t1") {
        throw 'Post-cleanup verification failed: expected exactly one active ADMIN user and no other login accounts.'
    }

    $studentCount = @(& $mysqlCommand.Source @mysqlBaseArguments '--execute=SELECT COUNT(*) FROM students')
    $studentCountExitCode = $LASTEXITCODE
    $teacherCount = @(& $mysqlCommand.Source @mysqlBaseArguments '--execute=SELECT COUNT(*) FROM teachers')
    $teacherCountExitCode = $LASTEXITCODE
    if ($studentCountExitCode -ne 0 -or $studentCount.Count -ne 1 -or
        $teacherCountExitCode -ne 0 -or $teacherCount.Count -ne 1 -or
        $studentCount[0].Trim() -ne '0' -or $teacherCount[0].Trim() -ne '0') {
        throw 'Post-cleanup verification failed: student or teacher records remain.'
    }

    if (Test-Path -LiteralPath $expectedUploadDirectory) {
        Remove-Item -LiteralPath $expectedUploadDirectory -Recurse -Force
    }
    Write-Output 'Test cleanup complete: Flyway schema retained, exactly one active admin login remains, student/teacher records are empty, and test uploads were removed.'
}
finally {
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    $adminPassword = $null
    if ($secureAdminPassword) {
        $secureAdminPassword.Dispose()
    }
    $secureAdminPassword = $null
    foreach ($name in $previousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
    if ($credentialFile) {
        Remove-MySqlCredentialOptionFile -OptionFile $credentialFile
    }
    if (Test-Path -LiteralPath $sqlFile) {
        Remove-Item -LiteralPath $sqlFile -Force
    }
    Pop-Location
}
