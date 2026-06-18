"""오프라인 개인정보 마스킹 파이프라인 (프로토타입).

스캔/텍스트 PDF → 페이지 렌더 → OCR(글자 단위 좌표) → 개인정보 탐지
→ 검은칠 + 한글 가명 라벨 → 이미지로 PDF 재생성 → 감사 리포트.

전 과정 로컬에서 동작(인터넷 불필요). 출력 PDF는 이미지로만 구성되어
원본 텍스트가 남지 않는다(텍스트 추출로 개인정보가 새지 않음).

OCR 토큰을 글자 단위로 통일하므로 탐지 구간이 박스에 정밀하게 밀착한다.
"""
import io
import json
import re
from dataclasses import dataclass, asdict, field
from difflib import SequenceMatcher
from pathlib import Path

import fitz  # PyMuPDF
from PIL import Image, ImageDraw, ImageFont

from detectors import detect_structured
from pseudonym import Pseudonymizer
import ocr_backends

# 렌더 배율(72dpi 기준) — 3이면 약 216dpi
RENDER_SCALE = 3
# 한글 라벨용 폰트
FONT_PATH = "/usr/share/fonts/truetype/nanum/NanumGothic.ttf"
# OCR 신뢰도 경고 임계값
LOW_CONF = 0.6
# 시드 이름 퍼지 매칭 유사도 임계값 — 짧은 이름은 오탐 방지를 위해 높게,
# 긴 이름(법인명 등)은 OCR 오류를 더 허용하도록 낮게.
def _fuzzy_threshold(key_len: int) -> float:
    return 0.8 if key_len < 5 else 0.7

# token = (char, x0, y0, x1, y1, conf, line_idx)
CH, X0, Y0, X1, Y1, CONF, LINE = range(7)


@dataclass
class Party:
    """사용자가 미리 제공하는 관련자(고소인/피의자 등) 시드."""
    label: str
    names: list[str] = field(default_factory=list)
    ids: list[str] = field(default_factory=list)


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


def _load_parties(path):
    if not path or not Path(path).exists():
        return []
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    return [Party(**p) for p in data.get("parties", [])]


def _union(tokens):
    """토큰 목록의 통합 바운딩 박스와 최소 신뢰도, 텍스트."""
    x0 = min(t[X0] for t in tokens)
    y0 = min(t[Y0] for t in tokens)
    x1 = max(t[X1] for t in tokens)
    y1 = max(t[Y1] for t in tokens)
    conf = min(t[CONF] for t in tokens)
    return [x0, y0, x1, y1], conf, "".join(t[CH] for t in tokens)


class Masker:
    def __init__(self, parties_path=None, mapping_path=None, ocr_engine="tesseract"):
        self.parties = _load_parties(parties_path)
        self.pseudo = Pseudonymizer(mapping_path)
        self.ocr_engine = ocr_engine
        self._reader = None  # EasyOCR 지연 초기화
        self.audit: list[AuditItem] = []
        # 시드 식별값을 라벨에 고정 등록(여러 문서에서 일관)
        for p in self.parties:
            for v in p.ids:
                self.pseudo.label_for("RRN", _norm(v), fixed_label=p.label)

    # --- OCR -------------------------------------------------------------
    def _ocr_lines(self, img):
        """선택된 엔진으로 글자 단위 토큰 줄 목록을 반환."""
        if self.ocr_engine == "easyocr":
            if self._reader is None:
                import easyocr
                self._reader = easyocr.Reader(["ko", "en"], gpu=False, verbose=False)
            return ocr_backends.easyocr_lines(img, self._reader)
        return ocr_backends.tesseract_lines(img)

    # --- 시드 이름 매칭 ---------------------------------------------------
    def _find_party_boxes(self, all_chars):
        """페이지 전체 글자 흐름에서 시드 이름을 찾는다.

        all_chars: [token, ...] (읽기 순서, 글자 단위). despaced 인덱스 = 토큰 인덱스.
        """
        despaced = "".join(t[CH] for t in all_chars)
        N = len(despaced)

        keys = []
        for party in self.parties:
            for name in party.names:
                k = _norm(name)
                if len(k) >= 2:
                    keys.append((len(k), k, party.label))
        keys.sort(reverse=True)

        claimed = [False] * N
        found = []  # (start, end, label)

        # 1) 페이지 전체 '정확' 매칭 — 줄바꿈으로 쪼개진 이름도 잡고 오탐 없음
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

        # 2) '한 줄 안' 퍼지 매칭 — OCR 글자오류 흡수, 줄 경계는 넘지 않음
        for L, key, label in keys:
            thr = _fuzzy_threshold(L)
            i = 0
            while i <= N - 2:
                best = None
                for w in range(max(2, L - 1), L + 3):
                    e = i + w
                    if e > N:
                        break
                    if len({all_chars[j][LINE] for j in range(i, e)}) != 1:
                        continue
                    r = SequenceMatcher(None, despaced[i:e], key).ratio()
                    if r >= thr and (best is None or r > best[0]):
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
            by_line = {}
            for j in range(s, e):
                by_line.setdefault(all_chars[j][LINE], []).append(all_chars[j])
            for toks in by_line.values():
                bbox, conf, txt = _union(toks)
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

    # --- 검은칠 ----------------------------------------------------------
    def _redact(self, draw, bbox, label):
        x0, y0, x1, y1 = bbox
        # 비례 분할 오차로 글자가 삐져나오는 것을 막기 위해 가로로 넉넉히 확장
        # (글자 폭 ≈ 글자 높이). 누락보다 약간의 과(過)마스킹이 안전.
        mx = max(2, int((y1 - y0) * 0.5))
        my = 2
        x0, y0, x1, y1 = x0 - mx, y0 - my, x1 + mx, y1 + my
        draw.rectangle([x0, y0, x1, y1], fill="black")
        h = max(12, int((y1 - y0) * 0.8))
        font = ImageFont.truetype(FONT_PATH, h)
        tb = draw.textbbox((0, 0), label, font=font)
        tw = tb[2] - tb[0]
        while tw > (x1 - x0) and h > 9:
            h -= 2
            font = ImageFont.truetype(FONT_PATH, h)
            tb = draw.textbbox((0, 0), label, font=font)
            tw = tb[2] - tb[0]
        th = tb[3] - tb[1]
        draw.text((x0 + 1, y0 + max(0, ((y1 - y0) - th) // 2) - tb[1]),
                  label, fill="white", font=font)

    # --- 메인 ------------------------------------------------------------
    def process(self, in_pdf, out_pdf, audit_path=None):
        doc = fitz.open(in_pdf)
        out_images = []
        for pno, page in enumerate(doc):
            pix = page.get_pixmap(matrix=fitz.Matrix(RENDER_SCALE, RENDER_SCALE))
            img = Image.open(io.BytesIO(pix.tobytes("png"))).convert("RGB")
            draw = ImageDraw.Draw(img)

            lines = self._ocr_lines(img)
            all_chars = []
            for li, toks in enumerate(lines):
                for t in toks:
                    all_chars.append((*t, li))

            # 1) 정형 개인정보: 줄 단위(글자=인덱스이므로 구간이 곧 토큰 범위)
            for li, toks in enumerate(lines):
                line_text = "".join(t[CH] for t in toks)
                for sp in detect_structured(line_text):
                    seg = toks[sp.start:sp.end]
                    if not seg:
                        continue
                    bbox, conf, txt = _union(seg)
                    label = self.pseudo.label_for(sp.ptype, sp.value)
                    self._redact(draw, bbox, label)
                    self._log(pno, sp.ptype, label, txt, conf, bbox)

            # 2) 시드 이름/법인: 페이지 전체 매칭(줄바꿈·OCR오류 대응)
            for label, bbox, conf, txt in self._find_party_boxes(all_chars):
                self._redact(draw, bbox, label)
                self._log(pno, "NAME", label, txt, conf, bbox)

            out_images.append(img)

        out_images[0].save(out_pdf, save_all=True, append_images=out_images[1:])
        self.pseudo.save()
        if audit_path:
            low = [a for a in self.audit if a.conf < LOW_CONF]
            report = {
                "input": in_pdf, "output": out_pdf,
                "engine": self.ocr_engine,
                "total_redactions": len(self.audit),
                "low_confidence_count": len(low),
                "items": [asdict(a) for a in self.audit],
            }
            Path(audit_path).write_text(
                json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        return self.audit

    def _log(self, pno, ptype, label, txt, conf, bbox):
        self.audit.append(AuditItem(
            page=pno + 1, ptype=ptype, label=label, ocr_text=txt,
            conf=round(conf, 2), bbox=[int(b) for b in bbox]))


if __name__ == "__main__":
    import argparse
    ap = argparse.ArgumentParser(description="오프라인 개인정보 마스킹")
    ap.add_argument("input")
    ap.add_argument("output")
    ap.add_argument("--parties", help="관련자 명단 JSON")
    ap.add_argument("--mapping", help="가명 대응표 저장 경로")
    ap.add_argument("--audit", help="감사 리포트 저장 경로")
    ap.add_argument("--engine", choices=["tesseract", "easyocr"], default="tesseract")
    args = ap.parse_args()
    m = Masker(parties_path=args.parties, mapping_path=args.mapping,
               ocr_engine=args.engine)
    items = m.process(args.input, args.output, audit_path=args.audit)
    print(f"마스킹 완료: {len(items)}건 → {args.output} (engine={args.engine})")
