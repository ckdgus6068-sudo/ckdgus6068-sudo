@echo off
REM ============================================================
REM  Run this ON the internal (offline) PC. No internet needed.
REM  Requires: Python installed (same version as on vmfort),
REM            and offline\wheels + models\easyocr present (from 준비_오프라인.bat).
REM ============================================================
cd /d "%~dp0"

if not exist "offline\wheels" (
  echo [!] offline\wheels folder not found.
  echo     Run the prepare-offline batch on vmfort first, then copy the folder here.
  pause
  exit /b
)

if not exist ".venv\Scripts\python.exe" python -m venv .venv
call ".venv\Scripts\activate.bat"

echo Installing packages from local wheels (no internet)...
pip install --no-index --find-links offline\wheels pymupdf pillow numpy easyocr

REM ---- memory/speed defaults (adjust if it crashes: lower THREADS) ----
set "TORCH_THREADS=2"
set "OMP_NUM_THREADS=2"
set "PII_RENDER_SCALE=2.0"
set "EASYOCR_CANVAS_SIZE=1280"
set "PII_DEFAULT_ENGINE=easyocr"

echo Starting program (offline, EasyOCR using bundled models)...
python gui.py

echo.
echo (Program closed. If install failed, your Python version must match vmfort's.)
pause
