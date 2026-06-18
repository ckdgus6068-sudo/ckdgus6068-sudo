# 개인정보 마스킹 도구 (오프라인 프로토타입)

스캔/텍스트 PDF에서 개인정보를 자동 탐지해 **검은칠 + 일관된 한글 가명 라벨**로
마스킹한 PDF를 만든다. 전 과정 로컬에서 동작하며 인터넷이 필요 없다.
마스킹된 결과물만 외부 LLM에 제공해 수사보고서 등을 작성하기 위한 용도.

## 파이프라인

```
PDF → 페이지 이미지 렌더 → OCR(좌표 포함) → 개인정보 탐지
    → 검은칠 + 가명 라벨 → 이미지 기반 PDF 재생성 → 감사 리포트(JSON)
```

출력 PDF는 이미지로만 구성되어 원본 텍스트가 남지 않는다(텍스트 추출 누출 방지).

## 구성

- `detectors.py` — 정형 개인정보(주민번호·전화·사업자번호·이메일·계좌·사건번호) 정규식
- `pseudonym.py` — 일관된 가명화 + 대응표(로컬 JSON) 저장/복원
- `ocr_backends.py` — 교체 가능한 OCR 백엔드(Tesseract / EasyOCR), 글자 단위 토큰으로 정규화
- `masker.py` — 전체 파이프라인 (OCR·탐지·검은칠·PDF 재생성·감사 리포트)
- `parties.example.json` — 관련자 명단 시드(고소인/피의자 이름·식별값 → 가명)

## 사용법

```bash
python3 masker.py 입력.pdf 출력.pdf \
    --parties parties.json \
    --mapping mapping.json \
    --audit audit.json \
    --engine easyocr        # tesseract(기본) | easyocr
```

- `--parties` : 미리 아는 인물(이름·주민번호 등)을 라벨에 고정 매핑 → 재현율 향상
- `--mapping` : 가명 대응표(복원용) 저장 경로
- `--audit`   : 무엇을 어떤 근거로 가렸는지, 저신뢰 OCR 항목은 무엇인지 기록
- `--engine`  : OCR 엔진. 한글 인식 정확도는 `easyocr`가 높고, 속도·용량은 `tesseract`가 가볍다

## 탐지 전략 (하이브리드)

1. **정형 정보**(주민번호·전화 등): 정규식 — 안정적
2. **관련자 이름/법인**(시드): 페이지 전체 정확 매칭(줄바꿈 분리 대응) +
   한 줄 내 퍼지 매칭(OCR 글자오류 흡수). 이름 길이에 따라 임계값 조정.
3. 검은칠 박스는 누락보다 약간의 과(過)마스킹이 안전하도록 가로로 확장.

## 의존성

- PyMuPDF, Pillow, 한글 폰트(NanumGothic)
- Tesseract 백엔드: pytesseract + tesseract-ocr, tesseract-ocr-kor
- EasyOCR 백엔드: easyocr(+torch) — 인식률은 높으나 용량이 큼(오프라인 패키징 시 고려)

## 알려진 한계 (개선 진행 중)

- 시드에 없는 제3자 이름·주소는 아직 미탐지 → 한글 NER 추가 예정
- EasyOCR 박스 비례분할 특성상 매칭 꼬리에 일반 단어(주식회사 등) 일부가 남을 수 있음
- 완전 자동 동작: 저신뢰(OCR conf 낮음) 항목은 감사 리포트로 추적 가능
