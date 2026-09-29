---
name: hwpx
description: 한글(HWPX, .hwpx) 문서를 만들고 읽고 편집하는 스킬. 사용자가 한글 파일, hwpx, 아래아한글, 기안문·공문 양식, 온나라 행정보고서 작성을 요청할 때 사용한다. ‘보고서’라는 단어만으로는 발동하지 않는다. Word(.docx) 문서에는 사용하지 않는다.
---

# HWPX 문서 생성·편집 스킬

## 개요

HWPX는 한컴오피스 한글의 개방형 문서 포맷이다. 내부는 **ZIP 패키지 + XML 파트** 구조이며, KS X 6101(OWPML) 표준에 기반한다. 이 스킬은 `python-hwpx` 라이브러리 또는 ZIP-level 치환으로 HWPX를 생성·편집한다.

## 설치

```bash
pip install python-hwpx --break-system-packages
```

> 설치가 막혀도 **ZIP-level 치환**(아래 함수)과 **온나라 행정보고서 빌더**(`scripts/report_builder_onnara.py`), `fix_namespaces.py`, `unify_layout.py` 는 파이썬 표준 라이브러리만 쓰므로 그대로 동작한다. `python-hwpx` 가 필요한 것은 `HwpxDocument.new()` 와 `ObjectFinder` 뿐이다.

---

## ⚠️ 가장 먼저: 무슨 문서인가?

```
사용자가 .hwpx 양식을 업로드?
  → 그 양식 + ZIP-level 치환
온나라 행정보고서(날짜·제목·문서요약·□◦-※ 본문·붙임)?
  → 아래 "온나라 행정보고서" 섹션 (onnara-report-template + report_builder_onnara.py)
그 밖의 보고서/공문/기안문?
  → assets/report-template.hwpx + ZIP-level 치환
아주 단순한 메모/목록?
  → HwpxDocument.new()
```

---

## 온나라 행정보고서 (□◦-※ 4단계)

온나라 행정보고 양식(날짜·제목·문서요약·본문 4단계·붙임)의 보고서는 아래를 사용한다.

- 양식: `assets/onnara-report-template.hwpx`
- 모듈: `scripts/report_builder_onnara.py` (`OnnaraReport`)
- 조립 규칙·예시: `references/onnara-report-build.md`
- 본문 내용·문체 규칙(배경-세부내용-향후계획, 기호 띄어쓰기, 내어쓰기, 65~75자, ▵ 나열, 관용 한자): `references/onnara-report-style.md`

본문 단계: □ 수준1 · ◦ 수준2 · - 수준3 · ※ 유의(각 `l1/l2/l3/note`). 빌더가 양식에서 서식 번호를 자동 탐지하고, 저장 직전 참조를 검증한다. 제목(앞에 붙던 따옴표 자동 제거)·**문서요약(`build(summary=[...])`)**·본문·표(2~6열, 머리행 #DFE6F7·아래 이중선, 상·하단 0.5mm 실선, 좌·우 선 없음, 첫 열 좁게)·붙임을 채운다. 날짜·부서 박스(`’26. 1. 1.(月)`, `○○국 / ○○과`)만 한글에서 직접 채운다(사진 미포함).

---

## ⚠️⚠️ 템플릿 선택 정책 ⚠️⚠️

### 1단계: 사용자 업로드 양식이 있는가?
`/mnt/user-data/uploads/` 에 `.hwpx` 가 있으면 **반드시 그 파일을 템플릿으로** 사용.

### 2단계: 기본 제공 양식
- 온나라 행정보고서 → `assets/onnara-report-template.hwpx` (위 섹션)
- 그 밖의 보고서 → `assets/report-template.hwpx`

### 3단계: HwpxDocument.new()는 최후의 수단
아주 단순한 메모·목록에만 허용. 양식이 필요한 문서는 `new()`로 만들지 않는다.

---

## 양식 활용 공통 워크플로우 (ZIP-level 치환)

```
[1] 양식을 /home/claude/ 로 복사
[2] ObjectFinder로 텍스트 전수 조사 → 플레이스홀더 파악
[3] ZIP-level 치환 (표 내부 포함. 동일 플레이스홀더 반복 시 순차 치환)
[4] fix_namespaces.py (필수)
[5] ObjectFinder로 검증
[6] /mnt/user-data/outputs/ 로 복사 → present_files
```

### HwpxDocument.open()은 사용하지 않는다
`python-hwpx` 버전에 따라 복잡한 양식을 파싱 못 할 수 있다. **ZIP-level 치환**이 안전하다.

### ZIP-level 치환 함수 (직접 포함)
```python
import zipfile, os

def zip_replace(src_path, dst_path, replacements):
    """HWPX ZIP 내 모든 Contents XML에서 텍스트 일괄 치환"""
    tmp = dst_path + ".tmp"
    with zipfile.ZipFile(src_path, "r") as zin:
        with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                data = zin.read(item.filename)
                if item.filename.startswith("Contents/") and item.filename.endswith(".xml"):
                    text = data.decode("utf-8")
                    for old, new in replacements.items():
                        text = text.replace(old, new)
                    data = text.encode("utf-8")
                zout.writestr(item, data)
    if os.path.exists(dst_path):
        os.remove(dst_path)
    os.rename(tmp, dst_path)

def zip_replace_sequential(src_path, dst_path, old, new_list):
    """동일 플레이스홀더를 순서대로 다른 값으로 (section XML에서 1개씩)"""
    tmp = dst_path + ".tmp"
    with zipfile.ZipFile(src_path, "r") as zin:
        with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                data = zin.read(item.filename)
                if "section" in item.filename and item.filename.endswith(".xml"):
                    text = data.decode("utf-8")
                    for new_val in new_list:
                        text = text.replace(old, new_val, 1)
                    data = text.encode("utf-8")
                zout.writestr(item, data)
    if os.path.exists(dst_path):
        os.remove(dst_path)
    os.rename(tmp, dst_path)
```

### 텍스트 전수 조사
```python
from hwpx import ObjectFinder
for r in ObjectFinder("양식.hwpx").find_all(tag="t"):
    if r.text and r.text.strip():
        print(repr(r.text))
```

---

## 기본 보고서 양식(report-template.hwpx)

구조: 1쪽 표지 / 2쪽 목차 / 3쪽~ 본문(결재란 + 제목 + □○―※ 계층).
주요 플레이스홀더: `○○기관`(기관명), `기본 보고서 양식`(제목), `2024. 5. 23.`(작성일), `제 목`(본문 제목), 본문은 `헤드라인M 폰트 16포인트(문단 위 15)`(□, 순차) 등.
본문 기호: □(16pt) → ○(15pt) → ―(15pt) → ※(13pt).
표지 상단의 기관 로고 자리는 투명 이미지(`BinData/image1.png`)로 비워 두었다. 로고가 필요하면 한글에서 그림을 바꿔 넣는다.

---

## 문서 유형별 스타일 가이드

- 온나라 행정보고서 → **`references/onnara-report-build.md`** + **`references/onnara-report-style.md`**
- 일반 보고서 → **`references/report-style.md`**
- 공문서(기안문) → **`references/official-doc-style.md`**
- 저수준 XML 조작 → **`references/xml-internals.md`**

---

## 표·레이아웃 공통 규칙

1. **표·사진 가로폭은 인쇄 영역에 딱 맞춰 전부 통일한다.** 인쇄 영역 = `pagePr` 의
   용지 폭 − 좌우 여백(A4 · 여백 5669 기준 **48190**). 빌드 후
   `scripts/unify_layout.py <파일.hwpx>` 를 돌리면 표 폭 통일과 줄나눔 전환을 한 번에 한다.
   - 폭 통일은 **행별 단순 재비율 금지**: 세로 병합(rowSpan) 셀이 걸친 행은 그 행 자체
     셀 합만 맞추면 실제 폭이 넘친다. 반드시 **열 격자 기준**으로
     열 폭을 조정한 뒤 셀 폭 = 차지하는 열들의 합으로 다시 쓴다. `unify_layout.py` 가 이 방식이다.
   - 검증은 행별 **실효 폭**(자기 셀 + 위 행에서 내려온 병합 셀 폭)으로 한다.
2. **줄 나눔 기준은 한글 글자로 한다 = `breakNonLatinWord="KEEP_WORD"`.**
   문단 모양 대화상자의 **‘글자’ = KEEP_WORD, ‘어절’ = BREAK_WORD** 로, 속성값 이름의 느낌과 반대다.
   BREAK_WORD 로 두면 산출물이 어절 단위가 된다. 영문 `breakLatinWord` 는 `KEEP_WORD`(단어) 유지.
   `unify_layout.py` 가 BREAK_WORD 를 KEEP_WORD 로 되돌린다.
3. 산출 직후 **기계 검증**을 습관화한다: 이번에 넣거나 고친 핵심 문자열 존재 · 표 수 · 표 실효 폭 ·
   곧은따옴표 여부. 편집 스크립트가 “적용 완료”를 출력해도 빌드된 hwpx 에서 문자열을 재검색해 확인한다.

---

## ⚠️ 필수 후처리: 네임스페이스 수정 + 참조 검증

> 빠뜨리면 한글에서 빈 페이지로 표시된다. **모든** 저장/치환 경로(빌더든 ZIP 치환이든 손작업이든) 뒤에 반드시 실행한다.

```python
import os, subprocess
SK = "<이 스킬 폴더 경로>"
subprocess.run(["python3", os.path.join(SK, "scripts/fix_namespaces.py"), "output.hwpx"], check=True)
```
(`report_builder_onnara.py` 의 `build()` 는 이를 자동 수행한다. `exec()` 말고 `subprocess.run()` 사용.)

`fix_namespaces.py` 는 네임스페이스를 고친 뒤, **본문(section)이 참조하는 모든 `paraPrIDRef`/`charPrIDRef` 가 `header.xml` 에 실제로 정의돼 있는지 검증**한다(최종 방어선). 끊긴 참조가 하나라도 있으면 빈 페이지가 될 파일을 그대로 통과시키지 않고 **exit code 2 로 멈춘다**(`check=True` 면 예외 발생).
- 이 오류가 나면 → 십중팔구 **양식의 `header.xml` 을 보존하지 않고 빈 문서로 새로 조립**한 것이다. `new()`/`open()` 으로 다시 만들지 말고, **양식 zip을 복사해 본문만 채우는 방식**(또는 `report_builder_onnara.py`)으로 다시 만든다.

---

## 주의사항

1. 양식 우선: 사용자 업로드 > 기본 제공 > `new()`.
2. ZIP-level 치환이 `HwpxDocument.open()`보다 안전.
3. 모든 저장/치환 후 `fix_namespaces.py` 필수(네임스페이스 + paraPr/charPr 참조 검증). 검증 실패(exit 2)면 양식 헤더를 보존하지 않은 것이므로, 양식 zip 복사 방식으로 다시 만든다.
4. 치환 전 ObjectFinder로 텍스트 전수 조사.
5. 동일 플레이스홀더 반복 → 순차 치환.
6. 따옴표는 둥근따옴표만(‘ ’ “ ”), 한국어 전용·한자 금지(온나라 행정보고서의 관용 한자는 `references/onnara-report-style.md` 의 예외를 따른다).
7. 공문서 날짜: `2026. 2. 13.` (월·일 앞 0 생략).
8. 줄 나눔 기준은 한글 **글자**(`breakNonLatinWord="KEEP_WORD"`), 영문은 단어(`breakLatinWord="KEEP_WORD"`) 유지.
   표·사진 폭은 인쇄 영역으로 통일. 둘 다 `scripts/unify_layout.py` 후처리로 보장한다.
9. 내용이 바뀐 파일은 파일명 번호를 올려 새로 저장하고, 같은 이름으로 덮어쓰지 않는다.
10. python-hwpx는 HWPX 전용. 레거시 `.hwp`는 별도 도구.
11. 글꼴은 파일에 포함되지 않는다. 양식에 쓰인 글꼴(HY헤드라인M, 휴먼명조, HY견고딕 등)이 열람하는 PC에 없으면 다른 글꼴로 대체되어 보인다.
