@echo off
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot"
set "MAVEN_HOME=C:\Users\Dong Hoon\tools\apache-maven-3.9.6"
set "ANDROID_HOME=C:\Users\Dong Hoon\tools\android-sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

cd /d "C:\Users\Dong Hoon\Desktop\github\forge"

echo Starting build at %date% %time% > C:\tmp\apk-build.log
echo. >> C:\tmp\apk-build.log

call "%MAVEN_HOME%\bin\mvn.cmd" -U -B -P android-debug verify -Dandroid.sdk.path="%ANDROID_SDK_ROOT%" -Dandroid.buildToolsVersion=35.0.0 -Dmaven.test.skip=true -pl forge-gui-android -s mvn-settings-android.xml >> C:\tmp\apk-build.log 2>&1

echo. >> C:\tmp\apk-build.log
echo EXIT_CODE=%ERRORLEVEL% >> C:\tmp\apk-build.log
