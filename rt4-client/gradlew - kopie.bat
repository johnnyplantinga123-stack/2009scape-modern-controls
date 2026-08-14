@echo off
title 2009Scape RT4 Client
color 0A

echo ==========================================
echo          2009SCAPE RT4 CLIENT
echo ==========================================
echo.

cd /d "%~dp0"

if not exist "gradlew.bat" (
    echo [ERROR] gradlew.bat niet gevonden.
    echo Map: %CD%
    pause
    exit /b 1
)

echo RT4 Client starten...
echo.

call gradlew.bat :client:run --console=plain

echo.
echo ==========================================
echo Client afgesloten.
echo Exit code: %ERRORLEVEL%
echo ==========================================
pause