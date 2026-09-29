# -*- coding: utf-8 -*-
"""
온나라 행정보고서(행정보고 양식) 전용 빌드 모듈.

양식 구조: 날짜박스 - 제목박스(HY견고딕 22pt) - 문서요약박스 - 본문(□◦-※ 4단계) - 붙임박스.
본문 단계: □ 수준1(HY견고딕 15pt) / ◦ 수준2(바탕체 15pt) / - 수준3(바탕체 15pt) / ※ 유의(중고딕 13pt).

★ 재발 방지 원칙
  - paraPr/charPr 번호를 코드에 박지 않고, 올린 양식의 골격 예시 줄(□ 수준 1 등)에서 직접 읽는다.
  - 저장 직전, 본문이 참조하는 모든 paraPr/charPr 가 header 에 있는지 검증. 끊기면 오류로 막는다.
  - linesegarray 제거, 저장 후 fix_namespaces, 둥근따옴표·한국어 전용.
"""
import zipfile, re, os, subprocess

import os as _os
def _find_fix_ns():
    _here = _os.path.dirname(_os.path.abspath(__file__))
    for _c in (_os.environ.get("HWPX_FIX_NS", ""),
               _os.path.join(_here, "fix_namespaces.py"),
               "/mnt/skills/user/hwpx/scripts/fix_namespaces.py"):
        if _c and _os.path.exists(_c):
            return _c
    raise FileNotFoundError("fix_namespaces.py 를 찾을 수 없습니다. HWPX_FIX_NS 환경변수로 경로를 지정하세요.")


FIX_NS = _find_fix_ns()


class OnnaraReport:
    def __init__(self, template_path):
        self.template = template_path
        z = zipfile.ZipFile(template_path)
        self.sec = z.read('Contents/section0.xml').decode('utf-8')
        self.hdr = z.read('Contents/header.xml').decode('utf-8')

        # 본문 4단계 서식 자동 탐지(골격 예시 줄에서)
        self.L1 = self._sty('□ 수준 1')   # (paraPr, charPr, styleID)
        self.L2 = self._sty('◦ 수준 2')
        self.L3 = self._sty('- 수준 3')
        self.NOTE = self._sty('※ 별표')
        for nm, v in [('□', self.L1), ('◦', self.L2), ('-', self.L3), ('※', self.NOTE)]:
            if not v:
                raise ValueError(f"양식 골격에서 '{nm}' 예시 줄을 찾지 못했습니다.")

        # 본문 영역 경계: 첫 □ 문단 시작 ~ 붙임 박스 문단 시작
        i = self.sec.find('□ 수준 1')
        self._body_start = self.sec.rfind('<hp:p ', 0, i)
        ai = self.sec.find('붙임1')
        self._attach_start = self.sec.rfind('<hp:p ', 0, self.sec.rfind('<hp:tbl', 0, ai))

        self._pid = 3600000000

        # 표용 서식 자동 탐지: 가운데 정렬 paraPr, 12pt 볼드/보통 charPr, 표 감싸는 문단
        m = re.search(r'<hh:paraPr id="(\d+)"[^>]*>(?:(?!</hh:paraPr>).)*?horizontal="CENTER"',
                      self.hdr, re.DOTALL)
        if not m:
            raise ValueError("양식에서 가운데 정렬 paraPr 를 찾지 못했습니다.")
        self._pp_center = m.group(1)
        def _cp(bold):
            for mm in re.finditer(r'<hh:charPr id="(\d+)".*?</hh:charPr>', self.hdr, re.DOTALL):
                blk = mm.group(0)
                if 'height="1200"' in blk and (('<hh:bold/>' in blk) == bold):
                    return mm.group(1)
            raise ValueError("양식에서 12pt charPr 를 찾지 못했습니다.")
        self._cp_bold12 = _cp(True)
        self._cp_reg12 = _cp(False)
        i = self.sec.find('붙임1')
        w = self.sec[self.sec.rfind('<hp:p ', 0, self.sec.rfind('<hp:tbl', 0, i)):
                     self.sec.rfind('<hp:tbl', 0, i)]
        self._pp_tblwrap = re.search(r'paraPrIDRef="(\d+)"', w).group(1)
        self._cp_tblwrap = re.search(r'charPrIDRef="(\d+)"', w).group(1)

    # ---------- 내부 ----------
    def _sty(self, anchor):
        i = self.sec.find(anchor)
        if i < 0:
            return None
        ps = self.sec.rfind('<hp:p ', 0, i)
        p = self.sec[ps:self.sec.find('</hp:p>', i)]
        pp = re.search(r'paraPrIDRef="(\d+)"', p)
        cp = re.search(r'charPrIDRef="(\d+)"', p)
        sid = re.search(r'styleIDRef="(\d+)"', p)
        if not (pp and cp and sid):
            return None
        return pp.group(1), cp.group(1), sid.group(1)

    def _validate(self, sec, hdr):
        pids = set(re.findall(r'<hh:paraPr id="(\d+)"', hdr))
        cids = set(re.findall(r'<hh:charPr id="(\d+)"', hdr))
        mp = set(re.findall(r'paraPrIDRef="(\d+)"', sec)) - pids
        mc = set(re.findall(r'charPrIDRef="(\d+)"', sec)) - cids
        if mp or mc:
            raise ValueError(f"[참조 검증 실패] 없는 paraPr={sorted(mp, key=int)}, "
                             f"없는 charPr={sorted(mc, key=int)} (이대로면 빈 페이지).")

    def _p(self, style, text):
        pp, cp, sid = style
        self._pid += 1
        return (f'<hp:p id="{self._pid}" paraPrIDRef="{pp}" styleIDRef="{sid}" '
                f'pageBreak="0" columnBreak="0" merged="0">'
                f'<hp:run charPrIDRef="{cp}"><hp:t>{text}</hp:t></hp:run></hp:p>')


    # ---------- 내어쓰기(기호+한 칸 기준) ----------
    # 예시 보고서 실측값(문체부 바탕체 15pt 기준): ◦ ≈ -3168, - ≈ -3674.
    # 괄호 요약으로 시작하면 괄호 뒤 기준이라 더 깊어짐(예시 -3321~-3867) — 필요 시 hang 인자로 조정.
    _HANG_L2 = 3168
    _HANG_L3 = 3674
    _HANG_NOTE = 3300

    def _hang_style(self, base_style, hang):
        """base_style의 paraPr를 복제해 내어쓰기(intent=-hang, left=0)를 건 새 paraPr id 반환."""
        key = (base_style[0], hang)
        if not hasattr(self, '_hang_cache'):
            self._hang_cache = {}
        if key in self._hang_cache:
            return self._hang_cache[key]
        cnt = re.search(r'<hh:paraPr id="(\d+)"[^>]*>(?s:.)*?</hh:paraPr>(?!(?s:.)*<hh:paraPr)', self.hdr)
        ids = [int(x) for x in re.findall(r'<hh:paraPr id="(\d+)"', self.hdr)]
        nid = max(ids) + 1
        blk = re.search(r'<hh:paraPr id="' + base_style[0] + r'".*?</hh:paraPr>', self.hdr, re.DOTALL).group(0)
        blk = re.sub(r'id="' + base_style[0] + r'"', 'id="%d"' % nid, blk, count=1)
        blk = re.sub(r'<hc:intent value="-?\d+"', '<hc:intent value="-%d"' % hang, blk)
        blk = re.sub(r'<hc:left value="\d+"', '<hc:left value="0"', blk)
        pcnt = re.search(r'<hh:paraProperties itemCnt="(\d+)"', self.hdr)
        self.hdr = self.hdr.replace('<hh:paraProperties itemCnt="%s">' % pcnt.group(1),
                                    '<hh:paraProperties itemCnt="%d">' % (int(pcnt.group(1)) + 1), 1)
        self.hdr = self.hdr.replace('</hh:paraProperties>', blk + '</hh:paraProperties>', 1)
        st = (str(nid), base_style[1], base_style[2])
        self._hang_cache[key] = st
        return st

    # ---------- 본문 단계 ----------
    def l1(self, text):
        """□ 수준1. 앞에 '□ ' 를 붙여 전달하거나 텍스트만 줘도 됨."""
        return self._p(self.L1, text if text.lstrip().startswith('□') else f'□ {text}')

    def l2(self, text, hang=None):
        """◦ 수준2. 기호 앞 1칸·뒤 1칸. 내어쓰기 자동(기호+한 칸 기준).
        괄호 요약으로 시작해 정렬이 어긋나면 hang 값으로 조정(예: 3800)."""
        t = text if text.lstrip().startswith('◦') else f' ◦ {text}'
        return self._p(self._hang_style(self.L2, hang or self._HANG_L2), t)

    def l3(self, text, hang=None):
        """- 수준3. 기호 앞 2칸·뒤 1칸. 내어쓰기 자동."""
        t = text if text.lstrip().startswith('-') else f'  - {text}'
        return self._p(self._hang_style(self.L3, hang or self._HANG_L3), t)

    def note(self, text, hang=None):
        """※ 유의. 내어쓰기 자동."""
        t = text if text.lstrip().startswith('※') else f'   ※ {text}'
        return self._p(self._hang_style(self.NOTE, hang or self._HANG_NOTE), t)

    def empty(self):
        return self._p(self.L2, '')


    # ---------- 표 ----------
    # 테두리 규격: 표 상·하단 굵은 실선(0.5mm), 좌·우단 선 없음,
    # 머리행 볼드·배경 #DFE6F7·아래 이중선, 내부선 0.12mm 실선.
    _BF_BASE = 24  # 추가 borderFill 시작 id (양식은 1~23 사용)

    def _ensure_table_borderfills(self):
        """표용 borderFill 9종(머리 좌/중/우, 데이터 좌/중/우, 마지막행 좌/중/우)을 header에 추가."""
        if getattr(self, '_bf_added', False):
            return
        THIN = '<hh:{side}Border type="SOLID" width="0.12 mm" color="#000000"/>'
        THICK = '<hh:{side}Border type="SOLID" width="0.5 mm" color="#000000"/>'
        NONE = '<hh:{side}Border type="NONE" width="0.1 mm" color="#000000"/>'
        DOUBLE = '<hh:{side}Border type="DOUBLE_SLIM" width="0.5 mm" color="#000000"/>'
        FILL = ('<hc:fillBrush><hc:winBrush faceColor="#DFE6F7" hatchColor="#999999" '
                'alpha="0"/></hc:fillBrush>')

        def bf(bid, left, right, top, bottom, fill=False):
            return ('<hh:borderFill id="%d" threeD="0" shadow="0" centerLine="NONE" '
                    'breakCellSeparateLine="0">'
                    '<hh:slash type="NONE" Crooked="0" isCounter="0"/>'
                    '<hh:backSlash type="NONE" Crooked="0" isCounter="0"/>'
                    % bid
                    + left.format(side='left') + right.format(side='right')
                    + top.format(side='top') + bottom.format(side='bottom')
                    + '<hh:diagonal type="SOLID" width="0.1 mm" color="#000000"/>'
                    + (FILL if fill else '')
                    + '</hh:borderFill>')

        B = self._BF_BASE
        add = (
            bf(B+0, NONE, THIN, THICK, DOUBLE, fill=True)   # 머리 좌
            + bf(B+1, THIN, THIN, THICK, DOUBLE, fill=True) # 머리 중
            + bf(B+2, THIN, NONE, THICK, DOUBLE, fill=True) # 머리 우
            + bf(B+3, NONE, THIN, NONE, THIN)               # 데이터 좌
            + bf(B+4, THIN, THIN, NONE, THIN)               # 데이터 중
            + bf(B+5, THIN, NONE, NONE, THIN)               # 데이터 우
            + bf(B+6, NONE, THIN, NONE, THICK)              # 끝행 좌
            + bf(B+7, THIN, THIN, NONE, THICK)              # 끝행 중
            + bf(B+8, THIN, NONE, NONE, THICK)              # 끝행 우
        )
        cnt = re.search(r'<hh:borderFills itemCnt="(\d+)"', self.hdr)
        self.hdr = self.hdr.replace(
            '<hh:borderFills itemCnt="%s">' % cnt.group(1),
            '<hh:borderFills itemCnt="%d">' % (int(cnt.group(1)) + 9), 1)
        self.hdr = self.hdr.replace('</hh:borderFills>', add + '</hh:borderFills>', 1)
        self._bf_added = True

    def table(self, rows, small=False):
        """표 생성. rows[0]=머리행(볼드·#DFE6F7·아래 이중선). 2~6열.
        열 너비: 균등 분할하되 첫 열만 좁게(0.7배).
        small=True 면 10pt(칸 부족 시), 기본 12pt. 맑은 고딕 가운데 정렬."""
        ncol = len(rows[0])
        if not 2 <= ncol <= 6:
            raise ValueError("표는 2~6열만 지원합니다.")
        if any(len(r) != ncol for r in rows):
            raise ValueError("모든 행의 칸 수가 머리행과 같아야 합니다.")
        self._ensure_table_borderfills()

        # 12pt: 머리 19 / 데이터 14. 10pt charPr 는 필요 시 생성.
        cp_h, cp_d = self._cp_bold12, self._cp_reg12
        if small:
            cp_h, cp_d = self._ensure_small_charpr()

        W = 46488                       # 양식 표 폭
        w1 = int(W * 0.7 / (ncol - 1 + 0.7))   # 첫 열(좁게)
        wo = (W - w1) // (ncol - 1)
        widths = [w1] + [wo] * (ncol - 2) + [W - w1 - wo * (ncol - 2)]

        B = self._BF_BASE
        nrow = len(rows)
        trs = []
        for r in range(nrow):
            cells = []
            for c in range(ncol):
                if r == 0:       grp = 0   # 머리
                elif r == nrow - 1: grp = 6  # 끝행
                else:            grp = 3   # 데이터
                pos = 0 if c == 0 else (2 if c == ncol - 1 else 1)
                bfid = B + grp + pos
                cp = cp_h if r == 0 else cp_d
                txt = rows[r][c]
                cells.append(
                    '<hp:tc name="" header="0" hasMargin="0" protect="0" editable="0" '
                    'dirty="0" borderFillIDRef="%d">'
                    '<hp:subList id="" textDirection="HORIZONTAL" lineWrap="BREAK" '
                    'vertAlign="CENTER" linkListIDRef="0" linkListNextIDRef="0" '
                    'textWidth="0" textHeight="0" hasTextRef="0" hasNumRef="0">'
                    '<hp:p id="0" paraPrIDRef="%s" styleIDRef="0" pageBreak="0" '
                    'columnBreak="0" merged="0"><hp:run charPrIDRef="%s">'
                    '<hp:t>%s</hp:t></hp:run></hp:p></hp:subList>'
                    '<hp:cellAddr colAddr="%d" rowAddr="%d"/>'
                    '<hp:cellSpan colSpan="1" rowSpan="1"/>'
                    '<hp:cellSz width="%d" height="850"/>'
                    '<hp:cellMargin left="510" right="510" top="141" bottom="141"/></hp:tc>'
                    % (bfid, self._pp_center, cp, txt, c, r, widths[c]))
            trs.append('<hp:tr>' + ''.join(cells) + '</hp:tr>')

        self._pid += 1
        tbl = ('<hp:tbl id="%d" zOrder="9" numberingType="TABLE" textWrap="TOP_AND_BOTTOM" '
               'textFlow="BOTH_SIDES" lock="0" dropcapstyle="None" pageBreak="CELL" '
               'repeatHeader="1" rowCnt="%d" colCnt="%d" cellSpacing="0" '
               'borderFillIDRef="1" noAdjust="0">'
               '<hp:sz width="%d" widthRelTo="ABSOLUTE" height="%d" heightRelTo="ABSOLUTE" protect="0"/>'
               '<hp:pos treatAsChar="1" affectLSpacing="0" flowWithText="1" allowOverlap="0" '
               'holdAnchorAndSO="0" vertRelTo="PARA" horzRelTo="PARA" vertAlign="TOP" '
               'horzAlign="LEFT" vertOffset="0" horzOffset="0"/>'
               '<hp:outMargin left="0" right="0" top="141" bottom="141"/>'
               '<hp:inMargin left="510" right="510" top="141" bottom="141"/>'
               % (self._pid, nrow, ncol, W, 850 * nrow)
               + ''.join(trs) + '</hp:tbl>')
        self._pid += 1
        return ('<hp:p id="%d" paraPrIDRef="%s" styleIDRef="0" pageBreak="0" '
                'columnBreak="0" merged="0"><hp:run charPrIDRef="%s">%s<hp:t/></hp:run></hp:p>'
                % (self._pid, self._pp_tblwrap, self._cp_tblwrap, tbl))

    def _ensure_small_charpr(self):
        """10pt(칸 부족 시) 볼드/보통 charPr 를 12pt 정의 복제로 생성."""
        if getattr(self, '_small_cp', None):
            return self._small_cp
        cnt = re.search(r'<hh:charProperties itemCnt="(\d+)"', self.hdr)
        n = int(cnt.group(1))
        new = []
        for src, bid in [(self._cp_bold12, n), (self._cp_reg12, n + 1)]:
            blk = re.search(r'<hh:charPr id="' + src + r'".*?</hh:charPr>', self.hdr, re.DOTALL).group(0)
            blk = re.sub(r'id="' + src + r'"', 'id="%d"' % bid, blk, count=1)
            blk = re.sub(r'height="\d+"', 'height="1000"', blk, count=1)
            new.append(blk)
        self.hdr = self.hdr.replace(
            '<hh:charProperties itemCnt="%d">' % n,
            '<hh:charProperties itemCnt="%d">' % (n + 2), 1)
        self.hdr = self.hdr.replace('</hh:charProperties>', ''.join(new) + '</hh:charProperties>', 1)
        self._small_cp = (str(n), str(n + 1))
        return self._small_cp

    # ---------- 조립·저장 ----------
    def _fill_summary(self, sec, summary):
        """문서요약 박스(1×1 표) 채우기. summary 는 문자열 또는 줄 리스트.
        박스의 '문서요약' 문단을 요약 줄들로 교체(원 문단의 paraPr/charPr 유지)."""
        if isinstance(summary, str):
            summary = [summary]
        m = re.search(
            r'<hp:p\b[^>]*paraPrIDRef="(\d+)"[^>]*><hp:run charPrIDRef="(\d+)">'
            r'<hp:t>문서요약</hp:t></hp:run>.*?</hp:p>', sec, re.DOTALL)
        if not m:
            return sec
        pp, cp = m.group(1), m.group(2)
        paras = "".join(
            f'<hp:p id="0" paraPrIDRef="{pp}" styleIDRef="0" pageBreak="0" '
            f'columnBreak="0" merged="0"><hp:run charPrIDRef="{cp}">'
            f'<hp:t>{line}</hp:t></hp:run></hp:p>' for line in summary)
        return sec[:m.start()] + paras + sec[m.end():]

    def build(self, out_path, *, title, body, attach=None, summary=None):
        """title: 제목 / body: l1/l2/l3/note/empty 결과 리스트 /
        attach: 붙임 문서명(문자열) 또는 None /
        summary: 문서요약 박스 내용(문자열 또는 줄 리스트). None 이면 '문서요약' 라벨 유지.
        날짜 박스는 한글에서 직접 채우는 수동 슬롯."""
        sec = self.sec
        # 제목 박스: 앞에 박힌 불필요한 따옴표 런 제거 후 제목 치환
        sec = re.sub(
            r'<hp:run charPrIDRef="\d+"><hp:t>[‘’\'"]</hp:t></hp:run>'
            r'(?=<hp:run charPrIDRef="\d+"><hp:t>보고서 양식 및 서식)', '', sec, count=1)
        sec = sec.replace('보고서 양식 및 서식', title, 1)
        sec = sec.replace('(HY견고딕, 20~22pt)', '', 1)
        # 문서요약 박스
        if summary is not None:
            sec = self._fill_summary(sec, summary)
        # 붙임 박스(런이 쪼개져 있어 주 텍스트 런만 치환)
        if attach is not None:
            sec = sec.replace('붙임 문서 제목', attach, 1)
            sec = sec.replace('(부제)', '', 1)
        # 본문 영역 치환(치환으로 오프셋이 변하므로 경계를 여기서 다시 찾는다)
        bi = sec.find('□ 수준 1')
        body_start = sec.rfind('<hp:p ', 0, bi)
        ai = sec.find('붙임1')
        attach_start = sec.rfind('<hp:p ', 0, sec.rfind('<hp:tbl', 0, ai))
        sec = sec[:body_start] + ''.join(body) + sec[attach_start:]
        sec = re.sub(r'<hp:linesegarray>.*?</hp:linesegarray>', '', sec, flags=re.DOTALL)

        self._validate(sec, self.hdr)

        tmp = out_path + '.tmp'
        with zipfile.ZipFile(self.template, 'r') as zin, \
                zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
            for it in zin.infolist():
                d = zin.read(it.filename)
                if it.filename == 'Contents/section0.xml':
                    d = sec.encode('utf-8')
                elif it.filename == 'Contents/header.xml':
                    d = self.hdr.encode('utf-8')
                zout.writestr(it, d)
        if os.path.exists(out_path):
            os.remove(out_path)
        os.rename(tmp, out_path)
        subprocess.run(['python3', FIX_NS, out_path], check=True)
        return out_path
