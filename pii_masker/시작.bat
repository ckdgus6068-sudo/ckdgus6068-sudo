@echo off
chcp 65001 >nul
REM ====================================================================
REM  개인정보 마스킹 도구 - 원클릭 실행 (이 파일을 더블클릭하세요)
REM  처음 실행: 가상환경 + 필요한 프로그램을 자동 설치(인터넷 필요, 1회)
REM  이후 실행: 바로 프로그램 창이 뜸
REM ====================================================================
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" (
    echo [최초 설정] 가상환경을 만들고 필요한 프로그램을 설치합니다...
    python -m venv .venv
    call ".venv\Scripts\activate.bat"
    python -m pip install --upgrade pip
    pip install pymupdf pillow pytesseract
) else (
    call ".venv\Scripts\activate.bat"
)

REM Tesseract 설치 경로(기본값) - 설치 위치가 다르면 이 줄만 수정
set PATH=%PATH%;C:\Program Files\Tesseract-OCR

echo.
echo 프로그램 창을 엽니다...
python gui.py

echo.
echo (프로그램이 닫혔습니다. 오류가 있으면 위 메시지를 확인하세요)
pause
