[CmdletBinding()]
param(
    [ValidateRange(1024, 65535)]
    [int]$Port = 18081,
    [string]$TestName
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$jarPath = Join-Path $projectRoot 'target\sis-0.0.1-SNAPSHOT.jar'
$javaCommand = Get-Command 'java.exe' -ErrorAction SilentlyContinue
if (-not $javaCommand) {
    $javaCommand = Get-Command 'java' -ErrorAction SilentlyContinue
}

if (-not $javaCommand) {
    throw 'JDK 25 is required to build and run the isolated browser tests.'
}
$previousErrorActionPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $javaVersionOutput = & $javaCommand.Source -version 2>&1 | Out-String
} finally {
    $ErrorActionPreference = $previousErrorActionPreference
}
if ($LASTEXITCODE -ne 0 -or $javaVersionOutput -notmatch 'version "25(?:\.|")') {
    throw "JDK 25 is required to build and run the isolated browser tests. Detected: $($javaVersionOutput.Trim())"
}

$existingListener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
if ($existingListener) {
    throw "Port $Port is already in use. Choose an unused test port; the runner will not stop existing processes."
}

Push-Location $projectRoot
$applicationProcess = $null
$testSucceeded = $false
$testDataDirectory = Join-Path ([IO.Path]::GetTempPath()) "mit-portal-e2e-$([Guid]::NewGuid().ToString('N'))"
$applicationStdout = Join-Path ([IO.Path]::GetTempPath()) "mit-portal-e2e-$([Guid]::NewGuid().ToString('N')).out.log"
$applicationStderr = Join-Path ([IO.Path]::GetTempPath()) "mit-portal-e2e-$([Guid]::NewGuid().ToString('N')).err.log"
$previousBaseUrl = $env:BASE_URL

try {
    $nativeErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $packageOutput = & (Join-Path $projectRoot 'mvnw.cmd') -q -DskipTests package 2>&1
        $packageExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $nativeErrorActionPreference
    }
    $packageOutput | ForEach-Object { Write-Output $_ }
    if ($packageExitCode -ne 0) {
        throw "Building the portal for isolated browser tests failed with exit code $packageExitCode."
    }
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw "The browser-test application JAR was not created at $jarPath."
    }

    New-Item -ItemType Directory -Path $testDataDirectory -Force | Out-Null
    $uploadDirectory = Join-Path $testDataDirectory 'uploads'
    New-Item -ItemType Directory -Path $uploadDirectory -Force | Out-Null
    $baseUrl = "http://127.0.0.1:$Port"
    $arguments = @(
        '-jar', $jarPath,
        '--spring.datasource.url=jdbc:h2:mem:sis_e2e;MODE=MySQL;DB_CLOSE_DELAY=-1',
        '--spring.datasource.username=sa',
        '--spring.datasource.password=',
        '--spring.datasource.driver-class-name=org.h2.Driver',
        '--spring.jpa.hibernate.ddl-auto=create-drop',
        '--spring.flyway.enabled=false',
        '--sis.sample-data.enabled=true',
        '--jwt.secret=E2EOnlySecretKeyMustNeverBeUsedOutsideTests',
        '--server.address=127.0.0.1',
        "--server.port=$Port",
        '--app.security.cookie-secure=false',
        "--app.frontend.url=$baseUrl",
        "--cors.allowed-origins=$baseUrl",
        "--file.upload.directory=$uploadDirectory",
        '--ratelimit.auth.capacity=1000',
        '--ratelimit.auth.refill-per-minute=1000',
        '--ratelimit.admissions.capacity=1000',
        '--ratelimit.admissions.refill-per-minute=1000'
    )
    $applicationProcess = Start-Process -FilePath $javaCommand.Source `
        -ArgumentList $arguments `
        -WorkingDirectory $projectRoot `
        -RedirectStandardOutput $applicationStdout `
        -RedirectStandardError $applicationStderr `
        -WindowStyle Hidden `
        -PassThru
    $env:BASE_URL = $baseUrl

    $ready = $false
    $deadline = (Get-Date).AddMinutes(3)
    while ((Get-Date) -lt $deadline) {
        if ($applicationProcess.HasExited) {
            throw "The isolated browser-test server exited with code $($applicationProcess.ExitCode). See its temporary logs at $applicationStdout and $applicationStderr."
        }
        try {
            $health = Invoke-WebRequest -Uri "$baseUrl/actuator/health" -TimeoutSec 3 -UseBasicParsing
            if ($health.StatusCode -eq 200) {
                $ready = $true
                break
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) {
        throw "The isolated browser-test server did not become healthy. See its temporary logs at $applicationStdout and $applicationStderr."
    }

    $testRunnerArguments = @('--workers=1')
    if (-not [string]::IsNullOrWhiteSpace($TestName)) {
        $testRunnerArguments += @('--grep', $TestName)
    }
    $nativeErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $testOutput = & npm run test:e2e -- @testRunnerArguments 2>&1
        $testExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $nativeErrorActionPreference
    }
    $testOutput | ForEach-Object { Write-Output $_ }
    if ($testExitCode -ne 0) {
        throw "The isolated browser tests failed with exit code $testExitCode."
    }
    $testSucceeded = $true
} finally {
    if ($applicationProcess -and -not $applicationProcess.HasExited) {
        Stop-Process -Id $applicationProcess.Id -ErrorAction SilentlyContinue
        $applicationProcess.WaitForExit()
    }
    $env:BASE_URL = $previousBaseUrl
    if (Test-Path -LiteralPath $testDataDirectory -PathType Container) {
        Remove-Item -LiteralPath $testDataDirectory -Recurse -Force
    }
    if ($testSucceeded) {
        foreach ($logFile in @($applicationStdout, $applicationStderr)) {
            if (Test-Path -LiteralPath $logFile -PathType Leaf) {
                Remove-Item -LiteralPath $logFile -Force
            }
        }
    } else {
        Write-Warning "Isolated E2E server logs were preserved for diagnosis: $applicationStdout ; $applicationStderr"
    }
    Pop-Location
}
