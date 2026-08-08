#Requires -RunAsAdministrator
<#
.SYNOPSIS
    CyvoraX Suite Professional v1.6.1 Launcher
.DESCRIPTION
    Launches CyvoraX Suite with proper environment setup.
    Run as Administrator for full proxy functionality.
#>

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host ""
Write-Host "  ========================================" -ForegroundColor Cyan
Write-Host "   CyvoraX Suite Professional v1.6.1" -ForegroundColor Cyan
Write-Host "   Autonomous Web Security Workbench" -ForegroundColor Cyan
Write-Host "  ========================================" -ForegroundColor Cyan
Write-Host ""

$maven = Join-Path $repoRoot "tools\apache-maven-3.9.14\bin\mvn.cmd"
if (-not (Test-Path $maven)) {
    Write-Host "[ERROR] Maven not found at $maven" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "[INFO] Starting CyvoraX Suite..." -ForegroundColor Green
Write-Host "[INFO] Proxy will listen on 127.0.0.1:8080" -ForegroundColor Green
Write-Host "[INFO] Configure browser proxy to 127.0.0.1:8080" -ForegroundColor Yellow
Write-Host ""

Push-Location $repoRoot
try {
    & $maven javafx:run -q
} finally {
    Pop-Location
}
