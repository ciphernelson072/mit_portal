param(
  [Parameter(Mandatory = $true)][string]$ArchiveFile,
  [Parameter(Mandatory = $true)][string]$DestinationDirectory
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $ArchiveFile -PathType Leaf)) {
  throw "Upload archive not found: $ArchiveFile"
}
if ((Get-Item -LiteralPath $ArchiveFile).Length -eq 0) {
  throw "Upload archive is empty."
}

$checksumFile = "$ArchiveFile.sha256"
if (-not (Test-Path -LiteralPath $checksumFile -PathType Leaf)) {
  throw "Upload archive checksum file not found: $checksumFile"
}
$expectedChecksum = (Get-Content -LiteralPath $checksumFile -Raw).Trim()
if ($expectedChecksum -notmatch '^[A-Fa-f0-9]{64}$') {
  throw "Upload archive checksum file is invalid: $checksumFile"
}
$actualChecksum = (Get-FileHash -LiteralPath $ArchiveFile -Algorithm SHA256).Hash
if (-not [string]::Equals($expectedChecksum, $actualChecksum, [StringComparison]::OrdinalIgnoreCase)) {
  throw "Upload archive checksum verification failed; restore was not started."
}

$destination = [System.IO.Path]::GetFullPath($DestinationDirectory)
if (Test-Path -LiteralPath $destination) {
  throw "Restore destination already exists; choose a new empty path: $destination"
}
$parent = Split-Path -Parent $destination
if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
  throw "Restore destination parent directory does not exist: $parent"
}
$stagingDirectory = Join-Path $parent (".uploads-restore-" + [guid]::NewGuid().ToString("N"))

try {
  Add-Type -AssemblyName System.IO.Compression
  $archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ArchiveFile).Path)
  try {
    foreach ($entry in $archive.Entries) {
      $entryPath = $entry.FullName.Replace('\', '/')
      $normalizedEntryPath = $entryPath.TrimEnd('/')
      $segments = $normalizedEntryPath.Split('/')
      if ([System.IO.Path]::IsPathRooted($entryPath) -or
          $entryPath -match '^[A-Za-z]:' -or
          [string]::IsNullOrWhiteSpace($normalizedEntryPath) -or
          $segments -contains '..' -or
          $segments -contains '') {
        throw "Archive contains an unsafe path: $($entry.FullName)"
      }
    }
  }
  finally {
    $archive.Dispose()
  }

  [System.IO.Compression.ZipFile]::ExtractToDirectory(
    (Resolve-Path -LiteralPath $ArchiveFile).Path,
    $stagingDirectory
  )
  Move-Item -LiteralPath $stagingDirectory -Destination $destination
}
catch {
  if (Test-Path -LiteralPath $stagingDirectory) {
    Remove-Item -LiteralPath $stagingDirectory -Recurse -Force
  }
  throw
}

Write-Output "Upload archive restored to: $destination"
