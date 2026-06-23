@echo off
REM ====================================================================
REM  개인정보 마스킹 도구 — Windows 오프라인 빌드 스크립트
REM  사전: Python 3.11 설치, (tesseract 사용 시) tesseract-ocr 한국어 설치
REM  사용: 이 파일이 있는 폴더(packaging)의 상위(pii_masker)에서 실행
REM       packaging\build.bat
REM ====================================================================
setlocal
cd /d "%~dp0\.."

echo [1/4] 가상환경 생성
python -m venv .venv
call .venv\Scripts\activate.bat

echo [2/4] 의존성 설치 (사용할 엔진에 맞게 requirements.txt 편집 후)
python -m pip install --upgrade pip
pip install -r requirements.txt
pip install pyinstaller

echo [3/4] PyInstaller 빌드
pyinstaller packaging\pii_masker.spec --noconfirm

echo [4/4] 완료 - dist\pii_masker\pii_masker.exe
echo  (OCR/NER 모델을 쓰면 models\ 폴더에 미리 넣고 다시 빌드하세요)
endlocal
pause
