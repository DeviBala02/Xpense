@echo off
REM BudgetBee: one-click start for Windows. Builds the web page, then starts everything in this one window.
cd /d "%~dp0"

echo.
echo === Step 1 of 2: building the web page (first time takes a few minutes) ===
cd frontend
if not exist node_modules (
  call npm install
  if errorlevel 1 goto :fail
)
call npm run build
if errorlevel 1 goto :fail

echo.
echo === Step 2 of 2: starting BudgetBee ===
echo When you see "Started BudgetBeeApplication", open  http://localhost:8080  in your browser.
echo To use it on your phone, see the guide (same Wi-Fi, then http://YOUR-COMPUTER-IP:8080).
echo Keep this window open. Press Ctrl+C to stop.
cd ..\backend
call mvn spring-boot:run
goto :eof

:fail
echo.
echo Something went wrong above. Read the red text, or send it to your helper.
pause
