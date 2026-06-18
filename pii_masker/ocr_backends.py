"""OCR 백엔드(교체 가능).

두 엔진을 동일한 형식으로 정규화한다:
  반환값 = list[line], line = list[token]
  token = (char, x0, y0, x1, y1, conf)   # 글자 1개 단위, 좌표는 픽셀

글자 단위 토큰으로 통일하면 탐지 구간을 그대로 박스로 변환할 수 있어
검은칠이 키워드에 정밀하게 밀착한다(엔진 무관).
공백도 토큰으로 유지한다(한국어 NER은 공백 있는 원문이 필요하므로).
"""
import numpy as np


def _split_chars(text, x0, y0, x1, y1, conf):
    """한 덩어리(단어/문장) 박스를 글자별 비례 박스로 분할(공백 포함)."""
    n = len(text)
    if n == 0:
        return []
    cw = (x1 - x0) / n
    toks = []
    for i, ch in enumerate(text):
        cx0 = x0 + i * cw
        toks.append((ch, cx0, y0, cx0 + cw, y1, conf))
    return toks


def tesseract_lines(img):
    """Tesseract: 단어 박스를 글자 단위로 분할, block/par/line로 줄 묶음.

    단어 사이에는 공백 토큰을 넣어 원문 띄어쓰기를 복원한다(NER에 필요).
    """
    import pytesseract
    from pytesseract import Output
    d = pytesseract.image_to_data(img, lang="kor+eng", output_type=Output.DICT)
    words: dict[tuple, list] = {}
    for i in range(len(d["text"])):
        t = d["text"][i]
        if not t.strip():
            continue
        key = (d["block_num"][i], d["par_num"][i], d["line_num"][i])
        l, top, w, h = d["left"][i], d["top"][i], d["width"][i], d["height"][i]
        words.setdefault(key, []).append((t, l, top, l + w, top + h, float(d["conf"][i])))

    lines = []
    for key in sorted(words):
        toks, prev_x1 = [], None
        for t, x0, y0, x1, y1, conf in words[key]:
            if prev_x1 is not None and x0 > prev_x1:  # 단어 사이 공백 토큰
                toks.append((" ", prev_x1, y0, x0, y1, conf))
            toks.extend(_split_chars(t, x0, y0, x1, y1, conf))
            prev_x1 = x1
        lines.append(toks)
    return lines


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
