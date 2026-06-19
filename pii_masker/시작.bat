@echo off
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" goto setup
call ".venv\Scripts\activate.bat"
goto run

:setup
echo [Setup] Creating virtual environment and installing packages (one time)...
python -m venv .venv
call ".venv\Scripts\activate.bat"
python -m pip install --upgrade pip
pip install pymupdf pillow pytesseract

:run
set "PATH=%PATH%;C:\Program Files\Tesseract-OCR"
echo Starting program...
python gui.py

echo.
echo (Program closed. If there was an error, read the messages above.)
pause
