@echo off
REM ============================================================
REM  Run this ON vmfort (internet OK) to build the offline bundle.
REM  It downloads packages and copies EasyOCR models into THIS folder.
REM  Then copy this WHOLE folder + a Python installer to USB.
REM ============================================================
cd /d "%~dp0"

echo [1/2] Downloading packages for offline install (torch included, can take minutes)...
python -m pip download pymupdf pillow numpy easyocr -d offline\wheels

echo.
echo [2/2] Copying EasyOCR Korean models into models\easyocr ...
if not exist models\easyocr mkdir models\easyocr
copy /Y "%USERPROFILE%\.EasyOCR\model\*" models\easyocr\ >nul 2>&1

echo.
echo ===== Check below: both should list files =====
echo -- offline\wheels --
dir /b offline\wheels
echo -- models\easyocr --
dir /b models\easyocr
echo.
echo If both have files: copy THIS folder + python-3.x.x-amd64.exe to USB.
echo (Tip: delete any .venv folders before copying - they are machine-specific.)
pause
