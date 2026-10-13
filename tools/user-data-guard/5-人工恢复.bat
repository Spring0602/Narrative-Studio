@echo off
chcp 65001 >nul
cd /d "%~dp0"
set "PY="
python -c "import sys" >nul 2>nul && set "PY=python"
if not defined PY (
  py -3 -c "import sys" >nul 2>nul && set "PY=py -3"
)
if not defined PY if exist "%LOCALAPPDATA%\Programs\Python\Python313\python.exe" set PY="%LOCALAPPDATA%\Programs\Python\Python313\python.exe"
if not defined PY if exist "%LOCALAPPDATA%\Programs\Python\Python312\python.exe" set PY="%LOCALAPPDATA%\Programs\Python\Python312\python.exe"
if not defined PY if exist "%LOCALAPPDATA%\Programs\Python\Python311\python.exe" set PY="%LOCALAPPDATA%\Programs\Python\Python311\python.exe"
if not defined PY if exist "C:\Python313\python.exe" set PY="C:\Python313\python.exe"
if not defined PY if exist "C:\Python312\python.exe" set PY="C:\Python312\python.exe"
if not defined PY if exist "C:\Python311\python.exe" set PY="C:\Python311\python.exe"
if not defined PY (
  echo.
  echo  [guard] Python not found.
  echo  [guard] Please install Python 3.10 or newer: https://www.python.org/downloads/
  echo  [guard] During install, tick "Add python.exe to PATH".
  echo.
  pause
  exit /b 1
)
echo  [guard] Restore user data into the whitelisted database.
echo  [guard] It will: verify snapshot -> verify identity/relations -> backup -> write.
echo  [guard] You will be asked to confirm before anything is written.
echo  [guard] Tip: add "--dry-run" to only verify without writing.
echo.
%PY% guard.py restore
if errorlevel 1 (
  echo [guard] FAILED. Review the error above; do not assume success.
  pause
  exit /b 1
)
echo.
pause
