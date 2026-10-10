@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-23"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d c:\Users\chiru\OneDrive\Desktop\AI_CALL
echo Accepting licenses and installing Android 34...
(
  echo y
  echo y
  echo y
  echo y
  echo y
  echo y
  echo y
  echo y
) | "C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root=C:\Android\Sdk "platforms;android-34" "build-tools;34.0.0" "platform-tools"
echo Finished.
