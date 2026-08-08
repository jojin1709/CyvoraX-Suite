@echo off
title CyvoraX Suite Professional v1.6.1
echo.
echo  ========================================
echo   CyvoraX Suite Professional v1.6.1
echo   Autonomous Web Security Workbench
echo  ========================================
echo.

set "BASE_DIR=%~dp0"
set "MAVEN=%BASE_DIR%tools\apache-maven-3.9.14\bin\mvn.cmd"

if not exist "%MAVEN%" (
    echo [ERROR] Maven not found at %MAVEN%
    pause
    exit /b 1
)

echo [INFO] Starting CyvoraX Suite...
echo [INFO] Proxy will listen on 127.0.0.1:8080
echo.

cd /d "%BASE_DIR%"
"%MAVEN%" javafx:run -q

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] CyvoraX Suite exited with error code %ERRORLEVEL%
    pause
)
