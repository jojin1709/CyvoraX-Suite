param(
    [switch]$Installer
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$appName = "CyvoraX Suite"
$appVersion = "1.0.1"
$mainJar = "cyvorax-suite-$appVersion.jar"
$iconPath = Join-Path $repoRoot "src\main\resources\icons\cyvorax.ico"
$maven = Join-Path $repoRoot "..\tools\apache-maven-3.9.14\bin\mvn.cmd"
if (-not (Test-Path -LiteralPath $maven)) {
    $maven = "mvn"
}

Push-Location $repoRoot
try {
    & $maven package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE."
    }

    $appInput = ".\target\app-input"
    $appContent = ".\target\app-content"
    $dest = ".\target\jpackage"
    if (Test-Path -LiteralPath $appInput) {
        Remove-Item -Recurse -Force -LiteralPath $appInput
    }
    if (Test-Path -LiteralPath $appContent) {
        Remove-Item -Recurse -Force -LiteralPath $appContent
    }
    if (Test-Path -LiteralPath $dest) {
        Remove-Item -Recurse -Force -LiteralPath $dest
    }
    New-Item -ItemType Directory -Force -Path $appInput, $appContent | Out-Null
    Copy-Item -Force -LiteralPath ".\target\$mainJar" -Destination (Join-Path $appInput $mainJar)
    if (Test-Path -LiteralPath ".\target\lib") {
        Copy-Item -Recurse -Force -LiteralPath ".\target\lib" -Destination (Join-Path $appInput "lib")
    }

    $type = if ($Installer) { "exe" } else { "app-image" }
    $sourceTools = Join-Path $repoRoot "tools"
    $appTools = Join-Path $appContent "tools"
    if (Test-Path -LiteralPath $sourceTools) {
        New-Item -ItemType Directory -Force -Path $appTools | Out-Null
        Copy-Item -Recurse -Force -Path (Join-Path $sourceTools "*") -Destination $appTools
    }

    $jpackageArgs = @(
        "--type", $type,
        "--name", $appName,
        "--app-version", $appVersion,
        "--input", $appInput,
        "--main-jar", $mainJar,
        "--main-class", "com.venomproxy.Main",
        "--dest", $dest
    )
    if (Test-Path -LiteralPath $appTools) {
        $jpackageArgs += @("--app-content", $appTools)
    }
    if (Test-Path -LiteralPath $iconPath) {
        $jpackageArgs += @("--icon", $iconPath)
    }
    if ($Installer) {
        $jpackageArgs += @("--win-menu", "--win-shortcut", "--win-dir-chooser", "--win-per-user-install")
    }

    & jpackage @jpackageArgs
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}
