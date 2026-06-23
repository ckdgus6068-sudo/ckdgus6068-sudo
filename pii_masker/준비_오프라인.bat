@echo off
REM ============================================================
REM  Run this ON vmfort (internet OK) to build the offline bundle.
REM  Downloads packages + copies EasyOCR models + Tesseract program.
REM  Then copy this WHOLE folder + a Python 3.14 installer to USB.
REM ============================================================
cd /d "%~dp0"

echo [1/3] Downloading packages for offline install (torch included, can take minutes)...
py -3.14 -m pip download pymupdf pillow numpy easyocr pytesseract -d offline\wheels

echo.
echo [2/3] Copying EasyOCR Korean models into models\easyocr ...
if not exist models\easyocr mkdir models\easyocr
copy /Y "%USERPROFILE%\.EasyOCR\model\*" models\easyocr\ >nul 2>&1

echo.
echo [3/3] Copying Tesseract-OCR program (for PCs where EasyOCR/torch cannot run)...
if exist "C:\Program Files\Tesseract-OCR\tesseract.exe" (
  if not exist tesseract mkdir tesseract
  xcopy /E /I /Y "C:\Program Files\Tesseract-OCR" tesseract >nul
)

echo.
echo ===== Check below: each should list files =====
echo -- offline\wheels --
dir /b offline\wheels
echo -- models\easyocr --
dir /b models\easyocr
echo -- tesseract --
if exist tesseract\tesseract.exe (echo   tesseract.exe OK) else (echo   [!] NOT bundled - install Tesseract+Korean on vmfort first, then rerun)
echo.
echo If all OK: copy THIS folder + python-3.14.x-amd64.exe to USB.
echo (Delete any .venv* folders before copying - they are machine-specific.)
pause
