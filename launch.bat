@echo off
cd /d "%~dp0"
if exist "release\CyvoraX Suite.exe" (
    "release\CyvoraX Suite.exe"
) else (
    echo Launch failed: CyvoraX Suite.exe not found in release\
    pause
)
