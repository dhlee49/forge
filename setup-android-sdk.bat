@echo off
REM Downloads and installs Android SDK command-line tools for building Forge APK
REM No Android Studio required

set ANDROID_SDK_ROOT=C:\Users\Dong Hoon\tools\android-sdk
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%

echo ============================================
echo  Android SDK Setup for Forge APK Build
echo ============================================
echo.
echo SDK will be installed to: %ANDROID_SDK_ROOT%
echo.

if exist "%ANDROID_SDK_ROOT%\cmdline-tools\latest\bin\sdkmanager.bat" (
    echo Android SDK command-line tools already installed.
    goto :install_packages
)

echo Downloading Android command-line tools...
mkdir "%ANDROID_SDK_ROOT%\cmdline-tools" 2>nul

powershell -Command "& {Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile '%TEMP%\cmdline-tools.zip'}"
if %ERRORLEVEL% NEQ 0 (
    echo Failed to download command-line tools!
    pause
    exit /b 1
)

echo Extracting...
powershell -Command "& {Expand-Archive -Path '%TEMP%\cmdline-tools.zip' -DestinationPath '%ANDROID_SDK_ROOT%\cmdline-tools\tmp' -Force}"

REM Move to 'latest' directory as sdkmanager expects
if exist "%ANDROID_SDK_ROOT%\cmdline-tools\latest" rmdir /s /q "%ANDROID_SDK_ROOT%\cmdline-tools\latest"
move "%ANDROID_SDK_ROOT%\cmdline-tools\tmp\cmdline-tools" "%ANDROID_SDK_ROOT%\cmdline-tools\latest"
rmdir /s /q "%ANDROID_SDK_ROOT%\cmdline-tools\tmp" 2>nul

echo Command-line tools installed.
echo.

:install_packages
echo Accepting licenses...
echo y | "%ANDROID_SDK_ROOT%\cmdline-tools\latest\bin\sdkmanager.bat" --licenses >nul 2>&1

echo.
echo Installing required SDK packages...
echo  - platform-tools
echo  - platforms;android-35
echo  - build-tools;35.0.0
echo.

call "%ANDROID_SDK_ROOT%\cmdline-tools\latest\bin\sdkmanager.bat" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Failed to install SDK packages!
    pause
    exit /b 1
)

echo.
echo ============================================
echo  Android SDK setup complete!
echo ============================================
echo.
echo ANDROID_SDK_ROOT = %ANDROID_SDK_ROOT%
echo.
echo You can now run build-apk.bat to build the Forge APK.
echo.
pause
