@echo off
REM Build mobile module and run it

set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot
set MAVEN_HOME=C:\Users\Dong Hoon\tools\apache-maven-3.9.6
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

echo Building mobile...
cd /d "%~dp0"
call mvn install -DskipTests -pl forge-gui-mobile-dev -am
if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo Starting Forge Mobile Dev...
cd /d "%~dp0forge-gui"
"%JAVA_HOME%\bin\java.exe" -jar "..\forge-gui-mobile-dev\target\forge-gui-mobile-dev-2.0.10-SNAPSHOT-jar-with-dependencies.jar"
pause
