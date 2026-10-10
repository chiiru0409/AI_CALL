@echo off
set "JAVA_HOME=C:\Android\jdk-17"
set "PATH=C:\Android\jdk-17\bin;%PATH%"
cd /d "C:\Users\chiru\OneDrive\Desktop\AI_CALL"

echo ==============================================
echo 1. Verifying Java Runtime
echo ==============================================
java -version

echo.
echo ==============================================
echo 2. Verifying Gradle Runtime JVM
echo ==============================================
call gradlew.bat --version

echo.
echo ==============================================
echo 3. Stopping Daemons and Cleaning
echo ==============================================
call gradlew.bat --stop

echo.
echo ==============================================
echo 4. Executing Build and Unit Tests
echo ==============================================
call gradlew.bat --no-daemon --max-workers=1 testDebugUnitTest assembleDebug

echo.
echo ==============================================
echo 5. Build Script Completed
echo ==============================================
