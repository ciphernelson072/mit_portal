param(
    [switch]$Deploy,
    [string]$ImageTag = "mit-student-portal:render-test",
    [int]$HealthTimeoutSeconds = 300
)

$ErrorActionPreference = "Stop"
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repositoryRoot

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed with exit code $LASTEXITCODE."
    }
}

if (-not (Test-Path "render.yaml") -or -not (Test-Path "Dockerfile")) {
    throw "Run this command from the portal repository; render.yaml and Dockerfile are required."
}

Write-Host "Running static JavaScript validation and frontend unit tests..."
Invoke-CheckedCommand "node" @("scripts/validate-javascript.js")
$frontendTests = @(Get-ChildItem -Path (Join-Path $repositoryRoot "tests") -Filter "*.test.js" -File |
    ForEach-Object { $_.FullName })
if ($frontendTests.Count -eq 0) {
    throw "No frontend unit test files were found in the tests directory."
}
Invoke-CheckedCommand "node" (@("--test") + $frontendTests)

Write-Host "Checking Docker Desktop..."
Invoke-CheckedCommand "docker" @("info", "--format", "{{.ServerVersion}}")

Write-Host "Building Render image '$ImageTag'. Plain output will show the Maven or test failure if verification fails..."
Invoke-CheckedCommand "docker" @("build", "--progress=plain", "--pull", "--tag", $ImageTag, ".")

if (-not $Deploy) {
    Write-Host "Local release checks and image build passed. No Render deployment was triggered."
    return
}

$deployHook = [Environment]::GetEnvironmentVariable("RENDER_DEPLOY_HOOK_URL")
$serviceUrl = [Environment]::GetEnvironmentVariable("RENDER_SERVICE_URL")
if ([string]::IsNullOrWhiteSpace($deployHook) -or [string]::IsNullOrWhiteSpace($serviceUrl)) {
    throw "Set RENDER_DEPLOY_HOOK_URL and RENDER_SERVICE_URL in the environment before using -Deploy."
}

$gitChanges = & git status --porcelain
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect Git working tree."
}
if ($gitChanges) {
    throw "Commit and push the verified changes before deployment; the Render deploy hook deploys the connected remote branch."
}

$upstream = & git rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($upstream)) {
    throw "The current branch has no upstream. Push the reviewed branch before deployment."
}
$localCommit = & git rev-parse HEAD
$remoteCommit = & git rev-parse $upstream
if ($LASTEXITCODE -ne 0 -or $localCommit -ne $remoteCommit) {
    throw "The current commit is not the latest pushed upstream commit. Push the reviewed commit before deployment."
}

$serviceUri = [Uri]$serviceUrl
if ($serviceUri.Scheme -ne "https" -or -not [string]::IsNullOrEmpty($serviceUri.AbsolutePath.Trim("/"))) {
    throw "RENDER_SERVICE_URL must be the service HTTPS origin without a path."
}

Write-Host "Triggering Render deployment for the configured remote branch..."
Invoke-RestMethod -Method Post -Uri $deployHook | Out-Null

$healthUri = "$($serviceUri.AbsoluteUri.TrimEnd('/'))/actuator/health"
$deadline = [DateTime]::UtcNow.AddSeconds($HealthTimeoutSeconds)
do {
    try {
        $health = Invoke-RestMethod -Method Get -Uri $healthUri -TimeoutSec 10
        if ($health.status -eq "UP") {
            Write-Host "Render health check passed: $healthUri"
            return
        }
        Start-Sleep -Seconds 10
    } catch {
        Start-Sleep -Seconds 10
    }
} while ([DateTime]::UtcNow -lt $deadline)

throw "Render did not report healthy within $HealthTimeoutSeconds seconds. Check Render deploy logs."
