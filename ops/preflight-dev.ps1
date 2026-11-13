$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $projectRoot

$dockerOk = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
    try {
        docker info 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $dockerOk = $true
        }
    }
    catch {
        $dockerOk = $false
    }
}

if (-not $dockerOk) {
    Write-Warning 'Docker is not running or is not available in this environment.'
    Write-Host 'The project is configured to use a MySQL 8.0 Docker service because the bundled Laragon MySQL 8.4.x database is not compatible with the project''s Flyway + Spring Boot version set.'
    Write-Host 'Start Docker Desktop, then rerun: ./run-sis.bat docker-up'
    throw 'Docker daemon unavailable.'
}

$mysqlVersion = $null
if (Get-Command mysql -ErrorAction SilentlyContinue) {
    try {
        $mysqlVersion = (& mysql --version 2>&1 | Select-Object -First 1)
    }
    catch {
        $mysqlVersion = $null
    }
}

if ($mysqlVersion -and $mysqlVersion -match '8\.4') {
    Write-Warning 'A local MySQL 8.4 installation was detected. This project requires a MySQL 8.0-compatible schema for Flyway startup.'
    Write-Host 'Use the Dockerized MySQL 8.0 stack or replace the local database with a compatible 8.0 instance.'
}

Write-Host 'Preflight passed. Docker is available and the runtime stack is ready to start.'
Pop-Location
