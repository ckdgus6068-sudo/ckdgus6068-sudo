"""정형(定型) 개인정보 탐지 규칙.

형식이 일정한 항목(주민등록번호·전화·계좌 등)을 정규식으로 찾는다.
OCR 텍스트는 공백/오타가 섞이므로 어느 정도 느슨하게 매칭한다.
우선순위가 높은(구체적인) 패턴부터 적용해 겹침을 방지한다.
"""
import re
from dataclasses import dataclass


@dataclass
class Span:
    """텍스트 한 줄 안에서 찾은 개인정보 구간."""
    start: int          # 줄 문자열 내 시작 인덱스
    end: int            # 끝 인덱스(미포함)
    ptype: str          # 개인정보 종류 코드
    value: str          # 정규화한 대표값(가명 매핑 키)


# (코드, 정규식) — 위에 있을수록 우선순위가 높다.
_PATTERNS = [
    # 주민등록번호: 6자리-7자리 (공백 허용)
    ("RRN", re.compile(r"\d{6}\s*-\s*\d{7}")),
    # 외국인등록번호도 동일 형식이라 RRN으로 함께 처리됨.
    # 사업자등록번호: 3-2-5
    ("BIZNO", re.compile(r"\d{3}-\d{2}-\d{5}")),
    # 법인등록번호: 6-7 (RRN과 형식이 같아 RRN 다음 순위로는 의미 없음 → 생략)
    # 이메일
    ("EMAIL", re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")),
    # 전화/팩스: 02.1234.5678 / 010-1234-5678 / 02-123-4567
    ("PHONE", re.compile(r"0\d{1,2}[.\-)\s]\s?\d{3,4}[.\-\s]\s?\d{4}")),
    # 계좌번호: 2~6자리 그룹이 '-'로 3개 이상 (RRN/사업자/전화 뒤 순위)
    ("ACCOUNT", re.compile(r"\d{2,6}-\d{2,6}-\d{2,6}(?:-\d{1,6})?")),
    # 사건/접수번호: 2024-1234 형태 (연도로 시작)
    ("CASENO", re.compile(r"20\d{2}\s*-\s*\d{3,6}\b")),
]


def _norm(value: str) -> str:
    """가명 매핑 키로 쓸 정규화값 — 공백 제거."""
    return re.sub(r"\s+", "", value)


def detect_structured(text: str) -> list[Span]:
    """한 줄 문자열에서 정형 개인정보 구간들을 우선순위·비중첩으로 반환."""
    claimed: list[tuple[int, int]] = []

    def overlaps(s: int, e: int) -> bool:
        return any(not (e <= cs or s >= ce) for cs, ce in claimed)

    spans: list[Span] = []
    for ptype, pat in _PATTERNS:
        for m in pat.finditer(text):
            s, e = m.start(), m.end()
            if overlaps(s, e):
                continue
            claimed.append((s, e))
            spans.append(Span(s, e, ptype, _norm(m.group())))
    spans.sort(key=lambda x: x.start)
    return spans
