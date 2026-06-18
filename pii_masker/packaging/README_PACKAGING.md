# 오프라인 Windows 배포 가이드 (EasyOCR, 설치 권한 없는 PC용)

목표: **인터넷·관리자권한이 없는 PC**에서 USB로 받은 폴더의 `pii_masker.exe`만
더블클릭하면 동작하게 만든다. Python 설치가 필요 없다(모두 한 폴더에 포함).

빌드는 **인터넷이 되는(또는 모델을 이미 받은) PC**에서 한 번 하고, 결과 폴더를
USB로 오프라인 PC에 복사한다.

---

## 1. 빌드 PC 준비 (이미 EasyOCR을 한 번 돌려본 vmfort 등)

EasyOCR을 한 번 실행했다면 한글 모델이 이미 받혀 있다:
`C:\Users\<사용자>\.EasyOCR\model\` (예: `craft_mlt_25k.pth`, `korean_g2.pth` 등)

이 파일들을 프로젝트의 `models\easyocr\` 로 복사한다:

```cmd
mkdir models\easyocr
copy "%USERPROFILE%\.EasyOCR\model\*" models\easyocr\
```

> 모델이 아직 없으면, 인터넷 되는 곳에서 프로그램을 한 번 실행(`python gui.py`)해
> 마스킹을 1회 돌리면 위 폴더에 모델이 생긴다. 그 뒤 위 copy를 수행.

`pii_masker.spec`는 `models\easyocr\`가 있으면 자동으로 함께 묶고, 실행 시
프로그램이 그 모델을 사용하며 **다운로드를 시도하지 않는다**(오프라인 OK).

## 2. 빌드

```cmd
pip install pyinstaller
pyinstaller packaging\pii_masker.spec --noconfirm
```

- 산출물: `dist\pii_masker\` (수 GB — torch 포함이라 큼)
- torch 관련 경고가 나와도 빌드가 끝나 `dist\pii_masker\pii_masker.exe`가 생기면 성공.

## 3. USB 복사 → 오프라인 PC 실행

1. `dist\pii_masker\` **폴더 전체**를 USB로 복사
2. 오프라인 PC의 적당한 위치(예: `D:\pii_masker\`)에 붙여넣기
3. `pii_masker.exe` 더블클릭 → 검은 콘솔 + 마스킹 창이 뜸
4. PDF 추가 → 관련자 명단 → 엔진 `easyocr` → 출력폴더 → **마스킹 실행**
   - 인터넷 없이 동작한다(동봉 모델 사용).

## 4. 메모리 부족 대비

EasyOCR(torch)은 메모리를 많이 쓴다. 오프라인 PC RAM이 작으면 처리 해상도를 낮춘다.
`pii_masker.exe`와 같은 폴더에서 콘솔로 환경변수를 주고 실행하거나, 바로가기를
만들어 다음처럼 설정한다(기본값은 이미 2):

```cmd
set PII_RENDER_SCALE=1.5
pii_masker.exe
```

값이 작을수록 메모리↓(정확도는 약간↓). 권장 1.5~2.

## 산출 파일 / 보안

- 출력 폴더에 `<원본>_masked.pdf`(검은칠본), `<원본>_audit.json`,
  `mapping.json`(가명↔실명 대응표).
- **`mapping.json`에는 실명이 들어있다.** 출력 폴더 접근을 통제하고, 외부(클로드 등)
  에는 `_masked.pdf`만 제공한다.

## 참고 — 한글 폰트

라벨용 한글 폰트는 `assets\NanumGothic.ttf`로 동봉된다(없으면 Windows 맑은 고딕).
