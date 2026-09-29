# -*- coding: utf-8 -*-
"""HWPX 후처리: 표·사진 폭을 인쇄 영역에 통일하고 줄나눔 기준을 글자로 바꾼다.

1) 표 폭: 행별 단순 재비율은 세로 병합(rowSpan) 셀이 걸친 행에서 실제 폭이
   넘치는 문제가 있다. 표의 열 격자(colCnt 기준 열 폭)를 구해
   격자를 TARGET으로 비례 조정한 뒤, 각 셀 폭 = 차지하는 열들의 새 폭 합.
2) 줄나눔: header.xml의 breakNonLatinWord를 KEEP_WORD로 통일한다.
   ★ 실측 확인(2026. 8. 26. 한글 저장 파일 기준): 문단 모양 대화상자의
   한글 단위 ‘글자’ = KEEP_WORD, ‘어절’ = BREAK_WORD 다. 속성값 이름의
   느낌과 반대이므로 주의한다(예전 판은 BREAK_WORD 로 바꿔 어절이 되는
   버그가 있었다). 영문(breakLatinWord)은 KEEP_WORD(단어) 그대로 둔다.

사용: python3 unify_layout.py <파일.hwpx> [TARGET폭]
TARGET 기본 48190 = A4 용지 59528 - 좌우 여백 5669*2.
"""
import sys, zipfile, re, os

TARGET = 48190
if len(sys.argv) < 2:
    sys.exit("사용: python3 unify_layout.py <파일.hwpx> [TARGET폭]")
path = sys.argv[1]
if len(sys.argv) > 2:
    TARGET = int(sys.argv[2])

CELL = re.compile(
    r'<hp:cellAddr colAddr="(\d+)" rowAddr="(\d+)"/>'
    r'<hp:cellSpan colSpan="(\d+)" rowSpan="(\d+)"/>'
    r'<hp:cellSz width="(\d+)"', re.S)


def fix_tbl(tbl):
    m = re.search(r'colCnt="(\d+)"', tbl)
    if not m:
        return tbl
    ncol = int(m.group(1))
    cells = [(int(c), int(cs), int(w))
             for c, r, cs, rs, w in CELL.findall(tbl)]
    if not cells:
        return tbl
    # 1) 원래 열 폭 격자: 단일 열 셀에서 직접, 나머지는 스팬 셀에서 배분
    grid = [None] * ncol
    for col, cspan, w in cells:
        if cspan == 1 and col < ncol and grid[col] is None:
            grid[col] = w
    for _ in range(ncol):
        changed = False
        for col, cspan, w in cells:
            if cspan <= 1:
                continue
            cover = list(range(col, min(col + cspan, ncol)))
            unknown = [i for i in cover if grid[i] is None]
            if unknown and len(unknown) < len(cover):
                rest = w - sum(grid[i] for i in cover if grid[i] is not None)
                for i in unknown:
                    grid[i] = max(1, rest // len(unknown))
                changed = True
        if not changed:
            break
    if any(g is None for g in grid):
        known = sum(g for g in grid if g is not None)
        miss = [i for i, g in enumerate(grid) if g is None]
        for i in miss:
            grid[i] = max(1, (TARGET - known) // len(miss))
    # 2) 격자를 TARGET으로 비례 조정(반올림 오차는 가장 넓은 열에 흡수)
    s = sum(grid)
    new = [max(1, round(g * TARGET / s)) for g in grid]
    new[new.index(max(new))] += TARGET - sum(new)
    # 3) 각 셀 폭 = 차지하는 열들의 새 폭 합
    def sub_cell(mm):
        col, cspan = int(mm.group(1)), int(mm.group(3))
        w = sum(new[col:min(col + cspan, ncol)])
        return (f'<hp:cellAddr colAddr="{mm.group(1)}" rowAddr="{mm.group(2)}"/>'
                f'<hp:cellSpan colSpan="{mm.group(3)}" rowSpan="{mm.group(4)}"/>'
                f'<hp:cellSz width="{w}"')
    tbl = CELL.sub(sub_cell, tbl)
    # 4) 표 자체의 sz width
    tbl = re.sub(r'(<hp:sz\s+width=")\d+(")', r'\g<1>%d\g<2>' % TARGET, tbl, count=1)
    return tbl


def rescale(sec):
    return re.sub(r'<hp:tbl\b.*?</hp:tbl>', lambda m: fix_tbl(m.group(0)), sec, flags=re.S)


tmp = path + ".tmp"
with zipfile.ZipFile(path, 'r') as zin, zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
    n = 0
    nb = 0
    for it in zin.infolist():
        d = zin.read(it.filename)
        if it.filename.startswith('Contents/section'):
            sec = d.decode('utf-8')
            n += len(re.findall(r'<hp:tbl\b', sec))
            d = rescale(sec).encode('utf-8')
        elif it.filename == 'Contents/header.xml':
            hd = d.decode('utf-8')
            nb = hd.count('breakNonLatinWord="BREAK_WORD"')
            hd = hd.replace('breakNonLatinWord="BREAK_WORD"',
                            'breakNonLatinWord="KEEP_WORD"')
            d = hd.encode('utf-8')
        zout.writestr(it, d)
os.replace(tmp, path)
print(f"표 {n}개 가로폭 {TARGET} 통일(열 격자), 줄나눔 글자(KEEP_WORD) 전환 {nb}건")
