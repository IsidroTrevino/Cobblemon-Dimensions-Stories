@echo off
setlocal

rem Windows counterpart of run-test-client.sh: builds the addon, drops it into the
rem Cobblemon dev workspace's client mods folder, then launches that client.

set "PROJECT_DIR=%~dp0"
if "%COBBLEMON_SOURCE_DIR%"=="" set "COBBLEMON_SOURCE_DIR=%PROJECT_DIR%..\cobblemon-main"
set "ADDON_JAR=%PROJECT_DIR%build\libs\cobblemon-dimensions-stories-0.1.0.jar"
set "CLIENT_MODS_DIR=%COBBLEMON_SOURCE_DIR%\fabric\runClient\mods"

if not exist "%COBBLEMON_SOURCE_DIR%\gradlew.bat" (
    echo Cobblemon source workspace not found at: %COBBLEMON_SOURCE_DIR% 1>&2
    exit /b 1
)

echo Building Cobblemon Dimensions Stories...
call "%PROJECT_DIR%gradlew.bat" --offline build || exit /b 1

echo Installing Cobblemon Dimensions Stories into the Fabric test client...
if not exist "%CLIENT_MODS_DIR%" mkdir "%CLIENT_MODS_DIR%"
copy /y "%ADDON_JAR%" "%CLIENT_MODS_DIR%\cobblemon-dimensions-stories-0.1.0.jar" >nul || exit /b 1

echo Starting Minecraft 1.21.1 with Cobblemon and Cobblemon Dimensions Stories...
pushd "%COBBLEMON_SOURCE_DIR%"
call .\gradlew.bat -PfabricOnly --offline :fabric:runClient
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
