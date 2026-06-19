"""오프라인 개인정보 마스킹 파이프라인 (프로토타입).

스캔/텍스트 PDF → 페이지 렌더 → OCR(글자 단위 좌표) → 개인정보 탐지
→ 검은칠 + 한글 가명 라벨 → 이미지로 PDF 재생성 → 감사 리포트.

전 과정 로컬에서 동작(인터넷 불필요). 출력 PDF는 이미지로만 구성되어
원본 텍스트가 남지 않는다(텍스트 추출로 개인정보가 새지 않음).

OCR 토큰을 글자 단위로 통일하므로 탐지 구간이 박스에 정밀하게 밀착한다.
"""
import io
import json
import os
import re
import sys
import gc
from dataclasses import dataclass, asdict, field
from difflib import SequenceMatcher
from pathlib import Path

import fitz  # PyMuPDF
from PIL import Image, ImageDraw, ImageFont

from detectors import detect_structured
from pseudonym import Pseudonymizer
import ocr_backends

# 렌더 배율(72dpi 기준) — 2면 약 144dpi. 메모리 부족 시 PII_RENDER_SCALE로 조절
RENDER_SCALE = float(os.environ.get("PII_RENDER_SCALE", "2"))


def _find_font():
    """한글 라벨용 폰트 경로 탐색(동봉본 → 플랫폼 기본)."""
    here = Path(__file__).resolve().parent
    candidates = [
        os.environ.get("PII_FONT"),
        str(here / "assets" / "NanumGothic.ttf"),   # 동봉(오프라인 배포)
        "C:/Windows/Fonts/malgun.ttf",               # Windows 맑은 고딕
        "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
        "/Library/Fonts/AppleGothic.ttf",            # macOS
    ]
    for c in candidates:
        if c and Path(c).exists():
            return c
    raise FileNotFoundError("한글 폰트를 찾을 수 없습니다. PII_FONT 환경변수로 지정하세요.")


FONT_PATH = _find_font()


def _bundled_dir(*parts):
    """동봉 리소스 경로 후보(빌드본/소스/실행파일 옆) 중 존재하는 첫 경로."""
    bases = []
    if getattr(sys, "frozen", False):           # PyInstaller 빌드본
        bases.append(Path(sys._MEIPASS))
        bases.append(Path(sys.executable).resolve().parent)
    bases.append(Path(__file__).resolve().parent)
    for b in bases:
        p = b.joinpath(*parts)
        if p.exists():
            return str(p)
    return None


def _easyocr_model_dir():
    """동봉된 EasyOCR 모델 폴더(.pth 들어있는 곳). 없으면 None(온라인 다운로드)."""
    return os.environ.get("EASYOCR_MODEL_DIR") or _bundled_dir("models", "easyocr")
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


def _overlap(a, b):
    """두 박스가 겹치는지(축 정렬 사각형)."""
    return not (a[2] <= b[0] or a[0] >= b[2] or a[3] <= b[1] or a[1] >= b[3])


_HANGUL = re.compile(r"[가-힣]")
# 인명에 흔히 붙는 조사 — 끝에서 떼어내 길이 판단
_JOSA = ("은", "는", "이", "가", "을", "를", "에게", "와", "과", "의", "도", "께", "께서")
# NER이 사람으로 오탐하기 쉬운 일반어(법률문서 빈출) — 제외
_NER_STOP = {"본건사업", "수익배분", "고소인", "피고소인", "참고인", "피의자"}


def _ner_keep(ptype, text, seg) -> bool:
    """NER 결과를 채택할지 — 노이즈 OCR의 과마스킹을 줄이는 보수적 필터."""
    avg_conf = sum(t[CONF] for t in seg) / len(seg)
    # OCR conf 스케일: easyocr 0~1, tesseract 0~100 → 둘 다 저신뢰 컷
    conf_ok = avg_conf >= (LOW_CONF if avg_conf <= 1 else LOW_CONF * 100)
    if not conf_ok:
        return False
    core = text.strip()
    for j in sorted(_JOSA, key=len, reverse=True):
        if core.endswith(j) and len(core) - len(j) >= 2:
            core = core[:-len(j)]
            break
    if ptype == "NAME":
        if core in _NER_STOP:
            return False
        # 한국 인명: 한글 2~4자, 거의 전부 한글
        hangul = len(_HANGUL.findall(core))
        return 2 <= len(core) <= 4 and hangul >= len(core) - 0
    if ptype == "ADDR":
        # 주소: 한글이 다수이고 일정 길이 이상
        return len(core) >= 4 and len(_HANGUL.findall(core)) >= 3
    return False


def _union(tokens):
    """토큰 목록의 통합 바운딩 박스와 최소 신뢰도, 텍스트."""
    x0 = min(t[X0] for t in tokens)
    y0 = min(t[Y0] for t in tokens)
    x1 = max(t[X1] for t in tokens)
    y1 = max(t[Y1] for t in tokens)
    conf = min(t[CONF] for t in tokens)
    return [x0, y0, x1, y1], conf, "".join(t[CH] for t in tokens)


class Masker:
    def __init__(self, parties_path=None, mapping_path=None, ocr_engine="tesseract",
                 ner=None, paddle_model_dir=None, render_scale=None, canvas_size=None):
        self.parties = _load_parties(parties_path)
        self.pseudo = Pseudonymizer(mapping_path)
        self.ocr_engine = ocr_engine
        self.ner = ner  # KoreanNER 인스턴스 또는 None
        self.paddle_model_dir = paddle_model_dir  # 오프라인 로컬 모델 경로
        # OCR 정밀도(클수록 정확·느림·메모리↑). None이면 환경변수/기본값 사용
        self.render_scale = float(render_scale) if render_scale else RENDER_SCALE
        self.canvas_size = canvas_size or os.environ.get("EASYOCR_CANVAS_SIZE", "1600")
        self._reader = None  # OCR 엔진 지연 초기화
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
                try:  # 메모리 절약: torch 스레드 수 제한(병렬 버퍼 감소)
                    import torch
                    torch.set_num_threads(max(1, int(os.environ.get("TORCH_THREADS", "1"))))
                except Exception:
                    pass
                kw = dict(gpu=False, verbose=False)
                mdir = _easyocr_model_dir()
                if mdir:  # 오프라인: 동봉 모델 사용 + 다운로드 금지
                    kw["model_storage_directory"] = mdir
                    kw["download_enabled"] = False
                self._reader = easyocr.Reader(["ko", "en"], **kw)
            # 메모리 절약: 내부 처리 이미지 크기 제한(기본 1600). 폭탄 할당 방지
            return ocr_backends.easyocr_lines(img, self._reader, canvas_size=self.canvas_size)
        if self.ocr_engine == "paddleocr":
            if self._reader is None:
                from paddleocr import PaddleOCR
                kw = dict(lang="korean", use_doc_orientation_classify=False,
                          use_doc_unwarping=False, use_textline_orientation=False)
                # 오프라인: 로컬 모델 경로 지정(없으면 기본 다운로드 시도)
                if self.paddle_model_dir:
                    kw["text_detection_model_dir"] = f"{self.paddle_model_dir}/det"
                    kw["text_recognition_model_dir"] = f"{self.paddle_model_dir}/rec"
                self._reader = PaddleOCR(**kw)
            return ocr_backends.paddleocr_lines(img, self._reader)
        return ocr_backends.tesseract_lines(img)

    # --- 시드 이름 매칭 ---------------------------------------------------
    def _find_party_boxes(self, all_chars):
        """페이지 전체 글자 흐름에서 시드 이름을 찾는다.

        all_chars: [token, ...] (읽기 순서, 공백 포함). 매칭은 공백 제거본에서 수행.
        """
        # 공백 제거본 + (despaced 인덱스 → all_chars 토큰 인덱스) 매핑
        d2t = [i for i, t in enumerate(all_chars) if not t[CH].isspace()]
        despaced = "".join(all_chars[i][CH] for i in d2t)
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
                    if len({all_chars[d2t[j]][LINE] for j in range(i, e)}) != 1:
                        continue
                    r = SequenceMatcher(None, despaced[i:e], key).ratio()
                    if r >= thr and (best is None or r > best[0]):
                        best = (r, e)
                if best and not any(claimed[i:best[1]]):
                    s2, te = self._tighten(despaced, i, best[1], key)
                    # 띄어쓴 이름의 끝글자 누락 방지: 같은 줄에서 최소 키 길이만큼 덮는다
                    e2 = max(te, s2)
                    line0 = all_chars[d2t[s2]][LINE]
                    while (e2 - s2) < L and e2 < N and all_chars[d2t[e2]][LINE] == line0:
                        e2 += 1
                    if not any(claimed[s2:e2]):
                        found.append((s2, e2, label))
                        for j in range(s2, e2):
                            claimed[j] = True
                        i = e2
                    else:
                        i += 1
                else:
                    i += 1

        # 매칭 구간 → 줄별 박스 (despaced 인덱스를 토큰 인덱스로 환원)
        results = []
        for s, e, label in found:
            by_line = {}
            for j in range(s, e):
                tok = all_chars[d2t[j]]
                by_line.setdefault(tok[LINE], []).append(tok)
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
        # 비례 분할 오차 보정용 여백(작게). 너무 크면 박스가 어색해지므로 절제.
        mx = max(2, int((y1 - y0) * 0.18))
        my = max(1, int((y1 - y0) * 0.06))
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
    def suggest_parties(self, in_pdf, progress=None):
        """문서를 OCR해 마스킹 후보 명단 초안 + 검토 리포트를 만든다.

        progress(done, total): 페이지 진행 콜백(선택).
        반환: (parties_list, report_text). 결과는 사람이 검토·수정해야 한다.
        """
        import suggest as _sg
        doc = fitz.open(in_pdf)
        total = len(doc)
        line_texts = []
        for pno, page in enumerate(doc):
            pix = page.get_pixmap(matrix=fitz.Matrix(self.render_scale, self.render_scale))
            img = Image.open(io.BytesIO(pix.tobytes("png"))).convert("RGB")
            for toks in self._ocr_lines(img):
                line_texts.append("".join(t[CH] for t in toks))
            del pix, img
            gc.collect()  # 페이지마다 OCR 중간 메모리 회수(저사양 대응)
            if progress:
                progress(pno + 1, total)
        return _sg.build_draft(_sg.suggest(line_texts))

    def process(self, in_pdf, out_pdf, audit_path=None, progress=None):
        doc = fitz.open(in_pdf)
        total = len(doc)
        out_images = []
        for pno, page in enumerate(doc):
            pix = page.get_pixmap(matrix=fitz.Matrix(self.render_scale, self.render_scale))
            img = Image.open(io.BytesIO(pix.tobytes("png"))).convert("RGB")
            draw = ImageDraw.Draw(img)

            lines = self._ocr_lines(img)
            all_chars = []
            for li, toks in enumerate(lines):
                for t in toks:
                    all_chars.append((*t, li))

            drawn = []  # 이 페이지에 이미 그린 박스들(중복/충돌 방지)

            def apply(ptype, label, bbox, conf, txt):
                self._redact(draw, bbox, label)
                drawn.append(bbox)
                self._log(pno, ptype, label, txt, conf, bbox)

            # 1) 정형 개인정보: 줄 단위(글자=인덱스이므로 구간이 곧 토큰 범위)
            for toks in lines:
                line_text = "".join(t[CH] for t in toks)
                for sp in detect_structured(line_text):
                    seg = toks[sp.start:sp.end]
                    if not seg:
                        continue
                    bbox, conf, txt = _union(seg)
                    apply(sp.ptype, self.pseudo.label_for(sp.ptype, sp.value),
                          bbox, conf, txt)

            # 2) 시드 이름/법인: 페이지 전체 매칭(줄바꿈·OCR오류 대응)
            for label, bbox, conf, txt in self._find_party_boxes(all_chars):
                apply("NAME", label, bbox, conf, txt)

            # 3) NER 보조 탐지: 시드에 없는 제3자 이름(PS)·주소(LC)
            #    이미 가려진 영역과 겹치면 건너뛴다(시드 라벨을 덮어쓰지 않도록).
            #    노이즈 OCR의 과마스킹을 막기 위해 보수적으로 필터링한다.
            if self.ner:
                for toks in lines:
                    line_text = "".join(t[CH] for t in toks)
                    for ptype, s, e, etext in self.ner.entities(line_text):
                        seg = toks[s:e]
                        if not seg or not _ner_keep(ptype, etext, seg):
                            continue
                        bbox, conf, _ = _union(seg)
                        if any(_overlap(bbox, b) for b in drawn):
                            continue
                        apply(ptype, self.pseudo.label_for(ptype, _norm(etext)),
                              bbox, conf, etext)

            out_images.append(img)
            del pix
            gc.collect()  # 페이지마다 OCR 중간 메모리 회수(저사양 대응)
            if progress:
                progress(pno + 1, total)

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
    ap.add_argument("--engine", choices=["tesseract", "easyocr", "paddleocr"],
                    default="tesseract")
    ap.add_argument("--paddle-model-dir",
                    help="(paddleocr) 오프라인 로컬 모델 폴더(det/, rec/ 하위)")
    ap.add_argument("--ner", action="store_true", help="한글 NER로 제3자 이름·주소 보조 탐지")
    ap.add_argument("--ner-backend", choices=["spacy", "transformers"], default="spacy")
    ap.add_argument("--ner-model", default="ko_core_news_sm",
                    help="spaCy 모델명 또는 (transformers) 로컬 모델 경로")
    args = ap.parse_args()
    ner = None
    if args.ner:
        from ner import KoreanNER
        ner = KoreanNER(backend=args.ner_backend, model=args.ner_model)
    m = Masker(parties_path=args.parties, mapping_path=args.mapping,
               ocr_engine=args.engine, ner=ner,
               paddle_model_dir=args.paddle_model_dir)
    items = m.process(args.input, args.output, audit_path=args.audit)
    print(f"마스킹 완료: {len(items)}건 → {args.output} "
          f"(engine={args.engine}, ner={'on' if ner else 'off'})")
