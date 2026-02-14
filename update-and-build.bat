@echo off
REM Pulls latest updates from Card-Forge/forge and builds the Android APK.
REM Run this whenever you want the latest card DB and a fresh APK.

echo ============================================
echo  Update Forge and Build Android APK
echo ============================================
echo.

cd /d "%~dp0"

REM Check for uncommitted local changes
git diff --quiet 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Local changes detected -- stashing them...
    git stash
    set "STASHED=1"
    echo.
) else (
    set "STASHED=0"
)

echo Pulling latest from Card-Forge/forge master...
git pull origin master
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Git pull failed! Check your internet connection.
    if "%STASHED%"=="1" (
        echo Restoring your local changes...
        git stash pop
    )
    pause
    exit /b 1
)
echo.

REM Restore stashed changes if any
if "%STASHED%"=="1" (
    echo Restoring your local changes...
    git stash pop
    if %ERRORLEVEL% NEQ 0 (
        echo.
        echo WARNING: Merge conflict when restoring your changes.
        echo Run "git stash pop" manually and resolve conflicts.
        pause
        exit /b 1
    )
    echo.
)

echo Starting APK build...
echo.
call "%~dp0build-apk.bat"
