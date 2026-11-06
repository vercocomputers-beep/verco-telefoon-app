@echo off
:: Verco Telefoon app - deze map naar GitHub (starter, vraagt om beheerdersrechten)
net session >nul 2>&1
if %errorlevel% neq 0 (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process cmd -ArgumentList '/c \"\"%~f0\"\" %*' -Verb RunAs"
    exit /b
)
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Naar GitHub.ps1" -Pauze %*
