@echo off
rem Starts Campaign Pocketbook. Needs Java 21 or newer (https://adoptium.net/).
cd /d "%~dp0"
where javaw >nul 2>nul
if errorlevel 1 (
    echo Java was not found. Campaign Pocketbook needs Java 21 or newer.
    echo Download it from https://adoptium.net/ , install it, and run this file again.
    pause
    exit /b 1
)
start "" javaw --module-path javafx --add-modules javafx.controls,javafx.swing -cp "CampaignPocketbook.jar;lib\*" dnd.App
