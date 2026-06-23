@echo off
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" python -m venv .venv
call ".venv\Scripts\activate.bat"

echo Checking packages... (first time installs EasyOCR/torch, can take several minutes)
pip install -q pymupdf pillow numpy easyocr

REM ====== Speed vs memory knob ======
REM  THREADS higher = FASTER but uses MORE memory.
REM  If the program crashes/closes (out of memory), lower THREADS to 2 or 1.
set "TORCH_THREADS=4"
set "OMP_NUM_THREADS=4"
REM  Image size: smaller = less memory, larger = more accurate.
set "PII_RENDER_SCALE=1.5"
set "EASYOCR_CANVAS_SIZE=1024"

echo Starting program (EasyOCR, threads=%OMP_NUM_THREADS%)...
echo TIP: close Chrome / Acrobat / other apps before masking.
python gui.py

echo.
echo (Program closed. If it crashed, lower TORCH_THREADS/OMP_NUM_THREADS to 2 in this file.)
pause
