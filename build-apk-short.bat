@echo off
REM Builds Forge Android APK using short paths to avoid Windows command line limit
REM Creates junction C:\f -> forge dir and uses C:\m2 for Maven repo

set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot
set MAVEN_HOME=C:\Users\Dong Hoon\tools\apache-maven-3.9.6
set ANDROID_HOME=C:\Users\Dong Hoon\tools\android-sdk
set ANDROID_SDK_ROOT=%ANDROID_HOME%
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%

REM Create short path junction if not exists
if not exist "C:\f\pom.xml" (
    if exist "C:\f" rmdir C:\f
    mklink /J C:\f "C:\Users\Dong Hoon\Desktop\github\forge"
    if not exist "C:\f\pom.xml" (
        echo Failed to create junction C:\f
        pause
        exit /b 1
    )
    echo Created junction C:\f
)

REM Ensure short Maven repo exists
if not exist "C:\m2\repository" mkdir "C:\m2\repository"

echo ============================================
echo  Building Forge Android APK (short paths)
echo ============================================
echo.

REM Check Android SDK
if not exist "%ANDROID_HOME%\platforms\android-35" (
    echo Android SDK not found! Run setup-android-sdk.bat first.
    pause
    exit /b 1
)

REM Install custom android-maven-plugin if needed
set PLUGIN_DIR=C:\m2\repository\com\simpligility\maven\plugins\android-maven-plugin\4.6.2
if not exist "%PLUGIN_DIR%\android-maven-plugin-4.6.2.jar" (
    echo Installing android-maven-plugin 4.6.2...
    mkdir "%PLUGIN_DIR%" 2>nul
    del "%PLUGIN_DIR%\*.lastUpdated" 2>nul
    powershell -Command "& {Invoke-WebRequest -Uri 'https://github.com/Card-Forge/android-maven-plugin/releases/download/4.6.2/android-maven-plugin-4.6.2.jar' -OutFile '%PLUGIN_DIR%\android-maven-plugin-4.6.2.jar'}"
    powershell -Command "& {Invoke-WebRequest -Uri 'https://github.com/Card-Forge/android-maven-plugin/releases/download/4.6.2/android-maven-plugin-4.6.2.pom' -OutFile '%PLUGIN_DIR%\android-maven-plugin-4.6.2.pom'}"
    echo Installed.
)

REM Clean old proguard output to avoid permission issues
if exist "C:\f\forge-gui-android\target\proguard" (
    del /q "C:\f\forge-gui-android\target\proguard\dump.txt" 2>nul
    del /q "C:\f\forge-gui-android\target\proguard\seeds.txt" 2>nul
    del /q "C:\f\forge-gui-android\target\proguard\usage.txt" 2>nul
    del /q "C:\f\forge-gui-android\target\proguard\mapping.txt" 2>nul
)

cd /d C:\f

echo.
echo Step 1: Building core modules...
call mvn.cmd install -DskipTests -pl forge-gui-mobile -am -s mvn-settings-android.xml
if %ERRORLEVEL% NEQ 0 (
    echo Core module build failed!
    pause
    exit /b 1
)

echo.
echo Step 2: Building Android APK (debug)...
call mvn.cmd -B -P android-debug verify -Dandroid.sdk.path=%ANDROID_SDK_ROOT% -Dandroid.buildToolsVersion=35.0.0 -Dmaven.test.skip=true -pl forge-gui-android -am -s mvn-settings-android.xml
if %ERRORLEVEL% NEQ 0 (
    echo APK build failed!
    pause
    exit /b 1
)

echo.
echo ============================================
echo  BUILD COMPLETE
echo ============================================

REM Find the APK
for %%f in (C:\f\forge-gui-android\target\*.apk) do (
    echo.
    echo APK location: %%f
    echo.
    echo To install on your phone:
    echo   1. Copy the APK to your phone
    echo   2. Enable "Install from unknown sources" in Settings
    echo   3. Tap the APK to install
)

pause
