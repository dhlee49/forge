@echo off
REM ===========================================================================
REM  Local 4-Player Commander Game
REM  All players on the same machine, each with their own window.
REM ===========================================================================
REM
REM  How to play:
REM    1. Go to "Constructed" mode in the menu
REM    2. Check the "Commander" variant checkbox
REM    3. Click "Add a Player" twice (total 4 players)
REM    4. Set ALL player slots to "LOCAL" (human)
REM    5. Select Commander decks for each player
REM    6. Click "Start" — 4 player windows will open automatically
REM
REM  Each player drags their window to their own monitor for hand privacy.
REM ===========================================================================

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot"
set "MAVEN_HOME=C:\Users\Dong Hoon\tools\apache-maven-3.9.6"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"
set "PROJECT_DIR=%~dp0"

echo ===========================================
echo   Local Commander Game Launcher
echo ===========================================
echo.

REM Check if JAR exists, build if not
set "DESKTOP_JAR=%PROJECT_DIR%forge-gui-desktop\target\forge-gui-desktop-2.0.10-SNAPSHOT-jar-with-dependencies.jar"
if not exist "%DESKTOP_JAR%" (
    echo Desktop JAR not found, building...
    cd /d "%PROJECT_DIR%"
    call "%MAVEN_HOME%\bin\mvn.cmd" install -DskipTests -pl forge-gui-desktop -am -q
    if %ERRORLEVEL% NEQ 0 (
        echo Build failed!
        pause
        exit /b 1
    )
    echo Build complete.
    echo.
)

echo Launching Forge Desktop...
echo.
echo   Instructions:
echo     1. Constructed ^> Check "Commander" variant
echo     2. Add players ^(click "Add a Player" twice^)
echo     3. Set all slots to LOCAL ^(human^)
echo     4. Select Commander decks
echo     5. Click Start
echo.

cd /d "%PROJECT_DIR%forge-gui"
"%JAVA_HOME%\bin\java.exe" -jar "%DESKTOP_JAR%"
