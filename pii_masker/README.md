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
- `masker.py` — 전체 파이프라인 (OCR·탐지·검은칠·PDF 재생성·감사 리포트)
- `parties.example.json` — 관련자 명단 시드(고소인/피의자 이름·식별값 → 가명)

## 사용법

```bash
python3 masker.py 입력.pdf 출력.pdf \
    --parties parties.json \
    --mapping mapping.json \
    --audit audit.json
```

- `--parties` : 미리 아는 인물(이름·주민번호 등)을 라벨에 고정 매핑 → 재현율 향상
- `--mapping` : 가명 대응표(복원용) 저장 경로
- `--audit`   : 무엇을 어떤 근거로 가렸는지, 저신뢰 OCR 항목은 무엇인지 기록

## 의존성

- PyMuPDF, pytesseract(+ tesseract-ocr, tesseract-ocr-kor), Pillow
- 한글 라벨 폰트: NanumGothic

## 알려진 한계 (개선 진행 중)

- OCR이 글자를 깨뜨린 이름은 정확매칭이 실패할 수 있음 → OCR 엔진 업그레이드/퍼지 매칭 검토
- 시드에 없는 제3자 이름·주소는 미탐지 → 한글 NER 추가 검토
