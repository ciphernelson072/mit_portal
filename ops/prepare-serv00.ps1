[CmdletBinding()]
param(
    [string]$OutputName = 'serv00-deployment'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$targetDirectory = Join-Path $projectRoot 'target'
$outputDirectory = Join-Path $targetDirectory $OutputName
$archivePath = "$outputDirectory.zip"
$jarPath = Join-Path $targetDirectory 'sis-0.0.1-SNAPSHOT.jar'
$mavenWrapper = Join-Path $projectRoot 'mvnw.cmd'

if ([string]::IsNullOrWhiteSpace($OutputName) -or
    [System.IO.Path]::IsPathRooted($OutputName) -or
    [System.IO.Path]::GetFileName($OutputName) -ne $OutputName -or
    $OutputName -in @('.', '..')) {
    throw 'OutputName must be a single directory name under target.'
}
if ((Test-Path -LiteralPath $outputDirectory) -or (Test-Path -LiteralPath $archivePath)) {
    throw "Deployment output already exists. Choose another OutputName or inspect and remove the generated output intentionally: $outputDirectory"
}

Push-Location $projectRoot
try {
    & $mavenWrapper -B -Pserv00 verify
    if ($LASTEXITCODE -ne 0) {
        throw "Serv00 profile verification failed with exit code $LASTEXITCODE."
    }
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw "Expected application JAR was not produced: $jarPath"
    }

    $jarTool = Get-Command 'jar.exe' -ErrorAction SilentlyContinue
    if (-not $jarTool) {
        throw 'The Java 17 JDK jar.exe tool is required to verify the deployment artifact.'
    }
    $entries = & $jarTool.Source tf $jarPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not inspect the packaged application JAR.'
    }
    $forbiddenEntries = @(
        $entries | Where-Object {
            $_ -match '(^|/)SampleDataLoader[^/]*\.class$' -or
            $_ -match '(^|/)sample_data\.sql$' -or
            $_ -match '(^|/)src/test/'
        }
    )
    if ($forbiddenEntries.Count -gt 0) {
        throw "Deployment JAR contains sample/test data that should be excluded: $($forbiddenEntries -join ', ')"
    }

    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
    Copy-Item -LiteralPath $jarPath -Destination (Join-Path $outputDirectory 'app.jar')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'serv00\portal.sh') -Destination (Join-Path $outputDirectory 'portal.sh')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'serv00\serv00.env.example') -Destination (Join-Path $outputDirectory 'serv00.env.example')
    Copy-Item -LiteralPath (Join-Path $projectRoot 'docs\SERV00_DEPLOYMENT.md') -Destination (Join-Path $outputDirectory 'DEPLOYMENT.md')
    Compress-Archive -Path (Join-Path $outputDirectory '*') -DestinationPath $archivePath

    Write-Host "Deployment package created: $archivePath"
    Write-Host 'The archive contains the production JAR, process control script, safe environment template, and deployment guide.'
    Write-Host 'Automated tests were run by Maven verify; sample/demo seeding and test fixtures are not in the JAR.'
} finally {
    Pop-Location
}
