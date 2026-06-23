@echo off
REM ============================================================
REM  Offline Tesseract launcher for the internal PC.
REM  Works on CPUs WITHOUT AVX (where EasyOCR/torch fails).
REM  Needs: Python 3.14 + offline\wheels + bundled tesseract folder.
REM ============================================================
cd /d "%~dp0"

if not exist "offline\wheels" (
  echo [!] offline\wheels not found. Run the prepare batch on vmfort first.
  pause
  exit /b
)
py -3.14 --version >nul 2>&1 || (
  echo [!] Python 3.14 not found. Install python-3.14.x-amd64.exe first.
  pause
  exit /b
)

if not exist ".venv\Scripts\python.exe" py -3.14 -m venv .venv
call ".venv\Scripts\activate.bat"

echo Installing packages from local wheels (no internet)...
pip install --no-index --find-links offline\wheels pymupdf pillow numpy pytesseract

REM Use the bundled Tesseract if present (no install needed), else system one
if exist "%~dp0tesseract\tesseract.exe" set "TESSERACT_CMD=%~dp0tesseract\tesseract.exe"
set "PATH=%PATH%;C:\Program Files\Tesseract-OCR"
set "PII_DEFAULT_ENGINE=tesseract"
set "PII_RENDER_SCALE=3.0"

echo Starting program (offline, Tesseract - runs on any CPU)...
python gui.py

echo.
echo (Program closed.)
pause
