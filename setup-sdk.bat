@echo off
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set ANDROID_SDK_ROOT=%ANDROID_HOME%
set PATH=%ANDROID_HOME%\cmdline-tools\latest\bin;%PATH%

echo y | sdkmanager.bat --licenses
echo.
echo Now installing SDK components...
sdkmanager.bat "platform-tools" "platforms;android-33" "build-tools;33.0.0"

echo.
echo Installed packages:
sdkmanager.bat --list_installed

pause
