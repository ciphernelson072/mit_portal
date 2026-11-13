[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [switch]$Rebuild,
    [switch]$Reset
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $projectRoot

function Get-ComposeCommand {
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        $dockerVersion = docker compose version 2>$null
        if ($LASTEXITCODE -eq 0) {
            return @('docker', 'compose')
        }
    }

    if (Get-Command docker-compose -ErrorAction SilentlyContinue) {
        return @('docker-compose')
    }

    throw 'Docker is required for the local automated stack. Install Docker Desktop or the Docker CLI and try again.'
}

$composeCommand = Get-ComposeCommand

& (Join-Path $PSScriptRoot 'preflight-dev.ps1')

if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
    $envPath = Join-Path $projectRoot '.env'
    $randomBytes = New-Object byte[] 32
    $randomGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomGenerator.GetBytes($randomBytes)
    }
    finally {
        $randomGenerator.Dispose()
    }
    $localWebhookSecret = [Convert]::ToBase64String($randomBytes)
    $envLines = foreach ($line in Get-Content $envPath) {
        $updated = $line
        if ($line -match '^DB_PASSWORD=') {
            $updated = 'DB_PASSWORD=LocalDev!Pass123'
        }
        elseif ($line -match '^MYSQL_ROOT_PASSWORD=') {
            $updated = 'MYSQL_ROOT_PASSWORD=RootLocal!Pass123'
        }
        elseif ($line -match '^JWT_SECRET=') {
            $updated = 'JWT_SECRET=0123456789abcdef0123456789abcdef'
        }
        elseif ($line -match '^PAYMENT_WEBHOOK_SECRET=') {
            $updated = "PAYMENT_WEBHOOK_SECRET=$localWebhookSecret"
        }
        $updated
    }
    Set-Content -Path $envPath -Value ($envLines -join [Environment]::NewLine) -Encoding UTF8
    Write-Host 'Created .env from .env.example with local demo values.'
}

$envValues = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $projectRoot '.env')) {
    if ($line -match '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $name = $matches[1]
        $value = $matches[2].Trim()
        if ($value.Length -ge 2 -and
            (($value.StartsWith('"') -and $value.EndsWith('"')) -or
             ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        else {
            $value = ($value -replace '\s+#.*$', '').TrimEnd()
        }
        $envValues[$name] = $value
    }
}

if ($Reset) {
    if ($PSCmdlet.ShouldProcess(
            'the local SIS Compose stack and its database volume',
            'Stop containers and permanently remove Compose-managed volumes')) {
        Write-Host 'Resetting Docker stack and volumes...'
        if ($composeCommand.Count -eq 2) {
            & docker compose down -v --remove-orphans
        }
        else {
            & docker-compose down -v --remove-orphans
        }

        if ($LASTEXITCODE -ne 0) {
            throw 'Docker Compose reset failed.'
        }
    }
}

Write-Host 'Starting the database service before the application...'
if ($composeCommand.Count -eq 2) {
    & docker compose up -d db
}
else {
    & docker-compose up -d db
}
if ($LASTEXITCODE -ne 0) {
    throw 'Docker database startup failed.'
}

$databaseReady = $false
for ($attempt = 1; $attempt -le 60; $attempt++) {
    if ($composeCommand.Count -eq 2) {
        & docker compose exec -T db mysqladmin ping --silent 2>$null | Out-Null
    }
    else {
        & docker-compose exec -T db mysqladmin ping --silent 2>$null | Out-Null
    }
    if ($LASTEXITCODE -eq 0) {
        $databaseReady = $true
        break
    }
    Start-Sleep -Seconds 2
}

if ($databaseReady) {
    $mysqlPort = if ($envValues.MYSQL_PORT) { [int]$envValues.MYSQL_PORT } else { 3306 }
    $hostBackupDirectory = $envValues.BACKUPS_HOST_DIR
    if ([string]::IsNullOrWhiteSpace($hostBackupDirectory)) {
        $hostBackupDirectory = Join-Path $projectRoot 'backups'
    }
    elseif (-not [IO.Path]::IsPathRooted($hostBackupDirectory)) {
        $hostBackupDirectory = Join-Path $projectRoot $hostBackupDirectory
    }
    New-Item -ItemType Directory -Force -Path $hostBackupDirectory | Out-Null

    $dumpCommand = Get-Command mysqldump.exe, mysqldump -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $dumpCommand -and (Test-Path 'C:\laragon\bin\mysql')) {
        $dumpCommand = Get-ChildItem -Path 'C:\laragon\bin\mysql\*\bin\mysqldump.exe' -File -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            Select-Object -First 1
    }

    if ($dumpCommand -and -not [string]::IsNullOrWhiteSpace($envValues.DB_PASSWORD)) {
        $dumpPath = if ($dumpCommand.Path) { $dumpCommand.Path } else { $dumpCommand.Source }
        $previousDbPassword = $env:DB_PASSWORD
        $hadDbPassword = Test-Path Env:DB_PASSWORD
        try {
            $env:DB_PASSWORD = $envValues.DB_PASSWORD
            $env:PATH = "$(Split-Path -Parent $dumpPath);$env:PATH"
            $backupScript = Join-Path $PSScriptRoot 'backup-mysql.ps1'
            & $backupScript `
                -OutputDirectory $hostBackupDirectory `
                -StatusFile (Join-Path $hostBackupDirectory '.backup-success') `
                -ArtifactPathPrefix '/app/backups' `
                -DatabaseHost '127.0.0.1' `
                -DatabasePort $mysqlPort `
                -DatabaseName 'sis_db' `
                -DatabaseUser $(if ($envValues.DB_USER) { $envValues.DB_USER } else { 'sis_app' })
            if ($LASTEXITCODE -ne 0) {
                Write-Warning 'Database backup failed; the dashboard will not report a recent backup.'
            }
        }
        catch {
            Write-Warning "Database backup failed; the dashboard will not report a recent backup: $($_.Exception.Message)"
        }
        finally {
            if ($hadDbPassword) { $env:DB_PASSWORD = $previousDbPassword }
            else { Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue }
        }
    }
    else {
        Write-Warning 'A local mysqldump client or DB_PASSWORD was not available; the Docker database backup status will not be green.'
    }
}
else {
    Write-Warning 'Docker database did not become ready in time; the app will still start, but database backup status will not be green.'
}

Write-Host 'Starting the SIS stack with Docker Compose...'
if ($composeCommand.Count -eq 2) {
    if ($Rebuild) {
        & docker compose up -d --build --force-recreate
    }
    else {
        & docker compose up -d --build
    }
}
else {
    if ($Rebuild) {
        & docker-compose up -d --build --force-recreate
    }
    else {
        & docker-compose up -d --build
    }
}

if ($LASTEXITCODE -ne 0) {
    throw 'Docker Compose startup failed.'
}

$healthUrl = 'http://localhost:8080/actuator/health'
$healthy = $false
for ($attempt = 1; $attempt -le 60; $attempt++) {
    try {
        $response = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -eq 200) {
            $healthy = $true
            break
        }
    }
    catch {
        Start-Sleep -Seconds 2
    }
}

if (-not $healthy) {
    throw 'App did not become healthy within the timeout window. Check docker compose logs.'
}

Write-Host ''
Write-Host 'SIS is running.'
Write-Host 'Student portal: http://localhost:8080/index.html'
Write-Host 'Admin portal:  http://localhost:8080/admin-login.html'
Write-Host 'Health check:  http://localhost:8080/actuator/health'
Write-Host ''
Write-Host 'Use ops/stop-local-dev.ps1 to stop everything.'

Pop-Location
