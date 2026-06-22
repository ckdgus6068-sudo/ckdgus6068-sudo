@echo off
cd /d "%~dp0"

REM PaddleOCR needs Python 3.9-3.12 (NOT 3.13/3.14). Prefer 3.12, then 3.11.
set "PYEXE="
py -3.12 --version >nul 2>&1 && set "PYEXE=py -3.12"
if not defined PYEXE ( py -3.11 --version >nul 2>&1 && set "PYEXE=py -3.11" )
if not defined PYEXE (
  echo.
  echo [!] PaddleOCR requires Python 3.11 or 3.12, which was not found.
  echo     1^) Install Python 3.12 from https://www.python.org/downloads/
  echo        ^(check "Add python.exe to PATH" during install^)
  echo     2^) Run this file again.
  echo.
  pause
  exit /b
)

echo Using %PYEXE% for PaddleOCR.
if not exist ".venv_paddle\Scripts\python.exe" %PYEXE% -m venv .venv_paddle
call ".venv_paddle\Scripts\activate.bat"

echo Checking packages... (first time installs PaddlePaddle/PaddleOCR, can take several minutes)
pip install -q pip-system-certs pymupdf pillow numpy paddlepaddle paddleocr

set "OMP_NUM_THREADS=4"
set "PII_DEFAULT_ENGINE=paddleocr"

echo Starting program (PaddleOCR)...
echo NOTE: the FIRST masking downloads Korean models (needs internet, one time).
python gui.py

echo.
echo (Program closed. If model download failed, check internet/proxy and retry.)
pause
