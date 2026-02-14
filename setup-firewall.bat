@echo off
REM Opens Windows Firewall port 36743 for Forge multiplayer server
REM Must be run as Administrator

net session >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo This script requires Administrator privileges.
    echo Right-click this file and select "Run as administrator".
    pause
    exit /b 1
)

echo Adding firewall rules for Forge Server (port 36743)...

netsh advfirewall firewall delete rule name="Forge Server" >nul 2>&1

netsh advfirewall firewall add rule name="Forge Server" dir=in action=allow protocol=TCP localport=36743
netsh advfirewall firewall add rule name="Forge Server" dir=in action=allow protocol=UDP localport=36743

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Firewall rules added successfully!
    echo Port 36743 is now open for incoming connections.
    echo Your phone can connect using the Local IP shown in the lobby.
) else (
    echo.
    echo Failed to add firewall rules.
)

echo.
pause
