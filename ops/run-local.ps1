[CmdletBinding()]
param(
    [ValidateSet('maven', 'jar')]
    [string]$Mode = 'maven',
    [string]$ServerAddress = '127.0.0.1',
    [int]$Port = 8080,
    [switch]$ValidateMailConfiguration
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $projectRoot

try {
    $runtimeVariables = @(
        'MAIL_ENABLED',
        'MAIL_PROVIDER',
        'MAIL_API_KEY',
        'MAIL_FROM_EMAIL',
        'MAIL_FROM_NAME',
        'MAIL_HOST',
        'MAIL_PORT',
        'MAIL_USERNAME',
        'MAIL_PASSWORD',
        'MAIL_STARTTLS_ENABLE',
        'MAIL_STARTTLS_REQUIRED',
        'MAIL_SMTP_SSL_ENABLE',
        'AUTH_TRUSTED_PROXY_IPS',
        'BACKUP_STATUS_FILE',
        'UPLOAD_BACKUP_STATUS_FILE',
        'MFA_ENABLED',
        'MFA_ENCRYPTION_KEY'
    )
    $envFile = Join-Path $projectRoot '.env'

    if (Test-Path -LiteralPath $envFile) {
        foreach ($line in Get-Content -LiteralPath $envFile) {
            if ($line -notmatch '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
                continue
            }

            $name = $matches[1]
            if ($name -notin $runtimeVariables) {
                continue
            }

            if ($null -ne [Environment]::GetEnvironmentVariable($name, 'Process')) {
                continue
            }

            $value = $matches[2].Trim()
            if ($value.Length -ge 2 -and
                (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                 ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            else {
                $value = ($value -replace '\s+#.*$', '').TrimEnd()
            }

            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }

    $env:SERVER_ADDRESS = $ServerAddress
    $env:SERVER_PORT = [string]$Port
    if ([string]::IsNullOrWhiteSpace($env:AUTH_TRUSTED_PROXY_IPS) -and
        $ServerAddress -in @('127.0.0.1', 'localhost', '::1')) {
        $env:AUTH_TRUSTED_PROXY_IPS = '127.0.0.1,::1,::ffff:127.0.0.1'
    }
    if ([string]::IsNullOrWhiteSpace($env:BACKUP_STATUS_FILE)) {
        $env:BACKUP_STATUS_FILE = Join-Path $projectRoot 'backups\.backup-success'
    }
    if ([string]::IsNullOrWhiteSpace($env:UPLOAD_BACKUP_STATUS_FILE)) {
        $env:UPLOAD_BACKUP_STATUS_FILE = Join-Path $projectRoot 'backups\.uploads-backup-success'
    }

    $localMysqlServer = $null
    $databasePort = if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }
    $mysqlListeners = Get-NetTCPConnection -LocalPort $databasePort -State Listen -ErrorAction SilentlyContinue
    foreach ($listener in $mysqlListeners) {
        $candidateProcess = Get-CimInstance Win32_Process `
            -Filter "ProcessId = $($listener.OwningProcess)" -ErrorAction SilentlyContinue
        if ($candidateProcess.Name -eq 'mysqld.exe') {
            $localMysqlServer = [pscustomobject]@{
                Listener = $listener
                Process = $candidateProcess
            }
            break
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_URL) -and $localMysqlServer) {
        $localDatabaseHost = if ($localMysqlServer.Listener.LocalAddress -match ':') {
            '::1'
        }
        else {
            $localMysqlServer.Listener.LocalAddress
        }
        if ($localMysqlServer.Listener.LocalAddress -in @('0.0.0.0', '::')) {
            $localDatabaseHost = if ($localMysqlServer.Listener.LocalAddress -eq '::') { '::1' } else { '127.0.0.1' }
        }
        $jdbcHost = if ($localDatabaseHost -match ':') { "[$localDatabaseHost]" } else { $localDatabaseHost }
        $env:DB_URL = "jdbc:mysql://$jdbcHost`:$databasePort/sis_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        Write-Host "Using the local MySQL listener at $localDatabaseHost`:$databasePort."
    }

    if ([string]::IsNullOrWhiteSpace($env:DB_URL) -or $env:DB_URL -match '^jdbc:h2:') {
        $env:DB_URL = 'jdbc:h2:mem:sis_local;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        $env:DB_USER = 'sa'
        if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) { $env:DB_PASSWORD = '' }
        $env:DB_DRIVER_CLASS_NAME = 'org.h2.Driver'
        $env:DDL_AUTO = 'create-drop'
        $env:FLYWAY_ENABLED = 'false'
        $env:MAIL_ENABLED = 'false'
        Write-Host 'No MySQL server was detected; starting the portal in local H2 demo mode with Flyway and outbound mail disabled so the JPA schema can bootstrap cleanly.'
    }

    if ($ValidateMailConfiguration) {
        $mailEnabled = $env:MAIL_ENABLED -eq 'true'
        $mailProvider = if ([string]::IsNullOrWhiteSpace($env:MAIL_PROVIDER)) { 'smtp' } else { $env:MAIL_PROVIDER.Trim().ToLowerInvariant() }
        $requiredMailSettings = switch ($mailProvider) {
            'smtp' { @('MAIL_HOST', 'MAIL_USERNAME', 'MAIL_PASSWORD'); break }
            'resend' { @('MAIL_API_KEY', 'MAIL_FROM_EMAIL'); break }
            default { throw 'MAIL_PROVIDER must be either smtp or resend.' }
        }
        $missing = $requiredMailSettings |
            Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_, 'Process')) }

        Write-Output ('MAIL_ENABLED=' + $mailEnabled)
        Write-Output ('MAIL_PROVIDER=' + $mailProvider)
        Write-Output ('Mail credentials complete=' + ($missing.Count -eq 0))
        if ($mailEnabled -and $missing.Count -gt 0) {
            throw ('Mail is enabled but required ' + $mailProvider + ' settings are missing: ' + ($missing -join ', '))
        }
        return
    }

    $backupDirectory = Join-Path $projectRoot 'backups'
    $backupMarker = $env:BACKUP_STATUS_FILE
    $backupIsRecent = $false
    if (Test-Path -LiteralPath $backupMarker -PathType Leaf) {
        try {
            $marker = Get-Content -LiteralPath $backupMarker -Raw | ConvertFrom-Json
            $completedAt = [DateTimeOffset]::Parse($marker.completedAt)
            $artifact = [string]$marker.artifact
            $checksum = [string]$marker.sha256
            $sidecar = "$artifact.sha256"
            if ($completedAt -le [DateTimeOffset]::UtcNow -and
                ([DateTimeOffset]::UtcNow - $completedAt).TotalHours -lt 24 -and
                $checksum -match '^[A-Fa-f0-9]{64}$' -and
                (Test-Path -LiteralPath $artifact -PathType Leaf) -and
                (Test-Path -LiteralPath $sidecar -PathType Leaf) -and
                (Get-Content -LiteralPath $sidecar -Raw).Trim() -eq $checksum -and
                (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash -eq $checksum) {
                $backupIsRecent = $true
            }
        }
        catch {
            Write-Warning "Existing database backup marker is invalid; a new backup will be attempted."
        }
    }

    if (-not $backupIsRecent) {
        $databaseHost = if ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' }
        $databasePort = if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }
        $databaseName = if ($env:DB_NAME) { $env:DB_NAME } else { 'sis_db' }
        $databaseUser = if ($env:DB_USER) { $env:DB_USER } else { 'root' }

        if ($env:DB_URL -match '^jdbc:mysql://\[([^\]]+)\](?::(\d+))?/([^?;]+)') {
            $databaseHost = $matches[1]
            if ($matches[2]) { $databasePort = [int]$matches[2] }
            $databaseName = $matches[3]
        }
        elseif ($env:DB_URL -match '^jdbc:mysql://([^:/?]+)(?::(\d+))?/([^?;]+)') {
            $databaseHost = $matches[1]
            if ($matches[2]) { $databasePort = [int]$matches[2] }
            $databaseName = $matches[3]
        }

        $mysqlDumpPath = $null
        $mysqlServer = $null
        $mysqlListeners = Get-NetTCPConnection -LocalPort $databasePort -State Listen -ErrorAction SilentlyContinue
        foreach ($listener in $mysqlListeners) {
            $candidateProcess = Get-CimInstance Win32_Process `
                -Filter "ProcessId = $($listener.OwningProcess)" -ErrorAction SilentlyContinue
            if ($candidateProcess.Name -eq 'mysqld.exe') {
                $mysqlServer = [pscustomobject]@{ Listener = $listener; Process = $candidateProcess }
                break
            }
        }
        if ($mysqlServer -and $mysqlServer.Process.ExecutablePath) {
                $candidate = Join-Path (Split-Path -Parent $mysqlServer.Process.ExecutablePath) 'mysqldump.exe'
                if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                    $mysqlDumpPath = $candidate
                }
        }
        if (-not $mysqlDumpPath) {
            $mysqlDumpCommand = Get-Command mysqldump.exe, mysqldump -ErrorAction SilentlyContinue |
                Select-Object -First 1
            if ($mysqlDumpCommand) { $mysqlDumpPath = $mysqlDumpCommand.Source }
        }

        if ($mysqlDumpPath) {
            $mysqlBin = Split-Path -Parent $mysqlDumpPath
            $env:PATH = "$mysqlBin;$env:PATH"
            if ($mysqlServer -and $databaseHost -eq '127.0.0.1' -and
                $mysqlServer.Listener.LocalAddress -eq '::') {
                $databaseHost = '::1'
            }
            $backupScript = Join-Path $PSScriptRoot 'backup-mysql.ps1'
            $backupArguments = @{
                OutputDirectory = $backupDirectory
                DatabaseHost = $databaseHost
                DatabasePort = $databasePort
                DatabaseName = $databaseName
                DatabaseUser = $databaseUser
                StatusFile = $backupMarker
            }
            if ([string]::IsNullOrEmpty($env:DB_PASSWORD)) {
                $backupArguments.AllowEmptyPassword = $true
            }
            try {
                & $backupScript @backupArguments
            }
            catch {
                Write-Warning "Database backup did not complete; the dashboard will show it as unavailable: $($_.Exception.Message)"
            }
        }
        else {
            Write-Warning "mysqldump was not found for the configured local database; the database backup status will not be green."
        }
    }

    if ($Mode -eq 'jar') {
        $jarPath = Join-Path $projectRoot 'target\sis-0.0.1-SNAPSHOT.jar'
        & java -jar $jarPath "--server.address=$ServerAddress" "--server.port=$Port"
    }
    else {
        $mavenWrapper = Join-Path $projectRoot 'mvnw.cmd'
        $springArguments = "--server.address=$ServerAddress --server.port=$Port"
        & $mavenWrapper 'spring-boot:run' "-Dspring-boot.run.arguments=$springArguments"
    }

    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
finally {
    Pop-Location
}
