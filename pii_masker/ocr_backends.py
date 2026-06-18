"""OCR 백엔드(교체 가능).

두 엔진을 동일한 형식으로 정규화한다:
  반환값 = list[line], line = list[token]
  token = (char, x0, y0, x1, y1, conf)   # 글자 1개 단위, 좌표는 픽셀

글자 단위 토큰으로 통일하면 탐지 구간을 그대로 박스로 변환할 수 있어
검은칠이 키워드에 정밀하게 밀착한다(엔진 무관).
공백은 토큰에서 제외한다.
"""
import numpy as np


def _split_chars(text, x0, y0, x1, y1, conf):
    """한 덩어리(단어/문장) 박스를 글자별 비례 박스로 분할."""
    n = len(text)
    if n == 0:
        return []
    cw = (x1 - x0) / n
    toks = []
    for i, ch in enumerate(text):
        if ch.isspace():
            continue
        cx0 = x0 + i * cw
        toks.append((ch, cx0, y0, cx0 + cw, y1, conf))
    return toks


def tesseract_lines(img):
    """Tesseract: 단어 박스를 글자 단위로 분할, block/par/line로 줄 묶음."""
    import pytesseract
    from pytesseract import Output
    d = pytesseract.image_to_data(img, lang="kor+eng", output_type=Output.DICT)
    lines: dict[tuple, list] = {}
    for i in range(len(d["text"])):
        t = d["text"][i]
        if not t.strip():
            continue
        key = (d["block_num"][i], d["par_num"][i], d["line_num"][i])
        l, top, w, h = d["left"][i], d["top"][i], d["width"][i], d["height"][i]
        lines.setdefault(key, []).extend(
            _split_chars(t, l, top, l + w, top + h, float(d["conf"][i])))
    return [lines[k] for k in sorted(lines)]


def easyocr_lines(img, reader):
    """EasyOCR: 인식 세그먼트 1개 = 1줄, 글자 단위로 비례 분할."""
    res = reader.readtext(np.array(img), detail=1, paragraph=False)
    out = []
    for box, text, conf in res:
        xs = [p[0] for p in box]
        ys = [p[1] for p in box]
        toks = _split_chars(text, min(xs), min(ys), max(xs), max(ys), float(conf))
        if toks:
            out.append(toks)
    return out
