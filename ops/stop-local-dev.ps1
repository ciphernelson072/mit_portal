$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $projectRoot

if (Get-Command docker -ErrorAction SilentlyContinue) {
    $dockerVersion = docker compose version 2>$null
    if ($LASTEXITCODE -eq 0) {
        docker compose down --remove-orphans
        Pop-Location
        exit 0
    }
}

if (Get-Command docker-compose -ErrorAction SilentlyContinue) {
    docker-compose down --remove-orphans
    Pop-Location
    exit 0
}

throw 'Docker is required to stop the local SIS stack.'
