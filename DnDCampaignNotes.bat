@echo off
rem Launches the app, recompiling first if any source file changed.
rem Uses JDK/Maven from %USERPROFILE%\dev-tools if present, otherwise whatever is on your PATH.
if exist "%USERPROFILE%\dev-tools\jdk-21\bin\java.exe" (
    set "JAVA_HOME=%USERPROFILE%\dev-tools\jdk-21"
    set "PATH=%USERPROFILE%\dev-tools\jdk-21\bin;%USERPROFILE%\dev-tools\apache-maven-3.9.9\bin;%PATH%"
)
cd /d "%~dp0"
where mvn >nul 2>nul
if errorlevel 1 (
    echo Maven was not found. Install JDK 21+ and Apache Maven, then try again.
    echo See README.md for details.
    pause
    exit /b 1
)
call mvn -q -B javafx:run
if errorlevel 1 (
    echo.
    echo The app failed to build or crashed. See the messages above.
    pause
)
