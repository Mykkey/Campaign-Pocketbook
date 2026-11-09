@echo off
rem Launches the app, recompiling first if any source file changed.
set "JAVA_HOME=%USERPROFILE%\dev-tools\jdk-21"
set "PATH=%JAVA_HOME%\bin;%USERPROFILE%\dev-tools\apache-maven-3.9.9\bin;%PATH%"
cd /d "%~dp0"
call mvn -q -B javafx:run
if errorlevel 1 (
    echo.
    echo The app failed to build or crashed. See the messages above.
    pause
)
