@echo off
setlocal EnableExtensions EnableDelayedExpansion
title 2009Scape - Build + Launch

REM ============================================================
REM                2009SCAPE DEV LAUNCHER
REM ============================================================

cd /d "%~dp0"

set "ROOT=%~dp0"
set "CLIENT_DIR=%ROOT%rt4-client"
set "SERVER_DIR=%ROOT%2009scape"

echo.
echo ============================================================
echo              2009SCAPE BUILD + LAUNCH
echo ============================================================
echo.

echo Root:
echo %ROOT%
echo.

echo Client:
echo %CLIENT_DIR%
echo.

echo Server:
echo %SERVER_DIR%
echo.

REM ============================================================
REM JAVA CONTROLEREN
REM ============================================================

where java >nul 2>&1

if errorlevel 1 (
    echo [FOUT] Java niet gevonden in PATH.
    goto :fail
)

echo Java:
java -version 2>&1
echo.

REM ============================================================
REM MAPPEN CONTROLEREN
REM ============================================================

if not exist "%CLIENT_DIR%\" (
    echo [FOUT] Clientmap bestaat niet:
    echo %CLIENT_DIR%
    goto :fail
)

if not exist "%SERVER_DIR%\" (
    echo [FOUT] Servermap bestaat niet:
    echo %SERVER_DIR%
    goto :fail
)

REM ============================================================
REM CLIENT GRADLE WRAPPER ZOEKEN
REM ============================================================

set "CLIENT_GRADLEW="

if exist "%CLIENT_DIR%\gradlew.bat" (
    set "CLIENT_GRADLEW=%CLIENT_DIR%\gradlew.bat"
)

if not defined CLIENT_GRADLEW (
    for /f "delims=" %%G in ('dir /s /b "%CLIENT_DIR%\gradlew.bat" 2^>nul') do (
        if not defined CLIENT_GRADLEW set "CLIENT_GRADLEW=%%G"
    )
)

if not defined CLIENT_GRADLEW (
    echo [FOUT] Geen gradlew.bat gevonden in:
    echo %CLIENT_DIR%
    goto :fail
)

echo Client Gradle:
echo !CLIENT_GRADLEW!
echo.

REM ============================================================
REM SERVER LAUNCHER ZOEKEN
REM ============================================================

set "SERVER_BAT="

if exist "%SERVER_DIR%\run-server.bat" (
    set "SERVER_BAT=%SERVER_DIR%\run-server.bat"
)

if not defined SERVER_BAT (
    for /f "delims=" %%S in ('dir /s /b "%SERVER_DIR%\run-server.bat" 2^>nul') do (
        if not defined SERVER_BAT set "SERVER_BAT=%%S"
    )
)

if not defined SERVER_BAT (
    echo [WAARSCHUWING] run-server.bat niet gevonden.
    echo.
    echo Beschikbare BAT bestanden in servermap:
    dir /s /b "%SERVER_DIR%\*.bat" 2>nul
    echo.
    echo Pas SERVER_BAT bovenaan aan als jouw server anders heet.
    goto :fail
)

echo Server launcher:
echo !SERVER_BAT!
echo.

REM ============================================================
REM CLIENT BUILD
REM ============================================================

echo ============================================================
echo [1/4] CLIENT BUILDEN
echo ============================================================
echo.

pushd "%CLIENT_DIR%"

call "!CLIENT_GRADLEW!" :client:compileJava

if errorlevel 1 (
    popd
    echo.
    echo [FOUT] Client build mislukt.
    goto :fail
)

popd

echo.
echo [OK] Client build succesvol.
echo.

REM ============================================================
REM SERVER STARTEN
REM ============================================================

echo ============================================================
echo [2/4] SERVER STARTEN
echo ============================================================
echo.

for %%F in ("!SERVER_BAT!") do set "SERVER_WORKDIR=%%~dpF"

start "2009Scape Server" cmd /k ^
    "cd /d "!SERVER_WORKDIR!" && call "!SERVER_BAT!""

echo [OK] Servervenster geopend.
echo.

REM ============================================================
REM WACHTEN
REM ============================================================

echo ============================================================
echo [3/4] SERVER LATEN OPSTARTEN
echo ============================================================
echo.

timeout /t 5 /nobreak >nul

REM ============================================================
REM CLIENT STARTEN
REM ============================================================

echo ============================================================
echo [4/4] CLIENT STARTEN
echo ============================================================
echo.

pushd "%CLIENT_DIR%"

REM Probeer eerst de meest waarschijnlijke run-task.
start "2009Scape Client" cmd /k ^
    "cd /d "%CLIENT_DIR%" && call "!CLIENT_GRADLEW!" :client:run"

popd

echo.
echo ============================================================
echo                 ALLES GESTART
echo ============================================================
echo.
echo Client build : OK
echo Server       : gestart
echo Client       : gestart
echo.
echo Als de client meldt dat ':client:run' niet bestaat,
echo moeten we alleen de exacte client run-task aanpassen.
echo.
pause
exit /b 0


:fail

echo.
echo ============================================================
echo                   START MISLUKT
echo ============================================================
echo.
pause
exit /b 1