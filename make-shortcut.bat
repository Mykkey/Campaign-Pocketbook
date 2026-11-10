@echo off
rem Puts a "Campaign Pocketbook" shortcut with the app's icon on your Desktop.
rem (A .bat file cannot have an icon of its own, but a shortcut to it can.)
rem Run it once. To put the shortcut somewhere else, give the folder as an argument:  make-shortcut.bat "C:\some\folder"
setlocal
set "APPDIR=%~dp0"
if "%APPDIR:~-1%"=="\" set "APPDIR=%APPDIR:~0,-1%"
set "TARGET=%APPDIR%\CampaignPocketbook.bat"
set "ICON=%APPDIR%\CampaignPocketbook.ico"
set "WHERE=%~1"
if not exist "%TARGET%" (
    echo Could not find CampaignPocketbook.bat next to this file.
    pause
    exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$folder = $env:WHERE; if (-not $folder) { $folder = [Environment]::GetFolderPath('Desktop') }; $lnk = Join-Path $folder 'Campaign Pocketbook.lnk'; $s = (New-Object -ComObject WScript.Shell).CreateShortcut($lnk); $s.TargetPath = $env:TARGET; $s.WorkingDirectory = $env:APPDIR; $s.WindowStyle = 7; $s.Description = 'Campaign Pocketbook'; if (Test-Path $env:ICON) { $s.IconLocation = $env:ICON + ',0' }; $s.Save(); Write-Host ('Shortcut made: ' + $lnk)"
if errorlevel 1 (
    echo The shortcut could not be made.
    pause
    exit /b 1
)
if "%~1"=="" pause
