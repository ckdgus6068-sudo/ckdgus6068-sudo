@echo off
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" python -m venv .venv
call ".venv\Scripts\activate.bat"

echo Checking packages... (first time installs PaddlePaddle/PaddleOCR, can take several minutes)
pip install -q pip-system-certs pymupdf pillow numpy paddlepaddle paddleocr

REM PaddleOCR is lighter than EasyOCR; allow a few CPU threads for speed
set "OMP_NUM_THREADS=4"
set "PII_DEFAULT_ENGINE=paddleocr"

echo Starting program (PaddleOCR)...
echo NOTE: the FIRST masking downloads Korean models (needs internet, one time).
python gui.py

echo.
echo (Program closed. If model download failed, check internet/proxy and retry.)
pause
