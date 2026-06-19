@echo off
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" python -m venv .venv
call ".venv\Scripts\activate.bat"

echo Checking packages... (first time installs EasyOCR/torch, can take several minutes)
pip install -q pymupdf pillow numpy easyocr

REM ---- Low-memory settings for ~6GB RAM PCs ----
set "PII_RENDER_SCALE=1.5"
set "EASYOCR_CANVAS_SIZE=1024"
set "OMP_NUM_THREADS=1"
set "TORCH_THREADS=1"

echo Starting program (EasyOCR, low-memory mode)...
echo TIP: close Chrome / Acrobat / other apps before masking.
python gui.py

echo.
echo (Program closed. If it crashed, lower EASYOCR_CANVAS_SIZE to 800 in this file.)
pause
