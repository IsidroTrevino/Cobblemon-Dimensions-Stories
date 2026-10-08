@echo off
setlocal

set "PROJECT_DIR=%~dp0"
if "%COBBLEMON_SOURCE_DIR%"=="" set "COBBLEMON_SOURCE_DIR=%PROJECT_DIR%..\cobblemon"
set "ADDON_JAR=%PROJECT_DIR%build\libs\cobblemon-dimensions-stories-0.1.0.jar"
set "SERVER_MODS_DIR=%COBBLEMON_SOURCE_DIR%\fabric\runServer\mods"

if not exist "%COBBLEMON_SOURCE_DIR%\gradlew.bat" (
    echo Cobblemon source workspace not found at: %COBBLEMON_SOURCE_DIR% 1>&2
    exit /b 1
)

echo Building the server-only Stories addon...
call "%PROJECT_DIR%gradlew.bat" build || exit /b 1
if not exist "%SERVER_MODS_DIR%" mkdir "%SERVER_MODS_DIR%"
copy /y "%ADDON_JAR%" "%SERVER_MODS_DIR%\cobblemon-dimensions-stories-0.1.0.jar" >nul || exit /b 1

echo Starting the dedicated Cobblemon server with Stories...
echo On first launch, review the Minecraft EULA in fabric/runServer/eula.txt.
pushd "%COBBLEMON_SOURCE_DIR%"
call .\gradlew.bat -PfabricOnly :fabric:runServer
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
