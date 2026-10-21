param(
    [Parameter(Mandatory = $true)]
    [string]$ApiBaseUrl,
    [string]$OutputDirectory = "frontend-dist"
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$staticDirectory = Join-Path $projectRoot "src\main\resources\static"

if (-not (Test-Path $staticDirectory)) { throw "Static frontend directory not found: $staticDirectory" }
if ([string]::IsNullOrWhiteSpace($ApiBaseUrl)) {
    throw "ApiBaseUrl must be an HTTPS API base URL, for example https://api.example.edu/api"
}

try {
    $apiUri = [Uri]$ApiBaseUrl
} catch {
    throw "ApiBaseUrl must be a valid absolute HTTPS API URL."
}
if (-not $apiUri.IsAbsoluteUri -or
    $apiUri.Scheme -ne "https" -or
    [string]::IsNullOrWhiteSpace($apiUri.Host) -or
    -not [string]::IsNullOrEmpty($apiUri.UserInfo) -or
    -not [string]::IsNullOrEmpty($apiUri.Query) -or
    -not [string]::IsNullOrEmpty($apiUri.Fragment) -or
    $apiUri.AbsolutePath.TrimEnd("/") -notmatch '/api$') {
    throw "ApiBaseUrl must be an HTTPS API URL ending in /api, without user info, query, or fragment."
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory) -or
    [System.IO.Path]::IsPathRooted($OutputDirectory) -or
    [System.IO.Path]::GetFileName($OutputDirectory) -ne $OutputDirectory -or
    $OutputDirectory -in @(".", "..")) {
    throw "OutputDirectory must be a new, direct child directory name under the project root."
}

$outputPath = Join-Path $projectRoot $OutputDirectory
if (Test-Path -LiteralPath $outputPath) {
    throw "Output directory already exists; choose a new directory or remove the generated bundle intentionally: $outputPath"
}

$stagingDirectory = Join-Path $projectRoot (".frontend-export-" + [guid]::NewGuid().ToString("N"))
try {
    Copy-Item -LiteralPath $staticDirectory -Destination $stagingDirectory -Recurse
    $configPath = Join-Path $stagingDirectory "js\deployment-config.js"
    $apiBaseUrlJson = ConvertTo-Json -InputObject $apiUri.AbsoluteUri.TrimEnd("/") -Compress
    $config = @"
/* Generated for InfinityFree static hosting. */
window.MIT_PORTAL_CONFIG = {
  apiBaseUrl: $apiBaseUrlJson,
};
"@
    [System.IO.File]::WriteAllText($configPath, $config, [System.Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $stagingDirectory -Destination $outputPath
} catch {
    if (Test-Path -LiteralPath $stagingDirectory -PathType Container) {
        Remove-Item -LiteralPath $stagingDirectory -Recurse -Force
    }
    throw
}

Write-Host "Prepared $outputPath for API $($apiUri.AbsoluteUri.TrimEnd('/'))"
Write-Host "Upload the contents of $outputPath to public_html."
