@echo off
setlocal

rem Launch the normal Cobblemon client, without the Stories addon.

set "PROJECT_DIR=%~dp0"
if "%COBBLEMON_SOURCE_DIR%"=="" set "COBBLEMON_SOURCE_DIR=%PROJECT_DIR%..\cobblemon"
set "CLIENT_MODS_DIR=%COBBLEMON_SOURCE_DIR%\fabric\runClient\mods"

if not exist "%COBBLEMON_SOURCE_DIR%\gradlew.bat" (
    echo Cobblemon source workspace not found at: %COBBLEMON_SOURCE_DIR% 1>&2
    exit /b 1
)

if exist "%CLIENT_MODS_DIR%\cobblemon-dimensions-stories*.jar" (
    echo Remove Stories from the test client's mods folder before testing. 1>&2
    exit /b 1
)

echo Starting the Cobblemon client without Stories. Join the dedicated test server.
pushd "%COBBLEMON_SOURCE_DIR%"
call .\gradlew.bat -PfabricOnly --offline :fabric:runClient
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
