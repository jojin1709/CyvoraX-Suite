@echo off
cd /d "%~dp0"
"target\jpackage\CyvoraX Suite\CyvoraX Suite.exe"
if %errorlevel% neq 0 (
  echo Launch failed with error %errorlevel%
  pause
)
