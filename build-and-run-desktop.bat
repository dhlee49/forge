@echo off
REM Build desktop module and run it

set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot
set MAVEN_HOME=C:\Users\Dong Hoon\tools\apache-maven-3.9.6
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

echo Building desktop...
cd /d "%~dp0"
call mvn install -DskipTests -pl forge-gui-desktop -am
if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo Starting Forge Desktop...
cd /d "%~dp0forge-gui"
"%JAVA_HOME%\bin\java.exe" -jar "..\forge-gui-desktop\target\forge-gui-desktop-2.0.10-SNAPSHOT-jar-with-dependencies.jar"
pause
