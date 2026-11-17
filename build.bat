@echo off
setlocal
pushd "%~dp0"
where java >nul 2>&1
if errorlevel 1 (
    echo Java was not found. Install JDK 17 and add it to PATH.
    popd
    exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\bootstrap-gradle.ps1"
if errorlevel 1 (
    echo Could not obtain verified Forge MDK Gradle wrapper.
    popd
    exit /b 1
)
call "%~dp0gradlew.bat" build
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
