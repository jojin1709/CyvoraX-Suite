param(
    [switch]$Installer
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$appName = "CyvoraX Suite"
$mainJar = "cyvorax-suite-1.0.0.jar"
$maven = Join-Path $repoRoot "..\tools\apache-maven-3.9.14\bin\mvn.cmd"
if (-not (Test-Path -LiteralPath $maven)) {
    $maven = "mvn"
}

Push-Location $repoRoot
try {
    & $maven clean package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE."
    }
    $dest = ".\target\jpackage"
    if (Test-Path -LiteralPath $dest) {
        Remove-Item -Recurse -Force -LiteralPath $dest
    }

    $type = if ($Installer) { "exe" } else { "app-image" }
    & jpackage `
        --type $type `
        --name $appName `
        --input ".\target" `
        --main-jar $mainJar `
        --main-class "com.venomproxy.Main" `
        --dest $dest
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }

    $sourceTools = Join-Path $repoRoot "tools"
    $appTools = Join-Path $dest "$appName\tools"
    if (Test-Path -LiteralPath $sourceTools) {
        New-Item -ItemType Directory -Force -Path $appTools | Out-Null
        Copy-Item -Recurse -Force -Path (Join-Path $sourceTools "*") -Destination $appTools
    }
} finally {
    Pop-Location
}
