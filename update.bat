@echo off
rem Downloads the latest version of the app from GitHub.
rem Your campaigns are stored outside this folder, so they are not touched.
cd /d "%~dp0"

where git >nul 2>nul
if errorlevel 1 (
    echo Git was not found. Install it from https://git-scm.com/ and try again.
    pause
    exit /b 1
)

if not exist ".git" (
    echo This copy was downloaded as a ZIP, so it can't update itself.
    echo To get automatic updates, install Git and run this instead:
    echo     git clone https://github.com/Mykkey/Campaign-Pocketbook.git
    echo Then use update.bat inside that new folder from now on.
    pause
    exit /b 1
)

echo Checking for updates...
git pull
if errorlevel 1 (
    echo.
    echo The update failed. If you edited any files in this folder, undo those changes and try again.
    pause
    exit /b 1
)

echo.
echo Done. Launch the app with CampaignPocketbook.bat - it rebuilds itself on the first start.
pause
