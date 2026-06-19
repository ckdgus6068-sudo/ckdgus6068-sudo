@echo off
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" python -m venv .venv
call ".venv\Scripts\activate.bat"

echo Checking required packages...
pip install -q pymupdf pillow pytesseract numpy

set "PATH=%PATH%;C:\Program Files\Tesseract-OCR"
echo Starting program...
python gui.py

echo.
echo (Program closed. If there was an error, read the messages above.)
pause
