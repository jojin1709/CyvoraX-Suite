$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$maven = Join-Path $repoRoot "tools\apache-maven-3.9.14\bin\mvn.cmd"
if (-not (Test-Path -LiteralPath $maven)) {
    $maven = "mvn"
}

Push-Location $repoRoot
try {
    & $maven clean package
    $jar = Get-ChildItem -LiteralPath ".\target" -Filter "cyvorax-suite-*.jar" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $jar) {
        throw "No CyvoraX Suite jar found in target."
    }
    $java = Join-Path $repoRoot "runtime\bin\java.exe"
    if (-not (Test-Path -LiteralPath $java)) {
        $java = "java"
    }
    & $java -jar $jar.FullName
} finally {
    Pop-Location
}
