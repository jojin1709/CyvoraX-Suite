$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ffufDir = Join-Path $root "tools\ffuf"
$katanaDir = Join-Path $root "tools\katana"
New-Item -ItemType Directory -Force -Path $ffufDir, $katanaDir | Out-Null

function Download-GitHubAsset {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Repository,

        [Parameter(Mandatory = $true)]
        [string]$AssetPattern,

        [Parameter(Mandatory = $true)]
        [string]$DestinationDirectory
    )

    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repository/releases/latest"
    $asset = $release.assets | Where-Object { $_.name -match $AssetPattern } | Select-Object -First 1
    if (-not $asset) {
        throw "No asset matching '$AssetPattern' found for $Repository"
    }

    $archive = Join-Path $DestinationDirectory $asset.name
    Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $archive -UseBasicParsing

    if ($archive.EndsWith(".zip")) {
        Expand-Archive -LiteralPath $archive -DestinationPath $DestinationDirectory -Force
    } elseif ($archive.EndsWith(".tar.gz")) {
        tar -xzf $archive -C $DestinationDirectory
    }
}

Download-GitHubAsset -Repository "ffuf/ffuf" -AssetPattern "windows.*amd64.*\.zip$" -DestinationDirectory $ffufDir
Download-GitHubAsset -Repository "projectdiscovery/katana" -AssetPattern "windows.*amd64.*\.zip$" -DestinationDirectory $katanaDir

Write-Host "Downloaded external tool binaries into tools/ffuf and tools/katana."
