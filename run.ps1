$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$maven = Join-Path $repoRoot "..\tools\apache-maven-3.9.14\bin\mvn.cmd"
if (-not (Test-Path -LiteralPath $maven)) {
    $maven = "mvn"
}

Push-Location $repoRoot
try {
    & $maven clean package
    & java -jar ".\target\cyvorax-suite-1.0.1.jar"
} finally {
    Pop-Location
}
