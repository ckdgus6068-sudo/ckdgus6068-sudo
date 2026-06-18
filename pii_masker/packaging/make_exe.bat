@echo off
chcp 65001 >nul
REM ====================================================================
REM  exe 자동 생성 — vmfort(필요한 패키지가 이미 깔린 PC)에서 실행.
REM  반드시 easyocr 가 동작하던 그 환경(.venv 활성화 상태)에서 돌릴 것.
REM  사용법: pii_masker 폴더에서  packaging\make_exe.bat  더블클릭(또는 실행)
REM ====================================================================
cd /d "%~dp0\.."

echo [1/3] EasyOCR 한글 모델을 models\easyocr 로 복사
if not exist models\easyocr mkdir models\easyocr
copy /Y "%USERPROFILE%\.EasyOCR\model\*" models\easyocr\ >nul 2>&1
dir /b models\easyocr

echo.
echo [2/3] PyInstaller 준비
python -m pip install pyinstaller

echo.
echo [3/3] exe 빌드 (torch 포함이라 수 GB / 10~20분)
pyinstaller packaging\pii_masker.spec --noconfirm

echo.
echo ====================================================================
echo  완료되면:  dist\pii_masker\pii_masker.exe
echo  dist\pii_masker 폴더 전체를 USB로 복사해 오프라인 PC에서 실행하세요.
echo ====================================================================
pause
