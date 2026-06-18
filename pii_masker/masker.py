"""오프라인 개인정보 마스킹 파이프라인 (프로토타입).

스캔 PDF → 페이지 렌더 → OCR(좌표 포함) → 개인정보 탐지
→ 검은칠 + 한글 가명 라벨 → 이미지로 PDF 재생성 → 감사 리포트.

전 과정 로컬에서 동작(인터넷 불필요). 출력 PDF는 이미지로만 구성되어
원본 텍스트가 남지 않는다(텍스트 추출로 개인정보가 새지 않음).
"""
import io
import json
import re
from dataclasses import dataclass, asdict, field
from difflib import SequenceMatcher
from pathlib import Path

import fitz  # PyMuPDF
import pytesseract
from pytesseract import Output
from PIL import Image, ImageDraw, ImageFont

from detectors import detect_structured, Span
from pseudonym import Pseudonymizer

# 렌더 배율(72dpi 기준) — 3이면 약 216dpi
RENDER_SCALE = 3
# 한글 라벨용 폰트
FONT_PATH = "/usr/share/fonts/truetype/nanum/NanumGothic.ttf"
# OCR 신뢰도 경고 임계값
LOW_CONF = 60
# 시드 이름 퍼지 매칭 유사도 임계값(0~1) — 낮을수록 과매칭 위험
FUZZY_THRESHOLD = 0.8


@dataclass
class Party:
    """사용자가 미리 제공하는 관련자(고소인/피의자 등) 시드."""
    label: str                       # 부여할 가명 (예: 피의자1)
    names: list[str] = field(default_factory=list)   # 이름 표기 변형들
    ids: list[str] = field(default_factory=list)     # 주민번호 등 식별값


@dataclass
class AuditItem:
    page: int
    ptype: str
    label: str
    ocr_text: str
    conf: float
    bbox: list[int]


def _norm(s: str) -> str:
    return re.sub(r"\s+", "", s)


def _load_parties(path: str | None) -> list[Party]:
    if not path or not Path(path).exists():
        return []
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    return [Party(**p) for p in data.get("parties", [])]


class Masker:
    def __init__(self, parties_path=None, mapping_path=None):
        self.parties = _load_parties(parties_path)
        self.pseudo = Pseudonymizer(mapping_path)
        self.font = ImageFont.truetype(FONT_PATH, 28)
        self.audit: list[AuditItem] = []
        # 시드 식별값/이름을 미리 라벨에 고정 등록
        for p in self.parties:
            for v in p.ids:
                self.pseudo.label_for("RRN", _norm(v), fixed_label=p.label)

    # --- 한 페이지 처리 ---------------------------------------------------
    def _ocr_lines(self, img: Image.Image):
        """OCR 결과를 줄 단위로 묶어 (line_text, words) 리스트로 반환.

        words: [(text, l, t, w, h, conf), ...]
        """
        d = pytesseract.image_to_data(img, lang="kor+eng", output_type=Output.DICT)
        lines: dict[tuple, list] = {}
        n = len(d["text"])
        for i in range(n):
            txt = d["text"][i]
            if not txt.strip():
                continue
            key = (d["block_num"][i], d["par_num"][i], d["line_num"][i])
            lines.setdefault(key, []).append(
                (txt, d["left"][i], d["top"][i], d["width"][i], d["height"][i],
                 float(d["conf"][i]))
            )
        return list(lines.values())

    def _find_party_boxes(self, all_words):
        """페이지 전체 단어 흐름에서 시드 이름을 찾는다(줄 경계 넘어 매칭).

        all_words: [(text, l, t, w, h, conf, line_idx), ...] (읽기 순서)
        반환: [(label, bbox, conf, ocr_text), ...] — 이름이 여러 줄에 걸치면
        줄별로 박스를 나눠 과도한 검은칠을 막는다.
        """
        # 공백 제거한 페이지 문자열 + 각 문자의 (단어 인덱스, 줄 인덱스)
        despaced, char_word, char_line = [], [], []
        for wi, w in enumerate(all_words):
            for ch in w[0]:
                if not ch.isspace():
                    despaced.append(ch)
                    char_word.append(wi)
                    char_line.append(w[6])
        despaced = "".join(despaced)
        N = len(despaced)

        # 매칭할 이름 키 목록 (긴 이름부터 — 더 구체적인 매칭 우선)
        keys = []
        for party in self.parties:
            for name in party.names:
                k = _norm(name)
                if len(k) >= 2:
                    keys.append((len(k), k, party.label))
        keys.sort(reverse=True)

        claimed = [False] * N  # 같은 글자 중복 매칭 방지
        found: list[tuple[int, int, str]] = []  # (start, end, label)

        # 1) 페이지 전체 '정확' 매칭 — 줄바꿈으로 쪼개진 이름도 잡고, 오탐 없음
        for L, key, label in keys:
            start = 0
            while True:
                pos = despaced.find(key, start)
                if pos < 0:
                    break
                if not any(claimed[pos:pos + L]):
                    found.append((pos, pos + L, label))
                    for j in range(pos, pos + L):
                        claimed[j] = True
                start = pos + L

        # 2) '한 줄 안' 퍼지 매칭 — OCR 글자오류(여인인석 등) 흡수. 줄 경계는 넘지 않음
        for L, key, label in keys:
            i = 0
            while i <= N - 2:
                best = None  # (ratio, end)
                for w in range(max(2, L - 1), L + 3):
                    e = i + w
                    if e > N:
                        break
                    if len(set(char_line[i:e])) != 1:  # 한 줄 안에서만
                        continue
                    r = SequenceMatcher(None, despaced[i:e], key).ratio()
                    if r >= FUZZY_THRESHOLD and (best is None or r > best[0]):
                        best = (r, e)
                if best and not any(claimed[i:best[1]]):
                    s2, e2 = self._tighten(despaced, i, best[1], key)
                    found.append((s2, e2, label))
                    for j in range(s2, e2):
                        claimed[j] = True
                    i = e2
                else:
                    i += 1

        # 매칭 구간 → 줄별 박스
        results = []
        for s, e, label in found:
            word_idxs = sorted(set(char_word[s:e]))
            by_line: dict[int, list[int]] = {}
            for i in word_idxs:
                by_line.setdefault(all_words[i][6], []).append(i)
            for idxs in by_line.values():  # 이름이 여러 줄에 걸치면 줄별 박스
                xs = [all_words[i][1] for i in idxs]
                ys = [all_words[i][2] for i in idxs]
                xe = [all_words[i][1] + all_words[i][3] for i in idxs]
                ye = [all_words[i][2] + all_words[i][4] for i in idxs]
                conf = min(all_words[i][5] for i in idxs)
                bbox = [min(xs), min(ys), max(xe), max(ye)]
                txt = " ".join(all_words[i][0] for i in idxs)
                results.append((label, bbox, conf, txt))
        return results

    @staticmethod
    def _tighten(despaced, s, e, key):
        """퍼지 창 양끝의 미매칭 글자를 잘라 박스를 키워드에 밀착시킨다."""
        sm = SequenceMatcher(None, despaced[s:e], key)
        blocks = [b for b in sm.get_matching_blocks() if b.size > 0]
        if not blocks:
            return s, e
        return s + blocks[0].a, s + blocks[-1].a + blocks[-1].size

    def _spans_to_boxes(self, line_text, words, spans):
        """줄 내 문자 구간(span)을 OCR 단어 박스들과 매칭해 통합 박스 산출."""
        # 단어별 줄 문자열 내 위치 계산 (단어 사이 공백 1칸으로 재구성)
        positions, cursor, parts = [], 0, []
        for w in words:
            t = w[0]
            parts.append(t)
            positions.append((cursor, cursor + len(t)))
            cursor += len(t) + 1  # +1 공백
        results = []
        for sp in spans:
            involved = [i for i, (ws, we) in enumerate(positions)
                        if not (sp.end <= ws or sp.start >= we)]
            if not involved:
                continue
            xs = [words[i][1] for i in involved]
            ys = [words[i][2] for i in involved]
            xe = [words[i][1] + words[i][3] for i in involved]
            ye = [words[i][2] + words[i][4] for i in involved]
            conf = min(words[i][5] for i in involved)
            bbox = [min(xs), min(ys), max(xe), max(ye)]
            ocr_text = " ".join(words[i][0] for i in involved)
            results.append((sp, bbox, conf, ocr_text))
        return results

    def _redact(self, draw: ImageDraw.ImageDraw, bbox, label):
        """검은 박스 + 흰 글씨 라벨."""
        x0, y0, x1, y1 = bbox
        pad = 2
        draw.rectangle([x0 - pad, y0 - pad, x1 + pad, y1 + pad], fill="black")
        # 박스 높이에 맞춰 폰트 크기 조정
        h = max(12, int((y1 - y0) * 0.8))
        font = ImageFont.truetype(FONT_PATH, h)
        tb = draw.textbbox((0, 0), label, font=font)
        tw, th = tb[2] - tb[0], tb[3] - tb[1]
        # 박스 폭을 넘으면 줄여서 맞춤
        while tw > (x1 - x0) and h > 9:
            h -= 2
            font = ImageFont.truetype(FONT_PATH, h)
            tb = draw.textbbox((0, 0), label, font=font)
            tw, th = tb[2] - tb[0], tb[3] - tb[1]
        draw.text((x0 + 1, y0 + max(0, ((y1 - y0) - th) // 2) - tb[1]),
                  label, fill="white", font=font)

    def process(self, in_pdf: str, out_pdf: str, audit_path: str | None = None):
        doc = fitz.open(in_pdf)
        out_images: list[Image.Image] = []
        for pno, page in enumerate(doc):
            pix = page.get_pixmap(matrix=fitz.Matrix(RENDER_SCALE, RENDER_SCALE))
            img = Image.open(io.BytesIO(pix.tobytes("png"))).convert("RGB")
            draw = ImageDraw.Draw(img)
            ocr_lines = self._ocr_lines(img)
            # 페이지 전체 단어 흐름(줄 인덱스 포함) — 이름의 줄 경계 매칭용
            all_words = []
            for li, words in enumerate(ocr_lines):
                for w in words:
                    all_words.append((*w, li))

            # 1) 정형 개인정보: 줄 단위 (RRN/전화 등은 줄을 넘지 않음)
            for words in ocr_lines:
                line_text = " ".join(w[0] for w in words)
                spans = detect_structured(line_text)
                for sp, bbox, conf, ocr_text in self._spans_to_boxes(line_text, words, spans):
                    label = self.pseudo.label_for(sp.ptype, sp.value)
                    self._redact(draw, bbox, label)
                    self.audit.append(AuditItem(
                        page=pno + 1, ptype=sp.ptype, label=label,
                        ocr_text=ocr_text, conf=round(conf, 1),
                        bbox=[int(b) for b in bbox]))

            # 2) 시드 이름/법인: 페이지 전체에서 매칭 (줄바꿈으로 쪼개져도 잡음)
            for label, bbox, conf, ocr_text in self._find_party_boxes(all_words):
                self._redact(draw, bbox, label)
                self.audit.append(AuditItem(
                    page=pno + 1, ptype="NAME", label=label,
                    ocr_text=ocr_text, conf=round(conf, 1),
                    bbox=[int(b) for b in bbox]))
            out_images.append(img)

        out_images[0].save(out_pdf, save_all=True, append_images=out_images[1:])
        self.pseudo.save()
        if audit_path:
            low = [a for a in self.audit if a.conf < LOW_CONF]
            report = {
                "input": in_pdf,
                "output": out_pdf,
                "total_redactions": len(self.audit),
                "low_confidence_count": len(low),
                "items": [asdict(a) for a in self.audit],
            }
            Path(audit_path).write_text(
                json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        return self.audit


if __name__ == "__main__":
    import argparse
    ap = argparse.ArgumentParser(description="오프라인 개인정보 마스킹")
    ap.add_argument("input")
    ap.add_argument("output")
    ap.add_argument("--parties", help="관련자 명단 JSON")
    ap.add_argument("--mapping", help="가명 대응표 저장 경로")
    ap.add_argument("--audit", help="감사 리포트 저장 경로")
    args = ap.parse_args()
    m = Masker(parties_path=args.parties, mapping_path=args.mapping)
    items = m.process(args.input, args.output, audit_path=args.audit)
    print(f"마스킹 완료: {len(items)}건 → {args.output}")
