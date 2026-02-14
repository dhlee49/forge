@echo off
REM Builds Forge Android APK (debug build)
REM Run setup-android-sdk.bat first if you haven't installed the SDK
REM
REM Uses short paths to avoid Windows 8191-char command line limit:
REM   - Maven repo mapped to G:\ via subst (was C:\m2\repository)
REM   - Project mapped to F:\ via subst
REM   Together these shorten D8's ~80 classpath entries enough to fit.

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot"
set "MAVEN_HOME=C:\Users\Dong Hoon\tools\apache-maven-3.9.6"
set "ANDROID_HOME=C:\Users\Dong Hoon\tools\android-sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

echo ============================================
echo  Building Forge Android APK
echo ============================================
echo.

REM Check Java
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Java not found at %JAVA_HOME%
    echo Install JDK 17 and update JAVA_HOME in this script.
    pause
    exit /b 1
)

REM Check Android SDK
if not exist "%ANDROID_HOME%\platforms\android-35" (
    echo Android SDK not found! Run setup-android-sdk.bat first.
    pause
    exit /b 1
)

REM Find Maven classworlds jar (needed to invoke Maven directly)
set "CLASSWORLDS_JAR="
for %%i in ("%MAVEN_HOME%\boot\plexus-classworlds-*") do set "CLASSWORLDS_JAR=%%i"
if "%CLASSWORLDS_JAR%"=="" (
    echo Maven installation broken: plexus-classworlds jar not found in
    echo   %MAVEN_HOME%\boot\
    echo Re-download Apache Maven 3.9.6 and extract to that folder.
    pause
    exit /b 1
)

REM Setup short Maven repo at G:\ to shorten classpath entries
if not exist "C:\m2\repository" mkdir "C:\m2\repository"
subst G: /d >nul 2>&1
subst G: "C:\m2\repository"
if %ERRORLEVEL% NEQ 0 (
    echo Failed to create drive mapping G: for Maven repo.
    echo Try: subst G: /d
    pause
    exit /b 1
)
echo Mapped G: to C:\m2\repository

REM Create a settings file that uses the short G:\ repo path
echo ^<settings^>^<localRepository^>G:\^</localRepository^>^</settings^> > "%~dp0mvn-settings-build.xml"
set "MVN_SETTINGS=%~dp0mvn-settings-build.xml"

REM Install custom android-maven-plugin 4.6.2 to short-path repo
set "PLUGIN_DIR=G:\com\simpligility\maven\plugins\android-maven-plugin\4.6.2"
if not exist "%PLUGIN_DIR%\android-maven-plugin-4.6.2.jar" (
    echo Installing android-maven-plugin 4.6.2 from Card-Forge fork...
    mkdir "%PLUGIN_DIR%" 2>nul
    del "%PLUGIN_DIR%\*.lastUpdated" 2>nul

    powershell -Command "& {Invoke-WebRequest -Uri 'https://github.com/Card-Forge/android-maven-plugin/releases/download/4.6.2/android-maven-plugin-4.6.2.jar' -OutFile '%PLUGIN_DIR%\android-maven-plugin-4.6.2.jar'}"
    if %ERRORLEVEL% NEQ 0 (
        echo Failed to download android-maven-plugin jar!
        subst G: /d >nul 2>&1
        pause
        exit /b 1
    )

    powershell -Command "& {Invoke-WebRequest -Uri 'https://github.com/Card-Forge/android-maven-plugin/releases/download/4.6.2/android-maven-plugin-4.6.2.pom' -OutFile '%PLUGIN_DIR%\android-maven-plugin-4.6.2.pom'}"
    if %ERRORLEVEL% NEQ 0 (
        echo Failed to download android-maven-plugin pom!
        subst G: /d >nul 2>&1
        pause
        exit /b 1
    )

    echo android-maven-plugin 4.6.2 installed.
    echo.
) else (
    echo android-maven-plugin 4.6.2 already installed.
)

REM ================================================================
REM Step 1: Build core modules (from original project directory)
REM   Uses G:\ for Maven repo so jars resolve with short paths
REM ================================================================
echo.
echo Step 1: Building core modules...

cd /d "%~dp0"

"%JAVA_HOME%\bin\java.exe" ^
    -classpath "%CLASSWORLDS_JAR%" ^
    "-Dclassworlds.conf=%MAVEN_HOME%\bin\m2.conf" ^
    "-Dmaven.home=%MAVEN_HOME%" ^
    "-Dlibrary.jansi.path=%MAVEN_HOME%\lib\jansi-native" ^
    "-Dmaven.multiModuleProjectDirectory=%~dp0." ^
    org.codehaus.plexus.classworlds.launcher.Launcher ^
    install -DskipTests -pl forge-gui-mobile -am -s "%MVN_SETTINGS%"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Core module build failed!
    subst G: /d >nul 2>&1
    pause
    exit /b 1
)

REM ================================================================
REM Step 2: Build Android APK (from short drive F: to avoid
REM         Windows 8191-char command line limit in D8 dex compiler)
REM ================================================================
echo.
echo Step 2: Building Android APK (debug)...

REM Map project to short drive letter F:
subst F: /d >nul 2>&1
subst F: "%~dp0."
if %ERRORLEVEL% NEQ 0 (
    echo Failed to create drive mapping F: - is it already in use?
    echo Try: subst F: /d
    subst G: /d >nul 2>&1
    pause
    exit /b 1
)
echo Mapped F: to %~dp0

cd /d F:\

REM Clean old proguard output to avoid permission issues
if exist "F:\forge-gui-android\target\proguard\dump.txt" (
    del /q "F:\forge-gui-android\target\proguard\dump.txt" 2>nul
    del /q "F:\forge-gui-android\target\proguard\seeds.txt" 2>nul
    del /q "F:\forge-gui-android\target\proguard\usage.txt" 2>nul
    del /q "F:\forge-gui-android\target\proguard\mapping.txt" 2>nul
    echo Cleaned old ProGuard output.
)

"%JAVA_HOME%\bin\java.exe" ^
    --add-opens java.base/sun.security.pkcs=ALL-UNNAMED ^
    --add-opens java.base/sun.security.x509=ALL-UNNAMED ^
    -classpath "%CLASSWORLDS_JAR%" ^
    "-Dclassworlds.conf=%MAVEN_HOME%\bin\m2.conf" ^
    "-Dmaven.home=%MAVEN_HOME%" ^
    "-Dlibrary.jansi.path=%MAVEN_HOME%\lib\jansi-native" ^
    "-Dmaven.multiModuleProjectDirectory=F:\\" ^
    org.codehaus.plexus.classworlds.launcher.Launcher ^
    -B -P android-debug verify "-Dandroid.sdk.path=%ANDROID_SDK_ROOT%" -Dandroid.buildToolsVersion=35.0.0 -Dmaven.test.skip=true -pl forge-gui-android -am -s "F:\mvn-settings-build.xml"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo APK build failed!
    subst F: /d >nul 2>&1
    subst G: /d >nul 2>&1
    pause
    exit /b 1
)

REM Clean up drive mappings
subst F: /d >nul 2>&1
subst G: /d >nul 2>&1

echo.
echo ============================================
echo  BUILD COMPLETE
echo ============================================

REM Find the APK
for %%f in ("%~dp0forge-gui-android\target\*.apk") do (
    echo.
    echo APK location: %%f
    echo.
    echo To install on your phone:
    echo   1. Copy the APK to your phone
    echo   2. Enable "Install from unknown sources" in Settings
    echo   3. Tap the APK to install
    echo.
    echo Or install via USB with:
    echo   adb install -r "%%f"
)

pause
