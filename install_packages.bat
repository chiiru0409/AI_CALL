@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-23"
"C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root=C:\Android\Sdk "platforms;android-34" "build-tools;34.0.0" "platform-tools"
