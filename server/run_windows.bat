@echo off
REM Chinese Grammar AI Server - Windows launcher
REM The console prints a PAIRING CODE once at startup; enter it in the
REM Android app to pair the phone. Do NOT close this window while using
REM Advanced Mode. Never expose this server or Ollama to the Internet.
setlocal
cd /d %~dp0
if exist .venv\Scripts\activate.bat call .venv\Scripts\activate.bat
if "%HOST%"=="" set HOST=127.0.0.1
if "%PORT%"=="" set PORT=8730
python -m app.main
pause
