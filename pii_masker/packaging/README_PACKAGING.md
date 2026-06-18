# 오프라인 Windows 패키징 가이드

이 도구를 인터넷 없는 Windows에서 단일 실행본(.exe)으로 배포하는 절차.

## 1. 사전 준비 (빌드 PC, 인터넷 필요 — 1회)

- Python 3.11 설치
- `requirements.txt`에서 **사용할 OCR 엔진의 주석을 해제**
  - 권장: PaddleOCR (`paddlepaddle`, `paddleocr`) — 한글 정확도 높고 비교적 가벼움
  - 또는 EasyOCR (`easyocr`) — torch 동반으로 용량 큼
  - tesseract 사용 시: Windows용 **tesseract-ocr(한국어 포함)** 별도 설치
- (NER 쓸 경우) `transformers` 주석 해제

## 2. 모델 동봉 (오프라인 핵심)

인터넷 없는 환경에서 돌리려면 OCR/NER 모델을 미리 받아 `models/`에 넣는다.

- **PaddleOCR**: 한글 검출(det)·인식(rec) 모델을 받아
  `models/det/`, `models/rec/`에 배치. 실행 시 `--paddle-model-dir models`로 지정
  (GUI 사용 시 추후 옵션 노출 예정).
- **EasyOCR**: 인터넷 PC에서 한 번 실행하면 `~/.EasyOCR/model`에 모델이 받힌다.
  그 폴더를 `models/easyocr/`로 복사해 동봉.
- **NER(transformers)**: KoELECTRA(MODU-NER) 등 모델 디렉터리를 `models/koelectra-ner/`에
  복사하고 `--ner-model models/koelectra-ner` 로 지정.

`pii_masker.spec`는 `models/` 폴더가 있으면 자동으로 함께 패키징한다.

## 3. 빌드

```
packaging\build.bat
```

또는 수동:

```
pyinstaller packaging\pii_masker.spec --noconfirm
```

`pii_masker.spec` 상단에서 **사용하는 엔진의 `add(...)` 줄을 활성화**해야
해당 라이브러리가 함께 묶인다.

## 4. 배포 / 실행

- 산출물 `dist\pii_masker\` 폴더를 통째로 대상 PC에 복사
- `pii_masker.exe` 실행 → PDF 추가 → 관련자 명단 입력 → 엔진 선택 → 실행
- 결과: `<원본>_masked.pdf`, `<원본>_audit.json`, `mapping.json`(가명 대응표)

## 참고 — 한글 폰트

라벨용 한글 폰트는 `assets/NanumGothic.ttf`로 동봉되며, 없으면 Windows
기본 글꼴(맑은 고딕)을 자동 사용한다. `PII_FONT` 환경변수로 직접 지정도 가능.

## 보안 메모

- `mapping.json`(가명↔실명 대응표)에는 실명 정보가 담기므로 **출력 폴더 접근을
  통제**하고, 외부(LLM 등)에는 마스킹된 PDF만 제공한다.
- 출력 PDF는 이미지로 재구성되어 원본 텍스트가 남지 않는다(텍스트 추출 누출 방지).
