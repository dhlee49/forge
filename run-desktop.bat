@echo off
REM Launch Forge Desktop (Commander Main Board / TV Display)
REM Runs from forge-gui directory so it finds res/skins

cd /d "%~dp0forge-gui"
"C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot\bin\java.exe" -jar "..\forge-gui-desktop\target\forge-gui-desktop-2.0.10-SNAPSHOT-jar-with-dependencies.jar"
pause
